package net.manaz.vtp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.rng.RandomSequenceSimulator;
import net.manaz.vtp.prediction.OfferMatching;
import net.manaz.vtp.trade.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;

import java.util.List;
import java.util.Optional;

/** Exercise 26.3 trade decoding through both server and multiplayer fallback registries. */
final class VtpTradePortGameTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static TradeSet tradeSet(VillagerTrade trade) {
        return new TradeSet(HolderSet.direct(Holder.direct(trade)),
                Holder.direct(new net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue(1)),
                false, Optional.of(Identifier.withDefaultNamespace("trade_set/librarian/level_1")));
    }

    static void run(ClientGameTestContext context, Holder<VillagerProfession> profession) {
        context.runOnClient(mc -> {
            var access = mc.getSingleplayerServer().registryAccess();
            ClientTradeRegistry.init();
            try {
                for (int tier = 1; tier <= 5; tier++) {
                    var key = profession.value().getTrades(tier);
                    var nativeSet = access.lookupOrThrow(Registries.TRADE_SET).getValueOrThrow(key);
                    var fallbackSet = ClientTradeRegistry.getTradeSet(key).orElseThrow();
                    String problem = TradeSimulator.compatibilityProblem(fallbackSet);
                    check(problem.isEmpty(), "Fallback trade set rejected: " + key + ": " + problem);
                    check(nativeSet.trades().stream().map(Holder::unwrapKey).toList().equals(
                            fallbackSet.trades().stream().map(Holder::unwrapKey).toList()), "Fallback trade order differs: " + key);
                    var nativeRng = RandomSequenceSimulator.createForSequence(1234, nativeSet.randomSequence().orElseThrow().toString());
                    var fallbackRng = RandomSequenceSimulator.createForSequence(1234, fallbackSet.randomSequence().orElseThrow().toString());
                    for (int round = 0; round < 100; round++) {
                        var expected = TradeSimulator.fingerprint(TradeSimulator.simulateTradeRound(nativeSet, nativeRng, List.of()));
                        var actual = TradeSimulator.fingerprint(TradeSimulator.simulateTradeRound(fallbackSet, fallbackRng, List.of()));
                        check(OfferMatching.same(expected, actual), "Fallback prediction differs: " + key + " round=" + round);
                    }
                }
            } finally { ClientTradeRegistry.clear(); }

            var ops = access.createSerializationContext(JsonOps.INSTANCE);
            var book = access.lookupOrThrow(Registries.VILLAGER_TRADE).getValueOrThrow(
                    net.minecraft.resources.ResourceKey.create(Registries.VILLAGER_TRADE,
                            Identifier.withDefaultNamespace("librarian/1/emerald_and_book_enchanted_book")));
            JsonObject trade = VillagerTrade.CODEC.encodeStart(ops, book).getOrThrow().getAsJsonObject();
            JsonObject enchant = trade.getAsJsonArray("given_item_modifier").get(0).getAsJsonObject().deepCopy();
            trade.add("given_item_modifier", enchant);
            var single = tradeSet(VillagerTrade.CODEC.parse(ops, trade).getOrThrow());
            check(TradeSimulator.compatibilityProblem(single).isEmpty(), "Single enchantment modifier rejected");
            var singleRng = RandomSequenceSimulator.createForSequence(1234, single.randomSequence().orElseThrow().toString());
            var nestedRng = RandomSequenceSimulator.createForSequence(1234, single.randomSequence().orElseThrow().toString());
            JsonObject sequence = new JsonObject();
            sequence.addProperty("type", "minecraft:sequence");
            JsonArray functions = new JsonArray();
            functions.add(enchant);
            sequence.add("functions", functions);
            JsonArray outer = new JsonArray();
            outer.add(sequence);
            trade.add("given_item_modifier", outer);
            var nested = tradeSet(VillagerTrade.CODEC.parse(ops, trade).getOrThrow());
            check(TradeSimulator.compatibilityProblem(nested).isEmpty(), "Nested modifier sequence rejected");
            check(OfferMatching.same(
                    TradeSimulator.fingerprint(TradeSimulator.simulateTradeRound(single, singleRng, List.of())),
                    TradeSimulator.fingerprint(TradeSimulator.simulateTradeRound(nested, nestedRng, List.of()))),
                    "Nested sequence changed enchantment prediction");

            enchant.add("condition", JsonParser.parseString("{\"type\":\"minecraft:random_chance\",\"chance\":0.5}"));
            check(!TradeSimulator.compatibilityProblem(tradeSet(VillagerTrade.CODEC.parse(ops, trade).getOrThrow())).isEmpty(),
                    "Conditional enchantment accepted as deterministic");
            enchant.remove("condition");
            sequence.add("condition", JsonParser.parseString("{\"type\":\"minecraft:random_chance\",\"chance\":0.5}"));
            check(!TradeSimulator.compatibilityProblem(tradeSet(VillagerTrade.CODEC.parse(ops, trade).getOrThrow())).isEmpty(),
                    "Conditional sequence accepted as deterministic");

            var mending = access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
                    net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("mending")));
            check(TradePrices.cheapest(mending, 1).orElseThrow() == 10, "Mending minimum price changed");
            VillagerTradingPredictor.LOGGER.info("[VTP TEST] PASS: 26.3 modifiers, conditions, book prices, 500 multiplayer fallback rounds.");
        });
    }
}
