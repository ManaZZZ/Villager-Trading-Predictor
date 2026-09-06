package net.manaz.vtp.gui;

import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.render.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import java.util.EnumMap;

/** Draft HUD options and a live, unblurred preview; dragging leaves only the size control visible. */
public final class VtpHudSettingsScreen extends Screen {
    private final Screen parent;
    private boolean enabled = VillagerHudRenderer.isEnabled(), levels = VillagerHudRenderer.isShowLevels(), resizing;
    private int percent = HudSize.percent();
    private HudVisibility.Mode mode = HudVisibility.mode();
    private String seconds = String.valueOf(HudVisibility.seconds()), range = String.valueOf(HudVisibility.range());
    private final EnumMap<HudVisibility.Mode, VtpButton> choices = new EnumMap<>(HudVisibility.Mode.class);
    private EditBox timeBox, rangeBox;
    private VtpButton toggle, preview, apply;
    private VtpSlider size;
    private int x, y, w;
    VtpHudSettingsScreen(Screen parent) { super(Component.literal("HUD settings")); this.parent = parent; }
    public int previewPercent() { return percent; }
    public boolean isResizing() { return resizing; }

    @Override protected void init() {
        w = Math.min(340, width - 12); x = (width - w) / 2; y = (height - 228) / 2;
        choices.clear(); resizing = false;
        int column = (w - 24) / 2;
        toggle = addRenderableWidget(new VtpButton("", x + 10, y + 28, column, () -> { enabled = !enabled; update(); }));
        preview = addRenderableWidget(new VtpButton("", x + 14 + column, y + 28, column, () -> { levels = !levels; update(); }));
        for (var choice : HudVisibility.Mode.values()) {
            int index = choice.ordinal();
            VtpButton option = addRenderableWidget(new VtpButton(choice.label, x + 10 + index % 2 * (column + 4),
                    y + 70 + index / 2 * 24, column, () -> { mode = choice; update(); }));
            option.setTooltip(Tooltip.create(Component.literal(switch (choice) {
                case NEVER -> "Keep the selected villager's HUD visible.";
                case TIME -> "Hide after the timeout since you last looked at or traded with this villager.";
                case RANGE -> "Hide when the selected villager is farther away than the range below.";
                case LOOKING -> "Hide as soon as you stop pointing at the selected villager.";
            })));
            choices.put(choice, option);
        }
        timeBox = addRenderableWidget(new VtpEditBox(font, x + 10, y + 134, column, 20, Component.literal("Timeout seconds")));
        timeBox.setMaxLength(4); timeBox.setValue(seconds); timeBox.setResponder(value -> { seconds = value; update(); });
        rangeBox = addRenderableWidget(new VtpEditBox(font, x + 14 + column, y + 134, column, 20, Component.literal("Range blocks")));
        rangeBox.setMaxLength(3); rangeBox.setValue(range); rangeBox.setResponder(value -> { range = value; update(); });
        size = addRenderableWidget(new VtpSlider(x + 10, y + 162, w - 20, HudSize.MIN, HudSize.MAX, percent,
                amount -> "HUD size: " + amount + "%", amount -> percent = amount) {
            @Override public void onClick(MouseButtonEvent event, boolean twice) {
                resizing = true; super.onClick(event, twice);
            }
            @Override public void onRelease(MouseButtonEvent event) { resizing = false; super.onRelease(event); }
        });
        addRenderableWidget(new VtpButton("100%", x + 10, y + 200, 54, () -> { percent = 100; size.sync(percent); }));
        addRenderableWidget(new VtpButton("Cancel", x + w - 164, y + 200, 74, this::onClose));
        apply = addRenderableWidget(new VtpButton("Apply", x + w - 84, y + 200, 74, () -> {
            if (!valid()) return;
            VillagerHudRenderer.setEnabled(enabled); VillagerHudRenderer.setShowLevels(levels); HudSize.set(percent);
            HudVisibility.configure(mode, number(seconds, 1, 3600) ? Integer.parseInt(seconds) : HudVisibility.seconds(),
                    number(range, 1, 256) ? Integer.parseInt(range) : HudVisibility.range());
            VtpPersistence.saveGlobalSettings(); onClose();
        }));
        update();
    }
    private static boolean number(String value, int min, int max) {
        try { int n = Integer.parseInt(value); return n >= min && n <= max; }
        catch (NumberFormatException ignored) { return false; }
    }
    private boolean valid() {
        return (mode != HudVisibility.Mode.TIME || number(seconds, 1, 3600))
                && (mode != HudVisibility.Mode.RANGE || number(range, 1, 256));
    }
    private void update() {
        choices.forEach((choice, button) -> button.selected(mode == choice));
        if (toggle != null) toggle.setMessage(Component.literal("HUD: " + (enabled ? "ON" : "OFF")));
        if (preview != null) preview.setMessage(Component.literal("Level preview: " + (levels ? "ON" : "OFF")));
        if (timeBox != null) timeBox.active = mode == HudVisibility.Mode.TIME;
        if (rangeBox != null) rangeBox.active = mode == HudVisibility.Mode.RANGE;
        if (apply != null) apply.active = valid();
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // Preview ignores the visibility rule and toggle so size can always be adjusted.
        VillagerHudRenderer.renderPreview(g, percent, levels);
        if (resizing) {
            VtpUi.frame(g, x, size.getY() - 4, w, 28);
            size.extractRenderState(g, mx, my, delta);
            return;
        }
        VtpUi.frame(g, x, y, w, 228);
        g.item(VtpIcons.of(Items.SPYGLASS), x + 8, y + 5);
        g.text(font, getTitle(), x + 30, y + 10, VtpUi.TEXT);
        int column = (w - 24) / 2;
        g.text(font, "When to hide the HUD", x + 10, y + 56, VtpUi.TEXT);
        g.text(font, "Timeout (1–3600 s)", x + 10, y + 122, timeBox.active ? VtpUi.TEXT : VtpUi.MUTED);
        g.text(font, "Range (1–256 blocks)", x + 14 + column, y + 122, rangeBox.active ? VtpUi.TEXT : VtpUi.MUTED);
        VtpUi.text(g, font, HudView.current() == null ? "Sample HUD preview. Drag to resize." : "Drag to preview. Large sizes fit the screen.",
                x + 10, y + 188, w - 20, VtpUi.MUTED, mx, my);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}