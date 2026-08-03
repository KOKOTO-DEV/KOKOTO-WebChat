# 4.5.5 から 4.6.0 へのアップグレード

## 最初にバックアップ

サーバーを停止し、`plugins/BlueMapWebChat` をバックアップしてください。特に `config.yml`、SQLite DB と `-wal`/`-shm`、DM/group DB、upload、emoji、custom language、audit log、Web Push key/subscription を含めます。

## 自動生成される設定 migration fragment

BlueMapWebChat は既存 `config.yml` を自動上書き・自動 merge しません。サーバー起動時と `/bmchat reload` 時に実ファイルの `config-version` を確認します。

- `config-version` が実行中の plugin version と一致する場合、確認済みとして比較を省略し、同じ version の古い fragment を削除します。
- marker がない、または異なる場合、実 config と同梱 current default を比較して次を生成・更新します。

判定表:

| installed config の状態 | migration file |
|---|---|
| version marker なし | 他の差分がなくても生成 |
| running plugin と異なる | 生成または更新 |
| running plugin と一致 | 生成しない。残っている同 version の案内も削除 |

```text
plugins/BlueMapWebChat/config-migration-4.6.0.yml
```

生成ファイルは構造化 report ではなく、そのまま参照・コピーできる YAML 設定 fragment です。次を表示します。

- 実 `config.yml` にない設定と現在の推奨 default
- bundled default が変わり、実設定値が旧 default のままの設定
- 最終確認 marker となる対象 `config-version`

version 情報、件数、旧・新 default の説明はすべて `#` comment だけで記録します。`migration:`, `summary:`, `settings-to-add:`, `preserved-custom-values:`, `obsolete-settings-to-review:`, `finalize-after-review:` のような情報用 YAML section は作りません。custom 値や obsolete 候補の情報一覧も出力しません。

必要な設定だけを実 `config.yml` の同じ場所へ merge してください。実設定ファイルは自動変更されません。

不足設定も changed bundled default もない場合でも migration file を生成し、対象 `config-version` を含めます。これにより version marker のない設定も明示的に確認済みとして管理できます。

4.6.0 は 4.5.5 default を比較 baseline として同梱します。version marker がない config は 4.5.5 以前として扱います。明示された未知 version では誤判定を避けるため missing key のみ報告し、default 変更は推測しません。

確認後、実 config に設定します。

```yaml
config-version: "4.6.0"
```

version が一致している間、以後の起動と reload は比較を省略します。

## 4.6.0 の主な追加設定

- top-level `server-relay:`
- `direct-message.capture-game-whispers`
- `reply.game-click.enabled`
- `reply.game-click.local-game-chat`
- `reply.game-command-format`
- Discord `{server}`, `{server_id}` placeholder

version が不一致の間、実ファイルにない private-message capture と local-chat replacement は安全のため runtime で無効になります。

## DB migration

public SQLite history には relay metadata column が追加型 `ALTER TABLE` で追加されます。既存 row は保持されますが、過去 row の origin server は復元できません。初回 4.6.0 起動前に DB をバックアップしてください。

## 推奨確認

1. old config で起動し、`config.yml` を変更せず fragment が作られることを確認します。
2. missing setting と changed default を確認して merge します。
3. `config-version: "4.6.0"` を設定し `/bmchat reload` 後に比較省略を確認します。
4. relay、game reply、whisper DM、Discord label、URL、ImageEmojis をテストします。
