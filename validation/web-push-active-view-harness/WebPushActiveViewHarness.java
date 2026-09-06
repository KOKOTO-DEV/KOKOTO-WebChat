package dev.kokoto.webchat;

import dev.kokoto.webchat.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class WebPushActiveViewHarness {
  static class Host implements WebPushHost {
    final ConfigValues c = new ConfigValues();
    Host(){ c.webPushEnabled=true; }
    public ConfigValues config(){ return c; }
    public Path dataDirectory(){ return Paths.get("/tmp/kwc37-wp"); }
    public Account findAccountByUuid(String u){ return null; }
    public PlayerIdentity findKnownPlayerByUuid(String u){ return null; }
    public Map<String,String> webStringsFor(String l){ return Map.of(); }
    public void fine(String m){}
    public void warn(String m){}
  }
  static Object sub(String user, String device, String endpoint) throws Exception {
    Class<?> cls=Class.forName("dev.kokoto.webchat.WebPushManager$Subscription");
    Constructor<?> c=cls.getDeclaredConstructor(); c.setAccessible(true); Object s=c.newInstance();
    for (var pair: new String[][]{{"userUuid",user},{"deviceId",device},{"endpoint",endpoint}}) {
      Field f=cls.getDeclaredField(pair[0]); f.setAccessible(true); f.set(s,pair[1]);
    }
    return s;
  }
  @SuppressWarnings("unchecked")
  static void register(WebPushManager m,Object s,String endpoint) throws Exception {
    Field f=WebPushManager.class.getDeclaredField("byEndpoint"); f.setAccessible(true);
    ((Map<String,Object>)f.get(m)).put(endpoint,s);
  }
  static boolean suppress(WebPushManager m,Object s,WebPushManager.Payload p) throws Exception {
    Method meth=WebPushManager.class.getDeclaredMethod("suppressedByActivePrivateView", s.getClass(), WebPushManager.Payload.class);
    meth.setAccessible(true); return (Boolean)meth.invoke(m,s,p);
  }
  static void chk(boolean v,String n){ if(!v) throw new AssertionError(n); }
  public static void main(String[] args) throws Exception {
    WebPushManager m=new WebPushManager(new Host());
    Account a=new Account(); a.uuid="user-a";
    Object dev1=sub("user-a","d_1234567890123456","https://push.example/a");
    Object dev2=sub("user-a","d_9999999999999999","https://push.example/b");
    register(m,dev1,"https://push.example/a"); register(m,dev2,"https://push.example/b");
    m.updateActiveView(a,"d_1234567890123456","pv_client_123456",true,"dm-1","");
    WebPushManager.Payload dm1=new WebPushManager.Payload(); dm1.type="dm"; dm1.dmThreadId="dm-1";
    WebPushManager.Payload dm2=new WebPushManager.Payload(); dm2.type="dm"; dm2.dmThreadId="dm-2";
    chk(suppress(m,dev1,dm1),"current DM should suppress desktop subscription");
    chk(!suppress(m,dev1,dm2),"different DM should notify");
    chk(suppress(m,dev2,dm1),"current DM on desktop should also suppress mobile subscription");
    WebPushManager.ActivePrivateViewSnapshot snap1=m.activePrivateViewSnapshot("user-a");
    chk(snap1.dmThreadExpiresAt.containsKey("dm-1"),"account active-view snapshot should expose dm-1");
    chk(!snap1.groupRoomExpiresAt.containsKey("group-7"),"group-7 should not exist before group view");
    chk(m.unsubscribe(a,"https://push.example/a","d_1234567890123456",false),"desktop unsubscribe should remove desktop Push subscription");
    chk(suppress(m,dev2,dm1),"desktop Push OFF must not clear its active viewing state");
    m.updateActiveView(a,"d_1234567890123456","pv_client_654321",true,"","group-7");
    WebPushManager.Payload g=new WebPushManager.Payload(); g.type="group"; g.groupRoomId="group-7";
    chk(suppress(m,dev1,g),"second tab current group should suppress desktop");
    chk(suppress(m,dev2,g),"second tab current group should also suppress mobile");
    WebPushManager.ActivePrivateViewSnapshot snap2=m.activePrivateViewSnapshot("user-a");
    chk(snap2.dmThreadExpiresAt.containsKey("dm-1"),"snapshot should retain first active DM tab");
    chk(snap2.groupRoomExpiresAt.containsKey("group-7"),"snapshot should expose active group tab");
    m.updateActiveView(a,"d_1234567890123456","pv_client_123456",false,"","");
    chk(!suppress(m,dev1,dm1),"closed DM tab should no longer suppress DM");
    chk(suppress(m,dev1,g),"other active tab must remain active");
    WebPushManager.ActivePrivateViewSnapshot snap3=m.activePrivateViewSnapshot("user-a");
    chk(!snap3.dmThreadExpiresAt.containsKey("dm-1"),"snapshot should remove closed DM tab");
    chk(snap3.groupRoomExpiresAt.containsKey("group-7"),"snapshot should retain other active group tab");
    m.updateActiveView(a,"d_1234567890123456","pv_client_654321",false,"","");
    chk(!suppress(m,dev1,g),"closed group tab should notify");
    // A viewing browser does not need its own Web Push subscription. Its
    // authenticated foreground view still suppresses the account's phone Push.
    m.updateActiveView(a,"d_not_subscribed_12345","pv_client_999999",true,"dm-1","");
    chk(suppress(m,dev2,dm1),"unsubscribed desktop viewer must suppress subscribed mobile Push");
    m.updateActiveView(a,"d_not_subscribed_12345","pv_client_999999",false,"","");
    chk(!suppress(m,dev2,dm1),"closing unsubscribed desktop view should restore mobile Push");
    Account b=new Account(); b.uuid="user-b";
    m.updateActiveView(b,"d_other_user_123456","pv_client_other12",true,"dm-1","");
    chk(!suppress(m,dev2,dm1),"another account must never suppress this account's Push");
    System.out.println("WEB_PUSH_ACTIVE_VIEW_PASS 19");
  }
}
