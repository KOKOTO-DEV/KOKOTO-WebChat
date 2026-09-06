# KOKOTO WebChat インストールとトラブルシューティング

## 要件

- Bukkit/Spigot/Paper 互換サーバーまたは互換フォーク
- ビルド/実行に Java 17 以上
- BlueMap プラグインと動作中の BlueMap webroot
- ブラウザからチャット API ポートに到達できること。既定値: `8899/tcp`
- Discord ブリッジは任意で、DiscordSRV を有効にする場合のみ必要です。

## ビルド

```bash
mvn clean package
```

出力:

```text
kwc-platform-bukkit/target/KOKOTO-WebChat-5.2.0-Bukkit-1.18-26.2.jar
```

Windows では root validator を platform build helper としても使用できます。

```bat

> `validate-release-windows.bat` と必要な PowerShell helper は source archive に含まれています。別の `KWC-5.2.0-validation-tools.zip` には開発専用の browser regression tool のみが含まれ、release build の実行には不要です。

validate-release-windows.bat --bukkit
validate-release-windows.bat --bukkit --fast
validate-release-windows.bat --parallel
```

`--fast` は `clean` を省略して既存の build 出力/cache を再利用する反復開発用 mode です。`--parallel` は clean/fast の意味を変えません。Bukkit が選択されている場合は Bukkit を先に build し、PASS 後に残りの選択 loader を並列実行するため、`validate-release-windows.bat --parallel` は clean full release validation として使用できます。main console には全体/platform 別 target progress が live 表示され、`--parallel` では各 active platform が別の live build window を使用し、詳細 log は `validation-logs/` に残ります。

## インストールまたはアップグレード

1. Minecraft サーバーを停止します。
2. `plugins/` の古い KOKOTO WebChat jar を新しい jar に置き換えます。
3. サーバーを起動します。
4. `plugins/KOKOTO-WebChat/config.yml` を確認します。
5. 重要なパスを変更した場合は `/kchat reload` または再起動を行います。
6. `/kchat reload` は BlueMap webapp 変更後に `bluemap reload light` を自動要求します。自動実行に失敗した場合は `/bluemap reload light` を手動実行してください。
7. ブラウザをハードリフレッシュします。

## Web アドオン登録確認

```bash
grep -R "bluemap-web-chat" -n /opt/minecraft/server/plugins/BlueMap/webapp.conf
```

現在バージョンの query が含まれている必要があります。

```text
addons/kokoto-web-chat/config.js?v=5.2.0-<cache-token>
addons/kokoto-web-chat/chat.js?v=5.2.0-<cache-token>
addons/kokoto-web-chat/chat.css?v=5.2.0-<cache-token>
```

実際の Web ファイル更新も確認します。

```bash
find /opt/minecraft/server -path "*addons/kokoto-web-chat/chat.js" -printf "%p  %TY-%Tm-%Td %TH:%TM\n"
```

## BlueMap webroot 不一致

`/api/config` が動作しているのにチャットパネルが表示されない場合、BlueMap が別の webroot を配信している可能性があります。実際のパスと次の設定を一致させてください。

```yaml
adapters:
  bluemap:
    bluemap-web-root: ""
    bluemap-webapp-conf: ""
    addon-path: "addons/kokoto-web-chat"
```

空のパスは自動検出を使用します。自動検出先が実際の BlueMap インスタンスと異なる場合は絶対パスを指定してください。

## ブラウザキャッシュ

Web UI 変更をテストするときは DevTools を開き、**Network -> Disable cache** を有効にしてハードリフレッシュしてください。コンソールでも読み込みバージョンを確認できます。

```js
[...document.scripts]
  .filter(s => s.src.includes("bluemap-web-chat"))
  .map(s => s.src)
```

## BlueMap が古い addon バージョンを読み込み続ける場合

更新後も BlueMap が古い KOKOTO WebChat addon バージョンを読み込み続ける場合は、`/kchat reload` をもう一度実行するかサーバーを再起動し、その後ブラウザをハードリフレッシュしてください。

## HTTPS リバースプロキシ

公開サーバーでは Caddy または nginx 経由の HTTPS を推奨します。Caddy は `docs/ja/CADDY_HTTPS.md` と `examples/caddy/Caddyfile`、nginx は `docs/ja/NGINX_HTTPS.md` と `examples/nginx/kokoto-webchat.conf` を参照してください。
