# KOKOTO WebChat 配置参考

## 5.0.0 内容过滤、Web Admin 设置、会话与上传文件名

`content-filter` 是游戏/Web 公共聊天、群聊以及可选 DM 共用的跨加载器过滤器。大量过滤词保存在 UTF-8 `plugins/KOKOTO-WebChat/filter-lists/*.txt` 文件中，每行一个词；空行和 `#` 注释会被忽略。每个列表默认使用 `block`，Web Admin 可为每个列表选择拦截或过滤；过滤模式使用 `content-filter.mask.text` 遮罩匹配内容。更细的 block/mask/replace 继续使用 `config.yml` 的 `content-filter.rules` 自定义规则。`replace` 的 `first` 始终使用第一个替换词，`random` 从候选词中随机选择；如果设置了逐词映射则优先使用映射值。Unicode 规范化与 compact/interleave 匹配可检测空格、符号、兼容字符以及 `욕1설` 这类有限插入规避。只有实际注册的 KWC 表情 token 是保护边界，未注册的 `:fake:` 形式仍按普通文本检查。


### 自定义过滤器快速用法

大量简单词请优先使用 UTF-8 **Filter word list** TXT 文件，每行一个词，并为列表选择 **Block** 或 **Filter（mask）**。需要 `replace`、多个替换候选或逐词替换时使用 **Custom rules**。

- **Block**：命中后拒绝整条消息。
- **Mask**：仅把命中范围替换为 `content-filter.mask.text`（默认 `***`）。
- **Replace + First**：始终使用公共替换候选中的第一个。
- **Replace + Random**：每次命中从公共候选中随机选择一个。
- **Per-word replacements**：使用 `word1 => 替换 A` 为不同词指定不同结果。左侧映射键即使没有写在 Target words 中，也会自动成为目标词，并优先于公共候选。

```text
Rule ID: soften-words
Action: Replace
Target words:
word1
word2

Replacement candidates:
较温和表达
另一种表达

Replacement mode: First

Per-word replacements:
word2 => 指定表达
```

`compact-match` 检测插入空格/分隔符后紧凑形式仍相同的规避；`interleave-match` 检测在 `interleave-max-gap` 范围内插入字母或数字的规避；`interleave-unlimited-gap: true` 会取消间隔限制，因此也更容易扩大误判范围。像 `ㅅㅂ` 这样的纯韩文字母规则只按字母缩写处理，完整韩语词按完整音节比较。

保存后请使用 Filter 页面中的 **Test**。它不会发送消息，可同时测试 TXT 列表和自定义规则，即使实时过滤器关闭也能测试。Rule / Word / Match 会显示命中规则、目标词以及 `literal`、`compact`、`interleave` 匹配方式。


Web Admin 提供 **Filter** 和 **Settings** 页签。Filter 可管理适用范围、规避检测、可逐列表选择拦截/过滤的过滤词列表、自定义规则的新增/编辑/删除以及不实际发送的测试。Settings 只暴露适合实时修改的 guest/CAPTCHA、认证/会话、用户配置文件、公开聊天/DM/群聊 typing-indicator 策略以及是否允许用户级显示设置、upload 和 Discord 管理员提醒值。`moderation.*` 以及 relay/network/adapter 拓扑仍仅通过 `config.yml` 管理。游戏内可使用 `/kchat filter ...` 和 `/kchat settings ...`。

修改 `auth.remember-session-days` 会从各会话原始 `createdAt` 重算现有 USER/MODERATOR 会话；`admin.admin-session-expire-hours` 独立重算 ADMIN 会话。`0` 在 USER/MODERATOR 与 ADMIN 两种会话期限设置中都表示无限期。已经过期的会话不会因延长期限或改为无限期而复活；超过新缩短期限的会话会立即失效。编辑 `config.yml` 后启动/reload 时也应用同一策略。

`upload.filename-mode` 默认是 `random`。`original` 为新上传保留经过安全处理的 Unicode 原文件名，只清理危险路径、控制字符和文件系统非法字符；同名时使用 `-2`, `-3` ... 后缀而不覆盖已有文件。现有上传不会改名。


本文说明 `plugins/KOKOTO-WebChat/config.yml`。

## 配置版本与迁移片段

`config-version` 用于选择自动 migration 行为。重建时使用由 `ui.language` 选择的**当前版本内置显示模板**：`en-US` 使用 `config.yml`，`ko-KR`/`ja-JP`/`zh-CN` 使用各自的内置本地化模板。`config-reference-<plugin-version>.yml` 是从所选模板生成的管理员可读默认配置，不作为 migration 输入。默认值的语义比较仍以 canonical 英文 `config.yml` 为准，四个内置模板的解析值必须完全一致。

如果 `config-version` 缺失或属于其他版本，KWC 会读取现有配置值，以最新内置 `config.yml` 创建新文件，再把现有用户值覆盖到新默认配置上。若旧 marker 不带 `*_auto_migration`，则视为管理员曾固定过该配置，并在重建前完整备份原 `config.yml`。旧注释、顺序、空白和缩进不会继承；以最新内置注释/布局为准，同时保留管理员设置值。已废弃设置不会重新写回。结果标记为 `<plugin-version>_auto_migration`。只要该 marker 保留，startup/reload 都会重复同样的“最新默认配置 + 当前值覆盖”过程，从而自动获得新增设置和最新注释/布局。精确的 `<plugin-version>` 表示当前版本配置已固定，同版本 startup/reload 不会重写 `config.yml`。

