package dev.kokoto.webchat;

import java.util.LinkedHashMap;
import java.util.Map;

public class DirectMessageThread {
    public String id = "";
    public String otherUuid = "";
    public String otherUsername = "";
    public String otherDisplayName = "";
    public String lastMessage = "";
    public String lastSenderUuid = "";
    public long lastMessageId;
    public long updatedAt;
    public int unread;

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        PlayerIdentity identity = new PlayerIdentity(otherUuid, otherUsername, otherDisplayName);
        String outputDisplay = identity.outputDisplayName();
        String label = identity.label();
        if (label == null || label.isBlank()) label = otherUuid;

        m.put("id", id);
        m.put("otherUuid", otherUuid);
        m.put("otherUsername", otherUsername);
        m.put("otherDisplayName", outputDisplay);
        m.put("otherLabel", label);
        RemotePlayerRef remote = RemotePlayerRef.parse(otherUuid);
        if (remote != null) {
            m.put("otherRemote", true);
            m.put("otherServerId", remote.serverId);
            m.put("otherServerName", identity.remoteServerName());
            m.put("otherPlayerUuid", remote.playerUuid);
        } else {
            m.put("otherRemote", false);
            m.put("otherServerName", "");
            m.put("otherPlayerUuid", otherUuid);
        }
        m.put("lastMessage", lastMessage);
        m.put("lastSenderUuid", lastSenderUuid);
        m.put("lastMessageId", lastMessageId);
        m.put("updatedAt", updatedAt);
        m.put("unread", unread);
        return JsonUtil.obj(m);
    }
}
