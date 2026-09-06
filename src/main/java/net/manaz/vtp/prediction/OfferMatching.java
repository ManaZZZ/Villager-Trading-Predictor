package net.manaz.vtp.prediction;

import java.util.ArrayList;
import java.util.List;

/** Multiset comparison preserves duplicate offers and ignores presentation order. */
public final class OfferMatching {
    private OfferMatching() {}

    public static <T> boolean same(List<T> a, List<T> b) {
        if (a.size() != b.size()) return false; List<T> remaining = new ArrayList<>(b); for (T value : a) if (!remaining.remove(value)) return false; return true;
    }

    public static <T> List<T> added(List<T> before, List<T> after) {
        List<T> remaining = new ArrayList<>(after);
        for (T value : before) {
            if (!remaining.remove(value)) return List.of();
        }
        return List.copyOf(remaining);
    }
}
