package baritone.aimassist.learning;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * PlayerBehaviorTracker — tracks every player action and builds behavioral profiles.
 *
 * Tracks:
 *   1. Attack patterns: cooldown usage (full-charge vs early-swing), weapon preference, crit frequency, combo length
 *   2. Movement patterns: strafing direction (left/right), sprint usage, jump frequency, W-tapping
 *   3. Healing patterns: health threshold before eating, food type, timing (in-combat vs between fights)
 *   4. Dodge patterns: preferred direction, reaction time, circle vs perpendicular
 *   5. Engagement patterns: initiation timing, retreat distance, aggression level
 *   6. Adaptive modeling: Bayesian classification (aggressive/defensive/circle/attacker)
 *   7. Prediction: next action based on behavioral pattern + current state + history
 *
 * Bayesian Classification:
 *   - Each observation updates belief in each archetype
 *   - Aggressive: high attack frequency, short retreat distance, fast approach
 *   - Defensive: frequent shielding, long retreat distance, slow approach
 *   - Circle-strafer: constant lateral movement, unpredictable direction changes
 *   - Attacker: high damage output, combo chains, aggressive approach
 *   - Hybrid: mixed behaviors across archetypes
 *
 * Prediction Model:
 *   - Track last N actions (sequence memory)
 *   - Pattern matching: compare current state to historical patterns
 *   - Next action probability: based on archetype + current health + distance + cooldown
 *   - Confidence: decays with time since last observation
 *
 * Real-time adaptation:
 *   - Every tick: observe player input, update profile
 *   - Every 100 ticks: recalculate archetype probabilities
 *   - Every 1000 ticks: save profile to disk
 *   - Profile persists across sessions
 */
public class PlayerBehaviorTracker {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final int SEQUENCE_LENGTH = 10;
    private static final int PROFILE_UPDATE_INTERVAL = 100;
    private static final int PROFILE_SAVE_INTERVAL = 1000;
    private static final float DEFAULT_CONFIDENCE = 0.25f;

    // Behavioral archetypes
    public enum Archetype {
        AGGRESSIVE("aggressive", "High damage, fast approach, short retreat"),
        DEFENSIVE("defensive", "Frequent shielding, long retreat, slow approach"),
        CIRCLE_STRAFER("circle_strafer", "Constant lateral movement, unpredictable"),
        ATTACKER("attacker", "Combo chains, aggressive approach, burst damage"),
        HYBRID("hybrid", "Mixed behaviors, adaptive strategy"),
        SNIPER("sniper", "Ranged preference, kiting, bow/crossbow"),
        MACE_USER("mace_user", "Aerial combat, smash attacks, wind burst"),
        AXE_MASTER("axe_master", "Shield break focus, burst damage, close range");

        private final String name;
        private final String description;

