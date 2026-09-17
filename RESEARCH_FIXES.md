# Shinigami by Saizo — Research, Fixes & Upgrades

## BUG #1: STRAFE INVERSION (CRITICAL)
### Location: `MixinKeyboardInput.java` — `onTickTail()`
### Root Cause
The mixin computes `moveVector` with the strafe direction INVERTED:
```java
float strafe = (overridden.right()?1:0) - (overridden.left()?1:0);
```
This gives `strafe=+1` when `right=true`. But in Minecraft's movement system:
- `ServerboundPlayerInputPacket.xxa` = **Positive for LEFT strafe, negative for RIGHT**
- `LivingEntity.travel()` uses `movementInput.x` as xxa

When strafe=+1 → xxa=+1 → server interprets this as LEFT strafe. But the override intent was RIGHT.

### The Fix
Invert the strafe calculation:
```java
float strafe = (overridden.left()?1:0) - (overridden.right()?1:0);
```
Or equivalently invert the right term:
```java
float strafe = (overridden.right()?1:0) - (overridden.left()?1:0);
strafe = -strafe;
```

### Cross-reference
- `KeyboardInput.getMovementMultiplier(boolean positive, boolean negative)` → returns `positive ? 1 : -1`
- Called as `getMovementMultiplier(keyLeft.isDown(), keyRight.isDown())`
- So `keyLeft=true, keyRight=false` → xxa=+1 → LEFT strafe ✓
- Old mixin: `right=true, left=false` → strafe=+1 → xxa=+1 → LEFT strafe ✗ (should be RIGHT)

---

## BUG #2: FORWARD/BACKWARD OSCILLATION LOOP
### Location: `KeyMovementController.java`, `AimAssistMod.java`, `MovementArbiter.java`
### Root Cause
The interaction between supplement mode and override mode creates a timing loop:

1. **Chase** sets `supplementForward(true)` every tick
2. **Dodge** triggers → calls `moveToward(dodgeDir)` → OVERRIDE_TTL=3 ticks
3. **During dodge**: supplement is cleared by `clearSupplement()` at start of tick → override provides movement
4. **After dodge TTL**: override expires → supplement forward resumes
5. **If dodge re-triggers immediately**: forward → dodgeDir → forward → dodgeDir → oscillation

Additionally, if `supplementForward()` is set with `forward=true` but the override target direction has a backward component, the player oscillates between forward (supplement) and the override direction.

### The Fix
Three-part solution:

#### A. Dodge should NOT use full override
Change dodge to use a **modulation** approach instead of full override:
- Dodge modulates the EXISTING movement direction, it doesn't replace it
- Dodge adds perpendicular/escape vector AS OFFSET to current supplement forward
- Dodge should NEVER clear supplement

#### B. Increase OVERRIDE_TTL or use persistent override
```java
// KeyMovementController.java
private static final int OVERRIDE_TTL = 5; // was 3 — more stable
```

#### C. Fix arbiter to not clear supplement
```java
// MovementArbiter.java — already fixed per AGENTS.md
// Remove clearSupplement() from empty-intents path
```

---

## BUG #3: TARGET SWITCHING AFTER KILL
### Location: `AimAssistModule.java` — entity targeting/tracking logic
### Root Cause
After killing the current target, the entity scan re-selects the nearest entity without a cooldown or death check. The dead target's entity object might still exist (in death animation) and gets filtered, but immediately the NEXT closest entity becomes the new target.

### The Fix

#### A. Add target switch cooldown
```java
private int targetSwitchCooldown = 0;
// After target dies or is removed:
targetSwitchCooldown = 20; // 20 ticks = 1 second pause
```

#### B. Check `entity.isRemoved()` and `entity.isDeadOrDying()`
```java
if (entity.isRemoved() || !entity.isAlive()) continue;
```

#### C. Target persist time
Give target a `persist_time` of ~40 ticks after death before allowing re-target. This prevents the "chain kill" issue where the mod immediately picks up the next entity.

