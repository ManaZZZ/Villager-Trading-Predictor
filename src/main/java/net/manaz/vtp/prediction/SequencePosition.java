package net.manaz.vtp.prediction;

/** Arithmetic shared by all forecast views. Existing offers do not reserve future rounds. */
public final class SequencePosition {
    private SequencePosition() {}

    public static int remaining(int targetRound, int nextRound, boolean offersPending) {
        if (targetRound < nextRound || nextRound < 0) throw new IllegalArgumentException("Target must be in the unconsumed future");
        return targetRound - nextRound + (offersPending ? 0 : 1);
    }
}
