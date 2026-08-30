package dev.kokoto.webchat;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KwcCommand implements CommandExecutor, TabCompleter {
    private final KokotoWebChatPlugin plugin;
    private final Map<String, DmReadCursor> dmReadCursors = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, DmListCursor> dmListCursors = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, GroupReadCursor> groupReadCursors = new java.util.concurrent.ConcurrentHashMap<>();

    private static class DmListCursor {
        int page = 1;
        int pageSize = 10;
    }

    private static class DmReadCursor {
        String otherUuid;
        String otherUsername;
        String otherDisplayName;
        String inputName;
        int page = 1;
        int pageSize = 20;
    }

    private static class GroupReadCursor {
        String roomId;
        String roomName;
        int pageSize = 20;
        long beforeId = 0L;
        final Deque<Long> newerBeforeIds = new ArrayDeque<>();
    }

    public KwcCommand(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        ConfigValues config = plugin.configValues();
        if (config != null && !config.pluginEnabled && !"reload".equals(sub) && !"status".equals(sub)) {
            sender.sendMessage(red(msg("disabledByConfig", "KOKOTO WebChat is disabled in config.yml.")));
            if (PermissionCompat.has(sender, "kwc.admin")) {
                sender.sendMessage(yellow("/kchat reload") + ChatColor.GRAY + " - " + msg("helpReload", "Reload configuration"));
            }
            return true;
        }

        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (sub) {
            case "auth":
                return auth(sender, args);
            case "password":
                return password(sender, args);
            case "reload":
                return reload(sender);
            case "status":
                return status(sender);
            case "admin":
                return admin(sender, args);
            case "guest":
                return guest(sender, args);
            case "dm":
                return dm(sender, args);
            case "reply":
                return reply(sender, args);
            case "group":
            case "gc":
                return group(sender, args);
            case "sessions":
                return sessions(sender);
            case "revoke":
                return revoke(sender, args);
            default:
                help(sender);
                return true;
        }
    }

    private boolean reply(CommandSender sender, String[] args) {
        ConfigValues config = plugin.configValues();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(red(msg("onlyPlayers", "This command can only be used by players.")));
            return true;
        }
        if (!PermissionCompat.has(sender, "kwc.reply")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        if (config == null || !config.replyGameClickEnabled) {
            sender.sendMessage(red(msg("replyDisabled", "Game replies are disabled.")));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(yellow("/kchat reply <messageId> <message>"));
            return true;
        }

        String playerInputCommand = directMessageCommandFromPlayerInput(player, args);
        String body = replyMessageBodyFromOriginalCommand(playerInputCommand, args);
        if (body == null) {
            sender.sendMessage(red(msg("replyDirectInputRequired", "Replies must be typed directly by the player.")));
            return true;
        }
        int replyMaxLength = config.maxUrlMessageLength > 0 ? config.maxUrlMessageLength : config.maxMessageLength;
        body = stripForDm(body, replyMaxLength);
        if (body.isBlank()) {
            sender.sendMessage(red(msg("replyEmpty", "Reply message is empty.")));
            return true;
        }

        // The raw command body is the canonical web/relay text. The Bukkit args can
        // already contain ImageEmojis' generated glyphs, which are preferable for the
        // immediate Minecraft echo because plugin-created interactive components are
        // not passed back through the original chat event renderer.
        String gameDisplayBody = stripForDm(commandBodyFromArguments(args, 2), replyMaxLength);
        if (gameDisplayBody.isBlank()) gameDisplayBody = body;

        String targetId = String.valueOf(args[1] == null ? "" : args[1]).trim();
        if (targetId.regionMatches(true, 0, "dm-", 0, 3)) {
            return replyDm(sender, player, targetId.substring(3), body, gameDisplayBody);
        }
        if (targetId.regionMatches(true, 0, "group-", 0, 6)) {
            return replyGroup(sender, player, targetId.substring(6), body, gameDisplayBody);
        }

        WebChatServer server = plugin.webServer();
        ChatMessage created = server == null ? null : server.publishReplyFromGame(
                plugin.displayPlayerName(player), player.getName(), player.getUniqueId().toString(),
                args[1], body, gameDisplayBody);
        if (created == null) {
            sender.sendMessage(red(msg("replyTargetNotFound", "The referenced message could not be found.")));
            return true;
        }
        return true;
    }

    private boolean auth(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(red(msg("onlyPlayers", "This command can only be used by players.")));
            return true;
        }
        if (!PermissionCompat.has(sender, "kwc.auth")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(yellow("/kchat auth <code>"));
            return true;
        }
        Player authPlayer = (Player) sender;
        PlatformPlayer platformPlayer = new PlatformPlayer(authPlayer.getUniqueId(), authPlayer.getName(),
                plugin.displayPlayerName(authPlayer), authPlayer.isOp());
        AuthManager.LinkResult result = plugin.authManager().completeCode(args[1], platformPlayer);
        sender.sendMessage((result.ok ? green("") : red("")) + result.message);
        return true;
    }

    private boolean password(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(red(msg("onlyPlayers", "This command can only be used by players.")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(yellow("/kchat password <newPassword>"));
            return true;
        }
        Player p = (Player) sender;
        Account a = plugin.storage().upsertLinkedAccount(
                p.getUniqueId().toString(),
                p.getName(),
                p.hasPermission(plugin.configValues().adminPermission) ? Role.ADMIN : Role.USER,
                plugin.displayPlayerName(p)
        );
        plugin.storage().setPassword(a, args[1]);
        sender.sendMessage(green(msg("passwordSet", "Web chat password has been set.")));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!PermissionCompat.has(sender, "kwc.admin")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        AuditLogger.log(plugin, "command.reload", sender.getName(), Map.of());
        if (!plugin.reloadPlugin()) {
            sender.sendMessage(red(msg("configReloadFailed",
                    "KOKOTO WebChat configuration reload failed: {error}",
                    "error", plugin.lastConfigReloadError())));
            return true;
        }
        sender.sendMessage(green(msg("configReloaded", "KOKOTO WebChat configuration reloaded.")));
        sendTransportSecurityWarnings(sender);
        return true;
    }

    private void sendTransportSecurityWarnings(CommandSender sender) {
        List<String> warnings = plugin.currentTransportSecurityWarnings();
        if (warnings == null || warnings.isEmpty()) return;

        for (String warning : warnings) {
            for (String line : TransportSecurityWarnings.splitForCommandOutput(warning)) {
                if (!line.isBlank()) sender.sendMessage(yellow(line));
            }
        }
    }

    private boolean status(CommandSender sender) {
        ConfigValues c = plugin.configValues();
        if (c == null) {
            sender.sendMessage(red("KOKOTO WebChat has not loaded a configuration yet."));
            return true;
        }
        String standalone = c.standaloneWebEnabled ? (c.standaloneWebPath == null ? "/" : c.standaloneWebPath) : "disabled";
        sender.sendMessage(ChatColor.AQUA + "KOKOTO WebChat " + plugin.getDescription().getVersion()
                + ChatColor.GRAY + " | enabled=" + c.pluginEnabled
                + " | HTTP=" + c.httpHost + ":" + c.httpPort
                + " | standalone=" + standalone);
        return true;
    }

    private boolean guest(CommandSender sender, String[] args) {
        if (!PermissionCompat.has(sender, "kwc.admin")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(yellow("/kchat guest mute <guest|ip> <value> [minutes] [reason]"));
            sender.sendMessage(yellow("/kchat guest unmute <guest|ip> <value>"));
            sender.sendMessage(yellow("/kchat guest list"));
            return true;
        }

        String sub = args[1].toLowerCase();
        if ("list".equals(sub)) {
            List<ModerationEntry> list = plugin.moderationManager().list();
            if (list.isEmpty()) {
                sender.sendMessage(green(msg("guestNoMutes", "There are no guest/IP mutes.")));
                return true;
            }
            sender.sendMessage(ChatColor.AQUA + msg("guestMutesTitle", "KOKOTO WebChat mutes:"));
            for (ModerationEntry e : list) {
                sender.sendMessage(yellow(msg("guestMuteEntry", "- {type}:{value} reason={reason} expires={expires}",
                        "type", e.type,
                        "value", e.value,
                        "reason", e.reason,
                        "expires", e.expiresAt == 0 ? msg("never", "never") : Long.toString(e.expiresAt))));
            }
            return true;
        }

        if ("mute".equals(sub)) {
            if (args.length < 4) {
                sender.sendMessage(yellow("/kchat guest mute <guest|ip> <value> [minutes] [reason]"));
                return true;
            }
            String type = args[2];
            String value = args[3];
            long minutes = plugin.configValues().defaultMuteMinutes;
            if (args.length >= 5) {
                try {
                    minutes = Long.parseLong(args[4]);
                } catch (NumberFormatException ignored) {
                    minutes = plugin.configValues().defaultMuteMinutes;
                }
            }
            String reason = args.length >= 6 ? String.join(" ", java.util.Arrays.copyOfRange(args, 5, args.length)) : "";
            plugin.moderationManager().mute(type, value, minutes, reason, sender.getName());
            AuditLogger.log(plugin, "command.guest-mute", sender.getName(), Map.of("type", type, "value", value, "minutes", minutes, "reason", reason));
            sender.sendMessage(green(msg("guestMuteAdded", "Mute added: {type}:{value} ({minutes} min)",
                    "type", type,
                    "value", value,
                    "minutes", Long.toString(minutes))));
            return true;
        }

        if ("unmute".equals(sub)) {
            if (args.length < 4) {
                sender.sendMessage(yellow("/kchat guest unmute <guest|ip> <value>"));
                return true;
            }
            boolean ok = plugin.moderationManager().unmute(args[2], args[3]);
            if (ok) AuditLogger.log(plugin, "command.guest-unmute", sender.getName(), Map.of("type", args[2], "value", args[3]));
            sender.sendMessage(ok ? green(msg("guestMuteRemoved", "Mute removed.")) : red(msg("guestMuteNotFound", "Mute not found.")));
            return true;
        }

        sender.sendMessage(red(msg("guestUnknownSubcommand", "Unknown guest subcommand.")));
        return true;
    }



    private boolean dm(CommandSender sender, String[] args) {
        ConfigValues config = plugin.configValues();
        if (config == null || !config.directMessageEnabled || plugin.directMessages() == null || !plugin.directMessages().available()) {
            sender.sendMessage(red(msg("dmDisabled", "Direct messages are disabled.")));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(red(msg("onlyPlayers", "This command can only be used by players.")));
            return true;
        }
        if (!PermissionCompat.has(sender, "kwc.dm")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        Player player = (Player) sender;
        String senderUuid = player.getUniqueId().toString();
        plugin.storage().updateLastDisplayName(senderUuid, player.getName(), plugin.displayPlayerName(player));
        String playerInputCommand = directMessageCommandFromPlayerInput(player, args);
        if (playerInputCommand == null) {
            sender.sendMessage(red(msg("dmDirectInputRequired", "Direct messages must be typed directly by the player.")));
            return true;
        }

        if (args.length < 2) {
            DmListCursor cursor = new DmListCursor();
            cursor.page = 1;
            cursor.pageSize = 10;
            dmListCursors.put(senderUuid.toLowerCase(java.util.Locale.ROOT), cursor);
            return dmListShow(sender, senderUuid, cursor);
        }

        if ("unread".equalsIgnoreCase(args[1]) || "list".equalsIgnoreCase(args[1])) {
            if (args.length >= 3 && ("next".equalsIgnoreCase(args[2]) || "prev".equalsIgnoreCase(args[2]) || "previous".equalsIgnoreCase(args[2]))) {
                return dmListMove(sender, senderUuid, args[2].toLowerCase(java.util.Locale.ROOT).startsWith("p") ? -1 : 1);
            }
            DmListCursor cursor = new DmListCursor();
            cursor.page = 1;
            cursor.pageSize = 10;
            if (args.length >= 3) {
                try { cursor.pageSize = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            }
            cursor.pageSize = Math.max(1, Math.min(100, cursor.pageSize));
            dmListCursors.put(senderUuid.toLowerCase(java.util.Locale.ROOT), cursor);
            return dmListShow(sender, senderUuid, cursor);
        }

        if ("read".equalsIgnoreCase(args[1]) || "view".equalsIgnoreCase(args[1])) {
            return dmRead(sender, senderUuid, args);
        }

        if ("next".equalsIgnoreCase(args[1]) || "prev".equalsIgnoreCase(args[1]) || "previous".equalsIgnoreCase(args[1])) {
            return dmReadMove(sender, senderUuid, args[1].toLowerCase(java.util.Locale.ROOT).startsWith("p") ? -1 : 1);
        }

        if ("hide".equalsIgnoreCase(args[1]) || "delete".equalsIgnoreCase(args[1])) {
            return dmHide(sender, senderUuid, args);
        }

        if (!config.directMessageAllowGameSend) {
            sender.sendMessage(red(msg("dmGameSendDisabled", "Sending direct messages from game is disabled.")));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(yellow("/kchat dm <player> <message>"));
            return true;
        }
        PlayerIdentity target = findDirectMessageTarget(args[1]);
        if (target == null || target.uuid == null || target.uuid.isBlank()) {
            sender.sendMessage(red(msg("dmPlayerNotFound", "Player not found. The player must have joined at least once.")));
            return true;
        }
        RemotePlayerRef remoteTarget = RemotePlayerRef.parse(target.uuid);
        ServerRelay relay = plugin.serverRelay();
        if (remoteTarget != null && (relay == null || !relay.canRouteDirectMessage(remoteTarget.serverId))) {
            sender.sendMessage(red(msg("dmRemoteServerUnavailable", "The target server is unavailable.")));
            return true;
        }
        String message = directMessageBodyFromOriginalCommand(playerInputCommand, args);
        if (message == null) {
            sender.sendMessage(red(msg("dmDirectInputRequired", "Direct messages must be typed directly by the player.")));
            return true;
        }
        return sendDirectMessage(sender, player, senderUuid, target, message, null);
    }

    private boolean replyDm(CommandSender sender, Player player, String rawId, String body, String gameDisplayBody) {
        ConfigValues config = plugin.configValues();
        if (config == null || !config.directMessageEnabled || !config.directMessageAllowGameSend
                || plugin.directMessages() == null || !plugin.directMessages().available()
                || !PermissionCompat.has(sender, "kwc.dm")) {
            sender.sendMessage(red(msg("replyTargetNotFound", "The referenced message could not be found.")));
            return true;
        }
        long messageId = parsePrivateReplyId(rawId);
        String senderUuid = player.getUniqueId().toString();
        DirectMessageMessage original = messageId <= 0L ? null : plugin.directMessages().messageForUser(senderUuid, messageId);
        if (original == null) {
            sender.sendMessage(red(msg("replyTargetNotFound", "The referenced message could not be found.")));
            return true;
        }
        String otherUuid = plugin.directMessages().otherParticipantUuid(original.threadId, senderUuid);
        PlayerIdentity target = plugin.storage().findKnownPlayerByUuid(otherUuid);
        if (target == null) target = new PlayerIdentity(otherUuid, "", "");
        return sendDirectMessage(sender, player, senderUuid, target, body,
                stripForDm(gameDisplayBody, config.directMessageMaxMessageLength), original.id);
    }

    private boolean replyGroup(CommandSender sender, Player player, String rawId, String body, String gameDisplayBody) {
        ConfigValues config = plugin.configValues();
        if (config == null || !config.groupChatEnabled || plugin.groupChats() == null
                || !PermissionCompat.has(sender, "kwc.group")) {
            sender.sendMessage(red(msg("replyTargetNotFound", "The referenced message could not be found.")));
            return true;
        }
        long messageId = parsePrivateReplyId(rawId);
        String senderUuid = player.getUniqueId().toString();
        GroupMessage original = messageId <= 0L ? null : plugin.groupChats().messageForUser(senderUuid, messageId);
        if (original == null) {
            sender.sendMessage(red(msg("replyTargetNotFound", "The referenced message could not be found.")));
            return true;
        }
        GroupRoom room = findGroupRoomForCommand(senderUuid, original.roomId);
        if (room == null) {
            sender.sendMessage(red(msg("replyTargetNotFound", "The referenced message could not be found.")));
            return true;
        }
        return groupSend(sender, player, senderUuid, room, body,
                stripForDm(gameDisplayBody, config.groupChatMaxMessageLength), original.id);
    }

    private boolean sendDirectMessage(CommandSender sender, Player player, String senderUuid, PlayerIdentity target,
                                      String rawMessage, String gameDisplayOverride) {
        return sendDirectMessage(sender, player, senderUuid, target, rawMessage, gameDisplayOverride, 0L);
    }

    private boolean sendDirectMessage(CommandSender sender, Player player, String senderUuid, PlayerIdentity target,
                                      String rawMessage, String gameDisplayOverride, long replyToId) {
        ConfigValues config = plugin.configValues();
        if (target == null || target.uuid == null || target.uuid.isBlank()) {
            sender.sendMessage(red(msg("dmPlayerNotFound", "Player not found. The player must have joined at least once.")));
            return true;
        }
        RemotePlayerRef remoteTarget = RemotePlayerRef.parse(target.uuid);
        ServerRelay relay = plugin.serverRelay();
        if (remoteTarget != null && (relay == null || !relay.canRouteDirectMessage(remoteTarget.serverId))) {
            sender.sendMessage(red(msg("dmRemoteServerUnavailable", "The target server is unavailable.")));
            return true;
        }
        String rawDmMessage = stripForDm(rawMessage, config.directMessageMaxMessageLength);
        String message = plugin.applyMessageTokens(rawDmMessage);
        String gameMessage = gameDisplayOverride == null || gameDisplayOverride.isBlank()
                ? plugin.applyMessageTokensForGame(rawDmMessage)
                : gameDisplayOverride;
        if (message.isBlank()) {
            sender.sendMessage(red(msg("dmEmpty", "Message is empty.")));
            return true;
        }
        String relayId = remoteTarget == null ? "" : relay.createDirectMessageRelayId();
        DirectMessageStore.SendResult result = remoteTarget == null
                ? plugin.directMessages().send(senderUuid, target.uuid, message, replyToId)
                : plugin.directMessages().sendPendingRemote(senderUuid, target.uuid, message, relayId, remoteTarget.serverId, "", replyToId);
        if (!result.ok) {
            sender.sendMessage(red(msg("dmFailed", "Direct message failed: {error}", "error", result.error)));
            return true;
        }
        WebChatServer server = plugin.webServer();
        String senderDisplayName = plugin.displayPlayerName(player);
        String threadId = result.thread == null ? "" : result.thread.id;
        long messageId = result.message == null ? 0L : result.message.id;
        if (server != null) {
            server.publishDirectMessageUpdate(senderUuid, target.uuid, threadId);
            server.dispatchWebPushDirectMessage(senderUuid, senderDisplayName, target.uuid, target.label(), threadId, messageId, message);
        }
        if (remoteTarget != null) {
            final String finalTargetUuid = target.uuid;
            relay.publishDirectMessage(
                            relayId,
                            senderUuid, player.getName(), senderDisplayName,
                            remoteTarget.serverId, remoteTarget.playerUuid,
                            target.username == null ? "" : target.username,
                            target.displayName == null ? "" : target.displayName,
                            message, gameMessage,
                            result.message == null ? "" : result.message.replyToRelayId,
                            result.message == null ? "" : result.message.replyToSender,
                            result.message == null ? "" : result.message.replyToPreview)
                    .whenComplete((delivery, error) -> {
                        boolean delivered = error == null && delivery != null && delivery.delivered;
                        String errorCode = delivered ? "" : (delivery == null ? "dm_transport_error" : delivery.error);
                        plugin.directMessages().updateDeliveryStatus(messageId, delivered ? "delivered" : "failed", errorCode);
                        WebChatServer currentServer = plugin.webServer();
                        if (currentServer != null) currentServer.publishDirectMessageUpdate(senderUuid, finalTargetUuid, threadId);
                        if (!delivered) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                Player online = plugin.getServer().getPlayer(player.getUniqueId());
                                if (online != null) {
                                    online.sendMessage(red(msg("dmDeliveryFailed", "Direct message delivery failed. You can retry it from web chat.", "error", errorCode)));
                                }
                            });
                        }
                    });
        } else {
            notifyOnlineRecipient(player, target, gameMessage, result.message);
        }
        sendPrivateInteractiveWithReply(sender,
                sentEchoLineForGame(player, target.label(), gameMessage),
                target.label(), msg("dmClickHint", "Click to write a DM to {player}", "player", target.label()),
                "/kchat dm " + dmCommandTarget(target) + " ",
                colorizeForGame(trim(gameMessage, 100)), msg("privateReplyClickHint", "Click the message to reply"),
                "/kchat reply dm-" + messageId + " ",
                result.message == null ? 0L : result.message.replyToId,
                result.message == null ? "" : result.message.replyToRelayId,
                result.message == null ? "" : result.message.replyToSender,
                result.message == null ? "" : result.message.replyToPreview);
        return true;
    }

    private static long parsePrivateReplyId(String raw) {
        try { return Long.parseLong(String.valueOf(raw == null ? "" : raw).trim()); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private PlayerIdentity findDirectMessageTarget(String rawInput) {
        String input = String.valueOf(rawInput == null ? "" : rawInput).trim();
        if (input.isBlank()) return null;

        RemotePlayerRef direct = RemotePlayerRef.parse(input);
        if (direct != null) return plugin.storage().findKnownPlayerByUuid(direct.key);

        int at = input.lastIndexOf('@');
        if (at > 0 && at < input.length() - 1) {
            String name = input.substring(0, at).trim();
            String serverId = RemotePlayerRef.normalizeServerId(input.substring(at + 1));
            if (!name.isBlank() && !serverId.isBlank()) {
                String localServerId = "";
                ServerRelay relay = plugin.serverRelay();
                if (relay != null) localServerId = RemotePlayerRef.normalizeServerId(relay.serverId());
                if (localServerId.isBlank() && plugin.configValues() != null) {
                    localServerId = RemotePlayerRef.normalizeServerId(plugin.configValues().serverRelayServerId);
                }
                if (!localServerId.isBlank() && localServerId.equals(serverId)) {
                    return plugin.storage().findKnownLocalPlayer(name);
                }
                for (PlayerIdentity candidate : plugin.storage().listKnownPlayers(name, 200)) {
                    RemotePlayerRef remote = RemotePlayerRef.parse(candidate == null ? "" : candidate.uuid);
                    if (remote == null || !serverId.equals(remote.serverId)) continue;
                    String username = String.valueOf(candidate.username == null ? "" : candidate.username).trim();
                    String displayName = String.valueOf(candidate.displayName == null ? "" : candidate.displayName).trim();
                    String baseDisplayName = RemotePlayerRef.baseDisplayName(displayName, username, candidate.remoteServerName(), serverId);
                    if (username.equalsIgnoreCase(name) || baseDisplayName.equalsIgnoreCase(name)
                            || candidate.label().equalsIgnoreCase(name)) return candidate;
                }
                return null;
            }
        }
        // An unqualified name always means this server. Cross-server DM targets
        // must be explicitly scoped as name@server-id (or carry a scoped key).
        return plugin.storage().findKnownLocalPlayer(input);
    }


    private boolean group(CommandSender sender, String[] args) {
        ConfigValues config = plugin.configValues();
        if (config == null || !config.groupChatEnabled || plugin.groupChats() == null || !plugin.groupChats().available()) {
            sender.sendMessage(red(msg("groupDisabled", "Group chats are disabled.")));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(red(msg("onlyPlayers", "This command can only be used by players.")));
            return true;
        }
        if (!PermissionCompat.has(sender, "kwc.group")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        Player player = (Player) sender;
        String senderUuid = player.getUniqueId().toString();
        plugin.storage().updateLastDisplayName(senderUuid, player.getName(), plugin.displayPlayerName(player));
        String playerInputCommand = directMessageCommandFromPlayerInput(player, args);
        if (playerInputCommand == null) {
            sender.sendMessage(red(msg("groupDirectInputRequired", "Group messages must be typed directly by the player.")));
            return true;
        }
        String originalGroupTail = groupCommandTailFromOriginal(playerInputCommand);

        if (args.length < 2 || "list".equalsIgnoreCase(args[1]) || "rooms".equalsIgnoreCase(args[1])) {
            return groupListShow(sender, senderUuid);
        }
        if ("read".equalsIgnoreCase(args[1]) || "view".equalsIgnoreCase(args[1])) {
            return groupRead(sender, senderUuid, stripLeadingWord(originalGroupTail, args[1]));
        }
        if ("next".equalsIgnoreCase(args[1]) || "prev".equalsIgnoreCase(args[1]) || "previous".equalsIgnoreCase(args[1])) {
            return groupReadMove(sender, senderUuid, args[1].toLowerCase(java.util.Locale.ROOT).startsWith("p") ? -1 : 1);
        }
        if ("send".equalsIgnoreCase(args[1])) {
            GroupCommandTargetResolver.Result target = resolveGroupCommandTarget(senderUuid, stripLeadingWord(originalGroupTail, args[1]));
            if (target == null || target.room == null || target.room.id == null || target.room.id.isBlank()) {
                sender.sendMessage(red(msg("groupRoomNotFound", "Group chat room not found or you are not a member.")));
                return true;
            }
            if (target.remainder.isBlank()) {
                sender.sendMessage(yellow("/kchat group send <room> <message>"));
                return true;
            }
            return groupSend(sender, player, senderUuid, target.room, target.remainder);
        }

        GroupCommandTargetResolver.Result target = resolveGroupCommandTarget(senderUuid, originalGroupTail);
        if (target == null || target.room == null || target.room.id == null || target.room.id.isBlank()) {
            sender.sendMessage(red(msg("groupRoomNotFound", "Group chat room not found or you are not a member.")));
            return true;
        }
        if (target.remainder.isBlank()) {
            sender.sendMessage(yellow("/kchat group <room> <message>"));
            sender.sendMessage(yellow("/kchat group list"));
            sender.sendMessage(yellow("/kchat group read <room> [pageSize]"));
            return true;
        }
        return groupSend(sender, player, senderUuid, target.room, target.remainder);
    }

    private boolean groupSend(CommandSender sender, Player player, String senderUuid, GroupRoom room, String message) {
        return groupSend(sender, player, senderUuid, room, message, null);
    }

    private boolean groupSend(CommandSender sender, Player player, String senderUuid, GroupRoom room, String message, String gameDisplayOverride) {
        return groupSend(sender, player, senderUuid, room, message, gameDisplayOverride, 0L);
    }

    private boolean groupSend(CommandSender sender, Player player, String senderUuid, GroupRoom room, String message, String gameDisplayOverride, long replyToId) {
        ConfigValues config = plugin.configValues();
        if (message == null) {
            sender.sendMessage(red(msg("groupDirectInputRequired", "Group messages must be typed directly by the player.")));
            return true;
        }
        String rawGroupMessage = stripForDm(message, config == null ? 500 : config.groupChatMaxMessageLength);
        message = plugin.applyMessageTokens(rawGroupMessage);
        String gameMessage = gameDisplayOverride == null || gameDisplayOverride.isBlank()
                ? plugin.applyMessageTokensForGame(rawGroupMessage)
                : gameDisplayOverride;
        if (message.isBlank()) {
            sender.sendMessage(red(msg("groupEmpty", "Message is empty.")));
            return true;
        }
        GroupChatStore.SendResult result = plugin.groupChats().send(senderUuid, room.id, message, replyToId);
        if (!result.ok) {
            sender.sendMessage(red(msg("groupFailed", "Group chat failed: {error}", "error", result.error)));
            return true;
        }
        WebChatServer server = plugin.webServer();
        if (server != null) {
            server.publishGroupChatUpdate(room.id);
            server.dispatchWebPushGroupMessage(senderUuid, plugin.displayPlayerName(player), room, result.message, room.id);
        }
        sendPrivateInteractiveWithReply(sender,
                ChatColor.GRAY + colorizeForGame(msg("groupSentEcho", "to group {room}: {message}",
                        "room", ChatColor.RESET + room.name + ChatColor.GRAY,
                        "message", ChatColor.RESET + gameMessage)),
                room.name, msg("groupClickHint", "Click to write to group {room}", "room", room.name),
                "/kchat group " + shortRoomId(room.id) + " ",
                colorizeForGame(trim(gameMessage, 100)), msg("privateReplyClickHint", "Click the message to reply"),
                "/kchat reply group-" + (result.message == null ? 0L : result.message.id) + " ",
                result.message == null ? 0L : result.message.replyToId, "",
                result.message == null ? "" : result.message.replyToSender,
                result.message == null ? "" : result.message.replyToPreview);
        notifyOnlineGroupMembers(player, room.id, room.name, gameMessage, result.message);
        return true;
    }

    private boolean groupListShow(CommandSender sender, String senderUuid) {
        List<GroupRoom> rooms = plugin.groupChats().listRooms(senderUuid, 100);
        List<GroupRoom> joined = new ArrayList<>();
        for (GroupRoom room : rooms) if (room != null && room.member) joined.add(room);
        sender.sendMessage(ChatColor.AQUA + msg("groupTitle", "Group chats: {count}", "count", Integer.toString(joined.size())));
        if (joined.isEmpty()) {
            sender.sendMessage(yellow(msg("groupNoRooms", "No joined group chats.")));
            return true;
        }
        for (GroupRoom room : joined) {
            String unreadText = room.unread > 0 ? " (" + room.unread + ")" : "";
            String last = transformForCommandDisplay((sender instanceof Player) ? (Player) sender : null, room.lastMessage, 60);
            sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.RESET + room.name + ChatColor.GRAY + unreadText
                    + ChatColor.DARK_GRAY + " [" + shortRoomId(room.id) + "]: " + ChatColor.RESET + last);
        }
        sender.sendMessage(yellow("/kchat group <room|id> <message>"));
        sender.sendMessage(yellow("/kchat group read <room|id> [pageSize]"));
        return true;
    }

    private boolean groupRead(CommandSender sender, String senderUuid, String targetText) {
        GroupCommandTargetResolver.Result target = resolveGroupCommandTarget(senderUuid, targetText);
        if (target == null || target.room == null || target.room.id == null || target.room.id.isBlank()) {
            sender.sendMessage(red(msg("groupRoomNotFound", "Group chat room not found or you are not a member.")));
            return true;
        }
        GroupRoom room = target.room;
        GroupReadCursor cursor = new GroupReadCursor();
        cursor.roomId = room.id;
        cursor.roomName = room.name;
        cursor.pageSize = 20;
        if (!target.remainder.isBlank()) {
            String page = target.remainder.split("\\s+", 2)[0];
            try { cursor.pageSize = Integer.parseInt(page); } catch (NumberFormatException ignored) {}
        }
        cursor.pageSize = Math.max(1, Math.min(100, cursor.pageSize));
        cursor.beforeId = 0L;
        cursor.newerBeforeIds.clear();
        groupReadCursors.put(senderUuid.toLowerCase(java.util.Locale.ROOT), cursor);
        return groupReadShow(sender, senderUuid, cursor, 0L);
    }

    private boolean groupReadMove(CommandSender sender, String senderUuid, int direction) {
        GroupReadCursor cursor = groupReadCursors.get(senderUuid.toLowerCase(java.util.Locale.ROOT));
        if (cursor == null || cursor.roomId == null || cursor.roomId.isBlank()) {
            sender.sendMessage(yellow(msg("groupReadNoCursor", "Open a group chat first: /kchat group read <room>")));
            return true;
        }
        return groupReadShow(sender, senderUuid, cursor, direction);
    }

    private boolean groupReadShow(CommandSender sender, String senderUuid, GroupReadCursor cursor, long direction) {
        int pageSize = Math.max(1, Math.min(100, cursor.pageSize <= 0 ? 20 : cursor.pageSize));
        if (direction < 0L) {
            if (cursor.newerBeforeIds.isEmpty()) {
                sender.sendMessage(yellow(msg("groupReadFirstPage", "You are already on the newest group page.")));
                return true;
            }
            cursor.beforeId = cursor.newerBeforeIds.pop();
        } else if (direction > 0L) {
            List<GroupMessage> current = plugin.groupChats().listMessages(senderUuid, cursor.roomId, cursor.beforeId, pageSize);
            if (current.isEmpty()) {
                sender.sendMessage(yellow(msg("groupReadLastPage", "You are already on the oldest group page.")));
                return true;
            }
            long nextBefore = current.get(0).id;
            List<GroupMessage> older = plugin.groupChats().listMessages(senderUuid, cursor.roomId, nextBefore, pageSize);
            if (older.isEmpty()) {
                sender.sendMessage(yellow(msg("groupReadLastPage", "You are already on the oldest group page.")));
                return true;
            }
            cursor.newerBeforeIds.push(cursor.beforeId);
            cursor.beforeId = nextBefore;
        }

        long readBefore = plugin.groupChats().readPosition(cursor.roomId, senderUuid);
        List<GroupMessage> messages = plugin.groupChats().listMessages(senderUuid, cursor.roomId, cursor.beforeId, pageSize);
        if (cursor.beforeId == 0L) {
            boolean markedRead = plugin.groupChats().markRead(cursor.roomId, senderUuid);
            long readAfter = plugin.groupChats().readPosition(cursor.roomId, senderUuid);
            if (markedRead && readAfter > readBefore) {
                WebChatServer currentServer = plugin.webServer();
                if (currentServer != null) currentServer.publishGroupChatUpdate(cursor.roomId);
            }
        }
        sender.sendMessage(ChatColor.AQUA + msg("groupReadTitle", "Group chat {room}:",
                "room", ChatColor.RESET + cursor.roomName + ChatColor.AQUA));
        if (messages.isEmpty()) {
            sender.sendMessage(yellow(msg("groupNoMessages", "No messages in this group chat.")));
            return true;
        }
        for (GroupMessage message : messages) {
            String senderName = message.senderUuid != null && message.senderUuid.equalsIgnoreCase(senderUuid)
                    ? msg("dmMe", "me")
                    : formatCommandPlayer(message.senderDisplayName, message.senderUsername, message.senderUuid);
            if ("member_join".equals(message.eventType) || "member_leave".equals(message.eventType)) {
                String key = "member_leave".equals(message.eventType) ? "groupMemberLeft" : "groupMemberJoined";
                String fallback = "member_leave".equals(message.eventType) ? "{player} left group {room}." : "{player} joined group {room}.";
                sender.sendMessage(ChatColor.DARK_GRAY + "#" + message.id + " " + ChatColor.GRAY
                        + msg(key, fallback, "player", senderName, "room", cursor.roomName));
                continue;
            }
            String body = transformForCommandDisplay((sender instanceof Player) ? (Player) sender : null, message.body, 0);
            sendPrivateInteractiveWithReply(sender,
                    ChatColor.DARK_GRAY + "#" + message.id + " " + ChatColor.RESET + senderName
                            + ChatColor.DARK_GRAY + ": " + ChatColor.RESET + body,
                    senderName, msg("groupClickHint", "Click to write to group {room}", "room", cursor.roomName),
                    "/kchat group " + shortRoomId(cursor.roomId) + " ",
                    body, msg("privateReplyClickHint", "Click the message to reply"),
                    "/kchat reply group-" + message.id + " ",
                    message.replyToId, "", message.replyToSender, message.replyToPreview);
        }
        sender.sendMessage(ChatColor.GRAY + msg("groupReadNavHint", "Use /kchat group prev for newer messages, /kchat group next for older messages."));
        sender.sendMessage(ChatColor.GRAY + msg("groupReadHint", "Send: /kchat group {room} <message>", "room", cursor.roomName));
        return true;
    }

    private GroupRoom findGroupRoomForCommand(String userUuid, String input) {
        String needle = String.valueOf(input == null ? "" : input).trim();
        if (needle.isBlank()) return null;
        List<GroupRoom> rooms = plugin.groupChats().listRooms(userUuid, 200);
        for (GroupRoom room : rooms) {
            if (room != null && room.member && room.id != null && room.id.equalsIgnoreCase(needle)) return room;
        }
        for (GroupRoom room : rooms) {
            if (room != null && room.member && room.id != null && shortRoomId(room.id).equalsIgnoreCase(needle)) return room;
        }
        for (GroupRoom room : rooms) {
            if (room != null && room.member && room.name != null && room.name.equalsIgnoreCase(needle)) return room;
        }
        return null;
    }

    private GroupCommandTargetResolver.Result resolveGroupCommandTarget(String userUuid, String input) {
        return GroupCommandTargetResolver.resolve(plugin.groupChats().listRooms(userUuid, 200), input);
    }

    private String groupCommandTailFromOriginal(String original) {
        String text = String.valueOf(original == null ? "" : original).trim();
        if (text.startsWith("/")) text = text.substring(1);
        String[] parts = text.split("\\s+", 3);
        if (parts.length < 3) return "";
        if (!isKwcCommandRoot(parts[0].toLowerCase(java.util.Locale.ROOT))) return "";
        if (!("group".equalsIgnoreCase(parts[1]) || "gc".equalsIgnoreCase(parts[1]))) return "";
        return parts[2].trim();
    }

    private String stripLeadingWord(String value, String expected) {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (text.isBlank()) return "";
        String[] parts = text.split("\\s+", 2);
        if (!parts[0].equalsIgnoreCase(String.valueOf(expected == null ? "" : expected))) return text;
        return parts.length < 2 ? "" : parts[1].trim();
    }

    private String groupMessageBodyFromOriginalCommand(String original, String[] args, int firstMessageArgIndex) {
        if (original == null || original.isBlank()) return null;
        String text = original.trim();
        if (text.startsWith("/")) text = text.substring(1);
        String[] parts = text.split("\\s+", firstMessageArgIndex + 2);
        if (parts.length < firstMessageArgIndex + 2) return null;
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
        if (!isKwcCommandRoot(root) || !("group".equals(sub) || "gc".equals(sub))) return null;
        for (int i = 1; i < firstMessageArgIndex; i++) {
            if (i + 1 >= parts.length || i >= args.length || !parts[i + 1].equalsIgnoreCase(args[i])) return null;
        }
        return parts[firstMessageArgIndex + 1];
    }

    private String shortRoomId(String roomId) {
        String id = String.valueOf(roomId == null ? "" : roomId).trim();
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private String directMessageCommandFromPlayerInput(Player player, String[] args) {
        ChatListener listener = plugin.chatListener();
        String original = listener == null ? null : listener.pollOriginalDirectMessageCommand(player);
        if (!matchesCurrentDirectMessageCommand(original, args)) return null;
        return original;
    }

    private boolean matchesCurrentDirectMessageCommand(String original, String[] args) {
        if (original == null || original.isBlank() || args == null || args.length < 1) return false;
        String wanted = args[0].toLowerCase(java.util.Locale.ROOT);
        if (!("dm".equals(wanted) || "reply".equals(wanted) || "group".equals(wanted) || "gc".equals(wanted))) return false;
        String text = original.trim();
        if (text.startsWith("/")) text = text.substring(1);
        String[] parts = text.split("\\s+", 4);
        if (parts.length < 2) return false;
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
        if (!isKwcCommandRoot(root)) return false;
        if ("reply".equals(wanted)) {
            if (!"reply".equals(sub)) return false;
            if (args.length == 1) return parts.length == 2;
            if (parts.length < 3 || !parts[2].equalsIgnoreCase(args[1])) return false;
            if (args.length == 2) return parts.length == 3;
            return parts.length >= 4;
        } else if ("dm".equals(wanted)) {
            if (!"dm".equals(sub)) return false;
            if (args.length == 1) return parts.length == 2;
            if (parts.length < 3 || !parts[2].equalsIgnoreCase(args[1])) return false;

            String actionOrTarget = args[1].toLowerCase(java.util.Locale.ROOT);
            if (!isDmReservedSubcommand(actionOrTarget)) {
                if (args.length == 2) return parts.length == 3;
                return args.length >= 3 && parts.length >= 4;
            }
        } else {
            if (!("group".equals(sub) || "gc".equals(sub))) return false;
            return matchesCurrentGroupCommandFromOriginal(text, args);
        }

        String[] tokens = text.split("\\s+");
        if (tokens.length < args.length + 1) return false;
        for (int i = 0; i < args.length; i++) {
            if (!tokens[i + 1].equalsIgnoreCase(args[i])) return false;
        }
        return true;
    }

    private boolean matchesCurrentGroupCommandFromOriginal(String text, String[] args) {
        if (text == null || text.isBlank() || args == null || args.length < 1) return false;
        if (!("group".equalsIgnoreCase(args[0]) || "gc".equalsIgnoreCase(args[0]))) return false;
        String normalized = text.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        String[] head = normalized.split("\\s+", 4);
        if (head.length < 2) return false;
        if (!isKwcCommandRoot(head[0].toLowerCase(java.util.Locale.ROOT))) return false;
        if (!("group".equalsIgnoreCase(head[1]) || "gc".equalsIgnoreCase(head[1]))) return false;
        if (args.length == 1) return head.length == 2;

        String actionOrRoom = args[1];
        String action = actionOrRoom == null ? "" : actionOrRoom.toLowerCase(java.util.Locale.ROOT);
        if ("send".equals(action)) {
            String[] parts = normalized.split("\\s+", 5);
            if (parts.length < 3 || !"send".equalsIgnoreCase(parts[2])) return false;
            if (args.length < 3) return parts.length == 3;
            if (parts.length < 4 || !parts[3].equalsIgnoreCase(args[2])) return false;
            if (args.length < 4) return parts.length == 4;
            // Do not compare the message tokens themselves. Emoji plugins may replace
            // :pack/name: tokens after PlayerCommandPreprocessEvent, while we still
            // want to store the original player-typed message body.
            return parts.length >= 5;
        }

        if ("list".equals(action) || "rooms".equals(action)
                || "next".equals(action) || "prev".equals(action) || "previous".equals(action)) {
            String[] tokens = normalized.split("\\s+");
            if (tokens.length < args.length + 1) return false;
            for (int i = 0; i < args.length; i++) {
                if (!tokens[i + 1].equalsIgnoreCase(args[i])) return false;
            }
            return true;
        }

        if ("read".equals(action) || "view".equals(action)) {
            String[] parts = normalized.split("\\s+", 4);
            if (parts.length < 3 || !parts[2].equalsIgnoreCase(args[1])) return false;
            if (args.length < 3) return parts.length == 3;
            // The room name may itself span multiple Bukkit args. The captured command
            // belongs to this immediate player dispatch, so keep the full original tail
            // and let GroupCommandTargetResolver determine the room boundary.
            return parts.length >= 4;
        }

        String[] parts = normalized.split("\\s+", 4);
        if (parts.length < 3 || !parts[2].equalsIgnoreCase(args[1])) return false;
        if (args.length < 3) return parts.length == 3;
        // Room-send form: /kchat group <room> <message>. Keep only the room
        // prefix strict and allow the original body to differ from Bukkit args.
        return parts.length >= 4;
    }


    private String commandBodyFromArguments(String[] args, int firstIndex) {
        if (args == null || firstIndex < 0 || firstIndex >= args.length) return "";
        StringBuilder out = new StringBuilder();
        for (int i = firstIndex; i < args.length; i++) {
            if (i > firstIndex) out.append(' ');
            out.append(args[i] == null ? "" : args[i]);
        }
        return out.toString();
    }

    private String directMessageBodyFromOriginalCommand(String original, String[] args) {
        if (original == null || original.isBlank()) return null;
        String text = original.trim();
        if (text.startsWith("/")) text = text.substring(1);
        String[] parts = text.split("\\s+", 4);
        if (parts.length < 4) return null;
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
        if (!isKwcCommandRoot(root) || !"dm".equals(sub)) return null;
        if (args == null || args.length < 3 || !parts[2].equalsIgnoreCase(args[1])) return null;
        return parts[3];
    }

    private String replyMessageBodyFromOriginalCommand(String original, String[] args) {
        if (original == null || original.isBlank()) return null;
        String text = original.trim();
        if (text.startsWith("/")) text = text.substring(1);
        String[] parts = text.split("\\s+", 4);
        if (parts.length < 4) return null;
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
        if (!isKwcCommandRoot(root) || !"reply".equals(sub)) return null;
        if (args == null || args.length < 3 || !parts[2].equalsIgnoreCase(args[1])) return null;
        return parts[3];
    }

    private boolean isKwcCommandRoot(String root) {
        if (root == null) return false;
        return root.equals("kchat")
                || root.equals("kc")
                || root.equals("kokoto-webchat:kchat")
                || root.equals("kokoto-webchat:kc");
    }

    private boolean isDmReservedSubcommand(String value) {
        if (value == null) return false;
        return value.equals("read")
                || value.equals("view")
                || value.equals("list")
                || value.equals("unread")
                || value.equals("next")
                || value.equals("prev")
                || value.equals("previous")
                || value.equals("hide")
                || value.equals("delete");
    }


    private boolean dmListMove(CommandSender sender, String senderUuid, int delta) {
        DmListCursor cursor = dmListCursors.get(senderUuid.toLowerCase(java.util.Locale.ROOT));
        if (cursor == null) {
            cursor = new DmListCursor();
            cursor.page = 1;
            cursor.pageSize = 10;
        }
        int total = plugin.directMessages().countThreads(senderUuid);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) Math.max(1, cursor.pageSize)));
        int nextPage = Math.max(1, Math.min(totalPages, cursor.page + delta));
        if (nextPage == cursor.page) {
            sender.sendMessage(yellow(delta > 0
                    ? msg("dmListLastPage", "You are already on the oldest conversation list page.")
                    : msg("dmListFirstPage", "You are already on the newest conversation list page.")));
            return true;
        }
        cursor.page = nextPage;
        dmListCursors.put(senderUuid.toLowerCase(java.util.Locale.ROOT), cursor);
        return dmListShow(sender, senderUuid, cursor);
    }

    private boolean dmListShow(CommandSender sender, String senderUuid, DmListCursor cursor) {
        if (cursor == null) cursor = new DmListCursor();
        int pageSize = Math.max(1, Math.min(100, cursor.pageSize <= 0 ? 10 : cursor.pageSize));
        int total = plugin.directMessages().countThreads(senderUuid);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        if (cursor.page < 1) cursor.page = 1;
        if (total > 0 && cursor.page > totalPages) cursor.page = totalPages;
        int unread = plugin.directMessages().unreadCount(senderUuid);
        sender.sendMessage(ChatColor.AQUA + msg("dmTitlePage", "Direct messages: {count} unread (page {page}/{pages})",
                "count", Integer.toString(unread),
                "page", Integer.toString(cursor.page),
                "pages", Integer.toString(totalPages)));
        List<DirectMessageThread> threads = plugin.directMessages().listThreadsPage(senderUuid, cursor.page, pageSize);
        if (threads.isEmpty()) {
            sender.sendMessage(yellow(msg("dmNoThreads", "No direct message threads.")));
        }
        for (DirectMessageThread thread : threads) {
            String unreadText = thread.unread > 0 ? " (" + thread.unread + ")" : "";
            String last = transformForCommandDisplay((sender instanceof Player) ? (Player) sender : null, thread.lastMessage, 60);
            String other = formatCommandPlayer(thread.otherDisplayName, thread.otherUsername, thread.otherUuid);
            sender.sendMessage(ChatColor.DARK_GRAY + "- "
                    + ChatColor.RESET + other
                    + ChatColor.GRAY + unreadText
                    + ChatColor.DARK_GRAY + ": "
                    + ChatColor.RESET + last);
        }
        dmListCursors.put(senderUuid.toLowerCase(java.util.Locale.ROOT), cursor);
        sender.sendMessage(yellow("/kchat dm <player> <message>"));
        sender.sendMessage(yellow("/kchat dm read <player> [pageSize]"));
        sender.sendMessage(ChatColor.GRAY + msg("dmListNavHint", "Use /kchat dm list prev for newer conversations, /kchat dm list next for older conversations."));
        return true;
    }

    private boolean dmRead(CommandSender sender, String senderUuid, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(yellow("/kchat dm read <player> [pageSize]"));
            return true;
        }
        PlayerIdentity target = findDirectMessageTarget(args[2]);
        if (target == null || target.uuid == null || target.uuid.isBlank()) {
            sender.sendMessage(red(msg("dmPlayerNotFound", "Player not found. The player must have joined at least once.")));
            return true;
        }
        int pageSize = 20;
        if (args.length >= 4) {
            try { pageSize = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
        }
        pageSize = Math.max(1, Math.min(100, pageSize));
        DmReadCursor cursor = new DmReadCursor();
        cursor.otherUuid = target.uuid;
        cursor.otherUsername = target.username;
        cursor.otherDisplayName = target.displayName;
        cursor.inputName = args[2];
        cursor.page = 1;
        cursor.pageSize = pageSize;
        dmReadCursors.put(senderUuid.toLowerCase(java.util.Locale.ROOT), cursor);
        return dmReadShow(sender, senderUuid, cursor);
    }

    private boolean dmReadMove(CommandSender sender, String senderUuid, int delta) {
        DmReadCursor cursor = dmReadCursors.get(senderUuid.toLowerCase(java.util.Locale.ROOT));
        if (cursor == null || cursor.otherUuid == null || cursor.otherUuid.isBlank()) {
            sender.sendMessage(yellow(msg("dmReadNoCursor", "Open a thread first: /kchat dm read <player>")));
            return true;
        }
        int total = plugin.directMessages().countMessagesBetween(senderUuid, cursor.otherUuid);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) Math.max(1, cursor.pageSize)));
        int nextPage = Math.max(1, Math.min(totalPages, cursor.page + delta));
        if (nextPage == cursor.page) {
            sender.sendMessage(yellow(delta > 0
                    ? msg("dmReadLastPage", "You are already on the oldest page.")
                    : msg("dmReadFirstPage", "You are already on the newest page.")));
            return true;
        }
        cursor.page = nextPage;
        dmReadCursors.put(senderUuid.toLowerCase(java.util.Locale.ROOT), cursor);
        return dmReadShow(sender, senderUuid, cursor);
    }

    private boolean dmReadShow(CommandSender sender, String senderUuid, DmReadCursor cursor) {
        if (cursor == null || cursor.otherUuid == null || cursor.otherUuid.isBlank()) {
            sender.sendMessage(yellow(msg("dmReadNoCursor", "Open a thread first: /kchat dm read <player>")));
            return true;
        }
        int pageSize = Math.max(1, Math.min(100, cursor.pageSize <= 0 ? 20 : cursor.pageSize));
        int total = plugin.directMessages().countMessagesBetween(senderUuid, cursor.otherUuid);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        if (cursor.page < 1) cursor.page = 1;
        if (total > 0 && cursor.page > totalPages) cursor.page = totalPages;
        String threadId = plugin.directMessages().threadIdForParticipants(senderUuid, cursor.otherUuid);
        long readBefore = plugin.directMessages().readPosition(threadId, senderUuid);
        List<DirectMessageMessage> messages = plugin.directMessages().listMessagesBetweenPage(senderUuid, cursor.otherUuid, cursor.page, pageSize);
        long readAfter = plugin.directMessages().readPosition(threadId, senderUuid);
        if (readAfter > readBefore) {
            WebChatServer currentServer = plugin.webServer();
            if (currentServer != null) currentServer.publishDirectMessageUpdate(senderUuid, cursor.otherUuid, threadId);
        }
        // The remote receipt is safe to replay. Send it whenever the newest page is
        // read so a lost acknowledgement is repaired on the next explicit read.
        if (cursor.page == 1) {
            DirectMessageStore.RemoteReadReceipt receipt = plugin.directMessages().latestRemoteReadReceipt(threadId, senderUuid);
            ServerRelay relay = plugin.serverRelay();
            if (receipt.ok && relay != null) relay.publishDirectMessageRead(receipt.sourceServerId, receipt.relayId);
        }
        String playerLabel = formatCommandPlayer(cursor.otherDisplayName, cursor.otherUsername, cursor.otherUuid);
        sender.sendMessage(ChatColor.AQUA + msg("dmReadTitlePage", "Direct messages with {player} (page {page}/{pages}):",
                "player", ChatColor.RESET + playerLabel + ChatColor.AQUA,
                "page", Integer.toString(cursor.page),
                "pages", Integer.toString(totalPages)));
        if (messages.isEmpty()) {
            sender.sendMessage(yellow(msg("dmNoMessages", "No messages in this thread.")));
            return true;
        }
        for (DirectMessageMessage message : messages) {
            String senderName = message.senderUuid != null && message.senderUuid.equalsIgnoreCase(senderUuid)
                    ? msg("dmMe", "me")
                    : formatCommandPlayer(message.senderDisplayName, message.senderUsername, message.senderUuid);
            if (senderName == null || senderName.isBlank()) senderName = message.senderUuid;
            String body = transformForCommandDisplay((sender instanceof Player) ? (Player) sender : null, message.body, 0);
            PlayerIdentity conversationTarget = plugin.storage().findKnownPlayerByUuid(cursor.otherUuid);
            if (conversationTarget == null) conversationTarget = new PlayerIdentity(cursor.otherUuid, cursor.otherUsername, cursor.otherDisplayName);
            sendPrivateInteractiveWithReply(sender,
                    ChatColor.DARK_GRAY + "#" + message.id + " " + ChatColor.RESET + senderName
                            + ChatColor.DARK_GRAY + ": " + ChatColor.RESET + body,
                    senderName, msg("dmClickHint", "Click to write a DM to {player}", "player", playerLabel),
                    "/kchat dm " + dmCommandTarget(conversationTarget) + " ",
                    body, msg("privateReplyClickHint", "Click the message to reply"),
                    "/kchat reply dm-" + message.id + " ",
                    message.replyToId, message.replyToRelayId, message.replyToSender, message.replyToPreview);
        }
        sender.sendMessage(ChatColor.GRAY + msg("dmReadNavHint", "Use /kchat dm prev for newer messages, /kchat dm next for older messages."));
        sender.sendMessage(ChatColor.GRAY + msg("dmHideHint", "Hide one from your view: /kchat dm hide <messageId>"));
        return true;
    }

    private boolean dmHide(CommandSender sender, String senderUuid, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(yellow("/kchat dm hide <messageId>"));
            return true;
        }
        long messageId;
        try {
            messageId = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(red(msg("dmInvalidMessageId", "Invalid message id.")));
            return true;
        }
        String threadId = plugin.directMessages().threadIdForMessage(senderUuid, messageId);
        boolean ok = plugin.directMessages().hideMessage(senderUuid, messageId);
        if (!ok) {
            sender.sendMessage(red(msg("dmHideFailed", "Failed to hide message.")));
            return true;
        }
        WebChatServer server = plugin.webServer();
        if (server != null) server.publishDirectMessageUpdate(senderUuid, senderUuid, threadId);
        sender.sendMessage(green(msg("dmHidden", "Message hidden from your view.")));
        return true;
    }
    private String formatCommandPlayer(String displayName, String username, String uuid) {
        String display = displayName == null ? "" : displayName.trim();
        String user = username == null ? "" : username.trim();
        if (!display.isBlank() && !user.isBlank() && !display.equals(user)) return display + " (" + user + ")";
        if (!display.isBlank()) return display;
        if (!user.isBlank()) return user;
        return uuid == null ? "" : uuid;
    }

    private String transformForCommandDisplay(Player viewer, String message, int maxLength) {
        String text = maxLength > 0 ? trim(message, maxLength) : String.valueOf(message == null ? "" : message);
        // DM command output should be delivered as-is; ImageEmojis can handle
        // direct message tokens on its own side.
        return plugin.restoreMessageTokenGameBreaks(colorizeForGame(text));
    }


    private String colorizeForGame(String value) {
        String out = String.valueOf(value == null ? "" : value);
        out = ChatColor.translateAlternateColorCodes('&', out);
        return out;
    }

    private String sentEchoLine(String targetLabel, String message) {
        String rendered = msg("dmSentEcho", "to: {player} {message}",
                "player", targetLabel == null ? "" : targetLabel,
                "message", message == null ? "" : message);
        // Older installed lang files can leave the old "sent" text in place.
        // Never let the sender confirmation drop the message body.
        if (message != null && !message.isBlank() && (rendered == null || !rendered.contains(message))) {
            String lang = plugin.langManager().currentLanguage();
            String prefix = lang != null && lang.toLowerCase(java.util.Locale.ROOT).startsWith("ko") ? "보냄: " : "to: ";
            rendered = prefix + (targetLabel == null ? "" : targetLabel) + " " + message;
        }
        if (rendered == null) return "";
        rendered = rendered.replaceFirst("(?i)^to:(\\S)", "to: $1");
        rendered = rendered.replaceFirst("^보냄:(\\S)", "보냄: $1");
        return rendered;
    }

    private String sentEchoLineForGame(Player viewer, String targetLabel, String message) {
        String target = targetLabel == null ? "" : targetLabel;
        String body = message == null ? "" : message;
        String rendered = sentEchoLine(ChatColor.RESET + target + ChatColor.GRAY, ChatColor.RESET + body);
        return ChatColor.GRAY + colorizeForGame(rendered);
    }

    private String incomingLineForGame(String senderName, String message) {
        String playerName = senderName == null ? "" : senderName;
        String body = colorizeForGame(trim(message, 100));
        return ChatColor.LIGHT_PURPLE + msg("dmIncoming", "DM from {player}: {message}",
                "player", ChatColor.RESET + playerName + ChatColor.LIGHT_PURPLE,
                "message", ChatColor.RESET + body);
    }

    private void notifyOnlineRecipient(Player sender, PlayerIdentity target, String message, DirectMessageMessage stored) {
        ConfigValues config = plugin.configValues();
        if (config == null || !config.directMessageNotifyOnMessage) return;
        try {
            Player recipient = plugin.getServer().getPlayer(UUID.fromString(target.uuid));
            if (recipient == null || !recipient.isOnline()) return;
            String senderName = plugin.displayPlayerName(sender);
            String line = incomingLineForGame(senderName, message);
            String displayBody = colorizeForGame(trim(message, 100));
            PlayerIdentity replyTarget = new PlayerIdentity(sender.getUniqueId().toString(), sender.getName(), senderName);
            sendPrivateInteractiveWithReply(recipient, line, senderName,
                    msg("dmClickHint", "Click to write a DM to {player}", "player", senderName),
                    "/kchat dm " + dmCommandTarget(replyTarget) + " ",
                    displayBody, msg("privateReplyClickHint", "Click the message to reply"),
                    "/kchat reply dm-" + (stored == null ? 0L : stored.id) + " ",
                    stored == null ? 0L : stored.replyToId,
                    stored == null ? "" : stored.replyToRelayId,
                    stored == null ? "" : stored.replyToSender,
                    stored == null ? "" : stored.replyToPreview);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void notifyOnlineGroupMembers(Player sender, String roomId, String roomName, String message, GroupMessage stored) {
        if (plugin.groupChats() == null) return;
        String senderUuid = sender.getUniqueId().toString();
        String senderName = plugin.displayPlayerName(sender);
        String body = colorizeForGame(trim(message, 100));
        String roomLabel = String.valueOf(roomName == null ? "" : roomName);
        String line = ChatColor.AQUA + msg("groupIncoming", "Group {room} from {player}: {message}",
                "room", ChatColor.RESET + roomLabel + ChatColor.AQUA,
                "player", ChatColor.RESET + senderName + ChatColor.AQUA,
                "message", ChatColor.RESET + body);
        for (String memberUuid : plugin.groupChats().memberUuids(roomId)) {
            if (memberUuid == null || memberUuid.equalsIgnoreCase(senderUuid)) continue;
            try {
                Player recipient = plugin.getServer().getPlayer(UUID.fromString(memberUuid));
                if (recipient != null && recipient.isOnline()) {
                    sendPrivateInteractiveWithReply(recipient, line, roomLabel,
                            msg("groupClickHint", "Click to write to group {room}", "room", roomLabel),
                            "/kchat group " + shortRoomId(roomId) + " ",
                            body, msg("privateReplyClickHint", "Click the message to reply"),
                            "/kchat reply group-" + (stored == null ? 0L : stored.id) + " ",
                            stored == null ? 0L : stored.replyToId, "",
                            stored == null ? "" : stored.replyToSender,
                            stored == null ? "" : stored.replyToPreview);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void sendPrivateInteractiveWithReply(CommandSender recipient, String text, String senderTarget, String senderHover,
                                                 String senderCommand, String replyTarget, String replyHover, String replyCommand,
                                                 long replyToId, String replyToStableId, String replyToSender, String replyToPreview) {
        if (!(recipient instanceof Player player) || plugin.platformAdapter() == null) {
            if (recipient != null) recipient.sendMessage(text);
            return;
        }
        WebChatServer server = plugin.webServer();
        if (server == null) {
            sendPrivateInteractive(recipient, text, senderTarget, senderHover, senderCommand, replyTarget, replyHover, replyCommand);
            return;
        }
        server.sendPrivateClickableGameMessage(java.util.List.of(player.getUniqueId()), privateMessageId(replyCommand),
                text, replyTarget,
                senderTarget, senderHover, senderCommand,
                replyToId, replyToStableId, replyToSender, replyToPreview);
    }

    private static String privateMessageId(String replyCommand) {
        String command = String.valueOf(replyCommand == null ? "" : replyCommand).trim();
        String prefix = "/kchat reply ";
        if (!command.regionMatches(true, 0, prefix, 0, prefix.length())) return "";
        String tail = command.substring(prefix.length()).trim();
        int space = tail.indexOf(' ');
        return space < 0 ? tail : tail.substring(0, space);
    }

    private void sendPrivateInteractive(CommandSender recipient, String text, String senderTarget, String senderHover,
                                        String senderCommand, String replyTarget, String replyHover, String replyCommand) {
        if (!(recipient instanceof Player player) || plugin.platformAdapter() == null) {
            if (recipient != null) recipient.sendMessage(text);
            return;
        }
        plugin.platformAdapter().sendInteractiveMessage(List.of(player.getUniqueId()), new PlatformGameMessage(
                text, true, senderTarget, senderHover, senderCommand, replyTarget, replyHover, replyCommand));
    }

    private String dmCommandTarget(PlayerIdentity identity) {
        if (identity == null) return "";
        RemotePlayerRef remote = RemotePlayerRef.parse(identity.uuid);
        if (remote != null) {
            String username = String.valueOf(identity.username == null ? "" : identity.username).trim();
            return !username.isBlank() ? username + "@" + remote.serverId : remote.key;
        }
        String username = String.valueOf(identity.username == null ? "" : identity.username).trim();
        return !username.isBlank() ? username : String.valueOf(identity.uuid == null ? "" : identity.uuid).trim();
    }

    private void sendTokenGameLines(CommandSender recipient, String protectedLine) {
        if (recipient == null) return;
        for (String line : plugin.splitMessageTokenGameLines(protectedLine)) {
            recipient.sendMessage(line == null || line.isEmpty() ? " " : line);
        }
    }

    private String stripForDm(String raw, int maxLength) {
        String out = String.valueOf(raw == null ? "" : raw);
        // Preserve Minecraft legacy color codes and :pack/name: emoji tokens. Only
        // remove real control characters; web rendering handles colors/emojis.
        out = out.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        if (maxLength > 0 && out.length() > maxLength) out = out.substring(0, maxLength);
        return out;
    }

    private String trim(String value, int max) {
        String out = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (max > 0 && out.length() > max) return out.substring(0, Math.max(0, max - 1)) + "…";
        return out;
    }

    private boolean sessions(CommandSender sender) {
        if (!PermissionCompat.has(sender, "kwc.admin")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        plugin.storage().cleanupExpiredSessions();
        List<SessionContext> sessions = plugin.storage().listSessions();
        sender.sendMessage(ChatColor.AQUA + msg("sessionsTitle", "KOKOTO WebChat sessions: {count}", "count", Integer.toString(sessions.size())));
        for (SessionContext ctx : sessions) {
            sender.sendMessage(yellow(msg("sessionEntry", "- {username} {role} ip={ip}",
                    "username", ctx.account.safeUsername(),
                    "role", ctx.account.role.name(),
                    "ip", ctx.session.lastIp)));
        }
        return true;
    }

    private boolean revoke(CommandSender sender, String[] args) {
        if (!PermissionCompat.has(sender, "kwc.admin")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(yellow("/kchat revoke <username>"));
            return true;
        }
        Account revokedAccount = plugin.storage().findAccountByUsername(args[1]);
        String revokedUuid = revokedAccount == null || revokedAccount.uuid == null ? "" : revokedAccount.uuid.trim().toLowerCase(java.util.Locale.ROOT);
        int removed = plugin.storage().revokeSessionsForUsername(args[1]);
        if (removed > 0 && plugin.webServer() != null && !revokedUuid.isBlank()) {
            plugin.webServer().broadcastAuthExpired(revokedUuid, "revoked");
        }
        AuditLogger.log(plugin, "command.revoke-sessions", sender.getName(), Map.of("username", args[1], "removed", removed));
        sender.sendMessage(green(msg("revokeDone", "Revoked {count} session(s) for {username}.",
                "count", Integer.toString(removed),
                "username", args[1])));
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!PermissionCompat.has(sender, "kwc.admin")) {
            sender.sendMessage(red(msg("noPermission", "You do not have permission.")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(yellow("/kchat admin create <id>"));
            sender.sendMessage(yellow("/kchat admin password <id> <password>"));
            sender.sendMessage(yellow("/kchat admin role <id> <user|moderator|admin>"));
            return true;
        }

        String sub = args[1].toLowerCase();
        if ("create".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(yellow("/kchat admin create <id>"));
                return true;
            }
            if (!plugin.configValues().allowLocalAdminAccounts) {
                sender.sendMessage(red(msg("adminLocalDisabled", "Local admin account creation is disabled.")));
                return true;
            }
            Account a = plugin.storage().createLocalAccount(args[2], Role.ADMIN);
            AuditLogger.log(plugin, "command.admin-create", sender.getName(), Map.of("username", a.safeUsername()));
            sender.sendMessage(green(msg("adminCreated", "Created local admin account: {username}", "username", a.safeUsername())));
            sender.sendMessage(yellow(msg("adminSetPasswordHint", "Set password: /kchat admin password {username} <password>", "username", a.safeUsername())));
            return true;
        }

        if ("password".equals(sub)) {
            if (args.length < 4) {
                sender.sendMessage(yellow("/kchat admin password <id> <password>"));
                return true;
            }
            Account a = plugin.storage().findAccountByUsername(args[2]);
            if (a == null) {
                sender.sendMessage(red(msg("adminAccountNotFound", "Account not found.")));
                return true;
            }
            plugin.storage().setPassword(a, args[3]);
            AuditLogger.log(plugin, "command.admin-password", sender.getName(), Map.of("username", a.safeUsername()));
            sender.sendMessage(green(msg("adminPasswordSetFor", "Password has been set for: {username}", "username", a.safeUsername())));
            return true;
        }

        if ("role".equals(sub)) {
            if (args.length < 4) {
                sender.sendMessage(yellow("/kchat admin role <id> <user|moderator|admin>"));
                return true;
            }
            Account a = plugin.storage().findAccountByUsername(args[2]);
            if (a == null) {
                sender.sendMessage(red(msg("adminAccountNotFound", "Account not found.")));
                return true;
            }
            Role role = Role.fromString(args[3], null);
            if (role == null || role == Role.GUEST) {
                sender.sendMessage(red(msg("adminRoleInvalid", "Role must be one of: user, moderator, admin.")));
                return true;
            }
            plugin.storage().setRole(a, role);
            AuditLogger.log(plugin, "command.admin-role", sender.getName(), Map.of("username", a.safeUsername(), "role", role.name()));
            sender.sendMessage(green(msg("adminRoleChanged", "{username} role changed to {role}.",
                    "username", a.safeUsername(),
                    "role", role.name())));
            return true;
        }

        sender.sendMessage(red(msg("adminUnknownSubcommand", "Unknown admin subcommand.")));
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "KOKOTO WebChat");
        sender.sendMessage(yellow("/kchat auth <code>") + ChatColor.GRAY + " - " + msg("helpAuth", "Link web account"));
        sender.sendMessage(yellow("/kchat password <newPassword>") + ChatColor.GRAY + " - " + msg("helpPassword", "Set web login password"));
        sender.sendMessage(yellow("/kchat status") + ChatColor.GRAY + " - " + msg("helpStatus", "Show runtime status"));
        if (PermissionCompat.has(sender, "kwc.admin")) {
            sender.sendMessage(yellow("/kchat reload") + ChatColor.GRAY + " - " + msg("helpReload", "Reload configuration"));
            sender.sendMessage(yellow("/kchat admin ...") + ChatColor.GRAY + " - " + msg("helpAdmin", "Manage admin accounts and roles"));
            sender.sendMessage(yellow("/kchat guest ...") + ChatColor.GRAY + " - " + msg("helpGuest", "Manage guest/IP mutes"));
            sender.sendMessage(yellow("/kchat sessions") + ChatColor.GRAY + " - " + msg("helpSessions", "List web sessions"));
            sender.sendMessage(yellow("/kchat revoke <username>") + ChatColor.GRAY + " - " + msg("helpRevoke", "Revoke web sessions"));
        }
        ConfigValues config = plugin.configValues();
        if (config != null && config.pluginEnabled && config.directMessageEnabled && sender instanceof Player && PermissionCompat.has(sender, "kwc.dm")) {
            sender.sendMessage(yellow("/kchat dm <player> <message>") + ChatColor.GRAY + " - " + msg("helpDm", "Send a direct message"));
            sender.sendMessage(yellow("/kchat dm list [pageSize]") + ChatColor.GRAY + " - " + msg("helpDmList", "List direct message threads"));
            sender.sendMessage(yellow("/kchat dm read <player> [pageSize]") + ChatColor.GRAY + " - " + msg("helpDmRead", "Read a direct message thread"));
            sender.sendMessage(yellow("/kchat dm hide <messageId>") + ChatColor.GRAY + " - " + msg("helpDmHide", "Hide a direct message from your view"));
        }
        if (config != null && config.pluginEnabled && config.groupChatEnabled && sender instanceof Player && PermissionCompat.has(sender, "kwc.group")) {
            sender.sendMessage(yellow("/kchat group list") + ChatColor.GRAY + " - " + msg("helpGroupList", "List joined group chats"));
            sender.sendMessage(yellow("/kchat group read <room> [pageSize]") + ChatColor.GRAY + " - " + msg("helpGroupRead", "Read a group chat"));
            sender.sendMessage(yellow("/kchat group <room> <message>") + ChatColor.GRAY + " - " + msg("helpGroup", "Send a group chat message"));
        }
        if (config != null && config.pluginEnabled && config.replyGameClickEnabled && sender instanceof Player && PermissionCompat.has(sender, "kwc.reply")) {
            sender.sendMessage(yellow("/kchat reply <messageId> <message>") + ChatColor.GRAY + " - " + msg("helpReply", "Reply to a public KWC message"));
        }
    }

    private String msg(String key, String fallback, String... values) {
        Map<String, String> map = new LinkedHashMap<>();
        if (values != null) {
            for (int i = 0; i + 1 < values.length; i += 2) {
                map.put(values[i], values[i + 1]);
            }
        }
        LangManager lm = plugin.langManager();
        if (lm != null) {
            return lm.text("command." + key, fallback, map);
        }
        String out = String.valueOf(fallback == null ? "" : fallback);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue() == null ? "" : entry.getValue()));
        }
        return out;
    }

    private String red(String s) { return ChatColor.RED + s; }
    private String green(String s) { return ChatColor.GREEN + s; }
    private String yellow(String s) { return ChatColor.YELLOW + s; }


    private List<String> filterPrefix(List<String> values, String partial) {
        String p = String.valueOf(partial == null ? "" : partial).toLowerCase(java.util.Locale.ROOT);
        if (p.isBlank()) return values;
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(java.util.Locale.ROOT).startsWith(p)) out.add(value);
        }
        return out;
    }

    private boolean isDmSubcommand(String value) {
        String v = String.valueOf(value == null ? "" : value).toLowerCase(java.util.Locale.ROOT);
        return v.equals("list") || v.equals("unread") || v.equals("read") || v.equals("view")
                || v.equals("next") || v.equals("prev") || v.equals("previous") || v.equals("hide") || v.equals("delete");
    }

    private List<String> emojiTokenTabSuggestions(String partial) {
        List<String> out = new ArrayList<>();
        ConfigValues config = plugin.configValues();
        if (config == null || !config.emojiEnabled) return out;
        String prefix = String.valueOf(partial == null ? "" : partial).trim();
        if (!prefix.startsWith(":")) return out;
        if (prefix.matches("(?i)^https?://.*") || prefix.matches("^\\d{1,2}:\\d{0,2}$")) return out;
        File root = emojiDirectory(config);
        if (root == null || !root.isDirectory()) return out;
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        collectEmojiTokensFromDirectory(root, "default", tokens, config);
        File[] packs = root.listFiles(File::isDirectory);
        if (packs != null) {
            java.util.Arrays.sort(packs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File pack : packs) {
                String packId = sanitizeEmojiTokenSegment(pack.getName());
                if (!packId.isBlank()) collectEmojiTokensFromDirectory(pack, packId, tokens, config);
            }
        }
        String q = prefix.toLowerCase(java.util.Locale.ROOT);
        int max = 80;
        for (String token : tokens) {
            if (token.toLowerCase(java.util.Locale.ROOT).startsWith(q)) {
                out.add(token);
                if (out.size() >= max) break;
            }
        }
        // If the user typed :emoji:..., allow completing the legacy/prefixed form too.
        if (out.isEmpty() && q.startsWith(":emoji:")) {
            String shortPrefix = ":" + prefix.substring(7);
            for (String token : tokens) {
                String legacy = ":emoji" + token;
                if (legacy.toLowerCase(java.util.Locale.ROOT).startsWith(q) || token.toLowerCase(java.util.Locale.ROOT).startsWith(shortPrefix.toLowerCase(java.util.Locale.ROOT))) {
                    out.add(legacy);
                    if (out.size() >= max) break;
                }
            }
        }
        return out;
    }

    private File emojiDirectory(ConfigValues config) {
        String configured = config == null ? null : config.emojiDirectory;
        if (configured == null || configured.isBlank()) configured = "emojis";
        File file = new File(configured);
        return file.isAbsolute() ? file : new File(plugin.getDataFolder(), configured);
    }

    private void collectEmojiTokensFromDirectory(File dir, String packId, java.util.LinkedHashSet<String> out, ConfigValues config) {
        File[] files = dir.listFiles(File::isFile);
        if (files == null) return;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        java.util.HashSet<String> seenNames = new java.util.HashSet<>();
        for (File file : files) {
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
            if (!emojiExtensionAllowedForTab(ext, config)) continue;
            String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
            String name = sanitizeEmojiTokenSegment(base);
            if (name.isBlank() || !seenNames.add(name.toLowerCase(java.util.Locale.ROOT))) continue;
            out.add(":" + packId + "/" + name + ":");
        }
    }

    private boolean emojiExtensionAllowedForTab(String ext, ConfigValues config) {
        String e = String.valueOf(ext == null ? "" : ext).replace(".", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (e.isBlank()) return false;
        List<String> allowed = config == null ? null : config.emojiAllowedExtensions;
        if (allowed == null || allowed.isEmpty()) return e.equals("png") || e.equals("jpg") || e.equals("jpeg") || e.equals("gif") || e.equals("webp");
        for (String a : allowed) {
            if (e.equals(String.valueOf(a).replace(".", "").trim().toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    private String sanitizeEmojiTokenSegment(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.') sb.append(ch);
        }
        return sb.toString();
    }

    private String directMessageTargetSuggestion(PlayerIdentity player) {
        if (player == null) return "";
        String name = player.username != null && !player.username.isBlank()
                ? player.username.trim()
                : String.valueOf(player.displayName == null ? "" : player.displayName).trim();
        if (name.isBlank()) return "";
        RemotePlayerRef remote = RemotePlayerRef.parse(player.uuid);
        return remote == null ? name : name + "@" + remote.serverId;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        ConfigValues config = plugin.configValues();
        if (config != null && !config.pluginEnabled) {
            if (args.length == 1) {
                out.add("status");
                if (PermissionCompat.has(sender, "kwc.admin")) out.add("reload");
            }
            return filterPrefix(out, args.length == 0 ? "" : args[args.length - 1]);
        }
        if (args.length == 1) {
            out.add("auth");
            out.add("password");
            out.add("status");
            if (config != null && config.directMessageEnabled && sender instanceof Player) out.add("dm");
            if (config != null && config.replyGameClickEnabled && sender instanceof Player) out.add("reply");
            if (config != null && config.groupChatEnabled && sender instanceof Player) out.add("group");
            if (PermissionCompat.has(sender, "kwc.admin")) {
                out.add("reload");
                out.add("admin");
                out.add("guest");
                out.add("sessions");
                out.add("revoke");
            }
        } else if (args.length >= 3 && "reply".equalsIgnoreCase(args[0]) && sender instanceof Player) {
            out.addAll(emojiTokenTabSuggestions(args[args.length - 1]));
        } else if (args.length == 2 && "dm".equalsIgnoreCase(args[0]) && sender instanceof Player) {
            out.add("list");
            out.add("unread");
            out.add("read");
            out.add("next");
            out.add("prev");
            out.add("hide");
            String partial = args.length > 1 ? args[1] : "";
            for (PlayerIdentity p : plugin.storage().listKnownPlayers(partial, 20)) {
                String suggestion = directMessageTargetSuggestion(p);
                if (!suggestion.isBlank()) out.add(suggestion);
            }
        } else if (args.length == 3 && "dm".equalsIgnoreCase(args[0]) && "read".equalsIgnoreCase(args[1]) && sender instanceof Player) {
            String partial = args.length > 2 ? args[2] : "";
            for (PlayerIdentity p : plugin.storage().listKnownPlayers(partial, 20)) {
                String suggestion = directMessageTargetSuggestion(p);
                if (!suggestion.isBlank()) out.add(suggestion);
            }
        } else if (args.length >= 3 && "dm".equalsIgnoreCase(args[0]) && sender instanceof Player && !isDmSubcommand(args[1])) {
            out.addAll(emojiTokenTabSuggestions(args[args.length - 1]));
        } else if (args.length == 2 && ("group".equalsIgnoreCase(args[0]) || "gc".equalsIgnoreCase(args[0])) && sender instanceof Player) {
            out.add("list");
            out.add("read");
            out.add("send");
            out.add("next");
            out.add("prev");
            String senderUuid = ((Player) sender).getUniqueId().toString();
            if (plugin.groupChats() != null && plugin.groupChats().available()) {
                for (GroupRoom room : plugin.groupChats().listRooms(senderUuid, 50)) {
                    if (room != null && room.member && room.name != null && !room.name.isBlank()) out.add(room.name);
                    if (room != null && room.member && room.id != null && !room.id.isBlank()) out.add(shortRoomId(room.id));
                }
            }
        } else if (args.length == 3 && ("group".equalsIgnoreCase(args[0]) || "gc".equalsIgnoreCase(args[0])) && "read".equalsIgnoreCase(args[1]) && sender instanceof Player) {
            String senderUuid = ((Player) sender).getUniqueId().toString();
            if (plugin.groupChats() != null && plugin.groupChats().available()) {
                for (GroupRoom room : plugin.groupChats().listRooms(senderUuid, 50)) {
                    if (room != null && room.member && room.name != null && !room.name.isBlank()) out.add(room.name);
                    if (room != null && room.member && room.id != null && !room.id.isBlank()) out.add(shortRoomId(room.id));
                }
            }
        } else if (args.length >= 3 && ("group".equalsIgnoreCase(args[0]) || "gc".equalsIgnoreCase(args[0])) && sender instanceof Player && !"read".equalsIgnoreCase(args[1]) && !"list".equalsIgnoreCase(args[1]) && !"rooms".equalsIgnoreCase(args[1])) {
            out.addAll(emojiTokenTabSuggestions(args[args.length - 1]));
        } else if (args.length == 2 && "admin".equalsIgnoreCase(args[0]) && PermissionCompat.has(sender, "kwc.admin")) {
            out.add("create");
            out.add("password");
            out.add("role");
        } else if (args.length == 2 && "guest".equalsIgnoreCase(args[0]) && PermissionCompat.has(sender, "kwc.admin")) {
            out.add("mute");
            out.add("unmute");
            out.add("list");
        } else if (args.length == 3 && "guest".equalsIgnoreCase(args[0]) && ("mute".equalsIgnoreCase(args[1]) || "unmute".equalsIgnoreCase(args[1])) && PermissionCompat.has(sender, "kwc.admin")) {
            out.add("guest");
            out.add("ip");
        }
        return filterPrefix(out, args.length == 0 ? "" : args[args.length - 1]);
    }
}
