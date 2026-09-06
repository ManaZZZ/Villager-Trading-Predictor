package net.manaz.vtp.prediction;

import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.rng.*;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.TradeSet;
import java.util.*;

/** Forecasts consume copied RNG only. UI results use relative reroll counts. */
public final class PredictionEngine {
    public static final int MAX_ROUND = 1_000_000;
    private static int maxRerolls = 500;
    private static long revision;
    private static long preparedRevision = -1;
    private static UUID preparedUuid;
    private static boolean preparing;
    public static long revision() { return revision; }
    public static void changed() { revision++; targetCache.clear(); }
    private record TargetKey(long seed, UUID uuid, VillagerData data, EnchantmentTarget target,
            List<TradeSimulator.SimulatedOffer> actual, SingleplayerSequences.Seed snapshot,
            int next, boolean pending, int max) {}
    private static final Map<TargetKey, PredictionResult> targetCache = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TargetKey, PredictionResult> e) { return size() > 256; }
    };
    private static final Map<ResourceKey<TradeSet>, Integer> offsets = new HashMap<>();
    private static final Map<TradeSet, String> compatibility = new IdentityHashMap<>();
    private record TimelineKey(long worldSeed, ResourceKey<TradeSet> key, TradeSet definition,
                               long lo, long hi) {}
    private static final Map<TimelineKey, Timeline> timelines = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TimelineKey, Timeline> entry) { return size() > 8; }
    };
    private static final class Timeline {
        final TradeSet set;
        final NavigableMap<Integer, SingleplayerSequences.Seed> checkpoints = new TreeMap<>();
        final Map<Integer, List<TradeSimulator.SimulatedOffer>> rounds = new LinkedHashMap<>(1024, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, List<TradeSimulator.SimulatedOffer>> e) { return size() > 1024; }
        };
        Timeline(TradeSet set, SingleplayerSequences.Seed seed) { this.set = set; checkpoints.put(0, seed); }
        List<List<TradeSimulator.SimulatedOffer>> read(int start, int count) {
            List<List<TradeSimulator.SimulatedOffer>> result = new ArrayList<>();
            boolean cached = true;
            for (int i = start; i < start + count; i++) if (!rounds.containsKey(i)) { cached = false; break; }
            if (cached) {
                for (int i = start; i < start + count; i++) result.add(rounds.get(i));
                return List.copyOf(result);
            }
            var checkpoint = checkpoints.floorEntry(start);
            var rng = checkpoint.getValue().copy();
            for (int i = checkpoint.getKey(); i < start + count; i++) {
                if (i % 128 == 0) {
                    var state = rng.getInternalRng();
                    checkpoints.put(i, new SingleplayerSequences.Seed(state.getSeedLo(), state.getSeedHi()));
                }
                var offers = TradeSimulator.simulateTradeRound(set, rng, List.of());
                if (i >= start) { rounds.put(i, offers); result.add(offers); }
            }
            return List.copyOf(result);
        }
    }

    public static void setMaxRerolls(int max) { maxRerolls = Math.clamp(max, 1, 10000); changed(); }
    public static int getMaxRerolls() { return maxRerolls; }
    public static ResourceKey<TradeSet> sequenceKey(ResourceKey<TradeSet> key) {
        return TradeDataLoader.getTradeSet(key).flatMap(TradeSet::randomSequence)
                .map(id -> ResourceKey.create(Registries.TRADE_SET, id)).orElse(key);
    }
    public static int getOffset(ResourceKey<TradeSet> key) { return offsets.getOrDefault(sequenceKey(key), 0); }
    public static void setOffset(ResourceKey<TradeSet> key, int value) {
        offsets.put(sequenceKey(key), Math.clamp(value, 0, MAX_ROUND));
        VillagerTracker.invalidateSynchronization(); clearCache();
    }
    public static Map<ResourceKey<TradeSet>, Integer> getAllOffsets() { return Map.copyOf(offsets); }
    public static void loadOffsets(Map<ResourceKey<TradeSet>, Integer> saved) {
        offsets.clear(); saved.forEach((k, v) -> offsets.put(k, Math.clamp(v, 0, MAX_ROUND))); clearCache();
    }
    public static void clearAllOffsets() { offsets.clear(); clearCache(); }
    public static void clearCache() { timelines.clear(); compatibility.clear(); changed(); }

    public static String compatibilityProblem(Holder<VillagerProfession> profession, int level) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.enabledFeatures().contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE))
            return "Trade Rebalance is not supported";
        var key = TradeDataLoader.getTradeSetKey(profession, level);
        if (key == null) return "no trades at this level";
        var set = TradeDataLoader.getTradeSet(key);
        if (set.isEmpty()) return "trade definitions unavailable";
        return compatibility.computeIfAbsent(set.get(), TradeSimulator::compatibilityProblem);
    }
    public static List<List<TradeSimulator.SimulatedOffer>> getUpcomingRounds(long seed,
            Holder<VillagerProfession> profession, int level, int start, int count) {
        if (start < 0 || start > MAX_ROUND || count <= 0 || !compatibilityProblem(profession, level).isEmpty()) return List.of();
        var key = TradeDataLoader.getTradeSetKey(profession, level);
        TradeSet set = TradeDataLoader.getTradeSet(key).orElseThrow();
        var initial = RandomSequenceSimulator.createForSequence(seed, set.randomSequence().orElseThrow().toString()).getInternalRng();
        return read(seed, key, set, new SingleplayerSequences.Seed(initial.getSeedLo(), initial.getSeedHi()), start, count);
    }
    private static List<List<TradeSimulator.SimulatedOffer>> read(long seed, ResourceKey<TradeSet> key,
            TradeSet set, SingleplayerSequences.Seed state, int start, int count) {
        int bounded = Math.min(Math.min(count, 20000), MAX_ROUND - start + 1);
        return timelines.computeIfAbsent(new TimelineKey(seed, key, set, state.lo(), state.hi()),
                ignored -> new Timeline(set, state)).read(start, bounded);
    }
    public static List<List<TradeSimulator.SimulatedOffer>> getForecastRounds(long seed,
            Holder<VillagerProfession> profession, int level, int count) {
        if (!compatibilityProblem(profession, level).isEmpty()) return List.of();
        var key = TradeDataLoader.getTradeSetKey(profession, level);
        if (Minecraft.getInstance().getSingleplayerServer() != null) {
            var snapshot = SingleplayerSequences.get(key);
            if (snapshot.isEmpty()) return List.of();
            return read(seed, key, TradeDataLoader.getTradeSet(key).orElseThrow(), snapshot.get(), 0, count);
        }
        return getUpcomingRounds(seed, profession, level, VillagerTracker.getNextRoundForLevel(profession, level), count);
    }
    public static List<TradeSimulator.SimulatedOffer> simulateRound(long seed,
            Holder<VillagerProfession> profession, int level, int round) {
        var rounds = getUpcomingRounds(seed, profession, level, round, 1);
        return rounds.isEmpty() ? List.of() : rounds.getFirst();
    }

    /** Check actual current offers, then search the shared future. Historical consumed rounds cannot be targets. */
    public static PredictionResult predictForVillager(long seed, UUID uuid, VillagerData data, EnchantmentTarget target) {
        var actual = VillagerTracker.getCurrentOffers(uuid).orElse(List.of());
        if (matchesTarget(actual, target)) return new PredictionResult(Optional.of(0), 0, actual, actual);
        if (data.level() > 1 || VillagerTracker.isTradeLocked(uuid)) return new PredictionResult(Optional.empty(), 0, List.of(), actual);
        boolean pending = VillagerTracker.isAwaitingOffers(uuid);
        var tradeKey = TradeDataLoader.getTradeSetKey(data.profession(), data.level());
        TargetKey cacheKey = new TargetKey(seed, uuid, data, target, actual,
                tradeKey == null ? null : SingleplayerSequences.get(tradeKey).orElse(null),
                VillagerTracker.getNextRoundForLevel(data.profession(), data.level()), pending, maxRerolls);
        PredictionResult cached = targetCache.get(cacheKey);
        if (cached != null) return cached;
        if (!preparing) return new PredictionResult(Optional.empty(), 0, List.of(), actual);
        var future = getForecastRounds(seed, data.profession(), data.level(), maxRerolls);
        for (int i = 0; i < future.size(); i++) {
            if (matchesTarget(future.get(i), target)) {
                int remaining = SequencePosition.remaining(i, 0, pending);
                PredictionResult result = new PredictionResult(Optional.of(remaining), i + 1, future.get(i), actual);
                targetCache.put(cacheKey, result);
                return result;
            }
        }
        PredictionResult result = new PredictionResult(Optional.empty(), future.size(), List.of(), actual);
        if (!future.isEmpty()) targetCache.put(cacheKey, result);
        return result;
    }
    /** Refresh the view once per changed state on the client tick, never from a draw call. */
    public static void prepareView() {
        UUID uuid = VillagerTracker.getLastVillagerUuid();
        var data = VillagerTracker.getLastVillagerData();
        if (uuid == null || data.isEmpty() || !SeedProvider.hasSeed()) return;
        if (preparedRevision == revision && uuid.equals(preparedUuid)) return;
        preparing = true;
        try {
            long seed = SeedProvider.getSeed().orElseThrow();
            for (int level = 1; level <= 5; level++) getForecastRounds(seed, data.get().profession(), level, 50);
            for (var target : net.manaz.vtp.render.VillagerHudRenderer.getTargets())
                predictForVillager(seed, uuid, data.get(), target);
            preparedRevision = revision; preparedUuid = uuid;
        } finally { preparing = false; }
    }
    private static boolean matchesTarget(List<TradeSimulator.SimulatedOffer> offers, EnchantmentTarget target) {
        return offers.stream().anyMatch(o -> o.enchantment().filter(e ->
                target.matches(e.enchantment(), e.level(), e.emeraldPrice())).isPresent());
    }
    public static boolean matchesObserved(VillagerData data, int round, List<ObservedOffer> observed) {
        var predicted = simulateRound(SeedProvider.getSeed().orElseThrow(), data.profession(), data.level(), round);
        return !predicted.isEmpty() && OfferMatching.same(TradeSimulator.fingerprint(predicted), observed);
    }
    public static List<Integer> findObserved(VillagerData data, List<ObservedOffer> observed, int expected) {
        int start = Math.max(0, expected - maxRerolls);
        int count = Math.min(MAX_ROUND - start + 1, Math.max(maxRerolls, expected - start + maxRerolls));
        var rounds = getUpcomingRounds(SeedProvider.getSeed().orElseThrow(), data.profession(), data.level(), start, count);
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < rounds.size(); i++) {
            if (OfferMatching.same(TradeSimulator.fingerprint(rounds.get(i)), observed)) matches.add(start + i);
            if (matches.size() > 1) break; // Ambiguity must never silently pick a candidate.
        }
        return matches;
    }

    public record CalibrationSpec(ResourceKey<Enchantment> enchantment, int level, int price, boolean noTrade) {
        public CalibrationSpec(ResourceKey<Enchantment> ench, int level) { this(ench, level, -1, false); }
        public static CalibrationSpec of(ResourceKey<Enchantment> ench, int level, int price) { return new CalibrationSpec(ench, level, price, false); }
        public static CalibrationSpec missing() { return new CalibrationSpec(null, 0, -1, true); }
    }
    public static List<Integer> findCalibrationSequence(long seed, Holder<VillagerProfession> profession,
                                                       int level, List<CalibrationSpec> specs) {
        if (specs.isEmpty() || specs.size() > 100) return List.of();
        int next = VillagerTracker.getNextRoundForLevel(profession, level);
        int start = Math.max(0, next - maxRerolls);
        int count = Math.min(20000, maxRerolls + next - start + specs.size() - 1);
        var rounds = getUpcomingRounds(seed, profession, level, start, count);
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i + specs.size() <= rounds.size(); i++) {
            boolean match = true;
            for (int j = 0; j < specs.size(); j++) {
                CalibrationSpec spec = specs.get(j);
                var books = rounds.get(i + j).stream().flatMap(o -> o.enchantment().stream()).toList();
                if (spec.noTrade() ? !books.isEmpty() : books.stream().noneMatch(e ->
                        e.enchantment().is(spec.enchantment()) && e.level() == spec.level()
                                && (spec.price() < 0 || e.emeraldPrice() == spec.price()))) { match = false; break; }
            }
            if (match) matches.add(start + i + specs.size() - 1);
        }
        return List.copyOf(matches);
    }
    public static Optional<ResourceKey<TradeSet>> applyCalibration(Holder<VillagerProfession> profession, int level, int round) {
        var key = TradeDataLoader.getTradeSetKey(profession, level);
        if (key == null) return Optional.empty();
        setOffset(key, round); return Optional.of(key);
    }
}
