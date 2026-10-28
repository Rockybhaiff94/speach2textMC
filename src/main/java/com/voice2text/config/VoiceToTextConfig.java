package com.voice2text.config;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Validated configuration options for the Voice-to-Text client mod.
 */
public class VoiceToTextConfig {

    // Automatic Send: OFF by default. When enabled, messages are sent automatically upon finalization.
    private boolean automaticSend = false;

    // Recording Mode: "HOLD_TO_ACTIVATE" (Gameplay V Hold), "CHAT_TOGGLE" (Chat Toggle), or "DUAL" (Both)
    private String recordingMode = "DUAL";

    // Audio Input Device
    private String selectedMicrophone = "System Default";

    // Custom Keybind Settings (defaults: T, P, V, O)
    private String activationKey = "T";
    private int activationKeyCode = InputConstants.KEY_T;
    private String stopKey = "P";
    private int stopKeyCode = InputConstants.KEY_P;
    private String dedicatedToggleKey = "V";
    private int dedicatedToggleKeyCode = InputConstants.KEY_V;
    private String configKey = "O";
    private int configKeyCode = InputConstants.KEY_O;

    private String activeModelId = "vosk-model-small-en-us-0.15";
    private String speechLanguage = "en-US";
    private float micSensitivity = 0.04f;
    private boolean offlineOnly = true;

    private boolean autoPunctuation = true;
    private boolean voiceCommandsEnabled = true;
    private boolean autoCapitalization = true;

    private boolean hudWaveformEnabled = true;
    private float hudScale = 1.0f;
    private String hudPosition = "BOTTOM_RIGHT";
    // Voice Interface Layout: Layout 2 (Transparent Floating Dots & Live Chat Overlay) is the only layout
    private String interfaceLayout = "LAYOUT_2";

    public VoiceToTextConfig() {
    }

    /**
     * Validates and constrains all configuration parameters to safe bounds.
     */
    public void validate() {
        // Layout 1 has been removed; automatically migrate any legacy "LAYOUT_1" or unrecognized layout to "LAYOUT_2"
        if (!"LAYOUT_2".equalsIgnoreCase(interfaceLayout)) {
            interfaceLayout = "LAYOUT_2";
        }

        if ("ACTIVATION".equalsIgnoreCase(recordingMode) || "TOGGLE".equalsIgnoreCase(recordingMode)) {
            recordingMode = "CHAT_TOGGLE";
        } else if ("HOLD_TO_TALK".equalsIgnoreCase(recordingMode)) {
            recordingMode = "HOLD_TO_ACTIVATE";
        } else if (!"HOLD_TO_ACTIVATE".equalsIgnoreCase(recordingMode) && !"CHAT_TOGGLE".equalsIgnoreCase(recordingMode) && !"DUAL".equalsIgnoreCase(recordingMode)) {
            recordingMode = "DUAL";
        }

        if (micSensitivity < 0.001f) micSensitivity = 0.001f;
        if (micSensitivity > 1.0f) micSensitivity = 1.0f;

        if (hudScale < 0.5f) hudScale = 0.5f;
        if (hudScale > 3.0f) hudScale = 3.0f;

        if (activeModelId == null || activeModelId.trim().isEmpty()) {
            activeModelId = "vosk-model-small-en-us-0.15";
        }
        if (speechLanguage == null || speechLanguage.trim().isEmpty()) {
            speechLanguage = "en-US";
        }

        // Validate keybinds
        if (activationKey == null || activationKey.trim().isEmpty()) {
            activationKey = "T";
        }
        if (activationKeyCode <= 0) {
            activationKeyCode = InputConstants.KEY_T;
        }
        if (stopKey == null || stopKey.trim().isEmpty()) {
            stopKey = "P";
        }
        if (stopKeyCode <= 0) {
            stopKeyCode = InputConstants.KEY_P;
        }
        if (dedicatedToggleKey == null || dedicatedToggleKey.trim().isEmpty()) {
            dedicatedToggleKey = "V";
        }
        if (dedicatedToggleKeyCode <= 0) {
            dedicatedToggleKeyCode = InputConstants.KEY_V;
        }
        if (configKey == null || configKey.trim().isEmpty()) {
            configKey = "O";
        }
        if (configKeyCode <= 0) {
            configKeyCode = InputConstants.KEY_O;
        }

        if (recordingMode == null || recordingMode.trim().isEmpty()) {
            recordingMode = "ACTIVATION";
        }
        if (selectedMicrophone == null || selectedMicrophone.trim().isEmpty()) {
            selectedMicrophone = "System Default";
        }

        if (hudPosition == null || hudPosition.trim().isEmpty()) {
            hudPosition = "BOTTOM_RIGHT";
        }
    }

