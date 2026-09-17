# ChatGPT Prompt — Shinigami by Saizo Mod Fixes & Upgrades

## CONTEXT
You are helping fix and upgrade a Minecraft 26.1 (1.21.1) Fabric mod called "Shinigami by Saizo" (mod ID: shinigami). It is a standalone client-side PVP assist mod that includes: aim assist, auto-crit, auto-dodge (19 threat types), chase/parkour (WASD-only), clutch system, and combo mode.

## ARCHITECTURE
- **Input override**: 3-layer mixin injection: `MixinKeyboardInput` (KeyboardInput.tick() TAIL), `MixinLocalPlayerInput` (LocalPlayer.onInput() HEAD+TAIL)
- **Movement**: `KeyMovementController` — two modes: `override` (full replacement, 3-tick TTL) and `supplement` (OR-blend with player input)
- **Config**: `AimAssistConfig.java` — all config fields, getters/setters. Tabbed GUI screen (`AimAssistScreen.java`)
- **Targeting**: Entity scanning + scoring by distance/angle/hybrid. Current target stored, auto-switches on kill/death
- **Rendering**: Basic overlay (`AimAssistOverlay.java`) — crosshair indicator. No ESP/tracers/prediction ghost yet
- **Files**: All source in `baritone-26.1/fabric/src/main/java/baritone/aimassist/` (Java, Mojang official mappings)

## BUGS TO FIX

### Bug 1: Strafe Inversion (CRITICAL)
**File**: `MixinKeyboardInput.java`
**Problem**: The mixin computes `moveVector` with strafe = `(right ? 1 : 0) - (left ? 1 : 0)`. When `right=true`, strafe=+1. But `ServerboundPlayerInputPacket.xxa` convention is: **positive = LEFT strafe, negative = RIGHT**. So strafe=+1 = LEFT when the intent was RIGHT. This causes all strafe overrides to move in the WRONG direction.
**Fix**: Invert the strafe computation to `(left ? 1 : 0) - (right ? 1 : 0)`.

### Bug 2: Forward/Backward Oscillation Loop
**Files**: `KeyMovementController.java`, `MovementArbiter.java`, `AimAssistMod.java`
**Problem**: Chase sets `supplementForward(true)` every tick. When Dodge triggers, it calls `moveToward()` with a full override (3-tick TTL). After TTL expires, supplement forward resumes. If dodge re-triggers immediately, the player oscillates: forward (supplement) → dodgeDir → forward → dodgeDir.
**Fix**: 
- Change dodge to MODULATE movement instead of full override (add perpendicular escape vector as offset to supplement forward)
- Increase OVERRIDE_TTL from 3 to 5
- Ensure dodge never clears supplement

### Bug 3: Target Switching After Kill
**File**: Entity targeting code
**Problem**: After killing the current target, the entity scan immediately re-selects the nearest entity without cooldown.
**Fix**: 
- Add `targetSwitchCooldown = 20` ticks after target death
- Check `entity.isRemoved()` and `!entity.isAlive()`
- Add `persist_time` of ~40 ticks before allowing re-target

### Bug 4: Hybrid Distance+Angle Priority Broken
**File**: Target selection scoring
**Problem**: Hybrid mode likely computes `score = distance + angle` without normalization. Distance (0-64) and angle (0-180) are on different scales.
**Fix**: Normalize both to [0,1] before combining:
```java
float normalizedDistance = distance / maxDistance;
float normalizedAngle = angle / maxAngle;
float score = normalizedDistance * distanceWeight + normalizedAngle * angleWeight;
```

### Bug 5: Prediction Not Working
**File**: Aim module
**Problem**: Prediction likely uses simple position + velocity without accounting for time-to-impact, target behavior, or movement patterns.
**Fix**: Implement multi-mode prediction:
- Kinematic (linear velocity for steady targets)
- Behavioral (pattern classification for players: strafe, jump, zigzag)
- Confidence-based weighting

### Bug 6: Visual Category Not Working
**File**: `AimAssistScreen.java`
**Problem**: "Visual" tab in config screen references non-existent rendering code.
**Fix**: Implement using Fabric's `HudElementRegistry`:
- ESP box around target (WorldRenderEvents)
- Target info HUD (name, health, distance)
- Tracers line
- Prediction ghost overlay
- Config UI toggles in the VISUAL tab

