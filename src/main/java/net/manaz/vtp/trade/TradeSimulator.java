package net.manaz.vtp.trade;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.manaz.vtp.mixin.accessor.*;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import java.util.*;

/** Supported deterministic trade definitions are validated before simulation. */
public final class TradeSimulator {
    public record SimulatedOffer(Holder<VillagerTrade> tradeDefinition, Optional<EnchantmentResult> enchantment) {}
    public record EnchantmentResult(Holder<Enchantment> enchantment, int level, int additionalCost, int emeraldPrice) {}

    private static RegistryAccess access() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().registryAccess()
                : mc.level.registryAccess();
    }

    public static String compatibilityProblem(TradeSet set) {
        if (set.randomSequence().isEmpty()) return "trade set has no named random sequence";
        try {
            var ops = access().createSerializationContext(JsonOps.INSTANCE);
            JsonObject json = TradeSet.CODEC.encodeStart(ops, set).getOrThrow().getAsJsonObject();
            if (json.has("amount") && !json.get("amount").isJsonPrimitive()) return "random trade amount";
            for (var holder : TradeDataLoader.getTradesFromSet(set)) {
                JsonObject trade = VillagerTrade.CODEC.encodeStart(ops, holder.value()).getOrThrow().getAsJsonObject();
                if (trade.has("merchant_predicate")) return "conditional trades (including Trade Rebalance)";
                for (String field : List.of("max_uses", "xp", "reputation_discount")) {
                    if (trade.has(field) && !trade.get(field).isJsonPrimitive()) return "random trade values";
                }
                for (String field : List.of("wants", "additional_wants")) {
                    if (trade.has(field)) {
                        JsonObject cost = trade.getAsJsonObject(field);
                        if (cost.has("count") && !cost.get("count").isJsonPrimitive()) return "random trade costs";
                        if (cost.has("components") && cost.getAsJsonObject("components").size() > 0)
                            return "custom trade cost components";
                        if (cost.has("count") && cost.get("count").getAsDouble() < 0) return "invalid trade cost";
                    }
                }
                JsonObject gives = trade.getAsJsonObject("gives");
                if (gives.has("components") && gives.getAsJsonObject("components").size() > 0)
                    return "custom output components";
                JsonArray functions = trade.has("given_item_modifiers") ? trade.getAsJsonArray("given_item_modifiers") : new JsonArray();
                int randomFunctions = 0;
                for (JsonElement element : functions) {
                    JsonObject function = element.getAsJsonObject();
                    String id = function.get("function").getAsString();
                    if (id.equals("minecraft:enchant_randomly")) {
                        randomFunctions++;
                        if (randomFunctions > 1 || !function.has("options")
                                || (function.has("conditions") && !function.getAsJsonArray("conditions").isEmpty()))
                            return "unsupported enchantment options or conditions";
                        if (!gives.get("id").getAsString().equals("minecraft:enchanted_book")
                                && !gives.get("id").getAsString().equals("minecraft:book")) return "non-book enchantments";
                        if (function.has("only_compatible") && function.get("only_compatible").getAsBoolean())
                            return "compatibility-filtered enchantments";
                    } else if (id.equals("minecraft:filtered") && randomFunctions == 1) {
                        // Only the vanilla postcondition is safe to omit: the supported function always adds a book enchantment.
                        JsonObject expected = JsonParser.parseString(
                                "{\"function\":\"minecraft:filtered\",\"item_filter\":{\"items\":\"minecraft:enchanted_book\",\"predicates\":{\"minecraft:stored_enchantments\":[{}]}},\"on_fail\":{\"function\":\"minecraft:discard\"}}").getAsJsonObject();
                        if (!function.equals(expected)) return "custom item filter";
                    } else return "unsupported item modifier: " + id;
                }
                if (randomFunctions > 0) {
                    var acc = (VillagerTradeAccessor) (Object) holder.value();
                    var random = acc.vtp$getGivenItemModifiers().stream().filter(f -> f instanceof EnchantRandomlyFunction).findFirst().orElseThrow();
                    var options = ((EnchantRandomlyAccessor) random).vtp$getOptions();
                    if (options.isEmpty() || options.get().size() == 0) return "empty enchantment pool";
                }
            }
            return "";
        } catch (Exception e) {
            return "trade definitions unavailable";
        }
    }

    public static List<SimulatedOffer> simulateTradeRound(TradeSet set, SimulatedRandomSource rng,
                                                          List<Holder<Enchantment>> unused) {
        List<Holder<VillagerTrade>> pool = new ArrayList<>(TradeDataLoader.getTradesFromSet(set));
        int amount = set.calculateNumberOfTrades(null);
        List<SimulatedOffer> result = new ArrayList<>();
        while (result.size() < amount && !pool.isEmpty()) {
            int index = rng.nextInt(pool.size());
            Holder<VillagerTrade> trade = set.allowDuplicates() ? pool.get(index) : pool.remove(index);
            var acc = (VillagerTradeAccessor) (Object) trade.value();
            var function = acc.vtp$getGivenItemModifiers().stream()
                    .filter(f -> f instanceof EnchantRandomlyFunction).findFirst();
            Optional<EnchantmentResult> enchantment = Optional.empty();
            if (function.isPresent()) {
                var random = (EnchantRandomlyAccessor) function.get();
                var options = random.vtp$getOptions().orElseThrow();
                Holder<Enchantment> ench = options.get(rng.nextInt(options.size()));
                int min = ench.value().getMinLevel(), max = ench.value().getMaxLevel();
                int level = min >= max ? min : min + rng.nextInt(max - min + 1);
                int cost = random.vtp$includeCost() ? 2 + rng.nextInt(5 + level * 10) + 3 * level : 0;
                if (acc.vtp$getDoubleTradePriceEnchantments().filter(s -> s.contains(ench)).isPresent()) cost *= 2;
                enchantment = Optional.of(new EnchantmentResult(ench, level, cost, acc.vtp$getWants().toItemCost(null, cost).count()));
            }
            int extra = enchantment.map(EnchantmentResult::additionalCost).orElse(0);
            if (acc.vtp$getWants().toItemCost(null, extra).count() < 1
                    || acc.vtp$getAdditionalWants().filter(c -> c.toItemCost(null, 0).count() < 1).isPresent()) {
                if (set.allowDuplicates()) pool.remove(index);
                continue;
            }
            result.add(new SimulatedOffer(trade, enchantment));
        }
        return List.copyOf(result);
    }

    public static List<ObservedOffer> fingerprint(List<SimulatedOffer> offers) {
        return offers.stream().map(o -> {
            var acc = (VillagerTradeAccessor) (Object) o.tradeDefinition().value();
            var wants = acc.vtp$getWants().toItemCost(null, o.enchantment().map(EnchantmentResult::additionalCost).orElse(0));
            var second = acc.vtp$getAdditionalWants().map(c -> c.toItemCost(null, 0).itemStack());
            List<ObservedOffer.Book> books = o.enchantment().map(e ->
                    List.of(new ObservedOffer.Book(e.enchantment().unwrapKey().orElseThrow().identifier().toString(), e.level()))).orElse(List.of());
            String result = o.enchantment().isPresent() ? "minecraft:enchanted_book"
                    : BuiltInRegistries.ITEM.getKey(acc.vtp$getGives().item().value()).toString();
            return new ObservedOffer(result, acc.vtp$getGives().count(),
                    BuiltInRegistries.ITEM.getKey(wants.itemStack().getItem()).toString(), wants.count(),
                    second.map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).orElse("minecraft:air"),
                    second.map(s -> s.getCount()).orElse(0), books);
        }).toList();
    }

    public static List<SimulatedOffer> fromObserved(List<ObservedOffer> observations) {
        List<SimulatedOffer> result = new ArrayList<>();
        var registry = access().lookupOrThrow(Registries.ENCHANTMENT);
        for (var offer : observations) {
            if (offer.books().isEmpty()) result.add(new SimulatedOffer(null, Optional.empty()));
            for (var book : offer.books()) registry.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(book.key())))
                    .ifPresent(ench -> result.add(new SimulatedOffer(null, Optional.of(
                            new EnchantmentResult(ench, book.level(), offer.basePrice(), offer.basePrice())))));
        }
        return List.copyOf(result);
    }
}
