package baritone.aimassist.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * HealSystem — strategic healing for every situation.
 *
 * Healing priorities:
 *   1. Golden Carrot: Instant full heal (best for emergencies)
 *   2. Steak: Sustained heal (best during combat pauses)
 *   3. Golden Apple: Absorption + regeneration (best before tough fights)
 *   4. Potions: Instant Health II, Regeneration, Absorption
 *
 * Eating rules:
 *   - NEVER eat during active combat (i-frames + attack animation)
 *   - 2-3 tick window between attacks for eating
 *   - Look down when eating for maximum healing
 *   - Golden carrots: eat immediately when health < 0.5
 *   - Steak: eat during cooldown windows
 *   - Golden apples: before entering tough fights
 *
 * Healing situations:
 *   - EMERGENCY: health < 0.3, safe → golden carrot immediately
 *   - COMBAT_PAUSE: between attack combos → steak
 *   - PRE_FIGHT: before engagement → golden apple + steak
 *   - RETREAT_HEAL: while backing away → sprint + eat
 *   - POTION_HEAL: splash healing potions at 4-5 hearts
 *   - REGEN_HEAL: regeneration potion for passive recovery
 *
 * Healing timing:
 *   - Golden carrot: 1 tick to eat, instant full heal
 *   - Steak: 3 ticks to eat, 4 hp per half-stack
 *   - Golden apple: 2 ticks, absorption + regen
 *   - Potion: instant splash, no eating required
 *   - Between attacks: 2-3 ticks after swing
 *
 * Anti-eat rules:
 *   - Do NOT eat when attacking (animation lock)
 *   - Do NOT eat when taking damage (i-frames)
 *   - Do NOT eat when enemy is in melee range
 *   - Do NOT eat when low on food
 *   - Do NOT eat if health > 0.8 (waste of food)
 *
 * Retreat heal:
 *   - Sprint away from target
 *   - Eat while moving
 *   - Return to fight after heal
 *   - Distance check: must be > 5 blocks from target
 *
 * Pre-fight heal:
 *   - Golden apple before tough fight
 *   - Steak immediately before engagement
 *   - Speed potion for approach
 *   - Strength potion for damage boost
 */
public class HealSystem {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final float EMERGENCY_HEALTH = 0.3f;
    private static final float LOW_HEALTH = 0.5f;
    private static final float SAFE_HEAL_HEALTH = 0.8f;
    private static final double SAFE_DISTANCE = 5.0;
    private static final int EAT_COOLDOWN_TICKS = 3;
    private static final int PRE_FIGHT_TICKS = 10;

    private int eatCooldown = 0;
    private int preFightTicks = 0;

    // Food types enum
    private enum FoodType {
        GOLDEN_CARROT("golden_carrot", Items.GOLDEN_CARROT, 6, true, "Instant full heal. Best for emergencies."),
        STEAK("steak", Items.STEAK, 8, false, "Sustained heal. 4 hp per half-stack. Best during combat pauses."),
        GOLDEN_APPLE("golden_apple", Items.GOLDEN_APPLE, 4, true, "Absorption + regeneration. Best before tough fights."),
        ENCHANTED_GOLDEN_APPLE("enchanted_golden_apple", Items.ENCHANTED_GOLDEN_APPLE, 2, true, "Maximum absorption + regen II. Ultimate heal."),
        CHORUS_FRUIT("chorus_fruit", Items.CHORUS_FRUIT, 4, false, "Teleport after eating. Emergency repositioning.");

        private final String name;
        private final Items item;
        private final int healAmount;
        private final boolean instant;
        private final String description;

        FoodType(String name, Items item, int healAmount, boolean instant, String description) {
            this.name = name;
            this.item = item;
            this.healAmount = healAmount;
            this.instant = instant;
            this.description = description;
        }

        public String getName() { return name; }
        public Items getItem() { return item; }
        public int getHealAmount() { return healAmount; }
        public boolean isInstant() { return instant; }
        public String getDescription() { return description; }
    }

    // Potion types enum
    private enum PotionType {
        INSTANT_HEALTH("instant_health", "Instant Health II", "Restores 6-10 hearts instantly."),
        INSTANT_HEALTH_II("instant_health_ii", "Instant Health IV", "Restores 8-12 hearts instantly. Higher level."),
        REGENERATION("regeneration", "Regeneration", "Passive health recovery over time."),
        REGENERATION_II("regeneration_ii", "Regeneration II", "Faster passive health recovery."),
        ABSORPTION("absorption", "Absorption", "Extra health bars that absorb damage."),
        STRENGTH("strength", "Strength", "1.3x attack damage boost."),
        SPEED("speed", "Speed II", "Faster movement. Controls spacing."),
        RESISTANCE("resistance", "Resistance", "Reduced incoming damage for 4-5 seconds."),
        FIRE_RESISTANCE("fire_resistance", "Fire Resistance", "Immune to fire/lava damage.");

