package com.voice2text.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.config.VoiceToTextConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.Locale;

/**
 * Manages configurable keybinds for Voice2Text, providing key code
 * validation, canonical name resolution, key and mouse matching, and bidirectional synchronization with Minecraft KeyMappings.
 */
public class KeybindManager {

    public static final int DEFAULT_ACTIVATION_KEY_CODE = InputConstants.KEY_T; // 23 in Minecraft 26.3
    public static final String DEFAULT_ACTIVATION_KEY_NAME = "T";

    public static final int DEFAULT_STOP_KEY_CODE = InputConstants.KEY_P; // 19 in Minecraft 26.3
    public static final String DEFAULT_STOP_KEY_NAME = "P";

    public static final int DEFAULT_TOGGLE_KEY_CODE = InputConstants.KEY_V; // 25 in Minecraft 26.3
    public static final String DEFAULT_TOGGLE_KEY_NAME = "V";

    public static final int DEFAULT_CONFIG_KEY_CODE = InputConstants.KEY_O; // 18 in Minecraft 26.3
    public static final String DEFAULT_CONFIG_KEY_NAME = "O";

    private final VoiceToTextConfig config;

    private KeyMapping activationKeyMapping;
    private KeyMapping toggleKeyMapping;
    private KeyMapping stopKeyMapping;
    private KeyMapping configKeyMapping;

    public KeybindManager(VoiceToTextConfig config) {
        this.config = config;
        syncFromConfig();
    }

    /**
     * Registers all 4 Voice2Text keymappings.
     */
    public void setKeyMappings(KeyMapping activationKeyMapping,
                               KeyMapping toggleKeyMapping,
                               KeyMapping stopKeyMapping,
                               KeyMapping configKeyMapping) {
        this.activationKeyMapping = activationKeyMapping;
        this.toggleKeyMapping = toggleKeyMapping;
        this.stopKeyMapping = stopKeyMapping;
        this.configKeyMapping = configKeyMapping;
        syncFromMinecraftKeyMappings();
    }

    /**
     * Backward-compatible overload.
     */
    public void setKeyMappings(KeyMapping toggleKeyMapping, KeyMapping configKeyMapping) {
        this.toggleKeyMapping = toggleKeyMapping;
        this.configKeyMapping = configKeyMapping;
        syncFromMinecraftKeyMappings();
    }

    /**
     * Bidirectionally synchronizes key assignments from Minecraft KeyMappings into config.
     * Called on client tick or screen open to detect changes made in Options -> Controls -> Key Binds.
     *
     * @return true if any configuration value was updated
     */
    public boolean syncFromMinecraftKeyMappings() {
        boolean changed = false;

        if (activationKeyMapping != null) {
            InputConstants.Key boundKey = getBoundKeyObject(activationKeyMapping);
            if (boundKey != null && boundKey != InputConstants.UNKNOWN) {
                int key = boundKey.getValue();
                boolean isMouse = boundKey.getType() == InputConstants.Type.MOUSE;
                String resolvedName = resolveKeyName(boundKey);
                if (key != -1 && (key != config.getActivationKeyCode() || isMouse != config.isActivationKeyMouse() || !resolvedName.equals(config.getActivationKey()))) {
                    config.setActivationKeyCode(key);
                    config.setActivationKey(resolvedName);
                    config.setActivationKeyMouse(isMouse);
                    changed = true;
                }
            }
        }

        if (toggleKeyMapping != null) {
            InputConstants.Key boundKey = getBoundKeyObject(toggleKeyMapping);
            if (boundKey != null && boundKey != InputConstants.UNKNOWN) {
                int key = boundKey.getValue();
                boolean isMouse = boundKey.getType() == InputConstants.Type.MOUSE;
                String resolvedName = resolveKeyName(boundKey);
                if (key != -1 && (key != config.getDedicatedToggleKeyCode() || isMouse != config.isDedicatedToggleKeyMouse() || !resolvedName.equals(config.getDedicatedToggleKey()))) {
                    config.setDedicatedToggleKeyCode(key);
                    config.setDedicatedToggleKey(resolvedName);
                    config.setDedicatedToggleKeyMouse(isMouse);
                    changed = true;
                }
            }
        }

        if (stopKeyMapping != null) {
            InputConstants.Key boundKey = getBoundKeyObject(stopKeyMapping);
            if (boundKey != null && boundKey != InputConstants.UNKNOWN) {
                int key = boundKey.getValue();
                boolean isMouse = boundKey.getType() == InputConstants.Type.MOUSE;
                String resolvedName = resolveKeyName(boundKey);
                if (key != -1 && (key != config.getStopKeyCode() || isMouse != config.isStopKeyMouse() || !resolvedName.equals(config.getStopKey()))) {
                    config.setStopKeyCode(key);
                    config.setStopKey(resolvedName);
                    config.setStopKeyMouse(isMouse);
                    changed = true;
                }
            }
        }

        if (configKeyMapping != null) {
            InputConstants.Key boundKey = getBoundKeyObject(configKeyMapping);
            if (boundKey != null && boundKey != InputConstants.UNKNOWN) {
                int key = boundKey.getValue();
                boolean isMouse = boundKey.getType() == InputConstants.Type.MOUSE;
                String resolvedName = resolveKeyName(boundKey);
                if (key != -1 && (key != config.getConfigKeyCode() || isMouse != config.isConfigKeyMouse() || !resolvedName.equals(config.getConfigKey()))) {
                    config.setConfigKeyCode(key);
                    config.setConfigKey(resolvedName);
                    config.setConfigKeyMouse(isMouse);
                    changed = true;
                }
            }
        }

        return changed;
    }

