package dev.kokoto.webchat;

import java.util.List;

/** Loader-neutral account/session/pin storage boundary used by the web server. */
public interface WebChatStorage {
    SessionContext getSession(String token);
    void updateLastDisplayName(String uuid, String username, String displayName);
    PlayerIdentity findKnownPlayerByUuid(String uuid);
    PlayerIdentity findKnownLocalPlayer(String query);
    List<PlayerIdentity> listKnownPlayers(String query, int limit);
    List<PinnedMessage> listPinnedMessages();
    List<String> pinnedMessageTexts();
    PinnedMessage pinMessage(ChatMessage msg, String pinnedBy, int maxPins);
    boolean unpinMessage(String pinId);
    boolean movePinnedMessage(String pinId, String direction);
    Account findAccountByUsername(String username);
    Account createLocalAccount(String username, Role role);
    void setPassword(Account account, String password);
    void setRole(Account account, Role role);
    List<Account> listAccounts();
    List<SessionContext> listSessions();
    boolean revokeSession(String token);
    int revokeSessionsForUsername(String username);
    int cleanupExpiredSessions();

    /**
     * Recalculate currently valid session expiry timestamps from each session's
     * original createdAt. adminSessions=false targets USER/MODERATOR sessions;
     * adminSessions=true targets ADMIN sessions. durationMillis<=0 means unlimited.
     * Implementations must not resurrect sessions that were already expired.
     */
    SessionPolicyUpdate recalculateSessionExpiry(boolean adminSessions, long durationMillis);
}
