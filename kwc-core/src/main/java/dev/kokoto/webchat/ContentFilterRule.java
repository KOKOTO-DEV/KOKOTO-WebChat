package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 단일 콘텐츠 필터 규칙의 pattern, action, scope 같은 설정을 표현한다.
 * Represents one content-filter rule including pattern, action, and scope.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
import java.util.*;

/** One administrator-managed content-filter rule group. */
public final class ContentFilterRule {
    public String id = "";
    public boolean enabled = true;
    /** block, mask, replace */
    public String action = "block";
    public List<String> words = new ArrayList<>();
    public List<String> replacements = new ArrayList<>();
    /** first or random. mappings, when present, take precedence for matching words. */
    public String replacementMode = "first";
    public Map<String, String> mappings = new LinkedHashMap<>();

    public ContentFilterRule copy() {
        ContentFilterRule r = new ContentFilterRule();
        r.id = id;
        r.enabled = enabled;
        r.action = action;
        r.words = words == null ? new ArrayList<>() : new ArrayList<>(words);
        r.replacements = replacements == null ? new ArrayList<>() : new ArrayList<>(replacements);
        r.replacementMode = replacementMode;
        r.mappings = mappings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(mappings);
        return r;
    }

    public void normalize() {
        id = cleanId(id);
        action = String.valueOf(action == null ? "block" : action).trim().toLowerCase(Locale.ROOT);
        if (!Set.of("block", "mask", "replace").contains(action)) action = "block";
        replacementMode = String.valueOf(replacementMode == null ? "first" : replacementMode).trim().toLowerCase(Locale.ROOT);
        if (!Set.of("first", "random").contains(replacementMode)) replacementMode = "first";
        words = cleanList(words);
        replacements = cleanList(replacements);
        LinkedHashMap<String,String> cleanMappings = new LinkedHashMap<>();
        if (mappings != null) {
            for (Map.Entry<String,String> e : mappings.entrySet()) {
                String k = cleanText(e.getKey());
                String v = String.valueOf(e.getValue() == null ? "" : e.getValue());
                if (!k.isBlank()) cleanMappings.put(k, v);
            }
        }
        mappings = cleanMappings;
        // A mapping key is also a match word even when it was omitted from words.
        LinkedHashSet<String> all = new LinkedHashSet<>(words);
        all.addAll(mappings.keySet());
        words = new ArrayList<>(all);
    }

    private static List<String> cleanList(Collection<String> input) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (input != null) for (String s : input) {
            String v = cleanText(s);
            if (!v.isBlank()) out.add(v);
        }
        return new ArrayList<>(out);
    }

    private static String cleanText(Object value) {
        return String.valueOf(value == null ? "" : value).replace("\r", "").trim();
    }

    public static String normalizedId(String value) {
        return cleanId(value);
    }

    private static String cleanId(String value) {
        String v = cleanText(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        while (v.startsWith("-")) v = v.substring(1);
        while (v.endsWith("-")) v = v.substring(0, v.length() - 1);
        return v.length() > 64 ? v.substring(0, 64) : v;
    }
}
