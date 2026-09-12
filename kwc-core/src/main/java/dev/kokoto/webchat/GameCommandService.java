package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * GameCommandService는 여러 저수준 객체를 조합해 하나의 KWC 기능 흐름을 수행하는 서비스/관리 계층이다.
 * GameCommandService is a service/manager layer coordinating lower-level objects into one KWC feature flow.
 *
 * 상태 변경 순서와 실패 시 rollback/재시도 의미가 호출자에게 예측 가능하도록 side effect를 한곳에서 조정한다.
 * Coordinate side effects so mutation order and rollback/retry behavior remain predictable to callers.
 */
import java.util.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Loader-neutral implementation of the game-side KWC private-chat/admin commands.
 * Fabric and NeoForge route their registered command tails here so command
 * behavior stays aligned without loader-specific copies of DM/group/reply logic.
 */
/**
 * KWC 유지보수 안내: 게임 내 /kchat 명령을 웹 API와 동일한 DM/그룹 정책으로 연결하는 command facade다. pagination cursor는 명령 실행자별 메모리 상태이고 영속 데이터가 아니며, 권한 판정은 WebChatServer/저장소의 최종 검사를 우회하지 않는다.
 *
 * KWC maintenance note: Command facade mapping in-game /kchat commands to the same DM/group policy used by web APIs. Pagination cursors are per-sender in-memory state rather than persisted data, and command authorization never bypasses final checks in WebChatServer/storage.
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

    // 명령 문자열을 subcommand로 분기하는 최상위 진입점이다. plugin/config enable 상태를 가장 먼저 확인하고 sender별 cursor를 이용해 DM/group read pagination을 유지한다.
    // Top-level command dispatcher. It first checks plugin/config enablement and uses per-sender cursors to maintain DM/group read pagination.
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
            case "game" -> game(sender, tail);
            default -> help(sender);
        };
    }

    public int help(Sender sender) {
        ConfigValues c = host.configValues();
        sender.send("KOKOTO WebChat");
        sender.send("/kchat auth <code> - " + msg("helpAuth", "Link web account"));
        sender.send("/kchat password <newPassword> - " + msg("helpPassword", "Set web login password"));
        sender.send("/kchat status - " + msg("helpStatus", "Show runtime status"));
        sender.send("/kchat game list | status [eventId] | join/vote/apply/withdraw - " + msg("helpGame", "List, view, or join events"));
        if (canManageGame(sender)) {
            gameCreateUsageLines(sender);
            sender.send("/kchat game autoend <eventId> <capacity|responses|time> <value>");
            WebChatServer server = webServer.get();
            if (server != null && server.chatGameRelayTopologyConfigured()) sender.send("/kchat game scope <eventId> <relay|local>");
            sender.send("/kchat game finish/draw/close/delete ...");
        }
        if (has(sender, "kwc.admin")) {
            sender.send("/kchat reload - " + msg("helpReload", "Reload configuration"));
            sender.send("/kchat admin ... - " + msg("helpAdmin", "Manage admin accounts and roles"));
            sender.send("/kchat guest ... - " + msg("helpGuest", "Manage guest/IP mutes"));
            sender.send("/kchat sessions - " + msg("helpSessions", "List web sessions"));
            sender.send("/kchat revoke <username> - " + msg("helpRevoke", "Revoke web sessions"));
        }
        if (has(sender, "kwc.admin.filter")) sender.send("/kchat filter ... - " + msg("helpFilter", "Manage content filter rules"));
        if (has(sender, "kwc.admin.settings")) sender.send("/kchat settings ... - " + msg("helpSettings", "View or change live settings"));
        if (sender.isPlayer() && c != null && c.pluginEnabled && c.directMessageEnabled && has(sender, "kwc.dm")) {
            sender.send("/kchat dm <player> <message> - " + msg("helpDm", "Send a direct message"));
            sender.send("/kchat dm list [pageSize] - " + msg("helpDmList", "List direct message threads"));
            sender.send("/kchat dm read <player> [pageSize] - " + msg("helpDmRead", "Read a direct message thread"));
            sender.send("/kchat dm delete <messageId> - " + msg("helpDmDelete", "Delete one of your direct messages for both participants"));
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

    private int game(Sender sender, String tail) {
        if (!requirePlayer(sender)) return 0;
        WebChatServer server = webServer.get();
        if (server == null) return fail(sender, msg("gameUnavailable", "The event service is unavailable."));
        String raw = clean(tail);
        String[] head = raw.isBlank() ? new String[]{"list"} : raw.split("\\s+", 2);
        String action = head[0].toLowerCase(Locale.ROOT);
        String rest = head.length > 1 ? clean(head[1]) : "";
        ChatGameManager.Result result;

        if (action.equals("list")) {
            List<Map<String,Object>> games = server.chatGameList(sender.uuid().toString());
            if (games.isEmpty()) return ok(sender, msg("gameNone", "There are no events."));
            sender.send(msg("gameListTitle", "Events: {count}", "count", String.valueOf(games.size())));
            for (Map<String,Object> game : games) {
                sender.send("- " + shortGameId(game) + " | " + String.valueOf(game.getOrDefault("title", "")) + " | "
                        + gameTypeText(String.valueOf(game.getOrDefault("type", ""))) + " | "
                        + gameStatusText(String.valueOf(game.getOrDefault("status", ""))) + " | " + gameListSummary(game));
            }
            return 1;
        }
        if (action.equals("join")) {
            result = rest.isBlank() ? server.joinChatGame(sender.uuid().toString(), sender.username(), sender.displayName())
                    : server.joinChatGame(resolveGameId(server, sender, rest), sender.uuid().toString(), sender.username(), sender.displayName());
            if (!result.ok()) return fail(sender, gameError(result.error()));
            sender.send(result.error().equals("already_joined") ? msg("gameAlreadyJoined", "You already joined this event.") : msg("gameJoined", "You joined the event."));
            sendGameStatus(sender, result.game());
            return 1;
        }
        if (action.equals("vote")) {
            String[] parts = rest.split("\\s+", 2);
            String gameId;
            int option;
            if (parts.length == 1 && parseLimit(parts[0], -1) > 0) {
                gameId = "";
                option = parseLimit(parts[0], -1);
            } else if (parts.length >= 2) {
                gameId = resolveGameId(server, sender, parts[0]);
                option = parseLimit(parts[1], -1);
            } else return usage(sender, "/kchat game vote <eventId> <optionNumber>");
            result = server.voteChatGame(gameId, sender.uuid().toString(), sender.username(), sender.displayName(), option);
            if (!result.ok()) return fail(sender, gameError(result.error()));
            sender.send(result.error().equals("already_voted") ? msg("gameAlreadyVoted", "That option is already your vote.") : msg("gameVoted", "Your vote was recorded."));
            sendGameStatus(sender, result.game());
            return 1;
        }
        if (action.equals("apply")) {
            String[] parts = rest.split("\\s+", 2);
            if (parts.length < 2) return usage(sender, "/kchat game apply <eventId> <role>");
            String gameId = resolveGameId(server, sender, parts[0]);
            result = server.applyRecruitmentChatGame(gameId, sender.uuid().toString(), sender.username(), sender.displayName(), parts[1]);
            if (!result.ok()) return fail(sender, gameError(result.error()));
            String state = String.valueOf(result.game().getOrDefault("recruitmentState", ""));
            int queue = intValue(result.game().get("recruitmentQueuePosition"));
            if ("waiting".equals(state)) sender.send(msg("gameRecruitmentWaiting", "Role is full. Added to the wait queue at #{position}.", "position", String.valueOf(queue)));
            else sender.send(msg("gameRecruitmentAccepted", "Recruitment application accepted."));
            sendGameStatus(sender, result.game());
            return 1;
        }
        if (action.equals("withdraw")) {
            String gameId = rest.isBlank() ? "" : resolveGameId(server, sender, rest);
            result = server.withdrawRecruitmentChatGame(gameId, sender.uuid().toString());
            if (!result.ok()) return fail(sender, gameError(result.error()));
            sender.send(msg("gameRecruitmentWithdrawn", "Recruitment application withdrawn."));
            sendGameStatus(sender, result.game());
            return 1;
        }
        if (action.equals("participants")) {
            result = rest.isBlank() ? server.chatGameSnapshot(sender.uuid().toString())
                    : server.chatGameSnapshot(resolveGameId(server, sender, rest), sender.uuid().toString());
            if (!result.ok()) return fail(sender, gameError(result.error()));
            sendGameParticipants(sender, result.game());
            return 1;
        }
        if (action.equals("create")) {
            if (!canManageGame(sender)) return fail(sender, msg("noPermission", "You do not have permission."));
            String[] typeAndRest = rest.split("\\s+", 2);
            if (typeAndRest.length < 2) return gameCreateUsage(sender);
            String type = typeAndRest[0].toLowerCase(Locale.ROOT);
            boolean relayAnnouncements = server.chatGameRelayTopologyConfigured();
            String createArgs = typeAndRest[1];
            if (server.chatGameRelayTopologyConfigured()) {
                String[] maybeScope = createArgs.split("\\s+", 2);
                if (maybeScope.length > 0 && (maybeScope[0].equalsIgnoreCase("local") || maybeScope[0].equalsIgnoreCase("relay"))) {
                    relayAnnouncements = maybeScope[0].equalsIgnoreCase("relay");
                    createArgs = maybeScope.length > 1 ? maybeScope[1] : "";
                }
            }
            if (type.equals("firstcome")) {
                String[] parts = createArgs.split("\\s+", 2);
                if (parts.length < 2) return gameCreateUsage(sender);
                int winners = parseLimit(parts[0], -1);
                result = server.createChatGame(type, parts[1], winners, winners, sender.displayName(), relayAnnouncements);
            } else if (type.equals("lottery")) {
                String[] parts = createArgs.split("\\s+", 3);
                if (parts.length < 3) return gameCreateUsage(sender);
                result = server.createChatGame(type, parts[2], parseLimit(parts[0], -1), parseLimit(parts[1], -1), sender.displayName(), relayAnnouncements);
            } else if (type.equals("poll")) {
                String[] sections = createArgs.split("\\s*\\|\\s*");
                if (sections.length < 3) return gameCreateUsage(sender);
                String title = sections[0];
                String options = String.join("|", Arrays.copyOfRange(sections, 1, sections.length));
                result = server.createPollChatGame(title, options, sender.displayName(), relayAnnouncements);
            } else if (type.equals("recruitment")) {
                String[] sections = createArgs.split("\\s*\\|\\s*");
                if (sections.length < 2) return gameCreateUsage(sender);
                String title = sections[0];
                String roles = String.join("|", Arrays.copyOfRange(sections, 1, sections.length));
                result = server.createRecruitmentChatGame(title, roles, sender.displayName(), relayAnnouncements);
            } else return fail(sender, gameError("invalid_type"));
            if (!result.ok()) return fail(sender, gameError(result.error()));
            return ok(sender, msg("gameCreated", "Event created: {title} ({id})", "title", String.valueOf(result.game().getOrDefault("title", "")), "id", shortGameId(result.game())));
        }
        if (action.equals("autoend")) {
            if (!canManageGame(sender)) return fail(sender, msg("noPermission", "You do not have permission."));
            String[] parts = rest.split("\\s+", 3);
            if (parts.length < 3) return usage(sender, "/kchat game autoend <eventId> <capacity|responses|time> <value>");
            String gameId = resolveGameId(server, sender, parts[0]);
            ChatGameManager.Result current = server.chatGameSnapshot(gameId, sender.uuid().toString());
            if (!current.ok() || current.game() == null) return fail(sender, gameError(current.error()));
            Map<String,Object> game = current.game();
            boolean capacity = Boolean.TRUE.equals(game.get("autoEndOnCapacity"));
            int responses = intValue(game.get("autoEndResponseCount"));
            long at = longValue(game.get("autoEndAt"));
            String condition = parts[1].toLowerCase(Locale.ROOT);
            String value = parts[2];
            String type = String.valueOf(game.getOrDefault("type", ""));
            if (condition.equals("capacity")) {
                if ("firstcome".equals(type)) {
                    if (!parseOnOff(value, true)) return fail(sender, msg("gameAutoEndRequired", "First-come events always end when winner capacity is filled."));
                    capacity = true;
                } else if (!("lottery".equals(type) || "recruitment".equals(type))) return fail(sender, msg("gameAutoEndNotApplicable", "That automatic-end condition is not available for this event type."));
                else capacity = parseOnOff(value, capacity);
            } else if (condition.equals("responses")) {
                if (!"poll".equals(type)) return fail(sender, msg("gameAutoEndNotApplicable", "That automatic-end condition is not available for this event type."));
                responses = value.equalsIgnoreCase("off") ? 0 : parseLimit(value, -1);
                if (responses < 0) return usage(sender, "/kchat game autoend <eventId> responses <count|off>");
            } else if (condition.equals("time")) {
                at = parseAutoEndTime(value);
                if (at < 0L) return usage(sender, "/kchat game autoend <eventId> time <off|ISO-8601>");
            } else return usage(sender, "/kchat game autoend <eventId> <capacity|responses|time> <value>");
            result = server.setChatGameAutoEnd(gameId, capacity, responses, at);
            if (!result.ok()) return fail(sender, gameError(result.error()));
            sendGameStatus(sender, result.game());
            return 1;
        }
        if (action.equals("scope")) {
            if (!canManageGame(sender)) return fail(sender, msg("noPermission", "You do not have permission."));
            if (!server.chatGameRelayTopologyConfigured()) return fail(sender, msg("gameScopeUnavailable", "Relay event delivery is not configured on this server."));
            String[] parts = rest.split("\\s+", 2);
            if (parts.length < 2 || !(parts[1].equalsIgnoreCase("local") || parts[1].equalsIgnoreCase("relay"))) return usage(sender, "/kchat game scope <eventId> <relay|local>");
            result = server.setChatGameRelayAnnouncements(resolveGameId(server, sender, parts[0]), parts[1].equalsIgnoreCase("relay"));
            if (!result.ok()) return fail(sender, gameError(result.error()));
            sendGameStatus(sender, result.game());
            return 1;
        }
        if (action.equals("draw")) {
            if (!canManageGame(sender)) return fail(sender, msg("noPermission", "You do not have permission."));
            result = rest.isBlank() ? server.drawChatGame() : server.drawChatGame(resolveGameId(server, sender, rest));
            if (!result.ok()) return fail(sender, gameError(result.error()));
            return ok(sender, msg("gameDrawn", "Winners were drawn for {title}.", "title", String.valueOf(result.game().getOrDefault("title", ""))));
        }
        if (action.equals("finish")) {
            if (!canManageGame(sender)) return fail(sender, msg("noPermission", "You do not have permission."));
            result = rest.isBlank() ? server.finishChatGame() : server.finishChatGame(resolveGameId(server, sender, rest));
            if (!result.ok()) return fail(sender, gameError(result.error()));
            return ok(sender, msg("gameFinished", "Event finalized: {title}", "title", String.valueOf(result.game().getOrDefault("title", ""))));
        }
        if (action.equals("close")) {
            if (!canManageGame(sender)) return fail(sender, msg("noPermission", "You do not have permission."));
            result = rest.isBlank() ? server.closeChatGame() : server.closeChatGame(resolveGameId(server, sender, rest));
            if (!result.ok()) return fail(sender, gameError(result.error()));
            return ok(sender, msg("gameClosed", "The event was closed."));
        }
        if (action.equals("delete") || action.equals("remove")) {
            if (!canManageGame(sender)) return fail(sender, msg("noPermission", "You do not have permission."));
            if (rest.isBlank()) return usage(sender, "/kchat game delete <eventId>");
            result = server.deleteChatGame(resolveGameId(server, sender, rest));
            if (!result.ok()) return fail(sender, gameError(result.error()));
            return ok(sender, msg("gameDeleted", "Event deleted: {title}", "title", String.valueOf(result.game().getOrDefault("title", ""))));
        }
        if (action.equals("status")) {
            result = rest.isBlank() ? server.chatGameSnapshot(sender.uuid().toString())
                    : server.chatGameSnapshot(resolveGameId(server, sender, rest), sender.uuid().toString());
            if (!result.ok()) return fail(sender, gameError(result.error()));
            sendGameStatus(sender, result.game());
            return 1;
        }
        String manage = canManageGame(sender) ? "|autoend <eventId> ..." + (server.chatGameRelayTopologyConfigured() ? "|scope <eventId> <relay|local>" : "") + "|create ...|finish/draw/close/delete ..." : "";
        return usage(sender, "/kchat game list|status [eventId]|participants [eventId]|join [eventId]|vote <eventId> <option>|apply <eventId> <role>|withdraw [eventId]" + manage);
    }

    private int gameCreateUsage(Sender sender) { gameCreateUsageLines(sender); return 0; }

    private void gameCreateUsageLines(Sender sender) {
        WebChatServer server = webServer.get();
        String scope = server != null && server.chatGameRelayTopologyConfigured() ? " [relay|local]" : "";
        sender.send("/kchat game create firstcome" + scope + " <winners> <title>");
        sender.send("/kchat game create lottery" + scope + " <participants> <winners> <title>");
        sender.send("/kchat game create poll" + scope + " <title> | <option1> | <option2> [| ...]");
        sender.send("/kchat game create recruitment" + scope + " <title> | <role>:<count> [| ...]");
    }

    /** Loader-neutral completion service used by Bukkit and Brigadier loaders. Returns candidates for the token at the cursor. */
    public List<String> suggest(Sender sender, String rawArguments) {
        String raw = rawArguments == null ? "" : rawArguments;
        boolean trailing = !raw.isEmpty() && Character.isWhitespace(raw.charAt(raw.length() - 1));
        String trimmed = raw.trim();
        List<String> tokens = trimmed.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(trimmed.split("\\s+")));
        String prefix = trailing || tokens.isEmpty() ? "" : tokens.remove(tokens.size() - 1);
        List<String> candidates = new ArrayList<>();
        ConfigValues c = host.configValues();
        if (tokens.isEmpty()) {
            candidates.addAll(List.of("auth","password","status"));
            if (sender != null && sender.isPlayer()) {
                candidates.add("game");
                if (c != null && c.directMessageEnabled && has(sender,"kwc.dm")) candidates.add("dm");
                if (c != null && c.groupChatEnabled && has(sender,"kwc.group")) candidates.add("group");
                if (c != null && c.replyGameClickEnabled && has(sender,"kwc.reply")) candidates.add("reply");
            }
            if (has(sender,"kwc.admin")) candidates.addAll(List.of("reload","admin","guest","sessions","revoke"));
            if (has(sender,"kwc.admin.filter")) candidates.add("filter");
            if (has(sender,"kwc.admin.settings")) candidates.add("settings");
            return complete(candidates, prefix);
        }
        String root = tokens.get(0).toLowerCase(Locale.ROOT);
        if (root.equals("filter") && has(sender,"kwc.admin.filter")) {
            if (tokens.size() == 1) return complete(List.of("status","list","enable","disable","scope","add","remove","test"), prefix);
            String action=tokens.get(1).toLowerCase(Locale.ROOT);
            if ((action.equals("scope") || action.equals("test")) && tokens.size()==2) return complete(List.of("public","group","dm"), prefix);
            if (action.equals("scope") && tokens.size()==3) return complete(List.of("on","off"), prefix);
            if (action.equals("add") && tokens.size()==2) return complete(List.of("block","mask","replace"), prefix);
            if (action.equals("remove") && tokens.size()==2 && c != null && c.contentFilterRules != null) {
                for (ContentFilterRule r:c.contentFilterRules) if(r!=null && r.id!=null) candidates.add(r.id);
                return complete(candidates,prefix);
            }
            return List.of();
        }
        if (root.equals("settings") && has(sender,"kwc.admin.settings")) {
            if (tokens.size()==1) return complete(List.of("show","set"),prefix);
            String action=tokens.get(1).toLowerCase(Locale.ROOT);
            if ((action.equals("show") || action.equals("set")) && tokens.size()==2) return complete(RuntimeSettingsController.SUPPORTED_PATHS,prefix);
            if (action.equals("set") && tokens.size()==3) {
                String key=tokens.get(2).toLowerCase(Locale.ROOT);
                if (key.endsWith(".enabled") || key.startsWith("content-filter.scopes.") || key.startsWith("upload.allow-") || key.endsWith(".user-display-control") || key.endsWith(".unicode-normalization") || key.endsWith(".compact-match") || key.endsWith(".interleave-match") || key.endsWith(".interleave-unlimited-gap") || key.endsWith(".collapse-repeats") || key.endsWith(".show-matched-word") || key.equals("auth.password-login")) return complete(List.of("true","false"),prefix);
                if (key.equals("upload.filename-mode")) return complete(List.of("random","original"),prefix);
            }
            return List.of();
        }
        if (root.equals("admin") && has(sender,"kwc.admin")) {
            if (tokens.size()==1) return complete(List.of("create","password","role"),prefix);
            String action=tokens.get(1).toLowerCase(Locale.ROOT);
            if ((action.equals("password") || action.equals("role")) && tokens.size()==2 && host.storage()!=null) {
                for(Account a:host.storage().listAccounts()) if(a!=null && a.safeUsername()!=null) candidates.add(a.safeUsername());
                return complete(candidates,prefix);
            }
            if (action.equals("role") && tokens.size()==3) return complete(List.of("user","moderator","admin"),prefix);
            return List.of();
        }
        if (root.equals("guest") && has(sender,"kwc.admin")) {
            if (tokens.size()==1) return complete(List.of("mute","unmute","list"),prefix);
            String action=tokens.get(1).toLowerCase(Locale.ROOT);
            if ((action.equals("mute") || action.equals("unmute")) && tokens.size()==2) return complete(List.of("guest","ip"),prefix);
            return List.of();
        }
        if (root.equals("dm") && sender!=null && sender.isPlayer() && c!=null && c.directMessageEnabled && has(sender,"kwc.dm")) {
            if (tokens.size()==1) {
                candidates.addAll(List.of("list","unread","read","next","prev","delete"));
                if(host.storage()!=null) for(PlayerIdentity p:host.storage().listKnownPlayers(prefix,20)) candidates.add(commandPlayerTarget(p));
                return complete(candidates,prefix);
            }
            if (tokens.get(1).equalsIgnoreCase("read") && tokens.size()==2 && host.storage()!=null) {
                for(PlayerIdentity p:host.storage().listKnownPlayers(prefix,20)) candidates.add(commandPlayerTarget(p));
                return complete(candidates,prefix);
            }
            return List.of();
        }
        if ((root.equals("group") || root.equals("gc")) && sender!=null && sender.isPlayer() && c!=null && c.groupChatEnabled && has(sender,"kwc.group")) {
            if (tokens.size()==1) {
                candidates.addAll(List.of("list","read","send","next","prev"));
                addGroupTargets(candidates,sender.uuid());
                return complete(candidates,prefix);
            }
            if (tokens.get(1).equalsIgnoreCase("read") && tokens.size()==2) { addGroupTargets(candidates,sender.uuid()); return complete(candidates,prefix); }
            if (tokens.get(1).equalsIgnoreCase("send") && tokens.size()==2) { addGroupTargets(candidates,sender.uuid()); return complete(candidates,prefix); }
            return List.of();
        }
        if (!root.equals("game")) return List.of();
        WebChatServer server = webServer.get();
        if (server == null || sender == null || !sender.isPlayer()) return List.of();
        if (tokens.size() == 1) {
            candidates.addAll(List.of("list","status","participants","join","vote","apply","withdraw"));
            if (canManageGame(sender)) { candidates.addAll(List.of("create","finish","draw","close","delete","autoend")); if (server.chatGameRelayTopologyConfigured()) candidates.add("scope"); }
            return complete(candidates, prefix);
        }
        String action = tokens.get(1).toLowerCase(Locale.ROOT);
        List<Map<String,Object>> games = server.chatGameList(sender.uuid().toString());
        if (action.equals("create")) {
            if (tokens.size() == 2) return complete(List.of("firstcome","lottery","poll","recruitment"), prefix);
            if (tokens.size() == 3 && server.chatGameRelayTopologyConfigured()) return complete(List.of("relay","local"), prefix);
            return List.of();
        }
        if (Set.of("status","participants","join","finish","draw","close","delete","withdraw","autoend","scope","vote","apply").contains(action)) {
            if (tokens.size() == 2) {
                for (Map<String,Object> g : games) if (gameMatchesAction(g, action)) candidates.add(shortGameId(g));
                return complete(candidates, prefix);
            }
        }
        if (action.equals("vote") && tokens.size() == 3) {
            Map<String,Object> g = findGameByToken(games, tokens.get(2));
            if (g != null && g.get("pollOptions") instanceof List<?> opts) for (Object o : opts) if (o instanceof Map<?,?> m) candidates.add(String.valueOf(m.get("index")));
            return complete(candidates, prefix);
        }
        if (action.equals("apply") && tokens.size() == 3) {
            Map<String,Object> g = findGameByToken(games, tokens.get(2));
            if (g != null && g.get("recruitmentRoles") instanceof List<?> roles) for (Object o : roles) if (o instanceof Map<?,?> m) candidates.add(String.valueOf(m.get("name")));
            return complete(candidates, prefix);
        }
        if (action.equals("autoend")) {
            if (tokens.size() == 3) {
                Map<String,Object> g = findGameByToken(games, tokens.get(2));
                if (g == null) return List.of();
                String type = String.valueOf(g.getOrDefault("type",""));
                candidates.add("time");
                if (type.equals("poll")) candidates.add("responses");
                if (type.equals("lottery") || type.equals("recruitment") || type.equals("firstcome")) candidates.add("capacity");
                return complete(candidates, prefix);
            }
            if (tokens.size() == 4) {
                String condition=tokens.get(3).toLowerCase(Locale.ROOT);
                if (condition.equals("capacity")) {
                    Map<String,Object> g=findGameByToken(games,tokens.get(2));
                    return complete(g!=null && "firstcome".equals(String.valueOf(g.getOrDefault("type",""))) ? List.of("on") : List.of("on","off"), prefix);
                }
                if (condition.equals("responses")) return complete(List.of("off"), prefix);
                if (condition.equals("time")) return complete(List.of("off"), prefix);
            }
        }
        if (action.equals("scope") && tokens.size() == 3 && server.chatGameRelayTopologyConfigured()) return complete(List.of("relay","local"), prefix);
        return List.of();
    }

    private String commandPlayerTarget(PlayerIdentity player) {
        if (player == null) return "";
        String name=clean(player.username);
        if(name.isBlank()) name=clean(player.displayName);
        if(name.isBlank()) return "";
        RemotePlayerRef remote=RemotePlayerRef.parse(player.uuid);
        return remote==null ? name : name+"@"+remote.serverId;
    }

    private void addGroupTargets(Collection<String> out, UUID uuid) {
        if(out==null || uuid==null || host.groupChats()==null || !host.groupChats().available()) return;
        for(GroupRoom room:host.groupChats().listRooms(uuid.toString(),50)) {
            if(room==null || !room.member) continue;
            if(room.name!=null && !room.name.isBlank()) out.add(room.name);
            if(room.id!=null && !room.id.isBlank()) out.add(room.id.length()<=8 ? room.id : room.id.substring(0,8));
        }
    }

    private boolean gameMatchesAction(Map<String,Object> g, String action) {
        if (g == null) return false;
        String type=String.valueOf(g.getOrDefault("type","")), status=String.valueOf(g.getOrDefault("status",""));
        boolean open=status.equals("open") || status.equals("ready");
        return switch(action) {
            case "vote" -> open && type.equals("poll");
            case "apply","withdraw" -> open && type.equals("recruitment");
            case "join" -> open && (type.equals("firstcome") || type.equals("lottery"));
            case "draw" -> open && type.equals("lottery");
            case "finish","close","autoend" -> open;
            default -> true;
        };
    }

    private Map<String,Object> findGameByToken(List<Map<String,Object>> games, String token) {
        String t=clean(token); Map<String,Object> found=null;
        for (Map<String,Object> g:games) { String id=String.valueOf(g.getOrDefault("id","")); if(id.equalsIgnoreCase(t)) return g; if(id.regionMatches(true,0,t,0,Math.min(t.length(),id.length()))) { if(found!=null) return null; found=g; } }
        return found;
    }

    private static List<String> complete(Collection<String> values, String prefix) {
        String p=clean(prefix).toLowerCase(Locale.ROOT); LinkedHashSet<String> out=new LinkedHashSet<>();
        for(String v:values){ String s=clean(v); if(!s.isBlank() && s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s); }
        return new ArrayList<>(out);
    }

    private String resolveGameId(WebChatServer server, Sender sender, String input) {
        String raw = clean(input);
        if (raw.isBlank() || server == null) return raw;
        List<Map<String,Object>> games = server.chatGameList(sender == null || sender.uuid() == null ? "" : sender.uuid().toString());
        String exact = "";
        for (Map<String,Object> game : games) {
            String id = String.valueOf(game.getOrDefault("id", ""));
            if (id.equalsIgnoreCase(raw)) return id;
            if (raw.length() <= id.length() && id.regionMatches(true, 0, raw, 0, raw.length())) {
                if (!exact.isBlank()) return raw; // ambiguous prefix; manager will return not found instead of selecting the wrong event.
                exact = id;
            }
        }
        return exact.isBlank() ? raw : exact;
    }

    private String shortGameId(Map<String,Object> game) {
        String id = String.valueOf(game == null ? "" : game.getOrDefault("id", ""));
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    private boolean canManageGame(Sender sender) {
        if (has(sender, "kwc.admin")) return true;
        if (sender == null || sender.uuid() == null) return false;
        WebChatServer server = webServer.get();
        return server != null && server.canManageChatGame(sender.uuid().toString());
    }

    private void sendGameStatus(Sender sender, Map<String,Object> game) {
        if (game == null || game.isEmpty()) { sender.send(msg("gameNone", "There are no events.")); return; }
        String type = clean(String.valueOf(game.getOrDefault("type", ""))).toLowerCase(Locale.ROOT);
        sender.send("[KWC Game] " + String.valueOf(game.getOrDefault("title", "")) + " [" + gameTypeText(type) + "]");
        if (type.equals("poll")) {
            sender.send(msg("gamePollVotes", "Votes: {count}", "count", String.valueOf(game.getOrDefault("voteCount", 0))) + " · " + gameStatusText(String.valueOf(game.getOrDefault("status", ""))));
            int selected = intValue(game.get("pollSelectedOption"));
            if (game.get("pollOptions") instanceof List<?> options) for (Object value : options) {
                if (!(value instanceof Map<?,?> option)) continue;
                int index = intValue(option.get("index"));
                String marker = selected == index ? "*" : " ";
                sender.send(marker + " [" + index + "] " + String.valueOf(option.get("label")) + " - " + String.valueOf(option.get("count")));
            }
            if ("open".equals(String.valueOf(game.get("status")))) sender.send("/kchat game vote " + shortGameId(game) + " <optionNumber>");
            sendGamePolicyStatus(sender, game);
            return;
        }
        if (type.equals("recruitment")) {
            sender.send(String.valueOf(game.getOrDefault("acceptedCount", 0)) + "/" + game.getOrDefault("maxParticipants", 0)
                    + " · " + msg("gameWaitingCount", "waiting: {count}", "count", String.valueOf(game.getOrDefault("waitingCount", 0)))
                    + " · " + gameStatusText(String.valueOf(game.getOrDefault("status", ""))));
            if (game.get("recruitmentRoles") instanceof List<?> roles) for (Object value : roles) {
                if (!(value instanceof Map<?,?> role)) continue;
                sender.send("- " + String.valueOf(role.get("name")) + ": " + String.valueOf(role.get("accepted")) + "/" + String.valueOf(role.get("capacity"))
                        + " (" + msg("gameWaitingCount", "waiting: {count}", "count", String.valueOf(role.get("waiting"))) + ")");
            }
            String myRole = String.valueOf(game.getOrDefault("recruitmentRole", ""));
            String myState = String.valueOf(game.getOrDefault("recruitmentState", ""));
            if (!myRole.isBlank()) {
                String extra = "waiting".equals(myState) ? " #" + game.getOrDefault("recruitmentQueuePosition", 0) : "";
                sender.send(msg("gameYourRecruitment", "Your application: {role} / {state}{extra}", "role", myRole, "state", recruitmentStateText(myState), "extra", extra));
            } else if ("open".equals(String.valueOf(game.get("status")))) sender.send("/kchat game apply " + shortGameId(game) + " <role>");
            sendGamePolicyStatus(sender, game);
            return;
        }
        List<?> participants = game.get("participants") instanceof List<?> list ? list : List.of();
        sender.send(participants.size() + "/" + game.getOrDefault("maxParticipants", 0) + " · " + msg("gameWinnerCount", "winners: {count}", "count", String.valueOf(game.getOrDefault("winnerCount", 0))) + " · " + gameStatusText(String.valueOf(game.getOrDefault("status", ""))));
        if ("completed".equals(String.valueOf(game.get("status")))) sender.send("[KWC Game] " + gameWinners(game));
        sendGamePolicyStatus(sender, game);
    }

    private void sendGamePolicyStatus(Sender sender, Map<String,Object> game) {
        if (game == null || game.isEmpty()) return;
        List<String> conditions = new ArrayList<>();
        String type = clean(String.valueOf(game.getOrDefault("type", ""))).toLowerCase(Locale.ROOT);
        if (Boolean.TRUE.equals(game.get("autoEndOnCapacity"))) {
            conditions.add(type.equals("recruitment")
                    ? msg("gameAutoEndRolesFull", "all role slots filled")
                    : msg("gameAutoEndCapacity", "capacity reached"));
        }
        int responses = intValue(game.get("autoEndResponseCount"));
        if (responses > 0) conditions.add(msg("gameAutoEndResponses", "{count} responses", "count", String.valueOf(responses)));
        long at = longValue(game.get("autoEndAt"));
        if (at > 0L) conditions.add(msg("gameAutoEndTime", "at {time}", "time", Instant.ofEpochMilli(at).toString()));
        sender.send(msg("gameAutoEndSummary", "Automatic end: {value}", "value", conditions.isEmpty() ? msg("gameAutoEndOff", "off") : String.join(" + ", conditions)));
        WebChatServer server = webServer.get();
        if (server != null && server.chatGameRelayTopologyConfigured()) {
            String scope = Boolean.TRUE.equals(game.get("relayAnnouncements")) ? msg("gameScopeRelay", "Relay") : msg("gameScopeLocal", "local only");
            sender.send(msg("gameScopeSummary", "Announcement scope: {scope}", "scope", scope));
        }
    }

    private void sendGameParticipants(Sender sender, Map<String,Object> game) {
        if (game == null || game.isEmpty()) { sender.send(msg("gameNone", "There is no current event.")); return; }
        String type = clean(String.valueOf(game.getOrDefault("type", ""))).toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        if (game.get("participants") instanceof List<?> participants) for (Object value : participants) {
            if (!(value instanceof Map<?,?> item)) continue;
            String label = LegacyText.stripColor(String.valueOf(item.get("label")));
            if (label == null || label.isBlank()) continue;
            if (type.equals("recruitment")) {
                String role = String.valueOf(item.get("role"));
                String state = String.valueOf(item.get("state"));
                String queue = "waiting".equals(state) ? " #" + String.valueOf(item.get("queuePosition")) : "";
                names.add(label + " - " + role + " [" + recruitmentStateText(state) + queue + "]");
            } else names.add(label);
        }
        sender.send("[KWC Game] " + game.getOrDefault("title", "") + " - " + gameListSummary(game));
        sender.send(names.isEmpty() ? msg("gameNoParticipants", "No participants yet.") : String.join(", ", names));
    }

    private String gameListSummary(Map<String,Object> game) {
        String type = clean(String.valueOf(game == null ? "" : game.getOrDefault("type", ""))).toLowerCase(Locale.ROOT);
        if (type.equals("poll")) return msg("gamePollVotes", "Votes: {count}", "count", String.valueOf(game.getOrDefault("voteCount", 0)));
        if (type.equals("recruitment")) return String.valueOf(game.getOrDefault("acceptedCount", 0)) + "/" + game.getOrDefault("maxParticipants", 0)
                + " + " + msg("gameWaitingCount", "waiting: {count}", "count", String.valueOf(game.getOrDefault("waitingCount", 0)));
        List<?> participants = game != null && game.get("participants") instanceof List<?> list ? list : List.of();
        return participants.size() + "/" + (game == null ? 0 : game.getOrDefault("maxParticipants", 0));
    }

    private String gameTypeText(String type) {
        return switch (clean(type).toLowerCase(Locale.ROOT)) {
            case "firstcome" -> msg("gameTypeFirstCome", "First come");
            case "lottery" -> msg("gameTypeLottery", "Lottery");
            case "poll" -> msg("gameTypePoll", "Poll");
            case "recruitment" -> msg("gameTypeRecruitment", "Recruitment");
            default -> clean(type);
        };
    }

    private String recruitmentStateText(String state) {
        return switch (clean(state).toLowerCase(Locale.ROOT)) {
            case "accepted" -> msg("gameRecruitmentStateAccepted", "accepted");
            case "waiting" -> msg("gameRecruitmentStateWaiting", "waiting");
            default -> clean(state);
        };
    }

    private static int intValue(Object value) {
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return 0; }
    }

    private String gameStatusText(String status) {
        return switch (clean(status).toLowerCase(Locale.ROOT)) {
            case "open" -> msg("gameStatusOpen", "Open");
            case "ready" -> msg("gameStatusReady", "Full");
            case "completed" -> msg("gameStatusCompleted", "Completed");
            case "closed" -> msg("gameStatusClosed", "Closed");
            default -> clean(status);
        };
    }

    private String gameWinners(Map<String,Object> game) {
        if (game == null) return msg("gameNoWinners", "No winners.");
        List<String> names = new ArrayList<>();
        if (game.get("winners") instanceof List<?> winners) for (Object item : winners) {
            if (item instanceof Map<?,?> map) {
                Object displayValue = map.containsKey("displayName") ? map.get("displayName") : map.get("label");
                Object usernameValue = map.containsKey("username") ? map.get("username") : "";
                String displayName = clean(String.valueOf(displayValue == null ? "" : displayValue));
                String username = clean(String.valueOf(usernameValue == null ? "" : usernameValue));
                if (displayName.isBlank()) displayName = username;
                if (displayName.isBlank()) continue;
                names.add(!username.isBlank() && !username.equalsIgnoreCase(displayName) ? displayName + " (" + username + ")" : displayName);
            }
        }
        return names.isEmpty() ? msg("gameNoWinners", "No winners.") : msg("gameWinners", "Winners: {names}", "names", String.join(", ", names));
    }

    private String gameError(String error) {
        return switch (clean(error)) {
            case "game_not_found" -> msg("gameNone", "There is no current event.");
            case "game_not_open" -> msg("gameNotOpen", "This event is not open.");
            case "game_full" -> msg("gameFull", "This event is full.");
            case "not_enough_participants" -> msg("gameNotEnough", "There are not enough participants to draw winners.");
            case "invalid_type" -> msg("gameInvalidType", "Event type must be firstcome, lottery, poll, or recruitment.");
            case "invalid_title" -> msg("gameInvalidTitle", "Enter an event title up to 80 characters.");
            case "invalid_participant_count" -> msg("gameInvalidParticipantCount", "Participant count must be at least 2.");
            case "invalid_winner_count" -> msg("gameInvalidWinnerCount", "Winner count must be between 1 and the participant count.");
            case "not_lottery" -> msg("gameNotLottery", "This event is not a lottery.");
            case "not_poll" -> msg("gameNotPoll", "This event is not a poll.");
            case "not_recruitment" -> msg("gameNotRecruitment", "This event is not recruitment.");
            case "vote_required" -> msg("gameVoteRequired", "Use the vote command for a poll.");
            case "role_required" -> msg("gameRoleRequired", "Choose a recruitment role.");
            case "invalid_poll_options" -> msg("gameInvalidPollOptions", "A poll needs at least two unique options.");
            case "invalid_poll_option" -> msg("gameInvalidPollOption", "Choose a valid poll option number.");
            case "invalid_recruitment_roles" -> msg("gameInvalidRecruitmentRoles", "Add at least one role as role:count.");
            case "invalid_recruitment_role" -> msg("gameInvalidRecruitmentRole", "Choose a valid recruitment role.");
            case "invalid_auto_end_count" -> msg("gameInvalidAutoEndCount", "Automatic-end response count must be between 0 and 500.");
            case "invalid_auto_end_time" -> msg("gameInvalidAutoEndTime", "Automatic-end time must be in the future or disabled.");
            case "not_applied" -> msg("gameNotApplied", "You have not applied to this recruitment event.");
            default -> msg("gameFailed", "Event action failed: {error}", "error", clean(error));
        };
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
        String targetId = clean(parts[0]);
        if (targetId.regionMatches(true, 0, "dm-", 0, 3)) return replyDm(sender, targetId.substring(3), body);
        if (targetId.regionMatches(true, 0, "group-", 0, 6)) return replyGroup(sender, targetId.substring(6), body);
        WebChatServer server = webServer.get();
        if (server != null && server.userChatBanned(sender.uuid().toString())) return fail(sender, msg("chatBanned", "You are not allowed to send KWC chat messages."));
        ChatMessage made = server == null ? null : server.publishReplyFromGame(
                sender.displayName(), sender.username(), sender.uuid().toString(), targetId, body, body);
        if (made == null) return fail(sender, msg("replyTargetNotFound", "The referenced message could not be found."));
        return 1;
    }

    private int replyDm(Sender sender, String rawId, String body) {
        ConfigValues c = host.configValues();
        if (c == null || !c.directMessageEnabled || !c.directMessageAllowGameSend || !has(sender, "kwc.dm"))
            return fail(sender, msg("replyTargetNotFound", "The referenced message could not be found."));
        long id = parseMessageId(rawId);
        String me = sender.uuid().toString();
        DirectMessageMessage original = id <= 0 ? null : host.directMessages().messageForUser(me, id);
        if (original == null) return fail(sender, msg("replyTargetNotFound", "The referenced message could not be found."));
        String otherUuid = host.directMessages().otherParticipantUuid(original.threadId, me);
        PlayerIdentity target = host.storage().findKnownPlayerByUuid(otherUuid);
        if (target == null) target = new PlayerIdentity(otherUuid, "", "");
        return dmSend(sender, me, target, body, original.id);
    }

    private int replyGroup(Sender sender, String rawId, String body) {
        ConfigValues c = host.configValues();
        if (c == null || !c.groupChatEnabled || !has(sender, "kwc.group"))
            return fail(sender, msg("replyTargetNotFound", "The referenced message could not be found."));
        long id = parseMessageId(rawId);
        String me = sender.uuid().toString();
        GroupMessage original = id <= 0 ? null : host.groupChats().messageForUser(me, id);
        if (original == null) return fail(sender, msg("replyTargetNotFound", "The referenced message could not be found."));
        GroupRoom room = findGroupRoom(me, original.roomId);
        if (room == null) return fail(sender, msg("replyTargetNotFound", "The referenced message could not be found."));
        return groupSend(sender, me, room, body, original.id);
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
        if (action.equals("delete")) return dmDelete(sender, me, rest);
        if (!c.directMessageAllowGameSend) return fail(sender, msg("dmGameSendDisabled", "Sending direct messages from game is disabled."));
        if (rest.isBlank()) return usage(sender, "/kchat dm <player> <message>");
        String[] send = raw.split("\\s+", 2);
        if (send.length < 2) return usage(sender, "/kchat dm <player> <message>");
        return dmSend(sender, me, send[0], send[1]);
    }

    private int dmSend(Sender sender, String me, String targetInput, String rawBody) {
        return dmSend(sender, me, findDmTarget(targetInput), rawBody, 0L);
    }

    private int dmSend(Sender sender, String me, PlayerIdentity target, String rawBody) {
        return dmSend(sender, me, target, rawBody, 0L);
    }

    private int dmSend(Sender sender, String me, PlayerIdentity target, String rawBody, long replyToId) {
        ConfigValues c = host.configValues();
        if (target == null || clean(target.uuid).isBlank()) return fail(sender, msg("dmPlayerNotFound", "Player not found. The player must have joined at least once."));
        RemotePlayerRef remote = RemotePlayerRef.parse(target.uuid);
        ServerRelay relay = host.serverRelay();
        if (remote != null && (relay == null || !relay.canRouteDirectMessage(remote.serverId))) return fail(sender, msg("dmRemoteServerUnavailable", "The target server is unavailable."));
        String raw = stripMessage(rawBody, c.directMessageMaxMessageLength);
        WebChatServer filterServer = webServer.get();
        if (filterServer != null && filterServer.userChatBanned(me)) return fail(sender, msg("chatBanned", "You are not allowed to send KWC chat messages."));
        if (filterServer != null && filterServer.directMessageBlocked(me, target.uuid)) return fail(sender, msg("dmBlocked", "Direct messaging with this user is blocked."));
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
                ? host.directMessages().send(me, target.uuid, body, replyToId)
                : host.directMessages().sendPendingRemote(me, target.uuid, body, relayId, remote.serverId, "", replyToId);
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
                    target.username, target.displayName, body, gameBody,
                    r.message == null ? "" : r.message.replyToRelayId,
                    r.message == null ? "" : r.message.replyToSender,
                    r.message == null ? "" : r.message.replyToPreview).whenComplete((delivery, error) -> {
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
                String line = msg("dmIncoming", "DM from {player}: {message}", "player", sender.displayName(), "message", gameBody);
                sendInteractiveWithReply(targetUuid, line, sender.displayName(),
                        msg("dmClickHint", "Click to write a DM to {player}", "player", sender.displayName()),
                        "/kchat dm " + dmCommandTarget(new PlayerIdentity(me, sender.username(), sender.displayName())) + " ",
                        gameBody, msg("privateReplyClickHint", "Click the message to reply"), "/kchat reply dm-" + messageId + " ",
                        r.message == null ? 0L : r.message.replyToId,
                        r.message == null ? "" : r.message.replyToRelayId,
                        r.message == null ? "" : r.message.replyToSender,
                        r.message == null ? "" : r.message.replyToPreview);
            } catch (IllegalArgumentException ignored) {}
        }
        sendInteractiveWithReply(sender,
                msg("dmSentEcho", "to: {player} {message}", "player", target.label(), "message", gameBody),
                target.label(), msg("dmClickHint", "Click to write a DM to {player}", "player", target.label()),
                "/kchat dm " + dmCommandTarget(target) + " ",
                gameBody, msg("privateReplyClickHint", "Click the message to reply"),
                "/kchat reply dm-" + messageId + " ",
                r.message == null ? 0L : r.message.replyToId,
                r.message == null ? "" : r.message.replyToRelayId,
                r.message == null ? "" : r.message.replyToSender,
                r.message == null ? "" : r.message.replyToPreview);
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
        for (DirectMessageMessage m : messages) {
            String senderName = me.equalsIgnoreCase(m.senderUuid) ? msg("dmMe", "me") : identityLabel(m.senderDisplayName, m.senderUsername, m.senderUuid);
            String line = "#" + m.id + " " + senderName + ": " + m.body;
            PlayerIdentity other = host.storage().findKnownPlayerByUuid(c.otherUuid);
            if (other == null) other = new PlayerIdentity(c.otherUuid, c.otherUsername, c.otherDisplayName);
            sendInteractiveWithReply(sender, line, senderName,
                    msg("dmClickHint", "Click to write a DM to {player}", "player", identityLabel(c.otherDisplayName, c.otherUsername, c.otherUuid)),
                    "/kchat dm " + dmCommandTarget(other) + " ",
                    m.body, msg("privateReplyClickHint", "Click the message to reply"), "/kchat reply dm-" + m.id + " ",
                    m.replyToId, m.replyToRelayId, m.replyToSender, m.replyToPreview);
        }
        sender.send(msg("dmReadNavHint", "Use /kchat dm prev for newer messages, /kchat dm next for older messages."));
        sender.send(msg("dmDeleteHint", "Delete one of your messages: /kchat dm delete <messageId>"));
        return 1;
    }
    private int dmDelete(Sender sender, String me, String rest) {
        long id; try { id = Long.parseLong(clean(rest).split("\\s+")[0]); } catch (Exception ex) { return fail(sender, msg("dmInvalidMessageId", "Invalid message id.")); }
        DirectMessageStore.DeletePlan plan = host.directMessages().deletePlan(me, id);
        if (!plan.ok) return fail(sender, msg("dmDeleteFailed", "Failed to delete message."));
        if (!plan.owner) return fail(sender, msg("dmDeleteNotOwner", "You can delete only direct messages that you sent."));
        ConfigValues deleteConfig = host.configValues();
        int deleteWindow = deleteConfig == null ? 0 : Math.max(0, deleteConfig.selfMessageDeleteWindowMinutes);
        if (deleteConfig == null || !deleteConfig.selfMessageDeleteEnabled || (deleteWindow > 0
                && (plan.createdAt <= 0L || System.currentTimeMillis() - plan.createdAt > deleteWindow * 60_000L))) {
            return fail(sender, msg("dmDeleteWindowExpired", "This message can no longer be deleted."));
        }
        if (!plan.targetServerId.isBlank()) {
            ServerRelay relay = host.serverRelay();
            if (relay == null || plan.relayId.isBlank() || !relay.canRouteDirectMessage(plan.targetServerId))
                return fail(sender, msg("dmDeleteRemoteUnavailable", "Remote server is unavailable; message was not deleted."));
            boolean remoteDeleted = false;
            try { remoteDeleted = relay.publishDirectMessageDelete(plan.targetServerId, me, plan.relayId).get(10, java.util.concurrent.TimeUnit.SECONDS); }
            catch (Exception ignored) {}
            if (!remoteDeleted) return fail(sender, msg("dmDeleteFailed", "Failed to delete message."));
        }
        DirectMessageStore.DeleteApplyResult deleted = host.directMessages().deleteOwnedMessage(me, id);
        if (deleted == null || !deleted.ok) return fail(sender, msg("dmDeleteFailed", "Failed to delete message."));
        WebChatServer current = webServer.get(); if (current != null) current.publishDirectMessageUpdate(me, plan.otherUuid, plan.threadId);
        return ok(sender, msg("dmDeleted", "Direct message deleted for both participants."));
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
            GroupCommandTargetResolver.Result target = resolveGroupCommandTarget(me, rest);
            if (target == null) return fail(sender, msg("groupRoomNotFound", "Group chat room not found or you are not a member."));
            if (target.remainder.isBlank()) return usage(sender, "/kchat group send <room> <message>");
            return groupSend(sender, me, target.room, target.remainder);
        }
        GroupCommandTargetResolver.Result target = resolveGroupCommandTarget(me, raw);
        if (target == null) return fail(sender, msg("groupRoomNotFound", "Group chat room not found or you are not a member."));
        if (target.remainder.isBlank()) return usage(sender, "/kchat group <room> <message>");
        return groupSend(sender, me, target.room, target.remainder);
    }
    private int groupSend(Sender sender, String me, GroupRoom room, String rawBody) {
        return groupSend(sender, me, room, rawBody, 0L);
    }

    private int groupSend(Sender sender, String me, GroupRoom room, String rawBody, long replyToId) {
        ConfigValues c = host.configValues(); String raw = stripMessage(rawBody, c == null ? 500 : c.groupChatMaxMessageLength);
        WebChatServer filterServer = webServer.get();
        if (filterServer != null && filterServer.userChatBanned(me)) return fail(sender, msg("chatBanned", "You are not allowed to send KWC chat messages."));
        if (filterServer != null) {
            ContentFilterResult filtered = filterServer.filterContent(raw, ContentFilterEngine.Scope.GROUP);
            if (filtered.blocked) return fail(sender, filterServer.contentFilterBlockedMessage(filtered));
            raw = filtered.message;
        }
        String body = host.applyMessageTokens(raw), gameBody = host.applyMessageTokensForGame(raw);
        if (body.isBlank()) return fail(sender, msg("groupEmpty", "Message is empty."));
        GroupChatStore.SendResult r = host.groupChats().send(me, room.id, body, replyToId);
        if (!r.ok) return fail(sender, msg("groupFailed", "Group chat failed: {error}", "error", r.error));
        WebChatServer s = webServer.get(); if (s != null) { s.publishGroupChatMessageUpdate(room.id, r.message); s.inspectAdminGroupAlert("group:" + (r.message == null ? 0L : r.message.id), sender.displayName(), "game", body); s.dispatchWebPushGroupMessage(me, sender.displayName(), room, r.message, room.id); }
        sendInteractiveWithReply(sender,
                msg("groupSentEcho", "to group {room}: {message}", "room", room.name, "message", gameBody),
                room.name, msg("groupClickHint", "Click to write to group {room}", "room", room.name),
                "/kchat group " + shortId(room.id) + " ",
                gameBody, msg("privateReplyClickHint", "Click the message to reply"),
                "/kchat reply group-" + (r.message == null ? 0L : r.message.id) + " ",
                r.message == null ? 0L : r.message.replyToId, "",
                r.message == null ? "" : r.message.replyToSender,
                r.message == null ? "" : r.message.replyToPreview);
        for (String member : host.groupChats().memberUuids(room.id)) {
            if (member == null || member.equalsIgnoreCase(me) || RemotePlayerRef.isRemote(member)) continue;
            try {
                UUID targetUuid = UUID.fromString(member);
                String line = msg("groupIncoming", "Group {room} from {player}: {message}", "room", room.name, "player", sender.displayName(), "message", gameBody);
                sendInteractiveWithReply(targetUuid, line, room.name,
                        msg("groupClickHint", "Click to write to group {room}", "room", room.name),
                        "/kchat group " + shortId(room.id) + " ",
                        gameBody, msg("privateReplyClickHint", "Click the message to reply"), "/kchat reply group-" + (r.message == null ? 0L : r.message.id) + " ",
                        r.message == null ? 0L : r.message.replyToId, "",
                        r.message == null ? "" : r.message.replyToSender,
                        r.message == null ? "" : r.message.replyToPreview);
            } catch (IllegalArgumentException ignored) {}
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
        GroupCommandTargetResolver.Result target = resolveGroupCommandTarget(me, rest);
        if (target == null) return fail(sender, msg("groupRoomNotFound", "Group chat room not found or you are not a member."));
        GroupRoom room = target.room;
        GroupReadCursor c = new GroupReadCursor();
        c.roomId = room.id; c.roomName = room.name;
        if (!target.remainder.isBlank()) c.pageSize = clamp(parseLimit(target.remainder.split("\\s+", 2)[0], 20), 1, 100);
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
        for (GroupMessage m : messages) {
            String senderName = me.equalsIgnoreCase(m.senderUuid) ? msg("dmMe", "me") : identityLabel(m.senderDisplayName, m.senderUsername, m.senderUuid);
            if ("member_join".equals(m.eventType) || "member_leave".equals(m.eventType)) {
                String key = "member_leave".equals(m.eventType) ? "groupMemberLeft" : "groupMemberJoined";
                String fallback = "member_leave".equals(m.eventType) ? "{player} left group {room}." : "{player} joined group {room}.";
                sender.send("#" + m.id + " " + msg(key, fallback, "player", senderName, "room", c.roomName));
                continue;
            }
            String line = "#" + m.id + " " + senderName + ": " + m.body;
            sendInteractiveWithReply(sender, line, senderName,
                    msg("groupClickHint", "Click to write to group {room}", "room", c.roomName),
                    "/kchat group " + shortId(c.roomId) + " ",
                    m.body, msg("privateReplyClickHint", "Click the message to reply"), "/kchat reply group-" + m.id + " ",
                    m.replyToId, "", m.replyToSender, m.replyToPreview);
        }
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

    private void sendInteractiveWithReply(Sender sender, String text, String senderTarget, String senderHover, String senderCommand,
                                          String replyTarget, String replyHover, String replyCommand,
                                          long replyToId, String replyToStableId, String replyToSender, String replyToPreview) {
        if (sender == null) return;
        WebChatServer server = webServer.get();
        if (server != null && sender.isPlayer() && sender.uuid() != null) {
            server.sendPrivateClickableGameMessage(List.of(sender.uuid()), privateMessageId(replyCommand),
                    text, replyTarget,
                    senderTarget, senderHover, senderCommand,
                    replyToId, replyToStableId, replyToSender, replyToPreview);
            return;
        }
        sender.send(text);
    }

    private void sendInteractiveWithReply(UUID targetUuid, String text, String senderTarget, String senderHover, String senderCommand,
                                          String replyTarget, String replyHover, String replyCommand,
                                          long replyToId, String replyToStableId, String replyToSender, String replyToPreview) {
        if (targetUuid == null) return;
        WebChatServer server = webServer.get();
        if (server != null) {
            server.sendPrivateClickableGameMessage(List.of(targetUuid), privateMessageId(replyCommand),
                    text, replyTarget,
                    senderTarget, senderHover, senderCommand,
                    replyToId, replyToStableId, replyToSender, replyToPreview);
            return;
        }
        sendInteractive(targetUuid, text, senderTarget, senderHover, senderCommand, replyTarget, replyHover, replyCommand);
    }

    private static String privateMessageId(String replyCommand) {
        String command = clean(replyCommand);
        String prefix = "/kchat reply ";
        if (!command.regionMatches(true, 0, prefix, 0, prefix.length())) return "";
        String tail = command.substring(prefix.length()).trim();
        int space = tail.indexOf(' ');
        return space < 0 ? tail : tail.substring(0, space);
    }

    private void sendInteractive(Sender sender, String text, String senderTarget, String senderHover, String senderCommand,
                                 String replyTarget, String replyHover, String replyCommand) {
        if (sender == null || !sender.isPlayer() || sender.uuid() == null || host.platformAdapter() == null) {
            if (sender != null) sender.send(text);
            return;
        }
        sendInteractive(sender.uuid(), text, senderTarget, senderHover, senderCommand, replyTarget, replyHover, replyCommand);
    }

    private void sendInteractive(UUID targetUuid, String text, String senderTarget, String senderHover, String senderCommand,
                                 String replyTarget, String replyHover, String replyCommand) {
        if (targetUuid == null || host.platformAdapter() == null) return;
        host.platformAdapter().sendInteractiveMessage(List.of(targetUuid), new PlatformGameMessage(
                text, true, senderTarget, senderHover, senderCommand, replyTarget, replyHover, replyCommand));
    }

    private static boolean parseOnOff(String value, boolean fallback) {
        String v=clean(value).toLowerCase(Locale.ROOT);
        if (Set.of("on","true","yes","1","enable","enabled").contains(v)) return true;
        if (Set.of("off","false","no","0","disable","disabled").contains(v)) return false;
        return fallback;
    }

    private static long parseAutoEndTime(String value) {
        String v=clean(value);
        if (v.equalsIgnoreCase("off") || v.equals("0")) return 0L;
        try { long n=Long.parseLong(v); return n > System.currentTimeMillis() ? n : -1L; } catch(Exception ignored) {}
        try { return Instant.parse(v).toEpochMilli(); } catch(DateTimeParseException ignored) {}
        try { return OffsetDateTime.parse(v).toInstant().toEpochMilli(); } catch(DateTimeParseException ignored) {}
        return -1L;
    }

    private static long longValue(Object value) { try { return Long.parseLong(String.valueOf(value)); } catch(Exception ignored) { return 0L; } }

    private static long parseMessageId(String value) {
        try { return Long.parseLong(clean(value)); } catch (Exception ignored) { return 0L; }
    }

    private static String dmCommandTarget(PlayerIdentity identity) {
        if (identity == null) return "";
        RemotePlayerRef remote = RemotePlayerRef.parse(identity.uuid);
        if (remote != null) return !clean(identity.username).isBlank() ? clean(identity.username) + "@" + remote.serverId : remote.key;
        return !clean(identity.username).isBlank() ? clean(identity.username) : clean(identity.uuid);
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
    private GroupCommandTargetResolver.Result resolveGroupCommandTarget(String userUuid, String input) {
        return GroupCommandTargetResolver.resolve(host.groupChats().listRooms(userUuid, 200), input);
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
