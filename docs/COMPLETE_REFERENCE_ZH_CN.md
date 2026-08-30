# KOKOTO WebChat 5.1.0 完整用户与运维手册


## 可视化资料

| 范围 | 图示 |
| --- | --- |
| 架构 | [SVG](assets/architecture-5.1.0.svg) |
| Relay Protocol v2 | [Animated GIF](assets/relay-v2-flow.gif) · [SVG](assets/relay-v2-flow.svg) |
| DM/群组 Reply | [SVG](assets/private-reply-flow.svg) |
| 配置迁移 | [动态 GIF](assets/config-language-migration.gif) · [SVG](assets/config-language-migration.svg) |
| 部署模式 | [SVG](assets/deployment-modes.svg) |
| 上传安全 | [SVG](assets/upload-security-pipeline.svg) |
| Web Push | [SVG](assets/web-push-flow.svg) |

本文引用的一手标准与第三方官方文档统一列在 [REFERENCES_ZH_CN.md](REFERENCES_ZH_CN.md) 中。

> **5.1.0 运维：** Web Admin **Filter** 管理公开聊天/群聊/可选私信的 block/mask/replace 规则与不发送测试，**Settings** 只管理受支持的实时安全设置：访客/CAPTCHA、会话、用户资料、管理员提醒、上传与内容过滤。5 个 moderation 策略设置仅允许在 `config.yml` 中配置，不会暴露到 Web Admin。游戏侧使用 `/kchat filter` / `/kchat settings`。会话期限变更按创建时间重算现有目标会话，且不会复活已经过期的会话。`upload.filename-mode: original` 为新上传保留安全的 Unicode 原名并在重名时自动编号。


本文从普通用户和服务器管理员两个角度说明 KOKOTO WebChat 5.1.0 的全部功能。逐项配置说明请参阅 `CONFIGURATION_ZH_CN.md`，服务器中继请参阅 `SERVER_RELAY_ZH_CN.md`，HTTPS 部署请同时参阅 `CADDY_HTTPS_ZH_CN.md` 与 `NGINX_HTTPS_ZH_CN.md`。

## 1. 插件概述

KOKOTO WebChat 用于把 Minecraft 服务器聊天连接到浏览器。5.1.0 提供 Bukkit/Paper/Spigot，以及 Fabric 1.18.2～26.2、NeoForge 1.20.2～26.2、Forge 1.18.2～26.2 exact-target 构建。

支持：

- 嵌入 BlueMap 的聊天面板
- 不依赖 BlueMap 的 standalone 页面
- 嵌入模式与 standalone 同时使用
- 游戏与 Web 双向公开聊天
- 持久化私信会话与群聊房间
- DiscordSRV 集成
- 多个 Minecraft 服务器之间的公开聊天中继

默认 HTTP 端口为 `8899`，API 路径前缀为 `/api`，standalone 内部路径为 `/`，默认反向代理将其公开为 `/chat`。

## 2. 环境要求

必需：

- 支持平台：Bukkit/Paper/Spigot **1.18～26.2**、Fabric exact-target **1.18.2～26.2**、NeoForge exact-target **1.20.2～26.2**、Forge exact-target **1.18.2～26.2**
- 所选 Minecraft/server target 要求的 Java。Bukkit 构建以 Java 17 为目标。Fabric/NeoForge/Forge exact-target 构建脚本会按 Minecraft target 选择 JDK 17/21/25；26.x 使用 Java 25。
- Bukkit 系可向 `plugins/` 安装 JAR，Fabric/NeoForge/Forge 可向 `mods/` 安装平台 JAR 的权限

可选集成：

- BlueMap
- DiscordSRV
- ImageEmojis-Bero 1.9.x
- Caddy 或 Nginx

公网部署时不要直接公开 `8899`，建议绑定到 `127.0.0.1:8899`，再通过 HTTPS 反向代理发布。

## 3. 安装与首次启用

1. Bukkit/Paper/Spigot 将对应 JAR 放入 `plugins/`；Fabric/NeoForge/Forge 将对应平台 JAR 放入 `mods/`。
2. 启动服务器一次。
3. 确认 `<KWC data dir>/config.yml`。`<KWC data dir>` 在 Bukkit 系为 `plugins/KOKOTO-WebChat`，在 Fabric/NeoForge/Forge 为 `config/KOKOTO-WebChat`。
4. 新配置默认使用 `enabled: false`。
5. 检查 URL、存储方式、保留期限、认证和上传限制。
6. 配置需要的功能后设置 `enabled: true`。
7. 重启服务器或执行 `/kchat reload`。

```yaml
config-version: "5.1.0"
enabled: false
```

禁用时不会启动 Web 服务、聊天转发和清理任务，但管理员仍可使用 `/kchat reload`。

## 4. 配置迁移

现有配置值会被保留，但迁移处理不会继承旧配置文件的文本排版。KWC 以当前内置 `config.yml` 生成新模板，再覆盖现有管理员值；旧注释、顺序、空白和缩进会被丢弃，改用最新内置注释与布局。

当前版本的完整 reference 始终写入：

```text
<KWC data dir>/config-reference-5.1.0.yml
```

它是当前默认设置的管理员可读参考文件，展示语言与 `ui.language` 选择的内置语言（`en-US`、`ko-KR`、`ja-JP`、`zh-CN`）一致；不受支持或自定义的 UI 语言使用英文展示。reference 文件不会作为迁移输入。`/kchat reload` 会在替换正在运行的服务之前验证 YAML；无效 YAML 会保留此前正在运行的配置。

当 `config-version` 缺失或与运行版本不同时，KWC 会对实际 `config.yml` 执行一次迁移：

- 使用最新内置 `config.yml` 作为新文件模板。
- 把现有管理员设置值覆盖到该模板上。
- 不继承旧注释、顺序、空白和缩进。
- 如果旧版本标记不是 `*_auto_migration`，真正进行版本升级前会完整备份原 `config.yml`。
- 已存在设置的内置默认值若在新版本发生变化，不会静默覆盖，而是保留为审核项目。
- 实际文件会标记为 `config-version: "5.1.0_auto_migration"`。

随后生成：

```text
<KWC data dir>/config-migration-5.1.0.yml
```

