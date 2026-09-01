package dev.kokoto.webchat.forge;

import dev.kokoto.webchat.ChatMessage;
import dev.kokoto.webchat.WebChatDiscord;

/** Forge builds have no DiscordSRV-equivalent hard dependency. */
public final class ForgeDiscordBridge implements WebChatDiscord {
    @Override public void sendWebMessage(ChatMessage msg) {}
    @Override public void sendGameMessage(ChatMessage msg) {}
    @Override public boolean shouldSuppressGameEcho(String player, String message) { return false; }
}
