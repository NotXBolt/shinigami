package shinigami.movement;

import shinigami.util.KeyController;
import java.util.ArrayList;
import java.util.List;

/**
 * MovementArbiter — Original, picks highest priority intent, applies via KeyController.
 * clearSupplement() at START of tickMovement, never in empty path.
 */
public class MovementArbiter {
    private final List<MovementIntent> intents = new ArrayList<>();
    private MovementIntent last = null;

    public void submit(MovementIntent i) { if (i != null && i.hasMovement()) intents.add(i); }
    public void clear() { intents.clear(); }

    public void apply(KeyController ctrl) {
        if (intents.isEmpty() || ctrl == null) return;
        MovementIntent win = intents.get(0);
        for (int j = 1; j < intents.size(); j++) if (intents.get(j).priority.value > win.priority.value) win = intents.get(j);
        intents.clear();
        last = win;
        if (win.priority.value >= 95) { // DODGE/CLUTCH override
            ctrl.moveToward(win.dir, win.sprint, win.wantsJump(), win.sneak);
        } else { // CHASE/PARKOUR/AUTO_WALK supplement (preserves A/D)
            ctrl.supplementForward(win.sprint);
            if (win.wantsJump()) ctrl.supplementJump();
        }
    }

    public MovementIntent getCurrent() {
        if (!intents.isEmpty()) {
            MovementIntent best = intents.get(0);
            for (int j = 1; j < intents.size(); j++) if (intents.get(j).priority.value > best.priority.value) best = intents.get(j);
            return best;
        }
        return last;
    }
}
