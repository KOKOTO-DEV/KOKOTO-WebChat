# KOKOTO WebChat 設定リファレンス

## 5.0.0 コンテンツフィルター、Web Admin 設定、セッション、アップロード名

`content-filter` はゲーム/Web 公開チャット、グループチャット、任意の DM に共通適用するローダー中立フィルターです。大量のフィルター単語は UTF-8 の `plugins/KOKOTO-WebChat/filter-lists/*.txt` に1行1語で保存します。空行と `#` コメントは無視されます。各リストは既定で `block` ですが、Web Admin でリストごとにブロックまたはフィルタリング（`content-filter.mask.text` によるマスク）を選択できます。細かな block/mask/replace は `config.yml` の `content-filter.rules` カスタムルールで管理します。`replace` の `first` は最初の置換語を常に使用し、`random` は候補からランダムに選びます。語別マッピングがある場合はそれが優先されます。Unicode 正規化と compact/interleave 一致で空白・記号・互換文字・`욕1설` のような制限付き挿入回避を検出します。登録済みの KWC 絵文字トークンはブロック/フィルタリングにかからないよう保護され、未登録の `:fake:` 形式は通常テキストとして検査されます。


### カスタムフィルターの簡単な使い方

単純な語が多い場合は UTF-8 の **Filter word list** TXT を使い、1 行に 1 語を書いて一覧の動作を **Block** または **Filter（mask）** にします。`replace`、複数の置換候補、単語別置換が必要な場合は **Custom rules** を使います。

- **Block**: 対象語が一致するとメッセージ全体を拒否します。
- **Mask**: 一致部分だけを `content-filter.mask.text`（既定 `***`）へ置換します。
- **Replace + First**: 共通置換候補の先頭を常に使います。
- **Replace + Random**: 共通候補から一致ごとにランダムで 1 つ選びます。
- **Per-word replacements**: `word1 => 置換 A` のように単語ごとの結果を指定します。左側のキーは Target words に書かなくても自動的に対象語となり、共通候補より優先されます。

```text
Rule ID: soften-words
Action: Replace
Target words:
word1
word2

Replacement candidates:
やわらかい表現
別の表現

Replacement mode: First

Per-word replacements:
word2 => 個別の表現
```

`compact-match` は区切り文字を挟んだ入力、`interleave-match` は `interleave-max-gap` の範囲で文字・数字を挿入した回避を検出します。`interleave-unlimited-gap: true` は間隔制限をなくすため、誤検出範囲も広がります。`ㅅㅂ` のようなハングル字母だけのルールは字母略語として扱われ、完成形ハングル単語は完成音節単位で比較されます。

保存後は Filter 画面の **Test** を使ってください。実際のメッセージを送らず TXT リストとカスタムルールを同時に評価でき、ライブフィルターが無効でも実行できます。Rule / Word / Match で `literal`、`compact`、`interleave` のどれで一致したか確認できます。


Web Admin には **Filter** と **Settings** タブがあります。Filter では適用範囲、回避検出、リストごとのブロック/フィルタリングを選べるフィルター単語リスト、カスタムルールの追加/編集/削除、送信しないテストを管理します。Settings はライブ変更しても安全な guest/CAPTCHA、認証・セッション、ユーザープロファイル、upload、Discord 管理者通知の値だけを公開します。`moderation.*` と relay/network/adapter 構成は `config.yml` 専用です。ゲーム側では `/kchat filter ...` と `/kchat settings ...` を使用します。

`auth.remember-session-days` は既存 USER/MODERATOR セッションを元の `createdAt` 基準で再計算し、`admin.admin-session-expire-hours` は ADMIN セッションだけを独立して再計算します。`0` は USER/MODERATOR と ADMIN のどちらのセッション期間設定でも無期限を意味します。既に期限切れのセッションは復活せず、新しい短い期間を超えたセッションは即時失効します。`config.yml` 編集後の起動/reload でも同じポリシーを適用します。

`upload.filename-mode` の既定は `random` です。`original` は新規アップロードの安全な Unicode 元ファイル名を保持し、危険なパス/制御/ファイルシステム禁止文字だけを整理します。同名は `-2`, `-3` ... を付け、既存ファイルを上書きしません。既存アップロードは改名しません。


`plugins/KOKOTO-WebChat/config.yml` の説明です。

## 設定 version と migration fragment

`config-version` は自動 migration の動作を選ぶ marker です。migration の**唯一の template は実行中 plugin に bundled された `config.yml`**です。`config-reference-<plugin-version>.yml` は管理者が全 default 設定を確認するための bundled config の完全コピーであり、migration source には使用しません。

`config-version` がない、または別 version の場合、KWC は既存の設定値を読み取り、最新 bundled `config.yml` から新しい file を作成して既存値を overlay します。以前の marker が `*_auto_migration` でなければ、operator が一度固定した設定とみなし、再構築前に元の `config.yml` 全体を backup します。古い comment・順序・空白・indent は引き継がず、最新 bundled comment/layout を使用し、operator の値だけを保持します。削除済み設定は再追加しません。結果は `<plugin-version>_auto_migration` になります。この marker が残る間は startup/reload ごとに同じ bundled-default rebuild を行い、新しい設定と最新 comment/layout を自動反映します。正確な `<plugin-version>` は現在 version の config を固定した状態で、同一 version の startup/reload は `config.yml` を書き換えません。

