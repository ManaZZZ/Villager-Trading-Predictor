package net.manaz.vtp.render;

import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.world.entity.npc.villager.VillagerData;
import java.util.*;

public final class ForecastView {
    private ForecastView() {}
    public static String levelText(UUID uuid, VillagerData data, int level, long seed) {
        String prefix = "Lv." + level + ": ";
        if (VillagerTracker.hasSavedLevel(uuid, level)) {
            var saved = VillagerTracker.getSavedLevelOffers(uuid, level);
            return prefix + (saved.isEmpty() ? "no enchanted book" : String.join(", ", saved.stream()
                    .map(o -> name(o.enchantmentKey()) + " " + VillagerHudRenderer.fmtLvl(o.enchantmentLevel())
                            + " (" + o.additionalCost() + " base em)").toList())) + " [observed]";
        }
        if (level <= data.level() && !(level == data.level() && VillagerTracker.isAwaitingOffers(uuid)))
            return prefix + "not observed";
        var rounds = PredictionEngine.getForecastRounds(seed, data.profession(), level, 1);
        if (rounds.isEmpty()) return prefix + "forecast unavailable";
        var books = rounds.getFirst().stream().flatMap(o -> o.enchantment().stream())
                .map(e -> name(e.enchantment().unwrapKey().orElseThrow().identifier().toString()) + " "
                        + VillagerHudRenderer.fmtLvl(e.level()) + " (" + e.emeraldPrice() + " base em)").toList();
        return prefix + (books.isEmpty() ? "no enchanted book" : String.join(", ", books)) + " [forecast]";
    }
    private static String name(String key) {
        String value = key.substring(key.lastIndexOf(':') + 1).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
