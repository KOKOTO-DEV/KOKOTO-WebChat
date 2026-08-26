# KOKOTO WebChat 5.0.0 完整用户与运维手册

> **5.0.0 运维：** Web Admin **Filter** 管理公共/群组/可选 DM 的 block/mask/replace 规则与测试，**Settings** 管理 guest/CAPTCHA、会话、moderation、upload、filter 的安全实时设置。游戏侧使用 `/kchat filter` / `/kchat settings`。会话期限变更按创建时间重算现有目标会话，且不会复活已经过期的会话。`upload.filename-mode: original` 为新上传保留安全的 Unicode 原名并在重名时自动编号。


本文从普通用户和服务器管理员两个角度说明 KOKOTO WebChat 5.0.0 的全部功能。逐项配置说明请参阅 `CONFIGURATION_ZH_CN.md`，服务器中继请参阅 `SERVER_RELAY_ZH_CN.md`，HTTPS 部署请同时参阅 `CADDY_HTTPS_ZH_CN.md` 与 `NGINX_HTTPS_ZH_CN.md`。

## 1. 插件概述

KOKOTO WebChat 用于把 Minecraft 服务器聊天连接到浏览器。5.0.0 提供 Bukkit/Paper/Spigot，以及 Fabric 1.18.2～26.2、NeoForge 1.20.2～26.2、Forge 1.18.2～26.2 exact-target 构建。

支持：

- 嵌入 BlueMap 的聊天面板
- 不依赖 BlueMap 的 standalone 页面
- 嵌入模式与 standalone 同时使用
- 游戏与 Web 双向公开聊天
- 持久化私信线程与群聊房间
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
config-version: "5.0.0"
enabled: false
```

禁用时不会启动 Web 服务、聊天转发和清理任务，但管理员仍可使用 `/kchat reload`。

## 4. 配置迁移

现有配置值会被保留，但 active migration 不继承旧 config 文本。KWC 以当前内置 `config.yml` 创建新的模板，再覆盖现有管理员值；旧注释、顺序、空白和缩进会被丢弃，使用最新内置注释/布局。

当前版本的完整 reference 始终写入：

```text
<KWC data dir>/config-reference-5.0.0.yml
```

它是 JAR 内置 `config.yml` 的**原样副本，包括注释和字符串 scalar 的规范双引号格式**，仅供管理员查看完整默认设置，不作为 migration 模板。`/kchat reload` 会在停止 live service 之前验证 YAML；无效 YAML 会保留之前正在运行的配置。

当 `config-version` 缺失或与运行版本不同时，KWC 会对真实 `config.yml` 执行一次 migration：

- 使用最新内置 `config.yml` 作为新文件模板。
- 把现有管理员设置值覆盖到该模板上。
- 不继承旧注释、顺序、空白和缩进。
- 若旧 marker 不带 `*_auto_migration`，真实版本升级前会完整备份原 `config.yml`。
- 已存在设置的内置默认值若在新版本发生变化，不会静默覆盖，而是保留为审核项目。
- 真实文件会标记为 `config-version: "5.0.0_auto_migration"`。

随后生成：

```text
<KWC data dir>/config-migration-5.0.0.yml
```

它不再是让管理员复制缺失设置的 fragment，而是**审核报告**。其中记录自动插入数量、仍需管理员判断的默认值变化、最终确认用的精确版本标记，以及 current-vs-reference 文本 diff。由于缺失设置和注释已经放进真实 config 的正确位置，不会再作为大块 reference-only 内容堆在 diff 顶部。

判定规则：

| 真实 `config.yml` 状态 | 行为 |
|---|---|
| `config-version` 缺失或为旧/其他版本 | 执行 migration，写入 `5.0.0_auto_migration` 并生成 migration/审核报告 |
| `config-version: "5.0.0_auto_migration"` | 启用自动 migration；每次 startup/reload 都从最新内置 `config.yml` 重建并覆盖当前值，然后刷新 report/diff |
| `config-version: "5.0.0"` | 停止当前版本的自动 migration；跳过同版本 migration/backfill，并删除旧 migration 提示 |

该 marker 表示**是否启用自动 migration，而不是审核状态**。

```yaml
# 即使已经审核，也继续自动 migration
config-version: "5.0.0_auto_migration"

# 停止同版本自动 migration
config-version: "5.0.0"
```

以后发生真正的插件版本升级时，会再次进入新版本的 `_auto_migration` 状态。
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

测试用直接 HTTP：

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

同域 HTTPS 反向代理：

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
    api-base-url: ""
```

