package dev.kokoto.bluemapwebchat;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

public class ConfigValues {
    public boolean pluginEnabled;
    public boolean updateCheckEnabled;
    public List<String> privateChatSuperAdmins;
    public boolean auditEnabled;
    public String auditDirectory;
    public String httpHost;
    public int httpPort;
    public String pathPrefix;
    public String corsOrigin;
    public List<String> trustedProxies;
    public boolean logClientIpResolution;

    public int maxSseConnectionsPerIp;
    public int maxSseConnectionsTotal;

    public boolean standaloneWebEnabled;
    public String standaloneWebPath;
    public String standaloneWebApiBaseUrl;
    public String standaloneWebAppName;
    public String standaloneWebAppShortName;

    public boolean webAutoInstall;
    public boolean webAutoPatch;
    public String bluemapWebRoot;
    public String bluemapWebappConf;
    public String addonPath;
    public String apiBaseUrl;

    public int historySize;
    public int historyRetentionDays;
    public String historyStorage;
    public String historyFile;
    public String historySqliteFile;
    public boolean historySqliteMigrateJsonl;
    public int historyPageSize;
    public boolean searchEnabled;
    public int searchResultLimit;

    public boolean directMessageEnabled;
    public boolean directMessageAllowWebSend;
    public boolean directMessageAllowGameSend;
    public boolean directMessageNotifyOnLogin;
    public boolean directMessageNotifyOnMessage;
    public boolean directMessageWebUnreadBadge;
    public boolean directMessageConfirmHide;
    public boolean directMessageCaptureGameWhispers;
    public boolean directMessageAdminAuditEnabled;
    public int directMessageRetentionDays;
    public int directMessageMaxMessagesPerThread;
    public int directMessageMaxMessageLength;
    public String directMessageStorage;
    public String directMessageJsonlFile;
    public String directMessageSqliteFile;

    public boolean groupChatEnabled;
    public boolean groupChatAllowWebSend;
    public boolean groupChatAllowPublicRooms;
    public boolean groupChatAllowRoomPasswords;
    public boolean groupChatConfirmLeave;
    public boolean groupChatConfirmHide;
    public boolean groupChatAdminAuditEnabled;
    public int groupChatRetentionDays;
    public int groupChatMaxMessagesPerRoom;
    public int groupChatMaxMessageLength;
    public int groupChatMaxRoomsPerUser;
    public int groupChatMaxMembersPerRoom;
    public int groupChatMaxRoomNameLength;
    public int groupChatInviteExpireHours;
    public String groupChatSqliteFile;
    public int maxMessageLength;
    public int maxUrlMessageLength;
    public boolean messageTokensEnabled;
    public int messageTokensMaxReplacements;
    public List<String> messageTokenNewlineAliases;
    public List<String> messageTokenBlankLineAliases;
    public List<String> messageTokenTabAliases;
    public int messageTokenTabSpaces;
    public Map<String, String> messageTokenCustomReplacements;
    public String webUserToGameFormat;
    public String webGuestToGameFormat;
    public String webAdminToGameFormat;
    public boolean broadcastWebChatToWeb;
    public boolean broadcastIngameChatToWeb;
    public boolean sendWebChatToGame;
    public boolean clickableUrlsInGame;

    public boolean serverRelayEnabled;
    public String serverRelayServerId;
    public String serverRelayServerName;
    public String serverRelaySharedSecret;
    public int serverRelayConnectTimeoutSeconds;
    public int serverRelayRequestTimeoutSeconds;
    public int serverRelayMaxClockSkewSeconds;
    public int serverRelayDedupeSeconds;
    public int serverRelayMaxHops;
    public boolean serverRelayGameChat;
    public boolean serverRelayWebChat;
    public boolean serverRelayGuestChat;
    public boolean serverRelayDiscordChat;
    public boolean serverRelaySystemEvents;
    public boolean serverRelayDeliverToWeb;
    public boolean serverRelayDeliverToGame;
    public String serverRelayGameFormat;
    public List<RelayPeer> serverRelayPeers;

    public static final class RelayPeer {
        public final String id;
        public final String url;
        public final String secret;
        public final boolean enabled;

        public RelayPeer(String id, String url, String secret, boolean enabled) {
            this.id = id;
            this.url = url;
            this.secret = secret;
            this.enabled = enabled;
        }
    }

    public boolean gameNameHoverEnabled;
    public String gameNameHoverText;

    public String uiLanguage;
    public String uiLanguageFallback;
    public String uiTimeZone;
    public boolean linkifyUrls;
    public boolean imagePreviewEnabled;
    public int imagePreviewMaxPerMessage;
    public int imagePreviewMaxHeight;
    public boolean googleDriveImagePreview;
    public String googleDrivePreviewMode;
    public boolean externalMediaCacheEnabled;
    public boolean cacheDiscordCdn;
    public String externalMediaCacheDirectory;
    public int externalMediaCacheMaxSizeMb;
    public int externalMediaCacheRetentionDays;
    public int externalMediaCacheTimeoutSeconds;
    public boolean youtubeEmbedEnabled;
    public boolean youtubeClickToLoad;
    public boolean mediaClickToLoad;
    public boolean youtubeNoCookie;
    public boolean youtubeRememberExpanded;
    public boolean youtubeAutoplayOnOpen;
    public int youtubeMaxEmbedsPerMessage;
    public boolean socialEmbedsEnabled;
    public boolean socialEmbedsClickToLoad;
    public int socialEmbedsMaxPerMessage;
    public boolean tiktokEmbedEnabled;
    public boolean xEmbedEnabled;
    public String xEmbedTheme;
    public boolean xEmbedDnt;
    public boolean xEmbedHideMedia;
    public boolean xEmbedHideThread;
    public boolean hideChatForGuestsWhenGuestDisabled;
    public boolean showLoginOnlyWhenHidden;
    public boolean uiResizable;
    public boolean uiRememberWindowSize;
    public int uiDefaultWidth;
    public int uiDefaultHeight;
    public int uiMinWidth;
    public int uiMinHeight;
    public int uiMaxWidth;
    public int uiMaxHeight;
    public int uiFontSize;
    public int uiMessageFontSize;
    public int uiInputFontSize;
    public String uiTextColor;
    public String uiUiTextColor;
    public String uiTextShadowMode;
    public String uiTextShadowCustom;
    public String uiInputBackgroundColor;
    public int uiButtonFontSize;
    public int uiBadgeFontSize;
    public boolean uiVirtualScrollEnabled;
    public double uiVirtualScrollOverscanScreens;
    public int uiVirtualScrollMinRenderedMessages;
    public boolean uiVirtualScrollPreserveVisibleMedia;
    public boolean uiVirtualScrollPreservePlayingMedia;
    public double uiHistoryPreloadScreens;
    public int uiHistoryPreloadMinPx;
    public int uiAutoFollowBottomThresholdPx;
    public int uiScrollInteractionIdleMs;
    public boolean uiResumeRefreshEnabled;
    public int uiResumeRefreshMinIntervalSeconds;
    public boolean uiResumeRefreshSkipWhileMediaActive;
    public boolean uiResumeRefreshSkipUnchanged;
    public String uiTheme;
    public boolean uiSyncBlueMapTheme;
    public double uiOpacity;
    public boolean uiUserPreferencesControl;
    public List<String> uiUserFontOptions;
    public String uiFontFamily;
    public boolean uiPictureInPictureEnabled;

    public boolean browserNotificationsEnabled;
    public boolean browserNotificationsOnlyWhenHidden;
    public boolean browserNotificationsNotifyNormalChat;
    public boolean browserNotificationsNotifyDm;
    public boolean browserNotificationsNotifyGroupChat;
    public boolean browserNotificationsNotifyMentions;
    public boolean browserNotificationsNotifyReplies;
    public boolean browserNotificationsNotifySystem;
    public boolean browserNotificationsNotifyKeywords;
    public boolean browserNotificationsNotifyOwnMessages;
    public boolean browserNotificationsShowMessagePreview;

