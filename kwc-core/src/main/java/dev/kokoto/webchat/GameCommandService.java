package dev.kokoto.webchat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Loader-neutral implementation of the game-side KWC private-chat/admin commands.
 * Fabric and NeoForge route their /kchat and /kc command tails here so command
 * behavior stays aligned without loader-specific copies of DM/group/reply logic.
 */
public final class GameCommandService {
    public interface Sender {
        boolean isPlayer();
        UUID uuid();
        String username();
        String displayName();
        String actorName();
        void send(String message);
    }

    private static final class DmListCursor { int page = 1; int pageSize = 10; }
    private static final class DmReadCursor {
        String otherUuid = "", otherUsername = "", otherDisplayName = "";
        int page = 1, pageSize = 20;
    }
    private static final class GroupReadCursor {
        String roomId = "", roomName = "";
        int pageSize = 20;
        long beforeId = 0L;
        final Deque<Long> newerBeforeIds = new ArrayDeque<>();
    }

    private final WebChatHost host;
    private final Supplier<WebChatServer> webServer;
    private final Map<String, DmListCursor> dmList = new ConcurrentHashMap<>();
    private final Map<String, DmReadCursor> dmRead = new ConcurrentHashMap<>();
    private final Map<String, GroupReadCursor> groupRead = new ConcurrentHashMap<>();

    public GameCommandService(WebChatHost host, Supplier<WebChatServer> webServer) {
        this.host = Objects.requireNonNull(host, "host");
        this.webServer = Objects.requireNonNull(webServer, "webServer");
    }

    public int execute(Sender sender, String rawArguments) {
        String raw = clean(rawArguments);
        ConfigValues c = host.configValues();
        if (c == null) return fail(sender, "KOKOTO WebChat has not loaded a configuration yet.");
        if (!c.pluginEnabled) return fail(sender, msg("disabledByConfig", "KOKOTO WebChat is disabled in config.yml."));
        if (raw.isBlank()) return help(sender);
        String[] head = raw.split("\\s+", 2);
        String sub = head[0].toLowerCase(Locale.ROOT);
        String tail = head.length > 1 ? head[1] : "";
        return switch (sub) {
            case "dm" -> dm(sender, tail);
            case "group", "gc" -> group(sender, tail);
            case "reply" -> reply(sender, tail);
            case "admin" -> admin(sender, tail);
            case "guest" -> guest(sender, tail);
            case "sessions" -> sessions(sender);
            case "revoke" -> revoke(sender, tail);
            case "filter" -> filter(sender, tail);
            case "settings" -> settings(sender, tail);
            default -> help(sender);
        };
    }

    public int help(Sender sender) {
        ConfigValues c = host.configValues();
        sender.send("KOKOTO WebChat");
        sender.send("/kchat auth <code> - " + msg("helpAuth", "Link web account"));
        sender.send("/kchat password <newPassword> - " + msg("helpPassword", "Set web login password"));
        sender.send("/kchat status - " + msg("helpStatus", "Show runtime status"));
        if (has(sender, "kwc.admin")) {
            sender.send("/kchat reload - " + msg("helpReload", "Reload configuration"));
            sender.send("/kchat admin ... - " + msg("helpAdmin", "Manage admin accounts and roles"));
            sender.send("/kchat guest ... - " + msg("helpGuest", "Manage guest/IP mutes"));
            sender.send("/kchat sessions - " + msg("helpSessions", "List web sessions"));
            sender.send("/kchat revoke <username> - " + msg("helpRevoke", "Revoke web sessions"));
            sender.send("/kchat filter ... - " + msg("helpFilter", "Manage content filter rules"));
            sender.send("/kchat settings ... - " + msg("helpSettings", "View or change live settings"));
        }
        if (sender.isPlayer() && c != null && c.pluginEnabled && c.directMessageEnabled && has(sender, "kwc.dm")) {
            sender.send("/kchat dm <player> <message> - " + msg("helpDm", "Send a direct message"));
            sender.send("/kchat dm list [pageSize] - " + msg("helpDmList", "List direct message threads"));
            sender.send("/kchat dm read <player> [pageSize] - " + msg("helpDmRead", "Read a direct message thread"));
            sender.send("/kchat dm hide <messageId> - " + msg("helpDmHide", "Hide a direct message from your view"));
        }
        if (sender.isPlayer() && c != null && c.pluginEnabled && c.groupChatEnabled && has(sender, "kwc.group")) {
            sender.send("/kchat group list - " + msg("helpGroupList", "List joined group chats"));
            sender.send("/kchat group read <room> [pageSize] - " + msg("helpGroupRead", "Read a group chat"));
            sender.send("/kchat group <room> <message> - " + msg("helpGroup", "Send a group chat message"));
        }
        if (sender.isPlayer() && c != null && c.pluginEnabled && c.replyGameClickEnabled && has(sender, "kwc.reply")) {
            sender.send("/kchat reply <messageId> <message> - " + msg("helpReply", "Reply to a public KWC message"));
        }
        return 1;
    }

    private int reply(Sender sender, String tail) {
        if (!requirePlayer(sender) || !require(sender, "kwc.reply")) return 0;
        ConfigValues c = host.configValues();
        if (c == null || !c.replyGameClickEnabled) return fail(sender, msg("replyDisabled", "In-game replies are disabled."));
        String[] parts = tail.split("\\s+", 2);
        if (parts.length < 2) return usage(sender, "/kchat reply <messageId> <message>");
        int max = c.maxUrlMessageLength > 0 ? c.maxUrlMessageLength : c.maxMessageLength;
        String body = stripMessage(parts[1], max);
        if (body.isBlank()) return fail(sender, msg("replyEmpty", "Reply message is empty."));
        WebChatServer server = webServer.get();
        ChatMessage made = server == null ? null : server.publishReplyFromGame(
                sender.displayName(), sender.username(), sender.uuid().toString(), parts[0], body, body);
        if (made == null) return fail(sender, msg("replyTargetNotFound", "The referenced message could not be found."));
        return 1;
    }

