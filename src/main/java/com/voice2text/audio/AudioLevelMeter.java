package com.voice2text.audio;

/**
 * Calculates audio volume levels and voice activity detection (VAD) from 16-bit PCM mono audio buffers.
 */
public class AudioLevelMeter {

    public static final float DEFAULT_VAD_THRESHOLD = 0.014f;
    public static final float DEFAULT_EXIT_THRESHOLD = 0.007f;
    public static final long DEFAULT_RELEASE_TIMEOUT_MS = 280L;

    private final float vadThreshold;
    private final float exitThreshold;
    private final long releaseTimeoutMs;
    private float smoothedLevel = 0.0f;
    private long lastSpeechTime = 0L;
    private boolean speechActive = false;
    private static final float SMOOTHING_FACTOR = 0.35f;

    public AudioLevelMeter(float vadThreshold, float exitThreshold, long releaseTimeoutMs) {
        this.vadThreshold = Math.max(0.001f, Math.min(1.0f, vadThreshold));
        this.exitThreshold = Math.max(0.0005f, Math.min(this.vadThreshold, exitThreshold));
        this.releaseTimeoutMs = Math.max(50L, releaseTimeoutMs);
    }

    public AudioLevelMeter(float vadThreshold) {
        this(vadThreshold, vadThreshold * 0.5f, DEFAULT_RELEASE_TIMEOUT_MS);
    }

    public AudioLevelMeter() {
        this(DEFAULT_VAD_THRESHOLD, DEFAULT_EXIT_THRESHOLD, DEFAULT_RELEASE_TIMEOUT_MS);
    }

    /**
     * Calculates the Root Mean Square (RMS) amplitude normalized to [0.0, 1.0].
     * Input format: 16-bit signed little-endian mono PCM.
     *
     * @param buffer raw PCM byte array
     * @param length number of valid bytes in buffer
     * @return normalized RMS amplitude between 0.0 and 1.0
     */
    public float calculateRms(byte[] buffer, int length) {
        if (buffer == null || length <= 1) {
            return 0.0f;
        }

        long sumOfSquares = 0;
        int sampleCount = length / 2;

        for (int i = 0; i < length - 1; i += 2) {
            // Little-endian 16-bit signed integer conversion
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sumOfSquares += (long) sample * sample;
        }

        if (sampleCount == 0) {
            return 0.0f;
        }

        double meanSquare = (double) sumOfSquares / sampleCount;
        double rms = Math.sqrt(meanSquare);

        // Normalize 16-bit signed maximum (32768)
        float normalized = (float) (rms / 32768.0);
        return Math.min(1.0f, Math.max(0.0f, normalized));
    }

    /**
     * Updates smoothed level and returns it for visual waveform rendering.
     *
     * @param rawLevel instantaneous raw RMS level [0.0, 1.0]
     * @return smoothed level [0.0, 1.0]
     */
    public float updateSmoothedLevel(float rawLevel) {
        this.smoothedLevel = (SMOOTHING_FACTOR * rawLevel) + ((1.0f - SMOOTHING_FACTOR) * this.smoothedLevel);
        return this.smoothedLevel;
    }

    /**
     * Checks if current audio level exceeds the VAD threshold.
     *
     * @param level normalized audio level [0.0, 1.0]
     * @return true if voice activity is detected, false otherwise
     */
    public boolean isVoiceActive(float level) {
        return level >= vadThreshold;
    }

    /**
     * Converts normalized RMS to decibels relative to full scale (dBFS).
     *
     * @param normalized normalized RMS [0.0, 1.0]
     * @return dBFS (typically -96 dB to 0 dB)
     */
    public static double toDecibels(float normalized) {
        if (normalized <= 0.00001f) {
            return -96.0;
        }
        return 20.0 * Math.log10(normalized);
    }

    /**
     * Updates speech detection status with hysteresis and release timeout.
     *
     * @param rms instantaneous RMS
     * @param now current timestamp in milliseconds
     * @return true if speech is currently active
     */
    public boolean updateSpeechActivity(float rms, long now) {
        if (rms >= vadThreshold) {
            speechActive = true;
            lastSpeechTime = now;
        } else if (speechActive) {
            if (rms < exitThreshold) {
                if (now - lastSpeechTime >= releaseTimeoutMs) {
                    speechActive = false;
                }
            } else {
                lastSpeechTime = now;
            }
        }
        return speechActive;
    }

    public boolean isSpeechActive() {
        return speechActive;
    }

    public float getSmoothedLevel() {
        return smoothedLevel;
    }

    public float getVadThreshold() {
        return vadThreshold;
    }

    public float getExitThreshold() {
        return exitThreshold;
    }

    public long getReleaseTimeoutMs() {
        return releaseTimeoutMs;
    }

    public void reset() {
        this.smoothedLevel = 0.0f;
        this.speechActive = false;
        this.lastSpeechTime = 0L;
    }
}
