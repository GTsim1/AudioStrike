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
        // Start the media companion process
        MediaManager.start();
        ActionSoundManager.init();
        ServerTracker.start();

        // Register shutdown hook to clean up companion process
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MediaManager.stop();
            ServerTracker.stop();
        }));

        // Register key mapping bound to 'P'
        openMediaControlKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.spotify_mod.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
        ));

        // Register key mapping to search Spotify (Default: V)
        KeyMapping searchSpotifyKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.spotify_mod.search",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
        ));

        // Listen for client tick to check for key press
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
                        try {
                            String url = "https://open.spotify.com/search/" + java.net.URLEncoder.encode(song, "UTF-8");
                            net.minecraft.util.Util.getPlatform().openUri(new java.net.URI(url));
                            if (client.player != null) {
                                client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7aOpening Spotify search for: \u00a7f" + song));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        if (client.player != null) {
                            client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7c" + playerName + " is not currently listening to anything."));
                        }
                    }
                }
            }
        });

        // Track last attacked entity client-side for kill sound triggers
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && player == Minecraft.getInstance().player) {
                MediaManager.lastAttackedEntityId = entity.getId();
                MediaManager.lastAttackTime = System.currentTimeMillis();
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
}