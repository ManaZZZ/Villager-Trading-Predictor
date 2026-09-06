package net.manaz.vtp;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.gui.*;
import net.manaz.vtp.render.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.*;
import static net.manaz.vtp.VtpUiGameTest.*;

final class VtpHudSizeGameTest {
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(0, 0);
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static AbstractSliderButton slider(VtpHudSettingsScreen screen) {
        return screen.children().stream().filter(e -> e instanceof AbstractSliderButton)
                .map(e -> (AbstractSliderButton) e).findFirst().orElseThrow();
    }
    private static void drag(VtpHudSettingsScreen screen, int percent) {
        var slider = slider(screen);
        double x = slider.getX() + 4 + (slider.getWidth() - 8) * (percent - 50) / 150.0;
        screen.mouseClicked(new MouseButtonEvent(x, slider.getY() + 10, LEFT), false);
    }
    private static void release(VtpHudSettingsScreen screen) {
        var slider = slider(screen);
        screen.mouseReleased(new MouseButtonEvent(slider.getX() + 4, slider.getY() + 10, LEFT));
    }
    static void run(ClientGameTestContext context) {
        context.setScreen(VtpSettingsScreen::new);
        context.runOnClient(mc -> {
            HudSize.set(100);
            click(mc.gui.screen(), "HUD settings");
            var screen = (VtpHudSettingsScreen) mc.gui.screen();
            checkLayout(screen, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            drag(screen, 50);
            check(screen.previewPercent() == 50 && screen.isResizing(), "Live resize did not start");
            check(slider(screen).getMessage().getString().equals("HUD size: 50%"), "Slider minimum failed");
            var slider = slider(screen);
            screen.mouseDragged(new MouseButtonEvent(slider.getRight() - 4, slider.getY() + 10, LEFT), slider.getWidth() - 8, 0);
            check(screen.previewPercent() == 200, "Preview did not follow drag");
            check(HudSize.percent() == 100, "Preview overwrote saved size");
            release(screen);
            check(!screen.isResizing(), "Resize mode did not end on release");
            click(screen, "Cancel");
            check(HudSize.percent() == 100, "Cancel changed HUD size");
            click(mc.gui.screen(), "HUD settings");
        });
        for (int percent : new int[]{75, 150}) {
            context.runOnClient(mc -> drag((VtpHudSettingsScreen) mc.gui.screen(), percent));
            context.waitTicks(2);
            context.takeScreenshot("live-hud-resize-" + percent);
            context.runOnClient(mc -> release((VtpHudSettingsScreen) mc.gui.screen()));
        }
        context.runOnClient(mc -> {
            click(mc.gui.screen(), "Apply");
            check(HudSize.percent() == 150, "Live size not applied");
            VtpPersistence.flush(); HudSize.set(100); VtpPersistence.loadGlobalSettings();
            check(HudSize.percent() == 150, "HUD size did not survive save/reload");
            click(mc.gui.screen(), "HUD settings");
            check(((VtpHudSettingsScreen) mc.gui.screen()).previewPercent() == 150, "Saved percentage not displayed");
            click(mc.gui.screen(), "100%"); click(mc.gui.screen(), "Apply"); click(mc.gui.screen(), "Done");
            var view = HudView.current();
            check(view != null && VillagerHudRenderer.rowMarker(view.trades().getFirst()).equals(">"), "Current marker not compact");
            var pending = new HudView.Row("Current", "...", -1, false, true);
            check(VillagerHudRenderer.rowMarker(pending).equals(">"), "Current marker changes while pending");
        });
        for (int[] sample : new int[][]{{1280, 720, 2, 75}, {1280, 720, 2, 125},
                {1280, 720, 2, 200}, {1280, 720, 3, 150}, {640, 480, 2, 200}}) {
            context.runOnClient(mc -> {
                mc.getWindow().setWindowed(sample[0], sample[1]);
                mc.options.guiScale().set(sample[2]); mc.resizeGui(); HudSize.set(sample[3]);
            });
            context.waitTicks(15);
            context.takeScreenshot("hud-size-" + sample[3] + "-gui-" + sample[2] + "-width-" + sample[0]);
        }
        context.runOnClient(mc -> {
            HudSize.set(HudSize.DEFAULT); VtpPersistence.saveGlobalSettings();
            VillagerTradingPredictor.LOGGER.info("[VTP HUD SIZE TEST] PASS: live drag preview, release, Cancel/Apply, persistence and screen fitting.");
        });
    }
}