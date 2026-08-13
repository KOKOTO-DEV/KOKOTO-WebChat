# Changelog

## 4.6.2

- Fixed same-name direct-message routing. A DM target without a server qualifier resolves only on the current server; cross-server targets require explicit server-scoped identity from `name@server-id` or the web UI.
- Added end-to-end acknowledgement for cross-server DMs. A remote DM remains pending until the destination server confirms that it was stored; routing, HTTP, timeout, and destination failures become retryable failures instead of appearing successfully delivered.
- Added idempotent DM retry using persistent relay IDs and web client message IDs. Retrying after an uncertain or failed request does not create duplicate sender or receiver messages, and interrupted pending deliveries recover as retryable failures after restart.
- Added the same browser-side pending/failure/retry and client-message-id duplicate protection to group-chat sends. Normal successful delivery is intentionally not labeled; only the compact localized `Sending` state or `Failed · Retry` action is shown beside the message timestamp.
- Added read status to every DM and group-chat message, displayed beside the message timestamp. A 1:1 DM shows a short localized unread label (`Unread` in English) until its recipient reads it, then changes to `✓`. Group chat keeps the unread-recipient count and changes to `✓` when that count reaches zero.
- Added authenticated cross-server DM read acknowledgements so read state for remote DMs is reflected on the message origin server. Read acknowledgements are idempotent and are re-sent when the conversation is viewed, so a temporary relay/HTTP failure does not permanently suppress the read mark.
- Changed multi-hop private relay acknowledgement so an intermediate hub reports success only after the final destination confirms storage.
- Fixed update notifications so an eligible administrator login refreshes stale Modrinth release state, OPs are explicitly recognized in addition to the update-notify permission, failed checks are visible in the server log, and reloads do not retain obsolete update-check listeners.

### Cross-server DM compatibility

All servers that exchange cross-server DMs should run **BlueMapWebChat 4.6.2 or later** to use end-to-end delivery confirmation, retry, and remote read-status acknowledgement.

### Configuration

4.6.2 adds no administrator-facing configuration keys and changes no existing setting defaults. The configuration review marker changes to:

```yaml
config-version: "4.6.2"
```

## 4.6.1

- Fixed cross-server direct messaging from the web interface. This includes exact `server-id + player UUID` targeting, shared game/web sessions, destination-server delivery, remote DM search, message-metadata shortcuts, game `/bmchat dm` and `name@server-id` whisper routing, and server identification in the DM window before the first message is sent.
- Added an optional read-only administrator DM audit view for responding to misconduct or other exceptional incidents. Access is limited to explicitly listed private-chat super administrators, and every audit read is recorded without copying message bodies into the audit log.
- Added a compact update checker. It uses Modrinth as the release source, logs a newer stable version to the console, and shows administrators a one-line join notice with clickable Modrinth and CurseForge pages. Only `update-check.enabled` is configurable; timing, channel, permission behavior, and links use built-in defaults.
- Reorganized the bundled `config.yml` into clearly labeled functional groups without renaming existing keys or changing their defaults. The only newly exposed update-check setting is the single `update-check.enabled` switch directly below the plugin master switch.

### Important cross-server DM compatibility requirement

All servers that exchange cross-server DMs must run the same corrected **4.6.1 build**. Matching the displayed version number alone is not sufficient because earlier 4.6.1 builds do not include the complete private DM relay and exact target handoff changes. Replace the plugin on every connected server and restart all of them; otherwise the sender may create a session while the destination server does not receive or store it.

### Configuration added in 4.6.1

The functional settings added compared with the bundled 4.6.0 defaults are:

```yaml
update-check:
  enabled: true

direct-message:
  admin-audit:
    enabled: false
```

The configuration review marker also changes to:

```yaml
config-version: "4.6.1"
```

To enable DM body auditing, both the allowlist and the audit switch are required:

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

direct-message:
  admin-audit:
    enabled: true
```

Ordinary ADMIN or MODERATOR roles do not gain DM body access automatically. The audit view is read-only.



## 4.6.0

- Added secure multi-server game/web chat relay with HTTPS and HMAC authentication.
- Added server-aware message labels and per-server web badge colors.
- Added in-game message replies using `/bmchat reply`.
- Added BMChat DM suggestions for web and remote-server senders.
- Added game whisper capture into BMChat DM history.
- Added ImageEmojis-Bero 1.9.0 compatibility for chat, replies, DMs, and relayed messages.
- Preserved URLs, emoji tokens, message IDs, reply references, and origin metadata across relays.
- Prevented duplicate Discord delivery and repeated server/source prefixes in shared channels.
- Restored the normal asynchronous logging path for local Minecraft chat.
- Added automatic SQLite relay metadata migration.
- Added a non-destructive configuration migration fragment for missing and changed settings.
- Updated the changelog, configuration manuals, upgrade guides, and four-language documentation.


## 4.5.5

- Expanded `config.yml` inline documentation for supported option values and placeholders beyond the game-name hover text, including Google Drive preview mode, X/Twitter embed theme, clipboard upload behavior, command role values, web font item fields, Web Push TTL range, UI scroll/opacity ranges, and upload/cache retention semantics.
- Fixed duplicate external link openings on mobile browsers by debouncing same-link tap handling and preventing the native anchor fallback before the custom `window.open` path runs.
- Added an optional Spigot-compatible in-game sender-name hover for web-to-Minecraft chat relays. When `player-display.mode` is `display-name` or `custom-name` and the displayed sender name differs from the real Minecraft account name, hovering the displayed name can show the real account name without requiring Paper-only APIs.
- Added `chat.game-name-hover.enabled` and `chat.game-name-hover.text` settings. The default hover text is now just `{real}`; the template still supports legacy color codes and `{display}`, `{real}`, `{uuid}`, and `{source}` placeholders for servers that want more detail.

```yml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

