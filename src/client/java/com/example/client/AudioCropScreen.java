package com.example.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.File;

public class AudioCropScreen extends Screen {
    private final Screen parent;
    private final String filename;
    
    private int startX, startY;
    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;
    
    private double durationSec = 0;
    private double startSec = 0;
    private double endSec = 10;
    
    private boolean isDraggingStart = false;
    private boolean isDraggingEnd = false;
    private boolean isDraggingTimeline = false;
    
    private boolean isPreviewing = false;
    private long previewStartTime = 0;

    public AudioCropScreen(Screen parent, String filename) {
        super(Component.literal("Crop Audio"));
        this.parent = parent;
        this.filename = filename;
        
        try {
            File runDir = new File(System.getProperty("user.dir"));
            File soundFile = new File(new File(runDir, "killsounds"), filename);
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(soundFile);
            long frameLength = fileFormat.getFrameLength();
            float frameRate = fileFormat.getFormat().getFrameRate();
            this.durationSec = frameLength / (double) frameRate;
            
            if (this.durationSec > 10) {
                this.endSec = 10;
            } else {
                this.endSec = this.durationSec;
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.durationSec = 60; // fallback
        }
    }

    @Override
    protected void init() {
        this.startX = (this.width - WIDTH) / 2;
        this.startY = (this.height - HEIGHT) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw background
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);
        
        // Draw window
        guiGraphics.fill(startX, startY, startX + WIDTH, startY + HEIGHT, 0xFF121212);
        guiGraphics.fill(startX - 1, startY - 1, startX + WIDTH + 1, startY + HEIGHT + 1, 0x33FFFFFF);
        guiGraphics.fill(startX, startY, startX + WIDTH, startY + HEIGHT, 0x00000000); // clear center border
        
        // Title
        guiGraphics.text(this.font, "Crop Audio (Max 10s)", startX + WIDTH / 2 - this.font.width("Crop Audio (Max 10s)") / 2, startY + 15, 0xFFFFFFFF, false);
        guiGraphics.text(this.font, truncateString(filename, WIDTH - 40), startX + 20, startY + 40, 0xFF888888, false);
        
        // Timeline area
        int tX = startX + 20;
        int tY = startY + 80;
        int tW = WIDTH - 40;
        int tH = 20;
        
        // Draw base timeline
        guiGraphics.fill(tX, tY, tX + tW, tY + tH, 0xFF333333);
        
        // Draw selected region
        int selStartX = tX + (int)((startSec / durationSec) * tW);
        int selEndX = tX + (int)((endSec / durationSec) * tW);
        guiGraphics.fill(selStartX, tY, selEndX, tY + tH, 0xFF1DB954);
        
        // Draw handles
        guiGraphics.fill(selStartX - 3, tY - 2, selStartX + 3, tY + tH + 2, 0xFFFFFFFF); // left handle
        guiGraphics.fill(selEndX - 3, tY - 2, selEndX + 3, tY + tH + 2, 0xFFFFFFFF); // right handle
        
        // Draw times
        String startStr = formatTime(startSec);
        String endStr = formatTime(endSec);
        guiGraphics.text(this.font, startStr, selStartX - this.font.width(startStr)/2, tY - 12, 0xFFFFFFFF, false);
        guiGraphics.text(this.font, endStr, selEndX - this.font.width(endStr)/2, tY - 12, 0xFFFFFFFF, false);
        
        // Draw preview progress if playing
        if (isPreviewing) {
            double elapsed = (System.currentTimeMillis() - previewStartTime) / 1000.0;
            if (elapsed > (endSec - startSec)) {
                isPreviewing = false;
                LocalSoundPlayer.stopSound();
            } else {
                int pX = selStartX + (int)((elapsed / durationSec) * tW);
                guiGraphics.fill(pX, tY, pX + 2, tY + tH, 0xFFFF2D55);
            }
        }
        
        // Buttons
        int btnY = startY + HEIGHT - 40;
        
        // Preview button
        int prevX = startX + 20;
        boolean prevHover = isHovering(mouseX, mouseY, prevX, btnY, 80, 20);
        guiGraphics.fill(prevX, btnY, prevX + 80, btnY + 20, prevHover ? 0xFF555555 : 0xFF333333);
        guiGraphics.text(this.font, isPreviewing ? "Stop" : "Preview", prevX + 40 - this.font.width(isPreviewing ? "Stop" : "Preview")/2, btnY + 6, 0xFFFFFFFF, false);
        
        // Save button
        int saveX = startX + WIDTH - 180;
        boolean saveHover = isHovering(mouseX, mouseY, saveX, btnY, 70, 20);
        guiGraphics.fill(saveX, btnY, saveX + 70, btnY + 20, saveHover ? 0xFF1ED760 : 0xFF1DB954);
        guiGraphics.text(this.font, "Save", saveX + 35 - this.font.width("Save")/2, btnY + 6, 0xFFFFFFFF, false);
        
        // Cancel button
        int cancelX = startX + WIDTH - 100;
        boolean cancelHover = isHovering(mouseX, mouseY, cancelX, btnY, 80, 20);
        guiGraphics.fill(cancelX, btnY, cancelX + 80, btnY + 20, cancelHover ? 0xFF555555 : 0xFF333333);
        guiGraphics.text(this.font, "Cancel", cancelX + 40 - this.font.width("Cancel")/2, btnY + 6, 0xFFFFFFFF, false);
        
        // Handle dragging
        if (isDraggingStart || isDraggingEnd || isDraggingTimeline) {
            double mouseSec = ((double)(mouseX - tX) / tW) * durationSec;
            mouseSec = Math.max(0, Math.min(durationSec, mouseSec));
            
            if (isDraggingStart) {
                startSec = Math.min(mouseSec, endSec - 0.1);
                if (endSec - startSec > 10) {
                    endSec = startSec + 10;
                }
            } else if (isDraggingEnd) {
                endSec = Math.max(mouseSec, startSec + 0.1);
                if (endSec - startSec > 10) {
                    startSec = endSec - 10;
                }
            } else if (isDraggingTimeline) {
                double dur = endSec - startSec;
                startSec = mouseSec - dur / 2;
                endSec = mouseSec + dur / 2;
                if (startSec < 0) {
                    startSec = 0;
                    endSec = dur;
                }
                if (endSec > durationSec) {
                    endSec = durationSec;
                    startSec = durationSec - dur;
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        
        int tX = startX + 20;
        int tY = startY + 80;
        int tW = WIDTH - 40;
        int tH = 20;
        
        int selStartX = tX + (int)((startSec / durationSec) * tW);
        int selEndX = tX + (int)((endSec / durationSec) * tW);
        
        // Check handles
        if (mouseY >= tY - 5 && mouseY <= tY + tH + 5) {
            if (Math.abs(mouseX - selStartX) <= 6) {
                isDraggingStart = true;
                return true;
            } else if (Math.abs(mouseX - selEndX) <= 6) {
                isDraggingEnd = true;
                return true;
            } else if (mouseX > selStartX && mouseX < selEndX) {
                isDraggingTimeline = true;
                return true;
            }
        }
        
        int btnY = startY + HEIGHT - 40;
        // Preview button
        if (isHovering((int)mouseX, (int)mouseY, startX + 20, btnY, 80, 20)) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (isPreviewing) {
                isPreviewing = false;
                LocalSoundPlayer.stopSound();
            } else {
                isPreviewing = true;
                previewStartTime = System.currentTimeMillis();
                LocalSoundPlayer.playPreview(filename, startSec);
            }
            return true;
        }
        
        // Save button
        if (isHovering((int)mouseX, (int)mouseY, startX + WIDTH - 180, btnY, 70, 20)) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            
            // Do crop
            FfmpegHelper.cropAudio(filename, startSec, endSec - startSec);
            MediaManager.activeKillSoundFile = filename;
            
            this.minecraft.setScreen(parent);
            return true;
        }
        
        // Cancel button
        if (isHovering((int)mouseX, (int)mouseY, startX + WIDTH - 100, btnY, 80, 20)) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.setScreen(parent);
            return true;
        }
        
        return super.mouseClicked(event, doubleClick);
    }
    
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        isDraggingStart = false;
        isDraggingEnd = false;
        isDraggingTimeline = false;
        return super.mouseReleased(event);
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    
    private String formatTime(double seconds) {
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        return String.format("%d:%02d", m, s);
    }
    
    private String truncateString(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        String ell = "...";
        int ellWidth = this.font.width(ell);
        while (text.length() > 0 && this.font.width(text) + ellWidth > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ell;
    }
}