`config-migration-<plugin-version>.yml` は review/diff report です。旧 version の生成済み `config-reference-*`、`config-migration-*`、`config-upgrade-*` は自動削除され、実 version upgrade の default 差分判定に必要な JAR 内部 `config-baselines/*` のみ保持されます。
## 全体有効化スイッチ

新規生成された config は最上位の `enabled: false` から始まります。この状態では KOKOTO WebChat は config の生成/読み込みのみを行い、/kchat reload は引き続き使用できますが、Web/チャットサービス、リスナー、Discord 連携、DM ストア、アドオン設置、アップロード/絵文字初期化、クリーンアップ処理を開始しません。既存 config にこのキーがない場合は、アップグレード互換性のため有効として扱います。保存方式、保持期間、アップロード、プレビュー、認証、公開設定を確認してから `enabled: true` に変更してください。

## アップデート確認

```yaml
update-check:
  enabled: true
```

有効にすると、KOKOTO WebChat は Bukkit、Fabric、NeoForge、Forge のすべてでバックグラウンドから Modrinth の最新 stable release を確認します。KWC の `kokoto-webchat` project を優先し、移行期間中は旧 `bluemapwebchat` project へ fallback します。OP または `kwc.update.notify` 権限を持つ player がログインすると、レート制限付きで再確認するため、新しい release の検出が定期確認結果だけに依存しません。5.0.0 の CurseForge 通知リンクは、新 KWC listing が実際に公開されるまでは既存 BMWC bridge page を使用します。確認間隔、release channel、join 通知 delay は内蔵 default のままです。確認失敗で server 起動は停止せず、warning log に記録されます。

## 配置モード

### BlueMap アドオン

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true
```

Bukkit では `addons/kokoto-web-chat` にファイルを配置し、BlueMap の `webapp.conf` を更新します。Fabric/NeoForge および Forge 26.1.2/26.2 で BlueMap mod を使う場合は BlueMapAPI 2.8.0 から web root を取得して JS/CSS を登録し、`auto-patch-webapp-conf` と 2 つの BlueMap path override は使用しません。

### standalone のみ

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

`http://<server-host>:8899/` を開きます。

### HTTPS リバースプロキシ

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: "/chat"
  cors-origin: "https://map.example.com"

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""

upload:
  # 推奨は空です。アップロード URL は有効な API base に自動追従します。
  # 従来の明示値も使えます: "/chat/api" または "/chat/api/uploads"
  public-base-url: ""

emoji:
  # 推奨は空です。絵文字 URL は有効な API base に自動追従します。
  # 従来の明示値も使えます: "/chat/api" または "/chat/api/emojis"
  public-base-url: ""
```

### 公開 URL オプションの規則

- `http.path-prefix` はプラグイン内部の HTTP API 経路です。通常は既定の `/api` のままにします。
- `adapters.bluemap.api-base-url` は BlueMap 埋め込みチャットが使う公開 API base です。HTTPS リバースプロキシでは通常 `/chat/api` にします。
- `frontend.standalone.api-base-url` は通常空のままにします。直接 HTTP では `http.path-prefix`、リバースプロキシ経由では `http.public-prefix + http.path-prefix` を使用し、既定の公開 API は `/chat/api` です。BlueMap adapter の override は継承しません。
- `upload.public-base-url` は通常空のままにします。空の場合は有効な API base に `/uploads` を追加します。例: `/chat/api/uploads`。
- `emoji.public-base-url` は通常空のままにします。空の場合は有効な API base に `/emojis` を追加します。例: `/chat/api/emojis`。
- 明示値も使えます。`/chat/api` を指定すると upload は `/uploads`、emoji は `/emojis` を自動で追加します。`/chat/api/uploads`、`/chat/api/emojis` はそのまま使います。
- 先頭 `/` のない相対値、例: `chat/api`、`chat/api/uploads`、`chat/api/emojis` は、`http.cors-origin` が実際の origin のときその origin を前に付けます。`cors-origin: "*"` の場合は `/chat/api...` のような同一 origin の絶対パスとして扱います。
- `https://map.example.com/chat/api` のような完全 URL はそのまま使います。

## チャット履歴保存

チャット履歴は `chat.history-storage` で `memory`、`jsonl`、`sqlite` のいずれかを選びます。`chat.history-size` と `chat.history-retention-days` は 3 つのモードで共通です。`0` は件数/期間の制限なしを意味します。新規生成された config は最上位の `enabled: false` から始まるため、これらの値を確認して `enabled: true` にするまでクリーンアップ処理は実行されません。サーバー方針として古いチャットの自動削除が必要な場合は、`30` や `90` などの正の保持日数を設定してください。アップロードと外部メディアキャッシュの保持設定も同じ考え方です。`chat.history-file` は JSONL のみ、`chat.history-sqlite-file` は SQLite のみで使われます。

## メッセージトークン

`message-tokens.enabled` は colon 区切りの管理者定義 text/control alias を有効にします。config には colon を付けず alias 名だけを記述し、たとえば `enter` は chat で `:enter:` と入力します。標準 alias は英語のみで、管理者が任意の言語へ変更・追加できます。未登録 alias はそのまま残るため custom/ImageEmojis token と共存できます。

YAML の list 設定は inline 形式 (`aliases: [bullet, arrow]`) と block 形式 (`aliases:` の次行に `- bullet`) の両方を使用できます。indent には通常の ASCII space のみを使用し、tab や全角 space は使用しないでください。`/kchat reload` は live service を停止する前に設定を検証するため、不正な YAML は適用されず、現在実行中の設定と UI 言語が維持されます。

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