### Bug 7: Movement Architecture (Design)
**Problem**: Multiple systems fight for movement simultaneously without arbitration.
**Fix**: Build `MovementIntent` priority system:
- DODGE(100) > CLUTCH(95) > CRIT(70) > CHASE(50) > COMBAT(30) > AUTO_WALK(10)
- 3-layer input model: Supplement (additive) → Steering (yaw) → Emergency (hard override)

### Bug 8: Player Takes Too Much Damage
**File**: `DodgeSystem.java`, `ClutchSystem.java`
**Problem**: Dodge is reactive, not predictive. Doesn't simulate threat trajectory.
**Fix**:
- Threat Timeline Simulation: Simulate projectile position N ticks ahead, dodge BEFORE collision
- Perpendicular evasion for projectiles (not away-from)
- Pre-emptive dodging when threat enters range
- Jump reset on damage (convert horizontal KB to vertical)

## UPGRADES TO IMPLEMENT

### Priority-Based Movement Arbitration
- `MovementIntent` class with priority enum
- `MovementResolver` that selects highest-priority intent each tick
- Three-layer input: Supplement → Steering → Emergency

### Entity Tracking Optimization (FPS)
- Centralized `EntityTrackerSystem` — single scan every 2-4 ticks
- All subsystems read from cache
- Single-pass filter by entity type

### Pro PVP Movement Patterns
- W-Tap (sprint reset): Release W for 1-2 ticks after each hit
- S-Tap: Brief backward after hit to create space
- Jump Reset: Jump 1 tick before damage
- Strafe Oscillation: Flip strafe direction every 8-15 ticks with random interval
- Combo Pressure: Increase aggression after 2+ consecutive hits
- Crit 2-Tick Sequence: Cancel sprint → jump (never same tick)

### Air Strafe Controller
```java
// While airborne: compute yawDiff, apply A/D
float yawDiff = wrapDegrees(targetYaw - currentVelocityYaw);
if (yawDiff > 10) strafe = 1; // turn right
else if (yawDiff < -10) strafe = -1; // turn left
```

### Action Buffer Queue
Buffer actions N ticks ahead for timing-critical sequences:
- Crit: schedule sprint cancel → jump → attack → sprint resume
- Shield: schedule raise before predicted damage

### Combat Rhythm Engine
Tempo states: PRESSURE → RESET → BURST → BAIT → RE-ENGAGE → FINISH
- Transition based on health, distance, combo counter
- Each tempo has different movement/attack profile

### Tactical Movement Graph
- Scan 8-block radius around player
- Classify blocks as SOLID/PASSABLE/HAZARD
- A* search for highest-momentum combat path
- Prefer paths that maintain sprint, avoid hazards

### Jump Intent Classification
7 jump types with different behaviors:
- MICRO_HOP / COMBAT_HOP / GAP_JUMP / CLIMB_JUMP / DODGE_JUMP / TOWER_JUMP / REVERSE_JUMP
- Controller handles charge time, direction, height

### Multiple Prediction Systems
1. **Kinematic** — projectiles, steady targets (linear velocity)
2. **Behavioral** — players (pattern classification: strafe/jump/zigzag)
3. **Confidence** — 1.0 minus decay per tick, reduced by hurtTime
4. **Visual feedback** — prediction ghost with brightness = confidence

## TECHNICAL CONSTRAINTS
- Java 25, Gradle 8.14.4, Fabric Loader 0.18.6+, Fabric API 0.145.3+
- Mojang official mappings (NOT Yarn)
- MC version: 26.1 (Minecraft 1.21.1 equivalent)
- NO refmap generated — Avoid `@Shadow` for inherited fields. Use `@Accessor` instead
- Client-side only mod
- `ClientInputAccessor` already exists for setting moveVector
- Build: `./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon`

## QUESTIONS
1. What is the best way to implement the MovementIntent priority system without breaking existing subsystems?
2. For the Threat Timeline Simulation, what's the most efficient way to simulate projectile paths on the client without accessing server-side raytracing?
3. How should the Combat Rhythm Engine integrate with the existing Chase/Dodge/Crit subsystems — as a wrapper layer or a separate module?
4. What's the recommended approach for the Tactical Movement Graph A* search — compute every tick or cache with dirty flags?
5. For the Behavioral Predictor, what's a good feature set to classify player movement patterns (velocity deltas, jump frequency, strafe intervals)?

## OUTPUT FORMAT
Please provide:
1. **Priority-ordered implementation plan** — which fixes first, which upgrades next
2. **Code snippets** for each fix/upgrade with exact file paths
3. **Integration notes** — how each change affects other subsystems
4. **Testing strategy** — how to verify each fix works
5. **Estimated complexity** (hours/minutes) for each change
