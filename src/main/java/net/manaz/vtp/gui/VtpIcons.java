package net.manaz.vtp.gui;

import net.minecraft.world.item.*;
import java.util.IdentityHashMap;
import java.util.Map;

/** Lazy creation avoids touching item components during early config loading. */
public final class VtpIcons {
    private static final Map<Item, ItemStack> CACHE = new IdentityHashMap<>();
    private VtpIcons() {}
    public static ItemStack of(Item item) { return CACHE.computeIfAbsent(item, ItemStack::new); }
    public static ItemStack book() { return of(Items.ENCHANTED_BOOK); }
    public static ItemStack profession(String id) {
        return of(switch (id) {
            case "armorer" -> Items.BLAST_FURNACE;
            case "butcher" -> Items.SMOKER;
            case "cartographer" -> Items.CARTOGRAPHY_TABLE;
            case "cleric" -> Items.BREWING_STAND;
            case "farmer" -> Items.COMPOSTER;
            case "fisherman" -> Items.BARREL;
            case "fletcher" -> Items.FLETCHING_TABLE;
            case "leatherworker" -> Items.CAULDRON;
            case "mason" -> Items.STONECUTTER;
            case "shepherd" -> Items.LOOM;
            case "toolsmith" -> Items.SMITHING_TABLE;
            case "weaponsmith" -> Items.GRINDSTONE;
            default -> Items.LECTERN;
        });
    }
}