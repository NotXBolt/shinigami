package shinigami.prediction;

import baritone.api.utils.Rotation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.math3.analysis.solvers.BisectionSolver;
import org.apache.commons.math3.analysis.UnivariateFunction;

public class BowPhysicsSolver {

    private static final double ARROW_SPEED_BASE = 3.0;
    private static final double ARROW_GRAVITY = 0.05;
    private static final double ARROW_DRAG = 0.01;
    private static final double TARGET_RADIUS = 0.6;
    private static final int MAX_TICKS = 80;

    public static double getArrowSpeed(ItemStack bow, int drawTicks) {
        double power;
        if (bow.getItem() instanceof CrossbowItem) {
            power = 1.0;
        } else {
            float f = BowItem.getPowerForTime(drawTicks);
            power = f;
        }
        return ARROW_SPEED_BASE * power;
    }

    public static double getArrowSpeedFromItem(ItemStack bow) {
        if (bow.getItem() instanceof CrossbowItem) return ARROW_SPEED_BASE;
        return ARROW_SPEED_BASE;
    }

    public static Rotation solveBowAim(LivingEntity shooter, LivingEntity target, double arrowSpeed) {
        Vec3 eyePos = shooter.getEyePosition();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.4, 0);
        Vec3 targetVel = target.getDeltaMovement();
        Vec3 diff = targetPos.subtract(eyePos);
        double dx = diff.x;
        double dy = diff.y;
        double dz = diff.z;
        double horzDist = Math.sqrt(dx * dx + dz * dz);

        double predictedTravelTicks = estimateTravelTime(horzDist, arrowSpeed, dy);
        Vec3 futureTarget = PhysicsUtils.predictEntityPosition(
            targetPos, targetVel, (int)Math.ceil(predictedTravelTicks),
            PhysicsUtils.PLAYER_DRAG_AIR, PhysicsUtils.PLAYER_GRAVITY
        );
        Vec3 futureDiff = futureTarget.subtract(eyePos);
        double fdx = futureDiff.x;
        double fdy = futureDiff.y;
        double fdz = futureDiff.z;
        double fHorz = Math.sqrt(fdx * fdx + fdz * fdz);

        double yaw = Math.toDegrees(Math.atan2(-fdx, fdz));
        double pitch = solvePitch(fHorz, fdy, arrowSpeed, ARROW_GRAVITY);

        return new Rotation((float)yaw, (float)pitch).normalizeAndClamp();
    }

    private static double solvePitch(double horzDist, double verticalDiff, double speed, double gravity) {
        if (horzDist < 0.1) return -90;
        double v2 = speed * speed;
        double v4 = v2 * v2;
        double g = Math.abs(gravity);
        double discriminant = v4 - g * (g * horzDist * horzDist + 2 * verticalDiff * v2);
        if (discriminant < 0) {
            return -Math.toDegrees(Math.atan2(verticalDiff, horzDist));
        }
        double sqrtDisc = Math.sqrt(discriminant);
        double angle1 = Math.atan2(v2 - sqrtDisc, g * horzDist);
        double pitch1 = -Math.toDegrees(angle1);
        if (pitch1 > -90 && pitch1 < 90) return (float)pitch1;
        double angle2 = Math.atan2(v2 + sqrtDisc, g * horzDist);
        double pitch2 = -Math.toDegrees(angle2);
        if (pitch2 > -90 && pitch2 < 90) return (float)pitch2;
        return -Math.toDegrees(Math.atan2(verticalDiff, horzDist));
    }

    private static double estimateTravelTime(double horzDist, double speed, double verticalDiff) {
        double minTicks = horzDist / speed;
        double gravityEffect = Math.sqrt(2 * Math.abs(verticalDiff) / ARROW_GRAVITY);
        return Math.max(minTicks * 0.8, minTicks + gravityEffect * 0.3);
    }

    public static int simulateArrowImpact(Vec3 origin, Vec3 velocity, Vec3 targetPos, int maxTicks) {
        return PhysicsUtils.timeToArrowImpact(origin, velocity, targetPos, maxTicks);
    }

    public static Vec3 predictArrowTrajectory(Vec3 origin, Vec3 direction, double speed, int ticksAhead) {
        Vec3 vel = direction.scale(speed);
        return PhysicsUtils.predictEntityPosition(origin, vel, ticksAhead, ARROW_DRAG, ARROW_GRAVITY);
    }
}
