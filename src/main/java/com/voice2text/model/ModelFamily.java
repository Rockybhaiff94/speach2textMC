package com.voice2text.model;

/**
 * Categorization of supported speech recognition model families and their respective runtimes.
 */
public enum ModelFamily {
    VOSK("Vosk Offline", "Vosk Kaldi JNI native offline engine. Extremely fast, lightweight, zero GPU requirement."),
    WHISPER("OpenAI Whisper", "OpenAI Whisper multilingual speech recognition architecture with deep contextual comprehension."),
    HINGLISH_SPECIALIZED("Hinglish Specialized", "Acoustic models specifically fine-tuned for Indian English & Hindi code-switched conversational speech."),
    INDIC_CONFORMER("AI4Bharat IndicConformer", "State-of-the-art Conformer ASR for Indian languages developed by AI4Bharat."),
    SHRUTI("Bhashini / IITM Shruti", "Indian regional speech recognition architecture optimized for native Indian accents."),
    CUSTOM("Custom / Local", "User-provided offline speech recognition model.");

    private final String displayTitle;
    private final String description;

    ModelFamily(String displayTitle, String description) {
        this.displayTitle = displayTitle;
        this.description = description;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public String getDescription() {
        return description;
    }
}

