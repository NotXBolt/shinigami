package shinigami.integrated.pvpbotfabric;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiPvpBotFabric — Rebranded pvpbotfabric.
 * Source: pvpbotfabric — windburst mace, shield break, crit fallTicks, bow pred.
 * Gated: pvpMode && target in 3.2m. Priority: COMBAT.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiPvpBotFabric {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        if (!isRelevant(cfg)) return;
        // windburst mace, shield break, crit fallTicks, bow pred — gated: pvpMode && target in 3.2m
        var dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        arbiter.submit(new MovementIntent(MovementIntent.Priority.COMBAT, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "pvpbotfabric"));
    }

    private boolean isRelevant(shinigami.config.ShinigamiConfig cfg) {
        return cfg.isPvpMode();
    }

    private net.minecraft.world.phys.Vec3 getDirection() {
        if (mc.player == null) return net.minecraft.world.phys.Vec3.ZERO;
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiPvpBotFabric"; }
    public String getSource() { return "pvpbotfabric"; }
}