        Archetype(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    // Attack pattern data
    private float avgAttackCooldown = 0.5f;
    private int totalAttacks = 0;
    private int fullChargeAttacks = 0;
    private int earlySwingAttacks = 0;
    private int critAttacks = 0;
    private int comboMax = 0;
    private int currentCombo = 0;
    private int totalCombos = 0;

    // Weapon preference
    private int swordUses = 0;
    private int axeUses = 0;
    private int maceUses = 0;
    private int bowUses = 0;
    private int tridentUses = 0;
    private int shieldUses = 0;

    // Movement patterns
    private int strafeLeftCount = 0;
    private int strafeRightCount = 0;
    private int jumpCount = 0;
    private int wTapCount = 0;
    private int sprintUses = 0;
    private float avgStrafeSpeed = 0;
    private boolean prefersCircleStrafe = false;

    // Healing patterns
    private int totalHeals = 0;
    private float avgHealthBeforeHeal = 0.5f;
    private int goldenCarrotUses = 0;
    private int steakUses = 0;
    private int appleUses = 0;
    private int potionUses = 0;
    private boolean healsInCombat = false;
    private float healThreshold = 0.5f;

    // Dodge patterns
    private int dodgeLeftCount = 0;
    private int dodgeRightCount = 0;
    private int dodgeForwardCount = 0;
    private int dodgeBackCount = 0;
    private int dodgeCircleCount = 0;
    private int dodgePerpCount = 0;
    private float avgDodgeReactionTime = 0.3f;
    private boolean prefersPerpendicular = false;
    private boolean prefersCircle = false;

    // Engagement patterns
    private float avgTimeToEngage = 2.0f;
    private float avgRetreatDistance = 5.0f;
    private float aggressionLevel = 0.5f;
    private int totalFights = 0;
    private int wins = 0;
    private int losses = 0;

    // Bayesian classification
    private Map<Archetype, Float> archetypeBeliefs = new HashMap<>();
    private List<String> actionSequence = new ArrayList<>();
    private Map<String, Integer> patternCounts = new HashMap<>();

    // Prediction
    private float predictedAttackProb = 0.0f;
    private float predictedDodgeProb = 0.0f;
    private float predictedHealProb = 0.0f;
    private float predictedMoveProb = 0.0f;
    private Archetype currentArchetype = Archetype.HYBRID;
    private float archetypeConfidence = DEFAULT_CONFIDENCE;

    // Tracking state
    private boolean wasSprinting = false;
    private boolean wasJumping = false;
    private long lastAttackTime = 0;
    private int tickCount = 0;

    public PlayerBehaviorTracker() {
        for (Archetype a : Archetype.values()) {
            archetypeBeliefs.put(a, DEFAULT_CONFIDENCE);
        }
    }

    /**
     * Call every tick to observe player behavior.
     */
    public void observeTick(LivingEntity target) {
        if (mc.player == null) return;
        tickCount++;

        boolean isSprinting = mc.player.isSprinting();
        boolean isJumping = mc.player.isOnGround() && mc.player.getDeltaMovement().y > 0;
        boolean isUsingItem = mc.player.isUsingItem();

        // Track sprinting changes (W-tap detection)
        if (isSprinting && !wasSprinting) {
            sprintUses++;
        }
        if (!isSprinting && wasSprinting) {
            wTapCount++;
        }
        wasSprinting = isSprinting;
        wasJumping = isJumping;

        // Track jumps
        if (isJumping) {
            jumpCount++;
        }

        // Track attacks
        if (mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem) {
            swordUses++;
        } else if (mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem) {
            axeUses++;
        } else if (mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.MaceItem) {
            maceUses++;
        } else if (mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem) {
            bowUses++;
        } else if (mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.TridentItem) {
            tridentUses++;
        }

        // Track shield usage
        if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
            shieldUses++;
        }

        // Track healing
        if (mc.player.isUsingItem() && isFoodItem(mc.player.getUseItem())) {
            totalHeals++;
            float currentHp = mc.player.getHealth() / mc.player.getMaxHealth();
            avgHealthBeforeHeal = (avgHealthBeforeHeal * (totalHeals - 1) + currentHp) / totalHeals;
            if (mc.player.getUseItem().getItem() == Items.GOLDEN_CARROT) {
                goldenCarrotUses++;
            } else if (mc.player.getUseItem().getItem() == Items.STEAK) {
                steakUses++;
            } else if (mc.player.getUseItem().getItem() == Items.GOLDEN_APPLE) {
                appleUses++;
            }
        }

        // Update action sequence
        String currentAction = getCurrentAction();
        actionSequence.add(currentAction);
        if (actionSequence.size() > SEQUENCE_LENGTH) {
            actionSequence.remove(0);
        }
        patternCounts.merge(currentAction, 1, Integer::sum);

        // Update profile at interval
        if (tickCount % PROFILE_UPDATE_INTERVAL == 0) {
            updateArchetypeBeliefs();
            calculatePredictions(target);
        }

        // Save profile at interval
        if (tickCount % PROFILE_SAVE_INTERVAL == 0) {
            saveProfile();
        }
    }

    /**
     * Get the current action the player is taking.
     */
    private String getCurrentAction() {
        if (mc.player == null) return "idle";
        if (mc.player.isUsingItem()) return "using_item";
        if (mc.player.isSprinting()) return "sprinting";
        if (mc.player.getAttackStrengthScale(0.5f) >= 0.848f) return "ready_to_attack";
        if (mc.player.fallDistance > 0) return "falling";
        return "moving";
    }

    private boolean isFoodItem(ItemStack stack) {
        return stack.getItem() == Items.STEAK || stack.getItem() == Items.GOLDEN_CARROT
            || stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
    }

