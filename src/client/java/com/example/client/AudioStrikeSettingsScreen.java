package com.example.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AudioStrikeSettingsScreen extends Screen {
    private final Screen parent;
    private AudioStrikeConfig config;

    public AudioStrikeSettingsScreen(Screen parent) {
        super(Component.literal("AudioStrike Settings"));
        this.parent = parent;
        this.config = AudioStrikeConfig.getInstance();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Animation Toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("Animation: " + (config.enableAnimation ? "ON" : "OFF")),
                button -> {
                    config.enableAnimation = !config.enableAnimation;
                    button.setMessage(Component.literal("Animation: " + (config.enableAnimation ? "ON" : "OFF")));
                }
        ).bounds(centerX - 100, startY, 200, 20).build());

        // Broadcast My Song Toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("Broadcast My Song: " + (config.broadcastMySong ? "ON" : "OFF")),
                button -> {
                    config.broadcastMySong = !config.broadcastMySong;
                    button.setMessage(Component.literal("Broadcast My Song: " + (config.broadcastMySong ? "ON" : "OFF")));
                }
        ).bounds(centerX - 100, startY + 25, 200, 20).build());

        // Show Others' Songs Toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("Show Others' Songs: " + (config.showOtherPlayersSongs ? "ON" : "OFF")),
                button -> {
                    config.showOtherPlayersSongs = !config.showOtherPlayersSongs;
                    button.setMessage(Component.literal("Show Others' Songs: " + (config.showOtherPlayersSongs ? "ON" : "OFF")));
                }
        ).bounds(centerX - 100, startY + 50, 200, 20).build());

        // Show All Characters Toggle
        this.addRenderableWidget(Button.builder(
                Component.literal("Show Full Name: " + (config.showAllCharacters ? "ON" : "OFF")),
                button -> {
                    config.showAllCharacters = !config.showAllCharacters;
                    button.setMessage(Component.literal("Show Full Name: " + (config.showAllCharacters ? "ON" : "OFF")));
                }
        ).bounds(centerX - 100, startY + 75, 200, 20).build());

        // Max Characters Buttons (- / +)
        this.addRenderableWidget(Button.builder(
                Component.literal("-"),
                button -> {
                    if (config.maxCharacters > 5) config.maxCharacters--;
                }
        ).bounds(centerX - 100, startY + 100, 20, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("+"),
                button -> {
                    if (config.maxCharacters < 50) config.maxCharacters++;
                }
        ).bounds(centerX + 80, startY + 100, 20, 20).build());

        // Done Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> this.onClose()
        ).bounds(centerX - 100, startY + 180, 200, 20).build());
    }

    private String getPreviewText() {
        String song = "Rick Astley - Never Gonna Give You Up";
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
        return "\uE000 " + displaySong;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000); // Simple dark background
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        
        guiGraphics.centeredText(this.font, Component.literal("Max Characters: " + config.maxCharacters), this.width / 2, this.height / 4 + 106, 0xFFFFFFFF);
        
        // Render Preview
        guiGraphics.centeredText(this.font, Component.literal("Preview:"), this.width / 2, this.height / 4 + 130, 0xFFAAAAAA);
        String previewText = getPreviewText();
        int previewWidth = this.font.width(previewText);
        int previewX = (this.width - previewWidth) / 2;
        int previewY = this.height / 4 + 145;
        
        // Draw nametag background
        int padding = 2;
        guiGraphics.fill(previewX - padding - 1, previewY - padding - 1, previewX + previewWidth + padding + 1, previewY + 9 + padding + 1, 0x40000000);
        guiGraphics.text(this.font, previewText, previewX, previewY, 0xFFFFFFFF, false);
        
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        AudioStrikeConfig.save();
        this.minecraft.setScreen(parent);
    }
}
