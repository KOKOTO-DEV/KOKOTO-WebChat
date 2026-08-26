package dev.kokoto.webchat;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebChatServer {
    private final WebChatHost host;
    private final PlatformAdapter platform;
    private final WebChatStorage storage;
    private final WebChatAuth auth;
    private final CaptchaManager captcha;
    private final WebPushManager webPush;
    private final UserPreferenceStore userPreferences;
    private final AdminDiscordAlertManager adminDiscordAlerts;
    private final RateLimiter rateLimiter = new RateLimiter();
    private static final long STREAM_TICKET_TTL_MILLIS = 30_000L;
    private static final long ADMIN_FILTER_REQUEST_BODY_LIMIT_BYTES = 32L * 1024L * 1024L;
    private final ConcurrentHashMap<String, StreamTicket> streamTickets = new ConcurrentHashMap<>();
    // HttpExchange attributes are backed by the HttpContext attribute map in the
    // JDK HTTP server and can therefore survive into later requests on the same
    // context. Never cache request bodies with ex.setAttribute(). Keep a weak map
    // keyed by the actual exchange object instead so one POST body cannot be reused
    // by the next POST to the same endpoint.
    private final Map<HttpExchange, Map<String,String>> parsedBodyByExchange =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Object uploadQuotaLock = new Object();
    private final Deque<ChatMessage> history = new ArrayDeque<>();
    private final ConcurrentHashMap<String, CachedReplyTarget> transientReplyTargets = new ConcurrentHashMap<>();
    private SqliteHistoryStore sqliteHistory;
    private final SseHub sseHub = new SseHub();
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)((?:https?://|www\\.)[^\\s<>\"]+)");
    // Keep this aligned with the frontend customEmojiTokenRegex().
    // Emoji ids may contain spaces and pack paths, for example :pack 1/name:.
    // The negative lookbehind prevents URL schemes such as http:// from being treated as emoji tokens.
    private static final Pattern EMOJI_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9+.-]):(?:emoji:)?([^:\\r\\n]{1,200}):");
    private static final Set<String> DANGEROUS_UPLOAD_EXTENSIONS = Set.of(
            "exe", "msi", "bat", "cmd", "com", "scr", "ps1", "vbs",
            "sh", "bash", "jar", "war", "class",
            "php", "phtml", "asp", "aspx", "jsp",
            "html", "htm", "js", "mjs", "css", "svg"
    );

    private CoreHttpServer httpServer;
    private ExecutorService historyExecutor;
    private volatile long lastSqlitePruneAt;
    private int sqliteWritesSincePrune;
    private volatile boolean running;
    private static volatile boolean imageIoPluginsRegistered;
    private volatile Map<String, String> imageEmojiRuntimeSymbols = Map.of();
    private volatile long imageEmojiRuntimeSymbolsLoadedAt;
    private final java.util.concurrent.atomic.AtomicBoolean imageEmojiRuntimeRefreshScheduled = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final ContentFilterEngine contentFilterEngine = new ContentFilterEngine();
    private volatile EmojiCatalog cachedEmojiCatalog;
    private volatile String cachedEmojiCatalogSignature = "";
    private volatile long cachedEmojiCatalogStamp = Long.MIN_VALUE;
    private volatile Set<String> cachedContentFilterEmojiAliases = Set.of();

    private record StreamTicket(String sessionToken, String clientIp, long expiresAt) {}

    public WebChatServer(WebChatHost host) {
        this.host = java.util.Objects.requireNonNull(host, "host");
        this.platform = java.util.Objects.requireNonNull(host.platformAdapter(), "platformAdapter");
        this.storage = java.util.Objects.requireNonNull(host.storage(), "storage");
        this.auth = java.util.Objects.requireNonNull(host.auth(), "auth");
        this.captcha = java.util.Objects.requireNonNull(host.captcha(), "captcha");
        this.webPush = new WebPushManager(java.util.Objects.requireNonNull(host.webPushHost(), "webPushHost"));
        this.userPreferences = new UserPreferenceStore(host.dataDirectory(), host.logger());
        this.adminDiscordAlerts = new AdminDiscordAlertManager(host);
    }

    public void start() throws IOException {
        ConfigValues config = host.configValues();
        int configuredSseCapacity = config.maxSseConnectionsTotal > 0 ? config.maxSseConnectionsTotal : 200;
        int httpThreadCap = Math.max(64, Math.min(512, configuredSseCapacity + 64));
        httpServer = new CoreHttpServer(config.httpHost, config.httpPort, "KOKOTO WebChat-HTTP", httpThreadCap);
        historyExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "KOKOTO WebChat-History");
            t.setDaemon(true);
            return t;
        });
        if (config.standaloneWebEnabled) {
            for (String standalonePath : standaloneContextPaths(config)) {
                httpServer.createContext(standalonePath, this::handleStandaloneWeb);
            }
        }
        for (String apiPrefix : apiContextPrefixes(config)) {
            createApiContexts(apiPrefix);
        }

        webPush.start();

        initializeHistoryStorage();
        loadPersistedHistory();
        rememberRelayedPlayerIdentitiesFromHistory();
        cleanupOldUploads();
        cleanupOldExternalMediaCache();
        ensureImageIoPluginsRegistered();
        syncExistingGameEmojiPngSidecarsOnStartup();
        refreshImageEmojiRuntimeSymbols();
        // Load the authoritative content-filter.rules block from config.yml through
        // the loader-neutral reader before the server starts accepting traffic.
        refreshContentFilterRulesFromDisk();

        running = true;
        httpServer.start();
        host.logger().info("HTTP chat server started on " + config.httpHost + ":" + config.httpPort + config.pathPrefix);
    }

    private void createApiContexts(String p) {
        httpServer.createContext(p + "/config", this::handleConfig);
        httpServer.createContext(p + "/lang", this::handleLang);
        httpServer.createContext(p + "/history", this::handleHistory);
        httpServer.createContext(p + "/history/around", this::handleHistoryAround);
        httpServer.createContext(p + "/history/search", this::handleHistorySearch);
        httpServer.createContext(p + "/pins", this::handlePins);
        httpServer.createContext(p + "/stream", this::handleStream);
        httpServer.createContext(p + "/stream-ticket", this::handleStreamTicket);
        httpServer.createContext(p + "/send", this::handleSend);
        httpServer.createContext(p + "/relay/receive", this::handleRelayReceive);
        httpServer.createContext(p + "/relay/dm/receive", this::handleRelayDirectMessageReceive);
        httpServer.createContext(p + "/relay/dm/read", this::handleRelayDirectMessageRead);
        httpServer.createContext(p + "/push/subscribe", this::handlePushSubscribe);
        httpServer.createContext(p + "/push/unsubscribe", this::handlePushUnsubscribe);
        httpServer.createContext(p + "/push/test", this::handlePushTest);
        httpServer.createContext(p + "/push/sw.js", this::handlePushServiceWorker);
        httpServer.createContext(p + "/preferences/profiles", this::handleUserProfiles);
        httpServer.createContext(p + "/preferences/profile/save", this::handleUserProfileSave);
        httpServer.createContext(p + "/preferences/profile/delete", this::handleUserProfileDelete);
        httpServer.createContext(p + "/preferences/profile/export", this::handleUserProfileExport);
        httpServer.createContext(p + "/preferences/profile/import", this::handleUserProfileImport);
        httpServer.createContext(p + "/preferences/notifications", this::handleUserNotificationPreferences);
        httpServer.createContext(p + "/dm/threads", this::handleDmThreads);
        httpServer.createContext(p + "/dm/messages", this::handleDmMessages);
        httpServer.createContext(p + "/dm/players", this::handleDmPlayers);
        httpServer.createContext(p + "/dm/send", this::handleDmSend);
        httpServer.createContext(p + "/dm/retry", this::handleDmRetry);
        httpServer.createContext(p + "/dm/read", this::handleDmRead);
        httpServer.createContext(p + "/dm/hide-message", this::handleDmHideMessage);
        httpServer.createContext(p + "/admin/dm/messages", this::handleAdminDmMessages);
        httpServer.createContext(p + "/group/rooms", this::handleGroupRooms);
        httpServer.createContext(p + "/group/players", this::handleGroupPlayers);
        httpServer.createContext(p + "/group/messages", this::handleGroupMessages);
        httpServer.createContext(p + "/admin/group/messages", this::handleAdminGroupMessages);
        httpServer.createContext(p + "/group/create", this::handleGroupCreate);
        httpServer.createContext(p + "/group/join", this::handleGroupJoin);
        httpServer.createContext(p + "/group/leave", this::handleGroupLeave);
        httpServer.createContext(p + "/group/invite", this::handleGroupInvite);
        httpServer.createContext(p + "/group/invites", this::handleGroupInvites);
        httpServer.createContext(p + "/group/invite/respond", this::handleGroupInviteRespond);
        httpServer.createContext(p + "/group/send", this::handleGroupSend);
        httpServer.createContext(p + "/group/read", this::handleGroupRead);
        httpServer.createContext(p + "/group/hide-message", this::handleGroupHideMessage);
        httpServer.createContext(p + "/group/settings", this::handleGroupSettings);
        httpServer.createContext(p + "/group/members", this::handleGroupMembers);
        httpServer.createContext(p + "/group/kick", this::handleGroupKick);
        httpServer.createContext(p + "/group/ban", this::handleGroupBan);
        httpServer.createContext(p + "/group/unban", this::handleGroupUnban);
        httpServer.createContext(p + "/group/hide-room", this::handleGroupHideRoom);
        httpServer.createContext(p + "/group/unhide-room", this::handleGroupUnhideRoom);
        httpServer.createContext(p + "/group/transfer-owner", this::handleGroupTransferOwner);
        httpServer.createContext(p + "/commands", this::handleCommands);
        httpServer.createContext(p + "/commands/run", this::handleCommandRun);
        httpServer.createContext(p + "/upload", this::handleUpload);
        httpServer.createContext(p + "/emojis", this::handleEmojis);
        httpServer.createContext(p + "/e", this::handleShortEmoji);
        httpServer.createContext(p + "/uploads", this::handleUploadedFile);
        httpServer.createContext(p + "/fonts", this::handleFontFile);
        httpServer.createContext(p + "/external-media", this::handleExternalMedia);
        httpServer.createContext(p + "/captcha", this::handleCaptcha);
        httpServer.createContext(p + "/auth/code", this::handleAuthCode);
        httpServer.createContext(p + "/auth/status", this::handleAuthStatus);
        httpServer.createContext(p + "/auth/login", this::handleAuthLogin);
        httpServer.createContext(p + "/auth/set-password", this::handleSetPassword);
        httpServer.createContext(p + "/auth/me", this::handleMe);
        httpServer.createContext(p + "/auth/logout", this::handleLogout);
        httpServer.createContext(p + "/admin/summary", this::handleAdminSummary);
        httpServer.createContext(p + "/admin/online", this::handleAdminOnline);
        httpServer.createContext(p + "/admin/sessions", this::handleAdminSessions);
        httpServer.createContext(p + "/admin/accounts", this::handleAdminAccounts);
        httpServer.createContext(p + "/admin/revoke", this::handleAdminRevoke);
        httpServer.createContext(p + "/admin/mutes", this::handleAdminMutes);
        httpServer.createContext(p + "/admin/mute", this::handleAdminMute);
        httpServer.createContext(p + "/admin/unmute", this::handleAdminUnmute);
        httpServer.createContext(p + "/admin/delete-message", this::handleAdminDeleteMessage);
        httpServer.createContext(p + "/admin/delete-dm-thread", this::handleAdminDeleteDmThread);
        httpServer.createContext(p + "/admin/delete-group-room", this::handleAdminDeleteGroupRoom);
        httpServer.createContext(p + "/admin/session-flags", this::handleAdminSessionFlags);
        httpServer.createContext(p + "/admin/cleanup-preview", this::handleAdminCleanupPreview);
        httpServer.createContext(p + "/admin/pin-message", this::handleAdminPinMessage);
        httpServer.createContext(p + "/admin/unpin-message", this::handleAdminUnpinMessage);
        httpServer.createContext(p + "/admin/move-pin", this::handleAdminMovePin);
        httpServer.createContext(p + "/admin/clear-history", this::handleAdminClearHistory);
        httpServer.createContext(p + "/admin/emojis", this::handleAdminEmojis);
        httpServer.createContext(p + "/admin/emojis/create-pack", this::handleAdminEmojiCreatePack);
        httpServer.createContext(p + "/admin/emojis/upload", this::handleAdminEmojiUpload);
        httpServer.createContext(p + "/admin/emojis/delete", this::handleAdminEmojiDelete);
        httpServer.createContext(p + "/admin/emojis/rename", this::handleAdminEmojiRename);
        httpServer.createContext(p + "/admin/emojis/move", this::handleAdminEmojiMove);
        httpServer.createContext(p + "/admin/settings", this::handleAdminSettings);
        httpServer.createContext(p + "/admin/filter", this::handleAdminFilter);
        httpServer.createContext(p + "/admin/filter/rules", this::handleAdminFilterRules);
        httpServer.createContext(p + "/admin/filter/lists", this::handleAdminFilterLists);
        httpServer.createContext(p + "/admin/filter/test", this::handleAdminFilterTest);
    }

    private Set<String> apiContextPrefixes(ConfigValues config) {
        Set<String> out = new LinkedHashSet<>();
        out.add(normalizeContextPrefix(config.pathPrefix, "/api"));
        return out;
    }

    private Set<String> standaloneContextPaths(ConfigValues config) {
        Set<String> out = new LinkedHashSet<>();
        out.add(normalizeStandaloneContextPath(config.standaloneWebPath, "/"));
        return out;
    }

    private String matchingStandaloneContextPath(ConfigValues config, String path) {
        String best = "";
        for (String base : standaloneContextPaths(config)) {
            boolean matches = "/".equals(base) || path.equals(base) || path.startsWith(base + "/");
            if (matches && base.length() > best.length()) best = base;
        }
        return best;
    }

    private String matchingApiContextPrefix(ConfigValues config, String path) {
        String best = "";
        for (String prefix : apiContextPrefixes(config)) {
            if ((path.equals(prefix) || path.startsWith(prefix + "/")) && prefix.length() > best.length()) {
                best = prefix;
            }
        }
        return best.isBlank() ? normalizeContextPrefix(config.pathPrefix, "/api") : best;
    }

    private String stripKnownResourceSuffix(String path) {
        String out = trimTrailingSlash(String.valueOf(path == null ? "" : path));
        for (String suffix : new String[]{"/uploads", "/emojis", "/external-media", "/fonts"}) {
            if (out.equals(suffix) || out.endsWith(suffix)) {
                return trimTrailingSlash(out.substring(0, out.length() - suffix.length()));
            }
        }
        return out;
    }

    private String normalizeContextPrefix(String value, String fallback) {
        String out = String.valueOf(value == null || value.isBlank() ? fallback : value).trim();
        if (!out.startsWith("/")) out = "/" + out;
        out = trimTrailingSlash(out);
        return out.isBlank() ? fallback : out;
    }

    private String normalizeStandaloneContextPath(String value, String fallback) {
        String out = String.valueOf(value == null || value.isBlank() ? fallback : value).trim();
        if (!out.startsWith("/")) out = "/" + out;
        if ("/".equals(out)) return "/";
        out = trimTrailingSlash(out);
        return out.isBlank() ? "/" : out;
    }

    public void stop() {
        savePersistedHistory();
        if (historyExecutor != null) {
            historyExecutor.shutdown();
            try {
                if (!historyExecutor.awaitTermination(5, TimeUnit.SECONDS)) historyExecutor.shutdownNow();
            } catch (InterruptedException ex) {
                historyExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            historyExecutor = null;
        }
        if (sqliteHistory != null) {
            sqliteHistory.close();
            sqliteHistory = null;
        }
        running = false;
        sseHub.close();
        if (httpServer != null) {
            httpServer.close(1);
            httpServer = null;
        }
    }

    private void handleStandaloneWeb(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendBytes(ex, 405, "text/plain; charset=utf-8", "method_not_allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }

        ConfigValues config = host.configValues();
        String path = ex.getRequestURI().getPath();
        String base = matchingStandaloneContextPath(config, path);
        if (base.isBlank()) {
            sendBytes(ex, 404, "text/plain; charset=utf-8", "not_found".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String suffix = "/".equals(base) ? path : (path.equals(base) ? "" : path.substring(base.length()));
        if (suffix.isEmpty() || "/".equals(suffix)) {
            sendStandaloneIndex(ex, config, base);
        } else if ("/chat.js".equals(suffix)) {
            sendClasspathResource(ex, "standalone/chat.js", "application/javascript; charset=utf-8");
        } else if ("/chat.css".equals(suffix)) {
            sendClasspathResource(ex, "standalone/chat.css", "text/css; charset=utf-8");
        } else if ("/manifest.webmanifest".equals(suffix)) {
            sendStandaloneManifest(ex);
        } else {
            sendBytes(ex, 404, "text/plain; charset=utf-8", "not_found".getBytes(StandardCharsets.UTF_8));
        }
    }


    private String configuredStandaloneAppName() {
        ConfigValues c = host.configValues();
        String value = c == null ? "" : c.standaloneWebAppName;
        value = stripControl(String.valueOf(value == null ? "" : value), 80);
        return value == null || value.isBlank() ? "Web Chat" : value;
    }

    private String configuredStandaloneAppShortName() {
        ConfigValues c = host.configValues();
        String value = c == null ? "" : c.standaloneWebAppShortName;
        value = stripControl(String.valueOf(value == null ? "" : value), 40);
        if (value == null || value.isBlank()) value = configuredStandaloneAppName();
        return value;
    }

    private String configuredWebPushTitle() {
        ConfigValues c = host.configValues();
        String value = c == null ? "" : c.webPushNotificationTitle;
        value = stripControl(String.valueOf(value == null ? "" : value), 80);
        return value == null || value.isBlank() ? configuredStandaloneAppName() : value;
    }

    private void sendStandaloneIndex(HttpExchange ex, ConfigValues config, String standaloneBasePath) throws IOException {
        String version = host.version();
        String apiBase = config.standaloneWebApiBaseUrl == null ? "" : config.standaloneWebApiBaseUrl.trim();
        String apiBaseJs;
        if (!apiBase.isEmpty()) {
            String resolved = stripKnownResourceSuffix(normalizePublicBaseUrl(ex, apiBase));
            apiBaseJs = JsonUtil.quote(resolved);
        } else {
            String internalApi = normalizeContextPrefix(config.pathPrefix, "/api");
            String publicApi = joinPublicPath(config.publicPrefix, internalApi);
            String publicPrefix = normalizePublicPrefix(config.publicPrefix);
            apiBaseJs = "(function(){"
                    + "var path=String(location.pathname||'').replace(/\\/+$/,'')||'/';"
                    + "var prefix=" + JsonUtil.quote(publicPrefix) + ";"
                    + "if(prefix&&(path===prefix||path.indexOf(prefix+'/')===0))return location.origin+" + JsonUtil.quote(publicApi) + ";"
                    + "return location.origin+" + JsonUtil.quote(internalApi) + ";"
                    + "})()";
        }

        String base = trimTrailingSlash(standaloneBasePath);
        if (base.isBlank()) base = "/";
        String appName = configuredStandaloneAppName();
        String appShortName = configuredStandaloneAppShortName();
        String standaloneScript = readClasspathUtf8("standalone/chat.js");
        if (standaloneScript == null) {
            sendBytes(ex, 500, "text/plain; charset=utf-8", "standalone_frontend_missing".getBytes(StandardCharsets.UTF_8));
            return;
        }
        // Embed the frontend bootstrap in the HTML so the standalone entry does not
        // depend on a second static-resource route through the reverse proxy. The
        // optional /chat.js and /chat.css resources remain available on the internal
        // standalone route for diagnostics.
        standaloneScript = standaloneScript.replace("</script", "<\\/script").replace("</SCRIPT", "<\\/SCRIPT");

        // Keep PWA metadata self-contained and derive start_url/scope from the same
        // internal/public route split used by the standalone page.
        String manifestBase = isForwardedRequest(ex)
                ? joinPublicPath(config.publicPrefix, normalizeStandaloneContextPath(config.standaloneWebPath, "/"))
                : normalizeStandaloneContextPath(config.standaloneWebPath, "/");
        Map<String, Object> inlineManifest = new LinkedHashMap<>();
        inlineManifest.put("name", appName);
        inlineManifest.put("short_name", appShortName);
        inlineManifest.put("display", "standalone");
        inlineManifest.put("start_url", manifestBase);
        inlineManifest.put("scope", manifestBase);
        inlineManifest.put("background_color", "#111318");
        inlineManifest.put("theme_color", "#111318");
        String manifestJson = JsonUtil.obj(inlineManifest);
        String manifestHref = "data:application/manifest+json;base64,"
                + Base64.getEncoder().encodeToString(manifestJson.getBytes(StandardCharsets.UTF_8));
        String faviconHref = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Crect width='64' height='64' rx='12' fill='%23111318'/%3E%3Cpath d='M14 16h36v24H30L20 50V40h-6z' fill='%23f2f4f8'/%3E%3C/svg%3E";

        String html = "<!doctype html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
                + "  <title>" + htmlEsc(appName) + "</title>\n"
                + "  <meta name=\"theme-color\" content=\"#111318\">\n"
                + "  <link rel=\"manifest\" href=\"" + htmlEsc(manifestHref) + "\">\n"
                + "  <link rel=\"icon\" href=\"" + htmlEsc(faviconHref) + "\">\n"
                + "  <style>html,body{margin:0;width:100%;height:100%;height:100dvh;background:#111318;color:#eee;font-family:system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif;overflow:hidden;} .kwc-standalone-note{position:fixed;left:14px;bottom:12px;opacity:.55;font-size:12px;pointer-events:none;}</style>\n"
                + "</head>\n"
                + "<body data-kwc-version=\"" + htmlEsc(version) + "\">\n"
                + "  <noscript>JavaScript is required to use the web chat.</noscript>\n"
                + "  <div class=\"kwc-standalone-note\">" + htmlEsc(appName) + " standalone mode</div>\n"
                + "  <script>window.KokotoWebChatConfig={apiBase:" + apiBaseJs + ",apiBaseUrl:" + apiBaseJs + ",standalone:true,standalonePath:" + JsonUtil.quote(config.standaloneWebPath) + ",standalonePublicUrl:" + JsonUtil.quote(publicStandaloneOpenUrl(ex)) + "};</script>\n"
                + "  <script data-kwc-standalone-inline=\"" + htmlEsc(version) + "\">\n" + standaloneScript + "\n  </script>\n"
                + "</body>\n"
                + "</html>\n";
        sendBytes(ex, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }


    private void sendStandaloneManifest(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String suffix = "/manifest.webmanifest";
        String base = path != null && path.endsWith(suffix)
                ? path.substring(0, path.length() - suffix.length())
                : ".";
        if (base.isBlank()) base = "/";
        Map<String, Object> m = new LinkedHashMap<>();
        String appName = configuredStandaloneAppName();
        String appShortName = configuredStandaloneAppShortName();
        m.put("name", appName);
        m.put("short_name", appShortName);
        m.put("display", "standalone");
        m.put("start_url", base);
        m.put("scope", base);
        m.put("background_color", "#111318");
        m.put("theme_color", "#111318");
        sendBytes(ex, 200, "application/manifest+json; charset=utf-8", JsonUtil.obj(m).getBytes(StandardCharsets.UTF_8));
    }

    private void handlePushServiceWorker(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendBytes(ex, 405, "text/plain; charset=utf-8", "method_not_allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String js = "self.addEventListener('push',function(event){"
                + "var data={};try{data=event.data?event.data.json():{};}catch(e){data={body:event.data?event.data.text():''};}"
                + "var title=data.title||" + JsonUtil.quote(configuredWebPushTitle()) + ";"
                + "var opts={body:data.body||'',tag:data.tag||'kwc',renotify:true,data:{url:data.url||'/'},timestamp:data.time||Date.now()};"
                + "event.waitUntil(self.registration.showNotification(title,opts));"
                + "});"
                + "self.addEventListener('notificationclick',function(event){"
                + "event.notification.close();var url=(event.notification.data&&event.notification.data.url)||'/';"
                + "event.waitUntil(clients.matchAll({type:'window',includeUncontrolled:true}).then(function(list){"
                + "var target;try{target=new URL(url,self.location.origin);}catch(e){target=new URL('/',self.location.origin);}"
                + "var samePath=null,sameOrigin=null;"
                + "for(var i=0;i<list.length;i++){var c=list[i];try{var cu=new URL(c.url);if(cu.origin!==target.origin)continue;if(cu.pathname===target.pathname&&!samePath)samePath=c;if(!sameOrigin)sameOrigin=c;}catch(e){}}"
                + "var chosen=samePath||sameOrigin;if(chosen){try{chosen.postMessage({source:'KWC',type:'notificationNavigate',url:target.href});}catch(e){}return chosen.focus();}"
                + "return clients.openWindow(target.href);" 
                + "}));"
                + "});";
        ex.getResponseHeaders().set("Service-Worker-Allowed", "/");
        ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        sendBytes(ex, 200, "application/javascript; charset=utf-8", js.getBytes(StandardCharsets.UTF_8));
    }

    private void handlePushSubscribe(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        ConfigValues config = host.configValues();
        if (config == null || !config.webPushEnabled) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"web_push_disabled\"}"); return; }
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        if (ctx == null || ctx.account == null || ctx.account.uuid == null || ctx.account.uuid.isBlank()) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"not_logged_in\"}"); return; }
        String userAgent = ex.getRequestHeaders().getFirst("User-Agent");
        boolean ok = webPush.subscribe(ctx.account, body, userAgent);
        sendJson(ex, ok ? 200 : 400, "{\"ok\":" + ok + (ok ? "" : ",\"error\":\"invalid_subscription\"") + "}");
    }

    private void handlePushUnsubscribe(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        if (ctx == null || ctx.account == null) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"not_logged_in\"}"); return; }
        boolean ok = webPush.unsubscribe(ctx.account, body.get("endpoint"), body.get("deviceId"), Boolean.parseBoolean(String.valueOf(body.getOrDefault("clearLegacy", "false"))));
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }

    private void handlePushTest(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        ConfigValues config = host.configValues();
        if (config == null || !config.webPushEnabled) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"web_push_disabled\"}"); return; }
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        if (ctx == null || ctx.account == null || ctx.account.uuid == null || ctx.account.uuid.isBlank()) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"not_logged_in\"}"); return; }
        WebPushManager.Payload p = new WebPushManager.Payload();
        p.type = "test";
        p.title = configuredWebPushTitle();
        p.body = "";
        p.url = "";
        p.tag = "kwc-test";
        p.senderUuid = "";
        p.targetDeviceId = body.getOrDefault("deviceId", "");
        webPush.sendToUser(ctx.account.uuid, p);
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private void handleUserProfiles(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        SessionContext ctx = requireUserSession(ex);
        if (ctx == null) return;
        ConfigValues c = host.configValues();
        Map<String,Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("enabled", c.uiUserProfilesEnabled && c.uiUserProfilesMaxProfiles > 0);
        res.put("maxProfiles", c.uiUserProfilesMaxProfiles);
        res.put("allowImportExport", c.uiUserProfilesAllowImportExport);
        res.put("profiles", (c.uiUserProfilesEnabled && c.uiUserProfilesMaxProfiles > 0)
                ? userPreferences.listProfiles(ctx.account, c.uiUserProfilesMaxProfiles) : List.of());
        sendJson(ex, 200, JsonUtil.obj(res));
    }

    private void handleUserProfileSave(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        SessionContext ctx = requireUserSession(ex);
        if (ctx == null) return;
        ConfigValues c = host.configValues();
        if (!c.uiUserProfilesEnabled || c.uiUserProfilesMaxProfiles <= 0) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"profiles_disabled\"}"); return; }
        Map<String,String> body = parsedBody(ex);
        UserPreferenceStore.SaveResult result = userPreferences.saveProfile(ctx.account, body.get("id"), body.get("name"), body, c.uiUserProfilesMaxProfiles);
        if (!result.ok()) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error()) + "}"); return; }
        audit(ctx, "preferences.profile-save", Map.of("id", String.valueOf(result.value().getOrDefault("id", "")), "name", String.valueOf(result.value().getOrDefault("name", ""))));
        sendJson(ex, 200, JsonUtil.obj(Map.of("ok", true, "profile", result.value())));
    }

    private void handleUserProfileDelete(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        SessionContext ctx = requireUserSession(ex);
        if (ctx == null) return;
        Map<String,String> body = parsedBody(ex);
        boolean removed = userPreferences.deleteProfile(ctx.account, body.get("id"));
        if (!removed) { sendJson(ex, 404, "{\"ok\":false,\"error\":\"profile_not_found\"}"); return; }
        audit(ctx, "preferences.profile-delete", Map.of("id", String.valueOf(body.getOrDefault("id", ""))));
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private void handleUserProfileExport(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        SessionContext ctx = requireUserSession(ex);
        if (ctx == null) return;
        ConfigValues c = host.configValues();
        if (!c.uiUserProfilesEnabled || !c.uiUserProfilesAllowImportExport) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"profile_export_disabled\"}"); return; }
        Map<String,String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        UserPreferenceStore.ExportResult result = userPreferences.exportProfile(ctx.account, q.get("id"));
        if (!result.ok()) { sendJson(ex, 404, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error()) + "}"); return; }
        sendJson(ex, 200, JsonUtil.obj(Map.of("ok", true, "json", result.json())));
    }

    private void handleUserProfileImport(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        SessionContext ctx = requireUserSession(ex);
        if (ctx == null) return;
        ConfigValues c = host.configValues();
        if (!c.uiUserProfilesEnabled || c.uiUserProfilesMaxProfiles <= 0 || !c.uiUserProfilesAllowImportExport) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"profile_import_disabled\"}"); return; }
        Map<String,String> body = parsedBody(ex);
        UserPreferenceStore.SaveResult result = userPreferences.importProfile(ctx.account, body.get("profileJson"), c.uiUserProfilesMaxProfiles);
        if (!result.ok()) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error()) + "}"); return; }
        audit(ctx, "preferences.profile-import", Map.of("id", String.valueOf(result.value().getOrDefault("id", "")), "name", String.valueOf(result.value().getOrDefault("name", ""))));
        sendJson(ex, 200, JsonUtil.obj(Map.of("ok", true, "profile", result.value())));
    }

    private void handleUserNotificationPreferences(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireUserSession(ex);
        if (ctx == null) return;
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 200, JsonUtil.obj(Map.of("ok", true, "preferences", userPreferences.notificationPreferences(ctx.account, host.configValues()))));
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        Map<String,String> body = parsedBody(ex);
        UserPreferenceStore.SaveResult result = userPreferences.saveNotificationPreferences(ctx.account, body, host.configValues());
        if (!result.ok()) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error()) + "}"); return; }
        webPush.applyAccountPreferences(ctx.account, result.value());
        sendJson(ex, 200, JsonUtil.obj(Map.of("ok", true, "preferences", result.value())));
    }

    private String readClasspathUtf8(String resource) throws IOException {
        try (InputStream in = host.resource(resource)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendClasspathResource(HttpExchange ex, String resource, String contentType) throws IOException {
        try (InputStream in = host.resource(resource)) {
            if (in == null) {
                sendBytes(ex, 404, "text/plain; charset=utf-8", "not_found".getBytes(StandardCharsets.UTF_8));
                return;
            }
            byte[] data = in.readAllBytes();
            sendBytes(ex, 200, contentType, data);
        }
    }

    public ChatMessage publishFromGame(String player, String message) {
        return publishFromGame(player, "", "", message);
    }

    public ChatMessage publishFromGame(String player, String realPlayerName, String playerUuid, String message) {
        if (playerUuid != null && !playerUuid.isBlank() && player != null && !player.isBlank()) {
            storage.updateLastDisplayName(playerUuid, realPlayerName, player);
        }
        ConfigValues config = host.configValues();
        String rawText = stripChatMessage(message, config);
        ContentFilterResult filtered = filterContent(rawText, ContentFilterEngine.Scope.PUBLIC);
        if (filtered.blocked) return null;
        rawText = filtered.message;
        String text = canonicalizeKnownEmojiTokens(host.applyMessageTokens(rawText), config);
        String gameText = host.applyMessageTokensForGame(rawText);
        if (text.isBlank()) return null;
        if (host.discord() != null && host.discord().shouldSuppressGameEcho(player, text)) {
            return null;
        }
        prewarmExternalMediaCache(text);
        ChatMessage msg = new ChatMessage(System.currentTimeMillis(), "game", player, "USER", text)
                .withGameMessage(gameText)
                .withRealSender(stripControl(realPlayerName, 64), stripControl(playerUuid, 64));
        prepareServerRelay(msg);
        addHistory(msg);
        broadcast(msg);
        dispatchWebPushChat(msg);
        adminDiscordAlerts.inspect(msg, AdminDiscordAlertManager.Scope.PUBLIC);
        if (host.discord() != null) {
            host.discord().sendGameMessage(msg);
        }
        publishServerRelay(msg);
        return msg;
    }

    public ChatMessage publishReplyFromGame(String displayName, String realName, String playerUuid,
                                            String replyToId, String message, String gameDisplayMessage) {
        if (displayName == null || displayName.isBlank()) return null;
        ConfigValues config = host.configValues();
        String rawText = stripChatMessage(message, config);
        ContentFilterResult filtered = filterContent(rawText, ContentFilterEngine.Scope.PUBLIC);
        if (filtered.blocked) return null;
        rawText = filtered.message;
        String text = canonicalizeKnownEmojiTokens(host.applyMessageTokens(rawText), config);
        if (text.isBlank()) return null;
        String rawGameText = stripChatMessage(gameDisplayMessage, config);
        String gameText = host.applyMessageTokensForGame(rawGameText);
        if (gameText.isBlank()) gameText = host.applyMessageTokensForGame(rawText);

        ChatMessage target = findHistoryMessageById(stripControl(replyToId, 96));
        if (target == null || target.hidden) return null;

        String uuid = String.valueOf(playerUuid == null ? "" : playerUuid);
        if (!uuid.isBlank()) storage.updateLastDisplayName(uuid, realName, displayName);

        prewarmExternalMediaCache(text);
        ChatMessage msg = new ChatMessage(System.currentTimeMillis(), "game", displayName, "USER", text)
                .withGameMessage(gameText)
                .withRealSender(stripControl(realName, 64), stripControl(uuid, 64))
                .withReply(target.id, stripControl(target.sender, 64), messageReplyPreview(target));
        prepareServerRelay(msg);
        addHistory(msg);
        broadcast(msg);
        dispatchWebPushChat(msg);
        adminDiscordAlerts.inspect(msg, AdminDiscordAlertManager.Scope.PUBLIC);
        if (host.discord() != null) host.discord().sendGameMessage(msg);
        publishServerRelay(msg);
        sendGameCommandReplyToGame(msg, config, gameText);
        return msg;
    }

    public void publishFromDiscord(String sender, String message) {
        ConfigValues config = host.configValues();
        String rawText = stripChatMessage(message, config);
        ContentFilterResult filtered = filterContent(rawText, ContentFilterEngine.Scope.PUBLIC);
        if (filtered.blocked) return;
        rawText = filtered.message;
        String text = host.applyMessageTokens(rawText);
        String gameText = host.applyMessageTokensForGame(rawText);
        String safeSender = stripControl(sender, 64);
        if (text.isBlank()) return;
        if (safeSender.isBlank()) safeSender = "Discord";
        prewarmExternalMediaCache(text);
        ChatMessage msg = new ChatMessage(System.currentTimeMillis(), "discord", safeSender, "DISCORD", text)
                .withGameMessage(gameText);
        prepareServerRelay(msg);
        addHistory(msg);
        broadcast(msg);
        dispatchWebPushChat(msg);
        publishServerRelay(msg);
    }

    public void publishSystemEvent(String sender, String message) {
        publishSystemEvent(sender, message, "", "");
    }

    public void publishSystemEvent(String sender, String message, String i18nKey, String i18nArgsJson) {
        String safeSender = stripControl(sender, 64);
        int eventMax = host.configValues().maxUrlMessageLength > 0 ? Math.max(256, host.configValues().maxUrlMessageLength) : 0;
        String text = stripControl(message, eventMax);
        if (safeSender.isBlank()) safeSender = "Server";
        if (text.isBlank()) return;
        ChatMessage msg = new ChatMessage(System.currentTimeMillis(), "event", safeSender, "SYSTEM", text);
        msg.withI18n(i18nKey, i18nArgsJson);
        prepareServerRelay(msg);
        addHistory(msg);
        broadcast(msg);
        dispatchWebPushChat(msg);
        publishServerRelay(msg);
    }

    private void handleRelayReceive(HttpExchange ex) throws IOException {
        ServerRelay relay = host.serverRelay();
        if (relay == null) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"relay_disabled\"}");
            return;
        }
        relay.handleIncoming(ex);
    }

    private void handleRelayDirectMessageReceive(HttpExchange ex) throws IOException {
        ServerRelay relay = host.serverRelay();
        if (relay == null) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"relay_disabled\"}");
            return;
        }
        relay.handleIncomingDirectMessage(ex);
    }

    private void handleRelayDirectMessageRead(HttpExchange ex) throws IOException {
        ServerRelay relay = host.serverRelay();
        if (relay == null) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"relay_disabled\"}");
            return;
        }
        relay.handleIncomingDirectMessageRead(ex);
    }

    private void handleConfig(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        ConfigValues c = host.configValues();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("serverVersion", host.version());
        m.put("serverRelayServerId", c.serverRelayServerId);
        m.put("serverRelayServerName", c.serverRelayServerName);
        m.put("guestEnabled", c.guestEnabled);
        m.put("guestAllowCustomName", c.guestAllowCustomName);
        m.put("guestNamePrefix", c.guestNamePrefix);
        m.put("hideChatForGuestsWhenGuestDisabled", c.hideChatForGuestsWhenGuestDisabled);
        m.put("uiResizable", c.uiResizable);
        m.put("uiRememberWindowSize", c.uiRememberWindowSize);
        m.put("uiDefaultWidth", c.uiDefaultWidth);
        m.put("uiDefaultHeight", c.uiDefaultHeight);
        m.put("uiMinWidth", c.uiMinWidth);
        m.put("uiMinHeight", c.uiMinHeight);
        m.put("uiMaxWidth", c.uiMaxWidth);
        m.put("uiMaxHeight", c.uiMaxHeight);
        m.put("uiFontSize", c.uiFontSize);
        m.put("uiMessageFontSize", c.uiMessageFontSize);
        m.put("uiInputFontSize", c.uiInputFontSize);
        m.put("uiTextColor", c.uiTextColor);
        m.put("uiUiTextColor", c.uiUiTextColor);
        m.put("uiTextShadowMode", c.uiTextShadowMode);
        m.put("uiTextShadowCustom", c.uiTextShadowCustom);
        m.put("uiInputBackgroundColor", c.uiInputBackgroundColor);
        m.put("uiButtonFontSize", c.uiButtonFontSize);
        m.put("uiBadgeFontSize", c.uiBadgeFontSize);
        m.put("uiVirtualScrollEnabled", c.uiVirtualScrollEnabled);
        m.put("uiVirtualScrollOverscanScreens", c.uiVirtualScrollOverscanScreens);
        m.put("uiVirtualScrollMinRenderedMessages", c.uiVirtualScrollMinRenderedMessages);
        m.put("uiHistoryPreloadScreens", c.uiHistoryPreloadScreens);
        m.put("uiHistoryPreloadMinPx", c.uiHistoryPreloadMinPx);
        m.put("uiAutoFollowBottomThresholdPx", c.uiAutoFollowBottomThresholdPx);
        m.put("uiScrollInteractionIdleMs", c.uiScrollInteractionIdleMs);
        m.put("uiResumeRefreshEnabled", c.uiResumeRefreshEnabled);
        m.put("uiResumeRefreshMinIntervalSeconds", c.uiResumeRefreshMinIntervalSeconds);
        m.put("uiResumeRefreshSkipUnchanged", c.uiResumeRefreshSkipUnchanged);
        m.put("uiTheme", c.uiTheme);
        m.put("uiSyncBlueMapTheme", c.uiSyncBlueMapTheme);
        m.put("uiOpacity", c.uiOpacity);
        m.put("uiUserPreferencesControl", c.uiUserPreferencesControl);
        m.put("uiUserProfilesEnabled", c.uiUserProfilesEnabled);
        m.put("uiUserProfilesMaxProfiles", c.uiUserProfilesMaxProfiles);
        m.put("uiUserProfilesAllowImportExport", c.uiUserProfilesAllowImportExport);
        m.put("uiUserFontOptions", c.uiUserFontOptions);
        m.put("uiFontFamily", c.uiFontFamily);
        m.put("uiPictureInPictureEnabled", c.uiPictureInPictureEnabled);
        m.put("browserNotificationsEnabled", c.browserNotificationsEnabled);
        m.put("browserNotificationsOnlyWhenHidden", c.browserNotificationsOnlyWhenHidden);
        m.put("browserNotificationsNotifyNormalChat", c.browserNotificationsNotifyNormalChat);
        m.put("browserNotificationsNotifyDm", c.browserNotificationsNotifyDm);
        m.put("browserNotificationsNotifyGroupChat", c.browserNotificationsNotifyGroupChat);
        m.put("browserNotificationsNotifyMentions", c.browserNotificationsNotifyMentions);
        m.put("browserNotificationsNotifyReplies", c.browserNotificationsNotifyReplies);
        m.put("browserNotificationsNotifySystem", c.browserNotificationsNotifySystem);
        m.put("browserNotificationsNotifyKeywords", c.browserNotificationsNotifyKeywords);
        m.put("browserNotificationsShowMessagePreview", c.browserNotificationsShowMessagePreview);
        m.put("webPushEnabled", c.webPushEnabled);
        m.put("webPushAvailable", c.webPushEnabled && webPush.available());
        m.put("webPushVapidPublicKey", c.webPushEnabled ? webPush.vapidPublicKey() : "");
        m.put("standaloneWebEnabled", c.standaloneWebEnabled);
        m.put("standaloneWebPath", c.standaloneWebPath);
        m.put("standaloneWebPublicUrl", c.standaloneWebEnabled ? publicStandaloneOpenUrl(ex) : "");
        m.put("standaloneWebAppName", configuredStandaloneAppName());
        m.put("standaloneWebAppShortName", configuredStandaloneAppShortName());
        m.put("webPushNotificationTitle", configuredWebPushTitle());
        m.put("webPushNotifyNormalChat", c.webPushNotifyNormalChat);
        m.put("webPushNotifyDm", c.webPushNotifyDm);
        m.put("webPushNotifyGroupChat", c.webPushNotifyGroupChat);
        m.put("webPushNotifyMentions", c.webPushNotifyMentions);
        m.put("webPushNotifyReplies", c.webPushNotifyReplies);
        m.put("webPushNotifySystem", c.webPushNotifySystem);
        m.put("webPushNotifyKeywords", c.webPushNotifyKeywords);
        m.put("webPushShowMessagePreview", c.webPushShowMessagePreview);
        m.put("playerNameMode", c.playerNameMode);
        m.put("playerNameStripColors", c.playerNameStripColors);
        m.put("webFontsEnabled", c.webFontsEnabled);
        m.put("webFontsItems", c.webFontsItems);
        m.put("captchaMode", c.captchaMode);
        m.put("captchaEnabled", captcha.enabled(c == null ? null : c.captchaMode));
        m.put("captchaRequireOnEachMessage", c.captchaRequireOnEachMessage);
        m.put("captchaPassValidMinutes", c.captchaPassValidMinutes);
        m.put("maxMessageLength", c.maxMessageLength);
        m.put("maxUrlMessageLength", c.maxUrlMessageLength);
        m.put("maxMessageInputLength", effectiveInputLengthLimit(c));
        m.put("historySize", c.historySize);
        m.put("historyRetentionDays", c.historyRetentionDays);
        m.put("historyStorage", c.historyStorage);
        m.put("historyPageSize", c.historyPageSize);
        m.put("searchEnabled", c.searchEnabled);
        m.put("searchResultLimit", c.searchResultLimit);
        m.put("directMessageEnabled", c.directMessageEnabled);
        m.put("directMessageAllowWebSend", c.directMessageAllowWebSend);
        m.put("directMessageMaxMessageLength", c.directMessageMaxMessageLength);
        m.put("directMessageRetentionDays", c.directMessageRetentionDays);
        m.put("directMessageWebUnreadBadge", c.directMessageWebUnreadBadge);
        m.put("directMessageConfirmHide", c.directMessageConfirmHide);
        m.put("directMessageStorage", c.directMessageStorage);
        m.put("groupChatEnabled", c.groupChatEnabled);
        m.put("groupChatAllowWebSend", c.groupChatAllowWebSend);
        m.put("groupChatRetentionDays", c.groupChatRetentionDays);
        m.put("groupChatMaxMessageLength", c.groupChatMaxMessageLength);
        m.put("groupChatConfirmLeave", c.groupChatConfirmLeave);
        m.put("groupChatConfirmHide", c.groupChatConfirmHide);
        m.put("groupChatAllowPublicRooms", c.groupChatAllowPublicRooms);
        m.put("groupChatAllowRoomPasswords", c.groupChatAllowRoomPasswords);
        m.put("privateChatSuperAdminConfigured", c.privateChatSuperAdmins != null && !c.privateChatSuperAdmins.isEmpty());
        m.put("language", c.uiLanguage);
        m.put("uiTimeZone", c.uiTimeZone);
        m.put("linkifyUrls", c.linkifyUrls);
        m.put("clickableUrlsInGame", c.clickableUrlsInGame);
        m.put("imagePreviewEnabled", c.imagePreviewEnabled);
        m.put("imagePreviewMaxPerMessage", c.imagePreviewMaxPerMessage);
        m.put("imagePreviewMaxHeight", c.imagePreviewMaxHeight);
        m.put("googleDriveImagePreview", c.googleDriveImagePreview);
        m.put("googleDrivePreviewMode", c.googleDrivePreviewMode);
        m.put("externalMediaCacheEnabled", c.externalMediaCacheEnabled);
        m.put("cacheDiscordCdn", c.cacheDiscordCdn);
        m.put("youtubeEmbedEnabled", c.youtubeEmbedEnabled);
        m.put("youtubeClickToLoad", c.youtubeClickToLoad);
        m.put("mediaClickToLoad", c.mediaClickToLoad);
        m.put("youtubeNoCookie", c.youtubeNoCookie);
        m.put("youtubeRememberExpanded", c.youtubeRememberExpanded);
        m.put("youtubeAutoplayOnOpen", c.youtubeAutoplayOnOpen);
        m.put("youtubeMaxEmbedsPerMessage", c.youtubeMaxEmbedsPerMessage);
        m.put("socialEmbedsEnabled", c.socialEmbedsEnabled);
        m.put("socialEmbedsClickToLoad", c.socialEmbedsClickToLoad);
        m.put("socialEmbedsMaxPerMessage", c.socialEmbedsMaxPerMessage);
        m.put("tiktokEmbedEnabled", c.tiktokEmbedEnabled);
        m.put("xEmbedEnabled", c.xEmbedEnabled);
        m.put("xEmbedTheme", c.xEmbedTheme);
        m.put("xEmbedDnt", c.xEmbedDnt);
        m.put("xEmbedHideMedia", c.xEmbedHideMedia);
        m.put("xEmbedHideThread", c.xEmbedHideThread);
        m.put("uploadEnabled", c.uploadEnabled);
        m.put("uploadAllowGuest", c.uploadAllowGuest);
        m.put("uploadAllowUser", c.uploadAllowUser);
        m.put("uploadAllowModerator", c.uploadAllowModerator);
        m.put("uploadAllowAdmin", c.uploadAllowAdmin);
        m.put("uploadMaxFileSizeMb", c.uploadMaxFileSizeMb);
        m.put("uploadMaxTotalSizeMb", c.uploadMaxTotalSizeMb);
        m.put("uploadMaxFilesPerMessage", c.uploadMaxFilesPerMessage);
        m.put("uploadAllowedExtensions", c.uploadAllowedExtensions);
        m.put("uploadClipboardEnabled", c.uploadClipboardEnabled);
        m.put("uploadClipboardSendMode", c.uploadClipboardSendMode);
        m.put("uploadClipboardImageDefaultExtension", c.uploadClipboardImageDefaultExtension);
        m.put("uploadPreviewImages", c.uploadPreviewImages);
        m.put("uploadPreviewVideos", c.uploadPreviewVideos);
        m.put("uploadPreviewAudio", c.uploadPreviewAudio);

        m.put("emojiEnabled", c.emojiEnabled);
        m.put("emojiShowButton", c.emojiShowButton);
        m.put("emojiRenderSizePx", c.emojiRenderSizePx);
        m.put("emojiPickerSizePx", c.emojiPickerSizePx);
        m.put("emojiMessageTokenLimit", c.emojiMessageTokenLimit);
        m.put("emojiTokenFormat", c.emojiTokenFormat == null ? "short" : c.emojiTokenFormat);
        m.put("pinnedEnabled", c.pinnedEnabled);
        m.put("pinnedMaxPins", c.pinnedMaxPins);
        m.put("pinnedShowToLoggedOut", c.pinnedShowToLoggedOut);
        m.put("commandsEnabled", c.commandsEnabled);
        m.put("commandsAllowAll", c.commandsAllowAll);
        m.put("commandsMinRole", c.commandsMinRole == null ? "ADMIN" : c.commandsMinRole.name());
        m.put("commandsShowButton", c.commandsShowButton);
        m.put("commandsShowSlashPanel", c.commandsShowSlashPanel);
        m.put("commandsRunFromChatInput", c.commandsRunFromChatInput);
        m.put("commandsRequireConfirm", c.commandsRequireConfirm);
        m.put("commandsMaxLength", c.commandsMaxLength);
        m.put("cooldownSeconds", c.guestCooldownSeconds);
        m.put("moderationEnabled", c.moderationEnabled);
        m.put("allowWebAdminPanel", c.allowWebAdminPanel);
        m.put("allowModeratorMessageDelete", c.allowModeratorMessageDelete);
        m.put("allowModeratorGuestMute", c.allowModeratorGuestMute);
        m.put("defaultMuteMinutes", c.defaultMuteMinutes);
        m.put("discordEnabled", c.discordEnabled);
        m.put("discordChannel", c.discordChannel);
        m.put("discordWebToDiscord", c.discordWebToDiscord);
        m.put("discordDiscordToWeb", c.discordDiscordToWeb);
        sendJson(ex, 200, JsonUtil.obj(m));
    }


    private void handleLang(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String requested = q.get("lang");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("language", requested == null || requested.isBlank() ? host.language().currentLanguage() : requested.trim());
        m.put("fallback", host.language().fallbackLanguage());
        m.put("available", Arrays.asList(host.language().availableLanguages()));
        m.put("strings", requested == null || requested.isBlank() ? host.language().webStrings() : host.language().webStringsFor(requested));
        sendJson(ex, 200, JsonUtil.obj(m));
    }

    private void handleHistory(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;

        ConfigValues config = host.configValues();
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        int limit = boundedInt(q.get("limit"), config.historyPageSize, 0, 0);
        String before = q.get("before");
        if (before != null && before.isBlank()) before = null;
        String after = q.get("after");
        if (after != null && after.isBlank()) after = null;

        List<ChatMessage> page = new ArrayList<>();
        boolean hasBefore;
        boolean hasAfter;
        String oldestId = "";
        String newestId = "";

        if (sqliteHistoryEnabled()) {
            SqliteHistoryStore.Page dbPage = sqliteHistory.page(before, after, limit, sqliteCutoffMillis());
            page.addAll(dbPage.messages);
            hasBefore = dbPage.hasBefore;
            hasAfter = dbPage.hasAfter;
            oldestId = dbPage.oldestId;
            newestId = dbPage.newestId;
        } else {
            boolean pruned;
            synchronized (history) {
                pruned = pruneHistoryLocked();
                List<ChatMessage> all = new ArrayList<>(history);

                int startIndex;
                int endIndex;
                if (after != null) {
                    startIndex = all.size();
                    for (int i = 0; i < all.size(); i++) {
                        if (after.equals(all.get(i).id)) {
                            startIndex = i + 1;
                            break;
                        }
                    }
                    endIndex = limit <= 0 ? all.size() : Math.min(all.size(), startIndex + limit);
                } else {
                    endIndex = all.size();
                    if (before != null) {
                        for (int i = all.size() - 1; i >= 0; i--) {
                            if (before.equals(all.get(i).id)) {
                                endIndex = i;
                                break;
                            }
                        }
                    }
                    startIndex = limit <= 0 ? 0 : Math.max(0, endIndex - limit);
                }

                startIndex = Math.max(0, Math.min(startIndex, all.size()));
                endIndex = Math.max(startIndex, Math.min(endIndex, all.size()));
                for (int i = startIndex; i < endIndex; i++) page.add(all.get(i));
                hasBefore = startIndex > 0;
                hasAfter = endIndex < all.size();
                if (!page.isEmpty()) {
                    oldestId = page.get(0).id;
                    newestId = page.get(page.size() - 1).id;
                }
            }
            if (pruned && legacyJsonlHistoryEnabled()) savePersistedHistory();
        }

        List<String> items = new ArrayList<>();
        for (ChatMessage m : page) items.add(m.toJson());

        sendJson(ex, 200, "{\"ok\":true,\"messages\":[" + String.join(",", items) + "]"
                + ",\"hasMore\":" + hasBefore
                + ",\"hasBefore\":" + hasBefore
                + ",\"hasAfter\":" + hasAfter
                + ",\"oldestId\":" + JsonUtil.quote(oldestId)
                + ",\"newestId\":" + JsonUtil.quote(newestId)
                + "}");
    }

    private void handleHistoryAround(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String targetId = stripControl(q.get("id"), 96);
        if (targetId.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_id\"}");
            return;
        }

        int before = boundedInt(q.get("before"), 40, 0, 200);
        int after = boundedInt(q.get("after"), 40, 0, 200);
        AroundHistoryResult around = findHistoryAround(targetId, before, after);
        if (around.pruned && legacyJsonlHistoryEnabled()) {
            savePersistedHistory();
        }
        if (around.targetIndex < 0) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\",\"targetId\":" + JsonUtil.quote(targetId) + "}");
            return;
        }

        List<String> items = new ArrayList<>();
        for (ChatMessage m : around.messages) {
            items.add(m.toJson());
        }
        String oldestId = around.messages.isEmpty() ? "" : around.messages.get(0).id;
        String newestId = around.messages.isEmpty() ? "" : around.messages.get(around.messages.size() - 1).id;
        sendJson(ex, 200, "{\"ok\":true"
                + ",\"targetId\":" + JsonUtil.quote(targetId)
                + ",\"messages\":[" + String.join(",", items) + "]"
                + ",\"hasBefore\":" + around.hasBefore
                + ",\"hasAfter\":" + around.hasAfter
                + ",\"oldestId\":" + JsonUtil.quote(oldestId)
                + ",\"newestId\":" + JsonUtil.quote(newestId)
                + "}");
    }

    private void handleHistorySearch(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        if (config == null || !config.searchEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"search_disabled\"}");
            return;
        }

        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String query = stripControl(q.get("q"), 120).trim();
        String lang = stripControl(q.get("lang"), 32).trim();
        Map<String, String> searchStrings = host.language().webStringsFor(lang);
        int limit = boundedInt(q.get("limit"), config.searchResultLimit, 1, config.searchResultLimit);
        long from = searchTimeMillis(q.get("from"), Long.MIN_VALUE);
        long to = searchTimeMillis(q.get("to"), Long.MAX_VALUE);
        if (from != Long.MIN_VALUE && to != Long.MAX_VALUE && from > to) {
            long tmp = from;
            from = to;
            to = tmp;
        }
        String senderFilter = stripControl(q.get("sender"), 64).trim();
        String sourceFilter = normalizeSearchSource(q.get("source"));
        boolean includeSystem = !"false".equalsIgnoreCase(String.valueOf(q.get("includeSystem")));
        boolean hasFilter = from != Long.MIN_VALUE || to != Long.MAX_VALUE
                || !senderFilter.isBlank() || !sourceFilter.isBlank() || !includeSystem;
        if (query.isBlank() && !hasFilter) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_query_or_filter\"}");
            return;
        }

        List<ChatMessage> result;
        if (sqliteHistoryEnabled()) {
            long cutoff = sqliteCutoffMillis();
            result = sqliteHistory.search(query, limit, cutoff, from, to, senderFilter, sourceFilter, includeSystem);
            // SQL can search stored raw text directly. System/event messages may
            // be persisted with an English fallback plus an i18n key, so add a
            // second pass over i18n-backed rows using the requested web language.
            appendSqliteLocalizedSearchMatches(result, query, limit, cutoff, searchStrings, from, to, senderFilter, sourceFilter, includeSystem);
            // Keep search useful even when the SQLite file has just been created,
            // migration was skipped, or very recent messages are still only present
            // in the in-memory cache for this server session.
            appendMemorySearchMatches(result, query, limit, cutoff, searchStrings, from, to, senderFilter, sourceFilter, includeSystem);
        } else {
            result = searchInMemoryAndJsonl(query, limit, searchStrings, from, to, senderFilter, sourceFilter, includeSystem);
        }

        List<String> items = new ArrayList<>();
        for (ChatMessage msg : result) items.add(msg.toJson());
        sendJson(ex, 200, "{\"ok\":true,\"messages\":[" + String.join(",", items) + "]}");
    }

    private void handlePins(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        SessionContext ctx = sessionFromRequest(ex);
        boolean visible = config.pinnedShowToLoggedOut || ctx != null;

        List<String> items = new ArrayList<>();
        if (config.pinnedEnabled && visible) {
            for (PinnedMessage pin : storage.listPinnedMessages()) {
                items.add(pin.toJson());
            }
        }

        boolean canPin = config.pinnedEnabled && config.allowWebAdminPanel && ctx != null && ctx.account.role.atLeast(Role.MODERATOR);

        sendJson(ex, 200, "{\"ok\":true,\"enabled\":" + config.pinnedEnabled
                + ",\"visible\":" + visible
                + ",\"canPin\":" + canPin
                + ",\"maxPins\":" + config.pinnedMaxPins
                + ",\"pins\":[" + String.join(",", items) + "]}");

    }

    private void handleStreamTicket(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        SessionContext ctx = requireUserSession(ex);
        if (ctx == null) return;
        String token = requestToken(ex, true);
        if (token.isBlank()) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"not_logged_in\"}");
            return;
        }
        long now = System.currentTimeMillis();
        streamTickets.entrySet().removeIf(e -> e.getValue() == null || e.getValue().expiresAt() < now);
        String ticket = SecurityUtil.randomToken(24);
        long expiresAt = now + STREAM_TICKET_TTL_MILLIS;
        streamTickets.put(ticket, new StreamTicket(token, remoteIp(ex), expiresAt));
        sendJson(ex, 200, JsonUtil.obj(Map.of("ok", true, "ticket", ticket, "expiresAt", expiresAt)));
    }

    private void handleStream(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        String ip = remoteIp(ex);
        ConfigValues config = host.configValues();
        if (!canOpenSse(ip, config)) {
            addCors(ex);
            addSecurityHeaders(ex);
            sendJson(ex, 429, "{\"ok\":false,\"error\":\"too_many_stream_connections\"}");
            return;
        }

        Map<String, String> streamQuery = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String streamToken = "";
        String ticketValue = String.valueOf(streamQuery.getOrDefault("ticket", "")).trim();
        if (!ticketValue.isBlank()) {
            StreamTicket ticket = streamTickets.remove(ticketValue); // one-time bearer
            long now = System.currentTimeMillis();
            if (ticket == null || ticket.expiresAt() < now || !Objects.equals(ticket.clientIp(), ip)) {
                sendJson(ex, 403, "{\"ok\":false,\"error\":\"invalid_stream_ticket\"}");
                return;
            }
            streamToken = ticket.sessionToken();
        } else {
            // Legacy compatibility only. The bundled frontend no longer puts the
            // long-lived session bearer in the EventSource URL.
            streamToken = String.valueOf(streamQuery.getOrDefault("token", "")).trim();
        }
        SessionContext streamContext = streamToken.isBlank() ? null : sessionForRequest(ex, streamToken);
        if (!streamToken.isBlank() && streamContext == null) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"not_logged_in\"}");
            return;
        }
        String streamAccountUuid = streamContext == null || streamContext.account == null || streamContext.account.uuid == null ? "" : streamContext.account.uuid.trim().toLowerCase(Locale.ROOT);
        boolean streamPrivateChatSuperAdmin = isPrivateChatSuperAdmin(streamContext);

        addCors(ex);
        addSecurityHeaders(ex);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "text/event-stream; charset=utf-8");
        h.set("Cache-Control", "no-cache");
        h.set("Connection", "keep-alive");
        ex.sendResponseHeaders(200, 0);

        SseConnection client = sseHub.add(ex.getResponseBody(), ip, streamAccountUuid, streamToken, streamPrivateChatSuperAdmin);
        try {
            client.sendRaw("event: ready\ndata: {\"ok\":true}\n\n");
            long lastPing = System.currentTimeMillis();
            while (running && client.isOpen()) {
                try {
                    Thread.sleep(1_000L);
                    if (client.hasToken() && storage.getSession(client.token()) == null) {
                        sendAuthExpired(client, "expired");
                        break;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastPing >= 25_000L) {
                        client.sendRaw("event: ping\ndata: {}\n\n");
                        lastPing = now;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            sseHub.remove(client);
            client.close();
            ex.close();
        }
    }

    private boolean canOpenSse(String ip, ConfigValues config) {
        int totalLimit = Math.max(0, config.maxSseConnectionsTotal);
        if (totalLimit > 0 && sseHub.size() >= totalLimit) return false;

        int perIpLimit = Math.max(0, config.maxSseConnectionsPerIp);
        if (perIpLimit > 0) {
            int current = 0;
            for (SseConnection client : sseHub.snapshot()) {
                if (Objects.equals(client.ip(), ip)) current++;
                if (current >= perIpLimit) return false;
            }
        }
        return true;
    }

    private boolean refreshContentFilterRulesFromDisk() {
        try {
            List<ContentFilterRule> diskRules = host.loadContentFilterRulesFromDisk();
            if (diskRules == null || host.configValues() == null) return false;
            host.configValues().contentFilterRules = new ArrayList<>(diskRules);
            host.configValues().contentFilterWordListRules = new ArrayList<>(ContentFilterWordListStore.loadRules(host.dataDirectory()));
            return true;
        } catch (Exception ex) {
            host.logger().warn("Failed to refresh content filter rules/lists: " + ex.getMessage());
            return false;
        }
    }

    public ContentFilterResult filterContent(String message, ContentFilterEngine.Scope scope) {
        ConfigValues c = host.configValues();
        if (c == null) return ContentFilterResult.unchanged(String.valueOf(message == null ? "" : message));
        if (c.contentFilterEnabled && c.emojiEnabled && cachedEmojiCatalog == null) scanEmojiCatalog(c);
        return contentFilterEngine.filter(message, scope, c, cachedContentFilterEmojiAliases);
    }

    private ContentFilterResult testContentFilter(String message, ContentFilterEngine.Scope scope) {
        ConfigValues c = host.configValues();
        if (c == null) return ContentFilterResult.unchanged(String.valueOf(message == null ? "" : message));
        if (c.emojiEnabled && cachedEmojiCatalog == null) scanEmojiCatalog(c);
        return contentFilterEngine.test(message, scope, c, cachedContentFilterEmojiAliases);
    }

    public String contentFilterBlockedMessage(ContentFilterResult result) {
        ConfigValues c = host.configValues();
        WebChatLanguage language = host.language();
        if (c != null && c.contentFilterShowMatchedWord && result != null && result.matchedWord != null && !result.matchedWord.isBlank()) {
            String fallback = "Message cannot be sent because it contains a blocked word: {word}";
            if (language != null) return language.text("command.contentFilterBlockedWord", fallback, Map.of("word", result.matchedWord));
            return fallback.replace("{word}", result.matchedWord);
        }
        String fallback = "This message cannot be sent because it contains blocked content.";
        return language == null ? fallback : language.text("command.contentFilterBlocked", fallback);
    }

    private void sendContentFilterBlocked(HttpExchange ex, ContentFilterResult result) throws IOException {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", "content_blocked");
        body.put("message", contentFilterBlockedMessage(result));
        ConfigValues c = host.configValues();
        if (c != null && c.contentFilterShowMatchedWord && result != null) body.put("matchedWord", result.matchedWord);
        sendJson(ex, 400, JsonUtil.obj(body));
    }

    private void handleSend(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        String ip = remoteIp(ex);
        ConfigValues config = host.configValues();
        String rawMessage = stripChatMessage(body.get("message"), config);
        ContentFilterResult filtered = filterContent(rawMessage, ContentFilterEngine.Scope.PUBLIC);
        if (filtered.blocked) {
            sendContentFilterBlocked(ex, filtered);
            return;
        }
        rawMessage = filtered.message;
        String message = host.applyMessageTokens(rawMessage);
        String gameMessage = host.applyMessageTokensForGame(rawMessage);
        if (message.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"empty_message\"}");
            return;
        }

        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        String replyToId = body.get("replyToId");
        String replyToSender = body.get("replyToSender");
        String replyToPreview = body.get("replyToPreview");
        if (ctx != null) {
            handleUserSend(ex, ctx, message, gameMessage, replyToId, replyToSender, replyToPreview);
            return;
        }

        handleGuestSend(ex, body, ip, message, gameMessage, replyToId, replyToSender, replyToPreview);
    }




    private void handleDmThreads(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled || host.directMessages() == null || !host.directMessages().available()) {
            sendJson(ex, 200, "{\"ok\":true,\"enabled\":false,\"unread\":0,\"privateChatSuperAdmin\":false,\"privateChatContentAccess\":false,\"threads\":[],\"adminThreads\":[],\"cleanupPreview\":null}");
            return;
        }
        SessionContext ctx = sessionFromQuery(ex);
        if (!validDmUser(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        int limit = boundedInt(q.get("limit"), 200, 1, 0);
        List<String> items = new ArrayList<>();
        for (DirectMessageThread thread : host.directMessages().listThreads(ctx.account.uuid, limit)) {
            items.add(thread.toJson());
        }
        boolean privateChatSuperAdmin = isPrivateChatSuperAdmin(ctx);
        boolean privateChatContentAccess = privateChatSuperAdmin && config.directMessageAdminAuditEnabled;
        List<String> adminItems = new ArrayList<>();
        String cleanupPreview = "null";
        if (privateChatSuperAdmin) {
            adminItems.addAll(host.directMessages().adminThreadSummaries(limit));
            cleanupPreview = host.directMessages().cleanupPreviewJson();
        }
        int unread = host.directMessages().unreadCount(ctx.account.uuid);
        sendJson(ex, 200, "{\"ok\":true,\"enabled\":true,\"unread\":" + unread
                + ",\"privateChatSuperAdmin\":" + privateChatSuperAdmin
                + ",\"privateChatContentAccess\":" + privateChatContentAccess
                + ",\"threads\":[" + String.join(",", items) + "]"
                + ",\"adminThreads\":[" + String.join(",", adminItems) + "]"
                + ",\"cleanupPreview\":" + cleanupPreview + "}");
    }

    private void handleDmMessages(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled || host.directMessages() == null || !host.directMessages().available()) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"dm_disabled\"}");
            return;
        }
        SessionContext ctx = sessionFromQuery(ex);
        if (!validDmUser(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String threadId = stripControl(q.get("threadId"), 160).trim();
        if (threadId.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_thread\"}");
            return;
        }
        long before = parseLong(q.get("before"), 0L);
        int limit = boundedInt(q.get("limit"), 100, 1, 0);
        long readBefore = host.directMessages().readPosition(threadId, ctx.account.uuid);
        List<String> items = new ArrayList<>();
        for (DirectMessageMessage message : host.directMessages().listMessages(ctx.account.uuid, threadId, before, limit)) {
            items.add(message.toJson());
        }
        long readAfter = host.directMessages().readPosition(threadId, ctx.account.uuid);
        if (readAfter > readBefore) {
            String other = host.directMessages().otherParticipantUuid(threadId, ctx.account.uuid);
            publishDirectMessageUpdate(ctx.account.uuid, other, threadId);
        }
        // Remote read receipts are idempotent. Re-publish the latest receipt every time
        // the thread is viewed so a transient relay/HTTP failure cannot permanently
        // suppress the read mark after the local read position has already advanced.
        DirectMessageStore.RemoteReadReceipt receipt = host.directMessages().latestRemoteReadReceipt(threadId, ctx.account.uuid);
        ServerRelay relay = host.serverRelay();
        if (receipt.ok && relay != null) {
            relay.publishDirectMessageRead(receipt.sourceServerId, receipt.relayId);
        }
        int unread = host.directMessages().unreadCount(ctx.account.uuid);
        sendJson(ex, 200, "{\"ok\":true,\"unread\":" + unread + ",\"messages\":[" + String.join(",", items) + "]}");
    }

    private void handleAdminDmMessages(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled || !config.directMessageAdminAuditEnabled
                || host.directMessages() == null || !host.directMessages().available()) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"dm_audit_disabled\"}");
            return;
        }
        SessionContext ctx = sessionFromQuery(ex);
        if (!isPrivateChatSuperAdmin(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String threadId = stripControl(q.get("threadId"), 160).trim();
        if (threadId.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_thread\"}");
            return;
        }
        long before = parseLong(q.get("before"), 0L);
        int limit = boundedInt(q.get("limit"), 100, 1, 200);
        List<String> items = new ArrayList<>();
        for (DirectMessageMessage message : host.directMessages().adminListMessages(threadId, before, limit)) {
            items.add(message.toJson());
        }
        audit(ctx, "admin.dm-audit-read", Map.of(
                "threadId", threadId,
                "before", before,
                "limit", limit,
                "returned", items.size()
        ));
        sendJson(ex, 200, "{\"ok\":true,\"audit\":true,\"messages\":[" + String.join(",", items) + "]}");
    }

    private void handleDmPlayers(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled) {
            sendJson(ex, 200, "{\"ok\":true,\"enabled\":false,\"players\":[]}");
            return;
        }
        SessionContext ctx = sessionFromQuery(ex);
        if (!validDmUser(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String query = stripControl(q.get("q"), 80).trim();
        int limit = boundedInt(q.get("limit"), 20, 1, 0);
        List<String> items = new ArrayList<>();
        String self = ctx.account.uuid == null ? "" : ctx.account.uuid.trim().toLowerCase(Locale.ROOT);
        for (PlayerIdentity player : storage.listKnownPlayers(query, limit + 1)) {
            if (player.uuid.equalsIgnoreCase(self)) continue;
            items.add(player.toJson());
            if (items.size() >= limit) break;
        }
        sendJson(ex, 200, "{\"ok\":true,\"enabled\":true,\"players\":[" + String.join(",", items) + "]}");
    }

    private void handleDmSend(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled || host.directMessages() == null || !host.directMessages().available()) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"dm_disabled\"}");
            return;
        }
        if (!config.directMessageAllowWebSend) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"web_send_disabled\"}");
            return;
        }
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        if (!validDmUser(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }

        String targetValue = stripControl(body.get("targetUuid"), 256).trim();
        String requestedServerId = RemotePlayerRef.normalizeServerId(stripControl(body.get("targetServerId"), 64));
        RemotePlayerRef remote = RemotePlayerRef.parse(targetValue);
        if (remote == null && !requestedServerId.isBlank() && !isLocalDirectMessageServer(requestedServerId)) {
            String realUuid = RemotePlayerRef.normalizePlayerUuid(targetValue);
            String remoteKey = RemotePlayerRef.key(requestedServerId, realUuid);
            remote = RemotePlayerRef.parse(remoteKey);
            if (remote != null) {
                String targetUsername = stripControl(body.get("targetUsername"), 64).trim();
                String targetDisplayName = stripControl(body.get("targetDisplayName"), 96).trim();
                String targetServerName = stripControl(body.get("targetServerName"), 96).trim();
                if (targetDisplayName.isBlank()) targetDisplayName = stripControl(body.get("targetLabel"), 96).trim();
                String decorated = RemotePlayerRef.decorateDisplayName(targetDisplayName, targetUsername, targetServerName, requestedServerId);
                storage.updateLastDisplayName(remote.key, targetUsername, decorated);
            }
        }

        PlayerIdentity target;
        if (remote != null) {
            target = storage.findKnownPlayerByUuid(remote.key);
            if (target == null) {
                String targetUsername = stripControl(body.get("targetUsername"), 64).trim();
                String targetDisplayName = stripControl(body.get("targetDisplayName"), 96).trim();
                String targetServerName = stripControl(body.get("targetServerName"), 96).trim();
                if (targetDisplayName.isBlank()) targetDisplayName = stripControl(body.get("targetLabel"), 96).trim();
                String decorated = RemotePlayerRef.decorateDisplayName(targetDisplayName, targetUsername, targetServerName, remote.serverId);
                storage.updateLastDisplayName(remote.key, targetUsername, decorated);
                target = storage.findKnownPlayerByUuid(remote.key);
            }
        } else {
            if (targetValue.isBlank()) {
                PlayerIdentity byName = storage.findKnownLocalPlayer(body.get("target"));
                if (byName != null) targetValue = byName.uuid;
            }
            target = storage.findKnownPlayerByUuid(targetValue);
        }
        if (target == null || target.uuid == null || target.uuid.isBlank()) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"player_not_found\"}");
            return;
        }

        String rawDmMessage = stripDirectMessage(body.get("message"), config.directMessageMaxMessageLength);
        ContentFilterResult filtered = filterContent(rawDmMessage, ContentFilterEngine.Scope.DM);
        if (filtered.blocked) {
            sendContentFilterBlocked(ex, filtered);
            return;
        }
        rawDmMessage = filtered.message;
        String message = host.applyMessageTokens(rawDmMessage);
        String gameNoticeMessage = host.applyMessageTokensForGame(rawDmMessage);
        if (message.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"empty_message\"}");
            return;
        }
        String clientMessageId = stripControl(body.get("clientMessageId"), 180).trim();

        ServerRelay relay = host.serverRelay();
        if (remote != null && (relay == null || !relay.canRouteDirectMessage(remote.serverId))) {
            sendJson(ex, 503, "{\"ok\":false,\"error\":\"remote_server_unavailable\"}");
            return;
        }

        DirectMessageStore.SendResult result;
        String relayId = "";
        if (remote != null) {
            relayId = relay.createDirectMessageRelayId();
            result = host.directMessages().sendPendingRemote(
                    ctx.account.uuid, target.uuid, message, relayId, remote.serverId, clientMessageId);
            if (result.duplicate && result.message != null) relayId = result.message.relayId;
        } else {
            result = host.directMessages().sendWithClientMessageId(ctx.account.uuid, target.uuid, message, clientMessageId);
        }
        if (!result.ok) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}");
            return;
        }

        String threadId = result.thread == null ? "" : result.thread.id;
        long messageId = result.message == null ? 0L : result.message.id;
        publishDirectMessageUpdate(ctx.account.uuid, target.uuid, threadId);
        if (!result.duplicate) {
            adminDiscordAlerts.inspect("dm:" + messageId, host.displayNameForAccount(ctx.account), "web", message, AdminDiscordAlertManager.Scope.DM);
        }

        if (remote != null) {
            String status = result.message == null ? "pending" : String.valueOf(result.message.deliveryStatus);
            boolean retryExistingFailed = result.duplicate && "failed".equalsIgnoreCase(status);
            if (retryExistingFailed) {
                host.directMessages().updateDeliveryStatus(messageId, "pending", "");
                if (result.message != null) {
                    result.message.deliveryStatus = "pending";
                    result.message.deliveryError = "";
                }
                status = "pending";
                publishDirectMessageUpdate(ctx.account.uuid, target.uuid, threadId);
            }
            if ((!result.duplicate || retryExistingFailed) && "pending".equalsIgnoreCase(status)) {
                String senderUsername = stripControl(ctx.account.safeUsername(), 64).trim();
                String senderDisplayName = stripControl(host.displayNameForAccount(ctx.account), 96).trim();
                String targetUsername = target.username == null ? "" : stripControl(target.username, 64).trim();
                String targetDisplayName = target.displayName == null ? "" : stripControl(target.displayName, 128).trim();
                final String finalRelayId = relayId;
                final String finalTargetUuid = target.uuid;
                relay.publishDirectMessage(
                                finalRelayId,
                                ctx.account.uuid, senderUsername, senderDisplayName,
                                remote.serverId, remote.playerUuid, targetUsername, targetDisplayName, message, gameNoticeMessage)
                        .whenComplete((delivery, error) -> completeRemoteDirectMessageDelivery(
                                ctx.account.uuid, finalTargetUuid, threadId, messageId, delivery, error));
                if (!result.duplicate) {
                    dispatchWebPushDirectMessage(ctx.account.uuid, senderDisplayName, target.uuid, target.label(), threadId, messageId, message);
                }
            }
        } else if (!result.duplicate) {
            dispatchWebPushDirectMessage(ctx.account.uuid, host.displayNameForAccount(ctx.account), target.uuid, target.label(), threadId, messageId, message);
            notifyOnlineDirectMessage(ctx.account, target, gameNoticeMessage);
        }

        String threadJson = result.thread == null ? "null" : result.thread.toJson();
        String messageJson = result.message == null ? "null" : result.message.toJson();
        int unread = host.directMessages().unreadCount(ctx.account.uuid);
        sendJson(ex, 200, "{\"ok\":true,\"unread\":" + unread + ",\"thread\":" + threadJson + ",\"message\":" + messageJson + "}");
    }

    private void completeRemoteDirectMessageDelivery(String senderUuid, String targetUuid, String threadId, long messageId,
                                                       ServerRelay.DirectMessageDelivery delivery, Throwable error) {
        if (host.directMessages() == null || messageId <= 0) return;
        boolean delivered = error == null && delivery != null && delivery.delivered;
        String errorCode = delivered ? "" : (delivery == null ? "dm_transport_error" : delivery.error);
        host.directMessages().updateDeliveryStatus(messageId, delivered ? "delivered" : "failed", errorCode);
        publishDirectMessageUpdate(senderUuid, targetUuid, threadId);
    }

    private void handleDmRetry(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled || !config.directMessageAllowWebSend
                || host.directMessages() == null || !host.directMessages().available()) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"dm_disabled\"}");
            return;
        }
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        if (!validDmUser(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        long messageId = parseLong(body.get("messageId"), 0L);
        DirectMessageStore.RetryData retry = host.directMessages().retryData(ctx.account.uuid, messageId);
        if (!retry.ok || retry.message == null) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(retry.error) + "}");
            return;
        }
        RemotePlayerRef remote = RemotePlayerRef.parse(retry.targetUuid);
        ServerRelay relay = host.serverRelay();
        if (remote == null || relay == null || !relay.canRouteDirectMessage(remote.serverId)) {
            sendJson(ex, 503, "{\"ok\":false,\"error\":\"remote_server_unavailable\"}");
            return;
        }
        String relayId = retry.message.relayId == null ? "" : retry.message.relayId.trim();
        if (relayId.isBlank()) {
            sendJson(ex, 409, "{\"ok\":false,\"error\":\"retry_metadata_missing\"}");
            return;
        }
        PlayerIdentity target = storage.findKnownPlayerByUuid(retry.targetUuid);
        String targetUsername = target == null || target.username == null ? "" : stripControl(target.username, 64).trim();
        String targetDisplayName = target == null || target.displayName == null ? "" : stripControl(target.displayName, 128).trim();
        String senderUsername = stripControl(ctx.account.safeUsername(), 64).trim();
        String senderDisplayName = stripControl(host.displayNameForAccount(ctx.account), 96).trim();
        host.directMessages().updateDeliveryStatus(messageId, "pending", "");
        String threadId = retry.message.threadId == null ? "" : retry.message.threadId;
        publishDirectMessageUpdate(ctx.account.uuid, retry.targetUuid, threadId);
        relay.publishDirectMessage(
                        relayId,
                        ctx.account.uuid, senderUsername, senderDisplayName,
                        remote.serverId, remote.playerUuid, targetUsername, targetDisplayName, retry.message.body, retry.message.body)
                .whenComplete((delivery, error) -> completeRemoteDirectMessageDelivery(
                        ctx.account.uuid, retry.targetUuid, threadId, messageId, delivery, error));
        sendJson(ex, 202, "{\"ok\":true,\"status\":\"pending\",\"messageId\":" + messageId + "}");
    }

    private boolean isLocalDirectMessageServer(String serverId) {
        String target = RemotePlayerRef.normalizeServerId(serverId);
        if (target.isBlank()) return true;
        ServerRelay relay = host.serverRelay();
        if (relay != null && !relay.serverId().isBlank()) return target.equalsIgnoreCase(relay.serverId());
        ConfigValues config = host.configValues();
        String local = RemotePlayerRef.normalizeServerId(config == null ? "" : config.serverRelayServerId);
        return !local.isBlank() && target.equalsIgnoreCase(local);
    }

    private void handleDmRead(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled || host.directMessages() == null || !host.directMessages().available()) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"dm_disabled\"}");
            return;
        }
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        if (!validDmUser(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        String threadId = stripControl(body.get("threadId"), 160).trim();
        long readBefore = host.directMessages().readPosition(threadId, ctx.account.uuid);
        boolean ok = host.directMessages().markRead(threadId, ctx.account.uuid);
        long readAfter = host.directMessages().readPosition(threadId, ctx.account.uuid);
        if (ok && readAfter > readBefore) {
            String other = host.directMessages().otherParticipantUuid(threadId, ctx.account.uuid);
            publishDirectMessageUpdate(ctx.account.uuid, other, threadId);
        }
        if (ok) {
            // Re-send the latest remote receipt even when this call did not advance
            // last_read_message_id. A previous acknowledgement may have been lost.
            DirectMessageStore.RemoteReadReceipt receipt = host.directMessages().latestRemoteReadReceipt(threadId, ctx.account.uuid);
            ServerRelay relay = host.serverRelay();
            if (receipt.ok && relay != null) relay.publishDirectMessageRead(receipt.sourceServerId, receipt.relayId);
        }
        sendJson(ex, 200, "{\"ok\":" + ok + ",\"unread\":" + host.directMessages().unreadCount(ctx.account.uuid) + "}");
    }

    private void handleDmHideMessage(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled || host.directMessages() == null || !host.directMessages().available()) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"dm_disabled\"}");
            return;
        }
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        if (!validDmUser(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        long messageId = parseLong(body.get("messageId"), 0L);
        if (messageId <= 0) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_message\"}");
            return;
        }
        String threadId = host.directMessages().threadIdForMessage(ctx.account.uuid, messageId);
        boolean ok = host.directMessages().hideMessage(ctx.account.uuid, messageId);
        if (ok && !threadId.isBlank()) {
            publishDirectMessageUpdate(ctx.account.uuid, ctx.account.uuid, threadId);
        }
        sendJson(ex, 200, "{\"ok\":" + ok + ",\"unread\":" + host.directMessages().unreadCount(ctx.account.uuid) + "}");
    }


    private void handleGroupRooms(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatEnabled || host.groupChats() == null || !host.groupChats().available()) {
            sendJson(ex, 200, "{\"ok\":true,\"enabled\":false,\"unread\":0,\"privateChatSuperAdmin\":false,\"groupChatContentAccess\":false,\"rooms\":[],\"invites\":[],\"hiddenRooms\":[],\"adminRooms\":[],\"cleanupPreview\":null}"); return;
        }
        SessionContext ctx = sessionFromQuery(ex);
        if (!validGroupUser(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        Map<String,String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        int limit = boundedInt(q.get("limit"), 200, 1, 0);
        List<String> rooms = new ArrayList<>();
        for (GroupRoom room : host.groupChats().listRooms(ctx.account.uuid, limit)) rooms.add(room.toJson());
        List<String> invites = new ArrayList<>();
        for (GroupInvite invite : host.groupChats().listInvites(ctx.account.uuid, 100)) invites.add(invite.toJson());
        List<String> hiddenRooms = host.groupChats().listHiddenRoomsJson(ctx.account.uuid, limit);
        boolean privateChatSuperAdmin = isPrivateChatSuperAdmin(ctx);
        boolean groupChatContentAccess = privateChatSuperAdmin && config.groupChatAdminAuditEnabled;
        List<String> adminRooms = new ArrayList<>();
        String cleanupPreview = "null";
        if (privateChatSuperAdmin) {
            adminRooms.addAll(host.groupChats().adminRoomSummaries(limit));
            cleanupPreview = host.groupChats().cleanupPreviewJson();
        }
        sendJson(ex, 200, "{\"ok\":true,\"enabled\":true,\"unread\":" + host.groupChats().unreadCount(ctx.account.uuid)
                + ",\"privateChatSuperAdmin\":" + privateChatSuperAdmin
                + ",\"groupChatContentAccess\":" + groupChatContentAccess
                + ",\"rooms\":[" + String.join(",", rooms) + "]"
                + ",\"invites\":[" + String.join(",", invites) + "]"
                + ",\"hiddenRooms\":[" + String.join(",", hiddenRooms) + "]"
                + ",\"adminRooms\":[" + String.join(",", adminRooms) + "]"
                + ",\"cleanupPreview\":" + cleanupPreview + "}");
    }


    private void handleGroupPlayers(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatEnabled || host.groupChats() == null || !host.groupChats().available()) {
            sendJson(ex, 200, "{\"ok\":true,\"enabled\":false,\"players\":[]}");
            return;
        }
        SessionContext ctx = sessionFromQuery(ex);
        if (!validGroupUser(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String query = stripControl(q.get("q"), 80).trim();
        int limit = boundedInt(q.get("limit"), 20, 1, 0);
        List<String> items = new ArrayList<>();
        String self = ctx.account.uuid == null ? "" : ctx.account.uuid.trim().toLowerCase(Locale.ROOT);
        for (PlayerIdentity player : storage.listKnownPlayers(query, limit + 1)) {
            if (player.uuid.equalsIgnoreCase(self) || RemotePlayerRef.isRemote(player.uuid)) continue;
            items.add(player.toJson());
            if (items.size() >= limit) break;
        }
        sendJson(ex, 200, "{\"ok\":true,\"enabled\":true,\"players\":[" + String.join(",", items) + "]}");
    }

    private void handleGroupMessages(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatEnabled || host.groupChats() == null || !host.groupChats().available()) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"group_disabled\"}"); return; }
        SessionContext ctx = sessionFromQuery(ex);
        if (!validGroupUser(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        Map<String,String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String roomId = stripControl(q.get("roomId"), 120).trim();
        long before = parseLong(q.get("before"), 0L);
        int limit = boundedInt(q.get("limit"), 100, 1, 0);
        List<String> messages = new ArrayList<>();
        for (GroupMessage message : host.groupChats().listMessages(ctx.account.uuid, roomId, before, limit)) messages.add(message.toJson());
        sendJson(ex, 200, "{\"ok\":true,\"unread\":" + host.groupChats().unreadCount(ctx.account.uuid) + ",\"messages\":[" + String.join(",", messages) + "]}");
    }

    private void handleAdminGroupMessages(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatEnabled || !config.groupChatAdminAuditEnabled
                || host.groupChats() == null || !host.groupChats().available()) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"group_audit_disabled\"}");
            return;
        }
        SessionContext ctx = sessionFromQuery(ex);
        if (!isPrivateChatSuperAdmin(ctx)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String,String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String roomId = stripControl(q.get("roomId"), 120).trim();
        if (roomId.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_room\"}");
            return;
        }
        long before = parseLong(q.get("before"), 0L);
        int limit = boundedInt(q.get("limit"), 100, 1, 200);
        List<String> items = new ArrayList<>();
        for (GroupMessage message : host.groupChats().adminListMessages(roomId, before, limit)) {
            items.add(message.toJson());
        }
        audit(ctx, "admin.group-audit-read", Map.of(
                "roomId", roomId,
                "before", before,
                "limit", limit,
                "returned", items.size()
        ));
        sendJson(ex, 200, "{\"ok\":true,\"audit\":true,\"messages\":[" + String.join(",", items) + "]}");
    }

    private void handleGroupCreate(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatEnabled || host.groupChats() == null || !host.groupChats().available()) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"group_disabled\"}"); return; }
        Map<String,String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        SessionContext ctx = sessionForRequest(ex, body.get("token"));
        if (!validGroupUser(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        GroupChatStore.CreateResult result = host.groupChats().createRoom(ctx.account.uuid, body.get("name"), body.get("visibility"), body.get("password"));
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(result.room == null ? "" : result.room.id);
        sendJson(ex, 200, "{\"ok\":true,\"room\":" + (result.room == null ? "null" : result.room.toJson()) + "}");
    }

    private void handleGroupJoin(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        GroupChatStore.ActionResult result = host.groupChats().joinRoom(req.ctx.account.uuid, req.body.get("roomId"), req.body.get("password"));
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(result.room == null ? req.body.get("roomId") : result.room.id);
        sendJson(ex, 200, "{\"ok\":true,\"room\":" + (result.room == null ? "null" : result.room.toJson()) + "}");
    }

    private void handleGroupLeave(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        String roomId = req.body.get("roomId");
        GroupChatStore.ActionResult result = host.groupChats().leaveRoom(req.ctx.account.uuid, roomId);
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(roomId);
        sendJson(ex, 200, "{\"ok\":true,\"unread\":" + host.groupChats().unreadCount(req.ctx.account.uuid) + "}");
    }

    private void handleGroupInvite(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        String targetUuid = stripControl(req.body.get("targetUuid"), 80).trim();
        if (targetUuid.isBlank()) {
            PlayerIdentity target = storage.findKnownLocalPlayer(req.body.get("target"));
            if (target != null) targetUuid = target.uuid;
        }
        PlayerIdentity target = storage.findKnownPlayerByUuid(targetUuid);
        if (target == null || target.uuid == null || target.uuid.isBlank() || RemotePlayerRef.isRemote(target.uuid)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"player_not_found\"}");
            return;
        }
        GroupChatStore.ActionResult result = host.groupChats().invite(req.ctx.account.uuid, req.body.get("roomId"), target.uuid);
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        // Broadcast globally so the invitee sees the pending invitation immediately even before becoming a room member.
        publishGroupChatUpdate("");
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private void handleGroupInvites(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatEnabled || host.groupChats() == null || !host.groupChats().available()) { sendJson(ex, 200, "{\"ok\":true,\"invites\":[]}"); return; }
        SessionContext ctx = sessionFromQuery(ex);
        if (!validGroupUser(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        List<String> invites = new ArrayList<>();
        for (GroupInvite invite : host.groupChats().listInvites(ctx.account.uuid, 100)) invites.add(invite.toJson());
        sendJson(ex, 200, "{\"ok\":true,\"invites\":[" + String.join(",", invites) + "]}");
    }

    private void handleGroupInviteRespond(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        long inviteId = parseLong(req.body.get("inviteId"), 0L);
        boolean accept = Boolean.parseBoolean(String.valueOf(req.body.getOrDefault("accept", "false")));
        GroupChatStore.ActionResult result = host.groupChats().respondInvite(req.ctx.account.uuid, inviteId, accept);
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(result.room == null ? "" : result.room.id);
        sendJson(ex, 200, "{\"ok\":true,\"room\":" + (result.room == null ? "null" : result.room.toJson()) + "}");
    }

    private void handleGroupSend(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatAllowWebSend) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"web_send_disabled\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        String rawGroupMessage = String.valueOf(req.body.get("message") == null ? "" : req.body.get("message"));
        ContentFilterResult filtered = filterContent(rawGroupMessage, ContentFilterEngine.Scope.GROUP);
        if (filtered.blocked) {
            sendContentFilterBlocked(ex, filtered);
            return;
        }
        rawGroupMessage = filtered.message;
        String message = host.applyMessageTokens(rawGroupMessage);
        String gameNoticeMessage = host.applyMessageTokensForGame(rawGroupMessage);
        GroupChatStore.SendResult result = host.groupChats().send(
                req.ctx.account.uuid,
                req.body.get("roomId"),
                message,
                stripControl(req.body.get("clientMessageId"), 180).trim());
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(result.room == null ? req.body.get("roomId") : result.room.id);
        if (!result.duplicate) {
            String alertId = "group:" + (result.message == null ? 0L : result.message.id);
            adminDiscordAlerts.inspect(alertId, host.displayNameForAccount(req.ctx.account), "web", message, AdminDiscordAlertManager.Scope.GROUP);
            dispatchWebPushGroupMessage(req.ctx.account.uuid, host.displayNameForAccount(req.ctx.account), result.room, result.message, req.body.get("roomId"));
            notifyOnlineGroupMembers(req.ctx.account, result.room, result.message, req.body.get("roomId"), gameNoticeMessage);
        }
        sendJson(ex, 200, "{\"ok\":true,\"room\":" + (result.room == null ? "null" : result.room.toJson()) + ",\"message\":" + (result.message == null ? "null" : result.message.toJson()) + "}");
    }

    private void handleGroupRead(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        String roomId = req.body.get("roomId");
        long readBefore = host.groupChats().readPosition(roomId, req.ctx.account.uuid);
        boolean ok = host.groupChats().markRead(roomId, req.ctx.account.uuid);
        long readAfter = host.groupChats().readPosition(roomId, req.ctx.account.uuid);
        if (ok && readAfter > readBefore) publishGroupChatUpdate(roomId);
        sendJson(ex, 200, "{\"ok\":" + ok + ",\"unread\":" + host.groupChats().unreadCount(req.ctx.account.uuid) + "}");
    }

    private void handleGroupHideMessage(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        long messageId = parseLong(req.body.get("messageId"), 0L);
        String roomId = host.groupChats().roomIdForMessage(req.ctx.account.uuid, messageId);
        boolean ok = host.groupChats().hideMessage(req.ctx.account.uuid, messageId);
        if (ok) publishGroupChatUpdate(roomId);
        sendJson(ex, 200, "{\"ok\":" + ok + ",\"unread\":" + host.groupChats().unreadCount(req.ctx.account.uuid) + "}");
    }

    private void handleGroupSettings(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        boolean passwordSet = req.body.containsKey("password");
        GroupChatStore.ActionResult result = host.groupChats().updateSettings(req.ctx.account.uuid, req.body.get("roomId"), req.body.get("name"), req.body.get("visibility"), req.body.get("password"), passwordSet);
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(result.room == null ? req.body.get("roomId") : result.room.id);
        sendJson(ex, 200, "{\"ok\":true,\"room\":" + (result.room == null ? "null" : result.room.toJson()) + "}");
    }


    private void handleGroupMembers(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatEnabled || host.groupChats() == null || !host.groupChats().available()) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"group_disabled\"}"); return; }
        SessionContext ctx = sessionFromQuery(ex);
        if (!validGroupUser(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        Map<String,String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String roomId = stripControl(q.get("roomId"), 120).trim();
        List<String> members = host.groupChats().listMembersJson(ctx.account.uuid, roomId);
        List<String> bans = host.groupChats().listBansJson(ctx.account.uuid, roomId);
        sendJson(ex, 200, "{\"ok\":true,\"members\":[" + String.join(",", members) + "],\"bans\":[" + String.join(",", bans) + "]}");
    }

    private void handleGroupKick(HttpExchange ex) throws IOException { handleGroupMemberAction(ex, "kick"); }
    private void handleGroupBan(HttpExchange ex) throws IOException { handleGroupMemberAction(ex, "ban"); }
    private void handleGroupUnban(HttpExchange ex) throws IOException { handleGroupMemberAction(ex, "unban"); }

    private void handleGroupMemberAction(HttpExchange ex, String action) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        String targetUuid = stripControl(req.body.get("targetUuid"), 80).trim();
        GroupChatStore.ActionResult result;
        if ("unban".equals(action)) result = host.groupChats().unban(req.ctx.account.uuid, req.body.get("roomId"), targetUuid);
        else result = host.groupChats().kick(req.ctx.account.uuid, req.body.get("roomId"), targetUuid, "ban".equals(action));
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(req.body.get("roomId"), targetUuid);
        sendJson(ex, 200, "{\"ok\":true}");
    }

    private void handleGroupHideRoom(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        String roomId = req.body.get("roomId");
        GroupChatStore.ActionResult result = host.groupChats().hideRoom(req.ctx.account.uuid, roomId);
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(roomId, req.ctx.account.uuid);
        sendJson(ex, 200, "{\"ok\":true,\"unread\":" + host.groupChats().unreadCount(req.ctx.account.uuid) + "}");
    }

    private void handleGroupUnhideRoom(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        String roomId = req.body.get("roomId");
        GroupChatStore.ActionResult result = host.groupChats().unhideRoom(req.ctx.account.uuid, roomId);
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(roomId, req.ctx.account.uuid);
        sendJson(ex, 200, "{\"ok\":true,\"room\":" + (result.room == null ? "null" : result.room.toJson()) + ",\"unread\":" + host.groupChats().unreadCount(req.ctx.account.uuid) + "}");
    }

    private void handleGroupTransferOwner(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        GroupRequest req = groupRequest(ex);
        if (!req.ok) return;
        String targetUuid = stripControl(req.body.get("targetUuid"), 80).trim();
        GroupChatStore.ActionResult result = host.groupChats().transferOwner(req.ctx.account.uuid, req.body.get("roomId"), targetUuid);
        if (!result.ok) { sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error) + "}"); return; }
        publishGroupChatUpdate(req.body.get("roomId"));
        sendJson(ex, 200, "{\"ok\":true,\"room\":" + (result.room == null ? "null" : result.room.toJson()) + "}");
    }

    private GroupRequest groupRequest(HttpExchange ex) throws IOException {
        GroupRequest req = new GroupRequest();
        ConfigValues config = host.configValues();
        if (config == null || !config.groupChatEnabled || host.groupChats() == null || !host.groupChats().available()) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"group_disabled\"}"); return req; }
        req.body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        req.ctx = sessionForRequest(ex, req.body.get("token"));
        if (!validGroupUser(req.ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return req; }
        req.ok = true;
        return req;
    }

    private boolean validGroupUser(SessionContext ctx) {
        return ctx != null && ctx.account != null && ctx.account.role.atLeast(Role.USER)
                && ctx.account.uuid != null && !ctx.account.uuid.isBlank();
    }

    private boolean isPrivateChatSuperAdmin(SessionContext ctx) {
        if (ctx == null || ctx.account == null || ctx.account.uuid == null || ctx.account.uuid.isBlank()) return false;
        ConfigValues config = host.configValues();
        if (config == null || config.privateChatSuperAdmins == null || config.privateChatSuperAdmins.isEmpty()) return false;
        String uuid = String.valueOf(ctx.account.uuid == null ? "" : ctx.account.uuid).trim().toLowerCase(Locale.ROOT);
        String username = String.valueOf(ctx.account.safeUsername() == null ? "" : ctx.account.safeUsername()).trim().toLowerCase(Locale.ROOT);
        String display = String.valueOf(host.displayNameForAccount(ctx.account) == null ? "" : host.displayNameForAccount(ctx.account)).trim().toLowerCase(Locale.ROOT);
        for (String raw : config.privateChatSuperAdmins) {
            String v = String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
            if (v.isBlank()) continue;
            if (v.equals(uuid) || v.equals(username) || v.equals(display)) return true;
        }
        return false;
    }

    private static final class GroupRequest {
        boolean ok;
        Map<String,String> body = new LinkedHashMap<>();
        SessionContext ctx;
    }

    private SessionContext sessionFromQuery(HttpExchange ex) {
        // Kept as a source-compatible helper name for older call sites; bearer
        // authentication is preferred and query tokens are legacy fallback only.
        return sessionFromRequest(ex);
    }

    private boolean validDmUser(SessionContext ctx) {
        return ctx != null && ctx.account != null && ctx.account.role.atLeast(Role.USER)
                && ctx.account.uuid != null && !ctx.account.uuid.isBlank();
    }

    private String stripDirectMessage(String raw, int maxLength) {
        String out = String.valueOf(raw == null ? "" : raw);
        // Keep the same raw text semantics as normal chat: color codes and
        // :pack/name: custom emoji tokens are stored and delivered unchanged.
        out = out.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        if (maxLength > 0 && out.length() > maxLength) out = out.substring(0, maxLength);
        return out;
    }

    private void notifyOnlineDirectMessage(Account sender, PlayerIdentity target, String message) {
        notifyOnlineDirectMessage(host.displayNameForAccount(sender), target, message);
    }

    private void notifyOnlineDirectMessage(String senderName, PlayerIdentity target, String message) {
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageNotifyOnMessage || target == null) return;
        platform.runMainThread(() -> {
            try {
                java.util.UUID recipientUuid = java.util.UUID.fromString(target.uuid);
                if (platform.onlinePlayer(recipientUuid).isEmpty()) return;
                String body = colorizeForGame(trimForNotice(message, 100));
                java.util.Map<String, String> vars = new java.util.HashMap<>();
                vars.put("player", LegacyText.RESET + String.valueOf(senderName == null ? "" : senderName) + LegacyText.LIGHT_PURPLE);
                vars.put("message", LegacyText.RESET + body);
                sendTokenGameLines(recipientUuid, LegacyText.LIGHT_PURPLE + host.language().text("command.dmIncoming", "DM from {player}: {message}", vars));
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    private void notifyOnlineGroupMembers(Account sender, GroupRoom room, GroupMessage message, String fallbackRoomId, String gameNoticeMessage) {
        if (sender == null || host.groupChats() == null || message == null) return;
        String roomId = room != null && room.id != null && !room.id.isBlank() ? room.id : String.valueOf(fallbackRoomId == null ? "" : fallbackRoomId);
        if (roomId.isBlank()) return;
        String roomName = room != null && room.name != null && !room.name.isBlank() ? room.name : roomId;
        String senderName = host.displayNameForAccount(sender);
        String body = colorizeForGame(trimForNotice(gameNoticeMessage == null || gameNoticeMessage.isBlank() ? message.body : gameNoticeMessage, 100));
        java.util.Map<String, String> vars = new java.util.HashMap<>();
        vars.put("room", LegacyText.RESET + roomName + LegacyText.AQUA);
        vars.put("player", LegacyText.RESET + senderName + LegacyText.AQUA);
        vars.put("message", LegacyText.RESET + body);
        String line = LegacyText.AQUA + host.language().text("command.groupIncoming", "Group {room} from {player}: {message}", vars);
        java.util.Set<String> members = host.groupChats().memberUuids(roomId);
        if (members == null || members.isEmpty()) return;
        platform.runMainThread(() -> {
            for (String memberUuid : members) {
                if (memberUuid == null || memberUuid.isBlank()) continue;
                try {
                    java.util.UUID recipientUuid = java.util.UUID.fromString(memberUuid);
                    if (platform.onlinePlayer(recipientUuid).isPresent()) sendTokenGameLines(recipientUuid, line);
                } catch (IllegalArgumentException ignored) {
                }
            }
        });
    }

    private String colorizeForGame(String value) {
        return LegacyText.translateAlternateColorCodes('&', String.valueOf(value == null ? "" : value));
    }

    private String trimForNotice(String value, int max) {
        String out = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (max > 0 && out.length() > max) return out.substring(0, Math.max(0, max - 1)) + "…";
        return out;
    }

    private boolean emojiTokenLimitExceeded(String message, ConfigValues config) {
        if (config == null || !config.emojiEnabled || config.emojiMessageTokenLimit <= 0) return false;
        EmojiCatalog catalog = scanEmojiCatalog(config);
        Map<String, EmojiItem> emojiById = new HashMap<>();
        for (EmojiItem item : catalog.items) {
            emojiById.put(item.id, item);
        }
        Map<String, String> aliasToId = emojiAliasToWebId(catalog, config);
        int count = 0;
        Matcher matcher = EMOJI_TOKEN_PATTERN.matcher(String.valueOf(message == null ? "" : message));
        while (matcher.find()) {
            if (emojiItemForToken(matcher.group(1), emojiById, aliasToId) == null) continue;
            count++;
            if (count > config.emojiMessageTokenLimit) return true;
        }
        return false;
    }

    private void handleUserSend(HttpExchange ex, SessionContext ctx, String message, String gameMessage, String replyToId, String replyToSender, String replyToPreview) throws IOException {
        if (emojiTokenLimitExceeded(message, host.configValues())) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"emoji_limit\"}");
            return;
        }
        if (!ctx.account.role.atLeast(Role.USER)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        prewarmExternalMediaCache(message);
        ChatMessage msg = new ChatMessage(System.currentTimeMillis(), "web", host.displayNameForAccount(ctx.account), ctx.account.role.name(), message)
                .withGameMessage(gameMessage)
                .withRealSender(stripControl(ctx.account.safeUsername(), 64), stripControl(ctx.account.uuid, 64));
        attachReplyIfPresent(msg, replyToId, replyToSender, replyToPreview);
        prepareServerRelay(msg);
        adminDiscordAlerts.inspect(msg, AdminDiscordAlertManager.Scope.PUBLIC);
        ConfigValues c = host.configValues();
        if (c.broadcastWebChatToWeb) {
            addHistory(msg);
            broadcast(msg);
            dispatchWebPushChat(msg);
        }
        sendJson(ex, 200, "{\"ok\":true}");
        sendToGame(msg, ctx.account.role == Role.ADMIN ? c.webAdminToGameFormat : c.webUserToGameFormat);
        host.discord().sendWebMessage(msg);
        publishServerRelay(msg);
    }

    private void handleGuestSend(HttpExchange ex, Map<String, String> body, String ip, String message, String gameMessage, String replyToId, String replyToSender, String replyToPreview) throws IOException {
        ConfigValues config = host.configValues();
        if (emojiTokenLimitExceeded(message, config)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"emoji_limit\"}");
            return;
        }
        if (!config.guestEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"guest_disabled\"}");
            return;
        }

        String captchaPass = null;
        if (captcha.enabled(config == null ? null : config.captchaMode)) {
            boolean passed = false;
            if (!config.captchaRequireOnEachMessage) {
                passed = captcha.verifyPass(body.get("captchaPass")) || captcha.verifyIpPass(ip);
            }
            if (!passed) {
                passed = captcha.verify(body.get("captchaId"), body.get("captchaAnswer"));
                if (passed && !config.captchaRequireOnEachMessage) {
                    captchaPass = captcha.issuePass(config.captchaPassValidMinutes);
                    captcha.issueIpPass(ip, config.captchaPassValidMinutes);
                }
            }
            if (!passed) {
                sendJson(ex, 403, "{\"ok\":false,\"error\":\"captcha_failed\"}");
                return;
            }
        }

        String guestName = sanitizeGuestName(body.get("guestName"));
        if (guestName.isBlank()) {
            guestName = generatedGuestNameForIp(ip);
        }
        if (!isGuestNameAllowed(guestName)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"blocked_name\"}");
            return;
        }
        if (config.moderationEnabled && host.moderation().isMuted(guestName, ip)) {
            ModerationEntry mute = host.moderation().findMatch(guestName, ip);
            String reason = mute == null ? "" : mute.reason;
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"guest_muted\",\"reason\":" + JsonUtil.quote(reason) + "}");
            return;
        }

        String limiterKey = "guest:" + ip + ":" + guestName.toLowerCase(Locale.ROOT);
        if (!rateLimiter.allow(limiterKey, config.guestCooldownSeconds, config.guestMaxMessagesPerMinute)) {
            String extra = captchaPass == null ? "" : ",\"captchaPass\":" + JsonUtil.quote(captchaPass);
            sendJson(ex, 429, "{\"ok\":false,\"error\":\"rate_limited\"" + extra + "}");
            return;
        }

        prewarmExternalMediaCache(message);
        ChatMessage msg = new ChatMessage(System.currentTimeMillis(), "guest", guestName, "GUEST", message)
                .withGameMessage(gameMessage);
        attachReplyIfPresent(msg, replyToId, replyToSender, replyToPreview);
        prepareServerRelay(msg);
        adminDiscordAlerts.inspect(msg, AdminDiscordAlertManager.Scope.PUBLIC);
        if (config.broadcastWebChatToWeb) {
            addHistory(msg);
            broadcast(msg);
            dispatchWebPushChat(msg);
        }
        String extra = captchaPass == null ? "" : ",\"captchaPass\":" + JsonUtil.quote(captchaPass);
        sendJson(ex, 200, "{\"ok\":true" + extra + "}");
        sendToGame(msg, config.webGuestToGameFormat);
        host.discord().sendWebMessage(msg);
        publishServerRelay(msg);
    }


    private void handleCommands(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        SessionContext ctx = sessionFromRequest(ex);
        Role minRole = config.commandsMinRole == null ? Role.ADMIN : config.commandsMinRole;
        boolean canRun = config.commandsEnabled && ctx != null && ctx.account.role.atLeast(minRole);

        List<String> presets = new ArrayList<>();
        if (canRun && config.commandPresets != null) {
            for (ConfigValues.CommandPreset preset : config.commandPresets) {
                if (preset == null || !preset.enabled || preset.id.isBlank() || preset.command.isBlank()) continue;
                presets.add(commandPresetJson(preset));
            }
        }

        sendJson(ex, 200, "{\"ok\":true"
                + ",\"enabled\":" + config.commandsEnabled
                + ",\"canRun\":" + canRun
                + ",\"allowAll\":" + config.commandsAllowAll
                + ",\"minRole\":" + JsonUtil.quote(minRole.name())
                + ",\"showButton\":" + config.commandsShowButton
                + ",\"showSlashPanel\":" + config.commandsShowSlashPanel
                + ",\"runFromChatInput\":" + config.commandsRunFromChatInput
                + ",\"requireConfirm\":" + config.commandsRequireConfirm
                + ",\"maxLength\":" + config.commandsMaxLength
                + ",\"presets\":[" + String.join(",", presets) + "]}");
    }

    private void handleCommandRun(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        if (!config.commandsEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"commands_disabled\"}");
            return;
        }

        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        String token = bearerToken(ex);
        if (token.isBlank()) token = String.valueOf(body.getOrDefault("token", "")).trim();
        if (token.isBlank()) token = String.valueOf(JsonUtil.parseQuery(ex.getRequestURI().getRawQuery()).getOrDefault("token", "")).trim();
        SessionContext ctx = sessionForRequest(ex, token);
        Role minRole = config.commandsMinRole == null ? Role.ADMIN : config.commandsMinRole;
        if (ctx == null || !ctx.account.role.atLeast(minRole)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }

        String id = String.valueOf(body.getOrDefault("id", "")).trim();
        String command;
        String resultLabel;
        ConfigValues.CommandPreset preset = null;

        if (config.commandsAllowAll && body.containsKey("command")) {
            command = String.valueOf(body.getOrDefault("command", "")).trim();
            resultLabel = command;
        } else {
            preset = findCommandPreset(id, config.commandPresets);
            if (preset == null) {
                sendJson(ex, 404, "{\"ok\":false,\"error\":\"command_not_found\"}");
                return;
            }
            command = preset.command == null ? "" : preset.command.trim();
            resultLabel = preset.label;
        }

        if (command.startsWith("/")) command = command.substring(1).trim();
        if (config.commandsMaxLength > 0 && command.length() > config.commandsMaxLength) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"command_too_long\",\"maxLength\":" + config.commandsMaxLength + "}");
            return;
        }
        if (command.isBlank() || command.contains("\n") || command.contains("\r") || command.indexOf('\0') >= 0) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_command\"}");
            return;
        }

        final String finalCommand = command;
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        platform.runMainThread(() -> {
            try {
                future.complete(platform.dispatchConsoleCommand(finalCommand));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        boolean accepted;
        try {
            accepted = future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            audit(ctx, "command.run", Map.of("command", finalCommand, "result", "timeout"));
            sendJson(ex, 202, "{\"ok\":true,\"submitted\":true,\"timeout\":true}");
            return;
        } catch (Exception err) {
            host.logger().warn("Web command failed: " + err.getMessage());
            sendJson(ex, 500, "{\"ok\":false,\"error\":\"command_failed\"}");
            return;
        }

        audit(ctx, "command.run", Map.of("command", finalCommand, "label", resultLabel == null ? "" : resultLabel, "accepted", accepted));

        if (config.commandsBroadcastToWebChat) {
            String executor = commandExecutorLabel(ctx.account);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("label", resultLabel);
            values.put("executor", executor);
            String message = host.language().text("system.command-executed-by",
                    executor + " executed web command: " + resultLabel, values);
            publishSystemEvent("Command", message, "system.command-executed-by", JsonUtil.obj(values));
        }

        sendJson(ex, 200, "{\"ok\":true,\"accepted\":" + accepted
                + ",\"label\":" + JsonUtil.quote(resultLabel)
                + ",\"id\":" + JsonUtil.quote(preset == null ? "" : preset.id) + "}");
    }

    private ConfigValues.CommandPreset findCommandPreset(String id, List<ConfigValues.CommandPreset> presets) {
        if (id == null || presets == null) return null;
        for (ConfigValues.CommandPreset preset : presets) {
            if (preset != null && preset.enabled && id.equals(preset.id)) return preset;
        }
        return null;
    }

    private String commandExecutorLabel(Account account) {
        if (account == null) return "Unknown";
        String display = stripControl(host.displayNameForAccount(account), 64).trim();
        String username = stripControl(account.safeUsername(), 64).trim();
        if (display.isBlank()) display = username;
        if (display.isBlank()) display = "Unknown";
        if (!username.isBlank() && !username.equals(display)) {
            return display + " (" + username + ")";
        }
        return display;
    }


    private String commandPresetJson(ConfigValues.CommandPreset preset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", preset.id);
        m.put("label", preset.label);
        m.put("description", preset.description);
        m.put("command", preset.command);
        m.put("confirm", preset.requireConfirm);
        return JsonUtil.obj(m);
    }

    private void handleUpload(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        ConfigValues config = host.configValues();

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        if (!config.uploadEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"upload_disabled\"}");
            return;
        }

        long maxBytes = config.uploadMaxFileSizeMb > 0 ? config.uploadMaxFileSizeMb * 1024L * 1024L : 0L;
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        String boundary = multipartBoundary(contentType);
        if (boundary == null || boundary.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"multipart_required\"}");
            return;
        }

        long contentLength = parseLong(ex.getRequestHeaders().getFirst("Content-Length"), -1);
        if (maxBytes > 0 && contentLength > 0 && contentLength > maxBytes + 1024L * 1024L) {
            sendJson(ex, 413, "{\"ok\":false,\"error\":\"file_too_large\"}");
            return;
        }

        byte[] body;
        try {
            body = readLimitedBytes(ex.getRequestBody(), maxBytes > 0 ? maxBytes + 1024L * 1024L : 0L);
        } catch (UploadTooLargeException tooLarge) {
            sendJson(ex, 413, "{\"ok\":false,\"error\":\"file_too_large\"}");
            return;
        }

        MultipartData multipart = parseMultipart(body, boundary);
        UploadedPart file = multipart.file;
        if (file == null || file.data == null || file.data.length == 0) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"file_missing\"}");
            return;
        }
        if (maxBytes > 0 && file.data.length > maxBytes) {
            sendJson(ex, 413, "{\"ok\":false,\"error\":\"file_too_large\"}");
            return;
        }

        String ip = remoteIp(ex);
        if (!rateLimiter.allow("upload:" + ip, config.uploadCooldownSeconds, config.uploadMaxUploadsPerMinute)) {
            sendJson(ex, 429, "{\"ok\":false,\"error\":\"rate_limited\"}");
            return;
        }

        SessionContext ctx = sessionForRequest(ex, multipart.fields.get("token"));
        if (!canUpload(ctx, config)) {
            if (ctx == null && config.uploadAllowGuest && !config.guestEnabled) {
                sendJson(ex, 403, "{\"ok\":false,\"error\":\"guest_disabled\"}");
            } else {
                sendJson(ex, 403, "{\"ok\":false,\"error\":\"upload_permission_denied\"}");
            }
            return;
        }

        String original = sanitizeFileName(file.filename);
        String ext = extension(original);
        if (!isAllowedUploadExtension(ext, config)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"extension_not_allowed\"}");
            return;
        }

        cleanupOldUploads();

        Path dir = uploadDir();
        Files.createDirectories(dir);
        Path target;
        synchronized (uploadQuotaLock) {
            if (!ensureUploadQuotaAvailable(dir, file.data.length, config)) {
                sendJson(ex, 507, "{\"ok\":false,\"error\":\"upload_storage_quota_exceeded\"}");
                return;
            }
            try {
                target = writeUploadWithNamePolicy(dir, original, ext, file.data, config);
            } catch (IOException io) {
                sendJson(ex, 500, "{\"ok\":false,\"error\":" + JsonUtil.quote(io.getMessage()) + "}");
                return;
            }
        }
        String stored = target.getFileName().toString();
        String url = publicUploadBaseUrl(ex) + "/" + urlPath(stored);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("url", url);
        res.put("filename", original);
        res.put("storedName", stored);
        res.put("size", file.data.length);
        res.put("extension", ext);
        res.put("mediaType", uploadMediaType(ext));
        sendJson(ex, 200, JsonUtil.obj(res));
    }



    private Path emojiDir() {
        String dir = host.configValues().emojiDirectory;
        if (dir == null || dir.isBlank()) dir = "emojis";
        Path path = Path.of(dir);
        if (!path.isAbsolute()) path = host.dataDirectory().resolve(path);
        return path.normalize();
    }


    private void ensureEmojiDirectoryExists() throws IOException {
        ConfigValues config = host.configValues();
        if (config == null || !config.emojiEnabled) return;
        Files.createDirectories(emojiDir());
    }

    private Path emojiPackDir(String packId) {
        Path dir = emojiDir();
        String pack = sanitizeEmojiSegment(packId);
        if (pack.isBlank() || "default".equalsIgnoreCase(pack)) return dir;
        return dir.resolve(pack).normalize();
    }

    private String normalizeEmojiPackId(String packId) {
        String pack = sanitizeEmojiSegment(packId);
        return pack.isBlank() ? "default" : pack;
    }

    private boolean validEmojiPackTarget(Path dir, Path target) {
        return target != null && target.normalize().startsWith(dir.normalize());
    }

    private long emojiTotalSize(ConfigValues config) {
        Path dir = emojiDir();
        if (!Files.isDirectory(dir)) return 0L;
        final long[] total = new long[]{0L};
        try (java.util.stream.Stream<Path> stream = Files.walk(dir, 2)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String ext = extension(path.getFileName().toString()).toLowerCase(Locale.ROOT);
                if (!emojiExtensionAllowed(ext, config)) return;
                try { total[0] += Files.size(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
        return total[0];
    }

    private String uniqueEmojiFilename(Path dir, String base, String ext) {
        String safeBase = sanitizeEmojiFilenameBase(base);
        if (safeBase.isBlank()) safeBase = "emoji";
        safeBase = limitCodePoints(safeBase, 80);
        String suffix = "." + ext.toLowerCase(Locale.ROOT);
        Path candidate = dir.resolve(safeBase + suffix).normalize();
        if (!Files.exists(candidate)) return safeBase + suffix;
        for (int i = 1; i < 10000; i++) {
            String name = safeBase + "-" + i + suffix;
            if (!Files.exists(dir.resolve(name).normalize())) return name;
        }
        return safeBase + "-" + System.currentTimeMillis() + suffix;
    }

    private String emojiRenameFilename(String requested, String currentExt) {
        String cleaned = safeUploadedFilename(requested, "emoji");
        String ext = extension(cleaned).toLowerCase(Locale.ROOT);
        String base = cleaned;
        if (!ext.isBlank() && ext.equalsIgnoreCase(currentExt)) {
            base = cleaned.substring(0, cleaned.lastIndexOf('.'));
        } else if (!ext.isBlank() && emojiExtensionAllowed(ext, host.configValues())) {
            // Renaming changes the file name only; changing the actual file format
            // extension is intentionally not supported from the admin panel.
            return "";
        }
        String safeBase = sanitizeEmojiFilenameBase(base);
        if (safeBase.isBlank()) return "";
        return safeBase + "." + currentExt.toLowerCase(Locale.ROOT);
    }


    private void handleEmojis(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        if (!config.emojiEnabled) {
            sendJson(ex, 200, "{\"ok\":true,\"enabled\":false,\"packs\":[],\"items\":[]}");
            return;
        }

        String path = ex.getRequestURI().getPath();
        String prefix = matchingApiContextPrefix(config, path) + "/emojis";
        if (path.equals(prefix) || path.equals(prefix + "/")) {
            handleEmojiList(ex, config);
            return;
        }
        handleEmojiFile(ex, config, prefix, path);
    }

    private void handleEmojiList(HttpExchange ex, ConfigValues config) throws IOException {
        EmojiCatalog catalog = scanEmojiCatalog(config);
        List<Object> packObjects = new ArrayList<>();
        for (EmojiPack pack : catalog.packs) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", pack.id);
            pm.put("label", pack.label);
            pm.put("count", pack.items.size());
            packObjects.add(pm);
        }
        String emojiBaseUrl = publicEmojiBaseUrl(ex);
        List<Object> itemObjects = new ArrayList<>();
        for (EmojiItem item : catalog.items) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", item.id);
            im.put("pack", item.pack);
            im.put("name", item.name);
            im.put("label", item.label);
            im.put("path", item.relativePath);
            im.put("filename", Path.of(item.relativePath).getFileName().toString());
            im.put("url", emojiBaseUrl + "/" + urlPath(item.relativePath));
            im.put("aliases", emojiPublicAliases(item, config));
            im.put("ext", item.ext);
            im.put("size", item.size);
            im.put("animated", "gif".equalsIgnoreCase(item.ext));
            itemObjects.add(im);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("enabled", true);
        res.put("renderSizePx", config.emojiRenderSizePx);
        res.put("pickerSizePx", config.emojiPickerSizePx);
        res.put("messageTokenLimit", config.emojiMessageTokenLimit);
        res.put("tokenFormat", config.emojiTokenFormat == null ? "short" : config.emojiTokenFormat);
        res.put("packs", packObjects);
        res.put("items", itemObjects);
        sendJson(ex, 200, JsonUtil.obj(res));
    }

    private List<String> emojiPublicAliases(EmojiItem item, ConfigValues config) {
        if (item == null) return List.of();
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String alias : new String[]{
                item.id,
                item.name,
                item.label,
                emojiTokenFallbackLabel(item.id),
                emojiGameLabel(item, config),
                item.pack == null || item.pack.isBlank() ? "" : item.pack + "/" + item.name,
                item.pack == null || item.pack.isBlank() ? "" : item.pack + "/" + item.label
        }) {
            for (String key : emojiAliasKeys(alias)) {
                if (!key.isBlank()) aliases.add(key);
            }
        }
        return new ArrayList<>(aliases);
    }

    private void handleEmojiFile(HttpExchange ex, ConfigValues config, String prefix, String path) throws IOException {
        String raw = path.startsWith(prefix + "/") ? path.substring((prefix + "/").length()) : "";
        String name = URLDecoder.decode(raw, StandardCharsets.UTF_8).replace("\\", "/");
        if (name.isBlank() || name.startsWith("/") || name.contains("..") || name.contains("\0")) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }
        String ext = extension(name).toLowerCase(Locale.ROOT);
        if (!emojiExtensionAllowed(ext, config)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }
        Path dir = emojiDir();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.exists(file) || !Files.isRegularFile(file)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        long len = Files.size(file);
        long max = config.emojiMaxFileSizeKb > 0 ? config.emojiMaxFileSizeKb * 1024L : 0L;
        if (max > 0 && len > max) {
            sendJson(ex, 413, "{\"ok\":false,\"error\":\"file_too_large\"}");
            return;
        }

        Headers h = ex.getResponseHeaders();
        addCors(ex);
        addSecurityHeaders(ex);
        h.set("Content-Type", contentTypeForExtension(ext));
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Cache-Control", "public, max-age=604800");
        setInlineContentDisposition(h, Path.of(name).getFileName().toString());
        h.set("Content-Length", String.valueOf(len));

        boolean head = "HEAD".equalsIgnoreCase(ex.getRequestMethod());
        ex.sendResponseHeaders(200, head ? -1 : len);
        if (!head) {
            try (OutputStream os = ex.getResponseBody()) {
                Files.copy(file, os);
            }
        } else {
            ex.close();
        }
    }


    private void handleShortEmoji(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (!config.emojiEnabled) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        String path = ex.getRequestURI().getPath();
        String prefix = matchingApiContextPrefix(config, path) + "/e/";
        if (!path.startsWith(prefix)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        String id = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        if (!id.matches("[0-9a-f]{8}")) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        EmojiItem found = null;
        for (EmojiItem item : scanEmojiCatalog(config).items) {
            if (id.equals(shortEmojiId(item.id))) {
                found = item;
                break;
            }
        }
        if (found == null) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        sendEmojiItemFile(ex, config, found);
    }

    private void sendEmojiItemFile(HttpExchange ex, ConfigValues config, EmojiItem item) throws IOException {
        Path dir = emojiDir();
        Path file = dir.resolve(item.relativePath).normalize();
        if (!file.startsWith(dir) || !Files.exists(file) || !Files.isRegularFile(file)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        String ext = item.ext.toLowerCase(Locale.ROOT);
        if (!emojiExtensionAllowed(ext, config)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }
        long len = Files.size(file);
        long max = config.emojiMaxFileSizeKb > 0 ? config.emojiMaxFileSizeKb * 1024L : 0L;
        if (max > 0 && len > max) {
            sendJson(ex, 413, "{\"ok\":false,\"error\":\"file_too_large\"}");
            return;
        }

        Headers h = ex.getResponseHeaders();
        addCors(ex);
        addSecurityHeaders(ex);
        h.set("Content-Type", contentTypeForExtension(ext));
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Cache-Control", "public, max-age=604800");
        setInlineContentDisposition(h, Path.of(item.relativePath).getFileName().toString());
        h.set("Content-Length", String.valueOf(len));

        boolean head = "HEAD".equalsIgnoreCase(ex.getRequestMethod());
        ex.sendResponseHeaders(200, head ? -1 : len);
        if (!head) {
            try (OutputStream os = ex.getResponseBody()) {
                Files.copy(file, os);
            }
        } else {
            ex.close();
        }
    }

    private EmojiCatalog scanEmojiCatalog(ConfigValues config) {
        String signature = emojiCatalogSignature(config);
        EmojiCatalog cached = cachedEmojiCatalog;
        if (cached != null && cachedEmojiCatalogSignature.equals(signature)) return cached;
        synchronized (this) {
            cached = cachedEmojiCatalog;
            if (cached != null && cachedEmojiCatalogSignature.equals(signature)) return cached;
            EmojiCatalog fresh = scanEmojiCatalogFresh(config);
            Map<String,String> aliases = emojiAliasToWebId(fresh, config);
            LinkedHashSet<String> protectedAliases = new LinkedHashSet<>();
            for (String alias : aliases.keySet()) {
                if (alias == null || alias.isBlank()) continue;
                String clean = alias.trim();
                if (clean.startsWith(":") && clean.endsWith(":") && clean.length() > 2) clean = clean.substring(1, clean.length() - 1).trim();
                if (clean.startsWith("emoji:") && clean.length() > 6) clean = clean.substring(6).trim();
                if (!clean.isBlank()) {
                    protectedAliases.add(clean);
                    protectedAliases.add(clean.toLowerCase(Locale.ROOT));
                }
            }
            cachedEmojiCatalog = fresh;
            cachedEmojiCatalogStamp = System.nanoTime();
            cachedEmojiCatalogSignature = signature;
            cachedContentFilterEmojiAliases = Collections.unmodifiableSet(protectedAliases);
            return fresh;
        }
    }

    private void invalidateEmojiCatalog() {
        cachedEmojiCatalog = null;
        cachedEmojiCatalogStamp = Long.MIN_VALUE;
        cachedEmojiCatalogSignature = "";
        cachedContentFilterEmojiAliases = Set.of();
    }

    private long emojiDirectoryStamp(Path dir) {
        if (dir == null || !Files.exists(dir)) return -1L;
        long stamp = 17L;
        try (java.util.stream.Stream<Path> stream = Files.walk(dir, 2)) {
            for (Path path : stream.toList()) {
                try {
                    stamp = 31L * stamp + path.toString().hashCode();
                    stamp = 31L * stamp + Files.getLastModifiedTime(path).toMillis();
                    if (Files.isRegularFile(path)) stamp = 31L * stamp + Files.size(path);
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
        return stamp;
    }

    private String emojiCatalogSignature(ConfigValues c) {
        if (c == null) return "";
        return String.valueOf(c.emojiEnabled) + '|' + c.emojiMaxFileSizeKb + '|' + c.emojiMaxTotalSizeMb + '|'
                + String.valueOf(c.emojiAllowedExtensions) + '|' + String.valueOf(c.emojiGameLinkDefaultPack) + '|'
                + String.valueOf(c.emojiGameLinkAliases);
    }

    private EmojiCatalog scanEmojiCatalogFresh(ConfigValues config) {
        EmojiCatalog catalog = new EmojiCatalog();
        Path dir = emojiDir();
        if (!Files.isDirectory(dir)) return catalog;

        long maxOne = config.emojiMaxFileSizeKb > 0 ? config.emojiMaxFileSizeKb * 1024L : 0L;
        long maxTotal = config.emojiMaxTotalSizeMb > 0 ? config.emojiMaxTotalSizeMb * 1024L * 1024L : 0L;
        long[] total = new long[]{0L};

        try {
            // Root files are individual/default emojis.
            EmojiPack rootPack = scanEmojiPack(config, dir, "default", "Default", "", maxOne, maxTotal, total);
            if (!rootPack.items.isEmpty()) {
                catalog.packs.add(rootPack);
                catalog.items.addAll(rootPack.items);
            }

            List<Path> packs = new ArrayList<>();
            try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isDirectory).forEach(packs::add);
            }
            packs.sort((a, b) -> compareNatural(a.getFileName().toString(), b.getFileName().toString()));
            for (Path packDir : packs) {
                String rawName = packDir.getFileName().toString();
                String packId = sanitizeEmojiSegment(rawName);
                if (packId.isBlank()) continue;
                EmojiPack pack = scanEmojiPack(config, packDir, packId, rawName, packId + "/", maxOne, maxTotal, total);
                if (!pack.items.isEmpty()) {
                    catalog.packs.add(pack);
                    catalog.items.addAll(pack.items);
                }
            }
        } catch (IOException ignored) {
        }
        return catalog;
    }

    private int emojiWebPreferenceRank(String ext) {
        String e = String.valueOf(ext == null ? "" : ext).replace(".", "").trim().toLowerCase(Locale.ROOT);
        if (e.equals("gif")) return 0;
        if (e.equals("webp")) return 1;
        if (e.equals("jpg") || e.equals("jpeg")) return 2;
        if (e.equals("png")) return 3;
        return 9;
    }

    private boolean shouldReplaceEmojiCatalogChoice(Path current, Path candidate) {
        String currentExt = extension(current.getFileName().toString()).toLowerCase(Locale.ROOT);
        String candidateExt = extension(candidate.getFileName().toString()).toLowerCase(Locale.ROOT);
        int currentRank = emojiWebPreferenceRank(currentExt);
        int candidateRank = emojiWebPreferenceRank(candidateExt);
        if (candidateRank != currentRank) return candidateRank < currentRank;
        return compareNatural(candidate.getFileName().toString(), current.getFileName().toString()) < 0;
    }

    private String uniqueEmojiBase(Path packDir, String desiredBase) {
        String clean = sanitizeEmojiFilenameBase(desiredBase);
        if (clean.isBlank()) clean = "emoji";
        String base = clean;
        int i = 1;
        while (emojiBaseExists(packDir, base)) {
            base = clean + "-" + i++;
        }
        return base;
    }

    private boolean emojiBaseExists(Path packDir, String base) {
        if (packDir == null || base == null || base.isBlank()) return false;
        String prefix = base + ".";
        try (java.util.stream.Stream<Path> stream = Files.list(packDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName() == null ? "" : p.getFileName().toString())
                    .anyMatch(name -> name.equals(base) || name.startsWith(prefix));
        } catch (IOException ignored) {
            return false;
        }
    }

    private EmojiPack scanEmojiPack(ConfigValues config, Path packDir, String packId, String label, String relativePrefix, long maxOne, long maxTotal, long[] total) throws IOException {
        EmojiPack pack = new EmojiPack(packId, label);
        Map<String, Path> chosenByBase = new LinkedHashMap<>();
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(packDir)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort((a, b) -> compareNatural(a.getFileName().toString(), b.getFileName().toString()));

        for (Path file : files) {
            String fileName = file.getFileName().toString();
            String ext = extension(fileName).toLowerCase(Locale.ROOT);
            if (!emojiExtensionAllowed(ext, config)) continue;
            String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            String itemName = sanitizeEmojiSegment(base);
            if (itemName.isBlank()) continue;

            Path current = chosenByBase.get(itemName);
            if (current == null || shouldReplaceEmojiCatalogChoice(current, file)) {
                chosenByBase.put(itemName, file);
            }
        }

        for (Path file : chosenByBase.values()) {
            String fileName = file.getFileName().toString();
            String ext = extension(fileName).toLowerCase(Locale.ROOT);
            long len;
            try { len = Files.size(file); } catch (IOException e) { continue; }
            if (maxOne > 0 && len > maxOne) continue;
            if (maxTotal > 0 && total[0] + len > maxTotal) continue;
            String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            String itemName = sanitizeEmojiSegment(base);
            if (itemName.isBlank()) continue;
            String id = packId + "/" + itemName;
            String rel = relativePrefix + fileName;
            EmojiItem item = new EmojiItem(id, packId, itemName, base, rel, ext, len);
            pack.items.add(item);
            total[0] += len;
        }
        return pack;
    }

    private boolean emojiExtensionAllowed(String ext, ConfigValues config) {
        if (ext == null || ext.isBlank()) return false;
        if (config.emojiAllowedExtensions == null || config.emojiAllowedExtensions.isEmpty()) {
            return Set.of("png", "jpg", "jpeg", "gif", "webp").contains(ext.toLowerCase(Locale.ROOT));
        }
        String e = ext.toLowerCase(Locale.ROOT);
        for (String allowed : config.emojiAllowedExtensions) {
            if (e.equals(String.valueOf(allowed).replace(".", "").trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean emojiNeedsGamePng(String ext) {
        String e = String.valueOf(ext == null ? "" : ext).replace(".", "").trim().toLowerCase(Locale.ROOT);
        return e.equals("png") || e.equals("gif") || e.equals("jpg") || e.equals("jpeg") || e.equals("webp");
    }
    private boolean gameLinkPngSidecarActive(ConfigValues config) {
        // PNG sidecars are a generic compatibility helper for external game-side
        // emoji plugins that can only read PNG files. They are intentionally
        // independent from emoji.game-link.enabled: ImageEmojis-compatible token
        // preserving mode normally keeps game-link disabled, but still needs
        // PNG sidecars for GIF/JPG/WEBP uploads.
        return config != null && config.emojiEnabled;
    }

    private boolean emojiHasPngSidecarSource(String ext) {
        String e = String.valueOf(ext == null ? "" : ext).replace(".", "").trim().toLowerCase(Locale.ROOT);
        return e.equals("gif") || e.equals("jpg") || e.equals("jpeg") || e.equals("webp");
    }

    private void ensureImageIoPluginsRegistered() {
        if (imageIoPluginsRegistered) return;
        synchronized (WebChatServer.class) {
            if (imageIoPluginsRegistered) return;
            ClassLoader original = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(WebChatServer.class.getClassLoader());
                ImageIO.scanForPlugins();
                imageIoPluginsRegistered = true;
            } catch (Throwable t) {
                host.logger().warn("Failed to scan ImageIO plugins: " + t.getMessage());
            } finally {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    private byte[] convertImageBytesToPng(byte[] data) throws IOException {
        if (data == null || data.length == 0) return null;
        ensureImageIoPluginsRegistered();
        BufferedImage image;
        try (InputStream in = new java.io.ByteArrayInputStream(data)) {
            image = ImageIO.read(in);
        }
        if (image == null) return null;
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(out, "png", baos)) return null;
        return baos.toByteArray();
    }

    private Path emojiPngSidecarPath(Path originalFile) {
        if (originalFile == null) return null;
        Path root = emojiDir();
        Path normalized = originalFile.normalize();
        if (!normalized.startsWith(root)) return null;
        String fileName = normalized.getFileName() == null ? "" : normalized.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot >= 0 ? fileName.substring(0, dot) : fileName;
        if (base.isBlank() || normalized.getParent() == null) return null;
        Path sidecar = normalized.getParent().resolve(base + ".png").normalize();
        return sidecar.startsWith(root) ? sidecar : null;
    }

    private boolean createPngSidecarForFile(Path originalFile, byte[] sourceData, boolean overwrite) {
        if (originalFile == null) return false;
        String ext = extension(originalFile.getFileName() == null ? "" : originalFile.getFileName().toString()).toLowerCase(Locale.ROOT);
        if (!emojiHasPngSidecarSource(ext)) return false;
        Path sidecar = emojiPngSidecarPath(originalFile);
        if (sidecar == null) return false;
        try {
            if (Files.exists(sidecar) && !overwrite) return true;
            byte[] input = sourceData != null ? sourceData : Files.readAllBytes(originalFile);
            byte[] pngData = convertImageBytesToPng(input);
            if (pngData == null || pngData.length == 0) return false;
            Files.write(sidecar, pngData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return true;
        } catch (Exception ex) {
            host.logger().warn("Failed to create PNG sidecar for emoji " + originalFile + ": " + ex.getMessage());
            return false;
        }
    }

    private void syncExistingGameEmojiPngSidecarsOnStartup() {
        ConfigValues config = host.configValues();
        if (!gameLinkPngSidecarActive(config)) return;
        Path root = emojiDir();
        if (!Files.isDirectory(root)) return;
        int created = 0;
        int already = 0;
        int failed = 0;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String ext = extension(file.getFileName() == null ? "" : file.getFileName().toString()).toLowerCase(Locale.ROOT);
                if (!emojiHasPngSidecarSource(ext)) continue;
                Path sidecar = emojiPngSidecarPath(file);
                if (sidecar != null && Files.isRegularFile(sidecar)) {
                    already++;
                    continue;
                }
                if (createPngSidecarForFile(file, null, false)) created++;
                else failed++;
            }
        } catch (IOException ex) {
            host.logger().warn("Failed to scan emoji directory for PNG sidecars: " + ex.getMessage());
        }
        host.logger().info("Game emoji PNG sidecar sync complete: created " + created
                + ", already present " + already + ", failed " + failed + ".");
    }

    private String sanitizeEmojiSegment(String value) {
        String raw = java.text.Normalizer.normalize(String.valueOf(value == null ? "" : value), java.text.Normalizer.Form.NFC).trim();
        if (raw.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        boolean lastSpace = false;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '/' || cp == '\\' || cp == ':' || cp == 0 || Character.isISOControl(cp)) {
                if (!lastSpace) {
                    out.append('-');
                    lastSpace = true;
                }
                continue;
            }
            if (Character.isWhitespace(cp)) {
                if (!lastSpace) {
                    out.append(' ');
                    lastSpace = true;
                }
                continue;
            }
            out.appendCodePoint(cp);
            lastSpace = false;
        }
        String result = out.toString().trim();
        while (result.startsWith("-")) result = result.substring(1).trim();
        while (result.endsWith("-")) result = result.substring(0, result.length() - 1).trim();
        return limitCodePoints(result, 96);
    }

    private String sanitizeEmojiFilenameBase(String value) {
        String raw = java.text.Normalizer.normalize(String.valueOf(value == null ? "" : value), java.text.Normalizer.Form.NFC).trim();
        if (raw.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        boolean lastSpace = false;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            boolean invalidPathChar = cp == '/' || cp == '\\' || cp == ':' || cp == '*' || cp == '?' || cp == '"' || cp == '<' || cp == '>' || cp == '|' || cp == 0 || Character.isISOControl(cp);
            if (invalidPathChar) {
                if (!lastSpace) {
                    out.append('-');
                    lastSpace = true;
                }
                continue;
            }
            if (Character.isWhitespace(cp)) {
                if (!lastSpace) {
                    out.append(' ');
                    lastSpace = true;
                }
                continue;
            }
            out.appendCodePoint(cp);
            lastSpace = false;
        }
        String result = out.toString().trim();
        while (result.startsWith("-")) result = result.substring(1).trim();
        while (result.endsWith("-")) result = result.substring(0, result.length() - 1).trim();
        return limitCodePoints(result, 80);
    }

    private int compareNatural(String a, String b) {
        String aa = String.valueOf(a == null ? "" : a);
        String bb = String.valueOf(b == null ? "" : b);
        int ia = 0, ib = 0;
        while (ia < aa.length() && ib < bb.length()) {
            int ca = aa.codePointAt(ia);
            int cb = bb.codePointAt(ib);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int sa = ia;
                int sb = ib;
                while (ia < aa.length() && Character.isDigit(aa.codePointAt(ia))) ia += Character.charCount(aa.codePointAt(ia));
                while (ib < bb.length() && Character.isDigit(bb.codePointAt(ib))) ib += Character.charCount(bb.codePointAt(ib));
                String na = aa.substring(sa, ia).replaceFirst("^0+(?!$)", "");
                String nb = bb.substring(sb, ib).replaceFirst("^0+(?!$)", "");
                int len = Integer.compare(na.length(), nb.length());
                if (len != 0) return len;
                int cmp = na.compareTo(nb);
                if (cmp != 0) return cmp;
                continue;
            }
            String la = new String(Character.toChars(ca)).toLowerCase(Locale.ROOT);
            String lb = new String(Character.toChars(cb)).toLowerCase(Locale.ROOT);
            int cmp = la.compareTo(lb);
            if (cmp != 0) return cmp;
            ia += Character.charCount(ca);
            ib += Character.charCount(cb);
        }
        return Integer.compare(aa.length(), bb.length());
    }

    private String limitCodePoints(String value, int maxCodePoints) {
        String text = String.valueOf(value == null ? "" : value);
        if (maxCodePoints <= 0 || text.codePointCount(0, text.length()) <= maxCodePoints) return text;
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints));
    }

    private static void setInlineContentDisposition(Headers headers, String filename) {
        setContentDisposition(headers, "inline", filename);
    }

    private static void setContentDisposition(Headers headers, String disposition, String filename) {
        String safeDisposition = "attachment".equalsIgnoreCase(String.valueOf(disposition)) ? "attachment" : "inline";
        String original = java.text.Normalizer.normalize(String.valueOf(filename == null ? "file" : filename), java.text.Normalizer.Form.NFC);
        original = original.replace("\r", "_").replace("\n", "_").replace("\0", "_");
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < original.length();) {
            int cp = original.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x20 && cp <= 0x7e && cp != '"' && cp != '\\' && cp != ';') ascii.appendCodePoint(cp);
            else ascii.append('_');
        }
        String fallback = ascii.toString().trim();
        if (fallback.isBlank()) fallback = "file";
        String encoded = java.net.URLEncoder.encode(original, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("%7E", "~");
        headers.set("Content-Disposition", safeDisposition + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded);
    }

    private String urlPath(String path) {
        return Arrays.stream(String.valueOf(path == null ? "" : path).replace("\\", "/").split("/"))
                .filter(part -> !part.isBlank())
                .map(part -> java.net.URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~"))
                .reduce((a, b) -> a + "/" + b)
                .orElse("");
    }

    private static final class EmojiCatalog {
        final List<EmojiPack> packs = new ArrayList<>();
        final List<EmojiItem> items = new ArrayList<>();
    }

    private static final class EmojiPack {
        final String id;
        final String label;
        final List<EmojiItem> items = new ArrayList<>();
        EmojiPack(String id, String label) {
            this.id = id == null ? "" : id;
            this.label = label == null ? this.id : label;
        }
    }

    private static final class EmojiItem {
        final String id;
        final String pack;
        final String name;
        final String label;
        final String relativePath;
        final String ext;
        final long size;
        EmojiItem(String id, String pack, String name, String label, String relativePath, String ext, long size) {
            this.id = id == null ? "" : id;
            this.pack = pack == null ? "" : pack;
            this.name = name == null ? "" : name;
            this.label = label == null ? this.name : label;
            this.relativePath = relativePath == null ? "" : relativePath;
            this.ext = ext == null ? "" : ext;
            this.size = size;
        }
    }

    private Path webFontDir() {
        String dir = host.configValues().webFontsDirectory;
        if (dir == null || dir.isBlank()) dir = "fonts";
        Path path = Path.of(dir);
        if (!path.isAbsolute()) path = host.dataDirectory().resolve(path);
        return path.normalize();
    }

    private void handleFontFile(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        if (!config.webFontsEnabled) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        String path = ex.getRequestURI().getPath();
        String prefix = matchingApiContextPrefix(config, path) + "/fonts/";
        if (!path.startsWith(prefix)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        String name = URLDecoder.decode(path.substring(prefix.length()), StandardCharsets.UTF_8).replace("\\", "/");
        if (name.isBlank() || name.startsWith("/") || name.contains("..") || name.contains("\0")) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }

        String ext = extension(name).toLowerCase(Locale.ROOT);
        if (!List.of("woff2", "woff", "ttf", "otf").contains(ext)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }

        Path dir = webFontDir();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.exists(file) || !Files.isRegularFile(file)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        long len = Files.size(file);
        boolean head = "HEAD".equalsIgnoreCase(ex.getRequestMethod());

        Headers h = ex.getResponseHeaders();
        addCors(ex);
        addSecurityHeaders(ex);
        h.set("Content-Type", contentTypeForFontExtension(ext));
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Cache-Control", "public, max-age=604800");
        h.set("Content-Length", String.valueOf(len));
        setContentDisposition(h, "inline", Path.of(name).getFileName().toString());

        ex.sendResponseHeaders(200, head ? -1 : len);
        if (!head) {
            try (OutputStream os = ex.getResponseBody()) {
                Files.copy(file, os);
            }
        } else {
            ex.close();
        }
    }

    private String contentTypeForFontExtension(String ext) {
        switch (ext.toLowerCase(Locale.ROOT)) {
            case "woff2": return "font/woff2";
            case "woff": return "font/woff";
            case "ttf": return "font/ttf";
            case "otf": return "font/otf";
            default: return "application/octet-stream";
        }
    }

    private void handleUploadedFile(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        if (!config.uploadEnabled) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        String path = ex.getRequestURI().getPath();
        String prefix = matchingApiContextPrefix(config, path) + "/uploads/";
        int idx = path.indexOf(prefix);
        if (idx < 0) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        // URI#getPath() is already percent-decoded. Do not run URLDecoder a
        // second time here: a literal '+' or a filename containing "%xx"
        // would otherwise be changed after it was safely stored.
        String name = path.substring(idx + prefix.length());
        if (!isSafeStoredUploadName(name)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }

        Path dir = uploadDir();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.exists(file) || !Files.isRegularFile(file)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        String ext = extension(name);
        long len = Files.size(file);

        Headers h = ex.getResponseHeaders();
        addCors(ex);
        addSecurityHeaders(ex);
        h.set("Content-Type", contentTypeForExtension(ext));
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Accept-Ranges", "bytes");
        h.set("Cache-Control", "public, max-age=3600");
        String disposition = isInlineUploadExtension(ext) ? "inline" : "attachment";
        setContentDisposition(h, disposition, name);

        ByteRange range = parseRange(ex.getRequestHeaders().getFirst("Range"), len);
        boolean head = "HEAD".equalsIgnoreCase(ex.getRequestMethod());

        if (range == ByteRange.INVALID) {
            h.set("Content-Range", "bytes */" + len);
            ex.sendResponseHeaders(416, -1);
            ex.close();
            return;
        }

        if (range != null) {
            long count = range.end - range.start + 1;
            h.set("Content-Range", "bytes " + range.start + "-" + range.end + "/" + len);
            h.set("Content-Length", String.valueOf(count));
            ex.sendResponseHeaders(206, head ? -1 : count);
            if (!head) {
                try (OutputStream os = ex.getResponseBody()) {
                    copyRange(file, os, range.start, count);
                }
            } else {
                ex.close();
            }
            return;
        }

        h.set("Content-Length", String.valueOf(len));
        ex.sendResponseHeaders(200, head ? -1 : len);
        if (!head) {
            try (OutputStream os = ex.getResponseBody()) {
                Files.copy(file, os);
            }
        } else {
            ex.close();
        }
    }

    private ByteRange parseRange(String header, long length) {
        if (header == null || header.isBlank()) return null;
        if (length <= 0) return ByteRange.INVALID;

        String value = header.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("bytes=")) return null;

        // Only support a single byte range. Browsers normally use one range for media seeking.
        String spec = value.substring("bytes=".length()).trim();
        int comma = spec.indexOf(',');
        if (comma >= 0) spec = spec.substring(0, comma).trim();

        int dash = spec.indexOf('-');
        if (dash < 0) return ByteRange.INVALID;

        try {
            String left = spec.substring(0, dash).trim();
            String right = spec.substring(dash + 1).trim();

            long start;
            long end;

            if (left.isEmpty()) {
                // Suffix range, e.g. bytes=-500
                long suffix = Long.parseLong(right);
                if (suffix <= 0) return ByteRange.INVALID;
                start = Math.max(0, length - suffix);
                end = length - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? length - 1 : Long.parseLong(right);
            }

            if (start < 0 || end < start || start >= length) return ByteRange.INVALID;
            end = Math.min(end, length - 1);
            return new ByteRange(start, end);
        } catch (RuntimeException ex) {
            return ByteRange.INVALID;
        }
    }

    private void copyRange(Path file, OutputStream os, long start, long count) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            long skipped = 0;
            while (skipped < start) {
                long s = in.skip(start - skipped);
                if (s <= 0) {
                    if (in.read() == -1) return;
                    s = 1;
                }
                skipped += s;
            }

            byte[] buffer = new byte[8192];
            long remaining = count;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                os.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static final class ByteRange {
        static final ByteRange INVALID = new ByteRange(-1, -1);

        final long start;
        final long end;

        ByteRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }


    private void handleExternalMedia(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod()) && !"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }

        ConfigValues config = host.configValues();
        if (!config.externalMediaCacheEnabled || !config.cacheDiscordCdn) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }

        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String url = q.get("url");
        if (url == null || url.isBlank() || !isDiscordCdnPreviewUrl(url)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_url\"}");
            return;
        }

        Path file = cacheDiscordCdnResource(url);
        if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_available\"}");
            return;
        }

        String ext = extension(file.getFileName().toString());
        long len = Files.size(file);
        boolean head = "HEAD".equalsIgnoreCase(ex.getRequestMethod());

        Headers h = ex.getResponseHeaders();
        addCors(ex);
        addSecurityHeaders(ex);
        h.set("Content-Type", contentTypeForExtension(ext));
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Accept-Ranges", "bytes");
        h.set("Cache-Control", "public, max-age=604800");
        setContentDisposition(h, "inline", file.getFileName().toString());

        ByteRange range = parseRange(ex.getRequestHeaders().getFirst("Range"), len);
        if (range == ByteRange.INVALID) {
            h.set("Content-Range", "bytes */" + len);
            ex.sendResponseHeaders(416, -1);
            ex.close();
            return;
        }

        if (range != null) {
            long count = range.end - range.start + 1;
            h.set("Content-Range", "bytes " + range.start + "-" + range.end + "/" + len);
            h.set("Content-Length", String.valueOf(count));
            ex.sendResponseHeaders(206, head ? -1 : count);
            if (!head) {
                try (OutputStream os = ex.getResponseBody()) {
                    copyRange(file, os, range.start, count);
                }
            } else {
                ex.close();
            }
            return;
        }

        h.set("Content-Length", String.valueOf(len));
        ex.sendResponseHeaders(200, head ? -1 : len);
        if (!head) {
            try (OutputStream os = ex.getResponseBody()) {
                Files.copy(file, os);
            }
        } else {
            ex.close();
        }
    }

    private void prewarmExternalMediaCache(String message) {
        ConfigValues config = host.configValues();
        if (!config.externalMediaCacheEnabled || !config.cacheDiscordCdn || message == null || message.isBlank()) return;
        if (httpServer == null) return;

        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(message);
        while (matcher.find() && urls.size() < 4) {
            String raw = matcher.group(1);
            String[] split = splitUrlTrailing(raw);
            String url = normalizeClickUrl(split[0]);
            if (isDiscordCdnPreviewUrl(url)) urls.add(url);
        }
        if (urls.isEmpty()) return;

        httpServer.submit(() -> {
            for (String url : urls) {
                try {
                    cacheDiscordCdnResource(url);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private Path externalMediaCacheDir() {
        String dir = host.configValues().externalMediaCacheDirectory;
        if (dir == null || dir.isBlank()) dir = "uploads/external-media-cache";
        Path path = Path.of(dir);
        if (!path.isAbsolute()) path = host.dataDirectory().resolve(path);
        return path.normalize();
    }

    private void cleanupOldExternalMediaCache() {
        ConfigValues config = host.configValues();
        if (!config.externalMediaCacheEnabled || config.externalMediaCacheRetentionDays <= 0) return;

        Path dir = externalMediaCacheDir();
        if (!Files.isDirectory(dir)) return;

        long cutoff = System.currentTimeMillis() - (config.externalMediaCacheRetentionDays * 24L * 60L * 60L * 1000L);
        Set<String> protectedPinnedExternal = config.pinnedPreserveUploads ? protectedPinnedExternalCacheNames() : Collections.emptySet();
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    if (protectedPinnedExternal.contains(file.getFileName().toString())) return;
                    if (Files.getLastModifiedTime(file).toMillis() < cutoff) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ex) {
            host.logger().warn("Failed to cleanup external media cache: " + ex.getMessage());
        }
    }

    private Path cacheDiscordCdnResource(String url) {
        ConfigValues config = host.configValues();
        if (!config.externalMediaCacheEnabled || !config.cacheDiscordCdn || !isDiscordCdnPreviewUrl(url)) return null;

        String ext = extensionFromUrlPath(url);
        if (ext.isBlank()) ext = "bin";
        ext = ext.toLowerCase(Locale.ROOT);

        // Hash by host+path, not the expiring Discord query string. This way, if Discord renews
        // ex/is/hm parameters, the same attachment maps to the same cached file.
        String cacheKey = discordCdnCacheKey(url);
        String name = "discord-" + SecurityUtil.sha256Hex(cacheKey).substring(0, 32) + "." + ext;

        Path dir = externalMediaCacheDir();
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir)) return null;
        if (Files.exists(target) && Files.isRegularFile(target)) return target;

        long maxBytes = config.externalMediaCacheMaxSizeMb > 0 ? config.externalMediaCacheMaxSizeMb * 1024L * 1024L : 0L;
        int timeoutMs = Math.max(1, config.externalMediaCacheTimeoutSeconds) * 1000;

        try {
            Files.createDirectories(dir);

            HttpURLConnection conn = openExternalMediaConnection(url, timeoutMs, 4);
            if (conn == null) {
                return null;
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }

            String type = conn.getContentType();
            if (type != null) type = type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);

            String typeExt = extensionFromContentType(type);
            if (type != null && !type.isBlank() && !isAllowedExternalContentType(type)) {
                return null;
            }
            if (!isExternalMediaExtension(ext) && typeExt.isBlank()) {
                return null;
            }

            long length = conn.getContentLengthLong();
            if (maxBytes > 0 && length > maxBytes) {
                return null;
            }

            byte[] data;
            try (InputStream in = conn.getInputStream()) {
                data = readLimitedBytes(in, maxBytes);
            }
            if (data.length == 0 || (maxBytes > 0 && data.length > maxBytes)) return null;

            // If Discord serves a better concrete media type than the URL extension, adjust extension.
            if (!typeExt.isBlank() && !typeExt.equals(ext)) {
                ext = typeExt;
                name = "discord-" + SecurityUtil.sha256Hex(cacheKey).substring(0, 32) + "." + ext;
                target = dir.resolve(name).normalize();
                if (!target.startsWith(dir)) return null;
                if (Files.exists(target) && Files.isRegularFile(target)) return target;
            }

            Path temp = dir.resolve(name + ".tmp-" + SecurityUtil.randomToken(6)).normalize();
            if (!temp.startsWith(dir)) return null;
            Files.write(temp, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception ex) {
            return Files.exists(target) ? target : null;
        }
    }

    private HttpURLConnection openExternalMediaConnection(String url, int timeoutMs, int redirects) throws IOException {
        URI current = URI.create(normalizeClickUrl(url));

        for (int i = 0; i <= redirects; i++) {
            if (!"https".equalsIgnoreCase(current.getScheme()) || !isDiscordCdnPreviewUrl(current.toString())) return null;

            URL remote = current.toURL();
            HttpURLConnection conn = (HttpURLConnection) remote.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 KOKOTO WebChat/" + host.version());
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,video/mp4,video/webm,audio/mpeg,audio/mp4,audio/ogg,audio/*,*/*;q=0.8");

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isBlank()) return null;
                current = current.resolve(location);
                continue;
            }

            return conn;
        }

        return null;
    }

    private boolean isDiscordCdnPreviewUrl(String raw) {
        try {
            String normalized = normalizeClickUrl(raw);
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;

            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);

            boolean discordHost = host.equals("cdn.discordapp.com")
                    || host.equals("media.discordapp.net")
                    || host.equals("cdn.discordapp.net")
                    || host.matches("images-ext-\\d+\\.discordapp\\.net");
            if (!discordHost) return false;

            String path = uri.getPath();
            if (path == null || path.isBlank()) return false;

            String lowerPath = path.toLowerCase(Locale.ROOT);
            String ext = extension(path);
            if (isExternalMediaExtension(ext)) return true;

            String q = uri.getQuery();
            if (q != null && Pattern.compile("(?i)(^|[&?])format=(png|jpe?g|gif|webp|avif|bmp|mp4|webm|mp3|m4a|ogg|oga|wav|flac|aac)($|&)").matcher(q).find()) {
                return true;
            }

            // Discord sometimes gives proxy or attachment URLs whose final content type is the
            // only reliable signal. Treat these as preview candidates and verify Content-Type
            // during download. If it is not allowed media, the cache request is rejected.
            return lowerPath.contains("/attachments/")
                    || lowerPath.contains("/ephemeral-attachments/")
                    || lowerPath.startsWith("/external/");
        } catch (RuntimeException ex) {
            return false;
        }
    }


    private String discordCdnCacheKey(String raw) {
        try {
            URI uri = URI.create(normalizeClickUrl(raw));
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            return host + path;
        } catch (RuntimeException ex) {
            return normalizeClickUrl(raw);
        }
    }

    private String extensionFromUrlPath(String raw) {
        try {
            URI uri = URI.create(normalizeClickUrl(raw));
            String ext = extension(uri.getPath());
            if (!ext.isBlank()) return ext;

            String q = uri.getQuery();
            if (q != null) {
                Matcher m = Pattern.compile("(?i)(^|[&?])format=(png|jpe?g|gif|webp|avif|bmp|mp4|webm|mp3|m4a|ogg|oga|wav|flac|aac)($|&)").matcher(q);
                if (m.find()) {
                    String v = m.group(2).toLowerCase(Locale.ROOT);
                    return "jpeg".equals(v) ? "jpg" : v;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return extension(raw);
    }

    private String extensionFromContentType(String type) {
        if (type == null) return "";
        switch (type.toLowerCase(Locale.ROOT)) {
            case "image/png": return "png";
            case "image/jpeg": return "jpg";
            case "image/gif": return "gif";
            case "image/webp": return "webp";
            case "image/avif": return "avif";
            case "image/bmp": return "bmp";
            case "video/mp4": return "mp4";
            case "video/webm": return "webm";
            case "video/quicktime": return "mov";
            case "audio/mpeg": return "mp3";
            case "audio/mp4": return "m4a";
            case "audio/ogg": return "ogg";
            case "audio/wav": return "wav";
            case "audio/flac": return "flac";
            case "audio/aac": return "aac";
            default: return "";
        }
    }

    private boolean isExternalMediaExtension(String ext) {
        return isImageExtension(ext) || isVideoExtension(ext) || isAudioExtension(ext);
    }

    private boolean isAllowedExternalContentType(String type) {
        if (type == null || type.isBlank()) return false;
        String normalized = type.toLowerCase(Locale.ROOT);
        return normalized.startsWith("image/") || normalized.startsWith("video/") || normalized.startsWith("audio/");
    }


    private boolean canUpload(SessionContext ctx, ConfigValues config) {
        if (ctx == null) return config.uploadAllowGuest;
        if (ctx.account.role == Role.ADMIN) return config.uploadAllowAdmin;
        if (ctx.account.role == Role.MODERATOR) return config.uploadAllowModerator;
        return ctx.account.role.atLeast(Role.USER) && config.uploadAllowUser;
    }

    private Path uploadDir() {
        String dir = host.configValues().uploadDirectory;
        if (dir == null || dir.isBlank()) dir = "uploads";
        Path path = Path.of(dir);
        if (!path.isAbsolute()) path = host.dataDirectory().resolve(path);
        return path.normalize();
    }

    private void cleanupOldUploads() {
        ConfigValues config = host.configValues();
        if (!config.uploadEnabled || config.uploadRetentionDays <= 0) return;

        Path dir = uploadDir();
        if (!Files.isDirectory(dir)) return;

        long cutoff = System.currentTimeMillis() - (config.uploadRetentionDays * 24L * 60L * 60L * 1000L);
        Set<String> protectedPinnedUploads = config.pinnedPreserveUploads ? protectedPinnedUploadNames() : Collections.emptySet();
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    String uploadName = file.getFileName().toString();
                    if (protectedPinnedUploads.contains(uploadName)) return;
                    if (uploadNameReferencedAnywhere(uploadName, "", "")) return;
                    if (Files.getLastModifiedTime(file).toMillis() < cutoff) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ex) {
            host.logger().warn("Failed to cleanup uploaded files: " + ex.getMessage());
        }
    }

    private boolean ensureUploadQuotaAvailable(Path dir, long incomingBytes, ConfigValues config) {
        long quotaBytes = uploadQuotaBytes(config);
        if (quotaBytes <= 0L) return true;
        long needed = Math.max(0L, incomingBytes);
        if (needed > quotaBytes) return false;

        List<UploadQuotaFile> files = uploadQuotaFiles(dir);
        long total = 0L;
        for (UploadQuotaFile file : files) total += Math.max(0L, file.size);
        if (total + needed <= quotaBytes) return true;

        Set<String> protectedPinnedUploads = config != null && config.pinnedPreserveUploads
                ? protectedPinnedUploadNames() : Collections.emptySet();
        files.sort(Comparator.comparingLong((UploadQuotaFile f) -> f.modified).thenComparing(f -> f.name));

        int deleted = 0;
        long freed = 0L;
        for (UploadQuotaFile file : files) {
            if (total + needed <= quotaBytes) break;
            if (protectedPinnedUploads.contains(file.name)) continue;
            if (uploadNameReferencedAnywhere(file.name, "", "")) continue;
            try {
                if (Files.deleteIfExists(file.path)) {
                    total -= Math.max(0L, file.size);
                    freed += Math.max(0L, file.size);
                    deleted++;
                }
            } catch (IOException ignored) {
            }
        }
        if (deleted > 0) {
            host.logger().info("Cleaned " + deleted + " unreferenced upload(s) (" + freed + " bytes) to satisfy upload.max-total-size-mb quota.");
        }
        return total + needed <= quotaBytes;
    }

    private long uploadQuotaBytes(ConfigValues config) {
        if (config == null || config.uploadMaxTotalSizeMb <= 0) return 0L;
        long mb = Math.max(0L, (long) config.uploadMaxTotalSizeMb);
        if (mb > Long.MAX_VALUE / 1024L / 1024L) return Long.MAX_VALUE;
        return mb * 1024L * 1024L;
    }

    private List<UploadQuotaFile> uploadQuotaFiles(Path dir) {
        List<UploadQuotaFile> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    out.add(new UploadQuotaFile(
                            path,
                            path.getFileName().toString(),
                            Files.size(path),
                            Files.getLastModifiedTime(path).toMillis()
                    ));
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ex) {
            host.logger().warn("Failed to inspect uploaded files for quota cleanup: " + ex.getMessage());
        }
        return out;
    }

    private static final class UploadQuotaFile {
        final Path path;
        final String name;
        final long size;
        final long modified;

        UploadQuotaFile(Path path, String name, long size, long modified) {
            this.path = path;
            this.name = name;
            this.size = size;
            this.modified = modified;
        }
    }

    private Set<String> protectedPinnedUploadNames() {
        return uploadNamesFromTexts(storage.pinnedMessageTexts());
    }

    private Set<String> uploadNamesFromTexts(Collection<String> texts) {
        Set<String> out = new LinkedHashSet<>();
        if (texts == null || texts.isEmpty()) return out;
        Pattern relativeUpload = Pattern.compile("(?i)(?:^|[\\s<>'\"])(/[^\\s<>'\"]*/uploads/[^\\s<>'\"]+)");
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            Matcher urlMatcher = URL_PATTERN.matcher(text);
            while (urlMatcher.find()) {
                String raw = splitUrlTrailing(urlMatcher.group(1))[0];
                String name = uploadNameFromUrl(raw);
                if (!name.isBlank()) out.add(name);
            }
            Matcher relative = relativeUpload.matcher(text);
            while (relative.find()) {
                String raw = splitUrlTrailing(relative.group(1))[0];
                String name = uploadNameFromUrl(raw);
                if (!name.isBlank()) out.add(name);
            }
        }
        return out;
    }

    private void deleteUploadedFilesIfUnreferenced(Collection<String> names, String ignoreDmThreadId, String ignoreGroupRoomId) {
        if (names == null || names.isEmpty()) return;
        Path dir = uploadDir();
        for (String rawName : names) {
            String name = String.valueOf(rawName == null ? "" : rawName).trim();
            if (!isSafeStoredUploadName(name)) continue;
            if (uploadNameReferencedAnywhere(name, ignoreDmThreadId, ignoreGroupRoomId)) continue;
            try {
                Path file = dir.resolve(name).normalize();
                if (!file.startsWith(dir)) continue;
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
        }
    }

    private boolean uploadNameReferencedAnywhere(String name, String ignoreDmThreadId, String ignoreGroupRoomId) {
        String n = String.valueOf(name == null ? "" : name).trim();
        if (!isSafeStoredUploadName(n)) return false;
        String encoded = urlPath(n);
        for (String text : storage.pinnedMessageTexts()) {
            if (containsUploadNameReference(text, n, encoded)) return true;
        }
        synchronized (history) {
            for (ChatMessage msg : history) {
                if (msg != null && !msg.hidden && containsUploadNameReference(msg.message, n, encoded)) return true;
            }
        }
        if (sqliteHistoryEnabled() && sqliteHistory.uploadNameReferenced(n)) return true;
        if (host.directMessages() != null && host.directMessages().uploadNameReferenced(n, ignoreDmThreadId)) return true;
        if (host.groupChats() != null && host.groupChats().uploadNameReferenced(n, ignoreGroupRoomId)) return true;
        return false;
    }

    private String uploadNameFromUrl(String raw) {
        try {
            String value = String.valueOf(raw == null ? "" : raw).trim();
            if (value.isBlank()) return "";
            URI uri;
            if (value.startsWith("/")) uri = URI.create("https://kwc.invalid" + value);
            else if (value.regionMatches(true, 0, "http://", 0, 7) || value.regionMatches(true, 0, "https://", 0, 8)) uri = URI.create(value);
            else uri = URI.create(normalizeClickUrl(value));
            String path = uri.getPath() == null ? "" : uri.getPath();
            int idx = path.lastIndexOf("/uploads/");
            if (idx < 0) return "";
            String name = path.substring(idx + "/uploads/".length());
            if (name.contains("/")) return "";
            return isSafeStoredUploadName(name) ? name : "";
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private boolean containsUploadNameReference(String text, String rawName, String encodedName) {
        String value = String.valueOf(text == null ? "" : text);
        return (!rawName.isBlank() && value.contains(rawName)) || (!encodedName.isBlank() && value.contains(encodedName));
    }

    private Set<String> protectedPinnedExternalCacheNames() {
        Set<String> out = new HashSet<>();
        for (String text : storage.pinnedMessageTexts()) {
            if (text == null || text.isBlank()) continue;
            Matcher urlMatcher = URL_PATTERN.matcher(text);
            while (urlMatcher.find()) {
                String raw = splitUrlTrailing(urlMatcher.group(1))[0];
                String url = normalizeClickUrl(raw);
                if (!isDiscordCdnPreviewUrl(url)) continue;
                String ext = extensionFromUrlPath(url);
                if (ext.isBlank()) ext = "bin";
                ext = ext.toLowerCase(Locale.ROOT);
                String cacheKey = discordCdnCacheKey(url);
                out.add("discord-" + SecurityUtil.sha256Hex(cacheKey).substring(0, 32) + "." + ext);
            }
        }
        return out;
    }

    private String publicUploadBaseUrl(HttpExchange ex) {
        ConfigValues config = host.configValues();
        String configured = config.uploadPublicBaseUrl;
        if (configured != null && !configured.isBlank()) {
            return normalizeResourceBaseUrl(ex, configured, "uploads");
        }
        return publicApiBaseUrl(ex) + "/uploads";
    }

    public String externalEmojiLinksForDiscord(String message, int maxLinks) {
        ConfigValues config = host.configValues();
        if (config == null || !config.emojiEnabled || maxLinks <= 0) return "";
        String base = externalEmojiBaseUrl(config);
        if (base.isBlank()) return "";

        EmojiCatalog catalog = scanEmojiCatalog(config);
        if (catalog.items.isEmpty()) return "";
        Map<String, EmojiItem> emojiById = new HashMap<>();
        for (EmojiItem item : catalog.items) {
            emojiById.put(item.id, item);
        }
        Map<String, String> aliasToId = emojiAliasToWebId(catalog, config);

        List<String> urls = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = EMOJI_TOKEN_PATTERN.matcher(String.valueOf(message == null ? "" : message));
        while (matcher.find() && urls.size() < maxLinks) {
            EmojiItem item = emojiItemForToken(matcher.group(1), emojiById, aliasToId);
            if (item == null || item.relativePath.isBlank()) continue;
            String url = base + "/" + urlPath(item.relativePath);
            if (seen.add(url)) urls.add(url);
        }
        return String.join("\n", urls);
    }

    private String externalEmojiBaseUrl(ConfigValues config) {
        String explicit = externalResourceBaseUrl(config, config == null ? "" : config.emojiPublicBaseUrl, "emojis");
        if (!explicit.isBlank()) return explicit;

        String base = "";
        if (config != null) {
            String origin = configuredCorsOrigin(config);
            if (!origin.isBlank()) {
                base = trimTrailingSlash(origin + joinPublicPath(config.publicPrefix, normalizeContextPrefix(config.pathPrefix, "/api")));
            }
        }
        if (base.isBlank()) return "";
        base = stripKnownResourceSuffix(base);
        return trimTrailingSlash(base) + "/emojis";
    }

    private String externalResourceBaseUrl(ConfigValues config, String configured, String resource) {
        String base = externalConfiguredBaseUrl(config, configured);
        if (base.isBlank()) return "";
        base = stripKnownResourceSuffix(base);
        String suffix = "/" + resource;
        if (base.equals(suffix) || base.endsWith(suffix)) return base;
        if (looksLikeApiBase(base, config)) return base + suffix;
        if (configured != null && (configured.endsWith("/" + resource) || configured.contains("/" + resource + "/"))) return base;
        return base + suffix;
    }

    private String externalConfiguredBaseUrl(ConfigValues config, String configured) {
        String value = String.valueOf(configured == null ? "" : configured).trim();
        if (value.isBlank()) return "";
        if (value.startsWith("http://") || value.startsWith("https://")) return trimTrailingSlash(value);
        if (value.startsWith("//")) return trimTrailingSlash("https:" + value);

        String origin = configuredCorsOrigin(config);
        if (origin.isBlank()) return "";
        if (value.startsWith("/")) return trimTrailingSlash(origin + value);
        return trimTrailingSlash(origin + "/" + value.replaceFirst("^/+", ""));
    }

    private String publicEmojiBaseUrl(HttpExchange ex) {
        ConfigValues config = host.configValues();
        String configured = config.emojiPublicBaseUrl;
        if (configured != null && !configured.isBlank()) {
            return normalizeResourceBaseUrl(ex, configured, "emojis");
        }
        return publicApiBaseUrl(ex) + "/emojis";
    }

    private String normalizeResourceBaseUrl(HttpExchange ex, String configured, String resource) {
        String base = normalizePublicBaseUrl(ex, configured);
        String suffix = "/" + resource;
        if (base.equals(suffix) || base.endsWith(suffix)) return base;
        if (looksLikeApiBase(base, host.configValues())) return base + suffix;
        return base;
    }

    private boolean looksLikeApiBase(String value, ConfigValues config) {
        String v = trimTrailingSlash(String.valueOf(value == null ? "" : value).trim());
        if (v.isBlank()) return false;
        String path = v;
        try {
            URI uri = URI.create(v);
            if (uri.getScheme() != null && uri.getPath() != null) path = uri.getPath();
        } catch (IllegalArgumentException ignored) {
        }
        path = trimTrailingSlash(path);
        String pathPrefix = trimTrailingSlash(config.pathPrefix == null || config.pathPrefix.isBlank() ? "/api" : config.pathPrefix);
        if (path.equals(pathPrefix) || path.endsWith(pathPrefix)) return true;
        String webBase = trimTrailingSlash(config.apiBaseUrl == null ? "" : config.apiBaseUrl.trim());
        String standaloneBase = trimTrailingSlash(config.standaloneWebApiBaseUrl == null ? "" : config.standaloneWebApiBaseUrl.trim());
        return (!webBase.isBlank() && v.equals(webBase)) || (!standaloneBase.isBlank() && v.equals(standaloneBase));
    }

    private String publicApiBaseUrl(HttpExchange ex) {
        ConfigValues config = host.configValues();
        String proto = forwardedProto(ex);
        String host = forwardedHost(ex, config);

        boolean proxied = isForwardedRequest(ex);
        String apiPath = proxied
                ? joinPublicPath(config.publicPrefix, normalizeContextPrefix(config.pathPrefix, "/api"))
                : normalizeContextPrefix(config.pathPrefix, "/api");
        return trimTrailingSlash(proto + "://" + host + apiPath);
    }

    private String publicStandaloneOpenUrl(HttpExchange ex) {
        ConfigValues config = host.configValues();
        if (config == null) return "/";
        String standalonePath = normalizeStandaloneContextPath(config.standaloneWebPath, "/");
        String publicPath = isForwardedRequest(ex)
                ? joinPublicPath(config.publicPrefix, standalonePath)
                : standalonePath;
        String origin = configuredCorsOrigin(config);
        if (origin.isBlank()) origin = trimTrailingSlash(forwardedProto(ex) + "://" + forwardedHost(ex, config));
        return trimTrailingSlash(origin) + ("/".equals(publicPath) ? "/" : publicPath);
    }

    private String normalizePublicPrefix(String value) {
        String out = String.valueOf(value == null ? "" : value).trim();
        if (out.isBlank() || "/".equals(out)) return "";
        if (!out.startsWith("/")) out = "/" + out;
        return trimTrailingSlash(out);
    }

    private String joinPublicPath(String publicPrefix, String internalPath) {
        String prefix = normalizePublicPrefix(publicPrefix);
        String path = normalizeContextPrefix(internalPath, "/");
        if (prefix.isBlank()) return path;
        if ("/".equals(path)) return prefix;
        return prefix + path;
    }

    private boolean isForwardedRequest(HttpExchange ex) {
        return headerPresent(ex, "X-Forwarded-Proto") || headerPresent(ex, "X-Forwarded-Host") || headerPresent(ex, "X-Forwarded-For");
    }

    private boolean headerPresent(HttpExchange ex, String name) {
        String value = ex.getRequestHeaders().getFirst(name);
        return value != null && !value.isBlank();
    }

    private String normalizePublicBaseUrl(HttpExchange ex, String configured) {
        String value = String.valueOf(configured == null ? "" : configured).trim();
        if (value.isBlank()) return publicApiBaseUrl(ex);

        String proto = forwardedProto(ex);

        if (value.startsWith("http://") || value.startsWith("https://")) {
            return trimTrailingSlash(value);
        }
        if (value.startsWith("//")) {
            return trimTrailingSlash(proto + ":" + value);
        }
        if (value.startsWith("/")) {
            // Same-origin absolute browser path for reverse proxies, e.g. /chat/api.
            return trimTrailingSlash(value);
        }

        // Relative values without a leading slash are shorthand.
        // Use http.cors-origin when it names a real origin; otherwise fall back to
        // a same-origin absolute path so direct HTTP remains usable.
        String origin = configuredCorsOrigin(host.configValues());
        if (!origin.isBlank()) return trimTrailingSlash(origin + "/" + value.replaceFirst("^/+", ""));
        return trimTrailingSlash("/" + value);
    }

    private String forwardedProto(HttpExchange ex) {
        String proto = ex.getRequestHeaders().getFirst("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) proto = "http";
        return proto;
    }

    private String forwardedHost(HttpExchange ex, ConfigValues config) {
        String host = ex.getRequestHeaders().getFirst("X-Forwarded-Host");
        if (host == null || host.isBlank()) host = ex.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) host = config.httpHost + ":" + config.httpPort;
        return host;
    }

    private String configuredCorsOrigin(ConfigValues config) {
        String origin = String.valueOf(config == null || config.corsOrigin == null ? "" : config.corsOrigin).trim();
        if (origin.isBlank() || "*".equals(origin)) return "";
        if (origin.startsWith("http://") || origin.startsWith("https://")) return trimTrailingSlash(origin);
        return "";
    }

    private String trimTrailingSlash(String s) {
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static String sanitizeFileName(String name) {
        String raw = java.text.Normalizer.normalize(String.valueOf(name == null ? "" : name), java.text.Normalizer.Form.NFC).replace("\\", "/");
        int slash = raw.lastIndexOf('/');
        if (slash >= 0) raw = raw.substring(slash + 1);

        // Preserve ordinary Unicode (Korean/Japanese/Chinese/emoji/etc.) and
        // spaces, but neutralize characters that are unsafe on common filesystems,
        // can inject/control text, or can visually reverse a filename/extension.
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length();) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            boolean unsafeBidi = cp == 0x061C || cp == 0x200E || cp == 0x200F
                    || (cp >= 0x202A && cp <= 0x202E) || (cp >= 0x2066 && cp <= 0x2069);
            boolean unsafeSeparator = type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
            if (cp == 0 || Character.isISOControl(cp) || unsafeBidi || unsafeSeparator
                    || cp == '/' || cp == '\\' || cp == ':' || cp == '*' || cp == '?'
                    || cp == '"' || cp == '<' || cp == '>' || cp == '|') {
                out.append('_');
            } else {
                out.appendCodePoint(cp);
            }
        }

        String safe = out.toString().trim();
        while (safe.endsWith(".") || safe.endsWith(" ")) safe = safe.substring(0, safe.length() - 1);
        while (safe.startsWith(".")) safe = "_" + safe.substring(1);
        if (safe.isBlank() || safe.equals(".") || safe.equals("..")) {
            safe = "upload-" + SecurityUtil.randomToken(10) + ".bin";
        }

        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String suffix = dot > 0 ? safe.substring(dot) : "";
        if (base.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")) base = "_" + base;

        // Linux commonly limits one path component to 255 UTF-8 bytes. Keep a
        // margin for suffixes added by duplicate-name handling and preserve the
        // extension while truncating only the base.
        int maxBytes = 200;
        int suffixBytes = suffix.getBytes(StandardCharsets.UTF_8).length;
        int baseBudget = Math.max(24, maxBytes - suffixBytes);
        base = truncateUtf8Bytes(base, baseBudget);
        if (base.isBlank()) base = "upload-" + SecurityUtil.randomToken(10);
        safe = base + suffix;
        return safe;
    }

    private static String truncateUtf8Bytes(String value, int maxBytes) {
        String text = String.valueOf(value == null ? "" : value);
        if (maxBytes <= 0 || text.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return text;
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String one = new String(Character.toChars(cp));
            int bytes = one.getBytes(StandardCharsets.UTF_8).length;
            if (used + bytes > maxBytes) break;
            out.appendCodePoint(cp);
            used += bytes;
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    private boolean isSafeStoredUploadName(String name) {
        String value = String.valueOf(name == null ? "" : name);
        if (value.isBlank() || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0) return false;
        if (value.equals(".") || value.equals("..")) return false;
        return sanitizeFileName(value).equals(value);
    }

    private Path writeUploadWithNamePolicy(Path dir, String original, String ext, byte[] data, ConfigValues config) throws IOException {
        String mode = String.valueOf(config.uploadFilenameMode == null ? "random" : config.uploadFilenameMode).trim().toLowerCase(Locale.ROOT);
        if (!"original".equals(mode)) {
            for (int attempt = 0; attempt < 20; attempt++) {
                String stored = System.currentTimeMillis() + "-" + SecurityUtil.randomToken(10) + "." + ext;
                Path target = dir.resolve(stored).normalize();
                if (!target.startsWith(dir)) throw new IOException("invalid_path");
                try {
                    Files.write(target, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    return target;
                } catch (java.nio.file.FileAlreadyExistsException ignored) {}
            }
            throw new IOException("upload_name_collision");
        }
        int dot = original.lastIndexOf('.');
        String base = dot > 0 ? original.substring(0, dot) : original;
        String suffix = dot > 0 ? original.substring(dot) : (ext.isBlank() ? "" : "." + ext);
        for (int n = 1; n <= 10000; n++) {
            String stored = n == 1 ? original : base + "-" + n + suffix;
            Path target = dir.resolve(stored).normalize();
            if (!target.startsWith(dir)) throw new IOException("invalid_path");
            try {
                Files.write(target, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return target;
            } catch (java.nio.file.FileAlreadyExistsException ignored) {}
        }
        throw new IOException("upload_name_collision");
    }

    private String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedUploadExtension(String ext, ConfigValues config) {
        if (ext == null || ext.isBlank()) return false;
        ext = ext.toLowerCase(Locale.ROOT);
        if (DANGEROUS_UPLOAD_EXTENSIONS.contains(ext)) return false;
        List<String> allowed = config.uploadAllowedExtensions;
        if (allowed == null || allowed.isEmpty()) return false;
        for (String item : allowed) {
            if (ext.equals(String.valueOf(item).trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String uploadMediaType(String ext) {
        if (isImageExtension(ext)) return "image";
        if (isVideoExtension(ext)) return "video";
        return "file";
    }

    private boolean isImageExtension(String ext) {
        return Set.of("png", "jpg", "jpeg", "gif", "webp", "avif", "bmp").contains(ext);
    }

    private boolean isVideoExtension(String ext) {
        return Set.of("mp4", "webm", "mov").contains(ext);
    }

    private boolean isAudioExtension(String ext) {
        return Set.of("mp3", "m4a", "ogg", "oga", "wav", "flac", "aac").contains(ext);
    }

    private boolean isInlineUploadExtension(String ext) {
        return isImageExtension(ext) || isVideoExtension(ext) || isAudioExtension(ext) || "pdf".equals(ext) || "txt".equals(ext);
    }

    private String contentTypeForExtension(String ext) {
        switch (ext == null ? "" : ext.toLowerCase(Locale.ROOT)) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "avif": return "image/avif";
            case "bmp": return "image/bmp";
            case "mp4": return "video/mp4";
            case "webm": return "video/webm";
            case "mov": return "video/quicktime";
            case "mp3": return "audio/mpeg";
            case "m4a": return "audio/mp4";
            case "ogg":
            case "oga": return "audio/ogg";
            case "wav": return "audio/wav";
            case "flac": return "audio/flac";
            case "aac": return "audio/aac";
            case "zip": return "application/zip";
            case "pdf": return "application/pdf";
            case "txt": return "text/plain; charset=utf-8";
            default: return "application/octet-stream";
        }
    }

    private String multipartBoundary(String contentType) {
        if (contentType == null) return null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                String b = part.substring("boundary=".length()).trim();
                if (b.startsWith("\"") && b.endsWith("\"") && b.length() >= 2) {
                    b = b.substring(1, b.length() - 1);
                }
                return b;
            }
        }
        return null;
    }

    private byte[] readLimitedBytes(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buf)) != -1) {
            total += read;
            if (limit > 0 && total > limit) throw new UploadTooLargeException();
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }

    private MultipartData parseMultipart(byte[] body, String boundary) {
        MultipartData result = new MultipartData();
        String text = new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundary;
        int pos = 0;

        while (true) {
            int start = text.indexOf(delimiter, pos);
            if (start < 0) break;
            start += delimiter.length();
            if (start < text.length() && text.startsWith("--", start)) break;
            if (text.startsWith("\r\n", start)) start += 2;

            int headerEnd = text.indexOf("\r\n\r\n", start);
            if (headerEnd < 0) break;

            String headers = text.substring(start, headerEnd);
            int dataStart = headerEnd + 4;
            int next = text.indexOf("\r\n" + delimiter, dataStart);
            if (next < 0) next = text.indexOf(delimiter, dataStart);
            if (next < 0) break;

            byte[] data = Arrays.copyOfRange(body, dataStart, next);
            String fieldName = dispositionValue(headers, "name");
            String filename = dispositionValue(headers, "filename");

            if (filename != null && !filename.isBlank()) {
                result.file = new UploadedPart(fieldName, filename, data);
            } else if (fieldName != null) {
                result.fields.put(fieldName, new String(data, StandardCharsets.UTF_8).trim());
            }

            pos = next + 2;
        }

        return result;
    }

    private String dispositionValue(String headers, String key) {
        if (headers == null || key == null) return null;
        Pattern star = Pattern.compile("(?i)" + Pattern.quote(key + "*") + "=([^;\\r\\n]+)");
        Matcher sm = star.matcher(headers);
        if (sm.find()) {
            String raw = sm.group(1).trim();
            if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) raw = raw.substring(1, raw.length() - 1);
            int marker = raw.indexOf("''");
            if (marker >= 0) raw = raw.substring(marker + 2);
            try { return URLDecoder.decode(raw, StandardCharsets.UTF_8); } catch (Exception ignored) {}
        }
        Pattern p = Pattern.compile("(?i)" + Pattern.quote(key) + "=\"([^\"]*)\"");
        Matcher m = p.matcher(headers);
        if (!m.find()) return null;
        String raw = m.group(1);
        try {
            return new String(raw.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String safeUploadedFilename(String filename, String fallback) {
        String raw = java.text.Normalizer.normalize(String.valueOf(filename == null ? "" : filename), java.text.Normalizer.Form.NFC).replace("\\", "/");
        int slash = raw.lastIndexOf('/');
        if (slash >= 0) raw = raw.substring(slash + 1);
        raw = raw.replace("\0", "").trim();
        if (raw.isBlank()) raw = fallback == null || fallback.isBlank() ? "file" : fallback;
        String ext = extension(raw).toLowerCase(Locale.ROOT);
        String base = raw.contains(".") ? raw.substring(0, raw.lastIndexOf('.')) : raw;
        String safeBase = sanitizeEmojiFilenameBase(base);
        if (safeBase.isBlank()) safeBase = fallback == null || fallback.isBlank() ? "file" : fallback;
        return ext.isBlank() ? safeBase : safeBase + "." + ext;
    }

    private static final class MultipartData {
        final Map<String, String> fields = new LinkedHashMap<>();
        UploadedPart file;
    }

    private static final class UploadedPart {
        final String fieldName;
        final String filename;
        final byte[] data;

        UploadedPart(String fieldName, String filename, byte[] data) {
            this.fieldName = fieldName;
            this.filename = filename;
            this.data = data;
        }
    }

    private static final class UploadTooLargeException extends IOException {
    }

    private void handleCaptcha(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        ConfigValues config = host.configValues();
        if (!captcha.enabled(config == null ? null : config.captchaMode)) {
            sendJson(ex, 200, "{\"ok\":true,\"enabled\":false}");
            return;
        }
        if (!config.captchaRequireOnEachMessage && captcha.verifyIpPass(remoteIp(ex))) {
            sendJson(ex, 200, "{\"ok\":true,\"enabled\":true,\"passed\":true}");
            return;
        }
        CaptchaManager.Captcha c = captcha.issueMath(config.captchaExpireSeconds);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("enabled", true);
        m.put("id", c.id);
        m.put("question", c.question);
        sendJson(ex, 200, JsonUtil.obj(m));
    }

    private void handleAuthCode(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        ConfigValues config = host.configValues();
        if (!config.authEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"auth_disabled\"}");
            return;
        }
        String ip = remoteIp(ex);
        if (!rateLimiter.allow("auth-code:" + ip, config.authCodeCooldownSeconds, config.authCodeMaxPerMinute)) {
            sendJson(ex, 429, "{\"ok\":false,\"error\":\"rate_limited\"}");
            return;
        }
        WebAuthCode c = auth.issueCode();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("code", c.code);
        m.put("poll", c.pollToken);
        m.put("expiresAt", c.expiresAt);
        sendJson(ex, 200, JsonUtil.obj(m));
    }

    private void handleAuthStatus(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        Map<String, String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String json = auth.pollStatusJson(q.get("poll"), remoteIp(ex));
        Map<String, String> result = JsonUtil.parseFlatObject(json);
        if ("true".equalsIgnoreCase(result.get("ok")) && "linked".equalsIgnoreCase(result.get("status"))) {
            String name = result.getOrDefault("username", "");
            if (name == null) name = "";
            host.publishAnnouncement("web-login", Map.of("name", name, "player", name));
        }
        sendJson(ex, 200, json);
    }

    private void handleAuthLogin(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        String json = auth.login(body.get("username"), body.get("password"), remoteIp(ex));
        Map<String, String> result = JsonUtil.parseFlatObject(json);
        if ("true".equalsIgnoreCase(result.get("ok"))) {
            String name = result.getOrDefault("username", "");
            if (name == null) name = "";
            host.publishAnnouncement("web-login", Map.of("name", name, "player", name));
        }
        sendJson(ex, 200, json);
    }

    private void handleSetPassword(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        String token = bearerToken(ex);
        if (token.isBlank()) token = String.valueOf(body.getOrDefault("token", ""));
        if (sessionForRequest(ex, token) == null) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"not_logged_in\"}");
            return;
        }
        sendJson(ex, 200, auth.setPassword(token, body.get("password")));
    }

    private void handleMe(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        String token = requestToken(ex, false);
        SessionContext ctx = sessionForRequest(ex, token);
        if (ctx == null) {
            sendJson(ex, 200, "{\"ok\":false}");
            return;
        }
        sendJson(ex, 200, auth.me(token));
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        Map<String, String> body = JsonUtil.parseFlatObject(JsonUtil.readBody(ex.getRequestBody()));
        String logoutToken = bearerToken(ex);
        if (logoutToken.isBlank()) logoutToken = body.get("token");
        SessionContext ctx = storage.getSession(logoutToken);
        boolean ok = storage.revokeSession(logoutToken);
        if (ok) broadcastAuthExpiredForToken(logoutToken, "logout");
        if (ok && ctx != null && ctx.account != null) {
            String name = ctx.account.safeUsername();
            if (name == null) name = "";
            host.publishAnnouncement("web-logout", Map.of("name", name, "player", name));
        }
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }


    private void handleAdminSummary(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("onlineCount", platform.onlinePlayers().size());
        m.put("accountCount", storage.listAccounts().size());
        m.put("sessionCount", storage.listSessions().size());
        m.put("muteCount", host.moderation().list().size());
        sendJson(ex, 200, JsonUtil.obj(m));
    }

    private void handleAdminOnline(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        List<String> items = new ArrayList<>();
        for (PlatformPlayer p : platform.onlinePlayers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("displayName", p.displayName());
            m.put("uuid", p.uuid().toString());
            items.add(JsonUtil.obj(m));
        }
        sendJson(ex, 200, "{\"ok\":true,\"players\":[" + String.join(",", items) + "]}");
    }

    private void handleAdminSessions(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        storage.cleanupExpiredSessions();
        List<String> items = new ArrayList<>();
        for (SessionContext sc : storage.listSessions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", sc.account.safeUsername());
            m.put("displayName", host.displayNameForAccount(sc.account));
            m.put("uuid", sc.account.uuid == null ? "" : sc.account.uuid);
            m.put("role", sc.account.role.name());
            m.put("createdAt", sc.session.createdAt);
            m.put("expiresAt", sc.session.expiresAt);
            m.put("lastIp", sc.session.lastIp);
            items.add(JsonUtil.obj(m));
        }
        sendJson(ex, 200, "{\"ok\":true,\"sessions\":[" + String.join(",", items) + "]}");
    }

    private void handleAdminAccounts(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        List<String> items = new ArrayList<>();
        for (Account a : storage.listAccounts()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", a.safeUsername());
            m.put("displayName", host.displayNameForAccount(a));
            m.put("role", a.role.name());
            m.put("local", a.local);
            m.put("uuid", a.uuid == null ? "" : a.uuid);
            m.put("passwordSet", a.hasPassword());
            m.put("createdAt", a.createdAt);
            m.put("lastLogin", a.lastLogin);
            items.add(JsonUtil.obj(m));
        }
        sendJson(ex, 200, "{\"ok\":true,\"accounts\":[" + String.join(",", items) + "]}");
    }

    private void handleAdminRevoke(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        Map<String, String> body = parsedBody(ex);
        String username = body.get("username");
        Account revokedAccount = storage.findAccountByUsername(username);
        String revokedUuid = revokedAccount == null || revokedAccount.uuid == null ? "" : revokedAccount.uuid.trim().toLowerCase(Locale.ROOT);
        int removed = storage.revokeSessionsForUsername(username);
        if (removed > 0 && !revokedUuid.isBlank()) broadcastAuthExpired(revokedUuid, "revoked");
        audit(ctx, "admin.revoke-sessions", Map.of("username", username == null ? "" : username, "removed", removed));
        sendJson(ex, 200, "{\"ok\":true,\"removed\":" + removed + "}");
    }

    private void handleAdminMutes(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        if (!host.configValues().moderationEnabled) {
            sendJson(ex, 200, "{\"ok\":true,\"mutes\":[]}");
            return;
        }
        List<String> items = new ArrayList<>();
        for (ModerationEntry e : host.moderation().list()) {
            items.add(JsonUtil.obj(e.toMap()));
        }
        sendJson(ex, 200, "{\"ok\":true,\"mutes\":[" + String.join(",", items) + "]}");
    }

    private void handleAdminMute(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        if (!host.configValues().moderationEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"moderation_disabled\"}");
            return;
        }
        if (!host.configValues().allowModeratorGuestMute && !ctx.account.role.atLeast(Role.ADMIN)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String type = body.getOrDefault("type", "guest");
        String value = body.getOrDefault("value", "");
        long minutes = parseLong(body.get("minutes"), host.configValues().defaultMuteMinutes);
        String reason = body.getOrDefault("reason", "");
        if (value.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"empty_value\"}");
            return;
        }
        ModerationEntry e = host.moderation().mute(type, value, minutes, reason, ctx.account.safeUsername());
        audit(ctx, "admin.mute", Map.of("type", type, "value", value, "minutes", minutes, "reason", reason));
        sendJson(ex, 200, "{\"ok\":true,\"mute\":" + JsonUtil.obj(e.toMap()) + "}");
    }

    private void handleAdminUnmute(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        if (!host.configValues().moderationEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"moderation_disabled\"}");
            return;
        }
        if (!host.configValues().allowModeratorGuestMute && !ctx.account.role.atLeast(Role.ADMIN)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String type = body.getOrDefault("type", "guest");
        String value = body.getOrDefault("value", "");
        boolean removed = host.moderation().unmute(type, value);
        if (removed) audit(ctx, "admin.unmute", Map.of("type", type, "value", value));
        sendJson(ex, 200, "{\"ok\":" + removed + "}");
    }

    private void handleAdminDeleteMessage(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        if (!host.configValues().moderationEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"moderation_disabled\"}");
            return;
        }
        if (!host.configValues().allowModeratorMessageDelete && !ctx.account.role.atLeast(Role.ADMIN)) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String id = body.get("id");
        boolean ok = false;
        synchronized (history) {
            for (ChatMessage m : history) {
                if (m.id != null && m.id.equals(id)) {
                    m.hidden = true;
                    ok = true;
                    break;
                }
            }
        }
        if (sqliteHistoryEnabled()) {
            boolean dbOk = sqliteHistory.markHidden(id);
            ok = ok || dbOk;
        }
        if (ok) {
            if (legacyJsonlHistoryEnabled()) savePersistedHistory();
            broadcastEvent("delete", "{\"id\":" + JsonUtil.quote(id) + "}");
            audit(ctx, "admin.delete-message", Map.of("messageId", id == null ? "" : id));
        }
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }

    private void handleAdminDeleteDmThread(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!isPrivateChatSuperAdmin(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        Map<String, String> body = parsedBody(ex);
        String threadId = stripControl(body.get("threadId"), 180).trim();
        if (threadId.isBlank() || host.directMessages() == null || !host.directMessages().available()) { sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_thread\"}"); return; }
        Set<String> participants = host.directMessages().participantUuidsForThread(threadId);
        Set<String> uploadNames = uploadNamesFromTexts(host.directMessages().messageBodiesForThread(threadId));
        boolean ok = host.directMessages().deleteThread(threadId);
        if (ok) {
            deleteUploadedFilesIfUnreferenced(uploadNames, threadId, "");
            audit(ctx, "admin.delete-dm-thread", Map.of("threadId", threadId, "uploads", uploadNames.size(), "participants", participants.size()));
        }
        String a = participants.stream().findFirst().orElse("");
        String b = participants.stream().skip(1).findFirst().orElse("");
        publishDirectMessageUpdate(a, b, threadId);
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }

    private void handleAdminDeleteGroupRoom(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!isPrivateChatSuperAdmin(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        Map<String, String> body = parsedBody(ex);
        String roomId = stripControl(body.get("roomId"), 140).trim();
        if (roomId.isBlank() || host.groupChats() == null || !host.groupChats().available()) { sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_room\"}"); return; }
        Set<String> members = host.groupChats().memberUuids(roomId);
        Set<String> uploadNames = uploadNamesFromTexts(host.groupChats().messageBodiesForRoom(roomId));
        boolean ok = host.groupChats().deleteRoom(roomId);
        if (ok) {
            deleteUploadedFilesIfUnreferenced(uploadNames, "", roomId);
            audit(ctx, "admin.delete-group-room", Map.of("roomId", roomId, "uploads", uploadNames.size(), "members", members.size()));
        }
        publishGroupChatUpdate(roomId);
        for (String member : members) {
            if (member != null && !member.isBlank()) publishGroupChatUpdate(roomId, member);
        }
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }


    private void handleAdminCleanupPreview(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!isPrivateChatSuperAdmin(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        String dm = (host.directMessages() == null || !host.directMessages().available()) ? "null" : host.directMessages().cleanupPreviewJson();
        String group = (host.groupChats() == null || !host.groupChats().available()) ? "null" : host.groupChats().cleanupPreviewJson();
        sendJson(ex, 200, "{\"ok\":true,\"dm\":" + dm + ",\"group\":" + group + "}");
    }

    private void handleAdminSessionFlags(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!isPrivateChatSuperAdmin(ctx)) { sendJson(ex, 403, "{\"ok\":false,\"error\":\"permission_denied\"}"); return; }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}"); return; }
        Map<String, String> body = parsedBody(ex);
        String type = stripControl(body.get("type"), 20).trim().toLowerCase(Locale.ROOT);
        String id = stripControl(body.get("id"), 180).trim();
        Boolean locked = parseNullableBoolean(body.get("locked"));
        Boolean retentionExempt = parseNullableBoolean(body.get("retentionExempt"));
        if (id.isBlank() || (!"dm".equals(type) && !"group".equals(type))) { sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_session\"}"); return; }
        boolean ok;
        if ("dm".equals(type)) ok = host.directMessages() != null && host.directMessages().setSessionFlags(id, locked, retentionExempt);
        else ok = host.groupChats() != null && host.groupChats().setSessionFlags(id, locked, retentionExempt);
        if (ok) {
            audit(ctx, "admin.session-flags", Map.of("type", type, "id", id, "locked", locked == null ? "unchanged" : locked, "retentionExempt", retentionExempt == null ? "unchanged" : retentionExempt));
            if ("dm".equals(type)) {
                Set<String> participants = host.directMessages().participantUuidsForThread(id);
                String a = participants.stream().findFirst().orElse("");
                String b = participants.stream().skip(1).findFirst().orElse("");
                publishDirectMessageUpdate(a, b, id);
            } else {
                publishGroupChatUpdate(id);
                for (String member : host.groupChats().memberUuids(id)) publishGroupChatUpdate(id, member);
            }
        }
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }

    private Boolean parseNullableBoolean(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.isBlank() || "null".equals(v) || "undefined".equals(v)) return null;
        return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    private void handleAdminPinMessage(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        ConfigValues config = host.configValues();
        if (!config.pinnedEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"pinned_disabled\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String id = body.get("id");
        ChatMessage found = null;
        synchronized (history) {
            for (ChatMessage m : history) {
                if (m.id != null && m.id.equals(id) && !m.hidden) {
                    found = m;
                    break;
                }
            }
        }
        if (found == null && sqliteHistoryEnabled()) {
            ChatMessage dbMsg = sqliteHistory.find(id);
            if (dbMsg != null && !dbMsg.hidden) found = dbMsg;
        }
        if (found == null) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"message_not_found\"}");
            return;
        }
        PinnedMessage pin = storage.pinMessage(found, ctx.account.safeUsername(), config.pinnedMaxPins);
        if (pin == null) {
            sendJson(ex, 409, "{\"ok\":false,\"error\":\"pin_limit_reached\"}");
            return;
        }
        broadcastPinsChanged();
        audit(ctx, "admin.pin-message", Map.of("messageId", id == null ? "" : id, "pinId", pin.pinId == null ? "" : pin.pinId));
        sendJson(ex, 200, "{\"ok\":true,\"pin\":" + pin.toJson() + "}");
    }

    private void handleAdminUnpinMessage(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        Map<String, String> body = parsedBody(ex);
        String pinId = body.get("pinId");
        boolean ok = storage.unpinMessage(pinId);
        if (ok) {
            broadcastPinsChanged();
            audit(ctx, "admin.unpin-message", Map.of("pinId", pinId == null ? "" : pinId));
        }
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }


    private void handleAdminMovePin(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.MODERATOR);
        if (ctx == null) return;
        if (!host.configValues().pinnedEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"pinned_disabled\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String pinId = body.get("pinId");
        String direction = body.get("direction");
        boolean ok = storage.movePinnedMessage(pinId, direction);
        if (ok) {
            broadcastPinsChanged();
            audit(ctx, "admin.move-pin", Map.of("pinId", pinId == null ? "" : pinId, "direction", direction == null ? "" : direction));
        }
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }


    private void handleAdminEmojis(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (!config.emojiEnabled) {
            sendJson(ex, 200, "{\"ok\":true,\"enabled\":false,\"packs\":[],\"items\":[]}");
            return;
        }
        ensureEmojiDirectoryExists();
        EmojiCatalog catalog = scanEmojiCatalog(config);
        Map<String, EmojiPack> byId = new LinkedHashMap<>();
        byId.put("default", new EmojiPack("default", "Default"));
        for (EmojiPack pack : catalog.packs) byId.put(pack.id, pack);
        Path dir = emojiDir();
        if (Files.isDirectory(dir)) {
            try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isDirectory).sorted((a, b) -> compareNatural(a.getFileName().toString(), b.getFileName().toString())).forEach(p -> {
                    String raw = p.getFileName().toString();
                    String id = sanitizeEmojiSegment(raw);
                    if (!id.isBlank()) byId.putIfAbsent(id, new EmojiPack(id, raw));
                });
            } catch (IOException ignored) {}
        }
        List<Object> packs = new ArrayList<>();
        for (EmojiPack pack : byId.values()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", pack.id);
            pm.put("label", pack.label);
            pm.put("count", pack.items.size());
            pm.put("default", "default".equals(pack.id));
            packs.add(pm);
        }
        List<Object> items = new ArrayList<>();
        for (EmojiItem item : catalog.items) {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", item.id);
            im.put("pack", item.pack);
            im.put("name", item.name);
            im.put("label", item.label);
            im.put("path", item.relativePath);
            im.put("filename", Path.of(item.relativePath).getFileName().toString());
            im.put("ext", item.ext);
            im.put("size", item.size);
            items.add(im);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("enabled", true);
        res.put("packs", packs);
        res.put("items", items);
        res.put("maxFileSizeKb", config.emojiMaxFileSizeKb);
        res.put("maxTotalSizeMb", config.emojiMaxTotalSizeMb);
        res.put("maxTotalSize", config.emojiMaxTotalSizeMb > 0 ? config.emojiMaxTotalSizeMb * 1024L * 1024L : 0L);
        res.put("showStorageUsage", config.emojiShowStorageUsage);
        res.put("showStorageLimit", config.emojiShowStorageLimit);
        res.put("totalSize", emojiTotalSize(config));
        sendJson(ex, 200, JsonUtil.obj(res));
    }

    private void handleAdminEmojiCreatePack(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (!config.emojiEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"emoji_disabled\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String requested = body.getOrDefault("pack", body.getOrDefault("name", ""));
        String packId = normalizeEmojiPackId(requested);
        if ("default".equals(packId)) {
            ensureEmojiDirectoryExists();
            sendJson(ex, 200, "{\"ok\":true,\"pack\":\"default\"}");
            return;
        }
        Path root = emojiDir();
        Path dir = emojiPackDir(packId);
        if (!validEmojiPackTarget(root, dir)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_pack\"}");
            return;
        }
        Files.createDirectories(dir);
        invalidateEmojiCatalog();
        audit(ctx, "admin.emoji-create-pack", Map.of("pack", packId));
        sendJson(ex, 200, "{\"ok\":true,\"pack\":" + JsonUtil.quote(packId) + "}");
    }

    private void handleAdminEmojiUpload(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (!config.emojiEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"emoji_disabled\"}");
            return;
        }
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        String boundary = multipartBoundary(contentType);
        if (boundary == null || boundary.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"multipart_required\"}");
            return;
        }
        long maxOne = config.emojiMaxFileSizeKb > 0 ? config.emojiMaxFileSizeKb * 1024L : 0L;
        long maxBody = maxOne > 0 ? maxOne + 256 * 1024L : 64L * 1024L * 1024L;
        byte[] body;
        try {
            body = readLimitedBytes(ex.getRequestBody(), maxBody);
        } catch (UploadTooLargeException e) {
            sendJson(ex, 413, "{\"ok\":false,\"error\":\"file_too_large\"}");
            return;
        }
        MultipartData multipart = parseMultipart(body, boundary);
        UploadedPart file = multipart.file;
        if (file == null || file.data == null || file.data.length == 0) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"missing_file\"}");
            return;
        }
        if (maxOne > 0 && file.data.length > maxOne) {
            sendJson(ex, 413, "{\"ok\":false,\"error\":\"file_too_large\"}");
            return;
        }
        String original = safeUploadedFilename(file.filename, "emoji");
        String ext = extension(original).toLowerCase(Locale.ROOT);
        if (!emojiExtensionAllowed(ext, config)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_extension\"}");
            return;
        }
        byte[] sidecarPngData = null;
        if (gameLinkPngSidecarActive(config) && emojiHasPngSidecarSource(ext)) {
            sidecarPngData = convertImageBytesToPng(file.data);
            if (sidecarPngData == null || sidecarPngData.length == 0) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"png_conversion_failed\"}");
                return;
            }
        }

        long sidecarSize = sidecarPngData == null ? 0L : sidecarPngData.length;
        long maxTotal = config.emojiMaxTotalSizeMb > 0 ? config.emojiMaxTotalSizeMb * 1024L * 1024L : 0L;
        long currentTotal = emojiTotalSize(config);
        if (maxTotal > 0 && currentTotal + file.data.length + sidecarSize > maxTotal) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", "total_size_exceeded");
            err.put("currentSize", currentTotal);
            err.put("fileSize", file.data.length);
            err.put("sidecarSize", sidecarSize);
            err.put("maxTotalSize", maxTotal);
            err.put("maxTotalSizeMb", config.emojiMaxTotalSizeMb);
            sendJson(ex, 413, JsonUtil.obj(err));
            return;
        }
        String packId = normalizeEmojiPackId(multipart.fields.getOrDefault("pack", "default"));
        Path root = emojiDir();
        Path packDir = emojiPackDir(packId);
        if (!validEmojiPackTarget(root, packDir)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_pack\"}");
            return;
        }
        Files.createDirectories(packDir);
        String base = original.contains(".") ? original.substring(0, original.lastIndexOf('.')) : original;
        String uniqueBase = uniqueEmojiBase(packDir, base);
        String stored = uniqueBase + "." + ext;
        Path target = packDir.resolve(stored).normalize();
        if (!target.startsWith(root)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_path\"}");
            return;
        }
        Files.write(target, file.data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Path sidecar = null;
        if (sidecarPngData != null) {
            sidecar = emojiPngSidecarPath(target);
            if (sidecar != null) {
                Files.write(sidecar, sidecarPngData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("pack", packId);
        res.put("filename", stored);
        res.put("originalFilename", original);
        res.put("size", file.data.length);
        res.put("pngSidecar", sidecar != null);
        res.put("pngSidecarFilename", sidecar == null ? "" : sidecar.getFileName().toString());
        invalidateEmojiCatalog();
        audit(ctx, "admin.emoji-upload", Map.of("pack", packId, "filename", stored, "size", file.data.length, "pngSidecar", sidecar != null));
        sendJson(ex, 200, JsonUtil.obj(res));
    }


    private void handleAdminEmojiRename(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (!config.emojiEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"emoji_disabled\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String type = String.valueOf(body.getOrDefault("type", "item")).trim().toLowerCase(Locale.ROOT);
        String newName = String.valueOf(body.getOrDefault("name", body.getOrDefault("newName", ""))).trim();
        if (newName.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_name\"}");
            return;
        }

        Path root = emojiDir();
        Files.createDirectories(root);

        if ("pack".equals(type)) {
            String oldPack = normalizeEmojiPackId(body.getOrDefault("pack", ""));
            String newPack = normalizeEmojiPackId(newName);
            if (oldPack.isBlank() || newPack.isBlank() || "default".equalsIgnoreCase(oldPack) || "default".equalsIgnoreCase(newPack)) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_pack\"}");
                return;
            }
            Path oldDir = emojiPackDir(oldPack);
            Path newDir = emojiPackDir(newPack);
            if (!validEmojiPackTarget(root, oldDir) || !validEmojiPackTarget(root, newDir)) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_pack\"}");
                return;
            }
            if (!Files.isDirectory(oldDir)) {
                sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
                return;
            }
            if (oldDir.equals(newDir)) {
                sendJson(ex, 200, "{\"ok\":true,\"pack\":" + JsonUtil.quote(newPack) + "}");
                return;
            }
            if (Files.exists(newDir)) {
                sendJson(ex, 409, "{\"ok\":false,\"error\":\"already_exists\"}");
                return;
            }
            Files.move(oldDir, newDir);
            invalidateEmojiCatalog();
            audit(ctx, "admin.emoji-rename-pack", Map.of("oldPack", oldPack, "newPack", newPack));
            sendJson(ex, 200, "{\"ok\":true,\"pack\":" + JsonUtil.quote(newPack) + "}");
            return;
        }

        String id = String.valueOf(body.getOrDefault("id", "")).trim();
        if (id.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }
        EmojiCatalog catalog = scanEmojiCatalog(config);
        EmojiItem found = null;
        for (EmojiItem item : catalog.items) {
            if (id.equals(item.id)) {
                found = item;
                break;
            }
        }
        if (found == null) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        String renamed = emojiRenameFilename(newName, found.ext);
        if (renamed.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_name\"}");
            return;
        }
        Path oldFile = root.resolve(found.relativePath).normalize();
        if (!oldFile.startsWith(root) || !Files.isRegularFile(oldFile)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        Path target = oldFile.getParent().resolve(renamed).normalize();
        if (!target.startsWith(root)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_path\"}");
            return;
        }
        if (oldFile.equals(target)) {
            sendJson(ex, 200, "{\"ok\":true,\"id\":" + JsonUtil.quote(found.id) + "}");
            return;
        }
        if (Files.exists(target)) {
            sendJson(ex, 409, "{\"ok\":false,\"error\":\"already_exists\"}");
            return;
        }
        Path oldSidecar = emojiPngSidecarPath(oldFile);
        Files.move(oldFile, target);
        if (oldSidecar != null && Files.isRegularFile(oldSidecar)) {
            Path newSidecar = emojiPngSidecarPath(target);
            if (newSidecar != null && !oldSidecar.equals(newSidecar) && !Files.exists(newSidecar)) {
                Files.move(oldSidecar, newSidecar);
            }
        }
        String base = renamed.contains(".") ? renamed.substring(0, renamed.lastIndexOf('.')) : renamed;
        String newItemName = sanitizeEmojiSegment(base);
        String newId = found.pack + "/" + newItemName;
        invalidateEmojiCatalog();
        audit(ctx, "admin.emoji-rename", Map.of("oldId", found.id, "newId", newId, "filename", renamed));
        sendJson(ex, 200, "{\"ok\":true,\"id\":" + JsonUtil.quote(newId) + ",\"filename\":" + JsonUtil.quote(renamed) + "}");
    }


    private void handleAdminEmojiMove(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (!config.emojiEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"emoji_disabled\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String id = String.valueOf(body.getOrDefault("id", "")).trim();
        String targetPack = normalizeEmojiPackId(body.getOrDefault("pack", body.getOrDefault("targetPack", body.getOrDefault("target", ""))));
        if (id.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }
        if (targetPack.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_pack\"}");
            return;
        }

        Path root = emojiDir();
        Files.createDirectories(root);
        Path targetDir = "default".equalsIgnoreCase(targetPack) ? root : emojiPackDir(targetPack);
        if (!validEmojiPackTarget(root, targetDir)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_pack\"}");
            return;
        }
        Files.createDirectories(targetDir);

        EmojiCatalog catalog = scanEmojiCatalog(config);
        EmojiItem found = null;
        for (EmojiItem item : catalog.items) {
            if (id.equals(item.id)) {
                found = item;
                break;
            }
        }
        if (found == null) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        Path oldFile = root.resolve(found.relativePath).normalize();
        if (!oldFile.startsWith(root) || !Files.isRegularFile(oldFile)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        Path target = targetDir.resolve(oldFile.getFileName().toString()).normalize();
        if (!target.startsWith(root)) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_path\"}");
            return;
        }
        String currentPack = found.pack == null || found.pack.isBlank() ? "default" : found.pack;
        if (oldFile.equals(target) || currentPack.equals(targetPack)) {
            sendJson(ex, 200, "{\"ok\":true,\"id\":" + JsonUtil.quote(found.id) + ",\"pack\":" + JsonUtil.quote(currentPack) + "}");
            return;
        }
        if (Files.exists(target)) {
            sendJson(ex, 409, "{\"ok\":false,\"error\":\"already_exists\"}");
            return;
        }
        Path oldSidecar = emojiPngSidecarPath(oldFile);
        Path newSidecar = emojiPngSidecarPath(target);
        if (oldSidecar != null && Files.isRegularFile(oldSidecar) && newSidecar != null && !oldSidecar.equals(newSidecar) && Files.exists(newSidecar)) {
            sendJson(ex, 409, "{\"ok\":false,\"error\":\"already_exists\"}");
            return;
        }

        Files.move(oldFile, target);
        if (oldSidecar != null && Files.isRegularFile(oldSidecar) && newSidecar != null && !oldSidecar.equals(newSidecar)) {
            Files.move(oldSidecar, newSidecar);
        }
        String base = target.getFileName().toString();
        int dot = base.lastIndexOf('.');
        String itemName = sanitizeEmojiSegment(dot >= 0 ? base.substring(0, dot) : base);
        String newId = targetPack + "/" + itemName;
        invalidateEmojiCatalog();
        audit(ctx, "admin.emoji-move", Map.of("oldId", found.id, "newId", newId, "oldPack", currentPack, "newPack", targetPack));
        sendJson(ex, 200, "{\"ok\":true,\"id\":" + JsonUtil.quote(newId) + ",\"pack\":" + JsonUtil.quote(targetPack) + "}");
    }


    private void handleAdminEmojiDelete(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        ConfigValues config = host.configValues();
        if (!config.emojiEnabled) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"emoji_disabled\"}");
            return;
        }
        Map<String, String> body = parsedBody(ex);
        String type = String.valueOf(body.getOrDefault("type", "item")).trim().toLowerCase(Locale.ROOT);
        Path root = emojiDir();

        if ("pack".equals(type) || body.containsKey("pack")) {
            String packId = normalizeEmojiPackId(body.getOrDefault("pack", ""));
            if (packId.isBlank() || "default".equalsIgnoreCase(packId)) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_pack\"}");
                return;
            }
            Path packDir = emojiPackDir(packId);
            if (!validEmojiPackTarget(root, packDir) || !Files.isDirectory(packDir)) {
                sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
                return;
            }
            try (java.util.stream.Stream<Path> stream = Files.walk(packDir)) {
                List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    Path normalized = path.normalize();
                    if (!normalized.startsWith(root)) continue;
                    Files.deleteIfExists(normalized);
                }
            }
            invalidateEmojiCatalog();
            audit(ctx, "admin.emoji-delete-pack", Map.of("pack", packId));
            sendJson(ex, 200, "{\"ok\":true}");
            return;
        }

        String id = String.valueOf(body.getOrDefault("id", "")).trim();
        if (id.isBlank()) {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_file\"}");
            return;
        }
        EmojiCatalog catalog = scanEmojiCatalog(config);
        EmojiItem found = null;
        for (EmojiItem item : catalog.items) {
            if (id.equals(item.id)) {
                found = item;
                break;
            }
        }
        if (found == null) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        Path file = root.resolve(found.relativePath).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        Path sidecar = emojiPngSidecarPath(file);
        boolean ok = Files.deleteIfExists(file);
        if (ok && sidecar != null && Files.isRegularFile(sidecar)) Files.deleteIfExists(sidecar);
        if (ok) {
            invalidateEmojiCatalog();
            audit(ctx, "admin.emoji-delete", Map.of("id", found.id, "path", found.relativePath == null ? "" : found.relativePath));
        }
        sendJson(ex, 200, "{\"ok\":" + ok + "}");
    }

    private void handleAdminClearHistory(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        synchronized (history) {
            history.clear();
        }
        if (sqliteHistoryEnabled()) sqliteHistory.clear();
        else savePersistedHistory();
        broadcastEvent("clear", "{\"ok\":true}");
        audit(ctx, "admin.clear-history", Map.of());
        sendJson(ex, 200, "{\"ok\":true}");
    }


    private void handleAdminSettings(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            RuntimeSettingsController.SnapshotResult snapshot = RuntimeSettingsController.snapshotFromDisk(host);
            if (!snapshot.ok()) {
                sendJson(ex, 500, "{\"ok\":false,\"error\":" + JsonUtil.quote(snapshot.error()) + "}");
                return;
            }
            Map<String,Object> res = new LinkedHashMap<>();
            res.put("ok", true);
            res.put("writeProtocol", 5);
            res.put("requestId", adminRequestId(ex, null));
            res.put("settings", snapshot.values());
            res.put("discordAlertChannels", adminDiscordAlertChannelChoices());
            sendJson(ex, 200, JsonUtil.obj(res));
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String,String> body = parsedBody(ex);
        Map<String,Object> res = new LinkedHashMap<>();
        if (body.containsKey("path")) {
            // Backward-compatible single-setting request used by older frontends/game tools.
            RuntimeSettingsController.Result result = RuntimeSettingsController.set(host, body.get("path"), body.get("value"));
            if (!result.ok()) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error()) + "}");
                return;
            }
            audit(ctx, "admin.settings-update", Map.of("path", result.path(), "value", String.valueOf(result.value())));
            res.put("ok", true);
            res.put("path", result.path());
            res.put("value", result.value());
            res.put("sessionsUpdated", result.sessionsUpdated());
            res.put("sessionsExpired", result.sessionsExpired());
        } else {
            LinkedHashMap<String,String> updates = new LinkedHashMap<>();
            for (String path : RuntimeSettingsController.SUPPORTED_PATHS) {
                if (body.containsKey(path)) updates.put(path, body.get(path));
            }
            if (updates.isEmpty()) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"no_settings\"}");
                return;
            }
            RuntimeSettingsController.BatchResult result = RuntimeSettingsController.setAll(host, updates);
            if (!result.ok()) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error()) + "}");
                return;
            }
            audit(ctx, "admin.settings-update-batch", Map.of("count", updates.size(), "paths", String.join(",", updates.keySet())));
            res.put("ok", true);
            res.put("updated", result.values());
            res.put("sessionsUpdated", result.sessionsUpdated());
            res.put("sessionsExpired", result.sessionsExpired());
        }
        RuntimeSettingsController.SnapshotResult persisted = RuntimeSettingsController.snapshotFromDisk(host);
        if (!persisted.ok()) {
            sendJson(ex, 500, "{\"ok\":false,\"error\":" + JsonUtil.quote(persisted.error()) + "}");
            return;
        }
        res.put("writeProtocol", 5);
        res.put("requestId", adminRequestId(ex, body));
        res.put("settings", persisted.values());
        res.put("discordAlertChannels", adminDiscordAlertChannelChoices());
        sendJson(ex, 200, JsonUtil.obj(res));
    }

    private List<String> adminDiscordAlertChannelChoices() {
        WebChatDiscord discord = host.discord();
        if (discord == null) return List.of();
        try {
            List<String> values = discord.adminAlertChannelChoices();
            if (values == null || values.isEmpty()) return List.of();
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String raw : values) {
                String value = stripControl(raw, 100).trim();
                if (!value.isBlank()) out.add(value);
                if (out.size() >= 100) break;
            }
            return new ArrayList<>(out);
        } catch (Throwable ex) {
            return List.of();
        }
    }

    private void handleAdminFilter(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        RuntimeSettingsController.FilterRulesResult diskRules = RuntimeSettingsController.filterRulesFromDisk(host);
        if (!diskRules.ok()) {
            sendJson(ex, 500, "{\"ok\":false,\"error\":" + JsonUtil.quote(diskRules.error()) + "}");
            return;
        }
        RuntimeSettingsController.SnapshotResult snapshot = RuntimeSettingsController.snapshotFromDisk(host);
        if (!snapshot.ok()) {
            sendJson(ex, 500, "{\"ok\":false,\"error\":" + JsonUtil.quote(snapshot.error()) + "}");
            return;
        }
        Map<String,Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("writeProtocol", 5);
        res.put("requestId", adminRequestId(ex, null));
        res.put("settings", snapshot.values());
        ArrayList<Object> rules = new ArrayList<>();
        for (ContentFilterRule rule : diskRules.rules()) rules.add(contentFilterRuleMap(rule));
        res.put("rules", rules);
        try {
            ArrayList<Object> wordLists = new ArrayList<>();
            for (ContentFilterWordListStore.ListFile file : ContentFilterWordListStore.list(host.dataDirectory())) {
                wordLists.add(contentFilterWordListMap(file));
            }
            res.put("wordLists", wordLists);
            res.put("activeWordCount", ContentFilterWordListStore.activeWordCount(host.dataDirectory()));
        } catch (Exception listEx) {
            host.logger().warn("Failed to read content-filter word lists: " + listEx.getMessage());
            sendJson(ex, 500, "{\"ok\":false,\"error\":\"filter_list_read_failed\"}");
            return;
        }
        res.put("registeredEmojiAliases", cachedContentFilterEmojiAliases.size());
        sendJson(ex, 200, JsonUtil.obj(res));
    }

    private void handleAdminFilterRules(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String,String> body = parsedBody(ex);
        String operation = String.valueOf(body.getOrDefault("operation", "upsert")).trim().toLowerCase(Locale.ROOT);
        String actor = ctx.account.safeUsername();
        RuntimeSettingsController.FilterRulesResult result;
        if ("remove".equals(operation)) {
            result = RuntimeSettingsController.removeFilterRule(host, body.get("id"), actor);
        } else if ("create".equals(operation) || "update".equals(operation)) {
            ContentFilterRule rule = new ContentFilterRule();
            rule.id = String.valueOf(body.getOrDefault("id", "")).trim();
            rule.enabled = !"false".equalsIgnoreCase(String.valueOf(body.getOrDefault("enabled", "true")));
            rule.action = body.getOrDefault("action", "block");
            rule.replacementMode = body.getOrDefault("replacementMode", "first");
            rule.words = splitAdminLines(body.get("words"));
            rule.replacements = splitAdminLines(body.get("replacements"));
            rule.mappings = parseAdminMappings(body.get("mappings"));
            rule.normalize();
            if (rule.words.isEmpty()) {
                sendJson(ex, 400, "{\"ok\":false,\"error\":\"words_required\"}");
                return;
            }
            if ("create".equals(operation)) {
                result = RuntimeSettingsController.createFilterRule(host, rule, actor);
            } else {
                result = RuntimeSettingsController.updateFilterRule(host, body.get("originalId"), rule, actor);
            }
        } else {
            sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_operation\"}");
            return;
        }
        if (!result.ok()) {
            int status = "rule_not_found".equals(result.error()) ? 404 : ("rule_id_exists".equals(result.error()) ? 409 : 400);
            sendJson(ex, status, "{\"ok\":false,\"error\":" + JsonUtil.quote(result.error()) + "}");
            return;
        }
        RuntimeSettingsController.SnapshotResult settings = RuntimeSettingsController.snapshotFromDisk(host);
        if (!settings.ok()) {
            sendJson(ex, 500, "{\"ok\":false,\"error\":" + JsonUtil.quote(settings.error()) + "}");
            return;
        }
        Map<String,Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("writeProtocol", 5);
        res.put("requestId", adminRequestId(ex, body));
        res.put("count", result.rules().size());
        ArrayList<Object> savedRules = new ArrayList<>();
        for (ContentFilterRule saved : result.rules()) savedRules.add(contentFilterRuleMap(saved));
        res.put("rules", savedRules);
        res.put("settings", settings.values());
        sendJson(ex, 200, JsonUtil.obj(res));
    }

    private void handleAdminFilterLists(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Map<String,String> query = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
            String name = String.valueOf(query.getOrDefault("name", "")).trim();
            try {
                Map<String,Object> res = new LinkedHashMap<>();
                res.put("ok", true);
                res.put("writeProtocol", 5);
                res.put("requestId", adminRequestId(ex, null));
                if (!name.isBlank()) {
                    ContentFilterWordListStore.ListFile info = ContentFilterWordListStore.info(host.dataDirectory(), name);
                    res.put("file", contentFilterWordListMap(info));
                    res.put("text", ContentFilterWordListStore.readText(host.dataDirectory(), name));
                } else {
                    ArrayList<Object> files = new ArrayList<>();
                    for (ContentFilterWordListStore.ListFile file : ContentFilterWordListStore.list(host.dataDirectory())) {
                        files.add(contentFilterWordListMap(file));
                    }
                    res.put("wordLists", files);
                    res.put("activeWordCount", ContentFilterWordListStore.activeWordCount(host.dataDirectory()));
                }
                sendJson(ex, 200, JsonUtil.obj(res));
            } catch (NoSuchFileException missing) {
                sendJson(ex, 404, "{\"ok\":false,\"error\":\"filter_list_not_found\"}");
            } catch (Exception readEx) {
                host.logger().warn("Failed to read content-filter list: " + readEx.getMessage());
                sendJson(ex, 500, "{\"ok\":false,\"error\":\"filter_list_read_failed\"}");
            }
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String,String> body = parsedBody(ex);
        String operation = String.valueOf(body.getOrDefault("operation", "save")).trim().toLowerCase(Locale.ROOT);
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        try {
            Map<String,Object> res = new LinkedHashMap<>();
            switch (operation) {
                case "save" -> {
                    boolean enabled = !"false".equalsIgnoreCase(String.valueOf(body.getOrDefault("enabled", "true")));
                    ContentFilterWordListStore.ListFile saved = ContentFilterWordListStore.save(
                            host.dataDirectory(), name, body.getOrDefault("text", ""), enabled, body.getOrDefault("action", "block"));
                    res.put("file", contentFilterWordListMap(saved));
                    audit(ctx, "admin.filter-list-save", Map.of("name", saved.name(), "enabled", saved.enabled(), "action", saved.action(), "words", saved.wordCount()));
                }
                case "toggle" -> {
                    boolean enabled = !"false".equalsIgnoreCase(String.valueOf(body.getOrDefault("enabled", "true")));
                    ContentFilterWordListStore.ListFile saved = ContentFilterWordListStore.setEnabled(host.dataDirectory(), name, enabled);
                    res.put("file", contentFilterWordListMap(saved));
                    audit(ctx, "admin.filter-list-toggle", Map.of("name", saved.name(), "enabled", saved.enabled()));
                }
                case "delete" -> {
                    boolean removed = ContentFilterWordListStore.delete(host.dataDirectory(), name);
                    if (!removed) {
                        sendJson(ex, 404, "{\"ok\":false,\"error\":\"filter_list_not_found\"}");
                        return;
                    }
                    audit(ctx, "admin.filter-list-delete", Map.of("name", ContentFilterWordListStore.normalizeLogicalName(name)));
                }
                default -> {
                    sendJson(ex, 400, "{\"ok\":false,\"error\":\"invalid_operation\"}");
                    return;
                }
            }
            RuntimeSettingsController.refreshWordListRules(host);
            ArrayList<Object> files = new ArrayList<>();
            for (ContentFilterWordListStore.ListFile file : ContentFilterWordListStore.list(host.dataDirectory())) {
                files.add(contentFilterWordListMap(file));
            }
            res.put("ok", true);
            res.put("writeProtocol", 5);
            res.put("requestId", adminRequestId(ex, body));
            res.put("wordLists", files);
            res.put("activeWordCount", ContentFilterWordListStore.activeWordCount(host.dataDirectory()));
            sendJson(ex, 200, JsonUtil.obj(res));
        } catch (NoSuchFileException missing) {
            sendJson(ex, 404, "{\"ok\":false,\"error\":\"filter_list_not_found\"}");
        } catch (Exception writeEx) {
            host.logger().warn("Failed to update content-filter list: " + writeEx.getMessage());
            String code = "list_too_large".equals(writeEx.getMessage()) ? "filter_list_too_large" : "filter_list_write_failed";
            sendJson(ex, 400, "{\"ok\":false,\"error\":" + JsonUtil.quote(code) + "}");
        }
    }

    private void handleAdminFilterTest(HttpExchange ex) throws IOException {
        if (preflight(ex)) return;
        SessionContext ctx = requireRole(ex, Role.ADMIN);
        if (ctx == null) return;
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"ok\":false,\"error\":\"method_not_allowed\"}");
            return;
        }
        Map<String,String> body = parsedBody(ex);
        ContentFilterEngine.Scope scope;
        try { scope = ContentFilterEngine.Scope.valueOf(String.valueOf(body.getOrDefault("scope", "PUBLIC")).trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { scope = ContentFilterEngine.Scope.PUBLIC; }
        RuntimeSettingsController.FilterRulesResult diskRules = RuntimeSettingsController.filterRulesFromDisk(host);
        if (!diskRules.ok()) {
            sendJson(ex, 500, "{\"ok\":false,\"error\":" + JsonUtil.quote(diskRules.error()) + "}");
            return;
        }
        String testedText = String.valueOf(body.getOrDefault("text", ""));
        ContentFilterResult result = testContentFilter(testedText, scope);
        ConfigValues c = host.configValues();
        Map<String,Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("writeProtocol", 5);
        res.put("requestId", adminRequestId(ex, body));
        res.put("testedText", testedText);
        res.put("testedScope", scope.name().toLowerCase(Locale.ROOT));
        res.put("blocked", result.blocked);
        res.put("changed", result.changed);
        res.put("message", result.message);
        res.put("matchedWord", result.matchedWord);
        res.put("matchedText", result.matchedText);
        res.put("ruleId", result.ruleId);
        res.put("matchMode", result.matchMode);
        int listRuleCount = c == null || c.contentFilterWordListRules == null ? 0 : c.contentFilterWordListRules.size();
        int listWordCount = 0;
        if (c != null && c.contentFilterWordListRules != null) {
            for (ContentFilterRule listRule : c.contentFilterWordListRules) {
                if (listRule != null && listRule.words != null) listWordCount += listRule.words.size();
            }
        }
        res.put("ruleCount", diskRules.rules().size() + listRuleCount);
        res.put("customRuleCount", diskRules.rules().size());
        res.put("wordListCount", listRuleCount);
        res.put("wordListWordCount", listWordCount);
        res.put("liveEnabled", c != null && c.contentFilterEnabled);
        res.put("scopeEnabled", c != null && switch (scope) {
            case PUBLIC -> c.contentFilterPublic;
            case GROUP -> c.contentFilterGroup;
            case DM -> c.contentFilterDm;
        });
        sendJson(ex, 200, JsonUtil.obj(res));
    }

    private Map<String,Object> contentFilterWordListMap(ContentFilterWordListStore.ListFile file) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("name", file == null ? "" : file.name());
        m.put("enabled", file != null && file.enabled());
        m.put("action", file == null ? "block" : file.action());
        m.put("wordCount", file == null ? 0 : file.wordCount());
        m.put("sizeBytes", file == null ? 0L : file.sizeBytes());
        return m;
    }

    private Map<String,Object> contentFilterRuleMap(ContentFilterRule source) {
        ContentFilterRule rule = source == null ? new ContentFilterRule() : source.copy();
        rule.normalize();
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", rule.id);
        m.put("enabled", rule.enabled);
        m.put("action", rule.action);
        m.put("words", rule.words);
        m.put("replacements", rule.replacements);
        m.put("replacementMode", rule.replacementMode);
        m.put("mappings", rule.mappings);
        return m;
    }

    private List<String> splitAdminLines(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String raw = String.valueOf(text == null ? "" : text).replace("\\n", "\n");
        for (String line : raw.split("\\r?\\n")) {
            String value = line.trim();
            if (!value.isBlank()) out.add(value);
        }
        return new ArrayList<>(out);
    }

    private Map<String,String> parseAdminMappings(String text) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        for (String line : splitAdminLines(text)) {
            int arrow = line.indexOf("=>");
            if (arrow < 1) continue;
            String from = line.substring(0, arrow).trim();
            String to = line.substring(arrow + 2).trim();
            if (!from.isBlank()) out.put(from, to);
        }
        return out;
    }

    private String adminRequestId(HttpExchange ex, Map<String,String> body) {
        // The URL nonce is generated by adminApi() for every request and is the
        // most reliable request identity because it does not depend on body parsing.
        String value = "";
        if (ex != null && ex.getRequestURI() != null) {
            value = String.valueOf(JsonUtil.parseQuery(ex.getRequestURI().getRawQuery()).getOrDefault("_kwc", ""));
        }
        if (value.isBlank() && body != null) {
            value = String.valueOf(body.getOrDefault("_requestId", ""));
        }
        return stripControl(value, 128).trim();
    }

    private void audit(SessionContext ctx, String action, Map<String, ?> details) {
        String actor = "";
        if (ctx != null && ctx.account != null) actor = ctx.account.safeUsername();
        host.audit(action, actor, details);
    }

    private Map<String, String> parsedBody(HttpExchange ex) throws IOException {
        Map<String,String> existing = parsedBodyByExchange.get(ex);
        if (existing != null) return new LinkedHashMap<>(existing);

        String requestPath = ex.getRequestURI() == null ? "" : String.valueOf(ex.getRequestURI().getPath());
        long bodyLimit = requestPath.endsWith("/admin/filter/lists")
                ? ADMIN_FILTER_REQUEST_BODY_LIMIT_BYTES : JsonUtil.DEFAULT_BODY_LIMIT_BYTES;
        String raw = JsonUtil.readBody(ex.getRequestBody(), bodyLimit);
        String contentType = String.valueOf(ex.getRequestHeaders().getFirst("Content-Type"));
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String trimmed = raw == null ? "" : raw.trim();
        Map<String, String> body;
        if (normalizedType.startsWith("application/x-www-form-urlencoded")
                || (!trimmed.isEmpty() && !trimmed.startsWith("{") && trimmed.contains("="))) {
            // Form-urlencoded remains accepted for older Admin frontends and tools.
            // The current frontend sends a CORS-simple text/plain body containing a
            // flat JSON object; JSON also remains supported for game-side callers.
            body = JsonUtil.parseQuery(raw);
        } else {
            body = JsonUtil.parseFlatObject(raw);
        }
        parsedBodyByExchange.put(ex, new LinkedHashMap<>(body));
        return body;
    }

    private String bearerToken(HttpExchange ex) {
        if (ex == null) return "";
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null) return "";
        String value = header.trim();
        if (value.length() <= 7 || !value.regionMatches(true, 0, "Bearer ", 0, 7)) return "";
        return value.substring(7).trim();
    }

    private String requestToken(HttpExchange ex, boolean allowBody) throws IOException {
        String token = bearerToken(ex);
        if (!token.isBlank()) return token;
        Map<String,String> q = JsonUtil.parseQuery(ex.getRequestURI().getRawQuery());
        token = String.valueOf(q.getOrDefault("token", "")).trim();
        if (!token.isBlank() || !allowBody || !"POST".equalsIgnoreCase(ex.getRequestMethod())) return token;
        return String.valueOf(parsedBody(ex).getOrDefault("token", "")).trim();
    }

    private SessionContext sessionForRequest(HttpExchange ex, String token) {
        String resolvedToken = token == null ? "" : token.trim();
        if (resolvedToken.isBlank()) resolvedToken = bearerToken(ex);
        SessionContext ctx = storage.getSession(resolvedToken);
        if (ctx == null || ctx.account == null) return null;
        if (ctx.account.role == Role.ADMIN && !auth.roleAllowedFromIp(Role.ADMIN, remoteIp(ex))) return null;
        return ctx;
    }

    private SessionContext sessionFromRequest(HttpExchange ex) {
        try {
            return sessionForRequest(ex, requestToken(ex, false));
        } catch (IOException ignored) {
            return null;
        }
    }

    private SessionContext requireUserSession(HttpExchange ex) throws IOException {
        String token = requestToken(ex, true);
        SessionContext raw = storage.getSession(token);
        SessionContext ctx = sessionForRequest(ex, token);
        if (ctx == null || !ctx.account.role.atLeast(Role.USER)) {
            String error = raw != null && raw.account != null && raw.account.role == Role.ADMIN
                    && !auth.roleAllowedFromIp(Role.ADMIN, remoteIp(ex)) ? "admin_ip_not_allowed" : "not_logged_in";
            sendJson(ex, 403, "{\"ok\":false,\"error\":" + JsonUtil.quote(error) + "}");
            return null;
        }
        return ctx;
    }

    private SessionContext requireRole(HttpExchange ex, Role role) throws IOException {
        String token = requestToken(ex, true);
        SessionContext raw = storage.getSession(token);
        SessionContext ctx = sessionForRequest(ex, token);
        if (ctx == null || !ctx.account.role.atLeast(role)) {
            String error = raw != null && raw.account != null && raw.account.role == Role.ADMIN
                    && !auth.roleAllowedFromIp(Role.ADMIN, remoteIp(ex)) ? "admin_ip_not_allowed" : "permission_denied";
            sendJson(ex, 403, "{\"ok\":false,\"error\":" + JsonUtil.quote(error) + "}");
            return null;
        }
        if (!host.configValues().allowWebAdminPanel) {
            sendJson(ex, 403, "{\"ok\":false,\"error\":\"admin_panel_disabled\"}");
            return null;
        }
        return ctx;
    }

    private long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String canonicalizeKnownEmojiTokens(String text, ConfigValues config) {
        String raw = String.valueOf(text == null ? "" : text);
        if (raw.isBlank() || config == null || !config.emojiEnabled) return raw;
        Matcher matcher = EMOJI_TOKEN_PATTERN.matcher(raw);
        if (!matcher.find()) return raw;

        EmojiCatalog catalog = scanEmojiCatalog(config);
        if (catalog.items.isEmpty()) return raw;
        Map<String, EmojiItem> emojiById = new HashMap<>();
        for (EmojiItem item : catalog.items) emojiById.put(item.id, item);
        Map<String, String> aliasToId = emojiAliasToWebId(catalog, config);

        matcher.reset();
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            EmojiItem item = emojiItemForToken(matcher.group(1), emojiById, aliasToId);
            String replacement = item == null ? matcher.group(0) : ":" + item.id + ":";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String messageForGameChat(String message, ConfigValues config) {
        String text = String.valueOf(message == null ? "" : message);
        if (config == null || !config.emojiEnabled || !config.emojiGameLinkEnabled || isEmojiGameTokenPreserveMode(config)) return text;
        Matcher matcher = EMOJI_TOKEN_PATTERN.matcher(text);
        if (!matcher.find()) return text;

        EmojiCatalog catalog = scanEmojiCatalog(config);
        Map<String, EmojiItem> emojiById = new HashMap<>();
        for (EmojiItem item : catalog.items) {
            emojiById.put(item.id, item);
        }
        Map<String, String> aliasToId = emojiAliasToWebId(catalog, config);
        matcher.reset();

        String mode = normalizedEmojiGameLinkMode(config);
        String shortBase = publicShortEmojiBaseUrlForGame(config);
        int maxLinks = Math.max(0, config.emojiGameLinkMaxLinksPerMessage);
        int linked = 0;
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            EmojiItem item = emojiItemForToken(token, emojiById, aliasToId);
            if (item == null) {
                // Unknown :name: tokens might belong to another plugin. Keep them unchanged.
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String replacement = emojiGameLabel(item, config);
            boolean shouldAppendLink = item != null
                    && !shortBase.isBlank()
                    && (maxLinks <= 0 || linked < maxLinks)
                    && mode.equals("link");
            if (shouldAppendLink) {
                replacement = replacement + " " + shortBase + "/" + shortEmojiId(item.id);
                linked++;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private EmojiItem emojiItemForToken(String token, Map<String, EmojiItem> emojiById, Map<String, String> aliasToId) {
        String raw = String.valueOf(token == null ? "" : token).trim();
        if (raw.isBlank()) return null;
        EmojiItem item = emojiById == null ? null : emojiById.get(raw);
        if (item != null) return item;
        String id = emojiAliasLookup(aliasToId, raw);
        if (id != null && emojiById != null) return emojiById.get(id);
        return null;
    }

    private String emojiGameLabel(EmojiItem item, ConfigValues config) {
        String name = stripControl(item == null ? "" : item.name, 80).trim();
        if (name.isBlank() && item != null) name = stripControl(item.label, 80).trim();
        if (name.isBlank()) name = "emoji";
        String pack = stripControl(item == null ? "" : item.pack, 80).trim();
        String id = stripControl(item == null ? name : item.id, 200).trim();
        if (id.isBlank()) id = pack.isBlank() ? name : pack + "/" + name;
        String format = config == null ? "" : String.valueOf(config.emojiGameLinkLabelFormat == null ? "" : config.emojiGameLinkLabelFormat);
        if (format.isBlank()) format = ":{id}:";
        return stripControl(format
                .replace("{name}", name)
                .replace("{pack}", pack)
                .replace("{id}", id), 240).trim();
    }


    private String emojiTokenFallbackLabel(String id) {
        String raw = String.valueOf(id == null ? "" : id).replace("\\", "/").trim();
        int slash = raw.lastIndexOf('/');
        String name = slash >= 0 ? raw.substring(slash + 1) : raw;
        name = stripControl(name, 80).trim();
        if (name.isBlank()) name = "emoji";
        return ":" + name + ":";
    }



    private Map<String, String> emojiAliasToWebId(EmojiCatalog catalog, ConfigValues config) {
        Map<String, String> out = new LinkedHashMap<>();
        if (catalog == null) return out;

        // Explicit mapping has the highest priority. This is useful when a game-side
        // token such as :name: should map to a BM Web Chat pack/name emoji.
        if (config != null && config.emojiGameLinkAliases != null) {
            for (Map.Entry<String, String> entry : config.emojiGameLinkAliases.entrySet()) {
                String alias = String.valueOf(entry.getKey() == null ? "" : entry.getKey()).trim();
                String id = String.valueOf(entry.getValue() == null ? "" : entry.getValue()).trim();
                if (alias.isBlank() || id.isBlank()) continue;
                EmojiItem item = emojiItemById(catalog, id);
                if (item != null) addEmojiAlias(out, alias, item.id);
            }
        }

        // Fully-qualified IDs are always unambiguous.
        for (EmojiItem item : catalog.items) {
            addEmojiAlias(out, item.id, item.id);
            addEmojiAlias(out, emojiTokenFallbackLabel(item.id), item.id);
            if (item.pack != null && !item.pack.isBlank()) {
                addEmojiAlias(out, item.pack + "/" + item.name, item.id);
                addEmojiAlias(out, item.pack + "/" + item.label, item.id);
            }
        }

        // If a default game-link pack is configured, prefer that pack for flat :name: aliases.
        String defaultPack = normalizedEmojiPackName(config == null ? "" : config.emojiGameLinkDefaultPack);
        if (!defaultPack.isBlank()) {
            for (EmojiItem item : catalog.items) {
                if (!normalizedEmojiPackName(item.pack).equals(defaultPack)
                        && !normalizedEmojiPackName(item.label).equals(defaultPack)) continue;
                addEmojiAlias(out, item.name, item.id);
                addEmojiAlias(out, item.label, item.id);
                addEmojiAlias(out, emojiGameLabel(item, config), item.id);
            }
        }

        // For non-conflicting names, let :name: work automatically. Ambiguous aliases are
        // intentionally skipped to avoid mapping a flat token to the wrong packed emoji.
        Map<String, List<EmojiItem>> byAlias = new LinkedHashMap<>();
        for (EmojiItem item : catalog.items) {
            collectEmojiAliasCandidate(byAlias, item.name, item);
            collectEmojiAliasCandidate(byAlias, item.label, item);
            collectEmojiAliasCandidate(byAlias, emojiGameLabel(item, config), item);
        }
        for (Map.Entry<String, List<EmojiItem>> entry : byAlias.entrySet()) {
            List<EmojiItem> matches = entry.getValue();
            if (matches.size() == 1) addEmojiAlias(out, entry.getKey(), matches.get(0).id);
        }
        return out;
    }

    private EmojiItem emojiItemById(EmojiCatalog catalog, String id) {
        String raw = stripControl(id, 200).trim();
        if (catalog == null || raw.isBlank()) return null;
        for (EmojiItem item : catalog.items) {
            if (item.id.equals(raw)) return item;
        }
        return null;
    }

    private String normalizedEmojiPackName(String value) {
        String raw = stripControl(value, 200).trim();
        if (raw.isBlank()) return "";
        try {
            raw = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFC);
        } catch (Throwable ignored) {
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private void collectEmojiAliasCandidate(Map<String, List<EmojiItem>> byAlias, String alias, EmojiItem item) {
        if (byAlias == null || item == null) return;
        for (String key : emojiAliasKeys(alias)) {
            String cleaned = key.trim();
            if (cleaned.isBlank()) continue;
            byAlias.computeIfAbsent(cleaned, ignored -> new ArrayList<>()).add(item);
            String lower = cleaned.toLowerCase(Locale.ROOT);
            if (!lower.equals(cleaned)) byAlias.computeIfAbsent(lower, ignored -> new ArrayList<>()).add(item);
        }
    }

    private void addEmojiAlias(Map<String, String> out, String alias, String id) {
        String value = stripControl(id, 200).trim();
        if (value.isBlank()) return;
        for (String key : emojiAliasKeys(alias)) {
            if (key.isBlank()) continue;
            out.putIfAbsent(key, value);
            out.putIfAbsent(key.toLowerCase(Locale.ROOT), value);
        }
    }

    private List<String> emojiAliasKeys(String alias) {
        String raw = stripControl(alias, 200).trim();
        if (raw.isBlank()) return List.of();
        List<String> keys = new ArrayList<>();
        addEmojiAliasKey(keys, raw);
        if (raw.startsWith(":") && raw.endsWith(":") && raw.length() > 2) {
            String inner = raw.substring(1, raw.length() - 1).trim();
            if (inner.startsWith("emoji:") && inner.length() > "emoji:".length()) {
                inner = inner.substring("emoji:".length()).trim();
            }
            addEmojiAliasKey(keys, inner);
        }
        if (raw.startsWith("emoji:") && raw.length() > "emoji:".length()) {
            addEmojiAliasKey(keys, raw.substring("emoji:".length()).trim());
        }
        return keys;
    }

    private void addEmojiAliasKey(List<String> keys, String value) {
        String key = String.valueOf(value == null ? "" : value).trim();
        if (key.isBlank()) return;
        keys.add(key);
        try {
            String nfc = java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFC);
            String nfkc = java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFKC);
            if (!nfc.equals(key)) keys.add(nfc);
            if (!nfkc.equals(key) && !nfkc.equals(nfc)) keys.add(nfkc);
        } catch (Throwable ignored) {
        }
    }


    private String emojiAliasLookup(Map<String, String> aliasToId, String alias) {
        if (aliasToId == null || aliasToId.isEmpty()) return null;
        for (String key : emojiAliasKeys(alias)) {
            String id = aliasToId.get(key);
            if (id == null) id = aliasToId.get(key.toLowerCase(Locale.ROOT));
            if (id != null && !id.isBlank()) return id;
        }
        return null;
    }

    private String publicShortEmojiBaseUrlForGame(ConfigValues config) {
        String api = publicApiBaseUrlForGame(config);
        return api.isBlank() ? "" : api + "/e";
    }

    private String publicApiBaseUrlForGame(ConfigValues config) {
        if (config == null) return "";
        for (String candidate : new String[]{
                config.emojiGameLinkPublicApiBaseUrl,
                config.emojiPublicBaseUrl
        }) {
            String resolved = normalizeGamePublicApiBaseUrl(candidate, config);
            if (!resolved.isBlank()) return stripKnownResourceSuffix(resolved);
        }
        String origin = configuredCorsOrigin(config);
        if (!origin.isBlank()) {
            return trimTrailingSlash(origin + joinPublicPath(config.publicPrefix, normalizeContextPrefix(config.pathPrefix, "/api")));
        }
        return "";
    }

    private String normalizeGamePublicApiBaseUrl(String configured, ConfigValues config) {
        String value = String.valueOf(configured == null ? "" : configured).trim();
        if (value.isBlank()) return "";
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return trimTrailingSlash(value);
        }
        if (value.startsWith("//")) {
            return trimTrailingSlash("https:" + value);
        }
        String origin = configuredCorsOrigin(config);
        if (origin.isBlank()) return "";
        if (value.startsWith("/")) return trimTrailingSlash(origin + value);
        return trimTrailingSlash(origin + "/" + value.replaceFirst("^/+", ""));
    }

    private String shortEmojiId(String emojiId) {
        return SecurityUtil.sha256Hex(String.valueOf(emojiId == null ? "" : emojiId)).substring(0, 8);
    }
    private boolean shouldPreservePlainBroadcastForGameEmojiTokens(ChatMessage msg, String renderedLine, ConfigValues config) {
        return shouldPreservePlainBroadcastForGameEmojiTokens(String.valueOf(msg == null ? "" : msg.message), renderedLine, config);
    }

    private boolean shouldPreservePlainBroadcastForGameEmojiTokens(String rawMessage, String renderedLine, ConfigValues config) {
        if (config == null || !isEmojiGameTokenPreserveMode(config)) return false;
        // Interactive components bypass ImageEmojis' BroadcastMessageEvent listener.
        // We therefore resolve known ImageEmojis tokens to the receiving server's
        // runtime glyph before building the component. Use the plain broadcast fallback
        // only when a known token is still present after that conversion.
        String rendered = String.valueOf(renderedLine == null ? "" : renderedLine);
        return containsKnownEmojiTokenLiteral(rendered, config);
    }

    private boolean isEmojiGameTokenPreserveMode(ConfigValues config) {
        if (config == null || !config.emojiEnabled) return true;
        if (!config.emojiGameLinkEnabled) return true;
        return normalizedEmojiGameLinkMode(config).equals("preserve");
    }

    private String normalizedEmojiGameLinkMode(ConfigValues config) {
        String mode = String.valueOf(config == null || config.emojiGameLinkMode == null ? "link" : config.emojiGameLinkMode).trim().toLowerCase(Locale.ROOT);
        if (mode.equals("preserve") || mode.equals("token") || mode.equals("original") || mode.equals("none")) return "preserve";
        if (mode.equals("label") || mode.equals("template") || mode.equals("text")) return "label";
        return "link";
    }

    private boolean containsEmojiTokenLiteral(String text) {
        String raw = String.valueOf(text == null ? "" : text);
        return !raw.isBlank() && EMOJI_TOKEN_PATTERN.matcher(raw).find();
    }

    private boolean containsKnownEmojiTokenLiteral(String text, ConfigValues config) {
        String raw = String.valueOf(text == null ? "" : text);
        if (raw.isBlank() || config == null || !config.emojiEnabled) return false;
        Matcher matcher = EMOJI_TOKEN_PATTERN.matcher(raw);
        if (!matcher.find()) return false;

        EmojiCatalog catalog = scanEmojiCatalog(config);
        Map<String, EmojiItem> emojiById = new HashMap<>();
        for (EmojiItem item : catalog.items) {
            emojiById.put(item.id, item);
        }
        Map<String, String> aliasToId = emojiAliasToWebId(catalog, config);
        matcher.reset();
        while (matcher.find()) {
            if (emojiItemForToken(matcher.group(1), emojiById, aliasToId) != null) return true;
        }
        return false;
    }

    private String renderImageEmojiSymbolsForGame(String text) {
        String raw = String.valueOf(text == null ? "" : text);
        Matcher matcher = EMOJI_TOKEN_PATTERN.matcher(raw);
        if (!matcher.find()) return raw;

        Map<String, String> symbols = imageEmojiRuntimeSymbols();
        if (symbols.isEmpty()) return raw;

        matcher.reset();
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String symbol = imageEmojiRuntimeSymbol(symbols, matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(symbol.isBlank() ? matcher.group(0) : symbol));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private Map<String, String> imageEmojiRuntimeSymbols() {
        long now = System.currentTimeMillis();
        Map<String, String> cached = imageEmojiRuntimeSymbols;
        if (now - imageEmojiRuntimeSymbolsLoadedAt < 5000L) return cached;

        // HTTP/relay handlers are asynchronous. Platform plugin registries are read
        // only on the game thread and the immutable snapshot is reused off-thread.
        if (!platform.isMainThread()) {
            if (imageEmojiRuntimeRefreshScheduled.compareAndSet(false, true)) {
                platform.runMainThread(() -> {
                    try {
                        refreshImageEmojiRuntimeSymbols();
                    } finally {
                        imageEmojiRuntimeRefreshScheduled.set(false);
                    }
                });
            }
            return cached;
        }
        return refreshImageEmojiRuntimeSymbols();
    }

    private synchronized Map<String, String> refreshImageEmojiRuntimeSymbols() {
        long now = System.currentTimeMillis();
        if (now - imageEmojiRuntimeSymbolsLoadedAt < 5000L) return imageEmojiRuntimeSymbols;

        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> entry : platform.imageEmojiRuntimeSymbols().entrySet()) {
                addImageEmojiRuntimeSymbol(out, entry.getKey(), entry.getValue());
            }
        } catch (Throwable ignored) {
        }
        imageEmojiRuntimeSymbols = out.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(out));
        imageEmojiRuntimeSymbolsLoadedAt = now;
        return imageEmojiRuntimeSymbols;
    }

    private void addImageEmojiRuntimeSymbol(Map<String, String> out, String alias, String symbol) {
        if (out == null || symbol == null || symbol.isBlank()) return;
        for (String key : emojiAliasKeys(alias)) {
            String normalized = key.trim();
            if (normalized.isBlank()) continue;
            out.putIfAbsent(normalized, symbol);
            out.putIfAbsent(normalized.toLowerCase(Locale.ROOT), symbol);
        }
    }

    private String imageEmojiRuntimeSymbol(Map<String, String> symbols, String token) {
        if (symbols == null || symbols.isEmpty()) return "";
        for (String key : emojiAliasKeys(token)) {
            String symbol = symbols.get(key);
            if (symbol == null) symbol = symbols.get(key.toLowerCase(Locale.ROOT));
            if (symbol != null && !symbol.isBlank()) return symbol;
        }
        return "";
    }

    private String translateGameFormatCodes(String value) {
        return LegacyText.translateAlternateColorCodes('&', String.valueOf(value == null ? "" : value));
    }

    private String sanitizeConfiguredGameLine(String value, int maxLength) {
        // For configured game-chat formats only. This is used by reply.game-preview.format
        // and reply.game-prefix.text after placeholder replacement so legacy codes such
        // as &7 are always converted before the line is sent to Minecraft.
        return sanitizeSingleGameLine(translateGameFormatCodes(value), maxLength);
    }

    private void prepareServerRelay(ChatMessage msg) {
        ServerRelay relay = host.serverRelay();
        if (relay != null) relay.prepareLocal(msg);
    }

    private void publishServerRelay(ChatMessage msg) {
        ServerRelay relay = host.serverRelay();
        if (relay != null) relay.publishLocal(msg);
    }

    public boolean hasMessageId(String id) {
        if (id == null || id.isBlank()) return false;
        if (findTransientReplyTarget(id) != null) return true;
        synchronized (history) {
            for (ChatMessage existing : history) {
                if (id.equals(existing.id)) return true;
            }
        }
        return sqliteHistory != null && sqliteHistory.find(id) != null;
    }

    public void acceptRelayedMessage(ChatMessage msg) {
        if (msg == null || msg.message == null || msg.message.isBlank()) return;
        rememberRelayedPlayerIdentity(msg);
        if (hasMessageId(msg.id)) return;
        ConfigValues config = host.configValues();
        ContentFilterResult filtered = filterContent(msg.message, ContentFilterEngine.Scope.PUBLIC);
        if (filtered.blocked) return;
        if (filtered.changed) {
            msg.message = canonicalizeKnownEmojiTokens(host.applyMessageTokens(filtered.message), config);
            msg.gameMessage = host.applyMessageTokensForGame(filtered.message);
        }
        if (config.serverRelayDeliverToWeb) {
            prewarmExternalMediaCache(msg.message);
            addHistory(msg);
            broadcast(msg);
            dispatchWebPushChat(msg);
        }
        adminDiscordAlerts.inspect(msg, AdminDiscordAlertManager.Scope.RELAY);
        if (config.serverRelayDeliverToGame) {
            if (!config.serverRelayDeliverToWeb) cacheTransientReplyTarget(msg);
            sendRelayedToGame(msg, config.serverRelayGameFormat);
        }
    }

    public boolean acceptRelayedDirectMessage(String relayId, String originServerId, String originServerName,
                                               String senderUuid, String senderUsername, String senderDisplayName,
                                               String targetUuid, String targetUsername, String targetDisplayName,
                                               String rawMessage, String rawGameMessage) {
        ConfigValues config = host.configValues();
        if (config == null || !config.directMessageEnabled || host.directMessages() == null
                || !host.directMessages().available()) return false;

        String originId = RemotePlayerRef.normalizeServerId(stripControl(originServerId, 64));
        String senderRealUuid = RemotePlayerRef.normalizePlayerUuid(stripControl(senderUuid, 80));
        String targetRealUuid = RemotePlayerRef.normalizePlayerUuid(stripControl(targetUuid, 80));
        String message = stripDirectMessage(rawMessage, config.directMessageMaxMessageLength);
        ContentFilterResult filtered = filterContent(message, ContentFilterEngine.Scope.DM);
        if (filtered.blocked) return false;
        message = filtered.message;
        String gameNoticeMessage = filtered.changed ? host.applyMessageTokensForGame(message) : String.valueOf(rawGameMessage == null ? "" : rawGameMessage);
        if (gameNoticeMessage.isBlank()) gameNoticeMessage = message;
        if (originId.isBlank() || senderRealUuid.isBlank() || targetRealUuid.isBlank() || message.isBlank()) return false;

        PlayerIdentity target = storage.findKnownPlayerByUuid(targetRealUuid);
        String safeTargetUsername = stripControl(targetUsername, 64).trim();
        String safeTargetDisplay = stripControl(targetDisplayName, 96).trim();
        if (target == null && (!safeTargetUsername.isBlank() || !safeTargetDisplay.isBlank())) {
            if (safeTargetDisplay.isBlank()) safeTargetDisplay = safeTargetUsername;
            storage.updateLastDisplayName(targetRealUuid, safeTargetUsername, safeTargetDisplay);
            target = storage.findKnownPlayerByUuid(targetRealUuid);
        }
        if (target == null) return false;

        String remoteSenderKey = RemotePlayerRef.key(originId, senderRealUuid);
        if (remoteSenderKey.isBlank()) return false;
        String safeSenderUsername = stripControl(senderUsername, 64).trim();
        String safeSenderDisplay = stripControl(senderDisplayName, 96).trim();
        String remoteSenderDisplay = RemotePlayerRef.decorateDisplayName(
                safeSenderDisplay, safeSenderUsername, stripControl(originServerName, 96), originId);
        storage.updateLastDisplayName(remoteSenderKey, safeSenderUsername, remoteSenderDisplay);

        DirectMessageStore.SendResult result = host.directMessages().receiveRelayed(remoteSenderKey, targetRealUuid, message, relayId);
        if (!result.ok) return false;
        if (result.duplicate) return true;
        adminDiscordAlerts.inspect("dm-relay:" + stripControl(relayId, 180), remoteSenderDisplay, "relay", message, AdminDiscordAlertManager.Scope.DM);
        String threadId = result.thread == null ? "" : result.thread.id;
        long messageId = result.message == null ? 0L : result.message.id;
        publishDirectMessageUpdate(remoteSenderKey, targetRealUuid, threadId);
        dispatchWebPushDirectMessage(remoteSenderKey, remoteSenderDisplay, targetRealUuid, target.label(), threadId, messageId, message);
        notifyOnlineDirectMessage(remoteSenderDisplay, target, gameNoticeMessage);
        return true;
    }


    private void rememberRelayedPlayerIdentitiesFromHistory() {
        synchronized (history) {
            for (ChatMessage message : history) rememberRelayedPlayerIdentity(message);
        }
    }

    private void rememberRelayedPlayerIdentity(ChatMessage msg) {
        if (msg == null) return;
        ConfigValues config = host.configValues();
        String originServerId = stripControl(msg.originServerId, 64).trim();
        String localServerId = config == null ? "" : stripControl(config.serverRelayServerId, 64).trim();
        boolean remoteOrigin = msg.relayHop > 0
                || (!originServerId.isBlank() && (localServerId.isBlank() || !originServerId.equalsIgnoreCase(localServerId)));
        if (!remoteOrigin) return;

        String source = stripControl(msg.source, 32).trim().toLowerCase(Locale.ROOT);
        if (!source.equals("game") && !source.equals("web")) return;

        String uuid = stripControl(msg.playerUuid, 80).trim().toLowerCase(Locale.ROOT);
        String displayName = stripControl(msg.sender, 96).trim();
        String username = stripControl(msg.realSender, 64).trim();
        if (uuid.isBlank() || displayName.isBlank()) return;

        // A player on another server must not reuse the local UUID key.  The
        // server-scoped key keeps same-UUID players on different servers distinct
        // in DM search, threads, unread state, and delivery routing.
        String remoteKey = RemotePlayerRef.key(originServerId, uuid);
        if (remoteKey.isBlank()) return;
        String serverLabel = stripControl(msg.originServerName, 96).trim();
        String remoteDisplay = RemotePlayerRef.decorateDisplayName(displayName, username, serverLabel, originServerId);
        storage.updateLastDisplayName(remoteKey, username, remoteDisplay);
    }

    private String gameMessageSource(ChatMessage msg) {
        if (msg == null) return "";
        if (msg.gameMessage != null && !msg.gameMessage.isBlank()) return msg.gameMessage;
        return String.valueOf(msg.message == null ? "" : msg.message);
    }

    private String restoreTokenGameBreaks(String value) {
        return host.restoreMessageTokenGameBreaks(value);
    }

    private void sendRelayedToGame(ChatMessage msg, String format) {
        ConfigValues config = host.configValues();
        String rawTemplate = String.valueOf(format == null ? "" : format);
        String template = translateGameFormatCodes(rawTemplate);
        String gameMessageProtected = renderImageEmojiSymbolsForGame(messageForGameChat(gameMessageSource(msg), config));
        String gameMessage = restoreTokenGameBreaks(gameMessageProtected);
        String source = stripControl(msg == null ? "" : msg.source, 32);
        String serverId = stripControl(msg == null ? "" : msg.originServerId, 64);
        String serverName = stripControl(msg == null || msg.originServerName == null || msg.originServerName.isBlank() ? serverId : msg.originServerName, 96);
        String sender = stripControl(msg == null ? "" : msg.sender, 96);
        String realSender = stripControl(msg == null ? "" : msg.realSender, 96);
        String playerUuid = stripControl(msg == null ? "" : msg.playerUuid, 64);
        String role = stripControl(msg == null ? "" : msg.role, 32);
        String line = template
                .replace("{server}", serverName)
                .replace("{server_id}", serverId)
                .replace("{source}", source)
                .replace("{sender}", sender)
                .replace("{player}", sender)
                .replace("{guest}", sender)
                .replace("{real_sender}", realSender)
                .replace("{uuid}", playerUuid)
                .replace("{role}", role)
                .replace("{message}", gameMessageProtected);
        line = applyAutomaticServerGamePrefix(rawTemplate, line, serverName, msg, config);
        line = sanitizeSingleGameLine(applyReplyGameLinePrefix(msg, line, config), 32768);
        final String finalReplyLine = gameReplyPreviewLine(msg, config);
        final GameLineHover finalHover = gameLineHover(msg, restoreTokenGameBreaks(line), gameMessage, config);
        final String finalLine = line;
        boolean preservePlainForReply = shouldPreservePlainBroadcastForGameEmojiTokens(
                String.valueOf(msg == null ? "" : msg.replyToPreview), finalReplyLine, config);
        boolean preservePlainForTokens = shouldPreservePlainBroadcastForGameEmojiTokens(msg, finalLine, config);
        platform.runMainThread(() -> {
            if (!finalReplyLine.isBlank()) broadcastGameLine(finalReplyLine, preservePlainForReply, config);
            broadcastGameLine(finalLine, preservePlainForTokens, config, finalHover);
        });
    }

    private void sendToGame(ChatMessage msg, String format) {
        ConfigValues config = host.configValues();
        if (!config.sendWebChatToGame) return;

        // Translate color/format codes only in configured templates, not in user text.
        // This keeps user-provided literals such as "&n", "&l", "&a" intact when relayed to game chat.
        String rawTemplate = String.valueOf(format == null ? "" : format);
        String template = translateGameFormatCodes(rawTemplate);
        String gameMessageProtected = renderImageEmojiSymbolsForGame(messageForGameChat(gameMessageSource(msg), config));
        String gameMessage = restoreTokenGameBreaks(gameMessageProtected);
        String sender = String.valueOf(msg == null || msg.sender == null ? "" : msg.sender);
        String serverId = stripControl(msg == null ? "" : msg.originServerId, 64);
        String serverName = stripControl(msg == null || msg.originServerName == null || msg.originServerName.isBlank() ? serverId : msg.originServerName, 96);
        String line = template
                .replace("{server}", serverName)
                .replace("{server_id}", serverId)
                .replace("{player}", sender)
                .replace("{guest}", sender)
                .replace("{message}", gameMessageProtected);
        line = applyReplyGameLinePrefix(msg, line, config);
        line = applyAutomaticServerGamePrefix(rawTemplate, line, serverName, msg, config);

        final String finalReplyLine = gameReplyPreviewLine(msg, config);
        final GameLineHover finalHover = gameLineHover(msg, restoreTokenGameBreaks(line), gameMessage, config);
        final String finalLine = line;
        boolean preservePlainForReply = shouldPreservePlainBroadcastForGameEmojiTokens(
                String.valueOf(msg == null ? "" : msg.replyToPreview), finalReplyLine, config);
        boolean preservePlainForGameEmojiTokens = shouldPreservePlainBroadcastForGameEmojiTokens(msg, finalLine, config);
        platform.runMainThread(() -> {
            if (!finalReplyLine.isBlank()) {
                broadcastGameLine(finalReplyLine, preservePlainForReply, config);
            }
            broadcastGameLine(finalLine, preservePlainForGameEmojiTokens, config, finalHover);
        });
    }

    private void sendGameCommandReplyToGame(ChatMessage msg, ConfigValues config) {
        sendGameCommandReplyToGame(msg, config, msg == null ? "" : msg.message);
    }

    private void sendGameCommandReplyToGame(ChatMessage msg, ConfigValues config, String gameDisplayMessage) {
        if (msg == null || config == null) return;
        String rawTemplate = String.valueOf(config.replyGameCommandFormat == null ? "" : config.replyGameCommandFormat);
        if (rawTemplate.isBlank()) rawTemplate = "&8[&dReply&8] &f{player}&7: &f{message}";
        String template = translateGameFormatCodes(rawTemplate);
        String gameMessageProtected = renderImageEmojiSymbolsForGame(messageForGameChat(gameDisplayMessage, config));
        String gameMessage = restoreTokenGameBreaks(gameMessageProtected);
        String serverId = stripControl(msg.originServerId, 64);
        String serverName = stripControl(msg.originServerName == null || msg.originServerName.isBlank() ? serverId : msg.originServerName, 96);
        String sender = stripControl(msg.sender, 96);
        String line = template
                .replace("{server}", serverName)
                .replace("{server_id}", serverId)
                .replace("{player}", sender)
                .replace("{sender}", sender)
                .replace("{message}", gameMessageProtected);
        line = applyReplyGameLinePrefix(msg, line, config);
        line = applyAutomaticServerGamePrefix(rawTemplate, line, serverName, msg, config);
        line = sanitizeSingleGameLine(line, 32768);

        String replyPreview = gameReplyPreviewLine(msg, config);
        GameLineHover interaction = gameLineHover(msg, restoreTokenGameBreaks(line), gameMessage, config);
        boolean preservePreview = shouldPreservePlainBroadcastForGameEmojiTokens(msg.replyToPreview, replyPreview, config);
        boolean preserveLine = shouldPreservePlainBroadcastForGameEmojiTokens(gameDisplayMessage, line, config);
        final String finalLine = line;
        platform.runMainThread(() -> {
            if (!replyPreview.isBlank()) broadcastGameLine(replyPreview, preservePreview, config);
            broadcastGameLine(finalLine, preserveLine, config, interaction);
        });
    }

    private String applyAutomaticServerGamePrefix(String rawTemplate, String renderedLine, String serverName, ChatMessage msg, ConfigValues config) {
        String line = String.valueOf(renderedLine == null ? "" : renderedLine);
        if (config == null || !config.serverRelayEnabled || serverName == null || serverName.isBlank()) return line;
        // A local web/game message is already being viewed on its origin server.
        // Only remote relay messages need an origin-server prefix.
        if (isLocalMessageOrigin(msg, config)) return line;
        String template = String.valueOf(rawTemplate == null ? "" : rawTemplate);
        if (template.contains("{server}") || template.contains("{server_id}")) return line;
        return LegacyText.DARK_GRAY + "[" + LegacyText.AQUA + serverName + LegacyText.DARK_GRAY + "] " + LegacyText.RESET + line;
    }

    private boolean isLocalMessageOrigin(ChatMessage msg, ConfigValues config) {
        String originId = stripControl(msg == null ? "" : msg.originServerId, 64).trim();
        if (originId.isBlank()) return true;
        String localId = stripControl(config == null ? "" : config.serverRelayServerId, 64).trim();
        if (!localId.isBlank()) return originId.equalsIgnoreCase(localId);
        String originName = stripControl(msg == null ? "" : msg.originServerName, 96).trim();
        String localName = stripControl(config == null ? "" : config.serverRelayServerName, 96).trim();
        return !originName.isBlank() && !localName.isBlank() && originName.equalsIgnoreCase(localName);
    }

    private boolean shouldShowReplyPreviewInGame(ChatMessage msg, ConfigValues config) {
        return msg != null
                && config != null
                && config.replyGamePreviewEnabled
                && msg.replyToId != null
                && !msg.replyToId.isBlank();
    }

    private String gameReplyPreviewLine(ChatMessage msg, ConfigValues config) {
        if (!shouldShowReplyPreviewInGame(msg, config)) return "";
        String sender = stripControl(msg.replyToSender, 64).trim();
        if (sender.isBlank()) sender = "Unknown";

        String rawPreview = String.valueOf(msg.replyToPreview == null ? "" : msg.replyToPreview)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        int max = Math.max(0, config.replyGamePreviewMaxLength);
        rawPreview = truncateVisible(rawPreview, max);

        String gamePreview = renderImageEmojiSymbolsForGame(messageForGameChat(rawPreview, config));
        gamePreview = truncateVisible(gamePreview, max);
        if (gamePreview.isBlank()) gamePreview = "...";

        String format = String.valueOf(config.replyGamePreviewFormat == null ? "" : config.replyGamePreviewFormat);
        if (format.isBlank()) format = "&7{sender}: {preview}";
        String line = format
                .replace("{sender}", sender)
                .replace("{preview}", gamePreview)
                .replace("{id}", stripControl(msg.replyToId, 96));
        return sanitizeConfiguredGameLine(line, max > 0 ? max + 96 : 512).trim();
    }

    private String applyReplyGameLinePrefix(ChatMessage msg, String renderedLine, ConfigValues config) {
        String text = String.valueOf(renderedLine == null ? "" : renderedLine);
        if (msg == null || config == null || !config.replyGamePrefixEnabled) return text;
        if (msg.replyToId == null || msg.replyToId.isBlank()) return text;
        String prefix = formatReplyGamePrefix(msg, config);
        if (prefix.isBlank()) return text;
        String serverLabel = stripMinecraftFormatting(String.valueOf(
                msg.originServerName == null || msg.originServerName.isBlank() ? msg.originServerId : msg.originServerName)).trim();
        int[] firstBracket = firstVisibleBracketRange(text, 160);
        if (!serverLabel.isBlank() && firstBracket[0] >= 0 && firstBracket[1] > firstBracket[0]) {
            String existing = stripMinecraftFormatting(text.substring(firstBracket[0] + 1, firstBracket[1])).trim();
            if (existing.equalsIgnoreCase(serverLabel)) {
                int after = firstBracket[1] + 1;
                while (after < text.length() && Character.isWhitespace(text.charAt(after))) after++;
                String spacer = prefix.endsWith(" ") || after >= text.length() ? "" : " ";
                return text.substring(0, firstBracket[1] + 1) + " " + prefix + spacer + text.substring(after);
            }
        }
        String line = replaceFirstBracketLabelOrPrepend(text, prefix, 96);
        return line;
    }

    private int[] firstVisibleBracketRange(String text, int searchLimit) {
        String line = String.valueOf(text == null ? "" : text);
        int limit = searchLimit <= 0 ? line.length() : Math.min(line.length(), searchLimit);
        for (int i = 0; i < limit; i++) {
            char ch = line.charAt(i);
            if (ch == LegacyText.COLOR_CHAR && i + 1 < limit) {
                i++;
                continue;
            }
            if (ch != '[') continue;
            int end = line.indexOf(']', i + 1);
            if (end >= 0 && end < limit) return new int[]{i, end};
            break;
        }
        return new int[]{-1, -1};
    }

    private String formatReplyGamePrefix(ChatMessage msg, ConfigValues config) {
        String prefix = String.valueOf(config == null || config.replyGamePrefixText == null ? "" : config.replyGamePrefixText);
        if (prefix.isBlank()) return "";
        String sender = stripControl(msg == null ? "" : msg.replyToSender, 64);
        String preview = truncateVisible(stripControl(msg == null ? "" : msg.replyToPreview, 240), Math.max(0, config == null ? 0 : config.replyGamePreviewMaxLength));
        prefix = prefix
                .replace("{sender}", sender)
                .replace("{preview}", preview)
                .replace("{id}", stripControl(msg == null ? "" : msg.replyToId, 96));
        // Keep game-prefix color handling local to the configured reply prefix so
        // normal user message text is not globally color-translated.
        return sanitizeConfiguredGameLine(prefix, 160);
    }

    private String replaceFirstBracketLabelOrPrepend(String text, String prefix, int searchLimit) {
        String line = String.valueOf(text == null ? "" : text);
        String label = String.valueOf(prefix == null ? "" : prefix);
        if (label.isBlank()) return line;

        int limit = searchLimit <= 0 ? line.length() : Math.min(line.length(), searchLimit);
        for (int i = 0; i < limit; i++) {
            char ch = line.charAt(i);
            if (ch == LegacyText.COLOR_CHAR && i + 1 < limit) {
                i++;
                continue;
            }
            if (ch != '[') continue;
            int end = line.indexOf(']', i + 1);
            if (end < 0 || end >= limit) break;
            int after = end + 1;
            while (after < line.length() && Character.isWhitespace(line.charAt(after))) after++;
            String spacer = label.endsWith(" ") || after >= line.length() ? "" : " ";
            return line.substring(0, i) + label + spacer + line.substring(after);
        }

        String spacer = label.endsWith(" ") || line.isBlank() ? "" : " ";
        return label + spacer + line;
    }

    private String sanitizeSingleGameLine(String value, int maxLength) {
        String raw = String.valueOf(value == null ? "" : value);
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '\n' || ch == '\r') {
                out.append(' ');
            } else if (ch == '\t' || !Character.isISOControl(ch)) {
                out.append(ch);
            }
        }
        return truncateVisible(out.toString(), maxLength);
    }

    private String truncateVisible(String value, int maxLength) {
        String raw = String.valueOf(value == null ? "" : value).replace('\n', ' ').replace('\r', ' ').trim();
        if (maxLength <= 0 || raw.length() <= maxLength) return raw;
        if (maxLength <= 1) return "…";
        return raw.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private GameLineHover gameLineHover(ChatMessage msg, String renderedLine, String renderedMessage, ConfigValues config) {
        if (msg == null || config == null) return GameLineHover.empty();

        String display = String.valueOf(msg.sender == null ? "" : msg.sender);
        String real = stripControl(msg.realSender, 64).trim();
        String line = String.valueOf(renderedLine == null ? "" : renderedLine);
        String source = stripControl(msg.source, 32).trim();
        String suggestCommand = display.isBlank() || real.isBlank() || !line.contains(display)
                ? ""
                : senderSuggestCommand(msg, config, real);
        String hover = "";

        String mode = String.valueOf(config.playerNameMode == null ? "" : config.playerNameMode).trim();
        boolean nicknameMode = "display-name".equalsIgnoreCase(mode) || "custom-name".equalsIgnoreCase(mode);
        String plainDisplay = stripMinecraftFormatting(display).trim();
        if (!display.isBlank() && !real.isBlank() && line.contains(display)
                && config.gameNameHoverEnabled && nicknameMode && !plainDisplay.isBlank() && !plainDisplay.equalsIgnoreCase(real)) {
            String uuid = stripControl(msg.playerUuid, 64).trim();
            hover = String.valueOf(config.gameNameHoverText == null ? "" : config.gameNameHoverText);
            if (hover.isBlank()) hover = "&f{real}";
            hover = hover
                    .replace("{display}", plainDisplay)
                    .replace("{real}", real)
                    .replace("{uuid}", uuid)
                    .replace("{source}", source);
            hover = translateGameFormatCodes(hover.replace("\\n", "\n"));
        }

        String replyTarget = String.valueOf(renderedMessage == null ? "" : renderedMessage);
        String replyCommand = "";
        String replyHover = "";
        if (config.replyGameClickEnabled && msg.id != null && !msg.id.isBlank()
                && !replyTarget.isBlank() && line.contains(replyTarget)) {
            String shortId = stripControl(msg.id, 24);
            replyCommand = "/kchat reply " + stripControl(msg.id, 96) + " ";
            replyHover = LegacyText.GRAY + host.language().text(
                    "command.replyClickHint",
                    "Click to reply (#{id})",
                    Map.of("id", shortId));
        }

        if (hover.isBlank() && suggestCommand.isBlank() && replyCommand.isBlank()) return GameLineHover.empty();
        return new GameLineHover(display, hover, suggestCommand, replyTarget, replyHover, replyCommand);
    }

    private String senderSuggestCommand(ChatMessage msg, ConfigValues config, String realSender) {
        String target = stripMinecraftFormatting(stripControl(realSender, 64)).trim();
        // Minecraft Java names use [A-Za-z0-9_]. Common Geyser/Floodgate setups
        // may prepend '.', '*', or '-', so allow those safe non-whitespace characters too.
        if (!target.matches("[A-Za-z0-9_.*-]{1,64}")) return "";

        String source = stripControl(msg == null ? "" : msg.source, 32).trim();
        if ("game".equalsIgnoreCase(source) && isLocalMessageOrigin(msg, config)) {
            return "/w " + target + " ";
        }
        if (("web".equalsIgnoreCase(source) || "game".equalsIgnoreCase(source))
                && config != null && config.directMessageEnabled && config.directMessageAllowGameSend) {
            // Web users and players on another relayed server cannot be reached by
            // the local vanilla /w command, so use the KWC DM channel instead.
            String originServerId = RemotePlayerRef.normalizeServerId(msg == null ? "" : msg.originServerId);
            if (!isLocalMessageOrigin(msg, config) && !originServerId.isBlank()) {
                return "/kchat dm " + target + "@" + originServerId + " ";
            }
            return "/kchat dm " + target + " ";
        }
        return "";
    }

    private String stripMinecraftFormatting(String value) {
        return LegacyText.stripColor(LegacyText.translateAlternateColorCodes('&', String.valueOf(value == null ? "" : value)));
    }

    private static final class GameLineHover {
        final String target;
        final String text;
        final String suggestCommand;
        final String replyTarget;
        final String replyText;
        final String replySuggestCommand;

        GameLineHover(String target, String text, String suggestCommand,
                      String replyTarget, String replyText, String replySuggestCommand) {
            this.target = target == null ? "" : target;
            this.text = text == null ? "" : text;
            this.suggestCommand = suggestCommand == null ? "" : suggestCommand;
            this.replyTarget = replyTarget == null ? "" : replyTarget;
            this.replyText = replyText == null ? "" : replyText;
            this.replySuggestCommand = replySuggestCommand == null ? "" : replySuggestCommand;
        }

        boolean enabled() {
            boolean senderEnabled = !target.isBlank() && (!text.isBlank() || !suggestCommand.isBlank());
            boolean replyEnabled = !replyTarget.isBlank() && !replySuggestCommand.isBlank();
            return senderEnabled || replyEnabled;
        }

        GameLineHover withoutSender() {
            return new GameLineHover("", "", "", replyTarget, replyText, replySuggestCommand);
        }

        static GameLineHover empty() {
            return new GameLineHover("", "", "", "", "", "");
        }
    }

    private void broadcastGameLine(String line, boolean preservePlainForGameEmojiTokens, ConfigValues config) {
        broadcastGameLine(line, preservePlainForGameEmojiTokens, config, GameLineHover.empty());
    }

    private void broadcastGameLine(String line, boolean preservePlainForGameEmojiTokens, ConfigValues config, GameLineHover hover) {
        if (line == null || line.isEmpty()) return;
        List<String> lines = host.splitMessageTokenGameLines(line);
        for (int i = 0; i < lines.size(); i++) {
            String rendered = visibleGameLine(lines.get(i));
            GameLineHover lineHover = i == 0 ? hover : (hover == null ? GameLineHover.empty() : hover.withoutSender());
            broadcastSingleGameLine(rendered, preservePlainForGameEmojiTokens, config, lineHover);
        }
    }

    private void broadcastSingleGameLine(String line, boolean preservePlainForGameEmojiTokens, ConfigValues config, GameLineHover hover) {
        boolean hasClickableUrl = config != null && config.clickableUrlsInGame && containsUrl(line);
        boolean hasHover = hover != null && hover.enabled();
        if (!hasClickableUrl && !hasHover) {
            platform.broadcastPlainMessage(line);
            return;
        }

        if (preservePlainForGameEmojiTokens) {
            // Keep plain platform chat output so external game-side emoji plugins can
            // render :pack/name: tokens. Configured line-break tokens are already
            // split into explicit Minecraft message packets above.
            platform.broadcastPlainMessage(line);
            return;
        }

        broadcastInteractiveLine(line, hasClickableUrl, hover);
    }

    private String visibleGameLine(String line) {
        return line == null || line.isEmpty() ? " " : line;
    }

    private void sendTokenGameLines(java.util.UUID recipientUuid, String protectedLine) {
        if (recipientUuid == null) return;
        for (String line : host.splitMessageTokenGameLines(protectedLine)) {
            platform.sendPlainMessage(recipientUuid, visibleGameLine(line));
        }
    }

    private boolean containsUrl(String line) {
        return line != null && URL_PATTERN.matcher(line).find();
    }

    private void broadcastClickableUrlReferences(String line) {
        for (String url : extractClickableUrls(line)) {
            broadcastClickableLine("↪ " + url);
        }
    }

    private List<String> extractClickableUrls(String line) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(String.valueOf(line == null ? "" : line));
        while (matcher.find()) {
            String raw = matcher.group(1);
            String[] split = splitUrlTrailing(raw);
            String url = split[0];
            if (!url.isBlank()) urls.add(url);
        }
        return new ArrayList<>(urls);
    }

    private void broadcastClickableLine(String line) {
        broadcastInteractiveLine(line, true, GameLineHover.empty());
    }

    private void broadcastInteractiveLine(String line, boolean clickableUrls, GameLineHover hover) {
        try {
            platform.broadcastInteractiveMessage(platformGameMessage(line, clickableUrls, hover));
        } catch (Throwable t) {
            host.logger().warn("Interactive game chat failed; falling back to plain broadcast: " + t.getMessage());
            platform.broadcastPlainMessage(line);
        }
    }

    public void broadcastClickableLocalGameMessage(ChatMessage msg, String renderedLine, String renderedMessage,
                                                   java.util.Collection<java.util.UUID> recipients) {
        ConfigValues config = host.configValues();
        String originalMessage = String.valueOf(renderedMessage == null ? "" : renderedMessage);
        String protectedMessage = msg != null && msg.gameMessage != null && !msg.gameMessage.isBlank()
                ? msg.gameMessage : originalMessage;
        String message = renderImageEmojiSymbolsForGame(protectedMessage);
        String lineSource = String.valueOf(renderedLine == null ? "" : renderedLine);
        if (!originalMessage.isBlank() && !protectedMessage.equals(originalMessage) && lineSource.contains(originalMessage)) {
            lineSource = lineSource.replace(originalMessage, protectedMessage);
        }
        String line = sanitizeSingleGameLine(lineSource, 32768);
        String restoredMessage = restoreTokenGameBreaks(message);
        String restoredLine = restoreTokenGameBreaks(line);
        message = restoredMessage;
        GameLineHover interaction = gameLineHover(msg, restoredLine, message, config);
        java.util.Collection<java.util.UUID> targets = recipients == null
                ? platform.onlinePlayers().stream().map(PlatformPlayer::uuid).toList()
                : recipients;
        try {
            List<String> lines = host.splitMessageTokenGameLines(line);
            for (int i = 0; i < lines.size(); i++) {
                String rendered = visibleGameLine(lines.get(i));
                GameLineHover lineHover = i == 0 ? interaction : interaction.withoutSender();
                boolean clickableUrls = config != null && config.clickableUrlsInGame && containsUrl(rendered);
                platform.sendInteractiveMessage(targets, platformGameMessage(rendered, clickableUrls, lineHover));
            }
            // The original native chat event remains active for non-player viewers, so
            // the platform writes the normal chat log. Do not echo this manually.
        } catch (Throwable t) {
            host.logger().warn("Clickable local game chat failed; falling back to plain player delivery: " + t.getMessage());
            for (String split : host.splitMessageTokenGameLines(line)) {
                String rendered = visibleGameLine(split);
                for (java.util.UUID target : targets) platform.sendPlainMessage(target, rendered);
            }
        }
    }

    private PlatformGameMessage platformGameMessage(String line, boolean clickableUrls, GameLineHover hover) {
        GameLineHover interaction = hover == null ? GameLineHover.empty() : hover;
        return new PlatformGameMessage(
                String.valueOf(line == null ? "" : line),
                clickableUrls,
                interaction.target, interaction.text, interaction.suggestCommand,
                interaction.replyTarget, interaction.replyText, interaction.replySuggestCommand);
    }

    private String normalizeClickUrl(String url) {
        return url.toLowerCase(Locale.ROOT).startsWith("http://") || url.toLowerCase(Locale.ROOT).startsWith("https://")
                ? url
                : "https://" + url;
    }

    private String[] splitUrlTrailing(String raw) {
        String url = raw == null ? "" : raw;
        StringBuilder trailing = new StringBuilder();
        while (!url.isEmpty()) {
            char ch = url.charAt(url.length() - 1);
            if (ch == '.' || ch == ',' || ch == '!' || ch == '?' || ch == ';' || ch == ':'
                    || ch == ')' || ch == ']' || ch == '}') {
                trailing.insert(0, ch);
                url = url.substring(0, url.length() - 1);
            } else {
                break;
            }
        }
        return new String[]{url, trailing.toString()};
    }

    private static final class AroundHistoryResult {
        final List<ChatMessage> messages = new ArrayList<>();
        int targetIndex = -1;
        boolean hasBefore;
        boolean hasAfter;
        boolean pruned;
    }

    private AroundHistoryResult findHistoryAround(String targetId, int before, int after) {
        AroundHistoryResult result = new AroundHistoryResult();
        if (targetId == null || targetId.isBlank()) return result;

        if (sqliteHistoryEnabled()) {
            SqliteHistoryStore.AroundPage page = sqliteHistory.around(targetId, before, after, sqliteCutoffMillis());
            result.messages.addAll(page.messages);
            result.targetIndex = page.targetIndex;
            result.hasBefore = page.hasBefore;
            result.hasAfter = page.hasAfter;
            return result;
        }

        synchronized (history) {
            result.pruned = pruneHistoryLocked();
            List<ChatMessage> all = new ArrayList<>(history);
            fillAroundResult(result, all, targetId, before, after);
            if (result.targetIndex >= 0) return result;
        }

        ConfigValues config = host.configValues();
        if (!legacyJsonlHistoryEnabled()) return result;
        Path path = historyPath();
        if (!Files.exists(path)) return result;

        long persistCutoff = retentionCutoffMillis(config.historyRetentionDays);
        List<ChatMessage> all = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) continue;
                ChatMessage msg = ChatMessage.fromMap(JsonUtil.parseFlatObject(line));
                if (msg.message == null || msg.message.isBlank()) continue;
                if (msg.hidden || isOlderThan(msg, persistCutoff)) continue;
                all.add(msg);
            }
        } catch (IOException ex) {
            host.logger().warn("Failed to search chat history file " + path + ": " + ex.getMessage());
            return result;
        }
        fillAroundResult(result, all, targetId, before, after);
        return result;
    }

    private void fillAroundResult(AroundHistoryResult result, List<ChatMessage> all, String targetId, int before, int after) {
        if (result == null || all == null || targetId == null || targetId.isBlank()) return;
        result.messages.clear();
        result.targetIndex = -1;
        result.hasBefore = false;
        result.hasAfter = false;
        for (int i = 0; i < all.size(); i++) {
            ChatMessage msg = all.get(i);
            if (msg != null && !msg.hidden && targetId.equals(msg.id)) {
                result.targetIndex = i;
                break;
            }
        }
        if (result.targetIndex < 0) return;
        int start = Math.max(0, result.targetIndex - Math.max(0, before));
        int endExclusive = Math.min(all.size(), result.targetIndex + Math.max(0, after) + 1);
        result.hasBefore = start > 0;
        result.hasAfter = endExclusive < all.size();
        for (int i = start; i < endExclusive; i++) {
            ChatMessage msg = all.get(i);
            if (msg != null && !msg.hidden) result.messages.add(msg);
        }
    }

    private void cacheTransientReplyTarget(ChatMessage msg) {
        if (msg == null || msg.id == null || msg.id.isBlank()) return;
        long expiresAt = System.currentTimeMillis() + 3_600_000L;
        transientReplyTargets.put(msg.id, new CachedReplyTarget(msg, expiresAt));
        if (transientReplyTargets.size() > 4096) {
            long now = System.currentTimeMillis();
            transientReplyTargets.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt < now);
            while (transientReplyTargets.size() > 4096) {
                Map.Entry<String, CachedReplyTarget> oldest = transientReplyTargets.entrySet().stream()
                        .min(Comparator.comparingLong(entry -> entry.getValue().expiresAt))
                        .orElse(null);
                if (oldest == null || !transientReplyTargets.remove(oldest.getKey(), oldest.getValue())) break;
            }
        }
    }

    private ChatMessage findTransientReplyTarget(String id) {
        CachedReplyTarget cached = transientReplyTargets.get(id);
        if (cached == null) return null;
        if (cached.expiresAt < System.currentTimeMillis()) {
            transientReplyTargets.remove(id, cached);
            return null;
        }
        return cached.message;
    }

    private static final class CachedReplyTarget {
        final ChatMessage message;
        final long expiresAt;

        CachedReplyTarget(ChatMessage message, long expiresAt) {
            this.message = message;
            this.expiresAt = expiresAt;
        }
    }

    private ChatMessage findHistoryMessageById(String id) {
        if (id == null || id.isBlank()) return null;
        ChatMessage transientTarget = findTransientReplyTarget(id);
        if (transientTarget != null) return transientTarget;
        if (sqliteHistoryEnabled()) {
            ChatMessage msg = sqliteHistory.find(id);
            if (msg != null) return msg;
        }
        synchronized (history) {
            for (ChatMessage msg : history) {
                if (msg != null && !msg.hidden && id.equals(msg.id)) return msg;
            }
        }
        AroundHistoryResult around = findHistoryAround(id, 0, 0);
        if (around.targetIndex >= 0 && !around.messages.isEmpty()) return around.messages.get(0);
        return null;
    }

    private void appendMemorySearchMatches(List<ChatMessage> result, String query, int limit, long cutoff, Map<String, String> strings,
                                           long from, long to, String senderFilter, String sourceFilter, boolean includeSystem) {
        if (result == null) return;
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int actualLimit = Math.max(1, limit <= 0 ? host.configValues().searchResultLimit : limit);
        if (result.size() >= actualLimit) return;
        String senderLower = senderFilter == null ? "" : senderFilter.toLowerCase(Locale.ROOT).trim();
        Set<String> seen = new HashSet<>();
        for (ChatMessage msg : result) if (msg != null && msg.id != null) seen.add(msg.id);
        synchronized (history) {
            List<ChatMessage> all = new ArrayList<>(history);
            for (int i = all.size() - 1; i >= 0 && result.size() < actualLimit; i--) {
                ChatMessage msg = all.get(i);
                if (msg == null || (msg.id != null && seen.contains(msg.id))) continue;
                if (historySearchMatches(msg, q, cutoff, strings, from, to, senderLower, sourceFilter, includeSystem)) {
                    result.add(msg);
                    if (msg.id != null) seen.add(msg.id);
                }
            }
        }
    }

    private List<ChatMessage> searchInMemoryAndJsonl(String query, int limit, Map<String, String> strings,
                                                     long from, long to, String senderFilter, String sourceFilter, boolean includeSystem) {
        List<ChatMessage> result = new ArrayList<>();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int actualLimit = Math.max(1, limit <= 0 ? host.configValues().searchResultLimit : limit);
        long cutoff = retentionCutoffMillis(host.configValues().historyRetentionDays);
        String senderLower = senderFilter == null ? "" : senderFilter.toLowerCase(Locale.ROOT).trim();

        synchronized (history) {
            List<ChatMessage> all = new ArrayList<>(history);
            for (int i = all.size() - 1; i >= 0 && result.size() < actualLimit; i--) {
                ChatMessage msg = all.get(i);
                if (historySearchMatches(msg, q, cutoff, strings, from, to, senderLower, sourceFilter, includeSystem)) result.add(msg);
            }
        }
        if (result.size() >= actualLimit || !legacyJsonlHistoryEnabled()) return result;

        Path path = historyPath();
        if (!Files.exists(path)) return result;
        Set<String> seen = new HashSet<>();
        for (ChatMessage msg : result) if (msg.id != null) seen.add(msg.id);
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0 && result.size() < actualLimit; i--) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) continue;
                ChatMessage msg = ChatMessage.fromMap(JsonUtil.parseFlatObject(line));
                if (msg.id != null && seen.contains(msg.id)) continue;
                if (historySearchMatches(msg, q, cutoff, strings, from, to, senderLower, sourceFilter, includeSystem)) {
                    result.add(msg);
                    if (msg.id != null) seen.add(msg.id);
                }
            }
        } catch (IOException ex) {
            host.logger().warn("Failed to search JSONL chat history " + path + ": " + ex.getMessage());
        }
        return result;
    }

    private void appendSqliteLocalizedSearchMatches(List<ChatMessage> result, String query, int limit, long cutoff, Map<String, String> strings,
                                                    long from, long to, String senderFilter, String sourceFilter, boolean includeSystem) {
        if (result == null) return;
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int actualLimit = Math.max(1, limit <= 0 ? host.configValues().searchResultLimit : limit);
        if (result.size() >= actualLimit) return;
        String senderLower = senderFilter == null ? "" : senderFilter.toLowerCase(Locale.ROOT).trim();
        Set<String> seen = new HashSet<>();
        for (ChatMessage msg : result) if (msg != null && msg.id != null) seen.add(msg.id);
        for (ChatMessage msg : sqliteHistory.i18nSearchCandidates(cutoff, actualLimit, from, to, sourceFilter, includeSystem)) {
            if (result.size() >= actualLimit) break;
            if (msg == null || (msg.id != null && seen.contains(msg.id))) continue;
            if (historySearchMatches(msg, q, cutoff, strings, from, to, senderLower, sourceFilter, includeSystem)) {
                result.add(msg);
                if (msg.id != null) seen.add(msg.id);
            }
        }
    }

    private boolean historySearchMatches(ChatMessage msg, String queryLower, long cutoff, Map<String, String> strings,
                                         long from, long to, String senderLower, String sourceFilter, boolean includeSystem) {
        if (msg == null || msg.hidden || isOlderThan(msg, cutoff)) return false;
        if (!historyFilterMatches(msg, from, to, senderLower, sourceFilter, includeSystem)) return false;
        if (queryLower == null || queryLower.isBlank()) return true;
        String haystack = (String.valueOf(msg.sender == null ? "" : msg.sender) + "\n"
                + String.valueOf(msg.realSender == null ? "" : msg.realSender) + "\n"
                + String.valueOf(msg.message == null ? "" : msg.message) + "\n"
                + localizedMessageText(msg, strings) + "\n"
                + localizedSenderText(msg, strings) + "\n"
                + localizedSourceText(msg, strings)).toLowerCase(Locale.ROOT);
        return haystack.contains(queryLower);
    }

    private boolean historyFilterMatches(ChatMessage msg, long from, long to, String senderLower, String sourceFilter, boolean includeSystem) {
        if (msg == null) return false;
        if (from != Long.MIN_VALUE && msg.time < from) return false;
        if (to != Long.MAX_VALUE && msg.time > to) return false;
        String source = String.valueOf(msg.source == null ? "" : msg.source).toLowerCase(Locale.ROOT).trim();
        if (!includeSystem && isSystemLikeSource(source)) return false;
        String normalizedSource = normalizeSearchSource(sourceFilter);
        if (!normalizedSource.isBlank()) {
            if ("system".equals(normalizedSource)) {
                if (!isSystemLikeSource(source)) return false;
            } else if (!normalizedSource.equals(source)) {
                return false;
            }
        }
        if (senderLower != null && !senderLower.isBlank()) {
            String senderHaystack = (String.valueOf(msg.sender == null ? "" : msg.sender) + "\n"
                    + String.valueOf(msg.realSender == null ? "" : msg.realSender)).toLowerCase(Locale.ROOT);
            if (!senderHaystack.contains(senderLower)) return false;
        }
        return true;
    }

    private boolean isSystemLikeSource(String source) {
        String s = source == null ? "" : source.toLowerCase(Locale.ROOT).trim();
        return s.equals("system") || s.equals("event") || s.equals("server");
    }

    private String localizedMessageText(ChatMessage msg, Map<String, String> strings) {
        if (msg == null) return "";
        if (msg.hidden) return stringValue(strings, "message.deleted", "[deleted]");
        String fallback = String.valueOf(msg.message == null ? "" : msg.message);
        String key = String.valueOf(msg.i18nKey == null ? "" : msg.i18nKey).trim();
        if (key.isBlank()) return fallback;
        String value = stringValue(strings, key, fallback);
        Map<String, String> vars = JsonUtil.parseFlatObject(msg.i18nArgs);
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return value;
    }

    private String localizedSenderText(ChatMessage msg, Map<String, String> strings) {
        if (msg == null) return "";
        String source = String.valueOf(msg.source == null ? "" : msg.source).toLowerCase(Locale.ROOT);
        String sender = String.valueOf(msg.sender == null ? "" : msg.sender);
        String senderKey = sender.toLowerCase(Locale.ROOT);
        if (source.equals("event") || source.equals("system") || source.equals("server")) {
            if (senderKey.equals("server")) return stringValue(strings, "sender.server", "Server");
            if (senderKey.equals("command")) return stringValue(strings, "sender.command", "Command");
            if (senderKey.equals("system")) return stringValue(strings, "sender.system", "System");
        }
        if (source.equals("discord") && senderKey.equals("discord")) return stringValue(strings, "sender.discord", "Discord");
        return sender;
    }

    private String localizedSourceText(ChatMessage msg, Map<String, String> strings) {
        String source = String.valueOf(msg == null || msg.source == null ? "" : msg.source).toLowerCase(Locale.ROOT);
        return source.isBlank() ? "" : stringValue(strings, "source." + source, source);
    }

    private String stringValue(Map<String, String> strings, String key, String fallback) {
        if (strings != null && key != null) {
            String value = strings.get(key);
            if (value != null) return value;
        }
        return fallback == null ? "" : fallback;
    }

    private String messageReplyPreview(ChatMessage msg) {
        if (msg == null) return "";
        String text = msg.hidden ? "[deleted]" : String.valueOf(msg.message == null ? "" : msg.message);
        text = stripControl(text, 180).replace('\n', ' ').replace('\r', ' ').trim();
        if (text.length() > 120) text = text.substring(0, 117) + "...";
        return text;
    }

    private void attachReplyIfPresent(ChatMessage msg, String replyToId, String fallbackSender, String fallbackPreview) {
        if (msg == null || replyToId == null || replyToId.isBlank()) return;
        String id = stripControl(replyToId, 96);
        if (id.isBlank()) return;
        ChatMessage target = findHistoryMessageById(id);
        String sender = "";
        String preview = "";
        if (target != null) {
            id = target.id;
            sender = stripControl(target.sender, 64);
            preview = messageReplyPreview(target);
        } else {
            // The frontend only allows replying to messages it has already loaded,
            // but the server-side in-memory history may not always contain that
            // exact target yet (for example after pruning/reload or when a proxy
            // reconnect races with history refresh). Preserve the reply relation
            // using the client-provided preview instead of silently dropping it.
            sender = stripControl(fallbackSender, 64);
            preview = stripControl(fallbackPreview, 180).replace("\n", " ").replace("\r", " ").trim();
            if (preview.length() > 120) preview = preview.substring(0, 117) + "...";
        }
        if (sender.isBlank()) sender = "Unknown";
        if (preview.isBlank()) preview = "...";
        msg.withReply(id, sender, preview);
    }

    private void addHistory(ChatMessage msg) {
        boolean pruned;
        synchronized (history) {
            history.addLast(msg);
            pruned = pruneHistoryLocked();
        }

        ConfigValues config = host.configValues();
        if (sqliteHistoryEnabled()) {
            enqueueSqliteHistoryWrite(msg, config);
        } else if (legacyJsonlHistoryEnabled()) {
            if (pruned || config.historySize > 0 || config.historyRetentionDays > 0) savePersistedHistory();
            else appendPersistedHistory(msg);
        }
    }

    private void enqueueSqliteHistoryWrite(ChatMessage msg, ConfigValues config) {
        if (msg == null || sqliteHistory == null) return;
        ExecutorService worker = historyExecutor;
        Runnable task = () -> {
            SqliteHistoryStore store = sqliteHistory;
            if (store == null) return;
            store.insert(msg);
            if (shouldPruneSqliteHistory(config)) {
                store.prune(config == null ? 0 : config.historySize, sqliteCutoffMillis());
            }
        };
        if (worker == null || worker.isShutdown()) {
            task.run();
            return;
        }
        try {
            worker.execute(task);
        } catch (RejectedExecutionException ex) {
            task.run();
        }
    }

    private synchronized boolean shouldPruneSqliteHistory(ConfigValues config) {
        long now = System.currentTimeMillis();
        int writes = ++sqliteWritesSincePrune;
        boolean countLimited = config != null && config.historySize > 0;
        boolean retentionLimited = config != null && config.historyRetentionDays > 0;
        if (!countLimited && !retentionLimited) return false;
        if (writes >= 100 || lastSqlitePruneAt <= 0 || now - lastSqlitePruneAt >= 60_000L) {
            sqliteWritesSincePrune = 0;
            lastSqlitePruneAt = now;
            return true;
        }
        return false;
    }

    private long retentionCutoffMillis(int retentionDays) {
        if (retentionDays <= 0) return Long.MIN_VALUE;
        return System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
    }

    private boolean isOlderThan(ChatMessage msg, long cutoff) {
        return msg != null && cutoff != Long.MIN_VALUE && msg.time < cutoff;
    }

    private boolean pruneHistoryLocked() {
        ConfigValues config = host.configValues();
        boolean changed = false;

        if (config.historyRetentionDays > 0) {
            long cutoff = retentionCutoffMillis(config.historyRetentionDays);
            while (!history.isEmpty() && history.peekFirst().time < cutoff) {
                history.removeFirst();
                changed = true;
            }
        }

        // history-size: 0 means unlimited by count for persisted storage.
        // SQLite still keeps a bounded in-memory cache so long-lived logs do not
        // grow the server heap indefinitely during one server session.
        int memoryLimit = config.historySize > 0 ? config.historySize : (sqliteHistoryEnabled() ? sqliteMemoryCacheLimit() : 0);
        if (memoryLimit > 0) {
            while (history.size() > memoryLimit) {
                history.removeFirst();
                changed = true;
            }
        }

        return changed;
    }

    private boolean sqliteHistoryEnabled() {
        return sqliteHistory != null && "sqlite".equalsIgnoreCase(String.valueOf(host.configValues().historyStorage));
    }

    private boolean legacyJsonlHistoryEnabled() {
        ConfigValues config = host.configValues();
        return !sqliteHistoryEnabled() && "jsonl".equalsIgnoreCase(String.valueOf(config.historyStorage));
    }

    private long sqliteCutoffMillis() {
        ConfigValues config = host.configValues();
        return retentionCutoffMillis(config == null ? 0 : config.historyRetentionDays);
    }

    private Path sqliteHistoryPath() {
        ConfigValues config = host.configValues();
        String file = config.historySqliteFile == null || config.historySqliteFile.isBlank() ? "history.db" : config.historySqliteFile.trim();
        Path path = Path.of(file);
        if (!path.isAbsolute()) path = host.dataDirectory().resolve(path);
        return path.normalize();
    }

    private void initializeHistoryStorage() {
        ConfigValues config = host.configValues();
        if (!"sqlite".equalsIgnoreCase(String.valueOf(config.historyStorage))) return;
        try {
            sqliteHistory = SqliteHistoryStore.open(host.logger(), sqliteHistoryPath());
            if (config.historySqliteMigrateJsonl) {
                int migrated = sqliteHistory.migrateJsonlIfEmpty(historyPath(), sqliteCutoffMillis(), config.historySize);
                if (migrated > 0) host.logger().info("Migrated " + migrated + " JSONL chat history messages to SQLite: " + sqliteHistory.path());
            }
            sqliteHistory.prune(config.historySize, sqliteCutoffMillis());
            host.logger().info("Using SQLite chat history: " + sqliteHistory.path());
        } catch (Exception ex) {
            sqliteHistory = null;
            host.logger().warn("Failed to open SQLite chat history. Falling back to in-memory/JSONL history: " + ex.getMessage());
        }
    }

    private Path historyPath() {
        ConfigValues config = host.configValues();
        String file = config.historyFile == null || config.historyFile.isBlank()
                ? "history.jsonl"
                : config.historyFile.trim();
        Path path = Path.of(file);
        if (!path.isAbsolute()) {
            path = host.dataDirectory().resolve(path);
        }
        return path.normalize();
    }

    private void loadPersistedHistory() {
        ConfigValues config = host.configValues();
        if (sqliteHistoryEnabled()) {
            SqliteHistoryStore.Page latest = sqliteHistory.latest(sqliteMemoryCacheLimit(), sqliteCutoffMillis());
            synchronized (history) {
                history.clear();
                history.addAll(latest.messages);
                pruneHistoryLocked();
            }
            host.logger().info("Loaded " + latest.messages.size() + " recent SQLite chat history messages into memory cache.");
            return;
        }
        if (!legacyJsonlHistoryEnabled()) return;

        Path path = historyPath();
        if (!Files.exists(path)) return;

        int loaded = 0;
        int skippedExpired = 0;
        long persistCutoff = retentionCutoffMillis(config.historyRetentionDays);
        synchronized (history) {
            history.clear();
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (line == null || line.isBlank()) continue;
                    ChatMessage msg = ChatMessage.fromMap(JsonUtil.parseFlatObject(line));
                    if (msg.message == null || msg.message.isBlank()) continue;
                    if (isOlderThan(msg, persistCutoff)) {
                        skippedExpired++;
                        continue;
                    }
                    history.addLast(msg);
                    loaded++;
                }
                pruneHistoryLocked();
            } catch (IOException ex) {
                host.logger().warn("Failed to load chat history file " + path + ": " + ex.getMessage());
            }
        }
        savePersistedHistory();
        host.logger().info("Loaded " + loaded + " persisted web chat history messages" + (skippedExpired > 0 ? " and pruned " + skippedExpired + " expired persisted messages." : "."));
    }

    private int sqliteMemoryCacheLimit() {
        ConfigValues config = host.configValues();
        if (config.historySize > 0) return Math.max(100, Math.min(5000, config.historySize));
        return 1000;
    }

    private void appendPersistedHistory(ChatMessage msg) {
        ConfigValues config = host.configValues();
        if (!legacyJsonlHistoryEnabled()) return;
        Path path = historyPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, msg.toPersistJson() + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            host.logger().warn("Failed to append chat history file " + path + ": " + ex.getMessage());
        }
    }

    private void savePersistedHistory() {
        ConfigValues config = host.configValues();
        if (!legacyJsonlHistoryEnabled()) return;

        Path path = historyPath();
        StringBuilder sb = new StringBuilder();
        long persistCutoff = retentionCutoffMillis(config.historyRetentionDays);
        synchronized (history) {
            pruneHistoryLocked();
            for (ChatMessage msg : history) {
                if (isOlderThan(msg, persistCutoff)) continue;
                sb.append(msg.toPersistJson()).append(System.lineSeparator());
            }
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            host.logger().warn("Failed to save chat history file " + path + ": " + ex.getMessage());
        }
    }

    private void broadcastPinsChanged() {
        List<String> items = new ArrayList<>();
        if (host.configValues().pinnedEnabled) {
            for (PinnedMessage pin : storage.listPinnedMessages()) {
                items.add(pin.toJson());
            }
        }
        broadcastEvent("pins", "{\"ok\":true,\"pins\":[" + String.join(",", items) + "]}");
    }

    private void broadcast(ChatMessage msg) {
        broadcastEvent("chat", msg.toJson());
    }

    private void dispatchWebPushChat(ChatMessage msg) {
        ConfigValues c = host.configValues();
        if (c == null || !c.webPushEnabled || msg == null) return;
        String source = String.valueOf(msg.source == null ? "" : msg.source).toLowerCase(Locale.ROOT);
        boolean system = source.equals("event") || source.equals("system") || source.equals("server");
        WebPushManager.Payload p = new WebPushManager.Payload();
        p.type = system ? "system" : "chat";
        p.title = configuredWebPushTitle();
        p.body = system ? String.valueOf(msg.message == null ? "" : msg.message) : notificationSender(msg.sender) + (msg.message == null || msg.message.isBlank() ? "" : ": " + msg.message);
        p.url = system ? "" : webPushNavigationUrlWithParams(Map.of("kwcMessage", String.valueOf(msg.id == null ? "" : msg.id)));
        p.tag = system ? "kwc-system" : "kwc-chat";
        p.senderUuid = msg.playerUuid == null ? "" : msg.playerUuid;
        p.i18nKey = msg.i18nKey == null ? "" : msg.i18nKey;
        p.i18nArgs = msg.i18nArgs == null ? "" : msg.i18nArgs;
        p.systemKind = systemKindFor(msg);
        String replyTargetUuid = replyTargetUuidFor(msg);
        p.replyTargetUuid = replyTargetUuid;
        webPush.sendToAll(p);
        dispatchWebPushReply(msg, replyTargetUuid);
    }


    private String systemKindFor(ChatMessage msg) {
        if (msg == null) return "";
        String key = String.valueOf(msg.i18nKey == null ? "" : msg.i18nKey).trim().toLowerCase(Locale.ROOT);
        if (key.endsWith("minecraft-join")) return "minecraft-join";
        if (key.endsWith("minecraft-quit")) return "minecraft-quit";
        if (key.endsWith("first-join")) return "first-join";
        return "";
    }

    public void broadcastAuthExpired(String accountUuid, String reason) {
        String uuid = String.valueOf(accountUuid == null ? "" : accountUuid).trim().toLowerCase(Locale.ROOT);
        if (uuid.isBlank()) return;
        for (SseConnection client : sseHub.snapshot()) {
            if (!uuid.equals(client.accountUuid())) continue;
            try {
                sendAuthExpired(client, reason);
            } catch (IOException ignored) {
            } finally {
                sseHub.remove(client);
                client.close();
            }
        }
    }

    public void broadcastAuthExpiredForToken(String token, String reason) {
        String targetToken = String.valueOf(token == null ? "" : token).trim();
        if (targetToken.isBlank()) return;
        for (SseConnection client : sseHub.snapshot()) {
            if (!targetToken.equals(client.token())) continue;
            try {
                sendAuthExpired(client, reason);
            } catch (IOException ignored) {
            } finally {
                sseHub.remove(client);
                client.close();
            }
        }
    }

    private void sendAuthExpired(SseConnection client, String reason) throws IOException {
        if (client == null) return;
        String safeReason = String.valueOf(reason == null || reason.isBlank() ? "expired" : reason);
        client.sendRaw("event: auth\ndata: {\"ok\":false,\"reason\":" + JsonUtil.quote(safeReason) + "}\n\n");
    }

    private String replyTargetUuidFor(ChatMessage msg) {
        if (msg == null || msg.replyToId == null || msg.replyToId.isBlank()) return "";
        ChatMessage target = findHistoryMessageById(msg.replyToId);
        if (target == null || target.playerUuid == null || target.playerUuid.isBlank()) return "";
        String targetUuid = target.playerUuid.trim().toLowerCase(Locale.ROOT);
        String senderUuid = msg.playerUuid == null ? "" : msg.playerUuid.trim().toLowerCase(Locale.ROOT);
        if (!senderUuid.isBlank() && senderUuid.equals(targetUuid)) return "";
        return targetUuid;
    }

    private void dispatchWebPushReply(ChatMessage msg, String targetUuid) {
        ConfigValues c = host.configValues();
        if (c == null || !c.webPushEnabled || msg == null || targetUuid == null || targetUuid.isBlank()) return;
        WebPushManager.Payload p = new WebPushManager.Payload();
        p.type = "reply";
        p.title = notificationSender(msg.sender);
        p.body = msg.message == null ? "" : msg.message;
        p.url = webPushNavigationUrlWithParams(Map.of("kwcMessage", String.valueOf(msg.id == null ? "" : msg.id)));
        p.tag = "kwc-reply-" + targetUuid.replaceAll("[^A-Za-z0-9_-]", "");
        p.senderUuid = msg.playerUuid == null ? "" : msg.playerUuid;
        p.replyTargetUuid = targetUuid;
        webPush.sendToUser(targetUuid, p);
    }

    public void dispatchWebPushDirectMessage(String senderUuid, String senderName, String targetUuid, String targetName, String threadId, long messageId, String body) {
        ConfigValues c = host.configValues();
        if (c == null || !c.webPushEnabled) return;
        WebPushManager.Payload p = new WebPushManager.Payload();
        p.type = "dm";
        p.title = notificationSender(senderName);
        p.body = body == null ? "" : body;
        p.url = webPushNavigationUrlWithParams(Map.of("kwcDmThread", String.valueOf(threadId == null ? "" : threadId), "kwcDmMessage", String.valueOf(messageId > 0 ? messageId : 0)));
        p.tag = "kwc-dm-" + String.valueOf(targetUuid == null ? "" : targetUuid).replaceAll("[^A-Za-z0-9_-]", "");
        p.senderUuid = senderUuid == null ? "" : senderUuid;
        webPush.sendToUser(targetUuid, p);
    }

    public void dispatchWebPushGroupMessage(String senderUuid, String senderName, GroupRoom room, GroupMessage message, String fallbackRoomId) {
        ConfigValues c = host.configValues();
        if (c == null || !c.webPushEnabled || host.groupChats() == null) return;
        String roomId = room != null && room.id != null && !room.id.isBlank() ? room.id : String.valueOf(fallbackRoomId == null ? "" : fallbackRoomId);
        if (roomId.isBlank()) return;
        Set<String> members = host.groupChats().memberUuids(roomId);
        if (members == null || members.isEmpty()) return;
        String roomName = room != null && room.name != null && !room.name.isBlank() ? room.name : host.language().text("notification.groupChat", "Group chat");
        String body = message == null ? "" : message.body;
        WebPushManager.Payload p = new WebPushManager.Payload();
        p.type = "group";
        p.title = roomName + " · " + notificationSender(senderName);
        p.body = body == null ? "" : body;
        p.url = webPushNavigationUrlWithParams(Map.of("kwcGroupRoom", roomId, "kwcGroupMessage", String.valueOf(message == null || message.id <= 0 ? 0 : message.id)));
        p.tag = "kwc-group-" + roomId.replaceAll("[^A-Za-z0-9_-]", "");
        p.senderUuid = senderUuid == null ? (message == null ? "" : message.senderUuid) : senderUuid;
        webPush.sendToUsers(members, p);
    }

    private String standaloneOpenUrl() {
        ConfigValues c = host.configValues();
        if (c != null && c.standaloneWebPath != null && !c.standaloneWebPath.isBlank()) return c.standaloneWebPath;
        return "/";
    }

    private String webPushNavigationUrlWithParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey() == null ? "" : e.getKey().trim();
            String v = e.getValue() == null ? "" : e.getValue().trim();
            if (k.isBlank() || v.isBlank()) continue;
            if (query.length() > 0) query.append('&');
            query.append(java.net.URLEncoder.encode(k, StandardCharsets.UTF_8));
            query.append('=');
            query.append(java.net.URLEncoder.encode(v, StandardCharsets.UTF_8));
        }
        if (query.length() == 0) return "";
        // Web Push subscriptions already store the actual page URL. Keep server
        // payloads pathless so addon subscriptions are not accidentally forced
        // back to the standalone /chat path. The push sender merges these params
        // into the subscription openUrl.
        return "?" + query;
    }

    private String notificationSender(String value) {
        String text = stripControl(String.valueOf(value == null ? "" : value), 80);
        text = LegacyText.stripColor(text);
        return text == null || text.isBlank() ? configuredWebPushTitle() : text;
    }



    public void publishDirectMessageUpdate(String userUuidA, String userUuidB, String threadId) {
        String a = String.valueOf(userUuidA == null ? "" : userUuidA).trim().toLowerCase(Locale.ROOT);
        String b = String.valueOf(userUuidB == null ? "" : userUuidB).trim().toLowerCase(Locale.ROOT);
        String id = String.valueOf(threadId == null ? "" : threadId).trim();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("threadId", id);
        m.put("userA", a);
        m.put("userB", b);
        broadcastDirectMessageEvent(JsonUtil.obj(m), a, b);
    }


    public void inspectAdminDirectMessageAlert(String id, String sender, String source, String message) {
        adminDiscordAlerts.inspect(id, sender, source, message, AdminDiscordAlertManager.Scope.DM);
    }

    public void inspectAdminGroupAlert(String id, String sender, String source, String message) {
        adminDiscordAlerts.inspect(id, sender, source, message, AdminDiscordAlertManager.Scope.GROUP);
    }

    public void publishGroupChatUpdate(String roomId) {
        publishGroupChatUpdate(roomId, "");
    }

    public void publishGroupChatUpdate(String roomId, String extraUserUuid) {
        String id = String.valueOf(roomId == null ? "" : roomId).trim();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("roomId", id);
        broadcastGroupChatEvent(JsonUtil.obj(m), id, extraUserUuid);
    }

    private void broadcastGroupChatEvent(String json, String roomId, String extraUserUuid) {
        String id = String.valueOf(roomId == null ? "" : roomId).trim();
        String extra = String.valueOf(extraUserUuid == null ? "" : extraUserUuid).trim().toLowerCase(Locale.ROOT);
        Set<String> members = host.groupChats() == null ? Collections.emptySet() : host.groupChats().memberUuids(id);
        String data = "event: group\ndata: " + json + "\n\n";
        for (SseConnection client : sseHub.snapshot()) {
            String clientUuid = client.accountUuid() == null ? "" : client.accountUuid();
            boolean allowed = client.privateChatSuperAdmin() || members.isEmpty() || members.contains(clientUuid) || (!extra.isBlank() && extra.equals(clientUuid));
            if (!allowed) continue;
            try { client.sendRaw(data); }
            catch (IOException ex) { sseHub.remove(client); client.close(); }
        }
    }

    private void broadcastDirectMessageEvent(String json, String userUuidA, String userUuidB) {
        String a = String.valueOf(userUuidA == null ? "" : userUuidA).trim().toLowerCase(Locale.ROOT);
        String b = String.valueOf(userUuidB == null ? "" : userUuidB).trim().toLowerCase(Locale.ROOT);
        String data = "event: dm\ndata: " + json + "\n\n";
        for (SseConnection client : sseHub.snapshot()) {
            String clientUuid = client.accountUuid() == null ? "" : client.accountUuid();
            if (!client.privateChatSuperAdmin() && !clientUuid.equals(a) && !clientUuid.equals(b)) continue;
            try {
                client.sendRaw(data);
            } catch (IOException ex) {
                sseHub.remove(client);
                client.close();
            }
        }
    }

    private void broadcastEvent(String event, String json) {
        String data = "event: " + event + "\ndata: " + json + "\n\n";
        for (SseConnection client : sseHub.snapshot()) {
            try {
                client.sendRaw(data);
            } catch (IOException ex) {
                sseHub.remove(client);
                client.close();
            }
        }
    }

    private boolean isGuestNameAllowed(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (String blocked : host.configValues().guestBlockedNames) {
            if (blocked != null && n.equals(blocked.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (host.configValues().guestBlockPlayerNameSpoofing) {
            String guestKey = GuestNameSanitizer.spoofComparisonKey(name);
            if (!guestKey.isBlank()) {
                for (String playerName : platform.knownPlayerNameAliases()) {
                    if (playerName != null && guestKey.equals(GuestNameSanitizer.spoofComparisonKey(playerName))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private String sanitizeGuestName(String name) {
        ConfigValues config = host.configValues();
        if (!config.guestAllowCustomName) {
            return sanitizeGeneratedGuestName(name);
        }
        return GuestNameSanitizer.sanitizeCustom(name, 16);
    }

    private String safeGuestNamePrefix() {
        return GuestNameSanitizer.sanitizePrefix(host.configValues().guestNamePrefix, "Guest-");
    }

    private String sanitizeGeneratedGuestName(String name) {
        return GuestNameSanitizer.sanitizeGenerated(name, safeGuestNamePrefix());
    }

    private String generatedGuestNameForIp(String ip) {
        String prefix = safeGuestNamePrefix();
        String seed = ip == null ? "" : ip;
        int number = 1000 + Math.floorMod(seed.hashCode(), 9000);
        return prefix + number;
    }

    private long searchTimeMillis(String raw, long fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            long value = Long.parseLong(raw.trim());
            // Browser Date.getTime() uses milliseconds. Treat very small values as
            // invalid instead of accidentally filtering near 1970.
            return value > 946684800000L ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String normalizeSearchSource(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("all")) return "";
        if (value.equals("game") || value.equals("web") || value.equals("discord") || value.equals("system")) return value;
        if (value.equals("event") || value.equals("server")) return "system";
        return "";
    }

    private int boundedInt(String raw, int fallback, int min, int max) {
        int value = fallback;
        try {
            if (raw != null && !raw.isBlank()) value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
        }
        if (value < min) value = min;
        if (max > 0 && value > max) value = max;
        return value;
    }

    private int effectiveInputLengthLimit(ConfigValues c) {
        if (c == null) return 120;
        if (c.maxMessageLength <= 0 || c.maxUrlMessageLength <= 0) return 0;
        return Math.max(c.maxMessageLength, c.maxUrlMessageLength);
    }

    private String stripChatMessage(String s, ConfigValues config) {
        int max = effectiveMessageLengthLimit(s, config);
        return stripControl(s, max);
    }

    private int effectiveMessageLengthLimit(String s, ConfigValues config) {
        if (config == null) return 120;
        if (s != null && URL_PATTERN.matcher(s).find()) {
            if (config.maxUrlMessageLength <= 0) return 0;
            if (config.maxMessageLength <= 0) return config.maxUrlMessageLength;
            return Math.max(config.maxMessageLength, config.maxUrlMessageLength);
        }
        return Math.max(0, config.maxMessageLength);
    }

    private String stripControl(String s, int maxLen) {
        if (s == null) return "";
        s = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").replace('\n', ' ').replace('\r', ' ').trim();
        if (maxLen > 0 && s.length() > maxLen) {
            s = s.substring(0, maxLen);
        }
        return s;
    }

    private boolean preflight(HttpExchange ex) throws IOException {
        addCors(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        addCors(ex);
        addSecurityHeaders(ex);
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        ex.getResponseHeaders().set("Pragma", "no-cache");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private void sendBytes(HttpExchange ex, int status, String contentType, byte[] data) throws IOException {
        addCors(ex);
        addSecurityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static String urlEncodeComponent(String value) {
        return URLEncoder.encode(String.valueOf(value == null ? "" : value), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private static String htmlEsc(String s) {
        return String.valueOf(s == null ? "" : s)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }

    private void addSecurityHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Referrer-Policy", "strict-origin-when-cross-origin");
        h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=(), serial=()");
    }

    private void addCors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", host.configValues().corsOrigin);
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Authorization, Content-Type, Cache-Control, Pragma");
    }

    private String remoteIp(HttpExchange ex) {
        String actual = socketRemoteIp(ex);
        ConfigValues config = host.configValues();
        String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        boolean trustedProxy = IpAddressMatcher.matchesAny(actual, config.trustedProxies);
        String resolved = actual;

        if (trustedProxy && fwd != null && !fwd.isBlank()) {
            String first = IpAddressMatcher.normalizeIpLiteral(fwd.split(",", 2)[0]);
            if (IpAddressMatcher.isValidAddress(first)) resolved = first;
        }

        if (config.logClientIpResolution) {
            host.logger().info("Client IP resolved: socket=" + actual
                    + ", trustedProxy=" + trustedProxy
                    + ", xForwardedFor=" + (fwd == null ? "" : fwd)
                    + ", result=" + resolved
                    + ", path=" + ex.getRequestURI().getPath());
        }
        return resolved;
    }

    private String socketRemoteIp(HttpExchange ex) {
        return ex.getRemoteAddress() == null || ex.getRemoteAddress().getAddress() == null
                ? ""
                : ex.getRemoteAddress().getAddress().getHostAddress();
    }


}
