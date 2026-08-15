# BlueMapWebChat 4.7.0 Complete User and Operations Manual

This manual describes all BlueMapWebChat 4.7.0 features from both the user and server-operator perspectives. For an option-by-option reference, see `CONFIGURATION_EN.md`. For relay protocol details, see `SERVER_RELAY_EN.md`. For HTTPS deployment, also see `CADDY_HTTPS_EN.md` and `NGINX_HTTPS_EN.md`.

## 1. Overview

BlueMapWebChat connects Minecraft chat on Bukkit/Paper/Spigot-compatible servers to a browser-based chat interface.

Supported deployment and feature areas:

- Chat panel embedded in BlueMap
- Standalone chat page without BlueMap
- Embedded and standalone modes at the same time
- Bidirectional game and public web chat
- Persistent direct-message threads and group rooms
- DiscordSRV integration
- Public-chat relay between multiple Minecraft servers

The default HTTP port is `8899`, the default API prefix is `/api`, and the default standalone path is `/chat`.

## 2. Requirements and Recommended Environment

Required:

- A Bukkit/Paper/Spigot-compatible Minecraft server in the conservative supported range **1.18 through 26.2**
- **Java 17 or newer as required by the selected Minecraft server version**; BlueMapWebChat itself is compiled for Java 17
- Permission to install plugin JAR files

BlueMapWebChat 4.7.0 declares `api-version: '1.18'` and compiles against `spigot-api:1.18.2-R0.1-SNAPSHOT`. Minecraft 1.17 and older are not claimed by this release.

Optional integrations:

- BlueMap for an embedded map chat panel
- DiscordSRV for Discord integration
- ImageEmojis-Bero 1.9.0 for in-game rendering of BMChat emoji tokens
- Caddy or Nginx for a public HTTPS deployment

For public servers, do not expose port `8899` directly to the Internet. Bind BlueMapWebChat to `127.0.0.1:8899` and publish it through an HTTPS reverse proxy.

## 3. Installation and First Enable

1. Put the built JAR in the server `plugins/` directory.
2. Start the server once.
3. Confirm that `plugins/BlueMapWebChat/config.yml` was created.
4. A newly generated configuration uses `enabled: false`.
5. Review URLs, storage, retention, authentication, and upload limits.
6. Enable the features you need and set `enabled: true`.
7. Restart the server or run `/bmchat reload`.

Safe initial state:

```yaml
config-version: "4.7.0"
enabled: false
```

While disabled, the web service, chat forwarding, and cleanup tasks do not start. Administrators can still use `/bmchat reload`.

## 4. Configuration Upgrade and Migration Fragment

BlueMapWebChat never overwrites existing setting values during an update. On startup/reload, known top-level config blocks are reordered to the bundled 4.7.0 layout while each block's current text, values, and custom comments are preserved; unknown top-level blocks remain last in their original order.

When `config-version` is missing or differs from the running plugin version, the plugin creates:

```text
plugins/BlueMapWebChat/config-migration-4.7.0.yml
```

The plugin also writes `plugins/BlueMapWebChat/config-reference-4.7.0.yml`, an exact full copy of the current bundled default config including comments. Use this file as the authoritative comparison source for older or unversioned configurations; the migration fragment remains the concise list of changes to review. At the bottom of the migration file, a comment-only textual diff shows only differing lines. Each side prints the file name, then `Line` or `Lines` on its own line, followed by the differing text. Each differing source line is prefixed directly with `#`, preserving its original YAML indentation; reference-only blocks also show their insertion position in the current config.

Decision rules:

| Physical `config.yml` state | Behavior |
|---|---|
| `config-version` is missing | Create the migration file with the target version marker even when there are zero other differences |
| `config-version` differs from the plugin version | Create or refresh the migration file with missing/changed settings and the target version marker |
| `config-version` matches the plugin version | Treat the configuration as reviewed, skip migration comparison/report creation, remove stale migration guidance, and still keep the full reference current |

The file contains copy-ready YAML for:

- New settings missing from the installed configuration
- Defaults that changed while the installed value still matches the old default
- The target `config-version` review marker

Even when no other settings differ, the file is created with `config-version` so configuration version management remains explicit.

Counts and old/new value explanations are comments beginning with `#`. The live `config.yml` is not changed.

Upgrade procedure:

1. Open the migration fragment.
2. Merge the required blocks into the matching locations in `config.yml`.
3. Adjust server-specific and custom values.
4. After review, set:

```yaml
config-version: "4.7.0"
```

When the version matches, future comparisons are skipped.

