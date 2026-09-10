package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * RelaySettings는 KWC 설정을 core가 사용할 수 있는 형태로 읽거나 보관하는 설정 계층이다.
 * RelaySettings is part of the configuration layer that reads or carries KWC settings in a core-friendly form.
 *
 * 설정 키를 바꿀 때는 canonical config, 과거 baseline, migration, 다국어 template, 문서 reference가 함께 움직여야 한다.
 * When changing a setting key, update canonical config, historical baselines, migration, localized templates, and documentation references together.
 */
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
    public final boolean eventAnnouncements;
    public final boolean deliverToWeb;
    public final boolean deliverToGame;
    public final String gameFormat;
    public final List<Group> groups;

    public RelaySettings(boolean enabled, String serverId, String serverName,
                         int connectTimeoutSeconds, int requestTimeoutSeconds, int maxClockSkewSeconds,
                         int dedupeSeconds, int maxHops, boolean gameChat, boolean webChat,
                         boolean guestChat, boolean discordChat, boolean systemEvents, boolean eventAnnouncements,
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
        this.eventAnnouncements = eventAnnouncements;
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

    public static final class DirectionPolicy {
        public final boolean enabled;
        public final boolean publicChat;
        public final boolean event;
        public final boolean dm;
        public final boolean profile;

        public DirectionPolicy(boolean enabled, boolean publicChat, boolean event, boolean dm, boolean profile) {
            this.enabled = enabled;
            this.publicChat = publicChat;
            this.event = event;
            this.dm = dm;
            this.profile = profile;
        }

        public static DirectionPolicy allowAll() { return new DirectionPolicy(true, true, true, true, true); }
    }

    public static final class Peer {
        public final String id;
        public final String url;
        public final boolean enabled;
        public final DirectionPolicy send;
        public final DirectionPolicy receive;

        public Peer(String id, String url, boolean enabled) {
            this(id, url, enabled, DirectionPolicy.allowAll(), DirectionPolicy.allowAll());
        }

        public Peer(String id, String url, boolean enabled, DirectionPolicy send, DirectionPolicy receive) {
            this.id = nz(id);
            this.url = nz(url);
            this.enabled = enabled;
            this.send = send == null ? DirectionPolicy.allowAll() : send;
            this.receive = receive == null ? DirectionPolicy.allowAll() : receive;
        }
    }
}
