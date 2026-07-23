package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("modid", "main"));
    private static KeyMapping openMediaControlKey;

    @Override
    public void onInitializeClient() {
        
        MediaManager.start();
        ActionSoundManager.init();
        ServerTracker.start();

        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MediaManager.stop();
            ServerTracker.stop();
        }));

        
        openMediaControlKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.spotify_mod.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
        ));

        
        KeyMapping searchSpotifyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.spotify_mod.search",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
        ));

        
        KeyMapping leaderboardKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.spotify_mod.leaderboard",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
        ));

        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMediaControlKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new MediaControlScreen());
                }
            }
            while (searchSpotifyKey.consumeClick()) {
                if (client.crosshairPickEntity != null && client.crosshairPickEntity instanceof net.minecraft.world.entity.player.Player targetPlayer) {
                    String playerName = targetPlayer.getName().getString();
                    String song = ServerTracker.activeUsersOnServer.get(playerName);
                    if (song != null && !song.isEmpty()) {
                        client.setScreen(new PlayerActionScreen(playerName, song));
                    } else {
                        if (client.player != null) {
                            client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7c" + playerName + " is not currently listening to anything."));
                        }
                    }
                }
            }
            while (leaderboardKey.consumeClick()) {
                ServerTracker.fetchTopSongs();
                if (client.player != null) {
                    client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7eFetching global leaderboard from Firebase..."));
                }
            }
        });

        
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && player == Minecraft.getInstance().player) {
                MediaManager.lastAttackedEntityId = entity.getId();
                MediaManager.lastAttackTime = System.currentTimeMillis();
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
}