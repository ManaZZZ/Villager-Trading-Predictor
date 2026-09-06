package net.manaz.vtp.mixin.accessor;

import net.minecraft.core.HolderSet;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Optional;

@Mixin(VillagerTrade.class)
public interface VillagerTradeAccessor {
    @Accessor("gives")
    ItemStackTemplate vtp$getGives();

    @Accessor("givenItemModifiers")
    List<LootItemFunction> vtp$getGivenItemModifiers();

    @Accessor("doubleTradePriceEnchantments")
    Optional<HolderSet<Enchantment>> vtp$getDoubleTradePriceEnchantments();

    @Accessor("wants")
    TradeCost vtp$getWants();

    @Accessor("additionalWants")
    Optional<TradeCost> vtp$getAdditionalWants();
}