## 4.5.4

- Fixed Web Push notification filter drift when the same account has multiple saved browser/mobile subscriptions. Updating notification options now also synchronizes the same account's existing Web Push subscriptions, so older endpoints do not keep sending notifications with stale settings such as `notifySystemMode=all` after the user changes the current device to `join-leave` or `off`.
- Reduced duplicate startup refresh work after stored-token verification. The client now verifies a saved token once, then loads pins, commands, DM rooms, group rooms, history, and SSE through the normal startup path only, avoiding duplicate initial API requests that could cause slower loading, brief UI flicker after SSE reconnect, or repeated DM/group/pinned-message re-rendering on mobile and lower-powered browsers.
- Added notification and mobile/background Web Push handling for Discord-to-web relay messages. Discord relays now use the same public-chat notification path as web/game chat, so the existing normal chat, mention, keyword, own-message, preview, and notification enable settings apply consistently.

- Restored the PIP unsupported/failure popup in mobile/standalone flows by showing a parent-page modal instead of relying only on native `alert()`, and added a child-frame fallback if the parent bridge does not answer.
- Fixed notification i18n regressions: removed the duplicated English keyword-help suffix and translated the Japanese Web Push error/failure strings.
- Updated JA/ZH README Web Push notes so the recent addon/standalone push behavior is documented in the target language instead of mixed English.


## 4.5.3

- When chat is hidden from logged-out users, expired or revoked sessions now immediately clear visible chat history, pinned messages, DM/group state, and chat-related modals.
- Connected browsers now receive an auth-expired SSE event when a session expires, is logged out from another tab, or is revoked from web/admin or `/bmchat revoke`.

- Added a dedicated server notification mode in user notification settings: all server notifications, join/leave only, or off.


Notification configuration migration

In 4.5.3, browser notification defaults and mobile/background Web Push notification defaults were consolidated into a single notifications: section.

Use the following block as the new default notification configuration:
```yml
# Browser/system notifications and mobile/background Web Push notification defaults.
# These server-side allow limits apply to both the browser web notification path
# and the mobile/background Web Push path. Users can still narrow these in Chat settings.
# Existing browser-notifications.* and web-push.notify-* keys are still read as
# legacy compatibility inputs when notifications.* is missing, but new default
# configs should use only this notifications.* block.
notifications:
  enabled: true
  # true = notify only when the tab/window is hidden, minimized, or not focused.
  only-when-hidden: true
  notify-normal-chat: true
  notify-dm: true
  notify-group-chat: true
  notify-mentions: true
  notify-replies: true
  # true = allow users to receive server notifications.
  # Users can still choose all, join/leave only, or off in Chat settings.
  notify-system: true
  notify-keywords: true
  notify-own-messages: true
  show-message-preview: true
```
The old configuration keys below are still read only for migration and backward compatibility:

browser-notifications.*
web-push.enabled
web-push.notify-*
web-push.show-message-preview

For new installations and updated configs, move notification category preferences into notifications:. The web-push: section should now be used only for Web Push transport settings such as VAPID keys, subject, notification title, subscription file, and TTL.

Existing configs do not have to be rewritten immediately. However, if both the new notifications.* values and old legacy values are present, the new notifications.* values should be treated as the intended configuration. Server owners who want a clean config can replace the old browser notification and Web Push notification preference blocks with the new notifications: block above, then keep only the Web Push transport settings under web-push:.


## 4.5.2

- Fixed BlueMap web-addon mobile Web Push registration by moving Service Worker registration/subscription work to the parent BlueMap page when the embedded chat runs inside the script-written addon iframe. Standalone chat pages still register directly.
- Removed the unnecessary standalone-page requirement for Web Push on supported browsers. iOS/iPadOS still requires launching the site from the Home Screen PWA before Web Push can be enabled.
- Fixed Web Push notification click URLs so subscriptions keep the exact page URL where push was enabled, including BlueMap web-addon pages and standalone chat pages.
- Added `upload.max-total-size-mb` to cap total files stored directly in `upload.directory`; `0` keeps the previous unlimited behavior.
- When the upload quota is enabled and a new upload would exceed it, the server deletes the oldest unreferenced uploads first. Files still referenced by chat history, SQLite history, DM/group messages, or preserved pinned messages are not removed.
- If quota cleanup cannot free enough space, the upload is rejected with `upload_storage_quota_exceeded` instead of writing beyond the configured limit.

Upgrade notes:
- Existing Web Push subscriptions should be turned off and on again from the intended page so the corrected `openUrl` is saved.
- Set `upload.max-total-size-mb` manually to enable upload-folder quota enforcement; the default is `0` for compatibility.


