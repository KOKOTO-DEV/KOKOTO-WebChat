# KOKOTO WebChat Caddy HTTPS 設定ガイド

このガイドでは、BlueMap と KOKOTO WebChat をローカル HTTP サービスとして動かしたまま、Caddy で HTTPS 公開する構成を説明します。

## 推奨構成

```text
ユーザーのブラウザー
  ↓ HTTPS
Caddy :443
  ├─ /           -> BlueMap Web サーバー、通常 127.0.0.1:8100
  └─ /chat/*      -> KOKOTO WebChat API とスタンドアロンページ、通常 127.0.0.1:8899
      /chat/api  -> 内部 /api
      /chat -> 内部 /
```

ブラウザー側は同じ公開 origin を使う構成を推奨します。

```text
https://map.example.com/
https://map.example.com/chat/api/config
https://map.example.com/chat
```

## BMWC から KWC へ移行する際の HTTPS path 変更

BlueMapWebChat の標準 HTTPS 構成では、通常 public `/bmwc/api` を内部 `:8899/api` へ、public `/bmwc/chat` を内部 standalone `/chat` へ proxy していました。KOKOTO WebChat 5.0.0 以降はこの構成をそのまま使用しません。migration 時に BMWC の標準 public path 値は KWC の新しい自動値へ正規化されます。

```text
BMWC
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/bmwc/chat
  API:         https://map.example.com/bmwc/api

KWC 5.0.0 以降
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/chat
  API:         https://map.example.com/chat/api
```

標準 BMWC 値は `adapters.bluemap.api-base-url: ""`、`frontend.standalone.api-base-url: ""` などの自動値へ変換し、standalone 内部 path は `frontend.standalone.path: "/"`、公開 prefix は `http.public-prefix: "/chat"` を使用します。標準 `/bmwc/api/uploads`、`/bmwc/api/emojis` も空の自動値へ正規化します。ユーザーが独自に設定した外部 URL は勝手に書き換えません。

**plugin config の変換だけでは既存の Caddy/nginx 設定は自動変更されません。** BMWC の `/bmwc/api`、`/bmwc/chat` proxy rule を削除または変更し、このガイドの `/chat` prefix stripping 構成へ変更してください。Caddy では `/chat` と `/chat/*` から `/chat` prefix を除去してから `:8899` へ転送します。

## 1. Caddy のインストール

Caddy は、ドメインがサーバーを指しており `80/tcp` と `443/tcp` が到達可能であれば、通常 Let's Encrypt 証明書を自動で取得・更新します。

### Debian / Ubuntu の例

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

### Fedora / RHEL 系の例

```bash
sudo dnf install -y 'dnf-command(copr)'
sudo dnf copr enable @caddy/caddy
sudo dnf install -y caddy
```

### Arch Linux の例

```bash
sudo pacman -S caddy
```

## 2. Caddyfile の例

`examples/caddy/Caddyfile` をコピーして、ドメイン名を変更してください。

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

`uri strip_prefix /chat` が公開 prefix を取り除くため、`/chat/api/config` はプラグイン側では `/api/config`、`/chat` は `/` として渡されます。

適用例:

```bash
sudo cp examples/caddy/Caddyfile /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

### 逆配置: KWC は `/`、BlueMap は `/chat/`

standalone KWC を site root `/` に置き、BlueMap を `/chat/` に置く場合:

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

`http.public-prefix: ""`、`frontend.standalone.path: "/"` とし、adapter/frontend の `api-base-url` は通常空のままにします。公開 URL は KWC `/`、KWC API `/api`、BlueMap `/chat/` です。


### BlueMap + squaremap + standalone を1つのドメインで使用

3つの frontend をすべて有効にする場合、1つの map を `/` に置き、もう1つの map には別 prefix を割り当てます。例えば squaremap が `127.0.0.1:8080`、BlueMap が `127.0.0.1:8100`、KWC が `127.0.0.1:8899` の場合:

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

この構成では squaremap が `/`、BlueMap が `/bluemap/`、standalone KWC が `/chat`、KWC API が `/chat/api` になります。BlueMap を root にする場合は root map と prefix 付き map handler を入れ替えてください。

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

スクロールの安定性のため、メディアプレビューの max-height 制限は有効にしておくことを推奨します。推奨値は `640-720` です。`0` は明示的なピクセル上限だけを解除し、ブラウザーの viewport 基準の安全上限は引き続き適用されるため、完全な無制限高さではありません。

## 4. BlueMap

BlueMap は既存の Web port（一般的には `8100`）をそのまま使用できます。公開環境では Internet に Caddy の `80/tcp` と `443/tcp` だけを公開し、BlueMap と KOKOTO WebChat の内部 port は外部へ直接公開しない構成を推奨します。

## 5. ファイアウォール推奨設定

```text
インターネットから許可: 80/tcp, 443/tcp
インターネットから遮断: 8100/tcp, 8899/tcp
```

Caddy と Minecraft が同じホストにある場合、KOKOTO WebChat API は `127.0.0.1` のみに bind するのがおすすめです。

## 6. 適用手順

1. ドメインの A/AAAA レコードをサーバー IP に向けます。
2. ファイアウォールで `80/tcp` と `443/tcp` を許可します。
3. Caddy をインストールします。
4. Caddyfile を配置し、Caddy を reload します。
5. adapter/standalone の `api-base-url` は別の公開 API URL を使う場合以外は空のままにします。
6. standalone は `https://map.example.com/chat` で開き、API override が空なら `http.public-prefix + http.path-prefix` から `/chat/api` を使用します。
7. アップロード/絵文字の公開 URL は通常空にします。従来の明示設定が必要な場合、`upload.public-base-url` は `/chat/api` または `/chat/api/uploads`、`emoji.public-base-url` は `/chat/api` または `/chat/api/emojis` を使用できます。
8. `/kchat reload` またはサーバー再起動で Web addon ファイルを再生成します。
9. `/kchat reload` は BlueMap adapter 更新後に `bluemap reload light` を自動要求します。自動実行に失敗した場合は `/bluemap reload light` を手動実行してください。
10. ブラウザーで `https://map.example.com/` または `https://map.example.com/chat` を開きます。

## 7. HTTP ページ + HTTPS API の注意

BlueMap ページを HTTP のまま配信し、チャット API だけ HTTPS にする構成は完全なセキュリティ境界ではありません。公開サーバーでは BlueMap と KOKOTO WebChat の両方を同じ HTTPS origin で配信してください。

## nginx を使う場合

nginx を使う場合は `docs/ja/NGINX_HTTPS.md` と `examples/nginx/kokoto-webchat.conf` を参照してください。

### URL 設定の解決規則

HTTPS 公開 API の基準は `http.public-prefix + http.path-prefix` で、既定値は `/chat/api` です。adapter と standalone の `api-base-url` は独立した任意の override で、通常は空のままにします。upload/emoji が空なら共通の公開 API に `/uploads`、`/emojis` を追加します。絶対パス、相対値、完全な `https://...` URL は別の公開 URL が必要な場合にだけ使います。

## 公式参照資料

- [Caddy `reverse_proxy`](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)
- [Caddy reverse-proxy quick start](https://caddyserver.com/docs/quick-starts/reverse-proxy)
- [BlueMap reverse-proxy guide](https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html)

KWC 固有の path-prefix、trusted proxy、SSE、upload、authentication の動作は、これらの外部資料ではなく KWC 5.1.0 のソースと設定を基準にしてください。
