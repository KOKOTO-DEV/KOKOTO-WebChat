# BlueMapWebChat 設定リファレンス

`plugins/BlueMapWebChat/config.yml` の説明です。

## 設定 version と migration fragment

`config-version` は自動 schema 変換番号ではなく、管理者が設定確認を完了した marker です。既存の設定値は自動上書きしません。startup/reload 時に既知の最上位 config block を bundled default 順へ並べ替えますが、各 block の現在の text・設定値・user-custom comment は保持し、default にない最上位 block は最後に元の順序で残します。既存 config を確認するたびに `config-reference-<plugin-version>.yml` を現在の JAR に同梱された完全な default `config.yml` の原文コピーとして生成し、同梱コメントもすべて保持します。この完全 reference は検出した旧 version に依存しないため、非常に古い config や version marker のない config を現在 default と比較する基準にできます。`config-version` がない、または異なる場合は、さらに `config-migration-<plugin-version>.yml` を生成して不足設定、変更された default、最終 review marker を示します。`message-tokens.custom: {}` のような空 map も実設定として扱い、不足時は migration に出力します。version marker が一致する場合は migration 比較を省略しますが、完全 reference は現在 default に維持します。別途、実 `config.yml` の comment が以前の BMWC bundled comment と完全一致する場合は現在の説明へ更新されることがありますが、設定値と user-custom comment は変更しません。生成された migration ファイルの末尾には、現在の `config.yml` と完全 reference の text diff も comment として出力します。同一行は出力しません。各差分では file 名を先に表示し、次の別行に `Line` または `Lines`、その下に実際に異なる内容だけを表示します。差分 source line は元の YAML indent をそのまま保持するため行頭に `#` だけを直接付けます。reference にだけ存在する連続 block は current config への挿入位置も別に表示します。Line 番号は report 生成時点の `config.yml` を基準にするため、設定編集後に `/bmchat reload` を実行すると現在の Line 番号で再生成されます。必要な値を手動 merge し、確認完了後だけ `config-version` を merge してください。

## 全体有効化スイッチ

新規生成された config は最上位の `enabled: false` から始まります。この状態では BlueMapWebChat は config の生成/読み込みのみを行い、/bmchat reload は引き続き使用できますが、Web/チャットサービス、リスナー、Discord 連携、DM ストア、アドオン設置、アップロード/絵文字初期化、クリーンアップ処理を開始しません。既存 config にこのキーがない場合は、アップグレード互換性のため有効として扱います。保存方式、保持期間、アップロード、プレビュー、認証、公開設定を確認してから `enabled: true` に変更してください。

## アップデート確認

```yaml
update-check:
  enabled: true
```

有効にすると、BlueMapWebChat はバックグラウンドで Modrinth の最新 stable release を確認します。OP または `bluemapwebchat.update.notify` 権限を持つ player がログインすると、レート制限付きで Modrinth を再確認するため、新しい release の検出が定期確認結果だけに依存しません。確認間隔、release channel、join 通知 delay、Modrinth/CurseForge download link は内蔵 default のままです。確認失敗で plugin 起動は停止せず、warning log に記録されます。

## 配置モード

### BlueMap アドオン

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true
```

`addons/bluemap-web-chat` にファイルを配置し、BlueMap の `webapp.conf` を更新します。

### standalone のみ

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false

standalone-web:
  enabled: true
  path: "/chat"
```

`http://<server-host>:8899/chat` を開きます。

### HTTPS リバースプロキシ

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"

web-addon:
  api-base-url: "/bmwc/api"

standalone-web:
  enabled: true
  # 推奨は空です。web-addon.api-base-url に従います。
  # 同じ公開 API 経路を明示する場合は "/bmwc/api" も使えます。
  api-base-url: ""

upload:
  # 推奨は空です。アップロード URL は有効な API base に自動追従します。
  # 従来の明示値も使えます: "/bmwc/api" または "/bmwc/api/uploads"
  public-base-url: ""

