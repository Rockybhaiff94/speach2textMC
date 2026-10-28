package com.voice2text.ui;

import com.voice2text.VoiceToTextController;
import com.voice2text.fabric.mixin.ChatScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

/**
 * Voice-to-Text Recording Indicator (Transparent Floating Dots & Live Chat Overlay):
 *
 * - 100% transparent background (no panel, no box, no border).
 * - 4 circular dots anchored to the right side of the chat input or temporary overlay.
 * - Grey stationary dots (#929292) during silence.
 * - Green animated glowing dots (#39FF88) with vertical wave motion during speech.
 * - Calibrated audio VAD with hysteresis (0.014 enter / 0.007 exit) and 280ms release timeout.
 * - Louder speech produces more noticeable vertical movement and brighter glow.
 * - Separate Minecraft-style temporary chat bar with live streaming transcription.
 */
public class ListeningOverlay {

    public static final int DOT_COUNT = 4;
    public static final int DOT_SIZE = 4;
    public static final int DOT_SPACING = 7; // Center-to-center spacing (3px gap)
    public static final int RIGHT_MARGIN = 8; // Margin from the right edge of chat input

    // Color definitions
    public static final int COLOR_IDLE_GREY = 0xFF929292;
    public static final int COLOR_ACTIVE_GREEN = 0xFF39FF88;
    public static final int COLOR_FINALIZING_AMBER = 0xFFFFD600;

    // Calibrated VAD & Hysteresis thresholds for system microphones
    public static final float VAD_SPEECH_ENTER_THRESHOLD = 0.014f;
    public static final float VAD_SPEECH_EXIT_THRESHOLD = 0.007f;
    public static final long SPEECH_RELEASE_TIMEOUT_MS = 280L;

    private final VoiceToTextController controller;

    // Audio & VAD tracking
    private float smoothedRms = 0.0f;
    private long lastSpeechDetectedTime = 0L;
    private boolean speechActive = false;
    private float speechFactor = 0.0f; // 0.0f (idle grey) to 1.0f (active speech green)

    public ListeningOverlay(VoiceToTextController controller) {
        this.controller = controller;
    }

    /**
     * Updates speech detection and interpolation factors based on current audio level and timestamp.
     * Exposed for deterministic testing and animation updates.
     */
    public void updateAudioState(float currentRms, long now, boolean isFinalizing) {
        // Smooth raw RMS to drive fluid wave amplitude rendering
        smoothedRms = (0.35f * currentRms) + (0.65f * smoothedRms);

        boolean hwSpeechDetected = controller != null && controller.isSpeechDetected();

        // Voice Activity Detection with hysteresis and release timeout
        if (currentRms >= VAD_SPEECH_ENTER_THRESHOLD || hwSpeechDetected) {
            speechActive = true;
            lastSpeechDetectedTime = now;
        } else if (speechActive) {
            if (currentRms < VAD_SPEECH_EXIT_THRESHOLD) {
                if (now - lastSpeechDetectedTime >= SPEECH_RELEASE_TIMEOUT_MS) {
                    speechActive = false;
                }
            } else {
                // In hysteresis zone [VAD_SPEECH_EXIT_THRESHOLD, VAD_SPEECH_ENTER_THRESHOLD)
                lastSpeechDetectedTime = now;
            }
        }

        // Determine target speech factor [0.0, 1.0]
        float targetFactor;
        if (isFinalizing) {
            targetFactor = 0.0f;
        } else if (speechActive) {
            float loudnessBoost = Math.min(1.0f, Math.max(0.0f, (smoothedRms - VAD_SPEECH_ENTER_THRESHOLD) / 0.05f));
            targetFactor = 0.70f + (0.30f * loudnessBoost);
        } else {
            targetFactor = 0.0f;
        }

        // Smooth interpolation towards target factor
        if (speechFactor < targetFactor) {
            speechFactor += (targetFactor - speechFactor) * 0.30f;
        } else {
            speechFactor += (targetFactor - speechFactor) * 0.20f;
        }

        if (speechFactor < 0.01f) {
            speechFactor = 0.0f;
        }
    }

