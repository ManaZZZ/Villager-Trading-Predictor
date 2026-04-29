package net.manaz.vtp.render;

import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeDataLoader;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Tracks villager state per UUID to detect rerolls.
 *
 * A "reroll" is detected when a villager's profession transitions:
 *   non-NONE → NONE (workstation broken) → non-NONE (workstation placed)
 *
 * Each full cycle increments the reroll counter for that villager.
 *
 * VillagerMixin calls update() for ALL villagers whenever their data changes,
 * so tracking is not limited to the crosshair entity.
 *
 * The tick-based grace window prevents false positives from entity
 * initialization (NONE default → actual profession, which happens within
 * 1-2 ticks when a chunk loads).
 */
public class VillagerTracker {
    private static VillagerData lastVillagerData = null;
    private static UUID lastVillagerUuid = null;

    private static final long INIT_GRACE_TICKS = 3;

    private static final Map<UUID, VillagerState> villagerStates = new HashMap<>();

    // Total profession assignments per trade set (keyed by "profession_id:level"),
    // across ALL villagers of that type. Used to anchor the upcoming rerolls panel
    // to the correct position in the shared RNG sequence.
    private static final Map<String, Integer> tradeSetTotals = new HashMap<>();

    /** Serializable form of one enchanted-book trade offer. */
    public record OfferData(String enchantmentKey, int enchantmentLevel, int additionalCost) {}

    private static class VillagerState {
        boolean hadProfession;
        long firstSeenTick;
        int rerollCount;
        // Absolute sequence round this villager's current offers come from.
        // -1 until their first observed assignment.
        int currentRound;
        // The villager level at the time of the last recorded assignment.
        // -1 = not yet initialized; 0 = loaded from save (re-initialize on next sight).
        int lastKnownLevel;
        // Snapshot of the villager's offers at the time of its last detected reroll.
        // Null until we observe the first assignment for this villager.
        List<TradeSimulator.SimulatedOffer> currentOffers;
        // Per-level round snapshot: level → absolute sequence round.
        final Map<Integer, Integer> levelRounds = new HashMap<>();
        // Per-level saved offers: level → list of offer data (persisted across relogs).
        final Map<Integer, List<OfferData>> savedLevelOffers = new HashMap<>();

        VillagerState(boolean hadProfession, long tick) {
            this.hadProfession = hadProfession;
            this.firstSeenTick = tick;
            this.rerollCount = 0;
            this.currentRound = -1;
            this.lastKnownLevel = -1;
            this.currentOffers = null;
        }
    }

    /** Saved snapshot for a single villager (used by persistence layer). */
    public record VillagerSavedState(int rerollCount, int currentRound,
                                     Map<Integer, Integer> levelRounds, int lastKnownLevel,
                                     Map<Integer, List<OfferData>> levelOffers) {}

    /**
     * Update tracking for a villager.
     * Safe to call from both the mixin (all villagers) and the HUD renderer (crosshair villager).
     */
    /**
     * Update tracking for a villager and mark it as the last-viewed villager.
     * Called from the HUD renderer for the crosshair entity.
     */
    public static void updateAndSelect(UUID uuid, VillagerData data) {
        lastVillagerData = data;
        lastVillagerUuid = uuid;
        update(uuid, data);
    }

    /**
     * Update tracking for a villager without changing the last-viewed villager
     * (unless it matches the currently selected UUID).
     * Called from the mixin for ALL villagers.
     */
    public static void update(UUID uuid, VillagerData data) {
        if (uuid.equals(lastVillagerUuid)) {
            lastVillagerData = data;
        }

        long currentTick = getCurrentTick();
        boolean hasProfession = hasProfession(data);
        VillagerState state = villagerStates.get(uuid);

        if (state == null) {
            VillagerState newState = new VillagerState(hasProfession, currentTick);
            if (hasProfession) {
                recordTradeAssignment(newState, data);
            }
            villagerStates.put(uuid, newState);
            return;
        }

        if (state.hadProfession && !hasProfession) {
            state.hadProfession = false;
        } else if (!state.hadProfession && hasProfession) {
            if (currentTick - state.firstSeenTick >= INIT_GRACE_TICKS) {
                state.rerollCount++;
                recordTradeAssignment(state, data);
            } else if (state.currentRound < 0) {
                // Within grace period but no round has ever been recorded for this villager
                // (fresh initial profession assignment, not a reroll). Record it without
                // counting as a reroll so levelRounds is populated for the level preview.
                recordTradeAssignment(state, data);
            }
            state.hadProfession = true;
        } else if (hasProfession) {
            if (state.lastKnownLevel == -1) {
                // First re-sight after session load — initialize level tracking without
                // advancing the sequence counter (the assignment was already counted on save).
                state.lastKnownLevel = data.level();
            } else if (data.level() != state.lastKnownLevel) {
                // Level-up detected — record the assignment for the new level so the
                // per-villager snapshot for this level is updated.
                recordTradeAssignment(state, data);
            }
        }
    }