    public boolean webPushEnabled;
    public String webPushVapidPublicKey;
    public String webPushVapidPrivateKey;
    public String webPushSubject;
    public String webPushNotificationTitle;
    public String webPushSubscriptionsFile;
    public int webPushTtlSeconds;
    public boolean webPushNotifyNormalChat;
    public boolean webPushNotifyDm;
    public boolean webPushNotifyGroupChat;
    public boolean webPushNotifyMentions;
    public boolean webPushNotifyReplies;
    public boolean webPushNotifySystem;
    public boolean webPushNotifyKeywords;
    public boolean webPushNotifyOwnMessages;
    public boolean webPushShowMessagePreview;

    public String playerNameMode;
    public boolean playerNameStripColors;
    public boolean webFontsEnabled;
    public String webFontsDirectory;
    public List<Map<String, Object>> webFontsItems;

    public boolean guestEnabled;
    public boolean guestAllowCustomName;
    public String guestNamePrefix;
    public int guestCooldownSeconds;
    public int guestMaxMessagesPerMinute;
    public boolean guestBlockPlayerNameSpoofing;
    public List<String> guestBlockedNames;

    public String captchaMode;
    public int captchaExpireSeconds;
    public boolean captchaRequireOnEachMessage;
    public int captchaPassValidMinutes;

    public boolean authEnabled;
    public int linkCodeLength;
    public int linkCodeExpireSeconds;
    public int authCodeCooldownSeconds;
    public int authCodeMaxPerMinute;
    public boolean passwordLogin;
    public int rememberSessionDays;
    public boolean autoAdminFromPermission;
    public String adminPermission;

    public int loginFailLimit;
    public int loginFailWindowSeconds;
    public int loginLockSeconds;

    public boolean allowLocalAdminAccounts;
    public int adminSessionExpireHours;
    public List<String> allowAdminLoginFrom;

    public boolean moderationEnabled;
    public boolean allowWebAdminPanel;
    public boolean allowModeratorMessageDelete;
    public boolean allowModeratorGuestMute;
    public int defaultMuteMinutes;

    public boolean discordEnabled;
    public String discordChannel;
    public boolean discordWebToDiscord;
    public boolean discordDiscordToWeb;
    public boolean discordIgnoreBotMessages;
    public boolean discordSuppressGameEcho;
    public int discordSuppressGameEchoSeconds;
    public String discordWebToDiscordFormat;
    public String discordGameToDiscordFormat;
    public String discordToWebSenderFormat;
    public String discordToWebMessageFormat;
    public boolean discordGameToDiscord;
    public boolean discordAppendGameEmojiLinks;
    public boolean discordSendWebUser;
    public boolean discordSendWebGuest;
    public boolean discordSendWebAdmin;
    public boolean discordAppendWebEmojiLinks;
    public int discordMaxEmojiLinksPerMessage;
    public boolean discordReplyRelayEnabled;
    public boolean discordReplyPrefixEnabled;
    public boolean discordReplyPreviewEnabled;
    public int discordReplyPreviewMaxLength;

    public boolean uploadEnabled;
    public boolean uploadAllowGuest;
    public boolean uploadAllowUser;
    public boolean uploadAllowModerator;
    public boolean uploadAllowAdmin;
    public int uploadCooldownSeconds;
    public int uploadMaxUploadsPerMinute;
    public int uploadMaxFileSizeMb;
    public int uploadMaxTotalSizeMb;
    public int uploadMaxFilesPerMessage;
    public String uploadDirectory;
    public String uploadPublicBaseUrl;
    public int uploadRetentionDays;
    public List<String> uploadAllowedExtensions;
    public boolean uploadClipboardEnabled;
    public String uploadClipboardSendMode;
    public String uploadClipboardImageDefaultExtension;
    public boolean uploadPreviewImages;
    public boolean uploadPreviewVideos;
    public boolean uploadPreviewAudio;


    public boolean emojiEnabled;
    public boolean emojiShowButton;
    public String emojiDirectory;
    public String emojiPublicBaseUrl;
    public int emojiMaxFileSizeKb;
    public int emojiMaxTotalSizeMb;
    public boolean emojiShowStorageUsage;
    public boolean emojiShowStorageLimit;
    public int emojiRenderSizePx;
    public int emojiPickerSizePx;
    public int emojiMessageTokenLimit;
    public String emojiTokenFormat;
    public List<String> emojiAllowedExtensions;
    public boolean emojiGameLinkEnabled;
    public String emojiGameLinkMode;
    public String emojiGameLinkPublicApiBaseUrl;
    public String emojiGameLinkLabelFormat;
    public int emojiGameLinkMaxLinksPerMessage;
    public String emojiGameLinkDefaultPack;
    public Map<String, String> emojiGameLinkAliases;

    public boolean replyGamePrefixEnabled;
    public String replyGamePrefixText;
    public boolean replyGamePreviewEnabled;
    public String replyGamePreviewFormat;
    public int replyGamePreviewMaxLength;
    public boolean replyGameClickEnabled;
    public boolean replyGameClickLocalChat;
    public String replyGameCommandFormat;

    public boolean pinnedEnabled;
    public int pinnedMaxPins;
    public boolean pinnedShowToLoggedOut;
    public boolean pinnedPreserveUploads;

    public boolean commandsEnabled;
    public boolean commandsAllowAll;
    public Role commandsMinRole;
    public boolean commandsShowButton;
    public boolean commandsShowSlashPanel;
    public boolean commandsRunFromChatInput;
    public boolean commandsRequireConfirm;
    public int commandsMaxLength;
    public boolean commandsBroadcastToWebChat;
    public List<CommandPreset> commandPresets;

    public boolean announcementsBroadcastToWebChat;
    public Map<String, AnnouncementConfig> announcements;

    public static class CommandPreset {
        public final String id;
        public final String label;
        public final String description;
        public final String command;
        public final boolean enabled;
        public final boolean requireConfirm;

        public CommandPreset(String id, String label, String description, String command, boolean enabled, boolean requireConfirm) {
            this.id = id == null ? "" : id;
            this.label = label == null ? "" : label;
            this.description = description == null ? "" : description;
            this.command = command == null ? "" : command;
            this.enabled = enabled;
            this.requireConfirm = requireConfirm;
        }
    }

    public static class AnnouncementConfig {
        public final boolean enabled;
        public final String message;

        public AnnouncementConfig(boolean enabled, String message) {
            this.enabled = enabled;
            this.message = message == null ? "" : message;
        }
    }

    public boolean announcementEnabled(String key) {
        if (!announcementsBroadcastToWebChat || announcements == null || key == null) return false;
        AnnouncementConfig config = announcements.get(key);
        return config != null && config.enabled && config.message != null && !config.message.isBlank();
    }

    public String announcementMessage(String key) {
        if (announcements == null || key == null) return "";
        AnnouncementConfig config = announcements.get(key);
        return config == null ? "" : config.message;
    }

