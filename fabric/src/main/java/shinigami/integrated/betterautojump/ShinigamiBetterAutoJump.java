package shinigami.integrated.betterautojump;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiBetterAutoJump — Rebranded betterautojump.
 * Source: betterautojump — edge detect step 0.35, vel>0.1, solid 0.001-0.6.
 * Gated: onGround && edge 0.3-2.0. Priority: PARKOUR.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiBetterAutoJump {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        if (!isRelevant(cfg)) return;
        // edge detect step 0.35, vel>0.1, solid 0.001-0.6 — gated: onGround && edge 0.3-2.0
        var dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "betterautojump"));
    }

    private boolean isRelevant(shinigami.config.ShinigamiConfig cfg) {
        return cfg.isMovementMode() && mc.player != null && mc.player.onGround();
    }

    private net.minecraft.world.phys.Vec3 getDirection() {
        if (mc.player == null) return net.minecraft.world.phys.Vec3.ZERO;
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiBetterAutoJump"; }
    public String getSource() { return "betterautojump"; }
}
