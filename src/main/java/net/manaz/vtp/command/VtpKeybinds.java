package net.manaz.vtp.command;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.autoreroll.AutoRerollManager;
import net.manaz.vtp.gui.VtpSettingsScreen;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.UUID;

public class VtpKeybinds {
    private static KeyMapping toggleKey;
    private static KeyMapping openGuiKey;
    private static KeyMapping rerollUpKey;
    private static KeyMapping rerollDownKey;
    private static KeyMapping autoRerollKey;

    public static void register() {
        KeyMapping.Category vtpCategory = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("villager-trading-predictor", "category"));

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villager-trading-predictor.toggle",
                GLFW.GLFW_KEY_V,
                vtpCategory
        ));
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villager-trading-predictor.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                vtpCategory
        ));
        rerollUpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villager-trading-predictor.reroll_up",
                GLFW.GLFW_KEY_UP,
                vtpCategory
        ));
        rerollDownKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villager-trading-predictor.reroll_down",
                GLFW.GLFW_KEY_DOWN,
                vtpCategory
        ));
        autoRerollKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villager-trading-predictor.auto_reroll",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                vtpCategory
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                boolean newState = !VillagerHudRenderer.isEnabled();
                VillagerHudRenderer.setEnabled(newState);
                VtpPersistence.saveGlobalSettings();
                if (client.player != null) {
                    client.player.sendSystemMessage(
                            Component.literal("[VTP] Overlay " + (newState ? "enabled" : "disabled")));
                }
            }

            while (openGuiKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new VtpSettingsScreen());
                }
            }

            while (rerollUpKey.consumeClick()) {
                adjustLastVillagerReroll(client, +1);
            }

            while (rerollDownKey.consumeClick()) {
                adjustLastVillagerReroll(client, -1);
            }

            while (autoRerollKey.consumeClick()) {
                AutoRerollManager.toggle();
            }

            AutoRerollManager.tick();
        });
    }

    public static KeyMapping getAutoRerollKey() {
        return autoRerollKey;
    }

    private static void adjustLastVillagerReroll(net.minecraft.client.Minecraft client, int delta) {
        UUID uuid = VillagerTracker.getLastVillagerUuid();
        Optional<VillagerData> data = VillagerTracker.getLastVillagerData();
        if (uuid == null || data.isEmpty()) return;

        VillagerTracker.adjustRerollCount(uuid, data.get(), delta);
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "[VTP] Reroll count: " + VillagerTracker.getRerollCount(uuid)));
        }
    }
}
