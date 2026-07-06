package com.example.client.mixin;

import com.example.client.ActionSoundManager;
import net.minecraft.client.Minecraft;
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
        LivingEntity entity = (LivingEntity) (Object) this;
        if (status == 2) {
            if (entity == Minecraft.getInstance().player) {
                ActionSoundManager.playActionSound(ActionSoundManager.ActionType.GETS_DAMAGED);
            }
        } else if (status == 3) {
            if (entity.getId() == MediaManager.lastAttackedEntityId) {
                long elapsed = System.currentTimeMillis() - MediaManager.lastAttackTime;
                if (elapsed < 2000) {
                    MediaManager.onKillRegistered(entity);
                }
            }
        }
    }
}

