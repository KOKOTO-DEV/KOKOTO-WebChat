package dev.kokoto.webchat;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Bukkit YAML loader for the platform-neutral ConfigValues data object. */
public final class BukkitConfigValuesLoader {
    private BukkitConfigValuesLoader() {}

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
        v.publicPrefix = normalizePublicPrefix(c.getString("http.public-prefix", "/chat"));
        v.corsOrigin = c.getString("http.cors-origin", "*");
        v.trustedProxies = c.getStringList("http.trusted-proxies");
        v.logClientIpResolution = c.getBoolean("http.log-client-ip-resolution", false);

        v.standaloneWebEnabled = c.getBoolean("frontend.standalone.enabled", true);
        v.standaloneWebPath = normalizePrefix(c.getString("frontend.standalone.path", "/"));
        v.standaloneWebApiBaseUrl = c.getString("frontend.standalone.api-base-url", "");
        v.standaloneWebAppName = normalizeDisplayName(c.getString("frontend.standalone.app-name", "KOKOTO WebChat"), "KOKOTO WebChat");
        v.standaloneWebAppShortName = normalizeDisplayName(c.getString("frontend.standalone.app-short-name", "KWC"), "KWC");
        if (v.standaloneWebAppShortName == null || v.standaloneWebAppShortName.isBlank()) v.standaloneWebAppShortName = v.standaloneWebAppName;

        v.bluemapEnabled = c.getBoolean("adapters.bluemap.enabled", false);
        v.webAutoInstall = c.getBoolean("adapters.bluemap.auto-install", true);
        v.webAutoPatch = c.getBoolean("adapters.bluemap.auto-patch-webapp-conf", true);
        v.bluemapWebRoot = c.getString("adapters.bluemap.bluemap-web-root", "bluemap/web");
        v.bluemapWebappConf = c.getString("adapters.bluemap.bluemap-webapp-conf", "plugins/BlueMap/webapp.conf");
        v.addonPath = stripSlashes(c.getString("adapters.bluemap.addon-path", "addons/kokoto-web-chat"));
        v.apiBaseUrl = c.getString("adapters.bluemap.api-base-url", "");

        v.squaremapEnabled = c.getBoolean("adapters.squaremap.enabled", false);
        v.squaremapAutoInstall = c.getBoolean("adapters.squaremap.auto-install", true);
        v.squaremapAutoPatchIndex = c.getBoolean("adapters.squaremap.auto-patch-index", true);
        v.squaremapWebRoot = c.getString("adapters.squaremap.web-root", "");
        v.squaremapAddonPath = stripSlashes(c.getString("adapters.squaremap.addon-path", "kokoto-web-chat"));
        v.squaremapApiBaseUrl = c.getString("adapters.squaremap.api-base-url", "");

        v.dynmapEnabled = c.getBoolean("adapters.dynmap.enabled", false);
        v.dynmapAutoInstall = c.getBoolean("adapters.dynmap.auto-install", true);
        v.dynmapAutoPatchIndex = c.getBoolean("adapters.dynmap.auto-patch-index", true);
        v.dynmapWebRoot = c.getString("adapters.dynmap.web-root", "");
        v.dynmapAddonPath = stripSlashes(c.getString("adapters.dynmap.addon-path", "kokoto-web-chat"));
        v.dynmapApiBaseUrl = c.getString("adapters.dynmap.api-base-url", "");

        v.pl3xmapEnabled = c.getBoolean("adapters.pl3xmap.enabled", false);
        v.pl3xmapAutoInstall = c.getBoolean("adapters.pl3xmap.auto-install", true);
        v.pl3xmapAutoPatchIndex = c.getBoolean("adapters.pl3xmap.auto-patch-index", true);
        v.pl3xmapWebRoot = c.getString("adapters.pl3xmap.web-root", "");
        v.pl3xmapAddonPath = stripSlashes(c.getString("adapters.pl3xmap.addon-path", "kokoto-web-chat"));
        v.pl3xmapApiBaseUrl = c.getString("adapters.pl3xmap.api-base-url", "");

