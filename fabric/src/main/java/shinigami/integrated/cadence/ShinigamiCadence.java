package shinigami.integrated.cadence;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiCadence — Rebranded cadence.
 * Source: cadence — diagonal A* + block costs soul 2.5 honey 3.0 slime 1.8.
 * Gated: chaseMode. Priority: PARKOUR.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiCadence {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        if (!isRelevant(cfg)) return;
        // diagonal A* + block costs soul 2.5 honey 3.0 slime 1.8 — gated: chaseMode
        var dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "cadence"));
    }

    private boolean isRelevant(shinigami.config.ShinigamiConfig cfg) {
        return cfg.isChaseMode();
    }

    private net.minecraft.world.phys.Vec3 getDirection() {
        if (mc.player == null) return net.minecraft.world.phys.Vec3.ZERO;
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiCadence"; }
    public String getSource() { return "cadence"; }
}
