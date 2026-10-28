package com.voice2text.fabric.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.VoiceToTextMod;
import com.voice2text.input.InputStateMachine;
import com.voice2text.input.KeyAction;
import com.voice2text.ui.VoiceConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting keyboard input for:
 * - Mode A: Gameplay V Hold-to-Activate (500ms threshold, natural movement with W, A, S, D, jump, crouch, sprint).
 * - Mode B: Chat-Only Toggle Recording (only in ChatScreen, activation key doesn't leak into message).
 * - Stop & Draft ('P'), Escape safe cancellation, and character isolation during active dictation.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod == null || mod.getController() == null) {
            return;
        }

        InputStateMachine sm = mod.getController().getInputStateMachine();
        long now = System.currentTimeMillis();

        // Evaluate 500ms hold threshold if waiting
        if (sm.isWaitingForHold()) {
            sm.checkHoldThreshold(now);
        }

        Screen screen = minecraft.gui != null ? minecraft.gui.screen() : null;

        boolean isActivationKey = mod.getKeybindManager() != null
                ? mod.getKeybindManager().matchesActivationKey(event)
                : event.key() == InputConstants.KEY_T;

        boolean isHoldToTalkKey = mod.getKeybindManager() != null
                ? mod.getKeybindManager().matchesToggleKey(event)
                : event.key() == InputConstants.KEY_V;

        boolean isStopKey = mod.getKeybindManager() != null
                ? mod.getKeybindManager().matchesStopKey(event)
                : event.key() == InputConstants.KEY_P;

        boolean isConfigKey = mod.getKeybindManager() != null
                ? mod.getKeybindManager().matchesConfigKey(event)
                : event.key() == InputConstants.KEY_O;

        // ==========================================
        // CASE 1: Gameplay (Chat is closed, screen == null)
        // ==========================================
        if (screen == null) {
            boolean isHoldAction = isHoldToTalkKey;
            com.voice2text.config.VoiceToTextConfig cfg = mod.getConfig();
            if (cfg != null && "HOLD_TO_ACTIVATE".equalsIgnoreCase(cfg.getRecordingMode()) && isActivationKey) {
                isHoldAction = true;
            }

            // Mode A Hold-to-Activate (Default: V)
            if (isHoldAction) {
                if (action == 1) { // PRESS -> begins 500ms threshold
                    sm.onHoldToTalkDown(now);
                    ci.cancel();
                    return;
                } else if (action == 0) { // RELEASE -> cancel pending or stop recording
                    sm.onHoldToTalkUp(now);
                    ci.cancel();
                    return;
                }
            }

            // Stop & Draft Key ('P')
            if (isStopKey && action == 1) {
                if (sm.isDictationActive()) {
                    boolean intercepted = sm.onStopKey(now);
                    if (intercepted) {
                        ci.cancel();
                        return;
                    }
                }
            }

            // Config Key ('O')
            if (isConfigKey && action == 1) {
                minecraft.execute(() -> minecraft.gui.setScreen(new VoiceConfigScreen(null)));
                ci.cancel();
                return;
            }

            // Escape Key
            if (event.key() == InputConstants.KEY_ESCAPE && action == 1) {
                if (sm.isWaitingForHold() || sm.isDictationActive()) {
                    sm.onKeyEscape(now);
                    ci.cancel();
                    return;
                }
            }

            // Note: Mode B Activation Key (T) is deliberately NOT intercepted when chat is closed.
            // Vanilla Minecraft processes T to open chat normally.
            return;
        }

        // ==========================================
        // CASE 2: Chat Screen is Open
        // ==========================================
        if (screen instanceof ChatScreen) {
            // Subcase 2A: Mode A Hold-to-Activate is actively recording
            if (sm.isHoldToTalkRecording()) {
                boolean isHoldAction = isHoldToTalkKey;
                com.voice2text.config.VoiceToTextConfig cfg = mod.getConfig();
                if (cfg != null && "HOLD_TO_ACTIVATE".equalsIgnoreCase(cfg.getRecordingMode()) && isActivationKey) {
                    isHoldAction = true;
                }
                // Key Released -> Stop recording and finalize
                if (isHoldAction && action == 0) {
                    sm.onHoldToTalkUp(now);
                    releaseMovementKeys();
                    ci.cancel();
                    return;
                }

                // Handle natural player movement while speaking
                Options opt = minecraft.options;
                boolean isMoveKey = false;
                boolean isDown = (action != 0);

                if (opt != null) {
                    if (opt.keyUp.matches(event)) { opt.keyUp.setDown(isDown); isMoveKey = true; }
                    else if (opt.keyDown.matches(event)) { opt.keyDown.setDown(isDown); isMoveKey = true; }
                    else if (opt.keyLeft.matches(event)) { opt.keyLeft.setDown(isDown); isMoveKey = true; }
                    else if (opt.keyRight.matches(event)) { opt.keyRight.setDown(isDown); isMoveKey = true; }
                    else if (opt.keyJump.matches(event)) { opt.keyJump.setDown(isDown); isMoveKey = true; }
                    else if (opt.keyShift.matches(event)) { opt.keyShift.setDown(isDown); isMoveKey = true; }
                    else if (opt.keySprint.matches(event)) { opt.keySprint.setDown(isDown); isMoveKey = true; }
                }

                if (isMoveKey) {
                    KeyMapping.set(InputConstants.getKey(event), isDown);
                    ci.cancel(); // Prevent movement keys from typing into chat
                    return;
                }

                // Stop key during Mode A
                if (isStopKey && action == 1) {
                    sm.onStopKey(now);
                    releaseMovementKeys();
                    ci.cancel();
                    return;
                }

                // Escape key cancels Mode A
                if (event.key() == InputConstants.KEY_ESCAPE && action == 1) {
                    sm.onKeyEscape(now);
                    releaseMovementKeys();
                    ci.cancel();
                    return;
                }

                // Suppress Enter so message isn't prematurely submitted while speaking
                if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                    ci.cancel();
                    return;
                }

                // Suppress other ordinary typing keys while voice owns text input
                ci.cancel();
                return;
            }

            // Subcase 2B: Mode B Chat-Only Toggle Recording
            if (isActivationKey && action == 1) {
                KeyAction keyAction = sm.onChatToggleKey(now);
                if (keyAction != KeyAction.NONE) {
                    ci.cancel(); // Consume key so 'T' is not inserted into message
                    return;
                }
            }

            // Stop Key ('P') while dictating in chat
            if (isStopKey && sm.isDictationActive() && action == 1) {
                boolean intercepted = sm.onStopKey(now);
                if (intercepted) {
                    ci.cancel();
                    return;
                }
            }

            // Escape Key while dictating in chat
            if (event.key() == InputConstants.KEY_ESCAPE && sm.isDictationActive() && action == 1) {
                sm.onKeyEscape(now);
                ci.cancel();
                return;
            }

            // Note: In ordinary chat, 'V' is NOT intercepted so 'v' types normally.
            return;
        }

        // ==========================================
        // CASE 3: Other Screens (Inventory, Pause, etc.)
        // ==========================================
        if (sm.isHoldToTalkActive()) {
            sm.onInterrupted();
            releaseMovementKeys();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTyped(long window, CharacterEvent event, CallbackInfo ci) {
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod != null && mod.getController() != null) {
            InputStateMachine sm = mod.getController().getInputStateMachine();
            // Completely prevent typed characters from leaking into chat while Mode A dictation owns input
            if (sm != null && sm.isHoldToTalkRecording()) {
                ci.cancel();
            }
        }
    }

    private void releaseMovementKeys() {
        if (minecraft.options != null) {
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyDown.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
            minecraft.options.keyJump.setDown(false);
            minecraft.options.keyShift.setDown(false);
            minecraft.options.keySprint.setDown(false);
        }
    }
}
