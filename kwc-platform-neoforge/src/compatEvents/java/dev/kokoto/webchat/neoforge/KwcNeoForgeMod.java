package dev.kokoto.webchat.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kokoto.webchat.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Mod(KwcNeoForgeMod.MOD_ID)
public final class KwcNeoForgeMod {
    public static final String MOD_ID = "kokoto_webchat";
    public static final String VERSION = "5.1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("KOKOTO WebChat");
    private static final KwcNeoForgeRuntime RUNTIME = new KwcNeoForgeRuntime();
    private static final NeoForgeWebChatHost COMMAND_HOST = new NeoForgeWebChatHost(RUNTIME);
    private static final GameCommandService GAME_COMMANDS = new GameCommandService(COMMAND_HOST, RUNTIME::webServer);

    public KwcNeoForgeMod(IEventBus modEventBus) {
        RUNTIME.initializeBlueMapIntegration(ModList.get().isLoaded("bluemap"));
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPermissionNodes(PermissionGatherEvent.Nodes event) {
        NeoForgePermissions.register(event);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        RUNTIME.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        RUNTIME.stop();
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RUNTIME.onPlayerJoin(player);
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RUNTIME.onPlayerLeave(player);
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        WebChatServer server = RUNTIME.webServer();
        ServerPlayer player = NeoForgeCompat.chatPlayer(event);
        String body = NeoForgeCompat.chatText(event);
        if (player == null) return;
        if (server != null) {
            ContentFilterResult filtered = server.filterContent(body, ContentFilterEngine.Scope.PUBLIC);
            if (filtered.blocked) {
                NeoForgeCompat.cancelChat(event);
                NeoForgeCompat.sendPlayerMessage(player, NeoForgeGameMessageRenderer.plain(server.contentFilterBlockedMessage(filtered)));
                return;
            }
            if (filtered.changed) {
                NeoForgeCompat.setChatMessage(event, NeoForgeGameMessageRenderer.plain(filtered.message));
                body = filtered.message;
            }
        }
        RUNTIME.onPlayerChat(player, body);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var root = Commands.literal("kchat")
                .executes(ctx -> commandShared(ctx.getSource(), ""))
                .then(Commands.literal("reload").executes(ctx -> commandReload(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> commandStatus(ctx.getSource())))
                .then(Commands.literal("auth")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(ctx -> commandAuth(ctx.getSource(), StringArgumentType.getString(ctx, "code")))))
                .then(Commands.literal("password")
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(ctx -> commandPassword(ctx.getSource(), StringArgumentType.getString(ctx, "password")))))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .executes(ctx -> commandShared(ctx.getSource(), StringArgumentType.getString(ctx, "arguments"))));
        var kchat = dispatcher.register(root);
        dispatcher.register(Commands.literal("kc").redirect(kchat));
    }

    private static int commandShared(CommandSourceStack source, String arguments) {
        ServerPlayer player = null;
        player = NeoForgeCompat.sourcePlayer(source);
        final ServerPlayer current = player;
        GameCommandService.Sender sender = new GameCommandService.Sender() {
            @Override public boolean isPlayer() { return current != null; }
            @Override public java.util.UUID uuid() { return current == null ? null : current.getUUID(); }
            @Override public String username() { return current == null ? source.getTextName() : NeoForgeCompat.profileName(current); }
            @Override public String displayName() { return current == null ? source.getTextName() : RUNTIME.displayPlayerName(current); }
            @Override public String actorName() { return source.getTextName(); }
            @Override public void send(String message) { NeoForgeCompat.sendSourceMessage(source, NeoForgeGameMessageRenderer.plain(message)); }
        };
        return GAME_COMMANDS.execute(sender, arguments);
    }

    private static int commandReload(CommandSourceStack source) {
        if (!hasPermission(source, "kwc.admin")) return fail(source, "You do not have permission.");
        RUNTIME.audit("command.reload", source.getTextName(), Map.of("platform", "neoforge"));
        if (!RUNTIME.reload()) return fail(source, "KOKOTO WebChat configuration reload failed. Check the server log.");
        return ok(source, "KOKOTO WebChat configuration reloaded.");
    }

    private static int commandStatus(CommandSourceStack source) {
        var c = RUNTIME.configValues();
        if (c == null) return fail(source, "KOKOTO WebChat has not loaded a configuration yet.");
        String standalone = c.standaloneWebEnabled ? (c.standaloneWebPath == null ? "/" : c.standaloneWebPath) : "disabled";
        return ok(source, "KOKOTO WebChat " + VERSION + " | enabled=" + c.pluginEnabled + " | HTTP=" + c.httpHost + ":" + c.httpPort + " | standalone=" + standalone);
    }

    private static int commandAuth(CommandSourceStack source, String code) {
        ServerPlayer p;
        p = NeoForgeCompat.sourcePlayer(source);
        if (p == null) return fail(source, "This command can only be used by players.");
        if (!RUNTIME.active() || RUNTIME.authManager() == null) return fail(source, "KOKOTO WebChat is not running.");
        if (!RUNTIME.platformAdapter().hasPermission(p.getUUID(), "kwc.auth") && !hasPermission(source, "kwc.admin")) {
            return fail(source, "You do not have permission.");
        }
        NeoForgeAuthManager.LinkResult result = RUNTIME.authManager().completeCode(code,
                new PlatformPlayer(p.getUUID(), NeoForgeCompat.profileName(p), RUNTIME.displayPlayerName(p), hasPermission(source, "kwc.admin")));
        return result.ok ? ok(source, result.message) : fail(source, result.message);
    }

    private static int commandPassword(CommandSourceStack source, String password) {
        ServerPlayer p;
        p = NeoForgeCompat.sourcePlayer(source);
        if (p == null) return fail(source, "This command can only be used by players.");
        if (!RUNTIME.active() || RUNTIME.storage() == null) return fail(source, "KOKOTO WebChat is not running.");
        Role role = RUNTIME.platformAdapter().hasPermission(p.getUUID(), RUNTIME.configValues().adminPermission) ? Role.ADMIN : Role.USER;
        Account account = RUNTIME.storage().upsertLinkedAccount(p.getUUID().toString(), NeoForgeCompat.profileName(p), role, RUNTIME.displayPlayerName(p));
        RUNTIME.storage().setPassword(account, password);
        return ok(source, "Web chat password has been set.");
    }

    private static boolean hasPermission(CommandSourceStack source, String permission) {
        try {
            ServerPlayer p = NeoForgeCompat.sourcePlayer(source);
            if (p != null) return RUNTIME.platformAdapter() != null && RUNTIME.platformAdapter().hasPermission(p.getUUID(), permission);
        } catch (Throwable ignored) {}
        return !NeoForgeCompat.sourceHasEntity(source);
    }

    private static int ok(CommandSourceStack source, String text) {
        NeoForgeCompat.sendSourceMessage(source, NeoForgeGameMessageRenderer.plain(text));
        return 1;
    }

    private static int fail(CommandSourceStack source, String text) {
        NeoForgeCompat.sendSourceMessage(source, NeoForgeGameMessageRenderer.plain(text));
        return 0;
    }
}
