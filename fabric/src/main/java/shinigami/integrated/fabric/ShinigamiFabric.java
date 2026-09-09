package shinigami.integrated.fabric;

import net.minecraft.client.Minecraft;
import shinigami.movement.MovementArbiter;

/**
 * ShinigamiFabric — Rebranded fabric.
 * Source: fabric — Fabric API base, no tick.
 * Gated: infra only. Priority: NONE.
 * Adapted-joined-rebranded as shinigami.* — real connection via MovementArbiter + IntegrationRegistry.tickAll.
 */
public class ShinigamiFabric {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        // Infra only — no tick. Real connection: build/mappings layer.
        return;
    }

    public String getName() { return "ShinigamiFabric"; }
    public String getSource() { return "fabric"; }
}
