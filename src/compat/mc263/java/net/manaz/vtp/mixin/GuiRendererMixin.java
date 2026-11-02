package net.manaz.vtp.mixin;

import net.manaz.vtp.render.HudFade;
import net.manaz.vtp.render.HudItemOpacity;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Only VTP's explicitly tagged icon draws get tinted; inventory/hotbar items keep native opacity. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @ModifyArgs(method = "submitBlitFromItemAtlas", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/BlitRenderState;<init>(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V"))
    private void vtp$fadeHudIcon(Args args, GuiItemRenderState item, GuiItemAtlas.SlotView slot) {
        float opacity = ((HudItemOpacity) (Object) item).vtp$getOpacity();
        if (opacity < 1) args.set(11, HudFade.itemTint((int) args.get(11), opacity));
    }
}
