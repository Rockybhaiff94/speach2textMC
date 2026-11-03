package com.voice2text.model;

/**
 * State machine representing the stages of speech model installation and readiness.
 */
public enum ModelInstallationState {
    NOT_INSTALLED("Not Installed", "§c"),
    CHECKING("Checking...", "§e"),
    READY_TO_DOWNLOAD("Ready to Download", "§6"),
    DOWNLOADING("Downloading...", "§e"),
    VERIFYING("Verifying...", "§e"),
    EXTRACTING("Extracting...", "§e"),
    INITIALIZING("Initializing...", "§e"),
    INSTALLED("Installed", "§a"),
    REINSTALLING("Reinstalling...", "§e"),
    DELETING("Deleting...", "§c"),
    DELETION_FAILED("Deletion Failed", "§c"),
    DOWNLOAD_CANCELLED("Download Cancelled", "§7"),
    DOWNLOAD_FAILED("Download Failed", "§c"),
    COMPATIBILITY_ERROR("Compatibility Error", "§c"),
    ERROR("Error", "§c"),
    CANCELLING("Cancelling...", "§7");

    private final String displayLabel;
    private final String colorCode;

    ModelInstallationState(String displayLabel, String colorCode) {
        this.displayLabel = displayLabel;
        this.colorCode = colorCode;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getColoredLabel() {
        return colorCode + displayLabel;
    }

    public boolean isBusy() {
        return this == CHECKING || this == DOWNLOADING || this == VERIFYING 
                || this == EXTRACTING || this == INITIALIZING || this == CANCELLING
                || this == REINSTALLING || this == DELETING;
    }

    public boolean isReady() {
        return this == INSTALLED;
    }
}
