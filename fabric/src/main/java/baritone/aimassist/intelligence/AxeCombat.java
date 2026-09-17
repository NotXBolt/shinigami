package baritone.aimassist.intelligence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.Vec3;

/**
 * AxeCombat — detailed axe combat with shield break, spin, and recovery.
 *
 * Axe states:
 *   IDLE          — Waiting
 *   APPROACH      — Moving to range
 *   ENGAGE        — In melee range
 *   ATTACK        — Execute axe attack
 *   SPIN          — Spin attack
 *   SHIELD_BREAK  — Break target's shield
 *   DODGE_MELEE   — Dodge incoming melee
 *   DODGE_PROJECTILE — Dodge projectile
 *   DODGE_EXPLOSION — Dodge explosion
 *   DODGE_SHIELD — Dodge shield bash
 *   HEAL          — Heal on cooldown
 *   RETREAT       — Retreat when low
 *   CLUTCH        — Emergency clutch
 *   FINISH        — Kill wounded target
 *   INTERCEPT     — Cut off retreating target
 *   CIRCLE        — Circle target for flanking
 *   BAIT          — Bait shield usage
 *
 * Axe mechanics:
 *   Higher damage than sword (1.3x)
 *   Can break shields on hit
 *   Slower attack speed
 *   Spin attack = area damage
 *   Shield bash stuns target
 *
 * Decision tree:
 *   if target using shield && in range → SHIELD_BREAK
 *   if combo hits > 2 && cooldown ready → SPIN
 *   if incoming melee → DODGE_MELEE
 *   if low health → HEAL or RETREAT
 *   if target wounded → FINISH
 *   if target retreating → INTERCEPT
 */
public class AxeCombat {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final double AXE_DAMAGE_BONUS = 1.3;
    private static final double MELEE_RANGE = 3.5;
    private static final double SHIELD_BREAK_RANGE = 4.0;
    private static final float FULL_CHARGE_THRESHOLD = 0.9f;
    private static final int MAX_SPIN_HITS = 3;
    private static final int SPIN_DURATION = 10;

    private int comboHits = 0;
    private int spinHits = 0;
    private boolean spinning = false;
    private boolean shieldBreakAttempt = false;
    private long lastAttackTime = 0;

    // Axe-specific dodge types
    private enum DodgeType {
        MELEE, PROJECTILE, EXPLOSION, SHIELD, CLAW, FALLING_BLOCK, VOID, LAVA, WITCH_POTION
    }

    /**
     * Determine the best axe action given current state.
     */
    public AxeAction decide(LivingEntity target) {
        if (mc.player == null || target == null) return AxeAction.IDLE;

        double dist = mc.player.distanceTo(target);
        float hp = mc.player.getHealth() / mc.player.getMaxHealth();
        float str = mc.player.getAttackStrengthScale(0.5f);
        boolean onGround = mc.player.onGround();
        boolean falling = !onGround && mc.player.getDeltaMovement().y < 0;
        boolean isUsingItem = mc.player.isUsingItem();
        boolean targetShield = target.isUsingItem() && target.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem;
        boolean hasSpeed = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        boolean hasStrength = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.ATTACK_DAMAGE);

        // Emergency: low health → retreat/heal
        if (hp < 0.25) {
            if (hasSpeed) return AxeAction.SPEED_RETREAT;
            if (falling) return AxeAction.RECOVER_LAND;
            return AxeAction.HEAL_RETREAT;
        }

        // Emergency: incoming attacks → dodge
        if (targetShield && dist < SHIELD_BREAK_RANGE) return AxeAction.SHIELD_BREAK;
        if (isIncomingMelee()) return dodge(DodgeType.MELEE);
        if (isIncomingProjectile()) return dodge(DodgeType.PROJECTILE);
        if (isExplosionNearby()) return dodge(DodgeType.EXPLOSION);

        // Shield break: target using shield
        if (targetShield && dist < SHIELD_BREAK_RANGE && str >= FULL_CHARGE_THRESHOLD) {
            return AxeAction.SHIELD_BREAK;
        }

