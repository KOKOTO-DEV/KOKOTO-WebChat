package dev.kokoto.bluemapwebchat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the physical top-level block order of config.yml aligned with the
 * bundled default while preserving every block's text, values, and comments.
 * Unknown top-level blocks are retained after known blocks in their original
 * relative order.
 */
final class ConfigLayoutManager {
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*:(?:\\s.*)?$");

    private ConfigLayoutManager() {
    }

    static void reorder(BlueMapWebChatPlugin plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(path)) return;
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            String bundled;
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in == null) throw new IllegalStateException("Missing bundled resource: config.yml");
                bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            String updated = reorderText(raw, bundled);
            if (updated.equals(raw)) return;
            Files.writeString(path, updated, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            plugin.getLogger().info("Reordered config.yml top-level blocks to match the BlueMapWebChat "
                    + plugin.getDescription().getVersion()
                    + " default layout. Setting values and block comments were preserved.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to reorder config.yml blocks: " + ex.getMessage());
        }
    }

    static String reorderText(String currentText, String referenceText) {
        String current = currentText == null ? "" : currentText;
        String reference = referenceText == null ? "" : referenceText;
        if (current.isEmpty() || reference.isEmpty()) return current;

        String newline = current.contains("\r\n") ? "\r\n" : "\n";
        boolean endedWithNewline = current.endsWith("\n") || current.endsWith("\r");
        String normalizedCurrent = normalizeNewlines(current);
        String normalizedReference = normalizeNewlines(reference);

        List<String> desiredOrder = topLevelKeys(normalizedReference);
        if (desiredOrder.isEmpty()) return current;

        ParsedDocument document = parse(normalizedCurrent);
        if (document.blocks.size() < 2) return current;

        Set<String> duplicates = duplicateKeys(document.blocks);
        if (!duplicates.isEmpty()) {
            // Duplicate top-level YAML keys are ambiguous. Preserve the file exactly
            // rather than risk changing which duplicate wins during YAML parsing.
            return current;
        }

        Map<String, Block> byKey = new HashMap<>();
        for (Block block : document.blocks) byKey.put(block.key, block);

        List<Block> reordered = new ArrayList<>(document.blocks.size());
        Set<String> emitted = new HashSet<>();
        for (String key : desiredOrder) {
            Block block = byKey.get(key);
            if (block != null) {
                reordered.add(block);
                emitted.add(key);
            }
        }
        // Preserve unknown/future/plugin-local top-level blocks instead of dropping them.
        for (Block block : document.blocks) {
            if (!emitted.contains(block.key)) reordered.add(block);
        }

        boolean changed = false;
        for (int i = 0; i < document.blocks.size(); i++) {
            if (!document.blocks.get(i).key.equals(reordered.get(i).key)) {
                changed = true;
                break;
            }
        }
        if (!changed) return current;

        StringBuilder out = new StringBuilder(normalizedCurrent.length() + 32);
        appendLines(out, document.preamble);
        for (Block block : reordered) appendLines(out, block.lines);
        String result = out.toString();
        if (!endedWithNewline && result.endsWith("\n")) result = result.substring(0, result.length() - 1);
        if (!"\n".equals(newline)) result = result.replace("\n", newline);
        return result;
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<String> topLevelKeys(String text) {
        List<String> keys = new ArrayList<>();
        for (String line : splitLines(text)) {
            Matcher matcher = TOP_LEVEL_KEY.matcher(line);
            if (matcher.matches()) keys.add(matcher.group(1));
        }
        return keys;
    }

    private static ParsedDocument parse(String text) {
        List<String> lines = splitLines(text);
        List<Integer> keyLines = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = TOP_LEVEL_KEY.matcher(lines.get(i));
            if (matcher.matches()) {
                keyLines.add(i);
                keys.add(matcher.group(1));
            }
        }
        if (keyLines.isEmpty()) return new ParsedDocument(lines, List.of());

        int[] starts = new int[keyLines.size()];
        starts[0] = keyLines.get(0); // Keep the file header/preamble fixed at the top.
        for (int i = 1; i < keyLines.size(); i++) {
            int start = keyLines.get(i);
            int lowerBound = keyLines.get(i - 1) + 1;
            while (start > lowerBound && isTopLevelTrivia(lines.get(start - 1))) start--;
            starts[i] = start;
        }

        List<String> preamble = new ArrayList<>(lines.subList(0, starts[0]));
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < keyLines.size(); i++) {
            int start = starts[i];
            int end = i + 1 < keyLines.size() ? starts[i + 1] : lines.size();
            blocks.add(new Block(keys.get(i), new ArrayList<>(lines.subList(start, end))));
        }
        return new ParsedDocument(preamble, blocks);
    }

    private static boolean isTopLevelTrivia(String line) {
        return line.isBlank() || line.startsWith("#");
    }

    private static Set<String> duplicateKeys(List<Block> blocks) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (Block block : blocks) {
            if (!seen.add(block.key)) duplicates.add(block.key);
        }
        return duplicates;
    }

    private static List<String> splitLines(String text) {
        String[] raw = text.split("\\n", -1);
        int length = raw.length;
        if (length > 0 && raw[length - 1].isEmpty()) length--;
        List<String> out = new ArrayList<>(length);
        for (int i = 0; i < length; i++) out.add(raw[i]);
        return out;
    }

    private static void appendLines(StringBuilder out, List<String> lines) {
        for (String line : lines) out.append(line).append('\n');
    }

    private record Block(String key, List<String> lines) {
    }

    private record ParsedDocument(List<String> preamble, List<Block> blocks) {
    }
}
