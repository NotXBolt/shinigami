package shinigami.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shinigami.config.ShinigamiConfig;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;
import shinigami.movement.ParkourEnforcer;
import shinigami.targeting.TargetManager;
import java.util.UUID;

/**
 * ChaseBehavior — Original, aggressive pakur, follow, direct yaw, no weave.
 */
public class ChaseBehavior {
    private final Minecraft mc = Minecraft.getInstance();
    private final TargetManager tm;
    private final ShinigamiConfig cfg = ShinigamiConfig.getInstance();
    private final ParkourEnforcer enforcer = new ParkourEnforcer();
    private String targetName = null;
    private UUID targetUUID = null;
    private boolean kill = false;

    public ChaseBehavior(TargetManager tm) { this.tm = tm; }

    public void tick(MovementArbiter arbiter) {
        LivingEntity target = tm.getPrimary();
        if (target == null || !target.isAlive()) return;
        Vec3 diff = target.position().subtract(mc.player.position());
        double dist = new Vec3(diff.x, 0, diff.z).length();
        if (dist < 3 && kill) return; // in range, let combat handle
        Vec3 dir = new Vec3(diff.x, 0, diff.z).normalize();
        // Face directly, no weave
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        mc.player.setYRot(yaw);
        // Aggressive parkour — super crazy original via ParkourEnforcer (enforced table + Theta* + block costs)
        boolean gap = detectGap();
        boolean edge = detectEdge();
        boolean blocked = isBlocked();
        boolean oneBlock = detectOneBlock();
        boolean above = target.getY() > mc.player.getY() + 1.5;
        boolean jump = enforcer.shouldJump(gap, edge, blocked, oneBlock, above);
        boolean sprint = enforcer.shouldSprint(dist);
        Vec3 finalDir = enforcer.calculateDir(new Vec3(diff.x, 0, diff.z));
        if (finalDir.lengthSqr() > 1e-6) dir = finalDir;
        arbiter.submit(new MovementIntent(MovementIntent.Priority.CHASE, dir, sprint, jump ? MovementIntent.JumpType.GAP_JUMP : MovementIntent.JumpType.NONE, false, 2, "chase"));
        if (blocked) breakBlock();
    }

    private boolean detectGap() {
        if (mc.player == null || mc.level == null) return false;
        Vec3 look = mc.player.getLookAngle();
        int dx = (int) Math.round(Math.signum(look.x));
        int dz = (int) Math.round(Math.signum(look.z));
        if (dx==0 && dz==0) return false;
        for (int i=1;i<=5;i++) {
            BlockPos below = mc.player.blockPosition().offset(dx*i, -1, dz*i);
            BlockPos at = mc.player.blockPosition().offset(dx*i, 0, dz*i);
            if (!mc.level.getBlockState(below).isSolid() && !mc.level.getBlockState(at).isSolid() && i>=2) return true;
            if (mc.level.getBlockState(at).isSolid()) return false;
        }
        return false;
    }

    private boolean detectEdge() {
        if (!mc.player.onGround()) return false;
        Vec3 vel = mc.player.getDeltaMovement();
        if (Math.sqrt(vel.x*vel.x + vel.z*vel.z) < 0.1) return false;
        Vec3 look = mc.player.getLookAngle();
        for (double d=0.3; d<=2.0; d+=0.35) {
            Vec3 p = mc.player.position().add(look.scale(d));
            BlockPos feet = new BlockPos((int)Math.floor(p.x), (int)Math.floor(mc.player.getY()-0.05), (int)Math.floor(p.z));
            if (!mc.level.getBlockState(feet).isSolid()) return true;
        }
        return false;
    }

    private boolean detectOneBlock() {
        BlockPos ahead = mc.player.blockPosition().offset((int)Math.signum(mc.player.getLookAngle().x), 0, (int)Math.signum(mc.player.getLookAngle().z));
        BlockState s = mc.level.getBlockState(ahead);
        return s.isSolid() && mc.level.getBlockState(ahead.above()).isAir();
    }

    private boolean isBlocked() {
        BlockPos ahead = mc.player.blockPosition().offset((int)Math.signum(mc.player.getLookAngle().x), 0, (int)Math.signum(mc.player.getLookAngle().z));
        BlockState s = mc.level.getBlockState(ahead);
        return s.isSolid() && s.getDestroySpeed(mc.level, ahead) >= 0;
    }

    private void breakBlock() {
        BlockPos ahead = mc.player.blockPosition().offset((int)Math.signum(mc.player.getLookAngle().x), 0, (int)Math.signum(mc.player.getLookAngle().z));
        mc.gameMode.destroyBlock(ahead);
    }

    public void setTarget(String name, boolean kill) { this.targetName = name; this.kill = kill; }
    public void clear() { targetName = null; targetUUID = null; kill = false; }
    public boolean hasTarget() { return tm.getPrimary() != null; }
}
