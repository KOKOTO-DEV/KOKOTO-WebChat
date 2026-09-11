# KOKOTO WebChat



![Architecture overview](docs/assets/architecture-5.3.0.svg)

[PNG](docs/assets/architecture-5.3.0.png) · [SVG](docs/assets/architecture-5.3.0.svg)

> Visual manuals, animated flows, editable diagram sources, and standards references are included under `docs/assets/`, `docs/en/VISUAL_DOCUMENTATION.md`, and `docs/en/REFERENCES.md`.

## 5.3.0 release

5.3.0 builds directly on 5.2.1 with retained-history search and sender-owned deletion for private chat, room-local group roles/pins/deletion policy, multi-event First come/Lottery Chat Events, user profiles and Game/Web presence privacy, personal blocking, delegated moderator capabilities, expanded built-in CAPTCHA, Web mention autocomplete, and image metadata stripping. Relay Protocol **2.2** keeps protocol-major-2 compatibility while adding capability-gated DM deletion, event routing, remote profile lookup, and independent per-peer send/receive policy for `public-chat`, `event`, `dm`, and `profile`. In **Standalone desktop mode**, DM/group list windows and multiple private conversations can use the independent drag/resize/maximize multi-window workflow. All eight adapter/standalone bundles are generated from the same frontend fragments/CSS, while responsive public-header and minimized-frame behavior remains synchronized across adapters.



- **5.2.0 controls:** `notifications.notify-reactions` provides one Reactions choice shared by browser notifications and Web Push; `chat.conversation-archive.enabled: false` hard-disables Saved-conversation DOM/API/DB startup while preserving existing archive data; the three archive `max-*` keys control saved-conversation quotas; `chat.typing-indicator.open-chat.enabled`, `.dm.enabled`, and `.group-chat.enabled` are server-wide typing-indicator policy switches (defaults OFF/ON/ON) exposed in Web Admin Settings; `chat.typing-indicator.user-display-control` (default OFF) lets the administrator optionally expose one account-level **Typing indicators** viewer setting; `emoji.favorites.enabled`, `storage`, and `max-per-account` control whether custom-emoji Favorites exist and whether they are browser-local or account-stored.

## 5.2.0 release

5.2.0 focuses on security hardening, private-chat usability, and multi-server continuity. It adds reactions for public, DM, and group messages, Relay Protocol **2.1** as a backward-compatible 2.x capability revision, event-driven public/DM/group typing indicators, and per-account **Saved conversations** snapshots with browser PDF export. Guest/public-history access checks, trusted-proxy handling, exact API leaf routing, and public config projection are hardened. The latest-message auto-follow threshold is unified at **32 px** for public/DM/group chat, while emoji/icon/attachment panel layout changes preserve the current viewport instead of forcing an immediate jump to the bottom. Current update/download addresses use only KOKOTO WebChat projects; historical BMWC addresses remain migration references only.

## Project rename and download addresses

**BlueMapWebChat (BMWC) was renamed to KOKOTO WebChat starting with 5.0.0.** Existing BMWC 4.x data remains migration input only. Starting with 5.2.0, runtime update checks and current download links use only KOKOTO WebChat addresses.

Current KOKOTO WebChat distribution addresses:

- Modrinth: `https://modrinth.com/plugin/kokoto-webchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`
- GitHub: `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`

Starting with 5.2.0, the updater checks only the canonical Modrinth `kokoto-webchat` project. Legacy BMWC project addresses are no longer queried or presented as active download sources.

A multi-platform server-side web chat for Minecraft. The Bukkit/Paper/Spigot platform can run as a BlueMap, squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, or Minecraft Overviewer embedded web chat, as a standalone page, or in multiple modes together. Fabric 1.18.2–26.2 exact-target, NeoForge 1.20.2–26.2 exact-target, and Forge 1.18.2–26.2 exact-target builds reuse the shared core/standalone frontend and support squaremap, Dynmap, LiveAtlas, uNmINeD, and Overviewer through filesystem adapters; Fabric also supports Pl3xMap through the same filesystem-adapter model, while Fabric/NeoForge and Forge 26.1.2/26.2 integrate with BlueMap 5.21+ through BlueMapAPI 2.8.0 when the BlueMap mod is present.

