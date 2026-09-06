package net.manaz.vtp.prediction;

import net.manaz.vtp.trade.TradeSimulator;

import java.util.List;
import java.util.Optional;

public record PredictionResult(
        /** Relative rerolls from the selected villager to the target; 0 means current/pending offer */
        Optional<Integer> rerollsNeeded,
        /** Maximum rerolls that were searched */
        int maxRerollsSearched,
        /** The trades generated at the target reroll (if found) */
        List<TradeSimulator.SimulatedOffer> targetRollOffers,
        /** The villager's observed current offers */
        List<TradeSimulator.SimulatedOffer> firstRollOffers
) {
    public boolean found() {
        return rerollsNeeded.isPresent();
    }
}
