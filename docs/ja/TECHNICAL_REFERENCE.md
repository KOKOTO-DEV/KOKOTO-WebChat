# KOKOTO WebChat 5.1.0 — 技術リファレンス

![KOKOTO WebChat 5.1.0 アーキテクチャ概要](../assets/architecture-5.1.0.svg)

[PNG](../assets/architecture-5.1.0.png) · [SVG](../assets/architecture-5.1.0.svg)

> **注記:** 図は理解を補助する資料です。KWC 固有の動作は実際のソースコードと本文の説明を基準にしてください。

## アーキテクチャ
`kwc-core` はローダー非依存のチャット、HTTP/SSE transport、履歴/非公開チャットストレージ、プロファイル/セキュリティ補助、Web Push、Relay v2 を担当します。Bukkit、Fabric、Forge、NeoForge はホスト/アダプター境界を通して、ローダー固有のプレイヤー、権限、スレッド、コンソール、native-message 連携だけを提供します。各マップアダプターと standalone frontend は同じ core の動作を利用します。

## HTTP と SSE
内蔵サービスは JDK `com.sun.net.httpserver.HttpServer` を `CoreHttpServer` の背後で使用します。REST 形式の handler が設定、履歴、認証、アップロード、非公開チャット、管理機能を提供します。リアルタイム更新には、上限管理された `SseHub` / `SseConnection` レジストリ経由の Server-Sent Events を使用します。認証済み frontend request は bearer authorization を使用し、SSE 接続時は長期 account token を URL に露出させない短時間有効な stream ticket を使用します。

## 認証情報とセッション
パスワードハッシュには、パスワードごとの salt と保存済み iteration count を持つ `PBKDF2WithHmacSHA256` を使用します。Session/CAPTCHA/rate-limit 補助は、必要に応じて機密 token を比較・保存する前にハッシュ化または制限処理します。管理者アクセスには role/permission/IP policy による追加制約があります。

## 永続化
公開履歴、DM、グループチャットは SQLite ストレージを使用します。SQLite は WAL journaling (`PRAGMA journal_mode=WAL`) で初期化されます。起動時の integrity/recovery 処理はメイン DB と `-wal` / `-shm` sidecar を扱うため、運用バックアップでも一貫した DB 状態を保持する必要があります。グループごとのメンバー入退室通知設定は `group_rooms.membership_events_enabled` に保存し、join/leave/kick/ban による実際のメンバーシップ変更は `group_messages.event_type` の `member_join` / `member_leave` イベントとして保存します。既存 DB には必要な列が自動追加されます。

## Web Push
`WebPushManager` は JDK のみで実装されています。SSRF リスクを抑えるため Push 送信先を検証し、VAPID key を管理し、HKDF で Web Push 用の暗号化鍵素材を導出して AES-GCM で保護した payload を送信します。ブラウザー通知設定はアカウント単位で扱えますが、実際の Push endpoint は端末固有です。

## プラットフォーム抽象化と完全一致ターゲット
`PlatformAdapter` と関連ホスト interface がローダー API を core から分離します。配布構成は Bukkit 1 + Fabric 16 + NeoForge 12 + Forge 16 = **45 個の配布成果物**です。ビルド補助は Minecraft 世代に応じて Java 17/21/25 を選択します。Windows の release helper は Gradle/Maven 実行前にパス長 preflight を行い、完全一致ターゲットの作業ディレクトリが検証済みの Windows パス条件を超える問題を早期検出します。

## Relay Protocol v2 の信頼モデル
Relay v2 は明示的な `groups -> peers` 構造を使用します。1 つのグループは対称的な信頼ドメインであり、共有シークレットはグループごとに 1 個だけです。peer 個別のシークレットはありません。空の group secret は provisioning request として扱われ、startup/reload で暗号学的に安全な 32-byte URL-safe secret を生成して `config.yml` に保存します。既存の空でない値は自動再生成しません。32 文字未満の手動 group secret は拒否され、同じ peer ID を複数のローカルグループで使用することもできません。双方が同じグループ内で互いを peer として登録する必要があります。

