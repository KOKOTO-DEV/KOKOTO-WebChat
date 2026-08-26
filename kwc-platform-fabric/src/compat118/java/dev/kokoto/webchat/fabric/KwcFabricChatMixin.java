package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.ContentFilterEngine;
import dev.kokoto.webchat.ContentFilterResult;
import dev.kokoto.webchat.WebChatServer;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.18.2 bridge for game chat; Fabric ServerMessageEvents starts with the 1.19 generation. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class KwcFabricChatMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void kwc$onChat(ServerboundChatPacket packet, CallbackInfo ci) {
        KwcFabricRuntime runtime = KwcFabricMod.runtime();
        WebChatServer server = runtime == null ? null : runtime.webServer();
        if (runtime == null || server == null || player == null || packet == null) return;
        String body = String.valueOf(packet.getMessage() == null ? "" : packet.getMessage());
        if (body.startsWith("/")) return;
        ContentFilterResult filtered = server.filterContent(body, ContentFilterEngine.Scope.PUBLIC);
        if (filtered.blocked) {
            FabricCompat.sendPlayerMessage(player, FabricGameMessageRenderer.plain(server.contentFilterBlockedMessage(filtered)));
            ci.cancel();
            return;
        }
        if (filtered.changed) {
            runtime.broadcastFilteredPlayerChat(player, filtered.message);
            ci.cancel();
            return;
        }
        runtime.onPlayerChat(player, body);
    }
}
