# KOKOTO WebChat アップグレードガイド

この文書は 4.5.5 から 5.1.0 までのアップグレード手順を統合したものです。複数バージョンを飛ばす場合は、バージョン順に各セクションを確認してください。

## 4.5.5 から 4.6.0 へのアップグレード

### 最初にバックアップ

サーバーを停止し、`plugins/KOKOTO-WebChat` をバックアップしてください。特に `config.yml`、SQLite DB と `-wal`/`-shm`、DM/group DB、upload、emoji、custom language、audit log、Web Push key/subscription を含めます。

### 自動生成される設定 migration fragment

KOKOTO WebChat は既存 `config.yml` を自動上書き・自動 merge しません。サーバー起動時と `/kchat reload` 時に実ファイルの `config-version` を確認します。

- `config-version` が実行中の plugin version と一致する場合、確認済みとして比較を省略し、同じ version の古い fragment を削除します。
- marker がない、または異なる場合、実 config と同梱 current default を比較して次を生成・更新します。

判定表:

| installed config の状態 | migration file |
|---|---|
| version marker なし | 他の差分がなくても生成 |
| running plugin と異なる | 生成または更新 |
| running plugin と一致 | 生成しない。残っている同 version の案内も削除 |

```text
plugins/KOKOTO-WebChat/config-migration-4.6.0.yml
```

生成ファイルは構造化 report ではなく、そのまま参照・コピーできる YAML 設定 fragment です。次を表示します。

- 実 `config.yml` にない設定と現在の推奨 default
- bundled default が変わり、実設定値が旧 default のままの設定
- 最終確認 marker となる対象 `config-version`

version 情報、件数、旧・新 default の説明はすべて `#` comment だけで記録します。`migration:`, `summary:`, `settings-to-add:`, `preserved-custom-values:`, `obsolete-settings-to-review:`, `finalize-after-review:` のような情報用 YAML section は作りません。custom 値や obsolete 候補の情報一覧も出力しません。

必要な設定だけを実 `config.yml` の同じ場所へ merge してください。実設定ファイルは自動変更されません。

不足設定も changed bundled default もない場合でも migration file を生成し、対象 `config-version` を含めます。これにより version marker のない設定も明示的に確認済みとして管理できます。

4.6.0 は 4.5.5 default を比較 baseline として同梱します。version marker がない config は 4.5.5 以前として扱います。明示された未知 version では誤判定を避けるため missing key のみ報告し、default 変更は推測しません。

確認後、実 config に設定します。

```yaml
config-version: "4.6.0"
```

version が一致している間、以後の起動と reload は比較を省略します。

### 4.6.0 の主な追加設定

- top-level `server-relay:`
- `direct-message.capture-game-whispers`
- `reply.game-click.enabled`
- `reply.game-click.local-game-chat`
- `reply.game-command-format`
- Discord `{server}`, `{server_id}` placeholder

version が不一致の間、実ファイルにない private-message capture と local-chat replacement は安全のため runtime で無効になります。

### DB migration

public SQLite history には relay metadata column が追加型 `ALTER TABLE` で追加されます。既存 row は保持されますが、過去 row の origin server は復元できません。初回 4.6.0 起動前に DB をバックアップしてください。

### 推奨確認

1. old config で起動し、`config.yml` を変更せず fragment が作られることを確認します。
2. missing setting と changed default を確認して merge します。
3. `config-version: "4.6.0"` を設定し `/kchat reload` 後に比較省略を確認します。
4. relay、game reply、whisper DM、Discord label、URL、ImageEmojis をテストします。

---

## 4.6.0 から 4.6.1 へのアップグレード

**5.1.0 注記:** この管理者 DM 本文監査の動作は 5.1.0 でも維持されています。`direct-message.admin-audit.enabled` と `private-chat-super-admins` を併用し、監査 view は read-only です。


### 主な変更

- relay message に player UUID がある場合、remote server の player を既存 web DM recipient search から検索できます。
- 既存 private chat metadata list から、任意で DM body を read-only audit view で確認できます。
- Modrinth を使う簡潔な update check と管理者 join 通知を追加しました。
- plugin/config version は `4.6.1` です。

### 設定 migration

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

### DM本文 audit を有効化

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

### Cross-server DM version requirement

