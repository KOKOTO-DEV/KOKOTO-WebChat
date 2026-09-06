package dev.kokoto.webchat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Standards-based Web Push sender for browser/mobile push notifications.
 *  It intentionally uses only JDK classes so the plugin does not need another shaded dependency.
 */
public class WebPushManager {
    public static class Payload {
        public String type = "chat";
        public String title = "";
        public String body = "";
        public String url = "";
        public String tag = "kwc";
        public String senderUuid = "";
        public String replyTargetUuid = "";
        public String systemKind = "";
        public String i18nKey = "";
        public String i18nArgs = "";
        public String targetDeviceId = "";
        // Private-chat delivery metadata used only for server-side suppression.
        // These values are never serialized into the browser push payload.
        public String dmThreadId = "";
        public String groupRoomId = "";
        // Server-resolved @mention recipients. This is delivery metadata only and
        // is never serialized into the browser push payload.
        public Set<String> mentionTargetUuids = Set.of();
    }

    private static class Subscription {
        String endpoint = "";
        String userUuid = "";
        String p256dh = "";
        String auth = "";
        String userAgent = "";
        String deviceId = "";
        boolean notifyNormalChat;
        boolean notifyDm;
        boolean notifyGroupChat;
        boolean notifyMentions;
        boolean notifyReplies;
        boolean notifyReactions;
        boolean notifySystem;
        String notifySystemMode = "all";
        boolean notifyKeywords;
        List<String> keywords = new ArrayList<>();
        String language = "";
        String openUrl = "";
        long updatedAt;
    }



    private static class ActiveView {
        String userUuid = "";
        String deviceId = "";
        String clientId = "";
        String dmThreadId = "";
        String groupRoomId = "";
        long updatedAt;
    }

    /** Snapshot of account-wide active private-conversation views.
     *  Expiry is tracked per target so clients never keep a crashed/stale tab
     *  suppressing browser/in-chat notifications longer than the server TTL.
     */
    public static class ActivePrivateViewSnapshot {
        public final Map<String, Long> dmThreadExpiresAt;
        public final Map<String, Long> groupRoomExpiresAt;

        ActivePrivateViewSnapshot(Map<String, Long> dmThreadExpiresAt, Map<String, Long> groupRoomExpiresAt) {
            this.dmThreadExpiresAt = Collections.unmodifiableMap(new LinkedHashMap<>(dmThreadExpiresAt));
            this.groupRoomExpiresAt = Collections.unmodifiableMap(new LinkedHashMap<>(groupRoomExpiresAt));
        }
    }

    private final WebPushHost host;
    private final Map<String, Subscription> byEndpoint = new ConcurrentHashMap<>();
    private final Base64.Encoder b64uNoPad = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder b64u = Base64.getUrlDecoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> keywordCooldownUntil = new ConcurrentHashMap<>();
    private final Map<String, ActiveView> activeViews = new ConcurrentHashMap<>();
    private static final long KEYWORD_PUSH_COOLDOWN_MILLIS = 60_000L;
    // Any signed-in KWC browser refreshes an active private-conversation view every
    // 4 seconds, independently of whether that browser itself subscribes to Push.
    // blur/hidden/close clears it; the TTL prevents a crashed/closed tab from
    // suppressing the account's legitimate Push for more than a few seconds.
    private static final long ACTIVE_VIEW_TTL_MILLIS = 12_000L;
    private static final int MAX_SUBSCRIPTIONS_PER_USER = 16;
    private volatile KeyPair vapidKeyPair;
    private volatile String vapidPublicKeyBase64 = "";

