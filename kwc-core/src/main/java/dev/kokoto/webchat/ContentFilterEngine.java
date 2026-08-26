package dev.kokoto.webchat;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loader-neutral, Unicode-aware content filter.
 *
 * Registered KWC emoji tokens are hard boundaries. A string that only looks like
 * an emoji token is ordinary text and therefore cannot bypass compact/interleave
 * matching. Matching operates on Unicode letters/marks/numbers instead of a
 * Korean/ASCII-only allow list.
 */
public final class ContentFilterEngine {
    public enum Scope { PUBLIC, GROUP, DM }

    private static final Pattern TOKEN = Pattern.compile(":([^:\\r\\n]{1,240}):");

    public ContentFilterResult filter(String input, Scope scope, ConfigValues config, Set<String> registeredEmojiAliases) {
        return filterInternal(input, scope, config, registeredEmojiAliases, false);
    }

    /**
     * Evaluates the configured rules without requiring the live filter/scope toggle
     * to be enabled. This is used only by the Admin no-send test endpoint so an
     * administrator can validate rules before enabling them for real traffic.
     */
    public ContentFilterResult test(String input, Scope scope, ConfigValues config, Set<String> registeredEmojiAliases) {
        return filterInternal(input, scope, config, registeredEmojiAliases, true);
    }

    private ContentFilterResult filterInternal(String input, Scope scope, ConfigValues config,
                                               Set<String> registeredEmojiAliases, boolean ignoreActivation) {
        String message = String.valueOf(input == null ? "" : input);
        if (config == null || (!ignoreActivation && (!config.contentFilterEnabled || !scopeEnabled(scope, config)))) {
            return ContentFilterResult.unchanged(message);
        }
        ArrayList<ContentFilterRule> rules = new ArrayList<>();
        if (config.contentFilterRules != null) rules.addAll(config.contentFilterRules);
        if (config.contentFilterWordListRules != null) rules.addAll(config.contentFilterWordListRules);
        if (rules.isEmpty() || message.isBlank()) return ContentFilterResult.unchanged(message);

        Set<String> emojiAliases = registeredEmojiAliases == null ? Set.of() : registeredEmojiAliases;
        List<Range> textRanges = unprotectedTextRanges(message, emojiAliases);
        ArrayList<Hit> hits = new ArrayList<>();
        int ruleOrder = 0;
        for (ContentFilterRule source : rules) {
            if (source == null || !source.enabled) { ruleOrder++; continue; }
            ContentFilterRule rule = source.copy();
            rule.normalize();
            if (rule.words.isEmpty()) { ruleOrder++; continue; }
            for (Range range : textRanges) {
                String segment = message.substring(range.start, range.end);
                for (String word : rule.words) {
                    hits.addAll(findHits(segment, range.start, word, rule, ruleOrder, config));
                }
            }
            ruleOrder++;
        }
        if (hits.isEmpty()) return ContentFilterResult.unchanged(message);

        hits.sort(Comparator.comparingInt((Hit h) -> h.start)
                // At the same start position, an exact/compact match is more
                // authoritative than an anti-evasion interleave span. Otherwise
                // a broad interleave hit can consume a short exact mapping such
                // as "ㅅㅂ" before a later word beginning with the same character.
                .thenComparingInt(h -> modeRank(h.mode))
                .thenComparingInt(h -> -(h.end - h.start))
                .thenComparingInt(h -> h.ruleOrder));

        // Resolve overlapping matches first. Custom config rules are added before
        // bulk word-list rules, so an exact same-span custom rule takes priority
        // over the list rule. A non-overlapping block match still blocks the whole
        // message, preserving block's message-level semantics.
        ArrayList<Hit> selected = new ArrayList<>();
        int occupiedUntil = -1;
        for (Hit hit : hits) {
            if (hit.start < occupiedUntil) continue;
            selected.add(hit);
            occupiedUntil = hit.end;
        }
        if (selected.isEmpty()) return ContentFilterResult.unchanged(message);

        for (Hit h : selected) {
            if ("block".equals(h.rule.action)) {
                return new ContentFilterResult(true, false, message, h.word,
                        safeSubstring(message, h.start, h.end), h.rule.id, h.mode);
            }
        }

        StringBuilder out = new StringBuilder(message);
        for (int i = selected.size() - 1; i >= 0; i--) {
            Hit h = selected.get(i);
            String replacement = replacementFor(h, config);
            out.replace(h.start, h.end, replacement);
        }
        Hit first = selected.get(0);
        String transformed = out.toString();
        return new ContentFilterResult(false, !transformed.equals(message), transformed, first.word,
                safeSubstring(message, first.start, first.end), first.rule.id, first.mode);
    }

