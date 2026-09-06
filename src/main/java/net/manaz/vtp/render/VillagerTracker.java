package net.manaz.vtp.render;

import net.manaz.vtp.VillagerTradingPredictor;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.prediction.*;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import java.util.*;

/** Client-thread state. Observations never come from rendering or simulated offers. */
public final class VillagerTracker {
    private static VillagerData lastVillagerData;
    private static UUID lastVillagerUuid, interactionUuid, menuUuid;
    private static int menuId = -1;
    private static long interactionAt;
    private static boolean interactionAmbiguous;
    private static boolean debug;
    public static void setDebug(boolean enabled) { debug = enabled; }
    public static void invalidateSynchronization() { synchronizedSequences.clear(); }
    private static final Map<UUID, State> states = new HashMap<>();
    private static final Map<String, Integer> totals = new HashMap<>();
    private static final Set<String> synchronizedSequences = new HashSet<>();

    public record OfferData(String enchantmentKey, int enchantmentLevel, int additionalCost) {}
    public record VillagerSavedState(int rerollCount, int currentRound,
            Map<Integer, Integer> levelRounds, int lastKnownLevel,
            Map<Integer, List<OfferData>> levelOffers, boolean awaitingOffers,
            boolean hasGeneratedOffers, String profession, List<ObservedOffer> observedOffers, boolean tradeLocked) {}

    private static final class State {
        VillagerData data;
        String profession;
        int level = -1, observedLevel = -1, rerolls, round = -1;
        boolean pending, knownBoundary, hasObservation, everObserved, tradeLocked;
        long revision;
        List<ObservedOffer> observed = List.of();
        final Map<Integer, Integer> rounds = new HashMap<>();
        final Map<Integer, List<OfferData>> levelOffers = new HashMap<>();
    }

