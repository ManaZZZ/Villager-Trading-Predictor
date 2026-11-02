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

    public static HolderSet<VillagerTrade> trades(TradeSet set) { return set.trades(); }
    public static boolean constantAmount(TradeSet set, RegistryOps<JsonElement> ops) {
        return set.amount().value() instanceof net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
    }
    public static int baseCostCount(TradeCost cost) {
        return cost.count().value() instanceof net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue value ? value.value() : 1;
    }
    public static List<LootItemFunction> modifiers(VillagerTrade trade) {
        List<LootItemFunction> result = new ArrayList<>();
        ((VillagerTradeAccessor) (Object) trade).vtp$getGivenItemModifier().ifPresent(holder -> collect(holder.value(), result));
        return List.copyOf(result);
    }
    private static void collect(LootItemFunction function, List<LootItemFunction> result) {
        if (function instanceof net.minecraft.world.level.storage.loot.functions.SequenceFunction)
            ((SequenceFunctionAccessor) function).vtp$getFunctions().forEach(holder -> collect(holder.value(), result));
        else result.add(function);
    }
    public static JsonElement modifiersJson(JsonObject trade) { return trade.get("given_item_modifier"); }
}
