# 升级到 KOKOTO WebChat 5.1.0

![ui.language 配置重建流程](assets/config-language-migration.gif)

> **重要：** `ui.language` 只改变配置文件、参考文件和迁移报告的显示语言；已经解析出的实际配置值会原样保留。

KOKOTO WebChat 5.1.0 面向 **5.0.0** 系列升级。发布说明按 **5.0.0** 正式版到 5.1.0 的最终变更内容编写。

## 升级前

1. 备份完整的 KWC 数据目录，包括 `config.yml`、公开/私聊数据库或 JSONL、上传文件、表情资源以及审计数据。
2. 如果多台服务器通过 Relay 互联，请同时规划所有相关服务器的升级与 Relay v2 重新配置。Relay Protocol v1 与 5.1.0 Relay v2 不兼容。
3. 如果 KWC 位于 Caddy/Nginx 后方，请记录当前公开 URL 与代理结构，以便升级后核对客户端 IP 解析是否正确。

## 配置迁移与语言

5.1.0 会保留现有的**已解析配置值**，仅使用当前模板重新构建注释与排版。实际生效的 `ui.language` 决定重建后的 `config.yml`、生成的 `config-reference-5.1.0.yml` 以及迁移/Difference 说明文字所使用的语言。内置显示语言为 `en-US`、`ko-KR`、`ja-JP`、`zh-CN`。

Difference 采用语义比较：只比较解析后的 YAML 配置路径和值，不比较注释、空行、缩进、引号形式、行号或键顺序。因此，仅切换显示语言不应产生配置差异。

首次启动后请同时检查 `config.yml` 与 `config-reference-5.1.0.yml`。包括已经在 5.1.0 中创建的 Relay 组在内，管理员自定义值在同版本自动迁移重新渲染显示形式时仍会保留。

## Relay Protocol v2 需要手工重建信任关系

首次迁移 5.1.0 之前的配置时，KWC 不会根据旧 Relay v1 的扁平信任关系猜测新的组配置。旧的全局共享密钥、扁平 peer 与转发设置会被废弃，并将 `server-relay.enabled` 重置为 `false`。

请显式创建 `server-relay.groups`。新 group 可先只在一台服务器上保留 `shared-secret: ""`，启动/重载 KWC 生成并保存安全随机值，再把该值原样复制到同一 group 的其他服务器。已有非空 secret 会保留；手工 secret 不足 32 字符时仍保持 invalid。同组双方必须互相登记为 peer。peer 项仅包含 `id`、`url`、`enabled`，不存在 peer 独立密钥。确认所有预期 peer 都已完成双向配置后，再重新启用 `server-relay.enabled`。

开始滚动升级前，请先在仍运行 5.0.0 的服务器上设置 `server-relay.enabled: false` 并 reload。若保持 5.0.0 Relay 开启而只把对端升级到 5.1.0，旧 v1 请求会持续重试，预期的 HTTP 426 响应可能不断累积为 WARN。请先把所有服务器升级并配置好 v2 group/peer，再重新启用 Relay。

Relay v1/BMWC endpoint 会返回 HTTP 426。直接单跳 HTTP peer 的 Relay v2 负载本身仍会经过加密与认证，但会触发安全警告；转发仅允许同组 **HTTPS→HTTPS**。Relay v2 属于**逐跳认证加密（hop-by-hop authenticated encryption）**，不是端到端加密。

## 私聊 Reply 存储升级

5.1.0 为 DM 与群组消息增加持久化回复元数据。现有私聊消息仍然有效。SQLite 群组存储会在架构升级时增加所需的可选回复列；旧 DM/JSONL 记录在没有回复元数据时继续使用省略对应字段的向后兼容格式，无需手工转换数据库。

5.1.0 还增加了按房间保存的群聊成员事件状态。现有 `group-messages.db` 会自动新增 `group_rooms.membership_events_enabled`（默认开启）和 `group_messages.event_type`（默认普通消息）列，无需手工转换数据库。关闭群聊 UI 不会生成退出事件。

