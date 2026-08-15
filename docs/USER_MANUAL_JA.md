# BlueMapWebChat 4.7.0 総合ユーザー・運用マニュアル

この文書は BlueMapWebChat 4.7.0 の全機能を、利用者とサーバー管理者の両方の視点から説明します。設定項目ごとの詳細は `CONFIGURATION_JA.md`、サーバー間リレーは `SERVER_RELAY_JA.md`、HTTPS は `CADDY_HTTPS_JA.md` と `NGINX_HTTPS_JA.md` を参照してください。

## 1. 概要

BlueMapWebChat は Bukkit/Paper/Spigot 互換 Minecraft サーバーのチャットをブラウザーに接続するプラグインです。

主な利用形態:

- BlueMap 内の埋め込みチャット
- BlueMap を使わない standalone チャット
- 埋め込みと standalone の同時運用
- ゲームと Web の双方向公開チャット
- 保存型 DM とグループチャット
- DiscordSRV 連携
- 複数 Minecraft サーバー間の公開チャットリレー

標準 HTTP ポートは `8899`、API prefix は `/api`、standalone path は `/chat` です。

## 2. 必要環境

必須:

- Bukkit/Paper/Spigot 互換サーバー
- プラグイン JAR を配置できる管理権限

任意連携:

- BlueMap
- DiscordSRV
- ImageEmojis-Bero 1.9.0
- Caddy または Nginx

公開運用では `8899` を直接インターネットへ公開せず、`127.0.0.1:8899` に bind して HTTPS reverse proxy 経由で公開することを推奨します。

## 3. インストールと初回有効化

1. JAR を `plugins/` に配置します。
2. サーバーを一度起動します。
3. `plugins/BlueMapWebChat/config.yml` を確認します。
4. 新規設定は `enabled: false` です。
5. URL、保存方式、保持期間、認証、アップロード制限を確認します。
6. 必要な機能を設定し `enabled: true` にします。
7. 再起動または `/bmchat reload` を実行します。

```yaml
config-version: "4.7.0"
enabled: false
```

無効時は Web サービス、チャット転送、cleanup task は開始されません。管理者の `/bmchat reload` は使用できます。

## 4. 設定マイグレーション

既存の設定値は自動上書きされません。startup/reload 時に既知の最上位 config block を 4.7.0 bundled default 順へ並べ替えますが、各 block の現在の text・設定値・custom comment は保持し、default にない最上位 block は最後に元の順序で残します。

`config-version` がない、または実行中バージョンと異なる場合、次のファイルが生成されます。

```text
plugins/BlueMapWebChat/config-migration-4.7.0.yml
```

プラグインは `plugins/BlueMapWebChat/config-reference-4.7.0.yml` も生成します。これは現在 JAR の完全な default config をコメント込みでそのままコピーした基準ファイルです。古い config や version marker のない config はこの完全 reference と比較し、migration fragment は実際に確認する差分一覧として使用してください。migration file 末尾には current と reference の text diff を comment として追加しますが、同一行は出力しません。各差分は file 名、別行の `Line` または `Lines`、実際に異なる内容の順で表示します。差分 source line は元の YAML indent を保持するため行頭に `#` だけを直接付け、reference-only block は current config への挿入位置も別に表示します。

判定基準:

| 実際の `config.yml` | 動作 |
|---|---|
| `config-version` がない | 他の差分が 0 件でも対象 version marker を含む migration file を生成 |
| `config-version` が plugin version と異なる | 不足・変更設定と対象 version marker を含む file を生成・更新 |
| `config-version` が plugin version と一致 | 確認済みとして migration 比較/report 生成を省略し、古い migration 案内は削除するが、完全 reference file は最新状態に維持 |

含まれる内容:

- 現在の設定にない新規項目
- 旧 default のままで、新バージョンで default が変更された項目
- 最終確認 marker の対象 `config-version`

他の設定差分がなくても、設定 version 管理のため `config-version` を含む file を生成します。説明や旧値は `#` comment のみで、実際の `config.yml` は変更されません。確認後に次を設定します。

```yaml
config-version: "4.7.0"
```

バージョンが一致すると比較を省略します。

