package baritone.aimassist;

import baritone.api.aimassist.IAimAssist.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.Consumer;

public class AimAssistScreen extends Screen {

    private final Screen parent;
    private final AimAssistModule module = AimAssistModule.getInstance();
    private final AimAssistConfig config = AimAssistConfig.getInstance();

    private int tabIndex = 0;
    private static final String[] TABS = {"MAIN", "AIM", "CHASE", "SETTINGS"};

    private EditBox targetNameField;

    public AimAssistScreen(Screen parent) {
        super(Component.literal("Shinigami Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;

        addRenderableWidget(CycleButton.<Mode>builder(
            m -> Component.literal(m == Mode.DEMON ? "§cDEMON" : "§bEZ"),
            config.getModeType()
        ).withValues(Mode.values())
        .create(cx - 155, 10, 100, 18, Component.literal("Mode"), (btn, mode) -> module.setMode(mode)));

        addRenderableWidget(Button.builder(
            Component.literal(config.isEnabled() ? "§aON" : "§cOFF"),
            btn -> { module.toggle(); btn.setMessage(Component.literal(config.isEnabled() ? "§aON" : "§cOFF")); }
        ).bounds(cx - 50, 10, 100, 18).build());

        addRenderableWidget(Button.builder(
            Component.literal("Back"),
            btn -> Minecraft.getInstance().setScreen(parent)
        ).bounds(cx + 55, 10, 100, 18).build());

        int tabX = 10;
        int tabY = 34;
        int tabW = (width - 20) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int ti = i;
            addRenderableWidget(Button.builder(
                Component.literal((i == tabIndex ? "§l" : "") + TABS[i]),
                btn -> { tabIndex = ti; rebuildWidgets(); }
            ).bounds(tabX + i * tabW, tabY, tabW - 2, 16).build());
        }

        int y = 56;
        int row = 20;
        switch (tabIndex) {
            case 0 -> addMainTab(cx, y, row);
            case 1 -> addAimTab(cx, y, row);
            case 2 -> addChaseTab(cx, y, row);
            case 3 -> addSettingsTab(cx, y, row);
        }
    }

    // ═══════════ MAIN ═══════════

    private void addMainTab(int cx, int y, int h) {
        // Weapon row
        int b = 48;
        addToggle(cx - b * 3 - 4, y, "Crit", config.isCritMode(), config::setCritMode, b);
        addToggle(cx - b * 2 - 2, y, "Combo", config.isComboMode(), config::setComboMode, b);
        addToggle(cx - b, y, "Mace", config.isMaceMode(), config::setMaceMode, b);
        addToggle(cx + 2, y, "Bow", config.isBowMode(), config::setBowMode, b);
        addToggle(cx + b + 4, y, "Bridge", config.isBridgeMode(), config::setBridgeMode, b);

        // Movement + Dodge + PvP
        addToggle(cx - 75, y + h, "Movement", config.isMovementMode(), config::setMovementMode, 70);
        addToggle(cx, y + h, "Dodge", config.isAutoDodge(), config::setAutoDodge, 70);
        addToggle(cx + 75, y + h, "PvP", config.isPvpMode(), config::setPvpMode, 70);

        // Auto + Survival
        addToggle(cx - 100, y + h * 2, "Auto", config.isAutoMode(), config::setAutoMode, 60);
        addToggle(cx - 35, y + h * 2, "Eat", config.isAutoEat(), config::setAutoEat, 60);
        addToggle(cx + 30, y + h * 2, "Heal", config.isAutoHeal(), config::setAutoHeal, 60);
        addToggle(cx + 95, y + h * 2, "Clutch", config.isAutoClutch(), config::setAutoClutch, 60);

        // Flee + Farm
        addToggle(cx - 50, y + h * 3, "Flee", module.isFleeMode(), v -> module.setFleeMode(v), 90);
        addToggle(cx + 50, y + h * 3, "Farm", module.isFarmMode(), v -> module.setFarmMode(v), 90);
    }

    // ═══════════ AIM ═══════════

    private void addAimTab(int cx, int y, int h) {
        addSlider(cx, y, "Aim Speed", config.getAimSpeed(), 0.1, 10.0, v -> config.setAimSpeed(v));
        addSlider(cx, y + h, "Smoothing", config.getSmoothing(), 0.0, 1.0, v -> config.setSmoothing(v));
        addSlider(cx, y + h * 2, "Predict", config.getPredictAmount(), 0, 2.0, v -> config.setPredictAmount(v));
        addSlider(cx, y + h * 3, "Range", config.getRange(), 1, 6, v -> config.setRange(v));
        addSlider(cx, y + h * 4, "Detect", config.getDetectionRange(), 1, 64, v -> config.setDetectionRange(v));
        addSlider(cx, y + h * 5, "FOV", config.getFOV(), 30, 360, v -> config.setFOV(v));

        addToggle(cx, y + h * 6, "Silent", config.isSilentAim(), config::setSilentAim, 80);
        addToggle(cx + 85, y + h * 6, "Dynamic", config.isDynamicAimSpeed(), config::setDynamicAimSpeed, 80);

        addRenderableWidget(CycleButton.<String>builder(
            s -> Component.literal("§e" + s), config.getPriorityMode()
        ).withValues("distance", "hybrid", "angle", "health")
        .create(cx - 80, y + h * 7, 160, 18,
            Component.literal("Priority"), (btn, v) -> config.setPriorityMode(v)));

        addToggle(cx, y + h * 8, "Players", config.isTargetPlayers(), config::setTargetPlayers, 70);
        addToggle(cx + 75, y + h * 8, "Hostile", config.isTargetHostile(), config::setTargetHostile, 70);
        addToggle(cx - 75, y + h * 8, "Passive", config.isTargetPassive(), config::setTargetPassive, 70);
    }

    // ═══════════ CHASE ═══════════

    private void addChaseTab(int cx, int y, int h) {
        targetNameField = new EditBox(Minecraft.getInstance().font, cx - 100, y, 200, 16, Component.literal("Target"));
        targetNameField.setMaxLength(64);
        String prev = module.getChaseTargetName();
        if (prev != null) targetNameField.setValue(prev);
        addRenderableWidget(targetNameField);

        int b = 50;
        addRenderableWidget(Button.builder(Component.literal("Chase"), btn -> {
            String n = targetNameField.getValue().trim();
            if (!n.isEmpty()) { module.setCurrentChaseTarget(n); module.setChaseMode(true); module.setChaseKill(false); config.setMovementMode(true); module.setEnabled(true); }
        }).bounds(cx - b * 2 - 3, y + h, b, 16).build());

        addRenderableWidget(Button.builder(Component.literal("§cKill"), btn -> {
            String n = targetNameField.getValue().trim();
            if (!n.isEmpty()) { module.setCurrentChaseTarget(n); module.setChaseMode(true); module.setChaseKill(true); config.setMovementMode(true); module.setEnabled(true); config.setEnabled(true); }
        }).bounds(cx - b + 1, y + h, b, 16).build());

        addRenderableWidget(Button.builder(Component.literal("Follow"), btn -> {
            String n = targetNameField.getValue().trim();
            if (!n.isEmpty()) { module.setCurrentChaseTarget(n); module.setChaseMode(true); module.setChaseKill(false); config.setMovementMode(true); module.setEnabled(true); }
        }).bounds(cx + 5, y + h, b, 16).build());

        addRenderableWidget(Button.builder(Component.literal("§cHunt"), btn -> {
            String n = targetNameField.getValue().trim();
            if (!n.isEmpty()) { module.setCurrentChaseTarget(n); module.setChaseMode(true); module.setChaseKill(true); module.getChaseBehavior().setMobHunt(n); config.setMovementMode(true); module.setEnabled(true); config.setCritMode(true); config.setAutoEat(true); }
        }).bounds(cx + b + 9, y + h, b, 16).build());

        addRenderableWidget(Button.builder(Component.literal("§4Clear"), btn -> {
            module.getTargetManager().clearTarget(); module.getChaseBehavior().reset(); module.setChaseMode(false); module.setChaseKill(false); config.setPvpMode(false); config.setCritMode(false); config.setMaceMode(false); config.setComboMode(false); rebuildWidgets();
        }).bounds(cx - 80, y + h * 2, 75, 16).build());

        addRenderableWidget(Button.builder(Component.literal("§cAll Off"), btn -> {
            module.getTagSystem().clearTags(); module.getChaseBehavior().reset(); module.setChaseMode(false); module.setChaseKill(false); module.setFleeMode(false); module.setFarmMode(false); module.getTargetManager().clearTarget(); config.setPvpMode(false); config.setCritMode(false); config.setMaceMode(false); config.setComboMode(false); config.setAutoMode(false); module.setEnabled(false); rebuildWidgets();
        }).bounds(cx + 5, y + h * 2, 75, 16).build());

        addRenderableWidget(Button.builder(Component.literal("Status"), btn -> {
            String chase = module.getChaseTargetName();
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                "§6[Status] §fDodge:" + (config.isAutoDodge() ? "§aON" : "§cOFF")
                + " Move:" + (config.isMovementMode() ? "§aON" : "§cOFF")
                + " En:" + (config.isEnabled() ? "§aON" : "§cOFF")
                + (chase != null ? " §bChase:" + chase : "")));
        }).bounds(cx - 40, y + h * 3, 80, 16).build());
    }

    // ═══════════ SETTINGS ═══════════

    private void addSettingsTab(int cx, int y, int h) {
        // Visual
        addToggle(cx, y, "HUD", config.isShowHUD(), config::setShowHUD, 80);
        addToggle(cx + 85, y, "ESP", config.isShowTargetInfo(), config::setShowTargetInfo, 80);

        // Anti-cheat
        addToggle(cx, y + h, "Silent", config.isSilentAim(), config::setSilentAim, 80);
        addSlider(cx, y + h * 2, "Randomize", config.getRandomization(), 0, 5.0, v -> config.setRandomization(v));
        addToggle(cx, y + h * 3, "Human Mouse", config.isHumanMouseSimulation(), config::setHumanMouseSimulation, 100);
        addToggle(cx + 105, y + h * 3, "Spoof", config.isRotationSpoof(), config::setRotationSpoof, 80);

        // Trigger bot
        addToggle(cx, y + h * 4, "Trigger", config.isTriggerBot(), config::setTriggerBot, 80);
        addSlider(cx, y + h * 5, "T-Delay", config.getTriggerDelay(), 0, 20, v -> config.setTriggerDelay((int)v));
        addSlider(cx, y + h * 6, "T-Range", config.getTriggerRange(), 1, 10, v -> config.setTriggerRange(v));

        // Misc
        addToggle(cx, y + h * 7, "Mace Assist", config.isMaceAssist(), config::setMaceAssist, 100);
        addSlider(cx, y + h * 8, "Smash H", config.getMinSmashHeight(), 0, 10, v -> config.setMinSmashHeight(v));
        addToggle(cx, y + h * 9, "Sprint H2O", config.isSprintInWater(), config::setSprintInWater, 100);
        addToggle(cx + 105, y + h * 9, "Surface", config.isAutoSurface(), config::setAutoSurface, 80);
    }

    // ═══════════ HELPERS ═══════════

    private void addSlider(int cx, int y, String label, double value, double min, double max, DoubleConsumer setter) {
        addRenderableWidget(new AbstractSliderButton(cx - 80, y, 160, 16,
            Component.literal(label + ": " + String.format("%.1f", value)), (value - min) / (max - min)) {
            @Override
            protected void updateMessage() {
                double val = min + (max - min) * this.value;
                setMessage(Component.literal("§7" + label + ": §f" + String.format("%.1f", val)));
            }
            @Override
            protected void applyValue() { setter.accept(min + (max - min) * this.value); }
        });
    }

    private void addToggle(int cx, int y, String label, boolean current, Consumer<Boolean> setter, int w) {
        addRenderableWidget(Button.builder(
            Component.literal((current ? "§a✔ " : "§7✗ ") + label),
            btn -> { setter.accept(!current); rebuildWidgets(); }
        ).bounds(cx - w / 2, y, w, 16).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        var font = Minecraft.getInstance().font;
        graphics.text(font, Component.literal("§l§6SHINIGAMI §7" + (config.isEnabled() ? "§aON" : "§cOFF")),
            (width - font.width("SHINIGAMI")) / 2, 0, 0xFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
