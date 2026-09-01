# KOKOTO WebChat 5.1.0 Release Checklist

## Source/config/i18n
- [ ] Root/Bukkit/Fabric/NeoForge/Forge metadata and artifact names are `5.1.0`.
- [ ] `config.yml`, `config-baselines/config-5.1.0.yml`, and `distribution/config-reference-5.1.0.yml` are byte-identical.
- [ ] 5.0.0 → 5.1.0 migration writes `5.1.0_auto_migration`, preserves supported operator values, removes retired settings, resets Relay v1 trust/topology to disabled Relay v2 for explicit reconfiguration, and exact `5.1.0` stops same-version reconstruction.
- [ ] en-US/ko-KR/ja-JP/zh-CN key sets and placeholders are identical.
- [ ] `inner.js` and all 8 frontend wrappers pass syntax and embedded JS/CSS equality checks.

## Functional smoke test
- [ ] Public game↔web chat, replies, URLs, custom emojis, pins, search, message tokens, and content filter work.
- [ ] Existing invalid emoji pack/item names migrate to canonical names; new pack/item uploads use the same rule; same-pack collisions receive numeric suffixes.
- [ ] Emoji picker inserts the exact token without automatic spaces; configured newline aliases keep emoji-only rows compact while blank-line aliases still create blank lines.
- [ ] Web replies preserve full source text and render readable URLs/custom emojis.
- [ ] In-game DM/group labels prepare the existing commands and message-body clicks prepare replies; URL actions still open URLs.
- [ ] Tampered `dm-...`/`group-...` private reply targets fail unless the player is a DM participant/current group member.

## Relay/security
- [ ] The optional signed `/relay/v2/handshake` identity/health probe succeeds when both servers list each other with the same group ID and group shared secret; the probe creates no route state and direct relay authenticates each request independently.
- [ ] One-sided peer configuration is unusable in both directions.
- [ ] `server-relay.forwarding.enabled` defaults to `false`.
- [ ] When forwarding is enabled, both inbound and outbound forwarding hops require HTTPS; HTTP forwarding is blocked.
- [ ] Direct HTTP peers remain one-hop compatible and emit explicit localized WARNING/경고/警告 messages.
- [ ] A non-loopback built-in KWC HTTP listener emits the localized HTTP exposure warning.
- [ ] Public relay and cross-server 1:1 DM/read receipts work; group chat remains local and is not server-relayed.

## Distribution/build
- [ ] Modrinth updater checks `kokoto-webchat` first, falls back to `bluemapwebchat` during the address transition, and warns only when both sources fail.
- [ ] CurseForge points to `bukkit-plugins/kokoto-webchat`.
- [ ] Windows path preflight is active in the full validator and all Fabric/NeoForge/Forge build-all/build-target entry points.
- [ ] Bukkit builds with JDK 17; Fabric 16 / NeoForge 12 / Forge 16 exact targets build with their selected JDK 17/21/25.
- [ ] The release validator opens every NeoForge JAR and verifies the target-selected `META-INF/mods.toml` or `META-INF/neoforge.mods.toml`, including `modLoader`, `loaderVersion`, KWC identity/version, exact Minecraft dependency, and no unresolved template placeholders.

> `validate-release-windows.bat` and its required PowerShell helpers are included in the source archive. The separate `KWC-5.1.0-validation-tools.zip` contains development-only browser regression tooling and is not required to run release builds.

- [ ] `validate-release-windows.bat` produces `FINAL RELEASE BUILD PASS`, 45 deployable JARs, and SHA256SUMS.
- [ ] Final acceptance was run without `--fast`; sequential or `--parallel` clean scheduling is allowed, but cached/partial builds are not treated as `FINAL RELEASE BUILD PASS`.
