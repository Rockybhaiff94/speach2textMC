package com.voice2text.speech.vosk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voice2text.speech.EngineState;
import com.voice2text.speech.RecognitionListener;
import com.voice2text.speech.RecognitionResult;
import com.voice2text.speech.SpeechRecognitionEngine;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.IOException;

/**
 * SpeechRecognitionEngine implementation backed by Vosk (Kaldi-based offline streaming ASR).
 */
public class VoskSpeechEngine implements SpeechRecognitionEngine {

    public static final float DEFAULT_SAMPLE_RATE = 16000.0f;

    private Model model;
    private Recognizer recognizer;
    private EngineState state = EngineState.UNINITIALIZED;
    private RecognitionListener listener;
    private String lastPartialText = "";
    private final Object lock = new Object();

    static {
        try {
            System.setProperty("jna.encoding", "UTF-8");
            LibVosk.setLogLevel(LogLevel.WARNINGS);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void initialize(String modelPath) throws IOException {
        synchronized (lock) {
            if (modelPath == null || modelPath.trim().isEmpty()) {
                throw new IOException("Speech model path cannot be empty.");
            }

            File modelDir = new File(modelPath);
            if (!modelDir.exists() || !modelDir.isDirectory()) {
                throw new IOException("Speech model directory does not exist: " + modelPath);
            }

            try {
                close();
                model = new Model(modelDir.getAbsolutePath());
                recognizer = new Recognizer(model, DEFAULT_SAMPLE_RATE);
                setState(EngineState.READY);
            } catch (Throwable e) {
                setState(EngineState.ERROR);
                throw new IOException("Failed to initialize Vosk model from " + modelPath + ": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (model == null) {
                notifyError("Cannot start recognition: Vosk model not initialized", null);
                return;
            }

            if (recognizer == null) {
                try {
                    recognizer = new Recognizer(model, DEFAULT_SAMPLE_RATE);
                } catch (Exception e) {
                    notifyError("Failed to create Vosk recognizer: " + e.getMessage(), e);
                    return;
                }
            }

            recognizer.reset();
            lastPartialText = "";
            setState(EngineState.LISTENING);
        }
    }

    @Override
    public void acceptAudio(byte[] audioData, int length) {
        if (audioData == null || length <= 0) {
            return;
        }

        RecognitionListener currentListener;
        String partialToSend = null;
        String finalToSend = null;

        synchronized (lock) {
            if (state != EngineState.LISTENING || recognizer == null) {
                return;
            }

            currentListener = this.listener;

            try {
                boolean isFinal = recognizer.acceptWaveForm(audioData, length);
                if (isFinal) {
                    String json = recognizer.getResult();
                    String text = parseTextFromJson(json, "text");
                    if (!text.isEmpty()) {
                        finalToSend = text;
                        lastPartialText = "";
                    }
                } else {
                    String json = recognizer.getPartialResult();
                    String partial = parseTextFromJson(json, "partial");
                    if (!partial.isEmpty() && !partial.equals(lastPartialText)) {
                        lastPartialText = partial;
                        partialToSend = partial;
                    }
                }
            } catch (Throwable e) {
                notifyError("Error during Vosk speech inference: " + e.getMessage(), e);
                return;
            }
        }

        // Dispatch outside lock to prevent deadlocks
        if (currentListener != null) {
            if (finalToSend != null) {
                currentListener.onFinalResult(new RecognitionResult(finalToSend, true));
            } else if (partialToSend != null) {
                currentListener.onPartialResult(new RecognitionResult(partialToSend, false));
            }
        }
    }

    @Override
    public void stop() {
        RecognitionListener currentListener;
        String finalToSend = null;

        synchronized (lock) {
            if (state != EngineState.LISTENING) {
                return;
            }

            setState(EngineState.FINALIZING);
            currentListener = this.listener;

            if (recognizer != null) {
                try {
                    String json = recognizer.getFinalResult();
                    String text = parseTextFromJson(json, "text");
                    if (!text.isEmpty()) {
                        finalToSend = text;
                    }
                } catch (Throwable e) {
                    notifyError("Error flushing final Vosk result: " + e.getMessage(), e);
                }
            }

            lastPartialText = "";
            setState(EngineState.READY);
        }

        if (currentListener != null && finalToSend != null) {
            currentListener.onFinalResult(new RecognitionResult(finalToSend, true));
        }
    }

    @Override
    public void reset() {
        synchronized (lock) {
            if (recognizer != null) {
                try {
                    recognizer.reset();
                } catch (Throwable ignored) {
                }
            }
            lastPartialText = "";
            if (state == EngineState.LISTENING || state == EngineState.FINALIZING) {
                setState(EngineState.READY);
            }
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (lock) {
            return state == EngineState.LISTENING;
        }
    }

    @Override
    public EngineState getState() {
        synchronized (lock) {
            return state;
        }
    }

    @Override
    public void setListener(RecognitionListener listener) {
        synchronized (lock) {
            this.listener = listener;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            setState(EngineState.CLOSED);
            if (recognizer != null) {
                try {
                    recognizer.close();
                } catch (Throwable ignored) {
                } finally {
                    recognizer = null;
                }
            }
            if (model != null) {
                try {
                    model.close();
                } catch (Throwable ignored) {
                } finally {
                    model = null;
                }
            }
        }
    }

    private void setState(EngineState newState) {
        EngineState oldState = this.state;
        this.state = newState;
        RecognitionListener l = this.listener;
        if (l != null && oldState != newState) {
            try {
                l.onStateChanged(oldState, newState);
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyError(String message, Throwable throwable) {
        setState(EngineState.ERROR);
        RecognitionListener l = this.listener;
        if (l != null) {
            try {
                l.onError(message, throwable);
            } catch (Exception ignored) {
            }
        }
    }

    private static String parseTextFromJson(String json, String field) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        // Fast path for Vosk standard JSON: "field" : "value"
        String marker = "\"" + field + "\" : \"";
        int start = json.indexOf(marker);
        if (start != -1) {
            start += marker.length();
            int end = json.indexOf("\"", start);
            if (end != -1) {
                return json.substring(start, end).trim();
            }
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has(field)) {
                return obj.get(field).getAsString().trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