    public WebPushManager(WebPushHost host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    public void start() {
        loadSubscriptions();
        ensureVapidKeyPair();
    }

    public String vapidPublicKey() {
        ensureVapidKeyPair();
        return vapidPublicKeyBase64 == null ? "" : vapidPublicKeyBase64;
    }

    public boolean available() {
        ConfigValues c = host.config();
        return c != null && c.webPushEnabled && !vapidPublicKey().isBlank();
    }

    public synchronized boolean subscribe(Account account, Map<String, String> body, String userAgent) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) return false;
        String endpoint = clean(body.get("endpoint"), 2048);
        String p256dh = clean(body.get("p256dh"), 512);
        String auth = clean(body.get("auth"), 256);
        if (endpoint.isBlank() || p256dh.isBlank() || auth.isBlank() || !validPushEndpoint(endpoint, false)) return false;
        try {
            byte[] pub = b64u.decode(p256dh);
            byte[] secret = b64u.decode(auth);
            if (pub.length != 65 || pub[0] != 0x04 || secret.length < 16 || secret.length > 64) return false;
        } catch (IllegalArgumentException invalidKey) {
            return false;
        }
        Subscription s = new Subscription();
        s.endpoint = endpoint;
        s.userUuid = account.uuid.trim().toLowerCase(Locale.ROOT);
        s.p256dh = p256dh;
        s.auth = auth;
        s.userAgent = clean(userAgent, 300);
        s.deviceId = cleanDeviceId(body == null ? "" : body.get("deviceId"));
        ConfigValues c = host.config();
        s.notifyNormalChat = (c == null || c.webPushNotifyNormalChat) && readBool(body, "notifyNormalChat", c != null && c.webPushNotifyNormalChat);
        s.notifyDm = (c == null || c.webPushNotifyDm) && readBool(body, "notifyDm", c == null || c.webPushNotifyDm);
        s.notifyGroupChat = (c == null || c.webPushNotifyGroupChat) && readBool(body, "notifyGroupChat", c == null || c.webPushNotifyGroupChat);
        s.notifyMentions = (c == null || c.webPushNotifyMentions) && readBool(body, "notifyMentions", c == null || c.webPushNotifyMentions);
        s.notifyReplies = (c == null || c.webPushNotifyReplies) && readBool(body, "notifyReplies", c == null || c.webPushNotifyReplies);
        s.notifyReactions = (c == null || c.webPushNotifyReactions) && readBool(body, "notifyReactions", s.notifyReplies);
        s.notifySystemMode = readSystemMode(body, "notifySystemMode", readBool(body, "notifySystem", c == null || c.webPushNotifySystem) ? "all" : "off");
        s.notifySystem = (c == null || c.webPushNotifySystem) && !"off".equals(s.notifySystemMode);
        s.notifyKeywords = (c == null || c.webPushNotifyKeywords) && readBool(body, "notifyKeywords", c == null || c.webPushNotifyKeywords);
        s.keywords = normalizeKeywords(body == null ? "" : body.get("keywords"));
        s.language = clean(body == null ? "" : body.get("language"), 40);
        s.openUrl = clean(body == null ? "" : body.get("openUrl"), 2048);
        s.updatedAt = System.currentTimeMillis();
        syncNotificationPreferencesForAccount(s);
        removeSupersededDeviceSubscriptions(s);
        trimUserSubscriptionsBeforeInsert(s.userUuid, endpoint);
        byEndpoint.put(endpoint, s);
        saveSubscriptions();
        return true;
    }

    private void syncNotificationPreferencesForAccount(Subscription source) {
        if (source == null || source.userUuid == null || source.userUuid.isBlank()) return;
        String user = source.userUuid.trim().toLowerCase(Locale.ROOT);
        for (Subscription target : byEndpoint.values()) {
            if (target == null || target.userUuid == null || !user.equals(target.userUuid.trim().toLowerCase(Locale.ROOT))) continue;
            copyNotificationPreferences(source, target);
            target.updatedAt = System.currentTimeMillis();
        }
    }

    public synchronized void applyAccountPreferences(Account account, Map<String,Object> prefs) {
        if (account == null || account.uuid == null || account.uuid.isBlank() || prefs == null) return;
        String user = account.uuid.trim().toLowerCase(Locale.ROOT);
        ConfigValues c = host.config();
        for (Subscription target : byEndpoint.values()) {
            if (target == null || target.userUuid == null || !user.equals(target.userUuid.trim().toLowerCase(Locale.ROOT))) continue;
            target.notifyNormalChat = allowed(c == null || c.webPushNotifyNormalChat, boolPref(prefs, "normalChat", target.notifyNormalChat));
            target.notifyDm = allowed(c == null || c.webPushNotifyDm, boolPref(prefs, "dm", target.notifyDm));
            target.notifyGroupChat = allowed(c == null || c.webPushNotifyGroupChat, boolPref(prefs, "groupChat", target.notifyGroupChat));
            target.notifyMentions = allowed(c == null || c.webPushNotifyMentions, boolPref(prefs, "mentions", target.notifyMentions));
            target.notifyReplies = allowed(c == null || c.webPushNotifyReplies, boolPref(prefs, "replies", target.notifyReplies));
            target.notifyReactions = allowed(c == null || c.webPushNotifyReactions, boolPref(prefs, "reactions", target.notifyReplies));
            String mode = String.valueOf(prefs.getOrDefault("systemMode", target.notifySystemMode)).trim().toLowerCase(Locale.ROOT);
            if (!Set.of("all", "join-leave", "off").contains(mode)) mode = "all";
            if (c != null && !c.webPushNotifySystem) mode = "off";
            target.notifySystemMode = mode;
            target.notifySystem = !"off".equals(mode);
            target.notifyKeywords = allowed(c == null || c.webPushNotifyKeywords, boolPref(prefs, "keywords", target.notifyKeywords));
            target.keywords = normalizeKeywords(String.valueOf(prefs.getOrDefault("keywordText", "")));
            target.updatedAt = System.currentTimeMillis();
        }
        saveSubscriptions();
    }

    private static boolean allowed(boolean serverAllows, boolean requested) { return serverAllows && requested; }
    private static boolean boolPref(Map<String,Object> prefs, String key, boolean fallback) {
        Object value = prefs.get(key);
        if (value instanceof Boolean b) return b;
        if (value == null) return fallback;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void copyNotificationPreferences(Subscription source, Subscription target) {
        if (source == null || target == null) return;
        target.notifyNormalChat = source.notifyNormalChat;
        target.notifyDm = source.notifyDm;
        target.notifyGroupChat = source.notifyGroupChat;
        target.notifyMentions = source.notifyMentions;
        target.notifyReplies = source.notifyReplies;
        target.notifyReactions = source.notifyReactions;
        target.notifySystem = source.notifySystem;
        target.notifySystemMode = source.notifySystemMode;
        target.notifyKeywords = source.notifyKeywords;
        target.keywords = source.keywords == null ? new ArrayList<>() : new ArrayList<>(source.keywords);
    }

    private void removeSupersededDeviceSubscriptions(Subscription incoming) {
        if (incoming == null || incoming.userUuid == null || incoming.userUuid.isBlank() || incoming.deviceId == null || incoming.deviceId.isBlank()) return;
        String user = clean(incoming.userUuid, 80).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Subscription> entry : new ArrayList<>(byEndpoint.entrySet())) {
            Subscription candidate = entry.getValue();
            if (candidate == null || !user.equals(clean(candidate.userUuid, 80).toLowerCase(Locale.ROOT))) continue;
            boolean sameDevice = incoming.deviceId.equals(candidate.deviceId);
            boolean legacyWithoutDeviceId = candidate.deviceId == null || candidate.deviceId.isBlank();
            if ((sameDevice || legacyWithoutDeviceId) && !incoming.endpoint.equals(entry.getKey())) {
                byEndpoint.remove(entry.getKey(), candidate);
            }
        }
    }

    private void trimUserSubscriptionsBeforeInsert(String userUuid, String incomingEndpoint) {
        String user = clean(userUuid, 80).toLowerCase(Locale.ROOT);
        if (user.isBlank() || byEndpoint.containsKey(incomingEndpoint)) return;
        List<Subscription> sameUser = new ArrayList<>();
        for (Subscription candidate : byEndpoint.values()) {
            if (candidate != null && user.equals(clean(candidate.userUuid, 80).toLowerCase(Locale.ROOT))) sameUser.add(candidate);
        }
        sameUser.sort(java.util.Comparator.comparingLong(v -> v.updatedAt));
        int remove = Math.max(0, sameUser.size() - MAX_SUBSCRIPTIONS_PER_USER + 1);
        for (int i = 0; i < remove; i++) {
            Subscription old = sameUser.get(i);
            if (old != null && old.endpoint != null) byEndpoint.remove(old.endpoint, old);
        }
    }

    private boolean validPushEndpoint(String endpoint, boolean resolveHost) {
        try {
            URI uri = URI.create(clean(endpoint, 2048));
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            if (uri.getUserInfo() != null || uri.getFragment() != null) return false;
            String hostName = uri.getHost();
            if (hostName == null || hostName.isBlank()) return false;
            int port = uri.getPort();
            if (port != -1 && port != 443) return false;
            if (!resolveHost) {
                // Reject literal private/loopback IPs immediately. Hostnames are
                // resolved again immediately before every outbound delivery.
                if (looksLikeIpLiteral(hostName)) {
                    InetAddress address = InetAddress.getByName(hostName);
                    return isPublicPushAddress(address);
                }
                return true;
            }
            InetAddress[] addresses = InetAddress.getAllByName(hostName);
            if (addresses.length == 0) return false;
            for (InetAddress address : addresses) if (!isPublicPushAddress(address)) return false;
            return true;
        } catch (Exception invalid) {
            return false;
        }
    }

    private boolean looksLikeIpLiteral(String hostName) {
        String h = String.valueOf(hostName == null ? "" : hostName).trim();
        return h.indexOf(':') >= 0 || h.matches("[0-9.]+");
    }

    private boolean isPublicPushAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] raw = address.getAddress();
        if (address instanceof Inet4Address && raw.length == 4) {
            int a = raw[0] & 0xff, b = raw[1] & 0xff;
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
            if (a == 100 && b >= 64 && b <= 127) return false; // carrier-grade NAT
            if (a == 169 && b == 254) return false;
            if (a == 172 && b >= 16 && b <= 31) return false;
            if (a == 192 && b == 168) return false;
            if (a == 198 && (b == 18 || b == 19)) return false; // benchmark range
        } else if (address instanceof Inet6Address && raw.length == 16) {
            int first = raw[0] & 0xff, second = raw[1] & 0xff;
            if ((first & 0xfe) == 0xfc) return false; // fc00::/7 ULA
            if (first == 0xfe && (second & 0xc0) == 0x80) return false; // fe80::/10
        }
        return true;
    }


    public void updateActiveView(Account account, String deviceId, String clientId, boolean active,
                                 String dmThreadId, String groupRoomId) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) return;
        String user = clean(account.uuid, 80).toLowerCase(Locale.ROOT);
        String device = cleanDeviceId(deviceId);
        String client = cleanViewClientId(clientId);
        if (user.isBlank() || device.isBlank() || client.isBlank()) return;
        // Viewing state is account/session state, not Web Push subscription state.
        // A desktop browser that does not subscribe to Push must still be able to
        // suppress duplicate mobile Push while that same account is actively
        // reading the exact DM/group conversation.
        String key = activeViewKey(user, device, client);
        pruneExpiredActiveViews();
        if (!active) {
            activeViews.remove(key);
            return;
        }
        String dm = clean(dmThreadId, 160);
        String group = clean(groupRoomId, 160);
        if (dm.isBlank() && group.isBlank()) {
            activeViews.remove(key);
            return;
        }
        ActiveView view = new ActiveView();
        view.userUuid = user;
        view.deviceId = device;
        view.clientId = client;
        view.dmThreadId = dm;
        view.groupRoomId = group;
        view.updatedAt = System.currentTimeMillis();
        activeViews.put(key, view);
        trimActiveViewsForDevice(user, device);
    }

    public ActivePrivateViewSnapshot activePrivateViewSnapshot(String userUuid) {
        String user = clean(userUuid, 80).toLowerCase(Locale.ROOT);
        Map<String, Long> dm = new LinkedHashMap<>();
        Map<String, Long> group = new LinkedHashMap<>();
        if (user.isBlank()) return new ActivePrivateViewSnapshot(dm, group);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ActiveView> e : new ArrayList<>(activeViews.entrySet())) {
            ActiveView view = e.getValue();
            if (view == null) continue;
            long expiresAt = view.updatedAt + ACTIVE_VIEW_TTL_MILLIS;
            if (expiresAt <= now) {
                activeViews.remove(e.getKey(), view);
                continue;
            }
            if (!user.equals(view.userUuid)) continue;
            if (view.dmThreadId != null && !view.dmThreadId.isBlank()) {
                dm.merge(view.dmThreadId, expiresAt, Math::max);
            }
            if (view.groupRoomId != null && !view.groupRoomId.isBlank()) {
                group.merge(view.groupRoomId, expiresAt, Math::max);
            }
        }
        return new ActivePrivateViewSnapshot(dm, group);
    }

    public void clearActiveViews(Account account, String deviceId) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) return;
        clearActiveViews(account.uuid, deviceId);
    }

    private void clearActiveViews(String userUuid, String deviceId) {
        String user = clean(userUuid, 80).toLowerCase(Locale.ROOT);
        String device = cleanDeviceId(deviceId);
        if (user.isBlank() || device.isBlank()) return;
        for (Map.Entry<String, ActiveView> e : new ArrayList<>(activeViews.entrySet())) {
            ActiveView view = e.getValue();
            if (view != null && user.equals(view.userUuid) && device.equals(view.deviceId)) {
                activeViews.remove(e.getKey(), view);
            }
        }
    }

    private boolean suppressedByActivePrivateView(Subscription subscription, Payload payload) {
        if (subscription == null || payload == null) return false;
        String type = clean(payload.type, 40).toLowerCase(Locale.ROOT);
        if (!"dm".equals(type) && !"group".equals(type) && !"group-chat".equals(type)) return false;
        String user = clean(subscription.userUuid, 80).toLowerCase(Locale.ROOT);
        if (user.isBlank()) return false;
        String dm = clean(payload.dmThreadId, 160);
        String group = clean(payload.groupRoomId, 160);
        if (dm.isBlank() && group.isBlank()) return false;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ActiveView> e : new ArrayList<>(activeViews.entrySet())) {
            ActiveView view = e.getValue();
            if (view == null) continue;
            if (now - view.updatedAt > ACTIVE_VIEW_TTL_MILLIS) {
                activeViews.remove(e.getKey(), view);
                continue;
            }
            // Account-wide attention suppression: if any foreground KWC client
            // for this account is actively viewing the exact private conversation,
            // suppress every Push subscription for the account (desktop/mobile).
            if (!user.equals(view.userUuid)) continue;
            if (!dm.isBlank() && dm.equals(view.dmThreadId)) return true;
            if (!group.isBlank() && group.equals(view.groupRoomId)) return true;
        }
        return false;
    }

    private void pruneExpiredActiveViews() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ActiveView> e : new ArrayList<>(activeViews.entrySet())) {
            ActiveView view = e.getValue();
            if (view == null || now - view.updatedAt > ACTIVE_VIEW_TTL_MILLIS) {
                activeViews.remove(e.getKey(), view);
            }
        }
    }

    private void trimActiveViewsForDevice(String user, String device) {
        List<Map.Entry<String, ActiveView>> matches = new ArrayList<>();
        for (Map.Entry<String, ActiveView> e : activeViews.entrySet()) {
            ActiveView view = e.getValue();
            if (view != null && user.equals(view.userUuid) && device.equals(view.deviceId)) matches.add(e);
        }
        if (matches.size() <= 8) return;
        matches.sort(java.util.Comparator.comparingLong(e -> e.getValue().updatedAt));
        for (int i = 0; i < matches.size() - 8; i++) {
            Map.Entry<String, ActiveView> e = matches.get(i);
            activeViews.remove(e.getKey(), e.getValue());
        }
    }

    private boolean hasSubscriptionForDevice(String user, String device) {
        for (Subscription subscription : byEndpoint.values()) {
            if (subscription == null) continue;
            if (user.equals(clean(subscription.userUuid, 80).toLowerCase(Locale.ROOT))
                    && device.equals(cleanDeviceId(subscription.deviceId))) return true;
        }
        return false;
    }

    private String activeViewKey(String user, String device, String client) {
        return user + "|" + device + "|" + client;
    }

    private String cleanViewClientId(String value) {
        String id = clean(value, 96);
        return id.matches("[A-Za-z0-9_-]{12,96}") ? id : "";
    }

    public synchronized boolean unsubscribe(Account account, String endpoint) {
        return unsubscribe(account, endpoint, "", false);
    }

    public synchronized boolean unsubscribe(Account account, String endpoint, String deviceId, boolean clearLegacy) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) return false;
        String user = clean(account.uuid, 80).toLowerCase(Locale.ROOT);
        String ep = clean(endpoint, 2048);
        String device = cleanDeviceId(deviceId);
        boolean removed = false;
        for (Map.Entry<String, Subscription> entry : new ArrayList<>(byEndpoint.entrySet())) {
            Subscription candidate = entry.getValue();
            if (candidate == null || !user.equals(clean(candidate.userUuid, 80).toLowerCase(Locale.ROOT))) continue;
            boolean exactEndpoint = !ep.isBlank() && ep.equals(entry.getKey());
            boolean sameDevice = !device.isBlank() && device.equals(candidate.deviceId);
            boolean legacy = clearLegacy && (candidate.deviceId == null || candidate.deviceId.isBlank());
            if (exactEndpoint || sameDevice || legacy) removed |= byEndpoint.remove(entry.getKey(), candidate);
        }
        if (removed) {
            // Active-view state is independent of whether this browser/device
            // subscribes to Web Push. The page heartbeat/TTL owns its lifecycle.
            saveSubscriptions();
        }
        return removed;
    }

    public void sendToUser(String userUuid, Payload payload) {
        if (userUuid == null || userUuid.isBlank()) return;
        sendToUsers(Set.of(userUuid.trim().toLowerCase(Locale.ROOT)), payload);
    }

    public void sendToAll(Payload payload) {
        Set<String> all = ConcurrentHashMap.newKeySet();
        for (Subscription s : byEndpoint.values()) {
            if (s != null && s.userUuid != null && !s.userUuid.isBlank()) all.add(s.userUuid);
        }
        sendToUsers(all, payload);
    }

    public void sendToUsers(Set<String> userUuids, Payload payload) {
        ConfigValues c = host.config();
        if (c == null || !c.webPushEnabled || payload == null || userUuids == null || userUuids.isEmpty()) return;
        ensureVapidKeyPair();
        if (vapidKeyPair == null || vapidPublicKeyBase64 == null || vapidPublicKeyBase64.isBlank()) return;
        Set<String> normalized = ConcurrentHashMap.newKeySet();
        for (String uuid : userUuids) {
            if (uuid != null && !uuid.isBlank()) normalized.add(uuid.trim().toLowerCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) return;
        String targetDeviceId = cleanDeviceId(payload.targetDeviceId);
        List<Subscription> targets = new ArrayList<>();
        for (Subscription s : byEndpoint.values()) {
            if (s == null || !normalized.contains(s.userUuid)) continue;
            if (!targetDeviceId.isBlank() && !targetDeviceId.equals(s.deviceId)) continue;
            if (suppressedByActivePrivateView(s, payload)) continue;
            if (!allowsSubscription(s, payload, c)) continue;
            targets.add(s);
        }
        if (targets.isEmpty()) return;
        for (Subscription s : targets) {
            try {
                String matchedKeyword = matchedKeyword(s, payload);
                if (!matchedKeyword.isBlank() && keywordCooldownActive(s, payload, matchedKeyword)) continue;
                String json = payloadJson(s, payload, matchedKeyword);
                sendOne(s, json, Math.max(30, c.webPushTtlSeconds));
                if (!matchedKeyword.isBlank()) markKeywordCooldown(s, payload, matchedKeyword);
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                host.fine("Web Push delivery failed: " + msg);
            }
        }
    }

    private boolean allowsSubscription(Subscription s, Payload payload, ConfigValues c) {
        if (s == null || payload == null || c == null) return false;
        String type = payload.type == null ? "" : payload.type.trim().toLowerCase(Locale.ROOT);
        if ("test".equals(type)) return true;
        String sender = payload.senderUuid == null ? "" : payload.senderUuid.trim().toLowerCase(Locale.ROOT);
        boolean ownMessage = !sender.isBlank() && sender.equalsIgnoreCase(s.userUuid);
        if (ownMessage) return false;
        // Reaction notifications have an explicit category toggle. Do not let a
        // keyword match bypass that OFF state.
        if ("reaction".equals(type)) return c.webPushNotifyReactions && s.notifyReactions;
        if (isSystemType(type) && !allowsSystemMode(s, payload, c)) return false;
        if (c.webPushNotifyKeywords && s.notifyKeywords && !matchedKeyword(s, payload).isBlank()) return true;
        String replyTarget = payload.replyTargetUuid == null ? "" : payload.replyTargetUuid.trim().toLowerCase(Locale.ROOT);
        boolean isReplyTarget = !replyTarget.isBlank() && replyTarget.equalsIgnoreCase(s.userUuid);
        boolean isMentionTarget = "chat".equals(type) && payload.mentionTargetUuids != null
                && payload.mentionTargetUuids.stream().anyMatch(v -> v != null && v.equalsIgnoreCase(s.userUuid));
        if ("reply".equals(type)) {
            return c.webPushNotifyReplies && s.notifyReplies;
        }
        if (isReplyTarget && c.webPushNotifyReplies && s.notifyReplies) return false;
        if (isMentionTarget) {
            return c.webPushNotifyMentions && s.notifyMentions;
        }
        // Own messages have already been rejected before selective keyword, reply,
        // mention, and broad-type evaluation.
        if ("dm".equals(type)) {
            if (!c.webPushNotifyDm || !s.notifyDm) return false;
        } else if ("group".equals(type) || "group-chat".equals(type)) {
            if (!c.webPushNotifyGroupChat || !s.notifyGroupChat) return false;
        } else if (isSystemType(type)) {
            if (!allowsSystemMode(s, payload, c)) return false;
        } else {
            if (!c.webPushNotifyNormalChat || !s.notifyNormalChat) return false;
        }
        return true;
    }


    private boolean isSystemType(String type) {
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return t.equals("system") || t.equals("server");
    }

    private boolean allowsSystemMode(Subscription s, Payload payload, ConfigValues c) {
        if (s == null || payload == null || c == null || !c.webPushNotifySystem || !s.notifySystem) return false;
        String mode = normalizeSystemMode(s.notifySystemMode, s.notifySystem ? "all" : "off");
        if ("off".equals(mode)) return false;
        if ("join-leave".equals(mode)) return isJoinLeaveSystemPayload(payload);
        return true;
    }

    private boolean isJoinLeaveSystemPayload(Payload payload) {
        if (payload == null) return false;
        String key = clean(payload.i18nKey, 120).toLowerCase(Locale.ROOT);
        if (key.endsWith("minecraft-join") || key.endsWith("minecraft-quit") || key.endsWith("first-join")) return true;
        String kind = clean(payload.systemKind, 80).toLowerCase(Locale.ROOT);
        return kind.equals("minecraft-join") || kind.equals("minecraft-quit") || kind.equals("first-join") || kind.equals("join") || kind.equals("quit") || kind.equals("leave");
    }

    private String normalizeSystemMode(String value, String fallback) {
        String v = clean(value, 40).toLowerCase(Locale.ROOT).replace('_', '-');
        if (v.equals("all") || v.equals("join-leave") || v.equals("off")) return v;
        String f = clean(fallback, 40).toLowerCase(Locale.ROOT).replace('_', '-');
        if (f.equals("all") || f.equals("join-leave") || f.equals("off")) return f;
        return "all";
    }

    private String readSystemMode(Map<String, String> body, String key, String fallback) {
        return normalizeSystemMode(body == null ? "" : String.valueOf(body.getOrDefault(key, "")), fallback);
    }

    private List<String> normalizeKeywords(String raw) {
        String text = String.valueOf(raw == null ? "" : raw);
        List<String> out = new ArrayList<>();
        Set<String> seen = ConcurrentHashMap.newKeySet();
        for (String part : text.split("[\r\n,]+")) {
            String keyword = clean(part, 80);
            if (keyword.isBlank()) continue;
            String lower = keyword.toLowerCase(Locale.ROOT);
            if (!seen.add(lower)) continue;
            out.add(keyword);
            if (out.size() >= 40) break;
        }
        return out;
    }

    private String matchedKeyword(Subscription s, Payload payload) {
        if (s == null || payload == null || s.keywords == null || s.keywords.isEmpty()) return "";
        String body = localizedPayloadBody(s, payload);
        String haystack = (mobileNotificationText(payload.title, 0) + " " + mobileNotificationText(body, 0)).toLowerCase(Locale.ROOT);
        for (String keyword : s.keywords) {
            String kw = String.valueOf(keyword == null ? "" : keyword).trim();
            if (kw.isBlank()) continue;
            if (haystack.contains(kw.toLowerCase(Locale.ROOT))) return kw;
        }
        return "";
    }

    private String keywordCooldownKey(Subscription s, Payload payload, String keyword) {
        String endpoint = s == null ? "" : clean(s.endpoint, 512);
        String type = payload == null ? "" : clean(payload.type, 40).toLowerCase(Locale.ROOT);
        String tag = payload == null ? "" : clean(payload.tag, 120).toLowerCase(Locale.ROOT);
        String kw = clean(keyword, 80).toLowerCase(Locale.ROOT);
        return endpoint + "|" + type + "|" + tag + "|" + kw;
    }

    private boolean keywordCooldownActive(Subscription s, Payload payload, String keyword) {
        String key = keywordCooldownKey(s, payload, keyword);
        if (key.isBlank()) return false;
        long now = System.currentTimeMillis();
        Long until = keywordCooldownUntil.get(key);
        if (until != null && until > now) return true;
        if (until != null) keywordCooldownUntil.remove(key, until);
        return false;
    }

    private void markKeywordCooldown(Subscription s, Payload payload, String keyword) {
        String key = keywordCooldownKey(s, payload, keyword);
        if (!key.isBlank()) keywordCooldownUntil.put(key, System.currentTimeMillis() + KEYWORD_PUSH_COOLDOWN_MILLIS);
    }


    private boolean readBool(Map<String, String> body, String key, boolean fallback) {
        String value = body == null ? "" : String.valueOf(body.getOrDefault(key, "")).trim().toLowerCase(Locale.ROOT);
        if (value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on")) return true;
        if (value.equals("false") || value.equals("0") || value.equals("no") || value.equals("off")) return false;
        return fallback;
    }

    private String configuredNotificationTitle() {
        ConfigValues c = host.config();
        String title = c == null ? "" : clean(c.webPushNotificationTitle, 80);
        if (title != null && !title.isBlank()) return title;
        String appName = c == null ? "" : clean(c.standaloneWebAppName, 80);
        return appName == null || appName.isBlank() ? "Web Chat" : appName;
    }

    private String subscriptionText(Subscription s, String key, String fallback) {
        try {
            String lang = s == null ? "" : clean(s.language, 40);
            Map<String, String> strings = host.webStringsFor(lang);
            String value = strings == null ? null : strings.get(key);
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }


    private String localizedPayloadBody(Subscription sub, Payload p) {
        if (p == null) return "";
        String fallback = p.body == null ? "" : p.body;
        String key = clean(p.i18nKey, 160);
        if (key.isBlank()) return fallback;
        String value = subscriptionText(sub, key, fallback);
        Map<String, String> vars = JsonUtil.parseFlatObject(p.i18nArgs);
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return value;
    }

    private String payloadJson(Subscription sub, Payload p, String matchedKeyword) {
        Map<String, Object> m = new LinkedHashMap<>();
        String keyword = clean(matchedKeyword, 80);
        boolean keywordHit = !keyword.isBlank();
        String baseTitle = mobileNotificationText(configuredNotificationTitle(), 80);
        String type = clean(p.type, 40).toLowerCase(Locale.ROOT);
        String rawTitle = p.title == null || p.title.isBlank() ? baseTitle : p.title;
        String title = mobileNotificationText(rawTitle, 120);
        if (title.isBlank()) title = baseTitle;
        String rawBody = localizedPayloadBody(sub, p);
        if ("test".equals(type) && rawBody.isBlank()) rawBody = subscriptionText(sub, "notification.testBody", "Test push sent.");
        String body = mobileNotificationText(rawBody, 240);
        if (keywordHit) {
            String keywordLabel = subscriptionText(sub, "notification.keyword", "Keyword");
            m.put("title", baseTitle);
            m.put("body", mobileNotificationText(keywordLabel + ": " + keyword + " · " + title + (body.isBlank() ? "" : " · " + body), 240));
            m.put("type", "keyword");
            m.put("tag", clean("kwc-keyword-" + keyword.replaceAll("[^A-Za-z0-9가-힣ぁ-んァ-ン一-龥_-]", ""), 120));
        } else {
            m.put("title", baseTitle);
            String detailPrefix = title.equals(baseTitle) ? "" : title;
            if ("reply".equals(type)) {
                String replyLabel = subscriptionText(sub, "notification.reply", "Reply");
                detailPrefix = replyLabel + (detailPrefix.isBlank() ? "" : ": " + detailPrefix);
            }
            if ("dm".equals(type) && !detailPrefix.isBlank()) {
                detailPrefix = subscriptionText(sub, "notification.dm", "DM") + ": " + detailPrefix;
            }
            m.put("body", mobileNotificationText(detailPrefix + (detailPrefix.isBlank() || body.isBlank() ? "" : " · ") + body, 240));
            m.put("type", clean(p.type, 40));
            m.put("tag", clean(p.tag, 120));
        }
        m.put("url", notificationOpenUrl(sub, p == null ? "" : p.url));
        m.put("time", System.currentTimeMillis());
        return JsonUtil.obj(m);
    }

    private String mobileNotificationText(String value, int limit) {
        String out = value == null ? "" : String.valueOf(value);
        // OS/mobile Web Push notifications cannot render Minecraft legacy color
        // codes, ampersand color codes, MiniMessage tags, or raw hex markers.
        // Strip them here while leaving in-page web rendering untouched.
        out = out.replaceAll("(?i)[§&]x(?:[§&][0-9a-f]){6}", "");
        out = out.replaceAll("(?i)&#[0-9a-f]{6}", "");
        out = out.replaceAll("(?i)[§&][0-9a-fk-or]", "");
        out = out.replaceAll("<[^>]+>", "");
        out = out.replace("§", "");
        out = out.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        if (limit > 0 && out.length() > limit) {
            out = out.substring(0, Math.max(0, limit - 1)).trim() + "…";
        }
        return out;
    }

    private String notificationOpenUrl(Subscription sub, String payloadUrl) {
        String payload = clean(payloadUrl, 2048);
        String base = sub == null ? "" : clean(sub.openUrl, 2048);
        if (base.isBlank()) return payload;
        Map<String, String> nav = extractNavigationParams(payload);
        // The subscription openUrl is the canonical page where the user enabled
        // Web Push. Payload URLs are kept only as navigation hints/fallbacks.
        // Without this, no-target notifications such as test/system pushes could
        // still open the configured standalone path (/chat) even for BlueMap addon
        // subscriptions.
        if (nav.isEmpty()) return base;
        return withNavigationParams(base, nav);
    }

    private Map<String, String> extractNavigationParams(String value) {
        Map<String, String> out = new LinkedHashMap<>();
        String raw = clean(value, 2048);
        if (raw.isBlank()) return out;
        try {
            URI uri;
            if (raw.startsWith("http://") || raw.startsWith("https://")) uri = URI.create(raw);
            else uri = URI.create("https://kwc.local" + (raw.startsWith("/") ? raw : "/" + raw));
            String q = uri.getRawQuery();
            if (q == null || q.isBlank()) return out;
            for (String part : q.split("&")) {
                if (part == null || part.isBlank()) continue;
                int eq = part.indexOf('=');
                String k = eq >= 0 ? part.substring(0, eq) : part;
                String v = eq >= 0 ? part.substring(eq + 1) : "";
                k = java.net.URLDecoder.decode(k, StandardCharsets.UTF_8);
                if (!isNavigationParam(k)) continue;
                k = canonicalNavigationParam(k);
                v = java.net.URLDecoder.decode(v, StandardCharsets.UTF_8);
                if (!v.isBlank()) out.put(k, v);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String canonicalNavigationParam(String key) {
        return switch (String.valueOf(key == null ? "" : key)) {
            case "bmwcMessage" -> "kwcMessage";
            case "bmwcDmThread" -> "kwcDmThread";
            case "bmwcDmMessage" -> "kwcDmMessage";
            case "bmwcGroupRoom" -> "kwcGroupRoom";
            case "bmwcGroupMessage" -> "kwcGroupMessage";
            default -> String.valueOf(key == null ? "" : key);
        };
    }

    private boolean isNavigationParam(String key) {
        return "kwcMessage".equals(key) || "kwcDmThread".equals(key) || "kwcDmMessage".equals(key)
                || "kwcGroupRoom".equals(key) || "kwcGroupMessage".equals(key)
                // BlueMapWebChat 4.x notification URLs are accepted only as a legacy navigation alias.
                || "bmwcMessage".equals(key) || "bmwcDmThread".equals(key) || "bmwcDmMessage".equals(key)
                || "bmwcGroupRoom".equals(key) || "bmwcGroupMessage".equals(key);
    }

    private String withNavigationParams(String baseUrl, Map<String, String> nav) {
        String base = clean(baseUrl, 2048);
        if (base.isBlank() || nav == null || nav.isEmpty()) return base;
        try {
            String hash = "";
            int hashIdx = base.indexOf('#');
            if (hashIdx >= 0) {
                hash = base.substring(hashIdx);
                base = base.substring(0, hashIdx);
            }
            String path = base;
            String query = "";
            int qIdx = base.indexOf('?');
            if (qIdx >= 0) {
                path = base.substring(0, qIdx);
                query = base.substring(qIdx + 1);
            }
            List<String> parts = new ArrayList<>();
            if (!query.isBlank()) {
                for (String part : query.split("&")) {
                    if (part == null || part.isBlank()) continue;
                    String k = part;
                    int eq = part.indexOf('=');
                    if (eq >= 0) k = part.substring(0, eq);
                    try { k = java.net.URLDecoder.decode(k, StandardCharsets.UTF_8); } catch (Exception ignored) {}
                    if (!isNavigationParam(k)) parts.add(part);
                }
            }
            for (Map.Entry<String, String> e : nav.entrySet()) {
                String k = e.getKey() == null ? "" : e.getKey();
                String v = e.getValue() == null ? "" : e.getValue();
                if (!isNavigationParam(k) || v.isBlank()) continue;
                parts.add(java.net.URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + java.net.URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
            return path + (parts.isEmpty() ? "" : "?" + String.join("&", parts)) + hash;
        } catch (Exception ignored) {
            return base;
        }
    }

    private void sendOne(Subscription sub, String jsonPayload, int ttlSeconds) throws Exception {
        if (sub == null || !validPushEndpoint(sub.endpoint, true)) throw new IOException("unsafe_push_endpoint");
        byte[] userPublicKey = b64u.decode(sub.p256dh);
        byte[] authSecret = b64u.decode(sub.auth);
        byte[] plain = jsonPayload.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = encryptAes128Gcm(userPublicKey, authSecret, plain);
        URL url = URI.create(sub.endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        conn.setDoOutput(true);
        conn.setRequestProperty("TTL", String.valueOf(ttlSeconds));
        conn.setRequestProperty("Urgency", "normal");
        conn.setRequestProperty("Content-Encoding", "aes128gcm");
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("Authorization", vapidAuthorizationHeader(sub.endpoint));
        conn.setRequestProperty("Content-Length", String.valueOf(encrypted.length));
        try (OutputStream out = conn.getOutputStream()) {
            out.write(encrypted);
        }
        int code = conn.getResponseCode();
        if (code == 404 || code == 410) {
            byEndpoint.remove(sub.endpoint);
            saveSubscriptions();
            return;
        }
        if (code < 200 || code >= 300) {
            String error = "HTTP " + code;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream() == null ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line = br.readLine();
                if (line != null && !line.isBlank()) error += " " + line;
            } catch (Exception ignored) {}
            throw new IOException(error);
        }
    }

    private byte[] encryptAes128Gcm(byte[] userPublicKey, byte[] authSecret, byte[] payload) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), random);
        KeyPair serverKey = kpg.generateKeyPair();
        byte[] serverPublicKey = uncompressedPublicKey((ECPublicKey) serverKey.getPublic());

        PublicKey receiverPublic = publicKeyFromUncompressed(userPublicKey);
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(serverKey.getPrivate());
        ka.doPhase(receiverPublic, true);
        byte[] ecdhSecret = ka.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] info = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), userPublicKey, serverPublicKey);
        byte[] ikm = hkdfExpand(prkKey, info, 32);
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = hkdfExpand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdfExpand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        byte[] recordPlaintext = new byte[payload.length + 1];
        System.arraycopy(payload, 0, recordPlaintext, 0, payload.length);
        recordPlaintext[recordPlaintext.length - 1] = 0x02;

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] cipherText = cipher.doFinal(recordPlaintext);

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + serverPublicKey.length + cipherText.length);
        header.put(salt);
        header.putInt(4096);
        header.put((byte) serverPublicKey.length);
        header.put(serverPublicKey);
        header.put(cipherText);
        return header.array();
    }

    private String vapidAuthorizationHeader(String endpoint) throws Exception {
        ConfigValues c = host.config();
        String subject = c == null || c.webPushSubject == null || c.webPushSubject.isBlank() ? "mailto:admin@example.com" : c.webPushSubject.trim();
        URI uri = URI.create(endpoint);
        String aud = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        long exp = Instant.now().getEpochSecond() + 12 * 60 * 60;
        String header = b64uNoPad.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = JsonUtil.obj(Map.of("aud", aud, "exp", exp, "sub", subject));
        String body = b64uNoPad.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + body;
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(vapidKeyPair.getPrivate());
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String signature = b64uNoPad.encodeToString(derEcdsaToJose(sig.sign(), 64));
        return "vapid t=" + signingInput + "." + signature + ", k=" + vapidPublicKeyBase64;
    }

    private synchronized void ensureVapidKeyPair() {
        if (vapidKeyPair != null && vapidPublicKeyBase64 != null && !vapidPublicKeyBase64.isBlank()) return;
        ConfigValues c = host.config();
        try {
            String publicKey = c == null ? "" : clean(c.webPushVapidPublicKey, 512);
            String privateKey = c == null ? "" : clean(c.webPushVapidPrivateKey, 512);
            if (!publicKey.isBlank() && !privateKey.isBlank()) {
                vapidKeyPair = keyPairFromBase64(publicKey, privateKey);
                vapidPublicKeyBase64 = publicKey;
                return;
            }
            Path file = host.dataDirectory().resolve("web-push-vapid.properties");
            Properties props = new Properties();
            if (Files.exists(file)) {
                try (var in = Files.newInputStream(file)) { props.load(in); }
                publicKey = clean(props.getProperty("publicKey"), 512);
                privateKey = clean(props.getProperty("privateKey"), 512);
                if (!publicKey.isBlank() && !privateKey.isBlank()) {
                    vapidKeyPair = keyPairFromBase64(publicKey, privateKey);
                    vapidPublicKeyBase64 = publicKey;
                    return;
                }
            }
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"), random);
            KeyPair generated = kpg.generateKeyPair();
            String generatedPublic = b64uNoPad.encodeToString(uncompressedPublicKey((ECPublicKey) generated.getPublic()));
            String generatedPrivate = b64uNoPad.encodeToString(toUnsignedFixed(((ECPrivateKey) generated.getPrivate()).getS(), 32));
            Files.createDirectories(file.getParent());
            props.setProperty("publicKey", generatedPublic);
            props.setProperty("privateKey", generatedPrivate);
            try (var out = Files.newOutputStream(file)) { props.store(out, "KOKOTO WebChat auto-generated Web Push VAPID keys"); }
            vapidKeyPair = generated;
            vapidPublicKeyBase64 = generatedPublic;
        } catch (Exception ex) {
            host.warn("Failed to initialize Web Push VAPID keys: " + ex.getMessage());
            vapidKeyPair = null;
            vapidPublicKeyBase64 = "";
        }
    }

    private KeyPair keyPairFromBase64(String publicKeyBase64, String privateKeyBase64) throws Exception {
        byte[] pub = b64u.decode(publicKeyBase64);
        byte[] priv = b64u.decode(privateKeyBase64);
        PublicKey publicKey = publicKeyFromUncompressed(pub);
        ECParameterSpec params = ((ECPublicKey) publicKey).getParams();
        BigInteger d = new BigInteger(1, priv);
        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(d, params));
        return new KeyPair(publicKey, privateKey);
    }

    private PublicKey publicKeyFromUncompressed(byte[] key) throws Exception {
        if (key == null || key.length != 65 || key[0] != 0x04) throw new GeneralSecurityException("invalid P-256 public key");
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec params = parameters.getParameterSpec(ECParameterSpec.class);
        byte[] xb = new byte[32];
        byte[] yb = new byte[32];
        System.arraycopy(key, 1, xb, 0, 32);
        System.arraycopy(key, 33, yb, 0, 32);
        ECPoint point = new ECPoint(new BigInteger(1, xb), new BigInteger(1, yb));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, params));
    }

    private byte[] uncompressedPublicKey(ECPublicKey key) {
        byte[] x = toUnsignedFixed(key.getW().getAffineX(), 32);
        byte[] y = toUnsignedFixed(key.getW().getAffineY(), 32);
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(x, 0, out, 1, 32);
        System.arraycopy(y, 0, out, 33, 32);
        return out;
    }

    private byte[] toUnsignedFixed(BigInteger value, int len) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[len];
        int src = Math.max(0, raw.length - len);
        int count = Math.min(raw.length, len);
        System.arraycopy(raw, src, out, len - count, count);
        return out;
    }

    private byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[length];
        byte[] t = new byte[0];
        int pos = 0;
        int counter = 1;
        while (pos < length) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update((byte) counter++);
            t = mac.doFinal();
            int copy = Math.min(t.length, length - pos);
            System.arraycopy(t, 0, out, pos, copy);
            pos += copy;
        }
        return out;
    }

    private byte[] concat(byte[]... items) {
        int len = 0;
        for (byte[] b : items) len += b == null ? 0 : b.length;
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] b : items) {
            if (b == null) continue;
            System.arraycopy(b, 0, out, pos, b.length);
            pos += b.length;
        }
        return out;
    }

    private byte[] derEcdsaToJose(byte[] der, int outputLength) throws IOException {
        if (der == null || der.length < 8 || der[0] != 0x30) throw new IOException("invalid ECDSA signature");
        int offset = 2;
        if ((der[1] & 0xff) > 0x80) offset = 2 + (der[1] & 0x7f);
        if (der[offset++] != 0x02) throw new IOException("invalid ECDSA R");
        int rLen = der[offset++] & 0xff;
        byte[] r = new byte[rLen];
        System.arraycopy(der, offset, r, 0, rLen);
        offset += rLen;
        if (der[offset++] != 0x02) throw new IOException("invalid ECDSA S");
        int sLen = der[offset++] & 0xff;
        byte[] s = new byte[sLen];
        System.arraycopy(der, offset, s, 0, sLen);
        byte[] out = new byte[outputLength];
        copyDerInteger(r, out, 0, outputLength / 2);
        copyDerInteger(s, out, outputLength / 2, outputLength / 2);
        return out;
    }

    private void copyDerInteger(byte[] src, byte[] out, int offset, int len) {
        int start = 0;
        while (start < src.length - 1 && src[start] == 0) start++;
        int count = Math.min(src.length - start, len);
        System.arraycopy(src, start + (src.length - start - count), out, offset + len - count, count);
    }

    private Path subscriptionsFile() {
        ConfigValues c = host.config();
        String name = c == null || c.webPushSubscriptionsFile == null || c.webPushSubscriptionsFile.isBlank() ? "web-push-subscriptions.jsonl" : c.webPushSubscriptionsFile;
        Path path = Path.of(name);
        if (!path.isAbsolute()) path = host.dataDirectory().resolve(path);
        return path;
    }

    private synchronized void loadSubscriptions() {
        byEndpoint.clear();
        Path file = subscriptionsFile();
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Map<String, String> m = JsonUtil.parseFlatObject(line);
                String endpoint = clean(m.get("endpoint"), 2048);
                if (endpoint.isBlank()) continue;
                Subscription s = new Subscription();
                s.endpoint = endpoint;
                s.userUuid = clean(m.get("userUuid"), 80).toLowerCase(Locale.ROOT);
                s.p256dh = clean(m.get("p256dh"), 512);
                s.auth = clean(m.get("auth"), 256);
                s.userAgent = clean(m.get("userAgent"), 300);
                s.deviceId = cleanDeviceId(m.get("deviceId"));
                ConfigValues c = host.config();
                s.notifyNormalChat = (c == null || c.webPushNotifyNormalChat) && readBool(m, "notifyNormalChat", c != null && c.webPushNotifyNormalChat);
                s.notifyDm = (c == null || c.webPushNotifyDm) && readBool(m, "notifyDm", c == null || c.webPushNotifyDm);
                s.notifyGroupChat = (c == null || c.webPushNotifyGroupChat) && readBool(m, "notifyGroupChat", c == null || c.webPushNotifyGroupChat);
                s.notifyMentions = (c == null || c.webPushNotifyMentions) && readBool(m, "notifyMentions", c == null || c.webPushNotifyMentions);
                s.notifyReplies = (c == null || c.webPushNotifyReplies) && readBool(m, "notifyReplies", c == null || c.webPushNotifyReplies);
                // Existing 5.2.0 subscriptions predate this field; inherit their Reply choice once.
                s.notifyReactions = (c == null || c.webPushNotifyReactions) && readBool(m, "notifyReactions", s.notifyReplies);
                s.notifySystemMode = readSystemMode(m, "notifySystemMode", readBool(m, "notifySystem", c == null || c.webPushNotifySystem) ? "all" : "off");
                s.notifySystem = (c == null || c.webPushNotifySystem) && !"off".equals(s.notifySystemMode);
                s.notifyKeywords = (c == null || c.webPushNotifyKeywords) && readBool(m, "notifyKeywords", c == null || c.webPushNotifyKeywords);
                s.keywords = normalizeKeywords(m.get("keywords"));
                s.language = clean(m.get("language"), 40);
                s.openUrl = clean(m.get("openUrl"), 2048);
                try { s.updatedAt = Long.parseLong(String.valueOf(m.getOrDefault("updatedAt", "0"))); } catch (Exception ignored) {}
                if (!s.userUuid.isBlank() && !s.p256dh.isBlank() && !s.auth.isBlank()) byEndpoint.put(endpoint, s);
            }
        } catch (Exception ex) {
            host.warn("Failed to load Web Push subscriptions: " + ex.getMessage());
        }
    }

    private synchronized void saveSubscriptions() {
        Path file = subscriptionsFile();
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            for (Subscription s : byEndpoint.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("endpoint", s.endpoint);
                m.put("userUuid", s.userUuid);
                m.put("p256dh", s.p256dh);
                m.put("auth", s.auth);
                m.put("userAgent", s.userAgent);
                m.put("deviceId", s.deviceId);
                m.put("notifyNormalChat", s.notifyNormalChat);
                m.put("notifyDm", s.notifyDm);
                m.put("notifyGroupChat", s.notifyGroupChat);
                m.put("notifyMentions", s.notifyMentions);
                m.put("notifyReplies", s.notifyReplies);
                m.put("notifyReactions", s.notifyReactions);
                m.put("notifySystem", s.notifySystem);
                m.put("notifySystemMode", s.notifySystemMode);
                m.put("notifyKeywords", s.notifyKeywords);
                m.put("keywords", String.join("\n", s.keywords == null ? Collections.emptyList() : s.keywords));
                m.put("language", s.language);
                m.put("openUrl", s.openUrl);
                m.put("updatedAt", s.updatedAt);
                lines.add(JsonUtil.obj(m));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            host.warn("Failed to save Web Push subscriptions: " + ex.getMessage());
        }
    }

    private String cleanDeviceId(String value) {
        String deviceId = clean(value, 96);
        return deviceId.matches("[A-Za-z0-9_-]{12,96}") ? deviceId : "";
    }

    private String clean(String value, int max) {
        String text = String.valueOf(value == null ? "" : value).replaceAll("[\\r\\n\\u0000-\\u001f]", "").trim();
        if (max > 0 && text.length() > max) return text.substring(0, max);
        return text;
    }
}
