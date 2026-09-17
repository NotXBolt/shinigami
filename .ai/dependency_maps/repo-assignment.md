# Repo Assignment — What Each Repo Is Best At (Shinigami)

| Repo | Best At | Used For in Shinigami | Not Used For |
|---|---|---|---|
| `cabaletta/baritone` | **Pathfinding** A* voxel, `CalculationContext`, `Movement`, `PathExecutor`, caching `CachedWorld`, `IPlayerContext` | `src/api`, `src/main/java/baritone/pathing/`, `baritone/cache/` — kept as pathfinding core, `GoalBlock`/`GoalNear` etc | Parkour gap jumps, dodge, sprint persistence (use Parkour/Oogabooga instead) |
| `stylextv/maple` | **Continuous path** `0.5` step, `gScore+heuristic`, `requiresMining +4.0` | Adapted into `ParkourEnforcer.calculateDir` Theta* any-angle + `getBlockCost` continuous sampling (inspiration, not copy) | Block breaking (use Baritone `MovementHelper` + `equipBestTool`) |
| `0x1bd/Kiwi` | **Theta* any-angle** (`GoalXYZ`/`GoalNear`) not 0/45, high-performance `26.1.2` | `ParkourEnforcer.calculateDir` `Vec3.normalize` any-angle, `TargetManager` `isInFOV 360` | - |
| `Leg0shii/ParkourCalculator` | **Parkour physics** `0.91/0.98/0.08` `JumpArcPredictor`, 1.8.9/1.12/1.20 simulation | `ChaseBehavior` `jump = gap||edge||blocked||oneBlock||targetAbove` (no air spam), `detectGap` `5` `detectEdge` `0.35` | - |
| `Fesuoy1/better-auto-jump` | **Edge jumping** `Sprint Distance 2.0` `Walk 0.3` `Step 0.35` `Min Velocity 0.1` `Solid 0.001-0.6` + sprint persistence | `ChaseBehavior.detectEdgeAhead()` `0.3-2.0` `0.35` `speed>0.1` `height<0.001`, `shouldSprint` `food>6` | - |
| `LucasErrNotFound/Oogabooga` | **Relentless pursuit** always sprinting, terrain-aware sprint-jump, `2-4` gap jumps at edge, pillar/bridge, reactive digging | `ChaseBehavior` `sprint` `food>6 && dist>2` + `ParkourEnforcer.shouldSprint` + `breakBlockInFront` `12` ticks stuck | - |
| `RikkTheGaijin/Dodger` | **Dodge** projectile `perp` minimal `1-block` `isSafeDodgePosition` `LAVA/FIRE/CACTUS/MAGMA` void `solid below` | `DodgeSystem.perpToTrajectory` + `isSafeDodgePosition` tries opposite if unsafe, `DODGE 100` absolute | Melee (use `aggressive circle` `toward+perp*0.7`) |
| `TRC-AI/cadence` | **Block costs** `soul sand 2.5` `honey 3.0` `slime 1.8` `dripleaf 2.0`, diagonal `A*` | `ParkourEnforcer.blockCost()` `getBlockCost()` | - |
| `AngelFireLA/ParkourSolverMod` | **Beam-search solver** checkpoints, TAS replay | Documented for `MovementGraph` `SCANG_RADIUS 8` `A*` combat scoring Phase 3 | - |
| `Stepan1411/pvp-bot-fabric` | **Combat** windburst `mace` `shield break` `crit fallTicks` `bow prediction` | Adapted to `MaceAssist` `AutoCombatSwitch` `CritAssist` `BowAssist` Phase 1 (not Phase 0) | - |
| `Smartouspeak/reflex-client-download` | **Crit** `Criticals` micro-hops `fallTicks` | `CritAssist` `critFallTicks` `micro-hop` — toggle `Crit` actually enables `CritAssist` `1-tick burst` `0.848` | - |
| `Stepan1411/pvp-bot-fabric` (combo) | **Combo** hit-chain `2+ in 20 ticks` | `ComboTracker` `hit-chain` `W-tap` — toggle `Combo` actually enables `ComboTracker` | - |
| `Stepan1411/pvp-bot-fabric` (mace) | **Mace** `windBurst` `mace` `shield break` | `MaceAssist` `minSmashHeight 2.0` `windBurstTracking` — toggle `Mace` actually enables `MaceAssist` | - |
| `Stepan1411/pvp-bot-fabric` (bow) + `RikkTheGaijin/Dodger` | **Bow** `ranged` `bow prediction` + `projectile` dodge | `BowAssist` `BowPhysicsSolver` — toggle `Bow` actually enables `BowAssist` | - |