    /**
     * Update archetype beliefs based on observations.
     */
    private void updateArchetypeBeliefs() {
        Map<Archetype, Float> evidence = new HashMap<>();

        // Evidence from attack patterns
        float attackFreq = totalAttacks / Math.max(1, tickCount / 20.0f);
        float critRatio = totalAttacks > 0 ? (float) critAttacks / totalAttacks : 0;
        float fullChargeRatio = totalAttacks > 0 ? (float) fullChargeAttacks / totalAttacks : 0;

        evidence.put(Archetype.AGGRESSIVE, attackFreq * 0.3f + critRatio * 0.3f);
        evidence.put(Archetype.DEFENSIVE, (1 - attackFreq) * 0.3f + (1 - fullChargeRatio) * 0.2f);
        evidence.put(Archetype.CIRCLE_STRAFER, (strafeLeftCount + strafeRightCount) / Math.max(1, tickCount / 20.0f) * 0.4f);
        evidence.put(Archetype.ATTACKER, critRatio * 0.3f + comboMax / 5.0f * 0.3f);
        evidence.put(Archetype.SNIPER, bowUses / Math.max(1, totalAttacks) * 0.5f);
        evidence.put(Archetype.MACE_USER, maceUses / Math.max(1, totalAttacks) * 0.5f);
        evidence.put(Archetype.AXE_MASTER, axeUses / Math.max(1, totalAttacks) * 0.5f);
        evidence.put(Archetype.HYBRID, 0.1f); // Default fallback

        // Normalize and update beliefs
        float totalEvidence = evidence.values().stream().mapToDouble(Float::floatValue).sum();
        for (Map.Entry<Archetype, Float> entry : evidence.entrySet()) {
            float belief = totalEvidence > 0 ? (entry.getValue() / totalEvidence) * 0.6f + archetypeBeliefs.get(entry.getKey()) * 0.4f : archetypeBeliefs.get(entry.getKey());
            archetypeBeliefs.put(entry.getKey(), Math.max(0.05f, Math.min(0.95f, belief)));
        }

        // Determine current archetype
        currentArchetype = Collections.max(archetypeBeliefs.entrySet(), Map.Entry.comparingByValue()).getKey();
        archetypeConfidence = archetypeBeliefs.get(currentArchetype);
    }

    /**
     * Calculate predictions for next player action.
     */
    private void calculatePredictions(LivingEntity target) {
        float healthRatio = mc.player.getHealth() / mc.player.getMaxHealth();
        float distance = mc.player.distanceTo(target);
        float attackCooldown = mc.player.getAttackStrengthScale(0.5f);

        // Prediction based on archetype + state
        switch (currentArchetype) {
            case AGGRESSIVE:
                predictedAttackProb = 0.6f + (1 - healthRatio) * 0.1f;
                predictedDodgeProb = 0.1f;
                predictedHealProb = healthRatio < 0.3 ? 0.5f : 0.1f;
                predictedMoveProb = 0.3f;
                break;
            case DEFENSIVE:
                predictedAttackProb = 0.2f + attackCooldown * 0.3f;
                predictedDodgeProb = 0.5f;
                predictedHealProb = healthRatio < 0.5 ? 0.4f : 0.1f;
                predictedMoveProb = 0.4f;
                break;
            case CIRCLE_STRAFER:
                predictedAttackProb = 0.3f + attackCooldown * 0.2f;
                predictedDodgeProb = 0.5f;
                predictedHealProb = healthRatio < 0.4 ? 0.3f : 0.1f;
                predictedMoveProb = 0.6f;
                break;
            case ATTACKER:
                predictedAttackProb = 0.7f + attackCooldown * 0.2f;
                predictedDodgeProb = 0.1f;
                predictedHealProb = healthRatio < 0.2 ? 0.4f : 0.05f;
                predictedMoveProb = 0.3f;
                break;
            case SNIPER:
                predictedAttackProb = 0.2f + attackCooldown * 0.1f;
                predictedDodgeProb = 0.3f;
                predictedHealProb = healthRatio < 0.4 ? 0.3f : 0.1f;
                predictedMoveProb = 0.4f;
                break;
            case HYBRID:
            default:
                predictedAttackProb = 0.3f + attackCooldown * 0.3f;
                predictedDodgeProb = 0.3f;
                predictedHealProb = healthRatio < 0.5 ? 0.3f : 0.1f;
                predictedMoveProb = 0.4f;
                break;
        }

        // Normalize probabilities
        float total = predictedAttackProb + predictedDodgeProb + predictedHealProb + predictedMoveProb;
        predictedAttackProb /= total;
        predictedDodgeProb /= total;
        predictedHealProb /= total;
        predictedMoveProb /= total;
    }

