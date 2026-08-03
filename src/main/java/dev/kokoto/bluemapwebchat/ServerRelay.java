package dev.kokoto.bluemapwebchat;

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

    private final BlueMapWebChatPlugin plugin;
    private final ConfigValues config;
    private final String serverId;
    private final String serverName;
    private final Map<String, ConfigValues.RelayPeer> peersById = new LinkedHashMap<>();
    private final List<String> peerDiagnostics = new ArrayList<>();
    private final int configuredPeerCount;
    private final ConcurrentHashMap<String, Long> seenRelayIds = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final boolean active;

    public ServerRelay(BlueMapWebChatPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.configValues();
        String configuredId = config == null ? "" : safe(config.serverRelayServerId);
        this.serverId = configuredId.isBlank() ? fallbackServerId(plugin.getServer().getName()) : configuredId;
        String configuredName = config == null ? "" : safe(config.serverRelayServerName);
        this.serverName = configuredName.isBlank() ? plugin.getServer().getName() : configuredName;
        int configuredPeers = 0;
        if (config != null && config.serverRelayPeers != null) {
            int index = 0;
            for (ConfigValues.RelayPeer peer : config.serverRelayPeers) {
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
            Thread t = new Thread(r, "BlueMapWebChat-ServerRelay");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config == null ? 5 : config.serverRelayConnectTimeoutSeconds))
                .executor(executor)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.active = config != null && config.serverRelayEnabled && !serverId.isBlank() && !peersById.isEmpty();
    }

    public void start() {
        if (config == null || !config.serverRelayEnabled) return;
        if (serverId.isBlank()) {
            plugin.getLogger().warning("Server relay is enabled, but server-relay.server-id is empty and no usable fallback could be created.");
            return;
        }
        for (String diagnostic : peerDiagnostics) {
            plugin.getLogger().warning("Server relay config: " + diagnostic + ".");
        }
        if (peersById.isEmpty()) {
            plugin.getLogger().warning("Server relay is enabled, but no usable peers remain after validation (configured="
                    + configuredPeerCount + ").");
            return;
        }
        plugin.getLogger().info("Server relay enabled. serverId=" + serverId
                + ", activePeers=" + peersById.size() + "/" + configuredPeerCount
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

    public boolean shouldRelay(ChatMessage msg) {
        if (!isEnabled() || msg == null) return false;
        String source = safe(msg.source).toLowerCase(Locale.ROOT);
        if (source.equals("game")) return config.serverRelayGameChat;
        if (source.equals("web")) return config.serverRelayWebChat;
        if (source.equals("guest")) return config.serverRelayGuestChat;
        if (source.equals("discord")) return config.serverRelayDiscordChat;
        return config.serverRelaySystemEvents && (source.equals("event") || source.equals("system") || source.equals("server"));
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
        ConfigValues.RelayPeer peer = peersById.get(fromId);
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
        long allowedSkew = config.serverRelayMaxClockSkewSeconds * 1000L;
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
        if (!envelope.valid() || !fromId.equals(envelope.fromServerId) || envelope.hop < 0 || envelope.hop >= config.serverRelayMaxHops) {
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
        WebChatServer web = plugin.webServer();
        if (web == null || web.hasMessageId(envelope.relayId)) {
            sendJson(exchange, 200, "{\"ok\":true,\"duplicate\":true}");
            return;
        }
        ChatMessage msg = envelope.toMessage();
        web.acceptRelayedMessage(msg);
        sendJson(exchange, 200, "{\"ok\":true}");
        if (envelope.hop + 1 < config.serverRelayMaxHops) {
            envelope.hop++;
            envelope.fromServerId = serverId;
            sendToPeers(envelope, fromId);
        }
    }

    private void sendToPeers(RelayEnvelope envelope, String excludePeerId) {
        if (!isEnabled() || envelope == null) return;
        String body = envelope.toJson();
        for (ConfigValues.RelayPeer peer : peersById.values()) {
            if (peer.id.equals(excludePeerId) || peer.id.equals(envelope.originServerId)) continue;
            String secret = secretFor(peer);
            if (secret.isBlank()) continue;
            send(peer, body, secret);
        }
    }

    private void send(ConfigValues.RelayPeer peer, String body, String secret) {
        try {
            String timestamp = Long.toString(System.currentTimeMillis());
            HttpRequest request = HttpRequest.newBuilder(relayUri(peer.url))
                    .timeout(Duration.ofSeconds(config.serverRelayRequestTimeoutSeconds))
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
                            plugin.getLogger().warning("Server relay send failed for peer " + peer.id + ": " + safe(error.getMessage()));
                        } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            String detail = compactResponseBody(response.body());
                            plugin.getLogger().warning("Server relay peer " + peer.id + " returned HTTP "
                                    + response.statusCode() + (detail.isBlank() ? "" : " (" + detail + ")"));
                        }
                    });
        } catch (Exception ex) {
            plugin.getLogger().warning("Server relay request could not be created for peer " + peer.id + ": " + ex.getMessage());
        }
    }

    private URI relayUri(String configured) {
        String url = safe(configured).trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (!url.endsWith("/relay/receive")) url += "/relay/receive";
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
        long expiry = System.currentTimeMillis() + config.serverRelayDedupeSeconds * 1000L;
        Long existing = seenRelayIds.putIfAbsent(relayId, expiry);
        if (existing == null) return true;
        if (existing < System.currentTimeMillis()) {
            return seenRelayIds.replace(relayId, existing, expiry);
        }
        return false;
    }

    private void cleanupSeen() {
        if (seenRelayIds.size() < 2048) return;
        long now = System.currentTimeMillis();
        seenRelayIds.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private String secretFor(ConfigValues.RelayPeer peer) {
        String perPeer = peer == null ? "" : safe(peer.secret);
        return perPeer.isBlank() ? safe(config.serverRelaySharedSecret) : perPeer;
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
