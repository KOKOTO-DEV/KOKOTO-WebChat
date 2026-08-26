# KOKOTO WebChat Core

Platform-neutral code extracted in 5.0.0 as the foundation for additional server loaders and web-map adapters.

This module must remain free of Bukkit/Paper/Fabric/NeoForge/Forge/Bungee imports. It currently owns:

- shared chat/DM/group/session/security models
- `ConfigValues`, JSON/security/rate-limit/captcha helpers, and message-token processing
- `PlatformAdapter` / `PlatformPlayer` / `PlatformGameMessage` contracts plus legacy game-text helpers
- public-chat `SqliteHistoryStore` persistence
- `DirectMessageStore` / `GroupChatStore` persistence behind `ConversationStoreHost`
- the complete signed `ServerRelay` engine, including HTTP/HMAC transport, routing, deduplication, cross-server DM delivery, and remote DM read receipts
- JDK HTTP/SSE transport primitives through `CoreHttpServer`, `SseHub`, and `SseConnection`
- complete `WebChatServer` endpoint semantics/orchestration behind `WebChatHost` and narrow storage/auth/language/moderation/Discord service contracts
- pinned-message runtime model (`PinnedMessage`)
- the JDK-only `WebPushManager` behind `WebPushHost`, including VAPID/subscription/encryption/filtering logic

Platform runtimes provide loader-specific lifecycle, YAML/resource loading, identity, permission, scheduler, command, game-delivery, optional integration, persistence-host, localization, moderation, Discord, audit, and announcement callbacks. SQLite JDBC remains a core runtime dependency and is shaded into the final Bukkit artifact transitively.
