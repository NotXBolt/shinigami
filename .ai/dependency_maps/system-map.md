# System Dependency Map

## Core Dependency Chain (Config → Mod → Module → Controller → Arbiter → Subsystems)
```
AimAssistConfig (data layer — all config fields + getters/setters)
    → AimAssistMod.java (wiring layer — creates all systems on init)
        → KeyMovementController.java (WASD input override + supplement)
            ├── getOverriddenInput(Input) — supplement/override blend
            ├── moveToward(Vec3) — full WASD override (≤ 5 ticks TTL)
            ├── supplementForward() / supplementJump() — OR blend
            ├── clearSupplement() — runs at START of tickMovement()
            └── OVERRIDE_TTL = 5 (was 3)
        → AimAssistModule.java (central tick orchestrator)
            ├── tickMovement() — HEAD (pre-tick) + TAIL (post-tick)
            │   ├─ movementArbiter.clear()
            │   ├─ ctrl.clearSupplement()
            │   ├─ ChaseBehavior → submits MovementIntent(PARKOUR/CHASE)
            │   ├─ AirStrafeController → submits MovementIntent(PARKOUR)
            │   ├─ DodgeSystem → submits MovementIntent(DODGE, 100)
            │   └─ movementArbiter.apply(ctrl) → resolves → applies
            ├── entityTracker.tick() → scans every 2-4 ticks
            ├── movementBrain.tick() → CombatFlow state machine
            ├── tagSystem.processTags()
            ├── clutchSystem / smartTask / autoInventory / durability / breathing
            ├── targetManager.tick() → hybrid scoring (0.4 dist + 0.4 angle + 0.2 health)
            ├── aimController.updatePredictions() → Kalman → kinematic → behavioral
            ├── critAssist / maceAssist (maceMode: critOnly/comboOnly/both/normal + witch/heal)
            ├── bowAssist / bridgeAssist / windBurstAssist
            ├── handleNormalAim() → rotation + smoothing + prediction ghost
            ├── Attack flow (crit combo + mace smash trigger)
            ├── triggerBot / dodgeSystem / comboTracker
            └── cleanup() → target persist + cooldown (20 ticks after kill)
        → AimAssistScreen.java (GUI: G key; tabbed: COMBAT, MOVEMENT, VISUAL, MISC)
        → AimAssistOverlay.java (HUD: minimal crosshair indicator when locked)
        → AimAssistKeybinds.java (R=toggle, G=GUI)
        → AimAssistRenderer.java (World render: ESP box + health bar [green/yellow/red] + prediction ghost [alpha=confidence/2] + tracers + velocity line; registered via WorldRenderEvents.AFTER_ENTITIES)

## Mixin Layer (Exact Files + Injection Points)
- MixinAimAssistClient.java (`launch/mixins/MixinAimAssistClient.java`) → Minecraft.tick() HEAD + TAIL
- MixinKeyboardInput.java (`launch/mixins/MixinKeyboardInput.java`) → KeyboardInput.tick() TAIL (primary)
- MixinLocalPlayerInput.java (`launch/mixins/MixinLocalPlayerInput.java`) → LocalPlayer.onInput() HEAD + TAIL (secondary)
- ClientInputAccessor.java (`launch/mixins/ClientInputAccessor.java`) → @Accessor for ClientInput.moveVector (NOT @Shadow — requires refmap)
- MixinKeyboardHandler.java → keyboard event interceptor
- MixinAimAssistChat.java → chat command intercept (`#` / `&` prefixes)

