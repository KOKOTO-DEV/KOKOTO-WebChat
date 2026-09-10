package dev.kokoto.webchat.fabric;


/* KWC 파일 안내 / KWC file guide
 * FabricDiscordBridge는 Fabric 런타임에서 KWC core 기능을 해당 loader/Minecraft API에 연결한다.
 * FabricDiscordBridge connects KWC core behavior to the concrete Fabric/Minecraft runtime APIs.
 *
 * 동일 기능의 다른 loader 구현과 의미를 맞추되 API 버전 차이는 이 플랫폼 계층 안에서만 처리한다.
 * Keep semantics aligned with other loaders while containing API-version differences within this platform layer.
 */
import dev.kokoto.webchat.ChatMessage;
import dev.kokoto.webchat.WebChatDiscord;

/** Fabric has no DiscordSRV-equivalent hard dependency. */
public final class FabricDiscordBridge implements WebChatDiscord {
    @Override public void sendWebMessage(ChatMessage msg) {}
    @Override public void sendGameMessage(ChatMessage msg) {}
    @Override public boolean shouldSuppressGameEcho(String player, String message) { return false; }
}
