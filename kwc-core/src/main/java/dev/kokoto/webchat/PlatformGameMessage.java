package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 플랫폼별 게임 채팅 이벤트를 core가 이해하는 중립 형태로 옮기는 모델이다.
 * Neutral model translating platform-specific game-chat events into a form understood by core.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
/**
 * Loader-neutral description of one Minecraft chat line with optional
 * click/hover interactions. Platform modules translate this into their native
 * chat component API (Bungee components, Adventure components, etc.).
 */
public record PlatformGameMessage(
        String text,
        boolean clickableUrls,
        String senderTarget,
        String senderHoverText,
        String senderSuggestCommand,
        String replyTarget,
        String replyHoverText,
        String replySuggestCommand
) {
    public PlatformGameMessage {
        text = safe(text);
        senderTarget = safe(senderTarget);
        senderHoverText = safe(senderHoverText);
        senderSuggestCommand = safe(senderSuggestCommand);
        replyTarget = safe(replyTarget);
        replyHoverText = safe(replyHoverText);
        replySuggestCommand = safe(replySuggestCommand);
    }

    public boolean interactive() {
        return clickableUrls
                || (!senderTarget.isBlank() && (!senderHoverText.isBlank() || !senderSuggestCommand.isBlank()))
                || (!replyTarget.isBlank() && (!replyHoverText.isBlank() || !replySuggestCommand.isBlank()));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
