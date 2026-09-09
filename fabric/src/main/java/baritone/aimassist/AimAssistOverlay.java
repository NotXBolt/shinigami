package baritone.aimassist;

import baritone.api.aimassist.IAimTarget;
import baritone.api.aimassist.PredictionData;
import baritone.aimassist.combat.BowAssist;
import baritone.aimassist.combat.MaceAssist;
import baritone.aimassist.combat.ComboTracker;
import baritone.aimassist.prediction.MovementPredictor;
import baritone.aimassist.tags.SmartTaskExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public class AimAssistOverlay {

    private final Minecraft mc = Minecraft.getInstance();
    private final AimAssistModule module = AimAssistModule.getInstance();
    private final AimAssistConfig config = AimAssistConfig.getInstance();

    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int ACCENT_COLOR = 0xFFAA00;

    public static void register() {
        // Mixin calls render directly; no Fabric API registration needed
    }

    public void render(GuiGraphicsExtractor graphics, float partialTick) {
        try {
            if (!config.isShowHUD() || mc.player == null || mc.level == null) return;

            int x = config.getHudX();
            int y = config.getHudY();
            Font font = mc.font;

            String modeStr = module.getMode() == baritone.api.aimassist.IAimAssist.Mode.DEMON ? "§cDEMON" : "§bEZ";
            String status = config.isEnabled() ? "§aENABLED" : "§7DISABLED";
            graphics.text(font, "§l§6Shinigami §7[" + modeStr + "§7] " + status, x, y, TEXT_COLOR);
            y += LINE_HEIGHT;

            // Chase target
            if (module.isChaseMode() && module.getChaseTargetName() != null) {
                String chaseStr = "§7Chase: §e" + module.getChaseTargetName();
                if (module.isChaseKill()) chaseStr += " §c☠";
                graphics.text(font, chaseStr, x, y, TEXT_COLOR);
                y += LINE_HEIGHT;
            }

            // Active task from SmartTaskExecutor
            var executor = module.getSmartExecutor();
            if (executor != null) {
                String action = executor.getCurrentAction();
                if (action != null && !action.equals("Idle")) {
                    graphics.text(font, "§7Task: §f" + action, x, y, TEXT_COLOR);
                    y += LINE_HEIGHT;
                }
                int tasks = executor.getTaskQueue().size();
                if (tasks > 0) {
                    graphics.text(font, "§7Queue: §f" + tasks + " tasks", x, y, TEXT_COLOR);
                    y += LINE_HEIGHT;
                }
            }

            // Sub-modes
            StringBuilder modes = new StringBuilder("§7[");
            if (config.isCritMode()) modes.append("§eCRIT ");
            if (config.isComboMode()) modes.append("§dCOMBO ");
            if (config.isMaceMode()) modes.append("§cMACE ");
            if (config.isBowMode()) modes.append("§bBOW ");
            if (config.isBridgeMode()) modes.append("§aBRIDGE ");
            if (config.isPvpMode()) modes.append("§4PVP ");
            if (config.isAutoMode()) modes.append("§dAUTO ");
            if (modes.length() > 3) {
                modes.setLength(modes.length() - 1);
            }
            modes.append("§7]");
            if (modes.length() > 3) {
                graphics.text(font, modes.toString(), x, y, TEXT_COLOR);
                y += LINE_HEIGHT;
            }

            // Target info
            IAimTarget target = module.getCurrentTarget();
            if (target != null && config.isShowTargetInfo()) {
                LivingEntity entity = target.getEntity();

                String name = entity instanceof Player ?
                    ((Player) entity).getName().getString() :
                    entity.getType().getDescription().getString();

                float health = target.getHealth();
                float maxHealth = target.getMaxHealth();
                String healthStr = health > 0 ?
                    String.format("§a%.0f§7/§a%.0f", health, maxHealth) :
                    "§cDEAD";

                graphics.text(font, "§7Target: §f" + name, x, y, TEXT_COLOR);
                y += LINE_HEIGHT;
                graphics.text(font, "§7HP: " + healthStr + " §7| §7Dist: §f" + String.format("%.1f", target.getDistance()), x, y, TEXT_COLOR);
                y += LINE_HEIGHT;

                int combo = module.getComboTracker().getComboCount(entity);
                if (combo > 1) {
                    graphics.text(font, "§7Combo: §6" + combo + "x", x, y, TEXT_COLOR);
                    y += LINE_HEIGHT;
                }

                if (config.isMaceMode()) {
                    MaceAssist mace = module.getMaceAssist();
                    double smashDmg = mace.calculateSmashDamage(mc.player.fallDistance);
                    graphics.text(font, "§7Mace: §f" + String.format("%.1f", smashDmg) + " §7dmg", x, y, TEXT_COLOR);
                    y += LINE_HEIGHT;
                }

                if (config.isBowMode()) {
                    int drawTicks = module.getBowAssist().getDrawTicks();
                    graphics.text(font, "§7Bow Draw: §f" + drawTicks + "t", x, y, TEXT_COLOR);
                    y += LINE_HEIGHT;
                }
            }

            // Survival status
            if (config.isShowTargetInfo()) {
                int food = mc.player.getFoodData().getFoodLevel();
                float health = mc.player.getHealth();
                int air = mc.player.getAirSupply();
                int maxAir = mc.player.getMaxAirSupply();
                graphics.text(font, "§7HP: §a" + String.format("%.0f", health) + " §7Food: §e" + food + " §7Air: §b" + (air * 100 / maxAir) + "%", x, y, TEXT_COLOR);
                y += LINE_HEIGHT;
            }

            int activeCombos = module.getComboTracker().getActiveCombos();
            if (activeCombos > 0) {
                graphics.text(font, "§7Active Combos: §f" + activeCombos, x, y, TEXT_COLOR);
                y += LINE_HEIGHT;
            }

            // Durability warning
            var mainHand = mc.player.getMainHandItem();
            if (mainHand.isDamageableItem()) {
                double dur = 1.0 - (double) mainHand.getDamageValue() / mainHand.getMaxDamage();
                if (dur < 0.3) {
                    String color = dur < 0.1 ? "§c" : "§e";
                    graphics.text(font, color + "Durability: " + String.format("%.0f%%", dur * 100), x, y, TEXT_COLOR);
                    y += LINE_HEIGHT;
                }
            }
        } catch (Exception e) {
            // Silent fail - overlay must never crash
        }
    }
}