4.5.1
Fixed Web Push notification click navigation so subscriptions remember the actual page URL where push was enabled, including BlueMap web-addon pages and standalone chat pages.
Fixed notification click handling so public chat, reply, keyword, DM, and group notifications can reopen/focus an existing chat page and jump to the target message, thread, or room when available.
Fixed mobile Web Push support in BlueMap web-addon mode by registering the Service Worker from the parent BlueMap page instead of the embedded addon iframe.
Removed the unnecessary standalone-page requirement for Web Push on supported browsers. iOS/iPadOS still requires the site to be added to the Home Screen as a PWA before Web Push can be enabled.
Added parent-page forwarding for Service Worker notification navigation so notification clicks can reach the embedded chat iframe on BlueMap web-addon pages.
Fixed minimized chat pill controls so the minimized area only shows the title and restore button. DM, group, notification inbox, login, PIP, and other controls remain available only in the expanded chat window.
Added emoji folder moving in the admin emoji manager, including single/multi-select moves, conflict checks, PNG sidecar movement, path validation, and audit logging.
Added reply notifications for public replies to the current user's messages.
Added a browser-local notification inbox so recent notification-worthy events can be reviewed from the same browser.
Improved notification language handling so Web Push subscriptions remember browser language and notification text uses language resources more consistently.
Improved chat settings layout so saved-setting controls and notification-related labels are not clipped in the settings modal.

Upgrade notes:

Existing Web Push subscriptions should be turned off and on again so the corrected open URL and language values are saved.
BlueMap web-addon mobile push requires HTTPS and a browser that supports Service Workers, Push API, and Notifications.
iOS/iPadOS Web Push requires launching the site from the Home Screen PWA.


## 4.5.0

4.5.0 jumps directly from the 4.3.x line because the accumulated changes are larger than a normal patch release. The summary below focuses on the major changes from 4.3.0 to 4.5.0.

### Group chat
- Added optional web group-chat rooms on top of the 4.3.0 direct-message system.
- Supports public/private rooms, optional room passwords, invitations, unread badges, hidden-room restore, member management, and room management actions.
- Group chat uses its own SQLite-backed storage and retention settings, and remains disabled by default because it stores multi-user private messages.

### Metadata-only private chat administration
- Added `private-chat-super-admins` for metadata-only DM/group review.
- Super admins can review room/thread titles, participants, message counts, approximate storage size, retention status, locks, exclusion flags, cleanup previews, and deletion controls without viewing message bodies.
- Added session locks, auto-delete exclusions, retention-remaining displays, cleanup previews, and full-session deletion for DM/group sessions.

### Notifications and mobile Web Push
- Added browser/system notifications for open web chat pages, with per-browser user preferences and server-side allow limits.
- Added background/mobile Web Push using Service Worker subscriptions, VAPID keys, stored subscriptions, notification type filters, and a Web Push test action.
- Documented HTTPS/PWA requirements, iOS/iPadOS limitations, VAPID `subject` guidance, and the fact that browser/OS spam warnings cannot be disabled by the plugin.

### Theme, layout, and chat settings
- Reworked public chat, DM, group chat, pinned-message, and admin metadata rows for more consistent wrapping, clipping, and `·` separator behavior.
- Scoped chat font, text color, UI-meta color, shadow, message background, and input background settings to real chat-content areas only, instead of applying them to settings and management UI.
- Grouped chat settings into language/theme, window, font, and notification areas; optional sections are collapsed by default and the collapsed/open state is saved locally.
- The saved-chat-settings action area stays visible instead of becoming another collapsible section.
- Improved light/dark/system/high-contrast control visibility, color swatch borders, settings scrollbar placement/style, detached modal styling, standalone viewport sizing, toasts, minimized mode, and DM/group hide button alignment.
- Added browser-local setting presets for visual chat settings, window size/position, resize lock, minimized state, language, notification toggles, and related browser-local preferences.

### Standalone, HTTPS, and resource loading
- Fixed standalone `chat.js` bootstrap so the embedded inner application is available and does not fail with `BMWC_EMBEDDED_INNER_TEXT is not defined`.
- Fixed standalone manifest URL generation so `/bmwc/chat` serves its manifest from the standalone route.
- Improved API base handling for direct HTTP, same-domain HTTPS reverse proxy, standalone pages, uploads, and emoji URLs.
- Refreshed Caddy/Nginx and configuration documentation around same-domain `/bmwc/api` and `/bmwc/chat` deployments.

### Retention, cleanup, upload safety, and audit logs
- Improved DM/group retention cleanup so decisions are based on surviving message timestamps and respect locked or auto-delete-excluded sessions.
- Improved upload cleanup so files still referenced by public chat history, pinned messages, DM threads, or group rooms are protected before deletion.
- Added optional append-only daily audit logs under `audit/YYYY-MM-DD.log` for management-impacting actions such as command execution, administrative `/bmchat` commands, session flag changes, forced DM/group deletion, message deletion, pin management, emoji management, and history clearing.

### Configuration, language files, and upgrade notes
- Added the `group-chat`, `private-chat-super-admins`, `audit`, `browser-notifications`, and `web-push` configuration blocks.
- Changed the default `discordsrv.game-to-discord-format` to `{sender}: {message}`.
- Browser notification and Web Push `notify-*` values are server-side allow limits; users can still choose their own browser-local notification preferences within those limits.
- Added or refreshed UI language strings for group chat, metadata-only administration, retention status, hidden-room actions, admin confirmations, notification controls, and Web Push status/test messages in `en-US`, `ko-KR`, `ja-JP`, and `zh-CN`.
- Existing `config.yml` files are not rewritten automatically. Merge the new blocks from the default config or regenerate the config after backing up your current settings.
- If group chat is enabled, include `group-messages.db`, `group-messages.db-wal`, and `group-messages.db-shm` in backup plans.
- Message bodies remain unavailable from the super-admin metadata view by design.

### 4.5.0 - documentation and upgrade guidance

