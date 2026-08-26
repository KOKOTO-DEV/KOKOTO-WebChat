package dev.kokoto.webchat;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModerationEntry {
    public String key;
    public String type; // guest, ip
    public String value;
    public String reason;
    public String createdBy;
    public long createdAt;
    public long expiresAt;

    public boolean expired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("type", type);
        m.put("value", value);
        m.put("reason", reason == null ? "" : reason);
        m.put("createdBy", createdBy == null ? "" : createdBy);
        m.put("createdAt", createdAt);
        m.put("expiresAt", expiresAt);
        return m;
    }
}
