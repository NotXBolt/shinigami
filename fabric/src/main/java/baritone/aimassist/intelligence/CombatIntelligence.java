package baritone.aimassist.intelligence;

import baritone.aimassist.combat.*;
import baritone.aimassist.learning.ReinforcementLearner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * CombatIntelligence — master combat decision engine (Ultra Instinct).
 *
 * Continuously evaluates the world state and selects the best action:
 * OBSERVE → PREDICT → EVALUATE → DECIDE → ACT → VERIFY → ADAPT → REPEAT
 *
 * Decision priorities (highest to lowest):
 *   EMERGENCY_DODGE       100 — void, lethal fall, explosion, unavoidable projectile
 *   DODGE                 90  — incoming attack/projectile within dodge window
 *   COMBAT_ESCAPE         80  — retreat when outnumbered or low health
 *   CRIT                  70  — fall-based crit opportunity
 *   ATTACK                60  — engage target with best weapon
 *   MOVEMENT              50  — chase/intercept/parkour
 *   HEAL                  40  — heal when safe window exists
 *   RELOAD                30  — crossbow reload, bow draw
 *   IDLE                  10  — wait/observe
 */
public class CombatIntelligence {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;
    private final ReinforcementLearner learner;

    // Combat state machine
    private CombatState state = CombatState.IDLE;
    private long stateSince = 0;
    private LivingEntity currentTarget = null;
    private Vec3 lastDodgeDir = Vec3.ZERO;
    private int consecutiveMisses = 0;
    private int consecutiveHits = 0;

    // Decision history for adaptation
    private static final int HISTORY_SIZE = 50;
    private final double[] decisionRewards = new double[HISTORY_SIZE];
    private int historyIndex = 0;

    // Ultra Instinct parameters
    private double threatScore = 0.0;
    private double opportunityScore = 0.0;
    private double survivalScore = 0.0;

    public CombatIntelligence(AimAssistConfig config) {
        this.config = config;
        this.learner = new ReinforcementLearner();
    }

    /**
     * Main decision tick — called every game tick.
     * Returns the best action to execute.
     */
    public synchronized CombatAction decide() {
        if (mc.player == null || mc.level == null) return CombatAction.IDLE;

        observe();
        if (state == CombatState.IDLE) {
            return handleIdle();
        }
        switch (state) {
            case IDLE:       return handleIdle();
            case APPROACH:   return handleApproach();
            case ENGAGE:     return handleEngage();
            case PRESSURE:   return handlePressure();
            case EVADE:      return handleEvade();
            case RECOVER:    return handleRecover();
            case HEAL:       return handleHeal();
            case CLUTCH:     return handleClutch();
            case CHASE:      return handleChase();
            case FINISH:     return handleFinish();
            case ESCAPE:     return handleEscape();
            default:         return handleIdle();
        }
    }

    // ─── OBSERVE: Build world state ────────────────────────────

    private void observe() {
        if (mc.player == null || mc.level == null) return;

        // Find best target
        currentTarget = findBestTarget();

        // Calculate threat score
        threatScore = calculateThreatScore();
        opportunityScore = calculateOpportunityScore();
        survivalScore = calculateSurvivalScore();

        // Update state machine
        updateState();
    }

