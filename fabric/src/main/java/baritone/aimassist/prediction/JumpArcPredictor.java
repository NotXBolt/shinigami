package baritone.aimassist.prediction;

import net.minecraft.world.phys.Vec3;

/**
 * Predicts the parabolic arc of a jumping entity.
 * Uses Minecraft's physics: initial velocity + gravity.
 */
public class JumpArcPredictor {

    // Minecraft's gravity acceleration (blocks/tick^2)
    private static final double MC_GRAVITY = 0.08;
    // Minecraft's jump velocity (blocks/tick)
    private static final double JUMP_VELOCITY = 0.42;

    /**
     * Predict position of a jumping entity after the given number of ticks.
     *
     * @param startPos Starting position
     * @param velocity Current velocity (horizontal movement)
     * @param ticks How many ticks into the future
     * @return Predicted position
     */
    public Vec3 predictJumpArc(Vec3 startPos, Vec3 velocity, int ticks) {
        double x = startPos.x;
        double y = startPos.y;
        double z = startPos.z;

        double vx = velocity.x;
        double vy = JUMP_VELOCITY; // Initial jump velocity
        double vz = velocity.z;

        for (int t = 0; t < ticks; t++) {
            // Apply gravity
            vy -= MC_GRAVITY;

            // Apply position update (simplified - no collision)
            x += vx;
            y += vy;
            z += vz;

            // Air resistance (Minecraft applies friction in air)
            vx *= 0.98;
            vy *= 0.98;
            vz *= 0.98;
        }

        return new Vec3(x, y, z);
    }

    /**
     * Calculate the landing position of the current jump.
     */
    public Vec3 predictLanding(Vec3 startPos, Vec3 horizontalVelocity) {
        double x = startPos.x;
        double y = startPos.y;
        double z = startPos.z;

        double vx = horizontalVelocity.x;
        double vy = JUMP_VELOCITY;
        double vz = horizontalVelocity.z;

        int maxTicks = 100; // Safety limit
        for (int t = 0; t < maxTicks; t++) {
            vy -= MC_GRAVITY;
            x += vx;
            y += vy;
            z += vz;

            vx *= 0.98;
            vy *= 0.98;
            vz *= 0.98;

            // If fallen back to start height (or below), we landed
            if (y <= startPos.y) {
                return new Vec3(x, startPos.y, z);
            }
        }

        return new Vec3(x, y, z);
    }

    /**
     * Get the estimated peak height of a jump.
     */
    public double getJumpPeakHeight() {
        // Peak = v0^2 / (2 * g) = 0.42^2 / (2 * 0.08)
        return (JUMP_VELOCITY * JUMP_VELOCITY) / (2.0 * MC_GRAVITY);
    }

    /**
     * Get total air time of a jump in ticks.
     */
    public int getJumpAirTime() {
        // Time up + time down = 2 * v0 / g
        return (int) Math.ceil(2.0 * JUMP_VELOCITY / MC_GRAVITY);
    }
}
