# KOKOTO WebChat 5.2.0 総合ユーザー・運用マニュアル


## ビジュアルマップ

| 項目 | 図 |
| --- | --- |
| アーキテクチャ | [PNG](../assets/architecture-5.2.0.png) · [SVG](../assets/architecture-5.2.0.svg) |
| Relay Protocol v2 | [Animated GIF](../assets/relay-v2-flow.gif) · [PNG](../assets/relay-v2-flow.png) · [SVG](../assets/relay-v2-flow.svg) |
| DM/グループ Reply | [PNG](../assets/private-reply-flow.png) · [SVG](../assets/private-reply-flow.svg) |
| 設定移行 | [Animated GIF](../assets/config-language-migration.gif) · [PNG](../assets/config-language-migration.png) · [SVG](../assets/config-language-migration.svg) |
| 配備モード | [PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg) |
| アップロードセキュリティ | [PNG](../assets/upload-security-pipeline.png) · [SVG](../assets/upload-security-pipeline.svg) |
| Web Push | [PNG](../assets/web-push-flow.png) · [SVG](../assets/web-push-flow.svg) |

この文書で参照する一次規格と公式の外部プロジェクト文書は [REFERENCES.md](REFERENCES.md) にまとめています。

> **5.2.0 運用:** Web Admin **Filter** で公開/グループ/任意 DM の block/mask/replace ルールと送信しないテストを管理し、**Settings** では対応しているライブ設定（guest/CAPTCHA、セッション、ユーザープロファイル、Open chat/DM/group の typing-indicator policy、管理者アラート、upload、content-filter）のみを管理します。moderation の 5 つのポリシー設定は `config.yml` 専用で Web Admin には公開しません。ゲーム側は `/kchat filter` / `/kchat settings`。セッション期間変更は期限切れセッションを復活させず作成時刻基準で既存対象を再計算します。`upload.filename-mode: original` は新規アップロードの安全な Unicode 元名を保持し重複時に番号を付けます。


この文書は KOKOTO WebChat 5.2.0 の全機能を、利用者とサーバー管理者の両方の視点から説明します。設定項目ごとの詳細は `CONFIGURATION.md`、サーバー間リレーは `SERVER_RELAY.md`、HTTPS は `CADDY_HTTPS.md` と `NGINX_HTTPS.md` を参照してください。


## 5.2.0 の追加機能

- **メッセージ reaction:** ログインユーザーは公開 chat、DM、通常の group-chat message に Unicode または KWC custom emoji reaction を追加/解除できます。group の join/leave event は対象外です。実 reaction がない場合、32 × 16px の `+` button は本文の下と次の message の前にそれぞれ 1px の視覚的余白を取り、文字を覆いません。reaction OFF では元の 8px message spacing を維持し、実 reaction が付いた時だけ通常の in-flow row/chip spacing を使います。picker は emoji 文字、server-generated Unicode name、admin-managed search alias、custom emoji ID/name/pack を検索します。alias は **Admin > Emojis > Reaction icons** で `emoji = search words` として編集し `reaction-search-aliases.txt` に保存します。category/search 再描画後も位置と outside-click close を維持し、hover chip では反応者名を scroll list で表示し、通常のチャット送信者と同じ表示名 ↔ 元の名前の切り替えを使用します。master ON/OFF と custom emoji 許可は他の Admin settings と同じ rounded themed row を使います。機能 OFF では既存 data を保持して read-only 表示し、すべての local/Relay mutation を拒否します。
- **保存済み会話:** `chat.conversation-archive.enabled: true`（既定）の場合、公開/DM/group の先頭・末尾メッセージを指定して private snapshot として保存します。server が無効化すると保存関連 DOM を生成せず archive API も登録せず、archive DB も開く/新規作成しません。通常 retention で原本が消えても snapshot は残りますが、管理者による原本/room 強制削除と private room lock policy が常に優先されます。attachment bytes は複製せず、原本 image が残っていれば印刷/PDF に表示し、その他の file は link、原本がなければ unavailable と表示します。PDF は現在の KWC appearance を使用します。server 側の保存上限は `chat.conversation-archive` 配下の `max-archives-per-user`、`max-messages-per-archive`、`max-messages-per-user` で制限します。
- **typing indicator:** public/DM/group typing は event-driven です。サーバー管理者が Web Admin **Settings** または `chat.typing-indicator.*` で Open chat / DM / Group chat を個別に切り替え、既定値は OFF / ON / ON です。`chat.typing-indicator.user-display-control` は既定 OFF で、管理者が有効にするとログインユーザーの Chat settings にアカウント保存の **入力中表示** が 1 個現れます。個人 OFF は自分の画面で受信した表示だけを隠し、自分の typing 送信には影響しません。最初の input event で 5 秒表示し、その window 中は追加送信せず、polling・message DB write・常駐 typing worker はありません。表示は composer の 1 行上に rounded high-opacity pill として浮き、通常 chat と同じ表示名 ↔ 元の名前切替を使い、名前の formatting tag は除去します。font は user chat font の約 80% に追従しつつ base UI font より小さくなりません。複数の長い名前が 1 行に収まらない場合は人数表示へ自動短縮します。
- **private room header:** 長い title のみ ellipsis し、member count・Settings・Leave の幅を保持します。Invite、保存済み会話、hide、room management は権限に応じて Settings 内へ集約します。
- **auto-follow:** 公開/DM/group 共通で 32px bottom threshold を使います。emoji/icon/attachment panel による layout 移動は viewport を保持し、その変化だけでは即時に最下部へ強制 scroll しません。
- **Relay 2.1:** KWC product version と relay protocol revision を分離しました。Protocol major 2 が wire compatibility 境界で、2.1 は `public`, `dm`, `read`, `reaction`, `reaction-authority`, `typing` capability を通知します。