    private int dm(Sender sender, String tail) {
        ConfigValues c = host.configValues();
        DirectMessageStore store = host.directMessages();
        if (c == null || !c.directMessageEnabled || store == null || !store.available()) return fail(sender, msg("dmDisabled", "Direct messages are disabled."));
        if (!requirePlayer(sender) || !require(sender, "kwc.dm")) return 0;
        String me = sender.uuid().toString();
        host.storage().updateLastDisplayName(me, sender.username(), sender.displayName());
        String raw = clean(tail);
        if (raw.isBlank()) return dmListOpen(sender, me, 10);
        String[] p = raw.split("\\s+", 2);
        String action = p[0].toLowerCase(Locale.ROOT);
        String rest = p.length > 1 ? p[1] : "";
        if (action.equals("list") || action.equals("unread")) {
            String rr = clean(rest);
            if (rr.equalsIgnoreCase("next")) return dmListMove(sender, me, 1);
            if (rr.equalsIgnoreCase("prev") || rr.equalsIgnoreCase("previous")) return dmListMove(sender, me, -1);
            return dmListOpen(sender, me, parseLimit(rr, 10));
        }
        if (action.equals("read") || action.equals("view")) return dmReadOpen(sender, me, rest);
        if (action.equals("next")) return dmReadMove(sender, me, 1);
        if (action.equals("prev") || action.equals("previous")) return dmReadMove(sender, me, -1);
        if (action.equals("hide") || action.equals("delete")) return dmHide(sender, me, rest);
        if (!c.directMessageAllowGameSend) return fail(sender, msg("dmGameSendDisabled", "Sending direct messages from game is disabled."));
        if (rest.isBlank()) return usage(sender, "/kchat dm <player> <message>");
        String[] send = raw.split("\\s+", 2);
        if (send.length < 2) return usage(sender, "/kchat dm <player> <message>");
        return dmSend(sender, me, send[0], send[1]);
    }

    private int dmSend(Sender sender, String me, String targetInput, String rawBody) {
        ConfigValues c = host.configValues();
        PlayerIdentity target = findDmTarget(targetInput);
        if (target == null || clean(target.uuid).isBlank()) return fail(sender, msg("dmPlayerNotFound", "Player not found. The player must have joined at least once."));
        RemotePlayerRef remote = RemotePlayerRef.parse(target.uuid);
        ServerRelay relay = host.serverRelay();
        if (remote != null && (relay == null || !relay.canRouteDirectMessage(remote.serverId))) return fail(sender, msg("dmRemoteServerUnavailable", "The target server is unavailable."));
        String raw = stripMessage(rawBody, c.directMessageMaxMessageLength);
        WebChatServer filterServer = webServer.get();
        if (filterServer != null) {
            ContentFilterResult filtered = filterServer.filterContent(raw, ContentFilterEngine.Scope.DM);
            if (filtered.blocked) return fail(sender, filterServer.contentFilterBlockedMessage(filtered));
            raw = filtered.message;
        }
        String body = host.applyMessageTokens(raw);
        String gameBody = host.applyMessageTokensForGame(raw);
        if (body.isBlank()) return fail(sender, msg("dmEmpty", "Message is empty."));
        String relayId = remote == null ? "" : relay.createDirectMessageRelayId();
        DirectMessageStore.SendResult r = remote == null
                ? host.directMessages().send(me, target.uuid, body)
                : host.directMessages().sendPendingRemote(me, target.uuid, body, relayId, remote.serverId, "");
        if (!r.ok) return fail(sender, msg("dmFailed", "Direct message failed: {error}", "error", r.error));
        String threadId = r.thread == null ? "" : r.thread.id;
        long messageId = r.message == null ? 0L : r.message.id;
        WebChatServer server = webServer.get();
        if (server != null) {
            server.publishDirectMessageUpdate(me, target.uuid, threadId);
            server.inspectAdminDirectMessageAlert("dm:" + messageId, sender.displayName(), "game", body);
            server.dispatchWebPushDirectMessage(me, sender.displayName(), target.uuid, target.label(), threadId, messageId, body);
        }
        if (remote != null) {
            String finalTarget = target.uuid;
            relay.publishDirectMessage(relayId, me, sender.username(), sender.displayName(), remote.serverId, remote.playerUuid,
                    target.username, target.displayName, body, gameBody).whenComplete((delivery, error) -> {
                boolean delivered = error == null && delivery != null && delivery.delivered;
                String errorCode = delivered ? "" : (delivery == null ? "dm_transport_error" : delivery.error);
                host.directMessages().updateDeliveryStatus(messageId, delivered ? "delivered" : "failed", errorCode);
                WebChatServer current = webServer.get();
                if (current != null) current.publishDirectMessageUpdate(me, finalTarget, threadId);
                if (!delivered && sender.uuid() != null) host.platformAdapter().sendPlainMessage(sender.uuid(),
                        msg("dmDeliveryFailed", "Direct message delivery failed. You can retry it from web chat."));
            });
        } else if (c.directMessageNotifyOnMessage) {
            try {
                UUID targetUuid = UUID.fromString(target.uuid);
                host.platformAdapter().sendPlainMessage(targetUuid, msg("dmIncoming", "DM from {player}: {message}",
                        "player", sender.displayName(), "message", gameBody));
            } catch (IllegalArgumentException ignored) {}
        }
        sender.send(msg("dmSentEcho", "to: {player} {message}", "player", target.label(), "message", gameBody));
        return 1;
    }

