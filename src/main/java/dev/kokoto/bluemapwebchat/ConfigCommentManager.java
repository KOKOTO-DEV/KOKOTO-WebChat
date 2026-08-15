package dev.kokoto.bluemapwebchat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refreshes only bundled config comment text that is still unchanged from an
 * older BlueMapWebChat default. YAML setting values are never modified here.
 */
final class ConfigCommentManager {
    private static final Pattern BUNDLED_HEADER = Pattern.compile(
            "(?m)^# BlueMapWebChat \\d+\\.\\d+\\.\\d+ configuration[ \\t]*$");

    private static final String REVIEW_OLD = """
# Configuration review marker. Existing config.yml files are never overwritten on update.
# If this value differs from the running plugin version, BlueMapWebChat creates
# config-migration-<version>.yml containing copy-ready missing settings, changed defaults, and the target config-version marker.
# The file is created even when config-version is the only required change.
# When the versions match, the config is treated as already reviewed and comparison is skipped.
""".stripTrailing();

    private static final String REVIEW_PRE_REFERENCE = """
# Configuration review marker. Existing setting values are never overwritten on update.
# If this value differs from the running plugin version, BlueMapWebChat creates
# config-migration-<version>.yml containing copy-ready missing settings, changed defaults, and the target config-version marker.
# The file is created even when config-version is the only required change.
# Bundled comment text may be refreshed when it still exactly matches an older built-in
# comment block. Custom comments and all configured values are preserved.
# When the versions match, the config is treated as already reviewed and comparison is skipped.
""".stripTrailing();

    private static final String REVIEW_PRE_ORDER = """
# Configuration review marker. Existing setting values are never overwritten on update.
# BlueMapWebChat always writes config-reference-<version>.yml as a complete copy of the
# current bundled default config, including all comments, so old configs can be compared directly.
# If this value differs from the running plugin version, BlueMapWebChat also creates
# config-migration-<version>.yml containing copy-ready missing settings, changed defaults, and the target config-version marker.
# The migration file is created even when config-version is the only required change.
# Bundled comment text may be refreshed when it still exactly matches an older built-in
# comment block. Custom comments and all configured values are preserved.
# When the versions match, the config is treated as already reviewed and migration comparison is skipped.
""".stripTrailing();

    private static final String REVIEW_CURRENT = """
# Configuration review marker. Existing setting values are never overwritten on update.
# BlueMapWebChat always writes config-reference-<version>.yml as a complete copy of the
# current bundled default config, including all comments, so old configs can be compared directly.
# On startup/reload, known top-level config blocks are reordered to match the current bundled layout.
# Existing block text, setting values, and custom comments are preserved; unknown top-level blocks stay last.
# If this value differs from the running plugin version, BlueMapWebChat also creates
# config-migration-<version>.yml containing copy-ready missing settings, changed defaults, and the target config-version marker.
# The migration file is created even when config-version is the only required change.
# Bundled comment text may be refreshed when it still exactly matches an older built-in
# comment block. Custom comments and all configured values are preserved.
# When the versions match, the config is treated as already reviewed and migration comparison is skipped.
""".stripTrailing();

    private static final String UPDATE_OLD = """
# Check Modrinth for a newer stable release and notify the console and online administrators.
# The request interval, release channel, join delay, and download links use built-in defaults.
""".stripTrailing();

    private static final String UPDATE_CURRENT = """
# Check Modrinth for a newer stable release and notify the console and eligible in-game administrators.
# In-game notices are shown to OPs and players with bluemapwebchat.update.notify.
# Checks run after startup and on the built-in schedule; an eligible administrator login can refresh stale release state.
# Modrinth is the version source. Notices include clickable Modrinth and CurseForge download pages.
# HTTP/API failures are logged to the server console. Timing, release channel, and URLs use built-in defaults.
""".stripTrailing();

    private static final String DM_OLD = """
  # Thread-style 1:1 direct messages.
  # Only players that have joined or linked at least once and have a stored UUID/name can be selected.
  # Messages are stored by UUID, while the UI shows display name (real account name).
  # This feature uses its own private message store and is disabled by default because it stores private messages.
""".stripTrailing();

    private static final String DM_CURRENT = """
  # Thread-style 1:1 direct messages.
  # Only players that have joined or linked at least once and have a stored UUID/name can be selected.
  # An unqualified name resolves only on the current server. Cross-server targets must carry
  # an explicit server-scoped identity from name@server-id or the web UI's remote-player metadata.
  # Remote identity is server ID + player UUID; cross-server delivery/read receipts use server-relay.
  # Messages use the private DM store and this feature is disabled by default because it stores private messages.
""".stripTrailing();

