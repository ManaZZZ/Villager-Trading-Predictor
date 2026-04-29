package net.manaz.vtp.render;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.manaz.vtp.gui.VtpTheme;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.prediction.PredictionResult;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public class VillagerHudRenderer implements HudElement {
    private static final Identifier HUD_ID =
            Identifier.fromNamespaceAndPath("villager-trading-predictor", "hud_overlay");

    private static final int PAD        = 5;  // panel inner padding
    private static final int LH         = 11; // line height
    private static final int HEADER_GAP = 3;  // extra gap after header line

    // ── State ─────────────────────────────────────────────────────────────────
    private static boolean enabled = true;
    private static boolean romanNumerals = false;
    private static final List<EnchantmentTarget> targets = new ArrayList<>();

    private static final String[] ROMAN = {"I","II","III","IV","V","VI","VII","VIII","IX","X"};

    public static boolean isRomanNumerals()           { return romanNumerals; }
    public static void setRomanNumerals(boolean roman) { romanNumerals = roman; }

    /** Format an enchantment level as Roman or Arabic depending on the current setting. */
    public static String fmtLvl(int level) {
        if (romanNumerals && level >= 1 && level <= ROMAN.length) return ROMAN[level - 1];
        return String.valueOf(level);
    }

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, new VillagerHudRenderer());
    }

    public static void setEnabled(boolean enabled) { VillagerHudRenderer.enabled = enabled; }
    public static boolean isEnabled()              { return enabled; }

    public static void addTarget(EnchantmentTarget target) {
        if (!targets.contains(target)) targets.add(target);
        PredictionEngine.clearCache();
    }
    public static void removeTarget(int index) {
        if (index >= 0 && index < targets.size()) {
            targets.remove(index);
            PredictionEngine.clearCache();
        }
    }
    public static void clearTargets() { targets.clear(); PredictionEngine.clearCache(); }
    public static List<EnchantmentTarget> getTargets() { return List.copyOf(targets); }
    public static void setTargets(List<EnchantmentTarget> list) {
        targets.clear(); targets.addAll(list); PredictionEngine.clearCache();
    }
    /** Backwards-compat: replaces all targets with one. */
    public static void setTarget(EnchantmentTarget target) {
        targets.clear(); targets.add(target); PredictionEngine.clearCache();
    }
    /** Backwards-compat: returns the first target or null. */
    public static EnchantmentTarget getTarget() { return targets.isEmpty() ? null : targets.get(0); }

    // ── HUD render ────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // Always track the crosshair villager so the level preview stays current
        Entity crosshairEntity = client.crosshairPickEntity;
        if (crosshairEntity instanceof Villager v) {
            VillagerTracker.updateAndSelect(v.getUUID(), v.getVillagerData());
        }

        if (!enabled || targets.isEmpty()) return;
        if (client.screen instanceof MerchantScreen) return;
        if (!(crosshairEntity instanceof Villager villager)) return;

        VillagerData villagerData = villager.getVillagerData();
        Holder<VillagerProfession> profession = villagerData.profession();

        Optional<ResourceKey<VillagerProfession>> profKeyOpt = profession.unwrapKey();
        if (profKeyOpt.isEmpty()) return;
        if (profKeyOpt.get().equals(VillagerProfession.NONE)
                || profKeyOpt.get().equals(VillagerProfession.NITWIT)) return;

        OptionalLong seedOpt = SeedProvider.getSeed();
        if (seedOpt.isEmpty()) {
            renderNoSeedMessage(graphics, client.font);
            return;
        }

        long worldSeed = seedOpt.getAsLong();
        int tradeSetTotal = VillagerTracker.getTradeSetTotal(villagerData);

        ResourceKey<net.minecraft.world.item.trading.TradeSet> tradeSetKey =
                net.manaz.vtp.trade.TradeDataLoader.getTradeSetKey(profession, villagerData.level());
        int typeOffset = tradeSetKey != null ? PredictionEngine.getOffset(tradeSetKey) : 0;

        // Use this villager's specific round, not the global counter
        int nowRound = VillagerTracker.getCurrentRound(villager.getUUID())
                .orElse(typeOffset + tradeSetTotal);

        // Find the closest target — found result with the fewest rerolls wins
        PredictionResult bestResult = null;
        EnchantmentTarget bestTarget = null;
        for (EnchantmentTarget tgt : targets) {
            PredictionResult r = PredictionEngine.predictFrom(
                    worldSeed, profession, villagerData.level(), tgt, nowRound);
            if (bestResult == null
                    || (r.found() && (!bestResult.found()
                    || r.rerollsNeeded().get() < bestResult.rerollsNeeded().get()))) {
                bestResult = r;
                bestTarget = tgt;
            }
        }

        renderPrediction(graphics, client.font, bestResult, villagerData, nowRound, bestTarget);

        if (villagerData.level() > 1) {
            renderLevelPreview(graphics, client.font, profession, villagerData.level(), worldSeed, villager.getUUID());
        } else {
            List<TradeSimulator.SimulatedOffer> nowOffers = VillagerTracker.getCurrentOffers(villager.getUUID())
                    .orElseGet(() -> PredictionEngine.simulateRound(
                            worldSeed, profession, villagerData.level(), nowRound));

            // Upcoming list is the shared global queue — same for every villager of this
            // profession+level, since the next reroll on any of them consumes the next round.
            int nextRound = VillagerTracker.getNextRoundForLevel(profession, villagerData.level());
            List<List<TradeSimulator.SimulatedOffer>> nextList = PredictionEngine.getUpcomingRounds(
                    worldSeed, profession, villagerData.level(), nextRound, 5);

            List<List<TradeSimulator.SimulatedOffer>> upcoming = new ArrayList<>();
            upcoming.add(nowOffers);
            upcoming.addAll(nextList);
            int upcomingBottom = renderUpcoming(graphics, client.font, upcoming);
            renderFutureLevels(graphics, client.font, profession, worldSeed, villager.getUUID(), upcomingBottom + 4);
        }
    }

    // ── Prediction panel (left) ───────────────────────────────────────────────

    private void renderPrediction(GuiGraphicsExtractor g, Font font,
                                  PredictionResult result, VillagerData villagerData,
                                  int nowRound,
                                  EnchantmentTarget activeTarget) {
        VtpTheme t = VtpTheme.current();
        record Line(String text, int color) {}
        List<Line> lines = new ArrayList<>();

        String profName = villagerData.profession().unwrapKey()
                .map(k -> capitalize(k.identifier().getPath())).orElse("Unknown");
        lines.add(new Line(profName + "  Lv." + villagerData.level(), t.accent));

        String tName = capitalize(activeTarget.enchantmentKey().identifier().getPath().replace("_", " "))
                + " " + fmtLvl(activeTarget.minLevel());
        if (activeTarget.maxPrice() >= 0) tName += " (\u2264" + activeTarget.maxPrice() + " em)";
        lines.add(new Line(tName, t.text));

        boolean isCurrentRoll = false;
        if (result.found()) {
            int remaining = result.rerollsNeeded().orElse(0) - nowRound;
            if (remaining <= 0) {
                lines.add(new Line("● Target is on this roll!", t.accent));
                isCurrentRoll = true;
            } else {
                lines.add(new Line("Rerolls needed: " + remaining, t.accent));
            }
            for (TradeSimulator.SimulatedOffer offer : result.targetRollOffers()) {
                if (offer.enchantment().isPresent()) {
                    TradeSimulator.EnchantmentResult ench = offer.enchantment().get();
                    String eName = capitalize(ench.enchantment().unwrapKey()
                            .map(k -> k.identifier().getPath()).orElse("?").replace("_", " "));
                    lines.add(new Line(eName + " " + fmtLvl(ench.level())
                            + "  (+" + ench.additionalCost() + " em)",
                            isCurrentRoll ? t.accent : t.muted));
                }
            }
        } else {
            lines.add(new Line("Not found in " + result.maxRerollsSearched() + " rerolls", t.error));
        }

        int maxW = lines.stream().mapToInt(l -> font.width(l.text())).max().orElse(80);
        int px = 4, py = 4;
        int pw = maxW + PAD * 2;
        int ph = lines.size() * LH + PAD * 2 + HEADER_GAP;

        if (!t.hudNoBackground) {
            g.fill(px, py, px + pw, py + ph, t.hudPanel);
            g.fill(px + 1, py + 1, px + pw - 1, py + PAD + LH + 1, t.hudPhdr);
            outline(g, px, py, pw, ph, t.border);
        }

        int tx = px + PAD, ty = py + PAD;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (isCurrentRoll && i >= 2) {
                g.fill(px + 1, ty - 1, px + pw - 1, ty + LH - 1, t.hudMatchBg);
            }
            g.text(font, line.text(), tx, ty, line.color());
            ty += LH;
            if (i == 0) ty += HEADER_GAP;
        }
    }

    // ── Upcoming rerolls panel (right) ────────────────────────────────────────

    private int renderUpcoming(GuiGraphicsExtractor g, Font font,
                               List<List<TradeSimulator.SimulatedOffer>> upcoming) {
        if (upcoming.isEmpty()) return 4;
        VtpTheme t = VtpTheme.current();

        record Line(String text, int color, boolean match) {}
        List<Line> lines = new ArrayList<>();

        lines.add(new Line("Rerolls:", t.accent, false));

        for (int i = 0; i < upcoming.size(); i++) {
            List<TradeSimulator.SimulatedOffer> round = upcoming.get(i);
            String label = i == 0 ? "now " : ("+" + i + "  ");

            List<TradeSimulator.SimulatedOffer> enchOffers = round.stream()
                    .filter(o -> o.enchantment().isPresent()).toList();

            if (enchOffers.isEmpty()) {
                lines.add(new Line(label + "\u2014", t.muted, false));
            } else {
                for (int j = 0; j < enchOffers.size(); j++) {
                    TradeSimulator.EnchantmentResult ench = enchOffers.get(j).enchantment().get();
                    String eName = capitalize(ench.enchantment().unwrapKey()
                            .map(k -> k.identifier().getPath()).orElse("?").replace("_", " "));
                    String text = (j == 0 ? label : "     ") + eName + " " + fmtLvl(ench.level())
                            + "  (+" + ench.additionalCost() + ")";
                    final TradeSimulator.EnchantmentResult enchFinal = ench;
                    boolean match = targets.stream().anyMatch(
                            tgt -> tgt.matches(enchFinal.enchantment(), enchFinal.level(), enchFinal.additionalCost()));
                    lines.add(new Line(text, match ? t.accent : t.text, match));
                }
            }
        }

        int maxW = lines.stream().mapToInt(l -> font.width(l.text())).max().orElse(60);
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int pw = maxW + PAD * 2;
        int ph = lines.size() * LH + PAD * 2 + HEADER_GAP;
        int px = screenWidth - pw - 4, py = 4;

        if (!t.hudNoBackground) {
            g.fill(px, py, px + pw, py + ph, t.hudPanel);
            g.fill(px + 1, py + 1, px + pw - 1, py + PAD + LH + 1, t.hudPhdr);
            outline(g, px, py, pw, ph, t.border);
        }

        int tx = px + PAD, ty = py + PAD;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (line.match()) {
                g.fill(px + 1, ty - 1, px + pw - 1, ty + LH - 1, t.hudMatchBg);
            }
            g.text(font, line.text(), tx, ty, line.color());
            ty += LH;
            if (i == 0) ty += HEADER_GAP;
        }
        return py + ph;
    }

    // ── Future levels panel (right, below upcoming, for level 1 villagers) ────

    private void renderFutureLevels(GuiGraphicsExtractor g, Font font,
                                    Holder<VillagerProfession> profession,
                                    long worldSeed, java.util.UUID villagerUuid, int startY) {
        VtpTheme t = VtpTheme.current();
        record Line(String text, int color) {}
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Next Levels:", t.accent));

        for (int lvl = 2; lvl <= 4; lvl++) {
            if (net.manaz.vtp.trade.TradeDataLoader.getTradeSetKey(profession, lvl) == null) continue;

            int round = VillagerTracker.getVillagerRoundForLevel(villagerUuid, profession, lvl);
            List<TradeSimulator.SimulatedOffer> offers =
                    PredictionEngine.simulateRound(worldSeed, profession, lvl, round);

            StringBuilder sb = new StringBuilder("Lv." + lvl + ": ");
            boolean any = false;
            for (TradeSimulator.SimulatedOffer offer : offers) {
                if (offer.enchantment().isPresent()) {
                    TradeSimulator.EnchantmentResult ench = offer.enchantment().get();
                    String name = capitalize(ench.enchantment().unwrapKey()
                            .map(k -> k.identifier().getPath()).orElse("?").replace("_", " "));
                    if (any) sb.append("  ");
                    sb.append(name).append(' ').append(fmtLvl(ench.level()));
                    if (ench.additionalCost() > 0) sb.append(" (+").append(ench.additionalCost()).append(')');
                    any = true;
                }
            }
            if (!any) sb.append("\u2014");

            boolean hasMatch = offers.stream()
                    .filter(o -> o.enchantment().isPresent())
                    .anyMatch(o -> {
                        TradeSimulator.EnchantmentResult e = o.enchantment().get();
                        return targets.stream().anyMatch(tgt -> tgt.matches(e.enchantment(), e.level(), e.additionalCost()));
                    });
            lines.add(new Line(sb.toString(), hasMatch ? t.accent : t.text));
        }

        if (lines.size() <= 1) return; // no levels to show

        int maxW = lines.stream().mapToInt(l -> font.width(l.text())).max().orElse(60);
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int pw = maxW + PAD * 2;
        int ph = lines.size() * LH + PAD * 2;
        int px = screenWidth - pw - 4;
        int py = startY;

        if (!t.hudNoBackground) {
            g.fill(px, py, px + pw, py + ph, t.hudPanel);
            g.fill(px + 1, py + 1, px + pw - 1, py + PAD + LH + 1, t.hudPhdr);
            outline(g, px, py, pw, ph, t.border);
        }

        int tx = px + PAD, ty = py + PAD;
        for (Line line : lines) {
            g.text(font, line.text(), tx, ty, line.color());
            ty += LH;
        }
    }

    // ── Level preview panel (right, for level 2+ villagers) ──────────────────

    private void renderLevelPreview(GuiGraphicsExtractor g, Font font,
                                    Holder<VillagerProfession> profession,
                                    int currentLevel, long worldSeed,
                                    java.util.UUID villagerUuid) {
        VtpTheme t = VtpTheme.current();
        record Line(String text, int color, boolean current) {}
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Levels:", t.accent, false));

        for (int lvl = 1; lvl <= 5; lvl++) {
            ResourceKey<net.minecraft.world.item.trading.TradeSet> tsKey =
                    net.manaz.vtp.trade.TradeDataLoader.getTradeSetKey(profession, lvl);
            if (tsKey == null) continue;

            boolean isCurrent = lvl == currentLevel;
            StringBuilder sb = new StringBuilder("Lv." + lvl + ": ");
            boolean any = false;
            boolean hasMatch = false;

            // Always use the global next-reroll prediction so this panel is identical
            // across all villagers of the same profession+level.
            int startRound = VillagerTracker.getNextRoundForLevel(profession, lvl);
            List<TradeSimulator.SimulatedOffer> offers =
                    PredictionEngine.simulateRound(worldSeed, profession, lvl, startRound);
            for (TradeSimulator.SimulatedOffer offer : offers) {
                if (offer.enchantment().isPresent()) {
                    TradeSimulator.EnchantmentResult ench = offer.enchantment().get();
                    String name = capitalize(ench.enchantment().unwrapKey()
                            .map(k -> k.identifier().getPath()).orElse("?").replace("_", " "));
                    if (any) sb.append("  ");
                    sb.append(name).append(' ').append(fmtLvl(ench.level()));
                    if (ench.additionalCost() > 0) sb.append(" (+").append(ench.additionalCost()).append(')');
                    any = true;
                    if (!hasMatch) {
                        hasMatch = targets.stream().anyMatch(tgt ->
                                tgt.matches(ench.enchantment(), ench.level(), ench.additionalCost()));
                    }
                }
            }

            if (!any) sb.append("\u2014");
            lines.add(new Line(sb.toString(), (isCurrent || hasMatch) ? t.accent : t.text, isCurrent || hasMatch));
        }

        int maxW = lines.stream().mapToInt(l -> font.width(l.text())).max().orElse(60);
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int pw = maxW + PAD * 2;
        int ph = lines.size() * LH + PAD * 2;
        int px = screenWidth - pw - 4, py = 4;

        if (!t.hudNoBackground) {
            g.fill(px, py, px + pw, py + ph, t.hudPanel);
            g.fill(px + 1, py + 1, px + pw - 1, py + PAD + LH + 1, t.hudPhdr);
            outline(g, px, py, pw, ph, t.border);
        }

        int tx = px + PAD, ty = py + PAD;
        for (Line line : lines) {
            if (line.current()) {
                g.fill(px + 1, ty - 1, px + pw - 1, ty + LH - 1, t.hudMatchBg);
            }
            g.text(font, line.text(), tx, ty, line.color());
            ty += LH;
        }
    }

    // ── No-seed message panel ─────────────────────────────────────────────────

    private void renderNoSeedMessage(GuiGraphicsExtractor g, Font font) {
        VtpTheme t = VtpTheme.current();
        String msg = "VTP  No seed set — use /vtp seed <value>";
        int w = font.width(msg) + PAD * 2;
        g.fill(4, 4, 4 + w, 4 + LH + PAD * 2, t.hudPanel);
        outline(g, 4, 4, w, LH + PAD * 2, t.border);
        g.text(font, msg, 4 + PAD, 4 + PAD, t.error);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int col) {
        g.fill(x,         y,         x + w,     y + 1,     col);
        g.fill(x,         y + h - 1, x + w,     y + h,     col);
        g.fill(x,         y + 1,     x + 1,     y + h - 1, col);
        g.fill(x + w - 1, y + 1,     x + w,     y + h - 1, col);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
