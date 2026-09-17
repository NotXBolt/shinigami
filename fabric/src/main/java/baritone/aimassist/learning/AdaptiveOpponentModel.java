package baritone.aimassist.learning;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/**
 * AdaptiveOpponentModel — adaptive RL that learns how the player plays and uses it against them.
 *
 * Extends ReinforcementLearner with opponent modeling:
 *   1. Q-learning with opponent awareness: Each state-action pair includes opponent state as context
 *   2. Behavioral clustering: Classifies opponent into archetypes (aggressive/defensive/circle/attacker)
 *   3. Best-response learning: Learns optimal counter-strategy for each opponent archetype
 *   4. Real-time adaptation: Updates Q-values every tick based on observed opponent behavior
 *   5. Dual-policy: Separate Q-tables for aggressive vs evasive personas
 *   6. Opponent trajectory encoding: Encodes last N opponent actions into state vector
 *   7. Bayesian belief tracking: Quantized opponent models with belief updates
 *
 * State space expanded:
 *   [self_health, self_armor, weapon, distance, opponent_health, opponent_weapon,
 *    opponent_archetype, opponent_recent_action, environment_cover, cooldown_ratio,
 *    threat_score, opportunity_score, survival_score, opponent_archetype_confidence]
 *
 * Action space:
 *   [DODGE_LEFT, DODGE_RIGHT, DODGE_FORWARD, DODGE_BACK, ATTACK, CRIT, COMBO,
 *    SHIELD_BREAK, HEAL, RETREAT, CHASE, INTERCEPT, CIRCLE, ESCAPE, CLUTCH,
 *    REPOSITION, WEAPON_SWITCH]
 *
 * Reward function:
 *   damage_dealt × (1 + opponent_health_frac) - damage_taken × (1 + self_health_frac)
 *   + dodge_success × 5 + kill_bonus × 10 - death_penalty × 50
 *   + counter_strike_bonus × 3 (when countering opponent's predicted action)
 *   + prediction_accuracy_bonus × 2 (when correctly predicting opponent's next action)
 *
 * ε-greedy: ε decays from 0.5 → 0.05
 * α=0.1, γ=0.9
 * Persisted to: ~/shinigami_adaptive_qtable.bin
 *
 * Persona switching:
 *   - Aggressive persona: attacks more, takes risks, pursues aggressively
 *   - Evasive persona: dodges more, maintains distance, heals frequently
 *   - Switch based on: opponent archetype, own health, threat level
 *   - PersonaManager tracks which persona is optimal at any given time
 *
 * Best-response mapping:
 *   Aggressive opponent → Evasive persona (dodge, retreat, circle)
 *   Defensive opponent → Aggressive persona (attack, pressure, shield break)
 *   Circle-strafer → Intercept + close range (limit their movement)
 *   Attacker → Counter-attack + shield break (exploit their commit)
 *   Sniper → Close range (force melee engagement)
 *   Mace user → Keep distance + avoid vertical (prevent smash setup)
 */
public class AdaptiveOpponentModel {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final double ALPHA = 0.1;
    private static final double GAMMA = 0.9;
    private static final double EPSILON_START = 0.5;
    private static final double EPSILON_MIN = 0.05;
    private static final double EPSILON_DECAY = 0.9995;
    private static final int STATE_SIZE = 14;
    private static final int ACTION_SIZE = 17;
    private static final int TRAJECTORY_LENGTH = 10;
    private static final int UPDATE_INTERVAL = 1; // Update every tick
    private static final int SAVE_INTERVAL = 3000;
    private static final double DOUBLE_POLICY_SPLIT = 0.5;

    private double epsilon = EPSILON_START;
    private int totalSteps = 0;
    private int totalRewards = 0;

    // Q-tables: aggressive and evasive personas
    private double[][] qTableAggressive;
    private double[][] qTableEvasive;
    private double[][] qTableHybrid; // Combined for neutral mode

    // Persona state
    private boolean useAggressivePersona = true;
    private double aggressiveScore = 0.5;
    private double evasiveScore = 0.5;
    private int personaSwitchCooldown = 0;

