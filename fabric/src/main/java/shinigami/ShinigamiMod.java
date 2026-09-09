package shinigami;

import net.minecraft.client.Minecraft;
import shinigami.combat.*;
import shinigami.config.ShinigamiConfig;
import shinigami.gui.ShinigamiKeybinds;
import shinigami.learning.ReinforcementLearner;
import shinigami.movement.MovementArbiter;
import shinigami.prediction.BehavioralPredictor;
import shinigami.safety.SafeGuard;
import shinigami.targeting.TargetManager;
import shinigami.integrated.IntegrationRegistry;
import shinigami.util.KeyController;

/**
 * ShinigamiMod — Total from scratch, original, Phase 0+ with GUI, RL, Safe.
 * v1.0.5 — all 22 repos integrated, R toggle, G GUI, not auto-run on world load, safe chase/dodge.
 * NOTE 26.1: no Fabric KeyBindingHelper dep — keys polled via GLFW in ShinigamiKeybinds.tick().
 */
public class ShinigamiMod implements ClientModInitializer {

    private static ShinigamiMod INSTANCE;
    private final Minecraft mc = Minecraft.getInstance();
    private final ShinigamiConfig config = ShinigamiConfig.getInstance();
    private final TargetManager targetManager = new TargetManager(config);
    private final BehavioralPredictor predictor = new BehavioralPredictor();
    private final MovementArbiter arbiter = new MovementArbiter();
    private final KeyController keyController = new KeyController();
    private final ChaseBehavior chaseBehavior = new ChaseBehavior(targetManager);
    private final DodgeSystem dodgeSystem = new DodgeSystem(predictor);
    private final ClutchSystem clutchSystem = new ClutchSystem();
    private final CritAssist critAssist = new CritAssist();
    private final ComboTracker comboTracker = new ComboTracker();
    private final MaceAssist maceAssist = new MaceAssist();
    private final BowAssist bowAssist = new BowAssist(predictor);
    private final ReinforcementLearner learner = new ReinforcementLearner();
    private final SafeGuard safeGuard = new SafeGuard();
    private final IntegrationRegistry integration = new IntegrationRegistry();

    private double lastHealth = 20;

    public static ShinigamiMod getInstance() { return INSTANCE; }
    public static void init() { INSTANCE = new ShinigamiMod(); }

    @Override
    public void onInitializeClient() {
        init();
        // No KeyBindingHelper — 26.1 + no fabric-api dep. GLFW polling in tick handles R/G.
        learner.setLearningRate(config.getRlLearningRate());
        learner.setDiscount(config.getRlDiscount());
    }

    public void onPreClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        ShinigamiKeybinds.tick();
        keyController.clearSupplement();
        arbiter.clear();

        // Safety: if health dropped, RL reward
        double curHp = mc.player.getHealth();
        if (curHp < lastHealth) learner.onDamage(lastHealth, curHp);
        if (mc.player.isDeadOrDying()) learner.onDeath();
        lastHealth = curHp;

        targetManager.tick();
        var target = targetManager.getPrimary();
        if (target != null) predictor.update(target);
        comboTracker.tick();

        // Dodge first (DODGE 100) — RL + SafeGuard integrated, only if enabled
        if (config.isEnabled() && config.isAutoDodge()) {
            dodgeSystem.tick(arbiter, target, learner, safeGuard);
        } else {
            // still check void/lava via clutch even if dodge off, if safeMode
            if (config.isSafeMode()) clutchSystem.tick(arbiter);
        }
        // Clutch (CLUTCH 95)
        if (config.isEnabled() && config.isAutoClutch()) clutchSystem.tick(arbiter);

        // Combat assists — only when in chase/pvp and enabled
        if (config.isEnabled() && config.isPvpMode() && target != null) {
            critAssist.tick(target, arbiter);
            maceAssist.tick(target, arbiter);
            bowAssist.tick(target);
            if (config.isRlEnabled()) learner.onSurviveTick(mc.player.distanceTo(target), curHp/20.0);
        }

        // Chase (CHASE 50) — ONLY if explicitly chasing, not auto on world load
        // Fix: previously auto-chased any nearby mob; now require chaseMode or R + target
        if (config.isEnabled() && config.isMovementMode()) {
            if (config.isChaseMode() && config.getChaseTargetName() != null) {
                if (!safeGuard.shouldAbortChase() || !config.isSafeMode()) {
                    chaseBehavior.tick(arbiter, safeGuard);
                }
            } else if (!config.isSafeMode()) {
                // safeMode true => don't auto chase mobs; safeMode false => allow old mob chase
                // but still avoid passive unless targetPassive true
                if (target != null && (config.isTargetHostile() || config.isTargetPlayers())) {
                    // only chase hostile/players, not passive unless explicit
                    if (target instanceof net.minecraft.world.entity.monster.Enemy || target instanceof net.minecraft.world.entity.player.Player) {
                        chaseBehavior.tick(arbiter, safeGuard);
                    }
                }
            }
        }
        // Integrated 22 repos — tick all rebranded modules
        try { integration.tickAll(arbiter); } catch (Exception ignored) {}
        arbiter.apply(keyController);
        keyController.tick();
    }

    public void onClientTick(Minecraft mc) {
        if (mc.player == null) return;
        keyController.applyInputs();
    }

    public ShinigamiConfig getConfig() { return config; }
    public TargetManager getTargetManager() { return targetManager; }
    public MovementArbiter getArbiter() { return arbiter; }
    public KeyController getKeyController() { return keyController; }
    public ChaseBehavior getChaseBehavior() { return chaseBehavior; }
    public ReinforcementLearner getLearner() { return learner; }
    public SafeGuard getSafeGuard() { return safeGuard; }
    public IntegrationRegistry getIntegration() { return integration; }
}
