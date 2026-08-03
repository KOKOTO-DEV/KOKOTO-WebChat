# 서버 간 공개 채팅 릴레이

`server-relay`는 여러 BlueMapWebChat 서버의 공개 채팅을 연결합니다. 게임, 연동된 웹 사용자, 게스트 메시지를 상대 서버의 웹 채팅과 Minecraft 채팅으로 전달하며 메시지 ID, 댓글 관계, 발신자 정보와 원본 서버 정보를 유지합니다.

## 서버 2대 설정 예시

서버 1:

```yaml
server-relay:
  enabled: true
  server-id: "server1"
  server-name: "서버 1"
  shared-secret: "양쪽-서버에서-동일하게-쓸-충분히-긴-임의의-비밀키"
  connect-timeout-seconds: 5
  request-timeout-seconds: 10
  max-clock-skew-seconds: 60
  dedupe-seconds: 300
  max-hops: 8
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server3"
      url: "https://server3.example.com/bmwc/api"
      secret: ""
      enabled: true
```

서버 3:

```yaml
server-relay:
  enabled: true
  server-id: "server3"
  server-name: "서버 3"
  shared-secret: "양쪽-서버에서-동일하게-쓸-충분히-긴-임의의-비밀키"
  sources:
    game: true
    web: true
    guest: true
    discord: false
    system: false
  delivery:
    web: true
    game: true
  game-format: "&8[&b{server}&8] &f{sender}&7: &f{message}"
  peers:
    - id: "server1"
      url: "https://server1.example.com/bmwc/api"
      secret: ""
      enabled: true
```

피어는 반드시 서로 등록해야 합니다. 요청을 받는 서버의 `peers[].id`가 보내는 서버의 `server-id`와 정확히 같아야 합니다. 서버마다 ID는 고유해야 하며 같은 ID를 두 서버에 사용하면 안 됩니다.

## HTTPS와 리버스 프록시

`url`에는 상대 서버에서 외부 접근 가능한 BMChat API 기본 주소를 입력합니다. `/relay/receive`는 자동으로 붙습니다.

```text
설정값: https://server3.example.com/bmwc/api
실제 요청: https://server3.example.com/bmwc/api/relay/receive
```

공개 HTTPS 경로가 `/relay/receive`의 POST 요청을 포함해 BMChat API 전체를 내부 BMChat HTTP 포트로 전달해야 합니다. 이미 HTTPS로 공개 중이면 8899 포트를 외부에 직접 열 필요가 없습니다. 프록시는 다음 헤더를 보존해야 합니다.

```text
X-BMWC-Relay-Version
X-BMWC-Relay-From
X-BMWC-Relay-Timestamp
X-BMWC-Relay-Signature
```

공인 인증서는 Java에서 보통 바로 동작합니다. 자체 서명 인증서는 Java trust store에 등록하지 않으면 요청이 BMChat까지 도달하기 전에 TLS 검증에서 실패합니다.

## 비밀키

- `shared-secret`은 모든 피어에 사용할 기본 키입니다.
- `peers[].secret`은 해당 피어 연결에만 사용할 개별 키이며 공통 키보다 우선합니다.
- 서버가 2대라면 양쪽 `shared-secret`을 같은 긴 임의 문자열로 설정하고 피어의 `secret: ""`은 비워두면 됩니다.
- 피어별 키를 쓰면 양쪽의 서로 마주보는 피어 항목에 같은 전용 키를 넣어야 합니다.
- 피어 키와 공통 키가 모두 없으면 해당 피어는 활성 목록에서 제외됩니다.

## 여러 서버 연결

- 풀 메시: 모든 서버가 나머지 모든 서버를 피어로 등록합니다. 가장 단순하고 한 서버 장애에도 유리합니다.
- 허브: 각 리프 서버는 허브만 등록하고 허브가 모든 리프를 등록합니다. 허브가 다른 서버로 전달합니다.

릴레이 ID 중복 제거, 원본 서버 억제, 바로 전 송신 피어 제외, `max-hops`가 순환 구조의 무한 반복을 방지합니다. 상대 서버가 꺼져 있을 때의 메시지를 나중에 재전송하는 영구 오프라인 큐는 없습니다.

## reload와 진단 로그

`/bmchat reload`는 기존 릴레이 인스턴스를 닫고 현재 설정으로 새 인스턴스를 만듭니다. 릴레이는 상시 소켓 연결이 아니라 메시지마다 HTTPS 요청을 보내므로 별도 재연결 상태는 없습니다.

정상 로그 예시:

```text
Server relay enabled. serverId=server1, activePeers=2/2 [server2, server3]
```

`activePeers`가 설정한 수보다 적으면 바로 앞뒤 경고에 제외 이유가 표시됩니다. 중복 ID, 자기 서버와 같은 ID, 빈 URL, 잘못된 URL/프로토콜, 비밀키 누락을 확인하세요.

## HTTP 오류

- `403 unknown_peer`: 받는 서버의 활성 피어 목록에 보내는 서버의 정확한 `server-id`가 없습니다. 받는 서버의 `activePeers` 로그와 양방향 설정을 확인합니다.
- `401 bad_signature`: 실제 적용되는 비밀키가 다르거나 프록시가 본문/헤더를 변경했습니다.
- `401 expired_request`: 양쪽 서버 시간이 `max-clock-skew-seconds`보다 많이 차이 납니다.
- `404 relay_disabled`: 받는 서버에서 릴레이가 꺼져 있거나 프록시가 다른 BMChat 인스턴스/경로로 전달합니다.
- `426 unsupported_protocol`: 양쪽 플러그인의 릴레이 프로토콜 버전이 호환되지 않습니다.

설정을 바꾼 쪽에서 `/bmchat reload`를 실행합니다. 특히 받는 서버의 피어 목록이나 비밀키를 바꿨다면 받는 서버도 반드시 reload해야 합니다.

## 서버 구별 표시

- 웹 채팅은 `originServerId`를 기준으로 서버별 고정 색상의 배지를 표시합니다.
- 웹에서는 현재 서버의 배지는 생략하고 다른 서버 메시지에만 서버별 고정 색상 배지를 표시합니다. 게임 출력도 현재 서버명은 생략하며, 다른 서버 메시지의 이전 형식에 `{server}`와 `{server_id}`가 모두 없을 때만 `[server-name]`을 자동으로 붙입니다. Discord는 여러 서버가 공유하는 외부 채널이므로 서버명을 유지합니다.
- Discord 직접 전달 형식도 `{server}`, `{server_id}`를 지원하며 없으면 자동 접두사가 붙습니다. 같은 채널을 여러 서버가 공유할 때는 로컬 게임 채팅을 실제로 감지한 원본 서버만 DiscordSRV 메시지를 수정하며, 다른 서버는 자기 서버명이나 이모지 링크를 다시 붙이지 않습니다. 수신 서버는 릴레이 메시지를 Discord로 재전송하지 않으므로 경유 서버 대체 전송은 없습니다.
- `sources.discord`와 `sources.system`은 DiscordSRV 순환 및 과도한 이벤트 복제를 막기 위해 기본적으로 꺼져 있습니다.
