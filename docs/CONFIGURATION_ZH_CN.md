# BlueMapWebChat 配置参考

本文说明 `plugins/BlueMapWebChat/config.yml`。

## 配置版本与迁移片段

`config-version` 不是自动转换架构的编号，而是管理员已完成配置审核的标记。现有设置值不会被自动覆盖。startup/reload 时会把已知顶层配置块按 bundled 默认顺序重新排列，同时保留每个块的当前文本、设置值和用户自定义注释；默认配置中没有的顶层块会按原顺序保留在最后。每次检查已有配置时，插件都会生成 `config-reference-<plugin-version>.yml`，内容是当前 JAR 内置完整默认 `config.yml` 的原样副本，并保留全部内置注释。这个完整 reference 不依赖检测到的旧版本，因此非常旧或没有版本标记的配置也可以直接与当前默认配置比较。如果 `config-version` 缺失或不同，还会另外生成 `config-migration-<plugin-version>.yml`，列出缺失设置、已变化的默认值和最终审核标记。`message-tokens.custom: {}` 这类空 map 也按真实设置处理，缺失时会写入 migration。版本标记匹配时会跳过 migration 比较，但完整 reference 文件仍保持为当前默认内容。另外，如果真实 `config.yml` 中的注释仍与旧版 BMWC 内置注释完全一致，则可能刷新为当前说明，但不会修改设置值或用户自定义注释。生成的 migration 文件末尾还会以注释形式附上当前 `config.yml` 与完整 reference 的文本 diff。相同的行不会输出。每个差异先显示文件名，再在下一行显示 `Line` 或 `Lines`，下面只显示实际不同的内容。差异源行只在行首直接加 `#`，因此会原样保留 YAML 自身的缩进；仅 reference 中存在的连续块还会另行显示在当前 config 中的插入位置。行号以生成 migration 报告时的 `config.yml` 为准，编辑配置后执行 `/bmchat reload` 即可按当前行号重新生成。请手动合并需要的值，并仅在审核完成后合并 `config-version`。

## 总开关

新生成的 config 顶层默认为 `enabled: false`。在此状态下，BlueMapWebChat 只会生成/读取配置，/bmchat reload 仍可使用，但不会启动 Web/聊天服务、监听器、Discord 集成、私信存储、插件网页安装、上传/表情初始化或清理任务。已有 config 如果没有此键，为了升级兼容会视为已启用。请先检查存储方式、保留期限、上传、预览、认证和对外公开设置，再改为 `enabled: true`。

## 更新检查

```yaml
update-check:
  enabled: true
```

启用后，BlueMapWebChat 会在后台检查 Modrinth 上的最新正式版本。OP 或拥有 `bluemapwebchat.update.notify` 权限的玩家登录时会按限频规则重新查询 Modrinth，因此新版本检测不再只依赖定时查询结果。检查间隔、发布通道、进服提示延迟以及 Modrinth/CurseForge 下载链接仍使用内置默认值。更新查询失败不会阻止插件启动，并会记录为警告日志。

## 部署模式

### BlueMap 插件模式

```yaml
web-addon:
  auto-install: true
  auto-patch-webapp-conf: true
```

将资源安装到 `addons/bluemap-web-chat`，并更新 BlueMap `webapp.conf`。

### 仅 standalone

```yaml
web-addon:
  auto-install: false
  auto-patch-webapp-conf: false

standalone-web:
  enabled: true
  path: "/chat"
```

访问 `http://<server-host>:8899/chat`。

### HTTPS 反向代理

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
  # 推荐留空。会跟随 web-addon.api-base-url。
  # 也可以显式设置同一个公开 API 路径，例如 "/bmwc/api"。
  api-base-url: ""

upload:
  # 推荐留空。上传 URL 会自动跟随当前 API base。
  # 旧式显式值也可用: "/bmwc/api" 或 "/bmwc/api/uploads"
  public-base-url: ""

