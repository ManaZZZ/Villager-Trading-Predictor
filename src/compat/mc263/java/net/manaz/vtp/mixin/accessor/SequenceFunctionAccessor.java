package net.manaz.vtp.mixin.accessor;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SequenceFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SequenceFunction.class)
public interface SequenceFunctionAccessor {
    @Accessor("functions") HolderSet<LootItemFunction> vtp$getFunctions();
}