## 5. 配布モード

### 5.1 BlueMap addon

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true
standalone-web:
  enabled: false
```

Web asset が更新されない場合:

```text
/bluemap reload
```

### 5.2 standalone のみ

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false
standalone-web:
  enabled: true
  path: "/chat"
```

```text
http://server.example.com:8899/chat
```

### 5.3 両方を使用

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true
standalone-web:
  enabled: true
```

アカウント、履歴、通知、サーバー設定は共有されます。

## 6. HTTP・HTTPS・公開 URL

テスト用の直接 HTTP:

```yaml
http:
  host: "0.0.0.0"
  port: 8899
  path-prefix: "/api"
  cors-origin: "*"
web-addon:
  api-base-url: ""
```

HTTPS reverse proxy:

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"
web-addon:
  api-base-url: "/bmwc/api"
standalone-web:
  enabled: true
  api-base-url: ""
```

公開例:

```text
https://map.example.com/
https://map.example.com/bmwc/api
https://map.example.com/bmwc/chat
```

通常は `standalone-web.api-base-url`、`upload.public-base-url`、`emoji.public-base-url` を空にします。`X-Forwarded-For` は `trusted-proxies` に登録された proxy からのみ信頼されます。

IP 判定確認用:

```yaml
http:
  log-client-ip-resolution: true
```

確認後は無効にしてください。

## 7. Web UI

主な画面:

- 公開メッセージ一覧
- 入力欄
- ログイン・ログアウト
- DM・グループチャット
- 検索
- ピン留め
- 絵文字 picker
- アップロード
- 通知設定
- 管理・moderation panel

ユーザー設定は browser localStorage に保存されます。Panel は移動・resize・size 記憶に対応し、browser-local notification inbox で最近の通知対象 event を確認できます。

```yaml
ui:
  language: "en-US"
  language-fallback: "en-US"
  time-zone: "local"
  theme: "system"
  opacity: 0.92
  resizable: true
  remember-window-size: true
```

言語: `en-US`, `ko-KR`, `ja-JP`, `zh-CN`

テーマ: `system`, `dark`, `light`, `high-contrast`

## 8. 公開チャット

ゲーム→Web:

```yaml
chat:
  broadcast-ingame-chat-to-web: true
```

Web→ゲーム:

```yaml
chat:
  send-web-chat-to-game: true
  web-user-to-game-format: "[Web] {player}: {message}"
  web-guest-to-game-format: "[Web Guest] {guest}: {message}"
  web-admin-to-game-format: "[Web Admin] {player}: {message}"
```

Web→Web:

```yaml
chat:
  broadcast-web-chat-to-web: true
```

長さ制限:

```yaml
chat:
  max-message-length: 120
  max-url-message-length: 2048
```

`0` は無制限です。

### 8.5 メッセージトークン

BlueMapWebChat 4.7.0 は、保存または relay の前に管理者設定の `:alias:` token を置換できます。標準 alias は英語のみで、管理者が任意の言語の alias に変更・追加できます。

- `:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, `:br:` → 改行 1 行
- `:blankline:`, `:emptyline:`, `:paragraphbreak:` → 空行 1 行
- `:tab:`, `:indent:` → 設定数の space（既定 4）

未知の token はそのまま残るため、ImageEmojis/custom emoji token と共存できます。`:\n:` のような backslash escape は解釈しません。`custom` には printable text の置換を追加できます。Minecraft では通常の CR/LF は従来どおり 1 行へ flatten し、`newline`/`blank-line` alias で作成した改行だけを最終 game delivery で明示的な複数 chat line として送ります。server relay の game 表示でも受信側に同じ 4.7.0 token-line 対応が必要です。

```yaml
message-tokens:
  enabled: true
  max-replacements-per-message: 24
  newline:
    aliases: [enter, newline, nextline, linebreak, br]
  blank-line:
    aliases: [blankline, emptyline, paragraphbreak]
  tab:
    aliases: [tab, indent]
    spaces: 4
  custom: {}
```

## 9. 履歴と検索

推奨は SQLite です。

```yaml
chat:
  history-storage: "sqlite"
  history-sqlite-file: "history.db"
  history-retention-days: 5
  history-size: 0
  history-page-size: 80
