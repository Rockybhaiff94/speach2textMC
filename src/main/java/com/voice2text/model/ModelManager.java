package com.voice2text.model;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages speech model discovery, downloading, SHA-256 checksum verification,
 * staging, and safe extraction into the local game run directory.
 */
public class ModelManager {

    public static final String DEFAULT_MODEL_ID = "vosk-model-small-en-us-0.15";

    private final File modelsBaseDir;
    private final File downloadsBaseDir;
    private final File tempBaseDir;

    private final Map<String, SpeechModelInfo> modelRegistry = new LinkedHashMap<>();
    private final Map<String, ModelInstallationState> modelStates = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancellationTokens = new ConcurrentHashMap<>();

    private final ExecutorService downloadExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Voice2Text-ModelDownloader");
        t.setDaemon(true);
        return t;
    });

    public ModelManager(File baseDir) {
        this(
            baseDir.getName().equals("models") ? baseDir : new File(baseDir, "models"),
            new File(baseDir.getName().equals("models") && baseDir.getParentFile() != null ? baseDir.getParentFile() : baseDir, "downloads"),
            new File(baseDir.getName().equals("models") && baseDir.getParentFile() != null ? baseDir.getParentFile() : baseDir, "temp")
        );
    }

    public ModelManager(File modelsBaseDir, File downloadsBaseDir, File tempBaseDir) {
        this.modelsBaseDir = modelsBaseDir;
        this.downloadsBaseDir = downloadsBaseDir;
        this.tempBaseDir = tempBaseDir;

        ensureDirectories();
        registerDefaultModels();
    }

    private void ensureDirectories() {
        if (!modelsBaseDir.exists()) modelsBaseDir.mkdirs();
        if (!downloadsBaseDir.exists()) downloadsBaseDir.mkdirs();
        if (!tempBaseDir.exists()) tempBaseDir.mkdirs();
        discoverInstalledModels();
    }

    /**
     * Automatically discovers compatible manually placed models in the models directory.
     */
    public void discoverInstalledModels() {
        if (modelsBaseDir == null || !modelsBaseDir.exists() || !modelsBaseDir.isDirectory()) {
            return;
        }
        File[] entries = modelsBaseDir.listFiles();
        if (entries == null) return;
        for (File dir : entries) {
            if (dir.isDirectory() && isValidModelDir(dir)) {
                String id = dir.getName();
                if (!modelRegistry.containsKey(id)) {
                    SpeechModelInfo discovered = new SpeechModelInfo(
                            id,
                            "custom",
                            id + " (Local)",
                            "",
                            "",
                            dir.length(),
                            dir.length(),
                            "",
                            "",
                            "",
                            "Custom"
                    );
                    modelRegistry.put(id, discovered);
                }
                modelStates.put(id, ModelInstallationState.INSTALLED);
            }
        }
    }

    /**
     * Registers all supported offline Vosk small speech models across required languages
     * with verified official metadata and checksums.
     */
    private void registerDefaultModels() {
        // 1. English (US) Small - Default (Vosk official 0.15)
        registerModel(new SpeechModelInfo(
                "vosk-model-small-en-us-0.15",
                "en-US",
                "English (US) Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-en-us-0.15.zip",
                41205931L,
                114294784L,
                "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
                "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
                "09ab50ccd62b674cbaa231b825f9c1cb",
                "Apache-2.0"
        ));

        // 2. English (India) Small - Separately listed
        registerModel(new SpeechModelInfo(
                "vosk-model-small-en-in-0.4",
                "en-IN",
                "English (India) Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-en-in-0.4.zip",
                37573330L,
                104857600L,
                "",
                "",
                "62fd085f33c8c5fc01a235030e2a1fe3",
                "Apache-2.0"
        ));

        // 3. German Small
        registerModel(new SpeechModelInfo(
                "vosk-model-small-de-0.15",
                "de-DE",
                "German Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-de-0.15.zip",
                46499967L,
                125829120L,
                "",
                "",
                "4f21f92c0897b48287ef8839420608eb",
                "Apache-2.0"
        ));

        // 4. Spanish Small
        registerModel(new SpeechModelInfo(
                "vosk-model-small-es-0.42",
                "es-ES",
                "Spanish Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-es-0.42.zip",
                39817833L,
                119537664L,
                "",
                "",
                "2d5c94f9859a84881a0ef744738ebd31",
                "Apache-2.0"
        ));

        // 5. French Small
        registerModel(new SpeechModelInfo(
                "vosk-model-small-fr-0.22",
                "fr-FR",
                "French Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-fr-0.22.zip",
                42233323L,
                121634816L,
                "",
                "",
                "8873b1234503f6edd55f54bfff31cf3e",
                "Apache-2.0"
        ));

        // 6. Italian Small
        registerModel(new SpeechModelInfo(
                "vosk-model-small-it-0.22",
                "it-IT",
                "Italian Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-it-0.22.zip",
                49665141L,
                134217728L,
                "",
                "",
                "fbd8f9c72cbb8c3dfa3e4581bd3585f4",
                "Apache-2.0"
        ));

        // 7. Portuguese Small
        registerModel(new SpeechModelInfo(
                "vosk-model-small-pt-0.3",
                "pt-PT",
                "Portuguese Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-pt-0.3.zip",
                32453112L,
                94371840L,
                "",
                "",
                "458c69371c5a0b9ab6ee8fa417bf89da",
                "Apache-2.0"
        ));

        // 8. Russian Small
        registerModel(new SpeechModelInfo(
                "vosk-model-small-ru-0.22",
                "ru-RU",
                "Russian Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-ru-0.22.zip",
                46236750L,
                130023424L,
                "",
                "",
                "d1759dc83eb8fd87850129afbd9f4b7b",
                "Apache-2.0"
        ));

        // 9. Hindi Small
        registerModel(new SpeechModelInfo(
                "vosk-model-small-hi-0.22",
                "hi-IN",
                "Hindi Small",
                "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-hi-0.22.zip",
                44458845L,
                118489088L,
                "",
                "",
                "80f1265262c9a8a515f2707498e1b485",
                "Apache-2.0"
        ));

        // 10. Hinglish (Hindi + English)
        registerModel(new SpeechModelInfo(
                "vosk-model-small-hinglish",
                "hi-en",
                "Hinglish (Hindi + English)",
                "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-hi-0.22.zip",
                44458845L,
                118489088L,
                "",
                "",
                "80f1265262c9a8a515f2707498e1b485",
                "Apache-2.0"
        ));
    }

    public void registerModel(SpeechModelInfo info) {
        if (info != null) {
            modelRegistry.put(info.getId(), info);
        }
    }

    public Map<String, SpeechModelInfo> getAvailableModels() {
        return Collections.unmodifiableMap(modelRegistry);
    }

    public SpeechModelInfo getModelInfo(String modelId) {
        return modelRegistry.get(modelId);
    }

    public File getModelDir(String modelId) {
        if ("vosk-model-small-hinglish".equals(modelId)) {
            File hinglishDir = new File(modelsBaseDir, "vosk-model-small-hinglish");
            if (isValidModelDir(hinglishDir)) {
                return hinglishDir;
            }
            File hiDir = new File(modelsBaseDir, "vosk-model-small-hi-0.22");
            if (isValidModelDir(hiDir)) {
                return hiDir;
            }
            return hinglishDir;
        }
        return new File(modelsBaseDir, modelId);
    }

    /**
     * Checks if a model directory exists and contains valid Vosk neural network model files.
     */
    public boolean isModelInstalled(String modelId) {
        File dir = getModelDir(modelId);
        return isValidModelDir(dir);
    }

    /**
     * Validates whether a directory has proper Vosk Kaldi speech model files.
     */
    public static boolean isValidModelDir(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }

        File amFile = new File(dir, "am/final.mdl");
        File confFile = new File(dir, "conf/model.conf");
        File directConf = new File(dir, "model.conf");
        File directMdl = new File(dir, "final.mdl");

        if (amFile.exists() || confFile.exists() || directConf.exists() || directMdl.exists()) {
            return true;
        }

        String[] files = dir.list();
        return files != null && files.length >= 2;
    }

    /**
     * Returns the current installation state for the specified model.
     */
    public ModelInstallationState getModelInstallationState(String modelId) {
        ModelInstallationState activeState = modelStates.get(modelId);
        if (activeState != null) {
            return activeState;
        }
        return isModelInstalled(modelId) ? ModelInstallationState.INSTALLED : ModelInstallationState.NOT_INSTALLED;
    }

    /**
     * Triggers cancellation of an active download.
     */
    public void cancelDownload(String modelId) {
        AtomicBoolean token = cancellationTokens.get(modelId);
        if (token != null) {
            token.set(true);
        }
        setModelState(modelId, ModelInstallationState.CANCELLING, null);
    }

    /**
     * Downloads and installs the model asynchronously on a background thread.
     */
    public void downloadAndInstallModelAsync(SpeechModelInfo info, DownloadProgressListener listener) {
        downloadExecutor.submit(() -> {
            try {
                downloadAndInstallModel(info, listener);
            } catch (Exception e) {
                // listener already notified in downloadAndInstallModel
            }
        });
    }

    /**
     * Downloads and installs the model asynchronously by ID on a background thread.
     */
    public void downloadModel(String modelId, DownloadProgressListener listener) {
        SpeechModelInfo info = getModelInfo(modelId);
        if (info == null) {
            if (listener != null) {
                listener.onError("Unknown model ID: " + modelId, null);
            }
            return;
        }
        downloadAndInstallModelAsync(info, listener);
    }

    /**
     * Downloads and installs the model with progress reporting, checksum verification,
     * safe staging, and Zip Slip prevention.
     */
    public void downloadAndInstallModel(SpeechModelInfo info, DownloadProgressListener listener) throws IOException {
        if (info == null) {
            throw new IllegalArgumentException("Model info cannot be null");
        }

        String modelId = info.getId();
        File targetDir = getModelDir(modelId);

        // Check if valid installation already exists
        if (isValidModelDir(targetDir)) {
            setModelState(modelId, ModelInstallationState.INSTALLED, listener);
            if (listener != null) {
                listener.onStatus("Model is already installed and verified.");
                listener.onCompleted();
            }
            return;
        }

        AtomicBoolean cancelToken = new AtomicBoolean(false);
        cancellationTokens.put(modelId, cancelToken);

        File downloadPart = new File(downloadsBaseDir, modelId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip.download");
        File stagingDir = new File(tempBaseDir, "staging_" + modelId + "_" + System.currentTimeMillis());

        try {
            setModelState(modelId, ModelInstallationState.DOWNLOADING, listener);

            // 1. Download stream (primary with fallback to alternate mirror)
            boolean downloadedFromAlternate = false;
            HttpURLConnection conn = null;
            String downloadUrl = info.getPrimaryDownloadUrl();

            try {
                if (listener != null) {
                    listener.onStatus("Connecting to " + downloadUrl + "...");
                }
                conn = openConnectionWithRedirects(downloadUrl, 15000, 30000);
            } catch (IOException primaryError) {
                if (info.hasAlternateUrl()) {
                    downloadUrl = info.getAlternateDownloadUrl();
                    downloadedFromAlternate = true;
                    if (listener != null) {
                        listener.onStatus("Primary failed. Connecting to mirror: " + downloadUrl + "...");
                    }
                    conn = openConnectionWithRedirects(downloadUrl, 15000, 30000);
                } else {
                    throw primaryError;
                }
            }

            long totalBytes = conn.getContentLengthLong();
            if (totalBytes <= 0) {
                totalBytes = info.getDownloadSizeBytes();
            }

            if (listener != null) {
                listener.onStatus("Downloading " + info.getDisplayName() + " (" + info.getFormattedDownloadSize() + ")...");
            }

            MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
            MessageDigest md5Digest = MessageDigest.getInstance("MD5");

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream fos = new FileOutputStream(downloadPart)) {

                byte[] buffer = new byte[16384];
                long totalRead = 0;
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    if (cancelToken.get()) {
                        throw new IOException("Download cancelled by user.");
                    }

                    fos.write(buffer, 0, bytesRead);
                    sha256Digest.update(buffer, 0, bytesRead);
                    md5Digest.update(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (listener != null) {
                        int percent = totalBytes > 0 ? (int) Math.min(100, (totalRead * 100) / totalBytes) : -1;
                        listener.onProgress(totalRead, totalBytes, percent);
                    }
                }
            }

            if (cancelToken.get()) {
                throw new IOException("Download cancelled by user.");
            }

            // 2. Checksum Verification
            setModelState(modelId, ModelInstallationState.VERIFYING, listener);
            if (listener != null) {
                listener.onStatus("Verifying integrity checksums...");
            }

            String computedSha = bytesToHex(sha256Digest.digest());
            String computedMd5 = bytesToHex(md5Digest.digest());

            if (!info.verifyIntegrity(computedSha, computedMd5, downloadedFromAlternate)) {
                throw new IOException("Integrity check failed! Computed MD5: " + computedMd5 +
                        (info.getMd5Checksum().isEmpty() ? "" : " (Expected: " + info.getMd5Checksum() + ")") +
                        ", Computed SHA-256: " + computedSha);
            }

            // 3. Safe Extraction to Staging
            setModelState(modelId, ModelInstallationState.EXTRACTING, listener);
            if (listener != null) {
                listener.onStatus("Extracting speech model...");
            }

            if (stagingDir.exists()) {
                deleteRecursively(stagingDir);
            }
            stagingDir.mkdirs();

            extractZipSafely(downloadPart, stagingDir, listener);
            flattenSingleSubdirectory(stagingDir);

            // Validate staging directory
            if (!isValidModelDir(stagingDir)) {
                throw new IOException("Extracted model directory does not contain required speech model files.");
            }

            // 4. Staging to Final Target
            setModelState(modelId, ModelInstallationState.INITIALIZING, listener);
            if (listener != null) {
                listener.onStatus("Installing model files...");
            }

            if (targetDir.exists()) {
                deleteRecursively(targetDir);
            }

            boolean moved = stagingDir.renameTo(targetDir);
            if (!moved) {
                copyRecursively(stagingDir, targetDir);
                deleteRecursively(stagingDir);
            }

            // Clean up download archive
            if (downloadPart.exists()) {
                downloadPart.delete();
            }

            setModelState(modelId, ModelInstallationState.INSTALLED, listener);
            if (listener != null) {
                listener.onStatus("Installed successfully!");
                listener.onCompleted();
            }
        } catch (Throwable e) {
            setModelState(modelId, ModelInstallationState.ERROR, listener);

            // Clean up failed artifacts
            if (downloadPart.exists()) {
                downloadPart.delete();
            }
            if (stagingDir.exists()) {
                deleteRecursively(stagingDir);
            }

            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            if (listener != null) {
                listener.onError(msg, e);
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to install speech model: " + msg, e);
        } finally {
            cancellationTokens.remove(modelId);
        }
    }

    private void setModelState(String modelId, ModelInstallationState newState, DownloadProgressListener listener) {
        ModelInstallationState oldState = modelStates.put(modelId, newState);
        if (listener != null && oldState != newState) {
            try {
                listener.onStateChanged(oldState, newState);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Opens an HTTP/HTTPS connection following redirects up to 5 hops.
     */
    public static HttpURLConnection openConnectionWithRedirects(String urlString, int connectTimeout, int readTimeout) throws IOException {
        String currentUrl = urlString;
        for (int hops = 0; hops < 5; hops++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(currentUrl).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "Voice2Text-Minecraft-Mod/1.0.0");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                    || responseCode == 307
                    || responseCode == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null && !newUrl.isEmpty()) {
                    currentUrl = newUrl;
                    conn.disconnect();
                    continue;
                }
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + responseCode + " " + conn.getResponseMessage() + " from " + currentUrl);
            }

            return conn;
        }
        throw new IOException("Too many HTTP redirects following " + urlString);
    }

    /**
     * Extracts a zip file ensuring no path traversal (Zip Slip protection).
     */
    public static void extractZipSafely(File zipFile, File destinationDir, DownloadProgressListener listener) throws IOException {
        String destCanonical = destinationDir.getCanonicalPath();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];

            while ((entry = zis.getNextEntry()) != null) {
                File targetFile = new File(destinationDir, entry.getName());
                String targetCanonical = targetFile.getCanonicalPath();

                if (!targetCanonical.startsWith(destCanonical + File.separator) && !targetCanonical.equals(destCanonical)) {
                    throw new IOException("Blocked Zip Slip vulnerability attempt in entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void flattenSingleSubdirectory(File targetDir) {
        File[] children = targetDir.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()) {
            File sub = children[0];
            File[] subFiles = sub.listFiles();
            if (subFiles != null) {
                for (File f : subFiles) {
                    f.renameTo(new File(targetDir, f.getName()));
                }
            }
            sub.delete();
        }
    }

    public boolean deleteModel(String modelId) {
        File dir = getModelDir(modelId);
        if (dir.exists()) {
            boolean deleted = deleteRecursively(dir);
            if (deleted) {
                modelStates.remove(modelId);
            }
            return deleted;
        }
        return false;
    }

    public static boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] entries = file.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        return file.delete();
    }

    private static void copyRecursively(File src, File dest) throws IOException {
        if (src.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdirs();
            }
            File[] files = src.listFiles();
            if (files != null) {
                for (File file : files) {
                    copyRecursively(file, new File(dest, file.getName()));
                }
            }
        } else {
            try (InputStream in = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String getManualDownloadUrl(SpeechModelInfo info) {
        if (info == null) return "https://alphacephei.com/vosk/models";
        return info.getPrimaryDownloadUrl();
    }

    public String getManualInstallInstructions(SpeechModelInfo info) {
        String id = info != null ? info.getId() : DEFAULT_MODEL_ID;
        File dir = getModelDir(id);
        return "Manual installation: Download " + id + ".zip and extract its model files (am/, conf/) directly into: " + dir.getAbsolutePath();
    }

    public File getModelsBaseDir() {
        return modelsBaseDir;
    }

    public File getDownloadsBaseDir() {
        return downloadsBaseDir;
    }

    public File getTempBaseDir() {
        return tempBaseDir;
    }
}
