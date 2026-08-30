# KOKOTO WebChat 5.1.0 发布检查清单

## 源码 / 配置 / 多语言
- [ ] Root/Bukkit/Fabric/NeoForge/Forge 的 metadata 与产物名称全部为 `5.1.0`。
- [ ] `config.yml`、`config-baselines/config-5.1.0.yml`、`distribution/config-reference-5.1.0.yml` 完全逐字节一致。
- [ ] 5.0.0 → 5.1.0 migration 会写入 `5.1.0_auto_migration`，保留仍受支持的运维配置值、删除 retired 设置，不推断 Relay v1 的 trust/topology，而是重建为禁用的 Relay v2 以便显式重新配置；精确的 `5.1.0` 会停止同版本设置重构。
- [ ] en-US/ko-KR/ja-JP/zh-CN 的键集合与 placeholder 完全一致。
- [ ] `inner.js` 与全部 8 个 frontend wrapper 均通过语法检查和 embedded JS/CSS 一致性检查。

## 功能 smoke test
- [ ] 游戏↔Web 公共聊天、Reply、URL、自定义表情、置顶、搜索、message token、content filter 均正常。
- [ ] 现有不合法的 emoji pack/item 名会迁移为 canonical 名；新建 pack/item upload 使用同一规则；同一 pack 内的冲突使用数字 suffix 解决。
- [ ] Emoji picker 不自动添加空格，只插入准确 token；配置的 newline alias 在纯表情连续行中保持紧凑，而 blank-line alias 仍生成真正空行。
- [ ] Web Reply 保留完整原文，并正确、可读地呈现 URL 与自定义表情。
- [ ] 游戏内 DM/group 名称点击会准备现有命令，消息正文点击会准备 Reply，URL 片段仍执行打开 URL。
- [ ] 被篡改的 `dm-...`/`group-...` Reply target，若发送者不是实际 DM 参与者或当前 group member，必须被拒绝。

## Relay / 安全
- [ ] 可选的 signed `/relay/v2/handshake` identity/health probe 在双方以相同 group ID 与 group shared secret 互相登记时成功；probe 不创建 route 状态，direct relay 会逐请求独立认证。
- [ ] 单边 peer 配置在两个方向上都不可用。
- [ ] `server-relay.forwarding.enabled` 默认值为 `false`。
- [ ] 启用 forwarding 后，也只有 inbound/outbound 两个 forwarding hop 都为 HTTPS 时才允许转发；HTTP forwarding 必须被阻止。
- [ ] 直接 HTTP peer 保持单跳兼容，同时明确输出多语言 WARNING/警告。
- [ ] KWC 内置 HTTP listener 绑定到非 loopback 地址时会输出 HTTP 暴露警告。
- [ ] 公共 relay 与跨服务器 1:1 DM/read receipt 正常，group chat 保持本地功能，不进行服务器间 relay。

## 分发 / 构建
- [ ] Modrinth updater 在地址迁移期间优先查询 `kokoto-webchat`、回退到 `bluemapwebchat`，并只在两个来源都失败时警告。
- [ ] CurseForge URL 指向 `bukkit-plugins/kokoto-webchat`。
- [ ] Windows path preflight 已应用于完整 validator 以及 Fabric/NeoForge/Forge 的全部 build-all/build-target 入口。
- [ ] Bukkit 使用 JDK 17；Fabric 16 / NeoForge 12 / Forge 16 个 exact target 按目标使用选定的 JDK 17/21/25 构建。
- [ ] `validate-release-windows.bat` 生成 `FINAL RELEASE BUILD PASS`、45 个可部署 JAR 与 SHA256SUMS。
- [ ] 最终 acceptance 不使用 `--fast`；可采用 sequential 或 `--parallel` 的 clean 调度，但不得把缓存/部分构建视为 `FINAL RELEASE BUILD PASS`。
