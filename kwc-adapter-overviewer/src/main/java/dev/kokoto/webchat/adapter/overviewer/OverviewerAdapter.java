package dev.kokoto.webchat.adapter.overviewer;

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

/** Installs KWC's generic map frontend into an existing Minecraft Overviewer static web-map export. */
public final class OverviewerAdapter {
    private static final String BLOCK_START = "<!-- KWC overviewer adapter:start -->";
    private static final String BLOCK_END = "<!-- KWC overviewer adapter:end -->";

    private final OverviewerAdapterHost host;
    private final String assetVersionToken;

    public OverviewerAdapter(OverviewerAdapterHost host) {
        this.host = host;
        this.assetVersionToken = host.version() + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    public void install() {
        ConfigValues c = host.configValues();
        if (c == null) return;

        List<WebRoot> roots = findWebRoots(c, true);
        if (!c.pluginEnabled || !c.overviewerEnabled) {
            uninstall(findWebRoots(c, false), c);
            return;
        }

        if (roots.isEmpty()) {
            if (c.overviewerWebRoot != null && !c.overviewerWebRoot.isBlank()) {
                host.logger().warn("Configured adapters.overviewer.web-root does not contain a Minecraft Overviewer web map: " + c.overviewerWebRoot);
            } else {
                host.logger().info("Overviewer adapter is enabled, but no Overviewer web root was detected. Set adapters.overviewer.web-root to the directory containing Overviewer's generated index.html.");
            }
            return;
        }

        for (WebRoot target : roots) {
            try {
                if (c.overviewerAutoInstall) installAssets(target.root, c);
                if (c.overviewerAutoPatchIndex) patchIndex(target.index, c);
                host.logger().info("Overviewer KOKOTO WebChat frontend installed in " + target.root);
            } catch (IOException ex) {
                host.logger().warn("Failed to install KOKOTO WebChat into Overviewer web root " + target.root + ": " + ex.getMessage());
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
                        host.logger().info("Removed KOKOTO WebChat block from disabled Overviewer adapter: " + target.index);
                    }
                } catch (IOException ex) {
                    host.logger().warn("Failed to remove disabled Overviewer adapter block from " + target.index + ": " + ex.getMessage());
                }
            }

            Path dir = target.root.resolve(canonicalAddonPath(c.overviewerAddonPath)).normalize();
            if (!Files.exists(dir) || !dir.startsWith(target.root.toAbsolutePath().normalize())) continue;
            try {
                deleteTree(dir);
                host.logger().info("Removed disabled Overviewer adapter assets: " + dir);
            } catch (IOException ex) {
                host.logger().warn("Failed to remove disabled Overviewer adapter assets " + dir + ": " + ex.getMessage());
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private List<WebRoot> findWebRoots(ConfigValues c, boolean requireOverviewerMarker) {
        Set<String> seen = new LinkedHashSet<>();
        List<WebRoot> roots = new ArrayList<>();

        if (c.overviewerWebRoot != null && !c.overviewerWebRoot.isBlank()) {
            addRoot(roots, seen, Path.of(c.overviewerWebRoot), requireOverviewerMarker);
        }

        // Overviewer outputdir is arbitrary, so discovery remains intentionally conservative.
        // Explicit/external outputs should use web-root; every candidate is marker-checked.
        List<Path> candidates = List.of(
                Path.of("overviewer"),
                Path.of("overviewer-map"),
                Path.of("overviewer-web"),
                Path.of("web", "overviewer"),
                Path.of("web", "overviewer-map"),
                Path.of("maps", "overviewer"),
                Path.of("maps", "overviewer-map"),
                Path.of("www", "overviewer"),
                Path.of("www", "overviewer-map")
        );
        for (Path candidate : candidates) addRoot(roots, seen, candidate, requireOverviewerMarker);
        return roots;
    }

    private void addRoot(List<WebRoot> roots, Set<String> seen, Path path, boolean requireOverviewerMarker) {
        if (path == null) return;
        Path root = path.toAbsolutePath().normalize();
        String key = root.toString();
        if (!seen.add(key)) return;

        Path index = root.resolve("index.html");
        if (!Files.isRegularFile(index)) return;
        if (requireOverviewerMarker ? isOverviewerIndex(index) : isOverviewerOrOwnedIndex(index)) {
            roots.add(new WebRoot(root, index));
        }
    }

    static boolean isOverviewerIndex(Path index) {
        try {
            String html = Files.readString(index, StandardCharsets.UTF_8);
            String lower = html.toLowerCase(Locale.ROOT);
            Path root = index.toAbsolutePath().normalize().getParent();

            // Canonical Overviewer output contains a generator meta tag plus overviewerConfig.js,
            // overviewer.js and overviewer.css. Accept both original and successor builds that keep
            // the standard web assets, but do not patch an arbitrary Leaflet site.
            boolean generator = lower.contains("name=\"generator\"") && lower.contains("minecraft-overviewer")
                    || lower.contains("name='generator'") && lower.contains("minecraft-overviewer");
            boolean configRef = lower.contains("overviewerconfig.js");
            boolean runtimeRef = lower.contains("overviewer.js") || lower.contains("overviewer.util.initialize");
            boolean stylesheetRef = lower.contains("overviewer.css");
            boolean configFile = root != null && Files.isRegularFile(root.resolve("overviewerConfig.js"));
            boolean runtimeFile = root != null && Files.isRegularFile(root.resolve("overviewer.js"));

            return (generator && (configRef || configFile))
                    || (configRef && runtimeRef && stylesheetRef)
                    || (configFile && runtimeFile && (configRef || runtimeRef));
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isOverviewerOrOwnedIndex(Path index) {
        try {
            String html = Files.readString(index, StandardCharsets.UTF_8);
            return html.contains(BLOCK_START) || isOverviewerIndex(index);
        } catch (IOException ex) {
            return false;
        }
    }

    private void installAssets(Path root, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.overviewerAddonPath);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path dir = normalizedRoot.resolve(addon).normalize();
        if (!dir.startsWith(normalizedRoot)) throw new IOException("addon-path escapes Overviewer web root");
        Files.createDirectories(dir);
        copyResource("overviewer/chat.js", dir.resolve("chat.js"));
        copyResource("overviewer/chat.css", dir.resolve("chat.css"));
        writeGeneratedConfig(dir.resolve("config.js"), c);
    }

    private void copyResource(String resource, Path out) throws IOException {
        try (InputStream in = host.resource(resource)) {
            if (in == null) throw new IOException("missing bundled resource " + resource);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeGeneratedConfig(Path out, ConfigValues c) throws IOException {
        String apiBase = c.overviewerApiBaseUrl == null ? "" : c.overviewerApiBaseUrl.trim();
        String js;
        if (apiBase.isEmpty()) {
            String direct = "location.protocol + '//' + location.hostname + ':" + c.httpPort + c.pathPrefix + "'";
            String publicApi = joinPublicPath(c.publicPrefix, c.pathPrefix);
            String auto = "(location.protocol === 'https:' ? location.origin + " + JsonUtil.quote(publicApi) + " : " + direct + ")";
            js = "window.KokotoWebChatConfig = { apiBase: " + auto + ", apiBaseUrl: " + auto + ", mapAdapter: 'overviewer' };\n";
        } else {
            String normalized = normalizeConfiguredBrowserUrl(c, apiBase);
            js = "window.KokotoWebChatConfig = { apiBase: " + JsonUtil.quote(normalized)
                    + ", apiBaseUrl: " + JsonUtil.quote(normalized) + ", mapAdapter: 'overviewer' };\n";
        }
        Files.writeString(out, js, StandardCharsets.UTF_8);
    }

    private void patchIndex(Path index, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.overviewerAddonPath);
        String version = assetVersionToken;
        String nl = System.lineSeparator();
        String block = BLOCK_START + nl
                + "<link rel=\"stylesheet\" data-kwc-overviewer=\"style\" href=\"" + addon + "/chat.css?v=" + version + "\" />" + nl
                + "<script data-kwc-overviewer=\"config\" src=\"" + addon + "/config.js?v=" + version + "\"></script>" + nl
                + "<script data-kwc-overviewer=\"script\" src=\"" + addon + "/chat.js?v=" + version + "\"></script>" + nl
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
        out = out.replaceAll("(?im)^\\s*<link[^>]*data-kwc-overviewer[^>]*>\\s*(?:\\r?\\n)?", "");
        out = out.replaceAll("(?im)^\\s*<script[^>]*data-kwc-overviewer[^>]*></script>\\s*(?:\\r?\\n)?", "");
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