    private int dmListOpen(Sender sender, String me, int pageSize) {
        DmListCursor c = new DmListCursor(); c.pageSize = clamp(pageSize, 1, 100);
        dmList.put(key(me), c); return dmListShow(sender, me, c);
    }
    private int dmListMove(Sender sender, String me, int delta) {
        DmListCursor c = dmList.get(key(me));
        if (c == null) return dmListOpen(sender, me, 10);
        int total = host.directMessages().countThreads(me), pages = Math.max(1, ceilPages(total, c.pageSize));
        int next = c.page + delta;
        if (next < 1) return fail(sender, msg("dmListFirstPage", "You are already on the newest conversation list page."));
        if (next > pages) return fail(sender, msg("dmListLastPage", "You are already on the oldest conversation list page."));
        c.page = next; return dmListShow(sender, me, c);
    }
    private int dmListShow(Sender sender, String me, DmListCursor c) {
        int total = host.directMessages().countThreads(me), pages = Math.max(1, ceilPages(total, c.pageSize));
        if (c.page > pages) c.page = pages;
        List<DirectMessageThread> threads = host.directMessages().listThreadsPage(me, c.page, c.pageSize);
        int unread = host.directMessages().unreadCount(me);
        sender.send(msg("dmTitlePage", "Direct messages: {count} unread (page {page}/{pages})", "count", Integer.toString(unread), "page", Integer.toString(c.page), "pages", Integer.toString(pages)));
        if (threads.isEmpty()) return fail(sender, msg("dmNoThreads", "No direct message threads."));
        for (DirectMessageThread t : threads) sender.send("- " + dmThreadLabel(t) + (t.unread > 0 ? " (" + t.unread + ")" : "") + ": " + trimOneLine(t.lastMessage, 60));
        sender.send(msg("dmListNavHint", "Use /kchat dm list prev for newer conversations, /kchat dm list next for older conversations."));
        return 1;
    }

    private int dmReadOpen(Sender sender, String me, String rest) {
        String[] p = clean(rest).split("\\s+");
        if (p.length < 1 || p[0].isBlank()) return usage(sender, "/kchat dm read <player> [pageSize]");
        PlayerIdentity target = findDmTarget(p[0]);
        if (target == null || clean(target.uuid).isBlank()) return fail(sender, msg("dmPlayerNotFound", "Player not found. The player must have joined at least once."));
        DmReadCursor c = new DmReadCursor(); c.otherUuid = target.uuid; c.otherUsername = target.username; c.otherDisplayName = target.displayName;
        if (p.length >= 2) c.pageSize = clamp(parseLimit(p[1], 20), 1, 100);
        dmRead.put(key(me), c); return dmReadShow(sender, me, c);
    }
    private int dmReadMove(Sender sender, String me, int delta) {
        DmReadCursor c = dmRead.get(key(me));
        if (c == null) return fail(sender, msg("dmReadNoCursor", "Open a thread first: /kchat dm read <player>"));
        int total = host.directMessages().countMessagesBetween(me, c.otherUuid), pages = Math.max(1, ceilPages(total, c.pageSize));
        int next = c.page + delta;
        if (next < 1) return fail(sender, msg("dmReadFirstPage", "You are already on the newest page."));
        if (next > pages) return fail(sender, msg("dmReadLastPage", "You are already on the oldest page."));
        c.page = next; return dmReadShow(sender, me, c);
    }
    private int dmReadShow(Sender sender, String me, DmReadCursor c) {
        int total = host.directMessages().countMessagesBetween(me, c.otherUuid), pages = Math.max(1, ceilPages(total, c.pageSize));
        if (c.page > pages) c.page = pages;
        String threadId = host.directMessages().threadIdForParticipants(me, c.otherUuid);
        long before = host.directMessages().readPosition(threadId, me);
        List<DirectMessageMessage> messages = host.directMessages().listMessagesBetweenPage(me, c.otherUuid, c.page, c.pageSize);
        long after = host.directMessages().readPosition(threadId, me);
        if (after > before) { WebChatServer s = webServer.get(); if (s != null) s.publishDirectMessageUpdate(me, c.otherUuid, threadId); }
        if (c.page == 1) {
            DirectMessageStore.RemoteReadReceipt receipt = host.directMessages().latestRemoteReadReceipt(threadId, me);
            ServerRelay relay = host.serverRelay(); if (receipt.ok && relay != null) relay.publishDirectMessageRead(receipt.sourceServerId, receipt.relayId);
        }
        sender.send(msg("dmReadTitlePage", "Direct messages with {player} (page {page}/{pages}):", "player", identityLabel(c.otherDisplayName, c.otherUsername, c.otherUuid), "page", Integer.toString(c.page), "pages", Integer.toString(pages)));
        if (messages.isEmpty()) return fail(sender, msg("dmNoMessages", "No messages in this thread."));
        for (DirectMessageMessage m : messages) sender.send("#" + m.id + " " + (me.equalsIgnoreCase(m.senderUuid) ? msg("dmMe", "me") : identityLabel(m.senderDisplayName, m.senderUsername, m.senderUuid)) + ": " + m.body);
        sender.send(msg("dmReadNavHint", "Use /kchat dm prev for newer messages, /kchat dm next for older messages."));
        sender.send(msg("dmHideHint", "Hide one from your view: /kchat dm hide <messageId>"));
        return 1;
    }
    private int dmHide(Sender sender, String me, String rest) {
        long id; try { id = Long.parseLong(clean(rest).split("\\s+")[0]); } catch (Exception ex) { return fail(sender, msg("dmInvalidMessageId", "Invalid message id.")); }
        String threadId = host.directMessages().threadIdForMessage(me, id);
        if (!host.directMessages().hideMessage(me, id)) return fail(sender, msg("dmHideFailed", "Failed to hide message."));
        WebChatServer s = webServer.get(); if (s != null) s.publishDirectMessageUpdate(me, me, threadId);
        return ok(sender, msg("dmHidden", "Message hidden from your view."));
    }

