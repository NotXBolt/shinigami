package baritone.launch.mixins;

import baritone.aimassist.AimAssistMod;
import baritone.aimassist.util.KeyMovementController;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class MixinLocalPlayerInput {

    @Inject(method = "onInput", at = @At("HEAD"))
    private void onOnInputHead(ClientInput clientInput, CallbackInfo ci) {
        replaceInput(clientInput);
    }

    @Inject(method = "onInput", at = @At("TAIL"))
    private void onOnInputTail(ClientInput clientInput, CallbackInfo ci) {
        replaceInput(clientInput);
    }

    private void replaceInput(ClientInput clientInput) {
        AimAssistMod mod = AimAssistMod.getInstance();
        if (mod != null) {
            KeyMovementController ctrl = mod.getMovementController();
            if (ctrl != null && ctrl.isActive()) {
                clientInput.keyPresses = ctrl.getOverriddenInput(clientInput.keyPresses);
            }
        }
    }
}
