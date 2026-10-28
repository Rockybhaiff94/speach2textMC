# SPECIFICATION: SpeakCraft — Minecraft Real-Time Voice-to-Text Client Mod

**Status:** FINALIZED  
**Author:** Senior Minecraft Fabric Developer, Audio Systems Engineer & Game UI/UX Designer  
**Date:** 2026-10-27  
**Target Environments:** Minecraft Java Edition 26.2, 26.3 (Fabric Mod Loader)

---

## 1. Executive Summary & Goals

SpeakCraft (`voice2text`) is an offline-first, client-side Fabric mod for Minecraft Java Edition that transcribes spoken player voice into Minecraft chat in real time.

Key User Experiences:
1. **Activation Mode (Press to Start, Press Again to Stop)**:
   - Configurable Activation Key (default `T`).
   - If the key conflicts with an existing Minecraft action (e.g., `T` opens chat), SpeakCraft detects the conflict against registered Minecraft `KeyMapping`s and uses a double-tap window (e.g. 300ms).
     - Double-tap: activates or stops SpeakCraft without triggering the conflicting Minecraft action.
     - Single tap without second press: defers the conflicting Minecraft action until the window expires, then executes it normally.
   - If the key has NO conflict, a single press starts recording and a single press stops recording.
2. **Hold-to-Talk Mode (Push-to-Talk)**:
   - Separate configurable key (default `V`), clearly labeled as **Hold-to-Talk (Push-to-Talk)**.
   - Press and hold to capture and stream speech recognition.
   - Release key to immediately stop recording and transfer the transcription into Minecraft chat as a draft.
   - Guard against key repeats, screen changes, window blur, and disconnects.
3. **Real-Time Streaming Dictation**:
   - Progressive partial recognition updates live as the player speaks, avoiding word duplication or repetition.
   - When chat is closed, recording does NOT open the chat screen. Instead, recognized text streams progressively in the bottom-right SpeakCraft HUD preview.
   - When recording stops, the finalized draft is inserted into Minecraft chat for the player to review.
   - **Never auto-send**: The player always presses `Enter` manually.
   - If chat is already open, the input text field updates progressively while preserving any existing player text.
4. **Live Waveform HUD**:
   - Compact, polished Minecraft-aesthetic HUD positioned in the bottom-right above the chat area.
   - Displays listening status dot (green listening, amber finalizing), animated vertical EQ bars driven by real incoming microphone RMS amplitude, and live recognized draft preview.
   - Hidden when inactive.
5. **Dedicated Directory & Storage**:
   - Active instance path `.minecraft/voice2text/` (`models/`, `downloads/`, `temp/`, `logs/`, `config.json`).
   - Automatic model discovery and validation in `voice2text/models/`.
   - Migration from legacy config locations (`.minecraft/config/speakcraft/` and `.minecraft/config/voice2text.json`).
6. **Microphone Device Selection**:
   - Enumeration of real physical and virtual recording lines from Java Sound (`TargetDataLine`).
   - Dropdown selection with "System Default" and specific input devices.
   - Safe fallback and disconnection notifications.
7. **Speech Model Catalog & Downloader**:
   - Multi-language catalog: English (US), English (India), German, Spanish, French, Italian, Portuguese, Russian, Hindi.
   - Verified official Vosk URLs, correct file sizes, and official checksums.
   - Resilient download pipeline with HTTP redirect support, streaming hash verification, staging directory extraction, and Zip Slip protection.
   - Manual download fallback link and instructions if automatic download fails.

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

### 3.3 Hinglish (Hindi-English) Speech Recognition & Romanization Pipeline
- **Target Output**: Natural Romanized Hindi mixed with English gaming vocabulary (e.g. `Bhai game mein aa jao.`, `Server par jaldi join karo.`, `Isko config mein change karna padega.`).
- **Offline Kaldi/Vosk Acoustic Engine**: Recognizes phonetics using `vosk-model-small-hi-0.22` or `vosk-model-small-hinglish` entirely offline.
- **Transliteration Pipeline (`HinglishTransliterationService`)**:
  - Preserves pure English words, numbers, Minecraft commands (`/tp`, `/give`), and technical symbols untouched.
  - Unicode NFC normalization aligns decomposed nuktas with dictionary entries.
  - Full candra vowel support: candra-O `ॉ` (0x0949) and `ऑ` (0x0911) -> `o` (fixes `block`, `login`, `voice`, `config`, `offline`); candra-E `ॅ` (0x0945) and `ऍ` (0x090D) -> `e`.
  - Nukta consonants: `ज़` -> `z`, `फ़` -> `f`, `क़` -> `q`, `ख़` -> `kh`, `ग़` -> `gh`, `ड़` -> `d`, `ढ़` -> `dh`.
  - Matra + anusvara combinations: `में` -> `mein`, `हैं` -> `hain`, `लोगों` -> `logon`, `कहाँ` -> `kahan`.
  - Schwa deletion: eliminates trailing schwa on final consonants (`kar` not `kara`) and medial schwa in `VC_CV` environments (`करना` -> `karna`, `पड़ेगा` -> `padega`, `चलना` -> `chalna`).
  - Gaming loanword dictionary: `game`, `server`, `minecraft`, `guys`, `voice`, `chat`, `config`, `change`, `open`, `join`, `survival`.

### 3.3 Green Audio-Reactive Dot Indicator with Grey Idle State & Transparent Background
- **Visual Design**: 4 small circular dots (4×4 px, 3 px spacing) arranged horizontally on the right side of the chat input.
- **100% Transparent Background**: No background rectangle, panel, card, border, or opaque overlay.
- **Grey Idle State**: All dots remain neutral grey (`#929292`) and stationary when recording is active without speech.
- **Animated Green Speech State**: Transitions smoothly to bright emerald green (`#39FF88`) with a vertical sinusoidal rise-and-fall wave animation and soft green glow when speech is detected.
- **Voice Activity Detection**: Audio-driven with hysteresis (`0.035` entrance threshold, `0.020` exit threshold) and a 280 ms release timeout to bridge natural gaps between words without flickering.
- **Dynamic Anchoring**: Positioned on the right side of the chat input `EditBox` (`boxRight - 8px margin`, vertically centered with chat line) across all GUI scales and resolutions.
- **Finalizing & Clean Inactive Hide**: Stationary amber dots during finalization (`#FFD600`), and clean disappearance when recording ends.

### 3.3 Audio Device Management
- Enumerates real input lines via `AudioSystem.getMixerInfo()` matching 16kHz 16-bit mono PCM.
- System Default is the primary option; specific devices saved in `config.json`.
- Missing/disconnected device falls back gracefully to System Default with a status notification.

### 3.4 Speech Models & Downloader
- Official Vosk small models catalog with verified MD5 and SHA-256 hashes.
- Downloader supports HTTP 301/302/307/308 redirects, user-agent headers, and timeout configurations.
- Staging directory unzipping with canonical path validation prevents Zip Slip vulnerabilities.
- Manual download fallback instructions provided on failure.

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
