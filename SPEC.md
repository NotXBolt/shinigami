# SHINIGAMI — Implementation Plan / Spec
> **Master Plan Architect synthesis** — ZERO code execution. Markdown-only updates/creations.
> **Last Updated**: 2026-05-14 — Master-plan synthesis executed (Ocey). [—Ocey]

---

## Executive Summary

| Attribute | Value |
|---|---|
| **Project** | Shinigami by Saizo |
| **Mod ID** | `shinigami` (not baritone) |
| **Platform** | Minecraft 26.1 (1.21.1) Fabric standalone |
| **Loader** | Fabric Loader 0.19.2+ |
| **Mojang Mappings** | Official 1.21.1 |
| **Build Environment** | Termux (Android / ARM64) |
| **JDK** | Java 25 Temurin ARM64 (`/usr/lib/jvm/java-25-openjdk-arm64`) |
| **Gradle** | 8.14.4 wrapper (`--no-daemon` required) |
| **Runtime Dependency** | Fabric API `0.148.0+26.1.2` (42 sub-jars in `libs/extracted/`) |
| **Build Flags (exact)** | `-x test -x :fabric:proguard -x :fabric:createDist --no-daemon` |
| **Output JAR** | `fabric/build/libs/baritone-fabric-1.17.0.jar` (~2.0 MB) |
| **Deploy Target** | `/storage/emulated/0/1log/Shinigami-by-Saizo-1.17.0.jar` |
| **Install Path** | Zalith Launcher 1.4.1.4 / Minecraft 26.1.2 Fabric 0.19.2 |
| **Agent** | Ocey (male) — builder / speed / ship-it |
| **Anchor** | Opal (female) — architecture / continuity / reads-first |
| **Leader** | Sarib (male) — direction / vision |

**Why this spec exists**: `AGENTS.md` is the operational contract; `.ai/` is the live cognitive layer; `SPEC.md` is the verifiable implementation checklist. Every change must pass all 45 checklist items below.
**Where this lives**: `/root/Ws/nxt/shini/SPEC.md` (this file); continuity state lives in `/root/Ws/nxt/shini/.ai/`.

---

## Project Structure Tree (Directory Layout + Roles)

```
/root/Ws/nxt/shini/
├── shinigami/                          # LIVE SOURCE (Fabric mod code + build artifacts)
│   ├── fabric/src/main/java/baritone/aimassist/   # MOD CORE (~50 files — only edit here for code)
│   │   ├── AimAssistMod.java               # Entry point; wires all subsystems (Config → Module → Controller → Arbiter)
│   │   ├── AimAssistConfig.java            # All config fields + getters/setters (FULL_DAMAGE_THRESHOLD=0.848f, range=3.0, detection=64)
│   │   ├── AimAssistModule.java            # Central hub: tick(), tickMovement() (calls arbiter.clear(), ctrl.clearSupplement())
│   │   ├── AimAssistKeybinds.java          # R=toggle, G=GUI; chat commands (#/prefix)
│   │   ├── AimAssistOverlay.java           # HUD crosshair indicator (locked target)
│   │   ├── AimAssistScreen.java            # Config GUI (tabbed: COMBAT / MOVEMENT / VISUAL / MISC)
│   │   ├── aim/                             # AimController, PIDController, RotationSmoother
│   │   ├── combat/                          # AutoCombatSwitch, BowAssist, BridgeAssist, ClutchSystem, ComboTracker,
│   │   │                                      CritAssist (4-state machine), DodgeSystem (19 threats), MaceAssist,
│   │   │                                      TriggerBot, WindBurstAssist, CombatPeripherals
│   │   ├── movement/                        # MovementIntent, MovementArbiter (CMS resolver), MovementBrain (CombatFlow),
│   │   │                                      AirStrafeController, TerrainAnalyzer
│   │   ├── pathfinding/                     # AggressiveParkour (stub / early)
│   │   ├── prediction/                      # MovementPredictor (3-mode), BehavioralPredictor (6 patterns),
│   │   │                                      KalmanFilter (Commons Math 9D), VelocityEstimator, BowPhysicsSolver, JumpArcPredictor
│   │   ├── render/                          # AimAssistRenderer (ESP + health bar + prediction ghost + tracers)
│   │   ├── system/                          # EntityTrackerSystem (scan interval 2-4 ticks), AreaManager, DurabilityManager
│   │   ├── tags/                            # ChaseBehavior (WASD-only, supplement path), TagSystem, SmartTaskExecutor
│   │   ├── targeting/                       # TargetManager (hybrid scoring: 0.4/0.4/0.2), TrackedTarget
│   │   └── util/                            # KeyMovementController (override/supplement/passthrough, OVERRIDE_TTL=5)
│   ├── launch/mixins/                      # MIXIN STACK (6 mixins — exact files)
│   │   ├── MixinAimAssistClient.java       # Minecraft.tick() HEAD (pre) + TAIL (post)
│   │   ├── MixinKeyboardInput.java         # KeyboardInput.tick() TAIL (primary override — replaces keyPresses + moveVector)
│   │   ├── MixinLocalPlayerInput.java      # LocalPlayer.onInput() HEAD + TAIL (secondary — catches modifyInput changes)
│   │   ├── MixinAimAssistChat.java         # Chat intercept (# / & prefixes)
│   │   ├── MixinKeyboardHandler.java       # Keyboard event interceptor
│   │   └── ClientInputAccessor.java        # @Accessor for ClientInput.moveVector (NOT @Shadow — refmap missing)
│   ├── build.gradle                        # Fabric build config (compileOnly fileTree(dir: 'libs/extracted', include: '*.jar'))
│   ├── deploy.sh                           # cp jar → /storage/emulated/0/1log/
│   ├── .github/                            # Workflows (gradle_build.yml — build verification)
│   └── ...                                 # Only code / config / build artifacts live inside shinigami/
├── .ai/                                    # CANONICAL SESSION CONTINUITY LAYER (6 folders — live cognitive state)
│   ├── architecture/core-decisions.md      # Architecture decisions + rationale + tick timeline
│   ├── architecture/README.md              # Pointer to architecture decisions
│   ├── memory/key-facts.md                 # Concrete constants (FULL_DAMAGE_THRESHOLD, JDK path, build flags, file paths)
│   ├── memory/README.md                    # Pointer to key facts
│   ├── dependency_maps/system-map.md      # Full dependency chain (Config → Controller → Arbiter → Subsystems)
│   ├── dependency_maps/README.md           # Pointer
│   ├── issues/README.md                    # Known bugs inventory (19 fixed + remaining partial)
│   ├── timelines/session-log.md           # Session history (session entry format: timestamp + actions + file paths + status)
│   ├── timelines/README.md                 # Pointer
│   ├── summaries/README.md                 # Pointer
│   ├── memory.sh                           # CLI helper (add/search/list/help)
│   └── ... (other .ai/ subfolders)
├── AGENTS.md                               # Operational contract (continuity protocol, rules, team dynamics)
├── SHINIGAMI_REFERENCE.md                  # Complete technical reference (Sections 1-18)
├── SPEC.md                                 # THIS FILE (implementation checklist — 45 verifiable items)
├── RESEARCH_FIXES.md                       # Bug research + fix reference (all 19 fixes + remaining issues)
├── CHATGPT_PROMPT.md                      # Prompt archive (context + bugs + architecture + technical constraints)
├── memory-add.md                           # Memory instructions (memory tool usage pattern)
└── ... (other parent-level .md docs — NOT inside shinigami/)
```

