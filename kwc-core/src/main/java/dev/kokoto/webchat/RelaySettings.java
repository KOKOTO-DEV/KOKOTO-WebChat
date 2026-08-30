package dev.kokoto.webchat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, platform-neutral server relay v2 configuration. */
public final class RelaySettings {
    public final boolean enabled;
    public final String serverId;
    public final String serverName;
    public final int connectTimeoutSeconds;
    public final int requestTimeoutSeconds;
    public final int maxClockSkewSeconds;
    public final int dedupeSeconds;
    public final int maxHops;
    public final boolean gameChat;
    public final boolean webChat;
    public final boolean guestChat;
    public final boolean discordChat;
    public final boolean systemEvents;
    public final boolean deliverToWeb;
    public final boolean deliverToGame;
    public final String gameFormat;
    public final List<Group> groups;

    public RelaySettings(boolean enabled, String serverId, String serverName,
                         int connectTimeoutSeconds, int requestTimeoutSeconds, int maxClockSkewSeconds,
                         int dedupeSeconds, int maxHops, boolean gameChat, boolean webChat,
                         boolean guestChat, boolean discordChat, boolean systemEvents,
                         boolean deliverToWeb, boolean deliverToGame, String gameFormat, List<Group> groups) {
        this.enabled = enabled;
        this.serverId = nz(serverId);
        this.serverName = nz(serverName);
        this.connectTimeoutSeconds = Math.max(1, connectTimeoutSeconds);
        this.requestTimeoutSeconds = Math.max(1, requestTimeoutSeconds);
        this.maxClockSkewSeconds = Math.max(1, maxClockSkewSeconds);
        this.dedupeSeconds = Math.max(30, dedupeSeconds);
        this.maxHops = Math.max(1, Math.min(32, maxHops));
        this.gameChat = gameChat;
        this.webChat = webChat;
        this.guestChat = guestChat;
        this.discordChat = discordChat;
        this.systemEvents = systemEvents;
        this.deliverToWeb = deliverToWeb;
        this.deliverToGame = deliverToGame;
        this.gameFormat = nz(gameFormat);
        this.groups = Collections.unmodifiableList(new ArrayList<>(groups == null ? List.of() : groups));
    }

    private static String nz(String value) { return value == null ? "" : value; }

    public static final class Group {
        public final String id;
        public final String sharedSecret;
        public final boolean forwardingEnabled;
        public final List<Peer> peers;

        public Group(String id, String sharedSecret, boolean forwardingEnabled, List<Peer> peers) {
            this.id = nz(id);
            this.sharedSecret = nz(sharedSecret);
            this.forwardingEnabled = forwardingEnabled;
            this.peers = Collections.unmodifiableList(new ArrayList<>(peers == null ? List.of() : peers));
        }
    }

    public static final class Peer {
        public final String id;
        public final String url;
        public final boolean enabled;

        public Peer(String id, String url, boolean enabled) {
            this.id = nz(id);
            this.url = nz(url);
            this.enabled = enabled;
        }
    }
}
