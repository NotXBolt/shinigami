package shinigami.combat;

import baritone.api.utils.Rotation;
import shinigami.AimAssistConfig;
import shinigami.AimAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public class AutoCombatSwitch {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;
    private final AimAssistModule module;

    private long lastSwitchTime = 0;
    private static final long SWITCH_COOLDOWN_MS = 200;

    public AutoCombatSwitch(AimAssistModule module, AimAssistConfig config) {
        this.module = module;
        this.config = config;
    }

    public void tick() {
        if (mc.player == null) return;
        if (!config.isPvpMode() && !config.isChaseMode() && !config.isAutoMode()) return;
        if (mc.player.isUsingItem()) return;

        LivingEntity target = findBestTarget();

        if (target == null) {
            ensureSwordEquipped();
            return;
        }

        double dist = mc.player.distanceTo(target);

        if (System.currentTimeMillis() - lastSwitchTime < SWITCH_COOLDOWN_MS) return;

        if (target.isUsingItem()) {
            ItemStack using = target.getUseItem();
            if (using.getItem() instanceof ShieldItem) {
                if (switchToBestAxe()) return;
            }
        }

        if (dist > 10) {
            switchToBow();
        } else if (dist > 4 && dist <= 10) {
            if (hasPearls() && target instanceof Player) {
                tryPearlClose(target);
            } else {
                switchToBow();
            }
        } else {
            if (isUsingShield(target)) {
                switchToBestAxe();
            } else {
                ensureSwordEquipped();
            }
        }

        if (isBeingChased() && hasLavaBucket()) {
            placeLavaBucket(target);
        }

        if (isLosingTrade(target)) {
            eatGap();
        }
    }

    public void tryPearlThrow(Vec3 targetPos) {
        if (mc.player == null) return;

        int pearlSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.ENDER_PEARL)) {
                pearlSlot = i;
                break;
            }
        }
        if (pearlSlot == -1) return;

        mc.player.getInventory().setSelectedSlot(pearlSlot);

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 diff = targetPos.subtract(eyePos);
        double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        float pitch = -25;
        float yaw = (float)(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90);

        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);

        lastSwitchTime = System.currentTimeMillis();
        mc.options.keyUse.setDown(true);
        try { Thread.sleep(50); } catch (InterruptedException e) {}
        mc.options.keyUse.setDown(false);
    }

    public void placeLavaBucket(LivingEntity target) {
        if (mc.player == null) return;
        int lavaSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.LAVA_BUCKET)) {
                lavaSlot = i;
                break;
            }
        }
        if (lavaSlot == -1) return;

        mc.player.getInventory().setSelectedSlot(lavaSlot);
        Vec3 targetPos = target.position();

        mc.player.setYRot((float)(Math.toDegrees(Math.atan2(
            targetPos.z - mc.player.getZ(), targetPos.x - mc.player.getX()
        )) - 90));
        mc.player.setXRot(-10);

        mc.options.keyUse.setDown(true);
        lastSwitchTime = System.currentTimeMillis();
    }

    public void eatGap() {
        if (mc.player == null || mc.player.isUsingItem()) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                mc.player.getInventory().setSelectedSlot(i);
                mc.options.keyUse.setDown(true);
                lastSwitchTime = System.currentTimeMillis();
                return;
            }
        }
    }

    private boolean switchToBestAxe() {
        int best = -1;
        double bestDmg = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(ItemTags.AXES)) {
                double dmg = getAttackDamage(stack);
                if (dmg > bestDmg) {
                    bestDmg = dmg;
                    best = i;
                }
            }
        }
        if (best != -1) {
            mc.player.getInventory().setSelectedSlot(best);
            lastSwitchTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private void ensureSwordEquipped() {
        if (mc.player == null) return;
        ItemStack current = mc.player.getMainHandItem();
        if (current.is(ItemTags.SWORDS)) return;

        int bestSword = -1;
        double bestDmg = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(ItemTags.SWORDS)) {
                double dmg = getAttackDamage(stack);
                if (dmg > bestDmg) {
                    bestDmg = dmg;
                    bestSword = i;
                }
            }
        }
        if (bestSword != -1) {
            mc.player.getInventory().setSelectedSlot(bestSword);
            lastSwitchTime = System.currentTimeMillis();
        }
    }

    private boolean switchToBow() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) {
                mc.player.getInventory().setSelectedSlot(i);
                if (((AimAssistConfig)module.getConfig()).isBowMode()) {
                    module.getBowAssist().setActive(true);
                }
                lastSwitchTime = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    private LivingEntity findBestTarget() {
        if (mc.level == null || mc.player == null) return null;
        double closest = config.getRange();
        LivingEntity result = null;
        double scanRange = Math.max(config.getRange(), 64) + 16;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            mc.player.getX() - scanRange, mc.player.getY() - scanRange, mc.player.getZ() - scanRange,
            mc.player.getX() + scanRange, mc.player.getY() + scanRange, mc.player.getZ() + scanRange
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
            if (e instanceof LivingEntity living && living != mc.player && living.isAlive()) {
                double dist = mc.player.distanceTo(living);
                if (dist < closest) {
                    closest = dist;
                    result = living;
                }
            }
        }
        return result;
    }

    private boolean hasPearls() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.ENDER_PEARL)) return true;
        }
        return false;
    }

    private boolean hasLavaBucket() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.LAVA_BUCKET)) return true;
        }
        return false;
    }

    private boolean isUsingShield(LivingEntity target) {
        return target.isUsingItem() && target.getUseItem().getItem() instanceof ShieldItem;
    }

    private boolean isBeingChased() {
        if (mc.player == null || mc.level == null) return false;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
            mc.player.getX() - 16, mc.player.getY() - 16, mc.player.getZ() - 16,
            mc.player.getX() + 16, mc.player.getY() + 16, mc.player.getZ() + 16
        );
        for (Entity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box)) {
            if (e instanceof LivingEntity living && living != mc.player) {
                if (living.getLastHurtMob() == mc.player || (living instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == mc.player)) {
                    return mc.player.distanceTo(living) < 5;
                }
            }
        }
        return false;
    }

    private boolean isLosingTrade(LivingEntity target) {
        if (mc.player == null) return false;
        float myHealth = mc.player.getHealth();
        float targetHealth = target.getHealth();

        return myHealth < targetHealth * 0.5 && myHealth < 10;
    }

    private void tryPearlClose(LivingEntity target) {
        Vec3 targetPos = target.position().add(0, 0, 0);
        tryPearlThrow(targetPos);
    }

    private double getAttackDamage(ItemStack stack) {
        var attr = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attr != null) {
            for (var entry : attr.modifiers()) {
                if (entry.attribute() == Attributes.ATTACK_DAMAGE) {
                    return entry.modifier().amount();
                }
            }
        }
        return 0.0;
    }
}
