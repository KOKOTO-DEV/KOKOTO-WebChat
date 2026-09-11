# KOKOTO WebChat 升级指南

本文档整合了从 4.5.5 到 5.3.0 的升级说明。跨多个版本升级时，请按版本顺序依次检查各节。

## 从 4.5.5 升级到 4.6.0

### 先备份

停止服务器并备份 `plugins/KOKOTO-WebChat`，尤其是 `config.yml`、SQLite 数据库及 `-wal`/`-shm`、DM/群组数据库、上传、表情、自定义语言文件、审计日志以及 Web Push 密钥和订阅文件。

### 自动生成的配置迁移片段

KOKOTO WebChat 不会自动覆盖或合并现有 `config.yml`。服务器启动和执行 `/kchat reload` 时会读取磁盘上的实际文件并检查 `config-version`。

- 当 `config-version` 与正在运行的插件版本一致时，配置被视为已审核，跳过比较，并删除该版本的旧迁移片段。
- 当版本标记缺失或不同时，插件会比较实际配置与 JAR 内置当前默认配置，并生成或更新：

判定表：

| 已安装配置状态 | 迁移文件 |
|---|---|
| 缺少版本标记 | 即使没有其他差异也会生成 |
| 版本标记与当前插件不同 | 生成或更新 |
| 版本标记与当前插件一致 | 不生成，并删除遗留的同版本提示文件 |

```text
plugins/KOKOTO-WebChat/config-migration-4.6.0.yml
```

生成文件不是结构化报告，而是可直接参考和复制的 YAML 配置片段。它显示：

- 实际 `config.yml` 中缺失的设置及当前推荐默认值；
- 内置默认值已变化，并且实际设置值仍等于旧默认值的设置；
- 最终审核标记对应的目标 `config-version`。

版本信息、数量以及旧、新默认值说明全部只写成 `#` 注释。不会创建 `migration:`, `summary:`, `settings-to-add:`, `preserved-custom-values:`, `obsolete-settings-to-review:`, `finalize-after-review:` 等信息型 YAML 区块，也不会输出自定义值和废弃候选参考列表。

只把需要的设置合并到实际 `config.yml` 的对应位置。真实配置文件不会被自动修改。

即使没有任何缺失设置或变化的内置默认值，也会生成迁移文件并包含目标 `config-version`。这样，没有版本标记的配置也必须经过明确审核后才能标记为完成。

4.6.0 内置 4.5.5 默认配置作为比较基线。没有版本标记的配置按 4.5.5 或更早版本处理。对于明确但未知的版本，只报告缺失键，不推测默认值变化。

审核完成后在实际配置中设置：

```yaml
config-version: "4.6.0"
```

只要该标记与插件版本一致，之后的启动和 reload 都会跳过比较。

### 4.6.0 主要新增设置

- 顶级 `server-relay:`
- `direct-message.capture-game-whispers`
- `reply.game-click.enabled`
- `reply.game-click.local-game-chat`
- `reply.game-command-format`
- Discord `{server}`、`{server_id}` 占位符

版本不一致期间，实际文件中缺少的私聊捕获和本地聊天替换功能会使用安全的禁用状态。

### 数据库迁移

公共 SQLite 历史会通过增量 `ALTER TABLE` 添加中继元数据列。现有记录会保留，但旧记录无法追溯补充来源服务器。首次启动 4.6.0 前请备份数据库。

### 建议检查

1. 使用旧配置启动，确认生成配置片段且 `config.yml` 未改变。
2. 审核并合并缺失设置与变化的默认值。
3. 添加 `config-version: "4.6.0"`，执行 `/kchat reload`，确认跳过比较。
4. 测试中继、游戏回复、私聊复制、Discord 服务器标签、URL 和 ImageEmojis。

---

## 从 4.6.0 升级到 4.6.1

**5.1.0 说明：** 此管理员私信正文审计行为在 5.1.0 中仍然保留。需同时使用 `direct-message.admin-audit.enabled` 与 `private-chat-super-admins`，审计视图为只读。


### 主要变化

- 中继消息包含玩家 UUID 时，可在现有网页私信收件人搜索中找到其他服务器玩家。
- 可选择在现有私聊元数据列表中以只读审计方式查看私信正文。
- 新增基于 Modrinth 的简洁更新检查和管理员进服提示。
- 插件和配置版本更新为 `4.6.1`。

