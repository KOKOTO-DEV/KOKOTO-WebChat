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