emoji:
  # 推奨は空です。絵文字 URL は有効な API base に自動追従します。
  # 従来の明示値も使えます: "/bmwc/api" または "/bmwc/api/emojis"
  public-base-url: ""
```

### 公開 URL オプションの規則

- `http.path-prefix` はプラグイン内部の HTTP API 経路です。通常は既定の `/api` のままにします。
- `web-addon.api-base-url` は BlueMap 埋め込みチャットが使う公開 API base です。HTTPS リバースプロキシでは通常 `/bmwc/api` にします。
- `standalone-web.api-base-url` は通常空のままにします。空の場合は `web-addon.api-base-url` を再利用します。例: `/bmwc/chat` は `/bmwc/api` を使います。必要なら同じ `/bmwc/api` を明示しても構いません。
- `upload.public-base-url` は通常空のままにします。空の場合は有効な API base に `/uploads` を追加します。例: `/bmwc/api/uploads`。
- `emoji.public-base-url` は通常空のままにします。空の場合は有効な API base に `/emojis` を追加します。例: `/bmwc/api/emojis`。
- 明示値も使えます。`/bmwc/api` を指定すると upload は `/uploads`、emoji は `/emojis` を自動で追加します。`/bmwc/api/uploads`、`/bmwc/api/emojis` はそのまま使います。
- 先頭 `/` のない相対値、例: `bmwc/api`、`bmwc/api/uploads`、`bmwc/api/emojis` は、`http.cors-origin` が実際の origin のときその origin を前に付けます。`cors-origin: "*"` の場合は `/bmwc/api...` のような同一 origin の絶対パスとして扱います。
- `https://map.example.com/bmwc/api` のような完全 URL はそのまま使います。

## チャット履歴保存

チャット履歴は `chat.history-storage` で `memory`、`jsonl`、`sqlite` のいずれかを選びます。`chat.history-size` と `chat.history-retention-days` は 3 つのモードで共通です。`0` は件数/期間の制限なしを意味します。新規生成された config は最上位の `enabled: false` から始まるため、これらの値を確認して `enabled: true` にするまでクリーンアップ処理は実行されません。サーバー方針として古いチャットの自動削除が必要な場合は、`30` や `90` などの正の保持日数を設定してください。アップロードと外部メディアキャッシュの保持設定も同じ考え方です。`chat.history-file` は JSONL のみ、`chat.history-sqlite-file` は SQLite のみで使われます。

## メッセージトークン

`message-tokens.enabled` は colon 区切りの管理者定義 text/control alias を有効にします。config には colon を付けず alias 名だけを記述し、たとえば `enter` は chat で `:enter:` と入力します。標準 alias は英語のみで、管理者が任意の言語へ変更・追加できます。未登録 alias はそのまま残るため custom/ImageEmojis token と共存できます。

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

`capture-game-whispers` は、キャンセルされていない `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` を送信者と受信者の BMChat DM スレッドへ複製します。Minecraft whisper 自体を再送・置換しません。Bukkit は任意の whisper plugin の最終成功結果を共通 API で提供しないため、既知 player 宛ての正しい形式の command を記録基準にします。

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

ゲストチャットは `cooldown-seconds` と `max-messages-per-minute` の両方で制限されます。1分あたりの既定値は `50` メッセージです。既存サーバーの設定ファイルは自動で上書きされないため、既存環境で新しい既定値を使う場合は `plugins/BlueMapWebChat/config.yml` を手動で更新してください。

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

URL 以外の本文クリックは `/bmchat reply <messageId> ` を入力候補にし、URL 部分はリンクを開く動作を優先します。`local-game-chat` は通常のローカルゲームチャットもクリック可能にします。他の chat-format plugin が最終描画を独占する場合は無効にしてください。同じサーバーのゲーム送信者名は `/w <実名> `、連携済み Web 送信者と別サーバーのゲーム送信者名は `/bmchat dm <実名> ` を候補にします。

