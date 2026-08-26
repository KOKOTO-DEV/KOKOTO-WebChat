package dev.kokoto.webchat.forge;
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
 public static final String MOD_ID="kokoto_webchat"; private static final KwcForgeRuntime R=new KwcForgeRuntime(); private final ForgeCommandBridge commands=new ForgeCommandBridge(R);
 public KwcForgeMod(){R.initializeBlueMapIntegration(ModList.get().isLoaded("bluemap"));MinecraftForge.EVENT_BUS.register(this);}
 @SubscribeEvent public void started(ServerStartedEvent e){R.start(e.getServer());} @SubscribeEvent public void stopping(ServerStoppingEvent e){R.stop();}
 @SubscribeEvent public void join(PlayerEvent.PlayerLoggedInEvent e){if(e.getPlayer() instanceof ServerPlayer)R.onPlayerJoin((ServerPlayer)e.getPlayer());}
 @SubscribeEvent public void leave(PlayerEvent.PlayerLoggedOutEvent e){if(e.getPlayer() instanceof ServerPlayer)R.onPlayerLeave((ServerPlayer)e.getPlayer());}
 @SubscribeEvent public void commands(RegisterCommandsEvent e){commands.register(e.getDispatcher());}
 @SubscribeEvent public void chat(ServerChatEvent e){R.onPlayerChat(e.getPlayer(),e.getMessage());}
}
