package baritone.aimassist.intelligence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * TridentCombat — detailed trident combat (ranged throw + melee).
 *
 * Trident states:
 *   IDLE          — Waiting
 *   THROW         — Throw trident at range
 *   MELEE         — Melee attack after throw returns
 *   RETRIEVE      — Retrieve thrown trident
 *   DODGE_PROJECTILE — Dodge incoming projectile
 *   DODGE_MELEE   — Dodge incoming melee
 *   DODGE_EXPLOSION — Dodge explosion
 *   HEAL          — Heal on cooldown
 *   RETREAT       — Retreat when low
 *   CLUTCH        — Emergency clutch
 *   FINISH        — Kill wounded target
 *   DODGE_TRIDENT — Dodge incoming trident
 *   CIRCLE        — Circle target
 *   BAIT          — Bait enemy
 *   CHARGE        — Charge attack
 *   RICOCHET      — Ricochet throw off walls
 *
 * Trident mechanics:
 *   Thrown trident returns after 100 ticks (5 seconds)
 *   Trident deals more damage in melee than thrown
 *   Can be thrown at moving targets
 *   Thrown trident can ricochet off blocks
 *   Loyalty enchantment makes trident return automatically
 *   Channeling enchantment summons lightning in rain
 *   Riptide enchantment propels player when thrown in water/rain
 *
 * Decision tree:
 *   if trident in hand && target in range → THROW
 *   if thrown trident returning → RETRIEVE
 *   if trident returned → MELEE
 *   if incoming projectile → DODGE_PROJECTILE
 *   if incoming trident → DODGE_TRIDENT
 *   if low health → HEAL
 */
public class TridentCombat {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final double MELEE_RANGE = 3.5;
    private static final double THROW_RANGE = 20;
    private static final int THROW_COOLDOWN_TICKS = 100;
    private static final int RETRIEVE_RANGE = 10;

    private boolean thrown = false;
    private int throwTicks = 0;
    private boolean retrieving = false;
    private boolean ricochet = false;
    private boolean channelingReady = false;

    /**
     * Determine the best trident action given current state.
     */
    public TridentAction decide(LivingEntity target) {
        if (mc.player == null || target == null) return TridentAction.IDLE;

        double dist = mc.player.distanceTo(target);
        float hp = mc.player.getHealth() / mc.player.getMaxHealth();
        boolean onGround = mc.player.onGround();
        boolean inWater = mc.player.isInWater();
        boolean inRain = mc.player.level.isRainingAt(mc.player.blockPosition());
        ItemStack held = mc.player.getMainHandItem();
        boolean hasTrident = held.is(Items.TRIDENT);
        boolean hasLoyalty = hasEnchantment(Items.TRIDENT, net.minecraft.world.item.enchantment.Enchantments.LOYALTY);
        boolean hasRiptide = hasEnchantment(Items.TRIDENT, net.minecraft.world.item.enchantment.Enchantments.RIPTIDE);
        boolean hasChanneling = hasEnchantment(Items.TRIDENT, net.minecraft.world.item.enchantment.Enchantments.CHANNELING);

        // Emergency: low health → retreat/heal
        if (hp < 0.25) {
            if (thrown && retrieving) return TridentAction.RETRIEVE_ESCAPE;
            return TridentAction.HEAL_RETREAT;
        }

        // Emergency: incoming projectile → dodge
        if (isIncomingProjectile()) return TridentAction.DODGE_PROJECTILE;

        // Emergency: incoming trident → dodge
        if (isIncomingTrident()) return TridentAction.DODGE_TRIDENT;

        // Emergency: explosion → dodge
        if (isExplosionNearby()) return TridentAction.DODGE_EXPLOSION;

        // Throw: trident in hand, target in range
        if (hasTrident && !thrown && dist < THROW_RANGE && !mc.player.isUsingItem()) {
            if (inWater && hasRiptide) return TridentAction.RIPTIDE_BOOST;
            if (inRain && hasChanneling) return TridentAction.CHANNEL_LIGHTNING;
            return TridentAction.THROW;
        }

        // Retrieving: thrown trident returning
        if (thrown && retrieving) {
            if (dist <= RETRIEVE_RANGE) return TridentAction.RETRIEVE;
            return TridentAction.MOVE_TO_RETRIEVE;
        }

        // Melee: trident returned
        if (!thrown && hasTrident && dist <= MELEE_RANGE) {
            return TridentAction.MELEE;
        }

        // Heal
        if (hp < 0.5 && onGround && !mc.player.isUsingItem()) {
            return TridentAction.HEAL;
        }

        // Retreat
        if (hp < 0.4 && dist > MELEE_RANGE) {
            return TridentAction.RETREAT;
        }

        // Kill wounded
        if (target.getHealth() < target.getMaxHealth() * 0.3 && dist <= MELEE_RANGE) {
            return TridentAction.FINISH;
        }

        // Intercept
        if (dist > MELEE_RANGE && target.isMoving()) {
            return TridentAction.INTERCEPT;
        }

        // Circle
        if (dist > MELEE_RANGE && dist < MELEE_RANGE + 5) {
            return TridentAction.CIRCLE;
        }

        // Chase
        if (dist > MELEE_RANGE + 5) {
            return TridentAction.CHASE;
        }

        // Wait
        return TridentAction.WAIT;
    }

