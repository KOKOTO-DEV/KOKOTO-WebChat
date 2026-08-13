# 从 4.6.0 升级到 4.6.1

## 主要变化

- 中继消息包含玩家 UUID 时，可在现有网页私信收件人搜索中找到其他服务器玩家。
- 可选择在现有私聊元数据列表中以只读审计方式查看私信正文。
- 新增基于 Modrinth 的简洁更新检查和管理员进服提示。
- 插件和配置版本更新为 `4.6.1`。

## 配置迁移

使用 `config-version: "4.6.0"` 的配置启动 4.6.1 时会生成：

```text
plugins/BlueMapWebChat/config-migration-4.6.1.yml
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

## 启用私信正文审计

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
- 修改配置后执行 `/bmchat reload` 或重启。替换 JAR 仍需要重启服务器。

## 跨服务器私信版本要求

所有交换跨服务器私信的服务器都必须使用 BlueMapWebChat 4.6.1 或更高版本。点击 `服务器 · 类型` 时会直接传递该消息的 UUID 和来源服务器 ID，远程搜索结果及已有远程会话也会同时保留服务器 ID 与玩家 UUID。
