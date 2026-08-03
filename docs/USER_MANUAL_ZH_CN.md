# BlueMapWebChat 4.6.0 完整用户与运维手册

本文从普通用户和服务器管理员两个角度说明 BlueMapWebChat 4.6.0 的全部功能。逐项配置说明请参阅 `CONFIGURATION_ZH_CN.md`，服务器中继请参阅 `SERVER_RELAY_ZH_CN.md`，HTTPS 部署请同时参阅 `CADDY_HTTPS_ZH_CN.md` 与 `NGINX_HTTPS_ZH_CN.md`。

## 1. 插件概述

BlueMapWebChat 用于把 Bukkit/Paper/Spigot 兼容 Minecraft 服务器的聊天连接到浏览器。

支持：

- 嵌入 BlueMap 的聊天面板
- 不依赖 BlueMap 的 standalone 页面
- 嵌入模式与 standalone 同时使用
- 游戏与 Web 双向公开聊天
- 持久化私信线程与群聊房间
- DiscordSRV 集成
- 多个 Minecraft 服务器之间的公开聊天中继

默认 HTTP 端口为 `8899`，API 路径前缀为 `/api`，standalone 路径为 `/chat`。

## 2. 环境要求

必需：

- Bukkit/Paper/Spigot 兼容服务器
- 安装插件 JAR 的权限

可选集成：

- BlueMap
- DiscordSRV
- ImageEmojis-Bero 1.9.0
- Caddy 或 Nginx

公网部署时不要直接公开 `8899`，建议绑定到 `127.0.0.1:8899`，再通过 HTTPS 反向代理发布。

## 3. 安装与首次启用

1. 将 JAR 放入 `plugins/`。
2. 启动服务器一次。
3. 确认生成 `plugins/BlueMapWebChat/config.yml`。
4. 新配置默认使用 `enabled: false`。
5. 检查 URL、存储方式、保留期限、认证和上传限制。
6. 配置需要的功能后设置 `enabled: true`。
7. 重启服务器或执行 `/bmchat reload`。

```yaml
config-version: "4.6.0"
enabled: false
```

禁用时不会启动 Web 服务、聊天转发和清理任务，但管理员仍可使用 `/bmchat reload`。

## 4. 配置迁移

更新时不会覆盖现有 `config.yml`。

当 `config-version` 缺失或与当前插件版本不同，会生成：

```text
plugins/BlueMapWebChat/config-migration-4.6.0.yml
```

判定规则：

| 实际 `config.yml` 状态 | 行为 |
|---|---|
| 缺少 `config-version` | 即使其他差异为 0，也生成只包含目标版本标记的迁移文件 |
| `config-version` 与插件版本不同 | 生成或更新包含缺失/变化设置及目标版本标记的迁移文件 |
| `config-version` 与插件版本一致 | 视为已审核，跳过比较和文件生成，并删除遗留的同版本提示文件 |

文件包含可复制的实际 YAML：

- 当前配置缺少的新设置
- 当前值仍等于旧默认值、但新版本默认值已经变化的设置
- 最终审核标记对应的目标 `config-version`

即使没有其他配置差异，也会为了配置版本管理生成包含 `config-version` 的文件。数量、旧值和新值说明只使用 `#` 注释。实际 `config.yml` 不会自动修改。

确认并合并后设置：

```yaml
config-version: "4.6.0"
```

版本一致后将跳过后续比较。

## 5. 部署模式

### 5.1 BlueMap 插件面板

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true
standalone-web:
  enabled: false
```

Web 资源未更新时执行：

```text
/bluemap reload
```

### 5.2 仅 standalone

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false
standalone-web:
  enabled: true
  path: "/chat"
```

```text
http://server.example.com:8899/chat
```

### 5.3 两种模式同时使用

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true
standalone-web:
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
web-addon:
  api-base-url: ""
```

同域 HTTPS 反向代理：

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"
web-addon:
  api-base-url: "/bmwc/api"
standalone-web:
  enabled: true
  api-base-url: ""
```

公开路径示例：

