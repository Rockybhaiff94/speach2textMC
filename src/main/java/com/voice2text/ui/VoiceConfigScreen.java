package com.voice2text.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.VoiceToTextMod;
import com.voice2text.audio.AudioDeviceManager;
import com.voice2text.config.VoiceToTextConfig;
import com.voice2text.input.KeybindManager;
import com.voice2text.model.DownloadProgressListener;
import com.voice2text.model.ModelManager;
import com.voice2text.model.SpeechModelInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game configuration screen allowing players to configure Voice2Text voice options:
 * - Recording Mode (Hold-to-Talk vs Toggle Recording)
 * - Automatic Send (OFF by default)
 * - Keybinds and clear vanilla conflict warnings
 * - Microphone selection and refresh
 * - Simplified, direct AI Model downloads & activation (Whisper Small & Whisper Hindi2Hinglish Apex)
 */
public class VoiceConfigScreen extends Screen {

    public static final String MODEL_WHISPER_SMALL = "whisper-small";
    public static final String MODEL_APEX = "whisper-hindi2hinglish-apex";

    public enum SelectedKeybind {
        NONE,
        ACTIVATION,
        STOP_DRAFT,
        TOGGLE,
        CONFIG
    }

    private final Screen parent;
    private final VoiceToTextConfig config;
    private final ModelManager modelManager;
    private SelectedKeybind selectedKeybind = SelectedKeybind.NONE;

    // Download state tracking
    private String downloadStatus = "";
    private int downloadPercent = -1;
    private long bytesDownloaded = 0;
    private long totalBytesExpected = 0;
    private boolean isDownloading = false;
    private String downloadingModelId = null;

    private final List<String> availableMics = new ArrayList<>();
    private int currentMicIndex = 0;

    public VoiceConfigScreen(Screen parent) {
        super(Component.literal("Voice2Text Settings"));
        this.parent = parent;

        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        this.config = mod != null ? mod.getConfigManager().getConfig() : new VoiceToTextConfig();
        this.modelManager = mod != null ? mod.getModelManager() : new ModelManager(new java.io.File("."));

        // Sync keybinds from Minecraft options
        if (mod != null && mod.getKeybindManager() != null) {
            mod.getKeybindManager().syncFromMinecraftKeyMappings();
        }

        // Discover locally installed models
        if (modelManager != null) {
            modelManager.discoverInstalledModels();
        }

        // Discover microphones
        refreshMicrophoneList();
    }

