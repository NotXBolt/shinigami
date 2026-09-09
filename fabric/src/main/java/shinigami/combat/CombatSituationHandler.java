package shinigami.combat;

import shinigami.AimAssistConfig;
import shinigami.AimAssistMod;
import shinigami.util.KeyMovementController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CombatSituationHandler {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config = AimAssistConfig.getInstance();
    private final OpponentScanner scanner = new OpponentScanner();

    private boolean active = false;
    private boolean axeReady = false;
    private int shieldBreakCooldown = 0;
    private boolean hasAxeInHotbar = false;
    private boolean hasSwordInHotbar = false;

    public enum CombatStrategy {
        NORMAL, SHIELD_BREAK, BACKSTAB, BOW_APPROACH, PEARL_CLOSE, EATING_PUNISH
    }

    private CombatStrategy currentStrategy = CombatStrategy.NORMAL;

    public void tick(LivingEntity target) {
        if (!active || target == null || mc.player == null) return;
        scanner.scan(target);
        if (shieldBreakCooldown > 0) shieldBreakCooldown--;

        scanHotbar();

        if (mc.player.isBlocking() && scanner.isAttacking()) {
            handleShieldStare(target);
            return;
        }

        if (scanner.isEatingOrDrinking()) {
            currentStrategy = CombatStrategy.EATING_PUNISH;
            punishEating(target);
            return;
        }

        if (scanner.isDrawingBow()) {
            currentStrategy = CombatStrategy.BOW_APPROACH;
            approachBow(target);
            return;
        }

        if (scanner.isShieldUp()) {
            if (hasAxeInHotbar && shieldBreakCooldown <= 0) {
                currentStrategy = CombatStrategy.SHIELD_BREAK;
                performShieldBreak(target);
            } else {
                currentStrategy = CombatStrategy.BACKSTAB;
                performBackstab(target);
            }
            return;
        }

        if (scanner.isAttacking() || scanner.isShieldDown()) {
            currentStrategy = CombatStrategy.NORMAL;
        }
    }

    private void scanHotbar() {
        hasAxeInHotbar = false;
        hasSwordInHotbar = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WOODEN_AXE) || stack.is(Items.STONE_AXE) || stack.is(Items.IRON_AXE)
                || stack.is(Items.GOLDEN_AXE) || stack.is(Items.DIAMOND_AXE) || stack.is(Items.NETHERITE_AXE)) {
                hasAxeInHotbar = true;
            }
            if (stack.is(Items.WOODEN_SWORD) || stack.is(Items.STONE_SWORD) || stack.is(Items.IRON_SWORD)
                || stack.is(Items.GOLDEN_SWORD) || stack.is(Items.DIAMOND_SWORD) || stack.is(Items.NETHERITE_SWORD)) {
                hasSwordInHotbar = true;
            }
        }
    }

    private void switchToItem(ItemStack item) {
        if (mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (ItemStack.isSameItem(stack, item)) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private void switchToAxe() {
        if (mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WOODEN_AXE) || stack.is(Items.STONE_AXE) || stack.is(Items.IRON_AXE)
                || stack.is(Items.GOLDEN_AXE) || stack.is(Items.DIAMOND_AXE) || stack.is(Items.NETHERITE_AXE)) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private void switchToSword() {
        if (mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WOODEN_SWORD) || stack.is(Items.STONE_SWORD) || stack.is(Items.IRON_SWORD)
                || stack.is(Items.GOLDEN_SWORD) || stack.is(Items.DIAMOND_SWORD) || stack.is(Items.NETHERITE_SWORD)) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private void performShieldBreak(LivingEntity target) {
        if (mc.player == null) return;
        switchToAxe();
        double dist = mc.player.distanceTo(target);
        if (dist <= config.getRange() && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
            shieldBreakCooldown = 100;
            axeReady = false;
            switchToSword();
        }
    }

    private void performBackstab(LivingEntity target) {
        if (mc.player == null) return;
        Vec3 toTarget = target.position().subtract(mc.player.position()).normalize();
        Vec3 targetLook = target.getLookAngle();
        boolean behind = toTarget.dot(targetLook) < -0.3;
        if (behind) {
            switchToSword();
            double dist = mc.player.distanceTo(target);
            if (dist <= config.getRange() && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
                if (!mc.player.onGround()) {
                    mc.gameMode.attack(mc.player, target);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                } else {
                    mc.player.jumpFromGround();
                }
            }
        } else {
            Vec3 around = new Vec3(-toTarget.z, 0, toTarget.x);
            KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
            if (ctrl != null) {
                ctrl.moveToward(target.position().add(around.scale(2)), true, mc.player.onGround(), false);
            }
        }
    }

    private void approachBow(LivingEntity target) {
        if (mc.player == null) return;
        boolean hasShield = false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.SHIELD)) hasShield = true;
        }
        if (hasShield) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).is(Items.SHIELD)) {
                    mc.player.getInventory().setSelectedSlot(i);
                    break;
                }
            }
        }
        KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
        if (ctrl != null) {
            Vec3 perp = new Vec3(-(target.getX() - mc.player.getX()), 0, -(target.getZ() - mc.player.getZ()));
            if (perp.lengthSqr() > 0.01) perp = perp.normalize();
            Vec3 toward = new Vec3(target.getX() - mc.player.getX(), 0, target.getZ() - mc.player.getZ()).normalize();
            Vec3 zigzag = toward.add(perp.scale(0.5)).normalize();
            ctrl.moveToward(zigzag, true, false, false);
        }
    }

    private void punishEating(LivingEntity target) {
        if (mc.player == null) return;
        switchToSword();
        double dist = mc.player.distanceTo(target);
        if (dist <= config.getRange() && mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
        if (ctrl != null && dist > 2) {
            Vec3 toward = new Vec3(target.getX() - mc.player.getX(), 0, target.getZ() - mc.player.getZ()).normalize();
            ctrl.moveToward(toward, true, true, false);
        }
    }

    private void handleShieldStare(LivingEntity target) {
        if (mc.player == null) return;
        double dist = mc.player.distanceTo(target);
        if (dist < 2) {
            mc.player.jumpFromGround();
            double yaw = Math.toDegrees(Math.atan2(-(target.getX() - mc.player.getX()), target.getZ() - mc.player.getZ()));
            mc.player.setYRot((float)yaw);
            if (!mc.player.onGround()) {
                mc.gameMode.attack(mc.player, target);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        } else if (hasAxeInHotbar && dist <= config.getRange()) {
            performShieldBreak(target);
        } else {
            Vec3 toTarget = target.position().subtract(mc.player.position()).normalize();
            Vec3 around = new Vec3(-toTarget.z, 0, toTarget.x);
            Vec3 behindPos = target.position().add(around.scale(2));
            KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
            if (ctrl != null) {
                ctrl.moveToward(behindPos, true, true, false);
            }
        }
    }

    public void setActive(boolean a) { this.active = a; }
    public OpponentScanner getScanner() { return scanner; }
    public CombatStrategy getCurrentStrategy() { return currentStrategy; }
    public boolean isAxeReady() { return axeReady; }
    public boolean isActive() { return active; }
}