## 5. Choose a Deployment Mode

### 5.1 BlueMap Addon

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true

standalone-web:
  enabled: false
```

The plugin installs web assets under the BlueMap web directory and updates `plugins/BlueMap/webapp.conf`. If BlueMap does not pick up the new assets, run:

```text
/bluemap reload
```

### 5.2 Standalone Only

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false

standalone-web:
  enabled: true
  path: "/chat"
```

Direct HTTP example:

```text
http://server.example.com:8899/chat
```

### 5.3 Embedded and Standalone Together

The embedded panel and standalone page share the same accounts, history, notifications, and server-side settings.

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true

standalone-web:
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

web-addon:
  api-base-url: ""
```

### 6.2 Same-Domain HTTPS Reverse Proxy

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"

web-addon:
  api-base-url: "/bmwc/api"

standalone-web:
  enabled: true
  api-base-url: ""
```

Public paths:

```text
https://map.example.com/          BlueMap
https://map.example.com/bmwc/api  BlueMapWebChat API
https://map.example.com/bmwc/chat Standalone chat
```

Normally leave `standalone-web.api-base-url`, `upload.public-base-url`, and `emoji.public-base-url` empty. They then follow the active public API base automatically.

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

Browser-specific preferences are stored in localStorage. Theme, fonts, and notification filters can differ between browsers even for the same account. The panel can be moved and resized, and its size can be remembered. A browser-local notification inbox keeps recent notification-worthy events.

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

BlueMapWebChat 4.7.0 can replace administrator-configured `:alias:` tokens before messages are stored or relayed. The built-in alias names are English-only defaults, but aliases can be replaced or extended in any language.

Default controls:

- `:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, `:br:` → one new line
- `:blankline:`, `:emptyline:`, `:paragraphbreak:` → one empty line
- `:tab:`, `:indent:` → configurable spaces (4 by default)

Unknown tokens are left unchanged, so ImageEmojis/custom emoji tokens continue to work. Backslash escape forms such as `:\n:` are not supported. Printable custom replacements can also be configured, for example `:separator:` → `────────────`.

For Minecraft output, ordinary CR/LF characters still follow the existing one-line flattening behavior. Only line breaks produced by configured `newline` / `blank-line` aliases are carried through that sanitizer and emitted as explicit Minecraft chat lines at final delivery, so `:enter:` works without changing the treatment of arbitrary pasted newlines. For server-relayed game output, the receiving BlueMapWebChat server must also have the same 4.7.0 token-line delivery support; an older receiver flattens the normal relayed LF.

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
/bmchat auth <code>
```

Permission:

```text
bluemapwebchat.auth
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
/bmchat password <newPassword>
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
  admin-permission: "bluemapwebchat.admin"
```

### 10.4 Local Administrator Accounts

Local web administrator accounts can exist without a linked Minecraft UUID.

```yaml
admin:
  allow-local-admin-accounts: true
```

Commands:

```text
/bmchat admin create <id>
/bmchat admin password <id> <password>
/bmchat admin role <id> <user|moderator|admin>
```

### 10.5 Session Management

```text
/bmchat sessions
/bmchat revoke <username>
```

`revoke` removes active sessions and notifies connected browser clients that authentication expired.

## 11. Login and Connection Security

```yaml
security:
  login-fail-limit: 5
  login-fail-window-seconds: 300
  login-lock-seconds: 600
  max-sse-connections-per-ip: 5
  max-sse-connections-total: 200
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
  expire-seconds: 120
  require-on-each-message: false
  pass-valid-minutes: 120
```

Guest/IP moderation commands:

```text
/bmchat guest mute guest <name> [minutes] [reason]
/bmchat guest mute ip <address> [minutes] [reason]
/bmchat guest unmute guest <name>
/bmchat guest unmute ip <address>
/bmchat guest list
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
- Web sender: `/bmchat dm <realName> `
- Remote-server game sender: `/bmchat dm <realName>@<server-id> `

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

Clicking a non-URL part of a BMChat-rendered Minecraft message suggests:

```text
/bmchat reply <messageId> 
```

Send a reply with:

```text
/bmchat reply <messageId> <message>
```

Permission:

```text
bluemapwebchat.reply
```

`local-game-chat: true` replaces local game chat display with an equivalent clickable component. Disable it if another chat-format plugin must own final rendering. Web-to-game and relay replies continue to work when it is disabled.

URLs keep their open-link action. Only non-URL message parts suggest the reply command.

## 15. Direct Messages

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
  confirm-hide: true
```

