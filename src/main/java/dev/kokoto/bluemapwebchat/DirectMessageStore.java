package dev.kokoto.bluemapwebchat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.util.*;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class DirectMessageStore {
    private final BlueMapWebChatPlugin plugin;
    private Connection connection;
    private String storageMode = "sqlite";
    private File jsonlFile;
    private long jsonlNextMessageId = 1L;
    private final Map<Long, JsonlMessage> jsonlMessages = new LinkedHashMap<>();
    private final Map<String, Long> jsonlLastRead = new HashMap<>();
    private final Set<String> jsonlHiddenMessages = new HashSet<>();

    public DirectMessageStore(BlueMapWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void open() {
        close();
        ConfigValues c = plugin.configValues();
        if (c == null || !c.directMessageEnabled) return;
        storageMode = c.directMessageStorage == null || c.directMessageStorage.isBlank() ? "sqlite" : c.directMessageStorage.trim().toLowerCase(Locale.ROOT);
        if ("jsonl".equals(storageMode)) {
            openJsonl(c);
            return;
        }
        try {
            File file = resolveFile(c.directMessageSqliteFile == null || c.directMessageSqliteFile.isBlank() ? "direct-messages.db" : c.directMessageSqliteFile);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
            initSchema();
            cleanup();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to open direct message SQLite store: " + ex.getMessage());
            close();
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
        jsonlFile = null;
        jsonlMessages.clear();
        jsonlLastRead.clear();
        jsonlHiddenMessages.clear();
    }

    public boolean available() {
        return connection != null || ("jsonl".equals(storageMode) && jsonlFile != null);
    }

    private boolean jsonlMode() {
        return "jsonl".equals(storageMode);
    }

    private File resolveFile(String configured) {
        File file = new File(configured == null || configured.isBlank() ? "direct-messages.db" : configured);
        if (!file.isAbsolute()) file = new File(plugin.getDataFolder(), configured);
        return file;
    }

    private File resolveJsonlFile(String configured) {
        File file = new File(configured == null || configured.isBlank() ? "direct-messages.jsonl" : configured);
        if (!file.isAbsolute()) file = new File(plugin.getDataFolder(), configured);
        return file;
    }

    private void openJsonl(ConfigValues c) {
        jsonlFile = resolveJsonlFile(c.directMessageJsonlFile == null || c.directMessageJsonlFile.isBlank() ? "direct-messages.jsonl" : c.directMessageJsonlFile);
        File parent = jsonlFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        jsonlMessages.clear();
        jsonlLastRead.clear();
        jsonlHiddenMessages.clear();
        jsonlNextMessageId = 1L;
        if (jsonlFile.exists()) {
            try {
                for (String line : Files.readAllLines(jsonlFile.toPath(), StandardCharsets.UTF_8)) {
                    loadJsonlLine(line);
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to load direct message JSONL store: " + ex.getMessage());
            }
        }
        cleanup();
        plugin.getLogger().info("Using JSONL direct message store: " + jsonlFile.getAbsolutePath());
    }

    private void loadJsonlLine(String line) {
        if (line == null || line.isBlank()) return;
        Map<String, String> m = JsonUtil.parseFlatObject(line);
        String type = String.valueOf(m.getOrDefault("type", "")).trim().toLowerCase(Locale.ROOT);
        try {
            if ("message".equals(type)) {
                JsonlMessage msg = new JsonlMessage();
                msg.id = parseLong(m.get("id"), 0L);
                msg.threadId = String.valueOf(m.getOrDefault("threadId", "")).trim();
                msg.senderUuid = normalizeUuid(m.get("senderUuid"));
                msg.body = String.valueOf(m.getOrDefault("body", ""));
                msg.createdAt = parseLong(m.get("createdAt"), System.currentTimeMillis());
                msg.hidden = Boolean.parseBoolean(String.valueOf(m.getOrDefault("hidden", "false")));
                if (msg.id > 0 && !msg.threadId.isBlank() && !msg.senderUuid.isBlank()) {
                    jsonlMessages.put(msg.id, msg);
                    jsonlNextMessageId = Math.max(jsonlNextMessageId, msg.id + 1L);
                }
            } else if ("hide_message".equals(type)) {
                long messageId = parseLong(m.get("messageId"), 0L);
                String user = normalizeUuid(m.get("userUuid"));
                if (messageId > 0 && !user.isBlank()) {
                    String key = jsonlHiddenKey(user, messageId);
                    if (Boolean.parseBoolean(String.valueOf(m.getOrDefault("hidden", "true")))) jsonlHiddenMessages.add(key);
                    else jsonlHiddenMessages.remove(key);
                }
            } else if ("read".equals(type)) {
                String threadId = String.valueOf(m.getOrDefault("threadId", "")).trim();
                String user = normalizeUuid(m.get("userUuid"));
                long lastRead = parseLong(m.get("lastReadMessageId"), 0L);
                if (!threadId.isBlank() && !user.isBlank()) jsonlLastRead.put(jsonlThreadStateKey(threadId, user), Math.max(0L, lastRead));
            }
        } catch (RuntimeException ignored) {
        }
    }

    private long parseLong(String value, long fallback) {
        try { return Long.parseLong(String.valueOf(value == null ? "" : value).trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }

    private void appendJsonlEvent(Map<String, ?> values) throws IOException {
        if (jsonlFile == null) throw new IOException("direct message JSONL file is not open");
        File parent = jsonlFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        Files.writeString(jsonlFile.toPath(), JsonUtil.obj(values) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void rewriteJsonl() throws IOException {
        if (jsonlFile == null) return;
        File parent = jsonlFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        List<String> lines = new ArrayList<>();
        for (JsonlMessage msg : jsonlMessages.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "message");
            m.put("id", msg.id);
            m.put("threadId", msg.threadId);
            m.put("senderUuid", msg.senderUuid);
            m.put("body", msg.body);
            m.put("createdAt", msg.createdAt);
            m.put("hidden", msg.hidden);
            lines.add(JsonUtil.obj(m));
        }
        for (String key : jsonlHiddenMessages) {
            String[] parts = key.split("\\|", 2);
            if (parts.length != 2) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "hide_message");
            m.put("userUuid", parts[0]);
            m.put("messageId", parseLong(parts[1], 0L));
            m.put("hidden", true);
            lines.add(JsonUtil.obj(m));
        }
        for (Map.Entry<String, Long> e : jsonlLastRead.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            if (parts.length != 2) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "read");
            m.put("threadId", parts[0]);
            m.put("userUuid", parts[1]);
            m.put("lastReadMessageId", e.getValue());
            lines.add(JsonUtil.obj(m));
        }
        Files.write(jsonlFile.toPath(), lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static final class JsonlMessage {
        long id;
        String threadId = "";
        String senderUuid = "";
        String body = "";
        long createdAt;
        boolean hidden;
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS dm_threads (" +
                    "id TEXT PRIMARY KEY," +
                    "user_a_uuid TEXT NOT NULL," +
                    "user_b_uuid TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL," +
                    "locked INTEGER NOT NULL DEFAULT 0," +
                    "retention_exempt INTEGER NOT NULL DEFAULT 0" +
                    ")");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_dm_threads_pair ON dm_threads(user_a_uuid, user_b_uuid)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_dm_threads_user_a ON dm_threads(user_a_uuid, updated_at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_dm_threads_user_b ON dm_threads(user_b_uuid, updated_at)");
            addColumnIfMissing(st, "dm_threads", "locked", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(st, "dm_threads", "retention_exempt", "INTEGER NOT NULL DEFAULT 0");
            st.execute("CREATE TABLE IF NOT EXISTS dm_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "thread_id TEXT NOT NULL," +
                    "sender_uuid TEXT NOT NULL," +
                    "body TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "hidden INTEGER NOT NULL DEFAULT 0" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_dm_messages_thread ON dm_messages(thread_id, id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_dm_messages_created ON dm_messages(created_at)");
            st.execute("CREATE TABLE IF NOT EXISTS dm_thread_state (" +
                    "thread_id TEXT NOT NULL," +
                    "user_uuid TEXT NOT NULL," +
                    "last_read_message_id INTEGER NOT NULL DEFAULT 0," +
                    "hidden INTEGER NOT NULL DEFAULT 0," +
                    "muted INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(thread_id, user_uuid)" +
                    ")");
            st.execute("CREATE TABLE IF NOT EXISTS dm_message_state (" +
                    "message_id INTEGER NOT NULL," +
                    "user_uuid TEXT NOT NULL," +
                    "hidden INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(message_id, user_uuid)" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_dm_message_state_user ON dm_message_state(user_uuid, hidden)");
        }
    }

    private void addColumnIfMissing(Statement st, String table, String column, String definition) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return;
            }
        }
        st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    public synchronized void cleanup() {
        if (jsonlMode()) {
            cleanupJsonl();
            return;
        }
        if (connection == null) return;
        ConfigValues c = plugin.configValues();
        try {
            if (c != null && c.directMessageRetentionDays > 0) {
                long cutoff = System.currentTimeMillis() - c.directMessageRetentionDays * 24L * 60L * 60L * 1000L;
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM dm_messages WHERE created_at < ? AND thread_id NOT IN (SELECT id FROM dm_threads WHERE retention_exempt=1)")) {
                    ps.setLong(1, cutoff);
                    ps.executeUpdate();
                }
            }
            if (c != null && c.directMessageMaxMessagesPerThread > 0) {
                List<String> ids = new ArrayList<>();
                try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM dm_threads")) {
                    while (rs.next()) ids.add(rs.getString(1));
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM dm_messages WHERE thread_id=? AND id NOT IN (SELECT id FROM dm_messages WHERE thread_id=? ORDER BY id DESC LIMIT ?)")) {
                    for (String id : ids) {
                        ps.setString(1, id);
                        ps.setString(2, id);
                        ps.setInt(3, c.directMessageMaxMessagesPerThread);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM dm_message_state WHERE message_id NOT IN (SELECT id FROM dm_messages)");
                st.executeUpdate("DELETE FROM dm_threads WHERE retention_exempt=0 AND id NOT IN (SELECT DISTINCT thread_id FROM dm_messages)");
                st.executeUpdate("DELETE FROM dm_thread_state WHERE thread_id NOT IN (SELECT id FROM dm_threads)");
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to cleanup direct messages: " + ex.getMessage());
        }
    }

    private String threadId(String uuidA, String uuidB) {
        String a = normalizeUuid(uuidA);
        String b = normalizeUuid(uuidB);
        if (a.compareToIgnoreCase(b) <= 0) return a + ":" + b;
        return b + ":" + a;
    }

    private String[] orderedPair(String uuidA, String uuidB) {
        String a = normalizeUuid(uuidA);
        String b = normalizeUuid(uuidB);
        if (a.compareToIgnoreCase(b) <= 0) return new String[]{a, b};
        return new String[]{b, a};
    }

    private String normalizeUuid(String uuid) {
        return String.valueOf(uuid == null ? "" : uuid).trim().toLowerCase(Locale.ROOT);
    }

    private String jsonlHiddenKey(String userUuid, long messageId) {
        return normalizeUuid(userUuid) + "|" + messageId;
    }

    private String jsonlThreadStateKey(String threadId, String userUuid) {
        return String.valueOf(threadId == null ? "" : threadId).trim() + "|" + normalizeUuid(userUuid);
    }

    private String[] threadParticipants(String threadId) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        int idx = tid.indexOf(':');
        if (idx <= 0 || idx >= tid.length() - 1) return new String[]{"", ""};
        return new String[]{normalizeUuid(tid.substring(0, idx)), normalizeUuid(tid.substring(idx + 1))};
    }

    private boolean jsonlIsParticipant(String threadId, String userUuid) {
        String user = normalizeUuid(userUuid);
        String[] pair = threadParticipants(threadId);
        return !user.isBlank() && (user.equals(pair[0]) || user.equals(pair[1]));
    }

    private String jsonlOtherParticipant(String threadId, String userUuid) {
        String user = normalizeUuid(userUuid);
        String[] pair = threadParticipants(threadId);
        if (user.equals(pair[0])) return pair[1];
        if (user.equals(pair[1])) return pair[0];
        return "";
    }

    private boolean jsonlVisibleFor(JsonlMessage msg, String userUuid) {
        if (msg == null || msg.hidden) return false;
        String user = normalizeUuid(userUuid);
        return jsonlIsParticipant(msg.threadId, user) && !jsonlHiddenMessages.contains(jsonlHiddenKey(user, msg.id));
    }

    private void cleanupJsonl() {
        ConfigValues c = plugin.configValues();
        boolean changed = false;
        Set<Long> removeIds = new HashSet<>();
        if (c != null && c.directMessageRetentionDays > 0) {
            long cutoff = System.currentTimeMillis() - c.directMessageRetentionDays * 24L * 60L * 60L * 1000L;
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg.createdAt < cutoff) removeIds.add(msg.id);
            }
        }
        if (c != null && c.directMessageMaxMessagesPerThread > 0) {
            Map<String, List<JsonlMessage>> byThread = new HashMap<>();
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg.hidden) continue;
                byThread.computeIfAbsent(msg.threadId, k -> new ArrayList<>()).add(msg);
            }
            for (List<JsonlMessage> list : byThread.values()) {
                list.sort(Comparator.comparingLong((JsonlMessage m) -> m.id).reversed());
                for (int i = c.directMessageMaxMessagesPerThread; i < list.size(); i++) removeIds.add(list.get(i).id);
            }
        }
        for (Long id : removeIds) {
            if (jsonlMessages.remove(id) != null) changed = true;
        }
        if (!removeIds.isEmpty()) {
            changed |= jsonlHiddenMessages.removeIf(key -> {
                String[] parts = key.split("\\|", 2);
                return parts.length == 2 && removeIds.contains(parseLong(parts[1], 0L));
            });
        }
        Set<String> liveThreads = new HashSet<>();
        for (JsonlMessage msg : jsonlMessages.values()) if (!msg.hidden) liveThreads.add(msg.threadId);
        changed |= jsonlLastRead.keySet().removeIf(key -> {
            String[] parts = key.split("\\|", 2);
            return parts.length != 2 || !liveThreads.contains(parts[0]);
        });
        if (changed) {
            try { rewriteJsonl(); }
            catch (IOException ex) { plugin.getLogger().warning("Failed to rewrite direct message JSONL store: " + ex.getMessage()); }
        }
    }

    private DirectMessageMessage jsonlToMessage(JsonlMessage msg) {
        if (msg == null) return null;
        DirectMessageMessage m = new DirectMessageMessage();
        m.id = msg.id;
        m.threadId = msg.threadId;
        m.senderUuid = msg.senderUuid;
        PlayerIdentity sender = currentPlayerIdentity(m.senderUuid);
        m.senderUsername = sender == null ? "" : sender.username;
        m.senderDisplayName = sender == null ? "" : sender.displayName;
        m.body = msg.body;
        m.createdAt = msg.createdAt;
        return m;
    }

    private boolean isParticipant(String threadId, String userUuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM dm_threads WHERE id=? AND (user_a_uuid=? OR user_b_uuid=?)")) {
            ps.setString(1, threadId);
            ps.setString(2, normalizeUuid(userUuid));
            ps.setString(3, normalizeUuid(userUuid));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void ensureThread(String uuidA, String uuidB, long now) throws SQLException {
        String[] pair = orderedPair(uuidA, uuidB);
        String id = threadId(uuidA, uuidB);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO dm_threads(id,user_a_uuid,user_b_uuid,created_at,updated_at,locked,retention_exempt) VALUES(?,?,?,?,?,0,0)")) {
            ps.setString(1, id);
            ps.setString(2, pair[0]);
            ps.setString(3, pair[1]);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
        ensureState(id, pair[0]);
        ensureState(id, pair[1]);
    }

    private void ensureState(String threadId, String userUuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO dm_thread_state(thread_id,user_uuid,last_read_message_id,hidden,muted) VALUES(?,?,0,0,0)")) {
            ps.setString(1, threadId);
            ps.setString(2, normalizeUuid(userUuid));
            ps.executeUpdate();
        }
    }

    public static class SendResult {
        public boolean ok;
        public String error = "";
        public DirectMessageThread thread;
        public DirectMessageMessage message;
        public String targetUuid = "";
    }

    public synchronized SendResult send(String senderUuid, String targetUuid, String body) {
        if (jsonlMode()) return jsonlSend(senderUuid, targetUuid, body);
        SendResult result = new SendResult();
        if (connection == null) {
            result.error = "dm_unavailable";
            return result;
        }
        String sender = normalizeUuid(senderUuid);
        String target = normalizeUuid(targetUuid);
        if (sender.isBlank() || target.isBlank()) {
            result.error = "invalid_player";
            return result;
        }
        if (sender.equals(target)) {
            result.error = "self_message";
            return result;
        }
        String text = body == null ? "" : body;
        if (text.isBlank()) {
            result.error = "empty_message";
            return result;
        }
        long now = System.currentTimeMillis();
        String threadId = threadId(sender, target);
        try {
            ensureThread(sender, target, now);
            if (isThreadLocked(threadId)) {
                result.error = "thread_locked";
                return result;
            }
            long messageId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO dm_messages(thread_id,sender_uuid,body,created_at,hidden) VALUES(?,?,?,?,0)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, threadId);
                ps.setString(2, sender);
                ps.setString(3, text);
                ps.setLong(4, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    messageId = keys.next() ? keys.getLong(1) : 0L;
                }
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE dm_threads SET updated_at=? WHERE id=?")) {
                ps.setLong(1, now);
                ps.setString(2, threadId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE dm_thread_state SET hidden=0 WHERE thread_id=?")) {
                ps.setString(1, threadId);
                ps.executeUpdate();
            }
            markRead(threadId, sender);
            result.ok = true;
            result.targetUuid = target;
            result.message = readMessage(messageId);
            result.thread = listThreads(sender, 200).stream().filter(t -> threadId.equals(t.id)).findFirst().orElse(null);
            cleanup();
            return result;
        } catch (SQLException ex) {
            result.error = "sql_error";
            plugin.getLogger().warning("Failed to send direct message: " + ex.getMessage());
            return result;
        }
    }

    private SendResult jsonlSend(String senderUuid, String targetUuid, String body) {
        SendResult result = new SendResult();
        if (jsonlFile == null) {
            result.error = "dm_unavailable";
            return result;
        }
        String sender = normalizeUuid(senderUuid);
        String target = normalizeUuid(targetUuid);
        if (sender.isBlank() || target.isBlank()) {
            result.error = "invalid_player";
            return result;
        }
        if (sender.equals(target)) {
            result.error = "self_message";
            return result;
        }
        String text = body == null ? "" : body;
        if (text.isBlank()) {
            result.error = "empty_message";
            return result;
        }
        long now = System.currentTimeMillis();
        JsonlMessage msg = new JsonlMessage();
        msg.id = jsonlNextMessageId++;
        msg.threadId = threadId(sender, target);
        msg.senderUuid = sender;
        msg.body = text;
        msg.createdAt = now;
        msg.hidden = false;
        jsonlMessages.put(msg.id, msg);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "message");
            m.put("id", msg.id);
            m.put("threadId", msg.threadId);
            m.put("senderUuid", msg.senderUuid);
            m.put("body", msg.body);
            m.put("createdAt", msg.createdAt);
            m.put("hidden", false);
            appendJsonlEvent(m);
        } catch (IOException ex) {
            jsonlMessages.remove(msg.id);
            result.error = "io_error";
            plugin.getLogger().warning("Failed to append direct message JSONL store: " + ex.getMessage());
            return result;
        }
        markRead(msg.threadId, sender);
        result.ok = true;
        result.targetUuid = target;
        result.message = jsonlToMessage(msg);
        result.thread = listThreads(sender, 200).stream().filter(t -> msg.threadId.equals(t.id)).findFirst().orElse(null);
        cleanup();
        return result;
    }

    private synchronized DirectMessageMessage readMessage(long id) throws SQLException {
        if (connection == null) return null;
        try (PreparedStatement ps = connection.prepareStatement("SELECT id,thread_id,sender_uuid,body,created_at FROM dm_messages WHERE id=? AND hidden=0")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return messageFromResult(rs);
            }
        }
    }

    public synchronized String threadIdForMessage(String userUuid, long messageId) {
        if (jsonlMode()) return jsonlThreadIdForMessage(userUuid, messageId);
        if (connection == null) return "";
        String user = normalizeUuid(userUuid);
        if (user.isBlank() || messageId <= 0) return "";
        try {
            String threadId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT thread_id FROM dm_messages WHERE id=? AND hidden=0 " +
                            "AND id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1)")) {
                ps.setLong(1, messageId);
                ps.setString(2, user);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "";
                    threadId = rs.getString(1);
                }
            }
            return isParticipant(threadId, user) ? threadId : "";
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to resolve direct message thread: " + ex.getMessage());
            return "";
        }
    }

    private String jsonlThreadIdForMessage(String userUuid, long messageId) {
        String user = normalizeUuid(userUuid);
        JsonlMessage msg = jsonlMessages.get(messageId);
        if (user.isBlank() || msg == null || !jsonlVisibleFor(msg, user)) return "";
        return msg.threadId;
    }


    public synchronized List<String> adminThreadSummaries(int limit) {
        if (jsonlMode()) return jsonlAdminThreadSummaries(limit);
        List<String> out = new ArrayList<>();
        if (connection == null) return out;
        int max = limit <= 0 ? 200 : Math.min(limit, 500);
        // Use the latest surviving message timestamp as the retention base.
        // t.updated_at is thread metadata and can be refreshed by non-message actions
        // or old schema data; using it made retention estimates jump after reload.
        String sql = "SELECT t.id,t.user_a_uuid,t.user_b_uuid,t.updated_at," +
                "COUNT(m.id)," +
                "COALESCE(SUM(LENGTH(m.body)),0)," +
                "COALESCE(MAX(m.created_at),0)," +
                "t.locked,t.retention_exempt " +
                "FROM dm_threads t JOIN dm_messages m ON m.thread_id=t.id AND m.hidden=0 " +
                "GROUP BY t.id,t.user_a_uuid,t.user_b_uuid,t.updated_at,t.locked,t.retention_exempt " +
                "ORDER BY COALESCE(MAX(m.created_at), t.updated_at) DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(adminThreadSummaryJson(rs.getString(1), normalizeUuid(rs.getString(2)), normalizeUuid(rs.getString(3)), rs.getLong(4), rs.getInt(5), rs.getLong(6), rs.getLong(7), rs.getInt(8) != 0, rs.getInt(9) != 0));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to list direct message admin summaries: " + ex.getMessage());
        }
        return out;
    }

    private List<String> jsonlAdminThreadSummaries(int limit) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Long> bytes = new LinkedHashMap<>();
        Map<String, Long> updated = new LinkedHashMap<>();
        for (JsonlMessage msg : jsonlMessages.values()) {
            if (msg.hidden || msg.threadId == null || msg.threadId.isBlank()) continue;
            counts.computeIfAbsent(msg.threadId, k -> new int[]{0})[0]++;
            bytes.put(msg.threadId, bytes.getOrDefault(msg.threadId, 0L) + String.valueOf(msg.body == null ? "" : msg.body).getBytes(StandardCharsets.UTF_8).length);
            updated.put(msg.threadId, Math.max(updated.getOrDefault(msg.threadId, 0L), msg.createdAt));
        }
        List<Map.Entry<String, Long>> order = new ArrayList<>(updated.entrySet());
        order.sort((a,b) -> Long.compare(b.getValue(), a.getValue()));
        int max = limit <= 0 ? 200 : Math.min(limit, 500);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : order) {
            if (out.size() >= max) break;
            String[] parts = e.getKey().split(":", 2);
            String a = parts.length > 0 ? normalizeUuid(parts[0]) : "";
            String b = parts.length > 1 ? normalizeUuid(parts[1]) : "";
            out.add(adminThreadSummaryJson(e.getKey(), a, b, e.getValue(), counts.getOrDefault(e.getKey(), new int[]{0})[0], bytes.getOrDefault(e.getKey(), 0L), e.getValue(), false, false));
        }
        return out;
    }

    private String adminThreadSummaryJson(String id, String userA, String userB, long updatedAt, int messageCount, long storageBytes, long latestMessageAt, boolean locked, boolean retentionExempt) {
        PlayerIdentity a = currentPlayerIdentity(userA);
        PlayerIdentity b = currentPlayerIdentity(userB);
        ConfigValues c = plugin.configValues();
        int retentionDays = c == null ? 0 : Math.max(0, c.directMessageRetentionDays);
        long retentionBaseAt = latestMessageAt > 0L ? latestMessageAt : updatedAt;
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("userAUuid", userA);
        m.put("userBUuid", userB);
        m.put("userAUsername", a == null ? "" : String.valueOf(a.username == null ? "" : a.username));
        m.put("userADisplayName", a == null ? "" : String.valueOf(a.displayName == null ? "" : a.displayName));
        m.put("userBUsername", b == null ? "" : String.valueOf(b.username == null ? "" : b.username));
        m.put("userBDisplayName", b == null ? "" : String.valueOf(b.displayName == null ? "" : b.displayName));
        m.put("userALabel", labelForIdentity(a, userA));
        m.put("userBLabel", labelForIdentity(b, userB));
        m.put("updatedAt", updatedAt);
        m.put("latestMessageAt", latestMessageAt);
        m.put("retentionBaseAt", retentionBaseAt);
        m.put("retentionDays", retentionDays);
        m.put("retentionExpiresAt", retentionDays > 0 && retentionBaseAt > 0L ? retentionBaseAt + retentionDays * 24L * 60L * 60L * 1000L : 0L);
        m.put("messageCount", messageCount);
        m.put("storageBytes", storageBytes);
        m.put("locked", locked);
        m.put("retentionExempt", retentionExempt);
        m.put("adminOnly", true);
        return JsonUtil.obj(m);
    }

    public synchronized List<String> messageBodiesForThread(String threadId) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        List<String> out = new ArrayList<>();
        if (tid.isBlank()) return out;
        if (jsonlMode()) {
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg != null && tid.equals(msg.threadId) && !msg.hidden) out.add(String.valueOf(msg.body == null ? "" : msg.body));
            }
            return out;
        }
        if (connection == null) return out;
        try (PreparedStatement ps = connection.prepareStatement("SELECT body FROM dm_messages WHERE thread_id=? AND hidden=0")) {
            ps.setString(1, tid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(String.valueOf(rs.getString(1) == null ? "" : rs.getString(1)));
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to list direct message bodies: " + ex.getMessage());
        }
        return out;
    }

    public synchronized Set<String> participantUuidsForThread(String threadId) {
        Set<String> out = new LinkedHashSet<>();
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        if (tid.isBlank()) return out;
        String[] parts = tid.split(":", 2);
        if (parts.length > 0) {
            String a = normalizeUuid(parts[0]);
            if (!a.isBlank()) out.add(a);
        }
        if (parts.length > 1) {
            String b = normalizeUuid(parts[1]);
            if (!b.isBlank()) out.add(b);
        }
        if (connection != null) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT user_a_uuid,user_b_uuid FROM dm_threads WHERE id=?")) {
                ps.setString(1, tid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String a = normalizeUuid(rs.getString(1));
                        String b = normalizeUuid(rs.getString(2));
                        if (!a.isBlank()) out.add(a);
                        if (!b.isBlank()) out.add(b);
                    }
                }
            } catch (SQLException ignored) {}
        }
        return out;
    }

    public synchronized boolean deleteThread(String threadId) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        if (tid.isBlank()) return false;
        if (jsonlMode()) {
            boolean changed = false;
            Iterator<Map.Entry<Long, JsonlMessage>> it = jsonlMessages.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, JsonlMessage> e = it.next();
                JsonlMessage msg = e.getValue();
                if (msg != null && tid.equals(msg.threadId)) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                jsonlHiddenMessages.removeIf(key -> {
                    String[] parts = key.split("\\|", 2);
                    if (parts.length != 2) return false;
                    long messageId = parseLong(parts[1], 0L);
                    return messageId > 0 && !jsonlMessages.containsKey(messageId);
                });
                jsonlLastRead.keySet().removeIf(key -> key.startsWith(tid + "|"));
                try { rewriteJsonl(); } catch (IOException ex) { plugin.getLogger().warning("Failed to rewrite direct message JSONL after deleting thread: " + ex.getMessage()); }
            }
            return changed;
        }
        if (connection == null) return false;
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM dm_message_state WHERE message_id IN (SELECT id FROM dm_messages WHERE thread_id=?)")) {
                ps.setString(1, tid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM dm_messages WHERE thread_id=?")) {
                ps.setString(1, tid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM dm_thread_state WHERE thread_id=?")) {
                ps.setString(1, tid);
                ps.executeUpdate();
            }
            int removed;
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM dm_threads WHERE id=?")) {
                ps.setString(1, tid);
                removed = ps.executeUpdate();
            }
            connection.commit();
            return removed > 0;
        } catch (SQLException ex) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            plugin.getLogger().warning("Failed to delete direct message thread: " + ex.getMessage());
            return false;
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }


    public synchronized boolean isThreadLocked(String threadId) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        if (tid.isBlank() || connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement("SELECT locked FROM dm_threads WHERE id=?")) {
            ps.setString(1, tid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) != 0; }
        } catch (SQLException ex) { return false; }
    }

    public synchronized boolean setSessionFlags(String threadId, Boolean locked, Boolean retentionExempt) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        if (tid.isBlank() || connection == null) return false;
        List<String> sets = new ArrayList<>();
        if (locked != null) sets.add("locked=" + (locked ? "1" : "0"));
        if (retentionExempt != null) sets.add("retention_exempt=" + (retentionExempt ? "1" : "0"));
        if (sets.isEmpty()) return false;
        try (Statement st = connection.createStatement()) {
            int removed = st.executeUpdate("UPDATE dm_threads SET " + String.join(",", sets) + " WHERE id='" + tid.replace("'", "''") + "'");
            return removed > 0;
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to update direct message session flags: " + ex.getMessage());
            return false;
        }
    }

    public synchronized String cleanupPreviewJson() {
        Map<String,Object> m = new LinkedHashMap<>();
        ConfigValues c = plugin.configValues();
        int days = c == null ? 0 : Math.max(0, c.directMessageRetentionDays);
        m.put("retentionDays", days);
        if (jsonlMode() || connection == null || days <= 0) {
            m.put("expiredMessages", 0); m.put("emptySessions", 0); m.put("lockedSessions", 0); m.put("retentionExemptSessions", 0); return JsonUtil.obj(m);
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM dm_messages WHERE created_at < " + cutoff + " AND thread_id NOT IN (SELECT id FROM dm_threads WHERE retention_exempt=1)")) { m.put("expiredMessages", rs.next() ? rs.getInt(1) : 0); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM dm_threads WHERE retention_exempt=0 AND id NOT IN (SELECT DISTINCT thread_id FROM dm_messages WHERE created_at >= " + cutoff + ")")) { m.put("emptySessions", rs.next() ? rs.getInt(1) : 0); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM dm_threads WHERE locked=1")) { m.put("lockedSessions", rs.next() ? rs.getInt(1) : 0); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM dm_threads WHERE retention_exempt=1")) { m.put("retentionExemptSessions", rs.next() ? rs.getInt(1) : 0); }
        } catch (SQLException ex) { plugin.getLogger().warning("Failed to calculate direct message cleanup preview: " + ex.getMessage()); }
        return JsonUtil.obj(m);
    }

    public synchronized boolean uploadNameReferenced(String name, String ignoreThreadId) {
        String n = String.valueOf(name == null ? "" : name).trim();
        if (!n.matches("[A-Za-z0-9._-]+")) return false;
        String ignore = String.valueOf(ignoreThreadId == null ? "" : ignoreThreadId).trim();
        if (jsonlMode()) {
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg == null || msg.hidden || ignore.equals(msg.threadId)) continue;
                if (String.valueOf(msg.body == null ? "" : msg.body).contains(n)) return true;
            }
            return false;
        }
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM dm_messages WHERE hidden=0 AND thread_id<>? AND body LIKE ? LIMIT 1")) {
            ps.setString(1, ignore);
            ps.setString(2, "%" + n + "%");
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { return false; }
    }

    private String labelForIdentity(PlayerIdentity identity, String fallback) {
        if (identity == null) return fallback == null ? "" : fallback;
        String label = identity.displayName == null || identity.displayName.isBlank() ? identity.username : identity.displayName;
        if (label == null || label.isBlank()) label = fallback;
        if (identity.username != null && !identity.username.isBlank() && !identity.username.equals(label)) label += " (" + identity.username + ")";
        return label == null ? "" : label;
    }

    public synchronized List<DirectMessageThread> listThreads(String userUuid, int limit) {
        return listThreadsPage(userUuid, 1, limit <= 0 ? 200 : limit);
    }

    public synchronized int countThreads(String userUuid) {
        if (jsonlMode()) return jsonlListThreadsPage(userUuid, 1, Integer.MAX_VALUE).size();
        if (connection == null) return 0;
        String user = normalizeUuid(userUuid);
        if (user.isBlank()) return 0;
        String sql = "SELECT COUNT(*) FROM dm_threads t " +
                "LEFT JOIN dm_thread_state s ON s.thread_id=t.id AND s.user_uuid=? " +
                "WHERE (t.user_a_uuid=? OR t.user_b_uuid=?) AND COALESCE(s.hidden,0)=0 " +
                "AND EXISTS(SELECT 1 FROM dm_messages m WHERE m.thread_id=t.id AND m.hidden=0 " +
                "AND m.id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, user);
            ps.setString(3, user);
            ps.setString(4, user);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to count direct message threads: " + ex.getMessage());
            return 0;
        }
    }

    public synchronized List<DirectMessageThread> listThreadsPage(String userUuid, int page, int pageSize) {
        if (jsonlMode()) return jsonlListThreadsPage(userUuid, page, pageSize);
        List<DirectMessageThread> out = new ArrayList<>();
        if (connection == null) return out;
        String user = normalizeUuid(userUuid);
        int effectiveLimit = Math.max(1, pageSize <= 0 ? 10 : pageSize);
        int offset = Math.max(0, (Math.max(1, page) - 1) * effectiveLimit);
        String visibleFilter = "hidden=0 AND id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1)";
        String sql = "SELECT t.id,t.user_a_uuid,t.user_b_uuid,t.updated_at," +
                "m.id AS last_id,m.sender_uuid AS last_sender_uuid,m.body AS last_body,m.created_at AS last_created_at," +
                "COALESCE(s.last_read_message_id,0) AS last_read," +
                "(SELECT COUNT(*) FROM dm_messages mx WHERE mx.thread_id=t.id AND mx.hidden=0 " +
                "AND mx.id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1) " +
                "AND mx.id>COALESCE(s.last_read_message_id,0) AND mx.sender_uuid<>?) AS unread " +
                "FROM dm_threads t " +
                "JOIN dm_messages m ON m.id=(SELECT id FROM dm_messages WHERE thread_id=t.id AND " + visibleFilter + " ORDER BY id DESC LIMIT 1) " +
                "LEFT JOIN dm_thread_state s ON s.thread_id=t.id AND s.user_uuid=? " +
                "WHERE (t.user_a_uuid=? OR t.user_b_uuid=?) AND COALESCE(s.hidden,0)=0 " +
                "ORDER BY t.updated_at DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user); // unread hidden filter
            ps.setString(2, user); // unread sender comparison
            ps.setString(3, user); // last visible message hidden filter
            ps.setString(4, user); // thread state
            ps.setString(5, user);
            ps.setString(6, user);
            ps.setInt(7, effectiveLimit);
            ps.setInt(8, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DirectMessageThread t = new DirectMessageThread();
                    t.id = rs.getString("id");
                    String a = rs.getString("user_a_uuid");
                    String b = rs.getString("user_b_uuid");
                    String other = user.equals(a) ? b : a;
                    PlayerIdentity identity = currentPlayerIdentity(other);
                    t.otherUuid = other;
                    t.otherUsername = identity == null ? "" : identity.username;
                    t.otherDisplayName = identity == null ? "" : identity.displayName;
                    t.updatedAt = rs.getLong("updated_at");
                    t.lastMessageId = rs.getLong("last_id");
                    t.lastSenderUuid = rs.getString("last_sender_uuid");
                    t.lastMessage = rs.getString("last_body");
                    t.unread = rs.getInt("unread");
                    out.add(t);
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to list direct message threads: " + ex.getMessage());
        }
        return out;
    }

    private List<DirectMessageThread> jsonlListThreadsPage(String userUuid, int page, int pageSize) {
        String user = normalizeUuid(userUuid);
        if (user.isBlank()) return new ArrayList<>();
        Map<String, DirectMessageThread> byThread = new LinkedHashMap<>();
        Map<String, Integer> unreadByThread = new HashMap<>();
        for (JsonlMessage msg : jsonlMessages.values()) {
            if (!jsonlVisibleFor(msg, user)) continue;
            DirectMessageThread t = byThread.computeIfAbsent(msg.threadId, tid -> {
                DirectMessageThread thread = new DirectMessageThread();
                thread.id = tid;
                String other = jsonlOtherParticipant(tid, user);
                PlayerIdentity identity = currentPlayerIdentity(other);
                thread.otherUuid = other;
                thread.otherUsername = identity == null ? "" : identity.username;
                thread.otherDisplayName = identity == null ? "" : identity.displayName;
                return thread;
            });
            if (msg.createdAt >= t.updatedAt || msg.id >= t.lastMessageId) {
                t.updatedAt = msg.createdAt;
                t.lastMessageId = msg.id;
                t.lastSenderUuid = msg.senderUuid;
                t.lastMessage = msg.body;
            }
            long lastRead = jsonlLastRead.getOrDefault(jsonlThreadStateKey(msg.threadId, user), 0L);
            if (msg.id > lastRead && !user.equals(msg.senderUuid)) {
                unreadByThread.put(msg.threadId, unreadByThread.getOrDefault(msg.threadId, 0) + 1);
            }
        }
        List<DirectMessageThread> out = new ArrayList<>(byThread.values());
        for (DirectMessageThread t : out) t.unread = unreadByThread.getOrDefault(t.id, 0);
        out.sort(Comparator.comparingLong((DirectMessageThread t) -> t.updatedAt).reversed().thenComparingLong(t -> -t.lastMessageId));
        int effectiveLimit = Math.max(1, pageSize <= 0 ? 10 : pageSize);
        int offset = Math.max(0, (Math.max(1, page) - 1) * effectiveLimit);
        if (offset >= out.size()) return new ArrayList<>();
        int end = Math.min(out.size(), offset + effectiveLimit);
        return new ArrayList<>(out.subList(offset, end));
    }

    public synchronized List<DirectMessageMessage> listMessages(String userUuid, String threadId, long beforeId, int limit) {
        if (jsonlMode()) return jsonlListMessages(userUuid, threadId, beforeId, limit, true);
        List<DirectMessageMessage> out = new ArrayList<>();
        if (connection == null) return out;
        String user = normalizeUuid(userUuid);
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        if (tid.isBlank()) return out;
        int effectiveLimit = Math.max(1, limit <= 0 ? 100 : limit);
        try {
            if (!isParticipant(tid, user)) return out;
            String sql = "SELECT id,thread_id,sender_uuid,body,created_at FROM dm_messages WHERE thread_id=? AND hidden=0 " +
                    "AND id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1) " +
                    (beforeId > 0 ? "AND id<? " : "") +
                    "ORDER BY id DESC LIMIT ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, tid);
                ps.setString(2, user);
                int index = 3;
                if (beforeId > 0) ps.setLong(index++, beforeId);
                ps.setInt(index, effectiveLimit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(messageFromResult(rs));
                }
            }
            Collections.reverse(out);
            markRead(tid, user);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to list direct messages: " + ex.getMessage());
        }
        return out;
    }


    public synchronized List<DirectMessageMessage> adminListMessages(String threadId, long beforeId, int limit) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        if (tid.isBlank()) return new ArrayList<>();
        int effectiveLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 200));
        if (jsonlMode()) {
            List<JsonlMessage> raw = new ArrayList<>();
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg == null || msg.hidden || !tid.equals(msg.threadId)) continue;
                if (beforeId > 0 && msg.id >= beforeId) continue;
                raw.add(msg);
            }
            raw.sort(Comparator.comparingLong((JsonlMessage m) -> m.id).reversed());
            if (raw.size() > effectiveLimit) raw = new ArrayList<>(raw.subList(0, effectiveLimit));
            Collections.reverse(raw);
            List<DirectMessageMessage> out = new ArrayList<>();
            for (JsonlMessage msg : raw) out.add(jsonlToMessage(msg));
            return out;
        }
        List<DirectMessageMessage> out = new ArrayList<>();
        if (connection == null) return out;
        String sql = "SELECT id,thread_id,sender_uuid,body,created_at FROM dm_messages WHERE thread_id=? AND hidden=0 "
                + (beforeId > 0 ? "AND id<? " : "")
                + "ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tid);
            int index = 2;
            if (beforeId > 0) ps.setLong(index++, beforeId);
            ps.setInt(index, effectiveLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(messageFromResult(rs));
            }
            Collections.reverse(out);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to list direct messages for administrator audit: " + ex.getMessage());
        }
        return out;
    }

    public synchronized List<DirectMessageMessage> listMessagesBetween(String userUuid, String otherUuid, int limit) {
        String user = normalizeUuid(userUuid);
        String other = normalizeUuid(otherUuid);
        if (user.isBlank() || other.isBlank()) return new ArrayList<>();
        return listMessages(user, threadId(user, other), 0, limit);
    }

    public synchronized List<DirectMessageMessage> listMessagesBetweenPage(String userUuid, String otherUuid, int page, int pageSize) {
        String user = normalizeUuid(userUuid);
        String other = normalizeUuid(otherUuid);
        if (user.isBlank() || other.isBlank()) return new ArrayList<>();
        return listMessagesPage(user, threadId(user, other), Math.max(1, page), Math.max(1, pageSize));
    }

    private List<DirectMessageMessage> jsonlListMessages(String userUuid, String threadId, long beforeId, int limit, boolean markRead) {
        String user = normalizeUuid(userUuid);
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        if (user.isBlank() || tid.isBlank() || !jsonlIsParticipant(tid, user)) return new ArrayList<>();
        int effectiveLimit = limit <= 0 ? Integer.MAX_VALUE : Math.max(1, limit);
        List<JsonlMessage> raw = new ArrayList<>();
        for (JsonlMessage msg : jsonlMessages.values()) {
            if (!tid.equals(msg.threadId) || !jsonlVisibleFor(msg, user)) continue;
            if (beforeId > 0 && msg.id >= beforeId) continue;
            raw.add(msg);
        }
        raw.sort(Comparator.comparingLong((JsonlMessage m) -> m.id).reversed());
        if (raw.size() > effectiveLimit) raw = new ArrayList<>(raw.subList(0, effectiveLimit));
        Collections.reverse(raw);
        List<DirectMessageMessage> out = new ArrayList<>();
        for (JsonlMessage msg : raw) out.add(jsonlToMessage(msg));
        if (markRead) markRead(tid, user);
        return out;
    }

    public synchronized int countMessagesBetween(String userUuid, String otherUuid) {
        if (jsonlMode()) {
            String user = normalizeUuid(userUuid);
            String other = normalizeUuid(otherUuid);
            if (user.isBlank() || other.isBlank()) return 0;
            String tid = threadId(user, other);
            int count = 0;
            for (JsonlMessage msg : jsonlMessages.values()) if (tid.equals(msg.threadId) && jsonlVisibleFor(msg, user)) count++;
            return count;
        }
        if (connection == null) return 0;
        String user = normalizeUuid(userUuid);
        String other = normalizeUuid(otherUuid);
        if (user.isBlank() || other.isBlank()) return 0;
        String tid = threadId(user, other);
        try {
            if (!isParticipant(tid, user)) return 0;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM dm_messages WHERE thread_id=? AND hidden=0 " +
                            "AND id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1)")) {
                ps.setString(1, tid);
                ps.setString(2, user);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to count direct messages: " + ex.getMessage());
            return 0;
        }
    }

    private synchronized List<DirectMessageMessage> listMessagesPage(String userUuid, String threadId, int page, int pageSize) {
        if (jsonlMode()) {
            int effectivePage = Math.max(1, page);
            int effectiveLimit = Math.max(1, pageSize <= 0 ? 20 : pageSize);
            List<DirectMessageMessage> all = jsonlListMessages(userUuid, threadId, 0, 0, effectivePage == 1);
            int from = Math.max(0, all.size() - effectivePage * effectiveLimit);
            int to = Math.max(0, all.size() - (effectivePage - 1) * effectiveLimit);
            if (from >= to || from >= all.size()) return new ArrayList<>();
            return new ArrayList<>(all.subList(from, Math.min(to, all.size())));
        }
        List<DirectMessageMessage> out = new ArrayList<>();
        if (connection == null) return out;
        String user = normalizeUuid(userUuid);
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        if (tid.isBlank()) return out;
        int effectivePage = Math.max(1, page);
        int effectiveLimit = Math.max(1, pageSize <= 0 ? 20 : pageSize);
        int offset = Math.max(0, (effectivePage - 1) * effectiveLimit);
        try {
            if (!isParticipant(tid, user)) return out;
            String sql = "SELECT id,thread_id,sender_uuid,body,created_at FROM dm_messages WHERE thread_id=? AND hidden=0 " +
                    "AND id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1) " +
                    "ORDER BY id DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, tid);
                ps.setString(2, user);
                ps.setInt(3, effectiveLimit);
                ps.setInt(4, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(messageFromResult(rs));
                }
            }
            Collections.reverse(out);
            if (effectivePage == 1) markRead(tid, user);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to list paged direct messages: " + ex.getMessage());
        }
        return out;
    }

    private DirectMessageMessage messageFromResult(ResultSet rs) throws SQLException {
        DirectMessageMessage m = new DirectMessageMessage();
        m.id = rs.getLong("id");
        m.threadId = rs.getString("thread_id");
        m.senderUuid = rs.getString("sender_uuid");
        PlayerIdentity sender = currentPlayerIdentity(m.senderUuid);
        m.senderUsername = sender == null ? "" : sender.username;
        m.senderDisplayName = sender == null ? "" : sender.displayName;
        m.body = rs.getString("body");
        m.createdAt = rs.getLong("created_at");
        return m;
    }

    private PlayerIdentity currentPlayerIdentity(String uuid) {
        String normalized = normalizeUuid(uuid);
        if (!normalized.isBlank()) {
            try {
                Player online = plugin.getServer().getPlayer(UUID.fromString(normalized));
                if (online != null) {
                    String username = online.getName();
                    String displayName = plugin.displayPlayerName(online);
                    if (displayName != null && !displayName.isBlank()) {
                        plugin.storage().updateLastDisplayName(normalized, username, displayName);
                    }
                    return new PlayerIdentity(normalized, username, displayName == null ? "" : displayName);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        PlayerIdentity known = plugin.storage().findKnownPlayerByUuid(normalized);
        String username = known == null ? "" : known.username;
        String displayName = known == null ? "" : known.displayName;

        // DM rows need both the last display name and the real Minecraft account
        // name for the display-name/real-name click toggle.  Accounts created
        // before DM support may have a remembered display name but no username;
        // use Bukkit's offline player cache as a safe local fallback.
        if (!normalized.isBlank() && (username == null || username.isBlank())) {
            try {
                OfflinePlayer offline = plugin.getServer().getOfflinePlayer(UUID.fromString(normalized));
                String offlineName = offline == null ? null : offline.getName();
                if (offlineName != null && !offlineName.isBlank()) username = offlineName;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if ((displayName == null || displayName.isBlank()) && !normalized.isBlank()) {
            displayName = plugin.storage().knownDisplayName(normalized);
        }
        if (displayName != null && !displayName.isBlank() && username != null && !username.isBlank()) {
            plugin.storage().updateLastDisplayName(normalized, username, displayName);
        }
        if ((username == null || username.isBlank()) && (displayName == null || displayName.isBlank())) return null;
        return new PlayerIdentity(normalized, username == null ? "" : username, displayName == null ? "" : displayName);
    }

    public synchronized boolean markRead(String threadId, String userUuid) {
        if (jsonlMode()) return jsonlMarkRead(threadId, userUuid);
        if (connection == null) return false;
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        String user = normalizeUuid(userUuid);
        if (tid.isBlank() || user.isBlank()) return false;
        try {
            if (!isParticipant(tid, user)) return false;
            ensureState(tid, user);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE dm_thread_state SET last_read_message_id=COALESCE((SELECT MAX(id) FROM dm_messages WHERE thread_id=? AND hidden=0 " +
                            "AND id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1)),0) WHERE thread_id=? AND user_uuid=?")) {
                ps.setString(1, tid);
                ps.setString(2, user);
                ps.setString(3, tid);
                ps.setString(4, user);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to mark direct message thread as read: " + ex.getMessage());
            return false;
        }
    }

    private boolean jsonlMarkRead(String threadId, String userUuid) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        String user = normalizeUuid(userUuid);
        if (tid.isBlank() || user.isBlank() || !jsonlIsParticipant(tid, user)) return false;
        long max = 0L;
        for (JsonlMessage msg : jsonlMessages.values()) {
            if (tid.equals(msg.threadId) && jsonlVisibleFor(msg, user)) max = Math.max(max, msg.id);
        }
        jsonlLastRead.put(jsonlThreadStateKey(tid, user), max);
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "read");
            m.put("threadId", tid);
            m.put("userUuid", user);
            m.put("lastReadMessageId", max);
            appendJsonlEvent(m);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to append direct message read state: " + ex.getMessage());
        }
        return true;
    }


    public synchronized boolean hideMessage(String userUuid, long messageId) {
        if (jsonlMode()) return jsonlHideMessage(userUuid, messageId);
        if (connection == null) return false;
        String user = normalizeUuid(userUuid);
        if (user.isBlank() || messageId <= 0) return false;
        try {
            String threadId;
            try (PreparedStatement ps = connection.prepareStatement("SELECT thread_id FROM dm_messages WHERE id=? AND hidden=0")) {
                ps.setLong(1, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    threadId = rs.getString(1);
                }
            }
            if (!isParticipant(threadId, user)) return false;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO dm_message_state(message_id,user_uuid,hidden) VALUES(?,?,1) " +
                            "ON CONFLICT(message_id,user_uuid) DO UPDATE SET hidden=1")) {
                ps.setLong(1, messageId);
                ps.setString(2, user);
                ps.executeUpdate();
            }
            markRead(threadId, user);
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to hide direct message: " + ex.getMessage());
            return false;
        }
    }

    private boolean jsonlHideMessage(String userUuid, long messageId) {
        String user = normalizeUuid(userUuid);
        JsonlMessage msg = jsonlMessages.get(messageId);
        if (user.isBlank() || msg == null || !jsonlVisibleFor(msg, user)) return false;
        jsonlHiddenMessages.add(jsonlHiddenKey(user, messageId));
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", "hide_message");
            m.put("messageId", messageId);
            m.put("userUuid", user);
            m.put("hidden", true);
            appendJsonlEvent(m);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to append direct message hide state: " + ex.getMessage());
        }
        markRead(msg.threadId, user);
        return true;
    }

    public synchronized int unreadCount(String userUuid) {
        if (jsonlMode()) {
            String user = normalizeUuid(userUuid);
            if (user.isBlank()) return 0;
            int count = 0;
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (!jsonlVisibleFor(msg, user) || user.equals(msg.senderUuid)) continue;
                long lastRead = jsonlLastRead.getOrDefault(jsonlThreadStateKey(msg.threadId, user), 0L);
                if (msg.id > lastRead) count++;
            }
            return count;
        }
        if (connection == null) return 0;
        String user = normalizeUuid(userUuid);
        String sql = "SELECT COALESCE(SUM((SELECT COUNT(*) FROM dm_messages m WHERE m.thread_id=t.id AND m.hidden=0 " +
                "AND m.id NOT IN (SELECT message_id FROM dm_message_state WHERE user_uuid=? AND hidden=1) " +
                "AND m.id>COALESCE(s.last_read_message_id,0) AND m.sender_uuid<>?)),0) " +
                "FROM dm_threads t LEFT JOIN dm_thread_state s ON s.thread_id=t.id AND s.user_uuid=? " +
                "WHERE (t.user_a_uuid=? OR t.user_b_uuid=?) AND COALESCE(s.hidden,0)=0";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, user);
            ps.setString(3, user);
            ps.setString(4, user);
            ps.setString(5, user);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to count unread direct messages: " + ex.getMessage());
            return 0;
        }
    }
}