它不再是让管理员复制缺失设置的 fragment，而是**审核报告**。其中记录自动插入数量、仍需管理员判断的默认值变化、最终确认用的精确版本标记，以及 current-vs-reference 的**设置值语义差异**。Difference 只比较已解析的 YAML path/value；注释、空行、缩进、引号格式、行位置和键顺序都会被忽略。每个 Difference 块只显示该设置的实际 YAML 值块，不重复复制说明注释，list/map 仍保持多行结构。由于缺失设置和注释已经放进真实 config 的正确位置，不会再作为大块 reference-only 内容堆在 diff 顶部。

判定规则：

| 真实 `config.yml` 状态 | 行为 |
|---|---|
| `config-version` 缺失或为旧/其他版本 | 执行迁移，写入 `5.1.0_auto_migration` 并生成迁移/审核报告 |
| `config-version: "5.1.0_auto_migration"` | 启用自动迁移；每次启动/reload 都从最新内置 `config.yml` 重建并覆盖当前值，然后刷新报告/差异 |
| `config-version: "5.1.0"` | 停止当前版本的自动迁移；跳过同版本迁移/补全，并删除旧迁移提示 |

该版本标记表示**是否启用自动迁移，而不是审核状态**。

```yaml
# 即使已经审核，也继续自动 migration
config-version: "5.1.0_auto_migration"

# 停止同版本自动 migration
config-version: "5.1.0"
```

以后真正升级到新的插件版本时，会再次进入该新版本的 `_auto_migration` 状态。
## 5. 部署模式

### 5.1 BlueMap 插件面板

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true
frontend:
  standalone:
    enabled: false
```

在 Bukkit 上，KWC 更新 BlueMap web directory 与 `webapp.conf`。在 Fabric/NeoForge + BlueMap 5.21+ 环境中，KWC 使用 BlueMapAPI 2.8.0 获取 web root 并通过 API 注册 script/style，不修改 `webapp.conf`。启用 BlueMap 集成时，`/kchat reload` 会自动请求 `bluemap reload light`，随后在 BlueMap API 的下一次 `onEnable` 中按新 KWC 配置重新注册。

### 5.2 Pl3xMap 内嵌模式

在安装了 Pl3xMap 的 Bukkit/Paper 系或 Fabric 服务器上：

```yaml
adapters:
  pl3xmap:
    enabled: true
    api-base-url: ""
```

KWC 会读取当前 Pl3xMap `config.yml` 中的 `settings.web-directory.path`（`settings.yml` 仅作为旧版/分支 fallback），只管理 Pl3xMap Web 根目录里的 KWC 专用 `kokoto-web-chat` 资源与 `index.html` 的 KWC 标记区块。Pl3xMap 重新生成网页文件后，可执行 `/kchat reload` 重新检查。当前 Pl3xMap 26.2 发布目标是 Bukkit/Paper 系和 Fabric/Quilt，不包含 NeoForge。直接 HTTP 下，空 `api-base-url` 会使用 KWC `:8899/api`；如果 NAT 改变了外部 KWC 端口，请显式填写实际公开 API URL。

### 5.3 LiveAtlas 内嵌模式

LiveAtlas 是可显示 Dynmap、squaremap、Pl3xMap、Overviewer 或多个服务器的静态前端。在 Bukkit/Fabric/NeoForge/Forge 上可这样启用：

```yaml
adapters:
  liveatlas:
    enabled: true
    api-base-url: ""
    web-root: ""
```

`web-root` 为空时，只自动识别 `index.html` 中包含 `window.liveAtlasConfig` 等 LiveAtlas 标记的目录。若 Caddy/nginx 从独立目录提供 LiveAtlas，请把服务器可见的共享/挂载路径填入 `web-root`。KWC 只管理 `kokoto-web-chat/` 与带标记的 index 区块。LiveAtlas 文件更新后执行 `/kchat reload` 即可重新插入。同一个实际 Web 根目录不要同时指定 LiveAtlas 与后端专用 KWC adapter。

### 5.4 uNmINeD 静态 Web 导出

uNmINeD 不是运行在 Minecraft 服务器中的插件，而是生成自包含的静态 Web 地图。先导出地图，再让 KWC 指向该目录：

```yaml
adapters:
  unmined:
    enabled: true
    api-base-url: ""
    web-root: "/srv/www/unmined"
```

当前 uNmINeD 导出使用 `index.html`，旧版可能使用 `unmined.index.html`。KWC 只会在确认 uNmINeD 标记后修改对应文件，仅管理 `kokoto-web-chat/` 与带标记的区块，不会改动地图 tile 或库文件。再次用 uNmINeD 导出可能替换 HTML 或 KWC 专用目录，因此导出后执行 `/kchat reload`。若地图由另一台主机提供，必须把导出目录共享/挂载给 Minecraft 服务器以便修改。

### 5.5 Minecraft Overviewer 静态 Web 地图

Minecraft Overviewer 会把基于 Leaflet 的静态 Web 地图渲染到配置的 `outputdir`。先生成地图，再让 KWC 指向该目录：

```yaml
adapters:
  overviewer:
    enabled: true
    api-base-url: ""
    web-root: "/srv/www/overviewer"
```

KWC 只会修改能确认 Overviewer 专用生成标记/资源的 `index.html`，仅管理自身 `kokoto-web-chat/` 目录和标记区块，不会改动 Overviewer 地图 tile、配置或 Leaflet 资源。之后 Overviewer 渲染或 `--update-web-assets` 可能重新生成 HTML，因此应执行 `/kchat reload`。若在其他主机渲染/提供地图，必须把输出目录共享/挂载给 Minecraft 服务器以便修改。若自行维护长期自定义模板，也可以独立使用 Overviewer 的 `customwebassets` 选项。

### 5.6 仅 standalone

```yaml
adapters:
  bluemap:
    auto-install: false
    auto-patch-webapp-conf: false
frontend:
  standalone:
    enabled: true
    path: "/"
```

```text
http://server.example.com:8899/
```

### 5.7 两种模式同时使用

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true
frontend:
  standalone:
    enabled: true
```

两个页面共享账号、历史、通知和服务端配置。

## 6. HTTP、HTTPS 与公开 URL

### 6.1 直接 HTTP

建议仅用于测试环境或受信任的内网。

```yaml
http:
  host: "0.0.0.0"
  port: 8899
  path-prefix: "/api"
  cors-origin: "*"
adapters:
  bluemap:
    api-base-url: ""
```

### 6.2 同域 HTTPS 反向代理


```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: "/chat"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"
adapters:
  bluemap:
    enabled: true
    api-base-url: ""
frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""
```

