package net.manaz.vtp.render;

/** Manual size multiplies Minecraft's GUI scale; screen margins stay in GUI pixels. */
public final class HudSize {
    public static final int MIN = 50, MAX = 200, DEFAULT = 100;
    private static int percent = DEFAULT;
    private HudSize() {}
    public static int percent() { return percent; }
    public static void set(int value) { percent = Math.clamp(value, MIN, MAX); }

    public record Layout(float scale, int panelWidth, int left, int right, int top, int bottom) {}

    public static Layout layout(int screenWidth, int screenHeight, int mainHeight) {
        return layout(screenWidth, screenHeight, mainHeight, percent);
    }
    public static Layout layout(int screenWidth, int screenHeight, int mainHeight, int percent) {
        percent = Math.clamp(percent, MIN, MAX);
        // Retain two readable columns and space above the hotbar at large GUI scales.
        float scale = Math.min(percent / 100f, Math.min((screenWidth - 20) / 240f,
                (screenHeight - 56) / (float) Math.max(1, mainHeight)));
        scale = Math.max(0.01f, scale);
        int width = Math.min(212, (int) Math.floor((screenWidth - 20) / (2 * scale)));
        return new Layout(scale, width, (int) Math.ceil(6 / scale),
                (int) Math.floor((screenWidth - 6) / scale) - width,
                (int) Math.ceil(6 / scale), (int) Math.floor((screenHeight - 48) / scale));
    }
}