## 1. 概要

KOKOTO WebChat は Minecraft サーバーのチャットをブラウザーに接続するサーバー側 Web チャットです。5.2.0 は Bukkit/Paper/Spigot に加え、Fabric 1.18.2〜26.2、NeoForge 1.20.2〜26.2、Forge 1.18.2〜26.2 の exact-target build を提供します。

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
config-version: "5.2.0"
enabled: false
```

無効時は Web サービス、チャット転送、cleanup task は開始されません。管理者の `/kchat reload` は使用できます。

## 4. 設定マイグレーション

既存値は保持しますが、active migration では古い config text を継承しません。現在の bundled `config.yml` を新しい template として作成し、既存 operator 値だけを overlay します。旧 comment・順序・空白・indent は破棄され、最新 bundled comment/layout を使用します。

現在 version の完全な reference は常に次へ生成されます。

```text
<KWC data dir>/config-reference-5.2.0.yml
```

これは `ui.language` で選択した内蔵言語（`en-US`、`ko-KR`、`ja-JP`、`zh-CN`）と同じ言語で表示した現在のデフォルト設定の管理者向けコピーです。未対応/カスタム UI 言語では英語の設定表示を使用します。reference は migration 入力には使用しません。`/kchat reload` は live service を停止する前に YAML を検証し、不正な YAML なら現在の実行設定を維持します。

`config-version` がない、または実行 version と異なる場合、KWC は実 `config.yml` に対して一度だけ migration を行います。

- 最新 bundled `config.yml` を新しい file の template にします。
- 既存 operator 値をその template に overlay します。
- 旧 comment・順序・空白・indent は引き継ぎません。
- 旧 marker が `*_auto_migration` でない場合、実 version upgrade 前に元の `config.yml` を backup します。
- 既存設定の bundled default が新 version で変わった場合は自動上書きせず review 対象に残します。
- 実ファイルを `config-version: "5.2.0_auto_migration"` とします。

その後、次を生成します。

```text
<KWC data dir>/config-migration-5.2.0.yml
```

これは不足設定を copy/paste する fragment ではなく **review report** です。自動挿入数、operator 判断が必要な default 変更、最終確認用の正確な version marker、current-vs-reference の**設定値セマンティック差分**を記録します。Difference は解析済み YAML の path/value だけを比較し、comment、空行、indent、引用符形式、行位置、key 順は無視します。各 Difference block は説明 comment を重複コピーせず、その設定の実際の YAML 値 block だけを表示し、list/map は複数行構造を維持します。不足設定と comment はすでに実 config の適切な位置へ挿入されるため、diff の先頭に巨大な reference-only block として並びません。

判定基準:

| 実 `config.yml` | 動作 |
|---|---|
| `config-version` がない/以前/異なる | migration を実行し `5.2.0_auto_migration` にして migration/review report を生成 |
| `config-version: "5.2.0_auto_migration"` | 自動 migration 有効。startup/reload ごとに最新 bundled `config.yml` から再構築し、現在値を overlay して report/diff を更新 |
| `config-version: "5.2.0"` | 現在 version の自動 migration を停止。同一 version の migration/backfill を skip し、古い migration 案内を削除 |

この marker は **review 状態ではなく自動 migration の有効/無効**を表します。

```yaml
# 確認済みでも自動 migration を継続
config-version: "5.2.0_auto_migration"

# 同一 version の自動 migration を停止
config-version: "5.2.0"
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

### 6.1 直接 HTTP

テストまたはプライベートネットワーク用途に限定して使用することを推奨します。

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

### 6.2 同一ドメインの HTTPS リバースプロキシ


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
    path: "/"
    api-base-url: ""
