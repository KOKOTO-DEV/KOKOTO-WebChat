package dev.kokoto.webchat;

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
