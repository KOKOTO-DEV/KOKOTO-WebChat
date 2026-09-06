# ImageEmojis-Bero 連携 (1.9.x)

KOKOTO WebChat 5.2.0 は [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) と任意で連携できます。サーバー側 runtime glyph 連携は **Bukkit/Paper 系**が対象で、現在の 1.9.x Bero 系列（1.9.2 を含む）を基準に確認しています。reflection ベースのため hard dependency はありません。

基本的な導入、command、permission、resource pack 生成、一般運用は [upstream ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis) を参照してください。この文書は KWC と併用するときに必要な差分だけを説明します。

## 推奨設定

```yaml
# plugins/ImageEmojis-Bero/config.yml
serverIp: yourdomain
webServerPort: 5000
emojisFolder: /KOKOTO-WebChat/emojis
enforcementPolicy: REQUIRED
replaceInCommands: true
templateFormat: ':<emoji>:'
```

`replaceInAnvils`, `replaceOnSigns`, `replaceInCommandBlocks`, `replaceInBooks`, `suggestionMode`, `mergeWithServerResourcePack`, `extendedUnicodeRange` などは ImageEmojis-Bero 自体の運用設定であり、KWC の必須条件ではありません。

## 共通 emoji directory

この設定:

```yaml
emojisFolder: /KOKOTO-WebChat/emojis
```

は次の場所を参照します。

```text
plugins/KOKOTO-WebChat/emojis/<pack>/<name>.png
```

これにより KWC の Web emoji と ImageEmojis-Bero の Minecraft resource pack で同じ pack/name 構成を利用できます。

## Resource-pack HTTP port

`serverIp` と `webServerPort` は **KWC の Web server ではなく ImageEmojis-Bero の resource-pack HTTP server** の設定です。例えば:

```text
serverIp: yourdomain
webServerPort: 5000
```

この場合、Minecraft client から `yourdomain:5000` へ TCP 接続できる必要があります。必要に応じて OS firewall、router/NAT port forwarding、DNS を設定してください。KWC の `/chat` を公開しても ImageEmojis の port は自動では公開されません。

## KWC 側の動作

- Web/history/relay では canonical token を保持します。
- Bukkit/Paper 系では送信前に ImageEmojis-Bero の runtime repository から現在の glyph を解決できます。
- `:pack/name:` と `:emoji:pack/name:` を認識します。
- sender/reply/URL click と emoji glyph を同じ message で併用できます。
- runtime lookup に失敗しても hard failure せず token/plain broadcast fallback を利用できます。

Fabric/NeoForge/Forge の KWC server build が Bukkit ImageEmojis plugin API を提供するという意味ではありません。client picker は別の client-side 機能です。

## Permission / command

通常は `imageemojis.use` が必要です。`/msg`, `/tell`, `/kchat reply`, `/kchat dm` などで token を使う場合は `replaceInCommands: true` を維持してください。

## 更新手順

1. `/emojis reload`
2. client で `/emojis update` または再接続
3. KWC の短い runtime cache 更新を待つ

## Multi-server

Relay は token を送るだけで PNG/resource pack を同期しません。表示が必要な各受信 server に同じ pack/name の asset を用意してください。

## Troubleshooting

- Web のみ表示される: PNG、permission、resource pack、reload/update を確認
- Game のみ表示される: `plugins/KOKOTO-WebChat/emojis` の pack/name を確認
- Resource pack を取得できない: client から `serverIp:webServerPort` への TCP 到達性を確認
- command 内で変換されない: `replaceInCommands: true` を確認

## Project links

- KWC-tested fork: [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)
- Upstream / general operation: [ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)

## Upstream 参照資料

- [ImageEmojis upstream on Modrinth](https://modrinth.com/plugin/image-emojis)
- [ImageEmojis upstream source](https://github.com/MrQuackDuck/ImageEmojis)

これらは upstream project の資料です。この文書に記載する KWC の token 変換、共有 directory の扱い、Bero 固有の連携動作は、実際に使用する Bero/KWC version を基準に確認してください。
