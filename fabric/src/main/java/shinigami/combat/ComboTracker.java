package shinigami.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/**
 * ComboTracker — Original, hit-chain, adjusts timing based on RL.
 * Tracks hurtTime, predicts next hit window.
 */
public class ComboTracker {
    private int combo = 0;
    private long lastHit = 0;
    private int lastHurt = -1;

    public void onAttack(LivingEntity target) {
        long now = System.currentTimeMillis();
        if (now - lastHit < 900) combo++;
        else combo = 1;
        lastHit = now;
        if (target != null) lastHurt = target.hurtTime;
    }

    public int getCombo() { return combo; }
    public boolean isInCombo() { return System.currentTimeMillis() - lastHit < 1000 && combo >= 2; }

    public void tick() {
        if (System.currentTimeMillis() - lastHit > 2000) combo = 0;
    }
}