        v.liveAtlasEnabled = c.getBoolean("adapters.liveatlas.enabled", false);
        v.liveAtlasAutoInstall = c.getBoolean("adapters.liveatlas.auto-install", true);
        v.liveAtlasAutoPatchIndex = c.getBoolean("adapters.liveatlas.auto-patch-index", true);
        v.liveAtlasWebRoot = c.getString("adapters.liveatlas.web-root", "");
        v.liveAtlasAddonPath = stripSlashes(c.getString("adapters.liveatlas.addon-path", "kokoto-web-chat"));
        v.liveAtlasApiBaseUrl = c.getString("adapters.liveatlas.api-base-url", "");

        v.unminedEnabled = c.getBoolean("adapters.unmined.enabled", false);
        v.unminedAutoInstall = c.getBoolean("adapters.unmined.auto-install", true);
        v.unminedAutoPatchIndex = c.getBoolean("adapters.unmined.auto-patch-index", true);
        v.unminedWebRoot = c.getString("adapters.unmined.web-root", "");
        v.unminedAddonPath = stripSlashes(c.getString("adapters.unmined.addon-path", "kokoto-web-chat"));
        v.unminedApiBaseUrl = c.getString("adapters.unmined.api-base-url", "");

        v.overviewerEnabled = c.getBoolean("adapters.overviewer.enabled", false);
        v.overviewerAutoInstall = c.getBoolean("adapters.overviewer.auto-install", true);
        v.overviewerAutoPatchIndex = c.getBoolean("adapters.overviewer.auto-patch-index", true);
        v.overviewerWebRoot = c.getString("adapters.overviewer.web-root", "");
        v.overviewerAddonPath = stripSlashes(c.getString("adapters.overviewer.addon-path", "kokoto-web-chat"));
        v.overviewerApiBaseUrl = c.getString("adapters.overviewer.api-base-url", "");

        v.historySize = c.getInt("chat.history-size", 0);
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
        v.serverRelayForwardReceivedPublicChat = c.getBoolean("server-relay.forward-received-public-chat", true);
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
        v.uiHistoryPreloadScreens = Math.max(0.0, Math.min(5.0, c.getDouble("ui.history-preload.screens", 0.70)));
        v.uiHistoryPreloadMinPx = Math.max(0, Math.min(1000, c.getInt("ui.history-preload.min-px", 200)));
        v.uiAutoFollowBottomThresholdPx = Math.max(2, Math.min(300, c.getInt("ui.auto-follow-bottom-threshold-px", 80)));
        v.uiScrollInteractionIdleMs = Math.max(50, Math.min(1000, c.getInt("ui.scroll-interaction-idle-ms", 160)));
        v.uiResumeRefreshEnabled = c.getBoolean("ui.resume-refresh.enabled", true);
        v.uiResumeRefreshMinIntervalSeconds = Math.max(1, Math.min(300, c.getInt("ui.resume-refresh.min-interval-seconds", 5)));
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
        v.uiUserProfilesEnabled = c.getBoolean("ui.user-profiles.enabled", true);
        v.uiUserProfilesMaxProfiles = Math.max(0, Math.min(20, c.getInt("ui.user-profiles.max-profiles", 5)));
        v.uiUserProfilesAllowImportExport = c.getBoolean("ui.user-profiles.allow-import-export", true);
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
        v.guestCooldownSeconds = c.getInt("guest.cooldown-seconds", 6);
        v.guestMaxMessagesPerMinute = c.getInt("guest.max-messages-per-minute", 50);
        v.guestBlockPlayerNameSpoofing = c.getBoolean("guest.block-player-name-spoofing", true);
        v.guestBlockedNames = c.getStringList("guest.blocked-names");

