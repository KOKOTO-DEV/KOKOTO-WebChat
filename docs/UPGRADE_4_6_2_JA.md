# 4.6.1 から 4.6.2 へのアップグレード

BlueMapWebChat 4.6.2 は DM 配信の信頼性を改善し、同名 DM のルーティング問題を修正し、メッセージ単位の既読状態を追加します。新しい管理者向け設定項目はありません。

## 変更点

- server 指定のない DM 名は現在サーバーの player だけを解決します。他サーバー宛先は `server-id + UUID` で明示的に識別します。
- 他サーバー DM は宛先サーバーが実際に保存したことを確認してから配信完了とします。route / HTTP / timeout / destination failure は retry 可能な失敗として残ります。
- DM retry は永続 relay ID を再利用し、Web send は client message ID を使うため、request / response が不確実でも同じ message を重複保存しません。restart で中断された pending 配信は retry 可能な失敗として復旧します。
- group chat の Web send も client message ID で重複を防止します。正常送信完了の label は表示せず、時刻の横には処理中の `送信中`、失敗時の `失敗 · 再試行` だけを短く表示します。
- DM と group chat の**すべてのメッセージ**について既読状態を計算し、時刻表示の横に表示します。1 対 1 DM は相手が読む前に短い `未読` label、読んだ後に `✓` を表示します。group chat は未読受信者数を数字で表示し、0 人になると `✓` を表示します。
- 他サーバー DM の既読状態は認証済み private relay で返され、hub / chain 構成でも元の message 側へ反映されます。会話を再度開くと最新の既読 ACK を安全に再送するため、一時的な relay / HTTP 障害で既読マークが恒久的に失われることはありません。
- 多段 DM relay は最終宛先の保存確認後だけ上流へ success を返します。
- 更新通知を修正しました。通知対象の管理者がログインすると、以前の定期確認結果だけに依存せず、レート制限付きで Modrinth を再確認します。OP を明示的に通知対象として扱い、確認失敗は warning log に記録し、reload 時には以前の update listener を解除します。

## Config migration

4.6.1 と比べて 4.6.2 の bundled config に新しい管理者向け key はなく、既存 default も変更されません。review marker だけ更新します。

```yaml
config-version: "4.6.2"
```

review 済み 4.6.1 config で起動すると `plugins/BlueMapWebChat/config-migration-4.6.2.yml` を生成します。無関係な不足設定や local/default 差分がなければ、新しい `config-version` marker だけが含まれます。実際の `config.yml` は自動上書きされません。

## Cross-server deployment

server 間 DM を交換するすべての server は BlueMapWebChat 4.6.2 以降を使用してください。JAR 交換後に各 server を再起動すると、新しい relay 処理と追加型 DB migration が有効になります。既存 DM / group chat message は保持されます。
