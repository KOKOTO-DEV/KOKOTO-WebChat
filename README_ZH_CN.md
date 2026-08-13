# BlueMapWebChat

适用于 Bukkit/Paper/Spigot 系服务器的 Web 聊天插件。可以作为 BlueMap Web 插件显示，也可以不依赖 BlueMap，直接使用 standalone `/chat` 页面。

## 主要功能

- BlueMap 内嵌聊天面板，或 standalone Web 聊天页面
- 游戏 ↔ Web 双向聊天
- HMAC 签名的服务器间公共聊天中继、服务器彩色 Web 徽章、游戏/Discord 服务器标签
- Minecraft 点击回复(`/bmchat reply`)与 Web 发送者点击 BMChat DM(`/bmchat dm`)
- 可选将游戏 `/w`/`/msg`/`/tell` 类私聊复制到双方 Web DM
- 访客聊天、数学验证码、冷却与每分钟限制
- 通过 `/bmchat auth <code>` 绑定账号、Web 密码登录、本地管理员账号
- Web 管理/版主面板、隐藏消息、访客/IP 禁言、撤销会话
- 管理员自定义表情管理：创建、上传、重命名和删除表情文件夹/文件
- ImageEmojis-Bero 1.9.0 token、游戏回复与服务器中继兼容
- 文件/剪贴板上传，图片/视频/音频/YouTube/Shorts 预览，以及可选的 TikTok 和 X/Twitter 嵌入
- DiscordSRV 转发，Discord CDN 媒体缓存
- 回复与跳转到原消息、游戏内回复预览、置顶消息、虚拟滚动、可拖动/缩放窗口、PIP
- UI 语言: en-US, ko-KR, ja-JP, zh-CN

## 构建

```bash
mvn clean package
```

```text
target/BlueMapWebChat-4.6.2.jar
```

## 安装

1. 将 jar 放入 `plugins/`。
2. 启动一次服务器，生成 `plugins/BlueMapWebChat/config.yml`。
3. 新生成的 config 顶层默认为 `enabled: false`；在检查设置并选择启用前，只会生成配置；/bmchat reload 仍可使用。
4. 检查存储方式、保留期限、上传、预览、认证和对外公开设置后，再改为 `enabled: true`。
5. 如果要嵌入 BlueMap，保持 `web-addon.auto-install` 和 `web-addon.auto-patch-webapp-conf` 为 `true`。
6. 如果只使用 standalone，设置 `standalone-web.enabled: true`，并将 `web-addon.auto-install`、`web-addon.auto-patch-webapp-conf` 设为 `false`。
7. 重启服务器或执行 `/bmchat reload`。如果 BlueMap Web 资源没有刷新，再执行 `/bluemap reload`。


现有 `config.yml` 不会被覆盖。当 `config-version` 缺失或与正在运行的插件版本不同时，插件会比较实际配置与 JAR 内置默认配置，并生成 `plugins/BlueMapWebChat/config-migration-4.6.2.yml`。生成文件会把可直接合并的缺失设置、已变化的默认值以及最终审核用的 `config-version` 标记写成真实 YAML 设置。即使没有其他差异，也会为了配置版本管理生成该文件和 `config-version` 项。版本信息以及旧、新默认值说明只使用 `#` 注释。不会输出自定义值和废弃候选等参考列表。当 `config-version: "4.6.2"` 与插件版本一致时，配置被视为已审核并跳过比较。本次更新参见 `docs/UPGRADE_4_6_2_ZH_CN.md`，上一次更新参见 `docs/UPGRADE_4_6_1_ZH_CN.md`，之前的主要迁移参见 `docs/UPGRADE_4_6_0_ZH_CN.md`。

### 4.6.2 私信与群聊投递状态和重试

跨服务器私信在目标服务器确认消息已实际写入之前保持 `pending`；确认后才变为 `delivered`。路由、传输或超时失败会变为可重试的 `failed`，重试继续使用同一个 relay ID，因此即使只是 HTTP 响应丢失，也不会在接收端重复保存消息。Web 私信和群聊同样使用 client message ID，使浏览器在结果不确定时可以安全重试。没有服务器限定的私信名称只解析当前服务器玩家。私信与群聊的每一条消息都会在时间旁边显示已读状态。一对一私信在接收者阅读前显示 `未读`，阅读后显示 `✓`；群聊继续显示未读接收者人数，人数降至 0 时显示 `✓`。发送状态也使用短文本：处理中显示 `发送中`，失败时显示 `失败 · 重试`。