`config-migration-<plugin-version>.yml` 是 review/diff 报告。旧版本生成的 `config-reference-*`、`config-migration-*`、`config-upgrade-*` 会自动清理；只有用于真实版本升级默认值比较的 JAR 内部 `config-baselines/*` 会保留。
5.3.0 中，`ui.language` 不仅选择 Web UI 语言，也选择 KWC 重建 `config.yml` 时的注释/呈现语言、`config-reference-5.3.0.yml` 以及 migration/difference 报告语言。内置 template 为 `en-US`、`ko-KR`、`ja-JP`、`zh-CN`；切换语言只改变注释/布局，Relay group/secret/peer 等现有已解析管理员值会 overlay 回新模板并保持不变。Difference 判断比较的是已解析 YAML setting path + value，而不是注释、空白、缩进、引号样式、行号或 key 顺序。

## 总开关

新生成的 config 顶层默认为 `enabled: false`。在此状态下，KOKOTO WebChat 只会生成/读取配置，/kchat reload 仍可使用，但不会启动 Web/聊天服务、监听器、Discord 集成、私信存储、插件网页安装、上传/表情初始化或清理任务。已有 config 如果没有此键，为了升级兼容会视为已启用。请先检查存储方式、保留期限、上传、预览、认证和对外公开设置，再改为 `enabled: true`。

## 更新检查

```yaml
update-check:
  enabled: true
```

启用后，KOKOTO WebChat 会在 Bukkit、Fabric、NeoForge 和 Forge 上统一在后台检查 canonical Modrinth `kokoto-webchat` 项目的最新正式版本。从 5.2.0 开始，旧 BMWC 项目地址不再作为更新来源查询。OP 或拥有 `kwc.update.notify` 权限的玩家登录时会按限频规则重新查询，因此新版本检测不再只依赖定时查询结果。
## 部署模式

### BlueMap 插件模式

```yaml
adapters:
  bluemap:
    auto-install: true
    auto-patch-webapp-conf: true
```

在 Bukkit 上，KWC 将资源安装到 `addons/kokoto-web-chat` 并更新 BlueMap `webapp.conf`。在 Fabric/NeoForge 以及 Forge 26.1.2/26.2 的 BlueMap mod 环境中，KWC 改用 BlueMapAPI 2.8.0 获取 web root 并注册 JS/CSS；`auto-patch-webapp-conf` 和两个 BlueMap 路径 override 不参与运行时注册。

### 仅 standalone

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

访问 `http://<server-host>:8899/`。

### HTTPS 反向代理

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
  # 推荐留空。上传 URL 会自动跟随当前 API base。
  # 旧式显式值也可用: "/chat/api" 或 "/chat/api/uploads"
  public-base-url: ""

emoji:
  # 推荐留空。表情 URL 会自动跟随当前 API base。
  # 旧式显式值也可用: "/chat/api" 或 "/chat/api/emojis"
  public-base-url: ""
```

### 公开 URL 选项规则

- `http.path-prefix` 是插件内部 HTTP API 路径。通常保持默认 `/api` 不变。
- `adapters.bluemap.api-base-url` 是 BlueMap 内嵌聊天使用的公开 API base。HTTPS 反向代理中通常设为 `/chat/api`。
- `frontend.standalone.api-base-url` 通常留空。直接 HTTP 使用 `http.path-prefix`，通过反向代理时使用 `http.public-prefix + http.path-prefix`，默认公开 API 为 `/chat/api`。它不会继承 BlueMap adapter override。
- `upload.public-base-url` 通常留空。留空时使用当前 API base 加 `/uploads`，例如 `/chat/api/uploads`。
- `emoji.public-base-url` 通常留空。留空时使用当前 API base 加 `/emojis`，例如 `/chat/api/emojis`。
- 也支持显式值。设置 `/chat/api` 时，upload 会自动追加 `/uploads`，emoji 会自动追加 `/emojis`；`/chat/api/uploads` 和 `/chat/api/emojis` 会原样使用。
- 不带前导 `/` 的相对值，例如 `chat/api`、`chat/api/uploads`、`chat/api/emojis`，会在 `http.cors-origin` 为实际 origin 时自动加上该 origin。若 `cors-origin: "*"`，则按同源绝对路径 `/chat/api...` 处理。
- `https://map.example.com/chat/api` 这样的完整 URL 会原样使用。

## 聊天记录存储

聊天记录通过 `chat.history-storage` 选择 `memory`、`jsonl` 或 `sqlite`。`chat.history-size` 和 `chat.history-retention-days` 在三种模式中共用。`0` 表示不限制数量/期限。新生成的 config 顶层默认为 `enabled: false`，因此在检查这些值并设置 `enabled: true` 前不会执行清理任务。如果服务器策略需要自动清理旧聊天，请设置正数保留天数，例如 `30` 或 `90`。上传和外部媒体缓存保留设置也按同样方式工作。`chat.history-file` 仅用于 JSONL，`chat.history-sqlite-file` 仅用于 SQLite。当 `chat.history-sqlite-migrate-jsonl: true` 且 SQLite 数据库为空时，会一次性导入现有 `chat.history-file`。手工编辑、大规模清理或迁移前，请正常备份 `history.db`。

## 消息令牌

`message-tokens.enabled` 启用以冒号包围的管理员自定义文本/控制 alias。配置中只写不带冒号的 alias 名，例如 `enter` 在聊天中输入为 `:enter:`。内置 alias 只提供英文默认值，管理员可改成或追加任意语言。未注册 alias 保持原样，因此不会破坏自定义/ImageEmojis 令牌。

YAML 列表设置同时支持 inline 形式（`aliases: [bullet, arrow]`）和 block 形式（`aliases:` 下一行使用 `- bullet`）。缩进只能使用普通 ASCII 空格，不能使用 tab 或全角空格。`/kchat reload` 会在停止在线服务之前验证配置，因此无效 YAML 会被拒绝，当前运行配置和 UI 语言会继续保持。

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
  newline:
    aliases: [enter, newline, nextline, linebreak, br, next]
  custom:
    separator:
      aliases: [separator, divider, line]
      replacement: "────────────"
