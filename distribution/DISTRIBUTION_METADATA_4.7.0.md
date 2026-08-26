# KOKOTO WebChat 4.7.0 distribution metadata

## Modrinth summary

BlueMap/standalone web chat for Minecraft 1.18–26.2 with DMs, groups, relay, DiscordSRV, media, multi-upload emoji, message tokens, notifications, and admin tools.

## CurseForge summary

BlueMap/standalone web chat for Minecraft 1.18–26.2 with DMs, groups, multi-server relay, DiscordSRV, uploads, multi-upload emoji, message tokens, notifications, and admin tools.

## GitHub upload message

`Release 4.7.0: add 1.18–26.2 compatibility, multi-file emoji upload, configurable message tokens, and config migration/layout improvements`

## Modrinth description

# KOKOTO WebChat

KOKOTO WebChat is a server-side web chat plugin for Bukkit/Paper/Spigot-compatible Minecraft servers. **KOKOTO WebChat 4.7.0 supports Minecraft 1.18 through 26.2 and requires Java 17.** Run it inside BlueMap, as a standalone web chat page, or use both at the same time. No client mod is required for the core chat features.

## Highlights

- **Two-way game ↔ web chat** with linked Minecraft accounts and optional guest chat
- **BlueMap addon + standalone `/chat` page** with dark/light/high-contrast UI, PIP, draggable/resizable windows, search, replies, pins, and virtual scrolling
- **1:1 direct messages** with local and cross-server recipients, exact server+UUID routing, retry-safe delivery, unread/read state, and shared game/web threads
- **Group chat rooms** with public/private visibility, invitations, passwords, retention limits, unread counts, delivery retry protection, and member management
- **Signed server-to-server relay** for multi-server communities, including full-mesh or hub layouts, HMAC authentication, hop limits, deduplication, server badges, and source filters
- **DiscordSRV integration** plus Discord media caching and optional emoji-link augmentation
- **Uploads and rich previews** for images, video, audio, YouTube/Shorts, with optional TikTok and X/Twitter embeds
- **Custom emoji management** with multi-file administrator upload using the same immediate picker flow as normal chat uploads, plus optional ImageEmojis-Bero compatibility without a hard dependency
- **Configurable message tokens** such as `:enter:` / `:newline:` / `:tab:` with English defaults, administrator-localizable aliases, and printable custom substitutions
- **Web Push / keyword notifications** and administrator update notifications
- **Administration and moderation tools** for message hiding, pins, guest/IP mutes, session revocation, custom emoji management, and private-chat metadata
- **Optional read-only DM and group-chat body audit** for explicitly allowlisted super administrators; both are disabled by default and every audit page read is logged
- Built-in UI languages: **English, Korean, Japanese, Simplified Chinese**

## Private messaging and delivery reliability

Cross-server DM targets are identified by **server ID + player UUID**, so a local player cannot be confused with a same-name player on another server. Remote messages stay pending until the destination confirms storage. Failed or uncertain sends can be retried with persistent relay/client message IDs without duplicating sender or receiver records.

DM and group-chat message state is shown compactly beside the timestamp. Normal successful delivery is silent; pending or failed delivery is shown only when needed. DM messages use a short unread label until the recipient reads them, then show `✓`. Group messages show the number of recipients who have not read the message and change to `✓` when everyone has read it.

## Multi-server relay

The optional relay supports public chat and private DM routing between multiple KOKOTO WebChat servers. Relay requests are HMAC-SHA256 signed and include replay protection, timestamp validation, relay-ID deduplication, hop limits, and origin tracking. Direct and hub topologies are supported.

For cross-server DM delivery/read acknowledgements, every participating server should run the same current KOKOTO WebChat release.

## Administrator private-chat audit

Private message bodies are not automatically exposed to normal administrators. Optional audit access requires an exact Minecraft name/UUID in `private-chat-super-admins` **and** the corresponding feature switch:

