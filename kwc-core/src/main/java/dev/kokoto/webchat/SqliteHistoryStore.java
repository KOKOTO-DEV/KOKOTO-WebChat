package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * SqliteHistoryStore는 KWC 상태를 메모리/JSONL/SQLite 같은 영속 매체에 저장하고 조회하는 계층이다.
 * SqliteHistoryStore is a persistence layer storing and reading KWC state from memory, JSONL, SQLite, or another backing store.
 *
 * 조회 visibility와 mutation 권한을 분리하고, transaction/atomic rewrite가 필요한 작업은 중간 실패로 데이터가 반쯤 적용되지 않게 해야 한다.
 * Keep read visibility separate from mutation authorization, and use transactions/atomic rewrites where partial failure could leave inconsistent data.
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * KWC 유지보수 안내: 공개 채팅 history의 SQLite backend다. cursor pagination, around/search, hidden marker, retention prune를 제공하며 DB 손상 시 sidecar를 포함한 격리 복구를 수행한다. paging은 단순 row offset 대신 안정적인 message cursor를 사용해 동시 append에도 화면 순서를 유지한다.
 *
 * KWC maintenance note: SQLite backend for public-chat history, providing cursor pagination, around/search, hidden markers, retention pruning, and corruption quarantine including sidecars. Paging uses stable message cursors rather than row offsets so concurrent appends do not disturb visible ordering.
 */
public final class SqliteHistoryStore implements AutoCloseable {
    public static class Page {
        public final List<ChatMessage> messages = new ArrayList<>();
        public boolean hasBefore;
        public boolean hasAfter;
        public String oldestId = "";
        public String newestId = "";
    }

    public static final class AroundPage extends Page {
        public int targetIndex = -1;
    }

    private final CoreLogger logger;
    private final Path path;
    private final Connection conn;

    private SqliteHistoryStore(CoreLogger logger, Path path, Connection conn) {
        this.logger = logger;
        this.path = path;
        this.conn = conn;
    }