```

## 1:1 私信会话

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

启用 `direct-message.enabled` 后，已绑定或曾被服务器识别的玩家之间可使用持久化 1:1 私信会话。A→B 与 B→A 使用同一个 UUID 对应会话。存储方式、保留期限、消息数量限制与通知选项以当前默认 config 为准。

`group-chat.admin-audit.enabled` 是 4.6.3 新增的独立、默认关闭的群聊正文访问开关。即使启用，账号仍必须列在 `private-chat-super-admins` 中。管理员视图为只读，不要求房间成员身份，也不会加入房间或更新已读状态；每次分页读取都会记录为 `admin.group-audit-read`，且不会把消息正文复制到审计日志。

`direct-message.admin-audit.enabled` 是独立且默认关闭的私信正文审计开关。即使启用，也只有同时明确列在 `private-chat-super-admins` 中的账号可以在只读审计视图中打开私信正文。审计视图不能发送、回复、删除消息或更新已读状态；每次分页读取都会写入审计日志，但不会把正文复制到日志中。

`capture-game-whispers` 会把未取消的 `/w`, `/msg`, `/tell`, `/whisper`, `/m`, `/pm`, `/message`, `/t` 复制到发送者和接收者的 KWC DM 会话。它不会重新发送或替换 Minecraft 私聊。Bukkit 无法统一获得所有私聊插件的最终成功结果，因此以格式正确、目标为已知玩家的命令作为记录条件。

## 重要的 0 值语义

`0` 在不同设置中并不具有统一含义。以下说明以当前 5.3.1 loader/runtime 的实际行为为准；实际说明不同的设置不得自行推断为“无限制”。

- `chat.history-size`: 按数量保留的公开聊天历史最大行数，与按时间保留策略同时生效。 0 表示不限制数量。
- `chat.history-retention-days`: 公开聊天历史的按时间保留天数。 0 表示不按时间过期。
- `chat.history-page-size`: 每页默认请求的历史消息数量。设为 0 时，memory/JSONL 历史不设置显式分页上限，但 SQLite 仍应用内置的 500 行查询安全上限。
- `chat.conversation-archive.enabled`: 启用账号级**保存对话**功能。设为 `false` 时不会注册 archive API 路由，不会打开或新建 `conversation-archives.db`，Web UI 也不会生成保存对话相关 DOM。已有 archive 数据保持不变。
- `chat.conversation-archive.max-archives-per-user`: 单个账号可拥有的保存 snapshot 最大数量。默认 `100`，允许范围 `1-1000`。
- `chat.conversation-archive.max-messages-per-archive`: 单个 snapshot 的最大消息数。默认 `1000`，允许范围 `1-10000`。较大的值会增加范围读取、内存、响应和 SQLite 写入开销。
- `chat.conversation-archive.max-messages-per-user`: 单个账号在全部保存 snapshot 中可保存的消息总数上限。默认 `10000`，允许范围 `1-100000`。若小于 per-archive 上限，则较小的账号总量限制优先。保存 snapshot 不会因普通聊天 retention 自动过期。降低上限不会删除或隐藏已有 snapshot，只会拒绝会超过当前上限的新保存。
- `chat.max-message-length`: KWC 接受的普通公开聊天消息最大长度。 0 表示不限制此长度。
- `chat.max-url-message-length`: 包含 URL 的公开聊天消息最大长度。若为正数，会保证不小于正数的普通消息长度限制。 0 表示不限制此长度。
- `message-tokens.max-replacements-per-message`: 单条消息最多执行的 token 替换次数，用于限制替换工作量。 0 表示不限制数量。
- `reply.game-preview.max-length`: Minecraft Reply 引用预览中显示的原文最大长度。 0 表示不截断预览。
- `pinned.max-pins`: 同时保持置顶状态的公开消息最大数量。 0 表示不限制数量。
- `direct-message.retention-days`: 已存储 DM 消息的按时间保留天数。 0 表示不按时间过期。
- `direct-message.max-messages-per-thread`: 每个 DM thread 按数量保留的最大消息数。 0 表示不限制数量。
- `direct-message.max-message-length`: Web/游戏发送可接受的 DM 消息最大长度。 0 表示不限制此长度。
- `group-chat.retention-days`: 已存储群组房间消息的按时间保留天数。 0 表示不按时间过期。
- `group-chat.max-messages-per-room`: 每个群组房间按数量保留的最大消息数。 0 表示不限制数量。
- `group-chat.max-message-length`: 群组房间可接受的消息最大长度。 0 表示不限制此长度。
- `group-chat.max-rooms-per-user`: 房间管理校验中，单个用户可拥有/加入的群组房间最大数量。 0 表示不限制数量。
- `group-chat.max-members-per-room`: 单个群组房间允许的最大成员数。 0 表示不限制数量。
- `group-chat.invite-expire-hours`: 群组房间邀请有效期（小时）。0 并不表示无限期。 运行时最小值为 1；更小的值会提高到最小值。
- `guest.cooldown-seconds`: 同一 resolved client identity/IP 连续发送访客消息的最小间隔（秒）。0 表示关闭此 cooldown 限制项。
- `guest.max-messages-per-minute`: 对同一 resolved client identity/IP 应用的每分钟访客消息限制。0 表示关闭此每分钟限制项。
- `captcha.expire-seconds`: 每个已签发验证码题目的有效期（秒）。该值不钳制；0/负数会让新题立即或几乎立即过期。
- `captcha.pass-valid-minutes`: 未要求每条消息验证码时，一次成功的验证码状态可复用的时间（分钟）。 运行时最小值为 1；更小的值会提高到最小值。
- `auth.link-code-cooldown-seconds`: 同一 client/user 重复签发账号绑定码的最小间隔（秒）。 0 表示关闭此限制项。
- `auth.link-code-max-per-minute`: 签发 rate limiter 中每分钟允许生成的账号绑定码最大次数。 0 表示关闭此限制项。
- `auth.remember-session-days`: 普通 USER/MODERATOR Web 会话的过期天数。<=0 时不设置 expiry timestamp。
- `security.login-fail-limit`: 在配置的失败统计窗口内触发基于 IP 临时锁定的登录失败次数。 0 表示关闭此限制项。
- `security.login-lock-seconds`: 超过登录失败限制后，基于 IP 的登录锁定持续时间（秒）。 0 表示关闭此限制项。
- `security.max-sse-connections-per-ip`: 单个 resolved client IP 允许的并发 /stream SSE 连接最大数量。使用反向代理时应正确配置 http.trusted-proxies，避免所有客户端都显示为代理 IP。 0 表示关闭此限制项。
- `security.max-sse-connections-total`: 整个 KWC 服务器允许的并发 /stream SSE 连接最大数量。 0 表示关闭此限制项。
- `admin.admin-session-expire-hours`: ADMIN Web 会话过期时间（小时）。<=0 时管理员会话不设置 expiry timestamp。
- `moderation.default-mute-minutes`: 未指定时默认禁言时长（分钟）。<=0 表示永久禁言；此设置仅限 config。
- `moderation.allow-user-self-message-delete`: 允许普通用户删除自己在公开聊天、DM 和群聊中发送的消息。默认值为 `false`。DM 仍只能由发送者删除自己的消息；普通群成员删除自己的消息还要求启用该房间的成员自删策略和房间消息删除功能。
- `moderation.self-message-delete-window-minutes`: 普通用户删除自己消息的时间窗口。`0` 表示不限时间；正数表示发送后超过该分钟数，普通用户不能再删除。ADMIN/MODERATOR 删除不受此时间限制。
- `commands.max-length`: Web 命令执行接受的命令文本最大长度。 0 表示不限制此长度。
- `ui.image-preview-max-per-message`: 单条消息渲染的 inline 图片预览最大数量。 0 表示不限制数量。
- `ui.image-preview-max-height`: 图片预览的配置高度上限（px）。即使为正数也仍受聊天 viewport 安全上限约束；0 只取消此明确 px 上限并改用自动 viewport 上限，因此并非真正无限。
- `ui.max-width`: 配置的 KWC 面板最大宽度（px）。 0 仅取消配置的最大值；浏览器/viewport 限制仍可能生效。
- `ui.max-height`: 配置的 KWC 面板最大高度（px）。 0 仅取消配置的最大值；浏览器/viewport 限制仍可能生效。
- `ui.user-profiles.max-profiles`: 每个账号可在服务器保存的 preference 配置档最大数量。 运行时范围为 0-20，超出范围会钳制到边界值。 0 表示关闭服务器端保存的配置档。
- `ui.virtual-scroll.overscan-screens`: virtual-scroll 可见窗口上下额外渲染的 viewport 屏幕距离。 0 是有效的最小行为值。
- `ui.virtual-scroll.min-rendered-messages`: 即使 viewport 计算需要更少，也保持渲染的最小消息行数。 0 是有效的最小行为值。
- `discordsrv.max-emoji-links-per-message`: 单条 Discord 消息附加的 custom-emoji 图片 URL 最大数量。与 emoji.game-link.max-links-per-message 不同，本设置中的 0 表示禁用。 0 表示不向 Discord 附加 emoji 图片 URL。
- `discordsrv.reply-relay.preview-max-length`: Discord Reply preview 中包含的被回复原文最大长度。 0 表示不截断预览。
- `upload.cooldown-seconds`: 同一 resolved client IP 两次上传尝试之间的最小间隔（秒）。 0 表示关闭此限制项。
- `upload.max-uploads-per-minute`: 单个 resolved client IP 每分钟上传尝试限制。 0 表示关闭此限制项。
- `upload.max-file-size-mb`: 单个上传文件允许的最大大小（MiB）。 0 表示不限制此大小/配额。
- `upload.max-total-size-mb`: upload.directory 的总存储配额（MiB）。超额时先删除最旧的未引用上传；若仍无法腾出足够空间，则拒绝新上传。 0 表示不限制此大小/配额。
- `upload.max-files-per-message`: 一次 composer 上传操作可选择/附加的最大文件数。 0 表示不限制数量。
- `upload.retention-days`: 仅删除超过此天数且已不再被保留消息/pin 引用的上传文件。 0 表示关闭按时间清理。
- `preview.youtube-max-embeds-per-message`: 单条消息渲染的 YouTube embed 最大数量。 0 表示不限制数量。
- `preview.social-embeds.max-embeds-per-message`: 单条消息渲染的受支持社交 embed 最大数量。 0 表示不限制数量。
- `preview.external-media-cache-max-size-mb`: KWC 会获取/缓存的单个外部媒体对象最大大小（MiB）。 0 表示不限制此大小/配额。
- `preview.external-media-cache-retention-days`: 删除未引用 external-media cache 文件的按时间天数。 0 表示关闭按时间清理。
- `emoji.max-file-size-kb`: 单个 custom emoji 文件大小限制（KiB）；超出时会根据所用流程在管理 catalog 操作中被拒绝/忽略。 0 表示不限制此大小/配额。
- `emoji.max-total-size-mb`: 管理 emoji 文件的总 storage/catalog 配额（MiB）。超过配额的上传会被拒绝，catalog 扫描也不会暴露超出总限制的文件。 0 表示不限制此大小/配额。
- `emoji.message-token-limit`: 单条消息允许的 custom emoji token 最大数量。token 可包含规范化的 pack/name 路径。 0 表示不限制数量。
- `emoji.game-link.max-links-per-message`: link 模式下单条游戏消息附加的 emoji 图片链接最大数量。 0 表示不限制数量。

## 访客聊天限制

```yaml
guest:
  cooldown-seconds: 6
  max-messages-per-minute: 50
