package dev.kokoto.webchat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.*;
import java.util.*;

public class DirectMessageStore {
    private final ConversationStoreHost host;
    private Connection connection;
    private String storageMode = "sqlite";
    private File jsonlFile;
    private long jsonlNextMessageId = 1L;
    private final Map<Long, JsonlMessage> jsonlMessages = new LinkedHashMap<>();
    private final Map<String, Long> jsonlLastRead = new HashMap<>();
    private final Set<String> jsonlHiddenMessages = new HashSet<>();

    public DirectMessageStore(ConversationStoreHost host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    public synchronized void open() {
        close();
        DirectMessageSettings c = host.directMessageSettings();
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
            recoverInterruptedPending();
            cleanup();
        } catch (SQLException ex) {
            host.warn("Failed to open direct message SQLite store: " + ex.getMessage());
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
        if (!file.isAbsolute()) file = new File(host.dataDirectory().toFile(), configured);
        return file;
    }

    private File resolveJsonlFile(String configured) {
        File file = new File(configured == null || configured.isBlank() ? "direct-messages.jsonl" : configured);
        if (!file.isAbsolute()) file = new File(host.dataDirectory().toFile(), configured);
        return file;
    }

    private void openJsonl(DirectMessageSettings c) {
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
                host.warn("Failed to load direct message JSONL store: " + ex.getMessage());
            }
        }
        recoverInterruptedPending();
        cleanup();
        host.info("Using JSONL direct message store: " + jsonlFile.getAbsolutePath());
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
                msg.deliveryStatus = normalizeDeliveryStatus(m.get("deliveryStatus"));
                msg.deliveryError = String.valueOf(m.getOrDefault("deliveryError", ""));
                msg.relayId = String.valueOf(m.getOrDefault("relayId", "")).trim();
                msg.clientMessageId = String.valueOf(m.getOrDefault("clientMessageId", "")).trim();
                msg.replyToId = parseLong(m.get("replyToId"), 0L);
                msg.replyToSender = String.valueOf(m.getOrDefault("replyToSender", ""));
                msg.replyToPreview = String.valueOf(m.getOrDefault("replyToPreview", ""));
                msg.replyToRelayId = String.valueOf(m.getOrDefault("replyToRelayId", "")).trim();
                if (msg.id > 0 && !msg.threadId.isBlank() && !msg.senderUuid.isBlank()) {
                    jsonlMessages.put(msg.id, msg);
                    jsonlNextMessageId = Math.max(jsonlNextMessageId, msg.id + 1L);
                }
            } else if ("delivery".equals(type)) {
                long messageId = parseLong(m.get("messageId"), 0L);
                JsonlMessage msg = jsonlMessages.get(messageId);
                if (msg != null) {
                    msg.deliveryStatus = normalizeDeliveryStatus(m.get("status"));
                    msg.deliveryError = String.valueOf(m.getOrDefault("error", ""));
                    String relayId = String.valueOf(m.getOrDefault("relayId", "")).trim();
                    String clientMessageId = String.valueOf(m.getOrDefault("clientMessageId", "")).trim();
                    if (!relayId.isBlank()) msg.relayId = relayId;
                    if (!clientMessageId.isBlank()) msg.clientMessageId = clientMessageId;
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
            m.put("deliveryStatus", normalizeDeliveryStatus(msg.deliveryStatus));
            m.put("deliveryError", msg.deliveryError == null ? "" : msg.deliveryError);
            m.put("relayId", msg.relayId == null ? "" : msg.relayId);
            m.put("clientMessageId", msg.clientMessageId == null ? "" : msg.clientMessageId);
            m.put("replyToId", Math.max(0L, msg.replyToId));
            m.put("replyToSender", msg.replyToSender == null ? "" : msg.replyToSender);
            m.put("replyToPreview", msg.replyToPreview == null ? "" : msg.replyToPreview);
            m.put("replyToRelayId", msg.replyToRelayId == null ? "" : msg.replyToRelayId);
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
        String deliveryStatus = "delivered";
        String deliveryError = "";
        String relayId = "";
        String clientMessageId = "";
        long replyToId = 0L;
        String replyToSender = "";
        String replyToPreview = "";
        String replyToRelayId = "";
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
                    "hidden INTEGER NOT NULL DEFAULT 0," +
                    "reply_to_id INTEGER NOT NULL DEFAULT 0," +
                    "reply_to_sender TEXT NOT NULL DEFAULT ''," +
                    "reply_to_preview TEXT NOT NULL DEFAULT ''," +
                    "reply_to_relay_id TEXT NOT NULL DEFAULT ''" +
                    ")");
            addColumnIfMissing(st, "dm_messages", "reply_to_id", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(st, "dm_messages", "reply_to_sender", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(st, "dm_messages", "reply_to_preview", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(st, "dm_messages", "reply_to_relay_id", "TEXT NOT NULL DEFAULT ''");
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
            st.execute("CREATE TABLE IF NOT EXISTS dm_delivery_state (" +
                    "message_id INTEGER PRIMARY KEY," +
                    "relay_id TEXT NOT NULL DEFAULT ''," +
                    "client_message_id TEXT NOT NULL DEFAULT ''," +
                    "status TEXT NOT NULL DEFAULT 'delivered'," +
                    "error TEXT NOT NULL DEFAULT ''," +
                    "target_server_id TEXT NOT NULL DEFAULT ''," +
                    "updated_at INTEGER NOT NULL DEFAULT 0" +
                    ")");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_dm_delivery_relay_id ON dm_delivery_state(relay_id) WHERE relay_id<>''");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_dm_delivery_client_id ON dm_delivery_state(client_message_id) WHERE client_message_id<>''");
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

    private synchronized void recoverInterruptedPending() {
        if (jsonlMode()) {
            boolean changed = false;
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (!"pending".equals(normalizeDeliveryStatus(msg.deliveryStatus))) continue;
                msg.deliveryStatus = "failed";
                msg.deliveryError = "delivery_interrupted";
                changed = true;
            }
            if (changed) {
                try { rewriteJsonl(); }
                catch (IOException ex) { host.warn("Failed to recover pending direct-message delivery state: " + ex.getMessage()); }
            }
            return;
        }
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE dm_delivery_state SET status='failed',error='delivery_interrupted',updated_at=? WHERE status='pending'")) {
            ps.setLong(1, System.currentTimeMillis());
            int changed = ps.executeUpdate();
            if (changed > 0) host.warn("Recovered " + changed + " interrupted pending direct-message delivery state(s) as failed/retryable.");
        } catch (SQLException ex) {
            host.warn("Failed to recover pending direct-message delivery state: " + ex.getMessage());
        }
    }

    public synchronized void cleanup() {
        if (jsonlMode()) {
            cleanupJsonl();
            return;
        }
        if (connection == null) return;
        DirectMessageSettings c = host.directMessageSettings();
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
                st.executeUpdate("DELETE FROM dm_delivery_state WHERE message_id NOT IN (SELECT id FROM dm_messages)");
                st.executeUpdate("DELETE FROM dm_threads WHERE retention_exempt=0 AND id NOT IN (SELECT DISTINCT thread_id FROM dm_messages)");
                st.executeUpdate("DELETE FROM dm_thread_state WHERE thread_id NOT IN (SELECT id FROM dm_threads)");
            }
        } catch (SQLException ex) {
            host.warn("Failed to cleanup direct messages: " + ex.getMessage());
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

    private static String normalizeDeliveryStatus(String status) {
        String value = String.valueOf(status == null ? "" : status).trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "pending", "failed" -> value;
            default -> "delivered";
        };
    }

    private static String cleanDeliveryId(String value, int max) {
        String out = String.valueOf(value == null ? "" : value).trim();
        if (out.length() > max) out = out.substring(0, max);
        return out;
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
        DirectMessageSettings c = host.directMessageSettings();
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
            catch (IOException ex) { host.warn("Failed to rewrite direct message JSONL store: " + ex.getMessage()); }
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
        m.deliveryStatus = normalizeDeliveryStatus(msg.deliveryStatus);
        m.deliveryError = msg.deliveryError == null ? "" : msg.deliveryError;
        m.relayId = msg.relayId == null ? "" : msg.relayId;
        m.clientMessageId = msg.clientMessageId == null ? "" : msg.clientMessageId;
        m.replyToId = Math.max(0L, msg.replyToId);
        m.replyToSender = msg.replyToSender == null ? "" : msg.replyToSender;
        m.replyToPreview = msg.replyToPreview == null ? "" : msg.replyToPreview;
        m.replyToRelayId = msg.replyToRelayId == null ? "" : msg.replyToRelayId;
        applyReadReceiptState(m);
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
        public boolean duplicate;
        public String error = "";
        public DirectMessageThread thread;
        public DirectMessageMessage message;
        public String targetUuid = "";
    }

    public static class RemoteReadReceipt {
        public boolean ok;
        public String sourceServerId = "";
        public String relayId = "";
        public String threadId = "";
    }

    public static class ReadReceiptApplyResult {
        public boolean ok;
        public boolean changed;
        public String error = "";
        public String threadId = "";
        public String localUserUuid = "";
        public String remoteUserUuid = "";
        public long messageId;
    }

    public static class RetryData {
        public boolean ok;
        public String error = "";
        public DirectMessageMessage message;
        public String targetUuid = "";
        public String targetServerId = "";
    }

    private static final class ReplyMeta {
        long id;
        String sender = "";
        String preview = "";
        String relayId = "";
    }

    public synchronized SendResult send(String senderUuid, String targetUuid, String body) {
        return send(senderUuid, targetUuid, body, 0L);
    }

    public synchronized SendResult send(String senderUuid, String targetUuid, String body, long replyToId) {
        return sendWithDelivery(senderUuid, targetUuid, body, "delivered", "", "", "", replyToId, "", "", "");
    }

    public synchronized SendResult sendWithClientMessageId(String senderUuid, String targetUuid, String body, String clientMessageId) {
        return sendWithClientMessageId(senderUuid, targetUuid, body, clientMessageId, 0L);
    }

    public synchronized SendResult sendWithClientMessageId(String senderUuid, String targetUuid, String body,
                                                            String clientMessageId, long replyToId) {
        return sendWithDelivery(senderUuid, targetUuid, body, "delivered", "", "", clientMessageId, replyToId, "", "", "");
    }

    public synchronized SendResult sendPendingRemote(String senderUuid, String targetUuid, String body,
                                                     String relayId, String targetServerId, String clientMessageId) {
        return sendPendingRemote(senderUuid, targetUuid, body, relayId, targetServerId, clientMessageId, 0L);
    }

    public synchronized SendResult sendPendingRemote(String senderUuid, String targetUuid, String body,
                                                     String relayId, String targetServerId, String clientMessageId,
                                                     long replyToId) {
        return sendWithDelivery(senderUuid, targetUuid, body, "pending", relayId, targetServerId, clientMessageId,
                replyToId, "", "", "");
    }

    public synchronized SendResult receiveRelayed(String senderUuid, String targetUuid, String body, String relayId) {
        return receiveRelayed(senderUuid, targetUuid, body, relayId, "", "", "");
    }

    public synchronized SendResult receiveRelayed(String senderUuid, String targetUuid, String body, String relayId,
                                                   String replyToRelayId, String replyToSender, String replyToPreview) {
        String cleanRelayId = cleanDeliveryId(relayId, 180);
        if (!cleanRelayId.isBlank()) {
            SendResult existing = resultByRelayId(senderUuid, targetUuid, cleanRelayId);
            if (existing != null) {
                existing.duplicate = true;
                return existing;
            }
        }
        return sendWithDelivery(senderUuid, targetUuid, body, "delivered", cleanRelayId, "", "", 0L,
                cleanDeliveryId(replyToRelayId, 180), cleanReplyText(replyToSender, 128), cleanReplyText(replyToPreview, 240));
    }

    private synchronized SendResult sendWithDelivery(String senderUuid, String targetUuid, String body,
                                                       String deliveryStatus, String relayId,
                                                       String targetServerId, String clientMessageId,
                                                       long requestedReplyToId, String inboundReplyRelayId,
                                                       String inboundReplySender, String inboundReplyPreview) {
        if (jsonlMode()) return jsonlSend(senderUuid, targetUuid, body, deliveryStatus, relayId, targetServerId,
                clientMessageId, requestedReplyToId, inboundReplyRelayId, inboundReplySender, inboundReplyPreview);
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
        String status = normalizeDeliveryStatus(deliveryStatus);
        String cleanRelayId = cleanDeliveryId(relayId, 180);
        String cleanClientMessageId = cleanDeliveryId(clientMessageId, 180);
        String cleanTargetServerId = cleanDeliveryId(targetServerId, 64).toLowerCase(Locale.ROOT);

        if (!cleanClientMessageId.isBlank()) {
            SendResult existing = resultByClientMessageId(sender, target, cleanClientMessageId);
            if (existing != null) {
                existing.duplicate = true;
                return existing;
            }
        }
        if (!cleanRelayId.isBlank()) {
            SendResult existing = resultByRelayId(sender, target, cleanRelayId);
            if (existing != null) {
                existing.duplicate = true;
                return existing;
            }
        }

        long now = System.currentTimeMillis();
        String threadId = threadId(sender, target);
        try {
            ensureThread(sender, target, now);
            if (isThreadLocked(threadId)) {
                result.error = "thread_locked";
                return result;
            }
            ReplyMeta reply = null;
            if (requestedReplyToId > 0L) {
                reply = resolveLocalReplyMeta(sender, threadId, requestedReplyToId);
                if (reply == null) {
                    result.error = "reply_target_not_found";
                    return result;
                }
            } else if (!safeText(inboundReplyRelayId).isBlank()) {
                reply = resolveRelayReplyMeta(threadId, inboundReplyRelayId, inboundReplySender, inboundReplyPreview);
                if (reply == null) {
                    result.error = "reply_target_not_found";
                    return result;
                }
            }
            long messageId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO dm_messages(thread_id,sender_uuid,body,created_at,hidden,reply_to_id,reply_to_sender,reply_to_preview,reply_to_relay_id) VALUES(?,?,?,?,0,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, threadId);
                ps.setString(2, sender);
                ps.setString(3, text);
                ps.setLong(4, now);
                ps.setLong(5, reply == null ? 0L : reply.id);
                ps.setString(6, reply == null ? "" : reply.sender);
                ps.setString(7, reply == null ? "" : reply.preview);
                ps.setString(8, reply == null ? "" : reply.relayId);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    messageId = keys.next() ? keys.getLong(1) : 0L;
                }
            }
            if (messageId > 0 && (!"delivered".equals(status) || !cleanRelayId.isBlank() || !cleanClientMessageId.isBlank() || !cleanTargetServerId.isBlank())) {
                writeDeliveryState(messageId, cleanRelayId, cleanClientMessageId, status, "", cleanTargetServerId, now);
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
            host.warn("Failed to send direct message: " + ex.getMessage());
            return result;
        }
    }

    private ReplyMeta resolveLocalReplyMeta(String requestingUser, String expectedThreadId, long replyToId) {
        DirectMessageMessage original = replyToId <= 0L ? null : messageForUser(requestingUser, replyToId);
        if (original == null || !expectedThreadId.equals(original.threadId)) return null;
        ReplyMeta meta = new ReplyMeta();
        meta.id = original.id;
        meta.sender = replySenderLabel(original);
        meta.preview = replyPreview(original.body);
        meta.relayId = cleanDeliveryId(original.relayId, 180);
        return meta;
    }

    private ReplyMeta resolveRelayReplyMeta(String expectedThreadId, String relayId, String senderSnapshot, String previewSnapshot) {
        DirectMessageMessage original = messageByRelayIdInThread(expectedThreadId, cleanDeliveryId(relayId, 180));
        if (original == null) return null;
        ReplyMeta meta = new ReplyMeta();
        meta.id = original.id;
        meta.sender = safeText(senderSnapshot).isBlank() ? replySenderLabel(original) : cleanReplyText(senderSnapshot, 128);
        meta.preview = safeText(previewSnapshot).isBlank() ? replyPreview(original.body) : cleanReplyText(previewSnapshot, 240);
        meta.relayId = cleanDeliveryId(relayId, 180);
        return meta;
    }

    private DirectMessageMessage messageByRelayIdInThread(String threadId, String relayId) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        String rid = cleanDeliveryId(relayId, 180);
        if (tid.isBlank() || rid.isBlank()) return null;
        if (jsonlMode()) {
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg != null && !msg.hidden && tid.equals(msg.threadId) && rid.equals(msg.relayId)) return jsonlToMessage(msg);
            }
            return null;
        }
        if (connection == null) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT m.id FROM dm_messages m JOIN dm_delivery_state d ON d.message_id=m.id WHERE m.thread_id=? AND m.hidden=0 AND d.relay_id=? LIMIT 1")) {
            ps.setString(1, tid);
            ps.setString(2, rid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readMessage(rs.getLong(1)) : null;
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    private static String replySenderLabel(DirectMessageMessage message) {
        if (message == null) return "";
        String display = safeText(message.senderDisplayName).trim();
        if (!display.isBlank()) return cleanReplyText(display, 128);
        String username = safeText(message.senderUsername).trim();
        if (!username.isBlank()) return cleanReplyText(username, 128);
        return cleanReplyText(message.senderUuid, 128);
    }

    private static String replyPreview(String body) {
        return cleanReplyText(body, 240);
    }

    private static String cleanReplyText(String value, int max) {
        String text = safeText(value).replace('\r', ' ').replace('\n', ' ').trim().replaceAll("\\s+", " ");
        return max > 0 && text.length() > max ? text.substring(0, max) : text;
    }

    private static String safeText(String value) { return value == null ? "" : value; }

    private SendResult jsonlSend(String senderUuid, String targetUuid, String body,
                                 String deliveryStatus, String relayId,
                                 String targetServerId, String clientMessageId,
                                 long requestedReplyToId, String inboundReplyRelayId,
                                 String inboundReplySender, String inboundReplyPreview) {
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
        String status = normalizeDeliveryStatus(deliveryStatus);
        String cleanRelayId = cleanDeliveryId(relayId, 180);
        String cleanClientMessageId = cleanDeliveryId(clientMessageId, 180);
        String cleanTargetServerId = cleanDeliveryId(targetServerId, 64).toLowerCase(Locale.ROOT);
        for (JsonlMessage existing : jsonlMessages.values()) {
            boolean same = (!cleanClientMessageId.isBlank() && cleanClientMessageId.equals(existing.clientMessageId))
                    || (!cleanRelayId.isBlank() && cleanRelayId.equals(existing.relayId));
            if (!same) continue;
            String other = jsonlOtherParticipant(existing.threadId, sender);
            if (!target.equals(other) || !sender.equals(existing.senderUuid)) continue;
            result.ok = true;
            result.duplicate = true;
            result.targetUuid = target;
            result.message = jsonlToMessage(existing);
            result.thread = listThreads(sender, 200).stream().filter(t -> existing.threadId.equals(t.id)).findFirst().orElse(null);
            return result;
        }
        String threadId = threadId(sender, target);
        ReplyMeta reply = null;
        if (requestedReplyToId > 0L) {
            reply = resolveLocalReplyMeta(sender, threadId, requestedReplyToId);
            if (reply == null) { result.error = "reply_target_not_found"; return result; }
        } else if (!safeText(inboundReplyRelayId).isBlank()) {
            reply = resolveRelayReplyMeta(threadId, inboundReplyRelayId, inboundReplySender, inboundReplyPreview);
            if (reply == null) { result.error = "reply_target_not_found"; return result; }
        }
        long now = System.currentTimeMillis();
        JsonlMessage msg = new JsonlMessage();
        msg.id = jsonlNextMessageId++;
        msg.threadId = threadId;
        msg.senderUuid = sender;
        msg.body = text;
        msg.createdAt = now;
        msg.hidden = false;
        msg.deliveryStatus = status;
        msg.deliveryError = "";
        msg.relayId = cleanRelayId;
        msg.clientMessageId = cleanClientMessageId;
        msg.replyToId = reply == null ? 0L : reply.id;
        msg.replyToSender = reply == null ? "" : reply.sender;
        msg.replyToPreview = reply == null ? "" : reply.preview;
        msg.replyToRelayId = reply == null ? "" : reply.relayId;
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
            m.put("deliveryStatus", msg.deliveryStatus);
            m.put("deliveryError", "");
            m.put("relayId", msg.relayId);
            m.put("clientMessageId", msg.clientMessageId);
            m.put("replyToId", Math.max(0L, msg.replyToId));
            m.put("replyToSender", msg.replyToSender);
            m.put("replyToPreview", msg.replyToPreview);
            m.put("replyToRelayId", msg.replyToRelayId);
            m.put("targetServerId", cleanTargetServerId);
            appendJsonlEvent(m);
        } catch (IOException ex) {
            jsonlMessages.remove(msg.id);
            result.error = "io_error";
            host.warn("Failed to append direct message JSONL store: " + ex.getMessage());
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

    private void writeDeliveryState(long messageId, String relayId, String clientMessageId, String status,
                                    String error, String targetServerId, long updatedAt) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO dm_delivery_state(message_id,relay_id,client_message_id,status,error,target_server_id,updated_at) VALUES(?,?,?,?,?,?,?) " +
                        "ON CONFLICT(message_id) DO UPDATE SET relay_id=excluded.relay_id,client_message_id=excluded.client_message_id," +
                        "status=excluded.status,error=excluded.error,target_server_id=excluded.target_server_id,updated_at=excluded.updated_at")) {
            ps.setLong(1, messageId);
            ps.setString(2, cleanDeliveryId(relayId, 180));
            ps.setString(3, cleanDeliveryId(clientMessageId, 180));
            ps.setString(4, normalizeDeliveryStatus(status));
            ps.setString(5, cleanDeliveryId(error, 240));
            ps.setString(6, cleanDeliveryId(targetServerId, 64).toLowerCase(Locale.ROOT));
            ps.setLong(7, updatedAt);
            ps.executeUpdate();
        }
    }

    private SendResult resultByRelayId(String sender, String target, String relayId) {
        if (jsonlMode()) return jsonlResultById(sender, target, relayId, true);
        if (connection == null || relayId == null || relayId.isBlank()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT d.message_id FROM dm_delivery_state d JOIN dm_messages m ON m.id=d.message_id WHERE d.relay_id=? AND m.hidden=0")) {
            ps.setString(1, relayId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return resultForExistingMessage(sender, target, rs.getLong(1));
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    private SendResult resultByClientMessageId(String sender, String target, String clientMessageId) {
        if (jsonlMode()) return jsonlResultById(sender, target, clientMessageId, false);
        if (connection == null || clientMessageId == null || clientMessageId.isBlank()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT d.message_id FROM dm_delivery_state d JOIN dm_messages m ON m.id=d.message_id WHERE d.client_message_id=? AND m.hidden=0")) {
            ps.setString(1, clientMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return resultForExistingMessage(sender, target, rs.getLong(1));
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    private SendResult jsonlResultById(String sender, String target, String value, boolean relay) {
        for (JsonlMessage msg : jsonlMessages.values()) {
            String candidate = relay ? msg.relayId : msg.clientMessageId;
            if (!value.equals(candidate) || !sender.equals(msg.senderUuid) || !target.equals(jsonlOtherParticipant(msg.threadId, sender))) continue;
            SendResult r = new SendResult();
            r.ok = true;
            r.duplicate = true;
            r.targetUuid = target;
            r.message = jsonlToMessage(msg);
            r.thread = listThreads(sender, 200).stream().filter(t -> msg.threadId.equals(t.id)).findFirst().orElse(null);
            return r;
        }
        return null;
    }

    private SendResult resultForExistingMessage(String sender, String target, long messageId) throws SQLException {
        DirectMessageMessage message = readMessage(messageId);
        if (message == null || !sender.equals(normalizeUuid(message.senderUuid)) || !target.equals(otherParticipant(message.threadId, sender))) return null;
        SendResult r = new SendResult();
        r.ok = true;
        r.duplicate = true;
        r.targetUuid = target;
        r.message = message;
        r.thread = listThreads(sender, 200).stream().filter(t -> message.threadId.equals(t.id)).findFirst().orElse(null);
        return r;
    }

    private String otherParticipant(String threadId, String userUuid) {
        String user = normalizeUuid(userUuid);
        String[] pair = threadParticipants(threadId);
        if (user.equals(pair[0])) return pair[1];
        if (user.equals(pair[1])) return pair[0];
        return "";
    }

    public synchronized String otherParticipantUuid(String threadId, String userUuid) {
        return otherParticipant(threadId, userUuid);
    }

    public synchronized String threadIdForParticipants(String userUuidA, String userUuidB) {
        return threadId(normalizeUuid(userUuidA), normalizeUuid(userUuidB));
    }

    private long lastReadMessageId(String threadId, String userUuid) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        String user = normalizeUuid(userUuid);
        if (tid.isBlank() || user.isBlank()) return 0L;
        if (jsonlMode()) return jsonlLastRead.getOrDefault(jsonlThreadStateKey(tid, user), 0L);
        if (connection == null) return 0L;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(last_read_message_id,0) FROM dm_thread_state WHERE thread_id=? AND user_uuid=?")) {
            ps.setString(1, tid);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Math.max(0L, rs.getLong(1)) : 0L;
            }
        } catch (SQLException ex) {
            return 0L;
        }
    }

    public synchronized long readPosition(String threadId, String userUuid) {
        return lastReadMessageId(threadId, userUuid);
    }

    private void applyReadReceiptState(DirectMessageMessage message) {
        if (message == null || message.id <= 0 || message.threadId == null || message.threadId.isBlank()) return;
        String recipient = otherParticipant(message.threadId, message.senderUuid);
        if (recipient.isBlank()) return;
        message.readByOther = lastReadMessageId(message.threadId, recipient) >= message.id;
        message.unreadRecipientCount = message.readByOther ? 0 : 1;
    }

    private boolean setLastReadAtLeast(String threadId, String userUuid, long messageId) {
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        String user = normalizeUuid(userUuid);
        if (tid.isBlank() || user.isBlank() || messageId <= 0) return false;
        if (jsonlMode()) {
            long current = jsonlLastRead.getOrDefault(jsonlThreadStateKey(tid, user), 0L);
            if (current >= messageId) return true;
            jsonlLastRead.put(jsonlThreadStateKey(tid, user), messageId);
            try {
                Map<String, Object> event = new LinkedHashMap<>();
                event.put("type", "read");
                event.put("threadId", tid);
                event.put("userUuid", user);
                event.put("lastReadMessageId", messageId);
                appendJsonlEvent(event);
                return true;
            } catch (IOException ex) {
                host.warn("Failed to append direct message remote read state: " + ex.getMessage());
                return false;
            }
        }
        if (connection == null) return false;
        try {
            ensureState(tid, user);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE dm_thread_state SET last_read_message_id=CASE WHEN last_read_message_id<? THEN ? ELSE last_read_message_id END WHERE thread_id=? AND user_uuid=?")) {
                ps.setLong(1, messageId);
                ps.setLong(2, messageId);
                ps.setString(3, tid);
                ps.setString(4, user);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException ex) {
            host.warn("Failed to apply direct message remote read state: " + ex.getMessage());
            return false;
        }
    }

    public synchronized RemoteReadReceipt latestRemoteReadReceipt(String threadId, String readerUuid) {
        RemoteReadReceipt out = new RemoteReadReceipt();
        String tid = String.valueOf(threadId == null ? "" : threadId).trim();
        String reader = normalizeUuid(readerUuid);
        if (tid.isBlank() || reader.isBlank()) return out;
        long lastRead = lastReadMessageId(tid, reader);
        if (lastRead <= 0) return out;
        if (jsonlMode()) {
            JsonlMessage best = null;
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg == null || msg.hidden || !tid.equals(msg.threadId) || msg.id > lastRead || reader.equals(msg.senderUuid)) continue;
                RemotePlayerRef remote = RemotePlayerRef.parse(msg.senderUuid);
                if (remote == null || msg.relayId == null || msg.relayId.isBlank()) continue;
                if (best == null || msg.id > best.id) best = msg;
            }
            if (best == null) return out;
            RemotePlayerRef remote = RemotePlayerRef.parse(best.senderUuid);
            if (remote == null) return out;
            out.ok = true;
            out.sourceServerId = remote.serverId;
            out.relayId = best.relayId == null ? "" : best.relayId;
            out.threadId = tid;
            return out;
        }
        if (connection == null) return out;
        String sql = "SELECT m.sender_uuid,COALESCE(d.relay_id,'') FROM dm_messages m LEFT JOIN dm_delivery_state d ON d.message_id=m.id " +
                "WHERE m.thread_id=? AND m.hidden=0 AND m.id<=? AND m.sender_uuid<>? AND COALESCE(d.relay_id,'')<>'' ORDER BY m.id DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tid);
            ps.setLong(2, lastRead);
            ps.setString(3, reader);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sender = normalizeUuid(rs.getString(1));
                    RemotePlayerRef remote = RemotePlayerRef.parse(sender);
                    if (remote == null) continue;
                    out.ok = true;
                    out.sourceServerId = remote.serverId;
                    out.relayId = String.valueOf(rs.getString(2) == null ? "" : rs.getString(2)).trim();
                    out.threadId = tid;
                    return out;
                }
            }
        } catch (SQLException ex) {
            host.warn("Failed to resolve remote DM read receipt: " + ex.getMessage());
        }
        return out;
    }

    public synchronized ReadReceiptApplyResult applyRemoteReadReceipt(String relayId) {
        ReadReceiptApplyResult out = new ReadReceiptApplyResult();
        String id = cleanDeliveryId(relayId, 180);
        if (id.isBlank()) { out.error = "invalid_relay_id"; return out; }
        if (jsonlMode()) {
            JsonlMessage found = null;
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg != null && id.equals(msg.relayId)) { found = msg; break; }
            }
            if (found == null) { out.error = "message_not_found"; return out; }
            String remote = otherParticipant(found.threadId, found.senderUuid);
            if (RemotePlayerRef.parse(remote) == null) { out.error = "not_remote_message"; return out; }
            long before = lastReadMessageId(found.threadId, remote);
            if (!setLastReadAtLeast(found.threadId, remote, found.id)) { out.error = "read_state_failed"; return out; }
            out.ok = true;
            out.changed = before < found.id;
            out.threadId = found.threadId;
            out.localUserUuid = found.senderUuid;
            out.remoteUserUuid = remote;
            out.messageId = found.id;
            return out;
        }
        if (connection == null) { out.error = "store_unavailable"; return out; }
        String sql = "SELECT m.id,m.thread_id,m.sender_uuid FROM dm_messages m JOIN dm_delivery_state d ON d.message_id=m.id WHERE d.relay_id=? LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { out.error = "message_not_found"; return out; }
                long messageId = rs.getLong(1);
                String tid = rs.getString(2);
                String sender = normalizeUuid(rs.getString(3));
                String remote = otherParticipant(tid, sender);
                if (RemotePlayerRef.parse(remote) == null) { out.error = "not_remote_message"; return out; }
                long before = lastReadMessageId(tid, remote);
                if (!setLastReadAtLeast(tid, remote, messageId)) { out.error = "read_state_failed"; return out; }
                out.ok = true;
                out.changed = before < messageId;
                out.threadId = tid;
                out.localUserUuid = sender;
                out.remoteUserUuid = remote;
                out.messageId = messageId;
                return out;
            }
        } catch (SQLException ex) {
            out.error = "store_error";
            host.warn("Failed to apply remote DM read receipt: " + ex.getMessage());
            return out;
        }
    }

    public synchronized boolean hasRelayId(String relayId) {
        String id = cleanDeliveryId(relayId, 180);
        if (id.isBlank()) return false;
        if (jsonlMode()) {
            for (JsonlMessage msg : jsonlMessages.values()) if (id.equals(msg.relayId)) return true;
            return false;
        }
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM dm_delivery_state WHERE relay_id=? LIMIT 1")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { return false; }
    }

    public synchronized boolean updateDeliveryStatus(long messageId, String status, String error) {
        if (messageId <= 0) return false;
        String normalized = normalizeDeliveryStatus(status);
        String cleanError = cleanDeliveryId(error, 240);
        long now = System.currentTimeMillis();
        if (jsonlMode()) {
            JsonlMessage msg = jsonlMessages.get(messageId);
            if (msg == null) return false;
            msg.deliveryStatus = normalized;
            msg.deliveryError = cleanError;
            try {
                appendJsonlEvent(Map.of(
                        "type", "delivery",
                        "messageId", messageId,
                        "status", normalized,
                        "error", cleanError,
                        "relayId", msg.relayId == null ? "" : msg.relayId,
                        "clientMessageId", msg.clientMessageId == null ? "" : msg.clientMessageId));
                return true;
            } catch (IOException ex) {
                host.warn("Failed to update direct message delivery state: " + ex.getMessage());
                return false;
            }
        }
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE dm_delivery_state SET status=?,error=?,updated_at=? WHERE message_id=?")) {
            ps.setString(1, normalized);
            ps.setString(2, cleanError);
            ps.setLong(3, now);
            ps.setLong(4, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            host.warn("Failed to update direct message delivery state: " + ex.getMessage());
            return false;
        }
    }

    public synchronized RetryData retryData(String senderUuid, long messageId) {
        RetryData out = new RetryData();
        String sender = normalizeUuid(senderUuid);
        if (sender.isBlank() || messageId <= 0) { out.error = "invalid_message"; return out; }
        DirectMessageMessage message;
        try {
            message = jsonlMode() ? jsonlToMessage(jsonlMessages.get(messageId)) : readMessage(messageId);
        } catch (SQLException ex) {
            out.error = "store_error";
            return out;
        }
        if (message == null || !sender.equals(normalizeUuid(message.senderUuid))) { out.error = "message_not_found"; return out; }
        String target = otherParticipant(message.threadId, sender);
        RemotePlayerRef remote = RemotePlayerRef.parse(target);
        if (remote == null) { out.error = "not_remote"; return out; }
        String status = normalizeDeliveryStatus(message.deliveryStatus);
        if (!"failed".equals(status)) { out.error = "not_failed"; return out; }
        out.ok = true;
        out.message = message;
        out.targetUuid = target;
        out.targetServerId = remote.serverId;
        return out;
    }

    private synchronized DirectMessageMessage readMessage(long id) throws SQLException {
        if (connection == null) return null;
        try (PreparedStatement ps = connection.prepareStatement("SELECT id,thread_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,reply_to_relay_id FROM dm_messages WHERE id=? AND hidden=0")) {
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
            host.warn("Failed to resolve direct message thread: " + ex.getMessage());
            return "";
        }
    }

    private String jsonlThreadIdForMessage(String userUuid, long messageId) {
        String user = normalizeUuid(userUuid);
        JsonlMessage msg = jsonlMessages.get(messageId);
        if (user.isBlank() || msg == null || !jsonlVisibleFor(msg, user)) return "";
        return msg.threadId;
    }

    /** Returns a visible DM only when the requesting user is a participant in its thread. */
    public synchronized DirectMessageMessage messageForUser(String userUuid, long messageId) {
        String threadId = threadIdForMessage(userUuid, messageId);
        if (threadId.isBlank()) return null;
        if (jsonlMode()) {
            JsonlMessage msg = jsonlMessages.get(messageId);
            return msg == null ? null : jsonlToMessage(msg);
        }
        try {
            return readMessage(messageId);
        } catch (SQLException ex) {
            host.warn("Failed to resolve direct message for reply: " + ex.getMessage());
            return null;
        }
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
            host.warn("Failed to list direct message admin summaries: " + ex.getMessage());
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
        DirectMessageSettings c = host.directMessageSettings();
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
            host.warn("Failed to list direct message bodies: " + ex.getMessage());
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
                try { rewriteJsonl(); } catch (IOException ex) { host.warn("Failed to rewrite direct message JSONL after deleting thread: " + ex.getMessage()); }
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
            host.warn("Failed to delete direct message thread: " + ex.getMessage());
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
            host.warn("Failed to update direct message session flags: " + ex.getMessage());
            return false;
        }
    }

    public synchronized String cleanupPreviewJson() {
        Map<String,Object> m = new LinkedHashMap<>();
        DirectMessageSettings c = host.directMessageSettings();
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
        } catch (SQLException ex) { host.warn("Failed to calculate direct message cleanup preview: " + ex.getMessage()); }
        return JsonUtil.obj(m);
    }

    public synchronized boolean uploadNameReferenced(String name, String ignoreThreadId) {
        String n = String.valueOf(name == null ? "" : name).trim();
        if (!safeUploadReferenceName(n)) return false;
        String encoded = encodedUploadReferenceName(n);
        String ignore = String.valueOf(ignoreThreadId == null ? "" : ignoreThreadId).trim();
        if (jsonlMode()) {
            for (JsonlMessage msg : jsonlMessages.values()) {
                if (msg == null || msg.hidden || ignore.equals(msg.threadId)) continue;
                String body = String.valueOf(msg.body == null ? "" : msg.body);
                if (body.contains(n) || body.contains(encoded)) return true;
            }
            return false;
        }
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM dm_messages WHERE hidden=0 AND thread_id<>? AND (body LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') LIMIT 1")) {
            ps.setString(1, ignore);
            ps.setString(2, "%" + escapeLikeLiteral(n) + "%");
            ps.setString(3, "%" + escapeLikeLiteral(encoded) + "%");
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { return false; }
    }

    private boolean safeUploadReferenceName(String name) {
        return name != null && !name.isBlank() && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    private String encodedUploadReferenceName(String name) {
        return java.net.URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~");
    }

    private String escapeLikeLiteral(String value) {
        return String.valueOf(value == null ? "" : value).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
            host.warn("Failed to count direct message threads: " + ex.getMessage());
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
            host.warn("Failed to list direct message threads: " + ex.getMessage());
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
            String sql = "SELECT id,thread_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,reply_to_relay_id FROM dm_messages WHERE thread_id=? AND hidden=0 " +
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
            for (DirectMessageMessage message : out) applyReadReceiptState(message);
        } catch (SQLException ex) {
            host.warn("Failed to list direct messages: " + ex.getMessage());
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
        String sql = "SELECT id,thread_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,reply_to_relay_id FROM dm_messages WHERE thread_id=? AND hidden=0 "
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
            host.warn("Failed to list direct messages for administrator audit: " + ex.getMessage());
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
        if (markRead) {
            markRead(tid, user);
            for (DirectMessageMessage message : out) applyReadReceiptState(message);
        }
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
            host.warn("Failed to count direct messages: " + ex.getMessage());
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
            String sql = "SELECT id,thread_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,reply_to_relay_id FROM dm_messages WHERE thread_id=? AND hidden=0 " +
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
            if (effectivePage == 1) {
                markRead(tid, user);
                for (DirectMessageMessage message : out) applyReadReceiptState(message);
            }
        } catch (SQLException ex) {
            host.warn("Failed to list paged direct messages: " + ex.getMessage());
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
        m.replyToId = Math.max(0L, rs.getLong("reply_to_id"));
        m.replyToSender = String.valueOf(rs.getString("reply_to_sender") == null ? "" : rs.getString("reply_to_sender"));
        m.replyToPreview = String.valueOf(rs.getString("reply_to_preview") == null ? "" : rs.getString("reply_to_preview"));
        m.replyToRelayId = String.valueOf(rs.getString("reply_to_relay_id") == null ? "" : rs.getString("reply_to_relay_id"));
        applyDeliveryState(m);
        applyReadReceiptState(m);
        return m;
    }

    private void applyDeliveryState(DirectMessageMessage message) {
        if (message == null || connection == null || message.id <= 0) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT relay_id,client_message_id,status,error FROM dm_delivery_state WHERE message_id=?")) {
            ps.setLong(1, message.id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                message.relayId = String.valueOf(rs.getString(1) == null ? "" : rs.getString(1));
                message.clientMessageId = String.valueOf(rs.getString(2) == null ? "" : rs.getString(2));
                message.deliveryStatus = normalizeDeliveryStatus(rs.getString(3));
                message.deliveryError = String.valueOf(rs.getString(4) == null ? "" : rs.getString(4));
            }
        } catch (SQLException ignored) {
        }
    }

    private PlayerIdentity currentPlayerIdentity(String uuid) {
        return host.resolveIdentity(normalizeUuid(uuid));
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
            host.warn("Failed to mark direct message thread as read: " + ex.getMessage());
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
            host.warn("Failed to append direct message read state: " + ex.getMessage());
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
            host.warn("Failed to hide direct message: " + ex.getMessage());
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
            host.warn("Failed to append direct message hide state: " + ex.getMessage());
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
            host.warn("Failed to count unread direct messages: " + ex.getMessage());
            return 0;
        }
    }
}
