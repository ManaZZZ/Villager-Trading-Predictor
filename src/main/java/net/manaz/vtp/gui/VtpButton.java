package net.manaz.vtp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Native button input, sound and narration with a compact dark frame. */
public final class VtpButton extends Button {
    private final VtpMotion.Value hover = new VtpMotion.Value();
    private Item icon;
    private boolean selected;
    public VtpButton(String label, int x, int y, int width, Runnable action) {
        super(x, y, width, 20, Component.literal(label), ignored -> action.run(), DEFAULT_NARRATION);
    }
    public VtpButton icon(Item value) { icon = value; return this; }
    public VtpButton selected(boolean value) { selected = value; return this; }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        float glow = hover.sample(active && isHoveredOrFocused() ? 1 : 0);
        VtpUi.panel(g, x, y, width, height, 1);
        if (glow > 0 || selected) {
            g.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                    VtpUi.alpha(VtpUi.accent(), selected ? 0.10f : glow * 0.06f));
            int length = selected ? width - 4 : Math.round((width - 4) * glow);
            int start = x + (width - length) / 2;
            g.fill(start, y + height - 2, start + length, y + height - 1,
                    VtpUi.alpha(VtpUi.accent(), selected ? 0.85f : glow * 0.65f));
        }
        String label = getMessage().getString();
        boolean showIcon = icon != null && width >= font.width(label) + 30;
        int labelWidth = width - (showIcon ? 28 : 4);
        String fitted = VtpUi.fit(font, label, labelWidth);
        int color = selected ? VtpUi.accentText() : active ? VtpUi.TEXT : 0xFF707070;
        int textX = showIcon ? x + 24 : x + (width - font.width(fitted)) / 2;
        if (showIcon) g.item(VtpIcons.of(icon), x + 4, y + 2);
        g.text(font, fitted, textX, y + 6, color, true);
        if (isHovered() && !fitted.equals(label)) VtpUi.tooltip(g, font, label, mx, my);
    }
}