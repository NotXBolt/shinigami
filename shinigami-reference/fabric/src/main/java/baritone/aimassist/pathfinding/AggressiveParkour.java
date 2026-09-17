package baritone.aimassist.pathfinding;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.*;
import baritone.aimassist.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class AggressiveParkour {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;
    private boolean active = false;

    public AggressiveParkour(AimAssistConfig config) {
        this.config = config;
    }

    /**
     * Apply insane parkour settings to baritone.
     * Makes baritone do moves that normally wouldn't be considered "safe".
     */
    public void applyAggressiveSettings() {
        Settings s = BaritoneAPI.getSettings();

        s.allowParkour.value = true;
        s.allowParkourAscend.value = true;
        s.allowParkourPlace.value = true;

        s.maxFallHeightNoWater.value = 6;
        s.maxFallHeightBucket.value = 50;

        s.sprintInWater.value = true;

        s.costHeuristic.value = 1.0;

        s.blocksToAvoid.value.clear();

        s.freeLook.value = true;
        s.antiCheatCompatibility.value = false;

        s.elytraFreeLook.value = true;
    }

    /**
     * Get to surface with aggressive pathfinding.
     * Uses multiple strategies: dig up, pillar, tower, find cave exit.
     */
    public void getToSurface() {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null || mc.player == null) return;

        applyAggressiveSettings();

        BlockPos playerPos = mc.player.blockPosition();
        int surfaceY = mc.level.getHeight();

        // Multiple strategies for getting to surface quickly
        if (canSeeSky(playerPos)) {
            // Already exposed to sky - just pillar up or dig
            if (mc.player.getY() < surfaceY - 3) {
                // Try to pillar up directly
                // getToBlockProcess removed in 26.1
                baritone.getCustomGoalProcess().setGoalAndPath(
                    new GoalYLevel(surfaceY - 1)
                );
            }
        } else {
            // Underground - find quickest route up
            Optional<BlockPos> nearestOpen = findNearestOpenSpace(playerPos, 32);
            if (nearestOpen.isPresent()) {
                baritone.getCustomGoalProcess().setGoalAndPath(
                    new GoalNear(nearestOpen.get(), 2)
                );
            } else {
                // Dig straight up
                baritone.getCustomGoalProcess().setGoalAndPath(
                    new GoalYLevel(surfaceY - 1)
                );
                // Enable breaking for digging
            }
        }
    }

    /**
     * Override baritone's path to a target with aggressive movement.
     * Uses sprint-jumping, riskier parkour, and faster overall path.
     */
    public void pathToTargetAggressive(BlockPos target) {
        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone == null) return;

        applyAggressiveSettings();

        baritone.getCustomGoalProcess().setGoalAndPath(
            new GoalNear(target, 1)
        );
    }

    /**
     * Use the new Wind Charge mechanic for vertical movement.
     * Places wind charge below feet to launch upward.
     */
    public void windChargeLaunch() {
        if (mc.player == null || mc.gameMode == null) return;

        // Find wind charge in hotbar
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(
                net.minecraft.world.item.Items.WIND_CHARGE)) {
                slot = i;
                break;
            }
        }
        if (slot == -1) {
            // Check for wind burst enchantment on mace as alternative
            if (mc.player.getMainHandItem().is(net.minecraft.world.item.Items.MACE)) {
                // Smash below to trigger wind burst
                mc.player.setXRot(90);
                mc.options.keyAttack.setDown(true);
                try { Thread.sleep(50); } catch (InterruptedException e) {}
                mc.options.keyAttack.setDown(false);
            }
            return;
        }

        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.setXRot(90); // Look straight down
        mc.options.keyUse.setDown(true);
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        mc.options.keyUse.setDown(false);
    }

    private boolean canSeeSky(BlockPos pos) {
        if (mc.level == null) return false;
        for (int y = pos.getY() + 1; y <= mc.level.getHeight(); y++) {
            BlockState state = mc.level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
            if (!state.isAir() && !state.canBeReplaced()) return false;
        }
        return true;
    }

    private Optional<BlockPos> findNearestOpenSpace(BlockPos start, int radius) {
        if (mc.level == null) return Optional.empty();

        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos pos = start.offset(dx, 0, dz);
                    if (canSeeSky(pos)) {
                        return Optional.of(pos);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Override baritone's default "GoalSurface" with a smarter one.
     */
    public Goal findBetterSurfaceGoal() {
        if (mc.player == null) return new GoalYLevel(mc.level.getHeight() - 1);

        BlockPos playerPos = mc.player.blockPosition();
        int surfaceY = mc.level.getHeight() - 1;

        // Check if there's a nearby mountain/hill we can climb
        for (int dx = -10; dx <= 10; dx += 2) {
            for (int dz = -10; dz <= 10; dz += 2) {
                BlockPos check = playerPos.offset(dx, 0, dz);
                int height = getHeightAt(check);
                if (height > surfaceY - 5) {
                    return new GoalNear(new BlockPos(check.getX(), height, check.getZ()), 3);
                }
            }
        }

        return new GoalYLevel(surfaceY);
    }

    private int getHeightAt(BlockPos pos) {
        if (mc.level == null) return 0;
        int y = mc.level.getHeight();
        while (y > mc.level.getMinY()) {
            BlockState state = mc.level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
            if (!state.isAir()) return y + 1;
            y--;
        }
        return 0;
    }

    public void setActive(boolean a) { this.active = a; if (a) applyAggressiveSettings(); }
    public boolean isActive() { return active; }
}