    public static ConfigValues load(FileConfiguration c) {
        ConfigValues v = new ConfigValues();
        // New generated configs default enabled:false, but old configs without this key remain enabled.
        v.pluginEnabled = c.isSet("enabled") ? c.getBoolean("enabled", false) : true;
        v.updateCheckEnabled = c.getBoolean("update-check.enabled", true);
        v.privateChatSuperAdmins = c.getStringList("private-chat-super-admins");
        v.auditEnabled = c.getBoolean("audit.enabled", true);
        v.auditDirectory = c.getString("audit.directory", "audit");

        v.httpHost = c.getString("http.host", "0.0.0.0");
        v.httpPort = c.getInt("http.port", 8899);
        v.pathPrefix = normalizePrefix(c.getString("http.path-prefix", "/api"));
        v.corsOrigin = c.getString("http.cors-origin", "*");
        v.trustedProxies = c.getStringList("http.trusted-proxies");
        v.logClientIpResolution = c.getBoolean("http.log-client-ip-resolution", false);

        v.standaloneWebEnabled = c.getBoolean("standalone-web.enabled", false);
        v.standaloneWebPath = normalizePrefix(c.getString("standalone-web.path", "/chat"));
        if ("/".equals(v.standaloneWebPath)) v.standaloneWebPath = "/chat";
        v.standaloneWebApiBaseUrl = c.getString("standalone-web.api-base-url", "");
        v.standaloneWebAppName = normalizeDisplayName(c.getString("standalone-web.app-name", "Web Chat"), "Web Chat");
        v.standaloneWebAppShortName = normalizeDisplayName(c.getString("standalone-web.app-short-name", ""), "");
        if (v.standaloneWebAppShortName == null || v.standaloneWebAppShortName.isBlank()) v.standaloneWebAppShortName = v.standaloneWebAppName;

        v.webAutoInstall = c.getBoolean("web-addon.auto-install", true);
        v.webAutoPatch = c.getBoolean("web-addon.auto-patch-webapp-conf", true);
        v.bluemapWebRoot = c.getString("web-addon.bluemap-web-root", "bluemap/web");
        v.bluemapWebappConf = c.getString("web-addon.bluemap-webapp-conf", "plugins/BlueMap/webapp.conf");
        v.addonPath = stripSlashes(c.getString("web-addon.addon-path", "addons/bluemap-web-chat"));
        v.apiBaseUrl = c.getString("web-addon.api-base-url", "");

        v.historySize = c.getInt("chat.history-size", 1000);
        v.historyRetentionDays = c.getInt("chat.history-retention-days", 5);
        if (v.historyRetentionDays < 0) v.historyRetentionDays = 0;
        String configuredHistoryStorage = c.getString("chat.history-storage", "sqlite");
        configuredHistoryStorage = configuredHistoryStorage == null ? "sqlite" : configuredHistoryStorage.trim().toLowerCase(Locale.ROOT);
        if (configuredHistoryStorage.equals("memory") || configuredHistoryStorage.equals("none")) configuredHistoryStorage = "memory";
        else if (configuredHistoryStorage.equals("json") || configuredHistoryStorage.equals("jsonl") || configuredHistoryStorage.equals("file")) configuredHistoryStorage = "jsonl";
        else configuredHistoryStorage = "sqlite";
        v.historyStorage = configuredHistoryStorage;
        v.historyFile = c.getString("chat.history-file", "history.jsonl");
        v.historySqliteFile = c.getString("chat.history-sqlite-file", "history.db");
        v.historySqliteMigrateJsonl = c.getBoolean("chat.history-sqlite-migrate-jsonl", true);
        v.historyPageSize = Math.max(0, c.getInt("chat.history-page-size", 80));
        v.searchEnabled = c.getBoolean("search.enabled", true);
        v.searchResultLimit = Math.max(1, c.getInt("search.result-limit", 50));

        v.directMessageEnabled = c.getBoolean("direct-message.enabled", false);
        v.directMessageAllowWebSend = c.getBoolean("direct-message.allow-web-send", true);
        v.directMessageAllowGameSend = c.getBoolean("direct-message.allow-game-send", true);
        v.directMessageNotifyOnLogin = c.getBoolean("direct-message.notify-on-login", true);
        v.directMessageNotifyOnMessage = c.getBoolean("direct-message.notify-on-message", true);
        v.directMessageWebUnreadBadge = c.getBoolean("direct-message.web-unread-badge", true);
        v.directMessageConfirmHide = c.getBoolean("direct-message.confirm-hide", true);
        v.directMessageCaptureGameWhispers = c.getBoolean("direct-message.capture-game-whispers", false);
        v.directMessageAdminAuditEnabled = c.getBoolean("direct-message.admin-audit.enabled", false);
        v.directMessageRetentionDays = Math.max(0, c.getInt("direct-message.retention-days", 0));
        v.directMessageMaxMessagesPerThread = Math.max(0, c.getInt("direct-message.max-messages-per-thread", 0));
        v.directMessageMaxMessageLength = Math.max(0, c.getInt("direct-message.max-message-length", 500));
        String configuredDirectMessageStorage = c.getString("direct-message.storage", "auto");
        configuredDirectMessageStorage = configuredDirectMessageStorage == null ? "auto" : configuredDirectMessageStorage.trim().toLowerCase(Locale.ROOT);
        if (configuredDirectMessageStorage.equals("auto") || configuredDirectMessageStorage.isBlank()) {
            configuredDirectMessageStorage = "jsonl".equals(v.historyStorage) ? "jsonl" : "sqlite";
        } else if (configuredDirectMessageStorage.equals("json") || configuredDirectMessageStorage.equals("jsonl") || configuredDirectMessageStorage.equals("file")) {
            configuredDirectMessageStorage = "jsonl";
        } else {
            configuredDirectMessageStorage = "sqlite";
        }
        v.directMessageStorage = configuredDirectMessageStorage;
        v.directMessageJsonlFile = c.getString("direct-message.jsonl-file", "direct-messages.jsonl");
        v.directMessageSqliteFile = c.getString("direct-message.sqlite-file", "direct-messages.db");

        v.groupChatEnabled = c.getBoolean("group-chat.enabled", false);
        v.groupChatAllowWebSend = c.getBoolean("group-chat.allow-web-send", true);
        v.groupChatAllowPublicRooms = c.getBoolean("group-chat.allow-public-rooms", true);
        v.groupChatAllowRoomPasswords = c.getBoolean("group-chat.allow-room-passwords", true);
        v.groupChatConfirmLeave = c.getBoolean("group-chat.confirm-leave", true);
        v.groupChatConfirmHide = c.getBoolean("group-chat.confirm-hide", true);
        v.groupChatAdminAuditEnabled = c.getBoolean("group-chat.admin-audit.enabled", false);
        v.groupChatRetentionDays = Math.max(0, c.getInt("group-chat.retention-days", 30));
        v.groupChatMaxMessagesPerRoom = Math.max(0, c.getInt("group-chat.max-messages-per-room", 1000));
        v.groupChatMaxMessageLength = Math.max(0, c.getInt("group-chat.max-message-length", 500));
        v.groupChatMaxRoomsPerUser = Math.max(0, c.getInt("group-chat.max-rooms-per-user", 20));
        v.groupChatMaxMembersPerRoom = Math.max(0, c.getInt("group-chat.max-members-per-room", 50));
        v.groupChatMaxRoomNameLength = Math.max(1, c.getInt("group-chat.max-room-name-length", 32));
        v.groupChatInviteExpireHours = Math.max(1, c.getInt("group-chat.invite-expire-hours", 72));
        v.groupChatSqliteFile = c.getString("group-chat.sqlite-file", "group-messages.db");
        v.maxMessageLength = Math.max(0, c.getInt("chat.max-message-length", 120));
        v.maxUrlMessageLength = Math.max(0, c.getInt("chat.max-url-message-length", 2048));
        if (v.maxMessageLength > 0 && v.maxUrlMessageLength > 0 && v.maxUrlMessageLength < v.maxMessageLength) v.maxUrlMessageLength = v.maxMessageLength;
        v.messageTokensEnabled = c.getBoolean("message-tokens.enabled", true);
        v.messageTokensMaxReplacements = Math.max(0, c.getInt("message-tokens.max-replacements-per-message", 24));
        v.messageTokenNewlineAliases = c.getStringList("message-tokens.newline.aliases");
        if (v.messageTokenNewlineAliases == null || v.messageTokenNewlineAliases.isEmpty()) {
            v.messageTokenNewlineAliases = List.of("enter", "newline", "nextline", "linebreak", "br");
        }
        v.messageTokenBlankLineAliases = c.getStringList("message-tokens.blank-line.aliases");
        if (v.messageTokenBlankLineAliases == null || v.messageTokenBlankLineAliases.isEmpty()) {
            v.messageTokenBlankLineAliases = List.of("blankline", "emptyline", "paragraphbreak");
        }
        v.messageTokenTabAliases = c.getStringList("message-tokens.tab.aliases");
        if (v.messageTokenTabAliases == null || v.messageTokenTabAliases.isEmpty()) {
            v.messageTokenTabAliases = List.of("tab", "indent");
        }
        v.messageTokenTabSpaces = Math.max(1, Math.min(16, c.getInt("message-tokens.tab.spaces", 4)));
        v.messageTokenCustomReplacements = new LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection tokenCustom = c.getConfigurationSection("message-tokens.custom");
        if (tokenCustom != null) {
            for (String key : tokenCustom.getKeys(false)) {
                org.bukkit.configuration.ConfigurationSection item = tokenCustom.getConfigurationSection(key);
                if (item == null) continue;
                String replacement = String.valueOf(item.getString("replacement", ""));
                replacement = replacement.replaceAll("[\\p{Cntrl}]", "");
                if (replacement.isEmpty()) continue;
                List<String> aliases = item.getStringList("aliases");
                if (aliases == null || aliases.isEmpty()) aliases = List.of(key);
                for (String alias : aliases) {
                    String normalized = MessageTokenProcessor.normalizeAlias(alias);
                    if (!normalized.isBlank()) v.messageTokenCustomReplacements.putIfAbsent(normalized, replacement);
                }
            }
        }
        v.webUserToGameFormat = c.getString("chat.web-user-to-game-format", "[Web] {player}: {message}");
        v.webGuestToGameFormat = c.getString("chat.web-guest-to-game-format", "[Web Guest] {guest}: {message}");
        v.webAdminToGameFormat = c.getString("chat.web-admin-to-game-format", "[Web Admin] {player}: {message}");
        v.broadcastWebChatToWeb = c.getBoolean("chat.broadcast-web-chat-to-web", true);
        v.broadcastIngameChatToWeb = c.getBoolean("chat.broadcast-ingame-chat-to-web", true);
        v.sendWebChatToGame = c.getBoolean("chat.send-web-chat-to-game", true);
        v.clickableUrlsInGame = c.getBoolean("chat.clickable-urls-in-game", true);

        v.serverRelayEnabled = c.getBoolean("server-relay.enabled", false);
        v.serverRelayServerId = normalizeRelayId(c.getString("server-relay.server-id", ""));
        v.serverRelayServerName = normalizeDisplayName(c.getString("server-relay.server-name", ""), "");
        v.serverRelaySharedSecret = String.valueOf(c.getString("server-relay.shared-secret", "")).trim();
        v.serverRelayConnectTimeoutSeconds = Math.max(1, c.getInt("server-relay.connect-timeout-seconds", 5));
        v.serverRelayRequestTimeoutSeconds = Math.max(1, c.getInt("server-relay.request-timeout-seconds", 10));
        v.serverRelayMaxClockSkewSeconds = Math.max(1, c.getInt("server-relay.max-clock-skew-seconds", 60));
        v.serverRelayDedupeSeconds = Math.max(30, c.getInt("server-relay.dedupe-seconds", 300));
        v.serverRelayMaxHops = Math.max(1, Math.min(32, c.getInt("server-relay.max-hops", 8)));
        v.serverRelayGameChat = c.getBoolean("server-relay.sources.game", true);
        v.serverRelayWebChat = c.getBoolean("server-relay.sources.web", true);
        v.serverRelayGuestChat = c.getBoolean("server-relay.sources.guest", true);
        v.serverRelayDiscordChat = c.getBoolean("server-relay.sources.discord", false);
        v.serverRelaySystemEvents = c.getBoolean("server-relay.sources.system", false);
        v.serverRelayDeliverToWeb = c.getBoolean("server-relay.delivery.web", true);
        v.serverRelayDeliverToGame = c.getBoolean("server-relay.delivery.game", true);
        v.serverRelayGameFormat = c.getString("server-relay.game-format", "&8[&b{server}&8] &f{sender}&7: &f{message}");
        v.serverRelayPeers = sanitizeRelayPeers(c.getMapList("server-relay.peers"));

        v.gameNameHoverEnabled = c.getBoolean("chat.game-name-hover.enabled", false);
        v.gameNameHoverText = c.getString("chat.game-name-hover.text", "&f{real}");

        v.announcementsBroadcastToWebChat = c.getBoolean("announcements.broadcast-to-web-chat", true);
        v.announcements = new LinkedHashMap<>();
        loadAnnouncement(v, c, "minecraft-join", true, "🟢 {player} joined the server.");
        loadAnnouncement(v, c, "minecraft-quit", true, "🔴 {player} left the server.");
        loadAnnouncement(v, c, "first-join", true, "✨ {player} joined the server for the first time.");
        loadAnnouncement(v, c, "death", true, "☠ {message}");
        loadAnnouncement(v, c, "advancement", true, "🏆 {player} completed the advancement [{advancement}].");
        loadAnnouncement(v, c, "world-change", false, "🌍 {player} moved to {to_world}.");
        loadAnnouncement(v, c, "gamemode-change", false, "🎮 {player} changed game mode to {to_gamemode}.");
        loadAnnouncement(v, c, "level-change", false, "⭐ {player} changed level from {old_level} to {new_level}.");
        loadAnnouncement(v, c, "bed-enter", false, "💤 {player} entered a bed.");
        loadAnnouncement(v, c, "server-start", true, "🟢 Server started.");
        loadAnnouncement(v, c, "server-stop", true, "🔴 Server is stopping.");
        loadAnnouncement(v, c, "web-login", false, "🌐 {name} logged in to web chat.");
        loadAnnouncement(v, c, "web-logout", false, "🌐 {name} logged out of web chat.");

        v.uiLanguage = c.getString("ui.language", "en-US");
        v.uiLanguageFallback = c.getString("ui.language-fallback", "en-US");
        v.uiTimeZone = normalizeTimeZone(c.getString("ui.time-zone", "local"));
        v.linkifyUrls = c.getBoolean("ui.linkify-urls", true);
        v.imagePreviewEnabled = c.getBoolean("ui.image-preview-enabled", true);
        v.imagePreviewMaxPerMessage = Math.max(0, c.getInt("ui.image-preview-max-per-message", 3));
        v.imagePreviewMaxHeight = Math.max(0, c.getInt("ui.image-preview-max-height", 720));
        v.googleDriveImagePreview = c.getBoolean("ui.google-drive-image-preview", false);
        v.googleDrivePreviewMode = c.getString("ui.google-drive-preview-mode", "thumbnail");
        if (!"uc".equalsIgnoreCase(v.googleDrivePreviewMode)) v.googleDrivePreviewMode = "thumbnail";
        else v.googleDrivePreviewMode = "uc";
        v.externalMediaCacheEnabled = c.getBoolean("preview.external-media-cache-enabled", true);
        v.cacheDiscordCdn = c.getBoolean("preview.cache-discord-cdn", true);
        v.externalMediaCacheDirectory = c.getString("preview.external-media-cache-directory", "uploads/external-media-cache");
        v.externalMediaCacheMaxSizeMb = Math.max(0, c.getInt("preview.external-media-cache-max-size-mb", 20));
        v.externalMediaCacheRetentionDays = c.getInt("preview.external-media-cache-retention-days", 5);
        v.externalMediaCacheTimeoutSeconds = c.getInt("preview.external-media-cache-timeout-seconds", 6);
        v.youtubeEmbedEnabled = c.getBoolean("preview.youtube-embed-enabled", true);
        v.youtubeClickToLoad = c.getBoolean("preview.youtube-click-to-load", true);
        v.mediaClickToLoad = c.getBoolean("preview.media-click-to-load", true);
        v.youtubeNoCookie = c.getBoolean("preview.youtube-nocookie", true);
        v.youtubeRememberExpanded = c.getBoolean("preview.youtube-remember-expanded", true);
        v.youtubeAutoplayOnOpen = c.getBoolean("preview.youtube-autoplay-on-open", false);
        v.youtubeMaxEmbedsPerMessage = Math.max(0, c.getInt("preview.youtube-max-embeds-per-message", 1));
        v.socialEmbedsEnabled = c.getBoolean("preview.social-embeds.enabled", true);
        v.socialEmbedsClickToLoad = c.getBoolean("preview.social-embeds.click-to-load", true);
        v.socialEmbedsMaxPerMessage = Math.max(0, c.getInt("preview.social-embeds.max-embeds-per-message", 2));
        v.tiktokEmbedEnabled = c.getBoolean("preview.social-embeds.tiktok.enabled", false);
        v.xEmbedEnabled = c.getBoolean("preview.social-embeds.x.enabled", false);
        String xTheme = String.valueOf(c.getString("preview.social-embeds.x.theme", "auto")).trim().toLowerCase(Locale.ROOT);
        v.xEmbedTheme = (xTheme.equals("dark") || xTheme.equals("light")) ? xTheme : "auto";
        v.xEmbedDnt = c.getBoolean("preview.social-embeds.x.dnt", true);
        v.xEmbedHideMedia = c.getBoolean("preview.social-embeds.x.hide-media", false);
        v.xEmbedHideThread = c.getBoolean("preview.social-embeds.x.hide-thread", true);
        v.hideChatForGuestsWhenGuestDisabled = c.getBoolean("ui.hide-chat-for-guests-when-guest-disabled", true);
        v.showLoginOnlyWhenHidden = c.getBoolean("ui.show-login-only-when-hidden", true);
        v.uiResizable = c.getBoolean("ui.resizable", true);
        v.uiRememberWindowSize = c.getBoolean("ui.remember-window-size", true);
        v.uiDefaultWidth = c.getInt("ui.default-width", 372);
        v.uiDefaultHeight = c.getInt("ui.default-height", 462);
        v.uiMinWidth = c.getInt("ui.min-width", 280);
        v.uiMinHeight = c.getInt("ui.min-height", 240);
        v.uiMaxWidth = Math.max(0, c.getInt("ui.max-width", 640));
        v.uiMaxHeight = Math.max(0, c.getInt("ui.max-height", 720));
        v.uiFontSize = c.getInt("ui.font-size", 13);
        v.uiMessageFontSize = c.getInt("ui.message-font-size", 13);
        v.uiInputFontSize = c.getInt("ui.input-font-size", 13);
        v.uiTextColor = normalizeHexColor(c.getString("ui.text-color", ""));
        v.uiUiTextColor = normalizeHexColor(c.getString("ui.ui-text-color", ""));
        v.uiTextShadowMode = normalizeTextShadowMode(c.getString("ui.text-shadow-mode", "auto"));
        v.uiTextShadowCustom = sanitizeTextShadow(c.getString("ui.text-shadow-custom", "0 1px 2px rgba(0, 0, 0, 0.85)"));
        v.uiInputBackgroundColor = normalizeHexColor(c.getString("ui.input-background-color", ""));
        v.uiButtonFontSize = c.getInt("ui.button-font-size", 12);
        v.uiBadgeFontSize = c.getInt("ui.badge-font-size", 10);
        v.uiVirtualScrollEnabled = c.getBoolean("ui.virtual-scroll.enabled", true);
        v.uiVirtualScrollOverscanScreens = Math.max(0.0, c.getDouble("ui.virtual-scroll.overscan-screens", 0.75));
        v.uiVirtualScrollMinRenderedMessages = Math.max(0, c.getInt("ui.virtual-scroll.min-rendered-messages", 30));
        v.uiVirtualScrollPreserveVisibleMedia = c.getBoolean("ui.virtual-scroll.preserve-visible-media", false);
        v.uiVirtualScrollPreservePlayingMedia = c.getBoolean("ui.virtual-scroll.preserve-playing-media", true);
        v.uiHistoryPreloadScreens = Math.max(0.0, Math.min(5.0, c.getDouble("ui.history-preload.screens", 0.70)));
        v.uiHistoryPreloadMinPx = Math.max(0, Math.min(1000, c.getInt("ui.history-preload.min-px", 200)));
        v.uiAutoFollowBottomThresholdPx = Math.max(2, Math.min(300, c.getInt("ui.auto-follow-bottom-threshold-px", 80)));
        v.uiScrollInteractionIdleMs = Math.max(50, Math.min(1000, c.getInt("ui.scroll-interaction-idle-ms", 160)));
        v.uiResumeRefreshEnabled = c.getBoolean("ui.resume-refresh.enabled", true);
        v.uiResumeRefreshMinIntervalSeconds = Math.max(1, Math.min(300, c.getInt("ui.resume-refresh.min-interval-seconds", 5)));
        v.uiResumeRefreshSkipWhileMediaActive = c.getBoolean("ui.resume-refresh.skip-while-media-active", true);
        v.uiResumeRefreshSkipUnchanged = c.getBoolean("ui.resume-refresh.skip-unchanged", true);
        v.uiTheme = c.getString("ui.theme", "system");
        if (!List.of("system", "dark", "light", "high-contrast").contains(String.valueOf(v.uiTheme).toLowerCase(Locale.ROOT))) {
            v.uiTheme = "system";
        } else {
            v.uiTheme = v.uiTheme.toLowerCase(Locale.ROOT);
        }
        v.uiSyncBlueMapTheme = c.getBoolean("ui.sync-bluemap-theme", true);
        v.uiOpacity = Math.max(0.20, Math.min(1.00, c.getDouble("ui.opacity", 0.92)));
        v.uiUserPreferencesControl = c.getBoolean("ui.user-preferences-control", c.getBoolean("ui.user-opacity-control", true));
        v.uiUserFontOptions = c.getStringList("ui.user-font-options");
        if (v.uiUserFontOptions == null || v.uiUserFontOptions.isEmpty()) {
            v.uiUserFontOptions = List.of(
                    "",
                    "system-ui, sans-serif",
                    "Arial, sans-serif",
                    "Verdana, sans-serif",
                    "Georgia, serif",
                    "serif",
                    "monospace"
            );
        }
        v.uiFontFamily = c.getString("ui.font-family", "");
        v.uiPictureInPictureEnabled = c.getBoolean("ui.picture-in-picture.enabled", false);

        boolean notificationsEnabled = notificationBool(c, "enabled", legacyPairBool(c, "browser-notifications.enabled", "web-push.enabled", true));
        boolean notifyNormalChat = notificationBool(c, "notify-normal-chat", legacyPairBool(c, "browser-notifications.notify-normal-chat", "web-push.notify-normal-chat", true));
        boolean notifyDm = notificationBool(c, "notify-dm", legacyPairBool(c, "browser-notifications.notify-dm", "web-push.notify-dm", true));
        boolean notifyGroupChat = notificationBool(c, "notify-group-chat", legacyPairBool(c, "browser-notifications.notify-group-chat", "web-push.notify-group-chat", true));
        boolean notifyMentions = notificationBool(c, "notify-mentions", legacyPairBool(c, "browser-notifications.notify-mentions", "web-push.notify-mentions", true));
        boolean notifyReplies = notificationBool(c, "notify-replies", legacyPairBool(c, "browser-notifications.notify-replies", "web-push.notify-replies", true));
        boolean notifySystem = notificationBool(c, "notify-system", legacyPairBool(c, "browser-notifications.notify-system", "web-push.notify-system", true));
        boolean notifyKeywords = notificationBool(c, "notify-keywords", legacyPairBool(c, "browser-notifications.notify-keywords", "web-push.notify-keywords", true));
        boolean notifyOwnMessages = notificationBool(c, "notify-own-messages", legacyPairBool(c, "browser-notifications.notify-own-messages", "web-push.notify-own-messages", true));
        boolean showMessagePreview = notificationBool(c, "show-message-preview", legacyPairBool(c, "browser-notifications.show-message-preview", "web-push.show-message-preview", true));

        v.browserNotificationsEnabled = notificationsEnabled;
        v.browserNotificationsOnlyWhenHidden = notificationBool(c, "only-when-hidden", configBool(c, "browser-notifications.only-when-hidden", true));
        v.browserNotificationsNotifyNormalChat = notifyNormalChat;
        v.browserNotificationsNotifyDm = notifyDm;
        v.browserNotificationsNotifyGroupChat = notifyGroupChat;
        v.browserNotificationsNotifyMentions = notifyMentions;
        v.browserNotificationsNotifyReplies = notifyReplies;
        v.browserNotificationsNotifySystem = notifySystem;
        v.browserNotificationsNotifyKeywords = notifyKeywords;
        v.browserNotificationsNotifyOwnMessages = notifyOwnMessages;
        v.browserNotificationsShowMessagePreview = showMessagePreview;

        v.webPushEnabled = notificationsEnabled;
        v.webPushVapidPublicKey = c.getString("web-push.vapid-public-key", "");
        v.webPushVapidPrivateKey = c.getString("web-push.vapid-private-key", "");
        v.webPushSubject = c.getString("web-push.subject", "mailto:admin@example.com");
        v.webPushNotificationTitle = normalizeDisplayName(c.getString("web-push.notification-title", ""), "");
        if (v.webPushNotificationTitle == null || v.webPushNotificationTitle.isBlank()) v.webPushNotificationTitle = v.standaloneWebAppName;
        if (v.webPushNotificationTitle == null || v.webPushNotificationTitle.isBlank()) v.webPushNotificationTitle = "Web Chat";
        v.webPushSubscriptionsFile = c.getString("web-push.subscriptions-file", "web-push-subscriptions.jsonl");
        v.webPushTtlSeconds = Math.max(30, Math.min(86400, c.getInt("web-push.ttl-seconds", 300)));
        v.webPushNotifyNormalChat = notifyNormalChat;
        v.webPushNotifyDm = notifyDm;
        v.webPushNotifyGroupChat = notifyGroupChat;
        v.webPushNotifyMentions = notifyMentions;
        v.webPushNotifyReplies = notifyReplies;
        v.webPushNotifySystem = notifySystem;
        v.webPushNotifyKeywords = notifyKeywords;
        v.webPushNotifyOwnMessages = notifyOwnMessages;
        v.webPushShowMessagePreview = showMessagePreview;

        v.playerNameMode = c.getString("player-display.mode", "name");
        if (v.playerNameMode == null) v.playerNameMode = "name";
        v.playerNameMode = v.playerNameMode.toLowerCase(Locale.ROOT).replace('_', '-').trim();
        if (!List.of("name", "display-name", "custom-name").contains(v.playerNameMode)) {
            v.playerNameMode = "name";
        }
        v.playerNameStripColors = c.getBoolean("player-display.strip-colors", true);
        v.webFontsEnabled = c.getBoolean("web-fonts.enabled", false);
        v.webFontsDirectory = c.getString("web-fonts.directory", "fonts");
        v.webFontsItems = sanitizeWebFonts(c.getMapList("web-fonts.items"));

        v.guestEnabled = c.getBoolean("guest.enabled", true);
        v.guestAllowCustomName = c.getBoolean("guest.allow-custom-name", true);
        v.guestNamePrefix = c.getString("guest.name-prefix", "Guest-");
        v.guestCooldownSeconds = c.getInt("guest.cooldown-seconds", 5);
        v.guestMaxMessagesPerMinute = c.getInt("guest.max-messages-per-minute", 50);
        v.guestBlockPlayerNameSpoofing = c.getBoolean("guest.block-player-name-spoofing", true);
        v.guestBlockedNames = c.getStringList("guest.blocked-names");

        v.captchaMode = c.getString("captcha.mode", "math").toLowerCase();
        v.captchaExpireSeconds = c.getInt("captcha.expire-seconds", 120);
        v.captchaRequireOnEachMessage = c.getBoolean("captcha.require-on-each-message", false);
        v.captchaPassValidMinutes = c.getInt("captcha.pass-valid-minutes", 1440);

        v.authEnabled = c.getBoolean("auth.enabled", true);
        v.linkCodeLength = c.getInt("auth.link-code-length", 6);
        v.linkCodeExpireSeconds = c.getInt("auth.link-code-expire-seconds", 180);
        v.authCodeCooldownSeconds = Math.max(0, c.getInt("auth.link-code-cooldown-seconds", 3));
        v.authCodeMaxPerMinute = Math.max(0, c.getInt("auth.link-code-max-per-minute", 10));
        v.passwordLogin = c.getBoolean("auth.password-login", true);
        v.rememberSessionDays = c.getInt("auth.remember-session-days", 30);
        v.autoAdminFromPermission = c.getBoolean("auth.auto-admin-from-permission", true);
        v.adminPermission = c.getString("auth.admin-permission", "bluemapwebchat.admin");

        v.loginFailLimit = Math.max(0, c.getInt("security.login-fail-limit", 5));
        v.loginFailWindowSeconds = Math.max(1, c.getInt("security.login-fail-window-seconds", 300));
        v.loginLockSeconds = Math.max(0, c.getInt("security.login-lock-seconds", 600));
        v.maxSseConnectionsPerIp = Math.max(0, c.getInt("security.max-sse-connections-per-ip", 5));
        v.maxSseConnectionsTotal = Math.max(0, c.getInt("security.max-sse-connections-total", 200));

        v.allowLocalAdminAccounts = c.getBoolean("admin.allow-local-admin-accounts", true);
        v.adminSessionExpireHours = c.getInt("admin.admin-session-expire-hours", 12);
        v.allowAdminLoginFrom = c.getStringList("admin.allow-admin-login-from");

        v.moderationEnabled = c.getBoolean("moderation.enabled", true);
        v.allowWebAdminPanel = c.getBoolean("moderation.allow-web-admin-panel", true);
        v.allowModeratorMessageDelete = c.getBoolean("moderation.allow-moderator-message-delete", true);
        v.allowModeratorGuestMute = c.getBoolean("moderation.allow-moderator-guest-mute", true);
        v.defaultMuteMinutes = c.getInt("moderation.default-mute-minutes", 60);

        v.discordEnabled = c.getBoolean("discordsrv.enabled", false);
        v.discordChannel = c.getString("discordsrv.channel", "global");
        v.discordWebToDiscord = c.getBoolean("discordsrv.web-to-discord", true);
        v.discordDiscordToWeb = c.getBoolean("discordsrv.discord-to-web", true);
        v.discordIgnoreBotMessages = c.getBoolean("discordsrv.ignore-bot-messages", true);
        v.discordSuppressGameEcho = c.getBoolean("discordsrv.suppress-game-echo", true);
        v.discordSuppressGameEchoSeconds = c.getInt("discordsrv.suppress-game-echo-seconds", 5);
        v.discordWebToDiscordFormat = c.getString("discordsrv.web-to-discord-format", "[{server}] [Web] {sender}: {message}");
        v.discordGameToDiscord = c.getBoolean("discordsrv.game-to-discord", false);
        v.discordGameToDiscordFormat = c.getString("discordsrv.game-to-discord-format", "[{server}] {sender}: {message}");
        v.discordAppendGameEmojiLinks = c.getBoolean("discordsrv.append-game-emoji-links", true);
        v.discordToWebSenderFormat = c.getString("discordsrv.discord-to-web-sender-format", "Discord:{sender}");
        v.discordToWebMessageFormat = c.getString("discordsrv.discord-to-web-message-format", "{message}");
        v.discordSendWebUser = c.getBoolean("discordsrv.send-web-user-chat-to-discord", true);
        v.discordSendWebGuest = c.getBoolean("discordsrv.send-web-guest-chat-to-discord", false);
        v.discordSendWebAdmin = c.getBoolean("discordsrv.send-web-admin-chat-to-discord", true);
        v.discordAppendWebEmojiLinks = c.getBoolean("discordsrv.append-web-emoji-links", true);
        v.discordMaxEmojiLinksPerMessage = Math.max(0, c.getInt("discordsrv.max-emoji-links-per-message", 4));
        // Discord reply relay is intentionally separate from reply.game-preview.
        // The game preview can be useful in Minecraft chat, but duplicating the replied
        // message in Discord often looks like an unexpected extra/comment line.
        v.discordReplyRelayEnabled = c.getBoolean("discordsrv.reply-relay.enabled", false);
        v.discordReplyPrefixEnabled = c.getBoolean("discordsrv.reply-relay.prefix-enabled", true);
        v.discordReplyPreviewEnabled = c.getBoolean("discordsrv.reply-relay.preview-enabled", true);
        v.discordReplyPreviewMaxLength = Math.max(0, c.getInt("discordsrv.reply-relay.preview-max-length", 120));

        v.uploadEnabled = c.getBoolean("upload.enabled", true);
        v.uploadAllowGuest = c.getBoolean("upload.allow-guest-upload", false);
        v.uploadAllowUser = c.getBoolean("upload.allow-user-upload", true);
        v.uploadAllowModerator = c.getBoolean("upload.allow-moderator-upload", true);
        v.uploadAllowAdmin = c.getBoolean("upload.allow-admin-upload", true);
        v.uploadCooldownSeconds = c.getInt("upload.cooldown-seconds", 5);
        v.uploadMaxUploadsPerMinute = Math.max(0, c.getInt("upload.max-uploads-per-minute", 4));
        v.uploadMaxFileSizeMb = Math.max(0, c.getInt("upload.max-file-size-mb", 20));
        v.uploadMaxTotalSizeMb = Math.max(0, c.getInt("upload.max-total-size-mb", 0));
        v.uploadMaxFilesPerMessage = Math.max(0, c.getInt("upload.max-files-per-message", 3));
        v.uploadDirectory = c.getString("upload.directory", "uploads");
        v.uploadPublicBaseUrl = c.getString("upload.public-base-url", "");
        v.uploadRetentionDays = c.getInt("upload.retention-days", 5);
        v.uploadAllowedExtensions = c.getStringList("upload.allowed-extensions");
        if (v.uploadAllowedExtensions == null || v.uploadAllowedExtensions.isEmpty()) {
            v.uploadAllowedExtensions = List.of(
                    "png", "jpg", "jpeg", "gif", "webp",
                    "mp4", "webm",
                    "mp3", "m4a", "ogg", "wav", "flac"
            );
        }
        v.uploadClipboardEnabled = c.getBoolean("upload.clipboard-upload-enabled", true);
        v.uploadClipboardSendMode = c.getString("upload.clipboard-upload-send-mode", "insert");
        if (!"send".equalsIgnoreCase(v.uploadClipboardSendMode)) v.uploadClipboardSendMode = "insert";
        else v.uploadClipboardSendMode = "send";
        v.uploadClipboardImageDefaultExtension = c.getString("upload.clipboard-image-default-extension", "png");
        v.uploadPreviewImages = c.getBoolean("upload.preview-images", true);
        v.uploadPreviewVideos = c.getBoolean("upload.preview-videos", true);
        v.uploadPreviewAudio = c.getBoolean("upload.preview-audio", true);


        v.emojiEnabled = c.getBoolean("emoji.enabled", true);
        v.emojiShowButton = c.getBoolean("emoji.show-button", true);
        v.emojiDirectory = c.getString("emoji.directory", "emojis");
        v.emojiPublicBaseUrl = c.getString("emoji.public-base-url", "");
        v.emojiMaxFileSizeKb = Math.max(0, c.getInt("emoji.max-file-size-kb", 512));
        v.emojiMaxTotalSizeMb = Math.max(0, c.getInt("emoji.max-total-size-mb", 64));
        v.emojiShowStorageUsage = c.getBoolean("emoji.show-storage-usage", true);
        v.emojiShowStorageLimit = c.getBoolean("emoji.show-storage-limit", true);
        v.emojiRenderSizePx = Math.max(16, Math.min(1024, c.getInt("emoji.render-size-px", 32)));
        v.emojiPickerSizePx = Math.max(24, Math.min(1024, c.getInt("emoji.picker-size-px", 44)));
        v.emojiMessageTokenLimit = Math.max(0, c.getInt("emoji.message-token-limit", 12));
        String emojiTokenFormat = String.valueOf(c.getString("emoji.token-format", "short")).trim().toLowerCase(Locale.ROOT);
        v.emojiTokenFormat = (emojiTokenFormat.equals("legacy") || emojiTokenFormat.equals("prefixed") || emojiTokenFormat.equals("emoji")) ? "legacy" : "short";
        v.emojiAllowedExtensions = c.getStringList("emoji.allowed-extensions");
        if (v.emojiAllowedExtensions == null || v.emojiAllowedExtensions.isEmpty()) {
            v.emojiAllowedExtensions = List.of("png", "jpg", "jpeg", "gif", "webp");
        }
        v.emojiGameLinkEnabled = c.getBoolean("emoji.game-link.enabled", false);
        String emojiGameLinkMode = String.valueOf(c.getString("emoji.game-link.mode", "link")).trim().toLowerCase(Locale.ROOT);
        if (emojiGameLinkMode.equals("preserve") || emojiGameLinkMode.equals("token") || emojiGameLinkMode.equals("original") || emojiGameLinkMode.equals("none")) {
            v.emojiGameLinkMode = "preserve";
        } else if (emojiGameLinkMode.equals("label") || emojiGameLinkMode.equals("template") || emojiGameLinkMode.equals("text")) {
            v.emojiGameLinkMode = "label";
        } else {
            v.emojiGameLinkMode = "link";
        }
        v.emojiGameLinkPublicApiBaseUrl = c.getString("emoji.game-link.public-api-base-url", "");
        v.emojiGameLinkLabelFormat = c.getString("emoji.game-link.label-format", ":{id}:");
        v.emojiGameLinkMaxLinksPerMessage = Math.max(0, c.getInt("emoji.game-link.max-links-per-message", 4));
        v.emojiGameLinkDefaultPack = String.valueOf(c.getString("emoji.game-link.default-pack", "")).trim();
        v.emojiGameLinkAliases = new LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection gameLinkAliases = c.getConfigurationSection("emoji.game-link.aliases");
        if (gameLinkAliases != null) {
            for (String key : gameLinkAliases.getKeys(false)) {
                String alias = String.valueOf(key == null ? "" : key).trim();
                String id = String.valueOf(gameLinkAliases.getString(key, "")).trim();
                if (!alias.isBlank() && !id.isBlank()) v.emojiGameLinkAliases.put(alias, id);
            }
        }

        v.replyGamePrefixEnabled = c.getBoolean("reply.game-prefix.enabled", true);
        v.replyGamePrefixText = translateConfiguredGameFormat(c.getString("reply.game-prefix.text", "↪ [Reply] "));
        v.replyGamePreviewEnabled = c.getBoolean("reply.game-preview.enabled", true);
        v.replyGamePreviewFormat = translateConfiguredGameFormat(c.getString("reply.game-preview.format", "&7{sender}: {preview}"));
        v.replyGamePreviewMaxLength = Math.max(0, c.getInt("reply.game-preview.max-length", 120));
        v.replyGameClickEnabled = c.getBoolean("reply.game-click.enabled", false);
        v.replyGameClickLocalChat = c.getBoolean("reply.game-click.local-game-chat", false);
        v.replyGameCommandFormat = translateConfiguredGameFormat(c.getString("reply.game-command-format", "&8[&dReply&8] &f{player}&7: &f{message}"));

        v.pinnedEnabled = c.getBoolean("pinned.enabled", true);
        v.pinnedMaxPins = Math.max(0, c.getInt("pinned.max-pins", 20));
        v.pinnedShowToLoggedOut = c.getBoolean("pinned.show-to-logged-out", true);
        v.pinnedPreserveUploads = c.getBoolean("pinned.preserve-uploads", true);

        v.commandsEnabled = c.getBoolean("commands.enabled", false);
        v.commandsAllowAll = c.getBoolean("commands.allow-all", false);
        v.commandsMinRole = Role.fromString(c.getString("commands.min-role", "ADMIN"), Role.ADMIN);
        if (v.commandsMinRole == Role.GUEST) v.commandsMinRole = Role.ADMIN;
        v.commandsShowButton = c.getBoolean("commands.show-button", true);
        v.commandsShowSlashPanel = c.getBoolean("commands.show-when-input-starts-with-slash", true);
        v.commandsRunFromChatInput = c.getBoolean("commands.run-from-chat-input", false);
        v.commandsRequireConfirm = c.getBoolean("commands.require-confirm", true);
        v.commandsMaxLength = Math.max(0, c.getInt("commands.max-length", 0));
        v.commandsBroadcastToWebChat = c.getBoolean("commands.broadcast-result-to-web-chat", false);
        v.commandPresets = sanitizeCommandPresets(c.getMapList("commands.presets"));

        return v;
    }


