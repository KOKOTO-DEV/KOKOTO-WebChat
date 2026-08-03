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