- `max-replacements-per-message: 0` は成功した message-token 置換数を制限しません。
- `newline` は改行 1 行です。
- `blank-line` は改行 2 つで空行 1 行を作ります。
- `tab.spaces` は 1～16 に制限され、literal tab control character ではなく space を挿入します。
- `custom` は printable text の置換のみで、control character/newline は除去されます。
- `:\n:` のような backslash escape は意図的に解釈しません。
- Minecraft の通常 CR/LF は従来の 1 行 flatten を維持します。`newline`/`blank-line` alias で作成した改行だけを別管理し、最終 game delivery で明示的な複数 chat line として送ります。server relay でも受信側に同じ 4.7.0 token-line support が必要です。

`custom: {}` を次のような block に置き換えて printable substitution を追加できます。

```yaml
message-tokens:
  custom:
    separator:
      aliases: [separator, divider]
      replacement: "────────────"
```

## 1:1 ダイレクトメッセージスレッド

```yaml
direct-message:
  enabled: false
  allow-web-send: true
  allow-game-send: true
  capture-game-whispers: true

direct-message:
  admin-audit:
    enabled: false
```

`group-chat.admin-audit.enabled` は 4.6.3 で追加された独立した default-off の group content access switch です。有効化しても account が `private-chat-super-admins` に指定されている必要があります。管理者 view は read-only で room membership を必要とせず、room 参加や read state 更新も行いません。各 page read は body を audit log にコピーせず `admin.group-audit-read` として記録されます。

`direct-message.admin-audit.enabled` は default off の別 content-access switch です。有効でも `private-chat-super-admins` に指定された account だけが read-only audit view で DM body を開けます。page read は audit log に記録されますが body 自体は log にコピーされません。通常 ADMIN/MODERATOR role は自動対象ではありません。

`capture-game-whispers` は、キャンセルされていない `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` を送信者と受信者の KWC DM スレッドへ複製します。Minecraft whisper 自体を再送・置換しません。Bukkit は任意の whisper plugin の最終成功結果を共通 API で提供しないため、既知 player 宛ての正しい形式の command を記録基準にします。

## 0 が無制限/最大値なしを意味する項目

- `chat.history-size`
- `chat.history-retention-days`
- `chat.history-page-size`
- `chat.max-message-length`
- `chat.max-url-message-length`
- `upload.max-uploads-per-minute`
- `upload.max-file-size-mb`
- `upload.max-files-per-message`
- `ui.image-preview-max-per-message`
- `ui.image-preview-max-height`
- `ui.max-width`
- `ui.max-height`
- `preview.youtube-max-embeds-per-message`
- `preview.social-embeds.max-embeds-per-message`
- `preview.external-media-cache-max-size-mb`
- `pinned.max-pins`
- `pinned.show-to-logged-out`
- `commands.max-length`
- `direct-message.retention-days`
- `direct-message.max-messages-per-thread`
- `direct-message.max-message-length`

## ゲストチャット制限

```yaml
guest:
  cooldown-seconds: 6
  max-messages-per-minute: 50
```

ゲストチャットは `cooldown-seconds` と `max-messages-per-minute` の両方で制限されます。1分あたりの既定値は `50` メッセージです。既存サーバーの設定ファイルは自動で上書きされないため、既存環境で新しい既定値を使う場合は `plugins/KOKOTO-WebChat/config.yml` を手動で更新してください。

## Web→Minecraft 名前 hover

```yaml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

Web チャットを Minecraft チャットへ中継するとき、`chat.game-name-hover.enabled` で表示名に hover ツールチップを追加できます。これは `player-display.mode` が `display-name` または `custom-name` で、表示名が実際の Minecraft アカウント名と異なる場合にのみ適用されます。このツールチップは Spigot/Bungee チャットコンポーネントを使用するため、Paper 専用ではなく Spigot/Paper 互換サーバーで動作します。`text` は Minecraft legacy 色コードと `{display}`, `{real}`, `{uuid}`, `{source}` プレースホルダーに対応します。

## Minecraft チャットの返信と送信者クリック

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

URL 以外の本文クリックは `/kchat reply <messageId> ` を入力候補にし、URL 部分はリンクを開く動作を優先します。`local-game-chat` は通常のローカルゲームチャットもクリック可能にします。他の chat-format plugin が最終描画を独占する場合は無効にしてください。同じサーバーのゲーム送信者名は `/w <実名> `、連携済み Web 送信者と別サーバーのゲーム送信者名は `/kchat dm <実名> ` を候補にします。

ゲーム返信では、入力されたカスタム絵文字 token を Web 履歴とサーバーリレー用に保持し、送信元サーバーの Minecraft 表示にはゲーム側絵文字 plugin が処理した command body を再利用します。処理済み glyph がなく `emoji.game-link.mode` が `preserve` の場合、認識済み token は互換性のため通常の Bukkit chat 行として出力されます。この fallback 行には KWC の click/hover metadata は付きません。



`server-relay` は複数の KOKOTO WebChat サーバーの公開チャットを接続します。ゲーム、連携済み Web ユーザー、ゲストのメッセージを相手側の Web チャットと Minecraft チャットへ送り、メッセージ ID、返信関係、送信者、発信元サーバー情報を保持します。

## サーバーリレー設定

`peers` は接続セッション一覧ではなく、このサーバーがリレーメッセージを送る HTTP 宛先一覧です。同じ `id` / `secret` は相手サーバーから受信した要求の認証にも使用されます。双方向で使用する場合は両側に相手の項目を登録してください。

サーバー 1:

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "サーバー 1"
  shared-secret: "両方のサーバーで同じ長いランダム秘密鍵"
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
    - id: "server3"
      url: "https://server3.example.com/chat/api"
      secret: ""
      enabled: true
```

