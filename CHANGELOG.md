# Changelog

## 5.3.0

KOKOTO WebChat 5.3.0 expands **5.2.1** with improved DM/group management, Chat Events, user profiles and presence, blocking and moderation, Relay features, and desktop multi-window UI.

### Added

#### Chat Events

- Multiple **First come** and **Lottery** events can run at the same time.
- Events can be created, joined, completed, deleted, and inspected from both the Web UI and `/kchat game`.
- For First come events, the **number of winners is also the participant capacity**. Registration closes automatically and the event completes as soon as all slots are filled.
- Lottery events allow the participant capacity and number of winners to be configured separately.
- Event announcements can be limited to the current server or sent across Relay-connected servers.
- Relay event links are routed back to the exact server where the event was created.
- Participant and winner names follow the global **Display Name / Real Name** setting.
- Event result announcements in chat use the `🏆` prefix and display winners as **Display Name (Real Name)** when the two names differ.

#### Full DM and Group History Search

- DM and group conversations can be searched across stored history, including messages that are not currently loaded on screen.
- Search results can jump directly to the corresponding message position.
- Searching does not mark conversations as read.

#### Actual Message Deletion

- The previous DM **Hide for me** behavior has been removed for newly deleted messages.
- Users can delete only the DM messages they sent themselves, and deletion removes the message from both sides of the conversation.
- Messages sent by other users cannot be deleted by ordinary users.
- Cross-server DM deletion is handled through Relay and verifies deletion authority against the original sender.
- A policy for ordinary users deleting their own public-chat messages has been added.
- Group rooms can optionally allow ordinary members to delete their own messages.
- Reply metadata referencing a deleted message is cleaned up as part of deletion.
- Ordinary-user self-deletion is disabled by default with `moderation.allow-user-self-message-delete=false`.
- `moderation.self-message-delete-window-minutes` can optionally restrict how long after sending a message ordinary users may delete it. `0` means no time limit.

#### Group Roles and Pinned Messages

- Added per-group **owner / admin / member** roles.
- Group admin privileges apply only inside that group and do not grant global KWC administrator privileges.
- The owner can assign or remove group admins.
- Group-specific pinned messages are supported.
- All members can view pinned messages, while owners and admins can pin, unpin, and reorder them.
- Pinning, message deletion, and ordinary-member self-deletion can be controlled independently for each group.
- Deleting the original message does not remove its stored pin snapshot. The snapshot remains until it is explicitly unpinned or the room is deleted.

#### User Profiles and Presence

- User profiles can be opened by clicking a user name.
- Minecraft Heads are used as the default profile image with a local fallback image.
- Custom PNG, JPEG, and WebP profile images are supported.
- Profiles support an About/status message of up to 280 characters.
- **Game and Web presence are tracked separately.**
- Compact user lists show one representative state using the priority Game → Web → Offline.
- Users can set their visible status to **Online / Busy / Offline**.
- When a user selects Offline, their underlying Game/Web connection state is not exposed to other users.
- Administrators can optionally include offline accounts in the user list.

#### User Blocking

- Any logged-in user can personally block another user.
- Messages and related notifications from blocked users are hidden from that user's public chat, DM, and group views.
- Blocking is tied to the actual account identity and cannot be bypassed by changing the display name.
- Blocking and unblocking take effect immediately from profiles and the blocked-user management screen.

#### Administrator and Moderator Permissions

- Per-user chat and file-upload restrictions are supported.
- Administrators can remove custom user profile images.
- Administrators can change users between USER, MODERATOR, and ADMIN roles.
- Individual MODERATOR accounts can be granted specific administrative capabilities.
- Permissions such as online-user management, message deletion, mute, public pin management, user restrictions, and profile-image management can be enabled independently.

#### CAPTCHA

- CAPTCHA now supports `off`, `math`, `text`, and `mixed` modes.
- Math CAPTCHA supports `easy`, `normal`, and `hard` difficulty levels.
- No external CAPTCHA service is required.

#### @Mention Autocomplete

- `@` mention autocomplete is available in public chat, DM, and group message inputs.
- Users can be searched by both display name and real account name.
- Keyboard, mouse, touch, and IME composition input are supported.
- When possible, the selected user is inserted using the real Minecraft account name to provide a stable mention token.

#### Image Privacy

- Image metadata is stripped before normal uploads and custom profile images are stored.
- JPEG EXIF/IPTC/comments, PNG text/EXIF metadata, and WebP EXIF/XMP metadata are removed.

### Relay Protocol 2.2

- Relay Protocol revision has been extended to **2.2** while retaining protocol major version 2 compatibility.
- Added the `delete` capability for cross-server DM deletion.
- Added the `game` capability for remote Chat Event routing.
- Added the `profile` capability for remote public-profile lookup.
- Each Relay peer can independently configure **send / receive policies** for:
  - public-chat
  - event
  - dm
  - profile
- Event Relay traffic uses the dedicated `server-relay.sources.event` policy instead of the general system-event policy.
- Public-chat reactions and typing indicators follow the public-chat policy.
- DM reactions, typing indicators, read state, and deletion follow the dm policy.

### Desktop UI and Multi-window