```

访客聊天同时受 `cooldown-seconds` 和 `max-messages-per-minute` 限制。每分钟消息数默认值为 `50`。已有服务器的配置文件不会自动覆盖；如果要在现有安装中使用新的默认值，请手动更新 `plugins/KOKOTO-WebChat/config.yml`。

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

点击非 URL 正文会建议 `/kchat reply <messageId> `，URL 片段仍优先打开链接。`local-game-chat` 让普通本地游戏聊天也可点击回复；若其他聊天格式插件必须独占最终渲染，请关闭。点击同服游戏发送者名称会建议 `/w <真实名称> `；已关联 Web 发送者和其他服务器的游戏发送者会建议 `/kchat dm <真实名称> `。
启用 `reply.game-click.enabled` 后，点击由 KWC 渲染的非 URL 消息正文会准备 `/kchat reply <messageId> `；执行 `/kchat reply <messageId> <message>` 会创建与 Web Reply 使用相同 `replyTo` metadata 的公开消息。
DM/group 消息也使用相同的游戏点击模型：会话标签准备现有 `/kchat dm ...` 或 `/kchat group ...` 命令，正文准备内部 private reply target。发送前会重新验证私信参与者/当前群组成员身份，因此内部 ID 本身不是权限凭据。


游戏回复会保留玩家输入的自定义表情 token，用于 Web 历史和服务器中继；在发送端服务器的 Minecraft 输出中，则复用游戏侧表情插件处理后的命令正文来显示表情。若没有处理后的 glyph 且 `emoji.game-link.mode` 为 `preserve`，已识别 token 会以普通 Bukkit 聊天行输出以兼容游戏侧渲染器；该兼容行无法附带 KWC 的点击和 hover metadata。



`server-relay` 用于连接多个 KOKOTO WebChat 服务器的公共聊天。游戏、已关联 Web 用户和访客消息可发送到远端服务器的 Web 聊天与 Minecraft 聊天，同时保留消息 ID、回复关系、发送者和来源服务器信息。

## 服务器中继配置

KOKOTO WebChat 5.3.0 使用 Relay v2 信任/加密模型并保持 **Relay Protocol 2.2**。relay group 本身就是信任边界；同一 group 内所有 peer 共享一个 group `shared-secret`。peer 项保留 `id`、`url`、`enabled`，并可在 `send` / `receive` 下分别配置 `public-chat`、`event`、`dm`、`profile`。省略的策略默认启用。不存在 `peers[].secret`。

```yaml
server-relay:
  enabled: true
  server-id: "server-1"
  server-name: "Server 1"
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
    event: true
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  groups:
    - id: "main"
      shared-secret: ""
      forwarding:
        enabled: false
      peers:
        - id: "server-2"
          url: "https://server2.example.com/api"
          enabled: true
          send:
            public-chat: true
            event: true
            dm: true
            profile: true
          receive:
            public-chat: true
            event: true
            dm: true
            profile: true
