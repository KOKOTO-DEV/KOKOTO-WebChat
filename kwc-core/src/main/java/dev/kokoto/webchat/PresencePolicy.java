package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * PresencePolicy는 kwc-core 모듈의 KWC 구현 파일이다. 클래스 이름이 나타내는 책임을 이 파일 안에 한정해 다른 계층과의 결합을 줄인다.
 * PresencePolicy is a KWC implementation file in the kwc-core module. Keep the responsibility implied by the class name localized here to reduce cross-layer coupling.
 *
 * 변경 시 호출자와 반환값뿐 아니라 인증/권한, thread context, persistence, multi-loader 호환성에 미치는 영향을 함께 확인한다.
 * When changing it, review not only callers/returns but also effects on authorization, thread context, persistence, and multi-loader compatibility.
 */
import java.util.LinkedHashMap;
import java.util.Map;

/** Viewer-aware presence policy shared by compact lists and profile details. */
/**
 * KWC 유지보수 안내: Game/Web 접속 상태와 Offline privacy를 viewer 기준 결과로 변환하는 순수 정책 클래스다. compact 대표 상태는 Game이 Web보다 우선하며, Offline 사용자는 본인이 아닌 viewer에게 gameOnline/webOnline 원값 자체가 false로 마스킹된다.
 *
 * KWC maintenance note: Pure policy converting Game/Web connectivity and Offline privacy into a viewer-specific result. Compact representative presence prefers Game over Web, and an account choosing Offline has its raw gameOnline/webOnline values masked to false for every non-self viewer.
 */
public final class PresencePolicy {
    private PresencePolicy() {}

    public record Result(boolean gameOnline, boolean webOnline, boolean online, String source, String status) {
        public Map<String,Object> toMap(boolean selfView) {
            LinkedHashMap<String,Object> out = new LinkedHashMap<>();
            out.put("online", online);
            out.put("source", source == null || source.isBlank() ? "offline" : source);
            out.put("status", status == null || status.isBlank() ? "offline" : status);
            out.put("gameOnline", gameOnline);
            out.put("webOnline", webOnline);
            return out;
        }
    }

    public static Result resolve(boolean gameOnline, boolean webOnline, String requestedStatus, boolean selfView) {
        String manual = normalizeStatus(requestedStatus);
        boolean privacyMasked = "offline".equals(manual);
        boolean visibleGame = gameOnline;
        boolean visibleWeb = webOnline;
        if (privacyMasked && !selfView) {
            visibleGame = false;
            visibleWeb = false;
        }
        boolean connected = visibleGame || visibleWeb;
        String source = visibleGame ? "game" : visibleWeb ? "web" : "offline";
        String status = privacyMasked ? "offline" : (connected && "busy".equals(manual) ? "busy" : connected ? "online" : "offline");
        return new Result(visibleGame, visibleWeb, connected, source, status);
    }

    private static String normalizeStatus(String raw) {
        String status = String.valueOf(raw == null ? "" : raw).trim().toLowerCase(java.util.Locale.ROOT);
        if ("busy".equals(status) || "offline".equals(status)) return status;
        return "online";
    }
}
