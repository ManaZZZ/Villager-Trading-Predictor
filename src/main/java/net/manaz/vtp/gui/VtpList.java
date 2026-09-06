package net.manaz.vtp.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import org.lwjgl.glfw.GLFW;

/** Clipped item rows with native input, narration and subtle green selection feedback. */
public final class VtpList extends AbstractWidget {
    public record Entry(String text, String suffix, ItemStack icon, List<ItemStack> trade,
                        String tooltip, boolean highlight, Runnable action) {
        public static Entry text(String text, Runnable action) {
            return new Entry(text, "", ItemStack.EMPTY, List.of(), text, false, action);
        }
    }
    private static final int ROW = 20;
    private List<Entry> entries = List.of();
    private int offset, selected = -1;
    private boolean dragging;
    private final Map<Integer, VtpMotion.Value> hoverMotion = new HashMap<>();
    public VtpList(int x, int y, int w, int h, String title) {
        super(x, y, w, h, Component.literal(title));
    }
    public void entries(List<Entry> values) {
        entries = List.copyOf(values); offset = Math.clamp(offset, 0, maxOffset());
        selected = Math.min(selected, entries.size() - 1);
        hoverMotion.keySet().removeIf(index -> index >= entries.size());
    }
    public int offset() { return offset; }
    public void offset(int value) { offset = Math.clamp(value, 0, maxOffset()); }
    public void selected(int value) { selected = value; }
    public int selected() { return selected; }
    public int size() { return entries.size(); }
    private int visibleRows() { return Math.max(1, (height - 4) / ROW); }
    private int maxOffset() { return Math.max(0, entries.size() - visibleRows()); }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        VtpUi.slot(g, x, y, width, height);
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        if (entries.isEmpty()) g.text(font, "—", x + 7, y + 7, VtpUi.TEXT, true);
        for (int r = 0; r < visibleRows() && offset + r < entries.size(); r++) {
            int index = offset + r, ry = y + 2 + r * ROW;
            Entry e = entries.get(index);
            boolean hovered = mx >= x + 1 && mx < x + width - 8 && my >= ry && my < ry + ROW;
            boolean marked = selected == index || e.highlight();
            float glow = hoverMotion.computeIfAbsent(index, ignored -> new VtpMotion.Value())
                    .sample(marked || hovered && e.action() != null ? 1 : 0);
            if (glow > 0) {
                g.fill(x + 2, ry, x + width - 8, ry + ROW, VtpUi.alpha(VtpUi.accent(), glow * 0.09f));
                if (marked) g.fill(x + 2, ry + 2, x + 3, ry + ROW - 2, VtpUi.alpha(VtpUi.accent(), glow * 0.8f));
            }
            int tx = x + 6;
            if (!e.trade().isEmpty()) {
                int sx = tx;
                for (int i = 0; i < e.trade().size(); i++) {
                    if (i == 1 && e.trade().get(i).isEmpty()) continue;
                    if (i == 2) {
                        g.fill(sx, ry + 8, sx + 9, ry + 10, VtpUi.TEXT);
                        for (int a = 0; a < 4; a++) g.fill(sx + 6 + a, ry + 5 + a, sx + 7 + a, ry + 13 - a, VtpUi.TEXT);
                        sx += 14;
                    }
                    else if (i == 1) { g.text(font, "+", sx, ry + 6, VtpUi.TEXT, true); sx += 9; }
                    var stack = e.trade().get(i);
                    if (!stack.isEmpty()) {
                        g.item(stack, sx, ry + 1);
                        g.itemDecorations(font, stack, sx, ry + 1);
                        if (hovered && mx >= sx && mx < sx + 16) g.setTooltipForNextFrame(font, stack, mx, my);
                    }
                    sx += 20;
                }
                tx = sx + 4;
            } else if (!e.icon().isEmpty()) {
                g.item(e.icon(), tx, ry + 1); tx += 20;
            }
            boolean emeraldPrice = e.suffix().endsWith(" em");
            String suffix = emeraldPrice ? e.suffix().substring(0, e.suffix().length() - 3) : e.suffix();
            int suffixW = suffix.isEmpty() ? 0 : font.width(suffix) + (emeraldPrice ? 23 : 6);
            if (e.trade().isEmpty() || x + width - 12 - tx - suffixW >= 50)
                g.text(font, VtpUi.fitBook(font, e.text(), Math.max(0, x + width - 12 - tx - suffixW)), tx, ry + 6,
                    e.highlight() ? VtpUi.accentText() : VtpUi.TEXT, true);
            if (!suffix.isEmpty()) {
                int sx = x + width - 12 - (emeraldPrice ? 17 : 0) - font.width(suffix);
                g.text(font, suffix, sx, ry + 6, VtpUi.MUTED, true);
                if (emeraldPrice) g.item(VtpIcons.of(net.minecraft.world.item.Items.EMERALD), x + width - 27, ry + 1);
            }
            if (hovered && e.trade().isEmpty() && !e.tooltip().isEmpty())
                VtpUi.tooltip(g, font, e.tooltip(), mx, my);
        }
        g.disableScissor();
        if (maxOffset() > 0) {
            int track = height - 4;
            int thumb = Math.max(12, track * visibleRows() / entries.size());
            int top = y + 2 + (track - thumb) * offset / maxOffset();
            g.fill(x + width - 7, y + 2, x + width - 2, y + height - 2, 0xFF181818);
            g.fill(x + width - 7, top, x + width - 2, top + thumb, 0xFF505050);
            g.fill(x + width - 7, top, x + width - 6, top + thumb - 1, 0xFF747474);
        }
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= getX() + width - 8 && maxOffset() > 0) {
            dragging = true; dragTo(event.y()); return;
        }
        int index = offset + (int) (event.y() - getY() - 2) / ROW;
        if (index >= 0 && index < entries.size() && index < offset + visibleRows()) {
            selected = index;
            if (entries.get(index).action() != null) entries.get(index).action().run();
        }
    }
    private void dragTo(double y) {
        int track = height - 4, thumb = Math.max(12, track * visibleRows() / entries.size());
        offset((int) Math.round((y - getY() - 2 - thumb / 2.0) * maxOffset() / Math.max(1, track - thumb)));
    }
    @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { if (dragging) dragTo(event.y()); }
    @Override public void onRelease(MouseButtonEvent event) { dragging = false; }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (!isMouseOver(x, y) || sy == 0) return false;
        offset(offset - (int) Math.signum(sy) * 3); return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN
                || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
            if (entries.isEmpty()) return true;
            int step = key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN ? visibleRows() : 1;
            selected = key == GLFW.GLFW_KEY_HOME ? 0 : key == GLFW.GLFW_KEY_END ? entries.size() - 1
                    : Math.clamp(selected + (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_UP ? -step : step), 0, entries.size() - 1);
            if (selected < offset) offset(selected);
            if (selected >= offset + visibleRows()) offset(selected - visibleRows() + 1);
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && selected >= 0 && selected < entries.size()) {
            Runnable action = entries.get(selected).action();
            if (action != null) { playDownSound(Minecraft.getInstance().getSoundManager()); action.run(); }
            return true;
        }
        return false;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        if (selected >= 0 && selected < entries.size()) output.add(NarratedElementType.USAGE, entries.get(selected).tooltip());
        else output.add(NarratedElementType.USAGE, "Use arrow keys to browse, Enter to select.");
    }
}