    private int group(Sender sender, String tail) {
        ConfigValues c = host.configValues(); GroupChatStore store = host.groupChats();
        if (c == null || !c.groupChatEnabled || store == null || !store.available()) return fail(sender, msg("groupDisabled", "Group chats are disabled."));
        if (!requirePlayer(sender) || !require(sender, "kwc.group")) return 0;
        String me = sender.uuid().toString(); host.storage().updateLastDisplayName(me, sender.username(), sender.displayName());
        String raw = clean(tail);
        if (raw.isBlank() || raw.equalsIgnoreCase("list") || raw.equalsIgnoreCase("rooms")) return groupList(sender, me);
        String[] p = raw.split("\\s+", 2); String action = p[0].toLowerCase(Locale.ROOT), rest = p.length > 1 ? p[1] : "";
        if (action.equals("read") || action.equals("view")) return groupReadOpen(sender, me, rest);
        if (action.equals("next")) return groupReadMove(sender, me, 1);
        if (action.equals("prev") || action.equals("previous")) return groupReadMove(sender, me, -1);
        if (action.equals("send")) {
            String[] sr = rest.split("\\s+", 2); if (sr.length < 2) return usage(sender, "/kchat group send <room> <message>");
            GroupRoom room = findGroupRoom(me, sr[0]); if (room == null) return fail(sender, msg("groupRoomNotFound", "Group chat room not found or you are not a member."));
            return groupSend(sender, me, room, sr[1]);
        }
        if (rest.isBlank()) return usage(sender, "/kchat group <room> <message>");
        GroupRoom room = findGroupRoom(me, p[0]); if (room == null) return fail(sender, msg("groupRoomNotFound", "Group chat room not found or you are not a member."));
        return groupSend(sender, me, room, rest);
    }
    private int groupSend(Sender sender, String me, GroupRoom room, String rawBody) {
        ConfigValues c = host.configValues(); String raw = stripMessage(rawBody, c == null ? 500 : c.groupChatMaxMessageLength);
        WebChatServer filterServer = webServer.get();
        if (filterServer != null) {
            ContentFilterResult filtered = filterServer.filterContent(raw, ContentFilterEngine.Scope.GROUP);
            if (filtered.blocked) return fail(sender, filterServer.contentFilterBlockedMessage(filtered));
            raw = filtered.message;
        }
        String body = host.applyMessageTokens(raw), gameBody = host.applyMessageTokensForGame(raw);
        if (body.isBlank()) return fail(sender, msg("groupEmpty", "Message is empty."));
        GroupChatStore.SendResult r = host.groupChats().send(me, room.id, body);
        if (!r.ok) return fail(sender, msg("groupFailed", "Group chat failed: {error}", "error", r.error));
        WebChatServer s = webServer.get(); if (s != null) { s.publishGroupChatUpdate(room.id); s.inspectAdminGroupAlert("group:" + (r.message == null ? 0L : r.message.id), sender.displayName(), "game", body); s.dispatchWebPushGroupMessage(me, sender.displayName(), room, r.message, room.id); }
        sender.send(msg("groupSentEcho", "to group {room}: {message}", "room", room.name, "message", gameBody));
        for (String member : host.groupChats().memberUuids(room.id)) {
            if (member == null || member.equalsIgnoreCase(me) || RemotePlayerRef.isRemote(member)) continue;
            try { host.platformAdapter().sendPlainMessage(UUID.fromString(member), msg("groupIncoming", "Group {room} from {player}: {message}", "room", room.name, "player", sender.displayName(), "message", gameBody)); }
            catch (IllegalArgumentException ignored) {}
        }
        return 1;
    }
    private int groupList(Sender sender, String me) {
        List<GroupRoom> rooms = host.groupChats().listRooms(me, 100); int count = 0; for (GroupRoom r : rooms) if (r != null && r.member) count++;
        sender.send(msg("groupTitle", "Group chats: {count}", "count", Integer.toString(count)));
        if (count == 0) return fail(sender, msg("groupNoRooms", "No joined group chats."));
        for (GroupRoom r : rooms) if (r != null && r.member) sender.send("- " + r.name + (r.unread > 0 ? " (" + r.unread + ")" : "") + " [" + shortId(r.id) + "]: " + trimOneLine(r.lastMessage, 60));
        sender.send("/kchat group <room|id> <message>"); sender.send("/kchat group read <room|id> [pageSize]"); return 1;
    }
    private int groupReadOpen(Sender sender, String me, String rest) {
        String[] p = clean(rest).split("\\s+"); if (p.length < 1 || p[0].isBlank()) return usage(sender, "/kchat group read <room> [pageSize]");
        GroupRoom room = findGroupRoom(me, p[0]); if (room == null) return fail(sender, msg("groupRoomNotFound", "Group chat room not found or you are not a member."));
        GroupReadCursor c = new GroupReadCursor(); c.roomId = room.id; c.roomName = room.name; if (p.length >= 2) c.pageSize = clamp(parseLimit(p[1], 20), 1, 100);
        groupRead.put(key(me), c); return groupReadShow(sender, me, c);
    }
    private int groupReadMove(Sender sender, String me, int direction) {
        GroupReadCursor c = groupRead.get(key(me)); if (c == null) return fail(sender, msg("groupReadNoCursor", "Open a group chat first: /kchat group read <room>"));
        if (direction < 0) {
            if (c.newerBeforeIds.isEmpty()) return fail(sender, msg("groupReadFirstPage", "You are already on the newest group page."));
            c.beforeId = c.newerBeforeIds.pop(); return groupReadShow(sender, me, c);
        }
        List<GroupMessage> current = host.groupChats().listMessages(me, c.roomId, c.beforeId, c.pageSize);
        if (current.isEmpty()) return fail(sender, msg("groupReadLastPage", "You are already on the oldest group page."));
        long nextBefore = current.get(0).id;
        List<GroupMessage> older = host.groupChats().listMessages(me, c.roomId, nextBefore, c.pageSize);
        if (older.isEmpty()) return fail(sender, msg("groupReadLastPage", "You are already on the oldest group page."));
        c.newerBeforeIds.push(c.beforeId); c.beforeId = nextBefore; return groupReadShow(sender, me, c);
    }
    private int groupReadShow(Sender sender, String me, GroupReadCursor c) {
        long readBefore = host.groupChats().readPosition(c.roomId, me);
        List<GroupMessage> messages = host.groupChats().listMessages(me, c.roomId, c.beforeId, c.pageSize);
        if (c.beforeId == 0L) {
            boolean marked = host.groupChats().markRead(c.roomId, me); long after = host.groupChats().readPosition(c.roomId, me);
            if (marked && after > readBefore) { WebChatServer s = webServer.get(); if (s != null) s.publishGroupChatUpdate(c.roomId); }
        }
        sender.send(msg("groupReadTitle", "Group chat {room}:", "room", c.roomName));
        if (messages.isEmpty()) return fail(sender, msg("groupNoMessages", "No messages in this group chat."));
        for (GroupMessage m : messages) sender.send("#" + m.id + " " + (me.equalsIgnoreCase(m.senderUuid) ? msg("dmMe", "me") : identityLabel(m.senderDisplayName, m.senderUsername, m.senderUuid)) + ": " + m.body);
        sender.send(msg("groupReadNavHint", "Use /kchat group prev for newer messages, /kchat group next for older messages."));
        sender.send(msg("groupReadHint", "Send: /kchat group {room} <message>", "room", c.roomName)); return 1;
    }