<img width="1057" height="682" alt="Image" src="https://github.com/user-attachments/assets/722761ea-94a4-4da9-be79-3cd04997c166" />

## Features

- Per-account visual UI profiles for signed-in users (default 5, admin configurable), with strict JSON profile import/export for moving settings between KWC servers
- Account-level keyword/notification preferences for signed-in users, while device-local window state and Web Push endpoints stay local; when any signed-in KWC client is actively viewing the exact DM/group conversation, browser notifications, the browser-local notification inbox, and Web Push for that conversation are suppressed account-wide
- Administrator-only Discord keyword alerts with KWC-owned matching/deduplication and DiscordSRV logical-channel selection in Web Admin
- Shared Unicode-aware content filter for public/group chat and optional DM, with block/mask/replace, N:1/1:N/N:N replacement rules, and DeathWord-style compact/interleave anti-evasion matching
- UTF-8 `filter-lists/*.txt` bulk filter-word lists with per-list Block/Filter mode plus custom block/mask/replace rules; Web Admin can import and manage list files
- Web Admin **Filter** and **Settings** controls plus `/kchat filter` / `/kchat settings` operational commands
- Custom rule recipes and concrete Block/Mask/Replace examples: [`docs/en/CONFIGURATION.md`](docs/en/CONFIGURATION.md#custom-filter-quick-guide)
- Session lifetime changes recalculate existing USER/MODERATOR or ADMIN sessions from their original creation time; `0` means unlimited
- Optional `upload.filename-mode: original` preserves safe Unicode source filenames for new uploads and resolves collisions without overwriting
- BlueMap/squaremap/Dynmap/Pl3xMap/LiveAtlas/uNmINeD/Overviewer embedded chat panel and standalone web chat page
- Two-way game ↔ web chat relay
- Relay Protocol 2.2 over the Relay v2 trust/encryption model for public chat and cross-server DM/read receipts, with reaction/typing capabilities plus sender-authoritative targeted DM delete, request-by-request peer authentication, HKDF-SHA256/AES-256-GCM hop-by-hop authenticated encryption, replay protection, and HTTPS-only forwarding
- Saved-conversation snapshots for public/DM/group ranges, with administrator deletion/lock policy precedence and browser PDF export
- Event-driven public/DM/group typing indicators with a five-second window and no polling/persistence
- Public/DM/group message reactions for logged-in users, including Unicode and KWC custom emoji, a compact message-bottom hover `+` slot that expands to the normal row only when reactions exist, category/search picker with stable positioning/outside-click close, reactor-name hover lists, and **Admin > Emojis > Reaction icons** management with master enable/disable and administrator-managed search aliases; public reactions synchronize through origin authority, cross-server DM reactions are sent only to the participant server, and group reactions remain local
- Clickable Minecraft replies (`/kchat reply`) and linked web-sender DM shortcuts (`/kchat dm`)
- Optional mirroring of game `/w`/`/msg`/`/tell`-style whispers into both users' KWC web DM thread
- Guest chat with math captcha, cooldowns, and a 50 messages/minute default guest rate limit
- `/kchat auth <code>` account linking, web password login, local admin accounts
- Web admin/moderator panel, message deletion, pin/delete action toggle, guest/IP mutes, session revoke
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

KWC 5.2.0 documents two optional Bukkit/Paper-family companion-plugin paths:

- [**ImageEmojis-Bero**](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) — share `plugins/KOKOTO-WebChat/emojis`, keep canonical `:pack/name:` tokens across web/history/relay, and let ImageEmojis render the game glyph. Its resource-pack server (`serverIp` + `webServerPort`, commonly TCP 5000) must be reachable by Minecraft clients. See [`docs/en/IMAGEEMOJIS_BERO_1_9_0.md`](docs/en/IMAGEEMOJIS_BERO_1_9_0.md). General plugin operation remains documented by [upstream ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis).
- [**SimpleNicks-Bero**](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) — set `player-display.mode: "display-name"` so KWC shows the Bukkit display name produced by the nickname plugin while retaining the linked username/UUID as the real identity. See [`docs/en/SIMPLENICKS_BERO.md`](docs/en/SIMPLENICKS_BERO.md). General plugin operation remains documented by [upstream SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks).

These integrations do not turn ImageEmojis/SimpleNicks into hard dependencies, and they should not be interpreted as Bukkit-plugin API support on KWC's Fabric/NeoForge/Forge server builds.

## Build

### Bukkit / Paper / Spigot

```bash
mvn clean package
```

```text
kwc-platform-bukkit/target/KOKOTO-WebChat-5.3.0-Bukkit-1.18-26.2.jar
```

### Fabric exact-target builds

Fabric is built as 16 exact-target JARs. The build helpers select JDK 17/21/25 according to the target.

```bat
kwc-platform-fabric\build-all.bat
```

```bash
./kwc-platform-fabric/build-all.sh
```

Targets: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. Each artifact is written as `kwc-platform-fabric/targets/<Minecraft>/build/libs/KOKOTO-WebChat-5.3.0-Fabric-<Minecraft>.jar`.

### NeoForge exact-target builds

NeoForge is built as 12 exact-target JARs. Minecraft 1.20.2–1.20.6 use the NeoGradle userdev generation; 1.21.1 and later targets use ModDevGradle. The build helpers select JDK 17/21/25 according to the target.

```bat
kwc-platform-neoforge\build-all.bat
```

```bash
./kwc-platform-neoforge/build-all.sh
```

Targets: `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`. Each artifact is written as `kwc-platform-neoforge/targets/<Minecraft>/build/libs/KOKOTO-WebChat-5.3.0-NeoForge-<Minecraft>.jar`.

### Forge exact-target builds

Forge is built as 16 exact-target JARs instead of one wide-range artifact. On Windows:

```bat
kwc-platform-forge\build-all.bat
```

On Linux/macOS:

```bash
./kwc-platform-forge/build-all.sh
```

The Forge build helpers select JDK 17/21/25 per target and produce `KOKOTO-WebChat-5.3.0-Forge-<Minecraft>.jar` under each target's `build/libs/` directory.

### Final Windows release acceptance

> **The release build/validation workflow is included in the source package.** `validate-release-windows.bat` and the PowerShell helpers it requires are shipped with the source. The separate `KWC-5.3.0-validation-tools.zip` contains development-only browser regression tooling and is not required for normal or release builds.

Run `validate-release-windows.bat` from the source root to build Bukkit, all 16 Fabric targets, all 12 NeoForge targets, and all 16 Forge targets in one pass. A fully build-validated release must end with `FINAL RELEASE BUILD PASS`, collect exactly 45 deployable JARs under `release-5.3.0/`, and generate `SHA256SUMS.txt`. The same release gate also runs the loader-neutral security and Relay/reaction/typing regression harnesses, adapter/config migration harnesses, and a finished-Bukkit-JAR conversation-archive runtime smoke that opens the shaded SQLite driver and exercises save/read/rename/quota/admin-delete cascade behavior.

For normal development builds on Windows, the same script supports platform selection, incremental cache reuse, parallel platform scheduling, and live progress:

```bat
validate-release-windows.bat --bukkit
validate-release-windows.bat --bukkit --fast
validate-release-windows.bat --fabric --forge --fast
validate-release-windows.bat --parallel
```

Platform flags may be combined. `--bukkit` builds only the Bukkit/Paper artifact and its required Maven reactor dependencies. `--fast` skips `clean`, reuses existing Maven/Gradle outputs and dependency caches, and enables the Gradle build cache. `--parallel` keeps the selected build mode, builds Bukkit first when it is selected, and after Bukkit passes opens separate live build windows for Fabric, NeoForge, and Forge and runs them concurrently; therefore `validate-release-windows.bat --parallel` is still a clean 45-target release validation and may print `FINAL RELEASE BUILD PASS`. The main console continuously shows elapsed time, overall completed targets, each platform count, and the current Minecraft target while each worker window shows its actual build log and full logs remain in `validation-logs/`. Partial or `--fast` builds are written under `build-5.3.0/` and never count as final release validation. Root `mvn clean package` remains a valid Bukkit-only Maven build and does not build Fabric/NeoForge/Forge.
If a loader worker fails with a recognized Gradle cache/workspace corruption or cache-lock signature (for example an unreadable `caches/<Gradle>/transforms/.../metadata.bin`), the validation runner does not delete the possibly locked primary cache. It retries that platform once with a fresh isolated cache under `.build-cache/gradle-recovery/`. Source compilation and ordinary dependency/build failures are never retried. A successful recovery leaves the original cache untouched so it can be cleaned manually after Explorer, antivirus, or another locking process releases it.


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


Existing parsed operator values are preserved, while active migration rebuilds comments and layout from the bundled presentation template selected by `ui.language`: `en-US` uses `config.yml`, and `ko-KR`, `ja-JP`, and `zh-CN` use their localized bundled templates. Unsupported/custom UI languages use the English config presentation. `<KWC data dir>/config-reference-5.3.0.yml` is an administrator-readable rendering of the current default in that same built-in language and is never used as migration input. A fixed older-version config is backed up before a real version migration. The migrated config is marked `config-version: "5.3.0_auto_migration"`; while that marker remains, startup/reload rebuilds from the selected current template and overlays the existing parsed values so new settings and current comments/layout stay synchronized. Exact `config-version: "5.3.0"` disables normal same-version automatic setting reconstruction, but changing `ui.language` can still rebuild only the comment/layout presentation while preserving every parsed value. `config-migration-5.3.0.yml` compares parsed YAML path/value semantics rather than comments, whitespace, quoting, line positions, or key order. Older generated reference/migration/upgrade files are removed automatically; internal version baselines remain for changed-default detection. Bundled UTF-8 starter filter lists `filter-lists/ko-KR.txt`, `en-US.txt`, `ja-JP.txt`, and `zh-CN.txt` are initialized once when the starter-list marker is absent, including on an existing data directory that predates this feature. Existing or disabled list files are never overwritten; after initialization, deleting a starter list is respected and it is not recreated on restart.

## 5.0.0 KOKOTO WebChat architecture and rename

5.0.0 establishes the KOKOTO WebChat multi-platform split. The Maven reactor contains `kwc-core`, `kwc-standalone-frontend`, `kwc-adapter-bluemap`, `kwc-adapter-squaremap`, `kwc-adapter-dynmap`, `kwc-adapter-pl3xmap`, `kwc-adapter-liveatlas`, `kwc-adapter-unmined`, `kwc-adapter-overviewer`, and `kwc-platform-bukkit`; Fabric and NeoForge are separate Gradle platform projects, and Forge uses an exact-target Gradle matrix; all reuse the shared sources. Java code uses the `dev.kokoto.webchat` package, the canonical game command is `/kchat` with `/kc` as its short alias, permissions use `kwc.*`, and reverse-proxy examples use `/chat`.

The previous BlueMapWebChat 4.x installation is migration input only: KOKOTO WebChat imports it only when `plugins/KOKOTO-WebChat` has no existing data files. It converts the old `web-addon.*` and `standalone-web.*` settings into `adapters.bluemap.*` and `frontend.standalone.*` and leaves the original directory untouched as a backup/migration source. An existing KWC data directory is never merged with BMWC data, even if `.legacy-import-complete` was deleted manually. The temporary marker is removed automatically after `plugins/BlueMapWebChat` no longer exists. `/bmchat`, `/bluemapchat`, `/bmc`, and `/kwc` are no longer command aliases. Legacy `bluemapwebchat.*` permission grants may still be accepted by the permission compatibility layer, but new configuration and documentation use `kwc.*`.

> **BMWC HTTPS migration:** the standard BMWC `/bmwc/api` and `/bmwc/chat` public layout moves to KWC's `/chat` layout. Standard BMWC API URL settings are normalized to empty automatic values, but Caddy/nginx files are not rewritten automatically and must be changed to the `/chat` prefix-stripping layout.


Platform-neutral chat/session/security models, public/DM/group persistence, signed relay, HTTP/SSE, Web Push and endpoint orchestration live in `kwc-core`. Shared BlueMap assets/config generation and Bukkit `webapp.conf` integration live in `kwc-adapter-bluemap`; the Java 25 `kwc-adapter-bluemap-api` bridge supplies Fabric/NeoForge and Forge 26.1.2/26.2 BlueMapAPI registration; squaremap web-directory/index integration lives in `kwc-adapter-squaremap`; Dynmap `webpath`/index integration lives in `kwc-adapter-dynmap`; Pl3xMap `settings.web-directory.path`/index integration lives in `kwc-adapter-pl3xmap`; LiveAtlas static-web-root/index integration lives in `kwc-adapter-liveatlas`; uNmINeD static-export/index integration lives in `kwc-adapter-unmined`; standalone assets live independently in `kwc-standalone-frontend`; loader lifecycle and Minecraft integration live in `kwc-platform-bukkit`, `kwc-platform-fabric`, `kwc-platform-neoforge`, and the exact-target `kwc-platform-forge` tree.

KOKOTO WebChat 5.2.0 uses Relay Protocol v2 with explicit `groups -> peers`, one shared secret per group, independent request-by-request peer authentication, a stateless diagnostic handshake endpoint, HKDF-SHA256 directional keys, and AES-256-GCM hop-by-hop payload protection. Relay v1/BMWC endpoints are no longer interoperable and return HTTP 426; see `docs/en/SERVER_RELAY.md`.

## 4.7.0 multi-upload and compatibility

4.7.0 also adds configurable colon-delimited message tokens. English defaults cover newline, blank-line, and indentation actions, and administrators can add aliases in any language or printable custom substitutions. Unknown tokens remain untouched for emoji compatibility.

4.7.0 allows administrators to select multiple custom emoji image files at once using the same file-picker flow as normal chat uploads. The Upload control opens a hidden multi-file input; after files are selected, KOKOTO WebChat immediately copies the selection, clears the native input, and begins sequential upload without a second confirmation step. Upload progress and active-transfer cancel remain available, while per-file limits, total emoji storage limits, filename de-duplication, audit logging, and PNG sidecar generation continue through the existing server path.

The Bukkit/Spigot API baseline is lowered from 1.21 to 1.18 while the plugin remains on Java 17. The conservative supported Minecraft range for this release is **1.18 through 26.2**. Paper-specific `AsyncChatEvent` handling remains reflection-based and the Bukkit legacy chat event remains the fallback.

## 4.6.3 administrator DM and group-chat audit

4.6.3 added optional read-only administrator access to group-chat message bodies. In 5.2.0, DM and group content auditing remain independent: `direct-message.admin-audit.enabled` controls DM-body audit and `group-chat.admin-audit.enabled` controls group-body audit. Both also require an exact account in `private-chat-super-admins`. Audit views are read-only and do not send, reply, delete, join, or change read state. DM audit reads are logged as `admin.dm-audit-read`; group-chat audit reads are logged as `admin.group-audit-read`. Message bodies are not copied into the audit log.

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

direct-message:
  admin-audit:
    enabled: true

group-chat:
  admin-audit:
    enabled: true
```

4.6.3 also fixes DM/group native video and audio playback during live refreshes. Private-chat message lists now reconcile existing messages by stable key like public chat, so loaded media DOM remains mounted while new messages and delivery/read metadata are updated. Playback therefore continues instead of restarting from the beginning.

See `docs/en/UPGRADE.md` for upgrade details.

## 4.6.2 reliable private-message delivery

Remote DMs now remain `pending` until the destination server confirms that the message was stored. Delivery failures become retryable `failed` messages, and retries reuse the same relay ID to avoid duplicate receiver records. Web DM and group-chat sends also use client message IDs so an uncertain browser request can be retried without duplicating an already-stored message. Unqualified DM names always resolve on the current server; remote targets require explicit server-scoped selection. Read status is shown on every DM and group-chat message beside the timestamp. A 1:1 DM shows the short `Unread` label until its recipient reads it, then changes to `✓`; group chat keeps the unread-recipient count and changes to `✓` when the count reaches zero. Delivery UI is also compact: `Sending` while pending and `Failed · Retry` only when delivery cannot be confirmed.

All servers exchanging cross-server DMs should run KOKOTO WebChat 4.6.2 or later.

See `docs/en/UPGRADE.md` for upgrade details.

## 4.6.1 exact cross-server DM routing

Remote DM recipients are now identified by both `server-id` and player UUID. Clicking `server · source` opens the conversation for that exact server/player pair, even when the same UUID exists on the local server. The message is sent through the signed private relay endpoint and stored by the destination server.

Every server participating in cross-server DM must run KOKOTO WebChat 4.6.1 or later.

## 4.6.1 cross-server DM discovery and administrator audit

When a relayed message contains a player UUID, that remote player is added to the existing New Message recipient search. No separate DM button is added. Guest and Discord senders without a UUID remain excluded.

4.6.1 introduced optional administrator DM-body auditing. KOKOTO WebChat 5.2.0 keeps that behavior: only exact accounts listed in `private-chat-super-admins` can open DM bodies, and only when `direct-message.admin-audit.enabled: true`. The audit view is read-only and each page read is audit-logged. `group-chat.admin-audit.enabled` remains a separate group-content audit control.

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

See `docs/en/CADDY_HTTPS.md` for details.

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

A `/history/search` API and in-chat search modal are available for message text and sender searches, with optional date/time range, sender, source, and system/event filters. The search button is placed in the floating chat-panel area so the message input row stays compact, and the search modal follows the configured chat theme/font settings with a scrollable result list. i18n-backed system/event messages are searched and displayed in the selected web UI language when possible. Search can be disabled with `search.enabled`, and the single `search.result-limit` setting controls both the web UI result count and the `/history/search` API limit. There is no separate internal maximum: setting it to 2000 returns up to 2000 results, while setting it to 10 returns up to 10. Very large values such as 10000 or 100000 are accepted, but they can slow searches, increase response size, and add significant CPU, memory, and database load. The default is 50, and 50-200 is recommended for normal use. With `config-version: "5.3.0_auto_migration"`, missing search settings are inserted automatically on startup/reload. If same-version automatic migration has been disabled with exact `config-version: "5.3.0"`, add the missing keys manually or re-enable `_auto_migration`.


## Group chat rooms

`group-chat.enabled` enables the group-chat room system. Users can create rooms, choose public/private visibility, set an optional room password, invite known players, accept or decline invitations, leave rooms, hide rooms from their own list and restore them later, edit room settings, kick or ban members, unban users, transfer room ownership, promote/demote room-local admins, pin/reorder/unpin room messages, delete permitted room messages, and send/read group messages from both the Web UI and the game-side `/kchat group` commands. Public rooms appear in the room list; private rooms are invite-only. Room passwords are stored as PBKDF2 hashes, not plain text.

Each room also has a **member join/leave notices** option. When enabled, actual membership changes are stored as `member_join` / `member_leave` events and shown in Web/group history and to online game members. Accepting an invite or joining records an entry; leaving, kick, or ban records an exit. Closing the group-chat window, switching rooms, or hiding a room does **not** count as leaving and never creates an exit event. Membership events cannot be used as Reply targets.

Group chats use a dedicated SQLite store (`group-chat.sqlite-file`, default `group-messages.db`). `group-chat.retention-days: 0` means no time limit; positive values are shown next to the group-chat title and old group messages are physically removed after that many days. `group-chat.max-messages-per-room: 0` disables count-based cleanup. Existing SQLite databases are upgraded in place when optional 5.1.0 columns are missing.

## Direct message threads

`direct-message.enabled` enables optional 1:1 conversation threads. Targets include linked or previously known players and remote-server senders whose relayed message contains a player UUID. The latest relayed display name and real Minecraft name are added to the web DM recipient search, so a conversation can be started from the normal search without a separate DM button. Guest and Discord senders without a player UUID are excluded. A->B and B->A use the same thread, and messages are stored by UUID while the UI displays `display name (real account name)` when both are available.

DMs use an independent private-message store. `direct-message.storage: auto` follows `chat.history-storage` when public chat uses `jsonl`; otherwise it uses SQLite. You can also set `direct-message.storage` to `sqlite` or `jsonl` explicitly, using `direct-message.sqlite-file` or `direct-message.jsonl-file`. `direct-message.retention-days: 0` means no time limit; otherwise the DM window title shows the configured retention period and old DM rows are physically removed after that many days. `direct-message.max-messages-per-thread: 0` disables count-based cleanup. `direct-message.confirm-delete` controls whether the web UI asks before deleting one of your own DM messages for both participants. Received DM messages cannot be hidden or deleted by the recipient. Because private messages are stored on the server, the feature is disabled by default and should be enabled only after setting a server policy.


Game `/w`, `/msg`, `/tell`, and compatible aliases can be mirrored into the same web DM thread with `direct-message.capture-game-whispers`. Clicking a local game sender suggests `/w <realName> `; linked web senders suggest `/kchat dm <realName> `; remote-server game senders suggest `/kchat dm <realName>@<server-id> ` so same-name or same-UUID players on different servers remain distinct. Typing `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, or `/t` with the same `name@server-id` target routes that message through the cross-server KWC DM relay. An unqualified `/kchat dm <name>` always resolves on the current server; a remote target must include `@server-id` or be explicitly selected from the web UI.

## Custom emoji and game-side emoji plugins

KOKOTO WebChat stores custom emoji files under `<KWC data dir>/emojis`. Subfolders are treated as emoji packs. In 5.1.0, pack directory names and emoji filename stems use the same token-safe canonical naming rule: whitespace/unsupported characters are removed, existing invalid names are migrated at startup, and collisions receive numeric suffixes. The resulting on-disk path directly matches `:pack/name:`.

The custom-emoji picker always puts a browser-local **Recent** pseudo-folder first and keeps the 24 most recently inserted custom emojis. When `emoji.favorites.enabled: true`, **Favorites** follows it; hover a custom-emoji tile and click the small `☆`/`★` control to add or remove that emoji without inserting it. `emoji.favorites.storage: account` is the default and stores IDs in the signed-in account `user-preferences` independently of chat-history DB/JSONL selection; `browser` keeps IDs only in that browser. `emoji.favorites.max-per-account` defaults to 100; `0` means unlimited and positive values bound the retained list in either storage mode. Recent and Favorites are shared by public, DM, group, and search views. The magnifier immediately to the left of Recent opens a floating emoji-search field directly over the active public/DM/group message composer; it searches ID, name, display label, pack, and every public alias. Clicking outside the floating search field or pressing `Esc` closes search and returns to the selected folder view. Messages always insert the original `:pack/name:` token; no `:recent/...:` alias is used.

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

Detailed setup, relay, reply-command, permission, reload, and troubleshooting notes: [`docs/en/IMAGEEMOJIS_BERO_1_9_0.md`](docs/en/IMAGEEMOJIS_BERO_1_9_0.md).

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

- `docs/en/USER_MANUAL.md` - complete user and operator manual for all features
- `docs/en/CONFIGURATION.md` - configuration reference
- `docs/en/SERVER_RELAY.md` - Relay Protocol v2 public chat, cross-server DM/read receipts, trust and forwarding rules
- `docs/en/UPGRADE.md` - consolidated upgrade and migration guide through 5.3.0
- `wiki/` - GitHub Wiki source pages using safe page names without `and` / `&`
- `docs/en/CADDY_HTTPS.md` - HTTPS reverse proxy setup
- `docs/en/I18N.md` - language files and fallback behavior
- `docs/en/INSTALL_TROUBLESHOOTING.md` - install, upgrade, troubleshooting
- `docs/en/UPLOAD_SECURITY.md` - upload security notes
- `docs/en/RELEASE_CHECKLIST.md` - release checklist
- `docs/en/STANDALONE_REVIEW.md` - BlueMap dependency and standalone mode review
- `docs/en/OPERATIONS_SECURITY.md` - public deployment, trusted proxy logs, and security checklist

## Security note

HTTP-only mode is supported for private/testing use. Passwords are stored hashed on the server, but HTTP login traffic is not encrypted. Use HTTPS for public servers.

Font note: Installed fonts must be typed by their CSS font-family name. Chat settings include a Test button that estimates whether the typed name is available in the current browser without requesting local-font permissions.

### Private chat metadata super admins

Set `private-chat-super-admins` in `config.yml` to exact UUIDs or Minecraft names for users who may see DM/group-chat metadata for moderation/accounting. This view shows titles/participants, message counts, approximate stored byte sizes, retention status, cleanup preview counts, locks and cleanup exclusions. `direct-message.admin-audit.enabled: true` lets those explicitly listed accounts open DM bodies in a read-only audit view; `group-chat.admin-audit.enabled: true` independently enables the group-body audit view. Ordinary ADMIN/MODERATOR roles do not qualify automatically, and every audit page read is logged.

Administrative actions are also appended to date-based text audit files under `<KWC data dir>/audit` by default. The audit log is intended for server operators and is not shown in the web UI.


Note: `frontend.standalone.app-name` / `frontend.standalone.app-short-name` can change the mobile Home Screen web app name, and `web-push.notification-title` can change the default push title. Server notifications can be set to all, join/leave only, or off in Chat settings. If `web-push.notification-title` is empty, `frontend.standalone.app-name` is used. Android/desktop browsers can enable push from either the BlueMap addon or the standalone page when HTTPS and Push API support are available. On iOS/iPadOS, use a page added to the Home Screen and opened as a web app rather than a normal browser tab.


Existing configs that still contain legacy generated display names such as `BlueMapWebChat` or `BM WebChat` are treated as placeholders so they no longer appear as push titles by default.


- Pl3xMap integration: `docs/en/PL3XMAP_INTEGRATION.md`
- LiveAtlas integration: `docs/en/LIVEATLAS_INTEGRATION.md`
- uNmINeD integration: `docs/en/UNMINED_INTEGRATION.md`

## Overviewer

- Overviewer integration: `docs/en/OVERVIEWER_INTEGRATION.md`

## Forge

Forge uses exact-target server JARs for Minecraft 1.18.2 through 26.2. The Forge tree is split into `src/common` plus `compat118`, `compatClassic`, `compatModern`, and `compat26`; see `kwc-platform-forge/README.md` and `docs/en/FORGE_INTEGRATION.md`. Direct BlueMapAPI integration is limited to Forge 26.1.2/26.2; older Forge targets use the loader-neutral filesystem/static-map adapters. Build helpers select JDK 17/21/25 per exact target; use `kwc-platform-forge/build-all.bat` (Windows) or `build-all.sh` instead of forcing every ForgeGradle generation through one system JVM.

## AI assistance disclosure

Generative AI was used as a development assistant for code review, implementation and patching, documentation drafting, and multilingual translation. Project requirements, architecture and design decisions, source integration, testing, compatibility verification, release validation, and final acceptance are directed and reviewed by the human maintainer. AI-assisted output is reviewed and validated before inclusion. See `AI_USAGE.md` for details.

> Relay 2.2 adds targeted event (`game`) routing: relayed event links open/join the event on its origin server instead of substituting a local event.
