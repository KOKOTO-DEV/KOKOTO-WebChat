# BlueMapWebChat 4.6.3 升级

4.6.3 新增可选的只读管理员群聊正文审计功能，不改变现有私信审计行为。

## 新配置

```yaml
group-chat:
  admin-audit:
    enabled: false

config-version: "4.6.3"
```

对于已经标记为 `4.6.2` 的配置，如果没有其他实际缺失项，migration fragment 只会包含这个新开关和 4.6.3 检查标记。现有 `config.yml` 不会被覆盖。

## 访问条件

以下两个条件都必须满足：

```yaml
private-chat-super-admins:
  - "ExactMinecraftNameOrUUID"

group-chat:
  admin-audit:
    enabled: true
```

普通 ADMIN/MODERATOR 角色本身不能查看正文。审计视图为只读，不要求或创建房间成员身份，不会更新已读/未读状态，也不能发送、上传、隐藏消息或修改成员关系。每次分页读取都会记录为 `admin.group-audit-read`，但不会把消息正文复制到审计日志。

## 配置注释

4.6.3 更新了内置 `config.yml` 注释，使其准确说明当前的更新检查、跨服私信投递/已读 ACK、群聊已读状态以及私信/群聊管理员审计行为。服务器启动或执行 `/bmchat reload` 时，只有当现有配置中的某段注释 **仍与旧版 BlueMapWebChat 内置注释完全一致** 时，才会刷新为新的内置注释。该注释刷新不会修改任何配置值，用户自定义注释会保留，`config-version` 仍只应在管理员审核 migration fragment 后手动修改。
## 私聊媒体播放

私信和群聊消息列表现在也采用与普通聊天相同的 stable key DOM 更新方式。同一会话刷新时，现有消息和视频/音频 DOM 会保持连接，只更新新增/删除的消息以及投递/已读元数据。因此收发新消息时，正在播放的媒体不会从头重新播放。只有原本已在底部时才会继续跟随最新消息；浏览中间位置时会保持当前视口。离开当前会话或切换到其他会话时，会彻底删除该会话的消息/媒体 DOM，并清除其 private media-open 状态。再次进入时会从 `▶ Video` / `▶ Audio` 的未展开 click-to-load 状态重新创建；即使关闭 click-to-load，也不会复用旧播放器，而是创建新的未播放媒体元素。仅重新进入会话不会调用 `play()`。