サーバー 3 側では `server-id: "server3"` とし、`peers` に `id: "server1"` とサーバー 1 の公開 API URL を登録します。受信側の peer ID は送信側の `server-id` と正確に一致し、各サーバー ID は一意でなければなりません。

## HTTPS / リバースプロキシ

`url` は相手サーバーで外部から到達できる KWC API base です。`/relay/receive` は自動追加されます。

```text
設定: https://server3.example.com/chat/api
要求: https://server3.example.com/chat/api/relay/receive
```

公開 HTTPS ルートは `/relay/receive` の POST を含む API パス全体を内部 KWC HTTP リスナーへ転送してください。HTTPS 経由なら 8899 を外部公開する必要はありません。プロキシは `X-BMWC-Relay-Version`, `X-BMWC-Relay-From`, `X-BMWC-Relay-Timestamp`, `X-BMWC-Relay-Signature` を保持する必要があります。自己署名証明書は Java trust store へ登録しないと TLS 検証で失敗します。

## 秘密鍵

- `shared-secret` は全 peer の既定キーです。
- `peers[].secret` はその接続だけのキーで、共通キーより優先されます。
- 2 サーバーなら同じ長い `shared-secret` を両方に設定し、peer の `secret` は空にできます。
- peer キーも共通キーもない peer は無効として除外されます。

## 複数サーバーとループ防止

フルメッシュでは全サーバーが互いを登録します。ハブ構成では leaf が hub のみを登録して hub が全 leaf を登録し、`forward-received-public-chat: true` の場合は受信した公開チャットを他の peer へ再転送します。`false` の場合、公開チャットは直接 peer 間だけで配信されます。この設定は DM のマルチホップ配送・既読通知には影響しません。relay ID の重複排除、発信元抑止、直前 peer 除外、`max-hops` により循環構成でも無限ループを防ぎます。停止中の peer へ後から再送する永続オフラインキューはありません。

## reload と診断

`/kchat reload` は以前の relay を閉じ、現在の設定で作り直します。常時接続ではなくメッセージごとの HTTP(S) 要求なので、別の再接続操作はありません。

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

`activePeers` が設定数より少ない場合、重複 ID、自己 ID、空/不正 URL、未対応 scheme、秘密鍵不足などの理由が警告に表示されます。

## HTTP エラー

- `403 unknown_peer`: 受信側の有効 peer に送信側 `server-id` がありません。
- `401 bad_signature`: 実効秘密鍵が異なるか、プロキシが本文/ヘッダーを変更しました。
- `401 expired_request`: サーバー時刻差が `max-clock-skew-seconds` を超えています。
- `404 relay_disabled`: 受信側で無効、またはプロキシ先のパス/インスタンスが違います。
- `426 unsupported_protocol`: relay protocol の互換性がありません。

受信側の peer 一覧や秘密鍵を変えた場合は受信側でも `/kchat reload` を実行してください。

## 表示

Web では別サーバーのメッセージだけ `originServerId` 由来の固定色サーバーバッジを表示し、現在のサーバー自身のバッジは省略します。Web→ゲームでは別サーバー由来の古い形式に `{server}` / `{server_id}` がなければ `[server-name]` を自動付与します。Discord は共有外部チャンネルのためサーバー表示を維持します。DiscordSRV ループとイベント重複を避けるため `sources.discord` と `sources.system` は既定で無効です。

## Discord 連携オプション

```yaml
discordsrv:
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
  game-relay-mode: "discordsrv"
  append-web-emoji-links: true
  append-game-emoji-links: true
  reply-relay:
    enabled: false
```

format は `{server}`, `{server_id}`, `{sender}`, `{name}`, `{role}`, `{source}`, `{message}`, `{channel}` に対応します。relay 有効時、古い format に server placeholder がなければ `[server-name]` を自動付与します。同じ Discord チャンネルを複数サーバーで共有する場合、実際のローカル Minecraft チャットを検知した発信元サーバーの KWC だけが DiscordSRV の通常ゲーム転送にサーバー名と絵文字リンクを追加し、他サーバーは編集しません。受信側 peer は relay メッセージを Discord へ再送しないため、発信元サーバーの Discord 連携が無効または失敗した場合に別サーバーが代送する relay-only fallback はありません。DiscordSRV が通常ゲームチャットを送る場合は `game-relay-mode: "discordsrv"` を使用します。KWC から直接送信する場合は `kwc` を使用し、DiscordSRV 側の通常ゲームチャット中継を無効にして重複を防ぎます。

## 管理者向け Discord キーワード通知

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

個人ユーザー向け Discord 通知ではなく、server 管理者共通 policy です。keyword 検出、source filter、mention、format、重複除去は KWC が担当し、DiscordSRV からは認証済み JDA connection と Channels mapping のみ再利用します。Web Admin の alert channel selector は DiscordSRV の logical channel 名だけを表示し、logical 名がある場合は内部 numeric ID を表示しません。`channel: ""` は `discordsrv.channel` を再利用し、numeric ID は ID-only fallback の場合だけ許可します。Discord 由来 message は再 alert せず、DM/group scope は既定で無効です。