公开路径示例：

```text
https://map.example.com/
https://map.example.com/chat/api
https://map.example.com/chat
```

通常保持 `frontend.standalone.api-base-url`、`upload.public-base-url` 与 `emoji.public-base-url` 为空，让它们自动跟随当前公开 API 地址。

### 6.3 受信任代理处理

只有直接连接来源位于 `http.trusted-proxies` 时，才接受其提供的 `X-Forwarded-For`。直接 HTTP、不经过反向代理时应保持该列表为空。

临时诊断：

```yaml
http:
  log-client-ip-resolution: true
```

确认后请关闭。

#### 反向布局：standalone 位于 `/`，BlueMap 位于 `/chat/`

也可以使用与默认布局相反的方式。内部 standalone 路径仍保持 `/`，设置 `http.public-prefix: ""`，仅将 `/chat/` 去掉前缀后转发到 BlueMap，其余路径全部转发到 KWC。

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: ""

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""
```

公开路径：

```text
https://map.example.com/       KWC standalone
https://map.example.com/api    KWC API
https://map.example.com/chat/  BlueMap
```

不要把 `frontend.standalone.path` 改为 `/chat`。外部路径由 reverse proxy 与 `http.public-prefix` 决定。


## 7. Web UI 基本结构

主要区域：

- 公开消息列表
- 输入框
- 登录/退出
- 私信和群聊菜单
- 搜索
- 置顶消息
- Emoji 选择器
- 文件上传
- 通知设置
- 管理和审核面板

从 5.0.0 开始，登录用户的视觉设置可保存为多个 KWC 账号配置，只有访客继续使用浏览器本地 preset。窗口位置/尺寸、最小化状态、最后选择的配置 ID 和 Web Push 注册仍保存在 localStorage/设备本地。登录用户的通知类型与关键词提醒按账号共享，而不是按浏览器保存。浏览器本地通知收件箱仍可查看最近需要通知的事件。

```yaml
ui:
  language: "en-US"
  language-fallback: "en-US"
  time-zone: "local"
  theme: "system"
  opacity: 0.92
  resizable: true
  remember-window-size: true
```

语言：`en-US`, `ko-KR`, `ja-JP`, `zh-CN`

主题：`system`, `dark`, `light`, `high-contrast`

## 8. 公开聊天

### 8.1 游戏到 Web


```yaml
chat:
  broadcast-ingame-chat-to-web: true
```

普通 Minecraft 聊天会发布到公开 Web 聊天，发送者名称遵循 `player-display.mode`。

### 8.2 Web 到游戏


```yaml
chat:
  send-web-chat-to-game: true
  web-user-to-game-format: "[Web] {player}: {message}"
  web-guest-to-game-format: "[Web Guest] {guest}: {message}"
  web-admin-to-game-format: "[Web Admin] {player}: {message}"
```

仅配置的格式模板会解析旧式 `&` 颜色代码，不会任意改写用户消息正文的颜色。

### 8.3 Web 到 Web


```yaml
chat:
  broadcast-web-chat-to-web: true
```

公开 Web 消息会通过 SSE 推送到其他已连接的浏览器客户端。

### 8.4 消息长度


```yaml
chat:
  max-message-length: 120
  max-url-message-length: 2048
```

`0` 表示不限制。

### 8.5 消息令牌

KOKOTO WebChat 5.1.0 可以在消息保存或中继前替换管理员配置的 `:alias:` 令牌。内置 alias 只提供英文默认值，管理员可以改成或追加任意语言的 alias。

- `:enter:`, `:newline:`, `:nextline:`, `:linebreak:`, `:br:` → 换行 1 次
- `:blankline:`, `:emptyline:`, `:paragraphbreak:` → 留 1 个空行
- `:tab:`, `:indent:` → 配置数量的空格（默认 4）

未知令牌保持原样，因此不会破坏 ImageEmojis/自定义表情令牌。不会解释 `:\n:` 这类反斜杠 escape。`custom` 可添加仅包含可打印文本的替换。Minecraft 中普通 CR/LF 仍按原有规则压成单行，只有 `newline`/`blank-line` alias 产生的换行会在最终游戏投递时作为明确的多条聊天行发送。服务器中继的游戏显示也要求接收端具备相同的 4.7.0 token-line 支持。

YAML 列表设置同时支持 inline（`aliases: [bullet, arrow]`）和 block（`aliases:` 下一行使用 `- bullet`）形式。缩进只能使用普通 ASCII 空格，不能使用 tab 或全角空格。无效配置会在 `/kchat reload` 停止在线服务之前被拒绝，因此当前运行配置和 UI 语言会继续保持。

```yaml
message-tokens:
  enabled: true
  max-replacements-per-message: 24
  newline:
    aliases: [enter, newline, nextline, linebreak, br]
  blank-line:
    aliases: [blankline, emptyline, paragraphbreak]
  tab:
    aliases: [tab, indent]
    spaces: 4
  custom: {}
```

## 9. 历史记录与搜索

推荐 SQLite：

```yaml
chat:
  history-storage: "sqlite"
  history-sqlite-file: "history.db"
  history-retention-days: 5
  history-size: 0
  history-page-size: 80
```

- `sqlite`：推荐用于搜索与长期运行
- `jsonl`：旧式单文件存储
- `memory`：重启后丢失

`history-retention-days: 0` 表示禁用按保存天数清理，`history-size: 0` 表示禁用按消息条数清理。

JSONL 首次导入 SQLite：

```yaml
chat:
  history-sqlite-migrate-jsonl: true
```

搜索：

```yaml
search:
  enabled: true
  result-limit: 50
```

可按正文、发送者、日期时间、来源、是否包含系统/事件消息搜索。过大的结果数会增加数据库、内存和响应负载。

## 10. 账号绑定与登录

### 10.1 绑定 Minecraft 账号

先在 Web UI 生成绑定码，然后在游戏内执行：

```text
/kchat auth <code>
```

所需权限：

```text
kwc.auth
```

相关设置：

```yaml
auth:
  enabled: true
  link-code-length: 6
  link-code-expire-seconds: 180
  link-code-cooldown-seconds: 3
  link-code-max-per-minute: 10
```

### 10.2 密码登录

在游戏内设置 Web 登录密码：

```text
/kchat password <newPassword>
/kchat status
```

```yaml
auth:
  password-login: true
  remember-session-days: 30
