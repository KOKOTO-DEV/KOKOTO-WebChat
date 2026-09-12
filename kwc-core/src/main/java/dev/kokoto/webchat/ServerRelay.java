package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * ServerRelay는 서버간 Relay Protocol 2.x의 설정·호스트 계약·전송 데이터를 담당한다.
 * ServerRelay participates in configuration, host contracts, or transport data for Relay Protocol 2.x.
 *
 * Relay payload는 서버 경계를 넘으므로 origin/target/sender 식별과 capability negotiation을 신뢰 경계 안에서 다시 검증해야 한다.
 * Relay payloads cross a server trust boundary, so origin/target/sender identity and capability negotiation must be revalidated inside the trust boundary.
 */
import com.sun.net.httpserver.HttpExchange;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * KOKOTO WebChat relay protocol 2.x (current revision 2.2).
 *
 * Trust is group-scoped: every group owns one shared secret and a peer list. Peer
 * entries never carry a second secret, which prevents group/peer secret drift.
 * Direct relay follows the 5.0.0 operating model: each message request authenticates
 * and encrypts itself independently. The handshake endpoint is stateless diagnostics only. Message payloads
 * use hop-by-hop AES-256-GCM authenticated encryption. Relay participants are trusted
 * endpoints, not end-to-end opaque forwarders.
 */
/**
 * KWC 유지보수 안내: 여러 KWC 서버 사이의 Relay Protocol 2.x(현재 revision 2.2)를 구현한다. peer 요청은 shared secret 기반 인증과 AES-256-GCM hop-by-hop 암호화를 사용하며, public/DM/read/reaction/typing/delete/game/profile capability를 명시적으로 분리한다. DM delete는 지정한 target server에만 전달하고 sender identity + relay id를 검증해야 한다.
 *
 * KWC maintenance note: Implements KWC Relay Protocol 2.x (current revision 2.2) between servers. Peer requests use shared-secret authentication and hop-by-hop AES-256-GCM encryption, with explicit public/DM/read/reaction/typing/delete/game/profile capabilities. DM delete is targeted to one server and must validate sender identity plus relay ID.
 */
public final class ServerRelay implements AutoCloseable {
    /** Major wire compatibility. Keep this at 2 for all backward-compatible 2.x revisions. */
    private static final String PROTOCOL_MAJOR = "2";
    /** Human/diagnostic protocol revision. KWC 5.3.1 stays on 2.2; optional delete/game/profile features are capability-negotiated within that revision. */
    private static final String PROTOCOL_REVISION = "2.2";
    /** Existing 2.0 probe canonical embedded the then-current product version. Accept it only as a compatibility fallback. */
    private static final String LEGACY_V20_HANDSHAKE_PRODUCT_VERSION = "5.2.0";
    private static final String CAPABILITIES_CSV = "public,dm,read,delete,reaction,reaction-authority,typing,game,profile";
    private static final String HEADER_VERSION = "X-KWC-Relay-Version";
    private static final String HEADER_PROTOCOL = "X-KWC-Relay-Protocol";
    private static final String HEADER_CAPABILITIES = "X-KWC-Relay-Capabilities";
    private static final String HEADER_GROUP = "X-KWC-Relay-Group";
    private static final String HEADER_FROM = "X-KWC-Relay-From";
    private static final String HEADER_TO = "X-KWC-Relay-To";
    private static final String HEADER_TIMESTAMP = "X-KWC-Relay-Timestamp";
    private static final String HEADER_NONCE = "X-KWC-Relay-Nonce";
    private static final String HEADER_IV = "X-KWC-Relay-IV";
    private static final String HEADER_TRANSPORT = "X-KWC-Relay-Transport";
    private static final String HEADER_SIGNATURE = "X-KWC-Relay-Signature";
    private static final String HEADER_RESPONSE_TIMESTAMP = "X-KWC-Relay-Response-Timestamp";
    private static final String HEADER_RESPONSE_SIGNATURE = "X-KWC-Relay-Response-Signature";
    private static final int MAX_BODY_BYTES = 96 * 1024;
    private static final int MIN_GROUP_SECRET_LENGTH = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final long OUTBOUND_BACKOFF_MILLIS = 60_000L;
    private static final SecureRandom RNG = new SecureRandom();

    private final RelayHost host;
    private final RelaySettings config;
    private final String serverId;
    private final String serverName;
    private final Map<String, GroupRef> groupsById = new LinkedHashMap<>();
    private final Map<String, PeerRef> peersById = new LinkedHashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private final ConcurrentHashMap<String, Long> seenRelayIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> deliveredRelayIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> seenRequestNonces = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> transportFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> outboundBackoffUntil = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final OperationalIssueTracker issues;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final boolean active;

    public ServerRelay(RelayHost host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
        this.config = host.relaySettings();
        String defaultServerName = safe(host.defaultServerName());
        String configuredId = config == null ? "" : safe(config.serverId);
        this.serverId = normalizeId(configuredId.isBlank() ? fallbackServerId(defaultServerName) : configuredId);
        String configuredName = config == null ? "" : safe(config.serverName);
        this.serverName = configuredName.isBlank() ? defaultServerName : configuredName;

        Map<String,Integer> peerCounts = new HashMap<>();
        if (config != null && config.groups != null) {
            int groupIndex = 0;
            for (RelaySettings.Group group : config.groups) {
                groupIndex++;
                if (group == null) { diagnostics.add("group #" + groupIndex + " is empty and was ignored"); continue; }
                String gid = normalizeId(group.id);
                if (gid.isBlank()) { diagnostics.add("group #" + groupIndex + " has no usable id and was ignored"); continue; }
                if (groupsById.containsKey(gid)) { diagnostics.add("duplicate group id " + gid + " was ignored"); continue; }
                String secret = safe(group.sharedSecret).trim();
                if (secret.length() < MIN_GROUP_SECRET_LENGTH) {
                    diagnostics.add("group " + gid + " shared-secret must be at least " + MIN_GROUP_SECRET_LENGTH + " characters and was ignored");
                    continue;
                }
                GroupRef ref = new GroupRef(gid, secret, group.forwardingEnabled);
                groupsById.put(gid, ref);
                int peerIndex = 0;
                for (RelaySettings.Peer peer : group.peers) {
                    peerIndex++;
                    if (peer == null || !peer.enabled) continue;
                    String pid = normalizeId(peer.id);
                    if (pid.isBlank()) { diagnostics.add("group " + gid + " peer #" + peerIndex + " has no usable id and was ignored"); continue; }
                    if (pid.equals(serverId)) { diagnostics.add("group " + gid + " peer " + pid + " matches this server-id and was ignored"); continue; }
                    if (safe(peer.url).isBlank()) { diagnostics.add("group " + gid + " peer " + pid + " has no URL and was ignored"); continue; }
                    try { relayBaseUri(peer.url); }
                    catch (IllegalArgumentException ex) { diagnostics.add("group " + gid + " peer " + pid + " has an invalid URL and was ignored: " + ex.getMessage()); continue; }
                    PeerRef pref = new PeerRef(ref, new RelaySettings.Peer(pid, peer.url, true, peer.send, peer.receive));
                    ref.peers.put(pid, pref);
                    peerCounts.merge(pid, 1, Integer::sum);
                }
            }
        }
        for (GroupRef group : groupsById.values()) {
            for (PeerRef peer : new ArrayList<>(group.peers.values())) {
                if (peerCounts.getOrDefault(peer.id(), 0) > 1) {
                    diagnostics.add("peer id " + peer.id() + " is registered in multiple relay groups; all registrations for that peer were disabled");
                    group.peers.remove(peer.id());
                } else {
                    peersById.put(peer.id(), peer);
                }
            }
        }
        groupsById.values().removeIf(g -> g.peers.isEmpty());

        this.issues = new OperationalIssueTracker(host::info, host::warn);
        // Let java.net.http own its internal executor. A fixed two-thread executor is
        // too small for concurrent multi-peer HTTPS/TLS requests and can starve HttpClient's
        // internal asynchronous work under real reverse-proxy/network conditions.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config == null ? 5 : config.connectTimeoutSeconds))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.active = config != null && config.enabled && !serverId.isBlank() && !peersById.isEmpty();
    }

    public void start() {
        start(true);
    }

    public void start(boolean emitSecurityWarnings) {
        if (config == null || !config.enabled) return;
        for (String diagnostic : diagnostics) host.warn("Server relay v" + PROTOCOL_REVISION + " config: " + diagnostic + ".");
        // Security warnings are based on the operator's configured enabled peers, not
        // on the post-validation active peer map. This guarantees that an http:// peer
        // is reported even when another validation problem makes Relay inactive.
        if (emitSecurityWarnings) logHttpPeerWarnings();
        if (!active) {
            host.warn("Server relay v" + PROTOCOL_REVISION + " is enabled, but no usable relay groups/peers remain after validation.");
            return;
        }
        host.info("Server relay protocol v" + PROTOCOL_REVISION + " enabled (2.x compatible). serverId=" + serverId + ", groups=" + groupsById.size() + ", peers=" + peersById.size());
    }

    /**
     * Re-evaluates the configured Relay peer URLs and writes direct-HTTP security warnings
     * to the server console. This intentionally scans the loaded configuration rather than
     * only peers that survived runtime validation: an operator must still see the HTTP
     * warning even when that peer/group has another validation problem.
     */
    public void logHttpPeerWarnings() {
        if (config == null || !config.enabled || config.groups == null) return;
        Set<String> warned = new java.util.LinkedHashSet<>();
        for (RelaySettings.Group group : config.groups) {
            if (group == null || group.peers == null) continue;
            for (RelaySettings.Peer peer : group.peers) {
                if (peer == null || !peer.enabled) continue;
                String url = safe(peer.url).trim();
                if (!isPlainHttpUrl(url)) continue;
                String id = normalizeId(peer.id);
                String key = id + "\n" + url;
                if (!warned.add(key)) continue;
                warnText("security.relayHttpPeerWarning",
                        "[WARNING] Relay peer '{peer}' uses HTTP ({url}). Direct relay remains authenticated/encrypted at the payload layer, but HTTPS is strongly recommended and this peer is excluded from forwarding.",
                        "peer", id.isBlank() ? safe(peer.id) : id, "url", url);
            }
        }
    }

    public boolean isEnabled() { return active && !closed.get(); }
    public String serverId() { return serverId; }
    public String serverName() { return serverName; }

    /** Targeted Relay 2.2 `game` capability event request. It is point-to-point and never broadcast to unrelated peers. */
    public CompletableFuture<ChatGameRelayResponse> requestChatGame(String targetServerId, String payloadJson) {
        String target = normalizeId(targetServerId);
        if (!canRoute(target, TrafficClass.EVENT)) return CompletableFuture.completedFuture(ChatGameRelayResponse.failed("remote_server_unavailable", 503));
        ChatGameRequestEnvelope envelope = ChatGameRequestEnvelope.create(serverId, target, payloadJson);
        if (!envelope.valid()) return CompletableFuture.completedFuture(ChatGameRelayResponse.failed("invalid_envelope", 400));
        return sendChatGameRequestToPeers(envelope, "", null);
    }

    /** Targeted Relay 2.2 `profile` capability public profile/presence request. It follows the same deterministic point-to-point routing rule as DM/event requests. */
    public CompletableFuture<ProfileRelayResponse> requestUserProfile(String targetServerId, String payloadJson) {
        String target = normalizeId(targetServerId);
        if (!canRoute(target, TrafficClass.PROFILE)) return CompletableFuture.completedFuture(ProfileRelayResponse.failed("remote_server_unavailable", 503));
        ProfileRequestEnvelope envelope = ProfileRequestEnvelope.create(serverId, target, payloadJson);
        if (!envelope.valid()) return CompletableFuture.completedFuture(ProfileRelayResponse.failed("invalid_envelope", 400));
        return sendProfileRequestToPeers(envelope, "", null);
    }

    public boolean canRouteDirectMessage(String targetServerId) { return canRoute(targetServerId, TrafficClass.DM); }
    public boolean canRouteChatGame(String targetServerId) { return canRoute(targetServerId, TrafficClass.EVENT); }
    public boolean canRouteProfile(String targetServerId) { return canRoute(targetServerId, TrafficClass.PROFILE); }

    private boolean canRoute(String targetServerId, TrafficClass trafficClass) {
        String target = normalizeId(targetServerId);
        if (!isEnabled() || target.isBlank() || target.equals(serverId)) return false;
        PeerRef direct = peersById.get(target);
        if (direct != null) return isPeerUsable(direct, false, trafficClass);
        return uniqueForwardingNextHop(null, "", target, trafficClass) != null;
    }

    public String createDirectMessageRelayId() { return "dmrelay-" + SecurityUtil.randomToken(16); }

    public CompletableFuture<DirectMessageDelivery> publishDirectMessage(
            String relayId,
            String senderUuid, String senderUsername, String senderDisplayName,
            String targetServerId, String targetUuid, String targetUsername,
            String targetDisplayName, String message, String gameMessage) {
        return publishDirectMessage(relayId, senderUuid, senderUsername, senderDisplayName,
                targetServerId, targetUuid, targetUsername, targetDisplayName, message, gameMessage, "", "", "");
    }

    public CompletableFuture<DirectMessageDelivery> publishDirectMessage(
            String relayId,
            String senderUuid, String senderUsername, String senderDisplayName,
            String targetServerId, String targetUuid, String targetUsername,
            String targetDisplayName, String message, String gameMessage,
            String replyToRelayId, String replyToSender, String replyToPreview) {
        String target = normalizeId(targetServerId);
        if (!canRouteDirectMessage(target)) return CompletableFuture.completedFuture(DirectMessageDelivery.failed("remote_server_unavailable", 503));
        DirectMessageEnvelope envelope = DirectMessageEnvelope.create(relayId, serverId, serverName, target,
                senderUuid, senderUsername, senderDisplayName, targetUuid, targetUsername, targetDisplayName, message, gameMessage,
                replyToRelayId, replyToSender, replyToPreview);
        if (!envelope.valid()) return CompletableFuture.completedFuture(DirectMessageDelivery.failed("invalid_envelope", 400));
        markSeen(envelope.relayId);
        CompletableFuture<DirectMessageDelivery> future = sendDirectToPeers(envelope, "", null);
        future.whenComplete((delivery, error) -> {
            if (error != null || delivery == null || !delivery.delivered) seenRelayIds.remove(envelope.relayId);
            else markDelivered(envelope.relayId);
        });
        return future;
    }

