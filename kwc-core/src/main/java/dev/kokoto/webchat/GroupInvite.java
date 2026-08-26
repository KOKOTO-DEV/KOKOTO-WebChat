package dev.kokoto.webchat;

import java.util.LinkedHashMap;
import java.util.Map;

public class GroupInvite {
    public long id;
    public String roomId = "";
    public String roomName = "";
    public String inviterUuid = "";
    public String inviterUsername = "";
    public String inviterDisplayName = "";
    public long createdAt;
    public long expiresAt;

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("roomId", roomId);
        m.put("roomName", roomName);
        m.put("inviterUuid", inviterUuid);
        m.put("inviterUsername", inviterUsername);
        m.put("inviterDisplayName", inviterDisplayName);
        m.put("createdAt", createdAt);
        m.put("expiresAt", expiresAt);
        return JsonUtil.obj(m);
    }
}
