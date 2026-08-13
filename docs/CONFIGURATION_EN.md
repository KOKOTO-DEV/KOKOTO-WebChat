# BlueMapWebChat configuration reference

This document describes `plugins/BlueMapWebChat/config.yml`.

## Configuration version and migration fragment

`config-version` is an administrator review marker, not an automatic schema converter. If it matches the running plugin version, BlueMapWebChat assumes the configuration has already been reviewed and skips the comparison. If it is missing or different, the plugin compares the physical `config.yml` with the bundled defaults and writes `config-migration-<plugin-version>.yml` without changing the real config. The generated file contains copy-ready missing settings, changed bundled defaults, and the target `config-version` review marker as real YAML settings. It is still created when no other differences exist, so configuration version management remains explicit. Version information and previous/new default details are comments; preserved custom values and obsolete-setting notes are omitted. Merge the required values and merge `config-version` only after review. The file is regenerated on startup and `/bmchat reload` until the versions match.

## Master switch

New generated configs start with top-level `enabled: false`. In this state, BlueMapWebChat only creates/loads configuration and keeps only `/bmchat reload` available; it does not start web/chat services, listeners, Discord integration, private-message storage, addon installation, upload/emoji initialization, or cleanup tasks. Existing configs without this key are treated as enabled for upgrade compatibility. Review storage, retention, upload, preview, authentication, and exposure settings, then set `enabled: true`.

## Update check

```yaml
update-check:
  enabled: true
```

When enabled, BlueMapWebChat checks Modrinth for a newer stable release in the background. An OP or a player with `bluemapwebchat.update.notify` also triggers a rate-limited refresh on login, so a newly published release is not dependent only on the periodic result. The check interval, release channel, join delay, and Modrinth/CurseForge download links remain built-in defaults. Update lookup failures never stop plugin startup and are logged as warnings.

## Deployment modes

### BlueMap addon

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true
```

Installs assets under `addons/bluemap-web-chat` and patches BlueMap `webapp.conf`.

### Standalone only

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false

standalone-web:
  enabled: true
  path: "/chat"
```

Open `http://<server-host>:8899/chat`.

### HTTPS reverse proxy

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"

web-addon:
  api-base-url: "/bmwc/api"

standalone-web:
  enabled: true
  # Recommended: empty; follows web-addon.api-base-url.
  # You may also set the same public API route explicitly.
  api-base-url: ""

upload:
  # Recommended: keep empty so uploads follow the active API base.
  # Legacy explicit values also work: "/bmwc/api" or "/bmwc/api/uploads".
  public-base-url: ""

emoji:
  # Recommended: keep empty so emoji files follow the active API base.
  # Legacy explicit values also work: "/bmwc/api" or "/bmwc/api/emojis".
  public-base-url: ""
```

### Public URL option rules

- `http.path-prefix` is the plugin's internal HTTP API path. Normally leave the default `/api` unchanged.
- `web-addon.api-base-url` is the public API base used by the BlueMap embedded chat. In HTTPS reverse-proxy setups, this is usually `/bmwc/api`.
- `standalone-web.api-base-url` normally stays empty. When empty, standalone reuses `web-addon.api-base-url`; for example `/bmwc/chat` uses `/bmwc/api`. You may also explicitly set the same `/bmwc/api` value.
- `upload.public-base-url` normally stays empty. When empty, uploaded files use the active API base plus `/uploads`, for example `/bmwc/api/uploads`.
- `emoji.public-base-url` normally stays empty. When empty, custom emoji files use the active API base plus `/emojis`, for example `/bmwc/api/emojis`.
- Explicit legacy values are accepted. `/bmwc/api` appends `/uploads` or `/emojis` automatically, while `/bmwc/api/uploads` and `/bmwc/api/emojis` are used as-is.
- Relative values without a leading `/`, such as `bmwc/api`, `bmwc/api/uploads`, or `bmwc/api/emojis`, are resolved against `http.cors-origin` when it is a real origin. If `cors-origin` is `*`, they fall back to same-origin absolute paths such as `/bmwc/api...`.
- Full URLs such as `https://map.example.com/bmwc/api` are used as-is.

