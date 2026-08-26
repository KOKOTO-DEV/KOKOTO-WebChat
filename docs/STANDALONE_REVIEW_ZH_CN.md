# BlueMap 依赖 / standalone 模式检查

## 概要

Java 插件本体不依赖 BlueMap API。`plugin.yml` 没有声明 BlueMap `depend` / `softdepend`，Java 源码中也没有 BlueMap API import。运行时依赖是 Bukkit/Spigot 兼容服务器 API。DiscordSRV 集成是可选项。

因此，聊天功能本身可以在没有 BlueMap 的情况下运行。BlueMap 特有部分由 `kwc-adapter-bluemap` 负责，用于把内嵌 addon 资源复制到 BlueMap web 目录并修改 `webapp.conf`。standalone 资源位于独立的 `kwc-standalone-frontend` 模块中，不再从 BlueMap adapter 读取。

## 支持模式

KOKOTO WebChat 当前支持两种模式：

```text
BlueMap addon panel
standalone page
```

standalone 模式默认关闭，需要时请显式启用。

```yaml
  api-base-url: ""
```

`frontend.standalone.api-base-url` 通常留空。直接 HTTP 使用内部 `http.path-prefix`，通过反向代理时使用 `http.public-prefix + http.path-prefix`，因此默认公开 API 为 `/chat/api`。只有 standalone 需要独立公开 API URL 时才设置此项。

直接 HTTP URL：

```text
http://<server-host>:8899/
```

HTTPS 反向代理 URL 示例：

```text
https://<domain>/chat
```

## 仅 standalone 部署

如果不想在 BlueMap 地图中注入聊天 UI，请使用以下设置：

```yaml
adapters:
  bluemap:
    auto-install: false
    auto-patch-webapp-conf: false

frontend:
  standalone:
    enabled: true
    path: "/"
```



## 透明窗口限制

standalone 浏览器窗口和 Document Picture-in-Picture 窗口无法仅通过普通 Web API 变成操作系统级的真正透明窗口。CSS 可以让聊天面板本身半透明，但浏览器/PIP 窗口背景和桌面透视由浏览器或操作系统控制。

### URL 设置解析规则

`frontend.standalone.api-base-url` 是 standalone 页面自己的 API override，通常留空。它不会继承 `adapters.bluemap.api-base-url`。upload/emoji 留空时会基于统一的公开 API base 分别追加 `/uploads`、`/emojis`。`/chat/api` 这类绝对浏览器路径会原样使用。不带前导 `/` 的相对值会在 `http.cors-origin` 为实际 origin 时基于该 origin 解析。完整 `https://...` URL 原样使用。


