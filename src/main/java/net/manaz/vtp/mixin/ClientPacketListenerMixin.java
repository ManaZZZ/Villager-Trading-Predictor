package net.manaz.vtp.mixin;

import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    // RETURN runs after vanilla's thread check and menu/container validation.
    @Inject(method = "handleMerchantOffers", at = @At("RETURN"))
    private void vtp$observeOffers(ClientboundMerchantOffersPacket packet, CallbackInfo ci) {
        VillagerTracker.onMerchantOffers(packet);
    }
}
