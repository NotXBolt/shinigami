package baritone.api.aimassist;

import baritone.api.aimassist.IAimAssist.Mode;

public interface IAimConfig {

    // --- General ---

    double getRange();

    void setRange(double range);

    double getFOV();

    void setFOV(double fov);

    boolean isTargetPlayers();

    void setTargetPlayers(boolean target);

    boolean isTargetHostile();

    void setTargetHostile(boolean target);

    boolean isTargetPassive();

    void setTargetPassive(boolean target);

    boolean isTargetInvisible();

    void setTargetInvisible(boolean target);

    boolean isRequireLineOfSight();

    void setRequireLineOfSight(boolean los);

    boolean isCheckBehindWalls();

    void setCheckBehindWalls(boolean check);

    // --- Aim ---

    double getAimSpeed();

    void setAimSpeed(double speed);

    double getAimSpeedX();

    void setAimSpeedX(double speed);

    double getAimSpeedY();

    void setAimSpeedY(double speed);

    double getSmoothing();

    void setSmoothing(double smoothing);

    double getPredictAmount();

    void setPredictAmount(double amount);

    boolean isAimOnClick();

    void setAimOnClick(boolean click);

    boolean isAimOnAttack();

    void setAimOnAttack(boolean attack);

    boolean isDynamicAimSpeed();

    void setDynamicAimSpeed(boolean dynamic);

    // --- Trigger Bot ---

    boolean isTriggerBot();

    void setTriggerBot(boolean trigger);

    int getTriggerDelay();

    void setTriggerDelay(int delay);

    boolean isTriggerOnlyPlayers();

    void setTriggerOnlyPlayers(boolean only);

    double getTriggerRange();

    void setTriggerRange(double range);

    boolean isClicking();

    void setClicking(boolean clicking);

    // --- Prediction ---

    boolean isPredictMovement();

    void setPredictMovement(boolean predict);

    boolean isPredictBulletDrop();

    void setPredictBulletDrop(boolean predict);

    boolean isPredictJump();

    void setPredictJump(boolean predict);

    int getPredictionTicks();

    void setPredictionTicks(int ticks);

    double getPredictionConfidence();

    void setPredictionConfidence(double confidence);

    // --- Mace ---

    boolean isMaceAssist();

    void setMaceAssist(boolean assist);

    boolean isAutoSmash();

    void setAutoSmash(boolean auto);

    double getMinSmashHeight();

    void setMinSmashHeight(double height);

    boolean isWindBurstTracking();

    void setWindBurstTracking(boolean track);

    // --- Visuals ---

    boolean isShowHUD();

    void setShowHUD(boolean show);

    boolean isShowTargetInfo();

    void setShowTargetInfo(boolean show);

    boolean isShowPrediction();

    void setShowPrediction(boolean show);

    boolean isShowTrajectory();

    void setShowTrajectory(boolean show);

    int getHudX();

    void setHudX(int x);

    int getHudY();

    void setHudY(int y);

    // --- Anti-Cheat (EZ Mode) ---

    boolean isSilentAim();

    void setSilentAim(boolean silent);

    double getRandomization();

    void setRandomization(double rand);

    int getMinRotationChange();

    void setMinRotationChange(int change);

    boolean isHumanMouseSimulation();

    void setHumanMouseSimulation(boolean sim);

    boolean isAntiWallBang();

    void setAntiWallBang(boolean anti);

    boolean isRotationSpoof();

    void setRotationSpoof(boolean spoof);

    int getRotationSpoofTicks();

    void setRotationSpoofTicks(int ticks);

    // --- Targeting ---

    String getPriorityMode();

    void setPriorityMode(String mode);

    ButtonType getSortButton();

    void setSortButton(ButtonType button);

    double getDetectionRange();

    void setDetectionRange(double range);

    enum ButtonType {
        NONE,
        LEFT,
        RIGHT,
        MIDDLE,
        SIDE1,
        SIDE2
    }
}
