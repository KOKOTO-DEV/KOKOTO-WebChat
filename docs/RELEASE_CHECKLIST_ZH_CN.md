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
