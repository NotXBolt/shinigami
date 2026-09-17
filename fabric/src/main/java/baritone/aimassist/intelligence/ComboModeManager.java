package baritone.aimassist.intelligence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;

/**
 * ComboModeManager — handles weapon combination modes.
 *
 * Combos:
 *   SWORD + BOW  → melee engage, switch to bow at range
 *   AXE + BOW    → axe shield break, bow at range
 *   MACE + SWORD → mace smash, sword after landing
 *   AXE + MACE   → axe for shield break, mace for smash
 *   TRIDENT + BOW → trident throw + bow at range
 *
 * Switch logic:
 *   At range > 5 → switch to ranged weapon
 *   Target using shield → switch to axe
 *   On ground after fall → switch to melee
 *   Low health → switch to best defensive weapon
 */
public class ComboModeManager {

    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * Determine the current combo mode based on held weapons.
     */
    public enum ComboMode {
        SWORD_BOW("sword_bow", "Melee engage + bow at range"),
        AXE_BOW("axe_bow", "Axe shield break + bow at range"),
        MACE_SWORD("mace_sword", "Mace smash + sword follow-up"),
        AXE_MACE("axe_mace", "Axe break shield + mace smash"),
        TRIDENT_BOW("trident_bow", "Trident throw + bow"),
        SINGLE("single", "Single weapon mode");

        private final String name;
        private final String description;

        ComboMode(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    /**
     * Detect current combo mode from inventory.
     */
    public static ComboMode detectComboMode() {
        if (mc.player == null) return ComboMode.SINGLE;
        boolean hasSword = hasItem(net.minecraft.tags.ItemTags.SWORDS);
        boolean hasAxe = hasItem(net.minecraft.tags.ItemTags.AXES);
        boolean hasMace = hasItem(Items.MACE);
        boolean hasBow = hasItem(Items.BOW);
        boolean hasCrossbow = hasItem(Items.CROSSBOW);
        boolean hasTrident = hasItem(Items.TRIDENT);

        if ((hasSword && hasBow) || (hasSword && hasCrossbow)) return ComboMode.SWORD_BOW;
        if ((hasAxe && hasBow) || (hasAxe && hasCrossbow)) return ComboMode.AXE_BOW;
        if (hasMace && hasSword) return ComboMode.MACE_SWORD;
        if (hasAxe && hasMace) return ComboMode.AXE_MACE;
        if (hasTrident && hasBow) return ComboMode.TRIDENT_BOW;
        return ComboMode.SINGLE;
    }

    /**
     * Select the best weapon for the current situation based on combo mode.
     */
    public static int selectBestWeapon(LivingEntity target) {
        if (mc.player == null || target == null) return -1;
        double dist = mc.player.distanceTo(target);
        ComboMode mode = detectComboMode();

        switch (mode) {
            case SWORD_BOW:
                return dist > 5 ? findBowSlot() : findSwordSlot();
            case AXE_BOW:
                if (target.isUsingItem() && target.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem)
                    return findAxeSlot();
                return dist > 5 ? findBowSlot() : findAxeSlot();
            case MACE_SWORD:
                if (mc.player.fallDistance > 1.5) return findMaceSlot();
                return findSwordSlot();
            case AXE_MACE:
                if (target.isUsingItem()) return findAxeSlot();
                return mc.player.fallDistance > 1.5 ? findMaceSlot() : findAxeSlot();
            case TRIDENT_BOW:
                return dist > 5 ? findBowSlot() : findTridentSlot();
            default:
                return WeaponCombatLogic.selectWeapon(target);
        }
    }

    private static boolean hasItem(net.minecraft.world.item.Item item) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }

    private static boolean hasItem(net.minecraft.tags.Tag<Item> tag) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(tag)) return true;
        }
        return false;
    }

    private static int findBowSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return i;
        }
        return -1;
    }

    private static int findSwordSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(net.minecraft.tags.ItemTags.SWORDS)) return i;
        }
        return -1;
    }

    private static int findAxeSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(net.minecraft.tags.ItemTags.AXES)) return i;
        }
        return -1;
    }

    private static int findMaceSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.MACE)) return i;
        }
        return -1;
    }

    private static int findTridentSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.TRIDENT)) return i;
        }
        return -1;
    }
}
