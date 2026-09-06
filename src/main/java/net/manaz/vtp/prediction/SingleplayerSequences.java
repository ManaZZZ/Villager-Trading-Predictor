package net.manaz.vtp.prediction;

import net.manaz.vtp.VillagerTradingPredictor;
import net.manaz.vtp.mixin.accessor.*;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.rng.*;
import net.manaz.vtp.trade.ObservedOffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import java.util.*;

/** Server-thread reads, immutable messages, client-thread publication. Never calls getOffers/fork/next on live RNG. */
public final class SingleplayerSequences {
    public record Seed(long lo, long hi) {
        public SimulatedRandomSource copy() { return new SimulatedRandomSource(lo, hi); }
    }
    private record Snapshot(UUID uuid, VillagerData data, List<ObservedOffer> offers,
                            Map<ResourceKey<TradeSet>, Seed> seeds, Object registry, boolean tradeLocked) {}
    private static Map<ResourceKey<TradeSet>, Seed> seeds = Map.of();
    private static UUID selected;
    private static Object registry;
    private static long epoch, receivedAt;
    private static boolean inFlight;
    private static int ticks;
    private SingleplayerSequences() {}

    public static void clear() {
        epoch++; inFlight = false; selected = null; seeds = Map.of(); registry = null; receivedAt = 0;
    }
    public static boolean ready(UUID uuid) {
        return uuid != null && uuid.equals(selected) && System.nanoTime() - receivedAt < 2_000_000_000L;
    }
    public static Optional<Seed> get(ResourceKey<TradeSet> key) {
        return ready(VillagerTracker.getLastVillagerUuid()) ? Optional.ofNullable(seeds.get(key)) : Optional.empty();
    }
    public static void tick(Minecraft client) {
        var server = client.getSingleplayerServer();
        UUID uuid = VillagerTracker.getLastVillagerUuid();
        if (server == null || client.level == null || uuid == null || inFlight || ++ticks % 5 != 0) return;
        var dimension = client.level.dimension();
        long token = epoch;
        inFlight = true;
        server.execute(() -> {
            Snapshot snapshot = null;
            try {
                var level = server.getLevel(dimension);
                if (level != null && level.getEntity(uuid) instanceof Villager villager) {
                    VillagerData data = villager.getVillagerData();
                    Map<ResourceKey<TradeSet>, net.minecraft.resources.Identifier> keys = new HashMap<>();
                    for (int tier = 1; tier <= 5; tier++) {
                        var key = data.profession().value().getTrades(tier);
                        if (key != null) server.registryAccess().lookupOrThrow(Registries.TRADE_SET)
                                .getOptional(key).flatMap(TradeSet::randomSequence).ifPresent(id -> keys.put(key, id));
                    }
                    Map<net.minecraft.resources.Identifier, Seed> existing = new HashMap<>();
                    var sequences = server.getRandomSequences();
                    sequences.forAllSequences((id, sequence) -> {
                        if (keys.containsValue(id) && sequence.random() instanceof XoroshiroRandomSource rng) {
                            var state = (XoroshiroStateAccessor) (Object) ((XoroshiroSourceAccessor) rng).vtp$getGenerator();
                            existing.put(id, new Seed(state.vtp$getLo(), state.vtp$getHi()));
                        }
                    });
                    var defaults = (RandomSequencesAccessor) sequences;
                    Map<ResourceKey<TradeSet>, Seed> copied = new HashMap<>();
                    keys.forEach((key, id) -> {
                        Seed seed = existing.get(id);
                        if (seed == null) {
                            var initial = RandomSequenceSimulator.createForSequence(server.overworld().getSeed(),
                                    id.toString(), defaults.vtp$getSalt(), defaults.vtp$includeWorldSeed(),
                                    defaults.vtp$includeSequenceId()).getInternalRng();
                            seed = new Seed(initial.getSeedLo(), initial.getSeedHi());
                        }
                        copied.put(key, seed);
                    });
                    var actual = ((AbstractVillagerAccessor) villager).vtp$getExistingOffers();
                    snapshot = new Snapshot(uuid, data, actual == null ? null : ObservedOffer.copyOf(actual),
                            Map.copyOf(copied), server.registryAccess(), villager.getVillagerXp() > 0);
                }
            } catch (Exception e) {
                VillagerTradingPredictor.LOGGER.warn("[VTP] Could not snapshot integrated server", e);
            }
            Snapshot result = snapshot;
            client.execute(() -> {
                if (token != epoch || client.getSingleplayerServer() != server) return;
                if (client.level == null || !client.level.dimension().equals(dimension)) { inFlight = false; return; }
                inFlight = false;
                if (result == null || !uuid.equals(VillagerTracker.getLastVillagerUuid())) return;
                if (registry != result.registry()) { PredictionEngine.clearCache(); registry = result.registry(); }
                if (!uuid.equals(selected) || !seeds.equals(result.seeds())) PredictionEngine.changed();
                selected = uuid; seeds = result.seeds(); receivedAt = System.nanoTime();
                if (result.offers() == null) VillagerTracker.observePending(uuid, result.data());
                else VillagerTracker.observe(uuid, result.data(), result.offers(), true);
                VillagerTracker.setTradeLocked(uuid, result.tradeLocked());
            });
        });
    }
}
