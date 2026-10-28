package com.voice2text.fabric.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.VoiceToTextMod;
import com.voice2text.input.InputStateMachine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin intercepting key presses, rendering the compact audio-reactive visualizer,
 * and handling chat lifecycle interruptions in ChatScreen.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod == null || mod.getController() == null) {
            return;
        }

        InputStateMachine sm = mod.getController().getInputStateMachine();
        long now = System.currentTimeMillis();
        int key = event.key();

        // While Mode A Hold-to-Activate is actively recording, block EditBox key handling
        if (sm.isHoldToTalkRecording()) {
            cir.setReturnValue(true);
            return;
        }

        boolean isActivationKey = mod.getKeybindManager() != null
                ? mod.getKeybindManager().matchesActivationKey(event)
                : key == InputConstants.KEY_T;
        boolean isStopKey = mod.getKeybindManager() != null
                ? mod.getKeybindManager().matchesStopKey(event)
                : key == InputConstants.KEY_P;

        // Mode B Chat-Only Toggle
        if (isActivationKey) {
            sm.onChatToggleKey(now);
            cir.setReturnValue(true);
            return;
        } else if (isStopKey) {
            if (mod.getController().isDictationActive()) {
                boolean intercepted = sm.onStopKey(now);
                if (intercepted) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        } else if (key == InputConstants.KEY_ESCAPE) {
            if (mod.getController().isDictationActive()) {
                sm.onKeyEscape(now);
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onChatRemoved(CallbackInfo ci) {
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod != null && mod.getController() != null) {
            if (mod.getController().isDictationActive()) {
                mod.getController().getInputStateMachine().onInterrupted();
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod != null && mod.getListeningOverlay() != null) {
            mod.getListeningOverlay().render(extractor, this.font, this.width, this.height);
        }
    }
}