        v.captchaMode = c.getString("captcha.mode", "math").toLowerCase();
        v.captchaExpireSeconds = c.getInt("captcha.expire-seconds", 120);
        v.captchaRequireOnEachMessage = c.getBoolean("captcha.require-on-each-message", false);
        v.captchaPassValidMinutes = c.getInt("captcha.pass-valid-minutes", 120);

        v.authEnabled = c.getBoolean("auth.enabled", true);
        v.linkCodeLength = c.getInt("auth.link-code-length", 6);
        v.linkCodeExpireSeconds = c.getInt("auth.link-code-expire-seconds", 180);
        v.authCodeCooldownSeconds = Math.max(0, c.getInt("auth.link-code-cooldown-seconds", 3));
        v.authCodeMaxPerMinute = Math.max(0, c.getInt("auth.link-code-max-per-minute", 10));
        v.passwordLogin = c.getBoolean("auth.password-login", true);
        v.rememberSessionDays = c.getInt("auth.remember-session-days", 30);
        v.autoAdminFromPermission = c.getBoolean("auth.auto-admin-from-permission", true);
        v.adminPermission = c.getString("auth.admin-permission", "kwc.admin");

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

        v.contentFilterEnabled = c.getBoolean("content-filter.enabled", false);
        v.contentFilterPublic = c.getBoolean("content-filter.scopes.public", true);
        v.contentFilterGroup = c.getBoolean("content-filter.scopes.group", true);
        v.contentFilterDm = c.getBoolean("content-filter.scopes.dm", false);
        v.contentFilterShowMatchedWord = c.getBoolean("content-filter.block.show-matched-word", true);
        v.contentFilterMaskText = c.getString("content-filter.mask.text", "***");
        v.contentFilterUnicodeNormalization = c.getBoolean("content-filter.anti-evasion.unicode-normalization", true);
        v.contentFilterCompactMatch = c.getBoolean("content-filter.anti-evasion.compact-match", true);
        v.contentFilterInterleaveMatch = c.getBoolean("content-filter.anti-evasion.interleave-match", true);
        v.contentFilterInterleaveMaxGap = Math.max(0, c.getInt("content-filter.anti-evasion.interleave-max-gap", 2));
        v.contentFilterInterleaveUnlimitedGap = c.getBoolean("content-filter.anti-evasion.interleave-unlimited-gap", false);
        v.contentFilterCollapseRepeats = c.getBoolean("content-filter.anti-evasion.collapse-repeats", false);
        v.contentFilterRepeatLimit = Math.max(1, c.getInt("content-filter.anti-evasion.repeat-limit", 1));
        v.contentFilterRules = ContentFilterConfigParser.parse(c.getMapList("content-filter.rules"));

