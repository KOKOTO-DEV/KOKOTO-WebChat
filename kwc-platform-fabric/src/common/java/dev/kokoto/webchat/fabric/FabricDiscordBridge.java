package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.ChatMessage;
import dev.kokoto.webchat.WebChatDiscord;

/** Fabric has no DiscordSRV-equivalent hard dependency. */
public final class FabricDiscordBridge implements WebChatDiscord {
    @Override public void sendWebMessage(ChatMessage msg) {}
    @Override public void sendGameMessage(ChatMessage msg) {}
    @Override public boolean shouldSuppressGameEcho(String player, String message) { return false; }
}
