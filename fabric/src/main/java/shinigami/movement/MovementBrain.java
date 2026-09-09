package shinigami.movement;

import shinigami.AimAssistConfig;
import shinigami.AimAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

public class MovementBrain {
    private final Minecraft mc = Minecraft.getInstance();
    private final Random random = new Random();

    private int momentumTicks = 0;
    private int strafeDirection = 1;
    private int strafeSwitchTicks = 0;
    private int microOscillationTick = 0;
    private int actionBufferTick = 0;
    private int actionBufferPhase = 0;
    private int noDamageTicks = 0;
    private float lastHealth = 20f;

    private static final int STRAFE_INTERVAL_MIN = 5;
    private static final int STRAFE_INTERVAL_MAX = 10;
    private static final double MICRO_NOISE_AMOUNT = 0.015;

    public enum CombatFlow {
        APPROACH(0.6, 1.0,  0.2,  0.0),
        PRESSURE(0.8, 0.9,  0.3,  0.4),
        EVADE(   0.2, 0.4,  0.7,  0.0),
        RESET(   0.3, 0.3,  0.6,  0.0),
        BURST(   1.0, 1.0,  0.1,  0.9),
        FINISH(  1.0, 1.0,  0.1,  1.0),
        RETREAT( 0.0, 0.1,  0.8,  0.0);

        public final double aggression;
        public final double sprintChance;
        public final double dodgeSensitivity;
        public final double critFocus;

        CombatFlow(double a, double s, double d, double c) {
            this.aggression = a;
            this.sprintChance = s;
            this.dodgeSensitivity = d;
            this.critFocus = c;
        }
    }

    private CombatFlow currentFlow = CombatFlow.APPROACH;
    private int flowTicks = 0;

    public MovementBrain() {}

    public void tick() {
        momentumTicks++;
        microOscillationTick++;
        strafeSwitchTicks++;
        if (actionBufferTick > 0) actionBufferTick--;

        if (mc.player != null) {
            float health = mc.player.getHealth();
            if (health < lastHealth) noDamageTicks = 0;
            else noDamageTicks++;
            lastHealth = health;
        }

        flowTicks++;
        updateFlow();
    }

    private void updateFlow() {
        AimAssistModule module = AimAssistModule.getInstance();
        if (mc.player == null) return;

        var target = module.getCurrentTarget();
        boolean hasTarget = target != null && target.getEntity() != null && target.getEntity().isAlive();
        double dist = hasTarget ? mc.player.distanceTo(target.getEntity()) : 64;
        float myHealth = mc.player.getHealth();
        float maxHealth = mc.player.getMaxHealth();
        float healthRatio = myHealth / maxHealth;
        int combo = hasTarget ? module.getComboTracker().getComboCount(target.getEntity()) : 0;
        boolean targetLow = hasTarget && target.getHealth() < 6;

        if (!hasTarget || dist > 32) {
            setFlow(CombatFlow.APPROACH);
        } else if (myHealth < 2) {
            setFlow(CombatFlow.RETREAT);
        } else if (myHealth < maxHealth * 0.08) {
            // Only evade at very low HP (was 20%)
            setFlow(CombatFlow.EVADE);
        } else if (targetLow) {
            setFlow(CombatFlow.FINISH);
        } else if (dist < 4 && combo >= 2) {
            setFlow(CombatFlow.BURST);
        } else if (dist < 3 && healthRatio > 0.4) {
            setFlow(CombatFlow.BURST);
        } else if (noDamageTicks < 15 && hasTarget && dist < 8) {
            setFlow(CombatFlow.PRESSURE);
        } else if (dist < 10 && combo >= 1) {
            setFlow(CombatFlow.PRESSURE);
        } else if (dist < 16) {
            setFlow(CombatFlow.APPROACH);
        } else if (flowTicks > 60) {
            setFlow(CombatFlow.APPROACH);
        } else {
            setFlow(CombatFlow.APPROACH);
        }
    }

    private void setFlow(CombatFlow newFlow) {
        if (currentFlow != newFlow) {
            currentFlow = newFlow;
            flowTicks = 0;
            if (newFlow == CombatFlow.BURST || newFlow == CombatFlow.FINISH) {
                scheduleActionBuffer(true, true, true);
            }
        }
    }