```

- `sqlite`: 長期運用・検索向け
- `jsonl`: legacy single-file
- `memory`: 再起動で消去

JSONL から SQLite への初回移行:

```yaml
chat:
  history-sqlite-migrate-jsonl: true
```

検索:

```yaml
search:
  enabled: true
  result-limit: 50
```

本文、送信者、日時、source、system/event の有無を検索できます。大きすぎる limit は DB と応答サイズの負荷になります。

## 10. アカウント連携とログイン

Web で code を発行し、ゲームで実行:

```text
/bmchat auth <code>
```

権限:

```text
bluemapwebchat.auth
```

```yaml
auth:
  enabled: true
  link-code-length: 6
  link-code-expire-seconds: 180
  link-code-cooldown-seconds: 3
  link-code-max-per-minute: 10
```

Web password:

```text
/bmchat password <newPassword>
```

```yaml
auth:
  password-login: true
  remember-session-days: 30
```

Role: USER, MODERATOR, ADMIN。

Permission から ADMIN を自動設定:

```yaml
auth:
  auto-admin-from-permission: true
  admin-permission: "bluemapwebchat.admin"
```

ローカル管理者:

```text
/bmchat admin create <id>
/bmchat admin password <id> <password>
/bmchat admin role <id> <user|moderator|admin>
```

Session:

```text
/bmchat sessions
/bmchat revoke <username>
```

## 11. セキュリティ

```yaml
security:
  login-fail-limit: 5
  login-fail-window-seconds: 300
  login-lock-seconds: 600
  max-sse-connections-per-ip: 5
  max-sse-connections-total: 200
```

`0` は該当制限を無効化します。

管理者 login IP 制限:

```yaml
admin:
  allow-admin-login-from: []
```

公開環境では HTTPS と強い password を使用してください。

## 12. Guest chat と captcha

```yaml
guest:
  enabled: true
  allow-custom-name: true
  name-prefix: "Guest-"
  cooldown-seconds: 6
  max-messages-per-minute: 50
  block-player-name-spoofing: true
```

Captcha:

```yaml
captcha:
  mode: "math"
  expire-seconds: 120
  require-on-each-message: false
  pass-valid-minutes: 120
```

Mute command:

```text
/bmchat guest mute guest <name> [minutes] [reason]
/bmchat guest mute ip <address> [minutes] [reason]
/bmchat guest unmute guest <name>
/bmchat guest unmute ip <address>
/bmchat guest list
```

## 13. プレイヤー名・hover・click

```yaml
player-display:
  mode: "name"
  strip-colors: true
```

`name`, `display-name`, `custom-name` を使用できます。

```yaml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

Placeholder: `{display}`, `{real}`, `{uuid}`, `{source}`

名前 click:

- 同じサーバーの game player: `/w <realName> `
- Web sender: `/bmchat dm <realName> `
- 他サーバーの game sender: `/bmchat dm <realName>@<server-id> `

## 14. 公開メッセージ reply

```yaml
reply:
  game-click:
    enabled: true
    local-game-chat: true
  game-command-format: "&8[&dReply&8] &f{player}&7: &f{message}"
  game-preview:
    enabled: true
    format: "&7{sender}: {preview}"
    max-length: 120
  game-prefix:
    enabled: true
    text: "↪ [Reply] "
```

本文 click:

```text
/bmchat reply <messageId> 
```

送信:

```text
/bmchat reply <messageId> <message>
```

権限:

```text
bluemapwebchat.reply
```

URL 部分はリンクを開き、URL 以外だけ reply command を提案します。chat formatter と競合する場合は `local-game-chat: false` にします。

## 15. DM

```yaml
direct-message:
  enabled: true
  storage: "auto"
  retention-days: 0
  max-messages-per-thread: 0
  max-message-length: 500
  allow-web-send: true
  allow-game-send: true
  capture-game-whispers: true
  notify-on-login: true
  notify-on-message: true
  web-unread-badge: true
  confirm-hide: true
```

