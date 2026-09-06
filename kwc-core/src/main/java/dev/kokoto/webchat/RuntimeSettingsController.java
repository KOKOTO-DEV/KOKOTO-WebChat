package dev.kokoto.webchat;

import java.nio.file.Path;
import java.util.*;

/** Shared live-settings writer used by Web Admin and /kchat settings. */
public final class RuntimeSettingsController {
    private static final Object FILTER_RULES_LOCK = new Object();
    private RuntimeSettingsController() {}

    public record Result(boolean ok, String error, String path, Object value,
                         int sessionsUpdated, int sessionsExpired) {
        public static Result error(String path, String error) { return new Result(false, error, path, null, 0, 0); }
    }

    public record BatchResult(boolean ok, String error, Map<String,Object> values,
                              int sessionsUpdated, int sessionsExpired) {
        public static BatchResult error(String error) { return new BatchResult(false, error, Map.of(), 0, 0); }
    }

    public static final List<String> SUPPORTED_PATHS = List.of(
            "guest.enabled", "guest.allow-custom-name", "guest.cooldown-seconds", "guest.max-messages-per-minute",
            "captcha.mode", "captcha.require-on-each-message", "captcha.pass-valid-minutes",
            "auth.password-login", "auth.remember-session-days",
            "admin.admin-session-expire-hours",
            "chat.typing-indicator.user-display-control", "chat.typing-indicator.open-chat.enabled",
            "chat.typing-indicator.dm.enabled", "chat.typing-indicator.group-chat.enabled",
            "ui.user-profiles.enabled", "ui.user-profiles.max-profiles", "ui.user-profiles.allow-import-export",
            "admin-alerts.discord.enabled", "admin-alerts.discord.channel",
            "admin-alerts.discord.sources.public-chat", "admin-alerts.discord.sources.relay-chat",
            "admin-alerts.discord.sources.dm", "admin-alerts.discord.sources.group-chat",
            "admin-alerts.discord.mention", "admin-alerts.discord.case-sensitive", "admin-alerts.discord.keywords",
            "upload.enabled", "upload.allow-guest-upload", "upload.allow-user-upload",
            "upload.allow-moderator-upload", "upload.allow-admin-upload", "upload.cooldown-seconds",
            "upload.max-uploads-per-minute", "upload.max-file-size-mb", "upload.max-total-size-mb",
            "upload.max-files-per-message", "upload.retention-days", "upload.filename-mode",
            "content-filter.enabled", "content-filter.scopes.public", "content-filter.scopes.group",
            "content-filter.scopes.dm", "content-filter.block.show-matched-word", "content-filter.mask.text",
            "content-filter.anti-evasion.unicode-normalization", "content-filter.anti-evasion.compact-match",
            "content-filter.anti-evasion.interleave-match", "content-filter.anti-evasion.interleave-max-gap",
            "content-filter.anti-evasion.interleave-unlimited-gap", "content-filter.anti-evasion.collapse-repeats",
            "content-filter.anti-evasion.repeat-limit"
    );

    public static Result set(WebChatHost host, String path, String rawValue) {
        if (host == null || host.configValues() == null) return Result.error(path, "not_running");
        String key = String.valueOf(path == null ? "" : path).trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PATHS.contains(key)) return Result.error(key, "unsupported_setting");
        ConfigValues c = host.configValues();
        Object parsed;
        try { parsed = parse(key, rawValue); }
        catch (IllegalArgumentException ex) { return Result.error(key, ex.getMessage()); }

        Path configFile = host.dataDirectory().resolve("config.yml");
        Object persisted;
        try {
            ConfigTextEditor.setScalar(configFile, key, parsed);
            Map<String,String> disk = ConfigTextEditor.readScalars(configFile, List.of(key));
            if (!disk.containsKey(key)) return Result.error(key, "config_verify_missing");
            persisted = parse(key, disk.get(key));
            if (!Objects.equals(parsed, persisted)) return Result.error(key, "config_verify_mismatch");
        } catch (IllegalArgumentException ex) {
            host.logger().warn("Failed to verify persisted setting " + key + ": " + ex.getMessage());
            return Result.error(key, "config_verify_invalid");
        } catch (Exception ex) {
            host.logger().warn("Failed to persist setting " + key + ": " + ex.getMessage());
            return Result.error(key, "config_write_failed");
        }

