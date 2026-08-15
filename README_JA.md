# BlueMapWebChat

Bukkit/Paper/Spigot 系サーバー向けの Web チャットプラグインです。BlueMap の Web アドオンとして表示することも、BlueMap なしで standalone `/chat` ページとして使うこともできます。

## 主な機能

- BlueMap 内チャットパネル、または standalone Web チャットページ
- ゲーム ↔ Web チャット双方向連携
- HMAC 署名付きサーバー間公開チャットリレー、サーバー別 Web バッジ、ゲーム/Discord サーバー表示
- Minecraft クリック返信(`/bmchat reply`)と Web 送信者クリック BMChat DM(`/bmchat dm`)
- ゲーム `/w`/`/msg`/`/tell` 系 whisper の両ユーザー Web DM への任意複製
- ゲストチャット、計算 captcha、クールダウン、分間制限
- `/bmchat auth <code>` によるアカウント連携、Web パスワードログイン、ローカル管理者
- Web 管理/モデレーターパネル、メッセージ非表示、ゲスト/IP ミュート、セッション revoke
- 管理者向けカスタム絵文字管理: フォルダー/ファイルの作成、複数アップロード、名前変更、移動、削除
- ImageEmojis-Bero 1.9.0 の token・ゲーム返信・サーバーリレー互換
- ファイル/クリップボードアップロード、画像/動画/音声/YouTube/Shorts プレビュー、任意の TikTok / X(Twitter) 埋め込み
- DiscordSRV 連携、Discord CDN メディアキャッシュ
- 返信と元メッセージへのジャンプ、ゲーム内返信プレビュー、ピン留め、仮想スクロール、移動/リサイズ可能なウィンドウ、PIP
- UI 言語: en-US, ko-KR, ja-JP, zh-CN

## ビルド

```bash
mvn clean package
```

```text
target/BlueMapWebChat-4.7.0.jar
```

## インストール

1. jar を `plugins/` に入れます。
2. サーバーを一度起動して `plugins/BlueMapWebChat/config.yml` を生成します。
3. 新規生成された config は最上位の `enabled: false` から始まります。設定確認前は config 生成以外の機能は開始されませんが、`/bmchat reload` は使用できます。
4. 保存方式、保持期間、アップロード、プレビュー、認証、公開設定を確認してから `enabled: true` に変更します。
5. BlueMap 埋め込みで使う場合は `web-addon.auto-install` と `web-addon.auto-patch-webapp-conf` を `true` のままにします。
6. standalone のみで使う場合は `standalone-web.enabled: true`, `web-addon.auto-install: false`, `web-addon.auto-patch-webapp-conf: false` にします。
7. サーバーを再起動するか `/bmchat reload` を実行します。BlueMap の Web アセットが更新されない場合は `/bluemap reload` も実行します。


既存の設定値は自動上書きされません。startup/reload 時に既知の最上位 `config.yml` block は 4.7.0 の bundled default 順へ並べ替えられますが、各 block の現在の text、設定値、custom comment は保持されます。default にない最上位 block は既知 block の後ろに元の順序で残ります。既存設定を確認するたびに、BlueMapWebChat は現在の JAR に同梱された完全な default config を全コメント込みでそのままコピーした `plugins/BlueMapWebChat/config-reference-4.7.0.yml` を生成または更新します。この reference は元の設定 version に関係なく作られるため、4.5.x、4.6.x、version marker のない古い config でも 4.7.0 の完全な構成と直接比較できます。`config-version` がない、または実行中の plugin version と異なる場合は、さらに `config-migration-4.7.0.yml` を生成し、不足設定、変更された default、最終 `config-version` marker を示します。`message-tokens.custom: {}` のような空 map も実設定として扱い、不足時は migration に含めます。migration file 末尾には current と reference の text diff も comment として追加します。同一行は出力せず、各差分では最初に file 名、その次の別行に `Line` または `Lines`、さらにその下に実際に異なる内容だけを表示します。差分の source line は元の YAML indent をそのまま保持するため行頭に `#` だけを直接付けて表示し、reference にだけ存在する block は current config への挿入位置も別に表示します。`config-version: "4.7.0"` が一致する場合は migration 比較を省略しますが、完全な reference ファイルは現在の default に維持されます。今回の更新は `docs/UPGRADE_4_7_0_JA.md`、前回は `docs/UPGRADE_4_6_3_JA.md` を参照してください。