cross-server DM を交換するすべてのサーバーで KOKOTO WebChat 4.6.1 以降を使用してください。`server · source` のクリックは対象投稿の UUID と origin server ID を直接渡し、remote search と既存 remote thread でも server ID と player UUID を保持します。

---

## 4.6.1 から 4.6.2 へのアップグレード

KOKOTO WebChat 4.6.2 は DM 配信の信頼性を改善し、同名 DM のルーティング問題を修正し、メッセージ単位の既読状態を追加します。新しい管理者向け設定項目はありません。

### 変更点

- server 指定のない DM 名は現在サーバーの player だけを解決します。他サーバー宛先は `server-id + UUID` で明示的に識別します。
- 他サーバー DM は宛先サーバーが実際に保存したことを確認してから配信完了とします。route / HTTP / timeout / destination failure は retry 可能な失敗として残ります。
- DM retry は永続 relay ID を再利用し、Web send は client message ID を使うため、request / response が不確実でも同じ message を重複保存しません。restart で中断された pending 配信は retry 可能な失敗として復旧します。
- group chat の Web send も client message ID で重複を防止します。正常送信完了の label は表示せず、時刻の横には処理中の `送信中`、失敗時の `失敗 · 再試行` だけを短く表示します。
- DM と group chat の**すべてのメッセージ**について既読状態を計算し、時刻表示の横に表示します。1 対 1 DM は相手が読む前に短い `未読` label、読んだ後に `✓` を表示します。group chat は未読受信者数を数字で表示し、0 人になると `✓` を表示します。
- 他サーバー DM の既読状態は認証済み private relay で返され、hub / chain 構成でも元の message 側へ反映されます。会話を再度開くと最新の既読 ACK を安全に再送するため、一時的な relay / HTTP 障害で既読マークが恒久的に失われることはありません。
- 多段 DM relay は最終宛先の保存確認後だけ上流へ success を返します。
- 更新通知を修正しました。通知対象の管理者がログインすると、以前の定期確認結果だけに依存せず、レート制限付きで Modrinth を再確認します。OP を明示的に通知対象として扱い、確認失敗は warning log に記録し、reload 時には以前の update listener を解除します。

### Config migration

4.6.1 と比べて 4.6.2 の bundled config に新しい管理者向け key はなく、既存 default も変更されません。review marker だけ更新します。

```yaml
config-version: "4.6.2"
```

review 済み 4.6.1 config で起動すると `plugins/KOKOTO-WebChat/config-migration-4.6.2.yml` を生成します。無関係な不足設定や local/default 差分がなければ、新しい `config-version` marker だけが含まれます。実際の `config.yml` は自動上書きされません。

### Cross-server deployment

server 間 DM を交換するすべての server は KOKOTO WebChat 4.6.2 以降を使用してください。JAR 交換後に各 server を再起動すると、新しい relay 処理と追加型 DB migration が有効になります。既存 DM / group chat message は保持されます。

---

## KOKOTO WebChat 4.6.3 upgrade

4.6.3 は group-chat message body の optional read-only administrator audit を追加します。既存の DM audit behavior は変更しません。

### New configuration

```yaml
group-chat:
  admin-audit:
    enabled: false

config-version: "4.6.3"
```

`4.6.2` として review 済みの config では、他の実際の不足がなければ migration fragment にこの switch と 4.6.3 review marker だけが出ます。既存 `config.yml` は上書きされません。

### Access requirements

両方が必要です。

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

通常 ADMIN/MODERATOR role だけでは body access はできません。audit view は read-only で room membership を必要とせず、room に参加せず read/unread state も変更しません。send/upload/hide や membership change もできません。各 page read は body を audit log にコピーせず `admin.group-audit-read` として記録されます。

### 設定コメント

4.6.3 では bundled `config.yml` のコメントを、現在の update check、cross-server DM の delivery/read ACK、group の read status、DM/group administrator audit の動作に合わせて更新します。起動時または `/kchat reload` 時、既存 config のコメントが **以前の KOKOTO WebChat bundled comment と完全一致する場合だけ** 新しい bundled comment に更新されます。この comment refresh は設定値を変更せず、ユーザーが編集したコメントは保持され、`config-version` も migration fragment の確認後に管理者が変更する方式のままです。
### プライベートチャットのメディア再生

