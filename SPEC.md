# SPECIFICATION: SpeakCraft — Minecraft Real-Time Voice-to-Text Client Mod

**Status:** FINALIZED — REFACTORED FOR DUAL-MODEL HINGLISH + ENGLISH PIPELINE  
**Author:** Senior Minecraft Fabric Developer, Audio Systems Engineer & Game UI/UX Designer  
**Date:** 2026-11-01  
**Target Environments:** Minecraft Java Edition 26.2, 26.3 (Fabric Mod Loader)

---

## 1. Executive Summary & Goals

SpeakCraft (`voice2text`) is a lightweight, offline-first, client-side Fabric mod for Minecraft Java Edition that transcribes spoken player voice into Minecraft chat in real time.

### Core Architecture Overhaul:
1. **Exactly Two Supported Speech Models**:
   - **Model 1: Whisper Hindi2Hinglish Apex (`whisper-hindi2hinglish-apex`)**: Primary Hindi speech recognition and Hindi-to-Roman transliteration engine. Converts Hindi speech into natural Roman English alphabets (A-Z) without converting genuine English words into incorrect phonetic Hindi.
   - **Model 2: Whisper Small (`whisper-small`)**: English speech recognition and word identification engine. Independently identifies genuine English words, spellings, and audio timestamps from the original audio.
   - **Complete Removal of Extra Models**: All unnecessary models (Whisper Large-v3, Large-v3 Turbo, other Whisper variants, multiple regional Vosk models, IndicConformer, Shruti) are cleanly removed from catalog, registration, and UI.

2. **Dual-Model Recognition & Intelligent Merging Pipeline**:
   - **Single Audio Capture**: User audio is recorded once into an audio buffer with timing information.
   - **Independent Dual Recognition**: The audio is processed by both Whisper Hindi2Hinglish Apex and Whisper Small.
   - **Word-Level Alignment & Language Classification**: Word intervals, timestamps, sequence order, and confidence scores determine language boundaries without classifying all Roman text as English.
   - **Merged Romanized Hinglish Output**: Preserves genuine English spelling and exact word positions, while transcribing Hindi speech into natural Romanized Hindi.

3. **Simplified User Interface**:
   - **Minimal Settings Panel (`VoiceConfigScreen`)**:
     - Removed: Voice Capitalization button, Voice Punctuation button, Output Script toggle, duplicate model cycle/download controls, and unused AI options.
     - Kept: Automatic Chat Sending, Chat-Only Toggle Key (`T`), Gameplay Hold Key (`V`), Stop & Draft Key (`P`), Settings Key (`O`), Microphone Device selection & refresh, Reset Keybinds, Language & Model Library access, and Done button.
   - **Focused Model Library (`ModelLibraryScreen`)**:
     - Displays ONLY the two supported models (`Whisper Hindi2Hinglish Apex` and `Whisper Small`).
     - High-contrast, clean layout with direct Download, Activate, and Details actions.

4. **Preserved Gameplay & Controls**:
   - Preserves `V` gameplay hold-to-activate (with player movement intact: W, A, S, D, Space, Shift, Ctrl).
   - Preserves `T` chat-only toggle recording.
   - Preserves `P` stop & draft.
   - Preserves `Escape` cancel.
   - Configurable Minecraft keybinds.
   - Chat input integration, automatic send, manual text insertion, client-thread safety.

---

## 2. Architecture & Module Design

