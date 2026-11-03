package com.voice2text.ui;

import com.voice2text.model.ModelManager;
import com.voice2text.model.SpeechModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for model actions, discovery, installation detection, and display formatting
 * exposed in the simplified VoiceConfigScreen interface.
 */
public class VoiceConfigModelActionsTest {

    private ModelManager modelManager;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        modelManager = new ModelManager(tempDir.toFile());
    }

    @Test
    public void testModelDiscoveryAndRegistry() {
        assertNotNull(modelManager.getModelInfo("whisper-small"));
        assertNotNull(modelManager.getModelInfo("whisper-hindi2hinglish-apex"));
        assertNotNull(modelManager.getModelInfo("vosk-model-small-hi-0.22"));
        assertNotNull(modelManager.getModelInfo("vosk-model-small-en-us-0.15"));
    }

    @Test
    public void testModelInstallationDetection() throws IOException {
        String modelId = VoiceConfigScreen.MODEL_WHISPER_SMALL;
        assertFalse(modelManager.isModelInstalled(modelId));

        File modelDir = modelManager.getModelDir(modelId);
        modelDir.mkdirs();
        File weights = new File(modelDir, "model.bin");
        try (FileOutputStream fos = new FileOutputStream(weights)) {
            fos.write(new byte[512]);
        }

        assertTrue(modelManager.isModelInstalled(modelId));

        // Test deletion
        boolean deleted = modelManager.deleteModel(modelId);
        assertTrue(deleted);
        assertFalse(modelManager.isModelInstalled(modelId));
    }

    @Test
    public void testFormattedSizes() {
        SpeechModelInfo apex = modelManager.getModelInfo(VoiceConfigScreen.MODEL_APEX);
        assertNotNull(apex);
        assertTrue(apex.getFormattedDownloadSize().contains("MB") || apex.getFormattedDownloadSize().contains("GB"));

        SpeechModelInfo small = modelManager.getModelInfo(VoiceConfigScreen.MODEL_WHISPER_SMALL);
        assertNotNull(small);
        assertTrue(small.getFormattedDownloadSize().contains("MB") || small.getFormattedDownloadSize().contains("GB"));
    }
}

