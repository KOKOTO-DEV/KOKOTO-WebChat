# ImageEmojis-Bero 集成 (1.9.x)

KOKOTO WebChat 5.3.0 可选集成 [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)。服务器端 runtime glyph 集成面向 **Bukkit/Paper 系列**，当前按 1.9.x Bero 系列（包括 1.9.2）验证。该兼容层基于 reflection，不构成硬依赖。

基础安装、命令、权限、资源包生成和常规运维请参考 [上游 ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)。本文只说明与 KWC 配合时需要注意的配置。

## 推荐配置

```yaml
# plugins/ImageEmojis-Bero/config.yml
serverIp: yourdomain
webServerPort: 5000
emojisFolder: /KOKOTO-WebChat/emojis
enforcementPolicy: REQUIRED
replaceInCommands: true
templateFormat: ':<emoji>:'
```

`replaceInAnvils`、`replaceOnSigns`、`replaceInCommandBlocks`、`replaceInBooks`、`suggestionMode`、`mergeWithServerResourcePack`、`extendedUnicodeRange` 等属于 ImageEmojis-Bero 自身运维选项，并非 KWC 必需项。

## 共用表情目录

以下设置：

```yaml
emojisFolder: /KOKOTO-WebChat/emojis
```

对应：

```text
plugins/KOKOTO-WebChat/emojis/<pack>/<name>.png
```

这样 KWC Web 表情与 ImageEmojis-Bero 生成的 Minecraft 资源包可以共用相同 pack/name 结构。

## 资源包 HTTP 端口

`serverIp` 与 `webServerPort` 属于 **ImageEmojis-Bero 的资源包 HTTP 服务**，不是 KWC Web 服务。例如：

```text
serverIp: yourdomain
webServerPort: 5000
```

在这种配置下，Minecraft 客户端必须能够通过 TCP 访问 `yourdomain:5000`。根据环境可能需要操作系统防火墙放行、路由器/NAT 端口转发及正确 DNS。公开 KWC `/chat` 并不会自动公开 ImageEmojis 的资源包端口。

## KWC 行为

- Web/history/relay 中保留 canonical token，而不是其他服务器的 private-use glyph。
- Bukkit/Paper 系列可在发送交互式游戏消息前读取 ImageEmojis-Bero runtime repository 并解析当前服务器 glyph。
- 支持 `:pack/name:` 与 `:emoji:pack/name:`。
- sender/reply/URL 点击与 emoji glyph 可共存于同一消息。
- runtime lookup 不可用时不会 hard failure，可退回 token/plain broadcast 处理。

这不表示 Fabric/NeoForge/Forge KWC 服务器构建支持 Bukkit ImageEmojis plugin API；client picker 是独立的客户端功能。

## 权限与命令

通常需要 `imageemojis.use`。若在 `/msg`、`/tell`、`/kchat reply`、`/kchat dm` 等命令中使用 token，请保持 `replaceInCommands: true`。

## 更新顺序

1. `/emojis reload`
2. 客户端执行 `/emojis update` 或重新连接
3. 等待 KWC 的短期 runtime cache 刷新

## 多服务器

Relay 只传输 token，不同步 PNG 或资源包。需要显示该表情的每台接收服务器都必须具有相同 pack/name 资源。

## 故障排除

- Web 正常但游戏显示 token：检查 PNG、权限、资源包是否接受、reload/update
- 游戏正常但 Web 显示 token：检查 `plugins/KOKOTO-WebChat/emojis` 下对应 pack/name
- 资源包下载失败：从客户端检查 `serverIp:webServerPort` TCP 可达性
- 命令中不转换：检查 `replaceInCommands: true`

## 项目链接

- KWC 验证的 fork: [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)
- 上游 / 一般安装运维: [ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)

## Upstream 参考文档

- [ImageEmojis upstream on Modrinth](https://modrinth.com/plugin/image-emojis)
- [ImageEmojis upstream source](https://github.com/MrQuackDuck/ImageEmojis)

以上链接说明的是 upstream 项目。本文件中的 KWC token 转换、共享目录处理以及 Bero 专用集成行为，应以实际安装的 Bero/KWC 版本为准。
