# KOKOTO WebChat 5.3.0 Complete User and Operations Manual


## Visual map

| Area | Diagram |
| --- | --- |
| Architecture | [PNG](../assets/architecture-5.3.0.png) · [SVG](../assets/architecture-5.3.0.svg) |
| Relay Protocol v2 | [Animated GIF](../assets/relay-v2-flow.gif) · [PNG](../assets/relay-v2-flow.png) · [SVG](../assets/relay-v2-flow.svg) |
| DM/group Reply | [PNG](../assets/private-reply-flow.png) · [SVG](../assets/private-reply-flow.svg) |
| Configuration migration | [Animated GIF](../assets/config-language-migration.gif) · [PNG](../assets/config-language-migration.png) · [SVG](../assets/config-language-migration.svg) |
| Deployment modes | [PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg) |
| Upload security | [PNG](../assets/upload-security-pipeline.png) · [SVG](../assets/upload-security-pipeline.svg) |
| Web Push | [PNG](../assets/web-push-flow.png) · [SVG](../assets/web-push-flow.svg) |

See [REFERENCES.md](REFERENCES.md) for the primary standards and official third-party documentation cited by this manual.

> **5.3.0 operations:** Web Admin **Filter** manages shared public/group/optional-DM block/mask/replace rules and no-send testing; **Settings** exposes only the supported live-safe guest/CAPTCHA, session, profile, server-wide typing-indicator policy, administrator-alert, upload, and content-filter values. The five moderation policy switches remain `config.yml`-only and are not exposed by Web Admin. `/kchat filter` and `/kchat settings` provide game-side controls. Session lifetime changes recalculate existing affected sessions from their creation time without resurrecting already-expired sessions. `upload.filename-mode: original` preserves safe Unicode original names for new uploads with collision suffixes.


This manual describes all KOKOTO WebChat 5.3.0 features from both the user and server-operator perspectives. For an option-by-option reference, see `CONFIGURATION.md`. For relay protocol details, see `SERVER_RELAY.md`. For HTTPS deployment, also see `CADDY_HTTPS.md` and `NGINX_HTTPS.md`.


## 5.3.0 additions

- **Private chat:** DM and group rooms can search full retained history. DM “hide for me” is removed. When ordinary-user self-delete is enabled, only the sender can delete their own DM and deletion removes it for both participants. Group rooms add room-local `owner/admin/member` roles, pinned-message management, room-wide deletion, and a room-local member self-delete policy that works together with the global self-delete setting.
- **Chat Events:** multiple First come and Lottery events can run at once from Web or `/kchat game`. First come uses winner count as its only capacity and completes automatically when full; Lottery keeps separate participant and winner counts. Event announcements may stay local or use Relay.
- **Profiles and presence:** user profile cards expose Minecraft Head/custom avatar, a 280-character About field, role, personal block list, and Game/Web connection state. Users choose Online/Busy/Offline; Offline masks actual Game/Web state from other viewers server-side.
- **Moderation:** administrators can apply account chat/upload restrictions, remove custom profile images, change local roles, and delegate selected capabilities per moderator.
- **CAPTCHA and mentions:** guest CAPTCHA supports off/math/text/mixed plus math complexity, and public/DM/group Web composers share IME-safe `@` autocomplete.
- **Relay 2.2:** targeted sender-owned DM delete, Chat Event routing, and remote public-profile lookup are capability-gated within protocol major 2. Each peer can independently allow send/receive for `public-chat`, `event`, `dm`, and `profile`.
- **Window/UI and adapters:** public, DM, and group windows share drag/resize/maximize behavior; adapter/standalone wrappers are generated from the same frontend fragments and CSS to keep layout behavior synchronized.
- **Image privacy:** uploaded/profile images strip supported EXIF/IPTC/comment/XMP metadata before storage.

## 5.2.0 additions

- **Message reactions:** signed-in users can add/remove Unicode or KWC custom-emoji reactions on public, DM, and normal group-chat messages. Group membership join/leave events are excluded. When no reaction exists, the 32 × 16 px hover `+` sits between messages with 1 px visual clearance above and below, so it does not cover either text line and still avoids reserving the full 22 px reaction row. Reaction OFF restores the original 8 px message spacing; real reactions use the normal in-flow row/chip spacing. Search uses the literal emoji, server-generated Unicode names, administrator-managed aliases, or custom-emoji ID/name/pack. The alias list is editable under **Admin > Emojis > Reaction icons** as `emoji = search words` and persists to `reaction-search-aliases.txt`. The picker remains anchored across category/search rerenders and outside-click dismissal continues to work. Hovering a chip shows all sanitized reactor names in a bounded scroll list, using the same display-name/original-name toggle as chat senders. The same Admin screen uses the normal rounded themed setting rows for the master reaction ON/OFF switch and separate KWC custom-emoji allowance. Turning the feature off preserves existing data and leaves existing chips visible/read-only while rejecting all local and relayed reaction mutations, including removals. Relay 2.1 synchronizes reactions and reactor display labels for relayed public messages and cross-server DMs without rebuilding the message/media DOM; actor UUIDs remain internal.
- **Saved conversations:** when `chat.conversation-archive.enabled: true` (default), public, DM, and group ranges can be stored as private snapshots. Disabling the server option removes archive controls from the DOM, leaves archive API routes unregistered, and does not open/create the archive DB. Normal retention does not remove the snapshot, but administrator-forced source deletion/room deletion and private-room lock policy remain authoritative. Attachments are not copied; available images can appear in the printable view, other files remain links, and missing originals are marked unavailable. PDF export reuses the user's current KWC appearance. Server-side archive capacity is bounded by `max-archives-per-user`, `max-messages-per-archive`, and `max-messages-per-user` under `chat.conversation-archive`.
- **Typing indicator:** public/DM/group typing is event-driven. The server administrator controls Open chat, DM, and Group chat separately in Web Admin **Settings** or `chat.typing-indicator.*`; defaults are OFF, ON, and ON. `chat.typing-indicator.user-display-control` defaults OFF; when an administrator enables it, signed-in users get one account-stored **Typing indicators** choice that affects only incoming display on their own screen, never transmission of their own typing activity. One input event opens a five-second indicator window; there is no polling, message-database write, or continuously running typing worker. The rounded high-opacity indicator floats one line above the active composer, uses the normal display-name/original-name toggle, strips formatting tags from names, and uses about 80% of the user chat-font size without going below the configured base UI font. Group displays collapse to a count when multiple long names do not fit.
- **Private-room header:** long room titles ellipsize while member count, Settings, and Leave keep their width. Invite, saved-conversation, hide, and room-management actions are consolidated under Settings according to permission.
- **Auto-follow:** public, DM, and group chat use a 32 px bottom threshold. Layout movement caused by emoji/icon/attachment panels preserves the viewport and does not by itself trigger an immediate forced scroll to the bottom.
- **Relay 2.1:** product version and relay protocol revision are independent. Protocol major 2 remains the wire-compatibility boundary; 2.1 advertises `public`, `dm`, `read`, `reaction`, `reaction-authority`, and `typing` capabilities.

## 1. Overview

KOKOTO WebChat connects Minecraft server chat to a browser-based chat interface. Version 5.3.0 provides Bukkit/Paper/Spigot plus exact-target Fabric 1.18.2–26.2, NeoForge 1.20.2–26.2, and Forge 1.18.2–26.2 builds.

