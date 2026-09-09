package baritone.aimassist.aim;

import baritone.api.aimassist.IAimTarget;
import baritone.api.utils.Rotation;
import baritone.aimassist.AimAssistConfig;
import baritone.aimassist.prediction.BehavioralPredictor;
import baritone.aimassist.prediction.MovementPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AimController {

    private static final Minecraft mc = Minecraft.getInstance();
    private final RotationSmoother smoother = new RotationSmoother();
    private final BehavioralPredictor behavioralPredictor = new BehavioralPredictor();

    private boolean active = false;
    private double aimSpeed = 1.0;
    private double noiseLevel = 0.0;
    private boolean silentAim = false;
    private boolean perfectLock = true; // Perfect mouse-like tracking — smooth trajectory with PID smoothing

    private static final float BASE_YAW_SPEED = 360.0f;
    private static final float BASE_PITCH_SPEED = 360.0f;

    public void updatePredictions(LivingEntity target) {
        if (target != null) behavioralPredictor.update(target);
    }

    public Rotation calculateRotation(Vec3 targetPos) {
        if (mc.player == null) return new Rotation(0, 0);
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 diff = targetPos.subtract(eyePos);
        double yaw = Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90;
        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        double pitch = -Math.toDegrees(Math.atan2(diff.y, horizontalDist));
        return new Rotation((float) yaw, (float) pitch).normalizeAndClamp();
    }

    public Rotation calculateEntityAim(LivingEntity target, Vec3 predictedPosition) {
        AABB box = target.getBoundingBox();
        Vec3 center = new Vec3(box.minX + box.getXsize() * 0.5,
            box.minY + box.getYsize() * 0.45,
            box.minZ + box.getZsize() * 0.5);
        return calculateRotation(center);
    }

    public Vec3 getConfidencePredictedPosition(IAimTarget target, int ticksAhead) {
        if (target == null || target.getEntity() == null) return Vec3.ZERO;
        LivingEntity entity = target.getEntity();
        Vec3 currentPos = entity.position();

        if (!(entity instanceof Player)) {
            Vec3 vel = entity.getDeltaMovement();
            return currentPos.add(vel.scale(ticksAhead));
        }

        double confidence = behavioralPredictor.getConfidence(entity, ticksAhead);
        Vec3 behavPred = behavioralPredictor.predictPosition(
            currentPos, entity.getDeltaMovement(), entity.getYRot(), entity.getId(), ticksAhead);
        double clamp = Math.max(0.1, Math.min(0.95, confidence));

        return new Vec3(
            currentPos.x * (1 - clamp) + behavPred.x * clamp,
            currentPos.y * (1 - clamp) + behavPred.y * clamp,
            currentPos.z * (1 - clamp) + behavPred.z * clamp
        );
    }

    public Rotation getSmoothedRotation(Rotation targetRotation, Rotation currentRotation) {
        if (!active) return targetRotation;

        AimAssistConfig config = AimAssistConfig.getInstance();
        float speed = (float) aimSpeed;

        float yawDiff = RotationSmoother.normalizeYawDelta(targetRotation.getYaw() - currentRotation.getYaw());
        float pitchDiff = targetRotation.getPitch() - currentRotation.getPitch();

        float maxYaw = BASE_YAW_SPEED * speed * (float) config.getAimSpeedX();
        float maxPitch = BASE_PITCH_SPEED * speed * (float) config.getAimSpeedY();

        float yawStep = Math.max(-maxYaw, Math.min(maxYaw, yawDiff));
        float pitchStep = Math.max(-maxPitch, Math.min(maxPitch, pitchDiff));

        Rotation result = new Rotation(
            currentRotation.getYaw() + yawStep,
            currentRotation.getPitch() + pitchStep
        ).normalizeAndClamp();

        if (noiseLevel > 0) {
            result = smoother.addNoise(result, noiseLevel);
        }

        return result;
    }

    public Vec3 getAimPoint(IAimTarget target, boolean preferHead) {
        LivingEntity entity = target.getEntity();
        AABB box = entity.getBoundingBox();
        double yOffset = preferHead ? box.getYsize() * 0.85 : box.getYsize() * 0.45;
        return new Vec3(
            box.minX + box.getXsize() * 0.5,
            box.minY + yOffset,
            box.minZ + box.getZsize() * 0.5
        );
    }

    public Vec3 getAimPointForMobType(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        double height = box.getYsize();
        double yOffset;
        if (height < 1.0) {
            yOffset = height * 0.5;
        } else if (entity instanceof net.minecraft.world.entity.monster.Witch) {
            yOffset = height * 0.55;
        } else if (height > 2.0) {
            yOffset = height * 0.35;
        } else {
            yOffset = height * 0.45;
        }
        return new Vec3(
            box.minX + box.getXsize() * 0.5,
            box.minY + yOffset,
            box.minZ + box.getZsize() * 0.5
        );
    }

    public BehavioralPredictor getBehavioralPredictor() { return behavioralPredictor; }

    public void setActive(boolean active) { this.active = active; if (active) smoother.applyPerfectSmoothTuning(); }
    public void setAimSpeed(double speed) { this.aimSpeed = speed; }
    public void setNoiseLevel(double noise) { this.noiseLevel = noise; }
    public void setSilentAim(boolean silent) { this.silentAim = silent; }
    public boolean isSilentAim() { return silentAim; }
}