```

密码以哈希形式保存，但 HTTP 登录流量本身不会加密。公网部署应使用 HTTPS。

### 10.3 角色

可用角色：

- `USER`
- `MODERATOR`
- `ADMIN`
- 未登录访客

按权限自动授予管理员角色：

```yaml
auth:
  auto-admin-from-permission: true
  admin-permission: "kwc.admin"
```

### 10.4 本地管理员账号

可以创建不绑定 Minecraft UUID 的 Web 本地管理员账号。

```yaml
admin:
  allow-local-admin-accounts: true
```

命令：

```text
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
```

### 10.5 会话管理

```text
/kchat sessions
/kchat revoke <username>
```

`revoke` 会撤销该用户的有效会话，并通知已连接的浏览器认证已失效。

## 11. 登录与连接安全

```yaml
security:
  login-fail-limit: 5
  login-fail-window-seconds: 300
  login-lock-seconds: 600
  max-sse-connections-per-ip: 10
  max-sse-connections-total: 500
```

`0` 表示禁用对应限制。

管理员登录 IP 限制：

```yaml
admin:
  allow-admin-login-from: []
```

公网管理员账号应使用 HTTPS 与强密码。

## 12. 游客聊天与验证码

```yaml
guest:
  enabled: true
  allow-custom-name: true
  name-prefix: "Guest-"
  cooldown-seconds: 6
  max-messages-per-minute: 50
  block-player-name-spoofing: true
```

`block-player-name-spoofing` 会阻止游客冒用已知玩家名称。需要额外保护的管理员或服务器名称可以加入 `blocked-names`。

验证码：

```yaml
captcha:
  mode: "math"
  expire-seconds: 120
  require-on-each-message: false
  pass-valid-minutes: 120
```

游客/IP 禁言命令：

```text
/kchat guest mute guest <name> [minutes] [reason]
/kchat guest mute ip <address> [minutes] [reason]
/kchat guest unmute guest <name>
/kchat guest unmute ip <address>
/kchat guest list
```

## 13. 玩家名称、悬停和点击

```yaml
player-display:
  mode: "name"
  strip-colors: true
```

支持 `name`、`display-name`、`custom-name`。

游戏内悬停信息：

```yaml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

占位符：`{display}`, `{real}`, `{uuid}`, `{source}`

点击名称：

- 同服务器游戏玩家：`/w <realName> `
- Web 发送者：`/kchat dm <realName> `
- 其他服务器游戏玩家：`/kchat dm <realName>@<server-id> `

## 14. 公开消息回复

```yaml
reply:
  game-click:
    enabled: true
    local-game-chat: true
  game-command-format: "&8[&dReply&8] &f{player}&7: &f{message}"
  game-preview:
    enabled: true
    format: "&7{sender}: {preview}"
    max-length: 120
  game-prefix:
    enabled: true
    text: "↪ [Reply] "
```

点击 KWC 在 Minecraft 中渲染的消息正文中非 URL 部分，会在输入框准备：

```text
/kchat reply <messageId> 
```

发送回复：

```text
/kchat reply <messageId> <message>
```

权限：

```text
kwc.reply
```

`local-game-chat: true` 会把本地游戏聊天替换为等价的可点击组件。如果其他聊天格式插件必须负责最终显示，可将其关闭；关闭后 Web→游戏与 relay 回复仍可正常工作。

URL 部分继续执行打开链接操作，只有非 URL 的正文部分会准备回复命令。

## 15. 私信

Minecraft 中的私信历史与实时通知支持点击操作：点击私信对象名称会把现有 `/kchat dm <player> ` 放入输入框，点击消息正文会准备 `/kchat reply dm-<internal-id> `。该 internal ID 仅在服务器内部使用；实际发送前 KWC 会重新验证玩家确实属于该私信会话。URL 部分仍保持正常打开链接操作。保存的 DM 若属于 Reply，实时接收/发送 echo 与历史记录都会使用与公开聊天相同的 `reply.game-preview` / `reply.game-prefix` 配置。若通过 Web 发送 DM，而已绑定的发送玩家此时在线，该消息也会显示在发送者自己的 Minecraft 聊天中。

Web 端选择私信 Reply 后，会保存与原消息的真实回复关系。KWC 验证目标是否属于同一会话，保存规范化的发送者/预览快照并显示引用；如果本地仍有原消息，还可以跳转到原文。reply metadata 会在重启后保留。跨服务器私信回复使用稳定的 relay message ID，而不是另一台服务器的本地数字数据库 ID。

私信默认关闭。

```yaml
direct-message:
  enabled: true
  storage: "auto"
  retention-days: 0
  max-messages-per-thread: 0
  max-message-length: 500
  allow-web-send: true
  allow-game-send: true
  capture-game-whispers: true
  notify-on-login: true
  notify-on-message: true
  web-unread-badge: true
  confirm-hide: true
```

收件人必须能够通过 UUID 识别。除了本地加入记录与已绑定账号之外，只要 relay 过来的游戏消息或已绑定 Web 消息包含 `playerUuid`，KWC 就会把发送者的显示名与真实 Minecraft 名称登记到新建私信的收件人搜索中。因此，在公开聊天中见过的其他服务器玩家也可以通过普通私信搜索找到。最新身份保存在 `known-display-names.yml` 中，重启后仍可搜索。没有玩家 UUID 的访客或 Discord 消息不会登记。`storage: auto` 仅在公开聊天存储为 JSONL 时让私信也使用 JSONL，否则使用 SQLite；也可以明确指定 `sqlite` 或 `jsonl`。

游戏命令：

```text
/kchat dm
/kchat dm list [pageSize]
/kchat dm unread [pageSize]
/kchat dm list next
/kchat dm list prev
/kchat dm <player> <message>
/kchat dm read <player> [pageSize]
/kchat dm next
/kchat dm prev
/kchat dm hide <messageId>
```

权限：

```text
kwc.dm
```

### 15.1 捕获 Minecraft 私聊命令

`capture-game-whispers: true` 时，下列命令会复制到同一 KWC 私信会话：

```text
/w /msg /tell /whisper /m /pm /message /t
```

KWC 不会替换同服务器正常的 Minecraft 私聊，而是为发送者与接收者各记录一份副本。远程目标使用 `name@server-id`，同样的别名会被改写为 `/kchat dm name@server-id <message>`，再通过已认证的跨服务器私信 relay 发送。未带服务器限定的 `/kchat dm <name>` 只会解析当前服务器上的玩家。`/r` 与 `/reply` 不包含目标，因此不会被拦截，仍由服务器现有的私聊插件处理。

