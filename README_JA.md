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
- 管理者向けカスタム絵文字管理: フォルダー/ファイルの作成、アップロード、名前変更、削除
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
target/BlueMapWebChat-4.6.0.jar
```

## インストール

1. jar を `plugins/` に入れます。
2. サーバーを一度起動して `plugins/BlueMapWebChat/config.yml` を生成します。
3. 新規生成された config は最上位の `enabled: false` から始まります。設定確認前は config 生成以外の機能は開始されませんが、`/bmchat reload` は使用できます。
4. 保存方式、保持期間、アップロード、プレビュー、認証、公開設定を確認してから `enabled: true` に変更します。
5. BlueMap 埋め込みで使う場合は `web-addon.auto-install` と `web-addon.auto-patch-webapp-conf` を `true` のままにします。
6. standalone のみで使う場合は `standalone-web.enabled: true`, `web-addon.auto-install: false`, `web-addon.auto-patch-webapp-conf: false` にします。
7. サーバーを再起動するか `/bmchat reload` を実行します。BlueMap の Web アセットが更新されない場合は `/bluemap reload` も実行します。


既存の `config.yml` は上書きされません。`config-version` がない、または実行中のプラグイン version と異なる場合、実ファイルと同梱 default を比較して `plugins/BlueMapWebChat/config-migration-4.6.0.yml` を生成します。生成ファイルには、そのまま merge できる不足設定、変更された default、最終確認用の `config-version` marker を実 YAML 設定として出力します。他の差分がなくても設定 version 管理のため fragment と `config-version` は生成されます。version 情報や旧・新 default の説明は `#` comment にします。custom 値や obsolete 候補の情報一覧は出力しません。`config-version: "4.6.0"` がプラグイン version と一致する場合は確認済みとして比較を省略します。`docs/UPGRADE_4_6_0_JA.md` を参照してください。

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

`direct-message.enabled` を有効にすると、1:1 会話スレッド型のメッセージボックスを使用できます。送信先は UUID/名前が保存済みの、連携済みまたは参加履歴のあるプレイヤーに限定されます。A→B と B→A は同じスレッドを使い、保存は UUID 基準、UI 表示は可能な場合 `表示名 (実アカウント名)` 形式になります。

DM は公開チャット履歴とは別の専用ストアを使います。`direct-message.storage: auto` は、公開チャットが `jsonl` 保存方式のとき DM も JSONL を使い、それ以外では SQLite を使います。必要に応じて `direct-message.storage` を `sqlite` または `jsonl` に固定し、`direct-message.sqlite-file` または `direct-message.jsonl-file` を指定できます。`direct-message.retention-days: 0` は保持期限なしです。それ以外の値は DM 画面のタイトル横に保持期間として表示され、その日数を過ぎた DM 本文は物理削除されます。`direct-message.max-messages-per-thread: 0` はスレッドごとの件数削除なしです。`direct-message.confirm-hide` は Web UI で自分の表示から DM を隠す前に確認するかを制御します。個人メッセージがサーバーに保存されるため既定では無効です。サーバーポリシーに合わせて保持期間を決めてから有効化してください。


`direct-message.capture-game-whispers` でゲームの `/w`, `/msg`, `/tell` 系を同じ Web DM に複製できます。同じサーバーのゲーム送信者名は `/w <実名> `、Web 送信者と別サーバーのゲーム送信者名は `/bmchat dm <実名> ` が候補になります。

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
```

## ドキュメント

- `docs/USER_MANUAL_JA.md` - 全機能のユーザー・運用総合マニュアル
- `docs/CONFIGURATION_JA.md`
- `docs/SERVER_RELAY_JA.md` - サーバー間公開チャットリレー
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

`config.yml` の `private-chat-super-admins` に正確な UUID または Minecraft 名を指定すると、管理/容量確認用に DM/グループチャットのメタデータのみ表示できます。この表示ではタイトル/参加者、メッセージ数、おおよその保存サイズ、保存期限状態、クリーンアッププレビュー数だけを表示し、本文は開けません。スーパー管理者は DM/グループセッションをロックしたり、自動削除対象から除外したりできます。これらは管理者専用機能です。

管理上影響のある操作は、既定で `plugins/BlueMapWebChat/audit` 配下の日付別テキストログに追記されます。audit ログはサーバー運用者向けで、Web UI には表示されません。


注: `standalone-web.app-name` / `standalone-web.app-short-name` でモバイルのホーム画面 Web アプリ名を変更でき、`web-push.notification-title` で既定の Push 通知タイトルを変更できます。`web-push.notification-title` が空の場合は `standalone-web.app-name` が使われます。Android/デスクトップブラウザーでは HTTPS と Push API が利用できれば BlueMap addon と standalone ページのどちらからでも Push を有効化できます。iOS/iPadOS では通常のブラウザータブではなく、ホーム画面に追加して Web アプリとして開いたページを使用してください。
