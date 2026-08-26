# KOKOTO WebChat configuration reference

## 5.0.0 content filter, live Admin settings, sessions, and upload filenames

`content-filter` is a shared loader-neutral filter for Minecraft/web public chat, group chat, and optionally DM. Bulk filter words live in UTF-8 `plugins/KOKOTO-WebChat/filter-lists/*.txt` files, one word per line; blank lines and `#` comments are ignored. Each list defaults to `block`, and Web Admin can switch that list to Filter mode, which masks matches using `content-filter.mask.text`. Use `config.yml` `content-filter.rules` for custom block/mask/replace rules or smaller special cases. For `replace`, `first` always uses the first replacement candidate while `random` chooses one candidate at random; a per-word mapping takes priority. Unicode normalization plus compact/interleave matching catches spacing, punctuation, compatibility forms, and bounded inserted characters such as `욕1설`. Registered KWC emoji tokens are protected from block/filter matching; unregistered token-shaped text such as `:fake:` remains ordinary filterable text.


### Custom filter quick guide

Use UTF-8 **Filter word list** TXT files for large sets of simple words. Put one word on each line and choose **Block** or **Filter (mask)** for the list. Use **Custom rules** when you need `replace`, several replacement candidates, or a different replacement for each word.

- **Block** rejects the whole message when a selected match is found.
- **Mask** replaces only the matched span with `content-filter.mask.text` (default `***`).
- **Replace + First** always uses the first shared replacement candidate.
- **Replace + Random** chooses one shared candidate for each match.
- **Per-word replacements** such as `word1 => replacement A` override shared candidates. The left-hand mapping key automatically becomes a target word even if it is omitted from Target words.

Web Admin example:

```text
Rule ID: soften-words
Action: Replace
Target words:
word1
word2

Replacement candidates:
soft expression
another expression

Replacement mode: First

Per-word replacements:
word2 => specific expression
```

Here `word1` becomes the first shared candidate while `word2` uses its specific mapping.

Anti-evasion options work as follows: `compact-match` catches separators such as `word 1` or `word-1` when the compacted form matches; `interleave-match` catches inserted letters/numbers within `interleave-max-gap`; `interleave-unlimited-gap: true` removes that gap limit and can increase false positives. Hangul jamo-only rules such as `ㅅㅂ` remain shorthand-jamo rules, while complete Hangul words are compared as complete syllables, so a full-word rule does not treat `신발` as `시발` merely because of decomposed jamo.

After saving, use **Test** in the same Filter screen. It evaluates both TXT lists and custom rules without sending a message and works even when the live filter is disabled. The returned Rule, Word, and Match fields show whether the hit was `literal`, `compact`, or `interleave`.

Mapping keys are merged into the rule's target words automatically. When a custom rule and a TXT list match the same span, the custom rule has priority; a separate non-overlapping `block` hit elsewhere in the message still blocks the whole message.


Web Admin adds **Filter** and **Settings** tabs. Filter controls scopes, anti-evasion options, filter-word list files with per-list Block/Filter mode, custom rule CRUD, and no-send testing. Settings exposes only live-safe guest/CAPTCHA, authentication/session, user-profile, upload, and Discord administrator-alert values. `moderation.*` and relay/network/adapter topology remain config-file only. The corresponding game commands are `/kchat filter ...` and `/kchat settings ...`.

Changing `auth.remember-session-days` recalculates existing USER/MODERATOR sessions from their original `createdAt`; changing `admin.admin-session-expire-hours` independently recalculates ADMIN sessions. `0` means unlimited for both USER/MODERATOR and ADMIN session lifetime settings. Sessions already expired are never resurrected, and sessions older than a newly shortened lifetime expire immediately. The same policy is applied on startup/reload after editing `config.yml`.

