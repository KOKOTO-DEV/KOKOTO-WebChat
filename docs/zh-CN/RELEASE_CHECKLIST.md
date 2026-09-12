# KOKOTO WebChat 5.3.1 发布检查清单

## 源码 / 配置 / 多语言
- [ ] Root/Bukkit/Fabric/NeoForge/Forge 的 metadata 与产物名称全部为 `5.3.1`。
- [ ] 当前 `config.yml`、`config-baselines/config-5.3.0.yml`、多语言 config template 与 `distribution/config-reference-5.3.0.yml` 的解析设置路径和默认值一致，仅允许展示注释与 `_auto_migration` 标记不同；历史 baseline 仅作为 migration 输入保留。
- [ ] 5.2.1 → 5.3.0 migration 会写入 `5.3.0_auto_migration`，保留受支持的运维值与 Relay v2 信任设置，将废弃的 hide 确认项迁移为 delete 确认项；精确的 `5.3.0` 会停止同版本重构。
- [ ] en-US/ko-KR/ja-JP/zh-CN 的键集合与 placeholder 完全一致。
- [ ] 5.3.0 → 5.3.1 保持 config schema 5.3.0 与 Relay 2.2/现有配置值，并验证 Event 自动结束持久化、topology-aware Event scope、共享命令补全和移动端 add-on Administrator 交互。
- [ ] `inner.js` 与全部 8 个 frontend wrapper 均通过语法检查和 embedded JS/CSS 一致性检查。
- [ ] `node tools/build-inner-bundle.js --check` 通过，确认 `frontend/inner/manifest.txt`、生成的 `inner.js` 与 8 个 wrapper 的 embedded payload 完全一致。

## 功能 smoke test
- [ ] 游戏↔Web 公共聊天、Reply、URL、自定义表情、置顶、搜索、message token、content filter 均正常。
- [ ] 现有不合法的 emoji pack/item 名会迁移为 canonical 名；新建 pack/item upload 使用同一规则；同一 pack 内的冲突使用数字 suffix 解决。
- [ ] Emoji picker 不自动添加空格，只插入准确 token；配置的 newline alias 在纯表情连续行中保持紧凑，而 blank-line alias 仍生成真正空行。
- [ ] Web Reply 保留完整原文，并正确、可读地呈现 URL 与自定义表情。
- [ ] 游戏内 DM/group 名称点击会准备现有命令，消息正文点击会准备 Reply，URL 片段仍执行打开 URL。
- [ ] 被篡改的 `dm-...`/`group-...` Reply target，若发送者不是实际 DM 参与者或当前 group member，必须被拒绝。
- [ ] Chat Event 支持先到先得/抽奖/投票/招募；按类型的容量/响应数/时间自动结束条件在重启后仍保留；先到先得在获奖名额满时始终自动完成；参与者/结果名称遵循显示名 / 真实名模式。

- [ ] 公共消息 reaction 可持久化，Relay 2.2 传播正常，且 reaction-only SSE 更新不会重启正在播放的媒体；32 × 16px empty-state `+` 与正文/下一条消息各保留 1px 视觉间距，reaction OFF 保持原来的 8px spacing，实际 reaction 使用正常 in-flow row；分类/搜索重绘后 picker 位置和外部点击关闭正常；**Admin > Emojis > Reaction icons** 使用与其他 Admin settings 相同的圆角主题行，并提供 `表情 = 搜索词` 别名编辑器，保存到 `reaction-search-aliases.txt`。 搜索别名仅用于 picker 搜索，不转换聊天输入。
- [ ] 公开聊天/DM/群聊“正在输入…”使用 5 秒 event-driven window，排除本人/audit viewer，多个过长名称会折叠为人数，并且不产生 polling 或持久 typing 状态。Web Admin Settings/config.yml 分别执行服务器级默认值：公开聊天 OFF / DM ON / 群聊 ON。`chat.typing-indicator.user-display-control` 默认关闭；管理员启用后，按账号保存的个人开关只隐藏接收到的 typing 提示，不停止发送自己的 typing 状态。
- [ ] 对话存档由服务器重新验证范围，执行 archive quota 与私聊房间 lock 策略，在普通 retention 后仍保留 snapshot，但管理员删除源消息时会 cascade 清理。
- [ ] 对话存档 PDF/打印视图同时显示 display name + real name，使用当前 KWC appearance，仅嵌入仍存在原件的图片，video/audio/其他文件保留 link，原件丢失时标记 unavailable。
- [ ] `chat.conversation-archive.enabled: false` 时不注册 archive API，不打开或新建 `conversation-archives.db`，并且 Web UI 不生成保存对话相关 DOM。
- [ ] 将 `max-archives-per-user`、`max-messages-per-archive`、`max-messages-per-user` 设置为非默认值时，服务器实际执行这些限制，且 `/archive/list` 返回生效值。
- [ ] 打开的 DM 中，Back/标题 hover 背景应无缝覆盖整个标题栏直到内缩的 Settings 按钮区域，同时 Settings 按钮自身的 hover 状态仍独立生效。
- [ ] 单个表情反应通知复选框同时控制实时浏览器反应通知与后台/移动 Web Push 反应通知，不受支持的投递路径保持不工作。
- [ ] 公共/DM/群聊 latest-follow 仅在距离底部 **小于 2 行实际渲染文本**时生效；更上方阅读时不会因新消息/compose panel 布局变化被强制拉到底部，刷新/重连后会在可能的情况下恢复已保存的浏览位置。

