# KOKOTO WebChat 5.0.0 升级

5.0.0 汇总正式版 4.7.0 之后的开发内容，并完成 **BlueMapWebChat (BMWC) → KOKOTO WebChat (KWC)** 的名称迁移。

## 下载页面迁移

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

## 4.7.0 → 5.0.0 主要变化

- 正式标识统一为 KOKOTO WebChat：`/kchat` (`/kc`)、`kwc.*`、KWC 数据目录、`dev.kokoto.webchat`、`kwc-*` 模块。
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

## 平台支持

- Bukkit/Paper/Spigot：1.18–26.2，Java 17 bytecode。
- Fabric exact-target：`1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`；按 target 使用 JDK 17/21/25。
- NeoForge exact-target：`1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`；按 target 使用 JDK 17/21/25。
- Forge exact-target：`1.18.2`, `1.19.2`, `1.19.4`, `1.20.1`, `1.20.2`, `1.20.4`, `1.20.6`, `1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, `26.2`。

## BMWC 数据/配置迁移

Bukkit 上已有的 `plugins/BlueMapWebChat` 可在首次 KWC 启动时作为迁移输入，原目录保持不变。`web-addon.* → adapters.bluemap.*`，`standalone-web.* → frontend.standalone.*`。旧 `/bmchat` 等不再注册为命令别名，旧 `bluemapwebchat.*` 权限只作为兼容 fallback。`X-BMWC-Relay-*` wire header 为旧 peer 兼容而保留。

旧 `/bmwc/api`、`/bmwc/chat` reverse proxy 需要手动改成新的 `/chat` 布局。

## 配置迁移

4.7.0→5.0.0 reference 结构比较结果为 **新增 79 个路径、删除 14 个路径、2 个现有值发生变化**。实际版本迁移会保留用户值、插入缺失的 setting/comment，并写入 `config-version: "5.0.0_auto_migration"`。只有希望停止同版本自动 backfill 时才改为精确 `5.0.0`。

## 最终发布判定

只有 `validate-release-windows.bat` 输出 `FINAL RELEASE BUILD PASS`、收集到准确 45 个可发布 JAR，并通过 static/config/i18n/document validation 与主要功能 smoke test 的候选版本才作为正式发布版。
