package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * SseHub는 브라우저로 전달되는 장기 SSE 연결과 broadcast lifecycle을 관리한다.
 * SseHub manages long-lived SSE connections and their broadcast lifecycle to browsers.
 *
 * 연결 종료·heartbeat·write 실패 시 client 정리를 놓치면 connection limit 누수와 stale presence가 생기므로 cleanup 경로가 중요하다.
 * Cleanup on disconnect, heartbeat failure, or write error is critical to prevent connection-limit leaks and stale presence.
 */
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
