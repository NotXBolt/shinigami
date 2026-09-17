package baritone.aimassist.prediction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.math3.filter.*;
import org.apache.commons.math3.linear.*;

public class PhysicsUtils {

    public static final double PLAYER_GRAVITY = 0.08;
    public static final double PLAYER_DRAG_AIR = 0.09;
    public static final double PLAYER_DRAG_GROUND = 0.454;
    public static final double PLAYER_DRAG_VERT = 0.02;

    public static final double ARROW_GRAVITY = 0.05;
    public static final double ARROW_DRAG = 0.01;

    public static final double THROWN_GRAVITY = 0.03;
    public static final double THROWN_DRAG = 0.01;

    public static final double POTION_GRAVITY = 0.04;
    public static final double POTION_DRAG = 0.01;

    public static final double FIREBALL_GRAVITY = -0.10;
    public static final double FIREBALL_DRAG = 0.05;

    private static KalmanFilter createKalmanFilter(double dt) {
        double[][] A = {
            {1, dt, 0.5*dt*dt, 0, 0, 0, 0, 0, 0},
            {0, 1, dt, 0, 0, 0, 0, 0, 0},
            {0, 0, 0.9, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 1, dt, 0.5*dt*dt, 0, 0, 0},
            {0, 0, 0, 0, 1, dt, 0, 0, 0},
            {0, 0, 0, 0, 0, 0.9, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 1, dt, 0.5*dt*dt},
            {0, 0, 0, 0, 0, 0, 0, 1, dt},
            {0, 0, 0, 0, 0, 0, 0, 0, 0.9}
        };
        RealMatrix Amat = new Array2DRowRealMatrix(A);
        RealMatrix Bmat = new Array2DRowRealMatrix(9, 9);
        RealMatrix Hmat = new Array2DRowRealMatrix(new double[][] {
            {1,0,0,0,0,0,0,0,0},
            {0,0,0,1,0,0,0,0,0},
            {0,0,0,0,0,0,1,0,0}
        });
        RealMatrix Qmat = MatrixUtils.createRealIdentityMatrix(9).scalarMultiply(0.05);
        RealMatrix Rmat = MatrixUtils.createRealIdentityMatrix(3).scalarMultiply(0.5);
        RealMatrix Pmat = MatrixUtils.createRealIdentityMatrix(9).scalarMultiply(1.0);
        ProcessModel pm = new DefaultProcessModel(Amat, Bmat, Qmat, new ArrayRealVector(new double[9]), Pmat);
        MeasurementModel mm = new DefaultMeasurementModel(Hmat, Rmat);
        return new KalmanFilter(pm, mm);
    }

    public static KalmanFilter createPlayerKalmanFilter() {
        return createKalmanFilter(1.0);
    }

    public static double predictPositionClosedForm(double pos0, double vel0, double drag, double gravity, int ticks) {
        double d = Math.min(0.99, Math.max(0.001, drag));
        double oneMinusD = 1.0 - d;
        double factor = (1.0 - Math.pow(oneMinusD, ticks)) / d;
        double gravityTerm = gravity > 0 ? (gravity / d) * (1.0 - oneMinusD * factor) - gravity * ticks / d : 0;
        double dragTerm = vel0 * factor;
        return pos0 + dragTerm + gravityTerm;
    }

    public static Vec3 predictEntityPosition(Vec3 pos, Vec3 vel, int ticks, double drag, double gravity) {
        double x = predictPositionClosedForm(pos.x, vel.x, drag, 0, ticks);
        double z = predictPositionClosedForm(pos.z, vel.z, drag, 0, ticks);
        double y = predictPositionClosedForm(pos.y, vel.y, drag, gravity, ticks);
        return new Vec3(x, y, z);
    }

    public static Vec3 predictPlayerPosition(Vec3 pos, Vec3 vel, int ticks, boolean onGround) {
        double drag = onGround ? PLAYER_DRAG_GROUND : PLAYER_DRAG_AIR;
        return predictEntityPosition(pos, vel, ticks, drag, PLAYER_GRAVITY);
    }

    public static Vec3 predictArrowPosition(Vec3 pos, Vec3 vel, int ticks) {
        return predictEntityPosition(pos, vel, ticks, ARROW_DRAG, ARROW_GRAVITY);
    }

    public static Vec3 predictThrownPosition(Vec3 pos, Vec3 vel, int ticks) {
        return predictEntityPosition(pos, vel, ticks, THROWN_DRAG, THROWN_GRAVITY);
    }

    public static int timeToImpact(Vec3 origin, Vec3 velocity, Vec3 targetPos, double targetRadius,
                                    int maxTicks, double gravity, double drag) {
        double px = origin.x, py = origin.y, pz = origin.z;
        double vx = velocity.x, vy = velocity.y, vz = velocity.z;
        boolean dragBeforeGravity = drag < 0.02;
        for (int t = 1; t <= maxTicks; t++) {
            if (dragBeforeGravity) {
                vx *= (1 - drag); vy *= (1 - drag); vz *= (1 - drag);
                vy -= gravity;
            } else {
                vx *= (1 - drag); vz *= (1 - drag);
                vy = (vy - gravity) * (1 - drag);
            }
            px += vx; py += vy; pz += vz;
            double dx = px - targetPos.x;
            double dy = py - targetPos.y;
            double dz = pz - targetPos.z;
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < targetRadius) return t;
        }
        return -1;
    }

    public static int timeToPlayerImpact(Vec3 origin, Vec3 velocity, Vec3 targetPos, int maxTicks) {
        return timeToImpact(origin, velocity, targetPos, 1.0, maxTicks, PLAYER_GRAVITY, PLAYER_DRAG_AIR);
    }

    public static int timeToArrowImpact(Vec3 origin, Vec3 velocity, Vec3 targetPos, int maxTicks) {
        return timeToImpact(origin, velocity, targetPos, 0.6, maxTicks, ARROW_GRAVITY, ARROW_DRAG);
    }

    public static double getProjectileGravity(Entity projectile) {
        if (projectile instanceof Arrow) return ARROW_GRAVITY;
        if (projectile instanceof ThrownEnderpearl) return THROWN_GRAVITY;
        if (projectile instanceof AbstractThrownPotion) return POTION_GRAVITY;
        return ARROW_GRAVITY;
    }

    public static double getProjectileDrag(Entity projectile) {
        if (projectile instanceof Arrow) return ARROW_DRAG;
        if (projectile instanceof ThrownEnderpearl) return THROWN_DRAG;
        if (projectile instanceof AbstractThrownPotion) return POTION_DRAG;
        return ARROW_DRAG;
    }

    public static Vec3 extractKalmanState(KalmanFilter kf) {
        RealVector est = kf.getStateEstimationVector();
        return new Vec3(est.getEntry(0), est.getEntry(3), est.getEntry(6));
    }

    public static Vec3 extractKalmanVelocity(KalmanFilter kf) {
        RealVector est = kf.getStateEstimationVector();
        return new Vec3(est.getEntry(1), est.getEntry(4), est.getEntry(7));
    }
}
