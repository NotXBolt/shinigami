package shinigami.tags;

import shinigami.AimAssistConfig;
import shinigami.AimAssistMod;
import shinigami.AimAssistModule;
import shinigami.util.KeyMovementController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class ChaseBehavior {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module;

    public ChaseBehavior(AimAssistModule module) {
        this.module = module;
    }

    private double getDetectRange() {
        return AimAssistConfig.getInstance().getDetectionRange();
    }

    private String chaseTargetName;
    private UUID chaseTargetUUID;
    private boolean chaseKill = false;
    private int hungryTicks = 0;
    private int healthTicks = 0;
    private int attackCooldown = 0;
    private int eatingTicks = 0;
    private int blockedTicks = 0;
    private int stuckTicks = 0;
    private BlockPos lastPos = BlockPos.ZERO;
    private int noProgressTicks = 0;
    private boolean bridging = false;
    private int bridgeTicks = 0;
    private boolean towering = false;
    private int towerTicks = 0;
    private int windBurstCooldown = 0;

    private static final double ENGAGE_RANGE = 5.0;
    private static final double HUNGER_THRESHOLD = 6.0;
    private static final double HEALTH_THRESHOLD = 8.0;
    private static final int EAT_DURATION_TICKS = 26;
    private static final int STUCK_THRESHOLD = 12;
    private static final int BRIDGE_GAP = 5;
    private static final int WIND_BURST_COOLDOWN_TICKS = 60;
    private static final int BLOCKED_BREAK_TICKS = 8;

    public void tick() {
        if (chaseTargetName == null && chaseTargetUUID == null) return;

        LivingEntity target = findTarget();
        if (target == null) {
            handleLostTarget();
            return;
        }

        double distance = mc.player.distanceTo(target);

        if (windBurstCooldown > 0) windBurstCooldown--;

        // Track progress — if player hasn't moved much, count stuck
        BlockPos currentPos = mc.player.blockPosition();
        if (currentPos.distSqr(lastPos) < 2) {
            noProgressTicks++;
        } else {
            noProgressTicks = 0;
        }
        lastPos = currentPos;

        if (bridging) {
            bridgeTicks--;
            if (bridgeTicks <= 0) bridging = false;
        }
        if (towering) {
            towerTicks--;
            if (towerTicks <= 0) towering = false;
        }

        if (distance > ENGAGE_RANGE) {
            handlePathToTarget(target, distance);
        } else if (chaseKill) {
            attackTarget(target);
        }

        handleSurvivalNeeds();

        if (eatingTicks > 0) {
            eatingTicks--;
            if (eatingTicks == 0) {
                mc.options.keyUse.setDown(false);
            }
        }
    }

    private LivingEntity findTarget() {
        if (mc.level == null || mc.player == null) return null;

        double scanRange = getDetectRange();
        AABB scanBox = new AABB(
            mc.player.getX() - scanRange, mc.player.getY() - scanRange, mc.player.getZ() - scanRange,
            mc.player.getX() + scanRange, mc.player.getY() + scanRange, mc.player.getZ() + scanRange
        );

        if (chaseTargetUUID != null) {
            for (Entity e : mc.level.getEntitiesOfClass(Entity.class, scanBox)) {
                if (e.getUUID().equals(chaseTargetUUID) && e instanceof LivingEntity living) {
                    return living;
                }
            }
        }

        if (chaseTargetName != null) {
            double closestDist = Double.MAX_VALUE;
            LivingEntity closest = null;
            String hunt = chaseTargetName.toLowerCase();

            for (Entity e : mc.level.getEntitiesOfClass(Entity.class, scanBox)) {
                if (e instanceof LivingEntity living && living.isAlive()) {
                    if (living.hasCustomName()) {
                        String customName = living.getCustomName().getString().toLowerCase();
                        if (customName.contains(hunt)) {
                            double dist = mc.player.distanceTo(living);
                            if (dist < closestDist) {
                                closestDist = dist;
                                closest = living;
                                chaseTargetUUID = living.getUUID();
                            }
                        }
                    }
                }
            }
            if (closest != null) return closest;

            for (Entity e : mc.level.getEntitiesOfClass(Entity.class, scanBox)) {
                if (e instanceof Player player) {
                    String name = player.getName().getString();
                    if (name.equalsIgnoreCase(chaseTargetName)) {
                        double dist = mc.player.distanceTo(player);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = player;
                            chaseTargetUUID = player.getUUID();
                        }
                    }
                }
            }
            if (closest != null) return closest;

            // Perfect & on point: any entity type huntable (iron_golem, zombie, skeleton, creeper, warden, etc)
            for (Entity e : mc.level.getEntitiesOfClass(Entity.class, scanBox)) {
                if (e instanceof LivingEntity living && living.isAlive()) {
                    String typeName = living.getType().toString().toLowerCase(); // e.g. entity.minecraft.iron_golem
                    String name = living.getName().getString().toLowerCase();
                    if (typeName.contains(hunt) || name.contains(hunt)) {
                        double dist = mc.player.distanceTo(living);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = living;
                            chaseTargetUUID = living.getUUID();
                        }
                    }
                }
            }
            if (closest != null) return closest;
        }

        return null;
    }

    private void handlePathToTarget(LivingEntity target, double distance) {
        KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
        if (ctrl == null) return;

        Vec3 diff = target.position().subtract(mc.player.position());
        diff = new Vec3(diff.x, 0, diff.z);
        double dist = diff.length();

        if (dist < ENGAGE_RANGE) {
            if (chaseKill) {
                attackTarget(target);
            } else {
                ctrl.stopMoving();
            }
            return;
        }

        // ─── Obstacle / parkour detection ───
        boolean pathBlocked = isPathBlocked();
        boolean gapAhead = detectGapAhead();
        boolean oneBlockObstacle = detectOneBlockObstacle();

        if (pathBlocked && !oneBlockObstacle) {
            blockedTicks++;
            if (blockedTicks > BLOCKED_BREAK_TICKS && noProgressTicks > BLOCKED_BREAK_TICKS) {
                breakBlockInFront();
            }
        } else {
            blockedTicks = 0;
        }

        boolean targetAbove = target.getY() > mc.player.getY() + 1.5 && dist < 10;

        Vec3 dir = diff.normalize();

        // ─── Face target directly — no weave, no straightening (vision perfect, aggressive lock) ───
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        mc.player.setYRot(targetYaw);

        // ─── No strafe oscillation — mod never touches A/D ───

        // ─── Aggressive sprint: whenever possible ───
        boolean sprint = mc.player.getFoodData().getFoodLevel() > 6
            && !mc.player.isInWater()
            && !mc.player.isInLava()
            && distance > 2.0;

        // ─── Aggressive parkour: perfect edge detection (inspired by better-auto-jump + Oogabooga + Kiwi Theta*) ───
        // Edge jump (better-auto-jump): detect approaching block edge while sprinting, jump just before edge
        boolean edgeAhead = detectEdgeAhead();
        // Jump only when needed: gap at edge, edge-approach, 1-block obstacle, or target above — never spam in air
        boolean jump = gapAhead || edgeAhead || pathBlocked || oneBlockObstacle || (targetAbove && mc.player.onGround());

        // If there's a 1-block obstacle ahead and no sprint momentum: jump to clear it
        if (oneBlockObstacle && mc.player.onGround() && !jump) {
            jump = true;
        }

        boolean sneak = bridging;

        // ─── Fast bridging ───
        if (gapAhead && hasBridgeBlocks() && noProgressTicks > 1 && mc.player.onGround()) {
            doBridge(dir);
            ctrl.moveToward(dir, true, true, true);
            return;
        }

        // ─── Towering ───
        if (targetAbove && mc.player.onGround() && hasBridgeBlocks()) {
            doTower();
            ctrl.moveToward(dir, true, true, false);
            return;
        }

        // ─── Wind burst for height ───
        if (targetAbove && dist < 8 && windBurstCooldown == 0 && hasWindCharge()) {
            useWindCharge();
        }

        // ─── Supplement forward (preserves A/D, never overrides strafe) ───
        ctrl.supplementForward(sprint);
        if (jump) ctrl.supplementJump();
    }

    private boolean detectOneBlockObstacle() {
        if (mc.player == null || mc.level == null) return false;
        Vec3 look = mc.player.getLookAngle();
        int dx = (int) Math.signum(look.x);
        int dz = (int) Math.signum(look.z);
        if (dx == 0 && dz == 0) return false;
        BlockPos atFeet = mc.player.blockPosition();
        BlockPos ahead = atFeet.offset(dx, 0, dz);
        BlockState aheadState = mc.level.getBlockState(ahead);
        // 1-block obstacle: solid block at feet level but AIR above
        if (aheadState.isSolid() && !aheadState.is(Blocks.AIR)) {
            BlockPos aboveAhead = ahead.above();
            BlockState aboveState = mc.level.getBlockState(aboveAhead);
            if (aboveState.isAir() || !aboveState.isSolid()) {
                // Check if there's headroom above player too
                BlockPos playerHeadAbove = atFeet.above(2);
                if (mc.level.getBlockState(playerHeadAbove).isAir() || !mc.level.getBlockState(playerHeadAbove).isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPathBlocked() {
        if (mc.player == null || mc.level == null) return false;
        Vec3 look = mc.player.getLookAngle();
        BlockPos pos = mc.player.blockPosition();
        int dx = (int) Math.signum(look.x);
        int dz = (int) Math.signum(look.z);
        BlockPos ahead = pos.offset(dx, 0, dz);
        BlockState state = mc.level.getBlockState(ahead);
        return state.isSolid() && !state.is(Blocks.AIR) && !state.is(Blocks.CAVE_AIR)
            && !state.is(Blocks.VOID_AIR)
            && state.getDestroySpeed(mc.level, ahead) >= 0;
    }

    private void breakBlockInFront() {
        if (mc.player == null || mc.level == null) return;
        Vec3 look = mc.player.getLookAngle();
        BlockPos pos = mc.player.blockPosition();
        int dx = (int) Math.signum(look.x);
        int dz = (int) Math.signum(look.z);
        BlockPos target = pos.offset(dx, 0, dz);

        equipBestTool(target);
        mc.gameMode.destroyBlock(target);
        blockedTicks = 0;
    }

    private void equipBestTool(BlockPos blockPos) {
        if (mc.player == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(blockPos);
        int bestSlot = -1;
        float bestSpeed = 1f;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) {
            mc.player.getInventory().setSelectedSlot(bestSlot);
        }
    }

    private boolean detectGapAhead() {
        if (mc.player == null || mc.level == null) return false;
        Vec3 look = mc.player.getLookAngle();
        if (Math.abs(look.x) < 0.1 && Math.abs(look.z) < 0.1) return false;
        int dx = (int) Math.round(Math.signum(look.x));
        int dz = (int) Math.round(Math.signum(look.z));
        if (dx == 0 && dz == 0) return false;

        for (int i = 1; i <= BRIDGE_GAP; i++) {
            BlockPos check = mc.player.blockPosition().offset(dx * i, -1, dz * i);
            BlockState below = mc.level.getBlockState(check);
            BlockPos at = mc.player.blockPosition().offset(dx * i, 0, dz * i);
            BlockState above = mc.level.getBlockState(at);
            boolean solidBelow = below.isSolid();
            boolean passableAbove = !above.isSolid() || above.is(Blocks.AIR) || above.is(Blocks.CAVE_AIR) || above.is(Blocks.VOID_AIR);
            if (!solidBelow && passableAbove && i >= 2) return true;
            if (!passableAbove) return false;
            // Don't stop at solid ground - keep scanning for gaps further ahead
        }
        return false;
    }

    private boolean detectEdgeAhead() {
        if (mc.player == null || mc.level == null || !mc.player.onGround()) return false;
        Vec3 vel = mc.player.getDeltaMovement();
        double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (speed < 0.1) return false; // Min velocity from better-auto-jump
        Vec3 look = mc.player.getLookAngle();
        // Sprint distance 2.0, step 0.35 (better-auto-jump params)
        for (double d = 0.3; d <= 2.0; d += 0.35) {
            Vec3 checkPos = mc.player.position().add(look.scale(d));
            BlockPos feet = new BlockPos((int)Math.floor(checkPos.x), (int)Math.floor(mc.player.getY() - 0.05), (int)Math.floor(checkPos.z));
            var feetState = mc.level.getBlockState(feet);
            double height = feetState.isSolid() ? 1.0 : 0.0;
            // Edge if ground height below threshold (Solid Min 0.001, Max 0.6)
            if (height < 0.001) return true;
        }
        return false;
    }

    private boolean hasBridgeBlocks() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isPlaceableBlock(stack)) return true;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isPlaceableBlock(stack)) return true;
        }
        return false;
    }

    private boolean isPlaceableBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item instanceof net.minecraft.world.item.BlockItem;
    }

    // cadence-inspired block traversal costs (soul sand, honey, slime, dripleaf) — original perfect, not copy
    private double getBlockCost(BlockState state) {
        if (state.is(Blocks.SOUL_SAND)) return 2.5;
        if (state.is(Blocks.HONEY_BLOCK)) return 3.0;
        if (state.is(Blocks.SLIME_BLOCK)) return 1.8;
        if (state.is(Blocks.BIG_DRIPLEAF) || state.is(Blocks.SMALL_DRIPLEAF)) return 2.0;
        return 1.0;
    }

    private void doBridge(Vec3 dir) {
        if (mc.player == null || mc.level == null || bridging) return;
        selectBridgeBlock();
        int dx = (int) Math.round(Math.signum(dir.x));
        int dz = (int) Math.round(Math.signum(dir.z));
        BlockPos placePos = mc.player.blockPosition().offset(dx, -1, dz);
        BlockHitResult hit = new BlockHitResult(
            new Vec3(placePos.getX() + 0.5, placePos.getY() + 1, placePos.getZ() + 0.5),
            Direction.UP, placePos, false
        );
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        bridging = true;
        bridgeTicks = 10;
    }

    private void doTower() {
        if (mc.player == null || towering) return;
        selectBridgeBlock();
        BlockPos placePos = mc.player.blockPosition();
        BlockHitResult hit = new BlockHitResult(
            new Vec3(placePos.getX() + 0.5, placePos.getY() - 1, placePos.getZ() + 0.5),
            Direction.UP, placePos, false
        );
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        towering = true;
        towerTicks = 5;
    }

    private void selectBridgeBlock() {
        if (mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isPlaceableBlock(stack)) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private boolean hasWindCharge() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WIND_CHARGE)) return true;
        }
        return false;
    }

    private void useWindCharge() {
        if (mc.player == null || mc.gameMode == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WIND_CHARGE)) {
                mc.player.getInventory().setSelectedSlot(i);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                windBurstCooldown = WIND_BURST_COOLDOWN_TICKS;
                return;
            }
        }
    }

    private void attackTarget(LivingEntity target) {
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        if (mc.player.getAttackStrengthScale(0.5f) < 0.9f) return;
        if (mc.player.distanceTo(target) > ENGAGE_RANGE + 1) return;

        // Circle strafe during attack
        KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
        if (ctrl != null) {
            boolean clockwise = mc.player.tickCount % 40 < 20;
            ctrl.strafeAround(target.position(), clockwise, true);
        }

        mc.player.setYRot(module.getAimController().calculateRotation(
            target.position().add(0, target.getBbHeight() * 0.4, 0)
        ).getYaw());
        mc.player.setXRot(module.getAimController().calculateRotation(
            target.position().add(0, target.getBbHeight() * 0.4, 0)
        ).getPitch());

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);

        module.getComboTracker().onAttack(target);

        attackCooldown = 8;
    }

    private void handleLostTarget() {
        double scanRange = 80;
        if (chaseTargetName != null && mc.level != null) {
            AABB box = new AABB(
                mc.player.getX() - scanRange, mc.player.getY() - scanRange, mc.player.getZ() - scanRange,
                mc.player.getX() + scanRange, mc.player.getY() + scanRange, mc.player.getZ() + scanRange
            );
            for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
                if (e instanceof LivingEntity living && living.isAlive() && living.hasCustomName()) {
                    if (living.getCustomName().getString().equalsIgnoreCase(chaseTargetName)) {
                        chaseTargetUUID = living.getUUID();
                        return;
                    }
                }
                if (e instanceof Player player) {
                    String name = player.getName().getString();
                    if (name.equalsIgnoreCase(chaseTargetName)) {
                        chaseTargetUUID = player.getUUID();
                        return;
                    }
                }
            }
        }

        KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
        if (ctrl != null) ctrl.stopMoving();
    }

    private void handleSurvivalNeeds() {
        if (mc.player == null) return;

        if (eatingTicks > 0) return;

        if (mc.player.getFoodData().getFoodLevel() < HUNGER_THRESHOLD) {
            hungryTicks++;
            if (hungryTicks > 40) {
                autoEat();
                hungryTicks = 0;
            }
        } else {
            hungryTicks = 0;
        }

        if (mc.player.getHealth() < HEALTH_THRESHOLD) {
            healthTicks++;
            if (healthTicks > 40) {
                autoHeal();
                healthTicks = 0;
            }
        } else {
            healthTicks = 0;
        }
    }

    private void autoEat() {
        if (mc.player == null || mc.player.isUsingItem()) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem().components().has(net.minecraft.core.component.DataComponents.FOOD)) {
                mc.player.getInventory().setSelectedSlot(i);
                mc.options.keyUse.setDown(true);
                eatingTicks = EAT_DURATION_TICKS;
                return;
            }
        }
    }

    private void autoHeal() {
        if (mc.player == null || mc.player.isUsingItem()) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                mc.player.getInventory().setSelectedSlot(i);
                mc.options.keyUse.setDown(true);
                eatingTicks = EAT_DURATION_TICKS;
                return;
            }
        }
    }

    public LivingEntity getCurrentTarget() { return findTarget(); }
    public void setTarget(String name) { this.chaseTargetName = name; }
    public void setMobHunt(String mobType) {
        this.chaseTargetName = mobType;
    }
    public void setTargetUUID(UUID uuid) { this.chaseTargetUUID = uuid; }
    public void setKill(boolean k) { this.chaseKill = k; }
    public boolean isChaseKill() { return chaseKill; }
    public boolean hasTarget() { return chaseTargetName != null || chaseTargetUUID != null; }
    public String getTargetName() { return chaseTargetName; }

    public void reset() {
        chaseTargetName = null;
        chaseTargetUUID = null;
        chaseKill = false;
        hungryTicks = 0;
        healthTicks = 0;
        blockedTicks = 0;
        stuckTicks = 0;
        noProgressTicks = 0;
        bridging = false;
        towering = false;
        noProgressTicks = 0;
        lastPos = BlockPos.ZERO;
        if (eatingTicks > 0) {
            mc.options.keyUse.setDown(false);
            eatingTicks = 0;
        }
    }
}
