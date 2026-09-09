# Core Architecture Decisions

## Mod Identity
- **Name**: Shinigami by Saizo
- **Type**: Fabric standalone mod for Minecraft 26.1 (1.21.1)
- **Mod ID**: shinigami (no conflict with original Baritone)
- **Loader**: Fabric Loader 0.19.2+
- **Mappings**: Mojang official mappings
- **JDK**: Java 25 Temurin ARM64

## Build System
- **Build tool**: Gradle 8.14.4 via wrapper
- **Key flags**: `-x test -x :fabric:proguard -x :fabric:createDist --no-daemon`
- **JAVA_HOME**: `/usr/lib/jvm/java-25-openjdk-arm64`
- **Output**: `/storage/emulated/0/1log/Shinigami-by-Saizo-1.17.0.jar`
- **Runtime dep**: Fabric API 0.148.0+26.1.2

## Core Philosophy
- Vanilla exact hit range (3.0 blocks) — win through algorithms, not cheating
- Take ZERO damage — every threat predicted and dodged
- Kill ALL enemies — dominate every enemy simultaneously
- Frame-perfect micro-strafes — move when opponent commits
- All sources of information — physics math + opponent entity state scanning + pattern history
- Buttery smooth FPS — efficient algorithms, optimized scanning intervals
- 100% DEMON mode — no subtlety, always full power

## Combat System Layers
1. **Prediction** — Physics + opponent state reading
2. **Movement** — Inhuman strafe, impossible-to-hit micro-dodges
3. **Timing** — Frame-perfect crit, W-tap, sprint, shield, attack
4. **Situations** — Every PvP scenario has pre-planned counter
5. **Reliability** — Zero crashes, zero bugs

## Input Architecture (3-Layer)
- Layer 1 — Supplement (additive): auto-walk, sprint assist, bridge assist
- Layer 2 — Steering (yaw control): chase, circle strafe, combo lock
- Layer 3 — Emergency Override: dodge, clutch, void save (< 5 ticks)

## Mixin Strategy (3-Layer Injection)
- Primary: MixinKeyboardInput → KeyboardInput.tick() TAIL
- Secondary: MixinLocalPlayerInput → LocalPlayer.onInput() HEAD+TAIL
- Timing: HEAD sets supplement BEFORE entity tick, TAIL does full module tick with fresh positions

## Movement
- WASD-only (no Baritone pathfinding)
- KeyMovementController for input override
- Supplement mode (OR with player input) vs Full Override mode

## Prediction (3-Mode Hybrid)
1. Closed-form physics: x(t) = x0 + vx0*(1-(1-d)^t)/d per axis
2. Iterative simulation: 5-40 ticks ahead
3. Pattern classification: LINEAR_CHASER, CIRCLE_STRAFER, etc.

## Kalman Filter
- 9D state vector: [x, vx, ax, y, vy, ay, z, vz, az]
- Implementation: org.apache.commons.math3.filter.KalmanFilter
- Predict → correct cycle every tick
- Process noise Q tuned per entity type

## Dodge (3-Layer Predictive)
1. Predict BEFORE action — opponent reaction time pattern
2. React on action start — within 1 tick of swing/right-click
3. React on threat — projectile trajectory, mace fall, explosion

## Tick Timeline (Minecraft.tick())
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

---

