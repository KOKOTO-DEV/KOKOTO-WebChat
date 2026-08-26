package dev.kokoto.webchat;

/** Platform-neutral incoming relayed direct-message payload. */
public final class RelayDirectMessage {
    public final String relayId;
    public final String originServerId;
    public final String originServerName;
    public final String senderUuid;
    public final String senderUsername;
    public final String senderDisplayName;
    public final String targetUuid;
    public final String targetUsername;
    public final String targetDisplayName;
    public final String message;
    public final String gameMessage;

    public RelayDirectMessage(String relayId, String originServerId, String originServerName,
                              String senderUuid, String senderUsername, String senderDisplayName,
                              String targetUuid, String targetUsername, String targetDisplayName,
                              String message, String gameMessage) {
        this.relayId = nz(relayId);
        this.originServerId = nz(originServerId);
        this.originServerName = nz(originServerName);
        this.senderUuid = nz(senderUuid);
        this.senderUsername = nz(senderUsername);
        this.senderDisplayName = nz(senderDisplayName);
        this.targetUuid = nz(targetUuid);
        this.targetUsername = nz(targetUsername);
        this.targetDisplayName = nz(targetDisplayName);
        this.message = nz(message);
        this.gameMessage = nz(gameMessage);
    }

    private static String nz(String value) { return value == null ? "" : value; }
}