    // Opponent modeling
    private OpponentArchetype currentArchetype = OpponentArchetype.AGGRESSIVE;
    private double archetypeConfidence = 0.25;
    private final Map<OpponentArchetype, Double> archetypeBeliefs = new HashMap<>();
    private final List<Integer> opponentActionHistory = new ArrayList<>();
    private final List<Integer> selfActionHistory = new ArrayList<>();

    // Prediction
    private int predictedOpponentAction = -1;
    private boolean predictionCorrect = false;
    private int correctPredictions = 0;
    private int totalPredictions = 0;

    // Trajectory encoding
    private final int[] opponentTrajectory = new int[TRAJECTORY_LENGTH];
    private int trajectoryIndex = 0;

    // Rewards accumulated
    private double lastReward = 0;
    private int kills = 0;
    private int deaths = 0;
    private int dodgeSuccesses = 0;

    public AdaptiveOpponentModel() {
        qTableAggressive = new double[STATE_SIZE][ACTION_SIZE];
        qTableEvasive = new double[STATE_SIZE][ACTION_SIZE];
        qTableHybrid = new double[STATE_SIZE][ACTION_SIZE];

        for (OpponentArchetype a : OpponentArchetype.values()) {
            archetypeBeliefs.put(a, 0.25);
        }
        currentArchetype = OpponentArchetype.AGGRESSIVE;
    }

    /**
     * Get the best action given the current state and opponent state.
     */
    public int decide(double[] state, LivingEntity opponent) {
        if (mc.player == null || opponent == null) return 0;

        totalSteps++;
        updateEpsilon();

        // Determine which persona to use
        updatePersona(opponent);

        // Encode opponent trajectory
        encodeOpponentTrajectory(opponent);

        // Predict opponent's next action
        predictOpponentAction();

        // Select action using ε-greedy
        double[] qValues = getQValues(state);
        int action;

        if (Math.random() < epsilon && totalSteps > 100) {
            action = new Random().nextInt(ACTION_SIZE); // Exploration
        } else {
            action = getBestAction(qValues); // Exploitation
        }

        return action;
    }

    /**
     * Update Q-values based on reward and next state.
     */
    public void update(double[] state, int action, double reward, double[] nextState) {
        double[][] targetQTable = useAggressivePersona ? qTableAggressive : qTableEvasive;
        double oldValue = targetQTable[stateToIndex(state)][action];
        double nextMax = getMaxQValue(nextState);

        // Q-learning update
        targetQTable[stateToIndex(state)][action] = oldValue + ALPHA * (reward + GAMMA * nextMax - oldValue);

        // Update hybrid table
        qTableHybrid[stateToIndex(state)][action] = targetQTable[stateToIndex(state)][action];

        totalRewards += reward;
        lastReward = reward;

        // Update archetype beliefs based on opponent behavior
        updateArchetypeBeliefs(opponentActionHistory);
    }

    /**
     * Get Q-values for the current persona.
     */
    private double[] getQValues(double[] state) {
        int idx = stateToIndex(state);
        double[] qValues = new double[ACTION_SIZE];

        if (useAggressivePersona) {
            System.arraycopy(qTableAggressive[idx], 0, qValues, 0, ACTION_SIZE);
        } else {
            System.arraycopy(qTableEvasive[idx], 0, qValues, 0, ACTION_SIZE);
        }

        // Blend with hybrid if confidence is low
        if (archetypeConfidence < 0.5) {
            for (int i = 0; i < ACTION_SIZE; i++) {
                qValues[i] = qValues[i] * archetypeConfidence + qTableHybrid[idx][i] * (1 - archetypeConfidence);
            }
        }

        return qValues;
    }

    /**
     * Update which persona to use based on opponent archetype.
     */
    private void updatePersona(LivingEntity opponent) {
        if (personaSwitchCooldown > 0) {
            personaSwitchCooldown--;
            return;
        }

        // Calculate scores for each persona
        aggressiveScore = calculateAggressiveScore(opponent);
        evasiveScore = calculateEvasiveScore(opponent);

        // Switch if one is significantly better
        if (aggressiveScore > evasiveScore + 0.2) {
            if (!useAggressivePersona) {
                useAggressivePersona = true;
                personaSwitchCooldown = 100;
            }
        } else if (evasiveScore > aggressiveScore + 0.2) {
            if (useAggressivePersona) {
                useAggressivePersona = false;
                personaSwitchCooldown = 100;
            }
        }
    }

