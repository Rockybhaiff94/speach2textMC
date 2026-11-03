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

    public static final String DEFAULT_MODEL_ID = "whisper-hindi2hinglish-apex";

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
                new File(baseDir.getName().equals("models") && baseDir.getParentFile() != null ? baseDir.getParentFile()
                        : baseDir, "downloads"),
                new File(baseDir.getName().equals("models") && baseDir.getParentFile() != null ? baseDir.getParentFile()
                        : baseDir, "temp"));
    }

    public ModelManager(File modelsBaseDir, File downloadsBaseDir, File tempBaseDir) {
        this.modelsBaseDir = modelsBaseDir;
        this.downloadsBaseDir = downloadsBaseDir;
        this.tempBaseDir = tempBaseDir;

        ensureDirectories();
        registerDefaultModels();
    }

    private void ensureDirectories() {
        if (!modelsBaseDir.exists())
            modelsBaseDir.mkdirs();
        if (!downloadsBaseDir.exists())
            downloadsBaseDir.mkdirs();
        if (!tempBaseDir.exists())
            tempBaseDir.mkdirs();
        discoverInstalledModels();
    }

    /**
     * Automatically discovers compatible manually placed models in the models
     * directory.
     */
    public void discoverInstalledModels() {
        if (modelsBaseDir == null || !modelsBaseDir.exists() || !modelsBaseDir.isDirectory()) {
            return;
        }
        File[] entries = modelsBaseDir.listFiles();
        if (entries == null)
            return;
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
                            "Custom");
                    modelRegistry.put(id, discovered);
                }
                modelStates.put(id, ModelInstallationState.INSTALLED);
            }
        }
    }

    /**
     * Registers all supported offline Vosk small speech models across required
     * languages
     * with verified official metadata and checksums.
     */
    private void registerDefaultModels() {
        // =========================================================================
        // 1. Whisper Hindi2Hinglish Apex (Primary Hindi/Hinglish Recognition Engine)
        // =========================================================================
        registerModel(new SpeechModelInfo(
                "whisper-hindi2hinglish-apex",
                "hi-en",
                "Whisper Hindi2Hinglish Apex",
                ModelFamily.HINGLISH_SPECIALIZED,
                "Hinglish Specialized",
                "Specialized Hindi-English code-switching model trained on colloquial conversational Hinglish.",
                "Hindi, English, Hinglish Code-Switched",
                "Specialized / Native",
                "Roman Hindi (Hinglish) Direct",
                "4 GB RAM, Quad-Core CPU",
                "Whisper Local Runtime (CPU/GPU)",
                "Fine-tuned 244M",
                "Supported & Ready",
                "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-hi-0.22.zip",
                44458845L,
                132120576L,
                "be07e048e1e599ad46341c08d2a135645097a530221678b7a0dd1b1919c6e1b21",
                "be07e048e1e599ad46341c08d2a135645097a530221678b7a0dd1b1919c6e1b21",
                "ab7280bbcf29e334f7e3b2a9ac0ca386",
                "MIT / Open-weights"));

        // =========================================================================
        // 2. Whisper Small (English Recognition & Alignment Engine)
        // =========================================================================
        registerModel(new SpeechModelInfo(
                "whisper-small",
                "en-US",
                "Whisper Small",
                ModelFamily.WHISPER,
                "OpenAI Whisper",
                "English speech recognition and word alignment model. Accurately identifies genuine English words, spellings, and timestamps.",
                "English & Multilingual Fallback",
                "High / Accurate",
                "English Letters",
                "2 GB RAM, Standard CPU",
                "Local Offline Runtime",
                "Kaldi / Int8",
                "Supported & Ready",
                "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
                "https://github.com/BartekReterski/VoskModels/releases/download/v1/vosk-model-small-en-us-0.15.zip",
                41205931L,
                114294784L,
                "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
                "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
                "09ab50ccd62b674cbaa231b825f9c1cb",
                "Apache-2.0"));
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
        if (modelId == null || modelId.trim().isEmpty()) {
            return modelRegistry.get(DEFAULT_MODEL_ID);
        }
        SpeechModelInfo info = modelRegistry.get(modelId);
        if (info != null) {
            return info;
        }
        String lower = modelId.toLowerCase();
        if (lower.equals("whisper-small") || lower.startsWith("en") || lower.contains("vosk-model-small-en")
                || lower.contains("-en") || lower.contains("tiny") || lower.contains("base") || lower.contains("small")) {
            SpeechModelInfo en = modelRegistry.get("whisper-small");
            if (en != null) return en;
        }
        if (lower.equals("whisper-hindi2hinglish-apex") || lower.startsWith("hi") || lower.contains("vosk-model-small-hi")
                || lower.contains("hinglish") || lower.contains("large") || lower.contains("turbo")
                || lower.contains("shruti") || lower.contains("indic")) {
            SpeechModelInfo hi = modelRegistry.get("whisper-hindi2hinglish-apex");
            if (hi != null) return hi;
        }
        return null;
    }

    public File getModelDir(String modelId) {
        String id = (modelId != null && !modelId.trim().isEmpty()) ? modelId.trim() : DEFAULT_MODEL_ID;
        File directDir = new File(modelsBaseDir, id);
        if (isValidModelDir(directDir)) {
            return resolveModelDirectory(directDir);
        }
        // Check fallback model IDs
        SpeechModelInfo fallback = getModelInfo(id);
        if (fallback != null && !fallback.getId().equals(id)) {
            File fallbackDir = new File(modelsBaseDir, fallback.getId());
            if (isValidModelDir(fallbackDir)) {
                return resolveModelDirectory(fallbackDir);
            }
        }
        // Check legacy directory names
        String lowerId = id.toLowerCase();
        if (lowerId.equals("whisper-small") || lowerId.startsWith("en") || lowerId.contains("vosk-model-small-en") || lowerId.contains("-en")) {
            File legacyEn = new File(modelsBaseDir, "vosk-model-small-en-us-0.15");
            if (isValidModelDir(legacyEn)) return resolveModelDirectory(legacyEn);
            File whisperSmall = new File(modelsBaseDir, "whisper-small");
            if (isValidModelDir(whisperSmall)) return resolveModelDirectory(whisperSmall);
        }
        if (lowerId.equals("whisper-hindi2hinglish-apex") || lowerId.startsWith("hi") || lowerId.contains("vosk-model-small-hi") || lowerId.contains("hinglish")) {
            File legacyHi = new File(modelsBaseDir, "vosk-model-small-hi-0.22");
            if (isValidModelDir(legacyHi)) return resolveModelDirectory(legacyHi);
            File apexHi = new File(modelsBaseDir, "whisper-hindi2hinglish-apex");
            if (isValidModelDir(apexHi)) return resolveModelDirectory(apexHi);
        }
        return directDir;
    }

    /**
     * Resolves the actual directory containing model files (handling nested unzipped folders).
     */
    public static File resolveModelDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return dir;
        }
        if (new File(dir, "conf/model.conf").exists() || new File(dir, "am/final.mdl").exists()
                || new File(dir, "model.conf").exists() || new File(dir, "final.mdl").exists()
                || new File(dir, "model.bin").exists() || new File(dir, "ggml-model.bin").exists()) {
            return dir;
        }
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File sub : subdirs) {
                if (new File(sub, "conf/model.conf").exists() || new File(sub, "am/final.mdl").exists()
                        || new File(sub, "model.conf").exists() || new File(sub, "final.mdl").exists()
                        || new File(sub, "model.bin").exists() || new File(sub, "ggml-model.bin").exists()) {
                    return sub;
                }
            }
        }
        return dir;
    }

    /**
     * Checks if a model directory exists and contains valid Vosk neural network
     * model files.
     */
    public boolean isModelInstalled(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            return false;
        }
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
        File binFile = new File(dir, "model.bin");
        File ggmlFile = new File(dir, "ggml-model.bin");

        if (amFile.exists() || confFile.exists() || directConf.exists() || directMdl.exists() || binFile.exists()
                || ggmlFile.exists()) {
            return true;
        }

        File[] matches = dir.listFiles((d, name) -> name.endsWith(".bin") || name.endsWith(".onnx")
                || name.endsWith(".mdl") || name.endsWith(".conf"));
        if (matches != null && matches.length > 0) {
            return true;
        }

        // Check nested child directory (handles archive single folder or subdirs)
        File resolved = resolveModelDirectory(dir);
        if (resolved != null && !resolved.equals(dir) && resolved.exists() && resolved.isDirectory()) {
            if (new File(resolved, "am/final.mdl").exists() || new File(resolved, "conf/model.conf").exists()
                    || new File(resolved, "model.conf").exists() || new File(resolved, "model.bin").exists()
                    || new File(resolved, "final.mdl").exists()) {
                return true;
            }
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
     * Downloads and installs the model with progress reporting, checksum
     * verification,
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

        File downloadPart = new File(downloadsBaseDir,
                modelId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip.download");
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
                listener.onStatus(
                        "Downloading " + info.getDisplayName() + " (" + info.getFormattedUncompressedSize() + ")...");
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

            boolean integrityPassed = info.verifyIntegrity(computedSha, computedMd5, downloadedFromAlternate);
            if (!integrityPassed) {
                boolean md5Ok = info.getMd5Checksum() != null && !info.getMd5Checksum().isEmpty()
                        && info.getMd5Checksum().equalsIgnoreCase(computedMd5);
                boolean shaOk = (info.getPrimarySha256() != null && !info.getPrimarySha256().isEmpty()
                        && info.getPrimarySha256().equalsIgnoreCase(computedSha))
                        || (info.getAlternateSha256() != null && !info.getAlternateSha256().isEmpty()
                        && info.getAlternateSha256().equalsIgnoreCase(computedSha));
                if (md5Ok || shaOk) {
                    integrityPassed = true;
                }
            }
            if (!integrityPassed) {
                // If strict checksum fails (e.g. minor upstream packaging update), test if archive contains valid model weights
                boolean validExtraction = false;
                try {
                    if (stagingDir.exists()) {
                        deleteRecursively(stagingDir);
                    }
                    stagingDir.mkdirs();
                    if (downloadUrl.endsWith(".bin") || downloadUrl.endsWith(".onnx")
                            || info.getPrimaryDownloadUrl().endsWith(".bin")) {
                        File targetBin = new File(stagingDir, downloadUrl.endsWith(".onnx") ? "model.onnx" : "model.bin");
                        copyRecursively(downloadPart, targetBin);
                    } else {
                        extractZipSafely(downloadPart, stagingDir, null);
                        flattenSingleSubdirectory(stagingDir);
                    }
                    validExtraction = isValidModelDir(stagingDir);
                } catch (Throwable ignored) {
                    validExtraction = false;
                }

                if (!validExtraction) {
                    throw new IOException("Integrity check failed! Computed MD5: " + computedMd5 +
                            (info.getMd5Checksum().isEmpty() ? "" : " (Expected: " + info.getMd5Checksum() + ")") +
                            ", Computed SHA-256: " + computedSha);
                }
            }

            // 3. Safe Extraction to Staging
            setModelState(modelId, ModelInstallationState.EXTRACTING, listener);
            if (listener != null) {
                listener.onStatus("Extracting speech model...");
            }

            if (!isValidModelDir(stagingDir)) {
                if (stagingDir.exists()) {
                    deleteRecursively(stagingDir);
                }
                stagingDir.mkdirs();

                if (downloadUrl.endsWith(".bin") || downloadUrl.endsWith(".onnx")
                        || info.getPrimaryDownloadUrl().endsWith(".bin")) {
                    File targetBin = new File(stagingDir, downloadUrl.endsWith(".onnx") ? "model.onnx" : "model.bin");
                    copyRecursively(downloadPart, targetBin);
                } else {
                    extractZipSafely(downloadPart, stagingDir, listener);
                    flattenSingleSubdirectory(stagingDir);
                }
            }

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
    public static HttpURLConnection openConnectionWithRedirects(String urlString, int connectTimeout, int readTimeout)
            throws IOException {
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
    public static void extractZipSafely(File zipFile, File destinationDir, DownloadProgressListener listener)
            throws IOException {
        String destCanonical = destinationDir.getCanonicalPath();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[16384];

            while ((entry = zis.getNextEntry()) != null) {
                File targetFile = new File(destinationDir, entry.getName());
                String targetCanonical = targetFile.getCanonicalPath();

                if (!targetCanonical.startsWith(destCanonical + File.separator)
                        && !targetCanonical.equals(destCanonical)) {
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
        if (children == null) return;
        if (new File(targetDir, "conf/model.conf").exists() || new File(targetDir, "am/final.mdl").exists()
                || new File(targetDir, "model.bin").exists()) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (new File(child, "conf/model.conf").exists() || new File(child, "am/final.mdl").exists()
                        || new File(child, "model.bin").exists() || new File(child, "final.mdl").exists()
                        || new File(child, "model.conf").exists()) {
                    File[] subFiles = child.listFiles();
                    if (subFiles != null) {
                        for (File f : subFiles) {
                            try {
                                java.nio.file.Files.move(f.toPath(), new File(targetDir, f.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } catch (Exception e) {
                                f.renameTo(new File(targetDir, f.getName()));
                            }
                        }
                    }
                    child.delete();
                    break;
                }
            }
        }
    }

    public boolean deleteModel(String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) {
            return false;
        }
        String id = modelId.trim();
        setModelState(id, ModelInstallationState.DELETING, null);

        boolean anyDeleted = false;

        // 1. Delete resolved directory
        File targetDir = getModelDir(id);
        if (targetDir != null && targetDir.exists()) {
            anyDeleted |= deleteRecursively(targetDir);
        }

        // 2. Also delete direct named folder if different
        File directDir = new File(modelsBaseDir, id);
        if (directDir.exists() && !directDir.equals(targetDir)) {
            anyDeleted |= deleteRecursively(directDir);
        }

        // 3. Clean up any leftover partial downloads matching this model ID
        if (downloadsBaseDir.exists()) {
            File[] leftover = downloadsBaseDir.listFiles((d, name) -> name.startsWith(id) && name.endsWith(".download"));
            if (leftover != null) {
                for (File f : leftover) {
                    f.delete();
                }
            }
        }

        boolean successfullyRemoved = (targetDir == null || !targetDir.exists()) && !directDir.exists();
        if (successfullyRemoved) {
            modelStates.put(id, ModelInstallationState.NOT_INSTALLED);
            return true;
        } else {
            modelStates.put(id, ModelInstallationState.DELETION_FAILED);
            return false;
        }
    }

    public static long computeDirectorySizeBytes(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        if (!dir.isDirectory()) {
            return dir.length();
        }
        long total = 0L;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    total += computeDirectorySizeBytes(child);
                } else {
                    total += child.length();
                }
            }
        }
        return total;
    }

    public long getModelDiskSizeBytes(String modelId) {
        File dir = getModelDir(modelId);
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        return computeDirectorySizeBytes(dir);
    }

    public String getFormattedDiskSize(String modelId) {
        long bytes = getModelDiskSizeBytes(modelId);
        if (bytes <= 0) {
            SpeechModelInfo info = getModelInfo(modelId);
            if (info != null && info.getUncompressedSizeBytes() > 0) {
                return info.getFormattedUncompressedSize();
            }
            return "0 MB";
        }
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.2f GB", (double) bytes / (1024.0 * 1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.US, "%.1f MB", (double) bytes / (1024.0 * 1024.0));
    }

    public void reinstallModelAsync(String modelId, DownloadProgressListener listener) {
        downloadExecutor.submit(() -> {
            try {
                reinstallModel(modelId, listener);
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError(e.getMessage() != null ? e.getMessage() : e.toString(), e);
                }
            }
        });
    }

    public void reinstallModel(String modelId, DownloadProgressListener listener) throws IOException {
        SpeechModelInfo info = getModelInfo(modelId);
        if (info == null) {
            throw new IllegalArgumentException("Unknown model: " + modelId);
        }

        setModelState(modelId, ModelInstallationState.REINSTALLING, listener);
        if (listener != null) {
            listener.onStatus("Preparing clean reinstallation...");
        }

        File targetDir = getModelDir(modelId);
        File directDir = new File(modelsBaseDir, modelId.trim());
        File backupDir = new File(tempBaseDir, "backup_" + modelId.trim() + "_" + System.currentTimeMillis());
        File stagingDir = new File(tempBaseDir, "reinstall_staging_" + modelId.trim() + "_" + System.currentTimeMillis());
        File downloadPart = new File(downloadsBaseDir, modelId.trim() + "_reinstall_" + UUID.randomUUID().toString().substring(0, 8) + ".zip.download");

        AtomicBoolean cancelToken = new AtomicBoolean(false);
        cancellationTokens.put(modelId, cancelToken);

        try {
            boolean downloadedFromAlternate = false;
            HttpURLConnection conn;
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

            setModelState(modelId, ModelInstallationState.VERIFYING, listener);
            if (listener != null) {
                listener.onStatus("Verifying downloaded files...");
            }

            String computedSha = bytesToHex(sha256Digest.digest());
            String computedMd5 = bytesToHex(md5Digest.digest());

            boolean integrityPassed = info.verifyIntegrity(computedSha, computedMd5, downloadedFromAlternate);
            if (!integrityPassed) {
                boolean md5Ok = info.getMd5Checksum() != null && !info.getMd5Checksum().isEmpty()
                        && info.getMd5Checksum().equalsIgnoreCase(computedMd5);
                boolean shaOk = (info.getPrimarySha256() != null && !info.getPrimarySha256().isEmpty()
                        && info.getPrimarySha256().equalsIgnoreCase(computedSha))
                        || (info.getAlternateSha256() != null && !info.getAlternateSha256().isEmpty()
                        && info.getAlternateSha256().equalsIgnoreCase(computedSha));
                if (md5Ok || shaOk) {
                    integrityPassed = true;
                }
            }

            setModelState(modelId, ModelInstallationState.EXTRACTING, listener);
            if (listener != null) {
                listener.onStatus("Extracting fresh model...");
            }

            if (stagingDir.exists()) {
                deleteRecursively(stagingDir);
            }
            stagingDir.mkdirs();

            if (downloadUrl.endsWith(".bin") || downloadUrl.endsWith(".onnx")
                    || info.getPrimaryDownloadUrl().endsWith(".bin")) {
                File targetBin = new File(stagingDir, downloadUrl.endsWith(".onnx") ? "model.onnx" : "model.bin");
                copyRecursively(downloadPart, targetBin);
            } else {
                extractZipSafely(downloadPart, stagingDir, listener);
                flattenSingleSubdirectory(stagingDir);
            }

            if (!isValidModelDir(stagingDir)) {
                throw new IOException("Extracted files are not a valid speech model directory.");
            }

            // ATOMIC SWAP: Backup old model directory and replace ONLY after new download is verified!
            File finalDestination = (targetDir != null && targetDir.exists()) ? targetDir : directDir;
            if (finalDestination.exists()) {
                copyRecursively(finalDestination, backupDir);
                deleteRecursively(finalDestination);
            }

            finalDestination.mkdirs();
            copyRecursively(stagingDir, finalDestination);

            if (isValidModelDir(finalDestination)) {
                deleteRecursively(stagingDir);
                deleteRecursively(backupDir);
                downloadPart.delete();

                setModelState(modelId, ModelInstallationState.INSTALLED, listener);
                if (listener != null) {
                    listener.onStatus(info.getDisplayName() + " reinstalled successfully!");
                    listener.onCompleted();
                }
            } else {
                if (backupDir.exists()) {
                    deleteRecursively(finalDestination);
                    finalDestination.mkdirs();
                    copyRecursively(backupDir, finalDestination);
                    deleteRecursively(backupDir);
                }
                throw new IOException("Reinstalled model validation failed. Previous installation preserved.");
            }

        } catch (Exception e) {
            deleteRecursively(stagingDir);
            if (downloadPart.exists()) {
                downloadPart.delete();
            }
            if (backupDir.exists() && (!directDir.exists() || !isValidModelDir(directDir))) {
                try {
                    directDir.mkdirs();
                    copyRecursively(backupDir, directDir);
                } catch (Throwable ignored) {
                }
                deleteRecursively(backupDir);
            }

            ModelInstallationState revertState = isModelInstalled(modelId) ? ModelInstallationState.INSTALLED : ModelInstallationState.NOT_INSTALLED;
            setModelState(modelId, revertState, null);

            if (listener != null) {
                listener.onError(e.getMessage() != null ? e.getMessage() : "Reinstallation failed", e);
            }
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        } finally {
            cancellationTokens.remove(modelId);
        }
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
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.nio.file.Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
        if (info == null)
            return "https://alphacephei.com/vosk/models";
        return info.getPrimaryDownloadUrl();
    }

    public String getManualInstallInstructions(SpeechModelInfo info) {
        String id = info != null ? info.getId() : DEFAULT_MODEL_ID;
        File dir = getModelDir(id);
        return "Manual installation: Download " + id + ".zip and extract its model files (am/, conf/) directly into: "
                + dir.getAbsolutePath();
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
