package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 그룹방에 고정된 메시지 snapshot과 명시적 표시 순서를 표현한다.
 * Represents a pinned group-message snapshot and its explicit display order.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
import java.util.LinkedHashMap;
import java.util.Map;

/** Room-local pinned-message snapshot for group chat. */
public final class GroupPinnedMessage {
    public String pinId = "";
    public String roomId = "";
    public long messageId;
    public long pinnedAt;
    public long sortOrder;
    public String pinnedByUuid = "";
    public String pinnedByUsername = "";
    public String pinnedByDisplayName = "";
    public String pinnedBy = "";
    public long time;
    public String senderUuid = "";
    public String senderUsername = "";
    public String senderDisplayName = "";
    public String body = "";
    public String eventType = "";

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pinId", pinId == null ? "" : pinId);
        m.put("roomId", roomId == null ? "" : roomId);
        m.put("messageId", messageId);
        m.put("pinnedAt", pinnedAt);
        m.put("sortOrder", sortOrder);
        m.put("pinnedByUuid", pinnedByUuid == null ? "" : pinnedByUuid);
        m.put("pinnedByUsername", pinnedByUsername == null ? "" : pinnedByUsername);
        m.put("pinnedByDisplayName", pinnedByDisplayName == null ? "" : pinnedByDisplayName);
        m.put("pinnedBy", pinnedBy == null ? "" : pinnedBy);
        m.put("time", time);
        m.put("senderUuid", senderUuid == null ? "" : senderUuid);
        m.put("senderUsername", senderUsername == null ? "" : senderUsername);
        m.put("senderDisplayName", senderDisplayName == null ? "" : senderDisplayName);
        m.put("body", body == null ? "" : body);
        m.put("eventType", eventType == null ? "" : eventType);
        return JsonUtil.obj(m);
    }
}
