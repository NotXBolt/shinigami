package baritone.aimassist.movement;

import net.minecraft.world.phys.Vec3;

public class MovementIntent {
    public enum Priority {
        DODGE(100), CLUTCH(95), CRIT(70), CHASE(50), COMBAT(30), PARKOUR(20), AUTO_WALK(10);
        public final int value;
        Priority(int v) { this.value = v; }
        public boolean overrides(Priority other) { return this.value > other.value; }
    }

    public enum JumpType {
        NONE, MICRO_HOP, COMBAT_HOP, GAP_JUMP, CLIMB_JUMP, DODGE_JUMP, TOWER_JUMP, REVERSE_JUMP
    }

    public final Priority priority;
    public final Vec3 direction;
    public final boolean sprint;
    public final JumpType jumpType;
    public final boolean sneak;
    public final int durationTicks;
    public final String reason;

    public MovementIntent(Priority priority, Vec3 direction, boolean sprint, JumpType jump, boolean sneak, int duration, String reason) {
        this.priority = priority;
        this.direction = direction != null && direction.lengthSqr() > 0.01 ? direction.normalize() : Vec3.ZERO;
        this.sprint = sprint;
        this.jumpType = jump;
        this.sneak = sneak;
        this.durationTicks = Math.max(1, duration);
        this.reason = reason != null ? reason : "generic";
    }

    public boolean hasMovement() { return direction.lengthSqr() > 0.01; }
    public boolean wantsJump() { return jumpType != JumpType.NONE; }
    public boolean wantsSprint() { return sprint; }
}
