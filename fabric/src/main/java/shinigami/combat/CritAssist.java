package shinigami.combat;

import shinigami.AimAssistConfig;
import shinigami.AimAssistMod;
import shinigami.util.KeyMovementController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

public class CritAssist {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean active = false;
    private int jumpCooldown = 0;
    private boolean jumpedThisTick = false;

    public CritAssist(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (!active || mc.player == null) return;
        if (jumpCooldown > 0) jumpCooldown--;
        jumpedThisTick = false;
    }

    public boolean shouldCrit(LivingEntity target) {
        if (!active || mc.player == null || target == null) return false;
        if (mc.player.isPassenger() || mc.player.onClimbable() || mc.player.isMobilityRestricted()) return false;
        if (mc.player.isInWater() || mc.player.isInLava()) return false;
        if (mc.player.isSprinting()) return false;

        if (mc.player.onGround()) return false;

        double dist = mc.player.distanceTo(target);
        if (dist > config.getRange() + 0.5) return false;
        if (mc.player.getAttackStrengthScale(0.5f) < 0.9f) return false;

        return true;
    }

    public void requestCrit(LivingEntity target) {
        if (!active || mc.player == null || target == null) return;
        if (jumpCooldown > 0) return;
        if (mc.player.isPassenger() || mc.player.onClimbable() || mc.player.isMobilityRestricted()) return;
        if (mc.player.isInWater() || mc.player.isInLava()) return;
        if (!mc.player.onGround()) return;
        if (mc.player.isSprinting()) return;

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
        jumpCooldown = 4;
    }

    public boolean isCritReady() {
        if (mc.player == null) return false;
        return !mc.player.onGround() && !mc.player.isSprinting()
            && !mc.player.isInWater() && !mc.player.onClimbable();
    }

    public boolean justJumped() {
        return jumpedThisTick;
    }

    public void onAttack() {
        jumpCooldown = 5;
    }

    public void setActive(boolean a) {
        this.active = a;
        if (!a) { jumpCooldown = 0; jumpedThisTick = false; }
    }
    public boolean isActive() { return active; }
}