Supported deployment and feature areas:

- Chat panel embedded in BlueMap
- Standalone chat page without BlueMap
- Embedded and standalone modes at the same time
- Bidirectional game and public web chat
- Persistent direct-message threads and group rooms
- DiscordSRV integration
- Public-chat relay between multiple Minecraft servers

The default HTTP port is `8899`; the internal API prefix is `/api`. With the default reverse proxy prefix `/chat`, the public API is `/chat/api` and the public standalone path is `/chat`.

## 2. Requirements and Recommended Environment

Required:

- One supported server platform: Bukkit/Paper/Spigot **1.18–26.2**, Fabric exact targets **1.18.2–26.2**, NeoForge exact targets **1.20.2–26.2**, or a documented Forge exact target **1.18.2–26.2**
- A Java runtime supported by that Minecraft/server target. The Bukkit artifact is compiled for Java 17. Fabric/NeoForge/Forge exact-target helpers select JDK 17, 21, or 25 according to the Minecraft target; 26.x targets use Java 25.
- Permission to install the platform JAR in `plugins/` (Bukkit family) or `mods/` (Fabric/NeoForge/Forge)

KOKOTO WebChat 5.3.0 declares `api-version: '1.18'` and compiles against `spigot-api:1.18.2-R0.1-SNAPSHOT`. Minecraft 1.17 and older are not claimed by this release.

Optional integrations:

- BlueMap for an embedded map chat panel
- DiscordSRV for Discord integration
- ImageEmojis-Bero 1.9.x for in-game rendering of KWC emoji tokens
- Caddy or Nginx for a public HTTPS deployment

For public servers, do not expose port `8899` directly to the Internet. Bind KOKOTO WebChat to `127.0.0.1:8899` and publish it through an HTTPS reverse proxy.

## 3. Installation and First Enable

1. Put the matching JAR in `plugins/` for Bukkit/Paper/Spigot, or in `mods/` for Fabric/NeoForge/Forge.
2. Start the server once.
3. Confirm `<KWC data dir>/config.yml` was created. `<KWC data dir>` is `plugins/KOKOTO-WebChat` on Bukkit-family servers and `config/KOKOTO-WebChat` on Fabric/NeoForge/Forge.
4. A newly generated configuration uses `enabled: false`.
5. Review URLs, storage, retention, authentication, and upload limits.
6. Enable the features you need and set `enabled: true`.
7. Restart the server or run `/kchat reload`.

Safe initial state:

```yaml
config-version: "5.3.0"
enabled: false
```

While disabled, the web service, chat forwarding, and cleanup tasks do not start. Administrators can still use `/kchat reload`.

## 4. Configuration Upgrade and Migration Fragment

KOKOTO WebChat preserves existing configured values by rebuilding an active-migration config from the current bundled `config.yml` and overlaying those values. Old comments/order/whitespace/indentation are discarded; current bundled comments and layout are authoritative.

The complete current reference is always written as:

```text
<KWC data dir>/config-reference-5.3.0.yml
```

It is an administrator-readable copy of the current default rendered in the same built-in language selected by `ui.language` (`en-US`, `ko-KR`, `ja-JP`, or `zh-CN`). Unsupported/custom UI languages use the English configuration presentation. The reference is never migration input. `/kchat reload` validates YAML before any live service is stopped; invalid YAML leaves the previous running configuration active.

When `config-version` is missing or differs from the running plugin version, KWC performs one migration pass on the real `config.yml`:

- The current bundled `config.yml` is copied as a fresh template.
- Existing configured values are overlaid onto that template; removed settings are not copied back.
- Old comments, ordering, whitespace, indentation, and duplicate textual copies are not carried forward.
- If the old version marker is not `*_auto_migration`, the original `config.yml` is backed up before a real version upgrade.
- Existing defaults that changed in the new version are **not** silently replaced; they stay review items.
- The real file is marked `config-version: "5.3.0_auto_migration"`.

KWC then writes:

```text
<KWC data dir>/config-migration-5.3.0.yml
```

This is a **review report**, not a copy/paste file for missing settings. It records the automatic insertion count, changed defaults that still need an operator decision, the exact final confirmation marker, and a semantic current-vs-reference setting diff. Difference blocks compare parsed YAML path/value pairs; comments, blank lines, indentation, quoting style, line positions, and key order are ignored. Each Difference block prints only that setting's YAML value block without duplicating its explanatory comments, while list/map values remain multi-line. Because missing settings and their bundled comments are already inserted into the real config, they no longer appear as a large reference-only block at the top of the diff.

Decision rules:

| Physical `config.yml` state | Behavior |
|---|---|
| `config-version` missing or older/different | Perform the migration, write `5.3.0_auto_migration`, and generate/update the migration report |
| `config-version: "5.3.0_auto_migration"` | Automatic migration enabled; rebuild from the latest same-version bundled `config.yml`, overlay current values, and refresh the migration report/diff |
| `config-version: "5.3.0"` | Automatic migration disabled for the current version; skip same-version migration/backfill and remove stale same-version migration guidance |

The marker controls migration behavior rather than review status:

```yaml
# Keep same-version automatic migration enabled, even after you have reviewed the config
config-version: "5.3.0_auto_migration"

# Disable same-version automatic migration
config-version: "5.3.0"
```

A later real plugin-version upgrade enters the new version's `_auto_migration` state again.

## 5. Choose a Deployment Mode

### 5.1 BlueMap Addon

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true

frontend:
  standalone:
    enabled: false
```

On Bukkit, KWC installs web assets under the BlueMap web directory and updates `plugins/BlueMap/webapp.conf`. On Fabric/NeoForge, and on Forge 26.1.2/26.2 with BlueMap 5.21+, KWC uses BlueMapAPI 2.8.0 to get the configured web root and register its scripts/styles; no `webapp.conf` patch is used. `/kchat reload` requests `bluemap reload light` automatically when BlueMap integration is active, and BlueMap's next API `onEnable` callback re-registers KWC with the new settings.

### 5.2 Pl3xMap embedded mode

On Bukkit/Paper-family or Fabric servers with Pl3xMap installed:

```yaml
adapters:
  pl3xmap:
    enabled: true
    api-base-url: ""
```

KWC reads `settings.web-directory.path` from Pl3xMap's current `config.yml` (with `settings.yml` accepted only as a legacy/fork fallback), installs only its own `kokoto-web-chat` assets, and maintains a marked KWC block in Pl3xMap `index.html`. `/kchat reload` re-checks the web files if Pl3xMap regenerates them. Current Pl3xMap 26.2 releases target Bukkit/Paper-family and Fabric/Quilt, not NeoForge. For direct HTTP, the normal empty `api-base-url` uses KWC `:8899/api`; if NAT changes the public KWC port, specify the actual public API URL.


### 5.3 LiveAtlas embedded mode

LiveAtlas is a static frontend that can display Dynmap, squaremap, Pl3xMap, Overviewer, or multiple servers. On Bukkit, Fabric, NeoForge, or Forge:

```yaml
adapters:
  liveatlas:
    enabled: true
    api-base-url: ""
    web-root: ""