DM 宛先は UUID で識別されます。local join/linked account の記録に加えて、server relay で受信した game または linked web message に `playerUuid` がある場合、その送信者の表示名と実 Minecraft 名を Web DM の新規宛先検索へ登録します。公開 chat で見た別サーバーの名前を通常の DM 検索に入力して会話を開始できます。最新の名前は `known-display-names.yml` に保持され、再起動後も検索できます。UUID のない guest/Discord message は登録されません。`storage: auto` は公開 chat が JSONL の場合だけ DM も JSONL、それ以外は SQLite を使用します。`sqlite`/`jsonl` の明示指定も可能です。

Command:

```text
/bmchat dm
/bmchat dm list [pageSize]
/bmchat dm unread [pageSize]
/bmchat dm list next
/bmchat dm list prev
/bmchat dm <player> <message>
/bmchat dm read <player> [pageSize]
/bmchat dm next
/bmchat dm prev
/bmchat dm hide <messageId>
```

権限:

```text
bluemapwebchat.dm
```

`capture-game-whispers: true` では `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` を BMChat DM にも記録します。同一サーバーの通常 whisper 自体は置き換えません。別サーバー宛ては `名前@server-id` を指定すると `/bmchat dm 名前@server-id <message>` に変換され、署名付き cross-server DM relay で送信されます。サーバー指定のない `/bmchat dm <名前>` は現在のサーバー内だけを検索します。対象を含まない `/r`, `/reply` は既存 whisper plugin の last-target state と競合するため intercept しません。

### 15.2 送信・既読状態

正常送信完了には状態ラベルを表示しません。local の送信要求を処理中のときだけ `送信中`、配信を確認できない場合だけ `失敗 · 再試行` を時刻表示の横に短く表示します。既読状態は DM のすべてのメッセージで時刻表示の横に表示し、1 対 1 DM では相手が未読なら `未読`、読んだ後は `✓` を表示します。group chat は従来どおり未読受信者数を数字で表示します。他サーバー DM の既読通知は認証済み server relay で返され、元の message 側にも同じ状態が反映されます。会話を再度開くと最新の既読 ACK を安全に再送するため、一時的な relay / HTTP 障害があっても後の閲覧で既読マークを復旧できます。

## 16. Group chat

```yaml
group-chat:
  enabled: true
  allow-web-send: true
  allow-public-rooms: true
  allow-room-passwords: true
  retention-days: 30
  max-messages-per-room: 1000
  max-message-length: 500
  max-rooms-per-user: 20
  max-members-per-room: 50
  max-room-name-length: 32
  invite-expire-hours: 72
  sqlite-file: "group-messages.db"
```

Web では公開・非公開 room、PBKDF2 hash で保存される password、invite、leave、hide/restore、member kick/block、owner transfer、unread tracking を使用できます。

group chat のすべてのメッセージに、その message の受信者既読状態を表示します。数字は**送信時点ですでに room に参加しており、現在も member である受信者のうち未読の人数**です。message sender は受信者ではないため count 対象には含まれません。未読受信者が 0 人になると数字は `✓` に変わります。正常送信完了自体には状態 label を表示しません。

```text
/bmchat group
/bmchat group list
/bmchat group rooms
/bmchat group <room|id> <message>
/bmchat group send <room|id> <message>
/bmchat group read <room|id> [pageSize]
/bmchat group next
/bmchat group prev
/bmchat gc ...
```

権限: `bluemapwebchat.group`

## 17. System/event announcement

```yaml
announcements:
  broadcast-to-web-chat: true
```

Default ON: join, quit, first join, death, advancement, server start/stop。

Default OFF: world, gamemode, level, bed, web login/logout。

i18n key がある場合は Web UI language に合わせて表示できます。

## 18. Pinned message

```yaml
pinned:
  enabled: true
  max-pins: 20
  show-to-logged-out: true
  preserve-uploads: true
```

通常履歴とは別に保存され、top bar から開きます。参照 upload は cleanup から保護できます。

## 19. Upload

```yaml
upload:
  enabled: true
  allow-guest-upload: false
  allow-user-upload: true
  allow-moderator-upload: true
  allow-admin-upload: true
  cooldown-seconds: 5
  max-uploads-per-minute: 4
  max-file-size-mb: 20
  max-total-size-mb: 0
  max-files-per-message: 3
  directory: "uploads"
  retention-days: 5
  clipboard-upload-enabled: true
  clipboard-upload-send-mode: "insert"
```

