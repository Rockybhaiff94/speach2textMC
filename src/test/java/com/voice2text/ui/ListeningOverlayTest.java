package com.voice2text.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ListeningOverlayTest {

    private ListeningOverlay overlay;

    @BeforeEach
    void setUp() {
        overlay = new ListeningOverlay(null);
    }

    @Test
    void testIdleStateWhenNoSpeechDetected() {
        long now = 1000L;
        // Feed realistic ambient silence (~0.003, well below 0.014 entrance threshold)
        overlay.updateAudioState(0.003f, now, false);

        assertFalse(overlay.isSpeechActive(), "Speech should not be active for background noise");
        assertEquals(0.0f, overlay.getSpeechFactor(), 0.01f, "Speech factor should remain 0 when idle");

        // Verify color is exact neutral grey (#929292)
        int color = overlay.getDotColor(overlay.getSpeechFactor(), false);
        assertEquals(ListeningOverlay.COLOR_IDLE_GREY, color, "Idle color must be #929292");

        // Verify all 4 dots are stationary (offset == 0)
        for (int i = 0; i < ListeningOverlay.DOT_COUNT; i++) {
            float offsetY = overlay.calculateDotOffsetY(i, now, overlay.getSpeechFactor(), overlay.getSmoothedRms());
            assertEquals(0.0f, offsetY, 0.0001f, "Idle dots must be stationary with zero vertical offset");
        }
    }

    @Test
    void testCalibratedRealWorldSpeechThresholds() {
        long now = 1000L;
        // Ambient noise (~0.003) should not trigger speech
        overlay.updateAudioState(0.003f, now, false);
        assertFalse(overlay.isSpeechActive(), "Ambient noise must remain idle");

        // Normal conversational speech (~0.025) should trigger speech
        overlay.updateAudioState(0.025f, now + 50, false);
        assertTrue(overlay.isSpeechActive(), "Normal conversational speech (0.025) must activate speech state");
    }

    @Test
    void testLouderSpeechProducesMoreMovement() {
        long now = 1000L;
        // 1. Quiet speech offset
        float quietOffset = overlay.calculateDotOffsetY(0, now, 1.0f, 0.015f);

        // 2. Loud speech offset
        float loudOffset = overlay.calculateDotOffsetY(0, now, 1.0f, 0.080f);

        assertTrue(Math.abs(loudOffset) > Math.abs(quietOffset),
                "Louder speech must produce greater vertical movement: quiet=" + quietOffset + ", loud=" + loudOffset);
    }

    @Test
    void testSpeechDetectedTransitionsToGreenAndAnimates() {
        long now = 1000L;
        // User speaks with loud voice (RMS 0.12)
        for (int frame = 0; frame < 5; frame++) {
            overlay.updateAudioState(0.12f, now + (frame * 16), false);
        }

        assertTrue(overlay.isSpeechActive(), "Speech should be active when audio exceeds VAD threshold");
        assertTrue(overlay.getSpeechFactor() > 0.5f, "Speech factor should climb towards 1.0");

        // Verify color transitions towards emerald green
        int color = overlay.getDotColor(overlay.getSpeechFactor(), false);
        int greenComponent = (color >> 8) & 0xFF;
        assertTrue(greenComponent > 200, "Green component should be high during active speech");

        // Verify dots animate vertically with different offsets (wave motion)
        float offset0 = overlay.calculateDotOffsetY(0, now + 100, overlay.getSpeechFactor(), overlay.getSmoothedRms());
        float offset1 = overlay.calculateDotOffsetY(1, now + 100, overlay.getSpeechFactor(), overlay.getSmoothedRms());
        float offset2 = overlay.calculateDotOffsetY(2, now + 100, overlay.getSpeechFactor(), overlay.getSmoothedRms());

        // At least some dots must have non-zero vertical offset
        assertTrue(Math.abs(offset0) > 0.01f || Math.abs(offset1) > 0.01f || Math.abs(offset2) > 0.01f,
                "Dots must move vertically during speech");
        // Verify phase separation between dots (not moving identically)
        assertNotEquals(offset0, offset1, 0.001f, "Dots must have different wave phases");
    }

    @Test
    void testReleaseTimeoutPreventsFlickeringDuringWordPauses() {
        long now = 1000L;
        // 1. Speak
        for (int frame = 0; frame < 5; frame++) {
            overlay.updateAudioState(0.10f, now + (frame * 16), false);
        }
        assertTrue(overlay.isSpeechActive());

        long pauseTime = now + 100;
        // 2. Pause for 150 ms (within 280ms release timeout) with silence
        overlay.updateAudioState(0.003f, pauseTime, false);
        assertTrue(overlay.isSpeechActive(), "Speech should remain active during short pause between words");

        // 3. Pause exceeds release timeout (e.g., 350 ms silence)
        overlay.updateAudioState(0.003f, pauseTime + 350, false);
        assertFalse(overlay.isSpeechActive(), "Speech should become inactive after release timeout expires");

        // Let factor decay to 0 (approx 0.5s at 60 FPS)
        for (int frame = 0; frame < 30; frame++) {
            overlay.updateAudioState(0.002f, pauseTime + 400 + (frame * 16), false);
        }
        assertEquals(0.0f, overlay.getSpeechFactor(), 0.0001f, "Speech factor should decay back to 0");
    }

    @Test
    void testFinalizingStateDisplaysAmberAndStopsAnimation() {
        long now = 2000L;
        overlay.updateAudioState(0.0f, now, true);

        // Verify color is amber
        int color = overlay.getDotColor(overlay.getSpeechFactor(), true);
        assertEquals(ListeningOverlay.COLOR_FINALIZING_AMBER, color, "Finalizing state must be amber");

        // Verify dots do not animate
        float offset = overlay.calculateDotOffsetY(0, now, overlay.getSpeechFactor(), overlay.getSmoothedRms());
        assertEquals(0.0f, offset, 0.0001f, "Finalizing dots must not animate");
    }

    @Test
    void testResetRestoresAllValues() {
        overlay.updateAudioState(0.15f, 1000L, false);
        assertTrue(overlay.isSpeechActive());

        overlay.reset();
        assertFalse(overlay.isSpeechActive());
        assertEquals(0.0f, overlay.getSpeechFactor(), 0.0001f);
        assertEquals(0.0f, overlay.getSmoothedRms(), 0.0001f);
    }

    @Test
    void testDotGeometryConstants() {
        assertEquals(4, ListeningOverlay.DOT_COUNT, "Layout 2 must feature exactly 4 dots");
        assertEquals(4, ListeningOverlay.DOT_SIZE, "Dot size should be 4px");
        assertEquals(7, ListeningOverlay.DOT_SPACING, "Dot spacing should be 7px center-to-center");
        assertEquals(0xFF929292, ListeningOverlay.COLOR_IDLE_GREY);
        assertEquals(0xFF39FF88, ListeningOverlay.COLOR_ACTIVE_GREEN);
        assertEquals(0xFFFFD600, ListeningOverlay.COLOR_FINALIZING_AMBER);
    }
}