```

公開例:

```text
https://map.example.com/
https://map.example.com/chat/api
https://map.example.com/chat
```

通常は `frontend.standalone.api-base-url`、`upload.public-base-url`、`emoji.public-base-url` を空にし、現在の公開 API base に追従させます。

### 6.3 信頼するプロキシの処理

`X-Forwarded-For` は、直接接続元が `http.trusted-proxies` に登録されている場合だけ受け入れます。リバースプロキシを使わない直接 HTTP では一覧を空にします。

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

5.0.0 以降、ログインユーザーの表示設定は複数の KWC アカウントプロファイルとして保存できます。ゲストだけはブラウザー内のローカルプリセットを使用します。ウィンドウ位置・サイズ・最小化状態、最後に選択したプロファイル ID、Web Push 登録は localStorage / デバイスローカルのままです。ログインユーザーの通知種別とキーワード通知はブラウザー単位ではなくアカウント共通で、ブラウザー内の通知受信箱から最近の対象イベントを確認できます。

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

### 8.1 ゲームから Web へ


```yaml
chat:
  broadcast-ingame-chat-to-web: true
```

通常の Minecraft チャットを公開 Web チャットへ配信します。送信者名は `player-display.mode` に従います。

### 8.2 Web からゲームへ


```yaml
chat:
  send-web-chat-to-game: true
  web-user-to-game-format: "[Web] {player}: {message}"
  web-guest-to-game-format: "[Web Guest] {guest}: {message}"
  web-admin-to-game-format: "[Web Admin] {player}: {message}"
```

設定した format template の legacy `&` color は変換されますが、ユーザー本文を任意に color 変換することはありません。

### 8.3 Web から Web へ


```yaml
chat:
  broadcast-web-chat-to-web: true
```

公開 Web メッセージは SSE を通じて他の接続ブラウザへ配信されます。

### 8.4 メッセージ長


```yaml
chat:
  max-message-length: 120
  max-url-message-length: 2048
```

`0` は無制限です。

### 8.5 メッセージトークン

KOKOTO WebChat 5.2.0 は、保存または relay の前に管理者設定の `:alias:` token を置換できます。標準 alias は英語のみで、管理者が任意の言語の alias に変更・追加できます。

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

`history-retention-days: 0` は経過日数による削除を無効にし、`history-size: 0` は件数による削除を無効にします。

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

### 10.1 Minecraft アカウントを連携する

Web UI で連携コードを発行し、ゲーム内で次を実行します。

```text
/kchat auth <code>
```

必要権限:

```text
kwc.auth
```

関連設定:

```yaml
auth:
  enabled: true
  link-code-length: 6
  link-code-expire-seconds: 180
  link-code-cooldown-seconds: 3
  link-code-max-per-minute: 10
```

### 10.2 パスワードログイン

ゲーム内で Web ログイン用パスワードを設定します。

```text
/kchat password <newPassword>
/kchat status
```

```yaml
auth:
  password-login: true
  remember-session-days: 30
```

パスワードはハッシュ化して保存されますが、HTTP のログイン通信自体は暗号化されません。公開運用では HTTPS を使用してください。

### 10.3 ロール

利用可能なロール:

- `USER`
- `MODERATOR`
- `ADMIN`
- 未ログインのゲスト

権限に基づく ADMIN 自動付与:

```yaml
auth:
  auto-admin-from-permission: true
  admin-permission: "kwc.admin"
```

### 10.4 ローカル管理者アカウント

Minecraft UUID と連携していない Web 専用管理者アカウントも作成できます。

```yaml
admin:
  allow-local-admin-accounts: true
```

コマンド:

```text
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
```

### 10.5 セッション管理

```text
/kchat sessions
/kchat revoke <username>
```

`revoke` は対象ユーザーの有効なセッションを無効化し、接続中ブラウザーへ認証失効を通知します。

## 11. セキュリティ

```yaml
security:
  login-fail-limit: 5
  login-fail-window-seconds: 300
  login-lock-seconds: 600
  max-sse-connections-per-ip: 10
  max-sse-connections-total: 500
```

`0` は該当制限を無効化します。

管理者 login IP 制限:

```yaml
admin:
  allow-admin-login-from: []
```

公開環境では HTTPS と強い password を使用してください。

## 12. ゲストチャットと CAPTCHA

```yaml
guest:
  enabled: true
  allow-custom-name: true
  name-prefix: "Guest-"
  cooldown-seconds: 6
  max-messages-per-minute: 50
  block-player-name-spoofing: true