    public static void updateAndSelect(UUID uuid, VillagerData data) {
        lastVillagerUuid = uuid; lastVillagerData = data; update(uuid, data);
    }
    public static void update(UUID uuid, VillagerData data) {
        if (uuid.equals(lastVillagerUuid)) lastVillagerData = data;
        State s = states.computeIfAbsent(uuid, ignored -> new State());
        String profession = professionId(data.profession());
        boolean changed = s.profession != null && !s.profession.equals(profession);
        if (changed || s.level != data.level()) s.revision++;
        if (changed) {
            s.observed = List.of(); s.hasObservation = false; s.observedLevel = -1; s.tradeLocked = false;
            s.rounds.clear(); s.levelOffers.clear(); s.round = -1;
            s.pending = hasProfession(data); s.knownBoundary = true;
        } else if (s.profession == null || s.level != data.level()) {
            s.pending = hasProfession(data); s.round = -1; s.knownBoundary = s.level > 0;
        }
        s.profession = profession; s.level = data.level(); s.data = data;
    }
    public static void beginMerchantInteraction(UUID uuid, VillagerData data) {
        updateAndSelect(uuid, data);
        interactionAmbiguous = interactionUuid != null && !uuid.equals(interactionUuid)
                && System.nanoTime() - interactionAt < 5_000_000_000L;
        interactionUuid = uuid; interactionAt = System.nanoTime(); menuUuid = null; menuId = -1;
    }
    public static void clearMerchantInteraction() { interactionUuid = null; menuUuid = null; menuId = -1; interactionAmbiguous = false; }
    public static void onMerchantOffers(ClientboundMerchantOffersPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof MerchantMenu menu)
                || menu.containerId != packet.getContainerId()) return;
        if (interactionAmbiguous) { clearMerchantInteraction(); return; }
        if (interactionUuid != null && System.nanoTime() - interactionAt < 5_000_000_000L) {
            menuUuid = interactionUuid; menuId = packet.getContainerId(); interactionUuid = null;
        }
        if (menuUuid == null || menuId != packet.getContainerId()) return;
        State s = states.get(menuUuid);
        if (s == null || s.data == null) return;
        observe(menuUuid, s.data.withLevel(packet.getVillagerLevel()),
                ObservedOffer.copyOf(packet.getOffers()), mc.getSingleplayerServer() != null);
        setTradeLocked(menuUuid, packet.getVillagerXp() > 0);
    }

    public static void observe(UUID uuid, VillagerData data, List<ObservedOffer> offers, boolean authoritative) {
        update(uuid, data);
        State s = states.get(uuid);
        if (!s.pending && s.hasObservation && OfferMatching.same(s.observed, offers)
                && (authoritative || synchronizedSequences.contains(sequenceKey(data.profession(), data.level())))) return;
        List<ObservedOffer> newOffers = s.observedLevel > 0 && data.level() > s.observedLevel
                ? OfferMatching.added(s.observed, offers) : offers;
        boolean canAssignLevel = data.level() == 1 || (s.observedLevel > 0 && data.level() == s.observedLevel + 1);
        int expected = getNextRoundForLevel(data.profession(), data.level());
        String sequence = sequenceKey(data.profession(), data.level());
        int match = -1;
        if (!authoritative && canAssignLevel && SeedProvider.hasSeed()
                && PredictionEngine.compatibilityProblem(data.profession(), data.level()).isEmpty()) {
            if (s.pending && s.knownBoundary && synchronizedSequences.contains(sequence)
                    && PredictionEngine.matchesObserved(data, expected, newOffers)) {
                match = expected;
            } else {
                List<Integer> candidates = PredictionEngine.findObserved(data, newOffers, expected);
                if (candidates.size() == 1) match = candidates.getFirst();
            }
            if (match >= 0) {
                setNextRound(data, s.knownBoundary ? match + 1 : Math.max(expected, match + 1));
                synchronizedSequences.add(sequence);
            } else synchronizedSequences.remove(sequence);
        }
        if (s.pending && s.knownBoundary && s.everObserved && data.level() == 1) s.rerolls++;
        s.everObserved = true; s.revision++; PredictionEngine.changed();
        s.round = match; s.pending = false; s.knownBoundary = false;
        s.observed = List.copyOf(offers); s.hasObservation = true; s.observedLevel = data.level();
        if (canAssignLevel) {
            s.levelOffers.put(data.level(), bookData(newOffers));
            if (match >= 0) s.rounds.put(data.level(), match);
        }
        VtpPersistence.markDirty();
        if (debug) VillagerTradingPredictor.LOGGER.info(
                "[VTP] observation villager={} sequence={} expected={} matched={} source={} offers={}",
                uuid, sequence, expected, match, authoritative ? "integrated server" : "packet", newOffers);
    }
    /** Read the server field without forcing lazy offer generation. */
    public static void observePending(UUID uuid, VillagerData data) {
        update(uuid, data);
        State s = states.get(uuid);
        if (!s.pending) {
            s.revision++; PredictionEngine.changed();
            s.pending = true; s.knownBoundary = s.hasObservation;
            s.observed = List.of(); s.hasObservation = false; s.observedLevel = -1; s.tradeLocked = false;
            s.round = -1; s.levelOffers.clear(); s.rounds.clear();
        }
    }
    public static String status(UUID uuid, VillagerData data) {
        String problem = PredictionEngine.compatibilityProblem(data.profession(), data.level());
        if (!problem.isEmpty()) return "Forecast unavailable: " + problem;
        if (Minecraft.getInstance().getSingleplayerServer() != null)
            return SingleplayerSequences.ready(uuid) ? "Live server snapshot" : "Waiting for server snapshot";
        return synchronizedSequences.contains(sequenceKey(data.profession(), data.level()))
                ? "Observed match; vanilla server data assumed" : "Uncertain — open trades or calibrate";
    }
    public static void setTradeLocked(UUID uuid, boolean locked) {
        State s = states.get(uuid);
        if (s != null && s.tradeLocked != locked) {
            s.tradeLocked = locked; PredictionEngine.changed(); VtpPersistence.markDirty();
        }
    }
    public static boolean isTradeLocked(UUID uuid) {
        State s = states.get(uuid);
        return s != null && (s.tradeLocked || s.level > 1);
    }
    public static boolean isAwaitingOffers(UUID uuid) {
        State s = states.get(uuid); return s == null || s.pending;
    }
    public static boolean isProfessionAssigned(UUID uuid) {
        State s = states.get(uuid); return s != null && s.data != null && hasProfession(s.data);
    }
    public static long observationRevision(UUID uuid) { State s = states.get(uuid); return s == null ? -1 : s.revision; }
    public static int getRerollCount(UUID uuid) { State s = states.get(uuid); return s == null ? 0 : s.rerolls; }
    public static Optional<VillagerData> getLastVillagerData() { return Optional.ofNullable(lastVillagerData); }
    public static UUID getLastVillagerUuid() { return lastVillagerUuid; }
    public static UUID getMenuVillagerUuid() { return menuUuid; }
    public static Optional<VillagerData> getData(UUID uuid) {
        State s = states.get(uuid); return Optional.ofNullable(s == null ? null : s.data);
    }
    public static OptionalInt getCurrentRound(UUID uuid) {
        State s = states.get(uuid); return s == null || s.round < 0 ? OptionalInt.empty() : OptionalInt.of(s.round);
    }
    public static int getNextRoundForLevel(Holder<VillagerProfession> profession, int level) {
        var key = TradeDataLoader.getTradeSetKey(profession, level);
        return Math.max(0, (key == null ? 0 : PredictionEngine.getOffset(key))
                + totals.getOrDefault(sequenceKey(profession, level), 0));
    }
    public static int getTradeSetTotal(VillagerData data) { return getTradeSetTotalForLevel(data.profession(), data.level()); }
    public static int getTradeSetTotalForLevel(Holder<VillagerProfession> profession, int level) {
        return totals.getOrDefault(sequenceKey(profession, level), 0);
    }
    public static int getVillagerRoundForLevel(UUID uuid, Holder<VillagerProfession> profession, int level) {
        State s = states.get(uuid);
        return s == null ? getNextRoundForLevel(profession, level)
                : s.rounds.getOrDefault(level, getNextRoundForLevel(profession, level));
    }
    private static void setNextRound(VillagerData data, int round) {
        var key = TradeDataLoader.getTradeSetKey(data.profession(), data.level());
        totals.put(sequenceKey(data.profession(), data.level()),
                round - (key == null ? 0 : PredictionEngine.getOffset(key)));
    }
    public static void adjustRerollCount(UUID uuid, VillagerData data, int delta) {
        if (Minecraft.getInstance().getSingleplayerServer() != null) return;
        update(uuid, data);
        setNextRound(data, Math.max(0, getNextRoundForLevel(data.profession(), data.level()) + delta));
        synchronizedSequences.remove(sequenceKey(data.profession(), data.level()));
        VtpPersistence.markDirty();
    }
    public static void resetAfterCalibrate(UUID uuid, VillagerData data, int round) {
        update(uuid, data); State s = states.get(uuid); s.round = round; s.pending = false;
        s.rounds.put(data.level(), round); setNextRound(data, round + 1);
        synchronizedSequences.add(sequenceKey(data.profession(), data.level()));
        PredictionEngine.clearCache(); VtpPersistence.markDirty();
    }
    public static void resetCalibration() {
        totals.clear(); synchronizedSequences.clear();
        states.values().forEach(s -> { s.round = -1; s.rounds.clear(); });
        PredictionEngine.clearAllOffsets(); VtpPersistence.markDirty();
    }
    public static Optional<List<TradeSimulator.SimulatedOffer>> getCurrentOffers(UUID uuid) {
        State s = states.get(uuid);
        if (s == null || !s.hasObservation || s.pending) return Optional.empty();
        return Optional.of(TradeSimulator.fromObserved(s.observed));
    }
    public static List<OfferData> getSavedLevelOffers(UUID uuid, int level) {
        State s = states.get(uuid); return s == null ? List.of() : s.levelOffers.getOrDefault(level, List.of());
    }
    public static boolean hasSavedLevel(UUID uuid, int level) {
        State s = states.get(uuid); return s != null && s.levelOffers.containsKey(level);
    }
    public static boolean setLevelOffer(UUID uuid, int level, OfferData offer) {
        State s = states.get(uuid); if (s == null) return false;
        s.levelOffers.put(level, List.of(offer)); VtpPersistence.markDirty(); return true;
    }
    public static boolean clearLevelOffer(UUID uuid, int level) {
        State s = states.get(uuid); if (s == null) return false;
        if (level == -1) s.levelOffers.clear(); else s.levelOffers.remove(level);
        VtpPersistence.markDirty(); return true;
    }
    private static List<OfferData> bookData(List<ObservedOffer> offers) {
        return offers.stream().flatMap(o -> o.books().stream()
                .map(b -> new OfferData(b.key(), b.level(), o.basePrice()))).toList();
    }
    public static Map<String, Integer> getTradeSetTotals() { return Map.copyOf(totals); }
    public static Map<UUID, VillagerSavedState> getVillagerSavedStates() {
        Map<UUID, VillagerSavedState> saved = new HashMap<>();
        states.forEach((uuid, s) -> saved.put(uuid, new VillagerSavedState(s.rerolls, s.round,
                Map.copyOf(s.rounds), s.level, Map.copyOf(s.levelOffers), s.pending, s.hasObservation,
                s.profession, s.observed, s.tradeLocked)));
        return saved;
    }
    public static void loadState(Map<String, Integer> savedTotals, Map<UUID, VillagerSavedState> saved) {
        clear(); totals.putAll(savedTotals);
        saved.forEach((uuid, data) -> {
            State s = new State(); s.rerolls = data.rerollCount(); s.round = data.currentRound();
            s.rounds.putAll(data.levelRounds()); s.level = data.lastKnownLevel();
            s.observedLevel = data.hasGeneratedOffers() ? data.lastKnownLevel() : -1;
            s.levelOffers.putAll(data.levelOffers()); s.pending = data.awaitingOffers();
            s.hasObservation = data.hasGeneratedOffers(); s.everObserved = s.hasObservation || data.rerollCount() > 0; s.profession = data.profession();
            s.observed = List.copyOf(data.observedOffers()); s.tradeLocked = data.tradeLocked(); states.put(uuid, s);
        });
        // Sequence positions remain estimates after reconnecting.
    }
    public static void setLastVillagerUuid(UUID uuid) { lastVillagerUuid = uuid; }
    public static void clear() {
        lastVillagerData = null; lastVillagerUuid = null; clearMerchantInteraction();
        states.clear(); totals.clear(); synchronizedSequences.clear();
    }
    private static String professionId(Holder<VillagerProfession> profession) {
        return profession.unwrapKey().map(k -> k.identifier().toString()).orElse("unknown");
    }
    private static String sequenceKey(Holder<VillagerProfession> profession, int level) {
        var key = TradeDataLoader.getTradeSetKey(profession, level);
        return key == null ? professionId(profession) + ":" + level
                : PredictionEngine.sequenceKey(key).identifier().toString();
    }
    private static boolean hasProfession(VillagerData data) {
        return !data.profession().is(VillagerProfession.NONE) && !data.profession().is(VillagerProfession.NITWIT);
    }
}
