# Session Timeline

## 2026-05-11 — Mixin Architecture Overhaul + Movement Fixes
- Fixed movement burst bug: moved setActive + tickMovement to HEAD (pre-entity-tick)
- Created MixinKeyboardInput (primary) + MixinLocalPlayerInput (secondary) 3-layer injection
- Fixed @Shadow → @Accessor for inherited moveVector field
- ChaseBehavior: switched to yaw steering + supplement (no moveToward)
- combatStrafe: removed moveToward call
- Mod disabled: explicit clearSupplement + stopMoving on both paths
- Arbiter: removed clearSupplement from empty-intents path
- BUILD SUCCESSFUL (2.0M jar)

## 2026-05-12 — Combat Rewrite + Phase 3 Planning
- Fabric API 0.148.2 removed HudRenderCallback/WorldRenderEvents → switched to HudElementRegistry
- 15 compilation errors fixed (mojmap changes, imports, API removals)
- MovementPredictor fixed (removed stale onLanding call)
- Attack threshold raised to 84.8% (FULL_DAMAGE_THRESHOLD = 0.848f)
- Mode separation: crit-only / combo-only / both / normal / witch-heal
- CritAssist rewritten: 1-tick burst, no state machine
- AimController: AABB center aim with height-tiered Y offsets
- DodgeSystem: witch potion detection + shield raise
- Strafe inversion fixed: (left-right) confirmed correct
- TargetManager: threat-based priority with normalization
- Phase 3 architecture designed — 8 new systems

## 2026-05-13 — Memory System Setup
- Created .ai/ workflow memory directory structure
- Stored project knowledge in opencode-mem persistent database
- Installed memory CLI command
- Updated AGENTS.md with memory system info

## 2026-05-14 — Master-Plan Architecture Synthesis (Ocey)
- Read FULLY: AGENTS.md, SHINIGAMI_REFERENCE.md (all sections 1-18), memory-add.md, RESEARCH_FIXES.md, SETUP.md, FEATURES.md, README.md, USAGE.md, CHATGPT_PROMPT.md, CODE_OF_CONDUCT.md
- Read .ai/ continuity files: architecture/core-decisions.md, memory/key-facts.md, dependency_maps/system-map.md, issues/README.md, timelines/session-log.md
- Verified structure fix: `baritone-26.1` renamed → `shinigami` (code inside `fabric/src/main/java/baritone/aimassist/`); docs (.md) moved to parent `/root/Ws/nxt/shini/`; `.ai/` folder merged (6 subfolders + 11 files) from original shinigami `.ai` to parent `.ai`
- AGENTS.md upgraded (now 231 lines): added Section 10b (mixin strategy — 3 mixins with exact files/points), 10c (3-layer input — supplement/steering/emergency with exact constants), 10d (prediction 3-mode — physics/iterative/behavioral + Kalman 9D), 10e (dodge 3-layer predictive — 19 threat types + never retreat), 10f (Phase 3 — all 8 new systems listed with file paths and descriptions), 10g (build/deploy exact commands), 10h (continuity protocol checklist table), 10i (quality gates — 8 verifiable actions)
- Confirmed AGENTS.md references ALL 8 Phase 3 systems: KalmanFilter, PredictionIntegration, ActionBufferSystem, CombatRhythmEngine, MovementGraph, MovementArbiter (enhanced), RecoveryPlanner, DodgeSystem (upgrade)
- `.ai/architecture/core-decisions.md` enhanced with full tick timeline, exact mixin injection points (`HEAD`/`TAIL`), 3-layer input details (`KeyMovementController` blend/override logic), CMS resolver tiers (`DODGE=100` ... `AUTO_WALK=10`), movement formulas, targeting scoring weights (`0.4/0.4/0.2`), combat layer details (19 threats, 4-state crit machine, combo mechanics, mace mode, bow approach patterns)
- `.ai/memory/key-facts.md` expanded with concrete constants: `FULL_DAMAGE_THRESHOLD=0.848f`, hit range `3.0`, detection default `64`, JDK path exact, Gradle `8.14.4`, build flags exact (`-x test -x :fabric:proguard -x :fabric:createDist --no-daemon`), output jar path exact, deploy path exact, `Fabric API 0.148.0+26.1.2`, `ClientInputAccessor` @Accessor explanation, all file paths (`AimAssistMod.java`, `MovementArbiter.java`, `BehavioralPredictor.java`, etc.), build/compile/deploy commands, mixin file paths
- `.ai/dependency_maps/system-map.md` rewritten with full dependency chain (`Config → Mod → Controller → Arbiter → Subsystems`), exact mixin layer (`ClientInputAccessor` @Accessor note), subsystem detail map (each file + role), Phase 3 enhancement dependency mapping
- `.ai/issues/README.md` expanded from 1 line to full inventory: 19 fixed bugs (table format with criticality/file/root cause/status), 9 remaining/partial issues (jitter, VISUAL wiring, DodgeOnlyMode, farm stubs, parkour stub, heatmap, ActionPlanner, ParkourChain, RecoveryPlanner, CombatRhythmEngine), build verification commands
- `.ai/timelines/session-log.md` updated with new session entry (`2026-05-14`)
- `SPEC.md` created: Implementation Plan with checklist format; covers Executive Summary (`shinigami` by Saizo, Minecraft 26.1 Fabric, Termux/Android, JDK 25 ARM64), Project Structure Tree (`fabric/src/main/java/baritone/aimassist/` layout), Build & Deploy Checklist (8 `- [ ]` items with concrete actions: JAVA_HOME set, exact gradlew flags, jar verified, deploy completed), Continuity Protocol Checklist (6 `- [ ]` items: `.ai/` read, `.ai/` write before compaction, sync to `~/Ws/github/shini/`, AGENTS.md verified, memory search executed), Architecture Checklist (mixin injection verified, tick timeline reconstructed, input 3-layer active, CMS resolver applied), Movement Checklist (supplement/steering/emergency verified), Combat Checklist (19 threats, dodge direction, combo tracking, shield sequence, sprint algorithm, W-tap adaptive, bow approach, mace detection, wind burst, crit assist 1-tick burst), Prediction Checklist (physics O(1), iterative 5-40 ticks, behavioral 6 patterns, Kalman 9D state vector), Quality Gates Checklist (compile, build, `.ai/` verification, architecture review, docs update, sync check) — total checklist count: 48 verifiable checklist items (8 Build + 6 Continuity + 8 Architecture + 5 Movement + 10 Combat + 4 Prediction + 6 Quality Gates + 1 Cross-Project Awareness = 48)
- Zero `.java` source files modified; zero `git` commands executed; only `.md` updates/creations performed
- Next: sync `~/Ws/github/shini/` OR improve shinigami code (Phase 3 systems)

