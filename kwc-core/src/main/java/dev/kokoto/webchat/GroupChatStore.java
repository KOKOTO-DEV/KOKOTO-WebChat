package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * GroupChatStore는 KWC 상태를 메모리/JSONL/SQLite 같은 영속 매체에 저장하고 조회하는 계층이다.
 * GroupChatStore is a persistence layer storing and reading KWC state from memory, JSONL, SQLite, or another backing store.
 *
 * 조회 visibility와 mutation 권한을 분리하고, transaction/atomic rewrite가 필요한 작업은 중간 실패로 데이터가 반쯤 적용되지 않게 해야 한다.
 * Keep read visibility separate from mutation authorization, and use transactions/atomic rewrites where partial failure could leave inconsistent data.
 */
import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * KWC 유지보수 안내: 그룹방, 멤버십, room-local role, 메시지, 읽음 상태, pin, ban/hidden-room 상태를 SQLite에 저장한다. owner/admin/member 권한은 전역 KWC role과 독립이며, 모든 mutation 메서드는 caller UUID와 room membership/role을 저장소에서 다시 검증한다.
 *
 * KWC maintenance note: SQLite persistence for group rooms, membership, room-local roles, messages, read state, pins, bans, and hidden-room state. owner/admin/member authority is independent of global KWC roles, and every mutation method revalidates caller UUID plus room membership/role in storage.
 */
public class GroupChatStore {
    private final ConversationStoreHost host;
    private Connection connection;

