package dev.kokoto.webchat.neoforge;


/* KWC 파일 안내 / KWC file guide
 * NeoForgeCompat는 Minecraft/loader 버전 차이를 흡수하는 NeoForge compatibility shim이다.
 * NeoForgeCompat is a NeoForge compatibility shim absorbing Minecraft/loader API differences across target versions.
 *
 * reflection/method signature 분기는 정확한 target 범위에만 적용하고, 공통 runtime 코드가 버전별 API를 직접 참조하지 않게 한다.
 * Restrict reflection/signature branches to their exact target range and keep version-specific APIs out of common runtime code.
 */
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/** Minecraft/NeoForge ABI bridge shared by the exact-target source families. */
final class NeoForgeCompat {
    private NeoForgeCompat() {}

    static String profileName(ServerPlayer player) {
        if (player == null) return "";
        try {
            Object profile=player.getGameProfile();
            Object value=invokeNoArg(profile,"name","getName");
            if (value!=null) return String.valueOf(value);
        } catch(Throwable ignored) {}
        try { return player.getName().getString(); } catch(Throwable ignored) { return ""; }
    }

    static String worldDimension(ServerPlayer player) {
        try {
            Object level=invokeNoArg(player,"serverLevel","level","getLevel");
            Object dimension=invokeNoArg(level,"dimension");
            return dimension==null?"":String.valueOf(dimension);
        } catch(Throwable ignored) { return ""; }
    }

    static boolean isOperator(MinecraftServer server, ServerPlayer player) {
        if(server==null||player==null) return false;
        try {
            Object list=server.getPlayerList(), profile=player.getGameProfile(), nameAndId=invokeNoArg(player,"nameAndId");
            for(Method m:list.getClass().getMethods()) {
                if(!m.getName().equals("isOp")||m.getParameterCount()!=1) continue;
                Class<?> t=m.getParameterTypes()[0]; Object arg=null;
                if(nameAndId!=null&&t.isInstance(nameAndId)) arg=nameAndId; else if(profile!=null&&t.isInstance(profile)) arg=profile;
                if(arg!=null) return Boolean.TRUE.equals(m.invoke(list,arg));
            }
        } catch(Throwable ignored) {}
        return false;
    }

    static boolean dispatchConsoleCommand(MinecraftServer server,String command) {
        if(server==null||command==null||command.isBlank()) return false;
        try {
            Object commands=server.getCommands(), source=server.createCommandSourceStack();
            for(String name:new String[]{"performPrefixedCommand","performCommand"}) for(Method m:commands.getClass().getMethods()) {
                if(!m.getName().equals(name)||m.getParameterCount()!=2) continue;
                if(!m.getParameterTypes()[0].isInstance(source)||m.getParameterTypes()[1]!=String.class) continue;
                m.invoke(commands,source,command); return true;
            }
        } catch(Throwable ignored) {}
        return false;
    }

    static void sendPlayerMessage(ServerPlayer player, Component message) {
        if(player==null||message==null) return;
        if(invokeCompatible(player,"sendSystemMessage",message)) return;
        if(invokeCompatible(player,"sendMessage",message, UUID.randomUUID())) return;
        invokeCompatible(player,"displayClientMessage",message,false);
    }

    static void sendSourceMessage(CommandSourceStack source, Component message) {
        if(source==null||message==null) return;
        if(invokeCompatible(source,"sendSystemMessage",message)) return;
        if(invokeCompatible(source,"sendSuccess",message,false)) return;
        try {
            for(Method m:source.getClass().getMethods()) if(m.getName().equals("sendSuccess")&&m.getParameterCount()==2&&java.util.function.Supplier.class.isAssignableFrom(m.getParameterTypes()[0])) {
                m.invoke(source,(java.util.function.Supplier<Component>)()->message,false); return;
            }
        } catch(Throwable ignored) {}
        invokeCompatible(source,"sendFailure",message);
    }

    static ServerPlayer sourcePlayer(CommandSourceStack source) {
        try { Object v=invokeNoArg(source,"getPlayer","getPlayerOrException"); return v instanceof ServerPlayer p?p:null; }
        catch(Throwable ignored) { return null; }
    }
    static boolean sourceHasEntity(CommandSourceStack source) { try { return invokeNoArg(source,"getEntity")!=null; } catch(Throwable ignored){return false;} }

    static ServerPlayer chatPlayer(Object event) {
        try { Object v=invokeNoArg(event,"getPlayer"); return v instanceof ServerPlayer p?p:null; } catch(Throwable ignored){return null;}
    }
    static String chatText(Object event) {
        for(String n:new String[]{"getRawText","getMessage","getOriginalMessage"}) try {
            Object v=invokeNoArg(event,n); if(v==null) continue; if(v instanceof String s) return s;
            Object s=invokeNoArg(v,"getString"); if(s!=null) return String.valueOf(s);
        } catch(Throwable ignored) {}
        return "";
    }
    static void cancelChat(Object event) { invokeCompatible(event,"setCanceled",true); }
    static void setChatMessage(Object event, Component message) { invokeCompatible(event,"setMessage",message); }

    private static Object invokeNoArg(Object target,String... names)throws Exception{
        if(target==null)return null; for(String n:names) try{return target.getClass().getMethod(n).invoke(target);}catch(NoSuchMethodException ignored){} return null;
    }
    private static boolean invokeCompatible(Object target,String name,Object...args){
        if(target==null)return false; try{for(Method m:target.getClass().getMethods()){
            if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue; Class<?>[]pt=m.getParameterTypes(); boolean ok=true;
            for(int i=0;i<pt.length;i++){Object a=args[i];if(a==null)continue;if(!box(pt[i]).isInstance(a)){ok=false;break;}}
            if(ok){m.invoke(target,args);return true;}
        }}catch(Throwable ignored){} return false;
    }
    private static Class<?> box(Class<?>t){if(!t.isPrimitive())return t;if(t==boolean.class)return Boolean.class;if(t==int.class)return Integer.class;if(t==long.class)return Long.class;if(t==double.class)return Double.class;if(t==float.class)return Float.class;if(t==short.class)return Short.class;if(t==byte.class)return Byte.class;if(t==char.class)return Character.class;return t;}
}
