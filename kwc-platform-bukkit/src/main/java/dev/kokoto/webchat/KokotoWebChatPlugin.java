package dev.kokoto.webchat;

import dev.kokoto.webchat.adapter.bluemap.BlueMapAdapter;
import dev.kokoto.webchat.adapter.squaremap.SquaremapAdapter;
import dev.kokoto.webchat.adapter.dynmap.DynmapAdapter;
import dev.kokoto.webchat.adapter.pl3xmap.Pl3xMapAdapter;
import dev.kokoto.webchat.adapter.liveatlas.LiveAtlasAdapter;
import dev.kokoto.webchat.adapter.unmined.UnminedAdapter;
import dev.kokoto.webchat.adapter.overviewer.OverviewerAdapter;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class KokotoWebChatPlugin extends JavaPlugin {
    private ConfigValues configValues;
    private PlatformAdapter platformAdapter;
    private Storage storage;
    private AuthManager authManager;
    private CaptchaManager captchaManager;
    private ModerationManager moderationManager;
    private LangManager langManager;
    private DiscordBridge discordBridge;
    private ServerRelay serverRelay;
    private DirectMessageStore directMessages;
    private GroupChatStore groupChats;
    private ConversationStoreHost conversationStoreHost;
    private WebChatServer webServer;
    private ChatListener chatListener;
    private UpdateChecker updateChecker;
    private String lastConfigReloadError = "";

    @Override
    public void onEnable() {
        platformAdapter = new BukkitPlatformAdapter(this);
        conversationStoreHost = new BukkitConversationStoreHost(this);
        boolean legacyConfigPending = LegacyBmwcMigrationManager.prepare(this);
        saveDefaultConfig();
        installDefaultFilterLists();
        if (legacyConfigPending) {
            LegacyBmwcMigrationManager.convertLegacyConfig(this);
            reloadConfig();
        } else if (LegacyBmwcMigrationManager.convertCurrentConfigIfLegacy(this)) {
            reloadConfig();
        }
        ConfigValidationManager.Result initialValidation = ConfigValidationManager.validate(this);
        if (!initialValidation.valid()) {
            lastConfigReloadError = initialValidation.message();
            getLogger().severe("KOKOTO WebChat config.yml is invalid: " + lastConfigReloadError);
            getLogger().severe("KOKOTO WebChat services were not started. Fix config.yml and run /kchat reload.");
            registerCommandExecutor();
            return;
        }
        // ConfigMigrationManager is the only component allowed to rewrite an existing
        // config.yml. Exact <version> is a fixed operator config and must remain byte-for-byte
        // untouched; version migration / *_auto_migration rebuilds from the current bundled
        // config and overlays the operator's values.
        reloadConfig();
        ConfigMigrationManager.check(this);
        configValues = BukkitConfigValuesLoader.load(getConfig());
        lastConfigReloadError = "";
        registerCommandExecutor();
        if (!configValues.pluginEnabled) {
            installAssets();
            getLogger().info("KOKOTO WebChat is disabled by config. Set enabled: true in config.yml to start web/chat services. /kchat reload remains available.");
            return;
        }

        storage = new Storage(this);
        storage.load();
        enforceCurrentSessionPolicies("startup");

        moderationManager = new ModerationManager(this);
        moderationManager.load();

        langManager = new LangManager(this);
        langManager.reload();

        captchaManager = new CaptchaManager();
        authManager = new AuthManager(this, storage);
        discordBridge = new DiscordBridge(this);
        directMessages = new DirectMessageStore(conversationStoreHost);
        directMessages.open();
        groupChats = new GroupChatStore(conversationStoreHost);
        groupChats.open();

        installAssets();
        ensureEmojiDirectory();
        startWebServer();
        startServerRelay();
        discordBridge.start();

        registerRuntimeListeners();
        startUpdateChecker();

        scheduleServerStartAnnouncement();

        getLogger().info("KOKOTO WebChat enabled.");
    }

    private void installDefaultFilterLists() {
        try {
            DefaultFilterListInstaller.Result result = DefaultFilterListInstaller.initialize(
                    getDataFolder().toPath(), this::getResource);
            if (result.initializedNow()) {
                getLogger().info("Initialized starter filter lists: copied=" + result.copied()
                        + ", preserved=" + result.preserved());
            }
        } catch (Exception ex) {
            getLogger().warning("Failed to initialize starter filter lists: " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (configValues != null && configValues.pluginEnabled) {
            publishAnnouncement("server-stop", Map.of("server", getServer().getName()));
        }
        stopRuntimeServices();
        saveRuntimeState();
        getLogger().info("KOKOTO WebChat disabled.");
    }

    public PlatformAdapter platformAdapter() {
        return platformAdapter;
    }

    public String applyMessageTokens(String text) {
        return MessageTokenProcessor.apply(text, configValues);
    }

    public String applyMessageTokensForGame(String text) {
        return MessageTokenProcessor.applyForGameDisplay(text, configValues);
    }

    public String restoreMessageTokenGameBreaks(String text) {
        return MessageTokenProcessor.restoreGameDisplayBreaks(text);
    }

    public java.util.List<String> splitMessageTokenGameLines(String text) {
        return MessageTokenProcessor.splitGameDisplayLines(text);
    }

    public boolean reloadPlugin() {
        lastConfigReloadError = "";
        LegacyBmwcMigrationManager.cleanupMarkerIfSourceGone(this);
        installDefaultFilterLists();
        ConfigValidationManager.Result before = ConfigValidationManager.validate(this);
        if (!before.valid()) {
            lastConfigReloadError = before.message();
            getLogger().warning("KOKOTO WebChat reload rejected because config.yml is invalid: " + lastConfigReloadError);
            return false;
        }

        // Do not normalize comments/layout here. Exact <version> means the operator fixed
        // this config and reload must not rewrite it. ConfigMigrationManager performs a full
        // bundled-default reconstruction only for version migration or *_auto_migration.

        // Only stop the live services after the replacement configuration is known
        // to be valid. A malformed edit therefore keeps the previous live config,
        // language and web services intact.
        stopRuntimeServices();
        saveRuntimeState();
        reloadConfig();
        ConfigMigrationManager.check(this);
        configValues = BukkitConfigValuesLoader.load(getConfig());
        lastConfigReloadError = "";
        if (storage != null) enforceCurrentSessionPolicies("reload");
        if (!configValues.pluginEnabled) {
            registerCommandExecutor();
            installAssets();
            scheduleBlueMapLightReload();
            getLogger().info("KOKOTO WebChat is disabled by config. Web/chat services are stopped. /kchat reload remains available.");
            return true;
        }
        registerCommandExecutor();
        if (storage == null) {
            storage = new Storage(this);
            storage.load();
            enforceCurrentSessionPolicies("reload");
        } else {
            storage.saveAll();
        }
        if (moderationManager == null) {
            moderationManager = new ModerationManager(this);
        } else {
            moderationManager.save();
        }
        moderationManager.load();
        if (captchaManager == null) {
            captchaManager = new CaptchaManager();
        }
        if (authManager == null) {
            authManager = new AuthManager(this, storage);
        }
        if (directMessages == null) {
            directMessages = new DirectMessageStore(conversationStoreHost);
        }
        directMessages.open();
        if (groupChats == null) {
            groupChats = new GroupChatStore(conversationStoreHost);
        }
        groupChats.open();
        if (langManager == null) {
            langManager = new LangManager(this);
        }
        langManager.reload();
        installAssets();
        scheduleBlueMapLightReload();
        ensureEmojiDirectory();
        startWebServer();
        startServerRelay();
        if (discordBridge == null) {
            discordBridge = new DiscordBridge(this);
        }
        discordBridge.start();
        registerRuntimeListeners();
        startUpdateChecker();
        return true;
    }

    private void enforceCurrentSessionPolicies(String reason) {
        if (storage == null || configValues == null) return;
        long userDuration = configValues.rememberSessionDays <= 0
                ? 0L : Math.multiplyExact((long) configValues.rememberSessionDays, 86_400_000L);
        SessionPolicyUpdate users = storage.recalculateSessionExpiry(false, userDuration);
        long adminDuration = configValues.adminSessionExpireHours <= 0
                ? 0L : Math.multiplyExact((long) configValues.adminSessionExpireHours, 3_600_000L);
        SessionPolicyUpdate admins = storage.recalculateSessionExpiry(true, adminDuration);
        if (users.updated() > 0 || users.expired() > 0 || admins.updated() > 0 || admins.expired() > 0) {
            getLogger().info("Enforced current session lifetime policy after " + reason
                    + ": users updated=" + users.updated() + ", expired=" + users.expired()
                    + "; admins updated=" + admins.updated() + ", expired=" + admins.expired());
        }
    }

    public String lastConfigReloadError() {
        return lastConfigReloadError;
    }



    private void scheduleServerStartAnnouncement() {
        getServer().getScheduler().runTaskLater(this, () -> {
            ConfigValues config = configValues;
            if (config != null && config.pluginEnabled) {
                publishAnnouncement("server-start", Map.of("server", getServer().getName()));
            }
        }, 40L);
    }

    private void registerCommandExecutor() {
        KwcCommand cmd = new KwcCommand(this);
        PluginCommand pluginCommand = getCommand("kchat");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(cmd);
            pluginCommand.setTabCompleter(cmd);
        }
    }

    private void registerRuntimeListeners() {
        chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new EventAnnouncementListener(this), this);
    }

    private void startUpdateChecker() {
        updateChecker = new UpdateChecker(this);
        updateChecker.start();
    }

    private void startServerRelay() {
        serverRelay = new ServerRelay(new BukkitRelayHost(this));
        serverRelay.start();
    }

    private void stopRuntimeServices() {
        if (updateChecker != null) {
            updateChecker.close();
            updateChecker = null;
        }
        if (serverRelay != null) {
            serverRelay.close();
            serverRelay = null;
        }
        if (discordBridge != null) {
            discordBridge.stop();
            discordBridge = null;
        }
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        if (directMessages != null) {
            directMessages.close();
            directMessages = null;
        }
        if (groupChats != null) {
            groupChats.close();
            groupChats = null;
        }
        HandlerList.unregisterAll(this);
        chatListener = null;
    }

    private void saveRuntimeState() {
        if (storage != null) {
            storage.saveAll();
        }
        if (moderationManager != null) {
            moderationManager.save();
        }
    }

    private void installAssets() {
        new BlueMapAdapter(new BukkitBlueMapAdapterHost(this)).install();
        new SquaremapAdapter(new BukkitSquaremapAdapterHost(this)).install();
        new DynmapAdapter(new BukkitDynmapAdapterHost(this)).install();
        new Pl3xMapAdapter(new BukkitPl3xMapAdapterHost(this)).install();
        new LiveAtlasAdapter(new BukkitLiveAtlasAdapterHost(this)).install();
        new UnminedAdapter(new BukkitUnminedAdapterHost(this)).install();
        new OverviewerAdapter(new BukkitOverviewerAdapterHost(this)).install();
    }

    private void scheduleBlueMapLightReload() {
        if (getServer().getPluginManager().getPlugin("BlueMap") == null) return;
        getServer().getScheduler().runTask(this, () -> {
            try {
                boolean ok = getServer().dispatchCommand(getServer().getConsoleSender(), "bluemap reload light");
                if (ok) {
                    getLogger().info("Requested BlueMap light reload after KWC web adapter refresh.");
                } else {
                    getLogger().warning("BlueMap is installed but 'bluemap reload light' could not be dispatched. Run /bluemap reload light manually.");
                }
            } catch (Exception ex) {
                getLogger().warning("Could not request BlueMap light reload after KWC web adapter refresh: " + ex.getMessage());
            }
        });
    }

    private void ensureEmojiDirectory() {
        ConfigValues config = configValues;
        if (config == null || !config.emojiEnabled) return;

        String configured = config.emojiDirectory;
        if (configured == null || configured.isBlank()) configured = "emojis";

        try {
            Path dir = Path.of(configured);
            if (!dir.isAbsolute()) {
                dir = getDataFolder().toPath().resolve(dir);
            }
            Files.createDirectories(dir.normalize());
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Failed to create emoji directory: " + configured, ex);
        }
    }

    private void startWebServer() {
        webServer = new WebChatServer(new BukkitWebChatHost(this));
        try {
            webServer.start();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to start HTTP chat server", ex);
        }
    }


    public ChatListener chatListener() {
        return chatListener;
    }

    public void publishAnnouncement(String key, Map<String, String> placeholders) {
        ConfigValues config = configValues;
        if (config == null || !config.announcementEnabled(key)) return;

        String message = config.announcementMessage(key);
        if (message == null || message.isBlank()) return;

        Map<String, String> values = new LinkedHashMap<>();
        values.put("server", getServer().getName());
        values.put("event", key == null ? "" : key);
        if (placeholders != null) values.putAll(placeholders);

        if (langManager != null && isDefaultAnnouncementMessage(key, message)) {
            message = langManager.text("announcement." + key, message, values);
        } else {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                message = message.replace("{" + entry.getKey() + "}", value);
            }
        }

        message = ChatColor.translateAlternateColorCodes('&', message);
        message = ChatColor.stripColor(message);
        if (message == null || message.isBlank()) return;

        WebChatServer server = webServer;
        if (server != null) {
            server.publishSystemEvent("Server", message, "announcement." + key, JsonUtil.obj(values));
        }
    }


    private boolean isDefaultAnnouncementMessage(String key, String message) {
        if (key == null || message == null) return false;
        return switch (key) {
            case "minecraft-join" -> message.equals("🟢 {player} joined the server.");
            case "minecraft-quit" -> message.equals("🔴 {player} left the server.");
            case "first-join" -> message.equals("✨ {player} joined the server for the first time.");
            case "death" -> message.equals("☠ {message}");
            case "advancement" -> message.equals("🏆 {player} completed the advancement [{advancement}].");
            case "world-change" -> message.equals("🌍 {player} moved to {to_world}.");
            case "gamemode-change" -> message.equals("🎮 {player} changed game mode to {to_gamemode}.");
            case "level-change" -> message.equals("⭐ {player} changed level from {old_level} to {to_level}.") || message.equals("⭐ {player} changed level from {old_level} to {new_level}.");
            case "bed-enter" -> message.equals("💤 {player} entered a bed.");
            case "server-start" -> message.equals("🟢 Server started.");
            case "server-stop" -> message.equals("🔴 Server is stopping.");
            case "web-login" -> message.equals("🌐 {name} logged in to web chat.");
            case "web-logout" -> message.equals("🌐 {name} logged out of web chat.");
            default -> false;
        };
    }

    public void publishAnnouncement(String key, String... placeholders) {
        Map<String, String> values = new LinkedHashMap<>();
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                values.put(placeholders[i], placeholders[i + 1]);
            }
        }
        publishAnnouncement(key, values);
    }


    public String displayPlayerName(Player player) {
        if (player == null) return "";
        ConfigValues config = configValues;
        String mode = config == null ? "name" : config.playerNameMode;
        String name;
        if ("display-name".equalsIgnoreCase(mode)) {
            name = player.getDisplayName();
        } else if ("custom-name".equalsIgnoreCase(mode)) {
            name = player.getCustomName();
            if (name == null || name.isBlank()) name = player.getDisplayName();
        } else {
            name = player.getName();
        }
        return normalizePlayerDisplayName(name, player.getName());
    }

    public String normalizePlayerDisplayName(String name, String fallback) {
        String out = name == null || name.isBlank() ? String.valueOf(fallback == null ? "" : fallback) : name;
        ConfigValues config = configValues;
        if (config == null || config.playerNameStripColors) {
            out = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', out));
        }
        if (out == null || out.isBlank()) out = String.valueOf(fallback == null ? "" : fallback);
        return out == null ? "" : out;
    }

    public String displayNameForAccount(Account account) {
        if (account == null) return "";
        if (account.uuid != null && !account.uuid.isBlank()) {
            try {
                Player player = getServer().getPlayer(java.util.UUID.fromString(account.uuid));
                if (player != null) {
                    String name = displayPlayerName(player);
                    if (name != null && !name.isBlank()) {
                        storage.updateLastDisplayName(account.uuid, player.getName(), name);
                        return name;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
            String remembered = storage.knownDisplayName(account.uuid);
            if (remembered != null && !remembered.isBlank()) {
                String normalized = normalizePlayerDisplayName(remembered, account.safeUsername());
                if (account.lastDisplayName == null || account.lastDisplayName.isBlank() || !account.lastDisplayName.equals(normalized)) {
                    storage.updateLastDisplayName(account.uuid, account.safeUsername(), normalized);
                }
                return normalized;
            }
        }
        if (account.lastDisplayName != null && !account.lastDisplayName.isBlank()) {
            return normalizePlayerDisplayName(account.lastDisplayName, account.safeUsername());
        }
        return account.safeUsername();
    }

    public ConfigValues configValues() {
        return configValues;
    }

    public Storage storage() {
        return storage;
    }

    public AuthManager authManager() {
        return authManager;
    }

    public CaptchaManager captchaManager() {
        return captchaManager;
    }

    public ModerationManager moderationManager() {
        return moderationManager;
    }

    public ServerRelay serverRelay() {
        return serverRelay;
    }

    public DiscordBridge discordBridge() {
        return discordBridge;
    }

    public DirectMessageStore directMessages() {
        return directMessages;
    }

    public GroupChatStore groupChats() {
        return groupChats;
    }

    public LangManager langManager() {
        return langManager;
    }

    public WebChatServer webServer() {
        return webServer;
    }
}
