package dev.kokoto.webchat;

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
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } catch (JsonUtil.BodyTooLargeException tooLarge) {
                byte[] data = "{\"ok\":false,\"error\":\"request_body_too_large\"}".getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                    exchange.sendResponseHeaders(413, data.length);
                    try (var out = exchange.getResponseBody()) { out.write(data); }
                } catch (IOException ignored) {
                    try { exchange.close(); } catch (Exception ignored2) {}
                }
            }
        });
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