ゲーム返信では、入力されたカスタム絵文字 token を Web 履歴とサーバーリレー用に保持し、送信元サーバーの Minecraft 表示にはゲーム側絵文字 plugin が処理した command body を再利用します。処理済み glyph がなく `emoji.game-link.mode` が `preserve` の場合、認識済み token は互換性のため通常の Bukkit chat 行として出力されます。この fallback 行には BMChat の click/hover metadata は付きません。



`server-relay` は複数の BlueMapWebChat サーバーの公開チャットを接続します。ゲーム、連携済み Web ユーザー、ゲストのメッセージを相手側の Web チャットと Minecraft チャットへ送り、メッセージ ID、返信関係、送信者、発信元サーバー情報を保持します。

## サーバーリレー設定

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
      url: "https://server3.example.com/bmwc/api"
      secret: ""
      enabled: true
```

サーバー 3 側では `server-id: "server3"` とし、`peers` に `id: "server1"` とサーバー 1 の公開 API URL を登録します。受信側の peer ID は送信側の `server-id` と正確に一致し、各サーバー ID は一意でなければなりません。

## HTTPS / リバースプロキシ

`url` は相手サーバーで外部から到達できる BMChat API base です。`/relay/receive` は自動追加されます。

```text
設定: https://server3.example.com/bmwc/api
要求: https://server3.example.com/bmwc/api/relay/receive
```

公開 HTTPS ルートは `/relay/receive` の POST を含む API パス全体を内部 BMChat HTTP リスナーへ転送してください。HTTPS 経由なら 8899 を外部公開する必要はありません。プロキシは `X-BMWC-Relay-Version`, `X-BMWC-Relay-From`, `X-BMWC-Relay-Timestamp`, `X-BMWC-Relay-Signature` を保持する必要があります。自己署名証明書は Java trust store へ登録しないと TLS 検証で失敗します。

## 秘密鍵

- `shared-secret` は全 peer の既定キーです。
- `peers[].secret` はその接続だけのキーで、共通キーより優先されます。
- 2 サーバーなら同じ長い `shared-secret` を両方に設定し、peer の `secret` は空にできます。
- peer キーも共通キーもない peer は無効として除外されます。

## 複数サーバーとループ防止

フルメッシュでは全サーバーが互いを登録し、ハブ構成では leaf が hub のみを登録して hub が全 leaf を登録します。relay ID の重複排除、発信元抑止、直前 peer 除外、`max-hops` により循環構成でも無限ループを防ぎます。停止中の peer へ後から再送する永続オフラインキューはありません。

## reload と診断

`/bmchat reload` は以前の relay を閉じ、現在の設定で作り直します。常時接続ではなくメッセージごとの HTTP(S) 要求なので、別の再接続操作はありません。

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

受信側の peer 一覧や秘密鍵を変えた場合は受信側でも `/bmchat reload` を実行してください。

## 表示

Web では別サーバーのメッセージだけ `originServerId` 由来の固定色サーバーバッジを表示し、現在のサーバー自身のバッジは省略します。Web→ゲームでは別サーバー由来の古い形式に `{server}` / `{server_id}` がなければ `[server-name]` を自動付与します。Discord は共有外部チャンネルのためサーバー表示を維持します。DiscordSRV ループとイベント重複を避けるため `sources.discord` と `sources.system` は既定で無効です。

## Discord 連携オプション

```yaml
discordsrv:
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-to-discord-format: "[{server}] {sender}: {message}"
  game-to-discord: false
  append-web-emoji-links: true
  append-game-emoji-links: true
  reply-relay:
    enabled: false
