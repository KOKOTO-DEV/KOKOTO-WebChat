package dev.kokoto.webchat;


import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

public class ConfigValues implements MessageTokenConfig {
    public boolean pluginEnabled;
    public boolean updateCheckEnabled;
    public List<String> privateChatSuperAdmins;
    public boolean auditEnabled;
    public String auditDirectory;
    public String httpHost;
    public int httpPort;
    public String pathPrefix;
    public String publicPrefix;
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

    public boolean bluemapEnabled;
    public boolean webAutoInstall;
    public boolean webAutoPatch;
    public String bluemapWebRoot;
    public String bluemapWebappConf;
    public String addonPath;
    public String apiBaseUrl;

    public boolean squaremapEnabled;
    public boolean squaremapAutoInstall;
    public boolean squaremapAutoPatchIndex;
    public String squaremapWebRoot;
    public String squaremapAddonPath;
    public String squaremapApiBaseUrl;

    public boolean dynmapEnabled;
    public boolean dynmapAutoInstall;
    public boolean dynmapAutoPatchIndex;
    public String dynmapWebRoot;
    public String dynmapAddonPath;
    public String dynmapApiBaseUrl;

    public boolean pl3xmapEnabled;
    public boolean pl3xmapAutoInstall;
    public boolean pl3xmapAutoPatchIndex;
    public String pl3xmapWebRoot;
    public String pl3xmapAddonPath;
    public String pl3xmapApiBaseUrl;

    public boolean liveAtlasEnabled;
    public boolean liveAtlasAutoInstall;
    public boolean liveAtlasAutoPatchIndex;
    public String liveAtlasWebRoot;
    public String liveAtlasAddonPath;
    public String liveAtlasApiBaseUrl;

    public boolean unminedEnabled;
    public boolean unminedAutoInstall;
    public boolean unminedAutoPatchIndex;
    public String unminedWebRoot;
    public String unminedAddonPath;
    public String unminedApiBaseUrl;

    public boolean overviewerEnabled;
    public boolean overviewerAutoInstall;
    public boolean overviewerAutoPatchIndex;
    public String overviewerWebRoot;
    public String overviewerAddonPath;
    public String overviewerApiBaseUrl;

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
    public boolean serverRelayForwardReceivedPublicChat;
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
    public double uiHistoryPreloadScreens;
    public int uiHistoryPreloadMinPx;
    public int uiAutoFollowBottomThresholdPx;
    public int uiScrollInteractionIdleMs;
    public boolean uiResumeRefreshEnabled;
    public int uiResumeRefreshMinIntervalSeconds;
    public boolean uiResumeRefreshSkipUnchanged;
    public String uiTheme;
    public boolean uiSyncBlueMapTheme;
    public double uiOpacity;
    public boolean uiUserPreferencesControl;
    public boolean uiUserProfilesEnabled;
    public int uiUserProfilesMaxProfiles;
    public boolean uiUserProfilesAllowImportExport;
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

    // Shared public/group/DM content filtering. Rules are loader-neutral and are
    // evaluated by ContentFilterEngine for web and game entry points.
    public boolean contentFilterEnabled;
    public boolean contentFilterPublic;
    public boolean contentFilterGroup;
    public boolean contentFilterDm;
    public boolean contentFilterShowMatchedWord;
    public String contentFilterMaskText;
    public boolean contentFilterUnicodeNormalization;
    public boolean contentFilterCompactMatch;
    public boolean contentFilterInterleaveMatch;
    public int contentFilterInterleaveMaxGap;
    public boolean contentFilterInterleaveUnlimitedGap;
    public boolean contentFilterCollapseRepeats;
    public int contentFilterRepeatLimit;
    public List<ContentFilterRule> contentFilterRules;
    /** Active UTF-8 bulk filter lists loaded from filter-lists/*.txt. */
    public List<ContentFilterRule> contentFilterWordListRules = new ArrayList<>();

    public boolean discordEnabled;
    public String discordChannel;
    public boolean discordWebToDiscord;
    public boolean discordDiscordToWeb;
    public boolean discordIgnoreBotMessages;
    public boolean discordSuppressGameEcho;
    public int discordSuppressGameEchoSeconds;
    public String discordWebToDiscordFormat;
    public String discordGameRelayFormat;
    public String discordToWebSenderFormat;
    public String discordToWebMessageFormat;
    public String discordGameRelayMode;
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

    public boolean adminDiscordAlertsEnabled;
    public String adminDiscordAlertsChannel;
    public boolean adminDiscordAlertsPublicChat;
    public boolean adminDiscordAlertsRelayChat;
    public boolean adminDiscordAlertsDm;
    public boolean adminDiscordAlertsGroupChat;
    public String adminDiscordAlertsMention;
    public boolean adminDiscordAlertsCaseSensitive;
    public List<String> adminDiscordAlertKeywords;

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
    /** random (default) or original. */
    public String uploadFilenameMode;
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


    @Override
    public boolean messageTokensEnabled() { return messageTokensEnabled; }

    @Override
    public int messageTokensMaxReplacements() { return messageTokensMaxReplacements; }

    @Override
    public List<String> messageTokenNewlineAliases() { return messageTokenNewlineAliases; }

    @Override
    public List<String> messageTokenBlankLineAliases() { return messageTokenBlankLineAliases; }

    @Override
    public List<String> messageTokenTabAliases() { return messageTokenTabAliases; }

    @Override
    public int messageTokenTabSpaces() { return messageTokenTabSpaces; }

    @Override
    public Map<String, String> messageTokenCustomReplacements() { return messageTokenCustomReplacements; }
}
