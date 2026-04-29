package net.manaz.vtp.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.autoreroll.AutoRerollManager;
import net.manaz.vtp.command.VtpKeybinds;
import net.manaz.vtp.mixin.accessor.VillagerTradeAccessor;
import net.manaz.vtp.prediction.CalibrationListStore;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.prediction.PredictionResult;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeDataLoader;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class VtpSettingsScreen extends Screen {

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private static final String[] TAB_LABELS    = {"Settings", "Sequence", "Auto Reroll", "Trade List", "Calibrate"};
    private static final int TAB_SETTINGS       = 0;
    private static final int TAB_SEQUENCE       = 1;
    private static final int TAB_AUTO_REROLL    = 2;
    private static final int TAB_TRADE_LIST     = 3;
    private static final int TAB_CALIBRATE      = 4;

    private int   activeTab = TAB_SETTINGS;
    private int[] tabX;
    private int[] tabW;

    // ── Window layout ─────────────────────────────────────────────────────────
    private static final int WIN_W      = 444;
    private static final int WIN_H      = 290;
    private static final int TITLE_H    = 22;
    private static final int TAB_H      = 16;
    private static final int PAD        = 8;
    private static final int LP_W       = 196;
    private static final int RP_W       = 224;
    private static final int P_H        = WIN_H - TITLE_H - TAB_H - 26;
    private static final int PHDR_H     = 14;
    private static final int ITEM_H     = 13;
    private static final int LIST_ROWS  = 6;
    private static final int ROW_H      = 18;
    private static final int LVL_H      = 12;
    private static final int TGT_ROWS   = 3;
    private static final int TGT_H      = 13;

    // ── Trade list layout constants ───────────────────────────────────────────
    private static final int TL_ROW_H        = 20;  // height of each trade row
    private static final int TL_FILTER_H     = 16;  // height of level filter bar
    private static final int TL_PROF_H       = 15;  // height of each profession row in the trade list
    private static final String[] LEVEL_NAMES = {"", "Novice", "Apprentice", "Journeyman", "Expert", "Master"};
    private static final String[] FILTER_LABELS = {"All", "1", "2", "3", "4", "5"};

    // ── Cached layout positions ───────────────────────────────────────────────
    private int wx, wy;
    private int lCX, lCY;
    private int rCX, rCY;
    private int listX, listY, listW, listH;
    private int levelRowY, priceRowY, setTargetY;
    private int lvlPreviewY;
    private int tgtDividerY, tgtListY;
    private int tgtScrollOffset = 0;

    // ── Settings state ────────────────────────────────────────────────────────
    private boolean overlayEnabled;
    private EditBox maxRerollsBox;
    private Button  toggleBtn;
    private Button  themeBtn;

    // ── Enchantment list state ────────────────────────────────────────────────
    private List<Holder<Enchantment>> allEnchants      = new ArrayList<>();
    private List<Holder<Enchantment>> filteredEnchants = new ArrayList<>();
    private int     selectedIdx   = -1;
    private int     scrollOffset  = 0;
    private int     selectedLevel = 1;
    private EditBox searchBox;
    private EditBox maxPriceBox;
    private final Button[] levelBtns = new Button[5];

    // ── Level preview state ───────────────────────────────────────────────────
    private record LvlLine(String text, boolean current) {}
    private List<LvlLine> lvlLines    = new ArrayList<>();
    private String        lvlProfName = null;

    // ── Trade list state ──────────────────────────────────────────────────────
    /** One entry in the trade display list — either a level header or a trade row. */
    private record DisplayEntry(boolean isHeader, String headerText, TradeRow trade) {
        static DisplayEntry ofHeader(String text) { return new DisplayEntry(true, text, null); }
        static DisplayEntry ofTrade(TradeRow r)   { return new DisplayEntry(false, null, r); }
    }
    /** Resolved trade data ready for rendering. */
    private record TradeRow(ItemStack cost1, ItemStack cost2, ItemStack result, boolean isEnchBook) {}

    private static final String[] VANILLA_PROFESSION_IDS = {
        "armorer", "butcher", "cartographer", "cleric", "farmer",
        "fisherman", "fletcher", "leatherworker", "librarian", "mason",
        "shepherd", "toolsmith", "weaponsmith"
    };
    /** Workstation block for each profession, same order as VANILLA_PROFESSION_IDS. */
    private static final ItemStack[] WORKSTATION_STACKS = {
        new ItemStack(Items.BLAST_FURNACE), new ItemStack(Items.SMOKER),
        new ItemStack(Items.CARTOGRAPHY_TABLE), new ItemStack(Items.BREWING_STAND),
        new ItemStack(Items.COMPOSTER), new ItemStack(Items.BARREL),
        new ItemStack(Items.FLETCHING_TABLE), new ItemStack(Items.CAULDRON),
        new ItemStack(Items.LECTERN), new ItemStack(Items.STONECUTTER),
        new ItemStack(Items.LOOM), new ItemStack(Items.SMITHING_TABLE),
        new ItemStack(Items.GRINDSTONE)
    };
    private List<VillagerProfession> tlProfList = new ArrayList<>();
    private List<String>                     tlProfNames   = new ArrayList<>();
    private int                              tlSelProf     = 0;
    private int                              tlLevelFilter = 0; // 0=all, 1-5=specific level
    private int                              tlScroll      = 0;
    private final Map<String, List<TradeRow>> tlCache = new HashMap<>();
    private List<DisplayEntry>               tlDisplay = new ArrayList<>();

    // Filter bar hit areas (computed each frame, used in mouse handler)
    private final int[] filterBtnX = new int[FILTER_LABELS.length];
    private final int[] filterBtnW = new int[FILTER_LABELS.length];

    // ── Sequence tab state ───────────────────────────────────────────────────
    private static final int SEQ_ROW_H     = 12;
    private static final int SEQ_ROUNDS    = 50;
    private static final int SEQ_COLS      = 4;
    private static final int SEQ_COL_HDR_H = 12;
    private static final int SEQ_BTN_H     = 12;
    private final int[]         seqScroll          = new int[SEQ_COLS];
    private final int[]         seqCurrentRowsPrev = new int[SEQ_COLS];
    private List<List<SeqLine>> seqColumns         = new ArrayList<>();
    private final int[]         seqCurrentRows     = new int[SEQ_COLS];
    @SuppressWarnings("unchecked")
    private ResourceKey<TradeSet>[] seqTsKeys = new ResourceKey[SEQ_COLS];
    // Stored during render for use in mouseClicked:
    private int seqBtnRowY, seqBtnInnerX, seqBtnColW;

    /** One entry in a Sequence tab column. */
    private record SeqLine(String text, boolean highlight) {}

    // ── Auto Reroll tab state ────────────────────────────────────────────────
    private EditBox arManualCountBox;
    private EditBox arSafetyBox;
    private EditBox arDelayBox;
    private Button  arModeBtn;
    private Button  arToolSlotBtn;
    private Button  arWsSlotBtn;
    private Button  arHotkeyBtn;
    private Button  arRunBtn;
    private boolean arAwaitingRebind = false;

    // ── Calibrate tab state ──────────────────────────────────────────────────
    private static final int CAL_LIST_ROWS = 4;
    private static final int CAL_OBS_H     = 13;
    private static final int CAL_OBS_ROWS  = 12;
    private EditBox calSearchBox;
    private EditBox calPriceBox;
    private final Button[] calLevelBtns = new Button[5];
    private List<Holder<Enchantment>> calAllEnchants      = new ArrayList<>();
    private List<Holder<Enchantment>> calFilteredEnchants = new ArrayList<>();
    private int     calSelectedIdx     = -1;
    private int     calScrollOffset    = 0;
    private int     calSelectedLevel   = 1;
    private int     calObsScrollOffset = 0;
    private String  calResultMsg       = "";
    private List<Integer> calLastMatches = new ArrayList<>();
    private int calListX, calListY, calListW, calListH;
    private int calLevelRowY, calPriceRowY;
    private int calObsListX, calObsListY, calObsListW, calObsListH;

    public VtpSettingsScreen() {
        super(Component.literal("VTP Settings"));
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────────

    @Override
    protected void init() {
        wx = (width  - WIN_W) / 2;
        wy = (height - WIN_H) / 2;

        overlayEnabled = VillagerHudRenderer.isEnabled();

        // ── Tab positions ─────────────────────────────────────────────────────
        tabX = new int[TAB_LABELS.length];
        tabW = new int[TAB_LABELS.length];
        int tx = wx + 1;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            tabW[i] = font.width(TAB_LABELS[i]) + PAD * 2;
            tabX[i] = tx;
            tx += tabW[i] + 1;
        }

        // ── Layout origins ────────────────────────────────────────────────────
        lCX = wx + PAD + PAD;
        lCY = wy + TITLE_H + TAB_H + PHDR_H + PAD;

        rCX = wx + PAD + LP_W + PAD + PAD;
        rCY = wy + TITLE_H + TAB_H + PHDR_H + PAD;

        listX = rCX;
        listY = rCY + 18;
        listW = RP_W - PAD * 2;
        listH = LIST_ROWS * ITEM_H;

        levelRowY  = listY + listH + PAD;
        priceRowY  = levelRowY + ROW_H;
        setTargetY = priceRowY + ROW_H;

        tgtDividerY = setTargetY + ROW_H + 2;
        tgtListY    = tgtDividerY + 14;

        lvlPreviewY = lCY + ROW_H * 5 + 24;

        // ── Tab-specific widgets ──────────────────────────────────────────────
        switch (activeTab) {
            case TAB_SETTINGS    -> initSettingsWidgets();
            case TAB_SEQUENCE    -> initSequenceWidgets();
            case TAB_AUTO_REROLL -> initAutoRerollWidgets();
            case TAB_TRADE_LIST  -> initTradeListWidgets();
            case TAB_CALIBRATE   -> initCalibrateWidgets();
        }

        // ── Bottom buttons (always present) ───────────────────────────────────
        int bottomY = wy + WIN_H - 20;
        addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> saveAndClose())
                .bounds(wx + PAD, bottomY, 120, 14).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(wx + WIN_W - PAD - 80, bottomY, 80, 14).build());
    }

    private void initSettingsWidgets() {
        allEnchants = new ArrayList<>(TradeDataLoader.getTradeableEnchantments());
        allEnchants.sort(Comparator.comparing(this::enchPath));
        filteredEnchants = new ArrayList<>(allEnchants);

        EnchantmentTarget cur = VillagerHudRenderer.getTarget();
        if (cur != null) {
            selectedLevel = Math.max(1, Math.min(5, cur.minLevel()));
            for (int i = 0; i < filteredEnchants.size(); i++) {
                if (filteredEnchants.get(i).unwrapKey()
                        .map(k -> k.equals(cur.enchantmentKey())).orElse(false)) {
                    selectedIdx  = i;
                    scrollOffset = Math.max(0, i - LIST_ROWS / 2);
                    break;
                }
            }
        }

        toggleBtn = Button.builder(Component.literal(toggleLabel()), b -> {
            overlayEnabled = !overlayEnabled;
            b.setMessage(Component.literal(toggleLabel()));
        }).bounds(lCX + 104, lCY, 72, 14).build();
        addRenderableWidget(toggleBtn);

        maxRerollsBox = new EditBox(font, lCX + 104, lCY + ROW_H, 72, 14, Component.empty());
        maxRerollsBox.setValue(String.valueOf(PredictionEngine.getMaxRerolls()));
        maxRerollsBox.setMaxLength(5);
        addRenderableWidget(maxRerollsBox);

        themeBtn = Button.builder(Component.literal(VtpTheme.current().displayName), b -> {
            VtpTheme next = VtpTheme.cycle();
            b.setMessage(Component.literal(next.displayName));
            VtpPersistence.saveGlobalSettings();
        }).bounds(lCX + 104, lCY + ROW_H * 2, 72, 14).build();
        addRenderableWidget(themeBtn);

        addRenderableWidget(Button.builder(Component.literal(numeralsLabel()), b -> {
            VillagerHudRenderer.setRomanNumerals(!VillagerHudRenderer.isRomanNumerals());
            b.setMessage(Component.literal(numeralsLabel()));
            VtpPersistence.saveGlobalSettings();
        }).bounds(lCX + 104, lCY + ROW_H * 4, 72, 14).build());

        addRenderableWidget(Button.builder(Component.literal("Reset Calibration"), b -> {
            PredictionEngine.clearAllOffsets();
            VtpPersistence.saveGlobalSettings();
        }).bounds(lCX, lCY + ROW_H * 5 + 4, LP_W - PAD * 2, 14).build());

        searchBox = new EditBox(font, rCX, rCY, RP_W - PAD * 2, 14, Component.empty());
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Search enchantments..."));
        searchBox.setResponder(this::onSearch);
        addRenderableWidget(searchBox);

        for (int i = 0; i < 5; i++) {
            final int lvl = i + 1;
            levelBtns[i] = Button.builder(Component.literal(String.valueOf(lvl)), b ->
                    selectedLevel = lvl
            ).bounds(rCX + 44 + i * 22, levelRowY, 18, 14).build();
            addRenderableWidget(levelBtns[i]);
        }

        maxPriceBox = new EditBox(font, rCX + 68, priceRowY, RP_W - PAD * 2 - 68, 14, Component.empty());
        maxPriceBox.setMaxLength(4);
        maxPriceBox.setHint(Component.literal("none"));
        if (cur != null && cur.maxPrice() > 0) {
            maxPriceBox.setValue(String.valueOf(cur.maxPrice()));
        }
        addRenderableWidget(maxPriceBox);

        addRenderableWidget(Button.builder(Component.literal("Add Target"), b -> applyTarget())
                .bounds(rCX, setTargetY, RP_W - PAD * 2, 14).build());
    }

    private void initTradeListWidgets() {
        if (tlProfList.isEmpty()) loadProfessions();
        if (!tlProfList.isEmpty()) buildDisplayList();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Entity crosshair = Minecraft.getInstance().crosshairPickEntity;
        if (crosshair instanceof Villager v) {
            VillagerTracker.updateAndSelect(v.getUUID(), v.getVillagerData());
        }

        if (activeTab == TAB_SETTINGS) {
            VillagerTracker.getLastVillagerData().ifPresentOrElse(this::computeLevelPreview, () -> {
                lvlLines    = new ArrayList<>();
                lvlProfName = null;
            });
        }

        VtpTheme t = VtpTheme.current();

        g.fill(0, 0, width, height, t.overlay);

        // Window frame
        g.fill(wx, wy, wx + WIN_W, wy + WIN_H, t.win);
        outline(g, wx, wy, WIN_W, WIN_H, t.border);

        // Title bar
        g.fill(wx + 1, wy + 1, wx + WIN_W - 1, wy + TITLE_H, t.titlebar);
        g.fill(wx + 1, wy + TITLE_H, wx + WIN_W - 1, wy + TITLE_H + 1, t.border);
        g.centeredText(font, "VTP Settings", wx + WIN_W / 2, wy + 7, t.accent);

        // ── Tab bar ───────────────────────────────────────────────────────────
        int tabBarY = wy + TITLE_H;
        g.fill(wx + 1, tabBarY, wx + WIN_W - 1, tabBarY + TAB_H, t.win);
        g.fill(wx + 1, tabBarY + TAB_H - 1, wx + WIN_W - 1, tabBarY + TAB_H, t.border);

        for (int i = 0; i < TAB_LABELS.length; i++) {
            boolean active = (i == activeTab);
            int tbx = tabX[i], tbw = tabW[i];
            if (active) {
                g.fill(tbx, tabBarY + 1, tbx + tbw, tabBarY + TAB_H, t.panel);
                outline(g, tbx, tabBarY, tbw, TAB_H, t.border);
                g.fill(tbx + 1, tabBarY + TAB_H - 1, tbx + tbw - 1, tabBarY + TAB_H, t.panel);
            } else {
                g.fill(tbx, tabBarY + 2, tbx + tbw, tabBarY + TAB_H - 1, t.phdr);
            }
            g.text(font, TAB_LABELS[i], tbx + PAD, tabBarY + (TAB_H - 8) / 2, active ? t.accent : t.muted);
        }

        // ── Tab content ───────────────────────────────────────────────────────
        switch (activeTab) {
            case TAB_SETTINGS    -> renderSettingsContent(g, t, mx, my);
            case TAB_SEQUENCE    -> renderSequenceContent(g, t, mx, my);
            case TAB_AUTO_REROLL -> renderAutoRerollContent(g, t, mx, my);
            case TAB_TRADE_LIST  -> renderTradeListContent(g, t, mx, my);
            case TAB_CALIBRATE   -> renderCalibrateContent(g, t, mx, my);
        }

        // Bottom separator
        int divY = wy + WIN_H - 26;
        g.fill(wx + 1, divY, wx + WIN_W - 1, divY + 1, t.border);

        super.extractRenderState(g, mx, my, delta);
    }

    // ── Settings tab renderer ─────────────────────────────────────────────────

    private void renderSettingsContent(GuiGraphicsExtractor g, VtpTheme t, int mx, int my) {
        int panelY = wy + TITLE_H + TAB_H;

        panel(g, t, wx + PAD, panelY, LP_W, P_H, "GENERAL");

        g.text(font, "HUD Overlay",  lCX, lCY + 3,             t.text);
        g.text(font, "Max Rerolls",  lCX, lCY + ROW_H + 3,     t.text);
        g.text(font, "Theme",        lCX, lCY + ROW_H * 2 + 3, t.text);
        g.text(font, "Seed",         lCX, lCY + ROW_H * 3 + 3, t.text);
        g.text(font, "Numerals",     lCX, lCY + ROW_H * 4 + 3, t.text);

        String seedStr = SeedProvider.getSeed().isPresent()
                ? String.valueOf(SeedProvider.getSeed().getAsLong()) : "not set";
        g.text(font, seedStr, lCX + 176 - font.width(seedStr), lCY + ROW_H * 3 + 3, t.muted);

        int offsets = PredictionEngine.getAllOffsets().size();
        String calStr = offsets > 0 ? offsets + " set(s) calibrated" : "Not calibrated";
        g.text(font, calStr, lCX, lCY + ROW_H * 5 - 4, t.muted);

        if (!lvlLines.isEmpty()) {
            g.fill(lCX, lvlPreviewY - 4, lCX + LP_W - PAD * 2, lvlPreviewY - 3, t.border);
            String header = lvlProfName != null ? "LEVELS  " + lvlProfName : "LEVELS";
            g.text(font, header, lCX, lvlPreviewY, t.accent);

            for (int i = 0; i < lvlLines.size(); i++) {
                LvlLine line = lvlLines.get(i);
                int rowY = lvlPreviewY + 11 + i * LVL_H;
                int rowX = lCX, rowW = LP_W - PAD * 2;
                if (line.current()) {
                    g.fill(rowX - 1, rowY - 1, rowX + rowW + 1, rowY + LVL_H - 1, t.lvlCur);
                    g.fill(rowX - 1, rowY - 1, rowX + 1, rowY + LVL_H - 1, t.accent);
                } else {
                    g.fill(rowX - 1, rowY - 1, rowX + rowW + 1, rowY + LVL_H - 1, t.lvlBg);
                }
                g.text(font, line.text(), rowX + 3, rowY, line.current() ? t.accent : t.text);
            }
        } else if (lvlProfName == null) {
            g.fill(lCX - 1, lvlPreviewY - 4, lCX + LP_W - PAD * 2 + 1, lvlPreviewY - 3, t.border);
            g.text(font, "LEVELS", lCX, lvlPreviewY, t.accent);
            g.text(font, "Look at a villager", lCX + 2, lvlPreviewY + 12, t.muted);
        }

        panel(g, t, wx + PAD + LP_W + PAD, panelY, RP_W, P_H, "TARGET ENCHANTMENT");

        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, t.border);
        g.fill(listX, listY, listX + listW, listY + listH, t.listBg);

        for (int i = 0; i < LIST_ROWS; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredEnchants.size()) break;
            int iY  = listY + i * ITEM_H;
            boolean sel = idx == selectedIdx;
            boolean hov = mx >= listX && mx < listX + listW && my >= iY && my < iY + ITEM_H;
            if (sel) {
                g.fill(listX, iY, listX + listW, iY + ITEM_H, t.listSel);
                g.fill(listX, iY, listX + 2, iY + ITEM_H, t.accent);
            } else if (hov) {
                g.fill(listX, iY, listX + listW, iY + ITEM_H, t.listHov);
            }
            g.text(font, formatName(filteredEnchants.get(idx)),
                    listX + 5, iY + 2, sel ? t.accent : t.text);
        }

        if (filteredEnchants.size() > LIST_ROWS) {
            int thumbH = Math.max(6, listH * LIST_ROWS / filteredEnchants.size());
            int maxSc  = Math.max(1, filteredEnchants.size() - LIST_ROWS);
            int thumbY = listY + (listH - thumbH) * scrollOffset / maxSc;
            g.fill(listX + listW - 3, listY,  listX + listW, listY + listH, t.div);
            g.fill(listX + listW - 3, thumbY, listX + listW, thumbY + thumbH, t.muted);
        }
        if (filteredEnchants.isEmpty()) {
            g.centeredText(font, "No results", listX + listW / 2, listY + listH / 2 - 4, t.muted);
        }

        g.text(font, "Level:", rCX, levelRowY + 3, t.text);
        for (int i = 0; i < 5; i++) {
            if (selectedLevel == i + 1) {
                int bx = rCX + 44 + i * 22;
                g.fill(bx - 1, levelRowY + 15, bx + 19, levelRowY + 17, t.accent);
            }
        }
        g.text(font, "Max Price:", rCX, priceRowY + 3, t.text);

        g.fill(rCX - 1, tgtDividerY, rCX + listW + 1, tgtDividerY + 1, t.div);
        g.text(font, "Active Targets", rCX, tgtDividerY + 3, t.accent);

        List<EnchantmentTarget> activeTgts = VillagerHudRenderer.getTargets();
        if (activeTgts.isEmpty()) {
            g.text(font, "None", rCX + 3, tgtListY + 2, t.muted);
        } else {
            int endIdx = Math.min(tgtScrollOffset + TGT_ROWS, activeTgts.size());
            for (int i = tgtScrollOffset; i < endIdx; i++) {
                EnchantmentTarget tgt = activeTgts.get(i);
                int rowY = tgtListY + (i - tgtScrollOffset) * TGT_H;
                boolean hov = (double) mx >= rCX && (double) mx < rCX + listW
                        && (double) my >= rowY && (double) my < rowY + TGT_H;
                if (hov) g.fill(rCX, rowY, rCX + listW, rowY + TGT_H, t.listHov);
                String eName = formatNameStr(tgt.enchantmentKey().identifier().getPath());
                String label = eName + " " + VillagerHudRenderer.fmtLvl(tgt.minLevel());
                if (tgt.maxPrice() >= 0) label += " (\u2264" + tgt.maxPrice() + ")";
                g.text(font, label, rCX + 3, rowY + 2, t.text);
                String rerollStr = rerollsLabel(tgt);
                int rerollColor = rerollStr.equals("now!") ? t.accent
                        : rerollStr.equals("?") || rerollStr.startsWith(">") ? t.muted : t.text;
                int xBtnX = rCX + listW - font.width("\u00d7") - 3;
                g.text(font, rerollStr, xBtnX - font.width(rerollStr) - 4, rowY + 2, rerollColor);
                g.text(font, "\u00d7", xBtnX, rowY + 2, t.error);
            }
            if (activeTgts.size() > TGT_ROWS) {
                int thumbH = Math.max(4, TGT_ROWS * TGT_H * TGT_ROWS / activeTgts.size());
                int maxSc  = Math.max(1, activeTgts.size() - TGT_ROWS);
                int thumbY = tgtListY + (TGT_ROWS * TGT_H - thumbH) * tgtScrollOffset / maxSc;
                g.fill(rCX + listW - 2, tgtListY, rCX + listW, tgtListY + TGT_ROWS * TGT_H, t.div);
                g.fill(rCX + listW - 2, thumbY, rCX + listW, thumbY + thumbH, t.muted);
            }
        }
    }

    // ── Trade List tab renderer ───────────────────────────────────────────────

    private void renderTradeListContent(GuiGraphicsExtractor g, VtpTheme t, int mx, int my) {
        int panelY = wy + TITLE_H + TAB_H;
        int innerH = P_H - PHDR_H - PAD * 2; // usable inner height in each panel

        // ── Left panel — profession list ──────────────────────────────────────
        panel(g, t, wx + PAD, panelY, LP_W, P_H, "PROFESSION");

        if (tlProfList.isEmpty()) {
            g.text(font, "No data", lCX, lCY, t.muted);
            g.text(font, "(Open a world first)", lCX, lCY + 12, t.muted);
        } else {
            int profInnerW = LP_W - PAD * 2;
            for (int i = 0; i < tlProfList.size(); i++) {
                int rowY = lCY + i * TL_PROF_H;
                if (rowY + TL_PROF_H > panelY + P_H - PAD) break;

                boolean sel = (i == tlSelProf);
                boolean hov = !sel
                        && mx >= lCX - 2 && mx < lCX + profInnerW
                        && my >= rowY - 1 && my < rowY + TL_PROF_H - 1;

                if (sel) {
                    g.fill(lCX - 2, rowY - 1, lCX + profInnerW, rowY + TL_PROF_H - 1, t.listSel);
                    g.fill(lCX - 2, rowY - 1, lCX, rowY + TL_PROF_H - 1, t.accent);
                } else if (hov) {
                    g.fill(lCX - 2, rowY - 1, lCX + profInnerW, rowY + TL_PROF_H - 1, t.listHov);
                }

                // Workstation icon (16×16, centered in row)
                if (i < WORKSTATION_STACKS.length) {
                    g.item(WORKSTATION_STACKS[i], lCX, rowY - 1);
                }
                int textX = lCX + 17;
                g.text(font, tlProfNames.get(i), textX, rowY + 1, sel ? t.accent : t.text);
            }
        }

        // ── Right panel — trades ──────────────────────────────────────────────
        panel(g, t, wx + PAD + LP_W + PAD, panelY, RP_W, P_H, "TRADES");

        int innerW    = RP_W - PAD * 2;
        int filterY   = rCY;
        int tradeListY = filterY + TL_FILTER_H;
        int tradeAreaH = innerH - TL_FILTER_H;

        // Level filter bar
        int fx = rCX;
        for (int i = 0; i < FILTER_LABELS.length; i++) {
            int fw = font.width(FILTER_LABELS[i]) + 8;
            filterBtnX[i] = fx;
            filterBtnW[i] = fw;
            boolean active = (i == tlLevelFilter);
            boolean hov = mx >= fx && mx < fx + fw && my >= filterY && my < filterY + TL_FILTER_H - 2;

            if (active) {
                g.fill(fx, filterY, fx + fw, filterY + TL_FILTER_H - 2, t.accent);
                g.text(font, FILTER_LABELS[i], fx + 4, filterY + 4, t.win);
            } else if (hov) {
                g.fill(fx, filterY, fx + fw, filterY + TL_FILTER_H - 2, t.listHov);
                g.text(font, FILTER_LABELS[i], fx + 4, filterY + 4, t.text);
            } else {
                g.fill(fx, filterY, fx + fw, filterY + TL_FILTER_H - 2, t.phdr);
                g.text(font, FILTER_LABELS[i], fx + 4, filterY + 4, t.muted);
            }
            fx += fw + 2;
        }
        // Separator under filter bar
        g.fill(rCX, filterY + TL_FILTER_H - 2, rCX + innerW, filterY + TL_FILTER_H - 1, t.border);

        // Trade list
        if (tlDisplay.isEmpty()) {
            String hint = tlProfList.isEmpty() ? "Open a world to load trades"
                    : "Select a profession";
            g.centeredText(font, hint, rCX + innerW / 2, tradeListY + tradeAreaH / 2 - 4, t.muted);
        } else {
            int visRows = tradeAreaH / TL_ROW_H;
            int maxScroll = Math.max(0, tlDisplay.size() - visRows);
            if (tlScroll > maxScroll) tlScroll = maxScroll;

            int rowY = tradeListY;
            for (int i = tlScroll; i < tlDisplay.size() && rowY + TL_ROW_H <= tradeListY + tradeAreaH; i++) {
                DisplayEntry entry = tlDisplay.get(i);
                if (entry.isHeader()) {
                    // Level section header
                    g.fill(rCX, rowY + 2, rCX + innerW, rowY + TL_ROW_H - 2, t.phdr);
                    g.fill(rCX, rowY + TL_ROW_H - 3, rCX + innerW, rowY + TL_ROW_H - 2, t.border);
                    g.text(font, entry.headerText(), rCX + 4, rowY + 7, t.accent);
                } else {
                    TradeRow row = entry.trade();
                    boolean hov = mx >= rCX && mx < rCX + innerW - 4
                            && my >= rowY && my < rowY + TL_ROW_H;
                    if (hov) g.fill(rCX, rowY, rCX + innerW - 4, rowY + TL_ROW_H, t.listHov);
                    renderTradeRow(g, t, rCX + 2, rowY, innerW - 8, row);
                }
                rowY += TL_ROW_H;
            }

            // Scrollbar
            if (tlDisplay.size() > visRows) {
                int sbX = rCX + innerW - 3;
                int thumbH = Math.max(8, tradeAreaH * visRows / tlDisplay.size());
                int thumbY = tradeListY + (tradeAreaH - thumbH) * tlScroll / Math.max(1, maxScroll);
                g.fill(sbX, tradeListY, sbX + 3, tradeListY + tradeAreaH, t.div);
                g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, t.muted);
            }
        }
    }

    /** Renders one trade row with item icons: [cost1] [+ cost2] → [result]. */
    private void renderTradeRow(GuiGraphicsExtractor g, VtpTheme t, int x, int y, int availW, TradeRow row) {
        int iy = y + (TL_ROW_H - 16) / 2;  // vertically center 16px icons in row
        int ty = y + TL_ROW_H / 2 - 4;      // vertically center text
        int cx = x;

        // Cost 1
        g.item(row.cost1(), cx, iy);
        g.itemDecorations(font, row.cost1(), cx, iy);
        cx += 17;

        // Cost 2
        if (!row.cost2().isEmpty()) {
            g.text(font, "+", cx, ty, t.muted);
            cx += font.width("+") + 2;
            g.item(row.cost2(), cx, iy);
            g.itemDecorations(font, row.cost2(), cx, iy);
            cx += 17;
        }

        // Arrow
        g.text(font, "\u2192", cx + 1, ty, t.muted);
        cx += font.width("\u2192") + 4;

        // Result
        g.item(row.result(), cx, iy);
        g.itemDecorations(font, row.result(), cx, iy);
        cx += 17;

        // Result name label
        String name = row.isEnchBook() ? "Enchanted Book" : row.result().getHoverName().getString();
        int nameAvail = x + availW - cx;
        if (font.width(name) > nameAvail) {
            name = font.plainSubstrByWidth(name, nameAvail - font.width("\u2026")) + "\u2026";
        }
        g.text(font, name, cx, ty, t.text);
    }

    // ── Sequence tab ─────────────────────────────────────────────────────────

    private void initSequenceWidgets() {
        for (int c = 0; c < SEQ_COLS; c++) {
            seqScroll[c] = 0;
            seqCurrentRowsPrev[c] = -1; // force initial auto-scroll per column
        }
        buildSequenceList();
    }

    private void buildSequenceList() {
        seqColumns = new ArrayList<>();
        for (int i = 0; i < SEQ_COLS; i++) {
            seqColumns.add(new ArrayList<>());
            seqCurrentRows[i] = 0;
            seqTsKeys[i] = null;
        }

        if (SeedProvider.getSeed().isEmpty()) return;
        long seed = SeedProvider.getSeed().getAsLong();

        Minecraft mc = Minecraft.getInstance();
        RegistryAccess ra = mc.getSingleplayerServer() != null
                ? mc.getSingleplayerServer().registryAccess()
                : (mc.level != null ? mc.level.registryAccess() : null);
        if (ra == null) return;

        Optional<Registry<VillagerProfession>> regOpt = ra.lookup(Registries.VILLAGER_PROFESSION);
        if (regOpt.isEmpty()) return;

        ResourceKey<VillagerProfession> libKey = ResourceKey.create(
                Registries.VILLAGER_PROFESSION,
                Identifier.fromNamespaceAndPath("minecraft", "librarian"));
        Optional<Holder.Reference<VillagerProfession>> libRef = regOpt.get().get(libKey);
        if (libRef.isEmpty()) return;

        Holder<VillagerProfession> librarian = libRef.get();
        List<EnchantmentTarget> activeTgts = VillagerHudRenderer.getTargets();
        int[] levels = {1, 2, 3, 4};

        java.util.UUID lastUuid = VillagerTracker.getLastVillagerUuid();

        for (int s = 0; s < levels.length; s++) {
            int level = levels[s];
            List<SeqLine> col = seqColumns.get(s);
            ResourceKey<TradeSet> tsKey = TradeDataLoader.getTradeSetKey(librarian, level);
            seqTsKeys[s] = tsKey;
            if (tsKey == null) continue;

            int offset = PredictionEngine.getOffset(tsKey);

            // Determine which absolute round the tracked librarian is currently on
            int currentRound = offset;
            if (lastUuid != null) {
                currentRound = level == 1
                        ? VillagerTracker.getCurrentRound(lastUuid).orElse(offset)
                        : VillagerTracker.getVillagerRoundForLevel(lastUuid, librarian, level);
            }

            List<List<TradeSimulator.SimulatedOffer>> rounds =
                    PredictionEngine.getUpcomingRounds(seed, librarian, level, offset, SEQ_ROUNDS);

            for (int r = 0; r < rounds.size(); r++) {
                boolean addedAny = false;
                for (TradeSimulator.SimulatedOffer offer : rounds.get(r)) {
                    if (offer.enchantment().isEmpty()) continue;
                    TradeSimulator.EnchantmentResult ench = offer.enchantment().get();
                    String name = ench.enchantment().unwrapKey()
                            .map(k -> formatNameStr(k.identifier().getPath())).orElse("?");
                    String text = "#" + r + " " + name + " "
                            + VillagerHudRenderer.fmtLvl(ench.level())
                            + "  +" + ench.additionalCost() + "e";

                    boolean isTarget = false;
                    for (EnchantmentTarget tgt : activeTgts) {
                        if (tgt.matches(ench.enchantment(), ench.level())) {
                            isTarget = true;
                            break;
                        }
                    }
                    col.add(new SeqLine(text, isTarget));
                    addedAny = true;
                }
                if (!addedAny) {
                    col.add(new SeqLine("#" + r + "  \u2014", false));
                }
            }

            // Row index within this column that corresponds to the current round
            seqCurrentRows[s] = Math.max(0, Math.min(currentRound - offset, col.size() - 1));
        }

        // Auto-scroll each column whose current row changed since the last build.
        // Columns whose current row didn't change keep their existing user-adjusted scroll.
        for (int c = 0; c < SEQ_COLS; c++) {
            if (seqCurrentRows[c] != seqCurrentRowsPrev[c]) {
                seqScroll[c] = Math.max(0, seqCurrentRows[c]);
            }
            seqCurrentRowsPrev[c] = seqCurrentRows[c];
        }
    }

    private void renderSequenceContent(GuiGraphicsExtractor g, VtpTheme t, int mx, int my) {
        int panelY  = wy + TITLE_H + TAB_H;
        int seqW    = WIN_W - PAD * 2;
        int innerX  = wx + PAD + PAD;
        int innerW  = seqW - PAD * 2;
        int colW    = innerW / SEQ_COLS;

        // Store for mouseClicked hit-testing
        seqBtnInnerX = innerX;
        seqBtnColW   = colW;

        panel(g, t, wx + PAD, panelY, seqW, P_H, "LIBRARIAN SEQUENCE");

        // Column sub-headers
        int colHdrY = panelY + PHDR_H + 1;
        String[] colLabels = {"Reroll", "Level 2", "Level 3", "Level 4"};
        for (int c = 0; c < SEQ_COLS; c++) {
            int cx = innerX + c * colW;
            g.fill(cx, colHdrY, cx + colW, colHdrY + SEQ_COL_HDR_H, t.phdr);
            g.centeredText(font, colLabels[c], cx + colW / 2, colHdrY + 2, t.accent);
        }
        // Vertical dividers between columns (extend all the way to bottom)
        int bottomY = panelY + P_H - PAD;
        for (int c = 1; c < SEQ_COLS; c++) {
            int dx = innerX + c * colW - 1;
            g.fill(dx, colHdrY, dx + 1, bottomY, t.border);
        }

        // ── +/- offset buttons row ────────────────────────────────────────────
        int btnRowY  = colHdrY + SEQ_COL_HDR_H;
        seqBtnRowY   = btnRowY;
        int btnW     = 9;
        g.fill(innerX, btnRowY, innerX + innerW, btnRowY + SEQ_BTN_H, t.win);
        for (int c = 0; c < SEQ_COLS; c++) {
            int cx           = innerX + c * colW;
            int minusBtnX    = cx + 2;
            int plusBtnX     = cx + colW - btnW - 2;

            // "-" button
            boolean minusHov = mx >= minusBtnX && mx < minusBtnX + btnW
                    && my >= btnRowY && my < btnRowY + SEQ_BTN_H;
            g.fill(minusBtnX, btnRowY + 1, minusBtnX + btnW, btnRowY + SEQ_BTN_H - 1,
                    minusHov ? t.listHov : t.phdr);
            g.centeredText(font, "\u2212", minusBtnX + btnW / 2, btnRowY + 2, t.text);

            // Offset value centered between the two buttons
            int offsetVal  = seqTsKeys[c] != null ? PredictionEngine.getOffset(seqTsKeys[c]) : 0;
            String offStr  = String.valueOf(offsetVal);
            int midX       = minusBtnX + btnW + (plusBtnX - minusBtnX - btnW) / 2;
            g.centeredText(font, offStr, midX, btnRowY + 2, t.muted);

            // "+" button
            boolean plusHov = mx >= plusBtnX && mx < plusBtnX + btnW
                    && my >= btnRowY && my < btnRowY + SEQ_BTN_H;
            g.fill(plusBtnX, btnRowY + 1, plusBtnX + btnW, btnRowY + SEQ_BTN_H - 1,
                    plusHov ? t.listHov : t.phdr);
            g.centeredText(font, "+", plusBtnX + btnW / 2, btnRowY + 2, t.text);
        }
        // Divider below the button row
        g.fill(innerX, btnRowY + SEQ_BTN_H - 1, innerX + innerW, btnRowY + SEQ_BTN_H, t.border);

        int seqListY = btnRowY + SEQ_BTN_H;
        int seqAreaH = bottomY - seqListY;

        boolean allEmpty = seqColumns.stream().allMatch(List::isEmpty);
        if (allEmpty) {
            String msg = SeedProvider.getSeed().isEmpty() ? "No seed set" : "Open a world first";
            g.centeredText(font, msg, wx + WIN_W / 2, seqListY + seqAreaH / 2 - 4, t.muted);
            return;
        }

        int visRows = seqAreaH / SEQ_ROW_H;
        int textW   = colW - 6;

        for (int c = 0; c < SEQ_COLS && c < seqColumns.size(); c++) {
            List<SeqLine> col = seqColumns.get(c);
            int cx = innerX + c * colW;
            int colMaxScroll = Math.max(0, col.size() - visRows);
            if (seqScroll[c] > colMaxScroll) seqScroll[c] = colMaxScroll;
            if (seqScroll[c] < 0) seqScroll[c] = 0;
            boolean hasScrollbar = col.size() > visRows;
            int lineTextW = textW - (hasScrollbar ? 4 : 0);

            for (int r = 0; r < visRows; r++) {
                int idx = seqScroll[c] + r;
                if (idx >= col.size()) break;
                SeqLine line = col.get(idx);
                int ry = seqListY + r * SEQ_ROW_H;
                String txt = font.plainSubstrByWidth(line.text(), lineTextW);

                boolean isCurrent = (idx == seqCurrentRows[c]);

                if (line.highlight()) {
                    g.fill(cx, ry, cx + colW, ry + SEQ_ROW_H, t.lvlCur);
                    g.fill(cx, ry, cx + 2, ry + SEQ_ROW_H, t.accent);
                    // If also current, add a right-edge marker so both states are visible
                    if (isCurrent) g.fill(cx + colW - 2, ry, cx + colW, ry + SEQ_ROW_H, t.accent);
                    g.text(font, txt, cx + 4, ry + 2, t.accent);
                } else if (isCurrent) {
                    g.fill(cx, ry, cx + colW, ry + SEQ_ROW_H, t.listSel);
                    g.fill(cx + colW - 2, ry, cx + colW, ry + SEQ_ROW_H, t.accent);
                    g.text(font, txt, cx + 4, ry + 2, t.text);
                } else {
                    boolean hov = (double) mx >= cx && (double) mx < cx + colW
                            && (double) my >= ry && (double) my < ry + SEQ_ROW_H;
                    if (hov) g.fill(cx, ry, cx + colW, ry + SEQ_ROW_H, t.listHov);
                    g.text(font, txt, cx + 4, ry + 2, t.text);
                }
            }

            // Per-column scrollbar on the right edge of this column
            if (hasScrollbar) {
                int sbX    = cx + colW - 4;
                int thumbH = Math.max(8, seqAreaH * visRows / col.size());
                int thumbY = seqListY + (seqAreaH - thumbH) * seqScroll[c] / Math.max(1, colMaxScroll);
                g.fill(sbX, seqListY, sbX + 3, seqListY + seqAreaH, t.div);
                g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, t.muted);
            }
        }
    }

    // ── Auto Reroll tab ──────────────────────────────────────────────────────

    private static String slotLabel(int slot) {
        if (slot == AutoRerollManager.SLOT_OFFHAND) return "Offhand";
        return "Slot " + (slot + 1);
    }

    private static String hotkeyLabel() {
        KeyMapping km = VtpKeybinds.getAutoRerollKey();
        if (km == null) return "—";
        if (km.isUnbound()) return "Unbound";
        return km.getTranslatedKeyMessage().getString();
    }

    private void initAutoRerollWidgets() {
        arAwaitingRebind = false;

        // Column layout mirrors the Settings tab for visual consistency.
        int row = 0;

        arModeBtn = Button.builder(Component.literal(arModeLabel()), b -> {
            AutoRerollManager.setUseTargetCount(!AutoRerollManager.isUseTargetCount());
            b.setMessage(Component.literal(arModeLabel()));
            if (arManualCountBox != null) arManualCountBox.setEditable(!AutoRerollManager.isUseTargetCount());
        }).bounds(lCX + 90, lCY + ROW_H * row, 86, 14).build();
        addRenderableWidget(arModeBtn);
        row++;

        arManualCountBox = new EditBox(font, lCX + 90, lCY + ROW_H * row, 86, 14, Component.empty());
        arManualCountBox.setMaxLength(5);
        arManualCountBox.setValue(String.valueOf(AutoRerollManager.getManualCount()));
        arManualCountBox.setEditable(!AutoRerollManager.isUseTargetCount());
        addRenderableWidget(arManualCountBox);
        row++;

        arSafetyBox = new EditBox(font, lCX + 90, lCY + ROW_H * row, 86, 14, Component.empty());
        arSafetyBox.setMaxLength(5);
        arSafetyBox.setValue(String.valueOf(AutoRerollManager.getSafetyRerolls()));
        addRenderableWidget(arSafetyBox);
        row++;

        arDelayBox = new EditBox(font, lCX + 90, lCY + ROW_H * row, 86, 14, Component.empty());
        arDelayBox.setMaxLength(5);
        arDelayBox.setValue(String.valueOf(AutoRerollManager.getCycleDelayTicks()));
        arDelayBox.setHint(Component.literal("ticks"));
        addRenderableWidget(arDelayBox);
        row++;

        arToolSlotBtn = Button.builder(Component.literal(slotLabel(AutoRerollManager.getToolSlot())), b -> {
            AutoRerollManager.setToolSlot((AutoRerollManager.getToolSlot() + 1) % 9);
            b.setMessage(Component.literal(slotLabel(AutoRerollManager.getToolSlot())));
        }).bounds(lCX + 90, lCY + ROW_H * row, 86, 14).build();
        addRenderableWidget(arToolSlotBtn);
        row++;

        arWsSlotBtn = Button.builder(Component.literal(slotLabel(AutoRerollManager.getWorkstationSlot())), b -> {
            int cur = AutoRerollManager.getWorkstationSlot();
            // Cycle: 0..8 → OFFHAND → 0
            int next = cur == AutoRerollManager.SLOT_OFFHAND ? 0
                    : (cur == 8 ? AutoRerollManager.SLOT_OFFHAND : cur + 1);
            AutoRerollManager.setWorkstationSlot(next);
            b.setMessage(Component.literal(slotLabel(AutoRerollManager.getWorkstationSlot())));
        }).bounds(lCX + 90, lCY + ROW_H * row, 86, 14).build();
        addRenderableWidget(arWsSlotBtn);
        row++;

        arHotkeyBtn = Button.builder(Component.literal(hotkeyLabel()), b -> {
            arAwaitingRebind = !arAwaitingRebind;
            b.setMessage(Component.literal(arAwaitingRebind ? "Press a key..." : hotkeyLabel()));
        }).bounds(lCX + 90, lCY + ROW_H * row, 86, 14).build();
        addRenderableWidget(arHotkeyBtn);

        // Right panel: start/stop button
        arRunBtn = Button.builder(Component.literal(arRunLabel()), b -> {
            saveAutoRerollInputs();
            AutoRerollManager.toggle();
            b.setMessage(Component.literal(arRunLabel()));
        }).bounds(rCX, rCY + 8, RP_W - PAD * 2, 16).build();
        addRenderableWidget(arRunBtn);
    }

    private String arModeLabel() {
        return AutoRerollManager.isUseTargetCount() ? "Until Target" : "Manual";
    }

    private String arRunLabel() {
        return AutoRerollManager.isRunning() ? "Stop" : "Start";
    }

    /** Commit the two EditBox values to the manager. */
    private void saveAutoRerollInputs() {
        if (arManualCountBox != null) {
            try {
                int v = Integer.parseInt(arManualCountBox.getValue().trim());
                AutoRerollManager.setManualCount(v);
            } catch (NumberFormatException ignored) {}
        }
        if (arSafetyBox != null) {
            try {
                int v = Integer.parseInt(arSafetyBox.getValue().trim());
                AutoRerollManager.setSafetyRerolls(v);
            } catch (NumberFormatException ignored) {}
        }
        if (arDelayBox != null) {
            try {
                int v = Integer.parseInt(arDelayBox.getValue().trim());
                AutoRerollManager.setCycleDelayTicks(v);
            } catch (NumberFormatException ignored) {}
        }
    }

    private void renderAutoRerollContent(GuiGraphicsExtractor g, VtpTheme t, int mx, int my) {
        int panelY = wy + TITLE_H + TAB_H;

        // Left panel: configuration
        panel(g, t, wx + PAD, panelY, LP_W, P_H, "CONFIGURATION");

        String[] labels = {"Mode:", "Manual Count:", "Safety Rerolls:", "Cycle Delay:", "Tool Slot:", "Workstation:", "Hotkey:"};
        for (int i = 0; i < labels.length; i++) {
            g.text(font, labels[i], lCX, lCY + ROW_H * i + 3, t.text);
        }

        // Right panel: status + action button
        panel(g, t, wx + PAD + LP_W + PAD, panelY, RP_W, P_H, "STATUS");

        int sy = rCY + 32; // below the Start/Stop button

        boolean running = AutoRerollManager.isRunning();
        int runningColor = running ? t.accent : t.muted;

        g.text(font, "State:", rCX, sy, t.text);
        g.text(font, running ? "Running" : "Idle", rCX + 70, sy, runningColor);
        sy += LVL_H + 2;

        g.text(font, "Progress:", rCX, sy, t.text);
        g.text(font,
                AutoRerollManager.getCompletedRerolls() + " / " + AutoRerollManager.getPlannedRerolls(),
                rCX + 70, sy, t.text);
        sy += LVL_H + 2;

        int rerollsTilTarget = computeDisplayRerollsToTarget();
        String til = rerollsTilTarget < 0 ? "—" : String.valueOf(rerollsTilTarget);
        g.text(font, "To Target:", rCX, sy, t.text);
        g.text(font, til, rCX + 70, sy, t.text);
        sy += LVL_H + 4;

        g.fill(rCX - 2, sy, rCX + RP_W - PAD * 2 - 4, sy + 1, t.div);
        sy += 4;
        g.text(font, "Last Message:", rCX, sy, t.accent);
        sy += LVL_H;
        String msg = AutoRerollManager.getStatus();
        int avail = RP_W - PAD * 2 - 4;
        // Simple word-wrap into as many lines as fit
        while (!msg.isEmpty() && sy + LVL_H < panelY + P_H - PAD) {
            int take = msg.length();
            while (take > 0 && font.width(msg.substring(0, take)) > avail) take--;
            if (take <= 0) break;
            g.text(font, msg.substring(0, take), rCX, sy, t.text);
            msg = take >= msg.length() ? "" : msg.substring(take).stripLeading();
            sy += LVL_H;
        }

        // Hint at bottom of left panel
        int hintY = panelY + P_H - PAD - LVL_H;
        g.text(font, "Aim at a workstation, then Start.", lCX, hintY, t.muted);
    }

    private int computeDisplayRerollsToTarget() {
        if (!AutoRerollManager.isUseTargetCount()) return AutoRerollManager.getManualCount();
        var dataOpt = VillagerTracker.getLastVillagerData();
        var seedOpt = SeedProvider.getSeed();
        var tgts = VillagerHudRenderer.getTargets();
        if (dataOpt.isEmpty() || seedOpt.isEmpty() || tgts.isEmpty()) return -1;
        VillagerData data = dataOpt.get();
        var profKey = data.profession().unwrapKey();
        if (profKey.isEmpty()
                || profKey.get().equals(VillagerProfession.NONE)
                || profKey.get().equals(VillagerProfession.NITWIT)) return -1;

        ResourceKey<TradeSet> tsKey = TradeDataLoader.getTradeSetKey(data.profession(), data.level());
        int typeOffset = tsKey != null ? PredictionEngine.getOffset(tsKey) : 0;
        int tradeSetTotal = VillagerTracker.getTradeSetTotal(data);
        java.util.UUID uuid = VillagerTracker.getLastVillagerUuid();
        int nowRound = uuid != null
                ? VillagerTracker.getCurrentRound(uuid).orElse(typeOffset + tradeSetTotal)
                : typeOffset + tradeSetTotal;

        PredictionResult best = null;
        for (EnchantmentTarget tgt : tgts) {
            PredictionResult r = PredictionEngine.predictFrom(
                    seedOpt.getAsLong(), data.profession(), data.level(), tgt, nowRound);
            if (r.found() && (best == null
                    || r.rerollsNeeded().get() < best.rerollsNeeded().get())) best = r;
        }
        if (best == null) return -1;
        return Math.max(0, best.rerollsNeeded().get() - nowRound);
    }

    // ── Calibrate tab ────────────────────────────────────────────────────────

    private void initCalibrateWidgets() {
        calAllEnchants = new ArrayList<>(TradeDataLoader.getTradeableEnchantments());
        calAllEnchants.sort(Comparator.comparing(this::enchPath));
        calFilteredEnchants = new ArrayList<>(calAllEnchants);

        int panelY = wy + TITLE_H + TAB_H;
        int innerH = P_H - PHDR_H - PAD * 2;

        calObsListX = lCX;
        calObsListY = lCY + 12;
        calObsListW = LP_W - PAD * 2;
        calObsListH = CAL_OBS_ROWS * CAL_OBS_H;

        calListX = rCX;
        calListY = rCY + 18;
        calListW = RP_W - PAD * 2;
        calListH = CAL_LIST_ROWS * ITEM_H;

        calLevelRowY = calListY + calListH + PAD;
        calPriceRowY = calLevelRowY + ROW_H;
        int addBtnY     = calPriceRowY + ROW_H;
        int noTradeBtnY = addBtnY + 16;
        int findBtnY    = panelY + P_H - PAD - 14;
        int clearBtnY   = panelY + P_H - PAD - 14;

        calSearchBox = new EditBox(font, rCX, rCY, RP_W - PAD * 2, 14, Component.empty());
        calSearchBox.setMaxLength(40);
        calSearchBox.setHint(Component.literal("Search enchantments..."));
        calSearchBox.setResponder(this::onCalSearch);
        addRenderableWidget(calSearchBox);

        for (int i = 0; i < 5; i++) {
            final int lvl = i + 1;
            calLevelBtns[i] = Button.builder(Component.literal(String.valueOf(lvl)),
                    b -> calSelectedLevel = lvl)
                    .bounds(rCX + 44 + i * 22, calLevelRowY, 18, 14).build();
            addRenderableWidget(calLevelBtns[i]);
        }

        calPriceBox = new EditBox(font, rCX + 68, calPriceRowY, RP_W - PAD * 2 - 68, 14, Component.empty());
        calPriceBox.setMaxLength(4);
        calPriceBox.setHint(Component.literal("any"));
        addRenderableWidget(calPriceBox);

        addRenderableWidget(Button.builder(Component.literal("+ Add Observation"),
                b -> addCalibrationObservation())
                .bounds(rCX, addBtnY, RP_W - PAD * 2, 14).build());

        addRenderableWidget(Button.builder(Component.literal("+ Add 'No Trade'"),
                b -> {
                    CalibrationListStore.add(CalibrationListStore.Observation.missing());
                    calResultMsg = "";
                    VtpPersistence.saveCurrentWorldState();
                })
                .bounds(rCX, noTradeBtnY, RP_W - PAD * 2, 14).build());

        addRenderableWidget(Button.builder(Component.literal("Find & Apply"),
                b -> runFindAndApply())
                .bounds(rCX, findBtnY, RP_W - PAD * 2, 14).build());

        addRenderableWidget(Button.builder(Component.literal("Clear All"),
                b -> {
                    CalibrationListStore.clear();
                    calResultMsg = "";
                    calLastMatches.clear();
                    calObsScrollOffset = 0;
                    VtpPersistence.saveCurrentWorldState();
                })
                .bounds(lCX, clearBtnY, LP_W - PAD * 2, 14).build());
    }

    private void onCalSearch(String query) {
        calScrollOffset = 0;
        String q = query.toLowerCase().replace(" ", "_").trim();
        calFilteredEnchants = q.isEmpty()
                ? new ArrayList<>(calAllEnchants)
                : calAllEnchants.stream()
                        .filter(h -> enchPath(h).contains(q))
                        .collect(Collectors.toCollection(ArrayList::new));
        if (calSelectedIdx >= calFilteredEnchants.size()) calSelectedIdx = -1;
    }

    private void addCalibrationObservation() {
        if (calSelectedIdx < 0 || calSelectedIdx >= calFilteredEnchants.size()) {
            calResultMsg = "Select an enchantment first.";
            return;
        }
        calFilteredEnchants.get(calSelectedIdx).unwrapKey().ifPresent(key -> {
            int price = -1;
            try {
                String s = calPriceBox.getValue().trim();
                if (!s.isEmpty()) price = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
            CalibrationListStore.add(CalibrationListStore.Observation.ofTrade(key, calSelectedLevel, price));
            calResultMsg = "";
            calLastMatches.clear();
            VtpPersistence.saveCurrentWorldState();
        });
    }

    private void runFindAndApply() {
        if (CalibrationListStore.size() == 0) {
            calResultMsg = "Add at least one observation.";
            return;
        }
        Entity crosshair = Minecraft.getInstance().crosshairPickEntity;
        Villager villager = crosshair instanceof Villager v ? v : null;
        VillagerData data = villager != null ? villager.getVillagerData()
                : VillagerTracker.getLastVillagerData().orElse(null);
        if (data == null) {
            calResultMsg = "Look at a villager first.";
            return;
        }
        var profKey = data.profession().unwrapKey();
        if (profKey.isEmpty()
                || profKey.get().equals(VillagerProfession.NONE)
                || profKey.get().equals(VillagerProfession.NITWIT)) {
            calResultMsg = "Villager has no profession.";
            return;
        }
        if (SeedProvider.getSeed().isEmpty()) {
            calResultMsg = "No seed set.";
            return;
        }

        calLastMatches = PredictionEngine.findCalibrationSequence(
                SeedProvider.getSeed().getAsLong(),
                data.profession(), data.level(),
                CalibrationListStore.toSpecs());

        if (calLastMatches.isEmpty()) {
            calResultMsg = "No match in " + PredictionEngine.getMaxRerolls() + " rounds.";
            return;
        }
        if (calLastMatches.size() == 1) {
            int round = calLastMatches.get(0);
            PredictionEngine.applyCalibration(data.profession(), data.level(), round);
            if (villager != null) {
                VillagerTracker.resetAfterCalibrate(villager.getUUID(), data, round);
            }
            VtpPersistence.saveGlobalSettings();
            calResultMsg = "Calibrated! Offset = " + round;
        } else {
            StringBuilder sb = new StringBuilder(calLastMatches.size() + " matches: ");
            for (int i = 0; i < calLastMatches.size() && i < 6; i++) {
                if (i > 0) sb.append(", ");
                sb.append(calLastMatches.get(i));
            }
            if (calLastMatches.size() > 6) sb.append("...");
            sb.append(" — add more observations.");
            calResultMsg = sb.toString();
        }
    }

    private void renderCalibrateContent(GuiGraphicsExtractor g, VtpTheme t, int mx, int my) {
        int panelY = wy + TITLE_H + TAB_H;

        // ── Left panel: observations list ─────────────────────────────────────
        panel(g, t, wx + PAD, panelY, LP_W, P_H, "OBSERVATIONS");

        VillagerData data = VillagerTracker.getLastVillagerData().orElse(null);
        String header;
        if (data == null) {
            header = "Look at a villager";
        } else {
            String profName = data.profession().unwrapKey()
                    .map(k -> formatNameStr(k.identifier().getPath())).orElse("?");
            header = profName + "  Lv." + data.level();
        }
        g.text(font, header, lCX, lCY, t.muted);

        g.fill(calObsListX - 1, calObsListY - 1, calObsListX + calObsListW + 1, calObsListY + calObsListH + 1, t.border);
        g.fill(calObsListX, calObsListY, calObsListX + calObsListW, calObsListY + calObsListH, t.listBg);

        List<CalibrationListStore.Observation> obs = CalibrationListStore.getAll();
        if (obs.isEmpty()) {
            g.centeredText(font, "No observations yet",
                    calObsListX + calObsListW / 2, calObsListY + calObsListH / 2 - 4, t.muted);
        } else {
            int maxScroll = Math.max(0, obs.size() - CAL_OBS_ROWS);
            if (calObsScrollOffset > maxScroll) calObsScrollOffset = maxScroll;
            int xBtnW = font.width("\u00d7") + 4;

            for (int i = 0; i < CAL_OBS_ROWS; i++) {
                int idx = calObsScrollOffset + i;
                if (idx >= obs.size()) break;
                CalibrationListStore.Observation o = obs.get(idx);
                int rowY = calObsListY + i * CAL_OBS_H;
                boolean hov = mx >= calObsListX && mx < calObsListX + calObsListW
                        && my >= rowY && my < rowY + CAL_OBS_H;
                if (hov) g.fill(calObsListX, rowY, calObsListX + calObsListW, rowY + CAL_OBS_H, t.listHov);

                String label;
                if (o.noTrade()) {
                    label = "#" + (idx + 1) + "  No trade";
                } else {
                    String name = formatNameStr(o.enchantment().identifier().getPath());
                    label = "#" + (idx + 1) + "  " + name + " "
                            + VillagerHudRenderer.fmtLvl(o.enchantmentLevel());
                    if (o.price() >= 0) label += "  (" + o.price() + "e)";
                }
                int textAvail = calObsListW - xBtnW - 6;
                if (font.width(label) > textAvail) {
                    label = font.plainSubstrByWidth(label, textAvail - font.width("\u2026")) + "\u2026";
                }
                g.text(font, label, calObsListX + 3, rowY + 2, t.text);
                g.text(font, "\u00d7", calObsListX + calObsListW - xBtnW + 1, rowY + 2, t.error);
            }

            if (obs.size() > CAL_OBS_ROWS) {
                int thumbH = Math.max(6, calObsListH * CAL_OBS_ROWS / obs.size());
                int thumbY = calObsListY + (calObsListH - thumbH) * calObsScrollOffset / Math.max(1, maxScroll);
                g.fill(calObsListX + calObsListW - 3, calObsListY,
                        calObsListX + calObsListW, calObsListY + calObsListH, t.div);
                g.fill(calObsListX + calObsListW - 3, thumbY,
                        calObsListX + calObsListW, thumbY + thumbH, t.muted);
            }
        }

        // ── Right panel: manual entry ─────────────────────────────────────────
        panel(g, t, wx + PAD + LP_W + PAD, panelY, RP_W, P_H, "ADD OBSERVATION");

        g.fill(calListX - 1, calListY - 1, calListX + calListW + 1, calListY + calListH + 1, t.border);
        g.fill(calListX, calListY, calListX + calListW, calListY + calListH, t.listBg);

        for (int i = 0; i < CAL_LIST_ROWS; i++) {
            int idx = calScrollOffset + i;
            if (idx >= calFilteredEnchants.size()) break;
            int iY  = calListY + i * ITEM_H;
            boolean sel = idx == calSelectedIdx;
            boolean hov = mx >= calListX && mx < calListX + calListW && my >= iY && my < iY + ITEM_H;
            if (sel) {
                g.fill(calListX, iY, calListX + calListW, iY + ITEM_H, t.listSel);
                g.fill(calListX, iY, calListX + 2, iY + ITEM_H, t.accent);
            } else if (hov) {
                g.fill(calListX, iY, calListX + calListW, iY + ITEM_H, t.listHov);
            }
            g.text(font, formatName(calFilteredEnchants.get(idx)),
                    calListX + 5, iY + 2, sel ? t.accent : t.text);
        }

        if (calFilteredEnchants.size() > CAL_LIST_ROWS) {
            int thumbH = Math.max(6, calListH * CAL_LIST_ROWS / calFilteredEnchants.size());
            int maxSc  = Math.max(1, calFilteredEnchants.size() - CAL_LIST_ROWS);
            int thumbY = calListY + (calListH - thumbH) * calScrollOffset / maxSc;
            g.fill(calListX + calListW - 3, calListY, calListX + calListW, calListY + calListH, t.div);
            g.fill(calListX + calListW - 3, thumbY, calListX + calListW, thumbY + thumbH, t.muted);
        }
        if (calFilteredEnchants.isEmpty()) {
            g.centeredText(font, "No results", calListX + calListW / 2, calListY + calListH / 2 - 4, t.muted);
        }

        g.text(font, "Level:",     rCX, calLevelRowY + 3, t.text);
        for (int i = 0; i < 5; i++) {
            if (calSelectedLevel == i + 1) {
                int bx = rCX + 44 + i * 22;
                g.fill(bx - 1, calLevelRowY + 15, bx + 19, calLevelRowY + 17, t.accent);
            }
        }
        g.text(font, "Price:",     rCX, calPriceRowY + 3, t.text);

        if (!calResultMsg.isEmpty()) {
            int msgY = panelY + P_H - PAD - 14 - 14;
            int color = calResultMsg.startsWith("Calibrated") ? t.accent
                    : calResultMsg.startsWith("No match") || calResultMsg.startsWith("Look")
                            || calResultMsg.startsWith("Add") || calResultMsg.startsWith("Select")
                            || calResultMsg.startsWith("Villager") || calResultMsg.startsWith("No seed")
                                    ? t.error : t.text;
            String shown = calResultMsg;
            int avail = RP_W - PAD * 2;
            if (font.width(shown) > avail) {
                shown = font.plainSubstrByWidth(shown, avail - font.width("\u2026")) + "\u2026";
            }
            g.text(font, shown, rCX, msgY, color);
        }
    }

    /**
     * Blank content area for tabs not yet implemented.
     * Add a new case in extractRenderState + init to build out a tab.
     */
    private void renderStubContent(GuiGraphicsExtractor g, VtpTheme t, String tabName) {
        int panelY = wy + TITLE_H + TAB_H;
        g.fill(wx + 1, panelY, wx + WIN_W - 1, wy + WIN_H - 26, t.win);
        g.centeredText(font, tabName, wx + WIN_W / 2, panelY + P_H / 2 - 4, t.muted);
    }

    // ── Input handling ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mx = event.x(), my = event.y();

        // Tab bar
        int tabBarY = wy + TITLE_H;
        if (my >= tabBarY && my < tabBarY + TAB_H) {
            for (int i = 0; i < TAB_LABELS.length; i++) {
                if (mx >= tabX[i] && mx < tabX[i] + tabW[i]) {
                    if (activeTab != i) {
                        activeTab = i;
                        rebuildWidgets();
                    }
                    return true;
                }
            }
        }

        if (activeTab == TAB_SETTINGS) {
            if (mx >= listX && mx < listX + listW - 3 && my >= listY && my < listY + listH) {
                int idx = scrollOffset + (int) ((my - listY) / ITEM_H);
                if (idx >= 0 && idx < filteredEnchants.size()) {
                    selectedIdx = idx;
                    return true;
                }
            }
            int tgtAreaEnd = tgtListY + TGT_ROWS * TGT_H;
            if (mx >= rCX && mx < rCX + listW && my >= tgtListY && my < tgtAreaEnd) {
                int row = (int) ((my - tgtListY) / TGT_H);
                int idx = tgtScrollOffset + row;
                List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
                if (idx < tgts.size()) {
                    int xBtnX = rCX + listW - font.width("\u00d7") - 3;
                    if (mx >= xBtnX) {
                        VillagerHudRenderer.removeTarget(idx);
                        VtpPersistence.saveCurrentWorldState();
                        tgtScrollOffset = Math.max(0, Math.min(tgtScrollOffset,
                                VillagerHudRenderer.getTargets().size() - TGT_ROWS));
                        return true;
                    }
                }
            }
        }

        if (activeTab == TAB_SEQUENCE) {
            // +/- offset buttons
            if (my >= seqBtnRowY && my < seqBtnRowY + SEQ_BTN_H) {
                int btnW = 9;
                for (int c = 0; c < SEQ_COLS; c++) {
                    if (seqTsKeys[c] == null) continue;
                    int cx        = seqBtnInnerX + c * seqBtnColW;
                    int minusBtnX = cx + 2;
                    int plusBtnX  = cx + seqBtnColW - btnW - 2;
                    if (mx >= minusBtnX && mx < minusBtnX + btnW) {
                        int cur = PredictionEngine.getOffset(seqTsKeys[c]);
                        if (cur > 0) PredictionEngine.setOffset(seqTsKeys[c], cur - 1);
                        VtpPersistence.saveGlobalSettings();
                        buildSequenceList();
                        return true;
                    }
                    if (mx >= plusBtnX && mx < plusBtnX + btnW) {
                        PredictionEngine.setOffset(seqTsKeys[c], PredictionEngine.getOffset(seqTsKeys[c]) + 1);
                        VtpPersistence.saveGlobalSettings();
                        buildSequenceList();
                        return true;
                    }
                }
            }
        }

        if (activeTab == TAB_CALIBRATE) {
            if (mx >= calListX && mx < calListX + calListW - 3
                    && my >= calListY && my < calListY + calListH) {
                int idx = calScrollOffset + (int) ((my - calListY) / ITEM_H);
                if (idx >= 0 && idx < calFilteredEnchants.size()) {
                    calSelectedIdx = idx;
                    return true;
                }
            }
            if (mx >= calObsListX && mx < calObsListX + calObsListW
                    && my >= calObsListY && my < calObsListY + calObsListH) {
                int row = (int) ((my - calObsListY) / CAL_OBS_H);
                int idx = calObsScrollOffset + row;
                if (idx >= 0 && idx < CalibrationListStore.size()) {
                    int xBtnW = font.width("\u00d7") + 4;
                    if (mx >= calObsListX + calObsListW - xBtnW) {
                        CalibrationListStore.remove(idx);
                        calLastMatches.clear();
                        calResultMsg = "";
                        int max = Math.max(0, CalibrationListStore.size() - CAL_OBS_ROWS);
                        if (calObsScrollOffset > max) calObsScrollOffset = max;
                        VtpPersistence.saveCurrentWorldState();
                        return true;
                    }
                }
            }
        }

        if (activeTab == TAB_TRADE_LIST) {
            // Profession list click
            int profInnerW = LP_W - PAD * 2;
            if (mx >= lCX - 2 && mx < lCX + profInnerW && my >= lCY) {
                int clicked = (int) ((my - lCY) / TL_PROF_H);
                if (clicked >= 0 && clicked < tlProfList.size()) {
                    if (tlSelProf != clicked) {
                        tlSelProf = clicked;
                        tlScroll  = 0;
                        buildDisplayList();
                    }
                    return true;
                }
            }
            // Level filter click
            int filterY = rCY;
            if (my >= filterY && my < filterY + TL_FILTER_H) {
                for (int i = 0; i < filterBtnX.length; i++) {
                    if (mx >= filterBtnX[i] && mx < filterBtnX[i] + filterBtnW[i]) {
                        if (tlLevelFilter != i) {
                            tlLevelFilter = i;
                            tlScroll      = 0;
                            buildDisplayList();
                        }
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (activeTab == TAB_SETTINGS) {
            if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
                int max = Math.max(0, filteredEnchants.size() - LIST_ROWS);
                scrollOffset = (int) Math.max(0, Math.min(scrollOffset - Math.signum(scrollY), max));
                return true;
            }
            if (mx >= rCX && mx < rCX + listW && my >= tgtListY && my < tgtListY + TGT_ROWS * TGT_H) {
                int max = Math.max(0, VillagerHudRenderer.getTargets().size() - TGT_ROWS);
                tgtScrollOffset = (int) Math.max(0, Math.min(tgtScrollOffset - Math.signum(scrollY), max));
                return true;
            }
        }
        if (activeTab == TAB_SEQUENCE) {
            int panelY   = wy + TITLE_H + TAB_H;
            int colHdrY  = panelY + PHDR_H + 1;
            int seqListY = colHdrY + SEQ_COL_HDR_H + SEQ_BTN_H;
            int seqAreaH = panelY + P_H - PAD - seqListY;
            if (seqBtnColW > 0
                    && mx >= seqBtnInnerX && mx < seqBtnInnerX + seqBtnColW * SEQ_COLS
                    && my >= seqListY && my < seqListY + seqAreaH) {
                int c = (int) ((mx - seqBtnInnerX) / seqBtnColW);
                if (c >= 0 && c < SEQ_COLS && c < seqColumns.size()) {
                    int visRows      = seqAreaH / SEQ_ROW_H;
                    int colMaxScroll = Math.max(0, seqColumns.get(c).size() - visRows);
                    seqScroll[c] = (int) Math.max(0, Math.min(seqScroll[c] - Math.signum(scrollY), colMaxScroll));
                }
                return true;
            }
        }
        if (activeTab == TAB_CALIBRATE) {
            if (mx >= calListX && mx < calListX + calListW
                    && my >= calListY && my < calListY + calListH) {
                int max = Math.max(0, calFilteredEnchants.size() - CAL_LIST_ROWS);
                calScrollOffset = (int) Math.max(0, Math.min(calScrollOffset - Math.signum(scrollY), max));
                return true;
            }
            if (mx >= calObsListX && mx < calObsListX + calObsListW
                    && my >= calObsListY && my < calObsListY + calObsListH) {
                int max = Math.max(0, CalibrationListStore.size() - CAL_OBS_ROWS);
                calObsScrollOffset = (int) Math.max(0, Math.min(calObsScrollOffset - Math.signum(scrollY), max));
                return true;
            }
        }

        if (activeTab == TAB_TRADE_LIST) {
            int tradeListY = rCY + TL_FILTER_H;
            int tradeAreaH = (P_H - PHDR_H - PAD * 2) - TL_FILTER_H;
            if (mx >= rCX && mx < rCX + RP_W - PAD * 2
                    && my >= tradeListY && my < tradeListY + tradeAreaH) {
                int visRows  = tradeAreaH / TL_ROW_H;
                int maxScroll = Math.max(0, tlDisplay.size() - visRows);
                tlScroll = (int) Math.max(0, Math.min(tlScroll - Math.signum(scrollY), maxScroll));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (arAwaitingRebind && activeTab == TAB_AUTO_REROLL) {
            KeyMapping km = VtpKeybinds.getAutoRerollKey();
            if (km != null) {
                if (event.key() == InputConstants.KEY_ESCAPE) {
                    km.setKey(InputConstants.UNKNOWN);
                } else {
                    km.setKey(InputConstants.getKey(event));
                }
                KeyMapping.resetMapping();
            }
            arAwaitingRebind = false;
            if (arHotkeyBtn != null) arHotkeyBtn.setMessage(Component.literal(hotkeyLabel()));
            Minecraft.getInstance().options.save();
            VtpPersistence.saveGlobalSettings();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Trade list helpers ────────────────────────────────────────────────────

    /** Load all tradeable professions from the registry using hardcoded vanilla IDs. */
    private void loadProfessions() {
        Minecraft mc = Minecraft.getInstance();
        RegistryAccess ra = mc.getSingleplayerServer() != null
                ? mc.getSingleplayerServer().registryAccess()
                : (mc.level != null ? mc.level.registryAccess() : null);
        if (ra == null) return;
        Optional<Registry<VillagerProfession>> regOpt = ra.lookup(Registries.VILLAGER_PROFESSION);
        if (regOpt.isEmpty()) return;
        Registry<VillagerProfession> reg = regOpt.get();
        for (String id : VANILLA_PROFESSION_IDS) {
            ResourceKey<VillagerProfession> key = ResourceKey.create(
                    Registries.VILLAGER_PROFESSION,
                    Identifier.fromNamespaceAndPath("minecraft", id));
            reg.getOptional(key).ifPresent(prof -> {
                tlProfNames.add(formatNameStr(id));
                tlProfList.add(prof);
            });
        }
    }

    /** Rebuild tlDisplay from current profession + level filter selection. */
    private void buildDisplayList() {
        tlDisplay = new ArrayList<>();
        if (tlProfList.isEmpty() || tlSelProf >= tlProfList.size()) return;

        VillagerProfession prof = tlProfList.get(tlSelProf);

        if (tlLevelFilter == 0) {
            // All levels — show grouped headers
            for (int lvl = 1; lvl <= 5; lvl++) {
                List<TradeRow> rows = buildTradeRows(prof, lvl);
                if (!rows.isEmpty()) {
                    tlDisplay.add(DisplayEntry.ofHeader(LEVEL_NAMES[lvl] + "  (Level " + lvl + ")"));
                    for (TradeRow r : rows) tlDisplay.add(DisplayEntry.ofTrade(r));
                }
            }
        } else {
            // Specific level only
            List<TradeRow> rows = buildTradeRows(prof, tlLevelFilter);
            if (rows.isEmpty()) {
                tlDisplay.add(DisplayEntry.ofHeader("No trades at this level"));
            } else {
                for (TradeRow r : rows) tlDisplay.add(DisplayEntry.ofTrade(r));
            }
        }
    }

    /**
     * Load all VillagerTrade entries for a profession+level and convert them to
     * TradeRow display records.  Results are cached.
     */
    private List<TradeRow> buildTradeRows(VillagerProfession prof, int level) {
        ResourceKey<TradeSet> tsKey = prof.getTrades(level);
        String cacheKey = (tsKey != null ? tsKey.toString() : "null") + ":" + level;
        if (tlCache.containsKey(cacheKey)) return tlCache.get(cacheKey);
        if (tsKey == null) { tlCache.put(cacheKey, List.of()); return List.of(); }

        Optional<TradeSet> tsOpt = TradeDataLoader.getTradeSet(tsKey);
        if (tsOpt.isEmpty()) { tlCache.put(cacheKey, List.of()); return List.of(); }

        List<Holder<VillagerTrade>> trades = TradeDataLoader.getTradesFromSet(tsOpt.get());
        List<TradeRow> rows = new ArrayList<>();

        for (Holder<VillagerTrade> tradeHolder : trades) {
            try {
                VillagerTradeAccessor acc = (VillagerTradeAccessor) tradeHolder.value();

                ItemStack c1  = tradeCostToStack(acc.vtp$getWants());
                ItemStack c2  = acc.vtp$getAdditionalWants()
                        .map(VtpSettingsScreen::tradeCostToStack)
                        .orElse(ItemStack.EMPTY);
                ItemStack res = new ItemStack(
                        acc.vtp$getGives().item().value(),
                        acc.vtp$getGives().count());

                boolean enchBook = acc.vtp$getGivenItemModifiers().stream()
                        .anyMatch(f -> f instanceof EnchantRandomlyFunction);

                rows.add(new TradeRow(c1, c2, res, enchBook));
            } catch (Exception ignored) {
                // Skip any trade that fails to decode gracefully
            }
        }

        tlCache.put(cacheKey, rows);
        return rows;
    }

    private static ItemStack tradeCostToStack(TradeCost cost) {
        int count = cost.count() instanceof ConstantValue cv ? (int) cv.value() : 1;
        return new ItemStack(cost.item().value(), Math.max(1, count));
    }

    // ── Level preview computation ─────────────────────────────────────────────

    private void computeLevelPreview(VillagerData data) {
        lvlLines = new ArrayList<>();
        Holder<VillagerProfession> profession = data.profession();
        int currentLevel = data.level();

        lvlProfName = profession.unwrapKey()
                .map(k -> formatNameStr(k.identifier().getPath()))
                .orElse(null);

        if (lvlProfName == null
                || profession.unwrapKey().map(k -> k.equals(VillagerProfession.NONE)
                        || k.equals(VillagerProfession.NITWIT)).orElse(true)) {
            lvlProfName = null;
            return;
        }

        if (SeedProvider.getSeed().isEmpty()) {
            for (int lvl = 1; lvl <= 5; lvl++) {
                if (TradeDataLoader.getTradeSetKey(profession, lvl) == null) continue;
                lvlLines.add(new LvlLine("Lv." + lvl + ":  no seed set", lvl == currentLevel));
            }
            return;
        }
        long seed = SeedProvider.getSeed().getAsLong();
        java.util.UUID lastUuid = VillagerTracker.getLastVillagerUuid();

        for (int lvl = 1; lvl <= 5; lvl++) {
            ResourceKey<TradeSet> tsKey = TradeDataLoader.getTradeSetKey(profession, lvl);
            if (tsKey == null) continue;

            StringBuilder sb = new StringBuilder("Lv." + lvl + ": ");
            boolean any = false;

            java.util.List<net.manaz.vtp.render.VillagerTracker.OfferData> saved =
                    lastUuid != null ? VillagerTracker.getSavedLevelOffers(lastUuid, lvl)
                                    : java.util.List.of();
            if (!saved.isEmpty()) {
                for (net.manaz.vtp.render.VillagerTracker.OfferData od : saved) {
                    String name = formatNameStr(
                            od.enchantmentKey().substring(od.enchantmentKey().lastIndexOf(':') + 1));
                    if (any) sb.append("  ");
                    sb.append(name).append(' ').append(VillagerHudRenderer.fmtLvl(od.enchantmentLevel()));
                    if (od.additionalCost() > 0) sb.append(" (+").append(od.additionalCost()).append(')');
                    any = true;
                }
            } else {
                int startRound = lastUuid != null
                        ? VillagerTracker.getVillagerRoundForLevel(lastUuid, profession, lvl)
                        : PredictionEngine.getOffset(tsKey)
                                + VillagerTracker.getTradeSetTotalForLevel(profession, lvl);
                List<List<TradeSimulator.SimulatedOffer>> rounds =
                        PredictionEngine.getUpcomingRounds(seed, profession, lvl, startRound, 1);
                if (!rounds.isEmpty()) {
                    // Only persist offers for levels already reached — future levels must
                    // re-simulate on each reroll so they track the global counter rather
                    // than freezing on the first observation.
                    if (lastUuid != null && lvl <= currentLevel) {
                        VillagerTracker.saveLevelOffers(lastUuid, lvl, rounds.get(0));
                    }
                    for (TradeSimulator.SimulatedOffer offer : rounds.get(0)) {
                        if (offer.enchantment().isPresent()) {
                            TradeSimulator.EnchantmentResult ench = offer.enchantment().get();
                            String name = ench.enchantment().unwrapKey()
                                    .map(k -> formatNameStr(k.identifier().getPath())).orElse("?");
                            if (any) sb.append("  ");
                            sb.append(name).append(' ').append(VillagerHudRenderer.fmtLvl(ench.level()));
                            if (ench.additionalCost() > 0) sb.append(" (+").append(ench.additionalCost()).append(')');
                            any = true;
                        }
                    }
                }
            }

            if (!any) sb.append("—");
            lvlLines.add(new LvlLine(sb.toString(), lvl == currentLevel));
        }
    }

    // ── Settings actions ──────────────────────────────────────────────────────

    private void onSearch(String query) {
        scrollOffset = 0;
        String q = query.toLowerCase().replace(" ", "_").trim();
        filteredEnchants = q.isEmpty()
                ? new ArrayList<>(allEnchants)
                : allEnchants.stream()
                        .filter(h -> enchPath(h).contains(q))
                        .collect(Collectors.toCollection(ArrayList::new));
        if (selectedIdx >= filteredEnchants.size()) selectedIdx = -1;
    }

    private void applyTarget() {
        if (selectedIdx < 0 || selectedIdx >= filteredEnchants.size()) return;
        filteredEnchants.get(selectedIdx).unwrapKey().ifPresent(key -> {
            int maxPrice = -1;
            try {
                String s = maxPriceBox.getValue().trim();
                if (!s.isEmpty()) maxPrice = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {}
            VillagerHudRenderer.addTarget(new EnchantmentTarget(key, selectedLevel, maxPrice));
            VtpPersistence.saveCurrentWorldState();
        });
    }

    private void saveAndClose() {
        VillagerHudRenderer.setEnabled(overlayEnabled);
        if (maxRerollsBox != null) {
            try {
                String s = maxRerollsBox.getValue().trim();
                if (!s.isEmpty()) {
                    int v = Integer.parseInt(s);
                    if (v >= 1 && v <= 10000) PredictionEngine.setMaxRerolls(v);
                }
            } catch (NumberFormatException ignored) {}
        }
        saveAutoRerollInputs();
        VtpPersistence.saveGlobalSettings();
        onClose();
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private void panel(GuiGraphicsExtractor g, VtpTheme t, int x, int y, int w, int h, String title) {
        g.fill(x, y, x + w, y + h, t.panel);
        outline(g, x, y, w, h, t.border);
        g.fill(x + 1, y + 1, x + w - 1, y + PHDR_H, t.phdr);
        g.text(font, title, x + PAD, y + 3, t.accent);
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int col) {
        g.fill(x,         y,         x + w,     y + 1,     col);
        g.fill(x,         y + h - 1, x + w,     y + h,     col);
        g.fill(x,         y + 1,     x + 1,     y + h - 1, col);
        g.fill(x + w - 1, y + 1,     x + w,     y + h - 1, col);
    }

    // ── String helpers ────────────────────────────────────────────────────────

    private String toggleLabel()              { return overlayEnabled ? "● ON" : "○ OFF"; }
    private static String numeralsLabel()     { return VillagerHudRenderer.isRomanNumerals() ? "Roman" : "Arabic"; }
    private String enchPath(Holder<Enchantment> h) { return h.unwrapKey().map(k -> k.identifier().getPath()).orElse(""); }
    private String formatName(Holder<Enchantment> h) { return formatNameStr(enchPath(h)); }

    private static String formatNameStr(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private String rerollsLabel(EnchantmentTarget tgt) {
        Optional<VillagerData> dataOpt = VillagerTracker.getLastVillagerData();
        if (dataOpt.isEmpty() || SeedProvider.getSeed().isEmpty()) return "?";
        VillagerData data = dataOpt.get();
        var profKey = data.profession().unwrapKey();
        if (profKey.isEmpty()
                || profKey.get().equals(VillagerProfession.NONE)
                || profKey.get().equals(VillagerProfession.NITWIT)) return "?";

        long seed = SeedProvider.getSeed().getAsLong();
        ResourceKey<TradeSet> tsKey = TradeDataLoader.getTradeSetKey(data.profession(), data.level());
        int typeOffset = tsKey != null ? PredictionEngine.getOffset(tsKey) : 0;
        int tradeSetTotal = VillagerTracker.getTradeSetTotal(data);

        java.util.UUID lastUuid = VillagerTracker.getLastVillagerUuid();
        int nowRound = lastUuid != null
                ? VillagerTracker.getCurrentRound(lastUuid).orElse(typeOffset + tradeSetTotal)
                : typeOffset + tradeSetTotal;

        PredictionResult result = PredictionEngine.predictFrom(
                seed, data.profession(), data.level(), tgt, nowRound);
        if (!result.found()) return ">" + PredictionEngine.getMaxRerolls();
        int remaining = result.rerollsNeeded().get() - nowRound;
        return remaining <= 0 ? "now!" : remaining + " rerolls";
    }
}
