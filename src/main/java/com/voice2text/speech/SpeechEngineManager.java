package com.voice2text.speech;

import com.voice2text.model.ModelFamily;
import com.voice2text.model.SpeechModelInfo;
import com.voice2text.speech.indic.IndicConformerSpeechEngine;
import com.voice2text.speech.shruti.ShrutiSpeechEngine;
import com.voice2text.speech.vosk.VoskSpeechEngine;
import com.voice2text.speech.whisper.WhisperSpeechEngine;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized manager and factory for speech recognition backends.
 * Dynamically provisions the correct modular engine according to the active model's family,
 * performs resource & compatibility verification, and prevents memory leaks during model switching.
 */
public class SpeechEngineManager {

    private final Map<ModelFamily, SpeechRecognitionEngine> enginePool = new ConcurrentHashMap<>();

    public SpeechEngineManager() {
    }

    /**
     * Resolves or instantiates the compatible SpeechRecognitionEngine for the given model metadata.
     */
    public SpeechRecognitionEngine getOrCreateEngine(SpeechModelInfo modelInfo) {
        ModelFamily family = modelInfo != null ? modelInfo.getFamily() : ModelFamily.VOSK;
        return enginePool.computeIfAbsent(family, this::instantiateEngine);
    }

    /**
     * Factory creating a clean engine instance for a given model family.
     */
    public SpeechRecognitionEngine instantiateEngine(ModelFamily family) {
        if (family == null) return new VoskSpeechEngine();
        switch (family) {
            case WHISPER:
            case HINGLISH_SPECIALIZED:
                return new WhisperSpeechEngine();
            case INDIC_CONFORMER:
                return new IndicConformerSpeechEngine();
            case SHRUTI:
                return new ShrutiSpeechEngine();
            case VOSK:
            case CUSTOM:
            default:
                return new VoskSpeechEngine();
        }
    }

    /**
     * Verifies system resources and runtime files before activation.
     */
    public boolean verifyActivationCompatibility(SpeechModelInfo modelInfo, File modelDir) {
        if (modelInfo == null || modelDir == null || !modelDir.exists()) {
            return false;
        }

        // Verify available memory
        long freeMem = Runtime.getRuntime().freeMemory() + (Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory());
        long requiredEst = modelInfo.getDownloadSizeBytes() * 2;
        if (requiredEst > 0 && freeMem < (requiredEst / 4)) {
            // Memory is critically tight, but still attempt with warning
            System.err.println("[Voice2Text] Warning: Low available memory for model " + modelInfo.getDisplayName());
        }

        return true;
    }

    /**
     * Closes and cleans up all allocated engine instances.
     */
    public void closeAll() {
        for (SpeechRecognitionEngine engine : enginePool.values()) {
            try {
                engine.close();
            } catch (Throwable ignored) {
            }
        }
        enginePool.clear();
    }
}

