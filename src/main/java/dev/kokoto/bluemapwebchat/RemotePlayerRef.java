package dev.kokoto.bluemapwebchat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opaque participant key used to keep a player on another relayed server
 * distinct from a player with the same UUID on this server.
 */
public final class RemotePlayerRef {
    private static final String PREFIX = "remote~";
    private static final Pattern LEADING_SERVER_LABEL = Pattern.compile("^\\s*\\[([^]\\r\\n]{1,96})]\\s*");
    private static final Pattern TRAILING_SERVER_LABEL = Pattern.compile("\\s*\\[([^]\\r\\n]{1,96})]\\s*$");

    public final String key;
    public final String serverId;
    public final String playerUuid;

    private RemotePlayerRef(String key, String serverId, String playerUuid) {
        this.key = key;
        this.serverId = serverId;
        this.playerUuid = playerUuid;
    }

    public static String key(String serverId, String playerUuid) {
        String sid = normalizeServerId(serverId);
        String uuid = normalizePlayerUuid(playerUuid);
        if (sid.isBlank() || uuid.isBlank()) return "";
        // normalizeServerId only permits lowercase [a-z0-9._-], so the server
        // segment is already delimiter-safe and remains valid when the existing
        // storage layer lowercases participant identifiers.
        return PREFIX + sid + "~" + uuid;
    }

    public static RemotePlayerRef parse(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (!raw.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) return null;
        int separator = raw.indexOf('~', PREFIX.length());
        if (separator <= PREFIX.length() || separator >= raw.length() - 1) return null;
        String serverId = normalizeServerId(raw.substring(PREFIX.length(), separator));
        String playerUuid = normalizePlayerUuid(raw.substring(separator + 1));
        if (serverId.isBlank() || playerUuid.isBlank()) return null;
        return new RemotePlayerRef(key(serverId, playerUuid), serverId, playerUuid);
    }

    public static boolean isRemote(String value) {
        return parse(value) != null;
    }

    /**
     * Returns one canonical remote-player label.  Older builds stored the server
     * marker inside the display name and could append a second marker on every
     * web send (for example "Name [Main] [server1]").  This method accepts both
     * old prefix/suffix forms, removes all stored edge markers, keeps the friendly
     * server name when available, and emits exactly "[server] display".
     */
    public static String decorateDisplayName(String displayName, String username, String serverName, String serverId) {
        DisplayParts parts = displayParts(displayName, username, serverName, serverId);
        if (parts.serverLabel.isBlank()) return parts.baseName;
        return "[" + parts.serverLabel + "] " + parts.baseName;
    }

    /** Returns the player display name without a previously stored server marker. */
    public static String baseDisplayName(String displayName, String username, String serverName, String serverId) {
        return displayParts(displayName, username, serverName, serverId).baseName;
    }

    /** Returns the friendly server label inferred from explicit metadata or a legacy decorated name. */
    public static String displayServerName(String displayName, String serverName, String serverId) {
        return displayParts(displayName, "", serverName, serverId).serverLabel;
    }

