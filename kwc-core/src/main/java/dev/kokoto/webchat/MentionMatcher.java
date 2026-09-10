package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 공개/개인 알림에서 @mention과 team mention을 사용자 식별자 기준으로 판정하는 matcher다.
 * Matcher resolving @mentions and team mentions for public/private notification decisions.
 *
 * 단순 substring 매칭으로 다른 이름 일부가 오탐되지 않도록 token boundary와 display/username 정규화 규칙을 유지한다.
 * Preserve token-boundary and display/username normalization rules so simple substring matches do not create false positives.
 */
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves explicit @mentions using longest registered-name prefix matching. */
public final class MentionMatcher {
    public record Candidate(String userUuid, String name) {}

    private MentionMatcher() {}

    /**
     * At each '@' position, only the longest matching registered name wins.
     * Multiple users with that exact normalized name all match, which allows a
     * shared display name to act as a team mention. No trailing boundary is
     * required after the name.
     */
    public static Set<String> resolve(String rawText, Collection<Candidate> candidates) {
        String text = normalize(rawText, false);
        if (text.isBlank() || text.indexOf('@') < 0 || candidates == null || candidates.isEmpty()) return Set.of();

        Map<String, Set<String>> byName = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate == null) continue;
            String uuid = String.valueOf(candidate.userUuid() == null ? "" : candidate.userUuid()).trim().toLowerCase(Locale.ROOT);
            String name = normalize(candidate.name(), true);
            if (uuid.isBlank() || name.isBlank()) continue;
            byName.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(uuid);
        }
        if (byName.isEmpty()) return Set.of();

        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (int at = text.indexOf('@'); at >= 0 && at + 1 < text.length(); at = text.indexOf('@', at + 1)) {
            String suffix = text.substring(at + 1);
            String best = "";
            for (String name : byName.keySet()) {
                if (name.length() <= best.length()) continue;
                if (suffix.startsWith(name)) best = name;
            }
            if (!best.isBlank()) matched.addAll(byName.getOrDefault(best, Set.of()));
        }
        return matched.isEmpty() ? Set.of() : Set.copyOf(matched);
    }

    static String normalizeCandidate(String value) {
        return normalize(value, true);
    }

    private static String normalize(String raw, boolean stripFormatting) {
        String value = String.valueOf(raw == null ? "" : raw);
        if (stripFormatting) {
            value = value.replaceAll("(?i)[§&]x(?:[§&][0-9a-f]){6}", "");
            value = value.replaceAll("(?i)&#[0-9a-f]{6}", "");
            value = value.replaceAll("(?i)[§&][0-9a-fk-or]", "");
            value = value.replaceAll("<[^>]+>", "");
            value = value.replace("§", "");
        }
        value = Normalizer.normalize(value, Normalizer.Form.NFKC);
        value = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();
        return value.toLowerCase(Locale.ROOT);
    }
}
