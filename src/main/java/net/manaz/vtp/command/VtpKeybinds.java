package net.manaz.vtp.command;

import net.manaz.vtp.compat.ClientCompat;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.gui.VtpSettingsScreen;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class VtpKeybinds {
    private static KeyMapping openGuiKey;
    private static KeyMapping overlayToggleKey;

    public static void register() {
        KeyMapping.Category vtpCategory = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("villager-trading-predictor", "category"));

        // Keep the former V binding's ID so installed control settings work after upgrading.
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villager-trading-predictor.toggle",
                InputConstants.KEY_V,
                vtpCategory
        ));
        // A new ID prevents the old V assignment from binding the overlay toggle on upgrade.
        overlayToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.villager-trading-predictor.toggle_overlay",
                ClientCompat.keyboardType(),
                InputConstants.UNKNOWN.getValue(),
                vtpCategory
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (client.player != null && ClientCompat.screen(client) == null) {
                    ClientCompat.setScreen(client, new VtpSettingsScreen());
                }
            }
            while (overlayToggleKey.consumeClick()) {
                if (client.player == null || ClientCompat.screen(client) != null) continue;
                boolean enabled = !VillagerHudRenderer.isEnabled();
                VillagerHudRenderer.setEnabled(enabled);
                VtpPersistence.saveGlobalSettings();
                client.player.sendSystemMessage(Component.literal("[VTP] Overlay " + (enabled ? "enabled" : "disabled")));
            }
        });
    }

    public static KeyMapping getOpenGuiKey() {
        return openGuiKey;
    }
}