    private static void loadAnnouncement(ConfigValues v, FileConfiguration c, String key, boolean defaultEnabled, String defaultMessage) {
        boolean enabled = c.getBoolean("announcements." + key + ".enabled", defaultEnabled);
        String message = c.getString("announcements." + key + ".message", defaultMessage);
        v.announcements.put(key, new AnnouncementConfig(enabled, message));
    }


    private static List<RelayPeer> sanitizeRelayPeers(List<Map<?, ?>> raw) {
        List<RelayPeer> out = new ArrayList<>();
        if (raw == null) return out;
        for (Map<?, ?> item : raw) {
            if (item == null) continue;
            boolean enabled = boolValue(item.get("enabled"), true);
            String id = normalizeRelayId(String.valueOf(mapValue(item, "id", "")));
            String url = String.valueOf(mapValue(item, "url", "")).trim();
            String secret = String.valueOf(mapValue(item, "secret", "")).trim();
            // Keep incomplete entries so ServerRelay can report exactly why a configured
            // peer was ignored instead of silently reducing the loaded peer count.
            out.add(new RelayPeer(id, url, secret, enabled));
        }
        return out;
    }

    private static String normalizeRelayId(String raw) {
        String id = String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
        id = id.replaceAll("[^a-z0-9._-]", "-");
        while (id.contains("--")) id = id.replace("--", "-");
        while (id.startsWith("-")) id = id.substring(1);
        while (id.endsWith("-")) id = id.substring(0, id.length() - 1);
        return id.length() > 64 ? id.substring(0, 64) : id;
    }

