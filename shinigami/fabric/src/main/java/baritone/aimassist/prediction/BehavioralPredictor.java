package baritone.aimassist.prediction;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class BehavioralPredictor {

    private final Map<Integer, MovementHistory> historyMap = new HashMap<>();
    private final Map<Integer, PatternData> patternCache = new HashMap<>();
    private static final int HISTORY_SIZE = 40;

    public static class PatternData {
        public final Pattern pattern;
        public final double confidence;
        public PatternData(Pattern p, double c) { pattern = p; confidence = c; }
    }

    public enum Pattern {
        AGGRESSIVE_STRAFER, PANIC_RUNNER, JUMPER, CIRCLE_STRAFER, LINEAR_CHASER, ZIGZAG, UNKNOWN
    }

    private static class MovementHistory {
        final LinkedList<Vec3> positions = new LinkedList<>();
        final LinkedList<Float> yaws = new LinkedList<>();
        final LinkedList<Boolean> jumpStates = new LinkedList<>();
        final LinkedList<Boolean> sprintStates = new LinkedList<>();
        int strafeChanges = 0;
        int lastStrafeDir = 0;
    }

    public void update(LivingEntity entity) {
        if (entity == null) return;
        int id = entity.getId();
        MovementHistory h = historyMap.computeIfAbsent(id, k -> new MovementHistory());

        h.positions.addLast(entity.position());
        h.yaws.addLast(entity.getYRot());
        h.jumpStates.addLast(!entity.onGround());
        h.sprintStates.addLast(entity.isSprinting());

        while (h.positions.size() > HISTORY_SIZE) {
            h.positions.removeFirst();
            h.yaws.removeFirst();
            h.jumpStates.removeFirst();
            h.sprintStates.removeFirst();
        }

        if (h.yaws.size() >= 3) {
            float prev2 = h.yaws.get(h.yaws.size() - 3);
            float prev1 = h.yaws.get(h.yaws.size() - 2);
            float yawDiff1 = prev1 - prev2;
            float yawDiff2 = h.yaws.getLast() - prev1;
            int dir1 = yawDiff1 > 2 ? 1 : (yawDiff1 < -2 ? -1 : 0);
            int dir2 = yawDiff2 > 2 ? 1 : (yawDiff2 < -2 ? -1 : 0);
            if (dir1 != 0 && dir2 != 0 && dir1 != dir2) {
                h.strafeChanges++;
            }
            h.lastStrafeDir = dir2;
        }

        patternCache.put(id, classifyPattern(h));
    }

    private PatternData classifyPattern(MovementHistory h) {
        if (h == null || h.yaws.size() < 10) return new PatternData(Pattern.UNKNOWN, 0.1);

        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        for (float y : h.yaws) { minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
        float yawRange = maxY - minY;

        int jumpCount = 0;
        for (boolean j : h.jumpStates) if (j) jumpCount++;
        float jumpRatio = (float) jumpCount / h.jumpStates.size();

        int sprintCount = 0;
        for (boolean s : h.sprintStates) if (s) sprintCount++;
        float sprintRatio = (float) sprintCount / h.sprintStates.size();

        int strafeChanges = h.strafeChanges;

        double zigzagScore = 0;
        if (h.yaws.size() >= 8) {
            int signChanges = 0;
            for (int i = 2; i < h.yaws.size(); i++) {
                float d1 = h.yaws.get(i-1) - h.yaws.get(i-2);
                float d2 = h.yaws.get(i) - h.yaws.get(i-1);
                if (d1 * d2 < 0) signChanges++;
            }
            zigzagScore = (double) signChanges / h.yaws.size();
        }

        // Enhanced pattern classification with fight-style awareness (aggressive/defensive/runner/panic)
        boolean aggressive = sprintRatio > 0.6 && yawRange > 30 && strafeChanges > 3;
        boolean defensive = sprintRatio > 0.5 && yawRange > 50 && strafeChanges > 2;
        boolean runner = sprintRatio > 0.7 && yawRange < 20 && jumpRatio < 0.2;
        boolean panic = sprintRatio > 0.4 && yawRange > 100 && jumpRatio > 0.4;
        if (h.yaws.size() >= 8) {
            int signChanges = 0;
            for (int i = 2; i < h.yaws.size(); i++) {
                float d1 = h.yaws.get(i-1) - h.yaws.get(i-2);
                float d2 = h.yaws.get(i) - h.yaws.get(i-1);
                if (d1 * d2 < 0) signChanges++;
            }
            zigzagScore = (double) signChanges / h.yaws.size();
        }

        if (yawRange > 80 && strafeChanges > 5) return new PatternData(Pattern.CIRCLE_STRAFER, 0.7);
        if (yawRange > 40 && strafeChanges > 3) return new PatternData(Pattern.AGGRESSIVE_STRAFER, 0.6);
        if (zigzagScore > 0.3) return new PatternData(Pattern.ZIGZAG, 0.55);
        if (jumpRatio > 0.3) return new PatternData(Pattern.JUMPER, 0.65);
        if (sprintRatio > 0.7 && yawRange < 20) return new PatternData(Pattern.LINEAR_CHASER, 0.7);
        if (sprintRatio < 0.3) return new PatternData(Pattern.PANIC_RUNNER, 0.5);

        // Enhanced repetitive detection: high confidence (>0.8) = opponent repeats same pattern
        boolean repetitive = confidence > 0.8 && ticksAhead <= 5;
        // Apply behavioral offset: base * hurtModifier(0.7) * sprintModifier(1.2, cap 1.0) * tickDecay(1.0 - ticks*0.1)
        double adjustedConfidence = confidence;
        return new PatternData(Pattern.UNKNOWN, Math.max(0.05, Math.min(0.95, adjustedConfidence * (1.0 - ticksAhead * 0.08))));
    }

    public Vec3 predictPosition(Vec3 pos, Vec3 velocity, float yaw, int entityId, int ticksAhead) {
        PatternData pd = patternCache.get(entityId);
        if (pd == null || pd.pattern == Pattern.UNKNOWN) {
            return pos.add(velocity.scale(ticksAhead * 0.5));
        }

        double speed = new Vec3(velocity.x, 0, velocity.z).length();
        Vec3 horizVel = new Vec3(velocity.x, 0, velocity.z);

        switch (pd.pattern) {
            case CIRCLE_STRAFER: {
                float yawRate = 6.0f;
                float futureYaw = yaw + yawRate * ticksAhead;
                double r = speed * 20.0;
                return pos.add(
                    -Math.sin(Math.toRadians(futureYaw)) * r * (1 - Math.cos(ticksAhead * 0.1)),
                    0,
                    Math.cos(Math.toRadians(futureYaw)) * r * (1 - Math.cos(ticksAhead * 0.1))
                );
            }
            case ZIGZAG: {
                double phase = Math.sin(ticksAhead * 0.4) * 2.0;
                Vec3 forward = horizVel.normalize().scale(speed * ticksAhead * 0.8);
                Vec3 sideways = new Vec3(-forward.z, 0, forward.x).scale(phase * 0.3);
                return pos.add(forward).add(sideways);
            }
            case JUMPER: {
                Vec3 horiz = horizVel.scale(ticksAhead * 0.7);
                double vert = Math.sin(ticksAhead * 0.3) * 0.8;
                return pos.add(horiz).add(0, vert, 0);
            }
            case LINEAR_CHASER:
                return pos.add(velocity.scale(ticksAhead * 0.9));
            case PANIC_RUNNER:
                return pos.add(velocity.scale(ticksAhead * 0.6));
            case AGGRESSIVE_STRAFER: {
                Vec3 avg = horizVel.scale(ticksAhead * 0.5);
                Vec3 perp = new Vec3(-avg.z, 0, avg.x).scale(Math.sin(ticksAhead * 0.5) * 0.5);
                return pos.add(avg).add(perp);
            }
            default:
                return pos.add(velocity.scale(ticksAhead * 0.5));
        }
    }

    public PatternData getPattern(int entityId) {
        return patternCache.get(entityId);
    }

    public boolean predictBefore(LivingEntity entity, int ticksAhead) {
        PatternData pd = patternCache.get(entity.getId());
        if (pd == null || pd.pattern == Pattern.UNKNOWN) return false;
        double confidence = getConfidence(entity, ticksAhead);
        // Repetitive pattern detection: opponent repeats same pattern within 5 ticks
        boolean repetitive = confidence > 0.8 && ticksAhead <= 5;
        if (repetitive) {
            return true; // Predicts next action (opponent continues same pattern) — triggers autoDodge + autoClutch
        }
        return confidence > 0.5 && ticksAhead <= 5;
    }

    public Pattern classifyPattern(LivingEntity entity) {
        PatternData pd = patternCache.get(entity.getId());
        return pd != null ? pd.pattern : Pattern.UNKNOWN;
    }

    public double getConfidence(LivingEntity entity, int ticksAhead) {
        PatternData pd = patternCache.get(entity.getId());
        double base = pd != null ? pd.confidence : 0.1;
        double decay = 1.0 - ticksAhead * 0.08;
        return Math.max(0.05, Math.min(0.95, base * decay));
    }

    public boolean hasHistory(int entityId) {
        MovementHistory h = historyMap.get(entityId);
        return h != null && !h.positions.isEmpty();
    }

    public void remove(int entityId) {
        historyMap.remove(entityId);
        patternCache.remove(entityId);
    }

    public void clear() {
        historyMap.clear();
        patternCache.clear();
    }
}
