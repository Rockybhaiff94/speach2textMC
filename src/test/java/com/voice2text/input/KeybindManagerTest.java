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
    }

    @Test
    public void testAutomaticNameResolutionWhenNullOrEmpty() {
        keybindManager.setActivationKey(InputConstants.KEY_G, null);
        assertEquals("G", keybindManager.getActivationKeyName());

        keybindManager.setStopKey(InputConstants.KEY_K, "   ");
        assertEquals("K", keybindManager.getStopKeyName());
    }
}
