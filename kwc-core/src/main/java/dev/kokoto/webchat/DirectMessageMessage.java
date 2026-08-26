package dev.kokoto.webchat;

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
        m.put("readByOther", readByOther);
        m.put("unreadRecipientCount", Math.max(0, unreadRecipientCount));
        return JsonUtil.obj(m);
    }
}
