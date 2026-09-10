# KOKOTO WebChat 安装与故障排查

## 要求

- Bukkit/Spigot/Paper 兼容服务器或兼容分支
- 构建/运行需要 Java 17 或更新版本
- BlueMap 插件与可用的 BlueMap webroot
- 浏览器必须能访问聊天 API 端口，默认 `8899/tcp`
- DiscordSRV 为可选，仅启用 Discord 桥接时需要

## 构建

```bash
mvn clean package
```

输出:

```text
kwc-platform-bukkit/target/KOKOTO-WebChat-5.3.0-Bukkit-1.18-26.2.jar
```

Windows 下也可以把根目录 validator 作为平台构建辅助脚本使用：

```bat

> `validate-release-windows.bat`、所需 PowerShell helper 以及 `validation/` 下的开发回归 harness 都包含在 source archive 中。运行 release build 不需要单独的 validation-tools 包。

validate-release-windows.bat --bukkit
validate-release-windows.bat --bukkit --fast
validate-release-windows.bat --parallel
```

`--fast` 会跳过 `clean` 并复用已有构建输出/缓存，仅用于迭代开发。`--parallel` 不改变 clean/fast 语义。如果选择了 Bukkit，会先构建 Bukkit，Bukkit 通过后再并行运行其余选中的 loader，因此 `validate-release-windows.bat --parallel` 仍可作为 clean 的完整发布验证。主控制台会实时显示总进度、各平台 target 数量和当前 Minecraft target；`--parallel` 模式下每个活动平台还会使用独立的实时构建窗口，详细日志保留在 `validation-logs/`。

## 安装或升级

1. 停止 Minecraft 服务器。
2. 用新 jar 替换 `plugins/` 中旧的 KOKOTO WebChat jar。
3. 启动服务器。
4. 检查 `plugins/KOKOTO-WebChat/config.yml`。
5. 如果修改了重要路径，执行 `/kchat reload` 或重启。
6. `/kchat reload` 会在 BlueMap webapp 变更后自动请求 `bluemap reload light`。自动执行失败时请手动运行 `/bluemap reload light`。
7. 在浏览器中强制刷新。

## 验证 Web 插件注册

```bash
grep -R "bluemap-web-chat" -n /opt/minecraft/server/plugins/BlueMap/webapp.conf
```

条目应包含当前版本 query。

```text
addons/kokoto-web-chat/config.js?v=5.3.0-<cache-token>
addons/kokoto-web-chat/chat.js?v=5.3.0-<cache-token>
addons/kokoto-web-chat/chat.css?v=5.3.0-<cache-token>
```

同时确认实际 Web 文件已更新。

```bash
find /opt/minecraft/server -path "*addons/kokoto-web-chat/chat.js" -printf "%p  %TY-%Tm-%Td %TH:%TM\n"
```

## BlueMap webroot 不匹配

如果 `/api/config` 正常但聊天面板没有出现，BlueMap 可能正在提供另一个 webroot。请让实际路径与以下设置一致。

```yaml
adapters:
  bluemap:
    bluemap-web-root: ""
    bluemap-webapp-conf: ""
    addon-path: "addons/kokoto-web-chat"
```

空路径表示自动探测。如果自动探测到的目录不是实际运行中的 BlueMap 实例，请明确填写绝对路径。

## 浏览器缓存

测试 Web UI 变更时，打开 DevTools，启用 **Network -> Disable cache**，然后强制刷新页面。也可以在控制台查看加载版本。

```js
[...document.scripts]
  .filter(s => s.src.includes("bluemap-web-chat"))
  .map(s => s.src)
```

## BlueMap 仍加载旧 addon 版本时

更新后如果 BlueMap 仍然加载旧的 KOKOTO WebChat addon 版本，请再执行一次 `/kchat reload` 或重启服务器，然后在浏览器中强制刷新。

## HTTPS 反向代理

公网服务器建议使用 Caddy 或 nginx 提供 HTTPS。Caddy 请参阅 `docs/zh-CN/CADDY_HTTPS.md` 与 `examples/caddy/Caddyfile`，nginx 请参阅 `docs/zh-CN/NGINX_HTTPS.md` 与 `examples/nginx/kchat.conf`。
