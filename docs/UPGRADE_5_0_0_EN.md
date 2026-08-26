# KOKOTO WebChat 5.0.0 Upgrade

5.0.0 is the major release that completes the **BlueMapWebChat (BMWC) → KOKOTO WebChat (KWC)** identity change and consolidates the development work after the previously released 4.7.0.

## Distribution transition

The safest rollout is to publish **5.0.0 on the existing BlueMapWebChat project listings first**. BlueMapWebChat 4.7.0 update checkers know the legacy Modrinth project, so publishing the bridge release there lets existing installations discover 5.0.0 normally.

Legacy/current transition entry points:

- Modrinth: `https://modrinth.com/plugin/bluemapwebchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`
- GitHub: `https://github.com/KOKOTO-DEV/BlueMapWebChat`

Target canonical KOKOTO WebChat addresses after activation:

- Modrinth: `https://modrinth.com/plugin/kokoto-webchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`
- GitHub: `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`

KWC 5.0.0 already checks the new `kokoto-webchat` Modrinth project first and falls back to `bluemapwebchat`, so it works both before and after the listing transition. If a platform cannot preserve the old listing as the same project, keep the BMWC page as a retirement/migration notice and point it to the new KWC page. Do not retire the BMWC listing before 5.0.0 has been visible there long enough for 4.7.0 users to discover the bridge release.

For GitHub, renaming the repository to `KOKOTO-WebChat` is preferred; GitHub redirects normal repository web/git URLs from the old repository name. Update local remotes to the new URL after the rename.

## Major changes from 4.7.0

- Canonical identity changed to **KOKOTO WebChat**: `/kchat` (`/kc`), `kwc.*`, `plugins/KOKOTO-WebChat` or `config/KOKOTO-WebChat`, `dev.kokoto.webchat`, and `kwc-*` modules.
- Added shared multi-platform core and server platforms for Bukkit/Paper/Spigot, 16 exact Fabric targets, 12 exact NeoForge targets, and 16 exact Forge targets.
- Added BlueMap, squaremap, Dynmap, Pl3xMap, LiveAtlas, uNmINeD and Minecraft Overviewer adapter architecture; supported adapter combinations vary by loader as documented in the README.
- Standalone is enabled by default and the canonical public prefix is `/chat`; API becomes `/chat/api` with the bundled reverse-proxy layout.
- Added Unicode content filtering, starter UTF-8 filter lists, rule editing/testing, mask/replace modes and anti-evasion matching.
- Added server-side visual UI profiles for signed-in accounts, strict JSON import/export, and account-synced keyword/notification preferences.
- Added same-device Web Push/live-notification deduplication.
- Added administrator Discord keyword alerts. KWC owns detection/format/deduplication; DiscordSRV supplies its authenticated JDA connection and channel mapping only.
- `update-check.enabled` now works on Fabric, NeoForge and Forge as well as Bukkit, with KWC-first / BMWC-fallback Modrinth lookup and `kwc.update.notify` login notices.
- Added `upload.filename-mode: random|original`; original-mode serving/reference tracking now supports safe Unicode/spaces/`~`/`+`/`%`, and Windows clipboard 8.3 aliases are handled safely.
- Replaced `discordsrv.game-to-discord` with `discordsrv.game-relay-mode: discordsrv|kwc` and fixed game-origin registered emoji processing on the native DiscordSRV path.
- Improved server relay peer/backoff behavior, cross-server DM delivery/read acknowledgements and cross-platform `/kchat` command consistency.
- Added security hardening without changing normal UX: Bearer authentication for frontend API calls, one-time SSE stream tickets, request-body limits, bounded HTTP workers, administrator-IP enforcement, Web Push SSRF defenses, safer Discord mentions/CDN redirects and stricter iframe message-source checks.

## Platform support

- Bukkit/Paper/Spigot: one `KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar`, Java 17 bytecode, declared Minecraft range 1.18–26.2.
- Fabric exact targets: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2` using target-selected JDK 17/21/25.
- NeoForge exact targets: `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2` using target-selected JDK 17/21/25.
- Forge exact targets: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2` using target-selected JDK 17/21/25.

## BlueMapWebChat data migration

On first KWC start, an existing Bukkit `plugins/BlueMapWebChat` installation can be used as one-time migration input only when `plugins/KOKOTO-WebChat` has no existing data files. KWC never merges BMWC data into an established KWC directory, even if `.legacy-import-complete` is deleted manually. The old directory is left untouched as a rollback/migration source; after it is removed, KWC automatically removes the temporary `.legacy-import-complete` marker on startup or `/kchat reload`.

Legacy configuration names such as `web-addon.*` and `standalone-web.*` are converted to `adapters.bluemap.*` and `frontend.standalone.*`. `/bmchat`, `/bluemapchat`, `/bmc` and `/kwc` are not registered as command aliases in 5.0.0. Legacy `bluemapwebchat.*` permission grants remain a runtime compatibility fallback. Relay Protocol v1 intentionally keeps `X-BMWC-Relay-*` wire headers for interoperability with existing BMWC peers.

## BMWC → KWC web-path migration

Standard BMWC `/bmwc/api` and `/bmwc/chat` reverse-proxy layouts are not retained as KWC defaults. The default public prefix is now `/chat`, giving standalone `/chat` and API `/chat/api`. KWC can normalize standard BMWC URL values in migrated config, but it cannot rewrite external Caddy/nginx configuration; update those rules manually. See `CADDY_HTTPS_EN.md` and `NGINX_HTTPS_EN.md`.

## Configuration changes

A structural comparison of the bundled 4.7.0 and 5.0.0 references contains **79 added paths, 14 removed paths and 2 changed existing values**. The main renamed/removed groups are `web-addon.* → adapters.bluemap.*`, `standalone-web.* → frontend.standalone.*`, `discordsrv.game-to-discord* → discordsrv.game-relay-*`, and removal of `ui.show-login-only-when-hidden`. New groups include the additional map adapters, `content-filter.*`, `ui.user-profiles.*`, `admin-alerts.discord.*`, `http.public-prefix`, relay forwarding control and `upload.filename-mode`.

When a real version upgrade is detected, KWC backs up a fixed older-version config, creates a fresh config from the current bundled `config.yml`, overlays the existing configured values, discards old comments/layout, and writes:

```yaml
config-version: "5.0.0_auto_migration"
```

While `_auto_migration` remains, startup/reload repeats the bundled-default rebuild with current values overlaid. `config-reference-5.0.0.yml` is only the administrator-readable exact bundled default copy. Use exact `config-version: "5.0.0"` to fix the same-version config and stop rewriting it.

## Security/behavior compatibility

Normal users should not notice the security hardening. Login, chat, DM/group chat, uploads, profiles, Push and administration use the same UI. Only previously unsafe/invalid cases are rejected: administrator access from disallowed IPs, unsafe Push endpoints, oversized malformed requests, unintended Discord mentions and path-invalid uploads.

Public deployments should use HTTPS. Direct-IP operation remains supported for normal KWC use, but browser features that require a secure context must follow browser security rules.

## Release acceptance

A final release candidate is accepted only after `validate-release-windows.bat` finishes with `FINAL RELEASE BUILD PASS`, exactly 45 deployable JARs are collected, static/config/i18n/document checks pass, and the final candidate has been smoke-tested for login, public chat, upload/clipboard upload, DM/group, relay and enabled map/Discord integrations.
