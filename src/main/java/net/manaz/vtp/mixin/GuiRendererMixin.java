package net.manaz.vtp.mixin;

import net.manaz.vtp.render.HudFade;
import net.manaz.vtp.render.HudItemOpacity;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only VTP's explicitly tagged icon draws get tinted; inventory/hotbar items keep native opacity. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Redirect(method = "submitBlitFromItemAtlas", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;addBlitToCurrentLayer(Lnet/minecraft/client/renderer/state/gui/BlitRenderState;)V"))
    private void vtp$fadeHudIcon(GuiRenderState state, BlitRenderState blit, GuiItemRenderState item, GuiItemAtlas.SlotView slot) {
        float opacity = ((HudItemOpacity) (Object) item).vtp$getOpacity();
        if (opacity < 1) blit = new BlitRenderState(blit.pipeline(), blit.textureSetup(), blit.pose(),
                blit.x0(), blit.y0(), blit.x1(), blit.y1(), blit.u0(), blit.u1(), blit.v0(), blit.v1(),
                HudFade.itemTint(blit.color(), opacity), blit.scissorArea(), blit.bounds());
        state.addBlitToCurrentLayer(blit);
    }
}