### 15.2 发送与已读状态

正常成功送达不会显示状态标签。`Sending` 只在本地发送请求尚未完成时显示，`Failed · Retry` 只在无法确认送达时显示，均位于时间戳旁。每条私信还会显示已读状态：`Unread` 表示唯一收件人尚未阅读，`✓` 表示已读；群聊仍使用人数计数。跨服务器私信由接收服务器通过认证 relay 返回已读确认，因此发送端也会显示相同状态。最新确认是幂等的，并会在打开会话时重新发送，使临时 relay/HTTP 故障可以在之后自动修复。

## 16. 群聊

Minecraft 中的群聊消息也支持点击操作：点击群组/发送者区域会把现有 `/kchat group <room> ` 放入输入框，点击消息正文会准备该群组消息的回复目标。发送前 KWC 会重新确认当前群组成员身份，URL 部分继续保持正常打开链接操作。

Web 端的群聊 Reply 同样作为 metadata 保存。只有目标属于同一房间且发送者当前仍是成员时才接受；发送者与预览内容从已保存的原消息生成。reply metadata 重启后仍保留，本地历史中有原消息时可跳转过去。

每个房间的设置中都有 **显示成员加入/退出通知** 选项。启用后，实际成员关系变化会保存为 `member_join` / `member_leave` 事件，并显示在群聊历史以及在线成员的游戏通知中。主动加入或接受邀请会生成加入事件；主动离开、被踢出或被封禁导致成员关系移除时会生成退出事件。**关闭群聊窗口、切换房间或隐藏房间都不等于退出，不会生成退出事件。** 成员事件仅用于提示，不能作为 Reply 目标。

```yaml
group-chat:
  enabled: true
  allow-web-send: true
  allow-public-rooms: true
  allow-room-passwords: true
  retention-days: 30
  max-messages-per-room: 1000
  max-message-length: 500
  max-rooms-per-user: 20
  max-members-per-room: 50
  max-room-name-length: 32
  invite-expire-hours: 72
  sqlite-file: "group-messages.db"
```

Web 功能包括：创建公开/私有房间、以 PBKDF2 哈希保存的可选房间密码、邀请与接受/拒绝、离开、隐藏/恢复、房间设置、未读追踪、按用户隐藏消息、踢出/屏蔽/解除屏蔽成员，以及转移所有权。

每条群聊消息都会显示接收者已读状态。数字表示“发送时已经是成员、当前仍是房间成员且尚未阅读”的接收者人数，发送者本人不计入。未读人数降为 0 后显示 `✓`。正常成功送达本身不额外显示状态标签。

游戏命令：

```text
/kchat group
/kchat group list
/kchat group rooms
/kchat group <room|id> <message>
/kchat group send <room|id> <message>
/kchat group read <room|id> [pageSize]
/kchat group next
/kchat group prev
```

别名：

```text
/kchat gc ...
```

权限：

```text
kwc.group
```

## 17. 系统与事件公告

```yaml
announcements:
  broadcast-to-web-chat: true
```

默认开启：玩家进入、退出、首次进入、死亡、进度、服务器启动/停止。

默认关闭：世界切换、游戏模式、等级、上床、Web 登录/退出。

存在 i18n 键时，Web 可按用户语言显示。

## 18. 置顶消息

```yaml
pinned:
  enabled: true
  max-pins: 20
  show-to-logged-out: true
  preserve-uploads: true
```

置顶消息独立于普通历史。引用的上传文件可避免被清理。管理按钮需要在管理面板临时开启。

## 19. 文件和剪贴板上传

```yaml
upload:
  enabled: true
  allow-guest-upload: false
  allow-user-upload: true
  allow-moderator-upload: true
  allow-admin-upload: true
  cooldown-seconds: 5
  max-uploads-per-minute: 4
  max-file-size-mb: 20
  max-total-size-mb: 0
  max-files-per-message: 3
  directory: "uploads"
  retention-days: 5
  clipboard-upload-enabled: true
  clipboard-upload-send-mode: "insert"
```

默认支持 PNG/JPG/JPEG/GIF/WEBP、MP4/WEBM、MP3/M4A/OGG/WAV/FLAC。

`max-total-size-mb: 0` 表示不限总量。剪贴板模式为 `insert` 或 `send`。

即使使用 `filename-mode: original`，剪贴板上传也会优先采用 `clipboardData.files` 提供的长文件名。当 Windows/Chromium 的其他剪贴板条目返回 `202608~1.JPG` 这类 DOS 8.3 别名时，只要能够取得长文件名，KWC 就会使用原来的长文件名。如果浏览器只公开 8.3 别名，则不会把这个误导性的别名当作原文件名保存，而会改用 `clipboard-...` 形式的文件名。

## 20. 媒体与链接预览

上传媒体预览：

```yaml
upload:
  preview-images: true
  preview-videos: true
  preview-audio: true
```

YouTube：

```yaml
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  youtube-nocookie: true
  youtube-max-embeds-per-message: 1
```

YouTube Shorts 走普通 YouTube 预览流程，但使用竖屏布局。`ui.image-preview-max-per-message` 限制单条消息的图片预览数量。`ui.image-preview-max-height` 是显式的像素高度上限；设为 `0` 只会取消该显式上限，基于聊天视口的安全高度限制仍然存在。Google Drive 图片预览可通过 `ui.google-drive-image-preview` 启用，并用 `ui.google-drive-preview-mode` 选择模式。

TikTok 与 X：

```yaml
preview:
  social-embeds:
    enabled: true
    click-to-load: true
    max-embeds-per-message: 2
    tiktok:
      enabled: false
    x:
      enabled: false
      theme: "auto"
      dnt: true
```

外部嵌入会让浏览器向第三方服务发起请求。公网服务器除非明确接受自动加载，否则建议保持 `click-to-load: true`。

Discord CDN 缓存：

```yaml
preview:
  external-media-cache-enabled: true
  cache-discord-cdn: true
  external-media-cache-retention-days: 5
```

用于保留会过期的 Discord 附件 URL 的预览。

## 21. 自定义表情

```yaml
emoji:
  enabled: true
  show-button: true
  directory: "emojis"
  max-file-size-kb: 512
  max-total-size-mb: 64
  render-size-px: 32
  picker-size-px: 44
  message-token-limit: 12
  token-format: "short"
```