公开路径示例：

```text
https://map.example.com/
https://map.example.com/chat/api
https://map.example.com/chat
```

通常保持 `frontend.standalone.api-base-url`、`upload.public-base-url` 与 `emoji.public-base-url` 为空，让它们自动跟随当前公开 API 地址。

仅信任 `http.trusted-proxies` 中代理发送的 `X-Forwarded-For`。

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

游戏→Web：

```yaml
chat:
  broadcast-ingame-chat-to-web: true
```

Web→游戏：

```yaml
chat:
  send-web-chat-to-game: true
  web-user-to-game-format: "[Web] {player}: {message}"
  web-guest-to-game-format: "[Web Guest] {guest}: {message}"
  web-admin-to-game-format: "[Web Admin] {player}: {message}"
```

Web→Web：

```yaml
chat:
  broadcast-web-chat-to-web: true
```

长度限制：

```yaml
chat:
  max-message-length: 120
  max-url-message-length: 2048
```

`0` 表示不限制。

### 8.5 消息令牌

KOKOTO WebChat 5.0.0 可以在消息保存或中继前替换管理员配置的 `:alias:` 令牌。内置 alias 只提供英文默认值，管理员可以改成或追加任意语言的 alias。

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

Web 页面生成代码后，在游戏中执行：

```text
/kchat auth <code>
```

权限：

```text
kwc.auth
```

```yaml
auth:
  enabled: true
  link-code-length: 6
  link-code-expire-seconds: 180
  link-code-cooldown-seconds: 3
  link-code-max-per-minute: 10
```

设置 Web 密码：

```text
/kchat password <newPassword>
/kchat status
```

```yaml
auth:
  password-login: true
  remember-session-days: 30
```

角色：USER、MODERATOR、ADMIN。

从权限自动赋予管理员角色：

```yaml
auth:
  auto-admin-from-permission: true
  admin-permission: "kwc.admin"
```

本地管理员账号：

```text
/kchat admin create <id>
/kchat admin password <id> <password>
/kchat admin role <id> <user|moderator|admin>
```

会话管理：

```text
/kchat sessions
/kchat revoke <username>
```

## 11. 登录与连接安全

```yaml
security:
  login-fail-limit: 5
  login-fail-window-seconds: 300
  login-lock-seconds: 600
  max-sse-connections-per-ip: 5
  max-sse-connections-total: 200
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

点击非 URL 正文会建议：

```text
/kchat reply <messageId> 
```

发送：

```text
/kchat reply <messageId> <message>
```

权限：`kwc.reply`

URL 部分保留打开链接动作。与聊天格式插件冲突时可设置 `local-game-chat: false`。

## 15. 私信 DM

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

`storage: auto` 仅在公开聊天使用 JSONL 时让私信也使用 JSONL，其他情况使用 SQLite，也可显式选择 `sqlite` 或 `jsonl`。

DM 对象按 UUID 识别。除了本服加入记录和已关联账号之外，服务器中继收到的游戏消息或已关联 Web 消息只要包含 `playerUuid`，其显示名和真实 Minecraft 名也会登记到 Web DM 的新会话对象搜索中。这样可直接在普通 DM 搜索框中查找公开聊天里看到的其他服务器发送者。最新名称会保存在 `known-display-names.yml`，重启后仍可搜索。没有玩家 UUID 的访客和 Discord 消息不会登记。

命令：

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

权限：`kwc.dm`

`capture-game-whispers: true` 会把 `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` 的内容复制到 KWC 私信线程，但不会替代同服务器原本的 Minecraft 私聊。向其他服务器发送时使用 `名称@server-id`，这些别名会转换成 `/kchat dm 名称@server-id <消息>` 并通过签名的跨服务器 DM relay 发送。未指定服务器的 `/kchat dm <名称>` 只会解析当前服务器上的玩家。由于 `/r`、`/reply` 不包含目标并依赖现有私聊插件的最近联系人状态，因此不会被拦截。

### 15.2 发送与已读状态

正常投递完成不显示状态文字。只有本地发送请求仍在处理中时，才在时间显示旁边显示 `发送中`；无法确认投递时显示 `失败 · 重试`。私信的每一条消息都会在时间旁边显示已读状态：一对一私信在对方尚未阅读时显示 `未读`，阅读后显示 `✓`。群聊继续使用未读接收者人数。跨服务器私信的已读信息会通过认证的服务器中继返回，并在原始消息一侧显示相同状态。重新打开会话时会安全重发最新已读 ACK，因此即使发生临时中继或 HTTP 故障，也可以在之后查看会话时恢复已读标记。

## 16. 群聊

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

Web 支持公开/私有房间、以 PBKDF2 哈希保存的可选密码、邀请、接受/拒绝、退出、隐藏/恢复、成员踢出/封禁、房主转移和未读跟踪。

群聊的每一条消息都会显示该消息的接收者已读状态。数字表示**在消息发送时已经加入房间、当前仍是成员且尚未阅读该消息的接收者人数**。消息发送者不是接收者，因此不计入人数。未读接收者为 0 时显示 `✓`。正常投递完成本身不显示状态文字。

```text
/kchat group
/kchat group list
/kchat group rooms
/kchat group <room|id> <message>
/kchat group send <room|id> <message>
/kchat group read <room|id> [pageSize]
/kchat group next
/kchat group prev
/kchat gc ...
```

权限：`kwc.group`

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

## 20. 媒体预览

```yaml
upload:
  preview-images: true
  preview-videos: true
  preview-audio: true
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  youtube-nocookie: true
  youtube-max-embeds-per-message: 1
