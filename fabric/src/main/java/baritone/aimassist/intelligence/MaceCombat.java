package baritone.aimassist.intelligence;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.Vec3;

/**
 * MaceCombat — detailed mace combat with smash, wind burst, recovery.
 *
 * Mace states:
 *   IDLE          — Waiting, observe
 *   APPROACH      — Moving to melee range
 *   LAUNCH        — Self-launch with charge for height
 *   SMASH         — Smash attack during fall (fallDistance > min)
 *   WIND_BURST    — Use Wind Burst enchantment mid-air
 *   LAND          — Land safely after smash
 *   RECOVER       — Recover from missed smash
 *   DODGE_PROJECTILE — Dodge incoming projectile
 *   DODGE_MELEE   — Dodge incoming melee attack
 *   DODGE_EXPLOSION — Dodge explosion
 *   DODGE_MACE    — Dodge incoming mace attack
 *   HEAL          — Heal after landing
 *   RETREAT       — Retreat when low health
 *   CLUTCH        — Emergency clutch
 *   FINISH        — Finish wounded target
 *
 * Mace mechanics:
 *   Smash requires fall distance > 1.5 blocks
 *   Wind Burst enchantment provides mid-air boost
 *   Mace smash deals massive fall damage
 *   Self-launch with fire/wind charge for height
 *   Landing position must be safe
 *
 * Decision tree:
 *   if on ground && target in range && has charge → LAUNCH
 *   if falling && fallDist > 1.5 && str >= 0.9 → SMASH
 *   if falling && windBurstReady → WIND_BURST
 *   if incoming projectile → DODGE_PROJECTILE
 *   if incoming mace → DODGE_MACE
 *   if health < 0.4 && landing → HEAL
 *   if safe landing available → LAND
 *   if no safe landing → RECOVER
 */
public class MaceCombat {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final float MIN_SMASH_HEIGHT = 1.5f;
    private static final float LAUNCH_MIN_FALL = 2.0f;
    private static final double SMASH_RANGE = 5.0;
    private static final double LAUNCH_RANGE = 8.0;
    private static final int WIND_BURST_DURATION = 10;
    private static final int SMASH_DURATION = 15;
    private static final int LANDING_BUFFER_TICKS = 5;

    private boolean launching = false;
    private boolean smashing = false;
    private boolean windBurstActive = false;
    private boolean windBurstReady = false;
    private boolean landed = false;
    private int smashTicks = 0;
    private int windBurstTicks = 0;
    private int launchCooldown = 0;

    // Mace-specific dodge vectors
    private enum DodgeType {
        PROJECTILE,     // Dodge arrows, fireballs, tridents
        MELEE,          // Dodge sword/axe attacks
        EXPLOSION,      // Dodge TNT, creeper, end crystal
        MACE,           // Dodge incoming mace attack
        WITCH_POTION,   // Dodge thrown potions
        FALLING_BLOCK,  // Dodge falling blocks
        SELF_SMASH,     // Dodge our own failed smash
        VOID,           // Dodge void/fall damage
        LAVA,          // Dodge lava
        CLAW           // Dodge claw attacks (wither, etc.)
    }

    /**
     * Determine the best mace action given current state.
     */
    public MaceAction decide(LivingEntity target) {
        if (mc.player == null || target == null) return MaceAction.IDLE;

        double dist = mc.player.distanceTo(target);
        float hp = mc.player.getHealth() / mc.player.getMaxHealth();
        boolean onGround = mc.player.onGround();
        boolean falling = !onGround && mc.player.getDeltaMovement().y < 0;
        float fallDist = mc.player.fallDistance;
        ItemStack held = mc.player.getMainHandItem();
        boolean hasMace = held.is(Items.MACE);
        boolean hasWindBurst = hasEnchantment(Items.MACE, net.minecraft.world.item.enchantment.Enchantments.WIND_BURST);
        boolean hasDensity = hasEnchantment(Items.MACE, net.minecraft.world.item.enchantment.Enchantments.DENSITY);
        boolean hasFireCharge = hasItem(Items.FIRE_CHARGE);
        boolean hasWindCharge = hasItem(Items.WIND_CHARGE);

        // Emergency: low health → recover/heal
        if (hp < 0.25) {
            if (falling) return MaceAction.RECOVER_LAND;
            if (!onGround) return MaceAction.RECOVER_LAND;
            return MaceAction.HEAL_RETREAT;
        }

        // Emergency: incoming projectile → dodge
        if (isIncomingProjectile()) {
            return dodge(DodgeType.PROJECTILE);
        }

        // Emergency: incoming explosion → dodge
        if (isExplosionNearby()) {
            return dodge(DodgeType.EXPLOSION);
        }

        // Emergency: incoming mace → dodge
        if (isIncomingMace()) {
            return dodge(DodgeType.MACE);
        }

        // Self-launch: on ground, has charge, target in range
        if (onGround && launchCooldown == 0 && hasFireCharge && hasWindCharge && dist < LAUNCH_RANGE) {
            return MaceAction.LAUNCH;
        }

        // Launching state
        if (launching) {
            return MaceAction.LAUNCHING;
        }

        // Smash: falling + fall distance > threshold
        if (falling && fallDist >= MIN_SMASH_HEIGHT && hasMace && hasDensity) {
            return MaceAction.SMASH;
        }

        // Wind burst: falling + wind burst ready
        if (falling && windBurstActive && windBurstTicks > 0) {
            return MaceAction.WIND_BURST;
        }

        // Wind burst charge: mid-air + wind charge available
        if (falling && !windBurstActive && (hasFireCharge || hasWindCharge)) {
            return MaceAction.WIND_BURST_CHARGE;
        }

        // Landing: about to land safely
        if (falling && fallDist > MIN_SMASH_HEIGHT && isSafeLanding()) {
            return MaceAction.LAND;
        }

        // After smash: recover
        if (smashing && !onGround) {
            return MaceAction.RECOVER;
        }

        // Heal: on ground + low health
        if (onGround && hp < 0.5) {
            return MaceAction.HEAL;
        }

        // Retreat: low health + far from target
        if (hp < 0.4 && dist > SMASH_RANGE) {
            return MaceAction.RETREAT;
        }

        // Normal: approach + smash
        if (dist < SMASH_RANGE && onGround) {
            return MaceAction.APPROACH;
        }

        // Chase target with mace
        if (dist > SMASH_RANGE) {
            return MaceAction.CHASE;
        }

        // Default: idle
        return MaceAction.IDLE;
    }

