# KOKOTO WebChat 5.2.0 — 導入・運用

![KWC 配備モード](../assets/deployment-modes.svg)

[PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg)

> **注記:** 図は理解を補助する資料です。KWC 固有の動作は実際のソースコードと本文の説明を基準にしてください。

## プラットフォームの選択
サーバーのプラットフォームと Minecraft バージョンに一致する成果物を使用します。Bukkit/Paper/Spigot は Java 17 の単一プラグイン、Fabric と Forge はそれぞれ 16 個の完全一致ターゲット、NeoForge は 12 個の完全一致ターゲットがあります。ローダー向けビルドは Minecraft 世代に応じて Java 17/21/25 を選択します。KWC の主要チャット機能はサーバー側で動作し、クライアント Mod は必須ではありません。

## 初回起動と Web 公開
一度起動して KWC のデータと設定を生成します。standalone frontend、対応マップアダプター、またはその両方を利用できます。インターネットへ公開する場合は、可能な限り内蔵 HTTP サービスを loopback にバインドし、Caddy/Nginx で HTTPS を終端してください。loopback 以外のアドレスで平文 HTTP を公開すると、KWC は明示的に警告します。

## 設定のライフサイクル
現在の `config.yml` を編集した後、`/kchat reload` を実行します。Reload は稼働中 service を置き換える前に YAML を検証するため、不正な YAML の場合は以前の実行設定を維持します。`config-reference-5.2.0.yml` は組み込み `ui.language` と同じ言語で表示する現在の管理者向け default です。5.2.0 upgrade は対応する parsed operator value を保持します。歴史的な最初の 5.0.0 → 5.1.0 relay migration のみ Relay v1 trust 設定を意図的に reset し、通常の 5.1.0 → 5.2.0 upgrade は既存 Relay v2 group/secret/peer を保持します。`ui.language` は `config.yml` comment template、generated reference、migration/difference prose にも適用され、Difference は YAML path/value を比較します。

5.0.0 → 5.1.0 移行では 5.1.0 のテンプレートから設定を再構築し、対応している運用設定値を保持しますが、Relay v1 の信頼設定は意図的にリセットします。5.1.0 では `ui.language` が `config.yml` のコメントテンプレート、生成される reference、migration/Difference 文面の言語も選択します。解析済みの運用設定値は変更せずに上書き保持され、Difference は書式ではなく YAML の path/value を比較します。

## Relay Protocol v2
`server-relay.groups` を明示的に設定します。各グループは shared secret を 1 個だけ持ち、peer 個別の secret はありません。新しい group は 1 台のサーバーで `shared-secret: ""` のまま起動/リロードし、その `config.yml` に生成された値を同じ group の他サーバーへコピーします。既存の空でない値は保持され、32 文字未満の手動 secret は invalid のままです。同一グループ内で双方が互いを peer として登録する必要があります。直接 HTTP でも Relay payload 自体は暗号化・認証されますが警告対象です。forwarding は同一グループの HTTPS→HTTPS の場合だけ許可されます。Relay v1 endpoint は HTTP 426 を返します。詳細は `SERVER_RELAY.md` を参照してください。

## 更新と配布
5.2.0 以降、updater は canonical Modrinth `kokoto-webchat` のみを確認し、旧 BMWC project URL は実際の update source として使用しません。公開前には 45 個の配布ターゲット、SHA-256、現在の release text を検証してください。Windows では完全ビルドの前に build-path preflight を実行し、検証済み範囲を超える長いソースパスを早期に拒否します。

## バックアップ
KWC データディレクトリ全体をバックアップし、SQLite の sidecar (`-wal` / `-shm`) を一貫した状態で保存してください。確実なオフラインバックアップが必要な場合はサーバーを停止してから取得します。設定、アカウント/プロファイル、Push subscription、アップロード/絵文字、Relay 設定をまとめて保存してください。

## セキュリティ運用
HTTPS、強力な管理者認証情報、制限的な管理者 IP ルール、最小権限、明示的な super-admin 一覧を使用してください。Relay のグループ共有シークレットはグループ全体の対称信頼鍵です。いずれかのメンバーから漏えいした場合は、グループ内の全サーバーで同じシークレットを更新してください。

## トラブルシューティングの順序
1. 起動/reload 時の警告を確認します。  
2. 現在の公開 URL とリバースプロキシを確認します。  
3. ローダー/バージョン成果物と Java 世代を確認します。  
4. Relay では group ID、相互 peer ID、共有シークレット、時刻同期、HTTPS topology を確認します。  
5. DB やファイルを手作業で修復する前に、ログと設定を保存します。
