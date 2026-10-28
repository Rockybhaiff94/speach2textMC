package com.voice2text.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

public class ModelManagerDownloadTest {

    @Test
    public void testDownloadSuccessWithLocalHttpServerAndChecksum(@TempDir Path tempDir) throws Exception {
        // Prepare valid model zip
        File serverZip = tempDir.resolve("server_model.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(serverZip))) {
            ZipEntry entry1 = new ZipEntry("model/am/final.mdl");
            zos.putNextEntry(entry1);
            zos.write("model data".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            ZipEntry entry2 = new ZipEntry("model/conf/model.conf");
            zos.putNextEntry(entry2);
            zos.write("config data".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        byte[] zipBytes = java.nio.file.Files.readAllBytes(serverZip.toPath());
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String expectedHash = ModelManager.bytesToHex(md.digest(zipBytes));

        // Start lightweight test HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model.zip", exchange -> {
            exchange.sendResponseHeaders(200, zipBytes.length);
            exchange.getResponseBody().write(zipBytes);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        String url = "http://127.0.0.1:" + port + "/model.zip";

        try {
            ModelManager manager = new ModelManager(tempDir.toFile());
            SpeechModelInfo testModel = new SpeechModelInfo(
                    "test-dl-model",
                    "en-US",
                    "Test Download Model",
                    url,
                    "",
                    zipBytes.length,
                    zipBytes.length * 2,
                    expectedHash,
                    "",
                    "Apache-2.0"
            );

            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicInteger progressCalls = new AtomicInteger(0);

            manager.downloadAndInstallModel(testModel, new DownloadProgressListener() {
                @Override
                public void onProgress(long bytesProcessed, long totalBytes, int percent) {
                    progressCalls.incrementAndGet();
                }

                @Override
                public void onStatus(String statusMessage) {}

                @Override
                public void onCompleted() {
                    completed.set(true);
                }

                @Override
                public void onError(String errorMessage, Throwable throwable) {
                    fail("Unexpected error: " + errorMessage);
                }
            });

            assertTrue(completed.get(), "Model installation should succeed");
            assertTrue(progressCalls.get() > 0, "Progress should be reported during download");
            assertTrue(manager.isModelInstalled("test-dl-model"), "Model should be marked as installed");
            assertEquals(ModelInstallationState.INSTALLED, manager.getModelInstallationState("test-dl-model"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testChecksumMismatchRejectsInstallationAndCleansUp(@TempDir Path tempDir) throws Exception {
        byte[] dummyData = "corrupt data".getBytes(StandardCharsets.UTF_8);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bad_model.zip", exchange -> {
            exchange.sendResponseHeaders(200, dummyData.length);
            exchange.getResponseBody().write(dummyData);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        String url = "http://127.0.0.1:" + port + "/bad_model.zip";

        try {
            ModelManager manager = new ModelManager(tempDir.toFile());
            SpeechModelInfo testModel = new SpeechModelInfo(
                    "corrupt-model",
                    "en-US",
                    "Corrupt Model",
                    url,
                    "",
                    dummyData.length,
                    dummyData.length * 2,
                    "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", // Wrong checksum
                    "",
                    "Apache-2.0"
            );

            AtomicReference<String> error = new AtomicReference<>();

            assertThrows(IOException.class, () -> {
                manager.downloadAndInstallModel(testModel, new DownloadProgressListener() {
                    @Override
                    public void onProgress(long bytesProcessed, long totalBytes, int percent) {}

                    @Override
                    public void onStatus(String statusMessage) {}

                    @Override
                    public void onCompleted() {
                        fail("Must not complete on checksum mismatch");
                    }

                    @Override
                    public void onError(String errorMessage, Throwable throwable) {
                        error.set(errorMessage);
                    }
                });
            });

            assertNotNull(error.get());
            assertTrue(error.get().contains("checksum mismatch") || error.get().contains("SHA-256"));
            assertFalse(manager.isModelInstalled("corrupt-model"));
            assertEquals(ModelInstallationState.ERROR, manager.getModelInstallationState("corrupt-model"));

            // Verify no partial files left in downloads
            File downloadsDir = manager.getDownloadsBaseDir();
            File[] files = downloadsDir.listFiles();
            assertTrue(files == null || files.length == 0, "Partial downloads must be cleaned up on failure");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testHttpErrorHandlingAndFailoverToAlternateUrl(@TempDir Path tempDir) throws Exception {
        // Server where primary URL returns 404, but alternate URL returns valid zip
        File serverZip = tempDir.resolve("alt_model.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(serverZip))) {
            ZipEntry entry1 = new ZipEntry("model/am/final.mdl");
            zos.putNextEntry(entry1);
            zos.write("alt data".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        byte[] zipBytes = java.nio.file.Files.readAllBytes(serverZip.toPath());

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/primary_404.zip", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/alt_ok.zip", exchange -> {
            exchange.sendResponseHeaders(200, zipBytes.length);
            exchange.getResponseBody().write(zipBytes);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        String primaryUrl = "http://127.0.0.1:" + port + "/primary_404.zip";
        String altUrl = "http://127.0.0.1:" + port + "/alt_ok.zip";

        try {
            ModelManager manager = new ModelManager(tempDir.toFile());
            SpeechModelInfo testModel = new SpeechModelInfo(
                    "failover-model",
                    "en-US",
                    "Failover Model",
                    primaryUrl,
                    altUrl,
                    zipBytes.length,
                    zipBytes.length * 2,
                    "",
                    "",
                    "Apache-2.0"
            );

            AtomicBoolean completed = new AtomicBoolean(false);

            manager.downloadAndInstallModel(testModel, new DownloadProgressListener() {
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
                    fail("Should have fallen back to alternate URL: " + errorMessage);
                }
            });

            assertTrue(completed.get(), "Failover to alternate mirror should succeed");
            assertTrue(manager.isModelInstalled("failover-model"));
        } finally {
            server.stop(0);
        }
    }
}