建议所有交换跨服务器私信的服务器使用 BlueMapWebChat 4.6.2 或更高版本。详见 `docs/UPGRADE_4_6_2_ZH_CN.md`。

## standalone URL

```text
http://<server-host>:8899/chat
```

## HTTPS / Caddy 推荐配置

公开服务器建议将 BlueMap 和 BlueMapWebChat 保持为内部 HTTP 服务，并通过 HTTPS 反向代理对外提供。

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"

web-addon:
  api-base-url: "/bmwc/api"


standalone-web:
  enabled: true
  path: "/chat"
  # 可选。可以与 web-addon.api-base-url 使用同一路径。
  api-base-url: "/bmwc/api"

upload:
  # 推荐留空。上传 URL 会自动跟随 /bmwc/api。
  # 旧式显式写法也可用: "/bmwc/api" 或 "/bmwc/api/uploads"
  public-base-url: ""
  # 0 = 不限制。正数会限制 upload.directory 的总文件容量。
  max-total-size-mb: 0

emoji:
  # 推荐留空。表情 URL 会自动跟随 /bmwc/api。
  # 旧式显式写法也可用: "/bmwc/api" 或 "/bmwc/api/emojis"
  public-base-url: ""
  max-total-size-mb: 64
  show-storage-usage: true
  show-storage-limit: true
```

更多内容见 `docs/CADDY_HTTPS_ZH_CN.md`。

## 常用设置

- `ui.language`: `en-US`, `ko-KR`, `ja-JP`, `zh-CN`
- `ui.theme`: `system`, `dark`, `light`, `high-contrast`
- `player-display.mode`: `name`, `display-name`, `custom-name`
- `player-display.strip-colors`: 为 `false` 时，实际聊天发送者名称会渲染 Minecraft legacy 颜色代码。system/event 消息始终会去除颜色代码。
- `commands.enabled`: Web 命令面板
- `commands.allow-all`: 允许任意控制台命令
- `commands.run-from-chat-input`: 允许从普通输入框执行 `/command`
- `ui.picture-in-picture.enabled`: 控制 PIP 按钮和 PIP 执行

## 自定义表情与游戏侧表情插件

BlueMapWebChat 会把自定义表情文件保存到 `plugins/BlueMapWebChat/emojis`。子文件夹会作为表情包处理。

默认情况下，Web→游戏聊天会保留 `:default/wave:`、`:emoji:default/wave:` 这样的自定义表情 token。若 ImageEmojis 或其他游戏侧表情插件会在 Minecraft 聊天中渲染相同的 token 文本，请使用这个默认行为。

启用 `emoji.game-link.enabled` 后，`emoji.game-link.mode` 支持 `preserve`、`link` 和 `label`。

- `preserve`: 保持原始 token 文本不变。
- `link`: 发送配置的 token 文本，并附加一个短 BM Web Chat 图片链接。
- `label`: 只发送配置的 token 文本。

`emoji.game-link.*` 只影响 Web→Minecraft 聊天。Discord 图片预览链接由单独设置控制：`discordsrv.append-web-emoji-links` 用于 Web→Discord 消息，`discordsrv.append-game-emoji-links` 会在可能的情况下编辑 DiscordSRV 的普通 Minecraft→Discord 转发消息，为 Game→Discord token 附加 URL。多个服务器共享同一 Discord 频道时，只有实际检测到本地游戏聊天的来源服务器会编辑该 DiscordSRV 消息，其他服务器不会重复添加服务器名或表情链接；接收中继的 peer 也不会把消息重新发送到 Discord。如果 DiscordSRV 已经在转发普通 Minecraft 聊天，请保持 `game-to-discord` 关闭以避免重复发布。

BM Web Chat 会在 Web 历史和服务器中继 payload 中保留规范的表情 token。启用 ImageEmojis 或 ImageEmojis-Bero 时，BMChat 会通过 reflection 读取其公开的 runtime 表情 repository，并在构建可点击的 Minecraft component 时使用接收服务器当前的 token→glyph 映射。无需硬依赖或解析资源包；无法解析的 token 仍会回退到原有的游戏侧渲染路径。

上传 GIF/JPG/JPEG/WEBP 表情时，BlueMapWebChat 还会在同一文件夹创建 PNG sidecar，以兼容只读取 PNG 文件的游戏侧表情插件。

```text
plugins/BlueMapWebChat/emojis/default/wave.gif
plugins/BlueMapWebChat/emojis/default/wave.png
```

Web UI 会继续使用原始文件，因此 GIF 动画会保留。如果游戏侧表情插件监视同一个表情目录，它可以使用 PNG sidecar。添加或更改表情后，请运行该插件的 reload 命令。

ImageEmojis-Bero 1.9.0 的共用目录、权限、命令转换、服务器中继及故障排除参见 [`docs/IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md`](docs/IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md)。

## YouTube Shorts、TikTok 和 X/Twitter 预览

YouTube Shorts URL 由普通 YouTube 预览处理，使用竖屏播放器并循环播放，默认启用。TikTok 和 X/Twitter 作为可选 social embed 提供，因为会从用户浏览器加载第三方内容，所以默认关闭。TikTok 使用官方 `player/v1` iframe，并在聊天面板中隐藏较长的描述/音乐信息，以避免内部滚动条；完整信息可通过原始 TikTok 链接打开。

```yaml
preview:
  youtube-embed-enabled: true
  social-embeds:
    enabled: true
    click-to-load: true
    max-embeds-per-message: 2
    tiktok:
      enabled: false
    x:
      enabled: false