```

首次设置时，只在一台服务器上保留 `shared-secret: ""`，启动/重载后重新打开该服务器的 `config.yml`，再把自动生成的值原样复制到同一 **group** 的其他服务器。不要让每台服务器分别从空值生成，否则会得到不同 secret 而无法互相认证。已有 non-empty secret 会保留；手工 secret 不足 32 字符时不会自动替换，而会保持 invalid/fail-closed。双方必须在同一 group 中互相登记 peer，并使用完全相同的生成/复制 secret。同一个 peer ID 不能登记到多个本地 group，重复登记会被禁用。direct relay 会逐个独立认证/加密 `/relay/v2/message` 请求。`/relay/v2/handshake` 是无状态的诊断 identity/health probe，不控制 routing。

Relay v2 通过 `/relay/v2/message` 传递 public chat 以及跨服务器 1:1 DM/read receipt。payload 使用方向性 HKDF-SHA256 key 与 AES-256-GCM 做 hop-by-hop 保护。direct 1-hop HTTP 仍可在 payload 加密/认证的状态下使用，但会产生明确警告。forwarding 只发生在同一 group 内并按 peer 单独过滤：使用 `http://` 的 peer 只会被排除在经过该 peer 的 forwarding 之外，同组其他 `https://` peer 仍可继续参与。

首次从 5.0.0 迁移到 5.1.0 时，KWC **不会**根据旧 flat relay 配置猜测 group。旧 relay trust key/peer/forwarding 会被废弃，`server-relay.enabled` 安全重置为 `false`，管理员必须显式定义 v2 group 后再重新启用 relay。

完整 protocol、trust、migration、forwarding 与诊断请参阅 `docs/zh-CN/SERVER_RELAY.md`。

## Discord 转发选项

```yaml
discordsrv:
  web-to-discord-format: "[{server}] [Web] {sender}: {message}"
  game-relay-format: "[{server}] {sender}: {message}"
  game-relay-mode: "discordsrv"
  append-web-emoji-links: true
  append-game-emoji-links: true
  reply-relay:
    enabled: false
    prefix-enabled: true
    preview-enabled: true
    preview-max-length: 120
```

格式支持 `{server}`, `{server_id}`, `{sender}`, `{name}`, `{role}`, `{source}`, `{message}`, `{channel}`。中继启用时，旧格式若没有服务器占位符会自动添加 `[server-name]`。多个服务器共享同一 Discord 频道时，只有实际检测到本地 Minecraft 聊天的来源服务器 KWC 才会给 DiscordSRV 的普通游戏转发添加服务器名和表情链接，其他服务器不会编辑该消息。接收端不会把服务器中继消息再次发送到 Discord，因此来源服务器的 Discord 集成关闭或失败时，不存在由其他服务器代发的 relay-only fallback。DiscordSRV 负责普通游戏聊天转发时，请使用 `game-relay-mode: "discordsrv"`。若由 KWC 直接发送，请使用 `kwc` 并关闭 DiscordSRV 的普通游戏聊天转发以避免重复。