```yaml
direct-message:
  admin-audit:
    enabled: false

group-chat:
  admin-audit:
    enabled: false
```

Audit views are read-only. Group audit does not join the room or affect read/unread state. Every audit page read records the actor and pagination metadata without copying message bodies into the audit log.

## Deployment

Fresh configurations start with the plugin master switch disabled so operators can review storage, authentication, uploads, retention, and web exposure before enabling it. Existing setting values are not overwritten; known top-level config blocks are reordered to the bundled layout on startup/reload while block text, values, and custom comments are preserved. When the configuration version marker is old or missing, KOKOTO WebChat creates a copy-ready `config-migration-<version>.yml` fragment instead.

For public deployments, placing BlueMap and KOKOTO WebChat behind HTTPS with Caddy or Nginx is recommended.

## Optional integrations

- BlueMap
- DiscordSRV
- ImageEmojis / ImageEmojis-Bero compatibility
- Web Push notifications

See the bundled README and `docs/` directory for full configuration, reverse-proxy, relay, security, migration, and troubleshooting guides.

## CurseForge description

# KOKOTO WebChat

KOKOTO WebChat is a server-side web chat plugin for Bukkit/Paper/Spigot-compatible Minecraft servers. **KOKOTO WebChat 4.7.0 supports Minecraft 1.18 through 26.2 and requires Java 17.** Run it inside BlueMap, as a standalone web chat page, or use both at the same time. No client mod is required for the core chat features.

## Main features

- **Two-way game ↔ web chat** with linked Minecraft accounts and optional guest chat
- **BlueMap addon + standalone `/chat` page** with dark/light/high-contrast UI, PIP, draggable/resizable windows, search, replies, pins, and virtual scrolling
- **1:1 direct messages** with local and cross-server recipients, exact server+UUID routing, retry-safe delivery, unread/read state, and shared game/web threads
- **Group chat rooms** with public/private visibility, invitations, passwords, retention limits, unread counts, delivery retry protection, and member management
- **Signed server-to-server relay** for multi-server communities, including full-mesh or hub layouts, HMAC authentication, hop limits, deduplication, server badges, and source filters
- **DiscordSRV integration** plus Discord media caching and optional emoji-link augmentation
- **Uploads and rich previews** for images, video, audio, YouTube/Shorts, with optional TikTok and X/Twitter embeds
- **Custom emoji management** with multi-file administrator upload using the same immediate picker flow as normal chat uploads, plus optional ImageEmojis-Bero compatibility without a hard dependency
- **Configurable message tokens** such as `:enter:` / `:newline:` / `:tab:` with English defaults, administrator-localizable aliases, and printable custom substitutions
- **Web Push / keyword notifications** and administrator update notifications
- **Administration and moderation tools** for message hiding, pins, guest/IP mutes, session revocation, custom emoji management, and private-chat metadata
- **Optional read-only DM and group-chat body audit** for explicitly allowlisted super administrators; both are disabled by default and every audit page read is logged
- Built-in UI languages: **English, Korean, Japanese, Simplified Chinese**

## Private messaging and delivery reliability

Cross-server DM targets are identified by **server ID + player UUID**, so a local player cannot be confused with a same-name player on another server. Remote messages stay pending until the destination confirms storage. Failed or uncertain sends can be retried with persistent relay/client message IDs without duplicating sender or receiver records.

DM and group-chat message state is shown compactly beside the timestamp. Normal successful delivery is silent; pending or failed delivery is shown only when needed. DM messages use a short unread label until the recipient reads them, then show `✓`. Group messages show the number of recipients who have not read the message and change to `✓` when everyone has read it.

## Multi-server relay

The optional relay supports public chat and private DM routing between multiple KOKOTO WebChat servers. Relay requests are HMAC-SHA256 signed and include replay protection, timestamp validation, relay-ID deduplication, hop limits, and origin tracking. Direct and hub topologies are supported.

For cross-server DM delivery/read acknowledgements, every participating server should run the same current KOKOTO WebChat release.

