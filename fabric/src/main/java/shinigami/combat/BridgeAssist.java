package shinigami.combat;

import shinigami.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;

/**
 * God bridge assist.
 * Automatically places blocks at feet while walking forward for perfect bridging.
 */
public class BridgeAssist {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean active = false;
    private int placeCooldown = 0;
    private boolean wasBridging = false;

    private static final int PLACE_COOLDOWN_TICKS = 2;

    public BridgeAssist(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (!active || mc.player == null || mc.level == null) return;

        LocalPlayer player = mc.player;

        // Check if player is shifting (sneaking) and moving forward - classic bridge stance
        boolean isBridging = player.isShiftKeyDown() &&
                             (mc.options.keyUp.isDown());

        if (!isBridging) {
            wasBridging = false;
            return;
        }

        // Check if we're over air (need to place a block)
        BlockPos belowFeet = player.blockPosition().below();
        boolean needsBlock = mc.level.getBlockState(belowFeet).isAir();

        if (!needsBlock) {
            wasBridging = false;
            return;
        }

        // Check cooldown
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        // Find a block in hotbar
        int slot = findBlockInHotbar();
        if (slot == -1) return;

        // Switch to slot
        player.getInventory().setSelectedSlot(slot);

        // Place block at feet
        placeBlock(belowFeet);
        placeCooldown = PLACE_COOLDOWN_TICKS;
        wasBridging = true;
    }

    private int findBlockInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    private void placeBlock(BlockPos pos) {
        if (mc.gameMode == null) return;

        // Find the best face to place against
        BlockPos placeAgainst = pos.above(); // Place against block above
        Direction direction = Direction.DOWN;

        Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, direction, placeAgainst, false);

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    public void setActive(boolean a) { this.active = a; }
    public boolean isActive() { return active; }
}