    public CombatFlow getCurrentFlow() { return currentFlow; }
    public int getFlowTicks() { return flowTicks; }

    public void reset() {
        momentumTicks = 0;
        actionBufferTick = 0;
        actionBufferPhase = 0;
        currentFlow = CombatFlow.APPROACH;
        flowTicks = 0;
    }

    public double getAggressionFactor() {
        return currentFlow.aggression;
    }

    public double getDodgeSensitivity() {
        return currentFlow.dodgeSensitivity;
    }

    public Vec3 addStrafeNoise(Vec3 baseDir) {
        if (baseDir == null || baseDir.lengthSqr() < 0.01) return baseDir;
        double strafeStrength = currentFlow.aggression * 0.35;
        if (strafeStrength < 0.01) return baseDir;

        int interval = STRAFE_INTERVAL_MIN + (int)((1.0 - currentFlow.aggression) * (STRAFE_INTERVAL_MAX - STRAFE_INTERVAL_MIN));
        if (strafeSwitchTicks > interval) {
            strafeDirection *= -1;
            strafeSwitchTicks = 0;
        }

        Vec3 perp = new Vec3(-baseDir.z, 0, baseDir.x);
        return new Vec3(
            baseDir.x + perp.x * strafeStrength * strafeDirection,
            0,
            baseDir.z + perp.z * strafeStrength * strafeDirection
        ).normalize();
    }

    public Vec3 addMicroNoise(Vec3 dir) {
        if (dir == null || dir.lengthSqr() < 0.01) return dir;
        if (microOscillationTick % 3 != 0) return dir;
        double noiseScale = (0.3 + random.nextDouble() * 0.7) * MICRO_NOISE_AMOUNT;
        double noiseX = (random.nextDouble() - 0.5) * 2 * noiseScale;
        double noiseZ = (random.nextDouble() - 0.5) * 2 * noiseScale;
        return new Vec3(dir.x + noiseX, 0, dir.z + noiseZ).normalize();
    }

    public MovementIntent.JumpType classifyJump(double distanceToTarget, boolean targetAbove, boolean gapAhead,
                                                  boolean obstacleAhead, boolean isInCombat) {
        if (gapAhead) return MovementIntent.JumpType.GAP_JUMP;
        if (obstacleAhead) return MovementIntent.JumpType.CLIMB_JUMP;
        if (targetAbove && distanceToTarget < 10) return MovementIntent.JumpType.TOWER_JUMP;
        if (currentFlow == CombatFlow.BURST || currentFlow == CombatFlow.FINISH) return MovementIntent.JumpType.COMBAT_HOP;
        if (isInCombat && currentFlow == CombatFlow.PRESSURE) return MovementIntent.JumpType.COMBAT_HOP;
        if (momentumTicks % 20 < 2 && mc.player != null && mc.player.onGround()) return MovementIntent.JumpType.MICRO_HOP;
        return MovementIntent.JumpType.NONE;
    }

    public int getActionBufferPhase() { return actionBufferPhase; }

    public void scheduleActionBuffer(boolean needsSprintCancel, boolean needsJump, boolean needsAttack) {
        actionBufferPhase = 0;
        actionBufferTick = 3;
    }

    public void advanceActionBuffer(Runnable onSprintCancel, Runnable onJump, Runnable onAttack) {
        if (actionBufferTick <= 0) return;
        actionBufferTick--;
        if (actionBufferPhase == 0 && actionBufferTick == 2) {
            if (onSprintCancel != null) onSprintCancel.run();
            actionBufferPhase = 1;
        } else if (actionBufferPhase == 1 && actionBufferTick == 1) {
            if (onJump != null) onJump.run();
            actionBufferPhase = 2;
        } else if (actionBufferPhase == 2 && actionBufferTick == 0) {
            if (onAttack != null) onAttack.run();
            actionBufferPhase = 3;
        }
    }

    public boolean isMoving() {
        if (mc.player == null) return false;
        Vec3 vel = mc.player.getDeltaMovement();
        return vel.x * vel.x + vel.z * vel.z > 0.01;
    }
}