- In **Standalone desktop mode**, multiple DM and group conversations can be opened simultaneously as independent windows.
- Movement, resizing, maximization, and restoration behavior has been unified across public chat, DM/group lists, and individual DM/group windows.
- Double-clicking a window title toggles maximize/restore.
- The resize-lock feature has been removed in favor of normal freeform resizing.
- Files can be dragged and dropped directly onto an individual DM or group window.
- The exact DM/group conversation under the drop target is confirmed before the file is uploaded.
- Stacking behavior prevents the public-chat window from being accidentally dragged or resized through a foreground DM/group window.
- Resize hit areas extend slightly both inside and outside the visible window edge for easier grabbing without covering the normal scrollbar area.
- Resizing is available immediately after page refresh instead of waiting for configuration/bootstrap recovery to finish.

### UI and Usability

- Public-chat header controls shrink progressively as available space decreases and wrap to a second row only when necessary.
- The controls immediately return to one row when enough space becomes available again.
- Minimized public chat keeps its title visible together with the `+` restore button in a compact **124×48** layout.
- Minimization is available in supported embedded adapter environments as well as Standalone, including narrow/mobile layouts.
- Refreshing or reconnecting restores the conversation position the user was previously viewing.
- Automatic scrolling to the newest message occurs only when the current view is **less than approximately two rendered text lines from the bottom**. If the user is reading further up, new messages do not forcibly pull the view downward.
- The online-user button correctly displays the number of logged-in Web users, including `0`.
- The Display Name / Real Name setting is applied consistently to public/group pin attribution and Chat Event participant/winner lists.
- DM/group title dragging and double-click maximization no longer interfere with each other.
- DM/group windows and related popups have improved placement on mobile and narrow screens.

### Changed

- `direct-message.confirm-hide` → `direct-message.confirm-delete`
- `group-chat.confirm-hide` → `group-chat.confirm-delete`
- Existing 5.2.x boolean values are migrated automatically.
- DM/group conversations no longer create new per-message **hide-for-me** state.
- Previously stored hidden state remains read-compatible so existing users do not suddenly see messages they had already hidden.
- The configuration schema/reference has been updated to **5.3.0**.
- User-facing game command documentation now consistently uses `/kchat`; the `/kc` and `/kwc` aliases remain available.
- Frontend maintainability sources are now split into 18 ordered fragments under `frontend/inner/`, with the root `inner.js` and eight map/Standalone wrappers generated through a deterministic build process.

## 5.2.1

5.2.1 is a frontend hotfix release over 5.2.0.

- Fixed a BlueMap addon refresh race where `chat.js` could initialize before `config.js`, lock the API base to site-root `/api`, and keep retrying the wrong path after an SSE/bootstrap failure. The addon now recovers the config/base and retries bootstrap after transient failure.
- Ensured the user notification setting displays `@Mention` / `@멘션` / `@メンション` / `@提及`, including upgrades that retain an older external language file without the `@`.
- Kept the custom-emoji horizontal scrollbar at 12 px while matching the theme-aware vertical scrollbar thumb/hover colors and shape.

## 5.2.0

5.2.0 adds message reactions, personal conversation archives/PDF export, public/DM/group typing indicators, custom-emoji picker enhancements, and Relay Protocol 2.1 capability negotiation. It also includes security and account-linking corrections carried forward from the 5.1.0 release baseline.

### Added

#### Public, DM, and group message reactions
- Added persistent reactions for logged-in users on public-chat, DM, and normal group-chat messages using Unicode emoji and KWC custom emoji (`:pack/name:`). Group membership join/leave events are excluded.
- Added multi-server reaction support. Public reactions use the message origin server as the authority, cross-server DM reactions are exchanged only between the two participant servers, and group-room reactions remain local to that server.
- Added **Admin > Emojis > Reaction icons** management with a master reaction switch, a separate KWC custom-emoji allowance, Unicode reaction ordering/catalog control, and administrator-managed search aliases.
- Added a per-account **Reactions** notification preference shared by live browser notifications and background/mobile Web Push. Each browser/device uses only the delivery path it supports. The server-side allow/default key is `notifications.notify-reactions`.
- Suppress duplicate DM/group attention account-wide when that exact conversation is actively being viewed in any signed-in KWC browser for the account: the page must be visible and focused, the chat window must not be minimized, the DM/group modal must be open, and the current thread/room ID must match. This short-lived view heartbeat is independent of whether the viewing browser itself subscribes to Web Push. While one active client is reading the exact private conversation, all browser notification/in-chat notification-inbox paths and all Web Push subscriptions for that account are suppressed for that conversation; when no active client is viewing it, normal notification delivery resumes.

#### Custom-emoji picker
- Added a browser-local **Recent** pseudo-folder for recently inserted custom emoji.
- Added an optional **Favorites** pseudo-folder with hover `☆`/`★` controls. Administrators can disable Favorites, store favorite IDs with the signed-in KWC account by default, or select browser storage; `emoji.favorites.max-per-account` uses `0` for unlimited or a positive retained-item limit. Account storage uses KWC `user-preferences` and is independent of public/DM history storage backends. Favorites keep the original catalog IDs and message insertion continues to use the original `:pack/name:` token.
- Added floating custom-emoji search across ID, name, display label, pack name, and every public alias with NFKC/case normalization. Recent and Favorites are shared by public chat, DM, and group-chat pickers.