**Rule for file placement** (from `AGENTS.md` Section 4):
- `.java` (code) → `shinigami/fabric/src/main/java/baritone/aimassist/`
- `.gradle` / `.sh` / `.yml` → `shinigami/`
- `.md` (docs/reference) → `/root/Ws/nxt/shini/` (parent) — NOT inside `shinigami/`
- `.ai/*` (continuity) → `/root/Ws/nxt/shini/.ai/` (merged, unified)
- `build/` / `.gradle/` / `.idea/` → build artifacts; rebuildable; not source of truth

---

## Build & Deploy Checklist (`- [ ]` = must verify; `- [x]` = verified)

Every checklist item uses format: **What** / **Why** / **Where** / **Status**.

| # | Check Item | What (Concrete Action) | Why (Rationale) | Where (File / Command) | Status |
|---|---|---|---|---|---|
| 1 | JAVA_HOME set | `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-arm64` then `echo $JAVA_HOME` confirms path | Build fails with incorrect JDK; Termux requires ARM64 path | `.bashrc` or terminal before `gradlew`; `AGENTS.md` Section 10g | `- [ ]` |
| 2 | Gradlew flags exact | Run: `./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon` | Skip failing 26.1 APIs (`-x test`), skip obfuscation (`-x :fabric:proguard`), skip packaging (`-x :fabric:createDist`), prevent daemon conflicts (`--no-daemon`) | `shinigami/`; `AGENTS.md` 10g; `SPEC.md` Section Build | `- [ ]` |
| 3 | Output jar verified | Confirm `fabric/build/libs/baritone-fabric-1.17.0.jar` exists and size ≈ 2.0 MB (`ls -lh fabric/build/libs/*.jar`) | Jar is the deploy artifact; must exist before deploy | `shinigami/fabric/build/libs/`; `SHINIGAMI_REFERENCE.md` Section 1 | `- [ ]` |
| 4 | Deploy to Termux completed | `cp fabric/build/libs/baritone-fabric-1.17.0.jar /storage/emulated/0/1log/Shinigami-by-Saizo-1.17.0.jar`; verify `ls -lh /storage/emulated/0/1log/*.jar` | Deployed jar is the installed mod in Zalith Launcher | `deploy.sh` (`shinigami/deploy.sh`); `AGENTS.md` 10g | `- [ ]` |
| 5 | Quick compile passes | `./gradlew :fabric:compileJava --no-daemon 2>&1 | tail -10` returns `BUILD SUCCESSFUL` | Verifies code compiles before full build; faster iteration | `AGENTS.md` 10g; `RESEARCH_FIXES.md` (build commands) | `- [ ]` |
| 6 | Fabric API version correct | `build.gradle` references `compileOnly fileTree(dir: 'libs/extracted', include: '*.jar')`; verify `libs/extracted/` contains 42 sub-jars from `Fabric API 0.148.0+26.1.2` | Missing APIs = compile errors; `HudElementRegistry` removed `HudRenderCallback` / `WorldRenderEvents` in newer versions | `build.gradle` (`shinigami/build.gradle`); `.ai/memory/key-facts.md` (Fabric API) | `- [ ]` |
| 7 | Mappings confirmed official | `gradle.properties` or `.gradlew` uses Mojang official mappings (`1.21.1`); NO Yarn mappings | `@Shadow` requires refmap; official mappings avoid naming conflicts with `ClientInput` fields | `mapping/` directory; `SHINIGAMI_REFERENCE.md` Section 2 | `- [ ]` |
| 8 | Build artifacts cleanable | `./gradlew clean` works; `fabric/build/` and `.gradle/` can be rebuilt; `dist/` can be rebuilt | Confirms source of truth is `.java` files, not build artifacts | `build.gradle` (`clean` task); `AGENTS.md` 4 (folder rules) | `- [ ]` |

---

