package net.manaz.vtp.rng;

/**
 * Reimplementation of XoroshiroRandomSource methods that consume values from the PRNG.
 * Matches Minecraft's XoroshiroRandomSource exactly for nextInt(), nextInt(bound), nextFloat(), nextDouble().
 */
public class SimulatedRandomSource {
    private static final float FLOAT_UNIT = 5.9604645E-8F;  // 1.0f / (1 << 24)
    private static final double DOUBLE_UNIT = 1.1102230246251565E-16; // 1.0 / (1L << 53)

    private final SimulatedXoroshiro128PlusPlus rng;

    public SimulatedRandomSource(SimulatedXoroshiro128PlusPlus rng) {
        this.rng = rng;
    }

    public SimulatedRandomSource(long seedLo, long seedHi) {
        this.rng = new SimulatedXoroshiro128PlusPlus(seedLo, seedHi);
    }

    /** Consumes one nextLong() call, returns lower 32 bits as int. */
    public int nextInt() {
        return (int) rng.nextLong();
    }

    /**
     * Returns a uniformly distributed int in [0, bound).
     * Uses the fast "nearly divisionless" algorithm from Minecraft.
     * Each call consumes at least one nextLong().
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        long unsignedInt = Integer.toUnsignedLong(nextInt());
        long product = unsignedInt * (long) bound;
        long low = product & 0xFFFFFFFFL;
        if (low < (long) bound) {
            int threshold = Integer.remainderUnsigned(~bound + 1, bound);
            while (low < (long) threshold) {
                unsignedInt = Integer.toUnsignedLong(nextInt());
                product = unsignedInt * (long) bound;
                low = product & 0xFFFFFFFFL;
            }
        }
        return (int) (product >> 32);
    }

    public long nextLong() {
        return rng.nextLong();
    }

    public boolean nextBoolean() {
        return (rng.nextLong() & 1L) != 0L;
    }

    public float nextFloat() {
        return nextBits(24) * FLOAT_UNIT;
    }

    public double nextDouble() {
        return nextBits(53) * DOUBLE_UNIT;
    }

    /** Returns value in [min, max] inclusive. */
    public int nextIntBetweenInclusive(int min, int max) {
        return nextInt(max - min + 1) + min;
    }

    private long nextBits(int bits) {
        return rng.nextLong() >>> (64 - bits);
    }

    public SimulatedXoroshiro128PlusPlus getInternalRng() {
        return rng;
    }
}
