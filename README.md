## License
This mod is proprietary software. All rights reserved.
You may not copy, modify, or distribute it without explicit permission from the author.

# Villager Trading Predictor

Predicts the enchantments a villager will offer at every profession level, giving you a full picture of what that villager is worth. If a villager has nothing useful across all their levels, you will know immediately and can move on without wasting a single reroll.

## Commands

### `/vtp`
Opens the GUI.

### `/vtp calibrate <enchantment> <level>`
Calibrates the reroll list in case of an offset. Check the current enchantment in the villager, then execute the command. Confirm which reroll round it is on with `/vtp confirm <round>`. Alternatively, pass multiple enchantments at once:
```
/vtp calibrate <enchantment> <level>, <enchantment> <level>, ...
```

### `/vtp maxrerolls <number>`
Sets the max rerolls for the engine search.

### `/vtp seed <seed>`
Sets the seed for multiplayer.

### `/vtp target <enchantment> <level>`
Sets the targeted enchantment(s).

### `/vtp toggle`
Toggles the HUD.


