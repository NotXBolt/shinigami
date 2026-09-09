package shinigami.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public class KeyMovementController {

    private final Minecraft mc = Minecraft.getInstance();

    private boolean active = false;
    private boolean hasActiveOverride = false;
    private Vec3 targetDirection = Vec3.ZERO;

    private boolean overrideForward = false;
    private boolean overrideBackward = false;
    private boolean overrideLeft = false;
    private boolean overrideRight = false;
    private boolean overrideJump = false;
    private boolean overrideShift = false;
    private boolean overrideSprint = false;
    private boolean overrideAttack = false;
    private boolean overrideUse = false;

    // Supplement mode: OR with player input instead of full override
    private boolean supplementMode = false;
    private boolean supplementForward = false;
    private boolean supplementBackward = false;
    private boolean supplementLeft = false;
    private boolean supplementRight = false;
    private boolean supplementSprint = false;
    private boolean supplementJump = false;

    private int sprintTicks = 0;
    private int jumpTicks = 0;
    private int overrideExpiryTicks = 0;

    private static final float KEY_THRESHOLD = 0.3f;
    private static final int OVERRIDE_TTL = 5;

    public Input getOverriddenInput(Input original) {
        if (hasActiveOverride) {
            return new Input(overrideForward, overrideBackward, overrideLeft, overrideRight,
                             overrideJump, overrideShift, overrideSprint);
        }
        if (supplementMode) {
            boolean fwd = original.forward() || supplementForward;
            boolean bwd = original.backward() || supplementBackward;
            boolean left = original.left() || supplementLeft;
            boolean right = original.right() || supplementRight;
            if (fwd && bwd) bwd = false;
            if (left && right) right = false;
            return new Input(
                fwd, bwd, left, right,
                original.jump() || supplementJump,
                original.shift(),
                original.sprint() || supplementSprint
            );
        }
        return original;
    }

    public void supplementForward(boolean sprint) {
        supplementMode = true;
        supplementForward = true;
        supplementSprint = sprint;
    }

    public void supplementJump() {
        supplementMode = true;
        supplementJump = true;
    }

    /** Apply supplement flags from a world-space direction vector. */
    public void supplementDirection(Vec3 worldDir, boolean sprint) {
        if (mc.player == null || worldDir == null || worldDir.lengthSqr() < 0.01) return;
        supplementMode = true;

        float yaw = mc.player.getYRot();
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = forwardZ;
        double strafeZ = -forwardX;

        double fwd = worldDir.x * forwardX + worldDir.z * forwardZ;
        double str = worldDir.x * strafeX + worldDir.z * strafeZ;

        supplementForward = fwd > 0.01;
        supplementBackward = fwd < -0.01;
        supplementLeft = str > 0.01;
        supplementRight = str < -0.01;
        if (supplementForward && supplementBackward) supplementBackward = false;
        if (supplementLeft && supplementRight) supplementRight = false;
        supplementSprint = sprint;
    }

    public void clearSupplement() {
        supplementMode = false;
        supplementForward = false;
        supplementBackward = false;
        supplementLeft = false;
        supplementRight = false;
        supplementSprint = false;
        supplementJump = false;
    }

    public void tick() {
        if (!active) {
            if (hasActiveOverride) stopMoving();
            return;
        }

        // Auto-expire stale overrides
        if (hasActiveOverride && overrideExpiryTicks > 0) {
            overrideExpiryTicks--;
            if (overrideExpiryTicks == 0) {
                stopMoving();
                return;
            }
        }

        if (overrideSprint) {
            sprintTicks++;
            mc.player.setSprinting(true);
        } else {
            sprintTicks = 0;
        }

        if (overrideAttack) {
            mc.options.keyAttack.setDown(true);
        }

        if (overrideUse) {
            mc.options.keyUse.setDown(true);
        }
    }

    public void releaseAll() {
        if (mc.player == null) return;
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    public void moveToward(Vec3 worldTarget, boolean sprint, boolean jump, boolean sneak) {
        if (mc.player == null) return;
        targetDirection = worldTarget;

        Vec3 dir = worldTarget;
        if (dir.lengthSqr() < 0.01) {
            stopMoving();
            return;
        }

        float yaw = mc.player.getYRot();
        double yawRad = Math.toRadians(yaw);

        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = forwardZ;
        double strafeZ = -forwardX;

        double fwd = dir.x * forwardX + dir.z * forwardZ;
        double str = dir.x * strafeX + dir.z * strafeZ;

        double len = Math.sqrt(fwd * fwd + str * str);
        if (len > 0.01) {
            fwd /= len;
            str /= len;
        }

        overrideForward = fwd > KEY_THRESHOLD;
        overrideBackward = fwd < -KEY_THRESHOLD;
        // strafe vector points LEFT at yaw=0 (cos(yaw), sin(yaw) = (1,0))
        // positive dot = target is LEFT → set overrideLeft
        // negative dot = target is RIGHT → set overrideRight
        overrideLeft = str > KEY_THRESHOLD;
        overrideRight = str < -KEY_THRESHOLD;
        overrideSprint = sprint;
        overrideJump = jump;
        overrideShift = sneak;
        overrideExpiryTicks = OVERRIDE_TTL;
        hasActiveOverride = true;
    }

    public void strafeAround(Vec3 center, boolean clockwise, boolean sprint) {
        if (mc.player == null) return;
        Vec3 away = mc.player.position().subtract(center);
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
        away = away.normalize();

        double perpX = clockwise ? -away.z : away.z;
        double perpZ = clockwise ? away.x : -away.x;
        Vec3 strafeDir = new Vec3(perpX, 0, perpZ).normalize();

        moveToward(strafeDir, sprint, true, false);
    }

    public void stopMoving() {
        overrideForward = false;
        overrideBackward = false;
        overrideLeft = false;
        overrideRight = false;
        overrideJump = false;
        overrideShift = false;
        overrideSprint = false;
        overrideAttack = false;
        overrideUse = false;
        overrideExpiryTicks = 0;
        hasActiveOverride = false;
    }

    public void attackOnce() {
        overrideAttack = true;
    }

    public void useOnce() {
        overrideUse = true;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean a) {
        this.active = a;
        if (!a) {
            stopMoving();
            releaseAll();
        }
    }

    public boolean hasMovement() {
        return overrideForward || overrideBackward || overrideLeft || overrideRight;
    }

    public boolean hasActiveOverride() {
        return hasActiveOverride;
    }

    public void moveForward(boolean sprint) {
        if (mc.player == null) return;
        float yaw = mc.player.getYRot();
        double yawRad = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
        moveToward(forward, sprint, false, false);
    }

    // ─── SLOW COMPENSATION ────────────────────────────

    public boolean isSlowed() {
        if (mc.player == null) return false;
        if (mc.player.isInWater() || mc.player.isInLava()) return true;
        if (mc.player.hasEffect(MobEffects.SLOWNESS)) return true;

        if (mc.level != null) {
            var state = mc.level.getBlockState(mc.player.blockPosition());
            if (state.is(net.minecraft.world.level.block.Blocks.COBWEB)
                || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                || state.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)) return true;
        }
        return false;
    }

    public double getSlowFactor() {
        double factor = 1.0;
        if (mc.player == null) return factor;

        if (mc.player.isInWater()) factor = Math.min(factor, 0.5);
        if (mc.player.isInLava()) factor = Math.min(factor, 0.3);
        if (mc.player.hasEffect(MobEffects.SLOWNESS)) {
            int amp = mc.player.getEffect(MobEffects.SLOWNESS).getAmplifier();
            factor = Math.min(factor, 1.0 - (amp + 1) * 0.15);
        }
        if (mc.level != null) {
            var state = mc.level.getBlockState(mc.player.blockPosition());
            if (state.is(net.minecraft.world.level.block.Blocks.COBWEB)) factor = Math.min(factor, 0.2);
            if (state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) factor = Math.min(factor, 0.3);
            if (state.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)) factor = Math.min(factor, 0.6);
        }
        return factor;
    }
}
