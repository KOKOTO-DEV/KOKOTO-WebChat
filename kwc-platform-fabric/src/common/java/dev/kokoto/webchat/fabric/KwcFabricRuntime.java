package dev.kokoto.webchat.fabric;


/* KWC 파일 안내 / KWC file guide
 * KwcFabricRuntime는 Fabric 모드의 lifecycle/runtime 진입점으로 core server, config, storage, listeners와 map adapter를 조립한다.
 * KwcFabricRuntime is the Fabric mod lifecycle/runtime entry point assembling core server, config, storage, listeners, and map adapters.
 *
 * 서버 start/stop/reload에서 등록 객체를 중복 생성하지 않고, shutdown 시 background/SSE/relay 자원을 확실히 정리한다.
 * Avoid duplicate registrations across start/stop/reload and reliably close background/SSE/relay resources on shutdown.
 */
import dev.kokoto.webchat.*;
import dev.kokoto.webchat.adapter.squaremap.SquaremapAdapter;
import dev.kokoto.webchat.adapter.dynmap.DynmapAdapter;
import dev.kokoto.webchat.adapter.pl3xmap.Pl3xMapAdapter;
import dev.kokoto.webchat.adapter.liveatlas.LiveAtlasAdapter;
import dev.kokoto.webchat.adapter.unmined.UnminedAdapter;
import dev.kokoto.webchat.adapter.overviewer.OverviewerAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns one KWC runtime instance on a Fabric dedicated/integrated server. */
public final class KwcFabricRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("KOKOTO WebChat");
    private final Path dataDirectory;
    private MinecraftServer server;
    private volatile ConfigValues configValues;
    private FabricPlatformAdapter platformAdapter;
    private FabricStorage storage;
    private FabricAuthManager authManager;
    private CaptchaManager captchaManager;
    private FabricModerationManager moderationManager;
    private FabricLangManager langManager;
    private FabricDiscordBridge discordBridge;
    private DirectMessageStore directMessages;
    private GroupChatStore groupChats;
    private ServerRelay serverRelay;
    private WebChatServer webServer;
    private PortableUpdateChecker updateChecker;
    private FabricConversationStoreHost conversationStoreHost;
    private volatile AutoCloseable blueMapIntegration;
    private boolean blueMapIntegrationAttempted;

    public KwcFabricRuntime() {
        this.dataDirectory = FabricLoader.getInstance().getConfigDir().resolve("KOKOTO-WebChat");
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
                    .newInstance(new FabricBlueMapAdapterHost(this));
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
            FabricYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile());
            reconcileConfigMigration();
            provisionRelaySharedSecrets();
            return FabricConfigValuesLoader.load(FabricYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile()));
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

    // Fabric 서버가 준비된 뒤 호출되는 runtime start 진입점이다. data/config를 읽고 platform bridge와 core 서비스를 조립한 뒤 listener가 안전하게 사용할 수 있는 active 상태로 전환한다.
    // Fabric runtime start entry point invoked after the server is ready. It reads data/config, assembles platform bridges and core services, then transitions to an active state safe for listeners.
    public synchronized void start(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        try {
            Files.createDirectories(dataDirectory);
            installDefault("config.yml");
            installDefaultFilterLists();
            for (String lang : List.of("en-US", "ko-KR", "ja-JP", "zh-CN")) installDefault("lang/" + lang + ".yml");
            FabricYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile());
            reconcileConfigMigration();
            provisionRelaySharedSecrets();
            this.configValues = FabricConfigValuesLoader.load(FabricYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile()));
        } catch (Exception ex) {
            warn("KOKOTO WebChat config load failed; services were not started: " + ex.getMessage());
            return;
        }

        this.platformAdapter = new FabricPlatformAdapter(this);
        this.conversationStoreHost = new FabricConversationStoreHost(this);
        if (!configValues.pluginEnabled) {
            new SquaremapAdapter(new FabricSquaremapAdapterHost(this)).install();
            new DynmapAdapter(new FabricDynmapAdapterHost(this)).install();
            new Pl3xMapAdapter(new FabricPl3xMapAdapterHost(this)).install();
            new LiveAtlasAdapter(new FabricLiveAtlasAdapterHost(this)).install();
            new UnminedAdapter(new FabricUnminedAdapterHost(this)).install();
            new OverviewerAdapter(new FabricOverviewerAdapterHost(this)).install();
            info("KOKOTO WebChat is disabled by config. Set enabled: true in config/KOKOTO-WebChat/config.yml and run /kchat reload.");
            return;
        }
        startServices();
        info("KOKOTO WebChat Fabric enabled. Minecraft=" + minecraftVersion());
    }

    // config 검증이 끝난 뒤 auth/storage/moderation/private chat/Relay/Web 서버 같은 장기 서비스를 정해진 순서로 시작한다. 일부만 시작된 상태에서 예외가 나면 stop 경로가 처리할 수 있게 field 할당 순서를 주의한다.
    // Starts long-lived auth/storage/moderation/private-chat/Relay/web services in a defined order after config validation. Field assignment order must allow the stop path to clean up safely if startup fails partway.
    private void startServices() {
        startServices(true);
    }

    private void startServices(boolean emitSecurityWarnings) {
        storage = new FabricStorage(this);
        storage.load();
        enforceCurrentSessionPolicies("startup/reload");
        moderationManager = new FabricModerationManager(this);
        moderationManager.load();
        langManager = new FabricLangManager(this);
        langManager.reload();
        captchaManager = new CaptchaManager();
        authManager = new FabricAuthManager(this, storage);
        discordBridge = new FabricDiscordBridge();
        directMessages = new DirectMessageStore(conversationStoreHost);
        directMessages.open();
        groupChats = new GroupChatStore(conversationStoreHost);
        groupChats.open();
        new SquaremapAdapter(new FabricSquaremapAdapterHost(this)).install();
        new DynmapAdapter(new FabricDynmapAdapterHost(this)).install();
        new Pl3xMapAdapter(new FabricPl3xMapAdapterHost(this)).install();
        new LiveAtlasAdapter(new FabricLiveAtlasAdapterHost(this)).install();
        new UnminedAdapter(new FabricUnminedAdapterHost(this)).install();
        new OverviewerAdapter(new FabricOverviewerAdapterHost(this)).install();
        ensureEmojiDirectory();
        serverRelay = new ServerRelay(new FabricRelayHost(this));
        webServer = new WebChatServer(new FabricWebChatHost(this));
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

    // 현재 서버 객체는 유지한 채 설정·언어·필터·Relay·웹 서버·map integration을 재조정한다. 새 설정이 잘못됐을 때 실행 중인 서비스를 불필요하게 파괴하지 않는 것이 목표다.
    // Reconciles config, language, filters, Relay, web server, and map integration while keeping the current server object. The goal is to avoid needlessly destroying working services when new configuration is invalid.
    public synchronized boolean reload() {
        if (server == null) return false;
        try { installDefaultFilterLists(); }
        catch (Exception ex) { warn("Starter filter-list initialization failed during reload: " + ex.getMessage()); }
        try { FabricYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile()); }
        catch (Exception ex) { warn("Reload rejected: " + ex.getMessage()); return false; }
        reconcileConfigMigration();
        provisionRelaySharedSecrets();
        FabricYamlConfiguration loaded;
        try { loaded = FabricYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile()); }
        catch (Exception ex) { warn("Reload rejected after config migration: " + ex.getMessage()); return false; }
        ConfigValues replacement;
        try { replacement = FabricConfigValuesLoader.load(loaded); }
        catch (Exception ex) { warn("Reload rejected while mapping config: " + ex.getMessage()); return false; }

        stopServices(false);
        configValues = replacement;
        platformAdapter = new FabricPlatformAdapter(this);
        conversationStoreHost = new FabricConversationStoreHost(this);
        if (configValues.pluginEnabled) {
            startServices(false);
        } else {
            new SquaremapAdapter(new FabricSquaremapAdapterHost(this)).install();
            new DynmapAdapter(new FabricDynmapAdapterHost(this)).install();
            new Pl3xMapAdapter(new FabricPl3xMapAdapterHost(this)).install();
            new LiveAtlasAdapter(new FabricLiveAtlasAdapterHost(this)).install();
            new UnminedAdapter(new FabricUnminedAdapterHost(this)).install();
            new OverviewerAdapter(new FabricOverviewerAdapterHost(this)).install();
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
        info("KOKOTO WebChat Fabric disabled.");
        server = null;
    }

    // SSE/HTTP/Relay처럼 background thread 또는 socket을 가진 서비스를 먼저 닫고 저장소를 flush/close한다. loader shutdown 중에는 새 task를 예약하지 않는다.
    // Closes services with background threads/sockets such as SSE/HTTP/Relay first, then flushes/closes stores. Do not schedule new work while the loader is shutting down.
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

    // 게임 접속을 presence/identity/announcement 흐름에 반영한다. Invisible preference는 브라우저 viewer별 응답 정책에서 마스킹되므로 raw Game presence 자체는 정확히 유지한다.
    // Feeds game login into presence, identity, and announcement flows. Invisible is masked later per browser viewer, so raw Game presence itself must remain accurate.
    public void onPlayerJoin(ServerPlayer player) {
        if (!active() || player == null) return;
        String uuid = player.getUUID().toString();
        String username = FabricCompat.profileName(player);
        String display = displayPlayerName(player);
        storage.updateLastDisplayName(uuid, username, display);
        if (updateChecker != null) updateChecker.onPlayerJoin(player.getUUID());
        publishAnnouncement("minecraft-join", Map.of("player", display, "name", display, "real_player", username,
                "real_name", username, "uuid", uuid, "world", FabricCompat.worldDimension(player)));
    }

    // 게임 연결 해제를 presence와 account/SSE 갱신에 반영한다. Web 접속이 남아 있을 수 있으므로 Game offline을 전체 Offline과 동일하게 취급하지 않는다.
    // Feeds game disconnect into presence/account/SSE updates. A Web connection may remain, so Game offline must not automatically mean overall Offline.
    public void onPlayerLeave(ServerPlayer player) {
        if (!active() || player == null) return;
        String uuid = player.getUUID().toString();
        String username = FabricCompat.profileName(player);
        String display = displayPlayerName(player);
        storage.updateLastDisplayName(uuid, username, display);
        publishAnnouncement("minecraft-quit", Map.of("player", display, "name", display, "real_player", username,
                "real_name", username, "uuid", uuid, "world", FabricCompat.worldDimension(player)));
    }

    public void broadcastFilteredPlayerChat(ServerPlayer player, String message) {
        if (!active() || player == null) return;
        String text = String.valueOf(message == null ? "" : message);
        String display = displayPlayerName(player);
        platformAdapter.broadcastPlainMessage("<" + display + "> " + text);
        onPlayerChat(player, text);
    }

    public void onPlayerChat(ServerPlayer player, String message) {
        if (!active() || player == null || message == null) return;
        String display = displayPlayerName(player);
        String username = FabricCompat.profileName(player);
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
        String username = FabricCompat.profileName(player);
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
                    storage.updateLastDisplayName(account.uuid, FabricCompat.profileName(player), name);
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
            FabricYamlConfiguration yaml = FabricYamlConfiguration.loadStrict(dataDirectory.resolve("config.yml").toFile());
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
                    dataDirectory, PortableConfigMigration.CONFIG_SCHEMA_VERSION, this::resource,
                    in -> FabricYamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)).flatLeafValues(),
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
        return KwcFabricRuntime.class.getClassLoader().getResourceAsStream(name.startsWith("/") ? name.substring(1) : name);
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
    public FabricPlatformAdapter platformAdapter() { return platformAdapter; }
    public FabricStorage storage() { return storage; }
    public FabricAuthManager authManager() { return authManager; }
    public CaptchaManager captchaManager() { return captchaManager; }
    public FabricModerationManager moderationManager() { return moderationManager; }
    public FabricLangManager langManager() { return langManager; }
    public FabricDiscordBridge discordBridge() { return discordBridge; }
    public DirectMessageStore directMessages() { return directMessages; }
    public GroupChatStore groupChats() { return groupChats; }
    public ServerRelay serverRelay() { return serverRelay; }
    public WebChatServer webServer() { return webServer; }

    public String version() { return "5.3.1"; }

    public String serverName() {
        if (configValues != null && configValues.serverRelayServerName != null && !configValues.serverRelayServerName.isBlank()) return configValues.serverRelayServerName;
        return "Fabric Server";
    }
    public String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
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
