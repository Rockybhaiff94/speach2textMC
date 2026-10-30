package com.voice2text.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.VoiceToTextMod;
import com.voice2text.audio.AudioDeviceManager;
import com.voice2text.config.VoiceToTextConfig;
import com.voice2text.input.KeybindManager;
import com.voice2text.model.DownloadProgressListener;
import com.voice2text.model.ModelInstallationState;
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
 * - Microphone selection (bottom-left) and language/model selection (bottom-right)
 * - General formatting options
 */
public class VoiceConfigScreen extends Screen {

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

    private String downloadStatus = "";
    private int downloadPercent = -1;
    private long bytesDownloaded = 0;
    private long totalBytesExpected = 0;
    private boolean isDownloading = false;

    private final List<String> availableModelIds = new ArrayList<>();
    private int currentModelIndex = 0;

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

        // Discover models
        if (modelManager != null) {
            modelManager.discoverInstalledModels();
            this.availableModelIds.addAll(modelManager.getAvailableModels().keySet());
        }
        if (availableModelIds.isEmpty()) {
            availableModelIds.add(ModelManager.DEFAULT_MODEL_ID);
        }
        this.currentModelIndex = Math.max(0, availableModelIds.indexOf(config.getActiveModelId()));

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
        int startY = 26;
        int btnW = 185;
        int btnH = 20;
        int rowSpacing = 22;

        // --- ROW 1: Recording Mode & Automatic Send ---
        // Left: Recording Mode (Hold-to-Talk vs Toggle Recording)
        // Left: Recording Mode Selector
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

