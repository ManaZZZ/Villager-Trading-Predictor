package net.manaz.vtp.trade;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;

/** Immutable server observation. Demand, discounts and uses are deliberately not part of identity. */
public record ObservedOffer(String result, int count, String costA, int basePrice,
                            String costB, int countB, List<Book> books) {
    public record Book(String key, int level) {}

    public static ObservedOffer from(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        ItemEnchantments ench = result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<Book> books = ench.keySet().stream()
                .map(h -> new Book(h.unwrapKey().orElseThrow().identifier().toString(), ench.getLevel(h)))
                .sorted(java.util.Comparator.comparing(Book::key)).toList();
        return new ObservedOffer(id(result), result.getCount(), id(offer.getBaseCostA()),
                offer.getBaseCostA().getCount(), id(offer.getCostB()), offer.getCostB().getCount(), books);
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static List<ObservedOffer> copyOf(List<MerchantOffer> offers) {
        return offers.stream().map(ObservedOffer::from).toList();
    }
}
