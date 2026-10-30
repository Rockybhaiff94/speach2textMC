package com.voice2text.model;

/**
 * Metadata descriptor for an offline speech recognition model.
 * Supports primary official download endpoints, verified alternate release mirrors,
 * official MD5 checksums, verified SHA-256 hashes, hardware recommendations,
 * Hinglish compatibility, output script options, and runtime classification.
 */
public class SpeechModelInfo {

    private final String id;
    private final String languageCode;
    private final String displayName;
    private final ModelFamily family;
    private final String category;
    private final String shortDescription;
    private final String supportedLanguages;
    private final String hinglishSupport;
    private final String outputScript;
    private final String hardwareRequirements;
    private final String runtimeCompatibility;
    private final String quantization;
    private final String supportStatus;
    private final String primaryDownloadUrl;
    private final String alternateDownloadUrl;
    private final long downloadSizeBytes;
    private final long uncompressedSizeBytes;
    private final String primarySha256;
    private final String alternateSha256;
    private final String md5Checksum;
    private final String license;

    public SpeechModelInfo(String id,
                           String languageCode,
                           String displayName,
                           ModelFamily family,
                           String category,
                           String shortDescription,
                           String supportedLanguages,
                           String hinglishSupport,
                           String outputScript,
                           String hardwareRequirements,
                           String runtimeCompatibility,
                           String quantization,
                           String supportStatus,
                           String primaryDownloadUrl,
                           String alternateDownloadUrl,
                           long downloadSizeBytes,
                           long uncompressedSizeBytes,
                           String primarySha256,
                           String alternateSha256,
                           String md5Checksum,
                           String license) {
        this.id = id;
        this.languageCode = languageCode;
        this.displayName = displayName;
        this.family = family != null ? family : ModelFamily.VOSK;
        this.category = category != null ? category : "General";
        this.shortDescription = shortDescription != null ? shortDescription : "";
        this.supportedLanguages = supportedLanguages != null ? supportedLanguages : languageCode;
        this.hinglishSupport = hinglishSupport != null ? hinglishSupport : "None";
        this.outputScript = outputScript != null ? outputScript : "Latin";
        this.hardwareRequirements = hardwareRequirements != null ? hardwareRequirements : "500 MB RAM";
        this.runtimeCompatibility = runtimeCompatibility != null ? runtimeCompatibility : "Offline Native";
        this.quantization = quantization != null ? quantization : "Standard";
        this.supportStatus = supportStatus != null ? supportStatus : "Supported";
        this.primaryDownloadUrl = primaryDownloadUrl;
        this.alternateDownloadUrl = alternateDownloadUrl != null ? alternateDownloadUrl : "";
        this.downloadSizeBytes = downloadSizeBytes;
        this.uncompressedSizeBytes = uncompressedSizeBytes;
        this.primarySha256 = primarySha256 != null ? primarySha256.toLowerCase().trim() : "";
        this.alternateSha256 = alternateSha256 != null ? alternateSha256.toLowerCase().trim() : "";
        this.md5Checksum = md5Checksum != null ? md5Checksum.toLowerCase().trim() : "";
        this.license = license != null ? license : "Apache-2.0";
    }

    public SpeechModelInfo(String id,
                           String languageCode,
                           String displayName,
                           String primaryDownloadUrl,
                           String alternateDownloadUrl,
                           long downloadSizeBytes,
                           long uncompressedSizeBytes,
                           String primarySha256,
                           String alternateSha256,
                           String md5Checksum,
                           String license) {
        this(id, languageCode, displayName, ModelFamily.VOSK, "Vosk Offline",
             "Offline Vosk speech model.", languageCode,
             (id.contains("hi") || id.contains("hinglish") || id.contains("in")) ? "Supported" : "None",
             (id.contains("hi") || id.contains("hinglish")) ? "Roman Hindi & Devanagari" : "Latin",
             "500 MB RAM", "Vosk Native JNI (Offline)", "Standard", "Supported",
             primaryDownloadUrl, alternateDownloadUrl, downloadSizeBytes, uncompressedSizeBytes,
             primarySha256, alternateSha256, md5Checksum, license);
    }

    public SpeechModelInfo(String id,
                           String languageCode,
                           String displayName,
                           String primaryDownloadUrl,
                           String alternateDownloadUrl,
                           long downloadSizeBytes,
                           long uncompressedSizeBytes,
                           String primarySha256,
                           String alternateSha256,
                           String license) {
        this(id, languageCode, displayName, primaryDownloadUrl, alternateDownloadUrl, downloadSizeBytes, uncompressedSizeBytes, primarySha256, alternateSha256, "", license);
    }

