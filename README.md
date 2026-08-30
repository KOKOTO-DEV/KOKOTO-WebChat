# KOKOTO WebChat



![Architecture overview](docs/assets/architecture-5.1.0.svg)

> Visual manuals, animated flows, editable diagram sources, and standards references are included under `docs/assets/`, `docs/VISUAL_DOCUMENTATION.md`, and `docs/REFERENCES.md`.

## 5.1.0 release

5.1.0 upgrades server-to-server communication to group-scoped Relay Protocol v2 with reciprocal peer configuration and per-request authenticated encryption, adds persistent Web/game DM and group-chat replies including stable cross-server reply references, localizes config/reference/migration presentation from `ui.language`, hardens emoji catalog synchronization and SSE recovery, raises the default SSE limits to 10 per resolved client IP / 500 total, canonicalizes custom-emoji pack/file/token names, improves public reply rendering, and adds Android chat-composer Autofill suppression. Release notes and migration guidance describe the final 5.0.0 → 5.1.0 differences. Repeated operational HTTP/network failures use a shared state-aware console policy so identical retry errors do not accumulate indefinitely, while the first failure, state changes, and recovery remain visible.

## Project rename and download transition

**BlueMapWebChat (BMWC) was renamed to KOKOTO WebChat starting with 5.0.0.** The 5.0.0 transition release may initially be published through the existing BlueMapWebChat project listings so 4.7.0 installations that only know the legacy project can discover the upgrade. Existing BMWC 4.x data is migration input only; the 5.0.0 runtime identity is KOKOTO WebChat.

Current KOKOTO WebChat distribution addresses:

- Modrinth: `https://modrinth.com/plugin/bluemapwebchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`
- GitHub: `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`

During the current project-address transition, the 5.1.0 updater checks Modrinth `kokoto-webchat` first and falls back to the existing `bluemapwebchat` project when the canonical project is unavailable. The BMWC fallback remains a real update source until the address transition is complete; only when both sources fail is an update-check warning emitted.

A multi-platform server-side web chat for Minecraft. The Bukkit/Paper/Spigot platform can run as a BlueMap, squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, or Minecraft Overviewer embedded web chat, as a standalone page, or in multiple modes together. Fabric 1.18.2–26.2 exact-target, NeoForge 1.20.2–26.2 exact-target, and Forge 1.18.2–26.2 exact-target builds reuse the shared core/standalone frontend and support squaremap, Dynmap, LiveAtlas, uNmINeD, and Overviewer through filesystem adapters; Fabric also supports Pl3xMap through the same filesystem-adapter model, while Fabric/NeoForge and Forge 26.1.2/26.2 integrate with BlueMap 5.21+ through BlueMapAPI 2.8.0 when the BlueMap mod is present.

<img width="1057" height="682" alt="Image" src="https://github.com/user-attachments/assets/722761ea-94a4-4da9-be79-3cd04997c166" />

## Features

