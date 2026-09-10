package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * WebChatHost는 loader-neutral core와 실제 서버/맵 플랫폼 구현 사이의 경계 인터페이스 또는 bridge다.
 * WebChatHost is a boundary interface/bridge between loader-neutral core and the concrete server/map-platform implementation.
 *
 * core에서 Bukkit/Fabric/Forge/NeoForge 전용 타입을 직접 참조하지 않도록 이 경계를 유지해야 다중 플랫폼 빌드가 서로 독립적으로 유지된다.
 * Keep platform-specific Bukkit/Fabric/Forge/NeoForge types behind this boundary so multi-platform builds remain independent.
 */
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
