# KOKOTO WebChat 5.0.0 发布检查清单

- [ ] 5.0.0 metadata、artifact、config reference 一致。
- [ ] 4.7.0→5.0.0 migration、`5.0.0_auto_migration` 与停止同版本 backfill 的行为正确。
- [ ] en-US/ko-KR/ja-JP/zh-CN key set 完全相同且没有空翻译。
- [ ] smoke test 覆盖 login/guest/public chat/reply/pin/search/filter/DM/group/upload/clipboard/profile/Push/admin Discord alert/relay/map adapters。
- [ ] original filename 下长文件名可用，Windows DOS 8.3 clipboard 别名不会生成损坏链接。
- [ ] Bearer auth、一次性 SSE ticket、管理员 IP 限制、body limit、Web Push SSRF、Discord mention/CDN、防注入 profile import 均正常。
- [ ] `update-check.enabled` 在 Bukkit/Fabric/NeoForge/Forge 上均为实际功能，并确认 KWC 优先/BMWC fallback 查询与 `kwc.update.notify` 登录提醒。
- [ ] Bukkit JDK17、Fabric 16 exact-target、NeoForge 12 exact-target、Forge 16 exact-target 均使用对应的 JDK 17/21/25 build 成功。
- [ ] `validate-release-windows.bat` 输出 `FINAL RELEASE BUILD PASS`、45 个 deployable JAR 和 SHA256SUMS。
- [ ] README/Upgrade/Configuration/User Manual/Wiki/Modrinth/CurseForge 与最终 5.0.0 一致，不再包含“以后再重命名”的开发阶段文字。
- [ ] AI assistance disclosure 放在 README/description/`AI_USAGE.md`，不作为功能 changelog 项目。
- [ ] 先在旧 BMWC listing 发布 5.0.0，让 4.7.0 update checker 能发现 bridge release。
- [ ] 5.0.0 更新提醒中的 CurseForge 链接在新 KWC CurseForge listing 真正上线前继续使用现有 BMWC bridge 页面。
- [ ] 如果无法无缝重命名为同一项目，保留 BMWC 页面作为停止维护/迁移公告并引导至新 KWC listing。
- [ ] Modrinth 使用一个项目并为各版本标记正确 loader；CurseForge 先在现有 Bukkit Plugins 项目发布 Bukkit bridge，再确认 project class 对 Fabric/NeoForge/Forge 文件的兼容性。
- [ ] GitHub 优先将 `BlueMapWebChat` rename 为 `KOKOTO-WebChat`，rename 后更新 local remote。

- [ ] 使用 ImageEmojis-Bero 时确认共用 `plugins/KOKOTO-WebChat/emojis`、`serverIp:webServerPort` 客户端可达性、资源包 reload/update 及 game↔web token 渲染。
- [ ] 使用 SimpleNicks-Bero 时确认 `player-display.mode: "display-name"` 显示昵称，同时 KWC account/UUID identity 仍对应真实用户。
