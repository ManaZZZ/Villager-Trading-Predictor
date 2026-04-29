package net.manaz.vtp.prediction;

import net.manaz.vtp.trade.TradeSimulator;

import java.util.List;
import java.util.Optional;

public record PredictionResult(
        /** The reroll index where the target was found (0 = first assignment), empty if not found */
        Optional<Integer> rerollsNeeded,
        /** Maximum rerolls that were searched */
        int maxRerollsSearched,
        /** The trades generated at the target reroll (if found) */
        List<TradeSimulator.SimulatedOffer> targetRollOffers,
        /** The trades generated at reroll 0 (the first assignment) */
        List<TradeSimulator.SimulatedOffer> firstRollOffers
) {
    public boolean found() {
        return rerollsNeeded.isPresent();
    }
}