`upload.filename-mode` is `random` by default. `original` preserves a sanitized Unicode original filename for new uploads, removes unsafe path/control/filesystem characters, and uses `-2`, `-3`, ... suffixes on collisions without overwriting existing files. Existing uploads are not renamed.


This document describes `plugins/KOKOTO-WebChat/config.yml`.

## Configuration version and migration fragment

`config-version` selects automatic migration behavior. The bundled `config.yml` is the **only migration template**. `config-reference-<plugin-version>.yml` is only an administrator-readable exact copy of that bundled default; migration never uses the generated reference file as its source.

If `config-version` is missing or belongs to another version, KWC reads the existing values, backs up the original `config.yml` first when the previous marker is not `*_auto_migration`, creates a fresh file from the running plugin's bundled default config, and overlays the existing values. Old comments, order, whitespace, and indentation are intentionally discarded; bundled comments/layout become authoritative while operator values remain authoritative. Retired settings are not copied back. The result is marked `<plugin-version>_auto_migration`. While that marker remains, startup/reload repeats the same bundled-default rebuild so newly added settings and current bundled comments/layout are picked up automatically. Exact `<plugin-version>` means the operator fixed the current-version config, so same-version startup/reload does not rewrite `config.yml`.

`config-migration-<plugin-version>.yml` is a review/diff report. Older generated `config-reference-*`, `config-migration-*`, and `config-upgrade-*` files are removed automatically; internal `config-baselines/*` resources remain because they are required to identify changed defaults across real version upgrades.
## Master switch

New generated configs start with top-level `enabled: false`. In this state, KOKOTO WebChat only creates/loads configuration and keeps only `/kchat reload` available; it does not start web/chat services, listeners, Discord integration, private-message storage, addon installation, upload/emoji initialization, or cleanup tasks. Existing configs without this key are treated as enabled for upgrade compatibility. Review storage, retention, upload, preview, authentication, and exposure settings, then set `enabled: true`.

## Update check

```yaml
update-check:
  enabled: true
```

When enabled, KOKOTO WebChat checks Modrinth for a newer stable release in the background on Bukkit, Fabric, NeoForge, and Forge. It checks the KWC `kokoto-webchat` project first and falls back to the legacy `bluemapwebchat` project during the listing transition. An OP or a player with `kwc.update.notify` also triggers a rate-limited refresh on login, so a newly published release is not dependent only on the periodic result. For 5.0.0, the CurseForge notice intentionally continues to use the existing BMWC bridge page until a replacement KWC listing is confirmed live. The check interval, release channel, and join delay remain built-in defaults. Update lookup failures never stop server startup and are logged as warnings.

## Deployment modes

### BlueMap addon

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true
```

On Bukkit, KWC installs assets under `addons/kokoto-web-chat` and patches BlueMap `webapp.conf`. On Fabric/NeoForge, and on Forge 26.1.2/26.2, with the BlueMap mod, KWC instead uses BlueMapAPI 2.8.0 to obtain the web root and register the JS/CSS; `auto-patch-webapp-conf` and the two BlueMap path overrides are ignored there.

### Standalone only

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

Open `http://<server-host>:8899/`.

### HTTPS reverse proxy

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
  # Recommended: keep empty so uploads follow the active API base.
  # Explicit override example: "/chat/api/uploads".
  public-base-url: ""

emoji:
  # Recommended: keep empty so emoji files follow the active API base.
  # Explicit override example: "/chat/api/emojis".
  public-base-url: ""
