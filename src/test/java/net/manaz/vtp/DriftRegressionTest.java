package net.manaz.vtp;
import net.manaz.vtp.prediction.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DriftRegressionTest {
    @Test void switchingVillagersUsesSharedNextPosition() {
        // Selected villager's old round is 10; another consumed rounds 11..20.
        assertEquals(1, SequencePosition.remaining(21, 21, false));
        assertEquals(4, SequencePosition.remaining(24, 21, false));
        assertEquals(0, SequencePosition.remaining(21, 21, true));
    }
    @Test void duplicateBooksMustNotDisappearInMatching() {
        assertTrue(OfferMatching.same(List.of("mending", "paper"), List.of("paper", "mending")));
        assertFalse(OfferMatching.same(List.of("mending", "mending"), List.of("mending", "paper")));
        assertFalse(OfferMatching.same(List.of("mending:10"), List.of("mending:11")));
        assertFalse(OfferMatching.same(List.of(), List.of("paper")));
    }
    @Test void levelUpUsesOnlyNewlyAddedOffers() {
        assertEquals(List.of("mending", "glass"), OfferMatching.added(
                List.of("mending", "paper"), List.of("mending", "paper", "mending", "glass")));
    }
    @Test void worldIdentityIsNotJustTheSeed() {
        assertNotEquals(StateFiles.filename("server:a", 1), StateFiles.filename("server:b", 1));
        assertNotEquals(StateFiles.filename("world:a", 1), StateFiles.filename("world:b", 1));
        assertNotEquals(StateFiles.filename("world:a", 1), StateFiles.filename("world:a", 2));
        assertEquals(StateFiles.filename("world:a", 1), StateFiles.filename("world:a", 1));
    }
    @Test void atomicSaveReplacesCompleteDocument(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("state.json");
        StateFiles.writeAtomic(file, "{\"round\":1}");
        StateFiles.writeAtomic(file, "{\"round\":200}");
        assertEquals("{\"round\":200}", Files.readString(file));
        try (var files = Files.list(dir)) { assertEquals(1, files.count()); }
    }
}
