package dev.kokoto.webchat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * Per-account immutable conversation snapshots.
 *
 * Attachments are deliberately not copied into this database. Snapshot bodies keep
 * the original KWC references/URLs; the browser resolves an image only if that
 * original is still available when the archive is viewed/exported.
 */
public final class ConversationArchiveStore implements AutoCloseable {
    public static final int MAX_ARCHIVES_PER_USER = 100;
    public static final int MAX_MESSAGES_PER_ARCHIVE = 1000;
    public static final int MAX_MESSAGES_PER_USER = 10_000;
    public static final int MAX_CONFIGURED_ARCHIVES_PER_USER = 1000;
    public static final int MAX_CONFIGURED_MESSAGES_PER_ARCHIVE = 10_000;
    public static final int MAX_CONFIGURED_MESSAGES_PER_USER = 100_000;

    public static final class SnapshotMessage {
        public String sourceMessageId = "";
        public long time;
        public String senderUuid = "";
        public String senderUsername = "";
        public String senderDisplayName = "";
        public String body = "";
        public String eventType = "";
        public String messageSource = "";
        public String role = "";
        public String serverId = "";
        public String serverName = "";
        public String replyToId = "";
        public String replyToSender = "";
        public String replyToPreview = "";
        /** Browser-safe aggregate public reaction projection captured at save time. */
        public String reactionsJson = "[]";

