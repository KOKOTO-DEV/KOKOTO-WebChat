package dev.kokoto.webchat.adapter.dynmap;


/* KWC 파일 안내 / KWC file guide
 * DynmapAdapter는 dynmap 웹맵/프런트엔드에 KWC asset과 설정을 설치·갱신·제거하는 adapter 계층이다.
 * DynmapAdapter is an adapter layer installing, updating, and removing KWC assets/configuration for the dynmap web map/frontend.
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

/** Installs KWC's map frontend into Dynmap's configured static webpath. */
public final class DynmapAdapter {
    private static final String BLOCK_START = "<!-- KWC dynmap adapter:start -->";
    private static final String BLOCK_END = "<!-- KWC dynmap adapter:end -->";
    private static final Pattern WEBPATH_LINE = Pattern.compile("^\\s*webpath\\s*:\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);

    private final DynmapAdapterHost host;
    private final String assetVersionToken;

    public DynmapAdapter(DynmapAdapterHost host) {
        this.host = host;
        this.assetVersionToken = host.version() + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    public void install() {
        ConfigValues c = host.configValues();
        if (c == null) return;
        List<Path> roots = findWebRoots(c);
        if (!c.pluginEnabled || !c.dynmapEnabled) {
            uninstall(roots, c);
            return;
        }
        if (roots.isEmpty()) return;

        int installed = 0;
        for (Path root : roots) {
            Path index = root.resolve("index.html");
            if (!Files.isRegularFile(index)) continue;
            try {
                if (c.dynmapAutoInstall) installAssets(root, c);
                if (c.dynmapAutoPatchIndex) patchIndex(index, c);
                installed++;
                host.logger().info("Dynmap KOKOTO WebChat frontend installed in " + root);
            } catch (IOException ex) {
                host.logger().warn("Failed to install KOKOTO WebChat into Dynmap web root " + root + ": " + ex.getMessage());
            }
        }
        if (installed == 0 && c.dynmapWebRoot != null && !c.dynmapWebRoot.isBlank()) {
            host.logger().warn("Configured adapters.dynmap.web-root does not contain an index.html: " + c.dynmapWebRoot);
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
                        host.logger().info("Removed KOKOTO WebChat block from disabled Dynmap adapter: " + index);
                    }
                } catch (IOException ex) {
                    host.logger().warn("Failed to remove disabled Dynmap adapter block from " + index + ": " + ex.getMessage());
                }
            }
            Set<Path> ownedDirs = new LinkedHashSet<>();
            ownedDirs.add(root.resolve(canonicalAddonPath(c.dynmapAddonPath)).normalize());
            ownedDirs.add(root.resolve("kokoto-web-chat").normalize());
            for (Path dir : ownedDirs) {
                if (!Files.exists(dir) || !dir.startsWith(root.toAbsolutePath().normalize())) continue;
                try {
                    deleteTree(dir);
                    host.logger().info("Removed disabled Dynmap adapter assets: " + dir);
                } catch (IOException ex) {
                    host.logger().warn("Failed to remove disabled Dynmap adapter assets " + dir + ": " + ex.getMessage());
                }
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private List<Path> findWebRoots(ConfigValues c) {
        Set<String> seen = new LinkedHashSet<>();
        List<Path> roots = new ArrayList<>();
        if (c.dynmapWebRoot != null && !c.dynmapWebRoot.isBlank()) addRoot(roots, seen, Path.of(c.dynmapWebRoot));

        for (Path config : dynmapConfigCandidates()) {
            if (!Files.isRegularFile(config)) continue;
            String configured = readDynmapWebPath(config);
            if (configured == null || configured.isBlank()) configured = "web";
            Path path = Path.of(configured);
            if (path.isAbsolute()) addRoot(roots, seen, path);
            else {
                Path parent = config.getParent();
                if (parent != null) addRoot(roots, seen, parent.resolve(path));
            }
        }

        addRoot(roots, seen, Path.of("plugins", "dynmap", "web"));
        addRoot(roots, seen, Path.of("plugins", "Dynmap", "web"));
        addRoot(roots, seen, Path.of("dynmap", "web"));
        addRoot(roots, seen, Path.of("config", "dynmap", "web"));
        addRoot(roots, seen, Path.of("config", "Dynmap", "web"));
        roots.removeIf(root -> !Files.isRegularFile(root.resolve("index.html")));
        return roots;
    }

    private List<Path> dynmapConfigCandidates() {
        return List.of(
                Path.of("plugins", "dynmap", "configuration.txt"),
                Path.of("plugins", "Dynmap", "configuration.txt"),
                Path.of("dynmap", "configuration.txt"),
                Path.of("config", "dynmap", "configuration.txt"),
                Path.of("config", "Dynmap", "configuration.txt")
        );
    }

    /** Reads Dynmap's top-level webpath setting without depending on Dynmap internals. */
    private String readDynmapWebPath(Path config) {
        try {
            for (String raw : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                String line = stripComment(raw);
                Matcher m = WEBPATH_LINE.matcher(line);
                if (m.matches()) return unquote(m.group(1).trim());
            }
        } catch (IOException ignored) {}
        return "web";
    }

    private String stripComment(String line) {
        boolean single = false, dbl = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\'' && !dbl) single = !single;
            else if (ch == '"' && !single && (i == 0 || line.charAt(i - 1) != '\\')) dbl = !dbl;
            else if (ch == '#' && !single && !dbl && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) return line.substring(0, i);
        }
        return line;
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
        String addon = canonicalAddonPath(c.dynmapAddonPath);
        Path dir = root.resolve(addon).normalize();
        if (!dir.startsWith(root.toAbsolutePath().normalize())) throw new IOException("addon-path escapes Dynmap web root");
        Files.createDirectories(dir);
        copyResource("dynmap/chat.js", dir.resolve("chat.js"));
        copyResource("dynmap/chat.css", dir.resolve("chat.css"));
        writeGeneratedConfig(dir.resolve("config.js"), c);
    }

