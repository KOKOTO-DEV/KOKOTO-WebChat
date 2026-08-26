package dev.kokoto.webchat.fabric;

import dev.kokoto.webchat.*;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class FabricLangManager implements WebChatLanguage {
    private static final String[] BUILTIN = {"en-US", "ko-KR", "ja-JP", "zh-CN"};

    private final KwcFabricRuntime plugin;
    private final Map<String, String> strings = new LinkedHashMap<>();

    public FabricLangManager(KwcFabricRuntime plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        installBuiltinFiles();
        strings.clear();
        loadInto(strings, fallbackLanguage());
        loadInto(strings, currentLanguage());
    }

    public Map<String, String> webStrings() {
        return new LinkedHashMap<>(strings);
    }

    public String text(String key, String fallback) {
        return text(key, fallback, null);
    }

    public String text(String key, String fallback, Map<String, String> values) {
        String value = strings.get(key);
        if (value == null) value = defaultKeys().getOrDefault(key, fallback == null ? key : fallback);
        if (values != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return value;
    }

    public String[] availableLanguages() {
        return BUILTIN.clone();
    }

    public Map<String, String> webStringsFor(String requestedLanguage) {
        Map<String, String> out = new LinkedHashMap<>();
        loadInto(out, fallbackLanguage());
        String lang = cleanLanguage(requestedLanguage, currentLanguage());
        loadInto(out, lang);
        return out;
    }

    public String currentLanguage() {
        String lang = plugin.configValues() == null ? "en-US" : plugin.configValues().uiLanguage;
        return cleanLanguage(lang, "en-US");
    }

    public String fallbackLanguage() {
        String lang = plugin.configValues() == null ? "en-US" : plugin.configValues().uiLanguageFallback;
        return cleanLanguage(lang, "en-US");
    }

    private String cleanLanguage(String lang, String fallback) {
        if (lang == null || lang.isBlank()) return fallback;
        return lang.trim().replace('/', '-').replace('\\', '-');
    }

    private void installBuiltinFiles() {
        File langDir = new File(plugin.dataDirectory().toFile(), "lang");
        if (!langDir.exists() && !langDir.mkdirs()) {
            plugin.warn("Failed to create language directory: " + langDir);
            return;
        }

        for (String lang : BUILTIN) {
            File target = new File(langDir, lang + ".yml");
            String resource = "lang/" + lang + ".yml";
            if (!target.exists()) {
                try (InputStream in = plugin.resource(resource)) {
                    if (in == null) continue;
                    Files.copy(in, target.toPath());
                } catch (IOException ex) {
                    plugin.warn("Failed to install language file " + resource + ": " + ex.getMessage());
                }
                continue;
            }

            updateBuiltinLanguageFile(target, resource);
        }
    }

    private void updateBuiltinLanguageFile(File target, String resource) {
        try (InputStream in = plugin.resource(resource)) {
            if (in == null) return;

            FabricYamlConfiguration existing = FabricYamlConfiguration.loadConfiguration(target);
            FabricYamlConfiguration builtin = FabricYamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            boolean changed = migrateLegacyBranding(existing, builtin);
            changed |= migrateChangedBuiltinStrings(target.getName().replace(".yml", ""), existing, builtin);

            for (String key : defaultKeys().keySet()) {
                String path = "web." + key;
                if (existing.get(path) == null && builtin.get(path) != null) {
                    existing.set(path, builtin.get(path));
                    changed = true;
                }
            }


            if (changed) {
                existing.save(target);
            }
        } catch (Exception ex) {
            plugin.warn("Failed to update built-in language keys in " + target + ": " + ex.getMessage());
        }
    }



    private boolean migrateChangedBuiltinStrings(String language, FabricYamlConfiguration existing, FabricYamlConfiguration builtin) {
        Map<String,String> previous = BuiltinLanguageRevision.previousValues(language);
        if (previous.isEmpty()) return false;
        boolean changed = false;
        for (Map.Entry<String,String> entry : previous.entrySet()) {
            String path = "web." + entry.getKey();
            String current = existing.getString(path, null);
            String latest = builtin.getString(path, null);
            if (current != null && latest != null && BuiltinLanguageRevision.isKnownPreviousValue(language, entry.getKey(), current) && !current.equals(latest)) {
                existing.set(path, latest);
                changed = true;
            }
        }
        return changed;
    }

    private boolean migrateLegacyBranding(FabricYamlConfiguration existing, FabricYamlConfiguration builtin) {
        boolean changed = false;
        String[][] legacyValues = {
                {"web.title.full", "BlueMap Chat", "BlueMap 채팅", "BlueMap チャット", "BlueMap 聊天"},
                {"web.admin.title", "BlueMap Chat Admin", "BlueMap 채팅 관리", "BlueMap チャット管理", "BlueMap 聊天管理"},
                {"web.login.title", "BlueMap Chat Login", "BlueMap 채팅 로그인", "BlueMap チャットログイン", "BlueMap 聊天登录"}
        };
        for (String[] row : legacyValues) {
            String path = row[0];
            String current = existing.getString(path, "");
            if (current == null || current.isBlank()) continue;
            boolean legacy = false;
            for (int i = 1; i < row.length; i++) {
                if (current.equals(row[i])) {
                    legacy = true;
                    break;
                }
            }
            if (!legacy) continue;
            String replacement = builtin.getString(path, null);
            if (replacement != null && !replacement.equals(current)) {
                existing.set(path, replacement);
                changed = true;
            }
        }
        return changed;
    }

    private void loadInto(Map<String, String> target, String lang) {
        File file = new File(new File(plugin.dataDirectory().toFile(), "lang"), lang + ".yml");
        if (!file.exists()) {
            plugin.warn("Language file not found: " + file + " (using built-in/default strings where available)");
            return;
        }

        FabricYamlConfiguration yml = FabricYamlConfiguration.loadConfiguration(file);
        for (String key : defaultKeys().keySet()) {
            String value = yml.getString("web." + key, null);
            if (value != null) target.put(key, value);
        }
        for (Map.Entry<String, String> e : defaultKeys().entrySet()) {
            target.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    private Map<String, String> defaultKeys() {
        Map<String, String> m = hardcodedDefaultKeys();

        try (InputStream in = plugin.resource("lang/en-US.yml")) {
            if (in == null) return m;
            FabricYamlConfiguration builtin = FabricYamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            FabricYamlSection web = builtin.getConfigurationSection("web");
            if (web == null) return m;

            for (String key : web.getKeys(true)) {
                Object value = builtin.get("web." + key);
                if (value instanceof String) {
                    m.put(key, (String) value);
                }
            }
        } catch (Exception ex) {
            plugin.warn("Failed to read built-in language defaults: " + ex.getMessage());
        }

        return m;
    }

    private Map<String, String> hardcodedDefaultKeys() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title.full", "KOKOTO WebChat");
        m.put("title.minimized", "Chat");
        m.put("status.connecting", "connecting...");
        m.put("status.guest", "guest");
        m.put("status.reconnecting", "reconnecting...");

        m.put("status.loggedIn", "logged in");
        m.put("media.youtube", "YouTube");
        m.put("media.youtubeTitle", "YouTube video");
        m.put("media.loadVideo", "▶ Video");
        m.put("media.loadAudio", "▶ Audio");
        m.put("command.onlyPlayers", "This command can only be used by players.");
        m.put("command.noPermission", "You do not have permission.");
        m.put("button.admin", "Admin");
        m.put("button.login", "Login");
        m.put("button.upload", "Attach");
        m.put("button.resize", "Resize");
        m.put("button.send", "Send");
        m.put("button.close", "Close");
        m.put("button.cancel", "Cancel");
        m.put("button.reset", "Reset");
        m.put("button.start", "Start");
        m.put("button.save", "Save");
        m.put("button.skip", "Skip");
        m.put("button.logout", "Logout");
        m.put("button.setPassword", "Set password");
        m.put("button.clearHistory", "Clear web history");
        m.put("button.mute", "Mute");
        m.put("button.unmute", "Unmute");
        m.put("button.revoke", "Revoke");
        m.put("button.delete", "delete");
        m.put("placeholder.message", "message");
        m.put("message.deleted", "[deleted]");
        m.put("sender.server", "Server");
        m.put("sender.command", "Command");
        m.put("sender.system", "Server");
        m.put("system.command-executed", "{executor} executed web command: {label}");
        m.put("system.command-executed-by", "{executor} executed web command: {label}");
        m.put("error.unknown", "Unknown error");
        return m;
    }
}
