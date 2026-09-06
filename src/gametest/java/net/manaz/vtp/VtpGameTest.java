package net.manaz.vtp;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.mixin.accessor.*;
import net.manaz.vtp.prediction.*;
import net.manaz.vtp.render.*;
import net.manaz.vtp.rng.RandomSequenceSimulator;
import net.manaz.vtp.trade.*;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.util.Unit;
import java.util.*;

public class VtpGameTest implements FabricClientGameTest {
    private record Spawn(UUID uuid, VillagerData data) {}
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            Spawn spawn = server.computeOnServer(mc -> {
                Villager villager = new Villager(EntityTypes.VILLAGER, mc.overworld());
                villager.setNoAi(true);
                villager.setPos(0, -60, 0);
                villager.setVillagerData(villager.getVillagerData().withProfession(
                        mc.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN)));
                mc.overworld().addFreshEntity(villager);
                return new Spawn(villager.getUUID(), villager.getVillagerData());
            });
            context.runOnClient(mc -> {
                for (int level = 1; level <= 5; level++) {
                    String problem = PredictionEngine.compatibilityProblem(spawn.data().profession(), level);
                    check(problem.isEmpty(), "Vanilla librarian level " + level + " rejected: " + problem);
                }
                VillagerTracker.updateAndSelect(spawn.uuid(), spawn.data());
            });
            // Differential oracle: native offer generation with an isolated RNG and loot context.
            server.runOnServer(mc -> {
                Villager villager = (Villager) mc.overworld().getEntity(spawn.uuid());
                var method = AbstractVillager.class.getDeclaredMethod("addOffersFromItemListingsWithoutDuplicates",
                        LootContext.class, MerchantOffers.class, HolderSet.class, int.class);
                method.setAccessible(true);
                for (int level = 1; level <= 5; level++) {
                    var set = mc.registryAccess().lookupOrThrow(Registries.TRADE_SET)
                            .getValueOrThrow(spawn.data().profession().value().getTrades(level));
                    for (long seed : new long[]{0, 1, -123456789, Long.MAX_VALUE}) {
                        var nativeRng = new RandomSequence(seed, set.randomSequence().orElseThrow()).random();
                        var simulated = RandomSequenceSimulator.createForSequence(seed, set.randomSequence().orElseThrow().toString());
                        LootParams params = new LootParams.Builder(mc.overworld())
                                .withParameter(LootContextParams.THIS_ENTITY, villager)
                                .withParameter(LootContextParams.ORIGIN, villager.position())
                                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                                .create(LootContextParamSets.VILLAGER_TRADE);
                        var loot = new LootContext.Builder(params).withOptionalRandomSource(nativeRng).create(Optional.empty());
                        for (int round = 0; round < 1000; round++) {
                            MerchantOffers actual = new MerchantOffers();
                            method.invoke(null, loot, actual, set.getTrades(), set.calculateNumberOfTrades(loot));
                            var prediction = TradeSimulator.simulateTradeRound(set, simulated, List.of());
                            check(OfferMatching.same(ObservedOffer.copyOf(actual), TradeSimulator.fingerprint(prediction)),
                                    "Offer mismatch level=" + level + " seed=" + seed + " round=" + round);
                            var nativeState = (XoroshiroStateAccessor) (Object) ((XoroshiroSourceAccessor) nativeRng).vtp$getGenerator();
                            check(nativeState.vtp$getLo() == simulated.getInternalRng().getSeedLo()
                                    && nativeState.vtp$getHi() == simulated.getInternalRng().getSeedHi(),
                                    "RNG consumption mismatch level=" + level + " round=" + round);
                        }
                    }
                }
                check(((AbstractVillagerAccessor) villager).vtp$getExistingOffers() == null,
                        "Verification unexpectedly generated real villager offers");
            });
            context.waitFor(mc -> SingleplayerSequences.ready(spawn.uuid()));
            server.runOnServer(mc -> check(((AbstractVillagerAccessor) mc.overworld().getEntity(spawn.uuid()))
                    .vtp$getExistingOffers() == null, "Snapshot advanced real trade generation"));
            for (int iteration = 0; iteration < 12; iteration++) {
                var beforeView = context.computeOnClient(mc -> { PredictionEngine.prepareView(); HudView.tick(); return HudView.current(); });
                var predicted = context.computeOnClient(mc -> TradeSimulator.fingerprint(
                        PredictionEngine.getForecastRounds(mc.getSingleplayerServer().overworld().getSeed(),
                                spawn.data().profession(), 1, 1).getFirst()));
                var actual = server.computeOnServer(mc -> {
                    Villager villager = (Villager) mc.overworld().getEntity(spawn.uuid());
                    villager.setVillagerData(villager.getVillagerData().withProfession(
                            mc.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.NONE)));
                    villager.setVillagerData(spawn.data());
                    return ObservedOffer.copyOf(villager.getOffers());
                });
                check(OfferMatching.same(predicted, actual), "Live snapshot forecast drifted at reroll " + iteration);
                context.waitTicks(10);
                context.runOnClient(mc -> check(VillagerTracker.getCurrentOffers(spawn.uuid()).isPresent(),
                        "Authoritative existing offers were not published"));
                context.runOnClient(mc -> {
                    PredictionEngine.prepareView(); HudView.tick();
                    check(HudView.current().trades().getFirst().label().equals(beforeView.trades().getFirst().label()),
                            "Current label changed during generation");
                });
                if (iteration == 0) context.runOnClient(mc -> {
                    check(HudView.current().trades().stream().map(r -> List.of(r.label(), r.text(), r.price(), r.match())).toList().equals(
                            beforeView.trades().stream().map(r -> List.of(r.label(), r.text(), r.price(), r.match())).toList()),
                            "Observing the pending forecast shifted Current or numbered future rows");
                });
            }
            // Another villager consumes the same shared sequence while A keeps its old offers.
            server.runOnServer(mc -> {
                Villager other = new Villager(EntityTypes.VILLAGER, mc.overworld());
                other.setNoAi(true); other.setVillagerData(spawn.data());
                for (int i = 0; i < 8; i++) {
                    other.setVillagerData(other.getVillagerData().withProfession(
                            mc.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.NONE)));
                    other.setVillagerData(spawn.data()); other.getOffers();
                }
            });
            context.waitTicks(10);
            var afterOther = context.computeOnClient(mc -> TradeSimulator.fingerprint(
                    PredictionEngine.getForecastRounds(mc.getSingleplayerServer().overworld().getSeed(),
                            spawn.data().profession(), 1, 1).getFirst()));
            var nextActual = server.computeOnServer(mc -> {
                Villager other = new Villager(EntityTypes.VILLAGER, mc.overworld());
                other.setNoAi(true); other.setVillagerData(spawn.data());
                return ObservedOffer.copyOf(other.getOffers());
            });
            check(OfferMatching.same(afterOther, nextActual), "Another villager caused shared-sequence drift");

            // Exercise multiplayer observation accounting with deterministic, known round inputs.
            context.runOnClient(mc -> {
                UUID a = UUID.randomUUID(), b = UUID.randomUUID();
                var data = spawn.data();
                long seed = mc.getSingleplayerServer().overworld().getSeed();
                var rounds = PredictionEngine.getUpcomingRounds(seed, data.profession(), 1, 0, 100);
                VillagerTracker.update(a, data);
                VillagerTracker.observe(a, data, TradeSimulator.fingerprint(rounds.get(10)), false);
                PredictionEngine.applyCalibration(data.profession(), 1, 10);
                VillagerTracker.resetAfterCalibrate(a, data, 10);
                check(VillagerTracker.getNextRoundForLevel(data.profession(), 1) == 11, "Calibration double-counted existing rounds");
                var none = data.withProfession(mc.level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION)
                        .getOrThrow(VillagerProfession.NONE));
                VillagerTracker.update(a, none); VillagerTracker.update(a, data);
                VillagerTracker.observe(a, data, TradeSimulator.fingerprint(rounds.get(11)), false);
                check(VillagerTracker.getNextRoundForLevel(data.profession(), 1) == 12, "Confirmed reroll did not advance once");
                VillagerTracker.observe(a, data, TradeSimulator.fingerprint(rounds.get(11)), false);
                check(VillagerTracker.getNextRoundForLevel(data.profession(), 1) == 12, "Reopening advanced the sequence");
                PredictionEngine.applyCalibration(data.profession(), 1, 20);
                VillagerTracker.resetAfterCalibrate(b, data, 20);
                VillagerTracker.observe(a, data, TradeSimulator.fingerprint(rounds.get(11)), false);
                check(VillagerTracker.getNextRoundForLevel(data.profession(), 1) == 21, "Old villager rewound shared sequence");
                VillagerTracker.updateAndSelect(spawn.uuid(), spawn.data());
                var mending = net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,
                        net.minecraft.resources.Identifier.parse("minecraft:mending"));
                VillagerHudRenderer.setTarget(new EnchantmentTarget(mending, 1));
                var before = VillagerTracker.getSavedLevelOffers(spawn.uuid(), 1);
                VtpPersistence.save(seed);
                VtpPersistence.flush();
                VtpPersistence.load(seed);
                check(before.equals(VillagerTracker.getSavedLevelOffers(spawn.uuid(), 1)), "Observed offers lost on save/reload");
                check(VillagerHudRenderer.getTargets().size() == 1, "Targets lost on save/reload");
                VillagerTracker.updateAndSelect(spawn.uuid(), spawn.data());
            });
            context.waitFor(mc -> SingleplayerSequences.ready(spawn.uuid()));
            context.runOnClient(mc -> {
                VtpPersistence.saveCurrentWorldState();
                VtpPersistence.tick();
                var before = VillagerTracker.getTradeSetTotals();
                for (int i = 0; i < 10; i++) VillagerTracker.updateAndSelect(spawn.uuid(), spawn.data());
                check(before.equals(VillagerTracker.getTradeSetTotals()), "Repeated selection consumed rounds");
                VillagerTradingPredictor.LOGGER.info("[VTP TEST] PASS: 20000 vanilla rounds, 12 live rerolls, non-mutating snapshots, repeated selection.");
            });
            VtpUiGameTest.run(context);
            VtpHudSizeGameTest.run(context);
            VtpHudFadeGameTest.run(context);
            VtpLockedHudGameTest.run(context, tier -> server.runOnServer(mc -> {
                Villager villager = (Villager) mc.overworld().getEntity(spawn.uuid());
                villager.setVillagerData(spawn.data().withLevel(tier));
                villager.setVillagerXp(tier == 5 ? 250 : tier == 1 ? 1 : 10);
                String[] keys = {"mending", "unbreaking", "efficiency", "protection", "sharpness"};
                int[] levels = {1, 3, 5, 4, 5}, prices = {10, 11, 17, 22, 23};
                MerchantOffers offers = new MerchantOffers();
                for (int i = 0; i < (tier == 5 ? 5 : tier == 1 ? 1 : 3); i++) {
                    var book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
                    var enchants = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                            net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
                    var key = net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,
                            net.minecraft.resources.Identifier.withDefaultNamespace(keys[i]));
                    enchants.set(mc.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), levels[i]);
                    book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, enchants.toImmutable());
                    offers.add(new MerchantOffer(new ItemCost(net.minecraft.world.item.Items.EMERALD, prices[i]),
                            Optional.of(new ItemCost(net.minecraft.world.item.Items.BOOK, 1)), book, 12, 1, 0.2f));
                }
                var existing = ((AbstractVillagerAccessor) villager).vtp$getExistingOffers();
                check(existing != null, "Locked HUD fixture needs generated offers");
                existing.clear(); existing.addAll(offers);
            }));
            context.takeScreenshot("vtp-regression");
            context.runOnClient(mc -> { mc.options.guiScale().set(1); mc.getWindow().setGuiScale(1); });
            context.setScreen(() -> new net.manaz.vtp.gui.VtpSettingsScreen());
            context.waitTicks(2);
            context.takeScreenshot("vtp-settings");
            context.setScreen(() -> null);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
