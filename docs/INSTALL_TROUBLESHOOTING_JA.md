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
kwc-platform-bukkit/target/KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar
```

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
addons/kokoto-web-chat/config.js?v=5.0.0-<cache-token>
addons/kokoto-web-chat/chat.js?v=5.0.0-<cache-token>
addons/kokoto-web-chat/chat.css?v=5.0.0-<cache-token>
```

実際の Web ファイル更新も確認します。

```bash
find /opt/minecraft/server -path "*addons/kokoto-web-chat/chat.js" -printf "%p  %TY-%Tm-%Td %TH:%TM\n"
```

## BlueMap webroot 不一致

`/api/config` は動くのにチャットパネルが表示されない場合、BlueMap が別の webroot を配信している可能性があります。`adapters.bluemap.bluemap-web-root`, `adapters.bluemap.bluemap-webapp-conf`, `adapters.bluemap.addon-path` を実際のパスに合わせてください。

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

公開サーバーでは Caddy または nginx 経由の HTTPS を推奨します。Caddy は `docs/CADDY_HTTPS_JA.md` と `examples/caddy/Caddyfile`、nginx は `docs/NGINX_HTTPS_JA.md` と `examples/nginx/kokoto-webchat.conf` を参照してください。
