package dev.kokoto.webchat.adapter.liveatlas;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Installs KWC's map frontend into an existing LiveAtlas static web root. */
public final class LiveAtlasAdapter {
    private static final String BLOCK_START = "<!-- KWC liveatlas adapter:start -->";
    private static final String BLOCK_END = "<!-- KWC liveatlas adapter:end -->";
    private static final Pattern DYNMAP_WEBPATH_LINE = Pattern.compile("^\\s*webpath\\s*:\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);

    private final LiveAtlasAdapterHost host;
    private final String assetVersionToken;

    public LiveAtlasAdapter(LiveAtlasAdapterHost host) {
        this.host = host;
        this.assetVersionToken = host.version() + "-" + Long.toString(System.currentTimeMillis(), 36);
    }

    public void install() {
        ConfigValues c = host.configValues();
        if (c == null) return;
        List<Path> roots = findWebRoots(c, true);
        if (!c.pluginEnabled || !c.liveAtlasEnabled) {
            uninstall(findWebRoots(c, false), c);
            return;
        }
        if (roots.isEmpty()) {
            if (c.liveAtlasWebRoot != null && !c.liveAtlasWebRoot.isBlank()) {
                host.logger().warn("Configured adapters.liveatlas.web-root does not contain a LiveAtlas index.html: " + c.liveAtlasWebRoot);
            } else {
                host.logger().info("LiveAtlas adapter is enabled, but no LiveAtlas web root was detected. Set adapters.liveatlas.web-root when LiveAtlas is hosted from a custom/shared directory.");
            }
            return;
        }

        for (Path root : roots) {
            Path index = root.resolve("index.html");
            try {
                if (c.liveAtlasAutoInstall) installAssets(root, c);
                if (c.liveAtlasAutoPatchIndex) patchIndex(index, c);
                host.logger().info("LiveAtlas KOKOTO WebChat frontend installed in " + root);
            } catch (IOException ex) {
                host.logger().warn("Failed to install KOKOTO WebChat into LiveAtlas web root " + root + ": " + ex.getMessage());
            }
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
                        host.logger().info("Removed KOKOTO WebChat block from disabled LiveAtlas adapter: " + index);
                    }
                } catch (IOException ex) {
                    host.logger().warn("Failed to remove disabled LiveAtlas adapter block from " + index + ": " + ex.getMessage());
                }
            }
            Path dir = root.resolve(canonicalAddonPath(c.liveAtlasAddonPath)).normalize();
            if (!Files.exists(dir) || !dir.startsWith(root.toAbsolutePath().normalize())) continue;
            try {
                deleteTree(dir);
                host.logger().info("Removed disabled LiveAtlas adapter assets: " + dir);
            } catch (IOException ex) {
                host.logger().warn("Failed to remove disabled LiveAtlas adapter assets " + dir + ": " + ex.getMessage());
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private List<Path> findWebRoots(ConfigValues c, boolean requireLiveAtlasMarker) {
        Set<String> seen = new LinkedHashSet<>();
        List<Path> roots = new ArrayList<>();
        boolean explicit = c.liveAtlasWebRoot != null && !c.liveAtlasWebRoot.isBlank();
        if (explicit) addRoot(roots, seen, Path.of(c.liveAtlasWebRoot), false);

        // LiveAtlas is a drop-in Dynmap frontend, so respect Dynmap's configured
        // custom webpath as an auto-detection candidate when configuration.txt exists.
        for (Path config : dynmapConfigCandidates()) {
            if (!Files.isRegularFile(config)) continue;
            String configured = readDynmapWebPath(config);
            if (configured == null || configured.isBlank()) continue;
            Path path = Path.of(configured);
            if (!path.isAbsolute() && config.getParent() != null) path = config.getParent().resolve(path);
            addRoot(roots, seen, path, true);
        }

        // LiveAtlas is commonly used as a drop-in replacement for Dynmap, but it can
        // also be hosted independently or in front of squaremap/Pl3xMap/Overviewer.
        List<Path> candidates = List.of(
                Path.of("liveatlas"),
                Path.of("live-atlas"),
                Path.of("LiveAtlas"),
                Path.of("web", "liveatlas"),
                Path.of("web", "live-atlas"),
                Path.of("plugins", "dynmap", "web"),
                Path.of("plugins", "Dynmap", "web"),
                Path.of("dynmap", "web"),
                Path.of("config", "dynmap", "web"),
                Path.of("config", "Dynmap", "web"),
                Path.of("plugins", "squaremap", "web"),
                Path.of("squaremap", "web"),
                Path.of("plugins", "Pl3xMap", "web"),
                Path.of("plugins", "pl3xmap", "web"),
                Path.of("pl3xmap", "web")
        );
        for (Path path : candidates) addRoot(roots, seen, path, true);

        roots.removeIf(root -> {
            Path index = root.resolve("index.html");
            if (!Files.isRegularFile(index)) return true;
            if (!requireLiveAtlasMarker) return !isLiveAtlasOrOwnedIndex(index);
            // Explicit paths are still checked: this prevents KWC from silently
            // patching a different map UI because of a typo in web-root.
            return !isLiveAtlasIndex(index);
        });
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

    private String readDynmapWebPath(Path config) {
        try {
            for (String raw : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                String line = stripComment(raw);
                Matcher m = DYNMAP_WEBPATH_LINE.matcher(line);
                if (m.matches()) return unquote(m.group(1).trim());
            }
        } catch (IOException ignored) {}
        return "";
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

    private void addRoot(List<Path> roots, Set<String> seen, Path path, boolean autoDetected) {
        if (path == null) return;
        Path normalized = path.toAbsolutePath().normalize();
        if (seen.add(normalized.toString())) roots.add(normalized);
    }

    static boolean isLiveAtlasIndex(Path index) {
        try {
            String html = Files.readString(index, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return html.contains("window.liveatlasconfig")
                    || html.contains("liveatlasconfig")
                    || html.contains("live-atlas/favicons")
                    || html.contains("minecraft dynamic map - liveatlas");
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isLiveAtlasOrOwnedIndex(Path index) {
        try {
            String html = Files.readString(index, StandardCharsets.UTF_8);
            return html.contains(BLOCK_START) || isLiveAtlasIndex(index);
        } catch (IOException ex) {
            return false;
        }
    }

    private void installAssets(Path root, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.liveAtlasAddonPath);
        Path dir = root.resolve(addon).normalize();
        if (!dir.startsWith(root.toAbsolutePath().normalize())) throw new IOException("addon-path escapes LiveAtlas web root");
        Files.createDirectories(dir);
        copyResource("liveatlas/chat.js", dir.resolve("chat.js"));
        copyResource("liveatlas/chat.css", dir.resolve("chat.css"));
        writeGeneratedConfig(dir.resolve("config.js"), c);
    }

    private void copyResource(String resource, Path out) throws IOException {
        try (InputStream in = host.resource(resource)) {
            if (in == null) throw new IOException("missing bundled resource " + resource);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeGeneratedConfig(Path out, ConfigValues c) throws IOException {
        String apiBase = c.liveAtlasApiBaseUrl == null ? "" : c.liveAtlasApiBaseUrl.trim();
        String js;
        if (apiBase.isEmpty()) {
            String direct = "location.protocol + '//' + location.hostname + ':" + c.httpPort + c.pathPrefix + "'";
            String publicApi = joinPublicPath(c.publicPrefix, c.pathPrefix);
            String auto = "(location.protocol === 'https:' ? location.origin + " + JsonUtil.quote(publicApi) + " : " + direct + ")";
            js = "window.KokotoWebChatConfig = { apiBase: " + auto + ", apiBaseUrl: " + auto + ", mapAdapter: 'liveatlas' };\n";
        } else {
            String normalized = normalizeConfiguredBrowserUrl(c, apiBase);
            js = "window.KokotoWebChatConfig = { apiBase: " + JsonUtil.quote(normalized)
                    + ", apiBaseUrl: " + JsonUtil.quote(normalized) + ", mapAdapter: 'liveatlas' };\n";
        }
        Files.writeString(out, js, StandardCharsets.UTF_8);
    }

    private void patchIndex(Path index, ConfigValues c) throws IOException {
        String addon = canonicalAddonPath(c.liveAtlasAddonPath);
        String version = assetVersionToken;
        String nl = System.lineSeparator();
        String block = BLOCK_START + nl
                + "<link rel=\"stylesheet\" data-kwc-liveatlas=\"style\" href=\"" + addon + "/chat.css?v=" + version + "\" />" + nl
                + "<script data-kwc-liveatlas=\"config\" src=\"" + addon + "/config.js?v=" + version + "\"></script>" + nl
                + "<script data-kwc-liveatlas=\"script\" src=\"" + addon + "/chat.js?v=" + version + "\"></script>" + nl
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
            if (end < 0) { out = out.substring(0, start); break; }
            end += BLOCK_END.length();
            while (end < out.length() && (out.charAt(end) == '\r' || out.charAt(end) == '\n')) end++;
            out = out.substring(0, start) + out.substring(end);
        }
        out = out.replaceAll("(?im)^\\s*<link[^>]*data-kwc-liveatlas[^>]*>\\s*(?:\\r?\\n)?", "");
        out = out.replaceAll("(?im)^\\s*<script[^>]*data-kwc-liveatlas[^>]*></script>\\s*(?:\\r?\\n)?", "");
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