```
d:/mybuilds/voice2text/
├── src/main/java/com/voice2text/
│   ├── VoiceToTextMod.java             # Fabric Client Mod entrypoint & lifecycle
│   ├── VoiceToTextController.java      # Central state & audio-to-text pipeline orchestrator
│   ├── audio/                          # Audio capture, line discovery & metering
│   │   ├── AudioCaptureService.java    # Background capture thread, 16kHz 16-bit mono PCM
│   │   ├── AudioDeviceManager.java     # System input device discovery & line opening
│   │   ├── AudioLevelMeter.java        # RMS amplitude & smoothed meter calculation
│   │   ├── AudioSource.java            # Abstract audio stream interface
│   │   └── MicrophoneAudioSource.java  # Hardware microphone capture via AudioDeviceManager
│   ├── speech/                         # Speech Recognition Engine abstraction
│   │   ├── SpeechRecognitionEngine.java# Core interface for streaming recognition
│   │   ├── RecognitionResult.java      # Partial & final segment structures
│   │   ├── RecognitionListener.java    # Event callbacks
│   │   ├── vosk/                       # Vosk engine implementation
│   │   │   └── VoskSpeechEngine.java
│   │   └── mock/                       # Deterministic test engine
│   │       └── MockSpeechEngine.java
│   ├── model/                          # Offline Speech Model Management
│   │   ├── SpeechModelInfo.java        # Model descriptor with URLs, sizes, and hashes
│   │   ├── ModelManager.java           # Discovery, download, verification, and extraction
│   │   ├── ModelInstallationState.java # State lifecycle enum
│   │   └── DownloadProgressListener.java
│   ├── transcription/                  # Real-Time Text Reconciliation & Formatting
│   │   ├── TranscriptionBuffer.java    # 4-part logical buffer (draft, committed, partial, display)
│   │   ├── TextFormatter.java          # Capitalization, punctuation & voice commands
│   │   ├── HinglishTransliterationService.java # Offline Romanized Hinglish engine (candra vowels, nuktas, schwa deletion)
│   │   └── ChatMessageSplitter.java    # Minecraft 256-char limit handling
│   ├── session/                        # Session lifecycle and idempotency guards
│   │   └── RecordingSession.java       # Atomic states, start snapshotting, single-submission guard
│   ├── input/                          # Input state machine & keybind handling
│   │   ├── InputStateMachine.java      # Conflict-aware activation, hold-to-talk, timeout handler
│   │   ├── KeyAction.java              # Actions enum
│   │   └── KeybindManager.java         # Key mappings, conflict check & Controls menu sync
│   ├── config/                         # Configuration & Persistence
│   │   ├── VoiceToTextConfig.java      # Strongly-typed configuration POJO
│   │   └── ConfigManager.java          # JSON persistence in .minecraft/voice2text/config.json
│   ├── ui/                             # Visual overlays & screens
│   │   ├── ListeningOverlay.java       # Bottom-right HUD with waveform & live draft preview
│   │   └── VoiceConfigScreen.java      # Minecraft-style settings screen with dropdowns
│   └── fabric/mixin/                   # Fabric Mixins
│       ├── KeyboardHandlerMixin.java   # Intercepts gameplay keys (press & release)
│       ├── HudMixin.java               # Renders ListeningOverlay on in-game HUD
│       ├── ChatScreenMixin.java        # Intercepts chat keys and renders overlay in chat
│       └── ChatScreenAccessor.java     # Accesses chat input box safely
```

---

## 3. Detailed Specifications

### 3.1 Input State Machine & Recording Modes
- **Mode A: Gameplay V Hold-to-Activate**:
  - Key: `V` (default, configurable).
  - Press & Hold starts a 500 ms activation threshold.
  - If released before 500 ms: pending activation is cancelled, recording does not start, chat does not open.
  - If held for >= 500 ms: recording session begins immediately, normal Minecraft chat opens automatically, and live transcription streams directly into the chat input edit box.
  - **Natural Gameplay Movement**:
    - W, A, S, and D continue controlling player movement.
    - Jump (Space), Sneak/Crouch (Shift), and Sprint (Ctrl) remain fully functional.
    - Movement and gameplay key events are routed to `KeyMapping.setDown()` and suppressed from entering the chat draft.
    - Characters from ordinary typing and movement keys are isolated (`charTyped` cancelled) while voice recording owns text input.
    - Enter key is suppressed during active dictation to prevent accidental early message submission.
  - Releasing V stops recording and finalizes transcription immediately.
  - Releasing all movement keys when recording ends restores normal keyboard input handling.
  - When chat is already open, pressing 'V' types the character `'v'` normally without triggering Mode A.

- **Mode B: Chat-Only Toggle Recording**:
  - Key: `T` (default activation key, configurable).
  - Only activates when normal Minecraft chat input is open.
  - If chat is closed, pressing `T` does NOT start recording; vanilla Minecraft handles `T` to open chat normally.
  - Once chat is open, pressing `T` once starts recording immediately (zero double-tap, zero delay).
  - Next press stops recording and finalizes speech immediately.
  - Operating-system key-repeat events (<150 ms) are filtered out.
  - The activation key is consumed so it never appears as an unwanted character in the chat draft.
  - Preserves existing text entered before voice activation.
  - If chat closes unexpectedly during recording, safely stops and preserves draft without sending incomplete text.