#### D. Priority system should down-weight recently-killed entity types
```java
if (entity.getType() == lastKilledType && killTicks < 100) {
    priority *= 0.5; // half priority for same type recently killed
}
```

---

## BUG #4: HYBRID DISTANCE+ANGLE PRIORITY BROKEN
### Location: Target selection scoring
### Root Cause
The hybrid priority mode likely computes `score = distance + angle` without **normalizing** the two values. Distance ranges 0-64 blocks while angle ranges 0-180 degrees. The raw values are on completely different scales:
- distance=30, angle=30 → score=60 (angle dominates incorrectly)
- The two terms need to be normalized to [0,1] before combining

### The Fix (Wurst Client approach)
```java
// Normalize both to 0-1 range, then combine
float maxDistance = config.getDetectionRange(); // e.g., 64
float maxAngle = 180f;

float normalizedDistance = distance / maxDistance;
float normalizedAngle = angle / maxAngle;

// Weighted combination
float distanceWeight = config.getDistanceWeight(); // 0.5 default
float angleWeight = config.getAngleWeight();       // 0.5 default

float score = normalizedDistance * distanceWeight + normalizedAngle * angleWeight;

// Lower score = higher priority
```

#### Distance-Only mode (works best per user report)
```java
score = distance; // pure distance, no angle component
```

#### Angle-Only mode
```java
score = angle; // pure angle, no distance component
```

#### Wurst's "Angle+Dist" formula
```java
score = (angle * angle) + (distance * distance);
// This works because squaring both normalizes the spread differently
```

---

## BUG #5: PREDICTION NOT WORKING
### Location: Aim assist targeting/aiming code
### Root Cause
The current prediction likely uses simple position delta without accounting for:
1. Time-to-impact (projectile travel time for different weapons)
2. Target acceleration/deceleration
3. Movement patterns (strafe, jump, sprint)
4. Server-side position vs client-side position discrepancy

### The Fix

#### A. Kinematic prediction (for projectiles)
```java
Vec3 predictPosition(LivingEntity target, float timeToImpact) {
    Vec3 velocity = target.getDeltaMovement();
    Vec3 pos = target.position();
    // Simple linear: future = pos + vel * time
    return pos.add(velocity.x * timeToImpact, 0, velocity.z * timeToImpact);
}
```

#### B. Time-to-impact calculation
```java
float calculateTimeToImpact(Vec3 from, Vec3 to, float projectileSpeed) {
    double distance = from.distanceTo(to);
    return (float)(distance / projectileSpeed);
    // Projectile speeds: arrow=3.0, snowball/egg=1.5, potion=0.5, trident=2.5
}
```

#### C. Confidence-based prediction
```java
float predictionConfidence = 1.0f - (distance / maxDistance) * 0.5f;
if (target.hurtTime > 0) predictionConfidence *= 0.7f; // less confident when target is hit
if (target.isSprinting()) predictionConfidence *= 1.2f; // sprinting = more predictable
```

#### D. Multiple prediction modes (from AimBow)
1. **Stationary** — no prediction (target not moving)
2. **Vector** — linear velocity extrapolation (target running in open)
3. **Path** — follow path/terrain (target on bridge/linear path)
4. **Physics** — parabolic trajectory (target airborne/jumping)

#### E. Prediction for melee
For sword/melee, prediction should be minimal (0-2 ticks lookahead) since:
- Melee range is only 3 blocks
- Reaction time is more important than prediction
- Focus on crosshair placement at head/body level during crit jumps

---

## BUG #6: VISUAL CATEGORY NOT WORKING
### Location: `AimAssistScreen.java` — config GUI rendering
### Root Cause
The "Visual" tab in the config screen likely references rendering code that either:
1. Doesn't exist (methods not implemented)
2. References deprecated/removed Fabric HUD APIs
3. Uses incorrect rendering pipeline for 1.21.1

### The Fix

