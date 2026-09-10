package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * AuditLogger는 Bukkit/Paper 런타임에서 core 기능을 실제 Bukkit API와 연결하는 플랫폼 구현 코드다.
 * AuditLogger is Bukkit/Paper platform implementation code connecting core features to concrete Bukkit APIs.
 *
 * 같은 기능의 Fabric/Forge/NeoForge 구현과 의미가 달라지지 않도록 platform-specific 차이는 이 계층 안에만 가둔다.
 * Keep platform-specific differences inside this layer so behavior stays semantically aligned with Fabric/Forge/NeoForge.
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuditLogger {
    private AuditLogger() {}

    public static void log(KokotoWebChatPlugin plugin, String action, String actor, Map<String, ?> details) {
        if (plugin == null || action == null || action.isBlank()) return;
        ConfigValues config = plugin.configValues();
        if (config != null && !config.auditEnabled) return;
        try {
            String dirText = config == null ? "audit" : String.valueOf(config.auditDirectory == null || config.auditDirectory.isBlank() ? "audit" : config.auditDirectory);
            Path dir = Path.of(dirText);
            if (!dir.isAbsolute()) dir = plugin.getDataFolder().toPath().resolve(dir);
            Files.createDirectories(dir.normalize());
            ZoneId zone = ZoneId.systemDefault();
            String fileName = LocalDate.now(zone).format(DateTimeFormatter.ISO_LOCAL_DATE) + ".log";
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("time", System.currentTimeMillis());
            row.put("action", action);
            row.put("actor", actor == null ? "" : actor);
            if (details != null) row.putAll(details);
            Files.writeString(dir.resolve(fileName), JsonUtil.obj(row) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to write audit log: " + ex.getMessage());
        }
    }
}
