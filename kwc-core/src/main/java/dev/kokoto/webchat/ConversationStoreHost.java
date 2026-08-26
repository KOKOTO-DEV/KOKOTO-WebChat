package dev.kokoto.webchat;

import java.nio.file.Path;

/** Platform callbacks required by platform-neutral DM/group persistence. */
public interface ConversationStoreHost extends CoreLogger {
    Path dataDirectory();
    PlayerIdentity resolveIdentity(String uuid);
    boolean isOnline(String uuid);
    DirectMessageSettings directMessageSettings();
    GroupChatSettings groupChatSettings();
}
