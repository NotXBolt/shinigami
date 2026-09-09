package shinigami.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;
import shinigami.prediction.BehavioralPredictor;

/**
 * DodgeSystem — Original, 3-layer predictive, safe perpendicular, never retreat.
 */
public class DodgeSystem {
    private final Minecraft mc = Minecraft.getInstance();
    private final BehavioralPredictor predictor;
    private int dodgeTicks = 0;
    private Vec3 dodgeDir = Vec3.ZERO;

    public DodgeSystem(BehavioralPredictor p) { this.predictor = p; }

    public void tick(MovementArbiter arbiter, LivingEntity target) {
        if (mc.player == null || mc.level == null) return;
        if (dodgeTicks > 0) { dodgeTicks--; return; }
        // Layer 1: predict before action
        if (target != null && predictor.predictBefore(target, 5)) {
            Vec3 dir = perp(target);
            if (isSafe(dir)) { arbiter.submit(new MovementIntent(MovementIntent.Priority.DODGE, dir, true, MovementIntent.JumpType.DODGE_JUMP, false, 5, "predict")); dodgeTicks = 5; return; }
        }
        // Layer 2: react on action start (swing)
        if (target != null && target instanceof Player pl && pl.swingTime > 0 && mc.player.distanceTo(target) < 5) {
            Vec3 dir = perp(target);
            arbiter.submit(new MovementIntent(MovementIntent.Priority.DODGE, dir, true, MovementIntent.JumpType.DODGE_JUMP, false, 4, "react")); dodgeTicks = 4; return;
        }
        // Layer 3: projectile
        checkProjectile(arbiter);
        // Void / clutch handled by ClutchSystem, but also dodge void
        if (mc.player.blockPosition().getY() <= mc.level.getMinY() + 2) {
            Vec3 safe = findSafeGround();
            arbiter.submit(new MovementIntent(MovementIntent.Priority.DODGE, safe, true, MovementIntent.JumpType.GAP_JUMP, false, 8, "void"));
        }
    }

    private Vec3 perp(LivingEntity t) {
        Vec3 diff = t.position().subtract(mc.player.position());
        Vec3 d = new Vec3(diff.x, 0, diff.z).normalize();
        Vec3 perp = new Vec3(-d.z, 0, d.x);
        // Aggressive circle: toward + perp*0.7
        Vec3 dir = d.add(perp.scale(0.7)).normalize();
        if (!isSafe(dir)) {
            Vec3 alt = d.add(perp.scale(-0.7)).normalize();
            if (isSafe(alt)) return alt;
        }
        return dir;
    }

    private boolean isSafe(Vec3 dir) {
        BlockPos dest = mc.player.blockPosition().offset((int)Math.signum(dir.x), 0, (int)Math.signum(dir.z));
        var s = mc.level.getBlockState(dest);
        if (s.is(Blocks.LAVA) || s.is(Blocks.FIRE) || s.is(Blocks.CACTUS)) return false;
        if (dest.getY() <= mc.level.getMinY() + 1) return false;
        return mc.level.getBlockState(dest.below()).isSolid();
    }

    private Vec3 findSafeGround() {
        BlockPos center = mc.player.blockPosition();
        for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
            BlockPos p = center.offset(dx, 0, dz);
            if (mc.level.getBlockState(p).isSolid() || mc.level.getBlockState(p.below()).isSolid()) {
                Vec3 dir = new Vec3(p.getX()-center.getX(), 0, p.getZ()-center.getZ());
                if (dir.lengthSqr() > 1e-6) return dir.normalize();
            }
        }
        return new Vec3(0,0,1);
    }

    private void checkProjectile(MovementArbiter arbiter) {
        AABB box = mc.player.getBoundingBox().inflate(28);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, box)) {
            if (!e.isAlive()) continue;
            Vec3 vel = e.getDeltaMovement();
            if (vel.lengthSqr() < 0.01) continue;
            Vec3 pos = e.position();
            if (e instanceof net.minecraft.world.entity.projectile.Projectile && vel.length() > 0.2) {
                if (isAimed(pos, vel)) {
                    Vec3 perp = perpToTraj(pos, vel);
                    arbiter.submit(new MovementIntent(MovementIntent.Priority.DODGE, perp, true, MovementIntent.JumpType.DODGE_JUMP, false, 6, "projectile"));
                    return;
                }
            }
        }
    }

    private Vec3 perpToTraj(Vec3 o, Vec3 vel) {
        Vec3 traj = new Vec3(vel.x, 0, vel.z).normalize();
        Vec3 perp = new Vec3(-traj.z, 0, traj.x);
        Vec3 toMe = new Vec3(mc.player.getX()-o.x, 0, mc.player.getZ()-o.z);
        return toMe.dot(perp) >= 0 ? perp : perp.scale(-1);
    }

    private boolean isAimed(Vec3 o, Vec3 vel) {
        Vec3 traj = new Vec3(vel.x, 0, vel.z).normalize();
        Vec3 toMe = new Vec3(mc.player.getX()-o.x, 0, mc.player.getZ()-o.z);
        if (toMe.length() > 28 || toMe.length() < 0.5) return false;
        Vec3 closest = o.add(traj.scale(Math.max(0, toMe.dot(traj))));
        double miss = new Vec3(mc.player.getX()-closest.x, 0, mc.player.getZ()-closest.z).length();
        return miss < 2.5 && toMe.normalize().dot(traj) > 0.6;
    }
}