### Upload storage quota

`upload.max-total-size-mb` limits the total size of regular files stored directly in `upload.directory`. The default `0` keeps upload storage unlimited. When a new upload would exceed the limit, BlueMapWebChat deletes the oldest unreferenced uploads first; files still referenced by chat history, SQLite history, DM/group messages, or preserved pinned messages are kept. If cleanup cannot free enough space, the upload is rejected.

### Emoji storage display

`emoji.max-total-size-mb` limits total custom emoji storage. When the limit is exceeded, admin uploads show a localized warning instead of failing silently. `emoji.show-storage-usage` controls whether current emoji storage is shown in the admin emoji manager, and `emoji.show-storage-limit` controls whether the total limit is shown.

## Chat history storage

`chat.history-storage` controls the backend used for stored chat history. New configs use `sqlite`, which stores messages in `chat.history-sqlite-file` (`history.db` by default). SQLite is recommended for long-lived logs because before/after history pages, reply-jump lookup, deletion, and retention cleanup can use indexed queries.

Supported values:

- `sqlite`: persistent SQLite DB, recommended.
- `jsonl`: legacy single-file persistence using `chat.history-file`.
- `memory`: session-only in-memory history.

`chat.history-size` and `chat.history-retention-days` are shared by `memory`, `jsonl`, and `sqlite`. `0` means unlimited for each setting. New generated configs start with top-level `enabled: false`, so cleanup does not run until you review these values and set `enabled: true`. Use positive values such as `30` or `90` when your server policy requires automatic old-chat cleanup. Upload and external-media cache retention settings work the same way. `chat.history-file` is used only by JSONL; `chat.history-sqlite-file` is used only by SQLite. When `chat.history-sqlite-migrate-jsonl` is true, an empty SQLite DB imports `chat.history-file` once. Keep normal file backups of `history.db` before manual editing, large cleanup, or migration.

## Message search

`/history/search` and the in-chat search modal are available for message text and sender searches when stored history is enabled. The search options section can filter by date/time range, sender, source, and system/event inclusion. The search button is in the floating chat-panel area so the input row stays compact, and search results use a scrollable list with the configured chat theme/font settings. Search results can jump to the matching message using the existing history-around navigation. i18n-backed system/event messages are searched and displayed in the requested web UI language when possible. Search can be disabled with `search.enabled`, and the single `search.result-limit` setting controls both the web UI result count and the `/history/search` API limit. There is no separate internal maximum: setting it to 2000 returns up to 2000 results, while setting it to 10 returns up to 10. Very large values such as 10000 or 100000 are accepted, but they can slow searches, increase response size, and add significant CPU, memory, and database load. The default is 50, and 50-200 is recommended for normal use. Existing config files from older versions need these keys added manually or merged from the default config.


## Direct message threads

```yaml
direct-message:
  enabled: false
  allow-web-send: true
  allow-game-send: true
  capture-game-whispers: true

direct-message:
  admin-audit:
    enabled: false
```

`direct-message.enabled` enables stored 1:1 threads for linked or previously known players. A→B and B→A use the same UUID-pair thread. `storage`, retention, message-count limits, and notification options are documented in the default config.

`direct-message.admin-audit.enabled` is a separate, default-off content-access switch. When enabled, only accounts also listed in `private-chat-super-admins` can open DM bodies in the read-only audit view. Each page read is audit-logged; message bodies are not copied into the audit log. Ordinary ADMIN/MODERATOR roles do not qualify automatically.

