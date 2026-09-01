# KOKOTO WebChat nginx HTTPS 設定ガイド

このガイドでは、BlueMap と KOKOTO WebChat をローカル HTTP サービスとして動かしたまま、nginx で HTTPS 公開する構成を説明します。

## 推奨構成

```text
ユーザーのブラウザー
  ↓ HTTPS
nginx :443
  ├─ /           -> BlueMap Web サーバー、通常 127.0.0.1:8100
  └─ /chat/*      -> KOKOTO WebChat API とスタンドアロンページ、通常 127.0.0.1:8899
      /chat/api  -> 内部 /api
      /chat -> 内部 /
```

ブラウザー側は `https://map.example.com/`、`https://map.example.com/chat/api/config`、`https://map.example.com/chat` のように同じ公開 origin を使います。

## BMWC から KWC へ移行する際の HTTPS path 変更

BlueMapWebChat の標準 HTTPS 構成では、通常 public `/bmwc/api` を内部 `:8899/api` へ、public `/bmwc/chat` を内部 standalone `/chat` へ proxy していました。KOKOTO WebChat 5.0.0 はこの構成をそのまま使用しません。migration 時に BMWC の標準 public path 値は KWC の新しい自動値へ正規化されます。

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

標準 BMWC 値は `adapters.bluemap.api-base-url: ""`、`frontend.standalone.api-base-url: ""` などの自動値へ変換し、standalone 内部 path は `frontend.standalone.path: "/"`、公開 prefix は `http.public-prefix: "/chat"` を使用します。標準 `/bmwc/api/uploads`、`/bmwc/api/emojis` も空の自動値へ正規化します。ユーザーが独自に設定した外部 URL は勝手に書き換えません。

**plugin config の変換だけでは既存の Caddy/nginx 設定は自動変更されません。** BMWC の `/bmwc/api`、`/bmwc/chat` proxy rule を削除または変更し、このガイドの `/chat` prefix stripping 構成へ変更してください。nginx では `/chat/` location を `:8899/` へ proxy し、public `/chat` prefix が内部 request から除去されるように構成します。

## 1. nginx と Certbot のインストール

nginx は証明書を自動発行しません。公開 HTTPS 構成では nginx と Certbot をインストールし、ドメイン用の Let's Encrypt 証明書を取得します。

### Debian / Ubuntu の例

```bash
sudo apt update
sudo apt install -y nginx snapd
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -sf /snap/bin/certbot /usr/bin/certbot
```

証明書を取得する前に HTTP/HTTPS を許可します。

```bash
sudo ufw allow 'Nginx Full'
```

nginx プラグインで証明書を取得して適用します。

```bash
sudo certbot --nginx -d map.example.com
```

更新テスト:

```bash
sudo certbot renew --dry-run
```

環境によっては `sudo apt install certbot python3-certbot-nginx` も利用できますが、Certbot 公式手順では snap 方式が案内されることが多いです。

## 2. nginx 設定例

`examples/nginx/kokoto-webchat.conf` をコピーして、ドメイン名と証明書パスを変更してください。

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
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
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
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
    }
}
```

`rewrite` が公開 `/chat` prefix を取り除き、`/chat/api/config` を `/api/config`、`/chat` を `/` として転送します。

SSE(Server-Sent Events) のため、`proxy_buffering off` は重要です。この設定がないと、チャット更新や再接続が nginx のバッファリングで遅れる場合があります。

手動で server block を配置する場合:

```bash
sudo cp examples/nginx/kokoto-webchat.conf /etc/nginx/sites-available/kchat.conf
sudo ln -sf /etc/nginx/sites-available/kchat.conf /etc/nginx/sites-enabled/kchat.conf
sudo nginx -t
sudo systemctl reload nginx
```

### 逆配置: KWC は `/`、BlueMap は `/chat/`

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

`http.public-prefix: ""` を使い、`frontend.standalone.path: "/"` は維持します。KWC は `/`、API は `/api`、BlueMap は `/chat/` になります。


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
  # 推奨は空です。必要なら "/chat/api" または "/chat/api/uploads" も使えます。
  public-base-url: ""

emoji:
  # 推奨は空です。必要なら "/chat/api" または "/chat/api/emojis" も使えます。
  public-base-url: ""

ui:
  image-preview-max-height: 720
```

`map.example.com` は実際のドメインに置き換えてください。

スクロールの安定性のため、メディアプレビューの max-height 制限は有効にしておくことを推奨します。推奨値は `640-720` です。`0` は明示的なピクセル上限だけを解除します。ブラウザーの viewport 基準の安全上限は引き続き適用されるため、完全な無制限高さではありません。

## 4. ファイアウォール推奨設定

```text
インターネットから許可: 80/tcp, 443/tcp
インターネットから遮断: 8100/tcp, 8899/tcp
```

nginx と Minecraft が同じホストにある場合、KOKOTO WebChat API は `127.0.0.1` のみに bind するのがおすすめです。

## 5. 適用手順

1. ドメインの A/AAAA レコードをサーバー IP に向けます。
2. ファイアウォールで `80/tcp` と `443/tcp` を許可します。
3. nginx と Certbot をインストールします。
4. `sudo certbot --nginx -d map.example.com` で証明書を取得するか、証明書を手動配置します。
5. nginx 設定を適用し、`sudo nginx -t` が成功することを確認します。
6. adapter/standalone の `api-base-url` は別の公開 API URL を使う場合以外は空のままにします。
7. standalone は `https://map.example.com/chat` で開き、API override が空なら `http.public-prefix + http.path-prefix` から `/chat/api` を使用します。
8. アップロード/絵文字の公開 URL は通常空にします。従来の明示設定が必要な場合、`upload.public-base-url` は `/chat/api` または `/chat/api/uploads`、`emoji.public-base-url` は `/chat/api` または `/chat/api/emojis` を使用できます。
9. `/kchat reload` またはサーバー再起動で Web addon ファイルを再生成します。
10. `/kchat reload` は BlueMap adapter 更新後に `bluemap reload light` を自動要求します。自動実行に失敗した場合は `/bluemap reload light` を手動実行してください。
11. ブラウザーで `https://map.example.com/` または `https://map.example.com/chat` を開きます。

## 6. HTTP ページ + HTTPS API の注意

BlueMap ページを HTTP のまま配信し、チャット API だけ HTTPS にする構成は完全なセキュリティ境界ではありません。公開サーバーでは BlueMap と KOKOTO WebChat の両方を同じ HTTPS origin で配信してください。

### URL 設定の解決規則

HTTPS 公開 API の基準は `http.public-prefix + http.path-prefix` で、既定値は `/chat/api` です。adapter と standalone の `api-base-url` は独立した任意の override で、通常は空のままにします。upload/emoji が空なら共通の公開 API に `/uploads`、`/emojis` を追加します。絶対パス、相対値、完全な `https://...` URL は別の公開 URL が必要な場合にだけ使います。

## 公式参照資料

- [NGINX `ngx_http_proxy_module`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [BlueMap reverse-proxy guide](https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html)

KWC 固有の path-prefix、trusted proxy、SSE、upload、authentication の動作は、これらの外部資料ではなく KWC 5.1.0 のソースと設定を基準にしてください。
