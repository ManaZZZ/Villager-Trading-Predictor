## License
This mod is proprietary software. All rights reserved.
You may not copy, modify, or distribute it without explicit permission from the author.

# Villager Trading Predictor

Looking for **Mending** or **Efficiency V**? VTP shows upcoming librarian trades and helps you find the enchantments you want before spending another twenty minutes breaking lecterns.

VTP is a **client-side Fabric mod**. No server installation is required.

## Features

- **Upcoming trades:** See the current offer and upcoming rerolls in a compact HUD.
- **Enchantment targets:** Choose an enchantment, level, and maximum price to find matching trades.
- **Cheapest price targeting:** Automatically target the lowest possible base price for your selection—for example, **Mending I for 10 emeralds**.
- **Level predictions:** Preview enchanted-book offers for future profession levels.
- **Clear locked trades:** View each existing book on its own row, alongside predictions for remaining levels.
- **Customizable interface:** A Jade-inspired HUD with Minecraft item icons, adjustable size, live previews, and an RGB/Hex accent-color picker. Jade is not required.
- **HUD visibility rules:** Keep the HUD visible, hide it after a delay, hide it outside a chosen range, or show it only while looking at a villager. Visibility changes fade smoothly.

## Getting Started

**Singleplayer:** Look at a librarian and open its trades. VTP reads the current trade-sequence state automatically; no seed entry or calibration is needed.

**Multiplayer:** Enter the world seed using `/vtp seed <seed>`, then open the librarian’s trades. Calibration may be needed to align predictions.

Open **`/vtp`** to manage targets, inspect predictions, and customize the interface.

## Commands

| Command | Description |
| --- | --- |
| `/vtp` | Opens the GUI |
| `/vtp seed <seed>` | Sets and saves the multiplayer seed |
| `/vtp target <enchantment> <level>` | Adds an enchantment target |
| `/vtp maxrerolls <number>` | Sets the search limit, up to 10,000 rerolls |
| `/vtp toggle` | Toggles the HUD |
| `/vtp calibrate <observations>` | Matches a sequence of observed rerolls |
| `/vtp calibrate none` | Records a calibration observation without a book |
| `/vtp confirm <round>` | Confirms a reported calibration candidate |
| `/vtp calibrate reset` | Clears multiplayer alignment |
| `/vtp status` | Shows tracking and alignment information |

## Multiplayer Calibration

If predictions drift, use the **Calibrate** tab or record consecutive rerolls from the **same villager and profession level**.

For example:

```text
/vtp calibrate efficiency 3; none; mending 1
```

Replace the example with your actual observations. Use `none` for a round without an enchanted book. Each entry represents a separate reroll—not separate books from one trade screen.

If several matches remain, add more observations to narrow them down. Use `/vtp confirm <round>` to confirm a reported candidate.

## Things to Know

- Trade sequences are shared between villagers. Another villager generating trades can change future predictions while existing offers remain unchanged.
- Multiplayer requires the world seed. Activity from other players or unloaded villagers can cause drift and require recalibration.
- Predicted prices and target limits use **base prices**, before discounts and other player-specific adjustments.
- VTP supports standard vanilla librarian trades. Trade Rebalance and custom trading systems are not supported; server-only changes cannot always be detected.
