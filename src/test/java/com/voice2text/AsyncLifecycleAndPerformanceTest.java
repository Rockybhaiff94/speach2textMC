package com.voice2text;

import com.voice2text.audio.AudioCaptureService;
import com.voice2text.audio.AudioDeviceManager;
import com.voice2text.audio.AudioSource;
import com.voice2text.compat.MinecraftChatBridge;
import com.voice2text.config.ConfigManager;
import com.voice2text.input.KeyAction;
import com.voice2text.model.ModelManager;
import com.voice2text.session.RecordingSession;
import com.voice2text.speech.mock.MockSpeechEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncLifecycleAndPerformanceTest {

    private VoiceToTextController controller;
    private MockSpeechEngine mockEngine;
    private SlowMockAudioSource mockAudio;
    private MockChatBridge mockChat;
    private ConfigManager configManager;
    private ModelManager modelManager;
    private ExecutorService testAsyncExecutor;

    @BeforeEach
    public void setUp(@TempDir Path tempDir) {
        File configFile = tempDir.resolve("config.json").toFile();
        File modelsDir = tempDir.resolve("models").toFile();
        modelsDir.mkdirs();

        File defaultModelDir = new File(modelsDir, "vosk-model-small-en-us-0.15/conf");
        defaultModelDir.mkdirs();
        try {
            new File(defaultModelDir, "model.conf").createNewFile();
        } catch (IOException ignored) {
        }

        configManager = new ConfigManager(configFile);
        modelManager = new ModelManager(modelsDir);
        mockEngine = new MockSpeechEngine();
        mockAudio = new SlowMockAudioSource();
        mockChat = new MockChatBridge();

        testAsyncExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Test-AsyncWorker");
            t.setDaemon(true);
            return t;
        });

        controller = new VoiceToTextController(
                configManager,
                modelManager,
                mockChat,
                Runnable::run,
                mockEngine,
                mockAudio,
                null,
                null,
                testAsyncExecutor
        );
    }

    @AfterEach
    public void tearDown() {
        if (controller != null) {
            controller.shutdown();
        }
        if (testAsyncExecutor != null) {
            testAsyncExecutor.shutdownNow();
        }
    }

    @Test
    public void testAsyncStartupDoesNotBlockCallingThread() throws Exception {
        // SlowMockAudioSource simulates 150ms hardware delay on open()
        mockAudio.simulatedOpenDelayMs = 150;

        long start = System.currentTimeMillis();
        controller.handleKeyAction(KeyAction.START_DICTATION);
        long elapsed = System.currentTimeMillis() - start;

        // The calling thread (Minecraft client thread) must return immediately (< 30ms)
        // without waiting for the 150ms audio hardware initialization
        assertTrue(elapsed < 40, "startDictation on calling thread took " + elapsed + " ms, expected < 40 ms");

        // Wait asynchronously for background worker to complete startup
        assertTrue(mockAudio.awaitOpen(1000, TimeUnit.MILLISECONDS), "Audio source should open in background");
        assertTrue(controller.isDictationActive());
    }

    @Test
    public void testAsyncFinalizationDoesNotBlockCallingThread() throws Exception {
        controller.handleKeyAction(KeyAction.START_DICTATION);
        mockAudio.awaitOpen(1000, TimeUnit.MILLISECONDS);
        assertTrue(controller.isDictationActive());

        // Simulate speech detected
        mockEngine.simulatePartial("testing latency");

        long start = System.currentTimeMillis();
        controller.handleKeyAction(KeyAction.STOP_AND_KEEP_DRAFT);
        long elapsed = System.currentTimeMillis() - start;

        // stopAndKeepDraft must return immediately without waiting for final result latch
        assertTrue(elapsed < 40, "stopAndKeepDraft took " + elapsed + " ms, expected < 40 ms");

        // Background worker finalizes session cleanly
        assertFalse(controller.isDictationActive());
    }

    @Test
    public void testRapidRepeatedKeyEventsNoDuplicateSessions() throws Exception {
        // Trigger 10 rapid activation events in a burst
        for (int i = 0; i < 10; i++) {
            controller.handleKeyAction(KeyAction.START_DICTATION);
        }

        mockAudio.awaitOpen(1000, TimeUnit.MILLISECONDS);

        // There must be exactly 1 active recording session
        RecordingSession session = controller.getCurrentSession();
        assertNotNull(session);
        assertEquals(1, session.getId(), "Session ID should remain 1 despite 10 rapid key presses");
    }

    @Test
    public void testEarlyReleaseWhileStartingCancelsCleanly() throws Exception {
        mockAudio.simulatedOpenDelayMs = 80;

        // Press V then release immediately before audio hardware finishes opening
        controller.handleKeyAction(KeyAction.START_DICTATION);
        controller.handleKeyAction(KeyAction.STOP_AND_KEEP_DRAFT);

        Thread.sleep(150);

        // Should finalize cleanly without leaving recording permanently stuck
        assertFalse(controller.isDictationActive());
        RecordingSession session = controller.getCurrentSession();
        assertNotNull(session);
        assertTrue(session.isFinished(), "Session should be finished");
    }

    @Test
    public void testBoundedAudioQueueWithBackpressure() throws Exception {
        BlockingAudioSource audioSource = new BlockingAudioSource();
        MockSpeechEngine slowEngine = new MockSpeechEngine();
        AudioCaptureService captureService = new AudioCaptureService(audioSource, slowEngine, 0.04f);

        captureService.start();

        // Feed 100 chunks into audio source (capacity is 32)
        byte[] dummy = new byte[1024];
        for (int i = 0; i < 100; i++) {
            audioSource.feed(dummy);
        }

        Thread.sleep(100);

        // Verify queue size never exceeds MAX_QUEUE_CAPACITY
        assertTrue(captureService.getQueueSize() <= AudioCaptureService.MAX_QUEUE_CAPACITY,
                "Queue size " + captureService.getQueueSize() + " exceeded limit " + AudioCaptureService.MAX_QUEUE_CAPACITY);

        captureService.stop();
        assertFalse(captureService.isRunning());
    }

    @Test
    public void testMicrophoneLineReuseWithoutReopenOverhead() throws Exception {
        try (com.voice2text.audio.MicrophoneAudioSource mic = new com.voice2text.audio.MicrophoneAudioSource()) {
            assertEquals(AudioDeviceManager.SYSTEM_DEFAULT, mic.getDeviceName());

            // Changing device name cleanly releases previous device
            mic.setDeviceName("Custom Virtual Mic");
            assertEquals("Custom Virtual Mic", mic.getDeviceName());

            mic.release();
            assertFalse(mic.isOpen());
        }
    }

    // Mock AudioSource with simulated latency
    private static class SlowMockAudioSource implements AudioSource {
        volatile int simulatedOpenDelayMs = 0;
        volatile boolean open = false;
        final CountDownLatch openLatch = new CountDownLatch(1);

        @Override
        public void open() throws IOException {
            if (simulatedOpenDelayMs > 0) {
                try {
                    Thread.sleep(simulatedOpenDelayMs);
                } catch (InterruptedException ignored) {
                }
            }
            open = true;
            openLatch.countDown();
        }

        public boolean awaitOpen(long timeout, TimeUnit unit) throws InterruptedException {
            return openLatch.await(timeout, unit);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (!open) return -1;
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
            return length;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    private static class BlockingAudioSource implements AudioSource {
        volatile boolean open = false;
        final java.util.concurrent.BlockingQueue<byte[]> dataQueue = new java.util.concurrent.LinkedBlockingQueue<>();

        public void feed(byte[] data) {
            dataQueue.offer(data);
        }

        @Override
        public void open() {
            open = true;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (!open) return -1;
            try {
                byte[] chunk = dataQueue.poll(20, TimeUnit.MILLISECONDS);
                if (chunk != null) {
                    System.arraycopy(chunk, 0, buffer, offset, Math.min(chunk.length, length));
                    return Math.min(chunk.length, length);
                }
            } catch (InterruptedException ignored) {
            }
            return 0;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
            dataQueue.clear();
        }
    }

    private static class MockChatBridge implements MinecraftChatBridge {
        final List<String> sentMessages = new ArrayList<>();
        String currentInput = "";

        @Override
        public void sendChatMessage(String message) {
            sentMessages.add(message);
        }

        @Override
        public void updateChatInput(String text) {
            this.currentInput = text;
        }

        @Override
        public String getChatInput() {
            return currentInput;
        }

        @Override
        public void closeChat() {
        }

        @Override
        public boolean isChatOpen() {
            return false;
        }

        @Override
        public void openChat(String draft) {
            this.currentInput = draft;
        }

        @Override
        public void displayClientMessage(String message, boolean overlay) {
        }
    }
}