## 管理员 Discord 关键词提醒

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

这是服务器管理员共用策略，不是个人用户 Discord 通知。关键词匹配、来源过滤、mention、格式和去重由 KWC 负责；DiscordSRV 只提供已认证的 JDA 连接与 Channels 映射。Web Admin 的提醒频道选择器只显示 DiscordSRV 的逻辑频道名；存在逻辑名时不会显示内部数字 ID。`channel: ""` 复用 `discordsrv.channel`，数字 ID 只在 ID-only fallback 时接受。来自 Discord 的消息不会再次触发提醒，DM/群聊范围默认关闭。

## 置顶消息

`pinned.show-to-logged-out` 控制未登录访问者是否能看到置顶消息。如需仅登录用户可见，请设为 `false`。

## 置顶/删除显示开关

为避免误点，单条消息上的置顶/删除按钮默认隐藏。ADMIN/MOD 用户可以在管理面板中，使用“删除全部公开聊天记录”按钮旁边的置顶/删除开关来显示这些按钮。该开关不会持久保存，刷新后会恢复为关闭。

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

KOKOTO WebChat 会把自定义表情文件保存到 `plugins/KOKOTO-WebChat/emojis`。子文件夹会作为表情包处理。从 5.1.0 起，表情包目录名与表情文件名 stem 使用同一 token-safe 规则规范化；空格/不可用字符会被删除，已有不规范名称会在启动时统一更名，冲突时追加数字 suffix。最终路径与 `:pack/name:` token 一致。

默认情况下，`emoji.game-link.enabled` 为 `false`，因此 Web→游戏消息会保留 `:pack/name:`、`:emoji:pack/name:` 这样的自定义表情 token。若 ImageEmojis 或其他游戏侧表情插件会在 Minecraft 聊天中渲染 token，请使用这个默认行为。

当 `emoji.game-link.enabled` 为 `true` 时，`emoji.game-link.mode` 支持 `preserve`、`link` 和 `label`。

- `preserve`: 即使 game-link 已启用，也强制保持 token 不变。
- `link`: 发送 `label-format` 文本，并附加一个短 KOKOTO WebChat 图片链接。
- `label`: 只发送 `label-format` 文本。

`emoji.game-link.*` 只影响 Web→Minecraft 聊天。在 `discordsrv` 模式下，KWC 会直接扫描 DiscordSRV 实际发送到 Discord 的游戏消息中的 `:emoji:` token，并把它交给与 Web→Discord 相同的 KWC token→link 处理路径。LOWEST 阶段记录的游戏聊天信息只用于共享 Discord 频道中的来源服务器判定；Minecraft glyph 或游戏渲染后的文本不会作为 Discord 表情转换输入。Discord 图片预览链接分别由 Web→Discord 的 `discordsrv.append-web-emoji-links` 和 Game→Discord 的 `discordsrv.append-game-emoji-links` 控制。

KOKOTO WebChat 会在 Web 历史和中继 payload 中保留规范的表情 token。启用 ImageEmojis 或 ImageEmojis-Bero 时，KWC 会通过 reflection 读取其公开的 runtime 表情 repository，并在构建可点击的 Minecraft component 前把 token 转换为接收服务器当前使用的 glyph。该机制不增加硬依赖，也不会解析资源包。

对于交互式聊天行，KWC 会先插入 ImageEmojis glyph，再构建发送者、回复和 URL 点击事件，因此表情显示与可点击链接可以同时工作。只有接收服务器无法解析的已知 token 才会使用单行 plain Bukkit fallback 兼容其他游戏侧 renderer；该 fallback 无法携带 KWC 的点击或 hover metadata。

`default-pack` 和 `aliases` 可用于把扁平的游戏侧 token 映射回 KOKOTO WebChat 的 pack/name id。例如：

```yaml
emoji:
  game-link:
    default-pack: "default"
    aliases:
      wave: "default/wave"
```

GIF/JPG/JPEG/WEBP 表情原文件会自动获得同文件夹 PNG sidecar，以兼容只读取 PNG 文件的游戏侧表情插件。Web UI 会继续使用原始文件，因此 GIF 动画会保留。

### ImageEmojis-Bero 1.9.x

