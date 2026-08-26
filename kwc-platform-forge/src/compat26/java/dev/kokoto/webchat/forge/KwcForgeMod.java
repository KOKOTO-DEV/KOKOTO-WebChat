package dev.kokoto.webchat.forge;
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
 public KwcForgeMod(){R.initializeBlueMapIntegration(ModList.getModContainerById("bluemap").isPresent()); ServerStartedEvent.BUS.addListener(e->R.start(e.getServer())); ServerStoppingEvent.BUS.addListener(e->R.stop()); PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e->{if(e.getEntity() instanceof ServerPlayer p)R.onPlayerJoin(p);}); PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e->{if(e.getEntity() instanceof ServerPlayer p)R.onPlayerLeave(p);}); RegisterCommandsEvent.BUS.addListener(e->commands.register(e.getDispatcher())); ServerChatEvent.BUS.addListener((Consumer<ServerChatEvent>) e->R.onPlayerChat(e.getPlayer(),e.getRawText())); }
}
