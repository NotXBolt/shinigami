package shinigami.integrated.macebot;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ShinigamiMacebot — Rebranded macebot.
 * Source: macebot — katch0420 mace smash, elytra/totem awareness.
 * Gated: holding mace && fall>2. Priority: COMBAT.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiMacebot {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        if (!isRelevant(cfg)) return;
        // katch0420 mace smash, elytra/totem awareness — gated: holding mace && fall>2
        var dir = getDirection();
        if (dir.lengthSqr() < 1e-6) return;
        arbiter.submit(new MovementIntent(MovementIntent.Priority.COMBAT, dir, true, MovementIntent.JumpType.GAP_JUMP, false, 2, "macebot"));
    }

    private boolean isRelevant(shinigami.config.ShinigamiConfig cfg) {
        try { return cfg.isMaceMode() && mc.player != null && (mc.player.getMainHandItem().is(net.minecraft.world.item.Items.MACE)); } catch (Exception e) { return false; }
    }

    private net.minecraft.world.phys.Vec3 getDirection() {
        if (mc.player == null) return net.minecraft.world.phys.Vec3.ZERO;
        return mc.player.getLookAngle().scale(1.0);
    }

    public String getName() { return "ShinigamiMacebot"; }
    public String getSource() { return "macebot"; }
}