## 固定メッセージ

`pinned.show-to-logged-out` は、Webログイン前にも固定メッセージを表示するかを制御します。ログイン済みユーザーにのみ表示したい場合は `false` にします。

## 固定/削除表示トグル

メッセージごとの固定/削除ボタンは誤操作を避けるため既定では非表示です。ADMIN/MOD ユーザーは管理パネルの Web 履歴クリアボタン横にある固定/削除トグルをオンにすると表示できます。このトグルは保存されず、再読み込みするとオフに戻ります。

## UI

```yaml
ui:
  language: "en-US"        # en-US, ko-KR, ja-JP, zh-CN
  language-fallback: "en-US"
  theme: "system"          # system, dark, light, high-contrast
  opacity: 0.92
```

ユーザー別の表示設定はブラウザの localStorage に保存されます。

## プレイヤー名

```yaml
player-display:
  mode: "name"             # name, display-name, custom-name
  strip-colors: true
```

`strip-colors: false` の場合、Web UI では実際のチャット送信者名にのみ Minecraft legacy 色コードを描画します。システム/イベントメッセージと Discord 出力では、生の Minecraft 色コードを除去します。保存済みの表示名も再利用時に現在の `strip-colors` 設定で正規化されます。

## カスタム絵文字とゲーム側絵文字プラグイン

KOKOTO WebChat はカスタム絵文字を `plugins/KOKOTO-WebChat/emojis` 以下に保存します。サブフォルダーは絵文字パックとして扱われます。

既定では `emoji.game-link.enabled` が `false` のため、Web→ゲームメッセージの `:pack/name:` や `:emoji:pack/name:` のようなカスタム絵文字トークンは変更されません。ImageEmojis などのゲーム側絵文字プラグインが Minecraft チャット内でトークンを描画する場合は、この既定値を使用してください。

`emoji.game-link.enabled` が `true` の場合、`emoji.game-link.mode` は `preserve`、`link`、`label` をサポートします。

- `preserve`: game-link が有効でもトークン保持動作を強制します。
- `link`: `label-format` テキストと短い BM Web Chat 画像リンクを送信します。
- `label`: `label-format` テキストのみを送信します。

`emoji.game-link.*` は Web→Minecraft チャットのみに影響します。`discordsrv` モードの Game→Discord では、DiscordSRV が実際に Discord へ投稿したゲームメッセージ内の `:emoji:` token を Web→Discord と同じ KWC token→link 処理へ渡します。LOWEST 段階のゲームチャット記録は共有 Discord チャンネルで発信元サーバーを判定するためだけに使い、Minecraft 用 glyph やゲーム表示文字列を Discord 絵文字変換の入力には使いません。Discord の画像プレビューリンクは、Web→Discord 用の `discordsrv.append-web-emoji-links` と Game→Discord 用の `discordsrv.append-game-emoji-links` で分けて制御します。

BM Web Chat は Web 履歴とリレー payload に正規の絵文字 token を保持します。ImageEmojis または ImageEmojis-Bero が有効な場合、公開されている runtime 絵文字 repository を reflection で読み取り、クリック可能な Minecraft component を作成する前に受信サーバーの有効な glyph へ token を変換します。hard dependency の追加や resource pack の解析は行いません。

interactive chat では ImageEmojis glyph を先に挿入してから sender・reply・URL の click event を構築するため、絵文字表示とクリック可能な URL が同時に動作します。受信サーバーで解決できない既知 token のみ、別のゲーム側 renderer 向けに単一の plain Bukkit fallback を使用します。この fallback には KWC の click/hover metadata を付けられません。

`default-pack` と `aliases` は、flat なゲーム側トークンを BM Web Chat の pack/name id に対応付けるために使います。例:

```yaml
emoji:
  game-link:
    default-pack: "default"
    aliases:
      wave: "default/wave"
```

GIF/JPG/JPEG/WEBP 絵文字の元ファイルには、PNG のみを読むゲーム側絵文字プラグインとの互換性のため、同じフォルダーに PNG sidecar が自動生成されます。Web UI は元ファイルを使い続けるため、GIF アニメーションは維持されます。

### ImageEmojis-Bero 1.9.x

Bukkit/Paper 系では [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) と `plugins/KOKOTO-WebChat/emojis` を共有できます。重要な設定は `serverIp`, `webServerPort`, `emojisFolder: /KOKOTO-WebChat/emojis`, `templateFormat: ':<emoji>:'`, `replaceInCommands: true` です。Resource-pack HTTP host/port は Minecraft client から到達可能である必要があり、KWC Web port とは別です。詳細は `IMAGEEMOJIS_BERO_1_9_0_JA.md`。一般運用は [upstream ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis) を参照してください。

### SimpleNicks-Bero

Bukkit/Paper 系で [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) の nickname を表示する場合は `player-display.mode: "display-name"` を使用します。詳細は `SIMPLENICKS_BERO_JA.md`、一般運用は [upstream SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks) を参照してください。

## コマンドパネル

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: ADMIN
  run-from-chat-input: false
  max-length: 0
