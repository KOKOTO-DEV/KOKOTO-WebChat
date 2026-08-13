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