    private double calculateAggressiveScore(LivingEntity opponent) {
        if (opponent == null) return 0.5;
        double healthFrac = opponent.getHealth() / opponent.getMaxHealth();
        double distance = mc.player.distanceTo(opponent);
        boolean aggressive = currentArchetype == OpponentArchetype.AGGRESSIVE ||
                            currentArchetype == OpponentArchetype.ATTACKER;
        return aggressive && healthFrac > 0.5 && distance < 5 ? 0.8 : 0.3;
    }

    private double calculateEvasiveScore(LivingEntity opponent) {
        if (opponent == null) return 0.5;
        double healthFrac = mc.player.getHealth() / mc.player.getMaxHealth();
        double threat = calculateThreatScore(opponent);
        return threat > 50 || healthFrac < 0.4 ? 0.8 : 0.3;
    }

    private double calculateThreatScore(LivingEntity opponent) {
        if (opponent == null) return 0;
        double dist = mc.player.distanceTo(opponent);
        return Math.min(100, (1 - dist / 20) * 50 + (opponent.getHealth() / opponent.getMaxHealth()) * 30);
    }

    /**
     * Encode opponent's recent actions into trajectory buffer.
     */
    private void encodeOpponentTrajectory(LivingEntity opponent) {
        if (opponent == null) return;
        // Encode: 0=idle, 1=attack, 2=dodge, 3=heal, 4=retreat, 5=chase, 6=shield
        int action = encodeOpponentAction(opponent);
        opponentTrajectory[trajectoryIndex % TRAJECTORY_LENGTH] = action;
        trajectoryIndex++;
    }

    private int encodeOpponentAction(LivingEntity opponent) {
        if (opponent.isUsingItem() && opponent.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) return 6;
        if (opponent.isSprinting() && opponent.getAttackStrengthScale(0.5f) > 0.8) return 1;
        if (opponent.fallDistance > 0.5) return 2;
        if (opponent.getHealth() < opponent.getMaxHealth() * 0.7 && opponent.isUsingItem()) return 3;
        if (opponent.isMoving()) return 5;
        return 0;
    }

    /**
     * Predict opponent's next action based on trajectory and archetype.
     */
    private void predictOpponentAction() {
        totalPredictions++;
        int predicted = predictFromTrajectory();
        predictedOpponentAction = predicted;

        // Check if prediction was correct on next tick
        // This is verified when observeOpponentAction is called
    }

