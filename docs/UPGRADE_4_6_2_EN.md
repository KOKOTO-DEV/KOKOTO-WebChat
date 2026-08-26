# Upgrade from 4.6.1 to 4.6.2

KOKOTO WebChat 4.6.2 improves private-message delivery reliability, fixes same-name DM routing, and adds per-message read status. No new administrator-facing configuration option is required.

## What changes

- A DM name without a server qualifier resolves only to a player on the current server. Cross-server targets use explicit server-scoped identity (`server-id + UUID`).
- A cross-server DM is not considered delivered until the destination server confirms that the message was stored. Routing, HTTP, timeout, or destination failures remain retryable.
- DM retries reuse persistent relay IDs, and web sends use client message IDs, preventing duplicate messages when a request or response is uncertain. Pending remote deliveries interrupted by a restart recover as retryable failures.
- Group-chat web sends use the same client-message-id duplicate protection. Normal successful delivery has no label; the compact localized state beside the timestamp is `Sending` while pending and `Failed · Retry` when delivery cannot be confirmed.
- Read status is calculated for **every DM and group-chat message** and is shown beside the timestamp. A 1:1 DM uses a short localized unread label (`Unread` in English) until its recipient reads it, then shows `✓`. Group chat keeps the unread-recipient count as a number and shows `✓` when that count reaches zero.
- Cross-server DM read acknowledgements are returned through the authenticated private relay, including hub/chain topologies. The latest acknowledgement is safely re-sent when the conversation is viewed, so a temporary relay or HTTP failure does not permanently lose the read mark.
- Multi-hop DM delivery reports success upstream only after the final destination confirms storage.
- Fixed update notifications. Eligible administrator logins now trigger a rate-limited Modrinth refresh instead of relying only on the previous scheduled result; OPs are explicitly eligible, failed checks are logged as warnings, and reloads unregister the previous update listener.

## Configuration migration

The bundled 4.6.2 configuration adds no administrator-facing keys and changes no existing defaults compared with 4.6.1. Only the review marker changes:

```yaml
config-version: "4.6.2"
```

With a reviewed 4.6.1 config, KOKOTO WebChat creates `plugins/KOKOTO-WebChat/config-migration-4.6.2.yml`. If there are no unrelated missing settings or local/default differences, the fragment contains only the new `config-version` marker. The real `config.yml` is never overwritten automatically.

## Cross-server deployment

All servers exchanging cross-server DMs should run KOKOTO WebChat 4.6.2 or later. Restart each server after replacing the JAR so the new relay handling and additive database migration are active. Existing DM and group-chat messages are preserved.
