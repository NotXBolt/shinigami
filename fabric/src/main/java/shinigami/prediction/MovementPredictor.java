package shinigami.prediction;

import baritone.api.aimassist.PredictionData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.math3.filter.KalmanFilter;

public class MovementPredictor {

    private KalmanFilter kalman;
    private final VelocityEstimator velocityEstimator = new VelocityEstimator();
    private final JumpArcPredictor jumpPredictor = new JumpArcPredictor();
    private final BehavioralPredictor behavioralPredictor = new BehavioralPredictor();

    private Vec3 lastPosition = Vec3.ZERO;
    private boolean initialized = false;
    private boolean wasJumping = false;
    private int lastEntityId = -1;

    public MovementPredictor() {
        kalman = PhysicsUtils.createPlayerKalmanFilter();
    }

    public void update(LivingEntity entity) {
        if (entity == null) return;
        Vec3 pos = entity.position();
        boolean jumping = !entity.onGround();
        lastEntityId = entity.getId();

        try {
            kalman.predict();
            kalman.correct(new double[]{pos.x, pos.y, pos.z});
        } catch (Exception e) {
            kalman = PhysicsUtils.createPlayerKalmanFilter();
        }

        velocityEstimator.addSample(pos);
        behavioralPredictor.update(entity);

        if (wasJumping && !jumping) {
            // landing detected - no action needed
        }
        wasJumping = jumping;
        lastPosition = pos;
        initialized = true;
    }

    public Vec3 predictPosition(int ticksAhead) {
        if (!initialized) return lastPosition;
        Vec3 vel = getEstimatedVelocity();
        Vec3 pos = getEstimatedPosition();
        double speed = new Vec3(vel.x, 0, vel.z).length();

        if (speed < 0.01) return pos;

        boolean onGround = false;
        Vec3 physicsPred = PhysicsUtils.predictPlayerPosition(pos, vel, ticksAhead, onGround);

        if (lastEntityId >= 0 && behavioralPredictor.hasHistory(lastEntityId)) {
            try {
                Vec3 behavPred = behavioralPredictor.predictPosition(
                    pos, vel, 0, lastEntityId, ticksAhead);
                if (behavPred != null) {
                    BehavioralPredictor.PatternData pd = behavioralPredictor.getPattern(lastEntityId);
                    double patternConf = pd != null ? pd.confidence : 0.2;
                    if (patternConf > 0.5) {
                        Vec3 offset = behavPred.subtract(pos).scale(patternConf * 0.4);
                        return physicsPred.add(offset);
                    }
                }
            } catch (Exception e) { }
        }

        return physicsPred;
    }

    public PredictionData getPrediction(int ticksAhead) {
        Vec3 predicted = predictPosition(ticksAhead);
        Vec3 velocity = getEstimatedVelocity();
        double speed = new Vec3(velocity.x, 0, velocity.z).length();

        double baseConf = speed < 0.01 ? 0.95 : (speed > 0.5 ? 0.6 : 0.7);
        double decay = 1.0 - ticksAhead * 0.08;
        double confidence = Math.max(0.05, Math.min(0.95, baseConf * decay));

        return new PredictionData(predicted, velocity, confidence, wasJumping, wasJumping && velocity.y < 0, ticksAhead);
    }

    public Vec3 getEstimatedPosition() {
        if (!initialized) return lastPosition;
        return PhysicsUtils.extractKalmanState(kalman);
    }

    public Vec3 getEstimatedVelocity() {
        if (!initialized) return Vec3.ZERO;
        return velocityEstimator.getVelocity();
    }

    public double getConfidence(int ticksAhead) {
        return getPrediction(ticksAhead).getConfidence();
    }

    public void reset() {
        kalman = PhysicsUtils.createPlayerKalmanFilter();
        velocityEstimator.reset();
        lastPosition = Vec3.ZERO;
        wasJumping = false;
        initialized = false;
    }

    public boolean isInitialized() { return initialized; }
    public Vec3 getLastPosition() { return lastPosition; }
    public BehavioralPredictor getBehavioralPredictor() { return behavioralPredictor; }
}
