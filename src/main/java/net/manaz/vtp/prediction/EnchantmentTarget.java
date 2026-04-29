package net.manaz.vtp.prediction;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Represents a target enchantment+level the user is searching for,
 * with an optional maximum emerald price filter.
 */
public record EnchantmentTarget(
        ResourceKey<Enchantment> enchantmentKey,
        int minLevel,
        int maxPrice   // -1 means no price filter
) {
    /** Convenience constructor without a price filter. */
    public EnchantmentTarget(ResourceKey<Enchantment> enchantmentKey, int minLevel) {
        this(enchantmentKey, minLevel, -1);
    }

    /**
     * Check enchantment + level only (ignores price).
     * Used where additionalCost is not available (e.g. calibration).
     */
    public boolean matches(Holder<Enchantment> enchantment, int level) {
        if (level < minLevel) return false;
        return enchantment.is(enchantmentKey);
    }

    /**
     * Full match: enchantment, level, AND price constraint.
     * Used by the prediction engine and the upcoming-panel highlighter.
     */
    public boolean matches(Holder<Enchantment> enchantment, int level, int additionalCost) {
        if (!matches(enchantment, level)) return false;
        if (maxPrice >= 0 && additionalCost > maxPrice) return false;
        return true;
    }

    /**
     * Match against a saved OfferData (key string form) — used when rendering
     * persisted offers that don't carry a live Holder reference.
     */
    public boolean matchesKey(String enchantmentKeyStr, int level, int additionalCost) {
        if (level < minLevel) return false;
        if (!enchantmentKey.identifier().toString().equals(enchantmentKeyStr)) return false;
        if (maxPrice >= 0 && additionalCost > maxPrice) return false;
        return true;
    }
}