    private static final String GROUP_OLD = """
# Group chat rooms.
# This is disabled by default because it stores multi-user private messages.
# Enable only after reviewing retention, room limits, and password policy.
""".stripTrailing();

    private static final String GROUP_CURRENT = """
# Group chat rooms are stored locally on this BlueMapWebChat server.
# The web UI tracks send failure/retry state and per-message unread-recipient counts.
# This is disabled by default because it stores multi-user private messages.
# Enable only after reviewing retention, room limits, password policy, and optional administrator audit access.
""".stripTrailing();

    private static final String RELAY_OLD = """
# Optional server-to-server public chat relay.
# Each server runs the same plugin and sends signed HTTP POST requests to configured peers.
# Configure a unique server-id on every server and use the same shared-secret on both sides,
# or set a per-peer secret. Peer URL is the other server's BMChat API base URL;
# /relay/receive is appended automatically. Example: http://10.0.0.2:8899/api
# For more than two servers, configure a full mesh or a hub topology. relayId de-duplication
# and hop limits prevent messages from looping when peers form a cycle.
""".stripTrailing();

    private static final String RELAY_CURRENT = """
# Optional server-to-server relay for public chat and cross-server 1:1 DMs.
# Each server runs BlueMapWebChat and exchanges signed HTTP POST requests with configured peers.
# Configure a stable unique server-id on every server and use the same shared-secret on both sides,
# or set a per-peer secret. Peer URL is the other server's BMChat API base URL.
# Public chat uses /relay/receive; private DM delivery and read acknowledgements use
# /relay/dm/receive and /relay/dm/read. The correct endpoint is appended automatically.
# For more than two servers, full-mesh and hub topologies are supported. relayId de-duplication
# and hop limits prevent loops, and multi-hop DM success is returned only after final storage confirmation.
""".stripTrailing();

    private static final String DELIVERY_COMMENT =
            "  # Public-relay delivery destinations. These switches do not disable private DM relay handling.";

    private static final String DELIVERY_OLD = """
  delivery:
    web: true
    game: true
""".stripTrailing();

    private static final String DELIVERY_CURRENT = DELIVERY_COMMENT + "\n" + DELIVERY_OLD;

    private static final String PEERS_OLD = """
  # Add one entry for each remote BMChat server. The URL may point to the API root;
  # /relay/receive is appended automatically.
""".stripTrailing();

    private static final String PEERS_CURRENT = """
  # Add one entry for each remote BMChat server. The URL may point to the API root or
  # an existing relay endpoint; BlueMapWebChat normalizes it and appends the correct
  # public-chat, DM-delivery, or DM-read endpoint automatically.
""".stripTrailing();

    private static final String TOKEN_ALIAS_COMMENT_OLD =
            "# Write aliases without surrounding colons. For example alias \"enter\" is typed as :enter:.";

    private static final String TOKEN_ALIAS_COMMENT_CURRENT =
            "# Write aliases without surrounding colons; alias \"enter\" is typed as :enter:";

    private static final String TOKEN_NEWLINE_COMMENT_OLD =
            "    # Inserts one new line. Change/add aliases freely, e.g. \"다음줄\" for :다음줄:.";

    private static final String TOKEN_NEWLINE_COMMENT_CURRENT =
            "    # Inserts one new line. Change or add aliases freely; for example, add \"next\" to use :next:";

    private static final String FONT_EXAMPLE_COMMENT_OLD =
            "  # Examples: '\"Malgun Gothic\", sans-serif', '\"Noto Sans KR\", sans-serif', '\"맑은 고딕\", sans-serif'.";

    private static final String FONT_EXAMPLE_COMMENT_CURRENT =
            "  # Examples: '\"Malgun Gothic\", sans-serif', '\"Noto Sans KR\", sans-serif'.";

    private static final String SUPER_ADMIN_OLD = """
# Users allowed to see private-message/group-room metadata for moderation/accounting.
# When direct-message.admin-audit.enabled is also true, these users may open DM message
# bodies in a read-only audit view. Every audit read is written to the audit log.
# Use exact Minecraft names or UUIDs. Empty = no private-chat super administrators.
""".stripTrailing();

