# KOKOTO WebChat 5.3.0 — Technical Reference

For reaction authority, direct/multi-hop delivery, offline outbox behavior and notification diagrams, see [REACTIONS.md](REACTIONS.md).


![KOKOTO WebChat 5.3.0 architecture overview](../assets/architecture-5.3.0.svg)

[PNG](../assets/architecture-5.3.0.png) · [SVG](../assets/architecture-5.3.0.svg)

> **Note:** The diagrams are supplemental. KWC-specific behavior is defined by the source and the text in this manual.

## Visual architecture and data paths

![KWC 5.3.0 architecture](../assets/architecture-5.3.0.svg)

![Administration and HTTP security boundary](../assets/admin-security-boundary.svg)

![History search/navigation flow](../assets/history-search-navigation.svg)

These figures summarize component boundaries; feature-specific details follow in the sections below and in `REACTIONS.md`, `SERVER_RELAY.md`, and `UPLOAD_SECURITY.md`.

## Architecture
`kwc-core` owns loader-neutral chat, HTTP/SSE transport, history/private-chat stores, profiles/security helpers, Web Push and Relay v2. Bukkit, Fabric, Forge and NeoForge provide loader-specific player/permission/thread/console/native-message integration through host/adapter boundaries. Map adapters and the standalone frontend consume the same core behavior.

## HTTP and SSE
The embedded service is based on the JDK `com.sun.net.httpserver.HttpServer` behind `CoreHttpServer`. REST-style handlers serve configuration, history, authentication, uploads, private chat and administration. Live updates use Server-Sent Events through a bounded `SseHub`/`SseConnection` registry. Authenticated frontend requests use bearer authorization; stream establishment uses a short-lived stream ticket rather than exposing the long-lived account token in the SSE URL.

## Credentials and sessions
Password hashing uses `PBKDF2WithHmacSHA256` with a per-password salt and stored iteration count. Session/captcha/rate-limit helpers hash or bound sensitive tokens before comparison/storage where applicable. Administrator access is additionally constrained by configured role/permission/IP policy.

## Persistence
SQLite-backed history/private stores are used where configured, and saved conversations use the dedicated `conversation-archives.db` SQLite store. SQLite is initialized with WAL journaling (`PRAGMA journal_mode=WAL`). Startup integrity/recovery logic handles main database and `-wal`/`-shm` sidecars; operational backups must preserve a consistent database state. Group rooms persist the per-room membership-notice switch in `group_rooms.membership_events_enabled`, while join/leave/kick/ban membership changes are stored as `group_messages.event_type` values `member_join` / `member_leave`; existing databases receive these columns automatically.

## Web Push
`WebPushManager` is JDK-only. It validates push destinations to reduce SSRF exposure, manages VAPID keys, derives Web Push content-encryption material with HKDF and sends AES-GCM protected payloads. Browser notification preferences are account-aware while the actual push endpoint remains device-local.

## Platform abstraction and exact-target builds
`PlatformAdapter` and related host interfaces isolate loader APIs from core. The release matrix is 1 Bukkit artifact + 16 Fabric exact targets + 12 NeoForge exact targets + 16 Forge exact targets = **45 deployable artifacts**. Build helpers select Java 17/21/25 by Minecraft generation. Windows release helpers run a path-length preflight before Gradle/Maven because exact-target work directories can exceed the validated Windows path profile.

## Relay Protocol 2.2 compatibility and Relay v2 trust model
Relay Protocol 2.2 keeps the Relay v2 `groups -> peers` trust model. Protocol major `2` is the wire-compatibility boundary; the current revision advertises `public`, `dm`, `read`, `delete`, `reaction`, `reaction-authority`, `typing`, `game`, and `profile`, while KWC product version is diagnostic only. A group is a symmetric trust domain with exactly one group shared secret; peers have no individual secret. Group secrets shorter than 32 characters are rejected. An empty group secret is a provisioning request: startup/reload generates a cryptographically secure 32-byte URL-safe secret and persists it to `config.yml`; non-empty values are never regenerated automatically. A peer ID cannot be used in multiple local groups. Both sides must list each other in the same group. Each peer may independently gate outbound and inbound `public-chat`, `event`, `dm`, and `profile` traffic; omitted policies default to enabled for compatibility.

`/relay/v2/handshake` HMAC-authenticates protocol major/revision, group, sender ID, target ID, timestamp, nonce and sender outbound transport. A legacy Relay 2.0 probe canonical is accepted as a compatibility fallback; product version is no longer part of modern relay compatibility. The receiver checks group membership, target identity, clock skew and nonce replay. This endpoint is a stateless diagnostic identity/health probe only; it creates no route state. Direct `/relay/v2/message` delivery authenticates every request independently. The receiver must still reciprocally configure the sender in the same group with the same shared secret. Legacy v1 endpoints return HTTP 426.