```

只有在允许用户浏览器发起第三方 embed 请求的服务器上，才建议启用 TikTok 或 X/Twitter。公开服务器建议保持 `click-to-load: true`，让第三方内容只在用户打开预览后加载。

## 命令

```text
/bmchat dm <player> <message>
/bmchat reply <messageId> <message>
/bmchat auth <code>
/bmchat password <newPassword>
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

## 权限

```text
bluemapwebchat.auth
bluemapwebchat.webchat
bluemapwebchat.dm
bluemapwebchat.reply
bluemapwebchat.group
bluemapwebchat.admin
bluemapwebchat.update.notify
```

## 文档

- `docs/USER_MANUAL_ZH_CN.md` - 所有功能的完整用户与运维手册
- `docs/CONFIGURATION_ZH_CN.md`
- `docs/SERVER_RELAY_ZH_CN.md` - 服务器间公共聊天中继
- `docs/UPGRADE_4_6_2_ZH_CN.md` - 4.6.1→4.6.2 升级
- `docs/UPGRADE_4_6_1_ZH_CN.md` - 4.6.0→4.6.1 升级
- `docs/UPGRADE_4_6_0_ZH_CN.md` - 4.5.5→4.6.0 配置/数据库升级
- `docs/CADDY_HTTPS_ZH_CN.md`
- `docs/I18N_ZH_CN.md`
- `docs/INSTALL_TROUBLESHOOTING_ZH_CN.md`
- `docs/UPLOAD_SECURITY_ZH_CN.md`
- `docs/RELEASE_CHECKLIST_ZH_CN.md`
- `docs/STANDALONE_REVIEW_ZH_CN.md`
- `docs/OPERATIONS_SECURITY_ZH_CN.md`

字体说明：已安装字体需要输入 CSS font-family 名称。聊天设置中的检测按钮可在不请求本地字体权限的情况下，估算当前浏览器是否可使用该名称。


URL 设置说明：HTTPS 反向代理模式下，将 `web-addon.api-base-url` 设为 `/bmwc/api` 这样的公开 API 路径。`standalone-web.api-base-url`、`upload.public-base-url`、`emoji.public-base-url` 通常留空。留空时，standalone 会复用 web-addon API base，上传/表情会自动追加 `/uploads` 和 `/emojis`。也支持显式值 `/bmwc/api`、`/bmwc/api/uploads`、`/bmwc/api/emojis`。不带前导 `/` 的相对值会在 `http.cors-origin` 为实际 origin 时基于该 origin 解析。

## SQLite 历史搜索

使用 SQLite 历史存储时，可以通过聊天面板右上角的浮动区域的放大镜按钮搜索消息内容和发送者。搜索选项也可指定日期/时间范围、发送者、来源以及是否包含系统/事件消息。搜索结果会显示在可滚动列表中，并遵循聊天主题和字体设置。点击搜索结果会使用现有的周边历史加载跳转到对应消息。带有 i18n 键的系统/事件消息会尽可能按当前选择的 Web UI 语言搜索和显示。仅用 `search.result-limit` 同时控制 Web UI 结果数量和 `/history/search` API 限制，没有单独的内部最大值。10000 或 100000 这类非常大的值也会被接受，但可能导致搜索变慢、响应体变大，并显著增加 CPU、内存和数据库负载。


## 聊天记录保留期

新生成的 config 顶层默认为 `enabled: false`，因此在检查保留期限和清理相关设置并改为 `enabled: true` 前，不会执行自动清理任务。请按服务器策略确认聊天记录、上传文件和外部媒体缓存的保留期限后再启用。