### 4.7.0 絵文字の複数アップロードと互換範囲

4.7.0 では設定可能な `:token:` メッセージ置換も追加します。既定 alias は英語のみで、管理者は任意の言語へ変更・追加できます。newline / blank-line / indentation と印字可能な custom 置換をサポートし、未知のトークンは絵文字互換のためそのまま残します。

4.7.0 では管理者の絵文字アップロードも通常のチャットファイルアップロードと同じ picker フローを使います。画面の Upload ボタンで非表示の multiple file input を開き、ファイルを選択すると選択内容をすぐ通常配列へコピーして native input をクリアし、そのまま順次アップロードを開始します。選択後の追加確認 Upload はありません。進捗表示と実転送中のキャンセルは維持され、ファイル上限、総容量制限、重複名処理、監査ログ、PNG sidecar 生成は既存のサーバーアップロード経路をそのまま使用します。

Bukkit/Spigot API baseline を 1.21 から 1.18 に下げ、Java 17 は維持します。このリリースの保守的な Minecraft 対応範囲は **1.18 ～ 26.2** です。Paper 固有の `AsyncChatEvent` は引き続き reflection で検出し、Bukkit の legacy chat event を fallback として使用します。

詳細は `docs/UPGRADE_4_6_4_JA.md` を参照してください。

## 4.6.3 管理者グループチャット監査

4.6.3 ではグループチャット本文を確認できる任意の読み取り専用管理者監査を追加します。DM 監査と同じ `private-chat-super-admins` の明示 account list を使いますが、`group-chat.admin-audit.enabled` は独立したスイッチです。監査者は room に参加せず、既読状態や未読数を変更せず、送信・upload・hide・member 管理もできません。各監査 page read は本文をコピーせず `admin.group-audit-read` として記録されます。

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

4.6.3 では DM / グループチャットのライブ更新中に動画・音声が先頭から再生される問題も修正します。プライベートチャットのメッセージ一覧は通常チャットと同様に stable key で既存メッセージを維持し、新規メッセージと配信/既読メタデータだけを更新するため、読み込み済みメディア DOM が保持されます。

詳細は `docs/UPGRADE_4_6_3_JA.md` を参照してください。

### 4.6.2 DM / グループチャットの配信状態と再試行

他サーバーへの DM は、宛先サーバーが実際に保存したことを確認するまで `pending` のままです。保存確認後に `delivered` となり、経路・通信・タイムアウトなどの失敗時は `failed` となって同じ relay ID で再試行できます。これにより応答だけが失われた場合でも受信側に同じメッセージを重複保存しません。Web DM とグループチャットも client message ID を使い、不確実な HTTP 応答後の再送を重複なく処理します。サーバー指定のない DM 名は現在サーバーのプレイヤーだけを解決します。DM と group chat のすべての message は時刻の横に既読状態を表示します。1 対 1 DM は受信者が読む前は `未読`、読んだ後は `✓`、group chat は未読受信者数を数字で表示し 0 人になると `✓` になります。送信状態も短く `送信中`、失敗時は `失敗 · 再試行` のみ表示します。

サーバー間 DM を交換するすべてのサーバーでは BlueMapWebChat 4.6.2 以降を推奨します。詳細は `docs/UPGRADE_4_6_2_JA.md` を参照してください。

## standalone の URL

```text
http://<server-host>:8899/chat
```

## HTTPS / Caddy 推奨構成

公開サーバーでは、BlueMap と BlueMapWebChat を内部 HTTP サービスにし、HTTPS リバースプロキシの後ろに置くことを推奨します。

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
  path: "/chat"
  # 任意です。web-addon.api-base-url と同じ経路を指定できます。
  api-base-url: "/bmwc/api"

upload:
  # 推奨は空です。アップロード URL は自動的に /bmwc/api に従います。
  # 従来の明示指定も使えます: "/bmwc/api" または "/bmwc/api/uploads"
  public-base-url: ""
  # 0 = 無制限。正の値で upload.directory 全体のファイル容量を制限します。
  max-total-size-mb: 0

emoji:
  # 推奨は空です。絵文字 URL は自動的に /bmwc/api に従います。
  # 従来の明示指定も使えます: "/bmwc/api" または "/bmwc/api/emojis"
  public-base-url: ""
  max-total-size-mb: 64
  show-storage-usage: true
  show-storage-limit: true
