package com.voice2text.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.VoiceToTextMod;
import com.voice2text.config.VoiceToTextConfig;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class KeybindManagerTest {

    private VoiceToTextConfig config;
    private KeybindManager keybindManager;

    @BeforeEach
    public void setUp() {
        config = new VoiceToTextConfig();
        config.validate();
        keybindManager = new KeybindManager(config);
    }

    @Test
    public void testSpeakCraftCategoryRegistered() {
        assertNotNull(VoiceToTextMod.VOICE2TEXT_CATEGORY);
        assertNotNull(VoiceToTextMod.SPEAKCRAFT_CATEGORY);
        assertEquals(Identifier.fromNamespaceAndPath("voice2text", "main"), VoiceToTextMod.VOICE2TEXT_CATEGORY.id());
        assertEquals(Identifier.fromNamespaceAndPath("voice2text", "main"), VoiceToTextMod.SPEAKCRAFT_CATEGORY.id());
    }

    @Test
    public void testDefaultKeybinds() {
        assertEquals(InputConstants.KEY_T, keybindManager.getActivationKeyCode());
        assertEquals("T", keybindManager.getActivationKeyName());

        assertEquals(InputConstants.KEY_P, keybindManager.getStopKeyCode());
        assertEquals("P", keybindManager.getStopKeyName());

        assertEquals(InputConstants.KEY_V, keybindManager.getToggleKeyCode());
        assertEquals("V", keybindManager.getToggleKeyName());

        assertEquals(InputConstants.KEY_O, keybindManager.getConfigKeyCode());
        assertEquals("O", keybindManager.getConfigKeyName());

        assertTrue(keybindManager.matchesActivationKey(InputConstants.KEY_T));
        assertFalse(keybindManager.matchesActivationKey(InputConstants.KEY_P));

        assertTrue(keybindManager.matchesStopKey(InputConstants.KEY_P));
        assertFalse(keybindManager.matchesStopKey(InputConstants.KEY_T));

        assertTrue(keybindManager.matchesToggleKey(InputConstants.KEY_V));
        assertFalse(keybindManager.matchesToggleKey(InputConstants.KEY_O));

        assertTrue(keybindManager.matchesConfigKey(InputConstants.KEY_O));
        assertFalse(keybindManager.matchesConfigKey(InputConstants.KEY_V));
    }

    @Test
    public void testSetCustomKeybinds() {
        // Change activation key to 'G'
        keybindManager.setActivationKey(InputConstants.KEY_G, "G");
        assertEquals(InputConstants.KEY_G, keybindManager.getActivationKeyCode());
        assertEquals("G", keybindManager.getActivationKeyName());
        assertTrue(keybindManager.matchesActivationKey(InputConstants.KEY_G));
        assertFalse(keybindManager.matchesActivationKey(InputConstants.KEY_T));

        // Change stop key to 'K'
        keybindManager.setStopKey(InputConstants.KEY_K, "K");
        assertEquals(InputConstants.KEY_K, keybindManager.getStopKeyCode());
        assertEquals("K", keybindManager.getStopKeyName());
        assertTrue(keybindManager.matchesStopKey(InputConstants.KEY_K));
        assertFalse(keybindManager.matchesStopKey(InputConstants.KEY_P));

        // Change toggle key to 'B'
        keybindManager.setToggleKey(InputConstants.KEY_B, "B");
        assertEquals(InputConstants.KEY_B, keybindManager.getToggleKeyCode());
        assertEquals("B", keybindManager.getToggleKeyName());
        assertTrue(keybindManager.matchesToggleKey(InputConstants.KEY_B));

        // Change config key to 'M'
        keybindManager.setConfigKey(InputConstants.KEY_M, "M");
        assertEquals(InputConstants.KEY_M, keybindManager.getConfigKeyCode());
        assertEquals("M", keybindManager.getConfigKeyName());
        assertTrue(keybindManager.matchesConfigKey(InputConstants.KEY_M));
    }

    @Test
    public void testResetToDefaults() {
        // Customize keys first
        keybindManager.setActivationKey(InputConstants.KEY_G, "G");
        keybindManager.setStopKey(InputConstants.KEY_K, "K");
        keybindManager.setToggleKey(InputConstants.KEY_B, "B");
        keybindManager.setConfigKey(InputConstants.KEY_M, "M");

        assertEquals(InputConstants.KEY_G, keybindManager.getActivationKeyCode());

        // Reset
        keybindManager.resetToDefaults();

        assertEquals(InputConstants.KEY_T, keybindManager.getActivationKeyCode());
        assertEquals("T", keybindManager.getActivationKeyName());

        assertEquals(InputConstants.KEY_P, keybindManager.getStopKeyCode());
        assertEquals("P", keybindManager.getStopKeyName());

        assertEquals(InputConstants.KEY_V, keybindManager.getToggleKeyCode());
        assertEquals("V", keybindManager.getToggleKeyName());

        assertEquals(InputConstants.KEY_O, keybindManager.getConfigKeyCode());
        assertEquals("O", keybindManager.getConfigKeyName());
    }

    @Test
    public void testKeyNameResolution() {
        assertEquals("T", KeybindManager.resolveKeyName(InputConstants.KEY_T));
        assertEquals("P", KeybindManager.resolveKeyName(InputConstants.KEY_P));
        assertEquals("SPACE", KeybindManager.resolveKeyName(InputConstants.KEY_SPACE));
        assertEquals("ESCAPE", KeybindManager.resolveKeyName(InputConstants.KEY_ESCAPE));
        assertEquals("V", KeybindManager.resolveKeyName(InputConstants.KEY_V));
        assertEquals("O", KeybindManager.resolveKeyName(InputConstants.KEY_O));
        assertEquals("F6", KeybindManager.resolveKeyName(InputConstants.KEY_F6));
        assertTrue(KeybindManager.resolveKeyName(InputConstants.KEY_RSHIFT).toUpperCase().contains("SHIFT"));
    }

    @Test
    public void testLegacyNumericIdentifierMigration() {
        // Test normalization of internal-looking values such as key.keyboard.23, key.keyboard.25, etc.
        assertEquals("T", KeybindManager.normalizeKeyName("key.keyboard.23"));
        assertEquals("V", KeybindManager.normalizeKeyName("key.keyboard.25"));
        assertEquals("O", KeybindManager.normalizeKeyName("key.keyboard.18"));
        assertEquals("P", KeybindManager.normalizeKeyName("key.keyboard.19"));
        assertEquals("T", KeybindManager.normalizeKeyName("23"));
        assertEquals("V", KeybindManager.normalizeKeyName("25"));
        assertEquals("O", KeybindManager.normalizeKeyName("18"));
        assertEquals("P", KeybindManager.normalizeKeyName("19"));
        assertEquals("T", KeybindManager.normalizeKeyName("key.keyboard.84"));
        assertEquals("V", KeybindManager.normalizeKeyName("key.keyboard.86"));

        // Verify config migration
        VoiceToTextConfig legacyConfig = new VoiceToTextConfig();
        legacyConfig.setActivationKey("key.keyboard.23");
        legacyConfig.setDedicatedToggleKey("key.keyboard.25");
        legacyConfig.setConfigKey("18");
        legacyConfig.setStopKey("19");

        legacyConfig.validate();

        assertEquals("T", legacyConfig.getActivationKey());
        assertEquals(InputConstants.KEY_T, legacyConfig.getActivationKeyCode());
        assertEquals("V", legacyConfig.getDedicatedToggleKey());
        assertEquals(InputConstants.KEY_V, legacyConfig.getDedicatedToggleKeyCode());
        assertEquals("O", legacyConfig.getConfigKey());
        assertEquals(InputConstants.KEY_O, legacyConfig.getConfigKeyCode());
        assertEquals("P", legacyConfig.getStopKey());
        assertEquals(InputConstants.KEY_P, legacyConfig.getStopKeyCode());

        assertFalse(legacyConfig.getActivationKey().contains("key.keyboard."));
        assertFalse(legacyConfig.getDedicatedToggleKey().contains("key.keyboard."));
    }

    @Test
    public void testMouseKeybinds() {
        InputConstants.Key mouse4 = KeybindManager.createMouseKey(4);
        assertNotNull(mouse4);
        assertEquals("Mouse 4", KeybindManager.resolveKeyName(mouse4));

        InputConstants.Key mouse5 = KeybindManager.createMouseKey(5);
        assertEquals("Mouse 5", KeybindManager.resolveKeyName(mouse5));

        keybindManager.setActivationKey(4, "Mouse 4", true);
        assertEquals(4, keybindManager.getActivationKeyCode());
        assertEquals("Mouse 4", keybindManager.getActivationKeyName());
        assertTrue(config.isActivationKeyMouse());
    }

    @Test
    public void testAutomaticNameResolutionWhenNullOrEmpty() {
        keybindManager.setActivationKey(InputConstants.KEY_G, null);
        assertEquals("G", keybindManager.getActivationKeyName());

        keybindManager.setStopKey(InputConstants.KEY_K, "   ");
        assertEquals("K", keybindManager.getStopKeyName());
    }
}