    /**
     * Get dodge action based on dodge type.
     */
    public MaceAction dodge(DodgeType type) {
        switch (type) {
            case PROJECTILE:
                return MaceAction.DODGE_PROJECTILE_PERP;
            case MELEE:
                return MaceAction.DODGE_MELEE_CIRCLE;
            case EXPLOSION:
                return MaceAction.DODGE_EXPLOSION_AWAY;
            case MACE:
                return MaceAction.DODGE_MACE_PERP;
            case WITCH_POTION:
                return MaceAction.DODGE_WITCH_SHIELD;
            case FALLING_BLOCK:
                return MaceAction.DODGE_FALLING_BLOCK_AWAY;
            case SELF_SMASH:
                return MaceAction.RECOVER_LAND;
            case VOID:
                return MaceAction.CLUTCH_VOID;
            case LAVA:
                return MaceAction.CLUTCH_LAVA;
            case CLAW:
                return MaceAction.DODGE_CLAW_CIRCLE;
            default:
                return MaceAction.DODGE;
        }
    }

    private boolean isIncomingProjectile() { /* check nearby projectiles */ return false; }
    private boolean isIncomingMace() { /* check nearby mace users */ return false; }
    private boolean isExplosionNearby() { /* check for TNT/crystals */ return false; }
    private boolean isSafeLanding() { /* check safe ground below */ return true; }
    private boolean hasItem(net.minecraft.world.item.Item item) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return true;
        }
        return false;
    }
    private boolean hasEnchantment(net.minecraft.world.item.Item item, net.minecraft.world.item.enchantment.Enchantment ench) {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.is(item)) return false;
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
            mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(ench).orElseThrow(), stack) > 0;
    }

    public void onSmash() { smashing = true; smashTicks = SMASH_DURATION; }
    public void onLand() { smashing = false; landed = true; }
    public void onWindBurst() { windBurstActive = true; windBurstTicks = WIND_BURST_DURATION; }
    public void onLaunch() { launching = true; launchCooldown = 30; }

    public int getSmashTicks() { return smashTicks; }
    public boolean isSmashing() { return smashing; }
    public boolean isWindBurstActive() { return windBurstActive; }
    public boolean isLaunching() { return launching; }

    /**
     * Mace action types — every possible mace decision.
     */
    public enum MaceAction {
        IDLE("idle", "Wait and observe"),
        APPROACH("approach", "Move to melee range"),
        LAUNCH("launch", "Self-launch with fire/wind charge"),
        LAUNCHING("launching", "Mid-air after launch"),
        SMASH("smash", "Smash attack during fall"),
        WIND_BURST("wind_burst", "Use Wind Burst enchantment mid-air"),
        WIND_BURST_CHARGE("wind_burst_charge", "Charge wind burst at feet"),
        LAND("land", "Land safely after smash"),
        RECOVER("recover", "Recover from failed smash"),
        RECOVER_LAND("recover_land", "Recover and land"),
        DODGE("dodge", "Generic dodge"),
        DODGE_PROJECTILE_PERP("dodge_projectile", "Dodge projectile perpendicular"),
        DODGE_MELEE_CIRCLE("dodge_melee", "Dodge melee in circle"),
        DODGE_EXPLOSION_AWAY("dodge_explosion", "Dodge explosion away"),
        DODGE_MACE_PERP("dodge_mace", "Dodge incoming mace perpendicular"),
        DODGE_WITCH_SHIELD("dodge_witch", "Dodge witch potion with shield"),
        DODGE_FALLING_BLOCK_AWAY("dodge_falling", "Dodge falling block away"),
        DODGE_CLAW_CIRCLE("dodge_claw", "Dodge claw attack in circle"),
        HEAL("heal", "Eat food to heal"),
        HEAL_RETREAT("heal_retreat", "Heal while retreating"),
        RETREAT("retreat", "Back away from combat"),
        CHASE("chase", "Chase target"),
        CLUTCH_VOID("clutch_void", "Emergency clutch from void"),
        CLUTCH_LAVA("clutch_lava", "Emergency clutch from lava"),
        FINISH("finish", "Deliver killing smash"),
        WAIT("wait", "Wait for cooldown"),
        INTERCEPT("intercept", "Cut off retreating target"),
        COVER("cover", "Use cover while waiting"),
        BAIT("bait", "Bait target into smash range");

        private final String name;
        private final String description;

        MaceAction(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }
}
