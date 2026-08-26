package dev.kokoto.webchat.adapter.bluemap;

import dev.kokoto.webchat.ConfigValues;
import dev.kokoto.webchat.JsonUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BlueMapAdapter {
    private final BlueMapAdapterHost host;
    private final String assetVersionToken;

    public BlueMapAdapter(BlueMapAdapterHost host) {
        this.host = host;
        this.assetVersionToken = buildAssetVersionToken(host.version());
    }

    private String buildAssetVersionToken(String version) {
        String nonce = Long.toString(System.currentTimeMillis(), 36);
        return version + "-" + nonce;
    }

    /**
     * Installs/registers the addon through BlueMapAPI's WebApp surface.
     * This path is used by Fabric/NeoForge and intentionally does not touch webapp.conf.
     */
    public void installApi(java.nio.file.Path webRoot, WebAppRegistration registration) {
        ConfigValues config = host.configValues();
        if (config == null || webRoot == null || registration == null) return;

        String addonPath = canonicalAddonPath(config.addonPath);
        java.nio.file.Path normalizedRoot = webRoot.toAbsolutePath().normalize();
        java.nio.file.Path normalizedAddon = normalizedRoot.resolve(addonPath).normalize();
        if (!normalizedAddon.startsWith(normalizedRoot) || normalizedAddon.equals(normalizedRoot)) {
            host.logger().warn("Rejected BlueMap addon-path outside the BlueMap web root: " + addonPath);
            return;
        }
        File addonDir = normalizedAddon.toFile();
        if (!config.pluginEnabled || !config.bluemapEnabled) {
            cleanupApiAddonDirectory(webRoot, addonPath);
            host.logger().info("BlueMap API adapter is disabled; no KOKOTO WebChat web assets were registered.");
            return;
        }

        if (config.webAutoInstall) {
            if (!addonDir.exists() && !addonDir.mkdirs()) {
                host.logger().warn("Failed to create BlueMap API addon directory: " + addonDir);
                return;
            }
            copyResource("web/chat.js", new File(addonDir, "chat.js"));
            copyResource("web/chat.css", new File(addonDir, "chat.css"));
            writeGeneratedConfig(new File(addonDir, "config.js"));
            cleanupLegacyApiAddonDirectory(webRoot, addonPath);
        } else {
            host.logger().info("BlueMap API addon auto-install is disabled; expecting web assets to be managed manually at " + addonDir.getPath());
        }

        String version = assetVersionToken;
        registration.registerScript(addonPath + "/config.js?v=" + version);
        registration.registerScript(addonPath + "/chat.js?v=" + version);
        registration.registerStyle(addonPath + "/chat.css?v=" + version);
        host.logger().info("Registered KOKOTO WebChat assets through BlueMapAPI (v=" + version + ", root=" + webRoot + ").");
    }

    private void cleanupApiAddonDirectory(java.nio.file.Path webRoot, String addonPath) {
        if (webRoot == null) return;
        Set<java.nio.file.Path> owned = new LinkedHashSet<>();
        owned.add(webRoot.resolve(canonicalAddonPath(addonPath)).normalize());
        owned.add(webRoot.resolve("addons/kokoto-web-chat").normalize());
        owned.add(webRoot.resolve("addons/bluemap-web-chat").normalize());
        java.nio.file.Path normalizedRoot = webRoot.toAbsolutePath().normalize();
        for (java.nio.file.Path dir : owned) {
            java.nio.file.Path normalized = dir.toAbsolutePath().normalize();
            if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) continue;
            try {
                if (Files.exists(normalized)) {
                    deleteTree(normalized);
                    host.logger().info("Removed disabled BlueMap API adapter assets: " + normalized);
                }
            } catch (IOException ex) {
                host.logger().warn("Failed to remove disabled BlueMap API adapter assets " + normalized + ": " + ex.getMessage());
            }
        }
    }

    private void cleanupLegacyApiAddonDirectory(java.nio.file.Path webRoot, String addonPath) {
        if (webRoot == null) return;
        java.nio.file.Path current = webRoot.resolve(canonicalAddonPath(addonPath)).toAbsolutePath().normalize();
        java.nio.file.Path legacy = webRoot.resolve("addons/bluemap-web-chat").toAbsolutePath().normalize();
        java.nio.file.Path root = webRoot.toAbsolutePath().normalize();
        if (legacy.equals(current) || !legacy.startsWith(root) || legacy.equals(root) || !Files.exists(legacy)) return;
        try {
            deleteTree(legacy);
            host.logger().info("Removed legacy BlueMapWebChat API addon directory: " + legacy);
        } catch (IOException ex) {
            host.logger().warn("Failed to remove legacy BlueMapWebChat API addon directory " + legacy + ": " + ex.getMessage());
        }
    }

    public interface WebAppRegistration {
        void registerScript(String url);
        void registerStyle(String url);
    }

    public void install() {
        ConfigValues config = host.configValues();
        if (config == null) return;
        if (!config.pluginEnabled || !config.bluemapEnabled) {
            uninstall(config);
            return;
        }

        if (config.webAutoInstall) {
            List<File> addonDirs = findAddonInstallDirs(config);
            if (addonDirs.isEmpty()) {
                host.logger().warn("No BlueMap web addon directory could be resolved. Check adapters.bluemap.bluemap-web-root.");
            }

            for (File addonDir : addonDirs) {
                if (!addonDir.exists() && !addonDir.mkdirs()) {
                    host.logger().warn("Failed to create BlueMap addon directory: " + addonDir);
                    continue;
                }

                copyResource("web/chat.js", new File(addonDir, "chat.js"));
                copyResource("web/chat.css", new File(addonDir, "chat.css"));
                writeGeneratedConfig(new File(addonDir, "config.js"));
                host.logger().info("BlueMap web addon files installed to " + addonDir.getPath());
            }
        } else {
            host.logger().info("BlueMap web addon auto-install is disabled; bundled web files were not copied.");
        }

        boolean patchedWebapp = false;
        if (config.webAutoPatch) {
            patchedWebapp = patchWebappConfs();
        } else {
            writeWebappConfExample(config);
            host.logger().info("BlueMap webapp.conf auto-patch is disabled; refreshed webapp.conf.example-additions only.");
        }

        // Delete the old BMWC addon only after the new assets were installed and an
        // actual BlueMap webapp.conf was successfully patched/recognized. This keeps
        // manual or unusual BlueMap layouts recoverable instead of deleting the last
        // working addon before a replacement is active.
        if (config.webAutoInstall && config.webAutoPatch && patchedWebapp) {
            cleanupLegacyAddonDirectories(findAddonInstallDirs(config));
        }
    }

    private void uninstall(ConfigValues config) {
        int cleanedConfs = 0;
        for (File conf : findWebappConfCandidates(config)) {
            if (!conf.isFile()) continue;
            try {
                String original = Files.readString(conf.toPath(), StandardCharsets.UTF_8);
                String cleaned = removeOldWebChatEntries(original, canonicalAddonPath(config.addonPath));
                if (!cleaned.equals(original)) {
                    Files.writeString(conf.toPath(), cleaned, StandardCharsets.UTF_8);
                    cleanedConfs++;
                    host.logger().info("Removed KOKOTO WebChat entries from disabled BlueMap adapter: " + conf.getPath());
                }
            } catch (IOException ex) {
                host.logger().warn("Failed to remove disabled BlueMap adapter entries from " + conf.getPath() + ": " + ex.getMessage());
            }
        }

        Set<String> seen = new LinkedHashSet<>();
        for (File addonDir : findAddonInstallDirs(config)) {
            if (addonDir == null) continue;
            File addonsDir = addonDir.getParentFile();
            if (addonsDir == null) continue;
            List<File> owned = new ArrayList<>();
            owned.add(addonDir);
            owned.add(new File(addonsDir, "kokoto-web-chat"));
            owned.add(new File(addonsDir, "bluemap-web-chat"));
            for (File dir : owned) {
                if (dir == null || !dir.exists()) continue;
                String key;
                try { key = dir.getCanonicalPath(); }
                catch (IOException ex) { key = dir.getAbsolutePath(); }
                if (!seen.add(key)) continue;
                try {
                    deleteTree(dir.toPath());
                    host.logger().info("Removed disabled BlueMap adapter assets: " + dir.getPath());
                } catch (IOException ex) {
                    host.logger().warn("Failed to remove disabled BlueMap adapter assets " + dir.getPath() + ": " + ex.getMessage());
                }
            }
        }

        File example = new File(host.dataDirectory().toFile(), "webapp.conf.example-additions");
        try { Files.deleteIfExists(example.toPath()); }
        catch (IOException ex) { host.logger().warn("Failed to remove disabled BlueMap adapter example file: " + ex.getMessage()); }

        if (cleanedConfs == 0) {
            host.logger().info("BlueMap adapter is disabled; no active KOKOTO WebChat webapp.conf entries were found.");
        }
    }

    private List<File> findAddonInstallDirs(ConfigValues config) {
        String addonPath = canonicalAddonPath(config.addonPath);
        List<File> webRootCandidates = new ArrayList<>();

        // Always honor the configured web-root. This is the only candidate we create even if it does not exist.
        addFileCandidate(webRootCandidates, new File(config.bluemapWebRoot));

        // BlueMap commonly serves static files from ./bluemap/web, while webapp.conf lives in ./plugins/BlueMap/.
        // Older configs used ./plugins/BlueMap/web. Update existing/common roots too so v= changes never point at stale files.
        addExistingFileCandidate(webRootCandidates, new File("bluemap/web"));
        addExistingFileCandidate(webRootCandidates, new File("plugins/BlueMap/web"));

        File webappConf = new File(config.bluemapWebappConf);
        File webappParent = webappConf.getParentFile();
        if (webappParent != null) {
            addExistingFileCandidate(webRootCandidates, new File(webappParent, "web"));
        }

        Set<String> seen = new LinkedHashSet<>();
        List<File> addonDirs = new ArrayList<>();
        for (int i = 0; i < webRootCandidates.size(); i++) {
            File webRoot = webRootCandidates.get(i);
            if (webRoot == null) continue;

            // Only create the configured root. Other candidates are updated only when they already exist.
            if (i != 0 && !webRoot.exists()) continue;

            File addonDir = new File(webRoot, addonPath);
            String key;
            try {
                key = addonDir.getCanonicalPath();
            } catch (IOException ex) {
                key = addonDir.getAbsolutePath();
            }
            if (seen.add(key)) {
                addonDirs.add(addonDir);
            }
        }
        return addonDirs;
    }

    private void addFileCandidate(List<File> files, File file) {
        if (file != null) files.add(file);
    }

    private void addExistingFileCandidate(List<File> files, File file) {
        if (file != null && file.exists()) files.add(file);
    }

    private void writeGeneratedConfig(File out) {
        ConfigValues c = host.configValues();
        String apiBase = c.apiBaseUrl == null ? "" : c.apiBaseUrl.trim();
        String js;
        if (apiBase.isEmpty()) {
            // With BlueMap behind HTTPS, a direct :8899 URL is normally blocked/unreachable.
            // Prefer the canonical same-origin chat path in that case. Plain HTTP
            // deployments retain the direct plugin-port behavior used by BMWC 4.x.
            String direct = "location.protocol + '//' + location.hostname + ':" + c.httpPort + c.pathPrefix + "'";
            String publicApi = joinPublicPath(c.publicPrefix, c.pathPrefix);
            String auto = "(location.protocol === 'https:' ? location.origin + " + JsonUtil.quote(publicApi) + " : " + direct + ")";
            js = "window.KokotoWebChatConfig = { apiBase: " + auto + ", apiBaseUrl: " + auto + " }; "
                    + "window.BlueMapWebChatConfig = window.KokotoWebChatConfig;\n";
        } else {
            apiBase = normalizeConfiguredBrowserUrl(c, apiBase);
            js = "window.KokotoWebChatConfig = { apiBase: " + JsonUtil.quote(apiBase) + ", apiBaseUrl: " + JsonUtil.quote(apiBase) + " }; "
                    + "window.BlueMapWebChatConfig = window.KokotoWebChatConfig;\n";
        }
        try {
            Files.writeString(out.toPath(), js, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            host.logger().warn("Failed to write config.js: " + ex.getMessage());
        }
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

    private void copyResource(String resource, File out) {
        try (InputStream in = host.resource(resource)) {
            if (in == null) {
                host.logger().warn("Missing bundled resource: " + resource);
                return;
            }
            Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            host.logger().warn("Failed to copy " + resource + ": " + ex.getMessage());
        }
    }

    private boolean patchWebappConfs() {
        ConfigValues c = host.configValues();
        writeWebappConfExample(c);

        List<File> confs = findWebappConfCandidates(c);
        boolean patchedAny = false;
        for (File conf : confs) {
            if (!conf.exists()) continue;
            if (patchSingleWebappConf(conf, c)) {
                patchedAny = true;
            }
        }

        if (!patchedAny) {
            File configured = new File(c.bluemapWebappConf);
            host.logger().warn("No existing BlueMap webapp.conf was patched. Checked configured path and common candidates. Manual example refreshed at "
                    + new File(host.dataDirectory().toFile(), "webapp.conf.example-additions").getPath()
                    + "; configured path is " + configured.getPath());
        }
        return patchedAny;
    }

    private List<File> findWebappConfCandidates(ConfigValues config) {
        List<File> candidates = new ArrayList<>();
        addFileCandidate(candidates, new File(config.bluemapWebappConf));
        addFileCandidate(candidates, new File("plugins/BlueMap/webapp.conf"));
        addFileCandidate(candidates, new File("bluemap/webapp.conf"));
        addFileCandidate(candidates, new File("bluemap/web/webapp.conf"));

        File configuredRoot = new File(config.bluemapWebRoot);
        File rootParent = configuredRoot.getParentFile();
        if (rootParent != null) {
            addFileCandidate(candidates, new File(rootParent, "webapp.conf"));
        }

        Set<String> seen = new LinkedHashSet<>();
        List<File> unique = new ArrayList<>();
        for (File file : candidates) {
            if (file == null) continue;
            String key;
            try {
                key = file.getCanonicalPath();
            } catch (IOException ex) {
                key = file.getAbsolutePath();
            }
            if (seen.add(key)) unique.add(file);
        }
        return unique;
    }

    private boolean patchSingleWebappConf(File conf, ConfigValues c) {
        String version = assetVersionToken;
        String addonPath = canonicalAddonPath(c.addonPath);
        String scriptConfig = addonPath + "/config.js?v=" + version;
        String scriptChat = addonPath + "/chat.js?v=" + version;
        String styleChat = addonPath + "/chat.css?v=" + version;

        try {
            String text = Files.readString(conf.toPath(), StandardCharsets.UTF_8);
            String withoutOldEntries = removeOldWebChatEntries(text, addonPath);
            String patched = ensureListEntries(withoutOldEntries, "scripts", scriptConfig, scriptChat);
            patched = ensureListEntries(patched, "styles", styleChat);
            if (!patched.equals(text)) {
                Files.writeString(conf.toPath(), patched, StandardCharsets.UTF_8);
                host.logger().info("Patched BlueMap webapp.conf with KOKOTO WebChat JS/CSS entries (v=" + assetVersionToken + "): " + conf.getPath());
            } else {
                host.logger().info("BlueMap webapp.conf already contains current KOKOTO WebChat entries (v=" + assetVersionToken + "): " + conf.getPath());
            }
            return true;
        } catch (IOException ex) {
            host.logger().warn("Failed to patch webapp.conf " + conf.getPath() + ": " + ex.getMessage());
            return false;
        }
    }

    private void writeWebappConfExample(ConfigValues c) {
        String version = assetVersionToken;
        String addonPath = canonicalAddonPath(c.addonPath);
        String scriptConfig = addonPath + "/config.js?v=" + version;
        String scriptChat = addonPath + "/chat.js?v=" + version;
        String styleChat = addonPath + "/chat.css?v=" + version;
        File example = new File(host.dataDirectory().toFile(), "webapp.conf.example-additions");
        String text = "scripts: [\n"
                + "  \"" + scriptConfig + "\",\n"
                + "  \"" + scriptChat + "\"\n"
                + "]\n\n"
                + "styles: [\n"
                + "  \"" + styleChat + "\"\n"
                + "]\n";
        try {
            Files.writeString(example.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            host.logger().warn("Failed to write webapp.conf.example-additions: " + ex.getMessage());
        }
    }

    private String removeOldWebChatEntries(String text, String addonPath) {
        // Remove both the BlueMapWebChat 4.x addon path and every current KWC path so
        // a rename/upgrade can never run two frontend copies at once.
        text = removeOldWebChatEntriesForPath(text, "addons/bluemap-web-chat");
        text = removeOldWebChatEntriesForPath(text, "addons/kokoto-web-chat");
        String configuredPath = normalizeAddonPath(addonPath);
        if (!configuredPath.equals("addons/kokoto-web-chat") && !configuredPath.equals("addons/bluemap-web-chat")) {
            text = removeOldWebChatEntriesForPath(text, configuredPath);
        }
        return text;
    }

    private String normalizeAddonPath(String addonPath) {
        String path = addonPath == null ? "addons/kokoto-web-chat" : addonPath.trim().replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path.isBlank() ? "addons/kokoto-web-chat" : path;
    }

    private String canonicalAddonPath(String addonPath) {
        String path = normalizeAddonPath(addonPath);
        if (path.equalsIgnoreCase("addons/bluemap-web-chat")) return "addons/kokoto-web-chat";
        return path;
    }

    private void cleanupLegacyAddonDirectories(List<File> addonDirs) {
        Set<String> seen = new LinkedHashSet<>();
        for (File addonDir : addonDirs) {
            if (addonDir == null) continue;
            File addonsDir = addonDir.getParentFile();
            if (addonsDir == null) continue;
            File legacy = new File(addonsDir, "bluemap-web-chat");
            File current = new File(addonsDir, "kokoto-web-chat");
            String legacyKey;
            try {
                legacyKey = legacy.getCanonicalPath();
                if (legacyKey.equals(current.getCanonicalPath())) continue;
            } catch (IOException ex) {
                legacyKey = legacy.getAbsolutePath();
                if (legacyKey.equals(current.getAbsolutePath())) continue;
            }
            if (!seen.add(legacyKey) || !legacy.exists()) continue;
            try {
                deleteTree(legacy.toPath());
                host.logger().info("Removed legacy BlueMapWebChat addon directory after KWC migration: " + legacy.getPath());
            } catch (IOException ex) {
                host.logger().warn("Failed to remove legacy BlueMapWebChat addon directory " + legacy.getPath() + ": " + ex.getMessage());
            }
        }
    }

    private void deleteTree(java.nio.file.Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            List<java.nio.file.Path> paths = walk.sorted(java.util.Comparator.reverseOrder()).toList();
            for (java.nio.file.Path path : paths) Files.deleteIfExists(path);
        }
    }

    private String removeOldWebChatEntriesForPath(String text, String addonPath) {
        String path = Pattern.quote(normalizeAddonPath(addonPath));
        String jsEntry = "\"" + path + "/(?:config|chat)\\.js(?:\\?v=[^\"]*)?\"";
        String cssEntry = "\"" + path + "/chat\\.css(?:\\?v=[^\"]*)?\"";
        text = removeQuotedListEntry(text, jsEntry);
        text = removeQuotedListEntry(text, cssEntry);
        // Remove only exact legacy/current addon ownership comments; user comments are untouched.
        text = text.replaceAll("(?m)^\\s*#\\s*Added by (?:BlueMapWebChat|KOKOTO WebChat)\\s*\\R", "");
        return text;
    }

    private String removeQuotedListEntry(String text, String quotedEntryRegex) {
        // First remove entries that own a following comma, then entries that are the
        // final element and therefore own the preceding comma. The final replacement
        // handles a single-element list. This works for both one-line and block lists.
        text = text.replaceAll(quotedEntryRegex + "\\s*,\\s*", "");
        text = text.replaceAll(",\\s*" + quotedEntryRegex, "");
        text = text.replaceAll(quotedEntryRegex, "");
        return text;
    }

    private String ensureListEntries(String text, String listName, String... entries) {
        boolean allPresent = true;
        for (String e : entries) {
            if (!text.contains("\"" + e + "\"")) {
                allPresent = false;
                break;
            }
        }
        if (allPresent) return text;

        Pattern p = Pattern.compile("(?s)(" + Pattern.quote(listName) + "\\s*:\\s*\\[)(.*?)(\\])");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String body = m.group(2);
            StringBuilder insert = new StringBuilder();
            for (String e : entries) {
                if (!body.contains("\"" + e + "\"")) {
                    insert.append("\n  \"").append(e).append("\",");
                }
            }
            String replacement = m.group(1) + insert + body + m.group(3);
            return text.substring(0, m.start()) + replacement + text.substring(m.end());
        }

        StringBuilder append = new StringBuilder(text);
        if (!text.endsWith("\n")) append.append('\n');
        append.append("\n# Added by KOKOTO WebChat\n").append(listName).append(": [\n");
        for (int i = 0; i < entries.length; i++) {
            append.append("  \"").append(entries[i]).append("\"");
            if (i + 1 < entries.length) append.append(',');
            append.append('\n');
        }
        append.append("]\n");
        return append.toString();
    }
}
