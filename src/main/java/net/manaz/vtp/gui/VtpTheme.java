package net.manaz.vtp.gui;

/**
 * Color themes shared between the /vtp settings screen and the HUD overlay.
 * Switch with VtpTheme.cycle() or VtpTheme.set(name).
 */
public enum VtpTheme {

    // ── Vanilla Plus ─────────────────────────────────────────────────────────
    VANILLA("Vanilla+",
            /* overlay    */ 0xA0000000,
            /* win        */ 0xFF1C1C1C,
            /* titlebar   */ 0xFF141414,
            /* panel      */ 0xFF222222,
            /* phdr       */ 0xFF2C2C2C,
            /* border     */ 0xFF555555,
            /* accent     */ 0xFFFFFF55,
            /* text       */ 0xFFFFFFFF,
            /* muted      */ 0xFFAAAAAA,
            /* listBg     */ 0xFF161616,
            /* listSel    */ 0xFF3A3A3A,
            /* listHov    */ 0xFF2C2C2C,
            /* div        */ 0xFF383838,
            /* lvlBg      */ 0xFF242424,
            /* lvlCur     */ 0xFF2A3A18,
            /* error      */ 0xFFFF5555,
            /* hudPanel   */ 0xC0181818,
            /* hudPhdr    */ 0xD0242424,
            /* hudMatchBg */ 0xFF243018),

    // ── Modern (dark navy / emerald) ─────────────────────────────────────────
    MODERN("Modern",
            /* overlay    */ 0xC0050A12,
            /* win        */ 0xFF0B1420,
            /* titlebar   */ 0xFF060C14,
            /* panel      */ 0xFF111D2E,
            /* phdr       */ 0xFF172438,
            /* border     */ 0xFF1C3050,
            /* accent     */ 0xFF2ED492,
            /* text       */ 0xFFCDD8E8,
            /* muted      */ 0xFF5C718A,
            /* listBg     */ 0xFF070D18,
            /* listSel    */ 0xFF0B3D28,
            /* listHov    */ 0xFF0C1E30,
            /* div        */ 0xFF162030,
            /* lvlBg      */ 0xFF0D1928,
            /* lvlCur     */ 0xFF0B3D28,
            /* error      */ 0xFFE05555,
            /* hudPanel   */ 0xD0111D2E,
            /* hudPhdr    */ 0xFF172438,
            /* hudMatchBg */ 0xFF0F3020),

    // ── Transparent ───────────────────────────────────────────────────────────
    TRANSPARENT("Transparent",
            /* overlay    */ 0x28000000,
            /* win        */ 0x88000000,
            /* titlebar   */ 0x95000000,
            /* panel      */ 0x70000000,
            /* phdr       */ 0x80000000,
            /* border     */ 0x90888888,
            /* accent     */ 0xFFFFFF55,
            /* text       */ 0xFFFFFFFF,
            /* muted      */ 0xFFDDDDDD,
            /* listBg     */ 0x60000000,
            /* listSel    */ 0x90226622,
            /* listHov    */ 0x60333333,
            /* div        */ 0x70666666,
            /* lvlBg      */ 0x60000000,
            /* lvlCur     */ 0x80225522,
            /* error      */ 0xFFFF5555,
            /* hudPanel   */ 0x00000000,
            /* hudPhdr    */ 0x00000000,
            /* hudMatchBg */ 0x00000000,
            /* hudNoBg    */ true),

    // ── Nether (dark red / ember) ─────────────────────────────────────────────
    NETHER("Nether",
            /* overlay    */ 0xB0120404,
            /* win        */ 0xFF1A0806,
            /* titlebar   */ 0xFF0E0403,
            /* panel      */ 0xFF2A100C,
            /* phdr       */ 0xFF3A1812,
            /* border     */ 0xFF5E2418,
            /* accent     */ 0xFFFFA040,
            /* text       */ 0xFFF0D8C8,
            /* muted      */ 0xFFA07868,
            /* listBg     */ 0xFF140604,
            /* listSel    */ 0xFF5A1E10,
            /* listHov    */ 0xFF2E120C,
            /* div        */ 0xFF3A1812,
            /* lvlBg      */ 0xFF220C08,
            /* lvlCur     */ 0xFF5A2814,
            /* error      */ 0xFFFF6060,
            /* hudPanel   */ 0xC01A0806,
            /* hudPhdr    */ 0xD02A100C,
            /* hudMatchBg */ 0xFF4A1E10),

