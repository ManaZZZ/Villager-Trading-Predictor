package net.manaz.vtp.mixin.accessor;

import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Xoroshiro128PlusPlus.class)
public interface XoroshiroStateAccessor {
    @Accessor("seedLo") long vtp$getLo();
    @Accessor("seedHi") long vtp$getHi();
}
