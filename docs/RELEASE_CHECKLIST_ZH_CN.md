# BlueMapWebChat 发布检查清单

发布前请确认：

- 更新 `pom.xml` 版本。
- 更新 `src/main/resources/plugin.yml` 版本。
- 更新 `src/main/resources/config.yml` 中的版本注释。
- 更新 README 和文档中的构建输出版本示例。
- 添加 CHANGELOG 条目。
- 运行 JavaScript 语法检查。

```bash
node --check inner.js
node --check src/main/resources/web/chat.js
```

- 验证 YAML 文件。

```bash
python3 - <<'PY'
import yaml, glob
for path in ['src/main/resources/config.yml'] + glob.glob('src/main/resources/lang/*.yml'):
    with open(path, encoding='utf-8') as f:
        yaml.safe_load(f)
    print('OK', path)
PY
```

- 以 `en-US.yml` 为基准确认语言键一致。
- 使用 Maven 构建。

```bash
mvn clean package
```

- 确认 `webapp.conf` 指向新的版本 query。
- 在 DevTools 禁用缓存后测试浏览器加载。

- [ ] 四种语言的 `USER_MANUAL_*.md` 使用相同的主要结构，并反映当前命令、权限、默认值和功能行为。

## 4.6.2 投递状态发布检查

- [ ] `pom.xml`、`plugin.yml`、内置 `config-version`、构建产物示例和当前手册均为 `4.6.2`。
- [ ] 已审核的 4.6.1 配置若无其他差异，`config-migration-4.6.2.yml` 只包含 `config-version: "4.6.2"`。
- [ ] 本地与远程存在同名玩家时，未指定服务器的私信只选择当前服务器玩家。
- [ ] 跨服务器私信从 `pending` 开始，仅在目标存储确认后变为 `delivered`；HTTP 502、超时、路由或目标端失败会变为 `failed`。
- [ ] 重试复用同一个 relay ID，不会在接收端重复保存消息。
- [ ] 服务器在 pending 状态下重启后，消息恢复为可重试的 `failed`。
- [ ] Web 私信和群聊在 HTTP 结果不确定时复用同一个 client message ID 安全重试。
- [ ] hub / chain 私信中继仅在最终目标确认存储后返回成功。
- [ ] 私信和群聊的每一条消息都显示已读状态；一对一私信在对方阅读前显示 `未读`、阅读后显示 `✓`，群聊以数字显示未读接收者人数并在人数为 0 时显示 `✓`；私信和群聊的每一条消息都必须参与已读状态计算。

## 4.6.0 中继 / DM / 游戏回复检查

- [ ] 不覆盖旧 config，并生成升级指南。
- [ ] 检查 `activePeers`、HTTPS/HMAC、unknown peer、时钟差和 reload。
- [ ] 检查当前服务器徽章已隐藏，远端服务器徽章及游戏/Discord 来源标签正常。
- [ ] 多个服务器共享同一 Discord 频道时，DiscordSRV 游戏消息只添加一次来源服务器名和一组表情链接。
- [ ] 检查同服游戏发送者建议 `/w`，Web/远端游戏发送者建议 `/bmchat dm`，正文建议 `/bmchat reply`，URL 仍优先打开。
- [ ] 检查私聊复制与 SQLite 迁移。
- [ ] 四种语言的 key set 一致。
- [ ] ImageEmojis-Bero 1.9.0：验证共用目录 PNG、普通聊天、`/bmchat reply`、`/bmchat dm`、URL+表情点击共存及远程中继显示。

- [ ] 当 `config-version` 缺失或不同时，即使没有其他差异，也生成包含 `config-version` 的 `config-migration-4.6.0.yml`，且不覆盖实际 `config.yml`。
- [ ] 版本一致时跳过比较并删除同版本的旧报告。
- [ ] 迁移片段只把缺失设置和变化的默认值写成真实 YAML 设置，不输出自定义值和信息型区块。

## 4.6.1 跨服私信搜索与管理员审计检查

