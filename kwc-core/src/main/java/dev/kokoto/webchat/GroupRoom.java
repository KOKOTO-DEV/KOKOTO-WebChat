package dev.kokoto.webchat;

import java.util.LinkedHashMap;
import java.util.Map;

public class GroupRoom {
    public String id = "";
    public String name = "";
    public String ownerUuid = "";
    public String visibility = "private";
    public boolean passwordProtected;
    public boolean member;
    public String role = "";
    public String lastMessage = "";
    public String lastSenderUuid = "";
    public long lastMessageId;
    public long updatedAt;
    public int unread;
    public int memberCount;
    public int onlineMemberCount;
    public boolean membershipEventsEnabled = true;

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("ownerUuid", ownerUuid);
        m.put("visibility", visibility);
        m.put("passwordProtected", passwordProtected);
        m.put("member", member);
        m.put("role", role);
        m.put("lastMessage", lastMessage);
        m.put("lastSenderUuid", lastSenderUuid);
        m.put("lastMessageId", lastMessageId);
        m.put("updatedAt", updatedAt);
        m.put("unread", unread);
        m.put("memberCount", memberCount);
        m.put("onlineMemberCount", onlineMemberCount);
        m.put("membershipEventsEnabled", membershipEventsEnabled);
        return JsonUtil.obj(m);
    }
}
