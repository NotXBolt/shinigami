package baritone.aimassist;

import baritone.api.aimassist.IAimConfig;
import baritone.api.aimassist.IAimAssist.Mode;
import baritone.aimassist.system.AreaManager.Area;
import baritone.aimassist.system.DurabilityManager.RepairMode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AimAssistConfig implements IAimConfig {

    private static AimAssistConfig INSTANCE;

    // --- General ---
    private double range = 3.0;
    private double fov = 120.0;
    private boolean targetPlayers = true;
    private boolean targetHostile = false;
    private boolean targetPassive = false;
    private boolean targetInvisible = false;
    private boolean requireLineOfSight = false;
    private boolean checkBehindWalls = false;

    // --- Aim ---
    private double aimSpeed = 1.0;
    private double aimSpeedX = 1.0;
    private double aimSpeedY = 0.8;
    private double smoothing = 0.5;
    private double predictAmount = 1.0;
    private boolean aimOnClick = false;
    private boolean aimOnAttack = false;
    private boolean dynamicAimSpeed = true;

    // --- Trigger Bot ---
    private boolean triggerBot = false;
    private int triggerDelay = 0;
    private boolean triggerOnlyPlayers = false;
    private double triggerRange = 6.0;
    private boolean clicking = false;

    // --- Prediction ---
    private boolean predictMovement = true;
    private boolean predictBulletDrop = true;
    private boolean predictJump = true;
    private int predictionTicks = 2;
    private double predictionConfidence = 0.5;

    // --- Mace ---
    private boolean maceAssist = true;
    private boolean autoSmash = true;
    private double minSmashHeight = 2.0;
    private boolean windBurstTracking = true;

    // --- Visuals ---
    private boolean showHUD = true;
    private boolean showTargetInfo = true;
    private boolean showPrediction = false;
    private boolean showTrajectory = false;
    private int hudX = 5;
    private int hudY = 5;

    // --- Anti-Cheat (EZ Mode) ---
    private boolean silentAim = true;
    private double randomization = 0.5;
    private int minRotationChange = 1;
    private boolean humanMouseSimulation = true;
    private boolean antiWallBang = true;
    private boolean rotationSpoof = true;
    private int rotationSpoofTicks = 2;

    // --- Targeting ---
    private String priorityMode = "distance";
    private ButtonType sortButton = ButtonType.NONE;
    private double detectionRange = 64.0;
    public double getDetectionRange() { return detectionRange; }
    public void setDetectionRange(double r) { this.detectionRange = r; }

    // --- Modes ---
    private Mode mode = Mode.DEMON;
    private boolean enabled = false;

    // --- Sub-modes ---
    private boolean critMode = false;
    private boolean comboMode = false;
    private boolean maceMode = false;
    private boolean bowMode = false;
    private boolean bridgeMode = false;
    private boolean autoMode = false;
    private boolean pvpMode = false;

    public static AimAssistConfig getInstance() {
        if (INSTANCE == null) INSTANCE = new AimAssistConfig();
        return INSTANCE;
    }

    public void applyDemonPreset() {
        this.mode = Mode.DEMON;
        this.silentAim = false;
        this.randomization = 0;
        this.humanMouseSimulation = false;
        this.antiWallBang = false;
        this.rotationSpoof = false;
        this.aimSpeed = 3.0;
        this.smoothing = 0.1;
        this.predictionTicks = 4;
        this.dynamicAimSpeed = false;
    }

    public void applyEZPreset() {
        this.mode = Mode.EZ;
        this.silentAim = true;
        this.randomization = 1.5;
        this.humanMouseSimulation = true;
        this.antiWallBang = true;
        this.rotationSpoof = true;
        this.rotationSpoofTicks = 3;
        this.aimSpeed = 0.7;
        this.smoothing = 0.8;
        this.predictionTicks = 1;
        this.dynamicAimSpeed = true;
    }

    @Override public double getRange() { return range; }
    @Override public void setRange(double range) { this.range = range; }
    @Override public double getFOV() { return fov; }
    @Override public void setFOV(double fov) { this.fov = fov; }
    @Override public boolean isTargetPlayers() { return targetPlayers; }
    @Override public void setTargetPlayers(boolean target) { this.targetPlayers = target; }
    @Override public boolean isTargetHostile() { return targetHostile; }
    @Override public void setTargetHostile(boolean target) { this.targetHostile = target; }
    @Override public boolean isTargetPassive() { return targetPassive; }
    @Override public void setTargetPassive(boolean target) { this.targetPassive = target; }
    @Override public boolean isTargetInvisible() { return targetInvisible; }
    @Override public void setTargetInvisible(boolean target) { this.targetInvisible = target; }
    @Override public boolean isRequireLineOfSight() { return requireLineOfSight; }
    @Override public void setRequireLineOfSight(boolean los) { this.requireLineOfSight = los; }
    @Override public boolean isCheckBehindWalls() { return checkBehindWalls; }
    @Override public void setCheckBehindWalls(boolean check) { this.checkBehindWalls = check; }

    @Override public double getAimSpeed() { return aimSpeed; }
    @Override public void setAimSpeed(double speed) { this.aimSpeed = speed; }
    @Override public double getAimSpeedX() { return aimSpeedX; }
    @Override public void setAimSpeedX(double speed) { this.aimSpeedX = speed; }
    @Override public double getAimSpeedY() { return aimSpeedY; }
    @Override public void setAimSpeedY(double speed) { this.aimSpeedY = speed; }
    @Override public double getSmoothing() { return smoothing; }
    @Override public void setSmoothing(double s) { this.smoothing = s; }
    @Override public double getPredictAmount() { return predictAmount; }
    @Override public void setPredictAmount(double a) { this.predictAmount = a; }
    @Override public boolean isAimOnClick() { return aimOnClick; }
    @Override public void setAimOnClick(boolean c) { this.aimOnClick = c; }
    @Override public boolean isAimOnAttack() { return aimOnAttack; }
    @Override public void setAimOnAttack(boolean a) { this.aimOnAttack = a; }
    @Override public boolean isDynamicAimSpeed() { return dynamicAimSpeed; }
    @Override public void setDynamicAimSpeed(boolean d) { this.dynamicAimSpeed = d; }

    @Override public boolean isTriggerBot() { return triggerBot; }
    @Override public void setTriggerBot(boolean t) { this.triggerBot = t; }
    @Override public int getTriggerDelay() { return triggerDelay; }
    @Override public void setTriggerDelay(int d) { this.triggerDelay = d; }
    @Override public boolean isTriggerOnlyPlayers() { return triggerOnlyPlayers; }
    @Override public void setTriggerOnlyPlayers(boolean t) { this.triggerOnlyPlayers = t; }
    @Override public double getTriggerRange() { return triggerRange; }
    @Override public void setTriggerRange(double r) { this.triggerRange = r; }
    @Override public boolean isClicking() { return clicking; }
    @Override public void setClicking(boolean c) { this.clicking = c; }

    @Override public boolean isPredictMovement() { return predictMovement; }
    @Override public void setPredictMovement(boolean p) { this.predictMovement = p; }
    @Override public boolean isPredictBulletDrop() { return predictBulletDrop; }
    @Override public void setPredictBulletDrop(boolean p) { this.predictBulletDrop = p; }
    @Override public boolean isPredictJump() { return predictJump; }
    @Override public void setPredictJump(boolean p) { this.predictJump = p; }
    @Override public int getPredictionTicks() { return predictionTicks; }
    @Override public void setPredictionTicks(int t) { this.predictionTicks = t; }
    @Override public double getPredictionConfidence() { return predictionConfidence; }
    @Override public void setPredictionConfidence(double c) { this.predictionConfidence = c; }

    @Override public boolean isMaceAssist() { return maceAssist; }
    @Override public void setMaceAssist(boolean m) { this.maceAssist = m; }
    @Override public boolean isAutoSmash() { return autoSmash; }
    @Override public void setAutoSmash(boolean a) { this.autoSmash = a; }
    @Override public double getMinSmashHeight() { return minSmashHeight; }
    @Override public void setMinSmashHeight(double h) { this.minSmashHeight = h; }
    @Override public boolean isWindBurstTracking() { return windBurstTracking; }
    @Override public void setWindBurstTracking(boolean w) { this.windBurstTracking = w; }

    @Override public boolean isShowHUD() { return showHUD; }
    @Override public void setShowHUD(boolean s) { this.showHUD = s; }
    @Override public boolean isShowTargetInfo() { return showTargetInfo; }
    @Override public void setShowTargetInfo(boolean s) { this.showTargetInfo = s; }
    @Override public boolean isShowPrediction() { return showPrediction; }
    @Override public void setShowPrediction(boolean s) { this.showPrediction = s; }
    @Override public boolean isShowTrajectory() { return showTrajectory; }
    @Override public void setShowTrajectory(boolean s) { this.showTrajectory = s; }
    @Override public int getHudX() { return hudX; }
    @Override public void setHudX(int x) { this.hudX = x; }
    @Override public int getHudY() { return hudY; }
    @Override public void setHudY(int y) { this.hudY = y; }

    @Override public boolean isSilentAim() { return silentAim; }
    @Override public void setSilentAim(boolean s) { this.silentAim = s; }
    @Override public double getRandomization() { return randomization; }
    @Override public void setRandomization(double r) { this.randomization = r; }
    @Override public int getMinRotationChange() { return minRotationChange; }
    @Override public void setMinRotationChange(int m) { this.minRotationChange = m; }
    @Override public boolean isHumanMouseSimulation() { return humanMouseSimulation; }
    @Override public void setHumanMouseSimulation(boolean h) { this.humanMouseSimulation = h; }
    @Override public boolean isAntiWallBang() { return antiWallBang; }
    @Override public void setAntiWallBang(boolean a) { this.antiWallBang = a; }
    @Override public boolean isRotationSpoof() { return rotationSpoof; }
    @Override public void setRotationSpoof(boolean r) { this.rotationSpoof = r; }
    @Override public int getRotationSpoofTicks() { return rotationSpoofTicks; }
    @Override public void setRotationSpoofTicks(int t) { this.rotationSpoofTicks = t; }

    @Override public String getPriorityMode() { return priorityMode; }
    @Override public void setPriorityMode(String m) { this.priorityMode = m; }
    @Override public ButtonType getSortButton() { return sortButton; }
    @Override public void setSortButton(ButtonType b) { this.sortButton = b; }

    public boolean isCritMode() { return critMode; }
    public void setCritMode(boolean b) { this.critMode = b; }
    public boolean isComboMode() { return comboMode; }
    public void setComboMode(boolean b) { this.comboMode = b; }
    public boolean isMaceMode() { return maceMode; }
    public void setMaceMode(boolean b) { this.maceMode = b; }
    public boolean isBowMode() { return bowMode; }
    public void setBowMode(boolean b) { this.bowMode = b; }
    public boolean isBridgeMode() { return bridgeMode; }
    public void setBridgeMode(boolean b) { this.bridgeMode = b; }
    public boolean isAutoMode() { return autoMode; }
    public void setAutoMode(boolean b) { this.autoMode = b; }
    public boolean isPvpMode() { return pvpMode; }
    public void setPvpMode(boolean b) { this.pvpMode = b; }
    public Mode getModeType() { return mode; }
    public void setModeType(Mode m) { this.mode = m; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    // movementMode - use WASD keys for chase/follow
    private boolean movementMode = true;
    public boolean isMovementMode() { return movementMode; }
    public void setMovementMode(boolean m) { this.movementMode = m; }

    // autoDodge
    private boolean autoDodge = true;
    public boolean isAutoDodge() { return autoDodge; }
    public void setAutoDodge(boolean d) { this.autoDodge = d; }

    // autoEat
    private boolean autoEat = true;
    public boolean isAutoEat() { return autoEat; }
    public void setAutoEat(boolean a) { this.autoEat = a; }

    // autoHeal
    private boolean autoHeal = true;
    public boolean isAutoHeal() { return autoHeal; }
    public void setAutoHeal(boolean a) { this.autoHeal = a; }

    // autoClutch
    private boolean autoClutch = true;
    public boolean isAutoClutch() { return autoClutch; }
    public void setAutoClutch(boolean a) { this.autoClutch = a; }

    // superAim - demon mode extra power
    private boolean superAim = true;
    public boolean isSuperAim() { return superAim; }
    public void setSuperAim(boolean a) { this.superAim = a; }

    // --- Sprint ---
    private boolean sprintInWater = false;
    public boolean isSprintInWater() { return sprintInWater; }
    public void setSprintInWater(boolean s) { this.sprintInWater = s; }

    // --- Chase mode (referenced by AutoUtil) ---
    private boolean chaseMode = false;
    public boolean isChaseMode() { return chaseMode; }
    public void setChaseMode(boolean c) { this.chaseMode = c; }

    // --- Flee mode ---
    private boolean fleeMode = false;
    public boolean isFleeMode() { return fleeMode; }
    public void setFleeMode(boolean f) { this.fleeMode = f; }

    // --- Durability Management ---
    private double durabilityThreshold = 0.15;
    private RepairMode repairMode = RepairMode.AUTO;
    private boolean xpFarmEnabled = false;

    public double getDurabilityThreshold() { return durabilityThreshold; }
    public void setDurabilityThreshold(double t) { this.durabilityThreshold = t; }
    public RepairMode getRepairMode() { return repairMode; }
    public void setRepairMode(RepairMode m) { this.repairMode = m; }
    public boolean isXpFarmEnabled() { return xpFarmEnabled; }
    public void setXpFarmEnabled(boolean e) { this.xpFarmEnabled = e; }

    // --- Underwater Breathing ---
    private boolean autoSurface = true;
    private int breathMargin = 3;

    public boolean isAutoSurface() { return autoSurface; }
    public void setAutoSurface(boolean a) { this.autoSurface = a; }
    public int getBreathMargin() { return breathMargin; }
    public void setBreathMargin(int m) { this.breathMargin = m; }

    // --- Area Management ---
    private final Map<String, Area> areas = new ConcurrentHashMap<>();

    public void setArea(String name, Area area) { areas.put(name.toLowerCase(), area); }
    public Area getArea(String name) { return areas.get(name.toLowerCase()); }
    public boolean removeArea(String name) { return areas.remove(name.toLowerCase()) != null; }
    public Map<String, Area> getAllAreas() { return new HashMap<>(areas); }
}
