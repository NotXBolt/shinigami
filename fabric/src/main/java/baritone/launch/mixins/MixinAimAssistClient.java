package baritone.launch.mixins;

import baritone.aimassist.AimAssistMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinAimAssistClient {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreTick(CallbackInfo ci) {
        AimAssistMod mod = AimAssistMod.getInstance();
        if (mod != null) {
            mod.onPreClientTick(Minecraft.getInstance());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        AimAssistMod mod = AimAssistMod.getInstance();
        if (mod != null) {
            mod.onClientTick(Minecraft.getInstance());
        }
    }
}