    private LivingEntity findBestTarget() {
        if (mc.level == null || mc.player == null) return null;
        double range = config.getRange();
        LivingEntity best = null;
        double closest = range + 1;
        double scanRange = range + 4;

        for (net.minecraft.world.entity.Entity e : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(
                    mc.player.getX() - scanRange, mc.player.getY() - scanRange, mc.player.getZ() - scanRange,
                    mc.player.getX() + scanRange, mc.player.getY() + scanRange, mc.player.getZ() + scanRange
                ))) {
            if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            double dist = mc.player.distanceTo(living);
            if (dist < closest && dist < range) {
                closest = dist;
                best = living;
            }
        }
        return best;
    }

    private double calculateThreatScore() {
        if (mc.level == null || mc.player == null) return 0;
        double score = 0;
        double scanRange = config.getRange() + 4;

        for (net.minecraft.world.entity.Entity e : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(
                    mc.player.getX() - scanRange, mc.player.getY() - 20, mc.player.getZ() - scanRange,
                    mc.player.getX() + scanRange, mc.player.getY() + 20, mc.player.getZ() + scanRange
                ))) {
            if (!e.isAlive() || e == mc.player) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > scanRange) continue;

            double urgency = 1.0 / Math.max(dist, 0.5);
            if (e instanceof Monster m && m.getTarget() == mc.player) score += urgency * 3.0;
            else if (e instanceof Player p && p != mc.player && p.isAlive()) score += urgency * 4.0;
            else if (e instanceof net.minecraft.world.entity.projectile.Projectile) score += urgency * 2.0;
            else if (e instanceof net.minecraft.world.entity.item.PrimedTnt) score += urgency * 5.0;
            else if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) score += urgency * 6.0;
            else if (e instanceof net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull) score += urgency * 4.0;
            else if (e instanceof net.minecraft.world.entity.projectile.hurtingprojectile.Fireball) score += urgency * 4.0;
            else if (e instanceof net.minecraft.world.entity.projectile.ShulkerBullet) score += urgency * 3.0;
            else if (e instanceof net.minecraft.world.entity.item.FallingBlockEntity) score += urgency * 3.0;
            else if (e instanceof net.minecraft.world.entity.projectile.arrow.ThrownTrident) score += urgency * 3.0;
            else if (e instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion) score += urgency * 2.0;
            else if (e instanceof net.minecraft.world.entity.monster.Monster) score += urgency * 1.5;
            else if (e instanceof Player) score += urgency * 2.0;
        }
        return score;
    }

    private double calculateOpportunityScore() {
        if (mc.player == null || currentTarget == null) return 0;
        double score = 0;
        double dist = mc.player.distanceTo(currentTarget);

        // Target health advantage
        float targetHp = currentTarget.getHealth();
        float myHp = mc.player.getHealth();
        float targetMaxHp = currentTarget.getMaxHealth();
        float myMaxHp = mc.player.getMaxHealth();

        double hpRatio = (myHp / myMaxHp) / Math.max(targetHp / targetMaxHp, 0.1);
        score += hpRatio * 0.3;

        // Distance advantage (closer = better for melee)
        if (dist < 3.5) score += 0.4;
        else if (dist < 5) score += 0.2;
        else if (dist < 10) score += 0.1;

        // Target is vulnerable (attacking, jumping, sprinting)
        if (currentTarget.isUsingItem()) score -= 0.2;
        if (currentTarget.isPassenger()) score -= 0.1;

        // Attack cooldown advantage
        float str = mc.player.getAttackStrengthScale(0.5f);
        if (str >= 0.9f) score += 0.3;

        // Weapon advantage
        ItemStack held = mc.player.getMainHandItem();
        if (held.is(Items.AXE)) score += 0.15;
        if (held.is(Items.MACE)) score += 0.15;
        if (held.is(Items.SWORD)) score += 0.1;
        if (held.is(Items.BOW) || held.is(Items.CROSSBOW)) score += 0.1;

        return Math.min(score, 1.0);
    }

    private double calculateSurvivalScore() {
        if (mc.player == null) return 1.0;
        double score = 0;
        float hp = mc.player.getHealth();
        float maxHp = mc.player.getMaxHealth();
        score += (hp / maxHp);

        // Food level
        int food = mc.player.getFoodData().getFoodLevel();
        score += (food / 20.0) * 0.2;

        // Armor
        ItemStack helmet = mc.player.getInventory().getArmor(3);
        ItemStack chest = mc.player.getInventory().getArmor(2);
        ItemStack legs = mc.player.getInventory().getArmor(1);
        ItemStack boots = mc.player.getInventory().getArmor(0);
        int armorCount = 0;
        if (!helmet.isEmpty()) armorCount++;
        if (!chest.isEmpty()) armorCount++;
        if (!legs.isEmpty()) armorCount++;
        if (!boots.isEmpty()) armorCount++;
        score += (armorCount / 4.0) * 0.2;

        return Math.min(score, 1.0);
    }

    // ─── STATE MACHINE ────────────────────────────────────────

    private void updateState() {
        long now = System.currentTimeMillis();
        float myHp = mc.player.getHealth() / mc.player.getMaxHealth();

        // Emergency transitions (highest priority)
        if (myHp < 0.15 && threatScore > 5) {
            transitionTo(CombatState.ESCAPE); return;
        }
        if (isVoidNearby() || isLavaNearby() || isExplosionNearby()) {
            transitionTo(CombatState.CLUTCH); return;
        }
        if (threatScore > 10) {
            transitionTo(CombatState.EVADE); return;
        }

        // Normal transitions
        switch (state) {
            case IDLE:
                if (currentTarget != null && threatScore > 1) transitionTo(CombatState.APPROACH);
                else if (opportunityScore > 0.5) transitionTo(CombatState.ENGAGE);
                break;
            case APPROACH:
                if (currentTarget != null && mc.player.distanceTo(currentTarget) <= 3.5)
                    transitionTo(CombatState.ENGAGE);
                else if (myHp < 0.4) transitionTo(CombatState.RECOVER);
                break;
            case ENGAGE:
                if (threatScore > 5) transitionTo(CombatState.EVADE);
                else if (currentTarget == null || !currentTarget.isAlive()) transitionTo(CombatState.IDLE);
                else if (consecutiveHits > 3 && opportunityScore > 0.6) transitionTo(CombatState.PRESSURE);
                else if (consecutiveMisses > 3) transitionTo(CombatState.RECOVER);
                else if (myHp < 0.3 && currentTarget != null) transitionTo(CombatState.RECOVER);
                break;
            case PRESSURE:
                if (consecutiveHits == 0) transitionTo(CombatState.ENGAGE);
                else if (threatScore > 5) transitionTo(CombatState.EVADE);
                else if (myHp < 0.3) transitionTo(CombatState.RECOVER);
                else if (opportunityScore < 0.3) transitionTo(CombatState.REPOSITION);
                break;
            case EVADE:
                if (threatScore < 2) transitionTo(CombatState.ENGAGE);
                else if (myHp > 0.7 && opportunityScore > 0.5) transitionTo(CombatState.PRESSURE);
                break;
            case RECOVER:
                if (myHp > 0.7) transitionTo(CombatState.ENGAGE);
                else if (opportunityScore > 0.7 && currentTarget != null) transitionTo(CombatState.ENGAGE);
                break;
            case HEAL:
                if (myHp > 0.8) transitionTo(CombatState.ENGAGE);
                break;
            case CLUTCH:
                if (!isVoidNearby() && !isLavaNearby() && !isExplosionNearby()) transitionTo(CombatState.EVADE);
                break;
            case CHASE:
                if (currentTarget == null || !currentTarget.isAlive()) transitionTo(CombatState.IDLE);
                else if (threatScore > 5) transitionTo(CombatState.EVADE);
                break;
            case FINISH:
                if (currentTarget == null || currentTarget.getHealth() <= 0) transitionTo(CombatState.IDLE);
                else transitionTo(CombatState.ENGAGE);
                break;
            case ESCAPE:
                if (threatScore < 2 && myHp > 0.5) transitionTo(CombatState.IDLE);
                break;
            case REPOSITION:
                if (opportunityScore > 0.5) transitionTo(CombatState.PRESSURE);
                else if (threatScore > 3) transitionTo(CombatState.EVADE);
                break;
            default: transitionTo(CombatState.IDLE);
        }
    }

    private void transitionTo(CombatState newState) {
        if (newState != state) {
            state = newState;
            stateSince = System.currentTimeMillis();
        }
    }

    // ─── STATE HANDLERS ───────────────────────────────────────

    private CombatAction handleIdle() {
        return CombatAction.IDLE;
    }

    private CombatAction handleApproach() {
        if (currentTarget == null) return CombatAction.IDLE;
        return CombatAction.CHASE;
    }

    private CombatAction handleEngage() {
        if (currentTarget == null) return CombatAction.IDLE;

        ItemStack held = mc.player.getMainHandItem();
        boolean isBow = held.is(Items.BOW) || held.is(Items.CROSSBOW);
        boolean isMace = held.is(Items.MACE);
        boolean isSword = held.is(net.minecraft.tags.ItemTags.SWORDS);
        boolean isAxe = held.is(net.minecraft.tags.ItemTags.AXES);
        double dist = mc.player.distanceTo(currentTarget);

        // Weapon selection logic
        if (isBow && dist > 5) {
            return CombatAction.SHOOT;
        }
        if (isMace && mc.player.fallDistance > config.getMinSmashHeight()) {
            return CombatAction.SMASH;
        }
        if (isSword || isAxe) {
            if (mc.player.getAttackStrengthScale(0.5f) >= 0.9f) {
                return CombatAction.ATTACK;
            }
            return CombatAction.WAIT_FOR_COOLDOWN;
        }
        return CombatAction.ATTACK;
    }

    private CombatAction handlePressure() {
        if (currentTarget == null) return CombatAction.IDLE;
        return CombatAction.PRESSURE_ATTACK;
    }

    private CombatAction handleEvade() {
        if (mc.player == null) return CombatAction.IDLE;
        // Use RL-trained dodge direction
        Vec3 dodgeDir = computeDodgeDirection();
        return new CombatAction(CombatActionType.DODGE, dodgeDir);
    }

    private CombatAction handleRecover() {
        if (mc.player == null) return CombatAction.IDLE;
        if (mc.player.getHealth() < mc.player.getMaxHealth() * 0.5) {
            return CombatAction.HEAL;
        }
        return CombatAction.REPOSITION;
    }

    private CombatAction handleHeal() {
        return CombatAction.HEAL;
    }

    private CombatAction handleClutch() {
        return CombatAction.CLUTCH;
    }

    private CombatAction handleChase() {
        if (currentTarget == null) return CombatAction.IDLE;
        return CombatAction.CHASE;
    }

    private CombatAction handleFinish() {
        if (currentTarget == null || currentTarget.getHealth() <= 0) return CombatAction.IDLE;
        return CombatAction.FINISH_ATTACK;
    }

    private CombatAction handleEscape() {
        return CombatAction.ESCAPE;
    }

    // ─── COMBAT DECISIONS ─────────────────────────────────────

    private Vec3 computeDodgeDirection() {
        if (mc.level == null || mc.player == null) return Vec3.ZERO;
        // Aggregate all threats and pick safest direction
        Vec3 repulsion = Vec3.ZERO;
        double scanRange = config.getRange() + 4;

        for (net.minecraft.world.entity.Entity e : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(
                    mc.player.getX() - scanRange, mc.player.getY() - 20, mc.player.getZ() - scanRange,
                    mc.player.getX() + scanRange, mc.player.getY() + 20, mc.player.getZ() + scanRange
                ))) {
            if (!e.isAlive() || e == mc.player) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > scanRange) continue;
            Vec3 offset = mc.player.position().subtract(e.position());
            double d = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (d < 0.3) continue;
            double w = (scanRange - Math.min(dist, scanRange)) / scanRange;
            repulsion = repulsion.add(new Vec3(offset.x / d * w, 0, offset.z / d * w));
        }
        if (repulsion.lengthSqr() > 0.01) {
            return repulsion.normalize();
        }
        return new Vec3(0, 0, 1);
    }

    private boolean isVoidNearby() {
        if (mc.level == null || mc.player == null) return false;
        return mc.player.getY() <= mc.level.getMinY() + 2;
    }

    private boolean isLavaNearby() {
        if (mc.level == null || mc.player == null) return false;
        double scanRange = 5;
        for (net.minecraft.world.level.block.BlockPos bp : net.minecraft.core.BlockPos.betweenClosed(
                (int) mc.player.getX() - (int) scanRange, (int) mc.player.getY() - 2, (int) mc.player.getZ() - (int) scanRange,
                (int) mc.player.getX() + (int) scanRange, (int) mc.player.getY() + 2, (int) mc.player.getZ() + (int) scanRange)) {
            if (mc.level.getBlockState(bp).is(net.minecraft.world.level.block.Blocks.LAVA)) return true;
        }
        return false;
    }

    private boolean isExplosionNearby() {
        if (mc.level == null || mc.player == null) return false;
        double scanRange = 13;
        for (net.minecraft.world.entity.Entity e : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(
                    mc.player.getX() - scanRange, mc.player.getY() - 20, mc.player.getZ() - scanRange,
                    mc.player.getX() + scanRange, mc.player.getY() + 20, mc.player.getZ() + scanRange
                ))) {
            if (e instanceof net.minecraft.world.entity.item.PrimedTnt && e.isAlive()
                && mc.player.distanceTo(e) < 13) return true;
            if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal && e.isAlive()
                && mc.player.distanceTo(e) < 7) return true;
        }
        return false;
    }

    // ─── WEAPON SELECTION ─────────────────────────────────────

    /**
     * Select the best weapon based on combat situation.
     * Returns the slot index (0-8) for the hotbar.
     */
    public int selectBestWeapon() {
        if (mc.player == null || mc.level == null) return -1;
        if (currentTarget == null) return selectBestMelee();

        double dist = mc.player.distanceTo(currentTarget);
        ItemStack held = mc.player.getMainHandItem();

        // If at range with bow available, use bow
        if (dist > 5 && hasBow()) return findBowSlot();

        // If target is using shield, use axe for shield break
        if (currentTarget.isUsingItem() && currentTarget.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
            return findAxeSlot();
        }

        // Default to best melee
        return selectBestMelee();
    }

    private int selectBestMelee() {
        if (mc.player == null) return -1;
        // Prefer axe > sword > mace for damage
        int axeSlot = findAxeSlot();
        if (axeSlot >= 0) return axeSlot;
        int swordSlot = findSwordSlot();
        if (swordSlot >= 0) return swordSlot;
        return findMaceSlot();
    }

    private boolean hasBow() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return true;
        }
        return false;
    }

    private int findBowSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) return i;
        }
        return -1;
    }

    private int findAxeSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(net.minecraft.tags.ItemTags.AXES)) return i;
        }
        return -1;
    }

    private int findSwordSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(net.minecraft.tags.ItemTags.SWORDS)) return i;
        }
        return -1;
    }

    private int findMaceSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.MACE)) return i;
        }
        return -1;
    }

    public CombatState getState() { return state; }
    public LivingEntity getCurrentTarget() { return currentTarget; }
    public double getThreatScore() { return threatScore; }
    public double getOpportunityScore() { return opportunityScore; }
    public double getSurvivalScore() { return survivalScore; }
}
