package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * Bukkit/Fabric/Forge/NeoForge 플레이어 정보를 core에 전달하는 중립 모델이다.
 * Neutral player model passed from Bukkit/Fabric/Forge/NeoForge into core.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
import java.util.UUID;

/** Lightweight, loader-neutral online-player snapshot. */
public record PlatformPlayer(UUID uuid, String name, String displayName, boolean operator) {
}
