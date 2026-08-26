# KOKOTO WebChat 5.0.0 総合ユーザー・運用マニュアル

> **5.0.0 運用:** Web Admin **Filter** で公開/グループ/任意 DM の block/mask/replace ルールとテストを管理し、**Settings** で guest/CAPTCHA、セッション、moderation、upload、filter の安全なライブ設定を管理します。ゲーム側は `/kchat filter` / `/kchat settings`。セッション期間変更は期限切れセッションを復活させず作成時刻基準で既存対象を再計算します。`upload.filename-mode: original` は新規アップロードの安全な Unicode 元名を保持し重複時に番号を付けます。


この文書は KOKOTO WebChat 5.0.0 の全機能を、利用者とサーバー管理者の両方の視点から説明します。設定項目ごとの詳細は `CONFIGURATION_JA.md`、サーバー間リレーは `SERVER_RELAY_JA.md`、HTTPS は `CADDY_HTTPS_JA.md` と `NGINX_HTTPS_JA.md` を参照してください。

## 1. 概要

KOKOTO WebChat は Minecraft サーバーのチャットをブラウザーに接続するサーバー側 Web チャットです。5.0.0 は Bukkit/Paper/Spigot に加え、Fabric 1.18.2〜26.2、NeoForge 1.20.2〜26.2、Forge 1.18.2〜26.2 の exact-target build を提供します。

主な利用形態:

- BlueMap 内の埋め込みチャット
- BlueMap を使わない standalone チャット
- 埋め込みと standalone の同時運用
- ゲームと Web の双方向公開チャット
- 保存型 DM とグループチャット
- DiscordSRV 連携
- 複数 Minecraft サーバー間の公開チャットリレー

標準 HTTP ポートは `8899`、API prefix は `/api`、standalone の内部 path は `/` で、既定のリバースプロキシでは `/chat` として公開します。

## 2. 必要環境

必須:

- 対応 platform: Bukkit/Paper/Spigot **1.18〜26.2**、Fabric exact-target **1.18.2〜26.2**、NeoForge exact-target **1.20.2〜26.2**、Forge exact-target **1.18.2〜26.2**
- 選択した Minecraft/server target が要求する Java。Bukkit artifact は Java 17 target です。Fabric/NeoForge/Forge exact-target build script は Minecraft target に応じて JDK 17/21/25 を選択し、26.x は Java 25 を使用します。
- Bukkit 系は `plugins/`、Fabric/NeoForge/Forge は `mods/` に platform JAR を配置できる管理権限

任意連携:

- BlueMap
- DiscordSRV
- ImageEmojis-Bero 1.9.x
- Caddy または Nginx

公開運用では `8899` を直接インターネットへ公開せず、`127.0.0.1:8899` に bind して HTTPS reverse proxy 経由で公開することを推奨します。

## 3. インストールと初回有効化

1. Bukkit/Paper/Spigot は対応 JAR を `plugins/` に、Fabric/NeoForge/Forge は対応 platform JAR を `mods/` に配置します。
2. サーバーを一度起動します。
3. `<KWC data dir>/config.yml` を確認します。`<KWC data dir>` は Bukkit 系では `plugins/KOKOTO-WebChat`、Fabric/NeoForge/Forge では `config/KOKOTO-WebChat` です。
4. 新規設定は `enabled: false` です。
5. URL、保存方式、保持期間、認証、アップロード制限を確認します。
6. 必要な機能を設定し `enabled: true` にします。
7. 再起動または `/kchat reload` を実行します。

```yaml
config-version: "5.0.0"
enabled: false
```

無効時は Web サービス、チャット転送、cleanup task は開始されません。管理者の `/kchat reload` は使用できます。

## 4. 設定マイグレーション

既存値は保持しますが、active migration では古い config text を継承しません。現在の bundled `config.yml` を新しい template として作成し、既存 operator 値だけを overlay します。旧 comment・順序・空白・indent は破棄され、最新 bundled comment/layout を使用します。

