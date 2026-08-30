# KOKOTO WebChat Caddy HTTPS 配置指南


![KWC 反向代理部署结构](assets/deployment-modes.svg)

本指南说明如何让 BlueMap 和 KOKOTO WebChat 继续作为本地 HTTP 服务运行，并通过 Caddy 以 HTTPS 对外提供服务。

## 推荐结构

```text
用户浏览器
  ↓ HTTPS
Caddy :443
  ├─ /           -> BlueMap Web 服务器，通常为 127.0.0.1:8100
  └─ /chat/*      -> KOKOTO WebChat API 和独立页面，通常为 127.0.0.1:8899
      /chat/api  -> 内部 /api
      /chat -> 内部 /
```

浏览器应使用同一个公开 origin。

```text
https://map.example.com/
https://map.example.com/chat/api/config
https://map.example.com/chat
```

## 从 BMWC 迁移到 KWC 时的 HTTPS 路径变化

BlueMapWebChat 的标准 HTTPS 配置通常将公开 `/bmwc/api` 代理到内部 `:8899/api`，并将公开 `/bmwc/chat` 代理到内部 standalone `/chat`。KOKOTO WebChat 5.0.0 及之后版本不再沿用该布局。迁移时，BMWC 的标准公开路径值会规范化为 KWC 新的自动值。

```text
BMWC
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/bmwc/chat
  API:         https://map.example.com/bmwc/api

KWC 5.0.0 及之后版本
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/chat
  API:         https://map.example.com/chat/api
```

对于标准 BMWC 值，迁移会使用 `adapters.bluemap.api-base-url: ""`、`frontend.standalone.api-base-url: ""` 等自动值，standalone 内部路径使用 `frontend.standalone.path: "/"`，公开前缀使用 `http.public-prefix: "/chat"`。标准 `/bmwc/api/uploads`、`/bmwc/api/emojis` 也会规范化为空的自动值。用户自行设置的自定义外部 URL 不会被任意改写。

**转换插件配置不会自动修改现有的 Caddy/nginx 配置。** 请删除或替换 BMWC 的 `/bmwc/api`、`/bmwc/chat` 代理规则，并改用本文中的 `/chat` 前缀剥离方式。Caddy 会先去掉 `/chat` 前缀，再将 `/chat` 和 `/chat/*` 转发到 `:8899`。

## 1. 安装 Caddy

当域名已指向服务器，并且 `80/tcp`、`443/tcp` 可访问时，Caddy 通常会自动申请并续期 Let's Encrypt 证书。

### Debian / Ubuntu 示例

```bash
sudo apt update
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy
```

### Fedora / RHEL 系示例

```bash
sudo dnf install -y 'dnf-command(copr)'
sudo dnf copr enable @caddy/caddy
sudo dnf install -y caddy
```

### Arch Linux 示例

```bash
sudo pacman -S caddy
```

## 2. Caddyfile 示例

复制 `examples/caddy/Caddyfile`，并替换域名。

```caddyfile
map.example.com {
  encode zstd gzip

  @chat path /chat /chat/*
  handle @chat {
    uri strip_prefix /chat
    reverse_proxy 127.0.0.1:8899
  }

  handle {
    reverse_proxy 127.0.0.1:8100
  }
}
```

`uri strip_prefix /chat` 会去掉公开的 `/chat` 前缀，因此 `/chat/api/config` 会转发为插件内部的 `/api/config`，`/chat` 会转发为 `/`。

应用示例:

