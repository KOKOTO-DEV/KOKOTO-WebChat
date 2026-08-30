package dev.kokoto.webchat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves a game-command group target while preserving the remaining message text. */
public final class GroupCommandTargetResolver {
    private GroupCommandTargetResolver() {}

    public static Result resolve(List<GroupRoom> rooms, String rawInput) {
        String raw = clean(rawInput);
        if (raw.isBlank() || rooms == null || rooms.isEmpty()) return null;

        Result quoted = resolveQuoted(rooms, raw);
        if (quoted != null) return quoted;

        int firstSpace = firstWhitespace(raw);
        String firstToken = firstSpace < 0 ? raw : raw.substring(0, firstSpace);
        String afterFirst = firstSpace < 0 ? "" : raw.substring(firstSpace).trim();

        // IDs are intentionally token-based. They never contain whitespace and remain
        // the unambiguous fallback when room names overlap with command text.
        for (GroupRoom room : rooms) {
            if (!joined(room)) continue;
            if (clean(room.id).equalsIgnoreCase(firstToken)) return new Result(room, afterFirst);
        }
        for (GroupRoom room : rooms) {
            if (!joined(room)) continue;
            if (shortId(room.id).equalsIgnoreCase(firstToken)) return new Result(room, afterFirst);
        }

        // Room names may contain whitespace. Match every joined room against the command
        // prefix and select the longest matching name, so "Test Group" wins over "Test".
        GroupRoom bestRoom = null;
        int bestEnd = -1;
        int bestNameLength = -1;
        for (GroupRoom room : rooms) {
            if (!joined(room)) continue;
            String name = clean(room.name);
            if (name.isBlank()) continue;
            Matcher matcher = roomNamePrefixPattern(name).matcher(raw);
            if (!matcher.find()) continue;
            int logicalLength = name.length();
            if (logicalLength > bestNameLength) {
                bestRoom = room;
                bestEnd = matcher.end();
                bestNameLength = logicalLength;
            }
        }
        if (bestRoom == null) return null;
        return new Result(bestRoom, raw.substring(bestEnd).trim());
    }

    private static Result resolveQuoted(List<GroupRoom> rooms, String raw) {
        if (raw.length() < 2) return null;
        char quote = raw.charAt(0);
        if (quote != '"' && quote != '\'') return null;
        int end = raw.indexOf(quote, 1);
        if (end <= 1) return null;
        String wanted = normalizeWhitespace(raw.substring(1, end));
        for (GroupRoom room : rooms) {
            if (!joined(room)) continue;
            if (normalizeWhitespace(room.name).equalsIgnoreCase(wanted)) {
                return new Result(room, raw.substring(end + 1).trim());
            }
        }
        return null;
    }

    private static Pattern roomNamePrefixPattern(String roomName) {
        String[] words = normalizeWhitespace(roomName).split(" ");
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) regex.append("\\s+");
            regex.append(Pattern.quote(words[i]));
        }
        regex.append("(?=\\s|$)");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static boolean joined(GroupRoom room) {
        return room != null && room.member;
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isWhitespace(value.charAt(i))) return i;
        return -1;
    }

    private static String normalizeWhitespace(String value) {
        return clean(value).replaceAll("\\s+", " ");
    }

    private static String shortId(String id) {
        String value = clean(id);
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static String clean(String value) {
        return String.valueOf(value == null ? "" : value).trim();
    }

    public static final class Result {
        public final GroupRoom room;
        public final String remainder;

        private Result(GroupRoom room, String remainder) {
            this.room = room;
            this.remainder = String.valueOf(remainder == null ? "" : remainder);
        }
    }
}