```

`block-player-name-spoofing` は guest が既知の player name を名乗ることを防ぎます。管理者や server を装われたくない名前は `blocked-names` に追加できます。

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

## 13. プレイヤー名・ホバー・クリック操作

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

## 14. 公開メッセージへの返信

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

KWC が描画した Minecraft メッセージの URL 以外の部分をクリックすると、次のコマンドが入力欄に準備されます。

```text
/kchat reply <messageId> 
```

返信を送信するには:

```text
/kchat reply <messageId> <message>
```

権限:

```text
kwc.reply
```

`local-game-chat: true` はローカルのゲームチャット表示を同等のクリック可能コンポーネントへ置き換えます。最終表示を別のチャット整形プラグインに任せる必要がある場合は無効にしてください。無効でも Web→game と relay の返信処理は継続します。

URL 部分は通常のリンクを開く動作を維持し、返信コマンドを提案するのは URL 以外の本文部分だけです。

## 15. ダイレクトメッセージ

Minecraft の DM 履歴/ライブ通知は操作可能です。DM 相手名をクリックすると既存の `/kchat dm <player> ` が入力欄に入り、本文をクリックすると `/kchat reply dm-<internal-id> ` が準備されます。この internal ID はサーバー内部専用で、送信時に KWC が本人がその DM thread の参加者か再検証します。URL 部分は通常の URL を開く動作を維持します。保存された DM が Reply の場合、ライブ受信/送信 echo と履歴はいずれも public chat と同じ `reply.game-preview` / `reply.game-prefix` 設定を使用します。Web から送信した DM も、連携済み送信者が Minecraft にオンラインなら本人のゲームチャットへ表示されます。

Web で DM の Reply を選ぶと、元メッセージとの実際の返信関係を保存します。KWC は同じ thread のメッセージか検証し、正規化した送信者/preview snapshot を保存して参照を表示し、ローカルに原文が残っていればそこへ移動できます。reply metadata は再起動後も保持されます。サーバー間 DM の返信では他サーバーのローカル数値 DB ID を使わず、安定した relay message ID を使います。

DM は既定で無効です。

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

受信者は UUID で識別できる必要があります。ローカル参加履歴や連携アカウントだけでなく、relay 経由の game/linked-web message に `playerUuid` が含まれる場合、その送信者の表示名と実 Minecraft 名を新規 DM 検索へ登録します。これにより公開チャットで見た別サーバーの送信者も通常の DM 検索から選択できます。最新 identity は `known-display-names.yml` に保存され、再起動後も検索できます。UUID のない guest/Discord message は登録しません。`storage: auto` は公開チャット storage が JSONL の場合だけ DM も JSONL を使い、それ以外は SQLite を使います。`sqlite` / `jsonl` を明示指定することもできます。

ゲームコマンド:

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

### 15.1 Minecraft の whisper 取り込み

`capture-game-whispers: true` の場合、次のコマンドを同じ KWC DM thread に記録します。

```text
/w /msg /tell /whisper /m /pm /message /t
```

KWC は同一サーバーの通常 whisper を置き換えず、送信者/受信者双方の DM 履歴へコピーを記録します。remote target は `name@server-id` を使い、同じ alias が `/kchat dm name@server-id <message>` に書き換えられて認証済みサーバー間 DM relay から送信されます。修飾のない `/kchat dm <name>` は現在サーバーの player だけを解決します。`/r` と `/reply` は相手を含まないため intercept せず既存 whisper plugin に任せます。

### 15.2 送信・既読状態

通常の成功送信には状態ラベルを付けません。`Sending` はローカル送信要求が処理中の間だけ、`Failed · Retry` は配信確認に失敗した場合だけ timestamp 横に表示します。各 DM の既読状態も timestamp 横に表示され、`Unread` は唯一の受信者が未読、`✓` は既読です。group chat は人数ベースの表示を使います。cross-server DM では受信側が認証済み relay で read acknowledgement を返すため、送信元でも同じ状態になります。最新 acknowledgement は冪等で、conversation を開いたとき再送されるため一時的な relay/HTTP 障害も後から修復できます。

## 16. グループチャット

Minecraft の group message は操作可能です。group/sender 部分をクリックすると既存の `/kchat group <room> ` が入力欄に入り、本文をクリックするとその group message への reply target が準備されます。送信時に KWC は現在の group membership を再確認し、URL 部分は通常のリンク動作を維持します。Reply 付き group message はライブ受信/送信 echo と履歴のすべてで public chat と同じ `reply.game-preview` / `reply.game-prefix` 表示を使用します。

Web の group Reply も metadata として保存します。同じ room の target で送信者が現在 member の場合だけ受け付け、保存済み原文から sender/preview を生成します。再起動後も保持され、ローカル履歴に原文があればそこへ移動できます。

各ルームの設定には **メンバーの入退室通知を表示** オプションがあります。有効にすると、実際の membership 変更を `member_join` / `member_leave` event として保存し、group history とオンライン member へのゲーム通知に表示します。参加または招待承認で入室 event、退出・kick・ban で membership が削除されると退室 event を生成します。**グループチャット画面を閉じる、別ルームへ切り替える、ルームを非表示にする操作は退出ではなく、退室 event を生成しません。** membership event は案内専用で Reply target にはできません。

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

Web では公開/非公開ルームの作成、PBKDF2 ハッシュで保存する任意のルームパスワード、招待、承認/拒否、退出、非表示/復元、ルーム設定、未読管理、ユーザー単位のメッセージ非表示、メンバーの kick/block/unblock、所有権移譲を利用できます。

各 group message には受信者の既読状態を表示します。数字は「送信時点で member で、現在も room member であり、まだ読んでいない受信者」の人数で、送信者自身は数えません。未読人数が 0 になると `✓` を表示します。通常の配信成功そのものにはラベルを付けません。

ゲームコマンド:

```text
/kchat group
/kchat group list
/kchat group rooms
/kchat group <room|id> <message>
/kchat group send <room|id> <message>
/kchat group read <room|id> [pageSize]
/kchat group next
/kchat group prev
```

alias:

```text
/kchat gc ...
```

権限:

```text
kwc.group
```

## 17. システム/イベント通知

```yaml
announcements:
  broadcast-to-web-chat: true
