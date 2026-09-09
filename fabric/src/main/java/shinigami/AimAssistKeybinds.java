package shinigami;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public class AimAssistKeybinds {

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

    private static boolean shouldToggle = false;
    private static boolean shouldOpenGUI = false;

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module = AimAssistModule.getInstance();
    private final AimAssistConfig config = AimAssistConfig.getInstance();

    public static void onKeyPress(int key) {
        if (key == GLFW.GLFW_KEY_R) shouldToggle = true;
        if (key == GLFW.GLFW_KEY_G) shouldOpenGUI = true;
    }

    public void handleKeybinds() {
        if (mc.getWindow() != null) {
            long handle = mc.getWindow().handle();
            if (handle != 0) {
                checkGLFWKey(handle, GLFW.GLFW_KEY_R, wasR, v -> wasR = v, () -> {
                    module.toggle();
                    sendFeedback(module.isEnabled() ? "§7Shinigami stands deployed, Sir." : "§7Shinigami at rest, Sir.");
                });
                checkGLFWKey(handle, GLFW.GLFW_KEY_G, wasG, v -> wasG = v, () -> {
                    mc.setScreen(new AimAssistScreen(null));
                });
            }
        }

        if (shouldToggle) {
            shouldToggle = false;
            module.toggle();
            sendFeedback(module.isEnabled() ? "§7Shinigami stands deployed, Sir." : "§7Shinigami at rest, Sir.");
        }
        if (shouldOpenGUI) {
            shouldOpenGUI = false;
            mc.setScreen(new AimAssistScreen(null));
        }

        if (TOGGLE.consumeClick()) {
            module.toggle();
            sendFeedback(module.isEnabled() ? "§7Shinigami stands deployed, Sir." : "§7Shinigami at rest, Sir.");
        }
        if (OPEN_GUI.consumeClick()) {
            mc.setScreen(new AimAssistScreen(null));
        }
    }

    private static boolean wasR = false;
    private static boolean wasG = false;

    private void checkGLFWKey(long handle, int key, boolean prev, java.util.function.Consumer<Boolean> setter, Runnable action) {
        boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        if (down && !prev) action.run();
        setter.accept(down);
    }

    public void handleChatCommand(String message) {
        if (message.equals("tag toggle") || message.equals(".tag toggle")) {
            module.toggle();
            sendFeedback(module.isEnabled() ? "§7Shinigami stands deployed, Sir." : "§7Shinigami at rest, Sir.");
            return;
        }
        if (message.equals("tag gui") || message.equals(".tag gui")) {
            mc.setScreen(new AimAssistScreen(null));
            return;
        }
        if (message.equals("tag on") || message.equals(".tag on")) {
            config.setEnabled(true);
            sendFeedback("§7Shinigami stands deployed, Sir.");
            return;
        }
        if (message.equals("tag off") || message.equals(".tag off")) {
            config.setEnabled(false);
            sendFeedback("§7Shinigami at rest, Sir.");
            return;
        }
        if (!message.startsWith("tag ") && !message.startsWith(".tag ")) return;

        String cmd = message.startsWith(".") ? message.substring(1) : message;
        String[] parts = cmd.split("\\s+");

        if (parts.length < 2) return;
        String sub = parts[1].toLowerCase();

        switch (sub) {
            case "chase" -> {
                if (parts.length < 3) {
                    sendFeedback("Usage: &chase <playername>");
                    return;
                }
                module.getTagSystem().addTagWithString("chase " + parts[2]);
                module.setChaseMode(true);
                module.setCurrentChaseTarget(parts[2]);
                config.setMovementMode(true);
                module.setEnabled(true);
                sendFeedback("§7Mark acquired: §f" + parts[2] + "§7. Shinigami gives chase, Sir.");
            }
            case "kill" -> {
                if (parts.length < 3) {
                    sendFeedback("Usage: &kill <playername>");
                    return;
                }
                module.getTagSystem().addTagWithString("kill " + parts[2]);
                module.setChaseMode(true);
                module.setChaseKill(true);
                module.setCurrentChaseTarget(parts[2]);
                config.setMovementMode(true);
                module.setEnabled(true);
                config.setPvpMode(true);
                config.setCritMode(true);
                config.setMaceMode(true);
                config.setAutoEat(true);
                sendFeedback("§7Kill order confirmed: §f" + parts[2] + "§7. Shinigami will not relent, Sir.");
            }
            case "follow" -> {
                if (parts.length < 3) {
                    sendFeedback("Usage: &follow <playername>");
                    return;
                }
                module.getTagSystem().addTagWithString("follow " + parts[2]);
                module.setChaseMode(true);
                module.setCurrentChaseTarget(parts[2]);
                config.setMovementMode(true);
                module.setEnabled(true);
                sendFeedback("§7Escort detail assigned: §f" + parts[2] + "§7, Sir.");
            }
            case "hunt" -> {
                if (parts.length < 3) {
                    sendFeedback("Usage: &hunt <mobtype> (e.g. zombie, creeper, skeleton, iron_golem)");
                    return;
                }
                module.getTagSystem().addTagWithString("hunt " + parts[2]);
                module.setChaseMode(true);
                module.setChaseKill(true);
                module.getChaseBehavior().setMobHunt(parts[2]);
                config.setMovementMode(true);
                module.setEnabled(true);
                config.setPvpMode(true);
                config.setCritMode(true);
                config.setMaceMode(true);
                config.setAutoEat(true);
                sendFeedback("§7Hunt commenced: §f" + parts[2] + "§7. Shinigami advancing, Sir.");
            }
            case "clear" -> {
                module.getTagSystem().clearTags();
                module.getChaseBehavior().reset();
                module.setChaseMode(false);
                module.setFleeMode(false);
                module.setFarmMode(false);
                sendFeedback("§7All tags cleared");
            }
            case "status" -> {
                sendFeedback("§6--- Tag Status ---");
                sendFeedback("Chase: " + (module.isChaseMode() ? "§aON" : "§7OFF"));
                if (module.getChaseTargetName() != null) {
                    sendFeedback("Target: §e" + module.getChaseTargetName());
                    sendFeedback("Kill: " + (module.isChaseKill() ? "§aYES" : "§7NO"));
                }
                sendFeedback("Flee: " + (module.isFleeMode() ? "§aON" : "§7OFF"));
                sendFeedback("Farm: " + (module.isFarmMode() ? "§aON" : "§7OFF"));
                sendFeedback("Auto: " + (config.isAutoMode() ? "§aON" : "§7OFF"));
                sendFeedback("PvP: " + (config.isPvpMode() ? "§aON" : "§7OFF"));
                sendFeedback("Mode: " + (module.getMode() == baritone.api.aimassist.IAimAssist.Mode.DEMON ? "§cDEMON" : "§bEZ"));
            }
            case "area" -> handleAreaCommand(cmd);
            case "portal" -> handlePortalCommand(cmd);
            case "xpfarm" -> {
                if (parts.length >= 3) {
                    boolean on = parts[2].equalsIgnoreCase("on") || parts[2].equalsIgnoreCase("true");
                    config.setXpFarmEnabled(on);
                    sendFeedback("§6[Shinigami] §7XP Farm: " + (on ? "§aON" : "§cOFF"));
                } else {
                    sendFeedback("§6[Shinigami] §7XP Farm: " + (config.isXpFarmEnabled() ? "§aON" : "§cOFF"));
                }
            }
            case "repair" -> {
                if (parts.length >= 3) {
                    switch (parts[2].toLowerCase()) {
                        case "auto" -> config.setRepairMode(shinigami.system.DurabilityManager.RepairMode.AUTO);
                        case "switch" -> config.setRepairMode(shinigami.system.DurabilityManager.RepairMode.SWITCH);
                        case "mend" -> config.setRepairMode(shinigami.system.DurabilityManager.RepairMode.MEND);
                        case "ask" -> config.setRepairMode(shinigami.system.DurabilityManager.RepairMode.ASK);
                        case "continue" -> config.setRepairMode(shinigami.system.DurabilityManager.RepairMode.CONTINUE);
                        default -> sendFeedback("§7Modes: auto, switch, mend, ask, continue");
                    }
                    sendFeedback("§6[Shinigami] §7Repair mode: §e" + config.getRepairMode());
                }
            }
            case "durability" -> {
                if (parts.length >= 3) {
                    try {
                        double threshold = Double.parseDouble(parts[2]);
                        config.setDurabilityThreshold(Math.max(0, Math.min(1, threshold)));
                        sendFeedback("§6[Shinigami] §7Durability threshold: §e" + String.format("%.0f%%", config.getDurabilityThreshold() * 100));
                    } catch (NumberFormatException e) {
                        sendFeedback("§cUsage: &durability <0.0-1.0>");
                    }
                }
            }
            case "help" -> {
                sendFeedback("§6=== Shinigami Commands ===");
                sendFeedback("§7All native Baritone §e&§7commands also work alongside these.");
                sendFeedback("§e&chase <name> §7- Chase a player");
                sendFeedback("§e&kill <name> §7- Chase and kill a player");
                sendFeedback("§e&follow <name> §7- Follow a player");
                sendFeedback("§e&hunt <mobtype> §7- Hunt mobs (zombie, creeper, etc)");
                sendFeedback("§e&area define <name> [x1 y1 z1 x2 y2 z2]§7- Define area");
                sendFeedback("§e&area list §7- List areas");
                sendFeedback("§e&area goto <name> §7- Navigate to area");
                sendFeedback("§e&area remove <name> §7- Remove area");
                sendFeedback("§e&portal build <x y z> §7- Build portal at coords");
                sendFeedback("§e&portal link <nx ny nz> <ox oy oz> §7- Link portals");
                sendFeedback("§e&portal connect <ox oy oz> §7- Build nether portal to coords");
                sendFeedback("§e&xpfarm <on/off> §7- Toggle auto XP farm");
                sendFeedback("§e&repair <mode> §7- auto/switch/mend/ask/continue");
                sendFeedback("§e&durability <0.0-1.0> §7- Set durability threshold");
                sendFeedback("§e&targets §7- Show current target and nearby entities");
                sendFeedback("§e&scan §7- Force a manual entity scan");
                sendFeedback("§e&cleartarget §7- Clear current target and stop chase");
                sendFeedback("§e&clear §7- Clear all tags");
                sendFeedback("§e&status §7- Show current state");
                sendFeedback("§e&toggle §7- Toggle aim assist");
                sendFeedback("§e&gui §7- Open config GUI");
                sendFeedback("§eR §7- Toggle (keyboard)");
                sendFeedback("§eG §7- GUI (keyboard)");
            }
            case "targets" -> handleTargetsCommand();
            case "scan" -> handleScanCommand();
            case "cleartarget" -> {
                module.getTargetManager().clearTarget();
                module.getChaseBehavior().reset();
                module.setChaseMode(false);
                module.setChaseKill(false);
                module.setCurrentChaseTarget(null);
                config.setPvpMode(false);
                config.setCritMode(false);
                config.setMaceMode(false);
                config.setComboMode(false);
                module.setEnabled(false);
                mc.options.keyAttack.setDown(false);
                mc.options.keyUse.setDown(false);
                sendFeedback("§7Target cleared — all combat modes disabled");
            }
            default -> sendFeedback("Unknown command. Use &help");
        }
    }

    private void handleTargetsCommand() {
        baritone.api.aimassist.IAimTarget target = module.getCurrentTarget();
        if (target != null) {
            LivingEntity e = target.getEntity();
            sendFeedback("§6--- Current Target ---");
            sendFeedback("§e" + e.getName().getString() + " §7| HP: §c" + String.format("%.1f", e.getHealth()) + "§7/§4" + String.format("%.1f", e.getMaxHealth()));
            sendFeedback("§7Dist: §f" + String.format("%.1f", target.getDistance()) + "m §7| Angle: §f" + String.format("%.1f", target.getAngleDifference()) + "°");
            sendFeedback("§7Score: §f" + String.format("%.2f", target.getPriorityScore()));
        } else {
            sendFeedback("§7No current target");
        }

        // Manual scan to show nearby entities
        if (mc.level != null && mc.player != null) {
            double range = config.getRange();
            double fov = config.getFOV();
            boolean requireLoS = config.isRequireLineOfSight();
            sendFeedback("§6--- Scan Config ---");
            sendFeedback("§7Range: §f" + range + "m §7FOV: §f" + fov + "° §7LoS: " + (requireLoS ? "§aYES" : "§7NO"));
            sendFeedback("§7Priority: §f" + config.getPriorityMode());

            double scanRange = Math.max(range, 64) + 16;
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                mc.player.getX() - scanRange, mc.player.getY() - scanRange, mc.player.getZ() - scanRange,
                mc.player.getX() + scanRange, mc.player.getY() + scanRange, mc.player.getZ() + scanRange
            );
            java.util.List<net.minecraft.world.entity.Entity> all = mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box);
            int valid = 0;
            for (net.minecraft.world.entity.Entity e : all) {
                if (e instanceof LivingEntity living && living != mc.player && living.isAlive()) {
                    valid++;
                }
            }
            sendFeedback("§7Entities in range: §f" + all.size() + " §7(Living: §f" + valid + "§7)");

            // Show first 5 valid targets
            int shown = 0;
            for (net.minecraft.world.entity.Entity e : all) {
                if (shown >= 5) break;
                if (e instanceof LivingEntity living && living != mc.player && living.isAlive()) {
                    double d = mc.player.distanceTo(living);
                    String name = e.hasCustomName() ? e.getCustomName().getString() : e.getName().getString();
                    String hp = String.format("%.0f", living.getHealth());
                    String tag = "";
                    if (e instanceof net.minecraft.world.entity.monster.Enemy) tag = " §c[hostile]";
                    else if (e instanceof net.minecraft.world.entity.animal.Animal) tag = " §a[passive]";
                    else if (e instanceof Player) tag = " §b[player]";
                    sendFeedback(" §7- §f" + name + "§7: §a" + hp + "HP §7@ §f" + String.format("%.1f", d) + "m" + tag);
                    shown++;
                }
            }
            if (valid > 5) {
                sendFeedback(" §7... and §f" + (valid - 5) + " §7more");
            }
        }
    }

    private void handleScanCommand() {
        if (mc.level == null || mc.player == null) {
            sendFeedback("§cCan't scan: no level or player");
            return;
        }
        module.getTargetManager().forceScan();
        baritone.api.aimassist.IAimTarget target = module.getCurrentTarget();
        if (target != null) {
            sendFeedback("§6[Scan] §aTarget acquired: §e" + target.getEntity().getName().getString() + " §7@ §f" + String.format("%.1f", target.getDistance()) + "m");
        } else {
            sendFeedback("§6[Scan] §7No target found after scan");
        }
    }

    private void handleAreaCommand(String cmd) {
        String[] parts = cmd.split("\\s+");
        if (parts.length < 3) {
            sendFeedback(module.getAreaManager().listAreas());
            return;
        }

        String sub = parts[2].toLowerCase();
        switch (sub) {
            case "define" -> {
                if (parts.length >= 9) {
                    try {
                        int x1 = Integer.parseInt(parts[3]), y1 = Integer.parseInt(parts[4]), z1 = Integer.parseInt(parts[5]);
                        int x2 = Integer.parseInt(parts[6]), y2 = Integer.parseInt(parts[7]), z2 = Integer.parseInt(parts[8]);
                        String name = parts.length >= 10 ? parts[9] : "area_" + System.currentTimeMillis();
                        module.getAreaManager().defineArea(name, x1, y1, z1, x2, y2, z2);
                        sendFeedback("§6[Shinigami] §aArea §e" + name + " §adefined");
                    } catch (NumberFormatException e) {
                        sendFeedback("§cUsage: &area define <x1> <y1> <z1> <x2> <y2> <z2> [name]");
                    }
                } else {
                    String name = parts.length >= 4 ? parts[3] : "base";
                    module.getAreaManager().defineArea(name);
                    sendFeedback("§6[Shinigami] §aArea §e" + name + " §adefined at current position");
                }
            }
            case "goto" -> {
                if (parts.length < 4) {
                    sendFeedback("§cUsage: &area goto <name>");
                    return;
                }
                module.getAreaManager().goToArea(parts[3]);
            }
            case "list" -> sendFeedback(module.getAreaManager().listAreas());
            case "remove" -> {
                if (parts.length < 4) {
                    sendFeedback("§cUsage: &area remove <name>");
                    return;
                }
                if (module.getAreaManager().removeArea(parts[3])) {
                    sendFeedback("§6[Shinigami] §7Area §e" + parts[3] + " §7removed");
                } else {
                    sendFeedback("§cArea not found: " + parts[3]);
                }
            }
            default -> sendFeedback(module.getAreaManager().listAreas());
        }
    }

    private void handlePortalCommand(String cmd) {
        String[] parts = cmd.split("\\s+");
        if (parts.length < 3) {
            sendFeedback("§6--- Portal Commands ---");
            sendFeedback("§e&portal build <x> <y> <z> §7- Build portal at coords");
            sendFeedback("§e&portal link <nx> <ny> <nz> <ox> <oy> <oz> §7- Link nether<->overworld portals");
            sendFeedback("§e&portal connect <x> <y> <z> §7- Build nether portal linking to overworld coords");
            return;
        }

        String sub = parts[2].toLowerCase();
        try {
            switch (sub) {
                case "build" -> {
                    if (parts.length < 6) {
                        sendFeedback("§cUsage: &portal build <x> <y> <z>");
                        return;
                    }
                    int x = Integer.parseInt(parts[3]), y = Integer.parseInt(parts[4]), z = Integer.parseInt(parts[5]);
                    module.getPortalManager().buildPortalAt(new net.minecraft.core.BlockPos(x, y, z));
                }
                case "connect" -> {
                    if (parts.length < 6) {
                        sendFeedback("§cUsage: &portal connect <ox> <oy> <oz> (builds nether portal linking to these overworld coords)");
                        return;
                    }
                    int ox = Integer.parseInt(parts[3]), oy = Integer.parseInt(parts[4]), oz = Integer.parseInt(parts[5]);
                    module.getPortalManager().buildLinkedPortal("nether", new net.minecraft.core.BlockPos(ox, oy, oz));
                }
                case "link" -> {
                    if (parts.length < 9) {
                        sendFeedback("§cUsage: &portal link <nx> <ny> <nz> <ox> <oy> <oz>");
                        return;
                    }
                    int nx = Integer.parseInt(parts[3]), ny = Integer.parseInt(parts[4]), nz = Integer.parseInt(parts[5]);
                    int ox = Integer.parseInt(parts[6]), oy = Integer.parseInt(parts[7]), oz = Integer.parseInt(parts[8]);
                    boolean fromNether = getCurrentDimension().equals("nether");
                    module.getPortalManager().linkPortals(
                        new net.minecraft.core.BlockPos(nx, ny, nz),
                        new net.minecraft.core.BlockPos(ox, oy, oz),
                        fromNether ? "nether" : "overworld"
                    );
                }
                default -> sendFeedback("Unknown portal command");
            }
        } catch (NumberFormatException e) {
            sendFeedback("§cInvalid coordinates. Use integers.");
        }
    }

    private String getCurrentDimension() {
        if (mc.level == null) return "unknown";
        var dim = mc.level.dimension();
        if (dim == net.minecraft.world.level.Level.NETHER) return "nether";
        if (dim == net.minecraft.world.level.Level.OVERWORLD) return "overworld";
        return "end";
    }

    private void sendFeedback(String msg) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(msg)
            );
        }
    }
}
