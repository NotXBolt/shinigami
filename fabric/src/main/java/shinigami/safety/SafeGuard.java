package shinigami.safety;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * SafeGuard — Never lose progress. Prevents void, lava, fall deaths, keeps inventory safe.
 * Checks before chase/dodge, cancels unsafe moves, triggers clutch/save.
 */
public class SafeGuard {
    private final Minecraft mc = Minecraft.getInstance();

    public boolean isSafeToMove(BlockPos pos) {
        if (mc.level == null) return false;
        if (pos.getY() <= mc.level.getMinY() + 1) return false;
        var state = mc.level.getBlockState(pos);
        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)) return false;
        if (!mc.level.getBlockState(pos.below()).isSolid() && mc.level.getBlockState(pos).isAir()) {
            // check 3 below for void
            boolean hasGround = false;
            for (int i = 1; i <= 4; i++) if (mc.level.getBlockState(pos.below(i)).isSolid()) { hasGround = true; break; }
            if (!hasGround) return false;
        }
        return mc.level.getBlockState(pos.below()).isSolid() || !mc.level.getBlockState(pos).isSolid();
    }

    public boolean shouldAbortChase() {
        if (mc.player == null || mc.level == null) return true;
        if (mc.player.getHealth() / mc.player.getMaxHealth() < 0.25) return true;
        if (mc.player.fallDistance > 8) return true;
        BlockPos p = mc.player.blockPosition();
        if (p.getY() <= mc.level.getMinY() + 4 && mc.player.getDeltaMovement().y < -0.5) return true;
        return false;
    }

    public boolean isInventorySafe() {
        if (mc.player == null) return true;
        // keep at least one totem / water bucket
        return true; // placeholder — logic handled by AutoTotem etc
    }

    public BlockPos findSafeRetreat(int radius) {
        if (mc.player == null || mc.level == null) return null;
        BlockPos center = mc.player.blockPosition();
        BlockPos best = null;
        double bestDist = 0;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos cand = center.offset(dx, 0, dz);
            if (!isSafeToMove(cand)) continue;
            double d = cand.distSqr(center);
            if (d > bestDist) { bestDist = d; best = cand; }
        }
        return best;
    }
}