現在 version の完全な reference は常に次へ生成されます。

```text
<KWC data dir>/config-reference-5.0.0.yml
```

これは JAR 内の bundled `config.yml` を **comment と string scalar の double-quote 表記まで含めてそのまま**コピーした管理者確認用 file で、migration template には使用しません。`/kchat reload` は live service を停止する前に YAML を検証し、不正な YAML なら現在の実行設定を維持します。

`config-version` がない、または実行 version と異なる場合、KWC は実 `config.yml` に対して一度だけ migration を行います。

- 最新 bundled `config.yml` を新しい file の template にします。
- 既存 operator 値をその template に overlay します。
- 旧 comment・順序・空白・indent は引き継ぎません。
- 旧 marker が `*_auto_migration` でない場合、実 version upgrade 前に元の `config.yml` を backup します。
- 既存設定の bundled default が新 version で変わった場合は自動上書きせず review 対象に残します。
- 実ファイルを `config-version: "5.0.0_auto_migration"` とします。

その後、次を生成します。

```text
<KWC data dir>/config-migration-5.0.0.yml
```

これは不足設定を copy/paste する fragment ではなく **review report** です。自動挿入数、operator 判断が必要な default 変更、最終確認用の正確な version marker、current-vs-reference text diff を記録します。不足設定と comment はすでに実 config の適切な位置へ挿入されるため、diff の先頭に巨大な reference-only block として並びません。

判定基準:

| 実 `config.yml` | 動作 |
|---|---|
| `config-version` がない/以前/異なる | migration を実行し `5.0.0_auto_migration` にして migration/review report を生成 |
| `config-version: "5.0.0_auto_migration"` | 自動 migration 有効。startup/reload ごとに最新 bundled `config.yml` から再構築し、現在値を overlay して report/diff を更新 |
| `config-version: "5.0.0"` | 現在 version の自動 migration を停止。同一 version の migration/backfill を skip し、古い migration 案内を削除 |

この marker は **review 状態ではなく自動 migration の有効/無効**を表します。

```yaml
# 確認済みでも自動 migration を継続
config-version: "5.0.0_auto_migration"

# 同一 version の自動 migration を停止
config-version: "5.0.0"
```

後で実際の plugin version upgrade が発生した場合は、新しい version の `_auto_migration` 状態に入ります。
## 5. 配布モード

### 5.1 BlueMap addon

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true
frontend:
  standalone:
    enabled: false
```

Bukkit では KWC が BlueMap の web directory と `webapp.conf` を更新します。Fabric/NeoForge および Forge 26.1.2/26.2 + BlueMap 5.21+ では BlueMapAPI 2.8.0 から web root を取得し、script/style を API 登録するため `webapp.conf` は変更しません。BlueMap 連携が有効な場合、`/kchat reload` は `bluemap reload light` を自動要求し、次の BlueMap API `onEnable` で新しい KWC 設定を再登録します。

### 5.2 Pl3xMap 埋め込みモード

Pl3xMap を導入した Bukkit/Paper 系または Fabric サーバーでは次のように有効化します。

```yaml
adapters:
  pl3xmap:
    enabled: true
    api-base-url: ""
```

KWC は現在の Pl3xMap `config.yml` にある `settings.web-directory.path` を読み取り（`settings.yml` は旧版/フォーク向け fallback のみ）、Pl3xMap web root 内の KWC 専用 `kokoto-web-chat` asset と `index.html` の KWC marker block だけを管理します。Pl3xMap が Web file を再生成した場合は `/kchat reload` で再確認できます。現在の Pl3xMap 26.2 は Bukkit/Paper 系および Fabric/Quilt 向けで、NeoForge 向けではありません。direct HTTP では空の `api-base-url` が KWC `:8899/api` を使用し、NAT で外部 KWC port が変わる場合は実際の公開 API URL を指定します。

### 5.3 LiveAtlas 埋め込みモード

LiveAtlas は Dynmap、squaremap、Pl3xMap、Overviewer、または複数 server を表示できる static frontend です。Bukkit/Fabric/NeoForge/Forge では次のように有効化します。

```yaml
adapters:
  liveatlas:
    enabled: true
    api-base-url: ""
    web-root: ""