## Mixin Strategy (3-Layer Injection — Exact Files & Points)
- **Primary override**: `MixinKeyboardInput.java` (`launch/mixins/MixinKeyboardInput.java`) — `KeyboardInput.tick()` `TAIL`. Replaces `keyPresses` + `moveVector`. Computes `strafe = (left?1:0) - (right?1:0)`; `forward = (fwd?1:0) - (bwd?1:0)`. Uses `ClientInputAccessor` (`@Accessor`, NOT `@Shadow`) to call `setMoveVector(new Vec2(strafe, forward))`.
- **Secondary override**: `MixinLocalPlayerInput.java` (`launch/mixins/MixinLocalPlayerInput.java`) — `LocalPlayer.onInput(ClientInput)` `HEAD` + `TAIL`. Replaces `clientInput.keyPresses = ctrl.getOverriddenInput(...)` at both points to catch pipeline changes after `modifyInput()`.
- **Module tick coordinator**: `MixinAimAssistClient.java` (`launch/mixins/MixinAimAssistClient.java`) — `Minecraft.tick()` `HEAD` (pre-tick: set supplement, call `tickMovement()`) + `TAIL` (post-tick: `keybinds.handleKeybinds()`, `module.tick()` with fresh positions).
- **Chat intercept**: `MixinAimAssistChat.java` (`launch/mixins/MixinAimAssistChat.java`) — `#` / `&` prefixes for commands(`toggle`, `gui`, `chase`, `kill`, etc.).

---

## Input Architecture (3-Layer — Exact Controller & Arbiter)
- **Supplement mode** (`KeyMovementController.getOverriddenInput()`): `original.forward() || supplementForward`; `backward` cleared if `fwd && bwd` conflict; `original.left()` / `original.right()` preserved; `jump` = `original.jump() || supplementJump`. This is `OR`-blend — preserves player A/D.
- **Steering layer** (`AirStrafeController.java`, `ChaseBehavior.java`): Submits `MovementIntent(Priority.PARKOUR/CHASE, ...)` via `MovementArbiter.submit()`. Does NOT call `moveToward()`; uses `supplementForward()` for chase path.
- **Emergency override** (`DodgeSystem.java`, `ClutchSystem.java`, `CritAssist.java`): Submits `MovementIntent(Priority.DODGE, ...)` via arbiter. When `priority ≥ 70`: `ctrl.moveToward()` applied (full WASD replacement). TTL = `OVERRIDE_TTL = 5` ticks (`KeyMovementController.java`). When `priority < 70`: `ctrl.supplementForward()` only.
- **CMS Resolver** (`movement/MovementArbiter.java`): Collects all submitted intents → picks highest `Priority.value` → applies via `ctrl.moveToward()` if `priority.value ≥ 70`, else via `supplementForward()`. `clearSupplement()` runs at START of `tickMovement()`; never called in empty-intent path (prevents supplement clearing bug).

---

## Movement (WASD-Only, No Baritone Pathfinding)
- `ChaseBehavior.java`: `WASD-only` movement — no Baritone `AStarPathFinder`. Target methods: `setTarget(name)`, `setMobHunt(mobType)`, `setTargetUUID(uuid)`. Modes: `Chase` (follow), `Kill` (follow + attack). Detection: `detectionRange` blocks (default 64), matches UUID → custom name → player name.
- `MovementIntent.java`: Fields — `priority`, `Vec3 direction` (normalized world direction), `boolean sprint`, `JumpType jumpType` (`NONE | MICRO_HOP | COMBAT_HOP | GAP_JUMP | CLIMB_JUMP | DODGE_JUMP | TOWER_JUMP | REVERSE_JUMP`), `boolean sneak`, `int durationTicks`, `String reason`.
- `MovementBrain.java`: `CombatFlow` states (`APPROACH`, `PRESSURE`, `EVADE`, `RESET`, `BURST`, `FINISH`, `RETREAT`) with aggression/sprint/dodge/crit params. Transitions based on: distance to target, own HP ratio (`getHealth()`), `comboTicks`.
- `TerrainAnalyzer.java`: `Affordance` enum (`SPRINT_SAFE`, `JUMPABLE`, `CLIMBABLE`, `BRIDGEABLE`, `HEAD_HIT_RISK`, `VOID_RISK`, `OBSTACLE`). Methods: `classifyBlock()`, `isGapAhead()`, `isOneBlockObstacleAhead()`, `getJumpDistance()`, `hasHeadroom()`.
- `AirStrafeController.java`: `yawDiff = wrapDegrees(targetYaw - currentVelocityYaw)`. `strafe = sign(yawDiff)`. `forward = Math.abs(yawDiff) < 90 ? 1 : 0`. Submits `MovementIntent(Priority.PARKOUR, ...)` — supplement only. No one-shot reset (`active=false` removed).

