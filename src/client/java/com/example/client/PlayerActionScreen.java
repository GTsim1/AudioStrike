package com.example.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class PlayerActionScreen extends Screen {
    private final String targetPlayerName;
    private final String targetSong;

    public PlayerActionScreen(String targetPlayerName, String targetSong) {
        super(Component.literal("Player Action"));
        this.targetPlayerName = targetPlayerName;
        this.targetSong = targetSong;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.literal("\u2764 Like Song"), button -> {
            ServerTracker.sendLike(targetSong);
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("\u00a7eSending like for: \u00a7f" + targetSong + "..."));
            }
            this.onClose();
        }).bounds(centerX - 100, centerY, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("\uD83D\uDD0D Find on Spotify"), button -> {
            try {
                String url = "https://open.spotify.com/search/" + java.net.URLEncoder.encode(targetSong, "UTF-8");
                net.minecraft.util.Util.getPlatform().openUri(new java.net.URI(url));
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.onClose();
        }).bounds(centerX - 100, centerY + 25, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000); 
        
        guiGraphics.centeredText(this.font, Component.literal("\u00a7e" + targetPlayerName + "'s Song"), this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        guiGraphics.centeredText(this.font, Component.literal("\u00a7f" + targetSong), this.width / 2, this.height / 2 - 25, 0xAAAAAA);
        
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
