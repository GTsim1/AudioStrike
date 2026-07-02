package com.example.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

import java.io.File;

public class MediaControlScreen extends Screen {
    private static final int CARD_WIDTH = 280;
    private static final int CARD_HEIGHT = 130;
    
    private int startX;
    private int startY;
    
    private boolean isDraggingSlider = false;
    private double dragPosition = -1;
    private boolean isDropdownOpen = false;
    private boolean isSetupOpen = false;
    private net.minecraft.client.gui.components.EditBox linkField;
    private boolean showFfmpegWarning = false;
    
    // UI Local Favorites/Shuffle states (since Windows doesn't always sync Heart state)
    public static boolean isFavorited = false;
    public static boolean isShuffleEnabled = false;

    private int activeVolumeSliderIndex = -1;
    private boolean isDraggingVolumeSlider = false;

    // Mic test toggle state: tracks which sound file is being transmitted via mic (empty = off)
    public static volatile String currentMicFile = "";

    public MediaControlScreen() {
        super(Component.literal("Media Control"));
    }

    @Override
    protected void init() {
        int actualHeight = isSetupOpen ? 220 : CARD_HEIGHT;
        this.startX = (this.width - CARD_WIDTH) / 2;
        this.startY = (this.height - actualHeight) / 2;
        this.isDraggingSlider = false;
        this.dragPosition = -1;

        // Initialize the Spotify Link Input Box
        this.linkField = new net.minecraft.client.gui.components.EditBox(
            this.font,
            this.startX + 20,
            this.startY + 48,
            240,
            16,
            Component.literal("Spotify Link")
        );
        this.linkField.setMaxLength(256);
        this.linkField.setVisible(isSetupOpen);
        this.linkField.setEditable(isSetupOpen);
        this.linkField.setFocused(isSetupOpen);
        this.addRenderableWidget(this.linkField);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Ensure texture is updated if changed in MediaManager
        if (this.minecraft != null) {
            MediaManager.updateArtworkTexture(this.minecraft);
        }

        // 1. Draw Card Background (stretched album art if available)
        Identifier artId = MediaManager.currentArtworkIdentifier;
        if (artId != null && MediaManager.artworkWidth > 0 && MediaManager.artworkHeight > 0) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, artId, startX, startY, 0.0f, 0.0f, CARD_WIDTH, CARD_HEIGHT, MediaManager.artworkWidth, MediaManager.artworkHeight, MediaManager.artworkWidth, MediaManager.artworkHeight);
        }
        
        // Draw standard darkening overlay so text remains readable (rounded rectangle)
        drawRoundedRect(guiGraphics, startX, startY, CARD_WIDTH, CARD_HEIGHT, 0xA01E1E1E);

        // Draw Card Border (subtle semi-transparent Glassmorphic rounded outline)
        drawRoundedOutline(guiGraphics, startX, startY, CARD_WIDTH, CARD_HEIGHT, 0x33FFFFFF);

        // Calculate top-right menu button hover state
        int menuX = startX + CARD_WIDTH - 25;
        int menuY = startY + 12;
        boolean isMenuHovered = isHovering(mouseX, mouseY, menuX - 3, menuY - 3, 16, 16);

        if (isSetupOpen) {
            int setupHeight = 220;
            // Draw SpotDL Setup Panel (solid dark glass layer)
            drawRoundedRect(guiGraphics, startX + 5, startY + 5, CARD_WIDTH - 10, setupHeight, 0xF5121212);
            drawRoundedOutline(guiGraphics, startX + 5, startY + 5, CARD_WIDTH - 10, setupHeight, 0x22FFFFFF);

            // Render visible screen widgets (the EditBox) manually to avoid Screen's background blur
            if (this.linkField != null && this.linkField.isVisible()) {
                this.linkField.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }
                // Title
                String titleStr = "Spotify Mod Setup";
                if (SpotDLDownloader.isDownloading) {
                    long cycle = (System.currentTimeMillis() / 250) % 4;
                    String dots = "";
                    if (cycle == 1) dots = ".";
                    else if (cycle == 2) dots = "..";
                    else if (cycle == 3) dots = "...";
                    titleStr = "Spotify Mod Setup (Downloading" + dots + ")";
                }
                int titleWidth = this.font.width(titleStr);
                guiGraphics.text(this.font, titleStr, startX + (CARD_WIDTH - titleWidth) / 2, startY + 14, 0xFF3897F0, false);
                
                // Draw Close Button (X)
                int closeColor = isMenuHovered ? 0xFFFF2D55 : 0xFF888888;
                drawCloseIcon(guiGraphics, menuX, menuY + 1, closeColor);

                // Draw Input Labels
                int labelWidth = this.font.width("Spotify Link");
                guiGraphics.text(this.font, "Spotify Link", startX + (CARD_WIDTH - labelWidth) / 2, startY + 36, 0xFFAAAAAA, false);

                // Save button
                int saveX = startX + (CARD_WIDTH - 100) / 2;
                int saveY = startY + 86;
                boolean isSaveHovered = isHovering(mouseX, mouseY, saveX, saveY - 2, 100, 16);
                guiGraphics.fill(saveX, saveY - 2, saveX + 100, saveY + 12, isSaveHovered ? 0x33FFFFFF : 0x1AFFFFFF);
                int saveTextWidth = this.font.width("Download Sound");
                guiGraphics.text(this.font, "Download Sound", saveX + (100 - saveTextWidth) / 2, saveY + 2, isSaveHovered ? 0xFFFFFFFF : 0xFFCCCCCC, false);
                
                String linkText = "Open Spotify";
                int linkWidth = this.font.width(linkText);
                int linkX = startX + (CARD_WIDTH - linkWidth) / 2;
                boolean isLinkHovered = isHovering(mouseX, mouseY, linkX, startY + 110, linkWidth, 10);
                guiGraphics.text(this.font, linkText, linkX, startY + 110, isLinkHovered ? 0xFFFFFFFF : 0xFFAAAAAA, false);
                if (isLinkHovered) {
                    guiGraphics.fill(linkX, startY + 120, linkX + linkWidth, startY + 121, 0xFFFFFFFF);
                }

                // Display currently selected song at the bottom
                String activeText = "Active: " + (MediaManager.activeKillSoundFile.isEmpty() ? "None" : MediaManager.activeKillSoundFile);
                String truncatedActive = truncateString(activeText, CARD_WIDTH - 40);
                int activeWidth = this.font.width(truncatedActive);
                guiGraphics.text(this.font, truncatedActive, startX + (CARD_WIDTH - activeWidth) / 2, startY + 125, 0xFF888888, false);

                // Draw Gallery
                int gTitleWidth = this.font.width("Downloaded Sounds");
                guiGraphics.text(this.font, "Downloaded Sounds", startX + (CARD_WIDTH - gTitleWidth) / 2, startY + 145, 0xFFAAAAAA, false);
                java.util.List<String> sounds = LocalSoundPlayer.getAvailableSounds();
                int listY = startY + 160;
                if (sounds.isEmpty()) {
                    int noSoundsWidth = this.font.width("No sounds downloaded yet.");
                    guiGraphics.text(this.font, "No sounds downloaded yet.", startX + (CARD_WIDTH - noSoundsWidth) / 2, listY, 0xFF666666, false);
                } else {
                    for (int i = 0; i < Math.min(sounds.size(), 3); i++) {
                        String sound = sounds.get(i);
                        String truncated = truncateString(sound, CARD_WIDTH - 80);
                        int sWidth = this.font.width(truncated);
                        int spacing = 6;
                        
                        boolean isSliderOpen = (activeVolumeSliderIndex == i);
                        int playIconWidth = 7;
                        int speakerIconWidth = 7;
                        int micIconWidth = 7;
                        int volumeSliderWidth = 40;
                        
                        int binIconWidth = 9;
                        int scissorsIconWidth = 9;
                        
                        int textX, playX, speakerX, micX, scissorsX, binX;
                        int totalRowWidth;
                        
                        if (isSliderOpen) {
                            totalRowWidth = sWidth + spacing + volumeSliderWidth + spacing + speakerIconWidth + spacing + micIconWidth + spacing + scissorsIconWidth + spacing + binIconWidth;
                        } else {
                            totalRowWidth = sWidth + spacing + playIconWidth + spacing + speakerIconWidth + spacing + micIconWidth + spacing + scissorsIconWidth + spacing + binIconWidth;
                        }
                        
                        int rowStartX = startX + (CARD_WIDTH - totalRowWidth) / 2;
                        textX = rowStartX;
                        
                        boolean isRowHovered = isHovering(mouseX, mouseY, startX + 20, listY, CARD_WIDTH - 40, 14);
                        boolean isActive = sound.equals(MediaManager.activeKillSoundFile);
                        
                        if (isSliderOpen) {
                            int sliderStartX = rowStartX + sWidth + spacing;
                            int sliderY = listY + 7;
                            speakerX = sliderStartX + volumeSliderWidth + spacing;
                            micX = speakerX + speakerIconWidth + spacing;
                            scissorsX = micX + micIconWidth + spacing;
                            binX = scissorsX + scissorsIconWidth + spacing;
                            
                            boolean isSpeakerHovered = isHovering(mouseX, mouseY, speakerX - 2, listY, speakerIconWidth + 4, 14);
                            boolean isMicHovered = isHovering(mouseX, mouseY, micX - 2, listY, micIconWidth + 4, 14);
                            boolean isScissorsHovered = isHovering(mouseX, mouseY, scissorsX - 2, listY, scissorsIconWidth + 4, 14);
                            boolean isBinHovered = isHovering(mouseX, mouseY, binX - 2, listY, binIconWidth + 4, 14);
                            
                            int speakerColor = isSpeakerHovered ? 0xFFFFFFFF : 0xFF1DB954;
                            boolean isMicActive = sound.equals(currentMicFile) && VoicechatAudioQueue.isPlaying();
                            int micColor = isMicActive ? 0xFF1DB954 : (isMicHovered ? 0xFFFFFFFF : 0xFF888888);
                            int scissorsColor = isScissorsHovered ? 0xFFFFFFFF : 0xFF888888;
                            int binColor = isBinHovered ? 0xFFFF5555 : 0xFF888888;
                            int textColor = isActive ? 0xFF1DB954 : (isRowHovered ? 0xFFFFFFFF : 0xFFCCCCCC);
                            
                            guiGraphics.text(this.font, truncated, textX, listY + 3, textColor, false);
                            
                            // Draw slider track
                            guiGraphics.fill(sliderStartX, sliderY, sliderStartX + volumeSliderWidth, sliderY + 2, 0x33FFFFFF);
                            int filledWidth = (int) (volumeSliderWidth * LocalSoundPlayer.previewVolume);
                            guiGraphics.fill(sliderStartX, sliderY, sliderStartX + filledWidth, sliderY + 2, 0xFFFFFFFF);
                            int handleX = sliderStartX + filledWidth;
                            guiGraphics.fill(handleX - 1, sliderY - 2, handleX + 2, sliderY + 4, 0xFFFFFFFF);
                            
                            drawSpeakerIcon(guiGraphics, speakerX, listY + 3, speakerColor);
                            if (isMicActive) {
                                drawMicIcon(guiGraphics, micX, listY + 3, micColor);
                            } else {
                                drawMicClosedIcon(guiGraphics, micX, listY + 3, micColor);
                            }
                            drawScissorsIcon(guiGraphics, scissorsX, listY + 3, scissorsColor);
                            drawBinIcon(guiGraphics, binX, listY + 3, binColor);
                            
                            if (isRowHovered) {
                                guiGraphics.fill(startX + 20, listY, startX + CARD_WIDTH - 20, listY + 14, 0x15FFFFFF);
                                if (isMicHovered) {
                                    guiGraphics.fill(micX - 3, listY + 1, micX + micIconWidth + 3, listY + 13, 0x22FFFFFF);
                                }
                                if (isScissorsHovered) {
                                    guiGraphics.fill(scissorsX - 3, listY + 1, scissorsX + scissorsIconWidth + 3, listY + 13, 0x22FFFFFF);
                                }
                                if (isBinHovered) {
                                    guiGraphics.fill(binX - 3, listY + 1, binX + binIconWidth + 3, listY + 13, 0x22FFFFFF);
                                }
                            }
                        } else {
                            playX = rowStartX + sWidth + spacing;
                            speakerX = playX + playIconWidth + spacing;
                            micX = speakerX + speakerIconWidth + spacing;
                            scissorsX = micX + micIconWidth + spacing;
                            binX = scissorsX + scissorsIconWidth + spacing;
                            
                            boolean isPlayHovered = isHovering(mouseX, mouseY, playX - 2, listY, playIconWidth + 4, 14);
                            boolean isSpeakerHovered = isHovering(mouseX, mouseY, speakerX - 2, listY, speakerIconWidth + 4, 14);
                            boolean isMicHovered = isHovering(mouseX, mouseY, micX - 2, listY, micIconWidth + 4, 14);
                            boolean isScissorsHovered = isHovering(mouseX, mouseY, scissorsX - 2, listY, scissorsIconWidth + 4, 14);
                            boolean isBinHovered = isHovering(mouseX, mouseY, binX - 2, listY, binIconWidth + 4, 14);
                            
                            int playColor = isPlayHovered ? 0xFFFFFFFF : 0xFF888888;
                            int speakerColor = isSpeakerHovered ? 0xFFFFFFFF : 0xFF888888;
                            boolean isMicActive = sound.equals(currentMicFile) && VoicechatAudioQueue.isPlaying();
                            int micColor = isMicActive ? 0xFF1DB954 : (isMicHovered ? 0xFFFFFFFF : 0xFF888888);
                            int scissorsColor = isScissorsHovered ? 0xFFFFFFFF : 0xFF888888;
                            int binColor = isBinHovered ? 0xFFFF5555 : 0xFF888888;
                            int textColor = isActive ? 0xFF1DB954 : ((isRowHovered && !isPlayHovered && !isSpeakerHovered && !isMicHovered && !isScissorsHovered && !isBinHovered) ? 0xFFFFFFFF : 0xFFCCCCCC);
                            
                            guiGraphics.text(this.font, truncated, textX, listY + 3, textColor, false);
                            
                            boolean isCurrentlyPlaying = sound.equals(LocalSoundPlayer.currentPlayingFile) && LocalSoundPlayer.isClipPlaying();
                            if (isCurrentlyPlaying) {
                                drawPauseIcon(guiGraphics, playX, listY + 3, playColor);
                            } else {
                                drawPlayIcon(guiGraphics, playX, listY + 3, playColor);
                            }
                            
                            drawSpeakerIcon(guiGraphics, speakerX, listY + 3, speakerColor);
                            if (isMicActive) {
                                drawMicIcon(guiGraphics, micX, listY + 3, micColor);
                            } else {
                                drawMicClosedIcon(guiGraphics, micX, listY + 3, micColor);
                            }
                            drawScissorsIcon(guiGraphics, scissorsX, listY + 3, scissorsColor);
                            drawBinIcon(guiGraphics, binX, listY + 3, binColor);
                            
                            if (isRowHovered) {
                                guiGraphics.fill(startX + 20, listY, startX + CARD_WIDTH - 20, listY + 14, 0x15FFFFFF);
                                if (isPlayHovered) {
                                    guiGraphics.fill(playX - 3, listY + 1, playX + playIconWidth + 3, listY + 13, 0x22FFFFFF);
                                }
                                if (isSpeakerHovered) {
                                    guiGraphics.fill(speakerX - 3, listY + 1, speakerX + speakerIconWidth + 3, listY + 13, 0x22FFFFFF);
                                }
                                if (isMicHovered) {
                                    guiGraphics.fill(micX - 3, listY + 1, micX + micIconWidth + 3, listY + 13, 0x22FFFFFF);
                                }
                                if (isScissorsHovered) {
                                    guiGraphics.fill(scissorsX - 3, listY + 1, scissorsX + scissorsIconWidth + 3, listY + 13, 0x22FFFFFF);
                                }
                                if (isBinHovered) {
                                    guiGraphics.fill(binX - 3, listY + 1, binX + binIconWidth + 3, listY + 13, 0x22FFFFFF);
                                }
                            }
                        }
                        listY += 16;
                    }
                }

                if (showFfmpegWarning) {
                    // Draw Warning Overlay
                    drawRoundedRect(guiGraphics, startX + 20, startY + 20, CARD_WIDTH - 40, 90, 0xFF222222);
                    drawRoundedOutline(guiGraphics, startX + 20, startY + 20, CARD_WIDTH - 40, 90, 0xFF444444);
                    
                    guiGraphics.text(this.font, "FFmpeg Required", startX + 30, startY + 30, 0xFFFF5555, false);
                    guiGraphics.text(this.font, "To download audio, we need", startX + 30, startY + 45, 0xFFCCCCCC, false);
                    guiGraphics.text(this.font, "to download FFmpeg (~80MB).", startX + 30, startY + 55, 0xFFCCCCCC, false);
                    
                    // Accept button
                    boolean isAcceptHovered = isHovering(mouseX, mouseY, startX + 30, startY + 80, 80, 16);
                    guiGraphics.fill(startX + 30, startY + 80, startX + 110, startY + 96, isAcceptHovered ? 0xFF33AA33 : 0xFF228822);
                    int acceptWidth = this.font.width("Accept");
                    guiGraphics.text(this.font, "Accept", startX + 30 + (80 - acceptWidth) / 2, startY + 84, 0xFFFFFFFF, false);
                    
                    // Cancel button
                    boolean isCancelHovered = isHovering(mouseX, mouseY, startX + 120, startY + 80, 80, 16);
                    guiGraphics.fill(startX + 120, startY + 80, startX + 200, startY + 96, isCancelHovered ? 0xFFAA3333 : 0xFF882222);
                    int cancelWidth = this.font.width("Cancel");
                    guiGraphics.text(this.font, "Cancel", startX + 120 + (80 - cancelWidth) / 2, startY + 84, 0xFFFFFFFF, false);
                }
        } else {
            // --- Standard Media Card View ---
            if (this.linkField != null && this.linkField.isVisible()) {
                this.linkField.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            }
            
            // Draw Hamburger Icon in top right
            int menuColor = isMenuHovered ? 0xFFFFFFFF : 0xFF888888;
            drawHamburgerIcon(guiGraphics, menuX, menuY, menuColor);

            // Draw Top Source Header (e.g. Spotify)
            String sourceName = "Select App";
            if (MediaManager.hasSession) {
                sourceName = MediaManager.source;
                if (sourceName.toLowerCase().endsWith(".exe")) {
                    sourceName = sourceName.substring(0, sourceName.length() - 4);
                }
                if (!sourceName.isEmpty()) {
                    sourceName = sourceName.substring(0, 1).toUpperCase() + sourceName.substring(1);
                } else {
                    sourceName = "System";
                }
            }
            
            String headerText = "🎵 " + sourceName + "  ▼";
            int headerTextWidth = this.font.width(headerText);
            boolean isHeaderHovered = isHovering(mouseX, mouseY, startX + 15, startY + 8, headerTextWidth, 16);
            guiGraphics.text(this.font, headerText, startX + 15, startY + 12, isHeaderHovered ? 0xFFFFFFFF : 0xFFAAAAAA, false);

            if (!MediaManager.hasSession) {
                guiGraphics.text(this.font, "No media playing", startX + CARD_WIDTH / 2 - this.font.width("No media playing") / 2, startY + CARD_HEIGHT / 2 - 10, 0xFFFFFFFF, false);
                guiGraphics.text(this.font, "Click top-left to select application", startX + CARD_WIDTH / 2 - this.font.width("Click top-left to select application") / 2, startY + CARD_HEIGHT / 2 + 5, 0xFFAAAAAA, false);
            } else {
                // --- Draw actual Album Art on the right ---
                int rightArtSize = 60;
                int rightArtX = startX + CARD_WIDTH - rightArtSize - 15;
                int rightArtY = startY + 15;
                if (artId != null && MediaManager.artworkWidth > 0 && MediaManager.artworkHeight > 0) {
                    guiGraphics.blit(RenderPipelines.GUI_TEXTURED, artId, rightArtX, rightArtY, 0.0f, 0.0f, rightArtSize, rightArtSize, MediaManager.artworkWidth, MediaManager.artworkHeight, MediaManager.artworkWidth, MediaManager.artworkHeight);
                } else {
                    // Fallback artwork box
                    guiGraphics.fill(rightArtX, rightArtY, rightArtX + rightArtSize, rightArtY + rightArtSize, 0xFF2A2A2A);
                    guiGraphics.centeredText(this.font, "🎵", rightArtX + rightArtSize / 2, rightArtY + rightArtSize / 2 - 4, 0xFFAAAAAA);
                }

                // 4. Draw Song Info (Title & Artist) - Aligned to left and safe from overlapping
                int textX = startX + 15;
                int maxTextWidth = CARD_WIDTH - rightArtSize - 40; // 180 pixels (no overlap with art)
                
                String titleStr = truncateString(MediaManager.title, maxTextWidth);
                String artistStr = truncateString(MediaManager.artist, maxTextWidth);
                
                guiGraphics.text(this.font, titleStr, textX, startY + 32, 0xFFFFFFFF, false);
                guiGraphics.text(this.font, artistStr, textX, startY + 46, 0xFF999999, false);

                // 5. Draw Progress Timeline Slider
                int sliderX = startX + 15;
                int sliderWidth = CARD_WIDTH - 30; // 250
                int sliderY = startY + 80;
                
                double currentPosition = dragPosition >= 0 ? dragPosition : MediaManager.getCurrentTrackPosition();
                double duration = MediaManager.duration;
                
                double progressRatio = duration > 0 ? (currentPosition / duration) : 0.0;
                progressRatio = Math.max(0.0, Math.min(1.0, progressRatio));
                
                int filledWidth = (int) (sliderWidth * progressRatio);
                
                // Draw background slider track (semi-transparent white)
                guiGraphics.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + 3, 0x33FFFFFF);
                // Draw filled progress bar (solid white)
                guiGraphics.fill(sliderX, sliderY, sliderX + filledWidth, sliderY + 3, 0xFFFFFFFF);
                // Draw slider thumb (white circle/square)
                int thumbX = sliderX + filledWidth;
                guiGraphics.fill(thumbX - 2, sliderY - 1, thumbX + 2, sliderY + 4, 0xFFFFFFFF);

                // Draw times
                String currentStr = formatTime(currentPosition);
                String durationStr = formatTime(duration);
                
                guiGraphics.text(this.font, currentStr, sliderX, startY + 88, 0xFF888888, false);
                guiGraphics.text(this.font, durationStr, sliderX + sliderWidth - this.font.width(durationStr), startY + 88, 0xFF888888, false);

                // 6. Draw Control Buttons (Interactive)
                int btnY = startY + 105;
                
                // Heart Button (Favorite)
                int heartX = startX + 70;
                boolean heartHovered = isHovering(mouseX, mouseY, heartX - 4, btnY - 2, 14, 14);
                int heartColor = isFavorited ? 0xFFFF2D55 : (heartHovered ? 0xFFFFFFFF : 0xFF888888);
                drawHeartIcon(guiGraphics, heartX, btnY, heartColor, isFavorited);
                
                // Previous Button
                int prevX = startX + 105;
                boolean prevHovered = isHovering(mouseX, mouseY, prevX - 2, btnY - 2, 14, 14);
                drawPrevIcon(guiGraphics, prevX, btnY, prevHovered ? 0xFFFFFFFF : 0xFF888888);
                
                // Play / Pause Button
                int playX = startX + 135;
                boolean playHovered = isHovering(mouseX, mouseY, playX - 2, btnY - 4, 16, 16);
                if (MediaManager.isPlaying) {
                    drawPauseIcon(guiGraphics, playX, btnY, playHovered ? 0xFFFFFFFF : 0xFF888888);
                } else {
                    drawPlayIcon(guiGraphics, playX, btnY, playHovered ? 0xFFFFFFFF : 0xFF888888);
                }
                
                // Next Button
                int nextX = startX + 165;
                boolean nextHovered = isHovering(mouseX, mouseY, nextX - 2, btnY - 2, 14, 14);
                drawNextIcon(guiGraphics, nextX, btnY, nextHovered ? 0xFFFFFFFF : 0xFF888888);
                
                // Shuffle Button
                int shuffleX = startX + 195;
                boolean shuffleHovered = isHovering(mouseX, mouseY, shuffleX - 4, btnY - 2, 16, 14);
                int shuffleColor = isShuffleEnabled ? 0xFF2196F3 : (shuffleHovered ? 0xFFFFFFFF : 0xFF888888);
                drawShuffleIcon(guiGraphics, shuffleX, btnY, shuffleColor);
            }

            // Draw Dropdown Overlay if open
            if (isDropdownOpen) {
                java.util.List<MediaManager.MediaSession> sessions = new java.util.ArrayList<>(MediaManager.activeSessions);
                sessions.add(new MediaManager.MediaSession("stored_songs", "Stored songs", "", MediaManager.isStoredSongsActive));
                
                int dropdownX = startX + 15;
                int dropdownY = startY + 25;
                int dropdownWidth = 140;
                int rowHeight = 18;
                int dropdownHeight = Math.max(20, sessions.size() * rowHeight + 4);

                // Draw dropdown background (translucent solid dark grey)
                guiGraphics.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, 0xF51A1A1A);
                // Draw dropdown border
                guiGraphics.fill(dropdownX - 1, dropdownY - 1, dropdownX, dropdownY + dropdownHeight + 1, 0x44FFFFFF);
                guiGraphics.fill(dropdownX + dropdownWidth, dropdownY - 1, dropdownX + dropdownWidth + 1, dropdownY + dropdownHeight + 1, 0x44FFFFFF);
                guiGraphics.fill(dropdownX, dropdownY - 1, dropdownX + dropdownWidth, dropdownY, 0x44FFFFFF);
                guiGraphics.fill(dropdownX, dropdownY + dropdownHeight, dropdownX + dropdownWidth, dropdownY + dropdownHeight + 1, 0x44FFFFFF);

                for (int i = 0; i < sessions.size(); i++) {
                    MediaManager.MediaSession sess = sessions.get(i);
                    int rowY = dropdownY + 2 + i * rowHeight;
                    boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth && mouseY >= rowY && mouseY < rowY + rowHeight;
                    
                    if (hovered) {
                        guiGraphics.fill(dropdownX + 1, rowY, dropdownX + dropdownWidth - 1, rowY + rowHeight, 0x22FFFFFF);
                    }
                    
                    // Draw icon (12x12)
                    int iconX = dropdownX + 6;
                    int iconY = rowY + 3;
                    if (sess.iconIdentifier != null) {
                        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, sess.iconIdentifier, iconX, iconY, 0.0f, 0.0f, 12, 12, 32, 32, 32, 32);
                    } else {
                        guiGraphics.text(this.font, "🎵", iconX - 1, iconY - 1, 0xFFAAAAAA, false);
                    }
                    
                    // Draw app name
                    String displayName = truncateString(sess.name, 110);
                    int nameColor = sess.isActive ? 0xFF3897F0 : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
                    guiGraphics.text(this.font, displayName, dropdownX + 22, rowY + 5, nameColor, false);
                }
            }
        }
    }

    private void drawPlayIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 4, y + 8, color);
        g.fill(x + 4, y + 1, x + 5, y + 7, color);
        g.fill(x + 5, y + 2, x + 6, y + 6, color);
        g.fill(x + 6, y + 3, x + 7, y + 5, color);
    }

    private void drawPauseIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y, x + 3, y + 8, color);
        g.fill(x + 5, y, x + 7, y + 8, color);
    }

    private void drawPrevIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 2, y + 8, color);
        g.fill(x + 3, y + 3, x + 4, y + 5, color);
        g.fill(x + 4, y + 2, x + 5, y + 6, color);
        g.fill(x + 5, y + 1, x + 6, y + 7, color);
        g.fill(x + 6, y, x + 10, y + 8, color);
    }

    private void drawNextIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 4, y + 8, color);
        g.fill(x + 4, y + 1, x + 5, y + 7, color);
        g.fill(x + 5, y + 2, x + 6, y + 6, color);
        g.fill(x + 6, y + 3, x + 7, y + 5, color);
        g.fill(x + 8, y, x + 10, y + 8, color);
    }

    private void drawHeartIcon(GuiGraphicsExtractor g, int x, int y, int color, boolean solid) {
        if (solid) {
            g.fill(x + 1, y, x + 3, y + 1, color);
            g.fill(x + 5, y, x + 7, y + 1, color);
            g.fill(x, y + 1, x + 8, y + 4, color);
            g.fill(x + 1, y + 4, x + 7, y + 5, color);
            g.fill(x + 2, y + 5, x + 6, y + 6, color);
            g.fill(x + 3, y + 6, x + 5, y + 7, color);
        } else {
            g.fill(x + 1, y, x + 3, y + 1, color);
            g.fill(x + 5, y, x + 7, y + 1, color);
            g.fill(x, y + 1, x + 1, y + 4, color);
            g.fill(x + 7, y + 1, x + 8, y + 4, color);
            g.fill(x + 3, y + 1, x + 5, y + 2, color);
            g.fill(x + 1, y + 4, x + 2, y + 5, color);
            g.fill(x + 6, y + 4, x + 7, y + 5, color);
            g.fill(x + 2, y + 5, x + 3, y + 6, color);
            g.fill(x + 5, y + 5, x + 6, y + 6, color);
            g.fill(x + 3, y + 6, x + 5, y + 7, color);
        }
    }

    private void drawShuffleIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y + 2, x + 8, y + 3, color);
        g.fill(x + 6, y + 1, x + 7, y + 2, color);
        g.fill(x + 6, y + 3, x + 7, y + 4, color);
        
        g.fill(x + 2, y + 5, x + 10, y + 6, color);
        g.fill(x + 3, y + 4, x + 4, y + 5, color);
        g.fill(x + 3, y + 6, x + 4, y + 7, color);
    }

    private void drawBinIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 2, y, x + 7, y + 1, color);
        g.fill(x + 3, y - 1, x + 6, y, color);
        g.fill(x + 2, y + 2, x + 7, y + 8, color);
        g.fill(x + 3, y + 3, x + 4, y + 7, 0x000000);
        g.fill(x + 5, y + 3, x + 6, y + 7, 0x000000);
    }

    private void drawScissorsIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 3, y + 3, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0x000000);
        g.fill(x, y + 5, x + 3, y + 8, color);
        g.fill(x + 1, y + 6, x + 2, y + 7, 0x000000);
        g.fill(x + 3, y + 3, x + 8, y + 1, color);
        g.fill(x + 3, y + 4, x + 8, y + 7, color);
        g.fill(x + 3, y + 3, x + 4, y + 4, 0x000000);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        
        // Calculate menu (top right hamburger / close button) bounds
        int menuX = startX + CARD_WIDTH - 25;
        int menuY = startY + 12;
        boolean clickedMenu = mouseX >= menuX - 3 && mouseX <= menuX + 13 && mouseY >= menuY - 3 && mouseY <= menuY + 13;

        if (clickedMenu) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            isSetupOpen = !isSetupOpen;
            updateWidgetVisibilities();
            return true;
        }

        if (isSetupOpen) {
            if (showFfmpegWarning) {
                if (mouseX >= startX + 30 && mouseX <= startX + 110 && mouseY >= startY + 80 && mouseY <= startY + 96) {
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    String link = linkField.getValue().trim();
                    if (link.isEmpty() && !MediaManager.title.isEmpty() && !MediaManager.artist.isEmpty()) {
                        link = MediaManager.artist + " " + MediaManager.title;
                    }
                    SpotDLDownloader.downloadLink(link);
                    showFfmpegWarning = false;
                    isSetupOpen = false;
                    updateWidgetVisibilities();
                    return true;
                }
                if (mouseX >= startX + 120 && mouseX <= startX + 200 && mouseY >= startY + 80 && mouseY <= startY + 96) {
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    showFfmpegWarning = false;
                    return true;
                }
                return true; // block other clicks
            }

            // Gallery click handler
            java.util.List<String> sounds = LocalSoundPlayer.getAvailableSounds();
            int listY = startY + 160;
            for (int i = 0; i < Math.min(sounds.size(), 3); i++) {
                String sound = sounds.get(i);
                String truncated = truncateString(sound, CARD_WIDTH - 80);
                int sWidth = this.font.width(truncated);
                int spacing = 6;
                
                boolean isSliderOpen = (activeVolumeSliderIndex == i);
                int playIconWidth = 7;
                int speakerIconWidth = 7;
                int micIconWidth = 7;
                int scissorsIconWidth = 9;
                int binIconWidth = 9;
                int volumeSliderWidth = 40;
                
                int playX, speakerX, micX, scissorsX, binX, sliderStartX = 0;
                if (isSliderOpen) {
                    sliderStartX = startX + (CARD_WIDTH - (sWidth + spacing + volumeSliderWidth + spacing + speakerIconWidth + spacing + micIconWidth + spacing + scissorsIconWidth + spacing + binIconWidth)) / 2 + sWidth + spacing;
                    speakerX = sliderStartX + volumeSliderWidth + spacing;
                    micX = speakerX + speakerIconWidth + spacing;
                    scissorsX = micX + micIconWidth + spacing;
                    binX = scissorsX + scissorsIconWidth + spacing;
                } else {
                    playX = startX + (CARD_WIDTH - (sWidth + spacing + playIconWidth + spacing + speakerIconWidth + spacing + micIconWidth + spacing + scissorsIconWidth + spacing + binIconWidth)) / 2 + sWidth + spacing;
                    speakerX = playX + playIconWidth + spacing;
                    micX = speakerX + speakerIconWidth + spacing;
                    scissorsX = micX + micIconWidth + spacing;
                    binX = scissorsX + scissorsIconWidth + spacing;
                }
                
                if (mouseY >= listY && mouseY <= listY + 14) {
                    if (isSliderOpen) {
                        // Speaker is open: check if clicked speaker icon to close it
                        if (mouseX >= speakerX - 3 && mouseX <= speakerX + speakerIconWidth + 3) {
                            activeVolumeSliderIndex = -1;
                            isDraggingVolumeSlider = false;
                            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            return true;
                        }
                        // Check if clicked inside slider
                        if (mouseX >= sliderStartX && mouseX <= sliderStartX + volumeSliderWidth) {
                            isDraggingVolumeSlider = true;
                            float vol = (float) (mouseX - sliderStartX) / volumeSliderWidth;
                            LocalSoundPlayer.setVolume(vol);
                            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            return true;
                        }
                        // Clicked outside slider/speaker: close it!
                        activeVolumeSliderIndex = -1;
                        isDraggingVolumeSlider = false;
                    } else {
                        // Slider is closed: check play icon
                        playX = startX + (CARD_WIDTH - (sWidth + spacing + playIconWidth + spacing + speakerIconWidth + spacing + micIconWidth + spacing + scissorsIconWidth + spacing + binIconWidth)) / 2 + sWidth + spacing;
                        if (mouseX >= playX - 3 && mouseX <= playX + playIconWidth + 3) {
                            if (sound.equals(LocalSoundPlayer.currentPlayingFile) && LocalSoundPlayer.isClipPlaying()) {
                                LocalSoundPlayer.pauseSound();
                            } else {
                                LocalSoundPlayer.playKillSound(sound, true);
                            }
                            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            return true;
                        }
                        // Check speaker icon
                        if (mouseX >= speakerX - 3 && mouseX <= speakerX + speakerIconWidth + 3) {
                            activeVolumeSliderIndex = i;
                            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            return true;
                        }
                    }
                    
                    // Check mic toggle icon (available in both slider-open and slider-closed states)
                    if (mouseX >= micX - 3 && mouseX <= micX + micIconWidth + 3) {
                        if (sound.equals(currentMicFile) && VoicechatAudioQueue.isPlaying()) {
                            // Currently transmitting this sound — stop it
                            VoicechatAudioQueue.stop();
                            currentMicFile = "";
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7d[SpotifyMod] \u00a7cStopped mic transmission."));
                            }
                        } else {
                            // Start transmitting this sound
                            VoicechatAudioQueue.stop();
                            currentMicFile = sound;
                            VoicechatAudioQueue.playSound(sound);
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7d[SpotifyMod] \u00a7ePlaying '" + sound + "' over microphone..."));
                            }
                        }
                        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                    
                    // Check Scissors click
                    if (mouseX >= scissorsX - 3 && mouseX <= scissorsX + scissorsIconWidth + 3) {
                        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        this.minecraft.setScreen(new AudioCropScreen(this, sound));
                        return true;
                    }
                    
                    // Check Bin click
                    if (mouseX >= binX - 3 && mouseX <= binX + binIconWidth + 3) {
                        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        File runDir = new File(System.getProperty("user.dir"));
                        File soundFile = new File(new File(runDir, "killsounds"), sound);
                        if (soundFile.exists()) {
                            soundFile.delete();
                            if (sound.equals(MediaManager.activeKillSoundFile)) {
                                MediaManager.activeKillSoundFile = "";
                            }
                            if (sound.equals(LocalSoundPlayer.currentPlayingFile)) {
                                LocalSoundPlayer.stopSound();
                            }
                        }
                        return true;
                    }
                    
                    // Otherwise select active sound
                    if (mouseX >= startX + 20 && mouseX <= startX + CARD_WIDTH - 20) {
                        MediaManager.activeKillSoundFile = sound;
                        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                }
                listY += 16;
            }

            // Download button handler
            int saveX = startX + (CARD_WIDTH - 100) / 2;
            int saveY = startY + 86;
            if (mouseX >= saveX && mouseX <= saveX + 100 && mouseY >= saveY - 2 && mouseY <= saveY + 12) {
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (!SpotDLDownloader.hasCheckedFfmpeg) {
                    showFfmpegWarning = true;
                    return true;
                }
                String link = linkField.getValue().trim();
                if (link.isEmpty() && !MediaManager.title.isEmpty() && !MediaManager.artist.isEmpty()) {
                    link = MediaManager.artist + " " + MediaManager.title;
                }
                if (!link.isEmpty()) {
                    SpotDLDownloader.downloadLink(link);
                    isSetupOpen = false;
                    updateWidgetVisibilities();
                }
            }
            // Link handler
            String linkText = "Open Spotify";
            int linkWidth = this.font.width(linkText);
            int linkX = startX + (CARD_WIDTH - linkWidth) / 2;
            if (mouseX >= linkX && mouseX <= linkX + linkWidth && mouseY >= startY + 110 && mouseY <= startY + 120) {
                try {
                    net.minecraft.util.Util.getPlatform().openUri(new java.net.URI("https://open.spotify.com"));
                } catch (Exception e) {}
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        // Calculate header toggle button bounds
        String sourceName = "Select App";
        if (MediaManager.hasSession) {
            sourceName = MediaManager.source;
            if (sourceName.toLowerCase().endsWith(".exe")) {
                sourceName = sourceName.substring(0, sourceName.length() - 4);
            }
            if (!sourceName.isEmpty()) {
                sourceName = sourceName.substring(0, 1).toUpperCase() + sourceName.substring(1);
            } else {
                sourceName = "System";
            }
        }
        String headerText = "🎵 " + sourceName + "  ▼";
        int headerTextWidth = this.font.width(headerText);
        boolean clickedToggle = mouseX >= startX + 15 && mouseX <= startX + 15 + headerTextWidth && mouseY >= startY + 8 && mouseY <= startY + 24;

        if (isDropdownOpen) {
            java.util.List<MediaManager.MediaSession> sessions = new java.util.ArrayList<>(MediaManager.activeSessions);
            sessions.add(new MediaManager.MediaSession("stored_songs", "Stored songs", "", MediaManager.isStoredSongsActive));
            
            int dropdownX = startX + 15;
            int dropdownY = startY + 25;
            int dropdownWidth = 140;
            int rowHeight = 18;
            int dropdownHeight = Math.max(20, sessions.size() * rowHeight + 4);

            if (mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth && mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight) {
                int clickedRow = (int) ((mouseY - (dropdownY + 2)) / rowHeight);
                if (clickedRow >= 0 && clickedRow < sessions.size()) {
                    MediaManager.MediaSession sess = sessions.get(clickedRow);
                    if (sess.id.equals("stored_songs")) {
                        MediaManager.isStoredSongsActive = true;
                        MediaManager.updateStoredSongsState();
                    } else {
                        MediaManager.isStoredSongsActive = false;
                        MediaManager.sendCommand("switch " + sess.id);
                    }
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                isDropdownOpen = false;
                return true;
            } else {
                isDropdownOpen = false;
                if (clickedToggle) {
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        } else {
            if (clickedToggle) {
                isDropdownOpen = true;
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        
        if (!MediaManager.hasSession) {
            return super.mouseClicked(event, doubleClick);
        }
        
        int sliderX = startX + 15;
        int sliderWidth = CARD_WIDTH - 30;
        int sliderY = startY + 80;

        // Progress bar click
        if (mouseX >= sliderX && mouseX <= sliderX + sliderWidth && mouseY >= sliderY - 4 && mouseY <= sliderY + 6) {
            this.isDraggingSlider = true;
            updatePositionFromMouse(mouseX);
            return true;
        }

        int btnY = startY + 105;

        // Heart Button
        if (isHovering(mouseX, mouseY, startX + 70 - 4, btnY - 2, 14, 14)) {
            isFavorited = !isFavorited;
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Previous Button
        if (isHovering(mouseX, mouseY, startX + 105 - 2, btnY - 2, 14, 14)) {
            MediaManager.sendCommand("prev");
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Play/Pause Button
        if (isHovering(mouseX, mouseY, startX + 135 - 2, btnY - 4, 16, 16)) {
            MediaManager.sendCommand("toggle");
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Next Button
        if (isHovering(mouseX, mouseY, startX + 165 - 2, btnY - 2, 14, 14)) {
            MediaManager.sendCommand("next");
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Shuffle Button
        if (isHovering(mouseX, mouseY, startX + 195 - 4, btnY - 2, 16, 14)) {
            isShuffleEnabled = !isShuffleEnabled;
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.isDraggingSlider) {
            this.isDraggingSlider = false;
            if (dragPosition >= 0) {
                MediaManager.sendCommand("seek " + (int) dragPosition);
                dragPosition = -1;
            }
            return true;
        }
        if (this.isDraggingVolumeSlider) {
            this.isDraggingVolumeSlider = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        if (this.isDraggingSlider) {
            updatePositionFromMouse(mouseX);
            return true;
        }
        if (this.isDraggingVolumeSlider && activeVolumeSliderIndex >= 0) {
            java.util.List<String> sounds = LocalSoundPlayer.getAvailableSounds();
            if (activeVolumeSliderIndex < sounds.size()) {
                String sound = sounds.get(activeVolumeSliderIndex);
                String truncated = truncateString(sound, CARD_WIDTH - 80);
                int sWidth = this.font.width(truncated);
                int spacing = 6;
                int volumeSliderWidth = 40;
                int speakerIconWidth = 7;
                int sliderStartX = startX + (CARD_WIDTH - (sWidth + spacing + volumeSliderWidth + spacing + speakerIconWidth)) / 2 + sWidth + spacing;
                
                float vol = (float) (mouseX - sliderStartX) / volumeSliderWidth;
                LocalSoundPlayer.setVolume(vol);
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private void updatePositionFromMouse(double mouseX) {
        int sliderX = startX + 15;
        int sliderWidth = CARD_WIDTH - 30;
        double ratio = (mouseX - sliderX) / (double) sliderWidth;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        this.dragPosition = ratio * MediaManager.duration;
    }

    private boolean isHovering(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private String formatTime(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) {
            return "00:00";
        }
        int totalSecs = (int) seconds;
        int mins = totalSecs / 60;
        int secs = totalSecs % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private String truncateString(String text, int maxWidth) {
        if (text == null) return "";
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = this.font.width(suffix);
        while (text.length() > 0 && this.font.width(text) + suffixWidth > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + suffix;
    }

    private void drawRoundedRect(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        g.fill(x + 2, y, x + width - 2, y + height, color);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, color);
        g.fill(x, y + 2, x + width, y + height - 2, color);
    }

    private void drawRoundedOutline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        g.fill(x + 2, y - 1, x + width - 2, y, color);
        g.fill(x + 2, y + height, x + width - 2, y + height + 1, color);
        g.fill(x - 1, y + 2, x, y + height - 2, color);
        g.fill(x + width, y + 2, x + width + 1, y + height - 2, color);
        
        // Corners
        g.fill(x + 1, y, x + 2, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 2, color);
        g.fill(x + width - 2, y, x + width - 1, y + 1, color);
        g.fill(x + width - 1, y + 1, x + width, y + 2, color);
        g.fill(x + 1, y + height - 1, x + 2, y + height, color);
        g.fill(x, y + height - 2, x + 1, y + height - 1, color);
        g.fill(x + width - 2, y + height - 1, x + width - 1, y + height, color);
        g.fill(x + width - 1, y + height - 2, x + width, y + height - 1, color);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (isSetupOpen) {
            if (showFfmpegWarning) {
                return true; // block keyboard input during warning
            }
            int keyCode = event.key();
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                if (!SpotDLDownloader.hasCheckedFfmpeg) {
                    showFfmpegWarning = true;
                    return true;
                }
                String link = linkField.getValue().trim();
                if (link.isEmpty() && !MediaManager.title.isEmpty() && !MediaManager.artist.isEmpty()) {
                    link = MediaManager.artist + " " + MediaManager.title;
                }
                if (!link.isEmpty()) {
                    SpotDLDownloader.downloadLink(link);
                    isSetupOpen = false;
                    updateWidgetVisibilities();
                }
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                isSetupOpen = false;
                updateWidgetVisibilities();
                return true;
            }
            if (linkField.isFocused()) {
                return linkField.keyPressed(event);
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (isSetupOpen) {
            if (this.linkField.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    private void updateWidgetVisibilities() {
        if (this.minecraft != null) {
            this.init(this.width, this.height);
        }
    }

    private void drawHamburgerIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 10, y + 1, color);
        g.fill(x, y + 3, x + 10, y + 4, color);
        g.fill(x, y + 6, x + 10, y + 7, color);
    }

    private void drawCloseIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        for (int i = 0; i < 7; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, color);
        }
    }

    private void drawSpeakerIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        // Speaker base (2x4 rect)
        g.fill(x, y + 2, x + 2, y + 6, color);
        // Speaker cone (2x8 trapezoid)
        g.fill(x + 2, y + 1, x + 3, y + 7, color);
        g.fill(x + 3, y, x + 4, y + 8, color);
        // Sound wave lines
        g.fill(x + 5, y + 2, x + 6, y + 3, color);
        g.fill(x + 5, y + 5, x + 6, y + 6, color);
        g.fill(x + 6, y + 3, x + 7, y + 5, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // don't pause the game when checking/managing music!
    }

    private void drawMicIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 2, y, x + 5, y + 4, color); // Mic capsule
        g.fill(x + 1, y + 2, x + 2, y + 5, color); // Left curve
        g.fill(x + 5, y + 2, x + 6, y + 5, color); // Right curve
        g.fill(x + 2, y + 5, x + 5, y + 6, color); // Bottom curve
        g.fill(x + 3, y + 6, x + 4, y + 8, color); // Stand
        g.fill(x + 1, y + 7, x + 6, y + 8, color); // Base
    }

    private void drawMicClosedIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        // Same mic shape
        g.fill(x + 2, y, x + 5, y + 4, color); // Mic capsule
        g.fill(x + 1, y + 2, x + 2, y + 5, color); // Left curve
        g.fill(x + 5, y + 2, x + 6, y + 5, color); // Right curve
        g.fill(x + 2, y + 5, x + 5, y + 6, color); // Bottom curve
        g.fill(x + 3, y + 6, x + 4, y + 8, color); // Stand
        g.fill(x + 1, y + 7, x + 6, y + 8, color); // Base
        // Diagonal strikethrough line (red)
        int red = 0xFFFF4444;
        g.fill(x, y + 7, x + 1, y + 8, red);
        g.fill(x + 1, y + 6, x + 2, y + 7, red);
        g.fill(x + 2, y + 5, x + 3, y + 6, red);
        g.fill(x + 3, y + 4, x + 4, y + 5, red);
        g.fill(x + 4, y + 3, x + 5, y + 4, red);
        g.fill(x + 5, y + 2, x + 6, y + 3, red);
        g.fill(x + 6, y + 1, x + 7, y + 2, red);
    }
}
