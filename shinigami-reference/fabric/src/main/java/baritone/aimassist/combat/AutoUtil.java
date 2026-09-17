package baritone.aimassist.combat;

import baritone.aimassist.AimAssistConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AutoUtil {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistConfig config;

    public AutoUtil(AimAssistConfig config) {
        this.config = config;
    }

    // === AUTO SPRINT ===
    public void autoSprint(boolean attacking) {
        if (mc.player == null) return;
        if (mc.player.isUsingItem()) return;
        if (mc.player.isInWater() && !config.isSprintInWater()) return;

        boolean shouldSprint = !attacking && !mc.player.isShiftKeyDown() &&
            mc.player.getFoodData().getFoodLevel() > 6 &&
            (mc.options.keyUp.isDown());

        mc.player.setSprinting(shouldSprint);
    }

    // === AUTO SNEAK ===
    public void autoSneak() {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.onGround()) return;

        // Sneak when near edge
        Vec3 pos = mc.player.position();
        boolean atEdge = false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                var blockPos = mc.player.blockPosition().offset(dx, -1, dz);
                if (mc.level.getBlockState(blockPos).isAir()) {
                    atEdge = true;
                    break;
                }
            }
            if (atEdge) break;
        }

        if (atEdge && !mc.player.isShiftKeyDown()) {
            mc.options.keyShift.setDown(true);
        } else if (!atEdge && mc.options.keyShift.isDown() && !mc.player.isUsingItem()) {
            mc.options.keyShift.setDown(false);
        }
    }

    // === AUTO POTION ===
    public void autoPotion(boolean force) {
        if (mc.player == null) return;
        if (mc.player.isUsingItem()) return;

        if (!force) {
            // Only potion if in combat or chasing
            if (!config.isPvpMode() && !config.isChaseMode()) return;
        }

        // Check if we already have good effects
        if (mc.player.hasEffect(MobEffects.STRENGTH) &&
            mc.player.hasEffect(MobEffects.SPEED) &&
            mc.player.hasEffect(MobEffects.REGENERATION)) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof PotionItem) {
                var effects = new java.util.ArrayList<net.minecraft.world.effect.MobEffectInstance>(); // TODO: PotionUtils removed
                boolean useful = false;
                for (var effect : effects) {
                    if (effect.getEffect() == MobEffects.STRENGTH ||
                        effect.getEffect() == MobEffects.SPEED ||
                        effect.getEffect() == MobEffects.REGENERATION ||
                        effect.getEffect() == MobEffects.STRENGTH ||
                        effect.getEffect() == MobEffects.FIRE_RESISTANCE ||
                        effect.getEffect() == MobEffects.NIGHT_VISION) {
                        if (mc.player.hasEffect(effect.getEffect())) continue;
                        useful = true;
                        break;
                    }
                }
                if (useful) {
                    mc.player.getInventory().setSelectedSlot(i);
                    mc.options.keyUse.setDown(true);
                    return;
                }
            }
        }
    }

    // === PEARL PREDICTION ===
    public Vec3 predictPearlLanding(Vec3 startPos, Vec3 lookVec, float charge) {
        double power = 1.5 + charge * 0.5;
        double vx = lookVec.x * power;
        double vy = lookVec.y * power + 0.5;
        double vz = lookVec.z * power;

        double x = startPos.x, y = startPos.y, z = startPos.z;

        for (int t = 0; t < 200; t++) {
            x += vx;
            y += vy;
            z += vz;
            vy -= 0.03;
            vx *= 0.99;
            vy *= 0.99;
            vz *= 0.99;

            if (y < startPos.y - 0.5) break;
        }

        return new Vec3(x, y, z);
    }

    // === AUTO TOTEM REFILL ===
    public void autoTotemRefill() {
        if (mc.player == null) return;
        if (mc.player.tickCount % 10 != 0) return;

        // Check offhand
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;

        // Find totem in inventory
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId,
                    i < 9 ? i + 36 : i,
                    40,
                    net.minecraft.world.inventory.ContainerInput.SWAP,
                    mc.player
                );
                break;
            }
        }
    }

    // === SHIELD ASSIST ===
    public void shieldAssist() {
        if (mc.player == null) return;
        if (!config.isPvpMode() && !config.isChaseMode()) return;

        ItemStack offhand = mc.player.getOffhandItem();
        if (!(offhand.getItem() instanceof ShieldItem)) return;

        // Auto-block when taking damage or expecting damage
        boolean shouldBlock = mc.player.hurtTime > 0 ||
            (mc.player.getLastHurtMob() != null && mc.player.distanceTo(mc.player.getLastHurtMob()) < 5);

        if (shouldBlock && !mc.player.isUsingItem()) {
            mc.options.keyUse.setDown(true);
        } else if (!shouldBlock && mc.player.isUsingItem() && mc.player.getUseItem().getItem() instanceof ShieldItem) {
            mc.options.keyUse.setDown(false);
        }
    }

    // === AUTO XP ===
    public void autoXP() {
        if (mc.player == null) return;
        if (!config.isAutoMode()) return;

        // Use XP bottles if tools are low
        if (mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot()).is(Items.EXPERIENCE_BOTTLE)) {
            if (mc.player.experienceLevel < 30) {
                mc.options.keyUse.setDown(true);
            }
        }
    }

    // === COMBO KILL EFFECTS ===
    public void checkComboEffects(int combo) {
        if (combo <= 1) return;
        if (mc.level == null || mc.player == null) return;

        // Could add particle effects or sounds for combo milestones
        // Visual-only, client-side
    }

    public void setActive(boolean a) {}
}
