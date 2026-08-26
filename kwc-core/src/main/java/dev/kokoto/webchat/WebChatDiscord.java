package dev.kokoto.webchat;

import java.util.List;

/** Loader-neutral Discord integration boundary used by the web server. */
public interface WebChatDiscord {
    void sendWebMessage(ChatMessage msg);
    void sendGameMessage(ChatMessage msg);
    boolean shouldSuppressGameEcho(String player, String message);
    default boolean sendAdminAlert(String channel, String text) { return false; }
    /** DiscordSRV logical channel names suitable for the Admin alert selector. */
    default List<String> adminAlertChannelChoices() { return List.of(); }
}
