package dev.kokoto.webchat.adapter.pl3xmap;


/* KWC 파일 안내 / KWC file guide
 * Pl3xMapAdapter는 pl3xmap 웹맵/프런트엔드에 KWC asset과 설정을 설치·갱신·제거하는 adapter 계층이다.
 * Pl3xMapAdapter is an adapter layer installing, updating, and removing KWC assets/configuration for the pl3xmap web map/frontend.
 *
 * adapter가 소유한 파일만 수정하고 사용자/맵 프로그램의 다른 파일을 덮어쓰지 않으며, 반복 실행해도 같은 결과가 되는 idempotency를 유지한다.
 * Modify only adapter-owned files, never overwrite unrelated user/map files, and keep installation idempotent across repeated runs.
 */
import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.JsonUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Installs KWC's map frontend into Pl3xMap's static web directory.
 *
 * Pl3xMap serves a static web directory configured by settings.web-directory.path.
 * KWC owns only a marked block in index.html and a dedicated asset directory, so
 * Pl3xMap's own generated web files remain untouched outside that block.
 */
public final class Pl3xMapAdapter {
    private static final String BLOCK_START = "<!-- KWC Pl3xMap adapter:start -->";
    private static final String BLOCK_END = "<!-- KWC Pl3xMap adapter:end -->";
    private static final Pattern PATH_LINE = Pattern.compile("^\\s*path\\s*:\\s*(.*?)\\s*$");

    private final Pl3xMapAdapterHost host;
    private final String assetVersionToken;

