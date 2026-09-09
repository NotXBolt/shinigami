package baritone.aimassist.movement;

import baritone.aimassist.util.KeyMovementController;
import java.util.ArrayList;
import java.util.List;

public class MovementArbiter {
    private final List<MovementIntent> intents = new ArrayList<>();
    private MovementIntent lastWinner = null;

    public void submit(MovementIntent intent) {
        if (intent != null && intent.hasMovement()) intents.add(intent);
    }

    public void clear() { intents.clear(); }

    public void apply(KeyMovementController ctrl) {
        if (intents.isEmpty() || ctrl == null) {
            return;
        }

        // UNKILLABLE RESOLUTION: DODGE (100) > CLUTCH (95) > CRIT (70) > CHASE (50) > COMBAT (30) > PARKOUR (20) > AUTO_WALK (10)
        // Defensive priorities are ABSOLUTE — never overridden by offensive intentions
        // Packet defense (velocity suppression + explosion defense) ensures zero knockback disruption
        MovementIntent winner = null;
        for (int i = 1; i < intents.size(); i++) {
            if (intents.get(i).priority.value > winner.priority.value) {
                winner = intents.get(i);
            }
        }
        intents.clear();
        lastWinner = winner;

        if (winner.priority == MovementIntent.Priority.DODGE || winner.priority == MovementIntent.Priority.CLUTCH) {
            ctrl.moveToward(winner.direction, winner.sprint, winner.wantsJump(), winner.sneak);
        } else if (winner.priority == MovementIntent.Priority.CRIT) {
            ctrl.moveToward(winner.direction, winner.sprint, winner.wantsJump(), winner.sneak);
        } else if (winner.priority == MovementIntent.Priority.CHASE) {
            ctrl.supplementForward(winner.sprint);
            if (winner.wantsJump()) ctrl.supplementJump();
        } else if (winner.priority == MovementIntent.Priority.COMBAT) {
            // Combat strafe / melee dodge: use direction-aware supplement
            ctrl.supplementDirection(winner.direction, winner.sprint);
            if (winner.wantsJump()) ctrl.supplementJump();
        } else {
            // PARKOUR, AUTO_WALK: forward supplement only
            ctrl.supplementForward(winner.sprint);
            if (winner.wantsJump()) ctrl.supplementJump();
        }
    }

    /** Get the last resolved winner without clearing intents. */
    public MovementIntent getCurrentIntent() {
        if (!intents.isEmpty()) {
            MovementIntent best = intents.get(0);
            for (int i = 1; i < intents.size(); i++) {
                if (intents.get(i).priority.value > best.priority.value) best = intents.get(i);
            }
            return best;
        }
        return lastWinner;
    }
}
