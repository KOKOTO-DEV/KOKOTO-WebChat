package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 그룹방 메시지 또는 membership/system event 한 건을 표현한다.
 * Represents one group-room message or membership/system event.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
import java.util.LinkedHashMap;
import java.util.Map;

public class GroupMessage {
    public long id;
    public String roomId = "";
    public String senderUuid = "";
    public String senderUsername = "";
    public String senderDisplayName = "";
    public String body = "";
    public String eventType = "";
    public long createdAt;
    public String deliveryStatus = "delivered";
    public long replyToId = 0L;
    public String replyToSender = "";
    public String replyToPreview = "";
    public int unreadMemberCount = 0;

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("roomId", roomId);
        m.put("senderUuid", senderUuid);
        m.put("senderUsername", senderUsername);
        m.put("senderDisplayName", senderDisplayName);
        m.put("body", body);
        m.put("eventType", eventType == null ? "" : eventType);
        m.put("time", createdAt);
        m.put("deliveryStatus", deliveryStatus == null || deliveryStatus.isBlank() ? "delivered" : deliveryStatus);
        m.put("replyToId", Math.max(0L, replyToId));
        m.put("replyToSender", replyToSender == null ? "" : replyToSender);
        m.put("replyToPreview", replyToPreview == null ? "" : replyToPreview);
        m.put("unreadMemberCount", Math.max(0, unreadMemberCount));
        return JsonUtil.obj(m);
    }
}
