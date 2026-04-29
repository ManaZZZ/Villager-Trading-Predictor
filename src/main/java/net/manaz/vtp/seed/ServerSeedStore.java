package net.manaz.vtp.seed;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.manaz.vtp.VillagerTradingPredictor;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Persists a mapping of server address → world seed in config/vtp/servers.json.
 * Allows the seed (and associated state via VtpPersistence) to be auto-restored
 * when reconnecting to a known multiplayer server.
 */
public class ServerSeedStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("vtp").resolve("servers.json");

    public static void saveSeedForServer(String address, long seed) {
        Map<String, Long> map = loadMap();
        map.put(normalize(address), seed);
        writeMap(map);
        VillagerTradingPredictor.LOGGER.info("[VTP] Saved seed {} for server {}", seed, address);
    }

    public static OptionalLong getSeedForServer(String address) {
        Long seed = loadMap().get(normalize(address));
        return seed != null ? OptionalLong.of(seed) : OptionalLong.empty();
    }

    private static String normalize(String address) {
        return address.toLowerCase().trim();
    }

    private static Map<String, Long> loadMap() {
        if (!Files.exists(FILE)) return new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            Map<String, Long> map = new LinkedHashMap<>();
            if (obj != null) {
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    try { map.put(e.getKey(), e.getValue().getAsLong()); }
                    catch (Exception ignored) {}
                }
            }
            return map;
        } catch (Exception e) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to read servers.json", e);
            return new LinkedHashMap<>();
        }
    }

    private static void writeMap(Map<String, Long> map) {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject obj = new JsonObject();
            map.forEach(obj::addProperty);
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception e) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to write servers.json", e);
        }
    }
}