### 配置迁移

使用 `config-version: "4.6.0"` 的配置启动 4.6.1 时会生成：

```text
plugins/KOKOTO-WebChat/config-migration-4.6.1.yml
```

```yaml
update-check:
  enabled: true

direct-message:
  admin-audit:
    enabled: false

config-version: "4.6.1"
```

实际 `config.yml` 不会被自动修改。除非确实需要审查私信正文，否则请保持该功能禁用。

### 启用私信正文审计

必须同时满足两个条件：

```yaml
private-chat-super-admins:
  - "准确的Minecraft名称或UUID"

direct-message:
  admin-audit:
    enabled: true
```

- 普通 ADMIN 或 MODERATOR 角色本身不能查看正文。
- 审计视图为只读。
- 每次分页读取都会写入审计日志，但不会把消息正文复制到审计日志。
- 修改配置后执行 `/kchat reload` 或重启。替换 JAR 仍需要重启服务器。

### 跨服务器私信版本要求

所有交换跨服务器私信的服务器都必须使用 KOKOTO WebChat 4.6.1 或更高版本。点击 `服务器 · 类型` 时会直接传递该消息的 UUID 和来源服务器 ID，远程搜索结果及已有远程会话也会同时保留服务器 ID 与玩家 UUID。

---

## 从 4.6.1 升级到 4.6.2

KOKOTO WebChat 4.6.2 提升私信投递可靠性，修复同名私信路由问题，并增加逐消息已读状态。不需要新增管理员配置。

### 变化

- 未指定服务器的私信名称只解析当前服务器玩家。跨服务器目标使用明确的 `server-id + UUID` 身份。
- 跨服务器私信只有在目标服务器确认实际保存后才视为投递完成。路由、HTTP、超时或目标端失败会保留为可重试失败。
- 私信重试复用持久 relay ID，Web 发送使用 client message ID，因此请求或响应不确定时不会重复保存同一消息。因服务器重启中断的 pending 投递会恢复为可重试失败。
- 群聊 Web 发送同样使用 client message ID 防止重复。正常投递完成不显示文字；时间旁边只在处理中显示 `发送中`，失败时显示 `失败 · 重试`。
- 对私信和群聊的**每一条消息**计算已读状态，并显示在时间旁边。一对一私信在对方阅读前显示简短的 `未读`，阅读后显示 `✓`；群聊继续以数字显示未读接收者人数，人数为 0 时显示 `✓`。
- 跨服务器私信的已读状态通过认证的私有中继返回，在 hub / chain 拓扑中也会反映到原始消息一侧。重新打开会话时会安全地重发最新已读 ACK，因此临时的中继或 HTTP 故障不会永久丢失已读标记。
- 多跳私信中继只有在最终目标确认存储后才向上游返回成功。
- 修复更新通知。符合条件的管理员登录时会按限频规则重新查询 Modrinth，而不是只依赖之前的定时查询结果；OP 会被明确视为通知对象，查询失败会写入警告日志，reload 时也会注销旧的更新监听器。

### 配置迁移

与 4.6.1 相比，4.6.2 的内置配置没有新增管理员配置键，也没有修改现有默认值。仅更新审核标记：

```yaml
config-version: "4.6.2"
```

使用已审核的 4.6.1 配置启动时会生成 `plugins/KOKOTO-WebChat/config-migration-4.6.2.yml`。如果不存在无关的缺失设置或本地/默认值差异，该文件只包含新的 `config-version` 标记。实际 `config.yml` 不会被自动覆盖。

### 跨服务器部署

所有交换跨服务器私信的服务器都应使用 KOKOTO WebChat 4.6.2 或更高版本。替换 JAR 后重启各服务器即可启用新的中继处理和增量数据库迁移。现有私信和群聊消息会保留。

---

## KOKOTO WebChat 4.6.3 升级

4.6.3 新增可选的只读管理员群聊正文审计功能，不改变现有私信审计行为。

### 新配置

```yaml
group-chat:
  admin-audit:
    enabled: false

config-version: "4.6.3"
```

对于已经标记为 `4.6.2` 的配置，如果没有其他实际缺失项，migration fragment 只会包含这个新开关和 4.6.3 检查标记。现有 `config.yml` 不会被覆盖。

### 访问条件

以下两个条件都必须满足：

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