```

`web-root` が空の場合、`window.liveAtlasConfig` などの LiveAtlas marker を含む `index.html` だけを自動認識します。Caddy/nginx の別 directory から配信する場合は、server から見える共有/マウント済み path を `web-root` に指定します。KWC が管理するのは `kokoto-web-chat/` と marker block のみです。LiveAtlas file 更新後は `/kchat reload` を実行してください。同じ物理 web root に LiveAtlas adapter と backend 固有 adapter を同時指定しないでください。

### 5.4 uNmINeD static Web export

uNmINeD は Minecraft server 内で動く plugin ではなく、自己完結した static Web map を生成します。先に map を export し、その directory を KWC に指定します。

```yaml
adapters:
  unmined:
    enabled: true
    api-base-url: ""
    web-root: "/srv/www/unmined"
```

現在の uNmINeD export は `index.html`、旧 export は `unmined.index.html` の場合があります。KWC は uNmINeD marker を確認した file だけを patch し、`kokoto-web-chat/` と marker block だけを管理して map tile/library file は変更しません。uNmINeD で再 export すると HTML または KWC 所有 directory が置き換わる場合があるため、その後 `/kchat reload` を実行します。別 host で配信する場合は Minecraft server から変更できるよう export directory を共有/マウントしてください。

### 5.5 Minecraft Overviewer static Web map

Minecraft Overviewer は設定した `outputdir` に Leaflet ベースの static Web map を render します。先に map を生成し、その directory を KWC に指定します。

```yaml
adapters:
  overviewer:
    enabled: true
    api-base-url: ""
    web-root: "/srv/www/overviewer"
```

KWC は Overviewer 固有の生成 marker/asset を確認できる `index.html` だけを patch し、独自の `kokoto-web-chat/` directory と marker block だけを管理して Overviewer tile/config/Leaflet asset は変更しません。後の Overviewer render または `--update-web-assets` で HTML が再生成される場合があるため、その後 `/kchat reload` を実行します。別 host で render/配信する場合は Minecraft server から変更できるよう output directory を共有/マウントしてください。独自の persistent template を管理する場合は Overviewer の `customwebassets` option を別途利用できます。

### 5.6 standalone のみ

```yaml
adapters:
  bluemap:
    auto-install: false
    auto-patch-webapp-conf: false
frontend:
  standalone:
    enabled: true
    path: "/"
```

```text
http://server.example.com:8899/
```

### 5.7 両方を使用

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true
frontend:
  standalone:
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
adapters:
  bluemap:
    api-base-url: ""
```

HTTPS reverse proxy:

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: "/chat"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"
adapters:
  bluemap:
    enabled: true
    api-base-url: ""
frontend:
  standalone:
    enabled: true
    api-base-url: ""
```

公開例:

```text
https://map.example.com/
https://map.example.com/chat/api
https://map.example.com/chat
```

通常は `frontend.standalone.api-base-url`、`upload.public-base-url`、`emoji.public-base-url` を空にします。`X-Forwarded-For` は `trusted-proxies` に登録された proxy からのみ信頼されます。

IP 判定確認用:

```yaml
http:
  log-client-ip-resolution: true
```

確認後は無効にしてください。

#### 逆配置: standalone を `/`、BlueMap を `/chat/`

標準配置とは逆にすることもできます。内部 standalone path は `/` のまま、`http.public-prefix: ""` に設定し、`/chat/` だけ prefix を除去して BlueMap に送り、それ以外を KWC に送ります。

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: ""

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""
```

公開 path:

```text
https://map.example.com/       KWC standalone
https://map.example.com/api    KWC API
https://map.example.com/chat/  BlueMap
```

`frontend.standalone.path` を `/chat` に変更しません。外部配置は reverse proxy と `http.public-prefix` が決めます。


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

