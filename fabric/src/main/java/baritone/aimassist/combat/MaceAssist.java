package baritone.aimassist.combat;

import baritone.aimassist.AimAssistConfig;
import baritone.api.utils.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;

public class MaceAssist {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    private boolean active = false;
    private int windBurstCooldown = 0;
    private boolean windBurstTriggered = false;
    private int smashTicks = 0;
    private int lastSmashTick = 0;
    private int tickCounter = 0;

    // Self-launch state
    private boolean launchRequested = false;
    private int launchCooldown = 0;

    public MaceAssist(AimAssistConfig config) {
        this.config = config;
    }

    public void tick() {
        if (!active || mc.player == null) return;
        tickCounter++;
        if (launchCooldown > 0) launchCooldown--;

        if (windBurstCooldown > 0) windBurstCooldown--;

        ItemStack mainHand = mc.player.getMainHandItem();
        if (!mainHand.is(Items.MACE)) return;

        // SELF-LAUNCH: on the ground with a target in range + a charge item
        // (fire charge or wind charge) → blast at feet to gain smash height.
        if (mc.player.onGround() && launchCooldown == 0) {
            LivingEntity target = nearestTargetInRange();
            if (target != null && shouldSelfLaunch()) {
                launchRequested = true;
                launchWithChargeAtFeet();
                launchCooldown = 30;
                return;
            }
        }
        launchRequested = false;

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

    private boolean shouldSelfLaunch() {
        if (!config.isAutoSmash()) return false;
        // Needs at least one fire charge or wind charge in the hotbar.
        return hasFireCharge() || hasWindCharge();
    }

    private void launchWithChargeAtFeet() {
        if (mc.player == null) return;
        // Prefer wind charge (better vertical) then fire charge.
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.WIND_CHARGE)) { slot = i; break; }
        }
        if (slot == -1) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.is(Items.FIRE_CHARGE)) { slot = i; break; }
            }
        }
        if (slot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
            new BlockHitResult(
                mc.player.position().add(0, -0.4, 0),
                Direction.UP,
                mc.player.blockPosition().below(),
                false
            ));
        mc.player.getInventory().setSelectedSlot(prevSlot);
        windBurstTriggered = true;
    }

    private boolean hasFireCharge() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.FIRE_CHARGE)) return true;
        }
        return false;
    }

    private boolean hasWindCharge() {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.WIND_CHARGE)) return true;
        }
        return false;
    }

    private LivingEntity nearestTargetInRange() {
        if (mc.level == null || mc.player == null) return null;
        LivingEntity best = null;
        double bestDist = config.getRange() + 6;
        double scan = config.getRange() + 10;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            mc.player.getX() - scan, mc.player.getY() - scan, mc.player.getZ() - scan,
            mc.player.getX() + scan, mc.player.getY() + scan, mc.player.getZ() + scan
        );
        for (net.minecraft.world.entity.Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
            if (e instanceof LivingEntity living && living != mc.player && living.isAlive()) {
                double d = mc.player.distanceTo(living);
                if (d < bestDist) { bestDist = d; best = living; }
            }
        }
        return best;
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
    public boolean launchRequested() { return launchRequested; }
}