    private void refreshMicrophoneList() {
        availableMics.clear();
        availableMics.addAll(AudioDeviceManager.getAvailableInputDevices());
        if (availableMics.isEmpty()) {
            availableMics.add(AudioDeviceManager.SYSTEM_DEFAULT);
        }
        int found = availableMics.indexOf(config.getSelectedMicrophone());
        this.currentMicIndex = found >= 0 ? found : 0;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 24;
        int btnW = 185;
        int btnH = 20;
        int rowSpacing = 22;

        // --- ROW 1: Recording Mode & Automatic Send ---
        String recMode = config.getRecordingMode();
        String modeLabel;
        String modeTip;
        if ("CHAT_TOGGLE".equalsIgnoreCase(recMode) || "TOGGLE".equalsIgnoreCase(recMode)) {
            modeLabel = "Mode: §aChat-Only Toggle (T)";
            modeTip = "Mode B: Open chat, press T to start recording, press T again to stop (zero delay).";
        } else if ("HOLD_TO_ACTIVATE".equalsIgnoreCase(recMode) || "HOLD_TO_TALK".equalsIgnoreCase(recMode)) {
            modeLabel = "Mode: §eGameplay V Hold (500ms)";
            modeTip = "Mode A: Hold V for 500ms during gameplay to speak while moving; release to stop.";
        } else {
            modeLabel = "Mode: §bBoth Modes (V + Chat T)";
            modeTip = "Both Mode A (Hold V for 500ms in gameplay) and Mode B (Toggle T in chat) active.";
        }

        addRenderableWidget(Button.builder(
                Component.literal(modeLabel),
                button -> {
                    String current = config.getRecordingMode();
                    if ("DUAL".equalsIgnoreCase(current) || current == null) {
                        config.setRecordingMode("HOLD_TO_ACTIVATE");
                    } else if ("HOLD_TO_ACTIVATE".equalsIgnoreCase(current) || "HOLD_TO_TALK".equalsIgnoreCase(current)) {
                        config.setRecordingMode("CHAT_TOGGLE");
                    } else {
                        config.setRecordingMode("DUAL");
                    }
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal(modeTip)))
         .bounds(centerX - btnW - 4, startY, btnW, btnH).build());

        boolean autoSend = config.isAutomaticSend();
        String sendLabel = "Automatic Send: " + (autoSend ? "§aON" : "§cOFF");
        addRenderableWidget(Button.builder(
                Component.literal(sendLabel),
                button -> {
                    config.setAutomaticSend(!config.isAutomaticSend());
                    VoiceToTextMod mod = VoiceToTextMod.getInstance();
                    if (mod != null && mod.getController() != null) {
                        mod.getController().getInputStateMachine().setAutomaticSendEnabled(config.isAutomaticSend());
                    }
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("When enabled, dictated messages are sent automatically when recording ends.")))
         .bounds(centerX + 4, startY, btnW, btnH).build());

        // --- ROW 2: Keybinds (Chat Toggle Key & Gameplay Hold Key) ---
        String actMsg = selectedKeybind == SelectedKeybind.ACTIVATION ? "Chat Toggle: §e> Press Key <" : "Chat Toggle Key: §b[ " + config.getActivationKey() + " ]";
        addRenderableWidget(Button.builder(
                Component.literal(actMsg),
                button -> {
                    selectedKeybind = (selectedKeybind == SelectedKeybind.ACTIVATION) ? SelectedKeybind.NONE : SelectedKeybind.ACTIVATION;
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Key to toggle recording while inside Minecraft chat. Default: T")))
         .bounds(centerX - btnW - 4, startY + rowSpacing, btnW, btnH).build());

        String httMsg = selectedKeybind == SelectedKeybind.TOGGLE ? "Gameplay Hold: §e> Press Key <" : "Gameplay Hold Key: §b[ " + config.getDedicatedToggleKey() + " ]";
        addRenderableWidget(Button.builder(
                Component.literal(httMsg),
                button -> {
                    selectedKeybind = (selectedKeybind == SelectedKeybind.TOGGLE) ? SelectedKeybind.NONE : SelectedKeybind.TOGGLE;
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Key to hold for 500ms during gameplay to speak while moving. Default: V")))
         .bounds(centerX + 4, startY + rowSpacing, btnW, btnH).build());

        // --- ROW 3: Stop & Draft Key & Settings Key ---
        String stopMsg = selectedKeybind == SelectedKeybind.STOP_DRAFT ? "Stop & Draft: §e> Press Key <" : "Stop & Draft Key: §b[ " + config.getStopKey() + " ]";
        addRenderableWidget(Button.builder(
                Component.literal(stopMsg),
                button -> {
                    selectedKeybind = (selectedKeybind == SelectedKeybind.STOP_DRAFT) ? SelectedKeybind.NONE : SelectedKeybind.STOP_DRAFT;
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Press during recording to stop and preserve draft in chat. Default: P")))
         .bounds(centerX - btnW - 4, startY + (rowSpacing * 2), btnW, btnH).build());

        String cfgMsg = selectedKeybind == SelectedKeybind.CONFIG ? "Settings: §e> Press Key <" : "Settings Key: §b[ " + config.getConfigKey() + " ]";
        addRenderableWidget(Button.builder(
                Component.literal(cfgMsg),
                button -> {
                    selectedKeybind = (selectedKeybind == SelectedKeybind.CONFIG) ? SelectedKeybind.NONE : SelectedKeybind.CONFIG;
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Hotkey to open Voice2Text Settings. Default: O")))
         .bounds(centerX + 4, startY + (rowSpacing * 2), btnW, btnH).build());

        // --- ROW 4: Microphone Selection & Refresh ---
        String currentMic = availableMics.get(currentMicIndex);
        boolean micAvailable = AudioDeviceManager.isDeviceAvailable(currentMic, new javax.sound.sampled.AudioFormat(16000.0f, 16, 1, true, false));
        String micDisplayName = currentMic;
        if (micDisplayName.length() > 24) {
            micDisplayName = micDisplayName.substring(0, 22) + "..";
        }
        String micBtnText = "Mic: §f" + micDisplayName + (micAvailable ? "" : " §c(Offline)");

        addRenderableWidget(Button.builder(
                Component.literal(micBtnText),
                button -> {
                    currentMicIndex = (currentMicIndex + 1) % availableMics.size();
                    String selected = availableMics.get(currentMicIndex);
                    config.setSelectedMicrophone(selected);
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Click to cycle input microphones. Device: " + currentMic)))
         .bounds(centerX - btnW - 4, startY + (rowSpacing * 3), btnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.literal("⟳ Refresh Microphones"),
                button -> {
                    refreshMicrophoneList();
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Re-scans available operating system physical and virtual audio input devices.")))
         .bounds(centerX + 4, startY + (rowSpacing * 3), btnW, btnH).build());

        // --- ROW 5: Reset Keybinds & Dual-Model Recognition Toggle ---
        addRenderableWidget(Button.builder(
                Component.literal("§7Reset Keybinds"),
                button -> {
                    VoiceToTextMod mod = VoiceToTextMod.getInstance();
                    if (mod != null && mod.getKeybindManager() != null) {
                        mod.getKeybindManager().resetToDefaults();
                        mod.getConfigManager().save(config);
                    }
                    selectedKeybind = SelectedKeybind.NONE;
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Restores default key mappings: T for chat activation, V for gameplay hold, P for stop & draft, O for settings.")))
         .bounds(centerX - btnW - 4, startY + (rowSpacing * 4), btnW, btnH).build());

        boolean dualEnabled = config.isDualModelEnabled();
        String dualLabel = "Dual-Model Mode: " + (dualEnabled ? "§aENABLED" : "§cDISABLED");
        addRenderableWidget(Button.builder(
                Component.literal(dualLabel),
                button -> {
                    config.setDualModelEnabled(!config.isDualModelEnabled());
                    VoiceToTextMod mod = VoiceToTextMod.getInstance();
                    if (mod != null) {
                        mod.getConfigManager().save(config);
                    }
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("When enabled, Whisper Small (English) and Apex (Hindi) run concurrently in parallel on the same audio.")))
         .bounds(centerX + 4, startY + (rowSpacing * 4), btnW, btnH).build());

        // --- ROW 6: AVAILABLE AI MODELS (Simple Direct Buttons & Enable Toggles) ---
        int modelsY = startY + (rowSpacing * 5) + 14;
        int actionBtnW = 125;
        int toggleBtnW = 56;

        // Model 1: Whisper Small (English)
        boolean smallInstalled = modelManager.isModelInstalled(MODEL_WHISPER_SMALL);
        boolean smallIsDownloading = isDownloading && MODEL_WHISPER_SMALL.equals(downloadingModelId);
        boolean smallEnabled = config.isWhisperSmallEnabled();

        String smallBtnLabel;
        if (smallIsDownloading) {
            smallBtnLabel = "§c✕ Cancel (" + (downloadPercent >= 0 ? downloadPercent + "%" : "...") + ")";
        } else if (smallInstalled) {
            smallBtnLabel = "⚙ Manage Small";
        } else {
            smallBtnLabel = "⬇ Download Small";
        }

        addRenderableWidget(Button.builder(
                Component.literal(smallBtnLabel),
                button -> {
                    if (smallIsDownloading) {
                        cancelModelDownload(MODEL_WHISPER_SMALL);
                    } else if (smallInstalled) {
                        if (this.minecraft != null) {
                            this.minecraft.gui.setScreen(new ModelManageScreen(this, MODEL_WHISPER_SMALL));
                        }
                    } else {
                        startModelDownload(MODEL_WHISPER_SMALL);
                    }
                }
        ).tooltip(Tooltip.create(Component.literal(
                smallIsDownloading ? "Click to cancel the ongoing download."
                : (smallInstalled ? "Click to view details, reinstall, or permanently delete Whisper Small."
                : "Download OpenAI Whisper Small model (~488 MB) for accurate English speech recognition."))))
         .bounds(centerX - btnW - 4, modelsY, actionBtnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.literal("Eng: " + (smallEnabled ? "§aON" : "§cOFF")),
                button -> {
                    config.setWhisperSmallEnabled(!config.isWhisperSmallEnabled());
                    VoiceToTextMod mod = VoiceToTextMod.getInstance();
                    if (mod != null) {
                        mod.getConfigManager().save(config);
                    }
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Enable or disable Whisper Small for English recognition.")))
         .bounds(centerX - btnW - 4 + actionBtnW + 4, modelsY, toggleBtnW, btnH).build());

        // Model 2: Whisper Hindi2Hinglish Apex (Hindi / Hinglish)
        boolean apexInstalled = modelManager.isModelInstalled(MODEL_APEX);
        boolean apexIsDownloading = isDownloading && MODEL_APEX.equals(downloadingModelId);
        boolean apexEnabled = config.isApexEnabled();

        String apexBtnLabel;
        if (apexIsDownloading) {
            apexBtnLabel = "§c✕ Cancel (" + (downloadPercent >= 0 ? downloadPercent + "%" : "...") + ")";
        } else if (apexInstalled) {
            apexBtnLabel = "⚙ Manage Apex";
        } else {
            apexBtnLabel = "⬇ Download Apex";
        }

        addRenderableWidget(Button.builder(
                Component.literal(apexBtnLabel),
                button -> {
                    if (apexIsDownloading) {
                        cancelModelDownload(MODEL_APEX);
                    } else if (apexInstalled) {
                        if (this.minecraft != null) {
                            this.minecraft.gui.setScreen(new ModelManageScreen(this, MODEL_APEX));
                        }
                    } else {
                        startModelDownload(MODEL_APEX);
                    }
                }
        ).tooltip(Tooltip.create(Component.literal(
                apexIsDownloading ? "Click to cancel the ongoing download."
                : (apexInstalled ? "Click to view details, reinstall, or permanently delete Whisper Hindi2Hinglish Apex."
                : "Download Whisper Hindi2Hinglish Apex model (~574 MB) for natural Hindi speech & Roman transliteration."))))
         .bounds(centerX + 4, modelsY, actionBtnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.literal("Hin: " + (apexEnabled ? "§aON" : "§cOFF")),
                button -> {
                    config.setApexEnabled(!config.isApexEnabled());
                    VoiceToTextMod mod = VoiceToTextMod.getInstance();
                    if (mod != null) {
                        mod.getConfigManager().save(config);
                    }
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Enable or disable Apex for Hindi/Hinglish speech recognition.")))
         .bounds(centerX + 4 + actionBtnW + 4, modelsY, toggleBtnW, btnH).build());

        // --- BOTTOM: Done Button ---
        addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> {
                    VoiceToTextMod mod = VoiceToTextMod.getInstance();
                    if (mod != null) {
                        mod.getConfigManager().save(config);
                    }
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(parent);
                    }
                }
        ).bounds(centerX - 100, this.height - 26, 200, 20).build());
    }

    private void switchActiveModel(String modelId) {
        config.setActiveModelId(modelId);
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod != null) {
            mod.getConfigManager().save(config);
            if (mod.getController() != null) {
                mod.getController().switchModel(modelId);
            }
        }
        SpeechModelInfo info = modelManager.getModelInfo(modelId);
        String name = info != null ? info.getDisplayName() : modelId;
        downloadStatus = "§aActivated speech engine: " + name;
        rebuildWidgets();
    }

    private void startModelDownload(String modelId) {
        if (isDownloading) {
            downloadStatus = "§cAnother download is already in progress. Please wait or cancel.";
            rebuildWidgets();
            return;
        }

        if (modelManager.isModelInstalled(modelId)) {
            downloadStatus = "§aModel is already installed.";
            rebuildWidgets();
            return;
        }

        SpeechModelInfo info = modelManager.getModelInfo(modelId);
        if (info == null) {
            downloadStatus = "§cUnknown model: " + modelId;
            rebuildWidgets();
            return;
        }

        isDownloading = true;
        downloadingModelId = modelId;
        downloadPercent = 0;
        bytesDownloaded = 0;
        totalBytesExpected = info.getDownloadSizeBytes();
        downloadStatus = "§eConnecting to " + info.getDisplayName() + "...";
        rebuildWidgets();

        modelManager.downloadModel(modelId, new DownloadProgressListener() {
            @Override
            public void onProgress(long bytesRead, long totalBytes, int percent) {
                bytesDownloaded = bytesRead;
                totalBytesExpected = totalBytes;
                downloadPercent = percent;
            }

            @Override
            public void onStatus(String statusMessage) {
                downloadStatus = statusMessage;
            }

            @Override
            public void onCompleted() {
                isDownloading = false;
                downloadingModelId = null;
                downloadStatus = "§a" + info.getDisplayName() + " verified & ready for offline dictation!";

                VoiceToTextMod mod = VoiceToTextMod.getInstance();
                if (mod != null && mod.getController() != null) {
                    mod.getController().switchModel(modelId);
                }

                if (minecraft != null) {
                    minecraft.execute(VoiceConfigScreen.this::rebuildWidgets);
                }
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
                isDownloading = false;
                downloadingModelId = null;
                downloadStatus = "§cDownload failed: " + errorMessage;
                if (minecraft != null) {
                    minecraft.execute(VoiceConfigScreen.this::rebuildWidgets);
                }
            }
        });
    }

    private void cancelModelDownload(String modelId) {
        if (modelManager != null) {
            modelManager.cancelDownload(modelId);
        }
        isDownloading = false;
        downloadingModelId = null;
        downloadPercent = -1;
        downloadStatus = "§eDownload cancelled by user.";
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selectedKeybind != SelectedKeybind.NONE) {
            int key = event.key();
            if (key != InputConstants.KEY_ESCAPE) {
                VoiceToTextMod mod = VoiceToTextMod.getInstance();
                KeybindManager km = mod != null ? mod.getKeybindManager() : null;
                if (km != null) {
                    InputConstants.Key keyObj = InputConstants.getKey(event);
                    String keyName = KeybindManager.resolveKeyName(keyObj != null ? keyObj : KeybindManager.createKey(key));
                    switch (selectedKeybind) {
                        case ACTIVATION -> km.setActivationKey(key, keyName, false);
                        case STOP_DRAFT -> km.setStopKey(key, keyName, false);
                        case TOGGLE -> km.setToggleKey(key, keyName, false);
                        case CONFIG -> km.setConfigKey(key, keyName, false);
                        case NONE -> {}
                    }
                    mod.getConfigManager().save(config);
                }
            }
            selectedKeybind = SelectedKeybind.NONE;
            rebuildWidgets();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleOrTripleClick) {
        if (selectedKeybind != SelectedKeybind.NONE) {
            int button = event.button();
            VoiceToTextMod mod = VoiceToTextMod.getInstance();
            KeybindManager km = mod != null ? mod.getKeybindManager() : null;
            if (km != null) {
                InputConstants.Key mouseKey = KeybindManager.createMouseKey(button);
                String keyName = KeybindManager.resolveKeyName(mouseKey);
                switch (selectedKeybind) {
                    case ACTIVATION -> km.setActivationKey(button, keyName, true);
                    case STOP_DRAFT -> km.setStopKey(button, keyName, true);
                    case TOGGLE -> km.setToggleKey(button, keyName, true);
                    case CONFIG -> km.setConfigKey(button, keyName, true);
                    case NONE -> {}
                }
                mod.getConfigManager().save(config);
            }
            selectedKeybind = SelectedKeybind.NONE;
            rebuildWidgets();
            return true;
        }

        return super.mouseClicked(event, isDoubleOrTripleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int btnW = 185;
        int startY = 24;
        int rowSpacing = 22;

        // Header Title
        extractor.centeredText(this.font, "§lVoice2Text Settings", centerX, 5, 0xFFFFFFFF);
        extractor.centeredText(this.font, "§7Real-Time Offline Speech-to-Text for Minecraft", centerX, 14, 0x90A4B0BE);

        // Section Title: Available AI Models
        int modelsY = startY + (rowSpacing * 5) + 14;
        extractor.centeredText(this.font, "§e── Available AI Models ──", centerX, modelsY - 11, 0xFFFFFFFF);

        String activeModelId = config.getActiveModelId();

        // Model 1 (Left) subtitle / status
        boolean smallInstalled = modelManager.isModelInstalled(MODEL_WHISPER_SMALL);
        boolean smallIsDownloading = isDownloading && MODEL_WHISPER_SMALL.equals(downloadingModelId);
        boolean smallEnabled = config.isWhisperSmallEnabled();
        String smallStatus = smallInstalled
                ? String.format("Installed: §aYes §7| Loaded: §aYes §7| %s", smallEnabled ? "§aActive" : "§7Standby")
                : (smallIsDownloading ? String.format("§e⬇ Downloading %d%%", Math.max(0, downloadPercent)) : "Status: §7Not Installed (~488 MB)");
        extractor.centeredText(this.font, smallStatus, centerX - (btnW / 2) - 4, modelsY + 22, 0xFFFFFFFF);

        // Model 2 (Right) subtitle / status
        boolean apexInstalled = modelManager.isModelInstalled(MODEL_APEX);
        boolean apexIsDownloading = isDownloading && MODEL_APEX.equals(downloadingModelId);
        boolean apexEnabled = config.isApexEnabled();
        String apexStatus = apexInstalled
                ? String.format("Installed: §aYes §7| Loaded: §aYes §7| %s", apexEnabled ? "§aActive" : "§7Standby")
                : (apexIsDownloading ? String.format("§e⬇ Downloading %d%%", Math.max(0, downloadPercent)) : "Status: §7Not Installed (~574 MB)");
        extractor.centeredText(this.font, apexStatus, centerX + (btnW / 2) + 4, modelsY + 22, 0xFFFFFFFF);

        // Dual-Model Parallel Status banner
        boolean dualOn = config.isDualModelEnabled();
        boolean smallOn = smallEnabled && smallInstalled;
        boolean apexOn = apexEnabled && apexInstalled;

        String dualStatusBanner;
        if (dualOn && smallOn && apexOn) {
            dualStatusBanner = "§a✔ Dual-Model Active: Whisper Small (English) + Apex (Hindi) running concurrently";
        } else if (smallOn) {
            dualStatusBanner = "§e⚠ Fallback Mode: Whisper Small Active (English Only)";
        } else if (apexOn) {
            dualStatusBanner = "§e⚠ Fallback Mode: Apex Active (Hindi & Hinglish Only)";
        } else {
            dualStatusBanner = "§c✖ No AI speech model active. Install a model to begin.";
        }
        extractor.centeredText(this.font, dualStatusBanner, centerX, modelsY + 36, 0xFFFFFFFF);

        // Display Keybind Conflicts or Duplicate Bindings
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        List<String> warnings = (mod != null && mod.getKeybindManager() != null)
                ? mod.getKeybindManager().getConflictWarnings(this.minecraft)
                : List.of();

        int footerY = this.height - 40;

        if (selectedKeybind != SelectedKeybind.NONE) {
            extractor.centeredText(this.font, "§e▶ Press any key to assign keybind, or ESC to cancel ◀", centerX, footerY, 0xFFFFD700);
        } else if (isDownloading) {
            String progressText;
            if (downloadPercent >= 0) {
                double curMb = (double) bytesDownloaded / (1024 * 1024);
                double totMb = (double) totalBytesExpected / (1024 * 1024);
                String modelName = MODEL_WHISPER_SMALL.equals(downloadingModelId) ? "Whisper Small" : "Apex";
                progressText = String.format("§eDownloading %s: %.1f MB / %.1f MB (%d%%)", modelName, curMb, totMb, downloadPercent);
            } else {
                progressText = downloadStatus;
            }
            extractor.centeredText(this.font, progressText, centerX, footerY, 0xFFFFFFFF);
        } else if (!downloadStatus.isEmpty()) {
            extractor.centeredText(this.font, downloadStatus, centerX, footerY, 0xFFFFFFFF);
        } else if (!warnings.isEmpty()) {
            String firstWarn = warnings.get(0);
            extractor.centeredText(this.font, "§e⚠ " + firstWarn, centerX, footerY - 4, 0xFFFFFF55);
            extractor.centeredText(this.font, "§7Pressing key will trigger Voice2Text. You can remap in Controls.", centerX, footerY + 6, 0xFFAAAAAA);
        } else {
            extractor.centeredText(this.font, "§7Hotkeys: T (Chat Toggle), V (Gameplay Hold), P (Stop & Draft), O (Settings)", centerX, footerY, 0x8090A0B0);
        }
    }

    @Override
    public void onClose() {
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod != null) {
            mod.getConfigManager().save(config);
        }
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