        public String toJson() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", nz(sourceMessageId));
            m.put("time", time);
            m.put("senderUsername", plainMinecraftLabel(senderUsername));
            m.put("senderDisplayName", plainMinecraftLabel(senderDisplayName));
            m.put("body", nz(body));
            m.put("eventType", nz(eventType));
            m.put("source", nz(messageSource));
            m.put("role", nz(role));
            m.put("serverId", nz(serverId));
            m.put("serverName", nz(serverName));
            m.put("replyToId", nz(replyToId));
            m.put("replyToSender", plainMinecraftLabel(replyToSender));
            m.put("replyToPreview", nz(replyToPreview));
            String base = JsonUtil.obj(m);
            String reactions = normalizeRawArray(reactionsJson);
            return base.substring(0, base.length() - 1) + ",\"reactions\":" + reactions + "}";
        }
    }

    public static final class Archive {
        public String id = "";
        public String ownerUuid = "";
        public String sourceType = "";
        public String sourceId = "";
        public String title = "";
        public long createdAt;
        public long firstMessageAt;
        public long lastMessageAt;
        public int messageCount;
        public final List<SnapshotMessage> messages = new ArrayList<>();

        public String metadataJson() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("sourceType", sourceType);
            m.put("sourceId", sourceId);
            m.put("title", title);
            m.put("createdAt", createdAt);
            m.put("firstMessageAt", firstMessageAt);
            m.put("lastMessageAt", lastMessageAt);
            m.put("messageCount", messageCount);
            return JsonUtil.obj(m);
        }

        public String toJson() {
            String meta = metadataJson();
            List<String> items = new ArrayList<>();
            for (SnapshotMessage message : messages) items.add(message.toJson());
            return meta.substring(0, meta.length() - 1) + ",\"messages\":[" + String.join(",", items) + "]}";
        }
    }

    public static final class SaveResult {
        public boolean ok;
        public String error = "";
        public Archive archive;
    }

    private final Path dbPath;
    private final CoreLogger logger;
    private final int maxArchivesPerUser;
    private final int maxMessagesPerArchive;
    private final int maxMessagesPerUser;
    private Connection connection;

    public ConversationArchiveStore(Path dataDirectory, CoreLogger logger) {
        this(dataDirectory, logger, MAX_ARCHIVES_PER_USER, MAX_MESSAGES_PER_ARCHIVE, MAX_MESSAGES_PER_USER);
    }

    public ConversationArchiveStore(Path dataDirectory, CoreLogger logger, int maxArchivesPerUser,
                                    int maxMessagesPerArchive, int maxMessagesPerUser) {
        Path base = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        this.dbPath = base.resolve("conversation-archives.db").normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.maxArchivesPerUser = Math.max(1, Math.min(MAX_CONFIGURED_ARCHIVES_PER_USER, maxArchivesPerUser));
        this.maxMessagesPerArchive = Math.max(1, Math.min(MAX_CONFIGURED_MESSAGES_PER_ARCHIVE, maxMessagesPerArchive));
        this.maxMessagesPerUser = Math.max(1, Math.min(MAX_CONFIGURED_MESSAGES_PER_USER, maxMessagesPerUser));
    }

    public int maxArchivesPerUser() { return maxArchivesPerUser; }
    public int maxMessagesPerArchive() { return maxMessagesPerArchive; }
    public int maxMessagesPerUser() { return maxMessagesPerUser; }

    public synchronized void open() {
        close();
        try {
            Files.createDirectories(dbPath.getParent());
            try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("CREATE TABLE IF NOT EXISTS conversation_archives (" +
                        "id TEXT PRIMARY KEY," +
                        "owner_uuid TEXT NOT NULL," +
                        "source_type TEXT NOT NULL," +
                        "source_id TEXT NOT NULL," +
                        "title TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL," +
                        "first_message_at INTEGER NOT NULL," +
                        "last_message_at INTEGER NOT NULL," +
                        "message_count INTEGER NOT NULL DEFAULT 0" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_conv_archives_owner ON conversation_archives(owner_uuid,created_at DESC)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_conv_archives_source ON conversation_archives(source_type,source_id)");
                st.execute("CREATE TABLE IF NOT EXISTS conversation_archive_messages (" +
                        "archive_id TEXT NOT NULL," +
                        "ordinal INTEGER NOT NULL," +
                        "source_message_id TEXT NOT NULL," +
                        "message_time INTEGER NOT NULL," +
                        "sender_uuid TEXT NOT NULL DEFAULT ''," +
                        "sender_username TEXT NOT NULL DEFAULT ''," +
                        "sender_display_name TEXT NOT NULL DEFAULT ''," +
                        "body TEXT NOT NULL DEFAULT ''," +
                        "event_type TEXT NOT NULL DEFAULT ''," +
                        "message_source TEXT NOT NULL DEFAULT ''," +
                        "role TEXT NOT NULL DEFAULT ''," +
                        "server_id TEXT NOT NULL DEFAULT ''," +
                        "server_name TEXT NOT NULL DEFAULT ''," +
                        "reply_to_id TEXT NOT NULL DEFAULT ''," +
                        "reply_to_sender TEXT NOT NULL DEFAULT ''," +
                        "reply_to_preview TEXT NOT NULL DEFAULT ''," +
                        "reactions_json TEXT NOT NULL DEFAULT '[]'," +
                        "PRIMARY KEY(archive_id,ordinal)," +
                        "FOREIGN KEY(archive_id) REFERENCES conversation_archives(id) ON DELETE CASCADE" +
                        ")");
                st.execute("CREATE INDEX IF NOT EXISTS idx_conv_archive_message_source ON conversation_archive_messages(source_message_id)");
            }
        } catch (Exception ex) {
            logger.warn("Failed to open conversation archive store: " + ex.getMessage());
            close();
        }
    }

    public synchronized boolean available() { return connection != null; }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    public synchronized SaveResult save(String ownerUuid, String sourceType, String sourceId, String title,
                                        List<SnapshotMessage> messages) {
        SaveResult result = new SaveResult();
        String owner = normalizeUuid(ownerUuid);
        String type = normalizeType(sourceType);
        String sid = clean(sourceId, 220);
        String safeTitle = cleanTitle(title);
        if (connection == null) { result.error = "archive_unavailable"; return result; }
        if (owner.isBlank() || type.isBlank() || sid.isBlank()) { result.error = "invalid_source"; return result; }
        if (messages == null || messages.isEmpty()) { result.error = "empty_range"; return result; }
        if (messages.size() > maxMessagesPerArchive) { result.error = "range_too_large"; return result; }
        if (safeTitle.isBlank()) safeTitle = "Saved conversation";
        try {
            if (countArchives(owner) >= maxArchivesPerUser) { result.error = "archive_quota"; return result; }
            if (countMessages(owner) + messages.size() > maxMessagesPerUser) { result.error = "archive_message_quota"; return result; }
            long createdAt = System.currentTimeMillis();
            long firstAt = messages.stream().mapToLong(m -> m == null ? 0L : m.time).filter(v -> v > 0).min().orElse(createdAt);
            long lastAt = messages.stream().mapToLong(m -> m == null ? 0L : m.time).filter(v -> v > 0).max().orElse(firstAt);
            String id = "arc-" + SecurityUtil.randomToken(12);
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO conversation_archives(id,owner_uuid,source_type,source_id,title,created_at,first_message_at,last_message_at,message_count) VALUES(?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, id); ps.setString(2, owner); ps.setString(3, type); ps.setString(4, sid); ps.setString(5, safeTitle);
                ps.setLong(6, createdAt); ps.setLong(7, firstAt); ps.setLong(8, lastAt); ps.setInt(9, messages.size()); ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO conversation_archive_messages(archive_id,ordinal,source_message_id,message_time,sender_uuid,sender_username,sender_display_name,body,event_type,message_source,role,server_id,server_name,reply_to_id,reply_to_sender,reply_to_preview,reactions_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                int ordinal = 0;
                for (SnapshotMessage raw : messages) {
                    SnapshotMessage m = raw == null ? new SnapshotMessage() : raw;
                    ps.setString(1, id); ps.setInt(2, ordinal++); ps.setString(3, clean(m.sourceMessageId, 240)); ps.setLong(4, m.time);
                    ps.setString(5, clean(m.senderUuid, 240)); ps.setString(6, clean(m.senderUsername, 240)); ps.setString(7, clean(m.senderDisplayName, 500));
                    ps.setString(8, nz(m.body)); ps.setString(9, clean(m.eventType, 80)); ps.setString(10, clean(m.messageSource, 80)); ps.setString(11, clean(m.role, 80));
                    ps.setString(12, clean(m.serverId, 160)); ps.setString(13, clean(m.serverName, 240)); ps.setString(14, clean(m.replyToId, 240));
                    ps.setString(15, clean(m.replyToSender, 500)); ps.setString(16, clean(m.replyToPreview, 1200)); ps.setString(17, normalizeRawArray(m.reactionsJson));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
            result.ok = true;
            result.archive = get(owner, id);
            return result;
        } catch (SQLException ex) {
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
            logger.warn("Failed to save conversation archive: " + ex.getMessage());
            result.error = "archive_write_failed";
            return result;
        }
    }

    public synchronized List<Archive> list(String ownerUuid, int limit) {
        List<Archive> out = new ArrayList<>();
        String owner = normalizeUuid(ownerUuid);
        if (connection == null || owner.isBlank()) return out;
        int max = Math.max(1, Math.min(limit <= 0 ? MAX_CONFIGURED_ARCHIVES_PER_USER : limit, MAX_CONFIGURED_ARCHIVES_PER_USER));
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id,owner_uuid,source_type,source_id,title,created_at,first_message_at,last_message_at,message_count FROM conversation_archives WHERE owner_uuid=? ORDER BY created_at DESC LIMIT ?")) {
            ps.setString(1, owner); ps.setInt(2, max);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(archiveFromRow(rs)); }
        } catch (SQLException ex) { logger.warn("Failed to list conversation archives: " + ex.getMessage()); }
        return out;
    }

    public synchronized Archive get(String ownerUuid, String archiveId) {
        String owner = normalizeUuid(ownerUuid);
        String id = clean(archiveId, 220);
        if (connection == null || owner.isBlank() || id.isBlank()) return null;
        Archive archive = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id,owner_uuid,source_type,source_id,title,created_at,first_message_at,last_message_at,message_count FROM conversation_archives WHERE owner_uuid=? AND id=?")) {
            ps.setString(1, owner); ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) archive = archiveFromRow(rs); }
            if (archive == null) return null;
            try (PreparedStatement ms = connection.prepareStatement(
                    "SELECT source_message_id,message_time,sender_uuid,sender_username,sender_display_name,body,event_type,message_source,role,server_id,server_name,reply_to_id,reply_to_sender,reply_to_preview,reactions_json FROM conversation_archive_messages WHERE archive_id=? ORDER BY ordinal ASC")) {
                ms.setString(1, id);
                try (ResultSet rs = ms.executeQuery()) {
                    while (rs.next()) archive.messages.add(messageFromRow(rs));
                }
            }
            archive.messageCount = archive.messages.size();
            return archive;
        } catch (SQLException ex) {
            logger.warn("Failed to read conversation archive: " + ex.getMessage());
            return null;
        }
    }

    public synchronized boolean rename(String ownerUuid, String archiveId, String title) {
        String owner = normalizeUuid(ownerUuid), id = clean(archiveId, 220), safe = cleanTitle(title);
        if (connection == null || owner.isBlank() || id.isBlank() || safe.isBlank()) return false;
        try (PreparedStatement ps = connection.prepareStatement("UPDATE conversation_archives SET title=? WHERE owner_uuid=? AND id=?")) {
            ps.setString(1, safe); ps.setString(2, owner); ps.setString(3, id); return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    public synchronized boolean delete(String ownerUuid, String archiveId) {
        String owner = normalizeUuid(ownerUuid), id = clean(archiveId, 220);
        if (connection == null || owner.isBlank() || id.isBlank()) return false;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM conversation_archives WHERE owner_uuid=? AND id=?")) {
            ps.setString(1, owner); ps.setString(2, id); return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    /** Administrator-forced source-message deletion cascades into every private snapshot. */
    public synchronized int removeSourceMessage(String sourceType, String sourceId, String sourceMessageId) {
        String type = normalizeType(sourceType), sid = clean(sourceId, 220), mid = clean(sourceMessageId, 240);
        if (connection == null || type.isBlank() || sid.isBlank() || mid.isBlank()) return 0;
        try {
            connection.setAutoCommit(false);
            int removed;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM conversation_archive_messages WHERE source_message_id=? AND archive_id IN (SELECT id FROM conversation_archives WHERE source_type=? AND source_id=?)")) {
                ps.setString(1, mid); ps.setString(2, type); ps.setString(3, sid); removed = ps.executeUpdate();
            }
            refreshAffectedArchiveMetadata(type, sid);
            connection.commit(); connection.setAutoCommit(true);
            return removed;
        } catch (SQLException ex) {
            try { connection.rollback(); connection.setAutoCommit(true); } catch (SQLException ignored) {}
            logger.warn("Failed to cascade deleted source message into conversation archives: " + ex.getMessage());
            return 0;
        }
    }

    /** Administrator-forced thread/room/history deletion removes snapshots from that source. */
    public synchronized int removeSource(String sourceType, String sourceId) {
        String type = normalizeType(sourceType), sid = clean(sourceId, 220);
        if (connection == null || type.isBlank() || sid.isBlank()) return 0;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM conversation_archives WHERE source_type=? AND source_id=?")) {
            ps.setString(1, type); ps.setString(2, sid); return ps.executeUpdate();
        } catch (SQLException ex) { logger.warn("Failed to remove source conversation archives: " + ex.getMessage()); return 0; }
    }

    private void refreshAffectedArchiveMetadata(String type, String sid) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM conversation_archives WHERE source_type=? AND source_id=?")) {
            ps.setString(1, type); ps.setString(2, sid);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
        }
        for (String id : ids) {
            int count = 0; long first = 0L, last = 0L;
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*),COALESCE(MIN(message_time),0),COALESCE(MAX(message_time),0) FROM conversation_archive_messages WHERE archive_id=?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { count = rs.getInt(1); first = rs.getLong(2); last = rs.getLong(3); } }
            }
            if (count <= 0) {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM conversation_archives WHERE id=?")) { ps.setString(1, id); ps.executeUpdate(); }
            } else {
                try (PreparedStatement ps = connection.prepareStatement("UPDATE conversation_archives SET message_count=?,first_message_at=?,last_message_at=? WHERE id=?")) {
                    ps.setInt(1, count); ps.setLong(2, first); ps.setLong(3, last); ps.setString(4, id); ps.executeUpdate();
                }
            }
        }
    }

    private int countArchives(String owner) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM conversation_archives WHERE owner_uuid=?")) {
            ps.setString(1, owner); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private int countMessages(String owner) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(SUM(message_count),0) FROM conversation_archives WHERE owner_uuid=?")) {
            ps.setString(1, owner); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    private Archive archiveFromRow(ResultSet rs) throws SQLException {
        Archive a = new Archive();
        a.id = nz(rs.getString(1)); a.ownerUuid = nz(rs.getString(2)); a.sourceType = nz(rs.getString(3)); a.sourceId = nz(rs.getString(4));
        a.title = nz(rs.getString(5)); a.createdAt = rs.getLong(6); a.firstMessageAt = rs.getLong(7); a.lastMessageAt = rs.getLong(8); a.messageCount = rs.getInt(9);
        return a;
    }

    private SnapshotMessage messageFromRow(ResultSet rs) throws SQLException {
        SnapshotMessage m = new SnapshotMessage();
        m.sourceMessageId = nz(rs.getString(1)); m.time = rs.getLong(2); m.senderUuid = nz(rs.getString(3)); m.senderUsername = plainMinecraftLabel(rs.getString(4));
        m.senderDisplayName = plainMinecraftLabel(rs.getString(5)); m.body = nz(rs.getString(6)); m.eventType = nz(rs.getString(7)); m.messageSource = nz(rs.getString(8));
        m.role = nz(rs.getString(9)); m.serverId = nz(rs.getString(10)); m.serverName = nz(rs.getString(11)); m.replyToId = nz(rs.getString(12));
        m.replyToSender = plainMinecraftLabel(rs.getString(13)); m.replyToPreview = nz(rs.getString(14)); m.reactionsJson = normalizeRawArray(rs.getString(15));
        return m;
    }

    private static String normalizeType(String value) {
        String v = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        return Set.of("public", "dm", "group").contains(v) ? v : "";
    }

    private static String normalizeUuid(String value) { return clean(value, 240).toLowerCase(Locale.ROOT); }
    private static String cleanTitle(String value) { return clean(value, 160); }
    private static String clean(String value, int max) {
        String s = String.valueOf(value == null ? "" : value).replace("\u0000", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
    private static String nz(String value) { return value == null ? "" : value; }

    /**
     * Archive identity fields are projected as plain text. Normalize the common
     * persisted/escaped section-sign forms as well so old snapshots cannot leak
     * literal Minecraft formatting sequences such as §x§b§7§a§e§b§c§l.
     */
    private static String plainMinecraftLabel(String value) {
        String text = nz(value);
        for (int i = 0; i < 3; i++) {
            String next = text
                    .replace("\\u00a7", "§").replace("\\u00A7", "§")
                    .replace("\\xA7", "§").replace("\\xa7", "§")
                    .replace("Â§", "§")
                    .replaceAll("(?i)&amp;", "&")
                    .replaceAll("(?i)&#0*167;?", "§")
                    .replaceAll("(?i)&#x0*a7;?", "§")
                    .replaceAll("(?i)&sect;?", "§");
            if (next.equals(text)) break;
            text = next;
        }
        String plain = LegacyText.stripColor(LegacyText.translateAlternateColorCodes('&', text));
        return nz(plain).replaceAll("\\s+", " ").trim();
    }
    private static String normalizeRawArray(String value) {
        String v = String.valueOf(value == null ? "[]" : value).trim();
        return v.startsWith("[") && v.endsWith("]") && v.length() <= 100_000 ? v : "[]";
    }
}