```

### Public URL option rules

- `http.path-prefix` is the plugin's internal HTTP API path. Normally leave the default `/api` unchanged.
- `adapters.bluemap.api-base-url` is the public API base used by the BlueMap embedded chat. In HTTPS reverse-proxy setups, this is usually `/chat/api`.
- `frontend.standalone.api-base-url` normally stays empty. Direct HTTP uses `http.path-prefix`; through the reverse proxy standalone uses `http.public-prefix + http.path-prefix` (`/chat/api` by default). It does not inherit the BlueMap adapter override.
- `upload.public-base-url` normally stays empty. When empty, uploaded files use the active API base plus `/uploads`, for example `/chat/api/uploads`.
- `emoji.public-base-url` normally stays empty. When empty, custom emoji files use the active API base plus `/emojis`, for example `/chat/api/emojis`.
- Explicit legacy values are accepted. `/chat/api` appends `/uploads` or `/emojis` automatically, while `/chat/api/uploads` and `/chat/api/emojis` are used as-is.
- Relative values without a leading `/`, such as `chat/api`, `chat/api/uploads`, or `chat/api/emojis`, are resolved against `http.cors-origin` when it is a real origin. If `cors-origin` is `*`, they fall back to same-origin absolute paths such as `/chat/api...`.
- Full URLs such as `https://map.example.com/chat/api` are used as-is.

### Upload storage quota

`upload.max-total-size-mb` limits the total size of regular files stored directly in `upload.directory`. The default `0` keeps upload storage unlimited. When a new upload would exceed the limit, KOKOTO WebChat deletes the oldest unreferenced uploads first; files still referenced by chat history, SQLite history, DM/group messages, or preserved pinned messages are kept. If cleanup cannot free enough space, the upload is rejected.

### Emoji storage display

`emoji.max-total-size-mb` limits total custom emoji storage. When the limit is exceeded, admin uploads show a localized warning instead of failing silently. `emoji.show-storage-usage` controls whether current emoji storage is shown in the admin emoji manager, and `emoji.show-storage-limit` controls whether the total limit is shown.

## Chat history storage

`chat.history-storage` controls the backend used for stored chat history. New configs use `sqlite`, which stores messages in `chat.history-sqlite-file` (`history.db` by default). SQLite is recommended for long-lived logs because before/after history pages, reply-jump lookup, deletion, and retention cleanup can use indexed queries.

Supported values:

- `sqlite`: persistent SQLite DB, recommended.
- `jsonl`: legacy single-file persistence using `chat.history-file`.
- `memory`: session-only in-memory history.

`chat.history-size` and `chat.history-retention-days` are shared by `memory`, `jsonl`, and `sqlite`. `0` means unlimited for each setting. New generated configs start with top-level `enabled: false`, so cleanup does not run until you review these values and set `enabled: true`. Use positive values such as `30` or `90` when your server policy requires automatic old-chat cleanup. Upload and external-media cache retention settings work the same way. `chat.history-file` is used only by JSONL; `chat.history-sqlite-file` is used only by SQLite. When `chat.history-sqlite-migrate-jsonl` is true, an empty SQLite DB imports `chat.history-file` once. Keep normal file backups of `history.db` before manual editing, large cleanup, or migration.


## Message tokens

`message-tokens.enabled` enables colon-delimited administrator-defined text/control aliases. Aliases are configured without surrounding colons; alias `enter` is typed as `:enter:`. The default aliases are English-only and may be replaced or extended in any language. Unknown aliases remain untouched, preserving custom/image emoji tokens.

YAML list settings accept both inline form (`aliases: [bullet, arrow]`) and block form (`aliases:` followed by `- bullet`). Use normal ASCII spaces for indentation; tabs and full-width spaces are invalid YAML indentation. `/kchat reload` validates the file before stopping live services, so an invalid edit is rejected and the previous running configuration and UI language remain active.

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

- `max-replacements-per-message: 0` means unlimited successful message-token replacements.
- `newline` inserts one line break.
- `blank-line` inserts two line-break characters, leaving one empty line.
- `tab.spaces` is clamped to 1–16 and inserts spaces rather than a literal tab control character.
- `custom` supports printable text replacements only; control characters/newlines are removed from custom replacement values.
- Backslash escape syntax such as `:\n:` is intentionally not interpreted.
- Minecraft keeps its existing single-line sanitization for ordinary CR/LF input. Line breaks created by configured `newline` / `blank-line` tokens are tracked separately and emitted as explicit chat lines only at final game delivery, so `:enter:` can create a new game chat line without making arbitrary pasted newlines bypass the existing flattening rule. A relayed receiving server must run the same 4.7.0 token-line delivery support; older receivers flatten the ordinary relayed LF.

