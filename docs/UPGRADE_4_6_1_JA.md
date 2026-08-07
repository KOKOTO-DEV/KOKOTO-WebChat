# 4.6.0 から 4.6.1 へのアップグレード

## 主な変更

- relay message に player UUID がある場合、remote server の player を既存 web DM recipient search から検索できます。
- 既存 private chat metadata list から、任意で DM body を read-only audit view で確認できます。
- Modrinth を使う簡潔な update check と管理者 join 通知を追加しました。
- plugin/config version は `4.6.1` です。

## 設定 migration

`config-version: "4.6.0"` の config で 4.6.1 を起動すると次を生成します。

```text
plugins/BlueMapWebChat/config-migration-4.6.1.yml
```

```yaml
update-check:
  enabled: true

direct-message:
  admin-audit:
    enabled: false

config-version: "4.6.1"
```

実際の `config.yml` は自動変更されません。DM本文確認が明確に必要でない限り audit は無効のままにしてください。

## DM本文 audit を有効化

両方の条件が必要です。

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

direct-message:
  admin-audit:
    enabled: true
```

- 通常の ADMIN/MODERATOR role だけでは本文を閲覧できません。
- audit view は read-only です。
- 各 page read は audit log に記録され、message body 自体は log にコピーされません。
- 設定変更後は `/bmchat reload` または再起動を実行します。JAR差し替えには server restart が必要です。

## 修正版 4.6.1 ビルド

release number は 4.6.1 のままです。cross-server DM を交換する全サーバーへ修正版 4.6.1 を導入してください。`server · source` のクリックは対象投稿の UUID と origin server ID を直接渡し、remote search と既存 remote thread でも server ID と player UUID を保持します。
