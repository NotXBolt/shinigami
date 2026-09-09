package baritone.aimassist.movement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

/**
 * High-Velocity Aggressive Parkour Controller (EpicBaritone architecture).
 * Forces relentless sprint-jump sequences with automatic gap clearance.
 * Combines with Supplement layer (jump supplement) and Steering layer (yaw rotation).
 * Universal 26.1.2 Fabric compatible.
 */
public class AggressiveParkourController {

    private final MinecraftClient client = MinecraftClient.getInstance();

    /**
     * Overrides standard input mechanics to maintain maximum velocity loops.
     * Executes frame-perfect sprint-jump sequences and gap clearing.
     */
    public void executeAggressiveMovement(boolean dynamicJumpRequired, boolean gapDetected) {
        if (client.player == null) return;

        // Force relentless sprinting regardless of hunger thresholds
        client.player.setSprinting(true);
        KeyBinding.setKeyPressed(client.options.forwardKey.getDefaultKey(), true);

        // Frame-perfect jump input for gap clearance and vertical acceleration
        if (dynamicJumpRequired || gapDetected) {
            if (client.player.isOnGround()) {
                KeyBinding.setKeyPressed(client.options.jumpKey.getDefaultKey(), true);
            }
        } else {
            // Release jump to recover landing inertia metrics
            KeyBinding.setKeyPressed(client.options.jumpKey.getDefaultKey(), false);
        }
    }
}
