# KOKOTO WebChat 4.7.0 升级

4.7.0 将 Bukkit/Spigot 兼容基线下调到 Minecraft 1.18，并加入管理员自定义表情多文件上传和可配置的消息令牌替换。

## 兼容性

- 保守支持范围：**Minecraft 1.18 至 26.2**
- Java：**17**
- `plugin.yml`：`api-version: '1.18'`
- Maven 编译 API：`spigot-api:1.18.2-R0.1-SNAPSHOT`
- Paper `AsyncChatEvent` 继续通过 reflection 检测，Bukkit `AsyncPlayerChatEvent` 作为直接链接的 fallback。
- 1.17 及更早版本不列入本次正式支持范围。

## 自定义表情多文件上传

表情上传现在使用与普通聊天文件上传相同的文件选择流程。点击 Upload 后打开隐藏的 multiple file input；选择文件后立即把 `FileList` 复制为普通数组，清空 native input，并直接开始顺序上传。没有第二个确认上传按钮，也不再使用文件选择器 focus/visibility 绕过逻辑。仍保留上传进度和实际传输中的取消。现有服务器 endpoint 继续负责逐文件验证、总存储计算、重名处理、审计日志和 PNG sidecar 生成。

## 消息令牌

默认 alias 只提供英文，管理员可以改成或追加任意语言。内置动作包括换行、空行和缩进，也可配置只包含可打印字符的 custom 替换。未知的 `:token:` 保持原样，因此可以继续与现有自定义/图片表情令牌共存。

## 配置

4.7.0 新增 `message-tokens` 配置段。除此之外不修改现有默认值，并更新 review marker。migration 比较并不限于 4.6.3，更旧或没有版本标记的配置也会按当前 4.7.0 设置检查缺失项。另外会始终生成 `config-reference-4.7.0.yml`，它是当前 JAR 完整 4.7.0 默认配置及全部注释的原样副本。`message-tokens.custom: {}` 这类空 map 在缺失时也会保留在 migration 输出中。migration 文件末尾还会以注释形式附上与完整 reference 的文本 diff。相同的行不会输出；每个差异按文件名、单独一行的 `Line` 或 `Lines`、实际不同内容的顺序显示。差异源行只在行首直接加 `#`，因此会原样保留 YAML 自身的缩进，并为仅 reference 中存在的块标出在当前配置中的插入位置：

```yaml
config-version: "4.7.0"
```

startup/reload 时还会把已知顶层 `config.yml` 配置块按 4.7.0 bundled 默认顺序重新排列，同时保留各块的当前文本、设置值和用户自定义注释；默认中不存在的顶层块会按原顺序保留在最后。

### 游戏内换行行为

只有由已配置的 `newline` / `blank-line` 标记生成的换行会以受保护状态通过 Minecraft 现有的单行清理，并在最终发送时作为独立的游戏聊天行输出。普通 CR/LF 输入仍按原有方式展平。通过服务器中继在游戏中显示这种有意换行时，接收端 KOKOTO WebChat 也必须使用相同的 4.7.0 标记行发送支持；旧版接收端会在原有展平阶段把普通 LF 转为空格。
