package baritone.aimassist.intelligence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;

/**
 * WeaponCombatLogic — per-weapon combat strategies.
 * Each weapon has specific mechanics:
 *
 * SWORD: Standard melee, critical hits on fall/jump, sweep attack
 * AXE:   Higher damage, can break shields, spin attack possible
 * MACE:  Smashes on fall (fallDistance > minSmashHeight), wind burst
 * BOW:   Ranged, charge time for power, projectile prediction
 * CROSSBOW: Ranged, needs charging, rapid fire with Quick Charge
 * TRIDENT: Ranged throw + melee, returns after throw
 * SPEAR:  Extended reach (exploited via attribute swap)
 */
public class WeaponCombatLogic {

    private static final Minecraft mc = Minecraft.getInstance();

    // Weapon-specific parameters
    private static final double AXE_DAMAGE_BONUS = 1.3;
    private static final double SWORD_DAMAGE_BONUS = 1.0;
    private static final double MACE_FALL_DAMAGE_MULT = 1.5;
    private static final double SPEAR_REACH = 4.0; // Extended reach exploit
    private static final double BOW_MIN_CHARGE_TICKS = 20;
    private static final double BOW_MAX_CHARGE_TICKS = 60;

    /**
     * Determine if we should attack with the held weapon.
     */
    public static boolean shouldAttack(ItemStack held, LivingEntity target) {
        if (mc.player == null || target == null) return false;
        double dist = mc.player.distanceTo(target);
        float str = mc.player.getAttackStrengthScale(0.5f);

        if (held.is(Items.BOW) || held.is(Items.CROSSBOW)) {
            return dist > 5 && str >= 1.0;
        }
        if (held.is(Items.MACE)) {
            return mc.player.fallDistance > 1.5 && str >= 0.9;
        }
        if (held.is(net.minecraft.tags.ItemTags.SWORDS) || held.is(net.minecraft.tags.ItemTags.AXES)) {
            return dist <= 3.5 && str >= 0.9;
        }
        if (held.is(Items.TRIDENT)) {
            return dist <= 5 && str >= 0.9;
        }
        return str >= 0.9;
    }

    /**
     * Determine if we should use the held weapon for shield break.
     */
    public static boolean shouldShieldBreak(ItemStack held, LivingEntity target) {
        if (mc.player == null || target == null) return false;
        if (!target.isUsingItem()) return false;
        ItemStack targetItem = target.getUseItem();
        if (!(targetItem.getItem() instanceof net.minecraft.world.item.ShieldItem)) return false;

        // Axe is best for shield break
        if (held.is(net.minecraft.tags.ItemTags.AXES)) return true;
        // Spear (attribute swap exploit) for extended reach shield break
        return false;
    }

    /**
     * Calculate the optimal attack timing based on weapon.
     */
    public static int getAttackCooldown(ItemStack held) {
        if (mc.player == null) return 10;
        if (held.is(Items.AXE)) return 8;   // Axes are slower
        if (held.is(Items.MACE)) return 12;  // Mace has longer cooldown after smash
        if (held.is(Items.BOW)) return 30;   // Bow needs charge
        if (held.is(Items.CROSSBOW)) return 25;
        if (held.is(Items.TRIDENT)) return 10;
        return 10; // Sword default
    }

    /**
     * Check if we should use a specific weapon against the target.
     */
    public static int selectWeapon(LivingEntity target) {
        if (mc.player == null || target == null) return -1;
        double dist = mc.player.distanceTo(target);

        // Target at range → bow
        if (dist > 5) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return i;
            }
        }
        // Target using shield → axe
        if (target.isUsingItem() && target.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.is(net.minecraft.tags.ItemTags.AXES)) return i;
            }
        }
        // Default → best melee
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(net.minecraft.tags.ItemTags.AXES)) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(net.minecraft.tags.ItemTags.SWORDS)) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.MACE)) return i;
        }
        return -1;
    }

    /**
     * Check if the spear attribute-swap exploit is available.
     * This extends reach beyond normal sword range.
     */
    public static boolean canUseSpearExploit() {
        if (mc.player == null) return false;
        ItemStack held = mc.player.getMainHandItem();
        // Check if player has speed/strength effects for attribute swap
        boolean hasSpeed = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        boolean hasStrength = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.ATTACK_DAMAGE);
        boolean hasSpeedAmplifier = hasSpeed && mc.player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED).getAmplifier() >= 1;
        return hasSpeedAmplifier || hasStrength;
    }

    /**
     * Check if bow needs charging for optimal shot.
     */
    public static boolean needsBowCharge(ItemStack bow, double targetDist) {
        if (bow == null || mc.player == null) return false;
        return mc.player.getUseItemRemainingTicks() < BOW_MIN_CHARGE_TICKS && targetDist > 8;
    }

    /**
     * Check if crossbow needs quick charge for rapid fire.
     */
    public static boolean needsCrossbowQuickCharge(ItemStack crossbow) {
        if (crossbow == null || mc.player == null) return false;
        return crossbow.is(Items.CROSSBOW) && !net.minecraft.world.item.CrossbowItem.isCharged(crossbow);
    }
}
