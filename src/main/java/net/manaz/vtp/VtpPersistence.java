package net.manaz.vtp;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.manaz.vtp.autoreroll.AutoRerollManager;
import net.manaz.vtp.gui.VtpTheme;
import net.manaz.vtp.prediction.CalibrationListStore;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.TradeSet;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Saves and loads per-world mod state to config/vtp/<seed>.json.
 * Also saves/loads global user preferences to config/vtp/settings.json.
 */

public class VtpPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIR = "vtp";

    /** Saves per-world state for the current seed, if one is available. */
    public static void saveCurrentWorldState() {
        SeedProvider.getSeed().ifPresent(VtpPersistence::save);
    }

    public static void save(long worldSeed) {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve(DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(worldSeed + ".json");

            JsonObject root = new JsonObject();
            JsonObject offsets = new JsonObject();
            PredictionEngine.getAllOffsets().forEach((key, value) ->
                    offsets.addProperty(key.identifier().toString(), value));
            root.add("sequenceOffsets", offsets);

            java.util.List<EnchantmentTarget> targets = VillagerHudRenderer.getTargets();
            if (!targets.isEmpty()) {
                JsonArray tgtsArr = new JsonArray();
                for (EnchantmentTarget target : targets) {
                    JsonObject tgt = new JsonObject();
                    tgt.addProperty("enchantment", target.enchantmentKey().identifier().toString());
                    tgt.addProperty("level", target.minLevel());
                    tgt.addProperty("maxPrice", target.maxPrice());
                    tgtsArr.add(tgt);
                }
                root.add("targets", tgtsArr);
            }

            JsonObject totals = new JsonObject();
            VillagerTracker.getTradeSetTotals().forEach(totals::addProperty);
            root.add("tradeSetTotals", totals);

            // Save ALL seen villagers (rerollCount + currentRound) so re-sighting on
            // reload does not incorrectly advance the sequence counter.
            JsonObject villagers = new JsonObject();
            VillagerTracker.getVillagerSavedStates().forEach((uuid, saved) -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("rerolls", saved.rerollCount());
                entry.addProperty("round", saved.currentRound());
                if (saved.lastKnownLevel() > 0) {
                    entry.addProperty("lastLevel", saved.lastKnownLevel());
                }
                if (!saved.levelRounds().isEmpty()) {
                    JsonObject lvlRounds = new JsonObject();
                    saved.levelRounds().forEach((lvl, round) ->
                            lvlRounds.addProperty(String.valueOf(lvl), round));
                    entry.add("levelRounds", lvlRounds);
                }
                if (!saved.levelOffers().isEmpty()) {
                    JsonObject offersObj = new JsonObject();
                    saved.levelOffers().forEach((lvl, offers) -> {
                        JsonArray arr = new JsonArray();
                        offers.forEach(o -> {
                            JsonObject oe = new JsonObject();
                            oe.addProperty("ench", o.enchantmentKey());
                            oe.addProperty("lvl", o.enchantmentLevel());
                            oe.addProperty("cost", o.additionalCost());
                            arr.add(oe);
                        });
                        offersObj.add(String.valueOf(lvl), arr);
                    });
                    entry.add("offers", offersObj);
                }
                villagers.add(uuid.toString(), entry);
            });
            root.add("villagers", villagers);

            UUID lastUuid = VillagerTracker.getLastVillagerUuid();
            if (lastUuid != null) {
                root.addProperty("lastVillager", lastUuid.toString());
            }

            java.util.List<CalibrationListStore.Observation> calObs = CalibrationListStore.getAll();
            if (!calObs.isEmpty()) {
                JsonArray calArr = new JsonArray();
                for (CalibrationListStore.Observation o : calObs) {
                    JsonObject oe = new JsonObject();
                    if (o.noTrade()) {
                        oe.addProperty("noTrade", true);
                    } else {
                        oe.addProperty("ench", o.enchantment().identifier().toString());
                        oe.addProperty("lvl", o.enchantmentLevel());
                        oe.addProperty("price", o.price());
                    }
                    calArr.add(oe);
                }
                root.add("calibrationObservations", calArr);
            }

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
            VillagerTradingPredictor.LOGGER.info("[VTP] Saved state for seed {}", worldSeed);
        } catch (IOException e) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to save state", e);
        }
    }

    public static void load(long worldSeed) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(DIR).resolve(worldSeed + ".json");
        if (!Files.exists(file)) {
            VillagerTradingPredictor.LOGGER.info("[VTP] No saved state found for seed {}", worldSeed);
            return;
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);

            Map<ResourceKey<TradeSet>, Integer> offsets = new HashMap<>();
            if (root.has("sequenceOffsets")) {
                root.getAsJsonObject("sequenceOffsets").entrySet().forEach(e -> {
                    try {
                        ResourceKey<TradeSet> key = ResourceKey.create(
                                Registries.TRADE_SET, Identifier.parse(e.getKey()));
                        offsets.put(key, e.getValue().getAsInt());
                    } catch (Exception ignored) {}
                });
            } else if (root.has("globalOffset")) {
                // Legacy: single globalOffset not supported with per-type model — discard silently
            }
            PredictionEngine.loadOffsets(offsets);

            java.util.List<EnchantmentTarget> loadedTargets = new java.util.ArrayList<>();
            if (root.has("targets")) {
                root.getAsJsonArray("targets").forEach(el -> {
                    try {
                        JsonObject tgt = el.getAsJsonObject();
                        String enchStr = tgt.get("enchantment").getAsString();
                        int level = tgt.get("level").getAsInt();
                        int maxPrice = tgt.has("maxPrice") ? tgt.get("maxPrice").getAsInt() : -1;
                        ResourceKey<Enchantment> enchKey = ResourceKey.create(
                                Registries.ENCHANTMENT, Identifier.parse(enchStr));
                        loadedTargets.add(new EnchantmentTarget(enchKey, level, maxPrice));
                    } catch (Exception ignored) {}
                });
            } else if (root.has("target")) {
                // Legacy: single target object
                try {
                    JsonObject tgt = root.getAsJsonObject("target");
                    String enchStr = tgt.get("enchantment").getAsString();
                    int level = tgt.get("level").getAsInt();
                    int maxPrice = tgt.has("maxPrice") ? tgt.get("maxPrice").getAsInt() : -1;
                    ResourceKey<Enchantment> enchKey = ResourceKey.create(
                            Registries.ENCHANTMENT, Identifier.parse(enchStr));
                    loadedTargets.add(new EnchantmentTarget(enchKey, level, maxPrice));
                } catch (Exception ignored) {}
            }
            if (!loadedTargets.isEmpty()) {
                VillagerHudRenderer.setTargets(loadedTargets);
            }

            Map<String, Integer> totals = new HashMap<>();
            if (root.has("tradeSetTotals")) {
                root.getAsJsonObject("tradeSetTotals").entrySet()
                        .forEach(e -> totals.put(e.getKey(), e.getValue().getAsInt()));
            }

            Map<UUID, VillagerTracker.VillagerSavedState> states = new HashMap<>();
            // Support both the new "villagers" format and the legacy "villagerRerolls" format.
            if (root.has("villagers")) {
                root.getAsJsonObject("villagers").entrySet().forEach(e -> {
                    try {
                        UUID uuid = UUID.fromString(e.getKey());
                        JsonObject obj = e.getValue().getAsJsonObject();
                        int rerolls    = obj.has("rerolls")    ? obj.get("rerolls").getAsInt()    : 0;
                        int round      = obj.has("round")      ? obj.get("round").getAsInt()      : -1;
                        int lastLevel  = obj.has("lastLevel")  ? obj.get("lastLevel").getAsInt()  : -1;
                        java.util.Map<Integer, Integer> levelRounds = new java.util.HashMap<>();
                        if (obj.has("levelRounds")) {
                            obj.getAsJsonObject("levelRounds").entrySet().forEach(le -> {
                                try {
                                    levelRounds.put(Integer.parseInt(le.getKey()),
                                            le.getValue().getAsInt());
                                } catch (Exception ignored2) {}
                            });
                        }
                        java.util.Map<Integer, java.util.List<VillagerTracker.OfferData>> levelOffers =
                                new java.util.HashMap<>();
                        if (obj.has("offers")) {
                            obj.getAsJsonObject("offers").entrySet().forEach(le -> {
                                try {
                                    int lvl = Integer.parseInt(le.getKey());
                                    java.util.List<VillagerTracker.OfferData> offers =
                                            new java.util.ArrayList<>();
                                    le.getValue().getAsJsonArray().forEach(el -> {
                                        try {
                                            JsonObject oe = el.getAsJsonObject();
                                            offers.add(new VillagerTracker.OfferData(
                                                    oe.get("ench").getAsString(),
                                                    oe.get("lvl").getAsInt(),
                                                    oe.get("cost").getAsInt()));
                                        } catch (Exception ignored3) {}
                                    });
                                    levelOffers.put(lvl, offers);
                                } catch (Exception ignored2) {}
                            });
                        }
                        states.put(uuid, new VillagerTracker.VillagerSavedState(
                                rerolls, round, levelRounds, lastLevel, levelOffers));
                    } catch (IllegalArgumentException ignored) {}
                });
            } else if (root.has("villagerRerolls")) {
                // Legacy format — reroll count only, no round info
                root.getAsJsonObject("villagerRerolls").entrySet().forEach(e -> {
                    try {
                        UUID uuid = UUID.fromString(e.getKey());
                        int rerolls = e.getValue().getAsInt();
                        states.put(uuid, new VillagerTracker.VillagerSavedState(
                                rerolls, -1, new java.util.HashMap<>(), -1, new java.util.HashMap<>()));
                    } catch (IllegalArgumentException ignored) {}
                });
            }

            VillagerTracker.loadState(totals, states);

            if (root.has("lastVillager")) {
                try {
                    VillagerTracker.setLastVillagerUuid(
                            UUID.fromString(root.get("lastVillager").getAsString()));
                } catch (IllegalArgumentException ignored) {}
            }

            java.util.List<CalibrationListStore.Observation> calObs = new java.util.ArrayList<>();
            if (root.has("calibrationObservations")) {
                root.getAsJsonArray("calibrationObservations").forEach(el -> {
                    try {
                        JsonObject oe = el.getAsJsonObject();
                        if (oe.has("noTrade") && oe.get("noTrade").getAsBoolean()) {
                            calObs.add(CalibrationListStore.Observation.missing());
                        } else {
                            ResourceKey<Enchantment> enchKey = ResourceKey.create(
                                    Registries.ENCHANTMENT, Identifier.parse(oe.get("ench").getAsString()));
                            int lvl = oe.get("lvl").getAsInt();
                            int price = oe.has("price") ? oe.get("price").getAsInt() : -1;
                            calObs.add(CalibrationListStore.Observation.ofTrade(enchKey, lvl, price));
                        }
                    } catch (Exception ignored) {}
                });
            }
            CalibrationListStore.replaceAll(calObs);

            VillagerTradingPredictor.LOGGER.info("[VTP] Loaded state for seed {}", worldSeed);
        } catch (Exception e) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to load state", e);
        }
    }

    // ── Global settings (not world-specific) ─────────────────────────────────

    public static void saveGlobalSettings() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve(DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve("settings.json");

            JsonObject root = new JsonObject();
            root.addProperty("enabled", VillagerHudRenderer.isEnabled());
            root.addProperty("maxRerolls", PredictionEngine.getMaxRerolls());
            root.addProperty("theme", VtpTheme.current().name());
            root.addProperty("romanNumerals", VillagerHudRenderer.isRomanNumerals());

            JsonObject ar = new JsonObject();
            ar.addProperty("useTargetCount", AutoRerollManager.isUseTargetCount());
            ar.addProperty("manualCount", AutoRerollManager.getManualCount());
            ar.addProperty("safetyRerolls", AutoRerollManager.getSafetyRerolls());
            ar.addProperty("toolSlot", AutoRerollManager.getToolSlot());
            ar.addProperty("workstationSlot", AutoRerollManager.getWorkstationSlot());
            ar.addProperty("cycleDelay", AutoRerollManager.getCycleDelayTicks());
            root.add("autoReroll", ar);

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
            VillagerTradingPredictor.LOGGER.info("[VTP] Saved global settings");
        } catch (IOException e) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to save global settings", e);
        }
    }

    public static void loadGlobalSettings() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(DIR).resolve("settings.json");
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root.has("enabled")) {
                VillagerHudRenderer.setEnabled(root.get("enabled").getAsBoolean());
            }
            if (root.has("maxRerolls")) {
                PredictionEngine.setMaxRerolls(root.get("maxRerolls").getAsInt());
            }
            if (root.has("theme")) {
                VtpTheme.set(VtpTheme.fromName(root.get("theme").getAsString()));
            }
            if (root.has("romanNumerals")) {
                VillagerHudRenderer.setRomanNumerals(root.get("romanNumerals").getAsBoolean());
            }
            if (root.has("autoReroll")) {
                JsonObject ar = root.getAsJsonObject("autoReroll");
                if (ar.has("useTargetCount"))
                    AutoRerollManager.setUseTargetCount(ar.get("useTargetCount").getAsBoolean());
                if (ar.has("manualCount"))
                    AutoRerollManager.setManualCount(ar.get("manualCount").getAsInt());
                if (ar.has("safetyRerolls"))
                    AutoRerollManager.setSafetyRerolls(ar.get("safetyRerolls").getAsInt());
                if (ar.has("toolSlot"))
                    AutoRerollManager.setToolSlot(ar.get("toolSlot").getAsInt());
                if (ar.has("workstationSlot"))
                    AutoRerollManager.setWorkstationSlot(ar.get("workstationSlot").getAsInt());
                if (ar.has("cycleDelay"))
                    AutoRerollManager.setCycleDelayTicks(ar.get("cycleDelay").getAsInt());
            }
            VillagerTradingPredictor.LOGGER.info("[VTP] Loaded global settings");
        } catch (Exception e) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to load global settings", e);
        }
    }
}
