package baritone.aimassist.combat;

import baritone.aimassist.AimAssistConfig;
import baritone.api.utils.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class BowAssist {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean active = false;
    private int drawTicks = 0;
    private Rotation lastBowAim = null;

    private static final double ARROW_SPEED = 3.0;
    private static final double GRAVITY = 0.05;
    private static final double DRAG = 0.99;

    public BowAssist(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (!active || mc.player == null) return;

        boolean drawing = mc.player.isUsingItem() &&
            (mc.player.getMainHandItem().is(Items.BOW) || mc.player.getOffhandItem().is(Items.BOW));

        if (drawing) {
            drawTicks++;
        } else {
            drawTicks = 0;
            lastBowAim = null;
        }
    }

    public Rotation calculateBowAim(LivingEntity target, Vec3 predictedPos) {
        if (mc.player == null) return null;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 targetPos = predictedPos.add(0, target.getBbHeight() * 0.3, 0);

        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.01) return null;

        float power = BowItem.getPowerForTime(Math.min(drawTicks, 20));
        double arrowSpeed = ARROW_SPEED * power;
        if (arrowSpeed < 0.5) return null;

        double bestPitch = 0;
        double bestDist = Double.MAX_VALUE;
        Rotation bestRot = null;

        double dirX = dx / horizontalDist;
        double dirZ = dz / horizontalDist;

        for (double pitch = -60; pitch <= -5; pitch += 0.5) {
            double rad = Math.toRadians(pitch);
            double vx = Math.cos(rad) * arrowSpeed;
            double vy = -Math.sin(rad) * arrowSpeed;

            double simX = 0, simY = 0, simZ = 0;
            double svx = vx * dirX, svy = vy, svz = vx * dirZ;

            for (int t = 0; t < 200; t++) {
                simX += svx;
                simY += svy;
                simZ += svz;

                svx *= DRAG;
                svy *= DRAG;
                svy -= GRAVITY;
                svz *= DRAG;

                double ex = simX - dx;
                double ey = simY - dy;
                double ez = simZ - dz;
                double dist = Math.sqrt(ex*ex + ey*ey + ez*ez);

                if (dist < bestDist) {
                    bestDist = dist;
                    bestPitch = pitch;
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    bestRot = new Rotation(yaw, (float) pitch);
                }

                if (dist < 0.5) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    Rotation hit = new Rotation(yaw, (float) pitch).normalizeAndClamp();
                    lastBowAim = hit;
                    return hit;
                }
            }
        }

        if (bestDist < 3.0 && bestRot != null) {
            lastBowAim = bestRot.normalizeAndClamp();
            return lastBowAim;
        }

        return null;
    }

    public Rotation getBowAimOverride(LivingEntity target) {
        if (!active || target == null || drawTicks < 5) return null;
        Vec3 predicted = predictBowTarget(target, (int)(3 + drawTicks / 10f));
        return calculateBowAim(target, predicted);
    }

    public Vec3 predictBowTarget(LivingEntity target, int ticksAhead) {
        Vec3 pos = target.position();
        Vec3 vel = target.getDeltaMovement();
        for (int i = 0; i < ticksAhead; i++) {
            pos = pos.add(vel);
            vel = vel.scale(0.98);
        }
        return pos;
    }

    public boolean isDrawing() { return drawTicks > 0; }
    public int getDrawTicks() { return drawTicks; }

    public void setActive(boolean a) {
        this.active = a;
        if (!a) { drawTicks = 0; lastBowAim = null; }
    }
    public boolean isActive() { return active; }
}