```

既定で ON: join、quit、first join、death、advancement、server start/stop。

既定で無効: world、gamemode、level、bed、Web login/logout。

i18n key がある場合は Web UI language に合わせて表示できます。

## 18. ピン留めメッセージ

```yaml
pinned:
  enabled: true
  max-pins: 20
  show-to-logged-out: true
  preserve-uploads: true
```

通常履歴とは別に保存され、top bar から開きます。参照 upload は cleanup から保護できます。

## 19. ファイル/クリップボードアップロード

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

## 20. メディアとリンクプレビュー

アップロードメディアの preview:

```yaml
upload:
  preview-images: true
  preview-videos: true
  preview-audio: true
```

YouTube:

```yaml
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  youtube-nocookie: true
  youtube-max-embeds-per-message: 1
```

YouTube Shorts も通常の YouTube preview 経路を使い、縦長レイアウトで表示します。`ui.image-preview-max-per-message` は 1 メッセージ内の画像 preview 数を制限します。`ui.image-preview-max-height` は明示的 px 上限で、`0` にしても viewport ベースの安全上限は残ります。Google Drive image preview は `ui.google-drive-image-preview` で有効化し、`ui.google-drive-preview-mode` で動作を選択できます。

TikTok / X:

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

外部埋め込みはサードパーティーサービスへのブラウザーリクエストを発生させます。公開サーバーでは自動読み込みを明示的に許可する場合を除き、`click-to-load: true` を維持してください。

Discord CDN cache:

```yaml
preview:
  external-media-cache-enabled: true
  cache-discord-cdn: true
  external-media-cache-retention-days: 5
```

期限付き Discord attachment URL の preview を保存するために使用します。

## 21. カスタム絵文字

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

ファイル配置:

```text
<KWC data dir>/emojis/default/wave.png
<KWC data dir>/emojis/reaction/happy.gif
```

token:

```text
:default/wave:
:reaction/happy:
:emoji:default/wave:
```

`token-format: short` は `:pack/name:` を挿入し、`legacy` は `:emoji:pack/name:` を挿入します。解析時は両形式を受け付けます。

管理者は Web emoji manager から folder 作成、**1 回の picker で複数 PNG/JPG/JPEG/GIF/WEBP を選択して即時 upload**、item rename、delete を実行できます。multi-file upload は順番に処理し、既存の per-file validation、storage accounting、unique-name allocation、audit log、PNG sidecar generation を維持します。file/pack rename 後は古い token を含む過去メッセージが表示できなくなる場合があります。

既存および新規の pack directory 名/file 名/token は canonicalize されます。未対応文字と空白は保存前に除去し、同一 pack 内の衝突は数値 suffix で解決します。同じ emoji 名を別 pack で使うことはできます。

一時的に `/emojis` の取得に失敗しても、ブラウザーは直前に正常取得したカタログを保持し、上限付き指数バックオフで再試行します。SSE 再接続後はカタログを強制再同期し、管理者がカタログを変更した場合は `emoji-catalog` SSE イベントで他のブラウザーにも更新を通知します。`emoji.message-token-limit: 0` は再同期後も無制限として扱います。

### 21.1 ゲーム内絵文字処理

既定:

```yaml
emoji:
  game-link:
    enabled: false
```

この状態では token を保持し、ImageEmojis-Bero など game-side renderer に任せます。

KWC conversion mode:

```yaml
emoji:
  game-link:
    enabled: true
    mode: "link"
    label-format: ":{id}:"
    max-links-per-message: 4
```

mode:

- `preserve`: token をそのまま保持
- `label`: 設定した label だけを出力
- `link`: label と短い image URL を出力

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

詳細は `IMAGEEMOJIS_BERO_1_9_0.md`。

## 23. ブラウザー通知と Web Push

5.0.0 ではログインユーザーのキーワード/通知種別設定をアカウントデータに保存し、ブラウザーや端末間で共有します。Windows/モバイルなど表示条件が異なる場合はアカウントごとに複数の UI プロファイルを保存でき、ウィンドウ位置・サイズ・最小化状態・Web Push endpoint は端末ローカルのままです。プロファイル上限と JSON import/export の可否は Web Admin で管理します。同じアカウントでログインした KWC 画面のどれか 1 つでも実際に表示・フォーカスされ、チャットが最小化されておらず、DM/グループ画面が開き、現在の thread/room ID が新着対象と完全一致する場合だけ閲覧中と判定します。この短時間のブラウザー/タブ heartbeat は、閲覧端末自体に Web Push subscription がなくてもサーバーへ送られます。いずれかの active client がその会話を閲覧している間は、同じアカウントの接続中 KWC タブすべてでその DM/グループのブラウザー通知とブラウザーローカル通知 inbox 追加を抑止し、すべての Web Push subscription からも送信前に除外します。別ルーム、画面を閉じた状態、非表示/非フォーカス、最小化中なら閲覧していないものとして通常の通知配信に戻ります。


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
  notify-reactions: true
  notify-system: true
  notify-keywords: true
```

