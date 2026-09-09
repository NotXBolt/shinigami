package shinigami.combat;

import shinigami.AimAssistConfig;
import shinigami.AimAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;

import java.util.*;

public class AutoInventory {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private int lastFoodCheck = 0;
    private int lastTorchCheck = 0;
    private int torchSlot = -1;

    public AutoInventory(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (mc.player == null || mc.level == null) return;
        if (!config.isAutoMode() && !config.isPvpMode() && !config.isChaseMode()) return;

        autoEat();
        autoTorch();
        autoSwapTools();
    }

    private void autoEat() {
        if (mc.player.tickCount - lastFoodCheck < 10) return;
        lastFoodCheck = mc.player.tickCount;
        if (mc.player.isUsingItem()) return;

        int foodLevel = mc.player.getFoodData().getFoodLevel();
        float saturation = mc.player.getFoodData().getSaturationLevel();

        boolean shouldEat = foodLevel < 10 ||
            (foodLevel < 18 && saturation < 2 && config.isPvpMode());

        if (!shouldEat) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem().components().has(net.minecraft.core.component.DataComponents.FOOD)) {
                mc.player.getInventory().setSelectedSlot(i);
                mc.options.keyUse.setDown(true);
                return;
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem().components().has(net.minecraft.core.component.DataComponents.FOOD)) {
                int emptySlot = findEmptyHotbarSlot();
                if (emptySlot != -1) {
                    mc.gameMode.handleContainerInput(
                        mc.player.inventoryMenu.containerId,
                        i,
                        emptySlot,
                        net.minecraft.world.inventory.ContainerInput.SWAP,
                        mc.player
                    );
                    mc.player.getInventory().setSelectedSlot(emptySlot);
                    mc.options.keyUse.setDown(true);
                    return;
                }
            }
        }
    }

    private void autoTorch() {
        if (mc.player.tickCount - lastTorchCheck < 20) return;
        lastTorchCheck = mc.player.tickCount;

        if (mc.player.isUsingItem()) return;
        if (!mc.player.onGround()) return;

        BlockPos playerPos = mc.player.blockPosition();
        int lightLevel = mc.level.getMaxLocalRawBrightness(playerPos);

        if (lightLevel > 8) return;

        torchSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH)) {
                torchSlot = i;
                break;
            }
        }
        if (torchSlot == -1) return;

        BlockPos placeOn = findPlaceableBlockNearby(playerPos);
        if (placeOn == null) return;

        mc.player.getInventory().setSelectedSlot(torchSlot);

        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(placeOn),
            Direction.UP,
            placeOn,
            false
        );

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    private void autoSwapTools() {
        if (mc.player == null) return;
        if (!mc.options.keyAttack.isDown()) return;

        if (mc.hitResult == null) return;
        if (!(mc.hitResult instanceof BlockHitResult blockHit)) return;

        BlockState targetState = mc.level.getBlockState(blockHit.getBlockPos());
        ItemStack currentTool = mc.player.getMainHandItem();

        int bestSlot = -1;
        float bestSpeed = currentTool.getDestroySpeed(targetState);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            float speed = stack.getDestroySpeed(targetState);

            if (stack.isCorrectToolForDrops(targetState) && !currentTool.isCorrectToolForDrops(targetState)) {
                speed *= 5;
            }

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        if (bestSlot != -1) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
        }
    }

    public void autoElytra() {
        if (mc.player == null) return;
        if (!config.isAutoMode() && !config.isChaseMode()) return;

        if (mc.player.getDeltaMovement().y > -0.5) return;
        if (mc.player.onGround()) return;

        ItemStack chest = mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if (chest.is(Items.ELYTRA)) {
            if (mc.player.fallDistance > 3 && !mc.player.isFallFlying()) {
                mc.options.keyJump.setDown(true);
            }
            return;
        }

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.ELYTRA)) {
                mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId,
                    i < 9 ? i + 36 : i,
                    38,
                    net.minecraft.world.inventory.ContainerInput.SWAP,
                    mc.player
                );
                return;
            }
        }
    }

    public void autoFish() {
        if (mc.player == null) return;
        if (!config.isChaseMode()) return;

        AimAssistModule mod = AimAssistModule.getInstance();
        if (mod.isChaseMode() && mod.getChaseTargetName() != null) return;

        boolean hasRod = false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.FISHING_ROD)) {
                mc.player.getInventory().setSelectedSlot(i);
                hasRod = true;
                break;
            }
        }

        if (!hasRod) return;

        if (!mc.player.isUsingItem()) {
            mc.options.keyUse.setDown(true);
        } else {
            if (mc.player.tickCount % 100 == 0) {
                mc.options.keyUse.setDown(false);
            }
        }
    }

    public void autoDiscardTrash() {
        if (mc.player == null) return;
        if (mc.player.tickCount % 100 != 0) return;

        Set<Item> trash = Set.of(
            Items.ROTTEN_FLESH,
            Items.SPIDER_EYE,
            Items.POISONOUS_POTATO,
            Items.STONE,
            Items.DIRT,
            Items.GRAVEL,
            Items.FLINT,
            Items.FEATHER,
            Items.STRING,
            Items.BONE
        );

        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (trash.contains(stack.getItem()) && stack.getCount() > 16) {
                mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId,
                    i < 9 ? i + 36 : i,
                    0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP,
                    mc.player
                );
                mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId,
                    -999,
                    0,
                    net.minecraft.world.inventory.ContainerInput.PICKUP,
                    mc.player
                );
            }
        }
    }

    public void autoRepair() {
        if (mc.player == null || mc.level == null) return;

        BlockPos anvilPos = findNearestBlock(Blocks.ANVIL, 5);
        if (anvilPos == null) return;

        ItemStack tool = mc.player.getMainHandItem();
        if (tool.isDamaged() && tool.getDamageValue() > tool.getMaxDamage() * 0.7) {
            mc.player.connection.send(
                new ServerboundUseItemOnPacket(
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(anvilPos), Direction.UP, anvilPos, false),
                    0
                )
            );
        }
    }

    public void autoEnchant() {
        if (mc.player == null || mc.level == null) return;

        BlockPos tablePos = findNearestBlock(Blocks.ENCHANTING_TABLE, 5);
        if (tablePos == null) return;

        ItemStack tool = mc.player.getMainHandItem();
        if (tool.isEnchanted()) return;
        if (mc.player.experienceLevel < 30) return;

        mc.player.connection.send(
            new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(tablePos), Direction.UP, tablePos, false),
                0
            )
        );
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private BlockPos findPlaceableBlockNearby(BlockPos center) {
        if (mc.level == null) return null;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    BlockState state = mc.level.getBlockState(check);
                    if (state.isSolid() && !state.isAir()) {
                        BlockPos above = check.above();
                        if (mc.level.getBlockState(above).isAir()) {
                            if (mc.level.getBlockState(check).canOcclude()) {
                                return check;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findNearestBlock(net.minecraft.world.level.block.Block target, int radius) {
        if (mc.level == null || mc.player == null) return null;
        BlockPos playerPos = mc.player.blockPosition();
        double closest = radius * radius;
        BlockPos result = null;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos check = playerPos.offset(dx, dy, dz);
                    if (mc.level.getBlockState(check).is(target)) {
                        double dist = playerPos.distSqr(check);
                        if (dist < closest) {
                            closest = dist;
                            result = check;
                        }
                    }
                }
            }
        }
        return result;
    }
}
