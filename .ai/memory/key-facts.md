# Key Project Facts

## Build
- Java 25 Temurin at `/usr/lib/jvm/java-25-openjdk-arm64`
- Gradle 8.14.4, `--no-daemon` required on Termux
- Build: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64 && ./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon`
- Output: `/storage/emulated/0/1log/Shinigami-by-Saizo-1.17.0.jar`

## Fabric API
- Version: 0.148.0+26.1.2
- NO HudRenderCallback or WorldRenderEvents — use HudElementRegistry + HudElement
- 42 sub-jars extracted to libs/extracted/
- build.gradle: `compileOnly fileTree(dir: 'libs/extracted', include: '*.jar')`

## Key Constants
- FULL_DAMAGE_THRESHOLD: 0.848f (84.8%) — vanilla full-damage breakpoint
- Hit range (vanilla melee): 3.0 blocks
- Attack speed (sword): 1.6 → 12.5 ticks cooldown; axe: 1.0 → 20 ticks
- Critical hit charge threshold: 90% (`attackStrengthScale > 0.9`)
- Detection range: 64.0 blocks default (`detectionRange` config)
- Mace smash height: `minSmashHeight = 2.0` blocks
- Air drag: 0.98; ground drag: 0.91; gravity: 0.08 blocks/tick²
- Sprint acceleration: 0.13; max speed ~5.612 m/s (sprinting)
- Jump velocity: 0.42 m/s upward; max height ~1.25 blocks
- Diagonal movement normalization: ×0.7071 (when both forward + strafe active)
- Input packet: `ServerboundPlayerInputPacket(xxa, zza, isJumping, isShiftKeyDown)` — `xxa` positive = LEFT strafe, negative = RIGHT; `zza` positive = FORWARD, negative = BACKWARD
- CritAssist state machine: STATE 0 (idle) → 1 (sprint cancel, 1 tick) → 2 (jump trigger, 1 tick) → 3 (airborne, 5 ticks) → 4 (crit ready)
- Dodge TTL (`OVERRIDE_TTL`): 5 ticks
- Target switch cooldown: 20 ticks
- Entity scan interval (`SCAN_INTERVAL`): 2-4 ticks
- Build output jar: `fabric/build/libs/baritone-fabric-1.17.0.jar` (~2.0 MB)
- Deploy path: `/storage/emulated/0/1log/Shinigami-by-Saizo-1.17.0.jar`
- JAVA_HOME: `/usr/lib/jvm/java-25-openjdk-arm64`
- Gradle wrapper: 8.14.4; `--no-daemon` required on Termux
- Fabric API version: `0.148.0+26.1.2`; loader `0.19.2+`
- Mapping: Mojang official (1.21.1); NO refmap generated → `@Shadow` fails, use `@Accessor` (e.g., `ClientInputAccessor.java`)
- Build command exact: `./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon`
- Quick compile exact: `./gradlew :fabric:compileJava --no-daemon 2>&1 | tail -10`
- Mixin files exact paths: `launch/mixins/MixinAimAssistClient.java`, `launch/mixins/MixinKeyboardInput.java`, `launch/mixins/MixinLocalPlayerInput.java`
- Config GUI tabbed: `COMBAT`, `MOVEMENT`, `VISUAL`, `MISC`; `G` key opens (`AimAssistScreen.java`)
- Keybinds: `R` = toggle (`AimAssistKeybinds.java`), `G` = GUI (`AimAssistScreen.java`)
- Mod entry point: `AimAssistMod.java` (`fabric/src/main/java/baritone/aimassist/AimAssistMod.java`)
- Module tick orchestrator: `AimAssistModule.java` (`tick()` + `tickMovement()`)
- Input controller: `KeyMovementController.java` (`getOverriddenInput()`, `moveToward()`, `supplementForward()`, `clearSupplement()`)
- Movement arbiter: `movement/MovementArbiter.java`
- Movement brain state machine: `movement/MovementBrain.java` (`CombatFlow`: APPROACH, PRESSURE, EVADE, RESET, BURST, FINISH, RETREAT)
- Chase behavior: `tags/ChaseBehavior.java` (WASD-only, supplement mode, yaw oscillation ±12°)
- Air strafe controller: `movement/AirStrafeController.java` (continuous strafing, velocity yaw, no one-shot reset)
- Prediction master: `prediction/MovementPredictor.java` (3-mode: kinematic + behavioral + jump arc)
- Behavioral predictor: `prediction/BehavioralPredictor.java` (6 patterns: CIRCLE_STRAFER, AGGRESSIVE_STRAFER, JUMPER, LINEAR_CHASER, PANIC_RUNNER, UNKNOWN)
- Kalman filter: `prediction/KalmanFilter.java` (Commons Math `org.apache.commons.math3.filter.KalmanFilter`; 9D state vector: `[x, vx, ax, y, vy, ay, z, vz, az]`)
- Entity tracker: `system/EntityTrackerSystem.java` (scan interval 2-4 ticks, single-pass filter)
- Target manager: `targeting/TargetManager.java` (hybrid scoring: 0.4 distance + 0.4 angle + 0.2 health, normalized to [0,1])
- Dodge system: `combat/DodgeSystem.java` (19 threat types; `MovementIntent(Priority.DODGE)` submitted via arbiter; `dodgeOnlyMode` applies velocity but submits intent)
- Crit assist: `combat/CritAssist.java`
- Mace assist: `combat/MaceAssist.java`
- Bow assist: `combat/BowAssist.java`
- Bridge assist: `combat/BridgeAssist.java`
- Clutch system: `combat/ClutchSystem.java` (water bucket min 4, hay block min 8, ladder/vine min 3; 100ms cooldown)
- Rendering backend: `render/AimAssistRenderer.java` (ESP box + health bar color-coded green/yellow/red + prediction ghost alpha = confidence/2 + tracers + velocity line; registered via `WorldRenderEvents.AFTER_ENTITIES`)
- HUD overlay: `AimAssistOverlay.java` (minimal crosshair indicator when target locked)
- Visual config tab: `AimAssistScreen.java` (tabbed: COMBAT, MOVEMENT, VISUAL, MISC)
- Crit requires: 90%+ charge, airborne, !sprinting
- Attack range: 3.0 blocks (vanilla)
- Detection range: configurable (default 64)
- Dodge priority: threat-based, never retreat
- CritAssist: 1-tick burst (sprint cancel + jump in same tick via requestCrit())

## Known Fixes
- @Shadow for inherited fields fails without refmap → use @Accessor mixin instead
- strafe direction: (left-right), not (right-left)
- Dodge must reapply ctrl.moveToward() every tick, not just on trigger
- clearSupplement() called at START of tickMovement(), cleared on disabled path
- Attack flow: maceMode > (critOnly | comboOnly | both | normal) + witch/heal override

## Phase 3 Systems
Eight new files to implement:
1. prediction/KalmanFilter.java — Commons Math replacement
2. prediction/PredictionIntegration.java — unified pipeline
3. combat/ActionBufferSystem.java — action queue
4. combat/CombatRhythmEngine.java — attack rhythm
5. movement/MovementGraph.java — WASD state graph
6. movement/MovementArbiter.java — A* path eval
7. combat/RecoveryPlanner.java — recovery actions
8. combat/DodgeSystem.java — threat timeline upgrade

--- Updated: $(date +%Y-%m-%d) ---
Structure fix completed (not GitHub sync):
- baritone-26.1 renamed → shinigami (code only, no .MD inside)
- All doc .MD (AGENTS.md, SHINIGAMI_REFERENCE.md, etc.) moved to parent /root/Ws/nxt/shini/
- .ai folder merged: shinigami .ai → parent .ai (11 files), removed from shinigami
- New AGENTS.md (10906 bytes) with continuity rules: .ai contracts, folder rules, sync protocol, self-governance
- Nothing git-related executed in /
- Next: sync ~/Ws/github/shini/ OR improve shinigami code

--- External Pattern Discovery (GitHub repos) ---
- GiaoShou66/Minecraft-PVP-bot (Python RL - PPO + CNN): action space/reward design useful; not extractable (Python vs Java).
- Stepan1411/pvp-bot-fabric (Fabric PvP bot - 29★, 137 commits): confirms all shinigami patterns (combat AI, critical hits, shield/mace, bunny hop, smart nav, auto-equip, retreat, faction/path/kit systems — potential future addition).
- Leg0shii/ParkourCalculator (archived - A* pathfinding): confirms MovementGraph/AggressiveParkour approach.
- ParkourCalculatorMod (PrismarineJS): confirms A* pathfinding for Minecraft.
No new .java edits needed unless faction/path/kit systems requested. All patterns documented in IMPROVEMENT_SPEC.md.

--- Additional External References (Production-Grade Automation) ---
- reflex-client-download: packet hooks (EntityVelocityUpdateS2CPacket suppression), FastHit (cooldown removal), Criticals (packet timing).
- cosmos: raw packet movement (packet optimization framework — confirms PacketDefenseMixin approach).
- meinbot: auto inventory/potion/armor/eat (confirms AutoInventory + AutoCombatSwitch patterns).
All match IMPROVEMENT_SPEC.md exactly. No .java edited. Zero destructive actions. Everything preserved.

--- Universal Architecture References ---
- Architectury API (architectury/architectury-api): multi-version abstraction framework for universal mods.
- Fabric Yarn (FabricMC/yarn): cross-version obfuscation mappings.
- Fabric API (FabricMC/fabric): ClientPlayConnectionEvents / ClientTickEvents (stable hooks).
- Multi-module Gradle structure: shinigami-common (95% code) + shinigami-fabric-1.20/1.21 (version wrappers).
Current shinigami uses Fabric native (Gradle 8.14.4, Fabric 0.148.0+26.1.2, Mixin injections) — can migrate to Architectury for universal compatibility if needed.