---

## Prediction (3-Mode Hybrid — Exact Constants)
- **Kinematic** (`MovementPredictor.java`): Friction `ground=0.91`, `air=0.98`. Gravity `0.08` per tick (blocks/tick²). `predictPosition(int ticksAhead)` iterates `ticksAhead` steps (`O(1)` per tick). Uses `JumpArcPredictor` when `wasJumping && velocity.y > 0.05`.
- **Behavioral** (`BehavioralPredictor.java`): 6 patterns — `CIRCLE_STRAFER` (yaw range > 80°, strafe changes > 5), `AGGRESSIVE_STRAFER` (> 40°, > 3), `JUMPER` (jump ratio > 30%), `LINEAR_CHASER` (sprint ratio > 70%, yaw range < 20°), `PANIC_RUNNER` (sprint ratio < 30%), `UNKNOWN`. Confidence: `base * 0.7 (hurtTime > 0) * 1.2 (wasSprinting, cap 1.0) * (1.0 - ticksAhead * 0.1)`; clamp `[0.05, 0.95]`.
- **Jump Arc** (`JumpArcPredictor.java`): Predicts vertical path during airborne phase (`velocity.y > 0.05`). Used when `wasJumping` is true.

---

## Targeting & Tracking (Exact Scoring)
- `EntityTrackerSystem.java`: Centralized scan every `SCAN_INTERVAL = 2-4` ticks. Caches nearby players, mobs, projectiles, items. All subsystems read from `nearbyEntities` (instead of `level.getEntitiesOfClass()` every tick). Single-pass filter.
- `TargetManager.java`: `targetSwitchCooldown = 20` ticks after target death (`entity.isRemoved()` / `!entity.isAlive()`). Filter: alive, in `detectionRange`, valid type (players/hostile/passive/invisible). Re-validation every tick.
- `TrackedTarget.java`: Hybrid scoring (normalized to `[0,1]`): `distScore = 1.0 - min(1, distance / detectionRange)`; `angleScore = 1.0 - min(1, angle / fov)`; `healthScore = 1.0 - (health / maxHealth)`. `priorityScore = distScore * 0.4 + angleScore * 0.4 + healthScore * 0.2`. Priority modes: `"distance"` | `"angle"` | `"hybrid"`.

---

