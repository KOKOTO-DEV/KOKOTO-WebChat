package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * ChatListener는 Bukkit event bus에서 게임 이벤트를 받아 KWC core 이벤트/메시지로 변환하는 listener다.
 * ChatListener listens on the Bukkit event bus and translates game events into KWC core events/messages.
 *
 * Paper/Bukkit main-thread 규칙을 지키고, HTTP/SQLite처럼 오래 걸릴 수 있는 작업을 이벤트 thread에서 직접 block하지 않도록 한다.
 * Respect Paper/Bukkit main-thread rules and avoid blocking event threads on potentially slow HTTP/SQLite work.
 */
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ChatListener implements Listener {
    private static final Pattern CUSTOM_EMOJI_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9+.-]):(?:emoji:)?([^:\\r\\n]{1,200}):");
    private final KokotoWebChatPlugin plugin;
    private final Map<AsyncPlayerChatEvent, String> originalChatMessages = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Event, String> originalPaperChatMessages = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<PlayerCommandPreprocessEvent, String> originalNativeWhisperCommands = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<UUID, CapturedDirectMessageCommand> originalDirectMessageCommands = new ConcurrentHashMap<>();
    private final boolean paperAsyncChatRegistered;

    public ChatListener(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
        this.paperAsyncChatRegistered = registerPaperAsyncChatHandlers();
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void captureOriginalDirectMessageCommand(PlayerCommandPreprocessEvent event) {
        if (event == null || event.getPlayer() == null) return;
        String message = event.getMessage();
        NativeWhisperCommand whisper = NativeWhisperCommand.parse(message);
        if (whisper != null) {
            String routedCommand = routedWhisperCommand(whisper);
            if (routedCommand != null) {
                // Minecraft's native whisper commands cannot address another server.
                // Rewrite an explicit name@server target into the normal KWC DM
                // command early enough that command preprocessors such as ImageEmojis
                // still see and transform the command body exactly once. If the
                // suffix names this server, remove only the suffix and preserve the
                // original local whisper command instead.
                event.setMessage(routedCommand);
                if (NativeWhisperCommand.parse(routedCommand) != null) {
                    originalNativeWhisperCommands.put(event, routedCommand);
                }
                captureOriginalKwcCommand(event.getPlayer(), routedCommand);
                return;
            }
            originalNativeWhisperCommands.put(event, message);
        }
        captureOriginalKwcCommand(event.getPlayer(), event.getMessage());
    }

    private String routedWhisperCommand(NativeWhisperCommand whisper) {
        if (whisper == null) return null;
        ServerRelay relay = plugin.serverRelay();
        ConfigValues config = plugin.configValues();
        String localServerId = relay != null
                ? relay.serverId()
                : (config == null ? "" : config.serverRelayServerId);
        return whisper.routedCommand(localServerId);
    }

    private void captureOriginalKwcCommand(Player player, String message) {
        if (player == null || !looksLikeDirectMessageCommand(message)) return;
        // Command preprocessors from emoji plugins may replace :pack/name: with a
        // private-use font glyph before the command executor sees the args. Keep
        // the raw player command so /kchat dm stores the same token the user typed.
        // DM command handling later requires this capture; commands dispatched through
        // /execute, command blocks, console, or plugins do not pass this player-input check.
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        originalDirectMessageCommands.compute(playerId, (ignored, existing) -> {
            // ImageEmojis-style command preprocessors can re-dispatch the same command
            // after replacing :pack/name: with a generated font glyph. Preserve the
            // earliest player-typed form for a very short window so the nested command
            // cannot overwrite the token text that must be stored for web rendering.
            if (existing != null
                    && now - existing.createdAtMs <= 1_000L
                    && sameDirectMessageRoute(existing.message, message)
                    && containsEmojiTokenText(existing.message)
                    && !existing.message.equals(message)) {
                return existing;
            }
            return new CapturedDirectMessageCommand(message, now);
        });
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void discardCancelledDirectMessageCommand(PlayerCommandPreprocessEvent event) {
        if (event == null || event.getPlayer() == null || !event.isCancelled()) return;
        originalNativeWhisperCommands.remove(event);

        UUID playerId = event.getPlayer().getUniqueId();
        CapturedDirectMessageCommand captured = originalDirectMessageCommands.get(playerId);
        String processed = event.getMessage();
        // Some emoji preprocessors cancel the original event after replacing tokens
        // and then re-dispatch the transformed command. Retain the captured raw form
        // only when the cancellation clearly belongs to that same transformed route.
        if (captured != null
                && System.currentTimeMillis() - captured.createdAtMs <= 1_000L
                && sameDirectMessageRoute(captured.message, processed)
                && containsEmojiTokenText(captured.message)
                && !captured.message.equals(processed)) {
            return;
        }
        originalDirectMessageCommands.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mirrorNativeWhisperToWebDm(PlayerCommandPreprocessEvent event) {
        if (event == null || event.getPlayer() == null) return;
        String original = originalNativeWhisperCommands.remove(event);
        ConfigValues config = plugin.configValues();
        if (config == null || !config.directMessageEnabled || !config.directMessageCaptureGameWhispers) return;
        NativeWhisperCommand whisper = NativeWhisperCommand.parse(original == null ? event.getMessage() : original);
        if (whisper == null) return;

        Player player = event.getPlayer();
        String senderUuid = player.getUniqueId().toString();
        String senderDisplay = plugin.displayPlayerName(player);
        String senderName = player.getName();
        plugin.getServer().getScheduler().runTask(plugin, () -> mirrorNativeWhisper(
                senderUuid, senderName, senderDisplay, whisper.target, whisper.message));
    }

    private void mirrorNativeWhisper(String senderUuid, String senderName, String senderDisplay,
                                     String targetInput, String rawMessage) {
        ConfigValues config = plugin.configValues();
        DirectMessageStore store = plugin.directMessages();
        if (config == null || !config.directMessageEnabled || !config.directMessageCaptureGameWhispers
                || store == null || !store.available()) return;
        PlayerIdentity target = findLocalWhisperTarget(targetInput);
        if (target == null || target.uuid == null || target.uuid.isBlank()) return;
        if (target.uuid.equalsIgnoreCase(senderUuid)) return;

        String message = String.valueOf(rawMessage == null ? "" : rawMessage)
                .replace('\n', ' ').replace('\r', ' ').trim();
        message = plugin.applyMessageTokens(message);
        int max = Math.max(0, config.directMessageMaxMessageLength);
        if (max > 0 && message.length() > max) message = message.substring(0, max);
        if (message.isBlank()) return;

        plugin.storage().updateLastDisplayName(senderUuid, senderName, senderDisplay);
        DirectMessageStore.SendResult result = store.send(senderUuid, target.uuid, message);
        if (!result.ok) return;
        WebChatServer server = plugin.webServer();
        if (server == null) return;
        String threadId = result.thread == null ? "" : result.thread.id;
        long messageId = result.message == null ? 0L : result.message.id;
        server.publishDirectMessageUpdate(senderUuid, target.uuid, threadId);
        server.dispatchWebPushDirectMessage(senderUuid, senderDisplay, target.uuid, target.label(), threadId, messageId, message);
    }

    private PlayerIdentity findLocalWhisperTarget(String rawInput) {
        String input = String.valueOf(rawInput == null ? "" : rawInput).trim();
        if (input.isBlank()) return null;

        // The native command was resolved by this Minecraft server. Prefer the
        // online local player and never let a remembered remote identity with the
        // same UUID/name replace that local recipient in the mirrored DM thread.
        Player online = plugin.getServer().getPlayerExact(input);
        if (online != null) {
            String uuid = online.getUniqueId().toString();
            String display = plugin.displayPlayerName(online);
            plugin.storage().updateLastDisplayName(uuid, online.getName(), display);
            return new PlayerIdentity(uuid, online.getName(), display);
        }

        PlayerIdentity displayMatch = null;
        String plainInput = plainPlayerName(input);
        for (PlayerIdentity candidate : plugin.storage().listKnownPlayers("", 0)) {
            if (candidate == null || candidate.uuid == null || candidate.uuid.isBlank()) continue;
            if (RemotePlayerRef.isRemote(candidate.uuid)) continue;
            String username = String.valueOf(candidate.username == null ? "" : candidate.username).trim();
            String displayName = String.valueOf(candidate.displayName == null ? "" : candidate.displayName).trim();
            if (username.equalsIgnoreCase(input)) return candidate;
            if (displayMatch == null && displayName.equalsIgnoreCase(input)) displayMatch = candidate;
            if (displayMatch == null && !plainInput.isBlank()
                    && plainPlayerName(displayName).equalsIgnoreCase(plainInput)) displayMatch = candidate;
        }
        return displayMatch;
    }

    private String plainPlayerName(String value) {
        String text = String.valueOf(value == null ? "" : value);
        text = text.replaceAll("(?i)[§&]x(?:[§&][0-9a-f]){6}", "");
        text = text.replaceAll("(?i)&#[0-9a-f]{6}", "");
        text = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
        return String.valueOf(text == null ? "" : text).trim();
    }

    public String pollOriginalDirectMessageCommand(Player player) {
        if (player == null) return null;
        CapturedDirectMessageCommand captured = originalDirectMessageCommands.remove(player.getUniqueId());
        if (captured == null || captured.message == null || captured.message.isBlank()) return null;
        // A captured player command should be consumed immediately by the matching
        // command executor. Drop stale entries so they cannot be reused by a later
        // forced dispatch that only impersonates the Player sender.
        if (System.currentTimeMillis() - captured.createdAtMs > 5_000L) return null;
        return captured.message;
    }

    private boolean looksLikeDirectMessageCommand(String raw) {
        String text = String.valueOf(raw == null ? "" : raw).trim();
        if (!text.startsWith("/")) return false;
        String[] parts = text.substring(1).split("\\s+", 3);
        if (parts.length < 2) return false;
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
        return isKwcRoot(root) && (sub.equals("dm") || sub.equals("reply") || sub.equals("group") || sub.equals("gc"));
    }

    private boolean isKwcRoot(String root) {
        if (root == null) return false;
        return root.equals("kchat")
                || root.equals("kc")
                || root.equals("kwc")
                || root.equals("kokoto-webchat:kchat")
                || root.equals("kokoto-webchat:kc")
                || root.equals("kokoto-webchat:kwc");
    }


    private boolean sameDirectMessageRoute(String first, String second) {
        String a = directMessageRouteKey(first);
        String b = directMessageRouteKey(second);
        return !a.isBlank() && a.equalsIgnoreCase(b);
    }

    private String directMessageRouteKey(String raw) {
        String text = String.valueOf(raw == null ? "" : raw).trim();
        if (!text.startsWith("/")) return "";
        String[] parts = text.substring(1).split("\\s+", 5);
        if (parts.length < 2) return "";
        String root = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (!isKwcRoot(root)) return "";
        String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("reply")) {
            return parts.length >= 3 ? "reply:" + parts[2] : "reply:";
        }
        if (sub.equals("dm")) {
            return parts.length >= 3 ? "dm:" + parts[2] : "dm:";
        }
        if (sub.equals("group") || sub.equals("gc")) {
            if (parts.length < 3) return "group:";
            if (parts[2].equalsIgnoreCase("send")) {
                return parts.length >= 4 ? "group:send:" + parts[3] : "group:send:";
            }
            return "group:" + parts[2];
        }
        return "";
    }

    private boolean containsEmojiTokenText(String raw) {
        String text = String.valueOf(raw == null ? "" : raw);
        return !text.isBlank() && CUSTOM_EMOJI_TOKEN_PATTERN.matcher(text).find();
    }

    private static class CapturedDirectMessageCommand {
        final String message;
        final long createdAtMs;

        CapturedDirectMessageCommand(String message, long createdAtMs) {
            this.message = message;
            this.createdAtMs = createdAtMs;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void captureOriginalChatMessage(AsyncPlayerChatEvent event) {
        if (paperAsyncChatRegistered) return;
        // Some chat/emoji plugins replace the message later in the same event.
        // Keep the player's original template text so the web side receives
        // :pack/name: tokens instead of a later ImageEmojis font symbol.
        if (event != null) {
            String original = event.getMessage();
            WebChatServer server = plugin.webServer();
            if (server != null) {
                ContentFilterResult filtered = server.filterContent(original, ContentFilterEngine.Scope.PUBLIC);
                if (filtered.blocked) {
                    event.setCancelled(true);
                    if (event.getPlayer() != null) event.getPlayer().sendMessage(ChatColor.RED + server.contentFilterBlockedMessage(filtered));
                    return;
                }
                original = filtered.message;
            }
            originalChatMessages.put(event, original);
            rememberDiscordSrvGameOrigin(event.getPlayer(), original);
            String transformed = plugin.applyMessageTokens(original);
            if (!transformed.equals(event.getMessage())) event.setMessage(transformed);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (paperAsyncChatRegistered) return;
        String message = originalChatMessages.remove(event);
        if (message == null && event != null) message = event.getMessage();
        if (event == null || event.getPlayer() == null) return;

        ConfigValues config = plugin.configValues();
        WebChatServer server = plugin.webServer();
        boolean tokenMultiline = hasConfiguredGameLineBreak(message);
        boolean interactive = config != null && config.replyGameClickEnabled
                && config.replyGameClickLocalChat && config.broadcastIngameChatToWeb && server != null;
        if (!interactive && !tokenMultiline) {
            publishCapturedGameChat(event.getPlayer(), message);
            return;
        }

        ChatMessage msg = publishCapturedGameChat(event.getPlayer(), message);
        if (msg == null && tokenMultiline) msg = transientTokenGameMessage(event.getPlayer(), message);
        if (msg == null || server == null) return;
        String gameMessage = String.valueOf(event.getMessage() == null ? "" : event.getMessage());
        String line;
        try {
            line = String.format(event.getFormat(), event.getPlayer().getDisplayName(), gameMessage);
        } catch (Throwable ignored) {
            line = "<" + event.getPlayer().getDisplayName() + "> " + gameMessage;
        }
        List<java.util.UUID> recipients = new ArrayList<>();
        for (Player recipient : event.getRecipients()) {
            if (recipient != null) recipients.add(recipient.getUniqueId());
        }
        // Keep the native asynchronous chat event alive for console/non-player output
        // so the server log stays on the normal Async Chat Thread. Players receive the
        // interactive KWC component separately and are removed from the native route
        // to prevent duplicate lines.
        event.getRecipients().clear();
        ChatMessage finalMsg = msg;
        String finalLine = line;
        plugin.platformAdapter().runMainThread(
                () -> server.broadcastClickableLocalGameMessage(finalMsg, finalLine, gameMessage, recipients));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        // Nickname plugins often apply the final displayName after the join event.
        // Refresh once shortly after join so offline web chat can use the latest
        // in-game display name even before the player sends a chat message.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> rememberDisplayName(player), 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> rememberDisplayName(player), 60L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> notifyUnreadDirectMessages(player), 80L);
    }

    @SuppressWarnings("unchecked")
    private boolean registerPaperAsyncChatHandlers() {
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class
                    .forName("io.papermc.paper.event.player.AsyncChatEvent")
                    .asSubclass(Event.class);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.LOWEST, (listener, event) -> {
                try {
                    capturePaperOriginalChatMessage(event);
                } catch (Throwable ignored) {
                }
            }, plugin, false);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.HIGHEST, (listener, event) -> {
                try {
                    onPaperChat(event);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to relay Paper chat to web chat: " + t.getMessage());
                }
            }, plugin, true);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to register Paper AsyncChatEvent listener; falling back to legacy chat event: " + t.getMessage());
            return false;
        }
    }

    private void capturePaperOriginalChatMessage(Event event) {
        if (event == null) return;
        String message = paperPlainText(callNoArg(event, "originalMessage"));
        if (message.isBlank()) message = paperPlainText(callNoArg(event, "message"));
        if (!message.isBlank()) {
            WebChatServer server = plugin.webServer();
            if (server != null) {
                ContentFilterResult filtered = server.filterContent(message, ContentFilterEngine.Scope.PUBLIC);
                if (filtered.blocked) {
                    try {
                        if (event instanceof org.bukkit.event.Cancellable cancellable) cancellable.setCancelled(true);
                    } catch (Throwable ignored) {}
                    Player player = paperPlayer(event);
                    if (player != null) player.sendMessage(ChatColor.RED + server.contentFilterBlockedMessage(filtered));
                    return;
                }
                message = filtered.message;
                if (!message.equals(paperPlainText(callNoArg(event, "message")))) setPaperMessageText(event, message);
            }
            originalPaperChatMessages.put(event, message);
            rememberDiscordSrvGameOrigin(paperPlayer(event), message);
            applyPaperMessageTokens(event);
        }
    }


    private void setPaperMessageText(Event event, String text) {
        if (event == null) return;
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Object replacement = componentClass.getMethod("text", String.class).invoke(null, String.valueOf(text == null ? "" : text));
            event.getClass().getMethod("message", componentClass).invoke(event, replacement);
        } catch (Throwable ignored) {}
    }

    private void applyPaperMessageTokens(Event event) {
        if (event == null) return;
        try {
            Object current = callNoArg(event, "message");
            String plain = paperPlainText(current);
            String transformed = plugin.applyMessageTokens(plain);
            if (plain.equals(transformed)) return;
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Object replacement = componentClass.getMethod("text", String.class).invoke(null, transformed);
            event.getClass().getMethod("message", componentClass).invoke(event, replacement);
        } catch (Throwable ignored) {
        }
    }

    private void onPaperChat(Event event) {
        if (event == null) return;
        String original = originalPaperChatMessages.remove(event);
        if (original == null || original.isBlank()) original = paperPlainText(callNoArg(event, "originalMessage"));
        if (original == null || original.isBlank()) original = paperPlainText(callNoArg(event, "message"));
        Player player = paperPlayer(event);

        ConfigValues config = plugin.configValues();
        WebChatServer server = plugin.webServer();
        boolean tokenMultiline = hasConfiguredGameLineBreak(original);
        boolean interactive = config != null && config.replyGameClickEnabled
                && config.replyGameClickLocalChat && config.broadcastIngameChatToWeb && server != null;
        if (!interactive && !tokenMultiline) {
            publishCapturedGameChat(player, original);
            return;
        }

        ChatMessage msg = publishCapturedGameChat(player, original);
        if (msg == null && tokenMultiline) msg = transientTokenGameMessage(player, original);
        if (msg == null || player == null || server == null) return;
        String gameMessage = paperPlainText(callNoArg(event, "message"));
        if (gameMessage.isBlank()) gameMessage = original;
        String line = "<" + player.getDisplayName() + "> " + gameMessage;
        List<java.util.UUID> recipients = new ArrayList<>();
        for (Player recipient : paperPlayerViewers(event)) {
            if (recipient != null) recipients.add(recipient.getUniqueId());
        }
        removePaperPlayerViewers(event);
        ChatMessage finalMsg = msg;
        String finalGameMessage = gameMessage;
        plugin.platformAdapter().runMainThread(
                () -> server.broadcastClickableLocalGameMessage(finalMsg, line, finalGameMessage, recipients));
    }

    private void removePaperPlayerViewers(Event event) {
        Object viewers = callNoArg(event, "viewers");
        if (!(viewers instanceof Collection<?> collection)) return;
        try {
            collection.removeIf(viewer -> viewer instanceof Player);
        } catch (Throwable ignored) {
        }
    }

    private List<Player> paperPlayerViewers(Event event) {
        Object viewers = callNoArg(event, "viewers");
        List<Player> players = new ArrayList<>();
        if (viewers instanceof Collection<?> collection) {
            for (Object viewer : collection) if (viewer instanceof Player player) players.add(player);
            return players;
        }
        players.addAll(plugin.getServer().getOnlinePlayers());
        return players;
    }

    private void rememberDiscordSrvGameOrigin(Player player, String message) {
        if (player == null || message == null || message.isBlank()) return;
        DiscordBridge bridge = plugin.discordBridge();
        if (bridge == null) return;
        bridge.rememberGameChatOriginBeforeDiscordSrv(plugin.displayPlayerName(player), player.getName(), message);
    }

    private boolean hasConfiguredGameLineBreak(String message) {
        String protectedText = plugin.applyMessageTokensForGame(String.valueOf(message == null ? "" : message));
        return plugin.splitMessageTokenGameLines(protectedText).size() > 1;
    }

    private ChatMessage transientTokenGameMessage(Player player, String message) {
        if (player == null) return null;
        String raw = String.valueOf(message == null ? "" : message);
        String displayName = plugin.displayPlayerName(player);
        return new ChatMessage(System.currentTimeMillis(), "game", displayName, "USER", plugin.applyMessageTokens(raw))
                .withGameMessage(plugin.applyMessageTokensForGame(raw))
                .withRealSender(player.getName(), player.getUniqueId().toString());
    }

    private ChatMessage publishCapturedGameChat(Player player, String message) {
        if (!plugin.configValues().broadcastIngameChatToWeb) return null;
        WebChatServer server = plugin.webServer();
        if (server == null) return null;
        return server.publishFromGame(plugin.displayPlayerName(player), player.getName(),
                player.getUniqueId().toString(), message == null ? "" : message);
    }


    private void notifyUnreadDirectMessages(Player player) {
        if (player == null || !player.isOnline()) return;
        ConfigValues config = plugin.configValues();
        if (config == null || !config.directMessageEnabled || !config.directMessageNotifyOnLogin) return;
        DirectMessageStore store = plugin.directMessages();
        if (store == null || !store.available()) return;
        int unread = store.unreadCount(player.getUniqueId().toString());
        if (unread <= 0) return;
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("count", Integer.toString(unread));
        player.sendMessage(ChatColor.LIGHT_PURPLE + plugin.langManager().text("command.dmUnreadNotice", "You have {count} unread direct message(s). Open the web chat message box or use /kchat dm list.", vars));
    }

    private Player paperPlayer(Event event) {
        Object value = callNoArg(event, "getPlayer");
        if (!(value instanceof Player)) value = callNoArg(event, "player");
        return value instanceof Player player ? player : null;
    }

    private Object callNoArg(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String paperPlainText(Object component) {
        if (component == null) return "";
        if (component instanceof String text) return text;
        try {
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object serializer = serializerClass.getMethod("plainText").invoke(null);
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Object text = serializer.getClass().getMethod("serialize", componentClass).invoke(serializer, component);
            return text == null ? "" : String.valueOf(text);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void rememberDisplayName(Player player) {
        if (player == null || !player.isOnline()) return;
        String uuid = player.getUniqueId().toString();
        String displayName = plugin.displayPlayerName(player);
        if (displayName == null || displayName.isBlank()) return;
        plugin.storage().updateLastDisplayName(uuid, player.getName(), displayName);
    }
}
