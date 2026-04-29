package net.manaz.vtp.prediction;

import net.manaz.vtp.VillagerTradingPredictor;
import net.manaz.vtp.rng.RandomSequenceSimulator;
import net.manaz.vtp.rng.SimulatedRandomSource;
import net.manaz.vtp.trade.TradeDataLoader;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.TradeSet;

import java.util.*;

/**
 * Orchestrates trade prediction: for a given profession/level and world seed,
 * searches through rerolls to find when a target enchantment appears.
 *
 * Each trade set has its own independent RNG sequence. Sequence offsets are
 * stored per-trade-set so calibrating one profession does not affect others.
 * Use /vtp calibrate to anchor a trade set's sequence after joining multiplayer
 * or after missing rerolls while offline.
 */
public class PredictionEngine {
    private static final int DEFAULT_MAX_REROLLS = 500;
    private static int maxRerolls = DEFAULT_MAX_REROLLS;

    /**
     * Per-trade-set sequence offsets: how many rounds were consumed in each
     * sequence before the client started tracking. 0 = fresh world assumption.
     * Set per type via /vtp calibrate.
     */
    private static final Map<ResourceKey<TradeSet>, Integer> sequenceOffsets = new HashMap<>();

    // Cache: (seed, tradeSetKey, target, offset) -> result
    private static final Map<CacheKey, PredictionResult> cache = new HashMap<>();
    // Cache: (seed, tradeSetKey, target, startRound) -> result
    private static final Map<FromKey, PredictionResult> fromCache = new HashMap<>();
    // Cache: (seed, tradeSetKey, startRound, count) -> rounds
    private static final Map<UpcomingKey, List<List<TradeSimulator.SimulatedOffer>>> upcomingCache = new HashMap<>();

    private record CacheKey(long worldSeed, ResourceKey<TradeSet> tradeSetKey,
                            EnchantmentTarget target, int offset) {}
    private record FromKey(long worldSeed, ResourceKey<TradeSet> tradeSetKey,
                           EnchantmentTarget target, int startRound) {}
    private record UpcomingKey(long worldSeed, ResourceKey<TradeSet> tradeSetKey,
                               int startRound, int count) {}

    public static void setMaxRerolls(int max) {
        maxRerolls = max;
        cache.clear();
        fromCache.clear();
    }

    public static int getMaxRerolls() {
        return maxRerolls;
    }

    /** Returns the sequence offset for a specific trade set (0 if not calibrated). */
    public static int getOffset(ResourceKey<TradeSet> key) {
        return sequenceOffsets.getOrDefault(key, 0);
    }

    /** Sets the sequence offset for a specific trade set and clears prediction caches. */
    public static void setOffset(ResourceKey<TradeSet> key, int value) {
        sequenceOffsets.put(key, value);
        cache.clear();
        fromCache.clear();
    }

    /** Returns an unmodifiable snapshot of all per-type offsets (for persistence). */
    public static Map<ResourceKey<TradeSet>, Integer> getAllOffsets() {
        return Collections.unmodifiableMap(sequenceOffsets);
    }

    /** Restores all per-type offsets (called by persistence layer on world load). */
    public static void loadOffsets(Map<ResourceKey<TradeSet>, Integer> saved) {
        sequenceOffsets.clear();
        sequenceOffsets.putAll(saved);
        cache.clear();
        fromCache.clear();
    }

    /** Clears all offsets (resets to fresh-world assumption for all types). */
    public static void clearAllOffsets() {
        sequenceOffsets.clear();
        cache.clear();
        fromCache.clear();
    }

    public static void clearCache() {
        cache.clear();
        fromCache.clear();
        upcomingCache.clear();
    }