    private boolean scopeEnabled(Scope scope, ConfigValues c) {
        return switch (scope) {
            case PUBLIC -> c.contentFilterPublic;
            case GROUP -> c.contentFilterGroup;
            case DM -> c.contentFilterDm;
        };
    }

    private List<Range> unprotectedTextRanges(String message, Set<String> aliases) {
        if (aliases.isEmpty()) return List.of(new Range(0, message.length()));
        ArrayList<Range> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(message);
        int cursor = 0;
        while (matcher.find()) {
            if (!isRegisteredEmoji(matcher.group(1), aliases)) continue;
            if (matcher.start() > cursor) out.add(new Range(cursor, matcher.start()));
            cursor = matcher.end();
        }
        if (cursor < message.length()) out.add(new Range(cursor, message.length()));
        if (out.isEmpty() && cursor == 0) out.add(new Range(0, message.length()));
        return out;
    }

    private boolean isRegisteredEmoji(String token, Set<String> aliases) {
        String raw = normalizeAlias(token);
        return aliases.contains(raw) || aliases.contains(raw.toLowerCase(Locale.ROOT));
    }

    private List<Hit> findHits(String segment, int absoluteBase, String word, ContentFilterRule rule,
                               int ruleOrder, ConfigValues c) {
        ArrayList<Hit> out = new ArrayList<>();
        boolean jamoOnlyWord = isHangulJamoOnlyWord(word);
        boolean hangulSyllableWord = containsHangulSyllable(word);
        // Korean word rules must compare complete syllables as complete syllables.
        // If a rule such as "시발" and a message such as "신발" are both
        // decomposed with NFKD, the extra jongseong ᆫ can otherwise be treated as
        // an interleave/evasion character and produce a false positive. Jamo-only
        // shorthand rules still keep their literal-jamo word side, while the
        // message side remains syllable-protected.
        boolean preserveMessageHangul = jamoOnlyWord || hangulSyllableWord;
        List<Unit> normalMessage = units(segment, c, false, preserveMessageHangul);
        List<Unit> normalWord = units(word, c, false, hangulSyllableWord);
        collectContiguous(out, normalMessage, normalWord, absoluteBase, word, rule, ruleOrder, "literal");

        if (c.contentFilterCompactMatch) {
            List<Unit> compactMessage = units(segment, c, true, preserveMessageHangul);
            List<Unit> compactWord = units(word, c, true, hangulSyllableWord);
            collectContiguous(out, compactMessage, compactWord, absoluteBase, word, rule, ruleOrder, "compact");
        }
        if (c.contentFilterInterleaveMatch) {
            List<Unit> compactMessage = units(segment, c, true, preserveMessageHangul);
            List<Unit> compactWord = units(word, c, true, hangulSyllableWord);
            collectInterleave(out, compactMessage, compactWord, absoluteBase, word, rule, ruleOrder, c);
        }
        dedupeHits(out);
        return out;
    }

    private void collectContiguous(List<Hit> out, List<Unit> message, List<Unit> word, int base,
                                   String canonicalWord, ContentFilterRule rule, int ruleOrder, String mode) {
        if (message.isEmpty() || word.isEmpty() || word.size() > message.size()) return;
        for (int i = 0; i + word.size() <= message.size(); i++) {
            boolean ok = true;
            for (int j = 0; j < word.size(); j++) {
                if (message.get(i + j).cp != word.get(j).cp) { ok = false; break; }
            }
            if (!ok) continue;
            Unit first = message.get(i), last = message.get(i + word.size() - 1);
            out.add(new Hit(base + first.start, base + last.end, canonicalWord, rule, ruleOrder, mode));
        }
    }

    private void collectInterleave(List<Hit> out, List<Unit> message, List<Unit> word, int base,
                                   String canonicalWord, ContentFilterRule rule, int ruleOrder, ConfigValues c) {
        if (message.isEmpty() || word.isEmpty() || word.size() > message.size()) return;
        int maxGap = Math.max(0, c.contentFilterInterleaveMaxGap);
        boolean unlimited = c.contentFilterInterleaveUnlimitedGap;
        for (int start = 0; start < message.size(); start++) {
            if (message.get(start).cp != word.get(0).cp) continue;
            int pos = start;
            boolean ok = true;
            for (int wi = 1; wi < word.size(); wi++) {
                int limit = unlimited ? message.size() - 1 : Math.min(message.size() - 1, pos + maxGap + 1);
                int found = -1;
                for (int mi = pos + 1; mi <= limit; mi++) {
                    if (message.get(mi).cp == word.get(wi).cp) { found = mi; break; }
                }
                if (found < 0) { ok = false; break; }
                pos = found;
            }
            if (!ok) continue;
            // A contiguous result is already represented more precisely as literal/compact.
            if (pos - start + 1 == word.size()) continue;
            Unit first = message.get(start), last = message.get(pos);
            out.add(new Hit(base + first.start, base + last.end, canonicalWord, rule, ruleOrder, "interleave"));
        }
    }