    public static InputConstants.Key getBoundKeyObject(KeyMapping mapping) {
        if (mapping == null) return null;
        try {
            return KeyMappingHelper.getBoundKeyOf(mapping);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int getBoundKey(KeyMapping mapping) {
        InputConstants.Key key = getBoundKeyObject(mapping);
        return key != null ? key.getValue() : -1;
    }

    /**
     * Resolves an InputConstants.Key to a friendly, recognizable display label (e.g. "V", "Right Shift", "F6", "Mouse 4").
     */
    public static String resolveKeyName(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) {
            return "NONE";
        }
        try {
            if (key.getDisplayName() != null) {
                String disp = key.getDisplayName().getString();
                if (disp != null && !disp.trim().isEmpty()) {
                    disp = disp.trim();
                    if (disp.toLowerCase(Locale.ROOT).startsWith("key.keyboard.")) {
                        return normalizeKeyName(disp.substring("key.keyboard.".length()));
                    } else if (disp.toLowerCase(Locale.ROOT).startsWith("key.mouse.")) {
                        return normalizeMouseName(disp.substring("key.mouse.".length()));
                    } else if (disp.toLowerCase(Locale.ROOT).startsWith("key.")) {
                        return normalizeKeyName(disp.substring(4));
                    }
                    if (key.getType() == InputConstants.Type.MOUSE) {
                        if (disp.toLowerCase(Locale.ROOT).startsWith("button ")) {
                            return "Mouse " + disp.substring(7).trim();
                        }
                    }
                    if ("Space".equalsIgnoreCase(disp)) {
                        return "SPACE";
                    }
                    if ("Escape".equalsIgnoreCase(disp)) {
                        return "ESCAPE";
                    }
                    return disp;
                }
            }
        } catch (Throwable ignored) {
        }
        return resolveKeyName(key.getValue());
    }

    /**
     * Converts a key code to a friendly displayable key name.
     */
    public static String resolveKeyName(int keyCode) {
        // Safe check for primary Voice2Text keys across modern (SDL) and legacy (GLFW) representations
        if (keyCode == InputConstants.KEY_T || keyCode == 23 || keyCode == 84) return "T";
        if (keyCode == InputConstants.KEY_V || keyCode == 25 || keyCode == 86) return "V";
        if (keyCode == InputConstants.KEY_O || keyCode == 18 || keyCode == 79) return "O";
        if (keyCode == InputConstants.KEY_P || keyCode == 19 || keyCode == 80) return "P";

        try {
            InputConstants.Key key = createKey(keyCode);
            if (key != null && key.getDisplayName() != null) {
                String name = key.getDisplayName().getString();
                if (name != null && !name.trim().isEmpty()) {
                    name = name.trim();
                    if (name.toLowerCase(Locale.ROOT).startsWith("key.keyboard.")) {
                        return normalizeKeyName(name.substring("key.keyboard.".length()));
                    } else if (name.toLowerCase(Locale.ROOT).startsWith("key.mouse.")) {
                        return normalizeMouseName(name.substring("key.mouse.".length()));
                    } else if (name.toLowerCase(Locale.ROOT).startsWith("key.")) {
                        return normalizeKeyName(name.substring(4));
                    }
                    if ("Space".equalsIgnoreCase(name)) {
                        return "SPACE";
                    }
                    if ("Escape".equalsIgnoreCase(name)) {
                        return "ESCAPE";
                    }
                    return name;
                }
            }
        } catch (Throwable ignored) {
        }

        if (keyCode >= 32 && keyCode <= 126) {
            return String.valueOf((char) keyCode).toUpperCase(Locale.ROOT);
        }

        return normalizeKeyName(String.valueOf(keyCode));
    }

    /**
     * Resolves raw numeric/internal key names into readable labels.
     */
    public static String normalizeKeyName(String nameOrCode) {
        if (nameOrCode == null || nameOrCode.trim().isEmpty()) {
            return "NONE";
        }
        String clean = nameOrCode.trim();
        if (clean.toLowerCase(Locale.ROOT).startsWith("key.keyboard.")) {
            clean = clean.substring("key.keyboard.".length());
        } else if (clean.toLowerCase(Locale.ROOT).startsWith("key.mouse.")) {
            return normalizeMouseName(clean.substring("key.mouse.".length()));
        } else if (clean.toLowerCase(Locale.ROOT).startsWith("key.")) {
            clean = clean.substring(4);
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "23", "84", "t" -> { return "T"; }
            case "25", "86", "v" -> { return "V"; }
            case "18", "79", "o" -> { return "O"; }
            case "19", "80", "p" -> { return "P"; }
            case "right.shift", "rshift" -> { return "Right Shift"; }
            case "left.shift", "lshift" -> { return "Left Shift"; }
            case "right.control", "rcontrol" -> { return "Right Control"; }
            case "left.control", "lcontrol" -> { return "Left Control"; }
            case "right.alt", "ralt" -> { return "Right Alt"; }
            case "left.alt", "lalt" -> { return "Left Alt"; }
            case "space" -> { return "SPACE"; }
            case "escape" -> { return "ESCAPE"; }
            case "enter", "return" -> { return "RETURN"; }
            case "tab" -> { return "TAB"; }
            case "backspace" -> { return "BACKSPACE"; }
            case "delete" -> { return "DELETE"; }
        }

        if (lower.startsWith("f") && lower.length() >= 2 && Character.isDigit(lower.charAt(1))) {
            return clean.toUpperCase(Locale.ROOT);
        }

        // If it's a numeric code, inspect InputConstants
        try {
            int num = Integer.parseInt(clean);
            if (num == InputConstants.KEY_T || num == 23 || num == 84) return "T";
            if (num == InputConstants.KEY_V || num == 25 || num == 86) return "V";
            if (num == InputConstants.KEY_O || num == 18 || num == 79) return "O";
            if (num == InputConstants.KEY_P || num == 19 || num == 80) return "P";
            InputConstants.Key k = createKey(num);
            if (k != null && k.getDisplayName() != null) {
                String d = k.getDisplayName().getString();
                if (d != null && !d.toLowerCase(Locale.ROOT).startsWith("key.")) {
                    return d;
                }
            }
        } catch (NumberFormatException ignored) {
        }

        return clean.toUpperCase(Locale.ROOT);
    }

    /**
     * Resolves mouse suffix into readable mouse names.
     */
    public static String normalizeMouseName(String mouseSuffix) {
        if (mouseSuffix == null) return "Mouse Button";
        String lower = mouseSuffix.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "left", "1" -> { return "Left Button"; }
            case "right", "3" -> { return "Right Button"; }
            case "middle", "2" -> { return "Middle Button"; }
            case "4" -> { return "Mouse 4"; }
            case "5" -> { return "Mouse 5"; }
            case "6" -> { return "Mouse 6"; }
            case "7" -> { return "Mouse 7"; }
            case "8" -> { return "Mouse 8"; }
            default -> { return "Mouse " + mouseSuffix; }
        }
    }

