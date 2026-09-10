package baritone.aimassist.learning;

import baritone.aimassist.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * ReinforcementLearner — real tabular Q-learning over combat micro-decisions.
 * No external ML deps (Java only). Learns which dodge direction, engage timing,
 * strafe direction and weapon class produce the most reward over time.
 *
 * State (discretised): threatCluster (count + nearest-dist buckets),
 * health bucket, weapon class, airborne, target-dist bucket.
 * Action spaces: DODGE(5) / ENGAGE(2) / STRAFE(2) / WEAPON(5).
 * Reward: damageDealt*scale, damageTaken*(-scale), dodge success, kill, death.
 * Q-learning: ε-greedy (ε 0.3→0.05), α=0.1, γ=0.9. Table persisted to ~/shinigami_qtable.bin.
 */
public class ReinforcementLearner {

    public enum Space {
        DODGE(5),
        ENGAGE(2),
        STRAFE(2),
        WEAPON(5);

        final int actionCount;
        Space(int n) { this.actionCount = n; }
    }

    public static final int ACT_PERPL = 0;
    public static final int ACT_PERPR = 1;
    public static final int ACT_CIRCLE_L = 2;
    public static final int ACT_CIRCLE_R = 3;
    public static final int ACT_AWAY = 4;

    public static final int ACT_ATTACK = 0;
    public static final int ACT_WAIT = 1;

    private final Map<String, double[]> qTable = new HashMap<>();
    private final Map<Space, int[]> lastStep = new HashMap<>();
    private final Random rnd = new Random();

    // Q-learning hyperparams
    private double alpha = 0.1;
    private double gamma = 0.9;
    private double epsilon = 0.3;
    private double epsilonDecay = 0.999;
    private double epsilonMin = 0.05;

    private long totalSteps = 0;
    private int lastSaveTick = 0;
    private static final int SAVE_INTERVAL_TICKS = 6000; // 5 minutes

    public ReinforcementLearner() {
        load();
    }

    // ─── Feature → state key ─────────────────────────────────────────

    /** Build a discrete state key from the live world. */
    public int observeState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0;
        double hp = mc.player.getHealth() / mc.player.getMaxHealth();