## 2026-05-14 — GitHub Flatten + Release Workflow + Unkillable Hardening (Ocey) — CONTINUED
- **Structure flatten**: `~/Ws/github/shini/shinigami/` nested → root (`cp -a shinigami/. . && rm -rf shinigami`, 518 renames `shinigami/.build_counter -> .build_counter` etc, commits `ef39dfc fix: flatten structure so .github at repo root`), verified `https://github.com/NotXBolt/shinigami` now shows `.github/`, `fabric/`, `src/` at top level (not nested)
- **Publishing workflow**: `AGENTS.md 5b` added (repo `NotXBolt/shinigami` `master`, sync `cp -a shinigami/. ~/Ws/github/shini/` + verify `.github/workflows/unified-auto-release.yml && fabric/build.gradle` + `git -C ~/Ws/github/shini add -A && commit && push`; workflow `.github/workflows/unified-auto-release.yml` at root: `push master/main` + `tags v*` + `workflow_dispatch` → `setup-java@v4 temurin 25` → `./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon` → verify `fabric/build/libs/baritone-fabric-1.17.0.jar` → branch `upload-artifact@v4` (`Shinigami-by-Saizo-<sha>`) / tag `softprops/action-gh-release@v2` (`files: fabric/build/libs/baritone-fabric-1.17.0.jar`, `generate_release_notes: true`) → tag `v26.1.2-shinigami` pushed, release triggered `https://github.com/NotXBolt/shinigami/releases/tag/v26.1.2-shinigami`)
- **Build-error hardening (5ed091a)**: Actions `Shinigami Auto-Build & Release #1` failed 11s `JAVA_HOME is set to an invalid directory: /usr/lib/jvm/java-25-openjdk-arm64` + `DodgeSystem.java:233 error: statements not expected outside of methods` (duplicate `checkMeleePredictive` 24-line block) + `BehavioralPredictor.java` duplicate `zigzagScore` + invalid `confidence/ticksAhead` + typo `barotine/aimassist/ShinigamiUnifiedOrchestrator.java` + Frankenstein `ContinuousChaser`/`AggressiveParkourController`/`ShinigamiPacketDefenseMixin` with wrong `MinecraftClient`/`Vec3d` mappings → fixed: removed invalid `Setup JAVA_HOME` step from workflow (rely on `setup-java`), removed duplicate blocks, removed `barotine` package, deleted 3 Frankenstein files, reverted `MovementArbiter` `winner=null` → `intents.get(0)`, reverted `AimController` `smoother.applyPerfectSmoothTuning()`, reverted `DodgeSystem tick()` always-run, fixed `mixins.aimassist.json` 6 entries; push `5ed091a fix: correct DodgeSystem duplicate... restore build` → Actions now green `2m41s`/`4m19s`
- **Repos unified**: `/root/Ws/nxt/shini/repos/` 12 cloned (`Minecraft-PVP-bot`, `pvp-bot-fabric` 29★, `maple` A*, `mineflayer-pathfinder`, `Stonecraft`, `huntress-hacked-client`, `fabric`, `yarn`, `cosmos`, `meinbot`, `ParkourCalculator`, `ParkourCalculatorMod`; `Smartouspeak/reflex-client-download` → `Repository not found`), `IMPROVEMENT_SPEC.md` 71357B documents behavioral/predictive/aggressive/perfect/kill/universal 26.1.2 patterns; surgical `.java` edits: `BehavioralPredictor.predictBefore` repetitive `>0.8`, `PIDController.applyPerfectSmoothTuning` `2.0/0.1/0.5`, `DodgeSystem` predictive comment, `AimController perfectLock`; new files `ContinuousChaser`/`AggressiveParkourController`/`UnifiedModuleConnector` → later removed for build stability; package unified `org.stepan1411.pvp_bot` → `baritone.aimassist.combat` then external folder removed (single standalone mod, not Frankenstein)
- **Sync protocol enforced**: No `git` in `/`; all pushes from `~/Ws/github/shini` only after `cp -a .../. ~/Ws/github/shini/`; mirror now lean code-only root (`.ai/` + docs stay in source truth `/root/Ws/nxt/shini/`)
- **Next**: Tag already pushed `v26.1.2-shinigami` → monitor `https://github.com/NotXBolt/shinigami/actions` and `https://github.com/NotXBolt/shinigami/releases` for auto-release artifact; universal multi-module (Stonecutter/Architectury) deferred to Phase 3
## 2026-09-09 — Total from scratch pushed ff7aaf0 (Phase 0 shinigami package, 14 files, baritone removed, 360°, chase/pakur/dodge original)
## 2026-09-09 — Super crazy ParkourEnforcer pushed 59342b2 (ChaseBehavior enforcer, total from scratch)
## 2026-09-09 — Fix build 74ebff9 failure (baritone.aimassist not found, IronGolem, ParkourEnforcer) + version ${version} + icon per build, pushed f128c8f
## 2026-09-09 — Clean total-from-scratch 5c20025 (removed 60 Frankenstein files, baritone mixins, AimAssist*, keep only shinigami Phase0, pushed clean)
## 2026-09-09 — Release v26.1.3-shinigami (5c20025 clean total-from-scratch, 2m32s green, 4 assets Shinigami-by-Saizo-*.jar)

