package com.voice2text.session;

import com.voice2text.input.KeyAction;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates the complete lifecycle of a single voice-to-text recording session.
 *
 * Provides thread-safe state management, unique session identification,
 * immutable policy snapshotting (such as Automatic Send at start of recording),
 * and idempotency guards preventing duplicate submissions, stale transcripts,
 * or delayed callback leaks.
 */
public class RecordingSession {

    public enum State {
        STARTING,
        RECORDING,
        FINALIZING,
        COMPLETED,
        CANCELLED
    }

    private final long id;
    private final boolean autoSendSnapshot;
    private volatile boolean autoSendEffective;
    private final String initialDraft;
    private final long startTimeMillis;

    private volatile State state;
    private final AtomicBoolean finalized = new AtomicBoolean(false);
    private final AtomicBoolean submitted = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean finalResultReceived = new AtomicBoolean(false);
    private final java.util.concurrent.CountDownLatch finalResultLatch = new java.util.concurrent.CountDownLatch(1);
    private volatile boolean speechDetectedEver = false;
    private volatile KeyAction pendingStopAction = KeyAction.NONE;
    private volatile String finalizedText = null;

    public RecordingSession(long id, boolean autoSendSnapshot, String initialDraft) {
        this.id = id;
        this.autoSendSnapshot = autoSendSnapshot;
        this.autoSendEffective = autoSendSnapshot;
        this.initialDraft = initialDraft != null ? initialDraft : "";
        this.startTimeMillis = System.currentTimeMillis();
        this.state = State.STARTING;
    }

    public long getId() {
        return id;
    }

    public boolean isAutoSendSnapshot() {
        return autoSendSnapshot;
    }

    public String getInitialDraft() {
        return initialDraft;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public State getState() {
        return state;
    }

    public synchronized void transitionToRecording() {
        if (state == State.STARTING) {
            this.state = State.RECORDING;
        }
    }

    public synchronized void transitionToCompleted() {
        this.state = State.COMPLETED;
        finalResultLatch.countDown();
    }

    public synchronized void transitionToCancelled() {
        this.state = State.CANCELLED;
        this.finalized.set(true);
        finalResultLatch.countDown();
    }

    public void markFinalResultReceived() {
        finalResultReceived.set(true);
        finalResultLatch.countDown();
    }

    public boolean isFinalResultReceived() {
        return finalResultReceived.get();
    }

    public boolean awaitFinalResult(long timeoutMs) {
        if (finalResultReceived.get()) {
            return true;
        }
        try {
            return finalResultLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void setSpeechDetectedEver(boolean detected) {
        if (detected) {
            this.speechDetectedEver = true;
        }
    }

    public boolean hasSpeechDetectedEver() {
        return speechDetectedEver;
    }

    /**
     * Atomically marks the session as finalizing with an effective auto-send policy.
     * Returns true only for the first caller; subsequent callers receive false,
     * guaranteeing idempotency against multiple stop events.
     */
    public boolean markFinalizing(boolean autoSend) {
        if (finalized.compareAndSet(false, true)) {
            this.state = State.FINALIZING;
            this.autoSendEffective = autoSend;
            return true;
        }
        return false;
    }

    public boolean markFinalizing() {
        return markFinalizing(this.autoSendSnapshot);
    }

    public boolean isAutoSendEffective() {
        return autoSendEffective;
    }

    /**
     * Atomically marks the session as submitted.
     * Returns true only once, guaranteeing that chat messages are submitted exactly once.
     */
    public boolean markSubmitted() {
        return submitted.compareAndSet(false, true);
    }

    public boolean isSubmitted() {
        return submitted.get();
    }

    public boolean isFinalized() {
        return finalized.get();
    }

    public boolean isFinished() {
        return state == State.COMPLETED || state == State.CANCELLED;
    }

    public void requestStop(KeyAction action) {
        this.pendingStopAction = action;
        this.stopRequested.set(true);
    }

    public boolean isStopRequested() {
        return stopRequested.get();
    }

    public KeyAction getPendingStopAction() {
        return pendingStopAction;
    }

    public String getFinalizedText() {
        return finalizedText;
    }

    public void setFinalizedText(String finalizedText) {
        this.finalizedText = finalizedText;
    }

    @Override
    public String toString() {
        return "RecordingSession[id=" + id + ", state=" + state + ", autoSend=" + autoSendSnapshot + "]";
    }
}
