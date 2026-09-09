package shinigami.system;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import shinigami.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class UnderwaterBreathing {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private int airTicks = 0;
    private boolean surfacing = false;
    private BlockPos surfaceTarget = null;
    private int lastCheckTick = 0;

    private static final int TICKS_PER_BUBBLE = 20;
    private static final int SAFETY_MARGIN_TICKS = 30;

    public UnderwaterBreathing(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount - lastCheckTick < 5) return;
        lastCheckTick = mc.player.tickCount;

        if (!mc.player.isInWater()) {
            airTicks = 0;
            surfacing = false;
            surfaceTarget = null;
            return;
        }

        int air = mc.player.getAirSupply();
        int maxAir = mc.player.getMaxAirSupply();
        double airPercent = (double) air / maxAir;

        if (!config.isAutoSurface()) return;

        int breathMargin = config.getBreathMargin();
        double threshold = (breathMargin * TICKS_PER_BUBBLE) / (double) maxAir;

        if (airPercent < threshold && !surfacing) {
            surfacing = true;
            surfaceTarget = findSafeSurfacePoint();

            if (surfaceTarget != null) {
                mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§6[Shinigami] §7Low air! Surfacing..."
                    )
                );
            }
        }

        if (surfacing && surfaceTarget != null) {
            if (air <= 0) {
                // Critical - emergency surface
                emergencySurface();
                return;
            }

            int ticksRemaining = (air * TICKS_PER_BUBBLE) / maxAir;
            int etaTicks = estimateSurfaceTicks();

            if (etaTicks > 0 && ticksRemaining < etaTicks + SAFETY_MARGIN_TICKS) {
                BaritoneAPI.getProvider().getPrimaryBaritone()
                    .getCustomGoalProcess().setGoalAndPath(new GoalBlock(surfaceTarget));
            }

            if (!mc.player.isInWater()) {
                surfacing = false;
                surfaceTarget = null;
                mc.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§6[Shinigami] §aSurfaced safely"
                    )
                );
            }
        }
    }

    private BlockPos findSafeSurfacePoint() {
        if (mc.player == null || mc.level == null) return null;

        Vec3 pos = mc.player.position();
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos(
            (int)pos.x, (int)pos.y, (int)pos.z
        );

        // Search upward for surface
        int maxY = mc.level.getHeight();
        for (int y = check.getY(); y <= maxY; y++) {
            check.setY(y);
            if (mc.level.getBlockState(check).isAir() &&
                mc.level.canSeeSky(check)) {
                return check.immutable();
            }
        }

        // No direct surface - find nearest air block upward
        for (int y = check.getY(); y <= maxY; y++) {
            check.setY(y);
            if (mc.level.getBlockState(check).isAir()) {
                return check.immutable();
            }
        }

        return null;
    }

    private void emergencySurface() {
        if (mc.player == null) return;
        mc.player.setDeltaMovement(
            mc.player.getDeltaMovement().x,
            0.5,
            mc.player.getDeltaMovement().z
        );
        mc.player.sendSystemMessage(
            net.minecraft.network.chat.Component.literal(
                "§4[Shinigami] §cEMERGENCY SURFACE!"
            )
        );
    }

    private int estimateSurfaceTicks() {
        if (mc.player == null || surfaceTarget == null) return Integer.MAX_VALUE;

        double dy = surfaceTarget.getY() - mc.player.getY();
        if (dy <= 0) return 0;

        // Rough estimate: ~0.5 blocks/tick swim speed upward
        return (int)(dy / 0.5) + 10;
    }

    public boolean isSurfacing() { return surfacing; }
    public boolean isInWater() { return mc.player != null && mc.player.isInWater(); }

    public Vec3 calculateSafeAscendPoint(BlockPos underwaterPos) {
        if (mc.level == null) return null;

        int maxY = mc.level.getHeight();
        int airY = -1;

        // Find nearest air pocket
        for (int y = underwaterPos.getY(); y <= maxY; y++) {
            BlockPos check = new BlockPos(underwaterPos.getX(), y, underwaterPos.getZ());
            if (mc.level.getBlockState(check).isAir()) {
                airY = y;
                break;
            }
        }

        if (airY == -1) return null;

        int air = mc.player != null ? mc.player.getAirSupply() : 300;
        int maxAir = mc.player != null ? mc.player.getMaxAirSupply() : 300;
        int safeTicks = (air * TICKS_PER_BUBBLE) / maxAir;

        int blocksToSurface = airY - underwaterPos.getY();
        double swimSpeed = 0.5;
        int neededTicks = (int)(blocksToSurface / swimSpeed) + SAFETY_MARGIN_TICKS;

        if (neededTicks > safeTicks) {
            // Cannot surface safely - look for closer air pocket or bubbles
            return null;
        }

        return new Vec3(underwaterPos.getX(), airY, underwaterPos.getZ());
    }
}
