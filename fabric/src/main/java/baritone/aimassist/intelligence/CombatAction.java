package baritone.aimassist.intelligence;

import net.minecraft.world.phys.Vec3;

/**
 * CombatAction — the action the intelligence system decides to execute.
 */
public class CombatAction {

    public final CombatActionType type;
    public final Vec3 direction;
    public final boolean jump;
    public final boolean sprint;

    public CombatAction(CombatActionType type) {
        this(type, Vec3.ZERO, false, false);
    }

    public CombatAction(CombatActionType type, Vec3 direction) {
        this(type, direction, false, false);
    }

    public CombatAction(CombatActionType type, Vec3 direction, boolean jump, boolean sprint) {
        this.type = type;
        this.direction = direction;
        this.jump = jump;
        this.sprint = sprint;
    }

    @Override
    public String toString() {
        return "CombatAction{" + type + ", dir=" + direction + ", jump=" + jump + ", sprint=" + sprint + "}";
    }
}