DM とグループチャットのメッセージ一覧も、通常チャットと同じ stable key ベースの DOM 更新方式を使用します。同じ会話の更新では既存のメッセージと動画/音声 DOM を接続したまま維持し、新規・削除メッセージと配信/既読メタデータだけを更新します。そのため、メッセージの送受信中でも再生中のメディアが先頭から再開しません。すでに最下部を表示している場合だけ最新メッセージを追従し、途中を表示している場合は現在位置を維持します。会話を離れるか別の会話へ切り替えると、その会話のメッセージ/メディア DOM と private media-open 状態を完全に破棄します。再度開いた場合は `▶ Video` / `▶ Audio` の未展開 click-to-load 状態から新しく作成され、click-to-load が無効でも以前のプレイヤーは再利用せず、新しい未再生のメディア要素を作成します。再入室だけで `play()` は呼び出しません。

---

## KOKOTO WebChat 4.7.0 アップグレード

4.7.0 では Bukkit/Spigot の互換基準を Minecraft 1.18 まで下げ、管理者向けカスタム絵文字の複数ファイルアップロードと設定可能なメッセージトークン置換を追加します。

### 互換性

- 保守的な対応範囲: **Minecraft 1.18 ～ 26.2**
- Java: **17**
- `plugin.yml`: `api-version: '1.18'`
- Maven compile API: `spigot-api:1.18.2-R0.1-SNAPSHOT`
- Paper `AsyncChatEvent` は reflection で検出し、Bukkit `AsyncPlayerChatEvent` をリンク済み fallback として維持します。
- 1.17 以下はこのリリースの公式対応範囲に含めません。

### カスタム絵文字の複数アップロード

絵文字アップロードは通常のチャットファイルアップロードと同じファイル選択フローを使います。Upload ボタンで非表示の multiple file input を開き、ファイル選択後すぐに選択 `FileList` を通常配列へコピーし、native input をクリアして順番にアップロードします。選択後の追加確認ボタンや file-picker 用 focus/visibility 回避処理はありません。進捗表示と実転送中のキャンセルは維持されます。既存のサーバー endpoint がファイル単位の検証、ストレージ計算、重複名処理、監査ログ、PNG sidecar 生成を担当します。

### メッセージトークン

既定 alias は英語のみで、管理者は任意の言語へ変更・追加できます。newline / blank-line / tab の固定アクションと、印字可能文字だけを使う custom 置換を設定できます。未知の `:token:` はそのまま残るため、既存のカスタム絵文字や画像絵文字と共存できます。

### 設定

4.7.0 では `message-tokens` セクションを追加します。それ以外の既存既定値は変更せず、review marker は次のように変更されます。migration 比較は 4.6.3 だけに限定されず、さらに古い config や version marker のない config も現在の 4.7.0 設定に対して不足項目を確認します。また `config-reference-4.7.0.yml` を常に生成し、現在 JAR の完全な 4.7.0 default config と全コメントをそのまま提供します。`message-tokens.custom: {}` のような空 map も不足時は migration に保持されます。migration ファイル末尾には完全 reference との text diff も comment として追加します。同一行は出力せず、各差分は file 名、別行の `Line` または `Lines`、実際に異なる内容の順で表示します。差分 source line は元の YAML indent を保持するため行頭に `#` だけを直接付け、reference-only block は挿入位置も表示します。

```yaml
config-version: "4.7.0"
```

startup/reload 時には既知の最上位 `config.yml` block も 4.7.0 bundled default 順へ並べ替えますが、各 block の現在の text・設定値・custom comment は保持し、default にない最上位 block は最後に元の順序で残します。

#### ゲーム内の改行動作

設定した `newline` / `blank-line` トークンで生成した改行だけを保護したまま Minecraft の既存の1行サニタイズを通し、最終送信時に個別のゲームチャット行として出力します。通常の CR/LF 入力は従来どおり平坦化されます。サーバー間リレーでこの意図的な改行をゲームに表示するには、受信側の KOKOTO WebChat にも同じ 4.7.0 のトークン行送信対応が必要です。古い受信側は通常の LF を従来の平坦化処理で空白に変換します。

---

## KOKOTO WebChat 5.0.0 アップグレード

5.0.0 は、正式版 4.7.0 以後の開発内容を統合し、**BlueMapWebChat (BMWC) → KOKOTO WebChat (KWC)** の名称移行を完了する major release です。

### 配布先の移行

最も安全な公開順序は **5.0.0 を既存 BlueMapWebChat project listing に先に公開すること**です。4.7.0 の update checker は旧 `bluemapwebchat` Modrinth project を参照するため、旧 listing に 5.0.0 を置くことで既存利用者が bridge release を検出できます。

