package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * WebChatDiscord는 kwc-core 모듈의 KWC 구현 파일이다. 클래스 이름이 나타내는 책임을 이 파일 안에 한정해 다른 계층과의 결합을 줄인다.
 * WebChatDiscord is a KWC implementation file in the kwc-core module. Keep the responsibility implied by the class name localized here to reduce cross-layer coupling.
 *
 * 변경 시 호출자와 반환값뿐 아니라 인증/권한, thread context, persistence, multi-loader 호환성에 미치는 영향을 함께 확인한다.
 * When changing it, review not only callers/returns but also effects on authorization, thread context, persistence, and multi-loader compatibility.
 */
import java.util.List;

/** Loader-neutral Discord integration boundary used by the web server. */
public interface WebChatDiscord {
    void sendWebMessage(ChatMessage msg);
    void sendGameMessage(ChatMessage msg);
    boolean shouldSuppressGameEcho(String player, String message);
    default boolean sendAdminAlert(String channel, String text) { return false; }
    /** DiscordSRV logical channel names suitable for the Admin alert selector. */
    default List<String> adminAlertChannelChoices() { return List.of(); }
}
