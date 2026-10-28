package com.voice2text.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ModelManagerTest {

    @Test
    public void testRegistryContainsAllRequiredModels(@TempDir Path tempDir) {
        ModelManager manager = new ModelManager(tempDir.toFile());

        // English (US)
        SpeechModelInfo enUs = manager.getModelInfo("vosk-model-small-en-us-0.15");
        assertNotNull(enUs);
        assertEquals("en-US", enUs.getLanguageCode());
        assertEquals("English (US) Small", enUs.getDisplayName());
        assertTrue(enUs.hasAlternateUrl());
        assertEquals("30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498", enUs.getAlternateSha256());

        // English (India) - separately listed
        SpeechModelInfo enIn = manager.getModelInfo("vosk-model-small-en-in-0.4");
        assertNotNull(enIn);
        assertEquals("en-IN", enIn.getLanguageCode());
        assertEquals("English (India) Small", enIn.getDisplayName());
        assertNotEquals(enUs.getId(), enIn.getId());

        // German
        SpeechModelInfo de = manager.getModelInfo("vosk-model-small-de-0.15");
        assertNotNull(de);
        assertEquals("de-DE", de.getLanguageCode());

        // Spanish
        SpeechModelInfo es = manager.getModelInfo("vosk-model-small-es-0.42");
        assertNotNull(es);
        assertEquals("es-ES", es.getLanguageCode());

        // French
        SpeechModelInfo fr = manager.getModelInfo("vosk-model-small-fr-0.22");
        assertNotNull(fr);
        assertEquals("fr-FR", fr.getLanguageCode());

        // Italian
        SpeechModelInfo it = manager.getModelInfo("vosk-model-small-it-0.22");
        assertNotNull(it);
        assertEquals("it-IT", it.getLanguageCode());

        // Portuguese
        SpeechModelInfo pt = manager.getModelInfo("vosk-model-small-pt-0.3");
        assertNotNull(pt);
        assertEquals("pt-PT", pt.getLanguageCode());

        // Russian
        SpeechModelInfo ru = manager.getModelInfo("vosk-model-small-ru-0.22");
        assertNotNull(ru);
        assertEquals("ru-RU", ru.getLanguageCode());

        // Hindi
        SpeechModelInfo hi = manager.getModelInfo("vosk-model-small-hi-0.22");
        assertNotNull(hi);
        assertEquals("hi-IN", hi.getLanguageCode());

        // Hinglish (Hindi + English)
        SpeechModelInfo hinglish = manager.getModelInfo("vosk-model-small-hinglish");
        assertNotNull(hinglish);
        assertEquals("hi-en", hinglish.getLanguageCode());
        assertEquals("Hinglish (Hindi + English)", hinglish.getDisplayName());

        assertTrue(manager.getAvailableModels().size() >= 10);
    }

    @Test
    public void testChecksumHashingHelper() {
        byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input);
            String hex = ModelManager.bytesToHex(hash);
            assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hex.toLowerCase());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testChecksumVerificationLogic() {
        SpeechModelInfo info = new SpeechModelInfo(
                "test-model",
                "en-US",
                "Test Model",
                "https://example.com/primary.zip",
                "https://example.com/alt.zip",
                1000L,
                2000L,
                "AAAA1111",
                "BBBB2222",
                "Apache-2.0"
        );

        assertTrue(info.verifyChecksum("aaaa1111", false));
        assertFalse(info.verifyChecksum("wronghash", false));

        assertTrue(info.verifyChecksum("bbbb2222", true));
        assertFalse(info.verifyChecksum("wronghash", true));
    }

    @Test
    public void testModelInstallationStateEnum() {
        assertFalse(ModelInstallationState.NOT_INSTALLED.isBusy());
        assertTrue(ModelInstallationState.DOWNLOADING.isBusy());
        assertTrue(ModelInstallationState.VERIFYING.isBusy());
        assertTrue(ModelInstallationState.EXTRACTING.isBusy());
        assertTrue(ModelInstallationState.INSTALLED.isReady());
        assertFalse(ModelInstallationState.ERROR.isReady());
    }

    @Test
    public void testValidAndInvalidModelDir(@TempDir Path tempDir) throws IOException {
        File emptyDir = tempDir.resolve("empty").toFile();
        emptyDir.mkdirs();
        assertFalse(ModelManager.isValidModelDir(emptyDir));

        File validDir = tempDir.resolve("valid").toFile();
        validDir.mkdirs();
        new File(validDir, "am").mkdirs();
        new File(validDir, "am/final.mdl").createNewFile();
        assertTrue(ModelManager.isValidModelDir(validDir));

        File confDir = tempDir.resolve("conf_valid").toFile();
        confDir.mkdirs();
        new File(confDir, "model.conf").createNewFile();
        new File(confDir, "other.txt").createNewFile();
        assertTrue(ModelManager.isValidModelDir(confDir));
    }

    @Test
    public void testAlreadyInstalledModelDoesNotDownloadAgain(@TempDir Path tempDir) throws IOException {
        ModelManager manager = new ModelManager(tempDir.toFile());
        File modelDir = manager.getModelDir("vosk-model-small-en-us-0.15");
        modelDir.mkdirs();
        new File(modelDir, "model.conf").createNewFile();
        new File(modelDir, "am").mkdirs();
        new File(modelDir, "am/final.mdl").createNewFile();

        assertTrue(manager.isModelInstalled("vosk-model-small-en-us-0.15"));
        assertEquals(ModelInstallationState.INSTALLED, manager.getModelInstallationState("vosk-model-small-en-us-0.15"));

        AtomicBoolean completed = new AtomicBoolean(false);
        SpeechModelInfo info = manager.getModelInfo("vosk-model-small-en-us-0.15");

        manager.downloadAndInstallModel(info, new DownloadProgressListener() {
            @Override
            public void onProgress(long bytesProcessed, long totalBytes, int percent) {}

            @Override
            public void onStatus(String statusMessage) {}

            @Override
            public void onCompleted() {
                completed.set(true);
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
                fail("Should not fail: " + errorMessage);
            }
        });

        assertTrue(completed.get(), "Already installed model should immediately complete without downloading");
    }

    @Test
    public void testSafeExtractionAndZipSlipPrevention(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("test_model.zip").toFile();
        File destDir = tempDir.resolve("extracted").toFile();
        destDir.mkdirs();

        // Create a valid zip with model files
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry1 = new ZipEntry("model/conf/model.conf");
            zos.putNextEntry(entry1);
            zos.write("test config".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        ModelManager.extractZipSafely(zipFile, destDir, null);
        File extractedConf = new File(destDir, "model/conf/model.conf");
        assertTrue(extractedConf.exists());
    }

    @Test
    public void testZipSlipAttemptIsBlocked(@TempDir Path tempDir) throws Exception {
        File maliciousZip = tempDir.resolve("malicious.zip").toFile();
        File destDir = tempDir.resolve("target").toFile();
        destDir.mkdirs();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(maliciousZip))) {
            // Attempt directory traversal outside target
            ZipEntry maliciousEntry = new ZipEntry("../../escaped.txt");
            zos.putNextEntry(maliciousEntry);
            zos.write("danger".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        assertThrows(Exception.class, () -> {
            ModelManager.extractZipSafely(maliciousZip, destDir, null);
        }, "Zip Slip entry must be caught and blocked");
    }

    @Test
    public void testCancellationMechanism(@TempDir Path tempDir) {
        ModelManager manager = new ModelManager(tempDir.toFile());
        manager.cancelDownload("vosk-model-small-en-us-0.15");
        assertEquals(ModelInstallationState.CANCELLING, manager.getModelInstallationState("vosk-model-small-en-us-0.15"));
    }
}
