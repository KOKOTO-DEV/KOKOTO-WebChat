package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 그룹 초대의 대상·방·발급 시각 같은 전달 상태를 표현한다.
 * Represents group-invite delivery state such as target, room, and issue time.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
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
