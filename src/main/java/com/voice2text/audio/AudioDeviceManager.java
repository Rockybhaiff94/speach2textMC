package com.voice2text.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Manages discovery, selection, and line creation for physical and virtual audio input devices (microphones).
 */
public class AudioDeviceManager {

    public static final String SYSTEM_DEFAULT = "System Default";

    private static Consumer<String> fallbackNotificationListener;
    private static volatile List<String> cachedDevices = null;
    private static volatile long lastCacheTime = 0L;
    private static final long CACHE_TTL_MS = 5000L;

    public static synchronized void invalidateCache() {
        cachedDevices = null;
    }

    public static void setFallbackNotificationListener(Consumer<String> listener) {
        fallbackNotificationListener = listener;
    }

    /**
     * Enumerates real physical and virtual recording devices supported by the operating system.
     * Results are cached for 5 seconds to prevent repeated expensive mixer discovery calls.
     *
     * @param format required AudioFormat (16kHz, 16-bit, mono)
     * @return ordered list of device names with System Default first
     */
    public static List<String> getAvailableInputDevices(AudioFormat format) {
        long now = System.currentTimeMillis();
        List<String> cached = cachedDevices;
        if (cached != null && (now - lastCacheTime < CACHE_TTL_MS)) {
            return cached;
        }

        Set<String> devices = new LinkedHashSet<>();
        devices.add(SYSTEM_DEFAULT);

        try {
            DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

            if (mixerInfos != null) {
                for (Mixer.Info info : mixerInfos) {
                    if (info == null || info.getName() == null) continue;
                    try {
                        Mixer mixer = AudioSystem.getMixer(info);
                        if (mixer.isLineSupported(targetInfo)) {
                            String name = info.getName().trim();
                            if (!name.isEmpty()) {
                                devices.add(name);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        List<String> result = Collections.unmodifiableList(new ArrayList<>(devices));
        cachedDevices = result;
        lastCacheTime = now;
        return result;
    }

    /**
     * Backward-compatible default overload.
     */
    public static List<String> getAvailableInputDevices() {
        AudioFormat defaultFormat = new AudioFormat(16000.0f, 16, 1, true, false);
        return getAvailableInputDevices(defaultFormat);
    }

    /**
     * Checks whether a specific microphone device is currently detected by the system.
     */
    public static boolean isDeviceAvailable(String deviceName, AudioFormat format) {
        if (deviceName == null || deviceName.trim().isEmpty() || SYSTEM_DEFAULT.equalsIgnoreCase(deviceName.trim())) {
            return true;
        }
        List<String> list = getAvailableInputDevices(format);
        return list.contains(deviceName.trim());
    }

    /**
     * Opens a TargetDataLine for recording from the specified device name, with automatic
     * fallback to System Default if the selected device is disconnected or unavailable.
     */
    public static TargetDataLine openTargetDataLine(String requestedDeviceName, AudioFormat format, int bufferSize)
            throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (requestedDeviceName != null && !requestedDeviceName.trim().isEmpty() && !SYSTEM_DEFAULT.equalsIgnoreCase(requestedDeviceName.trim())) {
            String targetName = requestedDeviceName.trim();
            Mixer.Info matchingMixer = findMixerInfoByName(targetName, format);

            if (matchingMixer != null) {
                try {
                    Mixer mixer = AudioSystem.getMixer(matchingMixer);
                    TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                    line.open(format, bufferSize);
                    return line;
                } catch (Throwable t) {
                    notifyFallback("Selected microphone '" + targetName + "' unavailable (" + t.getMessage() + "). Falling back to System Default.");
                }
            } else {
                notifyFallback("Selected microphone '" + targetName + "' disconnected. Falling back to System Default.");
            }
        }

        // Fallback or System Default
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Default microphone audio line not supported for format: " + format);
        }

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format, bufferSize);
        return line;
    }

    private static Mixer.Info findMixerInfoByName(String deviceName, AudioFormat format) {
        try {
            DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, format);
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            if (mixerInfos != null) {
                for (Mixer.Info m : mixerInfos) {
                    if (m != null && deviceName.equalsIgnoreCase(m.getName().trim())) {
                        try {
                            Mixer mixer = AudioSystem.getMixer(m);
                            if (mixer.isLineSupported(targetInfo)) {
                                return m;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void notifyFallback(String message) {
        if (fallbackNotificationListener != null) {
            try {
                fallbackNotificationListener.accept(message);
            } catch (Throwable ignored) {
            }
        }
    }
}
