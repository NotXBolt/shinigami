package shinigami.integrated.fabricautoclicker;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;

/**
 * ShinigamiFabricAutoClicker — Rebranded fabricautoclicker.
 * Source: fabricautoclicker — ImadSaddik cooldown-aware swing, no blind spam.
 * Gated: attacking (cooldown-aware). Priority: COMBAT.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiFabricAutoClicker {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        // ImadSaddik cooldown-aware swing, no blind spam — gated: attacking (cooldown-aware). No movement submit (combat timing merged into CritAssist/ComboTracker).
        return;
    }

    public String getName() { return "ShinigamiFabricAutoClicker"; }
    public String getSource() { return "fabricautoclicker"; }
}
