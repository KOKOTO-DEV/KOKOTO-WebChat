package dev.kokoto.webchat;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry for live SSE connections. */
public final class SseHub implements AutoCloseable {
    private final Set<SseConnection> connections = ConcurrentHashMap.newKeySet();

    public SseConnection add(OutputStream out, String ip, String accountUuid, String token, boolean privateChatSuperAdmin) {
        SseConnection connection = new SseConnection(out, ip, accountUuid, token, privateChatSuperAdmin);
        connections.add(connection);
        return connection;
    }

    public void remove(SseConnection connection) {
        if (connection != null) connections.remove(connection);
    }

    public int size() {
        return connections.size();
    }

    public int countByIp(String ip) {
        int count = 0;
        for (SseConnection connection : connections) {
            if (java.util.Objects.equals(connection.ip(), ip)) count++;
        }
        return count;
    }

    public List<SseConnection> snapshot() {
        return new ArrayList<>(connections);
    }

    @Override
    public void close() {
        for (SseConnection connection : snapshot()) connection.close();
        connections.clear();
    }
}
