package shinigami.config;

/**
 * ShinigamiConfig — Total from scratch, original, extensive. Not copy, not Frankenstein.
 * Phase 0: chase + pakur + follow + dodge 360° — demon for sure, vanilla 3.0 exact, no labels.
 * Every field enforced via table, inspired by maple/Kiwi/Dodger/better-auto-jump but rewritten original.
 */
public class ShinigamiConfig {

    private static ShinigamiConfig INSTANCE;

    // General — original
    private double range = 3.0; // vanilla exact 3.0, never extended
    private double fov = 360.0; // 360° perfect, no blind behind
    private double detectionRange = 64.0;

    // Modes — Phase 0 retail: only Movement/Dodge/PvP, demon for sure
    private boolean enabled = true;
    private boolean movementMode = true;
    private boolean autoDodge = true;
    private boolean pvpMode = true;
    private boolean autoClutch = true;
    private boolean autoEat = true;
    private boolean autoHeal = true;

    // Sub-modes disabled for Phase 0 retail (chase/dodge only), enabled via GUI for Phase 1
    private boolean critMode = false;
    private boolean comboMode = false;
    private boolean maceMode = false;
    private boolean bowMode = false;

    // Targeting
    private boolean targetPlayers = true;
    private boolean targetHostile = true;
    private boolean targetPassive = true;
    private boolean targetInvisible = false;

    // Chase
    private String chaseTargetName = null;
    private boolean chaseKill = false;

    public static ShinigamiConfig getInstance() {
        if (INSTANCE == null) INSTANCE = new ShinigamiConfig();
        return INSTANCE;
    }

    public double getRange() { return range; }
    public void setRange(double v) { range = v; }
    public double getFOV() { return fov; }
    public void setFOV(double v) { fov = v; }
    public double getDetectionRange() { return detectionRange; }
    public void setDetectionRange(double v) { detectionRange = v; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
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
}
