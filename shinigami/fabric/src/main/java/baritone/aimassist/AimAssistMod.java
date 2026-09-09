package baritone.aimassist;

import baritone.aimassist.util.KeyMovementController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class AimAssistMod implements ModInitializer, ClientModInitializer {

    private static AimAssistMod INSTANCE;
    private final AimAssistModule module = AimAssistModule.getInstance();
    private final AimAssistConfig config = AimAssistConfig.getInstance();
    private final AimAssistKeybinds keybinds = new AimAssistKeybinds();
    private final AimAssistOverlay overlay = new AimAssistOverlay();
    private final KeyMovementController movementController = new KeyMovementController();

    @Override
    public void onInitialize() {
        INSTANCE = this;
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
            Identifier.fromNamespaceAndPath("shinigami", "target_info"),
            (graphics, deltaTracker) -> {
                if (config.isShowHUD()) {
                    overlay.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
                }
            });
        System.out.println("[Shinigami] Initialized successfully");
    }

    public void onPreClientTick(net.minecraft.client.Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        movementController.setActive(config.isMovementMode() || config.isAutoDodge());
        movementController.tick();
        if (!config.isEnabled()) {
            movementController.clearSupplement();
            movementController.stopMoving();
            return;
        }
        module.getDodgeSystem().setActive(config.isAutoDodge());
        module.getCombatSituationHandler().setActive(config.isEnabled());
        module.tickMovement();
    }

    public void onClientTick(net.minecraft.client.Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        keybinds.handleKeybinds();
        if (!config.isEnabled()) {
            movementController.clearSupplement();
            movementController.stopMoving();
            return;
        }
        module.tick();
    }

    public static AimAssistMod getInstance() { return INSTANCE; }
    public AimAssistKeybinds getKeybinds() { return keybinds; }
    public AimAssistOverlay getOverlay() { return overlay; }
    public AimAssistModule getModule() { return module; }
    public AimAssistConfig getConfig() { return config; }
    public KeyMovementController getMovementController() { return movementController; }
}
