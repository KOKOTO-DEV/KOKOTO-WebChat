# KOKOTO WebChat 5.0.0 distribution metadata

## GitHub upload message

`Release 5.0.0: complete BlueMapWebChat → KOKOTO WebChat migration, multi-platform core/adapters, profiles/alerts/filtering, security hardening, and upload fixes`

## Version

- KWC version: `5.0.0`
- Bukkit/Paper/Spigot: Minecraft `1.18–26.2`, Java `17`, `kwc-platform-bukkit/target/KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar`
- Fabric: 16 exact targets from Minecraft `1.18.2–26.2`, target-selected JDK `17/21/25`, `kwc-platform-fabric/targets/<version>/build/libs/KOKOTO-WebChat-5.0.0-Fabric-<version>.jar`
- NeoForge: 12 exact targets from Minecraft `1.20.2–26.2`, target-selected JDK `17/21/25`, `kwc-platform-neoforge/targets/<version>/build/libs/KOKOTO-WebChat-5.0.0-NeoForge-<version>.jar`
- Forge: 16 exact targets from Minecraft `1.18.2–26.2`, target-selected JDK `17/21/25`, `kwc-platform-forge/targets/<version>/build/libs/KOKOTO-WebChat-5.0.0-Forge-<version>.jar`

## Canonical identity

- Product: `KOKOTO WebChat`
- Bukkit plugin: `KOKOTO-WebChat`
- Command: `/kchat`
- Permissions: `kwc.*`
- Data directory: `plugins/KOKOTO-WebChat`
- Java package: `dev.kokoto.webchat`
- Modules: `kwc-core`, `kwc-standalone-frontend`, `kwc-adapter-bluemap`, `kwc-adapter-bluemap-api`, `kwc-adapter-squaremap`, `kwc-adapter-dynmap`, `kwc-adapter-pl3xmap`, `kwc-adapter-liveatlas`, `kwc-adapter-unmined`, `kwc-adapter-overviewer`, plus Bukkit/Fabric/NeoForge/Forge platform modules

BlueMapWebChat 4.x data and config are accepted as one-time migration input. The canonical command is `/kchat` with `/kc`; old `/bmchat`, `/bluemapchat`, `/bmc`, and `/kwc` command aliases are not registered. Relay Protocol v1 keeps its existing `X-BMWC-Relay-*` wire headers so old BMWC relay endpoints remain interoperable.

## Architecture

`kwc-core` owns loader-neutral persistence, relay, HTTP/SSE, Web Push and endpoint orchestration. `kwc-standalone-frontend` owns the map-independent standalone frontend. `kwc-adapter-bluemap` owns shared BlueMap assets and Bukkit `webapp.conf` integration, while `kwc-adapter-bluemap-api` provides Fabric/NeoForge and Forge 26.1.2/26.2 BlueMapAPI registration. `kwc-adapter-squaremap`, `kwc-adapter-dynmap`, `kwc-adapter-pl3xmap`, `kwc-adapter-liveatlas`, `kwc-adapter-unmined`, and `kwc-adapter-overviewer` own filesystem web integrations. Pl3xMap is enabled on Bukkit/Paper-family and Fabric; current Pl3xMap 26.2 does not publish a NeoForge build. Platform modules provide Bukkit, Fabric, NeoForge, and Forge lifecycle/native integration.

## Config

Canonical map settings include `adapters.bluemap.*`, `adapters.squaremap.*`, `adapters.dynmap.*`, `adapters.pl3xmap.*`, `adapters.liveatlas.*`, `adapters.unmined.*`, and `adapters.overviewer.*`; standalone settings are `frontend.standalone.*`. New generated config contains no BMWC setting names. Legacy `web-addon.*` and `standalone-web.*` keys are read only by migration.
## Final operations controls

5.0.0 includes the shared Unicode content filter, Web Admin Filter/Settings panels, `/kchat filter` and `/kchat settings`, existing-session lifetime recalculation, and optional original Unicode upload filenames via `upload.filename-mode: original`. Signed-in users also gain multiple server-side visual UI profiles and account-level keyword/notification preferences, with device-local window/Web Push state preserved and duplicate same-device OS notifications suppressed. Administrator Discord keyword alerts are configured in Web Admin; KWC owns matching/mentions/deduplication while DiscordSRV supplies only its authenticated JDA connection and logical channel mapping.
Configuration migration in 5.0.0 uses the bundled `config.yml` as the only template: KWC creates a fresh current default and overlays existing operator values, discarding legacy comments/order/whitespace/indentation. A fixed older-version config is backed up before reconstruction. `5.0.0_auto_migration` repeats this bundled-default rebuild on startup/reload; exact `5.0.0` leaves the same-version config untouched. `config-reference-5.0.0.yml` is only the administrator-readable exact default copy, and obsolete generated reference/migration/upgrade files are removed automatically. Bundled UTF-8 starter filter lists for Korean, English, Japanese, and Simplified Chinese are initialized once when the starter-list marker is absent. Existing/disabled list files are never overwritten, and lists deleted after initialization are not recreated.


BlueMapWebChat 4.x filesystem import is first-run-only: it runs only when `plugins/KOKOTO-WebChat` contains no existing data files. Existing KWC data is never merged with BMWC data even if `.legacy-import-complete` is manually removed. The marker is temporary migration state and is automatically removed once `plugins/BlueMapWebChat` no longer exists.

## uNmINeD static export

5.0.0 includes a filesystem-only `adapters.unmined` integration for existing uNmINeD web exports. Current `index.html` and legacy `unmined.index.html` are marker-checked before patching; arbitrary/external export roots should be supplied through `web-root`.

## Minecraft Overviewer static map

5.0.0 includes a filesystem-only `adapters.overviewer` integration for existing Minecraft Overviewer `outputdir` web maps. KWC requires Overviewer-specific generated markers/assets before patching `index.html`, rejects unrelated Leaflet pages, and manages only its marked block plus configured addon directory. Overviewer renders can regenerate the page, so `/kchat reload` restores the integration.
## Project-listing transition

5.0.0 is the bridge release from BlueMapWebChat to KOKOTO WebChat. Publish it on the existing BMWC listing first so 4.7.0 installations can discover the upgrade. Legacy transition addresses are `https://modrinth.com/plugin/bluemapwebchat`, `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`, and `https://github.com/KOKOTO-DEV/BlueMapWebChat`. Target names after activation are `https://modrinth.com/plugin/kokoto-webchat`, `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`, and `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`. See `PROJECT_TRANSITION_5.0.0.md` for the exact rollout and retirement procedure.

If same-project rename is unavailable, keep the BMWC listing as a retirement/migration notice and publish future versions from the new KWC listing only after it is live.

Modrinth should use one KWC project with loader-specific versions. CurseForge requires an extra class check: the current BMWC page is a Bukkit Plugins project, so publish the Bukkit bridge there first and verify project-class/file compatibility before attempting Fabric/NeoForge/Forge uploads. If CurseForge cannot host both plugin and mod-loader artifacts in one project, cross-link the existing Bukkit listing and a separate KWC Mods listing.

## Release acceptance

- Config 4.7.0 → 5.0.0 structural delta: 79 added paths, 14 removed paths, 2 changed existing values.
- Four bundled UI languages must have an identical keyset.
- Static/security/profile/admin-alert/clipboard validation must pass.
- Final Windows acceptance must build Bukkit + 16 Fabric exact targets + 12 NeoForge exact targets + 16 Forge exact targets and finish with `FINAL RELEASE BUILD PASS` / 45 deployable JARs.
- Distribution pages and Wiki must not contain development-stage “rebrand later” language.

