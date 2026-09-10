# KOKOTO WebChat nginx HTTPS setup guide


![KWC reverse-proxy deployment](../assets/deployment-modes.svg)

[PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg)

This guide keeps BlueMap and KOKOTO WebChat running as local HTTP services, then exposes them through nginx over HTTPS.

## Recommended layout

```text
User browser
  ↓ HTTPS
nginx :443
  ├─ /           -> BlueMap web server, usually 127.0.0.1:8100
  └─ /chat/*      -> KOKOTO WebChat API and standalone page, usually 127.0.0.1:8899
      /chat/api  -> internal /api
      /chat      -> internal /
```

The browser should use one public origin: `https://map.example.com/`, `https://map.example.com/chat/api/config`, and `https://map.example.com/chat`.

## HTTPS path changes when migrating from BMWC to KWC

The standard BlueMapWebChat HTTPS layout usually proxied public `/bmwc/api` to internal `:8899/api` and public `/bmwc/chat` to the internal standalone `/chat`. KOKOTO WebChat 5.0.0 does not keep that layout. During migration, standard BMWC public-path values are normalized to KWC's new automatic values.

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

For standard BMWC values, migration uses automatic KWC values such as `adapters.bluemap.api-base-url: ""` and `frontend.standalone.api-base-url: ""`, with `frontend.standalone.path: "/"` and `http.public-prefix: "/chat"`. Standard `/bmwc/api/uploads` and `/bmwc/api/emojis` values are normalized to their empty automatic values as well. User-defined custom external URLs are not rewritten arbitrarily.

**Converting the plugin config does not rewrite an existing Caddy/nginx configuration.** Remove or replace the old BMWC `/bmwc/api` and `/bmwc/chat` proxy rules and use the `/chat` prefix-stripping layout in this guide. With nginx, configure the `/chat/` location to proxy to `:8899/` so the public `/chat` prefix is removed from the internal request.

## 1. Install nginx and Certbot

nginx does not issue certificates by itself. For a public HTTPS setup, install nginx and Certbot, then request a Let's Encrypt certificate for your domain.

### Debian / Ubuntu example

```bash
sudo apt update
sudo apt install -y nginx snapd
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -sf /snap/bin/certbot /usr/bin/certbot
```

Allow HTTP/HTTPS before issuing the certificate:

```bash
sudo ufw allow 'Nginx Full'
```

Issue and install a certificate with the nginx plugin:

```bash
sudo certbot --nginx -d map.example.com
```

For dry-run renewal testing:

```bash
sudo certbot renew --dry-run
```

If your distribution packages Certbot directly, `sudo apt install certbot python3-certbot-nginx` may also work, but the official Certbot instructions often prefer the snap package.

## 2. nginx server block example

Copy `examples/nginx/kokoto-webchat.conf` and change the domain and certificate paths.

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

The `rewrite` removes the public `/chat` prefix before forwarding, so `/chat/api/config` reaches the plugin as `/api/config` and `/chat` reaches it as `/`.

`proxy_buffering off` is important for Server-Sent Events. Without it, chat updates or reconnect behavior may be delayed behind nginx buffering.

Apply it manually if you do not let Certbot edit nginx automatically:

```bash
sudo cp examples/nginx/kokoto-webchat.conf /etc/nginx/sites-available/kchat.conf
sudo ln -sf /etc/nginx/sites-available/kchat.conf /etc/nginx/sites-enabled/kchat.conf
sudo nginx -t
sudo systemctl reload nginx
```

### Alternative layout: KWC at `/`, BlueMap at `/chat/`

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

Use `http.public-prefix: ""` and keep `frontend.standalone.path: "/"`. This exposes KWC at `/`, its API at `/api`, and BlueMap at `/chat/`.


## 3. KOKOTO WebChat config.yml

Use this minimal HTTPS reverse-proxy override:

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
  # Recommended: keep empty. If needed, "/chat/api" or "/chat/api/uploads" also works.
  public-base-url: ""

emoji:
  # Recommended: keep empty. If needed, "/chat/api" or "/chat/api/emojis" also works.
  public-base-url: ""

ui:
  image-preview-max-height: 720
```

`map.example.com` must be replaced with your real domain.

Keep media preview max-height enabled for scroll stability. Recommended: `640-720`. `0` means unlimited and can cause scroll jumps in media-heavy virtual scrolling.

## 4. Firewall recommendation

```text
Allow from the internet: 80/tcp, 443/tcp
Block from the internet: 8100/tcp, 8899/tcp
```

When nginx and Minecraft run on the same host, bind the KOKOTO WebChat API to `127.0.0.1`. If nginx runs on another host or inside a container, use the appropriate private address instead.

## 5. Apply order

1. Point the domain A/AAAA record to the server IP.
2. Allow `80/tcp` and `443/tcp` in the firewall.
3. Install nginx and Certbot.
4. Issue a certificate with `sudo certbot --nginx -d map.example.com` or install your certificate manually.
5. Apply the nginx config and confirm `sudo nginx -t` succeeds.
6. Keep adapter/standalone `api-base-url` empty unless you intentionally use a separate public API URL.
7. Open standalone at `https://map.example.com/chat`; the empty standalone API override resolves to `/chat/api` from `http.public-prefix + http.path-prefix`.
8. Leave upload/emoji public URLs empty in normal deployments. Set `upload.public-base-url` or `emoji.public-base-url` only when those resources intentionally use another public URL.
9. Run `/kchat reload` or restart the server to regenerate the web addon file.
10. `/kchat reload` requests `bluemap reload light` automatically after refreshing the BlueMap adapter. If that dispatch fails, run `/bluemap reload light` manually.
11. Open `https://map.example.com/` or `https://map.example.com/chat` in the browser.

## 6. HTTP page + HTTPS API warning

Serving the BlueMap page over HTTP while only the chat API uses HTTPS is not a complete security boundary. If the page or `chat.js` is delivered over HTTP, a network attacker could modify the script before it talks to the HTTPS API.

For public servers, serve both BlueMap and KOKOTO WebChat under the same HTTPS origin.

### URL setting resolution

`http.public-prefix + http.path-prefix` defines the canonical HTTPS public API path (`/chat/api` by default). Adapter and standalone `api-base-url` values are optional independent overrides and normally stay empty. Empty upload/emoji settings follow the canonical public API and append `/uploads` and `/emojis`. Absolute browser paths, relative values, and full `https://...` URLs are only needed for intentional overrides.

## Official references

- [NGINX `ngx_http_proxy_module`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [BlueMap reverse-proxy guide](https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html)

KWC-specific path-prefix, trusted-proxy, SSE, upload and authentication behavior is defined by the KWC 5.3.0 source/configuration rather than by these external references.