Recipients must be known by UUID. In addition to local join and linked-account records, a relayed game or linked-web message with `playerUuid` registers the sender's display name and real Minecraft name in the new-conversation recipient search. This allows a remote-server sender seen in public chat to be found through the normal DM search and sent through the existing DM path. The latest identity is retained in `known-display-names.yml` and remains searchable after restart. Guest and Discord messages without a player UUID are not registered. With `storage: auto`, DM uses JSONL only when public chat storage is JSONL; otherwise it uses SQLite. You may explicitly select `sqlite` or `jsonl`.

Game commands:

```text
/bmchat dm
/bmchat dm list [pageSize]
/bmchat dm unread [pageSize]
/bmchat dm list next
/bmchat dm list prev
/bmchat dm <player> <message>
/bmchat dm read <player> [pageSize]
/bmchat dm next
/bmchat dm prev
/bmchat dm hide <messageId>
```

Permission:

```text
bluemapwebchat.dm
```

### 15.1 Capture Minecraft Whispers

When `capture-game-whispers: true`, the following commands are copied into the same BMChat DM thread:

```text
/w /msg /tell /whisper /m /pm /message /t
```

BlueMapWebChat does not replace a normal same-server Minecraft whisper. It records a copy for both sender and recipient. For a remote target, use `name@server-id`; the same aliases are rewritten to `/bmchat dm name@server-id <message>` and sent through the signed cross-server DM relay. An unqualified `/bmchat dm <name>` resolves only to a player on the current server. `/r` and `/reply` are not intercepted because they contain no target and remain owned by the server's existing whisper plugin.

### 15.2 Delivery and read status

Normal successful delivery is not labeled. `Sending` appears only while a local send request is pending, and `Failed · Retry` appears only when delivery cannot be confirmed. These compact states are displayed beside the message timestamp. Read status is shown beside the timestamp for every DM message: `Unread` means the single recipient has not read the message yet, and `✓` means the recipient has read it. Group chat remains count-based. For cross-server DMs, the recipient server returns the read acknowledgement through the authenticated relay so the same status is reflected on the message origin. The latest acknowledgement is idempotent and is re-sent when the conversation is viewed, allowing a transient relay or HTTP failure to repair on a later view.

## 16. Group Chat

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

Web features include public/private room creation, optional room passwords stored as PBKDF2 hashes, invites, accept/reject, leave, hide/restore, room settings, unread tracking, per-user message hiding, member kick/block/unblock, and ownership transfer.

Every group message shows its recipient read state. The number is the count of current room members who were already members when the message was sent and have not yet read it; the message sender is not a recipient. When the unread-recipient count reaches zero, the number changes to `✓`. Normal successful delivery itself is not labeled.

Game commands:

```text
/bmchat group
/bmchat group list
/bmchat group rooms
/bmchat group <room|id> <message>
/bmchat group send <room|id> <message>
/bmchat group read <room|id> [pageSize]
/bmchat group next
/bmchat group prev
```

Alias:

```text
/bmchat gc ...
```

Permission:

```text
bluemapwebchat.group
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

Pinned messages are stored separately from normal history and appear in a compact top bar. Referenced uploads are protected from cleanup when `preserve-uploads` is enabled. Moderator/admin pin and delete controls are shown only after the corresponding temporary admin-panel toggle is enabled.

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

See `UPLOAD_SECURITY_EN.md` for security guidance.

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
plugins/BlueMapWebChat/emojis/default/wave.png
plugins/BlueMapWebChat/emojis/reaction/happy.gif
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

BMChat conversion mode:

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

## 22. ImageEmojis-Bero 1.9.0 Integration

Recommended ImageEmojis-Bero settings:

```yaml
emojisFolder: "/BlueMapWebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

Player permission:

```text
imageemojis.use
```

`replaceInCommands: true` is required for token conversion inside `/bmchat reply`, `/bmchat dm`, and `/bmchat group`.

In a relay deployment, every server must have matching pack and file names. BlueMapWebChat preserves canonical tokens in web history and relay payloads, then uses the receiving server's runtime token-to-glyph map for Minecraft output.

Recommended refresh order after emoji changes:

```text
/emojis reload
/emojis update
```

Reconnect if the resource pack must be refreshed. See `IMAGEEMOJIS_BERO_1_9_0_EN.md` for full details.

## 23. Browser Notifications and Web Push

```yaml
notifications:
  enabled: true
  only-when-hidden: true
  notify-normal-chat: true
  notify-dm: true
  notify-group-chat: true
  notify-mentions: true
  notify-replies: true
  notify-system: true
  notify-keywords: true
  notify-own-messages: true
  show-message-preview: true
```

