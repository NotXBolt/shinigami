package baritone.api.aimassist;

public interface IAimAssist {

    enum Mode {
        DEMON,
        EZ
    }

    boolean isEnabled();

    void setEnabled(boolean enabled);

    void toggle();

    Mode getMode();

    void setMode(Mode mode);

    IAimConfig getConfig();

    boolean isTargeting();

    IAimTarget getCurrentTarget();
}
