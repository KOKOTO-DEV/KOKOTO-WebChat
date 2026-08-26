# KOKOTO WebChat 5.0.0 Release Checklist

## Source/config/i18n
- [ ] Root/Bukkit/Fabric/NeoForge/Forge metadata and artifact names are `5.0.0`.
- [ ] `config.yml`, `config-baselines/config-5.0.0.yml`, and `distribution/config-reference-5.0.0.yml` are byte-identical.
- [ ] 4.7.0 → 5.0.0 migration writes `5.0.0_auto_migration`, preserves configured values, inserts missing nested settings/comments idempotently, and exact `5.0.0` disables same-version backfill.
- [ ] en-US/ko-KR/ja-JP/zh-CN have identical non-empty key sets.
- [ ] `inner.js` and all embedded wrappers pass syntax checks and embed the current frontend.

## Functional smoke test
- [ ] Login/link/logout/guest flow and session expiration work.
- [ ] Public game↔web chat, replies, pins, search, message tokens and content filtering work.
- [ ] DM/group create/send/read/retry/history flows work; cross-server DM routing uses exact server+UUID identity.
- [ ] Normal file upload and clipboard paste upload work with `random` and `original` filename modes; long Windows clipboard names remain usable and 8.3 aliases do not create broken links.
- [ ] Per-account UI profiles and account-synced keyword/notification settings work; guest settings remain local.
- [ ] Web Push works and does not duplicate same-device live-page OS notifications.
- [ ] Web Admin Filter/Settings and administrator Discord keyword alerts work; DiscordSRV channel selector shows logical channel names and ID-only fallback only when needed.
- [ ] Enabled map adapters install/remove/reload only their owned assets/marked blocks.
- [ ] Server relay signing, unknown-peer handling/backoff, deduplication and configured forwarding mode work.
- [ ] If ImageEmojis-Bero is enabled, the shared `plugins/KOKOTO-WebChat/emojis` path, `serverIp:webServerPort` client reachability, resource-pack reload/update flow, and game↔web token rendering are verified.
- [ ] If SimpleNicks-Bero is enabled, `player-display.mode: "display-name"` shows the nickname while KWC account/UUID identity remains correct.

## Security acceptance
- [ ] Frontend uses Bearer authentication; SSE uses short-lived one-time stream tickets.
- [ ] Administrator IP restrictions apply to login/link completion and subsequent ADMIN requests.
- [ ] Request body limits and bounded HTTP worker pool are active.
- [ ] Web Push rejects unsafe/private endpoints and redirects are disabled.
- [ ] Discord alert user text cannot create unintended mentions; Discord CDN redirects remain on approved HTTPS hosts.
- [ ] Profile import rejects unknown/nested/duplicate/wrong-type/injection-like content.
- [ ] Public deployments are documented to use HTTPS.

## Platform/build acceptance
- [ ] `update-check.enabled` is wired on Bukkit/Fabric/NeoForge/Forge; KWC-first/BMWC-fallback lookup and `kwc.update.notify` login notices are verified.
- [ ] Bukkit JAR builds with JDK 17.
- [ ] All 16 Fabric exact targets build with the target-selected JDK 17/21/25.
- [ ] All 12 NeoForge exact targets build with the target-selected JDK 17/21/25.
- [ ] All 16 Forge exact targets build with the selected JDK 17/21/25.
- [ ] `validate-release-windows.bat` prints `FINAL RELEASE BUILD PASS`, collects exactly 45 deployable JARs, and writes `release-5.0.0/SHA256SUMS.txt`.

## Documentation/distribution
- [ ] README, Upgrade, Configuration, User Manual and Wiki reflect the final 5.0.0 behavior and contain no development-stage “rebrand later” wording.
- [ ] Modrinth/CurseForge description, summary and 5.0.0 notes match the final feature/platform scope.
- [ ] AI assistance disclosure is present in README/description/`AI_USAGE.md`, but not listed as a functional changelog item.
- [ ] 5.0.0 is published on the existing BMWC listing first so 4.7.0 update checkers can discover it.
- [ ] Existing BMWC pages clearly state “BlueMapWebChat is now KOKOTO WebChat” and list the new canonical address only after it is live.
- [ ] 5.0.0 update notices keep the CurseForge link on the existing BMWC bridge page until the replacement KWC CurseForge listing is confirmed live.
- [ ] If same-project rename is impossible, BMWC is left as a retirement/migration notice and future releases are published on the KWC listing.
- [ ] Modrinth uses one project with correct per-version loader metadata; CurseForge Bukkit bridge is uploaded to the existing Bukkit Plugins project and mod-loader artifact handling is confirmed before Fabric/NeoForge/Forge publication.
- [ ] GitHub repository rename uses `BlueMapWebChat` → `KOKOTO-WebChat`; local remotes are updated after rename and the old repository name is not reused.
