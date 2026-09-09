package shinigami.integrated.yarn;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;

/**
 * ShinigamiYarn — Rebranded yarn.
 * Source: yarn — mappings, no tick.
 * Gated: infra only. Priority: NONE.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiYarn {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        // Infra only — no tick. Real connection: build/mappings layer.
        return;
    }

    public String getName() { return "ShinigamiYarn"; }
    public String getSource() { return "yarn"; }
}
