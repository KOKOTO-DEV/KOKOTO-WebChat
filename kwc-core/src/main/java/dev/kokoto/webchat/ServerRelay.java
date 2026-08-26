package dev.kokoto.webchat;

import com.sun.net.httpserver.HttpExchange;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerRelay implements AutoCloseable {
    private static final String HEADER_VERSION = "X-BMWC-Relay-Version";
    private static final String HEADER_FROM = "X-BMWC-Relay-From";
    private static final String HEADER_TIMESTAMP = "X-BMWC-Relay-Timestamp";
    private static final String HEADER_SIGNATURE = "X-BMWC-Relay-Signature";
    private static final String PROTOCOL_VERSION = "1";
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int UNKNOWN_PEER_BACKOFF_THRESHOLD = 3;
    private static final int TRANSPORT_BACKOFF_THRESHOLD = 3;
    private static final long OUTBOUND_BACKOFF_MILLIS = 60_000L;
    private static final long OUTBOUND_DISABLED_UNTIL_RELOAD = Long.MAX_VALUE;

    private final RelayHost host;
    private final RelaySettings config;
    private final String serverId;
    private final String serverName;
    private final Map<String, RelaySettings.Peer> peersById = new LinkedHashMap<>();
    private final List<String> peerDiagnostics = new ArrayList<>();
    private final int configuredPeerCount;
    private final ConcurrentHashMap<String, Long> seenRelayIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> deliveredRelayIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> unknownPeerFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> transportFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> outboundBackoffUntil = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> outboundRecoveryInFlight = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final boolean active;

    public ServerRelay(RelayHost host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
        this.config = host.relaySettings();
        String defaultServerName = safe(host.defaultServerName());
        String configuredId = config == null ? "" : safe(config.serverId);
        this.serverId = configuredId.isBlank() ? fallbackServerId(defaultServerName) : configuredId;
        String configuredName = config == null ? "" : safe(config.serverName);
        this.serverName = configuredName.isBlank() ? defaultServerName : configuredName;
        int configuredPeers = 0;
        if (config != null && config.peers != null) {
            int index = 0;
            for (RelaySettings.Peer peer : config.peers) {
                index++;
                configuredPeers++;
                if (peer == null) {
                    peerDiagnostics.add("peer #" + index + " is empty and was ignored");
                    continue;
                }
                if (!peer.enabled) continue;
                if (peer.id.isBlank()) {
                    peerDiagnostics.add("peer #" + index + " has no id and was ignored");
                    continue;
                }
                if (peer.url.isBlank()) {
                    peerDiagnostics.add("peer " + peer.id + " has no URL and was ignored");
                    continue;
                }
                if (peer.id.equals(serverId)) {
                    peerDiagnostics.add("peer " + peer.id + " matches this server-id and was ignored");
                    continue;
                }
                if (peersById.containsKey(peer.id)) {
                    peerDiagnostics.add("duplicate peer id " + peer.id + " was ignored; peer ids must be unique");
                    continue;
                }
                if (secretFor(peer).isBlank()) {
                    peerDiagnostics.add("peer " + peer.id + " has no per-peer or shared secret and was ignored");
                    continue;
                }
                try {
                    relayUri(peer.url);
                } catch (IllegalArgumentException ex) {
                    peerDiagnostics.add("peer " + peer.id + " has an invalid URL and was ignored: " + ex.getMessage());
                    continue;
                }
                peersById.put(peer.id, peer);
            }
        }
        this.configuredPeerCount = configuredPeers;
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "KOKOTO WebChat-ServerRelay");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config == null ? 5 : config.connectTimeoutSeconds))
                .executor(executor)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.active = config != null && config.enabled && !serverId.isBlank() && !peersById.isEmpty();
    }

    public void start() {
        if (config == null || !config.enabled) return;
        if (serverId.isBlank()) {
            host.warn("Server relay is enabled, but server-relay.server-id is empty and no usable fallback could be created.");
            return;
        }
        for (String diagnostic : peerDiagnostics) {
            host.warn("Server relay config: " + diagnostic + ".");
        }
        if (peersById.isEmpty()) {
            host.warn("Server relay is enabled, but no usable peers remain after validation (configured="
                    + configuredPeerCount + ").");
            return;
        }
        host.info("Server relay enabled. serverId=" + serverId
                + ", activePeers=" + peersById.size() + "/" + configuredPeerCount
                + ", forwardReceivedPublicChat=" + config.forwardReceivedPublicChat
                + " [" + String.join(", ", peersById.keySet()) + "]");
    }

    public boolean isEnabled() {
        return active && !closed.get();
    }

    public String serverId() {
        return serverId;
    }

    public String serverName() {
        return serverName;
    }

    public boolean canRouteDirectMessage(String targetServerId) {
        String target = normalizeId(targetServerId);
        if (!isEnabled() || target.isBlank() || target.equals(serverId)) return false;

        RelaySettings.Peer direct = peersById.get(target);
        if (direct != null && !isOutboundBackedOff(direct.id) && !secretFor(direct).isBlank()) return true;

        // A spoke/chain can safely forward a private message only when there is
        // exactly one authenticated next hop. With two or more possible peers the
        // destination route is ambiguous, so reject before the sender-side thread
        // records a message that cannot be routed.
        int usableNextHops = 0;
        for (RelaySettings.Peer peer : peersById.values()) {
            if (peer == null || isOutboundBackedOff(peer.id) || secretFor(peer).isBlank()) continue;
            usableNextHops++;
            if (usableNextHops > 1) return false;
        }
        return usableNextHops == 1;
    }

    public String createDirectMessageRelayId() {
        return "dmrelay-" + SecurityUtil.randomToken(16);
    }

    public CompletableFuture<DirectMessageDelivery> publishDirectMessage(
            String relayId,
            String senderUuid, String senderUsername, String senderDisplayName,
            String targetServerId, String targetUuid, String targetUsername,
            String targetDisplayName, String message, String gameMessage) {
        String target = normalizeId(targetServerId);
        if (!canRouteDirectMessage(target)) {
            return CompletableFuture.completedFuture(DirectMessageDelivery.failed("remote_server_unavailable", 503));
        }
        DirectMessageEnvelope envelope = DirectMessageEnvelope.create(
                relayId, serverId, serverName, target,
                senderUuid, senderUsername, senderDisplayName,
                targetUuid, targetUsername, targetDisplayName, message, gameMessage);
        if (!envelope.valid()) {
            return CompletableFuture.completedFuture(DirectMessageDelivery.failed("invalid_envelope", 400));
        }
        markSeen(envelope.relayId);
        CompletableFuture<DirectMessageDelivery> future = sendDirectToPeers(envelope, "");
        future.whenComplete((delivery, error) -> {
            if (error != null || delivery == null || !delivery.delivered) {
                seenRelayIds.remove(envelope.relayId);
            } else {
                markDelivered(envelope.relayId);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> publishDirectMessageRead(String targetServerId, String messageRelayId) {
        String target = normalizeId(targetServerId);
        String relayMessageId = safe(messageRelayId).trim();
        if (!canRouteDirectMessage(target) || relayMessageId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        DirectMessageReadEnvelope envelope = DirectMessageReadEnvelope.create(
                serverId, target, relayMessageId);
        if (!envelope.valid()) return CompletableFuture.completedFuture(false);
        return sendDirectReadWithRetry(envelope, 0);
    }

    private CompletableFuture<Boolean> sendDirectReadWithRetry(DirectMessageReadEnvelope envelope, int attempt) {
        return sendDirectReadToPeers(envelope, "").thenCompose(ok -> {
            if (ok || attempt >= 2 || closed.get()) return CompletableFuture.completedFuture(ok);
            long delayMs = attempt == 0 ? 500L : 1500L;
            return CompletableFuture
                    .supplyAsync(() -> Boolean.TRUE, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS))
                    .thenCompose(ignored -> sendDirectReadWithRetry(envelope, attempt + 1));
        });
    }

    public boolean shouldRelay(ChatMessage msg) {
        if (!isEnabled() || msg == null) return false;
        String source = safe(msg.source).toLowerCase(Locale.ROOT);
        if (source.equals("game")) return config.gameChat;
        if (source.equals("web")) return config.webChat;
        if (source.equals("guest")) return config.guestChat;
        if (source.equals("discord")) return config.discordChat;
        return config.systemEvents && (source.equals("event") || source.equals("system") || source.equals("server"));
    }

    public void prepareLocal(ChatMessage msg) {
        if (!isEnabled() || msg == null) return;

        // Origin metadata is also presentation metadata. Stamp it on every local
        // message while server relay is enabled, even when that source category is
        // not forwarded to peers, so web/Discord views can always identify the
        // originating server consistently.
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
        sendToPeers(envelope, "");
    }

    public void handleIncoming(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        if (!isEnabled()) {
            sendJson(exchange, 404, "{\"ok\":false,\"error\":\"relay_disabled\"}");
            return;
        }
        String protocol = header(exchange, HEADER_VERSION);
        String fromId = normalizeId(header(exchange, HEADER_FROM));
        String timestampText = header(exchange, HEADER_TIMESTAMP);
        String signature = header(exchange, HEADER_SIGNATURE).toLowerCase(Locale.ROOT);
        RelaySettings.Peer peer = peersById.get(fromId);
        if (!PROTOCOL_VERSION.equals(protocol)) {
            sendJson(exchange, 426, "{\"ok\":false,\"error\":\"unsupported_protocol\"}");
            return;
        }
        if (peer == null) {
            sendJson(exchange, 403, "{\"ok\":false,\"error\":\"unknown_peer\"}");
            return;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException ex) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"invalid_timestamp\"}");
            return;
        }
        long now = System.currentTimeMillis();
        long allowedSkew = config.maxClockSkewSeconds * 1000L;
        if (timestamp < now - allowedSkew || timestamp > now + allowedSkew) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"expired_request\"}");
            return;
        }
        byte[] bodyBytes;
        try {
            bodyBytes = readLimited(exchange, MAX_BODY_BYTES);
        } catch (IOException ex) {
            sendJson(exchange, 413, "{\"ok\":false,\"error\":\"body_too_large\"}");
            return;
        }
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        String secret = secretFor(peer);
        if (secret.isBlank() || signature.isBlank() || !constantTimeEquals(signature, sign(secret, timestampText + "\n" + body))) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"bad_signature\"}");
            return;
        }
        RelayEnvelope envelope = RelayEnvelope.fromMap(JsonUtil.parseFlatObject(body));
        if (!envelope.valid() || !fromId.equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            sendJson(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}");
            return;
        }
        if (envelope.originServerId.equals(serverId)) {
            markSeen(envelope.relayId);
            sendJson(exchange, 200, "{\"ok\":true,\"duplicate\":true}");
            return;
        }
        if (!markSeen(envelope.relayId)) {
            sendJson(exchange, 200, "{\"ok\":true,\"duplicate\":true}");
            return;
        }
        if (host.hasPublicMessage(envelope.relayId)) {
            sendJson(exchange, 200, "{\"ok\":true,\"duplicate\":true}");
            return;
        }
        ChatMessage msg = envelope.toMessage();
        if (!host.acceptPublicMessage(msg)) {
            sendJson(exchange, 200, "{\"ok\":true,\"duplicate\":true}");
            return;
        }
        sendJson(exchange, 200, "{\"ok\":true}");
        if (config.forwardReceivedPublicChat && envelope.hop + 1 < config.maxHops) {
            envelope.hop++;
            envelope.fromServerId = serverId;
            sendToPeers(envelope, fromId);
        }
    }

    public void handleIncomingDirectMessage(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        if (!isEnabled()) {
            sendJson(exchange, 404, "{\"ok\":false,\"error\":\"relay_disabled\"}");
            return;
        }
        String protocol = header(exchange, HEADER_VERSION);
        String fromId = normalizeId(header(exchange, HEADER_FROM));
        String timestampText = header(exchange, HEADER_TIMESTAMP);
        String signature = header(exchange, HEADER_SIGNATURE).toLowerCase(Locale.ROOT);
        RelaySettings.Peer peer = peersById.get(fromId);
        if (!PROTOCOL_VERSION.equals(protocol)) {
            sendJson(exchange, 426, "{\"ok\":false,\"error\":\"unsupported_protocol\"}");
            return;
        }
        if (peer == null) {
            sendJson(exchange, 403, "{\"ok\":false,\"error\":\"unknown_peer\"}");
            return;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException ex) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"invalid_timestamp\"}");
            return;
        }
        long now = System.currentTimeMillis();
        long allowedSkew = config.maxClockSkewSeconds * 1000L;
        if (timestamp < now - allowedSkew || timestamp > now + allowedSkew) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"expired_request\"}");
            return;
        }
        byte[] bodyBytes;
        try {
            bodyBytes = readLimited(exchange, MAX_BODY_BYTES);
        } catch (IOException ex) {
            sendJson(exchange, 413, "{\"ok\":false,\"error\":\"body_too_large\"}");
            return;
        }
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        String secret = secretFor(peer);
        if (secret.isBlank() || signature.isBlank() || !constantTimeEquals(signature, sign(secret, timestampText + "\n" + body))) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"bad_signature\"}");
            return;
        }
        DirectMessageEnvelope envelope = DirectMessageEnvelope.fromMap(JsonUtil.parseFlatObject(body));
        if (!envelope.valid() || !fromId.equals(envelope.fromServerId)
                || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            sendJson(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}");
            return;
        }
        if (envelope.originServerId.equals(serverId)) {
            sendJson(exchange, 409, "{\"ok\":false,\"error\":\"relay_loop\"}");
            return;
        }

        if (envelope.targetServerId.equals(serverId)) {
            if (host.hasDirectRelayId(envelope.relayId) || isDelivered(envelope.relayId)) {
                markSeen(envelope.relayId);
                markDelivered(envelope.relayId);
                sendJson(exchange, 200, "{\"ok\":true,\"delivered\":true,\"duplicate\":true}");
                return;
            }
            if (!markSeen(envelope.relayId)) {
                sendJson(exchange, 409, "{\"ok\":false,\"error\":\"delivery_in_progress\"}");
                return;
            }
            boolean accepted = host.acceptDirectMessage(new RelayDirectMessage(
                    envelope.relayId,
                    envelope.originServerId, envelope.originServerName,
                    envelope.senderUuid, envelope.senderUsername, envelope.senderDisplayName,
                    envelope.targetUuid, envelope.targetUsername, envelope.targetDisplayName,
                    envelope.message, envelope.gameMessage));
            if (!accepted) {
                seenRelayIds.remove(envelope.relayId);
                sendJson(exchange, 404, "{\"ok\":false,\"error\":\"dm_target_unavailable\"}");
                return;
            }
            markDelivered(envelope.relayId);
            sendJson(exchange, 200, "{\"ok\":true,\"delivered\":true}");
            return;
        }

        if (isDelivered(envelope.relayId)) {
            sendJson(exchange, 200, "{\"ok\":true,\"delivered\":true,\"duplicate\":true}");
            return;
        }
        if (!markSeen(envelope.relayId)) {
            sendJson(exchange, 409, "{\"ok\":false,\"error\":\"relay_in_progress\"}");
            return;
        }
        if (envelope.hop + 1 >= config.maxHops) {
            seenRelayIds.remove(envelope.relayId);
            sendJson(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}");
            return;
        }
        envelope.hop++;
        envelope.fromServerId = serverId;
        DirectMessageDelivery delivery;
        try {
            delivery = sendDirectToPeers(envelope, fromId)
                    .get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS);
        } catch (Exception ex) {
            seenRelayIds.remove(envelope.relayId);
            sendJson(exchange, 504, "{\"ok\":false,\"error\":\"dm_forward_timeout\"}");
            return;
        }
        if (delivery == null || !delivery.delivered) {
            seenRelayIds.remove(envelope.relayId);
            String error = delivery == null ? "dm_route_unavailable" : delivery.error;
            int status = delivery == null || delivery.httpStatus < 400 ? 502 : delivery.httpStatus;
            sendJson(exchange, status, "{\"ok\":false,\"error\":" + JsonUtil.quote(error) + "}");
            return;
        }
        markDelivered(envelope.relayId);
        sendJson(exchange, 200, "{\"ok\":true,\"delivered\":true,\"forwarded\":true}");
    }

    public void handleIncomingDirectMessageRead(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        if (!isEnabled()) {
            sendJson(exchange, 404, "{\"ok\":false,\"error\":\"relay_disabled\"}");
            return;
        }
        String protocol = header(exchange, HEADER_VERSION);
        String fromId = normalizeId(header(exchange, HEADER_FROM));
        String timestampText = header(exchange, HEADER_TIMESTAMP);
        String signature = header(exchange, HEADER_SIGNATURE).toLowerCase(Locale.ROOT);
        RelaySettings.Peer peer = peersById.get(fromId);
        if (!PROTOCOL_VERSION.equals(protocol)) {
            sendJson(exchange, 426, "{\"ok\":false,\"error\":\"unsupported_protocol\"}");
            return;
        }
        if (peer == null) {
            sendJson(exchange, 403, "{\"ok\":false,\"error\":\"unknown_peer\"}");
            return;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException ex) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"invalid_timestamp\"}");
            return;
        }
        long now = System.currentTimeMillis();
        long allowedSkew = config.maxClockSkewSeconds * 1000L;
        if (timestamp < now - allowedSkew || timestamp > now + allowedSkew) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"expired_request\"}");
            return;
        }
        byte[] bodyBytes;
        try {
            bodyBytes = readLimited(exchange, MAX_BODY_BYTES);
        } catch (IOException ex) {
            sendJson(exchange, 413, "{\"ok\":false,\"error\":\"body_too_large\"}");
            return;
        }
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        String secret = secretFor(peer);
        if (secret.isBlank() || signature.isBlank() || !constantTimeEquals(signature, sign(secret, timestampText + "\n" + body))) {
            sendJson(exchange, 401, "{\"ok\":false,\"error\":\"bad_signature\"}");
            return;
        }
        DirectMessageReadEnvelope envelope = DirectMessageReadEnvelope.fromMap(JsonUtil.parseFlatObject(body));
        if (!envelope.valid() || !fromId.equals(envelope.fromServerId)
                || envelope.hop < 0 || envelope.hop >= config.maxHops) {
            sendJson(exchange, 400, "{\"ok\":false,\"error\":\"invalid_envelope\"}");
            return;
        }
        if (envelope.targetServerId.equals(serverId)) {
            RelayReadApplyResult applied = host.applyDirectMessageRead(envelope.messageRelayId);
            if (applied == null || !applied.ok) {
                String error = applied == null || applied.error == null || applied.error.isBlank() ? "message_not_found" : applied.error;
                sendJson(exchange, 404, "{\"ok\":false,\"error\":" + JsonUtil.quote(error) + "}");
                return;
            }
            // Replayed read receipts are intentionally idempotent. Only notify web
            // clients when the origin-side read position actually advanced; otherwise
            // two open remote DM windows could keep refreshing each other indefinitely.
            if (applied.changed) {
                host.publishDirectMessageUpdate(applied.localUserUuid, applied.remoteUserUuid, applied.threadId);
            }
            sendJson(exchange, 200, "{\"ok\":true,\"read\":true,\"changed\":" + applied.changed + "}");
            return;
        }
        if (envelope.hop + 1 >= config.maxHops) {
            sendJson(exchange, 409, "{\"ok\":false,\"error\":\"max_hops\"}");
            return;
        }
        envelope.hop++;
        envelope.fromServerId = serverId;
        boolean forwarded;
        try {
            forwarded = sendDirectReadToPeers(envelope, fromId)
                    .get(Math.max(2, config.requestTimeoutSeconds + 2L), TimeUnit.SECONDS);
        } catch (Exception ex) {
            sendJson(exchange, 504, "{\"ok\":false,\"error\":\"dm_read_forward_timeout\"}");
            return;
        }
        if (!forwarded) {
            sendJson(exchange, 502, "{\"ok\":false,\"error\":\"dm_read_route_unavailable\"}");
            return;
        }
        sendJson(exchange, 200, "{\"ok\":true,\"read\":true,\"forwarded\":true}");
    }

    private CompletableFuture<Boolean> sendDirectReadToPeers(DirectMessageReadEnvelope envelope, String excludePeerId) {
        if (!isEnabled() || envelope == null) return CompletableFuture.completedFuture(false);
        String body = envelope.toJson();
        RelaySettings.Peer direct = peersById.get(envelope.targetServerId);
        if (direct != null && !isOutboundBackedOff(direct.id) && !direct.id.equals(excludePeerId)) {
            String secret = secretFor(direct);
            if (secret.isBlank()) return CompletableFuture.completedFuture(false);
            return sendDirectRead(direct, body, secret);
        }
        RelaySettings.Peer nextHop = null;
        for (RelaySettings.Peer candidate : peersById.values()) {
            if (isOutboundBackedOff(candidate.id) || candidate.id.equals(excludePeerId) || candidate.id.equals(envelope.originServerId)) continue;
            if (secretFor(candidate).isBlank()) continue;
            if (nextHop != null) return CompletableFuture.completedFuture(false);
            nextHop = candidate;
        }
        if (nextHop == null) return CompletableFuture.completedFuture(false);
        return sendDirectRead(nextHop, body, secretFor(nextHop));
    }

    private CompletableFuture<DirectMessageDelivery> sendDirectToPeers(DirectMessageEnvelope envelope, String excludePeerId) {
        if (!isEnabled() || envelope == null) {
            return CompletableFuture.completedFuture(DirectMessageDelivery.failed("relay_disabled", 503));
        }
        String body = envelope.toJson();
        RelaySettings.Peer direct = peersById.get(envelope.targetServerId);
        if (direct != null && !isOutboundBackedOff(direct.id) && !direct.id.equals(excludePeerId)) {
            String secret = secretFor(direct);
            if (secret.isBlank()) return CompletableFuture.completedFuture(DirectMessageDelivery.failed("dm_route_unavailable", 404));
            return sendDirect(direct, body, secret);
        }

        RelaySettings.Peer nextHop = null;
        for (RelaySettings.Peer peer : peersById.values()) {
            if (isOutboundBackedOff(peer.id) || peer.id.equals(excludePeerId) || peer.id.equals(envelope.originServerId)) continue;
            if (secretFor(peer).isBlank()) continue;
            if (nextHop != null) {
                host.warn("DM relay route to " + envelope.targetServerId
                        + " is ambiguous; configure a direct peer or a single hub/next hop.");
                return CompletableFuture.completedFuture(DirectMessageDelivery.failed("dm_route_ambiguous", 409));
            }
            nextHop = peer;
        }
        if (nextHop == null) return CompletableFuture.completedFuture(DirectMessageDelivery.failed("dm_route_unavailable", 404));
        return sendDirect(nextHop, body, secretFor(nextHop));
    }

    private void sendToPeers(RelayEnvelope envelope, String excludePeerId) {
        if (!isEnabled() || envelope == null) return;
        String body = envelope.toJson();
        for (RelaySettings.Peer peer : peersById.values()) {
            if (isOutboundBackedOff(peer.id) || peer.id.equals(excludePeerId) || peer.id.equals(envelope.originServerId)) continue;
            String secret = secretFor(peer);
            if (secret.isBlank()) continue;
            send(peer, body, secret);
        }
    }

    private void send(RelaySettings.Peer peer, String body, String secret) {
        send(peer, body, secret, false);
    }

    private void send(RelaySettings.Peer peer, String body, String secret, boolean directMessage) {
        if (peer == null || !beginOutboundAttempt(peer.id)) return;
        String kind = directMessage ? "DM relay" : "Server relay";
        try {
            String timestamp = Long.toString(System.currentTimeMillis());
            URI endpoint = directMessage ? relayDirectMessageUri(peer.url) : relayUri(peer.url);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header(HEADER_VERSION, PROTOCOL_VERSION)
                    .header(HEADER_FROM, serverId)
                    .header(HEADER_TIMESTAMP, timestamp)
                    .header(HEADER_SIGNATURE, sign(secret, timestamp + "\n" + body))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .whenComplete((response, error) -> {
                        if (closed.get()) return;
                        if (error != null) {
                            recordTransportFailure(peer.id, kind, safe(error.getMessage()));
                        } else {
                            recordTransportReachable(peer.id);
                            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            recordPeerSuccess(peer.id);
                        } else if (isRelayDisabledResponse(response.statusCode(), response.body())) {
                            recordRelayDisabled(peer.id, kind);
                        } else if (isUnknownPeerResponse(response.statusCode(), response.body())) {
                            recordUnknownPeerFailure(peer.id, kind);
                            } else {
                                String detail = compactResponseBody(response.body());
                                String failure = "HTTP " + response.statusCode() + (detail.isBlank() ? "" : " (" + detail + ")");
                                if (!recordBackoffRetryFailureIfNeeded(peer.id, kind, failure)) {
                                    host.warn(kind + " peer " + peer.id + " returned " + failure);
                                }
                            }
                        }
                    });
        } catch (Exception ex) {
            recordTransportFailure(peer.id, kind, safe(ex.getMessage()));
        }
    }

    private CompletableFuture<DirectMessageDelivery> sendDirect(RelaySettings.Peer peer, String body, String secret) {
        if (peer == null || !beginOutboundAttempt(peer.id)) {
            return CompletableFuture.completedFuture(DirectMessageDelivery.failed("relay_backoff", 503));
        }
        try {
            String timestamp = Long.toString(System.currentTimeMillis());
            URI endpoint = relayDirectMessageUri(peer.url);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header(HEADER_VERSION, PROTOCOL_VERSION)
                    .header(HEADER_FROM, serverId)
                    .header(HEADER_TIMESTAMP, timestamp)
                    .header(HEADER_SIGNATURE, sign(secret, timestamp + "\n" + body))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .handle((response, error) -> {
                        if (error != null) {
                            recordTransportFailure(peer.id, "DM relay", safe(error.getMessage()));
                            return DirectMessageDelivery.failed("dm_transport_error", 502);
                        }
                        recordTransportReachable(peer.id);
                        int status = response.statusCode();
                        Map<String, String> responseBody = JsonUtil.parseFlatObject(response.body());
                        if (status >= 200 && status < 300) {
                            recordPeerSuccess(peer.id);
                        } else if (isRelayDisabledResponse(status, response.body())) {
                            recordRelayDisabled(peer.id, "DM relay");
                        } else if (isUnknownPeerResponse(status, response.body())) {
                            recordUnknownPeerFailure(peer.id, "DM relay");
                        } else {
                            String detail = compactResponseBody(response.body());
                            recordBackoffRetryFailureIfNeeded(peer.id, "DM relay", "HTTP " + status
                                    + (detail.isBlank() ? "" : " (" + detail + ")"));
                        }
                        boolean delivered = Boolean.parseBoolean(String.valueOf(responseBody.getOrDefault("delivered", "false")));
                        if (status >= 200 && status < 300 && delivered) {
                            return DirectMessageDelivery.delivered(status);
                        }
                        String errorCode = String.valueOf(responseBody.getOrDefault("error", "")).trim();
                        if (errorCode.isBlank()) {
                            errorCode = status >= 200 && status < 300 ? "delivery_not_confirmed" : "remote_http_" + status;
                        }
                        String detail = compactResponseBody(response.body());
                        if (!"relay_disabled".equalsIgnoreCase(errorCode)) {
                            host.warn("DM relay peer " + peer.id + " did not confirm delivery: HTTP "
                                    + status + (detail.isBlank() ? "" : " (" + detail + ")"));
                        }
                        return DirectMessageDelivery.failed(errorCode, status);
                    });
        } catch (Exception ex) {
            recordTransportFailure(peer.id, "DM relay", safe(ex.getMessage()));
            return CompletableFuture.completedFuture(DirectMessageDelivery.failed("dm_request_error", 500));
        }
    }

    private CompletableFuture<Boolean> sendDirectRead(RelaySettings.Peer peer, String body, String secret) {
        if (peer == null || !beginOutboundAttempt(peer.id)) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            String timestamp = Long.toString(System.currentTimeMillis());
            URI endpoint = relayDirectMessageReadUri(peer.url);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header(HEADER_VERSION, PROTOCOL_VERSION)
                    .header(HEADER_FROM, serverId)
                    .header(HEADER_TIMESTAMP, timestamp)
                    .header(HEADER_SIGNATURE, sign(secret, timestamp + "\n" + body))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .handle((response, error) -> {
                        if (error != null || response == null) {
                            recordTransportFailure(peer.id, "DM read relay", error == null ? "empty response" : safe(error.getMessage()));
                            return false;
                        }
                        recordTransportReachable(peer.id);
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            recordPeerSuccess(peer.id);
                        } else {
                            if (isRelayDisabledResponse(response.statusCode(), response.body())) {
                                recordRelayDisabled(peer.id, "DM read relay");
                            } else if (isUnknownPeerResponse(response.statusCode(), response.body())) {
                                recordUnknownPeerFailure(peer.id, "DM read relay");
                            } else {
                                String detail = compactResponseBody(response.body());
                                recordBackoffRetryFailureIfNeeded(peer.id, "DM read relay", "HTTP " + response.statusCode()
                                        + (detail.isBlank() ? "" : " (" + detail + ")"));
                            }
                            return false;
                        }
                        Map<String, String> parsed = JsonUtil.parseFlatObject(response.body());
                        return Boolean.parseBoolean(String.valueOf(parsed.getOrDefault("read", "false")));
                    });
        } catch (Exception ex) {
            recordTransportFailure(peer.id, "DM read relay", safe(ex.getMessage()));
            return CompletableFuture.completedFuture(false);
        }
    }

    private boolean isOutboundBackedOff(String peerId) {
        if (peerId == null || peerId.isBlank()) return false;
        Long retryAt = outboundBackoffUntil.get(peerId);
        if (retryAt == null) return false;
        if (outboundRecoveryInFlight.containsKey(peerId)) return true;
        return System.currentTimeMillis() < retryAt;
    }

    private boolean beginOutboundAttempt(String peerId) {
        if (peerId == null || peerId.isBlank()) return true;
        Long retryAt = outboundBackoffUntil.get(peerId);
        if (retryAt == null) return true;
        if (System.currentTimeMillis() < retryAt) return false;
        return outboundRecoveryInFlight.putIfAbsent(peerId, Boolean.TRUE) == null;
    }

    private void recordPeerSuccess(String peerId) {
        if (peerId == null || peerId.isBlank()) return;
        Long state = outboundBackoffUntil.get(peerId);
        // relay_disabled is an explicit remote administrative state. A response
        // from another request that was already in flight must not accidentally
        // reactivate the peer; only recreating ServerRelay on reload/restart does.
        if (state != null && state.longValue() == OUTBOUND_DISABLED_UNTIL_RELOAD) {
            outboundRecoveryInFlight.remove(peerId);
            return;
        }
        unknownPeerFailures.remove(peerId);
        transportFailures.remove(peerId);
        outboundBackoffUntil.remove(peerId);
        outboundRecoveryInFlight.remove(peerId);
    }

    private void recordRelayDisabled(String peerId, String kind) {
        if (peerId == null || peerId.isBlank()) return;
        unknownPeerFailures.remove(peerId);
        outboundRecoveryInFlight.remove(peerId);
        Long previous = outboundBackoffUntil.put(peerId, OUTBOUND_DISABLED_UNTIL_RELOAD);
        if (previous == null || previous.longValue() != OUTBOUND_DISABLED_UNTIL_RELOAD) {
            host.warn(kind + " peer " + peerId + " reports relay_disabled. "
                    + "Outbound relay to this peer is suspended until the local server relay is reloaded or restarted. "
                    + "No periodic probe will be sent.");
        }
    }

    private void recordUnknownPeerFailure(String peerId, String kind) {
        if (peerId == null || peerId.isBlank()) return;
        Long state = outboundBackoffUntil.get(peerId);
        if (state != null && state.longValue() == OUTBOUND_DISABLED_UNTIL_RELOAD) {
            outboundRecoveryInFlight.remove(peerId);
            return;
        }
        boolean recoveryAttempt = state != null;
        outboundRecoveryInFlight.remove(peerId);
        if (recoveryAttempt) {
            outboundBackoffUntil.put(peerId, System.currentTimeMillis() + OUTBOUND_BACKOFF_MILLIS);
            host.warn(kind + " peer " + peerId + " still returns HTTP 403 unknown_peer. "
                    + "Messages for this destination will be skipped for 60 seconds; the next actual relay message after that interval will retry automatically.");
            return;
        }
        int failures = unknownPeerFailures.merge(peerId, 1, Integer::sum);
        if (failures >= UNKNOWN_PEER_BACKOFF_THRESHOLD) {
            outboundBackoffUntil.put(peerId, System.currentTimeMillis() + OUTBOUND_BACKOFF_MILLIS);
            host.warn(kind + " peer " + peerId + " returned HTTP 403 unknown_peer "
                    + failures + " times without a successful response. Messages for this destination will be skipped for 60 seconds. "
                    + "After that, the next actual relay message will retry automatically; no periodic probe is sent. "
                    + "Add this server-id to the receiver's server-relay.peers and verify the shared/per-peer secret.");
            return;
        }
        host.warn(kind + " peer " + peerId + " returned HTTP 403 unknown_peer ("
                + failures + "/" + UNKNOWN_PEER_BACKOFF_THRESHOLD + "). "
                + "The receiver does not list this server-id; KWC will retry until the backoff threshold is reached.");
    }

    private void recordTransportReachable(String peerId) {
        if (peerId == null || peerId.isBlank()) return;
        transportFailures.remove(peerId);
    }

    private void recordTransportFailure(String peerId, String kind, String detail) {
        if (peerId == null || peerId.isBlank()) return;
        Long state = outboundBackoffUntil.get(peerId);
        if (state != null && state.longValue() == OUTBOUND_DISABLED_UNTIL_RELOAD) {
            outboundRecoveryInFlight.remove(peerId);
            return;
        }
        boolean recoveryAttempt = state != null;
        outboundRecoveryInFlight.remove(peerId);
        if (recoveryAttempt) {
            outboundBackoffUntil.put(peerId, System.currentTimeMillis() + OUTBOUND_BACKOFF_MILLIS);
            host.warn(kind + " peer " + peerId + " recovery attempt failed"
                    + (detail == null || detail.isBlank() ? "" : ": " + detail)
                    + ". Messages for this destination will be skipped for another 60 seconds; the next actual relay message after that interval will retry.");
            return;
        }
        int failures = transportFailures.merge(peerId, 1, Integer::sum);
        if (failures >= TRANSPORT_BACKOFF_THRESHOLD) {
            outboundBackoffUntil.put(peerId, System.currentTimeMillis() + OUTBOUND_BACKOFF_MILLIS);
            host.warn(kind + " peer " + peerId + " transport failed " + failures
                    + " times without a successful connection"
                    + (detail == null || detail.isBlank() ? "" : ": " + detail)
                    + ". Messages for this destination will be skipped for 60 seconds. "
                    + "After that, the next actual relay message will retry automatically; no periodic probe is sent.");
            return;
        }
        host.warn(kind + " transport failed for peer " + peerId
                + (detail == null || detail.isBlank() ? "" : ": " + detail)
                + " (" + failures + "/" + TRANSPORT_BACKOFF_THRESHOLD + "). "
                + "KWC will retry until the backoff threshold is reached.");
    }

    private boolean recordBackoffRetryFailureIfNeeded(String peerId, String kind, String detail) {
        if (peerId == null || peerId.isBlank()) return false;
        Long state = outboundBackoffUntil.get(peerId);
        if (state == null) return false;
        outboundRecoveryInFlight.remove(peerId);
        if (state.longValue() == OUTBOUND_DISABLED_UNTIL_RELOAD) {
            return true;
        }
        outboundBackoffUntil.put(peerId, System.currentTimeMillis() + OUTBOUND_BACKOFF_MILLIS);
        host.warn(kind + " peer " + peerId + " recovery attempt failed"
                + (detail == null || detail.isBlank() ? "" : ": " + detail)
                + ". Messages for this destination will be skipped for another 60 seconds; the next actual relay message after that interval will retry.");
        return true;
    }

    private static boolean isRelayDisabledResponse(int status, String body) {
        if (status != 404) return false;
        Map<String, String> parsed = JsonUtil.parseFlatObject(body);
        return "relay_disabled".equalsIgnoreCase(String.valueOf(parsed.getOrDefault("error", "")).trim());
    }

    private static boolean isUnknownPeerResponse(int status, String body) {
        if (status != 403) return false;
        Map<String, String> parsed = JsonUtil.parseFlatObject(body);
        return "unknown_peer".equalsIgnoreCase(String.valueOf(parsed.getOrDefault("error", "")).trim());
    }

    private URI relayUri(String configured) {
        return relayEndpointUri(configured, "/relay/receive");
    }

    private URI relayDirectMessageUri(String configured) {
        return relayEndpointUri(configured, "/relay/dm/receive");
    }

    private URI relayDirectMessageReadUri(String configured) {
        return relayEndpointUri(configured, "/relay/dm/read");
    }

    private URI relayEndpointUri(String configured, String endpoint) {
        String url = safe(configured).trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith("/relay/receive")) url = url.substring(0, url.length() - "/relay/receive".length());
        if (url.endsWith("/relay/dm/receive")) url = url.substring(0, url.length() - "/relay/dm/receive".length());
        if (url.endsWith("/relay/dm/read")) url = url.substring(0, url.length() - "/relay/dm/read".length());
        url += endpoint;
        URI uri = URI.create(url);
        String scheme = safe(uri.getScheme()).toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("URL must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL must include a host");
        }
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("URL must not include a query string or fragment");
        }
        return uri;
    }

    private boolean markSeen(String relayId) {
        cleanupSeen();
        long expiry = System.currentTimeMillis() + config.dedupeSeconds * 1000L;
        Long existing = seenRelayIds.putIfAbsent(relayId, expiry);
        if (existing == null) return true;
        if (existing < System.currentTimeMillis()) {
            return seenRelayIds.replace(relayId, existing, expiry);
        }
        return false;
    }

    private void cleanupSeen() {
        if (seenRelayIds.size() < 2048 && deliveredRelayIds.size() < 2048) return;
        long now = System.currentTimeMillis();
        seenRelayIds.entrySet().removeIf(entry -> entry.getValue() < now);
        deliveredRelayIds.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private void markDelivered(String relayId) {
        if (relayId == null || relayId.isBlank()) return;
        long expiry = System.currentTimeMillis() + config.dedupeSeconds * 1000L;
        deliveredRelayIds.put(relayId, expiry);
        seenRelayIds.put(relayId, expiry);
    }

    private boolean isDelivered(String relayId) {
        if (relayId == null || relayId.isBlank()) return false;
        Long expiry = deliveredRelayIds.get(relayId);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {
            deliveredRelayIds.remove(relayId, expiry);
            return false;
        }
        return true;
    }

    private String secretFor(RelaySettings.Peer peer) {
        String perPeer = peer == null ? "" : safe(peer.secret);
        return perPeer.isBlank() ? safe(config.sharedSecret) : perPeer;
    }

    private static byte[] readLimited(HttpExchange exchange, int maxBytes) throws IOException {
        byte[] data = exchange.getRequestBody().readNBytes(maxBytes + 1);
        if (data.length > maxBytes) throw new IOException("relay_body_too_large");
        return data;
    }

    private static String header(HttpExchange exchange, String name) {
        String value = exchange.getRequestHeaders().getFirst(name);
        return value == null ? "" : value.trim();
    }

    private static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }


    private static String compactResponseBody(String value) {
        String body = safe(value).replace('\n', ' ').replace('\r', ' ').trim();
        if (body.length() > 240) body = body.substring(0, 237) + "...";
        return body;
    }

    private static String normalizeId(String raw) {
        return safe(raw).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    private static String fallbackServerId(String raw) {
        String id = normalizeId(raw);
        return id.isBlank() ? "server" : id;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        executor.shutdownNow();
        seenRelayIds.clear();
        deliveredRelayIds.clear();
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

        static DirectMessageEnvelope create(String relayId, String originServerId, String originServerName, String targetServerId,
                                            String senderUuid, String senderUsername, String senderDisplayName,
                                            String targetUuid, String targetUsername, String targetDisplayName,
                                            String message, String gameMessage) {
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
                    && gameMessage.length() <= 16384;
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
                    && replyToPreview.length() <= 512;
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
