package net.manaz.vtp.mixin;

import net.manaz.vtp.gui.VtpUi;
import net.manaz.vtp.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
    protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Inject(method = "extractContents", at = @At("RETURN"))
    private void vtp$renderPrediction(GuiGraphicsExtractor g, int mx, int my, float delta, CallbackInfo ci) {
        if (!VillagerHudRenderer.isEnabled()) return;
        HudView view = HudView.current();
        if (view == null || !view.uuid().equals(VillagerTracker.getMenuVillagerUuid())
                || VillagerHudRenderer.getTargets().isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int room = leftPos - 12;
        if (room >= 144) {
            VillagerHudRenderer.renderTarget(g, font, view, leftPos - Math.min(220, room) - 6, topPos, Math.min(220, room));
        } else {
            // At large GUI scales, a single line above the merchant leaves every inventory slot clear.
            int w = Math.min(imageWidth, width - 12), x = (width - w) / 2, y = Math.max(4, topPos - 23);
            VtpUi.hud(g, x, y, w, 18);
            String text = view.target() + "  ·  " + (view.warning().isEmpty() ? view.result() : view.warning());
            g.text(font, VtpUi.fit(font, text, w - 12), x + 6, y + 5, view.found() ? VtpUi.accentText() : VtpUi.HUD_TEXT);
        }
    }
}
