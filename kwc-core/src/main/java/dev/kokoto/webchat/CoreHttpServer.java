package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * JDK HTTP server lifecycle을 감싸 KWC WebChatServer가 사용할 listener/start/stop 기반을 제공한다.
 * Wraps JDK HTTP-server lifecycle to provide the listener/start/stop foundation used by WebChatServer.
 *
 * bind address, thread executor, graceful stop은 외부 reverse proxy와 서버 재시작 안정성에 직접 영향을 준다.
 * Bind address, executor behavior, and graceful shutdown directly affect reverse-proxy deployment and restart stability.
 */
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Platform-neutral JDK HTTP transport used by the web-chat runtime.
 * Minecraft platform modules only supply handlers and lifecycle calls.
 */
public final class CoreHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;
    private volatile boolean started;

    public CoreHttpServer(String host, int port, String threadNamePrefix) throws IOException {
        this(host, port, threadNamePrefix, 256);
    }

    public CoreHttpServer(String host, int port, String threadNamePrefix, int maxThreads) throws IOException {
        String bindHost = host == null ? "" : host;
        String prefix = threadNamePrefix == null || threadNamePrefix.isBlank() ? "WebChat-HTTP" : threadNamePrefix;
        int boundedMaxThreads = Math.max(32, Math.min(512, maxThreads));
        this.server = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        this.executor = new ThreadPoolExecutor(
                0, boundedMaxThreads, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, prefix);
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
        this.server.setExecutor(executor);
    }

    public void createContext(String path, HttpHandler handler) {
        createPrefixContext(path, handler);
    }

    /**
     * Registers an endpoint that must match the request path exactly. JDK HttpServer
     * contexts are prefix-based by default, so without this guard a context such as
     * /api/config would also accept /api/config.yml or /api/config-anything.
     */
    public void createExactContext(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            String requestPath = exchange.getRequestURI() == null ? "" : exchange.getRequestURI().getPath();
            if (!path.equals(requestPath)) {
                sendTransportJson(exchange, 404, "{\"ok\":false,\"error\":\"not_found\"}");
                return;
            }
            handleSafely(exchange, handler);
        });
    }

    /** Registers a deliberately prefix-matched route such as /uploads/ or /fonts/. */
    public void createPrefixContext(String path, HttpHandler handler) {
        server.createContext(path, exchange -> handleSafely(exchange, handler));
    }

    private void handleSafely(com.sun.net.httpserver.HttpExchange exchange, HttpHandler handler) throws IOException {
        try {
            handler.handle(exchange);
        } catch (JsonUtil.BodyTooLargeException tooLarge) {
            sendTransportJson(exchange, 413, "{\"ok\":false,\"error\":\"request_body_too_large\"}");
        }
    }

    private void sendTransportJson(com.sun.net.httpserver.HttpExchange exchange, int status, String json) {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(status, data.length);
            try (var out = exchange.getResponseBody()) { out.write(data); }
        } catch (IOException ignored) {
            try { exchange.close(); } catch (Exception ignored2) {}
        }
    }

    public synchronized void start() {
        if (started) return;
        server.start();
        started = true;
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    public boolean submit(Runnable task) {
        if (task == null || executor.isShutdown()) return false;
        executor.submit(task);
        return true;
    }

    @Override
    public synchronized void close() {
        close(1);
    }

    public synchronized void close(int delaySeconds) {
        if (started) {
            server.stop(Math.max(0, delaySeconds));
            started = false;
        }
        executor.shutdownNow();
    }
}
