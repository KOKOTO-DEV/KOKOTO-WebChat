package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * WebChatModeration는 메시지 moderation/content-filter 파이프라인의 일부다.
 * WebChatModeration is part of the message moderation/content-filter pipeline.
 *
 * 필터 결과는 공개/DM/그룹 등 호출 위치에 따라 정책이 달라질 수 있으므로 parsing, matching, enforcement를 한 단계로 섞지 않는다.
 * Filter policy can vary by public/DM/group context, so parsing, matching, and enforcement should remain separate stages.
 */
import java.util.List;

/** Loader-neutral guest moderation boundary used by the web server. */
public interface WebChatModeration {
    ModerationEntry mute(String type, String value, long minutes, String reason, String createdBy);
    boolean unmute(String type, String value);
    boolean isMuted(String guestName, String ip);
    ModerationEntry findMatch(String guestName, String ip);
    List<ModerationEntry> list();
}