emoji:
  # 推荐留空。表情 URL 会自动跟随当前 API base。
  # 旧式显式值也可用: "/bmwc/api" 或 "/bmwc/api/emojis"
  public-base-url: ""
```

### 公开 URL 选项规则

- `http.path-prefix` 是插件内部 HTTP API 路径。通常保持默认 `/api` 不变。
- `web-addon.api-base-url` 是 BlueMap 内嵌聊天使用的公开 API base。HTTPS 反向代理中通常设为 `/bmwc/api`。
- `standalone-web.api-base-url` 通常留空。留空时会复用 `web-addon.api-base-url`。例如 `/bmwc/chat` 会使用 `/bmwc/api`。需要时也可以显式设置同一个 `/bmwc/api`。
- `upload.public-base-url` 通常留空。留空时使用当前 API base 加 `/uploads`，例如 `/bmwc/api/uploads`。
- `emoji.public-base-url` 通常留空。留空时使用当前 API base 加 `/emojis`，例如 `/bmwc/api/emojis`。
- 也支持显式值。设置 `/bmwc/api` 时，upload 会自动追加 `/uploads`，emoji 会自动追加 `/emojis`；`/bmwc/api/uploads` 和 `/bmwc/api/emojis` 会原样使用。
- 不带前导 `/` 的相对值，例如 `bmwc/api`、`bmwc/api/uploads`、`bmwc/api/emojis`，会在 `http.cors-origin` 为实际 origin 时自动加上该 origin。若 `cors-origin: "*"`，则按同源绝对路径 `/bmwc/api...` 处理。
- `https://map.example.com/bmwc/api` 这样的完整 URL 会原样使用。

## 聊天记录存储

聊天记录通过 `chat.history-storage` 选择 `memory`、`jsonl` 或 `sqlite`。`chat.history-size` 和 `chat.history-retention-days` 在三种模式中共用。`0` 表示不限制数量/期限。新生成的 config 顶层默认为 `enabled: false`，因此在检查这些值并设置 `enabled: true` 前不会执行清理任务。如果服务器策略需要自动清理旧聊天，请设置正数保留天数，例如 `30` 或 `90`。上传和外部媒体缓存保留设置也按同样方式工作。`chat.history-file` 仅用于 JSONL，`chat.history-sqlite-file` 仅用于 SQLite。

## 消息令牌

`message-tokens.enabled` 启用以冒号包围的管理员自定义文本/控制 alias。配置中只写不带冒号的 alias 名，例如 `enter` 在聊天中输入为 `:enter:`。内置 alias 只提供英文默认值，管理员可改成或追加任意语言。未注册 alias 保持原样，因此不会破坏自定义/ImageEmojis 令牌。

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

- `max-replacements-per-message: 0` 表示不限制成功的 message-token 替换次数。
- `newline` 插入一个换行。
- `blank-line` 插入两个换行，从而留下一个空行。
- `tab.spaces` 限制为 1～16，并插入空格而不是 literal tab 控制字符。
- `custom` 只支持可打印文本替换，control character/newline 会被移除。
- `:\n:` 这类 backslash escape 不会被解释。
- Minecraft 中普通 CR/LF 继续按原有规则压成单行。只有 `newline`/`blank-line` alias 产生的换行会单独跟踪，并在最终游戏投递时作为明确的多条聊天行发送。服务器中继也要求接收端具备相同的 4.7.0 token-line 支持。

可把 `custom: {}` 替换为下面的块来添加可打印文本替换：

```yaml
message-tokens:
  custom:
    separator:
      aliases: [separator, divider]
      replacement: "────────────"
```

## 1:1 私信会话线程

```yaml
direct-message:
  enabled: false
  allow-web-send: true
  allow-game-send: true
  capture-game-whispers: true

direct-message:
  admin-audit:
    enabled: false
```