Users can further restrict allowed notification types per browser. A server-side `false` cannot be overridden by the user. The browser-local notification inbox keeps recent events, and notification clicks can navigate to a public message, reply, DM thread, or group room when a target is available.

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
standalone-web:
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

```yaml
discordsrv:
  enabled: true
  channel: "global"
  web-to-discord: true
  game-to-discord: false
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
  game-to-discord-format: "[{server}] {sender}: {message}"
  discord-to-web-sender-format: "Discord:{sender}"
  discord-to-web-message-format: "{message}"
```

If DiscordSRV already relays normal Minecraft chat, keep BMChat `game-to-discord: false` to avoid duplicate posts.

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

## 26. Multi-Server Public Chat Relay

Every server needs a unique `server-id`.

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "Server1"
  shared-secret: "a-long-shared-secret"
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
    - id: "server2"
      url: "https://server2.example.com/bmwc/api"
      secret: ""
      enabled: true
```

Actual request URL:

```text
https://server2.example.com/bmwc/api/relay/receive
```

Rules:

- The receiving `peers[].id` must equal the sender's `server-id`
- Shared-secret deployments use the same secret on connected servers
- A non-empty peer secret overrides the shared secret
- Requests are rejected when server clocks differ beyond the configured skew
- There is no persistent offline delivery queue

Display behavior:

- Local-origin messages omit a server label
- Remote-origin messages show a colored web badge and a game `[server-name]` label

Expected log:

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

Errors:

- `403 unknown_peer`: sender ID is not an active peer on the receiver
- `401 bad_signature`: secret/signature mismatch
- `401 expired_request`: server clock skew
- `404 relay_disabled`: relay disabled or reverse proxy points to the wrong path/instance
- `426 unsupported_protocol`: incompatible relay protocol version

See `SERVER_RELAY_EN.md` for topologies and troubleshooting.

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

- Message hide/delete controls
- Pin management
- Guest and IP mutes
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

### 28.1 Private-Chat Metadata Super Administrators

```yaml
private-chat-super-admins:
  - "ExactMinecraftName"
  - "00000000-0000-0000-0000-000000000000"
```

The metadata view shows room/thread titles and participants, message counts, approximate storage usage, retention state, cleanup previews, and metadata-management actions.

To allow read-only DM content review, enable the separate switch:

```yaml
direct-message:
  admin-audit:
    enabled: true
```

Only accounts that satisfy both `private-chat-super-admins` and this switch can open an administrator DM thread. Normal ADMIN/MODERATOR roles are not enough. The audit view cannot send messages, hide participant messages, or mark them read. Every page load writes an `admin.dm-audit-read` record to the audit log without copying message bodies into the log.

### 28.2 Audit Log

```yaml
audit:
  enabled: true
  directory: "audit"
```

Administrative actions are appended to dated files under `plugins/BlueMapWebChat/audit` by default. Audit records are not displayed in the web UI.

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
    preserve-visible-media: false
    preserve-playing-media: true
  history-preload:
    screens: 0.7
    min-px: 200
```

Virtual scrolling reduces browser rendering cost for long histories. Preserving more media increases memory use.

Resume refresh:

```yaml
ui:
  resume-refresh:
    enabled: true
    min-interval-seconds: 5
    skip-while-media-active: true
    skip-unchanged: true
```

This refreshes missed messages after returning to a mobile or backgrounded page without unnecessarily interrupting active media.

## 31. Complete Command Reference

User commands:

```text
/bmchat auth <code>
/bmchat password <newPassword>
/bmchat dm
/bmchat dm list [pageSize]
/bmchat dm unread [pageSize]
/bmchat dm <player> <message>
/bmchat dm read <player> [pageSize]
/bmchat dm next
/bmchat dm prev
/bmchat dm hide <messageId>
/bmchat reply <messageId> <message>
/bmchat group list
/bmchat group <room> <message>
/bmchat group send <room> <message>
/bmchat group read <room> [pageSize]
/bmchat group next
/bmchat group prev
```

Administrator commands:

```text
/bmchat reload
/bmchat admin create <id>
/bmchat admin password <id> <password>
/bmchat admin role <id> <user|moderator|admin>
/bmchat guest mute <guest|ip> <value> [minutes] [reason]
/bmchat guest unmute <guest|ip> <value>
/bmchat guest list
/bmchat sessions
/bmchat revoke <username>
```

Root aliases:

```text
/bmc
/bluemapchat
```