旧/移行中の URL:
- Modrinth: `https://modrinth.com/plugin/bluemapwebchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`
- GitHub: `https://github.com/KOKOTO-DEV/BlueMapWebChat`

移行後の予定 canonical URL:
- Modrinth: `https://modrinth.com/plugin/kokoto-webchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`
- GitHub: `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`

KWC 5.0.0 は Modrinth の `kokoto-webchat` を先に確認し、存在しない間は `bluemapwebchat` に fallback します。同一 project として rename できない platform では、旧 BMWC page を終了/移行案内として残し、新 KWC page へリンクします。4.7.0 利用者が 5.0.0 を検出できる前に旧 listing を閉じないでください。

### 4.7.0 → 5.0.0 の主な変更

- 正式 identity を KOKOTO WebChat に統一: `/kchat` (`/kc`), `kwc.*`, KWC data directory, `dev.kokoto.webchat`, `kwc-*` modules。
- Bukkit/Paper/Spigot、Fabric 16 exact-target、NeoForge 12 exact-target、Forge 16 exact-target が shared core を利用。
- BlueMap、squaremap、Dynmap、Pl3xMap、LiveAtlas、uNmINeD、Overviewer adapter を追加/整理。
- standalone を既定有効、public prefix `/chat`、API `/chat/api` に統一。
- Unicode content filter、UTF-8 filter list、mask/replace、anti-evasion、Web Admin 編集/テストを追加。
- account ごとの server-side UI profile、strict JSON import/export、account 共通 keyword/notification 設定を追加。
- Web Push と live-page OS notification の同一 device 重複を抑止。
- 管理者 Discord keyword alert を追加。matching/format/mention/dedupe は KWC、DiscordSRV は JDA connection/channel mapping のみ提供。
- `update-check.enabled` は Bukkit だけでなく Fabric・NeoForge・Forge でも動作し、KWC 優先/BMWC fallback の Modrinth 確認と `kwc.update.notify` ログイン通知を使用します。
- `upload.filename-mode: random|original` を追加し、Unicode/space/`~`/`+`/`%` と Windows clipboard 8.3 alias 問題を修正。
- Discord game relay mode、server relay、cross-server DM/read status、platform 共通 `/kchat` を整理。
- 通常 UX を変えない security hardening: Bearer auth、one-time SSE ticket、body size limit、bounded HTTP worker、admin IP enforcement、Web Push SSRF 防御、Discord mention/CDN redirect 防御、iframe source validation。

### Platform support

- Bukkit/Paper/Spigot: 1.18–26.2, Java 17 bytecode。
- Fabric exact-target: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。target ごとに JDK 17/21/25 を使用。
- NeoForge exact-target: `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。target ごとに JDK 17/21/25 を使用。
- Forge exact target: `1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。

### BMWC data/config migration

Bukkit の既存 `plugins/BlueMapWebChat` は初回 KWC 起動時の migration input として利用でき、元 directory は変更せず残します。`web-addon.* → adapters.bluemap.*`、`standalone-web.* → frontend.standalone.*` を変換します。旧 `/bmchat` 等は command alias として登録せず、旧 `bluemapwebchat.*` permission は compatibility fallback のみです。`X-BMWC-Relay-*` wire header は既存 peer 互換のため維持します。

旧 `/bmwc/api`・`/bmwc/chat` reverse proxy は新 `/chat` layout に手動変更してください。

### Config migration

4.7.0→5.0.0 reference 比較は **79 paths added / 14 removed / 2 existing values changed** です。実際の version migration では既存値を保持して不足 setting/comment を挿入し、`config-version: "5.0.0_auto_migration"` にします。same-version auto backfill を止める場合のみ exact `5.0.0` にします。

### Release acceptance


> `validate-release-windows.bat` と必要な PowerShell helper は source archive に含まれています。別の `KWC-5.1.0-validation-tools.zip` には開発専用の browser regression tool のみが含まれ、release build の実行には不要です。

`validate-release-windows.bat` が `FINAL RELEASE BUILD PASS`、deployable JAR 45 個、static/config/i18n/document validation、主要 flow の smoke test をすべて通過した候補だけを正式配布します。

---

## KOKOTO WebChat 5.1.0 へのアップグレード