    /**
     * Predict rerolls needed for a target enchantment.
     * Starts searching from globalOffset (rounds 0..globalOffset-1 are already consumed).
     *
     * @param worldSeed         the world seed
     * @param professionHolder  the villager's profession
     * @param level             the villager's level (1-5)
     * @param target            the enchantment + level to search for
     * @return prediction result
     */
    public static PredictionResult predict(
            long worldSeed,
            Holder<VillagerProfession> professionHolder,
            int level,
            EnchantmentTarget target) {

        ResourceKey<TradeSet> tradeSetKey = TradeDataLoader.getTradeSetKey(professionHolder, level);
        if (tradeSetKey == null) {
            return new PredictionResult(Optional.empty(), 0, List.of(), List.of());
        }

        int offset = getOffset(tradeSetKey);
        CacheKey cacheKey = new CacheKey(worldSeed, tradeSetKey, target, offset);
        PredictionResult cached = cache.get(cacheKey);
        if (cached != null) return cached;

        PredictionResult result = runPredictionFrom(worldSeed, tradeSetKey, target, offset);
        cache.put(cacheKey, result);
        return result;
    }

    /**
     * Find the first occurrence of the target starting at or after {@code startRound},
     * ignoring globalOffset. Useful for finding the next target after one has been collected.
     */
    public static PredictionResult predictFrom(
            long worldSeed,
            Holder<VillagerProfession> professionHolder,
            int level,
            EnchantmentTarget target,
            int startRound) {

        ResourceKey<TradeSet> tradeSetKey = TradeDataLoader.getTradeSetKey(professionHolder, level);
        if (tradeSetKey == null) return new PredictionResult(Optional.empty(), 0, List.of(), List.of());

        FromKey key = new FromKey(worldSeed, tradeSetKey, target, startRound);
        PredictionResult cached = fromCache.get(key);
        if (cached != null) return cached;

        PredictionResult result = runPredictionFrom(worldSeed, tradeSetKey, target, startRound);
        fromCache.put(key, result);
        return result;
    }

    /**
     * Returns the earliest target occurrence at or ahead of the current sequence position
     * ({@code globalOffset + consumedRounds}). Automatically skips past occurrences that have
     * already been collected, so the display always shows the next actionable target.
     */
    public static PredictionResult predictActive(
            long worldSeed,
            Holder<VillagerProfession> professionHolder,
            int level,
            EnchantmentTarget target,
            int consumedRounds) {

        ResourceKey<TradeSet> tradeSetKey = TradeDataLoader.getTradeSetKey(professionHolder, level);
        int offset = tradeSetKey != null ? getOffset(tradeSetKey) : 0;
        int currentPos = offset + consumedRounds;
        return predictFrom(worldSeed, professionHolder, level, target, currentPos);
    }

    /**
     * Return the simulated offers for the next {@code count} rounds starting at {@code startRound}.
     * Used to render the "upcoming rolls" panel.
     *
     * @param startRound absolute sequence round index to start from (= globalOffset + rerollsDone)
     */
    public static List<List<TradeSimulator.SimulatedOffer>> getUpcomingRounds(
            long worldSeed,
            Holder<VillagerProfession> professionHolder,
            int level,
            int startRound,
            int count) {

        ResourceKey<TradeSet> tradeSetKey = TradeDataLoader.getTradeSetKey(professionHolder, level);
        if (tradeSetKey == null) return List.of();

        UpcomingKey key = new UpcomingKey(worldSeed, tradeSetKey, startRound, count);
        List<List<TradeSimulator.SimulatedOffer>> cached = upcomingCache.get(key);
        if (cached != null) return cached;

        Optional<TradeSet> tradeSetOpt = TradeDataLoader.getTradeSet(tradeSetKey);
        if (tradeSetOpt.isEmpty()) return List.of();

        TradeSet tradeSet = tradeSetOpt.get();
        List<Holder<Enchantment>> tradeableEnchantments = TradeDataLoader.getTradeableEnchantments();

        String sequenceId = tradeSet.randomSequence()
                .map(Object::toString)
                .orElse(tradeSetKey.identifier().toString());

        SimulatedRandomSource rng = RandomSequenceSimulator.createForSequence(worldSeed, sequenceId);

        // Advance to startRound
        for (int i = 0; i < startRound; i++) {
            TradeSimulator.simulateTradeRound(tradeSet, rng, tradeableEnchantments);
        }

        List<List<TradeSimulator.SimulatedOffer>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(TradeSimulator.simulateTradeRound(tradeSet, rng, tradeableEnchantments));
        }

