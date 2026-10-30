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
    private boolean activationKeyMouse = false;
    private String stopKey = "P";
    private int stopKeyCode = InputConstants.KEY_P;
    private boolean stopKeyMouse = false;
    private String dedicatedToggleKey = "V";
    private int dedicatedToggleKeyCode = InputConstants.KEY_V;
    private boolean dedicatedToggleKeyMouse = false;
    private String configKey = "O";
    private int configKeyCode = InputConstants.KEY_O;
    private boolean configKeyMouse = false;

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
    private String outputScript = "ROMAN_HINDI";

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
        // Safe migration for legacy numeric key codes / malformed translation identifiers
        if ("key.keyboard.23".equalsIgnoreCase(activationKey) || "23".equals(activationKey) || "key.keyboard.84".equalsIgnoreCase(activationKey) || "84".equals(activationKey) || (!activationKeyMouse && activationKeyCode == 84)) {
            activationKeyCode = InputConstants.KEY_T;
            activationKey = "T";
            activationKeyMouse = false;
        }
        if ("key.keyboard.25".equalsIgnoreCase(dedicatedToggleKey) || "25".equals(dedicatedToggleKey) || "key.keyboard.86".equalsIgnoreCase(dedicatedToggleKey) || "86".equals(dedicatedToggleKey) || (!dedicatedToggleKeyMouse && dedicatedToggleKeyCode == 86)) {
            dedicatedToggleKeyCode = InputConstants.KEY_V;
            dedicatedToggleKey = "V";
            dedicatedToggleKeyMouse = false;
        }
        if ("key.keyboard.18".equalsIgnoreCase(configKey) || "18".equals(configKey) || "key.keyboard.79".equalsIgnoreCase(configKey) || "79".equals(configKey) || (!configKeyMouse && configKeyCode == 79)) {
            configKeyCode = InputConstants.KEY_O;
            configKey = "O";
            configKeyMouse = false;
        }
        if ("key.keyboard.19".equalsIgnoreCase(stopKey) || "19".equals(stopKey) || "key.keyboard.80".equalsIgnoreCase(stopKey) || "80".equals(stopKey) || (!stopKeyMouse && stopKeyCode == 80)) {
            stopKeyCode = InputConstants.KEY_P;
            stopKey = "P";
            stopKeyMouse = false;
        }

        // Clean any raw key. prefix if present
        activationKey = cleanKeyIdentifier(activationKey, activationKeyMouse);
        dedicatedToggleKey = cleanKeyIdentifier(dedicatedToggleKey, dedicatedToggleKeyMouse);
        stopKey = cleanKeyIdentifier(stopKey, stopKeyMouse);
        configKey = cleanKeyIdentifier(configKey, configKeyMouse);

        if (activationKey == null || activationKey.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(activationKey.trim())) {
            activationKey = "T";
        }
        if (activationKeyCode <= 0) {
            activationKeyCode = InputConstants.KEY_T;
        }
        if (stopKey == null || stopKey.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(stopKey.trim())) {
            stopKey = "P";
        }
        if (stopKeyCode <= 0) {
            stopKeyCode = InputConstants.KEY_P;
        }
        if (dedicatedToggleKey == null || dedicatedToggleKey.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(dedicatedToggleKey.trim())) {
            dedicatedToggleKey = "V";
        }
        if (dedicatedToggleKeyCode <= 0) {
            dedicatedToggleKeyCode = InputConstants.KEY_V;
        }
        if (configKey == null || configKey.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(configKey.trim())) {
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

        if (outputScript == null || (!"ROMAN_HINDI".equalsIgnoreCase(outputScript) && !"DEVANAGARI".equalsIgnoreCase(outputScript) && !"AUTO".equalsIgnoreCase(outputScript))) {
            outputScript = "ROMAN_HINDI";
        }
    }

    public String getOutputScript() {
        return outputScript;
    }

    public void setOutputScript(String outputScript) {
        this.outputScript = outputScript != null ? outputScript : "ROMAN_HINDI";
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

    public boolean isActivationKeyMouse() {
        return activationKeyMouse;
    }

    public void setActivationKeyMouse(boolean activationKeyMouse) {
        this.activationKeyMouse = activationKeyMouse;
    }

    public boolean isStopKeyMouse() {
        return stopKeyMouse;
    }

    public void setStopKeyMouse(boolean stopKeyMouse) {
        this.stopKeyMouse = stopKeyMouse;
    }

    public boolean isDedicatedToggleKeyMouse() {
        return dedicatedToggleKeyMouse;
    }

    public void setDedicatedToggleKeyMouse(boolean dedicatedToggleKeyMouse) {
        this.dedicatedToggleKeyMouse = dedicatedToggleKeyMouse;
    }

    public boolean isConfigKeyMouse() {
        return configKeyMouse;
    }

    public void setConfigKeyMouse(boolean configKeyMouse) {
        this.configKeyMouse = configKeyMouse;
    }

    private static String cleanKeyIdentifier(String key, boolean isMouse) {
        if (key == null || key.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(key.trim())) return null;
        String s = key.trim();
        if (s.toLowerCase().startsWith("key.keyboard.")) {
            String suffix = s.substring("key.keyboard.".length());
            return switch (suffix.toLowerCase()) {
                case "23", "84", "t" -> "T";
                case "25", "86", "v" -> "V";
                case "18", "79", "o" -> "O";
                case "19", "80", "p" -> "P";
                default -> suffix.toUpperCase();
            };
        }
        if (s.toLowerCase().startsWith("key.mouse.")) {
            String suffix = s.substring("key.mouse.".length());
            return switch (suffix.toLowerCase()) {
                case "left", "1" -> "Left Button";
                case "right", "3" -> "Right Button";
                case "middle", "2" -> "Middle Button";
                case "4" -> "Mouse 4";
                case "5" -> "Mouse 5";
                case "6" -> "Mouse 6";
                case "7" -> "Mouse 7";
                case "8" -> "Mouse 8";
                default -> "Mouse " + suffix;
            };
        }
        if (s.toLowerCase().startsWith("key.")) {
            return s.substring(4).toUpperCase();
        }
        return s;
    }
}
