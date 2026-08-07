package dev.kokoto.bluemapwebchat;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerIdentity {
    public final String uuid;
    public final String username;
    public final String displayName;

    public PlayerIdentity(String uuid, String username, String displayName) {
        this.uuid = uuid == null ? "" : uuid;
        this.username = username == null ? "" : username;
        String rawDisplay = displayName == null ? "" : displayName;
        RemotePlayerRef remote = RemotePlayerRef.parse(this.uuid);
        if (remote != null) {
            String serverName = RemotePlayerRef.displayServerName(rawDisplay, "", remote.serverId);
            rawDisplay = RemotePlayerRef.decorateDisplayName(rawDisplay, this.username, serverName, remote.serverId);
        }
        this.displayName = rawDisplay;
    }

    public String outputDisplayName() {
        RemotePlayerRef remote = RemotePlayerRef.parse(uuid);
        if (remote == null) return displayName;
        String serverName = RemotePlayerRef.displayServerName(displayName, "", remote.serverId);
        return RemotePlayerRef.decorateDisplayName(displayName, username, serverName, remote.serverId);
    }

    public String remoteServerName() {
        RemotePlayerRef remote = RemotePlayerRef.parse(uuid);
        if (remote == null) return "";
        return RemotePlayerRef.displayServerName(displayName, "", remote.serverId);
    }

    public String label() {
        String outputDisplay = outputDisplayName();
        if (!outputDisplay.isBlank() && !username.isBlank() && !outputDisplay.equals(username)) return outputDisplay + " (" + username + ")";
        if (!outputDisplay.isBlank()) return outputDisplay;
        return username;
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        String outputDisplay = outputDisplayName();
        m.put("uuid", uuid);
        m.put("username", username);
        m.put("displayName", outputDisplay);
        m.put("label", label());
        RemotePlayerRef remote = RemotePlayerRef.parse(uuid);
        if (remote != null) {
            m.put("remote", true);
            m.put("serverId", remote.serverId);
            m.put("serverName", remoteServerName());
            m.put("playerUuid", remote.playerUuid);
        } else {
            m.put("remote", false);
            m.put("serverName", "");
            m.put("playerUuid", uuid);
        }
        return JsonUtil.obj(m);
    }
}
