package net.manaz.vtp.trade;

import net.manaz.vtp.mixin.accessor.EnchantRandomlyAccessor;
import net.manaz.vtp.mixin.accessor.VillagerTradeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import java.util.OptionalInt;

/** Minimum undiscounted book cost from the same supported definitions used by prediction. */
public final class TradePrices {
    private TradePrices() {}
    public static OptionalInt cheapest(Holder<Enchantment> enchantment, int level) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || enchantment == null || level < enchantment.value().getMinLevel()
                || level > enchantment.value().getMaxLevel()) return OptionalInt.empty();
        var access = mc.getSingleplayerServer() == null ? mc.level.registryAccess() : mc.getSingleplayerServer().registryAccess();
        var librarian = access.lookupOrThrow(Registries.VILLAGER_PROFESSION).getValue(Identifier.withDefaultNamespace("librarian"));
        if (librarian == null) return OptionalInt.empty();
        int cheapest = Integer.MAX_VALUE;
        for (int tier = 1; tier <= 5; tier++) {
            var key = librarian.getTrades(tier);
            if (key == null) continue;
            var set = TradeDataLoader.getTradeSet(key);
            if (set.isEmpty() || !TradeSimulator.compatibilityProblem(set.get()).isEmpty()) continue;
            for (var holder : TradeDataLoader.getTradesFromSet(set.get())) {
                var trade = (VillagerTradeAccessor) holder.value();
                if (trade.vtp$getWants().item().value() != Items.EMERALD) continue;
                for (var modifier : trade.vtp$getGivenItemModifiers()) {
                    if (!(modifier instanceof EnchantRandomlyFunction)) continue;
                    var random = (EnchantRandomlyAccessor) modifier;
                    // Server/client holders can differ; compare their registry keys.
                    boolean available = random.vtp$getOptions().stream().flatMap(options -> options.stream())
                            .anyMatch(option -> option.unwrapKey().equals(enchantment.unwrapKey()));
                    if (!available) continue;
                    int cost = random.vtp$includeCost() ? 2 + 3 * level : 0;
                    boolean doubled = trade.vtp$getDoubleTradePriceEnchantments().stream().flatMap(options -> options.stream())
                            .anyMatch(option -> option.unwrapKey().equals(enchantment.unwrapKey()));
                    if (doubled) cost *= 2;
                    int price = trade.vtp$getWants().toItemCost(null, cost).count();
                    if (price >= 1) cheapest = Math.min(cheapest, price);
                }
            }
        }
        return cheapest == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(cheapest);
    }
}