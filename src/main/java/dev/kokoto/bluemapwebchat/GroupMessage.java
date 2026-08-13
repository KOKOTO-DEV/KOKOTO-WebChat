package dev.kokoto.bluemapwebchat;

import java.util.LinkedHashMap;
import java.util.Map;

public class GroupMessage {
    public long id;
    public String roomId = "";
    public String senderUuid = "";
    public String senderUsername = "";
    public String senderDisplayName = "";
    public String body = "";
    public long createdAt;
    public String deliveryStatus = "delivered";
    public int unreadMemberCount = 0;

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("roomId", roomId);
        m.put("senderUuid", senderUuid);
        m.put("senderUsername", senderUsername);
        m.put("senderDisplayName", senderDisplayName);
        m.put("body", body);
        m.put("time", createdAt);
        m.put("deliveryStatus", deliveryStatus == null || deliveryStatus.isBlank() ? "delivered" : deliveryStatus);
        m.put("unreadMemberCount", Math.max(0, unreadMemberCount));
        return JsonUtil.obj(m);
    }
}