    // ── Ocean (deep blue / cyan) ──────────────────────────────────────────────
    OCEAN("Ocean",
            /* overlay    */ 0xA0030A18,
            /* win        */ 0xFF0A1828,
            /* titlebar   */ 0xFF061020,
            /* panel      */ 0xFF0F2438,
            /* phdr       */ 0xFF143048,
            /* border     */ 0xFF1E4870,
            /* accent     */ 0xFF40D0E0,
            /* text       */ 0xFFD8E8F0,
            /* muted      */ 0xFF6890A8,
            /* listBg     */ 0xFF061220,
            /* listSel    */ 0xFF1A4860,
            /* listHov    */ 0xFF0E2A40,
            /* div        */ 0xFF15304A,
            /* lvlBg      */ 0xFF0C2030,
            /* lvlCur     */ 0xFF154858,
            /* error      */ 0xFFFF6060,
            /* hudPanel   */ 0xC00A1828,
            /* hudPhdr    */ 0xD00F2438,
            /* hudMatchBg */ 0xFF164858),

    // ── Sunset (purple / pink / orange) ───────────────────────────────────────
    SUNSET("Sunset",
            /* overlay    */ 0xA0100818,
            /* win        */ 0xFF1E1028,
            /* titlebar   */ 0xFF140820,
            /* panel      */ 0xFF2C1A3C,
            /* phdr       */ 0xFF3A2048,
            /* border     */ 0xFF5A3068,
            /* accent     */ 0xFFFF80B0,
            /* text       */ 0xFFF0E0E8,
            /* muted      */ 0xFFB088A0,
            /* listBg     */ 0xFF180A20,
            /* listSel    */ 0xFF5A2858,
            /* listHov    */ 0xFF2C1438,
            /* div        */ 0xFF3A2048,
            /* lvlBg      */ 0xFF22142E,
            /* lvlCur     */ 0xFF502048,
            /* error      */ 0xFFFF6060,
            /* hudPanel   */ 0xC01E1028,
            /* hudPhdr    */ 0xD02C1A3C,
            /* hudMatchBg */ 0xFF4A2048),

    // ── Forest (green / earthy) ───────────────────────────────────────────────
    FOREST("Forest",
            /* overlay    */ 0xA0050A04,
            /* win        */ 0xFF0E1810,
            /* titlebar   */ 0xFF081008,
            /* panel      */ 0xFF162418,
            /* phdr       */ 0xFF1C2E1E,
            /* border     */ 0xFF2E4E30,
            /* accent     */ 0xFF88D060,
            /* text       */ 0xFFE0E8D8,
            /* muted      */ 0xFF889078,
            /* listBg     */ 0xFF0A120A,
            /* listSel    */ 0xFF284828,
            /* listHov    */ 0xFF142814,
            /* div        */ 0xFF203020,
            /* lvlBg      */ 0xFF122010,
            /* lvlCur     */ 0xFF224820,
            /* error      */ 0xFFFF6060,
            /* hudPanel   */ 0xC00E1810,
            /* hudPhdr    */ 0xD0162418,
            /* hudMatchBg */ 0xFF244820),

    // ── AMOLED (pure black) ───────────────────────────────────────────────────
    AMOLED("AMOLED",
            /* overlay    */ 0xC0000000,
            /* win        */ 0xFF000000,
            /* titlebar   */ 0xFF000000,
            /* panel      */ 0xFF080808,
            /* phdr       */ 0xFF121212,
            /* border     */ 0xFF2A2A2A,
            /* accent     */ 0xFF00E0E0,
            /* text       */ 0xFFE8E8E8,
            /* muted      */ 0xFF808080,
            /* listBg     */ 0xFF000000,
            /* listSel    */ 0xFF202020,
            /* listHov    */ 0xFF101010,
            /* div        */ 0xFF1A1A1A,
            /* lvlBg      */ 0xFF080808,
            /* lvlCur     */ 0xFF1A2A1A,
            /* error      */ 0xFFFF5555,
            /* hudPanel   */ 0xC0000000,
            /* hudPhdr    */ 0xD0080808,
            /* hudMatchBg */ 0xFF1A2A1A),

