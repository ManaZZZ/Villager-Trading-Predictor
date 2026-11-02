package net.manaz.vtp.prediction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalibrationDraftStoreTest {
    @AfterEach void reset() { CalibrationDraftStore.clear(); }

    @Test void incompleteInputSurvivesAQuickPriceCheckAndExpiresAfterAMinute() {
        var draft = new CalibrationDraftStore.Draft("minecraft:efficiency", 4, "1x");
        CalibrationDraftStore.save("session:seed:villager:tier", draft, 100);
        assertEquals(draft, CalibrationDraftStore.get("session:seed:villager:tier", 100 + CalibrationDraftStore.LIFETIME_NANOS - 1).orElseThrow());
        assertTrue(CalibrationDraftStore.get("session:seed:villager:tier", 100 + CalibrationDraftStore.LIFETIME_NANOS).isEmpty());
    }

    @Test void anotherVillagerSeedOrSessionCannotInheritAnUnfinishedObservation() {
        var draft = new CalibrationDraftStore.Draft("minecraft:mending", 1, "12");
        CalibrationDraftStore.save("a", draft, 100);
        assertTrue(CalibrationDraftStore.get("b", 101).isEmpty());
        assertTrue(CalibrationDraftStore.get("a", 102).isEmpty());
    }

    @Test void leavingTheFormAgainRestartsTheGracePeriod() {
        var first = new CalibrationDraftStore.Draft("minecraft:efficiency", 3, "");
        var updated = new CalibrationDraftStore.Draft("minecraft:efficiency", 4, "17");
        CalibrationDraftStore.save("a", first, 0);
        CalibrationDraftStore.save("a", updated, CalibrationDraftStore.LIFETIME_NANOS / 2);
        assertEquals(updated, CalibrationDraftStore.get("a", CalibrationDraftStore.LIFETIME_NANOS).orElseThrow());
        CalibrationDraftStore.clear();
        assertTrue(CalibrationDraftStore.get("a", CalibrationDraftStore.LIFETIME_NANOS).isEmpty());
    }
}