- Added an upgrade recommendation for 4.5.0: because many options changed between 4.3.x and 4.5.0, it is usually safer to back up the old `config.yml`, regenerate a fresh one, and then copy custom settings back manually.
- Clarified that newly generated configs start with `enabled: false`, so regenerating `config.yml` does not start web/chat services, cleanup tasks, or private-message/group-chat storage until the administrator reviews settings and sets `enabled: true`. Existing history databases, uploads, emojis, audit logs, language files, and VAPID key files are not deleted by config regeneration.

Example migration flow:

```bash
# Stop the Minecraft server first.
cd plugins/BlueMapWebChat
cp config.yml config.yml.4.3-backup
# Optional: keep a full plugin-data backup too.
# cp -a . ../BlueMapWebChat-backup-4.3
rm config.yml
# Start the server once. The new config.yml is generated with enabled: false.
# Review and merge your custom values, then set enabled: true and restart or /bmchat reload.
```

Key 4.5.0 config blocks to review or merge:

```yaml
enabled: false

private-chat-super-admins: []

audit:
  enabled: true
  directory: "audit"

standalone-web:
  enabled: false
  path: "/chat"
  app-name: "Web Chat"
  app-short-name: "Web Chat"
  api-base-url: ""

direct-message:
  enabled: false
  storage: "auto"
  retention-days: 0
  max-messages-per-thread: 0
  max-message-length: 500
  allow-web-send: true
  allow-game-send: true
  notify-on-login: true
  notify-on-message: true
  web-unread-badge: true
  confirm-hide: true
  jsonl-file: "direct-messages.jsonl"
  sqlite-file: "direct-messages.db"

group-chat:
  enabled: false
  allow-web-send: true
  allow-public-rooms: true
  allow-room-passwords: true
  confirm-leave: true
  confirm-hide: true
  retention-days: 30
  max-messages-per-room: 1000
  max-message-length: 500
  max-rooms-per-user: 20
  max-members-per-room: 50
  max-room-name-length: 32
  invite-expire-hours: 72
  sqlite-file: "group-messages.db"

browser-notifications:
  enabled: true
  only-when-hidden: true
  notify-normal-chat: true
  notify-dm: true
  notify-group-chat: true
  notify-mentions: true
  notify-system: true
  notify-keywords: true
  notify-own-messages: true
  show-message-preview: true

web-push:
  enabled: true
  vapid-public-key: ""
  vapid-private-key: ""
  subject: "mailto:admin@example.com"
  notification-title: ""
  subscriptions-file: "web-push-subscriptions.jsonl"
  ttl-seconds: 300
  notify-normal-chat: true
  notify-dm: true
  notify-group-chat: true
  notify-mentions: true
  notify-system: true
  notify-keywords: true
  notify-own-messages: true
  show-message-preview: true

discordsrv:
  append-web-emoji-links: true
  append-game-emoji-links: true
```


## 4.3.0

### Startup safety

- Added a top-level master switch for newly generated configs. New `config.yml` files start disabled so BlueMapWebChat only creates configuration files until the administrator reviews settings and opts in.
- Existing configs without the `enabled` key are treated as enabled for upgrade compatibility.

```yml
# Master switch for BlueMapWebChat.
# New generated configs default to false so the plugin creates config.yml first
# without starting web/chat services or cleanup tasks. /bmchat reload remains available.
# Existing configs that do not have this key are treated as enabled for upgrade compatibility.
enabled: false
```

When `enabled: false`, the plugin does not start the HTTP server, web addon installer, chat listeners, Discord bridge, upload/emoji directory initialization, direct-message store, or cleanup tasks. Set `enabled: true` after reviewing retention/storage/exposure settings.

### Thread-style direct messages

- Changed the in-game DM sent confirmation to use the new `command.dmSentEcho` key so existing language files still show the sent body (`to: {player} {message}` / `보냄: {player} {message}`).
- Added optional 1:1 direct message threads for linked/known Minecraft players.
- Direct message targets are limited to players with a stored UUID/name, usually from joining the server at least once or linking a web account.
- Messages are stored by UUID and displayed as `display name (real account name)` where both values are available.
- A->B and B->A messages always use the same thread by using a deterministic pair of UUIDs.
- Added a web message-box button with unread badge, player search, thread list, conversation view, and reply-style message sending inside each 1:1 thread.
- Added `/bmchat dm <player> <message>` and `/bmchat dm list` for game-side direct message sending and unread/thread summary checks.
- Added per-user unread tracking, web SSE direct-message refresh events, join-time unread notices, and online recipient notices.
- Direct-message storage uses an independent private-message store from public chat history, so public chat retention and private-message retention can be managed independently.
- Added `direct-message.storage`, `direct-message.jsonl-file`, and `direct-message.sqlite-file`. With `storage: "auto"`, DM storage follows `chat.history-storage: "jsonl"`; otherwise it uses SQLite.
- Added `direct-message.confirm-hide` to control whether the web message box asks for confirmation before hiding a DM message.
- Shows `direct-message.retention-days` next to the DM message-box title. `0` is displayed as no time limit.
- Added shared `:emoji` + Tab autocomplete for both public chat and direct-message input fields.
- Direct-message sender/time meta now uses the same display-name/time toggle behavior as public chat and removes the extra dot separator.

New direct message configuration:

```yml
direct-message:
  # Thread-style 1:1 direct messages.
  # Only players that have joined or linked at least once and have a stored UUID/name can be selected.
  # Messages are stored by UUID, while the UI shows display name (real account name).
  # This feature uses its own private message store and is disabled by default because it stores private messages.
  enabled: false

  # auto = follow chat.history-storage when it is jsonl, otherwise use sqlite.
  # sqlite = recommended database storage.
  # jsonl = append-only JSONL file storage, useful when chat history also uses JSONL.
  storage: "auto"

  # 0 = no time limit. A positive value is shown next to the DM window title and
  # physically removes old DM messages after that many days.
  retention-days: 0

  # 0 = unlimited by count. When set, only the newest N messages are kept per 1:1 thread.
  max-messages-per-thread: 0

  # 0 = unlimited. Recommended: 300-1000.
  max-message-length: 500

  # Allow sending DMs from the web UI.
  allow-web-send: true
  # Allow sending DMs from /bmchat dm in game.
  allow-game-send: true

  # Notify players about unread DMs when they join.
  notify-on-login: true
  # Notify online players immediately when a new DM arrives.
  notify-on-message: true
  # Show unread DM count badge in the web UI.
  web-unread-badge: true

  # Ask before hiding a DM message from the web message box.
  confirm-hide: true

  # JSONL file for private 1:1 message threads when storage is jsonl. Relative paths are stored under the plugin data folder.
  jsonl-file: "direct-messages.jsonl"
  # SQLite file for private 1:1 message threads when storage is sqlite. Relative paths are stored under the plugin data folder.
  sqlite-file: "direct-messages.db"
```

Upgrade note:

- Existing `config.yml` files are not rewritten automatically. To use direct messages after upgrading, merge the `direct-message` block above into your existing config.
- Because 4.2.0 and 4.3.0 include large configuration changes, if manual merging is difficult, stop the server, back up and delete `plugins/BlueMapWebChat/config.yml`, start the server once to regenerate it, and then reapply your custom settings manually.
- New generated configs now start with top-level `enabled: false`. This prevents web/chat services and cleanup tasks from running until the administrator reviews the generated config and sets `enabled: true`.
- If `direct-message.storage: "auto"` and `chat.history-storage: "jsonl"`, DMs are stored in `direct-messages.jsonl`. Otherwise `auto` uses SQLite.
- If direct messages are enabled with SQLite storage, include `direct-messages.db`, `direct-messages.db-wal`, and `direct-messages.db-shm` in backup plans.
- If direct messages are enabled with JSONL storage, include `direct-messages.jsonl` in backup plans.

## 4.2.0

### SQLite history storage

- Added SQLite chat history storage and made `chat.history-storage: "sqlite"` the recommended/default backend for new configurations.
- Kept `jsonl` and `memory` history-storage modes for compatibility.
- Unified history retention settings so `chat.history-size` and `chat.history-retention-days` are used by `memory`, `jsonl`, and `sqlite`.
- `chat.history-retention-days` controls age-based cleanup for all history backends. Review this value before setting top-level `enabled: true` on a newly generated config.
- Removed the old `chat.history-persist` and `chat.history-persist-retention-days` settings. Persistence is now selected only by `chat.history-storage`.
- Added `chat.history-sqlite-file` to control the SQLite DB file path. The default is `history.db` in the plugin data folder.
- Added `chat.history-sqlite-migrate-jsonl`; when enabled, an empty SQLite DB imports the existing legacy `chat.history-file` JSONL log once.
- Moved history paging, newer/older history loading, reply-jump lookup, deletion, pinned-message target lookup, retention cleanup, and search lookup to SQLite-backed queries when SQLite storage is enabled.
- Excluded SQLite messages marked as hidden from normal history, around-message lookup, and search results.
- Made SQLite writes asynchronous so web messages are broadcast to connected web clients immediately without waiting for DB insert/prune work.
- Reduced normal-send pruning overhead while keeping retention cleanup active in the background.

Changed history configuration:

```yml
chat:
  # History storage backend.
  # sqlite = recommended persistent storage for long-lived chat history and search.
  # jsonl = legacy single-file persistence using history-file.
  # memory = keep only in-memory history for the current server session.
  history-storage: "sqlite"
  # Shared by memory/jsonl/sqlite. 0 = unlimited by count.
  history-size: 0
  # Shared by memory/jsonl/sqlite. 0 = unlimited by age.
  # Review this value before setting top-level enabled: true on newly generated configs.
  history-retention-days: 5
  # JSONL history file. Relative paths are stored under the plugin data folder.
  history-file: "history.jsonl"
  # SQLite DB file. Relative paths are stored under the plugin data folder.
  history-sqlite-file: "history.db"
  # Import history-file into SQLite once when the DB is empty.
  history-sqlite-migrate-jsonl: true
```

Removed history configuration:

```yml
chat:
  history-persist: false
  history-persist-retention-days: 5
```

### Message search

- Added the `/history/search` API for searching message text, sender names, and stored display names, with optional date/time, sender, source, and system/event filters.
- Added the in-chat message search UI with an options section for date/time ranges, sender filtering, source filtering, and system/event inclusion, plus result-click jump using the existing history-around navigation.
- Moved the search button out of the crowded message input row and into the floating chat-panel area.
- Enlarged the search modal and added a scrollable results pane for easier review of long result sets.
- Applied chat font and theme variables to the search modal, inputs, buttons, status text, and result items so light/dark chat themes remain readable.
- Isolated search modal keyboard, wheel, touch, and pointer events from the BlueMap map while preserving search, close, and result-click handling.
- Localized i18n-backed system/event messages in search results and included the requested web UI language in search matching, so translated system messages can be found by their displayed text.
- Added `search.enabled` and `search.result-limit` settings for disabling search and controlling the search result count.
- `search.result-limit` is the only search result count limit. There is no separate search-result maximum setting or internal hard-coded maximum. Very large values are accepted but may be expensive.

New search configuration:

```yml
search:
  # Enable the web message search button and /history/search API.
  enabled: true
  # Number of search results returned by the web UI and /history/search API.
  # This is the only search result count limit. There is no separate internal maximum.
  # Setting this to 2000 returns up to 2000 results; setting it to 10 returns up to 10.
  # Very large values such as 10000 or 100000 are accepted, but can make searches slow,
  # increase response size, and add significant CPU, memory, and database load.
  # Recommended: 50-200 for normal use. Raise only when you need large admin searches.
  result-limit: 50
```

### Upgrade notes

- In-game group-chat sending now preserves original `:emoji:` / `:pack/name:` message tokens without failing the direct-player-input safety check.
- Existing `config.yml` files are not rewritten automatically. To use SQLite/search after upgrading from 4.1.1, add or merge the actual configuration blocks shown above.
- Because 4.2.0 changes history and search configuration significantly, the safest upgrade path is to stop the server, back up the existing `config.yml`, delete `config.yml`, start the server once to regenerate it, and then reapply your custom settings manually.
- Back up `history.jsonl`, `history.db`, `history.db-wal`, and `history.db-shm` before testing migration, changing storage backends, or editing the database manually.

## 4.1.1

### Pinned messages and player display

- Fixed collapsed pinned-message summaries so raw Minecraft color codes such as `&7`, `§7`, and `&#RRGGBB` do not leak into the compact pinned bar or tooltip.
- Ensured `player-display.mode` and `player-display.strip-colors` are applied consistently to Minecraft-origin chat, web-authenticated player names, stored display-name fallbacks, pinned messages, and web rendering.
- Stripped raw Minecraft color codes from Discord output even when sender-name colors are preserved for the web UI.

### Discord relay

- Improved game-side custom emoji URL appending for Discord, including augmentation of DiscordSRV native Minecraft-to-Discord relay messages when possible.
- Removed the hard-coded reply arrow from Discord reply preview lines; the reply marker now applies only to the actual replied message line.

### Reply relay and game chat output

- Added separate game-side reply preview and reply prefix settings.
- Ensured `reply.game-preview.format` and `reply.game-prefix.text` translate Minecraft legacy `&` color codes before broadcasting to game chat.
- Changed mixed token/URL game-chat fallback to keep a single plain Minecraft chat line instead of repeating URL reference lines.
- Fixed image-only upload URLs in Minecraft chat so normal sender labels before `https://` are no longer mistaken for custom emoji tokens, preserving clickable URL output.

### History loading and edge notices

- Added scrollbar-drag and touch-scroll handling for the bottom-edge no-more-history notice.

## 4.1.0

### Pinned message ordering

- Added manual pinned-message ordering with up/down controls in the pinned-message management UI.
- Added the `/admin/move-pin` API and persistent `sortOrder` storage while keeping backward compatibility with existing pinned messages.

### Custom emoji relay compatibility

- Preserved custom emoji token text by default for game-side emoji plugins such as ImageEmojis.
- Kept `link` and `label` game-link modes for servers that do not use a game-side emoji plugin, and added an explicit `preserve` mode for token-preserving behavior.
- Removed ImageEmojis-specific glyph/resource-pack conversion paths from BM Web Chat; token text is now the canonical relay format.
- Improved game-to-web original-message capture with optional Paper/Purpur `AsyncChatEvent` support and Bukkit/Spigot fallback handling.

### Custom emoji sidecars and WebP support

- Generalized PNG sidecar generation for GIF/JPG/JPEG/WEBP custom emoji files whenever custom emoji support is enabled.
- Added WebP ImageIO support through TwelveMonkeys ImageIO so WebP custom emoji files can generate PNG sidecars reliably.
- Kept the web UI rendering the original uploaded emoji file so animated GIFs remain animated in the browser.

### Discord relay

- Kept optional web-to-Discord custom emoji image URL appending for BM Web Chat emoji tokens, disabled by default.
- Added optional game-to-Discord relay with game-side custom emoji URL appending, disabled by default to avoid duplicate DiscordSRV Minecraft chat relay.
- Fixed Discord reply relay so `discordsrv.reply-relay.enabled`, `prefix-enabled`, and `preview-enabled` are respected independently from game-side reply settings.
- Kept Discord-to-web image attachment relay and Minecraft legacy color-code stripping for Discord output.

### History loading and edge notices

- Improved top and bottom edge history loading retries when older or newer messages are still available.
- Added a bottom-edge “No more messages to display” toast after repeated extra-scroll attempts at the latest loaded message while keeping newer-history retry requests immediate.
- Kept the existing top-edge no-more-history notice for the oldest loaded history edge.

## 4.0.1

### Discord relay fixes

- Fixed Discord -> web image attachment relay by appending Discord image attachment URLs to the web message so the existing media preview system can render them.
- Stripped Minecraft legacy formatting codes from BM Web Chat's web -> Discord output, including standard codes such as `§a`, `§l`, `§r` and RGB sequences such as `§x§8§a§b§4§f§e`.
- Added optional Discord-side image preview links for BM Web Chat custom emoji tokens such as `:pack/name:`.
- Added `discordsrv.append-web-emoji-links` and `discordsrv.max-emoji-links-per-message`.

### Reply relay fixes

- Changed game-side reply labeling to render the normal web -> game relay line first and then replace the source label with the configured reply label.
- Changed reply relay output from `[Web] Player: message` to `↪ [Reply] Player: message` by default.
- Applied the same reply preview and reply label behavior to web -> Discord relay output.

### Release cleanup

