# Villager Trading Predictor

Client-side Fabric mod for Minecraft **26.1.x, 26.2 and 26.3**, with Java 25. Predicts librarian enchanted-book trades and tracks observed offers.

Replace the previous VTP JAR with the single `VTP-<version>-mc26-universal.jar` from the root `build/libs` directory, together with Fabric Loader 0.19.5 or newer and Fabric API for your Minecraft version. Fabric automatically selects the matching implementation inside the JAR. The original version-specific projects are preserved; all ongoing development belongs in this project.

Open this folder as one Gradle project in IntelliJ IDEA. Feature code and assets are shared in `src/main`; the small API and mixin adapters are in `src/compat`. See [DEVELOPMENT.md](DEVELOPMENT.md) for the build, test matrix and adding versions.

## How prediction works

Trade sequences are shared by their named RNG sequence, not owned by individual villagers.

- **Singleplayer:** reads immutable snapshots of the integrated server's current sequence state. Reading a snapshot does not generate offers or consume the game's RNG. Snapshots refresh about four times per second while a villager is selected.
- **Multiplayer:** loads vanilla trade definitions from the client's bundled game files and uses the synced enchantments, supplied seed, observed trade packets, and calibration. Other players, unloaded villagers, custom server trades, or altered random-sequence settings can invalidate estimates. An ambiguous observation remains marked **Uncertain**.
- **Current offers:** come from server observations. Future level offers are conditional forecasts; another villager can consume the shared sequence first.
- **Prices:** forecasts and calibration use base emerald prices, before demand, reputation, curing, or other player-specific adjustments.
- **Compatibility:** standard vanilla librarian trades are supported. Trade Rebalance, conditional trades, and unsupported random modifiers explicitly disable forecasts. A client cannot verify server-only datapacks; multiplayer assumes vanilla trade definitions.

## HUD and settings

The HUD uses Jade-inspired dark tooltip panels with fixed, matching widths, native item icons, and green accents. A compact **>** marks the current trade and stays in place while a pending forecast becomes an observed trade; **+1**, **+2**, and **+3** show further rerolls. The emerald column shows base prices. Locked villagers list each observed book on its own line, with its own price; remaining profession levels appear below under **Predicted**. Both panels fit together when level preview is enabled. Traded Novice villagers use the same locked layout once their XP is known. Ordinary synchronization details are available in the Settings footer or `/vtp status`; the HUD only shows a short warning when attention is needed.

Press **V** or run `/vtp` for dark themed Minecraft controls, scrollable item lists, searchable enchantments, and a layout that follows GUI Scale down to the vanilla minimum of 320×240 GUI pixels. Settings include a HUD size slider, a level-preview toggle, an accent-color picker, and HUD visibility rules. The old HUD Style option is retired; every panel uses the dark frame. Hover shortened text in the GUI for the full value. List controls support mouse scrolling, dragging the scrollbar, and keyboard navigation.
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

The World seed field in Settings retains the active seed. Valid numbers apply immediately as you type or paste and are saved for that server; invalid or unfinished input keeps the previous active seed. Seeds are hidden by default, with a **Show/Hide** button and a saved visibility preference. Hidden seeds are also masked in narration.

An unfinished Calibrate form keeps its enchantment, level, and price text for **60 seconds after leaving the menu**, so you can check the villager and return. Drafts belong to the same villager, profession level, seed, and session. Added observations remain in that villager's list until cleared or the context changes.

On multiplayer, **Add observation** below a librarian's trading screen records the received round and its base prices, including rounds without a book. Multiple books stay together in one round. Newly unlocked tiers can be captured when the previous level was observed. Reopening unchanged offers or clicking twice keeps one observation. Both this button and the manual form check calibration after each addition: a unique match applies automatically and reports **Sequence calibrated**. The merchant button also turns green and reads **Calibrated**; ambiguous matches request another reroll.

**V** opens settings while in the world. **Toggle VTP Overlay** is available in Options → Controls → Key Binds, unbound by default. HUD visibility can also be controlled in HUD settings or with `/vtp toggle`. Multiplayer sequence alignment uses observations and calibration.

## Saved state

World/server identity and seed jointly identify saves in `config/vtp`. Writes use an atomic replacement and background writer. Existing seed-only saves are retained; their targets are imported, but their inferred trade history and offsets require fresh observation/calibration. Reconnecting starts multiplayer alignment as unverified because the server may have advanced while disconnected.

## Testing and building

See [the in-game test guide](INGAME_TESTING.md).

With Java 25 selected:

```text
gradlew.bat build
gradlew.bat testAllClients
gradlew.bat :mc263:runClient
```

Each client test compares 20,000 trade rounds against vanilla, checks live rerolls, and exercises every UI tab at multiple GUI scales, including 320×240. Adapter tests cover native item modifiers, conditional-modifier rejection and minimum book prices. The multiplayer regression rebuilds registries through the remote login codecs, disables integrated-server access, and checks all five librarian tiers, 500 forecast rounds, five-book manual calibration, both observation workflows, the HUD, and session resets. Screenshots are saved under each test module's `build/run/clientGameTest/screenshots`. Test code is excluded from the release JAR. See [VALIDATION.md](VALIDATION.md) for the tested artifact and results.

`build` compiles and checks every supported target and produces one distributable JAR in the root `build/libs`. The version combines `mod_version` and an automatic `build_number` from `gradle.properties`: for example, `1.3.0.1`, then `1.3.0.2`. Each packaged universal JAR reserves the next number and is also saved in `releases/`, which survives `gradlew.bat clean`. Existing JARs are never overwritten. Run `gradlew.bat build testAllClients` together to build and test the same numbered artifact. The original projects are unchanged. `testAllClients` launches the packaged JAR on 26.1, 26.1.1, 26.1.2, 26.2 and 26.3, checking that only the matching nested implementation loads. Client tests require a graphical desktop and create isolated worlds under each test module's `build/run/clientGameTest` directory.

## License

This mod is proprietary software. All rights reserved.
You may not copy, modify, or distribute it without explicit permission from the author.