```

YouTube Shorts 使用竖屏播放器。可用 `ui.image-preview-max-per-message` 和 `ui.image-preview-max-height` 限制预览数量与高度，Google Drive 图片预览可通过 `ui.google-drive-image-preview` 启用。

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

外部嵌入会从用户浏览器访问第三方服务，公网服务器建议保持 click-to-load。

Discord CDN 缓存：

```yaml
preview:
  external-media-cache-enabled: true
  cache-discord-cdn: true
  external-media-cache-retention-days: 5
```

## 21. 自定义 Emoji

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

```text
<KWC data dir>/emojis/default/wave.png
:default/wave:
:emoji:default/wave:
```

管理员可创建文件夹、上传、重命名和删除。重命名会使历史消息中的旧 token 可能无法解析。

使用游戏侧 Emoji 插件时：

```yaml
emoji:
  game-link:
    enabled: false
```

KWC 自行转换时可使用 `preserve`、`label`、`link` 模式。

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

## 24. PWA 与 PIP

```yaml
frontend:
  standalone:
    app-name: "Web Chat"
    app-short-name: "Web Chat"
ui:
  picture-in-picture:
    enabled: false
```

更改已安装 PWA 名称后可能需要重新安装。

## 25. DiscordSRV 集成

5.0.0 的管理员 Discord 关键词提醒不会把检测或格式策略交给 DiscordSRV。关键词匹配、来源选择、mention、去重与提醒内容由 KWC 负责，仅复用 DiscordSRV 已认证的 JDA 连接和 Channels 映射。Web Admin 的提醒频道下拉框只显示 DiscordSRV 的逻辑频道名；只有在 ID-only 配置中才把频道 ID 作为 fallback。来自 Discord 的消息不会再次进入管理员关键词提醒。

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

`channel: ""` 复用 `discordsrv.channel`；Web Admin 通常选择/保存 DiscordSRV 的逻辑频道名。




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
  append-web-emoji-links: true
  append-game-emoji-links: true
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
```

DiscordSRV 已发送普通游戏聊天时，保持 `game-relay-mode: "discordsrv"`。

多个服务器共享频道时：

- 只有检测到原始本地游戏事件的服务器修改 DiscordSRV 消息
- 中继接收服务器不重新发送到 Discord
- 不重复添加 `[Server]` 与 `[Web]`

可通过 `discordsrv.reply-relay` 启用回复预览。

## 26. 多服务器聊天中继

`peers` 不是持久连接/会话列表，而是本服务器发送消息的 HTTP 目标列表。同一条目的 `id` / `secret` 也用于入站请求认证；双向中继需要两端分别配置对方。

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "Server1"
  shared-secret: "long-shared-secret"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8
  forward-received-public-chat: true
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server2"
      url: "https://server2.example.com/chat/api"
      secret: ""
      enabled: true
