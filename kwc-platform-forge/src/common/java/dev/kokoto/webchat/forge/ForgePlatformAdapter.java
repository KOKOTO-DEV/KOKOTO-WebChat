package dev.kokoto.webchat.forge;

import dev.kokoto.webchat.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.*;

/** Forge exact-target implementation of the shared Minecraft platform boundary. */
public final class ForgePlatformAdapter implements PlatformAdapter {
    private final KwcForgeRuntime runtime;
    public ForgePlatformAdapter(KwcForgeRuntime runtime) { this.runtime = runtime; }
    private MinecraftServer server() { return runtime.server(); }

    @Override public String platformName() { return "Forge"; }
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
        return ForgePermissions.has(player, permission);
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
        Runnable task = () -> ForgeCompat.dispatchConsoleCommand(server(), cmd);
        if (server().isSameThread()) task.run(); else server().execute(task);
        return true;
    }

    @Override public Set<String> knownPlayerNames() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (server() != null) for (ServerPlayer p : server().getPlayerList().getPlayers()) out.add(ForgeCompat.profileName(p));
        ForgeStorage storage = runtime.storage();
        if (storage != null) for (PlayerIdentity p : storage.listKnownPlayers("", 0)) {
            if (p != null && p.username != null && !p.username.isBlank() && !RemotePlayerRef.isRemote(p.uuid)) out.add(p.username);
        }
        return out;
    }

    @Override public Set<String> knownPlayerNameAliases() {
        LinkedHashSet<String> out = new LinkedHashSet<>(knownPlayerNames());
        if (server() != null) for (ServerPlayer p : server().getPlayerList().getPlayers()) {
            add(out, ForgeCompat.profileName(p));
            add(out, runtime.displayPlayerName(p));
        }
        ForgeStorage storage = runtime.storage();
        if (storage != null) for (PlayerIdentity p : storage.listKnownPlayers("", 0)) {
            if (p == null || RemotePlayerRef.isRemote(p.uuid)) continue;
            add(out, p.username); add(out, p.displayName);
        }
        return out;
    }

    @Override public void sendPlainMessage(UUID uuid, String message) {
        runMainThread(() -> {
            ServerPlayer p = server().getPlayerList().getPlayer(uuid);
            if (p != null) ForgeCompat.sendPlayerMessage(p, ForgeCompat.text(String.valueOf(message == null ? "" : message)));
        });
    }

    @Override public void broadcastPlainMessage(String message) {
        runMainThread(() -> {
            Component c = ForgeCompat.text(String.valueOf(message == null ? "" : message));
            for (ServerPlayer p : server().getPlayerList().getPlayers()) ForgeCompat.sendPlayerMessage(p, c);
            runtime.info(c.getString());
        });
    }

    @Override public void sendInteractiveMessage(Collection<UUID> recipients, PlatformGameMessage message) {
        if (message == null) return;
        Collection<UUID> ids = recipients == null ? null : new ArrayList<>(recipients);
        runMainThread(() -> {
            Component c = ForgeCompat.text(message.text());
            if (ids == null) {
                for (ServerPlayer p : server().getPlayerList().getPlayers()) ForgeCompat.sendPlayerMessage(p, c);
            } else {
                for (UUID id : ids) {
                    ServerPlayer p = server().getPlayerList().getPlayer(id);
                    if (p != null) ForgeCompat.sendPlayerMessage(p, c);
                }
            }
        });
    }

    @Override public void broadcastInteractiveMessage(PlatformGameMessage message) {
        if (message == null) return;
        broadcastPlainMessage(message.text());
    }

    @Override public Map<String,String> imageEmojiRuntimeSymbols() { return Map.of(); }

    private PlatformPlayer snapshot(ServerPlayer p) {
        return new PlatformPlayer(p.getUUID(), ForgeCompat.profileName(p), runtime.displayPlayerName(p), isLikelyOperator(p));
    }

    private boolean isLikelyOperator(ServerPlayer p) {
        return ForgeCompat.isOperator(server(), p);
    }

    private static void add(Set<String> out, String value) { if (value != null && !value.isBlank()) out.add(value); }
}
