package net.manaz.vtp;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.gui.*;
import net.manaz.vtp.render.*;
import net.manaz.vtp.trade.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.world.entity.npc.villager.Villager;
import static net.manaz.vtp.VtpUiGameTest.*;

final class VtpCustomizationGameTest {
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(0, 0);
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(e -> e instanceof Button).map(e -> (Button) e)
                .filter(e -> e.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static EditBox input(Screen screen, String label) {
        return screen.children().stream().filter(e -> e instanceof EditBox).map(e -> (EditBox) e)
                .filter(e -> e.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    static void checkDialogs(ClientGameTestContext context, int scale) {
        context.runOnClient(mc -> {
            var screen = mc.gui.screen();
            check(screen.children().stream().filter(e -> e instanceof Button).map(e -> (Button) e)
                    .noneMatch(e -> e.getMessage().getString().equals("Accent color...")
                            || e.getMessage().getString().equals("HUD size...") || e.getMessage().getString().equals("HUD visibility...")),
                    "Separate appearance buttons remain");
            check(input(screen, "Hex color") != null, "Inline color picker is missing");
            var plane = screen.children().stream().filter(e -> e instanceof AbstractWidget).map(e -> (AbstractWidget) e)
                    .filter(e -> e.getMessage().getString().equals("Saturation and brightness")).findFirst().orElseThrow();
            check(plane.getWidth() == plane.getHeight(), "Color area is not square");
            var hue = widget(screen, "Hue");
            check(hue.getX() == plane.getX() && hue.getWidth() == plane.getWidth()
                    && hue.getY() == plane.getY() + plane.getHeight() + 4, "Hue strip is not aligned below the square");
            check(plane.getY() == button(screen, "HUD settings").getY()
                    && plane.getX() == button(screen, "Settings").getX(), "Color picker is not aligned with the settings");
            var reset = button(screen, "Reset");
            check(reset.getX() + reset.getWidth() == button(screen, "HUD settings").getX() - 8,
                    "Picker does not fill the settings column");
            for (String channel : new String[]{"Red", "Green", "Blue"}) input(screen, channel);
            click(screen, "HUD settings");
            var toggle = button(mc.gui.screen(), "HUD: ON");
            int panelTop = toggle.getY() - 28;
            check(Math.abs(2 * panelTop + 228 - mc.getWindow().getGuiScaledHeight()) <= 1, "HUD settings isn't vertically centered");
            checkLayout(mc.gui.screen(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            for (var mode : HudVisibility.Mode.values()) {
                click(mc.gui.screen(), mode.label);
                check(input(mc.gui.screen(), "Timeout seconds").active == (mode == HudVisibility.Mode.TIME), "Timeout field gating");
                check(input(mc.gui.screen(), "Range blocks").active == (mode == HudVisibility.Mode.RANGE), "Range field gating");
            }
        });
        context.waitTicks(2);
        if (scale == 2) context.takeScreenshot("combined-hud-settings");
        context.runOnClient(mc -> click(mc.gui.screen(), "Cancel"));
        if (scale == 2) {
            context.runOnClient(mc -> setReferenceColor(mc.gui.screen()));
            context.waitTicks(2);
            context.takeScreenshot("paint-style-color-picker");
            context.runOnClient(mc -> click(mc.gui.screen(), "Reset"));
        }
    }
    private static AbstractWidget widget(Screen screen, String label) {
        return screen.children().stream().filter(e -> e instanceof AbstractWidget).map(e -> (AbstractWidget) e)
                .filter(e -> e.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }
    private static void setReferenceColor(Screen screen) {
        input(screen, "Red").setValue("44");
        input(screen, "Green").setValue("51");
        input(screen, "Blue").setValue("164");
        check(VtpAccent.color() == 0xFF2C33A4 && input(screen, "Hex color").getValue().equals("#2C33A4"),
                "RGB fields did not produce the reference color");
    }
    private static void checkColorSync(Screen screen) {
        int color = VtpAccent.color();
        check(VtpAccent.parse(input(screen, "Hex color").getValue()).orElseThrow() == color, "Hex is out of sync");
        String[] channels = {"Red", "Green", "Blue"};
        for (int i = 0; i < channels.length; i++)
            check(Integer.parseInt(input(screen, channels[i]).getValue()) == (color >> ((2 - i) * 8) & 255),
                    channels[i] + " is out of sync");
    }
    private static void pickColor(Screen screen, AbstractWidget widget, double x, double y) {
        var event = new MouseButtonEvent(widget.getX() + x, widget.getY() + y, LEFT);
        check(screen.mouseClicked(event, false), "Color control click missed");
        screen.mouseReleased(event);
    }
    private static void testColorControls(Screen screen) {
        setReferenceColor(screen);
        for (String channel : new String[]{"Red", "Green", "Blue"}) {
            var box = input(screen, channel);
            int before = VtpAccent.color();
            for (String invalid : new String[]{"", "-1", "256", "999", "abc"}) {
                box.setValue(invalid);
                check(VtpAccent.color() == before, "Invalid " + channel + " changed the accent: " + invalid);
            }
            box.setValue("0"); checkColorSync(screen);
            box.setValue("255"); checkColorSync(screen);
        }
        input(screen, "Hex color").setValue("2c33a4"); checkColorSync(screen);
        check(VtpAccent.color() == 0xFF2C33A4, "Lowercase hex without # failed");
        var plane = widget(screen, "Saturation and brightness");
        var hue = widget(screen, "Hue");
        pickColor(screen, plane, plane.getWidth() - 1, 0);
        pickColor(screen, hue, 0, hue.getHeight() / 2.0);
        check(VtpAccent.color() == 0xFFFF0000, "Hue strip left edge is not red");
        var end = new MouseButtonEvent(hue.getX() + (hue.getWidth() - 1) * 2 / 3.0, hue.getY() + 6, LEFT);
        screen.mouseClicked(new MouseButtonEvent(hue.getX(), hue.getY() + 6, LEFT), false);
        screen.mouseDragged(end, end.x() - hue.getX(), 0);
        screen.mouseReleased(end);
        check(VtpAccent.color() == 0xFF0000FF, "Horizontal hue drag did not select blue");
        checkColorSync(screen);
        int beforeArrow = VtpAccent.color();
        check(hue.keyPressed(new KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0)), "Hue keyboard control unavailable");
        check(VtpAccent.color() != beforeArrow, "Hue arrow key did not make a fine adjustment");
        checkColorSync(screen);
        input(screen, "Hex color").setValue("#FFFFFF");
        pickColor(screen, hue, (hue.getWidth() - 1) / 3.0, 6);
        check(VtpAccent.color() == 0xFFFFFFFF, "Hue changed white before choosing saturation");
        pickColor(screen, plane, plane.getWidth() - 1, 0);
        check(VtpAccent.color() == 0xFF00FF00, "Chosen hue was lost while color was white");
        input(screen, "Hex color").setValue("#000000");
        pickColor(screen, plane, plane.getWidth() - 1, 0);
        check(VtpAccent.color() == 0xFF00FF00, "Achromatic hex edit discarded the chosen hue");
        input(screen, "Red").setValue("256");
        click(screen, "Blue #5599FF");
        check(VtpAccent.color() == 0xFF5599FF, "Preset did not recover invalid RGB");
        checkColorSync(screen);
        click(screen, "Reset");
        check(VtpAccent.color() == VtpAccent.DEFAULT, "Reset did not restore green");
        checkColorSync(screen);
    }
    private static void choose(Screen screen, String query) {
        input(screen, "Search enchantments").setValue(query);
        var list = screen.children().stream().filter(e -> e instanceof VtpList).map(e -> (VtpList) e)
                .filter(e -> e.getMessage().getString().equals("Enchantments")).findFirst().orElseThrow();
        check(list.size() == 1, "Enchantment search: " + query);
        check(screen.mouseClicked(new MouseButtonEvent(list.getX() + 12, list.getY() + 10, LEFT), false), "Enchantment click missed");
        screen.mouseReleased(new MouseButtonEvent(list.getX() + 12, list.getY() + 10, LEFT));
    }
    private static void slide(Screen screen, AbstractSliderButton slider, int amount) {
        double x = slider.getX() + 4 + (slider.getWidth() - 8) * amount / 60.0;
        screen.mouseClicked(new MouseButtonEvent(x, slider.getY() + 10, LEFT), false);
        screen.mouseReleased(new MouseButtonEvent(x, slider.getY() + 10, LEFT));
    }
    private static void testPrices(Screen screen) {
        var slider = screen.children().stream().filter(e -> e instanceof AbstractSliderButton)
                .map(e -> (AbstractSliderButton) e).findFirst().orElseThrow();
        var box = input(screen, "Maximum base emerald price (1–64)");
        choose(screen, "mending");
        slide(screen, slider, 0);
        check(slider.getMessage().getString().equals("Cheapest"), "Missing Cheapest stop");
        check(box.getValue().equals("10"), "Cheapest Mending I should cost 10 emeralds");
        click(screen, "Add target");
        var target = VillagerHudRenderer.getTargets().stream().filter(t -> t.enchantmentKey().identifier().getPath().equals("mending")
                && t.maxPrice() == 10 && t.minLevel() == 1).findFirst().orElseThrow();
        var mending = TradeDataLoader.getTradeableEnchantments().stream()
                .filter(e -> e.is(target.enchantmentKey())).findFirst().orElseThrow();
        check(target.matches(mending, 1, 10) && !target.matches(mending, 1, 11), "Cheapest target price filter failed");
        for (int value = 5; value <= 64; value++) {
            slide(screen, slider, value - 4);
            check(box.getValue().equals(String.valueOf(value)), "Price slider skipped " + value);
            check(slider.getMessage().getString().equals("Max: " + value), "Price label desynchronized");
        }
        slide(screen, slider, 0);
        choose(screen, "efficiency");
        click(screen, "5");
        check(box.getValue().equals("17") && slider.getMessage().getString().equals("Cheapest"), "Cheapest did not follow Efficiency V");
        click(screen, "1");
        check(box.getValue().equals("5"), "Cheapest did not follow level change");
        choose(screen, "frost walker"); click(screen, "2");
        check(box.getValue().equals("16"), "Treasure price multiplier missing");
        box.setValue("23");
        check(slider.getMessage().getString().equals("Max: 23"), "Manual price did not synchronize slider");
        choose(screen, "mending");
        check(box.getValue().equals("23"), "Manual price changed with enchantment");
        box.setValue("0"); check(!button(screen, "Add target").active, "Zero price accepted");
        box.setValue("65"); check(!button(screen, "Add target").active, "Price over 64 accepted");
        box.setValue("xx"); check(!button(screen, "Add target").active, "Non-numeric price accepted");
        box.setValue("10"); check(button(screen, "Add target").active, "Valid typed price disabled");
        slide(screen, slider, 0);
    }

    static void run(ClientGameTestContext context) {
        context.setScreen(VtpSettingsScreen::new);
        context.runOnClient(mc -> {
            click(mc.gui.screen(), "Settings");
            var screen = mc.gui.screen();
            var plane = screen.children().stream().filter(e -> e instanceof AbstractWidget).map(e -> (AbstractWidget) e)
                    .filter(e -> e.getMessage().getString().equals("Saturation and brightness")).findFirst().orElseThrow();
            screen.mouseClicked(new MouseButtonEvent(plane.getX() + 15, plane.getY() + 10, LEFT), false);
            String clicked = input(screen, "Hex color").getValue();
            screen.mouseDragged(new MouseButtonEvent(plane.getX() + plane.getWidth() - 10, plane.getY() + plane.getHeight() - 10, LEFT), 45, 40);
            screen.mouseReleased(new MouseButtonEvent(plane.getX() + plane.getWidth() - 10, plane.getY() + plane.getHeight() - 10, LEFT));
            check(!input(screen, "Hex color").getValue().equals(clicked), "Color dragging did not update");
            int beforeInvalid = VtpAccent.color();
            input(screen, "Hex color").setValue("#GG00FF");
            check(VtpAccent.color() == beforeInvalid, "Invalid hex changed the accent");
            click(screen, "Blue #5599FF");
            check(input(screen, "Hex color").getValue().equals("#5599FF") && VtpAccent.color() == 0xFF5599FF, "Inline preset failed");
            testColorControls(screen);
            input(screen, "Hex color").setValue("#3399FF");
            check(VtpAccent.color() == 0xFF3399FF, "Typed accent not applied live");
            click(screen, "HUD settings");
            click(mc.gui.screen(), "HUD: ON");
            click(mc.gui.screen(), "Cancel");
            check(VillagerHudRenderer.isEnabled(), "Cancel committed HUD toggle");
            click(mc.gui.screen(), "HUD settings");
            screen = mc.gui.screen();
            click(screen, "After time");
            input(screen, "Timeout seconds").setValue("0");
            check(!button(screen, "Apply").active, "Zero timeout accepted");
            input(screen, "Timeout seconds").setValue("7");
            click(screen, "Out of range");
            input(screen, "Range blocks").setValue("999");
            check(!button(screen, "Apply").active, "Invalid range accepted");
            input(screen, "Range blocks").setValue("24");
            click(screen, "Apply");
            VtpPersistence.flush();
            VtpAccent.set(VtpAccent.DEFAULT); HudVisibility.configure(HudVisibility.Mode.NEVER, 10, 16);
            VtpPersistence.loadGlobalSettings();
            check(VtpAccent.color() == 0xFF3399FF, "Inline accent did not survive save/reload");
            check(HudVisibility.mode() == HudVisibility.Mode.RANGE && HudVisibility.seconds() == 7 && HudVisibility.range() == 24,
                    "Visibility settings did not survive save/reload");
            click(mc.gui.screen(), "Targets");
            screen = mc.gui.screen();
            choose(screen, "efficiency");
            for (int i = 1; i <= 5; i++) check(button(screen, String.valueOf(i)).active, "Efficiency level disabled: " + i);
            click(screen, "5"); click(screen, "Add target");
            choose(screen, "mending");
            check(button(screen, "1").active, "Mending level 1 disabled");
            for (int i = 2; i <= 5; i++) check(!button(screen, String.valueOf(i)).active, "Impossible Mending level enabled");
            choose(screen, "unbreaking");
            for (int i = 1; i <= 5; i++) check(button(screen, String.valueOf(i)).active == (i <= 3), "Unbreaking level availability");
            testPrices(screen);
            checkLayout(screen, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
        context.waitTicks(2);
        context.takeScreenshot("cheapest-mending-minimum");
        context.runOnClient(mc -> click(mc.gui.screen(), "Settings"));
        context.runOnClient(mc -> setReferenceColor(mc.gui.screen()));
        context.waitTicks(2);
        context.takeScreenshot("inline-color-settings-minimum");
        context.runOnClient(mc -> input(mc.gui.screen(), "Hex color").setValue("#3399FF"));
        context.runOnClient(mc -> click(mc.gui.screen(), "HUD settings"));
        context.takeScreenshot("combined-hud-settings-minimum");
        context.runOnClient(mc -> { click(mc.gui.screen(), "Cancel"); click(mc.gui.screen(), "Done"); });
        testVisibility(context);
        context.waitTicks(2);
        context.takeScreenshot("custom-accent-hud");
        context.runOnClient(mc -> {
            VtpAccent.set(VtpAccent.DEFAULT); HudVisibility.configure(HudVisibility.Mode.NEVER, 10, 16);
            VtpPersistence.saveGlobalSettings();
            VillagerTradingPredictor.LOGGER.info("[VTP CUSTOMIZATION TEST] PASS: square color picker, horizontal hue, RGB/hex synchronization and validation, presets, persistence, Cheapest and every price stop, manual price, level controls, grouped HUD rules.");
        });
    }

    private static void testVisibility(ClientGameTestContext context) {
        float[] rotation = context.computeOnClient(mc -> new float[]{mc.player.getXRot(), mc.player.getYRot()});
        context.runOnClient(mc -> {
            var uuid = VillagerTracker.getLastVillagerUuid();
            var before = VillagerTracker.getTradeSetTotals();
            Villager villager = null;
            for (var e : mc.level.entitiesForRendering())
                if (e instanceof Villager v && uuid.equals(v.getUUID())) { villager = v; break; }
            check(villager != null, "Selected villager is missing from client world");
            var previousHit = mc.crosshairPickEntity;
            var previousPos = mc.player.position();
            try {
                HudVisibility.configure(HudVisibility.Mode.LOOKING, 1, 4);
                mc.crosshairPickEntity = villager; HudVisibility.tick(mc);
                check(HudVisibility.visible(uuid), "Looking at selected villager should show HUD");
                mc.crosshairPickEntity = null; HudVisibility.tick(mc);
                check(!HudVisibility.visible(uuid), "Looking away should hide HUD");
                HudVisibility.configure(HudVisibility.Mode.RANGE, 1, 4);
                mc.player.setPos(villager.getX(), villager.getY(), villager.getZ() + 2); HudVisibility.tick(mc);
                check(HudVisibility.visible(uuid), "Nearby selected villager should show HUD");
                mc.player.setPos(villager.getX(), villager.getY(), villager.getZ() + 8); HudVisibility.tick(mc);
                check(!HudVisibility.visible(uuid), "Out-of-range villager should hide HUD");
                HudVisibility.configure(HudVisibility.Mode.NEVER, 1, 4); HudVisibility.tick(mc);
                check(HudVisibility.visible(uuid), "Never mode hid last selected villager");
                HudVisibility.configure(HudVisibility.Mode.TIME, 1, 4);
                mc.crosshairPickEntity = villager; HudVisibility.tick(mc);
                check(HudVisibility.visible(uuid), "Looking should refresh timeout");
            } finally {
                mc.player.setPos(previousPos.x, previousPos.y, previousPos.z);
                mc.crosshairPickEntity = previousHit;
            }
            check(before.equals(VillagerTracker.getTradeSetTotals()), "Visibility changed prediction state");
            mc.player.setXRot(-85);
        });
        context.waitTicks(26);
        context.runOnClient(mc -> {
            var uuid = VillagerTracker.getLastVillagerUuid();
            check(!HudVisibility.visible(uuid), "Timed HUD did not disappear after looking away");
            mc.player.setXRot(rotation[0]); mc.player.setYRot(rotation[1]);
            HudVisibility.configure(HudVisibility.Mode.NEVER, 10, 16); HudVisibility.tick(mc);
            check(HudVisibility.visible(uuid), "Never mode did not restore HUD after timeout");
            HudVisibility.reset();
            check(!HudVisibility.visible(uuid), "Reset leaked visibility into another session");
            HudVisibility.tick(mc);
        });
    }
}