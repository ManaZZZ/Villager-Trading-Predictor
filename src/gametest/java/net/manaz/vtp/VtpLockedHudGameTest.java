package net.manaz.vtp;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.gui.*;
import net.manaz.vtp.render.*;
import net.manaz.vtp.prediction.*;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.gui.components.Button;
import java.util.*;
import java.util.function.IntConsumer;
import static net.manaz.vtp.VtpUiGameTest.*;

final class VtpLockedHudGameTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void run(ClientGameTestContext context, IntConsumer configureVillager) {
        context.setScreen(() -> null);
        context.runOnClient(mc -> {
            VillagerHudRenderer.setEnabled(true); VillagerHudRenderer.setShowLevels(true);
            HudVisibility.configure(HudVisibility.Mode.NEVER, 10, 16); HudSize.set(100);
            mc.getWindow().setWindowed(1280, 720); mc.options.guiScale().set(2); mc.resizeGui();
        });
        configureVillager.accept(1);
        context.waitFor(mc -> HudView.current() != null && HudView.current().locked() && HudView.current().trades().size() == 1);
        context.runOnClient(mc -> {
            var view = HudView.current();
            check(view.trades().stream().noneMatch(r -> r.label().startsWith("+")), "Traded novice still shows rerolls");
            check(view.levels().stream().map(HudView.Row::label).distinct().toList().equals(List.of("II", "III", "IV", "V")),
                    "Traded novice future tiers missing");
            check(VillagerTracker.getVillagerSavedStates().get(view.uuid()).tradeLocked(), "Novice lock not included in saved state");
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                    net.minecraft.resources.Identifier.withDefaultNamespace("efficiency"));
            var data = VillagerTracker.getLastVillagerData().orElseThrow();
            var result = PredictionEngine.predictForVillager(mc.getSingleplayerServer().overworld().getSeed(),
                    view.uuid(), data, new EnchantmentTarget(key, 5));
            check(!result.found() && result.maxRerollsSearched() == 0, "Traded novice gets impossible reroll targets");
        });
        configureVillager.accept(2);
        context.waitFor(mc -> HudView.current() != null && HudView.current().locked() && HudView.current().trades().size() == 3);
        context.runOnClient(mc -> {
            var view = HudView.current();
            check(view.trades().stream().map(HudView.Row::text).toList().equals(List.of("Mending 1", "Unbreaking 3", "Efficiency 5")),
                    "Observed books were joined, dropped or reordered");
            check(view.trades().stream().map(HudView.Row::price).toList().equals(List.of(10, 11, 17)), "Per-book prices lost");
            check(view.trades().stream().noneMatch(HudView.Row::forecast), "Locked offers marked predicted");
            check(view.levels().stream().allMatch(HudView.Row::forecast), "Observed books duplicated in future tiers");
            check(view.levels().stream().map(HudView.Row::label).distinct().toList().equals(List.of("III", "IV", "V")),
                    "Future profession tiers missing");
            var data = VillagerTracker.getLastVillagerData().orElseThrow();
            var expected = new ArrayList<HudView.Row>();
            for (int level = 3; level <= 5; level++) {
                var offers = PredictionEngine.getForecastRounds(mc.getSingleplayerServer().overworld().getSeed(), data.profession(), level, 1);
                expected.addAll(HudView.bookRows(level == 3 ? "III" : level == 4 ? "IV" : "V", offers.getFirst(), true));
            }
            check(view.levels().equals(expected), "Future rows no longer reflect the prediction engine");
            var combined = new ArrayList<TradeSimulator.SimulatedOffer>(VillagerTracker.getCurrentOffers(view.uuid()).orElseThrow());
            var forecasts = HudView.bookRows("III", combined, true);
            check(forecasts.size() == 3 && forecasts.stream().allMatch(HudView.Row::forecast), "Multi-book forecasts must also be separate rows");
            for (int[] dimensions : new int[][]{{320, 240}, {427, 240}, {640, 360}}) {
                for (int size : new int[]{50, 100, 150, 200}) {
                    var layout = VillagerHudRenderer.layout(view, dimensions[0], dimensions[1], size, true);
                    int bottom = layout.top() + VillagerHudRenderer.rowsHeight(view.trades()) + 6
                            + VillagerHudRenderer.rowsHeight(view.levels());
                    check(bottom <= layout.bottom(), "Locked predictions hidden by screen fitting");
                }
            }
        });
        context.waitTicks(8);
        context.takeScreenshot("locked-three-books-and-predictions");
        context.setScreen(VtpSettingsScreen::new);
        context.runOnClient(mc -> {
            var screen = mc.gui.screen();
            boolean active = screen.children().stream().filter(e -> e instanceof Button).map(e -> (Button) e)
                    .anyMatch(b -> b.getMessage().getString().equals("Sequence") && b.active);
            if (active) click(screen, "Sequence");
            click(screen, "Apprentice");
            var list = screen.children().stream().filter(e -> e instanceof VtpList).map(e -> (VtpList) e).findFirst().orElseThrow();
            check(list.size() == 53, "Sequence must show three current books plus fifty future rounds");
        });
        context.waitTicks(2);
        context.takeScreenshot("locked-sequence-books");
        context.setScreen(() -> null);
        context.runOnClient(mc -> { mc.options.guiScale().set(3); mc.resizeGui(); HudSize.set(200); });
        context.waitTicks(10);
        context.takeScreenshot("locked-and-predicted-small-gui");
        configureVillager.accept(5);
        context.waitFor(mc -> HudView.current() != null && HudView.current().trades().size() == 5);
        context.runOnClient(mc -> {
            var view = HudView.current();
            check(view.locked() && view.levels().isEmpty(), "Master should not predict tiers beyond V");
            check(view.trades().stream().noneMatch(r -> r.text().contains(",")), "Master books still concatenated");
        });
        context.waitTicks(5);
        context.takeScreenshot("locked-master-five-books");
        context.runOnClient(mc -> {
            HudSize.set(100);
            VillagerTradingPredictor.LOGGER.info("[VTP LOCKED HUD TEST] PASS: individual books/prices, future tiers, multi-book forecasts, master trades and small-screen fitting.");
        });
    }
}