跨服务器 DM Reply 使用稳定的 Relay 消息 ID，而不是另一台服务器的本地数据库行 ID。不要在服务器之间复制私聊消息的数字 ID，也不要强制让它们保持一致。

## 表情与 SSE 变化

SSE 默认上限调整为**每个解析后的客户端 IP 10 个连接**、**服务器全局 500 个连接**。对应设置为 `0` 时仍表示关闭该项限制。使用反向代理时请检查 `http.trusted-proxies`；如果配置错误，多名客户端可能都被识别为同一个代理 IP，从而共享同一组按 IP 计算的 SSE 限制。

表情目录的恢复能力也得到增强：临时无法获取 `/emojis` 时会保留上一次成功加载的目录，并使用有上限的指数退避进行重试；SSE 重连后会强制重新同步；服务器端表情目录发生变化时，会通过 `emoji-catalog` 事件通知其他浏览器刷新。`emoji.message-token-limit: 0` 仍表示不限制。

5.1.0 还会规范化自定义表情的包名和项目名：移除不支持的字符与空白；同一包内名称冲突时自动添加数字后缀。如果外部集成依赖固定的磁盘文件名或表情 token，请在升级后检查。

- Android 管理员表情上传改用通用的系统文件/DocumentsUI 选择器，而不是仅限图片的 Photo Picker。桌面端图片过滤和服务器端图片验证保持不变。

## 其他需要确认的行为变化

- DM/群组 Reply 会保存与原消息的真实关系，并在发送时由服务器重新验证当前 DM 会话参与者或群组成员资格。
- 游戏内 `/kchat group` 可正确解析包含空格的房间名、带引号的名称以及连续空格。
- 每个房间的成员通知只针对实际加入/接受邀请和退出/踢出/封禁；关闭或隐藏房间不会被视为退出。
- 聊天设置配置档会准确保留显式值与未设置状态，加载后的字体大小也会应用到已打开的 DM/群组窗口。
- 在窄屏/移动端，DM/群组的时间、Reply 和已读状态会自然换行，不再占用过大的固定列。
- `/kchat reload` 会重新检查对外暴露的 HTTP listener 与 direct HTTP Relay peer，并把相应本地化警告同时写入服务器日志并发送给命令执行者。
- 当 `commands.broadcast-result-to-web-chat: true` 时，Web 命令执行提示也会发送给在线 Minecraft 玩家。
- 公共聊天、DM 和群组消息输入框改用单行 `<textarea>`，而不是普通 text `<input>`，同时保留 `autocomplete="off"`、Enter 发送、光标定位和表情插入行为。这样可以避开 Android 版 Chrome 在无关的普通输入框上也显示密码、地址和付款方式 Autofill accessory 的路径；登录/密码输入框保持不变。
- 项目地址迁移期间，更新检查器先查询 canonical Modrinth `kokoto-webchat`，并回退到 `bluemapwebchat`。BMWC 在迁移完成前仍作为实际更新来源，只有两个来源都失败时才发出警告。
- 对外暴露的明文 HTTP listener 与直接 HTTP Relay peer 会显示本地化安全警告。

## 升级后检查

1. 检查 `config-version`，并查看生成的 5.1.0 reference/migration 文件。
2. 切换 `ui.language`，确认只改变显示语言而不会改变管理员实际配置值。
3. 使用 Relay 时，确认 v2 双向 peer 配置无误后再重新启用，并确认其他服务器不再依赖旧 v1 endpoint。
4. 位于代理后方时检查解析后的客户端 IP 与 SSE 行为；仅在故障排查期间临时启用 `http.log-client-ip-resolution`。
5. 在实际部署环境中测试公共聊天、DM/群组 Reply、自定义表情、上传、Web Push/通知以及正在使用的地图/standalone frontend。
6. 在正常运行和保留策略清理都确认无误前，保留升级前备份。
