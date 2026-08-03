# 从 4.5.5 升级到 4.6.0

## 先备份

停止服务器并备份 `plugins/BlueMapWebChat`，尤其是 `config.yml`、SQLite 数据库及 `-wal`/`-shm`、DM/群组数据库、上传、表情、自定义语言文件、审计日志以及 Web Push 密钥和订阅文件。

## 自动生成的配置迁移片段

BlueMapWebChat 不会自动覆盖或合并现有 `config.yml`。服务器启动和执行 `/bmchat reload` 时会读取磁盘上的实际文件并检查 `config-version`。

- 当 `config-version` 与正在运行的插件版本一致时，配置被视为已审核，跳过比较，并删除该版本的旧迁移片段。
- 当版本标记缺失或不同时，插件会比较实际配置与 JAR 内置当前默认配置，并生成或更新：

判定表：

| 已安装配置状态 | 迁移文件 |
|---|---|
| 缺少版本标记 | 即使没有其他差异也会生成 |
| 版本标记与当前插件不同 | 生成或更新 |
| 版本标记与当前插件一致 | 不生成，并删除遗留的同版本提示文件 |

```text
plugins/BlueMapWebChat/config-migration-4.6.0.yml
```

生成文件不是结构化报告，而是可直接参考和复制的 YAML 配置片段。它显示：

- 实际 `config.yml` 中缺失的设置及当前推荐默认值；
- 内置默认值已变化，并且实际设置值仍等于旧默认值的设置；
- 最终审核标记对应的目标 `config-version`。

版本信息、数量以及旧、新默认值说明全部只写成 `#` 注释。不会创建 `migration:`, `summary:`, `settings-to-add:`, `preserved-custom-values:`, `obsolete-settings-to-review:`, `finalize-after-review:` 等信息型 YAML 区块，也不会输出自定义值和废弃候选参考列表。

只把需要的设置合并到实际 `config.yml` 的对应位置。真实配置文件不会被自动修改。

即使没有任何缺失设置或变化的内置默认值，也会生成迁移文件并包含目标 `config-version`。这样，没有版本标记的配置也必须经过明确审核后才能标记为完成。

4.6.0 内置 4.5.5 默认配置作为比较基线。没有版本标记的配置按 4.5.5 或更早版本处理。对于明确但未知的版本，只报告缺失键，不推测默认值变化。

审核完成后在实际配置中设置：

```yaml
config-version: "4.6.0"
```

只要该标记与插件版本一致，之后的启动和 reload 都会跳过比较。

## 4.6.0 主要新增设置

- 顶级 `server-relay:`
- `direct-message.capture-game-whispers`
- `reply.game-click.enabled`
- `reply.game-click.local-game-chat`
- `reply.game-command-format`
- Discord `{server}`、`{server_id}` 占位符

版本不一致期间，实际文件中缺少的私聊捕获和本地聊天替换功能会使用安全的禁用状态。

## 数据库迁移

公共 SQLite 历史会通过增量 `ALTER TABLE` 添加中继元数据列。现有记录会保留，但旧记录无法追溯补充来源服务器。首次启动 4.6.0 前请备份数据库。

## 建议检查

1. 使用旧配置启动，确认生成配置片段且 `config.yml` 未改变。
2. 审核并合并缺失设置与变化的默认值。
3. 添加 `config-version: "4.6.0"`，执行 `/bmchat reload`，确认跳过比较。
4. 测试中继、游戏回复、私聊复制、Discord 服务器标签、URL 和 ImageEmojis。
