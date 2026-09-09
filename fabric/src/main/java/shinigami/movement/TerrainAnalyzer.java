package shinigami.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class TerrainAnalyzer {
    private final Minecraft mc = Minecraft.getInstance();

    public enum Affordance {
        SPRINT_SAFE, JUMPABLE, CLIMBABLE, BRIDGEABLE, HEAD_HIT_RISK, VOID_RISK, OBSTACLE
    }

    public Affordance classifyBlock(BlockPos pos) {
        if (mc.level == null) return Affordance.OBSTACLE;
        BlockState state = mc.level.getBlockState(pos);
        BlockState below = mc.level.getBlockState(pos.below());
        BlockState above = mc.level.getBlockState(pos.above());

        boolean solid = state.isSolid();
        boolean groundSolid = below.isSolid();
        boolean headClear = !above.isSolid() || above.isAir();

        if (!groundSolid) {
            int airBelow = 0;
            BlockPos check = pos.below();
            while (airBelow < 10 && check.getY() > mc.level.getMinY()) {
                if (mc.level.getBlockState(check).isSolid()) break;
                airBelow++;
                check = check.below();
            }
            if (airBelow > 5) return Affordance.VOID_RISK;
            return Affordance.JUMPABLE;
        }

        if (!solid && !headClear) return Affordance.HEAD_HIT_RISK;
        if (solid && headClear && mc.level.getBlockState(pos.above(2)).isAir()) return Affordance.JUMPABLE;
        if (solid && !headClear) return Affordance.OBSTACLE;

        return Affordance.SPRINT_SAFE;
    }

    public boolean isGapAhead(Vec3 lookDir, int distance) {
        if (mc.player == null || mc.level == null || distance < 1) return false;
        int dx = (int) Math.round(Math.signum(lookDir.x));
        int dz = (int) Math.round(Math.signum(lookDir.z));
        if (dx == 0 && dz == 0) return false;

        for (int i = 1; i <= distance; i++) {
            BlockPos check = mc.player.blockPosition().offset(dx * i, -1, dz * i);
            BlockPos at = mc.player.blockPosition().offset(dx * i, 0, dz * i);
            boolean solidBelow = mc.level.getBlockState(check).isSolid();
            boolean passableAbove = mc.level.getBlockState(at).isAir() || !mc.level.getBlockState(at).isSolid();
            if (!solidBelow && passableAbove && i >= 2) return true;
            if (!passableAbove && mc.level.getBlockState(at).isSolid()) return false;
        }
        return false;
    }

    public boolean isOneBlockObstacleAhead(Vec3 lookDir) {
        if (mc.player == null || mc.level == null) return false;
        int dx = (int) Math.round(Math.signum(lookDir.x));
        int dz = (int) Math.round(Math.signum(lookDir.z));
        if (dx == 0 && dz == 0) return false;
        BlockPos ahead = mc.player.blockPosition().offset(dx, 0, dz);
        BlockState state = mc.level.getBlockState(ahead);
        if (state.isSolid() && !state.isAir()) {
            BlockPos above = ahead.above();
            if (mc.level.getBlockState(above).isAir() || !mc.level.getBlockState(above).isSolid()) {
                return mc.level.getBlockState(ahead.above(2)).isAir() || !mc.level.getBlockState(ahead.above(2)).isSolid();
            }
        }
        return false;
    }

    public double getJumpDistance(Vec3 lookDir) {
        if (mc.player == null || mc.level == null) return 0;
        int dx = (int) Math.round(Math.signum(lookDir.x));
        int dz = (int) Math.round(Math.signum(lookDir.z));
        if (dx == 0 && dz == 0) return 0;
        double dist = 0;
        for (int i = 1; i <= 6; i++) {
            BlockPos below = mc.player.blockPosition().offset(dx * i, -1, dz * i);
            if (mc.level.getBlockState(below).isSolid()) break;
            dist = i;
        }
        return dist;
    }

    public boolean hasHeadroom() {
        if (mc.player == null || mc.level == null) return true;
        BlockPos head = mc.player.blockPosition().above(2);
        return mc.level.getBlockState(head).isAir() || !mc.level.getBlockState(head).isSolid();
    }
}
