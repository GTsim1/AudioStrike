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

        // Register shutdown hook to clean up companion process
        Runtime.getRuntime().addShutdownHook(new Thread(MediaManager::stop));

        // Register key mapping bound to 'P'
        openMediaControlKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.spotify_mod.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
        ));

        // Listen for client tick to check for key press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMediaControlKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new MediaControlScreen());
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