package com.voice2text.speech;

/**
 * Encapsulates a speech recognition result (either partial or finalized segment).
 */
public class RecognitionResult {

    private final String text;
    private final boolean isFinal;
    private final long timestamp;
    private final float confidence;

    public RecognitionResult(String text, boolean isFinal, long timestamp, float confidence) {
        this.text = text != null ? text.trim() : "";
        this.isFinal = isFinal;
        this.timestamp = timestamp;
        this.confidence = confidence;
    }

    public RecognitionResult(String text, boolean isFinal) {
        this(text, isFinal, System.currentTimeMillis(), 1.0f);
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
