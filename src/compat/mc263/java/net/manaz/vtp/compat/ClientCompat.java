package net.manaz.vtp.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Minecraft 263 client API adapter; shared features call this boundary. */
public final class ClientCompat {
    private ClientCompat() {}
    public static String family() { return "26.3"; }
    public static Screen screen(Minecraft client) { return client.gui.screen(); }
    public static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    public static boolean hudHidden(Minecraft client) { return client.gui.hud.isHidden(); }
    public static void overlayMessage(Minecraft client, Component message, boolean animate) { client.gui.hud.setOverlayMessage(message, animate); }
    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYBOARD; }
    public static void swing(Minecraft client, InteractionHand hand) { client.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true); }
}