```

With an empty `web-root`, KWC accepts only detected `index.html` files that contain LiveAtlas markers such as `window.liveAtlasConfig`. If Caddy/nginx serves LiveAtlas from a separate directory, set `web-root` to the server-visible shared/mounted directory. KWC owns only `kokoto-web-chat/` and its marked index block. After replacing LiveAtlas files, run `/kchat reload`. Do not point both the LiveAtlas adapter and a backend-specific adapter at the same physical web root.

### 5.4 uNmINeD static web export

uNmINeD exports a self-contained static web map rather than running inside the Minecraft server. Export the map first, then point KWC at that directory:

```yaml
adapters:
  unmined:
    enabled: true
    api-base-url: ""
    web-root: "/srv/www/unmined"
```

Current uNmINeD exports use `index.html`; older exports may use `unmined.index.html`. KWC checks for uNmINeD markers before patching either file, installs only its own `kokoto-web-chat/` directory and marked block, and leaves map tiles/library files untouched. Because a later uNmINeD export can replace the HTML or KWC-owned directory, run `/kchat reload` after re-exporting. If the map is on another host, the export directory must be shared/mounted so the Minecraft server can modify it.

### 5.5 Minecraft Overviewer static web map

Minecraft Overviewer renders a static Leaflet-based web map into its configured `outputdir`. Render the map first, then point KWC at that directory:

```yaml
adapters:
  overviewer:
    enabled: true
    api-base-url: ""
    web-root: "/srv/www/overviewer"
```

KWC requires Overviewer-specific generated markers/assets before patching `index.html`, writes only its own `kokoto-web-chat/` directory and marked block, and leaves Overviewer tiles/configuration/Leaflet assets untouched. A later Overviewer render or `--update-web-assets` may recreate the HTML, so run `/kchat reload` afterward. If Overviewer is rendered on another host, the output directory must be shared/mounted so the Minecraft server can modify it. Overviewer's `customwebassets` option can be used independently when you maintain a persistent custom template.

### 5.6 Standalone Only

```yaml
adapters:
  bluemap:
    auto-install: false
    auto-patch-webapp-conf: false

frontend:
  standalone:
    enabled: true
    path: "/"
```

Direct HTTP example:

```text
http://server.example.com:8899/
```

### 5.7 Embedded and Standalone Together

The embedded panel and standalone page share the same accounts, history, notifications, and server-side settings.

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true

frontend:
  standalone:
    enabled: true
```

## 6. HTTP, HTTPS, and Public URLs

### 6.1 Direct HTTP

Recommended only for testing or a private network.

```yaml
http:
  host: "0.0.0.0"
  port: 8899
  path-prefix: "/api"
  cors-origin: "*"

adapters:
  bluemap:
    api-base-url: ""
```

### 6.2 Same-Domain HTTPS Reverse Proxy

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: "/chat"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""
```

Public paths:

```text
https://map.example.com/          BlueMap
https://map.example.com/chat/api  KOKOTO WebChat API
https://map.example.com/chat Standalone chat
```

Normally leave `frontend.standalone.api-base-url`, `upload.public-base-url`, and `emoji.public-base-url` empty. They then follow the active public API base automatically.

#### Alternative: standalone at `/`, BlueMap at `/chat/`

This is the inverse of the default layout. Keep the internal standalone route at `/`, set `http.public-prefix: ""`, proxy `/chat/` to BlueMap with that prefix stripped, and send every other path to KWC.

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: ""

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""
```

Public paths become:

```text
https://map.example.com/       KWC standalone
https://map.example.com/api    KWC API
https://map.example.com/chat/  BlueMap
```

Do not change `frontend.standalone.path` to `/chat`; the external placement is controlled by the reverse proxy and `http.public-prefix`.


### 6.3 Trusted Proxy Handling

`X-Forwarded-For` is accepted only from direct peers listed in `http.trusted-proxies`. Keep the list empty for direct HTTP.

Temporary diagnostic option:

```yaml
http:
  log-client-ip-resolution: true
```

Disable it after confirming the proxy and resolved client IP.

## 7. Web UI Basics

Main interface areas include:

- Public message list
- Message composer
- Login and logout controls
- DM and group-chat menus
- Search
- Pinned messages
- Emoji picker
- File upload
- Notification settings
- Moderation and administration panels

Since 5.0.0, signed-in users can save visual preferences as multiple KWC account profiles, while guests keep browser-local presets. Window position/size, minimized state, the selected profile ID, and Web Push registration remain in localStorage/device storage. Signed-in notification types and keyword alerts are account-level rather than browser-specific. The browser-local notification inbox keeps recent notification-worthy events.

Default UI settings:

```yaml
ui:
  language: "en-US"
  language-fallback: "en-US"
  time-zone: "local"
  theme: "system"
  opacity: 0.92
  resizable: true
  remember-window-size: true
```

Supported languages:

- `en-US`
- `ko-KR`
- `ja-JP`
- `zh-CN`

Supported themes:

- `system`
- `dark`
- `light`
- `high-contrast`

## 8. Public Chat

### 8.1 Game to Web

```yaml
chat:
  broadcast-ingame-chat-to-web: true
```

Normal Minecraft chat is published to the public web chat. Sender names follow `player-display.mode`.

### 8.2 Web to Game

```yaml
chat:
  send-web-chat-to-game: true
  web-user-to-game-format: "[Web] {player}: {message}"
  web-guest-to-game-format: "[Web Guest] {guest}: {message}"
  web-admin-to-game-format: "[Web Admin] {player}: {message}"
```

Legacy `&` colors are translated only in configured format templates. User message text is not arbitrarily color-translated.

### 8.3 Web to Web

```yaml
chat:
  broadcast-web-chat-to-web: true
```

A public web message is pushed to other connected browser clients through SSE.

### 8.4 Message Length

```yaml
chat:
  max-message-length: 120
  max-url-message-length: 2048
```

Normal text and URL-oriented messages can use different limits. `0` means unlimited.


### 8.5 Message Tokens

KOKOTO WebChat can replace administrator-configured `:alias:` tokens before messages are stored or relayed. The built-in alias names are English-only defaults, but aliases can be replaced or extended in any language.

Default controls:

- `:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, `:br:` → one new line
- `:blankline:`, `:emptyline:`, `:paragraphbreak:` → one empty line
- `:tab:`, `:indent:` → configurable spaces (4 by default)

Unknown tokens are left unchanged, so ImageEmojis/custom emoji tokens continue to work. Backslash escape forms such as `:\n:` are not supported. Printable custom replacements can also be configured, for example `:separator:` → `────────────`.

For Minecraft output, ordinary CR/LF characters still follow the existing one-line flattening behavior. Only line breaks produced by configured `newline` / `blank-line` aliases are carried through that sanitizer and emitted as explicit Minecraft chat lines at final delivery, so `:enter:` works without changing the treatment of arbitrary pasted newlines. For server-relayed game output, the receiving KOKOTO WebChat server must also have the same 4.7.0 token-line delivery support; an older receiver flattens the normal relayed LF.

YAML list settings accept both inline (`aliases: [bullet, arrow]`) and block (`aliases:` followed by `- bullet`) forms. Use normal ASCII spaces for indentation; tabs and full-width spaces are invalid. A malformed edit is rejected by `/kchat reload` before live services are stopped, so the previous running configuration and UI language remain active.

```yaml
message-tokens:
  enabled: true
  max-replacements-per-message: 24
  newline:
    aliases: [enter, newline, nextline, linebreak, br]
  blank-line:
    aliases: [blankline, emptyline, paragraphbreak]
  tab:
    aliases: [tab, indent]
    spaces: 4
  custom: {}