    public boolean matchesActivationKey(int keyCode) {
        return !config.isActivationKeyMouse() && keyCode == getActivationKeyCode();
    }

    public boolean matchesActivationKey(KeyEvent event) {
        if (event == null || config.isActivationKeyMouse()) return false;
        if (activationKeyMapping != null) {
            try {
                return activationKeyMapping.matches(event);
            } catch (Throwable ignored) {
            }
        }
        return matchesActivationKey(event.key());
    }

    public boolean matchesActivationKey(MouseButtonEvent event) {
        if (event == null || !config.isActivationKeyMouse()) return false;
        if (activationKeyMapping != null) {
            try {
                return activationKeyMapping.matchesMouse(event);
            } catch (Throwable ignored) {
            }
        }
        return event.button() == getActivationKeyCode();
    }

    public boolean matchesStopKey(int keyCode) {
        return !config.isStopKeyMouse() && keyCode == getStopKeyCode();
    }

    public boolean matchesStopKey(KeyEvent event) {
        if (event == null || config.isStopKeyMouse()) return false;
        if (stopKeyMapping != null) {
            try {
                return stopKeyMapping.matches(event);
            } catch (Throwable ignored) {
            }
        }
        return matchesStopKey(event.key());
    }

    public boolean matchesStopKey(MouseButtonEvent event) {
        if (event == null || !config.isStopKeyMouse()) return false;
        if (stopKeyMapping != null) {
            try {
                return stopKeyMapping.matchesMouse(event);
            } catch (Throwable ignored) {
            }
        }
        return event.button() == getStopKeyCode();
    }

