package com.voice2text.fabric.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin accessor to safely access the private input EditBox in ChatScreen.
 */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {

    @Accessor("input")
    EditBox voice2text$getInput();
}