    public CompletableFuture<Boolean> publishDirectMessageRead(String targetServerId, String messageRelayId) {
        String target = normalizeId(targetServerId);
        if (!canRouteDirectMessage(target)) return CompletableFuture.completedFuture(false);
        DirectMessageReadEnvelope envelope = DirectMessageReadEnvelope.create(serverId, target, messageRelayId);
        if (!envelope.valid()) return CompletableFuture.completedFuture(false);
        return sendDirectReadWithRetry(envelope, 0);
    }

    // DM sender-authoritative delete를 지정한 target server 하나에만 전송한다. broadcast를 사용하지 않아 관계없는 peer가 private delete metadata를 보지 않게 한다.
    // Sends sender-authoritative DM delete to exactly one target server. It deliberately avoids broadcast so unrelated peers never receive private delete metadata.
    public CompletableFuture<Boolean> publishDirectMessageDelete(String targetServerId, String senderUuid, String messageRelayId) {
        String target = normalizeId(targetServerId);
        if (!canRouteDirectMessage(target)) return CompletableFuture.completedFuture(false);
        DirectMessageDeleteEnvelope envelope = DirectMessageDeleteEnvelope.create(serverId, target, senderUuid, messageRelayId);
        if (!envelope.valid()) return CompletableFuture.completedFuture(false);
        return sendDirectDeleteWithRetry(envelope, 0);
    }

    public CompletableFuture<Boolean> publishDirectTyping(String targetServerId, String senderUuid, String senderUsername,
                                                           String senderDisplayName, String targetUuid, long expiresAt) {
        String target = normalizeId(targetServerId);
        if (!canRouteDirectMessage(target)) return CompletableFuture.completedFuture(false);
        DirectTypingEnvelope envelope = DirectTypingEnvelope.create(serverId, serverName, target, senderUuid, senderUsername,
                senderDisplayName, targetUuid, expiresAt);
        if (!envelope.valid()) return CompletableFuture.completedFuture(false);
        markSeen("typing:" + envelope.eventId);
        return sendDirectTypingToPeers(envelope, "", null);
    }

