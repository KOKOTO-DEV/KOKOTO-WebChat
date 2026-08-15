# BlueMapWebChat リリースチェックリスト

公開前の確認事項:

- `pom.xml` のバージョンを更新します。
- `src/main/resources/plugin.yml` のバージョンを更新します。
- `src/main/resources/config.yml` のバージョンコメントを更新します。
- README とドキュメントのビルド出力例を更新します。
- CHANGELOG に項目を追加します。
- JavaScript 構文チェックを実行します。

```bash
node --check inner.js
node --check src/main/resources/web/chat.js
```

- YAML ファイルを検証します。

```bash
python3 - <<'PY'
import yaml, glob
for path in ['src/main/resources/config.yml'] + glob.glob('src/main/resources/lang/*.yml'):
    with open(path, encoding='utf-8') as f:
        yaml.safe_load(f)
    print('OK', path)
PY
```

- `en-US.yml` を基準に言語キー数が一致するか確認します。
- Maven でビルドします。

```bash
mvn clean package
```

- `webapp.conf` が新しいバージョン query を指しているか確認します。
- DevTools でキャッシュを無効にしてブラウザ読み込みをテストします。

- [ ] 4 言語の `USER_MANUAL_*.md` が同じ主要構成を持ち、現在の command・permission・default・機能動作を反映している。

## 4.6.2 配信状態 release check

- [ ] `pom.xml`, `plugin.yml`, bundled `config-version`, artifact example, current manual が `4.6.2`。
- [ ] review 済み 4.6.1 config に他の差分がなければ `config-migration-4.6.2.yml` は `config-version: "4.6.2"` だけを出力する。
- [ ] 同名の local / remote player が存在しても、server 指定なし DM は current server の player だけを選択する。
- [ ] remote DM は `pending` から始まり、destination store の acknowledgement 後だけ `delivered`、HTTP 502 / timeout / route / destination failure は `failed` になる。
- [ ] retry は同じ relay ID を再利用し、receiver message を重複保存しない。
- [ ] pending 中の restart は retryable `failed` として復旧する。
- [ ] Web DM / group chat の不確実な HTTP 応答は同じ client message ID で安全に retry できる。
- [ ] hub / chain private relay は final destination の保存確認後だけ success を返す。
- [ ] DM と group chat の全 message に既読状態を表示する。1 対 1 DM は相手が読む前に `未読`、読んだ後に `✓` を表示し、group chat は未読受信者数を数字で表示して 0 人になると `✓` になる。DM / group chat の全 message を既読状態計算に含める。

## 4.6.0 relay / DM / ゲーム返信チェック

- [ ] 既存 config を上書きせず upgrade guide を生成する。
- [ ] `activePeers`、HTTPS/HMAC、unknown peer、時刻差、reload を確認する。
- [ ] 現在のサーバーバッジは非表示、別サーバーバッジとゲーム/Discord 発信元表示は有効であることを確認する。
- [ ] 複数サーバーが同じ Discord チャンネルを共有しても、DiscordSRV のゲームメッセージには発信元サーバー名と絵文字リンクが一度だけ付く。
- [ ] 同じサーバーのゲーム送信者は `/w`、Web/別サーバー送信者は `/bmchat dm`、本文は `/bmchat reply`、URL はリンク優先を確認する。
- [ ] whisper DM 複製と SQLite migration を確認する。
- [ ] 4 言語の key set が一致する。
- [ ] ImageEmojis-Bero 1.9.0: 共有フォルダー PNG、通常チャット、`/bmchat reply`、`/bmchat dm`、URL+絵文字 click、remote relay 表示を確認する。

- [ ] `config-version` がない、または異なる場合、他の差分がなくても実 `config.yml` を上書きせず、`config-version` を含む `config-migration-4.6.0.yml` を生成する。
- [ ] version が一致する場合は比較を省略し、同 version の古い report を削除する。
- [ ] fragment が missing key と changed default だけを実 YAML 設定として出力し、custom 値と情報用 section を出力しない。

## 4.6.1 remote DM search / 管理者 audit check

- [ ] `pom.xml`, `plugin.yml`, bundled `config-version`, artifact example, cache document が `4.6.1`。
- [ ] `config-version: "4.6.0"` config では、他の不足がなければ `config-migration-4.6.1.yml` に `direct-message.admin-audit.enabled: false` と `config-version: "4.6.1"` だけが出る。
- [ ] player UUID を持つ remote game/linked-web sender を既存 DM search で display name、real name、UUID から検索でき、UUID のない guest/Discord sender は除外される。
- [ ] restart 後に retained public history から remote identity が復元される。
- [ ] 通常 ADMIN/MODERATOR account は他 user の DM body を閲覧できない。
- [ ] `private-chat-super-admins` 登録済みでも audit switch が false なら metadata only。
- [ ] 両 gate 有効時だけ read-only audit view が開き、send/hide/mark-read control はなく global hidden message は除外される。
- [ ] page read ごとに `admin.dm-audit-read` が audit log に追加され、body 自体は log にコピーされない。

## 4.7.0 multi-upload / compatibility checks

- [ ] 元の config version に関係なく `config-reference-4.7.0.yml` が bundled `config.yml` と byte-for-byte 同一で、コメントも含めて生成される。
- [ ] startup/reload で既知の最上位 `config.yml` block が 4.7.0 default 順へ並べ替えられるが、YAML 値と block comment は変わらず、default にない最上位 block は最後に元の順序で残る。
- [ ] bundled `config.yml` の message-token 標準 alias/example は英語のみで、各言語 alias は管理者が設定で追加する方式として文書化されている。
- [ ] `en-US.yml`, `ko-KR.yml`, `ja-JP.yml`, `zh-CN.yml` の key set が一致し、空/null translation がない。
- [ ] 生成された 4.7.0 migration report 末尾に current/reference の text diff が comment として出力され、同一行を除外し、file 名と `Line`/`Lines` を実際の差分内容とは別行に表示し、reference-only block は挿入位置も表示する。
- [ ] `pom.xml` と `plugin.yml` が 4.7.0。
- [ ] `plugin.yml` は `api-version: '1.18'`、POM は Java 17 + Spigot API 1.18.2。
- [ ] 管理者 emoji の Upload ボタンが通常 upload と同じ非表示 multiple-file picker を開き、選択直後に追加確認なしで順次 upload を開始し、各選択ファイルを正確に1回だけ送信する。
- [ ] 途中のファイルが失敗しても残りの upload が継続する。
- [ ] game-link sidecar 有効時に GIF/JPG/WEBP の PNG sidecar 生成が維持される。
- [ ] review 済み 4.6.3 config は新しい `message-tokens` 設定（`custom: {}` を含む）と `config-version: "4.7.0"` を含む migration を生成し、さらに古い/marker のない config も現在の 4.7.0 全 key と比較される。

## 4.6.3 管理者 group-chat audit checks

- [ ] 以前の bundled comment をそのまま使う config では、その comment block だけが 4.6.3 の説明へ更新され、YAML 設定値や user-custom comment は変更されない。
- [ ] `config-version: "4.6.2"` config は他の不足がなければ `group-chat.admin-audit.enabled: false` と `config-version: "4.6.3"` を含む `config-migration-4.6.3.yml` を生成する。
- [ ] `private-chat-super-admins` 登録済みでも `group-chat.admin-audit.enabled: false` なら group は metadata-only。
- [ ] 両方を有効化すると、登録 super administrator は room member でなくても body を read-only で開け、read/unread state は変化しない。
- [ ] audit page read ごとに actor/room/pagination/count を含む `admin.group-audit-read` が記録され、body は audit log にコピーされない。