```

詳細は `docs/CADDY_HTTPS_JA.md` を参照してください。

## よく使う設定

- `ui.language`: `en-US`, `ko-KR`, `ja-JP`, `zh-CN`
- `ui.theme`: `system`, `dark`, `light`, `high-contrast`
- `player-display.mode`: `name`, `display-name`, `custom-name`
- `player-display.strip-colors`: `false` の場合、実際のチャット送信者名に Minecraft legacy 色コードをレンダリングします。system/event 行は常に色コードを削除します。
- `commands.enabled`: Web コマンドパネル
- `commands.allow-all`: 任意のコンソールコマンドを許可
- `commands.run-from-chat-input`: 通常入力欄から `/command` を実行
- `ui.picture-in-picture.enabled`: PIP ボタンと PIP 実行を制御

## チャット履歴の保持期間

新規生成された config は最上位の `enabled: false` から始まるため、保持期間とクリーンアップ関連の値を確認して `enabled: true` にするまで自動整理は実行されません。サーバー方針に合わせてチャット履歴、アップロード、外部メディアキャッシュの保持期間を確認してから有効化してください。

## 1:1 ダイレクトメッセージスレッド

`direct-message.enabled` を有効にすると、1:1 会話スレッド型のメッセージボックスを使用できます。送信先には、UUID/名前が保存済みの連携済み・参加履歴プレイヤーに加えて、リレーメッセージに player UUID が含まれる別サーバーの送信者も含まれます。受信した表示名と実 Minecraft 名は Web DM の新規宛先検索に反映されるため、専用 DM ボタンなしで通常の検索から会話を開始できます。UUID のない guest/Discord 送信者は対象外です。A→B と B→A は同じスレッドを使い、保存は UUID 基準、UI 表示は可能な場合 `表示名 (実アカウント名)` 形式になります。

DM は公開チャット履歴とは別の専用ストアを使います。`direct-message.storage: auto` は、公開チャットが `jsonl` 保存方式のとき DM も JSONL を使い、それ以外では SQLite を使います。必要に応じて `direct-message.storage` を `sqlite` または `jsonl` に固定し、`direct-message.sqlite-file` または `direct-message.jsonl-file` を指定できます。`direct-message.retention-days: 0` は保持期限なしです。それ以外の値は DM 画面のタイトル横に保持期間として表示され、その日数を過ぎた DM 本文は物理削除されます。`direct-message.max-messages-per-thread: 0` はスレッドごとの件数削除なしです。`direct-message.confirm-hide` は Web UI で自分の表示から DM を隠す前に確認するかを制御します。個人メッセージがサーバーに保存されるため既定では無効です。サーバーポリシーに合わせて保持期間を決めてから有効化してください。


`direct-message.capture-game-whispers` でゲームの `/w`, `/msg`, `/tell` 系を同じ Web DM に複製できます。同じサーバーのゲーム送信者名は `/w <実名> `、Web 送信者は `/bmchat dm <実名> `、別サーバーのゲーム送信者名は `/bmchat dm <実名>@<server-id> ` が候補になります。また `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` で `名前@server-id` を指定すると、同じ cross-server BMChat DM relay で送信されます。

## カスタム絵文字とゲーム側絵文字プラグイン

BlueMapWebChat はカスタム絵文字を `plugins/BlueMapWebChat/emojis` 以下に保存します。サブフォルダーは絵文字パックとして扱われます。

既定では、Web→ゲームチャットは `:default/wave:` や `:emoji:default/wave:` のようなカスタム絵文字トークンをそのまま保持します。ImageEmojis などのゲーム側絵文字プラグインが Minecraft チャット内で同じトークン文字列を描画する場合は、この既定値を使用してください。

`emoji.game-link.enabled` を有効にした場合、`emoji.game-link.mode` は `preserve`、`link`、`label` をサポートします。

- `preserve`: 元のトークン文字列を変更しません。
- `link`: 設定されたトークン文字列と短い BM Web Chat 画像リンクを送信します。
- `label`: 設定されたトークン文字列のみを送信します。

`emoji.game-link.*` は Web→Minecraft チャットのみに影響します。Discord の画像プレビューリンクは別設定です。`discordsrv.append-web-emoji-links` は Web→Discord 用で、`discordsrv.append-game-emoji-links` は可能な場合 DiscordSRV の通常の Minecraft→Discord リレー本文を編集して Game→Discord トークン URL を追加します。複数サーバーが同じ Discord チャンネルを共有する場合、実際のローカルゲームチャットを検知した発信元サーバーだけがその DiscordSRV メッセージを編集し、他サーバーはサーバー名や絵文字リンクを重ねません。受信 relay peer はそのメッセージを Discord へ再送しません。DiscordSRV が通常の Minecraft チャットを既に中継している場合は、重複投稿を避けるため `game-to-discord` を無効のままにしてください。

BM Web Chat は Web 履歴とサーバーリレー payload に正規の絵文字 token をそのまま保持します。ImageEmojis または ImageEmojis-Bero が有効な場合は、公開されている runtime 絵文字 repository を reflection で読み取り、クリック可能な Minecraft component を作成するときに受信サーバーの現在の token→glyph mapping を使用します。hard dependency や resource pack の解析は不要で、解決できない token は従来のゲーム側レンダリング経路へ fallback します。

GIF/JPG/JPEG/WEBP 絵文字をアップロードすると、PNG のみを読むゲーム側絵文字プラグインとの互換性のため、同じフォルダーに PNG sidecar も作成します。

```text
plugins/BlueMapWebChat/emojis/default/wave.gif
plugins/BlueMapWebChat/emojis/default/wave.png
```

Web UI は元ファイルを使い続けるため、GIF アニメーションは維持されます。同じ絵文字ディレクトリを監視するゲーム側絵文字プラグインは PNG sidecar を利用できます。絵文字の追加や変更後は、そのプラグインの reload コマンドを実行してください。

ImageEmojis-Bero 1.9.0 の共有フォルダー、権限、command 変換、relay、troubleshooting は [`docs/IMAGEEMOJIS_BERO_1_9_0_JA.md`](docs/IMAGEEMOJIS_BERO_1_9_0_JA.md) を参照してください。

## YouTube Shorts、TikTok、X/Twitter プレビュー

YouTube Shorts の URL は通常の YouTube プレビューとして処理され、縦型プレイヤーとループ再生を使い、既定で有効です。TikTok と X/Twitter は任意の social embed として提供され、ユーザーのブラウザーから外部コンテンツを読み込むため、既定では無効です。TikTok はチャット内で長い説明文や音楽情報による内部スクロールバーを避けるため、公式 `player/v1` iframe を使い、説明文/音楽情報を非表示にします。完全な情報は元の TikTok リンクから開けます。

```yaml
preview:
  youtube-embed-enabled: true
  social-embeds:
    enabled: true
    click-to-load: true
    max-embeds-per-message: 2
    tiktok:
      enabled: false
    x:
      enabled: false