ユーザーは Chat settings で許可された通知カテゴリをさらに ON/OFF できます。**絵文字リアクション**は 1 つの checkbox が live browser notification と background/mobile Web Push の両方に共通適用され、各 browser/device では対応する配信方式だけが動作します。**@メンション**は `@` の直後に登録済みの実名または表示名が続く場合だけ成立します。各 `@` では一致する登録名の最長一致を優先し、その表示名を完全に共有する複数アカウントはすべて通知対象になるため、共通表示名をチーム呼び出しとしても使えます。ログインユーザーの選択は account 単位、guest は browser-local です。server 側の `false` はユーザー側で上書きできません。**Message preview は標準仕様**として対象 browser notification / Web Push に常時使用され、user/admin 用の個別 checkbox はありません。

```yaml
web-push:
  vapid-public-key: ""
  vapid-private-key: ""
  subject: "mailto:admin@example.com"
  notification-title: ""
  ttl-seconds: 300
```

VAPID key が空なら plugin が永続 key を生成します。iOS/iPadOS は Home Screen web app が必要な場合があります。

## 24. PWA と Picture-in-Picture

standalone app 名:

```yaml
frontend:
  standalone:
    app-name: "Web Chat"
    app-short-name: "Web Chat"
```

既に Home Screen にインストールした app は、名前を変更した後に再インストールが必要になる場合があります。

Picture-in-Picture:

```yaml
ui:
  picture-in-picture:
    enabled: false
```

browser/OS 側の対応が必要です。外部 window の control は browser/OS が管理します。

## 25. DiscordSRV 連携

5.0.0 で追加された管理者向け Discord キーワード通知は、照合や表示形式の規則を DiscordSRV に委譲しません。KWC がキーワード照合、送信元選択、メンション、重複除去、通知本文を管理し、DiscordSRV の認証済み JDA 接続と Channels マッピングだけを利用します。Web Admin の通知チャンネル選択欄には DiscordSRV の論理チャンネル名を表示し、論理名がない ID-only 構成に限って生の channel ID をフォールバックとして使用します。Discord 由来のメッセージは管理者通知の照合対象へ戻しません。

管理者 alert 設定例:

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

`channel: ""` は `discordsrv.channel` を再利用します。Web Admin では通常、DiscordSRV の論理チャンネル名を保存/選択します。

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
  send-web-user-chat-to-discord: true
  send-web-guest-chat-to-discord: false
  send-web-admin-chat-to-discord: true
  append-web-emoji-links: true
  append-game-emoji-links: true
  max-emoji-links-per-message: 4
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
  discord-to-web-sender-format: "Discord:{sender}"
  discord-to-web-message-format: "{message}"
```

DiscordSRV が通常の Minecraft chat を既に relay している場合は KWC の `game-relay-mode: "discordsrv"` を維持し、二重投稿を防ぎます。

複数 server が同じ Discord channel を共有する場合:

- 元の local game chat を観測した server だけが DiscordSRV message を変更します。
- relay 受信 server は Discord へ再投稿しません。
- 他 server の listener は `[Server]` や `[Web]` prefix を重複追加しません。

reply preview オプション:

```yaml
discordsrv:
  reply-relay:
    enabled: false
    prefix-enabled: true
    preview-enabled: true
    preview-max-length: 120
```

## 26. サーバー間 Relay

KOKOTO WebChat 5.2.0 は public chat と cross-server 1:1 DM/read receipt に Relay v2 trust/暗号化モデルを維持する **Relay Protocol 2.1** を使用します。group chat room は local のままです。

Relay v2 は `groups -> peers` 構造です。各 group は 1 つの shared secret を持ち、peer は server ID、API URL、enabled state のみを持ちます。初回設定では 1 台のサーバーで `shared-secret: ""` のまま起動/リロードし、その `config.yml` に生成された値を同じ group の他サーバーへコピーします。既存の空でない secret は自動再生成されず、32 文字未満の手動 secret は invalid のままです。両側は同じ group で相互に peer 登録する必要があり、同一 peer ID を複数 local group に登録できません。

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  groups:
    - id: "main"
      shared-secret: ""
      forwarding:
        enabled: false
      peers:
        - id: "server2"
          url: "https://server2.example.com/chat/api"
          enabled: true
```

