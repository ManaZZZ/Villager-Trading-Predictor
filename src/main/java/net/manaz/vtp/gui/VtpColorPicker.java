package net.manaz.vtp.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

/** Inline square HSV picker with a live preview and synchronized RGB/hex controls. */
final class VtpColorPicker {
    private static final int[] PRESETS = {0x55FF55, 0x55FFFF, 0x5599FF, 0xAA77FF, 0xFF77AA, 0xFFAA55, 0xFFFF55, 0xFFFFFF};
    private static final String[] NAMES = {"Green", "Cyan", "Blue", "Purple", "Pink", "Orange", "Yellow", "White"};
    private static final String[] CHANNELS = {"Red", "Green", "Blue"};
    private int draft = VtpAccent.color();
    private float hue, saturation, brightness;
    private boolean updating;
    private final EditBox[] rgbBoxes = new EditBox[3];
    private final EditBox hexBox;

    VtpColorPicker(Font font, int x, int y, int w, int h, Consumer<AbstractWidget> add,
                   Consumer<Renderable> decorate) {
        readHsv();
        int side = Math.min(w - Math.clamp(w / 3, 64, 80) - 8, h - 58);
        int controlsX = x + side + 8, controlsWidth = w - side - 8;
        int colorHeight = Math.max(86, side + 16);
        boolean stackedLabels = side >= 122;
        int previewHeight = colorHeight - (stackedLabels ? 102 : 66);
        decorate.accept((g, mx, my, delta) -> {
            g.fill(controlsX, y, controlsX + controlsWidth, y + previewHeight, draft);
            g.outline(controlsX, y, controlsWidth, previewHeight, VtpUi.EDGE);
        });
        add.accept(new ColorPlane(x, y, side, side, false));
        add.accept(new ColorPlane(x, y + side + 4, side, 12, true));
        for (int i = 0; i < rgbBoxes.length; i++) {
            int channel = i, shift = (2 - i) * 8;
            int row = y + previewHeight + (stackedLabels ? 4 + i * 34 : 2 + i * 22);
            int fieldX = stackedLabels ? controlsX : controlsX + 32;
            int fieldY = stackedLabels ? row + 10 : row;
            label(decorate, font, CHANNELS[i], controlsX, stackedLabels ? row : row + 6);
            EditBox box = new VtpEditBox(font, fieldX, fieldY,
                    stackedLabels ? controlsWidth : controlsWidth - 32, 20, Component.literal(CHANNELS[i]));
            rgbBoxes[i] = box;
            box.setMaxLength(3);
            box.setValue(String.valueOf(draft >> shift & 255));
            box.setTooltip(Tooltip.create(Component.literal(CHANNELS[i] + ": 0–255. Changes apply immediately.")));
            box.setResponder(value -> {
                if (updating) return;
                int amount = value.matches("[0-9]{1,3}") ? Integer.parseInt(value) : -1;
                boolean valid = amount >= 0 && amount <= 255;
                rgbBoxes[channel].setTextColor(valid ? VtpUi.TEXT : 0xFFFF7777);
                if (valid) {
                    draft = draft & ~(255 << shift) | amount << shift;
                    readHsv(); sync(box);
                }
            });
            add.accept(box);
        }
        int row = y + colorHeight + 4;
        for (int i = 0; i < PRESETS.length; i++) {
            int start = i * (w + 2) / PRESETS.length, end = (i + 1) * (w + 2) / PRESETS.length - 2;
            add.accept(new Swatch(NAMES[i], 0xFF000000 | PRESETS[i], x + start, row, end - start));
        }
        label(decorate, font, "Hex", x, row + 24);
        hexBox = new VtpEditBox(font, x + 24, row + 18, w - 70, 20, Component.literal("Hex color"));
        hexBox.setMaxLength(7); hexBox.setValue(VtpAccent.hex(draft));
        hexBox.setTooltip(Tooltip.create(Component.literal("Hex: #RRGGBB. Changes apply immediately.")));
        hexBox.setResponder(value -> {
            if (updating) return;
            var parsed = VtpAccent.parse(value);
            hexBox.setTextColor(parsed.isPresent() ? VtpUi.TEXT : 0xFFFF7777);
            if (parsed.isPresent()) { draft = parsed.getAsInt(); readHsv(); sync(hexBox); }
        });
        add.accept(hexBox);
        add.accept(new VtpButton("Reset", x + w - 42, row + 18, 42, () -> setDraft(VtpAccent.DEFAULT)));
    }

