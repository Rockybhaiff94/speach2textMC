package com.voice2text.audio;

import com.voice2text.speech.SpeechRecognitionEngine;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Service managing background thread audio capture, streaming to SpeechRecognitionEngine,
 * and dispatching real-time audio levels for UI visualization.
 *
 * Performance enhancements:
 * - Decoupled capture and recognition threads with a bounded audio queue.
 * - Explicit backpressure handling: drops oldest non-essential frames if recognition lags,
 *   preventing unbounded memory growth or audio hardware buffer overflow.
 */
public class AudioCaptureService {

    private static final int BUFFER_SIZE_BYTES = 1024; // ~32 ms of 16kHz 16-bit mono for responsive audio levels
    public static final int MAX_QUEUE_CAPACITY = 32;   // ~1 second of buffered audio

    private final AudioSource audioSource;
    private final AudioLevelMeter levelMeter;
    private final SpeechRecognitionEngine speechEngine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final BlockingQueue<byte[]> audioQueue = new ArrayBlockingQueue<>(MAX_QUEUE_CAPACITY);

    private Thread captureThread;
    private Thread processingThread;

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

        audioQueue.clear();
        audioSource.open();
        running.set(true);

        captureThread = new Thread(this::captureLoop, "Voice2Text-AudioCaptureThread");
        captureThread.setDaemon(true);
        captureThread.start();

        processingThread = new Thread(this::processingLoop, "Voice2Text-AudioProcessingThread");
        processingThread.setDaemon(true);
        processingThread.start();
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

                    // Enqueue chunk for recognition with bounded backpressure handling
                    if (speechEngine != null && speechEngine.isRunning()) {
                        byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                        if (!audioQueue.offer(chunk)) {
                            // Backpressure: queue is full, drop oldest frame to maintain low latency
                            audioQueue.poll();
                            audioQueue.offer(chunk);
                        }
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
                    errorListener.accept("Unexpected audio capture error: " + e.getMessage());
                }
                break;
            }
        }

        cleanup();
    }

    private void processingLoop() {
        while (running.get() || !audioQueue.isEmpty()) {
            try {
                byte[] chunk = audioQueue.poll(50, TimeUnit.MILLISECONDS);
                if (chunk != null) {
                    if (speechEngine != null && speechEngine.isRunning()) {
                        speechEngine.acceptAudio(chunk, chunk.length);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get() && errorListener != null) {
                    errorListener.accept("Unexpected speech inference error: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);
        if (captureThread != null) {
            captureThread.interrupt();
        }
        if (processingThread != null) {
            processingThread.interrupt();
        }
        audioQueue.clear();
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

    public int getQueueSize() {
        return audioQueue.size();
    }
}