        // Right: Automatic Send (OFF by default)
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
        ).tooltip(Tooltip.create(Component.literal("When enabled, dictated messages are sent automatically when recording ends. Review your message before enabling this option.")))
         .bounds(centerX + 4, startY, btnW, btnH).build());

        // --- ROW 2: Keybinds (Chat-Only Toggle Key & Gameplay Hold Key) ---
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

        // --- ROW 4: General Formatting (Capitalization & Voice Punctuation) ---
        addRenderableWidget(Button.builder(
                Component.literal("Capitalization: " + (config.isAutoCapitalization() ? "§aOn" : "§cOff")),
                button -> {
                    config.setAutoCapitalization(!config.isAutoCapitalization());
                    VoiceToTextMod mod = VoiceToTextMod.getInstance();
                    if (mod != null && mod.getController() != null) {
                        mod.getController().getTranscriptionBuffer().setAutoCapitalization(config.isAutoCapitalization());
                    }
                    button.setMessage(Component.literal("Capitalization: " + (config.isAutoCapitalization() ? "§aOn" : "§cOff")));
                }
        ).tooltip(Tooltip.create(Component.literal("Automatically capitalizes the first letter of recognized speech sentences.")))
         .bounds(centerX - btnW - 4, startY + (rowSpacing * 3), btnW, btnH).build());

        addRenderableWidget(Button.builder(
                Component.literal("Voice Punctuation: " + (config.isVoiceCommandsEnabled() ? "§aOn" : "§cOff")),
                button -> {
                    config.setVoiceCommandsEnabled(!config.isVoiceCommandsEnabled());
                    VoiceToTextMod mod = VoiceToTextMod.getInstance();
                    if (mod != null && mod.getController() != null) {
                        mod.getController().getTranscriptionBuffer().setVoiceCommandsEnabled(config.isVoiceCommandsEnabled());
                    }
                    button.setMessage(Component.literal("Voice Punctuation: " + (config.isVoiceCommandsEnabled() ? "§aOn" : "§cOff")));
                }
        ).tooltip(Tooltip.create(Component.literal("Translates spoken words like 'period', 'comma', 'question mark' into punctuation symbols.")))
         .bounds(centerX + 4, startY + (rowSpacing * 3), btnW, btnH).build());

        // --- ROW 5: Audio Input (Bottom-Left) & Speech Recognition (Bottom-Right) ---
        // Bottom-Left: Microphone Dropdown / Cycle
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
        ).tooltip(Tooltip.create(Component.literal("Click to cycle input microphones. Full device name: " + currentMic)))
         .bounds(centerX - btnW - 4, startY + (rowSpacing * 4), btnW, btnH).build());

        // Bottom-Right: Language / Model Selection
        String activeModelId = availableModelIds.get(currentModelIndex);
        SpeechModelInfo activeInfo = modelManager.getModelInfo(activeModelId);
        String modelTitle = activeInfo != null ? activeInfo.getDisplayName() : activeModelId;
        if (modelTitle.length() > 22) {
            modelTitle = modelTitle.substring(0, 20) + "..";
        }
        boolean isInstalled = modelManager.isModelInstalled(activeModelId);
        ModelInstallationState installState = modelManager.getModelInstallationState(activeModelId);

        addRenderableWidget(Button.builder(
                Component.literal("Lang: §f" + modelTitle + (isInstalled ? " §a✓" : " §c✗")),
                button -> {
                    currentModelIndex = (currentModelIndex + 1) % availableModelIds.size();
                    String newId = availableModelIds.get(currentModelIndex);
                    config.setActiveModelId(newId);

                    SpeechModelInfo newInfo = modelManager.getModelInfo(newId);
                    if (newInfo != null) {
                        config.setSpeechLanguage(newInfo.getLanguageCode());
                    }

                    if (modelManager.isModelInstalled(newId)) {
                        VoiceToTextMod mod = VoiceToTextMod.getInstance();
                        if (mod != null && mod.getController() != null) {
                            mod.getController().switchModel(newId);
                        }
                    }
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Click to cycle speech models. Active: " + (activeInfo != null ? activeInfo.getDisplayName() : activeModelId))))
         .bounds(centerX + 4, startY + (rowSpacing * 4), btnW, btnH).build());

        // --- ROW 6: Audio Refresh (Bottom-Left) & Model Action (Bottom-Right) ---
        addRenderableWidget(Button.builder(
                Component.literal("⟳ Refresh Microphones"),
                button -> {
                    refreshMicrophoneList();
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Re-scans available operating system physical and virtual audio input devices.")))
         .bounds(centerX - btnW - 4, startY + (rowSpacing * 5), btnW, btnH).build());

        String actionBtnText;
        if (installState.isBusy()) {
            actionBtnText = "§e" + installState.getDisplayLabel() + (downloadPercent >= 0 ? " " + downloadPercent + "%" : "");
        } else if (isInstalled) {
            actionBtnText = "§aReady ✓";
        } else if (installState == ModelInstallationState.ERROR) {
            actionBtnText = "§cRetry Download";
        } else {
            String sizeStr = activeInfo != null ? " (" + activeInfo.getFormattedDownloadSize() + ")" : "";
            actionBtnText = "§6Download" + sizeStr;
        }

        addRenderableWidget(Button.builder(
                Component.literal(actionBtnText),
                button -> {
                    if (installState.isBusy()) {
                        modelManager.cancelDownload(activeModelId);
                        isDownloading = false;
                        downloadStatus = "§7Download cancelled.";
                        rebuildWidgets();
                    } else if (!isInstalled || installState == ModelInstallationState.ERROR) {
                        startModelDownload();
                    } else {
                        VoiceToTextMod mod = VoiceToTextMod.getInstance();
                        if (mod != null && mod.getController() != null) {
                            mod.getController().switchModel(activeModelId);
                        }
                        downloadStatus = "§aModel is active and verified.";
                        rebuildWidgets();
                    }
                }
        ).tooltip(Tooltip.create(Component.literal(isInstalled ? "Model installed. Click to ensure active." : "Download offline neural speech model from verified official repositories.")))
         .bounds(centerX + 4, startY + (rowSpacing * 5), btnW, btnH).build());

        // --- ROW 7: Reset Keybinds (Left) & Output Script (Right) ---
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
        ).tooltip(Tooltip.create(Component.literal("Restores default key mappings: T for activation, V for Hold-to-Talk, P for stop & draft, O for settings.")))
         .bounds(centerX - btnW - 4, startY + (rowSpacing * 6), btnW, btnH).build());

        boolean isRoman = "ROMAN_HINDI".equalsIgnoreCase(config.getOutputScript());
        String scriptLabel = "Output: §b" + (isRoman ? "Roman Hindi (Hinglish)" : "Devanagari Script");
        addRenderableWidget(Button.builder(
                Component.literal(scriptLabel),
                button -> {
                    if ("ROMAN_HINDI".equalsIgnoreCase(config.getOutputScript())) {
                        config.setOutputScript("DEVANAGARI");
                    } else {
                        config.setOutputScript("ROMAN_HINDI");
                    }
                    rebuildWidgets();
                }
        ).tooltip(Tooltip.create(Component.literal("Controls Hindi output script. Roman Hindi writes spoken Hindi in English letters, preserving English words without forcing Devanagari.")))
         .bounds(centerX + 4, startY + (rowSpacing * 6), btnW, btnH).build());

        // --- ROW 8: Language & Model Library (Centered at the very bottom of options) ---
        int libraryW = 210;
        int libraryH = 22;
        int libraryY = startY + (rowSpacing * 7) + 2;
        addRenderableWidget(Button.builder(
                Component.literal("🌐 Language & Model Library ➔"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.gui.setScreen(new ModelLibraryScreen(this));
                    }
                }
        ).tooltip(Tooltip.create(Component.literal("Open the dedicated Language & Model Library to discover, download, and activate Whisper, Hinglish, Vosk, and Indic models.")))
         .bounds(centerX - (libraryW / 2), libraryY, libraryW, libraryH).build());

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
        ).bounds(centerX - 75, this.height - 24, 150, 20).build());
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

    private void startModelDownload() {
        String activeModelId = availableModelIds.get(currentModelIndex);
        isDownloading = true;
        downloadStatus = "§eConnecting...";
        rebuildWidgets();

        modelManager.downloadModel(activeModelId, new DownloadProgressListener() {
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
                downloadStatus = "§aModel verified & ready for offline dictation!";

                VoiceToTextMod mod = VoiceToTextMod.getInstance();
                if (mod != null && mod.getController() != null) {
                    mod.getController().switchModel(activeModelId);
                }

                if (minecraft != null) {
                    minecraft.execute(VoiceConfigScreen.this::rebuildWidgets);
                }
            }

            @Override
            public void onError(String errorMessage, Throwable throwable) {
                isDownloading = false;
                downloadStatus = "§cDownload failed: " + errorMessage;
                if (minecraft != null) {
                    minecraft.execute(VoiceConfigScreen.this::rebuildWidgets);
                }
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        // Header Title
        extractor.centeredText(this.font, "§lVoice2Text Settings", centerX, 6, 0xFFFFFFFF);
        extractor.centeredText(this.font, "§7Real-Time Offline Speech-to-Text for Minecraft", centerX, 16, 0x90A4B0BE);

        // Display Keybind Conflicts or Duplicate Bindings
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        List<String> warnings = (mod != null && mod.getKeybindManager() != null)
                ? mod.getKeybindManager().getConflictWarnings(this.minecraft)
                : List.of();

        int footerY = this.height - 44;

        if (selectedKeybind != SelectedKeybind.NONE) {
            extractor.centeredText(this.font, "§e▶ Press any key to assign keybind, or ESC to cancel ◀", centerX, footerY, 0xFFFFD700);
        } else if (isDownloading) {
            String progressText;
            if (downloadPercent >= 0) {
                double curMb = (double) bytesDownloaded / (1024 * 1024);
                double totMb = (double) totalBytesExpected / (1024 * 1024);
                progressText = String.format("§eDownloading... %.1f MB / %.1f MB (%d%%)", curMb, totMb, downloadPercent);
            } else {
                progressText = downloadStatus;
            }
            extractor.centeredText(this.font, progressText, centerX, footerY, 0xFFFFFFFF);
        } else if (!downloadStatus.isEmpty()) {
            extractor.centeredText(this.font, downloadStatus, centerX, footerY, 0xFFFFFFFF);

            // If error, show manual fallback instructions
            if (downloadStatus.contains("failed") || downloadStatus.contains("Error")) {
                String activeModelId = availableModelIds.get(currentModelIndex);
                SpeechModelInfo info = modelManager.getModelInfo(activeModelId);
                String manualUrl = ModelManager.getManualDownloadUrl(info);
                extractor.centeredText(this.font, "§7Manual link: " + manualUrl, centerX, footerY + 10, 0x80C8D6E5);
            }
        } else if (!warnings.isEmpty()) {
            // Display first conflict warning prominently
            String firstWarn = warnings.get(0);
            extractor.centeredText(this.font, "§e⚠ " + firstWarn, centerX, footerY - 4, 0xFFFFFF55);
            extractor.centeredText(this.font, "§7Pressing key will trigger Voice2Text. You can remap in Controls.", centerX, footerY + 6, 0xFFAAAAAA);
        } else {
            extractor.centeredText(this.font, "§7Keys configurable in Options → Controls → Key Binds → Voice2Text", centerX, footerY, 0x8090A0B0);
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