普通 ADMIN/MODERATOR 角色本身不能查看正文。审计视图为只读，不要求或创建房间成员身份，不会更新已读/未读状态，也不能发送、上传、删除消息或修改成员关系。每次分页读取都会记录为 `admin.group-audit-read`，但不会把消息正文复制到审计日志。

### 配置注释

4.6.3 更新了内置 `config.yml` 注释，使其准确说明当前的更新检查、跨服私信投递/已读 ACK、群聊已读状态以及私信/群聊管理员审计行为。服务器启动或执行 `/kchat reload` 时，只有当现有配置中的某段注释 **仍与旧版 KOKOTO WebChat 内置注释完全一致** 时，才会刷新为新的内置注释。该注释刷新不会修改任何配置值，用户自定义注释会保留，`config-version` 仍只应在管理员审核 migration fragment 后手动修改。
### 私聊媒体播放

私信和群聊消息列表现在也采用与普通聊天相同的 stable key DOM 更新方式。同一会话刷新时，现有消息和视频/音频 DOM 会保持连接，只更新新增/删除的消息以及投递/已读元数据。因此收发新消息时，正在播放的媒体不会从头重新播放。只有原本已在底部时才会继续跟随最新消息；浏览中间位置时会保持当前视口。离开当前会话或切换到其他会话时，会彻底删除该会话的消息/媒体 DOM，并清除其 private media-open 状态。再次进入时会从 `▶ Video` / `▶ Audio` 的未展开 click-to-load 状态重新创建；即使关闭 click-to-load，也不会复用旧播放器，而是创建新的未播放媒体元素。仅重新进入会话不会调用 `play()`。

---

## KOKOTO WebChat 4.7.0 升级

4.7.0 将 Bukkit/Spigot 兼容基线下调到 Minecraft 1.18，并加入管理员自定义表情多文件上传和可配置的消息令牌替换。

### 兼容性

- 保守支持范围：**Minecraft 1.18 至 26.2**
- Java：**17**
- `plugin.yml`：`api-version: '1.18'`
- Maven 编译 API：`spigot-api:1.18.2-R0.1-SNAPSHOT`
- Paper `AsyncChatEvent` 继续通过 reflection 检测，Bukkit `AsyncPlayerChatEvent` 作为直接链接的 fallback。
- 1.17 及更早版本不列入本次正式支持范围。

### 自定义表情多文件上传

表情上传现在使用与普通聊天文件上传相同的文件选择流程。点击 Upload 后打开隐藏的 multiple file input；选择文件后立即把 `FileList` 复制为普通数组，清空 native input，并直接开始顺序上传。没有第二个确认上传按钮，也不再使用文件选择器 focus/visibility 绕过逻辑。仍保留上传进度和实际传输中的取消。现有服务器 endpoint 继续负责逐文件验证、总存储计算、重名处理、审计日志和 PNG sidecar 生成。

### 消息令牌

默认 alias 只提供英文，管理员可以改成或追加任意语言。内置动作包括换行、空行和缩进，也可配置只包含可打印字符的 custom 替换。未知的 `:token:` 保持原样，因此可以继续与现有自定义/图片表情令牌共存。

### 配置

4.7.0 新增 `message-tokens` 配置段。除此之外不修改现有默认值，并更新 review marker。migration 比较并不限于 4.6.3，更旧或没有版本标记的配置也会按当前 4.7.0 设置检查缺失项。另外会始终生成 `config-reference-4.7.0.yml`，它是当前 JAR 完整 4.7.0 默认配置及全部注释的原样副本。`message-tokens.custom: {}` 这类空 map 在缺失时也会保留在 migration 输出中。migration 文件末尾还会以注释形式附上与完整 reference 的文本 diff。相同的行不会输出；每个差异按文件名、单独一行的 `Line` 或 `Lines`、实际不同内容的顺序显示。差异源行只在行首直接加 `#`，因此会原样保留 YAML 自身的缩进，并为仅 reference 中存在的块标出在当前配置中的插入位置：

```yaml
config-version: "4.7.0"
```

startup/reload 时还会把已知顶层 `config.yml` 配置块按 4.7.0 bundled 默认顺序重新排列，同时保留各块的当前文本、设置值和用户自定义注释；默认中不存在的顶层块会按原顺序保留在最后。

#### 游戏内换行行为

