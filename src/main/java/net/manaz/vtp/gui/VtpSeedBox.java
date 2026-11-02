package net.manaz.vtp.gui;

import net.manaz.vtp.seed.SeedProvider;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Mask both rendering and narration while retaining vanilla text editing and paste support. */
public final class VtpSeedBox extends VtpEditBox {
    public VtpSeedBox(Font font, int x, int y, int width) {
        super(font, x, y, width, 20, Component.literal("World seed; required on multiplayer"));
        addFormatter((text, offset) -> FormattedCharSequence.forward(
                SeedProvider.isSeedVisible() ? text : "*".repeat(text.length()), Style.EMPTY));
    }

    @Override protected MutableComponent createNarrationMessage() {
        return SeedProvider.isSeedVisible() ? super.createNarrationMessage()
                : Component.literal(getValue().isEmpty() ? "World seed, empty" : "World seed, hidden");
    }
}
