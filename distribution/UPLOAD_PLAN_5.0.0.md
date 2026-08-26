# KOKOTO WebChat 5.0.0 upload plan

This file is the final publication checklist for the 5.0.0 bridge release. Target KOKOTO WebChat URLs are **planned until the corresponding rename/new listing is live**.

## Publication order

1. Run `validate-release-windows.bat` from the final source and require `FINAL RELEASE BUILD PASS` with exactly 45 deployable JARs.
2. Perform the short real-server smoke test in `docs/RELEASE_CHECKLIST_*.md`.
3. Publish 5.0.0 on the existing **BlueMapWebChat** listing first so 4.7.0 clients can discover the bridge upgrade.
4. Update the existing page title/description with the BMWC → KWC transition notice.
5. Rename the existing project where a same-project transition is supported. Only advertise the new canonical URL after it is actually live.
6. If same-project transition is impossible, keep BMWC as the 5.0.0 bridge/retirement page and publish later versions from the new KWC listing, with explicit cross-links.

## Deployable artifacts

| Artifact | Loader / class | Minecraft | Java | Publication note |
| --- | --- | --- | --- | --- |
| `KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar` | Bukkit/Paper/Spigot | 1.18–26.2 | 17 | Existing BMWC Bukkit listing bridge artifact |
| `KOKOTO-WebChat-5.0.0-Fabric-1.18.2.jar` | Fabric | 1.18.2 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.19.2.jar` | Fabric | 1.19.2 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.19.4.jar` | Fabric | 1.19.4 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.20.1.jar` | Fabric | 1.20.1 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.20.2.jar` | Fabric | 1.20.2 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.20.4.jar` | Fabric | 1.20.4 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.20.6.jar` | Fabric | 1.20.6 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.21.1.jar` | Fabric | 1.21.1 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.21.3.jar` | Fabric | 1.21.3 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.21.4.jar` | Fabric | 1.21.4 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.21.5.jar` | Fabric | 1.21.5 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.21.8.jar` | Fabric | 1.21.8 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.21.10.jar` | Fabric | 1.21.10 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-1.21.11.jar` | Fabric | 1.21.11 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-26.1.2.jar` | Fabric | 26.1.2 | 25 | exact target |
| `KOKOTO-WebChat-5.0.0-Fabric-26.2.jar` | Fabric | 26.2 | 25 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.20.2.jar` | NeoForge | 1.20.2 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.20.4.jar` | NeoForge | 1.20.4 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.20.6.jar` | NeoForge | 1.20.6 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.21.1.jar` | NeoForge | 1.21.1 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.21.3.jar` | NeoForge | 1.21.3 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.21.4.jar` | NeoForge | 1.21.4 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.21.5.jar` | NeoForge | 1.21.5 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.21.8.jar` | NeoForge | 1.21.8 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.21.10.jar` | NeoForge | 1.21.10 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-1.21.11.jar` | NeoForge | 1.21.11 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-26.1.2.jar` | NeoForge | 26.1.2 | 25 | exact target |
| `KOKOTO-WebChat-5.0.0-NeoForge-26.2.jar` | NeoForge | 26.2 | 25 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.18.2.jar` | Forge | 1.18.2 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.19.2.jar` | Forge | 1.19.2 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.19.4.jar` | Forge | 1.19.4 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.20.1.jar` | Forge | 1.20.1 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.20.2.jar` | Forge | 1.20.2 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.20.4.jar` | Forge | 1.20.4 | 17 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.20.6.jar` | Forge | 1.20.6 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.21.1.jar` | Forge | 1.21.1 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.21.3.jar` | Forge | 1.21.3 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.21.4.jar` | Forge | 1.21.4 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.21.5.jar` | Forge | 1.21.5 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.21.8.jar` | Forge | 1.21.8 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.21.10.jar` | Forge | 1.21.10 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-1.21.11.jar` | Forge | 1.21.11 | 21 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-26.1.2.jar` | Forge | 26.1.2 | 25 | exact target |
| `KOKOTO-WebChat-5.0.0-Forge-26.2.jar` | Forge | 26.2 | 25 | exact target |

## Modrinth

- Preferred: keep/rename one project and upload loader-specific versions to the same KWC project.
- Existing bridge URL: `https://modrinth.com/plugin/bluemapwebchat`.
- Planned canonical URL: `https://modrinth.com/plugin/kokoto-webchat` after activation.
- Upload 5.0.0 to the legacy project **before** retiring/changing the old entry point.
- Set each Fabric/NeoForge/Forge version entry to the **exact Minecraft target of that JAR**. Do not combine the exact-target loader artifacts into a wider compatibility claim.
- Enable the required AI-content disclosure for code/text and keep the human-review disclosure in the description/README.

## CurseForge

- Existing bridge URL: `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`.
- It is currently a Bukkit Plugins project; therefore the Bukkit 5.0.0 bridge is the artifact that can be published there without assuming a project-class change.
- Confirm in the author UI/support whether Fabric/NeoForge/Forge artifacts can be represented correctly on the same project. If not, create a KOKOTO WebChat Mods listing for those loaders and cross-link it with the Bukkit project.
- Do not publish a planned KWC URL as a working link before the new/renamed listing is live.

## GitHub

- Existing: `https://github.com/KOKOTO-DEV/BlueMapWebChat`.
- Preferred final name: `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`.
- Rename the repository rather than creating an unrelated replacement where possible, then update local remotes.
- Do not recreate/reuse the old repository name after rename; preserving GitHub's old-name redirect is useful for existing links.

## Page copy

Use:
- `distribution/modrinth/DESCRIPTION.md` + `RELEASE_NOTES_5.0.0.md` + `SUMMARY.txt`
- `distribution/curseforge/DESCRIPTION.md` + `CHANGELOG_5.0.0.md` + `SUMMARY.txt`
- `distribution/BMWC_RETIREMENT_NOTICE.md` only **after** the replacement KWC page is live and `{KWC_PROJECT_URL}` has been replaced with the real URL.
- `distribution/GIT_COMMIT_MESSAGE_5.0.0.txt` for the release commit and `distribution/GITHUB_UPLOAD_MESSAGE_5.0.0.txt` for the release/upload title text.

## Final publish gate

Do not call 5.0.0 final until all of these are true:

- source/static/config/i18n/security/profile/admin-alert/clipboard validation PASS;
- final Windows 45-JAR validator/build-matrix PASS (1 Bukkit + 16 Fabric + 12 NeoForge + 16 Forge);
- short real-server smoke test PASS;
- actual project URLs have been substituted only after they are live;
- the BMWC bridge page remains discoverable during the transition.