只有由已配置的 `newline` / `blank-line` 标记生成的换行会以受保护状态通过 Minecraft 现有的单行清理，并在最终发送时作为独立的游戏聊天行输出。普通 CR/LF 输入仍按原有方式展平。通过服务器中继在游戏中显示这种有意换行时，接收端 KOKOTO WebChat 也必须使用相同的 4.7.0 标记行发送支持；旧版接收端会在原有展平阶段把普通 LF 转为空格。

---

## KOKOTO WebChat 5.0.0 升级

5.0.0 汇总正式版 4.7.0 之后的开发内容，并完成 **BlueMapWebChat (BMWC) → KOKOTO WebChat (KWC)** 的名称迁移。

### 下载页面迁移

最安全的发布顺序是 **先在现有 BlueMapWebChat 项目页面发布 5.0.0**。4.7.0 的更新检查器只认识旧的 Modrinth `bluemapwebchat` 项目，因此旧页面上的 5.0.0 可以作为 bridge release 让现有用户正常发现升级。

旧/过渡地址：
- Modrinth: `https://modrinth.com/plugin/bluemapwebchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/bluemapwebchat`
- GitHub: `https://github.com/KOKOTO-DEV/BlueMapWebChat`

目标 KOKOTO WebChat 地址（实际启用后）：
- Modrinth: `https://modrinth.com/plugin/kokoto-webchat`
- CurseForge: `https://www.curseforge.com/minecraft/bukkit-plugins/kokoto-webchat`
- GitHub: `https://github.com/KOKOTO-DEV/KOKOTO-WebChat`

KWC 5.0.0 会优先查询 Modrinth `kokoto-webchat`，如果尚未存在则回退到 `bluemapwebchat`。如果某个平台无法把旧项目无缝重命名为同一项目，则保留 BMWC 页面作为停止维护/迁移公告并链接到新的 KWC 页面。不要在 4.7.0 用户能够发现 5.0.0 之前先关闭旧页面。

### 4.7.0 → 5.0.0 主要变化

- 正式标识统一为 KOKOTO WebChat：`/kchat` (`/kchat`)、`kwc.*`、KWC 数据目录、`dev.kokoto.webchat`、`kwc-*` 模块。
- Bukkit/Paper/Spigot、Fabric 16 个 exact-target、NeoForge 12 个 exact-target、Forge 16 个 exact-target 使用 shared core。
- 新增/整理 BlueMap、squaremap、Dynmap、Pl3xMap、LiveAtlas、uNmINeD、Overviewer adapter。
- standalone 默认启用，公开前缀 `/chat`，API `/chat/api`。
- 新增 Unicode 内容过滤、UTF-8 filter list、mask/replace、anti-evasion、Web Admin 编辑/测试。
- 新增账号级 server-side UI profile、strict JSON import/export，以及账号同步的关键词/通知设置。
- 抑制同一设备 Web Push 与实时页面系统通知的重复提示。
- 新增管理员 Discord 关键词提醒；匹配/格式/mention/去重由 KWC 处理，DiscordSRV 只提供 JDA 连接与频道映射。
- `update-check.enabled` 现在不仅支持 Bukkit，也支持 Fabric、NeoForge 和 Forge，并使用 KWC 优先/BMWC fallback 的 Modrinth 查询与 `kwc.update.notify` 登录提醒。
- 新增 `upload.filename-mode: random|original`，修复 Unicode/空格/`~`/`+`/`%` 文件名和 Windows clipboard 8.3 别名问题。
- 整理 Discord game relay、server relay、跨服务器 DM/已读状态以及各平台 `/kchat` 一致性。
- 加入不改变正常 UX 的安全加固：Bearer 认证、一次性 SSE ticket、请求体限制、bounded HTTP worker、管理员 IP 检查、Web Push SSRF 防护、Discord mention/CDN redirect 防护、iframe source 验证。

### 平台支持

