package net.manaz.vtp.render;

/** A reversible, bounded transition; prediction data is never held back by the animation. */
public final class HudFade {
    public static final double OUT_SECONDS = 0.25, IN_SECONDS = 0.15;
    private double progress;
    public void reset() { progress = 0; }
    public float opacity() { return (float) (progress * progress * (3 - 2 * progress)); }
    public float advance(boolean visible, double seconds) {
        if (seconds > 0 && Double.isFinite(seconds)) {
            double step = seconds / (visible ? IN_SECONDS : OUT_SECONDS);
            progress = Math.clamp(progress + (visible ? step : -step), 0, 1);
        }
        return opacity();
    }
    /** Native item-atlas colors are premultiplied: both RGB and alpha must fade. */
    public static int itemTint(int color, float opacity) {
        float fade = Math.clamp(opacity, 0, 1);
        return Math.round((color >>> 24) * fade) << 24
                | Math.round((color >> 16 & 255) * fade) << 16
                | Math.round((color >> 8 & 255) * fade) << 8
                | Math.round((color & 255) * fade);
    }
}