package net.manaz.vtp.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.manaz.vtp.gui.*;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

public class VillagerHudRenderer implements HudElement {
    private static boolean enabled = true, romanNumerals, showLevels = true;
    private static final List<EnchantmentTarget> targets = new ArrayList<>();
    private static final String[] ROMAN = {"I","II","III","IV","V","VI","VII","VIII","IX","X"};
    private static ItemStack emerald;
    private final HudFade fade = new HudFade();
    private Object world;
    private long lastFrame;
    private static ItemStack emerald() {
        if (emerald == null) emerald = new ItemStack(Items.EMERALD);
        return emerald;
    }
    public static boolean isRomanNumerals() { return romanNumerals; }
    public static void setRomanNumerals(boolean roman) { romanNumerals = roman; }
    public static boolean isShowLevels() { return showLevels; }
    public static void setShowLevels(boolean show) { showLevels = show; }
    public static String fmtLvl(int level) {
        return romanNumerals && level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
    }
    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("villager-trading-predictor", "hud_overlay"), new VillagerHudRenderer());
    }
    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean isEnabled() { return enabled; }
    public static void addTarget(EnchantmentTarget target) {
        if (!targets.contains(target)) targets.add(target);
        PredictionEngine.clearCache();
    }
    public static void removeTarget(int index) {
        if (index >= 0 && index < targets.size()) { targets.remove(index); PredictionEngine.clearCache(); }
    }
    public static void clearTargets() { targets.clear(); PredictionEngine.clearCache(); }
    public static List<EnchantmentTarget> getTargets() { return List.copyOf(targets); }
    public static void setTargets(List<EnchantmentTarget> list) { targets.clear(); targets.addAll(list); PredictionEngine.clearCache(); }
    public static void setTarget(EnchantmentTarget target) { setTargets(List.of(target)); }
    public static EnchantmentTarget getTarget() { return targets.isEmpty() ? null : targets.getFirst(); }

    public static String rowMarker(HudView.Row row) { return row.label().equals("Current") ? ">" : row.label(); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) {
            resetFade(); return;
        }
        HudView view = HudView.current();
        if (view == null) { resetFade(); return; }
        if (world != mc.level) { resetFade(); world = mc.level; }
        long now = System.nanoTime();
        double elapsed = lastFrame == 0 ? 0 : (now - lastFrame) / 1.0e9;
        lastFrame = now;
        float opacity = fade.advance(enabled && HudVisibility.visible(view.uuid()), elapsed);
        // Avoid legacy font alpha normalization turning nearly transparent text opaque.
        if (opacity < 4f / 255) return;
        render(g, view, opacity, HudSize.percent(), showLevels);
    }

    public static void renderPreview(GuiGraphicsExtractor g, int percent, boolean levels) {
        var view = HudView.current();
        if (view == null) {
            // Clearly identified as sample data by the HUD settings screen.
            view = new HudView(new UUID(0, 0), "Mending 1", "2 rerolls", 10, true, "",
                    List.of(new HudView.Row("Current", "Unbreaking 3", 17, false, false),
                            new HudView.Row("+1", "Efficiency 4", 24, false, true),
                            new HudView.Row("+2", "Mending 1", 10, true, true),
                            new HudView.Row("+3", "Sharpness 5", 23, false, true)),
                    List.of(new HudView.Row("II", "Protection 4", 22, false, true),
                            new HudView.Row("III", "—", -1, false, true),
                            new HudView.Row("IV", "—", -1, false, true)), false);
        }
        render(g, view, 1, percent, levels);
    }

    public static int rowsHeight(List<HudView.Row> rows) { return 26 + rows.size() * 16; }

    public static HudSize.Layout layout(HudView view, int screenWidth, int screenHeight, int percent, boolean levels) {
        int rightHeight = rowsHeight(view.trades());
        if (view.locked() && levels && !view.levels().isEmpty()) rightHeight += 6 + rowsHeight(view.levels());
        return HudSize.layout(screenWidth, screenHeight, Math.max(targetHeight(view), rightHeight), percent);
    }

    private static void render(GuiGraphicsExtractor g, HudView view, float opacity, int percent, boolean levels) {
        var mc = Minecraft.getInstance();
        var layout = layout(view, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), percent, levels);
        g.pose().pushMatrix();
        try {
            g.pose().scale(layout.scale(), layout.scale());
            renderTarget(g, mc.font, view, layout.left(), layout.top(), layout.panelWidth(), opacity);
            if (view.trades().isEmpty()) return;
            int bottom = renderRows(g, mc.font, layout.right(), layout.top(), layout.panelWidth(),
                    view.locked() ? "Locked trades" : "Trades", view.trades(), Items.LECTERN, opacity);
            int levelHeight = rowsHeight(view.levels());
            if (levels && !view.levels().isEmpty() && bottom + 6 + levelHeight <= layout.bottom())
                renderRows(g, mc.font, layout.right(), bottom + 6, layout.panelWidth(),
                        view.locked() ? "Predicted" : "Level preview", view.levels(), Items.EXPERIENCE_BOTTLE, opacity);
        } finally {
            g.pose().popMatrix();
        }
    }

    private void resetFade() { fade.reset(); world = null; lastFrame = 0; }
    private static int targetHeight(HudView view) { return view.warning().isEmpty() ? 61 : 75; }
    private static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, float opacity) {
        g.text(font, value, x, y, VtpUi.alpha(color, opacity));
    }

    public static void renderTarget(GuiGraphicsExtractor g, Font font, HudView view, int x, int y, int width) {
        renderTarget(g, font, view, x, y, width, 1);
    }

    public static void renderTarget(GuiGraphicsExtractor g, Font font, HudView view, int x, int y, int width, float opacity) {
        if (opacity < 4f / 255) return;
        VtpUi.panel(g, x, y, width, targetHeight(view), 0.7f * opacity);
        HudItems.render(g, VtpIcons.book(), x + 7, y + 5, opacity);
        text(g, font, "Target", x + 29, y + 9, VtpUi.HUD_TEXT, opacity);
        text(g, font, VtpUi.fitBook(font, view.target(), width - 16), x + 8, y + 27, VtpUi.HUD_TEXT, opacity);
        text(g, font, VtpUi.fit(font, view.result(), width - (view.price() >= 0 ? 55 : 16)),
                x + 8, y + 43, view.found() ? VtpUi.accentText() : VtpUi.HUD_MUTED, opacity);
        if (view.price() >= 0) {
            String price = String.valueOf(view.price());
            text(g, font, price, x + width - 27 - font.width(price), y + 43, VtpUi.HUD_TEXT, opacity);
            HudItems.render(g, emerald(), x + width - 24, y + 38, opacity);
        }
        if (!view.warning().isEmpty())
            text(g, font, VtpUi.fit(font, view.warning(), width - 16), x + 8, y + 59, 0xFFFFCC66, opacity);
    }

    private static int renderRows(GuiGraphicsExtractor g, Font font, int x, int y, int width,
                                  String title, List<HudView.Row> rows, net.minecraft.world.item.Item icon, float opacity) {
        int height = rowsHeight(rows);
        VtpUi.panel(g, x, y, width, height, 0.7f * opacity);
        HudItems.render(g, VtpIcons.of(icon), x + 7, y + 5, opacity);
        text(g, font, VtpUi.fit(font, title, width - 56), x + 29, y + 9, VtpUi.HUD_TEXT, opacity);
        HudItems.render(g, emerald(), x + width - 24, y + 5, opacity);
        int nameX = rows.stream().anyMatch(row -> !row.label().isEmpty()) ? 27 : 8;
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            int ry = y + 27 + i * 16;
            if (row.match()) g.fill(x + 3, ry - 3, x + 4, ry + 10, VtpUi.alpha(VtpUi.accent(), 0.7f * opacity));
            text(g, font, rowMarker(row), x + 8, ry, VtpUi.HUD_MUTED, opacity);
            text(g, font, VtpUi.fitBook(font, row.text(), width - nameX - 25), x + nameX, ry,
                    row.match() ? VtpUi.accentText() : VtpUi.HUD_TEXT, opacity);
            if (row.price() >= 0) {
                String price = String.valueOf(row.price());
                text(g, font, price, x + width - 10 - font.width(price), ry, VtpUi.HUD_MUTED, opacity);
            }
        }
        return y + height;
    }
}