Group alias:

```text
/bmchat gc
```

## 32. Permission Reference

```text
bluemapwebchat.auth      Link a web account
bluemapwebchat.webchat   Use authenticated web chat
bluemapwebchat.dm        Send and read direct messages
bluemapwebchat.reply     Reply to public messages from Minecraft
bluemapwebchat.group     Use group chat
bluemapwebchat.admin     Administer the plugin
bluemapwebchat.update.notify  Receive update notices (OP by default)
```

User permissions are allowed by default. `bluemapwebchat.admin` and `bluemapwebchat.update.notify` default to OP.

## 33. Data Files and Backup

Common files:

```text
plugins/BlueMapWebChat/config.yml
plugins/BlueMapWebChat/history.db
plugins/BlueMapWebChat/direct-messages.db
plugins/BlueMapWebChat/group-messages.db
plugins/BlueMapWebChat/web-push-subscriptions.jsonl
plugins/BlueMapWebChat/emojis/
plugins/BlueMapWebChat/uploads/
plugins/BlueMapWebChat/audit/
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

Usually reloadable with `/bmchat reload`:

- Most configuration changes
- HTTP service and relay reinitialization
- UI default changes

Requires a server restart:

- JAR replacement
- Java code changes
- Plugin load-order changes
- Some port-lock or web-resource conditions

If only BlueMap web assets appear stale, also run `/bluemap reload`.

## 35. Troubleshooting Quick Reference

### Page Does Not Open

- Confirm `enabled: true`
- Check `http.host` and `http.port`
- Check for a port conflict
- Confirm reverse-proxy upstream points to `127.0.0.1:8899`
- Enable `standalone-web.enabled` when a standalone page is required

### No BlueMap Chat Button

- Enable `web-addon.auto-install`
- Enable `web-addon.auto-patch-webapp-conf`
- Check BlueMap paths
- Read installation/patch log messages
- Run `/bluemap reload`
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

- Confirm the BMChat emoji file exists
- Check ImageEmojis-Bero shared folder and permission
- Enable `replaceInCommands`
- Run `/emojis reload` and `/emojis update`
- Confirm matching emoji files on every relay server

### Relay Returns 403

- Compare receiver `peers[].id` with sender `server-id`
- Reload the receiving server too
- Check the active-peer log

### Relay Returns 401

- Compare the actual effective secrets
- Confirm the proxy does not modify body or HMAC headers
- Check server clock synchronization

### Discord Prefixes Are Duplicated

- Confirm every server runs the same fixed build
- Check for another plugin reposting relay messages
- Keep `game-to-discord: false` when DiscordSRV already forwards game chat

### Web Push Does Not Work

- Confirm HTTPS
- Check notification permission
- Check Service Worker and Push API support
- On iOS, use an installed Home Screen web app
- Check VAPID subject and key files

## 36. Related Documents

- `CONFIGURATION_EN.md`: detailed setting reference
- `SERVER_RELAY_EN.md`: relay topology, authentication, and errors
- `UPGRADE_4_6_4_EN.md`: 4.6.3→4.7.0 upgrade
- `UPGRADE_4_6_3_EN.md`: 4.6.2 to 4.6.3 upgrade
- `UPGRADE_4_6_2_EN.md`: 4.6.1 to 4.6.2 upgrade
- `UPGRADE_4_6_1_EN.md`: 4.6.0 to 4.6.1 upgrade
- `UPGRADE_4_6_0_EN.md`: 4.5.5 to 4.6.0 upgrade
- `CADDY_HTTPS_EN.md`: Caddy HTTPS
- `NGINX_HTTPS_EN.md`: Nginx HTTPS
- `IMAGEEMOJIS_BERO_1_9_0_EN.md`: ImageEmojis-Bero integration
- `INSTALL_TROUBLESHOOTING_EN.md`: installation troubleshooting
- `UPLOAD_SECURITY_EN.md`: upload security
- `OPERATIONS_SECURITY_EN.md`: public operations security
- `I18N_EN.md`: language files and fallback
- `RELEASE_CHECKLIST_EN.md`: release checklist

### Administrator group-chat body audit (4.6.3)

Set `group-chat.admin-audit.enabled: true` and list the exact Minecraft name or UUID in `private-chat-super-admins`. Both gates are required. A qualifying administrator may open group-chat bodies from the administrator room metadata list even when they are not a room member. The audit view is read-only: it does not join the room, mark messages read, change unread counts, send/upload/hide messages, or change membership. Every page read records `admin.group-audit-read`; message bodies are not copied into the audit log.

