package net.manaz.vtp;

import net.manaz.vtp.gui.VtpAccent;
import net.manaz.vtp.render.HudVisibility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.manaz.vtp.render.HudVisibility.Mode.*;

class UiCustomizationTest {
    @Test void colorInputIsExactAndRejectsPartialOrTransparentValues() {
        assertEquals(0xFFAA12EF, VtpAccent.parse("#aa12ef").orElseThrow());
        assertEquals(0xFFAA12EF, VtpAccent.parse(" AA12EF ").orElseThrow());
        assertEquals("#AA12EF", VtpAccent.hex(0xFFAA12EF));
        for (String value : new String[]{"", "#123", "#12345678", "#GGFF00", "-12345"})
            assertTrue(VtpAccent.parse(value).isEmpty(), value);
        assertTrue(VtpAccent.parse(null).isEmpty());
    }
    @Test void visualPickerPreservesRgbValuesAndDarkTextStaysReadable() {
        for (int color : new int[]{0xFF55FF55, 0xFF123456, 0xFFAA33CC, 0xFF000000, 0xFFFFFFFF}) {
            float[] hsv = VtpAccent.hsv(color);
            assertEquals(color, VtpAccent.hsv(hsv[0], hsv[1], hsv[2]));
        }
        assertEquals(VtpAccent.DEFAULT, VtpAccent.readable(VtpAccent.DEFAULT));
        assertNotEquals(0xFF000000, VtpAccent.readable(0xFF000000));
    }
    @Test void timeoutUsesElapsedTimeAndLookingRefreshesVisibility() {
        assertTrue(HudVisibility.permits(TIME, false, 9_999_999_999L, 0, 10, 16));
        assertFalse(HudVisibility.permits(TIME, false, 10_000_000_000L, 0, 10, 16));
        assertTrue(HudVisibility.permits(TIME, true, Long.MAX_VALUE, 0, 10, 16));
        assertTrue(HudVisibility.permits(TIME, false, -1, 0, 10, 16));
    }
    @Test void rangeIncludesTheBoundaryAndHidesUnloadedVillagers() {
        assertTrue(HudVisibility.permits(RANGE, false, 0, 16 * 16, 10, 16));
        assertFalse(HudVisibility.permits(RANGE, false, 0, 16 * 16 + 0.01, 10, 16));
        assertFalse(HudVisibility.permits(RANGE, true, 0, Double.POSITIVE_INFINITY, 10, 16));
        assertFalse(HudVisibility.permits(RANGE, false, 0, Double.NaN, 10, 16));
    }
    @Test void lookingAndNeverModesHaveIndependentRules() {
        assertTrue(HudVisibility.permits(LOOKING, true, 0, 0, 10, 16));
        assertFalse(HudVisibility.permits(LOOKING, false, 0, 0, 10, 16));
        assertTrue(HudVisibility.permits(NEVER, false, Long.MAX_VALUE, Double.POSITIVE_INFINITY, 10, 16));
        assertEquals(NEVER, HudVisibility.Mode.parse("old-setting"));
        assertEquals(NEVER, HudVisibility.Mode.parse(null));
    }
}