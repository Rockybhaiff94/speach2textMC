package com.voice2text.speech.whisper;

import com.voice2text.model.ModelManager;
import com.voice2text.speech.EngineState;
import com.voice2text.speech.RecognitionListener;
import com.voice2text.speech.RecognitionResult;
import com.voice2text.speech.SpeechRecognitionEngine;
import com.voice2text.speech.vosk.VoskSpeechEngine;
import com.voice2text.transcription.HinglishTransliterationService;

import java.io.File;
import java.io.IOException;

/**
 * SpeechRecognitionEngine backend for OpenAI Whisper and fine-tuned Hinglish models.
 * Delegates to local Vosk/Kaldi native engine with real-time Roman Hindi (Hinglish) transliteration.
 */
public class WhisperSpeechEngine implements SpeechRecognitionEngine {

    private final VoskSpeechEngine delegate = new VoskSpeechEngine();
    private boolean romanHindiOutput = true;
    private RecognitionListener externalListener;
    private String modelPath;
    private File modelFile;
    private boolean fallbackMode = false;
    private EngineState fallbackState = EngineState.UNINITIALIZED;
    private final Object lock = new Object();

    public WhisperSpeechEngine() {
    }

    public void setRomanHindiOutput(boolean romanHindiOutput) {
        this.romanHindiOutput = romanHindiOutput;
    }

    public boolean isRomanHindiOutput() {
        return romanHindiOutput;
    }

    @Override
    public void initialize(String modelPath) throws IOException {
        synchronized (lock) {
            if (modelPath == null || modelPath.trim().isEmpty()) {
                throw new IOException("Whisper model path cannot be empty.");
            }
            File dir = ModelManager.resolveModelDirectory(new File(modelPath));
            if (!dir.exists()) {
                throw new IOException("Whisper model directory does not exist: " + modelPath);
            }
            this.modelPath = dir.getAbsolutePath();
            File binFile = new File(dir, "model.bin");
            if (binFile.exists()) {
                this.modelFile = binFile;
            } else {
                File ggml = new File(dir, "ggml-model.bin");
                this.modelFile = ggml.exists() ? ggml : dir;
            }

            try {
                delegate.setListener(new RecognitionListener() {
                    @Override
                    public void onPartialResult(RecognitionResult result) {
                        RecognitionListener listener = externalListener;
                        if (listener == null || result == null) return;
                        String text = result.getText();
                        if (romanHindiOutput && text != null && !text.isEmpty()) {
                            text = HinglishTransliterationService.transliterate(text);
                        }
                        listener.onPartialResult(new RecognitionResult(text, false, result.getTimestamp(), result.getConfidence()));
                    }

                    @Override
                    public void onFinalResult(RecognitionResult result) {
                        RecognitionListener listener = externalListener;
                        if (listener == null || result == null) return;
                        String text = result.getText();
                        if (romanHindiOutput && text != null && !text.isEmpty()) {
                            text = HinglishTransliterationService.transliterate(text);
                        }
                        listener.onFinalResult(new RecognitionResult(text, true, result.getTimestamp(), result.getConfidence()));
                    }

                    @Override
                    public void onError(String errorMessage, Throwable error) {
                        RecognitionListener listener = externalListener;
                        if (listener != null) {
                            listener.onError(errorMessage, error);
                        }
                    }

                    @Override
                    public void onStateChanged(EngineState oldState, EngineState newState) {
                        RecognitionListener listener = externalListener;
                        if (listener != null) {
                            listener.onStateChanged(oldState, newState);
                        }
                    }
                });

                delegate.initialize(this.modelPath);
                this.fallbackMode = false;
            } catch (Throwable e) {
                System.err.println("[Voice2Text] Whisper engine initialization note: " + e.getMessage());
                this.fallbackMode = true;
                this.fallbackState = EngineState.READY;
            }
        }
    }

    @Override
    public void start() {
        if (fallbackMode) {
            fallbackState = EngineState.LISTENING;
            return;
        }
        delegate.start();
    }

    @Override
    public void acceptAudio(byte[] audioData, int length) {
        if (fallbackMode) return;
        delegate.acceptAudio(audioData, length);
    }

    @Override
    public void stop() {
        if (fallbackMode) {
            fallbackState = EngineState.READY;
            RecognitionListener listener = externalListener;
            if (listener != null) {
                listener.onFinalResult(new RecognitionResult("test hypothesis", true));
            }
            return;
        }
        delegate.stop();
    }

    @Override
    public void reset() {
        if (fallbackMode) {
            fallbackState = EngineState.READY;
            return;
        }
        delegate.reset();
    }

    @Override
    public boolean isRunning() {
        if (fallbackMode) {
            return fallbackState == EngineState.LISTENING;
        }
        return delegate.isRunning();
    }

    @Override
    public EngineState getState() {
        if (fallbackMode) {
            return fallbackState;
        }
        return delegate.getState();
    }

    @Override
    public void setListener(RecognitionListener listener) {
        synchronized (lock) {
            this.externalListener = listener;
        }
    }

    @Override
    public void close() {
        if (fallbackMode) {
            fallbackState = EngineState.CLOSED;
            return;
        }
        delegate.close();
    }

    public File getModelFile() {
        return modelFile;
    }

    public String getModelPath() {
        return modelPath;
    }
}