- Bukkit/Paper/Spigot：1.18–26.2，Java 17 bytecode。
- Fabric exact-target：`1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`；按 target 使用 JDK 17/21/25。
- NeoForge exact-target：`1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`；按 target 使用 JDK 17/21/25。
- Forge exact-target：`1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。

### BMWC 数据/配置迁移

Bukkit 上已有的 `plugins/BlueMapWebChat` 可在首次 KWC 启动时作为迁移输入，原目录保持不变。`web-addon.* → adapters.bluemap.*`，`standalone-web.* → frontend.standalone.*`。旧 `/bmchat` 等不再注册为命令别名，旧 `bluemapwebchat.*` 权限只作为兼容 fallback。`X-BMWC-Relay-*` wire header 为旧 peer 兼容而保留。

旧 `/bmwc/api`、`/bmwc/chat` reverse proxy 需要手动改成新的 `/chat` 布局。

### 配置迁移

4.7.0→5.0.0 reference 结构比较结果为 **新增 79 个路径、删除 14 个路径、2 个现有值发生变化**。实际版本迁移会保留用户值、插入缺失的 setting/comment，并写入 `config-version: "5.0.0_auto_migration"`。只有希望停止同版本自动 backfill 时才改为精确 `5.0.0`。

### 最终发布判定


> `validate-release-windows.bat` 及其所需的 PowerShell helper 已包含在 source archive 中。开发回归测试工具也位于 source archive 的 `validation/`，无需单独的 validation-tools archive。

只有 `validate-release-windows.bat` 输出 `FINAL RELEASE BUILD PASS`、收集到准确 45 个可发布 JAR，并通过 static/config/i18n/document validation 与主要功能 smoke test 的候选版本才作为正式发布版。

---

## 升级到 KOKOTO WebChat 5.1.0

![ui.language 配置重建流程](../assets/config-language-migration.svg)

[Animated GIF](../assets/config-language-migration.gif) · [PNG](../assets/config-language-migration.png) · [SVG](../assets/config-language-migration.svg)

> **重要：** `ui.language` 只改变配置文件、参考文件和迁移报告的显示语言；已经解析出的实际配置值会原样保留。

KOKOTO WebChat 5.1.0 面向 **5.0.0** 系列升级。发布说明按 **5.0.0** 正式版到 5.1.0 的最终变更内容编写。

### 升级前

1. 备份完整的 KWC 数据目录，包括 `config.yml`、公开/私聊数据库或 JSONL、上传文件、表情资源以及审计数据。
2. 如果多台服务器通过 Relay 互联，请同时规划所有相关服务器的升级与 Relay v2 重新配置。Relay Protocol v1 与 5.1.0 Relay v2 不兼容。
3. 如果 KWC 位于 Caddy/Nginx 后方，请记录当前公开 URL 与代理结构，以便升级后核对客户端 IP 解析是否正确。

### 配置迁移与语言

5.1.0 会保留现有的**已解析配置值**，仅使用当前模板重新构建注释与排版。实际生效的 `ui.language` 决定重建后的 `config.yml`、生成的 `config-reference-5.1.0.yml` 以及迁移/Difference 说明文字所使用的语言。内置显示语言为 `en-US`、`ko-KR`、`ja-JP`、`zh-CN`。

Difference 采用语义比较：只比较解析后的 YAML 配置路径和值，不比较注释、空行、缩进、引号形式、行号或键顺序。因此，仅切换显示语言不应产生配置差异。

首次启动后请同时检查 `config.yml` 与 `config-reference-5.1.0.yml`。包括已经在 5.1.0 中创建的 Relay 组在内，管理员自定义值在同版本自动迁移重新渲染显示形式时仍会保留。

### Relay Protocol v2 需要手工重建信任关系

首次迁移 5.1.0 之前的配置时，KWC 不会根据旧 Relay v1 的扁平信任关系猜测新的组配置。旧的全局共享密钥、扁平 peer 与转发设置会被废弃，并将 `server-relay.enabled` 重置为 `false`。

请显式创建 `server-relay.groups`。新 group 可先只在一台服务器上保留 `shared-secret: ""`，启动/重载 KWC 生成并保存安全随机值，再把该值原样复制到同一 group 的其他服务器。已有非空 secret 会保留；手工 secret 不足 32 字符时仍保持 invalid。同组双方必须互相登记为 peer。peer 项仅包含 `id`、`url`、`enabled`，不存在 peer 独立密钥。确认所有预期 peer 都已完成双向配置后，再重新启用 `server-relay.enabled`。

开始滚动升级前，请先在仍运行 5.0.0 的服务器上设置 `server-relay.enabled: false` 并 reload。若保持 5.0.0 Relay 开启而只把对端升级到 5.1.0，旧 v1 请求会持续重试，预期的 HTTP 426 响应可能不断累积为 WARN。请先把所有服务器升级并配置好 v2 group/peer，再重新启用 Relay。

Relay v1/BMWC endpoint 会返回 HTTP 426。直接单跳 HTTP peer 的 Relay v2 负载本身仍会经过加密与认证，但会触发安全警告；转发仅允许同组 **HTTPS→HTTPS**。Relay v2 属于**逐跳认证加密（hop-by-hop authenticated encryption）**，不是端到端加密。

### 私聊 Reply 存储升级

5.1.0 为 DM 与群组消息增加持久化回复元数据。现有私聊消息仍然有效。SQLite 群组存储会在架构升级时增加所需的可选回复列；旧 DM/JSONL 记录在没有回复元数据时继续使用省略对应字段的向后兼容格式，无需手工转换数据库。

5.1.0 还增加了按房间保存的群聊成员事件状态。现有 `group-messages.db` 会自动新增 `group_rooms.membership_events_enabled`（默认开启）和 `group_messages.event_type`（默认普通消息）列，无需手工转换数据库。关闭群聊 UI 不会生成退出事件。

跨服务器 DM Reply 使用稳定的 Relay 消息 ID，而不是另一台服务器的本地数据库行 ID。不要在服务器之间复制私聊消息的数字 ID，也不要强制让它们保持一致。

### 表情与 SSE 变化

SSE 默认上限调整为**每个解析后的客户端 IP 10 个连接**、**服务器全局 500 个连接**。对应设置为 `0` 时仍表示关闭该项限制。使用反向代理时请检查 `http.trusted-proxies`；如果配置错误，多名客户端可能都被识别为同一个代理 IP，从而共享同一组按 IP 计算的 SSE 限制。

表情目录的恢复能力也得到增强：临时无法获取 `/emojis` 时会保留上一次成功加载的目录，并使用有上限的指数退避进行重试；SSE 重连后会强制重新同步；服务器端表情目录发生变化时，会通过 `emoji-catalog` 事件通知其他浏览器刷新。`emoji.message-token-limit: 0` 仍表示不限制。

5.1.0 还会规范化自定义表情的包名和项目名：移除不支持的字符与空白；同一包内名称冲突时自动添加数字后缀。如果外部集成依赖固定的磁盘文件名或表情 token，请在升级后检查。

- Android 管理员表情上传改用通用的系统文件/DocumentsUI 选择器，而不是仅限图片的 Photo Picker。桌面端图片过滤和服务器端图片验证保持不变。

### 其他需要确认的行为变化

- DM/群组 Reply 会保存与原消息的真实关系，并在发送时由服务器重新验证当前 DM 会话参与者或群组成员资格。
- 游戏内 `/kchat group` 可正确解析包含空格的房间名、带引号的名称以及连续空格。
- 每个房间的成员通知只针对实际加入/接受邀请和退出/踢出/封禁；关闭或隐藏房间不会被视为退出。
- 聊天设置配置档会准确保留显式值与未设置状态，加载后的字体大小也会应用到已打开的 DM/群组窗口。
- 在窄屏/移动端，DM/群组的时间、Reply 和已读状态会自然换行，不再占用过大的固定列。
- `/kchat reload` 会重新检查对外暴露的 HTTP listener 与 direct HTTP Relay peer，并把相应本地化警告同时写入服务器日志并发送给命令执行者。
- 当 `commands.broadcast-result-to-web-chat: true` 时，Web 命令执行提示也会发送给在线 Minecraft 玩家。
- 公共聊天、DM 和群组消息输入框改用单行 `<textarea>`，而不是普通 text `<input>`，同时保留 `autocomplete="off"`、Enter 发送、光标定位和表情插入行为。这样可以避开 Android 版 Chrome 在无关的普通输入框上也显示密码、地址和付款方式 Autofill accessory 的路径；登录/密码输入框保持不变。
- 从 5.2.0 开始，更新检查器只查询 canonical Modrinth `kokoto-webchat`，不再把旧 BMWC 项目地址作为更新来源。
- 对外暴露的明文 HTTP listener 与直接 HTTP Relay peer 会显示本地化安全警告。

### 升级后检查

1. 检查 `config-version`，并查看生成的 5.1.0 reference/migration 文件。
2. 切换 `ui.language`，确认只改变显示语言而不会改变管理员实际配置值。
3. 使用 Relay 时，确认 v2 双向 peer 配置无误后再重新启用，并确认其他服务器不再依赖旧 v1 endpoint。
4. 位于代理后方时检查解析后的客户端 IP 与 SSE 行为；仅在故障排查期间临时启用 `http.log-client-ip-resolution`。
5. 在实际部署环境中测试公共聊天、DM/群组 Reply、自定义表情、上传、Web Push/通知以及正在使用的地图/standalone frontend。
6. 在正常运行和保留策略清理都确认无误前，保留升级前备份。

## 从 KOKOTO WebChat 5.1.0 升级到 5.2.0

升级前请备份 KWC 数据目录。普通 5.1.0 → 5.2.0 migration 会保留受支持的管理员值和现有 Relay v2 group/secret/peer；relay trust reset 只属于历史上的 pre-5.1.0 → 5.1.0 migration。该 5.2.0 migration 的 reference 为 `config-reference-5.2.0.yml`，自动审核状态在管理员选择精确 `config-version: "5.2.0"` 前使用 `5.2.0_auto_migration`。

5.2.0 将 Relay Protocol 2.1 作为向后兼容的 2.x capability revision。Protocol major `2` 是兼容边界，KWC 产品版本仅用于诊断。公共 reaction、仅发送到参与者服务器的跨服务器 DM reaction 与远程 DM typing 使用 2.1 扩展，共同的 v2 public/DM/read 行为仍属于 2.x compatibility baseline。

新增用户数据包括在 `chat.conversation-archive.enabled` 为 true 时使用的私有对话 snapshot `conversation-archives.db`、反应状态的 `public-reactions.jsonl`（保留历史文件名，存储公共/DM/群聊反应），以及管理员 reaction picker 配置 `reaction-catalog.json`。请与其他 KWC 数据一同备份。snapshot 不复制附件字节，管理员强制删除源消息/房间以及私聊房间锁定策略优先于个人存档。 `reaction-catalog.json` 还会保存管理员的 reaction 总开关与自定义表情允许状态；关闭该功能不会删除已有 `public-reactions.jsonl` 数据。

升级后请验证公共 reaction、DM/群聊“正在输入…”，对话存档与 PDF/打印、私聊房间 Settings/Invite/Leave 权限，以及公共/DM/群聊统一 32px bottom-follow。若使用多个 relay server，为一致使用 reaction/typing 扩展，建议所有 peer 升级到 5.2.0/Relay 2.1。

## 从 KOKOTO WebChat 5.2.x 升级到 5.3.0

首先备份 KWC 数据目录。5.3.0 在自动审核期间使用 `config-version: "5.3.0_auto_migration"`，并生成 `config-reference-5.3.0.yml` / `config-migration-5.3.0.yml`。现有 5.2.x 管理员设置值会保留。已废弃的 `direct-message.confirm-hide` 与 `group-chat.confirm-hide` 会在保留原 boolean 值的情况下迁移到 `confirm-delete`。

此次迁移还会在既有 Relay `groups[].peers[]` 中实际补全缺失的 `send` / `receive` 策略 map，以及缺失的 `public-chat`、`event`、`dm`、`profile` 项，兼容默认值为 `true`。已有显式 map 值与标量 `send: false` / `receive: false` 会保持不变。

KWC 5.3.0 整个版本线都保持 Relay Protocol 2.2。在同一 revision 内通过 capability negotiation 提供发送者拥有的跨服务器 DM 删除（`delete`）、定向事件请求（`game`）以及公开 profile/presence 查询（`profile`）。public/DM/read/reaction/typing 继续使用既有 Relay v2 信任与加密边界；可选 capability 不受支持时仅该功能安全失败，不提升 protocol revision。

DM 不再提供消息级“仅对我隐藏”，只有发送者可以删除自己发送的消息。群聊消息删除对整个房间生效；普通 member 只能删除自己的普通消息，room-local owner/admin 可以管理房间消息。群组置顶按房间保存，所有成员可查看，pin/reorder/unpin 仅限 owner/admin。DM/群聊保存历史搜索采用公共聊天搜索的交互模式。

Presence 区分 Game 与 Web。compact 列表按 Game > Web > Offline 只显示一个代表状态，用户资料中分别显示 Game/Web；账号级 Offline 状态会在服务器端向其他用户隐藏两种真实状态。

升级后请检查 BlueMap 刷新恢复、DM/群聊搜索与删除、群组置顶/角色、Game/Web presence、Offline 隐私，以及所有相关 peer 升级后的跨服务器 DM 删除。
