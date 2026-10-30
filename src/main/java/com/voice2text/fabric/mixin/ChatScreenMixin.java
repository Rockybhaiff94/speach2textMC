package com.voice2text.fabric.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.voice2text.VoiceToTextMod;
import com.voice2text.input.InputStateMachine;
import com.voice2text.model.SpeechModelInfo;
import com.voice2text.ui.ModelLibraryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private Button voice2text$liveButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void onChatInit(CallbackInfo ci) {
        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod == null || mod.getController() == null) {
            return;
        }

        int btnW = 54;
        int btnH = 14;
        int btnX = this.width - btnW - 4;
        int btnY = this.height - 30;

        boolean active = mod.getController().isDictationActive();
        String initialLabel = active ? "§c■ Live..." : "§a● Live";

        this.voice2text$liveButton = Button.builder(
                Component.literal(initialLabel),
                button -> {
                    String activeModelId = mod.getConfig().getActiveModelId();
                    if (mod.getModelManager().isModelInstalled(activeModelId)) {
                        // Model is installed -> toggle live dictation
                        if (mod.getController().isDictationActive()) {
                            mod.getController().stopAndKeepDraft();
                        } else {
                            mod.getController().startDictation();
                        }
                    } else {
                        // Model is missing -> explain and open Model Library
                        SpeechModelInfo info = mod.getModelManager().getModelInfo(activeModelId);
                        String modelName = info != null ? info.getDisplayName() : activeModelId;
                        mod.getChatBridge().displayClientMessage("§c[Voice2Text] Selected model '" + modelName + "' needs to be downloaded before using Live speech.", true);
                        if (Minecraft.getInstance() != null) {
                            Minecraft.getInstance().gui.setScreen(new ModelLibraryScreen(this, activeModelId));
                        }
                    }
                }
        ).tooltip(Tooltip.create(Component.literal("SpeakCraft Live Speech Recognition. Click to toggle dictation or open Model Library.")))
         .bounds(btnX, btnY, btnW, btnH).build();

        addRenderableWidget(this.voice2text$liveButton);
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
        if (mod != null && mod.getController() != null && this.voice2text$liveButton != null) {
            boolean active = mod.getController().isDictationActive();
            String label = active ? "§c■ Live..." : "§a● Live";
            this.voice2text$liveButton.setMessage(Component.literal(label));
        }

        if (mod != null && mod.getListeningOverlay() != null) {
            mod.getListeningOverlay().render(extractor, this.font, this.width, this.height);
        }
    }
}