Example custom aliases:

```yaml
message-tokens:
  newline:
    aliases: [enter, newline, nextline, linebreak, br, next]
  custom:
    separator:
      aliases: [separator, divider, line]
      replacement: "────────────"
```

## Message search

`/history/search` and the in-chat search modal are available for message text and sender searches when stored history is enabled. The search options section can filter by date/time range, sender, source, and system/event inclusion. The search button is in the floating chat-panel area so the input row stays compact, and search results use a scrollable list with the configured chat theme/font settings. Search results can jump to the matching message using the existing history-around navigation. i18n-backed system/event messages are searched and displayed in the requested web UI language when possible. Search can be disabled with `search.enabled`, and the single `search.result-limit` setting controls both the web UI result count and the `/history/search` API limit. There is no separate internal maximum: setting it to 2000 returns up to 2000 results, while setting it to 10 returns up to 10. Very large values such as 10000 or 100000 are accepted, but they can slow searches, increase response size, and add significant CPU, memory, and database load. The default is 50, and 50-200 is recommended for normal use. With `config-version: "5.0.0_auto_migration"`, missing search settings are inserted automatically on startup/reload. If same-version automatic migration has been disabled with exact `config-version: "5.0.0"`, add the missing keys manually or re-enable `_auto_migration`.


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

`group-chat.admin-audit.enabled` is an independent, default-off group-content access switch added in 4.6.3. It still requires the account to be listed in `private-chat-super-admins`. The administrator view is read-only, does not require room membership, does not join the room or update read state, and every page read is logged as `admin.group-audit-read` without copying message bodies into the audit log.

`direct-message.admin-audit.enabled` is a separate, default-off content-access switch. When enabled, only accounts also listed in `private-chat-super-admins` can open DM bodies in the read-only audit view. Each page read is audit-logged; message bodies are not copied into the audit log. Ordinary ADMIN/MODERATOR roles do not qualify automatically.

`capture-game-whispers` mirrors non-cancelled `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, and `/t` commands into the sender and recipient KWC DM thread. It does not resend or replace the Minecraft whisper. Bukkit does not expose a reliable final success result for every whisper plugin, so a valid command targeting a known player is used as the capture criterion.

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

Guest chat is rate-limited by both `cooldown-seconds` and `max-messages-per-minute`. The default per-minute limit is `50` messages. Existing server configs are not overwritten automatically, so update `plugins/KOKOTO-WebChat/config.yml` manually if you want the new default on an existing installation.

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

When `reply.game-click.enabled` is true, clicking a non-URL message body rendered by KWC suggests `/kchat reply <messageId> `. URL segments retain their open-link action. `/kchat reply <messageId> <message>` creates a public message with the same `replyTo` metadata used by web replies.

Game replies preserve the player-entered custom emoji token for web history and server relay. On the originating server, KWC also reuses the game emoji plugin's processed command body so the Minecraft reply line renders the emoji. If no processed glyph is available and `emoji.game-link.mode` is `preserve`, KWC emits a plain compatibility line for recognized tokens; that fallback line cannot carry KWC's click/hover metadata.

`local-game-chat: true` replaces the normal local Minecraft chat line with an equivalent clickable component so game-origin messages can also be replied to. Disable it if another chat-format plugin must exclusively own the final renderer. This does not disable reply clicks on web-to-game or remote relay lines.

A local game sender name suggests `/w <realName> ` when clicked. Linked web-user and remote-server game sender names suggest `/kchat dm <realName> `. Sender-name actions remain separate from message-body reply clicks and keep the optional real-name hover.

`game-preview` sends the referenced-message preview before a relayed reply. `game-prefix` changes the source label of the actual reply line. Formats support legacy `&` colors and the placeholders documented in `config.yml`.



`server-relay` connects the public chat of multiple KOKOTO WebChat servers. Game, linked web-user, and guest messages can be delivered to the remote server's web chat and Minecraft chat while preserving message IDs, replies, sender identity, and the originating server.

## Server relay configuration

`peers` is not a connection/session list. It defines the HTTP destinations this server sends relay messages to; the same `id`/`secret` entries authenticate relay requests received from those servers. Configure matching entries on both sides for two-way relay.

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
  forward-received-public-chat: true
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
      url: "https://server3.example.com/chat/api"
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
      url: "https://server1.example.com/chat/api"
      secret: ""
      enabled: true
```

