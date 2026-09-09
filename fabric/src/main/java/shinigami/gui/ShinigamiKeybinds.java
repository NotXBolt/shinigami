package shinigami.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * ShinigamiKeybinds — R toggle, G GUI. Original shinigami package.
 * 26.1: KeyMapping.Category.MISC (mojmap), sendSystemMessage (not displayClientMessage).
 * No KeyBindingHelper dep — GLFW polling handles R/G even if KeyMappings unregistered.
 */
public class ShinigamiKeybinds {
    public static final KeyMapping TOGGLE = new KeyMapping(
        "key.shinigami.toggle",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_R,
        KeyMapping.Category.MISC
    );
    public static final KeyMapping OPEN_GUI = new KeyMapping(
        "key.shinigami.gui",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        KeyMapping.Category.MISC
    );

    private static boolean wasR = false;
    private static boolean wasG = false;

    private static void feedback(String msg) {
        try {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
        } catch (Exception ignored) {}
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        // Vanilla consumeClick (works if registered elsewhere, harmless if not)
        try {
            while (TOGGLE.consumeClick()) {
                var cfg = shinigami.config.ShinigamiConfig.getInstance();
                cfg.toggle();
                feedback(cfg.isEnabled() ? "§7Shinigami stands deployed, Sir." : "§7Shinigami at rest, Sir.");
            }
        } catch (Exception ignored) {}
        try {
            while (OPEN_GUI.consumeClick()) {
                if (mc.screen == null) mc.setScreen(new ShinigamiScreen(null));
            }
        } catch (Exception ignored) {}
        // GLFW direct polling — always works, no Fabric API needed
        try {
            if (mc.getWindow() != null && mc.player != null) {
                long handle = mc.getWindow().handle();
                if (handle != 0) {
                    boolean downR = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    if (downR && !wasR) {
                        var cfg = shinigami.config.ShinigamiConfig.getInstance();
                        cfg.toggle();
                        feedback(cfg.isEnabled() ? "§7Shinigami stands deployed, Sir." : "§7Shinigami at rest, Sir.");
                    }
                    wasR = downR;
                    boolean downG = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
                    if (downG && !wasG) {
                        if (mc.screen == null) mc.setScreen(new ShinigamiScreen(null));
                    }
                    wasG = downG;
                }
            }
        } catch (Exception ignored) {}
    }

    public static void onKeyPress(int key) {
        // Called from MixinKeyboardHandler if present
        var mc = Minecraft.getInstance();
        if (key == GLFW.GLFW_KEY_R) {
            var cfg = shinigami.config.ShinigamiConfig.getInstance();
            cfg.toggle();
            feedback(cfg.isEnabled() ? "§7Shinigami stands deployed, Sir." : "§7Shinigami at rest, Sir.");
        }
        if (key == GLFW.GLFW_KEY_G) {
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
                feedback("§7Mark acquired: §f"+name);
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
