package dev.kokoto.webchat.forge;

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