#### A. Implement proper HUD rendering using Fabric Hud API
```java
// Use HudElementRegistry (Fabric API 0.116+)
HudElementRegistry.attachElementAfter(
    VanillaHudElements.CROSSHAIR,
    Identifier.of("shinigami", "target_info"),
    (graphics, deltaTracker) -> {
        // Render target health, distance, ESP box
    }
);
```

#### B. Visual features to implement
1. **Target ESP Box** — colored bounding box around current target
   ```java
   // In WorldRenderEvents.AFTER_ENTITIES or similar
   if (target != null) {
       Box box = target.getBoundingBox();
       drawOutlinedBox(matrixStack, box, color, 2f);
   }
   ```

2. **Target Info HUD** — name, health, distance, weapon overlay
   - Render using `GuiGraphics.drawString()`
   - Position near crosshair or in configurable screen corner

3. **Crosshair Enhancement** — color change when target locked
   - Override crosshair render color based on target state

4. **Tracers** — line from player to target
   ```java
   // In WorldRenderEvents
   Vec3 playerPos = camera.getPosition();
   Vec3 targetPos = target.getBoundingBox().getCenter();
   drawLine(matrixStack, playerPos, targetPos, color);
   ```

5. **Prediction Ghost** — translucent box where target WILL be

#### C. Config screen integration
```java
// In AimAssistScreen, add VISUAL tab with:
- ESP toggle + color picker
- Target Info toggle
- Tracers toggle + color
- Crosshair indicator toggle
- Prediction ghost toggle + opacity slider
```

---

## BUG #7: MOVEMENT OVERRIDE ARCHITECTURE (DESIGN ISSUE)
### Location: Entire movement pipeline
### Root Cause
Multiple systems (dodge, chase, crit, parkour, combat) all fight for control simultaneously without a central arbitration system. The current `KeyMovementController` has two modes (override and supplement) but:
1. **Override** is full-replacement (too aggressive)
2. **Supplement** is OR-based (too passive)
3. No priority system to determine which system wins
4. No concept of "movement intent" — each system just pushes raw booleans

### The Fix: Priority-Based Movement Arbitration

#### A. MovementIntent class
```java
public class MovementIntent {
    public enum Priority {
        DODGE(100),
        CLUTCH(95),
        CRIT(70),
        CHASE(50),
        COMBAT_STRAFE(30),
        AUTO_WALK(10);
        
        final int value;
    }
    
    Priority priority;
    Vec2 moveVector; // strafe, forward
    boolean jump;
    boolean sprint;
    boolean sneak;
    int durationTicks; // 0 = continuous until replaced
    String reason;
}
```

#### B. MovementResolver
```java
public class MovementResolver {
    private MovementIntent currentIntent;
    private final NavigableMap<Integer, MovementIntent> pendingIntents = new TreeMap<>();
    
    public void submitIntent(MovementIntent intent) {
        pendingIntents.put(intent.priority.value, intent);
    }
    
    public void resolve() {
        // Highest priority wins
        if (!pendingIntents.isEmpty()) {
            currentIntent = pendingIntents.lastEntry().getValue();
        }
        pendingIntents.clear();
    }
    
    public Input getOverriddenInput(Input original) {
        if (currentIntent == null) return original;
        return blend(original, currentIntent);
    }
}
```

#### C. Three-layer input model
1. **Supplement Layer** (additive) — auto-walk, sprint assist, bridge assist. OR with player input.
2. **Steering Layer** (yaw control) — chase, circle strafe, combo lock. Rotate yaw, don't override WASD.
3. **Emergency Layer** (hard override) — dodge, clutch, void save. Full override, <5 ticks max.

---

## BUG #8: PLAYER TAKES TOO MUCH DAMAGE
### Location: DodgeSystem, ClutchSystem
### Root Cause
Multiple issues:
1. Dodge might not trigger early enough (reaction time too slow)
2. Dodge direction might move TOWARD danger instead of away
3. Clutch doesn't activate in time for fall damage
4. No prediction of incoming damage (only reactive)

### The Fix

