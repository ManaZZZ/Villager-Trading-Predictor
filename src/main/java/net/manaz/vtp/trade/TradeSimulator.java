package net.manaz.vtp.trade;

import net.manaz.vtp.mixin.accessor.VillagerTradeAccessor;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Simulates the trade generation logic from AbstractVillager.addOffersFromTradeSet.
 * Replicates the exact RNG draw sequence so that predictions match the server.
 */
public class TradeSimulator {

    /**
     * Represents one simulated trade offer.
     */
    public record SimulatedOffer(
            Holder<VillagerTrade> tradeDefinition,
            Optional<EnchantmentResult> enchantment
    ) {}

    /**
     * Result of the EnchantRandomlyFunction simulation for a trade.
     */
    public record EnchantmentResult(
            Holder<Enchantment> enchantment,
            int level,
            int additionalCost
    ) {}

    /**
     * Simulate one round of trade generation for a TradeSet.
     * This replicates what happens when a villager gets/re-gets trades for a level.
     *
     * @param tradeSet      the TradeSet to simulate
     * @param random        the RNG state (will be advanced)
     * @param tradeableEnchantments the ordered list of tradeable enchantments (for enchant_randomly)
     * @return list of simulated offers generated in this round
     */
    public static List<SimulatedOffer> simulateTradeRound(
            TradeSet tradeSet,
            SimulatedRandomSource random,
            List<Holder<Enchantment>> tradeableEnchantments) {

        List<Holder<VillagerTrade>> availableTrades = TradeDataLoader.getTradesFromSet(tradeSet);
        int amount = getConstantAmount(tradeSet);

        List<SimulatedOffer> result = new ArrayList<>();

        if (tradeSet.allowDuplicates()) {
            // addOffersFromItemListings: pick with replacement
            for (int i = 0; i < amount; i++) {
                if (availableTrades.isEmpty()) break;
                int idx = random.nextInt(availableTrades.size());
                Holder<VillagerTrade> trade = availableTrades.get(idx);
                result.add(simulateSingleTrade(trade, random, tradeableEnchantments));
            }
        } else {
            // addOffersFromItemListingsWithoutDuplicates: pick without replacement
            List<Holder<VillagerTrade>> pool = new ArrayList<>(availableTrades);
            for (int i = 0; i < amount; i++) {
                if (pool.isEmpty()) break;
                int idx = random.nextInt(pool.size());
                Holder<VillagerTrade> trade = pool.remove(idx);
                result.add(simulateSingleTrade(trade, random, tradeableEnchantments));
            }
        }

        return result;
    }

    /**
     * Simulate VillagerTrade.getOffer for a single trade definition.
     * This applies the givenItemModifiers (enchant_randomly) and costs.
     *
     * For now, we only simulate the enchant_randomly function fully.
     * Other loot functions don't draw from RNG in a way that affects enchantment prediction.
     */
    private static SimulatedOffer simulateSingleTrade(
            Holder<VillagerTrade> tradeHolder,
            SimulatedRandomSource random,
            List<Holder<Enchantment>> tradeableEnchantments) {

        VillagerTrade trade = tradeHolder.value();

        // Check if this trade has the enchant_randomly modifier by checking if it gives enchanted_book
        // We detect enchanted book trades by the presence of enchantable output
        if (isEnchantedBookTrade(trade)) {
            return simulateEnchantedBookTrade(tradeHolder, random, tradeableEnchantments);
        }

        // For non-enchantment trades, we still need to advance the RNG for any NumberProvider
        // draws in cost/maxUses/xp/reputationDiscount evaluation.
        // In vanilla, most trades use constant NumberProviders, so no RNG draws.
        // We return the trade as-is with no enchantment.
        return new SimulatedOffer(tradeHolder, Optional.empty());
    }

    /**
     * Simulate the enchant_randomly loot function for an enchanted book trade.
     * Exact draw sequence:
     * 1. random.nextInt(enchantmentPool.size()) — pick enchantment
     * 2. random.nextInt(maxLevel - minLevel + 1) + minLevel — pick level (skipped if min==max)
     * 3. random.nextInt(5 + level * 10) — additional emerald cost
     */
    private static SimulatedOffer simulateEnchantedBookTrade(
            Holder<VillagerTrade> tradeHolder,
            SimulatedRandomSource random,
            List<Holder<Enchantment>> tradeableEnchantments) {

        if (tradeableEnchantments.isEmpty()) {
            return new SimulatedOffer(tradeHolder, Optional.empty());
        }

        // Step 1: Pick enchantment — Util.getRandomSafe -> random.nextInt(size)
        int enchIdx = random.nextInt(tradeableEnchantments.size());
        Holder<Enchantment> enchantment = tradeableEnchantments.get(enchIdx);

        // Step 2: Pick level — Mth.nextInt(random, minLevel, maxLevel)
        int minLevel = enchantment.value().getMinLevel();
        int maxLevel = enchantment.value().getMaxLevel();
        int level;
        if (minLevel >= maxLevel) {
            level = minLevel;
            // No RNG draw when min >= max (Mth.nextInt returns min immediately)
        } else {
            level = random.nextInt(maxLevel - minLevel + 1) + minLevel;
        }

        // Step 3: Additional cost — 2 + random.nextInt(5 + level * 10) + 3 * level
        int additionalCost = 2 + random.nextInt(5 + level * 10) + 3 * level;

        // Step 4: Double if enchantment is in doubleTradePriceEnchantments
        VillagerTradeAccessor accessor = (VillagerTradeAccessor) (Object) tradeHolder.value();
        Optional<net.minecraft.core.HolderSet<Enchantment>> doubleSet =
                accessor.vtp$getDoubleTradePriceEnchantments();
        if (doubleSet.isPresent() && doubleSet.get().contains(enchantment)) {
            additionalCost *= 2;
        }

        return new SimulatedOffer(tradeHolder, Optional.of(
                new EnchantmentResult(enchantment, level, additionalCost)));
    }

    /**
     * Detect if a VillagerTrade has the enchant_randomly loot function.
     * Uses mixin accessor to inspect the givenItemModifiers list.
     */
    private static boolean isEnchantedBookTrade(VillagerTrade trade) {
        VillagerTradeAccessor accessor = (VillagerTradeAccessor) (Object) trade;
        // Check if givenItemModifiers contains an EnchantRandomlyFunction
        for (LootItemFunction func : accessor.vtp$getGivenItemModifiers()) {
            if (func instanceof EnchantRandomlyFunction) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the trade amount from a TradeSet.
     * In vanilla, all trade sets use ConstantValue amounts (2 for most, 3 for librarian level 5,
     * 5 for wandering_trader/common). ConstantValue.getFloat() does not use the LootContext,
     * so passing null is safe for all vanilla trade sets.
     */
    private static int getConstantAmount(TradeSet tradeSet) {
        try {
            return tradeSet.calculateNumberOfTrades(null);
        } catch (Exception e) {
            return 2;
        }
    }
}
