package dev.kokoto.webchat.forge;


/* KWC 파일 안내 / KWC file guide
 * ForgePermissions는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * ForgePermissions is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
import net.minecraft.server.level.ServerPlayer;

/** Cross-version Forge permission fallback. Dedicated permission-node integration is intentionally not hard-linked. */
public final class ForgePermissions {
    private ForgePermissions() {}
    public static boolean has(ServerPlayer player, String permission) {
        if (player == null) return false;
        if ("kwc.auth".equalsIgnoreCase(permission)) return true;
        return ForgeCompat.isOperator(ForgeCompat.server(player), player);
    }
}