```

The server-side processor is shared by normal chat, direct messages, group chat, supported Minecraft command/chat paths, and relayed messages, so the stored/relayed content contains the resolved line breaks or printable replacement text.


## 9. History and Search

SQLite is the recommended storage backend.

```yaml
chat:
  history-storage: "sqlite"
  history-sqlite-file: "history.db"
  history-retention-days: 5
  history-size: 0
  history-page-size: 80
```

Storage modes:

- `sqlite`: recommended for search and long-running servers
- `jsonl`: legacy single-file persistence
- `memory`: history is lost at server restart

`history-retention-days: 0` disables age cleanup. `history-size: 0` disables count cleanup.

Optional one-time JSONL import when the SQLite database is empty:

```yaml
chat:
  history-sqlite-migrate-jsonl: true
```

Search:

```yaml
search:
  enabled: true
  result-limit: 50
```

Search filters include message text, sender, date/time range, source, and whether system/event messages are included. Selecting a result loads surrounding history and jumps to the message. Very large result limits increase database, memory, and response-size costs.

## 10. Account Linking and Login

### 10.1 Link a Minecraft Account

Generate a link code in the web UI, then run in-game:

```text
/kchat auth <code>
```

Permission:

```text
kwc.auth
```

Related settings:

```yaml
auth:
  enabled: true
  link-code-length: 6
  link-code-expire-seconds: 180
  link-code-cooldown-seconds: 3
  link-code-max-per-minute: 10
```

### 10.2 Password Login

Set a web password in-game:

```text
/kchat password <newPassword>
/kchat status
```

```yaml
auth:
  password-login: true
  remember-session-days: 30
```

Passwords are stored as hashes, but HTTP login traffic is not encrypted. Use HTTPS for public deployments.

### 10.3 Roles

Available roles:

- `USER`
- `MODERATOR`
- `ADMIN`
- Unauthenticated guest access

Permission-based automatic administrator role:

```yaml
auth:
  auto-admin-from-permission: true
  admin-permission: "kwc.admin"
```

### 10.4 Local Administrator Accounts

Local web administrator accounts can exist without a linked Minecraft UUID.

```yaml
admin:
  allow-local-admin-accounts: true
```

Commands:

```text
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
```

### 10.5 Session Management

```text
/kchat sessions
/kchat revoke <username>
```

`revoke` removes active sessions and notifies connected browser clients that authentication expired.

## 11. Login and Connection Security

```yaml
security:
  login-fail-limit: 5
  login-fail-window-seconds: 300
  login-lock-seconds: 600
  max-sse-connections-per-ip: 10
  max-sse-connections-total: 500
```

These settings provide temporary login lockout and per-IP/total SSE connection limits. A value of `0` disables the corresponding limit.

Optional administrator login IP restriction:

```yaml
admin:
  allow-admin-login-from: []
```

An empty list allows all addresses. Public administrator accounts should use HTTPS and strong passwords.

## 12. Guest Chat and Captcha

```yaml
guest:
  enabled: true
  allow-custom-name: true
  name-prefix: "Guest-"
  cooldown-seconds: 6
  max-messages-per-minute: 50
  block-player-name-spoofing: true
```

`block-player-name-spoofing` prevents guests from using known player names. Add protected administrative names to `blocked-names`.

Captcha:

```yaml
captcha:
  mode: "math"
  math-complexity: "normal"
  expire-seconds: 120
  require-on-each-message: false
  pass-valid-minutes: 120
```

`captcha.math-complexity` applies whenever math is selected (including `mixed`): `easy` = single-digit addition/subtraction, `normal` = moderate addition/subtraction/multiplication, `hard` = larger operands plus exact-integer division. The text-code prompt is localized by the selected KWC language.

`captcha.mode` supports four built-in modes and does not require an external CAPTCHA provider:

- `off`: disable guest CAPTCHA.
- `math`: an arithmetic expression shown directly as `a op b = ?` without a `Solve:` prefix.
- `text`: a short case-insensitive code using an ambiguity-reduced letter/number alphabet.
- `mixed`: randomly issue either a math or text challenge.

`expire-seconds` keeps its existing semantics, including immediate/effectively expired challenges when configured as `0` or a negative value. `require-on-each-message` and `pass-valid-minutes` continue to control whether a successful challenge can be reused.

Account/profile moderation in 5.3.0 is intentionally separate from guest/IP mute. ADMIN can apply per-account chat/upload restrictions and remove custom profile images; moderator accounts receive only individually delegated moderation capabilities. Personal user blocks are available to every signed-in role and affect only the blocking user's view/notifications.

Guest/IP moderation commands:

```text
/kchat guest mute guest <name> [minutes] [reason]
/kchat guest mute ip <address> [minutes] [reason]
/kchat guest unmute guest <name>
/kchat guest unmute ip <address>
/kchat guest list
```

## 13. Player Names, Hover Text, and Click Actions

```yaml
player-display:
  mode: "name"
  strip-colors: true
```

Modes:

- `name`: Minecraft account name
- `display-name`: server display name
- `custom-name`: stored custom display name

Optional hover information for linked web senders in Minecraft:

```yaml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

Placeholders:

- `{display}`
- `{real}`
- `{uuid}`
- `{source}`

Name click actions:

- Local game player: `/w <realName> `
- Web sender: `/kchat dm <realName> `
- Remote-server game sender: `/kchat dm <realName>@<server-id> `

## 14. Public Message Replies

```yaml
reply:
  game-click:
    enabled: true
    local-game-chat: true
  game-command-format: "&8[&dReply&8] &f{player}&7: &f{message}"
  game-preview:
    enabled: true
    format: "&7{sender}: {preview}"
    max-length: 120
  game-prefix:
    enabled: true
    text: "↪ [Reply] "
```

Clicking a non-URL part of a KWC-rendered Minecraft message suggests:

```text
/kchat reply <messageId>
```

Send a reply with:

```text
/kchat reply <messageId> <message>
```

Permission:

```text
kwc.reply
```

`local-game-chat: true` replaces local game chat display with an equivalent clickable component. Disable it if another chat-format plugin must own final rendering. Web-to-game and relay replies continue to work when it is disabled.

URLs keep their open-link action. Only non-URL message parts suggest the reply command.

## 15. Direct Messages

In Minecraft, DM history/live notifications are interactive: click the DM participant name to place the existing `/kchat dm <player> ` command in the input box, and click the message body to place `/kchat reply dm-<internal-id> `. The internal ID is server-side only; KWC verifies that the player is actually a participant before sending. URL portions keep their normal open-URL action. When the stored DM is a Reply, live receive/send echo and history use the same configured `reply.game-preview` / `reply.game-prefix` presentation as public chat. A DM sent from the Web is also echoed to the linked sender's Minecraft chat when that player is online.

On the Web, selecting Reply on a DM stores a real relationship to the original message. KWC validates that the target is in the same thread, stores a canonical sender/preview snapshot, displays the reference, and can jump to the local original. The metadata survives restart. Cross-server replies use a stable relay message ID instead of another server’s local numeric database ID.


DM is disabled by default.