文件布局：

```text
<KWC data dir>/emojis/default/wave.png
<KWC data dir>/emojis/reaction/happy.gif
```

Token：

```text
:default/wave:
:reaction/happy:
:emoji:default/wave:
```

`token-format: short` 插入 `:pack/name:`；`legacy` 插入 `:emoji:pack/name:`。解析时两种格式都接受。

管理员可以在 Web 表情管理器中创建文件夹、**一次选择多个 PNG/JPG/JPEG/GIF/WEBP 并立即上传**、重命名项目以及删除项目。多文件上传按顺序处理，并继续执行已有的逐文件验证、存储额度统计、唯一名称分配、审计日志和 PNG sidecar 生成。重命名文件或表情包后，历史消息中使用旧 token 的表情可能无法继续渲染。

已有与新建的表情包目录名、文件名和 token 都会规范化。保存前会去除不支持的字符与空白；同一表情包内发生名称冲突时追加数字后缀，不同表情包仍可使用相同表情名称。

临时 `/emojis` 获取失败时，浏览器保留上一次成功的 catalog，并使用有上限的指数退避重试。SSE 重新连接成功后会强制同步 catalog；管理员修改 catalog 时，服务器发送 `emoji-catalog` SSE 事件通知其他浏览器刷新。`emoji.message-token-limit: 0` 在重新同步后仍正确表示无限制。

### 21.1 游戏内表情处理

默认：

```yaml
emoji:
  game-link:
    enabled: false
```

此时保留 token，交给 ImageEmojis-Bero 或其他游戏侧渲染器处理。

KWC 转换模式：

```yaml
emoji:
  game-link:
    enabled: true
    mode: "link"
    label-format: ":{id}:"
    max-links-per-message: 4
```

模式：

- `preserve`：保留 token
- `label`：只输出配置的标签
- `link`：输出标签和短图片 URL

## 22. ImageEmojis-Bero 1.9.x

推荐配置：

```yaml
emojisFolder: "/KOKOTO-WebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

权限：

```text
imageemojis.use
```

`replaceInCommands` 用于 `/kchat reply`、`/kchat dm` 和 `/kchat group` 中的 token 转换。中继环境中所有服务器需要相同 pack 和文件名。

```text
/emojis reload
/emojis update
```

详情请参阅 `IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md`。

## 23. 浏览器通知与 Web Push

从 5.0.0 开始，登录用户的关键词与通知类型设置保存在账号数据中，并在不同浏览器/设备之间共享。Windows、移动端等显示环境可以分别保存多个账号 UI 配置；窗口位置、尺寸、最小化状态和 Web Push endpoint 仍保留为设备本地状态。Web Admin 可限制配置数量并控制是否允许 JSON 导入/导出。同一设备已有有效 KWC Web Push 订阅时，实时页面不会再次弹出重复的系统 Notification，但应用内通知记录仍保留。


服务器端视觉配置由以下设置控制：

```yaml
ui:
  user-profiles:
    enabled: true
    max-profiles: 5
    allow-import-export: true
```

`max-profiles` 允许 0-20。配置导入/导出仅包含视觉设置，不包含会话、身份、Push 或设备窗口数据。



```yaml
notifications:
  enabled: true
  only-when-hidden: true
  notify-normal-chat: true
  notify-dm: true
  notify-group-chat: true
  notify-mentions: true
  notify-replies: true
  notify-system: true
  notify-keywords: true
  show-message-preview: true
```

```yaml
web-push:
  vapid-public-key: ""
  vapid-private-key: ""
  subject: "mailto:admin@example.com"
  notification-title: ""
  ttl-seconds: 300
```

VAPID 为空时插件会生成持久密钥。iOS/iPadOS 通常需要从主屏幕安装的 Web App 打开。

## 24. PWA 与画中画

standalone 应用名称：

```yaml
frontend:
  standalone:
    app-name: "Web Chat"
    app-short-name: "Web Chat"
```

已经安装到主屏幕的应用，在名称变化后可能需要重新安装。

画中画：

```yaml
ui:
  picture-in-picture:
    enabled: false
```

需要浏览器和操作系统支持。外部窗口控制由浏览器/操作系统管理。

## 25. DiscordSRV 集成

5.0.0 加入的管理员 Discord 关键词提醒不会把匹配或格式策略交给 DiscordSRV。关键词匹配、来源选择、mention、去重与提醒内容由 KWC 负责，只复用 DiscordSRV 已认证的 JDA 连接和 Channels 映射。Web Admin 的提醒频道选择器显示 DiscordSRV 的逻辑频道名；仅在 ID-only 配置没有逻辑名时回退到原始频道 ID。来自 Discord 的消息不会再次进入管理员提醒匹配器。

管理员提醒策略示例：

```yaml
admin-alerts:
  discord:
    enabled: false
    channel: ""
    sources:
      public-chat: true
      relay-chat: false
      dm: false
      group-chat: false
    mention: "none"
    case-sensitive: false
    keywords: ""
```

`channel: ""` 会复用 `discordsrv.channel`；Web Admin 通常保存/选择 DiscordSRV 的逻辑频道名。

```yaml
discordsrv:
  enabled: true
  channel: "global"
  web-to-discord: true
  game-relay-mode: "discordsrv"
  discord-to-web: true
  ignore-bot-messages: true
  suppress-game-echo: true
  suppress-game-echo-seconds: 5
  send-web-user-chat-to-discord: true
  send-web-guest-chat-to-discord: false
  send-web-admin-chat-to-discord: true
  append-web-emoji-links: true
  append-game-emoji-links: true
  max-emoji-links-per-message: 4
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
  discord-to-web-sender-format: "Discord:{sender}"
  discord-to-web-message-format: "{message}"
```

如果 DiscordSRV 已经负责转发普通 Minecraft 聊天，请保持 KWC `game-relay-mode: "discordsrv"`，避免重复发帖。

多个服务器共用同一个 Discord 频道时：

- 只有观察到原始本地游戏聊天的服务器修改 DiscordSRV 消息。
- relay 接收服务器不会再次把消息发到 Discord。
- 其他服务器监听器不会重复追加 `[Server]` 或 `[Web]` 前缀。

可选回复预览：

```yaml
discordsrv:
  reply-relay:
    enabled: false
    prefix-enabled: true
    preview-enabled: true
    preview-max-length: 120