        upcomingCache.put(key, result);
        return result;
    }

    /**
     * Simulate a single round at the given absolute sequence position.
     * Returns empty list if the trade set is unavailable.
     */
    public static List<TradeSimulator.SimulatedOffer> simulateRound(
            long worldSeed,
            Holder<VillagerProfession> professionHolder,
            int level,
            int round) {
        List<List<TradeSimulator.SimulatedOffer>> rounds =
                getUpcomingRounds(worldSeed, professionHolder, level, round, 1);
        return rounds.isEmpty() ? List.of() : rounds.get(0);
    }

    /**
     * Calibrate the sequence offset by finding which round produced the observed enchantment.
     * Call this right after placing a villager, while looking at it, to anchor predictions
     * to the correct position in the sequence.
     *
     * @param worldSeed        the world seed
     * @param professionHolder the villager's current profession
     * @param level            the villager's current level
     * @param enchKey          the enchantment the villager currently offers
     * @param exactLevel       the exact level of that enchantment
     * @param minRound         earliest round to consider — pass (rerollsDone - 1) to skip
     *                         rounds that are impossible given how many times this specific
     *                         villager has already been assigned trades
     * @return the absolute round index P that was found, or -1 if not found within maxRerolls
     */

    /**
     * A single observation in a calibration sequence.
     * - noTrade=true matches a round with no enchantment-book offers.
     * - price < 0 means "any price"; otherwise the round's enchantment offer must
     *   match the additional emerald cost exactly.
     */
    public record CalibrationSpec(ResourceKey<Enchantment> enchantment, int level,
                                  int price, boolean noTrade) {
        public CalibrationSpec(ResourceKey<Enchantment> enchantment, int level) {
            this(enchantment, level, -1, false);
        }
        public static CalibrationSpec of(ResourceKey<Enchantment> ench, int level, int price) {
            return new CalibrationSpec(ench, level, price, false);
        }
        public static CalibrationSpec missing() {
            return new CalibrationSpec(null, 0, -1, true);
        }
    }

    /**
     * Search for starting positions where a sequence of consecutive rounds matches
     * the given spec list. For example, specs [A, B, C] find rounds N where:
     *   round N contains A, round N+1 contains B, round N+2 contains C.
     *
     * Returns the CURRENT round (N + specs.size() - 1), i.e. the round corresponding
     * to the last spec — which is typically what the user is currently seeing on the
     * villager. The caller can pass this round directly to applyCalibration().
     */
    public static List<Integer> findCalibrationSequence(
            long worldSeed,
            Holder<VillagerProfession> professionHolder,
            int villagerLevel,
            List<CalibrationSpec> specs) {

        if (specs.isEmpty()) return List.of();

        ResourceKey<TradeSet> tradeSetKey = TradeDataLoader.getTradeSetKey(professionHolder, villagerLevel);
        if (tradeSetKey == null) return List.of();

        Optional<TradeSet> tradeSetOpt = TradeDataLoader.getTradeSet(tradeSetKey);
        if (tradeSetOpt.isEmpty()) return List.of();

        TradeSet tradeSet = tradeSetOpt.get();
        List<Holder<Enchantment>> tradeableEnchantments = TradeDataLoader.getTradeableEnchantments();

        String sequenceId = tradeSet.randomSequence()
                .map(Object::toString)
                .orElse(tradeSetKey.identifier().toString());

        SimulatedRandomSource rng = RandomSequenceSimulator.createForSequence(worldSeed, sequenceId);

        record OfferInfo(int level, int price) {}

        int simulateUpTo = maxRerolls + specs.size() - 1;
        List<Map<ResourceKey<Enchantment>, OfferInfo>> roundEnchants = new ArrayList<>(simulateUpTo);
        for (int round = 0; round < simulateUpTo; round++) {
            List<TradeSimulator.SimulatedOffer> offers =
                    TradeSimulator.simulateTradeRound(tradeSet, rng, tradeableEnchantments);
            Map<ResourceKey<Enchantment>, OfferInfo> inRound = new HashMap<>();
            for (TradeSimulator.SimulatedOffer offer : offers) {
                offer.enchantment().ifPresent(e ->
                        e.enchantment().unwrapKey().ifPresent(k ->
                                inRound.put(k, new OfferInfo(e.level(), e.additionalCost()))));
            }
            roundEnchants.add(inRound);
        }

        List<Integer> matches = new ArrayList<>();
        for (int start = 0; start <= simulateUpTo - specs.size(); start++) {
            boolean allMatch = true;
            for (int i = 0; i < specs.size(); i++) {
                CalibrationSpec spec = specs.get(i);
                Map<ResourceKey<Enchantment>, OfferInfo> inRound = roundEnchants.get(start + i);
                if (spec.noTrade()) {
                    if (!inRound.isEmpty()) { allMatch = false; break; }
                } else {
                    OfferInfo info = inRound.get(spec.enchantment());
                    if (info == null || info.level() != spec.level()) {
                        allMatch = false; break;
                    }
                    if (spec.price() >= 0 && info.price() != spec.price()) {
                        allMatch = false; break;
                    }
                }
            }
            if (allMatch) {
                matches.add(start + specs.size() - 1);
            }
        }
        return matches;
    }

    /**
     * Apply a specific round as the calibrated sequence offset for the trade set
     * of the given profession+level. Call this after the user confirms a round
     * from findCalibrationMatches().
     *
     * @return the ResourceKey<TradeSet> that was calibrated, or empty if lookup failed
     */
    public static Optional<ResourceKey<TradeSet>> applyCalibration(
            Holder<VillagerProfession> professionHolder,
            int level,
            int round) {

        ResourceKey<TradeSet> tradeSetKey = TradeDataLoader.getTradeSetKey(professionHolder, level);
        if (tradeSetKey == null) return Optional.empty();
        setOffset(tradeSetKey, round);
        VillagerTradingPredictor.LOGGER.info("[VTP] Calibrated {}: offset = {}",
                tradeSetKey.identifier(), round);
        return Optional.of(tradeSetKey);
    }

    private static PredictionResult runPredictionFrom(
            long worldSeed,
            ResourceKey<TradeSet> tradeSetKey,
            EnchantmentTarget target,
            int startRound) {

        Optional<TradeSet> tradeSetOpt = TradeDataLoader.getTradeSet(tradeSetKey);
        if (tradeSetOpt.isEmpty()) {
            VillagerTradingPredictor.LOGGER.warn("TradeSet not found: {}", tradeSetKey);
            return new PredictionResult(Optional.empty(), 0, List.of(), List.of());
        }

        TradeSet tradeSet = tradeSetOpt.get();
        List<Holder<Enchantment>> tradeableEnchantments = TradeDataLoader.getTradeableEnchantments();

        String sequenceId = tradeSet.randomSequence()
                .map(Object::toString)
                .orElse(tradeSetKey.identifier().toString());

        SimulatedRandomSource rng = RandomSequenceSimulator.createForSequence(worldSeed, sequenceId);

        for (int i = 0; i < startRound; i++) {
            TradeSimulator.simulateTradeRound(tradeSet, rng, tradeableEnchantments);
        }

        List<TradeSimulator.SimulatedOffer> firstRollOffers = null;

        for (int reroll = startRound; reroll < startRound + maxRerolls; reroll++) {
            List<TradeSimulator.SimulatedOffer> offers =
                    TradeSimulator.simulateTradeRound(tradeSet, rng, tradeableEnchantments);

            if (reroll == startRound) {
                firstRollOffers = offers;
            }

            for (TradeSimulator.SimulatedOffer offer : offers) {
                if (offer.enchantment().isPresent()) {
                    TradeSimulator.EnchantmentResult ench = offer.enchantment().get();
                    if (target.matches(ench.enchantment(), ench.level(), ench.additionalCost())) {
                        return new PredictionResult(
                                Optional.of(reroll),
                                reroll,
                                offers,
                                firstRollOffers != null ? firstRollOffers : offers
                        );
                    }
                }
            }
        }

        return new PredictionResult(
                Optional.empty(),
                startRound + maxRerolls,
                List.of(),
                firstRollOffers != null ? firstRollOffers : List.of()
        );
    }
}