#### A. Threat Timeline Simulation
Instead of dodging where danger IS, dodge where danger WILL BE:
```java
// For projectiles
Vec3 simulateProjectile(Entity projectile, int ticksAhead) {
    Vec3 pos = projectile.position();
    Vec3 vel = projectile.getDeltaMovement();
    for (int t = 0; t < ticksAhead; t++) {
        pos = pos.add(vel);
        vel = vel.scale(0.99); // drag
        vel = vel.add(0, -0.05, 0); // gravity
    }
    return pos;
}

// Check if projectile will intersect player in N ticks
boolean willHit(Entity projectile, Player player, int maxTicks) {
    for (int t = 0; t < maxTicks; t++) {
        Vec3 projPos = simulateProjectile(projectile, t);
        Vec3 playerPos = player.position().add(player.getDeltaMovement().scale(t));
        if (projPos.distanceToSqr(playerPos) < 2.0) return true;
    }
    return false;
}
```

#### B. Perpendicular Evasion
For projectiles, dodge PERPENDICULAR to trajectory (not away from source):
```java
Vec3 perpendicularEscape(Entity projectile, Player player) {
    Vec3 trajectory = projectile.getDeltaMovement().normalize();
    Vec3 toPlayer = player.position().subtract(projectile.position()).normalize();
    // Cross product with Y-axis = perpendicular
    Vec3 perp = new Vec3(-trajectory.z, 0, trajectory.x);
    // Choose direction that moves away from trajectory
    if (perp.dot(toPlayer) < 0) perp = perp.scale(-1);
    return perp;
}
```

#### C. Pre-emptive dodging
Dodge when threat enters range, NOT when damage is about to land:
- Projectile enters 15-block radius → begin evasion
- Player aims bow at you → zigzag immediately
- Creeper within 5 blocks → sprint away

#### D. Jump reset on damage
```java
// When taking damage, jump to convert horizontal KB to vertical
if (hurtTime == maxHurtTime - 1) { // just got hit
    input.jump = true; // jump reset
}
```

---

## BUG #9: DODGE MOVEMENT NOT REAPPLIED
### Location: DodgeSystem.java
### Root Cause (already identified in AGENTS.md)
`dodgeTicks > 0` block only called `ctrl.moveToward()` in dodgeOnly mode. When movement was ON, WASD direction set once but never reapplied.

### The Fix (already applied per AGENTS.md)
Always call `ctrl.moveToward()` every tick during dodge. Also apply setDeltaMovement in dodgeOnly mode.

---

## BUG #10: SUPPLEMENT MODE BLOCKING PLAYER INPUT
### Location: KeyMovementController.getOverriddenInput()
### Root Cause (already identified in AGENTS.md)
`getOverriddenInput()` had `!active` check allowing override when active but no override set.

### The Fix (already applied per AGENTS.md)
Remove `!active` check — only `!hasActiveOverride` gates the override.

---

## UPGRADE: PRO PVP MOVEMENT PATTERNS
### Reference: Competitive PVP mechanics research

#### A. W-Tap (Sprint Reset)
```java
// After each hit, release W briefly for 1-2 ticks
if (justLandedHit) {
    wTapTimer = 2; // 2 ticks of W release
    sprintResetRequested = true;
}
// During wTapTimer: don't press W forward
if (wTapTimer > 0) {
    wTapTimer--;
    intent.forward = false; // KEY: cancel forward for sprint reset
}
```

#### B. S-Tap (Distance Creation)
```java
// Tap S after hit to create distance
if (justLandedHit && distance < 2.0) {
    sTapTimer = 1;
}
if (sTapTimer > 0) {
    sTapTimer--;
    intent.backward = true;
}
```

#### C. Block Hit timing (shield version)
```java
// Right-click shield just before taking damage
if (enemyAttackWindup && hurtTime == 0) {
    intent.sneak = true; // or right-click shield
}
```

#### D. Jump Reset
```java
// Jump 1 tick BEFORE taking damage
if (predictedDamageTick) {
    intent.jump = true;
    // Don't sprint during crit
    intent.sprint = false;
}
```