Each peer must be reciprocal: the receiving server must list the sender's exact `server-id`. IDs are case-sensitive after normalization and must be unique. Do not use the same ID for two servers.

## HTTPS and reverse proxies

`url` is the other server's externally reachable KWC API base. KOKOTO WebChat appends `/relay/receive` automatically:

```text
Configured: https://server3.example.com/chat/api
Requested:  https://server3.example.com/chat/api/relay/receive
```

The public HTTPS route must proxy the whole KWC API path to the internal KWC HTTP listener, including POST requests to `/relay/receive`. Do not expose port 8899 publicly when HTTPS already fronts the service. The proxy must preserve these request headers:

```text
X-BMWC-Relay-Version
X-BMWC-Relay-From
X-BMWC-Relay-Timestamp
X-BMWC-Relay-Signature
```

A publicly trusted certificate works with Java normally. A private/self-signed certificate must be imported into the Java trust store or the HTTPS request will fail before reaching KWC.

## Secrets

- `shared-secret` is the default key for every peer.
- `peers[].secret` overrides the shared key for that one connection.
- With two servers, use the same long random `shared-secret` on both servers and leave each peer `secret: ""`.
- With per-peer keys, the two reciprocal entries must use the same pair-specific key.
- If neither a peer secret nor a shared secret is available, the peer is ignored.

## Topology

For three or more servers, use either:

- Full mesh: every server lists every other server. This is simplest and most resilient.
- Hub: leaf servers list a hub and the hub lists every leaf. With `forward-received-public-chat: true`, the hub forwards received public messages to the remaining peers; `false` limits public chat to direct peer links. This option does not disable multi-hop DM routing/read receipts.

Relay IDs, origin suppression, immediate-sender exclusion, and `max-hops` prevent loops in cyclic topologies. There is no persistent offline queue; a message is not replayed later when a peer was unreachable.

## Reload behavior and diagnostics

`/kchat reload` closes the previous relay instance and creates a new one from the current config. Relay uses one HTTPS request per message, not a permanent connection, so there is no separate reconnect operation.

A healthy startup log looks like:

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

If `activePeers` is lower than the configured count, nearby warnings explain which peer was rejected and why. Typical causes are duplicate IDs, a peer ID equal to the local server ID, an empty/invalid URL, an unsupported URL scheme, or a missing secret.

## HTTP errors

- `403 unknown_peer`: the receiving server does not have the sender's exact `server-id` in its active peers. Check both directions and the `activePeers` log on the receiver.
- `401 bad_signature`: the effective secrets differ or a proxy altered the body/headers.
- `401 expired_request`: server clocks differ by more than `max-clock-skew-seconds`.
- `404 relay_disabled`: relay is disabled on the receiver, or the proxy routes to the wrong KWC instance/path.
- `426 unsupported_protocol`: the two plugin builds use incompatible relay protocol versions.

After editing either side, run `/kchat reload` on that side. When a receiver's peer list or secret changes, reload the receiver as well.

## Display behavior

