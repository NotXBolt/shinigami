package shinigami.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import shinigami.config.ShinigamiConfig;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * ShinigamiScreen — Original, 26.1 compatible (GuiGraphicsExtractor + extractRenderState).
 * Single mod GUI — all settings in one place, each with hover description at bottom.
 * Tabs: MAIN (Crit/Combo/Mace/Bow + Movement/Dodge/PvP + Clutch/Eat/Heal + RL/Safe)
 *        AIM (Range/FOV/Detect + target filters)
 *        CHASE (hunt/chase/kill)
 *        SETTINGS (HUD, aim, learning)
 */
public class ShinigamiScreen extends Screen {
    private final Screen parent;
    private final ShinigamiConfig cfg = ShinigamiConfig.getInstance();
    private int tab = 0;
    private static final String[] TABS = {"MAIN","AIM","CHASE","SETTINGS"};
    private EditBox nameField;
    private String hoverDesc = "Hover a setting for description — Shinigami v1.0.5 single mod, all 22 repos adapted";

    public ShinigamiScreen(Screen parent) {
        super(Component.literal("Shinigami v1.0.5"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width/2;
        // header toggles
        addRenderableWidget(Button.builder(Component.literal(cfg.isEnabled() ? "§aON" : "§cOFF"),
            b -> { cfg.toggle(); b.setMessage(Component.literal(cfg.isEnabled() ? "§aON" : "§cOFF")); })
            .bounds(cx-50, 10, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().setScreen(parent))
            .bounds(cx+55, 10, 100, 18).build());
        int tabW = (width-20)/TABS.length;
        for (int i=0;i<TABS.length;i++) {
            int ti=i;
            addRenderableWidget(Button.builder(Component.literal((i==tab?"§l":"")+TABS[i]),
                b->{ tab=ti; rebuildWidgets();}).bounds(10+i*tabW,34,tabW-2,16).build());
        }
        int y=58; int h=20;
        switch(tab){
            case 0 -> addMain(cx,y,h);
            case 1 -> addAim(cx,y,h);
            case 2 -> addChase(cx,y,h);
            case 3 -> addSettings(cx,y,h);
        }
    }

    private void addMain(int cx,int y,int h){
        int b=52;
        addToggle(cx-b*2-6,y,"Crit",cfg.isCritMode(),cfg::setCritMode,b);
        addToggle(cx-b+2,y,"Combo",cfg.isComboMode(),cfg::setComboMode,b);
        addToggle(cx+4,y,"Mace",cfg.isMaceMode(),cfg::setMaceMode,b);
        addToggle(cx+b+10,y,"Bow",cfg.isBowMode(),cfg::setBowMode,b);
        addToggle(cx-80,y+h,"Movement",cfg.isMovementMode(),cfg::setMovementMode,75);
        addToggle(cx,y+h,"Dodge",cfg.isAutoDodge(),cfg::setAutoDodge,75);
        addToggle(cx+80,y+h,"PvP",cfg.isPvpMode(),cfg::setPvpMode,75);
        addToggle(cx-80,y+h*2,"Clutch",cfg.isAutoClutch(),cfg::setAutoClutch,75);
        addToggle(cx,y+h*2,"Eat",cfg.isAutoEat(),cfg::setAutoEat,75);
        addToggle(cx+80,y+h*2,"Heal",cfg.isAutoHeal(),cfg::setAutoHeal,75);
        addToggle(cx-80,y+h*3,"Safe",cfg.isSafeMode(),cfg::setSafeMode,75);
        addToggle(cx,y+h*3,"RL",cfg.isRlEnabled(),cfg::setRlEnabled,75);
        addToggle(cx+80,y+h*3,"HUD",cfg.isShowHUD(),cfg::setShowHUD,75);
    }
    private void addAim(int cx,int y,int h){
        addSlider(cx,y,"Range",cfg.getRange(),1,6,cfg::setRange);
        addSlider(cx,y+h,"Detect",cfg.getDetectionRange(),8,128,cfg::setDetectionRange);
        addSlider(cx,y+h*2,"FOV",cfg.getFOV(),30,360,cfg::setFOV);
        addSlider(cx,y+h*3,"Aim",cfg.getAimSpeed(),0.1,3,cfg::setAimSpeed);
        addSlider(cx,y+h*4,"Smooth",cfg.getSmoothing(),0,1,cfg::setSmoothing);
        addToggle(cx-80,y+h*5,"Players",cfg.isTargetPlayers(),cfg::setTargetPlayers,70);
        addToggle(cx,y+h*5,"Hostile",cfg.isTargetHostile(),cfg::setTargetHostile,70);
        addToggle(cx+80,y+h*5,"Passive",cfg.isTargetPassive(),cfg::setTargetPassive,70);
        addToggle(cx,y+h*6,"Invisible",cfg.isTargetInvisible(),cfg::setTargetInvisible,90);
    }
    private void addChase(int cx,int y,int h){
        nameField = new EditBox(Minecraft.getInstance().font, cx-100,y,200,16,Component.literal("Target"));
        nameField.setMaxLength(64);
        if (cfg.getChaseTargetName()!=null) nameField.setValue(cfg.getChaseTargetName());
        addRenderableWidget(nameField);
        int bw=50;
        addRenderableWidget(Button.builder(Component.literal("Chase"),b->{
            String n=nameField.getValue().trim(); if(!n.isEmpty()){
                var mod=shinigami.ShinigamiMod.getInstance(); if(mod!=null) mod.getChaseBehavior().setTarget(n,false);
                cfg.setChaseTargetName(n); cfg.setChaseMode(true); cfg.setChaseKill(false); cfg.setEnabled(true);
            }
        }).bounds(cx-bw*2-3,y+h,bw,16).build());
        addRenderableWidget(Button.builder(Component.literal("§cKill"),b->{
            String n=nameField.getValue().trim(); if(!n.isEmpty()){
                var mod=shinigami.ShinigamiMod.getInstance(); if(mod!=null) mod.getChaseBehavior().setTarget(n,true);
                cfg.setChaseTargetName(n); cfg.setChaseMode(true); cfg.setChaseKill(true); cfg.setEnabled(true); cfg.setPvpMode(true);
            }
        }).bounds(cx-bw+1,y+h,bw,16).build());
        addRenderableWidget(Button.builder(Component.literal("Follow"),b->{
            String n=nameField.getValue().trim(); if(!n.isEmpty()){
                var mod=shinigami.ShinigamiMod.getInstance(); if(mod!=null) mod.getChaseBehavior().setTarget(n,false);
                cfg.setChaseTargetName(n); cfg.setChaseMode(true); cfg.setEnabled(true);
            }
        }).bounds(cx+5,y+h,bw,16).build());
        addRenderableWidget(Button.builder(Component.literal("§cHunt"),b->{
            String n=nameField.getValue().trim(); if(!n.isEmpty()){
                var mod=shinigami.ShinigamiMod.getInstance(); if(mod!=null) mod.getChaseBehavior().setTarget(n,true);
                cfg.setChaseTargetName(n); cfg.setChaseMode(true); cfg.setChaseKill(true); cfg.setEnabled(true); cfg.setPvpMode(true); cfg.setCritMode(true);
            }
        }).bounds(cx+bw+9,y+h,bw,16).build());
        addRenderableWidget(Button.builder(Component.literal("§4Clear"),b->{
            var mod=shinigami.ShinigamiMod.getInstance(); if(mod!=null){ mod.getChaseBehavior().clear(); mod.getTargetManager().clear(); }
            cfg.setChaseMode(false); cfg.setChaseKill(false); cfg.setPvpMode(false); cfg.setEnabled(false); rebuildWidgets();
        }).bounds(cx-80,y+h*2,75,16).build());
        addRenderableWidget(Button.builder(Component.literal("§cAll Off"),b->{
            var mod=shinigami.ShinigamiMod.getInstance(); if(mod!=null){ mod.getChaseBehavior().clear(); mod.getTargetManager().clear(); }
            cfg.setEnabled(false); cfg.setChaseMode(false); cfg.setPvpMode(false); rebuildWidgets();
        }).bounds(cx+5,y+h*2,75,16).build());
        addRenderableWidget(Button.builder(Component.literal("Status"),b->{
            String chase=cfg.getChaseTargetName();
            try {
                if (Minecraft.getInstance().player!=null) Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                    "§6[Shinigami] "+(cfg.isEnabled()?"§aON":"§cOFF")+" Dodge:"+(cfg.isAutoDodge()?"§aON":"§cOFF")+(chase!=null?" §b"+chase:"")));
            } catch (Exception ignored) {}
        }).bounds(cx-40,y+h*3,80,16).build());
    }
    private void addSettings(int cx,int y,int h){
        addToggle(cx,y,"RL",cfg.isRlEnabled(),cfg::setRlEnabled,80);
        addToggle(cx+85,y,"Safe",cfg.isSafeMode(),cfg::setSafeMode,80);
        addToggle(cx,y+h,"HUD",cfg.isShowHUD(),cfg::setShowHUD,80);
        addToggle(cx+85,y+h,"ESP",cfg.isShowTargetInfo(),cfg::setShowTargetInfo,80);
        addSlider(cx,y+h*2,"LR",cfg.getRlLearningRate(),0.01,0.5,cfg::setRlLearningRate);
        addSlider(cx,y+h*3,"Gamma",cfg.getRlDiscount(),0.5,0.99,cfg::setRlDiscount);
        addSlider(cx,y+h*4,"Dura",cfg.getDurabilityThreshold(),0,1,cfg::setDurabilityThreshold);
    }

