package shinigami.targeting;

import baritone.api.aimassist.IAimConfig;
import baritone.api.aimassist.IAimTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class TrackedTarget implements IAimTarget {

    private final LivingEntity entity;
    private final Vec3 position;
    private final double distance;
    private Vec3 predictedPosition;
    private Vec3 velocity = Vec3.ZERO;
    private long lastSeenTime;
    private int comboCount = 0;
    private double priorityScore = 0;
    private float recentDamageTaken = 0;
    private int recentDamageTicks = 0;
    private boolean targetingMe = false;
    private boolean hasShield = false;

    public TrackedTarget(LivingEntity entity, Vec3 position, double distance) {
        this.entity = entity;
        this.position = position;
        this.distance = distance;
        this.predictedPosition = position;
        this.lastSeenTime = System.currentTimeMillis();
    }

    public void setPredictedPosition(Vec3 pos) {
        this.predictedPosition = pos;
    }

    public void updateState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (recentDamageTicks > 0) recentDamageTicks--;

        if (recentDamageTicks <= 0) recentDamageTaken = 0;

        float prevHealth = entity.getHealth();
        if (prevHealth < entity.getMaxHealth()) {
            targetingMe = entity.getLastHurtByMob() == mc.player || 
                (entity instanceof Player && ((Player)entity).getLastHurtByPlayer() == mc.player);
        } else {
            targetingMe = entity.getLastHurtMob() == mc.player || 
                (entity instanceof Player && ((Player)entity).getLastHurtByPlayer() == mc.player);
        }

        hasShield = entity instanceof Player && (
            entity.getMainHandItem().is(net.minecraft.world.item.Items.SHIELD) ||
            entity.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD)
        );

        if (entity.hurtTime > 0 && recentDamageTicks == 0) {
            recentDamageTicks = 20;
        }
    }

    public void recalcDamageTaken(float damage) {
        recentDamageTaken += damage;
        recentDamageTicks = 40;
    }

    public void calculateScore(IAimConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { priorityScore = 0; return; }

        Vec3 lookVec = mc.player.getLookAngle();
        Vec3 toTarget = entity.position().subtract(mc.player.getEyePosition()).normalize();
        double angle = toTarget.lengthSqr() > 0.01 ?
            Math.toDegrees(Math.acos(Math.min(1, Math.max(-1, lookVec.dot(toTarget))))) : 180;

        double maxDist = config.getDetectionRange() > 0 ? config.getDetectionRange() : 64;

        double threatWeight;
        if (entity instanceof Player) threatWeight = 100;
        else if (entity instanceof net.minecraft.world.entity.monster.Enemy) threatWeight = 40;
        else if (entity instanceof net.minecraft.world.entity.animal.Animal) threatWeight = 10;
        else threatWeight = 20;

        double dmgWeight = Math.min(50, recentDamageTaken * 10);
        double targetingBonus = targetingMe ? 30 : 0;
        double distPenalty = Math.max(0, 1.0 - distance / maxDist) * 5;
        double healthBonus = (1.0 - entity.getHealth() / Math.max(1, entity.getMaxHealth())) * 20;

        priorityScore = threatWeight + dmgWeight + targetingBonus + distPenalty + healthBonus;
    }

    @Override
    public LivingEntity getEntity() { return entity; }
    @Override
    public Vec3 getPosition() { return position; }
    @Override
    public Vec3 getPredictedPosition() { return predictedPosition != null ? predictedPosition : position; }
    @Override
    public Vec3 getVelocity() { return velocity; }
    @Override
    public double getDistance() { return distance; }
    @Override
    public float getHealth() { return entity.getHealth(); }
    @Override
    public float getMaxHealth() { return entity.getMaxHealth(); }
    @Override
    public float getAngleDifference() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Vec3 lookVec = mc.player.getLookAngle();
        Vec3 toTarget = entity.position().subtract(mc.player.getEyePosition()).normalize();
        if (toTarget.lengthSqr() < 0.01) return 0;
        return (float) Math.toDegrees(Math.acos(lookVec.dot(toTarget)));
    }
    @Override
    public double getPriorityScore() { return priorityScore; }
    @Override
    public long getLastSeenTime() { return lastSeenTime; }
    @Override
    public boolean isVisible() { return true; }
    @Override
    public boolean isAttacking() { return false; }
    @Override
    public int getComboCount() { return comboCount; }

    public void incrementCombo() { comboCount++; }
    public void resetCombo() { comboCount = 0; }
    public void setVelocity(Vec3 v) { this.velocity = v; }
    public float getRecentDamageTaken() { return recentDamageTaken; }
    public boolean isTargetingMe() { return targetingMe; }
    public boolean hasShield() { return hasShield; }
}
