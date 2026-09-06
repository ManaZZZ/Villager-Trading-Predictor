package net.manaz.vtp.gui;

import net.manaz.vtp.trade.TradeDataLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import java.util.*;
import java.util.function.Consumer;

final class EnchantmentPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Holder<Enchantment>> onChoose;
    private String query = "";
    private VtpList list;
    private int x, y, w, h;
    EnchantmentPickerScreen(Screen parent, Consumer<Holder<Enchantment>> onChoose) {
        super(Component.literal("Choose enchantment")); this.parent = parent; this.onChoose = onChoose;
    }
    @Override protected void init() {
        w = Math.min(300, width - 12); h = Math.min(280, height - 12);
        x = (width - w) / 2; y = (height - h) / 2;
        EditBox search = addRenderableWidget(new VtpEditBox(font, x + 10, y + 28, w - 20, 20, Component.literal("Search enchantments")));
        search.setTextColor(VtpUi.TEXT); search.setTextShadow(true);
        search.setValue(query); search.setHint(Component.literal("Search enchantments"));
        list = addRenderableWidget(new VtpList(x + 10, y + 54, w - 20, h - 90, "Enchantments"));
        search.setResponder(value -> { query = value; list.offset(0); refresh(); });
        refresh(); setInitialFocus(search);
        addRenderableWidget(new VtpButton("Back", x + (w - 100) / 2, y + h - 28, 100, this::onClose));
    }
    private void refresh() {
        String filter = query.trim().toLowerCase(Locale.ROOT);
        list.entries(TradeDataLoader.getTradeableEnchantments().stream()
                .sorted(Comparator.comparing(e -> e.value().description().getString()))
                .filter(e -> e.value().description().getString().toLowerCase(Locale.ROOT).contains(filter))
                .map(e -> new VtpList.Entry(e.value().description().getString(), "", VtpIcons.book(), List.of(),
                        e.value().description().getString(), false, () -> { onChoose.accept(e); onClose(); })).toList());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        VtpUi.frame(g, x, y, w, h);
        String title = getTitle().getString();
        g.item(VtpIcons.book(), x + 8, y + 5);
        g.text(font, title, x + 30, y + 10, VtpUi.TEXT, true);
        super.extractRenderState(g, mx, my, delta);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