`capture-game-whispers` mirrors non-cancelled `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, and `/t` commands into the sender and recipient BMChat DM thread. It does not resend or replace the Minecraft whisper. Bukkit does not expose a reliable final success result for every whisper plugin, so a valid command targeting a known player is used as the capture criterion.

## UI time zone

`ui.time-zone` controls the time zone used for chat timestamps. Use `local` for the browser/device time zone, `UTC`, or an IANA time zone such as `Asia/Seoul`. Invalid values fall back to local time in the web UI.

## Options where 0 means unlimited/no maximum

- `chat.history-size`
- `chat.history-retention-days`
- `chat.history-page-size`
- `chat.max-message-length`
- `chat.max-url-message-length`
- `upload.max-uploads-per-minute`
- `upload.max-file-size-mb`
- `upload.max-files-per-message`
- `ui.image-preview-max-per-message`
- `ui.image-preview-max-height`
- `ui.max-width`
- `ui.max-height`
- `preview.youtube-max-embeds-per-message`
- `preview.social-embeds.max-embeds-per-message`
- `preview.external-media-cache-max-size-mb`
- `pinned.max-pins`
- `pinned.show-to-logged-out`
- `commands.max-length`
- `direct-message.retention-days`
- `direct-message.max-messages-per-thread`
- `direct-message.max-message-length`

## Guest chat controls

```yaml
guest:
  cooldown-seconds: 6
  max-messages-per-minute: 50
```

Guest chat is rate-limited by both `cooldown-seconds` and `max-messages-per-minute`. The default per-minute limit is `50` messages. Existing server configs are not overwritten automatically, so update `plugins/BlueMapWebChat/config.yml` manually if you want the new default on an existing installation.

## Web-to-Minecraft sender hover

```yaml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

When web chat is relayed into Minecraft, `chat.game-name-hover.enabled` can add a hover tooltip to the displayed sender name. It is only applied when `player-display.mode` is `display-name` or `custom-name` and the displayed name differs from the real Minecraft account name. The tooltip is built with Spigot/Bungee chat components, so it works on Spigot/Paper-compatible servers and is not Paper-only. `text` supports Minecraft legacy color codes and placeholders `{display}`, `{real}`, `{uuid}`, and `{source}`.

## Minecraft chat replies and sender actions

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

When `reply.game-click.enabled` is true, clicking a non-URL message body rendered by BMChat suggests `/bmchat reply <messageId> `. URL segments retain their open-link action. `/bmchat reply <messageId> <message>` creates a public message with the same `replyTo` metadata used by web replies.

Game replies preserve the player-entered custom emoji token for web history and server relay. On the originating server, BMChat also reuses the game emoji plugin's processed command body so the Minecraft reply line renders the emoji. If no processed glyph is available and `emoji.game-link.mode` is `preserve`, BMChat emits a plain compatibility line for recognized tokens; that fallback line cannot carry BMChat's click/hover metadata.

`local-game-chat: true` replaces the normal local Minecraft chat line with an equivalent clickable component so game-origin messages can also be replied to. Disable it if another chat-format plugin must exclusively own the final renderer. This does not disable reply clicks on web-to-game or remote relay lines.

A local game sender name suggests `/w <realName> ` when clicked. Linked web-user and remote-server game sender names suggest `/bmchat dm <realName> `. Sender-name actions remain separate from message-body reply clicks and keep the optional real-name hover.

`game-preview` sends the referenced-message preview before a relayed reply. `game-prefix` changes the source label of the actual reply line. Formats support legacy `&` colors and the placeholders documented in `config.yml`.



`server-relay` connects the public chat of multiple BlueMapWebChat servers. Game, linked web-user, and guest messages can be delivered to the remote server's web chat and Minecraft chat while preserving message IDs, replies, sender identity, and the originating server.

## Server relay configuration

Server 1:

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "Server 1"
  shared-secret: "replace-with-one-long-random-secret-used-on-both-servers"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server3"
      url: "https://server3.example.com/bmwc/api"
      secret: ""
      enabled: true
```

Server 3:

```yaml
server-relay:
  enabled: true
  server-id: "server3"
  server-name: "Server 3"
  shared-secret: "replace-with-one-long-random-secret-used-on-both-servers"
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server1"
      url: "https://server1.example.com/bmwc/api"
      secret: ""
      enabled: true
