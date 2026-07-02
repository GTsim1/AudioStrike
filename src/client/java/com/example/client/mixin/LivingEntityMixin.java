package com.example.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.example.client.MediaManager;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void onHandleEntityEvent(byte status, CallbackInfo ci) {
        if (status == 3) {
            LivingEntity entity = (LivingEntity) (Object) this;
            if (entity.getId() == MediaManager.lastAttackedEntityId) {
                long elapsed = System.currentTimeMillis() - MediaManager.lastAttackTime;
                if (elapsed < 2000) {
                    MediaManager.onKillRegistered(entity);
                }
            }
        }
    }
}