- **Stop & Draft (`P`) & Escape**:
  - `P` stops active recording in either mode, finalizes transcription, and applies Automatic Send setting.
  - `Escape` safely cancels pending hold or active recording without sending.

### 3.2 Recording Session Lifecycle & Automatic Send Setting
- **Recording Session Lifecycle (`RecordingSession`)**:
  - Each recording session receives a strictly unique, monotonically increasing session ID (`sessionIdCounter.getAndIncrement()`).
  - Thread-safe state machine: `STARTING` -> `RECORDING` -> `FINALIZING` -> `COMPLETED` / `CANCELLED`.
  - Atomic guards (`finalized`, `submitted`) prevent duplicate finalization and guarantee single submission.
  - Stale and delayed callbacks from previous sessions are strictly rejected when session ID does not match the active session.
  - Rapid start/stop events queue a pending stop action (`requestStop`), ensuring immediate safe termination without stuck recording states.
- **Snapshot Policy**:
  - The `automaticSend` setting is captured as an immutable snapshot at the exact instant recording begins (`autoSendSnapshot`).
  - Toggling `automaticSend` in settings during an in-flight recording does not affect the active session, eliminating unpredictable behavior.
- **Automatic Send: OFF (Default)**:
  - Stops capture safely, waits for speech recognition finalization.
  - Transfers complete non-empty transcript into Minecraft chat as an editable draft (`chatBridge.openChat(finalDraft)`), allowing player review and editing.
  - Never automatically submits a message; sends only upon manual Enter key press.
- **Automatic Send: ON**:
  - Stops recording, flushes recognition engine.
  - Validates that finalized text is non-empty and non-blank.
  - Submits the finalized message via Minecraft chat submission pathway (`chatBridge.sendChatMessage(toSend)`) exactly once.
  - Never submits partial, empty, or failed speech results.
  - Minecraft client thread safety: all chat operations execute via `clientThreadExecutor.execute(...)`.

### 3.3 Dual-Model Hindi + English Recognition & Merging Pipeline
- **Dual-Model Execution on Single Audio Stream**:
  - The microphone streams audio to a synchronized recording buffer.
  - **Whisper Hindi2Hinglish Apex**: Processes the audio to recognize Hindi speech and code-switched Hindi-English, producing natural Roman Hindi transcription with word timing and positions.
  - **Whisper Small**: Independently processes the same audio to recognize English words, English pronunciation, and exact word timestamps.
- **Word-Level Alignment & Classification (`DualModelAlignmentMerger`)**:
  - Compares Hindi model Romanized words with English model candidate words.
  - Uses word-level timestamps and sequence position to align words across both recognition streams.
  - Distinguishes genuine English words from Romanized Hindi words:
    - Genuine English words (including short functional words `is`, `it`, `in`, `on`, `the`, `can`, `you`, `please`, `help`, `me`, `and`, `to`, `remove`, `problem`, `server`, etc., and proper nouns/names like `Ashish`) retain precise English spelling at their exact spoken positions.
    - Hindi words retain their natural Romanized phonetic output (`mujhe`, `yahan`, `se`, `ye`, `samaan`, `hatwana`, `hai`, `mera`, `naam`, etc.).
  - Deduplicates words recognized concurrently by both models for the same audio segment.
  - Preserves legitimate repeated words (e.g. `I, I, I want to remove this` -> `I, I, I want to remove this`).
  - Seamless fallback: if either model is missing or fails, gracefully outputs the result from the active working model.

### 3.4 Supported Speech Models (Only Two Models)
1. **Whisper Hindi2Hinglish Apex (`whisper-hindi2hinglish-apex`)**:
   - Model ID: `whisper-hindi2hinglish-apex` (Default Model).
   - Primary Hindi and Hinglish speech recognition engine.
   - Downloads verified Kaldi/Vosk Hindi acoustic model (`vosk-model-small-hi-0.22.zip`, MD5 `ab7280bbcf29e334f7e3b2a9ac0ca386`, SHA-256 `be07e048e1e599ad46341c08d2a135645097a538221678b7a0dd1b1919c6e1b21`).
   - Roman Hindi output directly enabled.
2. **Whisper Small (`whisper-small`)**:
   - Model ID: `whisper-small`.
   - Secondary English recognition and word identification engine.
   - Downloads verified Kaldi/Vosk small English model (`vosk-model-small-en-us-0.15.zip`, MD5 `80f1265262c9a8a515f2707498e1b485`).
   - Fast, low-latency, offline recognition providing word-level timestamps for English candidate identification.
