package shinigami.config;

/**
 * ShinigamiConfig — Total from scratch, original, extensive.
 * Phase 0+ : chase + pakur + dodge 360 + RL safe + GUI.
 * Defaults: enabled=false (no auto-run on world load), R to toggle.
 */
public class ShinigamiConfig {

    private static ShinigamiConfig INSTANCE;

    // General — vanilla exact
    private double range = 3.0;
    private double fov = 360.0;
    private double detectionRange = 64.0;

    // Modes — default OFF so world load doesn't auto-run into mobs
    private boolean enabled = false;
    private boolean movementMode = true;
    private boolean autoDodge = true;
    private boolean pvpMode = true;
    private boolean autoClutch = true;
    private boolean autoEat = true;
    private boolean autoHeal = true;

    // Combat sub-modes — all functional, enabled via GUI
    private boolean critMode = false;
    private boolean comboMode = false;
    private boolean maceMode = false;
    private boolean bowMode = false;

    // Targeting
    private boolean targetPlayers = true;
    private boolean targetHostile = true;
    private boolean targetPassive = false; // default false to avoid mob aggro unless hunt
    private boolean targetInvisible = false;

    // Chase
    private String chaseTargetName = null;
    private boolean chaseKill = false;
    private boolean chaseMode = false;

    // GUI extras
    private boolean showHUD = true;
    private boolean showTargetInfo = true;
    private double aimSpeed = 1.0;
    private double smoothing = 0.5;
    private double predictAmount = 1.0;

    // Safety / RL
    private boolean safeMode = true; // never lose progress — avoid void/lava, keep inventory
    private boolean rlEnabled = true;
    private double rlLearningRate = 0.1;
    private double rlDiscount = 0.9;

    // Durability
    private double durabilityThreshold = 0.15;
    private boolean xpFarmEnabled = false;

    public static ShinigamiConfig getInstance() {
        if (INSTANCE == null) INSTANCE = new ShinigamiConfig();
        return INSTANCE;
    }

    public double getRange() { return range; }
    public void setRange(double v) { range = Math.max(1, Math.min(6, v)); }
    public double getFOV() { return fov; }
    public void setFOV(double v) { fov = Math.max(30, Math.min(360, v)); }
    public double getDetectionRange() { return detectionRange; }
    public void setDetectionRange(double v) { detectionRange = Math.max(8, Math.min(128, v)); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public void toggle() { enabled = !enabled; }
    public boolean isMovementMode() { return movementMode; }
    public void setMovementMode(boolean v) { movementMode = v; }
    public boolean isAutoDodge() { return autoDodge; }
    public void setAutoDodge(boolean v) { autoDodge = v; }
    public boolean isPvpMode() { return pvpMode; }
    public void setPvpMode(boolean v) { pvpMode = v; }
    public boolean isAutoClutch() { return autoClutch; }
    public void setAutoClutch(boolean v) { autoClutch = v; }
    public boolean isAutoEat() { return autoEat; }
    public void setAutoEat(boolean v) { autoEat = v; }
    public boolean isAutoHeal() { return autoHeal; }
    public void setAutoHeal(boolean v) { autoHeal = v; }

    public boolean isCritMode() { return critMode; }
    public void setCritMode(boolean v) { critMode = v; }
    public boolean isComboMode() { return comboMode; }
    public void setComboMode(boolean v) { comboMode = v; }
    public boolean isMaceMode() { return maceMode; }
    public void setMaceMode(boolean v) { maceMode = v; }
    public boolean isBowMode() { return bowMode; }
    public void setBowMode(boolean v) { bowMode = v; }

    public boolean isTargetPlayers() { return targetPlayers; }
    public void setTargetPlayers(boolean v) { targetPlayers = v; }
    public boolean isTargetHostile() { return targetHostile; }
    public void setTargetHostile(boolean v) { targetHostile = v; }
    public boolean isTargetPassive() { return targetPassive; }
    public void setTargetPassive(boolean v) { targetPassive = v; }
    public boolean isTargetInvisible() { return targetInvisible; }
    public void setTargetInvisible(boolean v) { targetInvisible = v; }

    public String getChaseTargetName() { return chaseTargetName; }
    public void setChaseTargetName(String v) { chaseTargetName = v; }
    public boolean isChaseKill() { return chaseKill; }
    public void setChaseKill(boolean v) { chaseKill = v; }
    public boolean isChaseMode() { return chaseMode; }
    public void setChaseMode(boolean v) { chaseMode = v; }

    public boolean isShowHUD() { return showHUD; }
    public void setShowHUD(boolean v) { showHUD = v; }
    public boolean isShowTargetInfo() { return showTargetInfo; }
    public void setShowTargetInfo(boolean v) { showTargetInfo = v; }
    public double getAimSpeed() { return aimSpeed; }
    public void setAimSpeed(double v) { aimSpeed = v; }
    public double getSmoothing() { return smoothing; }
    public void setSmoothing(double v) { smoothing = v; }
    public double getPredictAmount() { return predictAmount; }
    public void setPredictAmount(double v) { predictAmount = v; }

    public boolean isSafeMode() { return safeMode; }
    public void setSafeMode(boolean v) { safeMode = v; }
    public boolean isRlEnabled() { return rlEnabled; }
    public void setRlEnabled(boolean v) { rlEnabled = v; }
    public double getRlLearningRate() { return rlLearningRate; }
    public void setRlLearningRate(double v) { rlLearningRate = v; }
    public double getRlDiscount() { return rlDiscount; }
    public void setRlDiscount(double v) { rlDiscount = v; }

    public double getDurabilityThreshold() { return durabilityThreshold; }
    public void setDurabilityThreshold(double v) { durabilityThreshold = Math.max(0, Math.min(1, v)); }
    public boolean isXpFarmEnabled() { return xpFarmEnabled; }
    public void setXpFarmEnabled(boolean v) { xpFarmEnabled = v; }
}
