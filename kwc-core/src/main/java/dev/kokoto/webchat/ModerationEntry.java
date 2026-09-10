package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * moderation/audit 화면에 전달할 단일 관리 기록을 표현한다.
 * Represents one moderation/audit entry exposed to administrative views.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
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