    private CompletableFuture<Boolean> sendDirectReadWithRetry(DirectMessageReadEnvelope envelope, int attempt) {
        return sendDirectReadToPeers(envelope, "", null).thenCompose(ok -> {
            if (ok || attempt >= 2 || closed.get()) return CompletableFuture.completedFuture(ok);
            long delayMs = attempt == 0 ? 500L : 1500L;
            return CompletableFuture.supplyAsync(() -> Boolean.TRUE, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS))
                    .thenCompose(ignored -> sendDirectReadWithRetry(envelope, attempt + 1));
        });
    }

    private CompletableFuture<Boolean> sendDirectDeleteWithRetry(DirectMessageDeleteEnvelope envelope, int attempt) {
        return sendDirectDeleteToPeers(envelope, "", null).thenCompose(ok -> {
            if (ok || attempt >= 2 || closed.get()) return CompletableFuture.completedFuture(ok);
            long delayMs = attempt == 0 ? 500L : 1500L;
            return CompletableFuture.supplyAsync(() -> Boolean.TRUE, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS))
                    .thenCompose(ignored -> sendDirectDeleteWithRetry(envelope, attempt + 1));
        });
    }

    public boolean shouldRelay(ChatMessage msg) {
        if (!isEnabled() || msg == null) return false;
        String source = safe(msg.source).toLowerCase(Locale.ROOT);
        if (source.equals("game")) return config.gameChat;
        if (source.equals("web")) return config.webChat;
        if (source.equals("guest")) return config.guestChat;
        if (source.equals("discord")) return config.discordChat;
        if (source.equals("event")) return config.eventAnnouncements;
        return config.systemEvents && (source.equals("system") || source.equals("server"));
    }

    public void prepareLocal(ChatMessage msg) {
        if (!isEnabled() || msg == null) return;
        if (safe(msg.originServerId).isBlank()) msg.originServerId = serverId;
        if (safe(msg.originServerName).isBlank()) msg.originServerName = serverName;
        if (!shouldRelay(msg)) return;
        if (safe(msg.relayId).isBlank()) msg.relayId = "relay-" + SecurityUtil.randomToken(16);
        msg.id = msg.relayId;
        msg.relayHop = 0;
        markSeen(msg.relayId);
    }

    public void publishLocal(ChatMessage msg) {
        if (!shouldRelay(msg)) return;
        prepareLocal(msg);
        RelayEnvelope envelope = RelayEnvelope.fromMessage(msg, serverId, serverName, 0);
        for (GroupRef group : groupsById.values()) sendPublicToGroup(envelope, group, "", false);
    }

    public void publishPublicTyping(String source, String senderUuid, String senderUsername, String senderDisplayName,
                                    String clientId, long expiresAt) {
        if (!isEnabled()) return;
        String normalizedSource = safe(source).trim().toLowerCase(Locale.ROOT);
        if ((normalizedSource.equals("web") && !config.webChat) || (normalizedSource.equals("guest") && !config.guestChat)) return;
        if (!normalizedSource.equals("web") && !normalizedSource.equals("guest")) return;
        PublicTypingEnvelope envelope = PublicTypingEnvelope.create(serverId, serverName, normalizedSource, senderUuid,
                senderUsername, senderDisplayName, clientId, expiresAt);
        if (!envelope.valid()) return;
        markSeen("typing-public:" + envelope.eventId);
        for (GroupRef group : groupsById.values()) sendPublicTypingToGroup(envelope, group, "", false);
    }

    public String createPublicReactionEventId() {
        return "react-" + SecurityUtil.randomToken(16);
    }

    /**
     * Compatibility fan-out entry point. New code should commit only on the
     * message-origin server and use {@link #publishCommittedPublicReaction}.
     */
    public void publishPublicReaction(String messageRelayId, String actorUuid, String reaction, boolean active) {
        publishPublicReaction(messageRelayId, actorUuid, "", reaction, active);
    }

    public void publishPublicReaction(String messageRelayId, String actorUuid, String actorLabel, String reaction, boolean active) {
        publishCommittedPublicReaction(createPublicReactionEventId(), messageRelayId, actorUuid, actorLabel, reaction, active);
    }

    /** Fan out a reaction state that has already been validated and stored by this message-origin server. */
    public void publishCommittedPublicReaction(String eventId, String messageRelayId, String actorUuid,
                                               String actorLabel, String reaction, boolean active) {
        if (!isEnabled()) return;
        ReactionEnvelope envelope = ReactionEnvelope.create(serverId, eventId, messageRelayId, actorUuid, actorLabel, reaction, active);
        if (!envelope.valid()) return;
        markSeen("reaction:" + envelope.eventId);
        for (GroupRef group : groupsById.values()) sendReactionToGroup(envelope, group, "", false);
    }

    /**
     * Route a reaction mutation to the authoritative message-origin server.
     * Direct peers are used immediately; multi-hop forwarding uses the same
     * deterministic single-next-hop rule as remote DM delivery.
     */
    public CompletableFuture<ReactionRequestResult> requestPublicReaction(String eventId, String targetServerId,
                                                                           String messageRelayId, String actorUuid,
                                                                           String actorLabel, String reaction, boolean active) {
        return requestReaction("public", eventId, targetServerId, messageRelayId, actorUuid, actorLabel, reaction, active);
    }

    /** Route a scoped reaction mutation to one specific relay server. DM reactions use this
     * targeted path and are never broadcast to unrelated peers. */
    public CompletableFuture<ReactionRequestResult> requestReaction(String scope, String eventId, String targetServerId,
                                                                     String messageRelayId, String actorUuid,
                                                                     String actorLabel, String reaction, boolean active) {
        if (!isEnabled()) return CompletableFuture.completedFuture(ReactionRequestResult.retryable("relay_disabled", 503));
        ReactionRequestEnvelope envelope = ReactionRequestEnvelope.create(scope, eventId, serverId, targetServerId,
                messageRelayId, actorUuid, actorLabel, reaction, active);
        if (!envelope.valid()) return CompletableFuture.completedFuture(ReactionRequestResult.rejected("invalid_reaction_request", 400));
        return sendReactionRequestToPeers(envelope, "", null);
    }

    /** Legacy v1 endpoints are intentionally retired in 5.1.0. */
    public void handleLegacyV1(HttpExchange exchange) throws IOException {
        sendJson(exchange, 426, protocolErrorJson("relay_protocol_upgrade_required"));
    }

    // peer 설정/secret/protocol 호환을 확인하는 진단 endpoint다. 실제 message 인증 상태를 세션처럼 저장하지 않으며 각 relay message는 독립적으로 다시 인증된다.
    // Diagnostic endpoint checking peer configuration, secret, and protocol compatibility. It does not create authenticated session state; every relay message is independently authenticated again.
    public void handleHandshake(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        if (!isEnabled()) { sendJson(exchange, 404, "{\"ok\":false,\"error\":\"relay_disabled\"}"); return; }
        RequestMeta meta = readMeta(exchange, false);
        if (meta.error != null) { sendJson(exchange, meta.status, meta.error); return; }
        PeerRef peer = peerFor(meta.groupId, meta.fromId);
        if (peer == null) { sendJson(exchange, 403, "{\"ok\":false,\"error\":\"unknown_peer\"}"); return; }
        if (!serverId.equals(meta.toId)) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"peer_mismatch\"}", peer, meta.nonce); return; }
        if (!checkTimestamp(meta.timestamp)) { signedResponse(exchange, 401, "{\"ok\":false,\"error\":\"expired_request\"}", peer, meta.nonce); return; }
        if (!markRequestNonce(meta.groupId, meta.fromId, meta.nonce)) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"replayed_nonce\"}", peer, meta.nonce); return; }
        String transport = normalizeTransport(header(exchange, HEADER_TRANSPORT));
        if (transport.isBlank()) { signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_transport\"}", peer, meta.nonce); return; }
        String signature = header(exchange, HEADER_SIGNATURE).toLowerCase(Locale.ROOT);
        String peerRevision = normalizeProtocolRevision(header(exchange, HEADER_PROTOCOL));
        String canonical = handshakeCanonical(meta, transport, peerRevision);
        boolean signatureOk = !signature.isBlank() && constantTimeEquals(signature, hmacHex(peer.group.secret, canonical));
        // Relay 2.0 probes signed a product-version-bearing canonical. Keep that exact probe compatible
        // while product/plugin versions are no longer part of protocol compatibility from 2.1 onward.
        if (!signatureOk && peerRevision.isBlank()) {
            signatureOk = constantTimeEquals(signature, hmacHex(peer.group.secret, legacyV20HandshakeCanonical(meta, transport)));
        }
        if (!signatureOk) {
            signedResponse(exchange, 401, "{\"ok\":false,\"error\":\"bad_signature\"}", peer, meta.nonce); return;
        }
        // Stateless probe only. Relay traffic itself uses 5.0.0-style request-by-request
        // authentication/encryption; this diagnostic endpoint never creates routing state.
        String body = "{\"ok\":true,\"protocolMajor\":2,\"protocol\":" + JsonUtil.quote(PROTOCOL_REVISION)
                + ",\"capabilities\":[\"public\",\"dm\",\"read\",\"delete\",\"reaction\",\"reaction-authority\",\"typing\",\"game\",\"profile\"]"
                + ",\"serverVersion\":" + JsonUtil.quote(safe(host.productVersion())) + ",\"serverId\":" + JsonUtil.quote(serverId)
                + ",\"groupId\":" + JsonUtil.quote(peer.group.id) + ",\"nonce\":" + JsonUtil.quote(meta.nonce) + "}";
        signedResponse(exchange, 200, body, peer, meta.nonce);
    }

    // 암호화된 relay envelope의 header/signature/timestamp/replay 조건을 검증하고 복호화한 뒤 kind별 handler로 분배한다. 검증 전에 payload 내용을 신뢰하지 않는다.
    // Validates headers, signature, timestamp, and replay conditions on an encrypted relay envelope, decrypts it, then dispatches by kind. Payload contents are never trusted before envelope validation.
    public void handleMessage(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        if (!isEnabled()) { sendJson(exchange, 404, "{\"ok\":false,\"error\":\"relay_disabled\"}"); return; }
        RequestMeta meta = readMeta(exchange, true);
        if (meta.error != null) { sendJson(exchange, meta.status, meta.error); return; }
        PeerRef peer = peerFor(meta.groupId, meta.fromId);
        if (peer == null) { sendJson(exchange, 403, "{\"ok\":false,\"error\":\"unknown_peer\"}"); return; }
        if (!serverId.equals(meta.toId)) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"peer_mismatch\"}", peer, meta.nonce); return; }
        // 5.0.0-compatible relay behavior: each request authenticates itself.
        // Direct traffic is authenticated by the request itself; the probe stores no route state.
        if (!checkTimestamp(meta.timestamp)) { signedResponse(exchange, 401, "{\"ok\":false,\"error\":\"expired_request\"}", peer, meta.nonce); return; }
        if (!markRequestNonce(meta.groupId, meta.fromId, meta.nonce)) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"replayed_nonce\"}", peer, meta.nonce); return; }
        byte[] encrypted;
        try { encrypted = Base64.getDecoder().decode(readBody(exchange)); }
        catch (Exception ex) { signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_ciphertext\"}", peer, meta.nonce); return; }
        String plaintext;
        try { plaintext = decrypt(peer, meta, encrypted); }
        catch (Exception ex) { signedResponse(exchange, 401, "{\"ok\":false,\"error\":\"authentication_failed\"}", peer, meta.nonce); return; }
        int split = plaintext.indexOf('\n');
        if (split <= 0) { signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_message\"}", peer, meta.nonce); return; }
        String kind = plaintext.substring(0, split);
        String payload = plaintext.substring(split + 1);
        TrafficClass trafficClass = trafficClass(kind, payload);
        if (!peerAllowsReceive(peer, trafficClass)) {
            signedResponse(exchange, 403, "{\"ok\":false,\"error\":\"peer_receive_disabled\"}", peer, meta.nonce);
            return;
        }
        switch (kind) {
            case "public" -> handlePublicPayload(exchange, peer, meta, payload);
            case "reaction-request" -> handleReactionRequestPayload(exchange, peer, meta, payload);
            case "reaction" -> handleReactionPayload(exchange, peer, meta, payload);
            case "dm" -> handleDirectPayload(exchange, peer, meta, payload);
            case "read" -> handleReadPayload(exchange, peer, meta, payload);
            case "delete" -> handleDeletePayload(exchange, peer, meta, payload);
            case "typing" -> handleTypingPayload(exchange, peer, meta, payload);
            case "game-request" -> handleChatGameRequestPayload(exchange, peer, meta, payload);
            case "profile-request" -> handleProfileRequestPayload(exchange, peer, meta, payload);
            default -> signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"unknown_message_kind\"}", peer, meta.nonce);
        }
    }

    private void handlePublicPayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        RelayEnvelope envelope = RelayEnvelope.fromMap(JsonUtil.parseFlatObject(payload));
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}", peer, meta.nonce); return;
        }
        if (envelope.originServerId.equals(serverId) || !markSeen(envelope.relayId) || host.hasPublicMessage(envelope.relayId)) {
            signedResponse(exchange, 200, "{\"ok\":true,\"duplicate\":true}", peer, meta.nonce); return;
        }
        if (!host.acceptPublicMessage(envelope.toMessage())) {
            signedResponse(exchange, 200, "{\"ok\":true,\"duplicate\":true}", peer, meta.nonce); return;
        }
        signedResponse(exchange, 200, "{\"ok\":true}", peer, meta.nonce);
        // Forwarding is per hop. A plain-HTTP peer may exchange direct relay traffic,
        // but traffic received from that peer is not forwarded onward. Other HTTPS peers
        // in the same group remain eligible for forwarding.
        if (peer.group.forwardingEnabled && isHttpsPeer(peer) && envelope.hop + 1 < config.maxHops) {
            envelope.hop++; envelope.fromServerId = serverId;
            sendPublicToGroup(envelope, peer.group, peer.id(), true);
        }
    }

    private void handleReactionRequestPayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        ReactionRequestEnvelope envelope = ReactionRequestEnvelope.fromMap(JsonUtil.parseFlatObject(payload));
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_reaction_request\"}", peer, meta.nonce); return;
        }
        if (envelope.targetServerId.equals(serverId)) {
            // Requests are deliberately idempotent at the authority. Reprocessing the
            // same event after a lost HTTP response does not notify twice because the
            // store only reports a change on the first application, while republishing
            // the commit helps a recovered route converge on the authoritative state.
            boolean accepted = host.acceptPublicReaction(envelope.toReactionRequest());
            if (!accepted) {
                signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"reaction_rejected\"}", peer, meta.nonce); return;
            }
            // Public reactions retain origin-authoritative fan-out. DM reactions are
            // point-to-point: the requesting server applies the same committed state
            // locally after this authenticated 200 response, so no private metadata is
            // broadcast to unrelated relay peers.
            if ("public".equals(envelope.scope)) {
                publishCommittedPublicReaction(envelope.eventId, envelope.messageRelayId, envelope.actorUuid,
                        envelope.actorLabel, envelope.reaction, envelope.active);
            }
            signedResponse(exchange, 200, "{\"ok\":true,\"committed\":true}", peer, meta.nonce);
            return;
        }
        if (envelope.hop + 1 >= config.maxHops) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}", peer, meta.nonce); return;
        }
        envelope.hop++;
        envelope.fromServerId = serverId;
        ReactionRequestResult forwarded;
        try {
            forwarded = sendReactionRequestToPeers(envelope, peer.id(), peer.group)
                    .get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS);
        } catch (Exception ex) {
            signedResponse(exchange, 504, "{\"ok\":false,\"error\":\"reaction_forward_timeout\"}", peer, meta.nonce); return;
        }
        if (forwarded != null && forwarded.committed) {
            signedResponse(exchange, 200, "{\"ok\":true,\"committed\":true,\"forwarded\":true}", peer, meta.nonce); return;
        }
        int status = forwarded == null ? 502 : Math.max(400, forwarded.status);
        String error = forwarded == null || safe(forwarded.error).isBlank() ? "reaction_route_unavailable" : forwarded.error;
        signedResponse(exchange, status, "{\"ok\":false,\"error\":" + JsonUtil.quote(error) + "}", peer, meta.nonce);
    }

    private void handleReactionPayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        ReactionEnvelope envelope = ReactionEnvelope.fromMap(JsonUtil.parseFlatObject(payload));
        String seenKey = "reaction:" + envelope.eventId;
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}", peer, meta.nonce); return;
        }
        if (envelope.originServerId.equals(serverId) || !markSeen(seenKey)) {
            signedResponse(exchange, 200, "{\"ok\":true,\"duplicate\":true}", peer, meta.nonce); return;
        }
        if (!host.acceptPublicReaction(envelope.toReaction())) {
            seenRelayIds.remove(seenKey);
            signedResponse(exchange, 404, "{\"ok\":false,\"error\":\"reaction_target_unavailable\"}", peer, meta.nonce); return;
        }
        signedResponse(exchange, 200, "{\"ok\":true}", peer, meta.nonce);
        if (peer.group.forwardingEnabled && isHttpsPeer(peer) && envelope.hop + 1 < config.maxHops) {
            envelope.hop++; envelope.fromServerId = serverId;
            sendReactionToGroup(envelope, peer.group, peer.id(), true);
        }
    }

    private void handleChatGameRequestPayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        ChatGameRequestEnvelope envelope = ChatGameRequestEnvelope.fromMap(JsonUtil.parseFlatObject(payload));
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_game_request\"}", peer, meta.nonce); return;
        }
        if (envelope.targetServerId.equals(serverId)) {
            String requestJson = envelope.decodePayload();
            if (requestJson.isBlank()) { signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_game_request\"}", peer, meta.nonce); return; }
            String response = host.handleChatGameRelayRequest(envelope.originServerId, requestJson);
            if (safe(response).isBlank()) response = "{\"ok\":false,\"error\":\"game_relay_unavailable\"}";
            signedResponse(exchange, 200, response, peer, meta.nonce);
            return;
        }
        if (!peer.group.forwardingEnabled || !isHttpsPeer(peer)) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"forwarding_disabled_or_http_hop\"}", peer, meta.nonce); return;
        }
        if (envelope.hop + 1 >= config.maxHops) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}", peer, meta.nonce); return;
        }
        envelope.hop++; envelope.fromServerId = serverId;
        ChatGameRelayResponse forwarded;
        try { forwarded = sendChatGameRequestToPeers(envelope, peer.id(), peer.group).get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS); }
        catch (Exception ex) { signedResponse(exchange, 504, "{\"ok\":false,\"error\":\"game_forward_timeout\"}", peer, meta.nonce); return; }
        if (forwarded == null) { signedResponse(exchange, 502, "{\"ok\":false,\"error\":\"game_route_unavailable\"}", peer, meta.nonce); return; }
        signedResponse(exchange, forwarded.status, safe(forwarded.body).isBlank() ? "{\"ok\":false,\"error\":\"game_route_unavailable\"}" : forwarded.body, peer, meta.nonce);
    }


    private void handleProfileRequestPayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        ProfileRequestEnvelope envelope = ProfileRequestEnvelope.fromMap(JsonUtil.parseFlatObject(payload));
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_profile_request\"}", peer, meta.nonce); return;
        }
        if (envelope.targetServerId.equals(serverId)) {
            String requestJson = envelope.decodePayload();
            if (requestJson.isBlank()) { signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_profile_request\"}", peer, meta.nonce); return; }
            String response = host.handleProfileRelayRequest(envelope.originServerId, requestJson);
            if (safe(response).isBlank()) response = "{\"ok\":false,\"error\":\"profile_relay_unavailable\"}";
            signedResponse(exchange, 200, response, peer, meta.nonce);
            return;
        }
        if (!peer.group.forwardingEnabled || !isHttpsPeer(peer)) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"forwarding_disabled_or_http_hop\"}", peer, meta.nonce); return;
        }
        if (envelope.hop + 1 >= config.maxHops) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}", peer, meta.nonce); return;
        }
        envelope.hop++; envelope.fromServerId = serverId;
        ProfileRelayResponse forwarded;
        try { forwarded = sendProfileRequestToPeers(envelope, peer.id(), peer.group).get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS); }
        catch (Exception ex) { signedResponse(exchange, 504, "{\"ok\":false,\"error\":\"profile_forward_timeout\"}", peer, meta.nonce); return; }
        if (forwarded == null) { signedResponse(exchange, 502, "{\"ok\":false,\"error\":\"profile_route_unavailable\"}", peer, meta.nonce); return; }
        signedResponse(exchange, forwarded.status, safe(forwarded.body).isBlank() ? "{\"ok\":false,\"error\":\"profile_route_unavailable\"}" : forwarded.body, peer, meta.nonce);
    }

    private void handleDirectPayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        DirectMessageEnvelope envelope = DirectMessageEnvelope.fromMap(JsonUtil.parseFlatObject(payload));
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}", peer, meta.nonce); return;
        }
        if (envelope.originServerId.equals(serverId)) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"relay_loop\"}", peer, meta.nonce); return; }
        if (envelope.targetServerId.equals(serverId)) {
            if (host.hasDirectRelayId(envelope.relayId) || isDelivered(envelope.relayId)) {
                markSeen(envelope.relayId); markDelivered(envelope.relayId);
                signedResponse(exchange, 200, "{\"ok\":true,\"delivered\":true,\"duplicate\":true}", peer, meta.nonce); return;
            }
            if (!markSeen(envelope.relayId)) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"delivery_in_progress\"}", peer, meta.nonce); return; }
            boolean accepted = host.acceptDirectMessage(new RelayDirectMessage(envelope.relayId, envelope.originServerId, envelope.originServerName,
                    envelope.senderUuid, envelope.senderUsername, envelope.senderDisplayName, envelope.targetUuid, envelope.targetUsername,
                    envelope.targetDisplayName, envelope.message, envelope.gameMessage,
                    envelope.replyToRelayId, envelope.replyToSender, envelope.replyToPreview));
            if (!accepted) { seenRelayIds.remove(envelope.relayId); signedResponse(exchange, 404, "{\"ok\":false,\"error\":\"dm_target_unavailable\"}", peer, meta.nonce); return; }
            markDelivered(envelope.relayId); signedResponse(exchange, 200, "{\"ok\":true,\"delivered\":true}", peer, meta.nonce); return;
        }
        if (isDelivered(envelope.relayId)) { signedResponse(exchange, 200, "{\"ok\":true,\"delivered\":true,\"duplicate\":true}", peer, meta.nonce); return; }
        if (!peer.group.forwardingEnabled || !isHttpsPeer(peer)) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"forwarding_disabled_or_http_hop\"}", peer, meta.nonce); return;
        }
        if (!markSeen(envelope.relayId)) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"relay_in_progress\"}", peer, meta.nonce); return; }
        if (envelope.hop + 1 >= config.maxHops) { seenRelayIds.remove(envelope.relayId); signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}", peer, meta.nonce); return; }
        envelope.hop++; envelope.fromServerId = serverId;
        DirectMessageDelivery delivery;
        try { delivery = sendDirectToPeers(envelope, peer.id(), peer.group).get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS); }
        catch (Exception ex) { seenRelayIds.remove(envelope.relayId); signedResponse(exchange, 504, "{\"ok\":false,\"error\":\"dm_forward_timeout\"}", peer, meta.nonce); return; }
        if (delivery == null || !delivery.delivered) {
            seenRelayIds.remove(envelope.relayId); String error = delivery == null ? "dm_route_unavailable" : delivery.error;
            int status = delivery == null || delivery.httpStatus < 400 ? 502 : delivery.httpStatus;
            signedResponse(exchange, status, "{\"ok\":false,\"error\":" + JsonUtil.quote(error) + "}", peer, meta.nonce); return;
        }
        markDelivered(envelope.relayId); signedResponse(exchange, 200, "{\"ok\":true,\"delivered\":true,\"forwarded\":true}", peer, meta.nonce);
    }

    private void handleReadPayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        DirectMessageReadEnvelope envelope = DirectMessageReadEnvelope.fromMap(JsonUtil.parseFlatObject(payload));
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}", peer, meta.nonce); return;
        }
        if (envelope.targetServerId.equals(serverId)) {
            RelayReadApplyResult applied = host.applyDirectMessageRead(envelope.messageRelayId);
            if (applied == null || !applied.ok) {
                String error = applied == null || safe(applied.error).isBlank() ? "message_not_found" : applied.error;
                signedResponse(exchange, 404, "{\"ok\":false,\"error\":" + JsonUtil.quote(error) + "}", peer, meta.nonce); return;
            }
            if (applied.changed) host.publishDirectMessageUpdate(applied.localUserUuid, applied.remoteUserUuid, applied.threadId);
            signedResponse(exchange, 200, "{\"ok\":true,\"read\":true,\"changed\":" + applied.changed + "}", peer, meta.nonce); return;
        }
        if (!peer.group.forwardingEnabled || !isHttpsPeer(peer)) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"forwarding_disabled_or_http_hop\"}", peer, meta.nonce); return;
        }
        if (envelope.hop + 1 >= config.maxHops) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}", peer, meta.nonce); return; }
        envelope.hop++; envelope.fromServerId = serverId;
        boolean forwarded;
        try { forwarded = sendDirectReadToPeers(envelope, peer.id(), peer.group).get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS); }
        catch (Exception ex) { signedResponse(exchange, 504, "{\"ok\":false,\"error\":\"dm_read_forward_timeout\"}", peer, meta.nonce); return; }
        if (!forwarded) { signedResponse(exchange, 502, "{\"ok\":false,\"error\":\"dm_read_route_unavailable\"}", peer, meta.nonce); return; }
        signedResponse(exchange, 200, "{\"ok\":true,\"read\":true,\"forwarded\":true}", peer, meta.nonce);
    }

    private void handleDeletePayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        DirectMessageDeleteEnvelope envelope = DirectMessageDeleteEnvelope.fromMap(JsonUtil.parseFlatObject(payload));
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}", peer, meta.nonce); return;
        }
        if (envelope.targetServerId.equals(serverId)) {
            boolean applied = host.applyDirectMessageDelete(envelope.originServerId, envelope.senderUuid, envelope.messageRelayId);
            if (!applied) { signedResponse(exchange, 404, "{\"ok\":false,\"error\":\"message_not_found\"}", peer, meta.nonce); return; }
            signedResponse(exchange, 200, "{\"ok\":true,\"deleted\":true}", peer, meta.nonce); return;
        }
        if (!peer.group.forwardingEnabled || !isHttpsPeer(peer)) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"forwarding_disabled_or_http_hop\"}", peer, meta.nonce); return;
        }
        if (envelope.hop + 1 >= config.maxHops) { signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}", peer, meta.nonce); return; }
        envelope.hop++; envelope.fromServerId = serverId;
        boolean forwarded;
        try { forwarded = sendDirectDeleteToPeers(envelope, peer.id(), peer.group).get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS); }
        catch (Exception ex) { signedResponse(exchange, 504, "{\"ok\":false,\"error\":\"dm_delete_forward_timeout\"}", peer, meta.nonce); return; }
        if (!forwarded) { signedResponse(exchange, 502, "{\"ok\":false,\"error\":\"dm_delete_route_unavailable\"}", peer, meta.nonce); return; }
        signedResponse(exchange, 200, "{\"ok\":true,\"deleted\":true,\"forwarded\":true}", peer, meta.nonce);
    }

    private void handleTypingPayload(HttpExchange exchange, PeerRef peer, RequestMeta meta, String payload) throws IOException {
        Map<String, String> map = JsonUtil.parseFlatObject(payload);
        if ("public".equalsIgnoreCase(safe(map.get("scope")))) {
            PublicTypingEnvelope envelope = PublicTypingEnvelope.fromMap(map);
            String seenKey = "typing-public:" + envelope.eventId;
            if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
                signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}", peer, meta.nonce); return;
            }
            if (envelope.expiresAt <= System.currentTimeMillis() - 1000L) {
                signedResponse(exchange, 200, "{\"ok\":true,\"expired\":true}", peer, meta.nonce); return;
            }
            if (envelope.originServerId.equals(serverId) || !markSeen(seenKey)) {
                signedResponse(exchange, 200, "{\"ok\":true,\"duplicate\":true}", peer, meta.nonce); return;
            }
            if (!host.acceptPublicTyping(envelope.toTyping())) {
                seenRelayIds.remove(seenKey);
                signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"public_typing_rejected\"}", peer, meta.nonce); return;
            }
            signedResponse(exchange, 200, "{\"ok\":true}", peer, meta.nonce);
            if (peer.group.forwardingEnabled && isHttpsPeer(peer) && envelope.hop + 1 < config.maxHops) {
                envelope.hop++; envelope.fromServerId = serverId;
                sendPublicTypingToGroup(envelope, peer.group, peer.id(), true);
            }
            return;
        }

        DirectTypingEnvelope envelope = DirectTypingEnvelope.fromMap(map);
        String seenKey = "typing:" + envelope.eventId;
        if (!envelope.valid() || !peer.id().equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            signedResponse(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}", peer, meta.nonce); return;
        }
        if (envelope.expiresAt <= System.currentTimeMillis() - 1000L) {
            signedResponse(exchange, 200, "{\"ok\":true,\"expired\":true}", peer, meta.nonce); return;
        }
        if (envelope.originServerId.equals(serverId)) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"relay_loop\"}", peer, meta.nonce); return;
        }
        if (envelope.targetServerId.equals(serverId)) {
            if (!markSeen(seenKey)) { signedResponse(exchange, 200, "{\"ok\":true,\"duplicate\":true}", peer, meta.nonce); return; }
            boolean accepted = host.acceptDirectTyping(envelope.toTyping());
            if (!accepted) { seenRelayIds.remove(seenKey); signedResponse(exchange, 404, "{\"ok\":false,\"error\":\"dm_target_unavailable\"}", peer, meta.nonce); return; }
            signedResponse(exchange, 200, "{\"ok\":true}", peer, meta.nonce); return;
        }
        if (!peer.group.forwardingEnabled || !isHttpsPeer(peer)) {
            signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"forwarding_disabled_or_http_hop\"}", peer, meta.nonce); return;
        }
        if (!markSeen(seenKey)) { signedResponse(exchange, 200, "{\"ok\":true,\"duplicate\":true}", peer, meta.nonce); return; }
        if (envelope.hop + 1 >= config.maxHops) { seenRelayIds.remove(seenKey); signedResponse(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}", peer, meta.nonce); return; }
        envelope.hop++; envelope.fromServerId = serverId;
        boolean forwarded;
        try { forwarded = sendDirectTypingToPeers(envelope, peer.id(), peer.group).get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS); }
        catch (Exception ex) { seenRelayIds.remove(seenKey); signedResponse(exchange, 504, "{\"ok\":false,\"error\":\"dm_typing_forward_timeout\"}", peer, meta.nonce); return; }
        if (!forwarded) { seenRelayIds.remove(seenKey); signedResponse(exchange, 502, "{\"ok\":false,\"error\":\"dm_typing_route_unavailable\"}", peer, meta.nonce); return; }
        signedResponse(exchange, 200, "{\"ok\":true,\"forwarded\":true}", peer, meta.nonce);
    }

    // Compatibility method names retained for loader code compiled against the prior core surface.
    public void handleIncoming(HttpExchange exchange) throws IOException { handleLegacyV1(exchange); }
    public void handleIncomingDirectMessage(HttpExchange exchange) throws IOException { handleLegacyV1(exchange); }
    public void handleIncomingDirectMessageRead(HttpExchange exchange) throws IOException { handleLegacyV1(exchange); }

    private void sendPublicToGroup(RelayEnvelope envelope, GroupRef group, String excludePeerId, boolean forwarding) {
        if (!isEnabled() || envelope == null || group == null) return;
        if (forwarding && !group.forwardingEnabled) return;
        String payload = envelope.toJson();
        TrafficClass trafficClass = "event".equalsIgnoreCase(safe(envelope.source)) ? TrafficClass.EVENT : TrafficClass.PUBLIC_CHAT;
        for (PeerRef peer : group.peers.values()) {
            if (peer.id().equals(excludePeerId) || peer.id().equals(envelope.originServerId)) continue;
            if (!isPeerUsable(peer, forwarding, trafficClass)) continue;
            sendMessage(peer, "public", payload).thenAccept(x -> {});
        }
    }

    private void sendReactionToGroup(ReactionEnvelope envelope, GroupRef group, String excludePeerId, boolean forwarding) {
        if (!isEnabled() || envelope == null || group == null) return;
        if (forwarding && !group.forwardingEnabled) return;
        String payload = envelope.toJson();
        for (PeerRef peer : group.peers.values()) {
            if (peer.id().equals(excludePeerId) || peer.id().equals(envelope.originServerId)) continue;
            if (!isPeerUsable(peer, forwarding, TrafficClass.PUBLIC_CHAT)) continue;
            sendMessage(peer, "reaction", payload).thenAccept(x -> {});
        }
    }

    private CompletableFuture<ReactionRequestResult> sendReactionRequestToPeers(ReactionRequestEnvelope envelope,
                                                                                 String excludePeerId,
                                                                                 GroupRef groupConstraint) {
        if (!isEnabled() || envelope == null) return CompletableFuture.completedFuture(ReactionRequestResult.retryable("relay_disabled", 503));
        TrafficClass trafficClass = "dm".equals(envelope.scope) ? TrafficClass.DM : TrafficClass.PUBLIC_CHAT;
        PeerRef direct = peersById.get(envelope.targetServerId);
        if (direct != null && (groupConstraint == null || direct.group == groupConstraint) && !direct.id().equals(excludePeerId)) {
            boolean forwarding = !safe(excludePeerId).isBlank() || envelope.hop > 0;
            if (isPeerUsable(direct, forwarding, trafficClass)) return sendReactionRequest(direct, envelope.toJson());
        }
        PeerRef next = uniqueForwardingNextHop(groupConstraint, excludePeerId, envelope.targetServerId, trafficClass);
        if (next == null) return CompletableFuture.completedFuture(ReactionRequestResult.retryable("reaction_route_unavailable", 502));
        return sendReactionRequest(next, envelope.toJson());
    }

    private CompletableFuture<ChatGameRelayResponse> sendChatGameRequestToPeers(ChatGameRequestEnvelope envelope, String excludePeerId, GroupRef groupConstraint) {
        if (!isEnabled() || envelope == null) return CompletableFuture.completedFuture(ChatGameRelayResponse.failed("relay_disabled", 503));
        PeerRef direct = peersById.get(envelope.targetServerId);
        if (direct != null && (groupConstraint == null || direct.group == groupConstraint) && !direct.id().equals(excludePeerId)) {
            boolean forwarding = !safe(excludePeerId).isBlank() || envelope.hop > 0;
            if (isPeerUsable(direct, forwarding, TrafficClass.EVENT)) return sendChatGameRequest(direct, envelope.toJson());
        }
        PeerRef next = uniqueForwardingNextHop(groupConstraint, excludePeerId, envelope.targetServerId, TrafficClass.EVENT);
        if (next == null) return CompletableFuture.completedFuture(ChatGameRelayResponse.failed("game_route_unavailable", 502));
        return sendChatGameRequest(next, envelope.toJson());
    }

    private CompletableFuture<ProfileRelayResponse> sendProfileRequestToPeers(ProfileRequestEnvelope envelope, String excludePeerId, GroupRef groupConstraint) {
        if (!isEnabled() || envelope == null) return CompletableFuture.completedFuture(ProfileRelayResponse.failed("relay_disabled", 503));
        PeerRef direct = peersById.get(envelope.targetServerId);
        if (direct != null && (groupConstraint == null || direct.group == groupConstraint) && !direct.id().equals(excludePeerId)) {
            boolean forwarding = !safe(excludePeerId).isBlank() || envelope.hop > 0;
            if (isPeerUsable(direct, forwarding, TrafficClass.PROFILE)) return sendProfileRequest(direct, envelope.toJson());
        }
        PeerRef next = uniqueForwardingNextHop(groupConstraint, excludePeerId, envelope.targetServerId, TrafficClass.PROFILE);
        if (next == null) return CompletableFuture.completedFuture(ProfileRelayResponse.failed("profile_route_unavailable", 502));
        return sendProfileRequest(next, envelope.toJson());
    }

    private CompletableFuture<DirectMessageDelivery> sendDirectToPeers(DirectMessageEnvelope envelope, String excludePeerId, GroupRef groupConstraint) {
        if (!isEnabled() || envelope == null) return CompletableFuture.completedFuture(DirectMessageDelivery.failed("relay_disabled", 503));
        PeerRef direct = peersById.get(envelope.targetServerId);
        if (direct != null && (groupConstraint == null || direct.group == groupConstraint) && !direct.id().equals(excludePeerId)) {
            boolean forwarding = !safe(excludePeerId).isBlank() || envelope.hop > 0;
            if (isPeerUsable(direct, forwarding, TrafficClass.DM)) return sendDirect(direct, envelope.toJson());
        }
        PeerRef next = uniqueForwardingNextHop(groupConstraint, excludePeerId, envelope.targetServerId, TrafficClass.DM);
        if (next == null) return CompletableFuture.completedFuture(DirectMessageDelivery.failed("dm_route_unavailable", 404));
        return sendDirect(next, envelope.toJson());
    }

    private CompletableFuture<Boolean> sendDirectReadToPeers(DirectMessageReadEnvelope envelope, String excludePeerId, GroupRef groupConstraint) {
        if (!isEnabled() || envelope == null) return CompletableFuture.completedFuture(false);
        PeerRef direct = peersById.get(envelope.targetServerId);
        if (direct != null && (groupConstraint == null || direct.group == groupConstraint) && !direct.id().equals(excludePeerId)) {
            boolean forwarding = !safe(excludePeerId).isBlank() || envelope.hop > 0;
            if (isPeerUsable(direct, forwarding, TrafficClass.DM)) return sendRead(direct, envelope.toJson());
        }
        PeerRef next = uniqueForwardingNextHop(groupConstraint, excludePeerId, envelope.targetServerId, TrafficClass.DM);
        return next == null ? CompletableFuture.completedFuture(false) : sendRead(next, envelope.toJson());
    }

    private CompletableFuture<Boolean> sendDirectDeleteToPeers(DirectMessageDeleteEnvelope envelope, String excludePeerId, GroupRef groupConstraint) {
        if (!isEnabled() || envelope == null) return CompletableFuture.completedFuture(false);
        PeerRef direct = peersById.get(envelope.targetServerId);
        if (direct != null && (groupConstraint == null || direct.group == groupConstraint) && !direct.id().equals(excludePeerId)) {
            boolean forwarding = !safe(excludePeerId).isBlank() || envelope.hop > 0;
            if (isPeerUsable(direct, forwarding, TrafficClass.DM)) return sendDelete(direct, envelope.toJson());
        }
        PeerRef next = uniqueForwardingNextHop(groupConstraint, excludePeerId, envelope.targetServerId, TrafficClass.DM);
        return next == null ? CompletableFuture.completedFuture(false) : sendDelete(next, envelope.toJson());
    }

    private void sendPublicTypingToGroup(PublicTypingEnvelope envelope, GroupRef group, String excludePeerId, boolean forwarding) {
        if (!isEnabled() || envelope == null || group == null) return;
        if (forwarding && !group.forwardingEnabled) return;
        String payload = envelope.toJson();
        for (PeerRef peer : group.peers.values()) {
            if (peer.id().equals(excludePeerId) || peer.id().equals(envelope.originServerId)) continue;
            if (!isPeerUsable(peer, forwarding, TrafficClass.PUBLIC_CHAT)) continue;
            sendTyping(peer, payload).thenAccept(x -> {});
        }
    }

    private CompletableFuture<Boolean> sendDirectTypingToPeers(DirectTypingEnvelope envelope, String excludePeerId, GroupRef groupConstraint) {
        if (!isEnabled() || envelope == null) return CompletableFuture.completedFuture(false);
        PeerRef direct = peersById.get(envelope.targetServerId);
        if (direct != null && (groupConstraint == null || direct.group == groupConstraint) && !direct.id().equals(excludePeerId)) {
            boolean forwarding = !safe(excludePeerId).isBlank() || envelope.hop > 0;
            if (isPeerUsable(direct, forwarding, TrafficClass.DM)) return sendTyping(direct, envelope.toJson());
        }
        PeerRef next = uniqueForwardingNextHop(groupConstraint, excludePeerId, envelope.targetServerId, TrafficClass.DM);
        return next == null ? CompletableFuture.completedFuture(false) : sendTyping(next, envelope.toJson());
    }

    private PeerRef uniqueForwardingNextHop(GroupRef groupConstraint, String excludePeerId, String targetServerId, TrafficClass trafficClass) {
        PeerRef found = null;
        for (GroupRef group : groupsById.values()) {
            if (groupConstraint != null && group != groupConstraint) continue;
            if (!group.forwardingEnabled) continue;
            for (PeerRef peer : group.peers.values()) {
                if (peer.id().equals(excludePeerId) || peer.id().equals(serverId) || peer.id().equals(targetServerId)) continue;
                if (!isPeerUsable(peer, true, trafficClass)) continue;
                if (found != null) return null; // deterministic routing only; avoid accidental fan-out.
                found = peer;
            }
        }
        return found;
    }

    private CompletableFuture<ReactionRequestResult> sendReactionRequest(PeerRef peer, String json) {
        return sendMessage(peer, "reaction-request", json).thenApply(result -> {
            if (result == null) return ReactionRequestResult.retryable("reaction_transport_error", 502);
            Map<String,String> parsed = JsonUtil.parseFlatObject(result.body);
            boolean committed = Boolean.parseBoolean(safe(parsed.get("committed")));
            if (result.status >= 200 && result.status < 300 && committed) return ReactionRequestResult.committed(result.status);
            String error = safe(parsed.get("error"));
            if (error.isBlank()) error = result.status >= 200 && result.status < 300 ? "reaction_commit_not_confirmed" : "remote_http_" + result.status;
            boolean retryable = result.status >= 500 || result.status == 429
                    || error.contains("route_unavailable") || error.contains("timeout")
                    || error.contains("transport") || error.contains("relay_disabled");
            return retryable ? ReactionRequestResult.retryable(error, result.status) : ReactionRequestResult.rejected(error, result.status);
        });
    }

    private CompletableFuture<DirectMessageDelivery> sendDirect(PeerRef peer, String json) {
        return sendMessage(peer, "dm", json).thenApply(result -> {
            if (result == null) return DirectMessageDelivery.failed("dm_transport_error", 502);
            Map<String,String> parsed = JsonUtil.parseFlatObject(result.body);
            boolean delivered = Boolean.parseBoolean(safe(parsed.get("delivered")));
            if (result.status >= 200 && result.status < 300 && delivered) return DirectMessageDelivery.delivered(result.status);
            String error = safe(parsed.get("error"));
            if (error.isBlank()) error = result.status >= 200 && result.status < 300 ? "delivery_not_confirmed" : "remote_http_" + result.status;
            return DirectMessageDelivery.failed(error, result.status);
        });
    }

    private CompletableFuture<ChatGameRelayResponse> sendChatGameRequest(PeerRef peer, String json) {
        return sendMessage(peer, "game-request", json).thenApply(result -> {
            if (result == null) return ChatGameRelayResponse.failed("game_transport_error", 502);
            return new ChatGameRelayResponse(result.status, result.body, result.status >= 200 && result.status < 300 ? "" : "remote_http_" + result.status);
        });
    }

    private CompletableFuture<ProfileRelayResponse> sendProfileRequest(PeerRef peer, String json) {
        return sendMessage(peer, "profile-request", json).thenApply(result -> {
            if (result == null) return ProfileRelayResponse.failed("profile_transport_error", 502);
            return new ProfileRelayResponse(result.status, result.body, result.status >= 200 && result.status < 300 ? "" : "remote_http_" + result.status);
        });
    }

    private CompletableFuture<Boolean> sendRead(PeerRef peer, String json) {
        return sendMessage(peer, "read", json).thenApply(result -> result != null && result.status >= 200 && result.status < 300
                && Boolean.parseBoolean(safe(JsonUtil.parseFlatObject(result.body).get("read"))));
    }

    private CompletableFuture<Boolean> sendDelete(PeerRef peer, String json) {
        return sendMessage(peer, "delete", json).thenApply(result -> result != null && result.status >= 200 && result.status < 300
                && Boolean.parseBoolean(safe(JsonUtil.parseFlatObject(result.body).get("deleted"))));
    }

    private CompletableFuture<SignedResult> sendMessage(PeerRef peer, String kind, String json) {
        if (peer == null || isBackedOff(peer.id())) return CompletableFuture.completedFuture(null);
        if (!peerAllowsSend(peer, trafficClass(kind, json))) {
            return CompletableFuture.completedFuture(new SignedResult(403, "{\"ok\":false,\"error\":\"peer_send_disabled\"}"));
        }
        try {
            long timestamp = System.currentTimeMillis();
            String nonce = SecurityUtil.randomToken(18);
            byte[] iv = new byte[GCM_IV_BYTES]; RNG.nextBytes(iv);
            String ivText = Base64.getEncoder().encodeToString(iv);
            RequestMeta meta = new RequestMeta(peer.group.id, serverId, peer.id(), timestamp, nonce, ivText, 0, null);
            byte[] cipher = encrypt(peer, meta, (kind + "\n" + json).getBytes(StandardCharsets.UTF_8));
            String body = Base64.getEncoder().encodeToString(cipher);
            HttpRequest request = HttpRequest.newBuilder(relayMessageUri(peer.peer.url))
                    .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                    .header("Content-Type", "text/plain; charset=us-ascii")
                    .header(HEADER_VERSION, PROTOCOL_MAJOR)
                    .header(HEADER_PROTOCOL, PROTOCOL_REVISION)
                    .header(HEADER_CAPABILITIES, CAPABILITIES_CSV)
                    .header(HEADER_GROUP, peer.group.id)
                    .header(HEADER_FROM, serverId)
                    .header(HEADER_TO, peer.id())
                    .header(HEADER_TIMESTAMP, Long.toString(timestamp))
                    .header(HEADER_NONCE, nonce)
                    .header(HEADER_IV, ivText)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.US_ASCII)).build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).handle((response, error) -> {
                if (error != null || response == null) { recordTransportFailure(peer.id(), error == null ? "empty response" : safe(error.getMessage())); return null; }
                if (!verifyResponse(peer, nonce, response)) { recordTransportFailure(peer.id(), "unauthenticated response"); return new SignedResult(502, "{\"ok\":false,\"error\":\"unauthenticated_response\"}"); }
                recordTransportSuccess(peer.id());
                return new SignedResult(response.statusCode(), response.body());
            });
        } catch (Exception ex) {
            recordTransportFailure(peer.id(), safe(ex.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Boolean> sendTyping(PeerRef peer, String json) {
        return sendMessage(peer, "typing", json).thenApply(result -> result != null && result.status >= 200 && result.status < 300);
    }

    private byte[] encrypt(PeerRef peer, RequestMeta meta, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = Base64.getDecoder().decode(meta.iv);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(deriveKey(peer, meta.fromId, meta.toId), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(aad(meta).getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(plaintext);
    }

    private String decrypt(PeerRef peer, RequestMeta meta, byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = Base64.getDecoder().decode(meta.iv);
        if (iv.length != GCM_IV_BYTES) throw new IllegalArgumentException("bad iv");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(peer, meta.fromId, meta.toId), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(aad(meta).getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private byte[] deriveKey(PeerRef peer, String from, String to) throws Exception {
        byte[] ikm = peer.group.secret.getBytes(StandardCharsets.UTF_8);
        byte[] salt = ("KWC-Relay-v2|" + peer.group.id).getBytes(StandardCharsets.UTF_8);
        byte[] prk = hmacBytes(salt, ikm);
        byte[] info = ("message|" + peer.group.id + "|" + from + "|" + to).getBytes(StandardCharsets.UTF_8);
        return hkdfExpand(prk, info, 32);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[length]; byte[] previous = new byte[0]; int offset = 0; int counter = 1;
        while (offset < length) {
            mac.reset(); mac.update(previous); mac.update(info); mac.update((byte) counter++); previous = mac.doFinal();
            int n = Math.min(previous.length, length - offset); System.arraycopy(previous, 0, out, offset, n); offset += n;
        }
        return out;
    }

    private static byte[] hmacBytes(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(data);
    }

    private static String hmacHex(String secret, String canonical) {
        try {
            byte[] digest = hmacBytes(secret.getBytes(StandardCharsets.UTF_8), canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception ex) { throw new IllegalStateException("HMAC-SHA256 unavailable", ex); }
    }

    private String aad(RequestMeta meta) {
        return meta.groupId + "\n" + meta.fromId + "\n" + meta.toId + "\n" + meta.timestamp + "\n" + meta.nonce + "\n" + meta.iv;
    }

    private String handshakeCanonical(RequestMeta meta, String transport, String peerRevision) {
        String revision = peerRevision.isBlank() ? PROTOCOL_REVISION : peerRevision;
        return "handshake\n" + PROTOCOL_MAJOR + "\n" + revision + "\n" + meta.groupId + "\n" + meta.fromId + "\n" + meta.toId
                + "\n" + meta.timestamp + "\n" + meta.nonce + "\n" + transport;
    }

    private String legacyV20HandshakeCanonical(RequestMeta meta, String transport) {
        return "handshake\n" + PROTOCOL_MAJOR + "\n" + LEGACY_V20_HANDSHAKE_PRODUCT_VERSION + "\n" + meta.groupId + "\n" + meta.fromId + "\n" + meta.toId
                + "\n" + meta.timestamp + "\n" + meta.nonce + "\n" + transport;
    }

    private static String normalizeProtocolRevision(String value) {
        String v = safe(value).trim();
        return v.matches("2(?:\\.[0-9]{1,3})?") ? v : "";
    }

    private static String protocolErrorJson(String error) {
        return "{\"ok\":false,\"error\":" + JsonUtil.quote(error) + ",\"protocolMajor\":2,\"protocol\":"
                + JsonUtil.quote(PROTOCOL_REVISION) + "}";
    }

    private boolean verifyResponse(PeerRef peer, String requestNonce, HttpResponse<String> response) {
        String ts = response.headers().firstValue(HEADER_RESPONSE_TIMESTAMP).orElse("");
        String sig = response.headers().firstValue(HEADER_RESPONSE_SIGNATURE).orElse("").toLowerCase(Locale.ROOT);
        if (ts.isBlank() || sig.isBlank()) return false;
        try { if (!checkTimestamp(Long.parseLong(ts))) return false; } catch (NumberFormatException ex) { return false; }
        String canonical = "response\n" + peer.group.id + "\n" + peer.id() + "\n" + serverId + "\n" + ts + "\n" + requestNonce + "\n" + response.statusCode() + "\n" + safe(response.body());
        return constantTimeEquals(sig, hmacHex(peer.group.secret, canonical));
    }

    private void signedResponse(HttpExchange exchange, int status, String body, PeerRef peer, String requestNonce) throws IOException {
        String ts = Long.toString(System.currentTimeMillis());
        String canonical = "response\n" + peer.group.id + "\n" + serverId + "\n" + peer.id() + "\n" + ts + "\n" + requestNonce + "\n" + status + "\n" + body;
        exchange.getResponseHeaders().set(HEADER_RESPONSE_TIMESTAMP, ts);
        exchange.getResponseHeaders().set(HEADER_RESPONSE_SIGNATURE, hmacHex(peer.group.secret, canonical));
        sendJson(exchange, status, body);
    }

    private RequestMeta readMeta(HttpExchange exchange, boolean requireIv) {
        if (!PROTOCOL_MAJOR.equals(header(exchange, HEADER_VERSION))) return new RequestMeta("", "", "", 0, "", "", 426, protocolErrorJson("unsupported_protocol"));
        String group = normalizeId(header(exchange, HEADER_GROUP));
        String from = normalizeId(header(exchange, HEADER_FROM));
        String to = normalizeId(header(exchange, HEADER_TO));
        String nonce = safe(header(exchange, HEADER_NONCE)).trim();
        String iv = requireIv ? safe(header(exchange, HEADER_IV)).trim() : "";
        long ts;
        try { ts = Long.parseLong(header(exchange, HEADER_TIMESTAMP)); }
        catch (NumberFormatException ex) { return new RequestMeta(group, from, to, 0, nonce, iv, 401, "{\"ok\":false,\"error\":\"invalid_timestamp\"}"); }
        if (group.isBlank() || from.isBlank() || to.isBlank() || nonce.length() < 8 || nonce.length() > 180 || (requireIv && iv.isBlank()))
            return new RequestMeta(group, from, to, ts, nonce, iv, 400, "{\"ok\":false,\"error\":\"invalid_headers\"}");
        return new RequestMeta(group, from, to, ts, nonce, iv, 0, null);
    }

    private PeerRef peerFor(String groupId, String peerId) {
        GroupRef group = groupsById.get(groupId);
        if (group == null) return null;
        PeerRef peer = group.peers.get(peerId);
        return peer != null && peersById.get(peerId) == peer ? peer : null;
    }

    private enum TrafficClass { PUBLIC_CHAT, EVENT, DM, PROFILE }

    private static TrafficClass trafficClass(String kind, String payload) {
        String normalizedKind = safe(kind).trim().toLowerCase(Locale.ROOT);
        if (normalizedKind.equals("game-request")) return TrafficClass.EVENT;
        if (normalizedKind.equals("profile-request")) return TrafficClass.PROFILE;
        if (normalizedKind.equals("dm") || normalizedKind.equals("read") || normalizedKind.equals("delete")) return TrafficClass.DM;
        if (normalizedKind.equals("reaction-request")) {
            return "dm".equalsIgnoreCase(safe(JsonUtil.parseFlatObject(payload).get("scope"))) ? TrafficClass.DM : TrafficClass.PUBLIC_CHAT;
        }
        if (normalizedKind.equals("typing")) {
            return "public".equalsIgnoreCase(safe(JsonUtil.parseFlatObject(payload).get("scope"))) ? TrafficClass.PUBLIC_CHAT : TrafficClass.DM;
        }
        if (normalizedKind.equals("public")) {
            String source = safe(JsonUtil.parseFlatObject(payload).get("source")).trim().toLowerCase(Locale.ROOT);
            return source.equals("event") ? TrafficClass.EVENT : TrafficClass.PUBLIC_CHAT;
        }
        return TrafficClass.PUBLIC_CHAT;
    }

    private static boolean policyAllows(RelaySettings.DirectionPolicy policy, TrafficClass trafficClass) {
        if (policy == null || !policy.enabled) return false;
        return switch (trafficClass) {
            case EVENT -> policy.event;
            case DM -> policy.dm;
            case PROFILE -> policy.profile;
            case PUBLIC_CHAT -> policy.publicChat;
        };
    }

    private static boolean peerAllowsSend(PeerRef peer, TrafficClass trafficClass) {
        return peer != null && policyAllows(peer.peer.send, trafficClass);
    }

    private static boolean peerAllowsReceive(PeerRef peer, TrafficClass trafficClass) {
        return peer != null && policyAllows(peer.peer.receive, trafficClass);
    }

    private boolean isPeerUsable(PeerRef peer, boolean forwarding, TrafficClass trafficClass) {
        return isPeerUsable(peer, forwarding) && peerAllowsSend(peer, trafficClass);
    }

    private boolean isPeerUsable(PeerRef peer, boolean forwarding) {
        if (peer == null || isBackedOff(peer.id())) return false;
        if (!forwarding) return true;
        // Forwarding is filtered per hop, not per group. HTTP peers remain usable for
        // direct relay, while only HTTPS peers can be selected as forwarding hops.
        return peer.group.forwardingEnabled && isHttpsPeer(peer);
    }

    private boolean isHttpsPeer(PeerRef peer) { return "https".equals(transportForPeer(peer)); }
    private static boolean isPlainHttpUrl(String value) {
        String url = safe(value).trim().toLowerCase(Locale.ROOT);
        return url.startsWith("http://");
    }
    private String transportForPeer(PeerRef peer) {
        try { return normalizeTransport(relayBaseUri(peer.peer.url).getScheme()); } catch (Exception ex) { return ""; }
    }
    private static String normalizeTransport(String value) {
        String v = safe(value).trim().toLowerCase(Locale.ROOT); return (v.equals("http") || v.equals("https")) ? v : "";
    }

    private void warnForwardingBlocked(PeerRef peer, String direction) {
        warnText("security.relayForwardingHttpBlocked",
                "[WARNING] Relay forwarding was blocked for peer '{peer}' because every forwarding hop must be HTTPS.",
                "peer", peer.id(), "url", peer.peer.url, "direction", direction);
    }
    private void warnText(String key, String fallback, String... values) {
        host.warn(text(key, fallback, values));
    }
    private String text(String key, String fallback, String... values) {
        Map<String,String> args = new LinkedHashMap<>();
        if (values != null) for (int i=0;i+1<values.length;i+=2) args.put(values[i], values[i+1]);
        WebChatLanguage language = host.language();
        return language == null ? format(fallback,args) : language.text(key,fallback,args);
    }
    private static String format(String template, Map<String,String> args) {
        String out=safe(template); for (Map.Entry<String,String> e:args.entrySet()) out=out.replace("{"+e.getKey()+"}",safe(e.getValue())); return out;
    }

    private boolean markSeen(String relayId) {
        cleanupReplayCaches(); long now=System.currentTimeMillis(); return seenRelayIds.putIfAbsent(safe(relayId), now) == null;
    }
    private void markDelivered(String relayId) { deliveredRelayIds.put(safe(relayId), System.currentTimeMillis()); }
    private boolean isDelivered(String relayId) { cleanupReplayCaches(); return deliveredRelayIds.containsKey(safe(relayId)); }
    private boolean markRequestNonce(String group, String from, String nonce) {
        cleanupReplayCaches(); return seenRequestNonces.putIfAbsent(group + "|" + from + "|" + nonce, System.currentTimeMillis()) == null;
    }
    private void cleanupReplayCaches() {
        long cutoff=System.currentTimeMillis() - Math.max(30, config == null ? 300 : config.dedupeSeconds) * 1000L;
        seenRelayIds.entrySet().removeIf(e->e.getValue()<cutoff); deliveredRelayIds.entrySet().removeIf(e->e.getValue()<cutoff); seenRequestNonces.entrySet().removeIf(e->e.getValue()<cutoff);
    }
    private boolean checkTimestamp(long timestamp) { long skew=(config==null?60:config.maxClockSkewSeconds)*1000L, now=System.currentTimeMillis(); return timestamp>=now-skew && timestamp<=now+skew; }

    private boolean isBackedOff(String peerId) { Long until=outboundBackoffUntil.get(peerId); if (until==null) return false; if (until<=System.currentTimeMillis()) { outboundBackoffUntil.remove(peerId,until); return false; } return true; }
    private void recordTransportSuccess(String peerId) {
        transportFailures.remove(peerId);
        outboundBackoffUntil.remove(peerId);
        issues.recovered("relay-transport:" + peerId,
                "Server relay v2 transport recovered for peer " + peerId + ".");
    }
    private void recordTransportFailure(String peerId, String detail) {
        String safeDetail = safe(detail).trim();
        if (safeDetail.isBlank()) safeDetail = "unknown transport error";
        int n = transportFailures.merge(peerId, 1, Integer::sum);
        if (n >= 3) {
            outboundBackoffUntil.put(peerId, System.currentTimeMillis() + OUTBOUND_BACKOFF_MILLIS);
            transportFailures.put(peerId, 0);
        }
        issues.failed("relay-transport:" + peerId, safeDetail,
                "Server relay v2 transport failure for peer " + peerId + ": " + safeDetail);
    }

    private URI relayBaseUri(String configured) {
        String url=safe(configured).trim(); if(url.isBlank()) throw new IllegalArgumentException("URL is blank");
        URI uri=URI.create(url); String scheme=normalizeTransport(uri.getScheme()); if(scheme.isBlank()) throw new IllegalArgumentException("URL must use http or https");
        return uri;
    }
    private URI relayMessageUri(String configured) { return relayEndpointUri(configured, "/relay/v2/message"); }
    private URI relayEndpointUri(String configured, String endpoint) {
        String url=relayBaseUri(configured).toString(); while(url.endsWith("/")) url=url.substring(0,url.length()-1);
        String[] legacy={"/relay/handshake","/relay/receive","/relay/dm/receive","/relay/dm/read","/relay/v2/handshake","/relay/v2/message"};
        for(String suffix:legacy) if(url.endsWith(suffix)){url=url.substring(0,url.length()-suffix.length()); break;}
        return URI.create(url+endpoint);
    }


    private String readBody(HttpExchange exchange) throws IOException { return new String(readLimited(exchange, MAX_BODY_BYTES), StandardCharsets.US_ASCII).trim(); }
    private static byte[] readLimited(HttpExchange exchange,int maxBytes)throws IOException { byte[] data=exchange.getRequestBody().readNBytes(maxBytes+1); if(data.length>maxBytes) throw new IOException("body_too_large"); return data; }
    private static String header(HttpExchange exchange,String name){return safe(exchange.getRequestHeaders().getFirst(name)).trim();}
    private static boolean constantTimeEquals(String a,String b){return MessageDigest.isEqual(safe(a).getBytes(StandardCharsets.US_ASCII),safe(b).getBytes(StandardCharsets.US_ASCII));}
    private static void sendJson(HttpExchange exchange,int status,String json)throws IOException{byte[] bytes=json.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");exchange.sendResponseHeaders(status,bytes.length);try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}}
    private static String normalizeId(String raw){String id=safe(raw).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]","-");while(id.contains("--"))id=id.replace("--","-");while(id.startsWith("-"))id=id.substring(1);while(id.endsWith("-"))id=id.substring(0,id.length()-1);return id.length()>64?id.substring(0,64):id;}
    private static String fallbackServerId(String raw){String id=normalizeId(raw);return id.isBlank()?"server":id;}
    private static String safe(String value){return value==null?"":value;}

    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        seenRelayIds.clear();deliveredRelayIds.clear();seenRequestNonces.clear();issues.clearAll();
    }

    private static final class GroupRef {
        final String id; final String secret; final boolean forwardingEnabled; final Map<String,PeerRef> peers=new LinkedHashMap<>();
        GroupRef(String id,String secret,boolean forwardingEnabled){this.id=id;this.secret=secret;this.forwardingEnabled=forwardingEnabled;}
    }
    private static final class PeerRef {
        final GroupRef group; final RelaySettings.Peer peer; PeerRef(GroupRef group,RelaySettings.Peer peer){this.group=group;this.peer=peer;} String id(){return peer.id;}
    }
    private record SignedResult(int status,String body){}
    private record RequestMeta(String groupId,String fromId,String toId,long timestamp,String nonce,String iv,int status,String error){}

    public static final class ChatGameRelayResponse {
        public final int status;
        public final String body;
        public final String error;
        private ChatGameRelayResponse(int status, String body, String error) { this.status = status; this.body = body == null ? "" : body; this.error = error == null ? "" : error; }
        public static ChatGameRelayResponse failed(String error, int status) {
            String e = safe(error).isBlank() ? "game_relay_failed" : safe(error);
            return new ChatGameRelayResponse(status, "{\"ok\":false,\"error\":" + JsonUtil.quote(e) + "}", e);
        }
    }


    public static final class ProfileRelayResponse {
        public final int status;
        public final String body;
        public final String error;
        private ProfileRelayResponse(int status, String body, String error) { this.status = status; this.body = body == null ? "" : body; this.error = error == null ? "" : error; }
        public static ProfileRelayResponse failed(String error, int status) {
            String e = safe(error).isBlank() ? "profile_relay_failed" : safe(error);
            return new ProfileRelayResponse(status, "{\"ok\":false,\"error\":" + JsonUtil.quote(e) + "}", e);
        }
    }

    public static final class DirectMessageDelivery {
        public final boolean delivered;
        public final String error;
        public final int httpStatus;

        private DirectMessageDelivery(boolean delivered, String error, int httpStatus) {
            this.delivered = delivered;
            this.error = error == null ? "" : error;
            this.httpStatus = httpStatus;
        }

        public static DirectMessageDelivery delivered(int httpStatus) {
            return new DirectMessageDelivery(true, "", httpStatus);
        }

        public static DirectMessageDelivery failed(String error, int httpStatus) {
            return new DirectMessageDelivery(false, error == null || error.isBlank() ? "delivery_failed" : error, httpStatus);
        }
    }

    private static final class ChatGameRequestEnvelope {
        String requestId;
        String originServerId;
        String fromServerId;
        String targetServerId;
        String payloadB64;
        int hop;

        static ChatGameRequestEnvelope create(String originServerId, String targetServerId, String payloadJson) {
            ChatGameRequestEnvelope e = new ChatGameRequestEnvelope();
            e.requestId = "game-" + SecurityUtil.randomToken(12);
            e.originServerId = normalizeId(originServerId);
            e.fromServerId = e.originServerId;
            e.targetServerId = normalizeId(targetServerId);
            e.payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(safe(payloadJson).getBytes(StandardCharsets.UTF_8));
            e.hop = 0;
            return e;
        }

        static ChatGameRequestEnvelope fromMap(Map<String,String> map) {
            ChatGameRequestEnvelope e = new ChatGameRequestEnvelope();
            e.requestId = limit(safe(map.get("requestId")), 180);
            e.originServerId = normalizeId(map.get("originServerId"));
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.targetServerId = normalizeId(map.get("targetServerId"));
            e.payloadB64 = limit(safe(map.get("payloadB64")), MAX_BODY_BYTES);
            try { e.hop = Integer.parseInt(safe(map.get("hop"))); } catch (Exception ex) { e.hop = -1; }
            return e;
        }

        String decodePayload() {
            try { return new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8); } catch (Exception ignored) { return ""; }
        }

        String toJson() {
            return JsonUtil.obj(Map.of("requestId", requestId, "originServerId", originServerId, "fromServerId", fromServerId,
                    "targetServerId", targetServerId, "payloadB64", payloadB64, "hop", hop));
        }

        boolean valid() {
            return requestId.matches("[A-Za-z0-9._:-]{8,180}") && !originServerId.isBlank() && !fromServerId.isBlank()
                    && !targetServerId.isBlank() && !payloadB64.isBlank() && payloadB64.length() <= MAX_BODY_BYTES;
        }

        private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    }

    private static final class ProfileRequestEnvelope {
        String requestId;
        String originServerId;
        String fromServerId;
        String targetServerId;
        String payloadB64;
        int hop;

        static ProfileRequestEnvelope create(String originServerId, String targetServerId, String payloadJson) {
            ProfileRequestEnvelope e = new ProfileRequestEnvelope();
            e.requestId = "profile-" + SecurityUtil.randomToken(12);
            e.originServerId = normalizeId(originServerId);
            e.fromServerId = e.originServerId;
            e.targetServerId = normalizeId(targetServerId);
            e.payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(safe(payloadJson).getBytes(StandardCharsets.UTF_8));
            e.hop = 0;
            return e;
        }

        static ProfileRequestEnvelope fromMap(Map<String,String> map) {
            ProfileRequestEnvelope e = new ProfileRequestEnvelope();
            e.requestId = limit(safe(map.get("requestId")), 180);
            e.originServerId = normalizeId(map.get("originServerId"));
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.targetServerId = normalizeId(map.get("targetServerId"));
            e.payloadB64 = limit(safe(map.get("payloadB64")), MAX_BODY_BYTES);
            try { e.hop = Integer.parseInt(safe(map.get("hop"))); } catch (Exception ex) { e.hop = -1; }
            return e;
        }

        String decodePayload() {
            try { return new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8); } catch (Exception ignored) { return ""; }
        }

        String toJson() {
            return JsonUtil.obj(Map.of("requestId", requestId, "originServerId", originServerId, "fromServerId", fromServerId,
                    "targetServerId", targetServerId, "payloadB64", payloadB64, "hop", hop));
        }

        boolean valid() {
            return requestId.matches("[A-Za-z0-9._:-]{8,180}") && !originServerId.isBlank() && !fromServerId.isBlank()
                    && !targetServerId.isBlank() && !payloadB64.isBlank() && payloadB64.length() <= MAX_BODY_BYTES;
        }

        private static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    }

    private static final class DirectMessageDeleteEnvelope {
        String deleteId;
        String originServerId;
        String fromServerId;
        String targetServerId;
        String senderUuid;
        String messageRelayId;
        int hop;

        static DirectMessageDeleteEnvelope create(String originServerId, String targetServerId, String senderUuid, String messageRelayId) {
            DirectMessageDeleteEnvelope e = new DirectMessageDeleteEnvelope();
            e.deleteId = "dmdelete-" + SecurityUtil.randomToken(12);
            e.originServerId = normalizeId(originServerId);
            e.fromServerId = e.originServerId;
            e.targetServerId = normalizeId(targetServerId);
            e.senderUuid = RemotePlayerRef.normalizePlayerUuid(senderUuid);
            e.messageRelayId = limitDelete(safe(messageRelayId), 180);
            e.hop = 0;
            return e;
        }

        static DirectMessageDeleteEnvelope fromMap(Map<String, String> map) {
            DirectMessageDeleteEnvelope e = new DirectMessageDeleteEnvelope();
            e.deleteId = limitDelete(safe(map.get("deleteId")), 180);
            e.originServerId = normalizeId(map.get("originServerId"));
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.targetServerId = normalizeId(map.get("targetServerId"));
            e.senderUuid = RemotePlayerRef.normalizePlayerUuid(map.get("senderUuid"));
            e.messageRelayId = limitDelete(safe(map.get("messageRelayId")), 180);
            try { e.hop = Integer.parseInt(safe(map.get("hop"))); } catch (Exception ex) { e.hop = -1; }
            return e;
        }

        private static String limitDelete(String value, int max) {
            String text = safe(value);
            return text.length() <= max ? text : text.substring(0, max);
        }

        boolean valid() {
            return deleteId.matches("[A-Za-z0-9._:-]{8,180}") && !originServerId.isBlank() && !fromServerId.isBlank()
                    && !targetServerId.isBlank() && !senderUuid.isBlank()
                    && messageRelayId.matches("[A-Za-z0-9._:-]{8,180}");
        }

        String toJson() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("deleteId", deleteId);
            m.put("originServerId", originServerId);
            m.put("fromServerId", fromServerId);
            m.put("targetServerId", targetServerId);
            m.put("senderUuid", senderUuid);
            m.put("messageRelayId", messageRelayId);
            m.put("hop", hop);
            return JsonUtil.obj(m);
        }
    }

    private static final class DirectMessageReadEnvelope {
        String receiptId;
        String originServerId;
        String fromServerId;
        String targetServerId;
        String messageRelayId;
        int hop;
        long time;

        static DirectMessageReadEnvelope create(String originServerId, String targetServerId, String messageRelayId) {
            DirectMessageReadEnvelope e = new DirectMessageReadEnvelope();
            e.receiptId = "dmread-" + SecurityUtil.randomToken(12);
            e.originServerId = normalizeId(originServerId);
            e.fromServerId = e.originServerId;
            e.targetServerId = normalizeId(targetServerId);
            e.messageRelayId = limitRead(safe(messageRelayId), 180);
            e.hop = 0;
            e.time = System.currentTimeMillis();
            return e;
        }

        static DirectMessageReadEnvelope fromMap(Map<String, String> map) {
            DirectMessageReadEnvelope e = new DirectMessageReadEnvelope();
            e.receiptId = safe(map.get("receiptId"));
            e.originServerId = normalizeId(map.get("originServerId"));
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.targetServerId = normalizeId(map.get("targetServerId"));
            e.messageRelayId = safe(map.get("messageRelayId"));
            e.hop = parseReadInt(map.get("hop"), -1);
            e.time = parseReadLong(map.get("time"), System.currentTimeMillis());
            return e;
        }

        boolean valid() {
            return receiptId.matches("[A-Za-z0-9._:-]{8,160}")
                    && !originServerId.isBlank() && originServerId.length() <= 64
                    && !fromServerId.isBlank() && fromServerId.length() <= 64
                    && !targetServerId.isBlank() && targetServerId.length() <= 64
                    && !messageRelayId.isBlank() && messageRelayId.length() <= 180;
        }

        String toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("receiptId", receiptId);
            m.put("originServerId", originServerId);
            m.put("fromServerId", fromServerId);
            m.put("targetServerId", targetServerId);
            m.put("messageRelayId", messageRelayId);
            m.put("hop", hop);
            m.put("time", time);
            return JsonUtil.obj(m);
        }

        private static String limitRead(String value, int max) {
            String text = safe(value);
            return text.length() <= max ? text : text.substring(0, max);
        }

        private static int parseReadInt(String raw, int fallback) {
            try { return Integer.parseInt(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }

        private static long parseReadLong(String raw, long fallback) {
            try { return Long.parseLong(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
    }

    private static final class PublicTypingEnvelope {
        String eventId;
        String originServerId;
        String originServerName;
        String fromServerId;
        String scope;
        String source;
        int hop;
        long time;
        long expiresAt;
        String senderUuid;
        String senderUsername;
        String senderDisplayName;
        String clientId;

        static PublicTypingEnvelope create(String originServerId, String originServerName, String source, String senderUuid,
                                           String senderUsername, String senderDisplayName, String clientId, long expiresAt) {
            PublicTypingEnvelope e = new PublicTypingEnvelope();
            e.eventId = "typing-" + SecurityUtil.randomToken(16);
            e.originServerId = normalizeId(originServerId);
            e.originServerName = limitPublicTyping(safe(originServerName), 96);
            e.fromServerId = e.originServerId;
            e.scope = "public";
            e.source = limitPublicTyping(safe(source).toLowerCase(Locale.ROOT), 16);
            e.hop = 0;
            e.time = System.currentTimeMillis();
            e.expiresAt = Math.max(e.time + 1000L, Math.min(e.time + 10_000L, expiresAt));
            e.senderUuid = limitPublicTyping(safe(senderUuid).trim().toLowerCase(Locale.ROOT), 80);
            e.senderUsername = limitPublicTyping(safe(senderUsername), 64);
            e.senderDisplayName = limitPublicTyping(safe(senderDisplayName), 128);
            e.clientId = limitPublicTyping(safe(clientId), 96);
            return e;
        }

        static PublicTypingEnvelope fromMap(Map<String, String> map) {
            PublicTypingEnvelope e = new PublicTypingEnvelope();
            e.eventId = limitPublicTyping(safe(map.get("eventId")), 180);
            e.originServerId = normalizeId(map.get("originServerId"));
            e.originServerName = limitPublicTyping(safe(map.get("originServerName")), 96);
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.scope = limitPublicTyping(safe(map.get("scope")), 16);
            e.source = limitPublicTyping(safe(map.get("source")).toLowerCase(Locale.ROOT), 16);
            e.hop = parsePublicTypingInt(map.get("hop"), -1);
            e.time = parsePublicTypingLong(map.get("time"), 0L);
            e.expiresAt = parsePublicTypingLong(map.get("expiresAt"), 0L);
            e.senderUuid = limitPublicTyping(safe(map.get("senderUuid")).trim().toLowerCase(Locale.ROOT), 80);
            e.senderUsername = limitPublicTyping(safe(map.get("senderUsername")), 64);
            e.senderDisplayName = limitPublicTyping(safe(map.get("senderDisplayName")), 128);
            e.clientId = limitPublicTyping(safe(map.get("clientId")), 96);
            return e;
        }

        boolean valid() {
            long now = System.currentTimeMillis();
            return eventId.matches("[A-Za-z0-9._:-]{8,180}")
                    && !originServerId.isBlank() && originServerId.length() <= 64
                    && !fromServerId.isBlank() && fromServerId.length() <= 64
                    && "public".equals(scope)
                    && ("web".equals(source) || "guest".equals(source))
                    && !senderUsername.isBlank() && !senderDisplayName.isBlank()
                    && clientId.matches("[A-Za-z0-9._:-]{8,96}")
                    && expiresAt >= time && expiresAt <= now + 15_000L
                    && time >= now - 60_000L && time <= now + 60_000L;
        }

        RelayPublicTyping toTyping() {
            return new RelayPublicTyping(eventId, originServerId, originServerName, source, senderUuid, senderUsername,
                    senderDisplayName, clientId, expiresAt);
        }

        String toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", eventId);
            m.put("originServerId", originServerId);
            m.put("originServerName", originServerName);
            m.put("fromServerId", fromServerId);
            m.put("scope", scope);
            m.put("source", source);
            m.put("hop", hop);
            m.put("time", time);
            m.put("expiresAt", expiresAt);
            m.put("senderUuid", senderUuid);
            m.put("senderUsername", senderUsername);
            m.put("senderDisplayName", senderDisplayName);
            m.put("clientId", clientId);
            return JsonUtil.obj(m);
        }

        private static String limitPublicTyping(String value, int max) {
            String text = safe(value);
            return text.length() <= max ? text : text.substring(0, max);
        }
        private static int parsePublicTypingInt(String raw, int fallback) {
            try { return Integer.parseInt(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
        private static long parsePublicTypingLong(String raw, long fallback) {
            try { return Long.parseLong(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
    }

    private static final class DirectTypingEnvelope {
        String eventId;
        String originServerId;
        String originServerName;
        String fromServerId;
        String targetServerId;
        int hop;
        long time;
        long expiresAt;
        String senderUuid;
        String senderUsername;
        String senderDisplayName;
        String targetUuid;

        static DirectTypingEnvelope create(String originServerId, String originServerName, String targetServerId,
                                           String senderUuid, String senderUsername, String senderDisplayName,
                                           String targetUuid, long expiresAt) {
            DirectTypingEnvelope e = new DirectTypingEnvelope();
            e.eventId = "typing-" + SecurityUtil.randomToken(16);
            e.originServerId = normalizeId(originServerId);
            e.originServerName = limitTyping(safe(originServerName), 96);
            e.fromServerId = e.originServerId;
            e.targetServerId = normalizeId(targetServerId);
            e.hop = 0;
            e.time = System.currentTimeMillis();
            e.expiresAt = Math.max(e.time + 1000L, Math.min(e.time + 10_000L, expiresAt));
            e.senderUuid = limitTyping(safe(senderUuid).trim().toLowerCase(Locale.ROOT), 80);
            e.senderUsername = limitTyping(safe(senderUsername), 64);
            e.senderDisplayName = limitTyping(safe(senderDisplayName), 128);
            e.targetUuid = limitTyping(safe(targetUuid).trim().toLowerCase(Locale.ROOT), 80);
            return e;
        }

        static DirectTypingEnvelope fromMap(Map<String, String> map) {
            DirectTypingEnvelope e = new DirectTypingEnvelope();
            e.eventId = limitTyping(safe(map.get("eventId")), 180);
            e.originServerId = normalizeId(map.get("originServerId"));
            e.originServerName = limitTyping(safe(map.get("originServerName")), 96);
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.targetServerId = normalizeId(map.get("targetServerId"));
            e.hop = parseTypingInt(map.get("hop"), -1);
            e.time = parseTypingLong(map.get("time"), 0L);
            e.expiresAt = parseTypingLong(map.get("expiresAt"), 0L);
            e.senderUuid = limitTyping(safe(map.get("senderUuid")).trim().toLowerCase(Locale.ROOT), 80);
            e.senderUsername = limitTyping(safe(map.get("senderUsername")), 64);
            e.senderDisplayName = limitTyping(safe(map.get("senderDisplayName")), 128);
            e.targetUuid = limitTyping(safe(map.get("targetUuid")).trim().toLowerCase(Locale.ROOT), 80);
            return e;
        }

        boolean valid() {
            long now = System.currentTimeMillis();
            return eventId.matches("[A-Za-z0-9._:-]{8,180}")
                    && !originServerId.isBlank() && originServerId.length() <= 64
                    && !fromServerId.isBlank() && fromServerId.length() <= 64
                    && !targetServerId.isBlank() && targetServerId.length() <= 64
                    && !senderUuid.isBlank() && !targetUuid.isBlank()
                    && expiresAt >= time && expiresAt <= now + 15_000L
                    && time >= now - 60_000L && time <= now + 60_000L;
        }

        RelayDirectTyping toTyping() {
            return new RelayDirectTyping(eventId, originServerId, originServerName, senderUuid, senderUsername, senderDisplayName, targetUuid, expiresAt);
        }

        String toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", eventId);
            m.put("originServerId", originServerId);
            m.put("originServerName", originServerName);
            m.put("fromServerId", fromServerId);
            m.put("targetServerId", targetServerId);
            m.put("hop", hop);
            m.put("time", time);
            m.put("expiresAt", expiresAt);
            m.put("senderUuid", senderUuid);
            m.put("senderUsername", senderUsername);
            m.put("senderDisplayName", senderDisplayName);
            m.put("targetUuid", targetUuid);
            return JsonUtil.obj(m);
        }

        private static String limitTyping(String value, int max) {
            String text = safe(value);
            return text.length() <= max ? text : text.substring(0, max);
        }
        private static int parseTypingInt(String raw, int fallback) {
            try { return Integer.parseInt(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
        private static long parseTypingLong(String raw, long fallback) {
            try { return Long.parseLong(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
    }

    private static final class DirectMessageEnvelope {
        String relayId;
        String originServerId;
        String originServerName;
        String fromServerId;
        String targetServerId;
        int hop;
        long time;
        String senderUuid;
        String senderUsername;
        String senderDisplayName;
        String targetUuid;
        String targetUsername;
        String targetDisplayName;
        String message;
        String gameMessage;
        String replyToRelayId;
        String replyToSender;
        String replyToPreview;

        static DirectMessageEnvelope create(String relayId, String originServerId, String originServerName, String targetServerId,
                                            String senderUuid, String senderUsername, String senderDisplayName,
                                            String targetUuid, String targetUsername, String targetDisplayName,
                                            String message, String gameMessage,
                                            String replyToRelayId, String replyToSender, String replyToPreview) {
            DirectMessageEnvelope e = new DirectMessageEnvelope();
            e.relayId = safe(relayId).isBlank() ? "dmrelay-" + SecurityUtil.randomToken(16) : limit(safe(relayId), 180);
            e.originServerId = normalizeId(originServerId);
            e.originServerName = safe(originServerName);
            e.fromServerId = e.originServerId;
            e.targetServerId = normalizeId(targetServerId);
            e.hop = 0;
            e.time = System.currentTimeMillis();
            e.senderUuid = limit(safe(senderUuid).trim().toLowerCase(Locale.ROOT), 80);
            e.senderUsername = limit(safe(senderUsername), 64);
            e.senderDisplayName = limit(safe(senderDisplayName), 128);
            e.targetUuid = limit(safe(targetUuid).trim().toLowerCase(Locale.ROOT), 80);
            e.targetUsername = limit(safe(targetUsername), 64);
            e.targetDisplayName = limit(safe(targetDisplayName), 128);
            e.message = limit(safe(message), 16384);
            e.gameMessage = limit(safe(gameMessage), 16384);
            e.replyToRelayId = limit(safe(replyToRelayId).trim(), 180);
            e.replyToSender = limit(safe(replyToSender), 128);
            e.replyToPreview = limit(safe(replyToPreview), 240);
            return e;
        }

        static DirectMessageEnvelope fromMap(Map<String, String> map) {
            DirectMessageEnvelope e = new DirectMessageEnvelope();
            e.relayId = safe(map.get("relayId"));
            e.originServerId = normalizeId(map.get("originServerId"));
            e.originServerName = safe(map.get("originServerName"));
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.targetServerId = normalizeId(map.get("targetServerId"));
            e.hop = parseInt(map.get("hop"), -1);
            e.time = parseLong(map.get("time"), System.currentTimeMillis());
            e.senderUuid = safe(map.get("senderUuid")).trim().toLowerCase(Locale.ROOT);
            e.senderUsername = safe(map.get("senderUsername"));
            e.senderDisplayName = safe(map.get("senderDisplayName"));
            e.targetUuid = safe(map.get("targetUuid")).trim().toLowerCase(Locale.ROOT);
            e.targetUsername = safe(map.get("targetUsername"));
            e.targetDisplayName = safe(map.get("targetDisplayName"));
            e.message = safe(map.get("message"));
            e.gameMessage = safe(map.get("gameMessage"));
            e.replyToRelayId = safe(map.get("replyToRelayId")).trim();
            e.replyToSender = safe(map.get("replyToSender"));
            e.replyToPreview = safe(map.get("replyToPreview"));
            return e;
        }

        boolean valid() {
            return relayId.matches("[A-Za-z0-9._:-]{8,160}")
                    && !originServerId.isBlank() && originServerId.length() <= 64
                    && originServerName.length() <= 96
                    && !fromServerId.isBlank() && fromServerId.length() <= 64
                    && !targetServerId.isBlank() && targetServerId.length() <= 64
                    && !targetServerId.equals(originServerId)
                    && !senderUuid.isBlank() && senderUuid.length() <= 80
                    && senderUsername.length() <= 64
                    && senderDisplayName.length() <= 128
                    && !targetUuid.isBlank() && targetUuid.length() <= 80
                    && targetUsername.length() <= 64
                    && targetDisplayName.length() <= 128
                    && !message.isBlank() && message.length() <= 16384
                    && gameMessage.length() <= 16384
                    && (replyToRelayId.isBlank() || replyToRelayId.matches("[A-Za-z0-9._:-]{8,180}"))
                    && replyToSender.length() <= 128
                    && replyToPreview.length() <= 240;
        }

        String toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("relayId", relayId);
            m.put("originServerId", originServerId);
            m.put("originServerName", originServerName);
            m.put("fromServerId", fromServerId);
            m.put("targetServerId", targetServerId);
            m.put("hop", hop);
            m.put("time", time);
            m.put("senderUuid", senderUuid);
            m.put("senderUsername", senderUsername);
            m.put("senderDisplayName", senderDisplayName);
            m.put("targetUuid", targetUuid);
            m.put("targetUsername", targetUsername);
            m.put("targetDisplayName", targetDisplayName);
            m.put("message", message);
            if (!gameMessage.isBlank()) m.put("gameMessage", gameMessage);
            if (!replyToRelayId.isBlank()) {
                m.put("replyToRelayId", replyToRelayId);
                m.put("replyToSender", replyToSender);
                m.put("replyToPreview", replyToPreview);
            }
            return JsonUtil.obj(m);
        }

        private static String limit(String raw, int max) {
            String value = safe(raw);
            return value.length() <= max ? value : value.substring(0, max);
        }

        private static int parseInt(String raw, int fallback) {
            try { return Integer.parseInt(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }

        private static long parseLong(String raw, long fallback) {
            try { return Long.parseLong(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
    }

    public static final class ReactionRequestResult {
        public final boolean committed;
        public final boolean retryable;
        public final String error;
        public final int status;

        private ReactionRequestResult(boolean committed, boolean retryable, String error, int status) {
            this.committed = committed;
            this.retryable = retryable;
            this.error = safe(error);
            this.status = status;
        }

        static ReactionRequestResult committed(int status) { return new ReactionRequestResult(true, false, "", status); }
        static ReactionRequestResult retryable(String error, int status) { return new ReactionRequestResult(false, true, error, status); }
        static ReactionRequestResult rejected(String error, int status) { return new ReactionRequestResult(false, false, error, status); }
    }

    private static final class ReactionRequestEnvelope {
        String scope;
        String eventId;
        String originServerId;
        String fromServerId;
        String targetServerId;
        String messageRelayId;
        String actorUuid;
        String actorLabel;
        String reaction;
        boolean active;
        int hop;
        long time;

        static ReactionRequestEnvelope create(String scope, String eventId, String originServerId, String targetServerId,
                                              String messageRelayId, String actorUuid, String actorLabel,
                                              String reaction, boolean active) {
            ReactionRequestEnvelope e = new ReactionRequestEnvelope();
            e.scope = normalizeReactionScope(scope);
            e.eventId = safe(eventId).isBlank() ? "react-" + SecurityUtil.randomToken(16) : safe(eventId);
            e.originServerId = normalizeId(originServerId);
            e.fromServerId = e.originServerId;
            e.targetServerId = normalizeId(targetServerId);
            e.messageRelayId = safe(messageRelayId);
            e.actorUuid = safe(actorUuid).toLowerCase(Locale.ROOT);
            e.actorLabel = limitReactionLabel(actorLabel);
            e.reaction = safe(reaction);
            e.active = active;
            e.hop = 0;
            e.time = System.currentTimeMillis();
            return e;
        }

        static ReactionRequestEnvelope fromMap(Map<String,String> map) {
            ReactionRequestEnvelope e = new ReactionRequestEnvelope();
            e.scope = normalizeReactionScope(map.get("scope"));
            e.eventId = safe(map.get("eventId"));
            e.originServerId = normalizeId(map.get("originServerId"));
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.targetServerId = normalizeId(map.get("targetServerId"));
            e.messageRelayId = safe(map.get("messageRelayId"));
            e.actorUuid = safe(map.get("actorUuid")).toLowerCase(Locale.ROOT);
            e.actorLabel = limitReactionLabel(map.get("actorLabel"));
            e.reaction = safe(map.get("reaction"));
            e.active = Boolean.parseBoolean(safe(map.get("active")));
            e.hop = parseReactionInt(map.get("hop"), -1);
            e.time = parseReactionLong(map.get("time"), System.currentTimeMillis());
            return e;
        }

        boolean valid() {
            return ("public".equals(scope) || "dm".equals(scope))
                    && eventId.matches("[A-Za-z0-9._:-]{8,160}")
                    && !originServerId.isBlank() && originServerId.length() <= 64
                    && !fromServerId.isBlank() && fromServerId.length() <= 64
                    && !targetServerId.isBlank() && targetServerId.length() <= 64
                    && messageRelayId.matches("[A-Za-z0-9._:-]{8,160}")
                    && actorUuid.matches("[A-Za-z0-9._:-]{8,96}")
                    && !reaction.isBlank() && reaction.length() <= 240;
        }

        RelayPublicReaction toReactionRequest() {
            return new RelayPublicReaction(eventId, messageRelayId, originServerId, targetServerId, scope,
                    actorUuid, actorLabel, reaction, active, true);
        }

        String toJson() {
            Map<String,Object> m = new LinkedHashMap<>();
            if (!"public".equals(scope)) m.put("scope", scope);
            m.put("eventId", eventId);
            m.put("originServerId", originServerId);
            m.put("fromServerId", fromServerId);
            m.put("targetServerId", targetServerId);
            m.put("messageRelayId", messageRelayId);
            m.put("actorUuid", actorUuid);
            if (!actorLabel.isBlank()) m.put("actorLabel", actorLabel);
            m.put("reaction", reaction);
            m.put("active", active);
            m.put("hop", hop);
            m.put("time", time);
            return JsonUtil.obj(m);
        }

        private static String normalizeReactionScope(String raw) {
            String value = safe(raw).trim().toLowerCase(Locale.ROOT);
            return "dm".equals(value) ? "dm" : "public";
        }
        private static String limitReactionLabel(String raw) {
            String value = safe(raw);
            return value.length() <= 96 ? value : value.substring(0, 96);
        }
        private static int parseReactionInt(String raw, int fallback) {
            try { return Integer.parseInt(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
        private static long parseReactionLong(String raw, long fallback) {
            try { return Long.parseLong(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
    }

    private static final class ReactionEnvelope {
        String eventId;
        String messageRelayId;
        String originServerId;
        String fromServerId;
        String actorUuid;
        String actorLabel;
        String reaction;
        boolean active;
        int hop;
        long time;

        static ReactionEnvelope create(String serverId, String eventId, String messageRelayId, String actorUuid, String actorLabel, String reaction, boolean active) {
            ReactionEnvelope e = new ReactionEnvelope();
            e.eventId = safe(eventId).isBlank() ? "react-" + SecurityUtil.randomToken(16) : safe(eventId);
            e.messageRelayId = safe(messageRelayId);
            e.originServerId = normalizeId(serverId);
            e.fromServerId = normalizeId(serverId);
            e.actorUuid = safe(actorUuid).toLowerCase(Locale.ROOT);
            e.actorLabel = limit(safe(actorLabel), 96);
            e.reaction = safe(reaction);
            e.active = active;
            e.hop = 0;
            e.time = System.currentTimeMillis();
            return e;
        }

        static ReactionEnvelope fromMap(Map<String,String> map) {
            ReactionEnvelope e = new ReactionEnvelope();
            e.eventId = safe(map.get("eventId"));
            e.messageRelayId = safe(map.get("messageRelayId"));
            e.originServerId = normalizeId(map.get("originServerId"));
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.actorUuid = safe(map.get("actorUuid")).toLowerCase(Locale.ROOT);
            e.actorLabel = limit(safe(map.get("actorLabel")), 96);
            e.reaction = safe(map.get("reaction"));
            e.active = Boolean.parseBoolean(safe(map.get("active")));
            e.hop = parseInt(map.get("hop"), -1);
            e.time = parseLong(map.get("time"), System.currentTimeMillis());
            return e;
        }

        boolean valid() {
            return eventId.matches("[A-Za-z0-9._:-]{8,160}")
                    && messageRelayId.matches("[A-Za-z0-9._:-]{8,160}")
                    && !originServerId.isBlank() && originServerId.length() <= 64
                    && !fromServerId.isBlank() && fromServerId.length() <= 64
                    && actorUuid.matches("[A-Za-z0-9._:-]{8,96}")
                    && !reaction.isBlank() && reaction.length() <= 240;
        }

        RelayPublicReaction toReaction() {
            return new RelayPublicReaction(eventId, messageRelayId, originServerId, actorUuid, actorLabel, reaction, active);
        }

        String toJson() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("eventId", eventId);
            m.put("messageRelayId", messageRelayId);
            m.put("originServerId", originServerId);
            m.put("fromServerId", fromServerId);
            m.put("actorUuid", actorUuid);
            if (!actorLabel.isBlank()) m.put("actorLabel", actorLabel);
            m.put("reaction", reaction);
            m.put("active", active);
            m.put("hop", hop);
            m.put("time", time);
            return JsonUtil.obj(m);
        }

        private static String limit(String raw, int max) {
            String value = safe(raw);
            return value.length() <= max ? value : value.substring(0, max);
        }

        private static int parseInt(String raw, int fallback) {
            try { return Integer.parseInt(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
        private static long parseLong(String raw, long fallback) {
            try { return Long.parseLong(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
    }

    private static final class RelayEnvelope {
        String relayId;
        String originServerId;
        String originServerName;
        String fromServerId;
        int hop;
        long time;
        String source;
        String sender;
        String realSender;
        String playerUuid;
        String role;
        String message;
        String gameMessage;
        String i18nKey;
        String i18nArgs;
        String replyToId;
        String replyToSender;
        String replyToPreview;

        static RelayEnvelope fromMessage(ChatMessage msg, String serverId, String serverName, int hop) {
            RelayEnvelope e = new RelayEnvelope();
            e.relayId = safe(msg.relayId).isBlank() ? safe(msg.id) : msg.relayId;
            e.originServerId = safe(msg.originServerId).isBlank() ? serverId : msg.originServerId;
            e.originServerName = safe(msg.originServerName).isBlank() ? serverName : msg.originServerName;
            e.fromServerId = serverId;
            e.hop = hop;
            e.time = msg.time;
            e.source = safe(msg.source);
            e.sender = safe(msg.sender);
            e.realSender = safe(msg.realSender);
            e.playerUuid = safe(msg.playerUuid);
            e.role = safe(msg.role);
            e.message = safe(msg.message);
            e.gameMessage = safe(msg.gameMessage);
            e.i18nKey = safe(msg.i18nKey);
            e.i18nArgs = safe(msg.i18nArgs);
            e.replyToId = safe(msg.replyToId);
            e.replyToSender = safe(msg.replyToSender);
            e.replyToPreview = safe(msg.replyToPreview);
            return e;
        }

        static RelayEnvelope fromMap(Map<String, String> map) {
            RelayEnvelope e = new RelayEnvelope();
            e.relayId = safe(map.get("relayId"));
            e.originServerId = normalizeId(map.get("originServerId"));
            e.originServerName = safe(map.get("originServerName"));
            e.fromServerId = normalizeId(map.get("fromServerId"));
            e.hop = parseInt(map.get("hop"), -1);
            e.time = parseLong(map.get("time"), System.currentTimeMillis());
            e.source = safe(map.get("source"));
            e.sender = safe(map.get("sender"));
            e.realSender = safe(map.get("realSender"));
            e.playerUuid = safe(map.get("playerUuid"));
            e.role = safe(map.get("role"));
            e.message = safe(map.get("message"));
            e.gameMessage = safe(map.get("gameMessage"));
            e.i18nKey = safe(map.get("i18nKey"));
            e.i18nArgs = safe(map.get("i18nArgs"));
            e.replyToId = safe(map.get("replyToId"));
            e.replyToSender = safe(map.get("replyToSender"));
            e.replyToPreview = safe(map.get("replyToPreview"));
            return e;
        }

        boolean valid() {
            return relayId.matches("[A-Za-z0-9._:-]{8,160}")
                    && !originServerId.isBlank() && originServerId.length() <= 64
                    && originServerName.length() <= 96
                    && !source.isBlank() && source.length() <= 32
                    && !sender.isBlank() && sender.length() <= 96
                    && realSender.length() <= 96
                    && playerUuid.length() <= 64
                    && role.length() <= 32
                    && !message.isBlank() && message.length() <= 16384
                    && gameMessage.length() <= 16384
                    && i18nKey.length() <= 128 && i18nArgs.length() <= 8192
                    && replyToId.length() <= 160
                    && replyToSender.length() <= 96
                    && replyToPreview.length() <= 16384;
        }

        ChatMessage toMessage() {
            ChatMessage msg = new ChatMessage(time, source, sender, role.isBlank() ? "USER" : role, message);
            msg.id = relayId;
            msg.relayId = relayId;
            msg.originServerId = originServerId;
            msg.originServerName = originServerName;
            msg.relayHop = hop;
            msg.realSender = realSender;
            msg.playerUuid = playerUuid;
            msg.gameMessage = gameMessage;
            msg.i18nKey = i18nKey;
            msg.i18nArgs = i18nArgs;
            msg.replyToId = replyToId;
            msg.replyToSender = replyToSender;
            msg.replyToPreview = replyToPreview;
            return msg;
        }

        String toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("relayId", relayId);
            m.put("originServerId", originServerId);
            m.put("originServerName", originServerName);
            m.put("fromServerId", fromServerId);
            m.put("hop", hop);
            m.put("time", time);
            m.put("source", source);
            m.put("sender", sender);
            m.put("realSender", realSender);
            m.put("playerUuid", playerUuid);
            m.put("role", role);
            m.put("message", message);
            if (!gameMessage.isBlank()) m.put("gameMessage", gameMessage);
            m.put("i18nKey", i18nKey);
            m.put("i18nArgs", i18nArgs);
            m.put("replyToId", replyToId);
            m.put("replyToSender", replyToSender);
            m.put("replyToPreview", replyToPreview);
            return JsonUtil.obj(m);
        }

        private static int parseInt(String raw, int fallback) {
            try { return Integer.parseInt(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }

        private static long parseLong(String raw, long fallback) {
            try { return Long.parseLong(safe(raw).trim()); } catch (NumberFormatException ex) { return fallback; }
        }
    }
}
