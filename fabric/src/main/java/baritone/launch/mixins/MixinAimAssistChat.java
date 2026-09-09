package baritone.launch.mixins;

import baritone.aimassist.AimAssistKeybinds;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class MixinAimAssistChat {

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void onHandleChatInput(String message, boolean addToHistory, CallbackInfo ci) {
        if (!message.startsWith("&") && !message.startsWith(".")) return;
        String cmd = message.substring(1);
        if (cmd.isEmpty()) return;

        try {
            AimAssistKeybinds keybinds = new AimAssistKeybinds();
            if (cmd.startsWith("tag ") || cmd.startsWith(".tag ")) {
                keybinds.handleChatCommand(cmd.startsWith(".") ? cmd.substring(1) : cmd);
            } else {
                String first = cmd.split("\\s+")[0];
                boolean known = first.equals("chase") || first.equals("kill") || first.equals("follow") || first.equals("hunt")
                    || first.equals("toggle") || first.equals("gui") || first.equals("on") || first.equals("off")
                    || first.equals("help") || first.equals("clear") || first.equals("status")
                    || first.equals("area") || first.equals("portal")
                    || first.equals("xpfarm") || first.equals("repair") || first.equals("durability")
                    || first.equals("targets") || first.equals("scan") || first.equals("cleartarget");
                if (!known) return;
                keybinds.handleChatCommand("tag " + cmd);
            }
            ci.cancel();
        } catch (Exception e) {
            System.out.println("[Shinigami] Chat error: " + e.getMessage());
        }
    }
}
