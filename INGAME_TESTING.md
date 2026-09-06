# In-game regression guide

Use Minecraft 26.2, Fabric Loader 0.19.3 or newer, Fabric API for 26.2, and Java 25. Replace the old VTP jar with the new regular jar from `build/libs`; do not install the sources jar or two VTP versions together.

The HUD and settings use Minecraft's GUI Scale. The settings window adapts down to 320×240 GUI pixels; lists scroll when space is limited.

For the first check, use a new creative test world with Trade Rebalance disabled. Start with two adult, untraded villagers and two lecterns. Keep them separately enclosed so each uses its own lectern. Do not trade until the level-up test.

Run:

```text
/vtp target mending 1
/vtp debug true
/vtp status
```

Look at a librarian and allow about half a second for a snapshot. Open `/vtp` → Settings to check **Live server snapshot** under Tracking. The HUD keeps a permanent **Current** row: it shows the pending forecast before offers exist, then the observed offers after generation. **+1**, **+2**, and **+3** always mean that many further rerolls. Opening the trading screen is not necessarily the moment Minecraft generates trades.

## 1. One villager: rerolls and reopening

1. Look at villager A and note its pending or next forecast.
2. Open trades and compare the enchanted book and level with the prediction. A dash/no enchanted book is a valid result.
3. Close and reopen the same trades five times.
4. Break the lectern, wait for the villager to lose its profession, replace it, and wait for the librarian profession.
5. Open trades again. Repeat at least 20 times.

Expected: reopening unchanged offers does not consume another round. Each new generated set matches the appropriate forecast. Mending shows as available now only when it exists in the observed current offers or is the pending forecast. Compare prices in an undiscounted world; forecasts use base prices.

## 2. Shared sequence: alternate villagers

1. Generate and open A's trades, then leave A unchanged.
2. Reroll B five times, opening its trades each time.
3. Return to A.
4. Note A's **+1** forecast, then reroll A once.

Expected: A's **>** row remains its actual old trade. A's next forecast reflects B's consumption and matches A's new trade. Target counts must not point to intermediate rounds already used by B.

## 3. Day/night and offers generated before opening

1. Assign a profession without opening trades immediately.
2. Let the villager work, wait through a day/night transition, then inspect the forecast and open trades.
3. For a villager that has already generated offers, sleep or use the test world's time controls, then reopen unchanged trades.

Expected: singleplayer remains aligned through a server snapshot. Merely reopening, changing time, or restocking an already generated offer set does not falsely count a new set. If another villager generates trades, the shared future can legitimately change.

## 4. Level-up and historical offers

1. Trade with A until it levels up.
2. Reopen its trades and inspect the HUD and VTP settings.
3. Level B up to the same tier, then look at A again.

Expected: A's observed earlier trades stay unchanged. Future tiers appear under **Predicted** for locked villagers and may move after B consumes that tier's sequence. Tiers first encountered after they were already unlocked can show **not observed**, instead of inventing historical trades. A villager locked by trading does not receive a novice reroll countdown.

## 5. Reconnect, seed changes, and isolation

1. Set a target, observe trades, and quit normally.
2. Reopen the world and look at the same villager.
3. Open a different world with the same seed.
4. Return to the original world.

Expected: observed offers and targets survive reloading their own world. The second world does not inherit the first world's offsets, villagers, or per-world targets. Singleplayer obtains a fresh live snapshot. Old VTP seed-only files are retained, but their unreliable historical predictions are not imported.

On multiplayer, setting a different seed must clear old alignment before loading that seed's saved state. Re-entering the correct seed should recover its saved targets. Saved multiplayer positions start unverified after reconnecting.

## 6. Multiplayer calibration and drift detection

1. Join a vanilla 26.2 server whose seed you know and run `/vtp seed <seed>`.
2. Open an untraded librarian's trades. The HUD either finds an observed match or remains **Check alignment in /vtp**.
3. For calibration, record consecutive rerolls from that same librarian. Include a `none` observation for rounds without a book.
4. Use the GUI Calibrate tab or a command such as `/vtp calibrate efficiency 3; none; mending 1`, substituting the actual sequence you observed.
5. If several matches are reported, add observations or confirm one of the reported rounds with `/vtp confirm <round>`.
6. Try confirming a candidate while looking at a different villager, or after another reroll.
7. Have another player generate librarian trades, then open your librarian's next set.

