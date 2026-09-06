package net.manaz.vtp.prediction;

import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.villager.VillagerData;
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
}