#### Relay Protocol 2.1
- Added independent relay protocol revision/capability negotiation instead of using the KWC product version as the compatibility key.
- Relay Protocol 2.1 advertises `public`, `dm`, `read`, `reaction`, `reaction-authority`, and `typing` capabilities. KWC 5.1.0 peers remain compatible with 5.2.0 for the common Relay v2 public-chat/DM/read feature set; reactions and typing require participating 5.2.0 / Relay 2.1 peers.
- Retained verification compatibility with the legacy Relay 2.0 handshake canonical used before revision negotiation.

#### Saved conversations and PDF export
- Added per-account **Saved conversations** for public chat, DMs, and group rooms. Users select a message range, and the server re-reads and validates that range before creating the private snapshot.
- Added static archive/PDF views that preserve KWC chat presentation, reply/reaction/server context, display name and real account name. Attachments remain references to their original KWC sources rather than being duplicated into the archive.
- Added PDF export using the user's currently applied KWC appearance.
- Added `chat.conversation-archive.enabled` (default `true`). When disabled, archive API routes are not registered, `conversation-archives.db` is not opened/created, and saved-conversation controls are not created in the web DOM. Existing archive data is preserved for later re-enable.
- Added configurable archive quotas: `max-archives-per-user` (default 100), `max-messages-per-archive` (default 1000), and `max-messages-per-user` (default 10000). Lowering a quota does not delete existing snapshots; it blocks new saves that would exceed the active limit.

#### Public/DM/group typing and private-chat controls
- Added event-driven public/DM/group typing indicators without polling or persistent typing state. Server administrators control Open chat, DM, and Group chat independently in Web Admin **Settings** or `chat.typing-indicator.*` in `config.yml`; defaults are Open chat OFF, DM ON, and Group chat ON. `chat.typing-indicator.user-display-control` defaults OFF; when an administrator enables it, signed-in users can use one account-stored **Typing indicators** preference that hides incoming hints on their own screen without suppressing their outgoing typing activity. The rounded high-opacity indicator floats above the active composer, follows each viewer's existing display-name/original-name toggle, strips formatting tags from shown names, and scales to about 80% of the user chat font without dropping below the configured base UI font. Public and remote-DM typing can use Relay 2.1; group typing remains scoped to room participants.
- Consolidated private-room invite, saved-conversation, hide, and room-management actions under the room Settings menu according to the existing permissions.

### Fixed from 5.1.0
- Corrected mention notification matching inherited from 5.1.0. Ordinary occurrences of a user's name no longer count as mentions: a mention must start with `@`, matches registered real/display names using the longest name at each `@`, and notifies all accounts that share the same exact display name.
- Corrected existing bundled/account-linking language values that still showed the obsolete `/kwc auth` command. KWC now migrates those built-in login hint/waiting texts to the supported `/kchat auth <code>` command without overwriting unrelated customized translations.
- Enforced the guest-disabled public-chat visibility policy on server-side history, search, around-history, and SSE endpoints instead of relying on frontend hiding alone.
- Hardened trusted-proxy / `X-Forwarded-For` client-IP resolution against leftmost-header spoofing.
- Enforced exact paths for leaf HTTP API contexts while retaining prefix routing only for intentional file/subtree endpoints.
- Reduced the unauthenticated client-config response to fields actually consumed by the frontend.

## 5.1.0

5.1.0 is a feature, reliability, compatibility, and security update over the **5.0.0** public release baseline.

### Security — Relay Protocol v2

**Who should prioritize this upgrade:** servers that enabled/configured Relay v1 in KWC 5.0.0 or a compatible BMWC relay deployment. Servers that did not use server relay are not affected by the relay-specific issue.

- Relay v1 over `http://` carried relay payloads without transport encryption, so those deployments are the highest-priority upgrade case.
- Relay v1 also used a flat peer trust set and top-level shared secret. HTTPS protected the network hop, but exposure of that secret or a peer/forwarding misconfiguration still affected the whole flat relay trust set.
- Relay v2 replaces that model with explicit `groups -> peers`, one shared secret per group, reciprocal same-group peer configuration, request-by-request authenticated encryption, replay/loop defenses, and authenticated responses.
- Relay v2 payloads use directional HKDF-SHA256 keys and AES-256-GCM. Protection is hop-by-hop, not end-to-end; forwarding servers remain trusted participants.
- Direct one-hop HTTP remains possible with an explicit warning because the v2 payload itself is encrypted/authenticated, but HTTP peers cannot be forwarding hops. Forwarding is same-group HTTPS only.
- Legacy Relay v1/BMWC endpoints return HTTP 426.
- The first **5.0.0 -> 5.1.0** migration disables relay instead of guessing new trust groups. Reconfigure reciprocal v2 groups before re-enabling it.

### Cross-server chat, DM, and Reply

