package shinigami.render;

import baritone.api.aimassist.IAimTarget;
import baritone.api.aimassist.PredictionData;
import shinigami.AimAssistConfig;
import shinigami.AimAssistModule;
import shinigami.prediction.MovementPredictor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;

public class AimAssistRenderer {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module = AimAssistModule.getInstance();
    private final AimAssistConfig config = AimAssistConfig.getInstance();

    public void render(PoseStack poseStack, Camera camera, float partialTick) {
        try {
            if (!config.isEnabled()) return;

            IAimTarget target = module.getCurrentTarget();
            Vec3 camPos = camera.position();

            // ─── ESP Box ────────────────────────────────────────
            if (config.isShowTargetInfo() && target != null) {
                LivingEntity entity = target.getEntity();
                if (entity != null && entity.isAlive() && !entity.isRemoved()) {
                    AABB bb = entity.getBoundingBox().move(entity.position().subtract(camPos));
                    float healthRatio = entity.getHealth() / Math.max(1, entity.getMaxHealth());
                    float r = 1 - healthRatio;
                    float g = healthRatio;
                    drawBoxOutline(poseStack, bb, r, g, 0, 0.6f);

                    // Health bar above box
                    float bbWidth = (float)(bb.maxX - bb.minX);
                    float barY = (float)bb.maxY + 0.1f;
                    float barX = (float)(bb.minX + bb.maxX) / 2 - bbWidth / 2;
                    drawHealthBar(poseStack, barX, barY, bbWidth, 0.1f, healthRatio);
                }
            }

            // ─── Prediction Ghost ──────────────────────────────
            if (config.isShowPrediction() && target != null) {
                Map<Integer, MovementPredictor> predictors = module.getTargetManager().getPredictors();
                int entityId = target.getEntity().getId();
                MovementPredictor predictor = predictors.get(entityId);

                if (predictor != null && predictor.isInitialized()) {
                    for (int tick = 1; tick <= Math.min(config.getPredictionTicks(), 10); tick++) {
                        PredictionData data = predictor.getPrediction(tick);
                        if (data == null) continue;

                        Vec3 predPos = data.getPredictedPosition().subtract(camPos);
                        float alpha = (float) (1.0 - (tick / (float) Math.max(1, config.getPredictionTicks())));
                        alpha *= 0.4f;

                        float confidence = (float) data.getConfidence();
                        float r = 1 - confidence;
                        float g = confidence;
                        float b = 0.2f;

                        if (data.isJumping()) {
                            r = 1; g = 0.3f; b = 0;
                            alpha *= 1.2f;
                        }

                        AABB bb = target.getEntity().getBoundingBox();
                        AABB renderBB = bb.move(predPos.subtract(target.getPosition().subtract(camPos)));
                        drawBoxOutline(poseStack, renderBB, r, g, b, Math.min(1, alpha));

                        // Confidence meter below ghost
                        float confW = (float)(renderBB.maxX - renderBB.minX) * confidence;
                        float confY = (float)renderBB.minY - 0.15f;
                        drawHealthBar(poseStack, (float)renderBB.minX, confY, confW, 0.05f, confidence);
                    }

                    // Velocity tracer line
                    Vec3 velocity = predictor.getEstimatedVelocity();
                    if (velocity.length() > 0.1) {
                        Vec3 pos = target.getPosition().subtract(camPos);
                        Vec3 velEnd = pos.add(velocity.scale(2));
                        drawLine(poseStack, pos, velEnd, 0, 1, 0, 0.8f);
                    }
                }
            }

            // ─── Tracers ───────────────────────────────────────
            if (config.isShowTrajectory() && target != null) {
                LivingEntity entity = target.getEntity();
                if (entity != null && entity.isAlive()) {
                    Vec3 from = new Vec3(0, 0, 0); // camera origin
                    Vec3 to = entity.getBoundingBox().getCenter().subtract(camPos);
                    float healthRatio = entity.getHealth() / Math.max(1, entity.getMaxHealth());
                    drawLine(poseStack, from, to, 1 - healthRatio, healthRatio, 0.3f, 0.3f);
                }
            }
        } catch (Exception e) {
            // Silent fail - renderer must never crash
        }
    }

    private void drawHealthBar(PoseStack poseStack, float x, float y, float width, float height, float ratio) {
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderTypes.LINES);
        PoseStack.Pose pose = poseStack.last();
        int bg = 0x33 << 24;
        int fg;
        if (ratio > 0.6) fg = 0xFF00FF00;
        else if (ratio > 0.3) fg = 0xFFFFAA00;
        else fg = 0xFFFF0000;
        // Background
        drawLine3d(pose, consumer, x, y, 0, x + width, y, 0, 0, 0, 0, 80);
        // Foreground
        drawLine3d(pose, consumer, x, y, 0, x + width * ratio, y, 0,
            (fg >> 16) & 0xFF, (fg >> 8) & 0xFF, fg & 0xFF, 200);
    }

    private void drawBoxOutline(PoseStack poseStack, AABB bb, float r, float g, float b, float alpha) {
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderTypes.LINES);
        PoseStack.Pose pose = poseStack.last();

        float minX = (float) bb.minX;
        float minY = (float) bb.minY;
        float minZ = (float) bb.minZ;
        float maxX = (float) bb.maxX;
        float maxY = (float) bb.maxY;
        float maxZ = (float) bb.maxZ;

        int ri = (int)(r * 255);
        int gi = (int)(g * 255);
        int bi = (int)(b * 255);
        int ai = (int)(alpha * 255);

        drawLine3d(pose, consumer, minX, minY, minZ, maxX, minY, minZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, maxX, minY, minZ, maxX, minY, maxZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, maxX, minY, maxZ, minX, minY, maxZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, minX, minY, maxZ, minX, minY, minZ, ri, gi, bi, ai);

        drawLine3d(pose, consumer, minX, maxY, minZ, maxX, maxY, minZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, maxX, maxY, maxZ, minX, maxY, maxZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, minX, maxY, maxZ, minX, maxY, minZ, ri, gi, bi, ai);

        drawLine3d(pose, consumer, minX, minY, minZ, minX, maxY, minZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, maxX, minY, minZ, maxX, maxY, minZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, ri, gi, bi, ai);
        drawLine3d(pose, consumer, minX, minY, maxZ, minX, maxY, maxZ, ri, gi, bi, ai);
    }

    private void drawLine(PoseStack poseStack, Vec3 from, Vec3 to, float r, float g, float b, float alpha) {
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderTypes.LINES);
        PoseStack.Pose pose = poseStack.last();
        int ri = (int)(r * 255);
        int gi = (int)(g * 255);
        int bi = (int)(b * 255);
        int ai = (int)(alpha * 255);
        drawLine3d(pose, consumer, (float)from.x, (float)from.y, (float)from.z,
                   (float)to.x, (float)to.y, (float)to.z, ri, gi, bi, ai);
    }

    private void drawLine3d(PoseStack.Pose pose, VertexConsumer consumer,
                            float x1, float y1, float z1, float x2, float y2, float z2,
                            int r, int g, int b, int a) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
        consumer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0, 1, 0);
    }
}