## Subsystem Detail Map
- KeyMovementController → override/supplement/passthrough modes (Input record blend)
- MovementArbiter (`movement/MovementArbiter.java`) → CMS resolver: DODGE(100) > CLUTCH(95) > CRIT(70) > CHASE(50) > COMBAT(30) > PARKOUR(20) > AUTO_WALK(10)
- MovementIntent (`movement/MovementIntent.java`) → priority + Vec3 direction + sprint + jumpType + sneak + durationTicks + reason
- ChaseBehavior (`tags/ChaseBehavior.java`) → WASD-only; supplementForward() for path; yaw oscillation ±12°; NO direct moveToward()
- AirStrafeController (`movement/AirStrafeController.java`) → continuous strafe; velocity yaw; no one-shot reset
- DodgeSystem (`combat/DodgeSystem.java`) → submits via arbiter; hasActiveDodge() (dodgeTicks > 0); getDodgeIntent(); dodgeOnlyMode applies velocity directly
- ClutchSystem (`combat/ClutchSystem.java`) → water/hay/ladder; 100ms cooldown
- CritAssist (`combat/CritAssist.java`) → 4-state machine (idle → sprint cancel → jump → airborne [5 ticks] → crit ready); FULL_DAMAGE_THRESHOLD=0.848f
- ComboTracker (`combat/ComboTracker.java`) → comboCounter; lastHitTime; W-tap (wTapTimer=2); S-tap (sTapTimer=1)
- MaceAssist (`combat/MaceAssist.java`) → smash when fallDistance > minSmashHeight (2.0) + mace in hand; mode: critOnly/comboOnly/both/normal
- BowAssist (`combat/BowAssist.java`) → shield + zigzag + perpendicular + combo approach
- TargetManager (`targeting/TargetManager.java`) → scan + score (normalized dist/angle/health) + 20-tick cooldown after death
- TrackedTarget (`targeting/TrackedTarget.java`) → distScore/angleScore/healthScore weights 0.4/0.4/0.2
- EntityTrackerSystem (`system/EntityTrackerSystem.java`) → single scan every SCAN_INTERVAL (2-4 ticks); caches players/mobs/projectiles/items
- TerrainAnalyzer (`movement/TerrainAnalyzer.java`) → affordance classification (SPRINT_SAFE/JUMPABLE/CLIMBABLE/BRIDGEABLE/HEAD_HIT_RISK/VOID_RISK/OBSTACLE)
- MovementBrain (`movement/MovementBrain.java`) → CombatFlow (APPROACH/PRESSURE/EVADE/RESET/BURST/FINISH/RETREAT) + ActionBuffer + noise
- MovementPredictor (`prediction/MovementPredictor.java`) → 3-mode: kinematic (friction 0.91/0.98, gravity 0.08) + iterative (5-40 ticks) + behavioral (6 patterns) + jump arc
- BehavioralPredictor (`prediction/BehavioralPredictor.java`) → 6 patterns; confidence = base * 0.7(hurt) * 1.2(sprint, cap 1.0) * (1.0 - ticks*0.1); clamp [0.05, 0.95]
- KalmanFilter (`prediction/KalmanFilter.java`) → Commons Math; 9D state [x,vx,ax,y,vy,ay,z,vz,az]; Q tuned per type; R per sensor
- PredictionIntegration (`prediction/PredictionIntegration.java`) → unified pipeline: Kalman → MovementPredictor → BowPhysicsSolver
- BowPhysicsSolver (`prediction/BowPhysicsSolver.java`) → projectile trajectory; arrow speed 3.0; snowball/egg 1.5; potion 0.5; trident 2.5
- AimController (`aim/AimController.java`) → rotation calculation + smoothing + prediction offset; PID smoothing
- AimAssistRenderer (`render/AimAssistRenderer.java`) → ESP box + health bar (green/yellow/red) + prediction ghost (alpha=confidence/2) + tracers (solid to current, dashed to predicted) + velocity line
- AimAssistOverlay (`AimAssistOverlay.java`) → HUD crosshair indicator when locked
- AimAssistScreen (`AimAssistScreen.java`) → tabbed config (COMBAT/MOVEMENT/VISUAL/MISC)

## Phase 3 Enhancement Dependencies (8 New Files → Where They Fit)
1. `prediction/KalmanFilter.java` → replaces current 1D filter; used by MovementPredictor (full 9D state)
2. `prediction/PredictionIntegration.java` → connects Kalman output → MovementPredictor input; feeds BowPhysicsSolver
3. `combat/ActionBufferSystem.java` → consumed by MovementBrain (schedule sprint cancel → jump → attack → sprint resume)
4. `combat/CombatRhythmEngine.java` → integrates with MovementBrain (CombatFlow transitions based on health/distance/combo)
5. `movement/MovementGraph.java` → feeds MovementArbiter (A* combat scoring layer)
6. `movement/MovementArbiter.java` → enhanced resolver; uses MovementGraph scores
7. `combat/RecoveryPlanner.java` → called from MovementBrain (stuck detection → missedJump/edgeSlip/knockbackRecovery)
8. `combat/DodgeSystem.java` → upgraded threat timeline (`simulateProjectile` + `willHit` + 15-block pre-emptive radius)
