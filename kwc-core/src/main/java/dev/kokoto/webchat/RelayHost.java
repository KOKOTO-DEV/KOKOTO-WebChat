package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * RelayHost는 서버간 Relay Protocol 2.x의 설정·호스트 계약·전송 데이터를 담당한다.
 * RelayHost participates in configuration, host contracts, or transport data for Relay Protocol 2.x.
 *
 * Relay payload는 서버 경계를 넘으므로 origin/target/sender 식별과 capability negotiation을 신뢰 경계 안에서 다시 검증해야 한다.
 * Relay payloads cross a server trust boundary, so origin/target/sender identity and capability negotiation must be revalidated inside the trust boundary.
 */
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
    /** Applies a sender-authoritative cross-server DM delete. */
    boolean applyDirectMessageDelete(String originServerId, String senderUuid, String messageRelayId);
    void publishDirectMessageUpdate(String localUserUuid, String remoteUserUuid, String threadId);

    /** Handles a trusted, targeted event query/join request from another Relay server. */
    default String handleChatGameRelayRequest(String originServerId, String payloadJson) {
        return "{\"ok\":false,\"error\":\"game_relay_unsupported\"}";
    }

    /** Handles a trusted, targeted public profile/presence lookup from another Relay server. */
    default String handleProfileRelayRequest(String originServerId, String payloadJson) {
        return "{\"ok\":false,\"error\":\"profile_relay_unsupported\"}";
    }
}
