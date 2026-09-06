package net.manaz.vtp;

import net.manaz.vtp.render.HudSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudSizeTest {
    @AfterEach void restore() { HudSize.set(HudSize.DEFAULT); }

    @Test void manualSizeScalesEverythingWithoutChangingPanelProportions() {
        HudSize.set(75);
        var small = HudSize.layout(1280, 720, 90);
        HudSize.set(150);
        var large = HudSize.layout(1280, 720, 90);
        assertEquals(212, small.panelWidth());
        assertEquals(small.panelWidth(), large.panelWidth());
        assertEquals(2 * small.scale(), large.scale());
    }

    @Test void panelsStaySeparatedOnscreenAndAboveTheHotbar() {
        for (int[] screen : new int[][]{{320, 240}, {427, 240}, {640, 360}, {1280, 720}}) {
            for (int size = HudSize.MIN; size <= HudSize.MAX; size++) {
                HudSize.set(size);
                var p = HudSize.layout(screen[0], screen[1], 90);
                assertTrue(p.panelWidth() >= 120);
                assertTrue(p.left() * p.scale() >= 5.99);
                assertTrue((p.left() + p.panelWidth()) * p.scale() + 4 <= p.right() * p.scale());
                assertTrue((p.right() + p.panelWidth()) * p.scale() <= screen[0] - 5.99);
                assertTrue(p.top() * p.scale() >= 5.99);
                assertTrue(p.top() + 90 <= p.bottom());
                assertTrue(p.bottom() * p.scale() <= screen[1] - 47.99);
            }
        }
    }

    @Test void outOfRangePreferencesAreClamped() {
        HudSize.set(Integer.MIN_VALUE); assertEquals(50, HudSize.percent());
        HudSize.set(Integer.MAX_VALUE); assertEquals(200, HudSize.percent());
    }
}