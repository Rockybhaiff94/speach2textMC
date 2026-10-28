package com.voice2text.speech.mock;

import com.voice2text.speech.EngineState;
import com.voice2text.speech.RecognitionListener;
import com.voice2text.speech.RecognitionResult;
import com.voice2text.speech.SpeechRecognitionEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Mock SpeechRecognitionEngine for deterministic unit and integration tests.
 */
public class MockSpeechEngine implements SpeechRecognitionEngine {

    private EngineState state = EngineState.UNINITIALIZED;
    private RecognitionListener listener;
    private final ByteArrayOutputStream receivedAudio = new ByteArrayOutputStream();
    private boolean simulateErrorOnStart = false;

    @Override
    public void initialize(String modelPath) throws IOException {
        if ("invalid".equals(modelPath)) {
            setState(EngineState.ERROR);
            throw new IOException("Simulated model initialization failure");
        }
        setState(EngineState.READY);
    }

    @Override
    public void start() {
        if (simulateErrorOnStart) {
            setState(EngineState.ERROR);
            if (listener != null) {
                listener.onError("Simulated start error", new RuntimeException("Mock start error"));
            }
            return;
        }
        receivedAudio.reset();
        setState(EngineState.LISTENING);
    }

    @Override
    public void acceptAudio(byte[] audioData, int length) {
        if (state == EngineState.LISTENING && audioData != null && length > 0) {
            receivedAudio.write(audioData, 0, length);
        }
    }

    @Override
    public void stop() {
        if (state == EngineState.LISTENING) {
            setState(EngineState.FINALIZING);
            setState(EngineState.READY);
        }
    }

    @Override
    public void reset() {
        receivedAudio.reset();
        if (state == EngineState.LISTENING || state == EngineState.FINALIZING) {
            setState(EngineState.READY);
        }
    }

    @Override
    public boolean isRunning() {
        return state == EngineState.LISTENING;
    }

    @Override
    public EngineState getState() {
        return state;
    }

    @Override
    public void setListener(RecognitionListener listener) {
        this.listener = listener;
    }

    @Override
    public void close() {
        setState(EngineState.CLOSED);
        receivedAudio.reset();
    }

    public void simulatePartial(String text) {
        if (listener != null && state == EngineState.LISTENING) {
            listener.onPartialResult(new RecognitionResult(text, false));
        }
    }

    public void simulateFinal(String text) {
        if (listener != null) {
            listener.onFinalResult(new RecognitionResult(text, true));
        }
    }

    public void simulateError(String message) {
        setState(EngineState.ERROR);
        if (listener != null) {
            listener.onError(message, new RuntimeException(message));
        }
    }

    public byte[] getReceivedAudio() {
        return receivedAudio.toByteArray();
    }

    public void setSimulateErrorOnStart(boolean simulateErrorOnStart) {
        this.simulateErrorOnStart = simulateErrorOnStart;
    }

    private void setState(EngineState newState) {
        EngineState old = this.state;
        this.state = newState;
        if (listener != null && old != newState) {
            listener.onStateChanged(old, newState);
        }
    }
}
