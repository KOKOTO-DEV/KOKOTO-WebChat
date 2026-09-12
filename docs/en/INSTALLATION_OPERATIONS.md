# KOKOTO WebChat 5.3.1 — Installation & Operations


![KWC deployment modes](../assets/deployment-modes.svg)

[PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg)

> **Note:** The diagrams are supplemental. KWC-specific behavior is defined by the source and the text in this manual.

## Platform selection
Install the artifact matching the server platform/version: Bukkit/Paper/Spigot uses the single Java 17 plugin artifact; Fabric and Forge use 16 exact targets; NeoForge uses 12 exact targets. Loader builds select Java 17/21/25 according to the Minecraft generation. KWC is server-side; core chat does not require a client mod.

## First start and web publishing
Start once to create the KWC data/configuration. Use standalone, a supported map adapter, or both. For Internet exposure, bind the built-in HTTP service to loopback where practical and terminate HTTPS at Caddy/Nginx. Non-loopback plain HTTP produces an explicit warning.

## Configuration lifecycle
Edit the current `config.yml`, then use `/kchat reload`. Reload validates YAML before live services are replaced; malformed YAML leaves the previous running configuration active. `config-reference-5.3.0.yml` is the administrator-readable current default rendered in the same built-in `ui.language`; custom/unsupported UI languages use the English presentation. Upgrades to 5.3.1 keep the 5.3.0 configuration schema and preserve supported parsed operator values in the current template. The historical first 5.0.0 → 5.1.0 relay migration intentionally reset Relay v1 trust settings; a normal 5.1.0 → 5.2.0 upgrade preserves the existing Relay v2 group/secret/peer configuration. `ui.language` also selects the bundled comment template used for `config.yml`, the generated reference, and migration/difference prose; semantic differences compare YAML paths/values rather than formatting.

## Relay Protocol v2
Configure explicit `server-relay.groups`. Each group has one shared secret and its peers have no individual secret. For a new group, leave `shared-secret: ""` on one server, start/reload KWC, then copy the generated value from that server's `config.yml` to every other member of the same group. Existing non-empty values are preserved and manually supplied secrets shorter than 32 characters remain invalid. Reciprocal peer registration in the same group is mandatory. Direct HTTP is encrypted/authenticated at relay payload level but warns; forwarding is same-group and HTTPS→HTTPS only. Relay v1 endpoints return 426. See `SERVER_RELAY.md`.

## Updates and distribution
Starting with 5.2.0, the updater checks only canonical Modrinth `kokoto-webchat`; legacy BMWC project addresses are no longer active update sources. Before publishing, verify all 45 deployable target artifacts, SHA-256 output and current release text. On Windows run the build-path preflight before the full release build so an unsupported long source path is rejected early.

## Backup
Back up the whole KWC data directory while preserving SQLite database sidecars (`-wal`/`-shm`) consistently. For a clean offline backup, stop the server first. Keep config, account/profile data, push subscriptions, uploads/emoji assets and relay configuration together.

## Security operations
Use HTTPS, strong administrator credentials, restrictive admin IP rules, least-privilege permissions, and explicit super-admin lists. Treat every Relay group secret as a group-wide symmetric trust key: if one member leaks it, rotate the secret on all group members.

## Troubleshooting order
1. Check startup/reload warnings. 2. Validate current URL/reverse proxy. 3. Confirm loader/version artifact and Java generation. 4. For relay, confirm group ID, reciprocal peer IDs, group secret, clocks and HTTPS topology. 5. Preserve logs/config before manual database or file repair.