    public GroupChatStore(ConversationStoreHost host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    public synchronized void open() {
        close();
        GroupChatSettings c = host.groupChatSettings();
        if (c == null || !c.groupChatEnabled) return;
        try {
            File file = resolveFile(c.groupChatSqliteFile == null || c.groupChatSqliteFile.isBlank() ? "group-messages.db" : c.groupChatSqliteFile);
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
            host.warn("Failed to open group chat SQLite store: " + ex.getMessage());
            close();
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
        }
    }

    public boolean available() {
        return connection != null;
    }

    private File resolveFile(String configured) {
        String name = configured == null || configured.isBlank() ? "group-messages.db" : configured;
        File file = new File(name);
        if (!file.isAbsolute()) file = new File(host.dataDirectory().toFile(), name);
        return file;
    }

    private void initSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS group_rooms (" +
                    "id TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "owner_uuid TEXT NOT NULL," +
                    "visibility TEXT NOT NULL DEFAULT 'private'," +
                    "password_hash TEXT NOT NULL DEFAULT ''," +
                    "created_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL," +
                    "archived INTEGER NOT NULL DEFAULT 0," +
                    "locked INTEGER NOT NULL DEFAULT 0," +
                    "retention_exempt INTEGER NOT NULL DEFAULT 0," +
                    "membership_events_enabled INTEGER NOT NULL DEFAULT 1," +
                    "pins_enabled INTEGER NOT NULL DEFAULT 1," +
                    "message_delete_enabled INTEGER NOT NULL DEFAULT 1," +
                    "member_self_delete_enabled INTEGER NOT NULL DEFAULT 1" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_rooms_visibility ON group_rooms(visibility, updated_at)");
            addColumnIfMissing(st, "group_rooms", "locked", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(st, "group_rooms", "retention_exempt", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(st, "group_rooms", "membership_events_enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(st, "group_rooms", "pins_enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(st, "group_rooms", "message_delete_enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(st, "group_rooms", "member_self_delete_enabled", "INTEGER NOT NULL DEFAULT 1");
            st.execute("CREATE TABLE IF NOT EXISTS group_members (" +
                    "room_id TEXT NOT NULL," +
                    "user_uuid TEXT NOT NULL," +
                    "role TEXT NOT NULL DEFAULT 'member'," +
                    "joined_at INTEGER NOT NULL," +
                    "last_read_message_id INTEGER NOT NULL DEFAULT 0," +
                    "muted INTEGER NOT NULL DEFAULT 0," +
                    "hidden INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(room_id, user_uuid)" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_members_user ON group_members(user_uuid, hidden)");
            st.execute("CREATE TABLE IF NOT EXISTS group_bans (" +
                    "room_id TEXT NOT NULL," +
                    "user_uuid TEXT NOT NULL," +
                    "banned_by_uuid TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "PRIMARY KEY(room_id, user_uuid)" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_bans_user ON group_bans(user_uuid)");
            st.execute("CREATE TABLE IF NOT EXISTS group_invites (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "room_id TEXT NOT NULL," +
                    "inviter_uuid TEXT NOT NULL," +
                    "invitee_uuid TEXT NOT NULL," +
                    "status TEXT NOT NULL DEFAULT 'pending'," +
                    "created_at INTEGER NOT NULL," +
                    "expires_at INTEGER NOT NULL" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_invites_invitee ON group_invites(invitee_uuid, status, expires_at)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_group_invites_unique_pending ON group_invites(room_id, invitee_uuid, status)");
            st.execute("CREATE TABLE IF NOT EXISTS group_messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "room_id TEXT NOT NULL," +
                    "sender_uuid TEXT NOT NULL," +
                    "body TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "hidden INTEGER NOT NULL DEFAULT 0," +
                    "client_message_id TEXT NOT NULL DEFAULT ''," +
                    "reply_to_id INTEGER NOT NULL DEFAULT 0," +
                    "reply_to_sender TEXT NOT NULL DEFAULT ''," +
                    "reply_to_preview TEXT NOT NULL DEFAULT ''," +
                    "event_type TEXT NOT NULL DEFAULT ''" +
                    ")");
            addColumnIfMissing(st, "group_messages", "client_message_id", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(st, "group_messages", "reply_to_id", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(st, "group_messages", "reply_to_sender", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(st, "group_messages", "reply_to_preview", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(st, "group_messages", "event_type", "TEXT NOT NULL DEFAULT ''");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_messages_room ON group_messages(room_id, id)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_group_messages_client_id ON group_messages(client_message_id) WHERE client_message_id<>''");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_messages_created ON group_messages(created_at)");
            st.execute("CREATE TABLE IF NOT EXISTS group_message_state (" +
                    "message_id INTEGER NOT NULL," +
                    "user_uuid TEXT NOT NULL," +
                    "hidden INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(message_id, user_uuid)" +
                    ")");
            st.execute("CREATE TABLE IF NOT EXISTS group_pins (" +
                    "pin_id TEXT PRIMARY KEY," +
                    "room_id TEXT NOT NULL," +
                    "message_id INTEGER NOT NULL," +
                    "pinned_at INTEGER NOT NULL," +
                    "sort_order INTEGER NOT NULL," +
                    "pinned_by_uuid TEXT NOT NULL DEFAULT ''," +
                    "pinned_by_username TEXT NOT NULL DEFAULT ''," +
                    "pinned_by_display_name TEXT NOT NULL DEFAULT ''," +
                    "message_time INTEGER NOT NULL," +
                    "sender_uuid TEXT NOT NULL DEFAULT ''," +
                    "sender_username TEXT NOT NULL DEFAULT ''," +
                    "sender_display_name TEXT NOT NULL DEFAULT ''," +
                    "body TEXT NOT NULL DEFAULT ''," +
                    "event_type TEXT NOT NULL DEFAULT ''" +
                    ")");
            addColumnIfMissing(st, "group_pins", "pinned_by_username", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(st, "group_pins", "pinned_by_display_name", "TEXT NOT NULL DEFAULT ''");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_pins_room ON group_pins(room_id, sort_order, pinned_at)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_group_pins_message ON group_pins(room_id, message_id)");
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
        if (connection == null) return;
        GroupChatSettings c = host.groupChatSettings();
        try {
            if (c != null && c.groupChatRetentionDays > 0) {
                long cutoff = System.currentTimeMillis() - c.groupChatRetentionDays * 24L * 60L * 60L * 1000L;
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_messages WHERE created_at < ? AND room_id NOT IN (SELECT id FROM group_rooms WHERE retention_exempt=1)")) {
                    ps.setLong(1, cutoff);
                    ps.executeUpdate();
                }
            }
            if (c != null && c.groupChatMaxMessagesPerRoom > 0) {
                List<String> ids = new ArrayList<>();
                try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM group_rooms WHERE archived=0")) {
                    while (rs.next()) ids.add(rs.getString(1));
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM group_messages WHERE room_id=? AND id NOT IN (SELECT id FROM group_messages WHERE room_id=? ORDER BY id DESC LIMIT ?)")) {
                    for (String id : ids) {
                        ps.setString(1, id);
                        ps.setString(2, id);
                        ps.setInt(3, c.groupChatMaxMessagesPerRoom);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_invites SET status='expired' WHERE status='pending' AND expires_at > 0 AND expires_at < ?")) {
                ps.setLong(1, now);
                ps.executeUpdate();
            }
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DELETE FROM group_message_state WHERE message_id NOT IN (SELECT id FROM group_messages)");
                if (c != null && c.groupChatRetentionDays > 0) {
                    long cutoff = System.currentTimeMillis() - c.groupChatRetentionDays * 24L * 60L * 60L * 1000L;
                    st.executeUpdate("DELETE FROM group_rooms WHERE retention_exempt=0 AND updated_at < " + cutoff + " AND id NOT IN (SELECT DISTINCT room_id FROM group_messages)");
                }
                st.executeUpdate("DELETE FROM group_invites WHERE room_id NOT IN (SELECT id FROM group_rooms)");
                st.executeUpdate("DELETE FROM group_members WHERE room_id NOT IN (SELECT id FROM group_rooms)");
                st.executeUpdate("DELETE FROM group_bans WHERE room_id NOT IN (SELECT id FROM group_rooms)");
            }
        } catch (SQLException ex) {
            host.warn("Failed to cleanup group chat: " + ex.getMessage());
        }
    }

    // 새 room과 owner membership을 하나의 저장 흐름으로 만든다. owner는 첫 멤버이자 유일한 초기 owner이며, visibility/password/membership-event 설정을 함께 canonicalize한다.
    // Creates a new room and its owner membership in one persistence flow. The owner is the first and sole initial owner, while visibility/password/membership-event settings are canonicalized together.
    public synchronized CreateResult createRoom(String ownerUuid, String name, String visibility, String password, boolean membershipEventsEnabled, boolean pinsEnabled, boolean messageDeleteEnabled, boolean memberSelfDeleteEnabled) {
        CreateResult r = new CreateResult();
        if (connection == null) { r.error = "store_unavailable"; return r; }
        GroupChatSettings c = host.groupChatSettings();
        String owner = normalizeUuid(ownerUuid);
        String roomName = stripName(name, c == null ? 32 : c.groupChatMaxRoomNameLength);
        String vis = normalizeVisibility(visibility);
        if (owner.isBlank()) { r.error = "permission_denied"; return r; }
        if (roomName.isBlank()) { r.error = "empty_name"; return r; }
        if ("public".equals(vis) && c != null && !c.groupChatAllowPublicRooms) { r.error = "public_disabled"; return r; }
        int maxRooms = c == null ? 0 : c.groupChatMaxRoomsPerUser;
        if (maxRooms > 0 && countOwnedRooms(owner) >= maxRooms) { r.error = "too_many_rooms"; return r; }
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String passwordHash = hashPassword(password);
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO group_rooms(id,name,owner_uuid,visibility,password_hash,created_at,updated_at,archived,locked,retention_exempt,membership_events_enabled,pins_enabled,message_delete_enabled,member_self_delete_enabled) VALUES(?,?,?,?,?,?,?,0,0,0,?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, roomName);
                ps.setString(3, owner);
                ps.setString(4, vis);
                ps.setString(5, passwordHash);
                ps.setLong(6, now);
                ps.setLong(7, now);
                ps.setInt(8, membershipEventsEnabled ? 1 : 0);
                ps.setInt(9, pinsEnabled ? 1 : 0);
                ps.setInt(10, messageDeleteEnabled ? 1 : 0);
                ps.setInt(11, memberSelfDeleteEnabled ? 1 : 0);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO group_members(room_id,user_uuid,role,joined_at,last_read_message_id,hidden) VALUES(?,?,?,?,0,0)")) {
                ps.setString(1, id);
                ps.setString(2, owner);
                ps.setString(3, "owner");
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            connection.commit();
            r.ok = true;
            r.room = roomForUser(owner, id);
            return r;
        } catch (SQLException ex) {
            rollbackQuietly();
            r.error = "create_failed";
            return r;
        } finally {
            autoCommitQuietly();
        }
    }

    public synchronized ActionResult joinRoom(String userUuid, String roomId, String password) {
        ActionResult r = new ActionResult();
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (isMember(user, id)) { r.ok = true; r.room = roomForUser(user, id); return r; }
        RoomInfo info = roomInfo(id);
        if (info == null || info.archived) { r.error = "room_not_found"; return r; }
        if (isBanned(user, id)) { r.error = "banned"; return r; }
        boolean pendingInvite = hasPendingInvite(user, id);
        if (!"public".equals(info.visibility) && !pendingInvite) { r.error = "invite_required"; return r; }
        if (info.passwordHash != null && !info.passwordHash.isBlank()) {
            if (!verifyPassword(password, info.passwordHash) && !pendingInvite) { r.error = "bad_password"; return r; }
        }
        GroupChatSettings c = host.groupChatSettings();
        if (c != null && c.groupChatMaxMembersPerRoom > 0 && countMembers(id) >= c.groupChatMaxMembersPerRoom) { r.error = "room_full"; return r; }
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO group_members(room_id,user_uuid,role,joined_at,last_read_message_id,hidden) VALUES(?,?,?,?,0,0)")) {
            ps.setString(1, id);
            ps.setString(2, user);
            ps.setString(3, "member");
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (SQLException ex) { r.error = "join_failed"; return r; }
        acceptPendingInvites(user, id);
        r.membershipEvent = appendMembershipEvent(id, user, "member_join", now);
        r.ok = true;
        r.room = roomForUser(user, id);
        return r;
    }

    public synchronized ActionResult leaveRoom(String userUuid, String roomId) {
        ActionResult r = new ActionResult();
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!isMember(user, id)) { r.error = "not_member"; return r; }
        String role = roleOf(user, id);
        GroupRoom roomBeforeLeave = roomForUser(user, id);
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_members WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, id);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (SQLException ex) { r.error = "leave_failed"; return r; }
        r.membershipEvent = appendMembershipEvent(id, user, "member_leave", System.currentTimeMillis());
        if (countMembers(id) <= 0) {
            archiveRoom(id);
        } else if ("owner".equals(role)) {
            promoteOldestMemberToOwner(id);
        }
        r.ok = true;
        r.room = roomBeforeLeave;
        return r;
    }

    public synchronized ActionResult invite(String inviterUuid, String roomId, String inviteeUuid) {
        ActionResult r = new ActionResult();
        String inviter = normalizeUuid(inviterUuid);
        String invitee = normalizeUuid(inviteeUuid);
        String id = cleanId(roomId);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!canManage(inviter, id)) { r.error = "permission_denied"; return r; }
        if (invitee.isBlank()) { r.error = "player_not_found"; return r; }
        if (isMember(invitee, id)) { r.error = "already_member"; return r; }
        if (isBanned(invitee, id)) { r.error = "banned"; return r; }
        long now = System.currentTimeMillis();
        GroupChatSettings c = host.groupChatSettings();
        long expires = now + Math.max(1, c == null ? 72 : c.groupChatInviteExpireHours) * 60L * 60L * 1000L;
        try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO group_invites(room_id,inviter_uuid,invitee_uuid,status,created_at,expires_at) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, inviter);
            ps.setString(3, invitee);
            ps.setString(4, "pending");
            ps.setLong(5, now);
            ps.setLong(6, expires);
            ps.executeUpdate();
        } catch (SQLException ex) { r.error = "invite_failed"; return r; }
        r.ok = true;
        return r;
    }

    public synchronized ActionResult respondInvite(String userUuid, long inviteId, boolean accept) {
        ActionResult r = new ActionResult();
        String user = normalizeUuid(userUuid);
        String roomId = "";
        long expires = 0L;
        try (PreparedStatement ps = connection.prepareStatement("SELECT room_id, expires_at FROM group_invites WHERE id=? AND invitee_uuid=? AND status='pending'")) {
            ps.setLong(1, inviteId);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { r.error = "invite_not_found"; return r; }
                roomId = rs.getString(1);
                expires = rs.getLong(2);
            }
        } catch (SQLException ex) { r.error = "invite_failed"; return r; }
        long now = System.currentTimeMillis();
        if (expires > 0 && expires < now) {
            setInviteStatus(inviteId, "expired");
            r.error = "invite_expired";
            return r;
        }
        if (accept) {
            ActionResult joined = joinRoom(user, roomId, "");
            if (joined.ok) setInviteStatus(inviteId, "accepted");
            return joined;
        }
        setInviteStatus(inviteId, "declined");
        r.ok = true;
        return r;
    }

    public synchronized SendResult send(String userUuid, String roomId, String body) {
        return send(userUuid, roomId, body, "", 0L);
    }

    public synchronized SendResult send(String userUuid, String roomId, String body, long replyToId) {
        return send(userUuid, roomId, body, "", replyToId);
    }

    public synchronized SendResult send(String userUuid, String roomId, String body, String clientMessageId) {
        return send(userUuid, roomId, body, clientMessageId, 0L);
    }

    public synchronized SendResult send(String userUuid, String roomId, String body, String clientMessageId, long replyToId) {
        SendResult r = new SendResult();
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        String requestId = String.valueOf(clientMessageId == null ? "" : clientMessageId).trim();
        if (requestId.length() > 180) requestId = requestId.substring(0, 180);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!isMember(user, id)) { r.error = "not_member"; return r; }
        if (isRoomLocked(id)) { r.error = "room_locked"; return r; }
        GroupMessage reply = null;
        if (replyToId > 0L) {
            reply = replyTargetForSend(user, id, replyToId);
            if (reply == null) { r.error = "reply_target_not_found"; return r; }
        }
        if (!requestId.isBlank()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM group_messages WHERE client_message_id=? AND room_id=? AND sender_uuid=? AND hidden=0 LIMIT 1")) {
                ps.setString(1, requestId);
                ps.setString(2, id);
                ps.setString(3, user);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.messageId = rs.getLong(1);
                        r.ok = true;
                        r.duplicate = true;
                        r.room = roomForUser(user, id);
                        r.message = messageById(user, r.messageId);
                        return r;
                    }
                }
            } catch (SQLException ex) { r.error = "send_failed"; return r; }
        }
        String message = String.valueOf(body == null ? "" : body).trim();
        GroupChatSettings c = host.groupChatSettings();
        int max = c == null ? 500 : c.groupChatMaxMessageLength;
        if (max > 0 && message.length() > max) message = message.substring(0, max);
        if (message.isBlank()) { r.error = "empty_message"; return r; }
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO group_messages(room_id,sender_uuid,body,created_at,hidden,client_message_id,reply_to_id,reply_to_sender,reply_to_preview,event_type) VALUES(?,?,?,?,0,?,?,?,?,'')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, id);
            ps.setString(2, user);
            ps.setString(3, message);
            ps.setLong(4, now);
            ps.setString(5, requestId);
            ps.setLong(6, reply == null ? 0L : reply.id);
            ps.setString(7, reply == null ? "" : replySenderLabel(reply));
            ps.setString(8, reply == null ? "" : replyPreview(reply.body));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) r.messageId = keys.getLong(1); }
        } catch (SQLException ex) { r.error = "send_failed"; return r; }
        touchRoom(id, now);
        cleanup();
        r.ok = true;
        r.room = roomForUser(user, id);
        r.message = messageById(user, r.messageId);
        return r;
    }

    public synchronized long readPosition(String roomId, String userUuid) {
        String id = cleanId(roomId);
        String user = normalizeUuid(userUuid);
        if (connection == null || id.isBlank() || user.isBlank()) return 0L;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(last_read_message_id,0) FROM group_members WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, id);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Math.max(0L, rs.getLong(1)) : 0L;
            }
        } catch (SQLException ex) {
            return 0L;
        }
    }

    public synchronized boolean markRead(String roomId, String userUuid) {
        String id = cleanId(roomId);
        String user = normalizeUuid(userUuid);
        if (connection == null || !isMember(user, id)) return false;
        long last = lastMessageId(id);
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_members SET last_read_message_id=?, hidden=0 WHERE room_id=? AND user_uuid=?")) {
            ps.setLong(1, last);
            ps.setString(2, id);
            ps.setString(3, user);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    public synchronized ActionResult updateSettings(String userUuid, String roomId, String name, String visibility, String password, boolean passwordSet, Boolean membershipEventsEnabled, Boolean pinsEnabled, Boolean messageDeleteEnabled, Boolean memberSelfDeleteEnabled) {
        ActionResult r = new ActionResult();
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        if (!canManage(user, id)) { r.error = "permission_denied"; return r; }
        RoomInfo info = roomInfo(id);
        if (info == null) { r.error = "room_not_found"; return r; }
        GroupChatSettings c = host.groupChatSettings();
        String newName = name == null ? info.name : stripName(name, c == null ? 32 : c.groupChatMaxRoomNameLength);
        if (newName.isBlank()) newName = info.name;
        String newVis = visibility == null || visibility.isBlank() ? info.visibility : normalizeVisibility(visibility);
        if ("public".equals(newVis) && c != null && !c.groupChatAllowPublicRooms) { r.error = "public_disabled"; return r; }
        String newHash = info.passwordHash;
        if (passwordSet) newHash = hashPassword(password);
        long now = System.currentTimeMillis();
        boolean newMembershipEventsEnabled = membershipEventsEnabled == null ? roomMembershipEventsEnabled(id) : membershipEventsEnabled.booleanValue();
        boolean newPinsEnabled = pinsEnabled == null ? roomPinsEnabled(id) : pinsEnabled.booleanValue();
        boolean newMessageDeleteEnabled = messageDeleteEnabled == null ? roomMessageDeleteEnabled(id) : messageDeleteEnabled.booleanValue();
        boolean newMemberSelfDeleteEnabled = memberSelfDeleteEnabled == null ? roomMemberSelfDeleteEnabled(id) : memberSelfDeleteEnabled.booleanValue();
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_rooms SET name=?, visibility=?, password_hash=?, membership_events_enabled=?, pins_enabled=?, message_delete_enabled=?, member_self_delete_enabled=?, updated_at=? WHERE id=?")) {
            ps.setString(1, newName);
            ps.setString(2, newVis);
            ps.setString(3, newHash);
            ps.setInt(4, newMembershipEventsEnabled ? 1 : 0);
            ps.setInt(5, newPinsEnabled ? 1 : 0);
            ps.setInt(6, newMessageDeleteEnabled ? 1 : 0);
            ps.setInt(7, newMemberSelfDeleteEnabled ? 1 : 0);
            ps.setLong(8, now);
            ps.setString(9, id);
            ps.executeUpdate();
        } catch (SQLException ex) { r.error = "settings_failed"; return r; }
        r.ok = true;
        r.room = roomForUser(user, id);
        return r;
    }

    public synchronized List<GroupRoom> listRooms(String userUuid, int limit) {
        String user = normalizeUuid(userUuid);
        List<GroupRoom> out = new ArrayList<>();
        if (connection == null || user.isBlank()) return out;
        cleanup();
        int max = limit <= 0 ? 200 : Math.min(limit, 500);
        String sql = "SELECT r.id FROM group_rooms r " +
                "LEFT JOIN group_members m ON m.room_id=r.id AND m.user_uuid=? AND COALESCE(m.hidden,0)=0 " +
                "WHERE r.archived=0 " +
                "AND NOT EXISTS(SELECT 1 FROM group_members hm WHERE hm.room_id=r.id AND hm.user_uuid=? AND COALESCE(hm.hidden,0)=1) " +
                "AND NOT EXISTS(SELECT 1 FROM group_bans b WHERE b.room_id=r.id AND b.user_uuid=?) " +
                "AND (m.user_uuid IS NOT NULL OR r.visibility='public' OR EXISTS(SELECT 1 FROM group_invites i WHERE i.room_id=r.id AND i.invitee_uuid=? AND i.status='pending' AND (i.expires_at=0 OR i.expires_at>?))) " +
                "ORDER BY CASE WHEN m.user_uuid IS NOT NULL THEN 0 ELSE 1 END, r.updated_at DESC LIMIT ?";
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, user);
            ps.setString(3, user);
            ps.setString(4, user);
            ps.setLong(5, now);
            ps.setInt(6, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroupRoom room = roomForUser(user, rs.getString(1));
                    if (room != null) out.add(room);
                }
            }
        } catch (SQLException ex) {
            host.warn("Failed to list group rooms: " + ex.getMessage());
        }
        return out;
    }

    public synchronized List<GroupInvite> listInvites(String userUuid, int limit) {
        String user = normalizeUuid(userUuid);
        List<GroupInvite> out = new ArrayList<>();
        if (connection == null || user.isBlank()) return out;
        cleanup();
        int max = limit <= 0 ? 100 : Math.min(limit, 200);
        String sql = "SELECT i.id,i.room_id,r.name,i.inviter_uuid,i.created_at,i.expires_at FROM group_invites i JOIN group_rooms r ON r.id=i.room_id WHERE i.invitee_uuid=? AND i.status='pending' AND r.archived=0 AND (i.expires_at=0 OR i.expires_at>?) ORDER BY i.created_at DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroupInvite inv = new GroupInvite();
                    inv.id = rs.getLong(1);
                    inv.roomId = rs.getString(2);
                    inv.roomName = rs.getString(3);
                    inv.inviterUuid = rs.getString(4);
                    inv.createdAt = rs.getLong(5);
                    inv.expiresAt = rs.getLong(6);
                    fillIdentity(inv);
                    out.add(inv);
                }
            }
        } catch (SQLException ex) {
            host.warn("Failed to list group invites: " + ex.getMessage());
        }
        return out;
    }

    public synchronized List<GroupMessage> listMessages(String userUuid, String roomId, long before, int limit) {
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        List<GroupMessage> out = new ArrayList<>();
        // 고정 기능은 메시지 조회와 독립적이다. pin을 끄더라도 방의 기존 메시지와 입력 기능은 그대로 유지되어야 한다.
        // Pinning is independent from message retrieval. Disabling pins must never hide room history or disable composing.
        if (connection == null || !isMember(user, id)) return out;
        int max = limit <= 0 ? 100 : Math.min(limit, 300);
        String sql = before > 0
                ? "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE room_id=? AND hidden=0 AND id<? AND id NOT IN (SELECT message_id FROM group_message_state WHERE user_uuid=? AND hidden=1) ORDER BY id DESC LIMIT ?"
                : "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE room_id=? AND hidden=0 AND id NOT IN (SELECT message_id FROM group_message_state WHERE user_uuid=? AND hidden=1) ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            if (before > 0) {
                ps.setLong(2, before);
                ps.setString(3, user);
                ps.setInt(4, max);
            } else {
                ps.setString(2, user);
                ps.setInt(3, max);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroupMessage msg = messageFromResult(rs);
                    out.add(msg);
                }
            }
        } catch (SQLException ex) {
            host.warn("Failed to list group messages: " + ex.getMessage());
        }
        Collections.reverse(out);
        for (GroupMessage message : out) message.unreadMemberCount = unreadMemberCountForMessage(message);
        return out;
    }

    /** Searches a member's complete retained room history without changing read state. */
    public synchronized List<GroupMessage> searchMessages(String userUuid, String roomId, String query,
                                                           long from, long to, String senderFilter,
                                                           boolean includeEvents, int limit) {
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        String needle = String.valueOf(query == null ? "" : query).trim().toLowerCase(Locale.ROOT);
        String senderNeedle = String.valueOf(senderFilter == null ? "" : senderFilter).trim().toLowerCase(Locale.ROOT);
        int max = Math.max(1, limit <= 0 ? 50 : limit);
        List<GroupMessage> out = new ArrayList<>();
        if (connection == null || user.isBlank() || id.isBlank() || !isMember(user, id)) return out;
        if (from != Long.MIN_VALUE && to != Long.MAX_VALUE && from > to) {
            long swap = from; from = to; to = swap;
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type " +
                "FROM group_messages WHERE room_id=? AND hidden=0 " +
                "AND id NOT IN (SELECT message_id FROM group_message_state WHERE user_uuid=? AND hidden=1) ");
        if (from != Long.MIN_VALUE) sql.append("AND created_at>=? ");
        if (to != Long.MAX_VALUE) sql.append("AND created_at<=? ");
        sql.append("ORDER BY id DESC");
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int index = 1;
            ps.setString(index++, id);
            ps.setString(index++, user);
            if (from != Long.MIN_VALUE) ps.setLong(index++, from);
            if (to != Long.MAX_VALUE) ps.setLong(index++, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroupMessage msg = messageFromResult(rs);
                    if (!includeEvents && msg.eventType != null && !msg.eventType.isBlank()) continue;
                    if (!matchesGroupMessageSearch(msg, needle, senderNeedle)) continue;
                    out.add(msg);
                    if (out.size() >= max) break;
                }
            }
            for (GroupMessage msg : out) msg.unreadMemberCount = unreadMemberCountForMessage(msg);
        } catch (SQLException ex) {
            host.warn("Failed to search group messages: " + ex.getMessage());
        }
        return out;
    }

    private boolean matchesGroupMessageSearch(GroupMessage msg, String needle, String senderNeedle) {
        if (msg == null) return false;
        String sender = (String.valueOf(msg.senderUuid == null ? "" : msg.senderUuid) + "\n"
                + String.valueOf(msg.senderUsername == null ? "" : msg.senderUsername) + "\n"
                + String.valueOf(msg.senderDisplayName == null ? "" : msg.senderDisplayName)).toLowerCase(Locale.ROOT);
        if (senderNeedle != null && !senderNeedle.isBlank() && !sender.contains(senderNeedle)) return false;
        if (needle == null || needle.isBlank()) return true;
        String haystack = (String.valueOf(msg.body == null ? "" : msg.body) + "\n"
                + String.valueOf(msg.eventType == null ? "" : msg.eventType) + "\n" + sender).toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }


    /** Returns a member's inclusive visible group range without changing read state. */
    public synchronized List<GroupMessage> archiveRange(String userUuid, String roomId, long firstId, long lastId, int limit) {
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        int max = Math.max(1, Math.min(limit <= 0 ? 1000 : limit, ConversationArchiveStore.MAX_CONFIGURED_MESSAGES_PER_ARCHIVE));
        if (connection == null || user.isBlank() || id.isBlank() || firstId <= 0 || lastId <= 0 || !isMember(user, id)) return new ArrayList<>();
        long lo = Math.min(firstId, lastId), hi = Math.max(firstId, lastId);
        List<GroupMessage> out = new ArrayList<>();
        String sql = "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE room_id=? AND hidden=0 AND id>=? AND id<=? " +
                "AND id NOT IN (SELECT message_id FROM group_message_state WHERE user_uuid=? AND hidden=1) ORDER BY id ASC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id); ps.setLong(2, lo); ps.setLong(3, hi); ps.setString(4, user); ps.setInt(5, max + 1);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(messageFromResult(rs)); }
            if (out.size() > max) return new ArrayList<>();
            for (GroupMessage message : out) message.unreadMemberCount = unreadMemberCountForMessage(message);
            return out;
        } catch (SQLException ex) {
            host.warn("Failed to read group archive range: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    public synchronized List<GroupMessage> adminListMessages(String roomId, long before, int limit) {
        String id = cleanId(roomId);
        List<GroupMessage> out = new ArrayList<>();
        if (connection == null || id.isBlank()) return out;
        int max = limit <= 0 ? 100 : Math.min(limit, 200);
        String sql = before > 0
                ? "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE room_id=? AND hidden=0 AND id<? ORDER BY id DESC LIMIT ?"
                : "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE room_id=? AND hidden=0 ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            if (before > 0) {
                ps.setLong(2, before);
                ps.setInt(3, max);
            } else {
                ps.setInt(2, max);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(messageFromResult(rs));
            }
        } catch (SQLException ex) {
            host.warn("Failed to list group audit messages: " + ex.getMessage());
        }
        Collections.reverse(out);
        for (GroupMessage message : out) message.unreadMemberCount = unreadMemberCountForMessage(message);
        return out;
    }

    public synchronized String roomIdForMessage(String userUuid, long messageId) {
        String user = normalizeUuid(userUuid);
        if (connection == null || user.isBlank() || messageId <= 0) return "";
        try (PreparedStatement ps = connection.prepareStatement("SELECT room_id FROM group_messages WHERE id=?")) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "";
                String roomId = rs.getString(1);
                return isMember(user, roomId) ? roomId : "";
            }
        } catch (SQLException ex) { return ""; }
    }

    /** Delete a group message for the whole room. Members may delete their own normal messages; room owner/admin may delete any room message. */
    // 그룹 메시지를 “나에게만 숨김”이 아니라 room 전체에서 실제 삭제한다. 일반 member는 자기 메시지만, owner/admin은 관리 정책에 따라 삭제할 수 있고 관련 reply snapshot을 함께 정리하되 pin snapshot은 명시적 고정 해제 전까지 유지한다.
    // Deletes a group message for the entire room rather than hiding it locally. Ordinary members can delete only their own messages; owner/admin follow management policy, and related Reply snapshots are cleaned up while pin snapshots remain until explicit unpin.
    public synchronized DeleteResult deleteMessage(String requesterUuid, long messageId) {
        return deleteMessage(requesterUuid, messageId, false, true, 0);
    }

    public synchronized DeleteResult deleteMessage(String requesterUuid, long messageId, boolean moderatorDelete,
                                                     boolean selfDeleteEnabled, int selfDeleteWindowMinutes) {
        DeleteResult out = new DeleteResult();
        String requester = normalizeUuid(requesterUuid);
        if (connection == null) { out.error = "store_unavailable"; return out; }
        if (requester.isBlank() || messageId <= 0) { out.error = "invalid_message"; return out; }
        String roomId; String senderUuid; String eventType; long createdAt;
        try (PreparedStatement ps = connection.prepareStatement("SELECT room_id,sender_uuid,COALESCE(event_type,''),created_at FROM group_messages WHERE id=? AND hidden=0")) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { out.error = "message_not_found"; return out; }
                roomId = rs.getString(1); senderUuid = normalizeUuid(rs.getString(2)); eventType = String.valueOf(rs.getString(3) == null ? "" : rs.getString(3)); createdAt = rs.getLong(4);
            }
        } catch (SQLException ex) { out.error = "store_error"; return out; }
        if (!isMember(requester, roomId)) { out.error = "not_member"; return out; }
        if (!roomMessageDeleteEnabled(roomId)) { out.error = "delete_disabled"; return out; }
        boolean manager = moderatorDelete || canManage(requester, roomId);
        boolean ownNormalMessage = requester.equals(senderUuid) && eventType.isBlank();
        if (!manager && (!ownNormalMessage || !roomMemberSelfDeleteEnabled(roomId) || !selfDeleteEnabled)) { out.error = "permission_denied"; return out; }
        int windowMinutes = Math.max(0, selfDeleteWindowMinutes);
        if (!manager && windowMinutes > 0 && (createdAt <= 0L || System.currentTimeMillis() - createdAt > windowMinutes * 60_000L)) {
            out.error = "self_delete_window_expired"; return out;
        }
        try {
            connection.setAutoCommit(false);
            int changed;
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_messages SET hidden=1 WHERE id=? AND hidden=0")) {
                ps.setLong(1, messageId); changed = ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_message_state WHERE message_id=?")) {
                ps.setLong(1, messageId); ps.executeUpdate();
            }
            // pin은 body/sender/time snapshot을 보존한다. 원문 삭제와 함께 pin을 지우지 않고,
            // 관리자가 명시적으로 고정 해제하거나 방을 삭제할 때까지 독립 보존한다.
            // A pin stores a body/sender/time snapshot. Keep it after source-message deletion
            // until a manager explicitly unpins it or the room itself is deleted.
            out.pinRemoved = false;
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_messages SET reply_to_id=0,reply_to_sender='',reply_to_preview='' WHERE room_id=? AND hidden=0 AND reply_to_id=?")) {
                ps.setString(1, roomId); ps.setLong(2, messageId); ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_rooms SET updated_at=? WHERE id=?")) {
                ps.setLong(1, System.currentTimeMillis()); ps.setString(2, roomId); ps.executeUpdate();
            }
            connection.commit();
            out.ok = changed > 0; out.roomId = roomId; out.messageId = messageId; out.managerDelete = manager && !ownNormalMessage;
            if (!out.ok) out.error = "message_not_found";
        } catch (SQLException ex) {
            rollbackQuietly(); out.error = "store_error"; host.warn("Failed to delete group message: " + ex.getMessage());
        } finally { autoCommitQuietly(); }
        return out;
    }

    // room membership을 확인한 뒤 retention과 독립적으로 저장된 pin snapshot을 정렬 순서대로 반환한다. 원본 메시지가 일반 retention으로 없어져도 pin은 명시적 삭제 전까지 남을 수 있다.
    // After verifying room membership, returns retained pin snapshots in explicit order. A pin can outlive normal message retention and source-message deletion; it remains until explicit unpin or room deletion.
    public synchronized List<GroupPinnedMessage> listPins(String userUuid, String roomId) {
        List<GroupPinnedMessage> out = new ArrayList<>();
        String user = normalizeUuid(userUuid), id = cleanId(roomId);
        if (connection == null || !isMember(user, id)) return out;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pin_id,room_id,message_id,pinned_at,sort_order,pinned_by_uuid,pinned_by_username,pinned_by_display_name,message_time,sender_uuid,sender_username,sender_display_name,body,event_type " +
                        "FROM group_pins WHERE room_id=? ORDER BY sort_order ASC,pinned_at ASC,pin_id ASC")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(groupPinFromResult(rs));
            }
        } catch (SQLException ex) { host.warn("Failed to list group pins: " + ex.getMessage()); }
        return out;
    }

    // owner/admin만 현재 room의 메시지를 pin snapshot으로 고정한다. maxPins와 중복 pin을 transaction 안에서 확인해 경쟁 요청에도 제한을 넘지 않게 한다.
    // Allows only owner/admin to pin a current-room message as a snapshot. maxPins and duplicates are checked within the transaction so concurrent requests cannot exceed the limit.
    public synchronized GroupPinnedMessage pinMessage(String managerUuid, String managerUsername, String managerDisplayName, String roomId, long messageId, int maxPins) {
        String manager = normalizeUuid(managerUuid), id = cleanId(roomId);
        if (connection == null || !canManage(manager, id) || !roomPinsEnabled(id) || messageId <= 0) return null;
        try {
            try (PreparedStatement existing = connection.prepareStatement(
                    "SELECT pin_id,room_id,message_id,pinned_at,sort_order,pinned_by_uuid,pinned_by_username,pinned_by_display_name,message_time,sender_uuid,sender_username,sender_display_name,body,event_type FROM group_pins WHERE room_id=? AND message_id=?")) {
                existing.setString(1, id); existing.setLong(2, messageId);
                try (ResultSet rs = existing.executeQuery()) { if (rs.next()) return groupPinFromResult(rs); }
            }
            if (maxPins > 0) {
                try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM group_pins WHERE room_id=?")) {
                    count.setString(1, id); try (ResultSet rs = count.executeQuery()) { if (rs.next() && rs.getInt(1) >= maxPins) return null; }
                }
            }
            GroupMessage msg = rawMessageForRoom(id, messageId);
            if (msg == null) return null;
            long now = System.currentTimeMillis(), order = 1L;
            try (PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(sort_order),0)+1 FROM group_pins WHERE room_id=?")) {
                ps.setString(1, id); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) order = Math.max(1L, rs.getLong(1)); }
            }
            String pinId = "gpin-" + SecurityUtil.randomToken(10);
            PlayerIdentity sender = identity(msg.senderUuid);
            String senderUsername = sender == null ? msg.senderUsername : String.valueOf(sender.username == null ? "" : sender.username);
            String senderDisplay = sender == null ? msg.senderDisplayName : String.valueOf(sender.displayName == null ? "" : sender.displayName);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO group_pins(pin_id,room_id,message_id,pinned_at,sort_order,pinned_by_uuid,pinned_by_username,pinned_by_display_name,message_time,sender_uuid,sender_username,sender_display_name,body,event_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                String pinnerUsername = cleanIdentitySnapshot(managerUsername);
                String pinnerDisplay = cleanIdentitySnapshot(managerDisplayName);
                ps.setString(1, pinId); ps.setString(2, id); ps.setLong(3, messageId); ps.setLong(4, now); ps.setLong(5, order);
                ps.setString(6, manager); ps.setString(7, pinnerUsername); ps.setString(8, pinnerDisplay); ps.setLong(9, msg.createdAt);
                ps.setString(10, msg.senderUuid); ps.setString(11, senderUsername); ps.setString(12, senderDisplay); ps.setString(13, msg.body);
                ps.setString(14, msg.eventType == null ? "" : msg.eventType); ps.executeUpdate();
            }
            for (GroupPinnedMessage pin : listPins(manager, id)) if (pinId.equals(pin.pinId)) return pin;
        } catch (SQLException ex) { host.warn("Failed to pin group message: " + ex.getMessage()); }
        return null;
    }

    public synchronized boolean unpinMessage(String managerUuid, String roomId, String pinId) {
        String manager = normalizeUuid(managerUuid), id = cleanId(roomId), pin = cleanId(pinId);
        if (connection == null || !canManage(manager, id) || !roomPinsEnabled(id) || pin.isBlank()) return false;
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_pins WHERE room_id=? AND pin_id=?")) {
            ps.setString(1, id); ps.setString(2, pin); return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    public synchronized boolean movePin(String managerUuid, String roomId, String pinId, String direction) {
        String manager = normalizeUuid(managerUuid), id = cleanId(roomId), pin = cleanId(pinId);
        String dir = String.valueOf(direction == null ? "" : direction).trim().toLowerCase(Locale.ROOT);
        if (connection == null || !canManage(manager, id) || !roomPinsEnabled(id) || pin.isBlank() || !("up".equals(dir) || "down".equals(dir))) return false;
        try {
            List<GroupPinnedMessage> pins = listPins(manager, id);
            int index = -1; for (int i=0;i<pins.size();i++) if (pin.equals(pins.get(i).pinId)) { index=i; break; }
            int other = "up".equals(dir) ? index - 1 : index + 1;
            if (index < 0 || other < 0 || other >= pins.size()) return false;
            GroupPinnedMessage a = pins.get(index), b = pins.get(other);
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_pins SET sort_order=? WHERE room_id=? AND pin_id=?")) {
                ps.setLong(1, b.sortOrder); ps.setString(2, id); ps.setString(3, a.pinId); ps.executeUpdate();
                ps.setLong(1, a.sortOrder); ps.setString(2, id); ps.setString(3, b.pinId); ps.executeUpdate();
            }
            connection.commit(); return true;
        } catch (SQLException ex) { rollbackQuietly(); return false; }
        finally { autoCommitQuietly(); }
    }

    /** Owner-only room-local admin promotion/demotion. */
    // room owner만 member↔admin 역할을 변경한다. owner 자신을 일반 role로 내리는 동작이나 두 번째 owner 생성은 허용하지 않아 ownership 불변식을 지킨다.
    // Only the room owner can switch member↔admin roles. Demoting the owner or creating a second owner is disallowed to preserve ownership invariants.
    public synchronized ActionResult setMemberRole(String ownerUuid, String roomId, String targetUuid, String newRole) {
        ActionResult r = new ActionResult();
        String owner = normalizeUuid(ownerUuid), target = normalizeUuid(targetUuid), id = cleanId(roomId);
        String role = String.valueOf(newRole == null ? "" : newRole).trim().toLowerCase(Locale.ROOT);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!"owner".equals(roleOf(owner, id))) { r.error = "permission_denied"; return r; }
        if (!("admin".equals(role) || "member".equals(role)) || target.isBlank() || target.equals(owner) || !isMember(target, id) || "owner".equals(roleOf(target, id))) {
            r.error = "invalid_target"; return r;
        }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_members SET role=? WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, role); ps.setString(2, id); ps.setString(3, target); r.ok = ps.executeUpdate() > 0;
            if (!r.ok) r.error = "not_member"; else r.room = roomForUser(owner, id);
        } catch (SQLException ex) { r.error = "role_update_failed"; }
        return r;
    }

    private GroupMessage rawMessageForRoom(String roomId, long messageId) {
        if (connection == null) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE room_id=? AND id=? AND hidden=0")) {
            ps.setString(1, roomId); ps.setLong(2, messageId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { GroupMessage m = messageFromResult(rs); fillIdentity(m); return m; } }
        } catch (SQLException ignored) {}
        return null;
    }

    private GroupPinnedMessage groupPinFromResult(ResultSet rs) throws SQLException {
        GroupPinnedMessage pin = new GroupPinnedMessage();
        pin.pinId = rs.getString(1); pin.roomId = rs.getString(2); pin.messageId = rs.getLong(3); pin.pinnedAt = rs.getLong(4); pin.sortOrder = rs.getLong(5);
        pin.pinnedByUuid = rs.getString(6); pin.pinnedByUsername = rs.getString(7); pin.pinnedByDisplayName = rs.getString(8);
        pin.time = rs.getLong(9); pin.senderUuid = rs.getString(10); pin.senderUsername = rs.getString(11); pin.senderDisplayName = rs.getString(12);
        pin.body = rs.getString(13); pin.eventType = rs.getString(14);
        PlayerIdentity pinner = identity(pin.pinnedByUuid);
        if (pinner != null) {
            pin.pinnedByUsername = pinner.username;
            pin.pinnedByDisplayName = pinner.outputDisplayName();
        }
        String visibleName = pin.pinnedByDisplayName == null || pin.pinnedByDisplayName.isBlank() ? pin.pinnedByUsername : pin.pinnedByDisplayName;
        visibleName = LegacyText.stripColor(String.valueOf(visibleName == null ? "" : visibleName));
        pin.pinnedBy = String.valueOf(visibleName == null ? "" : visibleName).replace("**", "").trim();
        if (pin.pinnedBy.isBlank()) pin.pinnedBy = pin.pinnedByUuid;
        return pin;
    }

    public synchronized int unreadCount(String userUuid) {
        int total = 0;
        for (GroupRoom r : listRooms(userUuid, 500)) if (r.member) total += Math.max(0, r.unread);
        return total;
    }


    public synchronized ActionResult hideRoom(String userUuid, String roomId) {
        ActionResult r = new ActionResult();
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!isMember(user, id)) { r.error = "not_member"; return r; }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_members SET hidden=1 WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, id);
            ps.setString(2, user);
            r.ok = ps.executeUpdate() > 0;
            if (!r.ok) r.error = "hide_failed";
        } catch (SQLException ex) { r.error = "hide_failed"; }
        return r;
    }


    public synchronized ActionResult unhideRoom(String userUuid, String roomId) {
        ActionResult r = new ActionResult();
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!isMember(user, id)) { r.error = "not_member"; return r; }
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_members SET hidden=0 WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, id);
            ps.setString(2, user);
            r.ok = ps.executeUpdate() > 0;
            if (!r.ok) r.error = "unhide_failed";
        } catch (SQLException ex) { r.error = "unhide_failed"; }
        if (r.ok) r.room = roomForUser(user, id);
        return r;
    }

    public synchronized List<GroupRoom> listHiddenRooms(String userUuid, int limit) {
        String user = normalizeUuid(userUuid);
        List<GroupRoom> out = new ArrayList<>();
        if (connection == null || user.isBlank()) return out;
        int max = limit <= 0 ? 100 : Math.min(limit, 500);
        String sql = "SELECT r.id FROM group_rooms r JOIN group_members m ON m.room_id=r.id AND m.user_uuid=? " +
                "WHERE r.archived=0 AND COALESCE(m.hidden,0)=1 " +
                "AND NOT EXISTS(SELECT 1 FROM group_bans b WHERE b.room_id=r.id AND b.user_uuid=?) " +
                "ORDER BY r.updated_at DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, user);
            ps.setInt(3, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroupRoom room = roomForUser(user, rs.getString(1));
                    if (room != null) out.add(room);
                }
            }
        } catch (SQLException ex) {
            host.warn("Failed to list hidden group rooms: " + ex.getMessage());
        }
        return out;
    }

    public synchronized List<String> listHiddenRoomsJson(String userUuid, int limit) {
        List<String> out = new ArrayList<>();
        for (GroupRoom room : listHiddenRooms(userUuid, limit)) out.add(room.toJson());
        return out;
    }

    public synchronized List<String> messageBodiesForRoom(String roomId) {
        String id = cleanId(roomId);
        List<String> out = new ArrayList<>();
        if (connection == null || id.isBlank()) return out;
        try (PreparedStatement ps = connection.prepareStatement("SELECT body FROM group_messages WHERE room_id=? AND hidden=0")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(String.valueOf(rs.getString(1) == null ? "" : rs.getString(1)));
            }
        } catch (SQLException ex) {
            host.warn("Failed to list group message bodies: " + ex.getMessage());
        }
        return out;
    }

    public synchronized boolean deleteRoom(String roomId) {
        String id = cleanId(roomId);
        if (connection == null || id.isBlank()) return false;
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_pins WHERE room_id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_message_state WHERE message_id IN (SELECT id FROM group_messages WHERE room_id=?)")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_messages WHERE room_id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_invites WHERE room_id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_bans WHERE room_id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_members WHERE room_id=?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            int removed;
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_rooms WHERE id=?")) {
                ps.setString(1, id);
                removed = ps.executeUpdate();
            }
            connection.commit();
            return removed > 0;
        } catch (SQLException ex) {
            rollbackQuietly();
            host.warn("Failed to delete group room: " + ex.getMessage());
            return false;
        } finally {
            autoCommitQuietly();
        }
    }

    public synchronized ActionResult kick(String managerUuid, String roomId, String targetUuid, boolean ban) {
        ActionResult r = new ActionResult();
        String manager = normalizeUuid(managerUuid);
        String target = normalizeUuid(targetUuid);
        String id = cleanId(roomId);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!canManage(manager, id)) { r.error = "permission_denied"; return r; }
        if (target.isBlank() || manager.equals(target)) { r.error = "invalid_target"; return r; }
        String managerRole = roleOf(manager, id);
        String targetRole = roleOf(target, id);
        if (targetRole.isBlank()) { r.error = "not_member"; return r; }
        if ("owner".equals(targetRole)) { r.error = "cannot_kick_owner"; return r; }
        if (!"owner".equals(managerRole) && "admin".equals(targetRole)) { r.error = "permission_denied"; return r; }
        long now = System.currentTimeMillis();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_members WHERE room_id=? AND user_uuid=?")) {
                ps.setString(1, id);
                ps.setString(2, target);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_invites SET status='declined' WHERE room_id=? AND invitee_uuid=? AND status='pending'")) {
                ps.setString(1, id);
                ps.setString(2, target);
                ps.executeUpdate();
            }
            if (ban) {
                try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO group_bans(room_id,user_uuid,banned_by_uuid,created_at) VALUES(?,?,?,?)")) {
                    ps.setString(1, id);
                    ps.setString(2, target);
                    ps.setString(3, manager);
                    ps.setLong(4, now);
                    ps.executeUpdate();
                }
            }
            connection.commit();
            r.ok = true;
        } catch (SQLException ex) { rollbackQuietly(); r.error = ban ? "ban_failed" : "kick_failed"; }
        finally { autoCommitQuietly(); }
        if (r.ok) {
            r.membershipEvent = appendMembershipEvent(id, target, "member_leave", now);
            r.room = roomForUser(manager, id);
        }
        return r;
    }

    public synchronized ActionResult unban(String managerUuid, String roomId, String targetUuid) {
        ActionResult r = new ActionResult();
        String manager = normalizeUuid(managerUuid);
        String target = normalizeUuid(targetUuid);
        String id = cleanId(roomId);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!canManage(manager, id)) { r.error = "permission_denied"; return r; }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_bans WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, id);
            ps.setString(2, target);
            ps.executeUpdate();
            r.ok = true;
        } catch (SQLException ex) { r.error = "unban_failed"; }
        return r;
    }

    public synchronized ActionResult transferOwner(String ownerUuid, String roomId, String targetUuid) {
        ActionResult r = new ActionResult();
        String owner = normalizeUuid(ownerUuid);
        String target = normalizeUuid(targetUuid);
        String id = cleanId(roomId);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!"owner".equals(roleOf(owner, id))) { r.error = "permission_denied"; return r; }
        if (target.isBlank() || owner.equals(target) || !isMember(target, id)) { r.error = "invalid_target"; return r; }
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_members SET role='admin' WHERE room_id=? AND user_uuid=?")) {
                ps.setString(1, id);
                ps.setString(2, owner);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_members SET role='owner' WHERE room_id=? AND user_uuid=?")) {
                ps.setString(1, id);
                ps.setString(2, target);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("UPDATE group_rooms SET owner_uuid=?, updated_at=? WHERE id=?")) {
                ps.setString(1, target);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, id);
                ps.executeUpdate();
            }
            connection.commit();
            r.ok = true;
            r.room = roomForUser(owner, id);
        } catch (SQLException ex) { rollbackQuietly(); r.error = "transfer_failed"; }
        finally { autoCommitQuietly(); }
        return r;
    }

    // room 멤버 목록을 구조화 데이터로 반환해 WebChatServer가 viewer별 presence privacy를 적용할 수 있게 한다. 저장소 단계에서 최종 JSON을 굳히지 않는 이유가 이것이다.
    // Returns structured member data so WebChatServer can apply viewer-specific presence privacy. This is why the store does not permanently bake presence into final JSON.
    public synchronized List<Map<String,Object>> listMembers(String requesterUuid, String roomId) {
        String requester = normalizeUuid(requesterUuid);
        String id = cleanId(roomId);
        List<Map<String,Object>> out = new ArrayList<>();
        if (connection == null || !isMember(requester, id)) return out;
        try (PreparedStatement ps = connection.prepareStatement("SELECT user_uuid,role,joined_at FROM group_members WHERE room_id=? ORDER BY CASE role WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 ELSE 2 END, joined_at ASC")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuid = normalizeUuid(rs.getString(1));
                    PlayerIdentity ident = identity(uuid);
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("uuid", uuid);
                    m.put("username", ident.username);
                    m.put("displayName", ident.displayName);
                    m.put("label", labelForIdentity(ident));
                    m.put("role", rs.getString(2));
                    m.put("joinedAt", rs.getLong(3));
                    out.add(m);
                }
            }
        } catch (SQLException ex) { host.warn("Failed to list group members: " + ex.getMessage()); }
        return out;
    }

    public synchronized List<String> listMembersJson(String requesterUuid, String roomId) {
        List<String> out = new ArrayList<>();
        for (Map<String,Object> member : listMembers(requesterUuid, roomId)) out.add(JsonUtil.obj(member));
        return out;
    }

    public synchronized List<String> listBansJson(String requesterUuid, String roomId) {
        String requester = normalizeUuid(requesterUuid);
        String id = cleanId(roomId);
        List<String> out = new ArrayList<>();
        if (connection == null || !canManage(requester, id)) return out;
        try (PreparedStatement ps = connection.prepareStatement("SELECT user_uuid,banned_by_uuid,created_at FROM group_bans WHERE room_id=? ORDER BY created_at DESC")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuid = normalizeUuid(rs.getString(1));
                    PlayerIdentity ident = identity(uuid);
                    String bannedByUuid = normalizeUuid(rs.getString(2));
                    PlayerIdentity bannedBy = identity(bannedByUuid);
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("uuid", uuid);
                    m.put("username", ident.username);
                    m.put("displayName", ident.displayName);
                    m.put("label", labelForIdentity(ident));
                    m.put("bannedByUuid", bannedByUuid);
                    m.put("bannedByLabel", labelForIdentity(bannedBy));
                    m.put("createdAt", rs.getLong(3));
                    out.add(JsonUtil.obj(m));
                }
            }
        } catch (SQLException ex) {
            host.warn("Failed to list group bans: " + ex.getMessage());
        }
        return out;
    }

    public synchronized List<String> adminRoomSummaries(int limit) {
        List<String> out = new ArrayList<>();
        if (connection == null) return out;
        int max = limit <= 0 ? 200 : Math.min(limit, 500);
        String sql = "SELECT r.id,r.name,r.owner_uuid,r.visibility,r.password_hash,r.updated_at,r.archived," +
                "(SELECT COUNT(*) FROM group_members gm WHERE gm.room_id=r.id)," +
                "(SELECT COUNT(*) FROM group_messages msg WHERE msg.room_id=r.id AND msg.hidden=0)," +
                "(SELECT COALESCE(SUM(LENGTH(msg.body)),0) FROM group_messages msg WHERE msg.room_id=r.id AND msg.hidden=0)," +
                "(SELECT COALESCE(MAX(msg.created_at),0) FROM group_messages msg WHERE msg.room_id=r.id AND msg.hidden=0)," +
                "r.locked,r.retention_exempt " +
                "FROM group_rooms r ORDER BY r.updated_at DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlayerIdentity owner = identity(normalizeUuid(rs.getString(3)));
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString(1));
                    m.put("name", rs.getString(2));
                    m.put("ownerUuid", normalizeUuid(rs.getString(3)));
                    m.put("ownerLabel", labelForIdentity(owner));
                    m.put("visibility", rs.getString(4));
                    m.put("passwordProtected", rs.getString(5) != null && !rs.getString(5).isBlank());
                    m.put("updatedAt", rs.getLong(6));
                    m.put("archived", rs.getInt(7) != 0);
                    m.put("memberCount", rs.getInt(8));
                    m.put("messageCount", rs.getInt(9));
                    m.put("storageBytes", rs.getLong(10));
                    long latestMessageAt = rs.getLong(11);
                    long retentionBaseAt = latestMessageAt > 0L ? latestMessageAt : rs.getLong(6);
                    GroupChatSettings c = host.groupChatSettings();
                    int retentionDays = c == null ? 0 : Math.max(0, c.groupChatRetentionDays);
                    m.put("latestMessageAt", latestMessageAt);
                    m.put("retentionBaseAt", retentionBaseAt);
                    m.put("retentionDays", retentionDays);
                    m.put("retentionExpiresAt", retentionDays > 0 && retentionBaseAt > 0L ? retentionBaseAt + retentionDays * 24L * 60L * 60L * 1000L : 0L);
                    m.put("locked", rs.getInt(12) != 0);
                    m.put("retentionExempt", rs.getInt(13) != 0);
                    m.put("adminOnly", true);
                    out.add(JsonUtil.obj(m));
                }
            }
        } catch (SQLException ex) { host.warn("Failed to list group admin summaries: " + ex.getMessage()); }
        return out;
    }


    public synchronized boolean isRoomLocked(String roomId) {
        String id = cleanId(roomId);
        if (connection == null || id.isBlank()) return false;
        try (PreparedStatement ps = connection.prepareStatement("SELECT locked FROM group_rooms WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) != 0; }
        } catch (SQLException ex) { return false; }
    }

    public synchronized boolean setSessionFlags(String roomId, Boolean locked, Boolean retentionExempt) {
        String id = cleanId(roomId);
        if (connection == null || id.isBlank()) return false;
        List<String> sets = new ArrayList<>();
        if (locked != null) sets.add("locked=" + (locked ? "1" : "0"));
        if (retentionExempt != null) sets.add("retention_exempt=" + (retentionExempt ? "1" : "0"));
        if (sets.isEmpty()) return false;
        try (Statement st = connection.createStatement()) {
            int removed = st.executeUpdate("UPDATE group_rooms SET " + String.join(",", sets) + " WHERE id='" + id.replace("'", "''") + "'");
            return removed > 0;
        } catch (SQLException ex) {
            host.warn("Failed to update group room session flags: " + ex.getMessage());
            return false;
        }
    }

    public synchronized String cleanupPreviewJson() {
        Map<String,Object> m = new LinkedHashMap<>();
        GroupChatSettings c = host.groupChatSettings();
        int days = c == null ? 0 : Math.max(0, c.groupChatRetentionDays);
        m.put("retentionDays", days);
        if (connection == null || days <= 0) {
            m.put("expiredMessages", 0); m.put("emptySessions", 0); m.put("lockedSessions", 0); m.put("retentionExemptSessions", 0); return JsonUtil.obj(m);
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM group_messages WHERE created_at < " + cutoff + " AND room_id NOT IN (SELECT id FROM group_rooms WHERE retention_exempt=1)")) { m.put("expiredMessages", rs.next() ? rs.getInt(1) : 0); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM group_rooms WHERE retention_exempt=0 AND updated_at < " + cutoff + " AND id NOT IN (SELECT DISTINCT room_id FROM group_messages WHERE created_at >= " + cutoff + ")")) { m.put("emptySessions", rs.next() ? rs.getInt(1) : 0); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM group_rooms WHERE locked=1")) { m.put("lockedSessions", rs.next() ? rs.getInt(1) : 0); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM group_rooms WHERE retention_exempt=1")) { m.put("retentionExemptSessions", rs.next() ? rs.getInt(1) : 0); }
        } catch (SQLException ex) { host.warn("Failed to calculate group cleanup preview: " + ex.getMessage()); }
        return JsonUtil.obj(m);
    }

    public synchronized boolean uploadNameReferenced(String name, String ignoreRoomId) {
        String n = String.valueOf(name == null ? "" : name).trim();
        if (!safeUploadReferenceName(n)) return false;
        String encoded = encodedUploadReferenceName(n);
        String ignore = cleanId(ignoreRoomId);
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM group_messages WHERE hidden=0 AND room_id<>? AND (body LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\') LIMIT 1")) {
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
        return java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~");
    }

    private String escapeLikeLiteral(String value) {
        return String.valueOf(value == null ? "" : value).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public synchronized Set<String> memberUuids(String roomId) {
        Set<String> out = new LinkedHashSet<>();
        if (connection == null) return out;
        try (PreparedStatement ps = connection.prepareStatement("SELECT user_uuid FROM group_members WHERE room_id=?")) {
            ps.setString(1, cleanId(roomId));
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(normalizeUuid(rs.getString(1))); }
        } catch (SQLException ignored) {}
        return out;
    }

    private boolean isOnlineUuid(String uuid) {
        return host.isOnline(normalizeUuid(uuid));
    }

    private int onlineMemberCount(String roomId) {
        int count = 0;
        for (String uuid : memberUuids(roomId)) {
            if (isOnlineUuid(uuid)) count++;
        }
        return count;
    }

    /** Visible room metadata for a current member. */
    public synchronized GroupRoom roomForMember(String userUuid, String roomId) {
        GroupRoom room = roomForUser(userUuid, roomId);
        return room != null && room.member ? room : null;
    }

    /** Current room-management/invite permission. */
    public synchronized boolean canManageRoom(String userUuid, String roomId) {
        return canManage(normalizeUuid(userUuid), cleanId(roomId));
    }

    private GroupRoom roomForUser(String userUuid, String roomId) {
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        String sql = "SELECT r.id,r.name,r.owner_uuid,r.visibility,r.password_hash,r.updated_at,r.membership_events_enabled,r.pins_enabled,r.message_delete_enabled,r.member_self_delete_enabled," +
                "COALESCE(m.role,''), COALESCE(m.last_read_message_id,0)," +
                "(SELECT COUNT(*) FROM group_members gm WHERE gm.room_id=r.id)," +
                "(SELECT id FROM group_messages lm WHERE lm.room_id=r.id ORDER BY id DESC LIMIT 1)," +
                "(SELECT body FROM group_messages lm WHERE lm.room_id=r.id ORDER BY id DESC LIMIT 1)," +
                "(SELECT sender_uuid FROM group_messages lm WHERE lm.room_id=r.id ORDER BY id DESC LIMIT 1) " +
                "FROM group_rooms r LEFT JOIN group_members m ON m.room_id=r.id AND m.user_uuid=? WHERE r.id=? AND r.archived=0";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                GroupRoom room = new GroupRoom();
                room.id = rs.getString(1);
                room.name = rs.getString(2);
                room.ownerUuid = normalizeUuid(rs.getString(3));
                room.visibility = rs.getString(4);
                room.passwordProtected = rs.getString(5) != null && !rs.getString(5).isBlank();
                room.updatedAt = rs.getLong(6);
                room.membershipEventsEnabled = rs.getInt(7) != 0;
                room.pinsEnabled = rs.getInt(8) != 0;
                room.messageDeleteEnabled = rs.getInt(9) != 0;
                room.memberSelfDeleteEnabled = rs.getInt(10) != 0;
                room.role = rs.getString(11) == null ? "" : rs.getString(11);
                long lastRead = rs.getLong(12);
                room.member = room.role != null && !room.role.isBlank();
                room.memberCount = rs.getInt(13);
                room.onlineMemberCount = onlineMemberCount(room.id);
                room.lastMessageId = rs.getLong(14);
                room.lastMessage = rs.getString(15) == null ? "" : rs.getString(15);
                room.lastSenderUuid = normalizeUuid(rs.getString(16));
                room.unread = room.member ? countUnread(room.id, lastRead, user) : 0;
                return room;
            }
        } catch (SQLException ex) { return null; }
    }

    private int countUnread(String roomId, long lastRead, String userUuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM group_messages WHERE room_id=? AND id>? AND sender_uuid<>? AND hidden=0 AND id NOT IN (SELECT message_id FROM group_message_state WHERE user_uuid=? AND hidden=1)")) {
            ps.setString(1, roomId);
            ps.setLong(2, Math.max(0L, lastRead));
            ps.setString(3, normalizeUuid(userUuid));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    /** Returns a group message only when the requesting user is still a member of its room. */
    public synchronized GroupMessage messageForUser(String userUuid, long messageId) {
        return messageById(userUuid, messageId);
    }

    private GroupMessage messageById(String userUuid, long messageId) {
        if (messageId <= 0) return null;
        GroupMessage result = null;
        try (PreparedStatement ps = connection.prepareStatement("SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE id=? AND hidden=0")) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String roomId = rs.getString(2);
                if (!isMember(userUuid, roomId)) return null;
                result = messageFromResult(rs);
            }
        } catch (SQLException ex) { return null; }
        if (result != null) result.unreadMemberCount = unreadMemberCountForMessage(result);
        return result;
    }

    private int unreadMemberCountForMessage(GroupMessage message) {
        if (message == null || connection == null || message.id <= 0 || message.roomId == null || message.roomId.isBlank()) return 0;
        String sql = "SELECT COUNT(*) FROM group_members WHERE room_id=? AND user_uuid<>? AND joined_at<=? AND COALESCE(last_read_message_id,0)<?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, message.roomId);
            ps.setString(2, normalizeUuid(message.senderUuid));
            ps.setLong(3, message.createdAt);
            ps.setLong(4, message.id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Math.max(0, rs.getInt(1)) : 0; }
        } catch (SQLException ex) {
            return 0;
        }
    }

    private GroupMessage messageFromResult(ResultSet rs) throws SQLException {
        GroupMessage msg = new GroupMessage();
        msg.id = rs.getLong("id");
        msg.roomId = rs.getString("room_id");
        msg.senderUuid = normalizeUuid(rs.getString("sender_uuid"));
        msg.body = rs.getString("body") == null ? "" : rs.getString("body");
        msg.createdAt = rs.getLong("created_at");
        String eventType = rs.getString("event_type");
        msg.eventType = eventType == null ? "" : eventType;
        msg.replyToId = Math.max(0L, rs.getLong("reply_to_id"));
        String replySender = rs.getString("reply_to_sender");
        String replyPreview = rs.getString("reply_to_preview");
        msg.replyToSender = replySender == null ? "" : replySender;
        msg.replyToPreview = replyPreview == null ? "" : replyPreview;
        fillIdentity(msg);
        return msg;
    }

    private GroupMessage replyTargetForSend(String requestingUser, String expectedRoomId, long replyToId) {
        if (connection == null || replyToId <= 0L || expectedRoomId == null || expectedRoomId.isBlank()) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE id=? AND hidden=0")) {
            ps.setLong(1, replyToId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                GroupMessage original = messageFromResult(rs);
                if (original.eventType != null && !original.eventType.isBlank()) return null;
                if (!expectedRoomId.equals(original.roomId)) return null;
                if (!isMember(requestingUser, expectedRoomId)) return null;
                return original;
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    private String replySenderLabel(GroupMessage message) {
        if (message == null) return "";
        String display = String.valueOf(message.senderDisplayName == null ? "" : message.senderDisplayName).trim();
        if (!display.isBlank()) return cleanReplyText(display, 128);
        String username = String.valueOf(message.senderUsername == null ? "" : message.senderUsername).trim();
        if (!username.isBlank()) return cleanReplyText(username, 128);
        return cleanReplyText(message.senderUuid, 128);
    }

    private String replyPreview(String body) {
        return cleanReplyText(body, 240);
    }

    private String cleanReplyText(String text, int maxLength) {
        String value = String.valueOf(text == null ? "" : text).replace('\r', ' ').replace('\n', ' ').trim();
        value = value.replaceAll("\\s+", " ");
        if (maxLength > 0 && value.length() > maxLength) value = value.substring(0, maxLength);
        return value;
    }

    private void fillIdentity(GroupMessage msg) {
        PlayerIdentity identity = identity(msg.senderUuid);
        msg.senderUsername = identity.username;
        msg.senderDisplayName = identity.displayName;
    }

    private void fillIdentity(GroupInvite inv) {
        PlayerIdentity identity = identity(inv.inviterUuid);
        inv.inviterUsername = identity.username;
        inv.inviterDisplayName = identity.displayName;
    }

    private PlayerIdentity identity(String uuid) {
        PlayerIdentity identity = host.resolveIdentity(normalizeUuid(uuid));
        return identity == null ? new PlayerIdentity(normalizeUuid(uuid), normalizeUuid(uuid), normalizeUuid(uuid)) : identity;
    }

    public synchronized boolean isMemberOfRoom(String userUuid, String roomId) {
        return isMember(normalizeUuid(userUuid), cleanId(roomId));
    }

    private boolean isMember(String userUuid, String roomId) {
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        if (user.isBlank() || id.isBlank()) return false;
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM group_members WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, id);
            ps.setString(2, user);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { return false; }
    }

    private boolean canManage(String userUuid, String roomId) {
        String role = roleOf(userUuid, roomId);
        return "owner".equals(role) || "admin".equals(role);
    }

    private String roleOf(String userUuid, String roomId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT role FROM group_members WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, cleanId(roomId));
            ps.setString(2, normalizeUuid(userUuid));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? String.valueOf(rs.getString(1)) : ""; }
        } catch (SQLException ex) { return ""; }
    }

    private boolean isBanned(String userUuid, String roomId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM group_bans WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, cleanId(roomId));
            ps.setString(2, normalizeUuid(userUuid));
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { return false; }
    }

    private String cleanIdentitySnapshot(String value) {
        String out = String.valueOf(value == null ? "" : value).replace("\u0000", "").trim();
        return out.length() > 256 ? out.substring(0, 256) : out;
    }

    private String labelForIdentity(PlayerIdentity identity) {
        if (identity == null) return "";
        String label = identity.displayName == null || identity.displayName.isBlank() ? identity.username : identity.displayName;
        if (label == null || label.isBlank()) label = identity.uuid;
        if (identity.username != null && !identity.username.isBlank() && !identity.username.equals(label)) label += " (" + identity.username + ")";
        return label == null ? "" : label;
    }

    private boolean hasPendingInvite(String userUuid, String roomId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM group_invites WHERE room_id=? AND invitee_uuid=? AND status='pending' AND (expires_at=0 OR expires_at>?)")) {
            ps.setString(1, cleanId(roomId));
            ps.setString(2, normalizeUuid(userUuid));
            ps.setLong(3, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { return false; }
    }

    private void acceptPendingInvites(String userUuid, String roomId) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_invites SET status='accepted' WHERE room_id=? AND invitee_uuid=? AND status='pending'")) {
            ps.setString(1, cleanId(roomId));
            ps.setString(2, normalizeUuid(userUuid));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void setInviteStatus(long id, String status) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_invites SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private long lastMessageId(String roomId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COALESCE(MAX(id),0) FROM group_messages WHERE room_id=?")) {
            ps.setString(1, cleanId(roomId));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0L; }
        } catch (SQLException ex) { return 0L; }
    }

    private int countOwnedRooms(String ownerUuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM group_rooms WHERE owner_uuid=? AND archived=0")) {
            ps.setString(1, normalizeUuid(ownerUuid));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException ex) { return 0; }
    }

    private int countMembers(String roomId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM group_members WHERE room_id=?")) {
            ps.setString(1, cleanId(roomId));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException ex) { return 0; }
    }

    private void archiveRoom(String roomId) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_rooms SET archived=1, updated_at=? WHERE id=?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, cleanId(roomId));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void promoteOldestMemberToOwner(String roomId) {
        String next = "";
        try (PreparedStatement ps = connection.prepareStatement("SELECT user_uuid FROM group_members WHERE room_id=? ORDER BY joined_at ASC LIMIT 1")) {
            ps.setString(1, cleanId(roomId));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) next = normalizeUuid(rs.getString(1)); }
        } catch (SQLException ignored) {}
        if (next.isBlank()) return;
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_members SET role='owner' WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, cleanId(roomId));
            ps.setString(2, next);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_rooms SET owner_uuid=? WHERE id=?")) {
            ps.setString(1, next);
            ps.setString(2, cleanId(roomId));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void touchRoom(String roomId, long now) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_rooms SET updated_at=? WHERE id=?")) {
            ps.setLong(1, now);
            ps.setString(2, cleanId(roomId));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private RoomInfo roomInfo(String roomId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id,name,owner_uuid,visibility,password_hash,archived FROM group_rooms WHERE id=?")) {
            ps.setString(1, cleanId(roomId));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                RoomInfo info = new RoomInfo();
                info.id = rs.getString(1);
                info.name = rs.getString(2);
                info.ownerUuid = normalizeUuid(rs.getString(3));
                info.visibility = rs.getString(4);
                info.passwordHash = rs.getString(5);
                info.archived = rs.getInt(6) != 0;
                return info;
            }
        } catch (SQLException ex) { return null; }
    }

    private boolean roomMembershipEventsEnabled(String roomId) {
        if (connection == null) return true;
        try (PreparedStatement ps = connection.prepareStatement("SELECT membership_events_enabled FROM group_rooms WHERE id=? AND archived=0")) {
            ps.setString(1, cleanId(roomId));
            try (ResultSet rs = ps.executeQuery()) { return !rs.next() || rs.getInt(1) != 0; }
        } catch (SQLException ex) { return true; }
    }

    private boolean roomPinsEnabled(String roomId) {
        return roomBooleanSetting(roomId, "pins_enabled", false);
    }

    private boolean roomMessageDeleteEnabled(String roomId) {
        return roomBooleanSetting(roomId, "message_delete_enabled", false);
    }

    private boolean roomMemberSelfDeleteEnabled(String roomId) {
        return roomBooleanSetting(roomId, "member_self_delete_enabled", false);
    }

    private boolean roomBooleanSetting(String roomId, String column, boolean fallback) {
        if (connection == null) return fallback;
        if (!("pins_enabled".equals(column) || "message_delete_enabled".equals(column) || "member_self_delete_enabled".equals(column))) return fallback;
        try (PreparedStatement ps = connection.prepareStatement("SELECT " + column + " FROM group_rooms WHERE id=? AND archived=0")) {
            ps.setString(1, cleanId(roomId));
            try (ResultSet rs = ps.executeQuery()) { return !rs.next() ? fallback : rs.getInt(1) != 0; }
        } catch (SQLException ex) { return fallback; }
    }

    private GroupMessage appendMembershipEvent(String roomId, String actorUuid, String eventType, long now) {
        String id = cleanId(roomId);
        String actor = normalizeUuid(actorUuid);
        String type = String.valueOf(eventType == null ? "" : eventType).trim();
        if (connection == null || id.isBlank() || actor.isBlank() || type.isBlank() || !roomMembershipEventsEnabled(id)) return null;
        long messageId = 0L;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO group_messages(room_id,sender_uuid,body,created_at,hidden,client_message_id,reply_to_id,reply_to_sender,reply_to_preview,event_type) VALUES(?,?,'',?,0,'',0,'','',?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, id);
            ps.setString(2, actor);
            ps.setLong(3, now);
            ps.setString(4, type);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) messageId = keys.getLong(1); }
        } catch (SQLException ex) {
            host.warn("Failed to append group membership event: " + ex.getMessage());
            return null;
        }
        touchRoom(id, now);
        if (messageId <= 0L) return null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id,room_id,sender_uuid,body,created_at,reply_to_id,reply_to_sender,reply_to_preview,event_type FROM group_messages WHERE id=? AND hidden=0")) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                GroupMessage message = messageFromResult(rs);
                message.unreadMemberCount = unreadMemberCountForMessage(message);
                return message;
            }
        } catch (SQLException ex) {
            return null;
        }
    }

    private String hashPassword(String password) {
        String p = String.valueOf(password == null ? "" : password);
        if (p.isBlank()) return "";
        GroupChatSettings c = host.groupChatSettings();
        if (c != null && !c.groupChatAllowRoomPasswords) return "";
        return SecurityUtil.hashPassword(p.toCharArray());
    }

    private boolean verifyPassword(String password, String hash) {
        if (hash == null || hash.isBlank()) return true;
        return SecurityUtil.verifyPassword(String.valueOf(password == null ? "" : password).toCharArray(), hash);
    }

    private String stripName(String value, int max) {
        String s = String.valueOf(value == null ? "" : value).replaceAll("[\\p{Cntrl}]", "").trim();
        int limit = Math.max(1, max <= 0 ? 32 : max);
        if (s.length() > limit) s = s.substring(0, limit).trim();
        return s;
    }

    private String normalizeVisibility(String value) {
        String v = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        return "public".equals(v) ? "public" : "private";
    }

    private String normalizeUuid(String uuid) { return String.valueOf(uuid == null ? "" : uuid).trim().toLowerCase(Locale.ROOT); }
    private String cleanId(String id) { return String.valueOf(id == null ? "" : id).trim(); }
    private void rollbackQuietly() { try { if (connection != null) connection.rollback(); } catch (SQLException ignored) {} }
    private void autoCommitQuietly() { try { if (connection != null) connection.setAutoCommit(true); } catch (SQLException ignored) {} }

    private static final class RoomInfo {
        String id = "";
        String name = "";
        String ownerUuid = "";
        String visibility = "private";
        String passwordHash = "";
        boolean archived;
    }

    public static class DeleteResult {
        public boolean ok;
        public String error = "";
        public String roomId = "";
        public long messageId;
        public boolean pinRemoved;
        public boolean managerDelete;
    }

    public static class ActionResult {
        public boolean ok;
        public String error = "";
        public GroupRoom room;
        public GroupMessage membershipEvent;
    }

    public static class CreateResult extends ActionResult {}

    public static class SendResult extends ActionResult {
        public boolean duplicate;
        public long messageId;
        public GroupMessage message;
    }
}