`group-chat.admin-audit.enabled` 是 4.6.3 新增的独立、默认关闭的群聊正文访问开关。即使启用，账号仍必须列在 `private-chat-super-admins` 中。管理员视图为只读，不要求房间成员身份，也不会加入房间或更新已读状态；每次分页读取都会记录为 `admin.group-audit-read`，且不会把消息正文复制到审计日志。

`direct-message.admin-audit.enabled` 是默认关闭的独立正文访问开关。即使启用，也只有同时列在 `private-chat-super-admins` 中的账号可以在只读审计视图中打开私信正文。每次分页读取会写入审计日志，但正文不会复制到日志。普通 ADMIN/MODERATOR 角色不会自动获得权限。

`capture-game-whispers` 会把未取消的 `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` 复制到发送者和接收者的 BMChat DM 会话。它不会重新发送或替换 Minecraft 私聊。Bukkit 无法统一获得所有私聊插件的最终成功结果，因此以格式正确、目标为已知玩家的命令作为记录条件。

## 0 表示无限制/无最大值的选项

- `chat.history-size`
- `chat.history-retention-days`
- `chat.history-page-size`
- `chat.max-message-length`
- `chat.max-url-message-length`
- `upload.max-uploads-per-minute`
- `upload.max-file-size-mb`
- `upload.max-files-per-message`
- `ui.image-preview-max-per-message`
- `ui.image-preview-max-height`
- `ui.max-width`
- `ui.max-height`
- `preview.youtube-max-embeds-per-message`
- `preview.social-embeds.max-embeds-per-message`
- `preview.external-media-cache-max-size-mb`
- `pinned.max-pins`
- `pinned.show-to-logged-out`
- `commands.max-length`
- `direct-message.retention-days`
- `direct-message.max-messages-per-thread`
- `direct-message.max-message-length`

## 访客聊天限制

```yaml
guest:
  cooldown-seconds: 6
  max-messages-per-minute: 50
```

访客聊天同时受 `cooldown-seconds` 和 `max-messages-per-minute` 限制。每分钟消息数默认值为 `50`。已有服务器的配置文件不会自动覆盖；如果要在现有安装中使用新的默认值，请手动更新 `plugins/BlueMapWebChat/config.yml`。

## Web→Minecraft 名称 hover

```yaml
chat:
  game-name-hover:
    enabled: false
    text: "&f{real}"
```

当 Web 聊天转发到 Minecraft 聊天时，`chat.game-name-hover.enabled` 可以在显示名称上添加 hover 提示。仅当 `player-display.mode` 为 `display-name` 或 `custom-name`，且显示名称与真实 Minecraft 账号名不同时才会应用。该提示使用 Spigot/Bungee 聊天组件，因此不是 Paper 专用，在 Spigot/Paper 兼容服务器上可用。`text` 支持 Minecraft legacy 颜色代码以及 `{display}`, `{real}`, `{uuid}`, `{source}` 占位符。

## Minecraft 聊天回复与发送者点击

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

点击非 URL 正文会建议 `/bmchat reply <messageId> `，URL 片段仍优先打开链接。`local-game-chat` 让普通本地游戏聊天也可点击回复；若其他聊天格式插件必须独占最终渲染，请关闭。点击同服游戏发送者名称会建议 `/w <真实名称> `；已关联 Web 发送者和其他服务器的游戏发送者会建议 `/bmchat dm <真实名称> `。

游戏回复会保留玩家输入的自定义表情 token，用于 Web 历史和服务器中继；在发送端服务器的 Minecraft 输出中，则复用游戏侧表情插件处理后的命令正文来显示表情。若没有处理后的 glyph 且 `emoji.game-link.mode` 为 `preserve`，已识别 token 会以普通 Bukkit 聊天行输出以兼容游戏侧渲染器；该兼容行无法附带 BMChat 的点击和 hover metadata。



`server-relay` 用于连接多个 BlueMapWebChat 服务器的公共聊天。游戏、已关联 Web 用户和访客消息可发送到远端服务器的 Web 聊天与 Minecraft 聊天，同时保留消息 ID、回复关系、发送者和来源服务器信息。

