package baritone.aimassist.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class AirStrafeController {
    private final Minecraft mc = Minecraft.getInstance();

    private float targetYaw = 0;
    private boolean active = false;

    public void setTargetYaw(float yaw) { this.targetYaw = yaw; active = true; }

    public MovementIntent getStrafeIntent() {
        if (mc.player == null || mc.player.onGround()) {
            active = false;
            return null;
        }

        Vec3 vel = mc.player.getDeltaMovement();
        double horizSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (horizSpeed < 0.01) return null;

        float velYaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
        float yawDiff = targetYaw - velYaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        if (Math.abs(yawDiff) < 1) return null;

        // Air strafe: apply A/D to turn velocity toward target yaw
        // Positive strafe = left turn in Minecraft's air strafe model
        double strafeDir = yawDiff > 0 ? 1 : -1;

        // Only strafe if we need meaningful turn (otherwise forward is enough)
        if (Math.abs(yawDiff) < 5) strafeDir = 0;

        float yawRad = (float) Math.toRadians(mc.player.getYRot());
        Vec3 strafeVec = new Vec3(
            -Math.sin(yawRad) * strafeDir,
            0,
            Math.cos(yawRad) * strafeDir
        );
        if (strafeVec.lengthSqr() > 0.01) strafeVec = strafeVec.normalize();

        return new MovementIntent(
            MovementIntent.Priority.PARKOUR,
            strafeVec, true, MovementIntent.JumpType.NONE,
            false, 1, "air-strafe"
        );
    }

    public void setActive(boolean a) { this.active = a; }
    public void reset() { active = false; }
}
