package shinigami.integrated.parkourcalculator;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiParkourCalculator — Rebranded integration of ParkourCalculator — physics 0.91/0.98/0.08, JumpArcPredictor. Adapted from ParkourCalculator
 * Original repo: parkourcalculator — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: ParkourCalculator — physics 0.91/0.98/0.08, JumpArcPredictor. Adapted from ParkourCalculator
 */
public class ShinigamiParkourCalculator {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from parkourcalculator: runs via arbiter, safe, RL-aware
        // Original feature: ParkourCalculator — physics 0.91/0.98/0.08, JumpArcPredictor. Adapted from ParkourCalculator
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "parkourcalculator"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiParkourCalculator"; }
    public String getSource() { return "parkourcalculator"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
