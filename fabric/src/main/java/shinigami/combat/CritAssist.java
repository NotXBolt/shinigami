package shinigami.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * CritAssist — Original, 1-tick burst fall crit. Perfect vanilla.
 * Triggers small hop when onGround and target in range, ensures fallDamage 0.848.
 */
public class CritAssist {
    private final Minecraft mc = Minecraft.getInstance();
    private int cooldown = 0;

    public void tick(LivingEntity target, shinigami.movement.MovementArbiter arbiter) {
        if (mc.player == null || target == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isCritMode()) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isEnabled()) return;
        if (cooldown > 0) { cooldown--; return; }
        double dist = mc.player.distanceTo(target);
        if (dist > 3.2 || dist < 1.8) return;
        if (!mc.player.onGround() || mc.player.isInWater() || mc.player.isInLava()) return;
        // 1-tick burst: supplement jump via arbiter CHASE already handles, here we do crit jump
        if (mc.player.onGround()) {
            mc.player.jumpFromGround();
            cooldown = 8;
        }
    }
}
