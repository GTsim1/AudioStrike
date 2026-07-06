package com.example.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE))
    private void onSubmit(EntityRenderState renderState, CameraRenderState camera, double x, double y, double z, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        
        // ONLY do the brute force rendering if Dawn Client is loaded.
        if (!FabricLoader.getInstance().isModLoaded("dawn")) {
            return;
        }

        if (renderState instanceof AvatarRenderState) {
            AvatarRenderState avatarState = (AvatarRenderState) renderState;
            
            if (avatarState.nameTag != null) {
                String rawName = avatarState.nameTag.getString();
                
                String foundPlayer = null;
                for (String player : com.example.client.ServerTracker.activeUsersOnServer.keySet()) {
                    if (rawName.contains(player)) {
                        foundPlayer = player;
                        break;
                    }
                }
                
                if (foundPlayer != null) {
                    String song = com.example.client.ServerTracker.activeUsersOnServer.get(foundPlayer);
                    if (song != null && !song.isEmpty()) {
                        Component songComponent = Component.literal("\u266b " + song);
                        
                        int lines = 1;
                        if (avatarState.scoreText != null) lines++;
                        
                        // Push up past the name tag and any custom Dawn background (2.5 lines total)
                        float translationLines = lines + 1.5f;

                        // PoseStack is already pushed and translated to the entity's relative position here!
                        poseStack.pushPose();
                        
                        // translate UP above the head
                        poseStack.translate(0.0f, 9.0f * 1.15f * 0.025f * translationLines, 0.0f);
                        
                        submitNodeCollector.submitNameTag(poseStack, avatarState.nameTagAttachment, 0, songComponent, !avatarState.isDiscrete, avatarState.lightCoords, avatarState.distanceToCameraSq, camera);
                        
                        poseStack.popPose();
                    }
                }
            }
        }
    }
}