```yaml
direct-message:
  enabled: true
  storage: "auto"
  retention-days: 0
  max-messages-per-thread: 0
  max-message-length: 500
  allow-web-send: true
  allow-game-send: true
  capture-game-whispers: true
  notify-on-login: true
  notify-on-message: true
  web-unread-badge: true
  confirm-delete: true
```

Recipients must be known by UUID. In addition to local join and linked-account records, a relayed game or linked-web message with `playerUuid` registers the sender's display name and real Minecraft name in the new-conversation recipient search. This allows a remote-server sender seen in public chat to be found through the normal DM search and sent through the existing DM path. The latest identity is retained in `known-display-names.yml` and remains searchable after restart. Guest and Discord messages without a player UUID are not registered. With `storage: auto`, DM uses JSONL only when public chat storage is JSONL; otherwise it uses SQLite. You may explicitly select `sqlite` or `jsonl`.

In 5.3.0, DM has no message-level **hide for me** operation. A user may delete only a DM they sent; the delete removes it for both participants and clears surviving Reply snapshots that referenced it. `direct-message.confirm-delete` controls the confirmation prompt. Received DMs cannot be deleted by the recipient. Cross-server deletion is acknowledged by the target peer before the origin copy is removed.

Game commands:

```text
/kchat dm
/kchat dm list [pageSize]
/kchat dm unread [pageSize]
/kchat dm list next
/kchat dm list prev
/kchat dm <player> <message>
/kchat dm read <player> [pageSize]
/kchat dm next
/kchat dm prev
/kchat dm delete <messageId>
```

Permission:

```text
kwc.dm
```

### 15.1 Capture Minecraft Whispers

When `capture-game-whispers: true`, the following commands are copied into the same KWC DM thread:

```text
/w /msg /tell /whisper /m /pm /message /t
```

KOKOTO WebChat does not replace a normal same-server Minecraft whisper. It records a copy for both sender and recipient. For a remote target, use `name@server-id`; the same aliases are rewritten to `/kchat dm name@server-id <message>` and sent through the signed cross-server DM relay. An unqualified `/kchat dm <name>` resolves only to a player on the current server. `/r` and `/reply` are not intercepted because they contain no target and remain owned by the server's existing whisper plugin.

### 15.2 Delivery and read status

Normal successful delivery is not labeled. `Sending` appears only while a local send request is pending, and `Failed · Retry` appears only when delivery cannot be confirmed. These compact states are displayed beside the message timestamp. Read status is shown beside the timestamp for every DM message: `Unread` means the single recipient has not read the message yet, and `✓` means the recipient has read it. Group chat remains count-based. For cross-server DMs, the recipient server returns the read acknowledgement through the authenticated relay so the same status is reflected on the message origin. The latest acknowledgement is idempotent and is re-sent when the conversation is viewed, allowing a transient relay or HTTP failure to repair on a later view.

## 16. Group Chat

In Minecraft, group messages are interactive: click the group/sender area to place the existing `/kchat group <room> ` command in the input box, and click the message body to prepare a private reply target for that group message. KWC re-checks current group membership before sending, and URL portions keep their open-URL action. Reply-bearing group messages use the same configured `reply.game-preview` / `reply.game-prefix` presentation as public chat for live receive/send echo and history.

On the Web, group Reply is stored as metadata as well. KWC only accepts a target from the same room while the sender is a current member, derives the sender/preview from the stored original, preserves it across restart, and lets the browser jump to the local original when available.

Each room has a **member join/leave notices** option in Room settings. When enabled, actual membership changes are persisted as `member_join` / `member_leave` events and are visible in group history and as live game notices to online members. Joining or accepting an invite creates an entry event; leaving, being kicked, or being banned creates an exit event. Closing the group-chat window, switching rooms, or hiding a room does **not** leave the room and creates no exit event. These membership events are informational and cannot be used as Reply targets.


```yaml
group-chat:
  enabled: true
  allow-web-send: true
  allow-public-rooms: true
  allow-room-passwords: true
  retention-days: 30
  max-messages-per-room: 1000
  max-message-length: 500
  max-rooms-per-user: 20
  max-members-per-room: 50
  max-room-name-length: 32
  invite-expire-hours: 72
  sqlite-file: "group-messages.db"
```

Web features include public/private room creation, optional room passwords stored as PBKDF2 hashes, invites, accept/reject, leave, hide/restore, room settings, unread tracking, room-local owner/admin/member roles, room pinned messages, real room-wide message deletion, member kick/block/unblock, and ownership transfer.

Every group message shows its recipient read state. The number is the count of current room members who were already members when the message was sent and have not yet read it; the message sender is not a recipient. When the unread-recipient count reaches zero, the number changes to `✓`. Normal successful delivery itself is not labeled.

Game commands:

```text
/kchat group
/kchat group list
/kchat group rooms
/kchat group <room|id> <message>
/kchat group send <room|id> <message>
/kchat group read <room|id> [pageSize]
/kchat group next
/kchat group prev
```

Alias:

```text
/kchat gc ...
```

Permission:

```text
kwc.group
```

## 17. System and Event Announcements

```yaml
announcements:
  broadcast-to-web-chat: true
```

Enabled by default:

- Player join
- Player quit
- First join
- Death
- Advancement
- Server start
- Server stop

Disabled by default:

- World change
- Game-mode change
- Level change
- Bed enter
- Web login
- Web logout

Configured messages are fallback/custom text. When an i18n key exists, the web UI can display the event in the selected language.

## 18. Pinned Messages

```yaml
pinned:
  enabled: true
  max-pins: 20
  show-to-logged-out: true
  preserve-uploads: true
```

Pinned messages are stored separately from normal history and appear in a compact top bar. Referenced uploads are protected from cleanup when `preserve-uploads` is enabled. Moderator/admin pin and delete controls are shown only after the corresponding temporary admin-panel toggle is enabled. In both public chat and group chat, the **pinned by** identity follows the same Display name / Real name mode as ordinary user names. Older public pins that predate structured pinner identity remain readable and use their saved label as a fallback.

## 19. File and Clipboard Uploads

```yaml
upload:
  enabled: true
  allow-guest-upload: false
  allow-user-upload: true
  allow-moderator-upload: true
  allow-admin-upload: true
  cooldown-seconds: 5
  max-uploads-per-minute: 4
  max-file-size-mb: 20
  max-total-size-mb: 0
  max-files-per-message: 3
  directory: "uploads"
  retention-days: 5
  clipboard-upload-enabled: true
  clipboard-upload-send-mode: "insert"
```

Default extensions:

- Images: PNG, JPG, JPEG, GIF, WEBP
- Video: MP4, WEBM
- Audio: MP3, M4A, OGG, WAV, FLAC

`max-total-size-mb: 0` means unlimited. When a positive total limit is used, old unreferenced uploads are cleaned first; the new upload is rejected if space is still insufficient.

Clipboard modes:

- `insert`: insert the uploaded URL into the composer
- `send`: send immediately after upload

On desktop multi-window layouts, each detached DM or group conversation window is also a direct drag-and-drop target. Dropping files on a child window activates that exact thread/room and inserts the uploaded URLs into that conversation; the parent DM/group list window remains a valid drop target as well.

