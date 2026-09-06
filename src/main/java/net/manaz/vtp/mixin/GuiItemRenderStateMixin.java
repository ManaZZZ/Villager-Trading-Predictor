package net.manaz.vtp.mixin;

import net.manaz.vtp.render.HudItemOpacity;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GuiItemRenderState.class)
public abstract class GuiItemRenderStateMixin implements HudItemOpacity {
    @Unique private float vtp$opacity = 1;
    @Override public float vtp$getOpacity() { return vtp$opacity; }
    @Override public void vtp$setOpacity(float opacity) { vtp$opacity = Math.clamp(opacity, 0, 1); }
}