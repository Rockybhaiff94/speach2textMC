package com.voice2text.speech;

/**
 * Callback listener for streaming speech recognition events.
 */
public interface RecognitionListener {

    /**
     * Called when an in-progress partial transcription hypothesis is produced.
     *
     * @param result the partial recognition result
     */
    void onPartialResult(RecognitionResult result);

    /**
     * Called when a segment of speech has been finalized.
     *
     * @param result the finalized recognition result
     */
    void onFinalResult(RecognitionResult result);

    /**
     * Called when an error occurs during inference or initialization.
     *
     * @param errorMessage descriptive error message
     * @param throwable cause exception, or null
     */
    void onError(String errorMessage, Throwable throwable);

    /**
     * Called when the speech engine transitions between states.
     *
     * @param oldState previous engine state
     * @param newState current engine state
     */
    void onStateChanged(EngineState oldState, EngineState newState);
}