**Rule:** `Baritone` stays `pathfinding` core (`baritone.pathing`, `baritone.cache`, `baritone.utils`), `shinigami` `ParkourEnforcer`/`ChaseBehavior`/`DodgeSystem` use **appropriate repos** for parkour/dodge/aggressive, not Baritone's `MovementParkour` etc. All ports rewritten as `shinigami.*` original, `MovementArbiter` `DODGE 100` + `supplementForward` unified. **Toggles actually useful:** `Crit` → `CritAssist` (reflex), `Combo` → `ComboTracker` (pvp-bot), `Mace` → `MaceAssist` (pvp-bot windburst), `Bow` → `BowAssist` (pvp-bot ranged) — each toggle controls distinct repo-backed implementation, not redundant.

---
## v1.0.5 — All 22 repos present + gated respectively (2026-09-09)
| Repo dir | Integrated class | Priority / Gate | Real connection |
|---|---|---|---|
| Dodger | integrated/dodger/ShinigamiDodger | DODGE / autoDodge | Projectile perp logic merged into DodgeSystem; class gated submit |
| Kiwi | integrated/kiwi/ShinigamiKiwi | PARKOUR / chaseMode+name | Any-angle dir, GoalXYZ spirit |
| Oogabooga | integrated/oogabooga/ShinigamiOogabooga | CHASE / chaseMode+movement | Relentless sprint, merged into ChaseBehavior/ParkourEnforcer.shouldSprint |
| ParkourCalculator | integrated/parkourcalculator/ShinigamiParkourCalculator | PARKOUR / moving+airborne | Physics 0.91/0.98/0.08 |
| ParkourCalculatorMod | integrated/parkourcalculatormod/ShinigamiParkourCalculatorMod | PARKOUR / chaseMode | Checkpoint bridge |
| better-auto-jump | integrated/betterautojump/ShinigamiBetterAutoJump | PARKOUR / movement+onGround | Edge 0.3-2.0 step 0.35 |
| cadence | integrated/cadence/ShinigamiCadence | PARKOUR / chaseMode | Diagonal A* + block costs |
| cosmos | integrated/cosmos/ShinigamiCosmos | COMBAT no-submit / pvpMode | Crystal merged into combat, no auto-place grief |
| Stonecraft | integrated/stonecraft/ShinigamiStonecraft | PARKOUR / chaseMode | Baritone-fork executor |
| huntress-hacked-client | integrated/huntress/ShinigamiHuntress | COMBAT no-submit / pvpMode | ESP info only, no move |
| maple | integrated/maple/ShinigamiMaple | PARKOUR / chaseMode | Continuous 0.5, gScore+heuristic |
| mineflayer-pathfinder | integrated/mineflayerpathfinder/ShinigamiMineflayerPathfinder | PARKOUR / chaseMode | JS A* goals port |
| pvp-bot-fabric | integrated/pvpbotfabric/ShinigamiPvpBotFabric | COMBAT / pvpMode | Windburst/crit/bow, merged into MaceAssist/CritAssist/BowAssist |
| Minecraft-PVP-bot | integrated/minecraftpvpbot/ShinigamiMinecraftPvpBot | COMBAT / pvpMode | Python RL port (logic only) |
| meinbot | integrated/meinbot/ShinigamiMeinbot | COMBAT no-submit | JS automation, no spam |
| fabric | integrated/fabric/ShinigamiFabric | NONE infra | Build/mappings layer, no tick |
| yarn | integrated/yarn/ShinigamiYarn | NONE infra | Mappings, no tick |
| FabricAutoClicker (ImadSaddik) | integrated/fabricautoclicker/ShinigamiFabricAutoClicker | COMBAT no-submit | Cooldown-aware swing, merged into Crit timing |
| MaceBot (katch0420) | integrated/macebot/ShinigamiMacebot | COMBAT / maceMode+mace held | Mace smash, merged into MaceAssist |
| blockfighter (Lumitani) | integrated/blockfighter/ShinigamiBlockfighter | COMBAT / pvpMode | Style switch sword/axe/mace/crystal |
| unionclef (3ndetz) | integrated/unionclef/ShinigamiUnionclef | PARKOUR / chaseMode | Shredder v2 + tungsten no-break A* |
| EnthusiaAutoClicker (wsg138) | integrated/enthusiaautoclicker/ShinigamiEnthusiaAutoClicker | COMBAT no-submit | Rate-limited click, drives keys |

All wired via integrated/IntegrationRegistry.tickAll called in ShinigamiMod.onPreClientTick before arbiter.apply. Single mod, no fragment — GUI unified (ShinigamiScreen 4 tabs + per-setting descFor), R/G via ShinigamiKeybinds GLFW polling.
