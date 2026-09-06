package net.manaz.vtp.prediction;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

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
                              boolean noTrade) {
        public static Observation ofTrade(ResourceKey<Enchantment> ench, int level, int price) {
            return new Observation(ench, level, price, false);
        }
        public static Observation missing() {
            return new Observation(null, 0, -1, true);
        }
    }

    private static final List<Observation> observations = new ArrayList<>();
    private static String binding;
    private static String currentBinding() {
        return net.manaz.vtp.VtpPersistence.epoch() + ":" + net.manaz.vtp.render.VillagerTracker.getLastVillagerUuid()
                + ":" + net.manaz.vtp.render.VillagerTracker.getLastVillagerData().map(Object::toString).orElse("");
    }

    private CalibrationListStore() {}

    public static List<Observation> getAll() {
        return Collections.unmodifiableList(observations);
    }

    public static int size() {
        return observations.size();
    }

    public static void add(Observation obs) {
        String current = currentBinding();
        if (!current.equals(binding)) { observations.clear(); binding = current; }
        observations.add(obs);
        CalibrationService.clear();
    }

    public static void remove(int index) {
        if (index >= 0 && index < observations.size()) observations.remove(index);
    }

    public static void clear() {
        binding = null; CalibrationService.clear();
        observations.clear();
    }

    public static void replaceAll(List<Observation> newObs) {
        observations.clear();
        observations.addAll(newObs);
    }

    public static List<PredictionEngine.CalibrationSpec> toSpecs() {
        if (!currentBinding().equals(binding)) return List.of();
        List<PredictionEngine.CalibrationSpec> specs = new ArrayList<>(observations.size());
        for (Observation obs : observations) {
            if (obs.noTrade()) {
                specs.add(PredictionEngine.CalibrationSpec.missing());
            } else {
                specs.add(PredictionEngine.CalibrationSpec.of(
                        obs.enchantment(), obs.enchantmentLevel(), obs.price()));
            }
        }
        return specs;
    }
}