With `filename-mode: original`, clipboard uploads prefer the long filename reported by `clipboardData.files`. On Windows/Chromium, if an alternate clipboard entry reports a DOS 8.3 alias such as `202608~1.JPG`, KWC prefers the long name. If the browser exposes only the 8.3 alias, KWC uses a generated `clipboard-...` filename rather than storing the misleading alias as the original name.

See `UPLOAD_SECURITY.md` for security guidance.

## 20. Media and Link Previews

Uploaded media previews:

```yaml
upload:
  preview-images: true
  preview-videos: true
  preview-audio: true
```

YouTube:

```yaml
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  youtube-nocookie: true
  youtube-max-embeds-per-message: 1
```

YouTube Shorts use the normal YouTube preview path with a vertical layout. `ui.image-preview-max-per-message` and `ui.image-preview-max-height` limit the number and height of previews. Google Drive image previews can be enabled with `ui.google-drive-image-preview` and configured with `ui.google-drive-preview-mode`.

TikTok and X:

```yaml
preview:
  social-embeds:
    enabled: true
    click-to-load: true
    max-embeds-per-message: 2
    tiktok:
      enabled: false
    x:
      enabled: false
      theme: "auto"
      dnt: true
```

External embeds make browser requests to third-party services. Keep `click-to-load: true` on public servers unless automatic loading is explicitly acceptable.

Discord CDN cache:

```yaml
preview:
  external-media-cache-enabled: true
  cache-discord-cdn: true
  external-media-cache-retention-days: 5
```

This preserves previews for expiring Discord attachment URLs.

## 21. Custom Emoji

```yaml
emoji:
  enabled: true
  show-button: true
  directory: "emojis"
  max-file-size-kb: 512
  max-total-size-mb: 64
  render-size-px: 32
  picker-size-px: 44
  message-token-limit: 12
  token-format: "short"
```

File layout:

```text
<KWC data dir>/emojis/default/wave.png
<KWC data dir>/emojis/reaction/happy.gif
```

Tokens:

```text
:default/wave:
:reaction/happy:
:emoji:default/wave:
```

`token-format: short` inserts `:pack/name:`. `legacy` inserts `:emoji:pack/name:`. Both formats are accepted for parsing.

Administrators can create folders, select **multiple PNG/JPG/JPEG/GIF/WEBP files in one picker operation**, upload them immediately through the same validated per-file upload path used by normal chat uploads, rename items, and delete items from the web emoji manager. Multi-file uploads run sequentially and keep the existing per-file validation, storage accounting, unique-name allocation, audit logging, and PNG-sidecar generation. Renaming a file or pack can break rendering of historical messages that contain the old token.

### 21.1 In-Game Emoji Handling

Default:

```yaml
emoji:
  game-link:
    enabled: false
```

This preserves tokens for ImageEmojis-Bero or another game-side renderer.

KWC conversion mode:

```yaml
emoji:
  game-link:
    enabled: true
    mode: "link"
    label-format: ":{id}:"
    max-links-per-message: 4
```

Modes:

- `preserve`: keep the token
- `label`: output only a configured label
- `link`: output a label plus a short image URL

## 22. ImageEmojis-Bero 1.9.x Integration

Recommended ImageEmojis-Bero settings:

```yaml
emojisFolder: "/KOKOTO-WebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

Player permission:

```text
imageemojis.use
```

`replaceInCommands: true` is required for token conversion inside `/kchat reply`, `/kchat dm`, and `/kchat group`.

In a relay deployment, every server must have matching pack and file names. KOKOTO WebChat preserves canonical tokens in web history and relay payloads, then uses the receiving server's runtime token-to-glyph map for Minecraft output.

Recommended refresh order after emoji changes:

```text
/emojis reload
/emojis update
```

Reconnect if the resource pack must be refreshed. See `IMAGEEMOJIS_BERO_1_9_0.md` for full details.

## 23. Browser Notifications and Web Push

Since 5.0.0, logged-in users' keyword and notification-type choices are stored as account preferences and reused across browsers/devices. Visual settings can be kept in multiple per-account UI profiles for Windows/mobile/etc.; window geometry, minimized state and Web Push endpoints remain device-local. Web Admin controls the maximum profile count and whether JSON import/export is allowed. A private conversation counts as actively viewed only when at least one signed-in KWC browser for the account has a visible and focused page, the chat window is not minimized, the DM/group modal is open, and the current thread/room ID exactly matches the incoming target. This short-lived browser/tab state is reported to the server even when the viewing browser has no Web Push subscription. While any active client is viewing the exact conversation, live browser notifications and browser-local notification-inbox insertion are suppressed in every connected KWC tab for that account, and every Web Push subscription for that account is excluded for that DM/group target. A different room, closed modal, hidden/unfocused page, or minimized chat is treated as not viewing, and normal notification delivery resumes.


Server-side visual profiles are controlled by:

```yaml
ui:
  user-profiles:
    enabled: true
    max-profiles: 5
    allow-import-export: true
```

`max-profiles` accepts 0-20. Profile import/export is for visual settings only; session/identity/Push/device-window data is never included.


```yaml
notifications:
  enabled: true
  only-when-hidden: true
  notify-normal-chat: true
  notify-dm: true
  notify-group-chat: true
  notify-mentions: true
  notify-replies: true
  notify-reactions: true
  notify-system: true
  notify-keywords: true
```

Users can further restrict allowed notification types in Chat settings. **Reactions** is one shared checkbox for both live browser notifications and background/mobile Web Push; each browser/device simply uses the delivery methods it supports. The **@Mention** option triggers only for an explicit `@` followed by a registered real name or display name. At each `@`, KWC chooses the longest matching registered name; accounts that share that exact display name are all notified, so a shared display name can also be used as a team mention. For signed-in users these choices are account-level; guests keep browser-local choices. A server-side `false` cannot be overridden by the user. **Message preview is built in** for eligible browser notifications and Web Push and has no separate user or administrator checkbox. The browser-local notification inbox keeps recent events, and notification clicks can navigate to a public message, reply, DM thread, or group room when a target is available.

Web Push:

```yaml
web-push:
  vapid-public-key: ""
  vapid-private-key: ""
  subject: "mailto:admin@example.com"
  notification-title: ""
  ttl-seconds: 300
```

When VAPID keys are empty, the plugin creates persistent keys. Use a real administrator email or site URL for `subject`.

Platform notes:

- Android and desktop browsers: supported when HTTPS and Push API are available
- iOS/iPadOS: usually requires opening an installed Home Screen web app

## 24. PWA and Picture-in-Picture

Standalone app naming:

```yaml
frontend:
  standalone:
    app-name: "Web Chat"
    app-short-name: "Web Chat"
```

A previously installed Home Screen app may need to be reinstalled after a name change.

Picture-in-Picture:

```yaml
ui:
  picture-in-picture:
    enabled: false
```

Browser and operating-system support is required. External window controls are owned by the browser/OS.

## 25. DiscordSRV Integration

The 5.0.0 administrator Discord keyword alert does not delegate matching or formatting policy to DiscordSRV. KWC owns keyword matching, source selection, mentions, deduplication and alert content, and reuses only DiscordSRV's authenticated JDA connection and Channels mapping. Web Admin shows DiscordSRV logical channel names in the alert-channel selector; a raw channel ID is used only for an ID-only fallback. Discord-origin messages are never fed back into the administrator alert matcher.

Administrator alert policy example:

```yaml
admin-alerts:
  discord:
    enabled: false
    channel: ""
    sources:
      public-chat: true
      relay-chat: false
      dm: false
      group-chat: false
    mention: "none"
    case-sensitive: false
    keywords: ""
