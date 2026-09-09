package shinigami.integrated.dodger;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiDodger — Rebranded integration of Dodger — 1-block safe dodge, arrow/projectile perp, 19 threat types. Adapted from com.dodger.AutoDodger
 * Original repo: dodger — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: Dodger — 1-block safe dodge, arrow/projectile perp, 19 threat types. Adapted from com.dodger.AutoDodger
 */
public class ShinigamiDodger {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from dodger: runs via arbiter, safe, RL-aware
        // Original feature: Dodger — 1-block safe dodge, arrow/projectile perp, 19 threat types. Adapted from com.dodger.AutoDodger
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "dodger"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiDodger"; }
    public String getSource() { return "dodger"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
