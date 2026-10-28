package com.voice2text.speech;

/**
 * Lifecycle states of a SpeechRecognitionEngine.
 */
public enum EngineState {
    UNINITIALIZED,
    READY,
    LISTENING,
    FINALIZING,
    ERROR,
    CLOSED
}
