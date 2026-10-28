package com.voice2text.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;

/**
 * AudioSource implementation that records from system microphone using standard Java Sound API.
 * Uses 16,000 Hz, 16-bit, mono, signed PCM, little-endian format.
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

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName != null ? deviceName : AudioDeviceManager.SYSTEM_DEFAULT;
    }

    @Override
    public synchronized void open() throws IOException {
        if (open) {
            return;
        }

        try {
            int bufferSize = (int) (SAMPLE_RATE * (SAMPLE_SIZE_IN_BITS / 8) * 0.25);
            line = AudioDeviceManager.openTargetDataLine(deviceName, format, bufferSize);
            line.start();
            open = true;
        } catch (LineUnavailableException e) {
            throw new IOException("Microphone line unavailable or currently in use by another application: " + e.getMessage(), e);
        } catch (SecurityException e) {
            throw new IOException("Permission denied when accessing microphone: " + e.getMessage(), e);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (!open || line == null) {
            throw new IOException("Cannot read from closed MicrophoneAudioSource");
        }

        try {
            int bytesRead = line.read(buffer, offset, length);
            if (bytesRead < 0) {
                return -1;
            }
            return bytesRead;
        } catch (Exception e) {
            throw new IOException("Error reading microphone audio stream: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isOpen() {
        return open && line != null && line.isOpen();
    }

    @Override
    public synchronized void close() {
        open = false;
        if (line != null) {
            try {
                if (line.isOpen()) {
                    line.stop();
                    line.flush();
                    line.close();
                }
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
