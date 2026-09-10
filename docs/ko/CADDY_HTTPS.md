# KOKOTO WebChat Caddy HTTPS 구성 가이드


![KWC 리버스 프록시 배포 구성](../assets/deployment-modes.svg)

[PNG](../assets/deployment-modes.png) · [SVG](../assets/deployment-modes.svg)

이 가이드는 BlueMap과 KOKOTO WebChat을 로컬 HTTP 서비스로 유지하고, Caddy를 통해 HTTPS로 공개하는 방법을 설명합니다.

## 권장 구조

```text
사용자 브라우저
  ↓ HTTPS
Caddy :443
  ├─ /           -> BlueMap 웹 서버, 보통 127.0.0.1:8100
  └─ /chat/*      -> KOKOTO WebChat API 및 독립 페이지, 보통 127.0.0.1:8899
      /chat/api  -> 내부 /api
      /chat -> 내부 /
```

브라우저는 하나의 공개 origin만 사용해야 합니다.

```text
https://map.example.com/
https://map.example.com/chat/api/config
https://map.example.com/chat
```

내부 서비스는 기존 HTTP 포트 그대로 유지해도 됩니다.

## BMWC에서 KWC로 이전할 때 HTTPS 경로 변경

BlueMapWebChat의 표준 HTTPS 구성은 보통 공개 `/bmwc/api`를 내부 `:8899/api`로, 공개 `/bmwc/chat`을 내부 standalone `/chat`으로 전달했습니다. KOKOTO WebChat 5.0.0 이후 버전은 이 구조를 그대로 사용하지 않습니다. 마이그레이션 시 BMWC의 표준 공개 경로 값은 KWC의 새 자동값으로 정규화됩니다.

```text
BMWC
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/bmwc/chat
  API:         https://map.example.com/bmwc/api

KWC 5.0.0 이후
  BlueMap:     https://map.example.com/
  Standalone:  https://map.example.com/chat
  API:         https://map.example.com/chat/api
```

마이그레이션된 KWC 설정은 표준 BMWC `/bmwc/api` 값을 `adapters.bluemap.api-base-url: ""`, `frontend.standalone.api-base-url: ""` 같은 자동값으로 바꾸고, standalone 내부 경로는 `frontend.standalone.path: "/"`, 공개 prefix는 `http.public-prefix: "/chat"`을 사용합니다. `/bmwc/api/uploads`, `/bmwc/api/emojis` 같은 표준 공개 URL도 각각 빈 자동값으로 정규화됩니다. 사용자가 별도로 만든 커스텀 외부 URL은 임의로 변경하지 않습니다.

**플러그인 설정만 변환해서는 기존 Caddy/nginx 설정이 자동으로 바뀌지 않습니다.** BMWC에서 사용하던 `/bmwc/api`, `/bmwc/chat` 프록시 규칙을 제거하거나 수정하고, 이 문서의 `/chat` prefix 제거 방식으로 변경해야 합니다. Caddy에서는 `/chat`과 `/chat/*`를 `:8899`로 전달하기 전에 `/chat` prefix를 제거합니다.

## 1. Caddy 설치

Caddy는 도메인이 서버를 향하고 있고 `80/tcp`, `443/tcp`가 열려 있으면 보통 Let's Encrypt 인증서를 자동 발급/갱신합니다.

### Debian / Ubuntu 예시

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

### Fedora / RHEL 계열 예시

```bash
sudo dnf install -y 'dnf-command(copr)'
sudo dnf copr enable @caddy/caddy
sudo dnf install -y caddy
```

### Arch Linux 예시

```bash
sudo pacman -S caddy
```

## 2. Caddyfile 예시

`examples/caddy/Caddyfile`을 복사한 뒤 도메인을 바꾸세요.

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

`uri strip_prefix /chat`가 공개 prefix를 제거하므로 `/chat/api/config` 요청은 플러그인에 `/api/config`로, `/chat` 요청은 `/`로 전달됩니다.

적용 예시:

```bash
sudo cp examples/caddy/Caddyfile /etc/caddy/Caddyfile
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl reload caddy
```

### 반대 배치: KWC는 `/`, BlueMap은 `/chat/`

standalone KWC가 사이트 루트 `/`를 사용하고 BlueMap을 `/chat/` 아래에 두려면 다음과 같이 구성합니다.

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

`http.public-prefix: ""`로 설정하고 `frontend.standalone.path: "/"`는 그대로 둡니다. adapter/frontend의 `api-base-url`도 보통 비워둡니다. 결과는 KWC `/`, KWC API `/api`, BlueMap `/chat/`입니다.


