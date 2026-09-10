package dev.kokoto.webchat.forge;


/* KWC 파일 안내 / KWC file guide
 * ForgeCommandBridge는 Forge 런타임에서 KWC core 기능을 해당 loader/Minecraft API에 연결한다.
 * ForgeCommandBridge connects KWC core behavior to the concrete Forge/Minecraft runtime APIs.
 *
 * 동일 기능의 다른 loader 구현과 의미를 맞추되 API 버전 차이는 이 플랫폼 계층 안에서만 처리한다.
 * Keep semantics aligned with other loaders while containing API-version differences within this platform layer.
 */
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kokoto.webchat.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;

final class ForgeCommandBridge {
 private final KwcForgeRuntime runtime;
 private final ForgeWebChatHost host;
 private final GameCommandService commands;
 ForgeCommandBridge(KwcForgeRuntime runtime){ this.runtime=runtime; this.host=new ForgeWebChatHost(runtime); this.commands=new GameCommandService(host, runtime::webServer); }
 void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher){
   var root=Commands.literal("kchat").executes(c->shared(c.getSource(),""))
     .then(Commands.literal("reload").executes(c->reload(c.getSource())))
     .then(Commands.literal("status").executes(c->status(c.getSource())))
     .then(Commands.literal("auth").then(Commands.argument("code",StringArgumentType.word()).executes(c->auth(c.getSource(),StringArgumentType.getString(c,"code")))))
     .then(Commands.literal("password").then(Commands.argument("password",StringArgumentType.greedyString()).executes(c->password(c.getSource(),StringArgumentType.getString(c,"password")))))
     .then(Commands.argument("arguments",StringArgumentType.greedyString()).executes(c->shared(c.getSource(),StringArgumentType.getString(c,"arguments"))));
   var node=dispatcher.register(root); dispatcher.register(Commands.literal("kc").redirect(node)); dispatcher.register(Commands.literal("kwc").redirect(node));
 }
 private int shared(CommandSourceStack source,String args){
  ServerPlayer p=ForgeCompat.sourcePlayer(source); final ServerPlayer q=p;
  GameCommandService.Sender sender=new GameCommandService.Sender(){
   public boolean isPlayer(){return q!=null;} public java.util.UUID uuid(){return q==null?null:q.getUUID();}
   public String username(){return q==null?source.getTextName():ForgeCompat.profileName(q);}
   public String displayName(){return q==null?source.getTextName():runtime.displayPlayerName(q);}
   public String actorName(){return source.getTextName();}
   public void send(String m){ForgeCompat.sendSourceMessage(source, ForgeCompat.text(m));}
  }; return commands.execute(sender,args);
 }
 private boolean allowed(CommandSourceStack s,String perm){ ServerPlayer p=ForgeCompat.sourcePlayer(s); return p==null || ForgePermissions.has(p,perm); }
 private int reload(CommandSourceStack s){ if(!allowed(s,"kwc.admin")) return fail(s,"You do not have permission."); runtime.audit("command.reload",s.getTextName(),Map.of("platform","forge")); if(!runtime.reload()) return fail(s,"KOKOTO WebChat configuration reload failed. Check the server log."); return ok(s,"KOKOTO WebChat configuration reloaded."); }
 private int status(CommandSourceStack s){var c=runtime.configValues(); if(c==null)return fail(s,"KOKOTO WebChat has not loaded a configuration yet."); String standalone=c.standaloneWebEnabled?(c.standaloneWebPath==null?"/":c.standaloneWebPath):"disabled"; return ok(s,"KOKOTO WebChat 5.3.0 | enabled="+c.pluginEnabled+" | HTTP="+c.httpHost+":"+c.httpPort+" | standalone="+standalone);}
 private int auth(CommandSourceStack s,String code){ServerPlayer p=ForgeCompat.sourcePlayer(s);if(p==null)return fail(s,"This command can only be used by players."); if(!runtime.active()||runtime.authManager()==null)return fail(s,"KOKOTO WebChat is not running."); if(!ForgePermissions.has(p,"kwc.auth")&&!allowed(s,"kwc.admin"))return fail(s,"You do not have permission."); ForgeAuthManager.LinkResult r=runtime.authManager().completeCode(code,new PlatformPlayer(p.getUUID(),ForgeCompat.profileName(p),runtime.displayPlayerName(p),allowed(s,"kwc.admin"))); return r.ok?ok(s,r.message):fail(s,r.message);}
 private int password(CommandSourceStack s,String password){ServerPlayer p=ForgeCompat.sourcePlayer(s);if(p==null)return fail(s,"This command can only be used by players."); if(!runtime.active()||runtime.storage()==null)return fail(s,"KOKOTO WebChat is not running."); Role role=ForgePermissions.has(p,runtime.configValues().adminPermission)?Role.ADMIN:Role.USER; Account a=runtime.storage().upsertLinkedAccount(p.getUUID().toString(),ForgeCompat.profileName(p),role,runtime.displayPlayerName(p)); runtime.storage().setPassword(a,password); return ok(s,"Web chat password has been set.");}
 private int ok(CommandSourceStack s,String m){ForgeCompat.sendSourceMessage(s, ForgeCompat.text(m));return 1;} private int fail(CommandSourceStack s,String m){ForgeCompat.sendSourceMessage(s, ForgeCompat.text(m));return 0;}
}