- Public relay, cross-server DM delivery/read receipts, stable remote identities, duplicate protection, and forwarding now use the same Relay v2 trust boundary.
- Added persistent Web/game DM and group-chat Reply metadata, original-message preview/jump, restart persistence, and server-side validation.
- Unified game-side private Reply presentation with public Reply while preserving private delivery scope.
- Fixed `/kchat group` room names containing spaces/quotes/repeated whitespace and added optional real membership join/leave notices.
- Improved narrow/mobile DM/group metadata layout.

### Emoji, notifications, pins, and browser UI

- Canonicalized custom emoji names/tokens, improved Reply URL/emoji rendering, exact-token picker insertion, and emoji catalog recovery after fetch/SSE/catalog changes.
- Custom emoji now render in the notification center and pinned-message UI with compact size limits without changing normal chat emoji sizing.
- Fixed refresh-time history emoji that could stay blank until the message scrolled out of view and back; transient custom-emoji image failures are retried in place.
- Fixed exact account chat-profile persistence/application, including intentionally unset visual fields.
- Avoided Android Chrome Autofill accessory UI on normal chat composers and switched administrator emoji upload to the generic system file picker path.

### HTTP, SSE, configuration, and operations

- Raised default SSE limits to **10 per resolved client IP / 500 total** with clearer 429/trusted-proxy diagnostics.
- Added EN/KO/JA/ZH config comment templates selected by `ui.language` and a complete **394-setting** administrator input guide.
- Migration/difference output now compares semantic YAML paths/values and preserves readable structured YAML.
- Fixed Relay config reconstruction for multiple groups/peers.
- Added localized warnings for externally exposed plain-HTTP listeners and direct HTTP Relay peers.
- Repeated operational HTTP/network failures now suppress identical repeats, summarize them, and report recovery while still logging the first/changed failure immediately.
- Web-command execution notices can also be delivered to online Minecraft players when enabled.
- Update checks use `kokoto-webchat` first and fall back to `bluemapwebchat` during the project-address transition.

### Compatibility

- Fixed Fabric 1.18.2 legacy mixin packaging/startup.
- Fixed NeoForge 1.21.1 modern metadata startup (`InvalidModFileException: Missing ModLoader`).
- Fixed Forge 1.18.2–1.19.4 runtime dependency packaging.
- Fixed Forge/NeoForge runtime Minecraft target reporting so exact-target JARs no longer report a hard-coded `26.2` value.
- Bukkit/Paper/Spigot: Minecraft 1.18–26.2
- Fabric: 16 exact targets, Minecraft 1.18.2–26.2
- NeoForge: 12 exact targets, Minecraft 1.20.2–26.2
- Forge: 16 exact targets, Minecraft 1.18.2–26.2
- Mod-loader targets use Java 17/21/25 according to Minecraft version; Bukkit uses Java 17.

### Documentation and source packages

- Reorganized and synchronized the English/Korean/Japanese/Simplified-Chinese documentation and Wiki.
- Consolidated duplicate/version-specific upgrade/reference pages and canonicalized Wiki diagrams.
- Public source packages contain product/build source only; internal validation scripts/harnesses are distributed separately as validation tools.

## 5.0.0

5.0.0 is a major release that expands the former BlueMap-focused project into **KOKOTO WebChat**, a multi-platform and multi-map Minecraft web chat system.

### Platform and map expansion

- Standardized the project identity as **KOKOTO WebChat (KWC)**. `/kchat` and `/kchat` are the canonical commands and `/chat` is the default public web path.
- Added server builds for **Bukkit/Paper/Spigot**, **Fabric (16 exact targets)**, **NeoForge (12 exact targets)**, and **Forge (16 exact targets)**, covering supported Minecraft versions up to 26.2.
- Expanded frontend integration beyond BlueMap with **squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, Minecraft Overviewer**, and a **standalone WebChat** mode.
- Improved multi-server operation for public relay, cross-server DM/group chat, remote-user handling, delivery/read status, relay failure backoff, and mixed-version transition deployments.

### Chat, moderation and user features

- Added a shared **Unicode-aware content filter** for game and web chat with Block, Mask and Replace actions, bulk UTF-8 word lists, custom rules, anti-evasion matching, matched-word reporting, and Web Admin editing/testing. Hangul matching now avoids false positives such as `시발` matching `신발`, while jamo shorthand such as `ㅅㅂ` remains supported.
- Added **account-synced chat preferences and multiple visual profiles**, including validated JSON import/export, improved settings synchronization, and removal of stray native file-picker controls. Device-specific window state and Web Push endpoints remain local to each device.
- Expanded notifications with account-level keyword preferences, duplicate-notification suppression, desktop Notification/Web Push separation, mobile Web Push retry handling, and **administrator Discord keyword alerts** through DiscordSRV.
- Improved guest operation with Unicode guest names, stronger impersonation protection, corrected CAPTCHA/session behavior, and cleaner login/logout transitions.
- Added `upload.filename-mode: original` with safe **Unicode filename preservation**, collision handling, and Windows clipboard filename fixes, while retaining randomized filenames as the default alternative.
- Improved registered-emoji handling across game, web, relay and Discord paths, and added deployment-focused integration guides for **ImageEmojis-Bero** and **SimpleNicks-Bero**.

### Reliability, security and UI

