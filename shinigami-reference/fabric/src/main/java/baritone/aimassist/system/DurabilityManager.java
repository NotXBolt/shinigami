package baritone.aimassist.system;

import baritone.aimassist.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public class DurabilityManager {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    public enum RepairMode {
        CONTINUE,
        SWITCH,
        MEND,
        ASK,
        AUTO
    }

    private RepairMode currentMode = RepairMode.AUTO;
    private boolean xpFarmShown = false;
    private int lastCheckTick = 0;
    private boolean alerted = false;

    public DurabilityManager(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (mc.player == null) return;
        if (mc.player.tickCount - lastCheckTick < 20) return;
        lastCheckTick = mc.player.tickCount;

        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.isEmpty() || !mainHand.isDamageableItem()) return;

        double durabilityPercent = 1.0 - (double) mainHand.getDamageValue() / mainHand.getMaxDamage();
        double threshold = config.getDurabilityThreshold();

        if (durabilityPercent > threshold) {
            alerted = false;
            return;
        }

        RepairMode mode = config.getRepairMode();
        if (mode == RepairMode.ASK && !alerted) {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §eTool durability low! §7" +
                    String.format("%.0f%%", durabilityPercent * 100) +
                    " §eleft on §f" + mainHand.getDisplayName().getString()
                )
            );
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§7Options: §aContinue §7| §bSwitch §7| §dMend (XP) §7| §cIgnore"
                )
            );
            alerted = true;
            return;
        }

        switch (mode) {
            case SWITCH -> switchTool();
            case MEND -> mendTool(mainHand);
            case AUTO -> handleAuto(durabilityPercent, mainHand);
            default -> {}
        }
    }

    private void handleAuto(double durability, ItemStack tool) {
        if (durability < 0.05) {
            switchTool();
        } else if (hasMendingTool(tool) && hasXP()) {
            mendTool(tool);
        } else if (hasXPFarm()) {
            startXPFarm();
        } else if (!xpFarmShown) {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §7Tool low! Use §e/tag xpfarm <on/off> §7to auto-mend"
                )
            );
            xpFarmShown = true;
        }
    }

    private void switchTool() {
        if (mc.player == null) return;
        int currentSlot = mc.player.getInventory().getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            if (i == currentSlot) continue;
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.isDamageableItem()) {
                double dur = 1.0 - (double) stack.getDamageValue() / stack.getMaxDamage();
                if (dur > 0.5) {
                    mc.player.getInventory().setSelectedSlot(i);
                    mc.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§6[Shinigami] §7Switched to §f" + stack.getDisplayName().getString()
                        )
                    );
                    return;
                }
            }
        }

        // No good backup tool - use mending if possible
        mc.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal("§6[Shinigami] §cNo backup tool found!")
        );
    }

    private void mendTool(ItemStack tool) {
        if (mc.player == null) return;

        // Check for mending enchantment via components
        var ench = tool.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        boolean hasMending = false;
        if (ench != null) {
            hasMending = ench.keySet().stream()
                .anyMatch(e -> ((Holder<Enchantment>) e).is(Enchantments.MENDING));
        }
        if (!hasMending) return;

        // Signal that we need XP
        if (mc.player.experienceLevel > 0 || mc.player.totalExperience > 0) {
            // Holding the tool while gaining XP will auto-mend via Mending
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §7Hold tool while gaining XP to mend"
                )
            );
        } else {
            // Need XP source
            startXPFarm();
        }
    }

    private boolean hasMendingTool(ItemStack tool) {
        var ench = tool.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (ench == null) return false;
        return ench.keySet().stream()
            .anyMatch(e -> ((Holder<Enchantment>) e).is(Enchantments.MENDING));
    }

    private boolean hasXP() {
        return mc.player != null &&
            (mc.player.experienceLevel > 5 || mc.player.totalExperience > 100);
    }

    private boolean hasXPFarm() {
        return config.isXpFarmEnabled();
    }

    private void startXPFarm() {
        if (mc.player == null) return;
        baritone.aimassist.AimAssistModule.getInstance().setFarmMode(true);

        // Navigate to XP farm if defined
        var xpFarm = config.getArea("xp_farm");
        if (xpFarm != null) {
            var center = xpFarm.getCenter();
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone()
                .getCustomGoalProcess().setGoalAndPath(
                    new baritone.api.pathing.goals.GoalBlock(
                        new net.minecraft.core.BlockPos((int)center.x, (int)center.y, (int)center.z)
                    )
                );
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §7Heading to XP farm..."
                )
            );
        } else {
            mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "§6[Shinigami] §7Define XP farm: §e/area define xp_farm <x1> <y1> <z1> <x2> <y2> <z2>"
                )
            );
        }
    }

    public void setXpFarmShown(boolean shown) { this.xpFarmShown = shown; }
    public RepairMode getCurrentMode() { return currentMode; }
}
