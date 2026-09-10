package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 공개 채팅 한 건의 저장/전송 데이터를 표현하는 메시지 모델이다.
 * Message model representing one persisted/transmitted public-chat entry.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
import java.util.LinkedHashMap;
import java.util.Map;

public class ChatMessage {
    public String id;
    public long time;
    public String source;
    public String sender;
    public String realSender;
    public String playerUuid;
    public String relayId;
    public String originServerId;
    public String originServerName;
    public int relayHop;
    public String role;
    public String message;
    // Transient game-render variant. Not persisted or exposed to web clients.
    public String gameMessage;
    public String i18nKey;
    public String i18nArgs;
    public String replyToId;
    public String replyToSender;
    public String replyToPreview;
    public boolean hidden;

    public ChatMessage(long time, String source, String sender, String role, String message) {
        this.id = SecurityUtil.randomToken(8);
        this.time = time;
        this.source = source;
        this.sender = sender;
        this.role = role;
        this.message = message;
    }

    public ChatMessage withGameMessage(String gameMessage) {
        this.gameMessage = gameMessage == null ? "" : gameMessage;
        return this;
    }

    public ChatMessage withRealSender(String realSender, String playerUuid) {
        this.realSender = realSender == null ? "" : realSender;
        this.playerUuid = playerUuid == null ? "" : playerUuid;
        return this;
    }

    public ChatMessage withI18n(String key, String argsJson) {
        this.i18nKey = key == null ? "" : key;
        this.i18nArgs = argsJson == null ? "" : argsJson;
        return this;
    }

    public ChatMessage withReply(String replyToId, String replyToSender, String replyToPreview) {
        this.replyToId = replyToId == null ? "" : replyToId;
        this.replyToSender = replyToSender == null ? "" : replyToSender;
        this.replyToPreview = replyToPreview == null ? "" : replyToPreview;
        return this;
    }

    public String toJson() {
        Map<String, Object> m = baseMap();
        m.put("message", hidden ? "[deleted]" : message);
        return JsonUtil.obj(m);
    }

    public String toPersistJson() {
        Map<String, Object> m = baseMap();
        m.put("message", message);
        return JsonUtil.obj(m);
    }

    private Map<String, Object> baseMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("time", time);
        m.put("source", source);
        m.put("sender", sender);
        if (realSender != null && !realSender.isBlank() && !realSender.equals(sender)) m.put("realSender", realSender);
        if (playerUuid != null && !playerUuid.isBlank()) m.put("playerUuid", playerUuid);
        if (relayId != null && !relayId.isBlank()) m.put("relayId", relayId);
        if (originServerId != null && !originServerId.isBlank()) m.put("originServerId", originServerId);
        if (originServerName != null && !originServerName.isBlank()) m.put("originServerName", originServerName);
        if (relayHop > 0) m.put("relayHop", relayHop);
        m.put("role", role);
        m.put("hidden", hidden);
        if (i18nKey != null && !i18nKey.isBlank()) m.put("i18nKey", i18nKey);
        if (i18nArgs != null && !i18nArgs.isBlank()) m.put("i18nArgs", i18nArgs);
        if (replyToId != null && !replyToId.isBlank()) m.put("replyToId", replyToId);
        if (replyToSender != null && !replyToSender.isBlank()) m.put("replyToSender", replyToSender);
        if (replyToPreview != null && !replyToPreview.isBlank()) m.put("replyToPreview", replyToPreview);
        return m;
    }

    public static ChatMessage fromMap(Map<String, String> m) {
        long time = parseLong(m.get("time"), System.currentTimeMillis());
        String source = value(m.get("source"), "web");
        String sender = value(m.get("sender"), "Unknown");
        String role = value(m.get("role"), "USER");
        String message = value(m.get("message"), "");
        ChatMessage msg = new ChatMessage(time, source, sender, role, message);
        msg.realSender = value(m.get("realSender"), "");
        msg.playerUuid = value(m.get("playerUuid"), "");
        msg.relayId = value(m.get("relayId"), "");
        msg.originServerId = value(m.get("originServerId"), "");
        msg.originServerName = value(m.get("originServerName"), "");
        msg.relayHop = (int) parseLong(m.get("relayHop"), 0);
        String id = m.get("id");
        if (id != null && !id.isBlank()) msg.id = id;
        msg.i18nKey = value(m.get("i18nKey"), "");
        msg.i18nArgs = value(m.get("i18nArgs"), "");
        msg.replyToId = value(m.get("replyToId"), "");
        msg.replyToSender = value(m.get("replyToSender"), "");
        msg.replyToPreview = value(m.get("replyToPreview"), "");
        msg.hidden = Boolean.parseBoolean(value(m.get("hidden"), "false"));
        return msg;
    }

    private static String value(String s, String fallback) {
        return s == null ? fallback : s;
    }

    private static long parseLong(String s, long fallback) {
        try {
            return s == null ? fallback : Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