#### E. Strafe Oscillation
```java
// Oscillate strafe direction every 8-15 ticks
strafeTimer++;
if (strafeTimer > strafeInterval) {
    strafeTimer = 0;
    strafeDirection = -strafeDirection; // flip
    strafeInterval = 8 + random.nextInt(8); // randomize interval
}
intent.strafe = strafeDirection; // -1 or +1
```

#### F. Combo Pressure
```java
// After landing 2+ consecutive hits, increase aggression
comboCounter++;
if (comboCounter >= 2) {
    // Reduce strafe, increase forward pressure
    intent.forward = true;
    intent.sprint = true;
    // Aim more aggressively
    aimSpeed = Math.min(1.0f, aimSpeed + 0.1f);
}
if (missedHit || gotHit) {
    comboCounter = 0;
}
```

#### G. Distance Management
```java
if (distance > optimalRange * 1.2) {
    // Too far — close distance aggressively
    intent.forward = true;
    intent.sprint = true;
} else if (distance < optimalRange * 0.8) {
    // Too close — create space
    intent.backward = true;
    intent.sprint = false;
} else {
    // Optimal range — strafe fight
    swayStrafing(delta);
}
```

#### H. Crit Pattern
```java
// Proper 2-tick crit sequence
if (shouldCrit && state == CRIT_IDLE) {
    state = CRIT_SPRINT_CANCEL; // Tick 1: cancel sprint
    intent.sprint = false;
} else if (state == CRIT_SPRINT_CANCEL) {
    state = CRIT_JUMP; // Tick 2: jump
    intent.jump = true;
    intent.sprint = false;
} else if (state == CRIT_JUMP) {
    state = CRIT_AIRBORNE; // Airborne — wait for fall
    airTicks = 0;
} else if (state == CRIT_AIRBORNE) {
    airTicks++;
    if (airTicks >= 3 && targetInRange) {
        // Attack on way down — crit is ready
        shouldAttack = true;
    }
    if (onGround) {
        state = CRIT_IDLE; // Landed
    }
}
```

---

## UPGRADE: ENTITY TRACKING OPTIMIZATION (FPS)
### Location: Entity scanning code
### Problem
Every subsystem calls `level.getEntitiesOfClass(Entity.class, bigBox)` every tick. Multiple scans per frame = FPS death.

### Fix: Centralized EntityTrackerSystem
```java
public class EntityTrackerSystem {
    private List<Entity> nearbyEntities = new ArrayList<>();
    private int scanCooldown = 0;
    private static final int SCAN_INTERVAL = 2; // Scan every 2 ticks
    
    public void tick(Level level, Player player) {
        scanCooldown--;
        if (scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            // Single scan per interval
            nearbyEntities = level.getEntitiesOfClass(
                Entity.class,
                player.getBoundingBox().inflate(config.getDetectionRange()),
                e -> e != player && e.isAlive()
            );
        }
    }
    
    public List<Entity> getEntities() { return nearbyEntities; }
    
    public <T extends Entity> List<T> getEntitiesByType(Class<T> type) {
        return nearbyEntities.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .collect(Collectors.toList());
    }
}
```

---

## UPGRADE: MOVEMENT PREDICTOR (KINEMATIC + BEHAVIORAL)
### Location: Prediction system
### Current
Simple linear extrapolation (position + velocity * time). Poor for erratic targets.

### Fix: Multi-system prediction

#### A. Kinematic Predictor (projectiles, steady targets)
```java
Vec3 kinematicPredict(LivingEntity target, float ticksAhead) {
    Vec3 pos = target.position();
    Vec3 vel = target.getDeltaMovement();
    float friction = target.onGround() ? 0.91f : 0.98f;
    for (int t = 0; t < ticksAhead; t++) {
        vel = vel.scale(friction);
        pos = pos.add(vel);
    }
    return pos;
}
```

