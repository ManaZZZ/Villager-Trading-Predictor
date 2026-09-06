package net.manaz.vtp.mixin.accessor;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(RandomSequences.class)
public interface RandomSequencesAccessor {
    @Accessor("salt") int vtp$getSalt();
    @Accessor("includeWorldSeed") boolean vtp$includeWorldSeed();
    @Accessor("includeSequenceId") boolean vtp$includeSequenceId();
}
