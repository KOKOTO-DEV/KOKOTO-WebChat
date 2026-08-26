# KOKOTO WebChat 5.0.0

KOKOTO WebChat 5.0.0 is the major successor to BlueMapWebChat 4.7.0. It expands the project from a BlueMap-focused Bukkit plugin into a multi-platform, multi-map Minecraft web chat system.

## Highlights

- **KOKOTO WebChat identity:** canonical `/kchat` and `/kc` commands, default `/chat` web path, and first-run migration from BlueMapWebChat 4.x while preserving the original BMWC data directory.
- **Multi-platform builds:** Bukkit/Paper/Spigot, 16 Fabric exact targets, 12 NeoForge exact targets, and 16 Forge exact targets.
- **More map/frontends:** BlueMap, squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD, Minecraft Overviewer, plus standalone WebChat operation.
- **Multi-server improvements:** stronger public relay, cross-server DM/group chat, remote-user handling, message delivery/read state, and relay failure/backoff behavior.
- **Content filtering:** Unicode-aware Block/Mask/Replace rules, UTF-8 word lists, custom rules, anti-evasion matching, Web Admin editing/testing, and corrected Hangul/jamo matching.
- **User profiles and notifications:** account-synced preferences, multiple visual profiles with validated import/export, keyword notifications, desktop/mobile notification routing, and administrator Discord keyword alerts.
- **Upload reliability:** optional safe Unicode original filenames, collision handling, and Windows clipboard filename fixes.
- **Emoji/nickname integration:** improved game/web/Discord emoji token handling plus deployment guides for ImageEmojis-Bero and SimpleNicks-Bero.
- **Stable long-history scrolling:** rich media no longer receives special DOM ordering treatment; images, GIFs, video, audio, YouTube/iframe embeds and link previews follow one deterministic message order.
- **Security and recovery:** history DB integrity checks plus tighter API/SSE/admin/Web Push/Discord/iframe validation.
- **Configuration migration:** operator values are preserved while 5.0.0 settings/comments/layout are reconciled from the bundled config; `_auto_migration` keeps reconciliation enabled and exact `5.0.0` stops same-version rewriting.

## Compatibility

- Bukkit/Paper/Spigot: Minecraft **1.18–26.2**
- Fabric: **16 exact targets**, Minecraft **1.18.2–26.2**
- NeoForge: **12 exact targets**, Minecraft **1.20.2–26.2**
- Forge: **16 exact targets**, Minecraft **1.18.2–26.2**
- Java **17 / 21 / 25** depending on the selected Minecraft target.

## Upgrading from BlueMapWebChat 4.x

5.0.0 is the bridge release for the BlueMapWebChat → KOKOTO WebChat transition. Back up your existing server data and review the migration/configuration guide before switching. KWC can import existing BMWC 4.x data on first run and leaves the original BMWC directory untouched for rollback/reference.

Existing reverse-proxy rules using `/bmwc` must be updated to the current `/chat` layout. The canonical Minecraft commands are now `/kchat` and `/kc`.
