package com.voice2text.speech;

import java.io.Closeable;
import java.io.IOException;

/**
 * Interface abstracting the streaming Speech-to-Text recognition engine.
 */
public interface SpeechRecognitionEngine extends Closeable {

    /**
     * Initializes the engine with the specified local model directory path.
     *
     * @param modelPath filesystem path to the speech model directory
     * @throws IOException if model cannot be loaded
     */
    void initialize(String modelPath) throws IOException;

    /**
     * Starts a recognition session.
     */
    void start();

    /**
     * Streams an incoming audio buffer chunk (16kHz 16-bit signed mono PCM) into the engine.
     *
     * @param audioData raw PCM buffer
     * @param length number of valid bytes in audioData
     */
    void acceptAudio(byte[] audioData, int length);

    /**
     * Stops the active recognition session, flushing any pending audio hypothesis.
     */
    void stop();

    /**
     * Resets the active recognition session without re-loading the model.
     */
    void reset();

    /**
     * Checks if the engine is currently listening and actively processing speech.
     *
     * @return true if running, false otherwise
     */
    boolean isRunning();

    /**
     * Gets the current state of the engine.
     *
     * @return current EngineState
     */
    EngineState getState();

    /**
     * Registers a recognition listener for streaming events.
     *
     * @param listener listener callback
     */
    void setListener(RecognitionListener listener);

    /**
     * Closes the engine and frees all native memory and resources.
     */
    @Override
    void close();
}
