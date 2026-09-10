package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 콘텐츠 필터가 메시지를 검사한 결과와 차단/표시 판단을 전달한다.
 * Carries the result of content filtering, including block/display decisions.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
/** Result of one content-filter pass. */
public final class ContentFilterResult {
    public final boolean blocked;
    public final boolean changed;
    public final String message;
    public final String matchedWord;
    public final String matchedText;
    public final String ruleId;
    public final String matchMode;

    public ContentFilterResult(boolean blocked, boolean changed, String message,
                               String matchedWord, String matchedText, String ruleId, String matchMode) {
        this.blocked = blocked;
        this.changed = changed;
        this.message = message == null ? "" : message;
        this.matchedWord = matchedWord == null ? "" : matchedWord;
        this.matchedText = matchedText == null ? "" : matchedText;
        this.ruleId = ruleId == null ? "" : ruleId;
        this.matchMode = matchMode == null ? "" : matchMode;
    }

    public static ContentFilterResult unchanged(String message) {
        return new ContentFilterResult(false, false, message, "", "", "", "");
    }
}
