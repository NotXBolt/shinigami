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
