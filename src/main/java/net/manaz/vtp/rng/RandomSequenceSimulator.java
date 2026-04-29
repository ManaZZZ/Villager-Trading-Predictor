package net.manaz.vtp.rng;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Replicates Minecraft's RandomSequence and RandomSequences seeding logic.
 *
 * The server creates a RandomSequence for each named sequence key (e.g. "minecraft:trade_set/librarian/level_1")
 * using the formula:
 *   effectiveSeed = (includeWorldSeed ? worldSeed : 0) ^ salt
 *   seed128 = upgradeSeedTo128bitUnmixed(effectiveSeed)
 *   if (includeSequenceId) seed128 = seed128.xor(seedForKey(sequenceId))
 *   xoroshiro = new Xoroshiro128PlusPlus(seed128.mixed())
 *
 * Default RandomSequences state: salt=0, includeWorldSeed=true, includeSequenceId=true
 */
public class RandomSequenceSimulator {
    private static final long GOLDEN_RATIO_64 = 7640891576956012809L;
    private static final long SILVER_RATIO_64 = -7046029254386353131L;

    /**
     * Create a SimulatedRandomSource matching the server's RandomSequence for the given parameters.
     *
     * @param worldSeed the world seed
     * @param sequenceId the full sequence identifier string, e.g. "minecraft:trade_set/librarian/level_1"
     * @return a SimulatedRandomSource in the same initial state as the server's
     */
    public static SimulatedRandomSource createForSequence(long worldSeed, String sequenceId) {
        return createForSequence(worldSeed, sequenceId, 0, true, true);
    }

    public static SimulatedRandomSource createForSequence(
            long worldSeed, String sequenceId, int salt,
            boolean includeWorldSeed, boolean includeSequenceId) {
        long effectiveSeed = (includeWorldSeed ? worldSeed : 0L) ^ (long) salt;

        // upgradeSeedTo128bitUnmixed
        long lo = effectiveSeed ^ GOLDEN_RATIO_64;
        long hi = lo + SILVER_RATIO_64;

        if (includeSequenceId) {
            long[] keyHash = seedForKey(sequenceId);
            lo ^= keyHash[0];
            hi ^= keyHash[1];
        }

        // .mixed() applies mixStafford13 to both halves
        lo = mixStafford13(lo);
        hi = mixStafford13(hi);

        return new SimulatedRandomSource(lo, hi);
    }

    /**
     * Computes seedForKey: MD5-128 of the sequence ID string, split into two longs (big-endian).
     * Matches RandomSequence.seedForKey -> RandomSupport.seedFromHashOf.
     */
    static long[] seedForKey(String sequenceId) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] hash = md5.digest(sequenceId.getBytes(StandardCharsets.UTF_8));
            long lo = fromBytes(hash[0], hash[1], hash[2], hash[3],
                    hash[4], hash[5], hash[6], hash[7]);
            long hi = fromBytes(hash[8], hash[9], hash[10], hash[11],
                    hash[12], hash[13], hash[14], hash[15]);
            return new long[]{lo, hi};
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    /**
     * Matches Guava's Longs.fromBytes — big-endian byte-to-long conversion.
     */
    private static long fromBytes(byte b1, byte b2, byte b3, byte b4,
                                  byte b5, byte b6, byte b7, byte b8) {
        return ((long) b1 & 0xFFL) << 56
                | ((long) b2 & 0xFFL) << 48
                | ((long) b3 & 0xFFL) << 40
                | ((long) b4 & 0xFFL) << 32
                | ((long) b5 & 0xFFL) << 24
                | ((long) b6 & 0xFFL) << 16
                | ((long) b7 & 0xFFL) << 8
                | ((long) b8 & 0xFFL);
    }

    /**
     * Stafford variant 13 finalizer for 64-bit hashing.
     * Matches RandomSupport.mixStafford13 exactly.
     */
    static long mixStafford13(long value) {
        value = (value ^ (value >>> 30)) * -4658895280553007687L;
        value = (value ^ (value >>> 27)) * -7723592293110705685L;
        return value ^ (value >>> 31);
    }
}
