# KOKOTO WebChat 5.2.0 — 参考标准与官方文档

本页汇总 KOKOTO WebChat 5.2.0 技术与运维文档所引用的**一手标准和官方产品/项目文档**。KWC 自身行为以本仓库中的 5.2.0 源代码和当前发布文档为准；以下链接用于说明底层协议、API、存储方式和第三方集成。

## 加密与认证

- **RFC 2104 — HMAC: Keyed-Hashing for Message Authentication**  
  https://www.rfc-editor.org/rfc/rfc2104.html  
  用于说明 Relay Protocol v2 的握手与响应认证。
- **RFC 5869 — HMAC-based Extract-and-Expand Key Derivation Function (HKDF)**  
  https://www.rfc-editor.org/info/rfc5869/  
  用于说明 Relay v2 的方向性密钥派生与 Web Push 密钥派生。
- **NIST SP 800-38D — Galois/Counter Mode (GCM) and GMAC**  
  https://csrc.nist.gov/pubs/sp/800/38/d/final  
  用于说明 AES-GCM 认证加密。
- **RFC 8018 — PKCS #5: Password-Based Cryptography Specification Version 2.1**  
  https://www.rfc-editor.org/info/rfc8018/  
  用于说明 PBKDF2 密码哈希。
- **RFC 8291 — Message Encryption for Web Push**  
  https://www.rfc-editor.org/info/rfc8291/  
  用于说明 Web Push 负载加密。
- **RFC 8292 — VAPID for Web Push**  
  https://www.rfc-editor.org/info/rfc8292/  
  用于说明 KWC 的 VAPID 应用服务器身份标识。

## HTTP、SSE 与 JDK API

- **RFC 9110 — HTTP Semantics**  
  https://www.rfc-editor.org/rfc/rfc9110.html
- **WHATWG HTML Standard — Server-sent events / EventSource**  
  https://html.spec.whatwg.org/multipage/server-sent-events.html
- **Oracle Java SE 17 — `com.sun.net.httpserver.HttpServer`**  
  https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html

## 持久化

- **SQLite — Write-Ahead Logging**  
  https://sqlite.org/wal.html  
  用于说明 KWC 的 SQLite WAL 存储与备份。

## 反向代理与地图托管

- **Caddy — `reverse_proxy` directive**  
  https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
- **NGINX — `ngx_http_proxy_module`**  
  https://nginx.org/en/docs/http/ngx_http_proxy_module.html
- **BlueMap Wiki**  
  https://bluemap.bluecolored.de/wiki/
- **BlueMap Reverse Proxy guide**  
  https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html

## Discord 集成

- **DiscordSRV Documentation**  
  https://docs.discordsrv.com/
- **DiscordSRV Initial Setup**  
  https://docs.discordsrv.com/installation/initial-setup/

## 可选集成项目

- **ImageEmojis upstream**  
  https://github.com/MrQuackDuck/ImageEmojis
- **SimpleNicks upstream**  
  https://github.com/Simplexity-Development/SimpleNicks

upstream 文档描述原项目行为。KWC-Bero 集成和 KWC 自身兼容性应以对应的 KWC 文档及 5.2.0 源代码为准。

## Android 文件选择

- **Android Developers — OpenableColumns / DISPLAY_NAME**  
  https://developer.android.com/reference/android/provider/OpenableColumns.html
- **Chromium Issue 387440285 — Android picker 直接选择而不经过 Browse 时可能暴露大数字文件名的问题**  
  https://issues.chromium.org/issues/387440285
- **Chromium Issue 40912007 — Android 版 Chrome 可能在与密码无关的普通 HTML 输入框上显示密码／付款管理 UI 的问题**  
  https://issues.chromium.org/issues/40912007

这些参考说明 Android 与浏览器之间的文件名传递行为。普通 Web JavaScript 中，KWC 只能看到浏览器公开的 `File.name`，无法直接查询 Android MediaStore。

## 文档渲染

- **GitHub Docs — Mermaid diagrams**  
  https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams
- **GitHub Docs — non-code file rendering**  
  https://docs.github.com/en/repositories/working-with-files/using-files/working-with-non-code-files
- **Mermaid — Flowchart syntax**  
  https://mermaid.js.org/syntax/flowchart.html
- **Mermaid — Sequence diagram syntax**  
  https://mermaid.js.org/syntax/sequenceDiagram.html

## 引用原则

- 尽量优先使用一手标准和官方厂商/项目文档。
- 引用某项标准或产品并不表示 KWC 实现了其中的全部功能。
- KWC 的实际行为、默认值、兼容规则和安全边界必须以 5.2.0 源代码及当前 KWC 文档为准。
- 旧版本文档仅作为历史资料保留，不用于定义当前 5.2.0 行为。
