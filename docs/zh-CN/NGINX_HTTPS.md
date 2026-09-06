# KOKOTO WebChat nginx HTTPS 配置指南


![KWC 反向代理部署结构](../assets/deployment-modes.svg)

[PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg)

本指南说明如何让 BlueMap 和 KOKOTO WebChat 继续作为本地 HTTP 服务运行，并通过 nginx 以 HTTPS 对外提供服务。

## 推荐结构

```text
用户浏览器
  ↓ HTTPS
nginx :443
  ├─ /           -> BlueMap Web 服务器，通常为 127.0.0.1:8100
  └─ /chat/*      -> KOKOTO WebChat API 和独立页面，通常为 127.0.0.1:8899
      /chat/api  -> 内部 /api
      /chat -> 内部 /
```

浏览器应使用同一个公开 origin，例如 `https://map.example.com/`、`https://map.example.com/chat/api/config`、`https://map.example.com/chat`。

## 从 BMWC 迁移到 KWC 时的 HTTPS 路径变化

BlueMapWebChat 的标准 HTTPS 配置通常将公开 `/bmwc/api` 代理到内部 `:8899/api`，并将公开 `/bmwc/chat` 代理到内部 standalone `/chat`。KOKOTO WebChat 5.0.0 不再沿用该布局。迁移时，BMWC 的标准公开路径值会规范化为 KWC 新的自动值。

```text
BMWC
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/bmwc/chat
  API:         https://map.example.com/bmwc/api

KWC 5.0.0
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/chat
  API:         https://map.example.com/chat/api
```

对于标准 BMWC 值，迁移会使用 `adapters.bluemap.api-base-url: ""`、`frontend.standalone.api-base-url: ""` 等自动值，standalone 内部路径使用 `frontend.standalone.path: "/"`，公开前缀使用 `http.public-prefix: "/chat"`。标准 `/bmwc/api/uploads`、`/bmwc/api/emojis` 也会规范化为空的自动值。用户自行设置的自定义外部 URL 不会被任意改写。

**转换插件配置不会自动修改现有的 Caddy/nginx 配置。** 请删除或替换 BMWC 的 `/bmwc/api`、`/bmwc/chat` 代理规则，并改用本文中的 `/chat` 前缀剥离方式。nginx 应将 `/chat/` location 代理到 `:8899/`，从而在内部请求中去掉公开的 `/chat` 前缀。

## 1. 安装 nginx 和 Certbot

nginx 不会自行签发证书。公开 HTTPS 配置通常需要安装 nginx 和 Certbot，然后为域名申请 Let's Encrypt 证书。

### Debian / Ubuntu 示例

```bash
sudo apt update
sudo apt install -y nginx snapd
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -sf /snap/bin/certbot /usr/bin/certbot
```

申请证书前允许 HTTP/HTTPS。

```bash
sudo ufw allow 'Nginx Full'
```

使用 nginx 插件申请并安装证书。

```bash
sudo certbot --nginx -d map.example.com
```

测试自动续期:

```bash
sudo certbot renew --dry-run
```

某些发行版也可以使用 `sudo apt install certbot python3-certbot-nginx`，但 Certbot 官方说明通常优先推荐 snap 方式。

## 2. nginx 配置示例

复制 `examples/nginx/kokoto-webchat.conf`，并替换域名和证书路径。

```nginx
server {
    listen 80;
    server_name map.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name map.example.com;

    ssl_certificate     /etc/letsencrypt/live/map.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/map.example.com/privkey.pem;

    location ~ ^/kchat(?:/|$) {
        rewrite ^/kchat(?:/(.*))?$ /$1 break;
        proxy_pass http://127.0.0.1:8899;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 1h;
        proxy_send_timeout 1h;
    }

    location / {
        proxy_pass http://127.0.0.1:8100;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
    }
}
```

`rewrite` 会去掉公开的 `/kchat` 前缀，使 `/chat/api/config` 转发为 `/api/config`，`/chat` 转发为 `/`。

