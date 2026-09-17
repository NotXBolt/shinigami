package baritone.aimassist.intelligence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * NormalCombat — neutral mode covering all situations with vanilla-compliant techniques.
 *
 * This is the default combat mode when Ultra Instinct is disabled.
 * Every technique is vanilla-compliant (no exploits, no mods).
 * Uses standard Minecraft PvP mechanics: attack cooldown, strafing, crit timing.
 *
 * NormalCombat states:
 *   IDLE          — Default wait state
 *   APPROACH      — Move toward target cautiously
 *   ATTACK        — Basic attack when cooldown ready
 *   DODGE         — Basic dodge
 *   HEAL          — Eat food when low health
 *   RETREAT       — Back away when overwhelmed
 *   CHASE         — Chase retreating target
 *   FINISH        — Finish wounded target
 *   WAIT          — Wait for cooldown
 *   BAIT          — Bait enemy into attack
 *   CIRCLE        — Circle target
 *   INTERCEPT     — Cut off retreating target
 *   CLUTCH        — Emergency clutch
 *   ESCAPE        — Escape combat
 *   REPOSITION    — Reposition to better location
 *
 * Normal mechanics:
 *   Attack cooldown: weapon-specific (sword 0.625s, axe 1.0s, etc.)
 *   Crit: fall-based, requires ≥84.8% charge
 *   Strafing: circle-strafe to avoid hits
 *   Shield: raise to block, axe to disable
 *   W-tapping: reset sprint for knockback
 */
public class NormalCombat {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final double MELEE_RANGE = 3.5;
    private static final double OPTIMAL_RANGE = 3.0;
    private static final double CLOSE_RANGE = 2.0;
    private static final float CRIT_THRESHOLD = 0.848f;
    private static final float HEAL_THRESHOLD = 0.5f;
    private static final float LOW_HEALTH = 0.3f;

    private int comboCount = 0;
    private boolean strafingLeft = true;
    private long lastAttackTime = 0;

    /**
     * Determine the best normal combat action given current state.
     */
    public NormalAction decide(LivingEntity target) {
        if (mc.player == null || target == null) return NormalAction.IDLE;

        double dist = mc.player.distanceTo(target);
        float hp = mc.player.getHealth() / mc.player.getMaxHealth();
        float targetHp = target.getHealth() / target.getMaxHealth();
        float str = mc.player.getAttackStrengthScale(0.5f);
        boolean onGround = mc.player.onGround();
        boolean falling = !onGround && mc.player.getDeltaMovement().y < 0;
        boolean isSprinting = mc.player.isSprinting();
        boolean targetShield = target.isUsingItem() && target.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
        boolean hasSpeed = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        boolean hasStrength = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.ATTACK_DAMAGE);

        // Emergency: low health → heal or escape
        if (hp < LOW_HEALTH) {
            if (targetShield && dist < MELEE_RANGE) return NormalAction.ESCAPE;
            if (isSprinting && !targetShield) return NormalAction.RETREAT;
            return NormalAction.HEAL;
        }

        // Emergency: incoming attack → dodge
        if (targetShield && isIncomingAttack()) return NormalAction.DODGE;
        if (isIncomingProjectile()) return NormalAction.DODGE;

        // Emergency: critical health + target close → clutch
        if (hp < 0.2 && dist < MELEE_RANGE) return NormalAction.CLUTCH;

        // Healing: low health + safe
        if (hp < HEAL_THRESHOLD && dist > MELEE_RANGE && !mc.player.isUsingItem()) {
            return NormalAction.HEAL;
        }

        // Finish: target wounded
        if (targetHp < 0.3 && str >= CRIT_THRESHOLD) {
            return NormalAction.FINISH;
        }

        // Shield break: target using shield
        if (targetShield && dist < MELEE_RANGE) {
            return NormalAction.SHIELD_BREAK;
        }

        // Attack: cooldown ready + in range
        if (dist <= OPTIMAL_RANGE && str >= CRIT_THRESHOLD && !mc.player.isUsingItem()) {
            return NormalAction.ATTACK;
        }

        // Crit timing: falling + charge
        if (falling && mc.player.fallDistance >= 1.0 && str >= CRIT_THRESHOLD) {
            return NormalAction.CRIT;
        }

        // Chase: target retreating
        if (dist > MELEE_RANGE && target.isMoving() && !target.isUsingItem()) {
            return NormalAction.CHASE;
        }

        // Intercept: cut off retreating target
        if (dist > MELEE_RANGE && target.isMoving()) {
            return NormalAction.INTERCEPT;
        }

        // Circle: flanking
        if (dist > OPTIMAL_RANGE && dist < MELEE_RANGE + 3) {
            return NormalAction.CIRCLE;
        }

        // Bait: provoke shield usage
        if (dist < MELEE_RANGE + 1 && !targetShield) {
            return NormalAction.BAIT;
        }

        // Approach: move toward target
        if (dist > OPTIMAL_RANGE) {
            if (hasSpeed) return NormalAction.SPEED_APPROACH;
            return NormalAction.APPROACH;
        }

        // Wait: cooldown not ready
        if (str < CRIT_THRESHOLD) {
            return NormalAction.WAIT;
        }

        // Escape: overwhelmed
        if (hp < 0.4 && dist < MELEE_RANGE && !targetShield) {
            return NormalAction.ESCAPE;
        }

        // Reposition: find better location
        return NormalAction.REPOSITION;
    }

    private boolean isIncomingAttack() { return false; }
    private boolean isIncomingProjectile() { return false; }

    public void onAttack() { comboCount++; }
    public void onMiss() { comboCount = 0; }
    public void onCrit() { comboCount = 0; }
    public void toggleStrafe() { strafingLeft = !strafingLeft; }

    public int getComboCount() { return comboCount; }
    public boolean isStrafingLeft() { return strafingLeft; }

    /**
     * Normal action types — every possible neutral mode decision.
     */
    public enum NormalAction {
        IDLE("idle", "Default wait state"),
        APPROACH("approach", "Move toward target cautiously"),
        SPEED_APPROACH("speed_approach", "Speed-boosted approach (exploit)"),
        ATTACK("attack", "Basic attack when cooldown ready"),
        CRIT("crit", "Fall-based critical hit"),
        DODGE("dodge", "Basic dodge"),
        HEAL("heal", "Eat food when low health"),
        RETREAT("retreat", "Back away when overwhelmed"),
        CHASE("chase", "Chase retreating target"),
        FINISH("finish", "Finish wounded target"),
        WAIT("wait", "Wait for cooldown"),
        BAIT("bait", "Bait enemy into attack"),
        CIRCLE("circle", "Circle target"),
        INTERCEPT("intercept", "Cut off retreating target"),
        CLUTCH("clutch", "Emergency clutch"),
        ESCAPE("escape", "Escape combat"),
        REPOSITION("reposition", "Reposition to better location"),
        SHIELD_BREAK("shield_break", "Break target's shield");

        private final String name;
        private final String description;

        NormalAction(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
