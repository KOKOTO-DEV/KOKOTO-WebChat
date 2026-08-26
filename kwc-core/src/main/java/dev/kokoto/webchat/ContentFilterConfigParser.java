package dev.kokoto.webchat;

import java.util.*;

/** Converts loader-neutral YAML map-list data into content-filter rule objects. */
public final class ContentFilterConfigParser {
    private ContentFilterConfigParser() {}

    public static List<ContentFilterRule> parse(List<? extends Map<?, ?>> maps) {
        ArrayList<ContentFilterRule> out = new ArrayList<>();
        if (maps == null) return out;
        int generated = 1;
        for (Map<?,?> raw : maps) {
            if (raw == null) continue;
            ContentFilterRule r = new ContentFilterRule();
            r.id = text(raw.get("id"));
            if (r.id.isBlank()) r.id = "rule-" + generated;
            generated++;
            r.enabled = bool(raw.get("enabled"), true);
            r.action = textOr(raw.get("action"), "block");
            r.words = strings(raw.get("words"));
            r.replacements = strings(raw.get("replacements"));
            r.replacementMode = textOr(raw.get("replacement-mode"), textOr(raw.get("replacementMode"), "first"));
            r.mappings = stringMap(raw.get("mappings"));
            r.normalize();
            if (!r.words.isEmpty()) out.add(r);
        }
        return out;
    }

    public static List<Map<String,Object>> toMaps(List<ContentFilterRule> rules) {
        ArrayList<Map<String,Object>> out = new ArrayList<>();
        if (rules == null) return out;
        for (ContentFilterRule input : rules) {
            if (input == null) continue;
            ContentFilterRule r = input.copy();
            r.normalize();
            if (r.words.isEmpty()) continue;
            LinkedHashMap<String,Object> m = new LinkedHashMap<>();
            m.put("id", r.id);
            m.put("enabled", r.enabled);
            m.put("action", r.action);
            m.put("words", new ArrayList<>(r.words));
            if (!r.replacements.isEmpty()) m.put("replacements", new ArrayList<>(r.replacements));
            if ("replace".equals(r.action)) m.put("replacement-mode", r.replacementMode);
            if (!r.mappings.isEmpty()) m.put("mappings", new LinkedHashMap<>(r.mappings));
            out.add(m);
        }
        return out;
    }

    private static String text(Object value) { return String.valueOf(value == null ? "" : value).trim(); }
    private static String textOr(Object value, String def) { String v = text(value); return v.isBlank() ? def : v; }
    private static boolean bool(Object value, boolean def) {
        if (value instanceof Boolean b) return b;
        if (value == null) return def;
        String s = String.valueOf(value).trim();
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1") || s.equalsIgnoreCase("on")) return true;
        if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equals("0") || s.equalsIgnoreCase("off")) return false;
        return def;
    }
    private static List<String> strings(Object value) {
        ArrayList<String> out = new ArrayList<>();
        if (value instanceof Collection<?> c) {
            for (Object o : c) { String v = text(o); if (!v.isBlank()) out.add(v); }
        } else {
            String v = text(value); if (!v.isBlank()) out.add(v);
        }
        return out;
    }
    private static Map<String,String> stringMap(Object value) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        if (value instanceof Map<?,?> map) {
            for (Map.Entry<?,?> e : map.entrySet()) {
                String k = text(e.getKey());
                if (!k.isBlank()) out.put(k, String.valueOf(e.getValue() == null ? "" : e.getValue()));
            }
        }
        return out;
    }
}
