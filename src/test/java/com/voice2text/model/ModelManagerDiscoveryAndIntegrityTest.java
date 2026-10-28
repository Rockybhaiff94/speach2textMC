package com.voice2text.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ModelManagerDiscoveryAndIntegrityTest {

    @Test
    public void testAutoDiscoveryOfLocallyInstalledModels(@TempDir Path tempDir) throws IOException {
        File modelsDir = tempDir.resolve("models").toFile();
        File downloadsDir = tempDir.resolve("downloads").toFile();
        File tempBaseDir = tempDir.resolve("temp").toFile();

        // Create a custom valid model folder before ModelManager initializes
        File customModelDir = new File(modelsDir, "custom-local-model/conf");
        customModelDir.mkdirs();
        new File(customModelDir, "model.conf").createNewFile();

        ModelManager manager = new ModelManager(modelsDir, downloadsDir, tempBaseDir);

        assertTrue(manager.isModelInstalled("custom-local-model"));
        assertNotNull(manager.getModelInfo("custom-local-model"));
        assertEquals("custom-local-model (Local)", manager.getModelInfo("custom-local-model").getDisplayName());
        assertEquals(ModelInstallationState.INSTALLED, manager.getModelInstallationState("custom-local-model"));
    }

    @Test
    public void testDualChecksumIntegrityVerification() {
        SpeechModelInfo info = new SpeechModelInfo(
                "test-model",
                "en-US",
                "Test Model",
                "https://example.com/model.zip",
                "https://mirror.com/model.zip",
                1000L,
                2000L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "d41d8cd98f00b204e9800998ecf8427e",
                "Apache-2.0"
        );

        // Matching both MD5 and SHA-256
        assertTrue(info.verifyIntegrity(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "d41d8cd98f00b204e9800998ecf8427e",
                false
        ));

        // Mismatched MD5
        assertFalse(info.verifyIntegrity(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "wrong_md5_hash_value",
                false
        ));

        // Mismatched SHA-256
        assertFalse(info.verifyIntegrity(
                "wrong_sha256_hash_value",
                "d41d8cd98f00b204e9800998ecf8427e",
                false
        ));
    }

    @Test
    public void testManualDownloadInstructionsAndLinks(@TempDir Path tempDir) {
        File modelsDir = tempDir.resolve("models").toFile();
        ModelManager manager = new ModelManager(modelsDir);

        SpeechModelInfo info = manager.getModelInfo(ModelManager.DEFAULT_MODEL_ID);
        assertNotNull(info);

        String manualUrl = ModelManager.getManualDownloadUrl(info);
        assertEquals("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", manualUrl);

        String instructions = manager.getManualInstallInstructions(info);
        assertTrue(instructions.contains("vosk-model-small-en-us-0.15.zip"));
        assertTrue(instructions.contains("conf/"));
        assertTrue(instructions.contains(manager.getModelDir(ModelManager.DEFAULT_MODEL_ID).getAbsolutePath()));
    }
}
