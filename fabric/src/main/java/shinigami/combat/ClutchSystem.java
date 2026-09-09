package shinigami.combat;

import shinigami.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ClutchSystem {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean waterBucketReady = false;
    private int waterSlot = -1;
    private boolean clutchActive = false;
    private long lastClutchTick = 0;

    private static final double WATER_CLUTCH_HEIGHT = 4.0;
    private static final double HAYBALE_CLUTCH_HEIGHT = 8.0;
    private static final double LADDER_CLUTCH_HEIGHT = 3.0;

    public ClutchSystem(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (mc.player == null) return;
        if (!config.isAutoClutch()) return;

        LocalPlayer p = mc.player;
        if (p.onGround() || p.isInWater()) {
            clutchActive = false;
            return;
        }

        double fallDist = p.fallDistance;
        if (fallDist < 2.0) return;

        if (System.currentTimeMillis() - lastClutchTick < 100) return;
        lastClutchTick = System.currentTimeMillis();

        Vec3 vel = p.getDeltaMovement();
        boolean falling = vel.y < -0.5;

        if (!falling) return;

        if (fallDist >= WATER_CLUTCH_HEIGHT && findWaterBucket()) {
            doWaterClutch();
        } else if (fallDist >= HAYBALE_CLUTCH_HEIGHT && findHayBale()) {
            doHayBaleClutch();
        } else if (fallDist >= LADDER_CLUTCH_HEIGHT && findLadder()) {
            doLadderClutch();
        }
    }

    private boolean findWaterBucket() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WATER_BUCKET)) {
                waterSlot = i;
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WATER_BUCKET)) {
                waterSlot = i;
                return true;
            }
        }
        return false;
    }

    private boolean findHayBale() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.HAY_BLOCK)) {
                waterSlot = i;
                return true;
            }
        }
        return false;
    }

    private boolean findLadder() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.LADDER) || stack.is(Items.VINE) || stack.is(Items.TWISTING_VINES)) {
                waterSlot = i;
                return true;
            }
        }
        return false;
    }

    private void doWaterClutch() {
        if (mc.gameMode == null || mc.player == null) return;

        boolean inHotbar = waterSlot < 9;
        if (!inHotbar) {
            swapFromInventory(waterSlot);
            waterSlot = mc.player.getInventory().getSelectedSlot();
        } else {
            mc.player.getInventory().setSelectedSlot(waterSlot);
        }

        BlockPos below = mc.player.blockPosition().below();
        BlockHitResult hit = new BlockHitResult(
            new Vec3(below.getX() + 0.5, below.getY(), below.getZ() + 0.5),
            Direction.UP,
            below,
            false
        );

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        clutchActive = true;
    }

    private void doHayBaleClutch() {
        if (mc.gameMode == null || mc.player == null) return;

        boolean inHotbar = waterSlot < 9;
        if (!inHotbar) {
            swapFromInventory(waterSlot);
            waterSlot = mc.player.getInventory().getSelectedSlot();
        } else {
            mc.player.getInventory().setSelectedSlot(waterSlot);
        }

        BlockPos placeAt = mc.player.blockPosition();
        BlockHitResult hit = new BlockHitResult(
            new Vec3(placeAt.getX() + 0.5, placeAt.getY(), placeAt.getZ() + 0.5),
            Direction.UP,
            placeAt,
            false
        );

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        clutchActive = true;
    }

    private void doLadderClutch() {
        if (mc.gameMode == null || mc.player == null) return;

        boolean inHotbar = waterSlot < 9;
        if (!inHotbar) {
            swapFromInventory(waterSlot);
            waterSlot = mc.player.getInventory().getSelectedSlot();
        } else {
            mc.player.getInventory().setSelectedSlot(waterSlot);
        }

        BlockPos placeAt = mc.player.blockPosition();
        BlockHitResult hit = new BlockHitResult(
            new Vec3(placeAt.getX() + 0.5, placeAt.getY(), placeAt.getZ() + 0.5),
            Direction.UP,
            placeAt,
            false
        );

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        clutchActive = true;
    }

    private void swapFromInventory(int slot) {
        if (mc.gameMode == null) return;
        mc.gameMode.handleContainerInput(
            mc.player.inventoryMenu.containerId,
            slot < 9 ? slot + 36 : slot,
            mc.player.getInventory().getSelectedSlot(),
            net.minecraft.world.inventory.ContainerInput.SWAP,
            mc.player
        );
    }

    public boolean isClutchActive() { return clutchActive; }
}