Expected: calibration updates the shared next position once and preserves observed offers. An unrelated or stale confirmation is rejected. Reopening identical offers does not advance the position. If other-player activity produces a mismatch, VTP reconciles only a unique match; ambiguous cases remain **Check alignment in /vtp**. Client-only multiplayer cannot guarantee continuous synchronization with activity it never receives.

Optional command prices are **base prices**: `/vtp calibrate mending 1 12`. Do not substitute a discounted displayed price.

## 7. Unsupported configurations

Enable Trade Rebalance in a separate test world, then select a librarian.

Expected: the HUD reports that the forecast is unavailable/unsupported. It should not confidently display a vanilla prediction for those trades.

## 8. HUD and GUI layout

1. Change Options → Video Settings → GUI Scale between 1, 2, 3, and Auto where your resolution supports them. Open `/vtp` at each scale and visit all five tabs.
2. Check that every button stays on screen. Scroll long enchantment, sequence, profession, and observation lists; use the scrollbar or focus a list and use arrow keys, Page Up/Down, and Enter.
3. Add and remove a target. On multiplayer, choose an enchantment in Calibrate, add an observation and a no-book round, then search for matches.
4. Reroll a librarian while watching the HUD. **>** must stay in the same row; no label should alternate between `0` and `now`. Reopening unchanged trades must leave the rows unchanged.
5. Check that the HUD panels keep equal widths when enchantment names and prices change. Long names are shortened on the HUD and can be read in full by hovering their Sequence entries.
6. Toggle **Level preview** in Settings → **HUD settings**, then Apply. Press F1 and open other screens: the world HUD should stay hidden except for the preview in HUD settings. In a merchant screen, the compact target display should not cover inventory slots.
7. Hover clipped text for details. Normal synchronization messages belong in Settings, and emerald prices use the icon/price column.

Expected: dark gray frames, white shadowed text, green accents, item icons, equal-width HUD panels, stable row labels, and no overlap or clipping at any supported GUI scale. Accent changes apply immediately; HUD options use Apply/Cancel. **Done** also saves the search limit.
## 9. Accent color and level buttons

1. Open `/vtp` → Settings. The **HUD Style** option and moving frame outlines should be gone.
2. Open **Settings**. The square **Accent color** area and **HUD settings** button should share a top edge, with matching Accent color / General headings. Drag the square and the horizontal rainbow strip underneath, or use arrow keys while either is focused. Enter **Red 44**, **Green 51**, **Blue 164**: the preview should turn blue and **Hex** should read **#2C33A4**. Enter **#3399FF**: RGB should change to **51 / 153 / 255**. Presets should synchronize every field too. Highlights should update immediately; close settings and reopen the game to verify the color persists.
3. Try an incomplete or invalid Hex value, an empty RGB field, or a channel value of **256**: the text should turn red and the accent should keep its last valid color. **0** and **255** should work. Reset should restore the default green and synchronize RGB and Hex. Repeat at GUI scales 1, 2 and 3, including a small window: the color area should stay square, the English labels should remain readable, and no fields should overlap the footer.
4. In Targets, choose Efficiency: all five level buttons should be enabled. Select 5 and add it.
5. Switch to Mending: only 1 should be enabled and selected. Clicking a disabled button must not create an impossible target.
6. Choose Unbreaking: levels 1–3 should be enabled and 4–5 gray. Change the numeral format in Settings and verify the same five choices still work.

## 10. HUD visibility

When a visibility rule hides the HUD, its panels, text and icons should fade together over about a quarter of a second. Looking back or moving into range during the fade should smoothly reverse it. Inventory and hotbar icons must stay opaque. F1, other screens and session changes suppress the world HUD immediately; HUD settings deliberately draws a preview.