## Continuity Protocol Checklist (`- [ ]` / `- [x]`)

Every session MUST execute these 6 steps in order before any work.

| # | Step | What | Why | Where | Status |
|---|---|---|---|---|---|
| 1 | Read `.ai/` at session start | Open `.ai/PROJECT_STATE.md` (if present), `.ai/ACTIVE_REASONING.md`, `.ai/OPEN_LOOPS.md`, `.ai/CURRENT_OBJECTIVES.md`, `.ai/RECENT_DECISIONS.md`, `.ai/SESSION_LOG.md`; else read all `.md` in each `.ai/` subfolder | Reconstruct architecture + mental model + unfinished work + active debugging logic | `.ai/` directory (`/root/Ws/nxt/shini/.ai/`) | `- [ ]` |
| 2 | Read `AGENTS.md` | Read full `/root/Ws/nxt/shini/AGENTS.md` (continuity contract: mixin strategy, 3-layer input, CMS, prediction, dodge, Phase 3, build/deploy, quality gates) | Confirm rules + architecture reference; verify Phase 3 systems listed | `AGENTS.md` | `- [ ]` |
| 3 | Search memory | `memory({mode:"search", query:"shinigami <topic>", scope:"project"})` — SQLite DB at shared vault (`/root/Ws/boltbridge/data/memory.db`) | Cross-project context; retrieve patterns from any project | `memory` MCP server; `.ai/memory.sh` | `- [ ]` |
| 4 | Check `.ai/` folder state | Verify all 6 folders exist (`architecture/`, `memory/`, `dependency_maps/`, `issues/`, `timelines/`, `summaries/`) and contain current `.md` files | Prevent missing continuity data; missing folder = broken session state | `.ai/` tree | `- [ ]` |
| 5 | Check sync status | Verify `~/Ws/github/shini/` has current `.ai/` (with `core-decisions.md`, `key-facts.md`, `system-map.md`, `README.md` in subfolders), `.java` source, `.md` docs | Persistent remote mirror; if sync broken, fix immediately (`cp -r .ai/* ~/Ws/github/shini/.ai/`) | `~/Ws/github/shini/` (never edit directly) | `- [ ]` |
| 6 | Memory capture at session end / before compaction | `remember_fact("session-summary: ...", tags=["session","shinigami"])` saved to SQLite; also write `.ai/SESSION_LOG.md` with timestamp + actions | Preserve reasoning chain + unfinished work + architecture state for next session | `.ai/timelines/session-log.md`; `memory` MCP server | `- [ ]` |

---

## Architecture Checklist (`- [ ]` / `- [x]`)

Every architecture verification must reference exact file paths and constants.

