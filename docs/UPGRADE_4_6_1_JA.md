# 4.6.0 から 4.6.1 へのアップグレード

**5.1.0 注記:** この管理者 DM 本文監査の動作は 5.1.0 でも維持されています。`direct-message.admin-audit.enabled` と `private-chat-super-admins` を併用し、監査 view は read-only です。


## 主な変更

- relay message に player UUID がある場合、remote server の player を既存 web DM recipient search から検索できます。
- 既存 private chat metadata list から、任意で DM body を read-only audit view で確認できます。
- Modrinth を使う簡潔な update check と管理者 join 通知を追加しました。
- plugin/config version は `4.6.1` です。

## 設定 migration

`config-version: "4.6.0"` の config で 4.6.1 を起動すると次を生成します。

```text
plugins/KOKOTO-WebChat/config-migration-4.6.1.yml
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
- 設定変更後は `/kchat reload` または再起動を実行します。JAR差し替えには server restart が必要です。

## Cross-server DM version requirement

cross-server DM を交換するすべてのサーバーで KOKOTO WebChat 4.6.1 以降を使用してください。`server · source` のクリックは対象投稿の UUID と origin server ID を直接渡し、remote search と既存 remote thread でも server ID と player UUID を保持します。
