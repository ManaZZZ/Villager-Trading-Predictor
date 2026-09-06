package net.manaz.vtp.gui;

import java.awt.Color;
import java.util.Locale;
import java.util.OptionalInt;

/** User-selected RGB accent; no Minecraft registries are touched during config loading. */
public final class VtpAccent {
    public static final int DEFAULT = 0xFF55FF55;
    private static int color = DEFAULT, text = DEFAULT;
    private VtpAccent() {}
    public static int color() { return color; }
    public static void set(int rgb) { color = 0xFF000000 | (rgb & 0xFFFFFF); text = readable(color); }
    public static String hex(int rgb) { return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF); }
    public static OptionalInt parse(String value) {
        if (value == null) return OptionalInt.empty();
        String hex = value.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        return hex.matches("[0-9a-fA-F]{6}") ? OptionalInt.of(0xFF000000 | Integer.parseInt(hex, 16)) : OptionalInt.empty();
    }
    public static int hsv(float hue, float saturation, float brightness) {
        return Color.HSBtoRGB(hue, saturation, brightness);
    }
    public static float[] hsv(int rgb) {
        return Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
    }
    /** A readable tint for accent text when the chosen decoration color is very dark. */
    public static int text() { return text; }
    public static int readable(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        while (luminance(r, g, b) < 0.28 && (r < 255 || g < 255 || b < 255)) {
            r = Math.min(255, r + 4); g = Math.min(255, g + 4); b = Math.min(255, b + 4);
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
    private static double luminance(int r, int g, int b) {
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
    }
    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}