```

`forward-received-public-chat` 控制 peer 收到公共聊天后是否继续转发给其他 peer。`true` 支持 hub/chain 拓扑，`false` 将公共聊天限制为直接 peer 链路。跨服务器 DM 与已读回执路由不受影响。

实际 URL：

```text
https://server2.example.com/chat/api/relay/receive
```

接收端 `peers[].id` 必须等于发送端 `server-id`。Peer secret 优先于 shared secret。没有持久离线队列。

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

错误：

- 403 `unknown_peer`

同一目标在没有成功响应的情况下 3 次返回 `403 unknown_peer` 后，KWC 会把该目标置于 60 秒 backoff。此期间产生的该目标 relay 消息会直接跳过，不发送周期性 probe。60 秒后出现的第一条实际 relay 消息会自动重试；如果仍失败，则从该失败时刻重新等待 60 秒，成功响应则立即恢复正常发送并清零计数。接收端会在应用请求前拒绝未知发送方。连接被拒绝、超时等 transport failure 也使用相同的 3 次/60 秒 backoff，因此离线 peer 不会对每条转发消息都重复产生警告。
- 401 `bad_signature`
- 401 `expired_request`
- 404 `relay_disabled`
- 426 `unsupported_protocol`

详情：`SERVER_RELAY_ZH_CN.md`。

## 27. Web 控制台命令面板

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: "ADMIN"
  show-button: true
  run-from-chat-input: false
  require-confirm: true
  max-length: 0
  broadcast-result-to-web-chat: false
```

`allow-all: true` 允许从 Web 账号执行任意控制台命令，非常危险。必须配合 HTTPS、管理员 IP 限制、强密码和命令预设。

```yaml
commands:
  presets:
    - id: "day"
      label: "设置为白天"
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

功能包括：

- 隐藏/删除消息
- 管理置顶
- 游客/IP 禁言
- 查看和撤销会话
- Emoji 文件管理
- 上传与存储用量
- 私聊元数据管理
- 控制台命令面板

私聊元数据超级管理员：

```yaml
private-chat-super-admins:
  - "ExactMinecraftName"
  - "00000000-0000-0000-0000-000000000000"
```

默认只能查看参与者、数量、容量、保留和清理信息。

如需只读审计私信正文，还要启用：

```yaml
direct-message:
  admin-audit:
    enabled: true
```

只有同时满足 `private-chat-super-admins` 和该开关的账号才能打开管理员私信会话。普通 ADMIN/MODERATOR 角色本身不能查看正文。审计视图不能发送消息、隐藏参与者消息或更新已读状态。每次分页读取都会写入 `admin.dm-audit-read` 审计记录，但正文不会复制到审计日志。

审计日志：

```yaml
audit:
  enabled: true
  directory: "audit"
```

## 29. Web 字体与显示

```yaml
web-fonts:
  enabled: true
  directory: "fonts"
  items:
    - family: "Pretendard"
      file: "Pretendard.woff2"
      weight: 400
      style: "normal"
```

支持 WOFF2、WOFF、TTF、OTF。

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

空颜色使用主题默认值。

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

## 31. 命令完整列表

用户：

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

管理员：

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

别名：`/kc`；群聊还可使用 `/kchat gc`。

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

## 35. 快速故障排查

页面打不开：

- `enabled: true`
- host/port
- 端口冲突
- proxy upstream
- standalone 是否启用

BlueMap 没有聊天按钮：

- auto-install/auto-patch
- BlueMap 路径
- 日志
- `/kchat reload` 通常会自动执行 `bluemap reload light`；仅在需要时手动执行 `/bluemap reload light`。
- 浏览器缓存

登录失败：

- HTTPS/cookie path
- 代码过期
- 登录锁定
- password-login
- 管理员 IP 限制

游戏/Web 不互通：

- `send-web-chat-to-game`
- `broadcast-ingame-chat-to-web`
- 聊天插件事件冲突
- relay delivery

回复点击无效：

- game-click enabled
- local-game-chat
- URL 点击打开链接是正常行为

Emoji 显示为文本：

- 文件和 pack 名
- ImageEmojis 权限
- replaceInCommands
- reload/update
- 所有中继服务器文件一致

中继 403：

- 接收端 peer ID 与发送端 server-id
- 接收端 reload
- active peer 日志

中继 401：

- secret
- proxy 是否修改正文/头
- 时钟同步

Discord 前缀重复：

- 所有服务器版本一致
- 其他插件是否再次转发
- DiscordSRV 已转发时设置 game-relay-mode discordsrv

Web Push：

- HTTPS
- 通知权限
- Service Worker/Push API
- iOS 主屏幕 Web App
- VAPID

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