    private int guest(Sender sender, String tail) {
        if (!require(sender, "kwc.admin")) return 0;
        String raw = clean(tail); if (raw.isBlank()) { sender.send("/kchat guest mute <guest|ip> <value> [minutes] [reason]"); sender.send("/kchat guest unmute <guest|ip> <value>"); sender.send("/kchat guest list"); return 1; }
        String[] p = raw.split("\\s+", 2); String action = p[0].toLowerCase(Locale.ROOT), rest = p.length > 1 ? p[1] : "";
        if (action.equals("list")) {
            List<ModerationEntry> list = host.moderation().list(); if (list.isEmpty()) return ok(sender, msg("guestNoMutes", "There are no guest/IP mutes."));
            sender.send(msg("guestMutesTitle", "KOKOTO WebChat mutes:"));
            for (ModerationEntry e : list) sender.send(msg("guestMuteEntry", "- {type}:{value} reason={reason} expires={expires}", "type", e.type, "value", e.value, "reason", e.reason, "expires", e.expiresAt == 0 ? msg("never", "never") : Long.toString(e.expiresAt)));
            return 1;
        }
        if (action.equals("mute")) {
            String[] a = rest.split("\\s+", 4); if (a.length < 2) return usage(sender, "/kchat guest mute <guest|ip> <value> [minutes] [reason]");
            long minutes = host.configValues().defaultMuteMinutes; if (a.length >= 3) try { minutes = Long.parseLong(a[2]); } catch (NumberFormatException ignored) {}
            String reason = a.length >= 4 ? a[3] : ""; host.moderation().mute(a[0], a[1], minutes, reason, sender.actorName());
            host.audit("command.guest-mute", sender.actorName(), Map.of("type", a[0], "value", a[1], "minutes", minutes, "reason", reason));
            return ok(sender, msg("guestMuteAdded", "Mute added: {type}:{value} ({minutes} min)", "type", a[0], "value", a[1], "minutes", Long.toString(minutes)));
        }
        if (action.equals("unmute")) {
            String[] a = rest.split("\\s+"); if (a.length < 2) return usage(sender, "/kchat guest unmute <guest|ip> <value>");
            boolean ok = host.moderation().unmute(a[0], a[1]); if (ok) host.audit("command.guest-unmute", sender.actorName(), Map.of("type", a[0], "value", a[1]));
            return ok ? ok(sender, msg("guestMuteRemoved", "Mute removed.")) : fail(sender, msg("guestMuteNotFound", "Mute not found."));
        }
        return fail(sender, msg("guestUnknownSubcommand", "Unknown guest subcommand."));
    }

