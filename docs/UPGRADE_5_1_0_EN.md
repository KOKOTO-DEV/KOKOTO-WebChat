# Upgrade to KOKOTO WebChat 5.1.0


![ui.language configuration reconstruction](assets/config-language-migration.gif)

> **Important:** Parsed operator values remain authoritative while `ui.language` changes the config/reference/migration presentation language.

KOKOTO WebChat 5.1.0 upgrades from the **5.0.0** line. Release notes describe the final changes from the **5.0.0** release baseline to 5.1.0.

## Before upgrading

1. Back up the complete KWC data directory, including `config.yml`, chat/private-chat databases or JSONL files, uploads, emoji assets and audit data.
2. If multiple servers relay to each other, plan to upgrade and reconfigure all members together. Relay Protocol v1 does not interoperate with 5.1.0 Relay v2.
3. If KWC is behind Caddy/Nginx, keep the existing public URL and proxy layout available so client-IP resolution can be checked after the upgrade.

## Configuration migration and language

5.1.0 keeps the existing parsed setting values and rebuilds the presentation from the current template. The effective `ui.language` selects the comment/layout language for the rebuilt `config.yml`, generated `config-reference-5.1.0.yml`, and migration/difference prose. Supported built-in presentation languages are `en-US`, `ko-KR`, `ja-JP`, and `zh-CN`.

The migration Difference is semantic: it compares parsed YAML setting paths and values, not comments, blank lines, indentation, quote style, line numbers or key order. Changing only the presentation language must therefore not create setting differences.

After first startup, review both `config.yml` and `config-reference-5.1.0.yml`. Existing custom setting values, Relay group data created under 5.1.0, and other operator values are preserved when the same-version automatic migration presentation is rebuilt.

## Relay Protocol v2 is a manual trust migration

The first migration from a pre-5.1.0 configuration deliberately retires the old flat Relay v1 trust model instead of guessing new trust relationships. Legacy global shared-secret/flat-peer/forwarding settings are removed and `server-relay.enabled` is reset to `false`.

Create explicit `server-relay.groups`. For a new group, leave `shared-secret: ""` on one server, start/reload KWC to generate and save a secure random value, then copy that exact value to the other servers in the same group. Existing non-empty secrets are preserved, while manually supplied values shorter than 32 characters remain invalid. Configure reciprocal peers in the same group. Peer entries contain only `id`, `url`, and `enabled`; there is no peer-specific secret. Enable `server-relay.enabled` only after every intended peer has the matching reciprocal configuration.

Before a rolling upgrade, set `server-relay.enabled: false` on the still-running 5.0.0 servers and reload them. A 5.0.0 server otherwise keeps retrying its v1 peer against an already-upgraded 5.1.0 server and can repeatedly log the expected HTTP 426 response. Upgrade/configure all peers for v2 before re-enabling Relay.

Relay v1/BMWC endpoints return HTTP 426. Direct one-hop HTTP peers can still carry encrypted/authenticated Relay v2 payloads with a warning, but forwarding is same-group **HTTPS→HTTPS only**. Relay v2 is hop-by-hop authenticated encryption, not E2EE.

## Private Reply storage upgrade

5.1.0 adds persistent reply metadata to DM and group messages. Existing private messages remain valid. SQLite-backed group storage adds the required optional reply columns during schema upgrade, while backward-compatible DM/JSONL records simply omit reply metadata when it does not exist. No manual database conversion is required.

5.1.0 also adds per-room group membership-event state. Existing `group-messages.db` files are upgraded in place with `group_rooms.membership_events_enabled` (default enabled) and `group_messages.event_type` (default ordinary message). No manual database conversion is required; closing the group-chat UI does not create a leave event.

Cross-server DM replies use stable relay message IDs, not another server's local database row ID. Do not copy or rewrite private-message IDs between servers.

## Emoji and SSE behavior

The default SSE limits are now **10 connections per resolved client IP** and **500 total**. A value of `0` still disables the corresponding limit. Behind a reverse proxy, verify `http.trusted-proxies`; otherwise many clients can appear as one proxy IP and share the same per-IP bucket.

Emoji catalog reload is more resilient: a temporary `/emojis` fetch failure keeps the last known-good catalog, retries with bounded exponential backoff, resynchronizes after SSE reconnect, and refreshes when the server emits an `emoji-catalog` invalidation event. `emoji.message-token-limit: 0` remains unlimited.

Custom emoji pack/item names are canonicalized in 5.1.0. Unsupported characters and whitespace are removed and same-pack collisions receive numeric suffixes. Review custom integrations that assume a literal on-disk emoji filename or token.

- Android administrator emoji uploads now use the generic system file/DocumentsUI chooser instead of the image-only Photo Picker path. Desktop keeps the image filter, and server-side image validation remains unchanged.

## Other behavior changes to verify

- DM/group Reply now persists the original-message relationship and validates the current DM thread or group membership server-side.
- Game-side `/kchat group` commands correctly resolve room names containing spaces, quoted names, and repeated whitespace.
- Per-room membership notices cover actual join/invite-accept and leave/kick/ban changes; closing or hiding a room is not a leave event.
- Account chat profiles preserve explicit and intentionally unset visual values exactly, and loaded font settings apply to already-open DM/group windows.
- DM/group metadata now wraps timestamp, Reply, and read-state elements naturally on narrow/mobile layouts instead of reserving one oversized fixed column.
- `/kchat reload` rechecks exposed built-in HTTP listeners and direct HTTP Relay peers and echoes applicable localized warnings to the command sender as well as the server log.
- When `commands.broadcast-result-to-web-chat: true`, Web-command execution notices are also delivered to online Minecraft players.
- The public, DM and group message composers use one-line `<textarea>` controls rather than ordinary text `<input>` controls, while retaining `autocomplete=off`, Enter-to-send and cursor/emoji insertion behavior. This bypasses the Android Chrome path that can show password/address/payment Autofill keyboard accessories on unrelated inputs; login/password Autofill is unchanged.
- During the project-address transition, the updater checks canonical Modrinth `kokoto-webchat` first and falls back to `bluemapwebchat`; BMWC remains a real update source until the transition is complete, and only dual-source failure is warned.
- External plain-HTTP listeners and direct HTTP relay peers produce localized security warnings.

## Post-upgrade checks

1. Confirm `config-version` and review the generated 5.1.0 reference/migration files.
2. Confirm the selected `ui.language` changes only presentation and does not alter operator values.
3. If Relay is used, verify reciprocal v2 peers before re-enabling it and confirm no legacy v1 endpoint is still expected by another server.
4. Behind a proxy, verify resolved client IPs and SSE behavior; temporarily enable `http.log-client-ip-resolution` when troubleshooting.
5. Test public chat, DM/group Reply, custom emoji, upload access, Web Push/notifications and the map/standalone frontend actually used by the deployment.
6. Keep the pre-upgrade backup until normal operation and retention cleanup have been observed.