```

format は `{server}`, `{server_id}`, `{sender}`, `{name}`, `{role}`, `{source}`, `{message}`, `{channel}` に対応します。relay 有効時、古い format に server placeholder がなければ `[server-name]` を自動付与します。同じ Discord チャンネルを複数サーバーで共有する場合、実際のローカル Minecraft チャットを検知した発信元サーバーの BMChat だけが DiscordSRV の通常ゲーム転送にサーバー名と絵文字リンクを追加し、他サーバーは編集しません。受信側 peer は relay メッセージを Discord へ再送しないため、発信元サーバーの Discord 連携が無効または失敗した場合に別サーバーが代送する relay-only fallback はありません。DiscordSRV が通常チャットを既に送る場合は重複防止のため `game-to-discord` を無効にしてください。

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

BlueMapWebChat はカスタム絵文字を `plugins/BlueMapWebChat/emojis` 以下に保存します。サブフォルダーは絵文字パックとして扱われます。

既定では `emoji.game-link.enabled` が `false` のため、Web→ゲームメッセージの `:pack/name:` や `:emoji:pack/name:` のようなカスタム絵文字トークンは変更されません。ImageEmojis などのゲーム側絵文字プラグインが Minecraft チャット内でトークンを描画する場合は、この既定値を使用してください。

`emoji.game-link.enabled` が `true` の場合、`emoji.game-link.mode` は `preserve`、`link`、`label` をサポートします。

- `preserve`: game-link が有効でもトークン保持動作を強制します。
- `link`: `label-format` テキストと短い BM Web Chat 画像リンクを送信します。
- `label`: `label-format` テキストのみを送信します。

`emoji.game-link.*` は Web→Minecraft チャットのみに影響します。Discord の画像プレビューリンクは、Web→Discord 用の `discordsrv.append-web-emoji-links` と Game→Discord 用の `discordsrv.append-game-emoji-links` で分けて制御します。`append-game-emoji-links` は可能な場合 DiscordSRV の通常の Minecraft→Discord リレー本文を編集し、`game-to-discord` は BM Web Chat がゲームチャットを Discord へ直接送信したい場合にのみ必要です。

BM Web Chat は Web 履歴とリレー payload に正規の絵文字 token を保持します。ImageEmojis または ImageEmojis-Bero が有効な場合、公開されている runtime 絵文字 repository を reflection で読み取り、クリック可能な Minecraft component を作成する前に受信サーバーの有効な glyph へ token を変換します。hard dependency の追加や resource pack の解析は行いません。

interactive chat では ImageEmojis glyph を先に挿入してから sender・reply・URL の click event を構築するため、絵文字表示とクリック可能な URL が同時に動作します。受信サーバーで解決できない既知 token のみ、別のゲーム側 renderer 向けに単一の plain Bukkit fallback を使用します。この fallback には BMChat の click/hover metadata を付けられません。

`default-pack` と `aliases` は、flat なゲーム側トークンを BM Web Chat の pack/name id に対応付けるために使います。例:

```yaml
emoji:
  game-link:
    default-pack: "default"
    aliases:
      wave: "default/wave"