#### B. Behavioral Predictor (player patterns)
```java
enum MovementPattern {
    LINEAR, STRAFE_LEFT, STRAFE_RIGHT, JUMPING, IDLE, ZIGZAG
}

MovementPattern classifyPattern(LivingEntity target) {
    // Analyze last 20 ticks of movement
    // Check velocity changes, strafe frequency, jump pattern
}

Vec3 behavioralPredict(LivingEntity target, float ticksAhead) {
    MovementPattern pattern = classifyPattern(target);
    switch (pattern) {
        case STRAFE_LEFT:  return predictStrafe(target, ticksAhead, -1);
        case STRAFE_RIGHT: return predictStrafe(target, ticksAhead, 1);
        case ZIGZAG:       return predictZigzag(target, ticksAhead);
        case JUMPING:      return predictJumpArc(target, ticksAhead);
        default:           return kinematicPredict(target, ticksAhead);
    }
}
```

#### C. Confidence scoring
```java
float confidence = 1.0f;
if (target.hurtTime > 0) confidence *= 0.7f;   // hit = less predictable
if (target.isSprinting()) confidence *= 1.2f;    // sprinting = more predictable
if (distance > 20) confidence *= 0.8f;           // far = less predictable
if (target.hasEffect(MobEffects.SLOWNESS)) confidence *= 1.3f; // slowed = predictable
```

---

## UPGRADE: ACTION BUFFER QUEUE
### Location: New file
### Purpose
Buffer actions N ticks ahead to handle timing-critical sequences (crit, W-tap, shield).

```java
public class ActionBuffer {
    private final Queue<BufferedAction> queue = new LinkedList<>();
    
    public void enqueue(int delayTicks, Runnable action, String name) {
        queue.add(new BufferedAction(delayTicks, action, name));
    }
    
    public void tick() {
        for (BufferedAction action : queue) {
            action.ticksRemaining--;
        }
        while (!queue.isEmpty() && queue.peek().ticksRemaining <= 0) {
            queue.poll().action.run();
        }
    }
}

record BufferedAction(int ticksRemaining, Runnable action, String name) {}
```

Usage:
```java
// Schedule a crit sequence
actionBuffer.enqueue(1, () -> mc.options.keySprint.setDown(false));
actionBuffer.enqueue(2, () -> player.jumpFromGround());
actionBuffer.enqueue(4, () -> mc.options.keyAttack.click());
actionBuffer.enqueue(5, () -> mc.options.keySprint.setDown(true));
```

---

## UPGRADE: TACTICAL MOVEMENT GRAPH
### Location: New file (`TacticalMovementGraph.java`)
### Purpose
Every nearby block becomes a movement node. Edges = jump, bridge, tower, dodge. AI chooses highest-momentum combat path.

```java
public class TacticalMovementGraph {
    private static final int SCAN_RADIUS = 8;
    private final Node[][][] nodes = new Node[SCAN_RADIUS*2+1][5][SCAN_RADIUS*2+1];
    
    record Node(BlockPos pos, boolean solid, boolean passable, boolean hazard) {}
    record Edge(Node from, Node to, MoveType type, float cost) {}
    
    enum MoveType {
        WALK, SPRINT, JUMP, BRIDGE, TOWER, DODGE, CLIMB
    }
    
    public void rebuild(Level level, Player player) {
        BlockPos center = player.blockPosition();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    nodes[x+SCAN_RADIUS][y+2][z+SCAN_RADIUS] = new Node(
                        pos, state.isSolid(), state.isPassable(), isHazard(state)
                    );
                }
            }
        }
    }
    
    public Vec2 getBestDirection(Player player, LivingEntity target) {
        // A* or greedy best-first search for most advantageous move
    }
}
```

---

## UPGRADE: COMBAT RHYTHM ENGINE
### Location: New file (`CombatRhythmEngine.java`)
### Purpose
Build tempo states: `pressure → reset → burst → bait → re-engage`

