package net.manaz.vtp.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

/** Uses native item models, glint, resource packs, GUI scale and clipping during the fade. */
public final class HudItems {
    private HudItems() {}
    public static void render(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float opacity) {
        if (opacity >= 1) { g.item(stack, x, y); return; }
        if (opacity <= 0 || stack.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var model = new TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(model, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
        var item = new GuiItemRenderState(new Matrix3x2f(g.pose()), model, x, y, g.scissorStack.peek());
        ((HudItemOpacity) (Object) item).vtp$setOpacity(opacity);
        g.guiRenderState.addItem(item);
    }
}