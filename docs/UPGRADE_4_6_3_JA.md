# KOKOTO WebChat 4.6.3 upgrade

4.6.3 は group-chat message body の optional read-only administrator audit を追加します。既存の DM audit behavior は変更しません。

## New configuration

```yaml
group-chat:
  admin-audit:
    enabled: false

config-version: "4.6.3"
```

`4.6.2` として review 済みの config では、他の実際の不足がなければ migration fragment にこの switch と 4.6.3 review marker だけが出ます。既存 `config.yml` は上書きされません。

## Access requirements

両方が必要です。

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

通常 ADMIN/MODERATOR role だけでは body access はできません。audit view は read-only で room membership を必要とせず、room に参加せず read/unread state も変更しません。send/upload/hide や membership change もできません。各 page read は body を audit log にコピーせず `admin.group-audit-read` として記録されます。

## 設定コメント

4.6.3 では bundled `config.yml` のコメントを、現在の update check、cross-server DM の delivery/read ACK、group の read status、DM/group administrator audit の動作に合わせて更新します。起動時または `/kchat reload` 時、既存 config のコメントが **以前の KOKOTO WebChat bundled comment と完全一致する場合だけ** 新しい bundled comment に更新されます。この comment refresh は設定値を変更せず、ユーザーが編集したコメントは保持され、`config-version` も migration fragment の確認後に管理者が変更する方式のままです。
## プライベートチャットのメディア再生

DM とグループチャットのメッセージ一覧も、通常チャットと同じ stable key ベースの DOM 更新方式を使用します。同じ会話の更新では既存のメッセージと動画/音声 DOM を接続したまま維持し、新規・削除メッセージと配信/既読メタデータだけを更新します。そのため、メッセージの送受信中でも再生中のメディアが先頭から再開しません。すでに最下部を表示している場合だけ最新メッセージを追従し、途中を表示している場合は現在位置を維持します。会話を離れるか別の会話へ切り替えると、その会話のメッセージ/メディア DOM と private media-open 状態を完全に破棄します。再度開いた場合は `▶ Video` / `▶ Audio` の未展開 click-to-load 状態から新しく作成され、click-to-load が無効でも以前のプレイヤーは再利用せず、新しい未再生のメディア要素を作成します。再入室だけで `play()` は呼び出しません。

