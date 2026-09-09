package shinigami.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * KeyController — Original, 3-layer: supplement (OR) vs override (moveToward).
 * Phase 0: supplementForward/supplementJump for chase/pakur, moveToward for dodge/clutch 5-tick TTL.
 */
public class KeyController {

    private final Minecraft mc = Minecraft.getInstance();
    private boolean supplementForward = false;
    private boolean supplementJump = false;
    private boolean overrideActive = false;
    private Vec3 overrideDir = Vec3.ZERO;
    private boolean overrideSprint = false;
    private boolean overrideJump = false;
    private boolean overrideSneak = false;
    private int overrideTicks = 0;

    public void clearSupplement() { supplementForward = false; supplementJump = false; }

    public void supplementForward(boolean sprint) { supplementForward = true; if (sprint) mc.options.keySprint.setDown(true); }

    public void supplementJump() { supplementJump = true; }

    public void moveToward(Vec3 dir, boolean sprint, boolean jump, boolean sneak) {
        overrideActive = true;
        overrideDir = dir;
        overrideSprint = sprint;
        overrideJump = jump;
        overrideSneak = sneak;
        overrideTicks = 5; // OVERRIDE_TTL = 5
    }

    public void tick() {
        if (overrideActive) {
            if (overrideTicks-- <= 0) overrideActive = false;
        }
    }

    public void applyInputs() {
        if (overrideActive && overrideDir.lengthSqr() > 1e-6) {
            // Override WASD via Input.getMoveVector() convention: strafe = (left-right)
            double yaw = Math.toRadians(mc.player.getYRot());
            Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 strafe = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            double fwd = overrideDir.dot(forward);
            double str = overrideDir.dot(strafe);
            // Apply via movement input (simplified: supplement forward + strafe via KeyController)
            if (fwd > 0.1) supplementForward = true;
            if (overrideJump && mc.player.onGround()) mc.player.jumpFromGround();
            if (overrideSprint) mc.player.setSprinting(true);
            if (overrideSneak) mc.options.keyShift.setDown(true);
        }
        if (supplementForward) mc.options.keyUp.setDown(true);
        if (supplementJump && mc.player.onGround()) mc.player.jumpFromGround();
    }

    public void stopMoving() { clearSupplement(); overrideActive = false; mc.options.keyUp.setDown(false); mc.options.keyDown.setDown(false); mc.options.keyLeft.setDown(false); mc.options.keyRight.setDown(false); }
}