```

TikTok または X/Twitter は、外部 embed リクエストを許可できるサーバーでのみ有効にすることを推奨します。公開サーバーでは `click-to-load: true` を維持し、ユーザーがプレビューを開いたときだけ外部コンテンツを読み込む設定が安全です。

## コマンド

```text
/bmchat dm <player> <message>
/bmchat reply <messageId> <message>
/bmchat auth <code>
/bmchat password <newPassword>
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

## 権限

```text
bluemapwebchat.auth
bluemapwebchat.webchat
bluemapwebchat.dm
bluemapwebchat.reply
bluemapwebchat.group
bluemapwebchat.admin
bluemapwebchat.update.notify
```

## ドキュメント

- `docs/USER_MANUAL_JA.md` - 全機能のユーザー・運用総合マニュアル
- `docs/CONFIGURATION_JA.md`
- `docs/SERVER_RELAY_JA.md` - サーバー間公開チャットリレー
- `docs/UPGRADE_4_6_2_JA.md` - 4.6.1→4.6.2 upgrade
- `docs/UPGRADE_4_6_1_JA.md` - 4.6.0→4.6.1 upgrade
- `docs/UPGRADE_4_6_0_JA.md` - 4.5.5→4.6.0 設定/DB 更新
- `docs/CADDY_HTTPS_JA.md`
- `docs/I18N_JA.md`
- `docs/INSTALL_TROUBLESHOOTING_JA.md`
- `docs/UPLOAD_SECURITY_JA.md`
- `docs/RELEASE_CHECKLIST_JA.md`
- `docs/STANDALONE_REVIEW_JA.md`
- `docs/OPERATIONS_SECURITY_JA.md`