    /**
     * Computes the interpolated dot color between neutral grey (#929292) and active green (#39FF88),
     * or amber if finalizing.
     */
    public int getDotColor(float factor, boolean isFinalizing) {
        if (isFinalizing) {
            return COLOR_FINALIZING_AMBER;
        }
        int r = (int) (146 + (57 - 146) * factor);
        int g = (int) (146 + (255 - 146) * factor);
        int b = (int) (146 + (136 - 146) * factor);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Calculates the vertical wave offset for a given dot index.
     * Louder speech produces noticeably more movement.
     * When speechFactor is 0.0 (idle), returns strictly 0.0f (stationary).
     */
    public float calculateDotOffsetY(int dotIndex, long now, float factor, float rms) {
        if (factor <= 0.001f) {
            return 0.0f;
        }
        double phase = (now * 0.008) + (dotIndex * 0.80);
        float wave = (float) Math.sin(phase);
        // Louder speech visibly increases vertical travel
        float maxAmplitude = 1.8f + Math.min(3.2f, rms * 60.0f);
        return wave * maxAmplitude * factor;
    }

    /**
     * Main render entrypoint rendering the transparent audio-reactive 4-dot overlay
     * and temporary Minecraft-style chat bar when dictating outside chat.
     */
    public void render(GuiGraphicsExtractor extractor, Font font, int screenWidth, int screenHeight) {
        if (controller == null || extractor == null) {
            return;
        }

        boolean isActive = controller.isDictationActive();
        String status = controller.getStatusMessage();
        boolean isFinalizing = status != null && status.toLowerCase().contains("finaliz");

        // Clean disappearance when inactive
        if (!isActive && !isFinalizing) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        float currentRms = controller.getCurrentAudioLevel();
        updateAudioState(currentRms, now, isFinalizing);

        renderLayout2(extractor, font, screenWidth, screenHeight, now, isFinalizing);
    }

    /**
     * Renders the temporary Minecraft-style chat bar during gameplay dictation when vanilla chat is closed.
     * Matches vanilla Minecraft chat EditBox appearance: translucent dark background, vanilla font,
     * cursor, and smoothly streaming live transcript.
     */
    public void renderTemporaryChatBar(GuiGraphicsExtractor extractor, Font font, int screenWidth, int screenHeight, long now, int reservedRightWidth) {
        if (font == null || controller == null) {
            return;
        }

        int barX = 2;
        int barY = Math.max(2, screenHeight - 14);
        int barWidth = Math.max(100, screenWidth - 4);
        int barHeight = 12;

        // 1. Translucent dark background matching vanilla Minecraft chat EditBox (0x80000000 = 50% black)
        extractor.fill(barX, barY, barX + barWidth, barY + barHeight, 0x80000000);

        // 2. Fetch live text from transcription buffer
        String displayText = "";
        if (controller.getTranscriptionBuffer() != null) {
            displayText = controller.getTranscriptionBuffer().getDisplayText();
        }

        boolean showCursor = (now % 800) < 400;
        String textToDraw = (displayText != null ? displayText : "") + (showCursor ? "_" : "");

        int textX = barX + 2;
        int textY = barY + 2;
        int maxTextWidth = barWidth - 6 - Math.max(0, reservedRightWidth);

        if (maxTextWidth > 20 && font.width(textToDraw) > maxTextWidth) {
            // Keep recent spoken words visible at the cursor
            textToDraw = font.plainSubstrByWidth(textToDraw, maxTextWidth, true);
        }

        if (!textToDraw.isEmpty()) {
            extractor.text(font, textToDraw, textX, textY, 0xFFFFFFFF, true);
        }
    }

    /**
     * Renders Layout 2: Compact, transparent overlay with custom Minecraft-style chat input
     * and four audio-reactive circular dots anchored to the right side.
     */
    public void renderLayout2(GuiGraphicsExtractor extractor, Font font, int screenWidth, int screenHeight, long now, boolean isFinalizing) {
        boolean chatOpen = false;
        int boxRight = screenWidth - 4;
        int boxY = Math.max(2, screenHeight - 14);
        int boxHeight = 12;

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gui != null && mc.gui.screen() instanceof ChatScreenAccessor accessor) {
                EditBox editBox = accessor.voice2text$getInput();
                if (editBox != null) {
                    boxRight = editBox.getRight();
                    boxY = editBox.getY();
                    boxHeight = editBox.getHeight();
                    chatOpen = true;
                }
            }
        } catch (Throwable ignored) {
        }

        int totalSpan = (DOT_COUNT - 1) * DOT_SPACING;
        int reservedDotWidth = totalSpan + RIGHT_MARGIN + 6;

        // When gameplay dictation is active outside chat, render the temporary Minecraft-style chat input
        if (!chatOpen) {
            renderTemporaryChatBar(extractor, font, screenWidth, screenHeight, now, reservedDotWidth);
        }

