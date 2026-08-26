# KOKOTO WebChat


## 项目名称与下载地址迁移

**BlueMapWebChat (BMWC) 从 5.0.0 起更名为 KOKOTO WebChat。** 为了不切断 4.7.0 用户现有的更新检查路径，5.0.0 过渡版本可以先通过现有 BlueMapWebChat 项目页面发布。BMWC 4.x 数据只作为迁移输入，5.0.0 运行时的正式名称是 KOKOTO WebChat。

迁移期间可继续使用以下旧地址作为下载入口：

- Modrinth: `https://modrinth.com/plugin/bluemapwebchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`
- GitHub: `https://github.com/KOKOTO-DEV/BlueMapWebChat`

目标 KOKOTO WebChat 地址为 `https://modrinth.com/plugin/kokoto-webchat`, `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`, `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`，但只有在对应平台完成实际重命名/创建后才作为正式地址公布。如果平台无法把现有 BMWC 项目无缝迁移为同一项目，则保留 BMWC 页面作为停止维护/迁移公告，并引导用户前往新的 KOKOTO WebChat 页面。

适用于 Bukkit/Paper/Spigot、Fabric 1.18.2～26.2 exact-target、NeoForge 1.20.2～26.2 exact-target 以及 Forge 1.18.2～26.2 exact-target 的服务端 Web 聊天。除了 standalone 页面外，也可以把 KWC 聊天嵌入 BlueMap、squaremap、Dynmap、Pl3xMap、LiveAtlas、uNmINeD 和 Minecraft Overviewer 地图页面。

## 主要功能

