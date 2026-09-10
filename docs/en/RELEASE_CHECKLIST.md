# KOKOTO WebChat 5.3.0 Release Checklist

## Source/config/i18n
- [ ] Root/Bukkit/Fabric/NeoForge/Forge metadata and artifact names are `5.3.0`.
- [ ] Current `config.yml`, `config-baselines/config-5.3.0.yml`, localized config templates, and `distribution/config-reference-5.3.0.yml` have identical parsed setting paths/defaults; presentation comments and the `_auto_migration` marker may differ. Historical baselines remain migration inputs only.
- [ ] 5.2.1 → 5.3.0 migration writes `5.3.0_auto_migration`, preserves supported operator values and Relay v2 trust settings, migrates retired hide confirmations to delete confirmations, and exact `5.3.0` stops same-version reconstruction.
- [ ] en-US/ko-KR/ja-JP/zh-CN key sets and placeholders are identical.
- [ ] `inner.js` and all 8 frontend wrappers pass syntax and embedded JS/CSS equality checks.
- [ ] `node tools/build-inner-bundle.js --check` passes, proving `frontend/inner/manifest.txt`, generated `inner.js`, and all 8 embedded wrapper payloads are identical.

## Functional smoke test
- [ ] Public game↔web chat, replies, URLs, custom emojis, pins, search, message tokens, and content filter work.
- [ ] Existing invalid emoji pack/item names migrate to canonical names; new pack/item uploads use the same rule; same-pack collisions receive numeric suffixes.
- [ ] Emoji picker inserts the exact token without automatic spaces; configured newline aliases keep emoji-only rows compact while blank-line aliases still create blank lines.
- [ ] Web replies preserve full source text and render readable URLs/custom emojis.
- [ ] In-game DM/group labels prepare the existing commands and message-body clicks prepare replies; URL actions still open URLs.
- [ ] Tampered `dm-...`/`group-...` private reply targets fail unless the player is a DM participant/current group member.

- [ ] Public reactions persist, Relay 2.2 propagation works, and reaction-only SSE updates do not restart active media; the 32 × 16 px empty-state `+` has 1 px visual clearance above/below without covering message text, reaction OFF restores the original 8 px spacing, real reactions use the full in-flow row, category/search rerenders preserve picker position and outside-click close behavior, reactor-name hover lists scroll after about four lines, and **Admin > Emojis > Reaction icons** uses the normal rounded themed setting rows plus an editable `emoji = search words` alias list persisted to `reaction-search-aliases.txt`.
- [ ] Public/DM/group typing is event-driven with a five-second window, excludes self/audit viewers, collapses long multi-user labels, and creates no polling/persistent typing state. Web Admin Settings/config.yml independently enforce server-wide Open chat OFF / DM ON / Group ON defaults. `chat.typing-indicator.user-display-control` defaults OFF; when enabled, a signed-in user can hide incoming typing indicators with one account-stored setting without suppressing that user’s outgoing typing activity.
- [ ] Saved conversations validate ranges server-side, enforce archive quotas and private-room lock policy, survive normal retention, and cascade administrator source deletion.
- [ ] Saved-conversation PDF/print view shows display + real names, preserves current KWC appearance, embeds only still-available original images, uses links for video/audio/other files, and marks missing originals unavailable.
- [ ] `chat.conversation-archive.enabled: false` leaves archive API routes unregistered, does not open/create `conversation-archives.db`, and renders no Saved-conversation controls in the DOM.
- [ ] Non-default `max-archives-per-user`, `max-messages-per-archive`, and `max-messages-per-user` values are enforced server-side and `/archive/list` reports the active limits.
- [ ] In an open DM, the Back/title hover background spans the complete header through the inset Settings-button area with no vertical seam; the Settings button keeps its own hover state.
- [ ] The single Reactions notification checkbox gates both live browser reaction notifications and background/mobile Web Push reaction delivery; unsupported delivery paths remain inactive.
- [ ] Public/DM/group bottom-follow uses 32 px; compose-panel layout changes preserve viewport and do not trigger an immediate forced bottom scroll.

## Relay/security
- [ ] The optional signed `/relay/v2/handshake` identity/health probe succeeds when both servers list each other with the same group ID and group shared secret; the probe creates no route state and direct relay authenticates each request independently.
- [ ] One-sided peer configuration is unusable in both directions.
- [ ] `server-relay.forwarding.enabled` defaults to `false`.
- [ ] When forwarding is enabled, both inbound and outbound forwarding hops require HTTPS; HTTP forwarding is blocked.
- [ ] Direct HTTP peers remain one-hop compatible and emit explicit localized WARNING/경고/警告 messages.
- [ ] A non-loopback built-in KWC HTTP listener emits the localized HTTP exposure warning.
- [ ] Public relay and cross-server 1:1 DM/read receipts work; group chat remains local and is not server-relayed.

## Distribution/build
- [ ] Modrinth updater checks only the canonical `kokoto-webchat` project and does not query legacy BMWC project addresses.
- [ ] CurseForge points to `bukkit-plugins/kokoto-webchat`.
- [ ] Windows path preflight is active in the full validator and all Fabric/NeoForge/Forge build-all/build-target entry points.
- [ ] Bukkit builds with JDK 17; Fabric 16 / NeoForge 12 / Forge 16 exact targets build with their selected JDK 17/21/25.
- [ ] The release validator opens every NeoForge JAR and verifies the target-selected `META-INF/mods.toml` or `META-INF/neoforge.mods.toml`, including `modLoader`, `loaderVersion`, KWC identity/version, exact Minecraft dependency, and no unresolved template placeholders.
- [ ] The release validator runs security + Relay/reaction/typing regression harnesses and executes the saved-conversation SQLite runtime harness against the finished shaded Bukkit JAR.

> `validate-release-windows.bat` and its required PowerShell helpers are included in the source archive. Development regression harnesses are also included under `validation/`; no separate validation-tools archive is required.

- [ ] `validate-release-windows.bat` produces `FINAL RELEASE BUILD PASS`, 45 deployable JARs, and SHA256SUMS.
- [ ] Final acceptance was run without `--fast`; sequential or `--parallel` clean scheduling is allowed, but cached/partial builds are not treated as `FINAL RELEASE BUILD PASS`.
