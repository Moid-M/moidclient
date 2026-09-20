package com.moidclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Read access to the chat log for Chat Stack line replacement.
 * Field name verified against the 26.1, 26.2 and 26.3 deobfuscated jars.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> moidclient$allMessages();

    @Invoker("refreshTrimmedMessages")
    void moidclient$refreshTrimmedMessages();

    @Invoker("addMessage")
    void moidclient$addMessage(Component message, MessageSignature signature,
                               GuiMessageSource source, GuiMessageTag tag);
}
