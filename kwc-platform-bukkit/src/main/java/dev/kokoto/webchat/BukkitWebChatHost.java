package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * BukkitWebChatHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * BukkitWebChatHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bukkit service adapter for the loader-neutral core web server. */
public final class BukkitWebChatHost implements WebChatHost {
    private final KokotoWebChatPlugin plugin;
    private final CoreLogger logger;
    private final WebPushHost webPushHost;

    public BukkitWebChatHost(KokotoWebChatPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = CoreLogger.of(plugin.getLogger()::info, plugin.getLogger()::warning);
        this.webPushHost = new BukkitWebPushHost(plugin);
    }

    @Override public ConfigValues configValues() { return plugin.configValues(); }
    @Override public PlatformAdapter platformAdapter() { return plugin.platformAdapter(); }
    @Override public WebChatStorage storage() { return plugin.storage(); }
    @Override public WebChatAuth auth() { return plugin.authManager(); }
    @Override public CaptchaManager captcha() { return plugin.captchaManager(); }
    @Override public DirectMessageStore directMessages() { return plugin.directMessages(); }
    @Override public GroupChatStore groupChats() { return plugin.groupChats(); }
    @Override public ServerRelay serverRelay() { return plugin.serverRelay(); }
    @Override public WebChatLanguage language() { return plugin.langManager(); }
    @Override public WebChatModeration moderation() { return plugin.moderationManager(); }
    @Override public WebChatDiscord discord() { return plugin.discordBridge(); }
    @Override public WebPushHost webPushHost() { return webPushHost; }

    @Override public Path dataDirectory() { return plugin.getDataFolder().toPath(); }
    @Override public InputStream resource(String name) { return plugin.getResource(name); }
    @Override public String version() { return plugin.getDescription().getVersion(); }
    @Override public CoreLogger logger() { return logger; }
    @Override public List<ContentFilterRule> loadContentFilterRulesFromDisk() {
        try {
            return ConfigTextEditor.readContentFilterRules(dataDirectory().resolve("config.yml"));
        } catch (Exception ex) {
            logger.warn("Failed to reload content-filter.rules from config.yml: " + ex.getMessage());
            return null;
        }
    }

    @Override public String displayNameForAccount(Account account) { return plugin.displayNameForAccount(account); }
    @Override public String applyMessageTokens(String text) { return plugin.applyMessageTokens(text); }
    @Override public String applyMessageTokensForGame(String text) { return plugin.applyMessageTokensForGame(text); }
    @Override public String restoreMessageTokenGameBreaks(String text) { return plugin.restoreMessageTokenGameBreaks(text); }
    @Override public List<String> splitMessageTokenGameLines(String text) { return plugin.splitMessageTokenGameLines(text); }

    @Override public void publishAnnouncement(String key, Map<String, String> placeholders) {
        plugin.publishAnnouncement(key, placeholders);
    }

    @Override public void audit(String action, String actor, Map<String, ?> details) {
        AuditLogger.log(plugin, action, actor, details);
    }
}
