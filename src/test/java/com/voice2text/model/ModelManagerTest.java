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

        assertEquals(2, manager.getAvailableModels().size());

        // Model 1: Whisper Hindi2Hinglish Apex
        SpeechModelInfo apex = manager.getModelInfo("whisper-hindi2hinglish-apex");
        assertNotNull(apex);
        assertEquals("hi-en", apex.getLanguageCode());
        assertEquals("Whisper Hindi2Hinglish Apex", apex.getDisplayName());
        assertEquals(ModelFamily.HINGLISH_SPECIALIZED, apex.getFamily());
        assertEquals("Roman Hindi (Hinglish) Direct", apex.getOutputScript());
        assertTrue(apex.hasAlternateUrl());
        assertEquals("be07e048e1e599ad46341c08d2a135645097a530221678b7a0dd1b1919c6e1b21", apex.getPrimarySha256());
        assertEquals("ab7280bbcf29e334f7e3b2a9ac0ca386", apex.getMd5Checksum());

        // Model 2: Whisper Small
        SpeechModelInfo small = manager.getModelInfo("whisper-small");
        assertNotNull(small);
        assertEquals("en-US", small.getLanguageCode());
        assertEquals("Whisper Small", small.getDisplayName());
        assertEquals(ModelFamily.WHISPER, small.getFamily());
        assertTrue(small.hasAlternateUrl());
        assertEquals("30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498", small.getPrimarySha256());
        assertEquals("09ab50ccd62b674cbaa231b825f9c1cb", small.getMd5Checksum());

        // Legacy fallback mapping
        SpeechModelInfo fallbackEn = manager.getModelInfo("vosk-model-small-en-us-0.15");
        assertNotNull(fallbackEn);
        assertEquals("whisper-small", fallbackEn.getId());
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
