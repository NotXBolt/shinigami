package shinigami.prediction;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * BehavioralPredictor — Original, 6 patterns, 40 tick history, predictBefore.
 */
public class BehavioralPredictor {
    private final Map<Integer, Deque<Vec3>> posHist = new HashMap<>();
    private final Map<Integer, Deque<Float>> yawHist = new HashMap<>();
    private final Map<Integer, PatternData> cache = new HashMap<>();
    public static class PatternData { public final Pattern p; public final double c; public PatternData(Pattern p, double c){this.p=p; this.c=c;} }
    public enum Pattern { CIRCLE_STRAFER, AGGRESSIVE_STRAFER, JUMPER, LINEAR_CHASER, PANIC_RUNNER, ZIGZAG, UNKNOWN }

    public void update(LivingEntity e) {
        if (e == null) return;
        int id = e.getId();
        Deque<Vec3> pos = posHist.computeIfAbsent(id, k -> new ArrayDeque<>());
        Deque<Float> yaw = yawHist.computeIfAbsent(id, k -> new ArrayDeque<>());
        pos.addLast(e.position()); yaw.addLast(e.getYRot());
        while (pos.size() > 40) { pos.removeFirst(); yaw.removeFirst(); }
        cache.put(id, classify(id));
    }

    private PatternData classify(int id) {
        Deque<Float> yaw = yawHist.get(id);
        if (yaw == null || yaw.size() < 10) return new PatternData(Pattern.UNKNOWN, 0.1);
        List<Float> yaws = new ArrayList<>(yaw);
        float min = Collections.min(yaws), max = Collections.max(yaws);
        float range = max - min;
        // simple zigzag
        int changes = 0;
        for (int i=2;i<yaws.size();i++) {
            float a = yaws.get(i-1)-yaws.get(i-2);
            float b = yaws.get(i)-yaws.get(i-1);
            if (a*b < 0) changes++;
        }
        double zig = (double)changes / yaws.size();
        if (range > 80 && changes > 5) return new PatternData(Pattern.CIRCLE_STRAFER, 0.7);
        if (range > 40 && changes > 3) return new PatternData(Pattern.AGGRESSIVE_STRAFER, 0.6);
        if (zig > 0.3) return new PatternData(Pattern.ZIGZAG, 0.55);
        return new PatternData(Pattern.UNKNOWN, 0.15);
    }

    public boolean predictBefore(LivingEntity e, int ticks) {
        PatternData pd = cache.get(e.getId());
        if (pd == null || pd.p == Pattern.UNKNOWN) return false;
        double conf = getConfidence(e, ticks);
        return conf > 0.5 && ticks <= 5 || conf > 0.8;
    }

    public double getConfidence(LivingEntity e, int ticks) {
        PatternData pd = cache.get(e.getId());
        double base = pd != null ? pd.c : 0.1;
        double decay = 1.0 - ticks * 0.08;
        return Math.max(0.05, Math.min(0.95, base * decay));
    }

    public Pattern getPattern(int id) { PatternData pd = cache.get(id); return pd != null ? pd.p : Pattern.UNKNOWN; }
}
