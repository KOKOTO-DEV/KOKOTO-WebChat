/* KWC 파일 안내 / KWC file guide
 * AdapterFilesystemHarness는 validation 모듈의 KWC 구현 파일이다. 클래스 이름이 나타내는 책임을 이 파일 안에 한정해 다른 계층과의 결합을 줄인다.
 * AdapterFilesystemHarness is a KWC implementation file in the validation module. Keep the responsibility implied by the class name localized here to reduce cross-layer coupling.
 *
 * 변경 시 호출자와 반환값뿐 아니라 인증/권한, thread context, persistence, multi-loader 호환성에 미치는 영향을 함께 확인한다.
 * When changing it, review not only callers/returns but also effects on authorization, thread context, persistence, and multi-loader compatibility.
 */
import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.CoreLogger;
import dev.kokoto.webchat.adapter.bluemap.BlueMapAdapter;
import dev.kokoto.webchat.adapter.bluemap.BlueMapAdapterHost;
import dev.kokoto.webchat.adapter.squaremap.SquaremapAdapter;
import dev.kokoto.webchat.adapter.squaremap.SquaremapAdapterHost;
import dev.kokoto.webchat.adapter.dynmap.DynmapAdapter;
import dev.kokoto.webchat.adapter.dynmap.DynmapAdapterHost;
import dev.kokoto.webchat.adapter.pl3xmap.Pl3xMapAdapter;
import dev.kokoto.webchat.adapter.pl3xmap.Pl3xMapAdapterHost;
import dev.kokoto.webchat.adapter.liveatlas.LiveAtlasAdapter;
import dev.kokoto.webchat.adapter.liveatlas.LiveAtlasAdapterHost;
import dev.kokoto.webchat.adapter.unmined.UnminedAdapter;
import dev.kokoto.webchat.adapter.unmined.UnminedAdapterHost;
import dev.kokoto.webchat.adapter.overviewer.OverviewerAdapter;
import dev.kokoto.webchat.adapter.overviewer.OverviewerAdapterHost;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Loader-neutral filesystem adapter acceptance harness. */
public final class AdapterFilesystemHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: AdapterFilesystemHarness <project-root>");
        Path project = Path.of(args[0]).toAbsolutePath().normalize();
        Path temp = Files.createTempDirectory("kwc-adapter-harness-");
        try {
            testBlueMap(project, temp.resolve("bluemap"));
            testSquaremap(project, temp.resolve("squaremap"));
            testDynmap(project, temp.resolve("dynmap"));
            testPl3xMap(project, temp.resolve("pl3xmap"));
            testLiveAtlas(project, temp.resolve("liveatlas"));
            testUnmined(project, temp.resolve("unmined"));
            testOverviewer(project, temp.resolve("overviewer"));
            System.out.println("ADAPTER HARNESS PASS: 7 adapters, " + checks + " assertions");
        } finally {
            deleteTree(temp);
        }
    }

    private static ConfigValues base() {
        ConfigValues c = new ConfigValues();
        c.pluginEnabled = true;
        c.httpHost = "127.0.0.1";
        c.httpPort = 18899;
        c.pathPrefix = "/api";
        c.publicPrefix = "";
        return c;
    }

    private static void testBlueMap(Path project, Path base) throws Exception {
        Files.createDirectories(base);
        Path root = base.resolve("web");
        Path conf = base.resolve("webapp.conf");
        Files.createDirectories(root);
        Files.writeString(conf, "scripts: []\nstyles: []\n", StandardCharsets.UTF_8);
        ConfigValues c = base();
        c.bluemapEnabled = true;
        c.webAutoInstall = true;
        c.webAutoPatch = true;
        c.bluemapWebRoot = root.toString();
        c.bluemapWebappConf = conf.toString();
        c.addonPath = "addons/kokoto-web-chat";
        c.apiBaseUrl = "";
        Host h = new Host(project, base.resolve("data"), c);
        BlueMapAdapter a = new BlueMapAdapter(h);
        a.install();
        Path addon = root.resolve("addons/kokoto-web-chat");
        assets(addon, "bluemap");
        String text = Files.readString(conf);
        check(text.contains("addons/kokoto-web-chat/config.js?v="), "BlueMap config script entry missing");
        check(text.contains("addons/kokoto-web-chat/chat.js?v="), "BlueMap chat script entry missing");
        check(text.contains("addons/kokoto-web-chat/chat.css?v="), "BlueMap style entry missing");
        new BlueMapAdapter(h).install();
        text = Files.readString(conf);
        check(count(text, "addons/kokoto-web-chat/config.js?v=") == 1, "BlueMap config entry duplicated");
        check(count(text, "addons/kokoto-web-chat/chat.js?v=") == 1, "BlueMap script entry duplicated");
        check(count(text, "addons/kokoto-web-chat/chat.css?v=") == 1, "BlueMap style entry duplicated");

        List<String> scripts = new ArrayList<>(), styles = new ArrayList<>();
        Path apiRoot = base.resolve("api-web"); Files.createDirectories(apiRoot);
        new BlueMapAdapter(h).installApi(apiRoot, new BlueMapAdapter.WebAppRegistration() {
            public void registerScript(String url) { scripts.add(url); }
            public void registerStyle(String url) { styles.add(url); }
        });
        check(scripts.size() == 2 && styles.size() == 1, "BlueMap API registration count mismatch");
        check(scripts.get(0).contains("config.js?v="), "BlueMap API config registration missing");
        assets(apiRoot.resolve("addons/kokoto-web-chat"), "bluemap-api");

        c.bluemapEnabled = false;
        new BlueMapAdapter(h).install();
        check(!Files.exists(addon), "BlueMap disabled assets not removed");
        text = Files.readString(conf);
        check(!text.contains("addons/kokoto-web-chat/"), "BlueMap disabled webapp entries not removed");
        new BlueMapAdapter(h).installApi(apiRoot, new BlueMapAdapter.WebAppRegistration() {
            public void registerScript(String url) { throw new AssertionError("disabled BlueMap API registered script"); }
            public void registerStyle(String url) { throw new AssertionError("disabled BlueMap API registered style"); }
        });
        check(!Files.exists(apiRoot.resolve("addons/kokoto-web-chat")), "BlueMap API disabled assets not removed");
        pass("BlueMap filesystem + WebApp registration");
    }

    private static void testSquaremap(Path project, Path base) throws Exception {
        Path root = webRoot(base, "<html><head></head><body>squaremap</body></html>");
        ConfigValues c = base(); c.squaremapEnabled=true; c.squaremapAutoInstall=true; c.squaremapAutoPatchIndex=true;
        c.squaremapWebRoot=root.toString(); c.squaremapAddonPath="kokoto-web-chat"; c.squaremapApiBaseUrl="";
        Host h=new Host(project,base.resolve("data"),c);
        new SquaremapAdapter(h).install();
        assertHtmlAdapter(root,"squaremap","<!-- KWC squaremap adapter:start -->");
        new SquaremapAdapter(h).install();
        assertSingleMarker(root,"<!-- KWC squaremap adapter:start -->");
        c.squaremapEnabled=false; new SquaremapAdapter(h).install();
        assertUninstalled(root,"<!-- KWC squaremap adapter:start -->");
        pass("squaremap install/idempotency/uninstall");
    }

    private static void testDynmap(Path project, Path base) throws Exception {
        Path root = webRoot(base, "<html><head></head><body>dynmap</body></html>");
        ConfigValues c=base(); c.dynmapEnabled=true; c.dynmapAutoInstall=true; c.dynmapAutoPatchIndex=true;
        c.dynmapWebRoot=root.toString(); c.dynmapAddonPath="kokoto-web-chat"; c.dynmapApiBaseUrl="";
        Host h=new Host(project,base.resolve("data"),c);
        new DynmapAdapter(h).install(); assertHtmlAdapter(root,"dynmap","<!-- KWC dynmap adapter:start -->");
        new DynmapAdapter(h).install(); assertSingleMarker(root,"<!-- KWC dynmap adapter:start -->");
        c.dynmapEnabled=false; new DynmapAdapter(h).install(); assertUninstalled(root,"<!-- KWC dynmap adapter:start -->");
        pass("Dynmap install/idempotency/uninstall");
    }

    private static void testPl3xMap(Path project, Path base) throws Exception {
        Path root = webRoot(base, "<html><head></head><body>pl3xmap</body></html>");
        ConfigValues c=base(); c.pl3xmapEnabled=true; c.pl3xmapAutoInstall=true; c.pl3xmapAutoPatchIndex=true;
        c.pl3xmapWebRoot=root.toString(); c.pl3xmapAddonPath="kokoto-web-chat"; c.pl3xmapApiBaseUrl="";
        Host h=new Host(project,base.resolve("data"),c);
        new Pl3xMapAdapter(h).install(); assertHtmlAdapter(root,"pl3xmap","<!-- KWC Pl3xMap adapter:start -->");
        new Pl3xMapAdapter(h).install(); assertSingleMarker(root,"<!-- KWC Pl3xMap adapter:start -->");
        c.pl3xmapEnabled=false; new Pl3xMapAdapter(h).install(); assertUninstalled(root,"<!-- KWC Pl3xMap adapter:start -->");
        pass("Pl3xMap install/idempotency/uninstall");
    }

    private static void testLiveAtlas(Path project, Path base) throws Exception {
        Path root=webRoot(base,"<html><head></head><body><script>window.liveAtlasConfig = {};</script></body></html>");
        ConfigValues c=base(); c.liveAtlasEnabled=true; c.liveAtlasAutoInstall=true; c.liveAtlasAutoPatchIndex=true;
        c.liveAtlasWebRoot=root.toString(); c.liveAtlasAddonPath="kokoto-web-chat"; c.liveAtlasApiBaseUrl="";
        Host h=new Host(project,base.resolve("data"),c);
        new LiveAtlasAdapter(h).install(); assertHtmlAdapter(root,"liveatlas","<!-- KWC liveatlas adapter:start -->");
        new LiveAtlasAdapter(h).install(); assertSingleMarker(root,"<!-- KWC liveatlas adapter:start -->");
        c.liveAtlasEnabled=false; new LiveAtlasAdapter(h).install(); assertUninstalled(root,"<!-- KWC liveatlas adapter:start -->");
        pass("LiveAtlas marker detection/install/idempotency/uninstall");
    }

    private static void testUnmined(Path project, Path base) throws Exception {
        Path root=webRoot(base,"<html><head><script src=\"unmined.map.properties.js\"></script><script src=\"unmined.js\"></script></head><body></body></html>");
        ConfigValues c=base(); c.unminedEnabled=true; c.unminedAutoInstall=true; c.unminedAutoPatchIndex=true;
        c.unminedWebRoot=root.toString(); c.unminedAddonPath="kokoto-web-chat"; c.unminedApiBaseUrl="";
        Host h=new Host(project,base.resolve("data"),c);
        new UnminedAdapter(h).install(); assertHtmlAdapter(root,"unmined","<!-- KWC unmined adapter:start -->");
        new UnminedAdapter(h).install(); assertSingleMarker(root,"<!-- KWC unmined adapter:start -->");
        c.unminedEnabled=false; new UnminedAdapter(h).install(); assertUninstalled(root,"<!-- KWC unmined adapter:start -->");
        pass("uNmINeD marker detection/install/idempotency/uninstall");
    }

    private static void testOverviewer(Path project, Path base) throws Exception {
        Path root=webRoot(base,"<html><head><meta name=\"generator\" content=\"Minecraft-Overviewer\"><script src=\"overviewerConfig.js\"></script><script src=\"overviewer.js\"></script><link rel=\"stylesheet\" href=\"overviewer.css\"></head><body></body></html>");
        Files.writeString(root.resolve("overviewerConfig.js"),"var overviewerConfig = {};\n");
        Files.writeString(root.resolve("overviewer.js"),"var overviewer = {};\n");
        ConfigValues c=base(); c.overviewerEnabled=true; c.overviewerAutoInstall=true; c.overviewerAutoPatchIndex=true;
        c.overviewerWebRoot=root.toString(); c.overviewerAddonPath="kokoto-web-chat"; c.overviewerApiBaseUrl="";
        Host h=new Host(project,base.resolve("data"),c);
        new OverviewerAdapter(h).install(); assertHtmlAdapter(root,"overviewer","<!-- KWC overviewer adapter:start -->");
        new OverviewerAdapter(h).install(); assertSingleMarker(root,"<!-- KWC overviewer adapter:start -->");
        c.overviewerEnabled=false; new OverviewerAdapter(h).install(); assertUninstalled(root,"<!-- KWC overviewer adapter:start -->");
        pass("Overviewer marker detection/install/idempotency/uninstall");
    }

    private static Path webRoot(Path base,String html) throws IOException {
        Path root=base.resolve("web"); Files.createDirectories(root); Files.writeString(root.resolve("index.html"),html,StandardCharsets.UTF_8); return root;
    }
    private static void assertHtmlAdapter(Path root,String adapter,String marker) throws IOException {
        assets(root.resolve("kokoto-web-chat"),adapter);
        String html=Files.readString(root.resolve("index.html"));
        check(html.contains(marker),adapter+" marker missing");
        String cfg=Files.readString(root.resolve("kokoto-web-chat/config.js"));
        check(cfg.contains("mapAdapter: '"+adapter+"'"),adapter+" generated config adapter id missing");
    }
    private static void assertSingleMarker(Path root,String marker) throws IOException {
        check(count(Files.readString(root.resolve("index.html")),marker)==1,"duplicate adapter marker: "+marker);
    }
    private static void assertUninstalled(Path root,String marker) throws IOException {
        check(!Files.exists(root.resolve("kokoto-web-chat")),"disabled adapter assets not removed: "+root);
        check(!Files.readString(root.resolve("index.html")).contains(marker),"disabled adapter marker not removed: "+root);
    }
    private static void assets(Path dir,String label) {
        check(Files.isRegularFile(dir.resolve("chat.js")),label+" chat.js missing");
        check(Files.isRegularFile(dir.resolve("chat.css")),label+" chat.css missing");
        check(Files.isRegularFile(dir.resolve("config.js")),label+" config.js missing");
    }
    private static int count(String text,String needle) { int n=0,p=0; while((p=text.indexOf(needle,p))>=0){n++;p+=needle.length();} return n; }
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static void pass(String label) { System.out.println("PASS: "+label); }
    private static void deleteTree(Path root) throws IOException { if(root==null||!Files.exists(root))return; try(var s=Files.walk(root)){ for(Path p:s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }

    private static final class Host implements BlueMapAdapterHost, SquaremapAdapterHost, DynmapAdapterHost, Pl3xMapAdapterHost, LiveAtlasAdapterHost, UnminedAdapterHost, OverviewerAdapterHost {
        private final Path project, data; private final ConfigValues config;
        Host(Path project,Path data,ConfigValues config) throws IOException { this.project=project; this.data=data; this.config=config; Files.createDirectories(data); }
        public ConfigValues configValues(){return config;}
        public Path dataDirectory(){return data;}
        public String version(){return "5.1.0-test";}
        public CoreLogger logger(){return CoreLogger.of(s->{},s->System.err.println("WARN: "+s));}
        public InputStream resource(String name) {
            List<Path> candidates=List.of(
                project.resolve("kwc-adapter-bluemap/src/main/resources").resolve(name),
                project.resolve("kwc-adapter-squaremap/src/main/resources").resolve(name),
                project.resolve("kwc-adapter-dynmap/src/main/resources").resolve(name),
                project.resolve("kwc-adapter-pl3xmap/src/main/resources").resolve(name),
                project.resolve("kwc-adapter-liveatlas/src/main/resources").resolve(name),
                project.resolve("kwc-adapter-unmined/src/main/resources").resolve(name),
                project.resolve("kwc-adapter-overviewer/src/main/resources").resolve(name)
            );
            for(Path p:candidates) if(Files.isRegularFile(p)) try{return Files.newInputStream(p);}catch(IOException ignored){}
            return null;
        }
    }
}
