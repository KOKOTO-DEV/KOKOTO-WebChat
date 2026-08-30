package dev.kokoto.webchat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small comment-preserving editor for KWC's known config.yml paths.
 * It intentionally edits only existing scalar keys and the managed
 * content-filter.rules list, leaving all unrelated comments/order untouched.
 */
public final class ConfigTextEditor {
    private static final Object LOCK = new Object();
    private static final Pattern MAP_LINE = Pattern.compile("^(\\s*)([^#\\s][^:]*?):(?:\\s*(.*))?$");

    private ConfigTextEditor() {}

    public static void setScalar(Path file, String dotPath, Object value) throws IOException {
        LinkedHashMap<String,Object> one = new LinkedHashMap<>();
        one.put(dotPath, value);
        setScalars(file, one);
    }

    public static void setScalars(Path file, Map<String,?> values) throws IOException {
        Objects.requireNonNull(file, "file");
        if (values == null || values.isEmpty()) return;
        synchronized (LOCK) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (Map.Entry<String,?> entry : values.entrySet()) {
                String target = String.valueOf(entry.getKey() == null ? "" : entry.getKey()).trim();
                if (target.isBlank()) throw new IOException("empty config path");
                upsertScalar(lines, target, entry.getValue());
            }
            atomicWrite(file, lines);
        }
    }

    /**
     * Comment-preserving value writer used by configuration migration. Unlike
     * setScalars(), this method also accepts YAML lists/maps. Existing comments
     * immediately before the key are retained while the value block itself is
     * replaced. String scalars are rendered with the same double-quoted style as
     * the bundled KWC config/reference files.
     */
    public static void setValues(Path file, Map<String,?> values) throws IOException {
        Objects.requireNonNull(file, "file");
        if (values == null || values.isEmpty()) return;
        synchronized (LOCK) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (Map.Entry<String,?> entry : values.entrySet()) {
                String target = String.valueOf(entry.getKey() == null ? "" : entry.getKey()).trim();
                if (target.isBlank()) throw new IOException("empty config path");
                upsertValue(lines, target, normalizeYamlValue(entry.getValue()));
            }
            atomicWrite(file, lines);
        }
    }

    /**
     * Returns the physical YAML value block for each requested dot path. Comments
     * preceding/following the setting are deliberately excluded so migration
     * reports can compare semantic settings without turning comment movement into
     * false differences. Line numbers are 1-based and inclusive.
     */
    public static Map<String, ValueBlock> readValueBlocks(Path file, Collection<String> paths) throws IOException {
        Objects.requireNonNull(file, "file");
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (paths != null) {
            for (String path : paths) {
                String target = String.valueOf(path == null ? "" : path).trim();
                if (!target.isBlank()) targets.add(target);
            }
        }
        LinkedHashMap<String, ValueBlock> out = new LinkedHashMap<>();
        if (targets.isEmpty()) return out;
        synchronized (LOCK) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String target : targets) {
                int index = findPathIndex(lines, target);
                if (index < 0) continue;
                Parsed parsed = parse(lines.get(index));
                if (parsed == null) continue;
                String rawValue = stripInlineCommentValue(parsed.valuePart).trim();
                int end = index + 1;
                if (rawValue.isEmpty()) {
                    end = valueDataBlockEnd(lines, index, parsed.indent);
                }
                out.put(target, new ValueBlock(index + 1, Math.max(index + 1, end),
                        new ArrayList<>(lines.subList(index, end))));
            }
        }
        return out;
    }

    private static int valueDataBlockEnd(List<String> lines, int keyLine, int keyIndent) {
        int end = keyLine + 1;
        int sequenceIndent = -1;
        while (end < lines.size()) {
            String line = lines.get(end);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                // Comments/blank lines belong to layout, not to the setting value.
                break;
            }
            int indent = indentOf(line);
            if (sequenceIndent < 0 && trimmed.startsWith("-") && indent >= keyIndent) {
                sequenceIndent = indent;
                end++;
                continue;
            }
            if (sequenceIndent >= 0) {
                if (indent < sequenceIndent) break;
                if (indent == sequenceIndent && !trimmed.startsWith("-")) break;
                end++;
                continue;
            }
            if (indent <= keyIndent) break;
            end++;
        }
        return end;
    }

    public record ValueBlock(int startLine, int endLine, List<String> lines) {}

    /**
     * Returns a copy-ready physical YAML setting block, including only the
     * contiguous comment/blank lines immediately preceding the setting. This is
     * intended for migration reports and diagnostics where structured values
     * must remain readable YAML instead of being collapsed into flow/JSON text.
     */
    public static Map<String, SettingBlock> readSettingBlocks(Path file, Collection<String> paths) throws IOException {
        Objects.requireNonNull(file, "file");
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (paths != null) {
            for (String path : paths) {
                String target = String.valueOf(path == null ? "" : path).trim();
                if (!target.isBlank()) targets.add(target);
            }
        }
        LinkedHashMap<String, SettingBlock> out = new LinkedHashMap<>();
        if (targets.isEmpty()) return out;
        synchronized (LOCK) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String target : targets) {
                int index = findPathIndex(lines, target);
                if (index < 0) continue;
                Parsed parsed = parse(lines.get(index));
                if (parsed == null) continue;

                int start = index;
                while (start > 0) {
                    String previous = lines.get(start - 1);
                    String trimmed = previous.trim();
                    if (trimmed.startsWith("#")) { start--; continue; }
                    if (trimmed.isEmpty() && start - 2 >= 0 && lines.get(start - 2).trim().startsWith("#")) {
                        start--;
                        continue;
                    }
                    break;
                }

                String rawValue = stripInlineCommentValue(parsed.valuePart).trim();
                int end = rawValue.isEmpty() ? valueDataBlockEnd(lines, index, parsed.indent) : index + 1;
                out.put(target, new SettingBlock(start + 1, Math.max(start + 1, end),
                        new ArrayList<>(lines.subList(start, end))));
            }
        }
        return out;
    }

    public record SettingBlock(int startLine, int endLine, List<String> lines) {}

    private static void upsertScalar(List<String> lines, String target, Object value) throws IOException {
        Deque<Node> stack = new ArrayDeque<>();
        for (int i = 0; i < lines.size(); i++) {
            Parsed parsed = parse(lines.get(i));
            if (parsed == null) continue;
            while (!stack.isEmpty() && stack.peekLast().indent >= parsed.indent) stack.removeLast();
            String current = joinPath(stack, parsed.key);
            if (current.equals(target)) {
                String comment = inlineComment(parsed.valuePart);
                String replacement = " ".repeat(parsed.indent) + parsed.rawKey + ": " + yamlScalar(value);
                if (!comment.isBlank()) replacement += " " + comment;
                lines.set(i, replacement);
                return;
            }
            if (parsed.isSection()) stack.addLast(new Node(parsed.indent, parsed.key));
        }

        int dot = target.lastIndexOf('.');
        String parent = dot < 0 ? "" : target.substring(0, dot);
        String leaf = dot < 0 ? target : target.substring(dot + 1);
        if (leaf.isBlank()) throw new IOException("invalid config path: " + target);
        if (parent.isBlank()) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) lines.add("");
            lines.add(leaf + ": " + yamlScalar(value));
            return;
        }

        ensureSection(lines, parent);
        SectionLocation section = findSection(lines, parent);
        if (section == null) throw new IOException("config section not found: " + parent);
        int insertAt = sectionEnd(lines, section.lineIndex, section.indent);
        lines.add(insertAt, " ".repeat(section.indent + 2) + leaf + ": " + yamlScalar(value));
    }

    private static void upsertValue(List<String> lines, String target, Object value) throws IOException {
        int existing = findPathIndex(lines, target);
        if (existing >= 0) {
            Parsed parsed = parse(lines.get(existing));
            if (parsed == null) throw new IOException("config path could not be parsed: " + target);
            // Inline/scalar values occupy exactly one physical line. The previous
            // implementation used valueBlockEnd() even here, which consumed the
            // comments belonging to the following sibling setting during migration.
            // That is why rebuilt config.yml files kept only the first comment in
            // many sections. Only an actually block-valued existing entry may own
            // following indented data lines.
            String existingRawValue = stripInlineCommentValue(parsed.valuePart).trim();
            int end = existingRawValue.isEmpty()
                    ? valueDataBlockEnd(lines, existing, parsed.indent)
                    : existing + 1;
            String inline = isScalarValue(value) ? inlineComment(parsed.valuePart) : "";
            List<String> replacement = renderValueEntry(parsed.indent, parsed.rawKey, value, inline);
            lines.subList(existing, end).clear();
            lines.addAll(existing, replacement);
            return;
        }

        int dot = target.lastIndexOf('.');
        String parent = dot < 0 ? "" : target.substring(0, dot);
        String leaf = dot < 0 ? target : target.substring(dot + 1);
        if (leaf.isBlank()) throw new IOException("invalid config path: " + target);
        if (parent.isBlank()) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) lines.add("");
            lines.addAll(renderValueEntry(0, yamlKey(leaf), value, ""));
            return;
        }
        ensureSection(lines, parent);
        SectionLocation section = findSection(lines, parent);
        if (section == null) throw new IOException("config section not found: " + parent);
        int insertAt = sectionEnd(lines, section.lineIndex, section.indent);
        lines.addAll(insertAt, renderValueEntry(section.indent + 2, yamlKey(leaf), value, ""));
    }

    private static int valueBlockEnd(List<String> lines, int keyLine, int keyIndent) {
        int end = keyLine + 1;
        int sequenceIndent = -1;
        while (end < lines.size()) {
            String line = lines.get(end);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) { end++; continue; }
            int indent = indentOf(line);
            if (sequenceIndent < 0 && trimmed.startsWith("-") && indent >= keyIndent) {
                sequenceIndent = indent;
                end++;
                continue;
            }
            if (sequenceIndent >= 0) {
                if (indent < sequenceIndent) break;
                if (indent == sequenceIndent && !trimmed.startsWith("-")) break;
                end++;
                continue;
            }
            if (indent <= keyIndent) break;
            end++;
        }
        return end;
    }

    private static List<String> renderValueEntry(int indent, String rawKey, Object rawValue, String inlineComment) {
        Object value = normalizeYamlValue(rawValue);
        String prefix = " ".repeat(Math.max(0, indent)) + rawKey + ":";
        ArrayList<String> out = new ArrayList<>();
        if (isScalarValue(value)) {
            String line = prefix + " " + yamlScalar(value);
            if (inlineComment != null && !inlineComment.isBlank()) line += " " + inlineComment.trim();
            out.add(line);
            return out;
        }
        if (value instanceof Map<?,?> map) {
            if (map.isEmpty()) {
                out.add(prefix + " {}");
                return out;
            }
            out.add(prefix);
            renderMap(out, indent + 2, map);
            return out;
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.add(prefix + " []");
                return out;
            }
            out.add(prefix);
            renderList(out, indent + 2, list);
            return out;
        }
        out.add(prefix + " " + yamlScalar(value));
        return out;
    }

    private static void renderMap(List<String> out, int indent, Map<?,?> map) {
        for (Map.Entry<?,?> entry : map.entrySet()) {
            String key = yamlKey(String.valueOf(entry.getKey()));
            Object value = normalizeYamlValue(entry.getValue());
            String prefix = " ".repeat(indent) + key + ":";
            if (isScalarValue(value)) out.add(prefix + " " + yamlScalar(value));
            else if (value instanceof Map<?,?> nested) {
                if (nested.isEmpty()) out.add(prefix + " {}");
                else { out.add(prefix); renderMap(out, indent + 2, nested); }
            } else if (value instanceof List<?> list) {
                if (list.isEmpty()) out.add(prefix + " []");
                else { out.add(prefix); renderList(out, indent + 2, list); }
            }
        }
    }

    private static void renderList(List<String> out, int indent, List<?> list) {
        for (Object raw : list) {
            Object value = normalizeYamlValue(raw);
            String prefix = " ".repeat(indent) + "-";
            if (isScalarValue(value)) out.add(prefix + " " + yamlScalar(value));
            else if (value instanceof Map<?,?> nested) {
                if (nested.isEmpty()) {
                    out.add(prefix + " {}");
                } else {
                    // Prefer the conventional compact YAML sequence-map form:
                    //   - id: "server-2"
                    //     url: "https://..."
                    // instead of a standalone '-' line. This keeps generated config and
                    // migration output aligned with the operator-facing examples.
                    var iterator = nested.entrySet().iterator();
                    Map.Entry<?,?> first = iterator.next();
                    Object firstValue = normalizeYamlValue(first.getValue());
                    if (isScalarValue(firstValue)) {
                        out.add(prefix + " " + yamlKey(String.valueOf(first.getKey())) + ": " + yamlScalar(firstValue));
                        LinkedHashMap<String,Object> rest = new LinkedHashMap<>();
                        iterator.forEachRemaining(entry -> rest.put(String.valueOf(entry.getKey()), normalizeYamlValue(entry.getValue())));
                        if (!rest.isEmpty()) renderMap(out, indent + 2, rest);
                    } else {
                        out.add(prefix);
                        renderMap(out, indent + 2, nested);
                    }
                }
            } else if (value instanceof List<?> nested) {
                if (nested.isEmpty()) out.add(prefix + " []");
                else { out.add(prefix); renderList(out, indent + 2, nested); }
            }
        }
    }

    private static boolean isScalarValue(Object value) {
        return !(value instanceof Map<?,?>) && !(value instanceof List<?>);
    }

    private static Object normalizeYamlValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof Map<?,?> map) {
            LinkedHashMap<String,Object> out = new LinkedHashMap<>();
            for (Map.Entry<?,?> entry : map.entrySet()) out.put(String.valueOf(entry.getKey()), normalizeYamlValue(entry.getValue()));
            return out;
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> out = new ArrayList<>();
            for (Object item : collection) out.add(normalizeYamlValue(item));
            return out;
        }
        // Bukkit ConfigurationSection is intentionally handled reflectively so
        // kwc-core remains loader-neutral and has no Bukkit compile dependency.
        try {
            var getKeys = value.getClass().getMethod("getKeys", boolean.class);
            var get = value.getClass().getMethod("get", String.class);
            Object keys = getKeys.invoke(value, false);
            if (keys instanceof Collection<?> collection) {
                LinkedHashMap<String,Object> out = new LinkedHashMap<>();
                for (Object key : collection) {
                    String name = String.valueOf(key);
                    out.put(name, normalizeYamlValue(get.invoke(value, name)));
                }
                return out;
            }
        } catch (ReflectiveOperationException ignored) {}
        return String.valueOf(value);
    }

    private static String yamlKey(String key) {
        String s = String.valueOf(key == null ? "" : key);
        return s.matches("[A-Za-z0-9_.-]+") ? s : yamlScalar(s);
    }

    private static void ensureSection(List<String> lines, String path) throws IOException {
        String normalized = String.valueOf(path == null ? "" : path).trim();
        if (normalized.isBlank() || findSection(lines, normalized) != null) return;
        int dot = normalized.lastIndexOf('.');
        String parent = dot < 0 ? "" : normalized.substring(0, dot);
        String leaf = dot < 0 ? normalized : normalized.substring(dot + 1);
        if (leaf.isBlank()) throw new IOException("invalid config section: " + normalized);
        if (parent.isBlank()) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) lines.add("");
            lines.add(leaf + ":");
            return;
        }
        ensureSection(lines, parent);
        SectionLocation parentSection = findSection(lines, parent);
        if (parentSection == null) throw new IOException("config section not found: " + parent);
        int insertAt = sectionEnd(lines, parentSection.lineIndex, parentSection.indent);
        lines.add(insertAt, " ".repeat(parentSection.indent + 2) + leaf + ":");
    }

    private static SectionLocation findSection(List<String> lines, String target) {
        Deque<Node> stack = new ArrayDeque<>();
        for (int i = 0; i < lines.size(); i++) {
            Parsed parsed = parse(lines.get(i));
            if (parsed == null) continue;
            while (!stack.isEmpty() && stack.peekLast().indent >= parsed.indent) stack.removeLast();
            String current = joinPath(stack, parsed.key);
            if (current.equals(target) && parsed.isSection()) return new SectionLocation(i, parsed.indent);
            if (parsed.isSection()) stack.addLast(new Node(parsed.indent, parsed.key));
        }
        return null;
    }

    private static int sectionEnd(List<String> lines, int sectionLine, int sectionIndent) {
        int end = sectionLine + 1;
        while (end < lines.size()) {
            String line = lines.get(end);
            if (line.trim().isEmpty() || line.trim().startsWith("#")) { end++; continue; }
            if (indentOf(line) <= sectionIndent) break;
            end++;
        }
        return end;
    }

    /** Reads scalar values from the actual config.yml text using the same dot-path rules as the writer. */
    public static Map<String,String> readScalars(Path file, Collection<String> paths) throws IOException {
        Objects.requireNonNull(file, "file");
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (paths != null) for (String path : paths) {
            String value = String.valueOf(path == null ? "" : path).trim();
            if (!value.isBlank()) targets.add(value);
        }
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        if (targets.isEmpty()) return out;
        synchronized (LOCK) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Deque<Node> stack = new ArrayDeque<>();
            for (String line : lines) {
                Parsed parsed = parse(line);
                if (parsed == null) continue;
                while (!stack.isEmpty() && stack.peekLast().indent >= parsed.indent) stack.removeLast();
                String current = joinPath(stack, parsed.key);
                if (targets.contains(current) && !parsed.isSection()) {
                    out.put(current, decodeYamlScalar(stripInlineCommentValue(parsed.valuePart)));
                }
                if (parsed.isSection()) stack.addLast(new Node(parsed.indent, parsed.key));
            }
        }
        return out;
    }

    /**
     * Reads the managed content-filter.rules block directly from config.yml.
     *
     * Admin/filter runtime synchronization intentionally uses this loader-neutral
     * reader instead of Bukkit/SnakeYAML-specific getMapList implementations so
     * every platform sees the exact same rule list written by this class.
     */
    public static List<ContentFilterRule> readContentFilterRules(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        synchronized (LOCK) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Deque<Node> stack = new ArrayDeque<>();
            int ruleLine = -1;
            int ruleIndent = -1;
            String ruleValue = "";
            for (int i = 0; i < lines.size(); i++) {
                Parsed parsed = parse(lines.get(i));
                if (parsed == null) continue;
                while (!stack.isEmpty() && stack.peekLast().indent >= parsed.indent) stack.removeLast();
                String currentPath = joinPath(stack, parsed.key);
                if (currentPath.equals("content-filter.rules")) {
                    ruleLine = i;
                    ruleIndent = parsed.indent;
                    ruleValue = stripInlineCommentValue(parsed.valuePart).trim();
                    break;
                }
                if (parsed.isSection()) stack.addLast(new Node(parsed.indent, parsed.key));
            }
            if (ruleLine < 0 || "[]".equals(ruleValue)) return new ArrayList<>();

            // YAML emitters differ on sequence indentation. Bukkit/SnakeYAML can
            // emit sequence items at the mapping key indentation, while the
            // comment-preserving writer below uses an indented sequence. Discover the
            // actual first item indentation and
            // accept both layouts, including indentless nested words/replacements lists.
            ArrayList<ContentFilterRule> out = new ArrayList<>();
            ContentFilterRule current = null;
            int itemIndent = -1;
            String nested = "";
            int nestedFieldIndent = -1;

            for (int i = ruleLine + 1; i < lines.size(); i++) {
                String rawLine = lines.get(i);
                String trimmed = rawLine.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int indent = indentOf(rawLine);

                if (itemIndent < 0) {
                    if (trimmed.startsWith("-") && indent >= ruleIndent) {
                        itemIndent = indent;
                        current = new ContentFilterRule();
                        String rest = trimmed.substring(1).trim();
                        if (!rest.isEmpty()) applyRuleField(current, rest, "");
                        continue;
                    }
                    if (indent <= ruleIndent) break;
                    continue;
                }

                if (indent < itemIndent) break;
                if (indent == itemIndent) {
                    if (!trimmed.startsWith("-")) break;
                    addRule(out, current);
                    current = new ContentFilterRule();
                    nested = "";
                    nestedFieldIndent = -1;
                    String rest = trimmed.substring(1).trim();
                    if (!rest.isEmpty()) applyRuleField(current, rest, "");
                    continue;
                }
                if (current == null) continue;

                // Lists may be emitted with their '-' at the same indentation as the
                // words:/replacements: field. Because itemIndent is shallower, this is
                // still unambiguous once nested has been selected.
                if (("words".equals(nested) || "replacements".equals(nested)) && trimmed.startsWith("-") && indent > itemIndent) {
                    String value = decodeYamlScalar(stripInlineCommentValue(trimmed.substring(1)).trim());
                    if (!value.isBlank()) {
                        if ("words".equals(nested)) current.words.add(value);
                        else current.replacements.add(value);
                    }
                    continue;
                }

                int colon = yamlColonIndex(trimmed);
                if (colon < 0) continue;
                String key = decodeYamlScalar(trimmed.substring(0, colon).trim());
                String value = stripInlineCommentValue(trimmed.substring(colon + 1)).trim();

                if ("mappings".equals(nested) && nestedFieldIndent >= 0 && indent > nestedFieldIndent) {
                    // Mapping keys are user-controlled target words and may legitimately
                    // be named "action", "words", "enabled", etc. Indentation, not the
                    // key spelling, distinguishes a mapping entry from the next rule field.
                    String mappingValue = decodeYamlScalar(value);
                    if (!key.isBlank()) current.mappings.put(key, mappingValue);
                    continue;
                }

                if (!isRuleFieldKey(key)) continue;
                nested = "";
                nestedFieldIndent = -1;
                if (("words".equals(key) || "replacements".equals(key) || "mappings".equals(key)) && value.isEmpty()) {
                    nested = key;
                    nestedFieldIndent = indent;
                } else {
                    applyRuleField(current, key + ":" + value, "");
                }
            }
            addRule(out, current);
            int generated = 1;
            for (ContentFilterRule rule : out) {
                if (rule.id == null || rule.id.isBlank()) rule.id = "rule-" + generated;
                generated++;
            }
            return out;
        }
    }

    private static boolean isRuleFieldKey(String key) {
        return Set.of("id", "enabled", "action", "words", "replacements", "replacement-mode", "replacementMode", "mappings").contains(key);
    }

    private static void addRule(List<ContentFilterRule> out, ContentFilterRule rule) {
        if (rule == null) return;
        rule.normalize();
        if (!rule.words.isEmpty()) out.add(rule);
    }

    private static void applyRuleField(ContentFilterRule rule, String fieldLine, String ignored) {
        if (rule == null || fieldLine == null) return;
        int colon = yamlColonIndex(fieldLine);
        if (colon < 0) return;
        String key = decodeYamlScalar(fieldLine.substring(0, colon).trim());
        String raw = stripInlineCommentValue(fieldLine.substring(colon + 1)).trim();
        switch (key) {
            case "id" -> rule.id = decodeYamlScalar(raw);
            case "enabled" -> rule.enabled = parseYamlBoolean(raw, true);
            case "action" -> rule.action = decodeYamlScalar(raw);
            case "replacement-mode", "replacementMode" -> rule.replacementMode = decodeYamlScalar(raw);
            case "words" -> rule.words.addAll(parseInlineYamlList(raw));
            case "replacements" -> rule.replacements.addAll(parseInlineYamlList(raw));
            case "mappings" -> rule.mappings.putAll(parseInlineYamlMap(raw));
        }
    }

    private static boolean parseYamlBoolean(String raw, boolean def) {
        String v = decodeYamlScalar(raw).trim();
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("on") || v.equals("1")) return true;
        if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("no") || v.equalsIgnoreCase("off") || v.equals("0")) return false;
        return def;
    }

    private static List<String> parseInlineYamlList(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim();
        ArrayList<String> out = new ArrayList<>();
        if (value.isEmpty() || "[]".equals(value)) return out;
        if (!(value.startsWith("[") && value.endsWith("]"))) {
            String scalar = decodeYamlScalar(value);
            if (!scalar.isBlank()) out.add(scalar);
            return out;
        }
        String inner = value.substring(1, value.length() - 1);
        boolean single = false, dbl = false, escape = false;
        StringBuilder part = new StringBuilder();
        for (int i = 0; i <= inner.length(); i++) {
            char c = i < inner.length() ? inner.charAt(i) : ',';
            if (escape) { part.append(c); escape = false; continue; }
            if (dbl && c == '\\') { part.append(c); escape = true; continue; }
            if (!dbl && c == '\'') { single = !single; part.append(c); continue; }
            if (!single && c == '"') { dbl = !dbl; part.append(c); continue; }
            if (!single && !dbl && c == ',') {
                String scalar = decodeYamlScalar(part.toString().trim());
                if (!scalar.isBlank()) out.add(scalar);
                part.setLength(0);
            } else part.append(c);
        }
        return out;
    }

    private static Map<String,String> parseInlineYamlMap(String raw) {
        String value = String.valueOf(raw == null ? "" : raw).trim();
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        if (value.isEmpty() || "{}".equals(value)) return out;
        if (!(value.startsWith("{") && value.endsWith("}"))) return out;
        String inner = value.substring(1, value.length() - 1);
        boolean single = false, dbl = false, escape = false;
        StringBuilder part = new StringBuilder();
        ArrayList<String> entries = new ArrayList<>();
        for (int i = 0; i <= inner.length(); i++) {
            char c = i < inner.length() ? inner.charAt(i) : ',';
            if (escape) { part.append(c); escape = false; continue; }
            if (dbl && c == '\\') { part.append(c); escape = true; continue; }
            if (!dbl && c == '\'') { single = !single; part.append(c); continue; }
            if (!single && c == '"') { dbl = !dbl; part.append(c); continue; }
            if (!single && !dbl && c == ',') {
                if (!part.toString().trim().isEmpty()) entries.add(part.toString().trim());
                part.setLength(0);
            } else part.append(c);
        }
        for (String entry : entries) {
            int colon = yamlColonIndex(entry);
            if (colon <= 0) continue;
            String key = decodeYamlScalar(entry.substring(0, colon).trim());
            String val = decodeYamlScalar(stripInlineCommentValue(entry.substring(colon + 1)).trim());
            if (!key.isBlank()) out.put(key, val);
        }
        return out;
    }

    private static int yamlColonIndex(String text) {
        boolean single = false, dbl = false, escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escape) { escape = false; continue; }
            if (dbl && c == '\\') { escape = true; continue; }
            if (!dbl && c == '\'') single = !single;
            else if (!single && c == '"') dbl = !dbl;
            else if (!single && !dbl && c == ':') return i;
        }
        return -1;
    }

    private static String stripInlineCommentValue(String valuePart) {
        if (valuePart == null || valuePart.isBlank()) return "";
        boolean single = false, dbl = false, escape = false;
        for (int i = 0; i < valuePart.length(); i++) {
            char c = valuePart.charAt(i);
            if (escape) { escape = false; continue; }
            if (dbl && c == '\\') { escape = true; continue; }
            if (!dbl && c == '\'') single = !single;
            else if (!single && c == '"') dbl = !dbl;
            else if (!single && !dbl && c == '#' && (i == 0 || Character.isWhitespace(valuePart.charAt(i - 1)))) {
                return valuePart.substring(0, i).trim();
            }
        }
        return valuePart.trim();
    }

    private static String decodeYamlScalar(String raw) {
        String v = String.valueOf(raw == null ? "" : raw).trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            String inner = v.substring(1, v.length() - 1);
            StringBuilder out = new StringBuilder(inner.length());
            boolean escape = false;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (!escape && c == '\\') { escape = true; continue; }
                if (escape) {
                    switch (c) {
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        default -> out.append(c);
                    }
                    escape = false;
                } else out.append(c);
            }
            if (escape) out.append('\\');
            return out.toString();
        }
        if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) {
            return v.substring(1, v.length() - 1).replace("''", "'");
        }
        if ("null".equalsIgnoreCase(v) || "~".equals(v)) return "";
        return v;
    }

    public static void replaceContentFilterRules(Path file, List<ContentFilterRule> rules) throws IOException {
        Objects.requireNonNull(file, "file");
        synchronized (LOCK) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            ensureSection(lines, "content-filter");
            if (findPathIndex(lines, "content-filter.rules") < 0) {
                SectionLocation section = findSection(lines, "content-filter");
                if (section == null) throw new IOException("config section not found: content-filter");
                int insertAt = sectionEnd(lines, section.lineIndex, section.indent);
                lines.add(insertAt, " ".repeat(section.indent + 2) + "rules: []");
            }
            Deque<Node> stack = new ArrayDeque<>();
            int ruleLine = -1;
            int ruleIndent = -1;
            for (int i = 0; i < lines.size(); i++) {
                Parsed parsed = parse(lines.get(i));
                if (parsed == null) continue;
                while (!stack.isEmpty() && stack.peekLast().indent >= parsed.indent) stack.removeLast();
                String current = joinPath(stack, parsed.key);
                if (current.equals("content-filter.rules")) {
                    ruleLine = i;
                    ruleIndent = parsed.indent;
                    break;
                }
                if (parsed.isSection()) stack.addLast(new Node(parsed.indent, parsed.key));
            }
            if (ruleLine < 0) throw new IOException("config path not found: content-filter.rules");

            int end = contentFilterRulesEnd(lines, ruleLine, ruleIndent);

            ArrayList<String> replacement = new ArrayList<>();
            replacement.add(" ".repeat(ruleIndent) + "rules:");
            List<ContentFilterRule> normalized = new ArrayList<>();
            if (rules != null) for (ContentFilterRule source : rules) {
                if (source == null) continue;
                ContentFilterRule r = source.copy();
                r.normalize();
                if (!r.words.isEmpty()) normalized.add(r);
            }
            if (normalized.isEmpty()) {
                replacement.set(0, " ".repeat(ruleIndent) + "rules: []");
            } else {
                String p1 = " ".repeat(ruleIndent + 2);
                String p2 = " ".repeat(ruleIndent + 4);
                String p3 = " ".repeat(ruleIndent + 6);
                for (ContentFilterRule r : normalized) {
                    replacement.add(p1 + "- id: " + yamlScalar(r.id));
                    replacement.add(p2 + "enabled: " + r.enabled);
                    replacement.add(p2 + "action: " + yamlScalar(r.action));
                    replacement.add(p2 + "words:");
                    for (String word : r.words) replacement.add(p3 + "- " + yamlScalar(word));
                    if (!r.replacements.isEmpty()) {
                        replacement.add(p2 + "replacements:");
                        for (String value : r.replacements) replacement.add(p3 + "- " + yamlScalar(value));
                    }
                    if ("replace".equals(r.action)) replacement.add(p2 + "replacement-mode: " + yamlScalar(r.replacementMode));
                    if (!r.mappings.isEmpty()) {
                        replacement.add(p2 + "mappings:");
                        for (Map.Entry<String,String> e : r.mappings.entrySet()) {
                            replacement.add(p3 + yamlScalar(e.getKey()) + ": " + yamlScalar(e.getValue()));
                        }
                    }
                }
            }
            lines.subList(ruleLine, end).clear();
            lines.addAll(ruleLine, replacement);
            atomicWrite(file, lines);
        }
    }

    /**
     * Return the exclusive end of content-filter.rules for both common YAML
     * sequence layouts:
     *
     *   rules:                 rules:
     *     - id: one            - id: one
     *       words:               words:
     *         - bad              - bad
     *
     * Bukkit/SnakeYAML commonly emits the second (indentless sequence) form.
     * Treating an item at the same indentation as rules: as the next mapping key
     * leaves stale rule fragments behind after edit/delete and can make config.yml
     * invalid.
     */
    private static int contentFilterRulesEnd(List<String> lines, int ruleLine, int ruleIndent) {
        int end = ruleLine + 1;
        int itemIndent = -1;
        while (end < lines.size()) {
            String line = lines.get(end);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                end++;
                continue;
            }
            int indent = indentOf(line);
            if (itemIndent < 0) {
                if (trimmed.startsWith("-") && indent >= ruleIndent) {
                    itemIndent = indent;
                    end++;
                    continue;
                }
                // No block sequence follows rules: (for example rules: []).
                break;
            }
            if (indent < itemIndent) break;
            if (indent == itemIndent && !trimmed.startsWith("-")) break;
            end++;
        }
        return end;
    }

    private static int findPathIndex(List<String> lines, String target) {
        Deque<Node> stack = new ArrayDeque<>();
        for (int i = 0; i < lines.size(); i++) {
            Parsed parsed = parse(lines.get(i));
            if (parsed == null) continue;
            while (!stack.isEmpty() && stack.peekLast().indent >= parsed.indent) stack.removeLast();
            String current = joinPath(stack, parsed.key);
            if (current.equals(target)) return i;
            if (parsed.isSection()) stack.addLast(new Node(parsed.indent, parsed.key));
        }
        return -1;
    }

    private static Parsed parse(String line) {
        if (line == null || line.trim().isEmpty() || line.trim().startsWith("#") || line.trim().startsWith("- ")) return null;
        Matcher m = MAP_LINE.matcher(line);
        if (!m.matches()) return null;
        String rawKey = m.group(2).trim();
        String key = unquote(rawKey);
        if (key.isBlank()) return null;
        return new Parsed(m.group(1).length(), rawKey, key, m.group(3) == null ? "" : m.group(3));
    }

    private static String joinPath(Deque<Node> stack, String key) {
        StringBuilder out = new StringBuilder();
        for (Node node : stack) {
            if (out.length() > 0) out.append('.');
            out.append(node.key);
        }
        if (out.length() > 0) out.append('.');
        out.append(key);
        return out.toString();
    }

    private static String inlineComment(String valuePart) {
        if (valuePart == null || valuePart.isBlank()) return "";
        boolean single = false, dbl = false, escape = false;
        for (int i = 0; i < valuePart.length(); i++) {
            char c = valuePart.charAt(i);
            if (escape) { escape = false; continue; }
            if (dbl && c == '\\') { escape = true; continue; }
            if (!dbl && c == '\'') single = !single;
            else if (!single && c == '"') dbl = !dbl;
            else if (!single && !dbl && c == '#' && (i == 0 || Character.isWhitespace(valuePart.charAt(i - 1)))) {
                return valuePart.substring(i).trim();
            }
        }
        return "";
    }

    public static String yamlScalar(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return String.valueOf(value);
        String s = String.valueOf(value);
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    private static String unquote(String s) {
        String v = String.valueOf(s == null ? "" : s).trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') n++;
        return n;
    }

    private static void atomicWrite(Path file, List<String> lines) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private record Node(int indent, String key) {}
    private record SectionLocation(int lineIndex, int indent) {}
    private record Parsed(int indent, String rawKey, String key, String valuePart) {
        boolean isSection() {
            String v = valuePart == null ? "" : valuePart.trim();
            return v.isEmpty() || v.startsWith("#");
        }
    }
}