5.0.0 から login user の表示設定は複数の KWC account profile として保存でき、guest のみ browser-local preset を使います。window 位置・size・minimize 状態、最後に選択した profile ID、Web Push 登録は localStorage/device-local のままです。login user の notification 種別と keyword alert は browser ごとではなく account 共通です。browser-local notification inbox で最近の通知対象 event を確認できます。

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

KOKOTO WebChat 5.0.0 は、保存または relay の前に管理者設定の `:alias:` token を置換できます。標準 alias は英語のみで、管理者が任意の言語の alias に変更・追加できます。

- `:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, `:br:` → 改行 1 行
- `:blankline:`, `:emptyline:`, `:paragraphbreak:` → 空行 1 行
- `:tab:`, `:indent:` → 設定数の space（既定 4）

未知の token はそのまま残るため、ImageEmojis/custom emoji token と共存できます。`:\n:` のような backslash escape は解釈しません。`custom` には printable text の置換を追加できます。Minecraft では通常の CR/LF は従来どおり 1 行へ flatten し、`newline`/`blank-line` alias で作成した改行だけを最終 game delivery で明示的な複数 chat line として送ります。server relay の game 表示でも受信側に同じ 4.7.0 token-line 対応が必要です。

YAML の list 設定は inline (`aliases: [bullet, arrow]`) と block (`aliases:` の次行に `- bullet`) の両形式を使用できます。indent は通常の ASCII space のみを使用し、tab と全角 space は使用できません。不正な設定は `/kchat reload` が live service を停止する前に拒否するため、現在の実行設定と UI 言語は維持されます。

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
/kchat auth <code>
```

権限:

```text
kwc.auth
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
/kchat password <newPassword>
/kchat status
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
  admin-permission: "kwc.admin"
```

ローカル管理者:

```text
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
```

Session:

```text
/kchat sessions
/kchat revoke <username>
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
/kchat guest mute guest <name> [minutes] [reason]
/kchat guest mute ip <address> [minutes] [reason]
/kchat guest unmute guest <name>
/kchat guest unmute ip <address>
/kchat guest list
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
- Web sender: `/kchat dm <realName> `
- 他サーバーの game sender: `/kchat dm <realName>@<server-id> `

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
/kchat reply <messageId> 
```

送信:

```text
/kchat reply <messageId> <message>
```

権限:

```text
kwc.reply
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
/kchat dm
/kchat dm list [pageSize]
/kchat dm unread [pageSize]
/kchat dm list next
/kchat dm list prev
/kchat dm <player> <message>
/kchat dm read <player> [pageSize]
/kchat dm next
/kchat dm prev
/kchat dm hide <messageId>
```

権限:

```text
kwc.dm
```

`capture-game-whispers: true` では `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` を KWC DM にも記録します。同一サーバーの通常 whisper 自体は置き換えません。別サーバー宛ては `名前@server-id` を指定すると `/kchat dm 名前@server-id <message>` に変換され、署名付き cross-server DM relay で送信されます。サーバー指定のない `/kchat dm <名前>` は現在のサーバー内だけを検索します。対象を含まない `/r`, `/reply` は既存 whisper plugin の last-target state と競合するため intercept しません。

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
/kchat group
/kchat group list
/kchat group rooms
/kchat group <room|id> <message>
/kchat group send <room|id> <message>
/kchat group read <room|id> [pageSize]
/kchat group next
/kchat group prev
/kchat gc ...
```

権限: `kwc.group`

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

`filename-mode: original` でも、クリップボードアップロードは `clipboardData.files` から取得できる長いファイル名を優先します。Windows/Chromium が別のクリップボード項目で `202608~1.JPG` のような DOS 8.3 別名を返しても、長い名前を取得できる場合は元の長い名前を使用します。ブラウザーが 8.3 別名しか公開しない場合は、その別名を元ファイル名として保存せず `clipboard-...` 形式の名前へ置き換えます。

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
<KWC data dir>/emojis/default/wave.png
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

KWC 変換を使う場合は `preserve`, `label`, `link` mode を選択します。

## 22. ImageEmojis-Bero 1.9.x

推奨設定:

```yaml
emojisFolder: "/KOKOTO-WebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

