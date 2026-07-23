package com.example.client.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void onExtractRenderState(net.minecraft.world.entity.Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        com.example.client.AudioStrikeConfig config = com.example.client.AudioStrikeConfig.getInstance();
        boolean isLocalPlayer = net.minecraft.client.Minecraft.getInstance().player != null && net.minecraft.client.Minecraft.getInstance().player.getUUID().equals(entity.getUUID());

        

        String rawName = entity.getName().getString();
        String foundPlayer = null;
        for (String player : com.example.client.ServerTracker.activeUsersOnServer.keySet()) {
            if (rawName.contains(player)) {
                foundPlayer = player;
                break;
            }
        }
        
        String song = foundPlayer != null ? com.example.client.ServerTracker.activeUsersOnServer.get(foundPlayer) : null;
        
        if (song != null && !song.isEmpty()) {
            if (!config.showOtherPlayersSongs && !isLocalPlayer) {
                return; 
            }
            
            int maxLength = config.maxCharacters;
            String displaySong = song;
            
            if (!config.showAllCharacters && displaySong.length() > maxLength) {
                if (config.enableAnimation) {
                    String paddedSong = song + "   ";
                    int offset = (int) ((System.currentTimeMillis() / 300) % paddedSong.length());
                    StringBuilder builder = new StringBuilder(maxLength);
                    for (int i = 0; i < maxLength; i++) {
                        builder.append(paddedSong.charAt((offset + i) % paddedSong.length()));
                    }
                    displaySong = builder.toString();
                } else {
                    displaySong = displaySong.substring(0, maxLength);
                }
            }
            
            
            boolean isDawn = FabricLoader.getInstance().isModLoaded("dawn") || config.forceDawnClientCompat;
            String iconChar = isDawn ? "\uE001" : "\uE000";
            
            String likeSuffix = "";
            if (config.showLikesOnNametag) {
                Integer likes = com.example.client.ServerTracker.activeUsersOnServerLikes.get(foundPlayer);
                likeSuffix = (likes != null && likes > 0) ? " \u00a7c(" + likes + " \u2665)\u00a7f" : "";
            }

            
            Component songComponent = Component.literal(" " + iconChar + " " + displaySong + likeSuffix + " ");
            
            if (state.scoreText != null) {
                
                state.scoreText = Component.literal("").append(state.scoreText).append(" | ").append(songComponent);
            } else {
                
                state.scoreText = songComponent;
            }
        }
    }
}