- Per-account visual UI profiles for signed-in users (default 5, admin configurable), with strict JSON profile import/export for moving settings between KWC servers
- Account-level keyword/notification preferences for signed-in users, while device-local window state and Web Push endpoints stay local; duplicate live-page OS notifications are suppressed when Web Push is active on that device
- Administrator-only Discord keyword alerts with KWC-owned matching/deduplication and DiscordSRV logical-channel selection in Web Admin
- Shared Unicode-aware content filter for public/group chat and optional DM, with block/mask/replace, N:1/1:N/N:N replacement rules, and DeathWord-style compact/interleave anti-evasion matching
- UTF-8 `filter-lists/*.txt` bulk filter-word lists with per-list Block/Filter mode plus custom block/mask/replace rules; Web Admin can import and manage list files
- Web Admin **Filter** and **Settings** controls plus `/kchat filter` / `/kchat settings` operational commands
- Custom rule recipes and concrete Block/Mask/Replace examples: [`docs/CONFIGURATION_EN.md`](docs/CONFIGURATION_EN.md#custom-filter-quick-guide)
- Session lifetime changes recalculate existing USER/MODERATOR or ADMIN sessions from their original creation time; `0` means unlimited
- Optional `upload.filename-mode: original` preserves safe Unicode source filenames for new uploads and resolves collisions without overwriting
- BlueMap/squaremap/Dynmap/Pl3xMap/LiveAtlas/uNmINeD/Overviewer embedded chat panel and standalone web chat page
- Two-way game ↔ web chat relay
- Relay Protocol v2 for group-scoped public chat and cross-server DM/read receipts, with request-by-request peer authentication, HKDF-SHA256/AES-256-GCM hop-by-hop authenticated encryption, replay protection, and HTTPS-only forwarding
- Clickable Minecraft replies (`/kchat reply`) and linked web-sender DM shortcuts (`/kchat dm`)
- Optional mirroring of game `/w`/`/msg`/`/tell`-style whispers into both users' KWC web DM thread
- Guest chat with math captcha, cooldowns, and a 50 messages/minute default guest rate limit
- `/kchat auth <code>` account linking, web password login, local admin accounts
- Web admin/moderator panel, message hiding, pin/delete action toggle, guest/IP mutes, session revoke
- Admin custom emoji manager: create, multi-file upload, rename, move, and delete emoji folders/files
- ImageEmojis-Bero 1.9.x compatibility for token-preserving web/game/reply/relay rendering
- File and clipboard upload, image/video/audio/YouTube/Shorts previews, plus optional TikTok and X/Twitter embeds
- DiscordSRV relay and Discord CDN media cache
- Message replies with clickable referenced-message previews, optional game-side reply previews, pinned messages, virtual scrolling, draggable/resizable window, PIP
- Optional 1:1 direct-message threads for linked/known players, with unread badges and per-thread retention
- Remote-server player discovery in the existing web DM recipient search
- Optional read-only administrator DM and group-chat content audit with exact-account allowlist and audit logging
- Built-in UI languages: en-US, ko-KR, ja-JP, zh-CN

## Companion plugin integrations

KWC 5.1.0 documents two optional Bukkit/Paper-family companion-plugin paths:

- [**ImageEmojis-Bero**](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) — share `plugins/KOKOTO-WebChat/emojis`, keep canonical `:pack/name:` tokens across web/history/relay, and let ImageEmojis render the game glyph. Its resource-pack server (`serverIp` + `webServerPort`, commonly TCP 5000) must be reachable by Minecraft clients. See [`docs/IMAGEEMOJIS_BERO_1_9_0_EN.md`](docs/IMAGEEMOJIS_BERO_1_9_0_EN.md). General plugin operation remains documented by [upstream ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis).
- [**SimpleNicks-Bero**](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) — set `player-display.mode: "display-name"` so KWC shows the Bukkit display name produced by the nickname plugin while retaining the linked username/UUID as the real identity. See [`docs/SIMPLENICKS_BERO_EN.md`](docs/SIMPLENICKS_BERO_EN.md). General plugin operation remains documented by [upstream SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks).

These integrations do not turn ImageEmojis/SimpleNicks into hard dependencies, and they should not be interpreted as Bukkit-plugin API support on KWC's Fabric/NeoForge/Forge server builds.

## Build

### Bukkit / Paper / Spigot

```bash
mvn clean package
```

```text
kwc-platform-bukkit/target/KOKOTO-WebChat-5.1.0-Bukkit-1.18-26.2.jar
```

### Fabric exact-target builds

Fabric is built as 16 exact-target JARs. The build helpers select JDK 17/21/25 according to the target.

```bat
kwc-platform-fabric\build-all.bat
```

```bash
./kwc-platform-fabric/build-all.sh
```

Targets: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. Each artifact is written as `kwc-platform-fabric/targets/<Minecraft>/build/libs/KOKOTO-WebChat-5.1.0-Fabric-<Minecraft>.jar`.

### NeoForge exact-target builds

NeoForge is built as 12 exact-target JARs. Minecraft 1.20.2–1.20.6 use the NeoGradle userdev generation; 1.21.1 and later targets use ModDevGradle. The build helpers select JDK 17/21/25 according to the target.

```bat
kwc-platform-neoforge\build-all.bat
```

```bash
./kwc-platform-neoforge/build-all.sh
```

Targets: `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. Each artifact is written as `kwc-platform-neoforge/targets/<Minecraft>/build/libs/KOKOTO-WebChat-5.1.0-NeoForge-<Minecraft>.jar`.

### Forge exact-target builds

Forge is built as 16 exact-target JARs instead of one wide-range artifact. On Windows:

```bat
kwc-platform-forge\build-all.bat
```

On Linux/macOS:

```bash
./kwc-platform-forge/build-all.sh
```

The Forge build helpers select JDK 17/21/25 per target and produce `KOKOTO-WebChat-5.1.0-Forge-<Minecraft>.jar` under each target's `build/libs/` directory.

### Final Windows release acceptance

Run `validate-release-windows.bat` from the source root to build Bukkit, all 16 Fabric targets, all 12 NeoForge targets, and all 16 Forge targets in one pass. A fully build-validated release must end with `FINAL RELEASE BUILD PASS`, collect exactly 45 deployable JARs under `release-5.1.0/`, and generate `SHA256SUMS.txt`.

For normal development builds on Windows, the same script supports platform selection, incremental cache reuse, parallel platform scheduling, and live progress:

```bat
validate-release-windows.bat --bukkit
validate-release-windows.bat --bukkit --fast
validate-release-windows.bat --fabric --forge --fast
validate-release-windows.bat --parallel
```

Platform flags may be combined. `--bukkit` builds only the Bukkit/Paper artifact and its required Maven reactor dependencies. `--fast` skips `clean`, reuses existing Maven/Gradle outputs and dependency caches, and enables the Gradle build cache. `--parallel` keeps the selected build mode, builds Bukkit first when it is selected, and after Bukkit passes runs the remaining selected loaders concurrently; therefore `validate-release-windows.bat --parallel` is still a clean 45-target release validation and may print `FINAL RELEASE BUILD PASS`. The console continuously shows elapsed time, overall completed targets, each platform count, and the current Minecraft target while full logs remain in `validation-logs/`. Partial or `--fast` builds are written under `build-5.1.0/` and never count as final release validation. Root `mvn clean package` remains a valid Bukkit-only Maven build and does not build Fabric/NeoForge/Forge.

## Fabric / NeoForge exact-target platforms

`kwc-platform-fabric` reuses `kwc-core` and `kwc-standalone-frontend`; it does not duplicate the HTTP/SSE/relay implementation. BlueMap mod integration uses the official BlueMapAPI web-app registration surface, while squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, and Overviewer use loader-neutral filesystem adapters on their supported platforms. It provides Fabric lifecycle, player/chat events, permission checks, YAML/account/session persistence, the complete `/kchat` game command surface, standalone hosting, Web Push, DM/group storage, uploads, moderation, and signed server relay.

Fabric data is stored in `config/KOKOTO-WebChat/`. The canonical KWC `config.yml` is reused. BlueMap is optional; direct BlueMapAPI integration is included only in the Fabric/NeoForge 26.1.2 and 26.2 exact targets when the BlueMap mod is present; DiscordSRV integration remains Bukkit-specific. Fabric and NeoForge route DM/group/reply/admin/guest/session commands through the shared core command service, while loader-specific reload/status/auth/password handling remains native to each platform.

See `kwc-platform-fabric/README.md` for Fabric-specific build and installation notes.

## Install

1. Put the Bukkit/Paper/Spigot JAR into `plugins/`; put the Fabric/NeoForge/Forge JAR into `mods/`.
2. Start the server once to generate `<KWC data dir>/config.yml`. `<KWC data dir>` is `plugins/KOKOTO-WebChat` on Bukkit-family servers and `config/KOKOTO-WebChat` on Fabric/NeoForge/Forge.
3. New generated configs start with top-level `enabled: false`; only the config is created until you review settings and opt in; /kchat reload remains available.
4. Review storage, retention, upload, preview, authentication, and web exposure settings, then set `enabled: true`.
5. For BlueMap embedded mode, set `adapters.bluemap.enabled: true`. On Bukkit-family servers, keep `auto-install` and `auto-patch-webapp-conf` enabled unless you manage BlueMap web files manually. Fabric/NeoForge 26.1.2/26.2 and Forge 26.1.2/26.2 register through BlueMapAPI when the BlueMap mod is present; older exact targets do not include the direct BlueMapAPI bridge.
6. For squaremap embedded mode, set `adapters.squaremap.enabled: true`; KWC only installs when an existing squaremap web root is detected.
7. For Dynmap embedded mode, set `adapters.dynmap.enabled: true`; KWC reads Dynmap `webpath`, installs its own assets, and maintains only a marked block in Dynmap `index.html`.
8. For Pl3xMap embedded mode on Bukkit/Paper-family or Fabric, set `adapters.pl3xmap.enabled: true`; KWC reads `settings.web-directory.path` from Pl3xMap `config.yml`, installs its own assets, and maintains only a marked block in Pl3xMap `index.html`. Current Pl3xMap 26.2 does not publish a NeoForge build.
9. For a LiveAtlas frontend on Bukkit, Fabric, NeoForge, or Forge, set `adapters.liveatlas.enabled: true`. KWC patches only an existing LiveAtlas `index.html`; set `web-root` when the LiveAtlas files are hosted in a custom shared/mounted directory. Do not enable both LiveAtlas and a backend-specific adapter for the same physical web root.
10. For a uNmINeD static web export, set `adapters.unmined.enabled: true`. uNmINeD is external to the Minecraft server, so set `web-root` to the exported site directory unless it is in one of the conservative auto-detection paths. Current `index.html` and legacy `unmined.index.html` exports are marker-checked before patching.
11. For a Minecraft Overviewer static web map, set `adapters.overviewer.enabled: true` and normally set `web-root` to Overviewer's generated `outputdir`. KWC requires Overviewer-specific generator/assets before patching `index.html`; a plain Leaflet site is not accepted.
12. For standalone-only mode, keep `frontend.standalone.enabled: true` and leave all map adapters disabled.
13. Restart the server or run `/kchat reload`. `/kchat reload` requests `bluemap reload light` after refreshing BlueMap. squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, and Overviewer web files are re-checked directly by KWC. Re-run `/kchat reload` after a map/site generator replaces its web files.


Existing parsed operator values are preserved, while active migration rebuilds comments and layout from the bundled presentation template selected by `ui.language`: `en-US` uses `config.yml`, and `ko-KR`, `ja-JP`, and `zh-CN` use their localized bundled templates. Unsupported/custom UI languages use the English config presentation. `<KWC data dir>/config-reference-5.1.0.yml` is an administrator-readable rendering of the current default in that same built-in language and is never used as migration input. A fixed older-version config is backed up before a real version migration. The migrated config is marked `config-version: "5.1.0_auto_migration"`; while that marker remains, startup/reload rebuilds from the selected current template and overlays the existing parsed values so new settings and current comments/layout stay synchronized. Exact `config-version: "5.1.0"` disables normal same-version automatic setting reconstruction, but changing `ui.language` can still rebuild only the comment/layout presentation while preserving every parsed value. `config-migration-5.1.0.yml` compares parsed YAML path/value semantics rather than comments, whitespace, quoting, line positions, or key order. Older generated reference/migration/upgrade files are removed automatically; internal version baselines remain for changed-default detection. Bundled UTF-8 starter filter lists `filter-lists/ko-KR.txt`, `en-US.txt`, `ja-JP.txt`, and `zh-CN.txt` are initialized once when the starter-list marker is absent, including on an existing data directory that predates this feature. Existing or disabled list files are never overwritten; after initialization, deleting a starter list is respected and it is not recreated on restart.

## 5.0.0 KOKOTO WebChat architecture and rename

5.0.0 establishes the KOKOTO WebChat multi-platform split. The Maven reactor contains `kwc-core`, `kwc-standalone-frontend`, `kwc-adapter-bluemap`, `kwc-adapter-squaremap`, `kwc-adapter-dynmap`, `kwc-adapter-pl3xmap`, `kwc-adapter-liveatlas`, `kwc-adapter-unmined`, `kwc-adapter-overviewer`, and `kwc-platform-bukkit`; Fabric and NeoForge are separate Gradle platform projects, and Forge uses an exact-target Gradle matrix; all reuse the shared sources. Java code uses the `dev.kokoto.webchat` package, the canonical game command is `/kchat` with `/kc` as its short alias, permissions use `kwc.*`, and reverse-proxy examples use `/chat`.

The previous BlueMapWebChat 4.x installation is migration input only: KOKOTO WebChat imports it only when `plugins/KOKOTO-WebChat` has no existing data files. It converts the old `web-addon.*` and `standalone-web.*` settings into `adapters.bluemap.*` and `frontend.standalone.*` and leaves the original directory untouched as a backup/migration source. An existing KWC data directory is never merged with BMWC data, even if `.legacy-import-complete` was deleted manually. The temporary marker is removed automatically after `plugins/BlueMapWebChat` no longer exists. `/bmchat`, `/bluemapchat`, `/bmc`, and `/kwc` are no longer command aliases. Legacy `bluemapwebchat.*` permission grants may still be accepted by the permission compatibility layer, but new configuration and documentation use `kwc.*`.

> **BMWC HTTPS migration:** the standard BMWC `/bmwc/api` and `/bmwc/chat` public layout moves to KWC's `/chat` layout. Standard BMWC API URL settings are normalized to empty automatic values, but Caddy/nginx files are not rewritten automatically and must be changed to the `/chat` prefix-stripping layout.


Platform-neutral chat/session/security models, public/DM/group persistence, signed relay, HTTP/SSE, Web Push and endpoint orchestration live in `kwc-core`. Shared BlueMap assets/config generation and Bukkit `webapp.conf` integration live in `kwc-adapter-bluemap`; the Java 25 `kwc-adapter-bluemap-api` bridge supplies Fabric/NeoForge and Forge 26.1.2/26.2 BlueMapAPI registration; squaremap web-directory/index integration lives in `kwc-adapter-squaremap`; Dynmap `webpath`/index integration lives in `kwc-adapter-dynmap`; Pl3xMap `settings.web-directory.path`/index integration lives in `kwc-adapter-pl3xmap`; LiveAtlas static-web-root/index integration lives in `kwc-adapter-liveatlas`; uNmINeD static-export/index integration lives in `kwc-adapter-unmined`; standalone assets live independently in `kwc-standalone-frontend`; loader lifecycle and Minecraft integration live in `kwc-platform-bukkit`, `kwc-platform-fabric`, `kwc-platform-neoforge`, and the exact-target `kwc-platform-forge` tree.

KOKOTO WebChat 5.1.0 uses Relay Protocol v2 with explicit `groups -> peers`, one shared secret per group, independent request-by-request peer authentication, a stateless diagnostic handshake endpoint, HKDF-SHA256 directional keys, and AES-256-GCM hop-by-hop payload protection. Relay v1/BMWC endpoints are no longer interoperable and return HTTP 426; see `docs/SERVER_RELAY_EN.md`.

## 4.7.0 multi-upload and compatibility

4.7.0 also adds configurable colon-delimited message tokens. English defaults cover newline, blank-line, and indentation actions, and administrators can add aliases in any language or printable custom substitutions. Unknown tokens remain untouched for emoji compatibility.

4.7.0 allows administrators to select multiple custom emoji image files at once using the same file-picker flow as normal chat uploads. The Upload control opens a hidden multi-file input; after files are selected, KOKOTO WebChat immediately copies the selection, clears the native input, and begins sequential upload without a second confirmation step. Upload progress and active-transfer cancel remain available, while per-file limits, total emoji storage limits, filename de-duplication, audit logging, and PNG sidecar generation continue through the existing server path.

The Bukkit/Spigot API baseline is lowered from 1.21 to 1.18 while the plugin remains on Java 17. The conservative supported Minecraft range for this release is **1.18 through 26.2**. Paper-specific `AsyncChatEvent` handling remains reflection-based and the Bukkit legacy chat event remains the fallback.

## 4.6.3 administrator group-chat audit

4.6.3 added optional read-only administrator access to group-chat message bodies. In 5.1.0, DM and group content auditing remain independent: `direct-message.admin-audit.enabled` controls DM-body audit and `group-chat.admin-audit.enabled` controls group-body audit. Both also require an exact account in `private-chat-super-admins`. Audit views are read-only and do not send, reply, hide, join, or change read state. Each audit page read is logged without copying message bodies into the audit log.

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

4.6.3 also fixes DM/group native video and audio playback during live refreshes. Private-chat message lists now reconcile existing messages by stable key like public chat, so loaded media DOM remains mounted while new messages and delivery/read metadata are updated. Playback therefore continues instead of restarting from the beginning.

See `docs/UPGRADE_4_6_3_EN.md` for upgrade details.

## 4.6.2 reliable private-message delivery

Remote DMs now remain `pending` until the destination server confirms that the message was stored. Delivery failures become retryable `failed` messages, and retries reuse the same relay ID to avoid duplicate receiver records. Web DM and group-chat sends also use client message IDs so an uncertain browser request can be retried without duplicating an already-stored message. Unqualified DM names always resolve on the current server; remote targets require explicit server-scoped selection. Read status is shown on every DM and group-chat message beside the timestamp. A 1:1 DM shows the short `Unread` label until its recipient reads it, then changes to `✓`; group chat keeps the unread-recipient count and changes to `✓` when the count reaches zero. Delivery UI is also compact: `Sending` while pending and `Failed · Retry` only when delivery cannot be confirmed.

All servers exchanging cross-server DMs should run KOKOTO WebChat 4.6.2 or later.

See `docs/UPGRADE_4_6_2_EN.md` for upgrade details.

## 4.6.1 exact cross-server DM routing

Remote DM recipients are now identified by both `server-id` and player UUID. Clicking `server · source` opens the conversation for that exact server/player pair, even when the same UUID exists on the local server. The message is sent through the signed private relay endpoint and stored by the destination server.

Every server participating in cross-server DM must run KOKOTO WebChat 4.6.1 or later.

## 4.6.1 cross-server DM discovery and administrator audit

When a relayed message contains a player UUID, that remote player is added to the existing New Message recipient search. No separate DM button is added. Guest and Discord senders without a UUID remain excluded.

4.6.1 introduced optional administrator DM-body auditing. KOKOTO WebChat 5.1.0 keeps that behavior: only exact accounts listed in `private-chat-super-admins` can open DM bodies, and only when `direct-message.admin-audit.enabled: true`. The audit view is read-only and each page read is audit-logged. `group-chat.admin-audit.enabled` remains a separate group-content audit control.

## Deployment modes

### BlueMap addon and standalone page together

```yaml
frontend:
  standalone:
    enabled: true
    path: "/"

adapters:
  bluemap:
    enabled: true
    auto-install: true
    auto-patch-webapp-conf: true
```

### Standalone only

```yaml
frontend:
  standalone:
    enabled: true
    path: "/"

adapters:
  bluemap:
    enabled: false
  squaremap:
    enabled: false
```

Standalone URL:

```text
http://<server-host>:8899/
```

## HTTPS / Caddy recommended setup

For public servers, keep BlueMap and KOKOTO WebChat as internal HTTP services and expose them through HTTPS.

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: "/chat"
  cors-origin: "https://map.example.com"

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""

upload:
  # Recommended: keep empty so uploads follow /chat/api automatically.
  # Legacy explicit forms also work: "/chat/api" or "/chat/api/uploads".
  public-base-url: ""
  # 0 = unlimited. Positive values cap total files in upload.directory.
  max-total-size-mb: 0

emoji:
  # Recommended: keep empty so emoji files follow /chat/api automatically.
  # Legacy explicit forms also work: "/chat/api" or "/chat/api/emojis".
  public-base-url: ""
  max-total-size-mb: 64
  show-storage-usage: true
  show-storage-limit: true
```

Example paths:

```text
https://map.example.com/          # BlueMap
https://map.example.com/chat/api  # KOKOTO WebChat API
https://map.example.com/chat # standalone chat
```


URL settings note: `http.path-prefix` is the internal KWC API route and `http.public-prefix` is the external reverse-proxy prefix. With the defaults, Caddy/Nginx strips `/chat`, so external `/chat` reaches internal `/` and external `/chat/api` reaches internal `/api`. Adapter and standalone `api-base-url` values normally stay empty unless a separate public API URL is required.

See `docs/CADDY_HTTPS_EN.md` for details.

## Common options

- `ui.language`: default UI language. `en-US`, `ko-KR`, `ja-JP`, `zh-CN`
- `ui.theme`: `system`, `dark`, `light`, `high-contrast`
- `player-display.mode`: `name`, `display-name`, `custom-name`
- `player-display.strip-colors`: when `false`, Minecraft legacy colors are rendered for actual chat sender names. System/event lines are always stripped.
- `guest.block-player-name-spoofing`: protects real usernames and known display/custom names; spoof comparison always strips Minecraft color/format codes even when `player-display.strip-colors` is `false`.
- `commands.enabled`: enable the web command panel
- `commands.allow-all`: allow arbitrary console commands instead of presets only
- `commands.run-from-chat-input`: allow `/command` execution from the normal chat input
- `ui.picture-in-picture.enabled`: controls both the PIP button and PIP execution

## SQLite history

Chat history uses SQLite by default in new configs (`chat.history-storage: "sqlite"`). This keeps long-lived logs in `<KWC data dir>/history.db` and makes paging, reply jumps, deletion, and retention cleanup easier to maintain than the legacy single JSONL file.

Legacy modes remain available: use `chat.history-storage: "jsonl"` for the old `history.jsonl` file, or `"memory"` for session-only history. `chat.history-size` and `chat.history-retention-days` apply to memory, JSONL, and SQLite. Newly generated configs start with top-level `enabled: false`, so cleanup cannot run until you review retention values and set `enabled: true`. If `chat.history-sqlite-migrate-jsonl` is true, an empty SQLite DB imports the existing JSONL history once.

A `/history/search` API and in-chat search modal are available for message text and sender searches, with optional date/time range, sender, source, and system/event filters. The search button is placed in the floating chat-panel area so the message input row stays compact, and the search modal follows the configured chat theme/font settings with a scrollable result list. i18n-backed system/event messages are searched and displayed in the selected web UI language when possible. Search can be disabled with `search.enabled`, and the single `search.result-limit` setting controls both the web UI result count and the `/history/search` API limit. There is no separate internal maximum: setting it to 2000 returns up to 2000 results, while setting it to 10 returns up to 10. Very large values such as 10000 or 100000 are accepted, but they can slow searches, increase response size, and add significant CPU, memory, and database load. The default is 50, and 50-200 is recommended for normal use. With `config-version: "5.1.0_auto_migration"`, missing search settings are inserted automatically on startup/reload. If same-version automatic migration has been disabled with exact `config-version: "5.1.0"`, add the missing keys manually or re-enable `_auto_migration`.


## Group chat rooms

`group-chat.enabled` enables the group-chat room system. Users can create rooms, choose public/private visibility, set an optional room password, invite known players, accept or decline invitations, leave rooms, hide rooms from their own list and restore them later, edit room settings, kick or ban members, unban users, transfer room ownership, and send/read group messages from both the Web UI and the game-side `/kchat group` commands. Public rooms appear in the room list; private rooms are invite-only. Room passwords are stored as PBKDF2 hashes, not plain text.

Each room also has a **member join/leave notices** option. When enabled, actual membership changes are stored as `member_join` / `member_leave` events and shown in Web/group history and to online game members. Accepting an invite or joining records an entry; leaving, kick, or ban records an exit. Closing the group-chat window, switching rooms, or hiding a room does **not** count as leaving and never creates an exit event. Membership events cannot be used as Reply targets.

Group chats use a dedicated SQLite store (`group-chat.sqlite-file`, default `group-messages.db`). `group-chat.retention-days: 0` means no time limit; positive values are shown next to the group-chat title and old group messages are physically removed after that many days. `group-chat.max-messages-per-room: 0` disables count-based cleanup. Existing SQLite databases are upgraded in place when optional 5.1.0 columns are missing.

## Direct message threads

`direct-message.enabled` enables optional 1:1 conversation threads. Targets include linked or previously known players and remote-server senders whose relayed message contains a player UUID. The latest relayed display name and real Minecraft name are added to the web DM recipient search, so a conversation can be started from the normal search without a separate DM button. Guest and Discord senders without a player UUID are excluded. A->B and B->A use the same thread, and messages are stored by UUID while the UI displays `display name (real account name)` when both are available.

DMs use an independent private-message store. `direct-message.storage: auto` follows `chat.history-storage` when public chat uses `jsonl`; otherwise it uses SQLite. You can also set `direct-message.storage` to `sqlite` or `jsonl` explicitly, using `direct-message.sqlite-file` or `direct-message.jsonl-file`. `direct-message.retention-days: 0` means no time limit; otherwise the DM window title shows the configured retention period and old DM rows are physically removed after that many days. `direct-message.max-messages-per-thread: 0` disables count-based cleanup. `direct-message.confirm-hide` controls whether the web UI asks before hiding a DM from your own view. Because private messages are stored on the server, the feature is disabled by default and should be enabled only after setting a server policy.


Game `/w`, `/msg`, `/tell`, and compatible aliases can be mirrored into the same web DM thread with `direct-message.capture-game-whispers`. Clicking a local game sender suggests `/w <realName> `; linked web senders suggest `/kchat dm <realName> `; remote-server game senders suggest `/kchat dm <realName>@<server-id> ` so same-name or same-UUID players on different servers remain distinct. Typing `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, or `/t` with the same `name@server-id` target routes that message through the cross-server KWC DM relay. An unqualified `/kchat dm <name>` always resolves on the current server; a remote target must include `@server-id` or be explicitly selected from the web UI.

## Custom emoji and game-side emoji plugins

KOKOTO WebChat stores custom emoji files under `<KWC data dir>/emojis`. Subfolders are treated as emoji packs. In 5.1.0, pack directory names and emoji filename stems use the same token-safe canonical naming rule: whitespace/unsupported characters are removed, existing invalid names are migrated at startup, and collisions receive numeric suffixes. The resulting on-disk path directly matches `:pack/name:`.

By default, web-to-game chat preserves custom emoji tokens such as `:default/wave:` and `:emoji:default/wave:`. Use this default when ImageEmojis or another game-side emoji plugin renders the same token text in Minecraft chat.

`emoji.game-link.mode` supports `preserve`, `link`, and `label` when `emoji.game-link.enabled` is enabled.

- `preserve`: keeps the original token text unchanged.
- `link`: sends the configured token text plus a short KOKOTO WebChat image link.
- `label`: sends only the configured token text.

`emoji.game-link.*` only affects web-to-Minecraft chat. Discord image preview links are controlled separately: `discordsrv.append-web-emoji-links` handles web→Discord messages, and `discordsrv.append-game-emoji-links` scans the `:emoji:` token text in DiscordSRV's actual game post and passes it through the same registered KWC token-to-link routine. In shared channels the early game-chat fingerprint is used only to identify the origin server; Minecraft glyphs are not Discord conversion input. Receiving relay peers never re-send the message to Discord. Leave `game-relay-mode: "discordsrv"` when DiscordSRV already relays normal Minecraft chat to avoid duplicate posts.

KOKOTO WebChat keeps the canonical token text in web history and server-relay payloads. When ImageEmojis or ImageEmojis-Bero is enabled, KWC reads its public runtime emoji repository through reflection and uses the receiving server's current token-to-glyph mapping when it builds clickable Minecraft components. No hard plugin dependency or resource-pack parsing is required; unresolved tokens still fall back to the normal game-side rendering path.

When GIF/JPG/JPEG/WEBP emoji files are uploaded, KOKOTO WebChat also creates a same-folder PNG sidecar for compatibility with game-side emoji plugins that only read PNG files:

```text
<KWC data dir>/emojis/default/wave.gif
<KWC data dir>/emojis/default/wave.png
```

The web UI keeps using the original file, so GIF animation is preserved. A game-side emoji plugin may use the PNG sidecar if it watches the same emoji directory. Run that plugin's reload command after adding or changing emoji files.

Detailed setup, relay, reply-command, permission, reload, and troubleshooting notes: [`docs/IMAGEEMOJIS_BERO_1_9_0_EN.md`](docs/IMAGEEMOJIS_BERO_1_9_0_EN.md).

## YouTube Shorts, TikTok, and X/Twitter previews

YouTube Shorts URLs are handled by the normal YouTube preview, use a vertical player, and are enabled by default. TikTok and X/Twitter embeds are available as optional social embeds and are disabled by default because they load third-party content. TikTok uses the official `player/v1` iframe with long description/music text hidden in the chat panel; users can open the original TikTok link for full details.

```yaml
preview:
  youtube-embed-enabled: true
  social-embeds:
    enabled: true
    click-to-load: true
    max-embeds-per-message: 2
    tiktok:
      enabled: false
    x:
      enabled: false
```

Enable TikTok or X/Twitter only if you are comfortable with third-party embed requests from users' browsers. Keep `click-to-load: true` for public servers so third-party content loads only after a user opens a preview.

## Commands

```text
/kchat dm <player> <message>
/kchat reply <messageId> <message>
/kchat auth <code>
/kchat password <newPassword>
/kchat reload
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
/kchat guest mute <guest|ip> <value> [minutes] [reason]
/kchat guest unmute <guest|ip> <value>
/kchat guest list
/kchat sessions
/kchat revoke <username>
```

## Permissions

```text
kwc.auth
kwc.webchat
kwc.dm
kwc.reply
kwc.group
kwc.admin
kwc.update.notify
```

## Documentation

- `docs/USER_MANUAL_EN.md` - complete user and operator manual for all features
- `docs/CONFIGURATION_EN.md` - configuration reference
- `docs/SERVER_RELAY_EN.md` - Relay Protocol v2 public chat, cross-server DM/read receipts, trust and forwarding rules
- `docs/UPGRADE_5_0_0_EN.md` - 4.7.0 to 5.0.0 major upgrade and migration
- `docs/UPGRADE_4_7_0_EN.md` - 4.6.3 to 4.7.0 upgrade
- `wiki/` - GitHub Wiki source pages using safe page names without `and` / `&`
- `docs/UPGRADE_4_6_2_EN.md` - 4.6.1 to 4.6.2 upgrade
- `docs/UPGRADE_4_6_1_EN.md` - 4.6.0 to 4.6.1 upgrade
- `docs/UPGRADE_4_6_0_EN.md` - 4.5.5 to 4.6.0 config/database upgrade
- `docs/CADDY_HTTPS_EN.md` - HTTPS reverse proxy setup
- `docs/I18N_EN.md` - language files and fallback behavior
- `docs/INSTALL_TROUBLESHOOTING_EN.md` - install, upgrade, troubleshooting
- `docs/UPLOAD_SECURITY_EN.md` - upload security notes
- `docs/RELEASE_CHECKLIST_EN.md` - release checklist
- `docs/STANDALONE_REVIEW_EN.md` - BlueMap dependency and standalone mode review
- `docs/OPERATIONS_SECURITY_EN.md` - public deployment, trusted proxy logs, and security checklist

## Security note

HTTP-only mode is supported for private/testing use. Passwords are stored hashed on the server, but HTTP login traffic is not encrypted. Use HTTPS for public servers.

Font note: Installed fonts must be typed by their CSS font-family name. Chat settings include a Test button that estimates whether the typed name is available in the current browser without requesting local-font permissions.

### Private chat metadata super admins

Set `private-chat-super-admins` in `config.yml` to exact UUIDs or Minecraft names for users who may see DM/group-chat metadata for moderation/accounting. This view shows titles/participants, message counts, approximate stored byte sizes, retention status, cleanup preview counts, locks and cleanup exclusions. `direct-message.admin-audit.enabled: true` lets those explicitly listed accounts open DM bodies in a read-only audit view; `group-chat.admin-audit.enabled: true` independently enables the group-body audit view. Ordinary ADMIN/MODERATOR roles do not qualify automatically, and every audit page read is logged.

Administrative actions are also appended to date-based text audit files under `<KWC data dir>/audit` by default. The audit log is intended for server operators and is not shown in the web UI.


Note: `frontend.standalone.app-name` / `frontend.standalone.app-short-name` can change the mobile Home Screen web app name, and `web-push.notification-title` can change the default push title. Server notifications can be set to all, join/leave only, or off in Chat settings. If `web-push.notification-title` is empty, `frontend.standalone.app-name` is used. Android/desktop browsers can enable push from either the BlueMap addon or the standalone page when HTTPS and Push API support are available. On iOS/iPadOS, use a page added to the Home Screen and opened as a web app rather than a normal browser tab.


Existing configs that still contain legacy generated display names such as `BlueMapWebChat` or `BM WebChat` are treated as placeholders so they no longer appear as push titles by default.


- Pl3xMap integration: `docs/PL3XMAP_INTEGRATION.md`
- LiveAtlas integration: `docs/LIVEATLAS_INTEGRATION.md`
- uNmINeD integration: `docs/UNMINED_INTEGRATION.md`

## Overviewer

- Overviewer integration: `docs/OVERVIEWER_INTEGRATION.md`

## Forge Stage 1

Forge uses exact-target server JARs for Minecraft 1.18.2 through 26.2. The Forge tree is split into `src/common` plus `compat118`, `compatClassic`, `compatModern`, and `compat26`; see `kwc-platform-forge/README.md` and `docs/FORGE_INTEGRATION.md`. Direct BlueMapAPI integration is limited to Forge 26.1.2/26.2; older Forge targets use the loader-neutral filesystem/static-map adapters. Build helpers select JDK 17/21/25 per exact target; use `kwc-platform-forge/build-all.bat` (Windows) or `build-all.sh` instead of forcing every ForgeGradle generation through one system JVM.

## AI assistance disclosure

Generative AI was used as a development assistant for code review, implementation and patching, documentation drafting, and multilingual translation. Project requirements, architecture and design decisions, source integration, testing, compatibility verification, release validation, and final acceptance are directed and reviewed by the human maintainer. AI-assisted output is reviewed and validated before inclusion. See `AI_USAGE.md` for details.
