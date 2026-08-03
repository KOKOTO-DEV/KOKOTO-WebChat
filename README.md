# BlueMapWebChat

A web chat plugin for Bukkit/Paper/Spigot-compatible Minecraft servers. It can run as a BlueMap web addon, as a standalone `/chat` page served by the plugin, or both at the same time.

<img width="1057" height="682" alt="Image" src="https://github.com/user-attachments/assets/722761ea-94a4-4da9-be79-3cd04997c166" />

## Features

- BlueMap embedded chat panel and standalone web chat page
- Two-way game ↔ web chat relay
- Signed server-to-server public chat relay with per-server web badges and game/Discord server labels
- Clickable Minecraft replies (`/bmchat reply`) and linked web-sender DM shortcuts (`/bmchat dm`)
- Optional mirroring of game `/w`/`/msg`/`/tell`-style whispers into both users' BMChat web DM thread
- Guest chat with math captcha, cooldowns, and a 50 messages/minute default guest rate limit
- `/bmchat auth <code>` account linking, web password login, local admin accounts
- Web admin/moderator panel, message hiding, pin/delete action toggle, guest/IP mutes, session revoke
- Admin custom emoji manager: create, upload, rename, and delete emoji folders/files
- ImageEmojis-Bero 1.9.0 compatibility for token-preserving web/game/reply/relay rendering
- File and clipboard upload, image/video/audio/YouTube/Shorts previews, plus optional TikTok and X/Twitter embeds
- DiscordSRV relay and Discord CDN media cache
- Message replies with clickable referenced-message previews, optional game-side reply previews, pinned messages, virtual scrolling, draggable/resizable window, PIP
- Optional 1:1 direct-message threads for linked/known players, with unread badges and per-thread retention
- Built-in UI languages: en-US, ko-KR, ja-JP, zh-CN

## Build

```bash
mvn clean package
```

```text
target/BlueMapWebChat-4.6.0.jar
```

## Install

1. Put the jar into `plugins/`.
2. Start the server once to generate `plugins/BlueMapWebChat/config.yml`.
3. New generated configs start with top-level `enabled: false`; only the config is created until you review settings and opt in; /bmchat reload remains available.
4. Review storage, retention, upload, preview, authentication, and web exposure settings, then set `enabled: true`.
5. For BlueMap embedded mode, keep `web-addon.auto-install` and `web-addon.auto-patch-webapp-conf` enabled.
6. For standalone-only mode, set `standalone-web.enabled: true`, `web-addon.auto-install: false`, and `web-addon.auto-patch-webapp-conf: false`.
7. Restart the server or run `/bmchat reload`. Run `/bluemap reload` if BlueMap does not refresh web assets automatically.


Existing configs are never overwritten. When `config-version` is missing or differs from the running plugin version, BlueMapWebChat compares the physical `config.yml` with the bundled defaults and writes `plugins/BlueMapWebChat/config-migration-4.6.0.yml`. The generated file contains copy-ready missing settings, changed bundled defaults, and the target `config-version` review marker. Even when no other settings differ, the file is still created so configuration version management remains explicit. Version details and previous/new default values are comments, not YAML settings. Custom values and obsolete-setting notes are omitted. When `config-version: "4.6.0"` already matches the plugin version, the config is treated as reviewed and comparison is skipped. See `docs/UPGRADE_4_6_0_EN.md`.

## Deployment modes

### BlueMap addon and standalone page together

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true

standalone-web:
  enabled: true
  path: "/chat"
```

### Standalone only

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false

standalone-web:
  enabled: true
  path: "/chat"
```

Standalone URL:

```text
http://<server-host>:8899/chat
```

## HTTPS / Caddy recommended setup

For public servers, keep BlueMap and BlueMapWebChat as internal HTTP services and expose them through HTTPS.

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
  path: "/chat"
  # Optional. This may be the same route as web-addon.api-base-url.
  api-base-url: "/bmwc/api"

upload:
  # Recommended: keep empty so uploads follow /bmwc/api automatically.
  # Legacy explicit forms also work: "/bmwc/api" or "/bmwc/api/uploads".
  public-base-url: ""
  # 0 = unlimited. Positive values cap total files in upload.directory.
  max-total-size-mb: 0

emoji:
  # Recommended: keep empty so emoji files follow /bmwc/api automatically.
  # Legacy explicit forms also work: "/bmwc/api" or "/bmwc/api/emojis".
  public-base-url: ""
  max-total-size-mb: 64
  show-storage-usage: true
  show-storage-limit: true