```

`allow-all: true` は Web UI から任意のコンソールコマンドを実行できるため、HTTPS と強い認証が前提です。`run-from-chat-input: false` の場合、コマンドはボタン/モーダルからのみ実行されます。

## メディアプレビューの高さとスクロール安定性

`ui.image-preview-max-height` は画像、GIF、動画、iframe 系プレビューの表示高さを制限します。推奨範囲は `640-720` で、デフォルトは `720` です。

```yaml
ui:
  image-preview-max-height: 720
```

`0` にすると高さ制限なしになります。ただし、非常に大きいメディアまたは無制限プレビューは、メディアの読み込み完了時にスクロールジャンプを起こすことがあります。特に virtual scroll とメディアの多い長いチャット履歴を併用する場合に発生しやすくなります。

## プレビュー

```yaml
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  media-click-to-load: true
  youtube-nocookie: true
  youtube-remember-expanded: true
  youtube-autoplay-on-open: false
  youtube-max-embeds-per-message: 1

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
      hide-media: false
      hide-thread: true
```

YouTube Shorts は通常の YouTube プレビュー経路で処理されます。Shorts は縦型プレイヤーで表示され、YouTube の loop パラメーターを使用します。

TikTok と X/Twitter は、閲覧者のブラウザーから外部コンテンツを読み込むため任意機能です。サーバーポリシーで外部 embed を許可できる場合のみ有効にしてください。公開サーバーでは `social-embeds.click-to-load: true` を維持し、ユーザーがプレビューを開いたときだけ外部プレイヤーを読み込む設定が安全です。

TikTok は公式 `player/v1` iframe を使用し、`description=0` と `music_info=0` を適用します。これにより、投稿本文や音楽情報の長さによってチャットパネル内に内部スクロールバーが出る問題を避けます。完全な投稿情報はプレイヤー下の元 TikTok リンクから開けます。

`youtube-click-to-load` または `media-click-to-load` を `false` にすると、対象のプレビューを即時表示します。自動再生はブラウザーのポリシーに従います。


## ユーザープロファイルとアカウント設定

```yaml
ui:
  user-profiles:
    enabled: true
    max-profiles: 5
    allow-import-export: true
```

ログインユーザーは theme、font、font size、色、opacity、text shadow、language などの表示設定を server-side account profile として複数保存できます。`max-profiles` は 0-20、`0` は server profile 保存を無効化します。JSON import/export は 16 KiB 以下の strict flat schema のみを許可し、session token、UUID、Web Push endpoint、window 座標は含みません。window 位置/size、minimize 状態、Web Push 登録は device-local のままです。3 項目は Web Admin Settings から変更できます。

## ブラウザー通知と Web Push

`notifications` はブラウザー通知とモバイル/バックグラウンド Web Push の共通既定値およびサーバー側の許可上限を制御します。`notifications.enabled` が両方の配信経路に対する単一の既定 ON/OFF 値です。旧 `browser-notifications.*` と `web-push.notify-*` キーは移行/互換入力としてのみ読み取られます。`notify-*` を `true` にするとユーザーがチャット設定で切り替えられ、`false` にすると有効化してもその通知種別はブロックされます。5.0.0 からログインユーザーの通知種別と keyword 一覧は account data に一度保存され、browser/device 間で再利用されます。初回初期化時は既存 browser 値を account 設定へ昇格します。guest は browser-local 設定を継続します。各 device の Web Push 購読にはバックグラウンド配信に必要な endpoint/filter data のみ保持します。

`web-push` は VAPID キー、subject、購読ファイル、TTL、既定の Push タイトルなどの Web Push 配信設定を保存します。HTTPS または localhost、通知権限、Service Worker / Push API 対応がそろうと、バックグラウンド/モバイルプッシュ通知を送信できます。Android/desktop ブラウザーでは、現在の origin が Service Worker + Push API に対応していれば BlueMap addon と standalone ページのどちらからでも Push を有効化できます。iOS/iPadOS の通常のブラウザータブは Web Push に対応していないため、ホーム画面に追加して Web アプリとして開いたページでのみ試してください。未対応の挙動はプラットフォーム制限として扱います。`notifications.enabled: true` で VAPID キーが空の場合、プラグインは `web-push-vapid.properties` に永続キーを生成します。`web-push.subject` は `mailto:admin@example.com` または `https://map.example.com` のような実在する連絡先/運用者識別用の VAPID URI にしてください。任意の文字列は推奨されず、一部の push サービスで拒否または低信頼として扱われる可能性があります。モバイルの「スパムの可能性」などの警告はブラウザー/OS が表示するため、プラグインから無効化できません。安定した HTTPS ドメイン、意味のある通知タイトル/本文、控えめな通知フィルター、連続したテスト通知を避けることで発生しにくくできます。

## PIP

```yaml
ui:
  picture-in-picture:
    enabled: false
```

この 1 つの設定で PIP ボタンと PIP 実行を制御します。ブラウザの URL/閉じる UI、OS レベルのウィンドウ透明化、外側の PIP ウィンドウ移動はチャット設定タイトルではなくブラウザ/OS 側で制御されます。

## ログイン失敗制限

`security.login-fail-limit`, `security.login-fail-window-seconds`, `security.login-lock-seconds` は Web パスワードログインの連続失敗を制限します。`login-fail-limit: 0` で無効化できます。この設定は Web パスワードログインにのみ適用されます。
## リンクコード発行制限

`auth.link-code-cooldown-seconds` と `auth.link-code-max-per-minute` は、Web UI が `/kchat auth <code>` 用のリンクコードをリモート IP ごとに発行できる頻度を制限します。各値を `0` にすると、その制限を無効化できます。


