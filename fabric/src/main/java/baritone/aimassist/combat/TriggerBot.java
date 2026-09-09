package baritone.aimassist.combat;

import baritone.aimassist.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class TriggerBot {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean active = false;
    private int delayTicks = 0;
    private static final int ATTACK_COOLDOWN_TICKS = 10;

    public TriggerBot(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (!active || mc.player == null || mc.level == null) return;

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (mc.player.getAttackStrengthScale(0.5f) < 0.9f) return;

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) mc.hitResult;

                    if (entityHit.getEntity() instanceof LivingEntity target) {
                        if (isValidTarget(target)) {
                            mc.gameMode.attack(mc.player, target);
                            mc.player.swing(InteractionHand.MAIN_HAND);

                            baritone.aimassist.AimAssistModule.getInstance().getComboTracker().onAttack(target);

                            delayTicks = Math.max(ATTACK_COOLDOWN_TICKS, config.getTriggerDelay());
                            return;
                        }
                    }
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        if (target == mc.player || !target.isAlive()) return false;

        double dist = mc.player.distanceTo(target);
        if (dist > config.getTriggerRange()) return false;

        if (config.isTriggerOnlyPlayers() && !(target instanceof net.minecraft.world.entity.player.Player)) return false;

        return true;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
    public boolean isActive() { return active; }
}