`proxy_buffering off` 对 SSE(Server-Sent Events) 很重要。没有它时，聊天更新或重连可能会被 nginx 缓冲而延迟。

如果不让 Certbot 自动修改 nginx，而是手动管理 server block:

```bash
sudo cp examples/nginx/kokoto-webchat.conf /etc/nginx/sites-available/kchat.conf
sudo ln -sf /etc/nginx/sites-available/kchat.conf /etc/nginx/sites-enabled/kchat.conf
sudo nginx -t
sudo systemctl reload nginx
```

### 反向布局：KWC 位于 `/`，BlueMap 位于 `/chat/`

```nginx
location = /chat {
    return 308 /chat/;
}

location /chat/ {
    proxy_pass http://127.0.0.1:8100/;
}

location / {
    proxy_pass http://127.0.0.1:8899;
    proxy_buffering off;
}
```

使用 `http.public-prefix: ""` 并保持 `frontend.standalone.path: "/"`。KWC 位于 `/`，API 位于 `/api`，BlueMap 位于 `/chat/`。


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

为了保持滚动稳定，建议保留媒体预览 max-height 限制。推荐值为 `640-720`。`0` 表示无限制，在媒体较多的 virtual scroll 场景中可能导致滚动跳动。

## 4. 防火墙建议

```text
允许从互联网访问: 80/tcp, 443/tcp
阻止从互联网访问: 8100/tcp, 8899/tcp
```

如果 nginx 和 Minecraft 在同一台主机上，建议将 KOKOTO WebChat API 只绑定到 `127.0.0.1`。

## 5. 应用步骤

1. 将域名 A/AAAA 记录指向服务器 IP。
2. 在防火墙中允许 `80/tcp` 和 `443/tcp`。
3. 安装 nginx 和 Certbot。
4. 使用 `sudo certbot --nginx -d map.example.com` 申请证书，或手动放置证书。
5. 应用 nginx 配置并确认 `sudo nginx -t` 成功。
6. 除非有意使用独立的公开 API URL，否则 adapter/standalone 的 `api-base-url` 保持为空。
7. standalone 通过 `https://map.example.com/chat` 打开；API override 为空时会根据 `http.public-prefix + http.path-prefix` 使用 `/chat/api`。
8. 上传/表情公开 URL 通常留空。如需旧式显式配置，`upload.public-base-url` 可使用 `/chat/api` 或 `/chat/api/uploads`，`emoji.public-base-url` 可使用 `/chat/api` 或 `/chat/api/emojis`。
9. 执行 `/kchat reload` 或重启服务器以重新生成 Web addon 文件。
10. `/kchat reload` 会在更新 BlueMap adapter 后自动请求 `bluemap reload light`。自动执行失败时请手动运行 `/bluemap reload light`。
11. 在浏览器中打开 `https://map.example.com/` 或 `https://map.example.com/chat`。

## 6. HTTP 页面 + HTTPS API 注意事项

只让聊天 API 使用 HTTPS，而 BlueMap 页面仍通过 HTTP 提供，并不是完整的安全边界。公开服务器应将 BlueMap 和 KOKOTO WebChat 都放在同一个 HTTPS origin 下。

### URL 设置解析规则

HTTPS 公开 API 的基准是 `http.public-prefix + http.path-prefix`，默认值为 `/chat/api`。adapter 与 standalone 的 `api-base-url` 是彼此独立的可选 override，通常保持为空。upload/emoji 留空时会基于统一公开 API 分别追加 `/uploads`、`/emojis`。绝对路径、相对值和完整 `https://...` URL 仅在需要独立公开 URL 时使用。

## 官方参考文档

- [NGINX `ngx_http_proxy_module`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [BlueMap reverse-proxy guide](https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html)

KWC 特有的 path-prefix、trusted-proxy、SSE、上传和认证行为应以 KWC 5.2.0 源码与配置为准，而不是由这些外部文档定义。
