package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 그룹방의 식별자, 이름, owner, visibility와 설정 요약을 표현한다.
 * Represents group-room identity, name, owner, visibility, and configuration summary.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
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
    public boolean pinsEnabled = true;
    public boolean messageDeleteEnabled = true;
    public boolean memberSelfDeleteEnabled = true;

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
        m.put("pinsEnabled", pinsEnabled);
        m.put("messageDeleteEnabled", messageDeleteEnabled);
        m.put("memberSelfDeleteEnabled", memberSelfDeleteEnabled);
        return JsonUtil.obj(m);
    }
}
