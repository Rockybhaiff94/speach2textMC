# SpeakCraft — Minecraft Real-Time Voice-to-Text Client Mod

[![Minecraft 26.2 & 26.3](https://img.shields.io/badge/Minecraft-26.2%20%7C%2026.3-brightgreen.svg)]()
[![ModLoader Fabric](https://img.shields.io/badge/ModLoader-Fabric-blue.svg)]()
[![License Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)]()
[![Offline-First ASR](https://img.shields.io/badge/ASR-Offline%20First%20(Vosk)-purple.svg)]()

A high-performance, accessibility-focused, **client-side only** Minecraft mod that converts your spoken voice into text directly in the Minecraft chat box in real time using local offline neural speech recognition.

---

## 1. Key Features

- **Dedicated SpeakCraft Controls Category**: Integrated natively into Minecraft's controls menu (`Options → Controls → Key Binds → SpeakCraft`) with conflict detection and real-time bidirectional synchronization.
- **Dual Recording Modes**:
  - **Activation Mode (Press to Start, Press Again to Stop)**: Configurable activation key (default `T`). Automatically detects conflicts against registered Minecraft keybinds (such as vanilla chat). If conflicting, double-tap activates SpeakCraft without triggering the vanilla action, while a single tap preserves the vanilla action. If unconflicted, single-tap starts and single-tap stops.
  - **Hold-to-Talk Mode (Push-to-Talk)**: Dedicated key (default `V`). Press and hold to record continuously; release to stop, finalize the transcription, and insert it as a draft into chat.
- **Live Real-Time Streaming Dictation**: Recognized words appear progressively as you speak with low latency, just like live voice dictation in modern AI chat applications.
- **Player Review & Never Auto-Send**: Recording does NOT prematurely open chat while you are playing. When recording stops, the finalized speech is placed safely into Minecraft's chat input as a draft. The player reviews the message and presses `Enter` to send.
- **Live Microphone Waveform HUD**:
  - Positioned discreetly in the bottom-right of the screen just above the chat area.
  - Features a subtle listening indicator, animated vertical EQ bars driven by real incoming microphone RMS levels, and a live transcription preview.
  - Automatically hidden when inactive.
- **Dedicated `.minecraft/voice2text/` Directory**:
  - Clean directory structure (`models/`, `downloads/`, `temp/`, `logs/`, `config.json`).
  - Automatic migration from legacy config directories.
  - Automatic discovery of compatible models manually placed into `voice2text/models/`.
- **Physical & Virtual Microphone Selection**:
  - Real device enumeration using Java Sound `TargetDataLine` lines (16kHz 16-bit mono PCM).
  - Dropdown selector with System Default, discovered microphones, and refresh capability.
  - Graceful fallback with player notifications if a device is disconnected.
- **Language & Speech Model Dropdown**:
  - Multi-language catalog: English (US), English (India), German, Spanish, French, Italian, Portuguese, Russian, and Hindi.
  - Contextual Download action showing model download size, progress bar, dual MD5 and SHA-256 integrity verification, safe extraction with Zip Slip protection, and manual download fallback links.
- **Offline-First & Privacy Preserving**: Speech recognition runs 100% locally on your machine using Kaldi/Vosk neural models. **No audio ever leaves your computer.**
- **Universal Multiplayer Compatibility**: Works on single-player, LAN worlds, Realms, and multiplayer servers. **No server-side mod, plugin, proxy, or server operator permission is required.**

---

## 2. Keyboard Controls & Dedicated Category

SpeakCraft registers all 4 controls as genuine Minecraft key mappings inside the dedicated **SpeakCraft** category:

`Options → Controls → Key Binds → SpeakCraft`

| Control | Default Key | Mode / State | Behavior |
| :--- | :--- | :--- | :--- |
| **Activation Key** | `T` (or remapped) | Activation Mode | **With conflict (e.g. Vanilla Chat)**: Double-tap to start/stop SpeakCraft. Single tap opens vanilla chat.<br>**Without conflict**: Single tap starts recording; single tap stops recording. |
| **Hold-to-Talk (Push-to-Talk)** | `V` (or remapped) | Hold-to-Talk Mode | Press and hold to record continuously. Release to stop and insert finalized draft into chat. |
| **Stop & Draft Key** | `P` | Active Recording | Stops listening immediately in either mode, commits the transcription to the chat field, and preserves draft for editing. |
| **Config Hotkey** | `O` | In-Game | Opens the SpeakCraft settings, microphone selector, and speech model manager screen. |
| **Cancel Capture** | `Escape` | Active Recording | Immediately stops microphone capture without sending. Preserves existing draft. |

---

## 3. Dedicated Folder Structure

All SpeakCraft files are stored in the active Minecraft game directory:

```
.minecraft/
└── voice2text/
    ├── config.json          # Mod configuration (mic, language, keys, formatting)
    ├── models/              # Installed speech recognition models
    │   └── vosk-model-small-en-us-0.15/
    │       ├── am/
    │       ├── conf/
    │       └── graph/
    ├── downloads/           # Asynchronous download archive cache
    ├── temp/                # Staging folder for verified extractions
    └── logs/                # Safe diagnostic logs (no private speech stored)
```

---

## 4. Supported Versions & Artifacts

| Target Minecraft Version | Mod Loader | Release JAR Output Path |
| :--- | :--- | :--- |
| **Minecraft 26.3** | Fabric Loader 0.19.5+ / 0.20.1+ | [`build/libs/voice2text-1.0.0+mc26.3.jar`](file:///d:/mybuilds/voice2text/build/libs/voice2text-1.0.0+mc26.3.jar) |
| **Minecraft 26.2** | Fabric Loader 0.19.3+ | [`build/libs/voice2text-1.0.0+mc26.2.jar`](file:///d:/mybuilds/voice2text/build/libs/voice2text-1.0.0+mc26.2.jar) |

---

## 5. Automated Tests & Build Verification

```bash
# Run all automated unit tests (58 passing tests)
./gradlew test

# Build for Minecraft 26.3
./gradlew build

# Run in development environment
./gradlew runClient
```

---

## 6. License

- Source code: **Apache License 2.0**
- Offline speech recognition models (Vosk / Kaldi): Apache License 2.0 / CC-BY-SA 4.0
