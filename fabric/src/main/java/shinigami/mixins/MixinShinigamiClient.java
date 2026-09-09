package shinigami.mixins;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import shinigami.ShinigamiMod;

@Mixin(Minecraft.class)
public class MixinShinigamiClient {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreTick(CallbackInfo ci) {
        var mod = ShinigamiMod.getInstance();
        if (mod != null) mod.onPreClientTick(Minecraft.getInstance());
    }
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        var mod = ShinigamiMod.getInstance();
        if (mod != null) mod.onClientTick(Minecraft.getInstance());
    }
}