        // Determine dot cluster position: anchored to the right of the chat bar
        int startCenterX = boxRight - RIGHT_MARGIN - totalSpan;
        int centerY = boxY + (boxHeight / 2);

        int dotColor = getDotColor(speechFactor, isFinalizing);

        // Render each circular dot and its soft glow (NO background panel or rectangle)
        for (int i = 0; i < DOT_COUNT; i++) {
            int cx = startCenterX + (i * DOT_SPACING);
            float offsetY = calculateDotOffsetY(i, now, speechFactor, smoothedRms);
            int cy = (int) Math.round(centerY + offsetY);

            int x0 = cx - 2;
            int y0 = cy - 2;

            // Soft radial green glow only when speech is active
            if (speechFactor > 0.05f && !isFinalizing) {
                renderSoftGlow(extractor, x0, y0, speechFactor, smoothedRms);
            }

            // Draw smooth 4x4 circular dot
            renderCircularDot(extractor, x0, y0, dotColor);
        }
    }

    /**
     * Draws a 4x4 anti-aliased circular dot.
     */
    private void renderCircularDot(GuiGraphicsExtractor extractor, int x0, int y0, int color) {
        // Cross pattern for circular dot
        extractor.fill(x0 + 1, y0, x0 + 3, y0 + 1, color);     // Row 0 (2px wide)
        extractor.fill(x0, y0 + 1, x0 + 4, y0 + 2, color);     // Row 1 (4px wide)
        extractor.fill(x0, y0 + 2, x0 + 4, y0 + 3, color);     // Row 2 (4px wide)
        extractor.fill(x0 + 1, y0 + 3, x0 + 3, y0 + 4, color); // Row 3 (2px wide)

        // Subtle anti-aliased corner pixels (35% alpha)
        int cornerAlpha = (int) (0.35f * ((color >>> 24) & 0xFF));
        if (cornerAlpha > 0) {
            int cornerColor = (cornerAlpha << 24) | (color & 0x00FFFFFF);
            extractor.fill(x0, y0, x0 + 1, y0 + 1, cornerColor);
            extractor.fill(x0 + 3, y0, x0 + 4, y0 + 1, cornerColor);
            extractor.fill(x0, y0 + 3, x0 + 1, y0 + 4, cornerColor);
            extractor.fill(x0 + 3, y0 + 3, x0 + 4, y0 + 4, cornerColor);
        }
    }

    /**
     * Renders a soft radial green glow with volume-scaled intensity and no rectangular halo.
     */
    private void renderSoftGlow(GuiGraphicsExtractor extractor, int x0, int y0, float factor, float rms) {
        float loudnessBoost = Math.min(1.0f, Math.max(0.0f, rms * 30.0f));
        int innerAlpha = Math.min(255, (int) ((45 + (30 * loudnessBoost)) * factor));

        if (innerAlpha > 0) {
            int glowColor1 = (innerAlpha << 24) | 0x39FF88;
            extractor.fill(x0 + 1, y0 - 1, x0 + 3, y0, glowColor1);
            extractor.fill(x0 + 1, y0 + 4, x0 + 3, y0 + 5, glowColor1);
            extractor.fill(x0 - 1, y0 + 1, x0, y0 + 3, glowColor1);
            extractor.fill(x0 + 4, y0 + 1, x0 + 5, y0 + 3, glowColor1);
        }

        int outerAlpha = Math.min(255, (int) ((20 + (15 * loudnessBoost)) * factor));
        if (outerAlpha > 0) {
            int glowColor2 = (outerAlpha << 24) | 0x39FF88;
            extractor.fill(x0 + 1, y0 - 2, x0 + 3, y0 - 1, glowColor2);
            extractor.fill(x0 + 1, y0 + 5, x0 + 3, y0 + 6, glowColor2);
            extractor.fill(x0 - 2, y0 + 1, x0 - 1, y0 + 3, glowColor2);
            extractor.fill(x0 + 5, y0 + 1, x0 + 6, y0 + 3, glowColor2);
        }
    }

    public void reset() {
        this.smoothedRms = 0.0f;
        this.speechActive = false;
        this.speechFactor = 0.0f;
        this.lastSpeechDetectedTime = 0L;
    }

    public boolean isSpeechActive() {
        return speechActive;
    }

    public float getSpeechFactor() {
        return speechFactor;
    }

    public float getSmoothedRms() {
        return smoothedRms;
    }
}
