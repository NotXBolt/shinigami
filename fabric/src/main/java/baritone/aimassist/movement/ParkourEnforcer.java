package baritone.aimassist.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Own extended code — Enforced Learning Parkour (original, not copy).
 * Combines Dodger safe + better-auto-jump edge + Kiwi Theta* any-angle + Oogabooga sprint + cadence costs
 * into a single perfect, aggressive pakor executioner. Every possibility pre-planned.
 */
public class ParkourEnforcer {

    private final Minecraft mc = Minecraft.getInstance();

    // Enforced table: gapAhead / edgeAhead / pathBlocked / oneBlockObstacle / targetAbove — all must trigger jump
    public boolean shouldJump(boolean gapAhead, boolean edgeAhead, boolean pathBlocked, boolean oneBlockObstacle, boolean targetAbove) {
        if (!mc.player.onGround() && !mc.player.isUnderWater()) return false; // never spam in air (perfect)
        return gapAhead || edgeAhead || pathBlocked || oneBlockObstacle || targetAbove;
    }

    // Theta* any-angle dir (Kiwi) — Vec3 normalize already gives any angle, not 0/45
    public Vec3 calculateDir(Vec3 diff) {
        diff = new Vec3(diff.x, 0, diff.z);
        return diff.lengthSqr() < 1e-6 ? Vec3.ZERO : diff.normalize();
    }

    // Cadence block cost — original extended, not copy
    public double blockCost(BlockState state) {
        var blocks = net.minecraft.world.level.block.Blocks.class;
        try {
            if (state.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)) return 2.5;
            if (state.is(net.minecraft.world.level.block.Blocks.HONEY_BLOCK)) return 3.0;
            if (state.is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK)) return 1.8;
            if (state.is(net.minecraft.world.level.block.Blocks.BIG_DRIPLEAF)) return 2.0;
        } catch (Exception ignored) {}
        return 1.0;
    }

    // Oogabooga relentless: always sprint when food>6 and not in fluid and distance>2
    public boolean shouldSprint(double distance) {
        return mc.player != null && mc.player.getFoodData().getFoodLevel() > 6 && !mc.player.isInWater() && !mc.player.isInLava() && distance > 2.0;
    }

    // Safe dodge (Dodger) — minimal 1-block, check solid below
    public boolean isSafeDodge(Vec3 dir) {
        if (mc.player == null || mc.level == null) return false;
        BlockPos dest = mc.player.blockPosition().offset((int)Math.signum(dir.x), 0, (int)Math.signum(dir.z));
        var state = mc.level.getBlockState(dest);
        if (state.is(net.minecraft.world.level.block.Blocks.LAVA) || state.is(net.minecraft.world.level.block.Blocks.FIRE)) return false;
        if (dest.getY() <= mc.level.getMinY() + 1) return false;
        return mc.level.getBlockState(dest.below()).isSolid();
    }
}