- Bumped the project version to `4.0.1`.
- Refreshed default configuration comments, README files, configuration references, and i18n documentation for the 4.0.1 release.
- Documented the Discord attachment, color-code cleanup, and custom emoji link behavior.

## 4.0.0

### Replies, history, and scrolling

- Fixed Paper game-to-web chat capture so web relay uses the player's original typed message, preserving `:pack/name:` emoji tokens before any game-side emoji plugin renders them in game.
- Added web chat replies with referenced-message previews.
- Added jump-to-replied-message behavior, including loading older history around the target message when needed.
- Improved virtual scrolling around media-heavy histories and reply jumps.
- Added a safe media-preview wrapper so preview parser errors cannot break message rendering or virtual scrolling.

### Custom emoji and game-link cleanup

- Changed `emoji.game-link.plain-broadcast-with-urls` to split mixed emoji/URL messages: the original line stays plain for game-side emoji rendering, and each URL is repeated on a separate clickable reference line.

- Simplified web-to-game custom emoji relay to two modes: `link` and `label`.
- Removed the misleading ImageEmojis-specific game-link subsection from the default config and documentation.
- Moved `plain-broadcast-with-urls`, `default-pack`, and `aliases` directly under `emoji.game-link`.
- Clarified that BM Web Chat sends token text only; game-side emoji rendering is handled independently by external plugins that know the same token names.
- Fixed missing Java helper definitions after the resource-pack cleanup so game-link labels and game-side reply previews compile again.
- Fixed URL-before-custom-emoji rendering so `https://example.com :pack/name:` is linkified and the emoji token still renders; the frontend now avoids treating URL scheme colons as emoji token starts.
- Added the admin custom emoji manager for creating, uploading, renaming, and deleting emoji packs/files.
- Added emoji storage usage/limit display and upload warnings when the total emoji size limit is exceeded.
- Added same-folder PNG sidecars for GIF/JPG/JPEG/WEBP custom emoji files for compatibility with game-side emoji plugins that only read PNG files.
- Kept the web UI on the original uploaded emoji file so animated GIFs remain animated in the browser.
- Hid PNG sidecars from the web emoji catalog when a same-base non-PNG original exists.
- Improved emoji picker resizing, scrollbar spacing, and wheel-row snapping.

### Media previews

- Fixed YouTube embed Error 153 by using `strict-origin-when-cross-origin` referrer policy and an embed `origin` parameter.
- Added YouTube Shorts handling through the normal YouTube preview path, with a vertical player and loop parameters.
- Added optional TikTok and X/Twitter social embeds behind disabled-by-default provider switches.
- TikTok now uses the official `player/v1` iframe with `description=0` and `music_info=0` to avoid variable-height caption/music sections creating inner scrollbars.
- Added an external "Open on TikTok" link for full TikTok post details.
- Kept social embeds click-to-load by default so third-party content loads only after the user opens a preview.

### UI, settings, and localization

- Added UI text color, input background, theme, and text-shadow options.
- Applied font settings to collapsed pinned-message areas.
- Added latest-message and no-more-history notices.
- Added localized media/social preview labels for English, Korean, Japanese, and Simplified Chinese.

### Reliability and cleanup

- Improved SSE reconnect and post-reconnect config/history resynchronization.
- Guarded repeated Enter sends immediately after reconnect.
- Reduced stale web-asset problems by keeping generated asset version tokens.
- Removed project-specific examples from default configuration and documentation.
- Aligned bundled config, README, docs, and proxy override examples so `web-addon` appears before `standalone-web`.
- Removed game-to-web glyph reverse-conversion paths; game-to-web relay now depends on original chat text capture instead of glyph lookup.
- Simplified PNG sidecar behavior so it is a general compatibility helper for game-side emoji plugins.



## 3.2.x

### Reply and history navigation

* Added web chat reply support: hover a message to reply, send with a referenced-message preview, and click the preview to jump to the original message.
* Added `/history/around` lookup so replies to older messages can load and render the target message neighborhood directly.
* Fixed reply fallback compilation and hover-only reply button visibility after virtual-scroll action synchronization.

### Admin emoji manager improvements

* Added admin custom emoji rename support for emoji folders and individual emoji files while preserving file extensions.
* Added emoji storage usage/limit display options and localized warnings when uploads exceed the configured total emoji storage limit.
* Improved admin emoji upload error handling so HTTP 413 JSON responses are shown as user-visible alerts.
* Refined the admin emoji manager layout with clearer folder selection, emoji counts, selected-file display, select-all/delete-selected actions, and better button spacing.
* Simplified emoji item cards by removing secondary path lines, centering display names, and preventing long names from squeezing action buttons.

### URL and reverse-proxy behavior

* Reworked public URL resolution for direct HTTP, BlueMap addon mode, standalone mode, uploads, and custom emoji files.
* Made `web-addon.api-base-url` the primary public API base for reverse-proxy deployments.
* Made empty `standalone-web.api-base-url`, `upload.public-base-url`, and `emoji.public-base-url` consistently follow the active public API base.
* Kept compatibility with explicit legacy values such as `/bmwc/api`, `/bmwc/api/uploads`, and `/bmwc/api/emojis`.
* Documented absolute paths, full resource paths, relative shorthand values resolved through `http.cors-origin`, and full `https://...` URLs consistently.
* Reordered the default config so `web-addon` appears before `standalone-web`, making the reverse-proxy API base setting easier to find.

### Theme and user settings

