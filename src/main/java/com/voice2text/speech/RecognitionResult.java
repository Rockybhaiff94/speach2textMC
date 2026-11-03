package com.voice2text.speech;

/**
 * Encapsulates a speech recognition result (either partial or finalized segment).
 */
public class RecognitionResult {

    private final String text;
    private final boolean isFinal;
    private final long timestamp;
    private final float confidence;
    private final String wordDetails;

    public RecognitionResult(String text, boolean isFinal, long timestamp, float confidence, String wordDetails) {
        this.text = text != null ? text.trim() : "";
        this.isFinal = isFinal;
        this.timestamp = timestamp;
        this.confidence = confidence;
        this.wordDetails = wordDetails != null ? wordDetails : "";
    }

    public RecognitionResult(String text, boolean isFinal, long timestamp, float confidence) {
        this(text, isFinal, timestamp, confidence, "");
    }

    public RecognitionResult(String text, boolean isFinal, float confidence) {
        this(text, isFinal, System.currentTimeMillis(), confidence, "");
    }

    public RecognitionResult(String text, boolean isFinal) {
        this(text, isFinal, System.currentTimeMillis(), 1.0f, "");
    }

    public String getText() {
        return text;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getWordDetails() {
        return wordDetails;
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    @Override
    public String toString() {
        return "RecognitionResult{" +
                "text='" + text + '\'' +
                ", isFinal=" + isFinal +
                ", confidence=" + confidence +
                '}';
    }
}
