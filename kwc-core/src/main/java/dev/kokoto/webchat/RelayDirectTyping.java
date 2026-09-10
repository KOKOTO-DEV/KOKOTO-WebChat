package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 서버간 DM typing 상태 전달 payload를 표현한다.
 * Represents cross-server DM typing-state payload.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
/** Ephemeral cross-server DM typing indication. Never persisted or replayed into chat history. */
public final class RelayDirectTyping {
    public final String eventId;
    public final String originServerId;
    public final String originServerName;
    public final String senderUuid;
    public final String senderUsername;
    public final String senderDisplayName;
    public final String targetUuid;
    public final long expiresAt;

    public RelayDirectTyping(String eventId, String originServerId, String originServerName,
                             String senderUuid, String senderUsername, String senderDisplayName,
                             String targetUuid, long expiresAt) {
        this.eventId = eventId == null ? "" : eventId;
        this.originServerId = originServerId == null ? "" : originServerId;
        this.originServerName = originServerName == null ? "" : originServerName;
        this.senderUuid = senderUuid == null ? "" : senderUuid;
        this.senderUsername = senderUsername == null ? "" : senderUsername;
        this.senderDisplayName = senderDisplayName == null ? "" : senderDisplayName;
        this.targetUuid = targetUuid == null ? "" : targetUuid;
        this.expiresAt = expiresAt;
    }
}
