package net.manaz.vtp;
import net.manaz.vtp.rng.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.RandomSequence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RngParityTest {
    @Test void matchesMinecraftAcrossSeedsIdentifiersAndMixedDraws() {
        long[] seeds = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 918273645L};
        String[] ids = {"minecraft:trade_set/librarian/level_1", "minecraft:trade_set/librarian/level_5", "vtp:test"};
        int[] bounds = {1, 2, 3, 15, 55, 127, 1024, 1073741825, Integer.MAX_VALUE};
        for (long seed : seeds) for (String id : ids) {
            var expected = new RandomSequence(seed, Identifier.parse(id)).random();
            var actual = RandomSequenceSimulator.createForSequence(seed, id);
            for (int i = 0; i < 10000; i++) {
                assertEquals(expected.nextInt(bounds[i % bounds.length]), actual.nextInt(bounds[i % bounds.length]));
                assertEquals(expected.nextLong(), actual.nextLong());
                assertEquals(expected.nextBoolean(), actual.nextBoolean());
                assertEquals(expected.nextFloat(), actual.nextFloat());
                assertEquals(expected.nextDouble(), actual.nextDouble());
            }
        }
    }
    @Test void copyingStateLeavesOriginalUntouched() {
        var original = RandomSequenceSimulator.createForSequence(42, "minecraft:trade_set/librarian/level_1");
        original.nextInt(17);
        long lo = original.getInternalRng().getSeedLo(), hi = original.getInternalRng().getSeedHi();
        var copy = new SimulatedRandomSource(lo, hi);
        for (int i = 0; i < 1000; i++) copy.nextLong();
        assertEquals(lo, original.getInternalRng().getSeedLo());
        assertEquals(hi, original.getInternalRng().getSeedHi());
    }
}
