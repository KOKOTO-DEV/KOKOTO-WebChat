package dev.kokoto.webchat;

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
