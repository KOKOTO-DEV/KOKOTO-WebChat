package dev.kokoto.webchat.forge;


/* KWC 파일 안내 / KWC file guide
 * ForgeWebChatHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * ForgeWebChatHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
import dev.kokoto.webchat.*;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ForgeWebChatHost implements WebChatHost {
    private final KwcForgeRuntime runtime;
    private final CoreLogger logger;
    private final WebPushHost push;
    public ForgeWebChatHost(KwcForgeRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
        this.push = new ForgeWebPushHost(runtime);
    }
    @Override public ConfigValues configValues() { return runtime.configValues(); }
    @Override public PlatformAdapter platformAdapter() { return runtime.platformAdapter(); }
    @Override public WebChatStorage storage() { return runtime.storage(); }
    @Override public WebChatAuth auth() { return runtime.authManager(); }
    @Override public CaptchaManager captcha() { return runtime.captchaManager(); }
    @Override public DirectMessageStore directMessages() { return runtime.directMessages(); }
    @Override public GroupChatStore groupChats() { return runtime.groupChats(); }
    @Override public ServerRelay serverRelay() { return runtime.serverRelay(); }
    @Override public WebChatLanguage language() { return runtime.langManager(); }
    @Override public WebChatModeration moderation() { return runtime.moderationManager(); }
    @Override public WebChatDiscord discord() { return runtime.discordBridge(); }
    @Override public WebPushHost webPushHost() { return push; }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }
    @Override public InputStream resource(String name) { return runtime.resource(name); }
    @Override public String version() { return runtime.version(); }
    @Override public CoreLogger logger() { return logger; }
    @Override public List<ContentFilterRule> loadContentFilterRulesFromDisk() {
        try {
            return ConfigTextEditor.readContentFilterRules(dataDirectory().resolve("config.yml"));
        } catch (Exception ex) {
            logger.warn("Failed to reload content-filter.rules from config.yml: " + ex.getMessage());
            return null;
        }
    }
    @Override public String displayNameForAccount(Account account) { return runtime.displayNameForAccount(account); }
    @Override public String applyMessageTokens(String text) { return MessageTokenProcessor.apply(text, runtime.configValues()); }
    @Override public String applyMessageTokensForGame(String text) { return MessageTokenProcessor.applyForGameDisplay(text, runtime.configValues()); }
    @Override public String restoreMessageTokenGameBreaks(String text) { return MessageTokenProcessor.restoreGameDisplayBreaks(text); }
    @Override public List<String> splitMessageTokenGameLines(String text) { return MessageTokenProcessor.splitGameDisplayLines(text); }
    @Override public void publishAnnouncement(String key, Map<String,String> placeholders) { runtime.publishAnnouncement(key, placeholders); }
    @Override public void audit(String action, String actor, Map<String,?> details) { runtime.audit(action, actor, details); }
}
