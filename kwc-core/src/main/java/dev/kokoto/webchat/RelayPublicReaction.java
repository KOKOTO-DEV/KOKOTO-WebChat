package dev.kokoto.webchat;

/**
 * Public-reaction relay payload exposed across the core/platform boundary.
 *
 * <p>Normal relay fan-out uses {@code request=false}. Authority-routed reaction
 * requests use {@code request=true}; in that case {@code targetServerId}
 * identifies the message-origin server that must validate and commit the state.
 * Keeping both forms in one loader-neutral DTO avoids widening the RelayHost
 * interface while still letting the origin server distinguish a request from a
 * committed replication event.</p>
 */
public final class RelayPublicReaction {
    public final String eventId;
    /** Reaction scope. "public" is the legacy/default wire value; "dm" is targeted private-message replication. */
    public final String scope;
    public final String messageRelayId;
    public final String originServerId;
    public final String targetServerId;
    public final String actorUuid;
    public final String actorLabel;
    public final String reaction;
    public final boolean active;
    public final boolean request;

    public RelayPublicReaction(String eventId, String messageRelayId, String originServerId,
                               String actorUuid, String reaction, boolean active) {
        this(eventId, messageRelayId, originServerId, "", "public", actorUuid, "", reaction, active, false);
    }

    public RelayPublicReaction(String eventId, String messageRelayId, String originServerId,
                               String actorUuid, String actorLabel, String reaction, boolean active) {
        this(eventId, messageRelayId, originServerId, "", "public", actorUuid, actorLabel, reaction, active, false);
    }

    public RelayPublicReaction(String eventId, String messageRelayId, String originServerId, String targetServerId,
                               String actorUuid, String actorLabel, String reaction, boolean active, boolean request) {
        this(eventId, messageRelayId, originServerId, targetServerId, "public", actorUuid, actorLabel, reaction, active, request);
    }

    public RelayPublicReaction(String eventId, String messageRelayId, String originServerId, String targetServerId,
                               String scope, String actorUuid, String actorLabel, String reaction, boolean active, boolean request) {
        this.eventId = eventId == null ? "" : eventId;
        this.scope = scope == null || scope.isBlank() ? "public" : scope.trim().toLowerCase(java.util.Locale.ROOT);
        this.messageRelayId = messageRelayId == null ? "" : messageRelayId;
        this.originServerId = originServerId == null ? "" : originServerId;
        this.targetServerId = targetServerId == null ? "" : targetServerId;
        this.actorUuid = actorUuid == null ? "" : actorUuid;
        this.actorLabel = actorLabel == null ? "" : actorLabel;
        this.reaction = reaction == null ? "" : reaction;
        this.active = active;
        this.request = request;
    }
}
