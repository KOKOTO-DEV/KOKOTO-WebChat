# KOKOTO WebChat 5.3.1 — 安装与运维

![KWC 部署模式](../assets/deployment-modes.svg)

[PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg)

> **说明：** 图示用于辅助理解。KWC 自身行为应以实际源代码与本文说明为准。

## 平台选择
使用与服务器平台及 Minecraft 版本完全匹配的构建产物。Bukkit/Paper/Spigot 使用单一 Java 17 插件；Fabric 与 Forge 各提供 16 个精确目标版本；NeoForge 提供 12 个精确目标版本。Loader 构建会根据 Minecraft 世代选择 Java 17/21/25。KWC 的核心聊天功能运行在服务器端，不要求客户端安装 Mod。

## 首次启动与 Web 发布
先启动一次以生成 KWC 数据和配置。可以使用 standalone 前端、受支持的地图适配器，或同时使用两者。对互联网公开时，应尽可能把内置 HTTP 服务绑定到 loopback，并由 Caddy/Nginx 负责 HTTPS 终止。若在非 loopback 地址上公开明文 HTTP，KWC 会明确发出警告。

## 配置生命周期
修改当前 `config.yml` 后使用 `/kchat reload`。reload 会在替换运行中的服务前验证 YAML；如果 YAML 无效，则继续使用之前的运行配置。`config-reference-5.3.0.yml` 是按内置 `ui.language` 渲染的当前管理员默认参考；自定义/不支持的 UI 语言使用英文展示。升级到 5.3.1 时继续使用 5.3.0 配置架构，并在当前模板中保留受支持的已解析管理员值。只有历史上的首次 5.0.0 → 5.1.0 relay 迁移会故意重置 Relay v1 信任设置；普通 5.1.0 → 5.2.0 升级会保留现有 Relay v2 group/secret/peer。`ui.language` 也用于 `config.yml` 注释模板、生成 reference 与 migration/difference 文本，Difference 按 YAML path/value 比较。

5.0.0 → 5.1.0 迁移会基于 5.1.0 模板重建配置，并保留受支持的管理员实际值，但 Relay v1 的信任设置会有意重置。5.1.0 中，`ui.language` 还决定 `config.yml` 注释模板、生成的 reference 以及 migration/Difference 文本所使用的语言。解析后的管理员值会原样覆盖保留，而 Difference 只比较 YAML 的 path/value，不比较注释或排版。

## Relay Protocol v2
需要显式配置 `server-relay.groups`。每个 group 只有一个 shared secret，peer 不再拥有独立 secret。新 group 可先在一台服务器上保留 `shared-secret: ""`，启动/重载 KWC 后把其 `config.yml` 中自动生成的值复制到同一 group 的其他服务器。已有非空值会保留，手工 secret 不足 32 字符时保持 invalid。同组双方仍必须互相登记为 peer。直接 HTTP 的 Relay payload 仍会加密/认证，但会产生安全警告；forwarding 仅允许同组 HTTPS→HTTPS。Relay v1 endpoint 返回 HTTP 426。详情请参阅 `SERVER_RELAY.md`。

## 更新与发布
从 5.2.0 开始，更新检查器只查询 canonical Modrinth `kokoto-webchat`；旧 BMWC 项目地址不再作为实际更新来源。发布前必须验证 45 个可部署目标构建、SHA-256 以及当前 release text。Windows 上应先运行 build-path preflight，再进行完整构建，以便尽早拒绝超出已验证范围的过长源码路径。

## 备份
请备份整个 KWC 数据目录，并保证 SQLite sidecar (`-wal` / `-shm`) 与主数据库处于一致状态。需要可靠的离线备份时，应先停止服务器。配置、账号/资料、Push subscription、上传/表情资源以及 Relay 配置应一并保存。

## 安全运维
使用 HTTPS、强管理员凭据、严格的管理员 IP 规则、最小权限原则以及明确的 super-admin 列表。Relay 组共享密钥是整个组的对称信任密钥；任一成员发生泄露后，都必须在该组全部成员上轮换密钥。

## 故障排查顺序
1. 检查启动/reload 警告。  
2. 验证当前公开 URL 与反向代理。  
3. 确认 Loader/版本构建产物及 Java 世代。  
4. Relay 问题应检查 group ID、双向 peer ID、共享密钥、系统时钟和 HTTPS 拓扑。  
5. 手工修复数据库或文件之前先保存日志与配置。
