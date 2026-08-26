package dev.kokoto.webchat;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Loader-neutral service boundary consumed by {@link WebChatServer}.
 * Platform modules provide one host implementation and keep loader APIs outside core.
 */
public interface WebChatHost {
    ConfigValues configValues();
    PlatformAdapter platformAdapter();
    WebChatStorage storage();
    WebChatAuth auth();
    CaptchaManager captcha();
    DirectMessageStore directMessages();
    GroupChatStore groupChats();
    ServerRelay serverRelay();
    WebChatLanguage language();
    WebChatModeration moderation();
    WebChatDiscord discord();
    WebPushHost webPushHost();

    Path dataDirectory();
    InputStream resource(String name);
    String version();
    CoreLogger logger();

    /** Reload just the content-filter rule list from the platform's current config.yml. */
    List<ContentFilterRule> loadContentFilterRulesFromDisk();

    String displayNameForAccount(Account account);
    String applyMessageTokens(String text);
    String applyMessageTokensForGame(String text);
    String restoreMessageTokenGameBreaks(String text);
    List<String> splitMessageTokenGameLines(String text);

    void publishAnnouncement(String key, Map<String, String> placeholders);
    void audit(String action, String actor, Map<String, ?> details);
}
