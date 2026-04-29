package net.manaz.vtp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.manaz.vtp.command.VtpCommand;
import net.manaz.vtp.command.VtpKeybinds;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.seed.ServerSeedStore;
import net.manaz.vtp.trade.ClientTradeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalLong;

public class VillagerTradingPredictor implements ClientModInitializer {
    public static final String MOD_ID = "villager-trading-predictor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Villager Trading Predictor initializing...");

        VtpPersistence.loadGlobalSettings();

        VtpCommand.register();
        VtpKeybinds.register();
        VillagerHudRenderer.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            SeedProvider.onWorldJoin();
            // In multiplayer, initialize the classpath-based trade registry.
            // In singleplayer, the integrated server registry is used directly (no-op here).
            if (client.getSingleplayerServer() == null) {
                ClientTradeRegistry.init();
                // Auto-restore seed for this server if previously saved
                var serverData = client.getCurrentServer();
                if (serverData != null) {
                    ServerSeedStore.getSeedForServer(serverData.ip).ifPresent(savedSeed -> {
                        SeedProvider.setManualSeed(savedSeed);
                        LOGGER.info("[VTP] Auto-restored seed {} for server {}", savedSeed, serverData.ip);
                    });
                }
            }
            // For singleplayer the seed is available immediately — load saved state.
            // For multiplayer the seed is auto-restored above (or set manually via /vtp seed).
            OptionalLong seed = SeedProvider.getSeed();
            if (seed.isPresent()) {
                VtpPersistence.load(seed.getAsLong());
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            net.manaz.vtp.trade.TradeDataLoader.resetRegistryLog();
            ClientTradeRegistry.clear();
            // Save per-world state before clearing the seed.
            OptionalLong seed = SeedProvider.getSeed();
            if (seed.isPresent()) {
                VtpPersistence.save(seed.getAsLong());
            }
            VtpPersistence.saveGlobalSettings();
            SeedProvider.onWorldLeave();
            VillagerTracker.clear();
        });

        LOGGER.info("Villager Trading Predictor initialized.");
    }
}
