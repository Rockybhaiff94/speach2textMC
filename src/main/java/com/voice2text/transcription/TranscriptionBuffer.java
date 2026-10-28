package com.voice2text.transcription;

import java.util.Arrays;

/**
 * 4-Layer transcription buffer maintaining separate state for:
 * 1. Initial draft text before cursor (prefixDraft)
 * 2. Finalized recognized speech segments (committedSpeech)
 * 3. Temporary, changeable partial transcription (currentPartial)
 * 4. Initial draft text after cursor (suffixDraft)
 *
 * Implements intelligent token deduplication to prevent "hello hello hello" repeats,
 * ensures correct spacing and punctuation, and prevents destruction of user-entered text.
 */
public class TranscriptionBuffer {

    private String prefixDraft = "";
    private String suffixDraft = "";
    private final StringBuilder committedSpeech = new StringBuilder();
    private String currentPartial = "";

    private boolean voiceCommandsEnabled = true;
    private boolean autoCapitalization = true;

    public TranscriptionBuffer() {
    }

    public synchronized void startSession(String existingDraft, int cursorPosition) {
        if (existingDraft == null) {
            existingDraft = "";
        }

        int len = existingDraft.length();
        int pos = Math.max(0, Math.min(len, cursorPosition));

        this.prefixDraft = existingDraft.substring(0, pos);
        this.suffixDraft = existingDraft.substring(pos);
        this.committedSpeech.setLength(0);
        this.currentPartial = "";
    }

    /**
     * Updates the in-progress partial transcription hypothesis.
     * Deduplicates any leading words that overlap with already committed speech.
     *
     * @param rawPartial raw partial recognized text
     */
    public synchronized void updatePartial(String rawPartial) {
        if (rawPartial == null || rawPartial.trim().isEmpty()) {
            this.currentPartial = "";
            return;
        }

        String formatted = TextFormatter.formatUtterance(rawPartial, voiceCommandsEnabled, false);
        String deduplicated = removeOverlap(committedSpeech.toString(), formatted);

        if (deduplicated.trim().isEmpty()) {
            this.currentPartial = "";
        } else {
            // Apply capitalization if at the beginning of sentence
            boolean capitalize = shouldCapitalizeNext(committedSpeech.toString(), prefixDraft);
            this.currentPartial = capitalize ? TextFormatter.applySentenceCapitalization(deduplicated) : deduplicated;
        }
    }

    /**
     * Commits a finalized speech segment into the permanent speech buffer.
     *
     * @param rawFinal raw final recognized segment
     */
    public synchronized void commitFinal(String rawFinal) {
        if (rawFinal == null || rawFinal.trim().isEmpty()) {
            this.currentPartial = "";
            return;
        }

        String formatted = TextFormatter.formatUtterance(rawFinal, voiceCommandsEnabled, false);
        String deduplicated = removeOverlap(committedSpeech.toString(), formatted);

        if (!deduplicated.trim().isEmpty()) {
            boolean capitalize = shouldCapitalizeNext(committedSpeech.toString(), prefixDraft);
            String finalFormatted = capitalize ? TextFormatter.applySentenceCapitalization(deduplicated) : deduplicated;

            if (committedSpeech.length() > 0 && !endsWithWhitespace(committedSpeech)) {
                committedSpeech.append(' ');
            }
            committedSpeech.append(finalFormatted.trim());
        }

        this.currentPartial = "";
    }

    /**
     * Completes and flushes the transcription session, committing any remaining partial.
     *
     * @return the complete finalized message string
     */
    public synchronized String finalizeSession() {
        if (!currentPartial.trim().isEmpty()) {
            String pending = currentPartial.trim();
            String deduplicated = removeOverlap(committedSpeech.toString(), pending);
            if (!deduplicated.trim().isEmpty()) {
                if (committedSpeech.length() > 0 && !endsWithWhitespace(committedSpeech)) {
                    committedSpeech.append(' ');
                }
                committedSpeech.append(deduplicated);
            }
            currentPartial = "";
        }

        return getFullCommittedText();
    }

    /**
     * Reconstructs the full live display text for the Minecraft chat input field.
     */
    public synchronized String getDisplayText() {
        StringBuilder sb = new StringBuilder();

        // 1. Prefix Draft
        sb.append(prefixDraft);

        // Spacing between prefix draft and speech
        if (!prefixDraft.isEmpty() && !endsWithWhitespace(prefixDraft) && (committedSpeech.length() > 0 || !currentPartial.isEmpty())) {
            sb.append(' ');
        }

        // 2. Committed speech
        sb.append(committedSpeech);

        // 3. Current partial
        if (!currentPartial.isEmpty()) {
            if (committedSpeech.length() > 0 && !endsWithWhitespace(committedSpeech)) {
                sb.append(' ');
            }
            sb.append(currentPartial);
        }

        // 4. Suffix Draft
        if (!suffixDraft.isEmpty()) {
            if (sb.length() > 0 && !endsWithWhitespace(sb) && !startsWithWhitespace(suffixDraft)) {
                sb.append(' ');
            }
            sb.append(suffixDraft);
        }

        return sb.toString();
    }