    public boolean matchesToggleKey(int keyCode) {
        return !config.isDedicatedToggleKeyMouse() && keyCode == getToggleKeyCode();
    }

    public boolean matchesToggleKey(KeyEvent event) {
        if (event == null || config.isDedicatedToggleKeyMouse()) return false;
        if (toggleKeyMapping != null) {
            try {
                return toggleKeyMapping.matches(event);
            } catch (Throwable ignored) {
            }
        }
        return matchesToggleKey(event.key());
    }

    public boolean matchesToggleKey(MouseButtonEvent event) {
        if (event == null || !config.isDedicatedToggleKeyMouse()) return false;
        if (toggleKeyMapping != null) {
            try {
                return toggleKeyMapping.matchesMouse(event);
            } catch (Throwable ignored) {
            }
        }
        return event.button() == getToggleKeyCode();
    }

    public boolean matchesConfigKey(int keyCode) {
        return !config.isConfigKeyMouse() && keyCode == getConfigKeyCode();
    }

    public boolean matchesConfigKey(KeyEvent event) {
        if (event == null || config.isConfigKeyMouse()) return false;
        if (configKeyMapping != null) {
            try {
                return configKeyMapping.matches(event);
            } catch (Throwable ignored) {
            }
        }
        return matchesConfigKey(event.key());
    }

    public boolean matchesConfigKey(MouseButtonEvent event) {
        if (event == null || !config.isConfigKeyMouse()) return false;
        if (configKeyMapping != null) {
            try {
                return configKeyMapping.matchesMouse(event);
            } catch (Throwable ignored) {
            }
        }
        return event.button() == getConfigKeyCode();
    }

    public int getActivationKeyCode() {
        if (activationKeyMapping != null) {
            int key = getBoundKey(activationKeyMapping);
            if (key != -1) return key;
        }
        return config.getActivationKeyCode();
    }

