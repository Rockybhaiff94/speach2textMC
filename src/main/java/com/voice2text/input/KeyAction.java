package com.voice2text.input;

/**
 * High-level actions produced by the InputStateMachine.
 */
public enum KeyAction {
    NONE,
    OPEN_NORMAL_CHAT,
    START_DICTATION,
    STOP_AND_SEND,
    STOP_AND_KEEP_DRAFT,
    CANCEL_DICTATION
}
