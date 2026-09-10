package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * PermissionCompat는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * PermissionCompat is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
import org.bukkit.command.CommandSender;

/** Compatibility bridge for permission nodes used by BlueMapWebChat 4.x. */
final class PermissionCompat {
    private static final String CURRENT_PREFIX = "kwc.";
    private static final String LEGACY_PREFIX = "bluemapwebchat.";

    private PermissionCompat() {}

    static boolean has(CommandSender sender, String permission) {
        if (sender == null || permission == null || permission.isBlank()) return false;
        if (sender.hasPermission(permission)) return true;
        if (permission.startsWith(CURRENT_PREFIX)) {
            return sender.hasPermission(LEGACY_PREFIX + permission.substring(CURRENT_PREFIX.length()));
        }
        return false;
    }
}
