package com.voice2text;

import com.voice2text.audio.AudioCaptureService;
import com.voice2text.audio.AudioSource;
import com.voice2text.audio.MicrophoneAudioSource;
import com.voice2text.compat.MinecraftChatBridge;
import com.voice2text.config.ConfigManager;
import com.voice2text.config.VoiceToTextConfig;
import com.voice2text.input.InputStateMachine;
import com.voice2text.input.KeyAction;
import com.voice2text.input.KeybindManager;
import com.voice2text.model.ModelManager;
import com.voice2text.model.SpeechModelInfo;
import com.voice2text.session.RecordingSession;
import com.voice2text.speech.EngineState;
import com.voice2text.speech.RecognitionListener;
import com.voice2text.speech.RecognitionResult;
import com.voice2text.speech.SpeechRecognitionEngine;
import com.voice2text.speech.vosk.VoskSpeechEngine;
import com.voice2text.transcription.PersonalizedLearningStore;
import com.voice2text.transcription.TranscriptionBuffer;
import com.voice2text.transcription.TranscriptionCorrectionService;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Central orchestrator connecting input events, audio capture, speech recognition,
 * transcription buffering, recording session lifecycle, and Minecraft chat screen integration.
 *
 * Performance enhancements:
 * - Asynchronous, non-blocking recording startup and finalization on dedicated background worker.
 * - Zero Minecraft client thread blocking on microphone acquisition, Java Sound line opening,
 *   or Kaldi/Vosk speech model loading.
 * - Integration of ContextAwareCorrectionService and PersonalizedLearningStore for Hindi, English,
 *   and Hinglish speech recognition improvement.
 */
public class VoiceToTextController implements RecognitionListener {

    private final ConfigManager configManager;
    private final ModelManager modelManager;
    private final InputStateMachine inputStateMachine;
    private final KeybindManager keybindManager;
    private final TranscriptionBuffer transcriptionBuffer;
    private final MinecraftChatBridge chatBridge;
    private final Executor clientThreadExecutor;

    private final Executor asyncExecutor;
    private final boolean ownsAsyncExecutor;
    private final PersonalizedLearningStore learningStore;
    private final TranscriptionCorrectionService correctionService;

    private SpeechRecognitionEngine speechEngine;
    private AudioCaptureService audioCaptureService;
    private AudioSource audioSource;

    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong sessionIdCounter = new AtomicLong(1);
    private volatile RecordingSession currentSession = null;

    private volatile float currentAudioLevel = 0.0f;
    private volatile boolean speechDetected = false;
    private volatile String statusMessage = "Ready";
    private String loadedModelId = null;
    private Consumer<VoiceToTextController> stateChangeListener;