```

`channel: ""` reuses `discordsrv.channel`; Web Admin normally stores/selects the logical DiscordSRV channel name.




```yaml
discordsrv:
  enabled: true
  channel: "global"
  web-to-discord: true
  game-relay-mode: "discordsrv"
  discord-to-web: true
  ignore-bot-messages: true
  suppress-game-echo: true
  suppress-game-echo-seconds: 5
  send-web-user-chat-to-discord: true
  send-web-guest-chat-to-discord: false
  send-web-admin-chat-to-discord: true
  append-web-emoji-links: true
  append-game-emoji-links: true
  max-emoji-links-per-message: 4
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
  discord-to-web-sender-format: "Discord:{sender}"
  discord-to-web-message-format: "{message}"
```

If DiscordSRV already relays normal Minecraft chat, keep KWC `game-relay-mode: "discordsrv"` to avoid duplicate posts.

When multiple servers share one Discord channel:

- Only the server that observed the original local game chat modifies the DiscordSRV message
- Relay receivers do not repost the message to Discord
- Other server listeners do not append repeated `[Server]` or `[Web]` prefixes

Optional reply preview:

```yaml
discordsrv:
  reply-relay:
    enabled: false
    prefix-enabled: true
    preview-enabled: true
    preview-max-length: 120
```

## 26. Multi-Server Relay

KOKOTO WebChat 5.3.0 uses **Relay Protocol 2.2** over the Relay v2 trust/encryption model. Public chat, cross-server 1:1 DM/read/delete/reaction/typing, targeted event lookup/join, and remote public-profile lookup use capability-negotiated 2.x traffic; group-chat rooms remain local. Each peer can independently allow sending and receiving `public-chat`, `event`, `dm`, and `profile` traffic.

Relay v2 is configured as `groups -> peers`. Each group has one shared secret, and its peer entries contain only server ID, API URL and enabled state. For first setup, use `shared-secret: ""` on one server, start/reload KWC, then copy the generated value from that server's `config.yml` to the other servers in the same group. Existing non-empty secrets are never regenerated; a non-empty manual secret shorter than 32 characters remains invalid. Both servers must list each other in the same group, and the same peer ID cannot be registered in multiple local groups.

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  groups:
    - id: "main"
      shared-secret: ""
      forwarding:
        enabled: false
      peers:
        - id: "server2"
          url: "https://server2.example.com/chat/api"
          enabled: true
```

Direct relay uses 5.0.0-style request-by-request operation: `/relay/v2/message` independently authenticates and carries AES-256-GCM encrypted payloads using directional keys derived with HKDF-SHA256. `/relay/v2/handshake` is a stateless diagnostic identity/health probe only and never controls direct routing. Direct HTTP is allowed with a warning. Forwarding is same-group and peer-specific: an http:// peer is excluded only from forwarding through that peer, while other https:// peers remain eligible.

Relay v2 is hop-by-hop authenticated encryption, not E2EE. A forwarding server is a trusted participant that decrypts and re-encrypts the payload for the next hop. If a group secret is exposed, rotate that secret on every member of the group.

The first 5.0.0 → 5.1.0 migration disables relay instead of guessing groups from the old flat topology. Define v2 groups explicitly, then set `server-relay.enabled: true` and run `/kchat reload`.

See `docs/en/SERVER_RELAY.md` for the full protocol and operational reference.

## 27. Web Console Command Panel

Disabled by default.

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: "ADMIN"
  show-button: true
  run-from-chat-input: false
  show-when-input-starts-with-slash: true
  require-confirm: true
  max-length: 0
  broadcast-result-to-web-chat: false
```

When `broadcast-result-to-web-chat: true`, the execution notice is posted to public web chat and delivered to online Minecraft players. Existing console/audit logging is unchanged and is not duplicated by this player delivery path.

`allow-all: true` allows arbitrary console commands from a web account and is highly sensitive. Do not enable it without HTTPS, administrator IP restrictions, strong credentials, and a restrictive minimum role. Prefer configured presets.

```yaml
commands:
  presets:
    - id: "day"
      label: "Set day"
      description: "Set the current world time to day."
      command: "time set day"
      confirm: true
```

## 28. Administrator and Moderator Features

Depending on role and configuration, the web administration UI provides:

- Message deletion and public pin/delete action controls
- Pin management
- Guest and IP mutes
- Per-user chat/upload restrictions and custom-profile-image removal
- Per-moderator delegated capability checkboxes
- Session review and revoke
- Custom emoji folder/file management
- Upload and storage usage information
- Private-chat metadata administration
- Server console command panel

```yaml
moderation:
  enabled: true
  allow-web-admin-panel: true
  allow-moderator-message-delete: true
  allow-moderator-guest-mute: true
  default-mute-minutes: 60
```

These five `moderation.*` policy keys are **config.yml-only**. They are intentionally not exposed as editable settings in Web Admin; change them in `config.yml` and reload KWC. They govern whether the web moderation surface is available and what moderators may do.

ADMIN can additionally apply/release per-account chat and upload restrictions and delete custom profile images. Moderator permissions for online-list access, message delete, guest/IP mute, pin management, USER restrictions, and USER profile-image deletion are stored per moderator; the global moderator policy switches remain ceilings. These delegated capabilities never grant administrator role/settings/session powers. Every signed-in role also has its own personal block list, which is not an administrative sanction.

### 28.1 Private-Chat Metadata Super Administrators

```yaml
private-chat-super-admins:
  - "ExactMinecraftName"
  - "00000000-0000-0000-0000-000000000000"
```

The metadata view can show DM/group titles and participants, message counts, approximate storage usage, retention state, cleanup previews, locks/exclusions, and other metadata-management actions.

`direct-message.admin-audit.enabled` is a default-off, read-only DM body-audit switch. It grants content access only to exact accounts also listed in `private-chat-super-admins`. The audit view cannot send, reply, delete messages, or change read state, and each page read is logged as `admin.dm-audit-read`. `group-chat.admin-audit.enabled` remains an independent read-only group-body audit switch.

### 28.2 Audit Log

```yaml
audit:
  enabled: true
  directory: "audit"
```

Administrative actions are appended to dated files under `<KWC data dir>/audit` by default. Audit records are not displayed in the web UI.

## 29. Web Fonts and Visual Configuration

```yaml
web-fonts:
  enabled: false
  directory: "fonts"
  items: []
```

Example:

```yaml
web-fonts:
  enabled: true
  items:
    - family: "Pretendard"
      file: "Pretendard.woff2"
      weight: 400
      style: "normal"
```

Supported extensions: WOFF2, WOFF, TTF, OTF.

Visual defaults:

```yaml
ui:
  font-size: 13
  message-font-size: 13
  input-font-size: 13
  text-color: ""
  ui-text-color: ""
  input-background-color: ""
  text-shadow-mode: "auto"
```

Empty colors follow the selected theme.

## 30. Virtual Scrolling and Performance

```yaml
ui:
  virtual-scroll:
    enabled: true
    overscan-screens: 0.75
    min-rendered-messages: 30
  history-preload:
    screens: 0.7
    min-px: 200
