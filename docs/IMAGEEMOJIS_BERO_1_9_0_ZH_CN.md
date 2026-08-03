# ImageEmojis-Bero 1.9.0 兼容性

BlueMapWebChat 4.6.0 提供了对 [ImageEmojis-Bero 1.9.0](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) 的可选兼容路径。该集成使用 reflection，不增加硬依赖；未安装 ImageEmojis-Bero 时 BlueMapWebChat 仍可启动。

## 支持行为

- Web、游戏回复、DM 和服务器中继会在 BMChat 历史及 relay payload 中保留规范表情 token。
- 在构建可点击 Minecraft component 前，BMChat 会读取接收服务器的 ImageEmojis-Bero runtime repository，并将已知 token 转换为该服务器当前资源包的 glyph。
- 发送者点击、`/bmchat reply`、URL 点击和 ImageEmojis glyph 可以同时存在于同一聊天行。
- 支持 `:pack/name:` 与旧式 `:emoji:pack/name:`。正常的 `templateFormat: ":<emoji>:"` 会因为表情名包含 pack path 而生成 `:pack/name:`。
- runtime repository 无法解析的已知 token 会使用 plain Bukkit broadcast fallback，让 ImageEmojis-Bero 的 `BroadcastMessageEvent` listener 继续处理；该 fallback 行无法携带 BMChat 的 click/hover metadata。

## 推荐共用表情目录

让 Web UI 与 Minecraft 资源包使用同一份素材：

```yaml
# plugins/ImageEmojis-Bero/config.yml
emojisFolder: "/BlueMapWebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

实际目录：

```text
plugins/BlueMapWebChat/emojis/<pack>/<name>.png
```

ImageEmojis-Bero 读取一层 pack 文件夹和 PNG 文件。BlueMapWebChat 可保留 Web 使用的 GIF/JPG/JPEG/WEBP 原文件，并在同一目录生成游戏插件使用的 PNG sidecar。

## BlueMapWebChat 推荐设置

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

`emoji.game-link.enabled: false` 会保留规范 token，而不是在 Minecraft 中追加 BMChat 图片链接。只有需要将 `:wave:` 之类的 flat token 映射到 `default/wave` 时才使用 `default-pack` 或 `aliases`。

## 权限与命令转换

玩家需要 `imageemojis.use` 权限。若要在 `/bmchat reply`、`/bmchat dm`、`/w`、`/msg` 等命令中输入表情 token，请保持 `replaceInCommands: true`。BMChat 会在 Web/历史/relay 中保留原 token，并在发送服务器的即时游戏输出中使用 ImageEmojis-Bero 已处理的命令正文。

## 重新加载顺序

1. 执行 `/emojis reload` 重新生成资源包。
2. 在线玩家执行 `/emojis update`，或重新连接。
3. BMChat runtime token→glyph 缓存约在 5 秒内刷新。仅修改表情文件时通常不需要 `/bmchat reload`。

## 多服务器中继

BMChat 传输规范 token，而不是其他服务器的 private-use glyph。所有需要显示表情的接收服务器都必须安装 ImageEmojis-Bero 1.9.0，并具有相同 pack/name 的 PNG。每台服务器会使用自己的 runtime mapping 解析 glyph。

## DiscordSRV

ImageEmojis-Bero 的 Discord 表情转换与 BMChat 的 `append-web-emoji-links` / `append-game-emoji-links` 可能产生重叠。请只启用需要的显示方式，避免重复预览。多个服务器共用一个 Discord 频道时，只有原始服务器会增强 DiscordSRV 消息；relay peer 不会重新发送或再次添加服务器名/表情链接。

## 故障排除

- Minecraft 中仍显示 token：确认相同 PNG、执行 `/emojis reload`、检查 `imageemojis.use`，并等待短暂缓存刷新。
- Web 能显示但游戏不能：确认玩家接受并更新了 ImageEmojis-Bero 资源包。
- 游戏能显示但 Web 仍为 token：确认 `plugins/BlueMapWebChat/emojis` 下存在相同 pack/name。
- `/bmchat reply` / `/bmchat dm` 不转换：保持 `replaceInCommands: true`。
- 远程服务器表情不显示：在所有接收服务器同步相同 PNG 与 pack/name；聊天中继不会复制资源包文件。

## 兼容边界

4.6.0 依赖 ImageEmojis-Bero 1.9.0 的 `getEmojiRepository().getEmojis()` runtime repository，以及表情模型的 `getName()`、`getTemplate()`、`getAsUtf8Symbol()`。未来版本若改变这些 runtime API，BMChat 不会因此 hard failure，但在兼容代码更新前可能退回 token/plain-broadcast 处理。
