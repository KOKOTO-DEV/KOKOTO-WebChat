# 운영 / 보안 체크리스트

이 문서는 공개 운영 또는 HTTPS 리버스 프록시 환경에서 확인할 설정을 정리합니다.

## 권장 공개 구성

공개 서버에서는 KOKOTO WebChat HTTP 서버를 직접 외부에 열기보다 Caddy/Nginx 뒤에 두는 구성을 권장합니다.

```yaml
http:
  host: "127.0.0.1"
  port: 8899
  path-prefix: "/api"
  cors-origin: "https://map.example.com"
  trusted-proxies:
    - "127.0.0.1"
    - "::1"
  log-client-ip-resolution: false
```

직접 HTTP로 사용할 때는 `trusted-proxies: []`를 유지하세요. 이 경우 클라이언트가 보낸 `X-Forwarded-For`는 무시되고 실제 소켓 IP만 사용됩니다.

## 클라이언트 IP 로그 확인

`http.log-client-ip-resolution: true`를 켜면 서버 콘솔과 Minecraft 서버 로그에 다음 형식의 로그가 출력됩니다.

```text
[KOKOTO WebChat] Client IP resolved: socket=127.0.0.1, trustedProxy=true, xForwardedFor=203.0.113.10, result=203.0.113.10, path=/api/config
```

확인 위치:

```text
서버 콘솔
logs/latest.log
```

Linux에서 바로 확인하려면:

```bash
grep "Client IP resolved" logs/latest.log
```

실시간으로 보려면:

```bash
tail -f logs/latest.log | grep "Client IP resolved"
```

확인이 끝나면 `log-client-ip-resolution`을 다시 `false`로 돌리는 것을 권장합니다. `/stream`, `/config`, `/history` 요청까지 찍힐 수 있어 로그가 빠르게 늘어납니다.

## expected trusted-proxies 동작

Caddy/Nginx가 같은 서버에서 프록시하는 경우:

```text
socket=127.0.0.1
trustedProxy=true
xForwardedFor=<실제 사용자 IP>
result=<실제 사용자 IP>
```

`trusted-proxies`가 비어 있거나 프록시 IP가 목록에 없으면:

```text
trustedProxy=false
result=<socket IP>
```

이 경우 rate limit, login fail limit, mute/ban, admin IP 제한은 result 값을 기준으로 동작합니다.

## 명령어 기능

웹 명령어 기능은 강력합니다. 공개 운영에서는 아래처럼 제한해서 쓰는 것을 권장합니다.

```yaml
commands:
  enabled: true
  allow-all: false
  min-role: ADMIN
  require-confirm: true
```

`allow-all: true`는 웹 관리자 계정이 탈취되었을 때 콘솔 명령 실행으로 이어질 수 있으므로 폐쇄망/개인용이 아니면 권장하지 않습니다.

## SSE 연결 제한

`/stream`은 브라우저가 계속 열어두는 SSE 연결입니다.

```yaml
security:
  max-sse-connections-per-ip: 10
  max-sse-connections-total: 500
```

각 값은 `0`으로 두면 비활성화됩니다. 제한에 걸린 클라이언트는 `too_many_stream_connections` 응답을 받습니다.

## 유지 중인 알려진 trade-off

안정성을 위해 현재 다음 영역은 의도적으로 건드리지 않습니다.

```text
- token query/header/body 전달 방식
- request body cache/limit
- pin / unpin / delete 처리 흐름
- command 실행 흐름
```

이 영역은 pin 상태 꼬임 또는 첫 메시지 반복 현상을 만들기 쉬워, 별도 대규모 리팩터링 없이 보안 패치와 섞지 않는 것을 권장합니다.