## 服务器中继配置

服务器 1：

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "服务器 1"
  shared-secret: "两台服务器使用同一个足够长的随机密钥"
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
    - id: "server3"
      url: "https://server3.example.com/bmwc/api"
      secret: ""
      enabled: true
```

服务器 3 使用 `server-id: "server3"`，并在 `peers` 中添加 `id: "server1"` 和服务器 1 的公开 API URL。双方必须互相登记；接收方 peer ID 必须与发送方 `server-id` 完全一致，并且每台服务器的 ID 必须唯一。

## HTTPS 与反向代理

`url` 是远端可公开访问的 BMChat API base，`/relay/receive` 会自动追加。

```text
配置: https://server3.example.com/bmwc/api
请求: https://server3.example.com/bmwc/api/relay/receive
```

公开 HTTPS 路由必须把包含 `/relay/receive` POST 在内的整个 API 路径转发到内部 BMChat HTTP 监听器。已有 HTTPS 前端时无需公开 8899 端口。代理必须保留 `X-BMWC-Relay-Version`, `X-BMWC-Relay-From`, `X-BMWC-Relay-Timestamp`, `X-BMWC-Relay-Signature`。自签名证书需要加入 Java trust store，否则会在 TLS 验证阶段失败。

## 密钥

- `shared-secret` 是所有 peer 的默认密钥。
- `peers[].secret` 是单独连接的覆盖密钥。
- 两台服务器可使用相同的长随机 `shared-secret`，并保持 peer `secret: ""`。
- peer 密钥和公共密钥都为空时，该 peer 会从活动列表排除。

## 多服务器拓扑

全网状拓扑让每台服务器登记所有其他服务器；Hub 拓扑让 leaf 只连接 hub，hub 登记所有 leaf。relay ID 去重、来源抑制、上一跳排除和 `max-hops` 防止循环。没有持久离线队列；peer 离线期间的消息不会稍后补发。

## reload 与诊断

`/bmchat reload` 会关闭旧 relay，并使用当前配置重新创建。中继是每条消息一次 HTTP(S) 请求，不是永久连接，因此没有单独的“重新连接”操作。

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

如果 `activePeers` 少于配置数量，警告会指出重复 ID、自身 ID、空/非法 URL、不支持的 scheme 或缺少密钥等原因。

## HTTP 错误

- `403 unknown_peer`: 接收服务器的活动 peer 中没有发送方的准确 `server-id`。
- `401 bad_signature`: 实际密钥不同，或代理修改了正文/头。
- `401 expired_request`: 两台服务器时钟差超过 `max-clock-skew-seconds`。
- `404 relay_disabled`: 接收端未启用，或代理转发到了错误实例/路径。
- `426 unsupported_protocol`: 两端中继协议版本不兼容。

修改接收端 peer 或密钥后，也必须在接收端执行 `/bmchat reload`。

## 显示区分

Web 聊天只为其他服务器的消息显示基于 `originServerId` 的固定颜色徽章，当前服务器自身的徽章会省略。Web→游戏时，仅远端消息的旧格式缺少 `{server}` / `{server_id}` 才会自动添加 `[server-name]`。Discord 是共享外部频道，因此继续保留服务器标识。为避免 DiscordSRV 循环和系统事件重复，`sources.discord` 与 `sources.system` 默认关闭。

## Discord 转发选项

```yaml
discordsrv:
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-to-discord-format: "[{server}] {sender}: {message}"
  game-to-discord: false
  append-web-emoji-links: true
  append-game-emoji-links: true
  reply-relay:
    enabled: false