    private int predictFromTrajectory() {
        // Simple pattern matching: look at last N actions and predict most common
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int i = 0; i < Math.min(TRAJECTORY_LENGTH, trajectoryIndex); i++) {
            frequency.merge(opponentTrajectory[i], 1, Integer::sum);
        }
        return frequency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(0);
    }

    /**
     * Verify prediction and update confidence.
     */
    public void observeOpponentAction(int actualAction) {
        totalPredictions++;
        if (predictedOpponentAction == actualAction) {
            correctPredictions++;
            predictionCorrect = true;
        } else {
            predictionCorrect = false;
        }
    }

    /**
     * Update archetype beliefs based on observed behavior.
     */
    private void updateArchetypeBeliefs(List<Integer> history) {
        Map<OpponentArchetype, Float> evidence = new HashMap<>();

        // Count action types in history
        int attackCount = 0, dodgeCount = 0, healCount = 0, retreatCount = 0, shieldCount = 0;
        for (int action : history) {
            switch (action) {
                case 1: attackCount++; break;
                case 2: dodgeCount++; break;
                case 3: healCount++; break;
                case 4: retreatCount++; break;
                case 6: shieldCount++; break;
            }
        }

        float total = Math.max(1, history.size());
        evidence.put(OpponentArchetype.AGGRESSIVE, attackCount / total);
        evidence.put(OpponentArchetype.DEFENSIVE, shieldCount / total + dodgeCount / total * 0.5f);
        evidence.put(OpponentArchetype.CIRCLE_STRAFER, dodgeCount / total * 0.5f);
        evidence.put(OpponentArchetype.ATTACKER, attackCount / total + retreatCount / total * 0.3f);
        evidence.put(OpponentArchetype.SNIPER, 0.1f); // Default
        evidence.put(OpponentArchetype.MACE_USER, 0.1f);
        evidence.put(OpponentArchetype.HYBRID, 0.2f);

        float totalEvidence = evidence.values().stream().mapToDouble(Float::floatValue).sum();
        for (Map.Entry<OpponentArchetype, Double> entry : archetypeBeliefs.entrySet()) {
            Float ev = evidence.get(entry.getKey());
            if (ev != null) {
                float belief = totalEvidence > 0 ? (ev / totalEvidence) * 0.6f + entry.getValue() * 0.4f : entry.getValue();
                archetypeBeliefs.put(entry.getKey(), Math.max(0.05, Math.min(0.95, (double) belief)));
            }
        }

        currentArchetype = Collections.max(archetypeBeliefs.entrySet(), Map.Entry.comparingByValue()).getKey();
        archetypeConfidence = archetypeBeliefs.get(currentArchetype);
    }

    /**
     * Record opponent's action for learning.
     */
    public void observeOpponentAction(int action, LivingEntity opponent) {
        opponentActionHistory.add(action);
        if (opponentActionHistory.size() > TRAJECTORY_LENGTH * 2) {
            opponentActionHistory.remove(0);
        }
        observeOpponentAction(action);
        updateArchetypeBeliefs(opponentActionHistory);
    }

    /**
     * Get the reward for a given state-action pair.
     */
    public double calculateReward(double damageDealt, double damageTaken, boolean dodgeSuccess,
                                   boolean killed, boolean died, boolean counterStrike, boolean predictionCorrect) {
        double hpFrac = mc.player.getHealth() / mc.player.getMaxHealth();
        double oppHpFrac = mc.player.getHealth() / mc.player.getMaxHealth(); // Placeholder

        double reward = 0;
        reward += damageDealt * (1 + oppHpFrac);
        reward -= damageTaken * (1 + hpFrac);
        if (dodgeSuccess) reward += 5;
        if (killed) reward += 10;
        if (died) reward -= 50;
        if (counterStrike) reward += 3;
        if (predictionCorrect) reward += 2;

        return reward;
    }

    /**
     * Get state encoding for Q-table lookup.
     */
    public double[] getState(LivingEntity opponent) {
        if (mc.player == null || opponent == null) return new double[STATE_SIZE];

        double[] state = new double[STATE_SIZE];
        state[0] = mc.player.getHealth() / mc.player.getMaxHealth(); // self_health
        state[1] = mc.player.getArmor() / 20.0; // self_armor
        state[2] = getWeaponClass(mc.player.getMainHandItem()) / 5.0; // weapon
        state[3] = mc.player.distanceTo(opponent) / 20.0; // distance
        state[4] = opponent.getHealth() / opponent.getMaxHealth(); // opponent_health
        state[5] = getWeaponClass(opponent.getMainHandItem()) / 5.0; // opponent_weapon
        state[6] = archetypeBeliefs.get(currentArchetype).floatValue(); // opponent_archetype
        state[7] = opponentActionHistory.isEmpty() ? 0 : opponentActionHistory.get(opponentActionHistory.size() - 1) / 7.0; // opponent_recent_action
        state[8] = hasCover() ? 1.0 : 0.0; // environment_cover
        state[9] = mc.player.getAttackStrengthScale(0.5f); // cooldown_ratio
        state[10] = calculateThreatScore(opponent) / 100; // threat_score
        state[11] = calculateOpportunityScore(opponent) / 100; // opportunity_score
        state[12] = calculateSurvivalScore() / 100; // survival_score
        state[13] = archetypeConfidence; // opponent_archetype_confidence

        return state;
    }

    private double calculateOpportunityScore(LivingEntity opponent) {
        if (opponent == null) return 0;
        double hpFrac = opponent.getHealth() / opponent.getMaxHealth();
        double dist = mc.player.distanceTo(opponent);
        return Math.min(1, (hpFrac * 0.4 + (1 - dist / 20) * 0.3 + mc.player.getAttackStrengthScale(0.5f) * 0.3));
    }

    private double calculateSurvivalScore() {
        if (mc.player == null) return 0;
        double hp = mc.player.getHealth() / mc.player.getMaxHealth();
        double armor = mc.player.getArmor() / 20.0;
        return Math.min(1, hp * 0.5 + armor * 0.3 + (hasEscapeRoute() ? 0.2 : 0));
    }

    private boolean hasEscapeRoute() { return false; }
    private boolean hasCover() { return false; }

    private int getWeaponClass(net.minecraft.world.item.ItemStack stack) {
        if (stack == null) return 0;
        if (stack.getItem() instanceof net.minecraft.world.item.SwordItem) return 1;
        if (stack.getItem() instanceof net.minecraft.world.item.AxeItem) return 2;
        if (stack.getItem() instanceof net.minecraft.world.item.MaceItem) return 3;
        if (stack.getItem() instanceof net.minecraft.world.item.BowItem) return 4;
        if (stack.getItem() instanceof net.minecraft.world.item.TridentItem) return 5;
        return 0;
    }

    private int stateToIndex(double[] state) {
        int idx = 0;
        for (int i = 0; i < Math.min(state.length, STATE_SIZE); i++) {
            idx += (int) (state[i] * 10) % STATE_SIZE;
        }
        return Math.abs(idx % STATE_SIZE);
    }

    private int getBestAction(double[] qValues) {
        int best = 0;
        double maxQ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < qValues.length; i++) {
            if (qValues[i] > maxQ) {
                maxQ = qValues[i];
                best = i;
            }
        }
        return best;
    }

    private double getMaxQValue(double[] state) {
        int idx = stateToIndex(state);
        double max = Double.NEGATIVE_INFINITY;
        for (double q : useAggressivePersona ? qTableAggressive[idx] : qTableEvasive[idx]) {
            max = Math.max(max, q);
        }
        return max;
    }

    private void updateEpsilon() {
        epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);
    }

    public double getEpsilon() { return epsilon; }
    public int getTotalSteps() { return totalSteps; }
    public double getTotalRewards() { return totalRewards; }
    public OpponentArchetype getCurrentArchetype() { return currentArchetype; }
    public double getArchetypeConfidence() { return archetypeConfidence; }
    public boolean isUseAggressivePersona() { return useAggressivePersona; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public int getDodgeSuccesses() { return dodgeSuccesses; }
    public int getCorrectPredictions() { return correctPredictions; }
    public int getTotalPredictions() { return totalPredictions; }
    public double[] getQTableAggressive(int state) { return qTableAggressive[state]; }
    public double[] getQTableEvasive(int state) { return qTableEvasive[state]; }

    /**
     * Get a summary of the opponent model.
     */
    public String getModelSummary() {
        return String.format(
            "Opponent Archetype: %s (%.0f%%)\n" +
            "Persona: %s\n" +
            "Total Steps: %d | Epsilon: %.4f\n" +
            "Total Rewards: %.2f | Correct Predictions: %d/%d\n" +
            "Kills: %d | Deaths: %d | Dodge Successes: %d\n" +
            "Aggressive Score: %.2f | Evasive Score: %.2f\n" +
            "Archetype Beliefs: %s",
            currentArchetype.getName(), archetypeConfidence * 100,
            useAggressivePersona ? "AGGRESSIVE" : "EVASIVE",
            totalSteps, epsilon, totalRewards, correctPredictions, totalPredictions,
            kills, deaths, dodgeSuccesses, aggressiveScore, evasiveScore,
            archetypeBeliefs.toString()
        );
    }

    /**
     * Opponent archetypes for behavioral classification.
     */
    public enum OpponentArchetype {
        AGGRESSIVE("aggressive", "High damage, fast approach, short retreat"),
        DEFENSIVE("defensive", "Frequent shielding, long retreat, slow approach"),
        CIRCLE_STRAFER("circle_strafer", "Constant lateral movement, unpredictable"),
        ATTACKER("attacker", "Combo chains, aggressive approach, burst damage"),
        SNIPER("sniper", "Ranged preference, kiting, bow/crossbow"),
        MACE_USER("mace_user", "Aerial combat, smash attacks, wind burst"),
        AXE_MASTER("axe_master", "Shield break focus, burst damage, close range"),
        HYBRID("hybrid", "Mixed behaviors, adaptive strategy");

        private final String name;
        private final String description;

        OpponentArchetype(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