```

Each peer must be reciprocal: the receiving server must list the sender's exact `server-id`. IDs are case-sensitive after normalization and must be unique. Do not use the same ID for two servers.

## HTTPS and reverse proxies

`url` is the other server's externally reachable BMChat API base. BlueMapWebChat appends `/relay/receive` automatically:

```text
Configured: https://server3.example.com/bmwc/api
Requested:  https://server3.example.com/bmwc/api/relay/receive
```

The public HTTPS route must proxy the whole BMChat API path to the internal BMChat HTTP listener, including POST requests to `/relay/receive`. Do not expose port 8899 publicly when HTTPS already fronts the service. The proxy must preserve these request headers:

```text
X-BMWC-Relay-Version
X-BMWC-Relay-From
X-BMWC-Relay-Timestamp
X-BMWC-Relay-Signature
```

A publicly trusted certificate works with Java normally. A private/self-signed certificate must be imported into the Java trust store or the HTTPS request will fail before reaching BMChat.

## Secrets

- `shared-secret` is the default key for every peer.
- `peers[].secret` overrides the shared key for that one connection.
- With two servers, use the same long random `shared-secret` on both servers and leave each peer `secret: ""`.
- With per-peer keys, the two reciprocal entries must use the same pair-specific key.
- If neither a peer secret nor a shared secret is available, the peer is ignored.

## Topology

For three or more servers, use either:

- Full mesh: every server lists every other server. This is simplest and most resilient.
- Hub: leaf servers list a hub and the hub lists every leaf. The hub forwards messages to the remaining peers.

Relay IDs, origin suppression, immediate-sender exclusion, and `max-hops` prevent loops in cyclic topologies. There is no persistent offline queue; a message is not replayed later when a peer was unreachable.

## Reload behavior and diagnostics

`/bmchat reload` closes the previous relay instance and creates a new one from the current config. Relay uses one HTTPS request per message, not a permanent connection, so there is no separate reconnect operation.

A healthy startup log looks like:

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

If `activePeers` is lower than the configured count, nearby warnings explain which peer was rejected and why. Typical causes are duplicate IDs, a peer ID equal to the local server ID, an empty/invalid URL, an unsupported URL scheme, or a missing secret.

## HTTP errors

- `403 unknown_peer`: the receiving server does not have the sender's exact `server-id` in its active peers. Check both directions and the `activePeers` log on the receiver.
- `401 bad_signature`: the effective secrets differ or a proxy altered the body/headers.
- `401 expired_request`: server clocks differ by more than `max-clock-skew-seconds`.
- `404 relay_disabled`: relay is disabled on the receiver, or the proxy routes to the wrong BMChat instance/path.
- `426 unsupported_protocol`: the two plugin builds use incompatible relay protocol versions.

After editing either side, run `/bmchat reload` on that side. When a receiver's peer list or secret changes, reload the receiver as well.

## Display behavior

- Web chat shows a colored badge derived from `originServerId`; the same server keeps the same color.
- Web-to-game output uses `{server}` and `{server_id}`. The local server omits its own automatic label; if a remote message uses an older format containing neither placeholder, `[server-name]` is prepended automatically.
- Discord direct relay formats support `{server}` and `{server_id}` and receive an automatic prefix when missing.
- `sources.discord` and `sources.system` are disabled by default to avoid DiscordSRV loops and noisy cross-server event duplication.

## Discord relay options

```yaml
discordsrv:
  game-to-discord: false
  append-web-emoji-links: true
  append-game-emoji-links: true
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-to-discord-format: "[{server}] {sender}: {message}"
  reply-relay:
    enabled: false
    prefix-enabled: true
    preview-enabled: true
    preview-max-length: 120