`/relay/v2/handshake` は protocol version、product version、group、sender ID、target ID、timestamp、nonce、sender outbound transport を HMAC で認証します。受信側は group membership、target identity、clock skew、nonce replay を検証します。この endpoint は状態を保持しない診断用 identity/health probe で、route 状態を作りません。direct `/relay/v2/message` は request ごとに独立して認証されます。受信側は送信側を同じ group・同じ shared secret で相互登録する必要があります。旧 v1 endpoint は HTTP 426 を返します。

## Relay payload の暗号処理
方向ごとに、グループ共有シークレットと方向コンテキストから HKDF-SHA256 で 256-bit key を導出します。`/relay/v2/message` はランダムな 12-byte IV と 128-bit tag を持つ AES-256-GCM を使用します。GCM AAD は `group`、`from`、`to`、`timestamp`、`nonce`、`IV` を結び付けます。応答も group/responder/requester/timestamp/request nonce/status/body の組に対して HMAC-SHA256 で独立して認証されるため、未認証の中継者は成功応答を偽造できません。timestamp、request nonce、relay ID/receipt ID、origin、hop-count の検査で replay/loop を防ぎます。

## Relay forwarding の信頼境界
直接 HTTP でも relay payload 自体は AES-GCM で保護されますが、HTTP には transport metadata の機密性と通常の TLS server authentication がないため警告対象です。direct relay は各 request を独立認証し、handshake endpoint は routing を制御しません。forwarding は group の `forwarding.enabled` と同一グループ routing を必要とし、http:// peer はその peer の incoming/outgoing forwarding だけ除外され、他の https:// peer は引き続き候補です。

Relay v2 は **ホップ単位の認証付き暗号化（hop-by-hop authenticated encryption）であり、end-to-end encryption ではありません**。forwarding server は受信 envelope を復号・検証・処理した後、次の peer 向けに再暗号化します。そのため、すべての forwarding server は信頼された参加者です。いずれかのメンバーからグループ共有シークレットが漏えいした場合は、そのグループの全メンバーでシークレットをローテーションする必要があります。

## 5.0.0 からの移行境界
移行処理は v1 の flat trust graph から v2 group membership を推測しません。旧 relay secret/peer/forwarding 設定を廃止し、運用者が v2 グループを明示設定するまで Relay を無効化します。これは意図的な fail-closed の信頼設定移行です。

## 非公開 Reply の永続化と Relay identity
DM/グループ Reply は表示文字列から推測せず、メタデータとして保存します。DM 書き込みでは対象が同一 thread に属することを検証し、グループ書き込みでは同一 room と現在の membership を検証します。サーバーは保存済みの元メッセージから canonical reply sender/preview を生成します。サーバー間 DM envelope は相手サーバーのローカル DB ID を送らず、`replyToRelayId` と sender/preview snapshot を運び、受信側は可能な場合に stable relay ID を自分のローカルメッセージ ID へ解決します。

## 設定表示のローカライズ
`PortableConfigMigration` は `ui.language` に応じて組み込み EN/KO/JA/ZH の config template を選択し、解析済みの運用設定値を上書き保持します。生成される reference と migration report も同じ言語を使用します。Semantic Difference は解析済み YAML の path/value を比較するため、コメント、レイアウト、引用形式、キー順だけの変更では差分を生成しません。

## 参照規格と公式ドキュメント

この文書で参照する一次規格と公式の外部プロジェクト文書は [REFERENCES.md](REFERENCES.md) にまとめています。

- [RFC 2104 — HMAC](https://www.rfc-editor.org/rfc/rfc2104.html)
- [RFC 5869 — HKDF](https://www.rfc-editor.org/info/rfc5869/)
- [NIST SP 800-38D — GCM](https://csrc.nist.gov/pubs/sp/800/38/d/final)
- [RFC 8018 — PBKDF2 / PKCS #5](https://www.rfc-editor.org/info/rfc8018/)
- [RFC 8291 — Web Push encryption](https://www.rfc-editor.org/info/rfc8291/)
- [RFC 8292 — VAPID](https://www.rfc-editor.org/info/rfc8292/)
- [WHATWG — Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)
- [SQLite — Write-Ahead Logging](https://sqlite.org/wal.html)
- [Oracle Java SE 17 — HttpServer](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html)
