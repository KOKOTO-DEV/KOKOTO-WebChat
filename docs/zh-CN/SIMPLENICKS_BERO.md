# SimpleNicks-Bero 集成

KOKOTO WebChat 5.2.0 在 **Bukkit/Paper 系列**服务器上可以使用 [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) 写入 Bukkit player display name 的昵称。KWC 不把 SimpleNicks 作为硬依赖，也不会直接读取该插件的数据库。

常规安装、`/nick` 命令、权限、SQLite/MySQL、昵称保存、昵称保护、PlaceholderAPI/MiniPlaceholders 等一般操作请参考 [上游 SimpleNicks 文档](https://github.com/Simplexity-Development/SimpleNicks)。本文只说明与 KWC 有关的配置。

## KWC 设置

让 KWC 使用 Bukkit display name 作为玩家的可见名称：

```yaml
player-display:
  mode: "display-name"
```

`name` 始终使用真实 Minecraft 用户名；`display-name` 使用 Bukkit 当前 display name，也就是 SimpleNicks-Bero 应用渲染后昵称的位置。KWC 会独立保留 linked account/UUID identity，因此修改可见昵称不会改变认证后的玩家身份。

## SimpleNicks-Bero 代表性配置示例

以下值来自多语言 KWC 部署示例，**不是 KWC 的必需配置**：

```yaml
mysql:
  enabled: false
  ip: localhost:3306
  name: simplenicks
  username: username1
  password: badpassword!

max-nickname-length: 30
nickname-regex: '[A-Za-z0-9_가-힣 ぁ-ゔァ-ヴー々〆〤一-龥?!]+'

require-permission:
  nick: false
  color: false
  format: false
  who: false

tablist-nick: true
nickname-prefix: ''
```

上面的 Unicode `nickname-regex` 允许 Latin、韩文、日文、CJK 字符、空格以及列出的标点。请根据自己的 moderation/compatibility policy 调整。`tablist-nick`、MySQL、保护规则以及保存上限都属于 SimpleNicks 自身设置，不控制 KWC。

## 颜色 / 格式

SimpleNicks-Bero 会把 MiniMessage 昵称渲染到 Bukkit display name。KWC 对 Minecraft 名称颜色/格式代码的处理仍由以下设置控制：

```yaml
player-display:
  mode: "display-name"
  strip-colors: true
```

请根据是否希望 KWC Web UI 保留受支持的玩家名称格式来设置 `strip-colors`。认证、访客名称防冒充、relay identity 与真实账号匹配仍以底层玩家身份为准，不会把格式化昵称当作可信身份。

## 适用范围

本文所述集成依赖 Bukkit player display name，因此适用于 Bukkit/Paper 系列 KWC。Fabric/NeoForge/Forge 的 KWC 构建使用各自的平台玩家抽象，并不声称支持 Bukkit SimpleNicks plugin API。

## 项目链接

- KWC 使用的 fork：[SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)
- 原始插件 / 一般操作：[SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)

## Upstream 参考文档

- [SimpleNicks upstream source](https://github.com/Simplexity-Development/SimpleNicks)
- [SimpleNicks upstream on Modrinth](https://modrinth.com/plugin/simplenicks)

以上链接说明的是 upstream 项目。这里描述的 KWC 集成与 SimpleNicks-Bero fork 行为具有版本特性，不能视为 upstream 文档所保证的功能。
