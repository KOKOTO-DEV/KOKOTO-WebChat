package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.*;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class FabricWebChatHost implements WebChatHost {
    private final KwcFabricRuntime runtime;
    private final CoreLogger logger;
    private final WebPushHost push;
    public FabricWebChatHost(KwcFabricRuntime runtime) {
        this.runtime = runtime;
        this.logger = CoreLogger.of(runtime::info, runtime::warn);
        this.push = new FabricWebPushHost(runtime);
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