| # | Check Item | What (Concrete Verification) | Why | Where (File / Function / Constant) | Status |
|---|---|---|---|---|---|
| 1 | Mixin injection verified — `HEAD` + `TAIL` points | Confirm `MixinAimAssistClient.java` has `@At("HEAD")` (`onPreTick()` — sets supplement + override BEFORE entity tick) and `@At("TAIL")` (`onTick()` — keybinds + module.tick() with fresh positions) | Timing critical: `HEAD` must set supplement BEFORE `KeyboardInput.tick()`; `TAIL` must run AFTER entity tick with fresh entity positions | `launch/mixins/MixinAimAssistClient.java`; `AGENTS.md` 10b | `- [ ]` |
| 2 | Mixin injection verified — `KeyboardInput` `TAIL` | Confirm `MixinKeyboardInput.java` injects at `KeyboardInput.tick()` `TAIL`; replaces both `keyPresses` (`Input` record) and `moveVector` (`Vec2`) via `ClientInputAccessor.setMoveVector()` | Primary override pipeline; must fire BEFORE `modifyInput()` and `onInput()`; uses `(left?1:0) - (right?1:0)` for strafe | `launch/mixins/MixinKeyboardInput.java`; `AGENTS.md` 10b; `.ai/architecture/core-decisions.md` (tick timeline) | `- [ ]` |
| 3 | Mixin injection verified — `LocalPlayerInput` `HEAD` + `TAIL` | Confirm `MixinLocalPlayerInput.java` injects at `LocalPlayer.onInput()` `HEAD` (replaces `keyPresses`) and `TAIL` (replaces `keyPresses` again — catches `modifyInput()` changes) | Secondary override; catches pipeline changes after primary mixin; must not miss `modifyInput()` rotation | `launch/mixins/MixinLocalPlayerInput.java`; `.ai/dependency_maps/system-map.md` (mixin layer) | `- [ ]` |
| 4 | `ClientInputAccessor` uses `@Accessor` (not `@Shadow`) | Confirm `ClientInputAccessor.java` uses `@Accessor` targeting `ClientInput.moveVector`; NO `@Shadow` annotation present | `@Shadow` requires generated `.gradle/` refmap (not present in build); `@Accessor` avoids this dependency failure | `launch/mixins/ClientInputAccessor.java`; `.ai/memory/key-facts.md` (`@Shadow` fix); `RESEARCH_FIXES.md` Bug #19 | `- [ ]` |
| 5 | Tick timeline reconstructed | Read `.ai/architecture/core-decisions.md` (tick timeline section) and verify sequence: `HEAD` → `KeyboardInput.tick()` (vanilla) → `MixinKeyboardInput.TAIL` (override) → `modifyInput()` → `LocalPlayerInput.HEAD` → `LocalPlayerInput.TAIL` → `Player.travel()` → `TAIL` module tick | Confirms no timing inversions; verifies supplement is set before entity tick and cleared at START of `tickMovement()` | `.ai/architecture/core-decisions.md` (tick timeline); `AimAssistModule.java` (`tickMovement()` method) | `- [ ]` |
| 6 | Input 3-layer architecture active | Verify `KeyMovementController.getOverriddenInput()` has 3 branches: `hasActiveOverride` (`new Input(...)` full replacement), `supplementMode` (`original || supplement` blend), else `original` (passthrough). Confirm `OVERRIDE_TTL = 5` in `KeyMovementController.java` | Confirms supplement/steering/emergency layers are functional; verifies TTL is 5 ticks (not 3) for stability; verifies `clearSupplement()` runs at START of `tickMovement()` | `util/KeyMovementController.java`; `.ai/architecture/core-decisions.md` (input architecture) | `- [ ]` |
| 7 | CMS resolver (`MovementArbiter`) applied correctly | Confirm `MovementArbiter.java` has `Priority` enum (`DODGE=100`, `CLUTCH=95`, `CRIT=70`, `CHASE=50`, `COMBAT=30`, `PARKOUR=20`, `AUTO_WALK=10`). Confirm resolver picks highest priority and applies via `ctrl.moveToward()` (`priority.value ≥ 70`) or `supplementForward()` (`< 70`) | Verifies arbitration resolves conflicts between chase, dodge, crit, parkour, combat, auto-walk; confirms no `clearSupplement()` in empty-intent path | `movement/MovementArbiter.java`; `.ai/dependency_maps/system-map.md` (CMS resolver) | `- [ ]` |
| 8 | Dependency chain intact (`Config → Mod → Controller → Arbiter → Subsystems`) | Trace: `AimAssistConfig` → `AimAssistMod` (wiring) → `KeyMovementController` (input) → `AimAssistModule` (tick) → `MovementArbiter` (resolve) → `MovementIntent` (subsystems submit). Confirm `ChaseBehavior`, `AirStrafeController`, `DodgeSystem` all submit via `arbiter.submit()`; confirm `DodgeSystem` does NOT call `ctrl.moveToward()` directly | Confirms architecture matches design; prevents direct override bugs (Bug #3 oscillation); verifies `DodgeSystem.getDodgeIntent()` submits to arbiter | `.ai/dependency_maps/system-map.md` (core chain); `tags/ChaseBehavior.java`; `movement/AirStrafeController.java`; `combat/DodgeSystem.java` | `- [ ]` |

---

## Movement Checklist (`- [ ]` / `- [x]`)

Each item references exact file paths and constants from `.ai/` and source.

| # | Check Item | What (Concrete Verification) | Why | Where (File / Constant) | Status |
|---|---|---|---|---|---|
| 1 | Supplement mode — additive OR blend active | Confirm `KeyMovementController.getOverriddenInput()` returns: `fwd = original.forward() || supplementForward`; `bwd` cleared if `fwd && bwd` conflict; `left/right` preserved from original; `jump = original.jump() || supplementJump`. Confirm NO `original.backward()` override by supplement | Confirms supplement blends (does NOT replace) player input; prevents conflict between supplement forward and original backward (`Bug #18`) | `util/KeyMovementController.java`; `.ai/architecture/core-decisions.md` (input architecture); `.ai/memory/key-facts.md` (supplement mode) | `- [ ]` |
| 2 | Steering (yaw control) — `AirStrafeController` active | Confirm `AirStrafeController.java` computes `yawDiff = wrapDegrees(targetYaw - currentVelocityYaw)`; `strafe = sign(yawDiff)`; `forward = Math.abs(yawDiff) < 90 ? 1 : 0`; submits `MovementIntent(Priority.PARKOUR, ...)` — supplement ONLY (NO `moveToward()`). Confirm NO `active=false` reset (one-shot removed) | Confirms continuous air strafe (not one-shot); uses velocity yaw (not player yaw); preserves A/D control via supplement | `movement/AirStrafeController.java`; `.ai/architecture/core-decisions.md` (air strafe); `RESEARCH_FIXES.md` Bug #8 | `- [ ]` |
| 3 | Emergency override (`< 5 ticks`) — `DodgeSystem` + `ClutchSystem` + `CritAssist` | Confirm `DodgeSystem.triggerDodge()` submits `MovementIntent(Priority.DODGE, ...)` via arbiter; does NOT call `ctrl.moveToward()` directly. Confirm `ClutchSystem.java` (water min 4, hay min 8, ladder/vine min 3; 100ms cooldown). Confirm `CritAssist.java` 4-state: STATE 0 (idle) → STATE 1 (sprint cancel, 1 tick, NO jump) → STATE 2 (jump, 1 tick) → STATE 3 (airborne, 5 ticks) → STATE 4 (crit ready). Confirm `FULL_DAMAGE_THRESHOLD = 0.848f` (`attackStrengthScale > 0.848` = full damage; `> 0.9` = crit eligible) | Confirms emergency override is arbitration-based (not direct); confirms dodge does not create oscillation loop (`Bug #3`); confirms critical hit sequence is frame-perfect (sprint cancel → jump = 2-tick sequence, never same tick) | `combat/DodgeSystem.java`; `combat/ClutchSystem.java`; `combat/CritAssist.java`; `.ai/architecture/core-decisions.md` (dodge + combat); `.ai/memory/key-facts.md` (`FULL_DAMAGE_THRESHOLD`) | `- [ ]` |
| 4 | `AirStrafeController` continuous (not one-shot) | Confirm `AirStrafeController` has NO `active=false` reset; `tick()` submits `MovementIntent` continuously while airborne; `yawDiff` uses `velocityYaw` (not `playerYaw`) | Confirms continuous strafe; prevents `Bug #8` (one-shot air control) | `movement/AirStrafeController.java`; `RESEARCH_FIXES.md` Bug #8; `.ai/dependency_maps/system-map.md` (AirStrafeController) | `- [ ]` |
| 5 | `ChaseBehavior` — WASD only (`supplementForward` path) | Confirm `ChaseBehavior.java` uses `supplementForward()` for main chase path; does NOT call `ctrl.moveToward()` (only for bridge/tower emergency); yaw oscillation `±12°`; detection: UUID → custom name → player name; `detectionRange = 64` default; parkour: obstacle (`detectOneBlockObstacle()`), gap (`detectGapAhead()` — 5 blocks ahead), bridge (`bridgeTicks = 3`), tower (`> 1.5` above target), wind burst (`60` tick cooldown), sprint (`food > 6`, `!inWater`, `!inLava`, `distance > 2`) | Confirms chase uses supplement (not override), preserving player A/D; confirms parkour features active; confirms sprint conditions match design (`food > 6`, distance `> 2`) | `tags/ChaseBehavior.java`; `.ai/architecture/core-decisions.md` (chase); `.ai/dependency_maps/system-map.md` (ChaseBehavior) | `- [ ]` |

---

## Combat Checklist (`- [ ]` / `- [x]`)

Every combat item references exact constants from `.ai/memory/key-facts.md` and `.ai/architecture/core-decisions.md`.

| # | Check Item | What (Concrete Verification) | Why | Where (Constant / File) | Status |
|---|---|---|---|---|---|
| 1 | 19 threat types detected (`DodgeSystem`) | Confirm `DodgeSystem.java` detects all 19 types in priority order: `SLOWED` → `VOID` → `FALL_DAMAGE` → `DROWNING` → `ENVIRONMENT` → `EFFECT` → `HUNGER` → `EXPLOSION` → `POTION` → `WITHER_SKULL` → `FIREBALL` → `SHULKER` → `MACE` → `CRYSTAL` → `FALLING_BLOCK` → `TRIDENT` → `PROJECTILE` → `BOW_AIM` → `MELEE` | Confirms predictive dodge covers all threat categories; no missing threat type = no unexpected damage | `combat/DodgeSystem.java`; `.ai/architecture/core-decisions.md` (dodge 19 threats) | `- [ ]` |
| 2 | Dodge never retreats (perpendicular / aggressive circle) | Confirm `DodgeSystem.getDodgeIntent()` computes direction: `perpendicular` (cross-product with Y-axis) for projectiles; `away` for explosions; `toward solid ground` for void; `upward` for drowning; `behind attacker` for melee counter. Confirm NO `retreat` direction (no backward-only dodge) | Confirms dodge philosophy: never retreat (aggressive/perpendicular evasion); prevents backward escape vulnerability | `combat/DodgeSystem.java`; `.ai/architecture/core-decisions.md` (dodge: never retreat) | `- [ ]` |
| 3 | Combo tracking (`ComboTracker`) — `< 20 ticks`, `2+` hits | Confirm `ComboTracker.java` has `comboCounter`, `lastHitTime`, `consecutiveHits`, `wTapTimer`, `sTapTimer`. Confirm combo mode threshold `50%` (vs `90%` normal); `W-tap` releases `W` for `2` ticks after hit; `S-tap` presses `S` for `1` tick after hit; distance management: `> 1.2 * optimalRange` (close aggressively), `< 0.8 * optimalRange` (create space), else `swayStrafing()` | Confirms combo mechanics (pressure, sprint reset, distance oscillation, strafe weave); verifies `W-tap` and `S-tap` timers exact | `combat/ComboTracker.java`; `.ai/architecture/core-decisions.md` (combo mechanics); `.ai/memory/key-facts.md` (combo tracking) | `- [ ]` |
| 4 | Shield break sequence (`BowAssist` + `CritAssist`) | Confirm `BowAssist.java` uses `shield + zigzag` approach (zigzag alternating), `perpendicular` dodge, `zigzag + shield` combo. Confirm `CritAssist.java` sequence: `STATE 1` (sprint cancel, `!isSprinting()`, NO jump) → `STATE 2` (jump, `jumpFromGround()` + `supplementJump()` via mixin) → `STATE 3` (airborne, 5 ticks) → `STATE 4` (crit ready, `attackStrengthScale > 0.9`). Confirm `FULL_DAMAGE_THRESHOLD = 0.848f` (`0.848` = full damage; `0.9` = crit eligibility) | Confirms shield break + critical hit timing is exact; confirms 2-tick sequence (cancel sprint → jump, NEVER same tick); verifies full damage / crit thresholds | `combat/BowAssist.java`; `combat/CritAssist.java`; `.ai/architecture/core-decisions.md` (bow approach + crit); `.ai/memory/key-facts.md` (`FULL_DAMAGE_THRESHOLD`) | `- [ ]` |
| 5 | Sprint algorithm smart (`food > 6`, release during crit window) | Confirm `ChaseBehavior.java` sprint: `food > 6`, `!inWater`, `!inLava`, `distance > 2`. Confirm `CritAssist.java` STATE 1 (`sprint = false` — sprint cancel) + STATE 2 (`jump = true`, `sprint = false`). Confirm `MovementBrain.java` `CombatFlow.PRESSURE` (`sprint = 0.8`) and `BURST` (`sprint = 1.0`) use sprint; `EVADE` (`sprint = 0.3`) reduces sprint | Confirms sprint conditions match design; verifies sprint is canceled before jump (26.1 rule: `!isSprinting()` required for crit) | `tags/ChaseBehavior.java`; `combat/CritAssist.java`; `.ai/architecture/core-decisions.md` (combat flow) | `- [ ]` |
| 6 | `W-tap` adaptive (`dist < 2`: 1 tick; `2-4`: 2 ticks; `> 4`: none) | Confirm `ComboTracker.java`: `wTapTimer = 2` (default); if `distance < 2` then `wTapTimer = 1` (short tap); if `distance > 4` then `wTapTimer = 0` (no tap); else `2` ticks. Confirm `sTapTimer = 1` (brief backward) | Confirms `W-tap` adapts to distance (closer = shorter reset; farther = no reset); verifies sprint reset timing for knockback optimization | `combat/ComboTracker.java`; `.ai/architecture/core-decisions.md` (combo mechanics); `.ai/memory/key-facts.md` (W-tap) | `- [ ]` |
| 7 | `BowAssist` approach (shield + zigzag, perpendicular, zigzag + shield) | Confirm `BowAssist.java`: `zigzag` (alternating A/D) when bow drawn; `perpendicular` dodge for projectile trajectory (cross-product); `zigzag + shield` when enemy aims bow. Confirm `BowPhysicsSolver.java` trajectory: `arrow = 3.0`, `snowball/egg = 1.5`, `potion = 0.5`, `trident = 2.5`; `simulateProjectile()` predicts `ticksAhead` position | Confirms bow approach patterns; confirms projectile physics exact; verifies zigzag breaks opponent aim prediction | `combat/BowAssist.java`; `prediction/BowPhysicsSolver.java`; `.ai/architecture/core-decisions.md` (bow assist) | `- [ ]` |
| 8 | Mace detection (`fallDistance > minSmashHeight`) | Confirm `MaceAssist.java`: `minSmashHeight = 2.0`; smash triggers when `fallDistance > 2.0` + `mace` in hand (`mainHand` or `offHand`) + `maceMode` active (`critOnly` / `comboOnly` / `both` / `normal` + `witch/heal` override). Confirm `maceMode` config: `false` default; `true` enables assist | Confirms mace smash detection; verifies mode separation (crit/combo/both/normal); confirms witch/heal override (if potion effect detected, override mace with heal) | `combat/MaceAssist.java`; `.ai/architecture/core-decisions.md` (mace assist); `.ai/memory/key-facts.md` (`maceMode`) | `- [ ]` |
| 9 | Wind burst (`windBurstTracking`) | Confirm `WindBurstAssist.java` (`combat/WindBurstAssist.java`): `windBurstTracking = true` (default); `windBurstCooldown = 60` ticks; activates when vertical mobility needed (`targetY - playerY > 1.5`) + `windCharge` item available | Confirms wind burst tracking and cooldown exact; verifies vertical boost activation conditions | `combat/WindBurstAssist.java`; `.ai/architecture/core-decisions.md` (wind burst); `.ai/dependency_maps/system-map.md` | `- [ ]` |
| 10 | Crit assist — 1-tick burst (`sprint cancel` + `jump` same tick via `requestCrit()`) | Confirm `CritAssist.java`: `STATE 1` sets `sprint = false`; `STATE 2` calls `jumpFromGround()` + `supplementJump()` in same tick (NOT separate ticks). Confirm `requestCrit()` method called from `AimAssistModule.tick()`; `airTicks` starts at `0`; `STATE 4` reached after `5` ticks in air (`airTicks >= 5`) | Confirms 1-tick sprint cancel + jump (same tick = burst); verifies 5-tick airborne before crit ready; confirms `FULL_DAMAGE_THRESHOLD = 0.848f` used for full damage calculation (not just crit eligibility) | `combat/CritAssist.java`; `.ai/architecture/core-decisions.md` (crit assist); `.ai/memory/key-facts.md` (`FULL_DAMAGE_THRESHOLD`, `OVERRIDE_TTL`) | `- [ ]` |

---

## Prediction Checklist (`- [ ]` / `- [x]`)

| # | Check Item | What (Concrete Verification) | Why | Where (Constant / File) | Status |
|---|---|---|---|---|---|
| 1 | Physics — closed-form `O(1)` per tick | Confirm `MovementPredictor.java`: `predictPosition()` uses `friction = onGround ? 0.91f : 0.98f`; `gravity = 0.08f` per tick; iterates `ticksAhead` steps with `velocity = velocity.scale(friction); velocity = velocity.add(0, -0.08, 0)`; position updated by `pos = pos.add(velocity)` per tick. Confirm `O(1)` per tick (constant operations per tick, independent of `ticksAhead` complexity — but total `O(ticksAhead)`) | Confirms physics prediction is iterative (not matrix inversion); verifies friction/gravity constants match Minecraft 1.21.1 movement formulas (`travel()` formula: `dx = (strafe*cos - forward*sin)*speed`, `dz = (strafe*sin + forward*cos)*speed`) | `prediction/MovementPredictor.java`; `.ai/architecture/core-decisions.md` (prediction); `.ai/memory/key-facts.md` (physics constants) | `- [ ]` |
| 2 | Iterative simulation — 5-40 ticks ahead | Confirm `MovementPredictor.java` accepts `ticksAhead` parameter (`predictPosition(int ticksAhead)`); `BehavioralPredictor.predictPosition()` simulates `ticksAhead` steps; `JumpArcPredictor.java` predicts vertical arc for `ticksAhead` when `wasJumping && velocity.y > 0.05`. Confirm default `predictionTicks = 2` (`AimAssistConfig.java`) but `predictPosition()` can accept up to `40` ticks ahead (for long-range bow prediction) | Confirms prediction range is configurable; verifies 5-40 tick simulation covers melee (`0-2` ticks) to bow (`20-40` ticks for long-range projectile trajectory) | `prediction/MovementPredictor.java`; `prediction/JumpArcPredictor.java`; `AimAssistConfig.java` (`predictionTicks`) | `- [ ]` |
| 3 | Behavioral classifier — 6 patterns (`CIRCLE_STRAFER`, `AGGRESSIVE_STRAFER`, `JUMPER`, `LINEAR_CHASER`, `PANIC_RUNNER`, `UNKNOWN`) | Confirm `BehavioralPredictor.java`: `enum Pattern` has all 6 values. Confirm classification rules: `CIRCLE_STRAFER` = `yawRange > 80°` + `strafeChanges > 5`; `AGGRESSIVE_STRAFER` = `yawRange > 40°` + `strafeChanges > 3`; `JUMPER` = `jumpRatio > 0.30`; `LINEAR_CHASER` = `sprintRatio > 0.70` + `yawRange < 20°`; `PANIC_RUNNER` = `sprintRatio < 0.30`; else `UNKNOWN`. Confirm `hasHistory(int entityId)` checks `historyLength >= 20` ticks (minimum data for classification) | Confirms all 6 patterns implemented; verifies classification thresholds exact; confirms `UNKNOWN` fallback when insufficient data (`historyLength < 20`) | `prediction/BehavioralPredictor.java`; `.ai/architecture/core-decisions.md` (behavioral); `.ai/memory/key-facts.md` (6 patterns) | `- [ ]` |
| 4 | Kalman filter — 9D state vector `[x, vx, ax, y, vy, ay, z, vz, az]` with `Q`/`R` tuning | Confirm `KalmanFilter.java` (`prediction/KalmanFilter.java` — Phase 3 file): uses `org.apache.commons.math3.filter.KalmanFilter`; state vector size = `9`; transition matrix (9×9); measurement matrix (3×9 — measures position only, not velocity/acceleration); process noise `Q` tuned per entity type (`Player`, `Hostile`, `Passive`); measurement noise `R` tuned per sensor (position `R = 0.5`, velocity estimated `R = 1.0`, acceleration estimated `R = 2.0`). Confirm `predict()` (predict next state) + `correct()` (update with new measurement) cycle runs every tick | Confirms 9D state covers position + velocity + acceleration (full dynamics); verifies `Q`/`R` tuning allows different noise models per entity (e.g., hostile mobs have higher acceleration noise = less predictable) | `prediction/KalmanFilter.java` (Phase 3); `.ai/architecture/core-decisions.md` (Kalman); `.ai/memory/key-facts.md` (9D vector, `Q`/`R`) | `- [ ]` |

---

## Quality Gates Checklist (`- [ ]` / `- [x]`)

Every quality gate must pass before any work is declared complete. Format: **What / Why / Where / Status**.

| # | Gate | What (Concrete Action) | Why (Rationale) | Where (File / Command / Reference) | Status |
|---|---|---|---|---|---|
| 1 | Compile check (`compileJava`) | Run `./gradlew :fabric:compileJava --no-daemon 2>&1 | tail -10`; confirm `BUILD SUCCESSFUL` and zero `ERROR` lines | Confirms syntax + imports correct; faster than full build; catches `@Shadow` failures early | `gradlew` command; `.ai/memory/key-facts.md` (build); `AGENTS.md` 10i | `- [ ]` |
| 2 | Build check (full `fabric:build`) | Run `./gradlew :fabric:build -x test -x :fabric:proguard -x :fabric:createDist --no-daemon`; confirm `fabric/build/libs/baritone-fabric-1.17.0.jar` exists; confirm jar size ≈ 2.0 MB (`ls -lh`) | Confirms full build pipeline works (mixin compilation + resource processing + manifest); verifies jar deployable | `gradlew` command; `fabric/build/libs/` output; `.ai/memory/key-facts.md` (output jar) | `- [ ]` |
| 3 | `.ai/` verification — `.ai/` reflects change | Read `.ai/ACTIVE_REASONING.md` (current reasoning chain), `.ai/OPEN_LOOPS.md` (unresolved bugs / unfinished work), `.ai/CURRENT_OBJECTIVES.md` (immediate priorities), `.ai/RECENT_DECISIONS.md` (architecture choices + tradeoffs + rejected alternatives), `.ai/SESSION_LOG.md` (timestamped actions). Confirm each file has `# Title — Last Updated: <date>` and entries with `What / Why / Where (file path) / Status`. Confirm `.ai/memory/key-facts.md` has concrete constants updated (`FULL_DAMAGE_THRESHOLD`, file paths, build flags) | Confirms continuity layer captures the change; verifies session state preserved; prevents context loss across sessions / compactions / crashes | `.ai/` directory (all 6 subfolders); `.ai/ACTIVE_REASONING.md`; `.ai/OPEN_LOOPS.md`; `.ai/CURRENT_OBJECTIVES.md`; `.ai/RECENT_DECISIONS.md`; `.ai/SESSION_LOG.md`; `.ai/memory/key-facts.md` | `- [ ]` |
| 4 | Architecture review (`dependency_maps/`) | Read `.ai/dependency_maps/system-map.md` and verify chain: `Config → AimAssistMod → KeyMovementController → AimAssistModule → MovementArbiter → Subsystems (Chase, Dodge, Crit, AirStrafe, etc.)`. Confirm `DodgeSystem` submits via arbiter (not direct `moveToward()`); confirm `ChaseBehavior` uses `supplementForward()`; confirm `ClientInputAccessor` uses `@Accessor` | Confirms architecture not broken by change; verifies mixin strategy intact; verifies CMS resolution correct; confirms no silent dependency break | `.ai/dependency_maps/system-map.md`; `launch/mixins/ClientInputAccessor.java`; `tags/ChaseBehavior.java`; `movement/MovementArbiter.java`; `combat/DodgeSystem.java` | `- [ ]` |
| 5 | Documentation update (`AGENTS.md` + `SPEC.md`) | Confirm `AGENTS.md` references all 8 Phase 3 systems (`KalmanFilter`, `PredictionIntegration`, `ActionBufferSystem`, `CombatRhythmEngine`, `MovementGraph`, `MovementArbiter` [enhanced], `RecoveryPlanner`, `DodgeSystem` [upgrade]). Confirm `SPEC.md` has all 45 checklist items with concrete actions (`- [ ]` format), exact file paths, exact constants (`FULL_DAMAGE_THRESHOLD=0.848f`, `OVERRIDE_TTL=5`, `SCANG_RADIUS=8`, `minSmashHeight=2.0`, `detectionRange=64`, build flags exact, deploy path exact). Confirm `AGENTS.md` Section 10f lists all 8 Phase 3 systems with file paths (`prediction/KalmanFilter.java`, etc.) | Confirms documentation captures architecture + implementation plan; verifies Phase 3 systems tracked; confirms spec is verifiable (not abstract) | `AGENTS.md` (Section 10f); `SPEC.md` (all checklist sections); `.ai/architecture/core-decisions.md` (Phase 3) | `- [ ]` |
| 6 | Sync check (`~/Ws/github/shini/` current) | Confirm `~/Ws/github/shini/.ai/` exists and contains current `.ai/` files (`core-decisions.md` with tick timeline, `key-facts.md` with constants, `system-map.md` with dependency chain, `README.md` in subfolders). Confirm `~/Ws/github/shini/shinigami/` exists and has current code. Confirm sync flow executed: `cp -r /root/Ws/nxt/shini/shinigami/* ~/Ws/github/shini/shinigami/` + `cp -r /root/Ws/nxt/shini/.ai/* ~/Ws/github/shini/.ai/` + `cp /root/Ws/nxt/shini/*.md ~/Ws/github/shini/` (except `AGENTS.md` if must stay live) | Confirms persistent remote mirror has continuity data; verifies `.ai/` folder (continuity layer) preserved in mirror; prevents data loss if local `.ai/` corrupted | `~/Ws/github/shini/`; sync commands (`cp -r`); `.ai/` verification (`ls ~/Ws/github/shini/.ai/`); `AGENTS.md` Section 6 (sync protocol) | `- [ ]` |

---

## Cross-Project Awareness Note (`- [ ]` / `- [x]` — Reference Check)

| Check | What | Why | Where | Status |
|---|---|---|---|---|
| Cross-project pattern saved | If `KalmanFilter` (9D), `MovementIntent` bus, `DodgeSystem` predictive, or `CombatFlow` state machine patterns from `shinigami` apply to another project, save: `remember_fact("pattern: ... from shinigami applies to <other_project>", tags=["pattern","shinigami","<other_project>"])` | Preserves cross-project learning in shared SQLite DB; allows pattern reuse | `memory` MCP server (`/root/Ws/boltbridge/data/memory.db`); `.ai/architecture/core-decisions.md` (Phase 3); `AGENTS.md` Section 12 (cross-project awareness) | `- [ ]` |

---

## Final Verification (Before Declaring Complete)

| Step | Action | Confirm By Reading Back |
|---|---|---|
| A | Read `SPEC.md` first 20 lines | Confirm title (`SHINIGAMI — Implementation Plan / Spec`), last updated (`2026-05-14`), `Master Plan Architect` tag |
| B | Read `SPEC.md` last 10 lines | Confirm checklist count (`48` verifiable checklist items: `8` Build + `6` Continuity + `8` Architecture + `5` Movement + `10` Combat + `4` Prediction + `6` Quality Gates + `1` Cross-Project Awareness = `48`); confirm `Status` column present (`- [ ]` format); confirm exact file paths and constants (`FULL_DAMAGE_THRESHOLD=0.848f`, `OVERRIDE_TTL=5`, `SCANG_RADIUS=8`, `minSmashHeight=2.0`, `detectionRange=64`, build flags exact, deploy path exact). Confirm `AGENTS.md` Section 10f lists all 8 Phase 3 systems with file paths (`prediction/KalmanFilter.java`, etc.) |
| C | Confirm `AGENTS.md` references all 8 Phase 3 systems | Search `AGENTS.md` for: `KalmanFilter`, `PredictionIntegration`, `ActionBufferSystem`, `CombatRhythmEngine`, `MovementGraph`, `MovementArbiter`, `RecoveryPlanner`, `DodgeSystem`. Confirm Section 10f lists all 8 with file paths (`prediction/KalmanFilter.java`, etc.) and descriptions |
| D | Confirm `.ai/` files updated | Read `.ai/architecture/core-decisions.md` (first 20 lines: `# Core Architecture Decisions` + tick timeline), `.ai/memory/key-facts.md` (last 10 lines: constants list + build commands), `.ai/dependency_maps/system-map.md` (core dependency chain), `.ai/issues/README.md` (bug inventory table + remaining issues), `.ai/timelines/session-log.md` (new session entry `2026-05-14`) |
| E | Confirm NO `.java` modified | `find shinigami/fabric/src/main/java/baritone/aimassist/ -newer /root/Ws/nxt/shini/AGENTS.md` should return nothing (no newer `.java` files than `AGENTS.md` update time = no `.java` edited in this session) |
| F | Confirm NO `git` executed | `git status` (if run accidentally) should show no changes in `/`; but per protocol, `git` is NOT executed in `/` (only in `~/Ws/github/shini/` if sync performed) |

---

*This spec is complete when all 48 checklist items are verified (`- [x]`), when `AGENTS.md` references all 8 Phase 3 systems, when `.ai/` reflects all updates, when `SPEC.md` confirms 45 items with exact paths/constants, and when `git` shows no unexpected `.java` changes. [—Ocey]*