```

Virtual scrolling reduces browser rendering cost for long histories. Only the current message range and a small visible guard are kept in the DOM; child content types do not receive separate retention windows.

Resume refresh:

```yaml
ui:
  resume-refresh:
    enabled: true
    min-interval-seconds: 5
    skip-unchanged: true
```

This refreshes missed messages after returning to a mobile or backgrounded page. Public-chat virtualization is content-type agnostic: images, video, audio, link previews, YouTube, and other iframes all use the same message-range and height-tracking rules.

## 31. Complete Command Reference

User commands:

```text
/kchat auth <code>
/kchat password <newPassword>
/kchat status
/kchat dm
/kchat dm list [pageSize]
/kchat dm unread [pageSize]
/kchat dm <player> <message>
/kchat dm read <player> [pageSize]
/kchat dm next
/kchat dm prev
/kchat dm delete <messageId>
/kchat reply <messageId> <message>
/kchat group list
/kchat group <room> <message>
/kchat group send <room> <message>
/kchat group read <room> [pageSize]
/kchat group next
/kchat group prev
```

Administrator commands:

```text
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

Root alias:

```text
/kchat
```

Group alias:

```text
/kchat gc
```

## 32. Permission Reference

```text
kwc.auth      Link a web account
kwc.webchat   Use authenticated web chat
kwc.dm        Send and read direct messages
kwc.reply     Reply to public messages from Minecraft
kwc.group     Use group chat
kwc.admin     Administer the plugin
kwc.update.notify  Receive update notices (OP by default)
```

User permissions are allowed by default. `kwc.admin` and `kwc.update.notify` default to OP.

## 33. Data Files and Backup

Common files:

```text
<KWC data dir>/config.yml
<KWC data dir>/history.db
<KWC data dir>/direct-messages.db
<KWC data dir>/group-messages.db
<KWC data dir>/web-push-subscriptions.jsonl
<KWC data dir>/emojis/
<KWC data dir>/uploads/
<KWC data dir>/audit/
```

Names can differ when customized.

Recommended backup scope:

- `config.yml`
- SQLite and JSONL stores
- `emojis/`
- uploads that must be retained
- Web Push subscriptions and VAPID key files

Copy SQLite databases after a clean server shutdown whenever possible.

## 34. Reload Versus Restart

Usually reloadable with `/kchat reload`:

- Most configuration changes
- HTTP service and relay reinitialization
- UI default changes

Requires a server restart:

- JAR replacement
- Java code changes
- Plugin load-order changes
- Some port-lock or web-resource conditions

`/kchat reload` automatically requests `bluemap reload light`; if BlueMap assets still appear stale or the command dispatch failed, run `/bluemap reload light` manually.

## 35. Troubleshooting Quick Reference

### Page Does Not Open

- Confirm `enabled: true`
- Check `http.host` and `http.port`
- Check for a port conflict
- Confirm reverse-proxy upstream points to `127.0.0.1:8899`
- Enable `frontend.standalone.enabled` when a standalone page is required

### No BlueMap Chat Button

- Enable `adapters.bluemap.auto-install`
- Enable `adapters.bluemap.auto-patch-webapp-conf`
- Check BlueMap paths
- Read installation/patch log messages
- `/kchat reload` normally triggers `bluemap reload light`; run `/bluemap reload light` manually only if needed
- Refresh browser cache

### Login Fails

- Check HTTPS domain and cookie path
- Confirm link code expiration
- Check login lockout logs
- Verify `auth.password-login`
- Check administrator IP restrictions

### Web Chat Does Not Reach Game

- Set `chat.send-web-chat-to-game: true`
- Confirm players are online
- Check chat-format plugin conflicts
- For relay messages, confirm `server-relay.delivery.game: true`

### Game Chat Does Not Reach Web

- Set `chat.broadcast-ingame-chat-to-web: true`
- Check player permissions and event cancellation
- Check whether another chat plugin exclusively consumes the event

### Reply Click Does Not Work

- Enable `reply.game-click.enabled`
- For local game messages, enable `local-game-chat`
- Check chat-format plugin conflicts
- URL parts opening a link instead of replying is expected

### Emoji Token Is Shown as Text

- Confirm the KWC emoji file exists
- Check ImageEmojis-Bero shared folder and permission
- Enable `replaceInCommands`
- Run `/emojis reload` and `/emojis update`
- Confirm matching emoji files on every relay server

### Relay Returns 403

- Confirm both servers list each other in the same relay group
- Confirm the peer ID exactly matches the remote `server-id`
- Direct relay authenticates each request independently; confirm reciprocal same-group peer configuration and the same shared secret. For forwarding, the incoming configured peer and selected next-hop peer must both use HTTPS
- Reload both sides after changing relay configuration

### Relay Returns 401

- Compare the group IDs and group shared secrets on both servers
- Confirm both sides use the exact same group secret. For first setup, generate it from an empty value on one server and copy it to the others; non-empty manual secrets must be at least 32 characters
- Confirm the proxy does not modify signed relay headers/body
- Check server clock synchronization and replay/nonce diagnostics

### Discord Prefixes Are Duplicated

- Confirm every server runs the same fixed build
- Check for another plugin reposting relay messages
- Keep `game-relay-mode: "discordsrv"` when DiscordSRV already forwards game chat

### Web Push Does Not Work

- Confirm HTTPS
- Check notification permission
- Check Service Worker and Push API support
- On iOS, use an installed Home Screen web app
- Check VAPID subject and key files

## 36. Related Documents

- `CONFIGURATION.md`: detailed setting reference
- `SERVER_RELAY.md`: relay topology, authentication, and errors
- `UPGRADE.md`: 4.7.0 to 5.0.0 core-split/reload-safety upgrade
- `UPGRADE.md`: 4.6.3 to 4.7.0 feature upgrade
- `UPGRADE.md`: 4.6.2 to 4.6.3 upgrade
- `UPGRADE.md`: 4.6.1 to 4.6.2 upgrade
- `UPGRADE.md`: 4.6.0 to 4.6.1 upgrade
- `UPGRADE.md`: 4.5.5 to 4.6.0 upgrade
- `CADDY_HTTPS.md`: Caddy HTTPS
- `NGINX_HTTPS.md`: Nginx HTTPS
- `IMAGEEMOJIS_BERO_1_9_0.md`: ImageEmojis-Bero integration
- `INSTALL_TROUBLESHOOTING.md`: installation troubleshooting
- `UPLOAD_SECURITY.md`: upload security
- `OPERATIONS_SECURITY.md`: public operations security
- `I18N.md`: language files and fallback
- `RELEASE_CHECKLIST.md`: release checklist

### Administrator group-chat body audit (4.6.3)

Set `group-chat.admin-audit.enabled: true` and list the exact Minecraft name or UUID in `private-chat-super-admins`. Both gates are required. A qualifying administrator may open group-chat bodies from the administrator room metadata list even when they are not a room member. The audit view is read-only: it does not join the room, mark messages read, change unread counts, send/upload/delete messages, or change membership. Every page read records `admin.group-audit-read`; message bodies are not copied into the audit log.



## SimpleNicks-Bero integration

On Bukkit/Paper-family servers, use `player-display.mode: "display-name"` to show the nickname rendered by [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero). The linked username/UUID remains KWC's real account identity. See `SIMPLENICKS_BERO.md`; use [upstream SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks) for normal plugin installation and operation.
