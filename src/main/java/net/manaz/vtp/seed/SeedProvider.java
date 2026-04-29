package net.manaz.vtp.seed;

import net.manaz.vtp.VillagerTradingPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import java.util.OptionalLong;

/**
 * Captures and stores the world seed.
 * - Singleplayer: auto-detected from the integrated server.
 * - Multiplayer: set manually via /vtp seed command.
 */
public class SeedProvider {
    private static OptionalLong manualSeed = OptionalLong.empty();
    private static boolean seedLogged = false;

    public static void setManualSeed(long seed) {
        manualSeed = OptionalLong.of(seed);
        seedLogged = false;
        VillagerTradingPredictor.LOGGER.info("Manual seed set to: {}", seed);
    }

    public static void clearManualSeed() {
        manualSeed = OptionalLong.empty();
        seedLogged = false;
    }

    /**
     * Returns the world seed if available.
     * Prefers manual seed (for MP), falls back to integrated server seed (for SP).
     */
    public static OptionalLong getSeed() {
        if (manualSeed.isPresent()) {
            return manualSeed;
        }

        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            long seed = server.overworld().getSeed();
            if (!seedLogged) {
                VillagerTradingPredictor.LOGGER.info("Auto-detected world seed: {}", seed);
                seedLogged = true;
            }
            return OptionalLong.of(seed);
        }

        return OptionalLong.empty();
    }

    public static boolean hasSeed() {
        return getSeed().isPresent();
    }

    public static void onWorldJoin() {
        seedLogged = false;
    }

    public static void onWorldLeave() {
        manualSeed = OptionalLong.empty();
        seedLogged = false;
    }
}
