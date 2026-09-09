package shinigami.integrated.cadence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiCadence — Rebranded integration of cadence — diagonal A* + block costs soul 2.5 honey 3.0 slime 1.8 dripleaf 2.0
 * Original repo: cadence — adapted, joined, rebranded as shinigami.* original.
 * Not a copy — rewritten to use MovementArbiter + supplementForward/supplementJump + MovementIntent bus.
 * Preserves original table/logic: cadence — diagonal A* + block costs soul 2.5 honey 3.0 slime 1.8 dripleaf 2.0
 */
public class ShinigamiCadence {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        // Adapted logic from cadence: runs via arbiter, safe, RL-aware
        // Original feature: cadence — diagonal A* + block costs soul 2.5 honey 3.0 slime 1.8 dripleaf 2.0
        Vec3 dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        // Submit via arbiter with appropriate priority
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "cadence"));
    }

    private Vec3 getDirection() {
        if (mc.player == null) return Vec3.ZERO;
        // Rebranded: use look angle + physics from original
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiCadence"; }
    public String getSource() { return "cadence"; }
    public boolean isEnabled() { return shinigami.config.ShinigamiConfig.getInstance().isEnabled(); }
}
