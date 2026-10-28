package com.voice2text.transcription;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles splitting messages that exceed Minecraft's maximum chat message length (256 characters)
 * at word boundaries, ensuring text is never silently truncated.
 */
public class ChatMessageSplitter {

    public static final int DEFAULT_MAX_LENGTH = 256;

    /**
     * Splits a long text into chunks of at most maxLength characters, breaking at word boundaries.
     *
     * @param text the message text to split
     * @param maxLength maximum characters per message chunk
     * @return list of message segments
     */
    public static List<String> splitMessage(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String remaining = text.trim();
        while (remaining.length() > maxLength) {
            // Find last space before or at maxLength
            int splitIndex = remaining.lastIndexOf(' ', maxLength);
            if (splitIndex <= 0) {
                // No space found within the limit, break hard at maxLength
                splitIndex = maxLength;
            }

            String chunk = remaining.substring(0, splitIndex).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            remaining = remaining.substring(splitIndex).trim();
        }

        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    public static List<String> splitMessage(String text) {
        return splitMessage(text, DEFAULT_MAX_LENGTH);
    }
}
