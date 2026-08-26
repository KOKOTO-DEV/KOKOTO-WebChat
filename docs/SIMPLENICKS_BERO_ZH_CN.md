# SimpleNicks-Bero 集成

KOKOTO WebChat 5.0.0 在 **Bukkit/Paper 系列**可使用 [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) 写入 Bukkit player display name 的昵称。KWC 不直接读取 SimpleNicks 数据库，也不把它作为硬依赖。

常规安装、`/nick` 命令、权限、SQLite/MySQL、昵称保存与保护等请参考 [上游 SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)。

## KWC 设置

```yaml
player-display:
  mode: "display-name"
```

`name` 使用真实 Minecraft 用户名，`display-name` 使用 Bukkit 当前 display name。KWC 的 linked account/UUID identity 独立保存，因此修改可见昵称不会改变认证身份。

## SimpleNicks-Bero 示例

```yaml
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

以上只是运维示例，并非 KWC 必需配置。MySQL、昵称保护、保存数量和 tablist 属于 SimpleNicks 自身功能。

MiniMessage 昵称与 KWC 的 `player-display.strip-colors` 策略配合使用。认证、guest 防冒充和 relay identity 仍以真实玩家身份为准。

## 项目链接

- KWC 使用的 fork: [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero)
- 上游: [SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)