## Relay payload cryptography
For each direction, KWC derives a 256-bit key using HKDF-SHA256 from the group secret and direction context. `/relay/v2/message` uses AES-256-GCM with a random 12-byte IV and a 128-bit tag. GCM AAD binds `group`, `from`, `to`, `timestamp`, `nonce`, and `IV`. Responses are independently authenticated with HMAC-SHA256 over the group/responder/requester/timestamp/request nonce/status/body tuple so an unauthenticated intermediary cannot forge a successful response. Timestamp, request nonce, relay ID/receipt ID, origin and hop-count checks provide replay/loop defense.

## Relay forwarding trust boundary
Direct HTTP is allowed because the relay payload is still AES-GCM protected, but KWC warns because HTTP lacks transport metadata confidentiality and normal TLS server authentication. Direct relay authenticates each request independently; the diagnostic handshake endpoint does not control routing. Forwarding requires group `forwarding.enabled` and same-group routing; only the specific http:// peer is excluded as an incoming/outgoing forwarding hop, while other https:// peers remain eligible.

Relay v2 is **hop-by-hop authenticated encryption, not end-to-end encryption**. A forwarding server decrypts the incoming envelope, validates/processes it, then encrypts for the next peer. Every forwarding server is therefore a trusted participant. Compromise of any member's group shared secret requires rotating that secret on every member of the group.

## 5.0.0 migration boundary
The migration layer does not guess v2 group membership from the v1 flat trust graph. Legacy relay secret/peer/forwarding keys are retired and relay is disabled until the operator explicitly defines v2 groups. This is a deliberate fail-closed trust migration.
## Private reply persistence and relay identity
DM/group replies are stored as metadata, not inferred from display text. DM writes validate the target against the same thread; group writes validate the same room/current membership. The server derives the canonical reply sender/preview from storage. Cross-server DM envelopes carry `replyToRelayId` plus sender/preview snapshot and never a remote server-local database ID; the receiver resolves the stable relay ID to its own local message ID when possible.


## Saved-conversation persistence and deletion authority
When `chat.conversation-archive.enabled` is true, `ConversationArchiveStore` keeps per-account snapshots in `conversation-archives.db`. When false, the archive store is not opened/created and archive API routes are not registered. The client sends range identity, not trusted snapshot content; `WebChatServer` resolves the selected range from public/DM/group storage and re-validates account access before saving. Snapshot rows retain the original source-message identity internally so administrator-forced deletion can remove matching content later, while the browser projection omits sender UUIDs. Attachment bytes are never copied into the archive. Quotas are configurable with defaults of 100 archives/account, 1,000 messages/archive and 10,000 saved messages/account; loaders clamp them to 1-1000, 1-10000 and 1-100000 respectively, and the archive API reports the active values. Lowering quotas does not purge or hide existing snapshots; enforcement applies to new saves.

Normal history/private retention does not delete an already-saved snapshot. Administrator source-message deletion, full public-history clear, administrator DM-thread/group-room deletion and private-room lock policy remain authoritative over personal archives.

## Typing state
Public/DM/group typing state is ephemeral. A browser input event creates a five-second window; repeated keystrokes during that window are suppressed client-side. The state is not written to SQLite/JSONL and there is no polling or always-running typing worker. Local group state is distributed only to current room participants over existing SSE. Remote DM typing uses the typing capability carried by the current Relay Protocol 2.2.

## Configuration presentation localization
`PortableConfigMigration` chooses a bundled EN/KO/JA/ZH config template from `ui.language`, overlays parsed operator values, and uses the same language for the generated reference and migration report. Semantic Difference output is built from parsed YAML paths/values, so comments/layout/quote/order changes do not create false differences.

## References and standards

See [REFERENCES.md](REFERENCES.md) for the primary standards and official third-party documentation cited by this manual.

- [RFC 2104 — HMAC](https://www.rfc-editor.org/rfc/rfc2104.html)
- [RFC 5869 — HKDF](https://www.rfc-editor.org/info/rfc5869/)
- [NIST SP 800-38D — GCM](https://csrc.nist.gov/pubs/sp/800/38/d/final)
- [RFC 8018 — PBKDF2 / PKCS #5](https://www.rfc-editor.org/info/rfc8018/)
- [RFC 8291 — Web Push encryption](https://www.rfc-editor.org/info/rfc8291/)
- [RFC 8292 — VAPID](https://www.rfc-editor.org/info/rfc8292/)
- [WHATWG — Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [SQLite — Write-Ahead Logging](https://sqlite.org/wal.html)
- [Oracle Java SE 17 — HttpServer](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