- Web chat shows a colored badge derived from `originServerId`; the same server keeps the same color.
- Web-to-game output uses `{server}` and `{server_id}`. The local server omits its own automatic label; if a remote message uses an older format containing neither placeholder, `[server-name]` is prepended automatically.
- Discord direct relay formats support `{server}` and `{server_id}` and receive an automatic prefix when missing.
- `sources.discord` and `sources.system` are disabled by default to avoid DiscordSRV loops and noisy cross-server event duplication.

## Discord relay options

```yaml
discordsrv:
  game-relay-mode: "discordsrv"
  append-web-emoji-links: true
  append-game-emoji-links: true
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
  reply-relay:
    enabled: false
    prefix-enabled: true
    preview-enabled: true
    preview-max-length: 120
```

Discord formats support `{server}`, `{server_id}`, `{sender}`, `{name}`, `{role}`, `{source}`, `{message}`, and `{channel}`. While server relay is active, old custom formats without `{server}` or `{server_id}` receive an automatic `[server-name]` prefix. In a shared Discord channel, only the KWC instance that observed the original local Minecraft chat may edit DiscordSRV's native game relay; peer servers leave it unchanged so server labels and emoji links are not stacked. Server-relayed messages are not re-sent to Discord by receiving peers, so there is no relay-only fallback when the origin server's Discord bridge is unavailable.

`append-web-emoji-links` and `append-game-emoji-links` add public KWC emoji URLs for Discord previews. In `discordsrv` mode KWC scans the `:emoji:` token text in DiscordSRV's actual Discord game message and passes it through the same registered KWC token-to-link routine used by web→Discord. The early game-chat fingerprint is only for origin-server attribution in shared channels; Minecraft glyphs or game-rendered text are never used as the Discord emoji conversion source. Keep `game-relay-mode: "discordsrv"` when DiscordSRV already relays normal Minecraft chat to prevent duplicates. `reply-relay` optionally adds a replied-message preview and is disabled by default.

## Administrator Discord keyword alerts

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

This is an administrator-wide alert policy, not a per-user Discord notification. KWC performs matching, source filtering, mentions, formatting and duplicate suppression. DiscordSRV is reused only for its authenticated JDA connection and Channels mapping. Web Admin's alert-channel selector shows logical DiscordSRV channel names; the backing numeric channel ID is hidden when a logical name exists. `channel: ""` reuses `discordsrv.channel`; a raw numeric ID is accepted only as an ID-only fallback. Discord-origin messages are never re-alerted. DM and group scopes are disabled by default.

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

### ImageEmojis-Bero 1.9.x

For Bukkit/Paper-family servers, KWC can share `plugins/KOKOTO-WebChat/emojis` with [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero). The important integration values are `serverIp`, `webServerPort`, `emojisFolder: /KOKOTO-WebChat/emojis`, `templateFormat: ':<emoji>:'`, and `replaceInCommands: true`. The ImageEmojis resource-pack HTTP host/port must be reachable by Minecraft clients and is separate from KWC's web port. See `IMAGEEMOJIS_BERO_1_9_0_EN.md`. General ImageEmojis installation/operation belongs to the [upstream project](https://github.com/MrQuackDuck/ImageEmojis).

### SimpleNicks-Bero