```

## 26. 多服务器中继

KOKOTO WebChat 5.1.0 对 public chat 与跨服务器 1:1 DM/read receipt 使用 **Relay Protocol v2**。group chat room 仍为本地功能。

Relay v2 采用 `groups -> peers`。每个 group 只有一个 shared secret，peer 只保存 server ID、API URL 与 enabled 状态。首次设置时，可只在一台服务器上保留 `shared-secret: ""` 并启动/重载，然后把其 `config.yml` 中自动生成的值复制到同一 group 的其他服务器。已有的非空 secret 不会自动重新生成；手工 secret 不足 32 字符时保持 invalid。双方必须在同一个 group 中互相登记 peer，同一个 peer ID 不能登记到多个本地 group。

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  groups:
    - id: "main"
      shared-secret: ""
      forwarding:
        enabled: false
      peers:
        - id: "server2"
          url: "https://server2.example.com/chat/api"
          enabled: true
```

direct relay 采用逐请求模式：`/relay/v2/message` 使用 HKDF-SHA256 派生的方向性密钥与 AES-256-GCM 独立认证并传输加密负载。`/relay/v2/handshake` 只是无状态的诊断 identity/health probe，不控制 direct routing。直接 HTTP 仍可在警告状态下使用；forwarding 按 peer 单独过滤，只有对应的 http:// peer 会被排除，同组其他 https:// peer 仍可参与转发。

Relay v2 是 hop-by-hop authenticated encryption，而不是 E2EE。中继服务器是 trusted participant，会解密 payload 后再为下一 hop 重新加密。若 group secret 泄露，必须在该 group 的所有成员服务器上轮换 secret。

首次从 5.0.0 → 5.1.0 迁移时，不会根据旧的扁平拓扑猜测 v2 组，而是先禁用 Relay。请显式配置 v2 组后，再设置 `server-relay.enabled: true` 并执行 `/kchat reload`。

完整 protocol 与运维说明见 `docs/SERVER_RELAY_ZH_CN.md`。

## 27. Web 控制台命令面板

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: "ADMIN"
  show-button: true
  run-from-chat-input: false
  show-when-input-starts-with-slash: true
  require-confirm: true
  max-length: 0
  broadcast-result-to-web-chat: false
```

`broadcast-result-to-web-chat: true` 时，Web 命令执行通知会同时发布到公开 Web 聊天并显示给当前在线游戏玩家。现有控制台/审计日志保持不变，不会因为游戏内显示再额外写一份日志。

`allow-all: true` 允许从 Web 账号执行任意控制台命令，非常危险。必须配合 HTTPS、管理员 IP 限制、强密码和命令预设。

```yaml
commands:
  presets:
    - id: "day"
      label: "设置为白天"
      description: "将当前世界时间设置为白天。"
      command: "time set day"
      confirm: true
```

## 28. 管理与审核

```yaml
moderation:
  enabled: true
  allow-web-admin-panel: true
  allow-moderator-message-delete: true
  allow-moderator-guest-mute: true
  default-mute-minutes: 60
```

以上 5 个 `moderation.*` 策略键是 **仅限 config.yml 的设置**。它们不会作为可编辑项出现在 Web Admin 设置页面中；如需修改，请编辑 `config.yml` 后 reload KWC。这些值控制 Web 审核功能是否可用以及审核员允许执行的操作范围。

功能包括：

- 隐藏/删除消息
- 管理置顶
- 游客/IP 禁言
- 查看和撤销会话
- Emoji 文件管理
- 上传与存储用量
- 私聊元数据管理
- 控制台命令面板

### 28.1 私聊元数据超级管理员

```yaml
private-chat-super-admins:
  - "ExactMinecraftName"
  - "00000000-0000-0000-0000-000000000000"
```

元数据视图可显示私信/群组的标题与参与者、消息数、大致存储占用、保留状态、清理预览、锁定/自动删除排除等元数据管理功能。

`direct-message.admin-audit.enabled` 是默认关闭的只读私信正文审计开关。只有明确列在 `private-chat-super-admins` 中的账号可以使用，审计视图不能发送、回复、隐藏消息或更新已读状态。每次分页读取都会记录为 `admin.dm-audit-read`。`group-chat.admin-audit.enabled` 是独立的只读群聊正文审计开关。

### 28.2 审计日志

```yaml
audit:
  enabled: true
  directory: "audit"
```

管理操作默认追加写入 `<KWC data dir>/audit` 下按日期划分的文件；审计记录本身不会显示在 Web 管理界面中。

## 29. Web 字体与视觉配置

```yaml
web-fonts:
  enabled: false
  directory: "fonts"
  items: []
```

示例：

```yaml
web-fonts:
  enabled: true
  items:
    - family: "Pretendard"
      file: "Pretendard.woff2"
      weight: 400
      style: "normal"
```

支持的扩展名：WOFF2、WOFF、TTF、OTF。

视觉默认值：

```yaml
ui:
  font-size: 13
  message-font-size: 13
  input-font-size: 13
  text-color: ""
  ui-text-color: ""
  input-background-color: ""
  text-shadow-mode: "auto"
```

颜色值为空时跟随当前主题。

## 30. 虚拟滚动与性能

```yaml
ui:
  virtual-scroll:
    enabled: true
    overscan-screens: 0.75
    min-rendered-messages: 30
  history-preload:
    screens: 0.7
    min-px: 200
```

用于降低长历史记录的浏览器渲染负载。

```yaml
ui:
  resume-refresh:
    enabled: true
    min-interval-seconds: 5
    skip-unchanged: true
