package shinigami.targeting;

import baritone.api.aimassist.IAimTarget;
import shinigami.prediction.MovementPredictor;
import shinigami.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;

import java.util.*;
import java.util.stream.Collectors;

public class TargetManager {

    private static final Minecraft mc = Minecraft.getInstance();
    private final Map<Integer, TrackedTarget> trackedTargets = new HashMap<>();
    private final Map<Integer, MovementPredictor> predictors = new HashMap<>();
    private IAimTarget primaryTarget = null;
    private AimAssistConfig config;
    private long lastScanTime = 0;
    private int scanInterval = 2;

    public TargetManager(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScanTime < 50) return;
        lastScanTime = now;

        if (primaryTarget != null) {
            Entity e = primaryTarget.getEntity();
            if (e.isRemoved() || !e.isAlive() || mc.player.distanceTo(e) > config.getDetectionRange()) {
                primaryTarget = null;
            }
        }

        List<IAimTarget> targets = scanForTargets();

        Set<Integer> currentIds = targets.stream().map(t -> t.getEntity().getId()).collect(Collectors.toSet());
        trackedTargets.keySet().removeIf(id -> !currentIds.contains(id));
        predictors.keySet().removeIf(id -> !currentIds.contains(id));

        for (IAimTarget target : targets) {
            TrackedTarget tt = (TrackedTarget) target;
            int id = target.getEntity().getId();
            tt.updateState();

            MovementPredictor predictor = predictors.computeIfAbsent(id, k -> new MovementPredictor());
            predictor.update(target.getEntity());

            double amount = config.getPredictAmount();
            if (amount <= 0) {
                tt.setPredictedPosition(target.getPosition());
            } else {
                int ticks = (int) Math.max(1, config.getPredictionTicks() * amount);
                tt.setPredictedPosition(predictor.predictPosition(ticks));
            }
            tt.setVelocity(target.getEntity().getDeltaMovement());
        }

        primaryTarget = selectBestTarget(targets);
    }

    private List<IAimTarget> scanForTargets() {
        List<IAimTarget> targets = new ArrayList<>();
        if (mc.level == null || mc.player == null) return targets;

        double detectRange = config.getDetectionRange();
        Vec3 eyePos = mc.player.getEyePosition();

        AABB scanBox = mc.player.getBoundingBox().inflate(detectRange);
        for (Entity entity : mc.level.getEntitiesOfClass(LivingEntity.class, scanBox)) {
            if (entity == mc.player) continue;
            if (!entity.isAlive() || entity.isRemoved()) continue;
            if (!isValidTarget((LivingEntity)entity)) continue;

            Vec3 entityPos = entity.position();
            double distance = eyePos.distanceTo(entityPos);
            if (distance > detectRange) continue;

            if (!isInFOV(eyePos, entityPos, mc.player.getLookAngle(), config.getFOV())) continue;

            targets.add(new TrackedTarget((LivingEntity)entity, entityPos, distance));
        }

        return targets;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity.isRemoved() || !entity.isAlive()) return false;
        if (!config.isTargetInvisible() && entity.isInvisible()) return false;

        // All entities chase/hunt/killable — no scattered filter, perfect & on point (Phase 0)
        // Player / Hostile / Passive / Golem / Any LivingEntity all valid when hunting/chasing
        if (entity instanceof Player) return true;
        if (entity instanceof net.minecraft.world.entity.animal.IronGolem) return true;
        if (entity instanceof net.minecraft.world.entity.animal.SnowGolem) return true;
        if (entity instanceof Enemy || entity instanceof net.minecraft.world.entity.monster.Monster) return true;
        if (entity instanceof Animal) return true;
        // Catch-all: any LivingEntity (villager, enderman, warden, etc) — perfect, no missed targets
        return entity instanceof LivingEntity;
    }

    private boolean isInFOV(Vec3 from, Vec3 to, Vec3 lookVec, double fovDegrees) {
        Vec3 toTarget = to.subtract(from).normalize();
        if (toTarget.lengthSqr() < 0.01) return true;
        double angle = Math.toDegrees(Math.acos(lookVec.dot(toTarget)));
        return angle <= fovDegrees / 2.0;
    }

    private IAimTarget selectBestTarget(List<IAimTarget> targets) {
        if (targets.isEmpty()) return null;

        targets.forEach(t -> ((TrackedTarget) t).calculateScore(config));
        return targets.stream().max(Comparator.comparingDouble(IAimTarget::getPriorityScore)).orElse(null);
    }

    public IAimTarget getPrimaryTarget() { return primaryTarget; }
    public Map<Integer, MovementPredictor> getPredictors() { return predictors; }
    public void setConfig(AimAssistConfig config) { this.config = config; }

    public void clearTarget() {
        primaryTarget = null;
        trackedTargets.clear();
        predictors.clear();
    }

    public void forceScan() {
        lastScanTime = 0;
        tick();
    }
}
