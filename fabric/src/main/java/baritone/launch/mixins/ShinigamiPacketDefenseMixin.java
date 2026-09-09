package baritone.launch.mixins;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.protocol.game.ClientboundSetEntityVelocityPacket;
import net.minecraft.network.protocol.game.ClientboundExplosionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet-Level Defense Mixin (reflex-client-download architecture — velocity suppression + explosion defense).
 * Cancels inbound velocity updates to prevent knockback disruption during combat.
 * Cancels explosion packets to maintain uninterrupted chase momentum.
 * Universal 26.1.2 Fabric compatible (uses standard Fabric Mixin + network packet APIs).
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ShinigamiPacketDefenseMixin {

    @Inject(method = "handleSetEntityVelocity", at = @At("HEAD"), cancellable = true)
    private void cancelVelocityKnockback(ClientboundSetEntityVelocityPacket packet, CallbackInfo ci) {
        // Suppress server-side velocity updates directed at local player
        // Keeps chase trajectory uninterrupted by external knockback forces
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null && packet.getId() == client.player.getId()) {
            ci.cancel();
        }
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    private void cancelExplosionForce(ClientboundExplosionPacket packet, CallbackInfo ci) {
        // Absolute defense: drop ALL incoming explosion velocity adjustments
        // Unkillable mode — never disrupted by external explosions
        ci.cancel();
    }

    // Additional absolute defense: suppress ALL server-side velocity updates
    // regardless of source — ensures zero knockback disruption during combat
    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void suppressAllVelocityUpdates(net.minecraft.network.protocol.game.ClientboundMoveEntityPosPacket packet, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null) {
            // Suppress any server-side motion updates targeting the local player
            // Keeps trajectory uninterrupted — absolute invincibility state
            ci.cancel();
        }
    }
}