    // Record a trade assignment (new placement or reroll) against the shared
    // sequence counter. The very first villager seen for a trade set does NOT
    // advance the counter — it's treated as the calibration baseline at round 0
    // relative to the per-type offset.
    private static void recordTradeAssignment(VillagerState state, VillagerData data) {
        String tsKey = tradeSetKey(data);
        int total;
        if (tradeSetTotals.containsKey(tsKey)) {
            total = tradeSetTotals.merge(tsKey, 1, Integer::sum);
        } else {
            tradeSetTotals.put(tsKey, 0);
            total = 0;
        }
        int offset = getTypeOffset(data);
        state.currentRound = offset + total;
        state.lastKnownLevel = data.level();
        state.currentOffers = simulateOffersAt(data, state.currentRound);
        // Offers are saved lazily from the render path (where trade data is guaranteed loaded),
        // not here — the mixin may fire before the client registry is populated.

        // Snapshot the current global sequence position for every level of this profession.
        // This binds each level's prediction to this specific villager at the moment it was seen.
        snapshotLevelRounds(state, data);
    }

    /**
     * Records this villager's actual sequence round for their current level in levelRounds.
     * Non-current levels are intentionally NOT stored here — they are served live by
     * getVillagerRoundForLevel so they always reflect the latest global state.
     */
    private static void snapshotLevelRounds(VillagerState state, VillagerData data) {
        state.levelRounds.put(data.level(), state.currentRound);
    }

    /**
     * Returns the sequence round to use for a specific level when rendering this villager's
     * level preview.
     *
     * Observed levels (stored in levelRounds) → villager's actual recorded round (per-villager, stable).
     * Unobserved future levels → the next available round from the global counter (live, shared
     *                            across all villagers of this profession). This correctly advances
     *                            each time any villager of that profession levels up to that tier.
     */
    public static int getVillagerRoundForLevel(UUID uuid,
            net.minecraft.core.Holder<VillagerProfession> profession, int level) {
        VillagerState state = villagerStates.get(uuid);
        // For any level this villager has already been observed at, return the stored round.
        if (state != null) {
            Integer savedRound = state.levelRounds.get(level);
            if (savedRound != null) {
                return savedRound;
            }
        }
        return getNextRoundForLevel(profession, level);
    }

    /**
     * Returns the next-available round for a profession+level — the round the next placed/rerolled
     * villager of that profession+level would receive. Identical regardless of which villager
     * the caller has selected; used by the HUD's level-preview panel so all villagers of the
     * same profession show the same forecast.
     */
    public static int getNextRoundForLevel(net.minecraft.core.Holder<VillagerProfession> profession,
                                           int level) {
        ResourceKey<net.minecraft.world.item.trading.TradeSet> rk =
                TradeDataLoader.getTradeSetKey(profession, level);
        int off = rk != null ? PredictionEngine.getOffset(rk) : 0;
        String key = profession.unwrapKey()
                .map(k -> k.identifier().toString()).orElse("unknown") + ":" + level;
        int lastRound = tradeSetTotals.getOrDefault(key, -1);
        return off + (lastRound + 1);
    }

    private static int getTypeOffset(VillagerData data) {
        ResourceKey<TradeSet> rk = TradeDataLoader.getTradeSetKey(data.profession(), data.level());
        return rk != null ? PredictionEngine.getOffset(rk) : 0;
    }

