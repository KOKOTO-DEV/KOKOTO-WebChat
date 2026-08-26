package dev.kokoto.webchat;

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