権限:

```text
imageemojis.use
```

`replaceInCommands` は `/kchat reply`, `/kchat dm`, `/kchat group` の token 変換に必要です。Relay server すべてで pack/file name を一致させます。

```text
/emojis reload
/emojis update
```

詳細は `IMAGEEMOJIS_BERO_1_9_0_JA.md`。

## 23. Browser notification と Web Push

5.0.0 では login user の keyword/notification 種別設定を account data に保存し、browser/device 間で共有します。Windows/mobile など表示条件が異なる場合は account ごとに複数の UI profile を保存でき、window 位置・size・minimize 状態・Web Push endpoint は device-local のままです。profile 上限と JSON import/export 可否は Web Admin で管理します。同じ device に有効な KWC Web Push subscription がある場合、live page 側の OS Notification は重複表示しません。


server-side 表示 profile は次で制御します。

```yaml
ui:
  user-profiles:
    enabled: true
    max-profiles: 5
    allow-import-export: true
```

`max-profiles` は 0-20。profile import/export は表示設定のみが対象で、session/identity/Push/device window data は含みません。



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
frontend:
  standalone:
    app-name: "Web Chat"
    app-short-name: "Web Chat"
ui:
  picture-in-picture:
    enabled: false
```

Install 済み PWA の名前変更は再インストールが必要な場合があります。

## 25. DiscordSRV

5.0.0 の管理者向け Discord keyword alert では、検出や format policy を DiscordSRV に委譲しません。KWC が keyword、source、mention、重複除去、alert 本文を決定し、DiscordSRV の認証済み JDA connection と Channels mapping のみ再利用します。Web Admin の alert channel selector には DiscordSRV の logical channel 名だけを表示し、logical 名がない ID-only 構成だけ channel ID を fallback として使用します。Discord 由来 message は再 alert しません。

管理者 alert policy 例:

```yaml
admin-alerts:
  discord:
    enabled: false
    channel: ""
    sources:
      public-chat: true
      relay-chat: false
      dm: false
      group-chat: false
    mention: "none"
    case-sensitive: false
    keywords: ""
```

`channel: ""` は `discordsrv.channel` を再利用し、Web Admin では通常 DiscordSRV の logical channel 名を選択/保存します。




```yaml
discordsrv:
  enabled: true
  channel: "global"
  web-to-discord: true
  game-relay-mode: "discordsrv"
  discord-to-web: true
  ignore-bot-messages: true
  suppress-game-echo: true
  suppress-game-echo-seconds: 5
  append-web-emoji-links: true
  append-game-emoji-links: true
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
```

DiscordSRV が game chat を転送している場合は `game-relay-mode: "discordsrv"` を維持します。

複数 server が同じ channel を使う場合:

- 原本 game event を見た server だけが DiscordSRV message を加工
- relay receiver は Discord に再送しない
- `[Server]`, `[Web]` を重複追加しない

Reply preview は `discordsrv.reply-relay` で選択的に有効化できます。

## 26. Server relay

`peers` は常時接続セッションではなく、このサーバーがメッセージを送信する HTTP 宛先一覧です。同じ `id` / `secret` は受信要求の認証にも使用され、双方向通信には両側で相手を登録する必要があります。

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
  forward-received-public-chat: true
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
      url: "https://server2.example.com/chat/api"
      secret: ""
      enabled: true
```

`forward-received-public-chat` は peer が受信した公開チャットを他の peer へ再転送するかを制御します。`true` は hub/chain 構成、`false` は直接 peer 間のみの公開チャットにします。サーバー間 DM と既読通知のルーティングには影響しません。

実際の URL:

```text
https://server2.example.com/chat/api/relay/receive
```