![ui.language 設定再構築フロー](../assets/config-language-migration.svg)

[Animated GIF](../assets/config-language-migration.gif) · [PNG](../assets/config-language-migration.png) · [SVG](../assets/config-language-migration.svg)

> **重要:** `ui.language` が変更するのは config/reference/migration の表示言語です。解析済みの運用設定値はそのまま保持されます。

KOKOTO WebChat 5.1.0 は **5.0.0** 系からのアップグレードです。リリースノートは **5.0.0** リリースから 5.1.0 までの最終変更内容を基準に記載します。

### アップグレード前

1. `config.yml`、公開/非公開チャットの DB または JSONL、アップロード、絵文字アセット、監査データを含む KWC データディレクトリ全体をバックアップします。
2. 複数サーバーを Relay で接続している場合は、関係するサーバーをまとめてアップグレードし、Relay v2 用に再設定します。Relay Protocol v1 と 5.1.0 Relay v2 は相互運用できません。
3. Caddy/Nginx の背後で運用している場合は、アップグレード後にクライアント IP 解決を確認できるよう、現在の公開 URL とプロキシ構成を記録しておきます。

### 設定移行と言語

5.1.0 は既存の**解析済み設定値を保持**し、現在のテンプレートから表示形式だけを再構築します。実効 `ui.language` に応じて、再構築される `config.yml`、生成される `config-reference-5.1.0.yml`、migration/Difference の説明文に使用する言語が選択されます。組み込み表示言語は `en-US`、`ko-KR`、`ja-JP`、`zh-CN` です。

Difference はセマンティック比較です。解析済み YAML の設定パスと値を比較し、コメント、空行、インデント、引用形式、行番号、キー順は比較しません。そのため、表示言語だけを変更して設定差分が発生することはありません。

初回起動後は `config.yml` と `config-reference-5.1.0.yml` の両方を確認してください。5.1.0 で作成済みの Relay グループを含む運用設定値は、同一バージョンの自動移行で表示形式を再構築しても保持されます。

### Relay Protocol v2 は手動で信頼関係を再設定

5.1.0 より前の設定を初めて移行するとき、KWC は旧 Relay v1 のフラットな信頼関係から新しいグループ構成を推測しません。旧グローバル共有シークレット、フラットな peer、forwarding 設定を廃止し、`server-relay.enabled` を `false` にリセットします。

`server-relay.groups` を明示的に作成します。新しい group は 1 台のサーバーで `shared-secret: ""` のまま起動/リロードして安全な random 値を生成・保存し、その値を同じ group の他サーバーへそのままコピーしてください。既存の空でない secret は保持され、32 文字未満の手動値は invalid のままです。同じ group 内では双方のサーバーが互いを peer として登録する必要があります。peer の設定項目は `id`、`url`、`enabled` のみで、peer 固有のシークレットはありません。意図した peer の相互設定がすべて完了してから `server-relay.enabled` を再度有効にしてください。

ローリングアップグレードの前に、まだ 5.0.0 のサーバーでは `server-relay.enabled: false` にして reload してください。5.0.0 Relay を有効のまま相手だけ 5.1.0 にすると、旧 v1 要求を再試行し続け、正常な HTTP 426 応答が WARN として繰り返し蓄積する場合があります。すべてのサーバーを 5.1.0 に更新し v2 group/peer を構成してから Relay を再度有効にします。

Relay v1/BMWC の endpoint は HTTP 426 を返します。直接 1-hop の HTTP peer でも Relay v2 の payload 自体は暗号化・認証されますが、警告対象です。forwarding は同一グループ内の **HTTPS→HTTPS** に限られます。Relay v2 は E2EE ではなく、**ホップ単位の認証付き暗号化（hop-by-hop authenticated encryption）**です。

### 非公開 Reply の保存形式

5.1.0 は DM とグループメッセージに永続的な返信メタデータを追加します。既存の非公開メッセージはそのまま有効です。SQLite のグループストレージはスキーマ更新時に必要な任意列を追加し、既存の DM/JSONL レコードは返信メタデータが存在しない場合にその項目を省略する後方互換形式を使用します。手動で DB を変換する必要はありません。

5.1.0 ではルーム単位の group membership event state も追加します。既存の `group-messages.db` には `group_rooms.membership_events_enabled`（既定 ON）と `group_messages.event_type`（既定は通常 message）を自動追加するため、手動 DB 変換は不要です。グループチャット UI を閉じても退出 event は生成しません。

