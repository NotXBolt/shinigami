package baritone.aimassist.aim;

/**
 * PID (Proportional-Integral-Derivative) controller for smooth aim.
 * Used to calculate rotation adjustments that smoothly converge on the target.
 */
public class PIDController {
    
    private double kP;
    private double kI;
    private double kD;
    
    private double integral = 0;
    private double previousError = 0;
    private long lastTime = 0;
    private boolean initialized = false;
    
    private double minOutput = -180;
    private double maxOutput = 180;
    private double integralLimit = 100;
    
    public PIDController(double kP, double kI, double kD) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
    }
    
    public void setGains(double kP, double kI, double kD) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
    }
    
    public void setOutputLimits(double min, double max) {
        this.minOutput = min;
        this.maxOutput = max;
    }
    
    public void setIntegralLimit(double limit) {
        this.integralLimit = limit;
    }
    
    /**
     * Calculate the output given the current error.
     * Error = target - current (in degrees, normalized to [-180, 180]).
     */
    public double calculate(double error) {
        long now = System.nanoTime();
        
        if (!initialized) {
            previousError = error;
            lastTime = now;
            initialized = true;
            return 0;
        }
        
        double dt = (now - lastTime) / 1_000_000_000.0;
        if (dt <= 0) dt = 0.0001;
        if (dt > 0.1) dt = 0.1;
        
        double pTerm = kP * error;
        
        integral += error * dt;
        integral = Math.max(-integralLimit, Math.min(integralLimit, integral));
        double iTerm = kI * integral;
        
        double derivative = (error - previousError) / dt;
        double dTerm = kD * derivative;
        
        double output = pTerm + iTerm + dTerm;
        output = Math.max(minOutput, Math.min(maxOutput, output));
        
        previousError = error;
        lastTime = now;
        
        return output;
    }
    
    /**
     * Calculate yaw correction with proper angle wrapping.
     */
    public double calculateYaw(double currentYaw, double targetYaw) {
        double error = normalizeYaw(targetYaw - currentYaw);
        
        long now = System.nanoTime();
        double dt = (now - lastTime) / 1_000_000_000.0;
        if (dt <= 0) dt = 0.0001;
        if (dt > 0.1) dt = 0.1;
        
        double pTerm = kP * error;
        
        integral += error * dt;
        integral = Math.max(-integralLimit, Math.min(integralLimit, integral));
        double iTerm = kI * integral;
        
        double derivative = (error - previousError) / dt;
        double dTerm = kD * derivative;
        
        double output = pTerm + iTerm + dTerm;
        output = Math.max(minOutput, Math.min(maxOutput, output));
        
        previousError = error;
        lastTime = now;
        
        return output;
    }
    
    private double normalizeYaw(double yaw) {
        yaw = yaw % 360;
        if (yaw > 180) yaw -= 360;
        if (yaw < -180) yaw += 360;
        return yaw;
    }
    
    public void applyPerfectSmoothTuning() {
        // Perfect mouse-like tracking tuning: Kp=2.0, Ki=0.1, Kd=0.5
        // Tuned for smoothing=0.5, aimSpeed=1.0, minimal rotation changes (minRotationChange=1)
        setGains(2.0, 0.1, 0.5);
        setOutputLimits(-180, 180);
        setIntegralLimit(100);
    }

    public void reset() {
        integral = 0;
        previousError = 0;
        initialized = false;
    }
}
