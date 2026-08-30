# KOKOTO WebChat 5.1.0 — Installation & Operations


![KWC deployment modes](assets/deployment-modes.svg)

> **Note:** The diagrams are supplemental. KWC-specific behavior is defined by the source and the text in this manual.

## Platform selection
Install the artifact matching the server platform/version: Bukkit/Paper/Spigot uses the single Java 17 plugin artifact; Fabric and Forge use 16 exact targets; NeoForge uses 12 exact targets. Loader builds select Java 17/21/25 according to the Minecraft generation. KWC is server-side; core chat does not require a client mod.

## First start and web publishing
Start once to create the KWC data/configuration. Use standalone, a supported map adapter, or both. For Internet exposure, bind the built-in HTTP service to loopback where practical and terminate HTTPS at Caddy/Nginx. Non-loopback plain HTTP produces an explicit warning.

## Configuration lifecycle
Edit the current `config.yml`, then use `/kchat reload`. Reload validates YAML before live services are replaced; malformed YAML leaves the previous running configuration active. `config-reference-5.1.0.yml` is the administrator-readable current default rendered in the same built-in `ui.language`; custom/unsupported UI languages use the English presentation. A 5.0.0 → 5.1.0 migration rebuilds configuration from the 5.1.0 template while preserving supported operator values, but Relay v1 trust settings are intentionally reset. With 5.1.0, `ui.language` also selects the bundled comment template used for `config.yml`, the generated reference, and migration/difference prose; parsed operator values are overlaid unchanged, and semantic differences compare YAML paths/values rather than formatting.

## Relay Protocol v2
Configure explicit `server-relay.groups`. Each group has one shared secret and its peers have no individual secret. For a new group, leave `shared-secret: ""` on one server, start/reload KWC, then copy the generated value from that server's `config.yml` to every other member of the same group. Existing non-empty values are preserved and manually supplied secrets shorter than 32 characters remain invalid. Reciprocal peer registration in the same group is mandatory. Direct HTTP is encrypted/authenticated at relay payload level but warns; forwarding is same-group and HTTPS→HTTPS only. Relay v1 endpoints return 426. See `SERVER_RELAY_EN.md`.

## Updates and distribution
During the project-address transition, the 5.1.0 updater checks canonical Modrinth `kokoto-webchat` first and falls back to the existing `bluemapwebchat` publication. BMWC remains a real update source until the transition is complete. Before publishing, verify all 45 deployable target artifacts, SHA-256 output and current release text. On Windows run the build-path preflight before the full release build so an unsupported long source path is rejected early.

## Backup
Back up the whole KWC data directory while preserving SQLite database sidecars (`-wal`/`-shm`) consistently. For a clean offline backup, stop the server first. Keep config, account/profile data, push subscriptions, uploads/emoji assets and relay configuration together.

## Security operations
Use HTTPS, strong administrator credentials, restrictive admin IP rules, least-privilege permissions, and explicit super-admin lists. Treat every Relay group secret as a group-wide symmetric trust key: if one member leaks it, rotate the secret on all group members.

## Troubleshooting order
1. Check startup/reload warnings. 2. Validate current URL/reverse proxy. 3. Confirm loader/version artifact and Java generation. 4. For relay, confirm group ID, reciprocal peer IDs, group secret, clocks and HTTPS topology. 5. Preserve logs/config before manual database or file repair.
