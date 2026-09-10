package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * DM 메시지 한 건의 본문, sender, relay/delivery, reply snapshot 상태를 표현한다.
 * Represents one DM message including body, sender, relay/delivery state, and reply snapshot.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
import java.util.LinkedHashMap;
import java.util.Map;

public class DirectMessageMessage {
    public long id;
    public String threadId = "";
    public String senderUuid = "";
    public String senderUsername = "";
    public String senderDisplayName = "";
    public String body = "";
    public long createdAt;
    public String deliveryStatus = "delivered";
    public String deliveryError = "";
    public String relayId = "";
    public String clientMessageId = "";
    public long replyToId = 0L;
    public String replyToSender = "";
    public String replyToPreview = "";
    /** Stable relay id of the replied-to DM when that target crossed servers. */
    public String replyToRelayId = "";
    public boolean readByOther = false;
    public int unreadRecipientCount = 1;

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("threadId", threadId);
        m.put("senderUuid", senderUuid);
        m.put("senderUsername", senderUsername);
        m.put("senderDisplayName", senderDisplayName);
        m.put("body", body);
        m.put("time", createdAt);
        m.put("deliveryStatus", deliveryStatus == null || deliveryStatus.isBlank() ? "delivered" : deliveryStatus);
        m.put("deliveryError", deliveryError == null ? "" : deliveryError);
        m.put("relayId", relayId == null ? "" : relayId);
        m.put("clientMessageId", clientMessageId == null ? "" : clientMessageId);
        m.put("replyToId", Math.max(0L, replyToId));
        m.put("replyToSender", replyToSender == null ? "" : replyToSender);
        m.put("replyToPreview", replyToPreview == null ? "" : replyToPreview);
        m.put("replyToRelayId", replyToRelayId == null ? "" : replyToRelayId);
        m.put("readByOther", readByOther);
        m.put("unreadRecipientCount", Math.max(0, unreadRecipientCount));
        return JsonUtil.obj(m);
    }
}
