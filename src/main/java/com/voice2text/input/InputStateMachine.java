package com.voice2text.input;

import java.util.function.Consumer;

/**
 * State machine managing SpeakCraft recording sessions:
 * - Mode A: V Hold-to-Activate during gameplay (500ms threshold, continuous hold while speaking, release stops).
 * - Mode B: Chat-Only Toggle Recording (only in Minecraft chat, 1st press starts, 2nd press stops).
 * - Stop & Draft Key ('P'), Escape safe cancellation, and focus-interruption recovery.
 */
public class InputStateMachine {

    public enum State {
        IDLE,
        WAITING_FOR_HOLD,
        RECORDING,
        FINALIZING
    }

    public enum SessionMode {
        NONE,
        MODE_A_HOLD,
        MODE_B_CHAT_TOGGLE
    }

    public static final long HOLD_THRESHOLD_MS = 500L;

    private State state = State.IDLE;
    private SessionMode sessionMode = SessionMode.NONE;
    private boolean automaticSendEnabled = false; // default to safe keep draft

    private long holdPressStartTime = 0L;
    private long lastActivationPressTime = 0L;

    private Consumer<KeyAction> actionListener;

    public InputStateMachine(boolean automaticSendEnabled) {
        this.automaticSendEnabled = automaticSendEnabled;
    }

    public InputStateMachine() {
        this(false);
    }

    /**
     * Backward-compatible constructor ignoring obsolete doublePressWindowMs parameter.
     */
    public InputStateMachine(int doublePressWindowMs, boolean automaticSendEnabled) {
        this(automaticSendEnabled);
    }

    /**
     * Handles Hold-to-Activate key press (key down in gameplay).
     * Begins the 500ms activation threshold. Does not start recording immediately.
     */
    public synchronized KeyAction onHoldToTalkDown(long now) {
        if (state == State.IDLE) {
            state = State.WAITING_FOR_HOLD;
            sessionMode = SessionMode.MODE_A_HOLD;
            holdPressStartTime = now;
            return KeyAction.NONE;
        }
        // If already waiting or recording, ignore repeat key-down events
        return KeyAction.NONE;
    }

    /**
     * Checks if the 500ms activation threshold has been reached while V is held.
     * Transitions from WAITING_FOR_HOLD to RECORDING.
     */
    public synchronized KeyAction checkHoldThreshold(long now) {
        if (state == State.WAITING_FOR_HOLD && sessionMode == SessionMode.MODE_A_HOLD) {
            if (now - holdPressStartTime >= HOLD_THRESHOLD_MS) {
                state = State.RECORDING;
                notifyAction(KeyAction.START_DICTATION);
                return KeyAction.START_DICTATION;
            }
        }
        return KeyAction.NONE;
    }

    /**
     * Handles Hold-to-Activate key release (key up).
     * If released before 500ms: cancels pending activation, does not start recording.
     * If released while recording: stops recording and finalizes transcription.
     */
    public synchronized KeyAction onHoldToTalkUp(long now) {
        if (state == State.WAITING_FOR_HOLD) {
            // Released before 500ms threshold -> cancel activation cleanly
            state = State.IDLE;
            sessionMode = SessionMode.NONE;
            holdPressStartTime = 0L;
            return KeyAction.NONE;
        }

        if (state == State.RECORDING && sessionMode == SessionMode.MODE_A_HOLD) {
            state = State.FINALIZING;
            KeyAction action = automaticSendEnabled ? KeyAction.STOP_AND_SEND : KeyAction.STOP_AND_KEEP_DRAFT;
            notifyAction(action);
            state = State.IDLE;
            sessionMode = SessionMode.NONE;
            holdPressStartTime = 0L;
            return action;
        }

        return KeyAction.NONE;
    }

    /**
     * Handles Mode B Chat-Only Toggle Recording.
     * Only activates when Minecraft chat is open.
     * Single press starts immediately, single subsequent press stops and finalizes.
     */
    public synchronized KeyAction onChatToggleKey(long now) {
        if (state == State.IDLE) {
            state = State.RECORDING;
            sessionMode = SessionMode.MODE_B_CHAT_TOGGLE;
            lastActivationPressTime = now;
            notifyAction(KeyAction.START_DICTATION);
            return KeyAction.START_DICTATION;
        } else if (state == State.RECORDING && sessionMode == SessionMode.MODE_B_CHAT_TOGGLE) {
            if (now - lastActivationPressTime < 150) {
                // Ignore OS key-repeat events while the key is held
                return KeyAction.NONE;
            }
            state = State.FINALIZING;
            KeyAction action = automaticSendEnabled ? KeyAction.STOP_AND_SEND : KeyAction.STOP_AND_KEEP_DRAFT;
            notifyAction(action);
            state = State.IDLE;
            sessionMode = SessionMode.NONE;
            return action;
        }
        return KeyAction.NONE;
    }