## Combat Systems — Exact Details
- **CritAssist.java**: State machine — `STATE 0` (idle, `attackStrength >= 0.9`, `onGround`, `!inWater`, `!climbable`, `!passenger`, `!mobilityRestricted`, `!isSprinting()`); `STATE 1` (sprint cancel — 1 tick, NO jump); `STATE 2` (jump trigger — `jumpFromGround()` + `supplementJump()` via mixin); `STATE 3` (airborne — 5 ticks, accumulate fall distance); `STATE 4` (crit ready — `canCriticalAttack()` true, attack applies `critMultiplier = 1.5`, landing resets to `STATE 0`). `FULL_DAMAGE_THRESHOLD = 0.848f` (84.8% charge = full damage; 90% charge = crit eligibility).
- **ComboTracker.java**: `comboCounter`, `lastHitTime`, `consecutiveHits`, `W-tap` state (`wTapTimer`, `sTapTimer`). Attack threshold `50%` in combo mode vs `90%` normal. Distance management: `> 1.2 * optimalRange` → aggressive close; `< 0.8 * optimalRange` → backward; else `swayStrafing()`.
- **DodgeSystem.java**: 19 threat types (`SLOWED` → `MELEE`). `getDodgeIntent()` → `MovementIntent(Priority.DODGE, ...)`. `hasActiveDodge()` → `dodgeTicks > 0`. `dodgeOnlyMode`: applies velocity directly but submits intent. `triggerDodge()` — NO direct `ctrl.moveToward()`.
- **MaceAssist.java**: Smash detection when `fallDistance > minSmashHeight` (`minSmashHeight = 2.0` default), `mace` in hand (`maceMode > (critOnly | comboOnly | both | normal)` + `witch/heal` override).
- **ChaseBehavior.java**: Parkour features — 1-block obstacle (`detectOneBlockObstacle()`), gap detection (`detectGapAhead()` — 5 blocks ahead), bridging (`bridgeTicks = 3`), towering (`> 1.5` blocks above target), wind burst (`60` tick cooldown), block breaking (`8` ticks stuck), sprint (`food > 6`, `!inWater`, `!inLava`, `distance > 2`). Combat chase: yaw oscillation (`±12°`), `supplementForward()` for main path (`NOT moveToward()` — preserves player A/D).
- **ClutchSystem.java**: Strategies — `WATER_BUCKET` (min 4 blocks, places below); `HAY_BLOCK` (min 8, places at position); `LADDER` / `VINE` (min 3, places at feet). Cooldown: `100ms`.
- **BowAssist.java`: Shield + zigzag approach (`zigzag` alternating), perpendicular dodge, zigzag + shield combo. `BowPhysicsSolver.java` — trajectory simulation (`projectileSpeed`: arrow `3.0`, snowball/egg `1.5`, potion `0.5`, trident `2.5`).

---

## Phase 3 Systems (In Progress / Designed — All 8 Listed)
From `.ai/architecture/core-decisions.md` and `.ai/memory/key-facts.md` (8 new files to implement):
1. `prediction/KalmanFilter.java` — `org.apache.commons.math3.filter.KalmanFilter` replacement (9D state vector: `[x, vx, ax, y, vy, ay, z, vz, az]`). `Q` tuned per entity type; `R` per sensor.
2. `prediction/PredictionIntegration.java` — unified pipeline: `Kalman → MovementPredictor (kinematic/behavioral/jump) → BowPhysicsSolver`.
3. `combat/ActionBufferSystem.java` — tick-aligned queue (`BufferedAction(int delayTicks, Runnable action, String name)`). Usage: `enqueue(1, () -> sprintCancel)`, `enqueue(2, () -> jumpFromGround())`, `enqueue(4, () -> attackClick())`, `enqueue(5, () -> sprintResume)`.
4. `combat/CombatRhythmEngine.java` — tempo states (`PRESSURE` → `RESET` → `BURST` → `BAIT` → `RE_ENGAGE` → `FINISH`). Transition conditions: `targetHealth < 4` → `FINISH`; `myHealth < 6 && distance < 4` → `RESET`; `distance > 8 && myHealth > 10` → `BURST`; `tempoTicks > 40 && current == PRESSURE` → `RESET`.
5. `movement/MovementGraph.java` — WASD state graph (`SCANG_RADIUS = 8`). Nodes: `solid`, `passable`, `hazard`. `A*` search with combat scoring (`cost = base + combatValue`).
6. `movement/MovementArbiter.java` — enhanced `MovementIntent` bus (exists; Phase 3 = `A*` combat scoring layer). Resolver picks highest-priority intent and applies via `ctrl.moveToward()` (`≥ 70`) or `supplementForward()` (`< 70`).
7. `combat/RecoveryPlanner.java` — stuck detection (`stuckTicks > 8` → `missedJump` recovery; `edgeSlip` → backward supplement; `knockbackRecovery` → jump reset + perpendicular dodge). Damage ETA from `projectile.simulate()`.
8. `combat/DodgeSystem.java` — threat timeline upgrade (`simulateProjectile()` + `willHit()` simulation; pre-emptive dodging at `15`-block radius; perpendicular evasion using cross-product with Y-axis).

---
