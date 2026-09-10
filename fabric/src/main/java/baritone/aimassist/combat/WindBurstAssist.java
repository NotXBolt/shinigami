package baritone.aimassist.combat;

import baritone.aimassist.AimAssistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;

public class WindBurstAssist {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean active = false;
    private int airTicks = 0;
    private int windChargeUseCooldown = 0;

    public WindBurstAssist(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (!active || mc.player == null) return;

        LocalPlayer p = mc.player;

        if (windChargeUseCooldown > 0) windChargeUseCooldown--;

        if (p.onGround()) {
            airTicks = 0;
            return;
        }

        airTicks++;
        Vec3 vel = p.getDeltaMovement();

        boolean holdingMace = p.getMainHandItem().is(Items.MACE);

        // Use charge item (wind charge OR fire charge) to boost upward when
        // falling with mace. Fire at feet so explosion propels upward.
        if (holdingMace && airTicks > 8 && vel.y < -0.3 && hasChargeItem() && windChargeUseCooldown == 0) {
            useChargeAtFeet();
        }
    }

    private boolean hasChargeItem() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WIND_CHARGE) || stack.is(Items.FIRE_CHARGE)) return true;
        }
        return false;
    }

    private void useChargeAtFeet() {
        if (mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WIND_CHARGE) || stack.is(Items.FIRE_CHARGE)) {
                int prevSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(i);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                        mc.player.position().add(0, -0.5, 0),
                        Direction.UP,
                        mc.player.blockPosition().below(),
                        false
                    ));
                mc.player.getInventory().setSelectedSlot(prevSlot);
                windChargeUseCooldown = 10;
                return;
            }
        }
    }

    public boolean shouldWindBurst() {
        if (mc.player == null || !active) return false;
        if (mc.player.onGround()) return false;
        return mc.player.fallDistance > 10.0;
    }

    public void setActive(boolean a) { this.active = a; }
    public boolean isActive() { return active; }
    public int getAirTicks() { return airTicks; }
}