```

格式支持 `{server}`, `{server_id}`, `{sender}`, `{name}`, `{role}`, `{source}`, `{message}`, `{channel}`。中继启用时，旧格式若没有服务器占位符会自动添加 `[server-name]`。多个服务器共享同一 Discord 频道时，只有实际检测到本地 Minecraft 聊天的来源服务器 BMChat 才会给 DiscordSRV 的普通游戏转发添加服务器名和表情链接，其他服务器不会编辑该消息。接收端不会把服务器中继消息再次发送到 Discord，因此来源服务器的 Discord 集成关闭或失败时，不存在由其他服务器代发的 relay-only fallback。DiscordSRV 已转发普通游戏聊天时，应关闭 `game-to-discord` 以避免重复。

## 置顶消息

`pinned.show-to-logged-out` 控制未登录访问者是否能看到置顶消息。如需仅登录用户可见，请设为 `false`。

## 固定/删除显示开关

为避免误点，单条消息上的固定/删除按钮默认隐藏。ADMIN/MOD 用户可以在管理面板中，使用“清空 Web 历史”按钮旁边的固定/删除开关来显示这些按钮。该开关不会持久保存，刷新后会恢复为关闭。

## UI

```yaml
ui:
  language: "en-US"        # en-US, ko-KR, ja-JP, zh-CN
  language-fallback: "en-US"
  theme: "system"          # system, dark, light, high-contrast
  opacity: 0.92
```

用户在浏览器中的显示设置保存在 localStorage。

## 玩家名称

```yaml
player-display:
  mode: "name"             # name, display-name, custom-name
  strip-colors: true
```

当 `strip-colors: false` 时，Web UI 只会为实际聊天发送者名称渲染 Minecraft legacy 颜色代码。系统/事件消息和 Discord 输出仍会移除原始 Minecraft 颜色代码。已保存的显示名在再次使用时也会按当前 `strip-colors` 设置进行规范化。

## 自定义表情与游戏侧表情插件

BlueMapWebChat 会把自定义表情文件保存到 `plugins/BlueMapWebChat/emojis`。子文件夹会作为表情包处理。

默认情况下，`emoji.game-link.enabled` 为 `false`，因此 Web→游戏消息会保留 `:pack/name:`、`:emoji:pack/name:` 这样的自定义表情 token。若 ImageEmojis 或其他游戏侧表情插件会在 Minecraft 聊天中渲染 token，请使用这个默认行为。

当 `emoji.game-link.enabled` 为 `true` 时，`emoji.game-link.mode` 支持 `preserve`、`link` 和 `label`。

- `preserve`: 即使 game-link 已启用，也强制保持 token 不变。
- `link`: 发送 `label-format` 文本，并附加一个短 BM Web Chat 图片链接。
- `label`: 只发送 `label-format` 文本。

`emoji.game-link.*` 只影响 Web→Minecraft 聊天。Discord 图片预览链接分别由 Web→Discord 的 `discordsrv.append-web-emoji-links` 和 Game→Discord 的 `discordsrv.append-game-emoji-links` 控制。`append-game-emoji-links` 会在可能的情况下编辑 DiscordSRV 的普通 Minecraft→Discord 转发消息；只有当你希望 BM Web Chat 直接发送游戏聊天到 Discord 时才需要 `game-to-discord`。

BM Web Chat 会在 Web 历史和中继 payload 中保留规范的表情 token。启用 ImageEmojis 或 ImageEmojis-Bero 时，BMChat 会通过 reflection 读取其公开的 runtime 表情 repository，并在构建可点击的 Minecraft component 前把 token 转换为接收服务器当前使用的 glyph。该机制不增加硬依赖，也不会解析资源包。

对于交互式聊天行，BMChat 会先插入 ImageEmojis glyph，再构建发送者、回复和 URL 点击事件，因此表情显示与可点击链接可以同时工作。只有接收服务器无法解析的已知 token 才会使用单行 plain Bukkit fallback 兼容其他游戏侧 renderer；该 fallback 无法携带 BMChat 的点击或 hover metadata。

`default-pack` 和 `aliases` 可用于把扁平的游戏侧 token 映射回 BM Web Chat 的 pack/name id。例如：

```yaml
emoji:
  game-link:
    default-pack: "default"
    aliases:
      wave: "default/wave"