    private static final String SUPER_ADMIN_CURRENT = """
# Users allowed to see private-message/group-room metadata for moderation/accounting.
# With direct-message.admin-audit.enabled, these users may open DM bodies; with
# group-chat.admin-audit.enabled, they may open group-chat bodies. Both audit views
# are read-only and every page read is written to the audit log.
# Use exact Minecraft names or UUIDs. Empty = no private-chat super administrators.
""".stripTrailing();

    private static final List<Replacement> REPLACEMENTS = List.of(
            new Replacement(REVIEW_OLD, REVIEW_CURRENT),
            new Replacement(REVIEW_PRE_REFERENCE, REVIEW_CURRENT),
            new Replacement(REVIEW_PRE_ORDER, REVIEW_CURRENT),
            new Replacement(UPDATE_OLD, UPDATE_CURRENT),
            new Replacement(DM_OLD, DM_CURRENT),
            new Replacement(GROUP_OLD, GROUP_CURRENT),
            new Replacement(RELAY_OLD, RELAY_CURRENT),
            new Replacement(DELIVERY_OLD, DELIVERY_CURRENT),
            new Replacement(PEERS_OLD, PEERS_CURRENT),
            new Replacement(TOKEN_ALIAS_COMMENT_OLD, TOKEN_ALIAS_COMMENT_CURRENT),
            new Replacement(TOKEN_NEWLINE_COMMENT_OLD, TOKEN_NEWLINE_COMMENT_CURRENT),
            new Replacement(FONT_EXAMPLE_COMMENT_OLD, FONT_EXAMPLE_COMMENT_CURRENT),
            new Replacement(SUPER_ADMIN_OLD, SUPER_ADMIN_CURRENT)
    );

    private ConfigCommentManager() {
    }

    static void refresh(BlueMapWebChatPlugin plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(path)) return;
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            String newline = raw.contains("\r\n") ? "\r\n" : "\n";
            String normalized = raw.replace("\r\n", "\n");
            String updated = refreshText(normalized, plugin.getDescription().getVersion());
            if (updated.equals(normalized)) return;
            Files.writeString(path, updated.replace("\n", newline), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            plugin.getLogger().info("Refreshed unchanged bundled comments in config.yml for BlueMapWebChat "
                    + plugin.getDescription().getVersion() + ". Setting values were not modified.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to refresh bundled config comments: " + ex.getMessage());
        }
    }

    static String refreshText(String input, String targetVersion) {
        String text = input == null ? "" : input;
        String version = targetVersion == null ? "" : targetVersion.trim();
        if (!version.isBlank()) {
            Matcher matcher = BUNDLED_HEADER.matcher(text);
            text = matcher.replaceFirst(Matcher.quoteReplacement("# BlueMapWebChat " + version + " configuration"));
        }
        // Repair the duplicate delivery comment produced by older 4.7.0 test builds.
        // This cleanup touches only the exact bundled comment line and never user comments.
        text = collapseRepeatedBundledLine(text, DELIVERY_COMMENT);

        for (int i = 0; i < REPLACEMENTS.size(); i++) {
            text = replaceBundledBlock(text, REPLACEMENTS.get(i), i);
        }
        return text;
    }

    /**
     * Replaces an unchanged bundled legacy block without treating the already-current
     * replacement as legacy again. Some current blocks intentionally contain their old
     * value lines (for example delivery: web/game), so a plain String.replace() is not
     * idempotent. Protect exact current blocks first, update only remaining legacy blocks,
     * then restore the protected current blocks.
     */
    private static String replaceBundledBlock(String text, Replacement replacement, int index) {
        if (text == null || text.isEmpty()) return text;
        if (replacement.oldText.equals(replacement.newText)) return text;

        String marker = "\u0001BMWC_CONFIG_COMMENT_" + index + "_CURRENT\u0001";
        while (text.contains(marker)) marker += "_";

        String protectedText = text.replace(replacement.newText, marker);
        protectedText = protectedText.replace(replacement.oldText, replacement.newText);
        return protectedText.replace(marker, replacement.newText);
    }

    private static String collapseRepeatedBundledLine(String text, String line) {
        if (text == null || text.isEmpty() || line == null || line.isEmpty()) return text;
        String doubled = line + "\n" + line;
        while (text.contains(doubled)) {
            text = text.replace(doubled, line);
        }
        return text;
    }

    private record Replacement(String oldText, String newText) {
    }
}
