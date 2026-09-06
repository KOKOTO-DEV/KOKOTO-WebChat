package dev.kokoto.webchat;

/**
 * Platform boundary for the core relay engine. The relay protocol, HTTP transport,
 * authentication, routing and deduplication stay in core; the platform supplies
 * configuration, persistence checks and delivery callbacks.
 */
public interface RelayHost {
    RelaySettings relaySettings();
    String defaultServerName();
    /** Product/plugin version for diagnostics only. Never used for Relay compatibility. */
    default String productVersion() { return ""; }
    WebChatLanguage language();
    void info(String message);
    void warn(String message);

    boolean hasPublicMessage(String relayId);
    boolean acceptPublicMessage(ChatMessage message);
    boolean acceptPublicReaction(RelayPublicReaction reaction);
    boolean acceptPublicTyping(RelayPublicTyping typing);
    boolean acceptDirectTyping(RelayDirectTyping typing);

    boolean hasDirectRelayId(String relayId);
    boolean acceptDirectMessage(RelayDirectMessage message);

    RelayReadApplyResult applyDirectMessageRead(String messageRelayId);
    void publishDirectMessageUpdate(String localUserUuid, String remoteUserUuid, String threadId);
}
