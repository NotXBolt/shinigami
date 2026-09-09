package shinigami.combat;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import shinigami.AimAssistConfig;
import baritone.api.utils.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

public class CombatPeripherals {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    public CombatPeripherals(AimAssistConfig config) {
        this.config = config;
    }

    // === CRYSTAL ASSIST ===
    public void crystalAssist() {
        if (!config.isPvpMode()) return;
        if (mc.player == null || mc.level == null) return;

        // Find nearest end crystal
        Entity nearestCrystal = null;
        double nearestDist = 6.0;

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            mc.player.getX() - 8, mc.player.getY() - 8, mc.player.getZ() - 8,
            mc.player.getX() + 8, mc.player.getY() + 8, mc.player.getZ() + 8
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
            if (e.getType() == net.minecraft.world.entity.EntityType.END_CRYSTAL) {
                double dist = mc.player.distanceTo(e);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestCrystal = e;
                }
            }
        }

        if (nearestCrystal != null) {
            // Aim at crystal
            Vec3 crystalPos = nearestCrystal.position();
            Vec3 eyePos = mc.player.getEyePosition();
            Vec3 diff = crystalPos.subtract(eyePos);
            float yaw = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90);
            float pitch = (float)(-Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x*diff.x + diff.z*diff.z))));

            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.options.keyAttack.setDown(true);
        } else {
            // Place crystal if holding one and near enemy
            if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) {
                LivingEntity target = findNearestEnemy(4.0);
                if (target != null) {
                    BlockPos placePos = target.blockPosition().above();
                    if (mc.level.getBlockState(placePos).isAir()) {
                        // Place crystal on obsidian/bedrock
                        BlockPos below = placePos.below();
                        if (mc.level.getBlockState(below).is(Blocks.OBSIDIAN) ||
                            mc.level.getBlockState(below).is(Blocks.BEDROCK)) {
                            placeBlock(placePos);
                        }
                    }
                }
            }
        }
    }

    // === SHIELD BREAK TIMING ===
    public boolean shouldAxeShield(LivingEntity target) {
        if (target == null || !(target instanceof Player)) return false;
        if (!target.isUsingItem()) return false;
        ItemStack using = target.getUseItem();
        if (!(using.getItem() instanceof ShieldItem)) return false;

        ItemStack mainHand = mc.player.getMainHandItem();
        if (!mainHand.is(ItemTags.AXES)) return false;

        // Perfect timing: wait 5 ticks after they start blocking
        return target.getTicksUsingItem() >= 5;
    }

    // === AUTO GEAR ===
    public void autoGear() {
        if (mc.player == null) return;
        if (mc.player.tickCount % 20 != 0) return;

        // Check for better armor in inventory
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (isBetterArmor(stack)) {
                swapGear(slot, stack);
                return;
            }
        }

        // Check for better weapons
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (isBetterWeapon(stack)) {
                swapGear(slot, stack);
                return;
            }
        }
    }

    // === AUTO LOOT ===
    public void autoLoot() {
        if (mc.player == null || mc.level == null) return;
        if (!config.isAutoMode()) return;

        // Pick up nearby items
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            mc.player.getX() - 4, mc.player.getY() - 4, mc.player.getZ() - 4,
            mc.player.getX() + 4, mc.player.getY() + 4, mc.player.getZ() + 4
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
            if (e instanceof ItemEntity item) {
                if (mc.player.distanceTo(item) < 3.0 && !item.hasPickUpDelay()) {
                    // Items are picked up automatically by Minecraft
                }
            }
        }

        // TODO: ServerboundContainerOpenPacket does not exist in 26.1. Chest auto-loot is disabled.
    }

    // === AUTO SMELT ===
    public void autoSmelt() {
        if (mc.player == null || mc.level == null) return;
        if (!config.isAutoMode()) return;

        for (net.minecraft.world.level.block.entity.BlockEntity be : new java.util.ArrayList<net.minecraft.world.level.block.entity.BlockEntity>()) { // TODO: mc.level.blockEntityList doesn't exist
            if (be instanceof FurnaceBlockEntity furnace) {
                if (mc.player.distanceToSqr(Vec3.atCenterOf(furnace.getBlockPos())) < 25) {
                    // Check if we can take cooked food
                    if (!furnace.getItem(2).isEmpty()) { // Output slot
                        mc.gameMode.handleContainerInput(
                            mc.player.inventoryMenu.containerId,
                            2,
                            0,
                            net.minecraft.world.inventory.ContainerInput.PICKUP,
                            mc.player
                        );
                    }
                }
            }
        }
    }

    // === BARITONE SURFACE IMPROVEMENT ===
    public void improvedGetToSurface() {
        if (mc.player == null) return;
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        // Try multiple strategies for getting to surface
        BlockPos pos = mc.player.blockPosition();
        int surfaceY = mc.level.getHeight();

        // Strategy 1: Find nearest open area with skylight
        for (int r = 1; r <= 20; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos check = pos.offset(dx, 0, dz);
                    if (mc.level.canSeeSky(check)) {
                        baritone.getCustomGoalProcess().setGoalAndPath(
                            new baritone.api.pathing.goals.GoalNear(check, 3)
                        );
                        return;
                    }
                }
            }
        }

        // Strategy 2: Dig up staircase
        baritone.getCustomGoalProcess().setGoalAndPath(
            new baritone.api.pathing.goals.GoalYLevel(surfaceY - 1)
        );
    }

    private LivingEntity findNearestEnemy(double range) {
        if (mc.level == null || mc.player == null) return null;
        double closest = range;
        LivingEntity result = null;
        double scanRange = range + 8;
        AABB box = new AABB(
            mc.player.getX() - scanRange, mc.player.getY() - scanRange, mc.player.getZ() - scanRange,
            mc.player.getX() + scanRange, mc.player.getY() + scanRange, mc.player.getZ() + scanRange
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
            if (e instanceof LivingEntity living && living != mc.player) {
                double dist = mc.player.distanceTo(living);
                if (dist < closest) {
                    closest = dist;
                    result = living;
                }
            }
        }
        return result;
    }

    private void placeBlock(BlockPos pos) {
        if (mc.gameMode == null) return;
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(pos), Direction.UP, pos, false
        );
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    private boolean isBetterArmor(ItemStack stack) {
        if (mc.player == null) return false;

        net.minecraft.world.entity.EquipmentSlot slot = getSlotForArmor(stack);
        if (slot == null) return false;

        ItemStack current = mc.player.getItemBySlot(slot);
        if (current.isEmpty()) return true;

        // TODO: Implement proper armor comparison using component data
        return false;
    }

    private boolean isBetterWeapon(ItemStack stack) {
        if (mc.player == null) return false;
        ItemStack current = mc.player.getMainHandItem();
        if (current.isEmpty()) return true;

        double dmg = getWeaponDamage(stack);
        double currentDmg = getWeaponDamage(current);
        return dmg > currentDmg;
    }

    private double getWeaponDamage(ItemStack stack) {
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
            return getAttackDamage(stack);
        }
        return 0;
    }

    private void swapGear(int slot, ItemStack stack) {
        if (mc.gameMode == null || mc.player == null) return;
        int targetSlot;
        net.minecraft.world.entity.EquipmentSlot equipSlot = getSlotForArmor(stack);
        if (equipSlot != null) {
            targetSlot = switch (equipSlot) {
                case HEAD -> 39;
                case CHEST -> 38;
                case LEGS -> 37;
                case FEET -> 36;
                default -> mc.player.getInventory().getSelectedSlot();
            };
        } else {
            targetSlot = mc.player.getInventory().getSelectedSlot();
        }

        mc.gameMode.handleContainerInput(
            mc.player.inventoryMenu.containerId,
            slot < 9 ? slot + 36 : slot,
            targetSlot,
            net.minecraft.world.inventory.ContainerInput.SWAP,
            mc.player
        );
    }

    private net.minecraft.world.entity.EquipmentSlot getSlotForArmor(ItemStack stack) {
        if (stack.is(ItemTags.HEAD_ARMOR)) return net.minecraft.world.entity.EquipmentSlot.HEAD;
        if (stack.is(ItemTags.CHEST_ARMOR)) return net.minecraft.world.entity.EquipmentSlot.CHEST;
        if (stack.is(ItemTags.LEG_ARMOR)) return net.minecraft.world.entity.EquipmentSlot.LEGS;
        if (stack.is(ItemTags.FOOT_ARMOR)) return net.minecraft.world.entity.EquipmentSlot.FEET;
        return null;
    }

    private double getAttackDamage(ItemStack stack) {
        var attr = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attr != null) {
            for (var entry : attr.modifiers()) {
                if (entry.attribute() == Attributes.ATTACK_DAMAGE) {
                    return entry.modifier().amount();
                }
            }
        }
        return 0.0;
    }
}