* Improved light/system-light theme behavior so panel, modal, button, input, emoji/admin surfaces, and surrounding areasalone-web`, making the reverse-proxy API base setting easier to find.
* Improved light use the same user-selected background and text-color variables as dark theme.
* Changed light-theme panel, modal, button, and emoji resize-handle backgrounds from gradients to solid colors so custom colors render cleanly.
* Allowed decimal pixel values for user settings where fractional CSS pixels are safe, including font size and custom text-shadow X/Y/blur controls.

### Documentation and localization

* Updated `config.yml` comments, README files, HTTPS proxy guides, standalone notes, upload/security notes, and proxy example overrides to match the unified URL behavior.
* Updated bundled language files and documentation variants for the new admin emoji, URL, reply, and settings behavior.
* Verified bundled translation YAML files for syntax and key consistency.



## 3.1.x

### Custom emoji system

* Added server-managed custom emoji support.
* Added an admin emoji manager for creating emoji packs, uploading emoji files, renaming, deleting, and refreshing emoji lists from the web UI.
* Added support for PNG, JPG, JPEG, GIF, and WebP custom emoji uploads.
* Preserved Unicode emoji filenames where possible, allowing Korean, Unicode, and spaced emoji names to resolve correctly.
* Added custom emoji pack support through subfolders under the emoji directory.
* Added configurable emoji render size, picker size, per-file size limit, total storage limit, and per-message emoji token limit.
* Increased custom emoji size clamps up to 1024px for servers that want oversized emoji previews.
* Improved emoji sorting, empty-pack display, tooltips, and admin emoji list usability for large emoji collections.

### Emoji picker improvements

* Added an emoji picker button next to the attachment button.
* Added a draggable emoji picker resize handle between the message list and input area.
* Remembered emoji picker height per browser.
* Improved emoji picker minimum-height calculation so one full emoji row remains visible at small sizes.
* Reworked the emoji picker layout so pack tabs stay separate from the scrollable emoji grid.
* Added row-aligned mouse-wheel scrolling for cleaner emoji navigation.
* Improved resize and scroll behavior to reduce jitter and avoid partial-row clipping.

### Upload, preview, and URL handling

* Unified upload and emoji public URLs with the active web chat API base when public override values are left empty.
* Added `emoji.public-base-url` as a compatibility override for reverse proxy deployments.
* Clarified HTTP, HTTPS, reverse proxy, standalone, upload, and emoji URL defaults in configuration comments and documentation.
* Added Discord CDN media cache support for expiring external media URLs.
* Improved image, video, audio, and custom emoji preview handling.

### Standalone and reverse proxy support

* Fixed standalone mode API base resolution when opened through a reverse proxy path.
* Preserved standalone runtime mode inside the embedded chat iframe.
* Added standalone diagnostics to generated page configuration.
* Improved behavior when BlueMapWebChat is served from non-root API paths.

### Minimized window behavior

* Improved minimized-window anchoring so the chat collapses and restores from the nearest bottom edge based on its current position.
* Made windows near the right edge restore from the bottom-right, and windows near the left edge restore from the bottom-left.
* Cleaned up the minimized header display in light theme.

### Chat scrolling and history resilience

* Improved latest-message auto-follow so mid-history views are no longer pulled down unexpectedly by incoming messages, media layout changes, resume refreshes, or history refreshes.
* Added a latest-message jump path that bypasses stale viewport protection.
* Improved virtual scrolling, scroll anchoring, spacer recalculation, and idle viewport maintenance to reduce small viewport jitter.
* Added delayed-loading and failure notices for older message history when the network is slow, offline, or timed out.
* Added request timeouts for older-history loading so a failed request does not permanently block future history loads.
* Added better handling for large emoji lists and long chat histories.

### Web command and UI refinements

* Improved the server command modal layout so the command input and Run button align cleanly.
* Fixed web command execution notices so the executor display name is shown correctly even with older language files.
* Updated bundled translation files for YAML validity and key alignment.



## 3.1.0

### UI and theme settings

- Added a theme selector to Chat settings.
- Improved contrast for buttons, surfaces, and input fields in the light theme.
- Added a separate UI text color setting for labels such as role badges, source badges, timestamps, controls, and placeholders.
- Added text-shadow presets: `none`, `auto`, `dark`, `light`, and `custom`.
- Replaced raw custom text-shadow input with color picker and slider controls.
- Applied font settings to collapsed and expanded pinned-message areas.

### Navigation and history UI

- Added a jump-to-latest-message button when viewing older chat history.
- Added a "No more messages to display" notice at the top of history when there are no older messages left.
- Made the history-end notice and jump-to-latest button follow the active theme colors instead of user-overridden UI text colors.

### Message deletion and moderation actions

- Prevented duplicate delete confirmation handling.
- Refreshed deleted messages immediately after deletion.
- Improved scroll anchoring so deleting a message is less likely to pull the view down to the latest messages.

### Reconnection and sending

- Added automatic SSE reconnection after a server restart.
- Resynchronized config and the latest history page after reconnection.
- Prevented duplicate sends from repeated Enter presses immediately after reconnection.

### Media previews and link insertion

- Changed the default media preview max height to `720px`.
- Documented that unlimited or excessively large media preview heights can cause visible scroll jumps with virtual scrolling.
- Capped image, video, and YouTube previews so they do not exceed the current chat viewport even when the configured max height is very large or unlimited.
- Added trailing spaces when inserting multiple media links so preview detection can parse each link separately.

### HTTPS proxy documentation

- Expanded the Caddy HTTPS setup guide.
- Expanded the nginx + Certbot HTTPS setup guide.
- Added installation examples under `examples/caddy` and `examples/nginx`.

