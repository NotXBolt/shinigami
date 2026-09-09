package shinigami;

import baritone.api.aimassist.IAimAssist;
import baritone.api.aimassist.IAimConfig;
import baritone.api.aimassist.IAimTarget;
import baritone.api.utils.Rotation;
import shinigami.aim.AimController;
import shinigami.combat.*;
import shinigami.targeting.TargetManager;
import shinigami.prediction.MovementPredictor;
import shinigami.tags.TagSystem;
import shinigami.tags.ChaseBehavior;
import shinigami.tags.SmartTaskExecutor;
import shinigami.util.KeyMovementController;
import shinigami.system.*;
import shinigami.movement.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class AimAssistModule implements IAimAssist {

    private static AimAssistModule INSTANCE;
    private final Minecraft mc = Minecraft.getInstance();

    private final AimAssistConfig config = AimAssistConfig.getInstance();
    private final TargetManager targetManager = new TargetManager(config);
    private final AimController aimController = new AimController();
    private final TriggerBot triggerBot = new TriggerBot(config);
    private final DodgeSystem dodgeSystem = new DodgeSystem(config);
    private final MaceAssist maceAssist = new MaceAssist(config);
    private final ComboTracker comboTracker = new ComboTracker();
    private final BowAssist bowAssist = new BowAssist(config);
    private final BridgeAssist bridgeAssist = new BridgeAssist(config);
    private final CritAssist critAssist = new CritAssist(config);
    private final CombatSituationHandler combatSituationHandler = new CombatSituationHandler();
    private final OpponentScanner opponentScanner = new OpponentScanner();
    private final ScreenControl screenControl = new ScreenControl(this);
    private final ClutchSystem clutchSystem = new ClutchSystem(config);
    private final WindBurstAssist windBurstAssist = new WindBurstAssist(config);
    private final TagSystem tagSystem = new TagSystem(this);
    private final ChaseBehavior chaseBehavior = new ChaseBehavior(this);
    private final SmartTaskExecutor smartTaskExecutor = new SmartTaskExecutor(this);
    private final AutoInventory autoInventory = new AutoInventory(config);
    private final AutoCombatSwitch autoCombatSwitch = new AutoCombatSwitch(this, config);
    private final CombatPeripherals combatPeripherals = new CombatPeripherals(config);
    private final AutoTotem autoTotem = new AutoTotem();
    private final AutoUtil autoUtil = new AutoUtil(config);
    private final DurabilityManager durabilityManager = new DurabilityManager(config);
    private final AreaManager areaManager = new AreaManager(config);
    private final UnderwaterBreathing underwaterBreathing = new UnderwaterBreathing(config);
    private final PortalManager portalManager = new PortalManager(this, config);

    // ─── Next-Gen Systems ───
    private final EntityTrackerSystem entityTracker = new EntityTrackerSystem();
    private final MovementArbiter movementArbiter = new MovementArbiter();
    private final MovementBrain movementBrain = new MovementBrain();
    private final TerrainAnalyzer terrainAnalyzer = new TerrainAnalyzer();
    private final AirStrafeController airStrafeController = new AirStrafeController();

    private boolean enabled = false;
    private Mode currentMode = Mode.DEMON;
    private int aimMode = 0;

    private boolean chaseMode = false;
    private boolean fleeMode = false;
    private boolean farmMode = false;
    private String chaseTargetName;
    private boolean chaseKill = false;

    public static AimAssistModule getInstance() {
        if (INSTANCE == null) INSTANCE = new AimAssistModule();
        return INSTANCE;
    }

    private AimAssistModule() {}

    // ═══════════════════════════════════════════════════
    //  tickMovement() — Called at HEAD of Minecraft.tick()
    //  Sets up movement intents for the coming entity tick
    // ═══════════════════════════════════════════════════

    public void tickMovement() {
        if (mc.player == null || mc.level == null) return;
        AimAssistMod aimMod = AimAssistMod.getInstance();
        KeyMovementController ctrl = aimMod != null ? aimMod.getMovementController() : null;

        movementArbiter.clear();
        if (ctrl != null) ctrl.clearSupplement();

        // DODGE runs FIRST — directly controls movement, bypasses arbiter
        dodgeSystem.tick();
        if (dodgeSystem.hasActiveDodge()) {
            // If dodge is active, skip arbiter — dodge already called ctrl.moveToward()
            return;
        }

        // Update air strafe controller with target yaw
        if (chaseMode && chaseBehavior.hasTarget()) {
            var target = chaseBehavior.getCurrentTarget();
            if (target != null) {
                Vec3 diff = target.position().subtract(mc.player.position());
                float targetYaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
                airStrafeController.setTargetYaw(targetYaw);
            }
        }

        // CHASE: submits MovementIntent via arbiter (uses supplement)
        boolean chaseActive = chaseMode && chaseBehavior.hasTarget();
        if (chaseActive) {
            chaseBehavior.tick();
        } else if (config.isMovementMode() && config.isEnabled()) {
            if (ctrl != null) {
                movementArbiter.submit(new MovementIntent(
                    MovementIntent.Priority.AUTO_WALK,
                    new Vec3(0, 0, 1), mc.player.getFoodData().getFoodLevel() > 6,
                    MovementIntent.JumpType.NONE, false, 2, "auto-walk"
                ));
            }
        }

        // COMBO: submit combo movement intents (W-tap + strafe oscillation)
        if (config.isComboMode() && comboTracker.isInCombo() && comboTracker.getCombatIntent() != null) {
            movementArbiter.submit(comboTracker.getCombatIntent());
        }

        // AIR STRAFE: continuous
        if (!mc.player.onGround()) {
            MovementIntent strafe = airStrafeController.getStrafeIntent();
            if (strafe != null) movementArbiter.submit(strafe);
        }

        movementArbiter.apply(ctrl);
    }

    // ═══════════════════════════════════════════════════
    //  tick() — Called at TAIL of Minecraft.tick()
    //  Full targeting, aim, attack, combat systems
    // ═══════════════════════════════════════════════════

    public void tick() {
        try {
            if (mc.player == null || mc.level == null) return;

            entityTracker.tick();
            movementBrain.tick();
            tagSystem.processTags();

            AimAssistMod aimMod = AimAssistMod.getInstance();
            KeyMovementController ctrl = aimMod != null ? aimMod.getMovementController() : null;

            tickMovement();

            if (farmMode) autoFarmMode();
            if (fleeMode) handleFleeMode();

            clutchSystem.tick();
            smartTaskExecutor.tick();
            autoInventory.tick();
            durabilityManager.tick();
            underwaterBreathing.tick();

            if (!enabled) {
                mc.options.keyAttack.setDown(false);
                mc.options.keyUse.setDown(false);
                return;
            }

            autoCombatSwitch.tick();
            autoTotem.tick();
            autoUtil.autoSprint(targetManager.getPrimaryTarget() != null);
            autoUtil.shieldAssist();

            targetManager.tick();
            IAimTarget target = targetManager.getPrimaryTarget();

            if (target != null && target.getEntity() instanceof LivingEntity living) {
                aimController.updatePredictions(living);
                combatSituationHandler.tick(living);
                opponentScanner.scan(living);
            }

            boolean anyCombatMode = config.isAutoMode() || config.isPvpMode() || config.isMaceMode()
                || config.isCritMode() || config.isComboMode();
            boolean useAttackFlow = anyCombatMode && !(chaseMode && chaseBehavior.hasTarget() && chaseBehavior.isChaseKill());

            critAssist.setActive(config.isCritMode());
            comboTracker.setActive(config.isComboMode());
            maceAssist.setActive(config.isMaceMode());
            bowAssist.setActive(config.isBowMode());
            bridgeAssist.setActive(config.isBridgeMode());
            windBurstAssist.setActive(config.isMaceMode());

            if (currentMode == Mode.EZ) {
                aimController.setNoiseLevel(config.getRandomization());
                aimController.setSilentAim(config.isSilentAim());
            } else {
                aimController.setNoiseLevel(0);
                aimController.setSilentAim(false);
            }
            aimController.setAimSpeed(config.getAimSpeed());

            if (useAttackFlow) {
                combatPeripherals.crystalAssist();
                combatPeripherals.autoGear();
                combatPeripherals.autoLoot();
                combatPeripherals.autoSmelt();
            }

            if (config.isCritMode()) critAssist.tick();
            if (config.isMaceMode()) {
                maceAssist.tick();
                windBurstAssist.tick();
            }
            if (config.isBowMode()) bowAssist.tick();
            if (config.isBridgeMode()) bridgeAssist.tick();

            if (screenControl.shouldBlockAim()) return;

            // ─── Bow aim override ───
            boolean isBowDrawn = bowAssist.isDrawing() && bowAssist.isActive();
            if (isBowDrawn && target != null && target.getEntity() instanceof LivingEntity living) {
                Rotation bowRot = bowAssist.getBowAimOverride(living);
                if (bowRot != null) {
                    if (config.isSilentAim() && currentMode == Mode.EZ) {
                        baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                            .getLookBehavior().updateTarget(bowRot, false);
                    } else {
                        mc.player.setYRot(bowRot.getYaw());
                        mc.player.setXRot(bowRot.getPitch());
                    }
                }
            } else if (target != null) {
                handleNormalAim(target);
            }

            // ─── Attack flow ───
            if (useAttackFlow && target != null && target.getEntity() instanceof LivingEntity living) {
                double dist = mc.player.distanceTo(living);
                float str = mc.player.getAttackStrengthScale(0.5f);
                boolean inRange = dist <= config.getRange();
                boolean fullDamage = str >= FULL_DAMAGE_THRESHOLD;
                boolean critMode = config.isCritMode();
                boolean comboMode = config.isComboMode();

                if (wtapCooldown > 0) wtapCooldown--;

                // Detect entity healing (witch drinking potion)
                if (living.isUsingItem() && lastAttackTarget == living) {
                    entityHealTicks++;
                } else if (lastAttackTarget != living) {
                    entityHealTicks = 0;
                }
                lastAttackTarget = living;

                boolean attackThisTick = false;

                // ─── MACE MODE (highest priority) ───
                if (config.isMaceMode() && maceAssist.shouldSmash(living)) {
                    attackThisTick = true;
                    maceAssist.onSmash();
                }
                // ─── CRIT MODE (only crit, no combo) ───
                else if (critMode && !comboMode && fullDamage && inRange && mc.player.attackable()) {
                    if (critAssist.shouldCrit(living)) {
                        attackThisTick = true;
                    } else if (mc.player.onGround() && mc.player.getAttackStrengthScale(0.0f) >= 0.9f) {
                        critAssist.requestCrit(living);
                    } else if (critAssist.isCritReady()) {
                        attackThisTick = true;
                    }
                }
                // ─── COMBO MODE (only combo, NO crit) ───
                else if (comboMode && !critMode && fullDamage && inRange && mc.player.attackable()) {
                    Vec3 upAim = aimController.getAimPointForMobType(living);
                    var rot = aimController.calculateRotation(upAim);
                    mc.player.setYRot(rot.getYaw());
                    mc.player.setXRot(rot.getPitch());
                    attackThisTick = true;
                    if (comboTracker.isInCombo()) {
                        wtapCooldown = 2;
                        if (ctrl != null) ctrl.supplementForward(false);
                    }
                }
                // ─── CRIT + COMBO (both active) ───
                else if (critMode && comboMode && fullDamage && inRange && mc.player.attackable()) {
                    Vec3 upAim = aimController.getAimPointForMobType(living);
                    var rot = aimController.calculateRotation(upAim);
                    mc.player.setYRot(rot.getYaw());
                    mc.player.setXRot(rot.getPitch());
                    if (critAssist.shouldCrit(living)) {
                        attackThisTick = true;
                    } else if (mc.player.onGround() && mc.player.getAttackStrengthScale(0.0f) >= 0.9f) {
                        critAssist.requestCrit(living);
                    } else if (critAssist.isCritReady()) {
                        attackThisTick = true;
                    } else {
                        attackThisTick = true;
                    }
                    if (comboTracker.isInCombo()) {
                        wtapCooldown = 2;
                        if (ctrl != null) ctrl.supplementForward(false);
                    }
                }
                // ─── NORMAL MODE (no crit, no combo) ───
                else if (fullDamage && inRange && mc.player.attackable()) {
                    attackThisTick = true;
                }

                // Override: always attack if entity is healing (witch drinking potion)
                if (!attackThisTick && entityHealTicks > 0 && entityHealTicks < 30) {
                    if (mc.player.getAttackStrengthScale(0.5f) >= 0.5f && inRange) {
                        attackThisTick = true;
                    }
                }
                CombatSituationHandler.CombatStrategy strat = combatSituationHandler.getCurrentStrategy();
                if (!attackThisTick && inRange && mc.player.attackable() && mc.player.getAttackStrengthScale(0.5f) >= FULL_DAMAGE_THRESHOLD) {
                    if (strat == CombatSituationHandler.CombatStrategy.EATING_PUNISH
                        || strat == CombatSituationHandler.CombatStrategy.SHIELD_BREAK) {
                        attackThisTick = true;
                    }
                }

                if (attackThisTick) {
                    mc.gameMode.attack(mc.player, living);
                    mc.player.swing(InteractionHand.MAIN_HAND);

                    if (critMode) critAssist.onAttack();
                    comboTracker.onAttack(living);

                    entityHealTicks = 0;
                }
            }

            triggerBot.tick();
            comboTracker.tick();
        } catch (Exception e) {
            System.err.println("[Shinigami] Error in tick(): " + e.getMessage());
        }
    }

    private void handleNormalAim(IAimTarget target) {
        if (target == null || mc.player == null) return;

        Vec3 aimPoint = aimController.getAimPoint(target, false);
        Rotation targetRot = aimController.calculateRotation(aimPoint);
        Rotation currentRot = new Rotation(mc.player.getYRot(), mc.player.getXRot());
        Rotation smoothed = aimController.getSmoothedRotation(targetRot, currentRot);

        if (config.isSilentAim() && currentMode == Mode.EZ) {
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                .getLookBehavior().updateTarget(smoothed, false);
        } else {
            mc.player.setYRot(smoothed.getYaw());
            mc.player.setXRot(smoothed.getPitch());
        }
    }

    private void autoFarmMode() {
        try {
            if (mc.level == null || mc.player == null) return;
            if (mc.player.getFoodData().getFoodLevel() > 10) return;

            baritone.api.IBaritone b = baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
            if (b == null) return;

            net.minecraft.world.phys.AABB scanBox = new net.minecraft.world.phys.AABB(
                mc.player.getX() - 32, mc.player.getY() - 32, mc.player.getZ() - 32,
                mc.player.getX() + 32, mc.player.getY() + 32, mc.player.getZ() + 32
            );
            for (Entity e : mc.level.getEntitiesOfClass(Entity.class, scanBox)) {
                if (e instanceof net.minecraft.world.entity.animal.Animal animal && animal.isAlive() && !animal.isBaby()) {
                    if (mc.player.distanceTo(animal) < 20) {
                        b.getCustomGoalProcess().setGoalAndPath(
                            new baritone.api.pathing.goals.GoalBlock(animal.blockPosition())
                        );
                        Rotation rot = aimController.calculateRotation(
                            animal.position().add(0, animal.getBbHeight() * 0.4, 0)
                        );
                        mc.player.setYRot(rot.getYaw());
                        mc.player.setXRot(rot.getPitch());
                        if (mc.player.distanceTo(animal) <= config.getRange()
                            && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
                            mc.gameMode.attack(mc.player, animal);
                            mc.player.swing(InteractionHand.MAIN_HAND);
                        }
                        return;
                    }
                }
            }

            net.minecraft.core.BlockPos cropPos = null;
            for (int dx = -10; dx <= 10 && cropPos == null; dx++) {
                for (int dz = -10; dz <= 10 && cropPos == null; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        net.minecraft.core.BlockPos check = mc.player.blockPosition().offset(dx, dy, dz);
                        net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(check);
                        if (state.is(net.minecraft.world.level.block.Blocks.WHEAT) ||
                            state.is(net.minecraft.world.level.block.Blocks.CARROTS) ||
                            state.is(net.minecraft.world.level.block.Blocks.POTATOES) ||
                            state.is(net.minecraft.world.level.block.Blocks.BEETROOTS)) {
                            cropPos = check;
                            break;
                        }
                    }
                }
            }

            if (cropPos != null) {
                b.getCustomGoalProcess().setGoalAndPath(
                    new baritone.api.pathing.goals.GoalNear(cropPos, 2)
                );
                mc.gameMode.destroyBlock(cropPos);
            }
        } catch (Exception e) {
            System.err.println("[Shinigami] Error in autoFarm: " + e.getMessage());
        }
    }

    private void handleFleeMode() {
        if (mc.player == null) return;
        LivingEntity attacker = findNearestAttacker();
        if (attacker == null) return;

        Vec3 away = mc.player.position().subtract(attacker.position()).normalize();
        KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
        if (ctrl != null && config.isMovementMode()) {
            ctrl.moveToward(away, true, mc.player.onGround(), false);
        } else {
            Vec3 dir = away.scale(1.3);
            mc.player.setDeltaMovement(dir.x, mc.player.getDeltaMovement().y, dir.z);
            mc.player.setSprinting(true);
        }
    }

    private LivingEntity findNearestAttacker() {
        if (mc.level == null || mc.player == null) return null;
        double closest = 20;
        LivingEntity result = null;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            mc.player.getX() - 24, mc.player.getY() - 24, mc.player.getZ() - 24,
            mc.player.getX() + 24, mc.player.getY() + 24, mc.player.getZ() + 24
        );
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof net.minecraft.world.entity.monster.Monster monster) {
                if (monster.getTarget() == mc.player) {
                    double dist = mc.player.distanceTo(monster);
                    if (dist < closest) { closest = dist; result = monster; }
                }
            }
            if (e instanceof Player player && player != mc.player) {
                double dist = mc.player.distanceTo(player);
                if (dist < closest && dist < 5) { closest = dist; result = player; }
            }
        }
        return result;
    }

    public void onAttack(LivingEntity target) {
        comboTracker.onAttack(target);
        if (target instanceof Player) {
            config.setEnabled(true);
        }
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean e) {
        this.enabled = e;
        config.setEnabled(e);
        aimController.setActive(e);
        triggerBot.setActive(e && config.isTriggerBot());
        maceAssist.setActive(e && config.isMaceAssist());
        bowAssist.setActive(e && config.isBowMode());
        bridgeAssist.setActive(e && config.isBridgeMode());
        critAssist.setActive(e && config.isCritMode());
        dodgeSystem.setActive(e && config.isAutoDodge());
        windBurstAssist.setActive(e);
        if (!e) {
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
        }
    }
    @Override public void toggle() { setEnabled(!enabled); }
    @Override public Mode getMode() { return currentMode; }
    @Override public void setMode(Mode m) {
        this.currentMode = m;
        config.setModeType(m);
        if (m == Mode.DEMON) config.applyDemonPreset();
        else config.applyEZPreset();
    }
    @Override public IAimConfig getConfig() { return config; }
    @Override public boolean isTargeting() { return targetManager.getPrimaryTarget() != null; }
    @Override public IAimTarget getCurrentTarget() { return targetManager.getPrimaryTarget(); }

    public TargetManager getTargetManager() { return targetManager; }
    public AimController getAimController() { return aimController; }
    public TriggerBot getTriggerBot() { return triggerBot; }
    public DodgeSystem getDodgeSystem() { return dodgeSystem; }
    public CombatSituationHandler getCombatSituationHandler() { return combatSituationHandler; }
    public MaceAssist getMaceAssist() { return maceAssist; }
    public ComboTracker getComboTracker() { return comboTracker; }
    public BowAssist getBowAssist() { return bowAssist; }
    public BridgeAssist getBridgeAssist() { return bridgeAssist; }
    public CritAssist getCritAssist() { return critAssist; }
    public ScreenControl getScreenControl() { return screenControl; }
    public ClutchSystem getClutchSystem() { return clutchSystem; }
    public WindBurstAssist getWindBurstAssist() { return windBurstAssist; }
    public TagSystem getTagSystem() { return tagSystem; }
    public ChaseBehavior getChaseBehavior() { return chaseBehavior; }
    public SmartTaskExecutor getSmartExecutor() { return smartTaskExecutor; }
    public AutoInventory getAutoInventory() { return autoInventory; }
    public AutoCombatSwitch getAutoCombatSwitch() { return autoCombatSwitch; }
    public CombatPeripherals getCombatPeripherals() { return combatPeripherals; }
    public AutoTotem getAutoTotem() { return autoTotem; }
    public AutoUtil getAutoUtil() { return autoUtil; }
    public DurabilityManager getDurabilityManager() { return durabilityManager; }
    public AreaManager getAreaManager() { return areaManager; }
    public UnderwaterBreathing getUnderwaterBreathing() { return underwaterBreathing; }
    public PortalManager getPortalManager() { return portalManager; }
    public MovementBrain getMovementBrain() { return movementBrain; }
    public MovementArbiter getMovementArbiter() { return movementArbiter; }
    public AirStrafeController getAirStrafeController() { return airStrafeController; }

    // ─── Combat State ───
    private int wtapCooldown = 0;
    private boolean wasSprinting = false;
    private int entityHealTicks = 0;
    private LivingEntity lastAttackTarget = null;
    private static final float FULL_DAMAGE_THRESHOLD = 0.848f;

    public void setAimMode(int mode) { this.aimMode = mode; }
    public int getAimMode() { return aimMode; }

    public boolean isChaseMode() { return chaseMode; }
    public void setChaseMode(boolean c) { this.chaseMode = c; config.setChaseMode(c); }
    public boolean isFleeMode() { return fleeMode; }
    public void setFleeMode(boolean f) { this.fleeMode = f; }
    public boolean isFarmMode() { return farmMode; }
    public void setFarmMode(boolean f) { this.farmMode = f; }
    public String getChaseTargetName() { return chaseTargetName; }
    public void setCurrentChaseTarget(String n) {
        this.chaseTargetName = n;
        chaseBehavior.setTarget(n);
    }
    public boolean isChaseKill() { return chaseKill; }
    public void setChaseKill(boolean k) {
        this.chaseKill = k;
        chaseBehavior.setKill(k);
    }
    public void setTargetPlayerName(String n) { setCurrentChaseTarget(n); }
}
