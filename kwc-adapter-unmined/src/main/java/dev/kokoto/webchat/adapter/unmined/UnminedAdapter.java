package dev.kokoto.webchat.adapter.unmined;


/* KWC 파일 안내 / KWC file guide
 * UnminedAdapter는 unmined 웹맵/프런트엔드에 KWC asset과 설정을 설치·갱신·제거하는 adapter 계층이다.
 * UnminedAdapter is an adapter layer installing, updating, and removing KWC assets/configuration for the unmined web map/frontend.
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
import java.util.Locale;
import java.util.Set;

/** Installs KWC's generic map frontend into an existing uNmINeD static web export. */
public final class UnminedAdapter {
    private static final String BLOCK_START = "<!-- KWC unmined adapter:start -->";
    private static final String BLOCK_END = "<!-- KWC unmined adapter:end -->";

    private final UnminedAdapterHost host;
    private final String assetVersionToken;

    public UnminedAdapter(UnminedAdapterHost host) {
        this.host = host;
        this.assetVersionToken = host.version() + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    public void install() {
        ConfigValues c = host.configValues();
        if (c == null) return;

        List<WebRoot> roots = findWebRoots(c, true);
        if (!c.pluginEnabled || !c.unminedEnabled) {
            uninstall(findWebRoots(c, false), c);
            return;
        }

        if (roots.isEmpty()) {
            if (c.unminedWebRoot != null && !c.unminedWebRoot.isBlank()) {
                host.logger().warn("Configured adapters.unmined.web-root does not contain a uNmINeD web export: " + c.unminedWebRoot);
            } else {
                host.logger().info("uNmINeD adapter is enabled, but no uNmINeD web root was detected. Set adapters.unmined.web-root to the directory containing the exported index.html.");
            }
            return;
        }

        for (WebRoot target : roots) {
            try {
                if (c.unminedAutoInstall) installAssets(target.root, c);
                if (c.unminedAutoPatchIndex) patchIndex(target.index, c);
                host.logger().info("uNmINeD KOKOTO WebChat frontend installed in " + target.root);
            } catch (IOException ex) {
                host.logger().warn("Failed to install KOKOTO WebChat into uNmINeD web root " + target.root + ": " + ex.getMessage());
            }
        }
    }

    private void uninstall(List<WebRoot> roots, ConfigValues c) {
        for (WebRoot target : roots) {
            if (Files.isRegularFile(target.index)) {
                try {
                    String original = Files.readString(target.index, StandardCharsets.UTF_8);
                    String cleaned = removeExistingBlock(original);
                    if (!cleaned.equals(original)) {
                        Files.writeString(target.index, cleaned, StandardCharsets.UTF_8);
                        host.logger().info("Removed KOKOTO WebChat block from disabled uNmINeD adapter: " + target.index);
                    }
                } catch (IOException ex) {
                    host.logger().warn("Failed to remove disabled uNmINeD adapter block from " + target.index + ": " + ex.getMessage());
                }
            }

            Path dir = target.root.resolve(canonicalAddonPath(c.unminedAddonPath)).normalize();
            if (!Files.exists(dir) || !dir.startsWith(target.root.toAbsolutePath().normalize())) continue;
            try {
                deleteTree(dir);
                host.logger().info("Removed disabled uNmINeD adapter assets: " + dir);
            } catch (IOException ex) {
                host.logger().warn("Failed to remove disabled uNmINeD adapter assets " + dir + ": " + ex.getMessage());
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private List<WebRoot> findWebRoots(ConfigValues c, boolean requireUnminedMarker) {
        Set<String> seen = new LinkedHashSet<>();
        List<WebRoot> roots = new ArrayList<>();

        if (c.unminedWebRoot != null && !c.unminedWebRoot.isBlank()) {
            addRoot(roots, seen, Path.of(c.unminedWebRoot), requireUnminedMarker);
        }

        // uNmINeD can export to any directory, so auto-detection intentionally stays
        // conservative and marker-checked. Custom or external web roots should use web-root.
        List<Path> candidates = List.of(
                Path.of("unmined"),
                Path.of("uNmINeD"),
                Path.of("unmined-web"),
                Path.of("web", "unmined"),
                Path.of("web", "uNmINeD"),
                Path.of("web", "unmined-web"),
                Path.of("maps", "unmined"),
                Path.of("maps", "uNmINeD"),
                Path.of("www", "unmined"),
                Path.of("www", "uNmINeD")
        );
        for (Path candidate : candidates) addRoot(roots, seen, candidate, requireUnminedMarker);
        return roots;
    }

    private void addRoot(List<WebRoot> roots, Set<String> seen, Path path, boolean requireUnminedMarker) {
        if (path == null) return;
        Path root = path.toAbsolutePath().normalize();
        String key = root.toString();
        if (!seen.add(key)) return;

        Path index = findEntryPoint(root, requireUnminedMarker);
        if (index != null) roots.add(new WebRoot(root, index));
    }

    private Path findEntryPoint(Path root, boolean requireUnminedMarker) {
        if (!Files.isDirectory(root)) return null;
        Path current = root.resolve("index.html");
        Path legacy = root.resolve("unmined.index.html");

        if (Files.isRegularFile(current) && (requireUnminedMarker ? isUnminedIndex(current) : isUnminedOrOwnedIndex(current))) return current;
        if (Files.isRegularFile(legacy) && (requireUnminedMarker ? isUnminedIndex(legacy) : isUnminedOrOwnedIndex(legacy))) return legacy;
        return null;
    }

    static boolean isUnminedIndex(Path index) {
        try {
            String html = Files.readString(index, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            boolean metadata = html.contains("unmined.map.properties.js") || html.contains("unminedmapproperties");
            boolean runtime = html.contains("unmined.js") || html.contains("new unmined(") || html.contains("unmined.createplayermarkers");
            boolean generatedComment = html.contains("map metadata generated by unmined") || html.contains("unmined js and css");
            return (metadata && runtime) || (generatedComment && (metadata || runtime));
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isUnminedOrOwnedIndex(Path index) {
        try {
            String html = Files.readString(index, StandardCharsets.UTF_8);
            return html.contains(BLOCK_START) || isUnminedIndex(index);
        } catch (IOException ex) {
            return false;
        }
    }

    private void installAssets(Path root, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.unminedAddonPath);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path dir = normalizedRoot.resolve(addon).normalize();
        if (!dir.startsWith(normalizedRoot)) throw new IOException("addon-path escapes uNmINeD web root");
        Files.createDirectories(dir);
        copyResource("unmined/chat.js", dir.resolve("chat.js"));
        copyResource("unmined/chat.css", dir.resolve("chat.css"));
        writeGeneratedConfig(dir.resolve("config.js"), c);
    }

    private void copyResource(String resource, Path out) throws IOException {
        try (InputStream in = host.resource(resource)) {
            if (in == null) throw new IOException("missing bundled resource " + resource);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeGeneratedConfig(Path out, ConfigValues c) throws IOException {
        String apiBase = c.unminedApiBaseUrl == null ? "" : c.unminedApiBaseUrl.trim();
        String js;
        if (apiBase.isEmpty()) {
            String direct = "location.protocol + '//' + location.hostname + ':" + c.httpPort + c.pathPrefix + "'";
            String publicApi = joinPublicPath(c.publicPrefix, c.pathPrefix);
            String auto = "(location.protocol === 'https:' ? location.origin + " + JsonUtil.quote(publicApi) + " : " + direct + ")";
            js = "window.KokotoWebChatConfig = { apiBase: " + auto + ", apiBaseUrl: " + auto + ", mapAdapter: 'unmined' };\n";
        } else {
            String normalized = normalizeConfiguredBrowserUrl(c, apiBase);
            js = "window.KokotoWebChatConfig = { apiBase: " + JsonUtil.quote(normalized)
                    + ", apiBaseUrl: " + JsonUtil.quote(normalized) + ", mapAdapter: 'unmined' };\n";
        }
        Files.writeString(out, js, StandardCharsets.UTF_8);
    }

    private void patchIndex(Path index, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.unminedAddonPath);
        String version = assetVersionToken;
        String nl = System.lineSeparator();
        String block = BLOCK_START + nl
                + "<link rel=\"stylesheet\" data-kwc-unmined=\"style\" href=\"" + addon + "/chat.css?v=" + version + "\" />" + nl
                + "<script data-kwc-unmined=\"config\" src=\"" + addon + "/config.js?v=" + version + "\"></script>" + nl
                + "<script data-kwc-unmined=\"script\" src=\"" + addon + "/chat.js?v=" + version + "\"></script>" + nl
                + BLOCK_END;

        String original = Files.readString(index, StandardCharsets.UTF_8);
        String cleaned = removeExistingBlock(original);
        int head = cleaned.toLowerCase(Locale.ROOT).lastIndexOf("</head>");
        String patched = head >= 0 ? cleaned.substring(0, head) + block + nl + cleaned.substring(head) : block + nl + cleaned;
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
        out = out.replaceAll("(?im)^\\s*<link[^>]*data-kwc-unmined[^>]*>\\s*(?:\\r?\\n)?", "");
        out = out.replaceAll("(?im)^\\s*<script[^>]*data-kwc-unmined[^>]*></script>\\s*(?:\\r?\\n)?", "");
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

    private static final class WebRoot {
        final Path root;
        final Path index;

        WebRoot(Path root, Path index) {
            this.root = root;
            this.index = index;
        }
    }
}