    private void copyResource(String resource, Path out) throws IOException {
        try (InputStream in = host.resource(resource)) {
            if (in == null) throw new IOException("missing bundled resource " + resource);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeGeneratedConfig(Path out, ConfigValues c) throws IOException {
        String apiBase = c.dynmapApiBaseUrl == null ? "" : c.dynmapApiBaseUrl.trim();
        String js;
        if (apiBase.isEmpty()) {
            String direct = "location.protocol + '//' + location.hostname + ':" + c.httpPort + c.pathPrefix + "'";
            String publicApi = joinPublicPath(c.publicPrefix, c.pathPrefix);
            String auto = "(location.protocol === 'https:' ? location.origin + " + JsonUtil.quote(publicApi) + " : " + direct + ")";
            js = "window.KokotoWebChatConfig = { apiBase: " + auto + ", apiBaseUrl: " + auto + ", mapAdapter: 'dynmap' };\n";
        } else {
            String normalized = normalizeConfiguredBrowserUrl(c, apiBase);
            js = "window.KokotoWebChatConfig = { apiBase: " + JsonUtil.quote(normalized)
                    + ", apiBaseUrl: " + JsonUtil.quote(normalized) + ", mapAdapter: 'dynmap' };\n";
        }
        Files.writeString(out, js, StandardCharsets.UTF_8);
    }

    private void patchIndex(Path index, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.dynmapAddonPath);
        String version = assetVersionToken;
        String nl = System.lineSeparator();
        String block = BLOCK_START + nl
                + "<link rel=\"stylesheet\" data-kwc-dynmap=\"style\" href=\"" + addon + "/chat.css?v=" + version + "\" />" + nl
                + "<script data-kwc-dynmap=\"config\" src=\"" + addon + "/config.js?v=" + version + "\"></script>" + nl
                + "<script data-kwc-dynmap=\"script\" src=\"" + addon + "/chat.js?v=" + version + "\"></script>" + nl
                + BLOCK_END;
        String original = Files.readString(index, StandardCharsets.UTF_8);
        String cleaned = removeExistingBlock(original);
        int head = cleaned.toLowerCase().lastIndexOf("</head>");
        String patched = head >= 0 ? cleaned.substring(0, head) + block + nl + cleaned.substring(head) : block + nl + cleaned;
        if (!patched.equals(original)) Files.writeString(index, patched, StandardCharsets.UTF_8);
    }

    private String removeExistingBlock(String text) {
        String out = text;
        int start;
        while ((start = out.indexOf(BLOCK_START)) >= 0) {
            int end = out.indexOf(BLOCK_END, start);
            if (end < 0) { out = out.substring(0, start); break; }
            end += BLOCK_END.length();
            while (end < out.length() && (out.charAt(end) == '\r' || out.charAt(end) == '\n')) end++;
            out = out.substring(0, start) + out.substring(end);
        }
        out = out.replaceAll("(?im)^\\s*<link[^>]*data-kwc-dynmap[^>]*>\\s*(?:\\r?\\n)?", "");
        out = out.replaceAll("(?im)^\\s*<script[^>]*data-kwc-dynmap[^>]*></script>\\s*(?:\\r?\\n)?", "");
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
