package baritone.api.aimassist;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public interface IAimTarget {

    LivingEntity getEntity();

    Vec3 getPosition();

    Vec3 getPredictedPosition();

    Vec3 getVelocity();

    double getDistance();

    float getHealth();

    float getMaxHealth();

    float getAngleDifference();

    double getPriorityScore();

    long getLastSeenTime();

    boolean isVisible();

    boolean isAttacking();

    int getComboCount();
}
