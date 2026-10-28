package com.voice2text.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AudioLevelMeterTest {

    @Test
    public void testSilenceProducesZeroRms() {
        AudioLevelMeter meter = new AudioLevelMeter(0.05f);
        byte[] silence = new byte[1024]; // all 0
        float rms = meter.calculateRms(silence, silence.length);
        assertEquals(0.0f, rms, 0.0001f);
        assertFalse(meter.isVoiceActive(rms));
    }

    @Test
    public void testFullScaleProducesHighRms() {
        AudioLevelMeter meter = new AudioLevelMeter(0.05f);
        byte[] loud = new byte[1024];

        // Fill with full-scale square wave alternating +30000 and -30000
        for (int i = 0; i < loud.length - 1; i += 2) {
            short sample = (i % 4 == 0) ? (short) 30000 : (short) -30000;
            loud[i] = (byte) (sample & 0xFF);
            loud[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        float rms = meter.calculateRms(loud, loud.length);
        assertTrue(rms > 0.8f, "Loud audio should produce high normalized RMS");
        assertTrue(meter.isVoiceActive(rms));
    }

    @Test
    public void testSmoothedLevelTransitionsSmoothly() {
        AudioLevelMeter meter = new AudioLevelMeter();
        assertEquals(0.0f, meter.getSmoothedLevel(), 0.0001f);

        float s1 = meter.updateSmoothedLevel(1.0f);
        assertTrue(s1 > 0.2f && s1 < 0.5f, "Smoothing should prevent instantaneous jump to 1.0");

        float s2 = meter.updateSmoothedLevel(1.0f);
        assertTrue(s2 > s1, "Consecutive high levels should gradually climb");
    }

    @Test
    public void testDecibelCalculations() {
        assertEquals(0.0, AudioLevelMeter.toDecibels(1.0f), 0.01);
        assertTrue(AudioLevelMeter.toDecibels(0.000001f) <= -96.0);
    }
}
