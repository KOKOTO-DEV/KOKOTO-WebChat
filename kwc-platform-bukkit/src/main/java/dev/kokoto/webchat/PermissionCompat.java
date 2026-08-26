package dev.kokoto.webchat;

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
