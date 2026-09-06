package net.manaz.vtp.render;

import net.manaz.vtp.gui.VtpUi;
import net.manaz.vtp.prediction.*;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import java.util.*;

/** A coherent view published after prediction preparation, never assembled during a draw. */
public record HudView(UUID uuid, String target, String result, int price, boolean found,
                      String warning, List<Row> trades, List<Row> levels, boolean locked) {
    public record Row(String label, String text, int price, boolean match, boolean forecast) {}
    private static HudView current;
    public static HudView current() { return current; }

    /** Pending generation occupies Current, so numbered rerolls always begin at +1. */
    public static int futureIndex(boolean pending, int rerolls) {
        return rerolls - (pending ? 0 : 1);
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        UUID uuid = VillagerTracker.getLastVillagerUuid();
        var dataOpt = VillagerTracker.getLastVillagerData();
        if (mc.level == null || uuid == null || dataOpt.isEmpty()) { current = null; return; }
        var data = dataOpt.get();
        if (data.profession().is(VillagerProfession.NONE) || data.profession().is(VillagerProfession.NITWIT)) {
            current = null; return;
        }
        boolean locked = data.level() > 1 || VillagerTracker.isTradeLocked(uuid);
        if (!SeedProvider.hasSeed()) {
            var observed = VillagerTracker.getCurrentOffers(uuid);
            current = new HudView(uuid, "No seed set", "Set seed in /vtp", -1, false, "",
                    observed.map(offers -> bookRows(locked ? "" : "Current", offers, false)).orElse(List.of()), List.of(), locked);
            return;
        }
        long seed = SeedProvider.getSeed().orElseThrow();
        var targets = VillagerHudRenderer.getTargets();
        PredictionResult best = null;
        EnchantmentTarget selected = null;
        for (var target : targets) {
            var result = PredictionEngine.predictForVillager(seed, uuid, data, target);
            if (best == null || result.found() && (!best.found()
                    || result.rerollsNeeded().orElseThrow() < best.rerollsNeeded().orElseThrow())) {
                best = result; selected = target;
            }
        }
        String target = selected == null ? "No target" : VtpUi.name(selected.enchantmentKey().identifier().toString())
                + " " + VillagerHudRenderer.fmtLvl(selected.minLevel());
        String result = "Add a target in /vtp";
        int price = -1;
        boolean found = best != null && best.found();
        if (found) {
            int count = best.rerollsNeeded().orElseThrow();
            result = count == 0 ? "Available" : count + (count == 1 ? " reroll" : " rerolls");
            for (var offer : best.targetRollOffers()) {
                if (offer.enchantment().isPresent()) {
                    var e = offer.enchantment().get();
                    if (selected.matches(e.enchantment(), e.level(), e.emeraldPrice())) { price = e.emeraldPrice(); break; }
                }
            }
        } else if (best != null) {
            result = locked ? "Trades locked" : best.maxRerollsSearched() > 0
                    ? "Over " + best.maxRerollsSearched() + " rerolls" : "Waiting for trades";
        }
        String status = VillagerTracker.status(uuid, data);
        String warning = status.startsWith("Uncertain") ? "Check alignment in /vtp"
                : status.startsWith("Forecast unavailable") ? "Forecast unavailable" : "";
        var future = locked ? List.<List<TradeSimulator.SimulatedOffer>>of()
                : PredictionEngine.getForecastRounds(seed, data.profession(), data.level(), 5);
        var actual = VillagerTracker.getCurrentOffers(uuid);
        boolean pending = VillagerTracker.isAwaitingOffers(uuid);
        List<Row> trades = new ArrayList<>();
        if (locked) {
            trades.addAll(actual.map(offers -> bookRows("", offers, false))
                    .orElse(List.of(new Row("", "Open trades to view", -1, false, false))));
        } else {
            trades.addAll(bookRows("Current", actual.orElseGet(() -> pending && !future.isEmpty() ? future.getFirst() : null),
                    actual.isEmpty()));
            for (int reroll = 1; reroll <= 3; reroll++) {
                int index = futureIndex(pending, reroll);
                trades.addAll(bookRows("+" + reroll, index < future.size() ? future.get(index) : null, true));
            }
        }
        List<Row> levels = new ArrayList<>();
        // A locked villager's current offers already include every unlocked tier.
        // Keep only future tiers below that list so observed books aren't repeated.
        for (int level = locked ? data.level() + 1 : 2; level <= 5; level++) {
            String label = switch (level) { case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; default -> "V"; };
            if (VillagerTracker.hasSavedLevel(uuid, level)) {
                var saved = VillagerTracker.getSavedLevelOffers(uuid, level);
                if (saved.isEmpty()) levels.add(new Row(label, "—", -1, false, false));
                for (var e : saved) {
                    boolean match = targets.stream().anyMatch(t -> t.matchesKey(e.enchantmentKey(), e.enchantmentLevel(), e.additionalCost()));
                    levels.add(new Row(label, VtpUi.name(e.enchantmentKey()) + " " + VillagerHudRenderer.fmtLvl(e.enchantmentLevel()),
                            e.additionalCost(), match, false));
                }
            } else if (level <= data.level() && !(level == data.level() && pending)) {
                levels.add(new Row(label, "Not observed", -1, false, false));
            } else {
                var rounds = PredictionEngine.getForecastRounds(seed, data.profession(), level, 1);
                levels.addAll(bookRows(label, rounds.isEmpty() ? null : rounds.getFirst(), true));
            }
        }
        current = new HudView(uuid, target, result, price, found, warning, List.copyOf(trades), List.copyOf(levels), locked);
    }

    /** Each book keeps its own name, base price and target match; no comma-joined rows. */
    public static List<Row> bookRows(String label, List<TradeSimulator.SimulatedOffer> offers, boolean forecast) {
        if (offers == null) return List.of(new Row(label, "…", -1, false, forecast));
        var books = offers.stream().flatMap(o -> o.enchantment().stream()).toList();
        if (books.isEmpty()) return List.of(new Row(label, "—", -1, false, forecast));
        return books.stream().map(e -> new Row(label,
                e.enchantment().value().description().getString() + " " + VillagerHudRenderer.fmtLvl(e.level()),
                e.emeraldPrice(), VillagerHudRenderer.getTargets().stream()
                        .anyMatch(t -> t.matches(e.enchantment(), e.level(), e.emeraldPrice())), forecast)).toList();
    }
}