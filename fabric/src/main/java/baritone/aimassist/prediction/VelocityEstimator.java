package baritone.aimassist.prediction;

import net.minecraft.world.phys.Vec3;
import java.util.LinkedList;

/**
 * Estimates entity velocity from position history.
 * Uses weighted average of recent velocity samples for smoothing.
 */
public class VelocityEstimator {

    private static final int HISTORY_SIZE = 20;
    private static final int MIN_SAMPLES = 2;

    private final LinkedList<PositionSample> history = new LinkedList<>();
    private Vec3 smoothedVelocity = Vec3.ZERO;
    private Vec3 lastPosition = null;
    private long lastTimestamp = 0;

    public void addSample(Vec3 position) {
        long now = System.nanoTime();

        if (lastPosition != null) {
            double dt = (now - lastTimestamp) / 1_000_000_000.0;
            if (dt > 0.01) { // At least 10ms between samples
                Vec3 velocity = position.subtract(lastPosition).scale(1.0 / dt);
                // Clamp velocity to realistic values (max ~100 m/s)
                if (velocity.length() < 100) {
                    history.addLast(new PositionSample(velocity, now));
                }
            }
        }

        lastPosition = position;
        lastTimestamp = now;

        // Trim history
        while (history.size() > HISTORY_SIZE) {
            history.removeFirst();
        }

        // Recalculate smoothed velocity
        if (history.size() >= MIN_SAMPLES) {
            recalculateSmoothVelocity();
        }
    }

    private void recalculateSmoothVelocity() {
        double totalWeight = 0;
        double vx = 0, vy = 0, vz = 0;

        int size = history.size();
        long baseTime = history.getFirst().timestamp;

        for (PositionSample sample : history) {
            // Weight: recency-based exponential weighting
            double age = (sample.timestamp - baseTime) / 1_000_000_000.0;
            double weight = Math.exp(age * 2.0); // Recent samples weighted higher
            totalWeight += weight;

            vx += sample.velocity.x * weight;
            vy += sample.velocity.y * weight;
            vz += sample.velocity.z * weight;
        }

        if (totalWeight > 0) {
            smoothedVelocity = new Vec3(vx / totalWeight, vy / totalWeight, vz / totalWeight);
        }
    }

    public Vec3 getVelocity() {
        return smoothedVelocity;
    }

    public boolean hasSufficientData() {
        return history.size() >= MIN_SAMPLES;
    }

    public void reset() {
        history.clear();
        smoothedVelocity = Vec3.ZERO;
        lastPosition = null;
        lastTimestamp = 0;
    }

    private static class PositionSample {
        final Vec3 velocity;
        final long timestamp;

        PositionSample(Vec3 velocity, long timestamp) {
            this.velocity = velocity;
            this.timestamp = timestamp;
        }
    }
}
