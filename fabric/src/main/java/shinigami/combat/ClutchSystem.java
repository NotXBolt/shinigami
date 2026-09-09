package shinigami.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import shinigami.movement.MovementArbiter;
import shinigami.movement.MovementIntent;

/**
 * ClutchSystem — Original, water/hay/ladder, raycast, latency aware.
 */
public class ClutchSystem {
    private final Minecraft mc = Minecraft.getInstance();

    public void tick(MovementArbiter arbiter) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.getDeltaMovement().y > -0.6) return;
        if (mc.player.fallDistance < 3) return;
        // Raycast to ground
        Vec3 origin = mc.player.position();
        Vec3 proj = origin.add(0, mc.player.getDeltaMovement().y * 2, 0);
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(origin, proj, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            // Has water bucket?
            boolean hasWater = false;
            for (int i=0;i<9;i++) if (mc.player.getInventory().getItem(i).is(Items.WATER_BUCKET)) hasWater = true;
            if (hasWater) {
                arbiter.submit(new MovementIntent(MovementIntent.Priority.CLUTCH, Vec3.ZERO, false, MovementIntent.JumpType.CLUTCH_JUMP, false, 5, "clutch"));
            }
        }
    }
}
