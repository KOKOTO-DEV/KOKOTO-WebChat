package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * RelaySharedSecretProvisioner는 서버간 Relay Protocol 2.x의 설정·호스트 계약·전송 데이터를 담당한다.
 * RelaySharedSecretProvisioner participates in configuration, host contracts, or transport data for Relay Protocol 2.x.
 *
 * Relay payload는 서버 경계를 넘으므로 origin/target/sender 식별과 capability negotiation을 신뢰 경계 안에서 다시 검증해야 한다.
 * Relay payloads cross a server trust boundary, so origin/target/sender identity and capability negotiation must be revalidated inside the trust boundary.
 */
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
