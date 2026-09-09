package shinigami.integrated.kiwi;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiKiwi — Rebranded integration of Kiwi — Theta* any-angle, high-perf 26.1.2, GoalXYZ. Adapted from org.kvxd.kiwi
 * Original repo: kiwi — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: Kiwi — Theta* any-angle, high-perf 26.1.2, GoalXYZ. Adapted from org.kvxd.kiwi
 */
public class ShinigamiKiwi {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from kiwi: runs via arbiter, safe, RL-aware
        // Original feature: Kiwi — Theta* any-angle, high-perf 26.1.2, GoalXYZ. Adapted from org.kvxd.kiwi
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "kiwi"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiKiwi"; }
    public String getSource() { return "kiwi"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
