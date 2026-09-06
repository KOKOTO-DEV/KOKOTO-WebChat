# KOKOTO WebChat nginx HTTPS 구성 가이드


![KWC 리버스 프록시 배포 구성](../assets/deployment-modes.svg)

[PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg)

이 가이드는 BlueMap과 KOKOTO WebChat을 로컬 HTTP 서비스로 유지하고, nginx를 통해 HTTPS로 공개하는 방법을 설명합니다.

## 권장 구조

```text
사용자 브라우저
  ↓ HTTPS
nginx :443
  ├─ /           -> BlueMap 웹 서버, 보통 127.0.0.1:8100
  └─ /chat/*      -> KOKOTO WebChat API 및 독립 페이지, 보통 127.0.0.1:8899
      /chat/api  -> 내부 /api
      /chat -> 내부 /
```

브라우저는 하나의 공개 origin만 사용해야 합니다. 예: `https://map.example.com/`, `https://map.example.com/chat/api/config`, `https://map.example.com/chat`

## BMWC에서 KWC로 이전할 때 HTTPS 경로 변경

BlueMapWebChat의 표준 HTTPS 구성은 보통 공개 `/bmwc/api`를 내부 `:8899/api`로, 공개 `/bmwc/chat`을 내부 standalone `/chat`으로 전달했습니다. KOKOTO WebChat 5.0.0은 이 구조를 그대로 사용하지 않습니다. 마이그레이션 시 BMWC의 표준 공개 경로 값은 KWC의 새 자동값으로 정규화됩니다.

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

마이그레이션된 KWC 설정은 표준 BMWC `/bmwc/api` 값을 `adapters.bluemap.api-base-url: ""`, `frontend.standalone.api-base-url: ""` 같은 자동값으로 바꾸고, standalone 내부 경로는 `frontend.standalone.path: "/"`, 공개 prefix는 `http.public-prefix: "/chat"`을 사용합니다. `/bmwc/api/uploads`, `/bmwc/api/emojis` 같은 표준 공개 URL도 각각 빈 자동값으로 정규화됩니다. 사용자가 별도로 만든 커스텀 외부 URL은 임의로 변경하지 않습니다.

**플러그인 설정만 변환해서는 기존 Caddy/nginx 설정이 자동으로 바뀌지 않습니다.** BMWC에서 사용하던 `/bmwc/api`, `/bmwc/chat` 프록시 규칙을 제거하거나 수정하고, 이 문서의 `/chat` prefix 제거 방식으로 변경해야 합니다. nginx에서는 `/chat/` location을 `:8899/`로 전달해 공개 `/chat` prefix가 내부 요청에서 제거되도록 구성합니다.

## 1. nginx와 Certbot 설치

nginx는 인증서를 자체 발급하지 않습니다. 공개 HTTPS 구성에서는 nginx와 Certbot을 설치한 뒤, 도메인용 Let's Encrypt 인증서를 발급해야 합니다.

### Debian / Ubuntu 예시

```bash
sudo apt update
sudo apt install -y nginx snapd
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -sf /snap/bin/certbot /usr/bin/certbot
```

인증서 발급 전에 HTTP/HTTPS를 허용합니다.

```bash
sudo ufw allow 'Nginx Full'
```

nginx 플러그인으로 인증서를 발급하고 nginx 설정에 적용합니다.

```bash
sudo certbot --nginx -d map.example.com
```

자동 갱신 테스트:

```bash
sudo certbot renew --dry-run
```

배포판 패키지를 사용할 수 있는 환경에서는 `sudo apt install certbot python3-certbot-nginx` 방식도 가능하지만, Certbot 공식 안내는 대체로 snap 설치를 우선 안내합니다.

## 2. nginx 설정 예시

`examples/nginx/kokoto-webchat.conf`를 복사한 뒤 도메인과 인증서 경로를 바꾸세요.

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

`rewrite`가 공개 `/chat` prefix를 제거하므로 플러그인에는 `/chat/api/config`가 `/api/config`로, `/chat`이 `/`로 전달됩니다.

`proxy_buffering off`는 SSE(Server-Sent Events)에 중요합니다. 이 설정이 없으면 채팅 갱신이나 재연결 동작이 nginx 버퍼링 때문에 늦어질 수 있습니다.

Certbot이 자동으로 nginx 설정을 수정하게 하지 않고 직접 적용한다면:

```bash
sudo cp examples/nginx/kokoto-webchat.conf /etc/nginx/sites-available/kchat.conf
sudo ln -sf /etc/nginx/sites-available/kchat.conf /etc/nginx/sites-enabled/kchat.conf
sudo nginx -t
sudo systemctl reload nginx
```

### 반대 배치: KWC는 `/`, BlueMap은 `/chat/`

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

`http.public-prefix: ""`를 사용하고 `frontend.standalone.path: "/"`는 유지합니다. KWC는 `/`, API는 `/api`, BlueMap은 `/chat/`으로 공개됩니다.


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
  # 권장값은 빈 값입니다. 필요하면 "/chat/api" 또는 "/chat/api/uploads"도 사용 가능합니다.
  public-base-url: ""

emoji:
  # 권장값은 빈 값입니다. 필요하면 "/chat/api" 또는 "/chat/api/emojis"도 사용 가능합니다.
  public-base-url: ""

ui:
  image-preview-max-height: 720
```

`map.example.com`은 실제 도메인으로 바꾸세요.

스크롤 안정성을 위해 미디어 미리보기 max-height 제한을 유지하는 것을 권장합니다. 권장값은 `640-720`입니다. `0`은 무제한이며 미디어가 많은 virtual scroll에서 스크롤 튐이 발생할 수 있습니다.

## 4. 방화벽 권장값

```text
인터넷에서 허용: 80/tcp, 443/tcp
인터넷에서 차단: 8100/tcp, 8899/tcp
```

nginx와 Minecraft가 같은 호스트에 있으면 API는 `127.0.0.1`에만 바인딩하는 것이 좋습니다. 다른 호스트나 컨테이너에서 실행한다면 적절한 사설 주소를 사용하세요.

## 5. 적용 순서

1. 도메인 A/AAAA 레코드를 서버 IP로 지정합니다.
2. 방화벽에서 `80/tcp`, `443/tcp`를 허용합니다.
3. nginx와 Certbot을 설치합니다.
4. `sudo certbot --nginx -d map.example.com`으로 인증서를 발급하거나 직접 인증서를 배치합니다.
5. nginx 설정을 적용하고 `sudo nginx -t`가 성공하는지 확인합니다.
6. adapter/standalone의 `api-base-url`은 별도 공개 API URL을 쓰는 경우가 아니면 비워둡니다.
7. 독립 페이지는 `https://map.example.com/chat`으로 열며, standalone API override가 비어 있으면 `http.public-prefix + http.path-prefix`에 따라 `/chat/api`를 사용합니다.
8. 업로드/이모지 공개 URL은 보통 비워둡니다. 별도 공개 경로로 서빙할 때만 설정하고, 업로드/이모지를 다른 공개 URL로 제공할 때만 해당 public-base-url을 명시합니다.
9. `/kchat reload` 또는 서버 재시작으로 웹 애드온 파일을 다시 생성합니다.
10. `/kchat reload`가 BlueMap 어댑터 갱신 뒤 `bluemap reload light`를 자동 요청합니다. 자동 실행에 실패하면 `/bluemap reload light`를 수동으로 실행합니다.
11. 브라우저에서 `https://map.example.com/` 또는 `https://map.example.com/chat`을 엽니다.

## 6. HTTP 페이지 + HTTPS API 주의

BlueMap 페이지를 HTTP로 제공하고 채팅 API만 HTTPS로 사용하는 구성은 완전한 보안 경계가 아닙니다. 페이지나 `chat.js`가 HTTP로 전달되면 네트워크 공격자가 스크립트를 바꿀 수 있습니다.

공개 서버에서는 BlueMap과 KOKOTO WebChat을 같은 HTTPS origin에서 제공하세요.

### URL 설정 해석 규칙

HTTPS 공개 API의 기준은 `http.public-prefix + http.path-prefix`이며 기본값은 `/chat/api`입니다. adapter와 standalone의 `api-base-url`은 서로 독립적인 선택 override이고 보통 비워둡니다. upload/emoji를 비워두면 공통 공개 API에 각각 `/uploads`, `/emojis`를 붙입니다. 절대 경로, 상대값, 전체 `https://...` URL은 별도 공개 URL이 필요할 때만 사용합니다.

## 공식 참조 문서

- [NGINX `ngx_http_proxy_module`](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- [BlueMap reverse-proxy guide](https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html)

KWC 고유의 path-prefix, trusted-proxy, SSE, 업로드, 인증 동작은 외부 문서가 아니라 KWC 5.2.0 소스와 설정을 기준으로 합니다.