```text
https://map.example.com/
https://map.example.com/bmwc/api
https://map.example.com/bmwc/chat
```

通常保持 `standalone-web.api-base-url`、`upload.public-base-url` 与 `emoji.public-base-url` 为空，让它们自动跟随当前公开 API 地址。

仅信任 `http.trusted-proxies` 中代理发送的 `X-Forwarded-For`。

临时诊断：

```yaml
http:
  log-client-ip-resolution: true
```

确认后请关闭。

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

用户界面偏好保存在浏览器 localStorage 中。面板支持移动、调整大小和记忆尺寸，浏览器本地通知收件箱可查看最近需要通知的事件。

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
/bmchat auth <code>
```

权限：

```text
bluemapwebchat.auth
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
/bmchat password <newPassword>
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
  admin-permission: "bluemapwebchat.admin"
```

本地管理员账号：

```text
/bmchat admin create <id>
/bmchat admin password <id> <password>
/bmchat admin role <id> <user|moderator|admin>
```

会话管理：

```text
/bmchat sessions
/bmchat revoke <username>
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

游客/IP 禁言`storage: auto` 仅在公开聊天使用 JSONL 时让私信也使用 JSONL，其他情况使用 SQLite，也可显式选择 `sqlite` 或 `jsonl`。

命令：

```text
/bmchat guest mute guest <name> [minutes] [reason]
/bmchat guest mute ip <address> [minutes] [reason]
/bmchat guest unmute guest <name>
/bmchat guest unmute ip <address>
/bmchat guest list
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
- Web 发送者：`/bmchat dm <realName> `
- 其他服务器游戏玩家：`/bmchat dm <realName> `

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
/bmchat reply <messageId> 
```

发送：

```text
/bmchat reply <messageId> <message>
```

权限：`bluemapwebchat.reply`

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

命令：

```text
/bmchat dm
/bmchat dm list [pageSize]
/bmchat dm unread [pageSize]
/bmchat dm list next
/bmchat dm list prev
/bmchat dm <player> <message>
/bmchat dm read <player> [pageSize]
/bmchat dm next
/bmchat dm prev
/bmchat dm hide <messageId>
```

权限：`bluemapwebchat.dm`

`capture-game-whispers: true` 会把 `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` 的内容复制到 BMChat 私信线程，但不会替代 Minecraft 原本的私聊。

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

```text
/bmchat group
/bmchat group list
/bmchat group rooms
/bmchat group <room|id> <message>
/bmchat group send <room|id> <message>
/bmchat group read <room|id> [pageSize]
/bmchat group next
/bmchat group prev
/bmchat gc ...
```

权限：`bluemapwebchat.group`

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
plugins/BlueMapWebChat/emojis/default/wave.png
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

BMChat 自行转换时可使用 `preserve`、`label`、`link` 模式。

## 22. ImageEmojis-Bero 1.9.0

推荐配置：

```yaml
emojisFolder: "/BlueMapWebChat/emojis"
templateFormat: ":<emoji>:"
replaceInCommands: true
```

权限：

```text
imageemojis.use
```

`replaceInCommands` 用于 `/bmchat reply`、`/bmchat dm` 和 `/bmchat group` 中的 token 转换。中继环境中所有服务器需要相同 pack 和文件名。

```text
/emojis reload
/emojis update
```

详情请参阅 `IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md`。

## 23. 浏览器通知与 Web Push

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
  notify-own-messages: true
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
standalone-web:
  app-name: "Web Chat"
  app-short-name: "Web Chat"
ui:
  picture-in-picture:
    enabled: false
```

更改已安装 PWA 名称后可能需要重新安装。

## 25. DiscordSRV 集成

```yaml
discordsrv:
  enabled: true
  channel: "global"
  web-to-discord: true
  game-to-discord: false
  discord-to-web: true
  ignore-bot-messages: true
  suppress-game-echo: true
  suppress-game-echo-seconds: 5
  append-web-emoji-links: true
  append-game-emoji-links: true
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-to-discord-format: "[{server}] {sender}: {message}"
```

DiscordSRV 已发送普通游戏聊天时，保持 `game-to-discord: false`。

多个服务器共享频道时：

- 只有检测到原始本地游戏事件的服务器修改 DiscordSRV 消息
- 中继接收服务器不重新发送到 Discord
- 不重复添加 `[Server]` 与 `[Web]`

可通过 `discordsrv.reply-relay` 启用回复预览。

## 26. 多服务器聊天中继

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
      url: "https://server2.example.com/bmwc/api"
      secret: ""
      enabled: true
```

