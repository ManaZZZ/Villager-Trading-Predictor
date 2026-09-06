package net.manaz.vtp;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.manaz.vtp.gui.VtpAccent;
import net.manaz.vtp.prediction.*;
import net.manaz.vtp.render.*;
import net.manaz.vtp.seed.SeedProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Session identity is captured at JOIN, before Minecraft tears down its connection on disconnect. */
public final class VtpPersistence {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("vtp");
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "VTP state writer"); thread.setDaemon(true); return thread;
    });
    private static CompletableFuture<Void> pendingWrite = CompletableFuture.completedFuture(null);
    private static String identity;
    private static Long sessionSeed;
    private static boolean dirty;
    private static int ticks;
    private static long epoch;

    public static long epoch() { return epoch; }
    public static void beginSession() {
        resetState(); sessionSeed = null;
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        identity = server != null ? "world:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                : mc.getCurrentServer() != null ? "server:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).trim()
                : "connection:" + mc.getConnection().getConnection().getRemoteAddress();
    }
    private static void resetState() {
        epoch++; dirty = false;
        VillagerTracker.clear(); HudVisibility.reset(); PredictionEngine.clearAllOffsets(); SingleplayerSequences.clear();
        CalibrationListStore.clear(); CalibrationService.clear(); VillagerHudRenderer.clearTargets();
    }
    public static void markDirty() { dirty = true; }
    public static void tick() {
        if (++ticks % 20 == 0 && dirty && sessionSeed != null) save(sessionSeed);
    }
    public static void saveCurrentWorldState() { markDirty(); }
    public static void changeSeed(long seed) {
        if (sessionSeed != null) save(sessionSeed);
        flush();
        SeedProvider.setManualSeed(seed);
        load(seed);
    }
    public static void endSession() {
        if (sessionSeed != null) save(sessionSeed);
        saveGlobalSettings();
        resetState(); sessionSeed = null; identity = null;
    }
    public static void flush() { pendingWrite.join(); }
    private static void write(Path path, JsonObject root) {
        String json = GSON.toJson(root); // Snapshot on client thread; only immutable bytes cross to writer.
        pendingWrite = CompletableFuture.runAsync(() -> {
            try { StateFiles.writeAtomic(path, json); }
            catch (Exception e) { VillagerTradingPredictor.LOGGER.error("[VTP] Could not save {}", path, e); }
        }, WRITER);
    }
    public static void save(long seed) {
        if (identity == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 3);
        root.addProperty("minecraftVersion", "26.2");
        root.addProperty("seed", seed);
        JsonObject offsets = new JsonObject();
        PredictionEngine.getAllOffsets().forEach((key, value) -> offsets.addProperty(key.identifier().toString(), value));
        root.add("sequenceOffsets", offsets);
        root.add("tradeSetTotals", GSON.toJsonTree(VillagerTracker.getTradeSetTotals()));
        JsonObject villagers = new JsonObject();
        VillagerTracker.getVillagerSavedStates().forEach((uuid, state) -> villagers.add(uuid.toString(), GSON.toJsonTree(state)));
        root.add("villagers", villagers);
        if (VillagerTracker.getLastVillagerUuid() != null) root.addProperty("lastVillager", VillagerTracker.getLastVillagerUuid().toString());
        root.add("targets", targets());
        // Calibration observations/candidates are transient and bound to a session, never replayed after reconnect.
        write(DIR.resolve(StateFiles.filename(identity, seed)), root);
        dirty = false;
    }
    public static void load(long seed) {
        flush(); resetState(); sessionSeed = seed;
        if (identity == null) return;
        Path file = DIR.resolve(StateFiles.filename(identity, seed));
        boolean legacy = !Files.exists(file);
        if (legacy) file = DIR.resolve(seed + ".json");
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            restoreTargets(root);
            if (legacy || !root.has("schemaVersion") || root.get("schemaVersion").getAsInt() != 3
                    || !root.has("minecraftVersion") || !root.get("minecraftVersion").getAsString().equals("26.2")) {
                VillagerTradingPredictor.LOGGER.info("[VTP] Imported targets only. Old inferred trade state requires fresh observation/calibration.");
                return;
            }
            Map<ResourceKey<TradeSet>, Integer> offsets = new HashMap<>();
            root.getAsJsonObject("sequenceOffsets").entrySet().forEach(e ->
                    offsets.put(ResourceKey.create(Registries.TRADE_SET, Identifier.parse(e.getKey())), e.getValue().getAsInt()));
            Map<String, Integer> totals = new HashMap<>();
            root.getAsJsonObject("tradeSetTotals").entrySet().forEach(e -> {
                int value = e.getValue().getAsInt();
                if (Math.abs((long) value) <= PredictionEngine.MAX_ROUND) totals.put(e.getKey(), value);
            });
            Map<UUID, VillagerTracker.VillagerSavedState> states = new HashMap<>();
            root.getAsJsonObject("villagers").entrySet().forEach(e -> {
                try {
                    var state = GSON.fromJson(e.getValue(), VillagerTracker.VillagerSavedState.class);
                    if (state != null && state.profession() != null && state.observedOffers() != null
                            && state.levelOffers() != null && state.levelRounds() != null)
                        states.put(UUID.fromString(e.getKey()), state);
                } catch (Exception ignored) { }
            });
            PredictionEngine.loadOffsets(offsets);
            VillagerTracker.loadState(totals, states);
            if (root.has("lastVillager")) VillagerTracker.setLastVillagerUuid(UUID.fromString(root.get("lastVillager").getAsString()));
        } catch (Exception e) {
            resetState();
            VillagerTradingPredictor.LOGGER.error("[VTP] Could not load {}; starting with unverified state", file, e);
            try { Files.copy(file, file.resolveSibling(file.getFileName() + ".invalid-" + System.currentTimeMillis() + ".bak")); }
            catch (Exception backupError) { VillagerTradingPredictor.LOGGER.warn("[VTP] Could not back up invalid state", backupError); }
        }
    }
    private static JsonArray targets() {
        JsonArray result = new JsonArray();
        for (var target : VillagerHudRenderer.getTargets()) {
            JsonObject json = new JsonObject();
            json.addProperty("enchantment", target.enchantmentKey().identifier().toString());
            json.addProperty("level", target.minLevel()); json.addProperty("maxPrice", target.maxPrice()); result.add(json);
        }
        return result;
    }
    private static void restoreTargets(JsonObject root) {
        List<EnchantmentTarget> targets = new ArrayList<>();
        if (root.has("targets")) for (var element : root.getAsJsonArray("targets")) {
            try {
                var value = element.getAsJsonObject();
                targets.add(new EnchantmentTarget(ResourceKey.create(Registries.ENCHANTMENT,
                        Identifier.parse(value.get("enchantment").getAsString())), Math.clamp(value.get("level").getAsInt(), 1, 255),
                        value.has("maxPrice") ? value.get("maxPrice").getAsInt() : -1));
            } catch (Exception ignored) { }
        }
        VillagerHudRenderer.setTargets(targets);
    }
    public static void saveGlobalSettings() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", VillagerHudRenderer.isEnabled());
        root.addProperty("maxRerolls", PredictionEngine.getMaxRerolls());
        root.addProperty("accentColor", VtpAccent.hex(VtpAccent.color()));
        root.addProperty("hudVisibility", HudVisibility.mode().name());
        root.addProperty("hudTimeoutSeconds", HudVisibility.seconds());
        root.addProperty("hudRangeBlocks", HudVisibility.range());
        root.addProperty("romanNumerals", VillagerHudRenderer.isRomanNumerals());
        root.addProperty("showLevels", VillagerHudRenderer.isShowLevels());
        root.addProperty("hudSizePercent", HudSize.percent());
        write(DIR.resolve("settings.json"), root);
    }
    public static void loadGlobalSettings() {
        Path file = DIR.resolve("settings.json");
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.has("enabled")) VillagerHudRenderer.setEnabled(root.get("enabled").getAsBoolean());
            if (root.has("maxRerolls")) PredictionEngine.setMaxRerolls(root.get("maxRerolls").getAsInt());
            // Old theme choices are retired; all panels now use the unified dark frame.
            if (root.has("accentColor")) VtpAccent.parse(root.get("accentColor").getAsString()).ifPresent(VtpAccent::set);
            HudVisibility.configure(root.has("hudVisibility") ? HudVisibility.Mode.parse(root.get("hudVisibility").getAsString()) : HudVisibility.Mode.NEVER,
                    root.has("hudTimeoutSeconds") ? root.get("hudTimeoutSeconds").getAsInt() : 10,
                    root.has("hudRangeBlocks") ? root.get("hudRangeBlocks").getAsInt() : 16);
            if (root.has("showLevels")) VillagerHudRenderer.setShowLevels(root.get("showLevels").getAsBoolean());
            if (root.has("romanNumerals")) VillagerHudRenderer.setRomanNumerals(root.get("romanNumerals").getAsBoolean());
            HudSize.set(root.has("hudSizePercent") ? root.get("hudSizePercent").getAsInt() : HudSize.DEFAULT);
        } catch (Exception e) { VillagerTradingPredictor.LOGGER.warn("[VTP] Could not read settings", e); }
    }
}
