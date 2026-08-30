package dev.kokoto.webchat;

import java.util.List;
import java.util.Map;

/** Validates the structural YAML shape required by Relay v2 before config rewrite/runtime mapping. */
public final class RelayConfigShapeValidator {
    private RelayConfigShapeValidator() {}

    /** Returns an empty string when valid, otherwise an operator-facing error message. */
    public static String problem(Object rawGroups) {
        if (rawGroups == null) return "";
        if (!(rawGroups instanceof List<?> groups)) {
            return "server-relay.groups must be a YAML list. Prefix each group with '- id: ...'.";
        }
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            Object groupRaw = groups.get(groupIndex);
            if (!(groupRaw instanceof Map<?, ?> group)) {
                return "server-relay.groups[" + groupIndex + "] must be a YAML map/object.";
            }
            Object peersRaw = group.get("peers");
            if (peersRaw == null) continue;
            if (!(peersRaw instanceof List<?> peers)) {
                return "server-relay.groups[" + groupIndex + "].peers must be a YAML list. "
                        + "Prefix every peer with '- id: ...'; repeated id/url/enabled keys without '-' overwrite earlier peers.";
            }
            for (int peerIndex = 0; peerIndex < peers.size(); peerIndex++) {
                if (!(peers.get(peerIndex) instanceof Map<?, ?>)) {
                    return "server-relay.groups[" + groupIndex + "].peers[" + peerIndex + "] must be a YAML map/object.";
                }
            }
        }
        return "";
    }

    public static void requireValid(Object rawGroups) {
        String problem = problem(rawGroups);
        if (!problem.isBlank()) throw new IllegalArgumentException(problem);
    }
}