フォント補足: インストール済みフォントは CSS の font-family 名で入力する必要があります。チャット設定の確認ボタンで、権限要求なしに現在のブラウザーで利用できそうか推定できます。


URL 設定メモ: HTTPS リバースプロキシでは `web-addon.api-base-url` を `/bmwc/api` のような公開 API 経路に設定します。`standalone-web.api-base-url`、`upload.public-base-url`、`emoji.public-base-url` は通常空のままにします。空の場合、standalone は web-addon の API base を再利用し、upload/emoji は `/uploads` と `/emojis` を自動で付けます。`/bmwc/api`、`/bmwc/api/uploads`、`/bmwc/api/emojis` の明示指定も使用できます。先頭 `/` のない相対値は `http.cors-origin` が実際の origin のとき、その origin に対して解決されます。

## SQLite 履歴検索

SQLite 履歴ストレージを使用している場合、チャットパネル右上のフローティング領域の虫眼鏡ボタンからメッセージ本文と送信者を検索できます。検索オプションでは日付/時刻範囲、送信者、ソース、システム/イベントの含有も指定できます。検索結果はスクロール可能な一覧で表示され、チャットのテーマとフォント設定に従います。検索結果をクリックすると、既存の周辺履歴読み込みで該当メッセージへ移動します。i18n キー付きのシステム／イベントメッセージは、可能な場合は選択中の Web UI 言語で検索・表示されます。`search.result-limit` だけで Web UI の結果数と `/history/search` API の上限を制御し、別の内部最大値はありません。10000 や 100000 のような非常に大きい値も受け付けますが、検索速度の低下、応答サイズの増加、CPU・メモリ・DB 負荷の増加につながる可能性があります。

## グループチャットルーム

`group-chat.enabled` を有効にすると、Webグループチャットルーム機能を使用できます。ユーザーはルーム作成、公開/非公開の選択、任意のルームパスワード、保存済みプレイヤーの招待、招待の承諾/拒否、退出、自分の一覧からの非表示/再表示、ルーム設定変更、メンバーのキック/ban/ban解除、所有者移譲、Webからのメッセージ送信を行えます。公開ルームは一覧に表示され、非公開ルームは招待制です。ルームパスワードは平文ではなくPBKDF2ハッシュとして保存されます。

グループチャットは専用SQLiteストアを使用します（`group-chat.sqlite-file`、既定値 `group-messages.db`）。`group-chat.retention-days: 0` は期限なし、正の値はグループチャットタイトル横に保存期間として表示され、その日数を過ぎたメッセージは物理削除されます。`group-chat.max-messages-per-room: 0` は件数による整理なしです。このリリースはWeb中心で、ゲーム側 `/bmchat group` コマンド、ルームミュート、所有者/メンバー操作を超える詳細なロール管理UI、グループJSONL保存はまだ含まれていません。

### 非公開チャットメタデータのスーパー管理者

`config.yml` の `private-chat-super-admins` に exact UUID または Minecraft name を指定すると、管理/容量確認用 DM/group metadata を表示できます。default view は title/participant、message count、storage size、retention state、cleanup preview を表示します。`direct-message.admin-audit.enabled: true` を設定すると、同じ明示 account が DM body を read-only で開けます。4.6.3 では `group-chat.admin-audit.enabled: true` を別に有効化すると、room へ参加したり read state を変更したりせず group-chat body も read-only で開けます。通常 ADMIN/MODERATOR role は自動対象ではなく、audit page read はすべて audit log に記録されます。super admin は DM/group session lock と retention exclude も管理できます。

管理上影響のある操作は、既定で `plugins/BlueMapWebChat/audit` 配下の日付別テキストログに追記されます。audit ログはサーバー運用者向けで、Web UI には表示されません。


注: `standalone-web.app-name` / `standalone-web.app-short-name` でモバイルのホーム画面 Web アプリ名を変更でき、`web-push.notification-title` で既定の Push 通知タイトルを変更できます。`web-push.notification-title` が空の場合は `standalone-web.app-name` が使われます。Android/デスクトップブラウザーでは HTTPS と Push API が利用できれば BlueMap addon と standalone ページのどちらからでも Push を有効化できます。iOS/iPadOS では通常のブラウザータブではなく、ホーム画面に追加して Web アプリとして開いたページを使用してください。
