package shinigami;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import shinigami.combat.ChaseBehavior;
import shinigami.combat.ClutchSystem;
import shinigami.combat.DodgeSystem;
import shinigami.config.ShinigamiConfig;
import shinigami.movement.MovementArbiter;
import shinigami.prediction.BehavioralPredictor;
import shinigami.targeting.TargetManager;
import shinigami.util.KeyController;

/**
 * ShinigamiMod — Total from scratch, original, Phase 0.
 * Wires TargetManager 360 + ChaseBehavior + DodgeSystem + ClutchSystem + MovementArbiter + KeyController.
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

    public static ShinigamiMod getInstance() { return INSTANCE; }
    public static void init() { INSTANCE = new ShinigamiMod(); }

    @Override
    public void onInitializeClient() { init(); }

    public void onPreClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        keyController.clearSupplement();
        arbiter.clear();
        targetManager.tick();
        var target = targetManager.getPrimary();
        if (target != null) predictor.update(target);
        // Dodge first (DODGE 100)
        dodgeSystem.tick(arbiter, target);
        // Clutch (CLUTCH 95)
        clutchSystem.tick(arbiter);
        // Chase (CHASE 50) — only if not dodging
        if (config.isEnabled() && config.isMovementMode()) {
            chaseBehavior.tick(arbiter);
        }
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
}
