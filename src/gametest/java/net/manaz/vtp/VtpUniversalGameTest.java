package net.manaz.vtp;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.manaz.vtp.command.VtpKeybinds;
import net.manaz.vtp.compat.*;
import net.manaz.vtp.gui.VtpSettingsScreen;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.minecraft.client.KeyMapping;
import java.util.Arrays;

/** Prove the distributed bundle selects one nested implementation and uses its real runtime code. */
public final class VtpUniversalGameTest implements FabricClientGameTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        var loader = FabricLoader.getInstance();
        String bundleHash;
        try {
            var bundle = loader.getModContainer("villager-trading-predictor-universal").orElseThrow();
            var bytes = java.nio.file.Files.readAllBytes(bundle.getOrigin().getPaths().getFirst());
            bundleHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) { throw new AssertionError("Cannot identify the distributed artifact", e); }
        var active = loader.getModContainer(VillagerTradingPredictor.MOD_ID).orElseThrow();
        check(loader.isModLoaded("villager-trading-predictor-universal"), "Universal bundle was not loaded");
        check(active.getOrigin().getKind() == ModOrigin.Kind.NESTED, "Test loaded development classes instead of the nested implementation");
        check(active.getOrigin().getParentModId().equals("villager-trading-predictor-universal"), "Implementation has the wrong parent");
        check(active.getMetadata().getVersion().getFriendlyString().endsWith("+mc" + BuildInfo.minecraftFamily()), "Wrong implementation selected");
        check(ClientCompat.family().equals(BuildInfo.minecraftFamily()), "Wrong client adapter loaded");
        String origin = VillagerTradingPredictor.class.getProtectionDomain().getCodeSource().getLocation().toString().replace('\\', '/');
        check(!origin.contains("/classes/java/"), "Development classpath leaked into the bundle test: " + origin);
        check(loader.getEntrypointContainers("client", ClientModInitializer.class).stream()
                .filter(entry -> entry.getProvider().getMetadata().getId().equals(VillagerTradingPredictor.MOD_ID)).count() == 1,
                "Multiple VTP initializers loaded");
        try (var world = context.worldBuilder().create()) {
            boolean enabled = context.computeOnClient(mc -> VillagerHudRenderer.isEnabled());
            context.runOnClient(mc -> {
                var keys = Arrays.stream(mc.options.keyMappings).filter(key -> key.getName().startsWith("key.villager-trading-predictor.")).toList();
                check(keys.size() == 2, "Unexpected or duplicate VTP key bindings");
                var gui = VtpKeybinds.getOpenGuiKey();
                check(gui.getDefaultKey().getValue() == InputConstants.KEY_V, "GUI default does not match this game's V key");
                gui.setKey(gui.getDefaultKey()); KeyMapping.resetMapping();
                KeyMapping.click(gui.getDefaultKey());
            });
            context.waitFor(mc -> ClientCompat.screen(mc) instanceof VtpSettingsScreen);
            check(context.computeOnClient(mc -> VillagerHudRenderer.isEnabled()) == enabled, "V toggled the HUD instead of only opening settings");
            context.setScreen(() -> null);
            KeyMapping toggle = context.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings)
                    .filter(key -> key.getName().equals("key.villager-trading-predictor.toggle_overlay")).findFirst().orElseThrow());
            context.runOnClient(mc -> {
                check(toggle.getDefaultKey().equals(InputConstants.UNKNOWN), "Overlay shortcut has a default assignment");
                var key = ClientCompat.keyboardType().getOrCreate(InputConstants.KEY_F4);
                toggle.setKey(key); KeyMapping.resetMapping(); KeyMapping.click(key);
            });
            context.waitFor(mc -> VillagerHudRenderer.isEnabled() != enabled);
            context.runOnClient(mc -> {
                toggle.setKey(InputConstants.UNKNOWN); KeyMapping.resetMapping(); VillagerHudRenderer.setEnabled(enabled);
                VillagerTradingPredictor.LOGGER.info("[VTP UNIVERSAL TEST] PASS: {} selected nested {}, V opens settings, optional overlay shortcut. SHA256={}",
                        BuildInfo.minecraftVersion(), active.getMetadata().getVersion().getFriendlyString(), bundleHash);
            });
        }
    }
}
