package dev.kokoto.webchat;

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