```

Discord formats support `{server}`, `{server_id}`, `{sender}`, `{name}`, `{role}`, `{source}`, `{message}`, and `{channel}`. While server relay is active, old custom formats without `{server}` or `{server_id}` receive an automatic `[server-name]` prefix. In a shared Discord channel, only the BMChat instance that observed the original local Minecraft chat may edit DiscordSRV's native game relay; peer servers leave it unchanged so server labels and emoji links are not stacked. Server-relayed messages are not re-sent to Discord by receiving peers, so there is no relay-only fallback when the origin server's Discord bridge is unavailable.

`append-web-emoji-links` and `append-game-emoji-links` add public BMChat emoji URLs for Discord previews. Keep `game-to-discord` disabled when DiscordSRV already relays normal Minecraft chat to prevent duplicates. `reply-relay` optionally adds a replied-message preview and is disabled by default.

## Pinned messages

`pinned.show-to-logged-out` controls whether pinned messages are shown before web login. Set it to `false` when pinned content should only be visible to logged-in users.

## Pin/delete action toggle

Per-message pin/delete buttons are hidden by default to avoid accidental clicks. ADMIN/MOD users can open the admin panel and use the pin/delete action toggle next to the web-history clear button. The toggle is not persisted and resets to off after refresh.

## UI

```yaml
ui:
  language: "en-US"        # en-US, ko-KR, ja-JP, zh-CN
  language-fallback: "en-US"
  theme: "system"          # system, dark, light, high-contrast
  opacity: 0.92
```

Per-browser user settings are stored in localStorage.

`ui.text-color` sets the default chat message text color. `ui.ui-text-color` sets the default UI text/glyph color used by role labels, source labels, timestamps, placeholders, upload/command buttons, pinned labels, and similar chrome. Leave them empty to follow the selected theme. Users can override both per browser in Chat settings.

```yaml
ui:
  text-color: ""          # theme default for message body
  ui-text-color: ""       # theme default for UI labels/glyphs
  # text-color: "#f4f4f4"
  # ui-text-color: "#b8d8ff"
```

`ui.input-background-color` can override the input field background globally. Leave it empty to follow the selected theme. Users can also override it per browser in Chat settings.

```yaml
ui:
  input-background-color: ""      # theme default
  # input-background-color: "#1e1e24"
```

## Player names

```yaml
player-display:
  mode: "name"             # name, display-name, custom-name
  strip-colors: true
```

When `strip-colors: false`, Minecraft legacy color codes are rendered only for actual chat sender names in the web UI. System/event messages and Discord output strip raw Minecraft color codes. Stored/remembered display names are normalized against the current `strip-colors` setting when they are reused.

### ImageEmojis-Bero 1.9.0

The supported compatibility target is [ImageEmojis-Bero 1.9.0](https://github.com/KOKOTO-DEV/ImageEmojis-Bero). For a shared web/game emoji library, set its `emojisFolder: "/BlueMapWebChat/emojis"`, keep `templateFormat: ":<emoji>:"`, `replaceInCommands: true`, and give users `imageemojis.use`. See `IMAGEEMOJIS_BERO_1_9_0_EN.md` for the complete setup, relay behavior, reload sequence, Discord interaction, and troubleshooting.

## Command panel

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: ADMIN
  run-from-chat-input: false
  max-length: 0
```

`allow-all: true` lets the web UI run arbitrary console commands, so use it only with HTTPS and strong authentication. When `run-from-chat-input: false`, commands run only from the command button/modal.

## Media preview height and scroll stability

`ui.image-preview-max-height` limits the displayed height of image, GIF, video, and iframe-style previews. The recommended range is `640-720`; the default is `720`.

```yaml
ui:
  image-preview-max-height: 720
```

Set it to `0` only when unlimited preview height is acceptable. Unlimited or very large media previews can cause visible scroll jumps while media finishes loading, especially with virtual scrolling and long media-heavy histories.

## Previews

```yaml
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  media-click-to-load: true
  youtube-nocookie: true
  youtube-remember-expanded: true
  youtube-autoplay-on-open: false
  youtube-max-embeds-per-message: 1

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
      hide-media: false
      hide-thread: true
```

YouTube Shorts are handled by the normal YouTube preview path. Shorts are displayed in a vertical player and use YouTube loop parameters.

