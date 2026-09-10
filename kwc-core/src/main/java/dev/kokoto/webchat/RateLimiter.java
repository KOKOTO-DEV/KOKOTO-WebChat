package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * RateLimiter는 인증·입력 검증·접속 제한 중 하나를 담당하는 보안 경계 코드다.
 * RateLimiter is security-boundary code responsible for authentication, input validation, or access limiting.
 *
 * 화면에서 버튼을 숨기는 것은 권한 검사가 아니므로, 모든 민감한 작업은 서버에서 UUID/세션/권한을 다시 검증해야 한다.
 * Hiding a button is not authorization; every sensitive action must revalidate UUID/session/permission on the server.
 */
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private static final class Bucket {
        long lastSent;
        final Deque<Long> minute = new ArrayDeque<>();
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean allow(String key, int cooldownSeconds, int maxPerMinute) {
        long now = System.currentTimeMillis();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket());
        synchronized (b) {
            if (cooldownSeconds > 0 && now - b.lastSent < cooldownSeconds * 1000L) {
                return false;
            }
            long cutoff = now - 60_000L;
            while (!b.minute.isEmpty() && b.minute.peekFirst() < cutoff) {
                b.minute.removeFirst();
            }
            if (maxPerMinute > 0 && b.minute.size() >= maxPerMinute) {
                return false;
            }
            b.lastSent = now;
            b.minute.addLast(now);
            return true;
        }
    }
}
