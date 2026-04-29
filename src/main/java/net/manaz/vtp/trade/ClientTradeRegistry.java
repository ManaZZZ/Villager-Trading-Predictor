package net.manaz.vtp.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.manaz.vtp.VillagerTradingPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads vanilla TRADE_SET and VILLAGER_TRADE data directly from the Minecraft jar
 * (accessible as classpath resources) for use in multiplayer.
 *
 * The "trades" field in a TradeSet JSON is a HolderSet<VillagerTrade> which may be:
 *   - a tag reference:  "#minecraft:librarian/level_1"
 *   - a list of keys:  ["minecraft:foo", ...]
 *   - inline objects:  [{...}, ...]
 *
 * For tag references, we load the tag JSON, resolve all member VillagerTrade entries,
 * register them in a local MappedRegistry, and bind the tag before decoding TradeSet.
 */
public class ClientTradeRegistry {

    private static final Map<ResourceKey<TradeSet>, TradeSet> tradeSetCache = new HashMap<>();
    private static RegistryAccess clientAccess = null;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            VillagerTradingPredictor.LOGGER.warn("[VTP] ClientTradeRegistry.init() called before level loaded");
            return;
        }
        clientAccess = client.level.registryAccess();
        initialized = true;
        VillagerTradingPredictor.LOGGER.info("[VTP] ClientTradeRegistry initialized for multiplayer");
    }

    public static Optional<TradeSet> getTradeSet(ResourceKey<TradeSet> key) {
        if (!initialized || clientAccess == null) return Optional.empty();

        if (tradeSetCache.containsKey(key)) {
            return Optional.ofNullable(tradeSetCache.get(key));
        }

        Optional<TradeSet> result = loadTradeSet(key);

        if (result.isPresent()) {
            VillagerTradingPredictor.LOGGER.info("[VTP] Loaded TradeSet from classpath: {}", key.identifier());
        } else {
            VillagerTradingPredictor.LOGGER.warn("[VTP] Could not load TradeSet from classpath: {}", key.identifier());
        }

        tradeSetCache.put(key, result.orElse(null));
        return result;
    }

    public static void clear() {
        tradeSetCache.clear();
        clientAccess = null;
        initialized = false;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static Optional<TradeSet> loadTradeSet(ResourceKey<TradeSet> key) {
        String tsPath = dataPath(key, "trade_set");
        JsonElement tsJson = loadRawJson(tsPath);
        if (tsJson == null) return Optional.empty();

        // Build a local VillagerTrade registry, resolving tags and entries from classpath
        RegistryOps<JsonElement> vtOps = clientAccess.createSerializationContext(JsonOps.INSTANCE);
        MappedRegistry<VillagerTrade> vtReg = new MappedRegistry<>(Registries.VILLAGER_TRADE, Lifecycle.stable());
        Map<TagKey<VillagerTrade>, List<Holder<VillagerTrade>>> tagBindings = new HashMap<>();

        if (tsJson.isJsonObject()) {
            JsonElement tradesEl = tsJson.getAsJsonObject().get("trades");
            if (tradesEl != null) {
                collectEntries(tradesEl, vtReg, tagBindings, vtOps);
            }
        }

        vtReg.bindTags(tagBindings);
        vtReg.freeze(); // transitions tagSet from "unbound" to "bound" state so tag lookups work
        VillagerTradingPredictor.LOGGER.info("[VTP] VillagerTrade registry: {} entries, {} tags",
                vtReg.size(), tagBindings.size());

        RegistryOps<JsonElement> tsOps = RegistryOps.create(JsonOps.INSTANCE, buildInfoLookup(vtReg));
        return decodeFromPath(tsPath, TradeSet.CODEC, tsOps);
    }

    /**
     * Recursively walk a HolderSet JSON value, registering all referenced VillagerTrade
     * entries and binding any tags encountered.
     */
    private static void collectEntries(JsonElement el,
                                        MappedRegistry<VillagerTrade> vtReg,
                                        Map<TagKey<VillagerTrade>, List<Holder<VillagerTrade>>> tagBindings,
                                        RegistryOps<JsonElement> vtOps) {
        if (el.isJsonPrimitive()) {
            String value = el.getAsString();
            if (value.startsWith("#")) {
                resolveTag(value.substring(1), vtReg, tagBindings, vtOps);
            } else {
                loadAndRegister(value, vtReg, vtOps);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement item : el.getAsJsonArray()) {
                collectEntries(item, vtReg, tagBindings, vtOps);
            }
        }
        // JSON objects = inline VillagerTrade — codec handles them without pre-loading
    }

    /**
     * Load the tag file at /data/<ns>/tags/villager_trade/<path>.json,
     * register all member VillagerTrades, and bind the tag on vtReg.
     */
    private static void resolveTag(String tagId,
                                    MappedRegistry<VillagerTrade> vtReg,
                                    Map<TagKey<VillagerTrade>, List<Holder<VillagerTrade>>> tagBindings,
                                    RegistryOps<JsonElement> vtOps) {
        Identifier tagLoc = tagId.contains(":")
                ? Identifier.parse(tagId)
                : Identifier.fromNamespaceAndPath("minecraft", tagId);
        TagKey<VillagerTrade> tagKey = TagKey.create(Registries.VILLAGER_TRADE, tagLoc);

        if (tagBindings.containsKey(tagKey)) return; // already resolved

        String tagPath = "/data/" + tagLoc.getNamespace()
                + "/tags/villager_trade/" + tagLoc.getPath() + ".json";
        JsonElement tagJson = loadRawJson(tagPath);

        List<Holder<VillagerTrade>> members = new ArrayList<>();
        tagBindings.put(tagKey, members); // register early to prevent infinite recursion

        if (tagJson != null && tagJson.isJsonObject()) {
            JsonElement valuesEl = tagJson.getAsJsonObject().get("values");
            if (valuesEl != null && valuesEl.isJsonArray()) {
                for (JsonElement e : valuesEl.getAsJsonArray()) {
                    if (!e.isJsonPrimitive()) continue;
                    String id = e.getAsString();
                    if (id.startsWith("#")) {
                        // Nested tag — recurse then include its members
                        String nestedId = id.substring(1);
                        resolveTag(nestedId, vtReg, tagBindings, vtOps);
                        Identifier nestedLoc = nestedId.contains(":")
                                ? Identifier.parse(nestedId)
                                : Identifier.fromNamespaceAndPath("minecraft", nestedId);
                        TagKey<VillagerTrade> nestedKey = TagKey.create(Registries.VILLAGER_TRADE, nestedLoc);
                        List<Holder<VillagerTrade>> nestedMembers = tagBindings.get(nestedKey);
                        if (nestedMembers != null) members.addAll(nestedMembers);
                    } else {
                        Optional<Holder<VillagerTrade>> holder = loadAndRegister(id, vtReg, vtOps);
                        holder.ifPresent(members::add);
                    }
                }
            }
        } else {
            VillagerTradingPredictor.LOGGER.warn("[VTP] Tag not found on classpath: {}", tagPath);
        }
    }

    private static Optional<Holder<VillagerTrade>> loadAndRegister(String id,
                                                                     MappedRegistry<VillagerTrade> vtReg,
                                                                     RegistryOps<JsonElement> vtOps) {
        Identifier loc = id.contains(":")
                ? Identifier.parse(id)
                : Identifier.fromNamespaceAndPath("minecraft", id);
        ResourceKey<VillagerTrade> vtKey = ResourceKey.create(Registries.VILLAGER_TRADE, loc);

        // Return existing holder if already registered
        Optional<Holder.Reference<VillagerTrade>> existing = vtReg.get(vtKey);
        if (existing.isPresent()) return existing.map(h -> h);

        String vtPath = dataPath(vtKey, "villager_trade");
        Optional<VillagerTrade> vt = decodeFromPath(vtPath, VillagerTrade.CODEC, vtOps);
        if (vt.isEmpty()) {
            VillagerTradingPredictor.LOGGER.warn("[VTP] VillagerTrade not found on classpath: {}", vtPath);
            return Optional.empty();
        }

        Registry.register(vtReg, loc, vt.get());
        // Get the holder after registration
        return vtReg.get(vtKey).map(h -> h);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build a RegistryInfoLookup that serves our local VillagerTrade registry for
     * VILLAGER_TRADE and delegates to the client registry for everything else.
     */
    @SuppressWarnings("unchecked")
    private static RegistryOps.RegistryInfoLookup buildInfoLookup(MappedRegistry<VillagerTrade> vtReg) {
        return new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
                if (registryKey.equals(Registries.VILLAGER_TRADE)) {
                    RegistryOps.RegistryInfo<T> info = (RegistryOps.RegistryInfo<T>)
                            new RegistryOps.RegistryInfo<>(vtReg, vtReg, vtReg.registryLifecycle());
                    return Optional.of(info);
                }
                @SuppressWarnings("unchecked")
                ResourceKey<? extends Registry<T>> narrowed =
                        (ResourceKey<? extends Registry<T>>) registryKey;
                return clientAccess.lookup(narrowed).map(reg ->
                        new RegistryOps.RegistryInfo<>(reg, reg, reg.registryLifecycle()));
            }
        };
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** e.g. key=minecraft:librarian/level_1, type=trade_set → /data/minecraft/trade_set/librarian/level_1.json */
    private static <T> String dataPath(ResourceKey<T> key, String type) {
        return "/data/" + key.identifier().getNamespace()
                + "/" + type + "/" + key.identifier().getPath() + ".json";
    }

    private static JsonElement loadRawJson(String path) {
        try (InputStream is = ClientTradeRegistry.class.getResourceAsStream(path)) {
            if (is == null) return null;
            return JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (Exception e) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to read {}", path, e);
            return null;
        }
    }

    private static <T> Optional<T> decodeFromPath(String path,
                                                    com.mojang.serialization.Codec<T> codec,
                                                    RegistryOps<JsonElement> ops) {
        try (InputStream is = ClientTradeRegistry.class.getResourceAsStream(path)) {
            if (is == null) return Optional.empty();
            JsonElement json = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            return codec.decode(ops, json)
                    .resultOrPartial(err ->
                            VillagerTradingPredictor.LOGGER.warn("[VTP] Decode error for {}: {}", path, err))
                    .map(Pair::getFirst);
        } catch (Exception e) {
            VillagerTradingPredictor.LOGGER.error("[VTP] Failed to decode {}", path, e);
            return Optional.empty();
        }
    }
}