## Administrator private-chat audit

Private message bodies are not automatically exposed to normal administrators. Optional audit access requires an exact Minecraft name/UUID in `private-chat-super-admins` **and** the corresponding feature switch:

```yaml
direct-message:
  admin-audit:
    enabled: false

group-chat:
  admin-audit:
    enabled: false
```

Audit views are read-only. Group audit does not join the room or affect read/unread state. Every audit page read records the actor and pagination metadata without copying message bodies into the audit log.

## Deployment

Fresh configurations start with the plugin master switch disabled so operators can review storage, authentication, uploads, retention, and web exposure before enabling it. Existing setting values are not overwritten; known top-level config blocks are reordered to the bundled layout on startup/reload while block text, values, and custom comments are preserved. When the configuration version marker is old or missing, KOKOTO WebChat creates a copy-ready `config-migration-<version>.yml` fragment instead.

For public deployments, placing BlueMap and KOKOTO WebChat behind HTTPS with Caddy or Nginx is recommended.

## Optional integrations

- BlueMap
- DiscordSRV
- ImageEmojis / ImageEmojis-Bero compatibility
- Web Push notifications

See the bundled README and `docs/` folder for full configuration, reverse-proxy, relay, security, migration, and troubleshooting guides.

## 4.7.0 release notes

# KOKOTO WebChat 4.7.0

## Added

- Administrator custom-emoji **multi-file upload** now uses the same picker handoff as normal chat file upload.
- Added configurable `:token:` message substitutions with English defaults for newline, blank-line, indentation, and printable custom replacements; administrators can localize aliases.
- Configured newline/blank-line tokens are delivered to Minecraft as explicit chat lines while ordinary CR/LF remains flattened; relayed game rendering needs the same 4.7.0 token-line delivery support on the receiving server.
- The Upload control opens a hidden multi-file input; selecting files immediately clears the native input and starts sequential upload without a second confirmation step.
- Upload progress and active-transfer cancel remain available; per-file type/size checks, total-storage limits, unique-name handling, audit logging, and PNG-sidecar generation remain unchanged. Individual failures do not stop later files, and large failure summaries are bounded.

## Compatibility

- Lowered the declared Bukkit/Spigot API baseline from Minecraft 1.21 to **1.18**.
- Java requirement remains **Java 17**.
- Maven now builds against `spigot-api:1.18.2-R0.1-SNAPSHOT`.
- Conservative supported Minecraft range for this release: **1.18 through 26.2**.
- Paper `AsyncChatEvent` support remains reflection-detected with Bukkit `AsyncPlayerChatEvent` as the hard-linked fallback.
- Minecraft 1.17 and older are intentionally not claimed by 4.7.0.

## Configuration

4.7.0 adds the `message-tokens` section. Existing defaults outside this new section are unchanged. The configuration review marker becomes:

```yaml
config-version: "4.7.0"
```

Existing setting values are not overwritten. On startup/reload, known top-level config blocks are reordered to the bundled 4.7.0 layout while each block's current text, values, and custom comments are preserved; unknown top-level blocks remain last in their original order. KOKOTO WebChat also writes `config-reference-4.7.0.yml` as a complete copy of the bundled 4.7.0 default `config.yml` with all comments, regardless of the installed config's age. When review is required, `config-migration-4.7.0.yml` remains the concise missing/changed-settings fragment and now retains empty maps such as `message-tokens.custom: {}` when they are missing. The migration file ends with a comment-only current-vs-reference text diff that omits unchanged lines, separates `Line`/`Lines` metadata from the differing text, prefixes each differing source line directly with `#` to preserve original YAML indentation, and shows insertion locations for reference-only blocks.
 `/kwc reload` validates the edited YAML before stopping live services; invalid YAML is rejected so the previous running configuration, UI language, and services remain active. YAML list settings accept both inline `[a, b]` and block `- a` forms, and indentation must use normal ASCII spaces rather than tabs or full-width spaces.