TikTok and X/Twitter are optional because they load third-party content in the viewer's browser. Keep them disabled unless your server policy allows third-party embeds. For public servers, keep `social-embeds.click-to-load: true` so the third-party player loads only after a user opens the preview.

TikTok uses the official `player/v1` iframe with `description=0` and `music_info=0`. This avoids variable-height captions creating inner scrollbars in the chat panel. The original TikTok link remains available below the player for the full post details.

Set `youtube-click-to-load` or `media-click-to-load` to `false` to render those previews immediately. Autoplay is still controlled by browser policy.


## Browser notifications and Web Push

`notifications` controls the shared notification defaults and server-side allow limits for both browser notifications and mobile/background Web Push. `notifications.enabled` is the single default on/off switch for both delivery paths; the old `browser-notifications.*` and `web-push.notify-*` keys are only read as legacy migration/compatibility input. Leave `notify-*` values `true` to let each browser/user choose in Chat settings, or set one to `false` to block that notification type even if a user enables it. When `notify-system` is allowed, users can choose all server notifications, join/leave only, or off in Chat settings. `notify-keywords` controls user-defined keyword alerts; keyword lists are stored per browser/device and are synced only to that device's Web Push subscription for background matching.

`web-push` stores Web Push transport settings such as VAPID keys, subject, subscription file, TTL, and default push title. It can send background/mobile push notifications through Service Worker + Push API when HTTPS or localhost, browser permission, and push support are available. Android/desktop browsers can enable it from either the BlueMap addon or the standalone page when the current origin supports Service Worker + Push API. Ordinary iOS/iPadOS browser tabs do not support Web Push; on iOS/iPadOS only try it from a page added to the Home Screen and opened as a web app, and treat unsupported behavior as a platform limitation. If VAPID keys are left empty while `notifications.enabled: true`, the plugin creates persistent keys in `web-push-vapid.properties`. Set `web-push.subject` to real VAPID contact information such as `mailto:admin@example.com` or `https://map.example.com`; arbitrary text is not recommended and may be rejected or distrusted by push services. Browser or OS warnings such as “may be spam” are controlled by the device/browser and cannot be disabled by the plugin. A stable HTTPS domain, meaningful notification title/body, conservative notification filters, and avoiding repeated test notifications can reduce the chance of that warning.

## PIP

```yaml
ui:
  picture-in-picture:
    enabled: false
```

This single flag controls both the PIP button and PIP execution. Browser URL/close UI, OS-level window transparency, and the outer PIP window movement are controlled by the browser/OS, not by the chat settings title.

## Login failure limit

`security.login-fail-limit`, `security.login-fail-window-seconds`, and `security.login-lock-seconds` protect password login from repeated failures. Set `login-fail-limit: 0` to disable the limit. This change only affects web password login.
## Link code rate limit

`auth.link-code-cooldown-seconds` and `auth.link-code-max-per-minute` limit how often the web UI may issue `/bmchat auth <code>` link codes per remote IP. Set either value to `0` to disable that part of the limit.

## HTTP proxy / client IP

`http.trusted-proxies` controls whether `X-Forwarded-For` is trusted. Keep it empty for direct HTTP. When using Caddy/Nginx on the same host, add `127.0.0.1` and `::1` as block-style YAML list entries. Set `http.log-client-ip-resolution: true` only temporarily to confirm the socket IP, forwarded header, and resolved client IP in the server console and `logs/latest.log`. See `docs/OPERATIONS_SECURITY_EN.md` for the full check procedure.

## SSE connection limits

`security.max-sse-connections-per-ip` and `security.max-sse-connections-total` limit long-lived `/stream` connections. `0` disables each limit.


### System message translation

Built-in announcements and web command result messages include i18n keys. Keep `announcements.*.message` as the fallback/custom text; viewers will see the translated language-file text when the key exists.

Collapsed pinned-message bar text follows the configured chat font and message font size.

### Text shadow / readability

