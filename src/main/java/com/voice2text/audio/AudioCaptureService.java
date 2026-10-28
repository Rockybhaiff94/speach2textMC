package com.voice2text.audio;

import com.voice2text.speech.SpeechRecognitionEngine;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Service managing background thread audio capture, streaming to SpeechRecognitionEngine,
 * and dispatching real-time audio levels for UI visualization.
 */
public class AudioCaptureService {

    private static final int BUFFER_SIZE_BYTES = 1024; // ~32 ms of 16kHz 16-bit mono for responsive audio levels

    private final AudioSource audioSource;
    private final AudioLevelMeter levelMeter;
    private final SpeechRecognitionEngine speechEngine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread captureThread;
    private Consumer<Float> audioLevelListener;
    private Consumer<Boolean> voiceActivityListener;
    private Consumer<String> errorListener;

    public AudioCaptureService(AudioSource audioSource, SpeechRecognitionEngine speechEngine, float vadThreshold) {
        this.audioSource = audioSource;
        this.speechEngine = speechEngine;
        this.levelMeter = new AudioLevelMeter(vadThreshold);
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        audioSource.open();
        running.set(true);

        captureThread = new Thread(this::captureLoop, "Voice2Text-AudioCaptureThread");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void captureLoop() {
        byte[] buffer = new byte[BUFFER_SIZE_BYTES];

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                int bytesRead = audioSource.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    long now = System.currentTimeMillis();
                    // Calculate RMS and speech activity
                    float rms = levelMeter.calculateRms(buffer, bytesRead);
                    float smoothed = levelMeter.updateSmoothedLevel(rms);
                    boolean speechActive = levelMeter.updateSpeechActivity(rms, now);

                    if (audioLevelListener != null) {
                        try {
                            audioLevelListener.accept(smoothed);
                        } catch (Exception ignored) {
                        }
                    }

                    if (voiceActivityListener != null) {
                        try {
                            voiceActivityListener.accept(speechActive);
                        } catch (Exception ignored) {
                        }
                    }

                    // Feed audio to speech recognition engine
                    if (speechEngine != null && speechEngine.isRunning()) {
                        speechEngine.acceptAudio(buffer, bytesRead);
                    }
                } else if (bytesRead == -1) {
                    // End of stream
                    break;
                }
            } catch (IOException e) {
                if (running.get()) {
                    if (errorListener != null) {
                        errorListener.accept("Microphone capture error: " + e.getMessage());
                    }
                }
                break;
            } catch (Exception e) {
                if (running.get() && errorListener != null) {
                    errorListener.accept("Unexpected audio processing error: " + e.getMessage());
                }
                break;
            }
        }

        cleanup();
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);
        if (captureThread != null) {
            captureThread.interrupt();
        }
        cleanup();
    }

    private synchronized void cleanup() {
        try {
            audioSource.close();
        } catch (Exception ignored) {
        }
        levelMeter.reset();
        if (audioLevelListener != null) {
            audioLevelListener.accept(0.0f);
        }
        if (voiceActivityListener != null) {
            voiceActivityListener.accept(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setAudioLevelListener(Consumer<Float> audioLevelListener) {
        this.audioLevelListener = audioLevelListener;
    }

    public void setVoiceActivityListener(Consumer<Boolean> voiceActivityListener) {
        this.voiceActivityListener = voiceActivityListener;
    }

    public void setErrorListener(Consumer<String> errorListener) {
        this.errorListener = errorListener;
    }

    public AudioLevelMeter getLevelMeter() {
        return levelMeter;
    }

    public AudioSource getAudioSource() {
        return audioSource;
    }
}