    /**
     * Gets the full text with only finalized speech (excluding temporary partials).
     */
    public synchronized String getFullCommittedText() {
        StringBuilder sb = new StringBuilder();
        sb.append(prefixDraft);

        if (!prefixDraft.isEmpty() && !endsWithWhitespace(prefixDraft) && committedSpeech.length() > 0) {
            sb.append(' ');
        }

        sb.append(committedSpeech);

        if (!suffixDraft.isEmpty()) {
            if (sb.length() > 0 && !endsWithWhitespace(sb) && !startsWithWhitespace(suffixDraft)) {
                sb.append(' ');
            }
            sb.append(suffixDraft);
        }

        return sb.toString();
    }

    public synchronized boolean hasSpeech() {
        return committedSpeech.length() > 0 || !currentPartial.trim().isEmpty();
    }

    public synchronized boolean isSpeechEmpty() {
        return committedSpeech.length() == 0 && currentPartial.trim().isEmpty();
    }

    /**
     * Reconciles manual edits if the user types into the chat field during dictation.
     */
    public synchronized void reconcileManualEdit(String newContent) {
        if (newContent == null) {
            newContent = "";
        }

        String currentExpected = getDisplayText();
        if (newContent.equals(currentExpected)) {
            return;
        }

        // If the user modified the text manually, preserve their edit
        // by updating prefixDraft and resetting temporary fragments
        this.prefixDraft = newContent;
        this.suffixDraft = "";
        this.committedSpeech.setLength(0);
        this.currentPartial = "";
    }

    /**
     * Token-level deduplication: detects and strips overlapping prefix tokens in candidate
     * that match the tail tokens of previousText.
     * Prevents "hello hello hello" when ASR re-emits finalized words in partials.
     */
    public static String removeOverlap(String previousText, String candidate) {
        if (previousText == null || previousText.trim().isEmpty() || candidate == null || candidate.trim().isEmpty()) {
            return candidate != null ? candidate.trim() : "";
        }

        String[] prevWords = previousText.trim().toLowerCase().split("\\s+");
        String[] candWords = candidate.trim().split("\\s+");

        int maxOverlap = Math.min(prevWords.length, candWords.length);
        int matchedOverlap = 0;

        for (int overlapSize = maxOverlap; overlapSize >= 1; overlapSize--) {
            boolean match = true;
            for (int i = 0; i < overlapSize; i++) {
                String p = prevWords[prevWords.length - overlapSize + i];
                String c = candWords[i].toLowerCase();
                // Strip trailing punctuation for comparison
                p = p.replaceAll("[.,!?;:]+$", "");
                c = c.replaceAll("[.,!?;:]+$", "");
                if (!p.equals(c)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                matchedOverlap = overlapSize;
                break;
            }
        }

        if (matchedOverlap > 0) {
            return String.join(" ", Arrays.copyOfRange(candWords, matchedOverlap, candWords.length));
        }

        return candidate.trim();
    }

    private boolean shouldCapitalizeNext(String committed, String prefix) {
        if (!autoCapitalization) {
            return false;
        }

        String check = !committed.isEmpty() ? committed : prefix;
        if (check.isEmpty()) {
            return true;
        }

        String trimmed = check.trim();
        if (trimmed.isEmpty()) {
            return true;
        }

        char lastChar = trimmed.charAt(trimmed.length() - 1);
        return lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '\n';
    }

    private static boolean endsWithWhitespace(CharSequence cs) {
        if (cs == null || cs.length() == 0) return false;
        return Character.isWhitespace(cs.charAt(cs.length() - 1));
    }

    private static boolean startsWithWhitespace(CharSequence cs) {
        if (cs == null || cs.length() == 0) return false;
        return Character.isWhitespace(cs.charAt(0));
    }

    public synchronized void clear() {
        this.prefixDraft = "";
        this.suffixDraft = "";
        this.committedSpeech.setLength(0);
        this.currentPartial = "";
    }

    public void setVoiceCommandsEnabled(boolean voiceCommandsEnabled) {
        this.voiceCommandsEnabled = voiceCommandsEnabled;
    }

    public void setAutoCapitalization(boolean autoCapitalization) {
        this.autoCapitalization = autoCapitalization;
    }

    public synchronized String getCommittedSpeech() {
        return committedSpeech.toString();
    }

    public synchronized String getCurrentPartial() {
        return currentPartial;
    }
}