On Bukkit/Paper-family servers, set `player-display.mode: "display-name"` to use nicknames rendered into Bukkit display names by [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero). KWC keeps the real linked identity/UUID separately. See `SIMPLENICKS_BERO_EN.md`; use the [upstream SimpleNicks documentation](https://github.com/Simplexity-Development/SimpleNicks) for normal installation and operation.

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


## User profiles and account preferences

```yaml
ui:
  user-profiles:
    enabled: true
    max-profiles: 5
    allow-import-export: true
```

Signed-in users can save visual settings such as theme, font, font size, colors, opacity, text shadow and language as server-side account profiles. `max-profiles` accepts 0-20; `0` disables saved server profiles. JSON import/export is a strict flat schema capped at 16 KiB and excludes session tokens, UUIDs, Web Push endpoints and window coordinates. Window position/size, minimized state and Web Push registration remain device-local. These three settings are available in Web Admin Settings.

## Browser notifications and Web Push

`notifications` controls the shared notification defaults and server-side allow limits for both browser notifications and mobile/background Web Push. `notifications.enabled` is the single default on/off switch for both delivery paths; the old `browser-notifications.*` and `web-push.notify-*` keys are only read as legacy migration/compatibility input. Leave `notify-*` values `true` to let users choose in Chat settings, or set one to `false` to block that notification type even if a user enables it. When `notify-system` is allowed, users can choose all server notifications, join/leave only, or off. Since 5.0.0, signed-in users' notification-type and keyword choices are stored once at account level and reused across browsers/devices; the existing browser values are promoted on first initialization. Guests keep browser-local preferences. Each device's Web Push subscription still stores only the delivery endpoint/filter data needed for background delivery.

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

`auth.link-code-cooldown-seconds` and `auth.link-code-max-per-minute` limit how often the web UI may issue `/kchat auth <code>` link codes per remote IP. Set either value to `0` to disable that part of the limit.

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


Admin custom emoji manager note: the 4.7.0 manager uses a hidden multi-file picker and starts sequential upload immediately after selection, reusing the existing per-file validation/upload endpoint. Renaming an emoji file or folder changes the `:emoji:pack/name:` token. Existing chat messages that reference the old token may no longer render unless the old file/folder name is kept.


## Custom emoji and game-side emoji plugins

KOKOTO WebChat stores custom emoji files under `plugins/KOKOTO-WebChat/emojis`. Subfolders are treated as emoji packs.

By default, `emoji.game-link.enabled` is `false`, so web-to-game messages preserve custom emoji tokens such as `:pack/name:` and `:emoji:pack/name:` unchanged. Use this default when ImageEmojis or another game-side emoji plugin renders tokens in Minecraft chat.

When `emoji.game-link.enabled` is `true`, `emoji.game-link.mode` supports `preserve`, `link`, and `label`.

- `preserve`: force token-preserving behavior even when game-link is enabled.
- `link`: sends `label-format` text plus a short BM Web Chat image link.
- `label`: sends `label-format` text only.

`emoji.game-link.*` only affects web-to-Minecraft chat. Discord image preview links are controlled separately by `discordsrv.append-web-emoji-links` for web→Discord and `discordsrv.append-game-emoji-links` for game→Discord. `append-game-emoji-links` can augment DiscordSRV's normal Minecraft→Discord relay messages, while `game-relay-mode: "kwc"` selects KWC as the direct game-chat sender; `discordsrv` keeps DiscordSRV as the sender.

BM Web Chat preserves canonical tokens in web history and relay payloads. If ImageEmojis or ImageEmojis-Bero is enabled, KWC reads its public runtime emoji repository through reflection and resolves tokens to the receiving server's active glyphs before constructing clickable Minecraft components. This does not add a hard dependency and does not parse the resource pack.

For interactive lines, resolved ImageEmojis glyphs are inserted before KWC adds sender, reply, and URL click events, so emoji rendering and clickable URLs work together. Only unresolved known tokens use the single plain-Bukkit-line fallback for compatibility with another game-side renderer; that fallback cannot carry KWC click or hover metadata.

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

`private-chat-super-admins: []` lists exact UUIDs or Minecraft names allowed to see DM/group-chat metadata for moderation/accounting. The metadata view shows participants/titles, message counts, approximate storage size, retention status, and management actions. DM message bodies are available only when `direct-message.admin-audit.enabled: true`, and group-chat bodies only when `group-chat.admin-audit.enabled: true`; both views are read-only and every page read is audit-logged.


`frontend.standalone.app-name` and `frontend.standalone.app-short-name` control the standalone page/PWA name. Reinstall the Home Screen web app after changing them on mobile devices. `web-push.notification-title` controls the default title used for test/system/background push notifications; if it is empty, the plugin uses `frontend.standalone.app-name`.


Existing configs that still contain old generated display names such as `KOKOTO WebChat` or `KOKOTO WebChat` are treated as legacy defaults and use the new fallback.

### Dynmap adapter

`adapters.dynmap` embeds the KWC frontend in Dynmap without using Dynmap's own webchat transport. With `enabled: true`, KWC reads Dynmap `webpath` from common `configuration.txt` locations, installs `kokoto-web-chat/` assets, and maintains only a marked block in `index.html`. Because Dynmap may regenerate web files when `update-webpath-files: true`, run `/kchat reload` if another Dynmap operation rewrites the page. If the Dynmap web directory is copied to another host, set `web-root` to the actual shared/mounted directory; KWC cannot modify a remote copy that is not visible in the server filesystem. `api-base-url: ""` keeps the same KWC direct-HTTP/HTTPS auto-resolution used by other map adapters.


### LiveAtlas adapter

`adapters.liveatlas` embeds KWC into an existing LiveAtlas static frontend. LiveAtlas can display Dynmap, squaremap, Pl3xMap, Overviewer, or multiple configured servers, so this adapter is backend-neutral and is available on Bukkit, Fabric, NeoForge, and Forge. With `web-root: ""`, KWC scans common local map web directories but accepts only an `index.html` containing LiveAtlas markers such as `window.liveAtlasConfig`. For a Caddy/nginx-hosted copy, set `web-root` to the actual shared/mounted directory. KWC owns only its `addon-path` directory and a marked block in LiveAtlas `index.html`. Run `/kchat reload` after a LiveAtlas update replaces `index.html`. Do not enable a backend-specific KWC adapter and LiveAtlas adapter against the same physical web root.

### uNmINeD adapter

`adapters.unmined` embeds KWC into an existing uNmINeD static web export. uNmINeD is an external map generator rather than a Minecraft server plugin, so this filesystem adapter is available on Bukkit, Fabric, NeoForge, and Forge without a uNmINeD runtime dependency. Current exports use `index.html`; KWC also accepts legacy `unmined.index.html`. Auto-detection is intentionally conservative and requires uNmINeD markers such as `unmined.map.properties.js` plus the uNmINeD runtime. For arbitrary export locations or Caddy/nginx document roots, set `web-root` to the server-visible shared/mounted export directory. Re-exporting the map may replace the HTML entry point or KWC-owned assets, so run `/kchat reload` afterward.

### Overviewer adapter

`adapters.overviewer` embeds KWC into an existing Minecraft Overviewer static web-map output. Overviewer is an external renderer, not a server plugin, so the same filesystem adapter is available on Bukkit, Fabric, NeoForge, and Forge without an Overviewer runtime dependency. KWC accepts only an existing `index.html` positively identified by Overviewer-specific markers/assets such as the `Minecraft-Overviewer` generator metadata, `overviewerConfig.js`, `overviewer.js`, and `overviewer.css`; a generic Leaflet page is not modified. For arbitrary output directories or Caddy/nginx document roots, set `web-root` to the server-visible shared/mounted Overviewer `outputdir`. An Overviewer render or `--update-web-assets` can regenerate `index.html`, so run `/kchat reload` afterward. Overviewer's `customwebassets` feature remains available for operators who maintain their own persistent template; KWC Stage 1 does not edit the Overviewer Python configuration.

> **IP / router port-forwarding:** Direct map-adapter auto detection assumes the externally reachable KWC port is the same as `http.port` (default 8899). If a router maps a different public port, such as public `8900` → server `8899`, set that map adapter's `api-base-url` explicitly to `http://PUBLIC_IP:8900/api`. Standalone opened directly on the forwarded port can keep `api-base-url: ""` because it uses its current origin.
