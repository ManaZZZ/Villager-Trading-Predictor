package net.manaz.vtp.gui;

import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.mixin.accessor.VillagerTradeAccessor;
import net.manaz.vtp.prediction.*;
import net.manaz.vtp.render.*;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import java.util.*;
import java.util.function.Consumer;

/** Dark tooltip styling with native controls, item icons and Minecraft GUI scaling. */
public class VtpSettingsScreen extends Screen {
    private static final String[] TABS = {"Settings", "Targets", "Sequence", "Trades", "Calibrate"};
    private static final String[] LEVELS = {"", "Novice", "Apprentice", "Journeyman", "Expert", "Master"};
    private static final String[] PROFESSIONS = {"armorer", "butcher", "cartographer", "cleric", "farmer",
            "fisherman", "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith"};
    private static int lastTab;
    private int tab = lastTab;
    private int wx, wy, ww, wh, left, right, column, top, bottom;
    private record Label(String text, int x, int y, int width, int color) {}
    private final List<Label> labels = new ArrayList<>();
    private final Map<String, VtpList> lists = new HashMap<>();
    private final Map<String, Integer> scrolls = new HashMap<>();
    private List<Holder<Enchantment>> enchantments = List.of();
    private Holder<Enchantment> chosen;
    private String query = "", price = "", maxSearch = String.valueOf(PredictionEngine.getMaxRerolls());
    private String seedText = SeedProvider.getSeed().isPresent() ? String.valueOf(SeedProvider.getSeed().orElseThrow()) : "";
    private String message = "";
    private int chosenLevel = 1, targetIndex = -1, observationIndex = -1, sequenceLevel = 1, tradeLevel = 0;
    private String professionId = "librarian";
    private Button addButton, matchButton;
    private VtpSlider priceSlider;
    private EditBox priceBox;
    private String targetPrice = "64";
    private boolean cheapestPrice, updatingPrice;
    private OptionalInt minimumPrice = OptionalInt.empty();
    private final List<VtpButton> targetLevelButtons = new ArrayList<>();
    private List<Integer> matches = List.of();
    private boolean showingMatches;
    private long viewRevision = -1;

    public VtpSettingsScreen() { super(Component.literal("Villager Trading Predictor")); }

