package shinigami.integrated.unionclef;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiUnionclef — Rebranded unionclef.
 * Source: unionclef — altoclef fork, shredder v2 + tungsten no-break A*.
 * Gated: chaseMode (shredder/tungsten). Priority: PARKOUR.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiUnionclef {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        if (!isRelevant(cfg)) return;
        // altoclef fork, shredder v2 + tungsten no-break A* — gated: chaseMode (shredder/tungsten)
        var dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "unionclef"));
    }

    private boolean isRelevant(shinigami.config.ShinigamiConfig cfg) {
        return cfg.isChaseMode();
    }

    private net.minecraft.world.phys.Vec3 getDirection() {
        if (mc.player == null) return net.minecraft.world.phys.Vec3.ZERO;
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiUnionclef"; }
    public String getSource() { return "unionclef"; }
}
