package com.moidclient.utility.chatstack;

import com.moidclient.ClientMod;
import com.moidclient.config.ConfigManager;
import com.moidclient.mixin.ChatComponentAccessor;
import com.moidclient.module.ModuleDef;
import com.moidclient.module.ModuleOption;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.List;

/**
 * Chat Stack - compacts repeated chat lines. Consecutive identical messages
 * collapse into a single line with a [xN] counter instead of spamming chat:
 * {@code <MoidM> test} twice shows as {@code [x2] <MoidM> test}.
 *
 * How it works: ChatStackMixin intercepts every ChatComponent.addMessage.
 * On a repeat, the previously added line is removed (only if it is still
 * the one we added - content-guarded) and a stacked replacement is added
 * instead, so a burst never grows past one line. Fail-open by design: any
 * mistake lets the message through untouched.
 * Category: Utility
 */
public final class ChatStackManager {
    private ChatStackManager() {}

    private static String lastKey = null;
    private static int count = 0;
    private static String lastAdded = null;
    private static boolean replacing = false;

    public static ModuleDef definition() {
        return new ModuleDef("chatStack", "Chat Stack",
                "Stacks repeated chat lines with a [xN] counter.",
                "utility", false, "chat", false,
                ModuleOption.list(
                    ModuleOption.select("chatStackFormat", "Counter style",
                        List.of("prefix", "suffix"), "prefix"),
                    ModuleOption.slider("chatStackMax", "Max stack", 2, 20, 1, 10)
                ));
    }

    public static boolean isEnabled() {
        try {
            ClientMod inst = ClientMod.getInstance();
            ConfigManager config = inst != null ? inst.getConfigManager() : null;
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("chatStack") : null;
            return mod != null && mod.enabled;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * @return true if the caller (mixin) must cancel the original addMessage.
     */
    public static boolean handle(ChatComponent chat, Component message,
                                 MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
        try {
            if (chat == null || message == null) return false;
            if (replacing) return false; // our own re-add passes through untouched
            ClientMod inst = ClientMod.getInstance();
            ConfigManager config = inst != null ? inst.getConfigManager() : null;
            ConfigManager.ModuleConfig mod = config != null ? config.getModule("chatStack") : null;
            if (mod == null || !mod.enabled) {
                reset();
                return false;
            }
            String key = message.getString();
            int max = mod.optInt("chatStackMax", 10);
            if (max < 2) max = 2;
            if (max > 99) max = 99;
            boolean suffix = "suffix".equals(mod.optString("chatStackFormat", "prefix"));
            if (lastKey != null && lastKey.equals(key) && count < max) {
                count++;
                Component stacked = suffix
                        ? message.copy().append(Component.literal(" [x" + count + "]"))
                        : Component.literal("[x" + count + "] ").append(message.copy());
                removeLastIfOurs(chat);
                replacing = true;
                try {
                    ((ChatComponentAccessor) chat).moidclient$addMessage(stacked, signature, source, tag);
                } finally {
                    replacing = false;
                }
                lastKey = key;
                lastAdded = stacked.getString();
                return true;
            }
            count = 1;
            lastKey = key;
            lastAdded = message.getString();
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Removes the last chat line, but only if it is still the one we added. */
    private static void removeLastIfOurs(ChatComponent chat) {
        try {
            if (lastAdded == null) return;
            List<GuiMessage> all = ((ChatComponentAccessor) chat).moidclient$allMessages();
            if (all == null || all.isEmpty()) return;
            GuiMessage last = all.get(all.size() - 1);
            if (last == null || last.content() == null) return;
            if (!lastAdded.equals(last.content().getString())) return;
            all.remove(all.size() - 1);
            ((ChatComponentAccessor) chat).moidclient$refreshTrimmedMessages();
        } catch (Exception ignored) {}
    }

    private static void reset() {
        lastKey = null;
        count = 0;
        lastAdded = null;
    }
}