### アップロード保存容量の上限

`upload.max-total-size-mb` は `upload.directory` 直下の通常ファイルの合計容量を制限します。既定値 `0` は無制限です。新しいアップロードで上限を超える場合、KOKOTO WebChat は古い未参照アップロードから削除します。チャット履歴、SQLite 履歴、DM/グループメッセージ、保持対象の固定メッセージで参照されているファイルは残します。整理しても容量が足りない場合、アップロードは拒否されます。

### 絵文字容量表示

`emoji.max-total-size-mb` はカスタム絵文字の合計容量を制限します。制限を超えると、管理者アップロード画面に警告が表示されます。`emoji.show-storage-usage` は現在の絵文字容量表示、`emoji.show-storage-limit` は合計容量制限の表示を制御します。



## UI タイムゾーン

`ui.time-zone` はチャット時刻表示のタイムゾーンを指定します。`local` はブラウザー/端末のローカルタイムゾーンを使い、`UTC` や `Asia/Seoul` などの IANA タイムゾーンも指定できます。不正な値は Web UI でローカル時刻にフォールバックします。

## HTTP プロキシ / クライアント IP

`http.trusted-proxies` は `X-Forwarded-For` を信頼するプロキシを指定します。直接 HTTP で公開する場合は空のままにしてください。同じサーバー上の Caddy/Nginx 経由なら `127.0.0.1` と `::1` をブロック形式の YAML リストとして設定してください。`http.log-client-ip-resolution: true` は、ソケット IP、forwarded ヘッダー、解決後のクライアント IP をサーバーコンソールと `logs/latest.log` で確認する時だけ一時的に使ってください。詳しい確認手順は `docs/OPERATIONS_SECURITY_JA.md` を参照してください。

## SSE 接続数制限

`security.max-sse-connections-per-ip` と `security.max-sse-connections-total` は、長時間維持される `/stream` 接続数を制限します。各値は `0` で無効化できます。



`ui.text-color` はチャット本文の既定文字色です。`ui.ui-text-color` は権限表示、Web/Game の送信元表示、時刻、入力欄のプレースホルダー、アップロード/コマンドボタン、ピン留めラベルなどの UI 文字/記号の既定色です。空にすると選択中のテーマに従います。ユーザーはチャット設定でブラウザーごとに上書きできます。

```yaml
ui:
  text-color: ""          # 本文はテーマ既定
  ui-text-color: ""       # UI 表示/記号はテーマ既定
  # text-color: "#f4f4f4"
  # ui-text-color: "#b8d8ff"
```

`ui.input-background-color` は入力欄の背景色を全体設定で固定します。空のままにすると選択中のテーマに従います。ユーザーはチャット設定でブラウザーごとに上書きできます。

```yaml
ui:
  input-background-color: ""      # テーマ既定
  # input-background-color: "#1e1e24"
```

### システムメッセージ翻訳

組み込み announcement と Web コマンド結果メッセージには i18n キーが含まれます。`announcements.*.message` はフォールバック/カスタム文として保持してください。該当キーが言語ファイルにある場合、閲覧者には選択言語の翻訳文が表示されます。

折りたたまれた固定メッセージバーの文字も、設定されたチャットフォントとメッセージ文字サイズに従います。

### Text shadow / readability

- `ui.text-shadow-mode`: `none`、`auto`、`dark`、`light`、`custom` のいずれかです。文字色と背景色のコントラストが低い場合の視認性を補助します。
- `ui.text-shadow-custom`: `custom` モードで使用する CSS `text-shadow` 値です。チャット設定画面では、色ピッカーと横方向、縦方向、ぼかし、不透明度のスライダーで編集できます。保存値は標準の CSS 形式です。例: `0 1px 2px rgba(0, 0, 0, 0.85)`.

> テーマはブラウザごとのチャット設定からも変更できます。テーマを変更すると、文字色・背景色・影などの表示設定はそのテーマの既定値にリセットされます。


管理者向けカスタム絵文字メモ: 絵文字ファイル名またはフォルダー名を変更すると `:emoji:pack/name:` トークンも変わります。古いトークンを含む既存メッセージは、古いファイル/フォルダー名を残さない限り表示されなくなる場合があります。

## メッセージ検索

保存履歴が有効な場合、チャットパネル右上のフローティング領域の虫眼鏡ボタンと `/history/search` API でメッセージ本文と送信者を検索できます。検索オプションでは日付/時刻範囲、送信者、ソース、システム/イベントの含有を指定できます。検索結果はスクロール可能な一覧で表示され、チャットのテーマとフォント設定に従います。検索結果をクリックすると、既存の周辺履歴読み込みで該当メッセージへ移動します。i18n キー付きのシステム／イベントメッセージは、可能な場合は要求された Web UI 言語で検索・表示されます。 検索は `search.enabled` で有効/無効を切り替えられ、`search.result-limit` だけで Web UI の結果数と `/history/search` API の上限を制御します。別の内部最大値はなく、2000 に設定すれば最大 2000 件、10 に設定すれば最大 10 件を返します。10000 や 100000 のような非常に大きい値も受け付けますが、検索速度の低下、応答サイズの増加、CPU・メモリ・DB 負荷の増加につながる可能性があります。既定値は 50 で、通常利用では 50〜200 を推奨します。`config-version: "5.0.0_auto_migration"` の場合、不足している検索設定は startup/reload 時に自動挿入されます。正確な `config-version: "5.0.0"` で同一 version の自動 migration を停止した場合のみ、不足 key を手動で追加するか `_auto_migration` を再度有効にしてください。