- `ui.text-shadow-mode`: `none`, `auto`, `dark`, `light`, or `custom`. Use this to keep text readable when custom text/background colors have low contrast.
- `ui.text-shadow-custom`: CSS `text-shadow` value used when the mode is `custom`. In the chat settings UI, this is edited with a color picker and sliders for X offset, Y offset, blur, and opacity; the stored value remains standard CSS, for example `0 1px 2px rgba(0, 0, 0, 0.85)`.

> The theme can also be changed per browser from Chat settings. Changing the theme resets visual settings such as text colors, background colors, and shadows to that theme's defaults.


Admin custom emoji manager note: renaming an emoji file or folder changes the `:emoji:pack/name:` token. Existing chat messages that reference the old token may no longer render unless the old file/folder name is kept.


## Custom emoji and game-side emoji plugins

BlueMapWebChat stores custom emoji files under `plugins/BlueMapWebChat/emojis`. Subfolders are treated as emoji packs.

By default, `emoji.game-link.enabled` is `false`, so web-to-game messages preserve custom emoji tokens such as `:pack/name:` and `:emoji:pack/name:` unchanged. Use this default when ImageEmojis or another game-side emoji plugin renders tokens in Minecraft chat.

When `emoji.game-link.enabled` is `true`, `emoji.game-link.mode` supports `preserve`, `link`, and `label`.

- `preserve`: force token-preserving behavior even when game-link is enabled.
- `link`: sends `label-format` text plus a short BM Web Chat image link.
- `label`: sends `label-format` text only.

`emoji.game-link.*` only affects web-to-Minecraft chat. Discord image preview links are controlled separately by `discordsrv.append-web-emoji-links` for web→Discord and `discordsrv.append-game-emoji-links` for game→Discord. `append-game-emoji-links` can augment DiscordSRV's normal Minecraft→Discord relay messages, while `game-to-discord` is only needed when you want BM Web Chat to send game chat to Discord directly.

BM Web Chat preserves canonical tokens in web history and relay payloads. If ImageEmojis or ImageEmojis-Bero is enabled, BMChat reads its public runtime emoji repository through reflection and resolves tokens to the receiving server's active glyphs before constructing clickable Minecraft components. This does not add a hard dependency and does not parse the resource pack.

For interactive lines, resolved ImageEmojis glyphs are inserted before BMChat adds sender, reply, and URL click events, so emoji rendering and clickable URLs work together. Only unresolved known tokens use the single plain-Bukkit-line fallback for compatibility with another game-side renderer; that fallback cannot carry BMChat click or hover metadata.

`default-pack` and `aliases` help map flat game-side tokens back to BM Web Chat pack/name ids. For example:

```yaml
emoji:
  game-link:
    default-pack: "default"
    aliases:
      wave: "default/wave"
```

GIF/JPG/JPEG/WEBP emoji originals automatically get same-folder PNG sidecars for compatibility with game-side emoji plugins that only read PNG files. The web UI keeps using the original file, so GIF animation is preserved.

## Group chat

`group-chat.enabled` enables the web group-chat system. It supports public/private rooms, optional hashed room passwords, invitations, leave room, room hide/restore, room settings, unread tracking, per-user message hiding, member kick/ban/unban, and owner transfer. Group messages are stored in `group-chat.sqlite-file` (default `group-messages.db`). `group-chat.retention-days: 0` disables age-based cleanup; positive values physically delete older group messages.


## Private chat metadata super admins

`private-chat-super-admins: []` lists exact UUIDs or Minecraft names allowed to see DM/group-chat metadata for moderation/accounting. The metadata view shows participants/titles, message counts, approximate storage size, retention status, and management actions. DM message bodies are available only when `direct-message.admin-audit.enabled: true`; that view is read-only and every page read is audit-logged.


`standalone-web.app-name` and `standalone-web.app-short-name` control the standalone page/PWA name. Reinstall the Home Screen web app after changing them on mobile devices. `web-push.notification-title` controls the default title used for test/system/background push notifications; if it is empty, the plugin uses `standalone-web.app-name`.


Existing configs that still contain old generated display names such as `BlueMapWebChat` or `BM WebChat` are treated as legacy defaults and use the new fallback.