        int updated = 0, expired = 0;
        apply(c, key, persisted);
        if ("auth.remember-session-days".equals(key) && host.storage() != null) {
            long duration = c.rememberSessionDays <= 0 ? 0L : daysMillis(c.rememberSessionDays);
            SessionPolicyUpdate r = host.storage().recalculateSessionExpiry(false, duration);
            updated += r.updated(); expired += r.expired();
        } else if ("admin.admin-session-expire-hours".equals(key) && host.storage() != null) {
            long duration = c.adminSessionExpireHours <= 0 ? 0L : hoursMillis(c.adminSessionExpireHours);
            SessionPolicyUpdate r = host.storage().recalculateSessionExpiry(true, duration);
            updated += r.updated(); expired += r.expired();
        }
        host.audit("settings.update", "runtime", Map.of("path", key, "value", String.valueOf(parsed),
                "sessionsUpdated", updated, "sessionsExpired", expired));
        return new Result(true, "", key, persisted, updated, expired);
    }

    public static BatchResult setAll(WebChatHost host, Map<String,String> rawValues) {
        if (host == null || host.configValues() == null) return BatchResult.error("not_running");
        if (rawValues == null || rawValues.isEmpty()) return new BatchResult(true, "", Map.of(), 0, 0);

        LinkedHashMap<String,Object> parsedValues = new LinkedHashMap<>();
        for (Map.Entry<String,String> entry : rawValues.entrySet()) {
            String key = String.valueOf(entry.getKey() == null ? "" : entry.getKey()).trim().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_PATHS.contains(key)) return BatchResult.error("unsupported_setting:" + key);
            try { parsedValues.put(key, parse(key, entry.getValue())); }
            catch (IllegalArgumentException ex) { return BatchResult.error(key + ":" + ex.getMessage()); }
        }

        Path configFile = host.dataDirectory().resolve("config.yml");
        LinkedHashMap<String,Object> persistedValues = new LinkedHashMap<>();
        try {
            ConfigTextEditor.setScalars(configFile, parsedValues);
            Map<String,String> disk = ConfigTextEditor.readScalars(configFile, parsedValues.keySet());
            for (Map.Entry<String,Object> entry : parsedValues.entrySet()) {
                String key = entry.getKey();
                if (!disk.containsKey(key)) return BatchResult.error("config_verify_missing:" + key);
                Object persisted = parse(key, disk.get(key));
                if (!Objects.equals(entry.getValue(), persisted)) return BatchResult.error("config_verify_mismatch:" + key);
                persistedValues.put(key, persisted);
            }
        } catch (IllegalArgumentException ex) {
            host.logger().warn("Failed to verify persisted runtime settings batch: " + ex.getMessage());
            return BatchResult.error("config_verify_invalid");
        } catch (Exception ex) {
            host.logger().warn("Failed to persist runtime settings batch: " + ex.getMessage());
            return BatchResult.error("config_write_failed");
        }

        ConfigValues c = host.configValues();
        for (Map.Entry<String,Object> entry : persistedValues.entrySet()) apply(c, entry.getKey(), entry.getValue());

        int updated = 0, expired = 0;
        if (persistedValues.containsKey("auth.remember-session-days") && host.storage() != null) {
            long duration = c.rememberSessionDays <= 0 ? 0L : daysMillis(c.rememberSessionDays);
            SessionPolicyUpdate r = host.storage().recalculateSessionExpiry(false, duration);
            updated += r.updated(); expired += r.expired();
        }
        if (persistedValues.containsKey("admin.admin-session-expire-hours") && host.storage() != null) {
            long duration = c.adminSessionExpireHours <= 0 ? 0L : hoursMillis(c.adminSessionExpireHours);
            SessionPolicyUpdate r = host.storage().recalculateSessionExpiry(true, duration);
            updated += r.updated(); expired += r.expired();
        }

        host.audit("settings.update-batch", "runtime", Map.of(
                "count", persistedValues.size(), "paths", String.join(",", persistedValues.keySet()),
                "sessionsUpdated", updated, "sessionsExpired", expired));
        return new BatchResult(true, "", persistedValues, updated, expired);
    }

    public record FilterRulesResult(boolean ok, String error, List<ContentFilterRule> rules) {
        public static FilterRulesResult error(String error) { return new FilterRulesResult(false, error, List.of()); }
    }

    /**
     * Replace the complete filter rule set. Game-side commands use this API; Web Admin
     * uses the atomic upsert/remove methods below so read-modify-write is protected by
     * the same lock as persistence verification.
     */
    public static Result replaceFilterRules(WebChatHost host, List<ContentFilterRule> rules, String actor) {
        if (host == null || host.configValues() == null) return Result.error("content-filter.rules", "not_running");
        synchronized (FILTER_RULES_LOCK) {
            List<ContentFilterRule> normalized = normalizeRuleSet(rules, true);
            FilterRulesResult persisted = persistFilterRulesLocked(host, normalized, actor);
            if (!persisted.ok()) return Result.error("content-filter.rules", persisted.error());
            return new Result(true, "", "content-filter.rules", persisted.rules().size(), 0, 0);
        }
    }

    /** Create one Admin-managed rule from the current disk state. */
    public static FilterRulesResult createFilterRule(WebChatHost host, ContentFilterRule requested, String actor) {
        if (host == null || host.configValues() == null) return FilterRulesResult.error("not_running");
        if (requested == null) return FilterRulesResult.error("invalid_rule");
        synchronized (FILTER_RULES_LOCK) {
            try {
                Path configFile = host.dataDirectory().resolve("config.yml");
                ArrayList<ContentFilterRule> current = copyRules(ConfigTextEditor.readContentFilterRules(configFile));
                ContentFilterRule rule = requested.copy();
                rule.normalize();
                if (rule.words.isEmpty()) return FilterRulesResult.error("words_required");
                String base = rule.id.isBlank() ? "rule" : rule.id;
                rule.id = uniqueRuleId(current, base, Set.of());
                current.add(rule);
                return persistFilterRulesLocked(host, current, actor);
            } catch (Exception ex) {
                host.logger().warn("Failed to create content-filter rule: " + ex.getMessage());
                return FilterRulesResult.error("config_write_failed");
            }
        }
    }

    /** Update one Admin-managed rule. Duplicate copies of the same original id are coalesced. */
    public static FilterRulesResult updateFilterRule(WebChatHost host, String originalId,
                                                     ContentFilterRule requested, String actor) {
        if (host == null || host.configValues() == null) return FilterRulesResult.error("not_running");
        if (requested == null) return FilterRulesResult.error("invalid_rule");
        String previous = ContentFilterRule.normalizedId(originalId);
        if (previous.isBlank()) return FilterRulesResult.error("rule_id_required");
        synchronized (FILTER_RULES_LOCK) {
            try {
                Path configFile = host.dataDirectory().resolve("config.yml");
                ArrayList<ContentFilterRule> current = copyRules(ConfigTextEditor.readContentFilterRules(configFile));
                ContentFilterRule rule = requested.copy();
                rule.normalize();
                if (rule.words.isEmpty()) return FilterRulesResult.error("words_required");
                if (rule.id.isBlank()) rule.id = previous;

                int insertAt = -1;
                for (int i = 0; i < current.size(); i++) {
                    if (sameRuleId(current.get(i), previous)) { insertAt = i; break; }
                }
                if (insertAt < 0) return FilterRulesResult.error("rule_not_found");

                // A previous buggy write may have left duplicate copies of the same id.
                // Treat them as the same logical rule for this explicit edit operation.
                ArrayList<ContentFilterRule> remaining = new ArrayList<>();
                for (ContentFilterRule existing : current) {
                    if (!sameRuleId(existing, previous)) remaining.add(existing);
                }
                for (ContentFilterRule existing : remaining) {
                    if (sameRuleId(existing, rule.id)) return FilterRulesResult.error("rule_id_exists");
                }
                insertAt = Math.min(insertAt, remaining.size());
                remaining.add(insertAt, rule);
                return persistFilterRulesLocked(host, remaining, actor);
            } catch (Exception ex) {
                host.logger().warn("Failed to update content-filter rule: " + ex.getMessage());
                return FilterRulesResult.error("config_write_failed");
            }
        }
    }

    /** Atomically remove one Admin-managed rule from the current disk state. */
    public static FilterRulesResult removeFilterRule(WebChatHost host, String id, String actor) {
        if (host == null || host.configValues() == null) return FilterRulesResult.error("not_running");
        String target = ContentFilterRule.normalizedId(id);
        if (target.isBlank()) return FilterRulesResult.error("rule_id_required");
        synchronized (FILTER_RULES_LOCK) {
            try {
                Path configFile = host.dataDirectory().resolve("config.yml");
                ArrayList<ContentFilterRule> current = copyRules(ConfigTextEditor.readContentFilterRules(configFile));
                boolean removed = current.removeIf(rule -> sameRuleId(rule, target));
                if (!removed) return FilterRulesResult.error("rule_not_found");
                return persistFilterRulesLocked(host, current, actor);
            } catch (Exception ex) {
                host.logger().warn("Failed to remove content-filter rule: " + ex.getMessage());
                return FilterRulesResult.error("config_write_failed");
            }
        }
    }

    /** Read the exact persisted rule set without depending on the live ConfigValues object. */
    public static FilterRulesResult filterRulesFromDisk(WebChatHost host) {
        if (host == null || host.configValues() == null) return FilterRulesResult.error("not_running");
        synchronized (FILTER_RULES_LOCK) {
            try {
                List<ContentFilterRule> persisted = ConfigTextEditor.readContentFilterRules(host.dataDirectory().resolve("config.yml"));
                ArrayList<ContentFilterRule> copy = copyRules(persisted);
                host.configValues().contentFilterRules = copyRules(copy);
                refreshWordListRules(host);
                return new FilterRulesResult(true, "", copy);
            } catch (Exception ex) {
                host.logger().warn("Failed to read content-filter.rules from config.yml: " + ex.getMessage());
                return FilterRulesResult.error("filter_config_read_failed");
            }
        }
    }

    private static FilterRulesResult persistFilterRulesLocked(WebChatHost host, List<ContentFilterRule> requested, String actor) {
        List<ContentFilterRule> normalized = normalizeRuleSet(requested, false);
        Path configFile = host.dataDirectory().resolve("config.yml");
        try {
            ConfigTextEditor.replaceContentFilterRules(configFile, normalized);
            List<ContentFilterRule> persisted = ConfigTextEditor.readContentFilterRules(configFile);
            if (!ContentFilterConfigParser.toMaps(normalized).equals(ContentFilterConfigParser.toMaps(persisted))) {
                host.logger().warn("Persisted content-filter.rules did not match the requested rule set.");
                return FilterRulesResult.error("config_verify_mismatch");
            }
            ArrayList<ContentFilterRule> authoritative = copyRules(persisted);
            host.configValues().contentFilterRules = copyRules(authoritative);
            refreshWordListRules(host);
            host.audit("content-filter.rules-update", String.valueOf(actor == null ? "runtime" : actor), Map.of("count", authoritative.size()));
            return new FilterRulesResult(true, "", authoritative);
        } catch (Exception ex) {
            host.logger().warn("Failed to persist content-filter rules: " + ex.getMessage());
            return FilterRulesResult.error("config_write_failed");
        }
    }


    /** Refresh active filter-lists/*.txt independently of custom config.yml rules. */
    public static void refreshWordListRules(WebChatHost host) {
        if (host == null || host.configValues() == null) return;
        try {
            host.configValues().contentFilterWordListRules = new ArrayList<>(ContentFilterWordListStore.loadRules(host.dataDirectory()));
        } catch (Exception ex) {
            host.logger().warn("Failed to load content-filter word lists: " + ex.getMessage());
            host.configValues().contentFilterWordListRules = new ArrayList<>();
        }
    }

    private static List<ContentFilterRule> normalizeRuleSet(List<ContentFilterRule> rules, boolean deduplicateIds) {
        ArrayList<ContentFilterRule> normalized = new ArrayList<>();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (rules != null) for (ContentFilterRule source : rules) {
            if (source == null) continue;
            ContentFilterRule r = source.copy();
            r.normalize();
            if (r.words.isEmpty()) continue;
            String base = r.id.isBlank() ? "rule" : r.id;
            String id = base;
            int n = 2;
            if (deduplicateIds) {
                while (!ids.add(id)) id = base + "-" + n++;
            } else if (!ids.add(id)) {
                // Admin atomic paths reject duplicates before this point. Preserve the
                // first rule here rather than silently renaming a persisted rule.
                continue;
            }
            r.id = id;
            normalized.add(r);
        }
        return normalized;
    }

    private static ArrayList<ContentFilterRule> copyRules(Collection<ContentFilterRule> rules) {
        ArrayList<ContentFilterRule> out = new ArrayList<>();
        if (rules != null) for (ContentFilterRule rule : rules) if (rule != null) out.add(rule.copy());
        return out;
    }

    private static String uniqueRuleId(Collection<ContentFilterRule> rules, String requested, Set<String> ignoredIds) {
        String base = ContentFilterRule.normalizedId(requested);
        if (base.isBlank()) base = "rule";
        LinkedHashSet<String> used = new LinkedHashSet<>();
        if (rules != null) for (ContentFilterRule rule : rules) {
            if (rule == null) continue;
            String id = ContentFilterRule.normalizedId(rule.id);
            if (!id.isBlank() && (ignoredIds == null || !ignoredIds.contains(id))) used.add(id.toLowerCase(Locale.ROOT));
        }
        if (!used.contains(base.toLowerCase(Locale.ROOT))) return base;
        int n = 2;
        String stem = base;
        while (true) {
            String suffix = "-" + n++;
            int maxStem = Math.max(1, 64 - suffix.length());
            String candidate = (stem.length() > maxStem ? stem.substring(0, maxStem) : stem) + suffix;
            if (!used.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
    }

    private static boolean sameRuleId(ContentFilterRule rule, String id) {
        return rule != null && ContentFilterRule.normalizedId(rule.id).equalsIgnoreCase(ContentFilterRule.normalizedId(id));
    }

    public record SnapshotResult(boolean ok, String error, Map<String,Object> values) {
        public static SnapshotResult error(String error) { return new SnapshotResult(false, error, Map.of()); }
    }

    /**
     * Read the Admin-supported settings from the actual config.yml instead of the
     * in-memory ConfigValues object. Missing keys legitimately fall back to the
     * currently loaded defaults; malformed or unreadable values are reported so
     * the Admin UI never presents an old runtime value as a successful disk save.
     */
    public static SnapshotResult snapshotFromDisk(WebChatHost host) {
        if (host == null || host.configValues() == null) return SnapshotResult.error("not_running");
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        try {
            Map<String,String> disk = ConfigTextEditor.readScalars(
                    host.dataDirectory().resolve("config.yml"), SUPPORTED_PATHS);
            for (String path : SUPPORTED_PATHS) {
                if (disk.containsKey(path)) out.put(path, parse(path, disk.get(path)));
                else out.put(path, value(host.configValues(), path));
            }
            return new SnapshotResult(true, "", out);
        } catch (IllegalArgumentException ex) {
            host.logger().warn("Failed to parse Admin settings from config.yml: " + ex.getMessage());
            return SnapshotResult.error("config_read_invalid");
        } catch (Exception ex) {
            host.logger().warn("Failed to read Admin settings from config.yml: " + ex.getMessage());
            return SnapshotResult.error("config_read_failed");
        }
    }

    public static Map<String,Object> snapshot(ConfigValues c) {
        LinkedHashMap<String,Object> m = new LinkedHashMap<>();
        if (c == null) return m;
        for (String path : SUPPORTED_PATHS) m.put(path, value(c, path));
        return m;
    }

    public static Object value(ConfigValues c, String path) {
        if (c == null) return null;
        return switch (path) {
            case "guest.enabled" -> c.guestEnabled;
            case "guest.allow-custom-name" -> c.guestAllowCustomName;
            case "guest.cooldown-seconds" -> c.guestCooldownSeconds;
            case "guest.max-messages-per-minute" -> c.guestMaxMessagesPerMinute;
            case "captcha.mode" -> c.captchaMode;
            case "captcha.require-on-each-message" -> c.captchaRequireOnEachMessage;
            case "captcha.pass-valid-minutes" -> c.captchaPassValidMinutes;
            case "auth.password-login" -> c.passwordLogin;
            case "auth.remember-session-days" -> c.rememberSessionDays;
            case "admin.admin-session-expire-hours" -> c.adminSessionExpireHours;
            case "chat.typing-indicator.user-display-control" -> c.typingUserDisplayControl;
            case "chat.typing-indicator.open-chat.enabled" -> c.typingOpenChatEnabled;
            case "chat.typing-indicator.dm.enabled" -> c.typingDmEnabled;
            case "chat.typing-indicator.group-chat.enabled" -> c.typingGroupChatEnabled;
            case "ui.user-profiles.enabled" -> c.uiUserProfilesEnabled;
            case "ui.user-profiles.max-profiles" -> c.uiUserProfilesMaxProfiles;
            case "ui.user-profiles.allow-import-export" -> c.uiUserProfilesAllowImportExport;
            case "admin-alerts.discord.enabled" -> c.adminDiscordAlertsEnabled;
            case "admin-alerts.discord.channel" -> c.adminDiscordAlertsChannel;
            case "admin-alerts.discord.sources.public-chat" -> c.adminDiscordAlertsPublicChat;
            case "admin-alerts.discord.sources.relay-chat" -> c.adminDiscordAlertsRelayChat;
            case "admin-alerts.discord.sources.dm" -> c.adminDiscordAlertsDm;
            case "admin-alerts.discord.sources.group-chat" -> c.adminDiscordAlertsGroupChat;
            case "admin-alerts.discord.mention" -> c.adminDiscordAlertsMention;
            case "admin-alerts.discord.case-sensitive" -> c.adminDiscordAlertsCaseSensitive;
            case "admin-alerts.discord.keywords" -> String.join(",", c.adminDiscordAlertKeywords == null ? List.of() : c.adminDiscordAlertKeywords);
            case "upload.enabled" -> c.uploadEnabled;
            case "upload.allow-guest-upload" -> c.uploadAllowGuest;
            case "upload.allow-user-upload" -> c.uploadAllowUser;
            case "upload.allow-moderator-upload" -> c.uploadAllowModerator;
            case "upload.allow-admin-upload" -> c.uploadAllowAdmin;
            case "upload.cooldown-seconds" -> c.uploadCooldownSeconds;
            case "upload.max-uploads-per-minute" -> c.uploadMaxUploadsPerMinute;
            case "upload.max-file-size-mb" -> c.uploadMaxFileSizeMb;
            case "upload.max-total-size-mb" -> c.uploadMaxTotalSizeMb;
            case "upload.max-files-per-message" -> c.uploadMaxFilesPerMessage;
            case "upload.retention-days" -> c.uploadRetentionDays;
            case "upload.filename-mode" -> c.uploadFilenameMode;
            case "content-filter.enabled" -> c.contentFilterEnabled;
            case "content-filter.scopes.public" -> c.contentFilterPublic;
            case "content-filter.scopes.group" -> c.contentFilterGroup;
            case "content-filter.scopes.dm" -> c.contentFilterDm;
            case "content-filter.block.show-matched-word" -> c.contentFilterShowMatchedWord;
            case "content-filter.mask.text" -> c.contentFilterMaskText;
            case "content-filter.anti-evasion.unicode-normalization" -> c.contentFilterUnicodeNormalization;
            case "content-filter.anti-evasion.compact-match" -> c.contentFilterCompactMatch;
            case "content-filter.anti-evasion.interleave-match" -> c.contentFilterInterleaveMatch;
            case "content-filter.anti-evasion.interleave-max-gap" -> c.contentFilterInterleaveMaxGap;
            case "content-filter.anti-evasion.interleave-unlimited-gap" -> c.contentFilterInterleaveUnlimitedGap;
            case "content-filter.anti-evasion.collapse-repeats" -> c.contentFilterCollapseRepeats;
            case "content-filter.anti-evasion.repeat-limit" -> c.contentFilterRepeatLimit;
            default -> null;
        };
    }

    private static Object parse(String path, String raw) {
        String v = String.valueOf(raw == null ? "" : raw).trim();
        return switch (path) {
            case "guest.enabled", "guest.allow-custom-name", "captcha.require-on-each-message", "auth.password-login",
                 "chat.typing-indicator.user-display-control", "chat.typing-indicator.open-chat.enabled",
                 "chat.typing-indicator.dm.enabled", "chat.typing-indicator.group-chat.enabled",
                 "ui.user-profiles.enabled", "ui.user-profiles.allow-import-export",
                 "admin-alerts.discord.enabled", "admin-alerts.discord.sources.public-chat",
                 "admin-alerts.discord.sources.relay-chat", "admin-alerts.discord.sources.dm",
                 "admin-alerts.discord.sources.group-chat", "admin-alerts.discord.case-sensitive",
                 "upload.enabled", "upload.allow-guest-upload", "upload.allow-user-upload",
                 "upload.allow-moderator-upload", "upload.allow-admin-upload", "content-filter.enabled",
                 "content-filter.scopes.public", "content-filter.scopes.group", "content-filter.scopes.dm",
                 "content-filter.block.show-matched-word", "content-filter.anti-evasion.unicode-normalization",
                 "content-filter.anti-evasion.compact-match", "content-filter.anti-evasion.interleave-match",
                 "content-filter.anti-evasion.interleave-unlimited-gap", "content-filter.anti-evasion.collapse-repeats" -> parseBoolean(v);
            case "guest.cooldown-seconds", "guest.max-messages-per-minute", "captcha.pass-valid-minutes",
                 "auth.remember-session-days", "upload.cooldown-seconds", "upload.max-uploads-per-minute",
                 "upload.max-file-size-mb", "upload.max-total-size-mb", "upload.max-files-per-message",
                 "upload.retention-days", "content-filter.anti-evasion.interleave-max-gap" -> parseInt(v, 0, 1_000_000);
            case "admin.admin-session-expire-hours" -> parseInt(v, 0, 1_000_000);
            case "ui.user-profiles.max-profiles" -> parseInt(v, 0, 20);
            case "content-filter.anti-evasion.repeat-limit" -> parseInt(v, 1, 64);
            case "captcha.mode" -> {
                String x = v.toLowerCase(Locale.ROOT);
                if (!Set.of("off", "math").contains(x)) throw new IllegalArgumentException("invalid_value");
                yield x;
            }
            case "upload.filename-mode" -> {
                String x = v.toLowerCase(Locale.ROOT);
                if (!Set.of("random", "original").contains(x)) throw new IllegalArgumentException("invalid_value");
                yield x;
            }
            case "admin-alerts.discord.mention" -> {
                String x = v.toLowerCase(Locale.ROOT);
                if (!Set.of("none", "here", "everyone").contains(x)) throw new IllegalArgumentException("invalid_value");
                yield x;
            }
            case "admin-alerts.discord.channel" -> {
                if (v.length() > 100 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) throw new IllegalArgumentException("invalid_value");
                yield v;
            }
            case "admin-alerts.discord.keywords" -> {
                if (v.length() > 4096 || v.indexOf('<') >= 0 || v.indexOf('>') >= 0) throw new IllegalArgumentException("invalid_value");
                yield v;
            }
            case "content-filter.mask.text" -> {
                if (v.length() > 64) throw new IllegalArgumentException("value_too_long");
                yield v;
            }
            default -> throw new IllegalArgumentException("unsupported_setting");
        };
    }

    private static void apply(ConfigValues c, String path, Object value) {
        switch (path) {
            case "guest.enabled" -> c.guestEnabled = (Boolean)value;
            case "guest.allow-custom-name" -> c.guestAllowCustomName = (Boolean)value;
            case "guest.cooldown-seconds" -> c.guestCooldownSeconds = (Integer)value;
            case "guest.max-messages-per-minute" -> c.guestMaxMessagesPerMinute = (Integer)value;
            case "captcha.mode" -> c.captchaMode = (String)value;
            case "captcha.require-on-each-message" -> c.captchaRequireOnEachMessage = (Boolean)value;
            case "captcha.pass-valid-minutes" -> c.captchaPassValidMinutes = (Integer)value;
            case "auth.password-login" -> c.passwordLogin = (Boolean)value;
            case "auth.remember-session-days" -> c.rememberSessionDays = (Integer)value;
            case "admin.admin-session-expire-hours" -> c.adminSessionExpireHours = (Integer)value;
            case "chat.typing-indicator.user-display-control" -> c.typingUserDisplayControl = (Boolean)value;
            case "chat.typing-indicator.open-chat.enabled" -> c.typingOpenChatEnabled = (Boolean)value;
            case "chat.typing-indicator.dm.enabled" -> c.typingDmEnabled = (Boolean)value;
            case "chat.typing-indicator.group-chat.enabled" -> c.typingGroupChatEnabled = (Boolean)value;
            case "ui.user-profiles.enabled" -> c.uiUserProfilesEnabled = (Boolean)value;
            case "ui.user-profiles.max-profiles" -> c.uiUserProfilesMaxProfiles = (Integer)value;
            case "ui.user-profiles.allow-import-export" -> c.uiUserProfilesAllowImportExport = (Boolean)value;
            case "admin-alerts.discord.enabled" -> c.adminDiscordAlertsEnabled = (Boolean)value;
            case "admin-alerts.discord.channel" -> c.adminDiscordAlertsChannel = (String)value;
            case "admin-alerts.discord.sources.public-chat" -> c.adminDiscordAlertsPublicChat = (Boolean)value;
            case "admin-alerts.discord.sources.relay-chat" -> c.adminDiscordAlertsRelayChat = (Boolean)value;
            case "admin-alerts.discord.sources.dm" -> c.adminDiscordAlertsDm = (Boolean)value;
            case "admin-alerts.discord.sources.group-chat" -> c.adminDiscordAlertsGroupChat = (Boolean)value;
            case "admin-alerts.discord.mention" -> c.adminDiscordAlertsMention = (String)value;
            case "admin-alerts.discord.case-sensitive" -> c.adminDiscordAlertsCaseSensitive = (Boolean)value;
            case "admin-alerts.discord.keywords" -> {
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                ArrayList<String> words = new ArrayList<>();
                for (String part : String.valueOf(value).split("[,\r\n]+")) {
                    String word = part.replaceAll("[\\x00-\\x1f]", "").trim();
                    if (word.isBlank()) continue;
                    if (word.length() > 80) word = word.substring(0, 80);
                    if (seen.add(word.toLowerCase(Locale.ROOT))) words.add(word);
                    if (words.size() >= 80) break;
                }
                c.adminDiscordAlertKeywords = words;
            }
            case "upload.enabled" -> c.uploadEnabled = (Boolean)value;
            case "upload.allow-guest-upload" -> c.uploadAllowGuest = (Boolean)value;
            case "upload.allow-user-upload" -> c.uploadAllowUser = (Boolean)value;
            case "upload.allow-moderator-upload" -> c.uploadAllowModerator = (Boolean)value;
            case "upload.allow-admin-upload" -> c.uploadAllowAdmin = (Boolean)value;
            case "upload.cooldown-seconds" -> c.uploadCooldownSeconds = (Integer)value;
            case "upload.max-uploads-per-minute" -> c.uploadMaxUploadsPerMinute = (Integer)value;
            case "upload.max-file-size-mb" -> c.uploadMaxFileSizeMb = (Integer)value;
            case "upload.max-total-size-mb" -> c.uploadMaxTotalSizeMb = (Integer)value;
            case "upload.max-files-per-message" -> c.uploadMaxFilesPerMessage = (Integer)value;
            case "upload.retention-days" -> c.uploadRetentionDays = (Integer)value;
            case "upload.filename-mode" -> c.uploadFilenameMode = (String)value;
            case "content-filter.enabled" -> c.contentFilterEnabled = (Boolean)value;
            case "content-filter.scopes.public" -> c.contentFilterPublic = (Boolean)value;
            case "content-filter.scopes.group" -> c.contentFilterGroup = (Boolean)value;
            case "content-filter.scopes.dm" -> c.contentFilterDm = (Boolean)value;
            case "content-filter.block.show-matched-word" -> c.contentFilterShowMatchedWord = (Boolean)value;
            case "content-filter.mask.text" -> c.contentFilterMaskText = (String)value;
            case "content-filter.anti-evasion.unicode-normalization" -> c.contentFilterUnicodeNormalization = (Boolean)value;
            case "content-filter.anti-evasion.compact-match" -> c.contentFilterCompactMatch = (Boolean)value;
            case "content-filter.anti-evasion.interleave-match" -> c.contentFilterInterleaveMatch = (Boolean)value;
            case "content-filter.anti-evasion.interleave-max-gap" -> c.contentFilterInterleaveMaxGap = (Integer)value;
            case "content-filter.anti-evasion.interleave-unlimited-gap" -> c.contentFilterInterleaveUnlimitedGap = (Boolean)value;
            case "content-filter.anti-evasion.collapse-repeats" -> c.contentFilterCollapseRepeats = (Boolean)value;
            case "content-filter.anti-evasion.repeat-limit" -> c.contentFilterRepeatLimit = (Integer)value;
        }
    }

    private static boolean parseBoolean(String value) {
        if (Set.of("true", "yes", "on", "1").contains(value.toLowerCase(Locale.ROOT))) return true;
        if (Set.of("false", "no", "off", "0").contains(value.toLowerCase(Locale.ROOT))) return false;
        throw new IllegalArgumentException("invalid_boolean");
    }

    private static int parseInt(String value, int min, int max) {
        try {
            long n = Long.parseLong(value);
            if (n < min || n > max) throw new IllegalArgumentException("out_of_range");
            return (int)n;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("invalid_integer"); }
    }

    private static long daysMillis(int days) {
        try { return Math.multiplyExact((long)days, 24L * 60L * 60L * 1000L); }
        catch (ArithmeticException ex) { return Long.MAX_VALUE; }
    }
    private static long hoursMillis(int hours) {
        try { return Math.multiplyExact((long)hours, 60L * 60L * 1000L); }
        catch (ArithmeticException ex) { return Long.MAX_VALUE; }
    }
}
