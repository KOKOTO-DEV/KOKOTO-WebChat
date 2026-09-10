package dev.kokoto.webchat.fabric.mixin;


/* KWC 파일 안내 / KWC file guide
 * KwcFabricChatMixin는 Minecraft/loader 버전 차이를 흡수하는 Fabric compatibility shim이다.
 * KwcFabricChatMixin is a Fabric compatibility shim absorbing Minecraft/loader API differences across target versions.
 *
 * reflection/method signature 분기는 정확한 target 범위에만 적용하고, 공통 runtime 코드가 버전별 API를 직접 참조하지 않게 한다.
 * Restrict reflection/signature branches to their exact target range and keep version-specific APIs out of common runtime code.
 */
import dev.kokoto.webchat.ContentFilterEngine;
import dev.kokoto.webchat.ContentFilterResult;
import dev.kokoto.webchat.WebChatServer;
import dev.kokoto.webchat.fabric.KwcFabricMod;
import dev.kokoto.webchat.fabric.KwcFabricRuntime;
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
        KwcFabricRuntime runtime = KwcFabricMod.mixinRuntime();
        WebChatServer server = runtime == null ? null : runtime.webServer();
        if (runtime == null || server == null || player == null || packet == null) return;
        String body = String.valueOf(packet.getMessage() == null ? "" : packet.getMessage());
        if (body.startsWith("/")) return;
        ContentFilterResult filtered = server.filterContent(body, ContentFilterEngine.Scope.PUBLIC);
        if (filtered.blocked) {
            KwcFabricMod.sendMixinMessage(player, server.contentFilterBlockedMessage(filtered));
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
