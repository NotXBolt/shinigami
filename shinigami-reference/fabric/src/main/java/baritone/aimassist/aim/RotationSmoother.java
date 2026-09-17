package baritone.aimassist.aim;

import baritone.api.utils.Rotation;

/**
 * Provides rotation smoothing algorithms.
 * Supports linear, ease-in-out, sine, and bezier smoothing curves.
 */
public class RotationSmoother {
    
    public enum Curve {
        LINEAR,
        EASE_IN_OUT,
        SINE,
        BEZIER
    }
    
    /**
     * Interpolate between current and target rotation with smoothing.
     *
     * @param current Current rotation
     * @param target Target rotation
     * @param progress 0.0 to 1.0
     * @param curve Smoothing curve type
     * @return Interpolated rotation
     */
    public Rotation interpolate(Rotation current, Rotation target, float progress, Curve curve) {
        float t = applyCurve(progress, curve);
        
        float yaw = current.getYaw() + normalizeYawDelta(target.getYaw() - current.getYaw()) * t;
        float pitch = current.getPitch() + (target.getPitch() - current.getPitch()) * t;
        
        return new Rotation(yaw, pitch).clamp();
    }
    
    private float applyCurve(float t, Curve curve) {
        t = Math.max(0, Math.min(1, t));
        
        switch (curve) {
            case LINEAR:
                return t;
            case EASE_IN_OUT:
                return t < 0.5 ? 2 * t * t : (float) (1 - Math.pow(-2 * t + 2, 2) / 2);
            case SINE:
                return (float) (1 - Math.cos(t * Math.PI) / 2);
            case BEZIER:
                float t2 = t * t;
                float t3 = t2 * t;
                return 3 * (float) Math.pow(1 - t, 2) * t * 0.25f + 3 * (1 - t) * t2 * 0.25f + t3;
            default:
                return t;
        }
    }
    
    /**
     * Calculate the rotation step for this tick given current and target rotations.
     *
     * @param current Current player rotation
     * @param target Desired target rotation
     * @param speed Rotation speed (degrees per tick)
     * @param curve Smoothing curve
     * @return The rotation to set this tick
     */
    public Rotation stepTowards(Rotation current, Rotation target, float speed, Curve curve) {
        float yawDiff = normalizeYawDelta(target.getYaw() - current.getYaw());
        float pitchDiff = target.getPitch() - current.getPitch();
        
        float maxStep = Math.max(0.01f, speed);
        
        float yawStep = Math.abs(yawDiff) < maxStep ? yawDiff : Math.signum(yawDiff) * maxStep;
        float pitchStep = Math.abs(pitchDiff) < maxStep ? pitchDiff : Math.signum(pitchDiff) * maxStep;
        
        float curveFactor = applyCurve(Math.min(1, Math.abs(yawDiff) / 180), curve);
        yawStep *= curveFactor;
        
        curveFactor = applyCurve(Math.min(1, Math.abs(pitchDiff) / 90), curve);
        pitchStep *= curveFactor;
        
        return new Rotation(
            current.getYaw() + yawStep,
            current.getPitch() + pitchStep
        ).clamp();
    }
    
    public static float normalizeYawDelta(float delta) {
        while (delta > 180) delta -= 360;
        while (delta < -180) delta += 360;
        return delta;
    }
    
    /**
     * Add noise to rotation for anti-detection (EZ mode).
     */
    public Rotation addNoise(Rotation rotation, double noiseLevel) {
        if (noiseLevel <= 0) return rotation;
        
        long time = System.currentTimeMillis();
        double noiseYaw = Math.sin(time * 0.003) * noiseLevel * 0.3 
                        + Math.sin(time * 0.007) * noiseLevel * 0.2
                        + Math.sin(time * 0.013) * noiseLevel * 0.1;
        double noisePitch = Math.cos(time * 0.004) * noiseLevel * 0.3 
                          + Math.cos(time * 0.009) * noiseLevel * 0.2
                          + Math.cos(time * 0.015) * noiseLevel * 0.1;
        
        return new Rotation(
            rotation.getYaw() + (float) noiseYaw,
            rotation.getPitch() + (float) noisePitch
        ).clamp();
    }
}
