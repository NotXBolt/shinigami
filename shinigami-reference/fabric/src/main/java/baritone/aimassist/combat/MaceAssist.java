package baritone.aimassist.combat;

import baritone.aimassist.AimAssistConfig;
import baritone.api.utils.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class MaceAssist {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean active = false;
    private int windBurstCooldown = 0;
    private boolean windBurstTriggered = false;
    private int smashTicks = 0;
    private int lastSmashTick = 0;
    private int tickCounter = 0;

    public MaceAssist(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (!active || mc.player == null) return;
        tickCounter++;

        if (windBurstCooldown > 0) windBurstCooldown--;

        ItemStack mainHand = mc.player.getMainHandItem();
        if (!mainHand.is(Items.MACE)) return;

        if (mc.player.onGround()) return;

        Vec3 vel = mc.player.getDeltaMovement();
        if (vel.y < -0.3 && mc.player.fallDistance > config.getMinSmashHeight()) {
            int wbLevel = getWindBurstLevel();
            if (wbLevel > 0 && windBurstCooldown == 0 && mc.player.fallDistance > 3) {
                // Wind burst to maintain height advantage
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                windBurstCooldown = 40;
                windBurstTriggered = true;
            }

            smashTicks++;
            if (smashTicks > 2 && !windBurstTriggered) {
                // Will attack on next gameMode.attack call from module tick
            }
        } else {
            smashTicks = 0;
            windBurstTriggered = false;
        }
    }

    public boolean shouldSmash(LivingEntity target) {
        if (!active || !config.isAutoSmash()) return false;
        if (mc.player == null || mc.player.onGround()) return false;
        if (mc.player.fallDistance < config.getMinSmashHeight()) return false;
        if (!mc.player.getMainHandItem().is(Items.MACE)) return false;

        double dist = mc.player.distanceTo(target);
        if (dist > config.getRange() + 3) return false;

        if (mc.player.fallDistance > 1.5 && tickCounter - lastSmashTick < 20) return false;

        return true;
    }

    public void onSmash() {
        lastSmashTick = tickCounter;
        smashTicks = 0;
        windBurstTriggered = false;
    }

    public double calculateSmashDamage(double fallDistance) {
        int densityLevel = getDensityLevel();
        return 6.0 + (fallDistance * 1.5) + (densityLevel * fallDistance);
    }

    public double getOptimalSmashHeight(LivingEntity target) {
        float targetHealth = target.getHealth();
        int density = getDensityLevel();
        double needed = (targetHealth - 6) / (1.5 + density);
        return Math.max(0, Math.min(needed, 50));
    }

    private int getDensityLevel() {
        if (mc.player == null || mc.level == null) return 0;
        var registry = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var holder = registry.get(net.minecraft.world.item.enchantment.Enchantments.DENSITY).orElseThrow();
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, mc.player.getMainHandItem());
    }

    private int getWindBurstLevel() {
        if (mc.player == null || mc.level == null) return 0;
        var registry = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var holder = registry.get(net.minecraft.world.item.enchantment.Enchantments.WIND_BURST).orElseThrow();
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, mc.player.getMainHandItem());
    }

    public void setActive(boolean active) { this.active = active; }
    public boolean isActive() { return active; }
}
