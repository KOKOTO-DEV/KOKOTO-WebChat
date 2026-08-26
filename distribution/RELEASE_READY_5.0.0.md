# KOKOTO WebChat 5.0.0 release-ready summary

This file is the distribution handoff summary for the final 5.0.0 candidate.

## Build matrix

The release matrix is 45 deployable server artifacts:

- 1 Bukkit/Paper/Spigot artifact: Minecraft 1.18–26.2, Java 17 bytecode.
- 16 Fabric exact-target artifacts: 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2.
- 12 NeoForge exact-target artifacts: 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2.
- 16 Forge exact-target artifacts: the same 16 Minecraft targets as Fabric.

Mod-loader Java target selection is 17 for the older target group, 21 for the middle target group, and 25 for 26.1.2/26.2. Each Fabric/NeoForge/Forge file must be published with its exact Minecraft target metadata.

## 4.7.0 → 5.0.0 release appeal

The strongest release points are:

1. KOKOTO WebChat is no longer BlueMap-only: Bukkit/Paper/Spigot, Fabric, NeoForge and Forge builds share the same web-chat core.
2. Map/hosting coverage now includes BlueMap, squaremap, Dynmap, Pl3xMap where supported, LiveAtlas, uNmINeD, Minecraft Overviewer, and standalone web operation.
3. Multi-server public relay, DM and group-chat delivery/read state are expanded and hardened.
4. Unicode-aware Block/Mask/Replace content filtering, bulk filter lists, custom rules, anti-evasion matching and Web Admin tests are included.
5. Per-account UI profiles and notification preferences, mobile Web Push behavior, desktop notification separation, and administrator Discord keyword alerts are included.
6. Long-history virtual scrolling is stabilized across images, GIFs, video, audio, YouTube/iframes and link previews without media-specific message ordering exceptions.
7. Unicode/original-filename upload handling, clipboard filename reliability, migration/config reconciliation and security controls are substantially expanded.
8. Bukkit/Paper-family companion integration is documented for ImageEmojis-Bero and SimpleNicks-Bero.

## Existing core features to introduce on project pages

- Game ↔ Web two-way public chat.
- Linked Minecraft accounts, password login, local administrators and optional guest chat.
- Search, replies, pinned messages, time display controls, PIP, drag/resize and visual themes.
- Local and cross-server direct messages.
- Group chats with invitations/passwords/visibility/member management/unread state.
- Signed multi-server relay with HMAC, deduplication, hop limits and origin/server labeling.
- File/clipboard uploads and image/video/audio/YouTube/Shorts previews, with optional TikTok/X embeds.
- DiscordSRV relay/media integration.
- Custom emoji administration.
- Moderation, session control, guest/IP mutes and optional audited private-chat read-only access for explicitly allowlisted super administrators.
- English, Korean, Japanese and Simplified Chinese UI languages.

## Companion-plugin documentation

### ImageEmojis-Bero

KWC documents the KOKOTO-DEV fork for the tested integration, while ordinary ImageEmojis installation/operation remains owned by the upstream project.

KWC-facing settings include:

```yaml
serverIp: yourdomain
webServerPort: 5000
emojisFolder: /KOKOTO-WebChat/emojis
enforcementPolicy: REQUIRED
replaceInCommands: true
templateFormat: ':<emoji>:'
```

`serverIp:webServerPort` is the separate ImageEmojis resource-pack HTTP service and must be reachable by Minecraft clients. The shared emoji path resolves to `plugins/KOKOTO-WebChat/emojis`.

### SimpleNicks-Bero

General SimpleNicks installation/operation remains in the upstream project. The KWC-specific setting is:

```yaml
player-display:
  mode: "display-name"
```

This uses the Bukkit display name produced by the nickname plugin while KWC retains the linked username/UUID as the real account identity.

## Config/i18n/document state

- `config.yml`, `config-baselines/config-5.0.0.yml`, and `distribution/config-reference-5.0.0.yml` are byte-identical.
- Current bundled UI languages have identical key sets and matching placeholders.
- Current Configuration/User Manual/README/Wiki and release copy point to the KWC path `/KOKOTO-WebChat/emojis` rather than the obsolete space-containing path.
- Release pages describe Fabric/NeoForge/Forge as exact-target matrices rather than a 26.2-only build.
- The final upload plan contains exactly 1 + 16 + 12 + 16 artifact rows.

## Publication copy

- Modrinth description: `distribution/modrinth/DESCRIPTION.md`
- Modrinth 5.0.0 notes: `distribution/modrinth/RELEASE_NOTES_5.0.0.md`
- Modrinth summary: `distribution/modrinth/SUMMARY.txt`
- CurseForge description: `distribution/curseforge/DESCRIPTION.md`
- CurseForge 5.0.0 changelog: `distribution/curseforge/CHANGELOG_5.0.0.md`
- CurseForge summary: `distribution/curseforge/SUMMARY.txt`
- Git commit message: `distribution/GIT_COMMIT_MESSAGE_5.0.0.txt`
- GitHub release/upload title: `distribution/GITHUB_UPLOAD_MESSAGE_5.0.0.txt`
- Full publication order and all 45 artifact names: `distribution/UPLOAD_PLAN_5.0.0.md`

## Transition rule

5.0.0 remains the bridge from BlueMapWebChat to KOKOTO WebChat. Publish it through the existing BMWC listing first so 4.7.0 installations can discover the update. Only advertise a new canonical Modrinth/CurseForge/GitHub address after that address is actually live; if a same-project rename is impossible, retain the BMWC page as a retirement/migration entry point and cross-link the replacement KWC listing.
