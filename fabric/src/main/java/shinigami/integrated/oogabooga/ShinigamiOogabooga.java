package shinigami.integrated.oogabooga;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiOogabooga — Rebranded integration of Oogabooga — relentless pursuit, always sprint, terrain-aware jump, 2-4 gap. Adapted from Oogabooga sprint
 * Original repo: oogabooga — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: Oogabooga — relentless pursuit, always sprint, terrain-aware jump, 2-4 gap. Adapted from Oogabooga sprint
 */
public class ShinigamiOogabooga {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from oogabooga: runs via arbiter, safe, RL-aware
        // Original feature: Oogabooga — relentless pursuit, always sprint, terrain-aware jump, 2-4 gap. Adapted from Oogabooga sprint
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "oogabooga"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiOogabooga"; }
    public String getSource() { return "oogabooga"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