## グループチャット

`group-chat.enabled` はWebグループチャット機能を有効にします。公開/非公開ルーム、ハッシュ保存される任意パスワード、招待、退出、ルームの非表示/再表示、ルーム設定、未読追跡、ユーザー別メッセージ非表示、メンバーのキック/ban/ban解除、所有者移譲に対応します。グループメッセージは `group-chat.sqlite-file`（既定値 `group-messages.db`）に保存されます。`group-chat.retention-days: 0` は期間整理なし、正の値は古いグループメッセージを物理削除します。


## 非公開チャットメタデータ・スーパー管理者

`private-chat-super-admins: []` には管理/容量確認用に DM/group metadata を閲覧できる exact UUID または Minecraft name を指定します。metadata view は participant/title、message count、storage size、retention state と管理操作を表示します。DM body は `direct-message.admin-audit.enabled: true`、group-chat body は `group-chat.admin-audit.enabled: true` の場合だけ read-only で開け、どちらも各 page read が audit log に記録されます。


`frontend.standalone.app-name` と `frontend.standalone.app-short-name` は standalone ページ/PWA 名を制御します。モバイルでホーム画面 Web アプリとして追加済みの場合、変更後は再追加してください。`web-push.notification-title` はテスト/システム/バックグラウンド Push の既定タイトルを制御します。空の場合は `frontend.standalone.app-name` を使用します。

### Dynmap アダプター

`adapters.dynmap` は Dynmap 自身の Web チャット転送を使わず、Dynmap 画面へ KWC フロントエンドを埋め込みます。`enabled: true` では一般的な `configuration.txt` から Dynmap の `webpath` を読み、`kokoto-web-chat/` アセットを配置して `index.html` 内の KWC マーカーブロックだけを管理します。Dynmap は `update-webpath-files: true` の場合に Web ファイルを再生成することがあるため、Dynmap 側の処理でページが戻った場合は `/kchat reload` で再適用できます。別ホストへコピーした Dynmap Web ルートを使う場合は、実際に共有/マウントされているディレクトリを `web-root` に指定してください。`api-base-url: ""` は他のマップアダプターと同じ自動 URL 解決を使います。


### LiveAtlas アダプター

`adapters.liveatlas` は既存の LiveAtlas static frontend に KWC を埋め込みます。LiveAtlas が Dynmap、squaremap、Pl3xMap、Overviewer、または複数 server を表示する構成でも同じ adapter を使用でき、Bukkit/Fabric/NeoForge/Forge で利用できます。`web-root: ""` の場合は一般的な local map web directory を確認しますが、`window.liveAtlasConfig` などの LiveAtlas marker を含む `index.html` だけを対象にします。Caddy/nginx の別 directory から配信する場合は、server から見える共有/マウント済み directory を `web-root` に指定します。KWC が所有するのは `addon-path` と LiveAtlas `index.html` 内の marker block のみです。LiveAtlas update で `index.html` が置き換わった場合は `/kchat reload` を実行してください。同じ物理 web root に LiveAtlas adapter と backend 固有 KWC adapter を同時に有効化しないでください。

### uNmINeD アダプター

`adapters.unmined` は既存の uNmINeD static Web export に KWC を埋め込みます。uNmINeD は Minecraft server plugin ではなく外部 map generator のため、Bukkit/Fabric/NeoForge/Forge で uNmINeD runtime 依存なしに同じ filesystem adapter を利用できます。現在の `index.html` と旧 `unmined.index.html` を対象にし、自動検出では `unmined.map.properties.js` と uNmINeD runtime などの marker を確認した場合だけ patch します。任意の export 先や Caddy/nginx document root では、server から見える共有/マウント済み export directory を `web-root` に指定します。map を再 export すると HTML や KWC 所有 asset が置き換わる場合があるため、その後 `/kchat reload` を実行してください。

### Overviewer アダプター

`adapters.overviewer` は既存の Minecraft Overviewer static Web map 出力へ KWC を埋め込みます。Overviewer は server plugin ではなく外部 renderer のため、Bukkit/Fabric/NeoForge/Forge で Overviewer runtime 依存なしに同じ filesystem adapter を利用できます。KWC は `Minecraft-Overviewer` generator metadata、`overviewerConfig.js`、`overviewer.js`、`overviewer.css` など Overviewer 固有の marker/asset を確認できる既存 `index.html` だけを対象にし、一般の Leaflet page は変更しません。任意の出力先や Caddy/nginx document root では server から見える共有/マウント済み Overviewer `outputdir` を `web-root` に指定します。Overviewer render または `--update-web-assets` により `index.html` が再生成される場合があるため、その後 `/kchat reload` を実行してください。独自 template を維持する場合は Overviewer の `customwebassets` をそのまま利用でき、KWC Stage 1 は Overviewer の Python 設定を編集しません。

> **IP / ルーターポート転送:** map adapter の direct HTTP 自動判定は、外部から到達する KWC port が `http.port`（既定 8899）と同じであることを前提にします。public `8900` → server `8899` のように外部 port を変換する場合、ブラウザーは NAT 変換を推測できないため、その map adapter の `api-base-url` を `http://PUBLIC_IP:8900/api` のように明示してください。転送された port で standalone を直接開く場合は current origin を使うため `api-base-url: ""` のままで構いません。