```

GIF/JPG/JPEG/WEBP 絵文字の元ファイルには、PNG のみを読むゲーム側絵文字プラグインとの互換性のため、同じフォルダーに PNG sidecar が自動生成されます。Web UI は元ファイルを使い続けるため、GIF アニメーションは維持されます。

### ImageEmojis-Bero 1.9.0

互換対象は [ImageEmojis-Bero 1.9.0](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) です。Web とゲームで同じ絵文字を使う場合は `emojisFolder: "/BlueMapWebChat/emojis"`, `templateFormat: ":<emoji>:"`, `replaceInCommands: true` を設定し、ユーザーへ `imageemojis.use` を付与します。詳細は `IMAGEEMOJIS_BERO_1_9_0_JA.md` を参照してください。

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


## ブラウザー通知と Web Push

`notifications` はブラウザー通知とモバイル/バックグラウンド Web Push の共通既定値およびサーバー側の許可上限を制御します。`notifications.enabled` が両方の配信経路に対する単一の既定 ON/OFF 値です。旧 `browser-notifications.*` と `web-push.notify-*` キーは移行/互換入力としてのみ読み取られます。`notify-*` 値を `true` にすると各ユーザー/ブラウザーがチャット設定で切り替えられ、`false` にするとユーザーが有効化してもその通知種別はブロックされます。`notify-system` が許可されている場合、ユーザーはチャット設定でサーバー通知をすべて、参加/退出のみ、オフから選べます。`notify-keywords` はユーザー定義のキーワード通知を制御します。キーワード一覧はブラウザー/端末ごとに保存され、バックグラウンド判定のためその端末の Web Push 購読にのみ同期されます。

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

`auth.link-code-cooldown-seconds` と `auth.link-code-max-per-minute` は、Web UI が `/bmchat auth <code>` 用のリンクコードをリモート IP ごとに発行できる頻度を制限します。各値を `0` にすると、その制限を無効化できます。


### アップロード保存容量の上限

`upload.max-total-size-mb` は `upload.directory` 直下の通常ファイルの合計容量を制限します。既定値 `0` は無制限です。新しいアップロードで上限を超える場合、BlueMapWebChat は古い未参照アップロードから削除します。チャット履歴、SQLite 履歴、DM/グループメッセージ、保持対象の固定メッセージで参照されているファイルは残します。整理しても容量が足りない場合、アップロードは拒否されます。

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

保存履歴が有効な場合、チャットパネル右上のフローティング領域の虫眼鏡ボタンと `/history/search` API でメッセージ本文と送信者を検索できます。検索オプションでは日付/時刻範囲、送信者、ソース、システム/イベントの含有を指定できます。検索結果はスクロール可能な一覧で表示され、チャットのテーマとフォント設定に従います。検索結果をクリックすると、既存の周辺履歴読み込みで該当メッセージへ移動します。i18n キー付きのシステム／イベントメッセージは、可能な場合は要求された Web UI 言語で検索・表示されます。 検索は `search.enabled` で有効/無効を切り替えられ、`search.result-limit` だけで Web UI の結果数と `/history/search` API の上限を制御します。別の内部最大値はなく、2000 に設定すれば最大 2000 件、10 に設定すれば最大 10 件を返します。10000 や 100000 のような非常に大きい値も受け付けますが、検索速度の低下、応答サイズの増加、CPU・メモリ・DB 負荷の増加につながる可能性があります。既定値は 50 で、通常利用では 50〜200 を推奨します。既存の config.yml にはこれらの項目を手動で追加するか、既定設定とマージしてください。

## グループチャット

`group-chat.enabled` はWebグループチャット機能を有効にします。公開/非公開ルーム、ハッシュ保存される任意パスワード、招待、退出、ルームの非表示/再表示、ルーム設定、未読追跡、ユーザー別メッセージ非表示、メンバーのキック/ban/ban解除、所有者移譲に対応します。グループメッセージは `group-chat.sqlite-file`（既定値 `group-messages.db`）に保存されます。`group-chat.retention-days: 0` は期間整理なし、正の値は古いグループメッセージを物理削除します。


## 非公開チャットメタデータ・スーパー管理者

`private-chat-super-admins: []` には管理/容量確認用に DM/group metadata を閲覧できる exact UUID または Minecraft name を指定します。metadata view は participant/title、message count、storage size、retention state と管理操作を表示します。DM body は `direct-message.admin-audit.enabled: true`、group-chat body は `group-chat.admin-audit.enabled: true` の場合だけ read-only で開け、どちらも各 page read が audit log に記録されます。


`standalone-web.app-name` と `standalone-web.app-short-name` は standalone ページ/PWA 名を制御します。モバイルでホーム画面 Web アプリとして追加済みの場合、変更後は再追加してください。`web-push.notification-title` はテスト/システム/バックグラウンド Push の既定タイトルを制御します。空の場合は `standalone-web.app-name` を使用します。
