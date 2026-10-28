package com.voice2text.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AudioDeviceManagerTest {

    private final AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);

    @Test
    public void testAvailableInputDevicesIncludesSystemDefault() {
        List<String> devices = AudioDeviceManager.getAvailableInputDevices(format);
        assertNotNull(devices);
        assertFalse(devices.isEmpty());
        assertEquals(AudioDeviceManager.SYSTEM_DEFAULT, devices.get(0));
    }

    @Test
    public void testIsDeviceAvailableWithSystemDefault() {
        assertTrue(AudioDeviceManager.isDeviceAvailable(AudioDeviceManager.SYSTEM_DEFAULT, format));
        assertTrue(AudioDeviceManager.isDeviceAvailable(null, format));
        assertTrue(AudioDeviceManager.isDeviceAvailable("", format));
    }

    @Test
    public void testFallbackNotificationWhenDeviceNotFound() {
        AtomicReference<String> notification = new AtomicReference<>();
        AudioDeviceManager.setFallbackNotificationListener(notification::set);

        try {
            // Request a non-existent device name
            AudioDeviceManager.openTargetDataLine("NonExistentMicrophoneDevice12345", format, 4096);
        } catch (Throwable ignored) {
            // In headless/CI environments, line open may throw LineUnavailableException
        }

        assertNotNull(notification.get(), "Fallback notification should be dispatched when device is missing");
        assertTrue(notification.get().contains("NonExistentMicrophoneDevice12345"));
        assertTrue(notification.get().contains("System Default"));
    }
}