    @Override protected void init() {
        lists.forEach((key, list) -> scrolls.put(key, list.offset()));
        lists.clear(); labels.clear(); targetLevelButtons.clear(); addButton = null;
        priceSlider = null; priceBox = null;
        ww = Math.min(520, width - 12); wh = Math.min(330, height - 12);
        wx = (width - ww) / 2; wy = (height - wh) / 2;
        left = wx + 10; column = (ww - 28) / 2; right = left + column + 8;
        top = wy + 52; bottom = wy + wh - 34;
        int tabWidth = (ww - 20 - 8) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            final int targetTab = i;
            VtpButton button = button(TABS[i], left + i * (tabWidth + 2), wy + 26, tabWidth, () -> {
                tab = targetTab; lastTab = tab; message = ""; rebuildWidgets();
            });
            button.active = i != tab;
            button.selected(i == tab).icon(tabWidth >= 90 ? switch (i) {
                case 0 -> Items.COMPARATOR; case 1 -> Items.ENCHANTED_BOOK; case 2 -> Items.LECTERN;
                case 3 -> Items.EMERALD; default -> Items.COMPASS;
            } : null);
        }
        switch (tab) {
            case 0 -> settings();
            case 1 -> targets();
            case 2 -> sequence();
            case 3 -> trades();
            case 4 -> calibration();
        }
        button("Done", wx + ww - 90, wy + wh - 27, 80, this::onClose);
        viewRevision = PredictionEngine.revision();
    }

    private VtpButton button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new VtpButton(text, x, y, w, action));
    }
    private EditBox input(String name, String value, String hint, int x, int y, int w, Consumer<String> update) {
        EditBox box = new VtpEditBox(font, x, y, w, 20, Component.literal(name));
        box.setTextColor(VtpUi.TEXT); box.setTextColorUneditable(VtpUi.MUTED); box.setTextShadow(true);
        box.setMaxLength(80); box.setValue(value); box.setHint(Component.literal(hint)); box.setResponder(update);
        box.setTooltip(Tooltip.create(Component.literal(name)));
        return addRenderableWidget(box);
    }
    private VtpList list(String id, int x, int y, int w, int h, String name) {
        VtpList list = addRenderableWidget(new VtpList(x, y, w, Math.max(24, h), name));
        lists.put(id, list); return list;
    }
    private void rows(String id, List<VtpList.Entry> entries) {
        VtpList list = lists.get(id);
        list.entries(entries);
        if (scrolls.containsKey(id)) { list.offset(scrolls.remove(id)); }
    }
    private void label(String text, int x, int y, int w) { labels.add(new Label(text, x, y, w, VtpUi.TEXT)); }
    protected boolean singleplayer() { return Minecraft.getInstance().getSingleplayerServer() != null; }

    private void settings() {
        label("Accent color", left, top, column);
        new VtpColorPicker(font, left, top + 14, column, bottom - top - 14,
                this::addRenderableWidget, this::addRenderableOnly);
        label("General", right, top, column);
        button("HUD settings", right, top + 14, column,
                () -> minecraft.gui.setScreen(new VtpHudSettingsScreen(this))).icon(Items.SPYGLASS);
        int half = (column - 4) / 2;
        label("Search limit", right, top + 38, half);
        input("Maximum rerolls to search (1–10000)", maxSearch, "500", right, top + 50, half, value -> maxSearch = value);
        button("Levels: " + (VillagerHudRenderer.isRomanNumerals() ? "I–V" : "123"), right + half + 4, top + 50, column - half - 4, () -> {
            VillagerHudRenderer.setRomanNumerals(!VillagerHudRenderer.isRomanNumerals()); saveSettings(); rebuildWidgets();
        }).setTooltip(Tooltip.create(Component.literal("Switch between numeric and Roman enchantment levels.")));
        label(singleplayer() ? "World seed (auto)" : "World seed", right, top + 76, column);
        EditBox seed = input("World seed; required on multiplayer", seedText, "Enter seed", right, top + 88,
                singleplayer() ? column : column - 48, value -> seedText = value);
        seed.active = !singleplayer();
        if (!singleplayer()) button("Set", right + column - 44, top + 88, 44, () -> {
            try {
                VtpPersistence.changeSeed(Long.parseLong(seedText.trim()));
                message = "Seed saved."; rebuildWidgets();
            } catch (NumberFormatException e) { message = "Enter a valid world seed."; }
        });
        button("Reset calibration", right, top + 112, column, () -> {
            VillagerTracker.resetCalibration(); VtpPersistence.saveCurrentWorldState(); message = "Calibration reset.";
        }).active = !singleplayer();
    }

    private void loadEnchantments() {
        enchantments = new ArrayList<>(TradeDataLoader.getTradeableEnchantments());
        enchantments.sort(Comparator.comparing(h -> h.value().description().getString()));
    }
    private void picker() {
        loadEnchantments();
        input("Search enchantments", query, "Search enchantments", left, top, column, value -> {
            query = value; scrolls.remove("enchants"); lists.get("enchants").offset(0); enchantRows();
        });
        list("enchants", left, top + 26, column, bottom - top - 26, "Enchantments");
        enchantRows();
    }
    private void enchantRows() {
        String filter = query.toLowerCase(Locale.ROOT).trim();
        List<VtpList.Entry> entries = new ArrayList<>();
        for (var enchant : enchantments) {
            String name = enchant.value().description().getString();
            if (!name.toLowerCase(Locale.ROOT).contains(filter)) continue;
            entries.add(new VtpList.Entry(name, "", VtpIcons.book(), List.of(), name, Objects.equals(enchant, chosen), () -> {
                chosen = enchant; chosenLevel = Math.min(chosenLevel, enchant.value().getMaxLevel());
                updateTargetControls(); enchantRows();
            }));
        }
        rows("enchants", entries);
    }
    private void targetControls() {
        int size = (column - 8) / 5;
        for (int level = 1; level <= 5; level++) {
            int selectedLevel = level;
            VtpButton choice = button(VillagerHudRenderer.fmtLvl(level), right + (level - 1) * (size + 2), top,
                    level == 5 ? column - 4 * (size + 2) : size, () -> {
                chosenLevel = selectedLevel; updateTargetControls();
            });
            targetLevelButtons.add(choice);
        }
        priceSlider = addRenderableWidget(new VtpSlider(right, top + 24, column - 40, 0, 60,
                cheapestPrice ? 0 : pricePosition(), amount -> cheapestPrice ? "Cheapest" : "Max: " + (targetPrice.isBlank() ? "64" : targetPrice),
                amount -> {
                    cheapestPrice = amount == 0;
                    targetPrice = cheapestPrice ? minimumPrice.isPresent() ? String.valueOf(minimumPrice.getAsInt()) : "" : String.valueOf(amount + 4);
                    refreshPrice();
                }));
        priceBox = input("Maximum base emerald price (1–64)", targetPrice, "64",
                right + column - 36, top + 24, 36, value -> {
                    if (updatingPrice) return;
                    targetPrice = value; cheapestPrice = false;
                    priceSlider.sync(pricePosition()); updateAddTarget();
                });
        priceBox.setMaxLength(2);
        addButton = button("Add target", right, top + 48, column, () -> {
            if (!availableTargetLevel(chosenLevel)) return;
            Integer p = targetPriceValue();
            if (p == null) return;
            VillagerHudRenderer.addTarget(new EnchantmentTarget(chosen.unwrapKey().orElseThrow(), chosenLevel, p));
            VtpPersistence.saveCurrentWorldState(); targetRows(); message = "Target added.";
        });
        updateTargetControls();
    }
    private boolean availableTargetLevel(int level) {
        return chosen != null && level >= 1 && level <= 5
                && level >= chosen.value().getMinLevel() && level <= chosen.value().getMaxLevel();
    }
    private void updateTargetControls() {
        if (!availableTargetLevel(chosenLevel)) {
            for (int i = 1; i <= 5; i++) if (availableTargetLevel(i)) { chosenLevel = i; break; }
        }
        for (int i = 0; i < targetLevelButtons.size(); i++) {
            int level = i + 1;
            VtpButton choice = targetLevelButtons.get(i);
            choice.active = availableTargetLevel(level);
            choice.selected(choice.active && chosenLevel == level);
            choice.setTooltip(Tooltip.create(Component.literal(chosen == null ? "Select an enchantment first."
                    : "Level " + VillagerHudRenderer.fmtLvl(level) + (choice.active ? "" : " is unavailable for this enchantment."))));
        }
        minimumPrice = TradePrices.cheapest(chosen, chosenLevel);
        if (cheapestPrice) targetPrice = minimumPrice.isPresent() ? String.valueOf(minimumPrice.getAsInt()) : "";
        refreshPrice();
    }
    private int pricePosition() {
        try { return Math.clamp(Integer.parseInt(targetPrice) - 4, 1, 60); }
        catch (NumberFormatException ignored) { return 60; }
    }
    private Integer targetPriceValue() {
        if (cheapestPrice) return minimumPrice.isPresent() ? minimumPrice.getAsInt() : null;
        if (targetPrice.isBlank()) return 64;
        try { int value = Integer.parseInt(targetPrice); if (value >= 1 && value <= 64) return value; }
        catch (NumberFormatException ignored) {}
        return null;
    }
    private void refreshPrice() {
        if (priceBox != null) {
            updatingPrice = true; priceBox.setValue(targetPrice); updatingPrice = false;
        }
        if (priceSlider != null) {
            priceSlider.sync(cheapestPrice ? 0 : pricePosition());
            priceSlider.setTooltip(Tooltip.create(Component.literal(minimumPrice.isPresent()
                    ? "Cheapest: " + minimumPrice.getAsInt() + " emeralds. Prices are before discounts."
                    : "Select a supported enchantment to find its cheapest base price.")));
        }
        updateAddTarget();
    }
    private void updateAddTarget() {
        if (addButton == null) return;
        addButton.active = availableTargetLevel(chosenLevel) && targetPriceValue() != null;
        addButton.setTooltip(chosen == null ? Tooltip.create(Component.literal("Select an enchantment first."))
                : Tooltip.create(Component.literal(chosen.value().description().getString() + " " + VillagerHudRenderer.fmtLvl(chosenLevel))));
    }
    private Integer parsePrice() {
        if (price.isBlank()) return -1;
        try {
            int value = Integer.parseInt(price.trim());
            if (value >= 1 && value <= 64) return value;
        } catch (NumberFormatException ignored) {}
        message = "Price must be 1–64, or blank."; return null;
    }
    private void targets() {
        picker(); targetControls();
        label("Saved targets", right, top + 76, column);
        list("targets", right, top + 88, column, bottom - top - 112, "Saved targets");
        targetRows();
        button("Remove selected", right, bottom - 20, column, () -> {
            if (targetIndex >= 0) {
                VillagerHudRenderer.removeTarget(targetIndex); targetIndex = -1;
                VtpPersistence.saveCurrentWorldState(); targetRows();
            }
        });
    }
    private void targetRows() {
        List<VtpList.Entry> entries = new ArrayList<>();
        var targets = VillagerHudRenderer.getTargets();
        for (int i = 0; i < targets.size(); i++) {
            int index = i;
            var target = targets.get(i);
            String text = VtpUi.name(target.enchantmentKey().identifier().toString()) + " " + VillagerHudRenderer.fmtLvl(target.minLevel());
            String hint = text + (target.maxPrice() >= 0 ? "\nMaximum base price: " + target.maxPrice() + " emeralds" : "\nAny base price");
            entries.add(new VtpList.Entry(text, "", VtpIcons.book(), List.of(), hint, i == targetIndex, () -> {
                targetIndex = index; targetRows();
            }));
        }
        rows("targets", entries);
    }

    private void sequence() {
        int full = ww - 20;
        int w = (full - 8) / 5;
        for (int i = 1; i <= 5; i++) {
            int level = i;
            VtpButton tier = button(w < 80 ? switch (i) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; default -> "V"; } : LEVELS[i], left + (i - 1) * (w + 2), top, w, () -> {
                sequenceLevel = level; scrolls.remove("sequence"); rebuildWidgets();
            });
            tier.active = sequenceLevel != i;
            tier.selected(sequenceLevel == i);
            tier.setTooltip(Tooltip.create(Component.literal(LEVELS[i])));
        }
        list("sequence", left, top + 40, full, bottom - top - 40, "Trade sequence");
        sequenceRows();
    }
    private void sequenceRows() {
        var data = VillagerTracker.getLastVillagerData().orElse(null);
        if (data == null || !SeedProvider.hasSeed()) {
            rows("sequence", List.of(VtpList.Entry.text(data == null ? "Look at a villager first." : "Set the world seed in Settings.", null)));
            return;
        }
        long seed = SeedProvider.getSeed().orElseThrow();
        var future = PredictionEngine.getForecastRounds(seed, data.profession(), sequenceLevel, 50);
        List<VtpList.Entry> entries = new ArrayList<>();
        UUID uuid = VillagerTracker.getLastVillagerUuid();
        boolean sameLevel = data.level() == sequenceLevel;
        boolean pending = sameLevel && VillagerTracker.isAwaitingOffers(uuid);
        if (sameLevel) {
            var actual = VillagerTracker.getCurrentOffers(uuid);
            var current = actual.orElseGet(() -> pending && !future.isEmpty() ? future.getFirst() : null);
            addRound(entries, "Current", current, actual.isEmpty() ? "Forecast for the pending trade set." : "Observed offers.");
        }
        if (!sameLevel && sequenceLevel <= data.level()) {
            if (VillagerTracker.hasSavedLevel(uuid, sequenceLevel)) {
                var saved = VillagerTracker.getSavedLevelOffers(uuid, sequenceLevel);
                if (saved.isEmpty()) entries.add(VtpList.Entry.text("Observed   —", null));
                for (var offer : saved) {
                    String name = VtpUi.name(offer.enchantmentKey()) + " " + VillagerHudRenderer.fmtLvl(offer.enchantmentLevel());
                    entries.add(new VtpList.Entry("Observed   " + name, offer.additionalCost() + " em",
                            VtpIcons.book(), List.of(), "Observed at " + LEVELS[sequenceLevel] + ": " + name, false, null));
                }
            } else entries.add(VtpList.Entry.text("Earlier offers not observed.", null));
        }
        for (int r = pending ? 1 : 0; r < future.size(); r++) {
            String label = sameLevel ? "+" + (pending ? r : r + 1) : "Next " + (r + 1);
            addRound(entries, label, future.get(r), "Forecast; base emerald price. Other villagers can change the shared sequence.");
        }
        if (entries.isEmpty()) entries.add(VtpList.Entry.text("Forecast unavailable.", null));
        rows("sequence", entries);
    }
    private void addRound(List<VtpList.Entry> entries, String prefix, List<TradeSimulator.SimulatedOffer> offers, String hint) {
        for (var row : HudView.bookRows(prefix, offers, true)) {
            entries.add(new VtpList.Entry(prefix + "   " + row.text(), row.price() >= 0 ? row.price() + " em" : "",
                    row.text().equals("—") ? VtpIcons.of(Items.BOOK) : VtpIcons.book(), List.of(),
                    prefix + ": " + row.text() + "\n" + hint, row.match(), null));
        }
    }

    private void trades() {
        int profW = Math.min(150, column), tradeX = left + profW + 8, tradeW = ww - 20 - profW - 8;
        list("professions", left, top, profW, bottom - top, "Professions");
        List<VtpList.Entry> profs = new ArrayList<>();
        for (String id : PROFESSIONS) {
            profs.add(new VtpList.Entry(VtpUi.name(id), "", VtpIcons.profession(id), List.of(), VtpUi.name(id), professionId.equals(id), () -> {
                professionId = id; scrolls.remove("trades"); rebuildWidgets();
            }));
        }
        rows("professions", profs);
        button(tradeLevel == 0 ? "All levels" : LEVELS[tradeLevel], tradeX, top, tradeW, () -> {
            tradeLevel = (tradeLevel + 1) % 6; scrolls.remove("trades"); rebuildWidgets();
        });
        list("trades", tradeX, top + 26, tradeW, bottom - top - 26, "Available trades");
        List<VtpList.Entry> entries = new ArrayList<>();
        var mc = Minecraft.getInstance();
        if (mc.level == null) { rows("trades", entries); return; }
        var profession = mc.level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION)
                .getValue(Identifier.fromNamespaceAndPath("minecraft", professionId));
        if (profession == null) { rows("trades", entries); return; }
        for (int level = 1; level <= 5; level++) {
            if (tradeLevel != 0 && tradeLevel != level) continue;
            var key = profession.getTrades(level);
            if (key == null) continue;
            var set = TradeDataLoader.getTradeSet(key);
            if (set.isEmpty()) continue;
            entries.add(VtpList.Entry.text(LEVELS[level], null));
            for (var holder : TradeDataLoader.getTradesFromSet(set.get())) {
                var trade = (VillagerTradeAccessor) holder.value();
                ItemStack a = cost(trade.vtp$getWants());
                ItemStack b = trade.vtp$getAdditionalWants().map(VtpSettingsScreen::cost).orElse(ItemStack.EMPTY);
                ItemStack result = new ItemStack(trade.vtp$getGives().item().value(), trade.vtp$getGives().count());
                entries.add(new VtpList.Entry(result.getHoverName().getString(), "", ItemStack.EMPTY, List.of(a, b, result),
                        result.getHoverName().getString(), false, null));
            }
        }
        rows("trades", entries);
    }
    private static ItemStack cost(TradeCost cost) {
        int count = cost.count() instanceof ConstantValue value ? (int) value.value() : 1;
        return new ItemStack(cost.item().value(), Math.max(1, count));
    }

    private void calibration() {
        if (singleplayer()) {
            label("Singleplayer stays aligned automatically.", left, top + 8, ww - 20);
            label("Calibration is only needed on multiplayer.", left, top + 25, ww - 20);
            label("You can inspect the trade sequence in the Sequence tab.", left, top + 48, ww - 20);
            return;
        }
        label("Observed rounds", left, top, column);
        list("observations", left, top + 16, column, bottom - top - 64, "Observed rounds or calibration matches");
        int half = (column - 4) / 2;
        button("Remove", left, bottom - 44, half, () -> {
            if (observationIndex >= 0 && !showingMatches) {
                CalibrationListStore.remove(observationIndex); CalibrationService.clear();
                observationIndex = -1; matches = List.of(); observationRows();
            }
        });
        button("Clear", left + half + 4, bottom - 44, column - half - 4, () -> {
            CalibrationListStore.clear(); matches = List.of(); showingMatches = false; observationRows();
        });
        matchButton = button(showingMatches ? "Back to observations" : "Find matches", left, bottom - 20, column, () -> {
            if (showingMatches) { showingMatches = false; observationRows(); }
            else findMatches();
        });

        label("Enchantment", right, top, column);
        Button choose = button(chosen == null ? "Choose enchantment" : VtpUi.fit(font, chosen.value().description().getString(), column - 10),
                right, top + 14, column, () -> minecraft.gui.setScreen(new EnchantmentPickerScreen(this, value -> {
                    chosen = value; chosenLevel = Math.min(chosenLevel, value.value().getMaxLevel());
                })));
        if (chosen != null) choose.setTooltip(Tooltip.create(chosen.value().description()));
        button("Level: " + VillagerHudRenderer.fmtLvl(chosenLevel), right, top + 38, column, () -> {
            chosenLevel = chosenLevel % chosen.value().getMaxLevel() + 1; rebuildWidgets();
        }).active = chosen != null && chosen.value().getMaxLevel() > 1;
        input("Optional base emerald price (1–64)", price, "Base price: any", right, top + 62, column, value -> price = value);
        button("Add observation", right, top + 86, column, this::addObservation).active = chosen != null;
        button("Add no book", right, top + 110, column, () -> {
            if (CalibrationListStore.size() >= 100) { message = "Maximum 100 observations."; return; }
            CalibrationListStore.add(CalibrationListStore.Observation.missing());
            matches = List.of(); showingMatches = false; observationRows(); message = "Observation added.";
        });
        observationRows();
    }
    private void addObservation() {
        if (CalibrationListStore.size() >= 100) { message = "Maximum 100 observations."; return; }
        Integer p = parsePrice();
        if (p == null) return;
        if (chosen == null) { message = "Select an enchantment first."; return; }
        CalibrationListStore.add(CalibrationListStore.Observation.ofTrade(chosen.unwrapKey().orElseThrow(), chosenLevel, p));
        matches = List.of(); showingMatches = false; observationRows(); message = "Observation added.";
    }
    private void observationRows() {
        if (!lists.containsKey("observations")) return;
        if (matchButton != null) matchButton.setMessage(Component.literal(showingMatches ? "Back to observations" : "Find matches"));
        List<VtpList.Entry> entries = new ArrayList<>();
        if (showingMatches) {
            for (int round : matches) entries.add(VtpList.Entry.text("Confirm round " + round, () -> {
                var data = VillagerTracker.getLastVillagerData().orElse(null);
                boolean ok = data != null && CalibrationService.confirm(VillagerTracker.getLastVillagerUuid(), data, round);
                message = ok ? "Calibration applied." : "Observation changed; search again.";
                showingMatches = false; matches = List.of(); rebuildWidgets();
            }));
        } else {
            var observations = CalibrationListStore.getAll();
            for (int i = 0; i < observations.size(); i++) {
                int index = i;
                var o = observations.get(i);
                String value = o.noTrade() ? "No book" : VtpUi.name(o.enchantment().identifier().toString()) + " " + VillagerHudRenderer.fmtLvl(o.enchantmentLevel());
                entries.add(new VtpList.Entry((i + 1) + ". " + value, o.price() >= 0 ? o.price() + " em" : "",
                        o.noTrade() ? VtpIcons.of(Items.BOOK) : VtpIcons.book(), List.of(), value, observationIndex == i, () -> { observationIndex = index; observationRows(); }));
            }
        }
        rows("observations", entries);
    }
    private void findMatches() {
        var data = VillagerTracker.getLastVillagerData().orElse(null);
        if (data == null) { message = "Look at a villager first."; return; }
        String problem = CalibrationService.problem(data);
        if (!problem.isEmpty()) { message = problem; return; }
        if (CalibrationListStore.toSpecs().isEmpty()) { message = "Add observations from this villager."; return; }
        UUID uuid = VillagerTracker.getLastVillagerUuid();
        matches = CalibrationService.search(uuid, data, CalibrationListStore.toSpecs());
        if (matches.isEmpty()) message = "No match. Check observations and seed.";
        else if (matches.size() == 1) {
            message = CalibrationService.confirm(uuid, data, matches.getFirst()) ? "Calibration applied." : "Observation changed; search again.";
            matches = List.of();
        } else {
            showingMatches = true; message = matches.size() + " matches. Select a round to confirm."; rebuildWidgets();
        }
    }

    @Override public void tick() {
        if (tab == 2 && viewRevision != PredictionEngine.revision()) {
            sequenceRows(); viewRevision = PredictionEngine.revision();
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        VtpUi.frame(g, wx, wy, ww, wh);
        String title = getTitle().getString();
        g.item(VtpIcons.of(Items.LECTERN), wx + 8, wy + 5);
        g.text(font, title, wx + 30, wy + 10, VtpUi.TEXT, true);
        for (var label : labels) VtpUi.text(g, font, label.text(), label.x(), label.y(), label.width(), label.color(), mx, my);
        if (tab == 2) {
            VtpUi.text(g, font, "Round / enchanted book", left + 5, top + 27, ww - 100, VtpUi.MUTED, mx, my);
            g.text(font, "Emeralds", wx + ww - 58, top + 27, VtpUi.MUTED, true);
        }
        g.fill(left, wy + wh - 32, wx + ww - 10, wy + wh - 31, VtpUi.EDGE);
        String footer = !message.isEmpty() ? message : tab == 2 ? "Prices shown before discounts."
                : tab == 4 && !singleplayer() ? "Add consecutive rounds from one villager." : tab == 0 ? trackingStatus() : "";
        VtpUi.text(g, font, footer, left, wy + wh - 20, ww - 112, VtpUi.MUTED, mx, my);
        super.extractRenderState(g, mx, my, delta);
    }
    private String trackingStatus() {
        return VillagerTracker.getLastVillagerData().map(d -> VillagerTracker.status(VillagerTracker.getLastVillagerUuid(), d))
                .orElse("Look at a villager.");
    }
    @Override public void removed() { saveSettings(); super.removed(); }
    private void saveSettings() { VtpPersistence.saveGlobalSettings(); }
    @Override public void onClose() {
        try {
            int max = Integer.parseInt(maxSearch.trim());
            if (max < 1 || max > 10000) throw new NumberFormatException();
            if (max != PredictionEngine.getMaxRerolls()) PredictionEngine.setMaxRerolls(max);
        } catch (NumberFormatException e) {
            message = "Search limit must be 1–10000."; tab = 0; rebuildWidgets(); return;
        }
        saveSettings(); VtpPersistence.saveCurrentWorldState(); super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
}