- Reworked long-history **virtual scrolling** so message order remains deterministic with images, GIFs, video, audio, YouTube/iframe embeds and link previews; mid-history focus/reconnect no longer replaces the viewed message slice.
- Added SQLite history integrity/recovery checks and hardened authenticated APIs, SSE stream authentication, administrator IP restrictions, Web Push endpoints, Discord/iframe boundaries, and request limits.

### Configuration and upgrade behavior

- Reworked configuration migration around the bundled 5.0.0 `config.yml`: existing operator values are preserved while current settings, comments and layout are rebuilt consistently. `5.0.0_auto_migration` keeps same-version reconciliation enabled; exact `5.0.0` disables it.
- Unified configuration/reference behavior and bundled language coverage across supported platforms. The four bundled web languages remain aligned, while administrator-customized translations are preserved when unchanged bundled strings are refreshed.

### Compatibility

- Bukkit/Paper/Spigot: Minecraft **1.18–26.2**
- Fabric: **16 exact targets**, Minecraft **1.18.2–26.2**
- NeoForge: **12 exact targets**, Minecraft **1.20.2–26.2**
- Forge: **16 exact targets**, Minecraft **1.18.2–26.2**
- Target Java is selected by Minecraft generation: Java **17 / 21 / 25** as applicable.

## 4.7.0