Receiver の `peers[].id` は sender の `server-id` と一致する必要があります。Peer secret は shared secret より優先されます。Offline queue はありません。

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

Error:

- 403 `unknown_peer`

同じ destination から成功応答なしで `403 unknown_peer` が 3 回返ると、KWC はその destination を 60 秒の backoff 状態にします。その 60 秒間に発生した relay message は送信せず、定期 probe も送りません。60 秒経過後に最初に発生した実際の relay message で自動的に再試行し、失敗した場合はその失敗時刻から再び 60 秒待機します。成功応答が返れば通常送信へ即時復帰し、counter もリセットされます。受信側は unknown sender の直接 request を適用前に拒否します。接続拒否や timeout などの transport failure も同じ 3 回/60 秒 backoff を使用するため、offline peer に対して転送 message ごとに警告が繰り返されません。
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
    skip-unchanged: true
```

公開チャットの仮想スクロールはコンテンツ種類を区別せず、画像・動画・音声・リンクプレビュー・YouTube/その他 iframe を同じメッセージ範囲/高さ追跡ルールで扱います。

## 31. Command 一覧

User:

```text
/kchat auth <code>
/kchat password <newPassword>
/kchat status
/kchat dm
/kchat dm list [pageSize]
/kchat dm unread [pageSize]
/kchat dm <player> <message>
/kchat dm read <player> [pageSize]
/kchat dm next
/kchat dm prev
/kchat dm hide <messageId>
/kchat reply <messageId> <message>
/kchat group list
/kchat group <room> <message>
/kchat group send <room> <message>
/kchat group read <room> [pageSize]
/kchat group next
/kchat group prev
```

Admin:

```text
/kchat reload
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
/kchat guest mute <guest|ip> <value> [minutes] [reason]
/kchat guest unmute <guest|ip> <value>
/kchat guest list
/kchat sessions
/kchat revoke <username>
```

Alias: `/kc`。group は `/kchat gc` も使用できます。

## 32. Permission

```text
kwc.auth
kwc.webchat
kwc.dm
kwc.reply
kwc.group
kwc.admin
kwc.update.notify
```

User permission は default true、`kwc.admin` と `kwc.update.notify` は OP default です。

## 33. Data と backup

```text
<KWC data dir>/config.yml
<KWC data dir>/history.db
<KWC data dir>/direct-messages.db
<KWC data dir>/group-messages.db
<KWC data dir>/web-push-subscriptions.jsonl
<KWC data dir>/emojis/
<KWC data dir>/uploads/
<KWC data dir>/audit/
```

Backup 対象は config、DB/JSONL、emoji、必要な upload、Push subscription/VAPID key です。SQLite は clean shutdown 後の copy を推奨します。

## 34. Reload と restart

`/kchat reload`:

- 多くの config 変更
- HTTP service と relay の再生成
- UI default 更新

Restart 必須:

- JAR 交換
- Java code 変更
- load order 変更

`/kchat reload` は `bluemap reload light` を自動要求します。asset がまだ古い場合または自動実行に失敗した場合は `/bluemap reload light` を手動実行してください。

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
- `/kchat reload` は通常 `bluemap reload light` を自動実行します。必要な場合のみ `/bluemap reload light` を手動実行してください。
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
- DiscordSRV 転送時は game-relay-mode discordsrv

Web Push:

- HTTPS
- notification permission
- Service Worker/Push API
- iOS Home Screen app
- VAPID

## 36. 関連文書

- `CONFIGURATION_JA.md`
- `UPGRADE_5_0_0_JA.md`: 4.7.0→5.0.0 core split / reload safety
- `UPGRADE_4_7_0_JA.md`: 4.6.3→4.7.0 feature upgrade
- `SERVER_RELAY_JA.md`
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



## SimpleNicks-Bero 連携

Bukkit/Paper 系では `player-display.mode: "display-name"` で [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) の Bukkit display name を表示できます。詳細は `SIMPLENICKS_BERO_JA.md`。一般運用は [upstream SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks) を参照してください。
