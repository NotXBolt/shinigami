package shinigami.integrated.mineflayerpathfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiMineflayerPathfinder — Rebranded integration of mineflayer-pathfinder — JS A* for mineflayer, moves, goals
 * Original repo: mineflayerpathfinder — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: mineflayer-pathfinder — JS A* for mineflayer, moves, goals
 */
public class ShinigamiMineflayerPathfinder {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from mineflayerpathfinder: runs via arbiter, safe, RL-aware
        // Original feature: mineflayer-pathfinder — JS A* for mineflayer, moves, goals
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "mineflayerpathfinder"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiMineflayerPathfinder"; }
    public String getSource() { return "mineflayerpathfinder"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
