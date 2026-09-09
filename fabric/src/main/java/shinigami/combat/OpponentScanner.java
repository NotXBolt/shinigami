package shinigami.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class OpponentScanner {

    private final Minecraft mc = Minecraft.getInstance();
    private LivingEntity lastTarget = null;
    private int lastSwingTime = 0;
    private boolean wasBlocking = false;
    private boolean wasUsingItem = false;
    private int lastHurtTime = 0;
    private int ticksSinceLastSwing = 0;
    private int ticksSinceLastBlockChange = 0;

    public enum OpponentAction {
        IDLE, SWINGING_ATTACK, SHIELD_UP, SHIELD_DOWN,
        EATING, DRINKING_POTION, DRAWING_BOW, USING_ROD,
        THROWING_PEARL, SPRINTING, JUMPING, RECENTLY_HIT,
        SWITCHING_WEAPON, BLOCKING_SHIELD
    }

    private OpponentAction detectedAction = OpponentAction.IDLE;
    private ItemStack currentWeapon = ItemStack.EMPTY;
    private ItemStack previousWeapon = ItemStack.EMPTY;

    public void scan(LivingEntity entity) {
        if (entity == null) return;
        lastTarget = entity;

        previousWeapon = currentWeapon;
        currentWeapon = entity.getMainHandItem();

        boolean isPlayer = entity instanceof Player;
        boolean blocking = isPlayer && ((Player)entity).isBlocking();
        boolean usingItem = entity.isUsingItem();
        int swingTime = entity.swingTime;
        int hurtTime = entity.hurtTime;

        if (!wasBlocking && blocking) {
            detectedAction = OpponentAction.SHIELD_UP;
            ticksSinceLastBlockChange = 0;
        } else if (wasBlocking && !blocking) {
            detectedAction = OpponentAction.SHIELD_DOWN;
            ticksSinceLastBlockChange = 0;
        } else if (swingTime > lastSwingTime && swingTime > 0) {
            detectedAction = OpponentAction.SWINGING_ATTACK;
            ticksSinceLastSwing = 0;
        } else if (usingItem && !wasUsingItem) {
            ItemStack useItem = entity.getUseItem();
            if (useItem.has(DataComponents.FOOD)) detectedAction = OpponentAction.EATING;
            else if (useItem.is(Items.BOW) || useItem.is(Items.CROSSBOW)) detectedAction = OpponentAction.DRAWING_BOW;
            else if (useItem.is(Items.POTION) || useItem.is(Items.SPLASH_POTION)) detectedAction = OpponentAction.DRINKING_POTION;
            else if (useItem.is(Items.ENDER_PEARL)) detectedAction = OpponentAction.THROWING_PEARL;
            else detectedAction = OpponentAction.USING_ROD;
        } else if (entity.isSprinting()) {
            detectedAction = OpponentAction.SPRINTING;
        } else if (!entity.onGround()) {
            detectedAction = OpponentAction.JUMPING;
        } else if (hurtTime > 0 && lastHurtTime == 0) {
            detectedAction = OpponentAction.RECENTLY_HIT;
        } else if (!ItemStack.isSameItem(currentWeapon, previousWeapon)) {
            detectedAction = OpponentAction.SWITCHING_WEAPON;
        } else if (blocking) {
            detectedAction = OpponentAction.BLOCKING_SHIELD;
        } else {
            detectedAction = OpponentAction.IDLE;
        }

        wasBlocking = blocking;
        wasUsingItem = usingItem;
        lastSwingTime = swingTime;
        lastHurtTime = hurtTime;
        if (detectedAction == OpponentAction.SWINGING_ATTACK) ticksSinceLastSwing = 0;
        else ticksSinceLastSwing++;
        ticksSinceLastBlockChange++;
    }

    public boolean isAttacking() {
        return detectedAction == OpponentAction.SWINGING_ATTACK;
    }

    public boolean isShieldUp() {
        return detectedAction == OpponentAction.SHIELD_UP || detectedAction == OpponentAction.BLOCKING_SHIELD;
    }

    public boolean isShieldDown() {
        return detectedAction == OpponentAction.SHIELD_DOWN;
    }

    public boolean isEatingOrDrinking() {
        return detectedAction == OpponentAction.EATING || detectedAction == OpponentAction.DRINKING_POTION;
    }

    public boolean isDrawingBow() {
        return detectedAction == OpponentAction.DRAWING_BOW;
    }

    public boolean isRecentlyHit() {
        return detectedAction == OpponentAction.RECENTLY_HIT;
    }

    public int getTicksSinceLastSwing() { return ticksSinceLastSwing; }
    public int getTicksSinceLastBlockChange() { return ticksSinceLastBlockChange; }
    public OpponentAction getDetectedAction() { return detectedAction; }
    public ItemStack getCurrentWeapon() { return currentWeapon; }
    public boolean hasWeapon(ItemStack weapon) { return ItemStack.isSameItem(currentWeapon, weapon); }
    public boolean isHoldingSword() { return currentWeapon.is(Items.WOODEN_SWORD) || currentWeapon.is(Items.STONE_SWORD) || currentWeapon.is(Items.IRON_SWORD) || currentWeapon.is(Items.GOLDEN_SWORD) || currentWeapon.is(Items.DIAMOND_SWORD) || currentWeapon.is(Items.NETHERITE_SWORD); }
    public boolean isHoldingAxe() { return currentWeapon.is(Items.WOODEN_AXE) || currentWeapon.is(Items.STONE_AXE) || currentWeapon.is(Items.IRON_AXE) || currentWeapon.is(Items.GOLDEN_AXE) || currentWeapon.is(Items.DIAMOND_AXE) || currentWeapon.is(Items.NETHERITE_AXE); }
    public boolean isHoldingMace() { return currentWeapon.is(Items.MACE); }
    public boolean isHoldingBow() { return currentWeapon.is(Items.BOW) || currentWeapon.is(Items.CROSSBOW); }

    public void reset() {
        lastTarget = null;
        detectedAction = OpponentAction.IDLE;
        currentWeapon = ItemStack.EMPTY;
        previousWeapon = ItemStack.EMPTY;
        wasBlocking = false;
        wasUsingItem = false;
        lastSwingTime = 0;
        lastHurtTime = 0;
        ticksSinceLastSwing = 0;
        ticksSinceLastBlockChange = 0;
    }
}
