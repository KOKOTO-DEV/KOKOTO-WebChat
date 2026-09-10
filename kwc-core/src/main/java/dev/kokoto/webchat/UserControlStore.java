package dev.kokoto.webchat;

/* KWC 파일 안내 / KWC file guide
 * UserControlStore는 관리자/모데레이터가 설정하는 계정별 제재와 모데레이터 위임 권한을 저장한다.
 * UserControlStore persists per-account administrative restrictions and delegated moderator capabilities.
 *
 * 개인 사용자 차단은 UserPreferenceStore의 사용자 preference이며 이 저장소의 서버 제재와 의도적으로 분리한다.
 * Personal user blocking is a user preference in UserPreferenceStore and is intentionally separate from server moderation restrictions here.
 */
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class UserControlStore {
    public static final List<String> MODERATOR_CAPABILITIES = List.of(
            "view-online", "message-delete", "guest-mute", "pin-manage",
            "user-restrictions", "profile-avatar-delete", "content-filter-manage", "emoji-manage", "game-manage"
    );

    private final Path file;
    private final CoreLogger logger;

    public UserControlStore(Path dataDirectory, CoreLogger logger) {
        this.file = dataDirectory.resolve("moderation").resolve("user-controls.properties");
        this.logger = logger;
    }

    public synchronized Map<String,Object> restrictions(String uuid) {
        String id = normalizeUuid(uuid);
        Properties p = load();
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        out.put("chatBanned", bool(p, "user." + id + ".chatBanned", false));
        out.put("uploadBanned", bool(p, "user." + id + ".uploadBanned", false));
        return out;
    }

    public synchronized boolean setRestrictions(String uuid, boolean chatBanned, boolean uploadBanned) {
        String id = normalizeUuid(uuid);
        if (id.isBlank()) return false;
        Properties p = load();
        String base = "user." + id + ".";
        if (!chatBanned && !uploadBanned) {
            p.remove(base + "chatBanned");
            p.remove(base + "uploadBanned");
        } else {
            p.setProperty(base + "chatBanned", Boolean.toString(chatBanned));
            p.setProperty(base + "uploadBanned", Boolean.toString(uploadBanned));
        }
        return save(p);
    }

    public synchronized boolean chatBanned(String uuid) {
        String id = normalizeUuid(uuid);
        return !id.isBlank() && bool(load(), "user." + id + ".chatBanned", false);
    }

    public synchronized boolean uploadBanned(String uuid) {
        String id = normalizeUuid(uuid);
        return !id.isBlank() && bool(load(), "user." + id + ".uploadBanned", false);
    }

    public synchronized Map<String,Boolean> moderatorCapabilities(String uuid) {
        String id = normalizeUuid(uuid);
        Properties p = load();
        LinkedHashMap<String,Boolean> out = new LinkedHashMap<>();
        for (String capability : MODERATOR_CAPABILITIES) {
            boolean defaultValue = switch (capability) {
                case "view-online", "message-delete", "guest-mute", "pin-manage" -> true;
                default -> false;
            };
            out.put(capability, bool(p, "moderator." + id + "." + capability, defaultValue));
        }
        return out;
    }

    public synchronized boolean moderatorAllowed(String uuid, String capability) {
        if (!MODERATOR_CAPABILITIES.contains(capability)) return false;
        return Boolean.TRUE.equals(moderatorCapabilities(uuid).get(capability));
    }

    public synchronized boolean setModeratorCapabilities(String uuid, Map<String,Boolean> values) {
        String id = normalizeUuid(uuid);
        if (id.isBlank()) return false;
        Properties p = load();
        for (String capability : MODERATOR_CAPABILITIES) {
            if (!values.containsKey(capability)) continue;
            p.setProperty("moderator." + id + "." + capability, Boolean.toString(Boolean.TRUE.equals(values.get(capability))));
        }
        return save(p);
    }

    private Properties load() {
        Properties p = new Properties();
        if (!Files.isRegularFile(file)) return p;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(reader);
        } catch (IOException ex) {
            if (logger != null) logger.warn("Failed to load user controls: " + ex.getMessage());
        }
        return p;
    }

    private boolean save(Properties p) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                p.store(writer, "KOKOTO WebChat user controls");
            }
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException atomicUnsupported) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException ex) {
            if (logger != null) logger.warn("Failed to save user controls: " + ex.getMessage());
            return false;
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    public static String normalizeUuid(String raw) {
        return String.valueOf(raw == null ? "" : raw).trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._~:-]", "");
    }
}
