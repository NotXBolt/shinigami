package shinigami.movement;

import net.minecraft.world.phys.Vec3;

/**
 * MovementIntent — Original, extensive, perfect. Phase 0 bus.
 * DODGE 100 > CLUTCH 95 > CHASE 50 > COMBAT 30 > PARKOUR 20 > AUTO_WALK 10
 */
public class MovementIntent {
    public enum Priority {
        DODGE(100), CLUTCH(95), CHASE(50), COMBAT(30), PARKOUR(20), AUTO_WALK(10);
        public final int value;
        Priority(int v) { this.value = v; }
    }
    public enum JumpType { NONE, GAP_JUMP, DODGE_JUMP, CLUTCH_JUMP, TOWER_JUMP }

    public final Priority priority;
    public final Vec3 dir;
    public final boolean sprint;
    public final JumpType jump;
    public final boolean sneak;
    public final int duration;
    public final String reason;

    public MovementIntent(Priority p, Vec3 d, boolean sprint, JumpType j, boolean sneak, int dur, String r) {
        this.priority = p;
        this.dir = d != null && d.lengthSqr() > 1e-6 ? d.normalize() : Vec3.ZERO;
        this.sprint = sprint;
        this.jump = j != null ? j : JumpType.NONE;
        this.sneak = sneak;
        this.duration = Math.max(1, dur);
        this.reason = r != null ? r : "generic";
    }

    public boolean hasMovement() { return dir.lengthSqr() > 1e-6; }
    public boolean wantsJump() { return jump != JumpType.NONE; }
}
