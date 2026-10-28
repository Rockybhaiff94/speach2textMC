package com.voice2text.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class InputStateMachineConflictAndHoldTest {

    private InputStateMachine sm;
    private AtomicReference<KeyAction> lastAction;

    @BeforeEach
    public void setUp() {
        sm = new InputStateMachine(false);
        lastAction = new AtomicReference<>(KeyAction.NONE);
        sm.setActionListener(lastAction::set);
    }

    @Test
    public void testModeBChatOnlyToggleStartsAndStopsInChat() {
        long t = 1000;
        assertFalse(sm.isDictationActive());

        // Chat closed: activation key does NOT start recording
        KeyAction closedAction = sm.onActivationKey(false, false, t);
        assertEquals(KeyAction.NONE, closedAction);
        assertFalse(sm.isDictationActive());

        // Chat open: Press 1 starts dictation immediately! No double-tap, no delay.
        KeyAction a1 = sm.onActivationKey(true, false, t + 100);
        assertEquals(KeyAction.START_DICTATION, a1);
        assertTrue(sm.isDictationActive());
        assertEquals(KeyAction.START_DICTATION, lastAction.get());

        // Press 2 in chat: stops dictation immediately and leaves draft (auto-send is false)
        KeyAction a2 = sm.onActivationKey(true, false, t + 600);
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, a2);
        assertFalse(sm.isDictationActive());
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, lastAction.get());
    }

    @Test
    public void testHoldToTalk500msThresholdAndRelease() {
        long t = 1000;
        assertFalse(sm.isDictationActive());
        assertFalse(sm.isHoldToTalkActive());

        // Key Down: begins 500ms threshold
        KeyAction downAction = sm.onHoldToTalkDown(t);
        assertEquals(KeyAction.NONE, downAction);
        assertTrue(sm.isWaitingForHold());
        assertTrue(sm.isHoldToTalkActive());

        // Key Repeat ignored while waiting
        KeyAction repeatAction = sm.onHoldToTalkDown(t + 50);
        assertEquals(KeyAction.NONE, repeatAction);

        // 500ms reached: starts dictation
        KeyAction startAction = sm.checkHoldThreshold(t + 500);
        assertEquals(KeyAction.START_DICTATION, startAction);
        assertTrue(sm.isDictationActive());
        assertTrue(sm.isHoldToTalkRecording());

        // Key Up stops dictation and preserves draft
        KeyAction upAction = sm.onHoldToTalkUp(t + 2000);
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, upAction);
        assertFalse(sm.isDictationActive());
        assertFalse(sm.isHoldToTalkActive());
    }

    @Test
    public void testHoldToTalkEarlyReleaseCancelsCleanly() {
        long t = 1000;
        sm.onHoldToTalkDown(t);
        assertTrue(sm.isWaitingForHold());

        // Release at 200ms (before 500ms threshold)
        KeyAction upAction = sm.onHoldToTalkUp(t + 200);
        assertEquals(KeyAction.NONE, upAction);
        assertFalse(sm.isWaitingForHold());
        assertFalse(sm.isDictationActive());
        assertEquals(KeyAction.NONE, lastAction.get());
    }

    @Test
    public void testHoldToTalkInterruptionPreventsStuckRecording() {
        long t = 1000;
        sm.onHoldToTalkDown(t);
        sm.checkHoldThreshold(t + 500);
        assertTrue(sm.isHoldToTalkActive());
        assertTrue(sm.isDictationActive());

        // Focus lost or screen changed
        sm.onInterrupted();
        assertFalse(sm.isHoldToTalkActive());
        assertFalse(sm.isDictationActive());
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, lastAction.get());
    }

    @Test
    public void testStopKeyPAlwaysStopsAndPreservesDraft() {
        long t = 1000;
        sm.onHoldToTalkDown(t);
        sm.checkHoldThreshold(t + 500);
        assertTrue(sm.isDictationActive());

        // Press P
        boolean intercepted = sm.onStopKey(t + 1000);
        assertTrue(intercepted);
        assertFalse(sm.isDictationActive());
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, lastAction.get());
    }
}
