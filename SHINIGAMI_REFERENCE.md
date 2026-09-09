# Shinigami by Saizo — Complete Project Reference

> **Minecraft 26.1 (1.21.1) Combat Movement AI Framework**
> Fabric mod, client-side only. Mod ID: `shinigami`
> Built by: Saizo | Platform: Termux (Android) | JDK: Java 25 Temurin ARM64

---

## Table of Contents

1. [Build & Deploy](#1-build--deploy)
2. [Project Layout](#2-project-layout)
3. [Architecture Overview](#3-architecture-overview)
4. [Input Pipeline (The Mixin Stack)](#4-input-pipeline-the-mixin-stack)
5. [Movement Arbitration (CMS)](#5-movement-arbitration-cms)
6. [Combat Systems](#6-combat-systems)
7. [Prediction Engine](#7-prediction-engine)
8. [Targeting System](#8-targeting-system)
9. [Rendering & Visuals](#9-rendering--visuals)
10. [Config Reference](#10-config-reference)
11. [Commands & Keybinds](#11-commands--keybinds)
12. [All Applied Fixes](#12-all-applied-fixes)
13. [A/D Root Cause Analysis](#13-ad-root-cause-analysis)
14. [Bug Inventory](#14-bug-inventory)
15. [PVP Research Reference](#15-pvp-research-reference)
16. [Movement Formulas (26.1)](#16-movement-formulas-261)
17. [Import Reference (Mojang Mappings 1.21.1)](#17-import-reference-mojang-mappings-1211)
18. [Next-Gen Roadmap](#18-next-gen-roadmap)

---

## 1. Build & Deploy

### Prerequisites
```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64
```

### Full Build
```bash
./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon
```

### Quick Compile-Only
```bash
./gradlew :fabric:compileJava --no-daemon 2>&1 | tail -10
```

### Deploy
```bash
cp fabric/build/libs/baritone-fabric-1.17.0.jar /storage/emulated/0/1log/Shinigami-by-Saizo-1.17.0.jar
```

### Build Flags
| Flag | Purpose |
|------|---------|
| `-x test` | Skip tests (many fail on 26.1 APIs) |
| `-x :fabric:proguard` | Skip obfuscation (dev only) |
| `-x :fabric:createDist` | Skip distribution packaging |
| `--no-daemon` | Prevent daemon conflicts on Termux |

### Toolchain
- JDK: `/usr/lib/jvm/java-25-openjdk-arm64`
- Gradle: 8.14.4 (wrapper)
- Platform: Forge 26.1-62.0.9
- Mappings: Mojang official (1.21.1)
- Output JAR: `baritone-fabric-1.17.0.jar` (~2.0MB)
- Install path: Zalith Launcher 1.4.1.4, Minecraft 26.1.2 Fabric 0.19.2

---

## 2. Project Layout

```
baritone-26.1/
├── fabric/src/main/java/baritone/
│   ├── aimassist/                          # MOD CORE (~50 files)
│   │   ├── AimAssistMod.java               # Entry point, wires all systems
│   │   ├── AimAssistConfig.java            # All config fields + getters/setters
│   │   ├── AimAssistModule.java            # Central module: tick(), tickMovement()
│   │   ├── AimAssistKeybinds.java          # Keybind registration + command dispatch
│   │   ├── AimAssistOverlay.java           # HUD overlay rendering
│   │   ├── AimAssistScreen.java            # Config GUI (G key), tabbed layout
│   │   ├── RESEARCH_FIXES.md               # Bug research + fix reference
│   │   ├── aim/
│   │   │   ├── AimController.java          # Rotation calculation + smoothing
│   │   │   ├── PIDController.java          # PID-based aim smoothing
│   │   │   └── RotationSmoother.java       # Rotation smoothing helper
│   │   ├── combat/
│   │   │   ├── AutoCombatSwitch.java       # Weapon switching logic
│   │   │   ├── AutoInventory.java          # Inventory management
│   │   │   ├── AutoTotem.java              # Auto totem equip
│   │   │   ├── AutoUtil.java               # Sprint assist, shield assist
│   │   │   ├── BowAssist.java              # Bow aim assist
│   │   │   ├── BridgeAssist.java           # Auto bridging
│   │   │   ├── ClutchSystem.java           # Fall clutch (water/hay/ladder)
│   │   │   ├── CombatPeripherals.java      # Crystal/gear/loot/smelt assists
│   │   │   ├── ComboTracker.java           # Combo counter + pressure tracking
│   │   │   ├── CritAssist.java             # Auto-crit state machine
│   │   │   ├── DodgeSystem.java            # 19-type threat detection + dodge
│   │   │   ├── MaceAssist.java             # Mace smash detection
│   │   │   ├── ScreenControl.java          # Screen state controller
│   │   │   ├── TriggerBot.java             # Auto-attack trigger
│   │   │   └── WindBurstAssist.java        # Wind burst usage
│   │   ├── movement/
│   │   │   ├── MovementIntent.java         # Intent class (priority, dir, jump, etc)
│   │   │   ├── MovementArbiter.java        # CMS resolver: picks highest-priority intent
│   │   │   ├── MovementBrain.java          # Combat flow state machine + noise + action buffer
│   │   │   ├── AirStrafeController.java    # Continuous air strafing toward target yaw
│   │   │   └── TerrainAnalyzer.java        # Block affordance classification
│   │   ├── pathfinding/
│   │   │   └── AggressiveParkour.java      # Parkour pathing (stub/early)
│   │   ├── prediction/
│   │   │   ├── MovementPredictor.java       # Master predictor (kinematic + behavioral + jump)
│   │   │   ├── BehavioralPredictor.java     # Pattern classifier (6 player patterns)
│   │   │   ├── KalmanFilter.java           # 1D Kalman filter for position smoothing
│   │   │   ├── VelocityEstimator.java      # Velocity estimation from position history
│   │   │   └── JumpArcPredictor.java        # Jump arc prediction
│   │   ├── render/
│   │   │   └── AimAssistRenderer.java      # ESP, tracers, prediction ghost, health bar
│   │   ├── system/
│   │   │   ├── EntityTrackerSystem.java    # Centralized entity scanning (every 2-4 ticks)
│   │   │   ├── AreaManager.java            # Named area management
│   │   │   ├── DurabilityManager.java      # Item durability repair management
│   │   │   ├── PortalManager.java          # Portal building
│   │   │   └── UnderwaterBreathing.java    # Auto-surface logic
│   │   ├── tags/
│   │   │   ├── ChaseBehavior.java          # Chase/kill with parkour (WASD only)
│   │   │   ├── TagSystem.java              # Entity tagging system
│   │   │   ├── SmartTaskExecutor.java      # Task execution queue
│   │   │   ├── ResourceManager.java        # Resource tracking
│   │   │   └── TaskPlanner.java            # Task planning
│   │   ├── targeting/
│   │   │   ├── TargetManager.java          # Target selection + validation
│   │   │   └── TrackedTarget.java          # Tracked entity with scoring
│   │   └── util/
│   │       └── KeyMovementController.java  # WASD input override (override + supplement modes)
│   └── launch/mixins/
│       ├── MixinAimAssistClient.java       # Minecraft.tick() HEAD+TAIL
│       ├── MixinKeyboardInput.java         # KeyboardInput.tick() TAIL (primary override)
│       ├── MixinLocalPlayerInput.java      # LocalPlayer.onInput() HEAD+TAIL (secondary)
│       ├── MixinAimAssistChat.java         # Chat message intercept (#, & prefixes)
│       ├── MixinKeyboardHandler.java       # Keyboard event interceptor
│       └── ClientInputAccessor.java        # @Accessor for moveVector (avoids @Shadow)
├── src/api/java/baritone/api/aimassist/
│   ├── IAimAssist.java                    # Public API: mod interface
│   ├── IAimConfig.java                    # Public API: config interface
│   ├── IAimTarget.java                    # Public API: target interface
│   └── PredictionData.java                # Public API: prediction data record
└── AGENTS.md                              # Project context for AI assistant
```

---

## 3. Architecture Overview

### Layered Dependency Order
```
AimAssistConfig (data layer)
    → AimAssistMod (wiring layer — creates all systems)
        → KeyMovementController (input override management)
        → AimAssistModule (central hub — tick orchestrator)
            ├── ChaseBehavior (WASD chase + parkour)
            ├── DodgeSystem (19 threat types → dodge)
            ├── ClutchSystem (fall clutch)
            ├── CritAssist (auto-crit state machine)
            ├── Combat systems (bow, mace, bridge, combo, etc.)
            ├── TargetManager (entity selection)
            ├── MovementBrain (combat flow state machine)
            ├── MovementArbiter (intent resolver)
            ├── AirStrafeController (continuous air strafe)
            ├── TerrainAnalyzer (block affordance)
            ├── EntityTrackerSystem (entity caching)
            └── TagSystem (entity tags)
        → AimAssistScreen (config GUI)
        → AimAssistKeybinds (keyboard commands)
        → AimAssistOverlay (HUD)
        → AimAssistRenderer (world ESP/tracers)
```

### Tick Timeline (Minecraft.tick())
```
HEAD injection (MixinAimAssistClient @At("HEAD")):
  └─ AimAssistMod.onPreClientTick()
       ├─ movementController.setActive()
       ├─ movementController.tick()
       └─ module.tickMovement()
            ├─ movementArbiter.clear()
            ├─ ctrl.clearSupplement()
            ├─ Chase → submits MovementIntent via arbiter
            ├─ AirStrafe → submits MovementIntent
            ├─ DodgeSystem → submits MovementIntent
            └─ movementArbiter.apply(ctrl) → resolves winner → applies to controller

├── Entity tick starts:
│   ├─ KeyboardInput.tick() → sets keyPresses + moveVector (vanilla)
│   ├─ MIXIN: MixinKeyboardInput TAIL → replaces keyPresses + moveVector
│   ├─ modifyInput() → rotates moveVector by yaw (or onInput())
│   ├─ MIXIN: MixinLocalPlayerInput HEAD+TAIL → replaces keyPresses
│   ├─ Player.travel() → uses moveVector for physics
│   └─ Entity tick ends

TAIL injection (MixinAimAssistClient @At("TAIL")):
  └─ AimAssistMod.onClientTick()
       ├─ keybinds.handleKeybinds()
       └─ module.tick()
            ├─ entityTracker.tick()
            ├─ movementBrain.tick()
            ├─ tagSystem.processTags()
            ├─ tickMovement() [again, for next tick's supplement setup]
            ├─ clutchSystem / smartTask / autoInventory / durability / breathing
            ├─ targetManager.tick() → IAimTarget selection
            ├─ aimController.updatePredictions()
            ├─ critAssist / maceAssist / bowAssist / bridgeAssist
            ├─ handleNormalAim() → rotation calculation + smoothing
            ├─ Attack flow (crit, combo, mace smash)
            ├─ triggerBot / dodgeSystem / comboTracker
            └─ cleanup
```

### Three Input Layers

| Layer | Mode | Duration | Set By | Effect |
|-------|------|----------|--------|--------|
| **Supplement** | Additive (OR) | Per tick | `supplementForward()`, `supplementJump()` | Blends with player input |
| **Override** | Full replacement | 5-tick TTL | `moveToward()`, `strafeAround()` | Replaces ALL WASD input |
| **Passthrough** | No override | Always | Default | Player input unchanged |

---

## 4. Input Pipeline (The Mixin Stack)

### Injection Points (6 mixins)

```mermaid
Minecraft.tick()
├── @At("HEAD") → MixinAimAssistClient.onPreTick()
│   └─ Sets supplement + override state BEFORE entity tick
│
├── Player.tick() → KeyboardInput.tick()
│   ├─ [VANILLA] options → Input record → keyPresses → moveVector
│   └─ @At("TAIL") → MixinKeyboardInput.onTickTail()
│       ├─ ctrl.getOverriddenInput(self.keyPresses)
│       ├─ self.keyPresses = overridden Input
│       ├─ strafe = (left-right), forward = (fwd-bwd)
│       └─ setMoveVector(new Vec2(strafe, forward))
│
├── LocalPlayer.onInput(ClientInput)
│   ├─ @At("HEAD") → MixinLocalPlayerInput.replaceInput()
│   ├─ [BODY] → rotates moveVector by yaw (if applicable)
│   └─ @At("TAIL") → MixinLocalPlayerInput.replaceInput()
│
├── Player.travel(Vec3) → reads moveVector for physics
│
└── @At("TAIL") → MixinAimAssistClient.onTick()
    └─ Keybinds + module.tick() with fresh entity positions
```

### MixinKeyboardInput (Primary)
```java
@Inject(method = "tick", at = @At("TAIL"))
// Fires BEFORE modifyInput() and onInput()
// Replaces BOTH keyPresses AND moveVector

Input overridden = ctrl.getOverriddenInput(self.keyPresses);
self.keyPresses = overridden;

float forward = (overridden.forward() ? 1 : 0) - (overridden.backward() ? 1 : 0);
float strafe = (overridden.left() ? 1 : 0) - (overridden.right() ? 1 : 0);
if (forward != 0 && strafe != 0) { forward *= 0.7071f; strafe *= 0.7071f; }
((ClientInputAccessor)self).setMoveVector(new Vec2(strafe, forward));
```

### MixinLocalPlayerInput (Secondary)
```java
@Inject(method = "onInput", at = @At("HEAD"))
@Inject(method = "onInput", at = @At("TAIL"))
// Replaces keyPresses at both HEAD and TAIL of onInput()
// Catches any pipeline changes after modifyInput()
clientInput.keyPresses = ctrl.getOverriddenInput(clientInput.keyPresses);
```

### ClientInputAccessor
- `@Accessor` mixin targeting `ClientInput.moveVector`
- Avoids `@Shadow` which requires refmap (not generated in build)
- Cast `this` to `ClientInputAccessor` → `setMoveVector(Vec2)`

### KeyMovementController.getOverriddenInput() Logic

```java
if (hasActiveOverride) {
    // Full override: return stored boolean overrides
    return new Input(overrideForward, overrideBackward, overrideLeft, overrideRight,
                     overrideJump, overrideShift, overrideSprint);
}
if (supplementMode) {
    // Blend: OR supplement booleans with original input
    boolean fwd = original.forward() || supplementForward;
    boolean bwd = original.backward();
    if (fwd && bwd) bwd = false; // prevent forward+backward conflict
    return new Input(fwd, bwd, original.left(), original.right(),
                     original.jump() || supplementJump, original.shift(),
                     original.sprint() || supplementSprint);
}
return original; // passthrough
```

### Critical Timing: Supplement Lifecycle
1. `tickMovement()` at HEAD: `ctrl.clearSupplement()` then re-sets supplement for chase/auto-walk
2. `KeyboardInput.tick()` fires during entity tick → mixin applies supplement
3. `tickMovement()` called AGAIN at TAIL (inside `module.tick()`): clears + re-sets for NEXT tick

---

## 5. Movement Arbitration (CMS)

### MovementIntent Priority Tiers
```java
public enum Priority {
    DODGE(100),    // Emergency evade — highest
    CLUTCH(95),    // Fall clutch
    CRIT(70),      // Crit approach timing
    CHASE(50),     // Chase target movement
    COMBAT(30),    // Combat strafe
    PARKOUR(20),   // Parkour/air strafe
    AUTO_WALK(10); // Auto forward movement
}
```

### MovementArbiter Resolver Logic
```java
// 1. Collect intents from all subsystems via submit()
// 2. Pick highest priority
// 3. Apply based on priority:
//    DODGE/CLUTCH/CRIT (≥70): ctrl.moveToward() — full override
//    CHASE/PARKOUR (≥20):     ctrl.supplementForward() — additive
//    COMBAT/AUTO_WALK (<20):   ctrl.supplementForward() — additive
```

### MovementIntent Fields
```java
public final Priority priority;   // Priority tier
public final Vec3 direction;      // Normalized world direction
public final boolean sprint;      // Request sprint
public final JumpType jumpType;   // NONE | MICRO_HOP | COMBAT_HOP | GAP_JUMP | CLIMB_JUMP | DODGE_JUMP | TOWER_JUMP | REVERSE_JUMP
public final boolean sneak;       // Request sneak
public final int durationTicks;   // How long this intent should last
public final String reason;       // Debug label
```

### MovementBrain (Combat Flow State Machine)
```java
public enum CombatFlow {
    APPROACH(aggression=0.3, sprint=1.0, dodge=0.3, crit=0.0),  // Moving into range
    PRESSURE(0.6, 0.8, 0.5, 0.2),                                // Sustained combat
    EVADE(0.1, 0.3, 0.8, 0.6),                                   // Dodging (low HP)
    RESET(0.2, 0.2, 0.9, 0.3),                                    // Disengage
    BURST(0.9, 1.0, 0.2, 0.8),                                   // Aggressive push
    FINISH(1.0, 1.0, 0.1, 1.0),                                   // Low enemy HP
    RETREAT(0.0, 0.1, 0.9, 0.1);                                  // Low own HP
}
```

State transitions based on: distance to target, own HP ratio, combo ticks.

### AirStrafeController
```java
// Computes yawDiff = targetYaw - velocityYaw (wrapped to [-180, 180])
// If |yawDiff| > 1°: submits strafe direction
// strafeDir = sign(yawDiff) — positive = left turn in Minecraft air physics
// Converts to world direction: strafeVec perpendicular to player yaw
// Priority: PARKOUR(20) — supplement only
```

### TerrainAnalyzer
```java
public enum Affordance {
    SPRINT_SAFE,     // Clear ground + headroom
    JUMPABLE,        // Gap or low obstacle
    CLIMBABLE,       // Ladder/vine
    BRIDGEABLE,      // Gap with bridge blocks available
    HEAD_HIT_RISK,   // Low ceiling
    VOID_RISK,       // Drop into void
    OBSTACLE         // Solid block blocking path
}
```

Methods: `classifyBlock()`, `isGapAhead()`, `isOneBlockObstacleAhead()`, `getJumpDistance()`, `hasHeadroom()`

---

## 6. Combat Systems

### DodgeSystem (19 Threat Types)

Detection priority (highest to lowest):
```
SLOWED → VOID → FALL_DAMAGE → DROWNING → ENVIRONMENT → EFFECT → HUNGER
→ EXPLOSION → POTION → WITHER_SKULL → FIREBALL → SHULKER → MACE
→ CRYSTAL → FALLING_BLOCK → TRIDENT → PROJECTILE → BOW_AIM → MELEE
```

#### Dodge Responses

| Threat | Direction | Jump | Sprint | Strategy |
|--------|-----------|------|--------|----------|
| SLOWED | Forward | Yes | Yes | Sprint out of hazard |
| VOID | Toward solid ground | Yes | Yes | Escape void |
| FALL_DAMAGE | Toward nearest ground | No | No | Cushion fall |
| DROWNING | Upward | Yes | Yes | Surface |
| ENVIRONMENT | Away from hazard | Yes | No | Hazard escape |
| EXPLOSION | Away | On ground | Yes | Sprint from blast |
| POTION | Perpendicular to velocity | Yes | No | Dodge splash |
| WITHER_SKULL | Perpendicular | Yes | Yes | Skull dodge |
| FIREBALL | Away | Yes | Yes | Sprint from fireball |
| MACE | Away or perpendicular | Yes | Yes | Mace evade |
| TRIDENT | Perpendicular or away | Yes | Yes | Projectile juke |
| PROJECTILE | Perpendicular (trajectory) | Yes | Yes | Arrow evade |
| BOW_AIM | Zigzag (alternating) | Varied | Yes | Bow pattern break |
| MELEE (hit) | Away | On ground | Yes | Hit burst |
| MELEE (counter) | Behind attacker | No | No | Counter position |
| MELEE (general) | Perpendicular | On ground | No | Combat strafe |

#### Dodge Integration (MovementIntent Bus)
- `hasActiveDodge()` → returns `dodgeTicks > 0`
- `getDodgeIntent()` → returns `MovementIntent(Priority.DODGE, direction, sprint, jumpType, ...)`
- No longer calls `ctrl.moveToward()` directly — submits via arbiter
- `dodgeOnlyMode` (when `autoDodge=true` but `movementMode=false`): still applies velocity directly

### CritAssist

State machine:
```
STATE 0 (Idle):
  - onGround, !inWater, !climbable, !passenger, !mobilityRestricted
  - attackStrength >= 0.9, target in range
  → STATE 1

STATE 1 (Sprint cancel — 1 tick):
  - setSprinting(false)
  - NO jump (sprint needs 1 tick to propagate)
  → STATE 2

STATE 2 (Jump trigger — 1 tick):
  - jumpFromGround() + supplementJump() via mixin
  → STATE 3, airTicks = 0

STATE 3 (Airborne — 5 ticks):
  - Accumulate fall distance + airborne ticks
  → STATE 4 after 5 ticks

STATE 4 (Crit ready):
  - canCriticalAttack() returns true
  - On attack: apply crit damage
  - On landing: reset to STATE 0
```

### ChaseBehavior

- WASD-only movement (no Baritone pathfinding)
- Target methods: `setTarget(name)`, `setMobHunt(mobType)`, `setTargetUUID(uuid)`
- Modes: Chase (follow), Kill (follow + attack)
- Detection: scans `detectionRange` blocks, matches by UUID → custom name → player name

#### Parkour Features
- **1-block obstacle detection**: `detectOneBlockObstacle()` — solid at feet, air above → auto-jump
- **Gap detection**: `detectGapAhead()` — scans 5 blocks ahead, gap = no solid below + passable above
- **Bridging**: places blocks when stuck at gap (2-tick threshold), `bridgeTicks = 3`
- **Towering**: places block below + jump when target >1.5 blocks above
- **Wind burst**: auto-uses wind charge for vertical boost (60-tick cooldown)
- **Block breaking**: breaks blocking blocks after 8 ticks stuck, auto-equips best tool
- **Sprint**: Always ON when food >6, not in water/lava, distance >2

#### Combat Chase
- Yaw oscillation (±12°) for strafe weave — no A/D touch
- Line-of-sight yaw steering toward target
- supplementForward() for main chase (NOT moveToward — preserves player A/D)
- Emergency moveToward() only for bridging/towering

### ClutchSystem
| Strategy | Min Height | Item | Action |
|----------|-----------|------|--------|
| Water bucket | 4 blocks | WATER_BUCKET | Places below player |
| Hay bale | 8 blocks | HAY_BLOCK | Places at position |
| Ladder/vine | 3 blocks | LADDER/VINE | Places at feet |

Cooldown: 100ms between attempts.

### ComboTracker
- Tracks: `comboCounter`, `lastHitTime`, `consecutiveHits`, W-tap state
- Attack threshold: `50%` strength in combo mode (vs 90% normal)

### Other Combat Systems
- **MaceAssist**: Smash detection when falling > minSmashHeight, mace in hand
- **BowAssist**: Bow aim with draw timing
- **BridgeAssist**: Auto-bridging when at edge
- **WindBurstAssist**: Wind charge usage for vertical mobility
- **TriggerBot**: Auto-attack when crosshair on target
- **AutoCombatSwitch**: Automatic weapon switching based on context

---

## 7. Prediction Engine

### MovementPredictor (Master)
```java
// Combines 3 prediction modes:
public Vec3 predictPosition(int ticksAhead) {
    // 1. KINEMATIC: friction-based position extrapolation
    //    - Ground drag: 0.91, Air drag: 0.98
    //    - Gravity: 0.08 per tick
    //    - Iterates ticksAhead steps

    // 2. BEHAVIORAL: pattern classification fallback
    //    - If history available + prediction within bounds → use behavioral

    // 3. JUMP ARC: specific vertical path prediction
    //    - If wasJumping && velocity.y > 0.05 → use JumpArcPredictor
}
```

### BehavioralPredictor (6 Patterns)
```java
public enum Pattern {
    CIRCLE_STRAFER,   // Yaw range >80°, strafe changes >5
    AGGRESSIVE_STRAFER, // Yaw range >40°, strafe changes >3
    JUMPER,            // Jump ratio >30%
    LINEAR_CHASER,     // Sprint ratio >70%, yaw range <20°
    PANIC_RUNNER,      // Sprint ratio <30%
    UNKNOWN            // Insufficient data
}
```

### Prediction Chain
```java
// In AimAssistModule.tick():
// 1. Update predictor with current living entity position
aimController.updatePredictions(living);

// 2. Get aim point with prediction offset
Vec3 aimPoint = aimController.getAimPoint(target, false);

// 3. Calculate rotation to predicted position
Rotation targetRot = aimController.calculateRotation(aimPoint);
```

### Confidence Weighting
```java
// Base confidence from Kalman filter (avg of X, Y, Z filters)
// Modifiers:
//   - hurtTime > 0:  ×0.7
//   - wasSprinting:  ×1.2 (cap at 1.0)
//   - ticksAhead:     ×(1.0 - ticksAhead × 0.1)
// Final: clamp[0.05, 0.95]
```

---

## 8. Targeting System

### TargetManager
```java
// Primary flow:
targetManager.tick() → scan entities → score → select primary

// Cooldown: targetSwitchCooldown = 20 ticks after target death
// Filter: alive, in range, valid type (players/hostile/passive/invisible)
// Re-validation: checks entity.isAlive() every tick

// Priority modes: "distance" | "angle" | "hybrid"
```

### TrackedTarget Scoring (Hybrid)
```java
// Normalized to [0, 1]:
double distScore = 1.0 - min(1, distance / detectionRange);
double angleScore = 1.0 - min(1, angle / fov);
double healthScore = 1.0 - (health / maxHealth);

// Weighted combination:
priorityScore = distScore * 0.4 + angleScore * 0.4 + healthScore * 0.2;
```

### EntityTrackerSystem
- Centralized entity scanning every 2-4 ticks
- Caches nearby players, mobs, projectiles, items
- All subsystems read from cache (instead of calling `level.getEntitiesOfClass()` every tick)
- FPS optimization: single-pass manual filter by entity type

---

## 9. Rendering & Visuals

### AimAssistRenderer (World Render)
```java
// Renders via WorldRenderEvents.AFTER_ENTITIES:
// 1. ESP Box: 3D bounding box outline around target
// 2. Health Bar: 3D overlay above target, color-coded
//    - Green (>60% HP) → Yellow (30-60%) → Red (<30%)
// 3. Prediction Ghost: Fading box at predicted position
//    - Alpha = confidence/2 (fades with distance)
//    - Color matches health bar
// 4. Tracers: Lines from camera to target
//    - Solid line to current position
//    - Dashed line to predicted position
```

### AimAssistOverlay (HUD)
- Minimal crosshair indicator when target locked
- Rendered via HudElementRegistry or HudRenderCallback

### AimAssistScreen (GUI)
- Tabbed config screen: COMBAT, MOVEMENT, VISUAL, MISC
- G key to open
- VISUAL tab exists but rendering backend was non-functional (now fixed with AimAssistRenderer)

---

## 10. Config Reference

### General
| Field | Type | Default | Range | Description |
|-------|------|---------|-------|-------------|
| `enabled` | boolean | false | — | Master toggle |
| `mode` | Mode | DEMON | DEMON/EZ | Mode preset |
| `range` | double | 3.0 | 1-6 | Attack reach |
| `detectionRange` | double | 64.0 | 1-64 | Entity scan range |
| `fov` | double | 120.0 | 1-360 | Target FOV |
| `movementMode` | boolean | true | — | WASD chase movement |

### Targeting
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `targetPlayers` | boolean | true | Target players |
| `targetHostile` | boolean | false | Target hostile mobs |
| `targetPassive` | boolean | false | Target passive mobs |
| `targetInvisible` | boolean | false | Target invisible entities |
| `requireLineOfSight` | boolean | false | LOS requirement |
| `checkBehindWalls` | boolean | false | Check through walls |
| `priorityMode` | String | "hybrid" | Distance/Angle/Hybrid |
| `sortButton` | ButtonType | NONE | Mouse button for sort |

### Aim
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `aimSpeed` | double | 1.0 | Rotation speed |
| `aimSpeedX` | double | 1.0 | Horizontal speed |
| `aimSpeedY` | double | 0.8 | Vertical speed |
| `smoothing` | double | 0.5 | Rotation smoothing |
| `predictAmount` | double | 1.0 | Prediction strength |
| `aimOnClick` | boolean | false | Aim only on click |
| `aimOnAttack` | boolean | false | Aim only on attack |
| `dynamicAimSpeed` | boolean | true | Dynamic speed scaling |

### Anti-Cheat (EZ Mode)
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `silentAim` | boolean | true | Packet-based aim |
| `randomization` | double | 0.5 | Noise injection |
| `minRotationChange` | int | 1 | Min rotation delta |
| `humanMouseSimulation` | boolean | true | Human-like mouse |
| `antiWallBang` | boolean | true | Wall bang prevention |
| `rotationSpoof` | boolean | true | Spoof rotation |

### Combat Toggles
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `critMode` | boolean | false | Auto-crit |
| `comboMode` | boolean | false | Combo pressure |
| `maceMode` | boolean | false | Mace assist |
| `bowMode` | boolean | false | Bow assist |
| `bridgeMode` | boolean | false | Bridge assist |
| `pvpMode` | boolean | false | PVP mode |
| `autoMode` | boolean | false | Auto mode |

### Survival
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `autoDodge` | boolean | true | Auto-dodge threats |
| `autoEat` | boolean | true | Auto-eat <6 hunger |
| `autoHeal` | boolean | true | Auto-gapple <8 HP |
| `autoClutch` | boolean | true | Fall clutch |
| `chaseMode` | boolean | false | Chase mode |
| `fleeMode` | boolean | false | Flee from threats |
| `farmMode` | boolean | false | Auto-farm |

### Visual
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `showHUD` | boolean | true | HUD overlay |
| `showTargetInfo` | boolean | true | Target HP/distance |
| `showPrediction` | boolean | false | Prediction ghost |
| `showTrajectory` | boolean | false | Trajectory line |
| `hudX` | int | 5 | HUD X position |
| `hudY` | int | 5 | HUD Y position |

### Trigger Bot
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `triggerBot` | boolean | false | Auto-trigger |
| `triggerDelay` | int | 0 | Trigger delay (ticks) |
| `triggerOnlyPlayers` | boolean | false | Players only |
| `triggerRange` | double | 6.0 | Trigger range |

### Prediction
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `predictMovement` | boolean | true | Movement prediction |
| `predictBulletDrop` | boolean | true | Bullet drop calc |
| `predictJump` | boolean | true | Jump prediction |
| `predictionTicks` | int | 2 | Lookahead ticks |
| `predictionConfidence` | double | 0.5 | Confidence threshold |

### Misc
| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `maceAssist` | boolean | true | Mace assist |
| `autoSmash` | boolean | true | Auto mace smash |
| `minSmashHeight` | double | 2.0 | Min smash fall |
| `windBurstTracking` | boolean | true | Wind burst tracking |
| `sprintInWater` | boolean | false | Sprint while swimming |
| `autoSurface` | boolean | true | Auto-surface for air |
| `breathMargin` | int | 3 | Air margin before surface |
| `durabilityThreshold` | double | 0.15 | Repair threshold |
| `repairMode` | RepairMode | AUTO | Repair behavior |
| `xpFarmEnabled` | boolean | false | XP farm toggle |

---

## 11. Commands & Keybinds

### Keybinds
| Key | Action |
|-----|--------|
| **R** | Toggle aim assist on/off |
| **G** | Open config GUI screen |

### Chat Commands (prefix `#` or `&`)
| Command | Description |
|---------|-------------|
| `toggle` / `on` / `off` | Enable/disable |
| `gui` | Open config screen |
| `chase <name>` | Chase player or named entity |
| `kill <name>` | Chase + auto-attack |
| `hunt <mobtype>` | Hunt specific mob type |
| `follow <name>` | Follow player |
| `cleartarget` | Clear target, stop chase |
| `targets` | Show detected entities |
| `scan` | Force entity scan |
| `status` | Show current state |
| `area define <name>` | Define area at position |
| `area list` | List defined areas |
| `area goto <name>` | Navigate to area |
| `portal build <x> <y> <z>` | Build portal |
| `xpfarm <on/off>` | Toggle XP farm |
| `repair <mode>` | auto/switch/mend/ask/continue |
| `durability <0.0-1.0>` | Set durability threshold |
| `clear` | Clear all tags |
| `help` | Show all commands |

### GUI Controls (G key → Tabbed)
| Tab | Controls |
|-----|----------|
| **COMBAT** | MOVEMENT toggle, Auto Dodge, Auto Eat/Heal/Clutch |
| **MOVEMENT** | Target name input, Chase/Kill/Follow buttons |
| **VISUAL** | Target info, ESP, tracers, prediction ghost toggles |
| **MISC** | Scan button, Clear Tgt, Clear All, Status button |

---

## 12. All Applied Fixes

### Fix #1: Strafe Inversion (CRITICAL)
- **File**: `MixinKeyboardInput.java`
- **Root cause**: `strafe = (right?1:0) - (left?1:0)` → positive strafe = right key, but travel formula maps positive strafe to... left move. Formula depends on yaw convention.
- **Fix**: Changed to `strafe = (left?1:0) - (right?1:0)` — matches `Input.getMoveVector()` vanilla convention.
- **Verified**: Matches vanilla `Input.getMoveVector()` exactly.

### Fix #2: Forward/Backward Loop via Dodge Override
- **Files**: `DodgeSystem.java`, `AimAssistModule.java`
- **Root cause**: Dodge called `ctrl.moveToward()` directly, creating 5-tick full override. When override expired and chase supplement resumed, dodge could re-trigger → forward ↔ dodge oscillation.
- **Fix**: Dodge now submits `MovementIntent(Priority.DODGE, ...)` via `MovementArbiter`. Arbiter picks highest priority. No direct `ctrl.moveToward()` from DodgeSystem.

### Fix #3: Target Switching After Kill
- **File**: `TargetManager.java`
- **Root cause**: No cooldown after target death. Dead entity filtered → next nearest immediately selected.
- **Fix**: Added `targetSwitchCooldown = 20` ticks. Check `entity.isRemoved()` and `!entity.isAlive()`. Previous target persisted during cooldown.

### Fix #4: Hybrid Priority Normalization
- **File**: `TrackedTarget.java`
- **Root cause**: `score = distance + angle` without normalization. Distance (0-64) and angle (0-180) on different scales. Distance dominated.
- **Fix**: Normalized both to [0,1]: `distScore = 1.0 - min(1, dist/detectionRange)`, `angleScore = 1.0 - min(1, angle/fov)`. Weights: 0.4/0.4/0.2 (dist/angle/health).

### Fix #5: Multi-Mode Prediction
- **File**: `MovementPredictor.java`
- **Root cause**: Simple linear extrapolation only. No friction, no gravity, no pattern classification.
- **Fix**: 3-mode prediction: kinematic (friction 0.91 ground / 0.98 air + gravity 0.08), behavioral (6 player patterns), jump arc. Confidence weighting with modifiers (hurtTime×0.7, sprinting×1.2, tick decay×0.1).

### Fix #6: Visual Category
- **File**: `AimAssistRenderer.java`
- **Root cause**: VISUAL tab in config GUI existed but had no rendering backend.
- **Fix**: Implemented via `WorldRenderEvents`: ESP box + health bar (green→yellow→red), prediction ghost (fading alpha = confidence/2), velocity tracer line, camera tracer line.

### Fix #7: MovementArbiter Supplement Mode
- **File**: `MovementArbiter.java`
- **Root cause**: DODGE/CRIT priority used `moveToward()` full override; CHASE and PARKOUR also used `moveToward()` instead of supplement.
- **Fix**: CRIT(70+) → `moveToward()`. PARKOUR/CHASE (20+) → `supplementForward()` only. Never clears supplement.

### Fix #8: Air Strafe Continuous
- **File**: `AirStrafeController.java`
- **Root cause**: Used player yaw instead of velocity yaw for direction. Had `active=false` reset making it one-shot.
- **Fix**: Uses velocity yaw for direction calculation. Removed one-shot reset. Continuous strafing while airborne.

### Fix #9: A/D Root Cause (CRITICAL)
- **File**: `KeyMovementController.java` — `moveToward()`
- **Root cause**: Strafe vector computed as `(cos(yaw), sin(yaw))` which at yaw=0 equals `(1, 0)` = **LEFT** vector. But code set `overrideRight` when dot product was positive (target has LEFT component → set RIGHT key = wrong).
- **Old mixin `(right-left)`**: cancelled this out accidentally (double inversion = working).
- **New mixin `(left-right)`**: exposed the inversion (left→right, right→left).
- **Fix**: Swapped assignment: positive dot → `overrideLeft`, negative dot → `overrideRight`.

### Fix #10: ChaseBehavior Integration
- **File**: `ChaseBehavior.java`
- **Changes**: Added `getCurrentTarget()` method. Removed `ctrl.moveToward()` from main chase path (uses `supplementForward()` instead). Yaw oscillation for strafe weave without A/D touch.

### Fix #11: IAimConfig Interface
- **File**: `IAimConfig.java` (API)
- **Changes**: Added `getDetectionRange()` and `setDetectionRange()` methods to interface (TrackedTarget used these but they weren't declared in the interface).

### Fix #12: DodgeSystem Methods
- **File**: `DodgeSystem.java`
- **Changes**: Added `hasActiveDodge()` and `getDodgeIntent()` methods for MovementIntent bus integration. Removed direct `ctrl.moveToward()` from `triggerDodge()`.

### Fix #13: BehavioralPredictor Access
- **File**: `BehavioralPredictor.java`, `MovementPredictor.java`
- **Changes**: Added `hasHistory(int entityId)` public method. Added `predictPosition(Vec3, Vec3, float, int, int)` overload that doesn't require fake LivingEntity. Removed anonymous LivingEntity creation (was causing `getMainArm()` override error).

### Fix #14: Override TTL
- **File**: `KeyMovementController.java`
- **Change**: OVERRIDE_TTL increased from 3 to 5 ticks for stability.

### Fix #15: Forward+Backward Conflict
- **File**: `KeyMovementController.java` — supplement mode
- **Change**: If both `forward` and `supplementForward` + original `backward`, backward is cleared to prevent forward+backward conflict.

---

## 13. A/D Root Cause Analysis

### Complete Trace

```
Scenario: ChaseBehavior targets entity at EAST (+x), player facing SOUTH (yaw=0)

1. ChaseBehavior.handlePathToTarget():
   dir = (1, 0, 0)  // target direction (east = right side)

2. ctrl.moveToward(dir, ...):
   forwardX = -sin(0) = 0
   forwardZ = cos(0) = 1
   strafeX = forwardZ = 1           // ← strafe vector = (1, 0) = EAST
   strafeZ = -forwardX = 0           //   = LEFT of forward at yaw=0

   fwd = 1·0 + 0·1 = 0
   str = 1·1 + 0·0 = 1              // positive → dot with LEFT vector

   // BUG (now fixed): was overrideRight = str > 0
   overrideLeft = str > 0 → true     // CORRECT: target is left, go left
   overrideRight = false

3. getOverriddenInput(): returns Input(left=true, right=false, ...)

4. MixinKeyboardInput (strafe = left - right):
   strafe = 1 - 0 = 1
   moveVector = Vec2(1, 0)

5. travel(Vec3(1, 0, 0)) at yaw=0:
   dx = (1 · cos(0) - 0 · sin(0)) · speed = speed → EAST
   // Player moves RIGHT → target is RIGHT → CORRECT ✓
```

### Why Old Mixin "Worked"
```
Old mixin: strafe = (right - left)
Old moveToward: overrideRight = str > 0, overrideLeft = str < 0

Target east → overrideRight=true → Input(right=true)
Old mixin: strafe = 1 - 0 = 1 → dx = speed → east → RIGHT ✓
(Both were inverted, cancelling out)
```

### Why New Mixin Broke It
```
New mixin: strafe = (left - right)
Old moveToward: overrideRight = str > 0, overrideLeft = str < 0

Target east → overrideRight=true → Input(right=true)
New mixin: strafe = 0 - 1 = -1 → dx = -speed → west → LEFT ❌
(The old moveToward was now the only inversion, no longer cancelled)
```

### Key Insight
The `moveToward()` strafe vector `(cos(yaw), sin(yaw))` is the **left** perpendicular at yaw=0. This is because Minecraft's yaw system has specific sin/cos sign conventions. The code should have been comparing against the opposite sign for left/right assignment.

---

## 14. Bug Inventory

### Fixed Bugs

| # | Bug | Criticality | File | Root Cause |
|---|-----|-------------|------|------------|
| 1 | Strafe inverted | CRITICAL | `MixinKeyboardInput.java` | `(right-left)` instead of `(left-right)` in moveVector |
| 2 | A/D inverted in moveToward | CRITICAL | `KeyMovementController.java` | Strafe vector is LEFT at yaw=0, but overrideRight was set for positive dot |
| 3 | Forward/backward oscillation | HIGH | `DodgeSystem.java` | Dodge used full `moveToward()` override fighting chase supplement |
| 4 | Target switching after kill | HIGH | `TargetManager.java` | No cooldown, next nearest selected immediately |
| 5 | Hybrid priority broken | HIGH | `TrackedTarget.java` | Distance (0-64) and angle (0-180) not normalized |
| 6 | Prediction not working | HIGH | `MovementPredictor.java` | Simple extrapolation only, no friction/gravity/patterns |
| 7 | Visual category non-functional | MEDIUM | `AimAssistRenderer.java` (was missing) | No rendering backend existed |
| 8 | Air strafe one-shot | MEDIUM | `AirStrafeController.java` | Bad yaw source + active=false reset |
| 9 | Chase used moveToward | MEDIUM | `ChaseBehavior.java` | Full override for main chase path |
| 10 | MovementArbiter cleared supplement | MEDIUM | `MovementArbiter.java` | Empty-intents path called clearSupplement() |
| 11 | Mod disabled still moved | MEDIUM | `AimAssistMod.java` | Supplement not cleared on disabled path |
| 12 | getDetectionRange not in API | MEDIUM | `IAimConfig.java` | Interface missing method |
| 13 | Fake LivingEntity compile error | LOW | `MovementPredictor.java` | Anonymous class didn't override getMainArm() |
| 14 | historyMap private access | LOW | `MovementPredictor.java` | Direct access to private field |
| 15 | getCurrentTarget not in Chase | LOW | `ChaseBehavior.java` | Method didn't exist |
| 16 | hasActiveDodge/getDodgeIntent missing | LOW | `DodgeSystem.java` | Methods didn't exist for bus integration |
| 17 | Override TTL too short | LOW | `KeyMovementController.java` | 3 ticks → 5 ticks |
| 18 | Forward+backward conflict | LOW | `KeyMovementController.java` | supplement forward + player backward simultaneously |
| 19 | @Shadow fail (no refmap) | CRITICAL | `ClientInputAccessor.java` (added) | @Shadow requires refmap; replaced with @Accessor |

### Known Remaining Issues
- Prediction can cause aim jitter on erratic targets (mitigated via `predictAmount` config, default 1.0)
- VISUAL tab in config GUI exists but wiring to config toggles is incomplete
- DodgeOnly mode (movement off, dodge on) still uses direct velocity manipulation — not through arbiter
- `autoFarmMode()` has stubs with TODO markers (Furnace interaction incomplete)
- `AggressiveParkour.java` exists but may be a stub

---

## 15. PVP Research Reference

### Movement Patterns

| Technique | Execution | Purpose |
|-----------|-----------|---------|
| **Circle Strafing** | Hold A/D while circling + W | Harder to hit |
| **Counter Strafing** | Switch A↔D before trade | Messes up opponent aim |
| **Blitz Strafing** | Strafe one side → flick crosshair → flick back | Visual speed illusion |
| **Zigzag Strafing** | Rapid alternating A/D unpredictable | Prevents movement prediction |
| **W-Tapping** | Release W 1-2 ticks after hit | Sprint reset = full knockback |
| **S-Tapping** | Brief S press after hit | Creates distance, resets sprint |
| **Jump Resetting** | Jump 1 tick before damage | Converts horizontal→vertical KB |
| **Block Hitting** | Shield right-click before impact | 50% damage reduction (1.9+) |

### Attack Timing

| Weapon | Attack Speed | Cooldown (ticks) | Full Damage At |
|--------|-------------|-------------------|----------------|
| Sword | 1.6 | 12.5 | 84.8% charge (≈10.6 ticks) |
| Axe | 1.0 | 20 | 84.8% charge (≈17 ticks) |
| Pickaxe | 1.2 | 16.6 | 84.8% charge (≈14 ticks) |
| Trident | 1.1 | 18.2 | 84.8% charge (≈15.5 ticks) |

### Critical Hit Requirements (26.1)
```
1. fallDistance > 0.0          ─ Must have fallen slightly
2. !onGround()                 ─ Must be airborne
3. !onClimbable()              ─ Not on ladder/vine
4. !isInWater() && !isInLava() ─ Not in liquid
5. !isMobilityRestricted()     ─ Not in cobweb/powder snow/berry bush
6. !isPassenger()              ─ Not riding
7. !isSprinting()              ─ NOT sprinting (26.1 rule)
8. attackStrengthScale > 0.9   ─ Full charge
9. Target instanceof LivingEntity
```

### Damage Formula
```java
damage = 0.2 + attackStrength² × 0.8  // linear to quadratic at 84.8%
critMultiplier = 1.5                    // +50% on crit
```

### Combo Mechanics
1. First hit: sprint hit from optimal range (2.8-3.2 blocks)
2. Sprint reset: W-tap/S-tap after each hit
3. Distance oscillation: in (hit) → out (reset) → in (hit)
4. Strafe between W-taps: mix A/D
5. Invulnerability ticks: 10 (0.5s) between hits on same target
6. Hit selecting: delay hit 100-400ms after getting hit (use i-frames)

### Prediction Tips
- Read legs, not head (legs show movement direction first)
- Aim where target WILL be, not where they are
- Counter-strafing: pre-aim slightly left of center
- Decelerate crosshair as it approaches target (prevent overshoot)
- Sword aim: slightly above center of mass (accounts for crit jump height)

---

## 16. Movement Formulas (26.1)

### Travel Formula
```java
// Player.travel(Vec3 movementInput)
// movementInput = getTravelVector(moveVector)
// getTravelVector(Vec2) → new Vec3(vec.x, 0, vec.y)

float strafe = (float)movementInput.x;    // xxa
float forward = (float)movementInput.z;   // zza

float yawRad = player.getYRot() * Math.PI / 180;
float sin = Math.sin(yawRad);
float cos = Math.cos(yawRad);

double dx = (strafe * cos - forward * sin) * speed;
double dz = (strafe * sin + forward * cos) * speed;

// At yaw=0 (south): dx = strafe * speed, dz = forward * speed
// At yaw=90 (west): dx = -forward * speed, dz = strafe * speed
```

### Speeds
| Mode | Acceleration | Max Speed |
|------|-------------|-----------|
| Walking | 0.1 | ~4.317 m/s |
| Sprinting | 0.13 | ~5.612 m/s |
| Sneaking | 0.03 | ~1.3 m/s |
| Air | ground × 0.2 | Varies |

### Jump
| Property | Value |
|----------|-------|
| Jump velocity | 0.42 m/s upward |
| Gravity | 0.08 blocks/tick² |
| Max height (no boost) | ~1.25 blocks |
| Diagonal normalization | ×0.7071 (when both forward+strafe active) |

### Friction
| Surface | Drag |
|---------|------|
| Ground | 0.91 (velocity × 0.91 per tick) |
| Air | 0.98 (velocity × 0.98 per tick) |
| Ice | 0.98 (low friction) |

### ServerboundPlayerInputPacket
```java
// Sent EVERY tick client → server
// xxa: +1 = LEFT strafe, -1 = RIGHT strafe
// zza: +1 = FORWARD, -1 = BACKWARD
public ServerboundPlayerInputPacket(float xxa, float zza, boolean isJumping, boolean isShiftKeyDown)
```

### Input Record (1.21.1)
```java
public record Input(boolean forward, boolean backward, boolean left, boolean right,
                    boolean jump, boolean shift, boolean sprint) {
    static Vec2 getMoveVector(Input input) {
        float forward = (input.forward()?1:0) - (input.backward()?1:0);
        float strafe = (input.left()?1:0) - (input.right()?1:0);
        if (forward == 0 && strafe == 0) return Vec2.ZERO;
        if (forward != 0 && strafe != 0) { forward *= 0.7071f; strafe *= 0.7071f; }
        return new Vec2(strafe, forward);
    }
}
```

---

## 17. Import Reference (Mojang Mappings 1.21.1)

### Projectile Classes (MOVED in 1.21.1)
| Class | Import Path |
|-------|-------------|
| AbstractThrownPotion | `projectile.throwableitemprojectile.AbstractThrownPotion` |
| ThrownSplashPotion | `projectile.throwableitemprojectile.ThrownSplashPotion` |
| ThrownLingeringPotion | `projectile.throwableitemprojectile.ThrownLingeringPotion` |
| WitherSkull | `projectile.hurtingprojectile.WitherSkull` |
| Fireball | `projectile.hurtingprojectile.Fireball` |
| LargeFireball | `projectile.hurtingprojectile.LargeFireball` |
| SmallFireball | `projectile.hurtingprojectile.SmallFireball` |
| DragonFireball | `projectile.hurtingprojectile.DragonFireball` |
| ShulkerBullet | `projectile.ShulkerBullet` |
| ThrownTrident | `projectile.arrow.ThrownTrident` |

### Entity Classes
| Entity | Import Path |
|--------|-------------|
| Slime | `net.minecraft.world.entity.monster.Slime` |
| MagmaCube | `net.minecraft.world.entity.monster.MagmaCube` (extends Slime) |
| Player | `net.minecraft.world.entity.player.Player` |
| Monster | `net.minecraft.world.entity.monster.Monster` |
| EndCrystal | `net.minecraft.world.entity.boss.enderdragon.EndCrystal` |
| FallingBlockEntity | `net.minecraft.world.entity.item.FallingBlockEntity` |
| PrimedTnt | `net.minecraft.world.entity.item.PrimedTnt` |

### Input System
| Class | Role |
|-------|------|
| `Input` (record) | `(boolean forward, backward, left, right, jump, shift, sprint)` |
| `ClientInput` (abstract) | Fields: `keyPresses` (Input), `moveVector` (Vec2). Method: `tick()` |
| `KeyboardInput extends ClientInput` | Reads `Options` keys → Input record → moveVector |
| `LocalPlayer` | Has `ClientInput input` field. Method: `onInput(ClientInput)` |

### Effect Names
| Effect | Identifier |
|--------|------------|
| SLOWNESS | `MobEffects.SLOWNESS` |
| DAMAGE_BOOST | `MobEffects.DAMAGE_BOOST` |
| JUMP | `MobEffects.JUMP` |
| WITHER | `MobEffects.WITHER` |
| POISON | `MobEffects.POISON` |

---

## 18. Next-Gen Roadmap

### Phase 1: Architecture (DONE)
- ✅ MovementIntent priority system
- ✅ MovementArbiter resolver
- ✅ Priority arbitration (DODGE=100, CRIT=70, CHASE=50, etc.)
- ✅ ActionBuffer in MovementBrain
- ✅ CombatFlow state machine in MovementBrain

### Phase 2: Movement Intelligence (DONE)
- ✅ Spatial awareness (TerrainAnalyzer) — affordance classification
- ✅ Momentum preservation (MovementBrain)
- ✅ Terrain-assisted dodge (DodgeSystem via MovementIntent bus)
- ✅ Air control (AirStrafeController — continuous, yaw-based)

### Phase 3: Combat Intelligence (DONE)
- ✅ CombatFlow states (6 states with aggression/sprint/dodge/crit params)
- ✅ Behavioral prediction (6 player patterns: circle strafer, jumper, etc.)
- ⬜ Threat heatmap (dodgeScore[x][z]) — not yet implemented

### Phase 4: Advanced Movement
- ⬜ Traversal chains (sprint jump → ladder catch → neo turn → gap transfer)
- ⬜ Combo parkour (sustained momentum across terrain)
- ⬜ Predictive escape routing

### Future Upgrades from GPT Analysis

1. **CMS (Combat Motor System)** — fully implemented as MovementIntent + MovementArbiter + MovementBrain
2. **Spatial Awareness Engine** — TerrainAnalyzer (Block affordance: WALKABLE/JUMPABLE/CLIMBABLE/VOID/HAZARD)
3. **Momentum Preservation System** — MovementBrain tracks sprint chain, jump chain, air velocity, landing timing
4. **Behavioral Movement Prediction** — BehavioralPredictor classifies 6 player movement patterns
5. **Terrain-Assisted Dodging** — DodgeSystem evaluates wall/gap/elevation/block-jump/ladder before choosing direction
6. **Combat Flow State Machine** — MovementBrain.CombatFlow (6 states)
7. **Humanization Layer** — MovementBrain: micro noise, imperfect strafe intervals, variable jump delay
8. **Air Control Physics** — AirStrafeController: airTicks, velocity yaw, yaw delta
9. **Threat Priority Heatmap** — NOT IMPLEMENTED: dangerScore[x][z] from projectiles/explosions/void/lava
10. **Multi-Tick Action Planner** — MovementBrain.ActionBuffer: schedule sprint cancel → jump → attack → resume sprint
11. **Parkour Chain System** — NOT IMPLEMENTED: TraversalAction with cost/risk/momentumGain/combatValue
12. **Recovery Intelligence** — NOT IMPLEMENTED: missed jump, edge slip, knockback recovery
13. **Tactical Movement Graph** — NOT IMPLEMENTED: every nearby block as movement node with A* path

### Key Philosophy
> "How do I build a layered decision system?"
> NOT: "How do I make dodge work?"

Every subsystem submits intents. One resolver builds final movement.
`intent → arbitration → execution` — one pipeline.

---

*Generated 2026-05-12. Last build: SUCCESSFUL. Last deploy: Shinigami-by-Saizo-1.17.0.jar (2.0M)*