```java
public class CombatRhythmEngine {
    enum Tempo {
        PRESSURE,   // Aggressive forward, sprint, chase
        RESET,      // Back off, create space, heal
        BURST,      // Full aggression, crit combo, sprint
        BAIT,       // Fake retreat, draw opponent in
        RE_ENGAGE,  // Turn around, punish over-extension
        FINISH      // Low health opponent — chase and kill
    }
    
    private Tempo currentTempo = Tempo.PRESSURE;
    private int tempoTicks = 0;
    
    public void update(Player player, LivingEntity target) {
        tempoTicks++;
        float myHealth = player.getHealth();
        float targetHealth = target.getHealth();
        float distance = player.distanceTo(target);
        
        // State transitions
        if (targetHealth < 4) {
            setTempo(Tempo.FINISH); // Low HP target — kill
        } else if (myHealth < 6 && distance < 4) {
            setTempo(Tempo.RESET); // Low HP + close — retreat
        } else if (myHealth < myHealth * 0.3) {
            setTempo(Tempo.RESET); // Low HP — heal
        } else if (distance > 8 && myHealth > 10) {
            setTempo(Tempo.BURST); // Gap close
        } else if (tempoTicks > 40 && currentTempo == Tempo.PRESSURE) {
            setTempo(Tempo.RESET); // Periodic reset
        }
    }
    
    private void setTempo(Tempo newTempo) {
        if (currentTempo != newTempo) {
            currentTempo = newTempo;
            tempoTicks = 0;
        }
    }
}
```

---

## UPGRADE: AIR STRAFE CONTROLLER
### Location: New file (`AirStrafeController.java`)
### Purpose
Quake-style air control. While airborne, compute yawDiff and apply A/D for maximum velocity.

```java
public class AirStrafeController {
    public Vec2 computeAirInput(Player player, float targetYaw) {
        if (player.onGround()) return new Vec2(0, 0);
        
        Vec3 velocity = player.getDeltaMovement();
        float currentYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        
        float strafe = 0;
        if (yawDiff > 10) strafe = 1;     // Turn right
        else if (yawDiff < -10) strafe = -1; // Turn left
        
        float forward = Math.abs(yawDiff) < 90 ? 1 : 0;
        
        return new Vec2(strafe, forward);
    }
}
```

---

## UPGRADE: JUMP INTENT CLASSIFICATION
### Location: New file/class
### Purpose
Not all jumps are equal. Classify and handle each type:

| Jump Type | Trigger | Behavior |
|-----------|---------|----------|
| MICRO_HOP | Momentum maintenance | Short hop, minimal height |
| COMBAT_HOP | Crit timing | Max height, sprint cancel |
| GAP_JUMP | Bridge gap detected | Forward jump, extended |
| CLIMB_JUMP | Obstacle ahead | Jump + forward, precise |
| DODGE_JUMP | Threat detected | Directional, strafe + jump |
| TOWER_JUMP | Target above | Jump + block place below |
| REVERSE_JUMP | Combo reset | Backward jump, surprise |

```java
enum JumpIntent {
    MICRO_HOP(1, 0.3f),
    COMBAT_HOP(2, 0.6f),
    GAP_JUMP(3, 1.0f),
    CLIMB_JUMP(2, 0.5f),
    DODGE_JUMP(1, 0.4f),
    TOWER_JUMP(2, 0.7f),
    REVERSE_JUMP(1, 0.2f);
    
    final int chargeTicks;
    final float heightMultiplier;
}

class JumpController {
    private JumpIntent currentJump = null;
    private int jumpTicks = 0;
    
    public void requestJump(JumpIntent intent) {
        if (currentJump == null || intent.chargeTicks > currentJump.chargeTicks) {
            currentJump = intent;
            jumpTicks = intent.chargeTicks;
        }
    }
    
    public boolean shouldJump() {
        if (currentJump == null) return false;
        jumpTicks--;
        return jumpTicks <= 0;
    }
}
```

---

## REFERENCE: KEY MOVEMENT FORMULAS (1.21.1)

### Travel Formula
```java
// Player.travel(Vec3 movementInput)
// movementInput.x = strafe (xxa), movementInput.z = forward (zza)
// xxa: positive = left, negative = right
// zza: positive = forward, negative = backward

float yawRad = player.getYaw() * Math.PI / 180;
float sin = Math.sin(yawRad);
float cos = Math.cos(yawRad);

double dx = (xxa * cos - zza * sin) * speed;
double dz = (xxa * sin + zza * cos) * speed;
```