### BlueMap + squaremap + standalone을 한 도메인에서 사용

세 frontend를 모두 켜는 경우 한 map을 `/`에 두고 다른 map에는 별도 prefix를 주는 방식이 안전합니다. 예를 들어 squaremap `127.0.0.1:8080`, BlueMap `127.0.0.1:8100`, KWC `127.0.0.1:8899`이면:

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

이 구성은 squaremap을 `/`, BlueMap을 `/bluemap/`, standalone KWC를 `/chat`, KWC API를 `/chat/api`에 공개합니다. BlueMap을 루트로 사용하려면 root map과 prefixed map handler를 서로 바꾸세요.

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

스크롤 안정성을 위해 미디어 미리보기 max-height 제한을 유지하는 것을 권장합니다. 권장값은 `640-720`입니다. `0`은 명시적인 픽셀 상한만 해제하며 브라우저의 viewport 기반 안전 상한은 계속 적용되므로 완전한 무제한 높이는 아닙니다.

## 4. BlueMap

BlueMap은 기존 웹 포트, 보통 `8100`을 계속 사용해도 됩니다. 공개 환경에서는 인터넷에 Caddy의 `80/tcp`, `443/tcp`만 열고 BlueMap과 KOKOTO WebChat은 내부 포트로 유지하는 구성이 좋습니다.

## 5. 방화벽 권장값

```text
인터넷에서 허용: 80/tcp, 443/tcp
인터넷에서 차단: 8100/tcp, 8899/tcp
```

내부적으로 Caddy가 `127.0.0.1:8100`, `127.0.0.1:8899`에 접속합니다.

## 6. 적용 순서

1. 도메인 A/AAAA 레코드를 서버 IP로 지정합니다.
2. 방화벽에서 `80/tcp`, `443/tcp`를 허용합니다.
3. Caddy를 설치합니다.
4. Caddyfile을 복사하고 reload합니다.
5. adapter/standalone의 `api-base-url`은 별도 공개 API URL을 쓰는 경우가 아니면 비워둡니다.
6. 독립 페이지는 `https://map.example.com/chat`으로 열며, standalone API override가 비어 있으면 `http.public-prefix + http.path-prefix`에 따라 `/chat/api`를 사용합니다.
7. 업로드/이모지 공개 URL은 보통 비워둡니다. 별도 공개 경로로 서빙할 때만 설정하고, 업로드/이모지를 다른 공개 URL로 제공할 때만 해당 public-base-url을 명시합니다.
8. `/kchat reload` 또는 서버 재시작으로 웹 애드온 파일을 다시 생성합니다.
9. `/kchat reload`가 BlueMap 어댑터 갱신 뒤 `bluemap reload light`를 자동 요청합니다. 자동 실행에 실패하면 `/bluemap reload light`를 수동으로 실행합니다.
10. 브라우저에서 `https://map.example.com/` 또는 `https://map.example.com/chat`을 엽니다.

## 7. HTTP 페이지 + HTTPS API 주의

BlueMap 페이지를 HTTP로 제공하고 채팅 API만 HTTPS로 사용하는 구성은 완전한 보안 경계가 아닙니다. 페이지나 `chat.js`가 HTTP로 전달되면 네트워크 공격자가 스크립트를 바꿀 수 있습니다.

공개 서버에서는 BlueMap과 KOKOTO WebChat을 같은 HTTPS origin에서 제공하세요.

## nginx 대안

Caddy 대신 nginx를 사용한다면 `docs/ko/NGINX_HTTPS.md`와 `examples/nginx/kokoto-webchat.conf`를 참고하세요.

### URL 설정 해석 규칙

HTTPS 공개 API의 기준은 `http.public-prefix + http.path-prefix`이며 기본값은 `/chat/api`입니다. adapter와 standalone의 `api-base-url`은 서로 독립적인 선택 override이고 보통 비워둡니다. upload/emoji를 비워두면 공통 공개 API에 각각 `/uploads`, `/emojis`를 붙입니다. 절대 경로, 상대값, 전체 `https://...` URL은 별도 공개 URL이 필요할 때만 사용합니다.

## 공식 참조 문서

- [Caddy `reverse_proxy`](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)
- [Caddy reverse-proxy quick start](https://caddyserver.com/docs/quick-starts/reverse-proxy)
- [BlueMap reverse-proxy guide](https://bluemap.bluecolored.de/wiki/webserver/ReverseProxy.html)

KWC 고유의 path-prefix, trusted-proxy, SSE, 업로드, 인증 동작은 외부 문서가 아니라 KWC 5.3.0 소스와 설정을 기준으로 합니다.