    private static List<CommandPreset> sanitizeCommandPresets(List<Map<?, ?>> raw) {
        List<CommandPreset> out = new ArrayList<>();
        if (raw == null) return out;

        for (Map<?, ?> item : raw) {
            if (item == null) continue;
            String id = String.valueOf(mapValue(item, "id", "")).trim().toLowerCase(Locale.ROOT);
            id = id.replaceAll("[^a-z0-9._-]", "-");
            while (id.contains("--")) id = id.replace("--", "-");
            if (id.startsWith("-")) id = id.substring(1);
            if (id.endsWith("-")) id = id.substring(0, id.length() - 1);

            String command = String.valueOf(mapValue(item, "command", "")).trim();
            if (command.startsWith("/")) command = command.substring(1).trim();
            if (command.contains("\n") || command.contains("\r") || command.contains("\0")) continue;
            if (command.isBlank()) continue;
            if (id.isBlank()) id = command.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
            if (id.length() > 64) id = id.substring(0, 64);
            if (id.isBlank()) continue;

            String label = String.valueOf(mapValue(item, "label", id)).trim();
            String description = String.valueOf(mapValue(item, "description", "")).trim();
            boolean enabled = boolValue(item.get("enabled"), true);
            boolean requireConfirm = boolValue(item.get("confirm"), true);
            out.add(new CommandPreset(id, label, description, command, enabled, requireConfirm));
        }
        return out;
    }

