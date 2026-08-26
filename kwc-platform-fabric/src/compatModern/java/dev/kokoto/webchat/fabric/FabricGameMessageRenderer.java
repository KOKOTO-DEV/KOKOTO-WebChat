package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.PlatformGameMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minecraft 1.21.5+ native chat-component renderer used by the Fabric platform. */
final class FabricGameMessageRenderer {
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)((?:https?://|www\\.)[^\\s<>\"]+)");

    private FabricGameMessageRenderer() {}

    static Component plain(String text) {
        MutableComponent root = Component.empty();
        appendLegacy(root, String.valueOf(text == null ? "" : text), null, null);
        return root;
    }

    static Component interactive(PlatformGameMessage message) {
        if (message == null) return Component.empty();
        MutableComponent root = Component.empty();
        String text = message.text();
        int senderIndex = message.senderTarget().isBlank() ? -1 : text.indexOf(message.senderTarget());
        if (senderIndex < 0) {
            appendReplyAwareText(root, text, message);
            return root;
        }

        PlatformGameMessage withoutSender = withoutSender(message);
        appendReplyAwareText(root, text.substring(0, senderIndex), withoutSender);
        appendLegacy(root, message.senderTarget(), message.senderHoverText(), message.senderSuggestCommand());
        appendReplyAwareText(root, text.substring(senderIndex + message.senderTarget().length()), withoutSender);
        return root;
    }

    private static PlatformGameMessage withoutSender(PlatformGameMessage message) {
        return new PlatformGameMessage(message.text(), message.clickableUrls(), "", "", "",
                message.replyTarget(), message.replyHoverText(), message.replySuggestCommand());
    }

    private static void appendReplyAwareText(MutableComponent root, String text, PlatformGameMessage message) {
        if (text == null || text.isEmpty()) return;
        if (message.replyTarget().isBlank() || message.replySuggestCommand().isBlank()) {
            appendLegacyWithUrls(root, text, message.clickableUrls(), null, null);
            return;
        }
        int index = text.lastIndexOf(message.replyTarget());
        if (index < 0) {
            appendLegacyWithUrls(root, text, message.clickableUrls(), null, null);
            return;
        }
        appendLegacyWithUrls(root, text.substring(0, index), message.clickableUrls(), null, null);
        appendLegacyWithUrls(root, message.replyTarget(), message.clickableUrls(), message.replyHoverText(), message.replySuggestCommand());
        if (message.clickableUrls() && containsOnlyClickableUrls(message.replyTarget())) {
            appendLegacy(root, " §8[↩]", message.replyHoverText(), message.replySuggestCommand());
        }
        appendLegacyWithUrls(root, text.substring(index + message.replyTarget().length()), message.clickableUrls(), null, null);
    }

    private static boolean containsOnlyClickableUrls(String text) {
        String value = String.valueOf(text == null ? "" : text).trim();
        if (value.isEmpty()) return false;
        Matcher matcher = URL_PATTERN.matcher(value);
        if (!matcher.find()) return false;
        return matcher.replaceAll("").trim().isEmpty();
    }

    private static void appendLegacyWithUrls(MutableComponent root, String text, boolean clickableUrls,
                                             String hoverText, String suggestCommand) {
        if (text == null || text.isEmpty()) return;
        if (!clickableUrls) {
            appendLegacy(root, text, hoverText, suggestCommand);
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            appendLegacy(root, text.substring(last, matcher.start()), hoverText, suggestCommand);
            String[] split = splitUrlTrailing(matcher.group(1));
            String url = split[0];
            if (!url.isBlank()) appendOpenUrl(root, url);
            appendLegacy(root, split[1], hoverText, suggestCommand);
            last = matcher.end();
        }
        appendLegacy(root, text.substring(last), hoverText, suggestCommand);
    }

    private static void appendOpenUrl(MutableComponent root, String url) {
        ClickEvent event = null;
        try {
            event = new ClickEvent.OpenUrl(URI.create(normalizeClickUrl(url)));
        } catch (IllegalArgumentException ignored) {
            // Keep malformed URLs visible without attaching an unsafe/broken click action.
        }
        appendLegacyEvent(root, url, null, event);
    }

    private static void appendLegacy(MutableComponent root, String text, String hoverText, String suggestCommand) {
        ClickEvent click = suggestCommand == null || suggestCommand.isBlank()
                ? null : new ClickEvent.SuggestCommand(suggestCommand);
        appendLegacyEvent(root, text, hoverText, click);
    }

    private static void appendLegacyEvent(MutableComponent root, String text, String hoverText, ClickEvent clickEvent) {
        if (text == null || text.isEmpty()) return;
        HoverEvent hover = hoverText == null || hoverText.isBlank()
                ? null : new HoverEvent.ShowText(plain(hoverText));

        Style current = Style.EMPTY;
        StringBuilder segment = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) {
                int hexRgb = parseLegacyHex(text, i);
                if (hexRgb >= 0) {
                    flush(root, segment, decorate(current, hover, clickEvent));
                    current = Style.EMPTY.withColor(hexRgb);
                    i += 13;
                    continue;
                }

                ChatFormatting format = ChatFormatting.getByCode(text.charAt(i + 1));
                if (format != null) {
                    flush(root, segment, decorate(current, hover, clickEvent));
                    current = current.applyLegacyFormat(format);
                    i++;
                    continue;
                }
            }
            segment.append(ch);
        }
        flush(root, segment, decorate(current, hover, clickEvent));
    }

    /** Returns RGB for §x§R§R§G§G§B§B, or -1 when the sequence is not valid. */
    private static int parseLegacyHex(String text, int sectionIndex) {
        if (sectionIndex + 13 >= text.length()) return -1;
        char marker = text.charAt(sectionIndex + 1);
        if (marker != 'x' && marker != 'X') return -1;
        int rgb = 0;
        for (int n = 0; n < 6; n++) {
            int markerIndex = sectionIndex + 2 + n * 2;
            if (text.charAt(markerIndex) != '§') return -1;
            int digit = Character.digit(text.charAt(markerIndex + 1), 16);
            if (digit < 0) return -1;
            rgb = (rgb << 4) | digit;
        }
        return rgb;
    }

    private static Style decorate(Style base, HoverEvent hover, ClickEvent click) {
        Style style = base == null ? Style.EMPTY : base;
        if (hover != null) style = style.withHoverEvent(hover);
        if (click != null) style = style.withClickEvent(click);
        return style;
    }

    private static void flush(MutableComponent root, StringBuilder segment, Style style) {
        if (segment.length() == 0) return;
        root.append(Component.literal(segment.toString()).setStyle(style));
        segment.setLength(0);
    }

    private static String normalizeClickUrl(String url) {
        String value = String.valueOf(url == null ? "" : url);
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? value : "https://" + value;
    }

    private static String[] splitUrlTrailing(String raw) {
        String url = raw == null ? "" : raw;
        StringBuilder trailing = new StringBuilder();
        while (!url.isEmpty()) {
            char ch = url.charAt(url.length() - 1);
            if (ch == '.' || ch == ',' || ch == '!' || ch == '?' || ch == ';' || ch == ':'
                    || ch == ')' || ch == ']' || ch == '}') {
                trailing.insert(0, ch);
                url = url.substring(0, url.length() - 1);
            } else break;
        }
        return new String[]{url, trailing.toString()};
    }
}
