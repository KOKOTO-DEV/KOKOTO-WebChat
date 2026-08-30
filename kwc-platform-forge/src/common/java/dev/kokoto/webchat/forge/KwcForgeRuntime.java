package dev.kokoto.webchat.forge;

import dev.kokoto.webchat.*;
import dev.kokoto.webchat.adapter.squaremap.SquaremapAdapter;
import dev.kokoto.webchat.adapter.dynmap.DynmapAdapter;
import dev.kokoto.webchat.adapter.liveatlas.LiveAtlasAdapter;
import dev.kokoto.webchat.adapter.unmined.UnminedAdapter;
import dev.kokoto.webchat.adapter.overviewer.OverviewerAdapter;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns one KWC runtime instance on a Forge dedicated/integrated server. */
public final class KwcForgeRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("KOKOTO WebChat");
    private final Path dataDirectory;
    private MinecraftServer server;
    private volatile ConfigValues configValues;
    private ForgePlatformAdapter platformAdapter;
    private ForgeStorage storage;
    private ForgeAuthManager authManager;
    private CaptchaManager captchaManager;
    private ForgeModerationManager moderationManager;
    private ForgeLangManager langManager;
    private ForgeDiscordBridge discordBridge;
    private DirectMessageStore directMessages;
    private GroupChatStore groupChats;
    private ServerRelay serverRelay;
    private WebChatServer webServer;
    private PortableUpdateChecker updateChecker;
    private ForgeConversationStoreHost conversationStoreHost;
    private volatile AutoCloseable blueMapIntegration;
    private boolean blueMapIntegrationAttempted;

    public KwcForgeRuntime() {
        this.dataDirectory = FMLPaths.CONFIGDIR.get().resolve("KOKOTO-WebChat");
    }

    public synchronized void initializeBlueMapIntegration(boolean blueMapInstalled) {
        if (blueMapIntegrationAttempted) return;
        blueMapIntegrationAttempted = true;
        if (!blueMapInstalled) {
            info("BlueMap mod not detected; BlueMapAPI integration is inactive.");
            return;
        }
        try {
            Class<?> type = Class.forName("dev.kokoto.webchat.adapter.bluemap.api.BlueMapApiIntegration");
            Object integration = type.getConstructor(dev.kokoto.webchat.adapter.bluemap.BlueMapAdapterHost.class)
                    .newInstance(new ForgeBlueMapAdapterHost(this));
            type.getMethod("register").invoke(integration);
            blueMapIntegration = (AutoCloseable) integration;
        } catch (Throwable ex) {
            warn("BlueMapAPI integration could not be initialized: " + rootCauseMessage(ex));
        }
    }

    public ConfigValues configValuesForMapAdapter() {
        ConfigValues current = configValues;
        if (current != null) return current;
        try {
            Files.createDirectories(dataDirectory);
            installDefault("config.yml");
            installDefaultFilterLists();
            ForgeYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile());
            reconcileConfigMigration();
            provisionRelaySharedSecrets();
            return ForgeConfigValuesLoader.load(ForgeYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile()));
        } catch (Exception ex) {
            warn("BlueMap adapter config snapshot could not be loaded: " + ex.getMessage());
            return null;
        }
    }

    private String rootCauseMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String message = t.getMessage();
        return (message == null || message.isBlank()) ? t.getClass().getSimpleName() : message;
    }

    private void requestBlueMapLightReload() {
        if (blueMapIntegration == null || server == null) return;
        server.execute(() -> {
            try {
                boolean ok = platformAdapter != null && platformAdapter.dispatchConsoleCommand("bluemap reload light");
                if (ok) info("Requested BlueMap light reload so KWC web registrations use the reloaded configuration.");
                else warn("BlueMap light reload command was not accepted after KWC reload.");
            } catch (Throwable ex) {
                warn("Failed to request BlueMap light reload after KWC reload: " + ex.getMessage());
            }
        });
    }

    public synchronized void start(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        try {
            Files.createDirectories(dataDirectory);
            installDefault("config.yml");
            installDefaultFilterLists();
            for (String lang : List.of("en-US", "ko-KR", "ja-JP", "zh-CN")) installDefault("lang/" + lang + ".yml");
            ForgeYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile());
            reconcileConfigMigration();
            provisionRelaySharedSecrets();
            this.configValues = ForgeConfigValuesLoader.load(ForgeYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile()));
        } catch (Exception ex) {
            warn("KOKOTO WebChat config load failed; services were not started: " + ex.getMessage());
            return;
        }

        this.platformAdapter = new ForgePlatformAdapter(this);
        this.conversationStoreHost = new ForgeConversationStoreHost(this);
        if (!configValues.pluginEnabled) {
            new SquaremapAdapter(new ForgeSquaremapAdapterHost(this)).install();
            new DynmapAdapter(new ForgeDynmapAdapterHost(this)).install();
            new LiveAtlasAdapter(new ForgeLiveAtlasAdapterHost(this)).install();
            new UnminedAdapter(new ForgeUnminedAdapterHost(this)).install();
            new OverviewerAdapter(new ForgeOverviewerAdapterHost(this)).install();
            info("KOKOTO WebChat is disabled by config. Set enabled: true in config/KOKOTO-WebChat/config.yml and run /kchat reload.");
            return;
        }
        startServices();
        info("KOKOTO WebChat Forge enabled. Minecraft=" + minecraftVersion());
    }

    private void startServices() {
        startServices(true);
    }

    private void startServices(boolean emitSecurityWarnings) {
        storage = new ForgeStorage(this);
        storage.load();
        enforceCurrentSessionPolicies("startup/reload");
        moderationManager = new ForgeModerationManager(this);
        moderationManager.load();
        langManager = new ForgeLangManager(this);
        langManager.reload();
        captchaManager = new CaptchaManager();
        authManager = new ForgeAuthManager(this, storage);
        discordBridge = new ForgeDiscordBridge();
        directMessages = new DirectMessageStore(conversationStoreHost);
        directMessages.open();
        groupChats = new GroupChatStore(conversationStoreHost);
        groupChats.open();
        new SquaremapAdapter(new ForgeSquaremapAdapterHost(this)).install();
        new DynmapAdapter(new ForgeDynmapAdapterHost(this)).install();
        new LiveAtlasAdapter(new ForgeLiveAtlasAdapterHost(this)).install();
        new UnminedAdapter(new ForgeUnminedAdapterHost(this)).install();
        new OverviewerAdapter(new ForgeOverviewerAdapterHost(this)).install();
        ensureEmojiDirectory();
        serverRelay = new ServerRelay(new ForgeRelayHost(this));
        webServer = new WebChatServer(new ForgeWebChatHost(this));
        try { webServer.start(false); }
        catch (Exception ex) { warn("Failed to start HTTP chat server: " + ex); webServer = null; }
        serverRelay.start(false);
        if (emitSecurityWarnings) logReloadTransportSecurityWarnings();
        if (configValues.updateCheckEnabled) {
            updateChecker = new PortableUpdateChecker(version(), platformAdapter, langManager, this::info, this::warn);
            updateChecker.start();
        }
        publishAnnouncement("server-start", Map.of("server", serverName()));
    }

    public void logReloadTransportSecurityWarnings() {
        TransportSecurityWarnings.logAll(configValues, langManager, CoreLogger.of(this::info, this::warn));
    }

    public synchronized boolean reload() {
        if (server == null) return false;
        try { installDefaultFilterLists(); }
        catch (Exception ex) { warn("Starter filter-list initialization failed during reload: " + ex.getMessage()); }
        try { ForgeYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile()); }
        catch (Exception ex) { warn("Reload rejected: " + ex.getMessage()); return false; }
        reconcileConfigMigration();
        provisionRelaySharedSecrets();
        ForgeYamlConfiguration loaded;
        try { loaded = ForgeYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile()); }
        catch (Exception ex) { warn("Reload rejected after config migration: " + ex.getMessage()); return false; }
        ConfigValues replacement;
        try { replacement = ForgeConfigValuesLoader.load(loaded); }
        catch (Exception ex) { warn("Reload rejected while mapping config: " + ex.getMessage()); return false; }

        stopServices(false);
        configValues = replacement;
        platformAdapter = new ForgePlatformAdapter(this);
        conversationStoreHost = new ForgeConversationStoreHost(this);
        if (configValues.pluginEnabled) {
            startServices(false);
        } else {
            new SquaremapAdapter(new ForgeSquaremapAdapterHost(this)).install();
            new DynmapAdapter(new ForgeDynmapAdapterHost(this)).install();
            new LiveAtlasAdapter(new ForgeLiveAtlasAdapterHost(this)).install();
            new UnminedAdapter(new ForgeUnminedAdapterHost(this)).install();
            new OverviewerAdapter(new ForgeOverviewerAdapterHost(this)).install();
            info("KOKOTO WebChat disabled by reloaded config.");
        }
        // Emit transport warnings from the reload lifecycle itself so command-bridge
        // differences cannot suppress HTTP listener/peer warnings.
        logReloadTransportSecurityWarnings();
        requestBlueMapLightReload();
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
            info("Enforced current session lifetime policy after " + reason
                    + ": users updated=" + users.updated() + ", expired=" + users.expired()
                    + "; admins updated=" + admins.updated() + ", expired=" + admins.expired());
        }
    }

    public synchronized void stop() {
        if (configValues != null && configValues.pluginEnabled) publishAnnouncement("server-stop", Map.of("server", serverName()));
        stopServices(true);
        info("KOKOTO WebChat Forge disabled.");
        server = null;
    }

    private void stopServices(boolean save) {
        if (updateChecker != null) { updateChecker.close(); updateChecker = null; }
        if (serverRelay != null) { serverRelay.close(); serverRelay = null; }
        if (webServer != null) { webServer.stop(); webServer = null; }
        if (directMessages != null) { directMessages.close(); directMessages = null; }
        if (groupChats != null) { groupChats.close(); groupChats = null; }
        if (save && storage != null) storage.saveAll();
        else if (storage != null) storage.saveAll();
        if (moderationManager != null) moderationManager.save();
        authManager = null;
        captchaManager = null;
        moderationManager = null;
        langManager = null;
        discordBridge = null;
        storage = null;
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (!active() || player == null) return;
        String uuid = player.getUUID().toString();
        String username = ForgeCompat.profileName(player);
        String display = displayPlayerName(player);
        storage.updateLastDisplayName(uuid, username, display);
        if (updateChecker != null) updateChecker.onPlayerJoin(player.getUUID());
        publishAnnouncement("minecraft-join", Map.of("player", display, "name", display, "real_player", username,
                "real_name", username, "uuid", uuid, "world", ForgeCompat.worldDimension(player)));
    }

    public void onPlayerLeave(ServerPlayer player) {
        if (!active() || player == null) return;
        String uuid = player.getUUID().toString();
        String username = ForgeCompat.profileName(player);
        String display = displayPlayerName(player);
        storage.updateLastDisplayName(uuid, username, display);
        publishAnnouncement("minecraft-quit", Map.of("player", display, "name", display, "real_player", username,
                "real_name", username, "uuid", uuid, "world", ForgeCompat.worldDimension(player)));
    }

    public void onPlayerChat(ServerPlayer player, String message) {
        if (!active() || player == null || message == null) return;
        String display = displayPlayerName(player);
        String username = ForgeCompat.profileName(player);
        String uuid = player.getUUID().toString();
        storage.updateLastDisplayName(uuid, username, display);
        webServer.publishFromGame(display, username, uuid, message);
    }

    public void publishAnnouncement(String key, Map<String,String> placeholders) {
        ConfigValues c = configValues;
        if (c == null || !c.announcementEnabled(key) || webServer == null) return;
        String message = c.announcementMessage(key);
        if (message == null || message.isBlank()) return;
        Map<String,String> values = new LinkedHashMap<>();
        values.put("server", serverName());
        values.put("event", key == null ? "" : key);
        if (placeholders != null) values.putAll(placeholders);
        for (Map.Entry<String,String> e : values.entrySet()) message = message.replace("{" + e.getKey() + "}", String.valueOf(e.getValue() == null ? "" : e.getValue()));
        message = stripLegacyColors(translateAlternateColorCodes(message));
        if (!message.isBlank()) webServer.publishSystemEvent("Server", message, "announcement." + key, JsonUtil.obj(values));
    }

    public String displayPlayerName(ServerPlayer player) {
        if (player == null) return "";
        String username = ForgeCompat.profileName(player);
        String mode = configValues == null ? "name" : configValues.playerNameMode;
        String out = username;
        if ("display-name".equalsIgnoreCase(mode) && player.getDisplayName() != null) out = player.getDisplayName().getString();
        else if ("custom-name".equalsIgnoreCase(mode) && player.getCustomName() != null) out = player.getCustomName().getString();
        return normalizePlayerDisplayName(out, username);
    }

    public String displayNameForAccount(Account account) {
        if (account == null) return "";
        if (account.uuid != null && !account.uuid.isBlank() && server != null) {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(account.uuid));
                if (player != null) {
                    String name = displayPlayerName(player);
                    storage.updateLastDisplayName(account.uuid, ForgeCompat.profileName(player), name);
                    return name;
                }
            } catch (Exception ignored) {}
            if (storage != null) {
                String remembered = storage.knownDisplayName(account.uuid);
                if (remembered != null && !remembered.isBlank()) return normalizePlayerDisplayName(remembered, account.safeUsername());
            }
        }
        return normalizePlayerDisplayName(account.lastDisplayName, account.safeUsername());
    }

    public String normalizePlayerDisplayName(String name, String fallback) {
        String out = name == null || name.isBlank() ? String.valueOf(fallback == null ? "" : fallback) : name;
        if (configValues == null || configValues.playerNameStripColors) out = stripLegacyColors(translateAlternateColorCodes(out));
        return out == null || out.isBlank() ? String.valueOf(fallback == null ? "" : fallback) : out;
    }

    private void ensureEmojiDirectory() {
        if (configValues == null || !configValues.emojiEnabled) return;
        String configured = configValues.emojiDirectory == null || configValues.emojiDirectory.isBlank() ? "emojis" : configValues.emojiDirectory;
        try {
            Path dir = Path.of(configured);
            if (!dir.isAbsolute()) dir = dataDirectory.resolve(dir);
            Files.createDirectories(dir.normalize());
        } catch (Exception ex) { warn("Failed to create emoji directory: " + ex.getMessage()); }
    }


    private void provisionRelaySharedSecrets() {
        try {
            ForgeYamlConfiguration yaml = ForgeYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile());
            RelaySharedSecretProvisioner.provision(
                    dataDirectory.resolve("config.yml"), yaml.getMapList("server-relay.groups"),
                    CoreLogger.of(this::info, this::warn));
        } catch (Exception ex) {
            warn("Failed to provision Relay v2 group shared-secret: " + ex.getMessage());
        }
    }

    private void reconcileConfigMigration() {
        try {
            PortableConfigMigration.reconcile(
                    dataDirectory, version(), this::resource,
                    in -> ForgeYamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)).flatLeafValues(),
                    this::info);
        } catch (Exception ex) {
            warn("Config migration/reconciliation failed; continuing with the validated physical config: " + ex.getMessage());
        }
    }

    private void installDefaultFilterLists() throws IOException {
        DefaultFilterListInstaller.Result result = DefaultFilterListInstaller.initialize(dataDirectory, this::resource);
        if (result.initializedNow()) {
            info("Initialized starter filter lists: copied=" + result.copied()
                    + ", preserved=" + result.preserved());
        }
    }

    private void installDefault(String resource) throws IOException {
        Path target = dataDirectory.resolve(resource);
        if (Files.exists(target)) return;
        try (InputStream in = resource(resource)) {
            if (in == null) throw new FileNotFoundException("bundled resource " + resource);
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        }
    }

    public InputStream resource(String name) {
        if (name == null) return null;
        return KwcForgeRuntime.class.getClassLoader().getResourceAsStream(name.startsWith("/") ? name.substring(1) : name);
    }

    public void audit(String action, String actor, Map<String,?> details) {
        ConfigValues c = configValues;
        if (c == null || !c.auditEnabled) return;
        try {
            Path dir = dataDirectory.resolve(c.auditDirectory == null || c.auditDirectory.isBlank() ? "audit" : c.auditDirectory);
            Files.createDirectories(dir);
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("time", Instant.now().toString()); entry.put("action", action); entry.put("actor", actor); entry.put("details", details == null ? Map.of() : details);
            Files.writeString(dir.resolve("audit.jsonl"), JsonUtil.obj(entry) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ex) { warn("Audit write failed: " + ex.getMessage()); }
    }

    public boolean active() { return configValues != null && configValues.pluginEnabled && webServer != null; }
    public Path dataDirectory() { return dataDirectory; }
    public MinecraftServer server() { return server; }
    public ConfigValues configValues() { return configValues; }
    public ForgePlatformAdapter platformAdapter() { return platformAdapter; }
    public ForgeStorage storage() { return storage; }
    public ForgeAuthManager authManager() { return authManager; }
    public CaptchaManager captchaManager() { return captchaManager; }
    public ForgeModerationManager moderationManager() { return moderationManager; }
    public ForgeLangManager langManager() { return langManager; }
    public ForgeDiscordBridge discordBridge() { return discordBridge; }
    public DirectMessageStore directMessages() { return directMessages; }
    public GroupChatStore groupChats() { return groupChats; }
    public ServerRelay serverRelay() { return serverRelay; }
    public WebChatServer webServer() { return webServer; }

    public String version() { return "5.1.0"; }

    public String serverName() {
        if (configValues != null && configValues.serverRelayServerName != null && !configValues.serverRelayServerName.isBlank()) return configValues.serverRelayServerName;
        return "Forge Server";
    }
    public String minecraftVersion() {
        return "26.2";
    }
    public void info(String message) { LOGGER.info("{}", message); }
    public void warn(String message) { LOGGER.warn("{}", message); }
    public void debug(String message) { LOGGER.debug("{}", message); }

    private static String translateAlternateColorCodes(String value) {
        if (value == null) return "";
        char[] chars = value.toCharArray();
        for (int i = 0; i + 1 < chars.length; i++) if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i+1]) >= 0) {
            chars[i] = '\u00A7'; chars[i+1] = Character.toLowerCase(chars[i+1]);
        }
        return new String(chars);
    }
    private static String stripLegacyColors(String value) { return value == null ? "" : value.replaceAll("(?i)\\u00A7[0-9A-FK-ORX]", ""); }
}