    private void addSlider(int cx,int y,String label,double value,double min,double max,DoubleConsumer setter){
        addRenderableWidget(new AbstractSliderButton(cx-80,y,160,16,Component.literal(label+": "+String.format("%.1f",value)),(value-min)/(max-min)){
            @Override protected void updateMessage(){ double v=min+(max-min)*this.value; setMessage(Component.literal("§7"+label+": §f"+String.format("%.1f",v))); }
            @Override protected void applyValue(){ setter.accept(min+(max-min)*this.value); }
        });
    }
    private void addToggle(int cx,int y,String label,boolean cur,Consumer<Boolean> setter,int w){
        addRenderableWidget(Button.builder(Component.literal((cur?"§a✔ ":"§7✗ ")+label), b->{ setter.accept(!cur); rebuildWidgets(); }).bounds(cx-w/2,y,w,16).build());
    }

    private String descFor(String label){
        return switch(label){
            case "Crit" -> "§7Crit: §f1-tick burst fall crit — jumps to land 0.848 damage (reflex)";
            case "Combo" -> "§7Combo: §fhit-chain 2+ in 900ms — W-tap timing (pvp-bot)";
            case "Mace" -> "§7Mace: §ffall smash >2 blocks + windburst tracking (pvp-bot)";
            case "Bow" -> "§7Bow: §fpredictive intercept 5 ticks — aims ahead (pvp-bot + Dodger)";
            case "Movement" -> "§7Movement: §fWASD supplement chase — direct yaw, no weave (Oogabooga)";
            case "Dodge" -> "§7Dodge: §f3-layer predictive RL — 19 threats, 1-block safe perp (Dodger)";
            case "PvP" -> "§7PvP: §ffull scenario mode — enables all combat when ON";
            case "Clutch" -> "§7Clutch: §fwater/hay/ladder raycast — saves from void (safe)";
            case "Eat" -> "§7Eat: §fauto eat when hunger < threshold — keeps sprint";
            case "Heal" -> "§7Heal: §fauto heal potion/gap when health low";
            case "Safe" -> "§7Safe: §fnever lose progress — avoids void/lava, aborts unsafe chase";
            case "RL" -> "§7RL: §fReinforcementLearner — Q-learning dodge, lr 0.1 gamma 0.9";
            case "HUD" -> "§7HUD: §fshow demon status crosshair";
            case "Range" -> "§7Range: §fattack range 1-6, vanilla 3.0 exact";
            case "Detect" -> "§7Detect: §fscan range 8-128, finds targets beyond attack";
            case "FOV" -> "§7FOV: §f360° perfect — no blind behind, always finds";
            case "Players" -> "§7Players: §ftarget players when ON";
            case "Hostile" -> "§7Hostile: §ftarget monsters when ON";
            case "Passive" -> "§7Passive: §ftarget animals — OFF avoids mob aggro";
            default -> hoverDesc;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        var font = Minecraft.getInstance().font;
        String title = "§l§6SHINIGAMI §7v1.0.5 " + (cfg.isEnabled() ? "§aON" : "§cOFF") + " §8— single mod, 22 repos";
        try {
            graphics.text(font, Component.literal(title),
                (width - font.width("SHINIGAMI v1.0.5")) / 2, 0, 0xFFFFFF);
        } catch (Exception ignored) {}
        // hover description — resolve before super.extractRenderState draws widgets
        String cur = hoverDesc;
        try {
            for (var w : children()) {
                if (w instanceof net.minecraft.client.gui.components.AbstractWidget aw) {
                    try {
                        if (!aw.isMouseOver(mouseX, mouseY)) continue;
                    } catch (Exception ignored) { continue; }
                    String lbl;
                    try { lbl = aw.getMessage().getString(); } catch (Exception ignored) { continue; }
                    lbl = lbl.replace("✔ ", "").replace("✗ ", "").replace("§a", "").replace("§7", "").replace("§c", "").trim();
                    for (String k : new String[]{"Crit","Combo","Mace","Bow","Movement","Dodge","PvP","Clutch","Eat","Heal","Safe","RL","HUD","Range","Detect","FOV","Players","Hostile","Passive"}) {
                        if (lbl.contains(k)) { cur = descFor(k); break; }
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            graphics.text(font, Component.literal(cur), width/2 - font.width(cur)/2, height-12, 0xAAAAAA);
            graphics.text(font, Component.literal("§8R=toggle §7| §8G=gui §7| §8Single mod — all repos via IntegrationRegistry"), width/2 - 140, height-24, 0x888888);
        } catch (Exception ignored) {}
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose(){ Minecraft.getInstance().setScreen(parent); }
}