```bash
sudo cp examples/caddy/Caddyfile /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

### 反向布局：KWC 位于 `/`，BlueMap 位于 `/chat/`

如果 standalone KWC 使用站点根路径 `/`，而 BlueMap 放在 `/chat/`：

```caddyfile
map.example.com {
  encode zstd gzip

  redir /chat /chat/ 308

  handle_path /chat/* {
    reverse_proxy 127.0.0.1:8100
  }

  handle {
    reverse_proxy 127.0.0.1:8899
  }
}
```

设置 `http.public-prefix: ""`，保持 `frontend.standalone.path: "/"`，adapter/frontend 的 `api-base-url` 通常保持为空。最终 URL 为 KWC `/`、KWC API `/api`、BlueMap `/chat/`。


### 在同一域名下使用 BlueMap + squaremap + standalone

如果三个 frontend 都启用，应让一个地图占用 `/`，并给另一个地图分配独立前缀。例如 squaremap 位于 `127.0.0.1:8080`、BlueMap 位于 `127.0.0.1:8100`、KWC 位于 `127.0.0.1:8899` 时：

```caddyfile
map.example.com {
  encode zstd gzip

  @chat path /chat /chat/*
  handle @chat {
    uri strip_prefix /chat
    reverse_proxy 127.0.0.1:8899
  }

  @bluemap path /bluemap /bluemap/*
  handle @bluemap {
    uri strip_prefix /bluemap
    reverse_proxy 127.0.0.1:8100
  }

  handle {
    reverse_proxy 127.0.0.1:8080
  }
}
```

该配置会把 squaremap 发布在 `/`、BlueMap 发布在 `/bluemap/`、standalone KWC 发布在 `/chat`，KWC API 发布在 `/chat/api`。如果希望 BlueMap 使用根路径，请交换根地图与带前缀地图的 handler。

## 3. KOKOTO WebChat config.yml

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  public-prefix: "/chat"
  cors-origin: "https://map.example.com"

frontend:
  standalone:
    enabled: true
    path: "/"
    api-base-url: ""

adapters:
  bluemap:
    enabled: true
    api-base-url: ""

upload:
  # 推荐留空。需要时也可使用 "/chat/api" 或 "/chat/api/uploads"。
  public-base-url: ""

emoji:
  # 推荐留空。需要时也可使用 "/chat/api" 或 "/chat/api/emojis"。
  public-base-url: ""

ui:
  image-preview-max-height: 720
```

请将 `map.example.com` 替换为实际域名。

为了保持滚动稳定，建议保留媒体预览 max-height 限制。推荐值为 `640-720`。`0` 只会取消显式像素上限，浏览器仍会应用基于 viewport 的安全上限，因此并不代表完全无限的显示高度。

## 4. BlueMap

BlueMap 可以继续使用现有 Web 端口，常见为 `8100`。公开部署时建议只向互联网开放 Caddy 的 `80/tcp` 与 `443/tcp`，不要直接暴露 BlueMap 与 KOKOTO WebChat 的内部端口。

## 5. 防火墙建议

```text
允许从互联网访问: 80/tcp, 443/tcp
阻止从互联网访问: 8100/tcp, 8899/tcp
```

如果 Caddy 和 Minecraft 在同一台主机上，建议将 KOKOTO WebChat API 只绑定到 `127.0.0.1`。

## 6. 应用步骤

1. 将域名 A/AAAA 记录指向服务器 IP。
2. 在防火墙中允许 `80/tcp` 和 `443/tcp`。
3. 安装 Caddy。
4. 放置 Caddyfile 并 reload Caddy。
5. 除非有意使用独立的公开 API URL，否则 adapter/standalone 的 `api-base-url` 保持为空。
6. standalone 通过 `https://map.example.com/chat` 打开；API override 为空时会根据 `http.public-prefix + http.path-prefix` 使用 `/chat/api`。
7. 上传/表情公开 URL 通常留空。如需旧式显式配置，`upload.public-base-url` 可使用 `/chat/api` 或 `/chat/api/uploads`，`emoji.public-base-url` 可使用 `/chat/api` 或 `/chat/api/emojis`。
8. 执行 `/kchat reload` 或重启服务器以重新生成 Web addon 文件。
9. `/kchat reload` 会在更新 BlueMap adapter 后自动请求 `bluemap reload light`。自动执行失败时请手动运行 `/bluemap reload light`。
10. 在浏览器中打开 `https://map.example.com/` 或 `https://map.example.com/chat`。

## 7. HTTP 页面 + HTTPS API 注意事项

只让聊天 API 使用 HTTPS，而 BlueMap 页面仍通过 HTTP 提供，并不是完整的安全边界。公开服务器应将 BlueMap 和 KOKOTO WebChat 都放在同一个 HTTPS origin 下。

## nginx 替代方案

如果使用 nginx，请参考 `docs/NGINX_HTTPS_ZH_CN.md` 和 `examples/nginx/kokoto-webchat.conf`。

### URL 设置解析规则

HTTPS 公开 API 的基准是 `http.public-prefix + http.path-prefix`，默认值为 `/chat/api`。adapter 与 standalone 的 `api-base-url` 是彼此独立的可选 override，通常保持为空。upload/emoji 留空时会基于统一公开 API 分别追加 `/uploads`、`/emojis`。绝对路径、相对值和完整 `https://...` URL 仅在需要独立公开 URL 时使用。

## 官方参考文档

- [Caddy `reverse_proxy`](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)
- [Caddy reverse-proxy quick start](https://caddyserver.com/docs/quick-starts/reverse-proxy)
- [BlueMap reverse-proxy guide](https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html)

KWC 特有的 path-prefix、trusted-proxy、SSE、上传和认证行为应以 KWC 5.1.0 源码与配置为准，而不是由这些外部文档定义。
