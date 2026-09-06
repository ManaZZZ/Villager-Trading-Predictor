package net.manaz.vtp;

import net.manaz.vtp.render.HudFade;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudFadeTest {
    @Test void fadesToZeroAndRemainsHidden() {
        var fade = new HudFade();
        assertEquals(1, fade.advance(true, HudFade.IN_SECONDS));
        assertEquals(0.5, fade.advance(false, HudFade.OUT_SECONDS / 2), 0.0001);
        assertEquals(0, fade.advance(false, HudFade.OUT_SECONDS / 2));
        assertEquals(0, fade.advance(false, 30));
    }
    @Test void reversesFromItsCurrentOpacityWithoutJumping() {
        var fade = new HudFade();
        fade.advance(true, 1);
        float fadingOut = fade.advance(false, 0.1);
        assertEquals(fadingOut, fade.advance(true, 0));
        float returning = fade.advance(true, 0.02);
        assertTrue(returning > fadingOut && returning < 1);
        fade.reset();
        assertEquals(0, fade.opacity());
    }
    @Test void timingIsIndependentOfFrameRate() {
        var slow = new HudFade(); var fast = new HudFade();
        slow.advance(true, 1); fast.advance(true, 1);
        for (int i = 0; i < 3; i++) slow.advance(false, 1.0 / 30);
        for (int i = 0; i < 24; i++) fast.advance(false, 1.0 / 240);
        assertEquals(slow.opacity(), fast.opacity(), 0.00001);
    }
    @Test void premultipliedIconTintFadesRgbAndAlphaTogether() {
        assertEquals(0x80808080, HudFade.itemTint(0xFFFFFFFF, 0.5f));
        assertEquals(0, HudFade.itemTint(0xFFFFFFFF, 0));
        assertEquals(0x80402010, HudFade.itemTint(0x80402010, 1));
    }
}