- [ ] `pom.xml`、`plugin.yml`、内置 `config-version`、构建产物示例和缓存文档均为 `4.6.1`。
- [ ] 使用 `config-version: "4.6.0"` 且无其他缺失项时，`config-migration-4.6.1.yml` 只包含 `direct-message.admin-audit.enabled: false` 和 `config-version: "4.6.1"`。
- [ ] 带玩家 UUID 的其他服务器游戏/已关联网页发送者可在现有私信搜索中按显示名、真实名和 UUID 查找；无 UUID 的访客/Discord 发送者被排除。
- [ ] 重启后可从保留的公共聊天历史恢复远程玩家身份。
- [ ] 普通 ADMIN/MODERATOR 账号不能查看其他用户的私信正文。
- [ ] 即使列在 `private-chat-super-admins` 中，只要审计开关为 false 就只能查看元数据。
- [ ] 两个条件都启用时，管理员私信条目以只读审计视图打开，不提供发送/隐藏/已读操作，并排除全局隐藏消息。
- [ ] 每次分页读取都写入 `admin.dm-audit-read`，包含操作者、会话 ID、分页位置、限制和返回数量；正文不会复制到审计日志。

## 4.7.0 多文件上传与兼容性检查

- [ ] 无论旧配置版本如何，都会生成与内置 `config.yml` byte-for-byte 一致并保留全部注释的 `config-reference-4.7.0.yml`。
- [ ] startup/reload 会把已知顶层 `config.yml` 块按 4.7.0 默认顺序重新排列，但不会改变 YAML 值或块注释；默认中不存在的顶层块按原顺序保留在最后。
- [ ] bundled `config.yml` 的 message-token 默认 alias/示例只使用英文；多语言 alias 作为管理员可配置项记录在文档中。
- [ ] `en-US.yml`, `ko-KR.yml`, `ja-JP.yml`, `zh-CN.yml` 的 key 集合完全一致，且没有空/null 翻译。
- [ ] 生成的 4.7.0 migration 报告末尾以注释形式附带当前配置与 reference 的文本 diff，省略相同行，将文件名与 `Line`/`Lines` 和实际差异内容分行显示，并为仅 reference 中存在的块显示插入位置。
- [ ] `pom.xml` 和 `plugin.yml` 均为 4.7.0。
- [ ] `plugin.yml` 声明 `api-version: '1.18'`，POM 使用 Java 17 + Spigot API 1.18.2。
- [ ] 管理员表情 Upload 按钮使用与普通上传相同的隐藏多文件 picker；选择文件后无需第二次确认即开始顺序上传，并确保每个所选文件只提交一次。
- [ ] 中间某个文件失败时，后续文件仍继续上传。
- [ ] 开启 game-link sidecar 后，GIF/JPG/WEBP 仍会生成 PNG sidecar。
- [ ] 已审核的 4.6.3 config 会生成包含新 `message-tokens` 设置（包括 `custom: {}`）和 `config-version: "4.7.0"` 的 migration；更旧或没有版本标记的 config 也会与当前 4.7.0 全部键比较。

## 4.6.3 管理员群聊审计检查

- [ ] 仍使用旧版内置注释的配置只会把这些注释块刷新为 4.6.3 说明，不会修改 YAML 配置值或用户自定义注释。
- [ ] `config-version: "4.6.2"` 且无其他缺失项时，生成的 `config-migration-4.6.3.yml` 包含 `group-chat.admin-audit.enabled: false` 和 `config-version: "4.6.3"`。
- [ ] 即使列在 `private-chat-super-admins` 中，只要 `group-chat.admin-audit.enabled: false`，群聊仍只能查看元数据。
- [ ] 两个条件都启用后，列出的超级管理员即使不是房间成员，也可以只读打开群聊正文，且不会改变已读/未读状态。
- [ ] 每次审计分页读取都会记录包含 actor/room/pagination/count 的 `admin.group-audit-read`，正文不会复制到审计日志。

