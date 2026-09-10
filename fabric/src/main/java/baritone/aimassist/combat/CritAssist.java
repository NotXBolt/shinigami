package baritone.aimassist.combat;

import baritone.aimassist.AimAssistConfig;
import baritone.aimassist.AimAssistMod;
import baritone.aimassist.util.KeyMovementController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * CritAssist — pvp-bot style fall-based crits.
 * Sequence: sprint-cancel + jump → wait until velocity.y < 0 (falling)
 * → attack while falling = guaranteed crit, consistent timing.
 * We never attack on the rising phase (wasted crit window / bad rhythm).
 */
public class CritAssist {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean active = false;
    private int jumpCooldown = 0;
    private boolean jumpedThisTick = false;
    private boolean critPending = false;
    private int fallTicks = 0;

    private static final int MIN_FALL_TICKS = 2;

    public CritAssist(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (!active || mc.player == null) return;
        if (jumpCooldown > 0) jumpCooldown--;
        jumpedThisTick = false;

        // Cancel the pending crit if we landed or the chain broke.
        if (critPending) {
            if (mc.player.onGround()) {
                critPending = false;
                fallTicks = 0;
            } else if (mc.player.getVelocity().y < 0) {
                fallTicks++;
            } else {
                fallTicks = 0;
            }
        }
    }

    /** True when we should attack RIGHT NOW for a guaranteed crit. */
    public boolean shouldCrit(LivingEntity target) {
        if (!active || mc.player == null || target == null) return false;
        if (mc.player.isPassenger() || mc.player.onClimbable() || mc.player.isMobilityRestricted()) return false;
        if (mc.player.isInWater() || mc.player.isInLava()) return false;
        if (mc.player.isSprinting()) return false;
        if (mc.player.onGround()) return false;
        if (!critPending || mc.player.getVelocity().y >= 0) return false;
        if (fallTicks < MIN_FALL_TICKS) return false;

        double dist = mc.player.distanceTo(target);
        if (dist > config.getRange() + 0.5) return false;
        if (mc.player.getAttackStrengthScale(0.5f) < 0.9f) return false;

        return true;
    }

    /** Ask for a crit: sprint-cancel + jump. Attack comes later when falling. */
    public void requestCrit(LivingEntity target) {
        if (!active || mc.player == null || target == null) return;
        if (jumpCooldown > 0) return;
        if (mc.player.isPassenger() || mc.player.onClimbable() || mc.player.isMobilityRestricted()) return;
        if (mc.player.isInWater() || mc.player.isInLava()) return;
        if (!mc.player.onGround()) return;
        if (mc.player.isSprinting()) return;
        if (critPending) return;

        double dist = mc.player.distanceTo(target);
        if (dist > config.getRange() + 1.0) return;
        if (mc.player.getAttackStrengthScale(0.0f) < 0.9f) return;

        // Sprint cancel + jump in same action (1-tick burst)
        mc.player.setSprinting(false);
        mc.player.jumpFromGround();

        KeyMovementController ctrl = AimAssistMod.getInstance().getMovementController();
        if (ctrl != null) {
            ctrl.supplementJump();
        }

        jumpedThisTick = true;
        critPending = true;
        fallTicks = 0;
        jumpCooldown = 4;
    }

    /** Back-compat path: attackers already in a fall window just hit. */
    public boolean isCritReady() {
        if (mc.player == null) return false;
        return !mc.player.onGround() && !mc.player.isSprinting()
            && !mc.player.isInWater() && !mc.player.onClimbable()
            && mc.player.getVelocity().y < 0;
    }

    public boolean justJumped() {
        return jumpedThisTick;
    }

    public void onAttack() {
        jumpCooldown = 5;
        critPending = false;
        fallTicks = 0;
    }

    public void setActive(boolean a) {
        this.active = a;
        if (!a) { jumpCooldown = 0; jumpedThisTick = false; critPending = false; fallTicks = 0; }
    }
    public boolean isActive() { return active; }
}