    public SpeechModelInfo(String id,
                           String languageCode,
                           String displayName,
                           String primaryDownloadUrl,
                           long downloadSizeBytes,
                           long uncompressedSizeBytes,
                           String primarySha256) {
        this(id, languageCode, displayName, primaryDownloadUrl, "", downloadSizeBytes, uncompressedSizeBytes, primarySha256, "", "", "Apache-2.0");
    }

    public String getId() {
        return id;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ModelFamily getFamily() {
        return family;
    }

    public String getCategory() {
        return category;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getSupportedLanguages() {
        return supportedLanguages;
    }

    public String getHinglishSupport() {
        return hinglishSupport;
    }

    public String getOutputScript() {
        return outputScript;
    }

    public String getHardwareRequirements() {
        return hardwareRequirements;
    }

    public String getRuntimeCompatibility() {
        return runtimeCompatibility;
    }

    public String getQuantization() {
        return quantization;
    }

    public String getSupportStatus() {
        return supportStatus;
    }

    public String getPrimaryDownloadUrl() {
        return primaryDownloadUrl;
    }

    public String getAlternateDownloadUrl() {
        return alternateDownloadUrl;
    }

    public boolean hasAlternateUrl() {
        return alternateDownloadUrl != null && !alternateDownloadUrl.trim().isEmpty();
    }

    public long getDownloadSizeBytes() {
        return downloadSizeBytes;
    }

    public long getUncompressedSizeBytes() {
        return uncompressedSizeBytes;
    }

    public String getPrimarySha256() {
        return primarySha256;
    }

    public String getAlternateSha256() {
        return alternateSha256;
    }

    public String getMd5Checksum() {
        return md5Checksum;
    }

    public String getLicense() {
        return license;
    }

    /**
     * Verifies computed SHA-256 and/or MD5 hashes against registered expected values.
     */
    public boolean verifyIntegrity(String computedSha256, String computedMd5, boolean isAlternateSource) {
        if (!isAlternateSource && md5Checksum != null && !md5Checksum.isEmpty() && computedMd5 != null) {
            if (!md5Checksum.equalsIgnoreCase(computedMd5.trim())) {
                return false;
            }
        }

        String expectedSha256 = isAlternateSource ? alternateSha256 : primarySha256;
        if (expectedSha256 == null || expectedSha256.isEmpty()) {
            expectedSha256 = primarySha256.isEmpty() ? alternateSha256 : primarySha256;
        }

        if (expectedSha256 != null && !expectedSha256.isEmpty() && computedSha256 != null) {
            if (!expectedSha256.equalsIgnoreCase(computedSha256.trim())) {
                return false;
            }
        }

        return true;
    }

    public boolean verifyChecksum(String calculatedHash, boolean isAlternateSource) {
        if (calculatedHash == null || calculatedHash.isEmpty()) {
            return false;
        }
        String clean = calculatedHash.trim().toLowerCase();
        if (isAlternateSource) {
            if (!alternateSha256.isEmpty()) {
                return alternateSha256.equalsIgnoreCase(clean);
            }
            return primarySha256.isEmpty() || primarySha256.equalsIgnoreCase(clean);
        } else {
            if (!primarySha256.isEmpty()) {
                return primarySha256.equalsIgnoreCase(clean);
            }
            return alternateSha256.isEmpty() || alternateSha256.equalsIgnoreCase(clean);
        }
    }

    public String getFormattedDownloadSize() {
        if (downloadSizeBytes >= 1024L * 1024L * 1024L) {
            double gb = (double) downloadSizeBytes / (1024.0 * 1024.0 * 1024.0);
            return String.format("%.2f GB", gb);
        }
        double mb = (double) downloadSizeBytes / (1024.0 * 1024.0);
        return String.format("%.1f MB", mb);
    }

    public String getFormattedUncompressedSize() {
        if (uncompressedSizeBytes >= 1024L * 1024L * 1024L) {
            double gb = (double) uncompressedSizeBytes / (1024.0 * 1024.0 * 1024.0);
            return String.format("%.2f GB", gb);
        }
        double mb = (double) uncompressedSizeBytes / (1024.0 * 1024.0);
        return String.format("%.1f MB", mb);
    }

    @Override
    public String toString() {
        return displayName + " (" + languageCode + ") - " + getFormattedDownloadSize();
    }
}