    private static void label(Consumer<Renderable> decorate, Font font, String text, int x, int y) {
        decorate.accept((g, mx, my, delta) -> g.text(font, text, x, y, VtpUi.MUTED));
    }
    private void readHsv() {
        float[] hsv = VtpAccent.hsv(draft);
        // Achromatic RGB/hex edits should not discard the hue chosen on the strip.
        if (hsv[1] > 0) hue = hsv[0];
        saturation = hsv[1]; brightness = hsv[2];
    }
    private void setDraft(int color) { draft = color; readHsv(); sync(null); }
    private void sync(EditBox source) {
        updating = true;
        for (int i = 0; i < rgbBoxes.length; i++) if (rgbBoxes[i] != source) {
            rgbBoxes[i].setValue(String.valueOf(draft >> ((2 - i) * 8) & 255));
            rgbBoxes[i].setTextColor(VtpUi.TEXT);
        }
        if (hexBox != source) {
            hexBox.setValue(VtpAccent.hex(draft)); hexBox.setTextColor(VtpUi.TEXT);
        }
        updating = false; VtpAccent.set(draft);
    }
    private void changed() { draft = VtpAccent.hsv(hue, saturation, brightness); sync(null); }

    private final class ColorPlane extends AbstractWidget {
        private final boolean hueOnly;
        ColorPlane(int x, int y, int w, int h, boolean hueOnly) {
            super(x, y, w, h, Component.literal(hueOnly ? "Hue" : "Saturation and brightness")); this.hueOnly = hueOnly;
            setTooltip(Tooltip.create(Component.literal(hueOnly ? "Drag to choose a hue. Arrow keys make fine adjustments."
                    : "Left to right: saturation. Top to bottom: brightness. Drag or use arrow keys.")));
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            int x = getX(), y = getY();
            if (hueOnly) {
                for (int i = 0; i < width; i++)
                    g.fill(x + i, y, x + i + 1, y + height, VtpAccent.hsv(i / (float) (width - 1), 1, 1));
                int cx = x + Math.round(hue * (width - 1));
                g.outline(cx - 2, y, 5, height, 0xFF000000);
                g.outline(cx - 1, y + 1, 3, height - 2, VtpUi.TEXT);
            } else {
                for (int i = 0; i < width; i++)
                    g.fillGradient(x + i, y, x + i + 1, y + height,
                            VtpAccent.hsv(hue, i / (float) (width - 1), 1), 0xFF000000);
                int cx = x + Math.round(saturation * (width - 1));
                int cy = y + Math.round((1 - brightness) * (height - 1));
                g.outline(cx - 3, cy - 3, 7, 7, 0xFF000000);
                g.outline(cx - 2, cy - 2, 5, 5, VtpUi.TEXT);
            }
            if (isFocused()) g.outline(x, y, width, height, VtpUi.TEXT);
        }
        private void pick(double mx, double my) {
            float horizontal = (float) Math.clamp((mx - getX()) / (width - 1), 0, 1);
            if (hueOnly) hue = horizontal;
            else { saturation = horizontal; brightness = 1 - (float) Math.clamp((my - getY()) / (height - 1), 0, 1); }
            changed();
        }
        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { pick(event.x(), event.y()); }
        @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { pick(event.x(), event.y()); }
        @Override public boolean keyPressed(KeyEvent event) {
            int key = event.key();
            if (key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT && key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN) return false;
            if (hueOnly) hue = Math.clamp(hue + (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_RIGHT ? 1 : -1) / 360f, 0, 1);
            else {
                if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)
                    saturation = Math.clamp(saturation + (key == GLFW.GLFW_KEY_RIGHT ? 0.01f : -0.01f), 0, 1);
                else brightness = Math.clamp(brightness + (key == GLFW.GLFW_KEY_UP ? 0.01f : -0.01f), 0, 1);
            }
            changed(); return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.USAGE, (hueOnly ? "Hue " + Math.round(hue * 360) + " degrees."
                    : "Saturation " + Math.round(saturation * 100) + " percent. Brightness " + Math.round(brightness * 100) + " percent.")
                    + " Use arrow keys to adjust.");
        }
    }
    private final class Swatch extends Button {
        private final int color;
        Swatch(String name, int color, int x, int y, int w) {
            super(x, y, w, 14, Component.literal(name + " " + VtpAccent.hex(color)),
                    ignored -> setDraft(color), DEFAULT_NARRATION);
            this.color = color;
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
            VtpUi.slot(g, getX(), getY(), width, height);
            g.fill(getX() + 3, getY() + 3, getX() + width - 3, getY() + height - 3, color);
            if (draft == color || isHoveredOrFocused()) g.outline(getX(), getY(), width, height, VtpUi.TEXT);
        }
    }
}