package baritone.aimassist.intelligence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;

/**
 * BowCombat — detailed bow combat logic.
 *
 * Bow states:
 *   IDLE          — No target or not drawing
 *   DRAW          — Charging the bow
 *   AIM           — Aiming at target
 *   SHOOT         — Release arrow
 *   RAPID_FIRE    — Crossbow rapid fire
 *   RETREAT       — Back away while drawing
 *   RELOAD        — Reload crossbow
 *   HEAL          — Heal between shots
 *   DODGE         — Dodge while maintaining aim
 *   FINISH        — Finish wounded target with arrow
 *   COVER         — Use cover while drawing
 *
 * Bow mechanics:
 *   Charge time: 20-60 ticks for full power
 *   Draw speed affected by Quick Charge enchantment
 *   Crossbow: needs loading, can Rapid Fire
 *   Arrow trajectory affected by gravity and wind
 *   Movement while drawing reduces accuracy
 *
 * Decision tree:
 *   if target in range && bow drawn && ready → SHOOT
 *   if bow not drawn && target in range → DRAW
 *   if crossbow not loaded → RELOAD
 *   if health < 0.4 → HEAL
 *   if incoming projectile → DODGE
 *   if target close → REPAIR/CLOSE with sword
 *   if target retreating → INTERCEPT with arrow
 */
public class BowCombat {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final double BOW_FULL_CHARGE_TICKS = 60;
    private static final double BOW_MIN_CHARGE_TICKS = 20;
    private static final double CROSSBOW_MIN_CHARGE_TICKS = 25;
    private static final double MAX_RANGE = 50;
    private static final double OPTIMAL_RANGE = 15;
    private static final double CLOSE_RANGE = 5;

    private int drawTicks = 0;
    private boolean drawing = false;
    private boolean charged = false;
    private boolean rapidFireReady = false;
    private long lastShotTime = 0;
    private static final long SHOT_COOLDOWN_MS = 500;

    /**
     * Determine the best bow action given current state.
     */
    public BowAction decide(LivingEntity target) {
        if (mc.player == null || target == null) return BowAction.IDLE;

        double dist = mc.player.distanceTo(target);
        ItemStack held = mc.player.getMainHandItem();
        boolean isBow = held.is(Items.BOW);
        boolean isCrossbow = held.is(Items.CROSSBOW);
        boolean isCrossbowLoaded = isCrossbow && CrossbowItem.isCharged(held);
        float str = mc.player.getAttackStrengthScale(0.5f);

        float hp = mc.player.getHealth() / mc.player.getMaxHealth();

        // Emergency: low health → retreat or heal
        if (hp < 0.3) {
            if (isBow && drawing) return BowAction.RELOAD; // Cancel draw, retreat
            return BowAction.HEAL_RETREAT;
        }

        // Emergency: incoming projectile → dodge while maintaining aim
        if (isIncomingProjectile()) {
            if (drawing && dist > CLOSE_RANGE) return BowAction.DODGE_DRAW;
            return BowAction.DODGE;
        }

        // Crossbow reload: not loaded
        if (isCrossbow && !isCrossbowLoaded) {
            return BowAction.RELOAD;
        }

        // Crossbow rapid fire: loaded and ready
        if (isCrossbowLoaded && rapidFireReady) {
            return BowAction.RAPID_FIRE;
        }

        // Target in close range → switch to melee
        if (dist < CLOSE_RANGE) {
            return BowAction.CLOSE_COMBAT;
        }

        // Target in optimal range → draw/shoot
        if (dist >= OPTIMAL_RANGE && dist <= MAX_RANGE) {
            if (!drawing && !isCrossbowLoaded) {
                return BowAction.DRAW;
            }
            if (drawing && str >= 1.0f) {
                return BowAction.SHOOT;
            }
            if (drawing && str < 1.0f) {
                return BowAction.AIM;
            }
            if (isCrossbowLoaded) {
                return BowAction.SHOOT;
            }
        }

        // Target far but in range → draw
        if (dist > OPTIMAL_RANGE && dist <= MAX_RANGE && !drawing) {
            return BowAction.DRAW;
        }

        // Target retreating → intercept
        if (dist > OPTIMAL_RANGE && target.isMoving()) {
            if (drawing) return BowAction.INTERCEPT;
            return BowAction.COVER_SHOOT;
        }

        // No target or out of range → idle
        if (dist > MAX_RANGE) {
            drawing = false;
            return BowAction.IDLE;
        }

        // Default: wait for charge
        if (drawing && str < 1.0f) {
            return BowAction.AIM;
        }

        return BowAction.IDLE;
    }

