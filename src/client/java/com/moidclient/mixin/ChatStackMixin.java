package com.moidclient.mixin;

import com.moidclient.utility.chatstack.ChatStackManager;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat Stack entry point: the private addMessage funnel (called by every
 * public add* method) routes through ChatStackManager.handle: on a repeat the previous line is replaced in
 * place, so a burst never grows past one line. Anything the manager does
 * not explicitly stack passes through untouched.
 *
 * Vanilla names verified against the 26.1, 26.2 and 26.3 deobfuscated jars
 * (addMessage funnel, allMessages, refreshTrimmedMessages, GuiMessage.content).
 */
@Mixin(ChatComponent.class)
public abstract class ChatStackMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void moidclient$stackSourced(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        try {
            if (ChatStackManager.handle((ChatComponent) (Object) this, message, signature, source, tag)) ci.cancel();
        } catch (Exception ignored) {}
    }
}