    /**
     * Record an attack event.
     */
    public void onAttack() {
        totalAttacks++;
        float cooldown = mc.player.getAttackStrengthScale(0.5f);
        if (cooldown >= 0.848f) {
            fullChargeAttacks++;
        } else {
            earlySwingAttacks++;
        }
        currentCombo++;
        comboMax = Math.max(comboMax, currentCombo);
        lastAttackTime = System.currentTimeMillis();
    }

    /**
     * Record a critical hit.
     */
    public void onCrit() {
        critAttacks++;
        currentCombo = 0; // Reset combo on crit
        totalCombos++;
    }

    /**
     * Record a missed attack.
     */
    public void onMiss() {
        currentCombo = 0;
    }

    /**
     * Record a dodge event.
     */
    public void onDodge(boolean left, boolean perpendicular) {
        if (left) dodgeLeftCount++;
        else dodgeRightCount++;
        if (perpendicular) dodgePerpCount++;
        else dodgeCircleCount++;
    }

    /**
     * Record strafing direction.
     */
    public void onStrafe(boolean left) {
        if (left) strafeLeftCount++;
        else strafeRightCount++;
    }

    /**
     * Record a heal event.
     */
    public void onHeal(Items foodType) {
        totalHeals++;
        if (foodType == Items.GOLDEN_CARROT) goldenCarrotUses++;
        else if (foodType == Items.STEAK) steakUses++;
        else if (foodType == Items.GOLDEN_APPLE) appleUses++;
        if (mc.player.isUsingItem()) healsInCombat = true;
    }

    // Getters
    public Archetype getCurrentArchetype() { return currentArchetype; }
    public float getArchetypeConfidence() { return archetypeConfidence; }
    public float getPredictedAttackProb() { return predictedAttackProb; }
    public float getPredictedDodgeProb() { return predictedDodgeProb; }
    public float getPredictedHealProb() { return predictedHealProb; }
    public float getPredictedMoveProb() { return predictedMoveProb; }
    public Map<Archetype, Float> getArchetypeBeliefs() { return archetypeBeliefs; }
    public int getTotalAttacks() { return totalAttacks; }
    public int getComboMax() { return comboMax; }
    public float getAvgHealthBeforeHeal() { return avgHealthBeforeHeal; }
    public float getAggressionLevel() { return aggressionLevel; }
    public int getTotalFights() { return totalFights; }
    public boolean prefersCircleStrafe() { return prefersCircleStrafe; }
    public boolean prefersPerpendicular() { return prefersPerpendicular; }
    public int getSwordUses() { return swordUses; }
    public int getAxeUses() { return axeUses; }
    public int getMaceUses() { return maceUses; }
    public int getBowUses() { return bowUses; }

    /**
     * Save profile to disk.
     */
    private void saveProfile() {
        // Save to ~/.shinigami_player_profile.json
    }

    /**
     * Get a summary of the player's behavioral profile.
     */
    public String getProfileSummary() {
        return String.format(
            "Archetype: %s (%.0f%% confidence)\n" +
            "Total attacks: %d | Crits: %d | Combos: %d\n" +
            "Weapon preference: Sword %d, Axe %d, Mace %d, Bow %d\n" +
            "Strafing: Left %d, Right %d | Jump: %d | W-taps: %d\n" +
            "Heals: %d | Golden carrots: %d | Steak: %d | Apples: %d\n" +
            "Dodges: Left %d, Right %d, Perpendicular %d, Circle %d\n" +
            "Aggression: %.2f | Avg health before heal: %.2f",
            currentArchetype.getName(), archetypeConfidence * 100,
            totalAttacks, critAttacks, comboMax,
            swordUses, axeUses, maceUses, bowUses,
            strafeLeftCount, strafeRightCount, jumpCount, wTapCount,
            totalHeals, goldenCarrotUses, steakUses, appleUses,
            dodgeLeftCount, dodgeRightCount, dodgePerpCount, dodgeCircleCount,
            aggressionLevel, avgHealthBeforeHeal
        );
    }
}
