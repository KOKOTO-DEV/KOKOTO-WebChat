package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 공개 채팅의 고정 메시지 snapshot과 순서를 표현한다.
 * Represents a public-chat pinned-message snapshot and order.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
import java.util.LinkedHashMap;
import java.util.Map;

public class PinnedMessage {
    public String pinId;
    public String messageId;
    public long pinnedAt;
    public long sortOrder;
    public String pinnedByUuid;
    public String pinnedByUsername;
    public String pinnedByDisplayName;
    public String pinnedBy;
    public long time;
    public String source;
    public String sender;
    public String realSender;
    public String playerUuid;
    public String role;
    public String message;
    public String i18nKey;
    public String i18nArgs;
    public boolean hidden;

    public static PinnedMessage fromMessage(ChatMessage msg, String pinnedByUuid, String pinnedByUsername, String pinnedByDisplayName) {
        PinnedMessage pin = new PinnedMessage();
        pin.pinId = "pin-" + SecurityUtil.randomToken(10);
        pin.messageId = msg == null ? "" : value(msg.id, "");
        pin.pinnedAt = System.currentTimeMillis();
        pin.sortOrder = pin.pinnedAt;
        pin.pinnedByUuid = value(pinnedByUuid, "");
        pin.pinnedByUsername = value(pinnedByUsername, "");
        pin.pinnedByDisplayName = value(pinnedByDisplayName, "");
        pin.pinnedBy = !pin.pinnedByDisplayName.isBlank() ? pin.pinnedByDisplayName
                : !pin.pinnedByUsername.isBlank() ? pin.pinnedByUsername : pin.pinnedByUuid;
        pin.time = msg == null ? pin.pinnedAt : msg.time;
        pin.source = msg == null ? "web" : value(msg.source, "web");
        pin.sender = msg == null ? "Unknown" : value(msg.sender, "Unknown");
        pin.realSender = msg == null ? "" : value(msg.realSender, "");
        pin.playerUuid = msg == null ? "" : value(msg.playerUuid, "");
        pin.role = msg == null ? "USER" : value(msg.role, "USER");
        pin.message = msg == null ? "" : value(msg.message, "");
        pin.i18nKey = msg == null ? "" : value(msg.i18nKey, "");
        pin.i18nArgs = msg == null ? "" : value(msg.i18nArgs, "");
        pin.hidden = msg != null && msg.hidden;
        return pin;
    }

    public Map<String, Object> toMap(boolean publicView) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pinId", value(pinId, ""));
        m.put("messageId", value(messageId, ""));
        m.put("pinnedAt", pinnedAt);
        m.put("sortOrder", sortOrder);
        m.put("pinnedByUuid", value(pinnedByUuid, ""));
        m.put("pinnedByUsername", value(pinnedByUsername, ""));
        m.put("pinnedByDisplayName", value(pinnedByDisplayName, ""));
        m.put("pinnedBy", value(pinnedBy, ""));
        m.put("time", time);
        m.put("source", value(source, "web"));
        m.put("sender", value(sender, "Unknown"));
        if (realSender != null && !realSender.isBlank() && !realSender.equals(sender)) m.put("realSender", realSender);
        if (playerUuid != null && !playerUuid.isBlank()) m.put("playerUuid", playerUuid);
        m.put("role", value(role, "USER"));
        m.put("hidden", hidden);
        m.put("message", publicView && hidden ? "[deleted]" : value(message, ""));
        if (i18nKey != null && !i18nKey.isBlank()) m.put("i18nKey", i18nKey);
        if (i18nArgs != null && !i18nArgs.isBlank()) m.put("i18nArgs", i18nArgs);
        return m;
    }

    public String toJson() {
        return JsonUtil.obj(toMap(true));
    }

    private static String value(String s, String fallback) {
        return s == null ? fallback : s;
    }
}
