# KOKOTO WebChat 5.3.0 — 参照規格と公式ドキュメント

このページでは、KOKOTO WebChat 5.3.0 の技術・運用ドキュメントが参照する**一次規格と公式製品／プロジェクト文書**をまとめます。KWC 固有の動作は、このリポジトリの 5.3.0 ソースコードと現行リリース文書が基準です。以下のリンクは、基盤となるプロトコル、API、保存方式、外部連携を説明するための参照資料です。

## 暗号化と認証

- **RFC 2104 — HMAC: Keyed-Hashing for Message Authentication**  
  https://www.rfc-editor.org/rfc/rfc2104.html  
  Relay Protocol v2 のハンドシェイク／応答認証の説明で参照します。
- **RFC 5869 — HMAC-based Extract-and-Expand Key Derivation Function (HKDF)**  
  https://www.rfc-editor.org/info/rfc5869/  
  Relay v2 の方向別鍵導出と Web Push の鍵導出の説明で参照します。
- **NIST SP 800-38D — Galois/Counter Mode (GCM) and GMAC**  
  https://csrc.nist.gov/pubs/sp/800/38/d/final  
  AES-GCM 認証付き暗号化の説明で参照します。
- **RFC 8018 — PKCS #5: Password-Based Cryptography Specification Version 2.1**  
  https://www.rfc-editor.org/info/rfc8018/  
  PBKDF2 によるパスワードハッシュの説明で参照します。
- **RFC 8291 — Message Encryption for Web Push**  
  https://www.rfc-editor.org/info/rfc8291/  
  Web Push ペイロード暗号化の説明で参照します。
- **RFC 8292 — VAPID for Web Push**  
  https://www.rfc-editor.org/info/rfc8292/  
  KWC の VAPID アプリケーションサーバー識別の説明で参照します。

## HTTP、SSE、JDK API

- **RFC 9110 — HTTP Semantics**  
  https://www.rfc-editor.org/rfc/rfc9110.html
- **WHATWG HTML Standard — Server-sent events / EventSource**  
  https://html.spec.whatwg.org/multipage/server-sent-events.html
- **Oracle Java SE 17 — `com.sun.net.httpserver.HttpServer`**  
  https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html

## 永続化

- **SQLite — Write-Ahead Logging**  
  https://sqlite.org/wal.html  
  KWC の SQLite WAL 保存とバックアップの説明で参照します。

## リバースプロキシとマップ配信

- **Caddy — `reverse_proxy` directive**  
  https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
- **NGINX — `ngx_http_proxy_module`**  
  https://nginx.org/en/docs/http/ngx_http_proxy_module.html
- **BlueMap Wiki**  
  https://bluemap.bluecolored.de/wiki/
- **BlueMap Reverse Proxy guide**  
  https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html

## Discord 連携

- **DiscordSRV Documentation**  
  https://docs.discordsrv.com/
- **DiscordSRV Initial Setup**  
  https://docs.discordsrv.com/installation/initial-setup/

## 任意連携プロジェクト

- **ImageEmojis 上流**  
  https://github.com/MrQuackDuck/ImageEmojis
- **SimpleNicks 上流**  
  https://github.com/Simplexity-Development/SimpleNicks

上流 文書は元プロジェクトの動作を説明するものです。KWC-Bero 連携や KWC 固有の互換性は、該当する KWC 文書と 5.3.0 ソースコードを基準にしてください。

## Android のファイル選択

- **Android Developers — OpenableColumns / DISPLAY_NAME**  
  https://developer.android.com/reference/android/provider/OpenableColumns.html
- **Chromium Issue 387440285 — Android picker で Browse を経由せず直接選択した場合に大きな数値ファイル名が露出することがある問題**  
  https://issues.chromium.org/issues/387440285
- **Chromium Issue 40912007 — Android 版 Chrome がパスワードと無関係な通常の HTML 入力欄にもパスワード／支払い管理 UI を表示する場合がある問題**  
  https://issues.chromium.org/issues/40912007

これらの参照は Android／ブラウザー間のファイル名引き渡し動作を説明します。通常の Web JavaScript から KWC が取得できるのはブラウザーが公開した `File.name` だけで、Android MediaStore を直接照会することはできません。

## ドキュメント表示

- **GitHub Docs — Mermaid diagrams**  
  https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams
- **GitHub Docs — non-code file rendering**  
  https://docs.github.com/en/repositories/working-with-files/using-files/working-with-non-code-files
- **Mermaid — Flowchart syntax**  
  https://mermaid.js.org/syntax/flowchart.html
- **Mermaid — Sequence diagram syntax**  
  https://mermaid.js.org/syntax/sequenceDiagram.html

## 参照方針

- 可能な限り一次規格と公式ベンダー／プロジェクト文書を優先します。
- 参照リンクがあることは、KWC がその規格や製品の全機能を実装することを意味しません。
- KWC の実動作、既定値、互換性ルール、セキュリティ境界は、必ず 5.3.0 ソースコードと現行 KWC 文書で確認します。
- 過去バージョンの文書は履歴資料として保持し、現在の 5.3.0 動作定義には使用しません。
