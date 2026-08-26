# KOKOTO WebChat 上传安全说明

文件上传很方便，但在公网服务器上可能被滥用。默认配置会禁用访客上传。

## 推荐默认值

```yaml
upload:
  enabled: true
  allow-guest-upload: false
  allow-user-upload: true
  allow-moderator-upload: true
  allow-admin-upload: true
  max-file-size-mb: 20
  max-files-per-message: 3
  cooldown-seconds: 5
  max-uploads-per-minute: 4
  retention-days: 5
```

## 公网服务器

除非有额外审核流程，否则请保持访客上传关闭。如果必须启用访客上传，请降低文件大小限制并缩短保留时间。

## 公开 URL

直接 HTTP 示例:

```yaml
upload:
  public-base-url: ""
```

同域 HTTPS 反向代理示例:

```yaml
http:
  path-prefix: "/api"
  public-prefix: "/chat"

upload:
  # 推荐留空。上传 URL 会自动跟随 /chat/api。
  public-base-url: ""

emoji:
  # 推荐留空。表情 URL 会自动跟随 /chat/api。
  public-base-url: ""
```

旧式显式写法也可用：

```yaml
upload:
  public-base-url: "/chat/api"        # 插件会追加 /uploads
  # 或: "/chat/api/uploads"

emoji:
  public-base-url: "/chat/api"        # 插件会追加 /emojis
  # 或: "/chat/api/emojis"
```

以 `/` 开头的值会按同源浏览器路径处理。同域代理部署不需要使用完整 FQDN。

如果上传与 BlueMap 内嵌聊天使用同一个反向代理 API 路径，`upload.public-base-url` 可以留空，也可以设置为 `/chat/api` 这样的共享 API base。旧式完整资源路径 `/chat/api/uploads` 仍然可用。

自定义表情文件也使用同样规则：`emoji.public-base-url` 通常留空；旧式/自定义部署可使用 `/chat/api` 或 `/chat/api/emojis`。


## 允许扩展名

只允许你确实希望在聊天中显示或分享的文件类型。KOKOTO WebChat 会按扩展名和大小限制，但公网部署仍建议从受限目录提供上传文件并使用 HTTPS。`upload.max-total-size-mb` 还可以限制 `upload.directory` 直属普通文件的总存储容量；`0` 表示不限制。启用后，服务器会先删除最旧的未引用上传文件，仍无法腾出空间时会拒绝新的上传。

### URL 设置解析规则

`http.public-prefix + http.path-prefix` 是 HTTPS 公开 API 的基准，默认值为 `/chat/api`。adapter/standalone API override 相互独立，通常保持为空。upload/emoji 留空时会基于统一 API base 分别追加 `/uploads`、`/emojis`。


管理员自定义表情说明：重命名表情文件或文件夹会改变 `:emoji:pack/name:` 标记。引用旧标记的既有聊天消息可能不再渲染，除非保留旧文件/文件夹名称。

自定义表情上传也遵循 `emoji.max-total-size-mb` 总容量限制。超过限制时，管理员 UI 会显示警告。可通过 `emoji.show-storage-usage` 和 `emoji.show-storage-limit` 控制是否显示当前/最大表情容量。
