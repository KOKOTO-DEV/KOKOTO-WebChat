# KOKOTO WebChat 5.1.0 — 用户指南

本指南介绍日常使用。服务器部署与维护请参阅 `INSTALLATION_OPERATIONS_ZH_CN.md`；实现细节请参阅 `TECHNICAL_REFERENCE_ZH_CN.md`；完整长篇参考请参阅 `COMPLETE_REFERENCE_ZH_CN.md`。

## 公共聊天
在普通聊天视图中进行游戏 ↔ Web 对话。同一时间线支持 Reply、置顶、搜索、上传、自定义表情、富媒体预览以及跳到最新消息。发送者名称点击操作与消息正文 Reply 操作彼此独立，因此 URL 部分仍会正常作为链接打开。

## 账号与访客
已关联 Minecraft 的账号会保留服务器身份，并可同步受支持的聊天/通知偏好。只有管理员启用访客功能时才能使用访客聊天，并仍受名称、CAPTCHA、会话和 moderation 规则限制。

## 私信与群组

![私聊 Reply 验证与跨服务器身份](assets/private-reply-flow.svg)

1:1 私信可从收件人界面或 `/kchat dm` 打开。群组房间可使用群组界面或 `/kchat group`。在 Minecraft 中，点击私信/群组会话标签会预填相应命令；点击私聊消息正文会准备 Reply。发送时服务器始终会重新验证参与者或成员资格。

## Reply、表情与媒体
Reply 会保留原消息的完整文本。已注册的自定义表情可在 Reply 预览中显示，同时避免生成嵌套链接。表情选择器只会在光标位置插入准确 token，不会自动添加空格。配置的换行别名会让连续的纯表情行保持紧凑，而显式空行仍然保持为空行。

## 通知与外观
桌面通知、Web Push、关键词偏好、账号 UI 配置资料、主题/字体/文字阴影以及 PIP，可通过服务器公开的 Web 设置进行管理。窗口位置等设备本地状态以及 Push endpoint 仍只保存在对应设备上。

## 出现异常时
服务器更新后先刷新 Web 页面，再检查当前 URL 与登录状态。管理员在手工修改数据文件之前，应先参考安装/运维指南和 Wiki 的 `Troubleshooting` 页面。

## 参考资料

实现/安全细节与外部标准请参阅 [TECHNICAL_REFERENCE_ZH_CN.md](TECHNICAL_REFERENCE_ZH_CN.md) 和 [REFERENCES_ZH_CN.md](REFERENCES_ZH_CN.md)。