    public String getRecordingMode() {
        return recordingMode;
    }

    public void setRecordingMode(String recordingMode) {
        this.recordingMode = recordingMode != null ? recordingMode : "ACTIVATION";
    }

    public String getSelectedMicrophone() {
        return selectedMicrophone;
    }

    public void setSelectedMicrophone(String selectedMicrophone) {
        this.selectedMicrophone = selectedMicrophone != null ? selectedMicrophone : "System Default";
    }

    public boolean isAutomaticSend() {
        return automaticSend;
    }

    public void setAutomaticSend(boolean automaticSend) {
        this.automaticSend = automaticSend;
    }

    // Backward-compatible alias for automaticSend
    public boolean isStopAndSend() {
        return automaticSend;
    }

    public void setStopAndSend(boolean stopAndSend) {
        this.automaticSend = stopAndSend;
    }

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public int getActivationKeyCode() {
        return activationKeyCode;
    }

    public void setActivationKeyCode(int activationKeyCode) {
        this.activationKeyCode = activationKeyCode;
    }

    public String getStopKey() {
        return stopKey;
    }

    public void setStopKey(String stopKey) {
        this.stopKey = stopKey;
    }

    public int getStopKeyCode() {
        return stopKeyCode;
    }

    public void setStopKeyCode(int stopKeyCode) {
        this.stopKeyCode = stopKeyCode;
    }

    public String getDedicatedToggleKey() {
        return dedicatedToggleKey;
    }

    public void setDedicatedToggleKey(String dedicatedToggleKey) {
        this.dedicatedToggleKey = dedicatedToggleKey;
    }

    public int getDedicatedToggleKeyCode() {
        return dedicatedToggleKeyCode;
    }

    public void setDedicatedToggleKeyCode(int dedicatedToggleKeyCode) {
        this.dedicatedToggleKeyCode = dedicatedToggleKeyCode;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public int getConfigKeyCode() {
        return configKeyCode;
    }

    public void setConfigKeyCode(int configKeyCode) {
        this.configKeyCode = configKeyCode;
    }

    public String getActiveModelId() {
        return activeModelId;
    }

    public void setActiveModelId(String activeModelId) {
        this.activeModelId = activeModelId;
    }

    public String getSpeechLanguage() {
        return speechLanguage;
    }

    public void setSpeechLanguage(String speechLanguage) {
        this.speechLanguage = speechLanguage;
    }

    public float getMicSensitivity() {
        return micSensitivity;
    }

    public void setMicSensitivity(float micSensitivity) {
        this.micSensitivity = micSensitivity;
    }

    public boolean isOfflineOnly() {
        return offlineOnly;
    }

    public void setOfflineOnly(boolean offlineOnly) {
        this.offlineOnly = offlineOnly;
    }

    public boolean isAutoPunctuation() {
        return autoPunctuation;
    }

    public void setAutoPunctuation(boolean autoPunctuation) {
        this.autoPunctuation = autoPunctuation;
    }

    public boolean isVoiceCommandsEnabled() {
        return voiceCommandsEnabled;
    }

    public void setVoiceCommandsEnabled(boolean voiceCommandsEnabled) {
        this.voiceCommandsEnabled = voiceCommandsEnabled;
    }

    public boolean isAutoCapitalization() {
        return autoCapitalization;
    }

    public void setAutoCapitalization(boolean autoCapitalization) {
        this.autoCapitalization = autoCapitalization;
    }

    public boolean isHudWaveformEnabled() {
        return hudWaveformEnabled;
    }

    public void setHudWaveformEnabled(boolean hudWaveformEnabled) {
        this.hudWaveformEnabled = hudWaveformEnabled;
    }

    public float getHudScale() {
        return hudScale;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = hudScale;
    }

    public String getHudPosition() {
        return hudPosition;
    }

    public void setHudPosition(String hudPosition) {
        this.hudPosition = hudPosition;
    }

    public String getInterfaceLayout() {
        return interfaceLayout;
    }

    public void setInterfaceLayout(String interfaceLayout) {
        if ("LAYOUT_1".equalsIgnoreCase(interfaceLayout) || interfaceLayout == null) {
            this.interfaceLayout = "LAYOUT_2";
        } else {
            this.interfaceLayout = interfaceLayout;
        }
    }
}
