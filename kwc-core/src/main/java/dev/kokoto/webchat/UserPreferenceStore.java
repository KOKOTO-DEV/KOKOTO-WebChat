package dev.kokoto.webchat;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Loader-neutral per-account chat preferences.
 *
 * Device-local state (window geometry, minimized state, Push endpoint, etc.) is
 * intentionally never stored here. Visual profiles are portable across KWC
 * servers through a strict flat JSON export/import format, while notification
 * choices and keyword alerts are account-global on each server.
 */
public final class UserPreferenceStore {
    public static final int PROFILE_FORMAT_VERSION = 1;
    public static final int MAX_IMPORT_BYTES = 16 * 1024;
    private static final int MAX_PROFILE_NAME = 40;
    private static final int MAX_FONT_FAMILY = 160;
    private static final int MAX_SHADOW = 120;
    private static final int MAX_KEYWORD_TEXT = 4096;

    private static final List<String> PROFILE_FIELDS = List.of(
            "theme", "opacity", "fontSize", "fontFamily", "textColor", "uiTextColor",
            "textShadowMode", "textShadowCustom", "backgroundColor", "inputBackgroundColor", "language"
    );
    private static final Set<String> IMPORT_FIELDS = Set.of(
            "format", "formatVersion", "name", "theme", "opacity", "fontSize", "fontFamily",
            "textColor", "uiTextColor", "textShadowMode", "textShadowCustom", "backgroundColor",
            "inputBackgroundColor", "language"
    );
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x1f\\x7f]");
    private static final Pattern UNSAFE_FONT = Pattern.compile("(?i)(?:url\\s*\\(|var\\s*\\(|expression\\s*\\(|@import|[{};<>\\\\])");
    private static final Pattern SAFE_SHADOW = Pattern.compile("^[#a-zA-Z0-9(),.%\\s+\\-]*$");

    private final Path root;
    private final CoreLogger logger;

    public UserPreferenceStore(Path dataDirectory, CoreLogger logger) {
        this.root = dataDirectory.resolve("user-preferences");
        this.logger = logger;
    }

    public synchronized List<Map<String,Object>> listProfiles(Account account, int maxProfiles) {
        Properties p = load(account);
        ArrayList<Map<String,Object>> out = new ArrayList<>();
        for (String id : profileIds(p)) {
            Map<String,Object> profile = profileFromProperties(p, id);
            if (!profile.isEmpty()) out.add(profile);
            if (maxProfiles > 0 && out.size() >= maxProfiles) break;
        }
        return out;
    }

    public synchronized Map<String,Object> getProfile(Account account, String id) {
        String safeId = normalizeProfileId(id);
        if (safeId.isBlank()) return Map.of();
        return profileFromProperties(load(account), safeId);
    }

    public synchronized SaveResult saveProfile(Account account, String id, String name,
                                                Map<String,String> raw, int maxProfiles) {
        if (maxProfiles <= 0) return SaveResult.error("profiles_disabled");
        String profileName;
        Map<String,Object> data;
        try {
            profileName = validateProfileName(name);
            data = validateProfileData(raw);
        } catch (IllegalArgumentException ex) {
            return SaveResult.error(ex.getMessage());
        }
        Properties p = load(account);
        ArrayList<String> ids = profileIds(p);
        String safeId = normalizeProfileId(id);
        boolean existing = !safeId.isBlank() && ids.contains(safeId);
        if (!existing) {
            if (ids.size() >= maxProfiles) return SaveResult.error("profile_limit");
            safeId = UUID.randomUUID().toString();
            ids.add(0, safeId);
        } else {
            ids.remove(safeId);
            ids.add(0, safeId);
        }
        String base = "profile." + safeId + ".";
        clearPrefix(p, base);
        p.setProperty(base + "name", profileName);
        p.setProperty(base + "savedAt", Long.toString(System.currentTimeMillis()));
        for (String field : PROFILE_FIELDS) p.setProperty(base + field, encodeValue(data.get(field)));
        p.setProperty("profile.ids", String.join(",", ids));
        if (!save(account, p)) return SaveResult.error("profile_save_failed");
        return new SaveResult(true, "", profileFromProperties(p, safeId));
    }

    public synchronized boolean deleteProfile(Account account, String id) {
        String safeId = normalizeProfileId(id);
        if (safeId.isBlank()) return false;
        Properties p = load(account);
        ArrayList<String> ids = profileIds(p);
        if (!ids.remove(safeId)) return false;
        clearPrefix(p, "profile." + safeId + ".");
        p.setProperty("profile.ids", String.join(",", ids));
        return save(account, p);
    }

    public synchronized ExportResult exportProfile(Account account, String id) {
        Map<String,Object> profile = getProfile(account, id);
        if (profile.isEmpty()) return ExportResult.error("profile_not_found");
        LinkedHashMap<String,Object> data = new LinkedHashMap<>();
        data.put("format", "KWC-user-profile");
        data.put("formatVersion", PROFILE_FORMAT_VERSION);
        data.put("name", profile.get("name"));
        for (String field : PROFILE_FIELDS) data.put(field, profile.get(field));
        return new ExportResult(true, "", JsonUtil.obj(data));
    }

    public synchronized SaveResult importProfile(Account account, String json, int maxProfiles) {
        if (maxProfiles <= 0) return SaveResult.error("profiles_disabled");
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_IMPORT_BYTES) {
            return SaveResult.error("profile_import_too_large");
        }
        final Map<String,Object> parsed;
        try {
            parsed = StrictFlatJson.parse(json);
        } catch (IllegalArgumentException ex) {
            return SaveResult.error(ex.getMessage());
        }
        if (!IMPORT_FIELDS.containsAll(parsed.keySet()) || parsed.keySet().stream().anyMatch(k -> !IMPORT_FIELDS.contains(k))) {
            return SaveResult.error("profile_import_unknown_field");
        }
        if (!"KWC-user-profile".equals(parsed.get("format"))) return SaveResult.error("profile_import_format");
        Object version = parsed.get("formatVersion");
        if (!(version instanceof Number) || ((Number)version).intValue() != PROFILE_FORMAT_VERSION) {
            return SaveResult.error("profile_import_version");
        }
        if (!(parsed.get("name") instanceof String)) return SaveResult.error("profile_import_type");
        LinkedHashMap<String,String> raw = new LinkedHashMap<>();
        for (String field : PROFILE_FIELDS) {
            Object value = parsed.get(field);
            if (value == null) {
                raw.put(field, "");
                continue;
            }
            // Keep the import format strict: visual text fields must remain JSON
            // strings, while the two numeric fields must remain JSON numbers.
            // Do not coerce booleans/objects into strings such as "true".
            if ("opacity".equals(field) || "fontSize".equals(field)) {
                if (!(value instanceof Number)) return SaveResult.error("profile_import_type");
            } else if (!(value instanceof String)) {
                return SaveResult.error("profile_import_type");
            }
            raw.put(field, String.valueOf(value));
        }
        return saveProfile(account, "", String.valueOf(parsed.get("name")), raw, maxProfiles);
    }

    public synchronized Map<String,Object> notificationPreferences(Account account, ConfigValues c) {
        Properties p = load(account);
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        out.put("configured", Boolean.parseBoolean(p.getProperty("notify.configured", "false")));
        out.put("normalChat", bool(p, "notify.normalChat", c == null || c.browserNotificationsNotifyNormalChat));
        out.put("dm", bool(p, "notify.dm", c == null || c.browserNotificationsNotifyDm));
        out.put("groupChat", bool(p, "notify.groupChat", c == null || c.browserNotificationsNotifyGroupChat));
        out.put("mentions", bool(p, "notify.mentions", c == null || c.browserNotificationsNotifyMentions));
        out.put("replies", bool(p, "notify.replies", c == null || c.browserNotificationsNotifyReplies));
        String systemMode = p.getProperty("notify.systemMode", c == null || c.browserNotificationsNotifySystem ? "all" : "off");
        if (!Set.of("all", "join-leave", "off").contains(systemMode)) systemMode = "all";
        out.put("systemMode", systemMode);
        out.put("system", !"off".equals(systemMode));
        out.put("keywords", bool(p, "notify.keywords", c == null || c.browserNotificationsNotifyKeywords));
        out.put("preview", bool(p, "notify.preview", c == null || c.browserNotificationsShowMessagePreview));
        out.put("keywordText", normalizeKeywordText(p.getProperty("notify.keywordText", "")));
        return out;
    }

    public synchronized SaveResult saveNotificationPreferences(Account account, Map<String,String> raw, ConfigValues c) {
        Properties p = load(account);
        // Removed in 5.0.0 final notification UI: keep old profile files readable but
        // drop the obsolete per-account own-message preference on the next save.
        p.remove("notify.ownMessages");
        try {
            setBool(p, "notify.normalChat", raw, "normalChat", c == null || c.browserNotificationsNotifyNormalChat);
            setBool(p, "notify.dm", raw, "dm", c == null || c.browserNotificationsNotifyDm);
            setBool(p, "notify.groupChat", raw, "groupChat", c == null || c.browserNotificationsNotifyGroupChat);
            setBool(p, "notify.mentions", raw, "mentions", c == null || c.browserNotificationsNotifyMentions);
            setBool(p, "notify.replies", raw, "replies", c == null || c.browserNotificationsNotifyReplies);
            String mode = raw.getOrDefault("systemMode", p.getProperty("notify.systemMode", "all")).trim().toLowerCase(Locale.ROOT);
            if (!Set.of("all", "join-leave", "off").contains(mode)) throw new IllegalArgumentException("invalid_notification_system_mode");
            if (c != null && !c.browserNotificationsNotifySystem) mode = "off";
            p.setProperty("notify.systemMode", mode);
            setBool(p, "notify.keywords", raw, "keywords", c == null || c.browserNotificationsNotifyKeywords);
            setBool(p, "notify.preview", raw, "preview", c == null || c.browserNotificationsShowMessagePreview);
            if (raw.containsKey("keywordText")) p.setProperty("notify.keywordText", normalizeKeywordText(raw.get("keywordText")));
            p.setProperty("notify.configured", "true");
        } catch (IllegalArgumentException ex) {
            return SaveResult.error(ex.getMessage());
        }
        if (!save(account, p)) return SaveResult.error("notification_preferences_save_failed");
        return new SaveResult(true, "", notificationPreferences(account, c));
    }

    private Map<String,Object> profileFromProperties(Properties p, String id) {
        String base = "profile." + id + ".";
        String name = p.getProperty(base + "name", "").trim();
        if (name.isBlank()) return Map.of();
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", name);
        out.put("savedAt", parseLong(p.getProperty(base + "savedAt"), 0L));
        LinkedHashMap<String,String> raw = new LinkedHashMap<>();
        for (String field : PROFILE_FIELDS) raw.put(field, p.getProperty(base + field, ""));
        try { out.putAll(validateProfileData(raw)); }
        catch (IllegalArgumentException ex) { return Map.of(); }
        return out;
    }

    private Map<String,Object> validateProfileData(Map<String,String> raw) {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        String theme = clean(raw.get("theme"), 32).toLowerCase(Locale.ROOT);
        if (!Set.of("", "system", "dark", "light", "high-contrast").contains(theme)) throw new IllegalArgumentException("invalid_profile_theme");
        out.put("theme", theme);
        // Numeric visual preferences are nullable. Null means "follow the current
        // server/default value" and must remain distinct from explicitly saving that
        // numeric default so profiles round-trip exactly across servers/devices.
        out.put("opacity", nullableNumber(raw.get("opacity"), 0.10, 1.0));
        out.put("fontSize", nullableNumber(raw.get("fontSize"), 8.0, 36.0));
        String font = clean(raw.get("fontFamily"), MAX_FONT_FAMILY);
        if (!font.isBlank() && UNSAFE_FONT.matcher(font).find()) throw new IllegalArgumentException("invalid_profile_font");
        out.put("fontFamily", font);
        out.put("textColor", color(raw.get("textColor")));
        out.put("uiTextColor", color(raw.get("uiTextColor")));
        String shadowMode = clean(raw.get("textShadowMode"), 16).toLowerCase(Locale.ROOT);
        // Blank is meaningful: it means no user override, so the receiving server's
        // current ui.text-shadow mode remains authoritative. Do not coerce blank to auto.
        if (!Set.of("", "none", "auto", "dark", "light", "custom").contains(shadowMode)) throw new IllegalArgumentException("invalid_profile_shadow_mode");
        out.put("textShadowMode", shadowMode);
        String shadow = clean(raw.get("textShadowCustom"), MAX_SHADOW);
        if (!shadow.isBlank() && (!SAFE_SHADOW.matcher(shadow).matches() || shadow.toLowerCase(Locale.ROOT).contains("url("))) {
            throw new IllegalArgumentException("invalid_profile_shadow");
        }
        out.put("textShadowCustom", shadow);
        out.put("backgroundColor", color(raw.get("backgroundColor")));
        out.put("inputBackgroundColor", color(raw.get("inputBackgroundColor")));
        String language = clean(raw.get("language"), 16);
        if (!Set.of("", "ko-KR", "en-US", "ja-JP", "zh-CN").contains(language)) throw new IllegalArgumentException("invalid_profile_language");
        out.put("language", language);
        return out;
    }

    private String validateProfileName(String raw) {
        String name = clean(raw, MAX_PROFILE_NAME);
        if (name.isBlank()) throw new IllegalArgumentException("profile_name_required");
        if (name.indexOf('<') >= 0 || name.indexOf('>') >= 0) throw new IllegalArgumentException("invalid_profile_name");
        return name;
    }

    private String normalizeKeywordText(String raw) {
        String text = raw == null ? "" : raw;
        if (text.length() > MAX_KEYWORD_TEXT) text = text.substring(0, MAX_KEYWORD_TEXT);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayList<String> out = new ArrayList<>();
        for (String item : text.split("[\\r\\n,]+")) {
            String keyword = CONTROL.matcher(item).replaceAll("").trim();
            if (keyword.isBlank()) continue;
            if (keyword.indexOf('<') >= 0 || keyword.indexOf('>') >= 0) continue;
            if (keyword.length() > 80) keyword = keyword.substring(0, 80);
            String key = keyword.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            out.add(keyword);
            if (out.size() >= 40) break;
        }
        return String.join("\n", out);
    }

    private void setBool(Properties p, String prop, Map<String,String> raw, String field, boolean allowed) {
        if (!raw.containsKey(field)) return;
        boolean value = parseBoolean(raw.get(field));
        p.setProperty(prop, Boolean.toString(allowed && value));
    }

    private boolean bool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }

    private boolean parseBoolean(String raw) {
        String v = String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT);
        if (Set.of("true", "1", "yes", "on").contains(v)) return true;
        if (Set.of("false", "0", "no", "off", "").contains(v)) return false;
        throw new IllegalArgumentException("invalid_boolean");
    }

    private Double nullableNumber(String raw, double min, double max) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) return null;
        final double v;
        try { v = Double.parseDouble(raw.trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("invalid_profile_number"); }
        if (!Double.isFinite(v) || v < min || v > max) throw new IllegalArgumentException("invalid_profile_number");
        return v;
    }

    private String color(String raw) {
        String c = clean(raw, 16);
        if (c.isBlank()) return "";
        if (c.matches("^#[0-9a-fA-F]{3}$")) {
            char a = c.charAt(1), b = c.charAt(2), d = c.charAt(3);
            c = "#" + a + a + b + b + d + d;
        }
        if (!HEX.matcher(c).matches()) throw new IllegalArgumentException("invalid_profile_color");
        return c.toLowerCase(Locale.ROOT);
    }

    private String clean(String raw, int max) {
        String s = CONTROL.matcher(String.valueOf(raw == null ? "" : raw)).replaceAll("").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String normalizeProfileId(String id) {
        String s = String.valueOf(id == null ? "" : id).trim().toLowerCase(Locale.ROOT);
        return s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}") ? s : "";
    }

    private ArrayList<String> profileIds(Properties p) {
        ArrayList<String> out = new ArrayList<>();
        for (String id : p.getProperty("profile.ids", "").split(",")) {
            String safe = normalizeProfileId(id);
            if (!safe.isBlank() && !out.contains(safe)) out.add(safe);
        }
        return out;
    }

    private void clearPrefix(Properties p, String prefix) {
        ArrayList<String> keys = new ArrayList<>(p.stringPropertyNames());
        for (String key : keys) if (key.startsWith(prefix)) p.remove(key);
    }

    private String encodeValue(Object value) {
        if (value == null) return "";
        if (value instanceof Double d) {
            if (d == Math.rint(d)) return Long.toString((long)d.doubleValue());
        }
        return String.valueOf(value);
    }

    private Properties load(Account account) {
        Properties p = new Properties();
        Path file = fileFor(account);
        if (!Files.isRegularFile(file)) return p;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        catch (Exception ex) { if (logger != null) logger.warn("Failed to read user preferences: " + ex.getMessage()); }
        return p;
    }

    private boolean save(Account account, Properties p) {
        try {
            Files.createDirectories(root);
            Path file = fileFor(account);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                p.store(w, "KOKOTO WebChat per-account preferences");
            }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException atomicUnsupported) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception ex) {
            if (logger != null) logger.warn("Failed to save user preferences: " + ex.getMessage());
            return false;
        }
    }

    private Path fileFor(Account account) {
        String identity = account == null ? "unknown" : (account.uuid != null && !account.uuid.isBlank() ? account.uuid : account.id);
        if (identity == null || identity.isBlank()) identity = account == null ? "unknown" : account.safeUsername();
        return root.resolve(sha256(identity.toLowerCase(Locale.ROOT)) + ".properties");
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private long parseLong(String value, long fallback) {
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ex) { return fallback; }
    }

    public record SaveResult(boolean ok, String error, Map<String,Object> value) {
        public static SaveResult error(String error) { return new SaveResult(false, error, Map.of()); }
    }
    public record ExportResult(boolean ok, String error, String json) {
        public static ExportResult error(String error) { return new ExportResult(false, error, ""); }
    }

    /** Strict flat JSON parser used only for imported user-profile files. */
    private static final class StrictFlatJson {
        static Map<String,Object> parse(String json) {
            if (json == null) throw new IllegalArgumentException("profile_import_invalid_json");
            Parser p = new Parser(json);
            LinkedHashMap<String,Object> out = p.object();
            p.ws();
            if (!p.end()) throw new IllegalArgumentException("profile_import_invalid_json");
            return out;
        }
        private static final class Parser {
            final String s; int i;
            Parser(String s) { this.s=s; }
            boolean end(){return i>=s.length();}
            char ch(){return end()?0:s.charAt(i);}
            void ws(){while(!end()&&Character.isWhitespace(ch()))i++;}
            LinkedHashMap<String,Object> object(){
                ws(); if(ch()!='{') fail(); i++;
                LinkedHashMap<String,Object> m=new LinkedHashMap<>(); ws();
                if(ch()=='}'){i++;return m;}
                while(true){
                    ws(); String k=string(); if(m.containsKey(k)) throw new IllegalArgumentException("profile_import_duplicate_field");
                    ws(); if(ch()!=':') fail(); i++; ws();
                    Object v=value(); m.put(k,v); ws();
                    if(ch()=='}'){i++;return m;}
                    if(ch()!=',') fail(); i++;
                }
            }
            Object value(){
                ws(); char c=ch();
                if(c=='"') return string();
                if(c=='{'||c=='[') throw new IllegalArgumentException("profile_import_nested_value");
                if(s.startsWith("true",i)){i+=4;return Boolean.TRUE;}
                if(s.startsWith("false",i)){i+=5;return Boolean.FALSE;}
                if(s.startsWith("null",i)){i+=4;return null;}
                int st=i; if(c=='-')i++;
                boolean digit=false; while(Character.isDigit(ch())){digit=true;i++;}
                if(ch()=='.'){i++;while(Character.isDigit(ch())){digit=true;i++;}}
                if(ch()=='e'||ch()=='E'){i++;if(ch()=='+'||ch()=='-')i++;boolean exp=false;while(Character.isDigit(ch())){exp=true;i++;}if(!exp)fail();}
                if(!digit) fail();
                try{return Double.valueOf(s.substring(st,i));}catch(Exception ex){fail();return null;}
            }
            String string(){
                if(ch()!='"') fail(); i++; StringBuilder b=new StringBuilder();
                while(!end()){
                    char c=s.charAt(i++); if(c=='"') return b.toString();
                    if(c<' ') fail();
                    if(c!='\\'){b.append(c);continue;}
                    if(end())fail(); char e=s.charAt(i++);
                    switch(e){case '"'->b.append('"');case '\\'->b.append('\\');case '/'->b.append('/');case 'b'->b.append('\b');case 'f'->b.append('\f');case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'u'->{if(i+4>s.length())fail();String h=s.substring(i,i+4);i+=4;try{b.append((char)Integer.parseInt(h,16));}catch(Exception x){fail();}}default->fail();}
                } fail(); return "";
            }
            void fail(){throw new IllegalArgumentException("profile_import_invalid_json");}
        }
    }
}
