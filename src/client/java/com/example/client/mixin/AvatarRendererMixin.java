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
        
        // Always run this mixin for both Vanilla and Dawn Client

        String playerName = entity.getName().getString();
        String song = com.example.client.ServerTracker.activeUsersOnServer.get(playerName);
        
        if (song != null && !song.isEmpty()) {
            com.example.client.AudioStrikeConfig config = com.example.client.AudioStrikeConfig.getInstance();
            
            boolean isLocalPlayer = net.minecraft.client.Minecraft.getInstance().player != null && net.minecraft.client.Minecraft.getInstance().player.getUUID().equals(entity.getUUID());
            if (!config.showOtherPlayersSongs && !isLocalPlayer) {
                return; // Skip rendering for other players if config is disabled
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
            
            // Added leading space so Dawn's background box padding covers the left edge properly
            String iconChar = FabricLoader.getInstance().isModLoaded("dawn") ? "\uE001" : "\uE000";
            // Check if the song has likes
            Integer likes = com.example.client.ServerTracker.activeUsersOnServerLikes.get(playerName);
            String likeSuffix = (likes != null && likes > 0) ? " \u00a7c(" + likes + " \u2665)\u00a7f" : "";

            // Added a single trailing space so it doesn't end abruptly
            Component songComponent = Component.literal(" " + iconChar + " " + displaySong + likeSuffix + " ");
            
            if (state.scoreText != null) {
                // If there's already a scoreboard below the name, append to it
                state.scoreText = Component.literal("").append(state.scoreText).append(" | ").append(songComponent);
            } else {
                // Otherwise, use the scoreboard slot for our song
                state.scoreText = songComponent;
            }
        }
    }
}