实际 URL：

```text
https://server2.example.com/bmwc/api/relay/receive
```

接收端 `peers[].id` 必须等于发送端 `server-id`。Peer secret 优先于 shared secret。没有持久离线队列。

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

错误：

- 403 `unknown_peer`
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

只能查看参与者、数量、容量、保留和清理信息，不能查看消息正文。

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
    preserve-visible-media: false
    preserve-playing-media: true
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
    skip-while-media-active: true
    skip-unchanged: true
```

## 31. 命令完整列表

用户：

```text
/bmchat auth <code>
/bmchat password <newPassword>
/bmchat dm
/bmchat dm list [pageSize]
/bmchat dm unread [pageSize]
/bmchat dm <player> <message>
/bmchat dm read <player> [pageSize]
/bmchat dm next
/bmchat dm prev
/bmchat dm hide <messageId>
/bmchat reply <messageId> <message>
/bmchat group list
/bmchat group <room> <message>
/bmchat group send <room> <message>
/bmchat group read <room> [pageSize]
/bmchat group next
/bmchat group prev
```

管理员：

```text
/bmchat reload
/bmchat admin create <id>
/bmchat admin password <id> <password>
/bmchat admin role <id> <user|moderator|admin>
/bmchat guest mute <guest|ip> <value> [minutes] [reason]
/bmchat guest unmute <guest|ip> <value>
/bmchat guest list
/bmchat sessions
/bmchat revoke <username>
```

别名：`/bmc`、`/bluemapchat`，群聊可用 `/bmchat gc`。

## 32. 权限

```text
bluemapwebchat.auth
bluemapwebchat.webchat
bluemapwebchat.dm
bluemapwebchat.reply
bluemapwebchat.group
bluemapwebchat.admin
```

用户权限默认允许，管理员权限默认仅 OP。

## 33. 数据文件与备份

```text
plugins/BlueMapWebChat/config.yml
plugins/BlueMapWebChat/history.db
plugins/BlueMapWebChat/direct-messages.db
plugins/BlueMapWebChat/group-messages.db
plugins/BlueMapWebChat/web-push-subscriptions.jsonl
plugins/BlueMapWebChat/emojis/
plugins/BlueMapWebChat/uploads/
plugins/BlueMapWebChat/audit/
```

建议备份配置、数据库/JSONL、Emoji、需要保留的上传文件、Push 订阅和 VAPID 密钥。SQLite 最好在正常停服后复制。

## 34. Reload 与重启

通常可由 `/bmchat reload` 应用：

- 大多数配置变更
- HTTP 服务与中继重建
- UI 默认值更新

必须重启：

- 更换 JAR
- Java 代码变化
- 插件加载顺序变化

仅 BlueMap 资源未更新时可执行 `/bluemap reload`。

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
- `/bluemap reload`
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
- DiscordSRV 已转发时设置 game-to-discord false

Web Push：

- HTTPS
- 通知权限
- Service Worker/Push API
- iOS 主屏幕 Web App
- VAPID

## 36. 相关文档

- `CONFIGURATION_ZH_CN.md`
- `SERVER_RELAY_ZH_CN.md`
- `UPGRADE_4_6_0_ZH_CN.md`
- `CADDY_HTTPS_ZH_CN.md`
- `NGINX_HTTPS_ZH_CN.md`
- `IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md`
- `INSTALL_TROUBLESHOOTING_ZH_CN.md`
- `UPLOAD_SECURITY_ZH_CN.md`
- `OPERATIONS_SECURITY_ZH_CN.md`
- `I18N_ZH_CN.md`
- `RELEASE_CHECKLIST_ZH_CN.md`