3. **All Other Models Removed**:
   - Removed: Whisper Large-v3, Large-v3 Turbo, Whisper Medium, Whisper Base, Whisper Tiny, Vosk German, Spanish, French, Italian, Portuguese, Russian, AI4Bharat IndicConformer, Shruti.
   - Preserves user files on disk; removes catalog entries and unused engine mappings.

### 3.5 Non-Blocking Asynchronous Audio & Session Lifecycle (V-Key Freeze Fix)
- **Zero Client-Thread Blocking**: All microphone acquisition, Java Sound `TargetDataLine` opening, Vosk model initialization, and recognizer start/reset operations execute asynchronously on a dedicated daemon background executor (`recordingExecutor`).
- **Non-Blocking Key Release & Finalization**: The Minecraft client thread never blocks in `CountDownLatch.await(...)` or waits synchronously on audio flush. Finalization wait (up to 1000ms timeout) and text formatting run asynchronously on `recordingExecutor`, with the finalized message dispatched safely back to the client thread via `clientThreadExecutor.execute(...)`.
- **Pre-warmed Model & Lazy Capture Management**: The speech model is preloaded in the background on startup or model change; capture line startup avoids full system device re-enumeration where feasible.
- **Lock Isolation**: Synchronization between audio streaming, recognizer state, and Minecraft tick loop uses isolated, non-contending locks or atomic primitives.

### 3.6 Speech Recognition & Context-Aware Automatic Text Correction
- **Natural Mixed Script Support**: Preserves both Devanagari Hindi and English words where the speaker naturally mixes them (e.g. `मुझे food चाहिए`, `ये एक test build है`). Forced transliteration to Roman script is restricted to explicit Romanized Hinglish mode and never destroys English loanwords, commands, or numbers.
- **Context-Aware Automatic Correction Layer (`TranscriptionCorrectionService`)**:
  - Operates as a separate component between speech recognition and final text commitment.
  - Automatically corrects obvious transcription mistakes in finalized recognized text before commitment to Minecraft chat (e.g., `phood` -> `food` in Devanagari/Hindi context, `bil` -> `build` in test/gaming context).
  - Uses conservative context matching: corrects only when surrounding tokens (n-grams/bi-grams) strongly support the correction; leaves uncertain words untouched.
  - Never modifies existing drafts, manually typed text, or commands (`/tp`, `/give`, etc.).
  - Preserves legitimate repetitions, spacing, and punctuation.

### 3.7 Persistent Personalized Learning Store (`PersonalizedLearningStore`)
- **Persistent Local Store**: Saved to `.minecraft/voice2text/learned_corrections.json`.
- **Learned Correction Mappings**: Stores mappings from recognized mistake to confirmed intended word/phrase, with optional n-gram context and occurrence count.
- **Bounded Storage & LRU Eviction**: Caps storage at 1,000 entries; uses normalized keys while preserving the target script, case, and accents.
- **Explicit Confirmation Only**: Learns only from confirmed user actions (such as edited chat drafts or settings management), never unconfirmed raw ASR output.
- **Resilient & Reset-Capable**: Gracefully recovers from malformed/corrupted files by backing them up to `.bak` and initializing a fresh store. Supports resetting learned corrections independently of main configuration.

### 3.8 Runtime Resource & Performance Optimization
- **Bounded Audio Queue & Backpressure**: Streams audio chunks through a bounded queue to prevent memory growth under slow recognition.
- **Allocation & Regex Hygiene**: All regex patterns and JSON keys are precompiled and statically cached; hot paths avoid byte array copies and repeated JSON parsing.
- **Clean Resource Shutdown**: Native Vosk objects (`Model`, `Recognizer`) and background executors are gracefully terminated via shutdown hooks and client disconnect listeners.

---

## 4. Verification & Acceptance Criteria

1. Automated unit test suite passes:
   - Conflict-aware single-tap and double-tap behaviors.
   - Hold-to-Talk press and release.
   - Audio device enumeration and fallback.
   - Model manager discovery, verification, extraction, and error handling.
   - Transcription buffer reconciliation and deduplication.
2. Clean compilation and packaging for Minecraft 26.3 and 26.2.
3. Verification in Minecraft runtime environment.
