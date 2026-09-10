package dev.kokoto.webchat.fabric;


/* KWC 파일 안내 / KWC file guide
 * FabricPlatformAdapter는 Fabric API를 loader-neutral core/adapter 계약으로 변환하는 플랫폼 bridge다.
 * FabricPlatformAdapter bridges Fabric APIs into loader-neutral core/adapter contracts.
 *
 * 플랫폼 객체를 core에 노출하지 않고 UUID/중립 모델만 넘겨 다른 loader target의 classpath와 섞이지 않게 한다.
 * Expose only UUID/neutral models to core rather than platform objects so loader target classpaths stay isolated.
 */
import dev.kokoto.webchat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.*;

/** Fabric implementation of the shared Minecraft platform boundary across exact targets. */
public final class FabricPlatformAdapter implements PlatformAdapter {
    private final KwcFabricRuntime runtime;
    public FabricPlatformAdapter(KwcFabricRuntime runtime) { this.runtime = runtime; }
    private MinecraftServer server() { return runtime.server(); }

    @Override public String platformName() { return "Fabric"; }
    @Override public String minecraftVersion() { return runtime.minecraftVersion(); }
    @Override public Path dataDirectory() { return runtime.dataDirectory(); }

    @Override public Collection<PlatformPlayer> onlinePlayers() {
        ArrayList<PlatformPlayer> out = new ArrayList<>();
        if (server() == null) return out;
        for (ServerPlayer p : server().getPlayerList().getPlayers()) out.add(snapshot(p));
        return out;
    }

    @Override public Optional<PlatformPlayer> onlinePlayer(UUID uuid) {
        if (server() == null || uuid == null) return Optional.empty();
        ServerPlayer p = server().getPlayerList().getPlayer(uuid);
        return p == null ? Optional.empty() : Optional.of(snapshot(p));
    }

    @Override public boolean hasPermission(UUID uuid, String permission) {
        if (server() == null || uuid == null || permission == null || permission.isBlank()) return false;
        ServerPlayer player = server().getPlayerList().getPlayer(uuid);
        if (player == null) return false;
        boolean fallback = defaultPermission(permission, player);
        return FabricCompat.checkPermission(player, permission, fallback);
    }

    @Override public boolean isMainThread() { return server() != null && server().isSameThread(); }
    @Override public void runMainThread(Runnable task) {
        if (task == null || server() == null) return;
        if (server().isSameThread()) task.run(); else server().execute(task);
    }

    @Override public boolean dispatchConsoleCommand(String command) {
        if (server() == null) return false;
        String value = String.valueOf(command == null ? "" : command).trim();
        if (value.startsWith("/")) value = value.substring(1);
        if (value.isBlank()) return false;
        final String cmd = value;
        Runnable task = () -> FabricCompat.dispatchConsoleCommand(server(), cmd);
        if (server().isSameThread()) task.run(); else server().execute(task);
        return true;
    }

    @Override public Set<String> knownPlayerNames() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (server() != null) for (ServerPlayer p : server().getPlayerList().getPlayers()) out.add(FabricCompat.profileName(p));
        FabricStorage storage = runtime.storage();
        if (storage != null) for (PlayerIdentity p : storage.listKnownPlayers("", 0)) {
            if (p != null && p.username != null && !p.username.isBlank() && !RemotePlayerRef.isRemote(p.uuid)) out.add(p.username);
        }
        return out;
    }

    @Override public Set<String> knownPlayerNameAliases() {
        LinkedHashSet<String> out = new LinkedHashSet<>(knownPlayerNames());
        if (server() != null) for (ServerPlayer p : server().getPlayerList().getPlayers()) {
            add(out, FabricCompat.profileName(p));
            add(out, runtime.displayPlayerName(p));
        }
        FabricStorage storage = runtime.storage();
        if (storage != null) for (PlayerIdentity p : storage.listKnownPlayers("", 0)) {
            if (p == null || RemotePlayerRef.isRemote(p.uuid)) continue;
            add(out, p.username); add(out, p.displayName);
        }
        return out;
    }

    @Override public void sendPlainMessage(UUID uuid, String message) {
        runMainThread(() -> {
            ServerPlayer p = server().getPlayerList().getPlayer(uuid);
            if (p != null) FabricCompat.sendPlayerMessage(p, FabricGameMessageRenderer.plain(message));
        });
    }

    @Override public void broadcastPlainMessage(String message) {
        runMainThread(() -> {
            Component c = FabricGameMessageRenderer.plain(message);
            for (ServerPlayer p : server().getPlayerList().getPlayers()) FabricCompat.sendPlayerMessage(p, c);
            runtime.info(c.getString());
        });
    }

    @Override public void sendInteractiveMessage(Collection<UUID> recipients, PlatformGameMessage message) {
        if (message == null) return;
        Collection<UUID> ids = recipients == null ? null : new ArrayList<>(recipients);
        runMainThread(() -> {
            Component c = FabricGameMessageRenderer.interactive(message);
            if (ids == null) {
                for (ServerPlayer p : server().getPlayerList().getPlayers()) FabricCompat.sendPlayerMessage(p, c);
            } else {
                for (UUID id : ids) {
                    ServerPlayer p = server().getPlayerList().getPlayer(id);
                    if (p != null) FabricCompat.sendPlayerMessage(p, c);
                }
            }
        });
    }

    @Override public void broadcastInteractiveMessage(PlatformGameMessage message) {
        if (message == null) return;
        runMainThread(() -> {
            Component c = FabricGameMessageRenderer.interactive(message);
            for (ServerPlayer p : server().getPlayerList().getPlayers()) FabricCompat.sendPlayerMessage(p, c);
            runtime.info(message.text());
        });
    }

    @Override public Map<String,String> imageEmojiRuntimeSymbols() { return Map.of(); }

    private PlatformPlayer snapshot(ServerPlayer p) {
        return new PlatformPlayer(p.getUUID(), FabricCompat.profileName(p), runtime.displayPlayerName(p), isLikelyOperator(p));
    }

    private boolean defaultPermission(String permission, ServerPlayer player) {
        String key = String.valueOf(permission == null ? "" : permission).trim().toLowerCase(Locale.ROOT);
        if (key.equals("kwc.auth") || key.equals("kwc.webchat") || key.equals("kwc.dm")
                || key.equals("kwc.reply") || key.equals("kwc.group")) return true;
        if (key.equals("kwc.admin") || key.startsWith("kwc.admin.") || key.equals("kwc.update.notify")) return isLikelyOperator(player);
        return isLikelyOperator(player);
    }

    private boolean isLikelyOperator(ServerPlayer p) {
        return FabricCompat.isOperator(server(), p);
    }

    private static void add(Set<String> out, String value) { if (value != null && !value.isBlank()) out.add(value); }
}
