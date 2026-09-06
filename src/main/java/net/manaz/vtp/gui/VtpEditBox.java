package net.manaz.vtp.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Keeps vanilla editing, selection, IME and caret placement; changes only the border. */
public final class VtpEditBox extends EditBox {
    private final VtpMotion.Value focus = new VtpMotion.Value();
    public VtpEditBox(Font font, int x, int y, int w, int h, Component label) {
        super(font, x, y, w, h, label);
        setTextColor(VtpUi.TEXT); setTextColorUneditable(VtpUi.MUTED); setTextShadow(true);
    }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractWidgetRenderState(g, mx, my, delta);
        if (!isVisible() || !isBordered()) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int edge = active ? 0xFF505050 : VtpUi.EDGE;
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + h - 1, x + w, y + h, edge);
        g.fill(x, y, x + 1, y + h, edge);
        g.fill(x + w - 1, y, x + w, y + h, edge);
        float glow = focus.sample(active && isFocused() ? 1 : 0);
        int length = Math.round((w - 2) * glow), start = x + (w - length) / 2;
        if (length > 0) g.fill(start, y + h - 1, start + length, y + h, VtpUi.alpha(VtpUi.accent(), glow * 0.8f));
    }
}