## 1:1 私信会话线程

启用 `direct-message.enabled` 后，可以使用 1:1 会话线程式消息箱。目标包括已有 UUID/名称记录的已关联或曾加入玩家，以及中继消息中带有玩家 UUID 的其他服务器发送者。收到的显示名和真实 Minecraft 名会加入 Web DM 的新会话对象搜索，因此无需单独添加 DM 按钮即可通过普通搜索开始会话。没有玩家 UUID 的访客和 Discord 发送者不会被加入。A→B 与 B→A 会使用同一个线程，存储按 UUID 进行，UI 会尽可能显示为 `显示名 (真实账号名)`。

DM 使用独立于公开聊天历史的专用存储。`direct-message.storage: auto` 会在公开聊天使用 `jsonl` 存储时让 DM 也使用 JSONL，其他情况下使用 SQLite。也可以显式设置为 `sqlite` 或 `jsonl`，并分别使用 `direct-message.sqlite-file` 或 `direct-message.jsonl-file`。`direct-message.retention-days: 0` 表示无保留期限；其他值会显示在 DM 窗口标题旁作为保留期限，超过该天数的 DM 原文会被物理删除。`direct-message.max-messages-per-thread: 0` 表示不按线程消息数清理。`direct-message.confirm-hide` 控制 Web UI 在从自己视图隐藏 DM 前是否显示确认框。由于私信会保存在服务器上，此功能默认关闭，建议先确定服务器保留策略后再启用。


启用 `direct-message.capture-game-whispers` 后，游戏 `/w`、`/msg`、`/tell` 等会复制到相同 Web DM 会话。点击同服游戏发送者名称会建议 `/w <真实名称> `；Web 发送者会建议 `/bmchat dm <真实名称> `；其他服务器的游戏发送者会建议 `/bmchat dm <真实名称>@<server-id> `。在 `/w`、`/msg`、`/tell`、`/whisper`、`/m`、`/pm`、`/message`、`/t` 中使用 `名称@server-id` 目标时，也会通过同一跨服务器 BMChat DM relay 发送。

## 群组聊天室

启用 `group-chat.enabled` 后，可以使用 Web 群组聊天室功能。用户可以创建房间、选择公开/私密、设置可选房间密码、邀请已知玩家、接受或拒绝邀请、退出房间、从自己的列表隐藏/恢复房间、修改房间设置、踢出/封禁/解除封禁成员、转移房主，并从 Web UI 发送消息。公开房间会显示在房间列表中；私密房间仅限邀请加入。房间密码不会明文保存，而是使用 PBKDF2 哈希保存。

群组聊天使用独立的 SQLite 存储（`group-chat.sqlite-file`，默认 `group-messages.db`）。`group-chat.retention-days: 0` 表示无时间限制；正数会显示在群组聊天标题旁，并在超过该天数后物理删除旧群组消息。`group-chat.max-messages-per-room: 0` 表示不按数量清理。本版本仍以 Web 为主，尚未包含游戏侧 `/bmchat group` 命令、房间静音、超出房主/成员操作的详细角色管理 UI 和群组 JSONL 存储。


### 私信/群组聊天元数据超级管理员

在 `config.yml` 的 `private-chat-super-admins` 中填写准确 UUID 或 Minecraft 名后，可查看用于管理/容量检查的私信与群聊元数据。默认视图显示标题/参与者、消息数、大致存储大小、保留状态和清理预览。只有同时设置 `direct-message.admin-audit.enabled: true` 时，同一明确列出的账号才能只读打开私信正文；普通 ADMIN/MODERATOR 角色不会自动获得权限，每次分页读取都会写入审计日志。超级管理员还可以锁定会话或将其从自动删除中排除。

会影响管理状态的操作默认会按日期追加到 `plugins/BlueMapWebChat/audit` 下的文本审计日志中。审计日志供服务器运营者查看，不会显示在 Web UI 中。


注意：`standalone-web.app-name` / `standalone-web.app-short-name` 可更改移动端主屏幕 Web App 名称，`web-push.notification-title` 可更改默认推送标题。`web-push.notification-title` 留空时使用 `standalone-web.app-name`。Android/桌面浏览器在 HTTPS 与 Push API 可用时，可从 BlueMap addon 或 standalone 页面启用推送。iOS/iPadOS 请使用已添加到主屏幕并作为 Web App 打开的页面，而不是普通浏览器标签页。