### Sprint Speed
```
Walking speed:   0.1 acceleration, cap at ~4.317 m/s
Sprinting speed: 0.13 acceleration, cap at ~5.612 m/s
Sneaking speed:  0.03 acceleration, cap at ~1.3 m/s
```

### Air Movement
```
Air acceleration = ground * 0.2
Air drag = 0.91 (no block friction)
```

### Jump Velocity
```
Jump velocity = 0.42 m/s upward (0.42f)
sqrt(2 * gravity * height) where gravity = 0.08 blocks/tick²
Max jump height = ~1.25 blocks
Holding jump = +1 tick of upward velocity
```

### Diagonal Movement
```
Diagonal speed penalty: strafe + forward both active
-> fwd *= 0.7071, strafe *= 0.7071
Result: ~same speed as cardinal direction (not faster)
```

### Critical Hit Requirements
```
1. fallDistance > 0.0
2. !onGround()
3. !onClimbable()
4. !isInWater() && !isInLava()
5. !isMobilityRestricted()
6. !isPassenger()
7. !isSprinting() ← CRITICAL
8. attackStrengthScale > 0.9
9. Target instanceof LivingEntity
```

### Attack Cooldown
```
Sword (1.6 speed): 12.5 ticks
Axe (1.0 speed): 20 ticks
Full damage threshold: 84.8% of cooldown
Damage multiplier = 0.2 + (progress)² * 0.8
```

---

## REFERENCE: ServerboundPlayerInputPacket
```java
// xxa: positive = LEFT strafe, negative = RIGHT
// zza: positive = FORWARD, negative = BACKWARD
// isJumping: boolean
// isShiftKeyDown: boolean (sneak)

// This is sent EVERY tick from client to server
public ServerboundPlayerInputPacket(float xxa, float zza, boolean isJumping, boolean isShiftKeyDown)
```

**IMPORTANT**: The mixin replaces `input.keyPresses` (a `PlayerInput` record with booleans) AND `moveVector` (a `Vec2`). The `moveVector` maps directly to the xxa/zza values sent in the packet. If the mixin computes strafe as `(right-left)`, it gets the direction WRONG because the network protocol expects `positive = left`.

---

## REFERENCE: Fabric HUD API (1.21.1+)
```java
// Fabric API 0.116+
// Use HudElementRegistry instead of deprecated HudRenderCallback

HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, 
    Identifier.of("shinigami", "hud_element"),
    (graphics, deltaTracker) -> {
        // Render using GuiGraphics
        graphics.drawString(font, "Target: Zombie", 10, 10, 0xFFFFFF);
    });

// Or attach relative to other elements:
attachElementAfter(id, before, element)
attachElementBefore(id, after, element)
replaceElement(id, replacer)
addFirst(id, element)
addLast(id, element)
```

---

## REFERENCE: Config GUI Tab System
The `AimAssistScreen` uses tabbed categories. To add a VISUAL tab:
```java
// Example pattern from existing COMBAT/MOVEMENT tabs:
addTab("VISUAL", (graphics, width) -> {
    // Toggle: ESP
    addToggle(graphics, x, y, "ESP", config::isEsp, config::setEsp);
    // Color picker
    addColorPicker(graphics, x, y + 20, "ESP Color", config::getEspColor, config::setEspColor);
    // Toggle: Tracers
    addToggle(graphics, x, y + 40, "Tracers", config::isTracers, config::setTracers);
    // Slider: Opacity
    addSlider(graphics, x, y + 60, "ESP Opacity", 0.0, 1.0, config::getEspOpacity, config::setEspOpacity);
});
```

World render events for ESP/tracers:
```java
// Register in client init
WorldRenderEvents.AFTER_ENTITIES.register(context -> {
    if (!config.isEsp()) return;
    // Render bounding boxes, tracers
});
```
