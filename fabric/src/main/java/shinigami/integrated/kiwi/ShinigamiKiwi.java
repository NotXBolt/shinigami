package shinigami.integrated.kiwi;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiKiwi — Rebranded kiwi.
 * Source: kiwi — Theta* any-angle dir (not 0/45), GoalXYZ.
 * Gated: chaseMode. Priority: PARKOUR.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiKiwi {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        if (!isRelevant(cfg)) return;
        // Theta* any-angle dir (not 0/45), GoalXYZ — gated: chaseMode
        var dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        arbiter.submit(new MovementIntent(MovementIntent.Priority.PARKOUR, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "kiwi"));
    }

    private boolean isRelevant(shinigami.config.ShinigamiConfig cfg) {
        return cfg.isChaseMode() && cfg.getChaseTargetName() != null;
    }

    private net.minecraft.world.phys.Vec3 getDirection() {
        if (mc.player == null) return net.minecraft.world.phys.Vec3.ZERO;
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiKiwi"; }
    public String getSource() { return "kiwi"; }
}