## 2026-09-09 — v1.0.5 single mod, all 22 repos integrated + GUI/RL/Safe (Ocey)
- What: Total-from-scratch shinigami.* kept, added gui/ (ShinigamiScreen + ShinigamiKeybinds R/G), learning/ (ReinforcementLearner Q-learning), safety/ (SafeGuard), combat/ (Crit/Combo/Mace/Bow), integrated/ (22 rebranded modules + IntegrationRegistry.tickAll wired into ShinigamiMod.onPreClientTick)
- Why: GUI gone + R toggle gone + auto-run into mobs dying; user demanded better + RL + safe no-progress-loss + all combat/GUI/dodge perfect + all repos in + single mod + per-setting descriptions
- Where: shinigami/fabric/src/main/java/shinigami/{gui,learning,safety,combat,integrated}/, ShinigamiMod.java, ShinigamiConfig.java (enabled=false default, targetPassive=false default), gradle.properties 1.0.4->1.0.5, fabric.mod.json description
- Status: pushed 0ce5d1a, FAILED build (GuiGraphics/KeyBindingHelper/Category/displayClientMessage/MaceBot case)
## 2026-09-09 — Fix v1.0.5 build 0ce5d1a failure (Ocey)
- What: GuiGraphics->GuiGraphicsExtractor + render->extractRenderState (26.1 API, proven by 74ebff9 OldScreen), Category String->Category.MISC, displayClientMessage->sendSystemMessage, removed KeyBindingHelper (no fabric-api dep, GLFW polling instead), registry ShinigamiMaceBot->ShinigamiMacebot, rewrote 22 integrated modules gated respectively (DODGE/PARKOUR/CHASE/COMBAT/NONE with isRelevant gates, no 22x spam)
- Why: 26.1 mojmap has no GuiGraphics/KeyBindingHelper pkg; Category is enum not String; Player msg is sendSystemMessage
- Where: gui/ShinigamiScreen.java, gui/ShinigamiKeybinds.java, ShinigamiMod.java, integrated/IntegrationRegistry.java, integrated/*/Shinigami*.java
- Status: pushed 30403d3, build in_progress, continuity update next
## 2026-09-09 — Build green a6ecfff all 3 jobs (Ocey)
- What: Restored ClientModInitializer import dropped in rewrite; Release 2m06s + JavaCI 2m17s + Tests 3m55s all success
- Where: ShinigamiMod.java:3, mirror a6ecfff
- Status: done, v1.0.5 single mod green; .ai continuity (session-log/issues/key-facts/repo-assignment) + AGENTS.md updated
## 2026-09-10 — v1.0.7 real RL + ultimate dodge overhaul SHIPPED (Ocey)
- What: learning/ReinforcementLearner (real tabular Q-learning: DODGE/ENGAGE/STRAFE/WEAPON spaces, ε-greedy 0.3→0.05, α=0.1 γ=0.9, space-aware reward(Space,r), persisted ~/shinigami_qtable.bin, save every 6000 ticks); DodgeSystem rewritten (multi-threat aggregation + isSafeDodgePosition port of Dodger [lava/fire/cactus/magma/cobweb/powder-snow/void+solid ground] + safeLandingPosition + RL DODGE, never runs into enemies); AutoCombatSwitch respects held weapon when autoWeapon=false (axe included), ON = RL WEAPON bias + axe/sword/mace/bow switch; CritAssist pvp-bot fall-based crits (sprint-cancel+jump → wait falling vel.y<0 → attack); MaceAssist/WindBurstAssist fire/wind charge self-launch at feet then smash; AimAssistModule RL gate (engage/wait when totalSteps>200, bestActionValue>0.5) + rewards on hit/damage/dodge survival/dodge-end; config autoWeapon OFF default + rlLearning ON default + rlAlpha/Gamma/Epsilon; GUI Row 4 AutoWeapon/RL toggles
- Why: user directive — "hook everything up, make it ultimate dodgeable no matter how many mobs, perfectly dodge everything alone, choose more libraries, more techniques, make existing ones better, actually start real reinforcement learning"; fix complaints (runs into enemies, doesn't use given weapon unless autoWeapon, crit timing, mace+fire/wind charge)
- Where: baritone/aimassist/{learning/ReinforcementLearner.java [NEW], combat/DodgeSystem, combat/AutoCombatSwitch, combat/CritAssist, combat/MaceAssist, combat/WindBurstAssist, AimAssistModule, AimAssistConfig, AimAssistScreen}, gradle.properties mod_version 1.0.6→1.0.7
- Fix caught by CI: CritAssist getVelocity()→getDeltaMovement() (getVelocity NOT in 1.21.x mojmap; 3 sites lines 42/57/101)
- Status: pushed e46c903 (feat) + ddb6018 (fix), both workflows green, tag v1.0.7-shinigami released Shinigami by Saizo v1.0.7 (asset Shinigami-by-Saizo-1.0.7.jar, 5377150 bytes); AGENTS.md 10k.5 + this session log updated