    public VoiceToTextController(ConfigManager configManager,
                                 ModelManager modelManager,
                                 MinecraftChatBridge chatBridge,
                                 Executor clientThreadExecutor,
                                 SpeechRecognitionEngine engine,
                                 AudioSource audioSource,
                                 PersonalizedLearningStore learningStore,
                                 TranscriptionCorrectionService correctionService,
                                 Executor asyncExecutor) {
        this.configManager = configManager;
        this.modelManager = modelManager;
        this.chatBridge = chatBridge;
        this.clientThreadExecutor = clientThreadExecutor != null ? clientThreadExecutor : Runnable::run;
        this.speechEngine = engine != null ? engine : new VoskSpeechEngine();
        this.audioSource = audioSource != null ? audioSource : new MicrophoneAudioSource();

        if (asyncExecutor != null) {
            this.asyncExecutor = asyncExecutor;
            this.ownsAsyncExecutor = false;
        } else {
            this.asyncExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SpeakCraft-AsyncWorker");
                t.setDaemon(true);
                return t;
            });
            this.ownsAsyncExecutor = true;
        }

        if (learningStore != null) {
            this.learningStore = learningStore;
        } else {
            File baseDir = modelManager != null && modelManager.getModelsBaseDir() != null
                    ? (modelManager.getModelsBaseDir().getParentFile() != null ? modelManager.getModelsBaseDir().getParentFile() : modelManager.getModelsBaseDir())
                    : new File(".");
            File storeFile = new File(baseDir, "learned_corrections.json");
            this.learningStore = new PersonalizedLearningStore(storeFile);
        }

        this.correctionService = correctionService != null ? correctionService : new TranscriptionCorrectionService(this.learningStore);

        VoiceToTextConfig config = configManager.getConfig();
        this.keybindManager = new KeybindManager(config);
        this.inputStateMachine = new InputStateMachine(config.isAutomaticSend());
        this.inputStateMachine.setActionListener(this::handleKeyAction);

        this.transcriptionBuffer = new TranscriptionBuffer();
        this.transcriptionBuffer.setAutoCapitalization(config.isAutoCapitalization());
        this.transcriptionBuffer.setVoiceCommandsEnabled(config.isVoiceCommandsEnabled());

        this.speechEngine.setListener(this);
        this.audioCaptureService = new AudioCaptureService(this.audioSource, this.speechEngine, config.getMicSensitivity());
        this.audioCaptureService.setAudioLevelListener(this::onAudioLevel);
        this.audioCaptureService.setVoiceActivityListener(this::onVoiceActivity);
        this.audioCaptureService.setErrorListener(this::onAudioError);

        com.voice2text.audio.AudioDeviceManager.setFallbackNotificationListener(msg ->
                chatBridge.displayClientMessage("§e[SpeakCraft] " + msg, false)
        );
    }

    public VoiceToTextController(ConfigManager configManager,
                                 ModelManager modelManager,
                                 MinecraftChatBridge chatBridge,
                                 Executor clientThreadExecutor,
                                 SpeechRecognitionEngine engine,
                                 AudioSource audioSource) {
        this(configManager, modelManager, chatBridge, clientThreadExecutor, engine, audioSource, null, null, Runnable::run);
    }

    public VoiceToTextController(ConfigManager configManager,
                                 ModelManager modelManager,
                                 MinecraftChatBridge chatBridge,
                                 Executor clientThreadExecutor) {
        this(configManager, modelManager, chatBridge, clientThreadExecutor, null, null, null, null, null);
    }

    /**
     * Asynchronously preloads the active speech model into memory in the background
     * so that the first activation with V does not experience any disk or model loading latency.
     */
    public void preloadModelAsync() {
        VoiceToTextConfig config = configManager.getConfig();
        String modelId = config.getActiveModelId();
        if (modelManager.isModelInstalled(modelId) && loadedModelId == null) {
            asyncExecutor.execute(() -> {
                File modelDir = modelManager.getModelDir(modelId);
                try {
                    speechEngine.initialize(modelDir.getAbsolutePath());
                    this.loadedModelId = modelId;
                    statusMessage = "Ready";
                    notifyStateChange();
                } catch (Throwable ignored) {
                }
            });
        }
    }

    public void handleKeyAction(KeyAction action) {
        switch (action) {
            case START_DICTATION:
                startDictation();
                break;
            case STOP_AND_SEND:
                stopAndSend();
                break;
            case STOP_AND_KEEP_DRAFT:
                stopAndKeepDraft();
                break;
            case CANCEL_DICTATION:
                cancelDictation();
                break;
            case OPEN_NORMAL_CHAT:
                clientThreadExecutor.execute(() -> chatBridge.openChat(""));
                break;
            default:
                break;
        }
    }

    /**
     * Starts a new dictation session asynchronously without blocking the Minecraft main thread.
     * Captures an immutable snapshot of configuration options, allocates a unique session identifier,
     * and offloads microphone and model initialization to the background executor.
     */
    public synchronized void startDictation() {
        if (active.get() || (currentSession != null && !currentSession.isFinished())) {
            return;
        }

        VoiceToTextConfig config = configManager.getConfig();
        String modelId = config.getActiveModelId();

        // Check if model is downloaded
        if (!modelManager.isModelInstalled(modelId)) {
            SpeechModelInfo info = modelManager.getModelInfo(modelId);
            String modelName = info != null ? info.getDisplayName() : modelId;
            chatBridge.displayClientMessage("§c[SpeakCraft] Speech model not installed: " + modelName +
                    ". Press " + config.getConfigKey() + " to open SpeakCraft settings and download it.", true);
            statusMessage = "Model Missing";
            notifyStateChange();
            return;
        }

        // Snapshot configuration at recording start
        boolean autoSendSnapshot = config.isAutomaticSend();
        String currentDraft = chatBridge.isChatOpen() ? chatBridge.getChatInput() : "";

        long sessionId = sessionIdCounter.getAndIncrement();
        RecordingSession session = new RecordingSession(sessionId, autoSendSnapshot, currentDraft);
        this.currentSession = session;

        // Ensure state machine aligns with the active session snapshot
        inputStateMachine.setAutomaticSendEnabled(autoSendSnapshot);
        transcriptionBuffer.startSession(currentDraft, currentDraft.length());

        statusMessage = "Starting...";
        notifyStateChange();

        // Offload blocking hardware and speech model work to background executor
        asyncExecutor.execute(() -> {
            try {
                // Initialize or switch engine model if needed
                File modelDir = modelManager.getModelDir(modelId);
                if (!modelId.equals(loadedModelId) || speechEngine.getState() == EngineState.UNINITIALIZED || speechEngine.getState() == EngineState.CLOSED) {
                    try {
                        speechEngine.close();
                        speechEngine.initialize(modelDir.getAbsolutePath());
                        this.loadedModelId = modelId;
                    } catch (IOException e) {
                        this.loadedModelId = null;
                        chatBridge.displayClientMessage("§c[SpeakCraft] Failed to load speech model: " + e.getMessage(), true);
                        statusMessage = "Model Load Failed";
                        session.transitionToCancelled();
                        notifyStateChange();
                        return;
                    }
                }

                // Check if user cancelled or requested stop while startup was pending
                if (session.isFinished() || session.isStopRequested()) {
                    handlePendingStopOrCancel(session);
                    return;
                }

                if (audioSource instanceof MicrophoneAudioSource) {
                    ((MicrophoneAudioSource) audioSource).setDeviceName(config.getSelectedMicrophone());
                }

                speechEngine.start();
                audioCaptureService.start();
                active.set(true);
                session.transitionToRecording();
                statusMessage = "Listening...";
                notifyStateChange();

                // Handle race condition where key was released while audio line was opening
                if (session.isStopRequested()) {
                    handlePendingStopOrCancel(session);
                }
            } catch (Throwable e) {
                chatBridge.displayClientMessage("§c[SpeakCraft] Failed to open microphone: " + e.getMessage(), true);
                statusMessage = "Mic Error";
                active.set(false);
                session.transitionToCancelled();
                notifyStateChange();
            }
        });
    }

    private void handlePendingStopOrCancel(RecordingSession session) {
        if (session == null || session.isFinished()) {
            return;
        }

        KeyAction pending = session.getPendingStopAction();
        active.set(false);

        if (audioCaptureService != null) {
            audioCaptureService.stop();
        }
        if (speechEngine != null) {
            speechEngine.stop();
        }

        if (pending == KeyAction.CANCEL_DICTATION) {
            session.transitionToCancelled();
            String savedDraft = transcriptionBuffer.getFullCommittedText();
            currentAudioLevel = 0.0f;
            speechDetected = false;
            statusMessage = "Cancelled";
            notifyStateChange();
            clientThreadExecutor.execute(() -> chatBridge.updateChatInput(savedDraft));
        } else {
            boolean autoSend = (pending == KeyAction.STOP_AND_SEND);
            finalizeAndDispatch(session, autoSend);
        }
    }

    private boolean diagnosticLoggingEnabled = Boolean.getBoolean("voice2text.debug");

    public void setDiagnosticLoggingEnabled(boolean enabled) {
        this.diagnosticLoggingEnabled = enabled;
    }

    public boolean isDiagnosticLoggingEnabled() {
        return diagnosticLoggingEnabled;
    }

    public void logDiagnostic(String stage, String message) {
        if (diagnosticLoggingEnabled || System.getProperty("voice2text.debug") != null) {
            System.out.println("[SpeakCraft-Diagnostic] [" + stage + "] " + message);
        }
    }

    /**
     * Stops recording and automatically submits the finalized transcript to Minecraft chat.
     * Guaranteed to submit exactly once and only for non-empty speech.
     * Executes non-blocking: final result wait and submission occur on the background executor.
     */
    public synchronized void stopAndSend() {
        RecordingSession session = this.currentSession;
        if (session == null || session.isFinished()) {
            return;
        }

        if (!active.get()) {
            session.requestStop(KeyAction.STOP_AND_SEND);
            return;
        }

        if (!session.markFinalizing(true)) {
            return; // Idempotent guard: already finalising / finalized
        }

        statusMessage = "Finalizing...";
        notifyStateChange();
        active.set(false);

        asyncExecutor.execute(() -> {
            try {
                if (audioCaptureService != null) {
                    audioCaptureService.stop();
                }

                if (speechEngine != null) {
                    speechEngine.stop();
                }

                // Await final result asynchronously if speech was active and callback not yet received
                if (!session.isFinalResultReceived() && (session.hasSpeechDetectedEver() || transcriptionBuffer.hasSpeech())) {
                    session.awaitFinalResult(1000);
                }

                finalizeAndDispatch(session, true);
            } catch (Throwable t) {
                logDiagnostic("StopAndSend-Error", t.getMessage());
            }
        });
    }

    /**
     * Stops recording and inserts the finalized transcript into the Minecraft chat input
     * as an editable draft without automatically sending it.
     * Executes non-blocking: final result wait occurs on the background executor.
     */
    public synchronized void stopAndKeepDraft() {
        RecordingSession session = this.currentSession;
        if (session == null || session.isFinished()) {
            return;
        }

        if (!active.get()) {
            session.requestStop(KeyAction.STOP_AND_KEEP_DRAFT);
            return;
        }

        if (!session.markFinalizing(false)) {
            return; // Idempotent guard
        }

        statusMessage = "Finalizing...";
        notifyStateChange();
        active.set(false);

        asyncExecutor.execute(() -> {
            try {
                if (audioCaptureService != null) {
                    audioCaptureService.stop();
                }

                if (speechEngine != null) {
                    speechEngine.stop();
                }

                if (!session.isFinalResultReceived() && (session.hasSpeechDetectedEver() || transcriptionBuffer.hasSpeech())) {
                    session.awaitFinalResult(1000);
                }

                finalizeAndDispatch(session, false);
            } catch (Throwable t) {
                logDiagnostic("StopAndKeepDraft-Error", t.getMessage());
            }
        });
    }

    private synchronized void finalizeAndDispatch(RecordingSession session, boolean autoSend) {
        if (session == null || session.isFinished()) {
            return;
        }

        String finalDraft = transcriptionBuffer.finalizeSession();
        // Context-aware automatic speech correction on finalized text
        if (correctionService != null && finalDraft != null && !finalDraft.trim().isEmpty()) {
            finalDraft = correctionService.correctFinalizedText(finalDraft);
        }

        session.setFinalizedText(finalDraft);
        currentAudioLevel = 0.0f;
        speechDetected = false;

        logDiagnostic("Pre-Insertion", "Final draft before insertion: '" + finalDraft + "', autoSend: " + autoSend);

        final String dispatchedText = finalDraft;
        if (autoSend) {
            if (dispatchedText != null && !dispatchedText.trim().isEmpty()) {
                String toSend = dispatchedText.trim();
                if (session.markSubmitted()) {
                    statusMessage = "Sent";
                    notifyStateChange();
                    logDiagnostic("Chat-Submission", "Submitting message to Minecraft chat: '" + toSend + "'");
                    clientThreadExecutor.execute(() -> {
                        chatBridge.sendChatMessage(toSend);
                    });
                }
            } else {
                statusMessage = "Draft Kept";
                notifyStateChange();
                clientThreadExecutor.execute(() -> {
                    if (chatBridge.isChatOpen()) {
                        chatBridge.updateChatInput("");
                    }
                });
            }
        } else {
            statusMessage = "Draft Kept";
            notifyStateChange();
            clientThreadExecutor.execute(() -> {
                if (dispatchedText != null && !dispatchedText.trim().isEmpty()) {
                    chatBridge.updateChatInput(dispatchedText);
                    if (!chatBridge.isChatOpen()) {
                        chatBridge.openChat(dispatchedText);
                    }
                }
            });
        }

        session.transitionToCompleted();
    }

    /**
     * Cancels dictation, discarding any in-progress uncommitted speech
     * while retaining any pre-existing text draft.
     */
    public synchronized void cancelDictation() {
        RecordingSession session = this.currentSession;
        if (session == null || session.isFinished()) {
            return;
        }

        session.transitionToCancelled();
        active.set(false);

        asyncExecutor.execute(() -> {
            if (audioCaptureService != null) {
                audioCaptureService.stop();
            }
            if (speechEngine != null) {
                speechEngine.stop();
            }
        });

        String savedDraft = transcriptionBuffer.getFullCommittedText();
        currentAudioLevel = 0.0f;
        speechDetected = false;
        statusMessage = "Cancelled";
        notifyStateChange();

        clientThreadExecutor.execute(() -> {
            chatBridge.updateChatInput(savedDraft);
        });
    }

    /**
     * Convenience stop method evaluating the session's autoSend snapshot.
     */
    public synchronized void stopDictation() {
        RecordingSession session = this.currentSession;
        if (session == null || session.isFinished()) return;
        if (session.isAutoSendSnapshot()) {
            stopAndSend();
        } else {
            stopAndKeepDraft();
        }
    }

    /**
     * Checks if Romanized Hinglish transliteration is explicitly active.
     * Returns true ONLY when speech language is configured as "hi-en" or "hinglish".
     * Standard Hindi ("hi", "hi-IN") preserves natural Devanagari Hindi and English words.
     */
    public boolean isRomanizedHinglishActive() {
        VoiceToTextConfig cfg = configManager != null ? configManager.getConfig() : null;
        if (cfg == null) return false;
        String lang = cfg.getSpeechLanguage();
        String modelId = cfg.getActiveModelId();
        return (lang != null && (lang.equalsIgnoreCase("hi-en") || lang.equalsIgnoreCase("hinglish")))
                || (modelId != null && modelId.toLowerCase().contains("hinglish"));
    }

    /**
     * Backward-compatible alias for Hinglish recognition checking.
     */
    public boolean isHinglishActive() {
        return isRomanizedHinglishActive();
    }

    @Override
    public void onPartialResult(RecognitionResult result) {
        RecordingSession session = this.currentSession;
        if (session == null || session.isFinished() || !active.get() || result == null) {
            return;
        }

        String rawText = result.getText();
        if (isRomanizedHinglishActive()) {
            String original = rawText;
            rawText = com.voice2text.transcription.HinglishTransliterationService.transliterate(rawText);
            logDiagnostic("ASR-Partial", "Raw: '" + original + "' -> Transliterated: '" + rawText + "'");
        } else {
            logDiagnostic("ASR-Partial", "Raw: '" + rawText + "'");
        }
        transcriptionBuffer.updatePartial(rawText);
        String display = transcriptionBuffer.getDisplayText();
        notifyStateChange();

        clientThreadExecutor.execute(() -> {
            if (active.get() && chatBridge.isChatOpen()) {
                chatBridge.updateChatInput(display);
            }
        });
    }

    @Override
    public void onFinalResult(RecognitionResult result) {
        RecordingSession session = this.currentSession;
        if (session == null || session.isFinished() || result == null) {
            return;
        }

        String rawText = result.getText();
        // Context-aware speech recognition correction
        if (correctionService != null) {
            rawText = correctionService.correctFinalizedText(rawText);
        }

        if (isRomanizedHinglishActive()) {
            String original = rawText;
            rawText = com.voice2text.transcription.HinglishTransliterationService.transliterate(rawText);
            logDiagnostic("ASR-Final", "Raw: '" + original + "' -> Transliterated: '" + rawText + "'");
        } else {
            logDiagnostic("ASR-Final", "Raw: '" + rawText + "'");
        }

        transcriptionBuffer.commitFinal(rawText);
        session.markFinalResultReceived();
        String display = transcriptionBuffer.getDisplayText();
        notifyStateChange();

        if (session.getState() == RecordingSession.State.FINALIZING) {
            finalizeAndDispatch(session, session.isAutoSendEffective());
            return;
        }

        clientThreadExecutor.execute(() -> {
            if (active.get() && chatBridge.isChatOpen()) {
                chatBridge.updateChatInput(display);
            }
        });
    }

    @Override
    public void onError(String errorMessage, Throwable throwable) {
        statusMessage = "Error: " + errorMessage;
        notifyStateChange();
        chatBridge.displayClientMessage("§c[Voice2Text] " + errorMessage, true);
    }

    @Override
    public void onStateChanged(EngineState oldState, EngineState newState) {
        notifyStateChange();
    }

    private void onAudioLevel(float level) {
        this.currentAudioLevel = level;
        notifyStateChange();
    }

    private void onVoiceActivity(boolean active) {
        this.speechDetected = active;
        if (active && currentSession != null) {
            currentSession.setSpeechDetectedEver(true);
        }
        notifyStateChange();
    }

    private void onAudioError(String error) {
        onError(error, null);
    }

    private void notifyStateChange() {
        if (stateChangeListener != null) {
            try {
                stateChangeListener.accept(this);
            } catch (Exception ignored) {
            }
        }
    }

    public void tick(long now) {
        inputStateMachine.tick(now);
    }

    public boolean isDictationActive() {
        return active.get() || (currentSession != null && currentSession.getState() == RecordingSession.State.STARTING);
    }

    public boolean isHoldToTalkActive() {
        return inputStateMachine.isHoldToTalkActive();
    }

    public boolean isHoldToTalkRecording() {
        return inputStateMachine.isHoldToTalkRecording();
    }

    public boolean isWaitingForHold() {
        return inputStateMachine.isWaitingForHold();
    }

    public float getCurrentAudioLevel() {
        return currentAudioLevel;
    }

    public boolean isSpeechDetected() {
        return speechDetected;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public RecordingSession getCurrentSession() {
        return currentSession;
    }

    public InputStateMachine getInputStateMachine() {
        return inputStateMachine;
    }

    public KeybindManager getKeybindManager() {
        return keybindManager;
    }

    public TranscriptionBuffer getTranscriptionBuffer() {
        return transcriptionBuffer;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ModelManager getModelManager() {
        return modelManager;
    }

    public PersonalizedLearningStore getLearningStore() {
        return learningStore;
    }

    public TranscriptionCorrectionService getCorrectionService() {
        return correctionService;
    }

    public void learnCorrection(String mistake, String correction, String context) {
        if (learningStore != null) {
            learningStore.recordCorrection(mistake, correction, context);
        }
    }

    public void setStateChangeListener(Consumer<VoiceToTextController> listener) {
        this.stateChangeListener = listener;
    }

    public String getLoadedModelId() {
        return loadedModelId;
    }

    public synchronized boolean switchModel(String modelId) {
        if (modelId == null || modelId.equals(loadedModelId)) {
            return true;
        }
        if (!modelManager.isModelInstalled(modelId)) {
            return false;
        }
        File modelDir = modelManager.getModelDir(modelId);
        try {
            speechEngine.close();
            speechEngine.initialize(modelDir.getAbsolutePath());
            this.loadedModelId = modelId;
            statusMessage = "Ready";
            notifyStateChange();
            return true;
        } catch (IOException e) {
            this.loadedModelId = null;
            statusMessage = "Model Load Failed";
            notifyStateChange();
            return false;
        }
    }

    public void shutdown() {
        active.set(false);
        if (currentSession != null && !currentSession.isFinished()) {
            currentSession.transitionToCancelled();
        }
        if (audioCaptureService != null) {
            audioCaptureService.stop();
        }
        if (audioSource instanceof MicrophoneAudioSource) {
            ((MicrophoneAudioSource) audioSource).release();
        }
        if (speechEngine != null) {
            speechEngine.close();
        }
        if (ownsAsyncExecutor && asyncExecutor instanceof ExecutorService) {
            ((ExecutorService) asyncExecutor).shutdownNow();
        }
        loadedModelId = null;
    }
}
