package dev.kokoto.webchat.forge;

/* KWC 파일 안내 / KWC file guide
 * KwcForgeMod는 Minecraft/loader 버전 차이를 흡수하는 Forge compatibility shim이다.
 * KwcForgeMod is a Forge compatibility shim absorbing Minecraft/loader API differences across target versions.
 *
 * reflection/method signature 분기는 정확한 target 범위에만 적용하고, 공통 runtime 코드가 버전별 API를 직접 참조하지 않게 한다.
 * Restrict reflection/signature branches to their exact target range and keep version-specific APIs out of common runtime code.
 */
import dev.kokoto.webchat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
@Mod(KwcForgeMod.MOD_ID) public final class KwcForgeMod {
 public static final String MOD_ID="kokoto_webchat"; public static final String VERSION="5.3.1"; private static final KwcForgeRuntime R=new KwcForgeRuntime(); private final ForgeCommandBridge commands=new ForgeCommandBridge(R);
 public KwcForgeMod(){ R.initializeBlueMapIntegration(ModList.get().isLoaded("bluemap")); MinecraftForge.EVENT_BUS.register(this); }
 @SubscribeEvent public void started(ServerStartedEvent e){R.start(e.getServer());}
 @SubscribeEvent public void stopping(ServerStoppingEvent e){R.stop();}
 @SubscribeEvent public void join(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)R.onPlayerJoin(p);}
 @SubscribeEvent public void leave(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)R.onPlayerLeave(p);}
 @SubscribeEvent public void commands(RegisterCommandsEvent e){commands.register(e.getDispatcher());}
 @SubscribeEvent public void chat(ServerChatEvent e){ R.onPlayerChat(e.getPlayer(), e.getRawText()); }
}
