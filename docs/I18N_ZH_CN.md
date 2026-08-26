# KOKOTO WebChat 国际化指南

## 内置语言

- `en-US.yml` - 英语，默认
- `ko-KR.yml` - 韩语
- `ja-JP.yml` - 日语
- `zh-CN.yml` - 简体中文

首次运行时会复制到 `plugins/KOKOTO-WebChat/lang/`。更新时会自动补齐缺失的内置键。已经修改过的值会保留，因此如果旧文案仍然显示，请编辑已复制的 lang 文件，或删除后让插件重新生成。

## UI 语言

```yaml
ui:
  language: "zh-CN"
  language-fallback: "en-US"
```

`language` 是服务器默认值。用户也可以在聊天设置中选择浏览器本地语言，该选择会保存在 localStorage。

支持值:

```text
en-US, ko-KR, ja-JP, zh-CN
```

## fallback

如果所选语言缺少某个键，会使用 `ui.language-fallback`。如果 fallback 也缺失，则使用内置英文默认值。

## 翻译范围

包括窗口标题/状态、按钮、placeholder、登录/绑定/密码、账号/用户设置、包含固定/删除显示开关的管理面板、上传、媒体预览/social embed 标签、PIP、命令面板、置顶消息、服务器 command 响应等。

发往游戏内的聊天格式在 `config.yml` 中设置。


## 系统消息

内置服务器 announcement 和 Web 命令结果消息会带 i18n 键发送。语言文件中存在对应键时，Web UI 会按查看者选择的语言显示。`config.yml` 中的 `announcements.*.message` 仍作为自定义/回退文本保留，因此缺少翻译键或需要服务器专用措辞时仍会使用它。

折叠的置顶消息也会使用与普通消息相同的聊天字体和消息字号设置。

## 添加新语言

1. 复制 `plugins/KOKOTO-WebChat/lang/en-US.yml`。
2. 例如重命名为 `fr-FR.yml`。
3. 翻译 `web:` 下的字符串。
4. 将 `ui.language` 设置为不包含 `.yml` 的新文件名。
5. 运行 `/kchat reload` 并刷新页面。
