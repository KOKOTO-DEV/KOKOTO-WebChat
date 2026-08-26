# Upgrade from 4.5.5 to 4.6.0

## Back up first

Stop the server and back up `plugins/KOKOTO-WebChat`, especially `config.yml`, SQLite databases and their `-wal`/`-shm` files, DM/group databases, uploads, emojis, custom language files, audit logs, and Web Push key/subscription files.

## Generated configuration migration fragment

KOKOTO WebChat never overwrites or automatically merges an existing `config.yml`. On server start and `/kchat reload`, it reads the physical file and checks `config-version`.

- If `config-version` matches the running plugin version, the configuration is treated as already reviewed and comparison is skipped. A stale migration fragment for that version is removed.
- If the marker is missing or different, the plugin compares the physical config with the bundled current defaults and creates or refreshes:

Exact decision table:

| Installed configuration state | Migration file |
|---|---|
| Version marker missing | Created even when no other differences exist |
| Version marker differs from the running plugin | Created or refreshed |
| Version marker matches the running plugin | Not created; stale same-version guidance is removed |

```text
plugins/KOKOTO-WebChat/config-migration-4.6.0.yml
```

The generated file is a copy-ready YAML fragment, not a structured report. It contains:

- settings missing from the physical `config.yml`, using the current bundled default;
- settings whose bundled default changed and whose configured value still equals the previous default;
- the target `config-version` review marker.

All version information, counts, and previous/new default details are written only as `#` comments. There are no `migration:`, `summary:`, `settings-to-add:`, `preserved-custom-values:`, `obsolete-settings-to-review:`, or `finalize-after-review:` metadata sections. Custom values and obsolete-setting notes are intentionally omitted.

Merge only the settings you want into the matching locations in the real `config.yml`. The real file is never modified automatically.

If no missing settings or changed bundled defaults are found, the migration file is still created and contains the target `config-version` marker. This ensures that an unversioned configuration can always be explicitly marked as reviewed.

4.6.0 bundles the 4.5.5 default configuration as its comparison baseline. A config without a version marker is treated as 4.5.5-or-older. For an explicit unknown version, the plugin lists missing keys but does not guess which defaults changed.

After review, set this in the real config:

```yaml
config-version: "4.6.0"
```

Future starts and reloads skip comparison while that marker matches the plugin version.

## Main 4.6.0 additions

- top-level `server-relay:` section
- `direct-message.capture-game-whispers`
- `reply.game-click.enabled`
- `reply.game-click.local-game-chat`
- `reply.game-command-format`
- Discord `{server}` and `{server_id}` placeholders

While the versions do not match, privacy-sensitive capture and local-chat replacement options that are absent from the physical file use safe disabled runtime fallbacks.

## Database migration

Relay metadata columns are added to public SQLite history with additive `ALTER TABLE` migrations. Existing rows remain, but old rows cannot be assigned an origin server retroactively. Back up the database before the first 4.6.0 start.

## Recommended checks

1. Start with the old config and verify that `config-migration-4.6.0.yml` is created without changing `config.yml`.
2. Review and merge missing settings and changed defaults.
3. Add `config-version: "4.6.0"`, run `/kchat reload`, and confirm that comparison is skipped.
4. Test relay, game replies, whisper DM capture, Discord server labels, URLs, and ImageEmojis handling.