対応: PNG/JPG/JPEG/GIF/WEBP, MP4/WEBM, MP3/M4A/OGG/WAV/FLAC。

`max-total-size-mb: 0` は無制限です。Clipboard mode は `insert` または `send` です。

## 20. Media preview

```yaml
upload:
  preview-images: true
  preview-videos: true
  preview-audio: true
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  youtube-nocookie: true
  youtube-max-embeds-per-message: 1
```

YouTube Shorts は vertical player で処理されます。`ui.image-preview-max-per-message` と `ui.image-preview-max-height` で数と高さを制限し、Google Drive preview は `ui.google-drive-image-preview` で有効化できます。

```yaml
preview:
  social-embeds:
    enabled: true
    click-to-load: true
    max-embeds-per-message: 2
    tiktok:
      enabled: false
    x:
      enabled: false
      theme: "auto"
      dnt: true
```

External embed は third-party request を発生させるため public server では click-to-load を推奨します。

Discord CDN cache:

```yaml
preview:
  external-media-cache-enabled: true
  cache-discord-cdn: true
  external-media-cache-retention-days: 5
```

## 21. Custom emoji

```yaml
emoji:
  enabled: true
  show-button: true
  directory: "emojis"
  max-file-size-kb: 512
  max-total-size-mb: 64
  render-size-px: 32
  picker-size-px: 44
  message-token-limit: 12
  token-format: "short"
```

```text
plugins/BlueMapWebChat/emojis/default/wave.png
:default/wave:
:emoji:default/wave:
```

管理者は folder 作成、upload、rename、delete ができます。rename すると過去 token が解決できなくなる場合があります。

Game-side renderer を使う場合:

```yaml
emoji:
  game-link:
    enabled: false
```

BMChat 変換を使う場合は `preserve`, `label`, `link` mode を選択します。

## 22. ImageEmojis-Bero 1.9.0

推奨設定:

```yaml
emojisFolder: "/BlueMapWebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

権限:

```text
imageemojis.use
```

`replaceInCommands` は `/bmchat reply`, `/bmchat dm`, `/bmchat group` の token 変換に必要です。Relay server すべてで pack/file name を一致させます。

```text
/emojis reload
/emojis update
```

詳細は `IMAGEEMOJIS_BERO_1_9_0_JA.md`。

## 23. Browser notification と Web Push

```yaml
notifications:
  enabled: true
  only-when-hidden: true
  notify-normal-chat: true
  notify-dm: true
  notify-group-chat: true
  notify-mentions: true
  notify-replies: true
  notify-system: true
  notify-keywords: true
  notify-own-messages: true
  show-message-preview: true
```

```yaml
web-push:
  vapid-public-key: ""
  vapid-private-key: ""
  subject: "mailto:admin@example.com"
  notification-title: ""
  ttl-seconds: 300
```

VAPID key が空なら plugin が永続 key を生成します。iOS/iPadOS は Home Screen web app が必要な場合があります。

## 24. PWA と PIP

```yaml
standalone-web:
  app-name: "Web Chat"
  app-short-name: "Web Chat"
ui:
  picture-in-picture:
    enabled: false
```

Install 済み PWA の名前変更は再インストールが必要な場合があります。

## 25. DiscordSRV

```yaml
discordsrv:
  enabled: true
  channel: "global"
  web-to-discord: true
  game-to-discord: false
  discord-to-web: true
  ignore-bot-messages: true
  suppress-game-echo: true
  suppress-game-echo-seconds: 5
  append-web-emoji-links: true
  append-game-emoji-links: true
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-to-discord-format: "[{server}] {sender}: {message}"
```

DiscordSRV が game chat を転送している場合は `game-to-discord: false` を維持します。

複数 server が同じ channel を使う場合:

- 原本 game event を見た server だけが DiscordSRV message を加工
- relay receiver は Discord に再送しない
- `[Server]`, `[Web]` を重複追加しない

Reply preview は `discordsrv.reply-relay` で選択的に有効化できます。

## 26. Server relay

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "Server1"
  shared-secret: "long-shared-secret"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server2"
      url: "https://server2.example.com/bmwc/api"
      secret: ""
      enabled: true
```