    private static Object mapValue(Map<?, ?> map, String key, Object fallback) {
        Object value = map == null ? null : map.get(key);
        return value == null ? fallback : value;
    }

    private static boolean boolValue(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("yes") || s.equals("1") || s.equals("on")) return true;
        if (s.equals("false") || s.equals("no") || s.equals("0") || s.equals("off")) return false;
        return fallback;
    }

    private static List<Map<String, Object>> sanitizeWebFonts(List<Map<?, ?>> raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null) return out;

        for (Map<?, ?> item : raw) {
            if (item == null) continue;

            Object familyRaw = item.get("family");
            Object fileRaw = item.get("file");
            Object styleRaw = item.get("style");
            Object weightRaw = item.get("weight");

            String family = String.valueOf(familyRaw == null ? "" : familyRaw).trim();
            String file = String.valueOf(fileRaw == null ? "" : fileRaw).trim().replace("\\", "/");
            String style = String.valueOf(styleRaw == null ? "normal" : styleRaw).trim().toLowerCase(Locale.ROOT);
            if (weightRaw == null) weightRaw = 400;

            if (family.isEmpty() || file.isEmpty()) continue;
            if (file.contains("..") || file.startsWith("/") || file.contains("\0")) continue;
            if (!file.toLowerCase(Locale.ROOT).matches(".*\\.(woff2|woff|ttf|otf)$")) continue;
            if (!style.equals("normal") && !style.equals("italic") && !style.equals("oblique")) style = "normal";

            int weight = 400;
            try {
                weight = Integer.parseInt(String.valueOf(weightRaw));
            } catch (NumberFormatException ignored) {
            }
            if (weight < 100 || weight > 900) weight = 400;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("family", family);
            m.put("file", file);
            m.put("weight", weight);
            m.put("style", style);
            out.add(m);
        }

        return out;
    }


    private static boolean configBool(FileConfiguration c, String path, boolean def) {
        return c != null && c.contains(path) ? c.getBoolean(path) : def;
    }

    private static boolean notificationBool(FileConfiguration c, String key, boolean def) {
        return configBool(c, "notifications." + key, def);
    }

    private static boolean legacyPairBool(FileConfiguration c, String browserPath, String webPushPath, boolean def) {
        // Legacy compatibility only: 4.5.2 and older had separate browser-notifications.*
        // and web-push.* switches. In 4.5.3+, notifications.* is the single default source.
        // If an old config still contains either legacy switch as false, keep the safer
        // blocked result for both delivery paths.
        return configBool(c, browserPath, def) && configBool(c, webPushPath, def);
    }

    private static String normalizeDisplayName(String value, String fallback) {
        String v = String.valueOf(value == null ? "" : value).trim();
        // Treat old generated defaults as legacy placeholders, not intentional custom names.
        // Existing configs generated before the display-name options may still contain these
        // values, which made notifications keep showing the plugin name even after the
        // runtime fallback was changed.
        if (v.equalsIgnoreCase("BlueMapWebChat") || v.equalsIgnoreCase("BM WebChat")) v = "";
        if (v.isBlank()) return String.valueOf(fallback == null ? "" : fallback).trim();
        return v;
    }


    private static String normalizePrefix(String s) {
        if (s == null || s.isBlank()) return "";
        s = s.trim();
        if (!s.startsWith("/")) s = "/" + s;
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }



    private static String normalizeHexColor(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.matches("^#[0-9a-fA-F]{6}$")) return v.toLowerCase(Locale.ROOT);
        if (v.matches("^#[0-9a-fA-F]{3}$")) {
            char r = v.charAt(1);
            char g = v.charAt(2);
            char b = v.charAt(3);
            return ("#" + r + r + g + g + b + b).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static String normalizeTextShadowMode(String value) {
        if (value == null) return "auto";
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (List.of("none", "auto", "dark", "light", "custom").contains(v)) return v;
        return "auto";
    }

    private static String sanitizeTextShadow(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() > 120) v = v.substring(0, 120);
        // CSS text-shadow does not need URLs/functions other than rgb/rgba. Keep only conservative characters.
        if (!v.matches("^[#a-zA-Z0-9(),.%\\s+\\-]*$")) return "0 1px 2px rgba(0, 0, 0, 0.85)";
        if (v.toLowerCase(Locale.ROOT).contains("url")) return "0 1px 2px rgba(0, 0, 0, 0.85)";
        return v;
    }

    private static String normalizeTimeZone(String value) {
        if (value == null) return "local";
        String tz = value.trim();
        if (tz.isEmpty()) return "local";
        if (tz.equalsIgnoreCase("local")) return "local";
        try {
            return java.time.ZoneId.of(tz).getId();
        } catch (Exception ignored) {
            return "local";
        }
    }

    private static String stripSlashes(String s) {
        if (s == null) return "";
        s = s.trim().replace("\\", "/");
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
    private static String translateConfiguredGameFormat(String value) {
        return ChatColor.translateAlternateColorCodes('&', String.valueOf(value == null ? "" : value));
    }

}
