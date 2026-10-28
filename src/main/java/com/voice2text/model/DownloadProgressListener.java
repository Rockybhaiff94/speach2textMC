package com.voice2text.model;

/**
 * Callback listener for tracking model download and extraction progress.
 */
public interface DownloadProgressListener {

    /**
     * Fired when the installation state machine transitions to a new state.
     */
    default void onStateChanged(ModelInstallationState oldState, ModelInstallationState newState) {}

    /**
     * Fired when download or extraction percentage updates.
     *
     * @param bytesProcessed bytes processed so far
     * @param totalBytes total expected bytes, or -1 if unknown
     * @param percent percentage 0 to 100, or -1 if indeterminate
     */
    void onProgress(long bytesProcessed, long totalBytes, int percent);

    /**
     * Fired when status message changes (e.g. "Connecting...", "Downloading...", "Verifying checksum...", "Extracting...").
     *
     * @param statusMessage user-friendly status description
     */
    void onStatus(String statusMessage);

    /**
     * Fired on successful completion.
     */
    void onCompleted();

    /**
     * Fired if download, verification, or extraction fails.
     *
     * @param errorMessage descriptive error message
     * @param throwable cause, or null
     */
    void onError(String errorMessage, Throwable throwable);
}
