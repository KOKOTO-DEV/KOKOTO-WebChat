package dev.kokoto.webchat.fabric;


/* KWC 파일 안내 / KWC file guide
 * KwcFabricMod는 Minecraft/loader 버전 차이를 흡수하는 Fabric compatibility shim이다.
 * KwcFabricMod is a Fabric compatibility shim absorbing Minecraft/loader API differences across target versions.
 *
 * reflection/method signature 분기는 정확한 target 범위에만 적용하고, 공통 runtime 코드가 버전별 API를 직접 참조하지 않게 한다.
 * Restrict reflection/signature branches to their exact target range and keep version-specific APIs out of common runtime code.
 */
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kokoto.webchat.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class KwcFabricMod implements ModInitializer {
    public static final String MOD_ID = "kokoto_webchat";
    public static final String VERSION = "5.3.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("KOKOTO WebChat");
    private static final KwcFabricRuntime RUNTIME = new KwcFabricRuntime();
    private static final FabricWebChatHost COMMAND_HOST = new FabricWebChatHost(RUNTIME);
    private static final GameCommandService GAME_COMMANDS = new GameCommandService(COMMAND_HOST, RUNTIME::webServer);

    /** Internal bridge used by the 1.18.2 mixin package without exposing package-private helpers. */
    public static KwcFabricRuntime mixinRuntime() { return RUNTIME; }

    /** Internal bridge used by the 1.18.2 mixin package to render/send a KWC system message. */
    public static void sendMixinMessage(ServerPlayer player, String message) {
        if (player == null) return;
        FabricCompat.sendPlayerMessage(player, FabricGameMessageRenderer.plain(message));
    }

    @Override
    public void onInitialize() {
        RUNTIME.initializeBlueMapIntegration(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bluemap"));
        ServerLifecycleEvents.SERVER_STARTED.register(RUNTIME::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RUNTIME.stop());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RUNTIME.onPlayerJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RUNTIME.onPlayerLeave(handler.player));

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
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
            dispatcher.register(Commands.literal("kwc").redirect(kchat));
        });
    }

    private static int commandShared(CommandSourceStack source, String arguments) {
        ServerPlayer player = FabricCompat.sourcePlayer(source);
        final ServerPlayer current = player;
        GameCommandService.Sender sender = new GameCommandService.Sender() {
            @Override public boolean isPlayer() { return current != null; }
            @Override public java.util.UUID uuid() { return current == null ? null : current.getUUID(); }
            @Override public String username() { return current == null ? source.getTextName() : FabricCompat.profileName(current); }
            @Override public String displayName() { return current == null ? source.getTextName() : RUNTIME.displayPlayerName(current); }
            @Override public String actorName() { return source.getTextName(); }
            @Override public void send(String message) { FabricCompat.sendSourceMessage(source, FabricGameMessageRenderer.plain(message)); }
        };
        return GAME_COMMANDS.execute(sender, arguments);
    }

    private static int commandReload(CommandSourceStack source) {
        if (!hasPermission(source, "kwc.admin")) return fail(source, "You do not have permission.");
        RUNTIME.audit("command.reload", source.getTextName(), Map.of("platform", "fabric"));
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
        ServerPlayer p = FabricCompat.sourcePlayer(source);
        if (p == null) return fail(source, "This command can only be used by players.");
        if (!RUNTIME.active() || RUNTIME.authManager() == null) return fail(source, "KOKOTO WebChat is not running.");
        if (!RUNTIME.platformAdapter().hasPermission(p.getUUID(), "kwc.auth") && !hasPermission(source, "kwc.admin")) {
            return fail(source, "You do not have permission.");
        }
        FabricAuthManager.LinkResult result = RUNTIME.authManager().completeCode(code,
                new PlatformPlayer(p.getUUID(), FabricCompat.profileName(p), RUNTIME.displayPlayerName(p), hasPermission(source, "kwc.admin")));
        return result.ok ? ok(source, result.message) : fail(source, result.message);
    }

    private static int commandPassword(CommandSourceStack source, String password) {
        ServerPlayer p = FabricCompat.sourcePlayer(source);
        if (p == null) return fail(source, "This command can only be used by players.");
        if (!RUNTIME.active() || RUNTIME.storage() == null) return fail(source, "KOKOTO WebChat is not running.");
        Role role = RUNTIME.platformAdapter().hasPermission(p.getUUID(), RUNTIME.configValues().adminPermission) ? Role.ADMIN : Role.USER;
        Account account = RUNTIME.storage().upsertLinkedAccount(p.getUUID().toString(), FabricCompat.profileName(p), role, RUNTIME.displayPlayerName(p));
        RUNTIME.storage().setPassword(account, password);
        return ok(source, "Web chat password has been set.");
    }

    private static boolean hasPermission(CommandSourceStack source, String permission) {
        try {
            ServerPlayer p = FabricCompat.sourcePlayer(source);
            if (p != null) return RUNTIME.platformAdapter() != null && RUNTIME.platformAdapter().hasPermission(p.getUUID(), permission);
        } catch (Throwable ignored) {}
        // Server console / command blocks may administer KWC.
        return !FabricCompat.sourceHasEntity(source);
    }

    private static int ok(CommandSourceStack source, String text) {
        FabricCompat.sendSourceMessage(source, FabricGameMessageRenderer.plain(text));
        return 1;
    }
    private static int fail(CommandSourceStack source, String text) {
        FabricCompat.sendSourceMessage(source, FabricGameMessageRenderer.plain(text));
        return 0;
    }
}