```

GIF/JPG/JPEG/WEBP 表情原文件会自动获得同文件夹 PNG sidecar，以兼容只读取 PNG 文件的游戏侧表情插件。Web UI 会继续使用原始文件，因此 GIF 动画会保留。

### ImageEmojis-Bero 1.9.0

兼容目标为 [ImageEmojis-Bero 1.9.0](https://github.com/KOKOTO-DEV/ImageEmojis-Bero)。若 Web 与游戏共用表情文件，请设置 `emojisFolder: "/BlueMapWebChat/emojis"`、`templateFormat: ":<emoji>:"`、`replaceInCommands: true`，并授予用户 `imageemojis.use`。完整设置、中继行为、重新加载顺序、Discord 注意事项与故障排除参见 `IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md`。

## 命令面板

```yaml
commands:
  enabled: false
  allow-all: false
  min-role: ADMIN
  run-from-chat-input: false
  max-length: 0
```

`allow-all: true` 允许 Web UI 执行任意控制台命令，因此只应在 HTTPS 和强认证下使用。`run-from-chat-input: false` 时，命令只能从按钮/弹窗执行。

## 媒体预览高度与滚动稳定性

`ui.image-preview-max-height` 用于限制图片、GIF、视频和 iframe 类预览的显示高度。推荐范围为 `640-720`，默认值为 `720`。

```yaml
ui:
  image-preview-max-height: 720
```

设置为 `0` 表示不限制高度。但非常大的媒体或无限制预览可能会在媒体加载完成时造成可见的滚动跳动，尤其是在同时使用 virtual scroll 和包含大量媒体的长聊天记录时。

## 预览

```yaml
preview:
  youtube-embed-enabled: true
  youtube-click-to-load: true
  media-click-to-load: true
  youtube-nocookie: true
  youtube-remember-expanded: true
  youtube-autoplay-on-open: false
  youtube-max-embeds-per-message: 1

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
      hide-media: false
      hide-thread: true
```

YouTube Shorts 通过普通 YouTube 预览路径处理。Shorts 会以竖屏播放器显示，并使用 YouTube loop 参数。

TikTok 和 X/Twitter 会从查看者的浏览器加载第三方内容，因此是可选功能。只有在服务器策略允许第三方 embed 请求时才建议启用。公开服务器建议保持 `social-embeds.click-to-load: true`，让外部播放器只在用户打开预览后加载。

TikTok 使用官方 `player/v1` iframe，并应用 `description=0`、`music_info=0`。这样可以避免帖子描述或音乐信息长度变化导致聊天面板内出现内部滚动条。完整帖子信息可通过播放器下方的原始 TikTok 链接打开。

将 `youtube-click-to-load` 或 `media-click-to-load` 设为 `false` 会立即渲染对应预览。自动播放仍受浏览器策略控制。


## 浏览器通知和 Web Push

`notifications` 控制浏览器通知与移动/后台 Web Push 共用的默认值和服务器端允许上限。`notifications.enabled` 是两个投递路径的单一默认开关；旧的 `browser-notifications.*` 和 `web-push.notify-*` 键只作为迁移/兼容输入读取。`notify-*` 值保持 `true` 时每个用户/浏览器可在聊天设置中自行开关；设为 `false` 时，即使用户启用，该通知类型也会被阻止。允许 `notify-system` 时，用户可在聊天设置中选择全部服务器通知、仅加入/退出，或关闭。`notify-keywords` 控制用户自定义关键词提醒；关键词列表按浏览器/设备保存，并且仅同步到该设备的 Web Push 订阅用于后台匹配。

`web-push` 仅保存 Web Push 投递设置，例如 VAPID key、subject、订阅文件、TTL 和默认推送标题。当 HTTPS 或 localhost、浏览器通知权限以及 Service Worker / Push API 支持都满足时，可发送后台/移动推送通知。Android/desktop 浏览器在当前 origin 支持 Service Worker + Push API 时，可从 BlueMap addon 或 standalone 页面启用推送。普通 iOS/iPadOS 浏览器标签页不支持 Web Push；只能在把页面添加到主屏幕后作为 Web App 打开时尝试使用，未支持的行为应视为平台限制。若 `notifications.enabled: true` 且 VAPID key 留空，插件会在 `web-push-vapid.properties` 中生成持久 key。`web-push.subject` 建议使用真实的 VAPID 联系/运营者识别 URI，例如 `mailto:admin@example.com` 或 `https://map.example.com`。不建议使用任意文本；某些 push 服务可能拒绝或降低信任度。移动端“可能是垃圾信息”等警告由浏览器/操作系统控制，插件无法关闭。使用稳定的 HTTPS 域名、有意义的通知标题/正文、保守的通知过滤设置，并避免频繁测试通知，可降低出现概率。

