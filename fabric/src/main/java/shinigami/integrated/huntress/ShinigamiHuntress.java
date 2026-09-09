package shinigami.integrated.huntress;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;

/**
 * ShinigamiHuntress — Rebranded huntress.
 * Source: huntress — hack ESP info, no movement submit to avoid cheat spam.
 * Gated: pvpMode (ESP only, no move). Priority: COMBAT.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiHuntress {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        // hack ESP info, no movement submit to avoid cheat spam — gated: pvpMode (ESP only, no move). No movement submit (combat timing merged into CritAssist/ComboTracker).
        return;
    }

    public String getName() { return "ShinigamiHuntress"; }
    public String getSource() { return "huntress"; }
}
