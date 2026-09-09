package baritone.aimassist.combat;

import baritone.api.aimassist.IAimTarget;
import baritone.aimassist.AimAssistConfig;
import baritone.aimassist.AimAssistMod;
import baritone.aimassist.util.KeyMovementController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DodgeSystem {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;
    private final OpponentScanner scanner = new OpponentScanner();

    private boolean active = false;
    private int dodgeTicks = 0;
    private Vec3 dodgeDirection = Vec3.ZERO;
    private boolean dodgeJump = false;
    private boolean dodgeSprint = false;
    private String lastManeuver = "none";

    private static final int MELEE_DURATION = 4;
    private static final int MACE_DURATION = 10;
    private static final int PROJ_DURATION = 6;
    private static final int EXPLOSION_DURATION = 8;
    private static final double MELEE_RANGE = 4.0;
    private static final double PROJ_RANGE = 28.0;
    private static final double MACE_RANGE = 20.0;
    private static final double WITCH_RANGE = 16.0;

    private int shieldBlockCooldown = 0;
    private int witchAlertTicks = 0;

    public DodgeSystem(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        // UNKILLABLE MODE: Always run defensive predictive layers regardless of active state
        // Predict BEFORE action, react on action start, react on threat — all 3 layers always running
        if (mc.player == null || mc.level == null) return;

        if (shieldBlockCooldown > 0) shieldBlockCooldown--;
        if (witchAlertTicks > 0) witchAlertTicks--;

        // Shield blocking for witch potions
        if (witchAlertTicks > 0 && hasShieldInHotbar()) {
            raiseShield();
        }

        if (dodgeTicks > 0) {
            KeyMovementController ctrl = getController();
            if (ctrl != null && dodgeDirection.lengthSqr() > 0.01) {
                ctrl.moveToward(dodgeDirection, dodgeSprint, dodgeJump && mc.player.onGround(), false);
            }
            dodgeTicks--;
            return;
        }

        if (checkWitchPotion()) return;
        if (checkMeleeReactive()) return;
        if (checkVoid()) return;
        if (checkExplosion()) return;
        if (checkMace()) return;
        if (checkProjectile()) return;
        if (checkMeleePredictive()) return;
    }

    private boolean checkMeleeReactive() {
        if (mc.level == null || mc.player == null) return false;
        IAimTarget target = getPrimaryTarget();
        if (target == null || !(target.getEntity() instanceof LivingEntity living)) return false;
        double dist = mc.player.distanceTo(living);
        if (dist > 5) return false;

        scanner.scan(living);
        if (!scanner.isAttacking()) return false;

        Vec3 toward = new Vec3(living.getX() - mc.player.getX(), 0, living.getZ() - mc.player.getZ()).normalize();
        Vec3 perp = new Vec3(-toward.z, 0, toward.x);
        if (Math.random() < 0.5) perp = perp.scale(-1);
        Vec3 dir = toward.add(perp.scale(0.7)).normalize();
        triggerDodge(dir, true, true, MELEE_DURATION, "reactive-melee");
        return true;
    }

    private boolean checkVoid() {
        if (mc.player.blockPosition().getY() <= mc.level.getMinY() + 2) {
            BlockPos center = mc.player.blockPosition();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos bp = center.offset(dx, 0, dz);
                    if (mc.level.getBlockState(bp).isSolid() || mc.level.getBlockState(bp.below()).isSolid()) {
                        Vec3 dir = new Vec3(bp.getX() - center.getX(), 0, bp.getZ() - center.getZ());
                        if (dir.lengthSqr() > 0.01) { triggerDodge(dir.normalize(), true, true, 8, "void"); return true; }
                    }
                }
            }
            triggerDodge(new Vec3(0, 0, 1), true, true, 8, "void-fwd");
            return true;
        }
        return false;
    }

    private boolean checkExplosion() {
        AABB box = getSearchBox(13);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof PrimedTnt tnt && e.isAlive() && mc.player.distanceTo(tnt) < 13 && tnt.getFuse() < 10) {
                triggerDodge(awayFrom(e.position()), true, true, EXPLOSION_DURATION, "tnt");
                return true;
            }
            if (e instanceof EndCrystal c && e.isAlive() && mc.player.distanceTo(c) < 7) {
                triggerDodge(awayFrom(c.position()), mc.player.onGround(), true, EXPLOSION_DURATION, "crystal");
                return true;
            }
        }
        return false;
    }

    private boolean checkMace() {
        AABB box = getSearchBox(MACE_RANGE);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof Player p && p != mc.player && p.isAlive()) {
                boolean falling = !p.onGround() && p.getDeltaMovement().y < -0.15;
                boolean holdingMace = p.getMainHandItem().is(Items.MACE);
                if (!falling || !holdingMace) continue;
                double dist = mc.player.distanceTo(p);
                if (dist > 12) continue;

                Vec3 vel = p.getDeltaMovement();
                double vy = vel.y;
                int ticksToLand = 0;
                for (int t = 1; t <= 20; t++) {
                    vy = (vy - 0.08) * 0.98;
                    if (p.getY() + vy <= p.getBlockY()) { ticksToLand = t; break; }
                }
                if (ticksToLand == 0 || ticksToLand > 8) continue;
                Vec3 landingPos = new Vec3(p.getX() + vel.x * ticksToLand, 0, p.getZ() + vel.z * ticksToLand);
                if (landingPos.distanceTo(new Vec3(mc.player.getX(), 0, mc.player.getZ())) < 5) {
                    Vec3 perp = new Vec3(-vel.z, 0, vel.x).normalize();
                    triggerDodge(perp, true, true, MACE_DURATION, "mace");
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkProjectile() {
        AABB box = getSearchBox(PROJ_RANGE);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive()) continue;
            Vec3 vel = e.getDeltaMovement();
            Vec3 pos = e.position();
            if (vel.lengthSqr() < 0.01) continue;

            if (e instanceof ThrownTrident && vel.length() > 0.3 && isAimedAtMe(pos, vel)) {
                triggerDodge(perpToTrajectory(pos, vel), true, true, PROJ_DURATION, "trident");
                return true;
            }
            if (e instanceof WitherSkull || e instanceof Fireball || e instanceof DragonFireball) {
                Vec3 dir = vel.lengthSqr() > 0.01 ? vel : e.getLookAngle().scale(2);
                if (isAimedAtMe(pos, dir)) {
                    triggerDodge(perpToTrajectory(pos, dir), true, true, PROJ_DURATION, "fireball");
                    return true;
                }
            }
            if (e instanceof ShulkerBullet) {
                Vec3 dir = mc.player.position().subtract(pos).normalize();
                if (isAimedAtMe(pos, dir)) {
                    triggerDodge(perpToTrajectory(pos, dir), true, true, PROJ_DURATION, "shulker");
                    return true;
                }
            }
            if (e instanceof Projectile proj && !(e instanceof AbstractThrownPotion) && vel.length() > 0.2 && isAimedAtMe(pos, vel)) {
                triggerDodge(perpToTrajectory(pos, vel), true, true, PROJ_DURATION, "projectile");
                return true;
            }
            if (e instanceof FallingBlockEntity && mc.player.distanceTo(e) < 4) {
                triggerDodge(awayFrom(pos), mc.player.onGround(), true, PROJ_DURATION, "falling-block");
                return true;
            }
        }
        return false;
    }

    private boolean checkMeleePredictive() {
        AABB box = getSearchBox(MELEE_RANGE);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive() || e == mc.player) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > MELEE_RANGE) continue;

            if (e instanceof Player p && p != mc.player) {
                boolean hasWeapon = isWeapon(p.getMainHandItem());
                boolean facingMe = p.getLookAngle().dot(
                    mc.player.position().subtract(p.getEyePosition()).normalize()) > 0.5;
                // Predictive layer: detect opponent action before it starts
                if (dist < 3.5 && facingMe && hasWeapon && !p.isUsingItem()) {
                    Vec3 toward = new Vec3(p.getX() - mc.player.getX(), 0, p.getZ() - mc.player.getZ()).normalize();
                    Vec3 perp = new Vec3(-toward.z, 0, toward.x);
                    triggerDodge(toward.add(perp.scale(0.7)).normalize(), true, true, MELEE_DURATION, "predict-melee");
                    return true;
                }
            }
            if (e instanceof Monster m && m.getTarget() == mc.player && dist < 3) {
                triggerDodge(aggressiveStrafe(m.position()), true, true, MELEE_DURATION, "predict-monster");
                return true;
            }
        }
        return false;
    }
        AABB box = getSearchBox(MELEE_RANGE);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive() || e == mc.player) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > MELEE_RANGE) continue;

            if (e instanceof Player p && p != mc.player) {
                boolean hasWeapon = isWeapon(p.getMainHandItem());
                boolean facingMe = p.getLookAngle().dot(
                    mc.player.position().subtract(p.getEyePosition()).normalize()) > 0.5;
                if (dist < 3.5 && facingMe && hasWeapon && !p.isUsingItem()) {
                    Vec3 toward = new Vec3(p.getX() - mc.player.getX(), 0, p.getZ() - mc.player.getZ()).normalize();
                    Vec3 perp = new Vec3(-toward.z, 0, toward.x);
                    triggerDodge(toward.add(perp.scale(0.7)).normalize(), true, true, MELEE_DURATION, "predict-melee");
                    return true;
                }
            }
            if (e instanceof Monster m && m.getTarget() == mc.player && dist < 3) {
                triggerDodge(aggressiveStrafe(m.position()), true, true, MELEE_DURATION, "predict-monster");
                return true;
            }
        }
        return false;
    }

    private boolean checkWitchPotion() {
        if (mc.level == null) return false;
        AABB box = getSearchBox(WITCH_RANGE);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive()) continue;
            Vec3 vel = e.getDeltaMovement();
            Vec3 pos = e.position();

            // Detect splash potions thrown at player (witch's attack)
            if (e instanceof AbstractThrownPotion potion && potion.isAlive()) {
                if (isAimedAtMe(pos, vel)) {
                    // Raise shield to block potion (100% block in Java 1.16+)
                    if (hasShieldInHotbar()) {
                        raiseShield();
                        shieldBlockCooldown = 5;
                    }
                    // Aggressive dodge toward witch while blocking
                    IAimTarget target = getPrimaryTarget();
                    if (target != null && target.getEntity() != null) {
                        Vec3 toward = new Vec3(target.getEntity().getX() - mc.player.getX(),
                            0, target.getEntity().getZ() - mc.player.getZ());
                        if (toward.lengthSqr() > 0.01) {
                            triggerDodge(toward.normalize(), true, true, 4, "witch-block");
                            return true;
                        }
                    }
                }
                break;
            }

            // Detect witch entity within range - alert on aggressive approach
            if (e.getType() == net.minecraft.world.entity.EntityType.WITCH && e.isAlive()) {
                double dist = mc.player.distanceTo(e);
                if (dist < WITCH_RANGE) {
                    witchAlertTicks = 40; // alert for 2 seconds
                    if (hasShieldInHotbar() && mc.player.isBlocking()) {
                        // Advance on witch while shielding
                        Vec3 toward = new Vec3(e.getX() - mc.player.getX(), 0, e.getZ() - mc.player.getZ());
                        if (toward.lengthSqr() > 0.01) {
                            Vec3 dir = toward.normalize();
                            KeyMovementController ctrl = getController();
                            if (ctrl != null) {
                                ctrl.moveToward(dir, true, true, false);
                                dodgeTicks = 4;
                                dodgeJump = true;
                                dodgeSprint = true;
                                lastManeuver = "witch-advance";
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasShieldInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.SHIELD)) return true;
        }
        return false;
    }

    private void raiseShield() {
        if (!mc.player.isBlocking()) {
            // Find shield in hotbar and select it
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).is(Items.SHIELD)) {
                    mc.player.getInventory().setSelectedSlot(i);
                    break;
                }
            }
            mc.options.keyUse.setDown(true);
        }
    }

    private void triggerDodge(Vec3 direction, boolean jump, boolean sprint, int duration, String maneuver) {
        KeyMovementController ctrl = getController();
        if (ctrl == null) return;
        dodgeDirection = direction;
        dodgeJump = jump;
        dodgeSprint = sprint;
        dodgeTicks = duration;
        lastManeuver = maneuver;
        if (direction.lengthSqr() > 0.01) {
            ctrl.moveToward(direction, sprint, jump && mc.player.onGround(), false);
        }
    }

    private AABB getSearchBox(double range) {
        Vec3 p = mc.player.position();
        return new AABB(p.x - range, p.y - 20, p.z - range, p.x + range, p.y + 20, p.z + range);
    }

    private Vec3 awayFrom(Vec3 threat) {
        Vec3 diff = mc.player.position().subtract(threat);
        diff = new Vec3(diff.x, 0, diff.z);
        return diff.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : diff.normalize();
    }

    private Vec3 aggressiveStrafe(Vec3 threatPos) {
        Vec3 toward = new Vec3(threatPos.x - mc.player.getX(), 0, threatPos.z - mc.player.getZ());
        if (toward.lengthSqr() < 0.01) return new Vec3(1, 0, 0);
        toward = toward.normalize();
        Vec3 perp = new Vec3(-toward.z, 0, toward.x);
        return toward.add(perp.scale(0.7)).normalize();
    }

    private Vec3 perpToTrajectory(Vec3 origin, Vec3 vel) {
        Vec3 traj = new Vec3(vel.x, 0, vel.z).normalize();
        Vec3 perp = new Vec3(-traj.z, 0, traj.x);
        Vec3 toMe = new Vec3(mc.player.getX() - origin.x, 0, mc.player.getZ() - origin.z);
        return toMe.dot(perp) >= 0 ? perp : perp.scale(-1);
    }

    private boolean isAimedAtMe(Vec3 origin, Vec3 vel) {
        Vec3 traj = new Vec3(vel.x, 0, vel.z).normalize();
        Vec3 toMe = new Vec3(mc.player.getX() - origin.x, 0, mc.player.getZ() - origin.z);
        double dist = toMe.length();
        if (dist > PROJ_RANGE || dist < 0.5) return false;
        Vec3 closest = origin.add(traj.scale(Math.max(0, toMe.dot(traj))));
        double missDist = new Vec3(mc.player.getX() - closest.x, 0, mc.player.getZ() - closest.z).length();
        return missDist < 2.5 && toMe.normalize().dot(traj) > 0.6;
    }

    private boolean isWeapon(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.is(Items.WOODEN_SWORD) || s.is(Items.STONE_SWORD) || s.is(Items.IRON_SWORD)
            || s.is(Items.GOLDEN_SWORD) || s.is(Items.DIAMOND_SWORD) || s.is(Items.NETHERITE_SWORD)
            || s.is(Items.WOODEN_AXE) || s.is(Items.STONE_AXE) || s.is(Items.IRON_AXE)
            || s.is(Items.GOLDEN_AXE) || s.is(Items.DIAMOND_AXE) || s.is(Items.NETHERITE_AXE)
            || s.is(Items.MACE) || s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.is(Items.TRIDENT);
    }

    private IAimTarget getPrimaryTarget() {
        AimAssistMod mod = AimAssistMod.getInstance();
        return mod != null ? mod.getModule().getCurrentTarget() : null;
    }

    private KeyMovementController getController() {
        AimAssistMod mod = AimAssistMod.getInstance();
        return mod != null ? mod.getMovementController() : null;
    }

    public boolean hasActiveDodge() { return dodgeTicks > 0; }
    public boolean isDodgeActive() { return dodgeTicks > 0; }

    public void setActive(boolean a) {
        this.active = a;
        if (!a) dodgeTicks = 0;
    }
    public boolean isActive() { return active; }
    public String getLastManeuver() { return lastManeuver; }
}