    private int filter(Sender sender, String tail) {
        if (!require(sender, "kwc.admin.filter")) return 0;
        ConfigValues c = host.configValues();
        String raw = clean(tail);
        if (raw.isBlank() || raw.equalsIgnoreCase("status")) {
            sender.send(msg("filterStatus", "Content filter: {state} / public={public} group={group} dm={dm} / rules={rules}",
                    "state", c.contentFilterEnabled ? "ON" : "OFF", "public", Boolean.toString(c.contentFilterPublic),
                    "group", Boolean.toString(c.contentFilterGroup), "dm", Boolean.toString(c.contentFilterDm),
                    "rules", Integer.toString(c.contentFilterRules == null ? 0 : c.contentFilterRules.size())));
            return 1;
        }
        String[] p = raw.split("\\s+", 2);
        String action = p[0].toLowerCase(Locale.ROOT), rest = p.length > 1 ? p[1] : "";
        if (action.equals("enable") || action.equals("disable")) {
            RuntimeSettingsController.Result r = RuntimeSettingsController.set(host, "content-filter.enabled", Boolean.toString(action.equals("enable")));
            return settingResult(sender, r);
        }
        if (action.equals("scope")) {
            String[] a = clean(rest).split("\\s+");
            if (a.length < 2 || !Set.of("public","group","dm").contains(a[0].toLowerCase(Locale.ROOT))) return usage(sender, "/kchat filter scope <public|group|dm> <on|off>");
            RuntimeSettingsController.Result r = RuntimeSettingsController.set(host, "content-filter.scopes." + a[0].toLowerCase(Locale.ROOT), a[1]);
            return settingResult(sender, r);
        }
        if (action.equals("list")) {
            List<ContentFilterRule> rules = c.contentFilterRules == null ? List.of() : c.contentFilterRules;
            sender.send(msg("filterListTitle", "Content-filter rules: {count}", "count", Integer.toString(rules.size())));
            for (ContentFilterRule source : rules) {
                ContentFilterRule r = source.copy(); r.normalize();
                sender.send("- " + r.id + " [" + r.action + "] " + String.join(", ", r.words));
            }
            return 1;
        }
        if (action.equals("remove") || action.equals("delete")) {
            String id = clean(rest); if (id.isBlank()) return usage(sender, "/kchat filter remove <id>");
            ArrayList<ContentFilterRule> rules = copyFilterRules(c);
            boolean removed = rules.removeIf(r -> r != null && r.id != null && r.id.equalsIgnoreCase(id));
            if (!removed) return fail(sender, msg("filterRuleNotFound", "Filter rule not found: {id}", "id", id));
            RuntimeSettingsController.Result r = RuntimeSettingsController.replaceFilterRules(host, rules, sender.actorName());
            return r.ok() ? ok(sender, msg("filterRuleRemoved", "Filter rule removed: {id}", "id", id)) : fail(sender, r.error());
        }
        if (action.equals("add")) {
            String[] a = clean(rest).split("\\s+", 2);
            if (a.length < 2) return usage(sender, "/kchat filter add <block|mask|replace> <word> [=> replacement]");
            String mode = a[0].toLowerCase(Locale.ROOT);
            if (!Set.of("block","mask","replace").contains(mode)) return usage(sender, "/kchat filter add <block|mask|replace> <word> [=> replacement]");
            String word = a[1], replacement = "";
            if (mode.equals("replace")) {
                int arrow = word.indexOf("=>");
                if (arrow < 1) return usage(sender, "/kchat filter add replace <word> => <replacement>");
                replacement = word.substring(arrow + 2).trim(); word = word.substring(0, arrow).trim();
            }
            if (word.isBlank()) return fail(sender, msg("filterWordRequired", "A blocked word is required."));
            ContentFilterRule rule = new ContentFilterRule();
            rule.id = "rule-" + Long.toString(System.currentTimeMillis(), 36);
            rule.action = mode; rule.words = new ArrayList<>(List.of(word));
            if (mode.equals("replace")) rule.replacements = new ArrayList<>(List.of(replacement));
            rule.normalize();
            ArrayList<ContentFilterRule> rules = copyFilterRules(c); rules.add(rule);
            RuntimeSettingsController.Result r = RuntimeSettingsController.replaceFilterRules(host, rules, sender.actorName());
            return r.ok() ? ok(sender, msg("filterRuleAdded", "Filter rule added: {id}", "id", rule.id)) : fail(sender, r.error());
        }
        if (action.equals("test")) {
            String[] a = clean(rest).split("\\s+", 2);
            if (a.length < 2) return usage(sender, "/kchat filter test <public|group|dm> <text>");
            ContentFilterEngine.Scope scope;
            try { scope = ContentFilterEngine.Scope.valueOf(a[0].toUpperCase(Locale.ROOT)); }
            catch (Exception ex) { return usage(sender, "/kchat filter test <public|group|dm> <text>"); }
            WebChatServer server = webServer.get(); if (server == null) return fail(sender, "WebChat server is not running.");
            ContentFilterResult r = server.filterContent(a[1], scope);
            sender.send(msg("filterTestResult", "blocked={blocked} changed={changed} rule={rule} mode={mode} -> {message}",
                    "blocked", Boolean.toString(r.blocked), "changed", Boolean.toString(r.changed), "rule", r.ruleId, "mode", r.matchMode, "message", r.message));
            return 1;
        }
        sender.send("/kchat filter status|list|enable|disable");
        sender.send("/kchat filter scope <public|group|dm> <on|off>");
        sender.send("/kchat filter add <block|mask|replace> ... | remove <id> | test <scope> <text>");
        return 1;
    }

