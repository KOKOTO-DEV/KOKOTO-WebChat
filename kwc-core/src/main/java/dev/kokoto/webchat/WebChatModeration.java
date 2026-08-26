package dev.kokoto.webchat;

import java.util.List;

/** Loader-neutral guest moderation boundary used by the web server. */
public interface WebChatModeration {
    ModerationEntry mute(String type, String value, long minutes, String reason, String createdBy);
    boolean unmute(String type, String value);
    boolean isMuted(String guestName, String ip);
    ModerationEntry findMatch(String guestName, String ip);
    List<ModerationEntry> list();
}
