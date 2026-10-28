package com.voice2text.config;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    @Test
    public void testConfigDefaultsAndValidation() {
        VoiceToTextConfig config = new VoiceToTextConfig();
        config.validate();

        assertFalse(config.isAutomaticSend());
        assertFalse(config.isStopAndSend());
        assertEquals("DUAL", config.getRecordingMode());
        assertEquals("LAYOUT_2", config.getInterfaceLayout());
        assertEquals("System Default", config.getSelectedMicrophone());
        assertEquals("BOTTOM_RIGHT", config.getHudPosition());
        assertEquals("vosk-model-small-en-us-0.15", config.getActiveModelId());
        assertEquals("T", config.getActivationKey());
        assertEquals(InputConstants.KEY_T, config.getActivationKeyCode());
        assertEquals("P", config.getStopKey());
        assertEquals(InputConstants.KEY_P, config.getStopKeyCode());
        assertEquals("V", config.getDedicatedToggleKey());
        assertEquals(InputConstants.KEY_V, config.getDedicatedToggleKeyCode());
        assertEquals("O", config.getConfigKey());
        assertEquals(InputConstants.KEY_O, config.getConfigKeyCode());

        // Test out of bounds validation
        config.setMicSensitivity(-0.5f);
        config.setHudScale(10.0f);
        config.setInterfaceLayout("INVALID_LAYOUT");
        config.setActivationKey("   ");
        config.setActivationKeyCode(-1);
        config.setStopKey(null);
        config.setStopKeyCode(0);
        config.validate();

        assertEquals("LAYOUT_2", config.getInterfaceLayout(), "Invalid layout must fallback to default LAYOUT_2");
        config.setInterfaceLayout("LAYOUT_1");
        config.validate();
        assertEquals("LAYOUT_2", config.getInterfaceLayout(), "Legacy LAYOUT_1 must migrate to LAYOUT_2");
        config.setInterfaceLayout("LAYOUT_2");
        config.validate();
        assertEquals("LAYOUT_2", config.getInterfaceLayout(), "LAYOUT_2 must be preserved after validation");

        assertTrue(config.getMicSensitivity() >= 0.001f, "Sensitivity must clamp to min 0.001");
        assertEquals(3.0f, config.getHudScale(), "Scale must clamp to max 3.0");
        assertEquals("T", config.getActivationKey(), "Empty activation key must restore default");
        assertEquals(InputConstants.KEY_T, config.getActivationKeyCode(), "Invalid activation key code must restore default");
        assertEquals("P", config.getStopKey(), "Null stop key must restore default");
        assertEquals(InputConstants.KEY_P, config.getStopKeyCode(), "Zero stop key code must restore default");
    }

    @Test
    public void testSaveAndLoad(@TempDir Path tempDir) {
        File configFile = tempDir.resolve("voice2text.json").toFile();
        ConfigManager manager = new ConfigManager(configFile);

        VoiceToTextConfig config = manager.getConfig();
        config.setAutomaticSend(true);
        config.setActiveModelId("vosk-model-small-hi-0.22");
        config.setActivationKey("G");
        config.setActivationKeyCode(71);
        config.setStopKey("K");
        config.setStopKeyCode(75);
        config.setDedicatedToggleKey("B");
        config.setDedicatedToggleKeyCode(66);
        config.setConfigKey("M");
        config.setConfigKeyCode(77);
        config.setInterfaceLayout("LAYOUT_2");
        manager.save(config);

        // Create new manager instance reading the file
        ConfigManager reloadManager = new ConfigManager(configFile);
        VoiceToTextConfig loaded = reloadManager.getConfig();

        assertTrue(loaded.isAutomaticSend());
        assertTrue(loaded.isStopAndSend());
        assertEquals("LAYOUT_2", loaded.getInterfaceLayout());
        assertEquals("vosk-model-small-hi-0.22", loaded.getActiveModelId());
        assertEquals("G", loaded.getActivationKey());
        assertEquals(71, loaded.getActivationKeyCode());
        assertEquals("K", loaded.getStopKey());
        assertEquals(75, loaded.getStopKeyCode());
        assertEquals("B", loaded.getDedicatedToggleKey());
        assertEquals(66, loaded.getDedicatedToggleKeyCode());
        assertEquals("M", loaded.getConfigKey());
        assertEquals(77, loaded.getConfigKeyCode());
    }

    @Test
    public void testCorruptFileRecovery(@TempDir Path tempDir) throws Exception {
        File configFile = tempDir.resolve("corrupt.json").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{ INVALID JSON NOT VALID ::::: }");
        }

        ConfigManager manager = new ConfigManager(configFile);
        VoiceToTextConfig config = manager.getConfig();
        assertNotNull(config);
        assertFalse(config.isAutomaticSend(), "Should gracefully recover with defaults");
        assertEquals("DUAL", config.getRecordingMode());
    }

    @Test
    public void testLayoutMigrationFromLayout1ToLayout2(@TempDir Path tempDir) throws Exception {
        File configFile = tempDir.resolve("legacy_config.json").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("{\n" +
                    "  \"interfaceLayout\": \"LAYOUT_1\",\n" +
                    "  \"speechLanguage\": \"hi-en\",\n" +
                    "  \"activeModelId\": \"vosk-model-small-hi-0.22\",\n" +
                    "  \"automaticSend\": false,\n" +
                    "  \"activationKey\": \"T\"\n" +
                    "}");
        }

        ConfigManager manager = new ConfigManager(configFile);
        VoiceToTextConfig loaded = manager.getConfig();

        assertNotNull(loaded);
        assertEquals("LAYOUT_2", loaded.getInterfaceLayout(), "Legacy LAYOUT_1 must be automatically migrated to LAYOUT_2");
        assertEquals("hi-en", loaded.getSpeechLanguage(), "Other settings must be preserved during migration");
        assertEquals("vosk-model-small-hi-0.22", loaded.getActiveModelId());
        assertFalse(loaded.isAutomaticSend());

        // Verify the file on disk was actively migrated
        ConfigManager reloader = new ConfigManager(configFile);
        VoiceToTextConfig reloaded = reloader.getConfig();
        assertEquals("LAYOUT_2", reloaded.getInterfaceLayout(), "Migrated LAYOUT_2 must be saved to disk");
        assertEquals("hi-en", reloaded.getSpeechLanguage());
        assertEquals("vosk-model-small-hi-0.22", reloaded.getActiveModelId());
    }
}
