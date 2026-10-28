package com.voice2text.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InputStateMachineTest {

    private InputStateMachine stateMachine;
    private List<KeyAction> dispatchedActions;

    @BeforeEach
    public void setUp() {
        stateMachine = new InputStateMachine(true);
        dispatchedActions = new ArrayList<>();
        stateMachine.setActionListener(dispatchedActions::add);
    }

    @Test
    public void testHoldToActivateBelowThresholdDoesNotStart() {
        long t0 = 1000;
        assertFalse(stateMachine.isDictationActive());

        // Press V
        KeyAction downAction = stateMachine.onHoldToTalkDown(t0);
        assertEquals(KeyAction.NONE, downAction);
        assertEquals(InputStateMachine.State.WAITING_FOR_HOLD, stateMachine.getState());
        assertTrue(stateMachine.isWaitingForHold());
        assertFalse(stateMachine.isDictationActive());

        // Release V 200ms later (less than 500ms threshold)
        KeyAction upAction = stateMachine.onHoldToTalkUp(t0 + 200);
        assertEquals(KeyAction.NONE, upAction);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertFalse(stateMachine.isWaitingForHold());
        assertFalse(stateMachine.isDictationActive());
        assertEquals(0, dispatchedActions.size(), "No recording session must start when released before 500ms");
    }

    @Test
    public void testHoldToActivateAtOrAboveThresholdStartsDictation() {
        long t0 = 1000;

        // Press V down
        stateMachine.onHoldToTalkDown(t0);
        assertEquals(InputStateMachine.State.WAITING_FOR_HOLD, stateMachine.getState());

        // 250ms later: still waiting, no action
        KeyAction earlyCheck = stateMachine.checkHoldThreshold(t0 + 250);
        assertEquals(KeyAction.NONE, earlyCheck);
        assertEquals(InputStateMachine.State.WAITING_FOR_HOLD, stateMachine.getState());

        // 500ms reached: activation starts immediately
        KeyAction startAction = stateMachine.checkHoldThreshold(t0 + 500);
        assertEquals(KeyAction.START_DICTATION, startAction);
        assertEquals(InputStateMachine.State.RECORDING, stateMachine.getState());
        assertTrue(stateMachine.isDictationActive());
        assertTrue(stateMachine.isHoldToTalkRecording());
        assertEquals(1, dispatchedActions.size());
        assertEquals(KeyAction.START_DICTATION, dispatchedActions.get(0));

        // Release V: stops recording and sends message when Automatic Send is enabled
        KeyAction upAction = stateMachine.onHoldToTalkUp(t0 + 2500);
        assertEquals(KeyAction.STOP_AND_SEND, upAction);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertFalse(stateMachine.isDictationActive());
        assertFalse(stateMachine.isHoldToTalkActive());
        assertEquals(2, dispatchedActions.size());
        assertEquals(KeyAction.STOP_AND_SEND, dispatchedActions.get(1));
    }

    @Test
    public void testHoldToActivateReleaseWithAutoSendDisabledKeepsDraft() {
        stateMachine.setAutomaticSendEnabled(false);

        // Press and reach threshold
        stateMachine.onHoldToTalkDown(1000);
        stateMachine.checkHoldThreshold(1500);
        assertTrue(stateMachine.isDictationActive());
        dispatchedActions.clear();

        // Release V with auto-send OFF -> keeps draft
        KeyAction upAction = stateMachine.onHoldToTalkUp(3000);
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, upAction);
        assertFalse(stateMachine.isDictationActive());
        assertEquals(1, dispatchedActions.size());
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, dispatchedActions.get(0));
    }

    @Test
    public void testHoldToActivateRepeatEventsIgnored() {
        stateMachine.onHoldToTalkDown(1000);
        // Repeated key down events from held key
        KeyAction repeat1 = stateMachine.onHoldToTalkDown(1050);
        KeyAction repeat2 = stateMachine.onHoldToTalkDown(1100);
        assertEquals(KeyAction.NONE, repeat1);
        assertEquals(KeyAction.NONE, repeat2);
        assertEquals(InputStateMachine.State.WAITING_FOR_HOLD, stateMachine.getState());
    }

    @Test
    public void testChatOnlyToggleDoesNotStartWhenChatClosed() {
        // Chat closed: activation key (T) must NOT start recording
        KeyAction action = stateMachine.onActivationKey(false, 1000);
        assertEquals(KeyAction.NONE, action);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertFalse(stateMachine.isDictationActive());
        assertEquals(0, dispatchedActions.size());
    }

    @Test
    public void testChatOnlyToggleStartsAndStopsWhenChatOpen() {
        // Chat open: first press starts dictation immediately
        KeyAction startAction = stateMachine.onActivationKey(true, 1000);
        assertEquals(KeyAction.START_DICTATION, startAction);
        assertEquals(InputStateMachine.State.RECORDING, stateMachine.getState());
        assertTrue(stateMachine.isDictationActive());
        assertTrue(stateMachine.isChatToggleRecording());
        assertEquals(1, dispatchedActions.size());
        assertEquals(KeyAction.START_DICTATION, dispatchedActions.get(0));

        // Second press: stops dictation immediately
        KeyAction stopAction = stateMachine.onActivationKey(true, 3000);
        assertEquals(KeyAction.STOP_AND_SEND, stopAction);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertFalse(stateMachine.isDictationActive());
        assertEquals(2, dispatchedActions.size());
        assertEquals(KeyAction.STOP_AND_SEND, dispatchedActions.get(1));
    }

    @Test
    public void testChatOnlyToggleKeepsDraftWhenAutoSendDisabled() {
        stateMachine.setAutomaticSendEnabled(false);

        // Start
        stateMachine.onActivationKey(true, 1000);
        assertTrue(stateMachine.isDictationActive());
        dispatchedActions.clear();

        // Stop with auto-send OFF -> keeps draft
        KeyAction stopAction = stateMachine.onActivationKey(true, 3000);
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, stopAction);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertEquals(1, dispatchedActions.size());
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, dispatchedActions.get(0));
    }

    @Test
    public void testChatToggleKeyRepeatDoesNotStopPrematurely() {
        // First press starts
        stateMachine.onActivationKey(true, 1000);
        assertTrue(stateMachine.isDictationActive());

        // Key repeat 50ms later -> must be ignored!
        KeyAction repeatAction = stateMachine.onActivationKey(true, 1050);
        assertEquals(KeyAction.NONE, repeatAction);
        assertTrue(stateMachine.isDictationActive());
    }

    @Test
    public void testStopKeyDuringRecordingStopsAndKeepsDraftWhenAutoSendOff() {
        stateMachine.setAutomaticSendEnabled(false);
        stateMachine.onActivationKey(true, 1000);
        assertTrue(stateMachine.isDictationActive());
        dispatchedActions.clear();

        boolean intercepted = stateMachine.onStopKey(3000);
        assertTrue(intercepted);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertFalse(stateMachine.isDictationActive());
        assertEquals(1, dispatchedActions.size());
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, dispatchedActions.get(0));
    }

    @Test
    public void testStopKeyDuringRecordingAppliesAutoSendWhenEnabled() {
        stateMachine.setAutomaticSendEnabled(true);
        stateMachine.onActivationKey(true, 1000);
        assertTrue(stateMachine.isDictationActive());
        dispatchedActions.clear();

        boolean intercepted = stateMachine.onStopKey(3000);
        assertTrue(intercepted);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertEquals(KeyAction.STOP_AND_SEND, dispatchedActions.get(0));
    }

    @Test
    public void testEscapeKeyCancelsPendingHoldAndActiveDictation() {
        // 1. Cancel while waiting for 500ms hold
        stateMachine.onHoldToTalkDown(1000);
        assertTrue(stateMachine.isWaitingForHold());
        boolean cancelledEarly = stateMachine.onKeyEscape(1200);
        assertTrue(cancelledEarly);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());

        // 2. Cancel while actively recording
        stateMachine.onActivationKey(true, 2000);
        assertTrue(stateMachine.isDictationActive());
        dispatchedActions.clear();

        boolean cancelledActive = stateMachine.onKeyEscape(3000);
        assertTrue(cancelledActive);
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertFalse(stateMachine.isDictationActive());
        assertEquals(1, dispatchedActions.size());
        assertEquals(KeyAction.CANCEL_DICTATION, dispatchedActions.get(0));
    }

    @Test
    public void testInterruptionResetsStateSafely() {
        // Start Mode A hold
        stateMachine.onHoldToTalkDown(1000);
        stateMachine.checkHoldThreshold(1500);
        assertTrue(stateMachine.isDictationActive());
        dispatchedActions.clear();

        // Screen changed or focus lost
        stateMachine.onInterrupted();
        assertFalse(stateMachine.isDictationActive());
        assertFalse(stateMachine.isHoldToTalkActive());
        assertEquals(InputStateMachine.State.IDLE, stateMachine.getState());
        assertEquals(1, dispatchedActions.size());
        assertEquals(KeyAction.STOP_AND_KEEP_DRAFT, dispatchedActions.get(0));
    }
}
