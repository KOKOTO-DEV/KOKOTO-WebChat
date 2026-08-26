# KOKOTO WebChat

## BlueMapWebChat → KOKOTO WebChat transition

**BlueMapWebChat has been renamed to KOKOTO WebChat starting with 5.0.0.** This 5.0.0 release may initially remain on the existing BlueMapWebChat listing so 4.7.0 installations can discover the bridge upgrade. If this listing is later retired because a seamless rename is unavailable, the replacement KOKOTO WebChat address will be placed at the top of this page before future releases move there.

Existing BMWC 4.x server data/config can be imported by KWC 5.0.0; the original BMWC data directory is left untouched as a migration/rollback source.

KOKOTO WebChat is a server-side Minecraft web chat for **Bukkit/Paper/Spigot 1.18–26.2 (Java 17), Fabric exact targets 1.18.2–26.2, NeoForge exact targets 1.20.2–26.2, and Forge exact targets 1.18.2–26.2; mod-loader targets select Java 17/21/25 by Minecraft version**. It can run as a standalone web chat or embed into supported map frontends including BlueMap, squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, and Minecraft Overviewer. No client mod is required for the core chat features.

## What's new in 5.0.0

5.0.0 is the largest KWC/BMWC architecture expansion so far:

- **Bukkit/Paper/Spigot + Fabric + NeoForge + Forge** release matrix: 1 Bukkit artifact plus 16 Fabric, 12 NeoForge and 16 Forge exact-target artifacts
- **Map-independent deployment** across BlueMap, squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, Minecraft Overviewer, and a standalone web client
- **Unicode-aware moderation/content filtering** with Block/Mask/Replace, bulk word lists, custom rules, anti-evasion matching and Web Admin testing
- **Per-account UI profiles and notification preferences**, improved desktop/mobile notification separation, and administrator Discord keyword alerts
- **Long-history/rich-media scroll stability** using one deterministic virtual-scroll lifecycle for images, GIFs, video, audio, YouTube/iframes and link previews
- **Expanded multi-server DM/group/relay reliability**, delivery/read state, exact server+UUID routing and retry-safe persistence
- **ImageEmojis-Bero and SimpleNicks-Bero deployment guides** for shared emoji assets/resource-pack hosting and nickname display integration on Bukkit/Paper-family servers

## Highlights

- **5.0.0 multi-platform architecture**: shared behavior lives in `kwc-core`; Bukkit, Fabric, NeoForge, and Forge provide loader-specific server integration while reusing the same core and frontend/adapters
- **Two-way game ↔ web chat** with linked Minecraft accounts and optional guest chat
- **BlueMap addon + standalone page** with dark/light/high-contrast UI, PIP, draggable/resizable windows, search, replies, pins, and virtual scrolling
- **1:1 direct messages** with local and cross-server recipients, exact server+UUID routing, retry-safe delivery, unread/read state, and shared game/web threads
- **Group chat rooms** with public/private visibility, invitations, passwords, retention limits, unread counts, delivery retry protection, and member management
- **Signed server-to-server relay** for multi-server communities, including full-mesh or hub layouts, HMAC authentication, hop limits, deduplication, server badges, and source filters
- **DiscordSRV integration** plus Discord media caching and optional emoji-link augmentation
- **Uploads and rich previews** for images, video, audio, YouTube/Shorts, with optional TikTok and X/Twitter embeds
- **Deterministic long-history virtual scrolling** across images, GIFs, video, audio, YouTube/iframes and link previews, with scroll-anchor correction as media sizes settle
- **Unicode-aware content filtering** for public/group chat and optional DM with Block/Mask/Replace, bulk word lists, custom rules, anti-evasion matching and Web Admin testing
- **Custom emoji management** with multi-file administrator upload, plus optional **ImageEmojis-Bero** Bukkit/Paper integration using the same `plugins/KOKOTO-WebChat/emojis` tree; its resource-pack HTTP port remains a separate client-facing service
- **SimpleNicks-Bero nickname integration** on Bukkit/Paper-family servers through `player-display.mode: "display-name"`, while KWC retains the real linked account identity
- **Configurable message tokens** such as `:enter:` / `:newline:` / `:tab:` with administrator-localizable aliases and printable custom substitutions
- **Web Push / keyword notifications** and administrator update notifications
- **Per-account visual UI profiles** plus account-synced keyword/notification preferences for signed-in users; device-local window state and Push endpoints stay local
- **Administrator Discord keyword alerts** with KWC-owned matching/deduplication and DiscordSRV logical-channel selection
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

Fresh configurations start with the plugin master switch disabled so operators can review storage, authentication, uploads, retention, and web exposure before enabling it. During migration, the bundled `config.yml` is the only template: KWC creates a fresh current default and overlays existing operator values, so old comments/layout are replaced by the current bundled comments/layout. A fixed older-version config is backed up before reconstruction. `<version>_auto_migration` repeats this rebuild on startup/reload; exact `<version>` fixes the current-version config and prevents same-version rewriting. `config-reference-<version>.yml` is an administrator-readable exact default copy, not the migration source, and obsolete generated reference/migration files are cleaned up automatically. `/kchat reload` validates edited YAML before stopping live services; invalid YAML is rejected so the previous running configuration, UI language, and services remain active. Bundled UTF-8 starter filter-word lists for Korean, English, Japanese, and Simplified Chinese are initialized once when the starter-list marker is absent; existing or disabled files are preserved, and administrator deletions after initialization are not reversed.

For public deployments, placing BlueMap and KOKOTO WebChat behind HTTPS with Caddy or Nginx is recommended.

## Optional integrations

- BlueMap (Bukkit plus BlueMapAPI on Fabric/NeoForge and Forge 26.1.2/26.2)
- squaremap and Dynmap filesystem web adapters
- Pl3xMap on Bukkit/Paper-family and Fabric
- LiveAtlas, uNmINeD, and Minecraft Overviewer static-web adapters
- DiscordSRV on Bukkit
- ImageEmojis-Bero on Bukkit/Paper-family servers (shared KWC emoji path + runtime glyph resolution); general ImageEmojis operation follows the upstream plugin documentation
- SimpleNicks-Bero on Bukkit/Paper-family servers through Bukkit display names; general SimpleNicks operation follows the upstream plugin documentation
- Web Push notifications

See the bundled README and `docs/` directory for full configuration, reverse-proxy, relay, security, migration, and troubleshooting guides.

## AI assistance disclosure

Generative AI was used as a development assistant for code review, implementation and patching, documentation drafting, and multilingual translation. Project requirements, architecture/design decisions, integration, testing, compatibility verification, release validation, and final acceptance are human-directed and reviewed. AI-assisted output is reviewed and validated before inclusion.
