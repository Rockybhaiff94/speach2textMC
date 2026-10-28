package com.voice2text;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.audio.AudioSource;
import com.voice2text.compat.MinecraftChatBridge;
import com.voice2text.config.ConfigManager;
import com.voice2text.input.KeyAction;
import com.voice2text.model.ModelManager;
import com.voice2text.speech.mock.MockSpeechEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VoiceToTextControllerTest {

    private VoiceToTextController controller;
    private MockSpeechEngine mockEngine;
    private MockAudioSource mockAudio;
    private MockChatBridge mockChat;
    private ConfigManager configManager;
    private ModelManager modelManager;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        File configFile = tempDir.resolve("config.json").toFile();
        File modelsDir = tempDir.resolve("models").toFile();
        modelsDir.mkdirs();

        // Create dummy installed model directory
        File defaultModelDir = new File(modelsDir, "vosk-model-small-en-us-0.15/conf");
        defaultModelDir.mkdirs();
        try {
            new File(defaultModelDir, "model.conf").createNewFile();
        } catch (IOException ignored) {
        }

        configManager = new ConfigManager(configFile);
        modelManager = new ModelManager(modelsDir);
        mockEngine = new MockSpeechEngine();
        mockAudio = new MockAudioSource();
        mockChat = new MockChatBridge();

        controller = new VoiceToTextController(
                configManager,
                modelManager,
                mockChat,
                Runnable::run,
                mockEngine,
                mockAudio
        );
    }

    @Test
    public void testFullDictationFlowStartStreamAndSend() {
        assertFalse(controller.isDictationActive());

        // 1. Start dictation
        controller.handleKeyAction(KeyAction.START_DICTATION);

        assertTrue(controller.isDictationActive());
        assertFalse(mockChat.isChatOpen(), "Chat screen should not open when recording starts, preserving mouse capture and gameplay movement");
        assertTrue(mockAudio.isOpen(), "Audio recording should be active");
        assertTrue(mockEngine.isRunning(), "Speech engine should be listening");

        // 2. Speech arrives: live streaming preview in transcription buffer
        mockEngine.simulatePartial("hello");
        assertEquals("Hello", controller.getTranscriptionBuffer().getDisplayText());

        mockEngine.simulatePartial("hello everyone");
        assertEquals("Hello everyone", controller.getTranscriptionBuffer().getDisplayText());

        mockEngine.simulateFinal("hello everyone");
        assertEquals("Hello everyone", controller.getTranscriptionBuffer().getDisplayText());

        // 3. Stop recording in Automatic Send mode: submits completed message once
        controller.handleKeyAction(KeyAction.STOP_AND_SEND);

        assertFalse(controller.isDictationActive());
        assertFalse(mockAudio.isOpen(), "Audio source should be closed on stop");
        assertEquals(1, mockChat.sentMessages.size(), "Message should be sent once when Automatic Send is enabled");
        assertEquals("Hello everyone", mockChat.sentMessages.get(0));
    }

    @Test
    public void testStopAndKeepDraftEmptySpeechDoesNotOpenChat() {
        controller.handleKeyAction(KeyAction.START_DICTATION);
        assertTrue(controller.isDictationActive());
        assertFalse(mockChat.isChatOpen(), "Chat should remain closed during recording");

        // Stop with empty transcript (player remained silent)
        controller.handleKeyAction(KeyAction.STOP_AND_KEEP_DRAFT);

        assertFalse(controller.isDictationActive());
        assertFalse(mockAudio.isOpen());
        assertFalse(mockChat.isChatOpen(), "Chat screen must NOT open if no speech was recognized");
    }

    @Test
    public void testStopAndSendEmptySpeechDoesNotSend() {
        controller.handleKeyAction(KeyAction.START_DICTATION);
        assertTrue(controller.isDictationActive());

        // Stop with empty transcript
        controller.handleKeyAction(KeyAction.STOP_AND_SEND);

        assertFalse(controller.isDictationActive());
        assertFalse(mockAudio.isOpen());
        assertEquals(0, mockChat.sentMessages.size(), "Empty transcription must NEVER be sent automatically");
    }

    @Test
    public void testStopAndKeepDraftWhenAutomaticSendOff() {
        controller.handleKeyAction(KeyAction.START_DICTATION);
        assertTrue(controller.isDictationActive());

        mockEngine.simulatePartial("draft message");
        mockEngine.simulateFinal("draft message");

        // When Automatic Send is OFF, STOP_AND_KEEP_DRAFT is triggered
        controller.handleKeyAction(KeyAction.STOP_AND_KEEP_DRAFT);

        assertFalse(controller.isDictationActive());
        assertFalse(mockAudio.isOpen());
        assertEquals(0, mockChat.sentMessages.size(), "Message must NOT be sent automatically when Automatic Send is OFF");
        assertTrue(mockChat.isChatOpen(), "Chat screen must open with draft for player editing");
        assertEquals("Draft message", mockChat.getChatInput());
    }

    @Test
    public void testCancelWithEscapePreservesDraftAndStopsMic() {
        mockChat.updateChatInput("Draft: ");
        controller.handleKeyAction(KeyAction.START_DICTATION);

        mockEngine.simulatePartial("temporary speech");

        // Escape pressed
        controller.handleKeyAction(KeyAction.CANCEL_DICTATION);

        assertFalse(controller.isDictationActive());
        assertFalse(mockAudio.isOpen(), "Microphone must stop immediately on cancel");
        assertEquals(0, mockChat.sentMessages.size());
    }

    @Test
    public void testAudioPipelineStreamsDataToEngine() throws Exception {
        controller.handleKeyAction(KeyAction.START_DICTATION);

        // Feed some bytes to mock audio
        byte[] testPcm = new byte[512];
        testPcm[0] = 12;
        testPcm[1] = 34;
        mockAudio.feedAudio(testPcm);

        // Wait brief tick
        Thread.sleep(100);

        byte[] received = mockEngine.getReceivedAudio();
        assertTrue(received.length > 0, "Engine should receive audio bytes from capture service");
    }

    @Test
    public void testKeybindManagerIntegration() {
        assertNotNull(controller.getKeybindManager());
        assertEquals("T", controller.getKeybindManager().getActivationKeyName());
        assertEquals(InputConstants.KEY_T, controller.getKeybindManager().getActivationKeyCode());

        // Update keybind
        controller.getKeybindManager().setActivationKey(InputConstants.KEY_G, "G");
        assertEquals("G", controller.getKeybindManager().getActivationKeyName());
        assertEquals(InputConstants.KEY_G, controller.getKeybindManager().getActivationKeyCode());
        assertTrue(controller.getKeybindManager().matchesActivationKey(InputConstants.KEY_G));
        assertFalse(controller.getKeybindManager().matchesActivationKey(InputConstants.KEY_T));
    }

    @Test
    public void testModelSwitching() throws IOException {
        File modelsDir = modelManager.getModelsBaseDir();
        File deModelDir = new File(modelsDir, "vosk-model-small-de-0.15/conf");
        deModelDir.mkdirs();
        new File(deModelDir, "model.conf").createNewFile();

        assertTrue(modelManager.isModelInstalled("vosk-model-small-de-0.15"));

        boolean switched = controller.switchModel("vosk-model-small-de-0.15");
        assertTrue(switched);
        assertEquals("vosk-model-small-de-0.15", controller.getLoadedModelId());

        // Switching to non-existent model fails
        boolean failedSwitch = controller.switchModel("non-existent-model");
        assertFalse(failedSwitch);
    }

    @Test
    public void testRecordingSessionLifecycleAndUniqueId() {
        controller.handleKeyAction(KeyAction.START_DICTATION);
        assertTrue(controller.isDictationActive());
        assertNotNull(controller.getCurrentSession());
        long firstId = controller.getCurrentSession().getId();
        assertEquals(com.voice2text.session.RecordingSession.State.RECORDING, controller.getCurrentSession().getState());

        controller.handleKeyAction(KeyAction.STOP_AND_KEEP_DRAFT);
        assertFalse(controller.isDictationActive());
        assertEquals(com.voice2text.session.RecordingSession.State.COMPLETED, controller.getCurrentSession().getState());

        // Start second session
        controller.handleKeyAction(KeyAction.START_DICTATION);
        assertTrue(controller.isDictationActive());
        long secondId = controller.getCurrentSession().getId();
        assertTrue(secondId > firstId, "New session must receive monotonically increasing unique ID");
        controller.handleKeyAction(KeyAction.STOP_AND_KEEP_DRAFT);
    }

    @Test
    public void testMultipleStopAndSendIsIdempotent() {
        controller.handleKeyAction(KeyAction.START_DICTATION);
        mockEngine.simulateFinal("Diamond sword in chest");

        // Call stopAndSend 3 times concurrently / consecutively
        controller.stopAndSend();
        controller.stopAndSend();
        controller.stopAndSend();

        assertEquals(1, mockChat.sentMessages.size(), "Message must be submitted exactly once despite multiple stop events");
        assertEquals("Diamond sword in chest", mockChat.sentMessages.get(0));
    }

    @Test
    public void testSnapshotRespectsSettingAtRecordingStart() {
        // 1. Start recording with Automatic Send = false (default)
        configManager.getConfig().setAutomaticSend(false);
        controller.handleKeyAction(KeyAction.START_DICTATION);
        assertFalse(controller.getCurrentSession().isAutoSendSnapshot());

        mockEngine.simulateFinal("testing snapshot");

        // 2. User toggles setting to true during active recording
        configManager.getConfig().setAutomaticSend(true);

        // 3. Stop recording: must use the snapshot from session start (false)
        controller.stopDictation();

        assertEquals(0, mockChat.sentMessages.size(), "Recording that started with auto-send OFF must NOT auto-send even if toggled mid-flight");
        assertTrue(mockChat.isChatOpen());
        assertEquals("Testing snapshot", mockChat.getChatInput());
    }

    @Test
    public void testDelayedCallbacksFromOldSessionIgnored() {
        controller.handleKeyAction(KeyAction.START_DICTATION);
        mockEngine.simulateFinal("First message");
        controller.handleKeyAction(KeyAction.STOP_AND_SEND);
        assertEquals(1, mockChat.sentMessages.size());

        // Old delayed callbacks arrive after session completed
        controller.onPartialResult(new com.voice2text.speech.RecognitionResult("delayed old speech", false));
        controller.onFinalResult(new com.voice2text.speech.RecognitionResult("delayed old speech", true));

        // Start new session
        controller.handleKeyAction(KeyAction.START_DICTATION);
        mockEngine.simulateFinal("Second message");
        controller.handleKeyAction(KeyAction.STOP_AND_SEND);

        assertEquals(2, mockChat.sentMessages.size());
        assertEquals("First message", mockChat.sentMessages.get(0));
        assertEquals("Second message", mockChat.sentMessages.get(1));
    }

    @Test
    public void testDelayedFinalCallbackWaitsAndSubmitsSingleMessage() throws Exception {
        configManager.getConfig().setAutomaticSend(true);
        controller.handleKeyAction(KeyAction.START_DICTATION);
        assertTrue(controller.isDictationActive());

        // Partial speech arrives during recording
        mockEngine.simulatePartial("delayed speech incoming");

        // Release key initiates stop on another thread while engine is still processing
        Thread stopThread = new Thread(() -> {
            controller.handleKeyAction(KeyAction.STOP_AND_SEND);
        });
        stopThread.start();

        // Simulate 40ms inference delay before recognizer emits final result
        Thread.sleep(40);
        mockEngine.simulateFinal("delayed speech incoming");

        stopThread.join(2000);
        assertFalse(controller.isDictationActive());
        assertEquals(1, mockChat.sentMessages.size(), "Delayed final callback must be awaited and submitted exactly once");
        assertEquals("Delayed speech incoming", mockChat.sentMessages.get(0));
    }

    @Test
    public void testRapidConsecutiveRecordingsNoTranscriptLeaks() {
        configManager.getConfig().setAutomaticSend(true);

        // Session 1
        controller.handleKeyAction(KeyAction.START_DICTATION);
        mockEngine.simulateFinal("message alpha");
        controller.handleKeyAction(KeyAction.STOP_AND_SEND);

        assertEquals(1, mockChat.sentMessages.size());
        assertEquals("Message alpha", mockChat.sentMessages.get(0));

        // Rapid immediate Session 2
        controller.handleKeyAction(KeyAction.START_DICTATION);
        mockEngine.simulateFinal("message beta");
        controller.handleKeyAction(KeyAction.STOP_AND_SEND);

        assertEquals(2, mockChat.sentMessages.size());
        assertEquals("Message beta", mockChat.sentMessages.get(1));
    }

    @Test
    public void testRecognitionFailureDoesNotSendEmptyOrStaleMessage() {
        configManager.getConfig().setAutomaticSend(true);
        controller.handleKeyAction(KeyAction.START_DICTATION);

        // Engine fails
        mockEngine.simulateError("Recognition engine audio pipe severed");

        controller.handleKeyAction(KeyAction.STOP_AND_SEND);

        assertEquals(0, mockChat.sentMessages.size(), "No message must be sent on recognition failure");
        assertTrue(mockChat.clientMessages.size() > 0, "Error must be displayed to client");
    }

    @Test
    public void testHinglishSpeechTransliterationInController() throws IOException {
        File modelsDir = modelManager.getModelsBaseDir();
        File hinglishDir = new File(modelsDir, "vosk-model-small-hinglish/conf");
        hinglishDir.mkdirs();
        new File(hinglishDir, "model.conf").createNewFile();

        configManager.getConfig().setAutomaticSend(true);
        configManager.getConfig().setSpeechLanguage("hi-en");
        configManager.getConfig().setActiveModelId("vosk-model-small-hinglish");

        controller.handleKeyAction(KeyAction.START_DICTATION);
        assertTrue(controller.isDictationActive());

        // Vosk outputs phonetic Devanagari
        mockEngine.simulateFinal("भाई गेम में आ जाओ");

        controller.handleKeyAction(KeyAction.STOP_AND_SEND);

        assertEquals(1, mockChat.sentMessages.size());
        assertEquals("Bhai game mein aa jao", mockChat.sentMessages.get(0));
    }

    // Mock AudioSource
    private static class MockAudioSource implements AudioSource {
        private boolean open = false;
        private final List<byte[]> queue = new ArrayList<>();

        @Override
        public synchronized void open() {
            open = true;
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            if (!open) return -1;
            if (queue.isEmpty()) {
                // Return tiny silence
                int count = Math.min(length, 128);
                for (int i = 0; i < count; i++) {
                    buffer[offset + i] = 0;
                }
                return count;
            }
            byte[] next = queue.remove(0);
            int count = Math.min(length, next.length);
            System.arraycopy(next, 0, buffer, offset, count);
            return count;
        }

        public synchronized void feedAudio(byte[] data) {
            queue.add(data);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public synchronized void close() {
            open = false;
            queue.clear();
        }
    }

    // Mock ChatBridge
    private static class MockChatBridge implements MinecraftChatBridge {
        private boolean chatOpen = false;
        private String chatInput = "";
        private final List<String> sentMessages = new ArrayList<>();
        private final List<String> clientMessages = new ArrayList<>();

        @Override
        public void openChat(String initialText) {
            chatOpen = true;
            chatInput = initialText != null ? initialText : "";
        }

        @Override
        public void updateChatInput(String text) {
            chatInput = text != null ? text : "";
        }

        @Override
        public String getChatInput() {
            return chatInput;
        }

        @Override
        public void sendChatMessage(String message) {
            sentMessages.add(message);
        }

        @Override
        public void closeChat() {
            chatOpen = false;
        }

        @Override
        public boolean isChatOpen() {
            return chatOpen;
        }

        @Override
        public void displayClientMessage(String message, boolean isError) {
            clientMessages.add(message);
        }
    }
}
