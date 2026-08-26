package dev.kokoto.webchat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DiscordBridge implements WebChatDiscord {
    private final KokotoWebChatPlugin plugin;
    private Object jda;
    private Object jdaListener;
    private Thread jdaRetryThread;
    private volatile boolean started;
    private final Map<String, Long> recentDiscordInbound = new ConcurrentHashMap<>();
    private final Map<String, Long> recentDiscordEventIds = new ConcurrentHashMap<>();
    private final Map<String, Long> recentDirectDiscordOutbound = new ConcurrentHashMap<>();
    // Only the server where a Minecraft chat event actually originated records the
    // raw game message here. Every KWC instance connected to the same Discord
    // channel receives the resulting DiscordSRV bot message through JDA, so this
    // origin fingerprint prevents non-origin servers from prepending their own
    // server label or appending the same emoji links again.
    private final Map<String, LocalGameOutbound> recentLocalGameOutbound = new ConcurrentHashMap<>();
    private static final Map<String, Long> globalDiscordInboundEventIds = new ConcurrentHashMap<>();

    public DiscordBridge(KokotoWebChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        ConfigValues c = plugin.configValues();
        // Normal Discord chat relay and administrator keyword alerts are independent.
        // Either feature may reuse DiscordSRV's authenticated bot/channel mapping.
        if (!c.discordEnabled && !c.adminDiscordAlertsEnabled) return;

        Plugin discordSrv = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discordSrv == null || !discordSrv.isEnabled()) {
            plugin.getLogger().warning("Discord integration is enabled, but DiscordSRV is not loaded. Discord bridge disabled.");
            return;
        }

        started = true;

        if (needsJdaListener(c)) {
            startJdaListenerRetry();
        }

        plugin.getLogger().info("DiscordSRV integration enabled. channel=" + c.discordChannel
                + ", webToDiscord=" + c.discordWebToDiscord
                + ", gameRelayMode=" + c.discordGameRelayMode
                + ", discordToWeb=" + c.discordDiscordToWeb
                + ", adminAlerts=" + c.adminDiscordAlertsEnabled);
    }

    public void stop() {
        if (jdaRetryThread != null) {
            jdaRetryThread.interrupt();
            jdaRetryThread = null;
        }
        if (jda != null && jdaListener != null) {
            try {
                Method remove = jda.getClass().getMethod("removeEventListener", Object[].class);
                remove.invoke(jda, new Object[]{new Object[]{jdaListener}});
            } catch (Throwable ignored) {
            }
        }
        jda = null;
        jdaListener = null;
        recentDiscordInbound.clear();
        recentDiscordEventIds.clear();
        recentDirectDiscordOutbound.clear();
        recentLocalGameOutbound.clear();
        started = false;
    }

    public void sendWebMessage(ChatMessage msg) {
        ConfigValues c = plugin.configValues();
        if (!c.discordEnabled || !c.discordWebToDiscord) return;

        boolean allow;
        if ("guest".equalsIgnoreCase(msg.source)) {
            allow = c.discordSendWebGuest;
        } else if ("ADMIN".equalsIgnoreCase(msg.role)) {
            allow = c.discordSendWebAdmin;
        } else {
            allow = c.discordSendWebUser;
        }
        if (!allow) return;

        String text = formatMessage(c.discordWebToDiscordFormat, msg, c.discordChannel);
        if (c.discordReplyRelayEnabled) {
            text = applyReplyRelayToDiscord(msg, text, c);
        }
        text = appendWebEmojiLinksToDiscord(msg, text, c);
        if (text.isBlank()) return;

        sendDirectToDiscord(text);
    }


    public void sendGameMessage(ChatMessage msg) {
        ConfigValues c = plugin.configValues();
        if (msg == null || c == null || !c.discordEnabled) return;

        // This method is invoked only for a game chat event created on this server.
        // Relayed messages use WebChatServer.acceptRelayedMessage() and never enter
        // this path, so the fingerprint identifies the one server allowed to edit
        // DiscordSRV's native game-chat post.
        rememberLocalGameOutbound(msg);
        if (!isKwcGameRelay(c)) return;

        String text = formatMessage(c.discordGameRelayFormat, msg, c.discordChannel);
        text = appendGameEmojiLinksToDiscord(msg, text, c);
        if (text.isBlank()) return;

        sendDirectToDiscord(text);
    }


    /**
     * Record a local Minecraft chat fingerprint early enough to identify the
     * originating server when DiscordSRV posts the message. This capture is used
     * only for origin matching. Emoji conversion never uses a game-rendered glyph
     * or this captured copy; the Discord message's actual :emoji: token text is
     * passed through the same KWC token-to-link logic used by web -> Discord.
     */
    public void rememberGameChatOriginBeforeDiscordSrv(String displaySender, String realSender, String rawMessage) {
        ConfigValues c = plugin.configValues();
        if (c == null || !c.discordEnabled || !isDiscordSrvGameRelay(c)) return;
        rememberLocalGameOutbound("early", displaySender, realSender, rawMessage);
    }

    private void rememberLocalGameOutbound(ChatMessage msg) {
        if (msg == null) return;
        rememberLocalGameOutbound(safeText(msg.id), msg.sender, msg.realSender, msg.message);
    }

    private void rememberLocalGameOutbound(String keyPrefix, String displaySenderRaw, String realSenderRaw,
                                           String messageRaw) {
        String message = normalizeEcho(messageRaw);
        String displaySender = normalizeEcho(displaySenderRaw);
        String realSender = normalizeEcho(realSenderRaw);
        if (message.isBlank() && displaySender.isBlank() && realSender.isBlank()) return;

        long now = System.currentTimeMillis();
        String key = safeText(keyPrefix);
        if (key.isBlank() || "early".equals(key)) {
            key = (key.isBlank() ? "game" : key) + ":" + now + ":" + displaySender + ":" + realSender
                    + ":" + Integer.toHexString(message.hashCode());
        }
        recentLocalGameOutbound.put(key, new LocalGameOutbound(now, displaySender, realSender, message));
        pruneRecent(now, 60_000L);
    }

    private LocalGameOutbound findRecentLocalGameOutbound(String discordContent, long ttlMillis) {
        String content = normalizeEcho(discordContent);
        if (content.isBlank()) return null;
        long now = System.currentTimeMillis();
        recentLocalGameOutbound.entrySet().removeIf(e -> now - e.getValue().time > ttlMillis);

        LocalGameOutbound best = null;
        int bestScore = -1;
        for (LocalGameOutbound item : recentLocalGameOutbound.values()) {
            long age = now - item.time;
            if (age > ttlMillis) continue;

            boolean displayMatch = !item.displaySender.isBlank() && content.contains(item.displaySender);
            boolean realMatch = !item.realSender.isBlank() && content.contains(item.realSender);
            boolean senderMatch = displayMatch || realMatch;
            boolean messageMatch = !item.normalizedMessage.isBlank() && content.contains(item.normalizedMessage);

            int score = -1;
            if (senderMatch && messageMatch) score = 4;
            else if (messageMatch && item.normalizedMessage.length() >= 2) score = 3;

            if (score > bestScore || (score == bestScore && best != null && item.time > best.time)) {
                best = item;
                bestScore = score;
            }
        }
        return bestScore >= 0 ? best : null;
    }

    private boolean needsJdaListener(ConfigValues c) {
        if (c == null || !c.discordEnabled) return false;
        if (c.discordDiscordToWeb) return true;
        if (!isDiscordSrvGameRelay(c)) return false;
        if (c.serverRelayEnabled) return true;
        return c.discordAppendGameEmojiLinks && c.discordMaxEmojiLinksPerMessage > 0;
    }

    private boolean isDiscordSrvGameRelay(ConfigValues c) {
        return c == null || !"kwc".equalsIgnoreCase(safeText(c.discordGameRelayMode));
    }

    private boolean isKwcGameRelay(ConfigValues c) {
        return c != null && "kwc".equalsIgnoreCase(safeText(c.discordGameRelayMode));
    }

    private void startJdaListenerRetry() {
        if (jdaRetryThread != null && jdaRetryThread.isAlive()) return;

        jdaRetryThread = new Thread(() -> {
            int attempt = 0;
            while (started && !Thread.currentThread().isInterrupted()) {
                attempt++;
                try {
                    installJdaListener();
                    plugin.getLogger().info("DiscordSRV/JDA listener installed for Discord -> web chat. attempt=" + attempt);
                    return;
                } catch (Throwable t) {
                    if (attempt == 1 || attempt % 6 == 0) {
                        plugin.getLogger().warning("DiscordSRV/JDA listener is not ready yet; retrying. attempt="
                                + attempt + ", reason=" + t.getMessage());
                    }
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "KOKOTO WebChat-DiscordSRV-JDA-Retry");
        jdaRetryThread.setDaemon(true);
        jdaRetryThread.start();
    }

    private void installJdaListener() throws Exception {
        if (jda != null && jdaListener != null) return;

        Object jdaObject = getJda();
        if (jdaObject == null) {
            throw new IllegalStateException("DiscordSRV JDA is not ready");
        }

        ClassLoader cl = jdaObject.getClass().getClassLoader();
        Class<?> listenerClass = findClass(cl,
                "github.scarsz.discordsrv.dependencies.jda.api.hooks.EventListener",
                "net.dv8tion.jda.api.hooks.EventListener"
        );

        InvocationHandler handler = (proxy, method, args) -> {
            if ("onEvent".equals(method.getName()) && args != null && args.length == 1) {
                handleJdaEvent(args[0]);
            }
            return null;
        };

        Object listener = Proxy.newProxyInstance(cl, new Class[]{listenerClass}, handler);
        Method add = jdaObject.getClass().getMethod("addEventListener", Object[].class);
        add.invoke(jdaObject, new Object[]{new Object[]{listener}});

        this.jda = jdaObject;
        this.jdaListener = listener;
    }

    private Class<?> findClass(ClassLoader cl, String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return Class.forName(name, false, cl);
            } catch (ClassNotFoundException ex) {
                last = ex;
            }
        }
        throw last == null ? new ClassNotFoundException("No class names supplied") : last;
    }


    private void handleJdaEvent(Object event) {
        try {
            ConfigValues c = plugin.configValues();
            if (!c.discordEnabled) return;

            String eventName = event.getClass().getName();
            if (!eventName.endsWith("MessageReceivedEvent")) return;

            Object channel = call(event, "getChannel");
            if (!isConfiguredChannel(channel)) return;

            Object author = call(event, "getAuthor");
            if (author == null) return;

            Object message = call(event, "getMessage");

            String eventId = callString(message, "getId");
            String content = firstNonBlank(callString(message, "getContentRaw"), callString(message, "getContentDisplay"));
            String channelId = callString(channel, "getId");
            String authorId = callString(author, "getId");
            String dedupeKey = !eventId.isBlank()
                    ? "id:" + eventId
                    : "fallback:" + channelId + ":" + authorId + ":" + normalizeEcho(content);
            if (!dedupeKey.isBlank() && (seenRecently(recentDiscordEventIds, dedupeKey, 60_000L)
                    || seenRecently(globalDiscordInboundEventIds, dedupeKey, 60_000L))) return;
            if (!dedupeKey.isBlank()) {
                recentDiscordEventIds.put(dedupeKey, System.currentTimeMillis());
                globalDiscordInboundEventIds.put(dedupeKey, System.currentTimeMillis());
            }
            maybeEnhanceNativeDiscordMessage(message, author, content, c);

            if (!c.discordDiscordToWeb) return;
            if (c.discordIgnoreBotMessages && asBoolean(call(author, "isBot"))) return;

            content = appendDiscordAttachmentUrls(content, message, 8);
            if (content.isBlank()) return;

            rememberDiscordInbound(content);

            String sender = discordSender(event, author);
            String webSender = format(c.discordToWebSenderFormat, sender, "DISCORD", "discord", content, c.discordChannel);
            String webMessage = format(c.discordToWebMessageFormat, sender, "DISCORD", "discord", content, c.discordChannel);

            Bukkit.getScheduler().runTask(plugin, () -> {
                WebChatServer server = plugin.webServer();
                if (server != null) {
                    server.publishFromDiscord(webSender, webMessage);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to relay Discord message to web chat: " + t.getMessage());
        }
    }


    private void maybeEnhanceNativeDiscordMessage(Object message, Object author, String content, ConfigValues c) {
        if (message == null || author == null || c == null || content == null || content.isBlank()) return;

        // Only edit messages authored by DiscordSRV's own JDA account. This avoids
        // touching unrelated bots which happen to post in the configured channel.
        if (!isDiscordSrvSelfUser(author)) return;

        // Web/game messages sent directly by BM Web Chat are already formatted and
        // remembered on the originating server. Skip them there. Other KWC
        // instances sharing the channel will not have this exact direct-outbound
        // fingerprint, so the local-game origin check below also keeps them from
        // modifying another server's direct message.
        if (seenRecently(recentDirectDiscordOutbound, normalizeEcho(content), 60_000L)) return;

        // The default KWC web format is "[server] [Web] ...". Every server in
        // the shared channel sees that same bot post, but it is already fully
        // formatted by its origin and must never be treated as a native game relay.
        if (looksLikeKwcWebOutbound(content)) return;

        // A DiscordSRV bot account is shared by every connected server. JDA alone
        // cannot tell which Minecraft server produced a native DiscordSRV message.
        // Match it against a recent local game event and allow only that origin
        // server to add the server label/emoji links. Relayed game messages are not
        // recorded here and therefore cannot acquire an extra transit-server label.
        if (!isDiscordSrvGameRelay(c)) return;
        LocalGameOutbound localGame = findRecentLocalGameOutbound(content, 15_000L);
        if (localGame == null) return;

        String updated = content.trim();
        String serverLabel = localRelayServerLabel(c);
        if (!serverLabel.isBlank() && !hasServerLabelPrefix(updated, serverLabel)) {
            updated = "[" + serverLabel + "] " + updated;
        }

        // DiscordSRV has already produced the Discord text. Treat any registered
        // :emoji: token in that actual Discord message exactly like web -> Discord:
        // scan the token text directly and append the same public KWC emoji links.
        // Do not use Minecraft glyphs or a game-rendered representation as input.
        updated = appendRegisteredEmojiLinksToDiscord(content, updated, c.discordAppendGameEmojiLinks, c);

        if (updated.equals(content.trim())) return;
        try {
            Object action;
            try {
                action = message.getClass().getMethod("editMessage", CharSequence.class).invoke(message, updated);
            } catch (NoSuchMethodException ex) {
                action = message.getClass().getMethod("editMessage", String.class).invoke(message, updated);
            }
            action.getClass().getMethod("queue").invoke(action);
        } catch (Throwable t) {
            plugin.getLogger().fine("Failed to add server label/emoji links to DiscordSRV relay message: " + t.getMessage());
        }
    }

    private boolean looksLikeKwcWebOutbound(String content) {
        String value = safeText(content);
        if (value.isBlank()) return false;
        // Accept both "[Web] ..." and "[Server] [Web] ...". Limit bracket
        // contents so arbitrary message text later in the line is not classified.
        return value.matches("(?i)^\\s*(?:\\[[^\\]\\r\\n]{1,96}\\]\\s*)?\\[web\\](?:\\s|$).*");
    }

    private boolean isDiscordSrvSelfUser(Object author) {
        if (author == null || !asBoolean(call(author, "isBot")) || jda == null) return false;
        Object selfUser = call(jda, "getSelfUser");
        String selfId = callString(selfUser, "getId");
        String authorId = callString(author, "getId");
        return !selfId.isBlank() && selfId.equals(authorId);
    }

    private boolean hasServerLabelPrefix(String text, String serverLabel) {
        String value = String.valueOf(text == null ? "" : text).trim();
        String label = safeText(serverLabel);
        return !label.isBlank() && value.regionMatches(true, 0, "[" + label + "]", 0, label.length() + 2);
    }

    private boolean containsAnyLine(String base, String lines) {
        String value = String.valueOf(base == null ? "" : base);
        String list = String.valueOf(lines == null ? "" : lines);
        for (String line : list.split("\\R")) {
            String item = line.trim();
            if (!item.isBlank() && value.contains(item)) return true;
        }
        return false;
    }

    private String appendWebEmojiLinksToDiscord(ChatMessage msg, String renderedLine, ConfigValues c) {
        return appendRegisteredEmojiLinksToDiscord(msg == null ? "" : msg.message, renderedLine,
                msg != null && c != null && c.discordAppendWebEmojiLinks, c);
    }

    private String appendGameEmojiLinksToDiscord(ChatMessage msg, String renderedLine, ConfigValues c) {
        return appendRegisteredEmojiLinksToDiscord(msg == null ? "" : msg.message, renderedLine,
                msg != null && c != null && c.discordAppendGameEmojiLinks, c);
    }

    /** Shared web/game Discord token handling. The source must be KWC :emoji: token text. */
    private String appendRegisteredEmojiLinksToDiscord(String tokenSource, String renderedLine,
                                                       boolean enabled, ConfigValues c) {
        String text = String.valueOf(renderedLine == null ? "" : renderedLine);
        if (!enabled || c == null || c.discordMaxEmojiLinksPerMessage <= 0) return text;
        WebChatServer server = plugin.webServer();
        if (server == null) return text;
        String links = server.externalEmojiLinksForDiscord(tokenSource, c.discordMaxEmojiLinksPerMessage);
        if (links.isBlank() || containsAnyLine(text, links)) return text;
        return text.isBlank() ? links : text + "\n" + links;
    }

    private String appendDiscordAttachmentUrls(String content, Object message, int maxUrls) {
        String base = String.valueOf(content == null ? "" : content).trim();
        List<String> urls = discordAttachmentImageUrls(message, maxUrls);
        if (urls.isEmpty()) return base;
        String joined = String.join("\n", urls);
        return base.isBlank() ? joined : base + "\n" + joined;
    }

    private List<String> discordAttachmentImageUrls(Object message, int maxUrls) {
        List<String> out = new ArrayList<>();
        if (message == null || maxUrls <= 0) return out;
        Object attachments = call(message, "getAttachments");
        if (!(attachments instanceof Iterable<?> iterable)) return out;

        Set<String> seen = new LinkedHashSet<>();
        for (Object attachment : iterable) {
            if (attachment == null) continue;
            String url = firstNonBlank(callString(attachment, "getUrl"), callString(attachment, "getProxyUrl"));
            if (url.isBlank()) continue;
            String fileName = callString(attachment, "getFileName");
            boolean image = asBoolean(call(attachment, "isImage")) || looksLikeImageAttachment(url) || looksLikeImageAttachment(fileName);
            if (!image) continue;
            if (seen.add(url)) {
                out.add(url);
                if (out.size() >= maxUrls) break;
            }
        }
        return out;
    }

    private boolean looksLikeImageAttachment(String value) {
        String v = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        int q = v.indexOf('?');
        if (q >= 0) v = v.substring(0, q);
        return v.endsWith(".png") || v.endsWith(".jpg") || v.endsWith(".jpeg")
                || v.endsWith(".gif") || v.endsWith(".webp") || v.endsWith(".bmp");
    }

    private String applyReplyRelayToDiscord(ChatMessage msg, String renderedLine, ConfigValues c) {
        String line = String.valueOf(renderedLine == null ? "" : renderedLine);
        if (msg == null || c == null || msg.replyToId == null || msg.replyToId.isBlank()) return line;

        // Discord reply relay is intentionally controlled by discordsrv.reply-relay.*.
        // Do not reuse the game-side reply settings here; otherwise Discord can receive
        // the game reply prefix/preview even when Discord reply relay is disabled.
        if (!c.discordReplyRelayEnabled) return line;

        if (c.discordReplyPrefixEnabled) {
            line = replaceFirstBracketLabelOrPrepend(line, plainReplyPrefix(c), 96);
        }

        if (!c.discordReplyPreviewEnabled) return line;
        String sender = safeText(msg.replyToSender);
        if (sender.isBlank()) sender = "Unknown";
        String preview = truncateVisible(safeText(msg.replyToPreview), Math.max(0, c.discordReplyPreviewMaxLength));
        if (preview.isBlank()) preview = "...";
        return sender + ": " + preview + "\n" + line;
    }

    private String plainReplyPrefix(ConfigValues c) {
        String value = c == null || c.replyGamePrefixText == null ? "" : c.replyGamePrefixText;
        value = value.replaceAll("(?i)&[0-9A-FK-ORX]", "");
        value = value.replaceAll("§.", "");
        return safeText(value).isBlank() ? "↪ [Reply] " : safeText(value) + (safeText(value).endsWith(" ") ? "" : " ");
    }

    private String replaceFirstBracketLabelOrPrepend(String text, String prefix, int searchLimit) {
        String line = String.valueOf(text == null ? "" : text);
        String label = String.valueOf(prefix == null ? "" : prefix);
        if (label.isBlank()) return line;

        int limit = searchLimit <= 0 ? line.length() : Math.min(line.length(), searchLimit);
        for (int i = 0; i < limit; i++) {
            if (line.charAt(i) != '[') continue;
            int end = line.indexOf(']', i + 1);
            if (end < 0 || end >= limit) break;
            int after = end + 1;
            while (after < line.length() && Character.isWhitespace(line.charAt(after))) after++;
            String spacer = label.endsWith(" ") || after >= line.length() ? "" : " ";
            return line.substring(0, i) + label + spacer + line.substring(after);
        }

        String spacer = label.endsWith(" ") || line.isBlank() ? "" : " ";
        return label + spacer + line;
    }

    private String truncateVisible(String value, int maxLength) {
        String raw = safeText(value);
        if (maxLength <= 0 || raw.length() <= maxLength) return raw;
        if (maxLength <= 1) return "…";
        return raw.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private boolean sendDirectToDiscord(String text) {
        try {
            rememberDirectDiscordOutbound(text);
            Object channel = getDiscordSrvTextChannel();
            if (channel == null) {
                plugin.getLogger().warning("DiscordSRV channel not found: " + plugin.configValues().discordChannel);
                return false;
            }
            Object action = channel.getClass().getMethod("sendMessage", CharSequence.class).invoke(channel, text);
            action.getClass().getMethod("queue").invoke(action);
            return true;
        } catch (NoSuchMethodException ex) {
            try {
                Object channel = getDiscordSrvTextChannel();
                if (channel == null) return false;
                Object action = channel.getClass().getMethod("sendMessage", String.class).invoke(channel, text);
                action.getClass().getMethod("queue").invoke(action);
                return true;
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to send web chat to DiscordSRV channel: " + t.getMessage());
                return false;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to send web chat to DiscordSRV channel: " + t.getMessage());
            return false;
        }
    }


    @Override
    public boolean sendAdminAlert(String channelName, String text) {
        ConfigValues c = plugin.configValues();
        if (c == null || (!c.discordEnabled && !c.adminDiscordAlertsEnabled) || text == null || text.isBlank()) return false;
        try {
            Object channel = getDiscordSrvTextChannel(channelName);
            if (channel == null) {
                plugin.getLogger().warning("DiscordSRV admin-alert channel not found: " + channelName);
                return false;
            }
            Object action;
            try { action = channel.getClass().getMethod("sendMessage", CharSequence.class).invoke(channel, text); }
            catch (NoSuchMethodException ex) { action = channel.getClass().getMethod("sendMessage", String.class).invoke(channel, text); }
            action.getClass().getMethod("queue").invoke(action);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to send KWC admin keyword alert through DiscordSRV/JDA: " + t.getMessage());
            return false;
        }
    }

    @Override
    public List<String> adminAlertChannelChoices() {
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        try {
            Class<?> discordSrv = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Object srvPlugin = discordSrv.getMethod("getPlugin").invoke(null);
            if (srvPlugin != null) {
                Object raw = call(srvPlugin, "getChannels");
                if (raw instanceof Map<?, ?> channels) {
                    for (Map.Entry<?, ?> entry : channels.entrySet()) {
                        String logicalName = safeText(String.valueOf(entry.getKey()));
                        String channelId = safeText(String.valueOf(entry.getValue()));
                        // DiscordSRV normally exposes a logical in-game channel name.
                        // Do not leak/display its backing Discord ID when that name exists.
                        if (!logicalName.isBlank()) choices.add(logicalName);
                        else if (isDiscordChannelId(channelId)) choices.add(channelId);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Admin UI still keeps the currently configured value below so saving
            // unrelated settings cannot silently replace it while DiscordSRV is offline.
        }

        ConfigValues c = plugin.configValues();
        String current = c == null ? "" : safeText(c.adminDiscordAlertsChannel);
        if (!current.isBlank()) choices.add(current);
        return new ArrayList<>(choices);
    }

    public boolean shouldSuppressGameEcho(String player, String message) {
        ConfigValues c = plugin.configValues();
        if (!c.discordEnabled || !c.discordSuppressGameEcho) return false;
        if (message == null || message.isBlank()) return false;

        long ttl = Math.max(1, c.discordSuppressGameEchoSeconds) * 1000L;
        long now = System.currentTimeMillis();
        String normalizedMessage = normalizeEcho(message);

        pruneRecent(now, ttl);

        for (Map.Entry<String, Long> entry : recentDiscordInbound.entrySet()) {
            if (now - entry.getValue() > ttl) continue;
            String recent = entry.getKey();
            if (normalizedMessage.equals(recent) || normalizedMessage.contains(recent)) {
                return true;
            }
        }
        return false;
    }

    private void rememberDirectDiscordOutbound(String message) {
        String key = normalizeEcho(message);
        if (key.isBlank()) return;
        recentDirectDiscordOutbound.put(key, System.currentTimeMillis());
        pruneRecent(System.currentTimeMillis(), 60_000L);
    }

    private void rememberDiscordInbound(String message) {
        ConfigValues c = plugin.configValues();
        if (!c.discordSuppressGameEcho) return;
        String key = normalizeEcho(message);
        if (key.isBlank()) return;
        recentDiscordInbound.put(key, System.currentTimeMillis());
        pruneRecent(System.currentTimeMillis(), Math.max(1, c.discordSuppressGameEchoSeconds) * 1000L);
    }

    private boolean seenRecently(Map<String, Long> map, String key, long ttlMillis) {
        long now = System.currentTimeMillis();
        Long old = map.get(key);
        if (old != null && now - old <= ttlMillis) return true;
        map.entrySet().removeIf(e -> now - e.getValue() > ttlMillis);
        return false;
    }

    private void pruneRecent(long now, long ttlMillis) {
        recentDiscordInbound.entrySet().removeIf(e -> now - e.getValue() > ttlMillis);
        recentDiscordEventIds.entrySet().removeIf(e -> now - e.getValue() > 60_000L);
        recentDirectDiscordOutbound.entrySet().removeIf(e -> now - e.getValue() > 60_000L);
        recentLocalGameOutbound.entrySet().removeIf(e -> now - e.getValue().time > 60_000L);
        globalDiscordInboundEventIds.entrySet().removeIf(e -> now - e.getValue() > 60_000L);
    }

    private static final class LocalGameOutbound {
        final long time;
        final String displaySender;
        final String realSender;
        final String normalizedMessage;

        LocalGameOutbound(long time, String displaySender, String realSender, String normalizedMessage) {
            this.time = time;
            this.displaySender = displaySender == null ? "" : displaySender;
            this.realSender = realSender == null ? "" : realSender;
            this.normalizedMessage = normalizedMessage == null ? "" : normalizedMessage;
        }
    }

    private String normalizeEcho(String value) {
        if (value == null) return "";
        return value
                .replaceAll("§.", "")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private Object getDiscordSrvTextChannel() throws Exception {
        return getDiscordSrvTextChannel(plugin.configValues().discordChannel);
    }

    private Object getDiscordSrvTextChannel(String channelName) throws Exception {
        Class<?> discordSrv = Class.forName("github.scarsz.discordsrv.DiscordSRV");
        Object srvPlugin = discordSrv.getMethod("getPlugin").invoke(null);
        if (srvPlugin == null) return null;
        String target = String.valueOf(channelName == null ? "" : channelName).trim();
        if (target.isBlank()) target = plugin.configValues().discordChannel;

        // Preferred path: DiscordSRV's logical Channels mapping (for example "alerts").
        Object mapped = srvPlugin.getClass()
                .getMethod("getDestinationTextChannelForGameChannelName", String.class)
                .invoke(srvPlugin, target);
        if (mapped != null) return mapped;

        // Fallback for configurations where KWC has only a raw Discord channel ID.
        // IDs are never exposed in the Admin selector when a logical DiscordSRV name
        // is available; this exists only so ID-only configurations remain usable.
        if (isDiscordChannelId(target)) {
            Object jdaObject = getJda();
            if (jdaObject != null) {
                try {
                    return jdaObject.getClass().getMethod("getTextChannelById", String.class).invoke(jdaObject, target);
                } catch (NoSuchMethodException ignored) {
                    // Older/shaded JDA variants are handled by DiscordUtil below.
                }
                try {
                    Class<?> discordUtil = Class.forName("github.scarsz.discordsrv.util.DiscordUtil");
                    return discordUtil.getMethod("getTextChannelById", String.class).invoke(null, target);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private boolean isDiscordChannelId(String value) {
        String v = safeText(value);
        return v.length() >= 5 && v.length() <= 32 && v.chars().allMatch(Character::isDigit);
    }

    private Object getJda() throws Exception {
        try {
            Class<?> discordUtil = Class.forName("github.scarsz.discordsrv.util.DiscordUtil");
            Object value = discordUtil.getMethod("getJda").invoke(null);
            if (value != null) return value;
        } catch (ClassNotFoundException ignored) {
        }

        Class<?> discordSrv = Class.forName("github.scarsz.discordsrv.DiscordSRV");
        Object srvPlugin = discordSrv.getMethod("getPlugin").invoke(null);
        if (srvPlugin == null) return null;

        Object value = call(srvPlugin, "getJda");
        if (value != null) return value;

        value = call(srvPlugin, "getJDA");
        if (value != null) return value;

        return null;
    }


    private boolean isConfiguredChannel(Object channel) throws Exception {
        Object configured = getDiscordSrvTextChannel();
        if (configured == null || channel == null) return false;

        String a = callString(channel, "getId");
        String b = callString(configured, "getId");
        if (!a.isBlank() && a.equals(b)) return true;

        String an = callString(channel, "getName");
        String bn = callString(configured, "getName");
        return !an.isBlank() && an.equalsIgnoreCase(bn);
    }

    private String discordSender(Object event, Object author) {
        try {
            Object member = call(event, "getMember");
            String effective = callString(member, "getEffectiveName");
            if (!effective.isBlank()) return effective;
        } catch (Throwable ignored) {
        }
        String global = callString(author, "getGlobalName");
        if (!global.isBlank()) return global;
        String name = callString(author, "getName");
        return name.isBlank() ? "Discord" : name;
    }

    private String formatMessage(String template, ChatMessage msg, String channel) {
        if (msg == null) return "";
        ChatMessage value = msg;
        String rawTemplate = template == null || template.isBlank() ? "{message}" : template;
        String rendered = format(rawTemplate, value.sender, value.role, value.source, value.message, channel,
                messageServerName(value), messageServerId(value));

        // Existing config files do not contain the new placeholders. Prefix the
        // server automatically so upgrading the plugin immediately makes messages
        // distinguishable. Adding {server} or {server_id} to the custom format
        // suppresses this automatic prefix and lets the administrator place it.
        if (!containsServerPlaceholder(rawTemplate)) {
            String label = messageServerName(value);
            if (!label.isBlank() && !hasServerLabelPrefix(rendered, label)) {
                rendered = "[" + label + "] " + rendered;
            }
        }
        return rendered;
    }

    private String format(String template, String sender, String role, String source, String message, String channel) {
        return format(template, sender, role, source, message, channel, "", "");
    }

    private String format(String template, String sender, String role, String source, String message,
                          String channel, String server, String serverId) {
        if (template == null || template.isBlank()) template = "{message}";
        return template
                .replace("{sender}", safeText(sender))
                .replace("{name}", safeText(sender))
                .replace("{role}", safeText(role))
                .replace("{source}", safeText(source))
                .replace("{message}", safeText(message))
                .replace("{channel}", safeText(channel))
                .replace("{server}", safeText(server))
                .replace("{server_id}", safeText(serverId));
    }

    private boolean containsServerPlaceholder(String template) {
        String value = String.valueOf(template == null ? "" : template);
        return value.contains("{server}") || value.contains("{server_id}");
    }

    private String messageServerId(ChatMessage msg) {
        return msg == null ? "" : safeText(msg.originServerId);
    }

    private String messageServerName(ChatMessage msg) {
        if (msg == null) return "";
        String name = safeText(msg.originServerName);
        return name.isBlank() ? messageServerId(msg) : name;
    }

    private String localRelayServerLabel(ConfigValues c) {
        if (c == null || !c.serverRelayEnabled) return "";
        String name = safeText(c.serverRelayServerName);
        return name.isBlank() ? safeText(c.serverRelayServerId) : name;
    }

    private Object call(Object target, String method) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String callString(Object target, String method) {
        Object v = call(target, method);
        return v == null ? "" : String.valueOf(v);
    }

    private boolean asBoolean(Object v) {
        return v instanceof Boolean && (Boolean) v;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private String safeText(String s) {
        if (s == null) return "";
        return stripMinecraftColorCodes(s).replace("\r", " ").replace("\n", " ").trim();
    }

    private String stripMinecraftColorCodes(String s) {
        if (s == null || s.isEmpty()) return "";
        // Strip legacy color codes before sending to Discord. This intentionally
        // removes both section-sign codes and configured ampersand codes so
        // player-display.strip-colors=false can color names in the web UI without
        // leaking raw &a/§a/&#RRGGBB tags to Discord.
        String out = s.replaceAll("(?i)[§&]x(?:[§&][0-9a-f]){6}", "");
        out = out.replaceAll("(?i)&#[0-9a-f]{6}", "");
        out = out.replaceAll("(?i)[§&][0-9a-fk-or]", "");
        return out.replace("§", "");
    }

    private String sanitizeCommand(String s) {
        return safeText(s).replace("\"", "\\\"");
    }
}
