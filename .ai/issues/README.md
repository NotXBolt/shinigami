# Known Issues (From RESEARCH_FIXES.md)

## Fixed Bugs

| # | Bug | Criticality | File | Root Cause | Status |
|---|---|---|---|---|---|
| 1 | Strafe inverted | CRITICAL | `MixinKeyboardInput.java` | `(right-left)` instead of `(left-right)`; positive strafe mapped to LEFT instead of RIGHT | Fixed — confirmed matches vanilla `Input.getMoveVector()` |
| 2 | A/D inverted in `moveToward()` | CRITICAL | `KeyMovementController.java` | Strafe vector `(cos(yaw), sin(yaw))` is LEFT at yaw=0; override assignment was swapped (positive dot → `overrideRight`, should be `overrideLeft`) | Fixed — swapped assignment; positive dot → `overrideLeft` |
| 3 | Forward/backward oscillation loop | HIGH | `DodgeSystem.java`, `AimAssistModule.java`, `MovementArbiter.java` | Dodge used full `moveToward()` (3-tick TTL) fighting chase supplement; after TTL expired supplement resumed; re-trigger created oscillation | Fixed — Dodge submits `MovementIntent(Priority.DODGE)` via arbiter; `OVERRIDE_TTL = 5`; `clearSupplement()` removed from empty-intents path |
| 4 | Target switching after kill | HIGH | `TargetManager.java` | No cooldown; dead entity filtered → next nearest selected immediately | Fixed — `targetSwitchCooldown = 20` ticks; check `entity.isRemoved()` + `!entity.isAlive()` |
| 5 | Hybrid priority broken | HIGH | `TrackedTarget.java` | Distance (0-64) and angle (0-180) not normalized; raw sums dominated by distance | Fixed — normalized both to `[0,1]`; weights `0.4` (dist) + `0.4` (angle) + `0.2` (health) |
| 6 | Prediction not working | HIGH | `MovementPredictor.java` | Only linear extrapolation; no friction/gravity/pattern classification | Fixed — 3-mode: kinematic (`friction=0.91/0.98`, `gravity=0.08`) + behavioral (6 patterns) + jump arc; confidence modifiers (`hurtTime*0.7`, `sprint*1.2` cap 1.0, `tickDecay=1.0-ticks*0.1`) |
| 7 | Visual category non-functional | MEDIUM | `AimAssistRenderer.java` | No rendering backend; VISUAL tab existed but no `WorldRenderEvents` wiring | Fixed — ESP box + health bar (green>yellow>red) + prediction ghost (`alpha=confidence/2`) + tracers + velocity line via `WorldRenderEvents.AFTER_ENTITIES` |
| 8 | Air strafe one-shot | MEDIUM | `AirStrafeController.java` | Used player yaw instead of velocity yaw; `active=false` reset made it one-shot | Fixed — uses velocity yaw; removed `active=false` reset; continuous strafe |
| 9 | Chase used `moveToward()` | MEDIUM | `ChaseBehavior.java` | Main chase path called `ctrl.moveToward()` instead of supplement | Fixed — `supplementForward()` for chase; `moveToward()` only for bridge/tower emergency |
| 10 | MovementArbiter cleared supplement | MEDIUM | `MovementArbiter.java` | Empty-intents path called `clearSupplement()`; when no subsystems submitted, supplement was wiped | Fixed — `clearSupplement()` never called in empty-intent path |
| 11 | Mod disabled but still moved | MEDIUM | `AimAssistMod.java` | Supplement not cleared when `enabled=false`; `tickMovement()` kept supplement active | Fixed — explicit `clearSupplement()` + `stopMoving()` on disabled path |
| 12 | `getDetectionRange` missing from API | MEDIUM | `IAimConfig.java` (API interface) | `TrackedTarget.java` called `getDetectionRange()` / `setDetectionRange()` but interface didn't declare them | Fixed — added to `IAimConfig.java` interface |
| 13 | Fake `LivingEntity` compile error | LOW | `MovementPredictor.java` | Anonymous class didn't override `getMainArm()` — `BehavioralPredictor` tried to create fake entity for prediction | Fixed — removed anonymous `LivingEntity` creation; added `predictPosition(Vec3, Vec3, float, int, int)` overload |
| 14 | `historyMap` private access | LOW | `MovementPredictor.java` | Direct access to private `BehavioralPredictor.historyMap` field | Fixed — added public `hasHistory(int entityId)` method |
| 15 | `getCurrentTarget()` missing in Chase | LOW | `ChaseBehavior.java` | Method didn't exist for external consumers (`DodgeSystem`, `TargetManager`) | Fixed — added `getCurrentTarget()` method |
| 16 | `hasActiveDodge()` / `getDodgeIntent()` missing | LOW | `DodgeSystem.java` | Methods missing; `MovementArbiter` couldn't read dodge state | Fixed — added `hasActiveDodge()` (`dodgeTicks > 0`) and `getDodgeIntent()` (`MovementIntent(Priority.DODGE, ...)`) |
| 17 | `OVERRIDE_TTL` too short | LOW | `KeyMovementController.java` | `3` ticks caused unstable override; dodge expired too quickly | Fixed — increased to `5` ticks |
| 18 | Forward + backward conflict in supplement | LOW | `KeyMovementController.java` | If `supplementForward` set `forward=true` but original input also had `backward=true`, both active simultaneously → conflict | Fixed — if `fwd && bwd`, `backward` is cleared (`bwd = false`) |
| 19 | `@Shadow` remap failure | CRITICAL | `ClientInputAccessor.java` | `@Shadow` requires generated refmap (`.gradle/`); not present in build; compile fails with `shadow` resolution error | Fixed — replaced with `@Accessor` mixin (`ClientInputAccessor.java`) targeting `ClientInput.moveVector`; avoids `@Shadow` entirely |