    /**
     * Activation key dispatcher. Mode B only activates if chat is open.
     * If chat is closed, returns NONE so Minecraft can process normal chat opening.
     */
    public synchronized KeyAction onActivationKey(boolean isChatOpen, boolean hasConflict, long now) {
        if (isChatOpen) {
            return onChatToggleKey(now);
        }
        return KeyAction.NONE;
    }

    public synchronized KeyAction onActivationKey(boolean isChatOpen, long now) {
        return onActivationKey(isChatOpen, false, now);
    }

    public synchronized KeyAction onKeyT(boolean isChatOpen, long now) {
        return onActivationKey(isChatOpen, false, now);
    }

    /**
     * Handles unexpected interruption (focus lost, screen change, disconnect).
     */
    public synchronized void onInterrupted() {
        if (state == State.WAITING_FOR_HOLD) {
            state = State.IDLE;
            sessionMode = SessionMode.NONE;
            holdPressStartTime = 0L;
        } else if (state == State.RECORDING) {
            state = State.FINALIZING;
            notifyAction(KeyAction.STOP_AND_KEEP_DRAFT);
            state = State.IDLE;
            sessionMode = SessionMode.NONE;
            holdPressStartTime = 0L;
        }
    }

    /**
     * Handles a stop key press event (e.g. 'P' or custom stop key).
     * Stops dictation and applies automatic send setting in either recording mode.
     */
    public synchronized boolean onStopKey(long now) {
        if (state == State.RECORDING) {
            state = State.FINALIZING;
            KeyAction action = automaticSendEnabled ? KeyAction.STOP_AND_SEND : KeyAction.STOP_AND_KEEP_DRAFT;
            notifyAction(action);
            state = State.IDLE;
            sessionMode = SessionMode.NONE;
            holdPressStartTime = 0L;
            return true;
        }
        return false;
    }

    public synchronized boolean onKeyP(long now) {
        return onStopKey(now);
    }

    /**
     * Handles an 'Escape' key press event.
     * Safely cancels any pending or active recording without sending.
     */
    public synchronized boolean onKeyEscape(long now) {
        if (state == State.WAITING_FOR_HOLD) {
            state = State.IDLE;
            sessionMode = SessionMode.NONE;
            holdPressStartTime = 0L;
            return true;
        } else if (state == State.RECORDING) {
            state = State.FINALIZING;
            notifyAction(KeyAction.CANCEL_DICTATION);
            state = State.IDLE;
            sessionMode = SessionMode.NONE;
            holdPressStartTime = 0L;
            return true;
        }
        return false;
    }

    /**
     * Dedicated hotkey handler.
     */
    public synchronized KeyAction onDedicatedHotkey(long now) {
        return onActivationKey(false, false, now);
    }

    /**
     * Backward-compatible tick/timeout check. Evaluates the 500ms hold threshold.
     */
    public synchronized KeyAction checkTimeout(long now) {
        return checkHoldThreshold(now);
    }

    public synchronized void tick(long now) {
        checkHoldThreshold(now);
    }

    private void notifyAction(KeyAction action) {
        if (actionListener != null && action != KeyAction.NONE) {
            try {
                actionListener.accept(action);
            } catch (Exception ignored) {
            }
        }
    }

    public synchronized void reset() {
        this.state = State.IDLE;
        this.sessionMode = SessionMode.NONE;
        this.holdPressStartTime = 0L;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized SessionMode getSessionMode() {
        return sessionMode;
    }

    public synchronized boolean isDictationActive() {
        return state == State.RECORDING;
    }

    public synchronized boolean isWaitingForHold() {
        return state == State.WAITING_FOR_HOLD;
    }

    public synchronized boolean isHoldToTalkActive() {
        return sessionMode == SessionMode.MODE_A_HOLD && (state == State.WAITING_FOR_HOLD || state == State.RECORDING);
    }

    public synchronized boolean isHoldToTalkRecording() {
        return sessionMode == SessionMode.MODE_A_HOLD && state == State.RECORDING;
    }

    public synchronized boolean isChatToggleRecording() {
        return sessionMode == SessionMode.MODE_B_CHAT_TOGGLE && state == State.RECORDING;
    }

    public void setStopAndSendEnabled(boolean stopAndSendEnabled) {
        this.automaticSendEnabled = stopAndSendEnabled;
    }

    public boolean isStopAndSendEnabled() {
        return automaticSendEnabled;
    }

    public void setAutomaticSendEnabled(boolean automaticSendEnabled) {
        this.automaticSendEnabled = automaticSendEnabled;
    }

    public boolean isAutomaticSendEnabled() {
        return automaticSendEnabled;
    }

    public void setActionListener(Consumer<KeyAction> actionListener) {
        this.actionListener = actionListener;
    }
}