在 Bukkit/Paper 系列可让 [ImageEmojis-Bero](https://github.com/KOKOTO-DEV/ImageEmojis-Bero) 与 KWC 共用 `plugins/KOKOTO-WebChat/emojis`。关键配置为 `serverIp`、`webServerPort`、`emojisFolder: /KOKOTO-WebChat/emojis`、`templateFormat: ':<emoji>:'`、`replaceInCommands: true`。资源包 HTTP 主机/端口必须能被 Minecraft 客户端访问，并且与 KWC Web 端口相互独立。详见 `IMAGEEMOJIS_BERO_1_9_0.md`；常规安装运维请参考 [上游 ImageEmojis](https://github.com/MrQuackDuck/ImageEmojis)。

### SimpleNicks-Bero

Bukkit/Paper 系列使用 [SimpleNicks-Bero](https://github.com/KOKOTO-DEV/SimpleNicks-Bero) 时，将 `player-display.mode` 设为 `"display-name"`。KWC 独立保存真实 linked account/UUID identity。详见 `SIMPLENICKS_BERO.md`；常规安装运维请参考 [上游 SimpleNicks](https://github.com/Simplexity-Development/SimpleNicks)。

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

`ui.image-preview-max-height` 用于限制图片、GIF、视频和 iframe 类预览的显示高度。推荐范围为 `640-720`，默认值为 `720`。 `ui.image-preview-max-height` 设为 `0` 时，只取消明确的 px 上限，自动按 viewport 计算的安全上限仍会生效，因此并不是真正的无限高度。

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


`emoji.favorites.enabled` 控制是否创建 custom emoji Favorites UI。`emoji.favorites.storage` 可选 `account`（默认，与 chat history 存储方式无关的已登录账号 `user-preferences`）或 `browser`（localStorage）。`emoji.favorites.max-per-account` 默认 100；`0` 表示无限制，正数表示最大保留数量。

## 用户配置与账号设置

```yaml
ui:
  user-profiles:
    enabled: true
    max-profiles: 5
    allow-import-export: true
```

登录用户可把主题、字体、字号、颜色、透明度、text shadow 和语言等视觉设置保存为多个服务器端账号配置。`max-profiles` 允许 0-20，`0` 表示禁用服务器配置保存。JSON 导入/导出仅接受 16 KiB 以下的严格 flat schema，不包含会话 token、UUID、Web Push endpoint 或窗口坐标。窗口位置/尺寸、最小化状态和 Web Push 注册仍保持设备本地。这三个项目可在 Web Admin Settings 中调整。

## 浏览器通知和 Web Push

`notifications` 控制浏览器通知与移动/后台 Web Push 共用的默认值和服务器端允许上限。`notifications.enabled` 是两个投递路径的单一默认开关；旧的 `browser-notifications.*` 和 `web-push.notify-*` 键只作为迁移/兼容输入读取。`notify-*` 为 `true` 时用户可在聊天设置中自行开关；设为 `false` 时，即使用户启用，该通知类型也会被阻止。`notifications.notify-reactions` 控制实时浏览器通知与后台/移动 Web Push 共用的单个**表情反应**复选框，不拆分为浏览器和 Push 两个选项。从 5.0.0 开始，登录用户的通知类型和关键词列表只需保存在账号数据中一次，并在不同浏览器/设备间复用；首次初始化时会把现有浏览器值提升为账号设置。访客继续使用浏览器本地设置。每台设备的 Web Push 订阅只保留后台投递所需的 endpoint/filter 数据。

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

`auth.link-code-cooldown-seconds` 和 `auth.link-code-max-per-minute` 用于限制 Web UI 按远程 IP 生成 `/kchat auth <code>` 链接代码的频率。将任一值设为 `0` 可禁用对应限制。


### 上传存储容量上限

`upload.max-total-size-mb` 用于限制 `upload.directory` 直属普通文件的总容量。默认值 `0` 表示不限制。新的上传会超过限制时，KOKOTO WebChat 会先删除最旧的未引用上传文件；仍被聊天记录、SQLite 历史、DM/群聊消息或保留的置顶消息引用的文件会被保留。清理后仍空间不足时，上传会被拒绝。

### 表情容量显示

`emoji.max-total-size-mb` 用于限制自定义表情的总容量。超过限制时，管理员上传界面会显示警告。`emoji.show-storage-usage` 控制是否显示当前表情容量，`emoji.show-storage-limit` 控制是否显示总容量限制。



## UI 时区

`ui.time-zone` 用于指定聊天时间显示的时区。`local` 表示使用浏览器/设备本地时区，也可以使用 `UTC` 或 `Asia/Seoul` 等 IANA 时区。无效值会在 Web UI 中回退到本地时间。


## 重复运维错误日志

KWC 不再为每个 HTTP 状态码单独堆叠日志例外，而是对可重复出现的运维 HTTP/网络故障使用统一控制台策略。同一 operation/target 的首次故障会立即记录；相同状态的重复故障会被抑制，并在后续摘要中报告省略次数。故障状态发生变化时会立即记录新状态；在重复日志被抑制后恢复正常时，会输出一次恢复摘要。该策略用于 Relay 验证/HTTP 传输、更新源查询、运维 API 的 rate/server 故障等重复 transport 类问题；普通用户输入验证/认证失败响应不会升级为服务器控制台错误。实际 retry/backoff 仍由各功能独立控制，与日志抑制分离。

## HTTP 代理 / 客户端 IP

`http.trusted-proxies` 用于指定哪些代理的 `X-Forwarded-For` 可以被信任。直接公开 HTTP 时请保持为空；如果同一台服务器上使用 Caddy/Nginx，请将 `127.0.0.1` 和 `::1` 写成块状 YAML 列表。`http.log-client-ip-resolution: true` 只建议临时开启，用于在服务器控制台和 `logs/latest.log` 中确认 socket IP、forwarded 头和最终解析出的客户端 IP。完整检查步骤见 `docs/zh-CN/OPERATIONS_SECURITY.md`。

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

启用存储历史记录时，可以通过聊天面板右上角的浮动区域的放大镜按钮和 `/history/search` API 搜索消息内容和发送者。搜索选项可按日期/时间范围、发送者、来源以及是否包含系统/事件消息进行筛选。搜索结果会显示在可滚动列表中，并遵循聊天主题和字体设置。点击搜索结果会使用现有的周边历史加载跳转到对应消息。带有 i18n 键的系统/事件消息会尽可能按请求的 Web UI 语言搜索和显示。 可通过 `search.enabled` 启用/禁用搜索，且仅用 `search.result-limit` 同时控制 Web UI 结果数量和 `/history/search` API 限制。没有单独的内部最大值：设置为 2000 时最多返回 2000 条，设置为 10 时最多返回 10 条。10000 或 100000 这类非常大的值也会被接受，但可能导致搜索变慢、响应体变大，并显著增加 CPU、内存和数据库负载。默认值为 50，普通使用建议 50-200。当 `config-version: "5.3.0_auto_migration"` 时，缺少的搜索设置会在 startup/reload 时自动插入。只有使用精确的 `config-version: "5.3.0"` 停止同版本自动 migration 后，才需要手动添加缺少的键或重新启用 `_auto_migration`。

## 群组聊天

`group-chat.enabled` 启用 Web 群组聊天功能。支持公开/私密房间、哈希保存的可选密码、邀请、退出房间、隐藏/恢复房间、房间设置、未读追踪、room-local owner/admin/member 角色、置顶消息、全房间消息删除、普通成员自删策略、踢出/封禁/解除封禁成员以及转移房主。群组消息保存在 `group-chat.sqlite-file`（默认 `group-messages.db`）中。`group-chat.retention-days: 0` 表示不按时间清理；正数会物理删除更旧的群组消息。

房间加入/退出通知不是全局 `config.yml` 开关，而是**每个房间单独保存的数据库设置**。可在房间设置中开启或关闭，并保存到 `group_rooms.membership_events_enabled`；旧数据库新增该列时默认启用。只有实际成员关系变化才会保存事件，关闭群聊窗口不等于退出房间。


## 私信/群组聊天元数据超级管理员

`private-chat-super-admins: []` 用于填写可查看私信/群聊元数据的准确 UUID 或 Minecraft 名。元数据视图显示参与者/标题、消息数、大致存储大小、保留状态和管理操作。私信正文仅在 `direct-message.admin-audit.enabled: true` 时可审计，群聊正文仅在 `group-chat.admin-audit.enabled: true` 时可审计；两者都要求账号列在 `private-chat-super-admins` 中。两种审计视图均为只读，每次分页读取都会写入审计日志。


`frontend.standalone.app-name` 和 `frontend.standalone.app-short-name` 控制 standalone 页面/PWA 名称。移动端添加到主屏幕后如更改这些值，需要重新添加。`web-push.notification-title` 控制测试/系统/后台推送的默认标题；留空时使用 `frontend.standalone.app-name`。

如果旧 config 中仍保留 `BlueMapWebChat` 或 `BM WebChat` 这类历史生成显示名，KWC 会把它视为 legacy default，并使用当前 fallback 名称。

### Dynmap 适配器

`adapters.dynmap` 不使用 Dynmap 自带的网页聊天传输，而是把 KWC 前端嵌入 Dynmap 页面。启用后，KWC 会从常见 `configuration.txt` 读取 Dynmap 的 `webpath`，安装 `kokoto-web-chat/` 资源，并且只维护 `index.html` 中带标记的 KWC 区块。Dynmap 在 `update-webpath-files: true` 时可能重新生成网页文件，因此如果 Dynmap 操作覆盖了页面，可执行 `/kchat reload` 恢复。若 Dynmap 网页目录被复制到另一台 Web 服务器，请把 `web-root` 指向服务器文件系统可见的实际共享/挂载目录。`api-base-url: ""` 与其他地图适配器一样自动处理 IP 直连 HTTP 与 HTTPS。


### LiveAtlas 适配器

`adapters.liveatlas` 将 KWC 嵌入现有 LiveAtlas 静态前端。无论 LiveAtlas 显示 Dynmap、squaremap、Pl3xMap、Overviewer 还是多个服务器，都使用同一个后端无关适配器，并可用于 Bukkit/Fabric/NeoForge/Forge。`web-root: ""` 时，KWC 会检查常见本地地图 Web 目录，但只接受 `index.html` 中包含 `window.liveAtlasConfig` 等 LiveAtlas 标记的目录。若 LiveAtlas 由 Caddy/nginx 从独立目录提供，请把服务器可访问的共享/挂载目录填入 `web-root`。KWC 只管理 `addon-path` 目录和 LiveAtlas `index.html` 中带标记的 KWC 区块。LiveAtlas 更新替换 `index.html` 后执行 `/kchat reload` 即可恢复。同一个实际 Web 根目录不要同时启用 LiveAtlas 适配器和 Dynmap/squaremap/Pl3xMap KWC 适配器。

### uNmINeD 适配器

`adapters.unmined` 将 KWC 嵌入已有的 uNmINeD 静态 Web 导出。uNmINeD 是外部地图生成器而不是 Minecraft 服务器插件，因此 Bukkit/Fabric/NeoForge/Forge 都可使用同一个文件系统适配器，不依赖服务器中的 uNmINeD 运行时。支持当前的 `index.html`，并兼容旧版 `unmined.index.html`；自动检测会保守地要求 `unmined.map.properties.js` 与 uNmINeD 运行时代码等标记。若导出到任意目录或由 Caddy/nginx 使用独立 document root，请把服务器可见的共享/挂载导出目录填入 `web-root`。重新导出地图可能会替换 HTML 或 KWC 专用资源，因此之后应执行 `/kchat reload`。

### Overviewer 适配器

`adapters.overviewer` 将 KWC 嵌入已有的 Minecraft Overviewer 静态 Web 地图输出。Overviewer 是外部渲染器而不是服务器插件，因此 Bukkit/Fabric/NeoForge/Forge 都可使用同一个文件系统适配器，不依赖服务器中的 Overviewer 运行时。KWC 只接受可通过 `Minecraft-Overviewer` generator 元数据、`overviewerConfig.js`、`overviewer.js`、`overviewer.css` 等 Overviewer 专用标记/资源确认的现有 `index.html`，不会修改普通 Leaflet 页面。若输出目录任意或由 Caddy/nginx 使用独立 document root，请把服务器可见的共享/挂载 Overviewer `outputdir` 填入 `web-root`。Overviewer 渲染或 `--update-web-assets` 可能重新生成 `index.html`，因此之后应执行 `/kchat reload`。需要长期维护自定义模板的管理员仍可使用 Overviewer 的 `customwebassets`；KWC 不会修改 Overviewer 的 Python 配置。

> **IP / 路由器端口转发：** 地图适配器的 direct HTTP 自动判断假定外部可访问的 KWC 端口与 `http.port`（默认 8899）相同。如果路由器把公网 `8900` 转发到服务器 `8899`，浏览器无法推断 NAT 端口转换，因此请把对应地图适配器的 `api-base-url` 明确设置为 `http://PUBLIC_IP:8900/api`。如果直接通过转发后的端口打开 standalone，则可继续使用 `api-base-url: ""`，因为 standalone 会使用当前 origin。