## PIP

```yaml
ui:
  picture-in-picture:
    enabled: false
```

这一个选项同时控制 PIP 按钮和 PIP 执行。浏览器的 URL/关闭按钮 UI、OS 级窗口透明度以及外部 PIP 窗口移动由浏览器/操作系统控制，而不是由聊天设置标题控制。

## 登录失败限制

`security.login-fail-limit`、`security.login-fail-window-seconds` 和 `security.login-lock-seconds` 用于限制 Web 密码登录的连续失败。设置 `login-fail-limit: 0` 可禁用限制。该设置只影响 Web 密码登录。
## 链接代码生成限制

`auth.link-code-cooldown-seconds` 和 `auth.link-code-max-per-minute` 用于限制 Web UI 按远程 IP 生成 `/bmchat auth <code>` 链接代码的频率。将任一值设为 `0` 可禁用对应限制。


### 上传存储容量上限

`upload.max-total-size-mb` 用于限制 `upload.directory` 直属普通文件的总容量。默认值 `0` 表示不限制。新的上传会超过限制时，BlueMapWebChat 会先删除最旧的未引用上传文件；仍被聊天记录、SQLite 历史、DM/群聊消息或保留的置顶消息引用的文件会被保留。清理后仍空间不足时，上传会被拒绝。

### 表情容量显示

`emoji.max-total-size-mb` 用于限制自定义表情的总容量。超过限制时，管理员上传界面会显示警告。`emoji.show-storage-usage` 控制是否显示当前表情容量，`emoji.show-storage-limit` 控制是否显示总容量限制。



## UI 时区

`ui.time-zone` 用于指定聊天时间显示的时区。`local` 表示使用浏览器/设备本地时区，也可以使用 `UTC` 或 `Asia/Seoul` 等 IANA 时区。无效值会在 Web UI 中回退到本地时间。

## HTTP 代理 / 客户端 IP

`http.trusted-proxies` 用于指定哪些代理的 `X-Forwarded-For` 可以被信任。直接公开 HTTP 时请保持为空；如果同一台服务器上使用 Caddy/Nginx，请将 `127.0.0.1` 和 `::1` 写成块状 YAML 列表。`http.log-client-ip-resolution: true` 只建议临时开启，用于在服务器控制台和 `logs/latest.log` 中确认 socket IP、forwarded 头和最终解析出的客户端 IP。完整检查步骤见 `docs/OPERATIONS_SECURITY_ZH_CN.md`。

## SSE 连接数限制

`security.max-sse-connections-per-ip` 和 `security.max-sse-connections-total` 用于限制长期保持的 `/stream` 连接数。各项设置为 `0` 可禁用对应限制。



`ui.text-color` 设置聊天正文的默认文字颜色。`ui.ui-text-color` 设置 UI 文字/图标颜色，例如角色标记、Web/Game 来源、时间、输入框占位文字、上传/命令按钮和置顶消息标签。留空时跟随当前主题。用户也可以在聊天设置中按浏览器覆盖这些颜色。

