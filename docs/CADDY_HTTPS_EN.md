# KOKOTO WebChat Caddy HTTPS setup guide


![KWC reverse-proxy deployment](assets/deployment-modes.svg)

This guide keeps BlueMap and KOKOTO WebChat running as local HTTP services, then exposes them through Caddy over HTTPS.

## Recommended layout

```text
User browser
  ↓ HTTPS
Caddy :443
  ├─ /           -> BlueMap web server, usually 127.0.0.1:8100
  └─ /chat/*      -> KOKOTO WebChat API and standalone page, usually 127.0.0.1:8899
      /chat/api  -> internal /api
      /chat      -> internal /
```

The browser should use one public origin:

```text
https://map.example.com/
https://map.example.com/chat/api/config
https://map.example.com/chat
```

The internal services can stay on their original HTTP ports.

## HTTPS path changes when migrating from BMWC to KWC

The standard BlueMapWebChat HTTPS layout usually proxied public `/bmwc/api` to internal `:8899/api` and public `/bmwc/chat` to the internal standalone `/chat`. KOKOTO WebChat 5.0.0 and later do not keep that layout. During migration, standard BMWC public-path values are normalized to KWC's new automatic values.

```text
BMWC
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/bmwc/chat
  API:         https://map.example.com/bmwc/api

KWC 5.0.0 and later
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/chat
  API:         https://map.example.com/chat/api
```

For standard BMWC values, migration uses automatic KWC values such as `adapters.bluemap.api-base-url: ""` and `frontend.standalone.api-base-url: ""`, with `frontend.standalone.path: "/"` and `http.public-prefix: "/chat"`. Standard `/bmwc/api/uploads` and `/bmwc/api/emojis` values are normalized to their empty automatic values as well. User-defined custom external URLs are not rewritten arbitrarily.

**Converting the plugin config does not rewrite an existing Caddy/nginx configuration.** Remove or replace the old BMWC `/bmwc/api` and `/bmwc/chat` proxy rules and use the `/chat` prefix-stripping layout in this guide. With Caddy, `/chat` and `/chat/*` are sent to `:8899` after stripping the `/chat` prefix.

## 1. Install Caddy

Caddy usually obtains and renews Let's Encrypt certificates automatically when the domain points to the server and ports `80/tcp` and `443/tcp` are reachable.

### Debian / Ubuntu example

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

### Fedora / RHEL-family example

```bash
sudo dnf install -y 'dnf-command(copr)'
sudo dnf copr enable @caddy/caddy
sudo dnf install -y caddy
```

### Arch Linux example

```bash
sudo pacman -S caddy
```

## 2. Caddyfile example

Copy `examples/caddy/Caddyfile` and change the domain.

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

`uri strip_prefix /chat` removes the public prefix, so `/chat/api/config` reaches the plugin as `/api/config`, and `/chat` reaches it as `/`.

### Alternative layout: KWC at `/`, BlueMap at `/chat/`

If the standalone KWC page should own the site root and BlueMap should live under `/chat/`, use:

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

Set `http.public-prefix: ""`, keep `frontend.standalone.path: "/"`, and normally leave all adapter/frontend `api-base-url` values empty. The resulting URLs are KWC `/`, KWC API `/api`, and BlueMap `/chat/`.


### BlueMap + squaremap + standalone on one domain

If all three frontends are enabled, assign `/` to one map and give the other map a prefix. For example, with squaremap on `127.0.0.1:8080`, BlueMap on `127.0.0.1:8100`, and KWC on `127.0.0.1:8899`:

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

This publishes squaremap at `/`, BlueMap at `/bluemap/`, standalone KWC at `/chat`, and the KWC API at `/chat/api`. To make BlueMap the root site instead, swap the root map and prefixed map handlers.

Apply it:

```bash
sudo cp examples/caddy/Caddyfile /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

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

Replace `map.example.com` with your real domain.

Keep media preview max-height enabled for scroll stability. Recommended: `640-720`. `0` removes the explicit pixel cap, but the browser still applies the viewport-based safety cap; it is therefore not a completely unlimited display height.

## 4. BlueMap

BlueMap may keep using its existing web port, commonly `8100`. For public deployments, expose only Caddy's `80/tcp` and `443/tcp` ports to the internet and keep BlueMap and KOKOTO WebChat internal.

## 5. Firewall recommendation

```text
Allow from internet: 80/tcp, 443/tcp
Block from internet: 8100/tcp, 8899/tcp
```

Internally, Caddy connects to `127.0.0.1:8100` and `127.0.0.1:8899`.

## 6. Apply order

1. Point your domain A/AAAA record to the server IP.
2. Allow `80/tcp` and `443/tcp` in the firewall.
3. Install Caddy.
4. Copy and reload the Caddyfile.
5. Keep adapter/standalone `api-base-url` empty unless you intentionally use a separate public API URL.
6. Open standalone at `https://map.example.com/chat`; the empty standalone API override resolves to `/chat/api` from `http.public-prefix + http.path-prefix`.
7. Leave `upload.public-base-url` and `emoji.public-base-url` empty unless you intentionally serve them from a separate public path. Use explicit upload/emoji URLs only when those resources are intentionally published elsewhere.
8. Run `/kchat reload` or restart the server so the web addon files are regenerated.
9. `/kchat reload` requests `bluemap reload light` automatically after refreshing the BlueMap adapter. If that dispatch fails, run `/bluemap reload light` manually.
10. Open `https://map.example.com/` or `https://map.example.com/chat` in the browser.

## 7. HTTP page + HTTPS API warning

Serving the BlueMap page over HTTP while only the chat API uses HTTPS is technically possible, but it is not a complete security boundary. If the page or `chat.js` is delivered over HTTP, a network attacker could modify the script before it talks to the HTTPS API.

For public servers, serve both BlueMap and KOKOTO WebChat under the same HTTPS origin.

## nginx alternative

If you use nginx instead of Caddy, see `docs/NGINX_HTTPS_EN.md` and `examples/nginx/kokoto-webchat.conf`.

### URL setting resolution

`http.public-prefix + http.path-prefix` defines the canonical HTTPS public API path (`/chat/api` by default). Adapter and standalone `api-base-url` values are optional independent overrides and normally stay empty. Empty upload/emoji settings follow the canonical public API and append `/uploads` and `/emojis`. Absolute browser paths, relative values, and full `https://...` URLs are only needed for intentional overrides.

## Official references

- [Caddy `reverse_proxy`](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)
- [Caddy reverse-proxy quick start](https://caddyserver.com/docs/quick-starts/reverse-proxy)
- [BlueMap reverse-proxy guide](https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html)

KWC-specific path-prefix, trusted-proxy, SSE, upload and authentication behavior is defined by the KWC 5.1.0 source/configuration rather than by these external references.
