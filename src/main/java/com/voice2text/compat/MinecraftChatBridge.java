package com.voice2text.compat;

/**
 * Interface abstracting Minecraft client chat interactions.
 * Decouples core logic from Minecraft GUI classes for testability and version compatibility.
 */
public interface MinecraftChatBridge {

    /**
     * Opens Minecraft's native chat screen with optional initial text.
     *
     * @param initialText text to populate, or empty string
     */
    void openChat(String initialText);

    /**
     * Updates the text displayed in the active chat screen's input field.
     *
     * @param text new text to set
     */
    void updateChatInput(String text);

    /**
     * Gets the current text in the chat input field.
     *
     * @return current input field content
     */
    String getChatInput();

    /**
     * Sends a completed chat message through Minecraft's native client sending mechanism.
     *
     * @param message the message to send
     */
    void sendChatMessage(String message);

    /**
     * Closes the chat screen if currently open.
     */
    void closeChat();

    /**
     * Checks if the native Minecraft chat screen is currently displayed.
     *
     * @return true if chat screen is open, false otherwise
     */
    boolean isChatOpen();

    /**
     * Displays an in-game local message/feedback to the player (not broadcasted to server).
     *
     * @param message text message
     * @param isError true if this is an error notification
     */
    void displayClientMessage(String message, boolean isError);
}