## Relay / 安全
- [ ] 可选的 signed `/relay/v2/handshake` identity/health probe 在双方以相同 group ID 与 group shared secret 互相登记时成功；probe 不创建 route 状态，direct relay 会逐请求独立认证。
- [ ] 单边 peer 配置在两个方向上都不可用。
- [ ] `server-relay.forwarding.enabled` 默认值为 `false`。
- [ ] 启用 forwarding 后，也只有 inbound/outbound 两个 forwarding hop 都为 HTTPS 时才允许转发；HTTP forwarding 必须被阻止。
- [ ] 直接 HTTP peer 保持单跳兼容，同时明确输出多语言 WARNING/警告。
- [ ] KWC 内置 HTTP listener 绑定到非 loopback 地址时会输出 HTTP 暴露警告。
- [ ] 公共 relay 与跨服务器 1:1 DM/read receipt 正常，group chat 保持本地功能，不进行服务器间 relay。

## 分发 / 构建
- [ ] Modrinth updater 只查询 canonical `kokoto-webchat` 项目，不再查询旧 BMWC 项目地址。
- [ ] CurseForge URL 指向 `bukkit-plugins/kokoto-webchat`。
- [ ] Windows path preflight 已应用于完整 validator 以及 Fabric/NeoForge/Forge 的全部 build-all/build-target 入口。
- [ ] Bukkit 使用 JDK 17；Fabric 16 / NeoForge 12 / Forge 16 个 exact target 按目标使用选定的 JDK 17/21/25 构建。
- [ ] release validator 会打开每个 NeoForge JAR，检查 target 选择的 `META-INF/mods.toml` 或 `META-INF/neoforge.mods.toml`、`modLoader`、`loaderVersion`、KWC ID/version、精确 Minecraft dependency，并确认没有未替换的 template placeholder。
- [ ] release validator 会运行 security + Relay/reaction/typing 回归 harness，并针对完成的 shaded Bukkit JAR 执行对话存档 SQLite runtime harness。

> `validate-release-windows.bat` 及其所需的 PowerShell helper 已包含在 source archive 中。开发回归测试工具也位于 source archive 的 `validation/`，无需单独的 validation-tools archive。

- [ ] `validate-release-windows.bat` 生成 `FINAL RELEASE BUILD PASS`、45 个可部署 JAR 与 SHA256SUMS。
- [ ] 最终 acceptance 不使用 `--fast`；可采用 sequential 或 `--parallel` 的 clean 调度，但不得把缓存/部分构建视为 `FINAL RELEASE BUILD PASS`。

### 5.3.1 Presentation parity matrix

- PC 的 7 种 map add-on + PiP、Standalone + PiP 使用同一份 server config/account preference/权限/Event 状态。
- Mobile 的 7 种 map add-on 与 Standalone 同样共享设置/状态，差异只允许存在于 minimize/PiP/multi-window 等 presentation capability。
- 确认 `node validation/531-presentation-parity-harness/kwc-531-presentation-parity-harness.js` PASS。
- `node tools/build-inner-bundle.js --check` 必须同时验证 8 个 wrapper embedded inner 与 8 份 CSS 的 byte parity。
