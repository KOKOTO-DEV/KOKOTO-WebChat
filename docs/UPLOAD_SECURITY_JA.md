# KOKOTO WebChat アップロードのセキュリティ注意点

![アップロードセキュリティ処理フロー](assets/upload-security-pipeline.svg)

ファイルアップロードは便利ですが、公開サーバーでは悪用される可能性があります。既定設定ではゲストアップロードは無効です。

## 推奨既定値

```yaml
upload:
  enabled: true
  allow-guest-upload: false
  allow-user-upload: true
  allow-moderator-upload: true
  allow-admin-upload: true
  max-file-size-mb: 20
  max-files-per-message: 3
  cooldown-seconds: 5
  max-uploads-per-minute: 4
  retention-days: 5
```

## 公開サーバー

別のモデレーション体制がない限り、ゲストアップロードは無効のままにしてください。有効にする場合はファイルサイズ制限を下げ、保持期間を短くしてください。

## 公開 URL

直接 HTTP 例:

```yaml
upload:
  public-base-url: ""
```

同一ドメイン HTTPS プロキシ例:

```yaml
http:
  path-prefix: "/api"
  public-prefix: "/chat"

upload:
  # 推奨は空です。アップロード URL は自動的に /chat/api に従います。
  public-base-url: ""

emoji:
  # 推奨は空です。絵文字 URL は自動的に /chat/api に従います。
  public-base-url: ""
```

従来の明示指定も使用できます:

```yaml
upload:
  public-base-url: "/chat/api"        # プラグインが /uploads を追加
  # または: "/chat/api/uploads"

emoji:
  public-base-url: "/chat/api"        # プラグインが /emojis を追加
  # または: "/chat/api/emojis"
```

先頭が `/` の値は同一 origin のブラウザパスとして扱われます。同一ドメインプロキシでは完全な FQDN は不要です。

## 許可拡張子


```yaml
upload:
  allowed-extensions:
    - png
    - jpg
    - jpeg
    - gif
    - webp
    - mp4
    - webm
    - mp3
    - m4a
    - ogg
    - wav
    - flac
```

チャットで表示または共有したいファイル形式だけを許可してください。KOKOTO WebChat は拡張子とサイズで制限しますが、公開運用では制限されたディレクトリから配信し、HTTPS を使うことを推奨します。`upload.max-total-size-mb` で `upload.directory` 直下の通常ファイルの合計保存容量も制限できます。`0` は無制限です。有効時は古い未参照アップロードから削除し、それでも不足する場合は新しいアップロードを拒否します。

### URL 設定の解決規則

`http.public-prefix + http.path-prefix` が HTTPS 公開 API の基準で、既定値は `/chat/api` です。adapter/standalone の API override は互いに独立しており、通常は空のままにします。upload/emoji が空なら共通 API base に `/uploads`、`/emojis` を追加します。


管理者向けカスタム絵文字メモ: 絵文字ファイル名またはフォルダー名を変更すると `:emoji:pack/name:` トークンも変わります。古いトークンを含む既存メッセージは、古いファイル/フォルダー名を残さない限り表示されなくなる場合があります。

カスタム絵文字アップロードも `emoji.max-total-size-mb` の合計容量制限に従います。制限を超えると管理 UI に警告が表示されます。`emoji.show-storage-usage` と `emoji.show-storage-limit` で現在/最大の絵文字容量表示を制御できます。