    private int settings(Sender sender, String tail) {
        if (!require(sender, "kwc.admin.settings")) return 0;
        String raw = clean(tail);
        if (raw.isBlank() || raw.equalsIgnoreCase("show")) {
            sender.send("auth.remember-session-days=" + RuntimeSettingsController.value(host.configValues(), "auth.remember-session-days"));
            sender.send("admin.admin-session-expire-hours=" + RuntimeSettingsController.value(host.configValues(), "admin.admin-session-expire-hours"));
            sender.send("upload.filename-mode=" + RuntimeSettingsController.value(host.configValues(), "upload.filename-mode"));
            sender.send("content-filter.enabled=" + RuntimeSettingsController.value(host.configValues(), "content-filter.enabled"));
            sender.send(msg("settingsShowHint", "Use /kchat settings show <key> or /kchat settings set <key> <value>."));
            return 1;
        }
        String[] p = raw.split("\\s+", 3);
        if (p[0].equalsIgnoreCase("show")) {
            if (p.length < 2) return settings(sender, "");
            Object value = RuntimeSettingsController.value(host.configValues(), p[1]);
            if (value == null || !RuntimeSettingsController.SUPPORTED_PATHS.contains(p[1].toLowerCase(Locale.ROOT))) return fail(sender, msg("settingsUnsupported", "Unsupported setting: {key}", "key", p[1]));
            return ok(sender, p[1] + "=" + value);
        }
        if (p[0].equalsIgnoreCase("set")) {
            if (p.length < 3) return usage(sender, "/kchat settings set <key> <value>");
            RuntimeSettingsController.Result r = RuntimeSettingsController.set(host, p[1], p[2]);
            return settingResult(sender, r);
        }
        return usage(sender, "/kchat settings show [key] | /kchat settings set <key> <value>");
    }

    private int settingResult(Sender sender, RuntimeSettingsController.Result r) {
        if (r == null || !r.ok()) return fail(sender, msg("settingsFailed", "Setting update failed: {error}", "error", r == null ? "unknown" : r.error()));
        return ok(sender, msg("settingsUpdated", "Updated {key}={value}. Sessions updated={updated}, expired={expired}.",
                "key", r.path(), "value", String.valueOf(r.value()), "updated", Integer.toString(r.sessionsUpdated()), "expired", Integer.toString(r.sessionsExpired())));
    }

    private ArrayList<ContentFilterRule> copyFilterRules(ConfigValues c) {
        ArrayList<ContentFilterRule> out = new ArrayList<>();
        if (c != null && c.contentFilterRules != null) for (ContentFilterRule r : c.contentFilterRules) if (r != null) out.add(r.copy());
        return out;
    }

    private int sessions(Sender sender) {
        if (!require(sender, "kwc.admin")) return 0;
        host.storage().cleanupExpiredSessions(); List<SessionContext> list = host.storage().listSessions();
        sender.send(msg("sessionsTitle", "KOKOTO WebChat sessions: {count}", "count", Integer.toString(list.size())));
        for (SessionContext c : list) sender.send(msg("sessionEntry", "- {username} {role} ip={ip}", "username", c.account.safeUsername(), "role", c.account.role.name(), "ip", c.session.lastIp));
        return 1;
    }
    private int revoke(Sender sender, String tail) {
        if (!require(sender, "kwc.admin")) return 0; String username = clean(tail).split("\\s+")[0]; if (username.isBlank()) return usage(sender, "/kchat revoke <username>");
        Account a = host.storage().findAccountByUsername(username); String uuid = a == null ? "" : clean(a.uuid).toLowerCase(Locale.ROOT);
        int removed = host.storage().revokeSessionsForUsername(username); WebChatServer s = webServer.get(); if (removed > 0 && s != null && !uuid.isBlank()) s.broadcastAuthExpired(uuid, "revoked");
        host.audit("command.revoke-sessions", sender.actorName(), Map.of("username", username, "removed", removed));
        return ok(sender, msg("revokeDone", "Revoked {count} session(s) for {username}.", "count", Integer.toString(removed), "username", username));
    }
    private int admin(Sender sender, String tail) {
        if (!require(sender, "kwc.admin")) return 0; String raw = clean(tail);
        if (raw.isBlank()) { sender.send("/kchat admin create <id>"); sender.send("/kchat admin password <id> <password>"); sender.send("/kchat admin role <id> <user|moderator|admin>"); return 1; }
        String[] p = raw.split("\\s+", 2); String action = p[0].toLowerCase(Locale.ROOT), rest = p.length > 1 ? p[1] : "";
        if (action.equals("create")) {
            String id = clean(rest).split("\\s+")[0]; if (id.isBlank()) return usage(sender, "/kchat admin create <id>");
            if (!host.configValues().allowLocalAdminAccounts) return fail(sender, msg("adminLocalDisabled", "Local admin account creation is disabled."));
            Account a = host.storage().createLocalAccount(id, Role.ADMIN); host.audit("command.admin-create", sender.actorName(), Map.of("username", a.safeUsername()));
            sender.send(msg("adminCreated", "Created local admin account: {username}", "username", a.safeUsername()));
            sender.send(msg("adminSetPasswordHint", "Set password: /kchat admin password {username} <password>", "username", a.safeUsername())); return 1;
        }
        if (action.equals("password")) {
            String[] a = rest.split("\\s+", 2); if (a.length < 2) return usage(sender, "/kchat admin password <id> <password>");
            Account account = host.storage().findAccountByUsername(a[0]); if (account == null) return fail(sender, msg("adminAccountNotFound", "Account not found."));
            host.storage().setPassword(account, a[1]); host.audit("command.admin-password", sender.actorName(), Map.of("username", account.safeUsername()));
            return ok(sender, msg("adminPasswordSetFor", "Password has been set for: {username}", "username", account.safeUsername()));
        }
        if (action.equals("role")) {
            String[] a = rest.split("\\s+"); if (a.length < 2) return usage(sender, "/kchat admin role <id> <user|moderator|admin>");
            Account account = host.storage().findAccountByUsername(a[0]); if (account == null) return fail(sender, msg("adminAccountNotFound", "Account not found."));
            Role role = Role.fromString(a[1], null); if (role == null || role == Role.GUEST) return fail(sender, msg("adminRoleInvalid", "Role must be one of: user, moderator, admin."));
            host.storage().setRole(account, role); host.audit("command.admin-role", sender.actorName(), Map.of("username", account.safeUsername(), "role", role.name()));
            return ok(sender, msg("adminRoleChanged", "{username} role changed to {role}.", "username", account.safeUsername(), "role", role.name()));
        }
        return fail(sender, msg("adminUnknownSubcommand", "Unknown admin subcommand."));
    }

