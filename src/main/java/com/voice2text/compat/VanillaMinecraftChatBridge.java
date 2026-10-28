package com.voice2text.compat;

import com.voice2text.fabric.mixin.ChatScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Production implementation of MinecraftChatBridge targeting Minecraft Java Edition 26.2 and 26.3.
 */
public class VanillaMinecraftChatBridge implements MinecraftChatBridge {

    private final Minecraft minecraft;

    public VanillaMinecraftChatBridge(Minecraft minecraft) {
        this.minecraft = minecraft != null ? minecraft : Minecraft.getInstance();
    }

    public VanillaMinecraftChatBridge() {
        this(Minecraft.getInstance());
    }

    @Override
    public void openChat(String initialText) {
        if (minecraft.gui == null) return;
        ChatScreen chatScreen = new ChatScreen(initialText != null ? initialText : "", false);
        minecraft.gui.setScreen(chatScreen);
    }

    @Override
    public void updateChatInput(String text) {
        if (minecraft.gui == null) return;
        Screen currentScreen = minecraft.gui.screen();
        if (currentScreen instanceof ChatScreenAccessor accessor) {
            EditBox editBox = accessor.voice2text$getInput();
            if (editBox != null) {
                String safeText = text != null ? text : "";
                editBox.setValue(safeText);
                editBox.setCursorPosition(safeText.length());
            }
        }
    }

    @Override
    public String getChatInput() {
        if (minecraft.gui == null) return "";
        Screen currentScreen = minecraft.gui.screen();
        if (currentScreen instanceof ChatScreenAccessor accessor) {
            EditBox editBox = accessor.voice2text$getInput();
            if (editBox != null) {
                return editBox.getValue();
            }
        }
        return "";
    }

    @Override
    public void sendChatMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        Screen currentScreen = minecraft.gui != null ? minecraft.gui.screen() : null;
        if (currentScreen instanceof ChatScreen chatScreen) {
            chatScreen.handleChatInput(message.trim(), true);
        } else if (minecraft.getConnection() != null) {
            String trimmed = message.trim();
            if (trimmed.startsWith("/")) {
                minecraft.getConnection().sendCommand(trimmed.substring(1));
            } else {
                minecraft.getConnection().sendChat(trimmed);
            }
        }
    }

    @Override
    public void closeChat() {
        if (minecraft.gui == null) return;
        Screen current = minecraft.gui.screen();
        if (current instanceof ChatScreen) {
            minecraft.gui.setScreen(null);
        }
    }

    @Override
    public boolean isChatOpen() {
        return minecraft.gui != null && minecraft.gui.screen() instanceof ChatScreen;
    }

    @Override
    public void displayClientMessage(String message, boolean isError) {
        if (minecraft.gui != null && minecraft.gui.chatListener() != null) {
            minecraft.gui.chatListener().handleSystemMessage(Component.literal(message), false);
        } else if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(message));
        }
    }
}
