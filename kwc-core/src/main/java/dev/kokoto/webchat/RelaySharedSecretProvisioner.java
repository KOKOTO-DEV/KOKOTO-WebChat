package dev.kokoto.webchat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provisions Relay v2 group shared secrets when an operator deliberately leaves
 * a group's shared-secret empty. Existing non-empty values are never changed.
 */
public final class RelaySharedSecretProvisioner {
    private RelaySharedSecretProvisioner() {}

    public static boolean provision(Path configFile, List<Map<?, ?>> rawGroups, CoreLogger logger) throws IOException {
        if (configFile == null || rawGroups == null || rawGroups.isEmpty()) return false;
        RelayConfigShapeValidator.requireValid(rawGroups);
        List<Object> updated = new ArrayList<>();
        boolean changed = false;
        int generated = 0;
        for (Map<?, ?> raw : rawGroups) {
            if (raw == null) continue;
            LinkedHashMap<String,Object> group = deepCopyMap(raw);
            Object rawId = group.get("id");
            Object rawSecret = group.get("shared-secret");
            String id = rawId == null ? "" : String.valueOf(rawId).trim();
            String secret = rawSecret == null ? "" : String.valueOf(rawSecret).trim();
            if (!id.isBlank() && secret.isBlank()) {
                group.put("shared-secret", SecurityUtil.randomToken(32));
                changed = true;
                generated++;
            }
            updated.add(group);
        }
        if (!changed) return false;
        ConfigTextEditor.setValues(configFile, Map.of("server-relay.groups", updated));
        if (logger != null) {
            logger.info("Generated " + generated + " Relay v2 group shared-secret value(s) and saved them to config.yml. "
                    + "Copy each generated secret to every server that belongs to the same Relay group. Secret values are not written to the log.");
        }
        return true;
    }

    private static LinkedHashMap<String,Object> deepCopyMap(Map<?, ?> raw) {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) continue;
            out.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
        }
        return out;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) return deepCopyMap(map);
        if (value instanceof List<?> list) {
            ArrayList<Object> out = new ArrayList<>();
            for (Object item : list) out.add(deepCopyValue(item));
            return out;
        }
        return value;
    }
}