    public Pl3xMapAdapter(Pl3xMapAdapterHost host) {
        this.host = host;
        this.assetVersionToken = host.version() + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    public void install() {
        ConfigValues c = host.configValues();
        if (c == null) return;

        List<Path> roots = findWebRoots(c);
        if (!c.pluginEnabled || !c.pl3xmapEnabled) {
            uninstall(roots, c);
            return;
        }
        if (roots.isEmpty()) {
            // Optional adapter: absence of pl3xmap is normal and should not create noise.
            return;
        }

        int installed = 0;
        for (Path root : roots) {
            Path index = root.resolve("index.html");
            if (!Files.isRegularFile(index)) continue;
            try {
                if (c.pl3xmapAutoInstall) installAssets(root, c);
                if (c.pl3xmapAutoPatchIndex) patchIndex(index, c);
                installed++;
                host.logger().info("Pl3xMap KOKOTO WebChat frontend installed in " + root.toString());
            } catch (IOException ex) {
                host.logger().warn("Failed to install KOKOTO WebChat into Pl3xMap web root " + root + ": " + ex.getMessage());
            }
        }

        if (installed == 0 && c.pl3xmapWebRoot != null && !c.pl3xmapWebRoot.isBlank()) {
            host.logger().warn("Configured adapters.pl3xmap.web-root does not contain an index.html: " + c.pl3xmapWebRoot);
        }
    }

    private void uninstall(List<Path> roots, ConfigValues c) {
        for (Path root : roots) {
            Path index = root.resolve("index.html");
            if (Files.isRegularFile(index)) {
                try {
                    String original = Files.readString(index, StandardCharsets.UTF_8);
                    String cleaned = removeExistingBlock(original);
                    if (!cleaned.equals(original)) {
                        Files.writeString(index, cleaned, StandardCharsets.UTF_8);
                        host.logger().info("Removed KOKOTO WebChat block from disabled Pl3xMap adapter: " + index);
                    }
                } catch (IOException ex) {
                    host.logger().warn("Failed to remove disabled Pl3xMap adapter block from " + index + ": " + ex.getMessage());
                }
            }

            Set<Path> ownedDirs = new LinkedHashSet<>();
            ownedDirs.add(root.resolve(canonicalAddonPath(c.pl3xmapAddonPath)).normalize());
            ownedDirs.add(root.resolve("kokoto-web-chat").normalize());
            for (Path dir : ownedDirs) {
                if (!Files.exists(dir)) continue;
                try {
                    deleteTree(dir);
                    host.logger().info("Removed disabled Pl3xMap adapter assets: " + dir);
                } catch (IOException ex) {
                    host.logger().warn("Failed to remove disabled Pl3xMap adapter assets " + dir + ": " + ex.getMessage());
                }
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path path : paths) Files.deleteIfExists(path);
        }
    }

    private List<Path> findWebRoots(ConfigValues c) {
        Set<String> seen = new LinkedHashSet<>();
        List<Path> roots = new ArrayList<>();

        if (c.pl3xmapWebRoot != null && !c.pl3xmapWebRoot.isBlank()) {
            addRoot(roots, seen, Path.of(c.pl3xmapWebRoot));
        }

        for (Path config : pl3xmapConfigCandidates()) {
            if (!Files.isRegularFile(config)) continue;
            String configured = readPl3xMapWebDirectory(config);
            if (configured == null || configured.isBlank()) configured = "web";
            Path path = Path.of(configured);
            if (path.isAbsolute()) {
                addRoot(roots, seen, path);
            } else {
                // Pl3xMap resolves settings.web-directory.path against its platform data directory.
                Path parent = config.getParent();
                if (parent != null) addRoot(roots, seen, parent.resolve(path));
            }
        }

        // Common defaults. Only existing roots with index.html are accepted later.
        addRoot(roots, seen, Path.of("plugins", "Pl3xMap", "web"));
        addRoot(roots, seen, Path.of("plugins", "pl3xmap", "web"));
        addRoot(roots, seen, Path.of("config", "Pl3xMap", "web"));
        addRoot(roots, seen, Path.of("config", "pl3xmap", "web"));
        addRoot(roots, seen, Path.of("Pl3xMap", "web"));
        addRoot(roots, seen, Path.of("pl3xmap", "web"));

        roots.removeIf(root -> !Files.isRegularFile(root.resolve("index.html")));
        return roots;
    }

    private List<Path> pl3xmapConfigCandidates() {
        // Current Pl3xMap v3 uses config.yml. settings.yml is retained only as a
        // compatibility fallback for older/forked layouts; KWC never rewrites either file.
        return List.of(
                Path.of("plugins", "Pl3xMap", "config.yml"),
                Path.of("plugins", "pl3xmap", "config.yml"),
                Path.of("config", "Pl3xMap", "config.yml"),
                Path.of("config", "pl3xmap", "config.yml"),
                Path.of("Pl3xMap", "config.yml"),
                Path.of("pl3xmap", "config.yml"),
                Path.of("plugins", "Pl3xMap", "settings.yml"),
                Path.of("plugins", "pl3xmap", "settings.yml"),
                Path.of("config", "Pl3xMap", "settings.yml"),
                Path.of("config", "pl3xmap", "settings.yml"),
                Path.of("Pl3xMap", "settings.yml"),
                Path.of("pl3xmap", "settings.yml")
        );
    }

    /** Reads settings.web-directory.path without taking a dependency on Pl3xMap internals. */
    private String readPl3xMapWebDirectory(Path config) {
        try {
            List<String> lines = Files.readAllLines(config, StandardCharsets.UTF_8);
            int settingsIndent = -1;
            int webDirIndent = -1;
            for (String raw : lines) {
                String noComment = stripYamlComment(raw);
                if (noComment.trim().isEmpty()) continue;
                int indent = leadingSpaces(noComment);
                String trimmed = noComment.trim();

                if ("settings:".equals(trimmed)) {
                    settingsIndent = indent;
                    webDirIndent = -1;
                    continue;
                }
                if (settingsIndent >= 0 && indent <= settingsIndent) {
                    settingsIndent = -1;
                    webDirIndent = -1;
                }
                if (settingsIndent >= 0 && "web-directory:".equals(trimmed)) {
                    webDirIndent = indent;
                    continue;
                }
                if (webDirIndent >= 0 && indent <= webDirIndent) {
                    webDirIndent = -1;
                }
                if (webDirIndent >= 0) {
                    Matcher m = PATH_LINE.matcher(noComment);
                    if (m.matches()) return unquote(m.group(1).trim());
                }
            }
        } catch (IOException ignored) {}
        return "web";
    }

    private String stripYamlComment(String line) {
        boolean single = false, dbl = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\'' && !dbl) single = !single;
            else if (ch == '"' && !single && (i == 0 || line.charAt(i - 1) != '\\')) dbl = !dbl;
            else if (ch == '#' && !single && !dbl && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) return line.substring(0, i);
        }
        return line;
    }

    private int leadingSpaces(String value) {
        int n = 0;
        while (n < value.length() && value.charAt(n) == ' ') n++;
        return n;
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char a = value.charAt(0), b = value.charAt(value.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private void addRoot(List<Path> roots, Set<String> seen, Path path) {
        if (path == null) return;
        Path normalized = path.toAbsolutePath().normalize();
        if (seen.add(normalized.toString())) roots.add(normalized);
    }

    private void installAssets(Path root, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.pl3xmapAddonPath);
        Path safeRoot = root.toAbsolutePath().normalize();
        Path dir = safeRoot.resolve(addon).normalize();
        if (!dir.startsWith(safeRoot)) throw new IOException("unsafe addon path outside Pl3xMap web root");
        Files.createDirectories(dir);
        copyResource("pl3xmap/chat.js", dir.resolve("chat.js"));
        copyResource("pl3xmap/chat.css", dir.resolve("chat.css"));
        writeGeneratedConfig(dir.resolve("config.js"), c);
    }

    private void copyResource(String resource, Path out) throws IOException {
        try (InputStream in = host.resource(resource)) {
            if (in == null) throw new IOException("missing bundled resource " + resource);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeGeneratedConfig(Path out, ConfigValues c) throws IOException {
        String apiBase = c.pl3xmapApiBaseUrl == null ? "" : c.pl3xmapApiBaseUrl.trim();
        String js;
        if (apiBase.isEmpty()) {
            String direct = "location.protocol + '//' + location.hostname + ':" + c.httpPort + c.pathPrefix + "'";
            String publicApi = joinPublicPath(c.publicPrefix, c.pathPrefix);
            String auto = "(location.protocol === 'https:' ? location.origin + " + JsonUtil.quote(publicApi) + " : " + direct + ")";
            js = "window.KokotoWebChatConfig = { apiBase: " + auto + ", apiBaseUrl: " + auto + ", mapAdapter: 'pl3xmap' };\n";
        } else {
            String normalized = normalizeConfiguredBrowserUrl(c, apiBase);
            js = "window.KokotoWebChatConfig = { apiBase: " + JsonUtil.quote(normalized)
                    + ", apiBaseUrl: " + JsonUtil.quote(normalized) + ", mapAdapter: 'pl3xmap' };\n";
        }
        Files.writeString(out, js, StandardCharsets.UTF_8);
    }

    private void patchIndex(Path index, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.pl3xmapAddonPath);
        String version = assetVersionToken;
        String block = BLOCK_START + System.lineSeparator()
                + "        <link rel=\"stylesheet\" data-kwc-pl3xmap=\"style\" href=\"" + addon + "/chat.css?v=" + version + "\" />" + System.lineSeparator()
                + "        <script data-kwc-pl3xmap=\"config\" src=\"" + addon + "/config.js?v=" + version + "\"></script>" + System.lineSeparator()
                + "        <script data-kwc-pl3xmap=\"script\" src=\"" + addon + "/chat.js?v=" + version + "\"></script>" + System.lineSeparator()
                + BLOCK_END;

        String original = Files.readString(index, StandardCharsets.UTF_8);
        String cleaned = removeExistingBlock(original);
        String patched;
        int head = cleaned.toLowerCase().lastIndexOf("</head>");
        if (head >= 0) patched = cleaned.substring(0, head) + block + System.lineSeparator() + cleaned.substring(head);
        else patched = block + System.lineSeparator() + cleaned;
        if (!patched.equals(original)) Files.writeString(index, patched, StandardCharsets.UTF_8);
    }

    private String removeExistingBlock(String text) {
        String out = text;
        int start;
        while ((start = out.indexOf(BLOCK_START)) >= 0) {
            int end = out.indexOf(BLOCK_END, start);
            if (end < 0) {
                out = out.substring(0, start);
                break;
            }
            end += BLOCK_END.length();
            while (end < out.length() && (out.charAt(end) == '\r' || out.charAt(end) == '\n')) end++;
            out = out.substring(0, start) + out.substring(end);
        }
        // Pre-marker development entries, if any, are removed without touching Pl3xMap's own assets.
        out = out.replaceAll("(?im)^\\s*<link[^>]*data-kwc-pl3xmap[^>]*>\\s*(?:\\r?\\n)?", "");
        out = out.replaceAll("(?im)^\\s*<script[^>]*data-kwc-pl3xmap[^>]*></script>\\s*(?:\\r?\\n)?", "");
        return out;
    }

    private String canonicalAddonPath(String configured) {
        String out = configured == null ? "" : configured.trim().replace('\\', '/');
        out = out.replaceAll("^/+|/+$", "");
        if (out.isBlank() || out.contains("..")) return "kokoto-web-chat";
        return out;
    }

    private String normalizeConfiguredBrowserUrl(ConfigValues c, String configured) {
        String value = configured == null ? "" : configured.trim();
        if (value.isBlank()) return value;
        if (value.startsWith("http://") || value.startsWith("https://")) return trimTrailingSlash(value);
        if (value.startsWith("//")) {
            String origin = configuredCorsOrigin(c);
            String proto = origin.startsWith("http://") ? "http" : "https";
            return trimTrailingSlash(proto + ":" + value);
        }
        if (value.startsWith("/")) return trimTrailingSlash(value);
        String origin = configuredCorsOrigin(c);
        if (!origin.isBlank()) return trimTrailingSlash(origin + "/" + value.replaceFirst("^/+", ""));
        return trimTrailingSlash("/" + value);
    }

    private String configuredCorsOrigin(ConfigValues c) {
        String origin = c == null || c.corsOrigin == null ? "" : c.corsOrigin.trim();
        if (origin.isBlank() || "*".equals(origin)) return "";
        if (origin.startsWith("http://") || origin.startsWith("https://")) return trimTrailingSlash(origin);
        return "";
    }

    private String joinPublicPath(String publicPrefix, String internalPath) {
        String prefix = publicPrefix == null ? "" : publicPrefix.trim();
        String path = internalPath == null || internalPath.isBlank() ? "/api" : internalPath.trim();
        if (!path.startsWith("/")) path = "/" + path;
        while (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        if (prefix.isBlank() || "/".equals(prefix)) return path;
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        while (prefix.endsWith("/") && prefix.length() > 1) prefix = prefix.substring(0, prefix.length() - 1);
        return prefix + path;
    }

    private String trimTrailingSlash(String value) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/") && out.length() > 1) out = out.substring(0, out.length() - 1);
        return out;
    }
}