    private static final DateTimeFormatter CORRUPT_BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // DB 파일과 WAL/SHM sidecar를 검사한 뒤 SQLite를 연다. quick_check 실패나 orphan sidecar가 있으면 기존 파일 세트를 격리하고 새 DB를 시작해 부분 손상을 그대로 사용하지 않는다.
    // Checks the database and WAL/SHM sidecars before opening SQLite. Failed quick_check or orphan sidecars quarantine the existing database set and start fresh rather than continuing on partially corrupt state.
    public static SqliteHistoryStore open(CoreLogger logger, Path path) throws SQLException, IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // DriverManager can still discover the shaded driver through services.
        }

        quarantineOrphanSidecars(logger, normalized);
        if (Files.isRegularFile(normalized) && Files.size(normalized) > 0L) {
            String integrityError = quickCheck(normalized);
            if (integrityError != null) {
                Path backupDir = quarantineDatabaseSet(logger, normalized, integrityError);
                logger.warn("SQLite chat history failed integrity validation and was replaced with a new empty database. "
                        + "The damaged database set was preserved at: " + backupDir);
            }
        }

        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + normalized);
        SqliteHistoryStore store = new SqliteHistoryStore(logger, normalized, conn);
        store.initialize();
        return store;
    }

    private static String quickCheck(Path path) {
        String readOnlyUrl = "jdbc:sqlite:" + path.toUri().toASCIIString() + "?mode=ro";
        try (Connection probe = DriverManager.getConnection(readOnlyUrl);
             Statement st = probe.createStatement()) {
            st.execute("PRAGMA busy_timeout=5000");
            try (ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
                StringBuilder errors = new StringBuilder();
                while (rs.next()) {
                    String line = rs.getString(1);
                    if (line == null || line.isBlank() || "ok".equalsIgnoreCase(line.trim())) continue;
                    if (errors.length() >= 1200) break;
                    if (errors.length() > 0) errors.append(" | ");
                    String trimmed = line.trim();
                    int remaining = Math.max(0, 1200 - errors.length());
                    errors.append(trimmed, 0, Math.min(trimmed.length(), remaining));
                }
                return errors.length() == 0 ? null : errors.toString();
            }
        } catch (SQLException ex) {
            return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
        }
    }

    private static void quarantineOrphanSidecars(CoreLogger logger, Path dbPath) throws IOException {
        Path wal = sidecar(dbPath, "-wal");
        Path shm = sidecar(dbPath, "-shm");
        if (!Files.exists(wal) && !Files.exists(shm)) return;
        boolean missingMain = !Files.exists(dbPath);
        boolean emptyMain = Files.isRegularFile(dbPath) && Files.size(dbPath) == 0L;
        if (!missingMain && !emptyMain) return;
        Path backupDir = nextCorruptBackupDirectory(dbPath);
        Files.createDirectories(backupDir);
        moveIfExists(dbPath, backupDir.resolve(dbPath.getFileName()));
        moveIfExists(wal, backupDir.resolve(dbPath.getFileName().toString() + "-wal"));
        moveIfExists(shm, backupDir.resolve(dbPath.getFileName().toString() + "-shm"));
        logger.warn("Found SQLite WAL/SHM files without a usable main chat history database. "
                + "They were isolated before creating a new database: " + backupDir);
    }

    private static Path quarantineDatabaseSet(CoreLogger logger, Path dbPath, String reason) throws IOException {
        Path backupDir = nextCorruptBackupDirectory(dbPath);
        Files.createDirectories(backupDir);
        moveIfExists(dbPath, backupDir.resolve(dbPath.getFileName()));
        moveIfExists(sidecar(dbPath, "-wal"), backupDir.resolve(dbPath.getFileName().toString() + "-wal"));
        moveIfExists(sidecar(dbPath, "-shm"), backupDir.resolve(dbPath.getFileName().toString() + "-shm"));
        try {
            Files.writeString(backupDir.resolve("CORRUPTION.txt"),
                    "KOKOTO WebChat SQLite history integrity check failed.\n"
                            + "Reason: " + reason + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            logger.warn("Failed to write SQLite corruption note: " + ex.getMessage());
        }
        return backupDir;
    }

    private static Path nextCorruptBackupDirectory(Path dbPath) {
        Path parent = dbPath.getParent();
        String base = dbPath.getFileName().toString() + ".corrupt-" + CORRUPT_BACKUP_TIME.format(LocalDateTime.now());
        Path candidate = parent.resolve(base);
        int suffix = 2;
        while (Files.exists(candidate)) candidate = parent.resolve(base + "-" + suffix++);
        return candidate;
    }

    private static Path sidecar(Path dbPath, String suffix) {
        return dbPath.resolveSibling(dbPath.getFileName().toString() + suffix);
    }

    private static void moveIfExists(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.move(source, target);
    }

    private synchronized void initialize() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("CREATE TABLE IF NOT EXISTS chat_messages ("
                    + "seq INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "id TEXT NOT NULL UNIQUE,"
                    + "time INTEGER NOT NULL,"
                    + "source TEXT NOT NULL DEFAULT '',"
                    + "sender TEXT NOT NULL DEFAULT '',"
                    + "real_sender TEXT NOT NULL DEFAULT '',"
                    + "player_uuid TEXT NOT NULL DEFAULT '',"
                    + "relay_id TEXT NOT NULL DEFAULT '',"
                    + "origin_server_id TEXT NOT NULL DEFAULT '',"
                    + "origin_server_name TEXT NOT NULL DEFAULT '',"
                    + "relay_hop INTEGER NOT NULL DEFAULT 0,"
                    + "role TEXT NOT NULL DEFAULT '',"
                    + "message TEXT NOT NULL DEFAULT '',"
                    + "i18n_key TEXT NOT NULL DEFAULT '',"
                    + "i18n_args TEXT NOT NULL DEFAULT '',"
                    + "reply_to_id TEXT NOT NULL DEFAULT '',"
                    + "reply_to_sender TEXT NOT NULL DEFAULT '',"
                    + "reply_to_preview TEXT NOT NULL DEFAULT '',"
                    + "hidden INTEGER NOT NULL DEFAULT 0"
                    + ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_chat_messages_time_seq ON chat_messages(time, seq)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_chat_messages_seq ON chat_messages(seq)");
            ensureColumn(st, "relay_id", "TEXT NOT NULL DEFAULT ''");
            ensureColumn(st, "origin_server_id", "TEXT NOT NULL DEFAULT ''");
            ensureColumn(st, "origin_server_name", "TEXT NOT NULL DEFAULT ''");
            ensureColumn(st, "relay_hop", "INTEGER NOT NULL DEFAULT 0");
            st.execute("CREATE INDEX IF NOT EXISTS idx_chat_messages_id ON chat_messages(id)");
        }
    }

    private void ensureColumn(Statement st, String column, String definition) throws SQLException {
        boolean exists = false;
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(chat_messages)")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) st.execute("ALTER TABLE chat_messages ADD COLUMN " + column + " " + definition);
    }

    public Path path() {
        return path;
    }

    public synchronized int count() {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM chat_messages")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            warn("Failed to count SQLite chat history", ex);
            return 0;
        }
    }

    public synchronized void insert(ChatMessage msg) {
        if (msg == null || msg.id == null || msg.id.isBlank()) return;
        // Message IDs are immutable history identities. Do not use INSERT OR REPLACE here:
        // SQLite REPLACE deletes the existing row before inserting a new one, which assigns
        // a new AUTOINCREMENT seq. History paging is seq-based, so a reconnect/replay of an
        // already persisted message could move an old message to the newest edge and make the
        // web viewport jump. First writer wins for an existing id; this also prevents a replay
        // from resurrecting a row that was already hidden by moderation.
        String sql = "INSERT INTO chat_messages "
                + "(id,time,source,sender,real_sender,player_uuid,relay_id,origin_server_id,origin_server_name,relay_hop,role,message,i18n_key,i18n_args,reply_to_id,reply_to_sender,reply_to_preview,hidden) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindMessage(ps, msg);
            ps.executeUpdate();
        } catch (SQLException ex) {
            warn("Failed to insert SQLite chat history message", ex);
        }
    }

    private void bindMessage(PreparedStatement ps, ChatMessage msg) throws SQLException {
        ps.setString(1, nz(msg.id));
        ps.setLong(2, msg.time);
        ps.setString(3, nz(msg.source));
        ps.setString(4, nz(msg.sender));
        ps.setString(5, nz(msg.realSender));
        ps.setString(6, nz(msg.playerUuid));
        ps.setString(7, nz(msg.relayId));
        ps.setString(8, nz(msg.originServerId));
        ps.setString(9, nz(msg.originServerName));
        ps.setInt(10, msg.relayHop);
        ps.setString(11, nz(msg.role));
        ps.setString(12, nz(msg.message));
        ps.setString(13, nz(msg.i18nKey));
        ps.setString(14, nz(msg.i18nArgs));
        ps.setString(15, nz(msg.replyToId));
        ps.setString(16, nz(msg.replyToSender));
        ps.setString(17, nz(msg.replyToPreview));
        ps.setInt(18, msg.hidden ? 1 : 0);
    }

    private static final class Cursor {
        final long time;
        final long seq;

        Cursor(long time, long seq) {
            this.time = time;
            this.seq = seq;
        }
    }

    // before/after cursor를 사용해 안정적인 history 페이지를 만든다. 결과 edge에서 hasMoreBefore/After를 별도 계산해 UI가 더 불러올 데이터가 있는지 정확히 판단할 수 있게 한다.
    // Builds stable history pages using before/after cursors. It separately computes hasMoreBefore/After at the result edges so the UI knows precisely whether more data can be loaded.
    public synchronized Page page(String beforeId, String afterId, int limit, long cutoff) {
        Page page = new Page();
        int actualLimit = limit <= 0 ? 500 : Math.max(1, Math.min(500, limit));
        try {
            if (afterId != null && !afterId.isBlank()) {
                Cursor cursor = cursorForId(afterId);
                if (cursor == null) return latest(actualLimit, cutoff);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM chat_messages WHERE (time > ? OR (time = ? AND seq > ?)) AND time >= ? AND hidden = 0 ORDER BY time ASC, seq ASC LIMIT ?")) {
                    ps.setLong(1, cursor.time);
                    ps.setLong(2, cursor.time);
                    ps.setLong(3, cursor.seq);
                    ps.setLong(4, cutoff);
                    ps.setInt(5, actualLimit);
                    readInto(page.messages, ps);
                }
            } else if (beforeId != null && !beforeId.isBlank()) {
                Cursor cursor = cursorForId(beforeId);
                if (cursor == null) return latest(actualLimit, cutoff);
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM chat_messages WHERE (time < ? OR (time = ? AND seq < ?)) AND time >= ? AND hidden = 0 ORDER BY time DESC, seq DESC LIMIT ?")) {
                    ps.setLong(1, cursor.time);
                    ps.setLong(2, cursor.time);
                    ps.setLong(3, cursor.seq);
                    ps.setLong(4, cutoff);
                    ps.setInt(5, actualLimit);
                    readInto(page.messages, ps);
                }
                Collections.reverse(page.messages);
            } else {
                return latest(actualLimit, cutoff);
            }
            fillPageEdges(page, cutoff);
        } catch (SQLException ex) {
            warn("Failed to read SQLite chat history page", ex);
        }
        return page;
    }

    public synchronized Page latest(int limit, long cutoff) {
        Page page = new Page();
        int actualLimit = limit <= 0 ? 500 : Math.max(1, Math.min(500, limit));
        // `seq` is an insertion-order tie breaker only. Older KWC builds used
        // INSERT OR REPLACE for duplicate message IDs, which could move a replayed
        // old row to a very large seq and make it look like the newest message
        // after restart. Ordering by the immutable message timestamp first makes
        // existing affected databases self-heal without rewriting or deleting rows.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM chat_messages WHERE time >= ? AND hidden = 0 ORDER BY time DESC, seq DESC LIMIT ?")) {
            ps.setLong(1, cutoff);
            ps.setInt(2, actualLimit);
            readInto(page.messages, ps);
            Collections.reverse(page.messages);
            fillPageEdges(page, cutoff);
        } catch (SQLException ex) {
            warn("Failed to read latest SQLite chat history", ex);
        }
        return page;
    }

    public synchronized AroundPage around(String targetId, int before, int after, long cutoff) {
        AroundPage result = new AroundPage();
        if (targetId == null || targetId.isBlank()) return result;
        try {
            Cursor target = cursorForId(targetId);
            if (target == null) return result;
            List<ChatMessage> beforeList = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM chat_messages WHERE (time < ? OR (time = ? AND seq < ?)) AND time >= ? AND hidden = 0 ORDER BY time DESC, seq DESC LIMIT ?")) {
                ps.setLong(1, target.time);
                ps.setLong(2, target.time);
                ps.setLong(3, target.seq);
                ps.setLong(4, cutoff);
                ps.setInt(5, Math.max(0, before));
                readInto(beforeList, ps);
            }
            Collections.reverse(beforeList);
            ChatMessage targetMessage = messageBySeq(target.seq);
            List<ChatMessage> afterList = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM chat_messages WHERE (time > ? OR (time = ? AND seq > ?)) AND time >= ? AND hidden = 0 ORDER BY time ASC, seq ASC LIMIT ?")) {
                ps.setLong(1, target.time);
                ps.setLong(2, target.time);
                ps.setLong(3, target.seq);
                ps.setLong(4, cutoff);
                ps.setInt(5, Math.max(0, after));
                readInto(afterList, ps);
            }
            result.messages.addAll(beforeList);
            result.targetIndex = result.messages.size();
            if (targetMessage != null) result.messages.add(targetMessage);
            result.messages.addAll(afterList);
            fillPageEdges(result, cutoff);
        } catch (SQLException ex) {
            warn("Failed to read SQLite chat history around " + targetId, ex);
        }
        return result;
    }

    // SQL LIKE 검색과 source/system/date filter를 적용한다. 표시용 i18n 메시지처럼 DB body와 화면 문자열이 다를 수 있는 경우에는 별도 candidate 검색 경로와 조합된다.
    // Applies SQL LIKE plus source/system/date filtering. For i18n messages whose stored body differs from displayed text, it is combined with a separate candidate-search path.
    public synchronized List<ChatMessage> search(String query, int limit, long cutoff) {
        return search(query, limit, cutoff, Long.MIN_VALUE, Long.MAX_VALUE, "", "", true);
    }

    public synchronized List<ChatMessage> search(String query, int limit, long cutoff,
                                                 long from, long to, String senderFilter, String sourceFilter, boolean includeSystem) {
        List<ChatMessage> result = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        String sender = senderFilter == null ? "" : senderFilter.trim();
        int actualLimit = Math.max(1, limit <= 0 ? 50 : limit);
        long minTime = cutoff;
        if (from != Long.MIN_VALUE) minTime = Math.max(minTime, from);
        StringBuilder sql = new StringBuilder("SELECT * FROM chat_messages WHERE time >= ? AND hidden = 0 ");
        List<Object> params = new ArrayList<>();
        params.add(minTime);
        if (to != Long.MAX_VALUE) {
            sql.append("AND time <= ? ");
            params.add(to);
        }
        appendSourceFilter(sql, params, sourceFilter, includeSystem);
        if (!sender.isBlank()) {
            String senderLike = likePattern(sender);
            sql.append("AND (LOWER(sender) LIKE ? ESCAPE '\\' OR LOWER(real_sender) LIKE ? ESCAPE '\\') ");
            params.add(senderLike);
            params.add(senderLike);
        }
        if (!q.isBlank()) {
            String like = likePattern(q);
            sql.append("AND (LOWER(message) LIKE ? ESCAPE '\\' OR LOWER(sender) LIKE ? ESCAPE '\\' OR LOWER(real_sender) LIKE ? ESCAPE '\\') ");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY time DESC, seq DESC LIMIT ?");
        params.add(actualLimit);
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            readInto(result, ps);
        } catch (SQLException ex) {
            warn("Failed to search SQLite chat history", ex);
        }
        return result;
    }

    public synchronized List<ChatMessage> i18nSearchCandidates(long cutoff, int maxRows) {
        return i18nSearchCandidates(cutoff, maxRows, Long.MIN_VALUE, Long.MAX_VALUE, "", true);
    }

    public synchronized List<ChatMessage> i18nSearchCandidates(long cutoff, int maxRows,
                                                               long from, long to, String sourceFilter, boolean includeSystem) {
        List<ChatMessage> result = new ArrayList<>();
        int actualLimit = Math.max(1, maxRows <= 0 ? 50 : maxRows);
        long minTime = cutoff;
        if (from != Long.MIN_VALUE) minTime = Math.max(minTime, from);
        StringBuilder sql = new StringBuilder("SELECT * FROM chat_messages WHERE time >= ? AND hidden = 0 AND i18n_key <> '' ");
        List<Object> params = new ArrayList<>();
        params.add(minTime);
        if (to != Long.MAX_VALUE) {
            sql.append("AND time <= ? ");
            params.add(to);
        }
        appendSourceFilter(sql, params, sourceFilter, includeSystem);
        sql.append("ORDER BY time DESC, seq DESC LIMIT ?");
        params.add(actualLimit);
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            readInto(result, ps);
        } catch (SQLException ex) {
            warn("Failed to read SQLite localized-search candidates", ex);
        }
        return result;
    }

    private String likePattern(String raw) {
        return "%" + String.valueOf(raw == null ? "" : raw).toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private String normalizeSourceFilter(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("all")) return "";
        if (value.equals("game") || value.equals("web") || value.equals("discord") || value.equals("system")) return value;
        if (value.equals("event") || value.equals("server")) return "system";
        return "";
    }

    private void appendSourceFilter(StringBuilder sql, List<Object> params, String sourceFilter, boolean includeSystem) {
        String source = normalizeSourceFilter(sourceFilter);
        if (!includeSystem) {
            sql.append("AND LOWER(source) NOT IN ('system','event','server') ");
        }
        if (source.isBlank()) return;
        if (source.equals("system")) {
            sql.append("AND LOWER(source) IN ('system','event','server') ");
        } else {
            sql.append("AND LOWER(source) = ? ");
            params.add(source);
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            int idx = i + 1;
            if (value instanceof Long) ps.setLong(idx, (Long) value);
            else if (value instanceof Integer) ps.setInt(idx, (Integer) value);
            else ps.setString(idx, String.valueOf(value == null ? "" : value));
        }
    }

    /** Returns the inclusive persisted chronological range between two visible public messages. */
    public synchronized List<ChatMessage> rangeInclusive(String firstId, String lastId, int limit, long cutoff) {
        List<ChatMessage> out = new ArrayList<>();
        if (firstId == null || firstId.isBlank() || lastId == null || lastId.isBlank()) return out;
        int max = Math.max(1, Math.min(limit <= 0 ? 1000 : limit, ConversationArchiveStore.MAX_CONFIGURED_MESSAGES_PER_ARCHIVE));
        try {
            Cursor first = cursorForId(firstId);
            Cursor last = cursorForId(lastId);
            if (first == null || last == null) return out;
            Cursor lo = compareCursor(first, last) <= 0 ? first : last;
            Cursor hi = compareCursor(first, last) <= 0 ? last : first;
            String sql = "SELECT * FROM chat_messages WHERE "
                    + "(time > ? OR (time = ? AND seq >= ?)) AND "
                    + "(time < ? OR (time = ? AND seq <= ?)) AND "
                    + "time >= ? AND hidden=0 ORDER BY time ASC, seq ASC LIMIT ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, lo.time); ps.setLong(2, lo.time); ps.setLong(3, lo.seq);
                ps.setLong(4, hi.time); ps.setLong(5, hi.time); ps.setLong(6, hi.seq);
                ps.setLong(7, cutoff); ps.setInt(8, max + 1);
                readInto(out, ps);
            }
            if (out.size() > max) return new ArrayList<>();
            return out;
        } catch (SQLException ex) {
            warn("Failed to read SQLite chat history range", ex);
            return new ArrayList<>();
        }
    }

    public synchronized ChatMessage find(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Long seq = seqForId(id);
            return seq == null ? null : messageBySeq(seq);
        } catch (SQLException ex) {
            warn("Failed to find SQLite chat history message " + id, ex);
            return null;
        }
    }

    public synchronized boolean markHidden(String id) {
        if (id == null || id.isBlank()) return false;
        try (PreparedStatement ps = conn.prepareStatement("UPDATE chat_messages SET hidden = 1 WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            warn("Failed to hide SQLite chat history message " + id, ex);
            return false;
        }
    }

    public synchronized void clear() {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM chat_messages");
        } catch (SQLException ex) {
            warn("Failed to clear SQLite chat history", ex);
        }
    }

    public synchronized void prune(int maxMessages, long cutoff) {
        try {
            if (cutoff != Long.MIN_VALUE) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chat_messages WHERE time < ?")) {
                    ps.setLong(1, cutoff);
                    ps.executeUpdate();
                }
            }
            if (maxMessages > 0) {
                // Retain the chronologically newest rows. This also prevents a
                // legacy replay-inflated seq from protecting an old row while a
                // genuinely newer message is pruned.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM chat_messages WHERE id NOT IN (SELECT id FROM chat_messages ORDER BY time DESC, seq DESC LIMIT ?)")) {
                    ps.setInt(1, maxMessages);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            warn("Failed to prune SQLite chat history", ex);
        }
    }

    public synchronized boolean uploadNameReferenced(String name) {
        String n = String.valueOf(name == null ? "" : name).trim();
        if (!safeUploadReferenceName(n)) return false;
        String encoded = encodedUploadReferenceName(n);
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM chat_messages WHERE hidden=0 AND (message LIKE ? ESCAPE '\\' OR message LIKE ? ESCAPE '\\') LIMIT 1")) {
            ps.setString(1, "%" + escapeLikeLiteral(n) + "%");
            ps.setString(2, "%" + escapeLikeLiteral(encoded) + "%");
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) {
            warn("Failed to check SQLite chat history upload reference", ex);
            return false;
        }
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

    public synchronized int migrateJsonlIfEmpty(Path jsonl, long cutoff, int maxMessages) {
        if (jsonl == null || !Files.exists(jsonl) || count() > 0) return 0;
        int migrated = 0;
        try {
            for (String line : Files.readAllLines(jsonl, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) continue;
                ChatMessage msg = ChatMessage.fromMap(JsonUtil.parseFlatObject(line));
                if (msg.message == null || msg.message.isBlank()) continue;
                if (cutoff != Long.MIN_VALUE && msg.time < cutoff) continue;
                insert(msg);
                migrated++;
            }
            prune(maxMessages, cutoff);
        } catch (IOException ex) {
            logger.warn("Failed to migrate JSONL chat history to SQLite from " + jsonl + ": " + ex.getMessage());
        }
        return migrated;
    }

    private void readInto(List<ChatMessage> out, PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(fromResultSet(rs));
        }
    }

    private void fillPageEdges(Page page, long cutoff) throws SQLException {
        if (page.messages.isEmpty()) {
            page.hasBefore = hasAny(cutoff);
            page.hasAfter = false;
            return;
        }
        ChatMessage first = page.messages.get(0);
        ChatMessage last = page.messages.get(page.messages.size() - 1);
        page.oldestId = nz(first.id);
        page.newestId = nz(last.id);
        Cursor firstCursor = cursorForId(first.id);
        Cursor lastCursor = cursorForId(last.id);
        page.hasBefore = firstCursor != null && existsBefore(firstCursor, cutoff);
        page.hasAfter = lastCursor != null && existsAfter(lastCursor, cutoff);
    }

    private boolean hasAny(long cutoff) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM chat_messages WHERE time >= ? AND hidden = 0 LIMIT 1")) {
            ps.setLong(1, cutoff);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean existsBefore(Cursor cursor, long cutoff) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM chat_messages WHERE (time < ? OR (time = ? AND seq < ?)) AND time >= ? AND hidden = 0 LIMIT 1")) {
            ps.setLong(1, cursor.time);
            ps.setLong(2, cursor.time);
            ps.setLong(3, cursor.seq);
            ps.setLong(4, cutoff);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private boolean existsAfter(Cursor cursor, long cutoff) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM chat_messages WHERE (time > ? OR (time = ? AND seq > ?)) AND time >= ? AND hidden = 0 LIMIT 1")) {
            ps.setLong(1, cursor.time);
            ps.setLong(2, cursor.time);
            ps.setLong(3, cursor.seq);
            ps.setLong(4, cutoff);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private Cursor cursorForId(String id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT time,seq FROM chat_messages WHERE id = ? AND hidden = 0")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Cursor(rs.getLong(1), rs.getLong(2)) : null;
            }
        }
    }

    private static int compareCursor(Cursor a, Cursor b) {
        int byTime = Long.compare(a.time, b.time);
        return byTime != 0 ? byTime : Long.compare(a.seq, b.seq);
    }

    private Long seqForId(String id) throws SQLException {
        Cursor cursor = cursorForId(id);
        return cursor == null ? null : cursor.seq;
    }

    private ChatMessage messageBySeq(long seq) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM chat_messages WHERE seq = ? AND hidden = 0")) {
            ps.setLong(1, seq);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fromResultSet(rs) : null;
            }
        }
    }

    private ChatMessage fromResultSet(ResultSet rs) throws SQLException {
        ChatMessage msg = new ChatMessage(rs.getLong("time"), rs.getString("source"), rs.getString("sender"), rs.getString("role"), rs.getString("message"));
        msg.id = rs.getString("id");
        msg.realSender = rs.getString("real_sender");
        msg.playerUuid = rs.getString("player_uuid");
        msg.relayId = rs.getString("relay_id");
        msg.originServerId = rs.getString("origin_server_id");
        msg.originServerName = rs.getString("origin_server_name");
        msg.relayHop = rs.getInt("relay_hop");
        msg.i18nKey = rs.getString("i18n_key");
        msg.i18nArgs = rs.getString("i18n_args");
        msg.replyToId = rs.getString("reply_to_id");
        msg.replyToSender = rs.getString("reply_to_sender");
        msg.replyToPreview = rs.getString("reply_to_preview");
        msg.hidden = rs.getInt("hidden") != 0;
        return msg;
    }

    private void warn(String message, Exception ex) {
        logger.warn(message + ": " + ex.getMessage());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    @Override
    public synchronized void close() {
        try {
            conn.close();
        } catch (SQLException ex) {
            warn("Failed to close SQLite chat history", ex);
        }
    }
}