サーバー間 DM Reply では、相手サーバーのローカル DB 行 ID ではなく、安定した Relay メッセージ ID を使用します。非公開メッセージの数値 ID をサーバー間でコピーしたり、同じ値に揃えたりしないでください。

### 絵文字と SSE の変更

SSE の既定上限は **解決済みクライアント IP ごとに 10 接続**、**サーバー全体で 500 接続**です。各値の `0` は該当する制限を無効化します。リバースプロキシ配下では `http.trusted-proxies` を確認してください。誤設定すると複数クライアントが同じプロキシ IP として認識され、IP ごとの SSE 制限を共有する場合があります。

絵文字カタログの再読み込みも復旧性が向上しました。一時的に `/emojis` の取得に失敗しても直前の正常なカタログを保持し、上限付き指数バックオフで再試行します。SSE 再接続後は強制再同期し、サーバー側でカタログが変更されると `emoji-catalog` イベントで他のブラウザーにも更新を通知します。`emoji.message-token-limit: 0` は引き続き無制限です。

5.1.0 ではカスタム絵文字のパック名/項目名も正規化します。未対応文字と空白を除去し、同じパック内で名前が衝突した場合は数値の接尾番号を付けます。実ファイル名やトークン文字列を固定値として扱う外部連携がある場合は確認してください。

- Android の管理者絵文字アップロードは、画像専用 Photo Picker ではなく一般のシステムファイル/DocumentsUI 選択画面を使用します。デスクトップの画像フィルターとサーバー側の画像検証は変更しません。

### その他の確認項目

- DM/グループ Reply は元メッセージとの関係を保存し、送信時に現在の DM スレッド参加者またはグループ所属をサーバー側で再検証します。
- ゲーム内 `/kchat group` は、空白を含むルーム名、引用符付きの名前、連続する空白を正しく解決します。
- ルームごとのメンバー通知は、実際の参加/招待承認と退出/kick/ban に対してだけ発生し、ウィンドウを閉じたり非表示にしたりしても退出扱いにはなりません。
- チャット設定プロファイルは明示値と未設定状態を正確に保持し、読み込んだフォントサイズは既に開いている DM/グループ画面にも反映されます。
- 狭い画面/モバイルでは、DM/グループの時刻・Reply・既読状態が大きな固定列を確保せず自然に折り返されます。
- `/kchat reload` は外部公開 HTTP listener と direct HTTP Relay peer を再確認し、該当するローカライズ警告をサーバーログだけでなくコマンド実行者にも表示します。
- `commands.broadcast-result-to-web-chat: true` の場合、Web コマンド実行通知はオンラインの Minecraft プレイヤーにも送信されます.
- 公開チャット、DM、グループのメッセージ入力欄だけを通常の text `<input>` から 1 行の `<textarea>` に変更し、`autocomplete="off"`、Enter 送信、カーソル位置と絵文字挿入の動作は維持します。Android 版 Chrome が無関係な通常入力欄にもパスワード・住所・支払い方法の Autofill accessory を表示する経路を回避するための変更で、ログイン/パスワード欄は変更しません。
- プロジェクト URL 移行中は updater が canonical Modrinth `kokoto-webchat` を先に確認し、`bluemapwebchat` へ fallback します。BMWC は移行完了まで実際の update source として維持し、両方の取得に失敗した場合だけ警告します。
- 外部公開された平文 HTTP listener と直接 HTTP Relay peer には、ローカライズされたセキュリティ警告を表示します。

### アップグレード後の確認

1. `config-version` と生成された 5.1.0 の reference/migration ファイルを確認します。
2. `ui.language` を変更しても表示言語だけが変わり、運用設定値が保持されることを確認します。
3. Relay を使用する場合は v2 の相互 peer 設定を確認してから再度有効にし、他サーバーが旧 v1 endpoint を期待していないことを確認します。
4. プロキシ配下では解決済みクライアント IP と SSE の動作を確認し、トラブルシューティング時だけ `http.log-client-ip-resolution` を一時的に有効化します。
5. 実際の運用環境で、公開チャット、DM/グループ Reply、カスタム絵文字、アップロード、Web Push/通知、使用中のマップ/standalone frontend をテストします。
6. 通常運用と保持期間クリーンアップを確認するまで、アップグレード前のバックアップを保持します。
