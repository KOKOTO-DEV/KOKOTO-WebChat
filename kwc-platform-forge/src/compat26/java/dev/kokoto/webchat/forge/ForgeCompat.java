package dev.kokoto.webchat.forge;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Supplier;
public final class ForgeCompat {
 private ForgeCompat(){}
 public static String profileName(ServerPlayer p){ return p.getGameProfile().name(); }
 public static boolean isOperator(MinecraftServer s, ServerPlayer p){ try { return s != null && s.getPlayerList().isOp(p.nameAndId()); } catch(Throwable x){ return false; } }
 public static Component text(String s){ return Component.literal(String.valueOf(s)); }

 public static MinecraftServer server(ServerPlayer p){
  if(p==null) return null;
  Object direct=invokeNoArg(p,"getServer");
  if(direct instanceof MinecraftServer) return (MinecraftServer)direct;
  Object level=invokeNoArg(p,"serverLevel","level","getLevel");
  Object fromLevel=invokeNoArg(level,"getServer");
  return fromLevel instanceof MinecraftServer ? (MinecraftServer)fromLevel : null;
 }
 public static String worldDimension(ServerPlayer p){
  if(p==null)return "";
  Object level=invokeNoArg(p,"serverLevel","level","getLevel");
  Object dimension=invokeNoArg(level,"dimension");
  return dimension==null?"":String.valueOf(dimension);
 }
 public static boolean dispatchConsoleCommand(MinecraftServer s,String command){
  if(s==null)return false;
  Object manager=s.getCommands();
  CommandSourceStack source=s.createCommandSourceStack();
  if(invoke(manager,"performPrefixedCommand",new Class<?>[]{CommandSourceStack.class,String.class},source,command))return true;
  return invoke(manager,"performCommand",new Class<?>[]{CommandSourceStack.class,String.class},source,command);
 }
 public static void sendPlayerMessage(ServerPlayer p,Component message){
  if(p==null||message==null)return;
  if(invoke(p,"sendSystemMessage",new Class<?>[]{Component.class},message))return;
  if(invoke(p,"sendMessage",new Class<?>[]{Component.class,UUID.class},message,new UUID(0L,0L)))return;
  invoke(p,"displayClientMessage",new Class<?>[]{Component.class,boolean.class},message,false);
 }
 public static void sendSourceMessage(CommandSourceStack source,Component message){
  if(source==null||message==null)return;
  if(invoke(source,"sendSystemMessage",new Class<?>[]{Component.class},message))return;
  if(invoke(source,"sendSuccess",new Class<?>[]{Component.class,boolean.class},message,false))return;
  Supplier<Component> supplier=()->message;
  if(invoke(source,"sendSuccess",new Class<?>[]{Supplier.class,boolean.class},supplier,false))return;
  invoke(source,"sendFailure",new Class<?>[]{Component.class},message);
 }
 public static ServerPlayer sourcePlayer(CommandSourceStack source){
  if(source==null)return null;
  try{ Object e=invokeNoArg(source,"getEntity"); return e instanceof ServerPlayer?(ServerPlayer)e:null; }catch(Throwable x){return null;}
 }
 private static Object invokeNoArg(Object target,String... names){
  if(target==null)return null;
  for(String name:names)try{Method m=target.getClass().getMethod(name);return m.invoke(target);}catch(Throwable ignored){}
  return null;
 }
 private static boolean invoke(Object target,String name,Class<?>[] types,Object... args){
  if(target==null)return false;
  try{Method m=target.getClass().getMethod(name,types);m.invoke(target,args);return true;}catch(Throwable ignored){return false;}
 }
}
