# Villager Trading Predictor

Client-side Fabric mod for Minecraft 26.2 / Java 25. Predicts librarian enchanted-book trades and tracks observed offers.

## How prediction works

Trade sequences are shared by their named RNG sequence, not owned by individual villagers.

- **Singleplayer:** reads immutable snapshots of the integrated server's current sequence state. Reading a snapshot does not generate offers or consume the game's RNG. Snapshots refresh about four times per second while a villager is selected.
- **Multiplayer:** uses the supplied seed, observed trade packets, and calibration. Other players, unloaded villagers, custom server trades, or altered random-sequence settings can invalidate estimates. An ambiguous observation remains marked **Uncertain**.
- **Current offers:** come from server observations. Future level offers are conditional forecasts; another villager can consume the shared sequence first.
- **Prices:** forecasts and calibration use base emerald prices, before demand, reputation, curing, or other player-specific adjustments.
- **Compatibility:** standard vanilla librarian trades are supported. Trade Rebalance, conditional trades, and unsupported random modifiers explicitly disable forecasts. A client cannot verify server-only datapacks; multiplayer assumes vanilla trade definitions.

## HUD and settings

The HUD uses Jade-inspired dark tooltip panels with fixed, matching widths, native item icons, and green accents. A compact **>** marks the current trade and stays in place while a pending forecast becomes an observed trade; **+1**, **+2**, and **+3** show further rerolls. The emerald column shows base prices. Locked villagers list each observed book on its own line, with its own price; remaining profession levels appear below under **Predicted**. Both panels fit together when level preview is enabled. Traded Novice villagers use the same locked layout once their XP is known. Ordinary synchronization details are available in the Settings footer or `/vtp status`; the HUD only shows a short warning when attention is needed.

Open `/vtp` for dark themed Minecraft controls, scrollable item lists, searchable enchantments, and a layout that follows GUI Scale down to the vanilla minimum of 320×240 GUI pixels. Settings include a HUD size slider, a level-preview toggle, an accent-color picker, and HUD visibility rules. The old HUD Style option is retired; every panel uses the dark frame. Hover shortened text in the GUI for the full value. List controls support mouse scrolling, dragging the scrollbar, and keyboard navigation.
Panel borders stay still. Hover and selection highlights ease in; prediction text updates immediately. Targets use five level buttons, with unavailable enchantment levels disabled. The price slider has **Cheapest**, then every price from **5–64**; the adjacent box also accepts a typed maximum from 1–64. Cheapest updates for the selected enchantment and level and saves the calculated limit into the target: for example, Mending I costs 10 emeralds before discounts. The default maximum of 64 accepts all vanilla book prices. Unsupported trade definitions have no automatic minimum. See [the design notes and Jade references](UI_DESIGN.md).

Choose **Settings → HUD settings** for the centered panel containing the on/off toggle, level preview, size, and **When to hide the HUD** rules. Hover a visibility choice for an explanation. The size slider runs from 50–200% (default 100%). It previews the HUD immediately against the unblurred world; while dragging, the other controls disappear so you can judge the result. If no villager is selected, the screen identifies the preview as sample data. Apply saves all HUD options; Cancel discards them. The percentage multiplies Minecraft GUI Scale; large panels fit the screen, and the level preview appears when there is room. The merchant-screen target display keeps its native size.

The **Accent color** picker aligns with the Settings columns: a square saturation/brightness area, a horizontal hue strip, a live color preview, and English-labelled **Red**, **Green**, **Blue** (0–255) and **Hex** fields. Presets and Reset remain below. RGB, Hex and dragging stay synchronized; arrow keys make fine adjustments to the color area and hue strip. The layout adapts to smaller GUI scales. Valid edits apply immediately and save when leaving settings. Very dark colors use a lighter tint for accent text.

The visibility choices in **HUD settings** are:

- **Never hide** (default): retain the last selected villager's HUD.
- **After time**: hide 1–3600 seconds after you stop looking at or trading with that villager.
- **Out of range**: show only within 1–256 blocks of the selected villager; hide when it is unloaded.
- **Looking only**: show only while your crosshair points at the selected villager.

Visibility changes fade the entire HUD (including text and item icons) out over 250 ms and back in over 150 ms. Reversing direction continues from the current opacity. These rules affect the world HUD. F1 and other screens suppress it normally, except for the HUD settings preview; the merchant screen keeps its own compact target display. Hiding never clears predictions, targets, or tracking.

## Commands

| Command | Purpose |
| --- | --- |
| `/vtp` | Open settings |
| `/vtp seed <seed>` | Set the multiplayer seed; saved for that server |
| `/vtp target <enchantment> <level>` | Add a target |
| `/vtp maxrerolls <number>` | Set search depth (up to 10,000) |
| `/vtp toggle` | Toggle the HUD |
| `/vtp calibrate <enchantment> <level> [basePrice]; ...` | Match consecutive observed rounds |
| `/vtp calibrate none` | Match an observed round without an enchanted book |
| `/vtp calibrate confirm <round>` | Confirm a current candidate for the same villager |
| `/vtp confirm <round>` | Alias for calibration confirmation |
| `/vtp calibrate reset` | Clear multiplayer alignment |
| `/vtp status` | Show selected villager, tracking status, seed, and position |
| `/vtp debug true` / `false` | Enable or disable observation diagnostics in the game log |

Calibration is unnecessary in singleplayer. In multiplayer, open the villager's trades first. If more than one candidate remains, record more consecutive rerolls from the same villager and profession level. Separate rounds with commas or semicolons; multiple books from one trade screen are not separate rounds.

Up/down correction keys change the multiplayer forecast position only. They do not change observed offers, and are inactive inside screens and in singleplayer.

## Saved state

World/server identity and seed jointly identify saves in `config/vtp`. Writes use an atomic replacement and background writer. Existing seed-only saves are retained; their targets are imported, but their inferred trade history and offsets require fresh observation/calibration. Reconnecting starts multiplayer alignment as unverified because the server may have advanced while disconnected.

## Testing and building

See [the in-game test guide](INGAME_TESTING.md).

With Java 25 selected:

```text
gradlew.bat test
gradlew.bat runClientGameTest
gradlew.bat build
```

The client game test compares 20,000 trade rounds against vanilla, checks live rerolls, and exercises all UI tabs at multiple GUI scales, including 320×240. Screenshots are saved under `build/run/clientGameTest/screenshots`. It creates an isolated flat test world under `build/run/clientGameTest`. The test mod is not packaged in the release jar. A graphical desktop is required for client game tests. The normal build includes unit tests; client game tests run separately.

The existing build task increments `build_number` in `gradle.properties` after creating the jar in `build/libs`.

## License

This mod is proprietary software. All rights reserved.
You may not copy, modify, or distribute it without explicit permission from the author.