        private final String name;
        private final String potionName;
        private final String description;

        PotionType(String name, String potionName, String description) {
            this.name = name;
            this.potionName = potionName;
            this.description = description;
        }

        public String getName() { return name; }
        public String getPotionName() { return potionName; }
        public String getDescription() { return description; }
    }

    /**
     * Determine the best healing action given current state.
     */
    public HealAction decide() {
        if (mc.player == null) return HealAction.NONE;

        float hp = mc.player.getHealth() / mc.player.getMaxHealth();
        boolean onGround = mc.player.onGround();
        boolean isUsingItem = mc.player.isUsingItem();
        boolean isSprinting = mc.player.isSprinting();
        boolean hasSpeed = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        boolean targetInRange = hasTargetInMeleeRange();
        boolean cooldownReady = eatCooldown <= 0;

        // Emergency: critical health → golden carrot immediately
        if (hp < EMERGENCY_HEALTH && cooldownReady && !targetInRange && !isUsingItem) {
            return HealAction.EMERGENCY_HEAL; // Golden carrot
        }

        // Low health + safe → heal
        if (hp < LOW_HEALTH && cooldownReady && !targetInRange && onGround && !isUsingItem) {
            return HealAction.LOW_HEALTH_HEAL; // Steak or golden carrot
        }

        // Retreat heal: low health + not safe → sprint away, eat
        if (hp < LOW_HEALTH && targetInRange && !isUsingItem && cooldownReady) {
            return HealAction.RETREAT_HEAL; // Sprint + eat while backing away
        }

        // Pre-fight heal: before engagement → golden apple
        if (preFightTicks < PRE_FIGHT_TICKS && cooldownReady) {
            preFightTicks++;
            return HealAction.PRE_FIGHT_HEAL; // Golden apple + steak
        }

        // Combat pause: between combos → steak
        if (hp < SAFE_HEAL_HEALTH && cooldownReady && !targetInRange && !isUsingItem) {
            if (hasSpeed) return HealAction.SPEED_HEAL; // Sprint-cancel + steak
            return HealAction.COMBAT_PAUSE_HEAL; // Steak
        }

        // Potion heal: splash healing potions
        if (hp < 0.6 && !isUsingItem) {
            return HealAction.POTION_HEAL; // Splash Instant Health II
        }

        // Regeneration heal: passive recovery
        if (hp < 0.7 && !hasRegeneration()) {
            return HealAction.REGEN_HEAL; // Splash Regeneration
        }

        // Full health → no need to eat
        if (hp >= SAFE_HEAL_HEALTH) {
            return HealAction.NONE;
        }

        return HealAction.NONE;
    }

    /**
     * Check if target is in melee range.
     */
    private boolean hasTargetInMeleeRange() {
        if (mc.player == null || mc.world == null) return false;
        for (LivingEntity entity : mc.world.getEntitiesOfClass(
                LivingEntity.class,
                mc.player.getBoundingBox().inflate(4.0, 2.0, 4.0))) {
            if (entity != mc.player && entity.isAlive() && !entity.isDead()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRegeneration() { return false; }

    /**
     * After eating, update cooldown.
     */
    public void onEat() { eatCooldown = EAT_COOLDOWN_TICKS; }

    /**
     * Called each tick to decrement cooldown.
     */
    public void tick() {
        if (eatCooldown > 0) eatCooldown--;
    }

    public int getEatCooldown() { return eatCooldown; }
    public void resetPreFight() { preFightTicks = 0; }

    /**
     * Heal action types — every possible healing decision.
     */
    public enum HealAction {
        NONE("none", "No healing needed"),
        EMERGENCY_HEAL("emergency_heal", "Eat golden carrot immediately (health < 30%)"),
        LOW_HEALTH_HEAL("low_health_heal", "Eat steak or golden carrot (health < 50%)"),
        RETREAT_HEAL("retreat_heal", "Sprint away and eat while retreating"),
        COMBAT_PAUSE_HEAL("combat_pause_heal", "Eat steak between attacks"),
        SPEED_HEAL("speed_heal", "Sprint-cancel + steak (exploit)"),
        PRE_FIGHT_HEAL("pre_fight_heal", "Golden apple + steak before engagement"),
        POTION_HEAL("potion_heal", "Splash Instant Health II potion"),
        REGEN_HEAL("regen_heal", "Splash Regeneration potion"),
        PREPARE_HEAL("prepare_heal", "Golden apple + resistance potion before tough fight"),
        SUSTAIN_HEAL("sustain_heal", "Steak for sustained health during long fights"),
        ABSORPTION_HEAL("absorption_heal", "Golden apple for absorption + regen"),
        SPEED_PREPARE("speed_prepare", "Speed potion + steak before approach"),
        STRENGTH_PREPARE("strength_prepare", "Strength potion before engagement");

        private final String name;
        private final String description;

        HealAction(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