    public String getActivationKeyName() {
        if (activationKeyMapping != null) {
            InputConstants.Key bound = getBoundKeyObject(activationKeyMapping);
            if (bound != null && bound != InputConstants.UNKNOWN) {
                return resolveKeyName(bound);
            }
        }
        if (config.getActivationKey() != null && !config.getActivationKey().trim().isEmpty()) {
            return config.getActivationKey();
        }
        return config.isActivationKeyMouse() ? normalizeMouseName(String.valueOf(getActivationKeyCode())) : resolveKeyName(getActivationKeyCode());
    }

    public boolean hasActivationKeyConflict(Minecraft minecraft) {
        int activationCode = getActivationKeyCode();
        if (minecraft == null || minecraft.options == null || minecraft.options.keyMappings == null) {
            return activationCode == DEFAULT_ACTIVATION_KEY_CODE;
        }

        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping == null) continue;
            if (mapping == activationKeyMapping || mapping == toggleKeyMapping ||
                mapping == stopKeyMapping || mapping == configKeyMapping) {
                continue;
            }
            if (mapping.getCategory() == com.voice2text.VoiceToTextMod.VOICE2TEXT_CATEGORY ||
                mapping.getCategory() == com.voice2text.VoiceToTextMod.SPEAKCRAFT_CATEGORY) {
                continue;
            }
            int bound = getBoundKey(mapping);
            if (bound != -1 && bound == activationCode) {
                return true;
            }
        }
        return false;
    }

    public String getConflictingVanillaAction(Minecraft minecraft, int keyCode) {
        if (minecraft == null || minecraft.options == null || minecraft.options.keyMappings == null) {
            return keyCode == DEFAULT_ACTIVATION_KEY_CODE ? "key.chat" : null;
        }
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping == null) continue;
            if (mapping == activationKeyMapping || mapping == toggleKeyMapping ||
                mapping == stopKeyMapping || mapping == configKeyMapping) {
                continue;
            }
            if (mapping.getCategory() == com.voice2text.VoiceToTextMod.VOICE2TEXT_CATEGORY ||
                mapping.getCategory() == com.voice2text.VoiceToTextMod.SPEAKCRAFT_CATEGORY) {
                continue;
            }
            int bound = getBoundKey(mapping);
            if (bound != -1 && bound == keyCode) {
                return mapping.getName();
            }
        }
        return null;
    }

    public java.util.List<String> getConflictWarnings(Minecraft minecraft) {
        java.util.List<String> warnings = new java.util.ArrayList<>();

        int act = getActivationKeyCode();
        int tog = getToggleKeyCode();
        int stp = getStopKeyCode();

        if (act == tog) {
            warnings.add("Activation Key and Hold-to-Talk are both set to [" + getActivationKeyName() + "]");
        }
        if (act == stp) {
            warnings.add("Activation Key and Stop & Draft are both set to [" + getActivationKeyName() + "]");
        }
        if (tog == stp) {
            warnings.add("Hold-to-Talk and Stop & Draft are both set to [" + getToggleKeyName() + "]");
        }

        String actConflict = getConflictingVanillaAction(minecraft, act);
        if (actConflict != null) {
            warnings.add("Activation Key [" + getActivationKeyName() + "] conflicts with Minecraft (" + actConflict + ")");
        }
        String togConflict = getConflictingVanillaAction(minecraft, tog);
        if (togConflict != null) {
            warnings.add("Hold-to-Talk Key [" + getToggleKeyName() + "] conflicts with Minecraft (" + togConflict + ")");
        }
        String stpConflict = getConflictingVanillaAction(minecraft, stp);
        if (stpConflict != null) {
            warnings.add("Stop & Draft Key [" + getStopKeyName() + "] conflicts with Minecraft (" + stpConflict + ")");
        }

        return warnings;
    }

    public void setActivationKey(int keyCode, String keyName, boolean isMouse) {
        if (keyName == null || keyName.trim().isEmpty()) {
            keyName = isMouse ? normalizeMouseName(String.valueOf(keyCode)) : resolveKeyName(keyCode);
        }
        config.setActivationKeyCode(keyCode);
        config.setActivationKey(keyName);
        config.setActivationKeyMouse(isMouse);
        InputConstants.Key key = isMouse ? createMouseKey(keyCode) : createKey(keyCode);
        updateKeyMapping(activationKeyMapping, key);
    }

    public void setActivationKey(int keyCode, String keyName) {
        boolean isMouse = (keyName != null && keyName.toLowerCase(Locale.ROOT).startsWith("mouse")) || (keyCode >= 300 && keyCode <= 310);
        setActivationKey(keyCode, keyName, isMouse);
    }

    public void setActivationKey(InputConstants.Key key) {
        if (key == null) return;
        boolean isMouse = key.getType() == InputConstants.Type.MOUSE;
        setActivationKey(key.getValue(), resolveKeyName(key), isMouse);
    }

    public int getStopKeyCode() {
        if (stopKeyMapping != null) {
            int key = getBoundKey(stopKeyMapping);
            if (key != -1) return key;
        }
        return config.getStopKeyCode();
    }

    public String getStopKeyName() {
        if (stopKeyMapping != null) {
            InputConstants.Key bound = getBoundKeyObject(stopKeyMapping);
            if (bound != null && bound != InputConstants.UNKNOWN) {
                return resolveKeyName(bound);
            }
        }
        if (config.getStopKey() != null && !config.getStopKey().trim().isEmpty()) {
            return config.getStopKey();
        }
        return config.isStopKeyMouse() ? normalizeMouseName(String.valueOf(getStopKeyCode())) : resolveKeyName(getStopKeyCode());
    }

    public void setStopKey(int keyCode, String keyName, boolean isMouse) {
        if (keyName == null || keyName.trim().isEmpty()) {
            keyName = isMouse ? normalizeMouseName(String.valueOf(keyCode)) : resolveKeyName(keyCode);
        }
        config.setStopKeyCode(keyCode);
        config.setStopKey(keyName);
        config.setStopKeyMouse(isMouse);
        InputConstants.Key key = isMouse ? createMouseKey(keyCode) : createKey(keyCode);
        updateKeyMapping(stopKeyMapping, key);
    }

    public void setStopKey(int keyCode, String keyName) {
        boolean isMouse = (keyName != null && keyName.toLowerCase(Locale.ROOT).startsWith("mouse")) || (keyCode >= 300 && keyCode <= 310);
        setStopKey(keyCode, keyName, isMouse);
    }

    public void setStopKey(InputConstants.Key key) {
        if (key == null) return;
        boolean isMouse = key.getType() == InputConstants.Type.MOUSE;
        setStopKey(key.getValue(), resolveKeyName(key), isMouse);
    }

    public int getToggleKeyCode() {
        if (toggleKeyMapping != null) {
            int key = getBoundKey(toggleKeyMapping);
            if (key != -1) return key;
        }
        return config.getDedicatedToggleKeyCode();
    }

    public String getToggleKeyName() {
        if (toggleKeyMapping != null) {
            InputConstants.Key bound = getBoundKeyObject(toggleKeyMapping);
            if (bound != null && bound != InputConstants.UNKNOWN) {
                return resolveKeyName(bound);
            }
        }
        if (config.getDedicatedToggleKey() != null && !config.getDedicatedToggleKey().trim().isEmpty()) {
            return config.getDedicatedToggleKey();
        }
        return config.isDedicatedToggleKeyMouse() ? normalizeMouseName(String.valueOf(getToggleKeyCode())) : resolveKeyName(getToggleKeyCode());
    }

    public void setToggleKey(int keyCode, String keyName, boolean isMouse) {
        if (keyName == null || keyName.trim().isEmpty()) {
            keyName = isMouse ? normalizeMouseName(String.valueOf(keyCode)) : resolveKeyName(keyCode);
        }
        config.setDedicatedToggleKeyCode(keyCode);
        config.setDedicatedToggleKey(keyName);
        config.setDedicatedToggleKeyMouse(isMouse);
        InputConstants.Key key = isMouse ? createMouseKey(keyCode) : createKey(keyCode);
        updateKeyMapping(toggleKeyMapping, key);
    }

    public void setToggleKey(int keyCode, String keyName) {
        boolean isMouse = (keyName != null && keyName.toLowerCase(Locale.ROOT).startsWith("mouse")) || (keyCode >= 300 && keyCode <= 310);
        setToggleKey(keyCode, keyName, isMouse);
    }

    public void setToggleKey(InputConstants.Key key) {
        if (key == null) return;
        boolean isMouse = key.getType() == InputConstants.Type.MOUSE;
        setToggleKey(key.getValue(), resolveKeyName(key), isMouse);
    }

    public int getConfigKeyCode() {
        if (configKeyMapping != null) {
            int key = getBoundKey(configKeyMapping);
            if (key != -1) return key;
        }
        return config.getConfigKeyCode();
    }

    public String getConfigKeyName() {
        if (configKeyMapping != null) {
            InputConstants.Key bound = getBoundKeyObject(configKeyMapping);
            if (bound != null && bound != InputConstants.UNKNOWN) {
                return resolveKeyName(bound);
            }
        }
        if (config.getConfigKey() != null && !config.getConfigKey().trim().isEmpty()) {
            return config.getConfigKey();
        }
        return config.isConfigKeyMouse() ? normalizeMouseName(String.valueOf(getConfigKeyCode())) : resolveKeyName(getConfigKeyCode());
    }

    public void setConfigKey(int keyCode, String keyName, boolean isMouse) {
        if (keyName == null || keyName.trim().isEmpty()) {
            keyName = isMouse ? normalizeMouseName(String.valueOf(keyCode)) : resolveKeyName(keyCode);
        }
        config.setConfigKeyCode(keyCode);
        config.setConfigKey(keyName);
        config.setConfigKeyMouse(isMouse);
        InputConstants.Key key = isMouse ? createMouseKey(keyCode) : createKey(keyCode);
        updateKeyMapping(configKeyMapping, key);
    }

    public void setConfigKey(int keyCode, String keyName) {
        boolean isMouse = (keyName != null && keyName.toLowerCase(Locale.ROOT).startsWith("mouse")) || (keyCode >= 300 && keyCode <= 310);
        setConfigKey(keyCode, keyName, isMouse);
    }

    public void setConfigKey(InputConstants.Key key) {
        if (key == null) return;
        boolean isMouse = key.getType() == InputConstants.Type.MOUSE;
        setConfigKey(key.getValue(), resolveKeyName(key), isMouse);
    }

    /**
     * Resets all keybinds to standard defaults (T, P, V, O) and syncs to KeyMappings.
     */
    public void resetToDefaults() {
        setActivationKey(DEFAULT_ACTIVATION_KEY_CODE, DEFAULT_ACTIVATION_KEY_NAME, false);
        setStopKey(DEFAULT_STOP_KEY_CODE, DEFAULT_STOP_KEY_NAME, false);
        setToggleKey(DEFAULT_TOGGLE_KEY_CODE, DEFAULT_TOGGLE_KEY_NAME, false);
        setConfigKey(DEFAULT_CONFIG_KEY_CODE, DEFAULT_CONFIG_KEY_NAME, false);
    }

    private void updateKeyMapping(KeyMapping mapping, InputConstants.Key key) {
        if (mapping != null && key != null) {
            try {
                mapping.setKey(key);
                KeyMapping.resetMapping();
            } catch (Throwable ignored) {
            }
        }
    }

    public static InputConstants.Key createKey(int keyCode) {
        try {
            if (keyCode == -1) {
                return InputConstants.UNKNOWN;
            }
            InputConstants.Type type;
            try {
                type = InputConstants.Type.valueOf("KEYBOARD");
            } catch (IllegalArgumentException e) {
                type = InputConstants.Type.valueOf("KEYSYM");
            }
            return type.getOrCreate(keyCode);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static InputConstants.Key createMouseKey(int button) {
        try {
            return InputConstants.Type.MOUSE.getOrCreate(button);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void syncFromConfig() {
        config.validate();
    }

    public KeyMapping getActivationKeyMapping() {
        return activationKeyMapping;
    }

    public KeyMapping getToggleKeyMapping() {
        return toggleKeyMapping;
    }

    public KeyMapping getStopKeyMapping() {
        return stopKeyMapping;
    }

    public KeyMapping getConfigKeyMapping() {
        return configKeyMapping;
    }
}
