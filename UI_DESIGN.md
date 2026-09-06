# Jade-style interface

VTP's HUD and settings share a dark tooltip palette, thin pixel borders, white shadowed text, native Minecraft item icons and green accents. All dimensions use Minecraft GUI coordinates. The default HUD opacity is 70%; full screens use 94% so their controls remain readable.

## Reference analysis

Reference: Snownee/Jade, branch `26.3-fabric`, commit `576c9ecacfaf57599099396a0d42f00b22660951`.

- [JadeIds](https://github.com/Snownee/Jade/blob/576c9ecacfaf57599099396a0d42f00b22660951/src/main/java/snownee/jade/api/JadeIds.java) selects `dark` as the default theme.
- [Dark theme](https://github.com/Snownee/Jade/blob/576c9ecacfaf57599099396a0d42f00b22660951/src/main/resources/assets/jade/jade_themes/dark.json) uses a dark tooltip sprite. Its background is #131313 with #383838 and #242424 frame edges.
- [WailaConfig](https://github.com/Snownee/Jade/blob/576c9ecacfaf57599099396a0d42f00b22660951/src/main/java/snownee/jade/impl/config/WailaConfig.java) defaults to 0.7 overlay alpha and enabled animation.
- [ColorPalette](https://github.com/Snownee/Jade/blob/576c9ecacfaf57599099396a0d42f00b22660951/src/main/java/snownee/jade/api/ui/ColorPalette.java) supplies white primary text, #A0A0A0 secondary text and #55FF55 success text.
- [JadeClient](https://github.com/Snownee/Jade/blob/576c9ecacfaf57599099396a0d42f00b22660951/src/main/java/snownee/jade/JadeClient.java) draws block-breaking progress along the tooltip's bottom edge, retaining and fading its last progress after mining ends.

VTP uses an original primitive renderer and Minecraft item stacks. It does not bundle Jade source, textures or a Jade dependency.

## Current interface

- HUD panels keep matching widths, item icons, and a stationary dark frame. Locked books and multi-book forecasts use individual rows with independent prices and target highlights. The Locked trades and Predicted panels fit together on small screens; fully unlocked masters have no future-tier panel.
- The old HUD Style setting and perimeter sweep animation have been removed.
- HUD visibility changes use a reversible 250 ms fade out / 150 ms fade in. Text, backgrounds and native item-atlas draws share the same opacity; per-draw item tinting leaves inventory and hotbar items unchanged.
- Settings embeds a Paint 3D-inspired picker aligned with the General controls. It fills the left settings column with a square saturation/brightness area, a horizontal hue strip, a live preview, English-labelled Red/Green/Blue inputs (0–255), Hex, eight presets and Reset. At limited screen heights, RGB labels move beside their inputs to preserve a square area and fit the 320×240 minimum. Valid RGB/Hex/HSV edits synchronize the other controls without resetting the edited field; incomplete or out-of-range text leaves the last valid accent in place. The hue is retained through achromatic edits. Valid accent edits apply immediately and save when leaving the page. Very dark choices keep their exact decoration color but use a readable tint for text.
- Targets has exactly five level buttons. Availability uses the selected enchantment's supported levels; switching to a lower-level enchantment corrects an invalid selection.
- One centered HUD settings screen contains on/off, level preview, a 50–200% size slider, and visibility controls. The HUD previews the draft size against the unblurred world; while dragging, only the slider stays in front. Apply commits the draft, Cancel discards it, and 100% resets size.
- The target price slider offers Cheapest, then 5–64, paired with a small numeric field. Cheapest follows the enchantment and level and reads minimum costs from supported librarian trade definitions, including doubled prices and native cost limits.
- The **When to hide the HUD** heading and explanatory tooltips introduce four exclusive visibility rules: Never hide (default), After time (1–3600 seconds since looking/trading), Out of range (1–256 blocks from the selected villager), and Looking only.
- Visibility state is reset with the session. Range checks use the live client villager position and hide unloaded villagers. These rules only control presentation, never tracking or prediction state.
- Native button/edit/list input, sounds, narration, and GUI scaling remain. Small hover and focus transitions are retained; frames do not animate.

## Validation

Run `gradlew.bat test runClientGameTest build` with Java 25.

Unit tests cover color parsing, RGB/HSV round trips, readable accent text, visibility thresholds, and hover timing. Minecraft tests exercise square picker alignment, horizontal hue dragging, RGB/hex synchronization, 0/255 channel endpoints, invalid RGB/hex input, achromatic hue retention, keyboard hue adjustments, presets, HUD draft cancellation, live resize previews, preference save/reload, all five level buttons, every price stop, typed prices, Mending I at 10, Efficiency V at 17, Frost Walker II at 16, and actual look/range/timeout/never visibility behavior. Dialog and tab layouts are checked at GUI scales 1/2/3, with screenshots at 320×240 GUI pixels. Locked-villager tests cover individual books/prices, future tiers, masters, traded Novices and fitting both panels at GUI scales and size settings. Prediction checks still compare 20,000 vanilla rounds and 12 live rerolls.

See [INGAME_TESTING.md](INGAME_TESTING.md) for the manual checklist.