        v.discordEnabled = c.getBoolean("discordsrv.enabled", false);
        v.discordChannel = c.getString("discordsrv.channel", "global");
        v.discordWebToDiscord = c.getBoolean("discordsrv.web-to-discord", true);
        v.discordDiscordToWeb = c.getBoolean("discordsrv.discord-to-web", true);
        v.discordIgnoreBotMessages = c.getBoolean("discordsrv.ignore-bot-messages", true);
        v.discordSuppressGameEcho = c.getBoolean("discordsrv.suppress-game-echo", true);
        v.discordSuppressGameEchoSeconds = c.getInt("discordsrv.suppress-game-echo-seconds", 5);
        v.discordWebToDiscordFormat = c.getString("discordsrv.web-to-discord-format", "[{server}] [Web] {sender}: {message}");
        v.discordGameRelayMode = c.getString("discordsrv.game-relay-mode", "discordsrv").trim().toLowerCase(java.util.Locale.ROOT);
        if (!v.discordGameRelayMode.equals("discordsrv") && !v.discordGameRelayMode.equals("kwc")) {
            v.discordGameRelayMode = "discordsrv";
        }
        v.discordGameRelayFormat = c.getString("discordsrv.game-relay-format", "[{server}] {sender}: {message}");
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
        v.adminDiscordAlertsEnabled = c.getBoolean("admin-alerts.discord.enabled", false);
        v.adminDiscordAlertsChannel = c.getString("admin-alerts.discord.channel", "").trim();
        if (v.adminDiscordAlertsChannel.isBlank()) v.adminDiscordAlertsChannel = v.discordChannel;
        v.adminDiscordAlertsPublicChat = c.getBoolean("admin-alerts.discord.sources.public-chat", true);
        v.adminDiscordAlertsRelayChat = c.getBoolean("admin-alerts.discord.sources.relay-chat", false);
        v.adminDiscordAlertsDm = c.getBoolean("admin-alerts.discord.sources.dm", false);
        v.adminDiscordAlertsGroupChat = c.getBoolean("admin-alerts.discord.sources.group-chat", false);
        v.adminDiscordAlertsMention = c.getString("admin-alerts.discord.mention", "none").trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("none", "here", "everyone").contains(v.adminDiscordAlertsMention)) v.adminDiscordAlertsMention = "none";
        v.adminDiscordAlertsCaseSensitive = c.getBoolean("admin-alerts.discord.case-sensitive", false);
        v.adminDiscordAlertKeywords = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> adminAlertSeen = new java.util.LinkedHashSet<>();
        for (String part : c.getString("admin-alerts.discord.keywords", "").split("[,\r\n]+")) {
            String word = part.replaceAll("[\\x00-\\x1f]", "").trim();
            if (word.isBlank()) continue;
            if (word.length() > 80) word = word.substring(0, 80);
            String dedupe = word.toLowerCase(java.util.Locale.ROOT);
            if (adminAlertSeen.add(dedupe)) v.adminDiscordAlertKeywords.add(word);
            if (v.adminDiscordAlertKeywords.size() >= 80) break;
        }

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
        v.uploadFilenameMode = String.valueOf(c.getString("upload.filename-mode", "random")).trim().toLowerCase(Locale.ROOT);
        if (!v.uploadFilenameMode.equals("original")) v.uploadFilenameMode = "random";
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
        v.announcements.put(key, new ConfigValues.AnnouncementConfig(enabled, message));
    }


    private static List<ConfigValues.RelayPeer> sanitizeRelayPeers(List<Map<?, ?>> raw) {
        List<ConfigValues.RelayPeer> out = new ArrayList<>();
        if (raw == null) return out;
        for (Map<?, ?> item : raw) {
            if (item == null) continue;
            boolean enabled = boolValue(item.get("enabled"), true);
            String id = normalizeRelayId(String.valueOf(mapValue(item, "id", "")));
            String url = String.valueOf(mapValue(item, "url", "")).trim();
            String secret = String.valueOf(mapValue(item, "secret", "")).trim();
            // Keep incomplete entries so ServerRelay can report exactly why a configured
            // peer was ignored instead of silently reducing the loaded peer count.
            out.add(new ConfigValues.RelayPeer(id, url, secret, enabled));
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

    private static List<ConfigValues.CommandPreset> sanitizeCommandPresets(List<Map<?, ?>> raw) {
        List<ConfigValues.CommandPreset> out = new ArrayList<>();
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
            out.add(new ConfigValues.CommandPreset(id, label, description, command, enabled, requireConfirm));
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


    private static String normalizePublicPrefix(String value) {
        String out = value == null ? "" : value.trim();
        if (out.isBlank() || "/".equals(out)) return "";
        if (!out.startsWith("/")) out = "/" + out;
        while (out.endsWith("/") && out.length() > 1) out = out.substring(0, out.length() - 1);
        return out;
    }

    private static String normalizePrefix(String s) {
        if (s == null || s.isBlank()) return "";
        s = s.trim();
        if (!s.startsWith("/")) s = "/" + s;
        if ("/".equals(s)) return "/";
        while (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
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
