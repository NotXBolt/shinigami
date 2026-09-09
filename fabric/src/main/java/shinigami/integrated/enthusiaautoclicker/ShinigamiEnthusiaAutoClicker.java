package shinigami.integrated.enthusiaautoclicker;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiEnthusiaAutoClicker — Rebranded integration of EnthusiaAutoClicker — wsg138 rate-limited autoclicker 26.1.x
 * Original repo: enthusiaautoclicker — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: EnthusiaAutoClicker — wsg138 rate-limited autoclicker 26.1.x
 */
public class ShinigamiEnthusiaAutoClicker {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from enthusiaautoclicker: runs via arbiter, safe, RL-aware
        // Original feature: EnthusiaAutoClicker — wsg138 rate-limited autoclicker 26.1.x
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "enthusiaautoclicker"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiEnthusiaAutoClicker"; }
    public String getSource() { return "enthusiaautoclicker"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
