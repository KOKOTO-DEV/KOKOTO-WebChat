package dev.kokoto.webchat;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Loader-neutral regression checks for the 5.2.0 HTTP/security baseline. */
public final class SecurityRegressionHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        testPublicReadPolicy();
        testForwardedClientIp();
        testExactVsPrefixRoutes();
        System.out.println("SECURITY REGRESSION HARNESS PASS: " + checks + " assertions");
    }

    private static void testPublicReadPolicy() {
        check(WebChatServer.publicChatReadAllowed(null, null), "null config should not block startup reads");
        ConfigValues c = new ConfigValues();
        c.guestEnabled = true;
        c.hideChatForGuestsWhenGuestDisabled = true;
        check(WebChatServer.publicChatReadAllowed(c, null), "guest-enabled public read blocked");
        c.guestEnabled = false;
        c.hideChatForGuestsWhenGuestDisabled = false;
        check(WebChatServer.publicChatReadAllowed(c, null), "explicit visible guest-disabled chat blocked");
        c.hideChatForGuestsWhenGuestDisabled = true;
        check(!WebChatServer.publicChatReadAllowed(c, null), "hidden guest-disabled chat allowed anonymously");
        check(WebChatServer.publicChatReadAllowed(c, new SessionContext(null, null)), "authenticated context blocked");
    }

    private static void testForwardedClientIp() {
        List<String> trusted = List.of("127.0.0.0/8", "10.0.0.0/8");
        check("198.51.100.20".equals(IpAddressMatcher.resolveForwardedClientIp("198.51.100.20", "127.0.0.1", trusted)),
                "untrusted socket accepted XFF");
        check("203.0.113.8".equals(IpAddressMatcher.resolveForwardedClientIp("127.0.0.1", "203.0.113.8", trusted)),
                "single trusted proxy did not resolve client");
        check("203.0.113.9".equals(IpAddressMatcher.resolveForwardedClientIp("127.0.0.1", "127.0.0.1, 203.0.113.9", trusted)),
                "client-supplied leftmost XFF spoof won");
        check("198.51.100.44".equals(IpAddressMatcher.resolveForwardedClientIp("10.0.0.2", "127.0.0.1, 198.51.100.44, 10.0.0.1", trusted)),
                "multi-proxy trusted chain resolved incorrectly");
        check("127.0.0.1".equals(IpAddressMatcher.resolveForwardedClientIp("127.0.0.1", "203.0.113.8, not-an-ip", trusted)),
                "malformed XFF did not fail closed to socket peer");
    }

    private static void testExactVsPrefixRoutes() throws Exception {
        try (CoreHttpServer server = new CoreHttpServer("127.0.0.1", 0, "KWC-Security-Harness", 32)) {
            server.createExactContext("/api/config", ex -> ok(ex));
            server.createPrefixContext("/uploads/", ex -> ok(ex));
            server.start();
            int port = server.address().getPort();
            HttpClient client = HttpClient.newHttpClient();
            check(status(client, port, "/api/config") == 200, "canonical exact route failed");
            check(status(client, port, "/api/config.yml") == 404, "suffix route matched exact context");
            check(status(client, port, "/api/config123") == 404, "concatenated suffix matched exact context");
            check(status(client, port, "/api/config/anything") == 404, "child path matched exact context");
            check(status(client, port, "/uploads/file.png") == 200, "intentional prefix route stopped matching children");
        }
    }

    private static int status(HttpClient client, int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static void ok(HttpExchange ex) throws IOException {
        byte[] data = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, data.length);
        try (var out = ex.getResponseBody()) { out.write(data); }
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