---
## Known Remaining Issues (Not Yet Fixed / Partially Implemented)
- Prediction can cause aim jitter on erratic targets (`predictAmount` config default `1.0` — reduce if jitter occurs).
- VISUAL tab wiring to config toggles is incomplete (`showPrediction`, `showTrajectory`, `showHUD` toggles exist but not fully connected to renderer).
- `DodgeOnlyMode` (movement off, dodge on) still applies velocity directly (`setDeltaMovement`) — not fully routed through `MovementArbiter`.
- `autoFarmMode()` (`AimAssistModule`) has `TODO` markers (`Furnace` interaction, `Smelt` actions incomplete).
- `AggressiveParkour.java` (`pathfinding/AggressiveParkour.java`) exists but may be a stub (no full `A*` path integration yet; Phase 3 `MovementGraph.java` will address).
- `ThreatPriorityHeatmap` (`dodgeScore[x][z]`) from `BehavioralPredictor` — NOT IMPLEMENTED (`dangerScore` array not present).
- `Multi-TickActionPlanner` (`ActionBufferSystem.java`) — NOT FULLY IMPLEMENTED (queue exists but scheduling logic partial).
- `ParkourChainSystem` (`TraversalAction` with `cost/risk/momentumGain/combatValue`) — NOT IMPLEMENTED (Phase 4).
- `RecoveryPlanner.java` (`missedJump`, `edgeSlip`, `knockbackRecovery`) — STUB (detection logic present, recovery actions partial).
- `CombatRhythmEngine.java` — STUB (tempo enum exists, transition logic partial; needs full `Fabric EventBus` wiring).

---
## Build & Verification Commands (Every Fix Must Pass)
```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64
./gradlew :fabric:compileJava --no-daemon 2>&1 | tail -10
./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon
```
Deploy: `cp fabric/build/libs/baritone-fabric-1.17.0.jar /storage/emulated/0/1log/Shinigami-by-Saizo-1.17.0.jar`
