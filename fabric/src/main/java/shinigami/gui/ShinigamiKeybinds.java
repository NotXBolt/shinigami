package shinigami.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * ShinigamiKeybinds — R toggle, G GUI. Original shinigami package.
 * Uses Fabric KeyMapping + GLFW direct for chat-trigger safety.
 */
public class ShinigamiKeybinds {
    public static final KeyMapping TOGGLE = new KeyMapping(
        "key.shinigami.toggle",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        "key.categories.shinigami"
    );
    public static final KeyMapping OPEN_GUI = new KeyMapping(
        "key.shinigami.gui",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        "key.categories.shinigami"
    );

    private static final Minecraft mc = Minecraft.getInstance();

    public static void tick() {
        while (TOGGLE.consumeClick()) {
            var cfg = shinigami.config.ShinigamiConfig.getInstance();
            cfg.toggle();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                        cfg.isEnabled() ? "§7Shinigami stands deployed, Sir." : "§7Shinigami at rest, Sir."
                    ), true);
            }
        }
        while (OPEN_GUI.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ShinigamiScreen(null));
        }
    }

    public static void handleChatCommand(String msg) {
        if (msg == null) return;
        String m = msg.trim().toLowerCase();
        var cfg = shinigami.config.ShinigamiConfig.getInstance();
        var mod = shinigami.ShinigamiMod.getInstance();
        if (m.equals("tag toggle") || m.equals(".tag toggle") || m.equals("shinigami toggle")) {
            cfg.toggle();
            return;
        }
        if (m.equals("tag gui") || m.equals(".tag gui") || m.equals("shinigami gui")) {
            Minecraft.getInstance().setScreen(new ShinigamiScreen(null));
            return;
        }
        if (m.startsWith("tag chase ") || m.startsWith(".tag chase ")) {
            String name = msg.split("\\s+", 3).length >=3 ? msg.split("\\s+",3)[2] : "";
            if (!name.isEmpty() && mod != null) {
                mod.getChaseBehavior().setTarget(name, false);
                cfg.setChaseTargetName(name); cfg.setChaseMode(true); cfg.setEnabled(true);
                if (Minecraft.getInstance().player != null)
                    Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7Mark acquired: §f"+name), false);
            }
        }
        if (m.startsWith("tag kill ") || m.startsWith(".tag kill ")) {
            String name = msg.split("\\s+",3).length>=3 ? msg.split("\\s+",3)[2] : "";
            if (!name.isEmpty() && mod != null) {
                mod.getChaseBehavior().setTarget(name, true);
                cfg.setChaseTargetName(name); cfg.setChaseMode(true); cfg.setChaseKill(true); cfg.setEnabled(true); cfg.setPvpMode(true);
            }
        }
        if (m.equals("tag clear") || m.equals(".tag clear")) {
            if (mod != null) mod.getChaseBehavior().clear();
            cfg.setChaseMode(false); cfg.setChaseKill(false);
            if (mod != null) mod.getTargetManager().clear();
        }
    }
}