実際の URL:

```text
https://server2.example.com/bmwc/api/relay/receive
```

Receiver の `peers[].id` は sender の `server-id` と一致する必要があります。Peer secret は shared secret より優先されます。Offline queue はありません。

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

Error:

- 403 `unknown_peer`
- 401 `bad_signature`
- 401 `expired_request`
- 404 `relay_disabled`
- 426 `unsupported_protocol`

詳細は `SERVER_RELAY_JA.md`。

## 27. Web command panel

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: "ADMIN"
  show-button: true
  run-from-chat-input: false
  require-confirm: true
  max-length: 0
  broadcast-result-to-web-chat: false
```

`allow-all: true` は Web account から任意 console command を許可するため非常に危険です。HTTPS、IP restriction、強い password、preset 制限を使用してください。

```yaml
commands:
  presets:
    - id: "day"
      label: "昼に変更"
      command: "time set day"
      confirm: true
```

## 28. 管理・moderation

```yaml
moderation:
  enabled: true
  allow-web-admin-panel: true
  allow-moderator-message-delete: true
  allow-moderator-guest-mute: true
  default-mute-minutes: 60
```

機能:

- message hide/delete
- pin 管理
- guest/IP mute
- session revoke
- emoji 管理
- upload/storage usage
- private chat metadata
- console command panel

Private metadata super admin:

```yaml
private-chat-super-admins:
  - "ExactMinecraftName"
  - "00000000-0000-0000-0000-000000000000"
```

default では participant、count、size、retention、cleanup metadata のみ扱います。

DM本文 audit が必要な場合は次も有効にします。

```yaml
direct-message:
  admin-audit:
    enabled: true
```

`private-chat-super-admins` とこの switch の両方を満たす account だけが管理者 DM thread を read-only で開けます。通常の ADMIN/MODERATOR role だけでは本文を閲覧できません。audit view では送信、participant ごとの hide、read 状態更新はできません。各 page load は `admin.dm-audit-read` として audit log に記録され、本文自体は log にコピーされません。

Audit:

```yaml
audit:
  enabled: true
  directory: "audit"
```

## 29. Web font と表示

```yaml
web-fonts:
  enabled: true
  directory: "fonts"
  items:
    - family: "Pretendard"
      file: "Pretendard.woff2"
      weight: 400
      style: "normal"
```

対応: WOFF2, WOFF, TTF, OTF。

```yaml
ui:
  font-size: 13
  message-font-size: 13
  input-font-size: 13
  text-color: ""
  ui-text-color: ""
  input-background-color: ""
  text-shadow-mode: "auto"
```

空の色は theme default です。

## 30. Virtual scroll と性能

```yaml
ui:
  virtual-scroll:
    enabled: true
    overscan-screens: 0.75
    min-rendered-messages: 30
    preserve-visible-media: false
    preserve-playing-media: true
  history-preload:
    screens: 0.7
    min-px: 200
```

長い履歴の browser rendering cost を削減します。

```yaml
ui:
  resume-refresh:
    enabled: true
    min-interval-seconds: 5
    skip-while-media-active: true
    skip-unchanged: true
