# Upgrade from 4.6.0 to 4.6.1

## Main changes

- Remote-server players observed in relayed game or linked-web messages are available in the existing web DM recipient search when the relay payload contains a player UUID.
- Optional read-only administrator access to DM message bodies is available through the existing private-chat metadata list.
- Added the compact Modrinth update checker with administrator join notices.
- Plugin and configuration version are now `4.6.1`.

## Configuration migration

When an existing config contains `config-version: "4.6.0"`, starting 4.6.1 creates:

```text
plugins/KOKOTO-WebChat/config-migration-4.6.1.yml
```

The fragment contains the new settings and the target review marker:

```yaml
update-check:
  enabled: true

direct-message:
  admin-audit:
    enabled: false

config-version: "4.6.1"
```

The real `config.yml` is not modified. Keep audit disabled unless private-message content review is explicitly required.

## Enabling DM content audit

Both conditions are required:

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

direct-message:
  admin-audit:
    enabled: true
```

- Normal ADMIN or MODERATOR roles are insufficient by themselves.
- The audit view is read-only.
- Each page read is written to the configured audit log without copying message bodies into that log.
- Restart or run `/kchat reload` after changing configuration. A JAR replacement still requires a server restart.

## Cross-server DM version requirement

Every server that exchanges cross-server DMs must run KOKOTO WebChat 4.6.1 or later. Clicking `server · source` now passes the clicked message's UUID and origin server directly, remote search keeps the server ID, and existing remote threads send both destination server ID and player UUID.
