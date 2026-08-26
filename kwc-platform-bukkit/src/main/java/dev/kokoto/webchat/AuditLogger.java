package dev.kokoto.webchat;

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
