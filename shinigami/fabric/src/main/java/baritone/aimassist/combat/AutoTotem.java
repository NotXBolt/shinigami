package baritone.aimassist.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * Auto-totem: automatically places a totem in the offhand when health is critical.
 */
public class AutoTotem {

    private final Minecraft mc = Minecraft.getInstance();
    private boolean active = false;
    private double healthThreshold = 8.0;
    private int lastSwapTick = 0;

    public void tick() {
        if (!active || mc.player == null) return;

        // Check health
        float health = mc.player.getHealth();
        if (health > healthThreshold) return;

        // Check if already holding totem in offhand
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;

        // Cooldown between swaps
        if (mc.player.tickCount - lastSwapTick < 5) return;

        // Find totem in inventory
        int slot = findTotemSlot();
        if (slot == -1) return;

        // Swap totem to offhand
        mc.gameMode.handleContainerInput(
            mc.player.inventoryMenu.containerId,
            slot < 9 ? slot + 36 : slot,
            40, // Offhand slot
            net.minecraft.world.inventory.ContainerInput.SWAP,
            mc.player
        );

        lastSwapTick = mc.player.tickCount;
    }

    private int findTotemSlot() {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    public void setActive(boolean a) { this.active = a; }
    public boolean isActive() { return active; }
    public void setHealthThreshold(double t) { this.healthThreshold = t; }
}
