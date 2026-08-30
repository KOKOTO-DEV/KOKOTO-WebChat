# KOKOTO WebChat 5.1.0 — References and Standards

This page lists the primary external specifications and official product documentation used by the KOKOTO WebChat 5.1.0 technical and operations documentation. KWC-specific behavior is defined by the source code and release documentation in this repository; the links below are references for the underlying protocols, APIs, storage modes, and third-party integrations.

## Cryptography and authentication

- **RFC 2104 — HMAC: Keyed-Hashing for Message Authentication**  
  https://www.rfc-editor.org/rfc/rfc2104.html  
  Referenced by Relay Protocol v2 response/handshake authentication.
- **RFC 5869 — HMAC-based Extract-and-Expand Key Derivation Function (HKDF)**  
  https://www.rfc-editor.org/info/rfc5869/  
  Referenced by Relay Protocol v2 directional key derivation and Web Push key derivation.
- **NIST SP 800-38D — Galois/Counter Mode (GCM) and GMAC**  
  https://csrc.nist.gov/pubs/sp/800/38/d/final  
  Referenced by the AES-GCM authenticated-encryption descriptions.
- **RFC 8018 — PKCS #5: Password-Based Cryptography Specification Version 2.1**  
  https://www.rfc-editor.org/info/rfc8018/  
  Referenced by the PBKDF2 password-hashing discussion.
- **RFC 8291 — Message Encryption for Web Push**  
  https://www.rfc-editor.org/info/rfc8291/  
  Referenced by Web Push payload encryption.
- **RFC 8292 — VAPID for Web Push**  
  https://www.rfc-editor.org/info/rfc8292/  
  Referenced by KWC's VAPID application-server identification.

## HTTP, SSE, and JDK APIs

- **RFC 9110 — HTTP Semantics**  
  https://www.rfc-editor.org/rfc/rfc9110.html
- **WHATWG HTML Standard — Server-sent events / EventSource**  
  https://html.spec.whatwg.org/multipage/server-sent-events.html
- **Oracle Java SE 17 — `com.sun.net.httpserver.HttpServer`**  
  https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html

## Persistence

- **SQLite — Write-Ahead Logging**  
  https://sqlite.org/wal.html  
  Referenced by KWC's SQLite WAL storage and backup guidance.

## Reverse proxies and map hosting

- **Caddy — `reverse_proxy` directive**  
  https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
- **NGINX — `ngx_http_proxy_module`**  
  https://nginx.org/en/docs/http/ngx_http_proxy_module.html
- **BlueMap Wiki**  
  https://bluemap.bluecolored.de/wiki/
- **BlueMap reverse-proxy guide**  
  https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html

## Discord integration

- **DiscordSRV Documentation**  
  https://docs.discordsrv.com/
- **DiscordSRV initial setup**  
  https://docs.discordsrv.com/installation/initial-setup/

## Optional integration projects

- **ImageEmojis upstream**  
  https://github.com/MrQuackDuck/ImageEmojis
- **SimpleNicks upstream**  
  https://github.com/Simplexity-Development/SimpleNicks

Upstream documentation describes the original projects. KWC-Bero integration behavior and KWC-specific compatibility are defined by the matching KWC documentation and 5.1.0 source.

## Android file selection

- **Android Developers — OpenableColumns / DISPLAY_NAME**  
  https://developer.android.com/reference/android/provider/OpenableColumns.html
- **Chromium Issue 387440285 — Android picker may expose a large numeric filename when selecting directly instead of Browse**  
  https://issues.chromium.org/issues/387440285
- **Chromium Issue 40912007 — Chrome on Android may show password/payment manager UI on unrelated non-password HTML inputs**  
  https://issues.chromium.org/issues/40912007

These references explain the platform/browser filename handoff. KWC only sees the `File.name` exposed by the browser; it cannot query Android MediaStore from ordinary web JavaScript.

## Documentation rendering

- **GitHub Docs — Creating diagrams with Mermaid**  
  https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams
- **GitHub Docs — Rendering images and other non-code files**  
  https://docs.github.com/en/repositories/working-with-files/using-files/working-with-non-code-files
- **Mermaid — Flowchart syntax**  
  https://mermaid.js.org/syntax/flowchart.html
- **Mermaid — Sequence diagram syntax**  
  https://mermaid.js.org/syntax/sequenceDiagram.html

## Reference policy

- Prefer primary specifications and official vendor/project documentation.
- A reference link does **not** imply that KWC implements every feature in that specification or product.
- KWC behavior, defaults, compatibility rules, and security boundaries must be verified against the 5.1.0 source code and current KWC documentation.
- Historical KWC documents remain historical; do not use an older document as the definition of current 5.1.0 behavior.
