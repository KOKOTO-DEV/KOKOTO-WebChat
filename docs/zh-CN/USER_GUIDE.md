# KOKOTO WebChat 5.3.0 — 用户指南

反应 authority、直连/多跳传递、origin 断开时的 outbox 与作者通知图请参阅 [REACTIONS.md](REACTIONS.md)。

本指南介绍日常使用。服务器部署与维护请参阅 `INSTALLATION_OPERATIONS.md`；实现细节请参阅 `TECHNICAL_REFERENCE.md`；完整长篇参考请参阅 `USER_MANUAL.md`。

## 公共聊天
![公共消息生命周期](../assets/public-message-flow.svg)

在普通聊天视图中进行游戏 ↔ Web 对话。同一时间线支持 Reply、置顶、搜索、上传、自定义表情、富媒体预览以及跳到最新消息。发送者名称点击操作与消息正文 Reply 操作彼此独立，因此 URL 部分仍会正常作为链接打开。

## 账号与访客
已关联 Minecraft 的账号会保留服务器身份，并可同步受支持的聊天/通知偏好。只有管理员启用访客功能时才能使用访客聊天，并仍受名称、CAPTCHA、会话和 moderation 规则限制。

## 私信与群组

![私聊 Reply 验证与跨服务器身份](../assets/private-reply-flow.svg)

![DM/群聊消息、已读、typing 与 relay 流程](../assets/private-conversation-flow.svg)

[PNG](../assets/private-reply-flow.png) · [SVG](../assets/private-reply-flow.svg)

1:1 私信可从收件人界面或 `/kchat dm` 打开。群组房间可使用群组界面或 `/kchat group`。在 Minecraft 中，点击私信/群组会话标签会预填相应命令；点击私聊消息正文会准备 Reply。发送时服务器始终会重新验证参与者或成员资格。


登录用户可以在公共聊天、DM 与普通群聊消息上添加 Unicode 或 KWC 自定义表情 reaction；群聊加入/离开事件行不能添加 reaction。没有实际 reaction 时，32 × 16px 的 `+` 按钮与当前正文和下一条消息各保留 1px 的视觉间距，不会覆盖文字。reaction OFF 时不生成 empty affordance，继续使用原来的 8px 消息间距；出现实际 reaction 后才使用正常的 in-flow reaction row/chip 间距。picker 可按表情字符、服务器生成的 Unicode 名称、管理员维护的搜索别名、自定义表情 ID/名称/表情包搜索。搜索别名在 **Admin > Emojis > Reaction icons** 中按 `表情 = 搜索词` 编辑，并保存到 `reaction-search-aliases.txt`。搜索别名仅用于 reaction picker 搜索，不会转换公共聊天、DM 或群聊输入。分类/搜索重新渲染后仍保持位置和外部点击关闭。反应悬停列表中的用户名称使用与普通聊天发送者相同的显示名称 ↔ 原始名称切换。总开关与 KWC 自定义表情开关使用与其他 Admin settings 相同的圆角主题行。关闭功能后保留已有 reaction 数据并只读显示，同时拒绝所有本地/Relay mutation。公共/DM/群聊 typing indicator 仅显示 5 秒。服务器管理员可在 Web Admin **Settings** 或 `chat.typing-indicator.*` 中分别控制公开聊天、DM 与群聊，默认 OFF / ON / ON。`chat.typing-indicator.user-display-control` 默认 OFF；管理员启用后，登录用户的聊天设置中会出现一个按账号保存的 **输入中提示** 选项。用户关闭它只会隐藏自己屏幕上已启用范围内的其他用户 typing 提示，不会停止发送自己的 typing 状态。圆角提示浮在输入框上方，使用与普通聊天相同的显示名称 ↔ 原始名称切换，去除名称 formatting tag；字体约为用户聊天字体的 80%，但不小于基础 UI 字体。

![已保存对话生命周期](../assets/archive-lifecycle.svg)

在 DM/群聊房间中，通过 **Settings** 使用有权限时的 Invite、**Save conversation**、已保存对话、隐藏和房间管理。只有服务器保持 `chat.conversation-archive.enabled: true`（默认）时才会生成保存相关 UI；关闭后相关 DOM 与 archive API 都不可用。真正的房间管理设置只对有管理权限的用户显示，Leave 仍保留在标题栏。保存对话时选择第一条和最后一条消息生成个人 snapshot，但管理员删除/锁定策略始终优先。PDF 使用当前 KWC 外观，只包含仍存在原件的图片；其他文件保留链接，原件不存在时标记为不可用。

公共聊天、DM、群聊的最新消息自动跟随统一按当前实际渲染的 line-height 计算。只有距离底部小于 **2 行文本**时才跟随最新消息；如果用户正在更上方阅读，新消息或布局变化本身不会把视图强制拉到底部。打开/关闭 emoji/icon/attachment 面板会保留当前 viewport，刷新/重新连接后也会在可能的情况下恢复已保存的原浏览位置。

## Reply、表情与媒体
Reply 会保留原消息的完整文本。已注册的自定义表情可在 Reply 预览中显示，同时避免生成嵌套链接。表情选择器只会在光标位置插入准确 token，不会自动添加空格。配置的换行别名会让连续的纯表情行保持紧凑，而显式空行仍然保持为空行。

## 通知与外观
桌面通知、Web Push、关键词偏好、账号 UI 配置资料、主题/字体/文字阴影以及 PIP，可通过服务器公开的 Web 设置进行管理。窗口位置等设备本地状态以及 Push endpoint 仍保存在各自设备上，但私聊的“正在查看”状态按账号整体判断。只要同一账号登录的任意 KWC 页面实际可见并处于焦点、聊天窗口未最小化、DM/群聊窗口已打开，而且当前会话/房间 ID 与新消息目标完全一致，就视为正在查看。用于查看的设备本身无需启用 Web Push；在此期间，同一账号所有已连接 KWC 标签页都会抑制该 DM/群聊的浏览器通知和浏览器本地通知收件箱写入，同时所有设备上的 Web Push 也会被抑制。查看其他房间、关闭窗口、页面隐藏/失焦或聊天最小化时则视为未查看，并恢复正常通知。

## 出现异常时
服务器更新后先刷新 Web 页面，再检查当前 URL 与登录状态。管理员在手工修改数据文件之前，应先参考安装/运维指南和 Wiki 的 `Troubleshooting` 页面。

## 参考资料

实现/安全细节与外部标准请参阅 [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md) 和 [REFERENCES.md](REFERENCES.md)。
