package com.voice2text.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.config.VoiceToTextConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/**
 * Manages configurable keybinds for SpeakCraft, providing key code
 * validation, name resolution, key matching, and bidirectional synchronization with Minecraft KeyMappings.
 */
public class KeybindManager {

    public static final int DEFAULT_ACTIVATION_KEY_CODE = InputConstants.KEY_T; // 84
    public static final String DEFAULT_ACTIVATION_KEY_NAME = "T";

    public static final int DEFAULT_STOP_KEY_CODE = InputConstants.KEY_P; // 80
    public static final String DEFAULT_STOP_KEY_NAME = "P";

    public static final int DEFAULT_TOGGLE_KEY_CODE = InputConstants.KEY_V; // 86
    public static final String DEFAULT_TOGGLE_KEY_NAME = "V";

    public static final int DEFAULT_CONFIG_KEY_CODE = InputConstants.KEY_O; // 79
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
     * Registers all 4 SpeakCraft keymappings.
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
            int key = getBoundKey(activationKeyMapping);
            if (key != -1 && key != config.getActivationKeyCode()) {
                config.setActivationKeyCode(key);
                config.setActivationKey(resolveKeyName(key));
                changed = true;
            }
        }

        if (toggleKeyMapping != null) {
            int key = getBoundKey(toggleKeyMapping);
            if (key != -1 && key != config.getDedicatedToggleKeyCode()) {
                config.setDedicatedToggleKeyCode(key);
                config.setDedicatedToggleKey(resolveKeyName(key));
                changed = true;
            }
        }

        if (stopKeyMapping != null) {
            int key = getBoundKey(stopKeyMapping);
            if (key != -1 && key != config.getStopKeyCode()) {
                config.setStopKeyCode(key);
                config.setStopKey(resolveKeyName(key));
                changed = true;
            }
        }

        if (configKeyMapping != null) {
            int key = getBoundKey(configKeyMapping);
            if (key != -1 && key != config.getConfigKeyCode()) {
                config.setConfigKeyCode(key);
                config.setConfigKey(resolveKeyName(key));
                changed = true;
            }
        }

        return changed;
    }

    private int getBoundKey(KeyMapping mapping) {
        if (mapping == null) return -1;
        try {
            InputConstants.Key boundKey = KeyMappingHelper.getBoundKeyOf(mapping);
            if (boundKey != null) {
                return boundKey.getValue();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * Converts a key code to a friendly displayable key name.
     */
    public static String resolveKeyName(int keyCode) {
        try {
            InputConstants.Key key = createKey(keyCode);
            if (key != null && key.getDisplayName() != null) {
                String name = key.getDisplayName().getString();
                if (name != null && !name.trim().isEmpty()) {
                    name = name.trim();
                    if (name.toLowerCase().startsWith("key.keyboard.")) {
                        name = name.substring("key.keyboard.".length());
                    } else if (name.toLowerCase().startsWith("key.")) {
                        name = name.substring(4);
                    }
                    return name.toUpperCase();
                }
            }
        } catch (Throwable ignored) {
        }

        if (keyCode >= 32 && keyCode <= 126) {
            return String.valueOf((char) keyCode).toUpperCase();
        }

        return "KEY " + keyCode;
    }

    public boolean matchesActivationKey(int keyCode) {
        return keyCode == getActivationKeyCode();
    }

    public boolean matchesActivationKey(KeyEvent event) {
        if (event == null) return false;
        if (activationKeyMapping != null) {
            try {
                return activationKeyMapping.matches(event);
            } catch (Throwable ignored) {
            }
        }
        return matchesActivationKey(event.key());
    }

    public boolean matchesStopKey(int keyCode) {
        return keyCode == getStopKeyCode();
    }

    public boolean matchesStopKey(KeyEvent event) {
        if (event == null) return false;
        if (stopKeyMapping != null) {
            try {
                return stopKeyMapping.matches(event);
            } catch (Throwable ignored) {
            }
        }
        return matchesStopKey(event.key());
    }

    public boolean matchesToggleKey(int keyCode) {
        return keyCode == getToggleKeyCode();
    }

    public boolean matchesToggleKey(KeyEvent event) {
        if (event == null) return false;
        if (toggleKeyMapping != null) {
            try {
                return toggleKeyMapping.matches(event);
            } catch (Throwable ignored) {
            }
        }
        return matchesToggleKey(event.key());
    }

    public boolean matchesConfigKey(int keyCode) {
        return keyCode == getConfigKeyCode();
    }

    public boolean matchesConfigKey(KeyEvent event) {
        if (event == null) return false;
        if (configKeyMapping != null) {
            try {
                return configKeyMapping.matches(event);
            } catch (Throwable ignored) {
            }
        }
        return matchesConfigKey(event.key());
    }

    public int getActivationKeyCode() {
        if (activationKeyMapping != null) {
            int key = getBoundKey(activationKeyMapping);
            if (key != -1) return key;
        }
        return config.getActivationKeyCode();
    }

    public String getActivationKeyName() {
        return resolveKeyName(getActivationKeyCode());
    }

    /**
     * Detects whether the current activation key conflicts with any registered Minecraft action.
     */
    public boolean hasActivationKeyConflict(Minecraft minecraft) {
        int activationCode = getActivationKeyCode();
        if (minecraft == null || minecraft.options == null || minecraft.options.keyMappings == null) {
            // By default, 'T' conflicts with Minecraft's chat key
            return activationCode == DEFAULT_ACTIVATION_KEY_CODE;
        }

        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping == null) continue;
            // Skip SpeakCraft's own key mappings
            if (mapping == activationKeyMapping || mapping == toggleKeyMapping ||
                mapping == stopKeyMapping || mapping == configKeyMapping) {
                continue;
            }
            if (mapping.getCategory() == com.voice2text.VoiceToTextMod.SPEAKCRAFT_CATEGORY) {
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
            if (mapping.getCategory() == com.voice2text.VoiceToTextMod.SPEAKCRAFT_CATEGORY) {
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

    public void setActivationKey(int keyCode, String keyName) {
        if (keyName == null || keyName.trim().isEmpty()) {
            keyName = resolveKeyName(keyCode);
        }
        config.setActivationKeyCode(keyCode);
        config.setActivationKey(keyName);
        updateKeyMapping(activationKeyMapping, keyCode);
    }

    public int getStopKeyCode() {
        if (stopKeyMapping != null) {
            int key = getBoundKey(stopKeyMapping);
            if (key != -1) return key;
        }
        return config.getStopKeyCode();
    }

    public String getStopKeyName() {
        return resolveKeyName(getStopKeyCode());
    }

    public void setStopKey(int keyCode, String keyName) {
        if (keyName == null || keyName.trim().isEmpty()) {
            keyName = resolveKeyName(keyCode);
        }
        config.setStopKeyCode(keyCode);
        config.setStopKey(keyName);
        updateKeyMapping(stopKeyMapping, keyCode);
    }

    public int getToggleKeyCode() {
        if (toggleKeyMapping != null) {
            int key = getBoundKey(toggleKeyMapping);
            if (key != -1) return key;
        }
        return config.getDedicatedToggleKeyCode();
    }

    public String getToggleKeyName() {
        return resolveKeyName(getToggleKeyCode());
    }

    public void setToggleKey(int keyCode, String keyName) {
        if (keyName == null || keyName.trim().isEmpty()) {
            keyName = resolveKeyName(keyCode);
        }
        config.setDedicatedToggleKeyCode(keyCode);
        config.setDedicatedToggleKey(keyName);
        updateKeyMapping(toggleKeyMapping, keyCode);
    }

    public int getConfigKeyCode() {
        if (configKeyMapping != null) {
            int key = getBoundKey(configKeyMapping);
            if (key != -1) return key;
        }
        return config.getConfigKeyCode();
    }

    public String getConfigKeyName() {
        return resolveKeyName(getConfigKeyCode());
    }

    public void setConfigKey(int keyCode, String keyName) {
        if (keyName == null || keyName.trim().isEmpty()) {
            keyName = resolveKeyName(keyCode);
        }
        config.setConfigKeyCode(keyCode);
        config.setConfigKey(keyName);
        updateKeyMapping(configKeyMapping, keyCode);
    }

    /**
     * Resets all keybinds to standard defaults (T, P, V, O) and syncs to KeyMappings.
     */
    public void resetToDefaults() {
        setActivationKey(DEFAULT_ACTIVATION_KEY_CODE, DEFAULT_ACTIVATION_KEY_NAME);
        setStopKey(DEFAULT_STOP_KEY_CODE, DEFAULT_STOP_KEY_NAME);
        setToggleKey(DEFAULT_TOGGLE_KEY_CODE, DEFAULT_TOGGLE_KEY_NAME);
        setConfigKey(DEFAULT_CONFIG_KEY_CODE, DEFAULT_CONFIG_KEY_NAME);
    }

    private void updateKeyMapping(KeyMapping mapping, int keyCode) {
        if (mapping != null) {
            try {
                InputConstants.Key key = createKey(keyCode);
                if (key != null) {
                    mapping.setKey(key);
                    KeyMapping.resetMapping();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static InputConstants.Key createKey(int keyCode) {
        try {
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
