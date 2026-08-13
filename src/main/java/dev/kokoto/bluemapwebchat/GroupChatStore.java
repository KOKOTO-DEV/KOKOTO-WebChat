package dev.kokoto.bluemapwebchat;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;

public class GroupChatStore {
    private final BlueMapWebChatPlugin plugin;
    private Connection connection;

    public GroupChatStore(BlueMapWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void open() {
        close();
        ConfigValues c = plugin.configValues();
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
            plugin.getLogger().warning("Failed to open group chat SQLite store: " + ex.getMessage());
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
        if (!file.isAbsolute()) file = new File(plugin.getDataFolder(), name);
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
                    "retention_exempt INTEGER NOT NULL DEFAULT 0" +
                    ")");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_rooms_visibility ON group_rooms(visibility, updated_at)");
            addColumnIfMissing(st, "group_rooms", "locked", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(st, "group_rooms", "retention_exempt", "INTEGER NOT NULL DEFAULT 0");
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
                    "client_message_id TEXT NOT NULL DEFAULT ''" +
                    ")");
            addColumnIfMissing(st, "group_messages", "client_message_id", "TEXT NOT NULL DEFAULT ''");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_messages_room ON group_messages(room_id, id)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_group_messages_client_id ON group_messages(client_message_id) WHERE client_message_id<>''");
            st.execute("CREATE INDEX IF NOT EXISTS idx_group_messages_created ON group_messages(created_at)");
            st.execute("CREATE TABLE IF NOT EXISTS group_message_state (" +
                    "message_id INTEGER NOT NULL," +
                    "user_uuid TEXT NOT NULL," +
                    "hidden INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(message_id, user_uuid)" +
                    ")");
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
        ConfigValues c = plugin.configValues();
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
            plugin.getLogger().warning("Failed to cleanup group chat: " + ex.getMessage());
        }
    }

    public synchronized CreateResult createRoom(String ownerUuid, String name, String visibility, String password) {
        CreateResult r = new CreateResult();
        if (connection == null) { r.error = "store_unavailable"; return r; }
        ConfigValues c = plugin.configValues();
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
            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO group_rooms(id,name,owner_uuid,visibility,password_hash,created_at,updated_at,archived,locked,retention_exempt) VALUES(?,?,?,?,?,?,?,0,0,0)")) {
                ps.setString(1, id);
                ps.setString(2, roomName);
                ps.setString(3, owner);
                ps.setString(4, vis);
                ps.setString(5, passwordHash);
                ps.setLong(6, now);
                ps.setLong(7, now);
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
        ConfigValues c = plugin.configValues();
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
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM group_members WHERE room_id=? AND user_uuid=?")) {
            ps.setString(1, id);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (SQLException ex) { r.error = "leave_failed"; return r; }
        if (countMembers(id) <= 0) {
            archiveRoom(id);
        } else if ("owner".equals(role)) {
            promoteOldestMemberToOwner(id);
        }
        r.ok = true;
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
        ConfigValues c = plugin.configValues();
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
        return send(userUuid, roomId, body, "");
    }

    public synchronized SendResult send(String userUuid, String roomId, String body, String clientMessageId) {
        SendResult r = new SendResult();
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        String requestId = String.valueOf(clientMessageId == null ? "" : clientMessageId).trim();
        if (requestId.length() > 180) requestId = requestId.substring(0, 180);
        if (connection == null) { r.error = "store_unavailable"; return r; }
        if (!isMember(user, id)) { r.error = "not_member"; return r; }
        if (isRoomLocked(id)) { r.error = "room_locked"; return r; }
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
        ConfigValues c = plugin.configValues();
        int max = c == null ? 500 : c.groupChatMaxMessageLength;
        if (max > 0 && message.length() > max) message = message.substring(0, max);
        if (message.isBlank()) { r.error = "empty_message"; return r; }
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO group_messages(room_id,sender_uuid,body,created_at,hidden,client_message_id) VALUES(?,?,?,?,0,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, id);
            ps.setString(2, user);
            ps.setString(3, message);
            ps.setLong(4, now);
            ps.setString(5, requestId);
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

    public synchronized ActionResult updateSettings(String userUuid, String roomId, String name, String visibility, String password, boolean passwordSet) {
        ActionResult r = new ActionResult();
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        if (!canManage(user, id)) { r.error = "permission_denied"; return r; }
        RoomInfo info = roomInfo(id);
        if (info == null) { r.error = "room_not_found"; return r; }
        ConfigValues c = plugin.configValues();
        String newName = name == null ? info.name : stripName(name, c == null ? 32 : c.groupChatMaxRoomNameLength);
        if (newName.isBlank()) newName = info.name;
        String newVis = visibility == null || visibility.isBlank() ? info.visibility : normalizeVisibility(visibility);
        if ("public".equals(newVis) && c != null && !c.groupChatAllowPublicRooms) { r.error = "public_disabled"; return r; }
        String newHash = info.passwordHash;
        if (passwordSet) newHash = hashPassword(password);
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("UPDATE group_rooms SET name=?, visibility=?, password_hash=?, updated_at=? WHERE id=?")) {
            ps.setString(1, newName);
            ps.setString(2, newVis);
            ps.setString(3, newHash);
            ps.setLong(4, now);
            ps.setString(5, id);
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
            plugin.getLogger().warning("Failed to list group rooms: " + ex.getMessage());
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
            plugin.getLogger().warning("Failed to list group invites: " + ex.getMessage());
        }
        return out;
    }

    public synchronized List<GroupMessage> listMessages(String userUuid, String roomId, long before, int limit) {
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        List<GroupMessage> out = new ArrayList<>();
        if (connection == null || !isMember(user, id)) return out;
        int max = limit <= 0 ? 100 : Math.min(limit, 300);
        String sql = before > 0
                ? "SELECT id,room_id,sender_uuid,body,created_at FROM group_messages WHERE room_id=? AND hidden=0 AND id<? AND id NOT IN (SELECT message_id FROM group_message_state WHERE user_uuid=? AND hidden=1) ORDER BY id DESC LIMIT ?"
                : "SELECT id,room_id,sender_uuid,body,created_at FROM group_messages WHERE room_id=? AND hidden=0 AND id NOT IN (SELECT message_id FROM group_message_state WHERE user_uuid=? AND hidden=1) ORDER BY id DESC LIMIT ?";
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
            plugin.getLogger().warning("Failed to list group messages: " + ex.getMessage());
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

    public synchronized boolean hideMessage(String userUuid, long messageId) {
        String user = normalizeUuid(userUuid);
        String roomId = roomIdForMessage(user, messageId);
        if (roomId.isBlank()) return false;
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO group_message_state(message_id,user_uuid,hidden) VALUES(?,?,1) ON CONFLICT(message_id,user_uuid) DO UPDATE SET hidden=1")) {
            ps.setLong(1, messageId);
            ps.setString(2, user);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
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

    public synchronized List<String> listHiddenRoomsJson(String userUuid, int limit) {
        String user = normalizeUuid(userUuid);
        List<String> out = new ArrayList<>();
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
                    if (room != null) out.add(room.toJson());
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to list hidden group rooms: " + ex.getMessage());
        }
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
            plugin.getLogger().warning("Failed to list group message bodies: " + ex.getMessage());
        }
        return out;
    }

    public synchronized boolean deleteRoom(String roomId) {
        String id = cleanId(roomId);
        if (connection == null || id.isBlank()) return false;
        try {
            connection.setAutoCommit(false);
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
            plugin.getLogger().warning("Failed to delete group room: " + ex.getMessage());
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

    public synchronized List<String> listMembersJson(String requesterUuid, String roomId) {
        String requester = normalizeUuid(requesterUuid);
        String id = cleanId(roomId);
        List<String> out = new ArrayList<>();
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
                    m.put("online", isOnlineUuid(uuid));
                    m.put("joinedAt", rs.getLong(3));
                    out.add(JsonUtil.obj(m));
                }
            }
        } catch (SQLException ex) { plugin.getLogger().warning("Failed to list group members: " + ex.getMessage()); }
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
            plugin.getLogger().warning("Failed to list group bans: " + ex.getMessage());
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
                    ConfigValues c = plugin.configValues();
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
        } catch (SQLException ex) { plugin.getLogger().warning("Failed to list group admin summaries: " + ex.getMessage()); }
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
            plugin.getLogger().warning("Failed to update group room session flags: " + ex.getMessage());
            return false;
        }
    }

    public synchronized String cleanupPreviewJson() {
        Map<String,Object> m = new LinkedHashMap<>();
        ConfigValues c = plugin.configValues();
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
        } catch (SQLException ex) { plugin.getLogger().warning("Failed to calculate group cleanup preview: " + ex.getMessage()); }
        return JsonUtil.obj(m);
    }

    public synchronized boolean uploadNameReferenced(String name, String ignoreRoomId) {
        String n = String.valueOf(name == null ? "" : name).trim();
        if (!n.matches("[A-Za-z0-9._-]+")) return false;
        String ignore = cleanId(ignoreRoomId);
        if (connection == null) return false;
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM group_messages WHERE hidden=0 AND room_id<>? AND body LIKE ? LIMIT 1")) {
            ps.setString(1, ignore);
            ps.setString(2, "%" + n + "%");
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException ex) { return false; }
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
        String normalized = normalizeUuid(uuid);
        if (normalized.isBlank()) return false;
        try {
            Player p = plugin.getServer().getPlayer(UUID.fromString(normalized));
            return p != null && p.isOnline();
        } catch (Exception ignored) {
            return false;
        }
    }

    private int onlineMemberCount(String roomId) {
        int count = 0;
        for (String uuid : memberUuids(roomId)) {
            if (isOnlineUuid(uuid)) count++;
        }
        return count;
    }

    private GroupRoom roomForUser(String userUuid, String roomId) {
        String user = normalizeUuid(userUuid);
        String id = cleanId(roomId);
        String sql = "SELECT r.id,r.name,r.owner_uuid,r.visibility,r.password_hash,r.updated_at," +
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
                room.role = rs.getString(7) == null ? "" : rs.getString(7);
                long lastRead = rs.getLong(8);
                room.member = room.role != null && !room.role.isBlank();
                room.memberCount = rs.getInt(9);
                room.onlineMemberCount = onlineMemberCount(room.id);
                room.lastMessageId = rs.getLong(10);
                room.lastMessage = rs.getString(11) == null ? "" : rs.getString(11);
                room.lastSenderUuid = normalizeUuid(rs.getString(12));
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

    private GroupMessage messageById(String userUuid, long messageId) {
        if (messageId <= 0) return null;
        GroupMessage result = null;
        try (PreparedStatement ps = connection.prepareStatement("SELECT id,room_id,sender_uuid,body,created_at FROM group_messages WHERE id=?")) {
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
        msg.id = rs.getLong(1);
        msg.roomId = rs.getString(2);
        msg.senderUuid = normalizeUuid(rs.getString(3));
        msg.body = rs.getString(4) == null ? "" : rs.getString(4);
        msg.createdAt = rs.getLong(5);
        fillIdentity(msg);
        return msg;
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
        String u = normalizeUuid(uuid);
        if (u.isBlank()) return new PlayerIdentity("", "", "");
        try {
            Player player = plugin.getServer().getPlayer(UUID.fromString(u));
            if (player != null) {
                String display = plugin.displayPlayerName(player);
                if (display != null && !display.isBlank()) plugin.storage().updateLastDisplayName(u, player.getName(), display);
                return new PlayerIdentity(u, player.getName(), display == null || display.isBlank() ? player.getName() : display);
            }
        } catch (Exception ignored) {}
        PlayerIdentity known = plugin.storage().findKnownPlayerByUuid(u);
        if (known != null) return known;
        try {
            OfflinePlayer off = plugin.getServer().getOfflinePlayer(UUID.fromString(u));
            String name = off == null ? "" : String.valueOf(off.getName() == null ? "" : off.getName());
            return new PlayerIdentity(u, name, plugin.storage().knownDisplayName(u));
        } catch (Exception ignored) {}
        return new PlayerIdentity(u, u, u);
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

    private String hashPassword(String password) {
        String p = String.valueOf(password == null ? "" : password);
        if (p.isBlank()) return "";
        ConfigValues c = plugin.configValues();
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

    public static class ActionResult {
        public boolean ok;
        public String error = "";
        public GroupRoom room;
    }

    public static class CreateResult extends ActionResult {}

    public static class SendResult extends ActionResult {
        public boolean duplicate;
        public long messageId;
        public GroupMessage message;
    }
}
