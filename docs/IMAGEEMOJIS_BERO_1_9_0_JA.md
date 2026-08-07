# ImageEmojis-Bero 1.9.0 互換性

BlueMapWebChat 4.6.1 には [ImageEmojis-Bero 1.9.0](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) 向けの任意互換経路があります。reflection ベースで hard dependency は追加されず、ImageEmojis-Bero が未導入でも BlueMapWebChat は起動できます。

## 対応動作

- Web、ゲーム返信、DM、サーバーリレーでは、BMChat の履歴と relay payload に正規の絵文字 token を保持します。
- クリック可能な Minecraft component を作る前に、受信サーバーの ImageEmojis-Bero runtime repository を読み、既知 token をそのサーバーの現在の resource-pack glyph に変換します。
- sender click、`/bmchat reply`、URL click、ImageEmojis glyph を同じ行で併用できます。
- `:pack/name:` と従来の `:emoji:pack/name:` を認識します。通常の `templateFormat: ":<emoji>:"` では emoji 名に pack path が含まれるため `:pack/name:` になります。
- runtime repository で解決できない既知 token は、ImageEmojis-Bero の `BroadcastMessageEvent` listener が処理できる plain Bukkit broadcast fallback を使います。この fallback 行には BMChat の click/hover metadata を付けられません。

## 共通絵文字フォルダーの推奨設定

Web UI と Minecraft resource pack で同じ素材を使う場合、ImageEmojis-Bero を BlueMapWebChat の絵文字フォルダーへ向けます。

```yaml
# plugins/ImageEmojis-Bero/config.yml
emojisFolder: "/BlueMapWebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

実際の位置:

```text
plugins/BlueMapWebChat/emojis/<pack>/<name>.png
```

ImageEmojis-Bero は 1 段の pack folder と PNG を読みます。BlueMapWebChat は Web 用の GIF/JPG/JPEG/WEBP 原本を保持しつつ、ゲーム用 PNG sidecar を同じフォルダーに生成できます。

## BlueMapWebChat 推奨設定

```yaml
emoji:
  game-link:
    enabled: false
    default-pack: ""
    aliases: {}

reply:
  game-click:
    enabled: true
    local-game-chat: true
```

`emoji.game-link.enabled: false` は Minecraft 側へ画像 link を追加せず、token を正規形式のまま保持します。`:wave:` のような flat token を `default/wave` へ対応付ける場合のみ `default-pack` または `aliases` を使います。

## 権限と command 変換

プレイヤーには `imageemojis.use` が必要です。`/bmchat reply`、`/bmchat dm`、`/w`、`/msg` などで token を使う場合は `replaceInCommands: true` を維持してください。BMChat は Web/履歴/relay に原 token を残し、送信元ゲーム表示には ImageEmojis-Bero が処理した command 本文を使います。

## 再読み込み手順

1. `/emojis reload` で resource pack を再生成します。
2. オンラインプレイヤーは `/emojis update` を実行するか再接続します。
3. BMChat の runtime token→glyph cache は約 5 秒以内に更新されます。絵文字ファイルだけの変更では通常 `/bmchat reload` は不要です。

## サーバー間リレー

BMChat は他サーバーの private-use glyph ではなく正規 token を送ります。表示する全受信サーバーに ImageEmojis-Bero 1.9.0 と同じ pack/name の PNG が必要です。各サーバーは自分の runtime mapping で glyph を解決します。

## DiscordSRV

ImageEmojis-Bero の Discord emoji 変換と、BMChat の `append-web-emoji-links` / `append-game-emoji-links` は目的が重なる場合があります。同じ preview を二重に付けないよう、必要な方式のみ有効にしてください。共有 Discord channel では origin server のみが DiscordSRV message を補強し、relay peer は再送・再加工しません。

## トラブルシューティング

- token が Minecraft に残る: 同じ PNG、`/emojis reload`、`imageemojis.use`、短い cache 更新時間を確認します。
- Web では表示されゲームでは表示されない: resource pack の受諾・更新を確認します。
- ゲームでは表示され Web では token のまま: `plugins/BlueMapWebChat/emojis` の同じ pack/name を確認します。
- `/bmchat reply` / `/bmchat dm` で変換されない: `replaceInCommands: true` を確認します。
- remote emoji が表示されない: 全受信サーバーへ同じ PNG と pack/name を同期します。relay は resource-pack file をコピーしません。

## 互換境界

4.6.0 は ImageEmojis-Bero 1.9.0 の `getEmojiRepository().getEmojis()` と emoji model の `getName()`, `getTemplate()`, `getAsUtf8Symbol()` を前提にします。将来 runtime API が変更された場合も BMChat 自体は hard failure せず、互換更新まで token/plain-broadcast fallback へ移行します。
