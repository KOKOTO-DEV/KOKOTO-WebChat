package dev.kokoto.webchat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Emits transport-security warnings from the currently loaded configuration. */
public final class TransportSecurityWarnings {
    private TransportSecurityWarnings() {}

    public static void logAll(ConfigValues config, WebChatLanguage language, CoreLogger logger) {
        if (config == null || logger == null) return;
        for (String message : collectAll(config, language)) logger.warn(message);
    }

    /** Returns the same warning text used by the logger so command frontends can echo it directly. */
    public static List<String> collectAll(ConfigValues config, WebChatLanguage language) {
        List<String> messages = new ArrayList<>();
        if (config == null) return messages;
        collectHttpServer(config, language, messages);
        collectRelayPeers(config, language, messages);
        return messages;
    }

    /**
     * Splits a warning into sentence-sized command-output lines. Logger WARN records
     * intentionally remain single-line so log parsing stays stable.
     */
    public static List<String> splitForCommandOutput(String warning) {
        List<String> lines = new ArrayList<>();
        if (warning == null || warning.isBlank()) return lines;

        String value = warning.trim();
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '.' && ch != '!' && ch != '?' && ch != '。' && ch != '！' && ch != '？') continue;
            int next = i + 1;
            if (next < value.length() && !Character.isWhitespace(value.charAt(next))) continue;
            String sentence = value.substring(start, next).trim();
            if (!sentence.isEmpty()) lines.add(sentence);
            while (next < value.length() && Character.isWhitespace(value.charAt(next))) next++;
            start = next;
            i = next - 1;
        }
        if (start < value.length()) {
            String tail = value.substring(start).trim();
            if (!tail.isEmpty()) lines.add(tail);
        }
        return lines;
    }

    public static void logHttpServer(ConfigValues config, WebChatLanguage language, CoreLogger logger) {
        if (config == null || logger == null) return;
        List<String> messages = new ArrayList<>();
        collectHttpServer(config, language, messages);
        for (String message : messages) logger.warn(message);
    }

    private static void collectHttpServer(ConfigValues config, WebChatLanguage language, List<String> messages) {
        String[] candidates = {
                config.corsOrigin, config.standaloneWebApiBaseUrl, config.apiBaseUrl,
                config.squaremapApiBaseUrl, config.dynmapApiBaseUrl, config.pl3xmapApiBaseUrl,
                config.liveAtlasApiBaseUrl, config.unminedApiBaseUrl, config.overviewerApiBaseUrl,
                config.uploadPublicBaseUrl, config.emojiPublicBaseUrl, config.emojiGameLinkPublicApiBaseUrl
        };
        String explicitHttp = "";
        for (String candidate : candidates) {
            String value = safe(candidate).trim();
            if (isPlainHttpUrl(value)) {
                explicitHttp = value;
                break;
            }
        }

        String host = safe(config.httpHost).trim();
        // A loopback-only listener is not exposed to the network. Any explicit public
        // http:// URL still warrants a warning, even when the listener itself is local.
        if (explicitHttp.isBlank() && isLoopbackHost(host)) return;

        Map<String,String> values = new LinkedHashMap<>();
        values.put("host", host);
        values.put("port", Integer.toString(config.httpPort));
        values.put("url", !explicitHttp.isBlank()
                ? explicitHttp
                : "http://" + displayHost(host.isBlank() ? "0.0.0.0" : host) + ":" + config.httpPort);
        String fallback = "[WARNING] KOKOTO WebChat is configured for HTTP ({url}). Login data and chat traffic can be read in plaintext on the network. Use HTTPS through a reverse proxy whenever possible.";
        messages.add(text(language, "security.httpServerWarning", fallback, values));
    }

    public static void logRelayPeers(ConfigValues config, WebChatLanguage language, CoreLogger logger) {
        if (config == null || logger == null) return;
        List<String> messages = new ArrayList<>();
        collectRelayPeers(config, language, messages);
        for (String message : messages) logger.warn(message);
    }

    private static void collectRelayPeers(ConfigValues config, WebChatLanguage language, List<String> messages) {
        if (config.serverRelayGroups == null) return;
        Set<String> warned = new LinkedHashSet<>();
        for (ConfigValues.RelayGroup group : config.serverRelayGroups) {
            if (group == null || group.peers == null) continue;
            for (ConfigValues.RelayPeer peer : group.peers) {
                if (peer == null || !peer.enabled) continue;
                String url = safe(peer.url).trim();
                if (!isPlainHttpUrl(url)) continue;
                String id = safe(peer.id).trim();
                if (!warned.add(id.toLowerCase(Locale.ROOT) + "\n" + url)) continue;
                Map<String,String> values = new LinkedHashMap<>();
                values.put("peer", id);
                values.put("url", url);
                String fallback = "[WARNING] Relay peer '{peer}' uses HTTP ({url}). Direct Relay is still allowed, but that peer is excluded from forwarding; HTTPS is strongly recommended.";
                messages.add(text(language, "security.relayHttpPeerWarning", fallback, values));
            }
        }
    }

    static boolean isLoopbackHost(String value) {
        String host = safe(value).trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            host = host.substring(1, host.length() - 1);
        }
        if ("localhost".equals(host) || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host)) return true;
        if (!host.startsWith("127.")) return false;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) return false;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    static boolean isPlainHttpUrl(String value) {
        String url = safe(value).trim().toLowerCase(Locale.ROOT);
        return url.startsWith("http://");
    }

    private static String displayHost(String value) {
        String host = safe(value).trim();
        if (host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))) return "[" + host + "]";
        return host;
    }

    private static String text(WebChatLanguage language, String key, String fallback, Map<String,String> values) {
        if (language != null) return language.text(key, fallback, values);
        String out = fallback;
        if (values != null) for (Map.Entry<String,String> entry : values.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", safe(entry.getValue()));
        }
        return out;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
