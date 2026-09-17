package baritone.aimassist.intelligence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.Vec3;

/**
 * SwordCombat — detailed sword combat logic.
 *
 * Sword states:
 *   IDLE          — Waiting for cooldown, observe target
 *   APPROACH      — Moving toward target at range
 *   ENGAGE        — In melee range, preparing to attack
 *   ATTACK        — Executing attack
 *   COMBO         — Rapid successive attacks
 *   CRIT          — Timing fall-based critical hit
 *   SHIELD_BREAK  — Attacking target using shield
 *   DODGE         — Dodging incoming attack mid-combo
 *   HEAL          — Eating food during cooldown window
 *   RETREAT       — Backing away when low health
 *   FINISH        — Killing wounded target
 *
 * Sword mechanics:
 *   Attack cooldown: ~10 ticks (0.848s for full charge)
 *   Critical: jump + attack while falling = guaranteed crit
 *   Sweep: area attack on cooldown
 *   Combo: rapid attacks when cooldown allows
 *   Shield break: axe preferred, sword can break with strength
 *
 * Decision tree:
 *   if target in range && cooldown >= 0.9 → ATTACK
 *   if target using shield → try SHIELD_BREAK (switch to axe)
 *   if falling && cooldown >= 0.9 → CRIT
 *   if combo hits > 3 → COMBO
 *   if health < 0.4 && target far → HEAL
 *   if incoming attack → DODGE
 *   if target low health → FINISH
 */
public class SwordCombat {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final float FULL_CHARGE_THRESHOLD = 0.9f;
    private static final float CRIT_FALL_DISTANCE = 1.0f;
    private static final int MAX_COMBO_HITS = 5;
    private static final double MELEE_RANGE = 3.5;
    private static final double SHIELD_BREAK_RANGE = 4.0;

    // Sword-specific state
    private int comboHits = 0;
    private boolean critPending = false;
    private boolean shieldBreakAttempt = false;
    private boolean attacking = false;
    private long lastAttackTime = 0;
    private static final long ATTACK_COOLDOWN_MS = 850; // ~10 ticks at 20 TPS

    /**
     * Determine the best sword action given current state.
     */
    public SwordAction decide(LivingEntity target) {
        if (mc.player == null || target == null) return SwordAction.IDLE;

        double dist = mc.player.distanceTo(target);
        float str = mc.player.getAttackStrengthScale(0.5f);
        boolean onGround = mc.player.onGround();
        boolean falling = !onGround && mc.player.getDeltaMovement().y < 0;
        float fallDist = mc.player.fallDistance;
        boolean isSprinting = mc.player.isSprinting();
        boolean isUsingItem = mc.player.isUsingItem();
        boolean hasSpeed = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);

        // Emergency: low health + in range → retreat/heal
        float hp = mc.player.getHealth() / mc.player.getMaxHealth();
        if (hp < 0.3 && dist < MELEE_RANGE) {
            if (hasSpeed) return SwordAction.SPEED_RETREAT; // Exploit: speed to escape
            return SwordAction.HEAL_RETREAT;
        }

        // Emergency: incoming attack → dodge
        if (isIncomingAttack(target)) {
            return SwordAction.DODGE;
        }

        // Low health + safe → heal
        if (hp < 0.5 && dist > MELEE_RANGE && !isUsingItem) {
            return SwordAction.HEAL;
        }

        // Critical hit timing: falling + attack
        if (falling && fallDist >= CRIT_FALL_DISTANCE && str >= FULL_CHARGE_THRESHOLD) {
            critPending = true;
            return SwordAction.CRIT;
        }

        // Shield break: target using shield → switch to axe if possible
        if (target.isUsingItem() && target.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
            if (dist < SHIELD_BREAK_RANGE) {
                return SwordAction.SHIELD_BREAK;
            }
        }

        // Combo: after 3+ hits, continue combo
        if (comboHits >= 3 && str >= FULL_CHARGE_THRESHOLD && !isUsingItem) {
            return SwordAction.COMBO;
        }

        // Normal attack: in range + cooldown ready
        if (dist <= MELEE_RANGE && str >= FULL_CHARGE_THRESHOLD && !isUsingItem) {
            return SwordAction.ATTACK;
        }

        // Approach: out of range but target exists
        if (dist > MELEE_RANGE && dist < MELEE_RANGE + 3) {
            if (hasSpeed) return SwordAction.SPEED_APPROACH; // Exploit: speed approach
            return SwordAction.APPROACH;
        }

        // Chase: far away
        if (dist > MELEE_RANGE + 3) {
            return SwordAction.CHASE;
        }

        // Idle: waiting for cooldown
        if (str < FULL_CHARGE_THRESHOLD) {
            return SwordAction.WAIT;
        }

        return SwordAction.IDLE;
    }

    /**
     * Check if target is attacking us (incoming attack).
     */
    private boolean isIncomingAttack(LivingEntity target) {
        if (mc.level == null || target == null) return false;
        // Check if target is swinging
        if (target.isUsingItem()) return false; // Target is using item, not attacking
        // Check attack cooldown of target
        if (target.getAttackAnim(0.0f) > 0.8f) return true;
        return false;
    }

    /**
     * Get attack target point for aim correction.
     */
    public Vec3 getAttackTargetPoint(LivingEntity target) {
        if (target == null) return Vec3.ZERO;
        return target.position().add(0, target.getBbHeight() / 2, 0);
    }

    /**
     * After successful attack, update combo counter.
     */
    public void onAttack() {
        comboHits++;
        lastAttackTime = System.currentTimeMillis();
        attacking = true;
    }

    /**
     * After miss, increment misses and reset combo.
     */
    public void onMiss() {
        comboHits = 0;
        attacking = false;
    }

    /**
     * After hit, increment hits and prepare for combo.
     */
    public void onHit() {
        comboHits++;
        attacking = false;
    }

    public int getComboHits() { return comboHits; }
    public boolean isCritPending() { return critPending; }
    public void setCritPending(boolean b) { this.critPending = b; }
    public boolean isShieldBreakAttempt() { return shieldBreakAttempt; }
    public void setShieldBreakAttempt(boolean b) { this.shieldBreakAttempt = b; }
    public boolean isAttacking() { return attacking; }

    /**
     * Sword action types — every possible sword decision.
     */
    public enum SwordAction {
        IDLE("idle", "Wait and observe"),
        APPROACH("approach", "Move toward target"),
        SPEED_APPROACH("speed_approach", "Speed-boosted approach (exploit)"),
        ATTACK("attack", "Execute sword attack"),
        CRIT("crit", "Fall-based critical hit"),
        COMBO("combo", "Rapid successive attacks"),
        SHIELD_BREAK("shield_break", "Break target's shield"),
        DODGE("dodge", "Dodge incoming attack"),
        HEAL("heal", "Eat food to heal"),
        HEAL_RETREAT("heal_retreat", "Heal while retreating"),
        SPEED_RETREAT("speed_retreat", "Speed-boosted retreat (exploit)"),
        RETREAT("retreat", "Back away from combat"),
        CHASE("chase", "Chase retreating target"),
        FINISH("finish", "Deliver killing blow"),
        WAIT("wait", "Wait for attack cooldown"),
        INTERCEPT("intercept", "Cut off retreating target"),
        CIRCLE("circle", "Circle around target"),
        SURROUND("surround", "Flank target from multiple angles"),
        BAIT("bait", "Feint attack to provoke target");

        private final String name;
        private final String description;

        SwordAction(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
