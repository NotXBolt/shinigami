package shinigami.learning;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * ReinforcementLearner — Simple tabular Q-learning for dodge decisions.
 * State: threatType + distanceBucket (0-5) + healthBucket (0-3)
 * Action: 0=perp-left, 1=perp-right, 2=back-perp, 3=forward-perp, 4=stay
 * Reward: survive +1, dodge success +2, damage -5, death -10
 * Original, lightweight, no external ML deps.
 */
public class ReinforcementLearner {
    private final Map<String, double[]> qTable = new HashMap<>();
    private final Random rnd = new Random();
    private double lr = 0.1;
    private double gamma = 0.9;
    private double epsilon = 0.15;

    private String lastState = null;
    private int lastAction = -1;

    public void setLearningRate(double lr) { this.lr = lr; }
    public void setDiscount(double g) { this.gamma = g; }

    private String key(String threat, int distB, int hpB) {
        return threat + ":" + distB + ":" + hpB;
    }

    private double[] getQ(String k) {
        return qTable.computeIfAbsent(k, s -> new double[5]);
    }

    public int choose(String threat, double distance, double healthRatio) {
        int distB = (int) Math.min(5, distance / 6.0);
        int hpB = (int) Math.min(3, (1.0 - healthRatio) * 4);
        String k = key(threat, distB, hpB);
        double[] qs = getQ(k);
        if (rnd.nextDouble() < epsilon) {
            int a = rnd.nextInt(5);
            lastState = k; lastAction = a;
            return a;
        }
        int best = 0;
        for (int i = 1; i < qs.length; i++) if (qs[i] > qs[best]) best = i;
        lastState = k; lastAction = best;
        return best;
    }

    public void reward(double r, String nextThreat, double nextDist, double nextHealth) {
        if (lastState == null || lastAction < 0) return;
        double[] qs = getQ(lastState);
        int distB = (int) Math.min(5, nextDist / 6.0);
        int hpB = (int) Math.min(3, (1.0 - nextHealth) * 4);
        String nk = key(nextThreat, distB, hpB);
        double[] nqs = getQ(nk);
        double maxNext = nqs[0];
        for (double v : nqs) if (v > maxNext) maxNext = v;
        qs[lastAction] += lr * (r + gamma * maxNext - qs[lastAction]);
        if (r < -4) epsilon = Math.min(0.3, epsilon + 0.02); // explore more after damage
        else epsilon = Math.max(0.05, epsilon * 0.999);
    }

    public void onDamage(double healthBefore, double healthAfter) {
        double delta = healthAfter - healthBefore;
        if (delta < 0) reward(-5 + delta, "DAMAGE", 5, healthAfter / 20.0);
    }

    public void onSurviveTick(double dist, double hp) {
        reward(0.1, "SURVIVE", dist, hp);
    }

    public void onDodgeSuccess() { reward(2.0, "DODGE_OK", 3, 1.0); }
    public void onDeath() { reward(-10, "DEATH", 0, 0); }

    public double getEpsilon() { return epsilon; }
    public Map<String, double[]> snapshot() { return new HashMap<>(qTable); }
}
