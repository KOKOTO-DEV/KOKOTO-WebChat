# BlueMapWebChat 安装与故障排查

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
target/BlueMapWebChat-4.6.2.jar
```

## 安装或升级

1. 停止 Minecraft 服务器。
2. 用新 jar 替换 `plugins/` 中旧的 BlueMapWebChat jar。
3. 启动服务器。
4. 检查 `plugins/BlueMapWebChat/config.yml`。
5. 如果修改了重要路径，执行 `/bmchat reload` 或重启。
6. 如果 BlueMap 没有自动应用 Webapp 变更，执行 `/bluemap reload`。
7. 在浏览器中强制刷新。

## 验证 Web 插件注册

```bash
grep -R "bluemap-web-chat" -n /opt/minecraft/server/plugins/BlueMap/webapp.conf
```

条目应包含当前版本 query。

```text
addons/bluemap-web-chat/config.js?v=4.6.2-<cache-token>
addons/bluemap-web-chat/chat.js?v=4.6.2-<cache-token>
addons/bluemap-web-chat/chat.css?v=4.6.2-<cache-token>
```

同时确认实际 Web 文件已更新。

```bash
find /opt/minecraft/server -path "*addons/bluemap-web-chat/chat.js" -printf "%p  %TY-%Tm-%Td %TH:%TM\n"
```

## BlueMap webroot 不匹配

如果 `/api/config` 正常但聊天面板不显示，BlueMap 可能正在提供另一个 webroot。请确认 `web-addon.bluemap-web-root`, `web-addon.bluemap-webapp-conf`, `web-addon.addon-path` 与实际路径一致。

## 浏览器缓存

测试 Web UI 变更时，打开 DevTools，启用 **Network -> Disable cache**，然后强制刷新页面。也可以在控制台查看加载版本。

```js
[...document.scripts]
  .filter(s => s.src.includes("bluemap-web-chat"))
  .map(s => s.src)
```

## BlueMap 仍加载旧 addon 版本时

更新后如果 BlueMap 仍然加载旧的 BlueMapWebChat addon 版本，请再执行一次 `/bmchat reload` 或重启服务器，然后在浏览器中强制刷新。

## HTTPS 反向代理

公网服务器建议使用 Caddy 或 nginx 提供 HTTPS。Caddy 请参阅 `docs/CADDY_HTTPS_ZH_CN.md` 与 `examples/caddy/Caddyfile`，nginx 请参阅 `docs/NGINX_HTTPS_ZH_CN.md` 与 `examples/nginx/bluemapwebchat.conf`。
