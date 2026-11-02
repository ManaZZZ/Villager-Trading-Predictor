package net.manaz.vtp.prediction;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.manaz.vtp.trade.ObservedOffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the ordered list of observed villager rounds the user is building up
 * via the Calibrate tab. Survives GUI close so the user can reroll and re-open
 * the screen without losing progress.
 */
public final class CalibrationListStore {

    public record Observation(ResourceKey<Enchantment> enchantment,
                              int enchantmentLevel,
                              int price,
                              boolean noTrade, List<ObservedOffer> offers, long captureRevision) {
        public Observation { offers = List.copyOf(offers); }
        public static Observation ofTrade(ResourceKey<Enchantment> ench, int level, int price) {
            return new Observation(ench, level, price, false, List.of(), -1);
        }
        public static Observation missing() {
            return new Observation(null, 0, -1, true, List.of(), -1);
        }
        public static Observation captured(List<ObservedOffer> offers, long revision) {
            var bookOffer = offers.stream().filter(o -> !o.books().isEmpty()).findFirst();
            if (bookOffer.isEmpty()) return new Observation(null, 0, -1, true, offers, revision);
            var offer = bookOffer.get();
            var book = offer.books().getFirst();
            return new Observation(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(book.key())),
                    book.level(), offer.basePrice(), false, offers, revision);
        }
    }

    private static final List<Observation> observations = new ArrayList<>();
    private static String binding;
    private static String feedback = "";
    public static String currentBinding() {
        return net.manaz.vtp.VtpPersistence.epoch() + ":" + net.manaz.vtp.render.VillagerTracker.getLastVillagerUuid()
                + ":" + net.manaz.vtp.render.VillagerTracker.getLastVillagerData().map(Object::toString).orElse("");
    }

    private CalibrationListStore() {}

    private static void ensureBinding() {
        String current = currentBinding();
        if (!current.equals(binding)) { clear(); binding = current; }
    }
    public static String feedback() { ensureBinding(); return feedback; }
    public static void feedback(String value) { ensureBinding(); feedback = value; }
    public static boolean hasCapture(long revision) {
        ensureBinding();
        return observations.stream().anyMatch(o -> o.captureRevision() >= 0 && o.captureRevision() == revision);
    }

    public static List<Observation> getAll() {
        ensureBinding();
        return Collections.unmodifiableList(observations);
    }

    public static int size() {
        ensureBinding();
        return observations.size();
    }

    public static void add(Observation obs) {
        ensureBinding(); feedback = "";
        observations.add(obs);
        CalibrationService.clear();
    }

    public static void remove(int index) {
        ensureBinding(); feedback = ""; CalibrationService.clear();
        if (index >= 0 && index < observations.size()) observations.remove(index);
    }

    public static void clear() {
        binding = null; feedback = ""; CalibrationService.clear();
        observations.clear();
    }

    public static void replaceAll(List<Observation> newObs) {
        ensureBinding(); feedback = ""; CalibrationService.clear();
        observations.clear();
        observations.addAll(newObs);
    }

    public static List<PredictionEngine.CalibrationSpec> toSpecs() {
        if (!currentBinding().equals(binding)) return List.of();
        List<PredictionEngine.CalibrationSpec> specs = new ArrayList<>(observations.size());
        for (Observation obs : observations) {
            if (!obs.offers().isEmpty()) {
                specs.add(PredictionEngine.CalibrationSpec.captured(obs.offers()));
            } else if (obs.noTrade()) {
                specs.add(PredictionEngine.CalibrationSpec.missing());
            } else {
                specs.add(PredictionEngine.CalibrationSpec.of(
                        obs.enchantment(), obs.enchantmentLevel(), obs.price()));
            }
        }
        return specs;
    }
}
