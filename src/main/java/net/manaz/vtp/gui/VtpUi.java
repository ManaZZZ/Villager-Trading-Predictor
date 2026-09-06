package net.manaz.vtp.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Shared dark tooltip framing in Minecraft GUI coordinates. */
public final class VtpUi {
    public static final int TEXT = 0xFFFFFFFF, MUTED = 0xFFA0A0A0;
    public static final int HUD_TEXT = TEXT, HUD_MUTED = MUTED;
    public static int accent() { return VtpAccent.color(); }
    public static int accentText() { return VtpAccent.text(); }
    public static final int EDGE = 0xFF383838;
    private VtpUi() {}

    public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, 0.94f);
    }

    /** Original pixel frame; no Jade textures or rendering code are bundled. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float opacity) {
        g.fill(x + 2, y + 1, x + w - 2, y + h - 1, alpha(0xFF131313, opacity));
        g.fill(x + 1, y + 2, x + 2, y + h - 2, alpha(0xFF131313, opacity));
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, alpha(0xFF131313, opacity));
        int top = alpha(EDGE, opacity), bottom = alpha(0xFF242424, opacity);
        g.fill(x + 2, y, x + w - 2, y + 1, top);
        g.fill(x, y + 2, x + 1, y + h - 2, top);
        g.fill(x + 1, y + 1, x + 2, y + 2, top);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, top);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, bottom);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, bottom);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, bottom);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, bottom);
    }

    public static void slot(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF0C0C0C);
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y, x + 1, y + h, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF242424);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF242424);
    }

    public static void hud(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, 0.7f);
    }

    public static int alpha(int color, float alpha) {
        return (Math.clamp(Math.round((color >>> 24) * alpha), 0, 255) << 24) | (color & 0xFFFFFF);
    }

    public static String fit(Font font, String value, int width) {
        if (width <= 0) return "";
        if (font.width(value) <= width) return value;
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }

    public static String fitBook(Font font, String value, int width) {
        int space = value.lastIndexOf(' ');
        if (space > 0 && font.width(value) > width && value.substring(space + 1).matches("[0-9IVX]+")) {
            String level = value.substring(space);
            return fit(font, value.substring(0, space), width - font.width(level)) + level;
        }
        return fit(font, value, width);
    }

    public static void tooltip(GuiGraphicsExtractor g, Font font, String value, int mx, int my) {
        int width = Math.min(240, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24);
        g.setTooltipForNextFrame(font, font.split(Component.literal(value), width), mx, my);
    }
    public static String name(String id) {
        String path = id.substring(id.lastIndexOf(':') + 1);
        StringBuilder result = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w,
                            int color, int mx, int my) {
        g.text(font, fit(font, text, w), x, y, color, true);
        if (font.width(text) > w && mx >= x && mx < x + w && my >= y && my < y + 10)
            tooltip(g, font, text, mx, my);
    }
}