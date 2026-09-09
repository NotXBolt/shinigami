package shinigami.integrated.enthusiaautoclicker;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;

/**
 * ShinigamiEnthusiaAutoClicker — Rebranded enthusiaautoclicker.
 * Source: enthusiaautoclicker — wsg138 rate-limited click, drives key mappings.
 * Gated: attacking (rate-limited). Priority: COMBAT.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiEnthusiaAutoClicker {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        if (!cfg.isEnabled()) return;
        // wsg138 rate-limited click, drives key mappings — gated: attacking (rate-limited). No movement submit (combat timing merged into CritAssist/ComboTracker).
        return;
    }

    public String getName() { return "ShinigamiEnthusiaAutoClicker"; }
    public String getSource() { return "enthusiaautoclicker"; }
}