```yaml
ui:
  text-color: ""          # 正文跟随主题
  ui-text-color: ""       # UI 标签/图标跟随主题
  # text-color: "#f4f4f4"
  # ui-text-color: "#b8d8ff"
```

`ui.input-background-color` 用于全局固定输入框背景颜色。留空时跟随所选主题。用户也可以在聊天设置中按浏览器覆盖此颜色。

```yaml
ui:
  input-background-color: ""      # 主题默认值
  # input-background-color: "#1e1e24"
```

### 系统消息翻译

内置 announcement 和 Web 命令结果消息包含 i18n 键。请将 `announcements.*.message` 保留为回退/自定义文本。语言文件中存在对应键时，查看者会看到所选语言的翻译文本。

折叠的置顶消息栏文字也会使用配置的聊天字体和消息字号。

### Text shadow / readability

- `ui.text-shadow-mode`: `none`、`auto`、`dark`、`light` 或 `custom`。用于在自定义文字/背景颜色对比度较低时提高可读性。
- `ui.text-shadow-custom`: `custom` 模式使用的 CSS `text-shadow` 值。聊天设置界面会以颜色选择器以及水平偏移、垂直偏移、模糊、不透明度滑块进行编辑；保存值仍为标准 CSS 格式，例如 `0 1px 2px rgba(0, 0, 0, 0.85)`。

> 主题也可以在每个浏览器的聊天设置中更改。切换主题时，文字颜色、背景颜色和阴影等外观设置会重置为该主题默认值。


管理员自定义表情说明：重命名表情文件或文件夹会改变 `:emoji:pack/name:` 标记。引用旧标记的既有聊天消息可能不再渲染，除非保留旧文件/文件夹名称。

## 消息搜索

启用存储历史记录时，可以通过聊天面板右上角的浮动区域的放大镜按钮和 `/history/search` API 搜索消息内容和发送者。搜索选项可按日期/时间范围、发送者、来源以及是否包含系统/事件消息进行筛选。搜索结果会显示在可滚动列表中，并遵循聊天主题和字体设置。点击搜索结果会使用现有的周边历史加载跳转到对应消息。带有 i18n 键的系统/事件消息会尽可能按请求的 Web UI 语言搜索和显示。 可通过 `search.enabled` 启用/禁用搜索，且仅用 `search.result-limit` 同时控制 Web UI 结果数量和 `/history/search` API 限制。没有单独的内部最大值：设置为 2000 时最多返回 2000 条，设置为 10 时最多返回 10 条。10000 或 100000 这类非常大的值也会被接受，但可能导致搜索变慢、响应体变大，并显著增加 CPU、内存和数据库负载。默认值为 50，普通使用建议 50-200。旧版 config.yml 需要手动添加这些项目，或与默认配置合并。

## 群组聊天

`group-chat.enabled` 启用 Web 群组聊天功能。支持公开/私密房间、哈希保存的可选密码、邀请、退出房间、隐藏/恢复房间、房间设置、未读追踪、按用户隐藏消息、踢出/封禁/解除封禁成员以及转移房主。群组消息保存在 `group-chat.sqlite-file`（默认 `group-messages.db`）中。`group-chat.retention-days: 0` 表示不按时间清理；正数会物理删除更旧的群组消息。


## 私信/群组聊天元数据超级管理员

`private-chat-super-admins: []` 用于填写可查看私信/群聊元数据的准确 UUID 或 Minecraft 名。元数据视图显示参与者/标题、消息数、大致存储大小、保留状态和管理操作。只有在 `direct-message.admin-audit.enabled: true` 时才能以只读方式打开私信正文，只有在 `group-chat.admin-audit.enabled: true` 时才能打开群聊正文；两种审计视图的每次分页读取都会写入审计日志。


`standalone-web.app-name` 和 `standalone-web.app-short-name` 控制 standalone 页面/PWA 名称。移动端添加到主屏幕后如更改这些值，需要重新添加。`web-push.notification-title` 控制测试/系统/后台推送的默认标题；留空时使用 `standalone-web.app-name`。