```

## 31. Command 一覧

User:

```text
/bmchat auth <code>
/bmchat password <newPassword>
/bmchat dm
/bmchat dm list [pageSize]
/bmchat dm unread [pageSize]
/bmchat dm <player> <message>
/bmchat dm read <player> [pageSize]
/bmchat dm next
/bmchat dm prev
/bmchat dm hide <messageId>
/bmchat reply <messageId> <message>
/bmchat group list
/bmchat group <room> <message>
/bmchat group send <room> <message>
/bmchat group read <room> [pageSize]
/bmchat group next
/bmchat group prev
```

Admin:

```text
/bmchat reload
/bmchat admin create <id>
/bmchat admin password <id> <password>
/bmchat admin role <id> <user|moderator|admin>
/bmchat guest mute <guest|ip> <value> [minutes] [reason]
/bmchat guest unmute <guest|ip> <value>
/bmchat guest list
/bmchat sessions
/bmchat revoke <username>
```

Alias: `/bmc`, `/bluemapchat`, group の `/bmchat gc`。

## 32. Permission

```text
bluemapwebchat.auth
bluemapwebchat.webchat
bluemapwebchat.dm
bluemapwebchat.reply
bluemapwebchat.group
bluemapwebchat.admin
bluemapwebchat.update.notify
```

User permission は default true、`bluemapwebchat.admin` と `bluemapwebchat.update.notify` は OP default です。

## 33. Data と backup

```text
plugins/BlueMapWebChat/config.yml
plugins/BlueMapWebChat/history.db
plugins/BlueMapWebChat/direct-messages.db
plugins/BlueMapWebChat/group-messages.db
plugins/BlueMapWebChat/web-push-subscriptions.jsonl
plugins/BlueMapWebChat/emojis/
plugins/BlueMapWebChat/uploads/
plugins/BlueMapWebChat/audit/
```

Backup 対象は config、DB/JSONL、emoji、必要な upload、Push subscription/VAPID key です。SQLite は clean shutdown 後の copy を推奨します。

## 34. Reload と restart

`/bmchat reload`:

- 多くの config 変更
- HTTP service と relay の再生成
- UI default 更新

Restart 必須:

- JAR 交換
- Java code 変更
- load order 変更

BlueMap asset だけ古い場合は `/bluemap reload`。

## 35. Troubleshooting

Web が開かない:

- `enabled: true`
- host/port
- port conflict
- proxy upstream
- standalone enabled

BlueMap button がない:

- auto-install/auto-patch
- BlueMap path
- log
- `/bluemap reload`
- browser cache

Login 失敗:

- HTTPS/cookie path
- code expiry
- lockout
- password-login
- admin IP restriction

Game/Web 転送不良:

- `send-web-chat-to-game`
- `broadcast-ingame-chat-to-web`
- chat plugin event conflict
- relay delivery

Reply 不良:

- game-click enabled
- local-game-chat
- URL 部分は link open が正常

Emoji text のまま:

- file と pack name
- ImageEmojis permission
- replaceInCommands
- reload/update
- relay 全 server の同一 file

Relay 403:

- receiver peer ID と sender server-id
- receiver reload
- active peer log

Relay 401:

- secret
- proxy body/header
- clock sync

Discord prefix 重複:

- 全 server の build を統一
- 他 plugin の repost
- DiscordSRV 転送時は game-to-discord false

Web Push:

- HTTPS
- notification permission
- Service Worker/Push API
- iOS Home Screen app
- VAPID

## 36. 関連文書

- `CONFIGURATION_JA.md`
- `SERVER_RELAY_JA.md`
- `UPGRADE_4_6_4_JA.md`: 4.6.3→4.7.0 upgrade
- `UPGRADE_4_6_3_JA.md`: 4.6.2→4.6.3 upgrade
- `UPGRADE_4_6_2_JA.md`: 4.6.1→4.6.2 upgrade
- `UPGRADE_4_6_1_JA.md`: 4.6.0→4.6.1 upgrade
- `UPGRADE_4_6_0_JA.md`
- `CADDY_HTTPS_JA.md`
- `NGINX_HTTPS_JA.md`
- `IMAGEEMOJIS_BERO_1_9_0_JA.md`
- `INSTALL_TROUBLESHOOTING_JA.md`
- `UPLOAD_SECURITY_JA.md`
- `OPERATIONS_SECURITY_JA.md`
- `I18N_JA.md`
- `RELEASE_CHECKLIST_JA.md`

### 管理者 group-chat body audit (4.6.3)

`group-chat.admin-audit.enabled: true` を設定し、exact Minecraft name または UUID を `private-chat-super-admins` に登録する必要があります。両方の条件が必須です。対象管理者は room member でなくても管理者 room metadata list から body を read-only で開けます。audit view は room 参加、read/unread state 更新、message send/upload/hide、membership 変更を行いません。各 page read は `admin.group-audit-read` として記録され、message body は audit log にコピーされません。

