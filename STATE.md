# Project State: SpeakCraft — Minecraft Real-Time Voice-to-Text Client Mod

**Last Updated:** 2026-10-28T05:45:00+05:30  
**Phase:** Priority 1 & 2 Completed — Corrupted Hindi/Hinglish Transliteration & Automatic Send Lifecycle  
**Status:** COMPLETED & EMPIRICALLY VERIFIED (101/101 Tests Passing)

---

## 1. Completion Checklist

- [x] **Priority 1: Fix Corrupted Hindi and Romanized Hinglish Transcription**:
  - Identified root causes:
    1. **Windows Native JNA Encoding Mojibake:** On Windows, native C-strings returned by Vosk through JNA default to the platform ANSI code page (Windows-1252), causing UTF-8 Devanagari byte sequences to be misdecoded into extended Latin/ASCII mojibake (e.g. `à¤•à¥ˆà¤¸à¥‡`).
    2. Missing gaming loanwords in `LOANWORDS_MAP` (e.g. `वीसी` -> `VC`, `डिसकॉर्ड` -> `discord`, `यूट्यूब` -> `youtube`, `व्हाट्सएप` -> `whatsapp`, `पीवीपी` -> `pvp`, `लैग` -> `lag`).
    3. Missing colloquial Hindi pronouns and verbs in `COMMON_HINDI_MAP` (`मैं`, `मै` -> `main`, `उसका`, `सकते`, etc.).
    4. Unhandled Hindi Purna Viram danda (`।` U+0964 and `॥` U+0965) which should map to standard Roman punctuation (`.`).
  - Implemented fixes:
    - Added `System.setProperty("jna.encoding", "UTF-8")` in `VoiceToTextMod` and `VoskSpeechEngine`.
    - Implemented `repairMojibake(input)` in `HinglishTransliterationService` to detect and repair Windows-1252 / ISO-8859-1 misdecoded UTF-8 sequences.
    - Added comprehensive mapping for gaming loanwords, Hindi slang, colloquial terms, and Hindi Danda punctuation.
    - Updated `VoiceToTextController` to automatically detect Devanagari/mojibake and transliterate to Romanized Hinglish in both partial and final callbacks.
    - Added developer diagnostic logging (`[SpeakCraft-Diagnostic] [AudioFormat]`, `[ASR-Partial]`, `[ASR-Final]`, `[Pre-Insertion]`, `[Chat-Submission]`).
    - Verified all 6 target sentences and edge cases in `HinglishTransliterationTest`:
      - "कैसे हो" -> `kaise ho`
      - "भाई गेम में आ जाओ" -> `bhai game mein aa jao`
      - "क्या कर रहे हो" -> `kya kar rahe ho`
      - "मेरा सर्वर चालू नहीं हो रहा" -> `mera server chalu nahi ho raha`
      - "जल्दी वीसी में आ जाओ" -> `jaldi VC mein aa jao`
      - "मैं अभी Minecraft खेल रहा हूँ" -> `main abhi Minecraft khel raha hoon`

- [x] **Priority 2: Fix Voice Activation + Automatic Send Lifecycle**:
  - Identified root causes:
    1. Releasing the key stopped audio capture and immediately finalized the session before the asynchronous Vosk speech recognition engine emitted `onFinalResult`.
    2. Lack of an asynchronous synchronization barrier (`CountDownLatch`) causing empty or truncated partial transcripts to be submitted or discarded.
    3. Lack of explicit stop-action propagation when `stopAndSend()` was directly invoked with non-default configs.
  - Implemented fixes:
    - Added `CountDownLatch` and `finalResultReceived` in `RecordingSession`.
    - Added `awaitFinalResult(1000)` in `stopAndSend()` and `stopAndKeepDraft()` if speech was detected.
    - Added `markFinalizing(boolean autoSend)` and `autoSendEffective` in `RecordingSession` ensuring that the intended stop policy is honored throughout finalization.
    - Ensured single-submission guarantee with atomic `markSubmitted()` idempotency guard.
    - Preserved both recording modes (Mode A: 500ms Hold-to-Activate with gameplay movement, Mode B: Chat-Only Toggle).
    - Preserved Layout 2 UI (4-dot microphone indicator, grey idle, green animated audio wave, amber finalizing).

---

## 2. Verified Deliverables

1. **Release JAR (Minecraft 26.3)**: `d:\mybuilds\voice2text\build\libs\voice2text-1.0.0+mc26.3.jar` (27.85 MB, bundled with Vosk 0.3.45, JNA 5.14.0, and native DLLs)
2. **Release JAR (Minecraft 26.2)**: `d:\mybuilds\voice2text\build\libs\voice2text-1.0.0+mc26.2.jar`
3. **Sources JAR**: `d:\mybuilds\voice2text\build\libs\voice2text-1.0.0+mc26.3-sources.jar`

---

## 3. Empirical Test Results

```
com.voice2text.VoiceToTextControllerTest: 16 tests, 0 failures, 0 errors
com.voice2text.audio.AudioDeviceManagerTest: 3 tests, 0 failures, 0 errors
com.voice2text.audio.AudioLevelMeterTest: 4 tests, 0 failures, 0 errors
com.voice2text.config.ConfigManagerTest: 4 tests, 0 failures, 0 errors
com.voice2text.input.InputStateMachineConflictAndHoldTest: 5 tests, 0 failures, 0 errors
com.voice2text.input.InputStateMachineTest: 12 tests, 0 failures, 0 errors
com.voice2text.input.KeybindManagerTest: 6 tests, 0 failures, 0 errors
com.voice2text.model.ModelManagerDiscoveryAndIntegrityTest: 3 tests, 0 failures, 0 errors
com.voice2text.model.ModelManagerDownloadTest: 3 tests, 0 failures, 0 errors
com.voice2text.model.ModelManagerTest: 9 tests, 0 failures, 0 errors
com.voice2text.transcription.HinglishTransliterationTest: 20 tests, 0 failures, 0 errors
com.voice2text.transcription.TranscriptionBufferTest: 8 tests, 0 failures, 0 errors
com.voice2text.ui.ListeningOverlayTest: 8 tests, 0 failures, 0 errors

Total: 101 tests passed (100% success rate)
Gradle build: SUCCESSFUL
```

