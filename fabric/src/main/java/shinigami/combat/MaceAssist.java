package shinigami.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/**
 * MaceAssist — Original, fall smash, wind burst tracking.
 * Checks holding mace, height >2, triggers smash via jump logic.
 */
public class MaceAssist {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(LivingEntity target, shinigami.movement.MovementArbiter arbiter) {
        if (mc.player == null || target == null) return;
        if (!shinigami.config.ShinigamiConfig.getInstance().isMaceMode()) return;
        if (!mc.player.getMainHandItem().is(Items.MACE) && !mc.player.getOffhandItem().is(Items.MACE)) return;
        double height = mc.player.getY() - target.getY();
        if (height < 2.0) return;
        if (mc.player.fallDistance < 1.5) return;
        // in smash window — ensure not dodging into unsafe
        double dist = mc.player.distanceTo(target);
        if (dist > 4) return;
        // let chase handle movement, mace just ensures crit-like fall
    }
}