    private static DisplayParts displayParts(String displayName, String username, String serverName, String serverId) {
        String rawDisplay = clean(displayName);
        String user = clean(username);
        String sid = normalizeServerId(serverId);
        String explicitServer = clean(serverName);
        String base = rawDisplay;
        String leadingLabel = "";
        String friendlyTrailingLabel = "";
        String idTrailingLabel = "";

        // New canonical values have exactly one leading server marker. Remove
        // only that first marker so a real display-name prefix such as
        // "[VIP] Name" remains intact in "[Main] [VIP] Name".
        Matcher leading = LEADING_SERVER_LABEL.matcher(base);
        if (leading.find()) {
            String candidate = clean(leading.group(1));
            boolean explicitMatches = !explicitServer.isBlank() && candidate.equalsIgnoreCase(explicitServer);
            boolean idMatches = sameServerLabel(candidate, sid);
            boolean legacyCanonical = explicitServer.isBlank() || sameServerLabel(explicitServer, sid);
            if (explicitMatches || idMatches || legacyCanonical) {
                leadingLabel = candidate;
                base = base.substring(leading.end()).trim();
            }
        }

        // Older builds appended the server marker. The broken retry path could
        // append the raw server id once more: "Name [Main] [server1]". Remove
        // the id marker first, then at most one friendly marker. Do not peel all
        // bracketed suffixes, because they may be part of the actual nickname.
        Matcher trailing = TRAILING_SERVER_LABEL.matcher(base);
        if (trailing.find()) {
            String candidate = clean(trailing.group(1));
            boolean idMatches = sameServerLabel(candidate, sid);
            boolean explicitMatches = !explicitServer.isBlank() && candidate.equalsIgnoreCase(explicitServer);
            boolean legacySuffix = leadingLabel.isBlank() && (explicitServer.isBlank() || sameServerLabel(explicitServer, sid));
            if (idMatches) {
                idTrailingLabel = candidate;
                base = base.substring(0, trailing.start()).trim();
                Matcher previous = TRAILING_SERVER_LABEL.matcher(base);
                if (previous.find()) {
                    String previousCandidate = clean(previous.group(1));
                    if (!previousCandidate.isBlank()) {
                        friendlyTrailingLabel = previousCandidate;
                        base = base.substring(0, previous.start()).trim();
                    }
                }
            } else if (explicitMatches || legacySuffix) {
                friendlyTrailingLabel = candidate;
                base = base.substring(0, trailing.start()).trim();
            }
        }

        if (base.isBlank()) base = !user.isBlank() ? user : (!rawDisplay.isBlank() ? rawDisplay : "Unknown");

        String serverLabel = "";
        if (!explicitServer.isBlank() && !sameServerLabel(explicitServer, sid)) serverLabel = explicitServer;
        if (serverLabel.isBlank() && !leadingLabel.isBlank() && !sameServerLabel(leadingLabel, sid)) serverLabel = leadingLabel;
        if (serverLabel.isBlank() && !friendlyTrailingLabel.isBlank() && !sameServerLabel(friendlyTrailingLabel, sid)) serverLabel = friendlyTrailingLabel;
        if (serverLabel.isBlank() && !explicitServer.isBlank()) serverLabel = explicitServer;
        if (serverLabel.isBlank() && !leadingLabel.isBlank()) serverLabel = leadingLabel;
        if (serverLabel.isBlank() && !friendlyTrailingLabel.isBlank()) serverLabel = friendlyTrailingLabel;
        if (serverLabel.isBlank() && !idTrailingLabel.isBlank()) serverLabel = idTrailingLabel;
        if (serverLabel.isBlank()) serverLabel = sid;

        return new DisplayParts(base, serverLabel);
    }

    private static boolean sameServerLabel(String value, String normalizedServerId) {
        if (value == null || value.isBlank() || normalizedServerId == null || normalizedServerId.isBlank()) return false;
        return normalizeServerId(value).equals(normalizedServerId);
    }

    private static String clean(String value) {
        return String.valueOf(value == null ? "" : value).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static final class DisplayParts {
        final String baseName;
        final String serverLabel;

        DisplayParts(String baseName, String serverLabel) {
            this.baseName = baseName == null ? "" : baseName;
            this.serverLabel = serverLabel == null ? "" : serverLabel;
        }
    }

    public static String normalizeServerId(String value) {
        String id = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-");
        while (id.contains("--")) id = id.replace("--", "-");
        while (id.startsWith("-")) id = id.substring(1);
        while (id.endsWith("-")) id = id.substring(0, id.length() - 1);
        return id.length() > 64 ? id.substring(0, 64) : id;
    }

    public static String normalizePlayerUuid(String value) {
        String uuid = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (uuid.length() > 80 || uuid.indexOf('~') >= 0 || uuid.indexOf(':') >= 0) return "";
        return uuid;
    }
}
