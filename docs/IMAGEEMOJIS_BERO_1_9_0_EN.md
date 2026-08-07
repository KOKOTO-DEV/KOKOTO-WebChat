# ImageEmojis-Bero 1.9.0 compatibility

BlueMapWebChat 4.6.1 includes an optional compatibility path for [ImageEmojis-Bero 1.9.0](https://github.com/KOKOTO-DEV/ImageEmojis-Bero). The integration is reflection-based and does not add a hard dependency, so BlueMapWebChat still starts when ImageEmojis-Bero is not installed.

## Supported behavior

- Web, game-reply, DM, and server-relay messages preserve canonical emoji tokens in BMChat history and relay payloads.
- Before BMChat builds clickable Minecraft components, it reads the receiving server's active ImageEmojis-Bero emoji repository and converts known tokens to that server's current resource-pack glyph.
- Sender-name actions, `/bmchat reply`, clickable URLs, and rendered ImageEmojis glyphs can coexist in one Minecraft chat line.
- Both `:pack/name:` and legacy `:emoji:pack/name:` BMChat forms are accepted. ImageEmojis-Bero's normal `templateFormat: ":<emoji>:"` produces `:pack/name:` because its emoji name includes the pack path.
- If a known token cannot be resolved through the runtime repository, BMChat uses its plain Bukkit broadcast fallback so ImageEmojis-Bero's `BroadcastMessageEvent` listener can still process the token. That fallback line cannot carry BMChat click or hover metadata.

## Recommended shared emoji directory

To use the same assets in the web UI and Minecraft resource pack, point ImageEmojis-Bero at BlueMapWebChat's emoji directory:

```yaml
# plugins/ImageEmojis-Bero/config.yml
emojisFolder: "/BlueMapWebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

With ImageEmojis-Bero 1.9.0's path handling, this resolves under the server `plugins` directory:

```text
plugins/BlueMapWebChat/emojis/<pack>/<name>.png
```

ImageEmojis-Bero loads one pack-directory level and PNG files. BlueMapWebChat can retain GIF/JPG/JPEG/WEBP originals for the web UI and creates same-folder PNG sidecars for the game plugin.

## BlueMapWebChat settings

Token preservation is recommended:

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

`emoji.game-link.enabled: false` keeps token text canonical instead of adding BMChat image links to Minecraft. Use `default-pack` or `aliases` only when a flat token such as `:wave:` must map to a packed BMChat ID such as `default/wave`.

## Permissions and command processing

Players need `imageemojis.use` to see and use ImageEmojis-Bero emojis. Keep `replaceInCommands: true` when players may enter emoji tokens in `/bmchat reply`, `/bmchat dm`, `/w`, `/msg`, or similar commands. BMChat retains the original token for web/history/relay while using ImageEmojis-Bero's processed command body for immediate game output.

## Reload sequence

After adding, replacing, or renaming emoji files:

1. Run `/emojis reload` to rebuild the ImageEmojis-Bero resource pack.
2. Have online players run `/emojis update`, or reconnect.
3. Allow up to about five seconds for BMChat's runtime token-to-glyph cache to refresh. A BMChat reload is not normally required for an emoji-file-only change.

## Multi-server relay

BMChat relays canonical token text, not another server's private-use glyph. Every receiving server that should render the emoji must run ImageEmojis-Bero 1.9.0 and contain the same pack/name entry. This avoids sending a glyph whose resource-pack mapping is different on another server.

If emoji sets intentionally differ, unresolved remote tokens remain text or use the plain broadcast fallback on the receiving server.

## DiscordSRV

ImageEmojis-Bero can translate matching templates to Discord emoji names, while BMChat can append public image-preview links with `discordsrv.append-web-emoji-links` and `discordsrv.append-game-emoji-links`. Choose the desired presentation and avoid enabling overlapping output solely to obtain the same preview twice.

When several Minecraft servers share one Discord channel, BMChat only lets the origin server enhance the native DiscordSRV game message. Relay peers do not re-send the public message to Discord and do not add another server label or emoji link.

## Troubleshooting

- **Token remains visible in Minecraft:** verify the same `<pack>/<name>.png` exists, run `/emojis reload`, confirm the player has `imageemojis.use`, and wait for the short BMChat runtime cache refresh.
- **Web emoji works but game emoji does not:** confirm the ImageEmojis-Bero resource pack was accepted and updated by the player.
- **Game emoji works but web shows a token:** confirm the corresponding file exists under `plugins/BlueMapWebChat/emojis` with the same pack/name.
- **Emoji in `/bmchat reply` or `/bmchat dm` does not convert:** keep `replaceInCommands: true`.
- **Remote-server emoji does not render:** synchronize the emoji PNG and pack/name on every receiving server; relay transport alone does not copy resource-pack files.
- **Clickable URL becomes a reply action:** use the current 4.6.1 source, where URL segments retain `OPEN_URL` precedence and only non-URL text receives the reply suggestion.

## Compatibility boundary

The 4.6.1 integration expects the ImageEmojis-Bero 1.9.0 runtime repository shape exposed by `getEmojiRepository().getEmojis()` and emoji model accessors such as `getName()`, `getTemplate()`, and `getAsUtf8Symbol()`. If a future ImageEmojis-Bero release changes those runtime methods, BMChat continues without a hard failure but may fall back to token/plain-broadcast handling until compatibility is updated.
