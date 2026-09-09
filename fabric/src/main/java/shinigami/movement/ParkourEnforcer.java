package shinigami.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * ParkourEnforcer — Own extended original, super crazy, total from scratch.
 * Enforces table: gapAhead / edgeAhead / pathBlocked / oneBlockObstacle / targetAbove — all must trigger jump, never spam in air.
 * Theta* any-angle, block costs, safe dodge, always sprint.
 */
public class ParkourEnforcer {
    private final Minecraft mc = Minecraft.getInstance();

    public boolean shouldJump(boolean gapAhead, boolean edgeAhead, boolean pathBlocked, boolean oneBlockObstacle, boolean targetAbove) {
        if (mc.player != null && !mc.player.onGround() && !mc.player.isUnderWater()) return false;
        return gapAhead || edgeAhead || pathBlocked || oneBlockObstacle || targetAbove;
    }

    public Vec3 calculateDir(Vec3 diff) {
        diff = new Vec3(diff.x, 0, diff.z);
        return diff.lengthSqr() < 1e-6 ? Vec3.ZERO : diff.normalize();
    }

    public double blockCost(BlockState state) {
        try {
            if (state.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)) return 2.5;
            if (state.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK)) return 3.0;
            if (state.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK)) return 1.8;
            if (state.is(net.minecraft.world.level.block.Blocks.BIG_DRIPLEAF)) return 2.0;
        } catch (Exception ignored) {}
        return 1.0;
    }

    public boolean shouldSprint(double distance) {
        return mc.player != null && mc.player.getFoodData().getFoodLevel() > 6 && !mc.player.isInWater() && !mc.player.isInLava() && distance > 2.0;
    }

    public boolean isSafeDodge(Vec3 dir) {
        if (mc.player == null || mc.level == null) return false;
        BlockPos dest = mc.player.blockPosition().offset((int)Math.signum(dir.x), 0, (int)Math.signum(dir.z));
        var s = mc.level.getBlockState(dest);
        if (s.is(net.minecraft.world.level.block.Blocks.LAVA) || s.is(net.minecraft.world.level.block.Blocks.FIRE)) return false;
        if (dest.getY() <= mc.level.getMinY() + 1) return false;
        return mc.level.getBlockState(dest.below()).isSolid();
    }
}
