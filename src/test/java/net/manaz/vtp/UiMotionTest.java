package net.manaz.vtp;

import net.manaz.vtp.gui.VtpMotion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiMotionTest {
    @Test void hoverTimingIsIndependentOfFrameRate() {
        float slow = 0, fast = 0;
        for (int i = 0; i < 6; i++) slow = VtpMotion.approach(slow, 1, 1.0 / 30, 0.045);
        for (int i = 0; i < 48; i++) fast = VtpMotion.approach(fast, 1, 1.0 / 240, 0.045);
        assertEquals(slow, fast, 0.00001);
        assertTrue(slow > 0.98 && slow <= 1);
        assertEquals(slow, VtpMotion.approach(slow, 1, 0, 0.045));
    }
}