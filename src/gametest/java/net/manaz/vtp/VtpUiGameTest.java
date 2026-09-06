package net.manaz.vtp;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.gui.*;
import net.manaz.vtp.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import java.util.*;

final class VtpUiGameTest {
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(0, 0);
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    static void click(Screen screen, String label) {
        var button = screen.children().stream().filter(e -> e instanceof Button)
                .map(e -> (Button) e).filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow();
        check(button.active, "Disabled button: " + label);
        check(screen.mouseClicked(new MouseButtonEvent(button.getX() + button.getWidth() / 2.0,
                button.getY() + 10, LEFT), false), "Button click missed: " + label);
    }

    static void checkLayout(Screen screen, int w, int h) {
        var widgets = screen.children().stream().filter(e -> e instanceof AbstractWidget)
                .map(e -> (AbstractWidget) e).filter(e -> e.visible).toList();
        for (var widget : widgets) {
            check(widget.getX() >= 0 && widget.getY() >= 0
                    && widget.getX() + widget.getWidth() <= w && widget.getY() + widget.getHeight() <= h,
                    "Widget clipped at " + w + "x" + h + ": " + widget.getMessage().getString());
        }
        for (int i = 0; i < widgets.size(); i++) for (int j = i + 1; j < widgets.size(); j++) {
            var a = widgets.get(i); var b = widgets.get(j);
            boolean overlap = a.getX() < b.getX() + b.getWidth() && a.getX() + a.getWidth() > b.getX()
                    && a.getY() < b.getY() + b.getHeight() && a.getY() + a.getHeight() > b.getY();
            check(!overlap, "Widgets overlap: " + a.getMessage().getString() + " / " + b.getMessage().getString());
        }
    }

    static void run(ClientGameTestContext context) {
        context.runOnClient(mc -> { HudSize.set(HudSize.DEFAULT); VtpAccent.set(VtpAccent.DEFAULT); HudVisibility.configure(HudVisibility.Mode.NEVER, 10, 16); });
        for (int scale : new int[]{1, 2, 3}) {
            context.setScreen(() -> null);
            context.runOnClient(mc -> {
                mc.getWindow().setWindowed(1280, 720);
                mc.options.guiScale().set(scale);
                mc.resizeGui();
            });
            context.waitTicks(15);
            context.takeScreenshot("ui-hud-scale-" + scale);
            context.setScreen(VtpSettingsScreen::new);
            context.waitTicks(2);
            for (String tab : new String[]{"Settings", "Targets", "Sequence", "Trades", "Calibrate"}) {
                context.runOnClient(mc -> {
                    Screen screen = mc.gui.screen();
                    boolean active = screen.children().stream().filter(e -> e instanceof Button).map(e -> (Button) e)
                            .anyMatch(b -> b.getMessage().getString().equals(tab) && b.active);
                    if (active) click(screen, tab);
                    checkLayout(screen, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                });
                context.waitTicks(2);
                context.waitTicks(12);
                if (scale == 2 || scale == 3) context.takeScreenshot("ui-" + tab.toLowerCase(Locale.ROOT) + "-scale-" + scale);
                if (tab.equals("Settings")) VtpCustomizationGameTest.checkDialogs(context, scale);
            }
            context.setScreen(() -> null);
        }
        context.runOnClient(mc -> {
            mc.getWindow().setWindowed(640, 480);
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        context.waitTicks(15);
        context.setScreen(VtpSettingsScreen::new);
        context.waitTicks(2);
        for (String tab : new String[]{"Settings", "Targets", "Sequence", "Trades", "Calibrate"}) {
            context.runOnClient(mc -> {
                click(mc.gui.screen(), tab);
                checkLayout(mc.gui.screen(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            });
        }
        context.takeScreenshot("ui-minimum-size");

        // Exercise the multiplayer calibration controls without connecting to an external server.
        context.setScreen(() -> new VtpSettingsScreen() { @Override protected boolean singleplayer() { return false; } });
        context.waitTicks(2);
        context.runOnClient(mc -> {
            checkLayout(mc.gui.screen(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            click(mc.gui.screen(), "Choose enchantment");
        });
        context.waitTicks(2);
        context.takeScreenshot("ui-enchantment-picker");
        context.runOnClient(mc -> {
            Screen screen = mc.gui.screen();
            checkLayout(screen, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            EditBox search = screen.children().stream().filter(e -> e instanceof EditBox).map(e -> (EditBox) e).findFirst().orElseThrow();
            search.setValue("mending");
            VtpList list = screen.children().stream().filter(e -> e instanceof VtpList).map(e -> (VtpList) e).findFirst().orElseThrow();
            check(list.size() == 1, "Enchantment search did not filter");
            screen.mouseClicked(new MouseButtonEvent(list.getX() + 12, list.getY() + 10, LEFT), false);
        });
        context.waitTicks(2);
        context.runOnClient(mc -> {
            click(mc.gui.screen(), "Add observation");
            click(mc.gui.screen(), "Add no book");
            check(net.manaz.vtp.prediction.CalibrationListStore.size() == 2, "Calibration composer lost observations");
            checkLayout(mc.gui.screen(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
        context.takeScreenshot("ui-calibration-minimum");
        context.runOnClient(mc -> {
            click(mc.gui.screen(), "Targets");
            var screen = mc.gui.screen();
            var search = screen.children().stream().filter(e -> e instanceof EditBox).map(e -> (EditBox) e)
                    .filter(e -> e.getMessage().getString().equals("Search enchantments")).findFirst().orElseThrow();
            search.setValue("unbreaking");
            var list = screen.children().stream().filter(e -> e instanceof VtpList).map(e -> (VtpList) e)
                    .filter(e -> e.getMessage().getString().equals("Enchantments")).findFirst().orElseThrow();
            check(list.size() == 1, "Target search failed");
            screen.mouseClicked(new MouseButtonEvent(list.getX() + 12, list.getY() + 10, LEFT), false);
            click(screen, "Add target");
            check(VillagerHudRenderer.getTargets().stream().anyMatch(t -> t.enchantmentKey().identifier().getPath().equals("unbreaking")),
                    "GUI target action failed");
            checkLayout(screen, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
        context.takeScreenshot("ui-targets-minimum");
        context.runOnClient(mc -> click(mc.gui.screen(), "Done"));
        context.waitTicks(2);
        context.runOnClient(mc -> {
            check(mc.gui.screen() == null, "Done did not close settings");
            check(HudView.current() != null && HudView.current().trades().getFirst().label().equals("Current"),
                    "HUD current label changed");
            check(HudView.current().trades().stream().noneMatch(r -> r.label().equals("+0") || r.label().equals("now")),
                    "HUD still has the flickering numeric current row");
            VillagerTradingPredictor.LOGGER.info("[VTP UI TEST] PASS: all tabs at scales 1/2/3 and 320x240, no clipped or overlapping controls, search/targets/calibration.");
        });
        context.waitTicks(15);
        context.takeScreenshot("ui-hud-minimum");
        VtpCustomizationGameTest.run(context);
    }
}