    private List<Unit> units(String input, ConfigValues c, boolean compact, boolean preserveHangulSyllables) {
        String source = String.valueOf(input == null ? "" : input);
        ArrayList<Unit> out = new ArrayList<>();
        for (int offset = 0; offset < source.length();) {
            // Jamo-only shorthand rules must not borrow choseong from a decomposed
            // Hangul syllable. Browsers/IME/plugins may deliver the same visible
            // word either as NFC (시발) or NFD (시발). The old protection only
            // recognized the NFC U+AC00..D7A3 form, so the NFD form could still
            // satisfy a shorthand such as ㅅㅂ through interleave matching.
            // Collapse only sequences that NFC-normalize to one real Hangul
            // syllable and keep the original UTF-16 span for replacement offsets.
            if (preserveHangulSyllables) {
                Unit syllable = decomposedHangulSyllableUnit(source, offset);
                if (syllable != null) {
                    if (!compact || isCompactCodePoint(syllable.cp)) out.add(syllable);
                    offset = syllable.end;
                    continue;
                }
            }

            int cp = source.codePointAt(offset);
            int next = offset + Character.charCount(cp);
            String piece = new String(Character.toChars(cp));
            if (c.contentFilterUnicodeNormalization && !(preserveHangulSyllables && isPrecomposedHangulSyllable(cp))) {
                try { piece = Normalizer.normalize(piece, Normalizer.Form.NFKD); }
                catch (Throwable ignored) {}
            }
            piece = piece.toLowerCase(Locale.ROOT);
            for (int n = 0; n < piece.length();) {
                int normalizedCp = piece.codePointAt(n);
                n += Character.charCount(normalizedCp);
                if (isIgnorableFormat(normalizedCp)) continue;
                if (compact && !isCompactCodePoint(normalizedCp)) continue;
                if (!compact && Character.isISOControl(normalizedCp) && !Character.isWhitespace(normalizedCp)) continue;
                out.add(new Unit(normalizedCp, offset, next));
            }
            offset = next;
        }
        if (c.contentFilterCollapseRepeats) return collapse(out, Math.max(1, c.contentFilterRepeatLimit));
        return out;
    }

    private Unit decomposedHangulSyllableUnit(String source, int offset) {
        if (source == null || offset < 0 || offset >= source.length()) return null;
        int first = source.codePointAt(offset);
        if (!isHangulJamo(first)) return null;

        int secondEnd = offset + Character.charCount(first);
        if (secondEnd >= source.length()) return null;
        int second = source.codePointAt(secondEnd);
        int thirdEnd = secondEnd + Character.charCount(second);

        // Try L+V+T first, then L+V. We intentionally rely on NFC rather than a
        // hand-written modern-jamo table so only sequences Java itself recognizes
        // as one Hangul syllable are protected. Standalone jamo such as ᄉᄇ keep
        // their literal shorthand behavior because they do not compose.
        if (thirdEnd < source.length()) {
            int third = source.codePointAt(thirdEnd);
            int end = thirdEnd + Character.charCount(third);
            Unit unit = nfcHangulSyllableUnit(source, offset, end);
            if (unit != null) return unit;
        }
        return nfcHangulSyllableUnit(source, offset, thirdEnd);
    }

    private Unit nfcHangulSyllableUnit(String source, int start, int end) {
        if (end <= start || end > source.length()) return null;
        try {
            String nfc = Normalizer.normalize(source.substring(start, end), Normalizer.Form.NFC);
            if (nfc.codePointCount(0, nfc.length()) != 1) return null;
            int cp = nfc.codePointAt(0);
            return isPrecomposedHangulSyllable(cp) ? new Unit(cp, start, end) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }


    private boolean containsHangulSyllable(String value) {
        String raw = String.valueOf(value == null ? "" : value);
        for (int offset = 0; offset < raw.length();) {
            int cp = raw.codePointAt(offset);
            if (isPrecomposedHangulSyllable(cp)) return true;
            Unit decomposed = decomposedHangulSyllableUnit(raw, offset);
            if (decomposed != null) return true;
            offset += Character.charCount(cp);
        }
        return false;
    }

    private boolean isHangulJamoOnlyWord(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (raw.isEmpty()) return false;
        boolean sawJamo = false;
        for (int offset = 0; offset < raw.length();) {
            int cp = raw.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isWhitespace(cp)) continue;
            if (!isHangulJamo(cp)) return false;
            sawJamo = true;
        }
        return sawJamo;
    }

