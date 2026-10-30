package com.voice2text.speech.indic;

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
 * SpeechRecognitionEngine backend for AI4Bharat IndicConformer models.
 * Delegates to local Vosk/Kaldi native engine with real-time Roman Hindi (Hinglish) transliteration.
 */
public class IndicConformerSpeechEngine implements SpeechRecognitionEngine {

    private final VoskSpeechEngine delegate = new VoskSpeechEngine();
    private boolean romanHindiOutput = true;
    private RecognitionListener externalListener;
    private String modelPath;
    private File modelDir;
    private final Object lock = new Object();

    public IndicConformerSpeechEngine() {
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
                throw new IOException("IndicConformer model path cannot be empty.");
            }
            File dir = ModelManager.resolveModelDirectory(new File(modelPath));
            if (!dir.exists()) {
                throw new IOException("Model directory does not exist: " + modelPath);
            }
            this.modelPath = dir.getAbsolutePath();
            this.modelDir = dir;

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

            delegate.initialize(modelPath);
        }
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void acceptAudio(byte[] audioData, int length) {
        delegate.acceptAudio(audioData, length);
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public EngineState getState() {
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
        delegate.close();
    }

    public File getModelDir() {
        return modelDir;
    }

    public String getModelPath() {
        return modelPath;
    }
}
