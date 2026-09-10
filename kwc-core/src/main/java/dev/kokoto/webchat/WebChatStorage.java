package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * WebChatStorage는 KWC 상태를 메모리/JSONL/SQLite 같은 영속 매체에 저장하고 조회하는 계층이다.
 * WebChatStorage is a persistence layer storing and reading KWC state from memory, JSONL, SQLite, or another backing store.
 *
 * 조회 visibility와 mutation 권한을 분리하고, transaction/atomic rewrite가 필요한 작업은 중간 실패로 데이터가 반쯤 적용되지 않게 해야 한다.
 * Keep read visibility separate from mutation authorization, and use transactions/atomic rewrites where partial failure could leave inconsistent data.
 */
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
    PinnedMessage pinMessage(ChatMessage msg, String pinnedByUuid, String pinnedByUsername, String pinnedByDisplayName, int maxPins);
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