direct relay は 5.0.0 と同じ request 単位の方式です。`/relay/v2/message` が HKDF-SHA256 directional key と AES-256-GCM で暗号化・認証された payload を独立して運びます。`/relay/v2/handshake` は状態を保持しない診断用 identity/health probe で、direct routing を制御しません。direct HTTP は warning 付きで利用できます。forwarding は同一 group 内で peer 単位に判定され、http:// peer はその peer を通る forwarding だけ除外され、他の https:// peer は引き続き利用できます。

Relay v2 は E2EE ではなく hop-by-hop authenticated encryption です。中継 server は次 hop 用に payload を復号・再暗号化する trusted participant です。group secret が漏洩した場合は group 全体で rotate してください。

5.0.0 → 5.1.0 初回 migration は旧 flat topology から group を推測せず relay を無効化します。v2 group を明示的に定義してから `server-relay.enabled: true` に戻し `/kchat reload` を実行してください。

詳細は `docs/ja/SERVER_RELAY.md` を参照してください。

## 27. Web コンソールコマンドパネル

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: "ADMIN"
  show-button: true
  run-from-chat-input: false
  show-when-input-starts-with-slash: true
  require-confirm: true
  max-length: 0
  broadcast-result-to-web-chat: false
```

`broadcast-result-to-web-chat: true` の場合、Web コマンド実行通知を公開 Web チャットとオンラインのゲームプレイヤーへ同時に表示します。既存のコンソール/監査ログはそのまま維持し、ゲーム表示のための追加ログは出しません。

`allow-all: true` は Web アカウントから任意のコンソールコマンド実行を許可するため非常に危険です。HTTPS、IP 制限、強力なパスワード、preset 制限を使用してください。

```yaml
commands:
  presets:
    - id: "day"
      label: "昼に変更"
      description: "現在のワールド時刻を昼に設定します。"
      command: "time set day"
      confirm: true
```

## 28. 管理者・モデレーター機能

```yaml
moderation:
  enabled: true
  allow-web-admin-panel: true
  allow-moderator-message-delete: true
  allow-moderator-guest-mute: true
  default-mute-minutes: 60
```

上記 5 個の `moderation.*` ポリシーキーは **config.yml 専用設定**です。Web Admin の設定画面には編集項目として意図的に公開されません。変更する場合は `config.yml` を編集して KWC を reload してください。これらは Web moderation 機能の可用性と moderator の操作範囲を制御します。

機能:

- message hide/delete
- pin 管理
- guest/IP mute
- session revoke
- emoji 管理
- upload/storage usage
- private chat metadata
- コンソールコマンドパネル

### 28.1 非公開チャットメタデータのスーパー管理者

```yaml
private-chat-super-admins:
  - "ExactMinecraftName"
  - "00000000-0000-0000-0000-000000000000"
```

メタデータビューでは DM/group の title・participant、message count、概算 storage usage、retention state、cleanup preview、lock/exclusion などのメタデータ管理機能を確認できます。

`direct-message.admin-audit.enabled` は default-off の read-only DM 本文監査スイッチです。`private-chat-super-admins` に明示した account だけが利用でき、監査 view では送信、Reply、非表示、既読更新はできません。各 page read は `admin.dm-audit-read` として記録されます。`group-chat.admin-audit.enabled` は独立した read-only group 本文監査スイッチです。

### 28.2 監査ログ

```yaml
audit:
  enabled: true
  directory: "audit"
```

管理操作は既定で `<KWC data dir>/audit` 配下の日付別ファイルへ追記されます。監査ログ自体は Web UI には表示されません。

## 29. Web フォントと表示設定

```yaml
web-fonts:
  enabled: false
  directory: "fonts"
  items: []
```

例:

```yaml
web-fonts:
  enabled: true
  items:
    - family: "Pretendard"
      file: "Pretendard.woff2"
      weight: 400
      style: "normal"
```

対応 extension: WOFF2、WOFF、TTF、OTF。

表示の既定値:

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

空の color 値は選択中 theme の値に従います。

## 30. 仮想スクロールとパフォーマンス

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

長い履歴を表示するときのブラウザー描画負荷を削減します。

```yaml
ui:
  resume-refresh:
    enabled: true
    min-interval-seconds: 5
    skip-unchanged: true
