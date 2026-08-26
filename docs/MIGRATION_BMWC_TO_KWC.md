# BlueMapWebChat 4.x → KOKOTO WebChat 5.0.0 migration

KOKOTO WebChat uses a new canonical project identity. The old BlueMapWebChat identity is kept only where it is required to import an existing installation or preserve compatibility with existing command/permission/relay peers.

## Canonical identity

- Plugin: `KOKOTO-WebChat`
- Data directory: `plugins/KOKOTO-WebChat`
- Command: `/kchat`
- Permissions: `kwc.*`
- Java package: `dev.kokoto.webchat`
- Maven modules: `kwc-core`, `kwc-standalone-frontend`, `kwc-adapter-bluemap`, `kwc-platform-bukkit`
- BlueMap addon path: `addons/kokoto-web-chat`
- Recommended reverse-proxy prefix: `/chat`

## Automatic data import

When `plugins/BlueMapWebChat` exists, KOKOTO WebChat imports it only if `plugins/KOKOTO-WebChat` contains no existing data files. The import is first-run-only and never merges BMWC data into an established KWC installation. Deleting `.legacy-import-complete` manually does not bypass this safety check. Empty directories alone do not count as existing KWC data, but any file does, including a zero-byte data file. The old BMWC directory is never deleted or modified and remains a migration source/backup until the administrator removes it.

The generated/reference/migration config files from BMWC are not copied as active KWC configuration. KWC generates its own current `config.yml` and maps values from the legacy file into the new layout. `.legacy-import-complete` is temporary migration state only: while the BMWC source remains it can record that import was completed or intentionally skipped because KWC data already existed; once `plugins/BlueMapWebChat` is removed, KWC deletes the marker automatically on startup or `/kchat reload`.

When the legacy `config-version` matches a bundled BMWC baseline, values that are still exactly equal to that BMWC version's defaults do not overwrite the current KWC defaults. This keeps a normal BMWC installation aligned with the current 5.0.0 reference while preserving settings the administrator actually changed. Retired keys that no longer exist in the KWC reference are not copied.

## Config key migration

- `web-addon.*` → `adapters.bluemap.*`
- `standalone-web.*` → `frontend.standalone.*`
- `discordsrv.game-to-discord` → `discordsrv.game-relay-mode` (`false` → `discordsrv`, `true` → `kwc` when the old value was customized)
- `discordsrv.game-to-discord-format` → `discordsrv.game-relay-format`
- retired `ui.show-login-only-when-hidden` is not copied
- legacy local `/bmwc` public URL values → `/chat`
- `server-relay.peers[].url` is preserved exactly; remote BMWC peers may legitimately remain on `/bmwc/api`
- legacy `addons/bluemap-web-chat` → `addons/kokoto-web-chat`
- legacy `bluemapwebchat.*` permission values → `kwc.*`
- legacy `/bmchat` command values → `/kchat`

The new generated config contains only the KWC names.

## Compatibility aliases

The canonical command is `/kchat` (`/kc` alias). Old `/bmchat`, `/bluemapchat`, and `/bmc` command aliases are not registered. Existing permission-manager grants under `bluemapwebchat.*` may still be accepted by the permission compatibility layer when the matching `kwc.*` permission is checked.

## BlueMap addon migration

The canonical addon directory is `addons/kokoto-web-chat`. When patching `webapp.conf`, KWC removes script/style references to the old `addons/bluemap-web-chat` path before installing the current entries so both frontends are not loaded at once.

## Server relay compatibility

Relay Protocol v1 intentionally keeps the existing wire headers:

- `X-BMWC-Relay-Version`
- `X-BMWC-Relay-From`
- `X-BMWC-Relay-Timestamp`
- `X-BMWC-Relay-Signature`

These are a legacy protocol namespace, not a product-name check. KWC can therefore continue relaying with BlueMapWebChat 4.x peers when `server-id`, peer URL, protocol version, and shared secret/HMAC settings match. Migration never rewrites `server-relay.peers[].url`; a remote BMWC peer using `/bmwc/api` must keep that URL.

## Reverse proxy

If the previous public route was `/bmwc`, update Caddy/Nginx to publish `/chat` before testing the migrated URLs. The bundled web examples use `/chat` for standalone and `/chat/api` for the proxied API. `/kchat` is the Minecraft command, not the public web prefix.