    // ── Monochrome (grayscale minimalist) ─────────────────────────────────────
    MONOCHROME("Monochrome",
            /* overlay    */ 0xA0000000,
            /* win        */ 0xFFE8E8E8,
            /* titlebar   */ 0xFFD8D8D8,
            /* panel      */ 0xFFF2F2F2,
            /* phdr       */ 0xFFDCDCDC,
            /* border     */ 0xFF888888,
            /* accent     */ 0xFF202020,
            /* text       */ 0xFF101010,
            /* muted      */ 0xFF606060,
            /* listBg     */ 0xFFFBFBFB,
            /* listSel    */ 0xFFBEBEBE,
            /* listHov    */ 0xFFE0E0E0,
            /* div        */ 0xFFB8B8B8,
            /* lvlBg      */ 0xFFEAEAEA,
            /* lvlCur     */ 0xFFC8C8C8,
            /* error      */ 0xFFB02020,
            /* hudPanel   */ 0xC0F0F0F0,
            /* hudPhdr    */ 0xD0DCDCDC,
            /* hudMatchBg */ 0xFFBEBEBE);

    // ── Fields ────────────────────────────────────────────────────────────────
    public final String displayName;

    // GUI colors
    public final int overlay, win, titlebar, panel, phdr, border;
    public final int accent, text, muted;
    public final int listBg, listSel, listHov, div;
    public final int lvlBg, lvlCur, error;

    // HUD-specific (semi-transparent variants)
    public final int hudPanel, hudPhdr, hudMatchBg;

    /** When true, HUD panels render with no background fill or border. */
    public final boolean hudNoBackground;

    VtpTheme(String displayName,
             int overlay, int win, int titlebar, int panel, int phdr, int border,
             int accent, int text, int muted,
             int listBg, int listSel, int listHov, int div,
             int lvlBg, int lvlCur, int error,
             int hudPanel, int hudPhdr, int hudMatchBg) {
        this(displayName, overlay, win, titlebar, panel, phdr, border,
             accent, text, muted, listBg, listSel, listHov, div,
             lvlBg, lvlCur, error, hudPanel, hudPhdr, hudMatchBg, false);
    }

    VtpTheme(String displayName,
             int overlay, int win, int titlebar, int panel, int phdr, int border,
             int accent, int text, int muted,
             int listBg, int listSel, int listHov, int div,
             int lvlBg, int lvlCur, int error,
             int hudPanel, int hudPhdr, int hudMatchBg,
             boolean hudNoBackground) {
        this.displayName = displayName;
        this.overlay = overlay; this.win = win; this.titlebar = titlebar;
        this.panel = panel; this.phdr = phdr; this.border = border;
        this.accent = accent; this.text = text; this.muted = muted;
        this.listBg = listBg; this.listSel = listSel; this.listHov = listHov;
        this.div = div; this.lvlBg = lvlBg; this.lvlCur = lvlCur;
        this.error = error;
        this.hudPanel = hudPanel; this.hudPhdr = hudPhdr; this.hudMatchBg = hudMatchBg;
        this.hudNoBackground = hudNoBackground;
    }

    // ── Static API ────────────────────────────────────────────────────────────
    private static VtpTheme current = VANILLA;

    public static VtpTheme current() { return current; }

    public static void set(VtpTheme t) { current = t; }

    /** Advance to the next theme, wrapping around. */
    public static VtpTheme cycle() {
        current = values()[(current.ordinal() + 1) % values().length];
        return current;
    }

    /** Look up by enum name, returning VANILLA as fallback. */
    public static VtpTheme fromName(String name) {
        for (VtpTheme t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return VANILLA;
    }
}
