package net.manaz.vtp.mixin.accessor;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {
    /** Read without calling getOffers(), which would generate trades and consume real RNG. */
    @Accessor("offers") MerchantOffers vtp$getExistingOffers();
}
