package dev.kokoto.webchat.neoforge;


/* KWC 파일 안내 / KWC file guide
 * NeoForgePermissions는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * NeoForgePermissions is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** KWC permission nodes registered with NeoForge's native permission API. */
public final class NeoForgePermissions {
    private NeoForgePermissions() {}

    private static PermissionNode<Boolean> allow(String path) {
        return new PermissionNode<>("kwc", path, PermissionTypes.BOOLEAN, (player, uuid, context) -> true);
    }

    private static PermissionNode<Boolean> opOnly(String path) {
        return new PermissionNode<>("kwc", path, PermissionTypes.BOOLEAN, (player, uuid, context) -> false);
    }

    public static final PermissionNode<Boolean> AUTH = allow("auth");
    public static final PermissionNode<Boolean> WEBCHAT = allow("webchat");
    public static final PermissionNode<Boolean> ADMIN = opOnly("admin");
    public static final PermissionNode<Boolean> ADMIN_FILTER = opOnly("admin.filter");
    public static final PermissionNode<Boolean> ADMIN_SETTINGS = opOnly("admin.settings");
    public static final PermissionNode<Boolean> UPDATE_NOTIFY = opOnly("update.notify");
    public static final PermissionNode<Boolean> DM = allow("dm");
    public static final PermissionNode<Boolean> REPLY = allow("reply");
    public static final PermissionNode<Boolean> GROUP = allow("group");

    private static final Map<String, PermissionNode<Boolean>> NODES = new LinkedHashMap<>();
    static {
        NODES.put("kwc.auth", AUTH);
        NODES.put("kwc.webchat", WEBCHAT);
        NODES.put("kwc.admin", ADMIN);
        NODES.put("kwc.admin.filter", ADMIN_FILTER);
        NODES.put("kwc.admin.settings", ADMIN_SETTINGS);
        NODES.put("kwc.update.notify", UPDATE_NOTIFY);
        NODES.put("kwc.dm", DM);
        NODES.put("kwc.reply", REPLY);
        NODES.put("kwc.group", GROUP);
    }

    public static void register(PermissionGatherEvent.Nodes event) {
        for (PermissionNode<Boolean> node : NODES.values()) event.addNodes(node);
    }

    public static boolean check(ServerPlayer player, String permission, boolean operatorFallback) {
        if (player == null || permission == null || permission.isBlank()) return false;
        String key = permission.trim().toLowerCase(Locale.ROOT);
        PermissionNode<Boolean> node = NODES.get(key);
        if (node == null) return operatorFallback;
        try {
            Boolean value = PermissionAPI.getPermission(player, node);
            if (Boolean.TRUE.equals(value)) return true;
            // Preserve Bukkit semantics for default: op permissions even with NeoForge's default handler.
            if ((node == ADMIN || node == ADMIN_FILTER || node == ADMIN_SETTINGS || node == UPDATE_NOTIFY) && operatorFallback) return true;
            return false;
        } catch (Throwable ignored) {
            if (node == ADMIN || node == ADMIN_FILTER || node == ADMIN_SETTINGS || node == UPDATE_NOTIFY) return operatorFallback;
            // General KWC feature permissions are default-true, matching plugin.yml.
            return true;
        }
    }
}