    private boolean isIncomingProjectile() { return false; }
    private boolean isIncomingTrident() { return false; }
    private boolean isExplosionNearby() { return false; }
    private boolean hasEnchantment(net.minecraft.world.item.Item item, net.minecraft.world.item.enchantment.Enchantment ench) {
        if (mc.player == null || mc.level == null) return false;
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.is(item)) return false;
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
            mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(ench).orElseThrow(), stack) > 0;
    }

    public void onThrow() { thrown = true; throwTicks = 0; }
    public void onRetrieve() { thrown = false; retrieving = false; }
    public void onRicochet() { ricochet = true; }
    public void onChannelLightning() { channelingReady = true; }
    public void onRiptideBoost() { /* player propelled */ }

    public boolean isThrown() { return thrown; }
    public boolean isRetrieving() { return retrieving; }
    public boolean isRicochet() { return ricochet; }
    public boolean isChannelingReady() { return channelingReady; }

    /**
     * Trident action types.
     */
    public enum TridentAction {
        IDLE("idle", "Wait and observe"),
        THROW("throw", "Throw trident at range"),
        MELEE("melee", "Melee attack with trident"),
        RETRIEVE("retrieve", "Retrieve thrown trident"),
        MOVE_TO_RETRIEVE("move_retrieve", "Move to retrieve trident"),
        RETRIEVE_ESCAPE("retrieve_escape", "Retrieve and escape"),
        RIPPTE_BOOST("riptide", "Riptide water boost"),
        RIPTIDE_BOOST("riptide_boost", "Riptide water boost"),
        CHANNEL_LIGHTNING("channel_lightning", "Channel lightning in rain"),
        DODGE("dodge", "Generic dodge"),
        DODGE_PROJECTILE("dodge_projectile", "Dodge projectile"),
        DODGE_TRIDENT("dodge_trident", "Dodge incoming trident"),
        DODGE_EXPLOSION("dodge_explosion", "Dodge explosion"),
        DODGE_MELEE("dodge_melee", "Dodge melee"),
        HEAL("heal", "Eat food to heal"),
        HEAL_RETREAT("heal_retreat", "Heal while retreating"),
        RETREAT("retreat", "Back away from combat"),
        CHASE("chase", "Chase target"),
        INTERCEPT("intercept", "Cut off retreating target"),
        CIRCLE("circle", "Circle target"),
        FINISH("finish", "Deliver killing blow"),
        CLUTCH("clutch", "Emergency clutch"),
        BAIT("bait", "Bait enemy"),
        WAIT("wait", "Wait for cooldown"),
        CHARGE("charge", "Charge attack");

        private final String name;
        private final String description;

        TridentAction(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