    private static long getCurrentTick() {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level != null) ? mc.level.getGameTime() : 0L;
    }

    /**
     * Get the number of rerolls observed for a villager.
     */
    public static int getRerollCount(UUID uuid) {
        VillagerState state = villagerStates.get(uuid);
        return state != null ? state.rerollCount : 0;
    }

    /**
     * Manually adjust the reroll count for a villager by delta (+1 or -1).
     * Also shifts the shared trade-set counter and recomputes the cached
     * currentRound/currentOffers so the HUD's upcoming list moves in step.
     * Clamps the absolute sequence round at 0; the relative counter may go
     * negative, which lets the user scroll back past a calibration point.
     * Used by hotkeys to correct tracking drift.
     */
    public static void adjustRerollCount(UUID uuid, VillagerData data, int delta) {
        if (uuid == null || data == null) return;
        VillagerState state = villagerStates.get(uuid);
        if (state == null) {
            state = new VillagerState(true, getCurrentTick());
            villagerStates.put(uuid, state);
        }

        String tsKey = tradeSetKey(data);
        int offset = getTypeOffset(data);
        int currentTotal = tradeSetTotals.getOrDefault(tsKey, 0);
        int currentAbsolute = offset + currentTotal;
        int newAbsolute = Math.max(0, currentAbsolute + delta);
        int appliedDelta = newAbsolute - currentAbsolute;
        if (appliedDelta == 0) return;

        state.rerollCount = Math.max(0, state.rerollCount + appliedDelta);
        tradeSetTotals.put(tsKey, currentTotal + appliedDelta);

        state.currentRound = newAbsolute;
        state.levelRounds.put(data.level(), newAbsolute);
        state.currentOffers = simulateOffersAt(data, newAbsolute);
        PredictionEngine.clearCache();
    }

    /**
     * Called after a successful /vtp calibrate. Resets the shared assignment counter
     * for this trade set to 0 and updates the calibrated villager's recorded round,
     * so predictions start cleanly from the calibrated position.
     */
    public static void resetAfterCalibrate(UUID uuid, VillagerData data, int calibratedRound) {
        String tsKey = tradeSetKey(data);
        tradeSetTotals.put(tsKey, 0);
        // Update the calibrated villager's state
        VillagerState state = villagerStates.get(uuid);
        if (state != null) {
            state.currentRound = calibratedRound;
            state.levelRounds.put(data.level(), calibratedRound);
            state.currentOffers = simulateOffersAt(data, calibratedRound);
        }
        PredictionEngine.clearCache();
    }

    public static Optional<VillagerData> getLastVillagerData() {
        return Optional.ofNullable(lastVillagerData);
    }

    public static UUID getLastVillagerUuid() {
        return lastVillagerUuid;
    }

    /**
     * Total rerolls across all villagers of the same profession+level.
     * Use this as the start of the shared "next rerolls" sequence.
     */
    public static int getTradeSetTotal(VillagerData data) {
        return tradeSetTotals.getOrDefault(tradeSetKey(data), 0);
    }

    /**
     * Total assignments for a specific profession + level combination.
     * Use when querying levels other than the one carried by an in-hand VillagerData.
     */
    public static int getTradeSetTotalForLevel(net.minecraft.core.Holder<VillagerProfession> profession, int level) {
        String key = profession.unwrapKey()
                .map(k -> k.identifier().toString())
                .orElse("unknown") + ":" + level;
        return tradeSetTotals.getOrDefault(key, 0);
    }

    /**
     * Offers captured the last time this villager was observed being assigned trades.
     * Empty if we have not yet seen a trade assignment for this villager.
     */
    public static Optional<List<TradeSimulator.SimulatedOffer>> getCurrentOffers(UUID uuid) {
        VillagerState state = villagerStates.get(uuid);
        if (state == null || state.currentOffers == null) return Optional.empty();
        return Optional.of(state.currentOffers);
    }

    /**
     * The absolute sequence round this villager's current offers come from.
     * Empty if no assignment has been recorded for this villager.
     */
    public static OptionalInt getCurrentRound(UUID uuid) {
        VillagerState state = villagerStates.get(uuid);
        if (state == null || state.currentRound < 0) return OptionalInt.empty();
        return OptionalInt.of(state.currentRound);
    }

    private static List<TradeSimulator.SimulatedOffer> simulateOffersAt(VillagerData data, int absoluteRound) {
        OptionalLong seedOpt = SeedProvider.getSeed();
        if (seedOpt.isEmpty()) return null;
        return PredictionEngine.simulateRound(
                seedOpt.getAsLong(), data.profession(), data.level(), absoluteRound);
    }

    /** Returns a snapshot of the global trade-set assignment counters for persistence. */
    public static Map<String, Integer> getTradeSetTotals() {
        return new HashMap<>(tradeSetTotals);
    }

    /**
     * Saved enchantment offers for a specific villager at a specific level.
     * Returns empty if no data was recorded (e.g. villager not yet observed at that level).
     */
    public static List<OfferData> getSavedLevelOffers(UUID uuid, int level) {
        VillagerState state = villagerStates.get(uuid);
        if (state == null) return List.of();
        // Saved entries for levels this villager hasn't reached are stale predictions
        // against the shared sequence counter — ignore them so callers fall back to live simulation.
        if (state.lastKnownLevel > 0 && level > state.lastKnownLevel) return List.of();
        return state.savedLevelOffers.getOrDefault(level, List.of());
    }

    /**
     * Persist computed offers for a villager level. Called from the render path (after trade
     * data is loaded) so the JSON save always has real enchantment data rather than empty lists.
     * Only stores non-empty results; does not overwrite an existing non-empty entry.
     */
    public static void saveLevelOffers(UUID uuid, int level, List<TradeSimulator.SimulatedOffer> offers) {
        VillagerState state = villagerStates.get(uuid);
        if (state == null) return;
        if (state.savedLevelOffers.containsKey(level) && !state.savedLevelOffers.get(level).isEmpty()) return;
        List<OfferData> data = toOfferData(offers);
        if (!data.isEmpty()) {
            state.savedLevelOffers.put(level, data);
        }
    }

    /**
     * Manually override the saved offer for a specific villager level.
     * Used by /vtp configure to correct wrong predictions.
     */
    public static boolean setLevelOffer(UUID uuid, int villagerLevel, OfferData offer) {
        VillagerState state = villagerStates.get(uuid);
        if (state == null) return false;
        state.savedLevelOffers.put(villagerLevel, List.of(offer));
        return true;
    }

    /**
     * Clear the saved offer for a specific villager level so the simulation
     * fallback is used again. Pass level=-1 to clear all levels.
     */
    public static boolean clearLevelOffer(UUID uuid, int villagerLevel) {
        VillagerState state = villagerStates.get(uuid);
        if (state == null) return false;
        if (villagerLevel == -1) {
            state.savedLevelOffers.clear();
        } else {
            state.savedLevelOffers.remove(villagerLevel);
        }
        return true;
    }

    /** Convert SimulatedOffers to serializable OfferData (enchantment offers only). */
    private static List<OfferData> toOfferData(List<TradeSimulator.SimulatedOffer> offers) {
        return offers.stream()
                .filter(o -> o.enchantment().isPresent())
                .map(o -> {
                    TradeSimulator.EnchantmentResult e = o.enchantment().get();
                    String key = e.enchantment().unwrapKey()
                            .map(k -> k.identifier().toString()).orElse("unknown");
                    return new OfferData(key, e.level(), e.additionalCost());
                })
                .toList();
    }

    /**
     * Returns all tracked villager states for persistence.
     * Includes ALL seen villagers (not just those with rerolls) so that re-sighting
     * on reload does not incorrectly advance the sequence counter.
     */
    public static Map<UUID, VillagerSavedState> getVillagerSavedStates() {
        Map<UUID, VillagerSavedState> result = new HashMap<>();
        villagerStates.forEach((uuid, state) -> {
            Map<Integer, List<OfferData>> offersToSave = new HashMap<>();
            state.savedLevelOffers.forEach((lvl, offers) -> {
                if (!offers.isEmpty()) offersToSave.put(lvl, offers);
            });
            result.put(uuid, new VillagerSavedState(
                    state.rerollCount, state.currentRound,
                    new HashMap<>(state.levelRounds), state.lastKnownLevel,
                    offersToSave));
        });
        return result;
    }

    /**
     * Restores saved state on world load.
     * Sets hadProfession=true for all restored villagers so the first re-sight does
     * not count as a new reroll, and restores currentRound for correct "now" offers.
     */
    public static void loadState(Map<String, Integer> savedTotals,
                                 Map<UUID, VillagerSavedState> savedStates) {
        clear();
        tradeSetTotals.putAll(savedTotals);
        for (Map.Entry<UUID, VillagerSavedState> e : savedStates.entrySet()) {
            VillagerState state = new VillagerState(true, 0L);
            state.rerollCount = e.getValue().rerollCount();
            state.currentRound = e.getValue().currentRound();
            state.levelRounds.putAll(e.getValue().levelRounds());
            state.lastKnownLevel = e.getValue().lastKnownLevel();
            state.savedLevelOffers.putAll(e.getValue().levelOffers());
            // currentOffers stays null; it is recomputed lazily in VillagerHudRenderer
            // using the restored currentRound, or served from savedLevelOffers.
            villagerStates.put(e.getKey(), state);
        }
    }

    /**
     * Restores the last-viewed villager UUID from persistence.
     * VillagerData will be populated when the entity is next seen.
     */
    public static void setLastVillagerUuid(UUID uuid) {
        lastVillagerUuid = uuid;
    }

    public static void clear() {
        lastVillagerData = null;
        lastVillagerUuid = null;
        villagerStates.clear();
        tradeSetTotals.clear();
    }

    private static String tradeSetKey(VillagerData data) {
        return data.profession().unwrapKey()
                .map(k -> k.identifier().toString())
                .orElse("unknown") + ":" + data.level();
    }

    private static boolean hasProfession(VillagerData data) {
        Optional<ResourceKey<VillagerProfession>> key = data.profession().unwrapKey();
        if (key.isEmpty()) return false;
        return !key.get().equals(VillagerProfession.NONE)
                && !key.get().equals(VillagerProfession.NITWIT);
    }
}