1. Select a librarian and open Settings → **HUD settings**.
2. Choose **After time**, enter 3 seconds and Apply. Look at the villager, then turn away. The HUD should hide after about three seconds and return when you look at it again. The countdown also refreshes while its trading screen is open.
3. Choose **Out of range**, enter 8 blocks and Apply. Walk away from the selected villager until it hides, then approach until it reappears. Looking at a different villager switches the distance reference to that villager.
4. Choose **Looking only**. Looking away or behind a blocking wall should hide the HUD; pointing at the villager restores it.
5. Choose **Never hide**. Looking away or walking away should leave the current selection visible. F1, opening other screens, disconnecting, or having no valid villager view still follow normal Minecraft/VTP behavior.
6. Reopen the game and check that the mode, timeout and distance are saved. Invalid values (zero seconds, more than 256 blocks, or non-numeric text) must not apply.
7. Repeat the relevant checks at GUI Scale 1, 2, 3 and Auto. Hidden HUDs must not alter reroll counts or lose saved targets.
## 11. Live size preview and grouped HUD options

1. Select a librarian, then open /vtp → Settings → **HUD settings**. The box should be centered horizontally and vertically. The visibility choices should sit under **When to hide the HUD**, with explanations on hover.
2. Drag the size slider left and right. The other controls should disappear during the drag, leaving an unblurred world and HUD that resizes immediately.
3. Release the slider: all controls should return. Cancel and check that the HUD retains its original size. Repeat and Apply to save the new size.
4. Toggle the HUD off and Apply. Reopen HUD settings: the preview should still work so sizing is possible even while the normal HUD is disabled. Turn it back on and Apply.
5. Try different Minecraft GUI scales and window sizes. Large HUDs should fit the available space, and all panels should keep matching widths.
6. Open HUD settings before selecting a villager: it should identify its example as a sample preview. Resizing must not select a villager or change prediction state.

## 12. Cheapest price and manual entry

1. Open Targets, choose Mending, and slide the price control fully left. It should read **Cheapest**, with **10** in the adjacent box and only level 1 enabled.
2. Add the target. Hover the saved target to confirm its maximum base price is 10 emeralds. The prediction should accept a 10-emerald Mending I trade and reject a higher base price.
3. Keep Cheapest selected, switch to Efficiency and level 5: the box should change to **17**. Level 1 should change it to **5**. Frost Walker II should show **16**.
4. Move right from Cheapest: the values should be 5, 6, 7, …, 64. The small box must follow the slider.
5. Type 23 in the box: the slider should show **Max: 23**. Switching enchantments should keep this manual limit. Invalid values such as 0, 65, or letters must disable Add target.
6. Restart the world and hover the saved Mending target: the 10-emerald limit should persist. Compare undiscounted base prices, not curing/reputation discounts.

## 13. Locked book lists and predictions

1. Look at a leveled librarian with several enchanted-book offers. Each book should occupy its own row in **Locked trades**, with its base emerald price. The third book must remain visible.
2. Enable **Level preview** in HUD settings. The **Predicted** panel should appear below the locked list for tiers the villager has not yet unlocked, without repeating the existing books.
3. Try GUI Scale 3 and a large HUD size. Both lists should remain visible, with consistent widths and space above the hotbar.
4. Open Sequence and choose the villager's current tier: its current books should also have separate rows.
5. Look at a Master. All observed books should remain listed, with no predictions beyond tier V.
6. Make one trade with a Novice without leveling it up. It should use the locked display too, without a reroll countdown for an absent target. On multiplayer, open its trades so the client receives its XP.

## If something fails

Run `/vtp status`, then reproduce the issue with `/vtp debug true`. Keep:

- The status output and `logs/latest.log`.
- Whether this was singleplayer or multiplayer.
- Minecraft, Fabric, and VTP versions, plus any server datapacks/trading plugins.
- The predicted trade, actual trade, and the exact action sequence.
- Whether another villager/player generated trades between the prediction and observation.

Turn extra logging off with `/vtp debug false`.
