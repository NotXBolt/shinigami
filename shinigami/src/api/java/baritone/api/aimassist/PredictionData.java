package baritone.api.aimassist;

import net.minecraft.world.phys.Vec3;

public class PredictionData {

    private final Vec3 predictedPosition;
    private final Vec3 predictedVelocity;
    private final double confidence;
    private final boolean jumping;
    private final boolean falling;
    private final int tick;

    public PredictionData(
        Vec3 predictedPosition,
        Vec3 predictedVelocity,
        double confidence,
        boolean jumping,
        boolean falling,
        int tick
    ) {
        this.predictedPosition = predictedPosition;
        this.predictedVelocity = predictedVelocity;
        this.confidence = confidence;
        this.jumping = jumping;
        this.falling = falling;
        this.tick = tick;
    }

    public Vec3 getPredictedPosition() {
        return predictedPosition;
    }

    public Vec3 getPredictedVelocity() {
        return predictedVelocity;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isJumping() {
        return jumping;
    }

    public boolean isFalling() {
        return falling;
    }

    public int getTick() {
        return tick;
    }
}
