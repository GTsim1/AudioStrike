package com.example.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import com.example.client.MediaManager;
import com.example.client.MediaControlScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {
    private static final int CARD_WIDTH = 280;
    private static final int CARD_HEIGHT = 130;
    private static final int startX = 0;
    private static final int startY = 0;

    private static boolean isDraggingSlider = false;
    private static double dragPosition = -1;
    private static boolean isDropdownOpen = false;

    private static boolean isDraggingCard = false;
    private static boolean isResizingCard = false;
    private static double dragOffsetX = 0;
    private static double dragOffsetY = 0;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        
        if (MediaManager.cardWidth <= 140) {
            MediaManager.loadLayout();
        }

        
        if (this.minecraft != null) {
            MediaManager.updateArtworkTexture(this.minecraft);
        }

        long windowHandle = getGlfwWindowHandle();
        boolean isShiftHeld = windowHandle != 0L && (org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS || 
                                                    org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS);
        
        if (windowHandle != 0L) {
            boolean isMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(windowHandle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            
            if (isMouseDown) {
                if (isShiftHeld) {
                    if (!isDraggingCard && !isResizingCard) {
                        if (mouseX >= MediaManager.cardX + MediaManager.cardWidth - 15 && mouseX <= MediaManager.cardX + MediaManager.cardWidth + 5 &&
                            mouseY >= MediaManager.cardY + MediaManager.cardHeight - 15 && mouseY <= MediaManager.cardY + MediaManager.cardHeight + 5) {
                            isResizingCard = true;
                            dragOffsetX = mouseX - MediaManager.cardWidth;
                            dragOffsetY = mouseY - MediaManager.cardHeight;
                        } else if (mouseX >= MediaManager.cardX && mouseX <= MediaManager.cardX + MediaManager.cardWidth &&
                                   mouseY >= MediaManager.cardY && mouseY <= MediaManager.cardY + MediaManager.cardHeight) {
                            isDraggingCard = true;
                            dragOffsetX = mouseX - MediaManager.cardX;
                            dragOffsetY = mouseY - MediaManager.cardY;
                        }
                    }
                    
                    if (isDraggingCard) {
                        MediaManager.cardX = (int) (mouseX - dragOffsetX);
                        MediaManager.cardY = (int) (mouseY - dragOffsetY);
                    } else if (isResizingCard) {
                        MediaManager.cardWidth = Math.max(140, (int) (mouseX - dragOffsetX));
                        MediaManager.cardHeight = Math.max(65, (int) (mouseY - dragOffsetY));
                    }
                } else {
                    if (isDraggingSlider) {
                        float scaleX = (float) MediaManager.cardWidth / CARD_WIDTH;
                        double localMouseX = (mouseX - MediaManager.cardX) / scaleX;
                        updatePositionFromMouse(localMouseX);
                    }
                }
            } else {
                if (isDraggingCard || isResizingCard) {
                    isDraggingCard = false;
                    isResizingCard = false;
                    MediaManager.saveLayout();
                }
                if (isDraggingSlider) {
                    isDraggingSlider = false;
                    if (dragPosition >= 0) {
                        MediaManager.sendCommand("seek " + (int) dragPosition);
                        dragPosition = -1;
                    }
                }
            }
        }

        float scaleX = (float) MediaManager.cardWidth / CARD_WIDTH;
        float scaleY = (float) MediaManager.cardHeight / CARD_HEIGHT;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) MediaManager.cardX, (float) MediaManager.cardY);
        guiGraphics.pose().scale(scaleX, scaleY);

        int localMouseX = (int) ((mouseX - MediaManager.cardX) / scaleX);
        int localMouseY = (int) ((mouseY - MediaManager.cardY) / scaleY);

        
        mouseX = localMouseX;
        mouseY = localMouseY;

        
        Identifier artId = MediaManager.currentArtworkIdentifier;
        if (artId != null && MediaManager.artworkWidth > 0 && MediaManager.artworkHeight > 0) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, artId, startX, startY, 0.0f, 0.0f, CARD_WIDTH, CARD_HEIGHT, MediaManager.artworkWidth, MediaManager.artworkHeight, MediaManager.artworkWidth, MediaManager.artworkHeight);
        }
        
        drawRoundedRect(guiGraphics, startX, startY, CARD_WIDTH, CARD_HEIGHT, 0xA01E1E1E);
        drawRoundedOutline(guiGraphics, startX, startY, CARD_WIDTH, CARD_HEIGHT, 0x33FFFFFF);

        int menuX = startX + CARD_WIDTH - 25;
        int menuY = startY + 12;
        boolean isMenuHovered = isHovering(mouseX, mouseY, menuX - 3, menuY - 3, 16, 16);

        int menuColor = isMenuHovered ? 0xFFFFFFFF : 0xFF888888;
        drawHamburgerIcon(guiGraphics, menuX, menuY, menuColor);

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
            int rightArtSize = 60;
            int rightArtX = startX + CARD_WIDTH - rightArtSize - 15;
            int rightArtY = startY + 15;
            if (artId != null && MediaManager.artworkWidth > 0 && MediaManager.artworkHeight > 0) {
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, artId, rightArtX, rightArtY, 0.0f, 0.0f, rightArtSize, rightArtSize, MediaManager.artworkWidth, MediaManager.artworkHeight, MediaManager.artworkWidth, MediaManager.artworkHeight);
            } else {
                guiGraphics.fill(rightArtX, rightArtY, rightArtX + rightArtSize, rightArtY + rightArtSize, 0xFF2A2A2A);
                guiGraphics.centeredText(this.font, "🎵", rightArtX + rightArtSize / 2, rightArtY + rightArtSize / 2 - 4, 0xFFAAAAAA);
            }

            int textX = startX + 15;
            int maxTextWidth = CARD_WIDTH - rightArtSize - 40;
            
            String titleStr = truncateString(MediaManager.title, maxTextWidth);
            String artistStr = truncateString(MediaManager.artist, maxTextWidth);
            
            guiGraphics.text(this.font, titleStr, textX, startY + 32, 0xFFFFFFFF, false);
            guiGraphics.text(this.font, artistStr, textX, startY + 46, 0xFF999999, false);

            int sliderX = startX + 15;
            int sliderWidth = CARD_WIDTH - 30;
            int sliderY = startY + 80;
            
            double currentPosition = dragPosition >= 0 ? dragPosition : MediaManager.getCurrentTrackPosition();
            double duration = MediaManager.duration;
            
            double progressRatio = duration > 0 ? (currentPosition / duration) : 0.0;
            progressRatio = Math.max(0.0, Math.min(1.0, progressRatio));
            
            int filledWidth = (int) (sliderWidth * progressRatio);
            
            guiGraphics.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + 3, 0x33FFFFFF);
            guiGraphics.fill(sliderX, sliderY, sliderX + filledWidth, sliderY + 3, 0xFFFFFFFF);
            int thumbX = sliderX + filledWidth;
            guiGraphics.fill(thumbX - 2, sliderY - 1, thumbX + 2, sliderY + 4, 0xFFFFFFFF);

            String currentStr = formatTime(currentPosition);
            String durationStr = formatTime(duration);
            
            guiGraphics.text(this.font, currentStr, sliderX, startY + 88, 0xFF888888, false);
            guiGraphics.text(this.font, durationStr, sliderX + sliderWidth - this.font.width(durationStr), startY + 88, 0xFF888888, false);

            int btnY = startY + 105;
            
            int heartX = startX + 70;
            boolean heartHovered = isHovering(mouseX, mouseY, heartX - 4, btnY - 2, 14, 14);
            int heartColor = MediaControlScreen.isFavorited ? 0xFFFF2D55 : (heartHovered ? 0xFFFFFFFF : 0xFF888888);
            drawHeartIcon(guiGraphics, heartX, btnY, heartColor, MediaControlScreen.isFavorited);
            
            int prevX = startX + 105;
            boolean prevHovered = isHovering(mouseX, mouseY, prevX - 2, btnY - 2, 14, 14);
            drawPrevIcon(guiGraphics, prevX, btnY, prevHovered ? 0xFFFFFFFF : 0xFF888888);
            
            int playX = startX + 135;
            boolean playHovered = isHovering(mouseX, mouseY, playX - 2, btnY - 4, 16, 16);
            if (MediaManager.isPlaying) {
                drawPauseIcon(guiGraphics, playX, btnY, playHovered ? 0xFFFFFFFF : 0xFF888888);
            } else {
                drawPlayIcon(guiGraphics, playX, btnY, playHovered ? 0xFFFFFFFF : 0xFF888888);
            }
            
            int nextX = startX + 165;
            boolean nextHovered = isHovering(mouseX, mouseY, nextX - 2, btnY - 2, 14, 14);
            drawNextIcon(guiGraphics, nextX, btnY, nextHovered ? 0xFFFFFFFF : 0xFF888888);
            
            int repeatX = startX + 195;
            boolean repeatHovered = isHovering(mouseX, mouseY, repeatX - 4, btnY - 2, 16, 14);
            int repeatColor = MediaControlScreen.isRepeatEnabled ? 0xFF1DB954 : (repeatHovered ? 0xFFFFFFFF : 0xFF888888);
            drawRepeatIcon(guiGraphics, repeatX, btnY, repeatColor);
        }

        if (isDropdownOpen) {
            java.util.List<MediaManager.MediaSession> sessions = new java.util.ArrayList<>(MediaManager.activeSessions);
            sessions.add(new MediaManager.MediaSession("stored_songs", "Stored songs", "", MediaManager.isStoredSongsActive));
            
            int dropdownX = startX + 15;
            int dropdownY = startY + 25;
            int dropdownWidth = 140;
            int rowHeight = 18;
            int dropdownHeight = Math.max(20, sessions.size() * rowHeight + 4);

            guiGraphics.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight, 0xF51A1A1A);
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
                
                int iconX = dropdownX + 6;
                int iconY = rowY + 3;
                if (sess.iconIdentifier != null) {
                    guiGraphics.blit(RenderPipelines.GUI_TEXTURED, sess.iconIdentifier, iconX, iconY, 0.0f, 0.0f, 12, 12, 32, 32, 32, 32);
                } else {
                    guiGraphics.text(this.font, "🎵", iconX - 1, iconY - 1, 0xFFAAAAAA, false);
                }
                
                String displayName = truncateString(sess.name, 110);
                int nameColor = sess.isActive ? 0xFF3897F0 : (hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
                guiGraphics.text(this.font, displayName, dropdownX + 22, rowY + 5, nameColor, false);
            }
        }

        if (isShiftHeld) {
            
            guiGraphics.fill(0, 0, CARD_WIDTH, CARD_HEIGHT, 0x66000000);
            
            
            drawRoundedOutline(guiGraphics, 0, 0, CARD_WIDTH, CARD_HEIGHT, 0xFF1DB954);
            
            
            guiGraphics.fill(CARD_WIDTH - 12, CARD_HEIGHT - 3, CARD_WIDTH - 2, CARD_HEIGHT - 2, 0xFFFFFFFF);
            guiGraphics.fill(CARD_WIDTH - 9, CARD_HEIGHT - 6, CARD_WIDTH - 2, CARD_HEIGHT - 5, 0xFFFFFFFF);
            guiGraphics.fill(CARD_WIDTH - 6, CARD_HEIGHT - 9, CARD_WIDTH - 2, CARD_HEIGHT - 8, 0xFFFFFFFF);
            
            
            String infoText = "Shift + Drag to Move/Resize";
            int infoW = this.font.width(infoText);
            guiGraphics.text(this.font, infoText, CARD_WIDTH / 2 - infoW / 2, CARD_HEIGHT / 2 - 4, 0xFF1DB954, true);
        }

        guiGraphics.pose().popMatrix();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        
        if (MediaManager.cardWidth <= 140) {
            MediaManager.loadLayout();
        }

        double mouseX = event.x();
        double mouseY = event.y();

        long windowHandle = getGlfwWindowHandle();
        boolean isShiftHeld = windowHandle != 0L && (org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS || 
                                                    org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS);

        if (isShiftHeld) {
            
            if (mouseX >= MediaManager.cardX && mouseX <= MediaManager.cardX + MediaManager.cardWidth &&
                mouseY >= MediaManager.cardY && mouseY <= MediaManager.cardY + MediaManager.cardHeight) {
                cir.setReturnValue(true);
            }
            return;
        }

        
        if (mouseX < MediaManager.cardX || mouseX > MediaManager.cardX + MediaManager.cardWidth ||
            mouseY < MediaManager.cardY || mouseY > MediaManager.cardY + MediaManager.cardHeight) {
            return;
        }

        
        float scaleX = (float) MediaManager.cardWidth / CARD_WIDTH;
        float scaleY = (float) MediaManager.cardHeight / CARD_HEIGHT;

        double localMouseX = (mouseX - MediaManager.cardX) / scaleX;
        double localMouseY = (mouseY - MediaManager.cardY) / scaleY;

        
        mouseX = localMouseX;
        mouseY = localMouseY;
        
        int menuX = startX + CARD_WIDTH - 25;
        int menuY = startY + 12;
        boolean clickedMenu = mouseX >= menuX - 3 && mouseX <= menuX + 13 && mouseY >= menuY - 3 && mouseY <= menuY + 13;

        if (clickedMenu) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.setScreen(new MediaControlScreen());
            cir.setReturnValue(true);
            return;
        }

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
                cir.setReturnValue(true);
                return;
            } else {
                isDropdownOpen = false;
                if (clickedToggle) {
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    cir.setReturnValue(true);
                    return;
                }
            }
        } else {
            if (clickedToggle) {
                isDropdownOpen = true;
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                cir.setReturnValue(true);
                return;
            }
        }
        
        if (!MediaManager.hasSession) {
            return;
        }
        
        int sliderX = startX + 15;
        int sliderWidth = CARD_WIDTH - 30;
        int sliderY = startY + 80;

        if (mouseX >= sliderX && mouseX <= sliderX + sliderWidth && mouseY >= sliderY - 4 && mouseY <= sliderY + 6) {
            isDraggingSlider = true;
            updatePositionFromMouse(mouseX);
            cir.setReturnValue(true);
            return;
        }

        int btnY = startY + 105;

        if (isHovering(mouseX, mouseY, startX + 70 - 4, btnY - 2, 14, 14)) {
            MediaManager.toggleLike();
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            cir.setReturnValue(true);
            return;
        }

        if (isHovering(mouseX, mouseY, startX + 105 - 2, btnY - 2, 14, 14)) {
            MediaManager.sendCommand("prev");
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            cir.setReturnValue(true);
            return;
        }

        if (isHovering(mouseX, mouseY, startX + 135 - 2, btnY - 4, 16, 16)) {
            MediaManager.sendCommand("toggle");
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            cir.setReturnValue(true);
            return;
        }

        if (isHovering(mouseX, mouseY, startX + 165 - 2, btnY - 2, 14, 14)) {
            MediaManager.sendCommand("next");
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            cir.setReturnValue(true);
            return;
        }

        if (isHovering(mouseX, mouseY, startX + 195 - 4, btnY - 2, 16, 14)) {
            MediaControlScreen.isRepeatEnabled = !MediaControlScreen.isRepeatEnabled;
            MediaManager.sendCommand("repeat");
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            cir.setReturnValue(true);
            return;
        }
    }

    private void updatePositionFromMouse(double mouseX) {
        int sliderX = startX + 15;
        int sliderWidth = CARD_WIDTH - 30;
        double ratio = (mouseX - sliderX) / (double) sliderWidth;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        dragPosition = ratio * MediaManager.duration;
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
        
        g.fill(x + 1, y, x + 2, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 2, color);
        g.fill(x + width - 2, y, x + width - 1, y + 1, color);
        g.fill(x + width - 1, y + 1, x + width, y + 2, color);
        g.fill(x + 1, y + height - 1, x + 2, y + height, color);
        g.fill(x, y + height - 2, x + 1, y + height - 1, color);
        g.fill(x + width - 2, y + height - 1, x + width - 1, y + height, color);
        g.fill(x + width - 1, y + height - 2, x + width, y + height - 1, color);
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

    private void drawRepeatIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        
        g.fill(x + 2, y + 1, x + 8, y + 2, color);
        g.fill(x + 1, y + 2, x + 2, y + 4, color);
        g.fill(x + 8, y + 2, x + 9, y + 4, color);
        g.fill(x + 6, y, x + 8, y + 1, color);
        g.fill(x + 6, y + 2, x + 8, y + 3, color);
        
        
        g.fill(x + 2, y + 6, x + 8, y + 7, color);
        g.fill(x + 1, y + 4, x + 2, y + 6, color);
        g.fill(x + 8, y + 4, x + 9, y + 6, color);
        g.fill(x + 2, y + 5, x + 4, y + 6, color);
        g.fill(x + 2, y + 7, x + 4, y + 8, color);
    }

    private void drawHamburgerIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 10, y + 1, color);
        g.fill(x, y + 3, x + 10, y + 4, color);
        g.fill(x, y + 6, x + 10, y + 7, color);
    }

    private long getGlfwWindowHandle() {
        if (this.minecraft == null) return 0L;
        Object windowObj = this.minecraft.getWindow();
        if (windowObj == null) return 0L;
        
        
        for (String methodName : new String[]{"getWindow", "getWindowId", "getHandle", "handle"}) {
            try {
                java.lang.reflect.Method method = windowObj.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object res = method.invoke(windowObj);
                if (res instanceof Long) {
                    return (Long) res;
                }
            } catch (Exception e) {
                
            }
        }
        
        
        for (java.lang.reflect.Method method : windowObj.getClass().getDeclaredMethods()) {
            if (method.getReturnType() == long.class && method.getParameterCount() == 0) {
                try {
                    method.setAccessible(true);
                    Object res = method.invoke(windowObj);
                    if (res instanceof Long) {
                        return (Long) res;
                    }
                } catch (Exception e) {
                    
                }
            }
        }
        
        
        for (java.lang.reflect.Field field : windowObj.getClass().getDeclaredFields()) {
            if (field.getType() == long.class) {
                try {
                    field.setAccessible(true);
                    Object res = field.get(windowObj);
                    if (res instanceof Long) {
                        return (Long) res;
                    }
                } catch (Exception e) {
                    
                }
            }
        }
        
        return 0L;
    }
}
