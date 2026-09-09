package shinigami.targeting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import shinigami.config.ShinigamiConfig;
import java.util.*;

/**
 * TargetManager — Original, 360° perfect, all entities huntable. Phase 0.
 */
public class TargetManager {
    private final Minecraft mc = Minecraft.getInstance();
    private final ShinigamiConfig cfg = ShinigamiConfig.getInstance();
    private LivingEntity primary = null;
    private long lastScan = 0;

    public void tick() {
        if (mc.level == null || mc.player == null) return;
        if (System.currentTimeMillis() - lastScan < 50) return;
        lastScan = System.currentTimeMillis();
        if (primary != null && (primary.isRemoved() || !primary.isAlive() || mc.player.distanceTo(primary) > cfg.getDetectionRange())) primary = null;
        List<LivingEntity> found = scan();
        primary = selectBest(found);
    }

    private List<LivingEntity> scan() {
        List<LivingEntity> out = new ArrayList<>();
        double range = cfg.getDetectionRange();
        Vec3 eye = mc.player.getEyePosition();
        AABB box = mc.player.getBoundingBox().inflate(range);
        for (Entity e : mc.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == mc.player || !e.isAlive() || e.isRemoved()) continue;
            LivingEntity le = (LivingEntity) e;
            if (!isValid(le)) continue;
            Vec3 pos = e.position();
            if (eye.distanceTo(pos) > range) continue;
            if (!isInFOV(eye, pos, mc.player.getLookAngle(), cfg.getFOV())) continue;
            out.add(le);
        }
        return out;
    }

    private boolean isValid(LivingEntity e) {
        if (e == null || e.isRemoved() || !e.isAlive()) return false;
        if (!cfg.isTargetInvisible() && e.isInvisible()) return false;
        if (e instanceof net.minecraft.world.entity.animal.IronGolem) return true;
        if (e instanceof net.minecraft.world.entity.animal.SnowGolem) return true;
        if (e instanceof Player) return cfg.isTargetPlayers();
        if (e instanceof net.minecraft.world.entity.monster.Enemy) return cfg.isTargetHostile();
        if (e instanceof net.minecraft.world.entity.animal.Animal) return cfg.isTargetPassive();
        return e instanceof LivingEntity; // catch-all perfect
    }

    private boolean isInFOV(Vec3 from, Vec3 to, Vec3 look, double fov) {
        Vec3 dir = to.subtract(from).normalize();
        if (dir.lengthSqr() < 1e-6) return true;
        double ang = Math.toDegrees(Math.acos(look.dot(dir)));
        return ang <= fov / 2.0; // 360 => 180 always true
    }

    private LivingEntity selectBest(List<LivingEntity> list) {
        if (list.isEmpty()) return null;
        LivingEntity best = null;
        double bestScore = -1;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        for (LivingEntity e : list) {
            Vec3 pos = e.position();
            double dist = eye.distanceTo(pos);
            Vec3 dir = pos.subtract(eye).normalize();
            double angle = Math.toDegrees(Math.acos(look.dot(dir)));
            double distScore = 1.0 - Math.min(dist / cfg.getDetectionRange(), 1.0);
            double angleScore = 1.0 - Math.min(angle / 180.0, 1.0);
            double healthScore = 1.0 - (e.getHealth() / e.getMaxHealth());
            double score = distScore * 0.4 + angleScore * 0.4 + healthScore * 0.2;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    public LivingEntity getPrimary() { return primary; }
    public void clear() { primary = null; }
    public void forceScan() { lastScan = 0; tick(); }
}