    private boolean isPrecomposedHangulSyllable(int cp) {
        return cp >= 0xAC00 && cp <= 0xD7A3;
    }

    private boolean isHangulJamo(int cp) {
        return (cp >= 0x1100 && cp <= 0x11FF)
                || (cp >= 0x3130 && cp <= 0x318F)
                || (cp >= 0xA960 && cp <= 0xA97F)
                || (cp >= 0xD7B0 && cp <= 0xD7FF);
    }

    private boolean isCompactCodePoint(int cp) {
        int type = Character.getType(cp);
        return Character.isLetterOrDigit(cp)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private boolean isIgnorableFormat(int cp) {
        int type = Character.getType(cp);
        // Format controls outside an actual registered emoji token are not a bypass boundary.
        return type == Character.FORMAT || cp == 0xFEFF;
    }

    private List<Unit> collapse(List<Unit> input, int limit) {
        if (input.isEmpty()) return input;
        ArrayList<Unit> out = new ArrayList<>(input.size());
        int last = Integer.MIN_VALUE, count = 0;
        for (Unit unit : input) {
            if (unit.cp == last) count++; else { last = unit.cp; count = 1; }
            if (count <= limit) out.add(unit);
        }
        return out;
    }

    private String replacementFor(Hit hit, ConfigValues config) {
        ContentFilterRule rule = hit.rule;
        if ("mask".equals(rule.action)) return String.valueOf(config.contentFilterMaskText == null ? "***" : config.contentFilterMaskText);
        if (!"replace".equals(rule.action)) return safeSubstring("", 0, 0);

        String mapped = mappedReplacement(rule, hit.word, config);
        if (mapped != null) return mapped;
        if (rule.replacements == null || rule.replacements.isEmpty()) return String.valueOf(config.contentFilterMaskText == null ? "***" : config.contentFilterMaskText);
        if ("random".equals(rule.replacementMode) && rule.replacements.size() > 1) {
            return rule.replacements.get(ThreadLocalRandom.current().nextInt(rule.replacements.size()));
        }
        return rule.replacements.get(0);
    }

    private String mappedReplacement(ContentFilterRule rule, String word, ConfigValues config) {
        if (rule.mappings == null || rule.mappings.isEmpty()) return null;
        String normalizedWord = comparisonKey(word, config);
        for (Map.Entry<String,String> e : rule.mappings.entrySet()) {
            if (comparisonKey(e.getKey(), config).equals(normalizedWord)) return String.valueOf(e.getValue() == null ? "" : e.getValue());
        }
        return null;
    }

    private String comparisonKey(String value, ConfigValues config) {
        String raw = String.valueOf(value == null ? "" : value);
        if (config.contentFilterUnicodeNormalization) {
            try { raw = Normalizer.normalize(raw, Normalizer.Form.NFKD); } catch (Throwable ignored) {}
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private void dedupeHits(List<Hit> hits) {
        LinkedHashMap<String,Hit> unique = new LinkedHashMap<>();
        for (Hit h : hits) {
            String key = h.start + ":" + h.end + ":" + h.rule.id;
            Hit old = unique.get(key);
            if (old == null || modeRank(h.mode) < modeRank(old.mode)) unique.put(key, h);
        }
        hits.clear();
        hits.addAll(unique.values());
    }

    private int modeRank(String mode) {
        if ("literal".equals(mode)) return 0;
        if ("compact".equals(mode)) return 1;
        return 2;
    }

    public static String normalizeAlias(String token) {
        String raw = String.valueOf(token == null ? "" : token).trim();
        try { raw = Normalizer.normalize(raw, Normalizer.Form.NFC); } catch (Throwable ignored) {}
        if (raw.startsWith(":") && raw.endsWith(":") && raw.length() >= 2) raw = raw.substring(1, raw.length() - 1);
        return raw.trim();
    }

    private String safeSubstring(String s, int start, int end) {
        String value = String.valueOf(s == null ? "" : s);
        int a = Math.max(0, Math.min(value.length(), start));
        int b = Math.max(a, Math.min(value.length(), end));
        return value.substring(a, b);
    }

    private record Unit(int cp, int start, int end) {}
    private record Range(int start, int end) {}
    private static final class Hit {
        final int start, end;
        final String word;
        final ContentFilterRule rule;
        final int ruleOrder;
        final String mode;
        Hit(int start, int end, String word, ContentFilterRule rule, int ruleOrder, String mode) {
            this.start = start; this.end = end; this.word = word; this.rule = rule; this.ruleOrder = ruleOrder; this.mode = mode;
        }
    }
}
