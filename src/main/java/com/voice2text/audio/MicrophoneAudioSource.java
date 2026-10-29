package com.voice2text.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;

/**
 * AudioSource implementation that records from system microphone using standard Java Sound API.
 * Uses 16,000 Hz, 16-bit, mono, signed PCM, little-endian format.
 *
 * Performance enhancements:
 * - Line reuse across recording sessions: pauses/stops line between utterances without
 *   destroying and re-enumerating device handles, eliminating Java Sound startup stutter.
 * - Non-blocking reads: line.read() occurs outside synchronized lock to prevent monitor contention.
 */
public class MicrophoneAudioSource implements AudioSource {

    public static final float SAMPLE_RATE = 16000.0f;
    public static final int SAMPLE_SIZE_IN_BITS = 16;
    public static final int CHANNELS = 1;
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = false;

    private TargetDataLine line;
    private final AudioFormat format;
    private volatile boolean open = false;
    private String deviceName = AudioDeviceManager.SYSTEM_DEFAULT;

    public MicrophoneAudioSource() {
        this(AudioDeviceManager.SYSTEM_DEFAULT);
    }

    public MicrophoneAudioSource(String deviceName) {
        this.format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
        this.deviceName = deviceName != null ? deviceName : AudioDeviceManager.SYSTEM_DEFAULT;
    }

    public synchronized String getDeviceName() {
        return deviceName;
    }

    public synchronized void setDeviceName(String deviceName) {
        String newName = deviceName != null ? deviceName : AudioDeviceManager.SYSTEM_DEFAULT;
        if (!newName.equalsIgnoreCase(this.deviceName)) {
            release();
            this.deviceName = newName;
        }
    }

    @Override
    public synchronized void open() throws IOException {
        if (open) {
            return;
        }

        try {
            int bufferSize = (int) (SAMPLE_RATE * (SAMPLE_SIZE_IN_BITS / 8) * 0.25);
            if (line == null || !line.isOpen()) {
                line = AudioDeviceManager.openTargetDataLine(deviceName, format, bufferSize);
            }
            line.flush();
            line.start();
            open = true;
        } catch (LineUnavailableException e) {
            release();
            throw new IOException("Microphone line unavailable or currently in use: " + e.getMessage(), e);
        } catch (SecurityException e) {
            release();
            throw new IOException("Permission denied when accessing microphone: " + e.getMessage(), e);
        } catch (Throwable t) {
            release();
            throw new IOException("Failed to open microphone: " + t.getMessage(), t);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        TargetDataLine activeLine;
        synchronized (this) {
            if (!open || line == null) {
                return -1;
            }
            activeLine = line;
        }

        try {
            // Read outside lock so stopping the audio source never blocks
            int bytesRead = activeLine.read(buffer, offset, length);
            if (bytesRead < 0) {
                return -1;
            }
            return bytesRead;
        } catch (Exception e) {
            if (!open) {
                return -1; // Normal termination when closed
            }
            throw new IOException("Error reading microphone audio stream: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isOpen() {
        return open && line != null && line.isOpen();
    }

    /**
     * Pauses the audio capture line and flushes buffered audio without destroying the OS handle.
     */
    @Override
    public synchronized void close() {
        open = false;
        if (line != null) {
            try {
                if (line.isOpen()) {
                    line.stop();
                    line.flush();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Completely releases and closes the underlying system audio line.
     */
    public synchronized void release() {
        close();
        if (line != null) {
            try {
                line.close();
            } catch (Exception ignored) {
            } finally {
                line = null;
            }
        }
    }

    public AudioFormat getFormat() {
        return format;
    }
}
