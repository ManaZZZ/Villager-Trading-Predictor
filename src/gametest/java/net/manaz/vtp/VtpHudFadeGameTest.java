package net.manaz.vtp;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.gui.*;
import net.manaz.vtp.render.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** Fixed opacity samples let screenshot checks catch opaque icons or alpha leaking into other draws. */
final class VtpHudFadeGameTest {
    static void run(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            mc.getWindow().setWindowed(1280, 720); mc.options.guiScale().set(2); mc.resizeGui();
        });
        context.setScreen(() -> new Screen(Component.literal("HUD fade verification")) {
            @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
                g.fill(0, 0, width, height, 0xFF203040);
                var view = HudView.current();
                if (view == null) throw new AssertionError("No HUD view for fade verification");
                int w = (width - 48) / 3;
                var icons = new net.minecraft.world.item.Item[]{Items.ENCHANTED_BOOK, Items.EMERALD, Items.LECTERN, Items.EXPERIENCE_BOTTLE};
                for (int column = 0; column < 3; column++) {
                    int x = 12 + column * (w + 12);
                    float alpha = column == 0 ? 1 : column == 1 ? 0.5f : 0;
                    g.text(font, new String[]{"Full opacity", "Half opacity", "Hidden"}[column], x, 16, VtpUi.TEXT);
                    VillagerHudRenderer.renderTarget(g, font, view, x, 40, w, alpha);
                    for (int i = 0; i < icons.length; i++) {
                        HudItems.render(g, VtpIcons.of(icons[i]), x + 8 + i * 22, 120, alpha);
                        g.item(VtpIcons.of(icons[i]), x + 8 + i * 22, 160);
                    }
                }
                g.text(font, "Bottom row: ordinary item draws stay opaque", 12, 190, VtpUi.TEXT);
                g.text(font, "125% size, half opacity", 12, 216, VtpUi.TEXT);
                g.pose().pushMatrix();
                g.pose().translate(12, 236);
                g.pose().scale(1.25f, 1.25f);
                for (int i = 0; i < icons.length; i++)
                    HudItems.render(g, VtpIcons.of(icons[i]), i * 22, 0, 0.5f);
                g.pose().popMatrix();
            }
            @Override public boolean isPauseScreen() { return false; }
        });
        context.waitTicks(3);
        context.takeScreenshot("hud-fade-opacity");
        context.setScreen(() -> null);
        context.waitTicks(6);
    }
}