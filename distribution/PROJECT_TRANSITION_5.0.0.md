# KOKOTO WebChat 5.0.0 distribution transition plan

## Goal

Move the existing **BlueMapWebChat** audience to **KOKOTO WebChat** without breaking 4.7.0 update discovery or leaving old download pages ambiguous.

## Address map

| Service | Existing / transition address | Target canonical address |
| --- | --- | --- |
| GitHub | `https://github.com/KOKOTO-DEV/BlueMapWebChat` | `https://github.com/KOKOTO-DEV/KOKOTO-WebChat` |
| Modrinth | `https://modrinth.com/plugin/bluemapwebchat` | `https://modrinth.com/plugin/kokoto-webchat` |
| CurseForge | `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat` | `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat` |

The target Modrinth/CurseForge URLs are **planned names** until the corresponding listing rename/new project is actually approved and live. Do not present a target URL as an active download link before that point.

## Required publication order

1. Keep the existing BMWC listings active.
2. Update the existing listing title/description to explain that BlueMapWebChat is now KOKOTO WebChat.
3. Publish KOKOTO WebChat **5.0.0 on the existing BMWC listing first**. This is the bridge that the 4.7.0 update checker can discover.
4. Verify the 5.0.0 file, release notes and migration notice are visible from the legacy project page.
5. Rename/move the listing to KOKOTO WebChat if the platform supports a seamless same-project transition.
6. Only after the new canonical address is live, replace “planned” text with the actual KWC URL.
7. If seamless transition is impossible, create/approve the new KWC listing, then convert the BMWC listing into a retirement/migration page. Keep 5.0.0 visible on BMWC as the final bridge release; publish later releases on KWC.

## Why the BMWC 5.0.0 bridge is required

BlueMapWebChat 4.7.0 clients know the legacy Modrinth project. If the BMWC page is archived before a higher version is published there, those clients may never learn that KWC exists. Publishing 5.0.0 on the old listing first makes the transition discoverable without modifying already-installed 4.7.0 binaries.

KWC 5.0.0 uses the opposite direction for future-proofing: it queries `kokoto-webchat` first and falls back to `bluemapwebchat`. Therefore the same binary works before, during and after the project-listing transition.

The same lookup policy is active on Bukkit, Fabric, NeoForge, and Forge. In 5.0.0 the CurseForge button/link deliberately remains on the legacy BMWC Bukkit page, because that page is guaranteed to be the bridge/retirement entry point even if the future KWC CurseForge URL or project class changes. A later release can switch the CurseForge target after the replacement page is confirmed live.

## GitHub

Preferred action: rename `KOKOTO-DEV/BlueMapWebChat` to `KOKOTO-DEV/KOKOTO-WebChat`. GitHub documents automatic redirects for normal repository web URLs, issues, wiki, stars/followers and git clone/fetch/push traffic after a repository rename. Update local clones anyway:

```bash
git remote set-url origin https://github.com/KOKOTO-DEV/KOKOTO-WebChat.git
```

Do not create a new repository using the old `BlueMapWebChat` name after the rename, because doing so would destroy the old-name redirect.

## Modrinth

Preferred action: keep a **single KOKOTO WebChat project for all loaders**. Modrinth explicitly recommends one project with multiple loader-specific versions rather than separate projects. Keep the same existing project if possible, change its title to `KOKOTO WebChat`, and change the slug to `kokoto-webchat` only when the transition behavior is confirmed. Modrinth's project API allows project title and slug modification, but its public documentation does not guarantee that an old slug continues to redirect. Therefore the release plan must not rely on an undocumented old-slug redirect.

If changing the slug would break the legacy entry point, keep the old slug through the 5.0.0 bridge period or create a new KWC project and leave the old project as a retirement notice. Version uploads should declare the actual loader(s) for each artifact: Bukkit-family loaders for the Bukkit JAR, Fabric for each Fabric exact-target JAR, NeoForge for each NeoForge exact-target JAR, and Forge for each exact-target Forge JAR.

For the 5.0.0 project metadata, enable the required AI-content disclosure for code/text according to the platform UI/policy and retain the human-review disclosure from `AI_USAGE.md`.

## CurseForge

The existing BlueMapWebChat listing is currently a **Bukkit Plugins** project. CurseForge documentation states that project class can affect supported file types and CurseForge App support, so do **not** assume that Fabric/NeoForge/Forge mod JARs can be uploaded to that existing Bukkit Plugins listing.

Safe 5.0.0 procedure:

1. Rename/update the existing BMWC Bukkit project page to KOKOTO WebChat wording where allowed.
2. Publish the **Bukkit 5.0.0 bridge JAR** on that existing page first.
3. Before uploading Fabric/NeoForge/Forge artifacts, confirm through the author UI or CurseForge support whether the existing project can change class or accept the mod-loader files correctly.
4. If CurseForge cannot represent plugin + mod-loader artifacts in one project, keep the existing Bukkit project for Bukkit releases and create/approve a separate KOKOTO WebChat Mods listing for Fabric/NeoForge/Forge. Cross-link the two pages clearly.
5. Do not mark BMWC discontinued until the replacement page(s) are live. If a new KWC project is required, place the retirement/migration notice at the top of BMWC and provide the actual new address.

This CurseForge split, if required by project class, is a distribution-platform limitation only; it does not change KWC versioning or runtime identity.

## Copy-ready retirement notice

Use this only after the new KWC listing is live:

> **BlueMapWebChat has moved to KOKOTO WebChat.** BlueMapWebChat 4.x is no longer the active project line. Version 5.0.0 is the migration/bridge release. New releases are published under **KOKOTO WebChat**. Existing BMWC 4.x server data can be migrated by KWC 5.0.0; keep a backup of the old data directory during the upgrade.

Then add the actual new project URL directly below the notice.

## Do not do

- Do not delete/archive the BMWC project before 5.0.0 is published there.
- Do not publish a dead `kokoto-webchat` link as if it were already live.
- Do not reuse the old GitHub repository name after a rename.
- Do not publish 5.0.0 under both BMWC and a separate KWC listing with unrelated version histories unless the platform cannot support a same-project transition; if two listings are unavoidable, make BMWC explicitly a bridge/retirement listing.
