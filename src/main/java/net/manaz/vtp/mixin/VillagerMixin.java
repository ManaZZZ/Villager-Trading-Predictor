package net.manaz.vtp.mixin;

import net.manaz.vtp.render.VillagerTracker;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hooks into the client-side entity data sync path so that VillagerTracker
 * updates for ALL villagers, not just the one the player is looking at.
 *
 * When the client receives a ClientboundSetEntityDataPacket, it calls
 * Entity.onSyncedDataUpdated(List<DataValue<?>>) after applying the values.
 * This fires on the client thread for every entity data change — including
 * profession changes — regardless of camera direction.
 *
 * setVillagerData() is NOT on this path (it is only called by server-side
 * game logic), so the previous mixin on that method never fired for
 * client-received updates.
 */
@Mixin(Entity.class)
public class VillagerMixin {

    @Inject(method = "onSyncedDataUpdated(Ljava/util/List;)V", at = @At("RETURN"))
    private void vtp$onDataUpdated(
            List<SynchedEntityData.DataValue<?>> values, CallbackInfo ci) {

        // Fast-path: only care about Villager entities
        if (!((Object) this instanceof Villager villager)) return;
        if (!villager.level().isClientSide()) return;

        // Look for a VillagerData item in the changed values
        for (SynchedEntityData.DataValue<?> value : values) {
            if (value.value() instanceof VillagerData data) {
                VillagerTracker.update(villager.getUUID(), data);
                return;
            }
        }
    }
}
