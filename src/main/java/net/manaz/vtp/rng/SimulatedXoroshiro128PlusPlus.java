package net.manaz.vtp.rng;

/**
 * Pure reimplementation of Minecraft's Xoroshiro128PlusPlus PRNG.
 * Matches net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus exactly.
 */
public class SimulatedXoroshiro128PlusPlus {
    private long seedLo;
    private long seedHi;

    public SimulatedXoroshiro128PlusPlus(long seedLo, long seedHi) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = -7046029254386353131L; // SILVER_RATIO_64
            this.seedHi = 7640891576956012809L;  // GOLDEN_RATIO_64
        }
    }

    public long nextLong() {
        long lo = this.seedLo;
        long hi = this.seedHi;
        long result = Long.rotateLeft(lo + hi, 17) + lo;
        hi ^= lo;
        this.seedLo = Long.rotateLeft(lo, 49) ^ hi ^ (hi << 21);
        this.seedHi = Long.rotateLeft(hi, 28);
        return result;
    }

    public long getSeedLo() {
        return seedLo;
    }

    public long getSeedHi() {
        return seedHi;
    }
}