- Added administrator custom-emoji multi-file upload using the same browser picker flow as the existing normal chat file upload. The upload control now opens a hidden `multiple` file input; when the picker closes, KOKOTO WebChat immediately copies the selected `FileList`, clears the native input, and begins sequential upload without a separate selection/confirmation stage. Upload progress and active-transfer cancel behavior remain available, while per-file extension/size checks, total-storage accounting, unique-name handling, audit logging, and PNG-sidecar generation continue through the existing server endpoint.
- Expanded the declared Bukkit/Spigot API compatibility baseline from Minecraft 1.21 to 1.18. The plugin remains compiled for Java 17 and builds against Spigot API 1.18.2, keeping the conservative supported range at Minecraft 1.18 through 26.2. Paper `AsyncChatEvent` remains reflection-detected with Bukkit `AsyncPlayerChatEvent` as the hard-linked fallback.
- Added configurable colon-delimited message tokens. The English defaults provide newline (`:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, `:br:`), blank-line (`:blankline:`, `:emptyline:`, `:paragraphbreak:`), and indentation (`:tab:`, `:indent:`) actions. Administrators can replace or add aliases in any language. Optional printable custom substitutions are also supported, while unknown tokens remain untouched for compatibility with custom/image emoji plugins. Minecraft output keeps the existing single-line sanitizers for ordinary CR/LF input, but configured newline/blank-line tokens are carried separately and emitted as explicit Minecraft chat lines at final delivery. Cross-server game rendering of these intentional line breaks requires the receiving KOKOTO WebChat server to run the same 4.7.0 token-line delivery support.
- Fixed bundled config comment refresh so repeated startup/reload passes are idempotent. The public-relay delivery comment is no longer re-added on every refresh, and exact duplicate copies of that bundled comment left by earlier refreshes are collapsed to one without changing setting values or custom comments.
- Added safe top-level config layout normalization on startup/reload. Known `config.yml` blocks are reordered to the bundled 4.7.0 order without changing their current values or block comments; unknown top-level blocks are retained after known blocks in their original order.

### Configuration

4.7.0 adds the `message-tokens` section. Existing chat, DM, group-chat, relay, emoji, and upload defaults are otherwise unchanged. The configuration review marker changes to:

```yaml
config-version: "4.7.0"
```

Existing setting values are not overwritten. On startup/reload, known top-level `config.yml` blocks are reordered to the bundled 4.7.0 layout while each block's current text, values, and custom comments are preserved; unknown top-level blocks remain last in their original order. KOKOTO WebChat now writes `config-reference-4.7.0.yml` as a complete byte-for-byte copy of the bundled 4.7.0 default configuration, including comments, whenever an existing config is checked. This gives older or unversioned installations a full current reference independent of the detected baseline. When migration review is required, `config-migration-4.7.0.yml` still contains the concise missing/changed settings and review marker; empty maps such as `message-tokens.custom: {}` are now retained as real missing settings instead of disappearing from the fragment. The bottom of the migration file also contains a comment-only textual diff against `config-reference-4.7.0.yml`. Unchanged lines are omitted; each side prints the file name, a separate `Line` or `Lines` field, and then only the differing text. Reference-only blocks separately show where to insert them in the current config.

Default token configuration:

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

Aliases are written without the surrounding colons in config; users type them as `:alias:`. Administrators may localize aliases freely. Literal custom replacements are restricted to printable text; newline/blank-line/tab behavior uses the dedicated actions instead of escape strings.

## 4.6.3

- Added optional read-only administrator group-chat content audit. Explicit accounts listed in `private-chat-super-admins` can open group-chat message bodies from the administrator metadata view only when `group-chat.admin-audit.enabled: true`.
- Group-chat audit access does not require room membership and does not join the room, mark messages read, change unread counts, send messages, upload files, hide messages, or modify membership. Ordinary ADMIN/MODERATOR roles do not gain content access automatically.
- Every group-chat audit page read is recorded as `admin.group-audit-read` with the actor, room ID, pagination position, limit, and returned count. Message bodies are not copied into the audit log.
- Refreshed the bundled 4.6.3 configuration comments to describe the current update-check, cross-server DM, relay, read-status, and administrator-audit behavior. Existing `config.yml` setting values are not rewritten; only unchanged older bundled comment blocks are refreshed, while custom comments are preserved.
- Fixed DM/group native video and audio playback during live message/status refreshes. Private-chat message lists now reconcile by stable message key like public chat: existing message/media DOM stays mounted, new messages are inserted without rebuilding the conversation, and delivery/read metadata updates in place. Active playback therefore continues from its current position. Leaving or switching a private conversation now hard-discards that conversation's message/media DOM and private media-open state; re-entering starts from the unopened click-to-load state (or a newly created non-playing media element when click-to-load is disabled), so no previous player is reused or auto-resumed.
- Fixed pinned-message source metadata in the detached pinned-message window. Clickable Web/Game DM targets and relay server badges now use the same transparent/buttonless metadata styling as normal chat instead of falling back to browser default button chrome.

### Configuration added in 4.6.3

Compared with the bundled 4.6.2 defaults, 4.6.3 adds only:

```yaml
group-chat:
  admin-audit:
    enabled: false
```

The configuration review marker also changes to:

```yaml
config-version: "4.6.3"
```

Both gates are required to open group-chat bodies:

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

The existing `direct-message.admin-audit.enabled` switch remains independent and continues to control only DM body auditing.

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

All servers that exchange cross-server DMs should run **KOKOTO WebChat 4.6.2 or later** to use end-to-end delivery confirmation, retry, and remote read-status acknowledgement.

### Configuration

4.6.2 adds no administrator-facing configuration keys and changes no existing setting defaults. The configuration review marker changes to:

```yaml
config-version: "4.6.2"
```

## 4.6.1

- Fixed cross-server direct messaging from the web interface. This includes exact `server-id + player UUID` targeting, shared game/web sessions, destination-server delivery, remote DM search, message-metadata shortcuts, game `/kchat dm` and `name@server-id` whisper routing, and server identification in the DM window before the first message is sent.
- Added an optional read-only administrator DM audit view for responding to misconduct or other exceptional incidents. Access is limited to explicitly listed private-chat super administrators, and every audit read is recorded without copying message bodies into the audit log.
- Added a compact update checker. It uses Modrinth as the release source, logs a newer stable version to the console, and shows administrators a one-line join notice with clickable Modrinth and CurseForge pages. Only `update-check.enabled` is configurable; timing, channel, permission behavior, and links use built-in defaults.
- Reorganized the bundled `config.yml` into clearly labeled functional groups without renaming existing keys or changing their defaults. The only newly exposed update-check setting is the single `update-check.enabled` switch directly below the plugin master switch.

### Important cross-server DM requirement

All servers that exchange cross-server DMs must run **KOKOTO WebChat 4.6.1 or later**.

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

- Added dedicated ImageEmojis-Bero 1.9.x compatibility documentation covering the shared emoji directory, token formats, runtime glyph resolution, `replaceInCommands`, permissions, reload/update order, multi-server asset synchronization, DiscordSRV overlap, fallbacks, and troubleshooting.
- Documented the compatibility boundary around `getEmojiRepository().getEmojis()` and the emoji model accessors used by KWC's reflection-based integration; ImageEmojis-Bero remains optional and is not a hard dependency.

### Server-to-server relay

- Added optional signed server-to-server public chat relay for game, linked web-user, and guest messages. Relayed messages preserve sender, UUID, role, reply, message ID, and origin-server metadata and can be delivered to both remote web chat and Minecraft chat.
- Added HMAC-SHA256 request signing, timestamp skew validation, per-peer or shared secrets, HTTP/HTTPS peer URLs, relay-ID de-duplication, hop limits, origin suppression, and immediate-peer exclusion to prevent unauthorized injection and relay loops.
- Added full-mesh and hub topologies, source filters (`game`, `web`, `guest`, `discord`, `system`), independent web/game delivery filters, and remote game-format placeholders.
- Added detailed peer validation and startup diagnostics. Logs now report `activePeers=<usable>/<configured>`, active peer IDs, and the reason invalid, duplicate, self-referencing, secret-less, or malformed peers were ignored.
- Improved relay failure logs to include the remote HTTP response body. Common failures now expose reasons such as `unknown_peer`, `bad_signature`, `expired_request`, `relay_disabled`, and `unsupported_protocol` instead of only a status code.
- Added SQLite migration columns for relay ID, origin server ID/name, and relay hop count. Existing SQLite history databases are upgraded additively without deleting history.

### Server identification

- Added deterministic per-server badges and colors in the web UI. The current server omits its own badge; remote server IDs retain stable, distinguishable colors.
- Added originating-server labels to remote web-to-game output. Existing formats without `{server}` or `{server_id}` are automatically prefixed with `[server-name]` only when the message originated on another server.
- Added Discord `{server}` and `{server_id}` placeholders. Existing Discord formats without those placeholders are automatically prefixed with `[server-name]`, and editable DiscordSRV Minecraft relay messages are labeled when possible.

### Game replies and direct messages

- Added public in-game replies for KWC messages. Clicking a non-URL message body suggests `/kchat reply <messageId> ` and `/kchat reply <messageId> <message>` stores the same `replyTo` metadata used by web replies.
- Added optional clickable rendering for local Minecraft chat through `reply.game-click.local-game-chat`, allowing game-origin messages to participate in the same reply system. This can be disabled when another chat-format plugin must own the final renderer.
- Changed linked web-sender name clicks in Minecraft to suggest `/kchat dm <real Minecraft name> ` instead of vanilla `/w`, keeping the conversation in KWC's persistent private DM thread and web inbox.
- Added optional mirroring of non-cancelled `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, and `/t` commands into the sender and recipient KWC DM thread with `direct-message.capture-game-whispers`. The Minecraft command itself is not resent or replaced.
- Added localized game reply click hints and reply-command usage/errors for `en-US`, `ko-KR`, `ja-JP`, and `zh-CN`.
- Added remote relay senders with a player UUID to the same known-player directory used by the web DM recipient search and `/kchat dm`. Their latest relayed display name and real Minecraft name become searchable after receipt and are restored from retained chat history after restart; guest and Discord messages without a player UUID remain excluded.

### Configuration and documentation

- Added complete feature-by-feature user and operations manuals in English, Korean, Japanese, and Simplified Chinese. The manuals cover installation, deployment modes, HTTPS, public chat, accounts, guests, moderation, history/search, replies, DM, group chat, uploads, previews, emoji/ImageEmojis-Bero, notifications, DiscordSRV, server relay, commands, permissions, storage, backup, reload behavior, and troubleshooting with copy-ready examples.
- Bumped the plugin and build artifact version to `4.6.0` while retaining the historical release entries below this section.
- Added `config-version: "4.6.0"` as an administrator review marker and replaced the fixed partial upgrade file with a generated `config-migration-4.6.0.yml` fragment. When the marker is missing or differs from the running plugin version, the plugin compares the physical config with the current bundled defaults and the bundled 4.5.5 baseline, then writes copy-ready missing settings, changed bundled defaults, and the target `config-version` review marker. The fragment is still generated when there are no other differences, so unversioned configurations can be explicitly version-managed. Version and old/new default details are comments rather than YAML metadata; custom-value and obsolete-setting information lists are omitted. The real `config.yml` is never modified. When the marker matches, comparison is skipped and stale same-version guidance is removed. Privacy-sensitive missing behavior keys use migration-safe disabled fallbacks only while the versions do not match.
- Added and documented `server-relay.*`, `direct-message.capture-game-whispers`, `reply.game-click.*`, `reply.game-command-format`, and Discord server-label placeholders.
- Expanded English, Korean, Japanese, and Simplified Chinese README, configuration, relay, upgrade, troubleshooting, and release-checklist documentation.
- `/kchat reload` now reloads relay configuration by closing the previous relay instance and constructing a new one. Relay transport is request-based HTTP rather than a persistent socket, so there is no separate connection state to reconnect.
- Preserved raw player-entered emoji tokens when mirroring native whisper commands, moved clickable local-chat replacement to the proper high-priority stage, and kept empty Paper viewer sets from expanding into a broadcast to all players.
- Added a bounded transient reply-target cache so game-only relay delivery can still resolve clicked reply IDs without exposing those messages through web history.


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
- Connected browsers now receive an auth-expired SSE event when a session expires, is logged out from another tab, or is revoked from web/admin or `/kchat revoke`.

- Added a dedicated server notification mode in user notification settings: all server notifications, join/leave only, or off.


### Notification configuration migration

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
  notify-reactions: true
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

### Upgrade notes

- Existing Web Push subscriptions should be turned off and on again from the intended page so the corrected `openUrl` is saved.
- Set `upload.max-total-size-mb` manually to enable upload-folder quota enforcement; the default is `0` for compatibility.


## 4.5.1
- Fixed Web Push notification click navigation so subscriptions remember the actual page URL where push was enabled, including BlueMap web-addon pages and standalone chat pages.
- Fixed notification click handling so public chat, reply, keyword, DM, and group notifications can reopen/focus an existing chat page and jump to the target message, thread, or room when available.
- Fixed mobile Web Push support in BlueMap web-addon mode by registering the Service Worker from the parent BlueMap page instead of the embedded addon iframe.
- Removed the unnecessary standalone-page requirement for Web Push on supported browsers. iOS/iPadOS still requires the site to be added to the Home Screen as a PWA before Web Push can be enabled.
- Added parent-page forwarding for Service Worker notification navigation so notification clicks can reach the embedded chat iframe on BlueMap web-addon pages.
- Fixed minimized chat pill controls so the minimized area only shows the title and restore button. DM, group, notification inbox, login, PIP, and other controls remain available only in the expanded chat window.
- Added emoji folder moving in the admin emoji manager, including single/multi-select moves, conflict checks, PNG sidecar movement, path validation, and audit logging.
- Added reply notifications for public replies to the current user's messages.
- Added a browser-local notification inbox so recent notification-worthy events can be reviewed from the same browser.
- Improved notification language handling so Web Push subscriptions remember browser language and notification text uses language resources more consistently.
- Improved chat settings layout so saved-setting controls and notification-related labels are not clipped in the settings modal.

### Upgrade notes

- Existing Web Push subscriptions should be turned off and on again so the corrected open URL and language values are saved.
- BlueMap web-addon mobile push requires HTTPS and a browser that supports Service Workers, Push API, and Notifications.
- iOS/iPadOS Web Push requires launching the site from the Home Screen PWA.

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
- Fixed standalone `chat.js` bootstrap so the embedded inner application is available instead of failing with an undefined embedded-content bootstrap error.
- Fixed standalone manifest URL generation so the configured public standalone prefix serves its manifest from the standalone route.
- Improved API base handling for direct HTTP, same-domain HTTPS reverse proxy, standalone pages, uploads, and emoji URLs.
- Refreshed Caddy/Nginx and configuration documentation around same-domain `/chat` + `/chat/api` deployments.

### Retention, cleanup, upload safety, and audit logs
- Improved DM/group retention cleanup so decisions are based on surviving message timestamps and respect locked or auto-delete-excluded sessions.
- Improved upload cleanup so files still referenced by public chat history, pinned messages, DM threads, or group rooms are protected before deletion.
- Added optional append-only daily audit logs under `audit/YYYY-MM-DD.log` for management-impacting actions such as command execution, administrative `/kchat` commands, session flag changes, forced DM/group deletion, message deletion, pin management, emoji management, and history clearing.

### Configuration, language files, and upgrade notes
- Added the `group-chat`, `private-chat-super-admins`, `audit`, `browser-notifications`, and `web-push` configuration blocks.
- Changed the default `discordsrv.game-to-discord-format` to `{sender}: {message}`.
- Browser notification and Web Push `notify-*` values are server-side allow limits; users can still choose their own browser-local notification preferences within those limits.
- Added or refreshed UI language strings for group chat, metadata-only administration, retention status, hidden-room actions, admin confirmations, notification controls, and Web Push status/test messages in `en-US`, `ko-KR`, `ja-JP`, and `zh-CN`.
- Existing `config.yml` files are not rewritten automatically. Merge the new blocks from the default config or regenerate the config after backing up your current settings.
- If group chat is enabled, include `group-messages.db`, `group-messages.db-wal`, and `group-messages.db-shm` in backup plans.
- Message bodies remain unavailable from the super-admin metadata view by design.

### Documentation and upgrade guidance

- Added an upgrade recommendation for 4.5.0: because many options changed between 4.3.x and 4.5.0, it is usually safer to back up the old `config.yml`, regenerate a fresh one, and then copy custom settings back manually.
- Clarified that newly generated configs start with `enabled: false`, so regenerating `config.yml` does not start web/chat services, cleanup tasks, or private-message/group-chat storage until the administrator reviews settings and sets `enabled: true`. Existing history databases, uploads, emojis, audit logs, language files, and VAPID key files are not deleted by config regeneration.

Example migration flow:

```bash
# Stop the Minecraft server first.
cd plugins/KOKOTO-WebChat
cp config.yml config.yml.4.3-backup
# Optional: keep a full plugin-data backup too.
# cp -a . ../KOKOTO WebChat-backup-4.3
rm config.yml
# Start the server once. The new config.yml is generated with enabled: false.
# Review and merge your custom values, then set enabled: true and restart or /kchat reload.
```

Key 4.5.0 config blocks to review or merge:

```yaml
enabled: false

private-chat-super-admins: []

audit:
  enabled: true
  directory: "audit"

frontend:
  standalone:
    enabled: false
    path: "/kchat"
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

- Added a top-level master switch for newly generated configs. New `config.yml` files start disabled so KOKOTO WebChat only creates configuration files until the administrator reviews settings and opts in.
- Existing configs without the `enabled` key are treated as enabled for upgrade compatibility.

```yml
# Master switch for KOKOTO WebChat.
# New generated configs default to false so the plugin creates config.yml first
# without starting web/chat services or cleanup tasks. /kchat reload remains available.
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
- Added `/kchat dm <player> <message>` and `/kchat dm list` for game-side direct message sending and unread/thread summary checks.
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
  # Allow sending DMs from /kchat dm in game.
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
- Because 4.2.0 and 4.3.0 include large configuration changes, if manual merging is difficult, stop the server, back up and delete `plugins/KOKOTO-WebChat/config.yml`, start the server once to regenerate it, and then reapply your custom settings manually.
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
* Made `adapters.bluemap.api-base-url` the primary public API base for reverse-proxy deployments.
* Made empty `frontend.standalone.api-base-url`, `upload.public-base-url`, and `emoji.public-base-url` consistently follow the active public API base.
* Kept compatibility with explicit legacy values such as `/chat/api`, `/chat/api/uploads`, and `/chat/api/emojis`.
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
* Improved behavior when KOKOTO WebChat is served from non-root API paths.

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
* Mirrored enabled web command execution notices to online Minecraft players without adding a second console log path.
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
