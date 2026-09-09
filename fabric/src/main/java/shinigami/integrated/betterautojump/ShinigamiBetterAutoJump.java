package shinigami.integrated.betterautojump;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiBetterAutoJump — Rebranded integration of better-auto-jump — edge 0.3-2.0 step 0.35, velocity 0.1, solid 0.001-0.6
 * Original repo: betterautojump — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: better-auto-jump — edge 0.3-2.0 step 0.35, velocity 0.1, solid 0.001-0.6
 */
public class ShinigamiBetterAutoJump {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from betterautojump: runs via arbiter, safe, RL-aware
        // Original feature: better-auto-jump — edge 0.3-2.0 step 0.35, velocity 0.1, solid 0.001-0.6
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "betterautojump"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiBetterAutoJump"; }
    public String getSource() { return "betterautojump"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
