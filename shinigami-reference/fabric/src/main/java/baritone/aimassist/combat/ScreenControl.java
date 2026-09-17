package baritone.aimassist.combat;

import baritone.aimassist.AimAssistConfig;
import baritone.aimassist.AimAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

public class ScreenControl {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module;

    public ScreenControl(AimAssistModule module) {
        this.module = module;
    }

    private boolean eating = false;
    private boolean blocking = false;
    private boolean usingItem = false;

    private static final long EAT_SAFE_TIME_MS = 400;

    public boolean shouldBlockAim() {
        if (mc.player == null) return false;

        LocalPlayer p = mc.player;

        boolean isUsingItem = p.isUsingItem();
        ItemStack using = p.getUseItem();

        boolean isEating = isUsingItem && using.getItem().components().has(net.minecraft.core.component.DataComponents.FOOD);
        boolean isDrinking = isUsingItem && using.is(Items.POTION);
        boolean isBlocking = isUsingItem && using.getItem() instanceof net.minecraft.world.item.ShieldItem;
        boolean isCharging = isUsingItem && (using.is(Items.BOW) || using.is(Items.CROSSBOW));
        boolean isTrident = isUsingItem && using.is(Items.TRIDENT);

        this.eating = isEating || isDrinking;
        this.blocking = isBlocking;
        this.usingItem = isUsingItem;

        if (isEating || isDrinking) return true;
        if (isBlocking) return true;
        if (isCharging && ((AimAssistConfig)module.getConfig()).isBowMode()) return false;

        return false;
    }

    public boolean isEating() { return eating; }
    public boolean isBlocking() { return blocking; }
    public boolean isUsingItem() { return usingItem; }
}