        // Spin attack: combo hits > 2
        if (comboHits >= MAX_SPIN_HITS && str >= FULL_CHARGE_THRESHOLD) {
            return AxeAction.SPIN;
        }

        // Heal: low health + on ground
        if (hp < 0.5 && onGround && !isUsingItem) {
            return AxeAction.HEAL;
        }

        // Retreat: low health + far
        if (hp < 0.4 && dist > MELEE_RANGE) {
            return AxeAction.RETREAT;
        }

        // Kill: target wounded
        if (target.getHealth() < target.getMaxHealth() * 0.3 && str >= FULL_CHARGE_THRESHOLD) {
            return AxeAction.FINISH;
        }

        // Intercept: target retreating
        if (dist > MELEE_RANGE && target.isMoving() && !target.isUsingItem()) {
            return AxeAction.INTERCEPT;
        }

        // Circle: flanking
        if (dist > MELEE_RANGE + 1 && dist < MELEE_RANGE + 5) {
            return AxeAction.CIRCLE;
        }

        // Normal attack
        if (dist <= MELEE_RANGE && str >= FULL_CHARGE_THRESHOLD && !isUsingItem) {
            return AxeAction.ATTACK;
        }

        // Approach
        if (dist > MELEE_RANGE && dist < MELEE_RANGE + 3) {
            if (hasSpeed) return AxeAction.SPEED_APPROACH;
            return AxeAction.APPROACH;
        }

        // Chase
        if (dist > MELEE_RANGE + 3) {
            return AxeAction.CHASE;
        }

        // Wait
        if (str < FULL_CHARGE_THRESHOLD) {
            return AxeAction.WAIT;
        }

        return AxeAction.IDLE;
    }

    private DodgeType[] dodge(DodgeType type) {
        return new DodgeType[]{type}; // Simplified
    }

    private boolean isIncomingMelee() { return false; }
    private boolean isIncomingProjectile() { return false; }
    private boolean isExplosionNearby() { return false; }

    public void onAttack() { comboHits++; }
    public void onSpin() { spinHits++; spinning = true; }
    public void onShieldBreak() { shieldBreakAttempt = true; comboHits = 0; }
    public void onMiss() { comboHits = 0; spinning = false; }
    public void onHit() { comboHits++; spinning = false; }

    public int getComboHits() { return comboHits; }
    public boolean isSpinning() { return spinning; }
    public boolean isShieldBreakAttempt() { return shieldBreakAttempt; }

    /**
     * Axe action types.
     */
    public enum AxeAction {
        IDLE("idle", "Wait and observe"),
        APPROACH("approach", "Move to melee range"),
        SPEED_APPROACH("speed_approach", "Speed-boosted approach (exploit)"),
        ATTACK("attack", "Execute axe attack"),
        SPIN("spin", "Spin attack area damage"),
        SHIELD_BREAK("shield_break", "Break target's shield"),
        DODGE("dodge", "Generic dodge"),
        DODGE_MELEE_CIRCLE("dodge_melee", "Dodge melee in circle"),
        DODGE_PROJECTILE_PERP("dodge_projectile", "Dodge projectile perpendicular"),
        DODGE_EXPLOSION_AWAY("dodge_explosion", "Dodge explosion away"),
        DODGE_SHIELD("dodge_shield", "Dodge shield bash"),
        HEAL("heal", "Eat food to heal"),
        HEAL_RETREAT("heal_retreat", "Heal while retreating"),
        RETREAT("retreat", "Back away from combat"),
        SPEED_RETREAT("speed_retreat", "Speed-boosted retreat (exploit)"),
        CHASE("chase", "Chase target"),
        INTERCEPT("intercept", "Cut off retreating target"),
        CIRCLE("circle", "Circle target for flanking"),
        FINISH("finish", "Deliver killing blow"),
        BAIT("bait", "Bait shield usage"),
        CLUTCH("clutch", "Emergency clutch"),
        RECOVER_LAND("recover_land", "Recover and land"),
        WAIT("wait", "Wait for cooldown");

        private final String name;
        private final String description;

        AxeAction(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
