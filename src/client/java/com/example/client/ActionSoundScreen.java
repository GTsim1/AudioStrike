package com.example.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

public class ActionSoundScreen extends Screen {

    private final Screen parent;
    private final String targetSound;
    private final int CARD_WIDTH = 300;
    private final int CARD_HEIGHT = 200;

    public ActionSoundScreen(Screen parent, String targetSound) {
        super(Component.literal("Action Sound Configuration"));
        this.parent = parent;
        this.targetSound = targetSound;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        
        guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);

        int startX = (this.width - CARD_WIDTH) / 2;
        int startY = (this.height - CARD_HEIGHT) / 2;

        
        guiGraphics.fill(startX, startY, startX + CARD_WIDTH, startY + CARD_HEIGHT, 0xF5121212);
        
        guiGraphics.fill(startX, startY, startX + CARD_WIDTH, startY + 1, 0x33FFFFFF);
        guiGraphics.fill(startX, startY + CARD_HEIGHT - 1, startX + CARD_WIDTH, startY + CARD_HEIGHT, 0x33FFFFFF);
        guiGraphics.fill(startX, startY, startX + 1, startY + CARD_HEIGHT, 0x33FFFFFF);
        guiGraphics.fill(startX + CARD_WIDTH - 1, startY, startX + CARD_WIDTH, startY + CARD_HEIGHT, 0x33FFFFFF);

        guiGraphics.centeredText(this.font, "Assign: " + targetSound, this.width / 2, startY + 15, 0xFFFFFFFF);

        int yOffset = startY + 40;
        for (ActionSoundManager.ActionType action : ActionSoundManager.ActionType.values()) {
            boolean isAssigned = targetSound.equals(ActionSoundManager.getSoundForAction(action));
            
            guiGraphics.text(this.font, action.displayName, startX + 20, yOffset + 5, 0xFFAAAAAA, false);

            String buttonText = isAssigned ? "Unassign" : "Assign";
            int btnColor = isAssigned ? 0xFF1DB954 : 0xFF333333;
            int btnWidth = 60;
            int btnX = startX + CARD_WIDTH - 20 - btnWidth;
            
            boolean hovered = mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= yOffset && mouseY <= yOffset + 16;
            if (hovered && !isAssigned) {
                btnColor = 0xFF555555;
            } else if (hovered && isAssigned) {
                btnColor = 0xFF1ED760;
            }

            guiGraphics.fill(btnX, yOffset, btnX + btnWidth, yOffset + 16, btnColor);
            guiGraphics.centeredText(this.font, buttonText, btnX + btnWidth / 2, yOffset + 4, 0xFFFFFFFF);

            yOffset += 25;
        }

        
        int backWidth = 80;
        int backX = this.width / 2 - backWidth / 2;
        int backY = startY + CARD_HEIGHT - 25;
        boolean backHovered = mouseX >= backX && mouseX <= backX + backWidth && mouseY >= backY && mouseY <= backY + 16;
        guiGraphics.fill(backX, backY, backX + backWidth, backY + 16, backHovered ? 0xFF555555 : 0xFF333333);
        guiGraphics.centeredText(this.font, "Back", backX + backWidth / 2, backY + 4, 0xFFFFFFFF);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int startX = (this.width - CARD_WIDTH) / 2;
        int startY = (this.height - CARD_HEIGHT) / 2;

        int yOffset = startY + 40;
        for (ActionSoundManager.ActionType action : ActionSoundManager.ActionType.values()) {
            int btnWidth = 60;
            int btnX = startX + CARD_WIDTH - 20 - btnWidth;
            
            if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= yOffset && mouseY <= yOffset + 16) {
                if (targetSound.equals(ActionSoundManager.getSoundForAction(action))) {
                    ActionSoundManager.assignSound(action, "");
                } else {
                    ActionSoundManager.assignSound(action, targetSound);
                }
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            yOffset += 25;
        }

        int backWidth = 80;
        int backX = this.width / 2 - backWidth / 2;
        int backY = startY + CARD_HEIGHT - 25;
        if (mouseX >= backX && mouseX <= backX + backWidth && mouseY >= backY && mouseY <= backY + 16) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.setScreen(parent);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