```

Example paths:

```text
https://map.example.com/          # BlueMap
https://map.example.com/bmwc/api  # BlueMapWebChat API
https://map.example.com/bmwc/chat # standalone chat
```


URL settings note: in HTTPS reverse proxy mode, set `web-addon.api-base-url` to the public API path such as `/bmwc/api`. `standalone-web.api-base-url`, `upload.public-base-url`, and `emoji.public-base-url` usually stay empty. When empty, standalone reuses the web-addon API base, and uploads/emojis append `/uploads` and `/emojis` automatically. Legacy explicit values such as `/bmwc/api`, `/bmwc/api/uploads`, and `/bmwc/api/emojis` are also accepted. Relative values without a leading `/` are resolved against `http.cors-origin` when it is a real origin.

See `docs/CADDY_HTTPS_EN.md` for details.

## Common options

- `ui.language`: default UI language. `en-US`, `ko-KR`, `ja-JP`, `zh-CN`
- `ui.theme`: `system`, `dark`, `light`, `high-contrast`
- `player-display.mode`: `name`, `display-name`, `custom-name`
- `player-display.strip-colors`: when `false`, Minecraft legacy colors are rendered for actual chat sender names. System/event lines are always stripped.
- `commands.enabled`: enable the web command panel
- `commands.allow-all`: allow arbitrary console commands instead of presets only
- `commands.run-from-chat-input`: allow `/command` execution from the normal chat input
- `ui.picture-in-picture.enabled`: controls both the PIP button and PIP execution

## SQLite history

Chat history uses SQLite by default in new configs (`chat.history-storage: "sqlite"`). This keeps long-lived logs in `plugins/BlueMapWebChat/history.db` and makes paging, reply jumps, deletion, and retention cleanup easier to maintain than the legacy single JSONL file.

Legacy modes remain available: use `chat.history-storage: "jsonl"` for the old `history.jsonl` file, or `"memory"` for session-only history. `chat.history-size` and `chat.history-retention-days` apply to memory, JSONL, and SQLite. Newly generated configs start with top-level `enabled: false`, so cleanup cannot run until you review retention values and set `enabled: true`. If `chat.history-sqlite-migrate-jsonl` is true, an empty SQLite DB imports the existing JSONL history once.

A `/history/search` API and in-chat search modal are available for message text and sender searches, with optional date/time range, sender, source, and system/event filters. The search button is placed in the floating chat-panel area so the message input row stays compact, and the search modal follows the configured chat theme/font settings with a scrollable result list. i18n-backed system/event messages are searched and displayed in the selected web UI language when possible. Search can be disabled with `search.enabled`, and the single `search.result-limit` setting controls both the web UI result count and the `/history/search` API limit. There is no separate internal maximum: setting it to 2000 returns up to 2000 results, while setting it to 10 returns up to 10. Very large values such as 10000 or 100000 are accepted, but they can slow searches, increase response size, and add significant CPU, memory, and database load. The default is 50, and 50-200 is recommended for normal use. Existing config files from older versions need these keys added manually or merged from the default config.


## Group chat rooms

`group-chat.enabled` enables the web group-chat room system. Users can create rooms, choose public/private visibility, set an optional room password, invite known players, accept or decline invitations, leave rooms, hide rooms from their own list and restore them later, edit room settings, kick or ban members, unban users, transfer room ownership, and send messages from the web UI. Public rooms appear in the room list; private rooms are invite-only. Room passwords are stored as PBKDF2 hashes, not plain text.

Group chats use a dedicated SQLite store (`group-chat.sqlite-file`, default `group-messages.db`). `group-chat.retention-days: 0` means no time limit; positive values are shown next to the group-chat title and old group messages are physically removed after that many days. `group-chat.max-messages-per-room: 0` disables count-based cleanup. This release remains web-first; game-side `/bmchat group` commands, room mute, group role-management beyond owner/member actions, and JSONL group storage are not included yet.

## Direct message threads

`direct-message.enabled` enables optional 1:1 conversation threads. Targets are limited to linked or previously known players with a stored UUID/name. A->B and B->A use the same thread, and messages are stored by UUID while the UI displays `display name (real account name)` when both are available.

DMs use an independent private-message store. `direct-message.storage: auto` follows `chat.history-storage` when public chat uses `jsonl`; otherwise it uses SQLite. You can also set `direct-message.storage` to `sqlite` or `jsonl` explicitly, using `direct-message.sqlite-file` or `direct-message.jsonl-file`. `direct-message.retention-days: 0` means no time limit; otherwise the DM window title shows the configured retention period and old DM rows are physically removed after that many days. `direct-message.max-messages-per-thread: 0` disables count-based cleanup. `direct-message.confirm-hide` controls whether the web UI asks before hiding a DM from your own view. Because private messages are stored on the server, the feature is disabled by default and should be enabled only after setting a server policy.


Game `/w`, `/msg`, `/tell`, and compatible aliases can be mirrored into the same web DM thread with `direct-message.capture-game-whispers`. Clicking a local game sender suggests `/w <realName> `; linked web senders and remote-server game senders suggest `/bmchat dm <realName> `.

## Custom emoji and game-side emoji plugins

BlueMapWebChat stores custom emoji files under `plugins/BlueMapWebChat/emojis`. Subfolders are treated as emoji packs.

By default, web-to-game chat preserves custom emoji tokens such as `:default/wave:` and `:emoji:default/wave:`. Use this default when ImageEmojis or another game-side emoji plugin renders the same token text in Minecraft chat.

`emoji.game-link.mode` supports `preserve`, `link`, and `label` when `emoji.game-link.enabled` is enabled.

- `preserve`: keeps the original token text unchanged.
- `link`: sends the configured token text plus a short BM Web Chat image link.
- `label`: sends only the configured token text.

`emoji.game-link.*` only affects web-to-Minecraft chat. Discord image preview links are controlled separately: `discordsrv.append-web-emoji-links` handles web→Discord messages, and `discordsrv.append-game-emoji-links` handles game→Discord tokens by augmenting DiscordSRV's normal Minecraft→Discord relay when possible. When several servers share one Discord channel, only the server that observed the original local game chat edits that native DiscordSRV message; peer servers do not stack their own labels or emoji links. Receiving relay peers never re-send the message to Discord. Leave `game-to-discord` disabled when DiscordSRV already relays normal Minecraft chat to avoid duplicate posts.

BM Web Chat keeps the canonical token text in web history and server-relay payloads. When ImageEmojis or ImageEmojis-Bero is enabled, BMChat reads its public runtime emoji repository through reflection and uses the receiving server's current token-to-glyph mapping when it builds clickable Minecraft components. No hard plugin dependency or resource-pack parsing is required; unresolved tokens still fall back to the normal game-side rendering path.

When GIF/JPG/JPEG/WEBP emoji files are uploaded, BlueMapWebChat also creates a same-folder PNG sidecar for compatibility with game-side emoji plugins that only read PNG files:

```text
plugins/BlueMapWebChat/emojis/default/wave.gif
plugins/BlueMapWebChat/emojis/default/wave.png
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
/bmchat dm <player> <message>
/bmchat reply <messageId> <message>
/bmchat auth <code>
/bmchat password <newPassword>
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

