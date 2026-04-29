package net.manaz.vtp.trade;

import net.manaz.vtp.VillagerTradingPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads trade-related data from registries.
 *
 * TRADE_SET and VILLAGER_TRADE are server-only registries (not synced to client).
 * In singleplayer, we access them via the integrated server.
 * Enchantment registry IS synced to the client.
 */
public class TradeDataLoader {
    private static boolean registryLogged = false;

    public static void resetRegistryLog() {
        registryLogged = false;
    }

    /**
     * Look up a TradeSet by its ResourceKey.
     * Prefers the integrated server registry (singleplayer); falls back to the client registry
     * for multiplayer — TRADE_SET is a datapack registry and is synced to clients on login.
     */
    public static Optional<TradeSet> getTradeSet(ResourceKey<TradeSet> key) {
        // 1. Integrated server registry (singleplayer)
        RegistryAccess registryAccess = getServerRegistryAccess();
        if (registryAccess != null) {
            Optional<TradeSet> result = registryAccess.lookup(Registries.TRADE_SET)
                    .flatMap(reg -> reg.getOptional(key));
            if (result.isPresent()) return result;
        }

        // 2. Client registry (multiplayer — only if TRADE_SET was synced)
        RegistryAccess clientAccess = getClientRegistryAccess();
        if (clientAccess != null) {
            Optional<TradeSet> result = clientAccess.lookup(Registries.TRADE_SET)
                    .flatMap(reg -> reg.getOptional(key));
            if (result.isPresent()) return result;
        }

        // 3. Classpath fallback — load vanilla data directly from the Minecraft jar
        return ClientTradeRegistry.getTradeSet(key);
    }

    /**
     * Get the TradeSet ResourceKey for a given profession and level.
     */
    public static ResourceKey<TradeSet> getTradeSetKey(Holder<VillagerProfession> professionHolder, int level) {
        VillagerProfession profession = professionHolder.value();
        return profession.getTrades(level);
    }

    /**
     * Get the list of VillagerTrade entries from a TradeSet's HolderSet, in order.
     * This ordering must match the server's ordering for deterministic prediction.
     */
    public static List<Holder<VillagerTrade>> getTradesFromSet(TradeSet tradeSet) {
        HolderSet<VillagerTrade> trades = tradeSet.getTrades();
        List<Holder<VillagerTrade>> result = new ArrayList<>();
        for (int i = 0; i < trades.size(); i++) {
            result.add(trades.get(i));
        }
        return result;
    }

    /**
     * Get all enchantments in the #minecraft:tradeable tag, in order.
     * This is the pool from which EnchantRandomlyFunction picks enchantments.
     * Uses the server registry to ensure we get the correct resolved tag.
     */
    public static List<Holder<Enchantment>> getTradeableEnchantments() {
        RegistryAccess registryAccess = getServerRegistryAccess();
        if (registryAccess == null) {
            // Fallback to client registry for enchantments (they ARE synced)
            registryAccess = getClientRegistryAccess();
        }
        if (registryAccess == null) return List.of();

        Optional<Registry<Enchantment>> regOpt = registryAccess.lookup(Registries.ENCHANTMENT);
        if (regOpt.isEmpty()) return List.of();

        Registry<Enchantment> registry = regOpt.get();
        Optional<HolderSet.Named<Enchantment>> tagOpt = registry.get(EnchantmentTags.TRADEABLE);
        if (tagOpt.isEmpty()) {
            VillagerTradingPredictor.LOGGER.warn("Tradeable enchantment tag not found!");
            return List.of();
        }

        HolderSet.Named<Enchantment> tag = tagOpt.get();
        List<Holder<Enchantment>> result = new ArrayList<>();
        for (int i = 0; i < tag.size(); i++) {
            result.add(tag.get(i));
        }
        return result;
    }

    /**
     * Get the integrated server's registry access (singleplayer only).
     * TRADE_SET and VILLAGER_TRADE registries are only available server-side.
     */
    private static RegistryAccess getServerRegistryAccess() {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null) return null;
        return server.registryAccess();
    }

    private static RegistryAccess getClientRegistryAccess() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        RegistryAccess ra = client.level.registryAccess();
        if (!registryLogged) {
            registryLogged = true;
            ra.lookup(Registries.TRADE_SET).ifPresentOrElse(
                    reg -> VillagerTradingPredictor.LOGGER.info("[VTP] Client TRADE_SET registry: {} entries", reg.size()),
                    ()  -> VillagerTradingPredictor.LOGGER.warn("[VTP] Client TRADE_SET registry: NOT PRESENT")
            );
        }
        return ra;
    }
}
