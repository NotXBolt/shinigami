package baritone.aimassist.combat;

import baritone.api.aimassist.IAimTarget;
import baritone.aimassist.AimAssistConfig;
import baritone.aimassist.AimAssistMod;
import baritone.aimassist.learning.ReinforcementLearner;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

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
    private float lastHealth = -1;
    private boolean rewardedDodgeEnd = false;

    private int dodgeSuccessTicks = 0;

    private static final int MELEE_DURATION = 4;
    private static final int MACE_DURATION = 10;
    private static final int PROJ_DURATION = 6;
    private static final int EXPLOSION_DURATION = 8;
    private static final double MELEE_RANGE = 4.0;
    private static final double PROJ_RANGE = 28.0;
    private static final double MACE_RANGE = 20.0;
    private static final double WITCH_RANGE = 16.0;
    private static final double THREAT_AGGREGATE_RADIUS = 10.0;
    private static final double SAFE_CHECK_HORIZONTAL = 2.0;

    private int shieldBlockCooldown = 0;
    private int witchAlertTicks = 0;
    private int imminentThreatCount = 0;

    public DodgeSystem(AimAssistConfig config) {
        this.config = config;
    }

    public ReinforcementLearner getLearner() {
        if (!config.isRlLearning()) return null;
        AimAssistMod mod = AimAssistMod.getInstance();
        return mod != null && mod.getModule() != null ? mod.getModule().getReinforcementLearner() : null;
    }

    public void tick() {
        if (!active || mc.player == null || mc.level == null) return;

        if (shieldBlockCooldown > 0) shieldBlockCooldown--;
        if (witchAlertTicks > 0) witchAlertTicks--;

        // Track health to reward damage taken vs dodged
        float hp = mc.player.getHealth();
        if (lastHealth < 0) lastHealth = hp;
        if (hp < lastHealth - 0.01f) {
            ReinforcementLearner rl = getLearner();
            if (rl != null) rl.reward(ReinforcementLearner.Space.DODGE, -(lastHealth - hp) * 3.0);
            lastHealth = hp;
        } else if (hp > lastHealth) {
            lastHealth = hp;
        }

        // Shield blocking for witch potions
        if (witchAlertTicks > 0 && hasShieldInHotbar()) {
            raiseShield();
        }

        if (dodgeTicks > 0) {
            // Dodge in progress → reward survival each tick
            dodgeSuccessTicks++;
            ReinforcementLearner rl = getLearner();
            if (rl != null && dodgeSuccessTicks % 4 == 0) rl.reward(ReinforcementLearner.Space.DODGE, 0.15);

            KeyMovementController ctrl = getController();
            if (ctrl != null && dodgeDirection.lengthSqr() > 0.01) {
                // Re-validate the dodge destination each tick so we never
                // drift into a newly-appeared enemy or hazard.
                // Prioritize RL-learned direction when confident.
                ReinforcementLearner rl2 = getLearner();
                if (rl2 != null && rl2.getTotalSteps() > 200
                    && rl2.bestActionValue(ReinforcementLearner.Space.DODGE) > 0.5) {
                    // Use RL-learned dodge direction directly.
                    Vec3 rlDir = Vec3.ZERO;
                    int rlAction = rl2.choose(ReinforcementLearner.Space.DODGE);
                    if (rlAction == ReinforcementLearner.ACT_PERPL) rlDir = perpL;
                    else if (rlAction == ReinforcementLearner.ACT_PERPR) rlDir = perpR;
                    else if (rlAction == ReinforcementLearner.ACT_CIRCLE_L) rlDir = new Vec3(-dodgeDirection.z, 0, dodgeDirection.x);
                    else if (rlAction == ReinforcementLearner.ACT_CIRCLE_R) rlDir = new Vec3(dodgeDirection.z, 0, -dodgeDirection.x);
                    else if (rlAction == ReinforcementLearner.ACT_AWAY) rlDir = dodgeDirection.scale(-1);
                    if (rlDir.lengthSqr() > 0.01) {
                        Vec3 safePos = safeLandingPosition(mc.player.position().add(rlDir.scale(2.5)));
                        if (safePos != null) {
                            ctrl.moveToward(rlDir, dodgeSprint, dodgeJump && mc.player.onGround(), false);
                            dodgeDirection = rlDir;
                            return;
                        }
                    }
                }
                if (safePos == null) {
                    List<Entity> threats = aggregateThreats();
                    Vec3 fallback = pickSafeDirection(threats, null);
                    if (fallback != null) dodgeDirection = fallback;
                }
                ctrl.moveToward(dodgeDirection, dodgeSprint, dodgeJump && mc.player.onGround(), false);
            }
            dodgeTicks--;
            if (dodgeTicks == 0) {
                if (!rewardedDodgeEnd) {
                    ReinforcementLearner rl2 = getLearner();
                    if (rl2 != null && dodgeSuccessTicks >= 4) rl2.reward(ReinforcementLearner.Space.DODGE, 2.0);
                    rewardedDodgeEnd = true;
                }
            }
            return;
        } else {
            dodgeSuccessTicks = 0;
            rewardedDodgeEnd = false;
        }

        if (checkWitchPotion()) return;
        if (checkVoid()) return;
        if (checkExplosion()) return;
        if (checkMace()) return;
        if (checkProjectile()) return;
        if (checkMeleeReactive()) return;
        if (checkMeleePredictive()) return;
    }

    // ─── Multi-threat aggregation (Dodger + our own vector math) ──────

    private List<Entity> aggregateThreats() {
        List<Entity> threats = new ArrayList<>();
        AABB box = getSearchBox(THREAT_AGGREGATE_RADIUS);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive() || e == mc.player) continue;
            double dist = mc.player.distanceTo(e);
            boolean relevant = false;
            if (e instanceof Monster m) relevant = dist < THREAT_AGGREGATE_RADIUS && m.getTarget() == mc.player;
            else if (e instanceof Player p) relevant = dist < 6 && isWeapon(p.getMainHandItem()) && !p.isUsingItem();
            else if (e instanceof PrimedTnt || e instanceof EndCrystal
                || e instanceof WitherSkull || e instanceof Fireball || e instanceof DragonFireball
                || e instanceof ShulkerBullet || e instanceof ThrownTrident
                || e instanceof Projectile || e instanceof FallingBlockEntity) {
                relevant = dist < PROJ_RANGE;
            }
            if (relevant) threats.add(e);
        }
        imminentThreatCount = threats.size();
        return threats;
    }

    /**
     * Pick a dodging direction that (1) never runs into an enemy,
     * (2) lands on safe ground (Dodger isSafeDodgePosition port),
     * (3) prefers RL-learned direction when RL is confident.
     */
    private Vec3 pickSafeDirection(List<Entity> threats, Vec3 primaryThreatPos) {
        Vec3 playerPos = mc.player.position();

        // Combined repulsion: away from every threat, weighted by proximity.
        Vec3 repulsion = Vec3.ZERO;
        int n = 0;
        for (Entity t : threats) {
            Vec3 threatPos = t == null ? null : t.position();
            if (threatPos == null) continue;
            Vec3 offset = playerPos.subtract(threatPos);
            double dist = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (dist < 0.3) continue;
            double w = (THREAT_AGGREGATE_RADIUS - Math.min(dist, THREAT_AGGREGATE_RADIUS)) / THREAT_AGGREGATE_RADIUS;
            repulsion = repulsion.add(new Vec3(offset.x / dist * w, 0, offset.z / dist * w));
            n++;
        }
        if (n > 0) {
            double len = repulsion.length();
            if (len > 0.01) repulsion = repulsion.scale(1.0 / len);
        }

        // Candidate directions. RL picks among them when it has signal.
        Vec3 towardThreat = primaryThreatPos != null
            ? new Vec3(primaryThreatPos.x - playerPos.x, 0, primaryThreatPos.z - playerPos.z)
            : null;
        if (towardThreat != null && towardThreat.lengthSqr() > 0.01) towardThreat = towardThreat.normalize();
        Vec3 perpL = towardThreat != null ? new Vec3(-towardThreat.z, 0, towardThreat.x) : new Vec3(1, 0, 0);
        Vec3 perpR = perpL.scale(-1);

        List<Vec3> candidates = new ArrayList<>();
        if (repulsion.lengthSqr() > 0.01) candidates.add(repulsion.normalize());
        candidates.add(perpL);
        candidates.add(perpR);
        // Angled away (away + perp) to keep ground & avoid backpedal
        if (repulsion.lengthSqr() > 0.01) {
            candidates.add(repulsion.add(perpL.scale(0.6)).normalize());
            candidates.add(repulsion.add(perpR.scale(0.6)).normalize());
        }

        // RL direction bias when learner has seen this situation.
        ReinforcementLearner rl = getLearner();
        int rlAction = -1;
        if (rl != null && rl.getTotalSteps() > 200) {
            rlAction = rl.choose(ReinforcementLearner.Space.DODGE);
            if (rlAction == ReinforcementLearner.ACT_AWAY && repulsion.lengthSqr() > 0.01) {
                rlAction = -1; // away handled by repulsion candidate
            }
            if (rlAction == ReinforcementLearner.ACT_CIRCLE_L) candidates.add(0, perpL);
            if (rlAction == ReinforcementLearner.ACT_CIRCLE_R) candidates.add(0, perpR);
            if (rlAction == ReinforcementLearner.ACT_PERPL) candidates.add(perpL);
            if (rlAction == ReinforcementLearner.ACT_PERPR) candidates.add(perpR);
        }

        // Score each candidate: must be safe (ground, no hazard), away from enemies.
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Vec3 c : candidates) {
            Vec3 dest = playerPos.add(c.scale(SAFE_CHECK_HORIZONTAL));
            if (!isSafeDodgePosition(dest)) continue;

            double score = 0;
            // Prefer running away from the aggregated threat mass.
            if (repulsion.lengthSqr() > 0.01) {
                score += c.dot(repulsion) * 2.0;
            }
            // Penalise moving toward any specific enemy.
            for (Entity t : threats) {
                Vec3 tpos = t.position();
                double distToEnemy = Math.sqrt(
                    (dest.x - tpos.x) * (dest.x - tpos.x) + (dest.z - tpos.z) * (dest.z - tpos.z));
                if (distToEnemy < 2.5) score -= 4.0;
                else if (distToEnemy < 3.5) score -= 1.5;
            }
            // Mild preference for keeping the primary target (melee enemy) in front to face it.
            if (primaryThreatPos != null && towardThreat != null) {
                score -= c.dot(towardThreat) * 0.4;
            }
            // Small pseudorandom tiebreak so we don't lock onto one direction forever.
            score += rndSmall();
            if (score > bestScore) { bestScore = score; best = c; }
        }

        return best != null ? best : repulsion.lengthSqr() > 0.01 ? repulsion.normalize() : perpL;
    }

    private double rndSmall() { return (Math.random() - 0.5) * 0.15; }

    /**
     * Dodger-inspired safety test for a horizontal position.
     * True when: solid/liquid ground below, no hazard blocks at feet/head,
     * not in the void.
     */
    public boolean isSafeDodgePosition(Vec3 pos) {
        if (mc.level == null) return false;
        if (pos.y < mc.level.getMinY() + 1) return false;

        BlockPos at = BlockPos.containing(pos);
        for (int dy = 0; dy < 2; dy++) {
            BlockPos bp = at.offset(0, dy, 0);
            var state = mc.level.getBlockState(bp);
            if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.COBWEB) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.BLACKSTONE)) return false;
        }
        // Ground below must be solid or liquid (lake/void catch).
        BlockPos below = at.below();
        var ground = mc.level.getBlockState(below);
        boolean safeGround = ground.isSolid() || ground.is(Blocks.WATER) || ground.is(Blocks.LAVA)
            || ground.is(Blocks.HONEY_BLOCK) || ground.is(Blocks.SLIME_BLOCK)
            || ground.is(Blocks.POWDER_SNOW);
        if (!safeGround) return false;

        // Avoid spots right next to a hazard column.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                var s = mc.level.getBlockState(at.offset(dx, 0, dz));
                if (s.is(Blocks.LAVA) || s.is(Blocks.CACTUS) || s.is(Blocks.FIRE)) return false;
            }
        }
        return true;
    }

    /** Landing spot snapped to the walkable surface under the given position. */
    private Vec3 safeLandingPosition(Vec3 candidate) {
        if (mc.level == null) return null;
        BlockPos at = BlockPos.containing(candidate);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos bp = at.offset(0, dy, 0);
            var below = mc.level.getBlockState(bp.below());
            if (below.isSolid() || below.is(Blocks.WATER) || below.is(Blocks.LAVA)) {
                Vec3 pos = new Vec3(candidate.x, bp.getY() + 0.5, candidate.z);
                if (isSafeDodgePosition(pos)) return pos;
                return null;
            }
        }
        return null;
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

        List<Entity> threats = aggregateThreats();
        Vec3 dir = pickSafeDirection(threats, living.position());
        triggerDodge(dir, true, true, MELEE_DURATION, "reactive-melee");
        return true;
    }

    private boolean checkVoid() {
        if (mc.player.blockPosition().getY() <= mc.level.getMinY() + 2) {
            BlockPos center = mc.player.blockPosition();
            List<Vec3> dirs = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos bp = center.offset(dx, 0, dz);
                    if (mc.level.getBlockState(bp).isSolid() || mc.level.getBlockState(bp.below()).isSolid()) {
                        Vec3 dir = new Vec3(bp.getX() - center.getX(), 0, bp.getZ() - center.getZ());
                        if (dir.lengthSqr() > 0.01) dirs.add(dir.normalize());
                    }
                }
            }
            Vec3 best = null;
            for (Vec3 d : dirs) {
                Vec3 dest = mc.player.position().add(d.scale(2));
                if (isSafeDodgePosition(dest)) { best = d; break; }
            }
            triggerDodge(best != null ? best : new Vec3(0, 0, 1), true, true, 8, "void");
            return true;
        }
        return false;
    }

    /**
 * checkExplosion — TNT + EndCrystal explosion within blast radius.
 * Two threat types:
 *   1) Primed TNT with short fuse (<10 ticks): move away 3 blocks (awayFrom)
      + verify safe ground. Reward: damage taken → reward(DODGE, -dmg*3).
 *   2) EndCrystal within 7 blocks: move away 3 blocks + verify safe ground.
 * Never stand near exploding TNT or crystals — always dodge away with safe landing.
 * If safe ground not available at 3 blocks, try perp-direction dodge.
 */
    private boolean checkExplosion() {
        AABB box = getSearchBox(13);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (e instanceof PrimedTnt tnt && e.isAlive() && mc.player.distanceTo(tnt) < 13 && tnt.getFuse() < 10) {
                Vec3 away = awayFrom(e.position());
                if (isSafeDodgePosition(mc.player.position().add(away.scale(3)))) {
                    triggerDodge(away, true, true, EXPLOSION_DURATION, "tnt");
                } else {
                    // Perpendicular fallback if direct away is unsafe.
                    triggerDodge(perpToThreat(e.position()), true, true, EXPLOSION_DURATION, "tnt-perp");
                }
                return true;
            }
            if (e instanceof EndCrystal c && e.isAlive() && mc.player.distanceTo(c) < 7) {
                Vec3 away = awayFrom(c.position());
                if (isSafeDodgePosition(mc.player.position().add(away.scale(3)))) {
                    triggerDodge(away, mc.player.onGround(), true, EXPLOSION_DURATION, "crystal");
                } else {
                    triggerDodge(perpToThreat(c.position()), mc.player.onGround(), true, EXPLOSION_DURATION, "crystal-perp");
                }
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
                    if (isSafeDodgePosition(mc.player.position().add(perp.scale(3)))) {
                        triggerDodge(perp, true, true, MACE_DURATION, "mace");
                    } else {
                        triggerDodge(perp.scale(-1), true, true, MACE_DURATION, "mace-perp");
                    }
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
                Vec3 dir = perpToTrajectory(pos, vel);
                triggerDodge(ensureSafeDir(dir, pos), true, true, PROJ_DURATION, "trident");
                return true;
            }
            if (e instanceof WitherSkull || e instanceof Fireball || e instanceof DragonFireball) {
                Vec3 dir = vel.lengthSqr() > 0.01 ? vel : e.getLookAngle().scale(2);
                if (isAimedAtMe(pos, dir)) {
                    triggerDodge(ensureSafeDir(perpToTrajectory(pos, dir), pos), true, true, PROJ_DURATION, "fireball");
                    return true;
                }
            }
            if (e instanceof ShulkerBullet) {
                Vec3 dir = mc.player.position().subtract(pos).normalize();
                if (isAimedAtMe(pos, dir)) {
                    triggerDodge(ensureSafeDir(perpToTrajectory(pos, dir), pos), true, true, PROJ_DURATION, "shulker");
                    return true;
                }
            }
            if (e instanceof Projectile proj && !(e instanceof AbstractThrownPotion) && vel.length() > 0.2 && isAimedAtMe(pos, vel)) {
                triggerDodge(ensureSafeDir(perpToTrajectory(pos, vel), pos), true, true, PROJ_DURATION, "projectile");
                return true;
            }
            if (e instanceof FallingBlockEntity && mc.player.distanceTo(e) < 4) {
                Vec3 away = awayFrom(pos);
                if (!isSafeDodgePosition(mc.player.position().add(away.scale(3)))) away = perpToThreat(pos);
                triggerDodge(away, mc.player.onGround(), true, PROJ_DURATION, "falling-block");
                return true;
            }
        }
        return false;
    }

    private Vec3 ensureSafeDir(Vec3 dir, Vec3 threatPos) {
        Vec3 dest = mc.player.position().add(dir.scale(3));
        if (isSafeDodgePosition(dest)) return dir;
        Vec3 alt = dir.scale(-1);
        if (isSafeDodgePosition(mc.player.position().add(alt.scale(3)))) return alt;
        List<Entity> threats = aggregateThreats();
        Vec3 fallback = pickSafeDirection(threats, threatPos);
        return fallback != null ? fallback : dir;
    }

    private boolean checkMeleePredictive() {
        AABB box = getSearchBox(MELEE_RANGE);
        List<Entity> threats = aggregateThreats();
        boolean triggered = false;
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive() || e == mc.player) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > MELEE_RANGE) continue;

            // --- Player with weapon facing us ---
            if (e instanceof Player p && p != mc.player) {
                boolean hasWeapon = isWeapon(p.getMainHandItem());
                Vec3 toEye = mc.player.position().subtract(p.getEyePosition()).normalize();
                boolean facingMe = p.getLookAngle().dot(toEye) > 0.5;
                if (dist < 3.5 && facingMe && hasWeapon && !p.isUsingItem()) {
                    Vec3 dir = pickSafeDirection(threats, p.position());
                    triggerDodge(dir, true, true, MELEE_DURATION, "predict-melee");
                    triggered = true;
                }
            }

            // --- Monster targeting us ---
            if (e instanceof Monster m && m.getTarget() == mc.player && dist < 3) {
                Vec3 dir = pickSafeDirection(threats, m.position());
                triggerDodge(dir, true, true, MELEE_DURATION, "predict-monster");
                triggered = true;
            }
        }
        return triggered;
    }

    /**
 * checkWitchPotion — Witch danger within ~16 blocks.
 * Two sub-situations:
 *   1) Thrown potion aimed at us: if shielded → raise shield + block; if not shielded → dodge perpendicular away.
 *   2) Witch entity nearby: set alert ticks 40; if shielded and blocking → face the witch and move toward it (advance), otherwise dodge perpendicular.
 * Reward: damage taken from potion → reward(DODGE, -dmg*3); dodge survival +0.15/tick.
 * Important: never stand still blocking witch potions without shield — always dodge perpendicular.
 */
    private boolean checkWitchPotion() {
        if (mc.level == null) return false;
        AABB box = getSearchBox(WITCH_RANGE);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive()) continue;
            Vec3 vel = e.getDeltaMovement();
            Vec3 pos = e.position();

            // --- Thrown potion aimed at us ---
            if (e instanceof AbstractThrownPotion potion && potion.isAlive()) {
                if (isAimedAtMe(pos, vel)) {
                    if (hasShieldInHotbar()) {
                        raiseShield();
                        shieldBlockCooldown = 5;
                    }
                    IAimTarget target = getPrimaryTarget();
                    if (target != null && target.getEntity() != null) {
                        Vec3 toward = new Vec3(target.getEntity().getX() - mc.player.getX(),
                            0, target.getEntity().getZ() - mc.player.getZ());
                        if (toward.lengthSqr() > 0.01 && isSafeDodgePosition(
                            mc.player.position().add(toward.normalize().scale(3)))) {
                            // Shield + safe to face: move toward witch to pressure.
                            triggerDodge(toward.normalize(), true, true, 4, "witch-block");
                        } else {
                            // No safe face → dodge perpendicular away.
                            triggerDodge(perpToThreat(e.position()), true, true, 4, "witch-perp");
                        }
                        return true;
                    }
                }
                break;
            }

            // --- Witch entity nearby (not a thrown potion) ---
            if (e.getType() == net.minecraft.world.entity.EntityType.WITCH && e.isAlive()) {
                double dist = mc.player.distanceTo(e);
                if (dist < WITCH_RANGE) {
                    witchAlertTicks = 40;
                    if (hasShieldInHotbar() && mc.player.isBlocking()) {
                        // Shield up + blocking: advance toward witch to pressure.
                        Vec3 toward = new Vec3(e.getX() - mc.player.getX(), 0, e.getZ() - mc.player.getZ());
                        if (toward.lengthSqr() > 0.01 && isSafeDodgePosition(
                            mc.player.position().add(toward.normalize().scale(3)))) {
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
                    } else {
                        // No shield → dodge perpendicular away from witch.
                        double d = mc.player.distanceTo(e);
                        if (d < WITCH_RANGE) {
                            // Pick safe perpendicular direction.
                            List<Entity> threats = aggregateThreats();
                            Vec3 dir = pickSafeDirection(threats, e.position());
                            if (dir != null) {
                                triggerDodge(dir, true, true, 4, "witch-dodge");
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
        dodgeSuccessTicks = 0;
        rewardedDodgeEnd = false;
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

    private Vec3 perpToThreat(Vec3 threatPos) {
        Vec3 toward = new Vec3(threatPos.x - mc.player.getX(), 0, threatPos.z - mc.player.getZ());
        if (toward.lengthSqr() < 0.01) return new Vec3(1, 0, 0);
        Vec3 perp = new Vec3(-toward.z, 0, toward.x);
        if (!isSafeDodgePosition(mc.player.position().add(perp.scale(3)))) perp = perp.scale(-1);
        return perp.normalize();
    }

    private Vec3 perpToTrajectory(Vec3 origin, Vec3 vel) {
        Vec3 traj = new Vec3(vel.x, 0, vel.z).normalize();
        Vec3 perp = new Vec3(-traj.z, 0, traj.x);
        Vec3 toMe = new Vec3(mc.player.getX() - origin.x, 0, mc.player.getZ() - origin.z);
        Vec3 chosen = toMe.dot(perp) >= 0 ? perp : perp.scale(-1);
        if (!isSafeDodgePosition(mc.player.position().add(chosen.scale(3)))) chosen = chosen.scale(-1);
        return chosen;
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
    public int getImminentThreatCount() { return imminentThreatCount; }
}