## Permissions

```text
bluemapwebchat.auth
bluemapwebchat.webchat
bluemapwebchat.dm
bluemapwebchat.reply
bluemapwebchat.group
bluemapwebchat.admin
```

## Documentation

- `docs/USER_MANUAL_EN.md` - complete user and operator manual for all features
- `docs/CONFIGURATION_EN.md` - configuration reference
- `docs/SERVER_RELAY_EN.md` - server-to-server public chat relay
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

Set `private-chat-super-admins` in `config.yml` to exact UUIDs or Minecraft names for users who may see DM/group-chat metadata for moderation/accounting. This view only shows titles/participants, message counts, approximate stored byte sizes, retention status, and cleanup preview counts. It does not expose message bodies or allow opening other users' conversations. Super admins can also lock a DM/group session or exclude it from automatic retention cleanup; these controls are admin-only.

Administrative actions are also appended to date-based text audit files under `plugins/BlueMapWebChat/audit` by default. The audit log is intended for server operators and is not shown in the web UI.


Note: `standalone-web.app-name` / `standalone-web.app-short-name` can change the mobile Home Screen web app name, and `web-push.notification-title` can change the default push title. Server notifications can be set to all, join/leave only, or off in Chat settings. If `web-push.notification-title` is empty, `standalone-web.app-name` is used. Android/desktop browsers can enable push from either the BlueMap addon or the standalone page when HTTPS and Push API support are available. On iOS/iPadOS, use a page added to the Home Screen and opened as a web app rather than a normal browser tab.


Existing configs that still contain old generated display names such as `BlueMapWebChat` or `BM WebChat` are treated as legacy placeholders so they no longer appear as push titles by default.