- 登录用户可在账号中保存多个视觉 UI 配置（默认 5 个，可由管理员调整），并支持在 KWC 服务器之间移动配置的严格 JSON 导入/导出
- 登录用户的关键词/通知类型按账号同步，而窗口状态和 Web Push endpoint 保持设备本地；同一设备启用 Web Push 时抑制实时页面重复的系统通知
- 管理员专用 Discord 关键词提醒：KWC 负责匹配、mention 和去重，Web Admin 仅选择 DiscordSRV 的逻辑频道
- 可用于公共/群组及可选 DM 的 Unicode 内容过滤：block/mask/replace、N:1/1:N/N:N 替换、compact/interleave 规避检测
- UTF-8 `filter-lists/*.txt` 批量过滤词列表，可为每个列表选择拦截/过滤，并支持自定义 block/mask/replace 规则；Web Admin 可导入、编辑、启用/禁用和删除 TXT 列表
- Web Admin **Filter/Settings** 管理，以及游戏内 `/kchat filter` / `/kchat settings` 运维命令
- 自定义规则写法与 Block/Mask/Replace 示例: [`docs/CONFIGURATION_ZH_CN.md`](docs/CONFIGURATION_ZH_CN.md#自定义过滤器快速用法)
- 会话期限变更会按原始创建时间重算现有 USER/MODERATOR 或 ADMIN 会话，`0` 表示无限期
- `upload.filename-mode: original` 可为新上传保留安全的 Unicode 原文件名，同名文件自动编号且不覆盖
- BlueMap / squaremap / Dynmap / Pl3xMap / LiveAtlas / uNmINeD / Overviewer 内嵌聊天面板，或 standalone Web 聊天页面
- 游戏 ↔ Web 双向聊天
- HMAC 签名的服务器间公共聊天中继、服务器彩色 Web 徽章、游戏/Discord 服务器标签
- Minecraft 点击回复(`/kchat reply`)与 Web 发送者点击 KWC DM(`/kchat dm`)
- 可选将游戏 `/w`/`/msg`/`/tell` 类私聊复制到双方 Web DM
- 访客聊天、数学验证码、冷却与每分钟限制
- 通过 `/kchat auth <code>` 绑定账号、Web 密码登录、本地管理员账号
- Web 管理/版主面板、隐藏消息、访客/IP 禁言、撤销会话
- 管理员自定义表情管理：创建、多文件上传、重命名、移动和删除表情文件夹/文件
- ImageEmojis-Bero 1.9.x token、游戏回复与服务器中继兼容
- 文件/剪贴板上传，图片/视频/音频/YouTube/Shorts 预览，以及可选的 TikTok 和 X/Twitter 嵌入
- DiscordSRV 转发，Discord CDN 媒体缓存
- 回复与跳转到原消息、游戏内回复预览、置顶消息、虚拟滚动、可拖动/缩放窗口、PIP
- UI 语言: en-US, ko-KR, ja-JP, zh-CN

## 配套插件集成

- [**ImageEmojis-Bero**](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) — Bukkit/Paper 系列可共用 `plugins/KOKOTO-WebChat/emojis`，Web/history/relay 保留 canonical token，游戏侧由 ImageEmojis 显示 glyph。`serverIp` + `webServerPort` 的资源包 HTTP 服务必须能被 Minecraft 客户端访问。详见 [`docs/IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md`](docs/IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md)；常规运维参考 [上游 ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)。
- [**SimpleNicks-Bero**](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) — 使用 `player-display.mode: "display-name"` 显示 Bukkit display name，同时保留真实 linked username/UUID identity。详见 [`docs/SIMPLENICKS_BERO_ZH_CN.md`](docs/SIMPLENICKS_BERO_ZH_CN.md)；常规运维参考 [上游 SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)。

## 构建

```bash
mvn clean package
```

```text
kwc-platform-bukkit/target/KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar
```

### Fabric exact-target

Fabric 按 Minecraft 版本构建 16 个 exact-target JAR，脚本会按 target 选择 JDK 17/21/25。

```bat
kwc-platform-fabric\build-all.bat
```

Targets：`1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。产物位于 `kwc-platform-fabric/targets/<Minecraft>/build/libs/KOKOTO-WebChat-5.0.0-Fabric-<Minecraft>.jar`。

### NeoForge exact-target

NeoForge 构建 12 个 exact-target JAR。1.20.2～1.20.6 使用 NeoGradle userdev，1.21.1 及后续 target 使用 ModDevGradle，并按 target 选择 JDK 17/21/25。

```bat
kwc-platform-neoforge\build-all.bat
```

Targets：`1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。产物位于 `kwc-platform-neoforge/targets/<Minecraft>/build/libs/KOKOTO-WebChat-5.0.0-NeoForge-<Minecraft>.jar`。

### Forge exact-target

Forge 不使用单个宽版本 JAR，而是构建 16 个按 Minecraft 版本区分的 exact-target JAR。

```bat
kwc-platform-forge\build-all.bat
```

脚本会为每个目标选择 JDK 17/21/25，并在对应 target 的 `build/libs/` 下生成 `KOKOTO-WebChat-5.0.0-Forge-<Minecraft>.jar`。

### Windows 最终发布验证

在源码根目录运行 `validate-release-windows.bat`，会依次构建 Bukkit、16 个 Fabric target、12 个 NeoForge target 和 16 个 Forge target。只有输出 `FINAL RELEASE BUILD PASS`、在 `release-5.0.0/` 收集到准确 45 个可发布 JAR，并生成 `SHA256SUMS.txt` 后，才判定实际构建也完成最终验证。

## 安装

1. Bukkit/Paper/Spigot JAR 放入 `plugins/`；Fabric/NeoForge/Forge JAR 放入 `mods/`。
2. 启动一次服务器，生成 `<KWC data dir>/config.yml`。`<KWC data dir>` 在 Bukkit 系为 `plugins/KOKOTO-WebChat`，在 Fabric/NeoForge/Forge 为 `config/KOKOTO-WebChat`。
3. 新生成的 config 顶层默认为 `enabled: false`；在检查设置并选择启用前，只会生成配置；/kchat reload 仍可使用。
4. 检查存储方式、保留期限、上传、预览、认证和对外公开设置后，再改为 `enabled: true`。
5. 如果要嵌入 BlueMap，请设置 `adapters.bluemap.enabled: true`。在 Bukkit 系中，除非手动管理 BlueMap Web 文件，否则 `auto-install` 和 `auto-patch-webapp-conf` 保持为 `true`。Fabric/NeoForge 与 Forge 26.1.2/26.2 在存在 BlueMap mod 时通过 BlueMapAPI 注册；26 之前的 Forge target 不提供 BlueMap 集成。
6. squaremap 设置 `adapters.squaremap.enabled: true`，Dynmap 设置 `adapters.dynmap.enabled: true`。Dynmap 会读取 `configuration.txt` 的 `webpath`，管理 KWC 专用资源和 `index.html` 标记区块。
7. 在 Bukkit/Paper 系或 Fabric 上使用 Pl3xMap 时设置 `adapters.pl3xmap.enabled: true`。KWC 会读取 Pl3xMap `config.yml` 中的 `settings.web-directory.path`，管理专用资源和 `index.html` 标记区块。当前 Pl3xMap 26.2 不提供 NeoForge 构建。
8. 使用 LiveAtlas 时，在 Bukkit/Fabric/NeoForge/Forge 上设置 `adapters.liveatlas.enabled: true`。KWC 只修改已有 LiveAtlas `index.html` 的 Web 根目录；若由外部 Caddy/nginx 从独立目录提供，请在 `web-root` 指定服务器可访问的共享/挂载路径。同一个实际 Web 根目录不要同时启用 LiveAtlas 和后端专用 KWC adapter。
9. 使用 uNmINeD 静态 Web 导出时，设置 `adapters.unmined.enabled: true`。uNmINeD 不是服务器插件，因此通常应在 `web-root` 指定导出目录。KWC 只会在确认当前 `index.html` 或旧版 `unmined.index.html` 包含 uNmINeD 标记后进行修改。
10. 使用 Minecraft Overviewer 静态 Web 地图时，设置 `adapters.overviewer.enabled: true`，通常在 `web-root` 指定 Overviewer 生成的 `outputdir`。KWC 只会修改带有 Overviewer 专用 generator/资源标记的 `index.html`，不会修改普通 Leaflet 页面。
11. 如果只使用 standalone，设置 `frontend.standalone.enabled: true`，并关闭所有地图 adapter。
12. 重启服务器或执行 `/kchat reload`。BlueMap 会自动请求 `bluemap reload light`，squaremap/Dynmap/Pl3xMap/LiveAtlas/uNmINeD/Overviewer 则由 KWC 直接重新检查网页文件。如果地图/站点生成器之后重新生成网页文件，再执行一次 `/kchat reload`。


现有配置值会被保留，但 migration 不再继承旧 config 的注释和布局。当前 JAR 内置的 `config.yml` 是唯一 migration 模板：KWC 先创建最新默认 config，再把现有管理员设置值覆盖到该模板上。因此旧注释、顺序、空白和缩进会被丢弃，最新内置注释/布局成为标准。`<KWC data dir>/config-reference-5.0.0.yml` 只是供管理员查看完整默认设置的内置 config 精确副本，不作为 migration 输入。若旧 marker 不带 `_auto_migration`，则视为已固定配置，并在真实版本升级前完整备份原 `config.yml`。migration 后写入 `config-version: "5.0.0_auto_migration"`；只要该 marker 保留，startup/reload 都会重复同样的重建过程。精确的 `config-version: "5.0.0"` 表示固定当前版本配置，同版本启动/重载不会改写 `config.yml`。旧版本生成的 reference/migration/upgrade 文件会自动删除。内置 UTF-8 初始过滤词列表 `filter-lists/ko-KR.txt`、`en-US.txt`、`ja-JP.txt`、`zh-CN.txt` 会在 starter-list 初始化标记不存在时仅初始化一次，因此早于该功能创建的数据目录也会获得这些文件。已有或已禁用的列表文件绝不会被覆盖；初始化完成后由管理员删除的初始列表也不会在重启时重新生成。

### 5.0.0 KOKOTO WebChat 架构与名称迁移

从 5.0.0 开始，正式项目标识统一为 KOKOTO WebChat。Maven 模块为 `kwc-core`、`kwc-standalone-frontend`、`kwc-adapter-bluemap`、`kwc-adapter-squaremap`、`kwc-adapter-dynmap`、`kwc-adapter-pl3xmap`、`kwc-adapter-liveatlas`、`kwc-adapter-unmined`、`kwc-adapter-overviewer`、`kwc-platform-bukkit`，Java package 为 `dev.kokoto.webchat`。所有平台的正式游戏命令为 `/kchat`（短别名 `/kc`），权限使用 `kwc.*`，数据目录为 `<KWC data dir>`，反向代理示例使用 `/chat`。

旧 BlueMapWebChat 4.x 只作为迁移输入。只有当 `plugins/KOKOTO-WebChat` 中不存在任何已有数据文件时，才会导入 `plugins/BlueMapWebChat` 的运行数据，并把 `web-addon.*` 转换为 `adapters.bluemap.*`、`standalone-web.*` 转换为 `frontend.standalone.*`。如果 KWC 数据已经存在，即使手动删除 `.legacy-import-complete` 也不会再次合并 BMWC 数据。删除原 `plugins/BlueMapWebChat` 目录后，临时 `.legacy-import-complete` 标记也会自动删除。不再提供 `/bmchat`、`/bluemapchat`、`/bmc`、`/kwc` 命令别名。旧 `bluemapwebchat.*` 权限仍可由权限兼容层处理，但新配置和文档使用 `kwc.*`。

> **BMWC HTTPS 迁移：** BMWC 标准的 `/bmwc/api`、`/bmwc/chat` 公开布局会迁移到 KWC 的 `/chat` 布局。标准 BMWC API URL 设置会规范化为空的自动值，但 Caddy/nginx 文件不会自动修改，必须手动改为 `/chat` 前缀剥离方式。


Relay protocol v1 为了兼容旧 BMWC peer，继续保留 wire header `X-BMWC-Relay-*`。只要 `server-id`、peer URL 与 shared secret 匹配，KWC 与 BMWC 4.x 仍可互相中继。

## 4.7.0 表情批量上传与兼容范围

4.7.0 还加入可配置的 `:token:` 消息替换。默认 alias 只提供英文，管理员可改成或追加任意语言。支持换行、空行、缩进动作和可打印 custom 替换；未知令牌保持原样以兼容现有表情。

4.7.0 中，管理员表情上传改为使用与普通聊天文件上传相同的文件选择流程。点击 Upload 打开隐藏的 multiple file input；选择文件后立即把选择内容复制为普通数组，清空 native input，并直接开始顺序上传，不再有第二个确认上传步骤。仍保留进度显示和实际传输中的取消；单文件限制、总容量限制、重名处理、审计日志和 PNG sidecar 生成继续使用现有服务器上传路径。

Bukkit/Spigot API 基线从 1.21 下调到 1.18，同时继续使用 Java 17。本版本采用的保守 Minecraft 支持范围为 **1.18 ～ 26.2**。Paper 专用 `AsyncChatEvent` 仍通过 reflection 检测，并保留 Bukkit legacy chat event 作为 fallback。

## 4.6.3 管理员群聊审计

4.6.3 新增可选的只读管理员群聊正文审计。它使用与私信审计相同的 `private-chat-super-admins` 精确账号列表，但 `group-chat.admin-audit.enabled` 是独立开关。审计者不会加入房间，不会改变已读状态或未读人数，也不能发送、上传、隐藏消息或修改成员。每次审计分页读取都会记录为 `admin.group-audit-read`，审计日志不会复制消息正文。

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

4.6.3 还修复了私信/群聊实时刷新时视频和音频从头重新播放的问题。私聊消息列表现在与普通聊天一样按 stable key 保留现有消息，只更新新消息以及投递/已读元数据，因此已加载的媒体 DOM 会持续保持连接。

详情见 `docs/UPGRADE_4_6_3_ZH_CN.md`。

### 4.6.2 私信与群聊投递状态和重试

跨服务器私信在目标服务器确认消息已实际写入之前保持 `pending`；确认后才变为 `delivered`。路由、传输或超时失败会变为可重试的 `failed`，重试继续使用同一个 relay ID，因此即使只是 HTTP 响应丢失，也不会在接收端重复保存消息。Web 私信和群聊同样使用 client message ID，使浏览器在结果不确定时可以安全重试。没有服务器限定的私信名称只解析当前服务器玩家。私信与群聊的每一条消息都会在时间旁边显示已读状态。一对一私信在接收者阅读前显示 `未读`，阅读后显示 `✓`；群聊继续显示未读接收者人数，人数降至 0 时显示 `✓`。发送状态也使用短文本：处理中显示 `发送中`，失败时显示 `失败 · 重试`。

建议所有交换跨服务器私信的服务器使用 KOKOTO WebChat 4.6.2 或更高版本。详见 `docs/UPGRADE_4_6_2_ZH_CN.md`。

## standalone URL

```text
http://<server-host>:8899/
```

## HTTPS / Caddy 推荐配置

公开服务器建议将 BlueMap 和 KOKOTO WebChat 保持为内部 HTTP 服务，并通过 HTTPS 反向代理对外提供。

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: "/chat"
  cors-origin: "https://map.example.com"

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""

upload:
  # 推荐留空。上传 URL 会自动跟随 /chat/api。
  # 独立公开 URL override 示例: "/chat/api/uploads"
  public-base-url: ""
  # 0 = 不限制。正数会限制 upload.directory 的总文件容量。
  max-total-size-mb: 0

emoji:
  # 推荐留空。表情 URL 会自动跟随 /chat/api。
  # 独立公开 URL override 示例: "/chat/api/emojis"
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

KOKOTO WebChat 会把自定义表情文件保存到 `<KWC data dir>/emojis`。子文件夹会作为表情包处理。

默认情况下，Web→游戏聊天会保留 `:default/wave:`、`:emoji:default/wave:` 这样的自定义表情 token。若 ImageEmojis 或其他游戏侧表情插件会在 Minecraft 聊天中渲染相同的 token 文本，请使用这个默认行为。

启用 `emoji.game-link.enabled` 后，`emoji.game-link.mode` 支持 `preserve`、`link` 和 `label`。

- `preserve`: 保持原始 token 文本不变。
- `link`: 发送配置的 token 文本，并附加一个短 BM Web Chat 图片链接。
- `label`: 只发送配置的 token 文本。

`emoji.game-link.*` 只影响 Web→Minecraft 聊天。Discord 图片预览链接由单独设置控制：`discordsrv.append-web-emoji-links` 用于 Web→Discord 消息，`discordsrv.append-game-emoji-links` 会在可能的情况下编辑 DiscordSRV 的普通 Minecraft→Discord 转发消息，为 Game→Discord token 附加 URL。多个服务器共享同一 Discord 频道时，只有实际检测到本地游戏聊天的来源服务器会编辑该 DiscordSRV 消息，其他服务器不会重复添加服务器名或表情链接；接收中继的 peer 也不会把消息重新发送到 Discord。如果 DiscordSRV 负责普通 Minecraft 聊天转发，请将 `discordsrv.game-relay-mode` 设为 `discordsrv`。若要由 KWC 直接发送，请使用 `kwc`，并关闭 DiscordSRV 的普通游戏聊天转发以避免重复发布。

BM Web Chat 会在 Web 历史和服务器中继 payload 中保留规范的表情 token。启用 ImageEmojis 或 ImageEmojis-Bero 时，KWC 会通过 reflection 读取其公开的 runtime 表情 repository，并在构建可点击的 Minecraft component 时使用接收服务器当前的 token→glyph 映射。无需硬依赖或解析资源包；无法解析的 token 仍会回退到原有的游戏侧渲染路径。

上传 GIF/JPG/JPEG/WEBP 表情时，KOKOTO WebChat 还会在同一文件夹创建 PNG sidecar，以兼容只读取 PNG 文件的游戏侧表情插件。

```text
<KWC data dir>/emojis/default/wave.gif
<KWC data dir>/emojis/default/wave.png
```

Web UI 会继续使用原始文件，因此 GIF 动画会保留。如果游戏侧表情插件监视同一个表情目录，它可以使用 PNG sidecar。添加或更改表情后，请运行该插件的 reload 命令。

ImageEmojis-Bero 1.9.x 的共用目录、权限、命令转换、服务器中继及故障排除参见 [`docs/IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md`](docs/IMAGEEMOJIS_BERO_1_9_0_ZH_CN.md)。

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
/kchat dm <player> <message>
/kchat reply <messageId> <message>
/kchat auth <code>
/kchat password <newPassword>
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

## 权限

```text
kwc.auth
kwc.webchat
kwc.dm
kwc.reply
kwc.group
kwc.admin
kwc.update.notify
```

## 文档

- `docs/USER_MANUAL_ZH_CN.md` - 所有功能的完整用户与运维手册
- `docs/CONFIGURATION_ZH_CN.md`
- `docs/SERVER_RELAY_ZH_CN.md` - 服务器间公共聊天中继
- `docs/UPGRADE_5_0_0_ZH_CN.md` - 4.7.0→5.0.0 major upgrade / migration
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


URL 设置说明：`http.path-prefix` 是 KWC 内部 API 路径，`http.public-prefix` 是外部反向代理前缀。默认情况下，外部 `/chat` 转发到内部 `/`，外部 `/chat/api` 转发到内部 `/api`。只有需要独立公开 API URL 时才设置 adapter/standalone 的 `api-base-url`。

## SQLite 历史搜索

使用 SQLite 历史存储时，可以通过聊天面板右上角的浮动区域的放大镜按钮搜索消息内容和发送者。搜索选项也可指定日期/时间范围、发送者、来源以及是否包含系统/事件消息。搜索结果会显示在可滚动列表中，并遵循聊天主题和字体设置。点击搜索结果会使用现有的周边历史加载跳转到对应消息。带有 i18n 键的系统/事件消息会尽可能按当前选择的 Web UI 语言搜索和显示。仅用 `search.result-limit` 同时控制 Web UI 结果数量和 `/history/search` API 限制，没有单独的内部最大值。10000 或 100000 这类非常大的值也会被接受，但可能导致搜索变慢、响应体变大，并显著增加 CPU、内存和数据库负载。


## 聊天记录保留期

新生成的 config 顶层默认为 `enabled: false`，因此在检查保留期限和清理相关设置并改为 `enabled: true` 前，不会执行自动清理任务。请按服务器策略确认聊天记录、上传文件和外部媒体缓存的保留期限后再启用。

## 1:1 私信会话线程

启用 `direct-message.enabled` 后，可以使用 1:1 会话线程式消息箱。目标包括已有 UUID/名称记录的已关联或曾加入玩家，以及中继消息中带有玩家 UUID 的其他服务器发送者。收到的显示名和真实 Minecraft 名会加入 Web DM 的新会话对象搜索，因此无需单独添加 DM 按钮即可通过普通搜索开始会话。没有玩家 UUID 的访客和 Discord 发送者不会被加入。A→B 与 B→A 会使用同一个线程，存储按 UUID 进行，UI 会尽可能显示为 `显示名 (真实账号名)`。

DM 使用独立于公开聊天历史的专用存储。`direct-message.storage: auto` 会在公开聊天使用 `jsonl` 存储时让 DM 也使用 JSONL，其他情况下使用 SQLite。也可以显式设置为 `sqlite` 或 `jsonl`，并分别使用 `direct-message.sqlite-file` 或 `direct-message.jsonl-file`。`direct-message.retention-days: 0` 表示无保留期限；其他值会显示在 DM 窗口标题旁作为保留期限，超过该天数的 DM 原文会被物理删除。`direct-message.max-messages-per-thread: 0` 表示不按线程消息数清理。`direct-message.confirm-hide` 控制 Web UI 在从自己视图隐藏 DM 前是否显示确认框。由于私信会保存在服务器上，此功能默认关闭，建议先确定服务器保留策略后再启用。


启用 `direct-message.capture-game-whispers` 后，游戏 `/w`、`/msg`、`/tell` 等会复制到相同 Web DM 会话。点击同服游戏发送者名称会建议 `/w <真实名称> `；Web 发送者会建议 `/kchat dm <真实名称> `；其他服务器的游戏发送者会建议 `/kchat dm <真实名称>@<server-id> `。在 `/w`、`/msg`、`/tell`、`/whisper`、`/m`、`/pm`、`/message`、`/t` 中使用 `名称@server-id` 目标时，也会通过同一跨服务器 KWC DM relay 发送。

## 群组聊天室

启用 `group-chat.enabled` 后，可以使用 Web 群组聊天室功能。用户可以创建房间、选择公开/私密、设置可选房间密码、邀请已知玩家、接受或拒绝邀请、退出房间、从自己的列表隐藏/恢复房间、修改房间设置、踢出/封禁/解除封禁成员、转移房主，并从 Web UI 发送消息。公开房间会显示在房间列表中；私密房间仅限邀请加入。房间密码不会明文保存，而是使用 PBKDF2 哈希保存。

群组聊天使用独立的 SQLite 存储（`group-chat.sqlite-file`，默认 `group-messages.db`）。`group-chat.retention-days: 0` 表示无时间限制；正数会显示在群组聊天标题旁，并在超过该天数后物理删除旧群组消息。`group-chat.max-messages-per-room: 0` 表示不按数量清理。本版本仍以 Web 为主，尚未包含游戏侧 `/kchat group` 命令、房间静音、超出房主/成员操作的详细角色管理 UI 和群组 JSONL 存储。


### 私信/群组聊天元数据超级管理员

在 `config.yml` 的 `private-chat-super-admins` 中填写准确 UUID 或 Minecraft 名后，可查看用于管理/容量检查的私信与群聊元数据。默认视图显示标题/参与者、消息数、大致存储大小、保留状态和清理预览。设置 `direct-message.admin-audit.enabled: true` 后，同一明确列出的账号可以只读打开私信正文。4.6.3 中还可独立启用 `group-chat.admin-audit.enabled: true`，让这些账号无需加入房间、也不会改变已读状态即可只读查看群聊正文。普通 ADMIN/MODERATOR 角色不会自动获得权限，每次审计分页读取都会写入审计日志。超级管理员还可以锁定会话或将其从自动删除中排除。

会影响管理状态的操作默认会按日期追加到 `<KWC data dir>/audit` 下的文本审计日志中。审计日志供服务器运营者查看，不会显示在 Web UI 中。


注意：`frontend.standalone.app-name` / `frontend.standalone.app-short-name` 可更改移动端主屏幕 Web App 名称，`web-push.notification-title` 可更改默认推送标题。`web-push.notification-title` 留空时使用 `frontend.standalone.app-name`。Android/桌面浏览器在 HTTPS 与 Push API 可用时，可从 BlueMap addon 或 standalone 页面启用推送。iOS/iPadOS 请使用已添加到主屏幕并作为 Web App 打开的页面，而不是普通浏览器标签页。


- Pl3xMap integration: `docs/PL3XMAP_INTEGRATION.md`

- uNmINeD integration: `docs/UNMINED_INTEGRATION.md`

## Overviewer

- Overviewer 集成: `docs/OVERVIEWER_INTEGRATION.md`

## Forge Stage 1

Forge 对 Minecraft 1.18.2～26.2 使用按 Minecraft 版本区分的 exact-target 服务端 JAR，而不是单个宽版本 JAR。源码分为 `src/common`、`compat118`、`compatClassic`、`compatModern`、`compat26`。详细目标见 `kwc-platform-forge/README.md`。BlueMapAPI 直接集成仅包含在 Forge 26.1.2/26.2 中。 全部目标请使用 `kwc-platform-forge/build-all.bat`（Windows）或 `build-all.sh`；脚本会按 exact target 自动选择 JDK 17/21/25，不能让旧版 ForgeGradle 直接使用系统 Java 25。

## 生成式 AI 使用说明

本项目在开发过程中使用生成式 AI 作为辅助工具，用于代码审查、实现与补丁编写辅助、文档编写和多语言翻译。项目需求、架构与设计决策、源码整合、测试、兼容性验证、发布验证和最终批准均由人工维护者主导并审核。AI 辅助产生的内容只有在人工审查和验证后才会纳入项目。详情请参阅 `AI_USAGE.md`。
