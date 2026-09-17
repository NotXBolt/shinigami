package baritone.launch.mixins;

import baritone.aimassist.AimAssistMod;
import baritone.aimassist.util.KeyMovementController;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        AimAssistMod mod = AimAssistMod.getInstance();
        if (mod != null) {
            KeyMovementController ctrl = mod.getMovementController();
            if (ctrl != null && ctrl.isActive()) {
                var self = (KeyboardInput)(Object)this;
                Input overridden = ctrl.getOverriddenInput(self.keyPresses);
                self.keyPresses = overridden;

                float forward = (overridden.forward() ? 1 : 0) - (overridden.backward() ? 1 : 0);
                float strafe = (overridden.left() ? 1 : 0) - (overridden.right() ? 1 : 0);
                if (forward != 0 && strafe != 0) {
                    forward *= 0.7071067812f;
                    strafe *= 0.7071067812f;
                }
                ((ClientInputAccessor)self).setMoveVector(new Vec2(strafe, forward));
            }
        }
    }
}
