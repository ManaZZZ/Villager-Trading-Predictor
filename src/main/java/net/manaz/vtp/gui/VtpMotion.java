package net.manaz.vtp.gui;

/** Time-based visual interpolation; never delays prediction data or input geometry. */
public final class VtpMotion {
    private VtpMotion() {}
    public static float approach(float current, float target, double seconds, double responseSeconds) {
        if (seconds <= 0) return current;
        float value = (float) (target + (current - target) * Math.exp(-seconds / responseSeconds));
        return Math.abs(value - target) < 0.001f ? target : value;
    }
    public static final class Value {
        private long lastNanos;
        private float value;
        public float sample(float target) {
            long now = System.nanoTime();
            double seconds = lastNanos == 0 ? 0 : Math.min(0.25, (now - lastNanos) / 1.0e9);
            lastNanos = now;
            value = approach(value, target, seconds, 0.045);
            return value;
        }
    }
}