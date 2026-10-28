package com.voice2text.fabric.mixin;

import com.voice2text.VoiceToTextMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin injecting into Minecraft's in-game HUD to render SpeakCraft's
 * ListeningOverlay when voice recognition is active during gameplay outside chat.
 */
@Mixin(Hud.class)
public class HudMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        // If a GUI screen is open, the screen handles its own rendering
        if (minecraft.gui != null && minecraft.gui.screen() != null) {
            return;
        }

        VoiceToTextMod mod = VoiceToTextMod.getInstance();
        if (mod != null && mod.getListeningOverlay() != null) {
            int width = minecraft.getWindow().getGuiScaledWidth();
            int height = minecraft.getWindow().getGuiScaledHeight();
            mod.getListeningOverlay().render(extractor, minecraft.font, width, height);
        }
    }
}
