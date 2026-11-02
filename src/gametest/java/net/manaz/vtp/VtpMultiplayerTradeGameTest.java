package net.manaz.vtp;

import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.prediction.*;
import net.manaz.vtp.render.HudView;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.rng.RandomSequenceSimulator;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.Level;

import java.util.*;

/** Exercise remote login codecs without singleplayer's shortcut to the server's registry objects. */
public final class VtpMultiplayerTradeGameTest implements FabricClientGameTest {
    private static final long SEED = 1234;

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            try {
                context.runOnClient(VtpMultiplayerTradeGameTest::verifyRemoteRegistries);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static void verifyRemoteRegistries(Minecraft mc) throws ReflectiveOperationException {
        var server = mc.getSingleplayerServer();
        var collector = new RegistryDataCollector();
        RegistrySynchronization.packRegistries(server.registryAccess().createSerializationContext(NbtOps.INSTANCE),
                server.registryAccess(), Set.of(), collector::appendContents);
        collector.appendTags(TagNetworkSerialization.serializeTagsToNetwork(server.registries()));
        var remote = collector.collectGameRegistries(ResourceProvider.EMPTY, RegistryAccess.EMPTY, false);
        check(remote.lookup(Registries.TRADE_SET).isEmpty() && remote.lookup(Registries.VILLAGER_TRADE).isEmpty(),
                "Remote login unexpectedly includes server-only trade registries");

        // Restore both fields before returning control to the game's next tick.
        var serverField = Minecraft.class.getDeclaredField("singleplayerServer");
        serverField.setAccessible(true);
        var accessField = Level.class.getDeclaredField("registryAccess");
        accessField.setAccessible(true);
        var originalAccess = mc.level.registryAccess();
        try {
            serverField.set(mc, null);
            accessField.set(mc.level, remote);
            ClientTradeRegistry.clear(); // Verify loading also works without eager initialization.
            check(!TradeDataLoader.getTradeableEnchantments().isEmpty(), "Synced enchantments unavailable");
            var profession = remote.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN);
            List<List<ObservedOffer>> noviceRounds = List.of();
            for (int level = 1; level <= 5; level++) {
                var key = profession.value().getTrades(level);
                var fallback = TradeDataLoader.getTradeSet(key).orElseThrow();
                var nativeSet = server.registryAccess().lookupOrThrow(Registries.TRADE_SET).getValueOrThrow(key);
                String problem = PredictionEngine.compatibilityProblem(profession, level);
                check(problem.isEmpty(), "Remote librarian rejected: " + key + ": " + problem);

                // The 26.2 bug surfaced here: the local HolderSet cannot serialize with remote-only ops.
                var ops = TradeDataLoader.createSerializationContext(fallback, JsonOps.INSTANCE);
                var encoded = TradeSet.CODEC.encodeStart(ops, fallback).getOrThrow();
                var decoded = TradeSet.CODEC.parse(ops, encoded).getOrThrow();
                check(TradeDataLoader.getTradesFromSet(nativeSet).stream().map(h -> h.unwrapKey()).toList().equals(
                        TradeDataLoader.getTradesFromSet(decoded).stream().map(h -> h.unwrapKey()).toList()),
                        "Remote fallback changed trade order: " + key);

                var rng = RandomSequenceSimulator.createForSequence(SEED, nativeSet.randomSequence().orElseThrow().toString());
                List<List<ObservedOffer>> expected = new ArrayList<>();
                var actual = PredictionEngine.getUpcomingRounds(SEED, profession, level, 0, 100);
                check(actual.size() == 100, "Remote forecast unavailable: " + key);
                for (int round = 0; round < 100; round++) {
                    expected.add(TradeSimulator.fingerprint(TradeSimulator.simulateTradeRound(nativeSet, rng, List.of())));
                    check(OfferMatching.same(expected.getLast(), TradeSimulator.fingerprint(actual.get(round))),
                            "Remote forecast differs: " + key + " round=" + round);
                }
                if (level == 1) noviceRounds = expected;
            }

            var data = new VillagerData(remote.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS), profession, 1);
            UUID uuid = UUID.randomUUID();
            VillagerTracker.updateAndSelect(uuid, data);
            SeedProvider.setManualSeed(SEED);
            check(CalibrationService.problem(data).isEmpty(), "Manual calibration blocked with a known seed");
            int end = -1, consecutive = 0;
            for (int i = 0; i < noviceRounds.size() - 1; i++) {
                boolean book = noviceRounds.get(i).stream().anyMatch(o -> !o.books().isEmpty());
                consecutive = book ? consecutive + 1 : 0;
                if (consecutive == 5) { end = i; break; }
            }
            check(end >= 0, "Test seed has no five consecutive book rounds");
            for (int round = end - 4; round <= end; round++) {
                var book = noviceRounds.get(round).stream().flatMap(o -> o.books().stream()).findFirst().orElseThrow();
                CalibrationListStore.add(CalibrationListStore.Observation.ofTrade(
                        ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(book.key())), book.level(), -1));
            }
            var matches = CalibrationService.search(uuid, data, CalibrationListStore.toSpecs());
            check(matches.equals(List.of(end)), "Five manually entered enchantments failed to align: " + matches);
            check(CalibrationService.confirm(uuid, data, end), "Manual calibration confirmation failed");
            check(OfferMatching.same(noviceRounds.get(end + 1), TradeSimulator.fingerprint(
                    PredictionEngine.getForecastRounds(SEED, profession, 1, 1).getFirst())),
                    "Forecast did not continue after manual calibration");
            HudView.tick();
            check(HudView.current() != null && HudView.current().warning().isEmpty(), "HUD still blocks calibrated forecasts");

            var key = profession.value().getTrades(1);
            var previous = TradeDataLoader.getTradeSet(key).orElseThrow();
            ClientTradeRegistry.clear();
            PredictionEngine.clearCache();
            var reloaded = TradeDataLoader.getTradeSet(key).orElseThrow();
            check(reloaded != previous, "Session reset reused old trade holders");
            TradeSet.CODEC.encodeStart(TradeDataLoader.createSerializationContext(reloaded, JsonOps.INSTANCE), reloaded).getOrThrow();
            check(remote.lookup(Registries.TRADE_SET).isEmpty() && remote.lookup(Registries.VILLAGER_TRADE).isEmpty(),
                    "Fallback mutated the network registries");
            VtpQolGameTest.verifyRemote(mc);
            VillagerTradingPredictor.LOGGER.info("[VTP MULTIPLAYER TEST] PASS: remote registry codecs, all five tiers, "
                    + "500 forecast rounds, five-book manual calibration, HUD and session reset.");
        } finally {
            ClientTradeRegistry.clear();
            PredictionEngine.clearAllOffsets();
            VillagerTracker.clear();
            CalibrationListStore.clear();
            SeedProvider.clearManualSeed();
            accessField.set(mc.level, originalAccess);
            serverField.set(mc, server);
        }
    }
}