        int threatCount = 0;
        double nearestDist = 40;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(16))) {
            if (e == mc.player || !e.isAlive()) continue;
            boolean threat = e instanceof Player;
            if (!threat && e instanceof net.minecraft.world.entity.Mob mob) threat = mob.getTarget() == mc.player;
            if (!threat) continue;
            double d = mc.player.distanceTo(e);
            threatCount++;
            if (d < nearestDist) nearestDist = d;
        }

        int countBucket = Math.min(3, threatCount / 2 + (threatCount % 2));
        int distBucket = nearestDist < 3 ? 0 : nearestDist < 6 ? 1 : nearestDist < 12 ? 2 : 3;
        int hpBucket = hp > 0.75 ? 3 : hp > 0.5 ? 2 : hp > 0.25 ? 1 : 0;
        int weaponClass = classifyWeapon(mc.player.getMainHandItem());
        int airborne = mc.player.onGround() ? 0 : 1;

        return encode(countBucket, distBucket, hpBucket, weaponClass, airborne);
    }

    public static int classifyWeapon(ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        if (s.is(Items.MACE)) return 1;
        if (s.is(Items.BOW) || s.is(Items.CROSSBOW)) return 2;
        if (s.is(net.minecraft.tags.ItemTags.SWORDS)) return 3;
        if (s.is(net.minecraft.tags.ItemTags.AXES)) return 4;
        if (s.is(Items.TRIDENT)) return 5;
        return 0;
    }

    private static int encode(int countB, int distB, int hpB, int weapon, int air) {
        return (((((countB * 4) + distB) * 4) + hpB) * 6 + weapon) * 2 + air;
    }

    // ─── Decision ────────────────────────────────────────────────────

    /** Pick an action for the given space under the current world state. */
    public synchronized int choose(Space space) {
        int stateKey = observeState();
        String k = key(space, stateKey);
        double[] q = qTable.computeIfAbsent(k, s -> new double[space.actionCount]);
        totalSteps++;

        int action;
        if (rnd.nextDouble() < epsilon) {
            action = rnd.nextInt(space.actionCount);
        } else {
            action = argmax(q);
        }

        lastStep.put(space, new int[]{stateKey, action});
        return action;
    }

    /** Apply reward to the last action taken by the given space. */
    public synchronized void reward(Space space, double r) {
        int[] step = lastStep.get(space);
        if (step == null) return;
        int stateKey = step[0];
        int action = step[1];
        String k = key(space, stateKey);
        double[] q = qTable.get(k);
        if (q == null) return;

        int nextState = observeState();
        String nk = key(space, nextState);
        double[] nq = qTable.computeIfAbsent(nk, s -> new double[space.actionCount]);
        double maxNext = max(nq);

        q[action] += alpha * (r + gamma * maxNext - q[action]);

        epsilon = Math.max(epsilonMin, epsilon * epsilonDecay);
    }

    // ─── Convenience value reads ─────────────────────────────────────

    public double bestActionValue(Space space) {
        int stateKey = observeState();
        String k = key(space, stateKey);
        double[] q = qTable.get(k);
        return q == null ? 0 : max(q);
    }

    public int bestAction(Space space) {
        int stateKey = observeState();
        String k = key(space, stateKey);
        double[] q = qTable.get(k);
        return q == null ? 0 : argmax(q);
    }

    public double getEpsilon() { return epsilon; }
    public double getConfigAlpha() { return alpha; }
    public double getConfigGamma() { return gamma; }
    public void setConfig(AimAssistConfig c) {
        if (c.getRlAlpha() > 0) alpha = c.getRlAlpha();
        if (c.getRlGamma() > 0) gamma = c.getRlGamma();
        if (c.getRlEpsilon() > 0) epsilon = c.getRlEpsilon();
    }
    public long getTotalSteps() { return totalSteps; }
    public int getTableSize() { return qTable.size(); }

    // ─── Persistence ─────────────────────────────────────────────────

    private static String key(Space s, int stateKey) {
        return s.name() + ":" + stateKey;
    }

    private static int argmax(double[] a) {
        int best = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[best]) best = i;
        return best;
    }

    private static double max(double[] a) {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : a) if (v > m) m = v;
        return m;
    }

    private static Path tablePath() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home, "shinigami_qtable.bin");
    }

    public synchronized void save() {
        Path path = tablePath();
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("shinigami-qtable-v1\n");
            w.write("alpha=" + alpha + "\n");
            w.write("gamma=" + gamma + "\n");
            w.write("epsilon=" + epsilon + "\n");
            w.write("totalSteps=" + totalSteps + "\n");
            for (Map.Entry<String, double[]> e : qTable.entrySet()) {
                StringBuilder sb = new StringBuilder();
                sb.append(e.getKey());
                double[] v = e.getValue();
                for (double d : v) sb.append(' ').append(d);
                w.write(sb.toString()); w.write('\n');
            }
        } catch (IOException ex) {
            System.err.println("[Shinigami] RL save failed: " + ex.getMessage());
        }
        lastSaveTick = 0;
    }

    public synchronized void load() {
        Path path = tablePath();
        if (!Files.exists(path)) return;
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int meta = 0;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("shinigami")) continue;
                if (!line.contains(" ")) {
                    if (line.startsWith("alpha=")) alpha = Double.parseDouble(line.substring(6));
                    else if (line.startsWith("gamma=")) gamma = Double.parseDouble(line.substring(6));
                    else if (line.startsWith("epsilon=")) epsilon = Double.parseDouble(line.substring(8));
                    else if (line.startsWith("totalSteps=")) totalSteps = Long.parseLong(line.substring(11));
                    continue;
                }
                meta = line.indexOf(' ');
                String k = line.substring(0, meta);
                String[] parts = line.substring(meta + 1).split("\\s+");
                double[] vals = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    try { vals[i] = Double.parseDouble(parts[i]); }
                    catch (NumberFormatException e) { vals[i] = 0; }
                }
                qTable.put(k, vals);
            }
        } catch (IOException ex) {
            System.err.println("[Shinigami] RL load failed: " + ex.getMessage());
        }
    }

    public void tickCountdown() {
        lastSaveTick++;
        if (lastSaveTick >= SAVE_INTERVAL_TICKS) save();
    }
}