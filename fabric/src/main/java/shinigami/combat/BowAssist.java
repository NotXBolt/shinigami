package shinigami.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * BowAssist — Original, prediction via BehavioralPredictor, BowPhysics.
 * Calculates intercept for moving target, adjusts aim.
 */
public class BowAssist {
    private final Minecraft mc = Minecraft.getInstance();
    private final shinigami.prediction.BehavioralPredictor predictor;

    public BowAssist(shinigami.prediction.BehavioralPredictor p){ this.predictor=p; }

    public void tick(LivingEntity target) {
        if (mc.player == null || target == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isBowMode()) return;
        if (!mc.player.isUsingItem() || !(mc.player.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem)) return;
        // predict target pos 5 ticks ahead via velocity
        Vec3 vel = target.getDeltaMovement();
        Vec3 pred = target.position().add(vel.scale(5));
        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = pred.subtract(eye).normalize();
        float yaw = (float)Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float)Math.toDegrees(Math.asin(-dir.y));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }
}