    private PlayerIdentity findDmTarget(String rawInput) {
        String input = clean(rawInput); if (input.isBlank()) return null;
        RemotePlayerRef direct = RemotePlayerRef.parse(input); if (direct != null) return host.storage().findKnownPlayerByUuid(direct.key);
        int at = input.lastIndexOf('@');
        if (at > 0 && at < input.length() - 1) {
            String name = input.substring(0, at).trim(), serverId = RemotePlayerRef.normalizeServerId(input.substring(at + 1));
            String local = host.serverRelay() == null ? "" : RemotePlayerRef.normalizeServerId(host.serverRelay().serverId());
            if (local.isBlank() && host.configValues() != null) local = RemotePlayerRef.normalizeServerId(host.configValues().serverRelayServerId);
            if (!local.isBlank() && local.equals(serverId)) return host.storage().findKnownLocalPlayer(name);
            for (PlayerIdentity candidate : host.storage().listKnownPlayers(name, 200)) {
                if (candidate == null) continue; RemotePlayerRef remote = RemotePlayerRef.parse(candidate.uuid); if (remote == null || !serverId.equals(remote.serverId)) continue;
                String base = RemotePlayerRef.baseDisplayName(candidate.displayName, candidate.username, candidate.remoteServerName(), serverId);
                if (candidate.username.equalsIgnoreCase(name) || base.equalsIgnoreCase(name) || candidate.label().equalsIgnoreCase(name)) return candidate;
            }
            return null;
        }
        return host.storage().findKnownLocalPlayer(input);
    }
    private GroupRoom findGroupRoom(String userUuid, String input) {
        String needle = clean(input); if (needle.isBlank()) return null; List<GroupRoom> rooms = host.groupChats().listRooms(userUuid, 200);
        for (GroupRoom r : rooms) if (r != null && r.member && r.id.equalsIgnoreCase(needle)) return r;
        for (GroupRoom r : rooms) if (r != null && r.member && shortId(r.id).equalsIgnoreCase(needle)) return r;
        for (GroupRoom r : rooms) if (r != null && r.member && r.name != null && r.name.equalsIgnoreCase(needle)) return r;
        return null;
    }

    private boolean requirePlayer(Sender s) { if (s.isPlayer() && s.uuid() != null) return true; s.send(msg("onlyPlayers", "This command can only be used by players.")); return false; }
    private boolean require(Sender s, String permission) { if (has(s, permission)) return true; s.send(msg("noPermission", "You do not have permission.")); return false; }
    private boolean has(Sender s, String permission) { if (!s.isPlayer()) return permission != null && permission.toLowerCase(Locale.ROOT).startsWith("kwc.admin"); return s.uuid() != null && host.platformAdapter() != null && host.platformAdapter().hasPermission(s.uuid(), permission); }
    private int ok(Sender s, String text) { s.send(text); return 1; }
    private int fail(Sender s, String text) { s.send(text); return 0; }
    private int usage(Sender s, String text) { s.send(text); return 0; }
    private String msg(String key, String fallback, String... values) {
        Map<String,String> m = new LinkedHashMap<>(); for (int i=0; values != null && i+1<values.length; i+=2) m.put(values[i], values[i+1]);
        WebChatLanguage l = host.language(); if (l != null) return l.text("command." + key, fallback, m);
        String out = fallback == null ? "" : fallback; for (Map.Entry<String,String> e : m.entrySet()) out = out.replace("{"+e.getKey()+"}", String.valueOf(e.getValue())); return out;
    }
    private static int parseLimit(String s, int fallback) { try { return Integer.parseInt(clean(s)); } catch (Exception ignored) { return fallback; } }
    private static int clamp(int n, int lo, int hi) { return Math.max(lo, Math.min(hi, n)); }
    private static int ceilPages(int total, int size) { return (int)Math.ceil(Math.max(0,total)/(double)Math.max(1,size)); }
    private static String key(String s) { return clean(s).toLowerCase(Locale.ROOT); }
    private static String clean(String s) { return String.valueOf(s == null ? "" : s).trim(); }
    private static String shortId(String id) { String s=clean(id); return s.length()<=8?s:s.substring(0,8); }
    private static String stripMessage(String raw, int max) { String s=String.valueOf(raw==null?"":raw).replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim(); return max>0&&s.length()>max?s.substring(0,max):s; }
    private static String trimOneLine(String s, int max) { String v=String.valueOf(s==null?"":s).replace('\n',' ').replace('\r',' ').trim(); return max>0&&v.length()>max?v.substring(0,Math.max(0,max-1))+"…":v; }
    private static String dmThreadLabel(DirectMessageThread t) { return identityLabel(t.otherDisplayName,t.otherUsername,t.otherUuid); }
    private static String identityLabel(String display, String username, String uuid) { PlayerIdentity p=new PlayerIdentity(uuid,username,display); String l=p.label(); return l==null||l.isBlank()?clean(uuid):l; }
}
