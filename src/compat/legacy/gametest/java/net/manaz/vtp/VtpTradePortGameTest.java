package net.manaz.vtp;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.trade.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import java.util.Optional;

final class VtpTradePortGameTest {
    static void run(ClientGameTestContext context, Holder<VillagerProfession> profession) {
        context.runOnClient(mc -> {
            var access = mc.getSingleplayerServer().registryAccess();
            var ops = access.createSerializationContext(JsonOps.INSTANCE);
            var book = access.lookupOrThrow(Registries.VILLAGER_TRADE).getValueOrThrow(ResourceKey.create(Registries.VILLAGER_TRADE,
                    Identifier.withDefaultNamespace("librarian/1/emerald_and_book_enchanted_book")));
            var json = VillagerTrade.CODEC.encodeStart(ops, book).getOrThrow().getAsJsonObject();
            JsonArray conditions = new JsonArray();
            conditions.add(JsonParser.parseString("{\"condition\":\"minecraft:random_chance\",\"chance\":0.5}"));
            json.getAsJsonArray("given_item_modifiers").get(0).getAsJsonObject().add("conditions", conditions);
            var conditional = VillagerTrade.CODEC.parse(ops, json).getOrThrow();
            var set = new TradeSet(HolderSet.direct(Holder.direct(conditional)), ConstantValue.exactly(1), false,
                    Optional.of(Identifier.withDefaultNamespace("trade_set/librarian/level_1")));
            if (TradeSimulator.compatibilityProblem(set).isEmpty()) throw new AssertionError("Conditional legacy modifier accepted");
            var mending = access.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,
                    Identifier.withDefaultNamespace("mending")));
            if (TradePrices.cheapest(mending, 1).orElseThrow() != 10) throw new AssertionError("Legacy Mending minimum changed");
            VillagerTradingPredictor.LOGGER.info("[VTP TEST] PASS: legacy conditional modifiers and book prices.");
        });
    }
}
