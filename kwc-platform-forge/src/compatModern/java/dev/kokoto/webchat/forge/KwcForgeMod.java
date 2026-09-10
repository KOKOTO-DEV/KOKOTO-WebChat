package dev.kokoto.webchat.forge;

/* KWC 파일 안내 / KWC file guide
 * KwcForgeMod는 Minecraft/loader 버전 차이를 흡수하는 Forge compatibility shim이다.
 * KwcForgeMod is a Forge compatibility shim absorbing Minecraft/loader API differences across target versions.
 *
 * reflection/method signature 분기는 정확한 target 범위에만 적용하고, 공통 runtime 코드가 버전별 API를 직접 참조하지 않게 한다.
 * Restrict reflection/signature branches to their exact target range and keep version-specific APIs out of common runtime code.
 */
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
@Mod(KwcForgeMod.MOD_ID) public final class KwcForgeMod {
 public static final String MOD_ID="kokoto_webchat"; private static final KwcForgeRuntime R=new KwcForgeRuntime(); private final ForgeCommandBridge commands=new ForgeCommandBridge(R);
 public KwcForgeMod(){R.initializeBlueMapIntegration(ModList.get().isLoaded("bluemap")); ServerStartedEvent.BUS.addListener(e->R.start(e.getServer())); ServerStoppingEvent.BUS.addListener(e->R.stop()); PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e->{if(e.getEntity() instanceof ServerPlayer p)R.onPlayerJoin(p);}); PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e->{if(e.getEntity() instanceof ServerPlayer p)R.onPlayerLeave(p);}); RegisterCommandsEvent.BUS.addListener(e->commands.register(e.getDispatcher())); ServerChatEvent.BUS.addListener((Consumer<ServerChatEvent>) e->R.onPlayerChat(e.getPlayer(),e.getRawText())); }
}