```

公開チャットの仮想スクロールはコンテンツ種類を区別せず、画像・動画・音声・リンクプレビュー・YouTube/その他 iframe を同じメッセージ範囲/高さ追跡ルールで扱います。

## 31. コマンド一覧

ユーザーコマンド:

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

管理者コマンド:

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

root alias:

```text
/kc
```

group alias:

```text
/kchat gc
```

## 32. 権限リファレンス

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

## 33. データファイルとバックアップ

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

バックアップ対象は設定、DB/JSONL、絵文字、必要なアップロード、Push subscription/VAPID key です。SQLite は正常停止後にコピーすることを推奨します。

## 34. リロードと再起動

`/kchat reload`:

- 多くの config 変更
- HTTP service と relay の再生成
- UI default 更新

Restart 必須:

- JAR 交換
- Java code 変更
- load order 変更

`/kchat reload` は `bluemap reload light` を自動要求します。asset がまだ古い場合または自動実行に失敗した場合は `/bluemap reload light` を手動実行してください。

## 35. トラブルシューティング早見表

### ページが開かない

- `enabled: true` を確認
- `http.host` / `http.port` を確認
- port conflict を確認
- reverse proxy upstream が `127.0.0.1:8899` を向いているか確認
- standalone が必要なら `frontend.standalone.enabled` を有効化

### BlueMap にチャットボタンが出ない

- `adapters.bluemap.auto-install` を有効化
- `adapters.bluemap.auto-patch-webapp-conf` を有効化
- BlueMap path を確認
- install/patch log を確認
- `/kchat reload` は通常 `bluemap reload light` を要求するため、必要時のみ `/bluemap reload light` を手動実行
- browser cache を refresh

### ログインできない

- HTTPS domain と cookie path を確認
- link code の期限を確認
- login lockout log を確認
- `auth.password-login` を確認
- administrator IP restriction を確認

### Web chat が game に届かない

- `chat.send-web-chat-to-game: true` を設定
- player が online か確認
- chat-format plugin conflict を確認
- relay message は `server-relay.delivery.game: true` を確認

### Game chat が Web に届かない

- `chat.broadcast-ingame-chat-to-web: true` を設定
- player permission と event cancellation を確認
- 別 chat plugin が event を排他的に消費していないか確認

### Reply click が動かない

- `reply.game-click.enabled` を有効化
- local game message は `local-game-chat` を有効化
- chat-format plugin conflict を確認
- URL 部分が reply ではなくリンクを開くのは正常動作

### Emoji token が文字のまま表示される

- KWC emoji file の存在を確認
- ImageEmojis-Bero の共有 folder/permission を確認
- `replaceInCommands` を有効化
- `/emojis reload` と `/emojis update` を実行
- relay server 間で一致する emoji file を確認

### Relay が 403 を返す

- 両 server が同じ relay group で相互に peer 登録しているか確認
- peer ID が remote `server-id` と完全一致するか確認
- direct relay は request ごとに独立認証します。相互に同じ group の peer と同じ shared secret を設定しているか確認してください。forwarding では受信元として設定された peer と選択される次 hop peer の両方が HTTPS である必要があります
- relay config 変更後は両側を reload

### Relay が 401 を返す

- group ID と group shared secret を両 server で比較
- 双方が完全に同じ group secret を使用しているか確認します。初回設定は 1 台の空値から生成して他サーバーへコピーし、手動 non-empty secret は 32 文字以上必要です
- proxy が signed relay header/body を変更していないか確認
- server clock sync と replay/nonce diagnostic を確認

### Discord prefix が重複する

- 全 server が同じ修正版を使うことを確認
- 別 plugin が relay message を repost していないか確認
- DiscordSRV が game chat を転送する場合は `game-relay-mode: "discordsrv"` を維持

### Web Push が動かない

- HTTPS を確認
- notification permission を確認
- Service Worker / Push API 対応を確認
- iOS は Home Screen にインストールした Web app を使用
- VAPID subject と key file を確認

## 36. 関連文書

- `CONFIGURATION.md`
- `UPGRADE.md`: 4.7.0→5.0.0 core split / reload safety
- `UPGRADE.md`: 4.6.3→4.7.0 feature upgrade
- `SERVER_RELAY.md`
- `UPGRADE.md`: 4.6.2→4.6.3 upgrade
- `UPGRADE.md`: 4.6.1→4.6.2 upgrade
- `UPGRADE.md`: 4.6.0→4.6.1 upgrade
- `UPGRADE.md`
- `CADDY_HTTPS.md`
- `NGINX_HTTPS.md`
- `IMAGEEMOJIS_BERO_1_9_0.md`
- `INSTALL_TROUBLESHOOTING.md`
- `UPLOAD_SECURITY.md`
- `OPERATIONS_SECURITY.md`
- `I18N.md`
- `RELEASE_CHECKLIST.md`


### 管理者 group-chat body audit (4.6.3)
`group-chat.admin-audit.enabled: true` を設定し、exact Minecraft name または UUID を `private-chat-super-admins` に登録する必要があります。両方の条件が必須です。対象管理者は room member でなくても管理者 room metadata list から body を read-only で開けます。audit view は room 参加、read/unread state 更新、message send/upload/hide、membership 変更を行いません。各 page read は `admin.group-audit-read` として記録され、message body は audit log にコピーされません。



## SimpleNicks-Bero 連携

Bukkit/Paper 系では `player-display.mode: "display-name"` で [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) の Bukkit display name を表示できます。詳細は `SIMPLENICKS_BERO.md`。一般運用は [upstream SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks) を参照してください。
