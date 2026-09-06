package dev.kokoto.webchat;

/** Ephemeral cross-server public-chat typing indication. Never persisted or replayed into chat history. */
public final class RelayPublicTyping {
    public final String eventId;
    public final String originServerId;
    public final String originServerName;
    public final String source;
    public final String senderUuid;
    public final String senderUsername;
    public final String senderDisplayName;
    public final String clientId;
    public final long expiresAt;

    public RelayPublicTyping(String eventId, String originServerId, String originServerName, String source,
                             String senderUuid, String senderUsername, String senderDisplayName,
                             String clientId, long expiresAt) {
        this.eventId = eventId == null ? "" : eventId;
        this.originServerId = originServerId == null ? "" : originServerId;
        this.originServerName = originServerName == null ? "" : originServerName;
        this.source = source == null ? "" : source;
        this.senderUuid = senderUuid == null ? "" : senderUuid;
        this.senderUsername = senderUsername == null ? "" : senderUsername;
        this.senderDisplayName = senderDisplayName == null ? "" : senderDisplayName;
        this.clientId = clientId == null ? "" : clientId;
        this.expiresAt = expiresAt;
    }
}
