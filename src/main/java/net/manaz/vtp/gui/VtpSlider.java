package net.manaz.vtp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Discrete native mouse/keyboard slider with the same frame as VTP buttons. */
class VtpSlider extends AbstractSliderButton {
    private final int min, max;
    private final IntFunction<String> label;
    private final IntConsumer change;

    VtpSlider(int x, int y, int width, int min, int max, int initial,
              IntFunction<String> label, IntConsumer change) {
        super(x, y, width, 20, Component.empty(), (Math.clamp(initial, min, max) - min) / (double) (max - min));
        this.min = min; this.max = max; this.label = label; this.change = change;
        updateMessage();
    }
    int amount() { return min + (int) Math.round(value * (max - min)); }
    void sync(int amount) {
        value = (Math.clamp(amount, min, max) - min) / (double) (max - min);
        updateMessage();
    }
    @Override protected void applyValue() { change.accept(amount()); }
    @Override protected void updateMessage() { setMessage(Component.literal(label.apply(amount()))); }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        VtpUi.panel(g, x, y, width, height, 1);
        g.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, VtpUi.EDGE);
        int handle = x + 4 + (int) Math.round((width - 12) * value);
        g.fill(x + 4, y + height - 4, handle + 4, y + height - 3, VtpUi.alpha(VtpUi.accent(), 0.6f));
        g.fill(handle, y + height - 6, handle + 4, y + height - 2, VtpUi.accent());
        String fitted = VtpUi.fit(font, getMessage().getString(), width - 8);
        g.text(font, fitted, x + (width - font.width(fitted)) / 2, y + 3, active ? VtpUi.TEXT : VtpUi.MUTED);
        if (isHoveredOrFocused()) handleCursor(g);
    }
}