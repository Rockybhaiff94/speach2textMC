package com.voice2text.transcription;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text formatting utilities for capitalization, punctuation cleanup,
 * and voice-dictated punctuation commands.
 */
public class TextFormatter {

    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+([.,!?;:])");
    private static final Pattern MISSING_SPACE_AFTER_PUNCTUATION = Pattern.compile("([.,!?;:])([A-Za-z0-9])");

    /**
     * Replaces spoken punctuation commands with actual punctuation symbols.
     *
     * @param text raw recognized text
     * @return text with punctuation commands replaced
     */
    public static String replaceVoiceCommands(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String result = text;

        // Word-boundary case-insensitive replacements
        result = replaceWord(result, "comma", ",");
        result = replaceWord(result, "period", ".");
        result = replaceWord(result, "full stop", ".");
        result = replaceWord(result, "question mark", "?");
        result = replaceWord(result, "exclamation mark", "!");
        result = replaceWord(result, "exclamation point", "!");
        result = replaceWord(result, "colon", ":");
        result = replaceWord(result, "semicolon", ";");
        result = replaceWord(result, "new line", "\n");
        result = replaceWord(result, "newline", "\n");

        return cleanSpacing(result);
    }

    private static String replaceWord(String input, String target, String replacement) {
        String regex = "(?i)\\b" + Pattern.quote(target) + "\\b";
        return input.replaceAll(regex, Matcher.quoteReplacement(replacement));
    }

    /**
     * Cleans whitespace around punctuation.
     * E.g. "hello , world ." -> "hello, world."
     */
    public static String cleanSpacing(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 1. Remove space before punctuation
        String cleaned = SPACE_BEFORE_PUNCTUATION.matcher(text).replaceAll("$1");

        // 2. Ensure space after punctuation (unless followed by newline or space)
        cleaned = MISSING_SPACE_AFTER_PUNCTUATION.matcher(cleaned).replaceAll("$1 $2");

        // 3. Collapse multiple spaces
        cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ");

        return cleaned;
    }

    /**
     * Capitalizes the first letter of sentences.
     * Sentences begin at the start of text, or after '.', '?', '!', or '\n'.
     */
    public static String applySentenceCapitalization(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        char[] chars = text.toCharArray();
        boolean capitalizeNext = true;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    capitalizeNext = true;
                }
                continue;
            }

            if (c == '.' || c == '?' || c == '!') {
                capitalizeNext = true;
                continue;
            }

            if (capitalizeNext && Character.isLetter(c)) {
                chars[i] = Character.toUpperCase(c);
                capitalizeNext = false;
            } else if (Character.isLetter(c)) {
                capitalizeNext = false;
            }
        }

        return new String(chars);
    }

    /**
     * Formats recognized speech with optional capitalization and voice commands.
     */
    public static String formatUtterance(String text, boolean voiceCommands, boolean autoCapitalize) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        String formatted = text.trim();
        if (voiceCommands) {
            formatted = replaceVoiceCommands(formatted);
        }

        formatted = cleanSpacing(formatted);

        if (autoCapitalize) {
            formatted = applySentenceCapitalization(formatted);
        }

        return formatted.trim();
    }
}
