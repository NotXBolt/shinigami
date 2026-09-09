package shinigami.combat;

import shinigami.AimAssistMod;
import shinigami.movement.MovementIntent;
import shinigami.util.KeyMovementController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ComboTracker {

    private final Minecraft mc = Minecraft.getInstance();
    private final Map<Integer, ComboData> combos = new HashMap<>();

    private boolean active = false;
    private int wtapTicks = 0;
    private int sprintResetTicks = 0;
    private int strafeDir = 1;
    private int strafeTimer = 0;
    private int pressureTicks = 0;

    private static final long COMBO_TIMEOUT_MS = 3000;
    private static final int WTAP_DURATION = 2;
    private static final int SPRINT_RESET_COOLDOWN = 3;
    private static final int STRAFE_INTERVAL_COMBAT = 6;

    public void onAttack(LivingEntity target) {
        if (target == null || !active) return;

        int id = target.getId();
        long now = System.currentTimeMillis();

        ComboData combo = combos.computeIfAbsent(id, k -> new ComboData());

        if (now - combo.lastHitTime < 2000) {
            combo.hits++;
        } else {
            combo.hits = 1;
        }
        combo.lastHitTime = now;

        // W-tap on every hit for sprint reset knockback
        wtapTicks = WTAP_DURATION;
        sprintResetTicks = SPRINT_RESET_COOLDOWN;
        pressureTicks = Math.min(pressureTicks + 20, 100);
    }

    public void tick() {
        if (!active || mc.player == null) return;

        long now = System.currentTimeMillis();
        combos.entrySet().removeIf(e -> now - e.getValue().lastHitTime > COMBO_TIMEOUT_MS);
        combos.keySet().removeIf(id -> mc.level != null && mc.level.getEntity(id) == null);

        if (pressureTicks > 0) pressureTicks--;

        // W-tap: release sprint for WTAP_DURATION ticks
        if (wtapTicks > 0) {
            wtapTicks--;
            mc.player.setSprinting(false);
            return;
        }

        // Sprint reset cooldown
        if (sprintResetTicks > 0) {
            sprintResetTicks--;
            return;
        }

        // Resume sprint after W-tap cycle
        if (!mc.player.isSprinting() && mc.player.getFoodData().getFoodLevel() > 6 && isInCombo()) {
            mc.player.setSprinting(true);
        }

        // Strafe oscillation during combos
        if (isInCombo() && mc.player.onGround()) {
            strafeTimer++;
            if (strafeTimer >= STRAFE_INTERVAL_COMBAT) {
                strafeDir *= -1;
                strafeTimer = 0;
            }
        }
    }

    public MovementIntent getCombatIntent() {
        if (!active || !isInCombo() || mc.player == null) return null;

        Vec3 look = mc.player.getLookAngle();
        Vec3 perp = new Vec3(-look.z, 0, look.x).normalize();

        // Strafe perpendicular to look direction, alternating
        Vec3 dir = perp.scale(strafeDir * 0.5);
        if (mc.player.onGround()) {
            dir = dir.add(new Vec3(look.x, 0, look.z).normalize().scale(0.3));
        }

        boolean sprint = !isWTapActive();
        return new MovementIntent(
            MovementIntent.Priority.COMBAT,
            dir.lengthSqr() > 0.01 ? dir : Vec3.ZERO,
            sprint,
            mc.player.onGround() && strafeTimer == 0 ? MovementIntent.JumpType.COMBAT_HOP : MovementIntent.JumpType.NONE,
            false,
            1,
            "combo-strafe"
        );
    }

    public boolean isWTapActive() { return wtapTicks > 0 || sprintResetTicks > 0; }

    public int getComboCount(int entityId) {
        ComboData data = combos.get(entityId);
        return data != null ? data.hits : 0;
    }

    public int getComboCount(LivingEntity entity) {
        return getComboCount(entity.getId());
    }

    public int getActiveCombos() { return combos.size(); }

    public boolean isInCombo() {
        long now = System.currentTimeMillis();
        return combos.values().stream().anyMatch(c -> now - c.lastHitTime < 2000 && c.hits >= 1);
    }

    public int getPressureLevel() { return pressureTicks; }

    public void setActive(boolean a) {
        this.active = a;
        if (!a) {
            wtapTicks = 0;
            sprintResetTicks = 0;
            pressureTicks = 0;
        }
    }

    public void reset() {
        combos.clear();
        wtapTicks = 0;
        sprintResetTicks = 0;
        pressureTicks = 0;
    }

    private static class ComboData {
        int hits = 0;
        long lastHitTime = 0;
    }
}
