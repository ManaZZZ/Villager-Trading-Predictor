package net.manaz.vtp.compat;

import com.google.gson.*;
import net.manaz.vtp.mixin.accessor.*;
import net.minecraft.core.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import java.util.*;

public final class TradeCompat {
    private TradeCompat() {}

    public static HolderSet<VillagerTrade> trades(TradeSet set) { return set.getTrades(); }
    public static boolean constantAmount(TradeSet set, RegistryOps<JsonElement> ops) {
        JsonObject json = TradeSet.CODEC.encodeStart(ops, set).getOrThrow().getAsJsonObject();
        return !json.has("amount") || json.get("amount").isJsonPrimitive();
    }
    public static int baseCostCount(TradeCost cost) {
        return cost.count() instanceof net.minecraft.world.level.storage.loot.providers.number.ConstantValue value ? (int) value.value() : 1;
    }
    public static List<LootItemFunction> modifiers(VillagerTrade trade) {
        return ((VillagerTradeAccessor) (Object) trade).vtp$getGivenItemModifiers();
    }
    public static JsonElement modifiersJson(JsonObject trade) { return TradeJson.normalize(trade.get("given_item_modifiers")); }
}
