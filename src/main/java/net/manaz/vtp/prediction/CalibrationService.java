package net.manaz.vtp.prediction;

import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import java.util.*;

/** One transaction shared by commands and GUI. A stale or unrelated confirmation is rejected. */
public final class CalibrationService {
    private record Binding(long epoch, UUID uuid, VillagerData data, long seed, long revision) {}
    private static Binding binding;
    private static List<Integer> candidates = List.of();
    private CalibrationService() {}
    public static void clear() { binding = null; candidates = List.of(); }
    public static String problem(VillagerData data) {
        if (Minecraft.getInstance().getSingleplayerServer() != null) return "Singleplayer uses live server state; calibration is unnecessary.";
        if (!SeedProvider.hasSeed()) return "No seed set.";
        return PredictionEngine.compatibilityProblem(data.profession(), data.level());
    }
    public static List<Integer> search(UUID uuid, VillagerData data, List<PredictionEngine.CalibrationSpec> specs) {
        clear();
        if (uuid == null || !problem(data).isEmpty()) return List.of();
        binding = new Binding(VtpPersistence.epoch(), uuid, data, SeedProvider.getSeed().orElseThrow(), VillagerTracker.observationRevision(uuid));
        candidates = PredictionEngine.findCalibrationSequence(binding.seed(), data.profession(), data.level(), specs);
        return candidates;
    }
    public static boolean confirm(UUID uuid, VillagerData data, int round) {
        if (binding == null || !binding.equals(new Binding(VtpPersistence.epoch(), uuid, data,
                SeedProvider.getSeed().orElse(0), VillagerTracker.observationRevision(uuid))) || !candidates.contains(round)) return false;
        PredictionEngine.applyCalibration(data.profession(), data.level(), round);
        VillagerTracker.resetAfterCalibrate(uuid, data, round);
        VtpPersistence.saveCurrentWorldState();
        clear();
        return true;
    }

    public record CheckResult(boolean calibrated, List<Integer> matches, String message) {}

    public static CheckResult checkObservations(UUID uuid, VillagerData data) {
        String problem = data == null || uuid == null ? "Look at a villager first." : problem(data);
        CheckResult result;
        if (!problem.isEmpty()) result = new CheckResult(false, List.of(), problem);
        else {
            var matches = search(uuid, data, CalibrationListStore.toSpecs());
            if (matches.size() == 1) {
                boolean applied = confirm(uuid, data, matches.getFirst());
                result = new CheckResult(applied, List.of(), applied ? "Sequence calibrated." : "Observation changed; search again.");
            } else result = new CheckResult(false, matches, matches.isEmpty()
                    ? "No match yet. Check the seed and consecutive rounds."
                    : matches.size() + " possible matches. Add another reroll.");
        }
        CalibrationListStore.feedback(result.message());
        return result;
    }

    public static String captureProblem(MerchantMenu menu) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu != menu) return "Open this villager's trades first.";
        UUID uuid = VillagerTracker.getMenuVillagerUuid();
        var data = VillagerTracker.getData(uuid).orElse(null);
        if (uuid == null || data == null) return "Waiting for this villager's trades.";
        if (!data.profession().is(VillagerProfession.LIBRARIAN)) return "Calibration is for librarians.";
        String problem = problem(data);
        if (!problem.isEmpty()) return problem;
        if (menu.getOffers().isEmpty() || VillagerTracker.getCalibrationOffers(uuid).isEmpty())
            return "Open trades before leveling up to record this tier.";
        if (CalibrationListStore.hasCapture(VillagerTracker.observationRevision(uuid))) return "This round is already recorded.";
        if (CalibrationListStore.size() >= 100) return "Maximum 100 observations.";
        return "";
    }

    public static CheckResult capture(MerchantMenu menu) {
        String problem = captureProblem(menu);
        if (!problem.isEmpty()) return new CheckResult(false, List.of(), problem);
        UUID uuid = VillagerTracker.getMenuVillagerUuid();
        var data = VillagerTracker.getData(uuid).orElseThrow();
        VillagerTracker.updateAndSelect(uuid, data);
        CalibrationListStore.add(CalibrationListStore.Observation.captured(
                VillagerTracker.getCalibrationOffers(uuid).orElseThrow(), VillagerTracker.observationRevision(uuid)));
        return checkObservations(uuid, data);
    }
}