```

公共聊天的虚拟滚动不区分内容类型：图片、视频、音频、链接预览、YouTube 和其他 iframe 都使用相同的消息范围与高度跟踪规则。

## 31. 完整命令参考

用户命令：

```text
/kchat auth <code>
/kchat password <newPassword>
/kchat status
/kchat dm
/kchat dm list [pageSize]
/kchat dm unread [pageSize]
/kchat dm <player> <message>
/kchat dm read <player> [pageSize]
/kchat dm next
/kchat dm prev
/kchat dm hide <messageId>
/kchat reply <messageId> <message>
/kchat group list
/kchat group <room> <message>
/kchat group send <room> <message>
/kchat group read <room> [pageSize]
/kchat group next
/kchat group prev
```

管理员命令：

```text
/kchat reload
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
/kchat guest mute <guest|ip> <value> [minutes] [reason]
/kchat guest unmute <guest|ip> <value>
/kchat guest list
/kchat sessions
/kchat revoke <username>
```

根命令别名：

```text
/kc
```

群聊别名：

```text
/kchat gc
```

## 32. 权限

```text
kwc.auth
kwc.webchat
kwc.dm
kwc.reply
kwc.group
kwc.admin
kwc.update.notify
```

用户功能权限默认允许，`kwc.admin` 与 `kwc.update.notify` 默认仅 OP。

## 33. 数据文件与备份

```text
<KWC data dir>/config.yml
<KWC data dir>/history.db
<KWC data dir>/direct-messages.db
<KWC data dir>/group-messages.db
<KWC data dir>/web-push-subscriptions.jsonl
<KWC data dir>/emojis/
<KWC data dir>/uploads/
<KWC data dir>/audit/
```

建议备份配置、数据库/JSONL、Emoji、需要保留的上传文件、Push 订阅和 VAPID 密钥。SQLite 最好在正常停服后复制。

## 34. Reload 与重启

通常可由 `/kchat reload` 应用：

- 大多数配置变更
- HTTP 服务与中继重建
- UI 默认值更新

必须重启：

- 更换 JAR
- Java 代码变化
- 插件加载顺序变化

`/kchat reload` 会自动请求 `bluemap reload light`。如果资源仍未更新或自动执行失败，请手动运行 `/bluemap reload light`。

## 35. 故障排查速查

### 页面打不开

- 确认 `enabled: true`
- 检查 `http.host` 与 `http.port`
- 检查端口冲突
- 确认反向代理 upstream 指向 `127.0.0.1:8899`
- 需要 standalone 页面时启用 `frontend.standalone.enabled`

### BlueMap 中没有聊天按钮

- 启用 `adapters.bluemap.auto-install`
- 启用 `adapters.bluemap.auto-patch-webapp-conf`
- 检查 BlueMap 路径
- 查看安装/补丁日志
- `/kchat reload` 通常会请求 `bluemap reload light`；仅在必要时手动执行 `/bluemap reload light`
- 刷新浏览器缓存

### 登录失败

- 检查 HTTPS 域名与 cookie path
- 确认绑定码是否过期
- 查看登录锁定日志
- 检查 `auth.password-login`
- 检查管理员 IP 限制

### Web 聊天没有进入游戏

- 设置 `chat.send-web-chat-to-game: true`
- 确认有玩家在线
- 检查聊天格式插件冲突
- relay 消息还需确认 `server-relay.delivery.game: true`

### 游戏聊天没有进入 Web

- 设置 `chat.broadcast-ingame-chat-to-web: true`
- 检查玩家权限与事件是否被取消
- 检查其他聊天插件是否独占消费该事件

### 点击 Reply 没有反应

- 启用 `reply.game-click.enabled`
- 本地游戏消息还需启用 `local-game-chat`
- 检查聊天格式插件冲突
- URL 部分打开链接而不是触发回复属于正常行为

### 表情 token 仍显示为文字

- 确认 KWC 表情文件存在
- 检查 ImageEmojis-Bero 共用目录与权限
- 启用 `replaceInCommands`
- 执行 `/emojis reload` 与 `/emojis update`
- 确认所有 relay 服务器上的表情文件一致

### Relay 返回 403

- 确认两台服务器在同一 relay group 中相互登记
- 确认 peer ID 与远端 `server-id` 完全一致
- direct relay 会逐请求独立认证；请确认双方在同一 group 中使用相同 shared-secret 互相登记 peer。若涉及 forwarding，则收到该消息的 peer 与所选下一跳 peer 都必须配置为 HTTPS
- 修改 relay 配置后重载两端

### Relay 返回 401

- 对比两端 group ID 与 group shared secret
- 确认双方使用完全相同的 group secret。首次设置应只在一台服务器上从空值生成并复制到其他服务器；手工 non-empty secret 仍需至少 32 个字符
- 确认代理没有修改签名使用的 relay header/body
- 检查服务器时钟同步与 replay/nonce 诊断

### Discord 前缀重复

- 确认所有服务器运行相同修正版
- 检查是否有其他插件重新发送 relay 消息
- DiscordSRV 已转发游戏聊天时保持 `game-relay-mode: "discordsrv"`

### Web Push 不工作

- 确认 HTTPS
- 检查通知权限
- 检查 Service Worker 与 Push API 支持
- iOS 使用安装到主屏幕的 Web 应用
- 检查 VAPID subject 与 key 文件

## 36. 相关文档

- `CONFIGURATION_ZH_CN.md`
- `UPGRADE_5_0_0_ZH_CN.md`: 4.7.0→5.0.0 Core 拆分/reload 安全升级
- `UPGRADE_4_7_0_ZH_CN.md`: 4.6.3→4.7.0 功能升级
- `SERVER_RELAY_ZH_CN.md`
- `UPGRADE_4_6_3_ZH_CN.md`: 4.6.2→4.6.3 升级
- `UPGRADE_4_6_2_ZH_CN.md`: 4.6.1→4.6.2 升级
- `UPGRADE_4_6_1_ZH_CN.md`: 4.6.0→4.6.1 升级
- `UPGRADE_4_6_0_ZH_CN.md`
- `CADDY_HTTPS_ZH_CN.md`
- `NGINX_HTTPS_ZH_CN.md`
- `IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md`
- `INSTALL_TROUBLESHOOTING_ZH_CN.md`
- `UPLOAD_SECURITY_ZH_CN.md`
- `OPERATIONS_SECURITY_ZH_CN.md`
- `I18N_ZH_CN.md`
- `RELEASE_CHECKLIST_ZH_CN.md`


### 管理员群聊正文审计（4.6.3）
需要设置 `group-chat.admin-audit.enabled: true`，并把准确的 Minecraft 名或 UUID 列入 `private-chat-super-admins`；两个条件都必须满足。符合条件的管理员即使不是房间成员，也可以从管理员房间元数据列表中只读打开群聊正文。审计视图不会加入房间、更新已读/未读状态、发送/上传/隐藏消息或修改成员关系。每次分页读取都会记录为 `admin.group-audit-read`，消息正文不会复制到审计日志。



## SimpleNicks-Bero 集成

Bukkit/Paper 系列使用 `player-display.mode: "display-name"` 可显示 [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) 写入的 Bukkit display name，真实 linked username/UUID 仍作为 KWC identity。详见 `SIMPLENICKS_BERO_ZH_CN.md`；一般运维参考 [上游 SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)。