    /**
     * Check if a projectile is incoming.
     */
    private boolean isIncomingProjectile() {
        if (mc.level == null || mc.player == null) return false;
        double scanRange = 20;
        for (net.minecraft.world.entity.Entity e : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(
                    mc.player.getX() - scanRange, mc.player.getY() - 20, mc.player.getZ() - scanRange,
                    mc.player.getX() + scanRange, mc.player.getY() + 20, mc.player.getZ() + scanRange
                ))) {
            if (e instanceof net.minecraft.world.entity.projectile.Projectile && e.isAlive()) {
                double dist = mc.player.distanceTo(e);
                if (dist < scanRange && e.getDeltaMovement().lengthSqr() > 0.01) return true;
            }
        }
        return false;
    }

    /**
     * Start drawing the bow.
     */
    public void startDraw() {
        drawing = true;
        drawTicks = 0;
        charged = false;
    }

    /**
     * Stop drawing (cancel).
     */
    public void stopDraw() {
        drawing = false;
        drawTicks = 0;
    }

    /**
     * Increment draw ticks (called each tick while drawing).
     */
    public void tickDraw() {
        if (drawing) drawTicks++;
        if (drawTicks >= BOW_FULL_CHARGE_TICKS) {
            charged = true;
            drawing = false;
        }
    }

    /**
     * After successful shot, update state.
     */
    public void onShot() {
        drawTicks = 0;
        drawing = false;
        charged = false;
        lastShotTime = System.currentTimeMillis();
        if (mc.player.getMainHandItem().is(Items.CROSSBOW)) {
            rapidFireReady = true;
        }
    }

    /**
     * After crossbow shot, reset rapid fire.
     */
    public void onRapidFire() {
        rapidFireReady = false;
    }

    public int getDrawTicks() { return drawTicks; }
    public boolean isDrawing() { return drawing; }
    public boolean isCharged() { return charged; }
    public boolean isRapidFireReady() { return rapidFireReady; }

    /**
     * Bow action types.
     */
    public enum BowAction {
        IDLE("idle", "No target or ready"),
        DRAW("draw", "Start charging bow"),
        AIM("aim", "Aiming while charging"),
        SHOOT("shoot", "Release arrow"),
        RAPID_FIRE("rapid_fire", "Crossbow rapid fire"),
        RETREAT("retreat", "Back away while drawing"),
        RELOAD("reload", "Reload crossbow"),
        HEAL("heal", "Eat food"),
        HEAL_RETREAT("heal_retreat", "Heal while retreating"),
        DODGE("dodge", "Dodge incoming projectile"),
        DODGE_DRAW("dodge_draw", "Dodge while maintaining draw"),
        CLOSE_COMBAT("close_combat", "Switch to melee at close range"),
        FINISH("finish", "Deliver killing arrow"),
        COVER("cover", "Use cover while drawing"),
        INTERCEPT("intercept", "Cut off retreating target"),
        COVER_SHOOT("cover_shoot", "Shoot from cover"),
        REPAIR("repair", "Repair equipment"),
        CHARGE("charge", "Charge crossbow fully"),
        WAIT("wait", "Wait for target");

        private final String name;
        private final String description;

        BowAction(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
